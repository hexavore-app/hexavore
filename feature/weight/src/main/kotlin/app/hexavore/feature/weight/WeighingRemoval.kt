package app.hexavore.feature.weight

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.theme.Timing
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.usecase.TrendPoint
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Retirer une pesée, et la rendre ([D165][decisions]) : ce que la liste et la barre
 * d'annulation ont à se dire.
 *
 * [decisions]: docs/11-decisions.md
 */
internal data class WeighingRemoval(
    /** La pesée qu'on vient de retirer, tant qu'elle se rattrape ; `null` sinon. */
    val pending: WeightEntry? = null,
    val onDelete: (TrendPoint) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onExpired: () -> Unit = {},
)

/**
 * La barre qui rend une pesée retirée, cinq secondes.
 *
 * La même que celle d'un plat sur l'accueil, et pour les mêmes raisons : la fenêtre est
 * tenue par le délai de `Timing`, qui ne suit pas le réglage d'animations réduites, et
 * la barre par un affichage indéfini — `SnackbarDuration` n'offre que quatre ou dix
 * secondes.
 */
@Composable
internal fun rememberUndoBar(removal: WeighingRemoval): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.weight_deleted)
    val undo = stringResource(R.string.weight_undo)

    LaunchedEffect(removal.pending) {
        if (removal.pending == null) return@LaunchedEffect
        val result = withTimeoutOrNull(Timing.UNDO_WINDOW_MILLIS) {
            host.showSnackbar(message = deleted, actionLabel = undo, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) removal.onUndo() else removal.onExpired()
    }

    return host
}
