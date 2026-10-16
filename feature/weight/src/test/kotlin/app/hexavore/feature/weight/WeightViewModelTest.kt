package app.hexavore.feature.weight

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAdjustmentSettings
import app.hexavore.core.testing.InMemoryAppearanceSettings
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.usecase.DeleteWeight
import app.hexavore.domain.usecase.GetWeightTrend
import app.hexavore.domain.usecase.ObserveHeight
import app.hexavore.domain.usecase.ObserveUnitSystem
import app.hexavore.domain.usecase.ObserveWeightTrendShown
import app.hexavore.domain.usecase.RecordWeight
import app.hexavore.domain.usecase.RespondToAdjustment
import app.hexavore.domain.usecase.RestoreWeight
import app.hexavore.domain.usecase.ShowWeightTrend
import app.hexavore.domain.usecase.SuggestGoalAdjustment
import app.hexavore.domain.usecase.TrendPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Retirer une pesée depuis le journal, et la rendre ([D165][decisions]).
 *
 * L'écran n'est pas composé ici — il n'y a pas d'émulateur. Ce qui l'est, c'est ce dont
 * la barre d'annulation dépend : la pesée part tout de suite, l'annulation la rend telle
 * qu'elle était, et la fenêtre passée, il n'y a plus rien à rendre.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WeightViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val weights = InMemoryWeightLog(listOf(VEILLE, FAUSSE))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `supprimer retire la pesee tout de suite, et la garde pour l annuler`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onDelete(FAUSSE.asPoint())

        assertEquals(listOf(VEILLE), weights.entries, "partie avant la fin de la barre, pas apres")
        assertEquals(FAUSSE, viewModel.undoable.value)
    }

    @Test
    fun `annuler rend la pesee telle qu elle etait`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onDelete(FAUSSE.asPoint())

        viewModel.onUndo()

        assertEquals(listOf(FAUSSE, VEILLE), weights.entries)
        assertNull(viewModel.undoable.value, "une annulation ne se rejoue pas")
    }

    @Test
    fun `la fenetre passee, il n y a plus rien a rendre`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onDelete(FAUSSE.asPoint())

        viewModel.onUndoExpired()
        viewModel.onUndo()

        assertEquals(listOf(VEILLE), weights.entries)
    }

    private fun viewModel(): WeightViewModel {
        val clock = FixedClock.atNoon(AUJOURD_HUI)
        val profiles = InMemoryProfiles()
        val goals = InMemoryGoals()
        val adjustment = InMemoryAdjustmentSettings()
        val appearance = InMemoryAppearanceSettings()
        return WeightViewModel(
            getWeightTrend = GetWeightTrend(weights, goals),
            readings = ProfileReadings(ObserveUnitSystem(profiles), ObserveHeight(profiles)),
            suggestGoalAdjustment = SuggestGoalAdjustment(
                weights,
                InMemoryDiaryRepository(),
                goals,
                profiles,
                adjustment,
                clock,
            ),
            gestures = WeighingGestures(
                record = RecordWeight(weights, profiles, clock),
                delete = DeleteWeight(weights),
                restore = RestoreWeight(weights),
            ),
            respondToAdjustment = RespondToAdjustment(goals, adjustment, SequentialIdGenerator(), clock),
            clock = clock,
            trendDisplay = TrendDisplay(ObserveWeightTrendShown(appearance), ShowWeightTrend(appearance)),
        )
    }

    private fun WeightEntry.asPoint() = TrendPoint(date, weightKg, weightKg)

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 10, 16)

        /** La pesée de la veille, juste. */
        val VEILLE = WeightEntry(AUJOURD_HUI.minusDays(1), 82.4)

        /** Une pesée tapée de travers : la plus récente, donc celle qui sert au calcul. */
        val FAUSSE = WeightEntry(AUJOURD_HUI, 28.4)
    }
}
