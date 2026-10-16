package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * La liste des favoris nomme leurs composants comme l'éditeur ([D167][decisions]).
 *
 * Le défaut tenait en une ligne : la liste affichait le nom figé à l'enregistrement,
 * l'éditeur celui de la fiche vivante. Après un changement de langue, le même plat se
 * lisait en anglais d'un côté et en français de l'autre.
 *
 * [decisions]: docs/11-decisions.md
 */
class ObserveFavoriteDishesTest {
    @Test
    fun `un composant tire d une fiche porte son nom d aujourd hui`() = runTest {
        // La fiche s'appelait « Rice, white, cooked » le jour de l'enregistrement ;
        // elle se lit aujourd'hui en francais.
        catalogue.save(RIZ)
        favoris.save(favori(composant("Rice, white, cooked", RIZ.id)))

        val liste = observe().first()

        assertEquals(listOf("Riz blanc, cuit"), liste.single().components.map { it.name })
        assertEquals("Midi", liste.single().name, "le nom du favori est le sien, il ne bouge pas")
    }

    @Test
    fun `le titre court l emporte sur le libelle, comme dans l editeur`() = runTest {
        catalogue.save(RIZ.copy(shortName = "Riz"))
        favoris.save(favori(composant("Rice, white, cooked", RIZ.id)))

        assertEquals("Riz", observe().first().single().components.single().name)
    }

    @Test
    fun `un composant tape a la main garde son nom`() = runTest {
        // Il n'a pas de fiche : son nom est le seul qui existe.
        favoris.save(favori(composant("Sauce de mamie", foodId = null)))

        assertEquals("Sauce de mamie", observe().first().single().components.single().name)
    }

    @Test
    fun `une fiche supprimee depuis laisse le nom fige`() = runTest {
        favoris.save(favori(composant("Rice, white, cooked", FoodId("fiche-disparue"))))

        assertEquals("Rice, white, cooked", observe().first().single().components.single().name)
    }

    @Test
    fun `une lecture qui echoue laisse la liste s afficher`() = runTest {
        // Un nom d'hier vaut mieux qu'une liste qui ne s'affiche pas.
        favoris.save(favori(composant("Rice, white, cooked", RIZ.id)))
        val illisible = ObserveFavoriteDishes(favoris) { error("Base illisible") }

        assertEquals("Rice, white, cooked", illisible().first().single().components.single().name)
    }

    // --- Montage -----------------------------------------------------------------

    private val catalogue = InMemoryFoodCatalog()
    private val favoris = InMemoryFavoriteDishes()
    private val observe = ObserveFavoriteDishes(favoris, catalogue)

    private fun favori(vararg composants: FavoriteComponent) =
        FavoriteDish(id = FavoriteDishId("fav-midi"), name = "Midi", components = composants.toList())

    private fun composant(nom: String, foodId: FoodId?) = FavoriteComponent(
        foodId = foodId,
        name = nom,
        quantity = 150.0,
        unit = QuantityUnit.Gram,
        grams = 150.0,
        values = NutrientValues(kcal = 200.0),
    )

    private companion object {
        val RIZ = Food(
            id = FoodId("food-riz"),
            source = FoodSource.CIQUAL,
            name = "Riz blanc, cuit",
            per100g = NutrientValues(kcal = 133.0),
        )
    }
}
