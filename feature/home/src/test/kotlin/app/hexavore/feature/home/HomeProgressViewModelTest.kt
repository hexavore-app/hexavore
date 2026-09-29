package app.hexavore.feature.home

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProgressStore
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.nutrition.Macros
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.usecase.AdvanceProgress
import app.hexavore.domain.usecase.ObserveProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * La progression vue de l'accueil, et **le palier qui ne se fête qu'une fois**.
 *
 * C'est le seul cas qui vaille ici, et il vient de l'usage : l'animation se rejouait à
 * chaque aller-retour vers l'écran de progression ([D135][decisions]). Le flux s'arrête
 * quand on quitte l'accueil et repart quand on y revient ; sa première émission
 * repassait par la file, et la gerbe recommençait pour un palier obtenu la semaine
 * d'avant.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeProgressViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = FixedClock.atNoon(TODAY)
    private val store = InMemoryProgressStore()
    private val diary = InMemoryDiaryRepository(dishesOn(TODAY, count = 10))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `un palier franchi se fete`() = runTest {
        val viewModel = viewModel()
        val observation = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertTrue(Badge.DISHES_10 in viewModel.celebrating.value)
        observation.cancel()
    }

    @Test
    fun `un palier deja fete ne revient pas quand l ecran est rouvert`() = runTest {
        // Ce qui se passe en allant voir l'ecran de progression et en revenant : le flux
        // s'arrete faute d'observateur, puis repart avec une premiere emission.
        val viewModel = viewModel()
        val premiere = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onCelebrated()
        premiere.cancel()
        advanceUntilIdle()

        val seconde = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(emptyList<Badge>(), viewModel.celebrating.value)
        seconde.cancel()
    }

    @Test
    fun `une animation interrompue ne se rejoue pas non plus`() = runTest {
        // Le cas exact du defaut : on navigue **pendant** la gerbe, donc `onCelebrated`
        // n'est jamais appele. La file ne doit pas se remplir une seconde fois pour
        // autant -- ce qui compte est qu'un palier ne passe qu'une fois.
        val viewModel = viewModel()
        val premiere = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val avant = viewModel.celebrating.value
        premiere.cancel()
        advanceUntilIdle()

        val seconde = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(avant, viewModel.celebrating.value, "la file ne doit pas s'allonger")
        seconde.cancel()
    }

    private fun viewModel() = HomeProgressViewModel(
        observeProgress = ObserveProgress(diary, InMemoryGoals(), store, clock),
        advanceProgress = AdvanceProgress(store, clock),
    )

    private fun dishesOn(date: LocalDate, count: Int): List<Dish> = List(count) { index ->
        Dish(
            id = DishId("plat-$index"),
            date = date,
            source = EntrySource.MANUAL,
            loggedAt = date.atTime(12, index % 60).toInstant(ZoneOffset.UTC),
            entries = listOf(
                FoodEntry(
                    id = EntryId("ligne-$index"),
                    dishId = DishId("plat-$index"),
                    displayName = "Aliment",
                    quantity = 100.0,
                    unit = "g",
                    grams = 100.0,
                    macros = Macros(10.0, 1.0, 2.0, 0.5, 0.5, 1.0),
                ),
            ),
        )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 29)
    }
}
