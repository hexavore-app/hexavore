package app.hexavore.domain.report

import app.hexavore.core.testing.RecordedReports
import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.usecase.ReportAnalysis
import app.hexavore.domain.usecase.ReportCrash
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Ce qui part, ce qui ne part pas, et ce qui ne revient pas.
 *
 * Les trois choses que [D138][decisions] tient et qu'une relecture du code ne montre
 * pas : la trace est **oubliée dans les deux cas**, un échange absent n'empêche pas le
 * signalement, et la clé d'API n'a rien à faire dans un rapport.
 *
 * [decisions]: docs/11-decisions.md
 */
class ReportUseCasesTest {
    private val rapports = RecordedReports()

    @Test
    fun `la trace part dans le corps, pour etre relue avant l envoi`() = runTest {
        val traces = FakeCrashReports("java.lang.IllegalStateException: rien")

        assertTrue(ReportCrash(traces, rapports)(subject = "Plantage", intro = "Voici la trace."))

        val corps = rapports.last?.body.orEmpty()
        assertTrue(corps.startsWith("Voici la trace."))
        assertTrue(corps.contains("IllegalStateException"))
        // Dans le corps et non en piece jointe : une piece jointe se transmet sans
        // avoir ete ouverte, et ce qu'on lit est ce qu'on accepte d'envoyer.
        assertEquals(emptyList<Attachment>(), rapports.last?.attachments)
    }

    @Test
    fun `refuser oublie la trace, sinon la question reviendrait a chaque lancement`() = runTest {
        val traces = FakeCrashReports("une trace")
        traces.clear()

        assertFalse(ReportCrash(traces, rapports)(subject = "Plantage", intro = "Voici."))
        assertNull(traces.pending())
        assertEquals(emptyList<Report>(), rapports.proposed)
    }

    @Test
    fun `accepter l oublie aussi, meme quand le courriel ne s ouvre pas`() = runTest {
        val traces = FakeCrashReports("une trace")
        val muet = RecordedReports(accepts = false)

        assertFalse(ReportCrash(traces, muet)(subject = "Plantage", intro = "Voici."))
        // Oubliee quoi qu'il arrive : reproposer la meme trace au lancement suivant
        // serait une punition pour un plantage dont on n'est pas l'auteur.
        assertNull(traces.pending())
    }

    @Test
    fun `un signalement porte l echange et la photo`() = runTest {
        val echange = AiExchange(
            at = Instant.parse("2026-09-30T12:00:00Z"),
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/x",
            status = 200,
            request = "brochette de poulet",
            response = "{\"lines\":[]}",
        )

        assertTrue(
            ReportAnalysis(rapports)(
                subject = "Proposition incorrecte",
                description = "- Saule : 1 g",
                exchange = echange,
                photo = byteArrayOf(1, 2, 3),
            ),
        )

        val pieces = rapports.last?.attachments.orEmpty()
        assertEquals(listOf("echange.txt", "assiette.jpg"), pieces.map { it.name })
        val transcription = pieces.first().bytes.decodeToString()
        assertTrue(transcription.contains("brochette de poulet"))
        assertTrue(transcription.contains("HTTP 200"))
        // Ce que l'ecran montrait reste dans le corps : les pieces jointes portent ce
        // que le modele a rendu, pas ce que la resolution en a fait.
        assertTrue(rapports.last?.body.orEmpty().contains("Saule"))
    }

    @Test
    fun `un signalement ampute vaut mieux qu un bouton qui ne fait rien`() = runTest {
        assertTrue(
            ReportAnalysis(rapports)(
                subject = "Proposition incorrecte",
                description = "- Saule : 1 g",
                // Le mode debug etait eteint : il n'y a pas d'echange a joindre.
                exchange = null,
                photo = null,
            ),
        )
        assertEquals(emptyList<Attachment>(), rapports.last?.attachments)
    }
}

/** Une trace qui attend, et qu'on peut oublier une fois. */
private class FakeCrashReports(private var trace: String?) : CrashReports {
    override suspend fun pending(): String? = trace

    override suspend fun clear() {
        trace = null
    }
}
