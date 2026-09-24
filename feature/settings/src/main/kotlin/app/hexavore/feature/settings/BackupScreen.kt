package app.hexavore.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing

/**
 * La sauvegarde locale : emporter ses données, les remettre, ou tout effacer.
 *
 * **C'est la garantie de réversibilité du projet** ([docs/09][donnees]) : un format
 * ouvert et un export qui fonctionne valent mieux que toutes les promesses de
 * non-enfermement. L'écran ne parle pas encore de Google Drive — il attend que le local
 * soit complet, et ce qu'il propose ici n'a besoin d'aucun compte.
 *
 * **Les trois gestes sur le même écran, et le dernier en dessous.** Effacer n'est pas
 * une variante d'exporter ; il est séparé par un bouton de contour et un dialogue qui
 * demande d'écrire un mot. La proximité est voulue malgré tout : quelqu'un qui veut
 * partir doit voir l'export juste au-dessus du bouton qui détruit.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
@Composable
internal fun BackupRoute(onClose: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // **Les octets attendent le document, jamais l'inverse.** `onExport` les produit
    // puis rappelle ce bloc avec le `Uri` que le systeme vient de rendre : ce qui est
    // ecrit decrit donc l'instant de la demande, et non celui ou l'utilisateur a fini
    // de parcourir ses dossiers.
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        uri?.let { chosen -> viewModel.onExport { writeDocument(context, chosen) } }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImport { uri?.let { chosen -> readDocument(context, chosen) } }
    }

    BackupScreen(
        state = state,
        onExport = { createDocument.launch(viewModel.proposedName()) },
        onImport = { openDocument.launch(arrayOf(BACKUP_MIME, LEGACY_MIME, ANY_MIME)) },
        onErase = viewModel::onErase,
        onMessageShown = viewModel::onMessageShown,
        onClose = onClose,
    )
}

@Composable
private fun BackupScreen(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onErase: () -> Unit,
    onMessageShown: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmingRestore by rememberSaveable { mutableStateOf(false) }
    var confirmingErase by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { wording(it) }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        snackbar.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.backup_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.backup_close),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            BackupCards(
                enabled = !state.busy,
                onExport = onExport,
                onImport = { confirmingRestore = true },
                onErase = { confirmingErase = true },
            )
        }
    }

    BackupConfirmations(
        confirmingRestore = confirmingRestore,
        confirmingErase = confirmingErase,
        onRestore = {
            confirmingRestore = false
            onImport()
        },
        onErase = {
            confirmingErase = false
            onErase()
        },
        onDismissRestore = { confirmingRestore = false },
        onDismissErase = { confirmingErase = false },
    )
}

/** Les trois gestes, dans l'ordre où l'on veut les rencontrer : emporter, remettre, effacer. */
@Composable
private fun BackupCards(enabled: Boolean, onExport: () -> Unit, onImport: () -> Unit, onErase: () -> Unit) {
    ActionCard(
        title = stringResource(R.string.backup_export_title),
        body = stringResource(R.string.backup_export_body),
        action = stringResource(R.string.backup_export_action),
        enabled = enabled,
        onClick = onExport,
    )
    ActionCard(
        title = stringResource(R.string.backup_import_title),
        body = stringResource(R.string.backup_import_body),
        action = stringResource(R.string.backup_import_action),
        enabled = enabled,
        onClick = onImport,
    )
    EraseCard(enabled = enabled, onClick = onErase)
}

/**
 * Les deux boîtes qui gardent les gestes destructeurs.
 *
 * Sorties du corps de l'écran quand le seuil de longueur a mordu, et le découpage suit
 * ce que les choses sont : au-dessus, la page et ses trois cartes ; ici, ce qui
 * s'interpose entre un doigt et une donnée qui disparaît.
 */
