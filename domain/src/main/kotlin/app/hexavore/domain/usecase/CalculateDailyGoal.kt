package app.hexavore.domain.usecase

import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.EnergyExpenditureCalculator
import app.hexavore.domain.goal.GoalGuard
import app.hexavore.domain.goal.GoalSafetyPolicy
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.goal.KCAL_PER_KILOGRAM
import app.hexavore.domain.goal.MacroDistributionPolicy
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Ce que l'utilisateur vise, exprimé comme il le saisit.
 *
 * [targetWeightKg] et [targetDate] sont nuls en maintien : il n'y a ni poids à
 * atteindre ni échéance, seulement une dépense à couvrir.
 */
data class GoalRequest(
    val strategy: GoalStrategy,
    val currentWeightKg: Double,
    val targetWeightKg: Double? = null,
    val targetDate: LocalDate? = null,
)

/**
 * Le résultat complet, et pas seulement les six chiffres.
 *
 * L'écran a besoin de savoir **pourquoi** l'objectif est ce qu'il est : le TDEE pour
 * l'expliquer, les garde-fous qui ont mordu pour le dire, et la date atteignable pour
 * la proposer. Ne rendre que [goal] obligerait l'écran à refaire le calcul pour
 * l'annoncer, c'est-à-dire à le refaire différemment un jour.
 */
data class GoalPlan(
    val goal: DailyGoal,
    val tdee: Double,
    val guardsApplied: Set<GoalGuard>,
    val reachableOn: LocalDate?,
    val carbsBelowMinimum: Boolean,
    /**
     * Le plancher calorique qui a retenu **tout** le déficit, ou `null`.
     *
     * Quand la dépense estimée est déjà sous le plancher, aucun déficit ne tient sans
     * passer dessous : l'objectif reste la dépense elle-même ([D160][decisions]). Sans
     * ce chiffre, l'écran afficherait « 0 kg par semaine » sous « Perdre du poids », sans
     * dire pourquoi.
     *
     * [decisions]: docs/11-decisions.md
     */
    val withholdingFloorKcal: Double? = null,
) {
    val capped: Boolean get() = guardsApplied.isNotEmpty()

    /**
     * L'écart entre la somme des macros et l'objectif calorique, en kcal.
     *
     * Il vaut quelques kcal d'arrondi, jamais davantage. **Les calories font foi** ;
     * les macros sont des répartitions indicatives, et cet écart n'est ni affiché ni
     * corrigé artificiellement. Il est exposé pour être **éprouvé** : c'est ce
     * contrôle qui a révélé les 70 kcal de fibres distribuées deux fois ([D24][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val energyGap: Double get() = goal.macroEnergy - goal.kcal

    /**
     * Le rythme que cet objectif produit, en kg par semaine. Négatif en perte.
     *
     * C'est l'aperçu que [docs/02][parcours] demande sous les choix d'échéance —
     * « ≈ 0,6 kg par semaine ». Dérivé du budget retenu **après** garde-fous, et non
     * de l'échéance demandée : c'est le rythme réel qui intéresse, pas celui qu'on
     * espérait. Un second calcul dans l'écran aurait fini par annoncer autre chose.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    val weeklyWeightChangeKg: Double
        get() = (goal.kcal - tdee) * DAYS_PER_WEEK / KCAL_PER_KILOGRAM
}

private const val DAYS_PER_WEEK = 7.0

/**
 * Le calcul d'un objectif quotidien, de bout en bout.
 *
 * ```
 * BMR → TDEE → Δkcal demandé par l'échéance → garde-fous → répartition
 * ```
 *
 * Chaque étape vit dans son propre objet et se teste seule ; celui-ci les enchaîne et
 * ne décide de rien. C'est ce qui permet d'éprouver un garde-fou sur ses deux bornes
 * sans construire un profil complet à chaque fois.
 *
 * **L'horloge est injectée**, parce que l'âge et le nombre de jours restants en
 * dépendent : un objectif calculé avec `LocalDate.now()` serait invérifiable, et se
 * périmerait le lendemain de sa propre écriture.
 *
 * @see docs/03-nutrition-calculs.md
 */
class CalculateDailyGoal(private val clock: Clock) {
    operator fun invoke(profile: UserProfile, request: GoalRequest): GoalPlan {
        val today = clock.today()
        val tdee = EnergyExpenditureCalculator.totalExpenditure(profile, request.currentWeightKg, today)

        val budget = GoalSafetyPolicy.apply(
            rawDeltaKcal = request.rawDailyDelta(today),
            tdee = tdee,
            currentWeightKg = request.currentWeightKg,
            targetWeightKg = request.targetWeightKg,
            sex = profile.sex,
            from = today,
        )

        // L'objectif est arrondi avant la repartition, et non apres : c'est lui que
        // l'ecran affiche, et une repartition calculee sur 2 524,7 kcal ne retomberait
        // pas sur les 2 525 annonces.
        val kcal = (tdee + budget.dailyDeltaKcal).roundToInt().toDouble()
        val macros = MacroDistributionPolicy.distribute(
            kcal = kcal,
            strategy = request.strategy,
            currentWeightKg = request.currentWeightKg,
            targetWeightKg = request.targetWeightKg ?: request.currentWeightKg,
        )

        return GoalPlan(
            goal = macros.goal,
            tdee = tdee,
            guardsApplied = budget.guardsApplied,
            reachableOn = budget.reachableOn,
            carbsBelowMinimum = macros.carbsBelowMinimum,
            withholdingFloorKcal = budget.withholdingFloorKcal,
        )
    }

    /**
     * Ce que l'échéance demanderait, sans limite.
     *
     * Zéro en maintien, et zéro aussi si l'échéance est aujourd'hui ou passée : diviser
     * par zéro jour rendrait un infini, que les garde-fous ramèneraient certes à une
     * borne, mais en passant par une valeur qui n'a aucun sens.
     *
     * **Zéro encore quand le poids visé est du mauvais côté** ([D160][decisions]) : le
     * sens est celui de la stratégie, jamais celui de la soustraction. Perdre vers un
     * poids plus haut calculait une prise sous l'étiquette « perdre ». L'écran refuse ce
     * poids et dit pourquoi ; ceci est le filet, pour que la règle vive dans le domaine.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun GoalRequest.rawDailyDelta(today: LocalDate): Double {
        val target = targetWeightKg
        val date = targetDate
        if (target == null || date == null) return 0.0

        val days = ChronoUnit.DAYS.between(today, date)
        val delta = if (days > 0) (target - currentWeightKg) * KCAL_PER_KILOGRAM / days else 0.0
        return if (strategy.heads(delta)) delta else 0.0
    }
}

/** Un écart dans le sens de la stratégie : négatif pour perdre, positif pour prendre, aucun pour maintenir. */
private fun GoalStrategy.heads(deltaKcal: Double): Boolean = when (this) {
    GoalStrategy.LOSE -> deltaKcal < 0
    GoalStrategy.GAIN -> deltaKcal > 0
    GoalStrategy.MAINTAIN -> false
}
