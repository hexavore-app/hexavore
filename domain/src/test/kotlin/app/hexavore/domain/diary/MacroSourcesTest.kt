package app.hexavore.domain.diary

import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroTotals
import app.hexavore.domain.nutrition.Macros
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Ce qui a donné une macro dans une journée.
 *
 * **Trois questions distinctes, et la troisième est la seule qui compte vraiment.**
 * Classer et cumuler sont des opérations de liste ; distinguer *zéro connu* de *valeur
 * inconnue* est la règle que ce fichier existe pour tenir, parce que c'est la seule
 * qu'aucun écran ne rattrape si elle tombe.
 */
class MacroSourcesTest {
    @Test
    fun `la plus grosse source vient en premier`() {
        // La question posee en touchant un quartier est « qu'est-ce qui m'a donne mes
        // fibres » : la premiere ligne doit y repondre, pas la derniere.
        val sources = journee(plat("p1", ligne("Lentilles", fibres = 8.0), ligne("Pain", fibres = 2.0)))
            .sourcesOf(Macro.FIBER, detailed = 5)

        assertEquals(listOf("Lentilles", "Pain"), sources.top.map { it.name })
        assertEquals(8.0, sources.top.first().value)
    }

    @Test
    fun `le meme aliment deux fois ne fait qu une ligne`() {
        // Deux saisies de lentilles sont un aliment qui a donne deux fois, pas deux
        // aliments. C'est ce qui garde la bulle courte sur une journee ordinaire.
        val sources = journee(
            plat("p1", ligne("Lentilles", fibres = 8.0)),
            plat("p2", ligne("Lentilles", fibres = 4.0)),
        ).sourcesOf(Macro.FIBER, detailed = 5)

        assertEquals(listOf(MacroSource("Lentilles", 12.0)), sources.top)
    }

    @Test
    fun `au dela du detail, le reste est cumule et compte`() {
        // Le nombre autant que la somme : « 3 autres » dit qu'il reste quelque chose a
        // voir, la somme dit que ce qu'on lit ne fait pas le total.
        val lignes = (1..8).map { ligne("Aliment $it", fibres = it.toDouble()) }
        val sources = journee(plat("p1", *lignes.toTypedArray())).sourcesOf(Macro.FIBER, detailed = 5)

        assertEquals(listOf(8.0, 7.0, 6.0, 5.0, 4.0), sources.top.map { it.value })
        assertEquals(OtherSources(count = 3, value = 6.0), sources.others)
    }

    @Test
    fun `sans reste, il n y a pas de ligne de reste`() {
        // Une ligne « 0 autres -- 0 g » serait une phrase qui ne dit rien.
        val sources = journee(plat("p1", ligne("Lentilles", fibres = 8.0))).sourcesOf(Macro.FIBER, detailed = 5)

        assertNull(sources.others)
    }

    @Test
    fun `une valeur inconnue est nommee a part, jamais classee`() {
        // La regle la plus ancienne du projet : inconnu n'est pas zero. On ignore ou
        // ranger cet aliment, donc on ne le range pas -- on dit qu'on ne sait pas.
        val sources = journee(
            plat("p1", ligne("Lentilles", fibres = 8.0), ligne("Sauce", fibres = null)),
        ).sourcesOf(Macro.FIBER, detailed = 5)

        assertEquals(listOf("Lentilles"), sources.top.map { it.name })
        assertEquals(listOf("Sauce"), sources.unknown)
    }

    @Test
    fun `une valeur inconnue ne grossit pas le reste`() {
        // Le piege exact : cumuler des inconnues comme des zeros donne le meme nombre,
        // et plus rien ne permet de faire la difference.
        val connues = (1..6).map { ligne("Aliment $it", fibres = it.toDouble()) }
        val sources = journee(plat("p1", *connues.toTypedArray(), ligne("Sauce", fibres = null)))
            .sourcesOf(Macro.FIBER, detailed = 5)

        assertEquals(OtherSources(count = 1, value = 1.0), sources.others)
    }

