package app.hexavore.integration.reminders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Le délai avant la prochaine sonnerie, calculé par le fuseau.
 *
 * Deux fois par an, un jour fait vingt-trois ou vingt-cinq heures : une arithmétique
 * d'heures décalerait le rappel d'une heure, sans que personne ne fasse le lien.
 */
class ReminderDelayTest {
    @Test
    fun `avant l'heure, le rappel sonne aujourd'hui`() {
        assertEquals(Duration.ofMinutes(30), delayUntil(LocalTime.of(8, 0), at(2026, 3, 10, 7, 30), PARIS))
    }

    @Test
    fun `apres l'heure, il sonne demain`() {
        assertEquals(Duration.ofHours(23), delayUntil(LocalTime.of(8, 0), at(2026, 3, 10, 9, 0), PARIS))
    }

    @Test
    fun `a l'heure pile, il vise demain, sans quoi il sonnerait deux fois`() {
        assertEquals(Duration.ofDays(1), delayUntil(LocalTime.of(21, 0), at(2026, 3, 10, 21, 0), PARIS))
    }

    @Test
    fun `la nuit du passage a l'heure d'ete ne dure que vingt-trois heures`() {
        // Le 29 mars 2026, Paris passe de 2 h a 3 h.
        assertEquals(Duration.ofHours(23), delayUntil(LocalTime.of(21, 0), at(2026, 3, 28, 21, 0), PARIS))
    }

    @Test
    fun `la nuit du retour a l'heure d'hiver dure vingt-cinq heures`() {
        // Le 25 octobre 2026, Paris passe de 3 h a 2 h.
        assertEquals(Duration.ofHours(25), delayUntil(LocalTime.of(21, 0), at(2026, 10, 24, 21, 0), PARIS))
    }

    @Test
    fun `l'heure visee est celle du fuseau, pas celle de l'instant donne`() {
        // 7 h a Paris, donne en UTC : le rappel de 8 h est dans une heure.
        val utc = at(2026, 3, 10, 7, 0).withZoneSameInstant(ZoneId.of("UTC"))

        assertEquals(Duration.ofHours(1), delayUntil(LocalTime.of(8, 0), utc, PARIS))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        LocalDateTime.of(year, month, day, hour, minute).atZone(PARIS)

    private companion object {
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
