package app.hexavore.feature.entry

import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.diary.Suggestion
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.profile.UnitSystem
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Le brouillon, tel que les champs de l'écran le portent.
 *
 * **Du texte et non des nombres**, et c'est la seule raison d'exister de ce type.
 * Un champ qui rendrait un `Double` reformaterait « 12, » en « 12 » à la frappe
 * suivante, et il deviendrait impossible de saisir « 12,5 » : le point décimal
 * n'existerait jamais assez longtemps pour être suivi d'un chiffre.
 *
 * La conversion vers [EntryDraft] a donc lieu à chaque recomposition plutôt qu'à la
 * saisie. Elle est bon marché et rend un seul modèle de vérité — celui du domaine —
 * responsable de dire si l'enregistrement est possible.
 */
internal data class EntryForm(
    val dishId: DishId?,
    val date: LocalDate,
    val source: EntrySource,
    val lines: List<EntryFormLine>,
    /**
     * Le favori que ce brouillon rejoue, ou auquel le plat est rattaché.
     *
     * C'est lui qui allume l'étoile, et il **tombe dès qu'une ligne est touchée** :
     * le brouillon cesse alors d'être celui que le favori décrit ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val favoriteId: FavoriteDishId? = null,
    /**
     * Le titre écrit à la main, ou `null` tant que le champ n'a pas été touché.
     *
     * Le champ affiche pourtant quelque chose — le nom du [moment] — et c'est toute la
     * distinction : ce qui est **proposé** n'est pas ce qui est **saisi**, et seul le
     * second s'enregistre ([D118][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val title: String? = null,
    /** Le moment du plat. Les quatre pastilles le changent, et effacent [title]. */
    val moment: MealMoment,
) {
    fun toDraft(): EntryDraft = EntryDraft(
        dishId = dishId,
        date = date,
        source = source,
        lines = lines.map { it.toDraftLine() },
        favoriteId = favoriteId,
        title = title,
        moment = moment,
    )

    /**
     * Une ligne a bougé : le lien vers le favori tombe.
     *
     * Toute retouche y passe — corriger, ajouter, supprimer. Le lien ne se rétablit
     * pas en revenant en arrière, et c'est assumé : comparer le brouillon courant à
     * celui d'origine ferait dépendre l'étoile d'une égalité sur des flottants.
     */
    fun update(id: DraftLineId, transform: (EntryFormLine) -> EntryFormLine): EntryForm =
        copy(lines = lines.map { if (it.id == id) transform(it) else it }, favoriteId = null)

    companion object {
        fun of(draft: EntryDraft): EntryForm = EntryForm(
            dishId = draft.dishId,
            date = draft.date,
            source = draft.source,
            lines = draft.lines.map(EntryFormLine::of),
            favoriteId = draft.favoriteId,
            title = draft.title,
            moment = draft.moment,
        )
    }
}

/**
 * Une ligne saisissable.
 *
 * [macros] indexe les six champs par [Macro] plutôt que de les nommer un par un :
 * l'écran les parcourt dans l'ordre angulaire commun à toute l'application, et
 * ajouter un septième compteur — s'il en arrivait un — ne demanderait pas de
 * réécrire ce type.
 *
 * **Un champ vide vaut inconnu, jamais zéro.** C'est la même règle que partout
 * ailleurs, appliquée à l'endroit où elle est le plus facile à trahir : il serait
 * tentant de lire un champ vide comme un « 0 » et d'éviter ainsi tout traitement du
 * cas nul. Le journal porterait alors des zéros que personne n'a saisis.
 */
