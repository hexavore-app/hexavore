package app.hexavore.feature.settings

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.usecase.CalculateDailyGoal
import app.hexavore.domain.usecase.GoalRequest
import app.hexavore.domain.usecase.ReviseGoal
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * L'écran des réglages profil : ce qu'il relit, ce qu'il recalcule, ce qu'il écrit.
 *
 * L'écran lui-même n'est pas éprouvé ici — il n'y a pas d'émulateur. Ce qui l'est, c'est
 * ce dont il dépend et qui casserait en silence : on **consulte** avant de modifier, un
 * objectif saisi à la main ne bouge pas quand le profil change, et une correction qui
 * déplace les six chiffres ne s'écrit pas sans avoir été montrée.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = FixedClock.atNoon(AUJOURD_HUI)
    private val profiles = InMemoryProfiles(PROFIL)
    private val weights = InMemoryWeightLog(listOf(WeightEntry(AUJOURD_HUI.minusDays(3), POIDS)))
    private val calculate = CalculateDailyGoal(clock)
    private var goals = InMemoryGoals()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `l ecran ouvre en consultation, sur ce qui est enregistre`() = runTest(dispatcher) {
        // On relit avant de corriger. Un ecran de reglages en edition permanente
        // invite a modifier ce qu'on venait seulement verifier (D60).
        val viewModel = viewModel(objectifCourant())
        val state = viewModel.uiState.value

        assertTrue(state.loaded)
        assertFalse(state.editing, "l ecran ouvre en consultation")
        assertEquals(PROFIL.birthDate, state.form.birthDate)
        assertEquals(POIDS, state.form.currentWeightKg, "le poids vient du journal de pesees, pas du profil")
        assertEquals(2_525.0, state.daily?.kcal, "l exemple de reference de docs/03")
    }

    @Test
    fun `le crayon est la seule porte vers la modification`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())

        viewModel.onEdit()

        assertTrue(viewModel.uiState.value.editing)
    }

    @Test
    fun `un objectif saisi a la main rouvre en manuel, avec ses six chiffres`() = runTest(dispatcher) {
        // L'aller-retour de `origin` en base : c'est lui qui decide du mode, et sans
        // lui l'ecran rouvrirait en calcule et proposerait de tout recalculer.
        val viewModel = viewModel(objectifManuel())
        val state = viewModel.uiState.value

        assertTrue(state.form.manual)
        assertEquals(SAISI_A_LA_MAIN, state.daily)
    }

    @Test
    fun `en saisie manuelle, corriger le profil ne bouge aucun compteur`() = runTest(dispatcher) {
        // Le coeur de D60, vu de l'ecran. La taille change, donc la depense change,
        // donc le plan change -- et les six chiffres, eux, ne bougent pas d'un gramme.
        val viewModel = viewModel(objectifManuel())
        viewModel.onEdit()
        val avant = viewModel.uiState.value.plan?.goal

        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))

        val apres = viewModel.uiState.value
        assertEquals(SAISI_A_LA_MAIN, apres.daily, "un objectif manuel n est le resultat d aucun calcul")
        assertNotEquals(avant, apres.plan?.goal, "le calcul, lui, a bien suivi le profil corrige")
    }

    @Test
    fun `en objectif calcule, corriger le profil deplace les six compteurs`() = runTest(dispatcher) {
        // La contrepartie, et elle compte autant : un mode manuel qui figerait aussi
        // le mode calcule passerait le test d'a cote sans rien protoger.
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()
        val avant = viewModel.uiState.value.daily

        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))

        assertNotEquals(avant, viewModel.uiState.value.daily)
    }

    @Test
    fun `basculer en manuel part des chiffres affiches, revenir au calcul les jette`() = runTest(dispatcher) {
        // Ce qu'on veut corriger est presque toujours ce qui est la, de quelques
        // grammes. Et garder la saisie apres un retour au calcul la ferait
        // reapparaitre au prochain aller-retour, sans que rien ne dise d'ou elle sort.
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()
        val propose = viewModel.uiState.value.plan?.goal

        viewModel.onManual(true)

        assertEquals(propose, viewModel.uiState.value.daily, "la saisie part du chiffre propose")

        viewModel.onMacroChange(Macro.PROTEIN, 150.0)
        viewModel.onManual(false)

        assertTrue(viewModel.uiState.value.form.macros.isEmpty())
        assertEquals(propose, viewModel.uiState.value.daily, "le calcul a repris la main")
    }

    @Test
    fun `un compteur vide refuse l enregistrement, et dit lequel`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifManuel())
        viewModel.onEdit()

        viewModel.onMacroChange(Macro.PROTEIN, null)

        assertEquals(ProfileBlocker.EMPTY_MACRO, viewModel.uiState.value.blocker)
        assertFalse(viewModel.uiState.value.canSave)
        assertNull(viewModel.uiState.value.daily)
    }

    @Test
    fun `en manuel, le poids cible et l echeance ne sont plus exiges`() = runTest(dispatcher) {
        // Ils restent modifiables et enregistres -- le journal de poids en tirera sa
        // trajectoire -- mais ils ne pilotent plus les six chiffres. Exiger une date
        // qui ne sert a rien serait exiger pour la forme.
        val viewModel = viewModel(objectifManuel())
        viewModel.onEdit()

        viewModel.onForm(viewModel.uiState.value.form.copy(targetDate = null))

        assertNull(viewModel.uiState.value.blocker)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `en objectif calcule, l echeance reste exigee`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()

        viewModel.onForm(viewModel.uiState.value.form.copy(targetDate = null))

        assertEquals(ProfileBlocker.HORIZON, viewModel.uiState.value.blocker)
    }

    @Test
    fun `enregistrer demande confirmation quand les six chiffres changent`() = runTest(dispatcher) {
        // Corriger sa taille de quatre centimetres deplace un objectif quotidien. Ca
        // ne se decouvre pas sur l'accueil le lendemain.
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()
        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))
        var referme = false

        viewModel.onSave { referme = true }
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pending, "la correction attend d avoir ete montree")
        assertFalse(referme)
        assertEquals(1, goals.all.size, "rien n est ecrit avant la confirmation")
    }

    @Test
    fun `confirmer ouvre une nouvelle version et referme l ecran`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()
        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))
        viewModel.onSave { }
        var referme = false

        viewModel.onConfirm { referme = true }
        advanceUntilIdle()

        assertTrue(referme)
        assertNull(viewModel.uiState.value.pending)
        assertEquals(2, goals.all.size, "D04 : une ligne de plus, jamais celle qui court modifiee")
        assertEquals(178.0, profiles.saved?.heightCm)
    }

    @Test
    fun `renoncer a la confirmation garde les corrections et n ecrit rien`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()
        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))
        viewModel.onSave { }

        viewModel.onDismissConfirmation()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pending)
        assertEquals(178.0, viewModel.uiState.value.form.heightCm, "la correction est conservee telle quelle")
        assertEquals(1, goals.all.size)
    }

    @Test
    fun `basculer en manuel a chiffres identiques enregistre sans confirmation`() = runTest(dispatcher) {
        // Les six chiffres ne bougent pas : il n'y a rien a montrer, et un dialogue
        // qui repete ce qu'on vient de lire s'apprend a fermer sans le lire. Le
        // changement de mode, lui, est bien une nouvelle version.
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()
        viewModel.onManual(true)
        var referme = false

        viewModel.onSave { referme = true }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pending, "aucun chiffre ne change, donc rien a annoncer")
        assertTrue(referme)
        assertEquals(2, goals.all.size)
        assertEquals(GoalOrigin.MANUAL, goals.all.single { it.active }.origin)
    }

    @Test
    fun `un echec de lecture se dit au lieu d afficher un formulaire vide`() = runTest(dispatcher) {
        // Un formulaire vide enregistre ecraserait le profil qui est en base par des
        // champs que personne n'a saisis. C'est plus qu'un mensonge d'affichage.
        val viewModel = viewModel(objectifCourant(), unreadable = true)

        assertTrue(viewModel.uiState.value.unreadable)
        assertNull(viewModel.uiState.value.form.birthDate)
    }

    @Test
    fun `un echec d ecriture se dit et conserve les corrections`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())
        viewModel.onEdit()
        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))
        viewModel.onSave { }
        goals.failure = true
        var referme = false

        viewModel.onConfirm { referme = true }
        advanceUntilIdle()

        assertFalse(referme, "l ecran ne se referme pas sur un echec")
        assertTrue(viewModel.uiState.value.failed)
        assertEquals(178.0, viewModel.uiState.value.form.heightCm)
    }

    // --- Montage -----------------------------------------------------------------

    private fun viewModel(initial: Goal, unreadable: Boolean = false): ProfileViewModel {
        goals = InMemoryGoals(listOf(initial), failure = unreadable)
        return ProfileViewModel(
            profiles = profiles,
            weights = weights,
            goals = goals,
            calculate = calculate,
            reviseGoal = ReviseGoal(profiles, weights, goals, clock, SequentialIdGenerator("goal")),
            clock = clock,
        )
    }

    private fun objectifCourant() = objectif(GoalOrigin.CALCULATED, calculate(PROFIL, DEMANDE).goal)

    private fun objectifManuel() = objectif(GoalOrigin.MANUAL, SAISI_A_LA_MAIN)

    private fun objectif(origin: GoalOrigin, daily: DailyGoal) = Goal(
        id = GoalId("goal-initial"),
        startedAt = AUJOURD_HUI.minusMonths(1),
        origin = origin,
        strategy = DEMANDE.strategy,
        targetWeightKg = DEMANDE.targetWeightKg,
        targetDate = DEMANDE.targetDate,
        daily = daily,
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)
        const val POIDS = 88.0

        /** L'exemple de docs/03 : homme, 35 ans, 182 cm, 88 kg, 80 kg en 6 mois. */
        val PROFIL = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 182.0,
            activity = Activity(WorkActivity.ON_FEET, WeeklySessions(3)),
        )

        val DEMANDE = GoalRequest(
            strategy = GoalStrategy.LOSE,
            currentWeightKg = POIDS,
            targetWeightKg = 80.0,
            targetDate = AUJOURD_HUI.plusDays(182),
        )

        /** Six chiffres ronds, que le calcul ne produirait pour personne. */
        val SAISI_A_LA_MAIN = DailyGoal(
            kcal = 2_000.0,
            protein = 150.0,
            carbs = 200.0,
            sugars = 40.0,
            fat = 60.0,
            fiber = 30.0,
        )
    }
}
