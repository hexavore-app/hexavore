package app.hexavore.domain.diary

import java.time.LocalTime

/**
 * Le moment de la journée auquel un plat se rattache.
 *
 * **Ce n'est pas le retour des repas nommés de [D06][decisions].** Ce que [D31][decisions]
 * a écarté est la **case à choisir avant d'enregistrer**, pour répondre à une question
 * que personne ne se pose. Ici, personne ne choisit rien : le moment se déduit de
 * l'heure, il ne coûte aucun geste, et il ne range le plat nulle part — les plats
 * restent une liste chronologique. Il sert à **nommer** un plat pour qu'une liste
 * puisse s'écrire sans citer tous ses aliments.
 *
 * La correction, elle, reste possible : un dîner noté le lendemain matin porterait
 * sinon le nom du petit-déjeuner, puisque l'heure d'un plat est celle de sa saisie.
 *
 * [decisions]: docs/11-decisions.md
 */
enum class MealMoment {
    BREAKFAST,
    LUNCH,
    SNACK,
    DINNER,
    ;

    companion object {
        /**
         * Le moment d'une heure locale.
         *
         * **Les bornes sont des milieux, pas des heures de repas.** Personne ne dîne à
         * 18 h 01 ; ce qu'on cherche est l'endroit où basculer d'un nom à l'autre, et
         * il se place entre deux repas plutôt que sur l'un d'eux.
         *
         * **La nuit appartient au dîner.** Un plat noté à 1 h du matin est le
         * prolongement de la soirée — c'est déjà ainsi que le journal le range, la
         * journée d'un repas venant de sa date locale et non de son heure.
         */
        fun at(time: LocalTime): MealMoment = when {
            time < BREAKFAST_FROM -> DINNER
            time < LUNCH_FROM -> BREAKFAST
            time < SNACK_FROM -> LUNCH
            time < DINNER_FROM -> SNACK
            else -> DINNER
        }
    }
}

private val BREAKFAST_FROM: LocalTime = LocalTime.of(5, 0)
private val LUNCH_FROM: LocalTime = LocalTime.of(11, 0)
private val SNACK_FROM: LocalTime = LocalTime.of(15, 0)
private val DINNER_FROM: LocalTime = LocalTime.of(18, 0)
