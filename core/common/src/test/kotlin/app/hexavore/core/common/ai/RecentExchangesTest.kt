package app.hexavore.core.common.ai

import app.hexavore.domain.ai.AiExchange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Ce que le journal garde : le dernier échange, et lui seul.
 *
 * **La profondeur n'est pas zéro**, et c'est le cas qui comptait : le journal ne
 * retenait rien tant que le mode de mise au point restait éteint, si bien que le bouton
 * de signalement promettait d'envoyer l'échange et n'envoyait que le corps du courriel
 * ([D153][decisions]).
 *
 * **Et elle n'est plus qu'une.** Il y en avait deux — vingt, mode de mise au point
 * allumé — et l'interrupteur est parti avec l'écran qu'il ouvrait ([D156][decisions]).
 * Ce qui restait à éprouver est la borne, et qu'elle s'applique à chaque écriture.
 *
 * [decisions]: docs/11-decisions.md
 */
class RecentExchangesTest {
    @Test
    fun `le dernier echange reste joignable`() = runTest {
        val journal = RecentExchanges()

        journal.record(echange("premier"))
        journal.record(echange("second"))

        val gardes = journal.observe().first()
        assertEquals(1, gardes.size, "le signalement n'aurait rien a joindre")
        assertEquals("second", gardes.single().endpoint)
    }

    /**
     * Rien ne s'accumule, **quel que soit le nombre d'appels**.
     *
     * Une analyse approfondie part en trois tours : sans cette borne, une seule assiette
     * laisserait trois échanges en mémoire, puis six, puis neuf — l'accumulation que le
     * journal refuse par principe.
     */
    @Test
    fun `l historique ne s accumule pas`() = runTest {
        val journal = RecentExchanges()

        repeat(ASSEZ_D_APPELS) { journal.record(echange("appel $it")) }

        assertEquals(1, journal.observe().first().size)
    }

    @Test
    fun `vider le journal ne laisse rien`() = runTest {
        val journal = RecentExchanges()
        journal.record(echange("un"))

        journal.clear()

        assertEquals(emptyList<AiExchange>(), journal.observe().first())
    }

    private fun echange(endpoint: String) = AiExchange(
        at = Instant.EPOCH,
        endpoint = endpoint,
        status = 200,
        request = "{}",
        response = "{}",
    )

    private companion object {
        /** De quoi couvrir une boucle d'outillage entiere, et au-dela. */
        const val ASSEZ_D_APPELS = 25
    }
}
