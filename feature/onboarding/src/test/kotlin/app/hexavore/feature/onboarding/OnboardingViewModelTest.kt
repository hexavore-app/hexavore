package app.hexavore.feature.onboarding

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryReminderSettings
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.goal.GoalHorizon
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.goal.TargetProblem
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
    private val rappels = InMemoryReminderSettings()

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

    // --- Les bornes du corps (D160) ---------------------------------------------

    @Test
    fun `une taille de 1 cm bloque l etape, et dit laquelle`() = runTest(dispatcher) {
        // Rien ne bornait la saisie : 1 cm entrait tel quel dans Mifflin-St Jeor.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES.copy(heightCm = 1.0))

        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingStep.ABOUT_YOU, viewModel.uiState.value.step)
        assertEquals(OnboardingBlocker.HEIGHT, viewModel.uiState.value.blocker)
    }

    @Test
    fun `un poids qui ne va pas avec la taille bloque l etape`() = runTest(dispatcher) {
        // 182 cm et 300 kg : chacun passe seul, leur IMC de 90 non.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES.copy(currentWeightKg = 300.0))

        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingStep.ABOUT_YOU, viewModel.uiState.value.step)
        assertEquals(OnboardingBlocker.WEIGHT, viewModel.uiState.value.blocker)
    }

    @Test
    fun `une absence se dit avant une invraisemblance`() = runTest(dispatcher) {
        // « Renseignez votre taille » dit mieux ce qu'il faut faire qu'une plage de poids
        // jugee sur une taille qui n'existe pas.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES.copy(heightCm = null, currentWeightKg = 999.0))
        viewModel.onNext()

        assertEquals(OnboardingBlocker.IDENTITY, viewModel.uiState.value.blocker)
    }

    @Test
    fun `perdre vers la maigreur est refuse, chiffre a l appui`() = runTest(dispatcher) {
        // 182 cm : le seuil de la maigreur est a 62 kg.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES.copy(targetWeightKg = 55.0))

        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingStep.OBJECTIVE, viewModel.uiState.value.step)
        assertEquals(OnboardingBlocker.TARGET, viewModel.uiState.value.blocker)
        assertEquals(TargetProblem.BelowLean(182.0, 62.0), viewModel.uiState.value.answers.targetProblem)
    }

    @Test
    fun `perdre vers un poids plus haut est refuse`() = runTest(dispatcher) {
        // Le calcul en faisait une prise, sous l'etiquette « perdre ».
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES.copy(targetWeightKg = 95.0))

        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingStep.OBJECTIVE, viewModel.uiState.value.step)
        assertEquals(TargetProblem.NotBelowCurrent(88.0), viewModel.uiState.value.answers.targetProblem)
    }

    @Test
    fun `deja au seuil de la maigreur, perdre se refuse avant meme un poids vise`() = runTest(dispatcher) {
        // Lui demander « un poids cible et une echeance » l'enverrait remplir un champ
        // qui ne pourrait jamais aboutir.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES.copy(currentWeightKg = 60.0, targetWeightKg = null, targetDate = null))

        repeat(OnboardingStep.COUNT) { viewModel.onNext() }

        assertEquals(OnboardingBlocker.TARGET, viewModel.uiState.value.blocker, "et non OBJECTIVE")
    }

    @Test
    fun `sous le plancher, se caler sur la date proposee leve enfin le garde-fou`() = runTest(dispatcher) {
        // Femme de 80 ans, 145 cm, 45 kg : 954 kcal de depense, sous le plancher de
        // 1 200. Le plancher portait la prise au-dela des bornes de vitesse, et la date
        // proposee ne levait jamais l'alerte : c'est ce qui « bloquait » (D160).
        val viewModel = viewModel()
        viewModel.onAnswers(
            PETITE.copy(strategy = GoalStrategy.GAIN, targetWeightKg = 50.0, targetDate = AUJOURD_HUI.plusMonths(3)),
        )
        val plan = viewModel.uiState.value.plan!!
        assertTrue(plan.capped)
        assertNotNull(plan.reachableOn)

        viewModel.onUseReachableDate()

        assertFalse(viewModel.uiState.value.plan!!.capped, "se caler sur la date proposee doit lever le garde-fou")
    }

    @Test
    fun `sous le plancher, une perte reste a la depense, et le plan dit pourquoi`() = runTest(dispatcher) {
        // Elle devenait une prise de 246 kcal par jour, sous l'etiquette « perdre ».
        val viewModel = viewModel()
        viewModel.onAnswers(PETITE.copy(strategy = GoalStrategy.LOSE, targetWeightKg = 42.0))

        val plan = viewModel.uiState.value.plan!!

        assertEquals(1_200.0, plan.withholdingFloorKcal)
        assertNull(plan.reachableOn, "aucune date ne rend ce deficit possible")
        assertTrue(plan.goal.kcal < 1_200.0, "l'objectif reste la depense, pas le plancher")
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
    fun `terminer note que la question des notifications a ete posee`() = runTest(dispatcher) {
        // C'est elle qui mene ici. Sans cette trace, l'accueil la reposait aussitot,
        // juste apres un « Ne pas autoriser » (D162).
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES)

        viewModel.onFinish { }
        advanceUntilIdle()

        assertTrue(rappels.asked)
    }

    @Test
    fun `la question reste notee meme quand l ecriture echoue`() = runTest(dispatcher) {
        // Elle a ete posee, qu'on reessaie d'enregistrer ou non.
        val viewModel = viewModel()
        viewModel.onAnswers(REPONSES)
        goals.failure = true

        viewModel.onFinish { }
        advanceUntilIdle()

        assertTrue(rappels.asked)
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
        reminders = rappels,
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

        /** Femme de 80 ans, 145 cm, 45 kg, assise : une dépense sous le plancher de 1 200 kcal. */
        val PETITE = REPONSES.copy(
            birthDate = LocalDate.of(1946, 3, 4),
            sex = Sex.FEMALE,
            heightCm = 145.0,
            currentWeightKg = 45.0,
            work = WorkActivity.DESK,
            sessions = WeeklySessions.NONE,
        )
    }
}
