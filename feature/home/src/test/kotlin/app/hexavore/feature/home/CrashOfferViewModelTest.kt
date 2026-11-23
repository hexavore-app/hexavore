package app.hexavore.feature.home

import app.hexavore.core.testing.RecordedReports
import app.hexavore.domain.report.CrashReports
import app.hexavore.domain.usecase.ReportCrash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Proposer d'envoyer le dernier plantage, une fois.
 *
 * Envoyé ou refusé, le rapport s'oublie : une proposition qui revient à chaque
 * lancement punirait pour un plantage dont on n'est pas l'auteur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CrashOfferViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val crashes = PendingTrace("java.lang.IllegalStateException: boum")
    private val sender = RecordedReports()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `sans plantage, rien ne se propose`() = runTest(dispatcher) {
        assertFalse(CrashOfferViewModel(PendingTrace(null), ReportCrash(PendingTrace(null), sender)).pending.value)
    }

    @Test
    fun `envoyer ferme la boite et propose la trace`() = runTest(dispatcher) {
        val modele = CrashOfferViewModel(crashes, ReportCrash(crashes, sender))
        assertTrue(modele.pending.value)

        modele.onSend("Plantage", "Bonjour,")

        assertFalse(modele.pending.value)
        assertEquals(1, sender.proposed.size)
        assertNull(crashes.pending(), "la trace envoyee s'oublie")
    }

    @Test
    fun `refuser ferme la boite et oublie la trace, sans rien envoyer`() = runTest(dispatcher) {
        val modele = CrashOfferViewModel(crashes, ReportCrash(crashes, sender))

        modele.onDecline()

        assertFalse(modele.pending.value)
        assertNull(crashes.pending())
        assertTrue(sender.proposed.isEmpty())
    }

    @Test
    fun `un envoi qui echoue ne fait pas planter l'accueil`() = runTest(dispatcher) {
        val panne = object : CrashReports {
            override suspend fun pending(): String = "trace"

            override suspend fun clear() = error("disque plein")
        }
        val modele = CrashOfferViewModel(panne, ReportCrash(panne, sender))

        modele.onSend("Plantage", "Bonjour,")
        modele.onDecline()

        assertFalse(modele.pending.value)
    }

    private class PendingTrace(private var trace: String?) : CrashReports {
        override suspend fun pending(): String? = trace

        override suspend fun clear() {
            trace = null
        }
    }
}
