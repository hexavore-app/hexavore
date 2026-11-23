package app.hexavore.integration.reports

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Le dernier plantage, écrit au moment où il arrive et relu au lancement suivant.
 *
 * Une instance neuve relit le fichier : c'est ce que fait le lancement d'après, dans un
 * autre processus.
 */
@RunWith(RobolectricTestRunner::class)
class FileCrashReportsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val dispatchers = TestDispatchers(UnconfinedTestDispatcher())

    @Test
    fun `rien n'attend au premier lancement`() = runTest {
        assertNull(FileCrashReports(application, dispatchers).pending())
    }

    @Test
    fun `un plantage note se relit au lancement suivant, avec sa trace et son fil`() = runTest {
        FileCrashReports(application, dispatchers).record(Thread("fil-de-test"), IllegalStateException("boum-42"))

        val trace = FileCrashReports(application, dispatchers).pending()!!
        assertTrue(trace, trace.contains("boum-42"))
        assertTrue(trace, trace.contains("IllegalStateException"))
        assertTrue(trace, trace.contains("fil-de-test"))
    }

    @Test
    fun `une trace oubliee ne revient pas`() = runTest {
        val reports = FileCrashReports(application, dispatchers)
        reports.record(Thread.currentThread(), IllegalStateException("boum"))

        reports.clear()

        assertNull(FileCrashReports(application, dispatchers).pending())
    }

    @Test
    fun `un fichier vide n'est pas un plantage`() = runTest {
        File(application.cacheDir, "dernier-plantage.txt").writeText("   ")

        assertNull(FileCrashReports(application, dispatchers).pending())
    }
}
