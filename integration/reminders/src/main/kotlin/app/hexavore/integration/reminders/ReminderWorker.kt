package app.hexavore.integration.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSettings
import app.hexavore.domain.usecase.ShouldRemind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Ce qui sonne, et **ce qui se tait**.
 *
 * ### Il se replanifie lui-même
 *
 * Un travail unique qui replanifie le suivant, plutôt qu'un travail périodique. Trois
 * raisons, et la première suffirait : `PeriodicWorkRequest` n'accepte pas d'heure, mais
 * un intervalle — un rappel « toutes les 24 h » glisserait à chaque fois que le système
 * décale l'exécution, et finirait par sonner à n'importe quelle heure au bout de
 * quelques semaines. Ensuite, l'heure se règle, donc la replanification doit de toute
 * façon savoir recalculer. Enfin, un jour de changement d'heure fait vingt-trois ou
 * vingt-cinq heures, et seul un calcul de calendrier le sait ([delayUntil]).
 *
 * **Il se replanifie avant de décider s'il sonne**, et dans tous les cas — même quand
 * il se tait, même quand la permission manque. Un rappel qui ne se replanifierait
 * qu'après avoir sonné s'éteindrait définitivement le premier jour où il n'a rien à
 * dire, c'est-à-dire exactement le jour où tout va bien.
 *
 * ### Il se tait le plus souvent
 *
 * C'est [ShouldRemind] qui décide, et c'est la contrepartie qui rend ces notifications
 * tenables ([D134][decisions]) : un rappel qui sonne tous les jours à la même heure
 * quoi qu'on fasse devient prévisible, donc ignoré, puis coupé au niveau du système —
 * et l'on perd alors le canal entier.
 *
 * ### Il ne demande rien
 *
 * La permission se demande à l'écran, jamais ici : un travail de fond ne peut pas
 * afficher de boîte de dialogue, et une notification postée sans permission est
 * simplement ignorée par le système. Il vérifie donc, et s'abstient — sans erreur,
 * parce que ce n'en est pas une.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltWorker
internal class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val shouldRemind: ShouldRemind,
    private val settings: ReminderSettings,
    private val scheduler: ReminderScheduler,
    private val look: NotificationLook,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val reminder = inputData.getString(KEY_REMINDER)
            ?.let { name -> Reminder.entries.firstOrNull { it.name == name } }
            ?: return Result.success()

        // D'abord replanifier : un rappel qui ne le ferait qu'apres avoir sonne
        // s'eteindrait le premier jour ou il n'a rien a dire.
        scheduler.reschedule(settings.current())

        if (shouldRemind(reminder)) notify(reminder)
        return Result.success()
    }

    private fun notify(reminder: Reminder) {
        if (!allowed()) return

        ensureChannel()
        val corps = context.getString(reminder.body())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // **La marque, et non le « i » du systeme.** C'etait `ic_dialog_info`, que
            // tout le monde reconnait precisement comme n'appartenant a personne : une
            // notification sans visage ne se distingue pas de la quinzaine d'autres qui
            // attendent dans le volet (D152).
            .setSmallIcon(look.icon)
            // La teinte que le systeme pose sur l'icone et sur le nom de
            // l'application. Sans elle, les deux restent gris.
            .setColor(ContextCompat.getColor(context, look.color))
            .setContentTitle(context.getString(reminder.title()))
            .setContentText(corps)
            // **Deplie, le texte reste entier.** Une ligne de volet coupe a peu pres
            // quarante caracteres, et la phrase qui dit quoi faire en fait davantage :
            // repliee, elle s'arretait avant d'avoir dit l'essentiel.
            .setStyle(NotificationCompat.BigTextStyle().bigText(corps))
            // Ce que c'est, pour le systeme : un rappel. C'est ce qui lui permet de la
            // ranger, de la laisser passer en mode concentration si l'utilisateur
            // l'autorise, et de ne pas la confondre avec un message.
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // **Elle ouvre l'application**, et c'est tout ce qu'on lui demande : un
            // rappel de repas sert a ouvrir l'ecran ou l'on note, pas a choisir un mode
            // de saisie depuis l'ecran de verrouillage (D136).
            .setContentIntent(openApp())
            // Elle disparait quand on la touche : un rappel qu'il faut balayer une
            // seconde fois est un rappel de trop.
            .setAutoCancel(true)
            .build()

        // **La permission est reverifiee ici**, a un pas de l'envoi, et non seulement
        // dans `allowed`. Android Lint ne suit pas l'appel indirect et refuserait le
        // build ; la redondance lui repond, et elle ferme aussi la fenetre entre les
        // deux -- une permission retiree entre-temps leve une `SecurityException`, que
        // le `runCatching` absorbe plutot que de faire echouer un travail deja
        // replanifie.
        runCatching {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(reminder.ordinal, notification)
            }
        }
    }

    /**
     * Ce que la notification ouvre : l'application, telle que le lanceur l'ouvre.
     *
     * **L'intention du lanceur plutôt qu'une classe nommée.** Ce module ne connaît pas
     * `MainActivity` — il est un adaptateur, et `:integration` ne dépend jamais de
     * `:app` — et la demander au gestionnaire de paquets donne exactement ce qu'un
     * appui sur l'icône donnerait, y compris si l'activité de départ change un jour.
     *
     * `FLAG_IMMUTABLE` parce qu'Android 12 l'exige, et parce que rien ici n'a besoin
     * que le système complète l'intention.
     */
    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * La permission, à la seule version qui la demande.
     *
     * En dessous d'Android 13, poster une notification ne demande rien : la vérifier
     * quand même la ferait refuser partout, puisque le système ne l'accorde pas.
     */
    private fun allowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * Le canal, créé à la demande.
     *
     * **Un seul pour les quatre rappels.** Quatre canaux laisseraient couper le
     * déjeuner sans couper le dîner, ce qui est déjà ce que les réglages de
     * l'application permettent — en mieux, puisqu'ils peuvent aussi déplacer l'heure.
     * Un canal par rappel ferait deux endroits qui disent la même chose, dont un que
     * l'application ne voit pas.
     */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Deja cree, ou pas de gestionnaire : dans les deux cas il n'y a rien a faire,
        // et c'est la meme absence de travail.
        val manager = context.getSystemService(NotificationManager::class.java)
            ?.takeIf { it.getNotificationChannel(CHANNEL_ID) == null }
            ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reminder_channel_description) },
        )
    }

    internal companion object {
        const val KEY_REMINDER = "rappel"

        const val CHANNEL_ID = "hexavore.reminders"
    }
}

@StringRes
private fun Reminder.title(): Int = when (this) {
    Reminder.BREAKFAST -> R.string.reminder_breakfast_title
    Reminder.LUNCH -> R.string.reminder_lunch_title
    Reminder.DINNER -> R.string.reminder_dinner_title
    Reminder.STREAK -> R.string.reminder_streak_title
}

@StringRes
private fun Reminder.body(): Int = when (this) {
    Reminder.BREAKFAST -> R.string.reminder_breakfast_body
    Reminder.LUNCH -> R.string.reminder_lunch_body
    Reminder.DINNER -> R.string.reminder_dinner_body
    Reminder.STREAK -> R.string.reminder_streak_body
}
