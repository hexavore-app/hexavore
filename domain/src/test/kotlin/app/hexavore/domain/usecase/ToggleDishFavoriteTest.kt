package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.JOUR
import app.hexavore.domain.diary.brouillon
import app.hexavore.domain.diary.ligne
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'appui long sur un plat : une bascule, et l'état du plat dit dans quel sens.
 *
 * Le lien du plat vers son favori ne s'écrit qu'une fois le favori enregistré : un plat
 * qui désignerait un favori absent rallumerait une étoile qui ne mène nulle part.
 */
class ToggleDishFavoriteTest {
    @Test
    fun `un plat sans favori en devient un, et s'y rattache`() = runTest {
        val plat = noter()

        val resultat = toggle(plat, "Petit-déj")

        val id = (resultat as FavoriteOutcome.Saved).id
        assertEquals("Petit-déj", favoris.byId(id)?.name)
        assertEquals(id, diary.dish(plat.id)?.favoriteId, "le plat doit designer son favori")
    }

    @Test
    fun `un plat deja favori le quitte, sans rien a signaler`() = runTest {
        val plat = noter()
        val id = (toggle(plat, "Petit-déj") as FavoriteOutcome.Saved).id

        val resultat = toggle(diary.dish(plat.id)!!, "ignore")

        assertNull(resultat)
        assertNull(favoris.byId(id), "le favori devait etre supprime")
        assertEquals(0, favoris.all.size, "le nom donne au retrait ne cree rien")
    }

    @Test
    fun `sans nom, rien ne s'ecrit`() = runTest {
        val plat = noter()

        assertNull(toggle(plat, null))
        assertTrue(favoris.all.isEmpty())
        assertNull(diary.dish(plat.id)?.favoriteId)
    }

    @Test
    fun `un nom deja pris laisse le plat tel quel`() = runTest {
        toggle(noter(), "Petit-déj")
        val second = noter()

        val resultat = toggle(second, "petit dej")

        assertEquals(FavoriteOutcome.NameTaken, resultat)
        assertNull(diary.dish(second.id)?.favoriteId, "un refus ne doit pas lier le plat")
    }

    @Test
    fun `un plat disparu entre l'appui et l'ecriture ne cree aucun favori`() = runTest {
        val plat = noter()
        diary.deleteDish(plat.id)

        assertNull(toggle(plat, "Petit-déj"))
        assertTrue(favoris.all.isEmpty())
    }

    // --- Montage -----------------------------------------------------------------

    private val diary = InMemoryDiaryRepository()
    private val favoris = InMemoryFavoriteDishes()
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator("toggle")

    private val toggle = ToggleDishFavorite(
        drafts = GetDishDraft(diary, ids, clock),
        update = UpdateDish(diary, ids),
        save = SaveFavoriteDish(favoris, ids),
        remove = RemoveFavoriteDish(favoris),
    )

    private suspend fun noter(): Dish {
        val id: DishId = LogDish(diary, InMemoryFoodCatalog(), favoris, clock, ids)(brouillon(ligne("a")))
        return diary.dish(id)!!
    }
}