    @Test
    fun `un zero connu n est pas une source`() {
        // Lister le blanc de poulet sous les fibres repondrait a cote de la question,
        // et la bulle doit tenir sans defiler.
        val sources = journee(
            plat("p1", ligne("Lentilles", fibres = 8.0), ligne("Blanc de poulet", fibres = 0.0)),
        ).sourcesOf(Macro.FIBER, detailed = 5)

        assertEquals(listOf("Lentilles"), sources.top.map { it.name })
        assertTrue(sources.unknown.isEmpty())
    }

    @Test
    fun `un aliment mesure deux fois, dont une sans valeur, est dans les deux listes`() {
        // Les deux sont vrais en meme temps : il a donne 8 g qu'on sait, et une part
        // de lui n'est pas renseignee. Taire l'un des deux arrondirait du mauvais cote.
        val sources = journee(
            plat("p1", ligne("Lentilles", fibres = 8.0)),
            plat("p2", ligne("Lentilles", fibres = null)),
        ).sourcesOf(Macro.FIBER, detailed = 5)

        assertEquals(listOf(MacroSource("Lentilles", 8.0)), sources.top)
        assertEquals(listOf("Lentilles"), sources.unknown)
    }

    @Test
    fun `les calories se lisent comme les cinq autres`() {
        // Un sixieme de la figure qui ne repondrait pas se lirait comme une panne.
        val sources = journee(plat("p1", ligne("Riz", kcal = 195.0), ligne("Huile", kcal = 90.0)))
            .sourcesOf(Macro.CALORIES, detailed = 5)

        assertEquals(listOf("Riz", "Huile"), sources.top.map { it.name })
    }

    @Test
    fun `une journee sans rien n a rien a montrer`() {
        assertTrue(journee().sourcesOf(Macro.FIBER, detailed = 5).isEmpty)
    }

    @Test
    fun `une macro que rien n a apportee n a rien a montrer`() {
        // Distinct du cas precedent : la journee est pleine, le quartier est vide.
        val sources = journee(plat("p1", ligne("Blanc de poulet", fibres = 0.0))).sourcesOf(Macro.FIBER, detailed = 5)

        assertTrue(sources.isEmpty)
    }

    @Test
    fun `une lacune est quelque chose a montrer`() {
        // Le cas qui separe « rien » de « on ne sait pas ». Le quartier est a zero dans
        // les deux cas et la figure les dessine pareil ; ici, pourtant, il y a une
        // phrase a dire, et c'est meme la seule qui puisse faire corriger la saisie.
        val sources = journee(plat("p1", ligne("Sauce", fibres = null))).sourcesOf(Macro.FIBER, detailed = 5)

        assertTrue(sources.top.isEmpty())
        assertTrue(!sources.isEmpty)
    }

    // --- Décor ------------------------------------------------------------------

    private fun ligne(nom: String, kcal: Double = 100.0, fibres: Double? = 1.0) = FoodEntry(
        id = EntryId("$nom-e"),
        dishId = DishId("p"),
        displayName = nom,
        quantity = 100.0,
        unit = "g",
        grams = 100.0,
        macros = Macros(kcal = kcal, protein = 4.0, carbs = 42.0, sugars = 0.1, fat = 0.4, fiber = fibres),
    )

    private fun plat(id: String, vararg lignes: FoodEntry) = DishSummary(
        dish = Dish(
            id = DishId(id),
            date = JOUR,
            source = EntrySource.MANUAL,
            loggedAt = Instant.parse("2026-03-15T11:30:00Z"),
            entries = lignes.toList(),
        ),
        totals = MacroTotals.of(lignes.map { it.macros }),
        title = DishTitle.Moment(MealMoment.LUNCH, rank = 1),
    )

    private fun journee(vararg plats: DishSummary) = DaySummary(
        date = JOUR,
        zone = ZoneId.of("Europe/Paris"),
        goal = null,
        totals = MacroTotals.of(plats.flatMap { it.dish.entries }.map { it.macros }),
        dishes = plats.toList(),
    )
}
