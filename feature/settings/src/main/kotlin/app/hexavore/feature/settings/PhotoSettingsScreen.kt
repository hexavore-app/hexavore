package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.PhotoWeight

/**
 * Les photos gardées avec les plats.
 *
 * **Deux gestes et non un.** L'interrupteur décide de ce qui s'écrira ; le bouton
 * efface ce qui est écrit. Les fondre aurait fait disparaître des mois d'images à qui
 * voulait seulement cesser d'en accumuler.
 *
 * **Le poids est dit.** C'est la seule information qui permette de décider : « garder
 * les photos » ne veut rien dire tant qu'on ne sait pas ce que ça coûte, et personne ne
 * va compter ses fichiers.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@Composable
internal fun PhotoSettingsRoute(onClose: () -> Unit, viewModel: PhotoSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PhotoSettingsScreen(
        state = state,
        onKeeping = viewModel::onKeeping,
        onPurge = viewModel::onPurge,
        onClose = onClose,
    )
}

@Composable
private fun PhotoSettingsScreen(
    state: PhotoSettingsUiState,
    onKeeping: (Boolean) -> Unit,
    onPurge: () -> Unit,
    onClose: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.photos_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.photos_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            KeepingCard(keeping = state.keeping, onKeeping = onKeeping)
            StorageCard(weight = state.weight, onErase = { confirming = true })
        }
    }

    if (confirming) {
        PurgeConfirmation(
            weight = state.weight,
            onConfirm = {
                confirming = false
                onPurge()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun KeepingCard(keeping: Boolean, onKeeping: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.photos_keeping_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = keeping, onCheckedChange = onKeeping)
            }
            Text(
                text = stringResource(R.string.photos_keeping_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ce que les photos occupent, et de quoi les effacer.
 *
 * Le bouton disparaît quand il n'y a rien à effacer : un bouton qui ne fait rien
 * apprend à ne plus lire les boutons.
 */
@Composable
private fun StorageCard(weight: PhotoWeight, onErase: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = stringResource(R.string.photos_storage_title), style = MaterialTheme.typography.titleMedium)
            Text(text = weightText(weight), style = MaterialTheme.typography.bodyMedium)
            if (weight.count > 0) {
                NeonButton(
                    text = stringResource(R.string.photos_erase_action),
                    onClick = onErase,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PurgeConfirmation(weight: PhotoWeight, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photos_erase_title)) },
        text = { Text(pluralStringResource(R.plurals.photos_erase_body, weight.count, weight.count)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.photos_erase_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.photos_erase_cancel)) } },
    )
}

/**
 * « 12 photos, 2,4 Mo », ou l'absence de photo.
 *
 * Le nombre **et** le poids : l'un dit combien de repas on retrouverait, l'autre ce que
 * ça coûte. Les mégaoctets seuls ne disent rien à qui ne compte pas en octets.
 */
@Composable
private fun weightText(weight: PhotoWeight): String = when (weight.count) {
    0 -> stringResource(R.string.photos_storage_empty)
    else -> pluralStringResource(
        R.plurals.photos_storage_count,
        weight.count,
        weight.count,
        weight.bytes / BYTES_PER_MIB.toFloat(),
    )
}

private const val BYTES_PER_MIB = 1024 * 1024
