package app.hexavore.domain.diary

import app.hexavore.domain.nutrition.Macros
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Le nom d'un plat : d'où il vient, et ce qui le distingue de son voisin.
 *
 * **Deux règles distinctes se lisent ici.** Le **moment** répond à « quel repas »
 * — l'heure le dit, sauf quand quelqu'un a corrigé —, le **rang** répond à « lequel
 * des deux », et il ne se calcule qu'en regardant la journée entière.
 */
class DishTitleTest {
    @Test
    fun `les bornes sont des milieux, pas des heures de repas`() {
        // Personne ne dine a 18 h 01 : ce qu'on place est l'endroit ou l'on bascule
        // d'un nom a l'autre, entre deux repas plutot que sur l'un d'eux.
        assertEquals(MealMoment.BREAKFAST, MealMoment.at(LocalTime.of(5, 0)))
        assertEquals(MealMoment.BREAKFAST, MealMoment.at(LocalTime.of(10, 59)))
        assertEquals(MealMoment.LUNCH, MealMoment.at(LocalTime.of(11, 0)))
        assertEquals(MealMoment.LUNCH, MealMoment.at(LocalTime.of(14, 59)))
        assertEquals(MealMoment.SNACK, MealMoment.at(LocalTime.of(15, 0)))
        assertEquals(MealMoment.SNACK, MealMoment.at(LocalTime.of(17, 59)))
        assertEquals(MealMoment.DINNER, MealMoment.at(LocalTime.of(18, 0)))
    }

    @Test
    fun `la nuit appartient au diner`() {
        // Un plat note a 1 h du matin prolonge la soiree. C'est deja ainsi que le
        // journal le range : la journee d'un repas vient de sa date, pas de son heure.
        assertEquals(MealMoment.DINNER, MealMoment.at(LocalTime.of(23, 30)))
        assertEquals(MealMoment.DINNER, MealMoment.at(LocalTime.MIDNIGHT))
        assertEquals(MealMoment.DINNER, MealMoment.at(LocalTime.of(4, 59)))
    }

    @Test
    fun `sans titre, le plat porte le nom de son moment`() {
        val titres = listOf(plat("d1", midi)).titles(ZONE)

        assertEquals(listOf(DishTitle.Moment(MealMoment.LUNCH, rank = 1)), titres)
    }

    @Test
    fun `deux plats du meme moment se numerotent`() {
        // Le plat, puis le dessert note a part. En affichage simplifie, le titre est
        // le seul repere : deux « Dejeuner » identiques ne se distingueraient plus.
        val titres = listOf(plat("d1", midi), plat("d2", midi.plusSeconds(600))).titles(ZONE)

        assertEquals(
            listOf(DishTitle.Moment(MealMoment.LUNCH, rank = 1), DishTitle.Moment(MealMoment.LUNCH, rank = 2)),
            titres,
        )
    }

    @Test
    fun `deux moments differents gardent chacun leur rang`() {
        val titres = listOf(plat("d1", matin), plat("d2", midi)).titles(ZONE)

        assertEquals(
            listOf(DishTitle.Moment(MealMoment.BREAKFAST, rank = 1), DishTitle.Moment(MealMoment.LUNCH, rank = 1)),
            titres,
        )
    }

    @Test
    fun `un plat nomme garde son nom et ne prend aucun rang`() {
        // Ce que l'utilisateur a ecrit lui appartient : deux « Poke bowl » sont deux
        // « Poke bowl ». Et un plat nomme ne doit pas decaler le numero des autres,
        // sans quoi retirer un titre renumeroterait la journee.
        val titres = listOf(plat("d1", midi, titre = "Poke bowl"), plat("d2", midi)).titles(ZONE)

        assertEquals(listOf(DishTitle.Named("Poke bowl"), DishTitle.Moment(MealMoment.LUNCH, rank = 1)), titres)
    }

    @Test
    fun `un titre blanc ne compte pas comme un titre`() {
        // Un champ vide n'est pas un nom : le plat retombe sur celui de son moment,
        // plutot que de s'afficher sans rien.
        val titres = listOf(plat("d1", midi, titre = "   ")).titles(ZONE)

        assertEquals(listOf(DishTitle.Moment(MealMoment.LUNCH, rank = 1)), titres)
    }

    @Test
    fun `le moment retenu a la saisie l emporte sur l heure`() {
        // Le cas qui justifie la colonne : le diner d hier, note ce matin a 9 h.
        // L heure d un plat est celle de sa saisie, et elle dirait « Petit-dejeuner ».
        val titres = listOf(plat("d1", matin, moment = MealMoment.DINNER)).titles(ZONE)

        assertEquals(listOf(DishTitle.Moment(MealMoment.DINNER, rank = 1)), titres)
    }

    @Test
    fun `un plat d avant les moments prend celui de son heure`() {
        // Les plats deja en base n'ont pas de moment, et leur en inventer un a la
        // migration aurait affirme un choix que personne n'a fait.
        assertEquals(MealMoment.LUNCH, plat("d1", midi).momentIn(ZONE))
        assertEquals(MealMoment.BREAKFAST, plat("d2", matin).momentIn(ZONE))
    }

    @Test
    fun `l heure se lit dans le fuseau du resume`() {
        // 11 h a Paris, 10 h a Londres : le meme instant ne releve pas du meme repas,
        // et c'est le fuseau du resume qui tranche -- jamais celui de la machine.
        val instant = Instant.parse("2026-03-15T10:00:00Z")

        assertEquals(MealMoment.LUNCH, plat("d1", instant).momentIn(ZONE))
        assertEquals(MealMoment.BREAKFAST, plat("d1", instant).momentIn(ZoneId.of("Europe/London")))
    }

    // --- Décor ------------------------------------------------------------------

    private val matin: Instant = Instant.parse("2026-03-15T08:00:00Z")
    private val midi: Instant = Instant.parse("2026-03-15T11:30:00Z")

    private fun plat(id: String, at: Instant, titre: String? = null, moment: MealMoment? = null) = Dish(
        id = DishId(id),
        date = JOUR,
        source = EntrySource.MANUAL,
        loggedAt = at,
        entries = listOf(
            FoodEntry(
                id = EntryId("$id-l1"),
                dishId = DishId(id),
                displayName = "Riz",
                quantity = 150.0,
                unit = "g",
                grams = 150.0,
                macros = Macros(kcal = 195.0, protein = 4.0, carbs = 42.0, sugars = 0.1, fat = 0.4, fiber = 1.2),
            ),
        ),
        title = titre,
        moment = moment,
    )

    private companion object {
        /** Paris : une heure d'avance sur UTC en mars, ce que deux cas utilisent. */
        val ZONE: ZoneId = ZoneId.of("Europe/Paris")
    }
}
