package app.hexavore.domain.resolution

import app.hexavore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les deux invraisemblances vues à l'usage, et ce qui leur ressemble sans en être.
 *
 * Ce fichier ne juge pas la valeur du seuil — elle est assumée dans [D138][decisions].
 * Il tient les deux frontières qui, si elles bougeaient, feraient signaler du juste :
 * **zéro n'est pas un poids suspect**, et **une valeur inconnue n'est pas un zéro**.
 *
 * [decisions]: docs/11-decisions.md
 */
class PlausibleLineTest {
    @Test
    fun `une brochette a un gramme est une erreur d echelle`() {
        assertTrue(PlausibleLine.suspiciousWeight(1.0))
        assertTrue(PlausibleLine.suspiciousWeight(2.0))
    }

    @Test
    fun `un poids egal au seuil passe, et une epice avec lui`() {
        assertFalse(PlausibleLine.suspiciousWeight(PlausibleLine.MINIMUM_GRAMS))
        assertFalse(PlausibleLine.suspiciousWeight(180.0))
    }

    @Test
    fun `zero n est pas un poids suspect, c est un poids absent`() {
        // L'ecran de validation reclame le chiffre manquant : le marquer  estime  en
        // plus ne dirait rien de nouveau, et brouillerait ce que la marque signale.
        assertFalse(PlausibleLine.suspiciousWeight(0.0))
    }

    @Test
    fun `six zeros ne decrivent aucun aliment`() {
        val vide = NutrientValues(kcal = 0.0, protein = 0.0, carbs = 0.0, sugars = 0.0, fat = 0.0, fiber = 0.0)
        assertTrue(PlausibleLine.emptyValues(vide))
    }

    @Test
    fun `une seule valeur non nulle suffit a rendre le tableau credible`() {
        val presque = NutrientValues(kcal = 0.0, protein = 0.0, carbs = 0.0, sugars = 0.0, fat = 0.0, fiber = 1.6)
        assertFalse(PlausibleLine.emptyValues(presque))
    }

    @Test
    fun `un tableau inconnu n est pas un tableau vide`() {
        // `null` se voit a l'ecran, qui designe le champ manquant ; zero se lit comme
        // une mesure. Confondre les deux ferait signaler tout ce que le modele ignore.
        assertFalse(PlausibleLine.emptyValues(NutrientValues()))
    }
}
