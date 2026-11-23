package app.hexavore.feature.progress

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProgressStore
import app.hexavore.domain.progress.Progress
import app.hexavore.domain.progress.StoredProgress
import app.hexavore.domain.usecase.ObserveProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** L'écran de progression montre ce que le domaine compte, et rien avant. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProgressViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `avant la premiere lecture, rien n'est compte`() = runTest(dispatcher) {
        assertEquals(Progress.NONE, modele(StoredProgress()).uiState.value)
    }

    @Test
    fun `les points ranges s'affichent`() = runTest(dispatcher) {
        val modele = modele(StoredProgress(points = 5_000, bestStreak = 12))
        backgroundScope.launch { modele.uiState.collect { } }

        assertEquals(5_000L, modele.uiState.value.points)
        assertEquals(12, modele.uiState.value.tally.bestStreak)
    }

    private fun modele(stored: StoredProgress) = ProgressViewModel(
        ObserveProgress(
            InMemoryDiaryRepository(),
            InMemoryGoals(),
            InMemoryProgressStore(stored),
            FixedClock.atNoon(LocalDate.of(2026, 3, 15)),
        ),
    )
}
