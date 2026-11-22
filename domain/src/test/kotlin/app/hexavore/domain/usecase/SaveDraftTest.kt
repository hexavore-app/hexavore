package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.JOUR
import app.hexavore.domain.diary.brouillon
import app.hexavore.domain.diary.ligne
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Enregistrer un brouillon : noter un plat neuf, corriger un ancien, ou le vider. */
class SaveDraftTest {
    @Test
    fun `un brouillon neuf note un plat`() = runTest {
        val id = save(brouillon(ligne("a")))!!

        assertEquals(1, diary.dishes.size)
        assertEquals(id, diary.dishes.single().id)
    }

    @Test
    fun `un brouillon d'un plat existant le corrige, sans en creer un autre`() = runTest {
        val id = save(brouillon(ligne("a")))!!

        val corrige = save(brouillon(ligne("b", nom = "Pois"), dishId = id))

        assertEquals(id, corrige)
        assertEquals(listOf("Pois"), diary.dishes.single().entries.map { it.displayName })
    }

    @Test
    fun `un plat vide de ses lignes disparait`() = runTest {
        val id = save(brouillon(ligne("a")))!!

        assertNull(save(brouillon(dishId = id)), "il n'y a plus de plat a montrer")
        assertEquals(0, diary.dishes.size)
    }

    private val diary = InMemoryDiaryRepository()
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator("draft")
    private val save = SaveDraft(
        log = LogDish(diary, InMemoryFoodCatalog(), InMemoryFavoriteDishes(), clock, ids),
        update = UpdateDish(diary, ids),
    )
}
