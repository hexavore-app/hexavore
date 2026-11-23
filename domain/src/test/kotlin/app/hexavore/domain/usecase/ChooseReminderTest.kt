package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryReminderSettings
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSetup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalTime

/**
 * Régler un rappel le replace aussitôt dans le temps.
 *
 * Écrire le réglage sans replanifier laisserait le rappel sonner à l'ancienne heure
 * jusqu'au prochain lancement : c'est ce que ce cas d'usage empêche.
 */
class ChooseReminderTest {
    private val settings = InMemoryReminderSettings()
    private val scheduler = RecordingScheduler()
    private val choose = ChooseReminder(settings, scheduler)

    @Test
    fun `eteindre un rappel replanifie avec le reglage deja ecrit`() = runTest {
        choose.setEnabled(Reminder.LUNCH, enabled = false)

        assertEquals(listOf(settings.current()), scheduler.calls)
        assertEquals(false, Reminder.LUNCH in scheduler.calls.single())
    }

    @Test
    fun `deplacer un rappel replanifie a la nouvelle heure`() = runTest {
        val sept = LocalTime.of(7, 0)

        choose.setTime(Reminder.BREAKFAST, sept)

        assertEquals(sept, scheduler.calls.single()[Reminder.BREAKFAST])
    }

    @Test
    fun `l'ecran lit ce qui est regle`() = runTest {
        choose.setEnabled(Reminder.STREAK, enabled = false)

        assertEquals(settings.current(), ObserveReminders(settings)().first())
    }

    private class RecordingScheduler : ReminderScheduler {
        val calls = mutableListOf<ReminderSetup>()

        override suspend fun reschedule(setup: ReminderSetup) {
            calls += setup
        }
    }
}
