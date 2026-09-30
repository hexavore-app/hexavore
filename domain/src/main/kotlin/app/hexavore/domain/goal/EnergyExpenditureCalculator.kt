package app.hexavore.domain.goal

import app.hexavore.domain.profile.UserProfile
import java.time.LocalDate

/**
 * Le métabolisme de base et la dépense totale.
 *
 * **Mifflin-St Jeor**, la formule de référence depuis 1990, recommandée par l'Academy
 * of Nutrition and Dietetics pour la population générale. Elle bat Harris-Benedict en
 * précision sur les populations modernes et, contrairement à Katch-McArdle, ne demande
 * pas un taux de masse grasse que personne ne connaît.
 *
 * ```
 * BMR  = 10 × poids(kg) + 6,25 × taille(cm) − 5 × âge + constante du sexe
 * TDEE = BMR × facteur d'activité
 * ```
 *
 * **Sa marge d'erreur intrinsèque est de ±10 %**, et c'est assumé : cette formule est
 * un point de départ, pas une vérité. C'est l'adaptation hebdomadaire qui corrige, en
 * comparant la tendance réelle du poids à celle qui était visée ([docs/03][calculs]).
 *
 * Un objet sans état plutôt qu'une fonction libre : il sera injecté le jour où une
 * seconde formule apparaîtra, et le nom dit ce qu'il calcule.
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
object EnergyExpenditureCalculator {
    /** Le métabolisme de base, en kcal par jour. */
    fun basalRate(profile: UserProfile, weightKg: Double, on: LocalDate): Double = WEIGHT_FACTOR * weightKg +
        HEIGHT_FACTOR * profile.heightCm -
        AGE_FACTOR * profile.ageOn(on) +
        profile.sex.bmrConstant

    /** La dépense énergétique totale, exercice compris. */
    fun totalExpenditure(profile: UserProfile, weightKg: Double, on: LocalDate): Double =
        basalRate(profile, weightKg, on) * profile.activity.factor

    private const val WEIGHT_FACTOR = 10.0
    private const val HEIGHT_FACTOR = 6.25
    private const val AGE_FACTOR = 5.0
}
