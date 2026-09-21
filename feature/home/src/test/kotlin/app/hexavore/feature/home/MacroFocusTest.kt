package app.hexavore.feature.home

import app.hexavore.domain.diary.DaySummary
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DishSummary
import app.hexavore.domain.diary.DishTitle
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroTotals
import app.hexavore.domain.nutrition.Macros
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ce qu'un appui sur un quartier ou une barre change.
 *
 * **La règle vit ici et non dans l'écran**, parce qu'elle décide de quatre cas qui ne
 * se distinguent par aucune image : un appui à côté, un quartier muet, celui qu'on
 * regardait déjà, et un autre. Seul le dernier est le cas courant, et c'est toujours
 * lui qu'on éprouve à la main.
 */
class MacroFocusTest {
    @Test
    fun `un appui a cote referme`() {
        // Le `null` vient de la figure : l'appui est tombe dans la zone, mais hors
        // d'elle. C'est ce qui fait de la bulle quelque chose qui se referme.
        assertNull(journee().focusAfterTap(current = Macro.FIBER, tapped = null))
    }

    @Test
    fun `un autre quartier remplace, sans passer par le vide`() {
        // C'est ce qui permet de parcourir les six : jamais de fermeture entre deux.
        assertEquals(Macro.PROTEIN, journee().focusAfterTap(current = Macro.FIBER, tapped = Macro.PROTEIN))
    }

    @Test
    fun `le meme quartier referme`() {
        // Un second chemin de sortie pour qui n'a pas pense a toucher a cote.
        assertNull(journee().focusAfterTap(current = Macro.FIBER, tapped = Macro.FIBER))
    }

    @Test
    fun `un quartier sans rien a montrer ne repond pas`() {
        // « Ne repond pas » et non « referme » : un quartier vide est un quartier qui
        // n'est pas la, et ce qui etait ouvert le reste.
        val journee = journee(fibres = 8.0, sucres = 0.0)

        assertEquals(Macro.FIBER, journee.focusAfterTap(current = Macro.FIBER, tapped = Macro.SUGARS))
        assertNull(journee.focusAfterTap(current = null, tapped = Macro.SUGARS))
    }

    @Test
    fun `une lacune suffit a faire repondre un quartier a zero`() {
        // Le cas qui separe « rien » de « on ne sait pas ». Les deux dessinent un
        // quartier vide ; l'un n'a rien a dire, l'autre dit lequel aller corriger.
        val journee = journee(fibres = null)

        assertEquals(Macro.FIBER, journee.focusAfterTap(current = null, tapped = Macro.FIBER))
    }

    @Test
    fun `une journee vide ne repond a rien`() {
        // Aucun aliment, donc aucune source : les six quartiers sont muets, et la
        // figure entiere se comporte comme un dessin.
        val vide = DaySummary(
            date = JOUR,
            zone = ZONE,
            goal = null,
            totals = MacroTotals.Empty,
            dishes = emptyList(),
        )

        Macro.entries.forEach { assertNull(vide.focusAfterTap(current = null, tapped = it)) }
    }

    // --- Décor ------------------------------------------------------------------

    private fun journee(fibres: Double? = 8.0, sucres: Double = 4.0): DaySummary {
        val ligne = FoodEntry(
            id = EntryId("e1"),
            dishId = DishId("d1"),
            displayName = "Lentilles",
            quantity = 150.0,
            unit = "g",
            grams = 150.0,
            macros = Macros(kcal = 180.0, protein = 12.0, carbs = 30.0, sugars = sucres, fat = 1.0, fiber = fibres),
        )
        val plat = DishSummary(
            dish = Dish(
                id = DishId("d1"),
                date = JOUR,
                source = EntrySource.MANUAL,
                loggedAt = Instant.parse("2026-03-15T11:30:00Z"),
                entries = listOf(ligne),
            ),
            totals = MacroTotals.of(listOf(ligne.macros)),
            title = DishTitle.Moment(MealMoment.LUNCH, rank = 1),
        )

        return DaySummary(
            date = JOUR,
            zone = ZONE,
            goal = null,
            totals = MacroTotals.of(listOf(ligne.macros)),
            dishes = listOf(plat),
        )
    }

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 3, 15)
        val ZONE: ZoneId = ZoneId.of("Europe/Paris")
    }
}
