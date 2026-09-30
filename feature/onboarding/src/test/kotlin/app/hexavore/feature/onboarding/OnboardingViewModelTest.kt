package app.hexavore.feature.onboarding

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.goal.GoalHorizon
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.usecase.CalculateDailyGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Les cinq étapes, et ce qu'elles écrivent.
 *
 * L'écran n'est pas éprouvé ici — il n'y a pas d'émulateur. Ce qui l'est, c'est ce
 * dont l'écran dépend et qui casserait en silence : l'ordre des étapes, le fait que
 * **rien n'est écrit avant la fin**, et le fait qu'un objectif de maintien n'emporte ni
 * poids cible ni échéance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = FixedClock.atNoon(AUJOURD_HUI)
    private val profiles = InMemoryProfiles()
    private val weights = InMemoryWeightLog()
    private val goals = InMemoryGoals()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `la premiere etape est bloquante tant que l avertissement n est pas accepte`() = runTest(dispatcher) {
        // La seule des cinq qui le soit : les quatre autres se sautent, parce qu'un
        // utilisateur presse doit pouvoir arriver a l'accueil.
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.canContinue)
        viewModel.onNext()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.step, "l etape a ete franchie sans accord")

        viewModel.onAnswers(OnboardingAnswers(disclaimerAccepted = true))
        viewModel.onNext()

        assertEquals(OnboardingStep.ABOUT_YOU, viewModel.uiState.value.step)
    }

    @Test
    fun `chaque etape exige ses champs`() = runTest(dispatcher) {
        // D56 : le « Passer » de docs/02 disparait. Un objectif calcule sur des
        // valeurs par defaut est l'objectif de quelqu'un d'autre, affiche avec
        // l'autorite d'un chiffre personnel.
        val viewModel = viewModel()
        viewModel.onAnswers(OnboardingAnswers(disclaimerAccepted = true))

        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingStep.ABOUT_YOU, viewModel.uiState.value.step, "l identite n est pas remplie")
        assertEquals(OnboardingBlocker.IDENTITY, viewModel.uiState.value.blocker)
    }

    @Test
    fun `l identite complete debloque l etape suivante, et ainsi de suite`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAnswers(REPONSES.copy(work = null, strategy = null))
        viewModel.onNext()
        viewModel.onNext()

        assertEquals(OnboardingStep.ACTIVITY, viewModel.uiState.value.step)
        assertEquals(OnboardingBlocker.ACTIVITY, viewModel.uiState.value.blocker, "un niveau reste a choisir")

        viewModel.onAnswers(REPONSES.copy(strategy = null))
        viewModel.onNext()

        assertEquals(OnboardingStep.OBJECTIVE, viewModel.uiState.value.step)
        assertEquals(OnboardingBlocker.OBJECTIVE, viewModel.uiState.value.blocker)
    }

    @Test
    fun `toutes les reponses menent au resultat`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES)

        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingStep.RESULT, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.blocker)
        assertNotNull(viewModel.uiState.value.plan)
    }

    @Test
    fun `un objectif de maintien n exige ni poids cible ni echeance`() = runTest(dispatcher) {
        // Il n'y a rien a atteindre : exiger une echeance ferait bloquer sur un champ
        // que la strategie n'affiche meme pas.
        val viewModel = viewModel()

        viewModel.onAnswers(REPONSES.copy(strategy = GoalStrategy.MAINTAIN))
        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingStep.RESULT, viewModel.uiState.value.step)
    }

    @Test
    fun `rien n est ecrit avant la derniere etape`() = runTest(dispatcher) {
        // Un onboarding s'abandonne. Un profil ecrit au fil de l'eau ferait croire a
        // l'accueil qu'un objectif existe.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES)
        advanceUntilIdle()

        assertNull(profiles.saved)
        assertTrue(goals.all.isEmpty())
        assertNull(weights.latest)
    }

    @Test
    fun `terminer ecrit le profil, la pesee et le premier objectif`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES)
        var termine = false

        viewModel.onFinish { termine = true }
        advanceUntilIdle()

        assertTrue(termine)
        assertEquals(Sex.MALE, profiles.saved?.sex)
        assertEquals(88.0, weights.latest?.weightKg)
        assertEquals(AUJOURD_HUI, weights.latest?.date, "la pesee est datee par l horloge, pas par LocalDate.now()")

        val objectif = goals.all.single()
        assertEquals(GoalOrigin.CALCULATED, objectif.origin)
        assertEquals(AUJOURD_HUI, objectif.startedAt)
        assertTrue(objectif.active, "le premier objectif court")
        assertEquals(2_525.0, objectif.daily.kcal, "l exemple de reference de docs/03")
    }

    @Test
    fun `choisir le maintien efface le poids cible et l echeance`() = runTest(dispatcher) {
        // Les garder ferait calculer un ecart calorique pour une strategie qui n'en
        // veut pas : l'utilisateur verrait un deficit sous une etiquette « maintien ».
        val viewModel = viewModel()

        viewModel.onAnswers(REPONSES.copy(strategy = GoalStrategy.MAINTAIN))

        val reponses = viewModel.uiState.value.answers
        assertNull(reponses.targetWeightKg)
        assertNull(reponses.targetDate)
        assertEquals(2_864.0, viewModel.uiState.value.plan?.goal?.kcal, "un maintien couvre la depense, sans ecart")
    }

    @Test
    fun `une echeance intenable propose une date atteignable`() = runTest(dispatcher) {
        // L'application ne refuse jamais : elle recalcule et propose.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES.copy(targetDate = AUJOURD_HUI.plusDays(30)))

        val plan = viewModel.uiState.value.plan!!
        assertTrue(plan.capped, "8 kg en 30 jours devrait mordre")
        assertNotNull(plan.reachableOn)

        viewModel.onUseReachableDate()

        assertEquals(plan.reachableOn, viewModel.uiState.value.answers.targetDate)
        assertFalse(viewModel.uiState.value.plan!!.capped, "se caler sur la date proposee doit lever le garde-fou")
    }

    @Test
    fun `les trois echeances proposees sont des dates, pas un calendrier`() = runTest(dispatcher) {
        // D56 : trois pastilles remplacent la saisie d'une date. Ce test tient la
        // correspondance entre une pastille et la date qu'elle pose -- sans quoi
        // « dans 6 mois » pourrait en poser trois.
        assertEquals(AUJOURD_HUI.plusMonths(3), GoalHorizon.THREE_MONTHS.dateFrom(AUJOURD_HUI))
        assertEquals(AUJOURD_HUI.plusMonths(6), GoalHorizon.SIX_MONTHS.dateFrom(AUJOURD_HUI))
        assertEquals(AUJOURD_HUI.plusMonths(12), GoalHorizon.TWELVE_MONTHS.dateFrom(AUJOURD_HUI))

        assertEquals(GoalHorizon.SIX_MONTHS, GoalHorizon.of(AUJOURD_HUI, AUJOURD_HUI.plusMonths(6)))
        assertNull(
            GoalHorizon.of(AUJOURD_HUI, AUJOURD_HUI.plusDays(103)),
            "une date atteignable ne correspond a aucune pastille, et elle a la sienne",
        )
    }

    @Test
    fun `un echec d ecriture se dit et conserve les reponses`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES)
        goals.failure = true

        viewModel.onFinish { }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.failed)
        assertEquals(REPONSES.currentWeightKg, viewModel.uiState.value.answers.currentWeightKg)
    }

    private fun viewModel() = OnboardingViewModel(
        profiles = profiles,
        weights = weights,
        goals = goals,
        calculate = CalculateDailyGoal(clock),
        clock = clock,
        ids = SequentialIdGenerator("goal"),
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)

        /** L'exemple de docs/03 : homme, 35 ans, 182 cm, 88 kg, 80 kg en 6 mois. */
        val REPONSES = OnboardingAnswers(
            disclaimerAccepted = true,
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 182.0,
            currentWeightKg = 88.0,
            work = WorkActivity.ON_FEET,
            sessions = WeeklySessions(3),
            strategy = GoalStrategy.LOSE,
            targetWeightKg = 80.0,
            targetDate = AUJOURD_HUI.plusDays(182),
        )
    }
}
