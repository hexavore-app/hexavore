package app.hexavore.core.common.ai

import app.hexavore.domain.ai.AcceptedKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * La nouvelle d'une clé acceptée : elle attend, elle se dit, elle s'oublie.
 *
 * Ce qui s'éprouve ici est ce que l'accueil et le hub supposent tous les deux : qu'une
 * nouvelle posée par l'écran des clés **survit à sa fermeture**, et qu'une fois dite
 * elle ne reparaît pas sur l'écran suivant (D157).
 */
class PendingKeyAcceptanceTest {
    @Test
    fun `rien n attend tant qu aucune cle n a ete acceptee`() = runTest {
        assertNull(PendingKeyAcceptance().observe().first())
    }

    @Test
    fun `une cle acceptee attend qu un ecran la dise`() = runTest {
        val nouvelle = PendingKeyAcceptance()

        nouvelle.announce(AcceptedKey(vision = true))

        assertEquals(AcceptedKey(vision = true), nouvelle.observe().first())
    }

    @Test
    fun `une fois dite, elle ne reparait pas`() = runTest {
        val nouvelle = PendingKeyAcceptance()
        nouvelle.announce(AcceptedKey(vision = true))

        nouvelle.consume()

        assertNull(nouvelle.observe().first())
    }

    /** Deux clés validées coup sur coup n'ont qu'une chose à dire : celle qui sert. */
    @Test
    fun `la derniere cle acceptee chasse la precedente`() = runTest {
        val nouvelle = PendingKeyAcceptance()

        nouvelle.announce(AcceptedKey(vision = true))
        nouvelle.announce(AcceptedKey(vision = false))

        assertEquals(AcceptedKey(vision = false), nouvelle.observe().first())
    }
}
