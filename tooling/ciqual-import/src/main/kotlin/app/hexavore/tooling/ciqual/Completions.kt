package app.hexavore.tooling.ciqual

import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.nutrition.Macro

/** Un trou à combler : une fiche, son libellé, et la teneur que l'ANSES ne publie pas. */
internal data class Gap(val code: String, val name: String, val macro: Macro)

/**
 * La langue du libellé montré au modèle quand on lui demande une teneur.
 *
 * **Celle de la consigne d'[AnthropicCompleter], et c'est tout ce qui la détermine.** Ce
 * que cette passe produit est un nombre, qui n'a pas de langue ; le libellé n'est là que
 * pour dire de quel aliment il s'agit, et il doit être écrit dans la langue où la
 * question est posée. Le jour où la consigne est traduite, cette constante suit — et pas
 * avant, sinon on demanderait en français ce qu'on nomme en anglais.
 */
internal val COMPLETION_LANGUAGE = ContentLanguage.FRENCH

/**
 * Qui sait estimer une teneur manquante. Une interface, pour que la règle se teste
 * sans réseau.
 *
 * Le contrat est celui de [ShortNamer], et pour les mêmes raisons : le rattachement
 * se fait par **couple code-macro**, et ce qu'on n'a pas demandé est écarté plutôt
 * que deviné.
 */
internal fun interface NutritionCompleter {
    /** @return les teneurs rendues, par couple. Les couples absents seront redemandés. */
    fun complete(gaps: List<Gap>): Map<Pair<String, Macro>, Double>
}

/**
 * La complétion, telle qu'elle se déroule — sans rien savoir du réseau.
 *
 * Même forme que [ShortNames], et la ressemblance n'est pas une économie : ce sont
 * deux passes **distinctes** parce que ce qu'elles produisent n'a pas la même nature.
 * Un titre court est un affichage, une teneur complétée est un chiffre inventé qui
 * entrera dans un journal alimentaire. La seconde mérite sa propre relecture, ses
 * propres garde-fous, et le droit d'être lancée sans l'autre.
 *
 * **Trois garde-fous, et ils tiennent la porte étroite.**
 *
 * 1. **On ne demande que les trous.** Une teneur publiée par l'ANSES n'est jamais
 *    soumise : la question ne se pose pas, et la poser reviendrait à inviter un
 *    modèle à contredire une mesure.
 * 2. **Ce qui est déjà écrit n'est pas redemandé**, donc une passe coupée reprend.
 * 3. **Une complétion devenue inutile est retirée.** Quand l'ANSES publie enfin sa
 *    mesure, l'estimation ne décrit plus cette fiche-là : elle avait été produite
 *    contre un autre état. La garder ferait resurgir un chiffre périmé le jour où la
 *    mesure repartirait.
 */
internal class Completions(private val completer: NutritionCompleter, private val batchSize: Int = BATCH_SIZE) {
    /**
     * @param foods la table de l'ANSES, telle qu'elle vient d'être lue.
     * @param known ce que le fichier porte déjà.
     * @param onBatch reçoit **tout** ce qui est acquis après chaque lot.
     */
    fun generate(
        foods: List<CiqualFood>,
        known: List<CiqualCompletion>,
        onBatch: (List<CiqualCompletion>) -> Unit = {},
    ): List<CiqualCompletion> {
        val settled = kept(foods, known).associateBy { it.code to it.macro }.toMutableMap()

        pending(foods, known).chunked(batchSize).forEach { batch ->
            completer
                .complete(batch)
                .mapNotNull { (key, value) -> accept(batch, key, value) }
                .forEach { settled[it.code to it.macro] = it }
            onBatch(settled.values.toList())
        }
        return settled.values.toList()
    }

    /**
     * Les complétions qui décrivent encore un trou.
     *
     * Celles dont l'ANSES publie désormais la mesure sont **retirées** : c'est ce que
     * « la complétion est effacée quand la source se met à jour » veut dire, et c'est
     * ici qu'il faut le faire — la tâche de génération est la seule qui réécrive le
     * fichier. L'import, lui, refuse de lire une telle ligne plutôt que de la taire.
     */
    fun kept(foods: List<CiqualFood>, known: List<CiqualCompletion>): List<CiqualCompletion> {
        val table = foods.associateBy { it.code }
        return known.filter { table[it.code]?.get(it.macro.nutrient) == null }
    }

    /** Les trous restant à demander, avant de dépenser quoi que ce soit. */
    fun pending(foods: List<CiqualFood>, known: List<CiqualCompletion>): List<Gap> {
        val settled = kept(foods, known).mapTo(mutableSetOf()) { it.code to it.macro }
        return gaps(foods).filterNot { (it.code to it.macro) in settled }
    }

    /** Tous les trous de la table, dans l'ordre des fiches puis des six compteurs. */
    fun gaps(foods: List<CiqualFood>): List<Gap> = foods.flatMap { food ->
        Macro.entries
            .filter { food[it.nutrient] == null }
            .map { Gap(code = food.code, name = food.label(COMPLETION_LANGUAGE).name, macro = it) }
    }

    /**
     * La teneur, si elle tient le contrat. `null` sinon, et elle sera redemandée.
     *
     * **Une teneur négative est écartée sans discussion** ; une teneur nulle ne l'est
     * pas — zéro gramme de fibres dans une huile est une réponse juste, et la refuser
     * ferait redemander éternellement une valeur que le modèle a raison de donner.
     *
     * Le plafond n'est pas décoratif : au-delà de cent grammes pour cent grammes, la
     * teneur est arithmétiquement impossible, et un modèle qui rend 250 s'est trompé
     * d'unité. L'énergie a le sien, plus haut — cent grammes d'huile pure valent
     * 900 kcal.
     */
    private fun accept(batch: List<Gap>, key: Pair<String, Macro>, value: Double): CiqualCompletion? {
        val asked = batch.firstOrNull { it.code == key.first && it.macro == key.second } ?: return null
        val ceiling = if (asked.macro == Macro.CALORIES) MAX_KCAL_PER_100G else MAX_GRAMS_PER_100G
        return CiqualCompletion(asked.code, asked.macro, value).takeIf { value in 0.0..ceiling }
    }

    internal companion object {
        /**
         * Vingt trous par requête, contre cinquante pour les titres courts.
         *
         * Un chiffre nutritionnel demande plus d'attention qu'un raccourci de
         * libellé, et une liste courte laisse au modèle la place de regarder chaque
         * aliment. C'est aussi qu'il y en a **beaucoup moins** : 313 fiches à trou
         * contre 2 091 libellés à raccourcir, donc le nombre de requêtes reste petit
         * même avec des lots plus fins.
         */
        const val BATCH_SIZE = 20

        /** Cent grammes ne contiennent pas plus de cent grammes de quoi que ce soit. */
        const val MAX_GRAMS_PER_100G = 100.0

        /** Cent grammes d'huile pure valent 900 kcal ; au-delà, c'est une faute d'unité. */
        const val MAX_KCAL_PER_100G = 950.0
    }
}
