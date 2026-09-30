package app.hexavore.data.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.progress.Badge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Le dépôt de la progression, sur la vraie base.
 *
 * Deux règles s'y éprouvent, et aucune des deux ne se voit ailleurs : **un plancher ne
 * descend jamais**, et **un palier déjà pris garde sa date**. Elles vivent dans
 * l'adaptateur parce que c'est lui qui écrit, et un faux qui les respecterait sans que
 * le vrai les respecte laisserait passer un défaut qui ne se verrait que chez quelqu'un
 * venant de corriger un vieux plat.
 *
 * La troisième est une conséquence de la première, et c'est celle qui ferme la boucle
 * de [D132][decisions] : **un relevé qui n'apporte rien n'écrit rien**.
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RoomProgressStoreTest {
    private lateinit var base: HexavoreDatabase
    private lateinit var magasin: RoomProgressStore

    @Before
    fun ouvrir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(context, HexavoreDatabase::class.java).build()
        magasin = RoomProgressStore(progress = base.progressDao(), dispatchers = TestDispatchers(Dispatchers.IO))
    }

    @After
    fun fermer() = base.close()

    @Test
    fun `une base neuve vaut trois zeros`() = runBlocking {
        // L'absence de ligne **est** la valeur de depart : aucun chemin n'en ecrit une
        // « pour qu'elle existe ».
        val range = magasin.observe().first()

        assertEquals(0L, range.points)
        assertEquals(0, range.bestStreak)
        assertEquals(0, range.bestPerfectStreak)
    }

    @Test
    fun `un plancher monte et ne redescend jamais`() = runBlocking {
        magasin.raise(points = 4_200, bestStreak = 31, bestPerfectStreak = 4)

        magasin.raise(points = 10, bestStreak = 1, bestPerfectStreak = 0)

        val range = magasin.observe().first()
        assertEquals(4_200L, range.points)
        assertEquals(31, range.bestStreak)
        assertEquals(4, range.bestPerfectStreak)
    }

    @Test
    fun `les trois planchers montent separement`() = runBlocking {
        magasin.raise(points = 100, bestStreak = 10, bestPerfectStreak = 3)

        magasin.raise(points = 500, bestStreak = 2, bestPerfectStreak = 9)

        val range = magasin.observe().first()
        assertEquals(500L, range.points)
        assertEquals(10, range.bestStreak)
        assertEquals(9, range.bestPerfectStreak)
    }

    @Test
    fun `un palier garde la date de son premier rangement`() = runBlocking {
        // Sans quoi l'ecran annoncerait que la serie de trente jours a ete obtenue ce
        // matin, a chaque releve.
        magasin.unlock(Badge.STREAK_30, MARS)

        magasin.unlock(Badge.STREAK_30, SEPTEMBRE)

        assertEquals(MARS, magasin.observe().first().unlocked[Badge.STREAK_30])
    }

    @Test
    fun `plusieurs paliers cohabitent avec leurs dates`() = runBlocking {
        magasin.unlock(Badge.STREAK_7, MARS)
        magasin.unlock(Badge.DISHES_50, SEPTEMBRE)

        val range = magasin.observe().first().unlocked

        assertEquals(MARS, range[Badge.STREAK_7])
        assertEquals(SEPTEMBRE, range[Badge.DISHES_50])
    }

    private companion object {
        val MARS: LocalDate = LocalDate.of(2026, 3, 3)
        val SEPTEMBRE: LocalDate = LocalDate.of(2026, 9, 29)
    }
}

internal const val ROBOLECTRIC_SDK = 33
