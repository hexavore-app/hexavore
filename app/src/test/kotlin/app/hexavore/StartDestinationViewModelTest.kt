package app.hexavore

import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
 * Le premier écran : l'onboarding pour qui n'a pas d'objectif, l'accueil sinon.
 *
 * **Une base illisible mène à l'accueil**, jamais à l'onboarding : le refaire
 * écrirait un objectif neuf par-dessus celui qu'on n'a simplement pas su lire.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StartDestinationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `rien ne s'ouvre avant d'avoir lu`() {
        assertNull(StartDestinationViewModel(InMemoryGoals()).destination.value)
    }

    @Test
    fun `sans objectif, l'onboarding`() = runTest(dispatcher) {
        assertEquals(StartDestination.ONBOARDING, destination(InMemoryGoals()))
    }

    @Test
    fun `avec un objectif, l'accueil`() = runTest(dispatcher) {
        assertEquals(StartDestination.HOME, destination(InMemoryGoals(listOf(GOAL))))
    }

    @Test
    fun `une base illisible mene a l'accueil`() = runTest(dispatcher) {
        assertEquals(StartDestination.HOME, destination(InMemoryGoals(failure = true)))
    }

    private fun TestScope.destination(goals: InMemoryGoals): StartDestination? {
        val modele = StartDestinationViewModel(goals)
        backgroundScope.launch { modele.destination.collect { } }
        return modele.destination.value
    }

    private companion object {
        val GOAL = Goal(
            id = GoalId("objectif"),
            startedAt = LocalDate.of(2026, 1, 1),
            origin = GoalOrigin.CALCULATED,
            strategy = GoalStrategy.MAINTAIN,
            daily = DailyGoal(kcal = 2000.0, protein = 120.0, carbs = 220.0, sugars = 50.0, fat = 67.0, fiber = 30.0),
        )
    }
}
