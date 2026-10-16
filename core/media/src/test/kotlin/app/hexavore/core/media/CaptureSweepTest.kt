package app.hexavore.core.media

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * Ce que le balayage du lancement emporte, et ce qu'il laisse ([D163][decisions]).
 *
 * Android tue Hexavore pendant que l'appareil photo du système est ouvert, et c'est le
 * lancement suivant — celui que provoque l'écriture du fichier, ou le retour de
 * l'appareil photo — qui balaie. Il effaçait la photo qu'on venait de prendre. La règle
 * se juge sur deux instants, donc sur la JVM ; le fichier, lui, demande un appareil.
 *
 * [decisions]: docs/11-decisions.md
 */
class CaptureSweepTest {
    @Test
    fun `une prise qu on vient de faire n est pas un residu`() {
        assertFalse(abandonedCapture(writtenAt = LANCEMENT.minusSeconds(30), now = LANCEMENT))
    }

    @Test
    fun `une prise restee longtemps dans l appareil photo l est toujours`() {
        // Le temps de cadrer, de reprendre, de couper le son : une prise peut durer.
        assertFalse(abandonedCapture(writtenAt = LANCEMENT.minus(Duration.ofMinutes(45)), now = LANCEMENT))
    }

    @Test
    fun `un fichier d avant hier est un residu`() {
        assertTrue(abandonedCapture(writtenAt = LANCEMENT.minus(Duration.ofDays(2)), now = LANCEMENT))
    }

    @Test
    fun `le delai lui-meme appartient encore a la prise`() {
        // Juste au-dela, on balaie ; pile dessus, on attend encore.
        assertFalse(abandonedCapture(writtenAt = LANCEMENT.minus(CAPTURE_GRACE), now = LANCEMENT))
        assertTrue(abandonedCapture(writtenAt = LANCEMENT.minus(CAPTURE_GRACE).minusSeconds(1), now = LANCEMENT))
    }

    private companion object {
        val LANCEMENT: Instant = Instant.parse("2026-10-15T12:00:00Z")
    }
}
