package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSettings
import app.hexavore.domain.reminder.ReminderSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * Lesquels des quatre rappels sonnent, et à quelle heure.
 *
 * **Deux clés par rappel, nommées d'après lui** — `reminder.lunch` et
 * `reminder.lunch.time` — plutôt qu'une structure sérialisée. C'est le choix de
 * [StoredNoticeSettings], et pour la même raison : un fichier de préférences se relit à
 * l'œil quand on cherche un défaut.
 *
 * **Absent vaut allumé, à l'heure par défaut.** Un rappel éteint d'office ne serait
 * découvert par personne, et c'est aussi ce qui fait qu'un cinquième rappel ajouté plus
 * tard s'allume sans migration. L'heure suit la même règle : elle n'est écrite que
 * lorsque quelqu'un la déplace, donc changer un défaut dans le code déplace les rappels
 * de ceux qui n'y ont jamais touché — ce qui est la bonne lecture.
 *
 * **L'heure est rangée en minutes depuis minuit**, un entier. Une chaîne `HH:mm` aurait
 * demandé un format, donc une locale, donc un jour un fichier qu'on ne relit plus.
 */
internal class StoredReminderSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : ReminderSettings {
    private val setup = MutableStateFlow(preferences.readSetup())

    override fun observe(): Flow<ReminderSetup> = setup

    override suspend fun current(): ReminderSetup = setup.value

    override suspend fun setEnabled(reminder: Reminder, enabled: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(reminder.key(), enabled) }
        reread()
    }

    override suspend fun setTime(reminder: Reminder, time: LocalTime) = withContext(dispatchers.io) {
        preferences.edit { putInt(reminder.timeKey(), time.hour * MINUTES_PER_HOUR + time.minute) }
        reread()
    }

    /** Oublie les huit réglages. Appelé par l'effacement, comme les autres magasins. */
    internal suspend fun forget() = withContext(dispatchers.io) {
        preferences.edit {
            Reminder.entries.forEach {
                remove(it.key())
                remove(it.timeKey())
            }
        }
        reread()
    }

    /**
     * Relire le disque plutôt que modifier l'état en mémoire.
     *
     * C'est ce que font les autres magasins de ce module : l'affichage montre ce qui est
     * rangé, et non ce qu'on croit avoir rangé.
     */
    private fun reread() {
        setup.value = preferences.readSetup()
    }
}

private fun Reminder.key(): String = "reminder." + name.lowercase()

private fun Reminder.timeKey(): String = key() + ".time"

private fun SharedPreferences.readSetup(): ReminderSetup = ReminderSetup(
    enabled = Reminder.entries.filterTo(mutableSetOf()) { getBoolean(it.key(), true) },
    times = Reminder.entries.mapNotNull { reminder ->
        val minutes = getInt(reminder.timeKey(), UNSET)
        if (minutes == UNSET) {
            null
        } else {
            reminder to LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
        }
    }.toMap(),
)

/** Personne n'a déplacé ce rappel : son défaut s'applique. */
private const val UNSET = -1

private const val MINUTES_PER_HOUR = 60
