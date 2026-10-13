package app.hexavore.feature.entry

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Deux des trois boîtes de l'en-tête : la suppression, et le signalement.
 *
 * Elles vivent ensemble parce qu'elles répondent à la même règle : **un geste qui sort
 * de l'écran demande d'abord si c'est bien ce qu'on voulait** ([D143][decisions]).
 * Supprimer efface, signaler ouvre un courriel — aucun des deux ne se défait d'un retour
 * en arrière.
 *
 * La troisième, le calendrier de la copie, est partie dans le design system quand
 * l'accueil a appris à copier un plat lui aussi ([D157][decisions]) : deux écrans, une
 * seule boîte.
 *
 * [decisions]: docs/11-decisions.md
 */

/**
 * La confirmation d'une suppression.
 *
 * Un dialogue et non une barre annulable : ce n'est pas l'irréversibilité qui le
 * justifie mais le **volume** — un plat entier disparaît, pas une ligne
 * ([D61][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun DeleteDishDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringOf(R.string.entry_delete_title)) },
        text = { Text(text = stringOf(R.string.entry_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringOf(R.string.entry_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringOf(R.string.entry_delete_cancel)) }
        },
    )
}

/**
 * Ce que le bouton en forme d'insecte fait, dit avant de le faire.
 *
 * **Personne ne clique sur une icône qu'il ne comprend pas**, et ceux qui le font ne
 * savent pas qu'un courriel va s'ouvrir avec leur photo en pièce jointe. La boîte dit
 * les deux : à quoi ça sert, et ce qui part ([D143][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ReportDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringOf(R.string.entry_report_explain_title)) },
        text = { Text(text = stringOf(R.string.entry_report_explain_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringOf(R.string.entry_report_explain_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringOf(R.string.entry_report_explain_cancel)) }
        },
    )
}

@Composable
private fun stringOf(id: Int): String = androidx.compose.ui.res.stringResource(id)
