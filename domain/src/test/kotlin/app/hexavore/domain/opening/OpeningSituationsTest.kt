package app.hexavore.domain.opening

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.report.CrashReports
import app.hexavore.domain.tour.TourSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que l'ouverture sait de l'application, relu des ports.
 *
 * Un port en panne vaut la réponse prudente : un message manqué n'est rien, une
 * ouverture qui plante l'accueil serait tout.
 */
class OpeningSituationsTest {
    @Test
    fun `chaque port dit sa part`() = runTest {
        val situation = OpeningSituations(
            ai = { CONFIGURATION },
            tour = TourSeen(flowOf(true)),
            crashes = Pending("une trace"),
        ).current()

        assertEquals(OpeningSituation(aiConfigured = true, tourSeen = true, crashPending = true), situation)
    }

    @Test
    fun `rien de configure, rien de vu, rien en attente`() = runTest {
        val situation = OpeningSituations(
            ai = { null },
            tour = TourSeen(flowOf(false)),
            crashes = Pending(null),
        ).current()

        assertEquals(OpeningSituation(aiConfigured = false, tourSeen = false, crashPending = false), situation)
    }

    @Test
    fun `un port qui echoue vaut la reponse prudente`() = runTest {
        val panne = IllegalStateException("panne")
        val situation = OpeningSituations(
            ai = AiSettings { throw panne },
            tour = TourSeen(flow { throw panne }),
            crashes = object : CrashReports {
                override suspend fun pending(): String = throw panne

                override suspend fun clear() = Unit
            },
        ).current()

        assertEquals(OpeningSituation(aiConfigured = false, tourSeen = false, crashPending = false), situation)
    }

    @Test
    fun `un message ne parait qu'apres le tour, et jamais sur un plantage`() {
        assertTrue(OpeningSituation(aiConfigured = false, tourSeen = true, crashPending = false).quiet)
        assertFalse(OpeningSituation(aiConfigured = true, tourSeen = false, crashPending = false).quiet)
        assertFalse(OpeningSituation(aiConfigured = true, tourSeen = true, crashPending = true).quiet)
    }

    private class TourSeen(private val seen: Flow<Boolean>) : TourSettings {
        override fun observeSeen(): Flow<Boolean> = seen

        override suspend fun markSeen() = Unit
    }

    private class Pending(private val trace: String?) : CrashReports {
        override suspend fun pending(): String? = trace

        override suspend fun clear() = Unit
    }

    private companion object {
        val CONFIGURATION = AiConfiguration(
            provider = AiProvider.GEMINI,
            apiKey = ApiKey("cle-de-test"),
            model = "modele",
            baseUrl = "https://exemple.invalid/",
        )
    }
}
