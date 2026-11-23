package app.hexavore.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSetup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

/**
 * Les petits réglages, sur de vraies préférences.
 *
 * Ce qui se juge est **la relecture par une instance neuve** : c'est ce que voit le
 * lancement suivant. Une valeur gardée en mémoire seulement passerait un test qui relit
 * la même instance, et serait perdue au premier redémarrage.
 */
@RunWith(RobolectricTestRunner::class)
class StoredFlagsTest {
    private val preferences: SharedPreferences = ApplicationProvider
        .getApplicationContext<Context>()
        .getSharedPreferences("flags-test", Context.MODE_PRIVATE)
    private val dispatchers = TestDispatchers(UnconfinedTestDispatcher())

    @Test
    fun `une cle refusee le reste au lancement suivant, jusqu'a ce qu'on l'oublie`() = runTest {
        val neuve = StoredKeyRejection(preferences, dispatchers)
        assertFalse("rien n'est refuse au premier lancement", neuve.observe().first())

        StoredKeyRejection(preferences, dispatchers).note()
        assertTrue(StoredKeyRejection(preferences, dispatchers).observe().first())

        StoredKeyRejection(preferences, dispatchers).clear()
        assertFalse(StoredKeyRejection(preferences, dispatchers).observe().first())
    }

    @Test
    fun `les photos se gardent par defaut, et le choix contraire survit`() = runTest {
        assertTrue(StoredPhotoSettings(preferences, dispatchers).keeping())

        StoredPhotoSettings(preferences, dispatchers).setKeeping(false)

        val relu = StoredPhotoSettings(preferences, dispatchers)
        assertFalse(relu.keeping())
        assertFalse(relu.observeKeeping().first())
    }

    @Test
    fun `le tour vu ne se rejoue plus, sauf s'il est oublie`() = runTest {
        assertFalse(StoredTourSettings(preferences, dispatchers).observeSeen().first())

        StoredTourSettings(preferences, dispatchers).markSeen()
        assertTrue(StoredTourSettings(preferences, dispatchers).observeSeen().first())

        StoredTourSettings(preferences, dispatchers).forget()
        assertFalse(StoredTourSettings(preferences, dispatchers).observeSeen().first())
    }

    @Test
    fun `les rappels sont tous allumes, a leur heure, au premier lancement`() = runTest {
        assertEquals(ReminderSetup(), StoredReminderSettings(preferences, dispatchers).current())
    }

    @Test
    fun `un rappel eteint et deplace se relit tel quel`() = runTest {
        val reglages = StoredReminderSettings(preferences, dispatchers)

        reglages.setEnabled(Reminder.LUNCH, enabled = false)
        reglages.setTime(Reminder.DINNER, LocalTime.of(20, 45))

        val relu = StoredReminderSettings(preferences, dispatchers).current()
        assertFalse(Reminder.LUNCH in relu)
        assertEquals(LocalTime.of(20, 45), relu[Reminder.DINNER])
        assertEquals("un rappel non touche garde son heure", Reminder.BREAKFAST.default, relu[Reminder.BREAKFAST])
        assertEquals("le flux suit l'ecriture", relu, reglages.observe().first())
    }

    @Test
    fun `la permission demandee une fois le reste, et l'oubli remet tout a zero`() = runTest {
        val reglages = StoredReminderSettings(preferences, dispatchers)
        assertFalse(reglages.permissionAsked())

        reglages.markPermissionAsked()
        reglages.setEnabled(Reminder.STREAK, enabled = false)
        assertTrue(StoredReminderSettings(preferences, dispatchers).permissionAsked())

        reglages.forget()
        val relu = StoredReminderSettings(preferences, dispatchers)
        assertFalse(relu.permissionAsked())
        assertEquals(ReminderSetup(), relu.current())
    }
}
