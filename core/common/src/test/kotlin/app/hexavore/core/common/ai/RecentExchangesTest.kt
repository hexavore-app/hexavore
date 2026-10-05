package app.hexavore.core.common.ai

import app.hexavore.core.testing.InMemoryDebugSettings
import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.ai.EXCHANGE_HISTORY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Ce que le journal garde, selon qu'on accumule ou non.
 *
 * **Deux profondeurs, et aucune des deux n'est zéro.** Le journal ne retenait rien tant
 * que le mode de mise au point restait éteint, si bien que le bouton de signalement
 * promettait d'envoyer l'échange et n'envoyait que le corps du courriel. Il en garde
 * désormais **un** dans tous les cas, et vingt quand on a demandé à accumuler
 * ([D153][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class RecentExchangesTest {
    @Test
    fun `eteint, le dernier echange reste joignable`() = runTest {
        val journal = RecentExchanges(InMemoryDebugSettings(initial = false))

        journal.record(echange("premier"))
        journal.record(echange("second"))

        val gardes = journal.observe().first()
        assertEquals(1, gardes.size, "le signalement n'aurait rien a joindre")
        assertEquals("second", gardes.single().endpoint)
    }

    @Test
    fun `allume, l historique s accumule jusqu a sa borne`() = runTest {
        val journal = RecentExchanges(InMemoryDebugSettings(initial = true))

        repeat(EXCHANGE_HISTORY + 5) { journal.record(echange("appel $it")) }

        assertEquals(EXCHANGE_HISTORY, journal.observe().first().size)
    }

    /**
     * Éteindre fait **retomber** l'historique, et ne le fige pas à vingt.
     *
     * La profondeur se relit à chaque écriture : sans cela, une séance de mise au point
     * laisserait vingt échanges en mémoire pour le reste de la vie du processus, alors
     * même qu'on vient de dire qu'on ne voulait plus accumuler.
     */
    @Test
    fun `eteindre fait retomber l historique a un`() = runTest {
        val reglage = InMemoryDebugSettings(initial = true)
        val journal = RecentExchanges(reglage)
        repeat(5) { journal.record(echange("appel $it")) }

        reglage.setEnabled(false)
        journal.record(echange("apres"))

        assertEquals(1, journal.observe().first().size)
    }

    private fun echange(endpoint: String) = AiExchange(
        at = Instant.EPOCH,
        endpoint = endpoint,
        status = 200,
        request = "{}",
        response = "{}",
    )
}
