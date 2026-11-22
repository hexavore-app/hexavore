package app.hexavore.integration.reminders

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.hexavore.core.testing.FixedClock
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSetup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Planifier : un travail unique par rappel allumé, aucun pour un rappel éteint.
 *
 * Unique et remplacé : replanifier dix fois laisse un seul travail par rappel, sans
 * quoi chaque réglage touché ajouterait une sonnerie.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerRemindersTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val reminders = WorkManagerReminders(application, FixedClock.atNoon(LocalDate.of(2026, 3, 15)))

    @Before
    fun installer() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `chaque rappel allume a son travail, en attente`() = runTest {
        reminders.reschedule(ReminderSetup())

        Reminder.entries.forEach { reminder ->
            assertEquals(reminder.name, listOf(WorkInfo.State.ENQUEUED), states(reminder))
        }
    }

    @Test
    fun `un rappel eteint perd son travail`() = runTest {
        reminders.reschedule(ReminderSetup())

        reminders.reschedule(ReminderSetup(enabled = Reminder.entries.toSet() - Reminder.LUNCH))

        assertTrue(states(Reminder.LUNCH).none { it == WorkInfo.State.ENQUEUED })
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states(Reminder.DINNER))
    }

    @Test
    fun `replanifier remplace, il n'empile pas`() = runTest {
        repeat(3) { reminders.reschedule(ReminderSetup()) }

        val enAttente = states(Reminder.BREAKFAST).filter { it == WorkInfo.State.ENQUEUED }
        assertEquals(listOf(WorkInfo.State.ENQUEUED), enAttente)
    }

    private fun states(reminder: Reminder): List<WorkInfo.State> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWork("rappel." + reminder.name.lowercase())
        .get()
        .map { it.state }
}