@Composable
private fun BackupConfirmations(
    confirmingRestore: Boolean,
    confirmingErase: Boolean,
    onRestore: () -> Unit,
    onErase: () -> Unit,
    onDismissRestore: () -> Unit,
    onDismissErase: () -> Unit,
) {
    if (confirmingRestore) {
        RestoreWarningDialog(onConfirm = onRestore, onDismiss = onDismissRestore)
    }
    if (confirmingErase) {
        EraseConfirmDialog(onConfirm = onErase, onDismiss = onDismissErase)
    }
}

/** Un titre, ce que le geste fait, et le bouton qui le fait. */
@Composable
private fun ActionCard(title: String, body: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClick, enabled = enabled) { Text(action) }
        }
    }
}

/**
 * Le geste destructeur, et il ne ressemble pas aux deux autres.
 *
 * Un bouton de contour dans les couleurs d'erreur plutôt qu'un bouton plein : la forme
 * dit ce que le libellé dit, et les trois cartes ne se traversent plus du regard comme
 * trois variantes du même geste. C'est la même grammaire que le reste de l'application,
 * où une propriété se signale par une **forme** et pas seulement par une couleur.
 */
@Composable
private fun EraseCard(enabled: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.backup_erase_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.backup_erase_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onClick, enabled = enabled) {
                Text(
                    text = stringResource(R.string.backup_erase_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Ce que le compte rendu dit, en toutes lettres.
 *
 * La mise en mots vit ici et non dans le `ViewModel` : chaque issue a sa phrase, et
 * ranger des chaînes traduites dans un modèle les rendrait insensibles à la langue de
 * l'appareil.
 */
@Composable
private fun wording(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> exportedText(message.sizeBytes)
    BackupMessage.ExportFailed -> stringResource(R.string.backup_export_failed)
    is BackupMessage.Restored -> stringResource(R.string.backup_restored, message.entryCount)
    is BackupMessage.TooRecent -> stringResource(R.string.backup_too_recent, message.formatVersion)
    BackupMessage.Unreadable -> stringResource(R.string.backup_unreadable)
    BackupMessage.RestoreFailed -> stringResource(R.string.backup_restore_failed)
    BackupMessage.Erased -> stringResource(R.string.backup_erased)
}

/**
 * Le type que le sélecteur propose d'abord.
 *
 * Une archive, depuis que les photos voyagent avec le journal. Le déclarer pour ce qu'il
 * est évite qu'une application de fichiers renomme le document.
 */
private const val BACKUP_MIME = "application/zip"

/**
 * Le format d'avant, encore acceptable à l'ouverture.
 *
 * Un export écrit quand le journal voyageait seul est un gzip nu. Il se relit, et le
 * sélecteur ne doit pas le griser pour autant.
 */
private const val LEGACY_MIME = "application/gzip"

/**
 * Le second type accepté à l'ouverture, et il est nécessaire.
 *
 * Bien des fournisseurs de documents — les pièces jointes de courriel, certains nuages
 * — annoncent `application/octet-stream` pour tout. Filtrer strictement rendrait la
 * sauvegarde grisée et impossible à choisir, sans dire pourquoi.
 */
private const val ANY_MIME = "application/octet-stream"

/**
 * « Exporté, 84 Ko », ou « Exporté, 214,1 Mo ».
 *
 * Deux libellés parce que la taille a changé d'ordre de grandeur le jour où les photos
 * sont entrées dans l'archive : un journal seul pèse quelques dizaines de kilo-octets,
 * une année de photos deux cents mégaoctets, et « 214 072 Ko » ne se lit pas.
 */
@Composable
private fun exportedText(bytes: Long): String = when {
    bytes < BYTES_PER_MIB -> stringResource(R.string.backup_exported, bytes / BYTES_PER_KIB)
    else -> stringResource(R.string.backup_exported_large, bytes / BYTES_PER_MIB.toFloat())
}

private const val BYTES_PER_KIB = 1024

private const val BYTES_PER_MIB = 1024 * 1024
