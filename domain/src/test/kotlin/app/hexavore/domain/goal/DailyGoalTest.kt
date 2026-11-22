package app.hexavore.domain.goal

import app.hexavore.domain.nutrition.Macro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Un objectif manuel se construit compteur par compteur, sans toucher aux autres. */
class DailyGoalTest {
    private val goal =
        DailyGoal(kcal = 2_000.0, protein = 120.0, carbs = 220.0, sugars = 50.0, fat = 67.0, fiber = 30.0)

    @Test
    fun `chaque compteur se lit sous son nom`() {
        val attendu = mapOf(
            Macro.CALORIES to 2_000.0,
            Macro.PROTEIN to 120.0,
            Macro.CARBS to 220.0,
            Macro.SUGARS to 50.0,
            Macro.FAT to 67.0,
            Macro.FIBER to 30.0,
        )

        assertEquals(attendu, Macro.entries.associateWith { goal[it] })
    }

    @Test
    fun `remplacer un compteur laisse les cinq autres`() {
        Macro.entries.forEach { macro ->
            val change = goal.with(macro, 1.0)

            assertEquals(1.0, change[macro], "$macro")
            Macro.entries.filter { it != macro }.forEach { autre ->
                assertEquals(goal[autre], change[autre], "$macro a touche $autre")
            }
        }
    }
}
