package app.hexavore.integration.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSetup
import app.hexavore.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les rappels, posés dans le temps par `WorkManager`.
 *
 * ### Pourquoi pas une alarme exacte
 *
 * `AlarmManager.setExactAndAllowWhileIdle` sonnerait à la minute, et demande depuis
 * Android 12 la permission `SCHEDULE_EXACT_ALARM` — que Google n'accorde qu'aux
 * applications dont l'alarme *est* la fonction, un réveil ou un agenda. Une application
 * de nutrition ne l'obtiendrait pas, et la demander ferait refuser la publication.
 *
 * Et elle n'est pas nécessaire : un rappel de déjeuner à 12 h 35 au lieu de 12 h 30 ne
 * coûte rien. `WorkManager` peut décaler, il ne perd pas ([D134][decisions]).
 *
 * ### Un travail unique par rappel, remplacé à chaque fois
 *
 * Chacun porte un nom dérivé de son énumération, et `REPLACE` garantit qu'il n'en court
 * jamais deux : changer l'heure du déjeuner trois fois de suite ne laisse pas trois
 * rappels en attente. C'est aussi ce qui rend [reschedule] appelable à volonté — au
 * démarrage, après un réglage, après chaque sonnerie — sans jamais compter ce qui
 * existe déjà.
 *
 * **Ce qui est éteint s'annule.** Un rappel décoché doit cesser de sonner le jour même,
 * pas à la prochaine ouverture de l'application.
 *
 * [decisions]: docs/11-decisions.md
 */
@Singleton
class WorkManagerReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {
    override suspend fun reschedule(setup: ReminderSetup) {
        val work = WorkManager.getInstance(context)
        val now = clock.now().atZone(clock.zone())

        Reminder.entries.forEach { reminder ->
            if (reminder !in setup) {
                work.cancelUniqueWork(reminder.workName())
                return@forEach
            }

            val delay = delayUntil(target = setup[reminder], from = now, zone = clock.zone())
            work.enqueueUniqueWork(
                reminder.workName(),
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                    .setInputData(Data.Builder().putString(ReminderWorker.KEY_REMINDER, reminder.name).build())
                    .build(),
            )
        }
    }
}

/**
 * Le nom du travail d'un rappel.
 *
 * Dérivé de l'énumération et non écrit à la main : un cinquième rappel ne peut pas
 * arriver sans son nom, et deux ne peuvent pas partager le même — ce qui ferait qu'en
 * en planifiant un, on annulerait l'autre.
 */
private fun Reminder.workName(): String = "rappel." + name.lowercase()
