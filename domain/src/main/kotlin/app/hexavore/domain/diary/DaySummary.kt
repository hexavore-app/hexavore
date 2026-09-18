package app.hexavore.domain.diary

import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.MacroTotals
import java.time.LocalDate
import java.time.ZoneId

/**
 * Un plat et ce qu'il a réellement apporté.
 *
 * Les six totaux sont calculés une fois ici plutôt que par chaque écran : deux
 * calculs du même nombre finissent toujours par diverger. Les six, et pas seulement
 * les calories — savoir qu'un plat pèse 900 kcal ne dit pas s'il a apporté des
 * protéines ou du sucre, et c'est justement la question qu'on se pose en relisant
 * sa journée.
 */
data class DishSummary(
    val dish: Dish,
    val totals: MacroTotals,
    /**
     * Le nom sous lequel ce plat s'affiche.
     *
     * Calculé pour la journée entière et porté ici, parce qu'il dépend du
     * **voisinage** : un plat ne sait pas seul qu'il est le deuxième déjeuner du jour
     * ([titles]).
     */
    val title: DishTitle,
)

/**
 * Ce qu'affiche l'accueil pour une journée.
 *
 * [logged] distingue « rien noté » de « rien mangé ». Une journée sans saisie n'est
 * pas une journée à zéro : la compter comme telle fausserait les moyennes du
 * calendrier et déclencherait des suggestions d'ajustement absurdes.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
data class DaySummary(
    val date: LocalDate,
    /**
     * Fuseau dans lequel lire les horodatages des plats.
     *
     * Porté par le résumé plutôt que relu par l'écran : l'heure d'un plat doit se
     * lire dans le même fuseau que celui qui a décidé de quelle journée il relève.
     * Deux sources de fuseau, c'est un plat affiché à 23 h dans une journée qui
     * pense être la veille.
     */
    val zone: ZoneId,
    /**
     * L'objectif **actif ce jour-là**, et non celui d'aujourd'hui ([D04][decisions]).
     *
     * `null` pour une journée antérieure au premier objectif — avant l'onboarding, ou
     * pour une journée notée avant qu'un objectif ait été posé. Ce n'est pas un cas
     * d'erreur : une telle journée n'a rien à quoi se comparer, et lui appliquer
     * l'objectif courant serait la juger sur une règle qu'elle n'avait pas. L'accueil
     * affiche alors les six totaux sans jauge.
     *
     * [decisions]: docs/11-decisions.md
     */
    val goal: DailyGoal?,
    val totals: MacroTotals,
    val dishes: List<DishSummary>,
) {
    /** `false` quand la journée ne contient aucune saisie. */
    val logged: Boolean get() = dishes.isNotEmpty()
}
