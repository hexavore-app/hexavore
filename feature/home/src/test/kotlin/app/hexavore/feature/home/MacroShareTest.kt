package app.hexavore.feature.home

import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DishSummary
import app.hexavore.domain.diary.DishTitle
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroTotal
import app.hexavore.domain.nutrition.MacroTotals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * La part qu'un plat prend sur la journée.
 *
 * Ce que la mini-barre montre, et la seule chose qu'elle montre : **combien de ma
 * journée ce plat a-t-il pris**. Une question que le chiffre à côté ne pose pas — il
 * dit une quantité, pas une proportion.
 */
class MacroShareTest {
    @Test
    fun `la part est la valeur rapportee a l objectif du jour`() {
        assertEquals(0.25f, plat(protein = 36.0).share(Macro.PROTEIN, goal))
    }

    @Test
    fun `un depassement rend une barre pleine, jamais davantage`() {
        // Pas de rétrécissement d'échelle comme sur les grandes barres : il faudrait
        // la lire, et il n'y a rien à lire ici. Un plat qui dépasse à lui seul
        // l'objectif du jour est déjà lisible dans son chiffre.
        assertEquals(1f, plat(protein = 500.0).share(Macro.PROTEIN, goal))
    }

    @Test
    fun `sans objectif, aucune part`() {
        // Une barre suppose une cible, et une journee anterieure au premier objectif
        // n'en a aucune. Le chiffre, lui, reste exact et reste affiche.
        assertNull(plat(protein = 36.0).share(Macro.PROTEIN, goal = null))
    }

    @Test
    fun `un objectif nul n a pas de part non plus`() {
        // Le rapport serait infini, et une barre pleine affirmerait un depassement
        // d'une cible qui n'existe pas.
        assertNull(plat(protein = 36.0).share(Macro.PROTEIN, goal.copy(protein = 0.0)))
    }

    @Test
    fun `un plat sans la macro rend une part nulle, pas une absence`() {
        // Zero est une reponse : la barre existe et reste vide, ce qui dit « ce plat
        // n'en a pas apporte ». L'absence de barre, elle, dit « on ne compare rien ».
        assertEquals(0f, plat(protein = 0.0).share(Macro.PROTEIN, goal))
    }

    // --- Décor ------------------------------------------------------------------

    private val goal = DailyGoal(
        kcal = 2_000.0,
        protein = 144.0,
        carbs = 200.0,
        sugars = 50.0,
        fat = 70.0,
        fiber = 30.0,
    )

    private fun plat(protein: Double) = DishSummary(
        dish = Dish(
            id = DishId("d1"),
            date = LocalDate.of(2026, 3, 15),
            source = EntrySource.MANUAL,
            loggedAt = Instant.parse("2026-03-15T11:30:00Z"),
            entries = emptyList(),
        ),
        totals = MacroTotals(
            calories = MacroTotal(500.0, complete = true),
            protein = MacroTotal(protein, complete = true),
            carbs = MacroTotal(0.0, complete = true),
            sugars = MacroTotal(0.0, complete = true),
            fat = MacroTotal(0.0, complete = true),
            fiber = MacroTotal(0.0, complete = true),
        ),
        title = DishTitle.Moment(MealMoment.LUNCH, rank = 1),
    )
}
