package app.hexavore.domain.resolution

import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.SearchText
import app.hexavore.domain.language.ContentLanguage

/**
 * Des grammes, à partir de ce que le modèle a estimé.
 *
 * C'est la charnière entre [EstimatedUnit] — un vocabulaire d'estimation qui ne
 * porte aucun poids ([D72][decisions]) — et le journal, qui n'enregistre que des
 * grammes.
 *
 * **La portion nommée de la fiche l'emporte toujours sur le forfait**, et c'est un
 * écart avec le tableau de [docs/04][sources], qui fixe `BOWL → 250 g` à plat. La
 * table des portions contient déjà « 1 bol » à **40 g** pour un aliment et **50 g**
 * pour un autre : appliquer le forfait à un bol de céréales se tromperait d'un
 * facteur six. Le forfait n'est donc qu'un repli, et il se signale comme tel
 * ([D73][decisions]).
 *
 * **Le poids du modèle l'emporte sur tout forfait, jamais sur une mesure.** Quand la
 * fiche nomme la portion, c'est une donnée et elle gagne. Quand elle ne dit rien, notre
 * repli est un chiffre inventé — cent grammes pour « une pièce », qu'elle soit cacahuète
 * ou pomme — là où le modèle a regardé l'assiette. Le forfait ne sert donc plus que
 * lorsque personne ne s'est prononcé.
 *
 * **Et `G` n'est ni l'un ni l'autre.** [D112][decisions] a écrit cette règle avec deux
 * cas en tête — ce qu'on a mesuré, ce qu'on a deviné — et il en existait un troisième :
 * l'unité qui **est déjà** celle du journal. Convertir un gramme en gramme ne demande de
 * savoir ni la fiche ni la densité ; c'est l'identité, et elle n'a donc aucune mesure à
 * faire valoir. Ce qu'elle protégeait n'était pas une donnée, c'était la `quantity` du
 * modèle — contre le `grams` du **même** modèle, qui dit le poids total de la ligne
 * ([D155][decisions]).
 *
 * @param food la fiche visée, ou `null` quand la résolution n'a rien trouvé.
 * @param density en g/ml, quand on la connaît. **Elle ne vient pas de [food]**, et
 *   ce n'est pas un oubli : aucune source ne la publie aujourd'hui — CIQUAL ne la
 *   donne pas, Open Food Facts pas davantage — et [D64][decisions] veut qu'une
 *   colonne attende d'avoir quelqu'un pour l'écrire. Le paramètre existe pour que
 *   la règle soit juste le jour où une source arrive ; il vaut `null` partout
 *   aujourd'hui, et un millilitre pèse donc un gramme, en le disant.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
fun convertToGrams(
    quantity: Double,
    unit: EstimatedUnit,
    language: ContentLanguage,
    food: Food? = null,
    density: Double? = null,
    estimated: Double? = null,
): ConvertedQuantity {
    val perUnit = gramsPerUnit(unit, language, food, density)
    val converti = ConvertedQuantity(grams = quantity * perUnit.grams, guessed = perUnit.guessed)
    val weight = estimated?.takeIf { it > 0.0 } ?: return converti

    return when {
        // En grammes, les deux champs disent la meme chose dans la meme unite. Quand
        // ils se contredisent, c'est `grams` qui fait foi : c'est lui que le prompt
        // definit comme le poids total de la ligne, la ou `quantity` change de sens
        // avec l'unite -- et qu'un modele remplit a « 1 » comme on compte une part.
        // Rien n'est devine ici : un gramme reste un gramme.
        unit == EstimatedUnit.G -> known(weight)

        // Il reste une estimation -- il n'a rien pese -- mais une estimation informee,
        // et elle ne remplace jamais qu'un forfait.
        perUnit.guessed -> guessed(weight)

        else -> converti
    }
}

/**
 * Ce que pèse **une** unité.
 *
 * Séparé de la multiplication parce que c'est là qu'est toute la règle : la
 * quantité ne fait que mettre à l'échelle, et confondre les deux rendrait chaque
 * cas de test dépendant d'un facteur qui ne l'intéresse pas.
 *
 * Une assiette n'a pas de branche « portion nommée », et il n'y a pas de raison
 * d'en attendre une : une assiette n'est pas une propriété de l'aliment, donc
 * aucune fiche ne peut la mesurer.
 */
private fun gramsPerUnit(
    unit: EstimatedUnit,
    language: ContentLanguage,
    food: Food?,
    density: Double?,
): ConvertedQuantity {
    val words = language.portionWords
    return when (unit) {
        EstimatedUnit.G -> known(ONE_GRAM)
        EstimatedUnit.ML -> density?.let(::known) ?: guessed(DEFAULT_DENSITY)
        EstimatedUnit.PIECE -> food.pieceWeight()
        EstimatedUnit.SLICE -> food.portionOr(words.slice, DEFAULT_SLICE_G)
        EstimatedUnit.TBSP -> food.portionOr(words.tablespoon, DEFAULT_TBSP_G * densityOr(density))
        EstimatedUnit.TSP -> food.portionOr(words.teaspoon, DEFAULT_TSP_G * densityOr(density))
        EstimatedUnit.BOWL -> food.portionOr(words.bowl, DEFAULT_BOWL_G)
        EstimatedUnit.PLATE -> guessed(DEFAULT_PLATE_G)
        EstimatedUnit.GLASS -> food.portionOr(words.glass, DEFAULT_GLASS_G * densityOr(density))
    }
}

