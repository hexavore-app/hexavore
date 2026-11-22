package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.JOUR
import app.hexavore.domain.diary.brouillon
import app.hexavore.domain.diary.ligne
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Copier un plat vers un autre jour en fait un plat neuf.
 *
 * La copie ne partage rien avec l'original : ni identifiant, ni lignes, ni favori.
 * Corriger l'une ne doit jamais réécrire l'autre.
 */
class CopyDishToDateTest {
    @Test
    fun `la copie est un plat neuf, au jour choisi et a l'heure du geste`() = runTest {
        val original = noter(favori = FavoriteDishId("fav-1"))
        clock.instant = Instant.parse("2026-03-16T18:00:00Z")
        val lendemain = JOUR.plusDays(1)

        val id = copy(original, lendemain)!!

        val copie = diary.dish(id)!!
        val source = diary.dish(original)!!
        assertNotEquals(original, id)
        assertEquals(lendemain, copie.date)
        assertEquals(clock.instant, copie.loggedAt, "la copie se range a l'heure ou on la fait")
        assertNull(copie.favoriteId, "la copie n'a pas ete rejouee d'un favori")
        assertEquals(source.entries.map { it.displayName }, copie.entries.map { it.displayName })
        val communes = source.entries.map { it.id }.toSet() intersect copie.entries.map { it.id }.toSet()
        assertEquals(emptySet<Any>(), communes, "la copie ne partage aucune ligne")
        assertEquals(setOf(id), copie.entries.map { it.dishId }.toSet(), "chaque ligne appartient a la copie")
    }

    @Test
    fun `l'original ne bouge pas`() = runTest {
        val original = noter()
        val avant = diary.dish(original)

        copy(original, JOUR.plusDays(3))

        assertEquals(avant, diary.dish(original))
    }

    @Test
    fun `un plat disparu ne se copie pas`() = runTest {
        assertNull(copy(DishId("disparu"), JOUR))
        assertEquals(0, diary.dishes.size)
    }

    private val diary = InMemoryDiaryRepository()
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator("copie")
    private val copy = CopyDishToDate(diary, clock, ids)

    private suspend fun noter(favori: FavoriteDishId? = null): DishId {
        val favoris = InMemoryFavoriteDishes()
        val log = LogDish(diary, InMemoryFoodCatalog(), favoris, clock, ids)
        val id = log(brouillon(ligne("a"), ligne("b", nom = "Pois")))
        favori?.let { diary.save(diary.dish(id)!!.copy(favoriteId = it)) }
        return id
    }
}
