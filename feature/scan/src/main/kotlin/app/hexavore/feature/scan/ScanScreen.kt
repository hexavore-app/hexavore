package app.hexavore.feature.scan

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.ScrimSurface
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.FoodId
import app.hexavore.integration.scanner.BarcodeCamera
import androidx.core.content.ContextCompat as AndroidPermissions

/**
 * La modale de scan.
 *
 * **Quatre écrans en un**, et c'est [docs/02][parcours] qui les impose : on vise, on
 * attend, on n'a rien trouvé, ou la caméra est refusée. Les trois derniers proposent
 * tous une porte de sortie utile — créer la fiche à la main, avec son code-barres.
 *
 * **Rien ici n'est vérifiable sans appareil.** L'aperçu, la permission, la lampe et
 * le retour haptique ne s'éprouvent pas sur la JVM ; ce qui pouvait l'être est dans
 * [ScanViewModel] et dans `SteadyBarcode`.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun ScanRoute(
    onProduct: (FoodId) -> Unit,
    onCreateFood: (Barcode) -> Unit,
    onSearchByName: () -> Unit,
    onClose: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resumeKey by viewModel.resumeKey.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Le produit trouve fait quitter l'ecran : c'est une navigation, pas un etat a
    // afficher. La laisser dans l'etat obligerait a dessiner un ecran qu'on ne voit
    // jamais.
    LaunchedEffect(state) {
        val found = state as? ScanUiState.Found ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onProduct(found.food)
    }

    ScanScreen(
        state = state,
        resumeKey = resumeKey,
        actions = remember(viewModel, onCreateFood, onSearchByName, onClose) {
            ScanActions(
                onBarcode = viewModel::onBarcode,
                onFrame = viewModel::onFrame,
                onRetry = viewModel::onResume,
                onCreateFood = onCreateFood,
                onSearchByName = onSearchByName,
                onClose = onClose,
            )
        },
    )
}

/**
 * Ce que l'écran de scan peut faire, réuni.
 *
 * **Né quand le seuil de paramètres a mordu**, à l'arrivée de la trame figée, et le
 * regroupement suit ce que les choses sont : six lambdas, toutes des réactions de
 * l'écran. C'est la forme d'`EntryActions` et de `HomeActions`, appliquée là où le même
 * symptôme est apparu.
 */
@Immutable
internal data class ScanActions(
    val onBarcode: (Barcode) -> Unit,
    /** La trame sur laquelle le code a été lu, en JPEG. */
    val onFrame: (ByteArray) -> Unit,
    val onRetry: () -> Unit,
    val onCreateFood: (Barcode) -> Unit,
    val onSearchByName: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
private fun ScanScreen(state: ScanUiState, resumeKey: Int, actions: ScanActions) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            AndroidPermissions.checkSelfPermission(context, Manifest.permission.CAMERA) == PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    // Demandee a l'ouverture plutot que derriere un bouton : l'ecran n'a **aucun**
    // contenu sans camera, et faire tapoter une explication avant la seule question
    // qui compte est un ecran de transit de plus.
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize()) {
        if (granted) {
            BarcodeCamera(
                onBarcode = actions.onBarcode,
                modifier = Modifier.fillMaxSize(),
                onFrame = actions.onFrame,
                resumeKey = resumeKey,
            )
            Viewfinder(
                state = state,
                onRetry = actions.onRetry,
                onCreateFood = actions.onCreateFood,
                onSearchByName = actions.onSearchByName,
            )
            // Le bouton flotte sur l'apercu, seul de tout l'ecran a n'avoir aucun
            // panneau sous lui. Un libelle cyan pose sur un rayon de supermarche
            // eclaire ne se lit pas : il lui faut son propre voile.
            ScrimSurface(modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md), shape = Radius.pill) {
                CloseButton(actions.onClose)
            }
        } else {
            PermissionRefused(onAsk = { context.openAppSettings() })
            // Pas de voile ici : l'ecran de refus n'a pas de camera derriere lui, et
            // un panneau sombre y serait une pastille posee sur rien.
            CloseButton(actions.onClose, Modifier.align(Alignment.TopStart).padding(Spacing.md))
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClose, modifier = modifier) {
        Text(stringResource(R.string.scan_close))
    }
}

