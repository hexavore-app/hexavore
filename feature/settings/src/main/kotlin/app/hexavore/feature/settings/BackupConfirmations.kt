package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.theme.Spacing

/**
 * « Restaurer remplace tout. »
 *
 * **Un dialogue avant le sélecteur et non après.** Poser la question une fois le
 * fichier choisi obligerait à revenir en arrière pour rien ; la poser avant coûte un
 * geste à celui qui abandonne, et zéro à celui qui continue. Ce qu'il annonce est vrai
 * dans les deux cas : le remplacement est complet, jamais une fusion.
 *
 * Il dit aussi ce qui **ne** sera pas perdu — la copie de sécurité prise juste avant
 * l'écrasement. C'est ce qui distingue un avertissement d'une menace.
 */
@Composable
internal fun RestoreWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_restore_confirm_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

/**
 * « Revenir à l'état d'avant ? » ([D162][decisions])
 *
 * Le geste remplace le journal présent, comme une restauration, et la boîte le dit. Elle
 * dit aussi ce qui le rend sans risque : l'état qu'on quitte devient à son tour la copie
 * de sécurité, et le retour se défait de la même façon.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun UndoWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_undo_confirm_title)) },
        text = { Text(stringResource(R.string.backup_undo_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_undo_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

/**
 * « Effacer toutes mes données », et il faut écrire le mot.
 *
 * **La friction est intentionnelle** ([docs/09][donnees]). C'est le seul geste du
 * projet qui détruit sans retour possible : la barre d'annulation ne le rattrape pas,
 * la copie de sécurité d'avant restauration part avec le reste ([D162][decisions]), et
 * une double confirmation par boutons s'apprend à traverser en deux frappes.
 *
 * Taper un mot est le seul verrou qui demande de **lire**. Le champ est un
 * [DraftTextField] comme partout ailleurs — non contrôlé, et il refuse une frappe
 * plutôt que de la nettoyer ([D45][decisions]).
 *
 * La comparaison ignore la casse et les espaces de bord : quelqu'un qui écrit
 * « supprimer » sur un clavier sans majuscules automatiques a lu la phrase et l'a
 * comprise, et lui refuser le geste ne protège plus rien — cela punit son clavier.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun EraseConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val word = stringResource(R.string.backup_erase_word)
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_erase_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.backup_erase_confirm_body, word),
                    style = MaterialTheme.typography.bodyMedium,
                )
                DraftTextField(
                    initial = "",
                    onValueChange = { typed = it },
                    label = word,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.matches(word)) {
                Text(stringResource(R.string.backup_erase_confirm_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

/**
 * Le mot est-il écrit ?
 *
 * Une fonction nommée plutôt qu'une comparaison en ligne, parce que c'est une **règle**
 * et qu'elle a deux tolérances à justifier : la casse et les espaces de bord. Elles ne
 * se relisent pas dans un `equals` posé au milieu d'un `enabled`.
 */
internal fun String.matches(word: String): Boolean = trim().equals(word, ignoreCase = true)
