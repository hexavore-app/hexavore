package app.hexavore.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.CalculateDailyGoal
import app.hexavore.domain.usecase.GoalRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Les cinq étapes, et le calcul qu'elles alimentent.
 *
 * **Le plan est recalculé à chaque réponse**, pas seulement à la fin : c'est ce qui
 * fait suivre l'aperçu sous les curseurs, et surtout ce qui garantit qu'il n'existe
 * **qu'un** calcul. Un aperçu qui estimerait de son côté finirait par annoncer autre
 * chose que l'écran suivant.
 *
 * **Rien n'est écrit avant la dernière étape.** L'onboarding s'abandonne : un profil à
 * moitié rempli, écrit au fil de l'eau, ferait croire à l'accueil qu'un objectif
 * existe.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val profiles: Profiles,
    private val weights: WeightLog,
    private val goals: Goals,
    private val calculate: CalculateDailyGoal,
    private val clock: Clock,
    private val ids: IdGenerator,
) : ViewModel() {
    private val state = MutableStateFlow(OnboardingUiState(today = clock.today()))
    val uiState: StateFlow<OnboardingUiState> = state

    /**
     * Les réponses, remplacées en bloc.
     *
     * Choisir « Maintenir » efface le poids cible et l'échéance : les garder ferait
     * calculer un écart calorique pour une stratégie qui n'en veut pas, et
     * l'utilisateur verrait un déficit sous une étiquette « maintien ».
     */
    fun onAnswers(answers: OnboardingAnswers) {
        val cleaned = if (answers.strategy == GoalStrategy.MAINTAIN) {
            answers.copy(targetWeightKg = null, targetDate = null)
        } else {
            answers
        }
        state.update { it.copy(answers = cleaned).recalculated() }
    }

    /** Se caler sur la date atteignable que les garde-fous ont calculée. */
    fun onUseReachableDate() {
        val date = state.value.plan?.reachableOn ?: return
        onAnswers(state.value.answers.copy(targetDate = date))
    }

    fun onNext() {
        val current = state.value
        if (!current.canContinue) return
        val next = OnboardingStep.entries.getOrNull(current.step.index + 1) ?: return
        state.value = current.copy(step = next).recalculated()
    }

    fun onBack() {
        val previous = OnboardingStep.entries.getOrNull(state.value.step.index - 1) ?: return
        state.value = state.value.copy(step = previous)
    }

    /**
     * Enregistre le profil, la pesée et le **premier** objectif.
     *
     * Dans cet ordre, et c'est le même raisonnement que pour `LogDish` : l'objectif
     * découle d'un profil et d'une pesée, et les écrire après lui laisserait un
     * instant pendant lequel l'accueil aurait un objectif sans savoir d'où il vient.
     */
    fun onFinish(onDone: () -> Unit) {
        val answers = state.value.answers
        val plan = state.value.plan ?: return
        state.update { it.copy(saving = true, failed = false) }

        viewModelScope.launch {
            val outcome = runCatching {
                profiles.save(answers.toProfile(clock.today()))
                weights.record(WeightEntry(clock.today(), answers.weightOrDefault()))
                goals.replace(
                    Goal(
                        id = GoalId(ids.next()),
                        startedAt = clock.today(),
                        origin = GoalOrigin.CALCULATED,
                        strategy = answers.strategy ?: GoalStrategy.MAINTAIN,
                        targetWeightKg = answers.targetWeightKg,
                        targetDate = answers.targetDate,
                        daily = plan.goal,
                    ),
                )
            }
            state.update { it.copy(saving = false, failed = outcome.isFailure) }
            if (outcome.isSuccess) onDone()
        }
    }

    private fun OnboardingUiState.recalculated(): OnboardingUiState =
        copy(plan = calculate(answers.toProfile(clock.today()), answers.toRequest()))
}

/**
 * Les valeurs des étapes sautées.
 *
 * [docs/02][parcours] les demande « raisonnables », et elles le sont dans un sens
 * précis : chacune est le choix le **moins engageant**. Le niveau d'activité le plus
 * bas sous-estime la dépense plutôt que de la surestimer ; un sexe non précisé applique
 * la moyenne des deux formules. Elles ne sont jamais pré-remplies dans les champs — un
 * chiffre affiché serait accepté par distraction, et l'objectif calculé serait celui de
 * quelqu'un d'autre.
 *
 * Hors de la classe : ce sont des conversions, pas des décisions de l'écran.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
internal fun OnboardingAnswers.toProfile(today: LocalDate) = UserProfile(
    birthDate = birthDate ?: today.minusYears(DEFAULT_AGE),
    sex = sex ?: Sex.UNSPECIFIED,
    heightCm = heightCm ?: DEFAULT_HEIGHT_CM,
    activity = Activity(work = work ?: WorkActivity.DESK, sessions = sessions),
)

internal fun OnboardingAnswers.toRequest() = GoalRequest(
    strategy = strategy ?: GoalStrategy.MAINTAIN,
    currentWeightKg = weightOrDefault(),
    targetWeightKg = targetWeightKg,
    targetDate = targetDate,
)

internal fun OnboardingAnswers.weightOrDefault() = currentWeightKg ?: DEFAULT_WEIGHT_KG

private const val DEFAULT_AGE = 30L
private const val DEFAULT_HEIGHT_CM = 170.0
private const val DEFAULT_WEIGHT_KG = 70.0
