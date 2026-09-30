package app.hexavore.domain.usecase

import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSettings
import app.hexavore.domain.reminder.ReminderSetup
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/** Ce que l'utilisateur a réglé, pour l'écran qui le montre. */
class ObserveReminders(private val settings: ReminderSettings) {
    operator fun invoke(): Flow<ReminderSetup> = settings.observe()
}

/**
 * Change un rappel, **et le replace dans le temps**.
 *
 * **Les deux gestes n'en font qu'un**, et c'est toute la raison de ce cas d'usage
 * ([D134][decisions]). Écrire le réglage sans replanifier laisse le rappel sonner à
 * l'ancienne heure jusqu'au prochain lancement de l'application — c'est-à-dire jusqu'à
 * ce que quelqu'un l'ouvre pour comprendre pourquoi son réglage n'a rien changé.
 *
 * Laisser l'écran appeler les deux aurait marché, une fois. Le jour où un second écran
 * règle un rappel, il en oublie un.
 *
 * **La replanification porte sur tout**, et non sur le seul rappel touché : c'est un
 * appel de plus qui ne coûte rien — chaque travail est unique et remplacé — et une
 * branche de moins à se tromper.
 *
 * [decisions]: docs/11-decisions.md
 */
class ChooseReminder(private val settings: ReminderSettings, private val scheduler: ReminderScheduler) {
    suspend fun setEnabled(reminder: Reminder, enabled: Boolean) {
        settings.setEnabled(reminder, enabled)
        scheduler.reschedule(settings.current())
    }

    suspend fun setTime(reminder: Reminder, time: LocalTime) {
        settings.setTime(reminder, time)
        scheduler.reschedule(settings.current())
    }
}
