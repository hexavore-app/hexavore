package app.hexavore.feature.entry

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.hexavore.core.designsystem.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Les trois boîtes de l'en-tête : la date où copier, la suppression, et le signalement.
 *
 * Elles vivent ensemble parce qu'elles répondent à la même règle : **un geste qui sort
 * de l'écran demande d'abord si c'est bien ce qu'on voulait** ([D143][decisions]).
 * Copier écrit ailleurs, supprimer efface, signaler ouvre un courriel — aucun des trois
 * ne se défait d'un retour en arrière.
 *
 * [decisions]: docs/11-decisions.md
 */

/**
 * Le calendrier où choisir le jour de la copie.
 *
 * **Pas de jour à venir**, comme partout ailleurs : on note ce qu'on a mangé, et le
 * futur ne se mange pas ([décision par défaut n°10][parcours]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CopyDateDialog(today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val limite = remember(today) { today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
    val state = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis < limite
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(it.toLocalDate()) } },
            ) { Text(text = stringOf(R.string.entry_copy_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringOf(R.string.entry_copy_cancel)) }
        },
    ) {
        DatePicker(
            state = state,
            // Le titre porte lui-meme ses marges : la boite ne lui en donne aucune, et
            // sans elles il se posait dans le coin arrondi, qui lui rognait sa premiere
            // lettre. Ce sont celles du titre que Material pose quand on le laisse ecrire
            // le sien -- l'ecart avec le calendrier en dessous vient de la.
            title = {
                Text(
                    text = stringOf(R.string.entry_copy_title),
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.md, top = Spacing.lg),
                )
            },
        )
    }
}

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

/**
 * Le jour que le calendrier a rendu.
 *
 * Il compte en UTC, et c'est voulu : la date choisie est un jour du calendrier, pas un
 * instant. La relire dans le fuseau local décalerait d'un jour tous ceux qui sont à
 * l'ouest de Greenwich.
 */
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun stringOf(id: Int): String = androidx.compose.ui.res.stringResource(id)
