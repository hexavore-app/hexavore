package app.hexavore.domain.goal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Les trois échéances, et la pastille qu'une date rallume. */
class GoalHorizonTest {
    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun `chaque horizon tombe le meme jour, des mois plus tard`() {
        assertEquals(LocalDate.of(2026, 6, 15), GoalHorizon.THREE_MONTHS.dateFrom(today))
        assertEquals(LocalDate.of(2026, 9, 15), GoalHorizon.SIX_MONTHS.dateFrom(today))
        assertEquals(LocalDate.of(2027, 3, 15), GoalHorizon.TWELVE_MONTHS.dateFrom(today))
    }

    @Test
    fun `une date rallume la pastille qui la porte`() {
        GoalHorizon.entries.forEach { assertEquals(it, GoalHorizon.of(today, it.dateFrom(today))) }
    }

    @Test
    fun `une date libre ou absente ne rallume rien`() {
        assertNull(GoalHorizon.of(today, today.plusMonths(4)))
        assertNull(GoalHorizon.of(today, null))
    }
}
