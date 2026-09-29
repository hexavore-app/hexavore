package app.hexavore.domain.progress

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Les deux séries, et **le matin où elles ne doivent pas s'effondrer**.
 *
 * C'est le seul cas qui compte vraiment ici : à huit heures, personne n'a encore noté
 * son petit-déjeuner. Une règle naïve — compter à rebours depuis aujourd'hui — remet la
 * série à zéro chaque nuit, et le seul chiffre que l'application montre en permanence
 * annonce un échec tous les matins.
 */
internal class StreaksTest {
    @Test
    fun `une journee en cours non notee ne casse pas la serie`() {
        val logged = setOf(LUNDI, MARDI, MERCREDI)

        // Jeudi matin : rien de note, et pourtant trois jours courent.
        assertEquals(3, loggingStreak(logged, today = MERCREDI.plusDays(1)))
    }

    @Test
    fun `la journee en cours compte des qu elle est notee`() {
        val logged = setOf(LUNDI, MARDI, MERCREDI)

        assertEquals(3, loggingStreak(logged, today = MERCREDI))
    }

    @Test
    fun `un jour saute casse la serie, et ce qui precede ne se rattrape pas`() {
        // Une serie qui sauterait un trou pour reprendre plus loin ne serait pas une
        // serie.
        val logged = setOf(LUNDI, MERCREDI)

        assertEquals(1, loggingStreak(logged, today = MERCREDI))
    }

    @Test
    fun `deux jours sans rien remettent la serie a zero`() {
        val logged = setOf(LUNDI)

        assertEquals(0, loggingStreak(logged, today = LUNDI.plusDays(2)))
    }

    @Test
    fun `un journal vide ne donne aucune serie`() {
        assertEquals(0, loggingStreak(emptySet(), today = LUNDI))
    }

    @Test
    fun `la serie parfaite ignore le jour en cours, meme parfait`() {
        // Une journee en cours n'est pas parfaite : elle n'est pas finie. La juger
        // ferait passer chaque matin par « parfaite », rien n'etant encore au-dessus
        // du plafond.
        val perfect = setOf(LUNDI, MARDI, MERCREDI)

        assertEquals(2, perfectStreak(perfect, today = MERCREDI))
    }

    @Test
    fun `la serie parfaite se compte depuis la veille`() {
        val perfect = setOf(LUNDI, MARDI, MERCREDI)

        assertEquals(3, perfectStreak(perfect, today = MERCREDI.plusDays(1)))
    }

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 9, 21)
        val MARDI: LocalDate = LUNDI.plusDays(1)
        val MERCREDI: LocalDate = LUNDI.plusDays(2)
    }
}
