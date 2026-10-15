package app.hexavore.core.designsystem.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.R

/**
 * La caméra est refusée pour de bon, et l'écran dit où l'autoriser ([D162][decisions]).
 *
 * Refusée deux fois, la permission ne se redemande plus : Android rend « refusé » sans
 * rien afficher, et « Prendre une photo » ne faisait plus rien. **Un dialogue et non une
 * barre** : la seule issue est hors de l'application, et elle demande un bouton — qui
 * ouvre les réglages d'Hexavore dans Android. La phrase nomme aussi l'autre chemin, la
 * galerie, qui marche sans permission.
 *
 * Partagé par l'écran d'analyse et l'écran de validation, comme la boîte de copie
 * ([D157][decisions]) : deux écrans, une seule boîte.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun CameraRefusedDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ds_camera_refused_title)) },
        text = { Text(text = stringResource(R.string.ds_camera_refused_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    // Un telephone sans ecran de reglages par application n'a rien a
                    // ouvrir : la boite se referme, la phrase a deja dit ou aller.
                    runCatching { context.openAppSettings() }
                },
            ) { Text(text = stringResource(R.string.ds_camera_refused_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.ds_camera_refused_close)) }
        },
    )
}

/** Les réglages d'Hexavore dans Android : le seul chemin vers une permission refusée pour de bon. */
private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
