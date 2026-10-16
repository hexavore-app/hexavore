package app.hexavore.data.food

import app.hexavore.core.database.ciqual.CiqualAnnotations
import app.hexavore.core.database.ciqual.CiqualEstimates
import app.hexavore.core.database.ciqual.CiqualFoodRow
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle qui commande la seconde passe, éprouvée sur une ligne **construite**.
 *
 * **Il faut la construire, et c'est le point.** Une fiche portant à la fois une mesure
 * et une complétion pour la même teneur ne peut pas sortir de la base livrée : le
 * lecteur du CSV refuse cette ligne, et l'import s'arrête. Le `?:` du mapper est donc
 * une **seconde ceinture** — mais une ceinture qu'aucun test parti du fichier livré ne
 * peut serrer, puisque la situation n'y existe pas.
 *
 * C'est exactement la forme d'un défaut déjà payé ([D85][decisions]) : une règle
 * couverte en apparence par des cas qui ne peuvent pas la mettre en défaut. La
 * campagne de défaite l'a trouvée en deux sabotages qui survivaient.
 *
 * [decisions]: docs/11-decisions.md
 */
class FoodMapperTest {
    @Test
    fun `la mesure l emporte sur la completion quand les deux existent`() {
        val food = ligne(kcal = 54.0, kcalEstimee = 900.0).toDomain(FoodId("f"), emptyList())

        assertEquals(54.0, food.per100g.kcal)
    }

    @Test
    fun `une teneur mesuree n est pas marquee, meme si une completion dort a cote`() {
        val food = ligne(kcal = 54.0, kcalEstimee = 900.0).toDomain(FoodId("f"), emptyList())

        assertTrue("la marque suit ce qui s affiche, pas ce qui est stocke", food.estimated.isEmpty())
    }

    @Test
    fun `une teneur absente prend la completion, et se marque`() {
        val food = ligne(kcal = null, kcalEstimee = 39.0).toDomain(FoodId("f"), emptyList())

        assertEquals(39.0, food.per100g.kcal)
        assertEquals(setOf(Macro.CALORIES), food.estimated)
    }

    @Test
    fun `une teneur absente sans completion le reste`() {
        // Inconnu n'est pas zero, et une absence de complétion n'en fabrique pas une.
        val food = ligne(kcal = null, kcalEstimee = null).toDomain(FoodId("f"), emptyList())

        assertEquals(null, food.per100g.kcal)
        assertTrue(food.estimated.isEmpty())
    }

    @Test
    fun `une teneur mesuree a zero l emporte sur une completion`() {
        // Zero est une mesure, pas une absence : la completion ne doit pas s'y
        // substituer. C'est le piege du projet, applique a cette regle-ci.
        val food = ligne(kcal = 0.0, kcalEstimee = 500.0).toDomain(FoodId("f"), emptyList())

        assertEquals(0.0, food.per100g.kcal)
        assertTrue(food.estimated.isEmpty())
    }

    @Test
    fun `seule la teneur completee est marquee, pas les cinq autres`() {
        val food = ligne(kcal = null, kcalEstimee = 39.0).toDomain(FoodId("f"), emptyList())

        assertEquals(setOf(Macro.CALORIES), food.estimated)
        assertEquals(2.18, food.per100g.protein)
    }

    // --- Les noms courants (D164) ----------------------------------------------

    @Test
    fun `une ligne de l ANSES porte ses noms courants jusqu a la fiche`() {
        // Sans eux, le classement ne pese que le libelle, et « riz » retombe sur le riz
        // cru. C'est la lecture d'une fiche pas encore copiee : la recherche.
        val food = ligne(kcal = 155.0, kcalEstimee = null)
            .copy(commonNames = setOf("riz", "riz blanc"))
            .toDomain(FoodId("f"), emptyList())

        assertEquals(setOf("riz", "riz blanc"), food.commonNames)
    }

    @Test
    fun `une fiche copiee relit ses noms courants dans la reference`() {
        // Une fiche deja mangee passe par l'autre lecture. Si les noms s'y perdaient,
        // le riz cuit qu'on a deja note classerait plus mal que celui qu'on n'a jamais vu.
        val copie = Food(
            id = FoodId("f"),
            source = FoodSource.CIQUAL,
            sourceRef = "9104",
            name = "Riz blanc, cuit, sans sel ajoute",
            per100g = NutrientValues(kcal = 155.0, protein = 2.7, carbs = 33.0, sugars = 0.1, fat = 0.3, fiber = 0.4),
        ).toEntity(now = 0L)
        val reference = CiqualAnnotations(
            name = "Riz blanc, cuit, sans sel ajoute",
            category = null,
            shortName = null,
            commonNames = setOf("riz"),
        )

        assertEquals(setOf("riz"), copie.toDomain(annotations = reference).commonNames)
        assertTrue("sans reference, aucun nom ne s invente", copie.toDomain().commonNames.isEmpty())
    }

    private fun ligne(kcal: Double?, kcalEstimee: Double?) = CiqualFoodRow(
        code = "11040",
        name = "Capres, au vinaigre",
        shortName = null,
        groupName = null,
        category = null,
        kcal100 = kcal,
        protein100 = 2.18,
        carb100 = 3.5,
        sugar100 = 0.4,
        fat100 = 0.86,
        fiber100 = 3.6,
        saturatedFat100 = null,
        salt100 = null,
        estimated = CiqualEstimates(kcal100 = kcalEstimee),
    )
}
