package app.hexavore.integration.reports

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.report.Attachment
import app.hexavore.domain.report.REPORT_ADDRESS
import app.hexavore.domain.report.Report
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Un rapport devient un courriel prérempli, et rien ne part sans l'utilisateur.
 *
 * Ce qui se juge est l'intention remise au système : l'adresse, ce que l'on joint, et
 * le droit de lecture donné à la messagerie, sans lequel elle recevrait un lien mort.
 */
@RunWith(RobolectricTestRunner::class)
class MailReportsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val reports = MailReports(application, TestDispatchers(UnconfinedTestDispatcher()))

    /**
     * `FileProvider` garde ses racines dans un cache statique, calcule au premier appel.
     * Robolectric donne a chaque cas un dossier de cache neuf, dans la meme JVM : sans
     * ce vidage, le deuxieme cas a joindre un fichier le verrait refuse, hors racine.
     * Un telephone n'a qu'un dossier de cache, et ne connait pas ce probleme.
     */
    @Before
    fun oublierLesRacines() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let {
            (it.get(null) as MutableMap<*, *>).clear()
        }
    }

    @Test
    fun `sans piece jointe, un courriel vers l'adresse du projet`() = runTest {
        assertTrue(reports.propose(Report(subject = "Sujet", body = "Corps")))

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals(Uri.parse("mailto:$REPORT_ADDRESS"), intent.data)
        assertArrayEquals(arrayOf(REPORT_ADDRESS), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Sujet", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Corps", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue("lance hors d'une activite", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `une piece jointe traverse par le fournisseur, lisible par la messagerie`() = runTest {
        reports.propose(Report(subject = "Sujet", body = "Corps", attachments = listOf(PIECE)))

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_SEND, intent.action)
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(application.packageName + ".reports", uri.authority)
        val lisible = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        assertTrue("sans ce droit, la messagerie recoit un lien mort", lisible)
        assertArrayEquals(PIECE.bytes, application.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
    }

    @Test
    fun `deux pieces jointes partent ensemble`() = runTest {
        val seconde = Attachment("photo.jpg", "image/jpeg", byteArrayOf(9))

        reports.propose(Report(subject = "Sujet", body = "Corps", attachments = listOf(PIECE, seconde)))

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        @Suppress("DEPRECATION")
        assertEquals(2, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.size)
    }

    @Test
    fun `les pieces d'un rapport precedent ne restent pas`() = runTest {
        reports.propose(Report(subject = "Un", body = "Corps", attachments = listOf(PIECE)))

        reports.propose(Report(subject = "Deux", body = "Corps", attachments = listOf(PIECE.copy(name = "autre.txt"))))

        val dossier = File(application.cacheDir, "reports")
        assertEquals(listOf("autre.txt"), dossier.list()!!.toList())
    }

    @Test
    fun `sans messagerie, la reponse est non`() = runTest {
        shadowOf(application).checkActivities(true)

        assertFalse(reports.propose(Report(subject = "Sujet", body = "Corps")))
    }

    private companion object {
        val PIECE = Attachment("trace.txt", "text/plain", byteArrayOf(1, 2, 3))
    }
}
