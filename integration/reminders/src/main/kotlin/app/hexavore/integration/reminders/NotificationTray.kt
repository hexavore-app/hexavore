package app.hexavore.integration.reminders

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.hexavore.domain.reminder.PostedReminders
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Efface les rappels affichés.
 *
 * **Ouvrir l'application les rend caducs**, tous, quel que soit ce qu'on vient y faire
 * ([D136][decisions]). Un rappel dit *« il y a quelque chose à noter »* ; celui qui a
 * ouvert l'écran où l'on note l'a entendu, et le laisser dans le tiroir revient à le
 * répéter jusqu'au lendemain.
 *
 * C'est aussi ce qui corrige un cas qui passait pour un défaut : le rappel de 8 h
 * sonnait à 8 h, alors que rien n'était noté — il avait raison — puis restait affiché
 * pendant qu'on notait son petit-déjeuner à 8 h 29, et il avait l'air de se tromper.
 * Vérifier au moment de sonner ne suffit pas : une notification vit plus longtemps que
 * l'instant où elle est postée.
 *
 * **Toutes et non celle du moment** : il n'y en a qu'une à la fois en pratique, et
 * choisir laquelle effacer demanderait de savoir laquelle a été vue — ce que personne
 * ne sait.
 *
 * [decisions]: docs/11-decisions.md
 */
@Singleton
class NotificationTray @Inject constructor(@ApplicationContext private val context: Context) : PostedReminders {
    override fun clear() {
        // Aucune permission n'est requise pour retirer ce qu'on a soi-meme pose, et
        // l'appel ne fait rien de plus quand il n'y a rien a retirer.
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }
}
