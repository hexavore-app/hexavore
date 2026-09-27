package app.hexavore.domain.resolution

import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodServing
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La charnière entre ce que le modèle estime et ce que le journal enregistre.
 *
 * Les cas portent sur **une** unité : la multiplication par la quantité est
 * vérifiée une fois et n'a pas à polluer les autres.
 */
class QuantityConversionTest {
    @Test
    fun `des grammes sont des grammes, et rien n est devine`() {
        val rendu = convertToGrams(150.0, EstimatedUnit.G, FR)

        assertEquals(150.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed, "des grammes ne se devinent pas")
    }

    @Test
    fun `sans densite, un millilitre pese un gramme et le dit`() {
        val rendu = convertToGrams(200.0, EstimatedUnit.ML, FR)

        assertEquals(200.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed, "un litre de lait ne pese pas un kilo")
    }

    @Test
    fun `une densite connue s applique et cesse d etre une supposition`() {
        val rendu = convertToGrams(200.0, EstimatedUnit.ML, FR, density = LAIT)

        assertEquals(206.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed)
    }

    @Test
    fun `la portion nommee de la fiche l emporte sur le forfait`() {
        // Le cas qui justifie l'ecart avec docs/04 : la table des portions porte
        // « 1 bol » a 40 g pour des cereales. Le forfait de 250 g se tromperait
        // d'un facteur six, et sans se signaler puisqu'il aurait l'air d'une regle.
        val cereales = aliment(FoodServing("1 bol", grams = 40.0, isDefault = true))

        val rendu = convertToGrams(1.0, EstimatedUnit.BOWL, FR, cereales)

        assertEquals(40.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed, "une portion de la fiche est une donnee, pas une supposition")
    }

    @Test
    fun `sans portion nommee, le bol vaut le forfait et c est signale`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.BOWL, FR, aliment())

        assertEquals(250.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    @Test
    fun `une tranche prend le poids que la fiche lui donne`() {
        val pain = aliment(FoodServing("1 tranche", grams = 45.0))

        assertEquals(45.0, convertToGrams(1.0, EstimatedUnit.SLICE, FR, pain).grams, TOLERANCE)
    }

    @Test
    fun `les deux cuilleres ne se confondent pas`() {
        // « cuillere a soupe » contient « cuillere », et une comparaison sur le seul
        // mot commun rendrait la premiere portion trouvee pour les deux unites.
        val miel = aliment(
            FoodServing("1 cuillère à soupe", grams = 21.0),
            FoodServing("1 cuillère à café", grams = 7.0),
        )

        assertEquals(7.0, convertToGrams(1.0, EstimatedUnit.TSP, FR, miel).grams, TOLERANCE)
    }

    @Test
    fun `sans portion nommee, la cuillere applique la densite au forfait`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.TBSP, FR, density = HUILE)

        assertEquals(13.8, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed, "le forfait de 15 g reste une supposition, densite ou non")
    }

    @Test
    fun `une piece prend la portion par defaut de la fiche`() {
        val pomme = aliment(
            FoodServing("1 fraise", grams = 12.0),
            FoodServing("1 pomme moyenne", grams = 150.0, isDefault = true),
        )

        assertEquals(150.0, convertToGrams(1.0, EstimatedUnit.PIECE, FR, pomme).grams, TOLERANCE)
    }

    @Test
    fun `une piece retombe sur la quantite proposee a l ouverture`() {
        val produit = aliment().copy(defaultServingG = 90.0)

        val rendu = convertToGrams(1.0, EstimatedUnit.PIECE, FR, produit)

        assertEquals(90.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed, "une portion d'emballage est une donnee")
    }

    @Test
    fun `une piece sans rien vaut cent grammes, et c est une supposition`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.PIECE, FR, aliment())

        assertEquals(100.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    @Test
    fun `une assiette reste une supposition meme quand la fiche porte des portions`() {
        // Une assiette n'est pas une propriete de l'aliment : aucune fiche ne peut
        // la mesurer, donc aucune portion ne doit faire croire qu'elle l'a fait.
        val riz = aliment(FoodServing("1 portion", grams = 150.0, isDefault = true))

        val rendu = convertToGrams(1.0, EstimatedUnit.PLATE, FR, riz)

        assertEquals(350.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    @Test
    fun `la quantite met le poids unitaire a l echelle`() {
        val oeuf = aliment(FoodServing("1 oeuf", grams = 50.0, isDefault = true))

        assertEquals(150.0, convertToGrams(3.0, EstimatedUnit.PIECE, FR, oeuf).grams, TOLERANCE)
    }

    @Test
    fun `un verre garde la densite quand la fiche ne dit rien`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.GLASS, FR, density = JUS)

        assertEquals(208.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    @Test
    fun `le poids du modele remplace le forfait de la piece`() {
        // Cinq cacahuetes : le forfait en faisait cinq cents grammes, soit cent
        // vingt-cinq fois trop. Le modele, lui, a vu l assiette.
        val rendu = convertToGrams(5.0, EstimatedUnit.PIECE, FR, estimated = 4.0)

        assertEquals(4.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed, "il a estime, il n a pas pese")
    }

    @Test
    fun `le poids du modele ne remplace jamais une mesure`() {
        // La portion nommee de la fiche est une donnee ; l estimation du modele n en
        // est pas une, et une donnee ne se laisse pas ecraser par une estimation.
        val pomme = aliment(FoodServing("1 pomme moyenne", grams = 150.0, isDefault = true))

        val rendu = convertToGrams(2.0, EstimatedUnit.PIECE, FR, pomme, estimated = 40.0)

        assertEquals(300.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed)
    }

    @Test
    fun `un poids nul ou negatif n est pas un poids`() {
        // Zero est une absence deguisee : le forfait reprend la main plutot que de
        // faire disparaitre la ligne du journal.
        assertEquals(500.0, convertToGrams(5.0, EstimatedUnit.PIECE, FR, estimated = 0.0).grams, TOLERANCE)
        assertEquals(500.0, convertToGrams(5.0, EstimatedUnit.PIECE, FR, estimated = -3.0).grams, TOLERANCE)
    }

    @Test
    fun `le poids du modele vaut aussi pour une assiette`() {
        // L assiette n a jamais de portion nommee : c est le forfait ou rien, donc
        // c est exactement la ou le modele aide le plus.
        assertEquals(220.0, convertToGrams(1.0, EstimatedUnit.PLATE, FR, estimated = 220.0).grams, TOLERANCE)
    }

    private fun aliment(vararg servings: FoodServing) = Food(
        id = FoodId("fiche"),
        source = FoodSource.CIQUAL,
        name = "Aliment",
        per100g = NutrientValues(kcal = 100.0),
        servings = servings.toList(),
    )

    private companion object {
        /**
         * Le francais : les libelles de portion de ces decors le sont, comme ceux de
         * `servings.csv`. Le cas anglais est a part, plus bas.
         */
        val FR = ContentLanguage.FRENCH

        const val TOLERANCE = 0.001
        const val LAIT = 1.03
        const val JUS = 1.04
        const val HUILE = 0.92
    }
}
