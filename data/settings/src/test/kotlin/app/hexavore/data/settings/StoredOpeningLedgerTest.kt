package app.hexavore.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.opening.OpeningRecord
import app.hexavore.domain.opening.OpeningTip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Le registre des ouvertures survit à l'objet qui l'a écrit ([D179][decisions]).
 *
 * Sur de vraies `SharedPreferences`, sous Robolectric : un registre qui oublierait ses
 * astuces au redémarrage les remontrerait toutes, et une demande d'avis close
 * reviendrait — exactement ce que le registre existe pour empêcher.
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
class StoredOpeningLedgerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatchers = TestDispatchers(UnconfinedTestDispatcher())
    private val file = context.getSharedPreferences("opening-test", Context.MODE_PRIVATE)

    @Test
    fun `une installation neuve n a rien compte et allume les astuces`() = runTest {
        val ledger = StoredOpeningLedger(file, dispatchers)

        assertEquals(OpeningRecord(), ledger.read())
        assertEquals(true, ledger.observe().first())
    }

    @Test
    fun `le registre survit a l objet qui l a ecrit`() = runTest {
        val ecrit = OpeningRecord(
            openings = 9,
            lastSeenAt = Instant.parse("2026-10-25T12:00:00Z"),
            firstOpenedAt = Instant.parse("2026-10-18T08:30:00Z"),
            lastMessageAt = 7,
            tipsShown = setOf(OpeningTip.FREE_KEY, OpeningTip.QUARTER_SOURCES),
            ratingClosed = true,
            ratingAfter = 27,
        )
        StoredOpeningLedger(file, dispatchers).write(ecrit)

        // Un second magasin sur le meme fichier : c'est ce qu'est un redemarrage.
        assertEquals(ecrit, StoredOpeningLedger(file, dispatchers).read())
    }

    @Test
    fun `les astuces eteintes le restent au redemarrage`() = runTest {
        StoredOpeningLedger(file, dispatchers).setEnabled(false)

        assertEquals(false, StoredOpeningLedger(file, dispatchers).observe().first())
    }

    @Test
    fun `une astuce qui n existe plus s ignore a la lecture`() = runTest {
        // Le nom d'une astuce retiree par une version d'apres, ou d'avant : l'accueil
        // doit s'ouvrir quand meme, et les autres astuces garder leur souvenir.
        file.edit().putStringSet("opening.tips_shown", setOf("ACCENTS", "UNE_ASTUCE_RETIREE")).commit()

        assertEquals(setOf(OpeningTip.ACCENTS), StoredOpeningLedger(file, dispatchers).read().tipsShown)
    }
}
