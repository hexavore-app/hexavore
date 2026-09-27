package app.hexavore.domain.resolution

import app.hexavore.domain.food.SearchText
import app.hexavore.domain.language.ContentLanguage

/**
 * Le libellé du modèle, mis dans la forme sous laquelle l'index se laisse interroger.
 *
 * Minuscules, accents et ponctuation sont le travail de [SearchText.normalise], et
 * il n'est pas refait ici : c'est **la même** normalisation que celle des deux index,
 * et une seconde règle divergerait de la première le jour où l'une apprend les
 * ligatures et l'autre non ([D49][decisions], [D73][decisions]).
 *
 * **Ce que cette fonction ajoute est le retrait des articles de tête**, et c'est une
 * nécessité mécanique et non un embellissement. Les deux recherches sont
 * conjonctives : le catalogue local compare une sous-chaîne entière — `name_search
 * LIKE '%du pain%'` — et la table de l'ANSES exige que **tous** les termes du `MATCH`
 * soient présents. « du pain » ne rend donc rien nulle part, alors que « pain » rend
 * les deux cents lignes attendues. Un article gardé n'est pas du bruit dans le
 * classement : c'est une réponse vide.
 *
 * **Les pluriels, eux, ne sont pas traités ici**, et c'est la différence qui compte.
 * Voir [depluralise] : ils sont l'objet d'un second essai, pas de la normalisation.
 *
 * Un libellé qui ne serait fait que d'articles rend la chaîne vide, ce que les deux
 * implémentations de la recherche traitent déjà comme « rien à chercher ».
 *
 * @param language la langue du libellé, qui est celle du catalogue qu'on va interroger.
 *   Elle arrive en paramètre parce que les articles d'une langue ne sont pas ceux d'une
 *   autre, et que le domaine ne devine pas dans quelle langue on lui parle.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md § Résolution, étape 1
 */
fun normaliseLabel(raw: String, language: ContentLanguage): String {
    val articles = language.leadingArticles
    return SearchText
        .normalise(raw)
        .split(' ')
        .filter { it.isNotEmpty() }
        .dropWhile { it in articles }
        .joinToString(" ")
}

/**
 * Le même libellé au singulier naïf, pour le **second** essai et lui seul.
 *
 * L'ordre est la règle, et il est mesuré : l'index de l'ANSES **garde ses pluriels**
 * — 32 % de ses 3 484 libellés en portent un, 6 % commencent par un — et le `LIKE`
 * du catalogue local compare une sous-chaîne entière. Dépluraliser systématiquement
 * ferait donc **perdre** « haricots verts », que la requête brute trouvait. On
 * interroge avec le libellé tel qu'il vient, et on ne retente au singulier que si la
 * première recherche n'a rien rendu ([D74][decisions]).
 *
 * **C'est aussi ce qui rend la naïveté de la règle sans conséquence**, et c'est
 * l'argument le plus solide des deux. « Jus » ne devient pas « ju », « pois » devient
 * « poi » et « eaux » devient « eal » — mais aucun de ces trois libellés n'atteint
 * jamais cette fonction, parce que tous les trois rendent des résultats à la première
 * requête. Une règle approximative placée derrière une garde qui ne s'ouvre qu'en cas
 * d'échec ne peut dégrader que ce qui était déjà vide. **L'argument tient mot pour mot
 * en anglais**, où la règle est tout aussi approximative : « bass » resterait « bass »
 * seulement parce que la garde du `-ss` le protège, et « mice » ne devient rien du tout.
 *
 * Un mot de trois lettres ou moins n'est pas touché : ce qu'il en resterait serait un
 * préfixe si court qu'il ramènerait n'importe quoi.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md § Résolution, étape 1
 */
fun depluralise(normalisedLabel: String, language: ContentLanguage): String = normalisedLabel
    .split(' ')
    .joinToString(" ") { it.singular(language) }

/**
 * Ce qu'on retire en tête, sous la forme normalisée — où l'apostrophe est devenue une
 * coupure de mot, « d'orange » un « d » suivi d'« orange ».
 *
 * Seulement en tête : « pain de mie » garde son « de », qui y désigne quelque chose, et
 * « cream of mushroom soup » son « of ».
 *
 * **Une table par langue, et pas de branche `else`** : une troisième langue ne compile
 * pas tant qu'elle n'a pas dit ce qu'elle retire.
 */
private val ContentLanguage.leadingArticles: Set<String>
    get() = when (this) {
        ContentLanguage.FRENCH -> FRENCH_ARTICLES
        ContentLanguage.ENGLISH -> ENGLISH_ARTICLES
    }

/** Le singulier naïf d'un mot, selon les règles de sa langue. */
private fun String.singular(language: ContentLanguage): String = when {
    length <= SHORTEST_STEM -> this
    else -> when (language) {
        ContentLanguage.FRENCH -> frenchSingular()
        ContentLanguage.ENGLISH -> englishSingular()
    }
}

/**
 * Les trois règles de [docs/04][sources], appliquées dans l'ordre où elles se
 * recouvrent : `-aux` avant `-x`, sans quoi « chevaux » deviendrait « chevau ».
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
private fun String.frenchSingular(): String = when {
    endsWith(PLURAL_AUX) -> dropLast(PLURAL_AUX.length) + SINGULAR_AL
    last() in FRENCH_PLURAL_ENDINGS -> dropLast(1)
    else -> this
}

/**
 * Le pendant anglais, dans l'ordre où ses règles se recouvrent.
 *
 * `-ies` avant `-es`, sans quoi « berries » deviendrait « berri ». Les sifflantes
 * avant le `-s` simple, sans quoi « dishes » deviendrait « dishe ». Et le `-ss`
 * **avant tout le reste** : c'est la seule garde qui compte vraiment, parce que
 * « glass », « cress » et « bass » sont des aliments, qu'ils ne sont pas des pluriels,
 * et qu'un `-s` retiré à l'aveugle en aurait fait trois requêtes vides.
 */
private fun String.englishSingular(): String = when {
    endsWith(PLURAL_SS) -> this
    endsWith(PLURAL_IES) -> dropLast(PLURAL_IES.length) + SINGULAR_Y
    ENGLISH_SIBILANT_PLURALS.any { endsWith(it) } -> dropLast(PLURAL_ES.length)
    endsWith(PLURAL_S) -> dropLast(PLURAL_S.length)
    else -> this
}

private val FRENCH_ARTICLES = setOf("de", "du", "des", "d", "le", "la", "les", "un", "une", "l")

/**
 * Le pendant anglais.
 *
 * « some » et « of » y figurent parce que le modèle écrit volontiers « some bread » et
 * « a glass of milk » : ce sont des mots de quantité et de liaison, pas de désignation,
 * et ils vident la requête au même titre qu'un article.
 */
private val ENGLISH_ARTICLES = setOf("the", "a", "an", "of", "some")

private val FRENCH_PLURAL_ENDINGS = setOf('s', 'x')

private const val PLURAL_AUX = "aux"
private const val SINGULAR_AL = "al"

private const val PLURAL_S = "s"
private const val PLURAL_SS = "ss"
private const val PLURAL_ES = "es"
private const val PLURAL_IES = "ies"
private const val SINGULAR_Y = "y"

/** Les finales où le pluriel anglais ajoute `-es` et non `-s`. */
private val ENGLISH_SIBILANT_PLURALS = setOf("ses", "xes", "zes", "ches", "shes", "oes")

/** En deçà, la troncature laisse un préfixe qui ne désigne plus rien. En caractères. */
private const val SHORTEST_STEM = 3
