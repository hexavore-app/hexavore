package app.hexavore.domain.diary

import app.hexavore.domain.nutrition.Macro

/**
 * Un aliment, et ce qu'il a apporté d'une macro sur la journée.
 *
 * [value] est un cumul sur la journée entière et non une ligne de journal : le même
 * aliment mangé au déjeuner et au dîner est **un** aliment qui a donné deux fois. La
 * question posée en touchant un quartier est « qu'est-ce qui m'a donné mes fibres »,
 * et deux lignes « Lentilles » n'y répondent pas mieux qu'une.
 */
data class MacroSource(val name: String, val value: Double)

/**
 * Ce que la liste ne détaille pas, mais compte quand même.
 *
 * Sans cette ligne, une bulle qui s'arrête aux cinq premiers laisserait croire que la
 * somme de ce qu'on lit fait le total du jour. Elle ne le fait presque jamais.
 */
data class OtherSources(val count: Int, val value: Double)

/**
 * Ce qui a donné une macro dans une journée, du plus gros au plus petit.
 *
 * **Trois parts, et chacune répond à une question différente** : [top] dit ce qui a
 * compté, [others] dit combien il en reste, [unknown] dit ce qu'on ignore.
 *
 * [unknown] n'est pas une liste d'aliments qui n'ont rien apporté — ce sont ceux dont
 * la valeur **n'est pas renseignée**. La distinction est la plus ancienne du projet
 * (`Macros`), et c'est ici qu'elle devient enfin actionnable : elle ne dit plus
 * seulement qu'un total est minoré, elle dit **par quel aliment**, donc lequel aller
 * corriger. L'accueil ayant cessé de signaler les totaux minorés ([D119][decisions]),
 * c'est le seul endroit de l'écran qui porte encore cette information.
 *
 * [decisions]: docs/11-decisions.md
 */
data class MacroSources(
    val macro: Macro,
    val top: List<MacroSource>,
    val others: OtherSources?,
    val unknown: List<String>,
) {
    /** `true` quand il n'y a rien à montrer : ni source connue, ni lacune à signaler. */
    val isEmpty: Boolean get() = top.isEmpty() && unknown.isEmpty()

    companion object {
        /** Combien d'aliments sont nommés avant la ligne de reste. */
        const val DETAILED: Int = 5
    }
}

/**
 * Ce qui a donné [macro] dans cette journée.
 *
 * **Un aliment dont on ne sait rien n'est pas un aliment qui n'a rien donné.** Il ne
 * peut donc ni être classé — on ignore où —, ni être compté dans le reste — on ignore
 * combien. Il est nommé à part, et c'est tout ce qu'on peut honnêtement en dire.
 *
 * **Un aliment connu à zéro sort de la liste.** Ce n'est pas une source : lister le
 * blanc de poulet sous les fibres reviendrait à répondre à côté de la question, et la
 * bulle doit tenir sans défiler.
 *
 * **Un aliment mesuré deux fois, dont une sans valeur, est dans les deux listes.** Son
 * cumul est ce qu'on sait, et son nom figure aussi parmi les lacunes : les deux sont
 * vrais en même temps, et taire l'un des deux serait arrondir la vérité du mauvais côté.
 *
 * @param detailed combien d'aliments sont nommés avant que le reste soit cumulé.
 */
fun DaySummary.sourcesOf(macro: Macro, detailed: Int): MacroSources {
    // Par nom affiché, et non par fiche : une fiche manque aux lignes tapées à la
    // main, et la meme chose saisie deux fois -- une fois depuis une fiche, une fois
    // au clavier -- ferait deux lignes que rien ne distingue a l'oeil.
    val byName = dishes.flatMap { it.dish.entries }.groupBy { it.displayName }

    val known = byName
        // La somme de ce qu'on sait, et non la somme en comptant l'inconnu pour zero :
        // les deux rendent le meme nombre ici, mais la seconde ecrit noir sur blanc la
        // confusion que tout le projet evite, et elle deviendrait fausse au premier
        // jour ou une valeur negative existerait.
        .map { (name, entries) -> MacroSource(name, entries.mapNotNull { it.macros[macro] }.sum()) }
        // Un aliment sans rien a donner n'est pas une source, qu'on sache qu'il n'a
        // rien donne ou qu'on ne sache rien de lui : la liste repond a « qu'est-ce qui
        // m'a donne mes fibres », et un zero n'y repond pas. Ceux dont on ignore la
        // valeur sont nommes plus bas, ou ils disent quelque chose.
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }

    val rest = known.drop(detailed)

    return MacroSources(
        macro = macro,
        top = known.take(detailed),
        others = if (rest.isEmpty()) null else OtherSources(rest.size, rest.sumOf { it.value }),
        // L'ordre du journal, et non celui des valeurs : il n'y a pas de valeur.
        unknown = byName.filterValues { entries -> entries.any { it.macros[macro] == null } }.keys.toList(),
    )
}
