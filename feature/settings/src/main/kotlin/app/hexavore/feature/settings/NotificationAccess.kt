package app.hexavore.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Les notifications d'Hexavore sonneront-elles ?
 *
 * **Ce que le système en dit, et non la seule permission** ([D163][decisions]). Avant
 * Android 13, il n'y a pas de permission à accorder, mais on peut éteindre les
 * notifications d'une application dans ses réglages : la lecture de la permission les
 * déclarait permises, et l'écran se taisait sur des rappels qui ne sonneraient pas.
 * Depuis Android 13, la même lecture rend aussi compte de la permission.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun Context.notificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/**
 * Android rouvrira-t-il sa boîte si l'on redemande ?
 *
 * Refusée deux fois, la permission ne se redemande plus : Android rend « refusé » sans
 * rien afficher, et « Autoriser les notifications » ne faisait alors plus rien du tout
 * ([D163][decisions]). Ici, la question a toujours été posée avant — par l'onboarding, ou
 * par l'accueil pour qui l'avait installé avant les rappels —, donc une réponse négative
 * veut dire « refusée pour de bon », ou « boîte balayée sans répondre » : dans les deux
 * cas, les réglages d'Android sont un chemin qui marche.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun Context.mayAskNotifications(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    findActivity()?.let(::willAskAgain) == true

private fun willAskAgain(activity: Activity): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

/**
 * L'écran des notifications d'Hexavore dans Android : là où elles s'accordent quand la
 * boîte du système ne s'ouvre plus, et là où elles se rallument avant Android 13.
 *
 * Un téléphone sans cet écran n'a rien à ouvrir : l'appui ne fait rien, et la phrase
 * au-dessus du bouton a déjà dit ce qui bloque.
 */
internal fun Context.openNotificationSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
