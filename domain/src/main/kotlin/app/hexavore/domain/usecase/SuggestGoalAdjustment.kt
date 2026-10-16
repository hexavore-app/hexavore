package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.goal.ADHERENCE_WINDOW_DAYS
import app.hexavore.domain.goal.AdjustmentSettings
import app.hexavore.domain.goal.AdjustmentSetup
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.goal.EnergyExpenditureCalculator
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalSafetyPolicy
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.goal.MacroDistributionPolicy
import app.hexavore.domain.goal.adherentOn
import app.hexavore.domain.goal.correctionKcal
import app.hexavore.domain.goal.persistentGap
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeightAim
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.profile.declaredAim
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Ce que l'adaptation hebdomadaire a à proposer, s'il y a lieu.
 *
 * **Elle ne modifie jamais rien toute seule** ([docs/03][calculs]). Ce cas d'usage
 * calcule et se tait ; c'est [RespondToAdjustment] qui écrit, et seulement après un
 * appui.
 *
 * Toutes les conditions doivent être réunies, et **le silence est le cas normal** :
 *
 * 1. l'adaptation n'a pas été désactivée, et rien n'a été répondu depuis deux semaines ;
 * 2. au moins dix des quatorze derniers jours portent une saisie ;
 * 3. l'objectif annonce une trajectoire — sans poids cible ni échéance, il n'y a rien à
 *    quoi comparer ;
 * 4. l'écart entre le rythme annoncé et le rythme réel dépasse 0,15 kg par semaine
 *    **deux semaines de suite et dans le même sens** ;
 * 5. la correction retenue, garde-fous compris, ne s'annule pas et ne change pas de sens.
 *
 * **La signature s'écarte de [docs/06][archi]**, qui prévoyait une lecture unique de
 * trois dépôts. Un flux, parce que la carte paraît et disparaît sur deux écrans à la
 * fois ; cinq sources, parce que la correction repasse par les garde-fous et qu'ils
 * demandent la dépense — donc le profil — et parce qu'un refus doit se rappeler.
 *
 * [calculs]: docs/03-nutrition-calculs.md
 * [archi]: docs/06-architecture.md
 */
class SuggestGoalAdjustment(
    private val weights: WeightLog,
    private val diary: DiaryRepository,
    private val goals: Goals,
    private val profiles: Profiles,
    private val settings: AdjustmentSettings,
    private val clock: Clock,
) {
    /**
     * `null` tant qu'il n'y a rien à dire, ce qui est presque toujours.
     *
     * Le jour est lu **une fois**, à l'abonnement, et sert autant à borner la lecture du
     * journal qu'à juger les fenêtres : deux lectures d'horloge se contrediraient à
     * cheval sur minuit, et la fenêtre d'adhérence ne couvrirait plus la plage lue.
     */
    operator fun invoke(): Flow<AdjustmentSuggestion?> {
        val today = clock.today()

        return combine(
            weights.observeHistory(),
            diary.observeRange(today.minusDays(ADHERENCE_WINDOW_DAYS - 1L), today),
            goals.observeCurrent(),
            profiles.observeProfile(),
            settings.observe(),
        ) { log, dishes, goal, profile, setup ->
            suggestion(Facts(today, log, dishes.map { it.date }.toSet(), goal, profile, setup))
        }
    }

    /** Tout ce qu'il faut savoir pour décider, à un jour donné. */
    private data class Facts(
        val today: LocalDate,
        val log: List<WeightEntry>,
        val noted: Set<LocalDate>,
        val goal: Goal?,
        val profile: UserProfile?,
        val setup: AdjustmentSetup,
    )

    /**
     * Ce dont la correction a besoin, quand les trois existent.
     *
     * Séparé du **droit de parler** — l'interrupteur, le silence, l'adhérence — parce
     * que ce sont deux questions : l'une demande si l'on doit se taire, l'autre si l'on
     * a de quoi dire quelque chose. Mêlées en une condition, elles formaient une
     * disjonction de quatre termes qu'on ne relit pas.
     */
    private data class Ingredients(val goal: Goal, val profile: UserProfile, val aim: WeightAim)

    private fun Facts.ingredients(): Ingredients? {
        val aim = goal?.declaredAim(log)
        return if (goal == null || profile == null || aim == null) null else Ingredients(goal, profile, aim)
    }

    private fun suggestion(facts: Facts): AdjustmentSuggestion? {
        val speaking = facts.setup.openOn(facts.today) && facts.noted.adherentOn(facts.today)
        val ingredients = facts.ingredients()

        return if (!speaking || ingredients == null) {
            null
        } else {
            facts.log
                .persistentGap(ingredients.aim.weeklySlope, facts.today)
                ?.let { gap -> propose(facts, ingredients, gap) }
        }
    }

    /**
     * L'objectif corrigé, tel qu'il serait écrit.
     *
     * La correction repasse par **tous** les garde-fous et par la répartition, comme un
     * objectif calculé : une suggestion qui annoncerait un chiffre puis en écrirait un
     * autre ferait mentir la carte, et un plancher calorique franchi en douce serait
     * exactement ce que les garde-fous existent pour empêcher.
     *
     * `null` si la correction retenue s'annule — proposer de changer de zéro kcal
     * demanderait un geste pour ne rien faire.
     *
     * **`null` aussi si elle part à rebours** ([D163][decisions]). Les bornes se
     * recalculent sur la dépense du jour, pas sur celle du jour où l'objectif a été
     * posé : un objectif réglé sur la borne des 25 % quand on pesait un peu moins se
     * retrouve **sous** la borne d'aujourd'hui, et le garde-fou y ramenait la
     * correction — « augmenter l'objectif de 7 kcal » à quelqu'un dont le poids ne
     * bouge pas sous un objectif de perte. Un garde-fou réduit l'ambition jusqu'à
     * l'annuler, jamais au-delà : la même propriété que [GoalSafetyPolicy], tenue sur
     * l'ajustement.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun propose(facts: Facts, ingredients: Ingredients, gapKgPerWeek: Double): AdjustmentSuggestion? {
        val (goal, profile, aim) = ingredients
        val currentWeightKg = facts.log.last().weightKg
        val tdee = EnergyExpenditureCalculator.totalExpenditure(profile, currentWeightKg, facts.today)
        val correction = correctionKcal(gapKgPerWeek)
        val budget = GoalSafetyPolicy.apply(
            rawDeltaKcal = goal.daily.kcal + correction - tdee,
            tdee = tdee,
            currentWeightKg = currentWeightKg,
            targetWeightKg = goal.targetWeightKg,
            sex = profile.sex,
            from = facts.today,
        )
        // Arrondi avant la repartition, comme dans CalculateDailyGoal : c'est ce
        // chiffre que la carte annonce, et une repartition calculee sur 2 524,7 kcal
        // ne retomberait pas sur les 2 525 annonces.
        val kcal = (tdee + budget.dailyDeltaKcal).roundToInt().toDouble()

        return AdjustmentSuggestion(
            actualWeeklyKg = aim.weeklySlope - gapKgPerWeek,
            aimedWeeklyKg = aim.weeklySlope,
            current = goal.daily,
            proposed = MacroDistributionPolicy.distribute(
                kcal = kcal,
                strategy = goal.strategy,
                currentWeightKg = currentWeightKg,
                targetWeightKg = goal.targetWeightKg ?: currentWeightKg,
            ).goal,
        ).takeIf { it.deltaKcal != 0 && (it.deltaKcal > 0) == (correction > 0) }
    }
}