/**
 * La portion de la fiche dont le libellé nomme cette unité, ou le forfait.
 *
 * Le libellé est comparé sous la forme normalisée de la recherche, pour que
 * « 1 cuillère à soupe » se reconnaisse dans « cuillere a soupe ». Les deux
 * cuillères ne se confondent pas : le libellé cherché porte le mot entier, pas
 * seulement « cuillere ».
 */
private fun Food?.portionOr(label: String, fallbackGrams: Double): ConvertedQuantity = this
    ?.servings
    ?.firstOrNull { SearchText.normalise(it.label).contains(label) }
    ?.let { known(it.grams) }
    ?: guessed(fallbackGrams)

/**
 * Ce que pèse « une pièce », dans l'ordre de [docs/04][sources].
 *
 * La portion par défaut de la fiche, sinon la première qu'elle porte, sinon la
 * quantité proposée à l'ouverture, sinon cent grammes. Les trois premières sont des
 * données ; la dernière est une supposition et se déclare comme telle.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
private fun Food?.pieceWeight(): ConvertedQuantity {
    val fromServings = (this?.defaultServing ?: this?.servings?.firstOrNull())?.grams
    val fromFood = fromServings ?: this?.defaultServingG

    return if (fromFood == null) guessed(DEFAULT_PIECE_G) else known(fromFood)
}

private fun densityOr(density: Double?) = density ?: DEFAULT_DENSITY

private fun known(grams: Double) = ConvertedQuantity(grams, guessed = false)

private fun guessed(grams: Double) = ConvertedQuantity(grams, guessed = true)

/**
 * L'identité, et elle ne sert plus que lorsque le modèle s'est tu sur le poids.
 *
 * Elle reste `known` : un gramme est un gramme, et une ligne en grammes n'a jamais à
 * porter le marqueur de ce qui a été deviné.
 */
private const val ONE_GRAM = 1.0

/** Un millilitre pèse un gramme, faute de mieux — et c'est toujours une supposition. */
private const val DEFAULT_DENSITY = 1.0

// Les forfaits de docs/04-sources-de-donnees.md. Aucun n'est une mesure : ce sont
// les valeurs qu'on applique quand la fiche ne dit rien, et chacune est signalee.
private const val DEFAULT_PIECE_G = 100.0
private const val DEFAULT_SLICE_G = 30.0
private const val DEFAULT_TBSP_G = 15.0
private const val DEFAULT_TSP_G = 5.0
private const val DEFAULT_BOWL_G = 250.0
private const val DEFAULT_PLATE_G = 350.0
private const val DEFAULT_GLASS_G = 200.0

/**
 * Les cinq mots qu'une portion de fiche peut nommer, dans la langue de la table.
 *
 * **Ce ne sont pas des libellés d'interface, et c'est tout ce qu'il faut comprendre
 * ici.** Personne ne les lit : ils sont comparés à la colonne `label` de
 * `ciqual_serving`, c'est-à-dire à de la **donnée**. Ils suivent donc la langue du
 * catalogue — celle dans laquelle `servings.csv` a écrit ses portions — et non celle
 * des écrans, qui pourrait en différer le temps d'un redémarrage.
 */
private data class PortionWords(
    val slice: String,
    val tablespoon: String,
    val teaspoon: String,
    val bowl: String,
    val glass: String,
)

/**
 * La table par langue, sans branche `else` : une troisième langue ne compile pas tant
 * qu'elle n'a pas nommé ses cinq portions.
 */
private val ContentLanguage.portionWords: PortionWords
    get() = when (this) {
        ContentLanguage.FRENCH -> FRENCH_PORTIONS
        ContentLanguage.ENGLISH -> ENGLISH_PORTIONS
    }

// Sous leur forme normalisee, celle de l'index de recherche.
private val FRENCH_PORTIONS =
    PortionWords(
        slice = "tranche",
        tablespoon = "cuillere a soupe",
        teaspoon = "cuillere a cafe",
        bowl = "bol",
        glass = "verre",
    )

/**
 * Le pendant anglais.
 *
 * « tablespoon » et « teaspoon » en un mot, comme `servings.csv` les écrit : la
 * comparaison porte sur le mot entier, et « spoon » seul confondrait les deux
 * cuillères — c'est exactement le défaut que la version française évite en cherchant
 * « cuillere a soupe » plutôt que « cuillere ».
 */
private val ENGLISH_PORTIONS =
    PortionWords(
        slice = "slice",
        tablespoon = "tablespoon",
        teaspoon = "teaspoon",
        bowl = "bowl",
        glass = "glass",
    )
