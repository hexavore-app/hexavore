package app.hexavore.domain.usecase

import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodFilter
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.resolution.MatchVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L'ordre des deux requêtes, qui est toute la fonctionnalité.
 *
 * **Un bouchon et non `InMemoryFoodCatalog`**, et c'est la division de [D71][decisions] :
 * ce qui se juge ici n'est pas ce que la recherche trouve — le contrat de `FoodSearch`
 * s'en charge sur les deux implémentations — mais **quelles requêtes partent**, ce
 * qu'aucune des deux ne peut dire. Un faux fidèle ne rendrait pas ce cas observable.
 *
 * [decisions]: docs/11-decisions.md
 */
class ResolveFoodLabelTest {
    @Test
    fun `une requete qui rend quelque chose n est jamais retentee au singulier`() = runTest {
        // Le cas mesure : l'index de l'ANSES garde ses pluriels, et « haricots verts »
        // s'y trouve tel quel. Depluraliser d'emblee le perdrait.
        val catalogue = RecordingSearch("haricots verts" to listOf(HARICOTS_VERTS))

        ResolveFoodLabel(catalogue, FRANCAIS)("des haricots verts")

        assertEquals(listOf("haricots verts"), catalogue.queries)
    }

    @Test
    fun `une requete vide se retente au singulier`() = runTest {
        val catalogue = RecordingSearch("pomme" to listOf(POMME))

        ResolveFoodLabel(catalogue, FRANCAIS)("pommes")

        assertEquals(listOf("pommes", "pomme"), catalogue.queries)
    }

    @Test
    fun `le second essai pese ses candidats contre la requete qui les a trouves`() = runTest {
        // Sans quoi « Pomme » serait juge sur « pommes », dont il n'est ni l'egal ni
        // le prefixe : une correspondance certaine deviendrait une relecture.
        val rendu = ResolveFoodLabel(RecordingSearch("pomme" to listOf(POMME)), FRANCAIS)("pommes")

        assertEquals(MatchVerdict.AUTOMATIC, rendu.verdict)
        assertEquals(POMME, rendu.food)
    }

    @Test
    fun `un mot que la depluralisation ne change pas n est pas redemande`() = runTest {
        val catalogue = RecordingSearch()

        ResolveFoodLabel(catalogue, FRANCAIS)("riz")

        assertEquals(listOf("riz"), catalogue.queries)
    }

    @Test
    fun `le libelle est normalise avant d atteindre le catalogue`() = runTest {
        // « du pain » ne rendrait rien : les deux recherches sont conjonctives.
        val catalogue = RecordingSearch("pain" to listOf(PAIN))

        ResolveFoodLabel(catalogue, FRANCAIS)("du pain")

        assertEquals(listOf("pain"), catalogue.queries)
    }

    /**
     * Ce que le catalogue répond, et ce qu'on lui a demandé.
     *
     * Il ne cherche pas : il récite. C'est ce qui permet d'écrire « la première
     * requête ne rend rien » sans dépendre d'une règle de correspondance.
     */
    private class RecordingSearch(vararg replies: Pair<String, List<Food>>) : FoodSearch {
        private val answers = replies.toMap()

        val queries = mutableListOf<String>()

        override fun search(query: String, filter: FoodFilter, limit: Int): Flow<List<Food>> {
            queries += query
            return flowOf(answers[query].orEmpty())
        }
    }

    private companion object {
        val POMME = ciqual("Pomme")
        val PAIN = ciqual("Pain de mie")
        val HARICOTS_VERTS = ciqual("Haricots verts, crus")

        fun ciqual(name: String) = Food(
            id = FoodId(name),
            source = FoodSource.CIQUAL,
            name = name,
            per100g = NutrientValues(kcal = 100.0),
        )
    }
}