/**
 * Ce qui se pose par-dessus l'aperçu.
 *
 * En surimpression et jamais à la place : [docs/02][parcours] veut un chargement
 * inline, et un dialogue qui masquerait l'image ferait croire que la caméra a lâché.
 * Ce que la surimpression recouvre, dès la lecture confirmée, est la **trame figée**
 * sur laquelle le code a été lu — c'est `BarcodeCamera` qui la pose, et elle reste
 * là jusqu'à la reprise, y compris sous les deux issues d'échec ([D69][decisions]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun BoxScope.Viewfinder(
    state: ScanUiState,
    onRetry: () -> Unit,
    onCreateFood: (Barcode) -> Unit,
    onSearchByName: () -> Unit,
) {
    when (state) {
        ScanUiState.Scanning -> Hint(stringResource(R.string.scan_hint))
        ScanUiState.Looking -> Overlay {
            CircularProgressIndicator()
            Text(stringResource(R.string.scan_looking), style = MaterialTheme.typography.bodyMedium)
        }

        is ScanUiState.Unknown -> Outcome(
            title = stringResource(R.string.scan_unknown_title),
            body = stringResource(R.string.scan_unknown_body, state.code.value),
            code = state.code,
            onRetry = onRetry,
            onCreateFood = onCreateFood,
            onSearchByName = onSearchByName,
        )

        is ScanUiState.Unreachable -> Outcome(
            title = stringResource(R.string.scan_offline_title),
            body = stringResource(R.string.scan_offline_body, state.code.value),
            code = state.code,
            onRetry = onRetry,
            onCreateFood = onCreateFood,
            onSearchByName = onSearchByName,
        )

        // Trouve : la route emmene ailleurs, il n'y a rien a dessiner.
        is ScanUiState.Found -> Unit
    }
}

/**
 * Les trois issues de [docs/02][parcours] quand le produit n'est pas là.
 *
 * « Créer cet aliment » d'abord, parce que c'est celle qui fait avancer : la fiche
 * gardera le code, et le produit cessera d'être un cas particulier au prochain scan.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun BoxScope.Outcome(
    title: String,
    body: String,
    code: Barcode,
    onRetry: () -> Unit,
    onCreateFood: (Barcode) -> Unit,
    onSearchByName: () -> Unit,
) {
    Overlay {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        NeonButton(text = stringResource(R.string.scan_create), onClick = { onCreateFood(code) })
        TextButton(onClick = onSearchByName) { Text(stringResource(R.string.scan_search_by_name)) }
        TextButton(onClick = onRetry) { Text(stringResource(R.string.scan_retry)) }
    }
}

@Composable
private fun PermissionRefused(onAsk: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.scan_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        NeonButton(text = stringResource(R.string.scan_permission_settings), onClick = onAsk)
    }
}

@Composable
private fun BoxScope.Hint(text: String) {
    ScrimSurface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(Spacing.lg),
        shape = RoundedCornerShape(Radius.field),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun BoxScope.Overlay(content: @Composable () -> Unit) {
    ScrimSurface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        // Le panneau est au ras du bord bas : seuls ses coins hauts s'arrondissent,
        // et c'est ce qui le fait lire comme une feuille posee sur l'image plutot
        // que comme un assombrissement de l'apercu.
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/**
 * Une permission refusée deux fois ne se redemande plus : Android ne rouvre pas la
 * boîte système. Le seul chemin restant passe par les réglages de l'application, et
 * l'écran le dit plutôt que de laisser un bouton sans effet.
 */
private fun android.content.Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private const val PERMISSION_GRANTED = android.content.pm.PackageManager.PERMISSION_GRANTED
