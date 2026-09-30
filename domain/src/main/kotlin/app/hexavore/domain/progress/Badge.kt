package app.hexavore.domain.progress

/**
 * Ce qui se débloque, une fois, et ne se reprend jamais.
 *
 * ### Ce qu'un palier récompense
 *
 * **Trois familles, et elles ne disent pas la même chose.** La régularité ([Family.STREAK])
 * récompense le fait de revenir ; le volume ([Family.VOLUME]) récompense ce qu'on a
 * accumulé et ne peut jamais reculer ; la justesse ([Family.PERFECT]) récompense les
 * journées tenues dans leur fourchette. Quelqu'un qui note tous les jours sans jamais
 * tenir un objectif progresse quand même — c'est l'habitude qu'on cherche à installer
 * d'abord ([01][perimetre]).
 *
 * ### Pourquoi un palier obtenu ne se reprend pas
 *
 * Il est **daté à son déblocage** et rangé tel quel. Un palier recalculé depuis le
 * journal disparaîtrait en corrigeant un vieux plat : supprimer une saisie de l'an
 * dernier retirerait un trophée obtenu, ce qui transformerait la correction d'une
 * erreur en punition. Le journal dit ce qu'on a mangé ; la progression dit ce qu'on a
 * traversé, et ces deux vérités n'ont pas à être la même.
 *
 * ### Les premiers gestes
 *
 * Quatre paliers se débloquent **en essayant une fois** un mode de saisie. Ce ne sont
 * pas des récompenses de performance : ce sont les seules qui font découvrir ce que
 * l'application sait faire, et elles remplacent le tour guidé qu'on ne fera pas.
 *
 * [perimetre]: docs/01-perimetre.md
 */
enum class Badge(val family: Family, val threshold: Int) {
    /** Trois jours d'affilée : le premier seuil, et celui qui décide du reste. */
    STREAK_3(Family.STREAK, threshold = 3),
    STREAK_7(Family.STREAK, threshold = 7),
    STREAK_14(Family.STREAK, threshold = 14),
    STREAK_30(Family.STREAK, threshold = 30),
    STREAK_100(Family.STREAK, threshold = 100),
    STREAK_365(Family.STREAK, threshold = 365),

    /** Dix plats notés : atteint dans les premiers jours, et c'est le but. */
    DISHES_10(Family.VOLUME, threshold = 10),
    DISHES_50(Family.VOLUME, threshold = 50),
    DISHES_100(Family.VOLUME, threshold = 100),
    DISHES_500(Family.VOLUME, threshold = 500),

    /** Une journée dans sa fourchette. La première est la plus difficile. */
    PERFECT_1(Family.PERFECT, threshold = 1),
    PERFECT_3(Family.PERFECT, threshold = 3),
    PERFECT_7(Family.PERFECT, threshold = 7),
    PERFECT_30(Family.PERFECT, threshold = 30),

    /** Le premier code-barres scanné. */
    FIRST_SCAN(Family.DISCOVERY, threshold = 1),

    /** La première assiette photographiée. */
    FIRST_PHOTO(Family.DISCOVERY, threshold = 1),

    /** Le premier repas décrit en une phrase. */
    FIRST_TEXT(Family.DISCOVERY, threshold = 1),

    /** Le premier plat mis en favori. */
    FIRST_FAVORITE(Family.DISCOVERY, threshold = 1),
    ;

    /**
     * Ce que franchir ce palier rapporte.
     *
     * **Proportionnel au seuil pour les trois premières familles**, et forfaitaire pour
     * la découverte : essayer l'appareil photo une fois n'est pas un effort, c'est une
     * curiosité, et la payer comme trente jours de série dévaluerait les trente jours.
     */
    val reward: Int
        get() = when (family) {
            Family.DISCOVERY -> DISCOVERY_REWARD
            Family.STREAK -> threshold * STREAK_REWARD_PER_DAY
            Family.VOLUME -> threshold * VOLUME_REWARD_PER_DISH
            Family.PERFECT -> threshold * PERFECT_REWARD_PER_DAY
        }

    /** Les quatre sortes de paliers. */
    enum class Family { STREAK, VOLUME, PERFECT, DISCOVERY }

    private companion object {
        const val DISCOVERY_REWARD = 25
        const val STREAK_REWARD_PER_DAY = 10
        const val VOLUME_REWARD_PER_DISH = 2
        const val PERFECT_REWARD_PER_DAY = 20
    }
}

/**
 * Ce qu'on a fait, et que les paliers regardent.
 *
 * Un seul objet pour les quatre familles : chaque palier sait lire ce qui le concerne,
 * ce qui évite un `when` de dix-huit branches à chaque nouveau palier.
 */
data class Tally(
    val bestStreak: Int = 0,
    val dishes: Int = 0,
    /**
     * La plus longue suite de journées parfaites, jamais leur total.
     *
     * Trente journées parfaites éparpillées sur un an ne valent pas trente d'affilée :
     * c'est la suite qui est difficile, et c'est elle que le palier nomme.
     */
    val bestPerfectStreak: Int = 0,
    /** Les modes déjà essayés au moins une fois. */
    val discovered: Set<Badge> = emptySet(),
) {
    /** Ce palier est-il atteint ? */
    fun reaches(badge: Badge): Boolean = when (badge.family) {
        Badge.Family.STREAK -> bestStreak >= badge.threshold
        Badge.Family.VOLUME -> dishes >= badge.threshold
        Badge.Family.PERFECT -> bestPerfectStreak >= badge.threshold
        Badge.Family.DISCOVERY -> badge in discovered
    }
}
