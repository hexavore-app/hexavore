package app.hexavore.integration.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryReminderSettings
import app.hexavore.core.testing.SampleDiary
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSetup
import app.hexavore.domain.usecase.ShouldRemind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Le travail qui sonne : il replanifie toujours, et ne notifie que s'il a à dire.
 *
 * Replanifier d'abord : un rappel qui se tait aujourd'hui doit sonner demain, et c'est
 * ce même travail qui pose le suivant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderWorkerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = application.getSystemService(NotificationManager::class.java)
    private val diary = InMemoryDiaryRepository()
    private val settings = InMemoryReminderSettings()
    private val planned = mutableListOf<ReminderSetup>()
    private val clock = FixedClock.atNoon(TODAY)

    @Test
    fun `un rappel qui a a dire notifie, et replanifie`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(ListenableWorker.Result.success(), run(Reminder.LUNCH))

        assertEquals(1, planned.size)
        val posted = shadowOf(notifications).allNotifications
        assertEquals(1, posted.size)
        assertEquals(ReminderWorker.CHANNEL_ID, posted.single().channelId)
        assertTrue("le canal existe", notifications.getNotificationChannel(ReminderWorker.CHANNEL_ID) != null)
    }

    @Test
    fun `un repas deja note se tait, mais le suivant se pose`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        diary.setContent(SampleDiary.day(TODAY))

        run(Reminder.BREAKFAST)

        assertEquals(1, planned.size)
        assertEquals(0, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun `sans la permission, rien ne s'affiche`() = runTest {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        run(Reminder.LUNCH)

        assertEquals(1, planned.size)
        assertEquals(0, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun `un travail sans rappel connu ne fait rien et reussit`() = runTest {
        // Un nom d'une ancienne version, reste dans la file : il ne doit ni planter ni
        // reessayer sans fin.
        assertEquals(ListenableWorker.Result.success(), run(name = "GOUTER"))

        assertEquals(0, planned.size)
    }

    private suspend fun run(reminder: Reminder? = null, name: String? = reminder?.name): ListenableWorker.Result =
        TestListenableWorkerBuilder<ReminderWorker>(application)
            .setInputData(name?.let { workDataOf(ReminderWorker.KEY_REMINDER to it) } ?: workDataOf())
            .setWorkerFactory(factory)
            .build()
            .doWork()

    private val factory = object : WorkerFactory() {
        override fun createWorker(context: Context, workerClassName: String, parameters: WorkerParameters) =
            ReminderWorker(
                context = context,
                parameters = parameters,
                shouldRemind = ShouldRemind(diary, settings, clock),
                settings = settings,
                scheduler = object : ReminderScheduler {
                    override suspend fun reschedule(setup: ReminderSetup) {
                        planned += setup
                    }
                },
                look = NotificationLook(icon = android.R.drawable.ic_dialog_info, color = android.R.color.black),
            )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 15)
    }
}