internal data class EntryFormLine(
    val id: DraftLineId,
    val entryId: EntryId? = null,
    val foodId: FoodId? = null,
    /**
     * La fiche d'où vient cette ligne, quand elle n'est **pas encore au catalogue**.
     *
     * Elle traverse le formulaire sans y être saisissable, et c'est ce qui manquait :
     * un brouillon la portait, l'écran la perdait, et l'enregistrement citait donc une
     * fiche que personne n'avait versée — la base refusait l'écriture.
     *
     * Le défaut ne se voyait que sur le chemin de l'IA. La recherche verse la fiche au
     * moment du choix et le scan au moment de la lecture ; l'IA, elle, choisit pour
     * l'utilisateur et n'écrit rien, parce que résoudre est une lecture.
     */
    val food: Food? = null,
    val name: String = "",
    val quantity: String = "",
    val unit: QuantityUnit = QuantityUnit.Gram,
    val macros: Map<Macro, String> = emptyMap(),
    /** Les portions que propose la fiche d'origine. Vide pour une ligne tapée à la main. */
    val servings: List<QuantityUnit.Serving> = emptyList(),
    val reference: NutrientValues? = null,
    val edited: Set<Macro> = emptySet(),
    /**
     * Les valeurs qui viennent d'une **fiche completee par un modele**.
     *
     * Elle traverse le formulaire sans etre saisissable : c'est une provenance, pas
     * une donnee qu'on corrige. Le champ concerne porte un contour en pointilles,
     * et la marque tombe des qu'on y touche.
     */
    val estimated: Set<Macro> = emptySet(),
    /**
     * Combien de fois la quantité a réécrit les valeurs de cette ligne.
     *
     * **C'est ce qui fait revivre les champs.** Un champ de saisie tient son propre
     * texte et ne relit son texte initial qu'à la première composition
     * ([D45][decisions]) : sans un signal qui change, un recalcul mettrait à jour le
     * brouillon sans que l'écran bouge. Ce compteur sert de clé de composition, donc
     * un recalcul reconstruit les champs — et une frappe, qui ne l'incrémente pas,
     * laisse le curseur où il est.
     *
     * [decisions]: docs/11-decisions.md
     */
    val revision: Int = 0,
    /**
     * Combien de fois cette ligne a **changé d'aliment**.
     *
     * Un second compteur, et non un usage de plus de [revision], parce qu'ils ne
     * reconstruisent pas la même chose. [revision] fait revivre les six champs de
     * valeurs quand la quantité les recalcule — surtout pas le champ de quantité
     * lui-même, qu'on est en train de taper. Celui-ci fait revivre **toute** la ligne,
     * nom et quantité compris, parce que ce n'est plus le même aliment.
     *
     * Sans lui, choisir une alternative changeait le brouillon sans que l'écran bouge :
     * les pastilles disparaissaient, et le nom restait celui d'avant. Un champ de
     * saisie ne relit son texte initial qu'à la première composition ([D45][decisions]),
     * et rien ne lui disait qu'il en commençait une nouvelle.
     *
     * [decisions]: docs/11-decisions.md
     */
    val substitutions: Int = 0,
    /**
     * Ce qu'un modèle a proposé pour cette ligne, quand c'est un modèle qui l'a
     * proposée.
     *
     * Elle traverse le formulaire sans être saisissable : rien à l'écran ne la
     * modifie, et elle disparaît dès que l'utilisateur choisit une autre fiche — à ce
     * moment-là, la ligne n'est plus une proposition mais une décision.
     */
    val suggestion: Suggestion? = null,
) {
    /**
     * Les unités proposées, **lues du domaine et non recalculées ici**.
     *
     * Cette liste portait sa propre copie de la règle — les deux universelles, puis
     * celles de la fiche — et c'est ce qui a laissé « 1 bol » disparaître deux fois.
     * La première correction avait ajouté au domaine que l'unité qu'une ligne porte
     * fait toujours partie de celles qu'elle propose ; l'écran, lui, lisait la copie
     * et n'en savait rien.
     *
     * Une règle écrite à deux endroits n'est pas la même règle : c'est deux règles qui
     * se ressemblent jusqu'au jour où l'une change.
     */
    fun units(system: UnitSystem): List<QuantityUnit> = toDraftLine().units(system)

    /**
     * Cette unité est-elle celle que la ligne porte ?
     *
     * **La comparaison porte sur le code**, comme celle qui construit [units]. Une
     * portion qui aurait changé de poids depuis l'enregistrement — un bol passé de 250
     * à 260 g — reste le même bol pour qui regarde l'écran, et une pastille « 1 bol »
     * qui ne s'allume pas serait incompréhensible.
     */
    fun chose(unit: QuantityUnit): Boolean = this.unit.code == unit.code

    fun toDraftLine(): DraftLine = DraftLine(
        id = id,
        entryId = entryId,
        foodId = foodId,
        food = food,
        name = name,
        quantity = number(quantity),
        unit = unit,
        // Les six champs descendent independamment les uns des autres. Un champ
        // d'energie vide n'emporte plus les cinq autres : c'est ce qui permet a un
        // aliment sans energie determinee -- la feta, les capres -- d'arriver avec
        // ses proteines et ses lipides, l'energie seule restant a completer.
        values = NutrientValues(
            kcal = macroValue(Macro.CALORIES),
            protein = macroValue(Macro.PROTEIN),
            carbs = macroValue(Macro.CARBS),
            sugars = macroValue(Macro.SUGARS),
            fat = macroValue(Macro.FAT),
            fiber = macroValue(Macro.FIBER),
        ),
        servings = servings,
        reference = reference,
        edited = edited,
        estimated = estimated,
        suggestion = suggestion,
    )

    /**
     * La quantité a changé : les valeurs suivent, et les champs sont reconstruits.
     *
     * La règle est celle du domaine — seules les valeurs non corrigées à la main
     * bougent — et elle est appliquée là plutôt qu'ici : ce type ne fait que porter
     * le texte des champs, il ne décide pas de ce que valent les macros.
     */
    fun remeasured(quantity: String, unit: QuantityUnit = this.unit): EntryFormLine {
        val recomputed = toDraftLine().measured(number(quantity), unit)
        val fields = Macro.entries.associateWith { recomputed.values[it].asWholeField() }

        // La comparaison porte sur les champs et non sur les valeurs : arrondies,
        // 41,2 g et 41,4 g s'ecrivent pareil, et rien ne justifie de reconstruire
        // les champs -- donc de risquer un curseur -- pour un texte identique.
        return when (fields) {
            macros -> copy(quantity = quantity, unit = unit)
            else -> copy(quantity = quantity, unit = unit, macros = fields, revision = revision + 1)
        }
    }

    /**
     * La même ligne, sur une autre fiche — ce que choisir une alternative produit.
     *
     * **La quantité reste**, les valeurs suivent : celui qui corrige « riz » en « riz
     * complet » ne veut pas retaper 180 g. Et [suggestion] tombe, parce qu'une ligne
     * qu'on vient de choisir n'est plus une proposition à relire.
     *
     * [revision] avance pour que les champs se reconstruisent : sans ce signal, le
     * brouillon changerait sans que l'écran bouge ([D45][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    fun substituted(food: Food): EntryFormLine {
        val replaced = DraftLine.of(id, food).measured(number(quantity), unit)
        return of(replaced).copy(
            entryId = entryId,
            revision = revision + 1,
            substitutions = substitutions + 1,
            suggestion = null,
        )
    }

    private fun macroValue(macro: Macro): Double? = number(macros[macro].orEmpty())

    companion object {
        fun of(line: DraftLine): EntryFormLine = EntryFormLine(
            id = line.id,
            entryId = line.entryId,
            foodId = line.foodId,
            food = line.food,
            name = line.name,
            quantity = line.quantity.asField(),
            unit = line.unit,
            macros = Macro.entries.associateWith { line.values[it].asWholeField() },
            servings = line.servings,
            reference = line.reference,
            edited = line.edited,
            estimated = line.estimated,
            suggestion = line.suggestion,
        )
    }
}

/**
 * Un nombre tel qu'un clavier français le produit.
 *
 * La virgule décimale est acceptée au même titre que le point. Le clavier numérique
 * d'Android affiche l'un ou l'autre selon la locale, et refuser la virgule rendrait
 * la saisie impossible sur un téléphone en français sans qu'aucun message ne dise
 * pourquoi.
 *
 * Une chaîne vide rend `null` : le champ n'a pas été renseigné, ce qui n'est pas
 * zéro.
 */
private fun number(text: String): Double? = text
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()

/**
 * Un nombre tel qu'on le remet dans un champ.
 *
 * Sans décimale quand il n'en a pas : « 150 » et non « 150.0 », qui donnerait à
 * chaque relecture d'un plat l'apparence d'une précision au dixième de gramme.
 */
private fun Double?.asField(): String = when {
    this == null -> ""
    this == toLong().toDouble() -> toLong().toString()
    else -> toString()
}

/**
 * Une valeur nutritionnelle telle qu'on la met dans un champ : un entier.
 *
 * **Ce qui est affiché est ce qui sera enregistré.** L'arrondi a lieu ici, à
 * l'aller ; la lecture reprend le texte tel quel. Arrondir seulement à l'affichage
 * ferait diverger le chiffre lu de celui écrit dans le journal, ce qui est la
 * définition d'un écran qui ment.
 *
 * `null` reste vide : inconnu n'est pas zéro, et un arrondi ne crée pas de valeur.
 */
private fun Double?.asWholeField(): String = this?.roundToInt()?.toString().orEmpty()

/**
 * Ce qui manque à une ligne pour être enregistrable.
 *
 * Trois champs seulement : un nom, une quantité, une énergie. Les cinq autres valeurs
 * restent facultatives — un produit mal renseigné doit pouvoir entrer dans le journal
 * avec ses trous visibles.
 *
 * L'ordre du `when` est celui de l'écran, de haut en bas : ce qu'on désigne est le
 * **premier** manque, parce qu'un formulaire qui signale trois champs à la fois ne
 * dit plus par où commencer.
 */
internal enum class MissingField {
    NAME,
    QUANTITY,
    CALORIES,
}

/** Le premier champ manquant de cette ligne, ou `null` si elle est enregistrable. */
internal val EntryFormLine.missing: MissingField?
    get() = when {
        name.isBlank() -> MissingField.NAME
        (number(quantity) ?: 0.0) <= 0.0 -> MissingField.QUANTITY
        number(macros[Macro.CALORIES].orEmpty()) == null -> MissingField.CALORIES
        else -> null
    }
