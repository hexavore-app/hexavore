package app.hexavore.domain.opening

import java.time.Duration

/**
 * Le rythme des messages à l'ouverture ([D179][decisions]).
 *
 * **Des constantes nommées et non un écran de réglages.** Ce sont des choix d'auteur,
 * qui se changent d'un mot le jour où l'usage les contredit — l'auteur a demandé un
 * champ des possibles large, et c'est ici qu'il s'élargit. Celui qu'un message agace a
 * déjà de quoi le faire taire : l'interrupteur des astuces, et « Ne plus demander ».
 *
 * [decisions]: docs/11-decisions.md
 */
object OpeningRules {
    /**
     * L'absence qui fait une ouverture.
     *
     * **Trente minutes**, et non chaque passage au premier plan : on revient à Hexavore dix
     * fois au cours d'un repas — une photo, l'appareil, la validation, un coup d'œil —, et
     * compter chaque retour ferait d'un seul déjeuner quatre ouvertures. Trois repas par
     * jour en font trois.
     */
    val ABSENCE: Duration = Duration.ofMinutes(30)

    /**
     * La première ouverture qui peut porter un message.
     *
     * La troisième : la première est celle de l'onboarding et du tour, la deuxième celle
     * où l'on essaie seul. Un conseil n'a de sens qu'à quelqu'un qui revient.
     */
    const val FIRST_MESSAGE: Int = 3

    /**
     * Au moins tant d'ouvertures entre deux messages, quels qu'ils soient.
     *
     * Quatre, soit à peu près une fois par jour et demi pour qui note trois repas : assez
     * rare pour qu'un message reste un événement, assez fréquent pour que les treize
     * astuces passent en trois semaines.
     */
    const val SPACING: Int = 4

    /** La demande d'avis attend une semaine d'usage… */
    val RATING_AFTER: Duration = Duration.ofDays(7)

    /**
     * … et dix plats notés.
     *
     * On ne demande pas son avis à qui n'a pas encore eu le temps de s'en faire un : les
     * avis les plus utiles — à lire comme à recevoir — viennent de ceux qui s'en servent.
     */
    const val RATING_AFTER_DISHES: Int = 10

    /** « Plus tard » repousse la demande d'avis d'autant d'ouvertures : une semaine ou deux. */
    const val RATING_SNOOZE: Int = 20
}
