package app.hexavore.feature.home

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.SampleDiary
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.usecase.GetCalendar
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
import java.time.YearMonth

/**
 * Le calendrier lit trois mois : celui qu'on regarde, et ses deux voisins.
 *
 * Les voisins sont lus d'avance pour qu'un glissement montre ses pastilles tout de
 * suite, et non après une lecture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CalendarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val diary = InMemoryDiaryRepository()
    private val clock = FixedClock.atNoon(TODAY)

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `le mois courant et ses voisins portent leurs jours notes`() = runTest(dispatcher) {
        val voisin = TODAY.minusMonths(1).withDayOfMonth(1)
        val loin = TODAY.minusMonths(2)
        diary.setContent(jour(TODAY) + jour(voisin) + jour(loin))
        val modele = CalendarViewModel(GetCalendar(diary, InMemoryGoals()), clock)
        backgroundScope.launch { modele.uiState.collect { } }

        assertEquals(TODAY, modele.uiState.value.today)
        assertEquals(setOf(TODAY, voisin), modele.uiState.value.days.keys, "deux mois plus tot est hors de la fenetre")
    }

    @Test
    fun `glisser vers un mois deplace la fenetre`() = runTest(dispatcher) {
        val loin = TODAY.minusMonths(2)
        diary.setContent(jour(loin))
        val modele = CalendarViewModel(GetCalendar(diary, InMemoryGoals()), clock)
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onVisibleMonth(YearMonth.from(loin).plusMonths(1))

        assertEquals(setOf(loin), modele.uiState.value.days.keys)
    }

    @Test
    fun `une lecture qui echoue laisse un calendrier vide`() = runTest(dispatcher) {
        diary.failure = IllegalStateException("base illisible")
        val modele = CalendarViewModel(GetCalendar(diary, InMemoryGoals()), clock)
        backgroundScope.launch { modele.uiState.collect { } }

        assertEquals(emptyMap<LocalDate, Any>(), modele.uiState.value.days)
    }

    private fun jour(date: LocalDate) = SampleDiary.day(date).map { it.copy(id = DishId("$date-${it.id.value}")) }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 15)
    }
}
