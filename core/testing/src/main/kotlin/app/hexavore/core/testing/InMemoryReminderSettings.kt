package app.hexavore.core.testing

import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSettings
import app.hexavore.domain.reminder.ReminderSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime

/**
 * Les réglages des rappels, en mémoire.
 *
 * Première implémentation du port, comme [InMemoryAdjustmentSettings].
 *
 * **Le souvenir de la demande vit à part des réglages**, comme dans le vrai : il ne fait
 * pas partie de [ReminderSetup] parce qu'il ne se règle pas, il se constate. Un test qui
 * vérifie qu'un écran a noté la question le lit dans [asked] ([D162][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemoryReminderSettings(initial: ReminderSetup = ReminderSetup()) : ReminderSettings {
    private val state = MutableStateFlow(initial)

    /** `true` une fois la question de la permission posée. */
    var asked: Boolean = false
        private set

    override fun observe(): Flow<ReminderSetup> = state

    override suspend fun current(): ReminderSetup = state.value

    override suspend fun setEnabled(reminder: Reminder, enabled: Boolean) {
        val allumes = state.value.enabled
        state.value = state.value.copy(enabled = if (enabled) allumes + reminder else allumes - reminder)
    }

    override suspend fun setTime(reminder: Reminder, time: LocalTime) {
        state.value = state.value.copy(times = state.value.times + (reminder to time))
    }

    override suspend fun permissionAsked(): Boolean = asked

    override suspend fun markPermissionAsked() {
        asked = true
    }
}
