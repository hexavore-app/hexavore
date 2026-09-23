package app.hexavore.feature.search

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * La confirmation de suppression d'une fiche personnelle.
 *
 * Un dialogue et non une barre annulable : [docs/02][parcours] le réserve à ce qui
 * est destructif, et c'en est. La phrase change selon l'usage : une fiche jamais servie
 * se supprime sans conséquence, une fiche citée par des entrées demande qu'on dise ce
 * qu'elles deviennent. Elles survivent, avec leurs valeurs figées.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun DeleteConfirmation(deletion: FoodDeletion, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_delete_title, deletion.food.name)) },
        text = {
            Text(
                text = if (deletion.usedEntries > 0) {
                    pluralStringResource(R.plurals.search_delete_used, deletion.usedEntries, deletion.usedEntries)
                } else {
                    stringResource(R.string.search_delete_unused)
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.search_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_delete_cancel)) } },
    )
}
