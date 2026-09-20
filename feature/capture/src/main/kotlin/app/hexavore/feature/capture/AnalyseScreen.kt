package app.hexavore.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.component.aiErrorMessage
import app.hexavore.core.designsystem.component.diagnostic
import app.hexavore.core.designsystem.theme.Spacing

/** L'écran d'IA, branché sur le graphe d'injection. */
@Composable
internal fun AnalyseRoute(
    onProposal: () -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
    viewModel: AnalyseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val capture = rememberMealCapture(onJpeg = viewModel::onPhoto)

    // La proposition est deposee : l'ecran cede la place. `onNavigated` referme le
    // drapeau, sans quoi revenir corriger une phrase repartirait aussitot vers une
    // validation dont le depot est vide.
    LaunchedEffect(state.analysed) {
        if (state.analysed) {
            viewModel.onNavigated()
            onProposal()
        }
    }

    AnalyseScreen(
        state = state,
        capture = capture,
        actions = AnalyseActions(
            onRemovePhoto = viewModel::onRemovePhoto,
            onText = viewModel::onText,
            onAnalyse = viewModel::onAnalyse,
            onCancel = viewModel::onCancel,
            onConsent = viewModel::onConsent,
            onConsentDeclined = viewModel::onConsentDeclined,
            onManual = onManual,
            onClose = onClose,
        ),
    )
}

/**
 * Un cadre pour l'image, un cadre pour le texte, un bouton en bas.
 *
 * **Un seul écran pour les deux modes** ([D120][decisions]). « Photographier » et
 * « Décrire » étaient deux modales qui ne différaient que par ce qu'elles envoyaient :
 * même reconnaissance, même dépôt, mêmes erreurs, même sortie. Ici, une photo, une
 * phrase, ou les deux — et le bouton s'allume dès que l'un des deux existe.
 *
 * **L'ordre dit ce qui est facultatif.** L'image est en haut parce que c'est elle
 * qu'on prend d'abord quand on en prend une ; le texte la précise en dessous, et
 * décrit tout seul quand il n'y a pas d'image. Son libellé change selon le cas : un
 * champ qui demande « décrivez votre repas » sous une photo ferait tout retaper.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun AnalyseScreen(state: AnalyseUiState, capture: MealCapture, actions: AnalyseActions) {
    if (state.consentNeeded) {
        ConsentDialog(provider = state.provider, onAccept = actions.onConsent, onDecline = actions.onConsentDeclined)
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.analyse_title),
                onClose = actions.onClose,
                closeLabel = stringResource(R.string.analyse_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.screenMargin)
                // Le clavier reduit la place : sans defilement, le bouton d'analyse
                // passerait dessous des qu'on ecrit sur trois lignes.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PhotoFrame(
                photo = state.photo,
                capture = capture,
                onRemove = actions.onRemovePhoto,
                modifier = Modifier.height(FrameHeight),
            )

            DraftTextField(
                initial = state.text,
                onValueChange = actions.onText,
                label = stringResource(
                    if (state.photo == null) R.string.analyse_describe_label else R.string.analyse_note_label,
                ),
                modifier = Modifier.fillMaxWidth(),
                minLines = TEXT_LINES,
                maxLines = TEXT_LINES,
            )
            Text(
                text = stringResource(
                    if (state.photo == null) R.string.analyse_describe_example else R.string.analyse_note_example,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Analysis(state, actions.onAnalyse, actions.onCancel)
            Failure(state, actions.onManual)
        }
    }
}

/**
 * Le bouton d'analyse, et **l'annulation qui coupe vraiment**.
 *
 * [docs/02][parcours] l'écrit noir sur blanc, et c'est une question d'argent autant
 * que de patience : une requête abandonnée qu'on laisse courir se paie quand même.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun Analysis(state: AnalyseUiState, onAnalyse: () -> Unit, onCancel: () -> Unit) {
    if (state.analysing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(Spacing.xs))
            Text(text = stringResource(R.string.analyse_running), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCancel) { Text(stringResource(R.string.analyse_cancel)) }
        }
        return
    }

    NeonButton(
        text = stringResource(R.string.analyse_submit),
        onClick = onAnalyse,
        modifier = Modifier.fillMaxWidth(),
        style = NeonButtonStyle.FILLED,
        availability = if (state.analysable) NeonButtonAvailability.AVAILABLE else NeonButtonAvailability.DISABLED,
    )
}

/**
 * L'échec, et la porte de sortie.
 *
 * Ce qui a été saisi reste : réessayer ne redemande ni de ressortir le téléphone
 * au-dessus d'une assiette qu'on est peut-être en train de manger, ni de retaper une
 * phrase. Et la saisie manuelle est offerte, parce qu'un fournisseur en panne ne doit
 * pas empêcher de noter son repas.
 */
@Composable
private fun Failure(state: AnalyseUiState, onManual: () -> Unit) {
    val error = state.error ?: return

    Text(text = aiErrorMessage(error), style = MaterialTheme.typography.bodyMedium)
    error.diagnostic?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = onManual) { Text(stringResource(R.string.analyse_manual)) }
}

/**
 * Ce qui se dit **une fois**, avant que la première photo parte.
 *
 * [docs/05][ia] § Confidentialité l'exige, et exige qu'il nomme le fournisseur : « votre
 * photo part chez Mistral » se vérifie, « chez votre fournisseur » non. Refuser laisse
 * la photo en place — on peut changer d'avis sans reprendre la photo.
 *
 * **Il ne se montre que pour une photo**, jamais pour une phrase : celui qui écrit sait
 * exactement ce qu'il envoie, là où une image emporte aussi ce qui entoure l'assiette.
 *
 * [ia]: docs/05-ia.md
 */
@Composable
private fun ConsentDialog(provider: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.analyse_consent_title)) },
        text = {
            Text(
                if (provider.isBlank()) {
                    stringResource(R.string.analyse_consent_unnamed)
                } else {
                    stringResource(R.string.analyse_consent, provider)
                },
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.analyse_consent_accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.analyse_consent_decline)) } },
    )
}

/**
 * Les gestes de l'écran, rassemblés.
 *
 * Huit rappels passés un par un feraient une signature qu'on ne lit plus, et c'est la
 * forme que le projet a déjà retenue pour l'accueil, la validation et les réglages.
 */
@Immutable
internal data class AnalyseActions(
    val onRemovePhoto: () -> Unit,
    val onText: (String) -> Unit,
    val onAnalyse: () -> Unit,
    val onCancel: () -> Unit,
    val onConsent: () -> Unit,
    val onConsentDeclined: () -> Unit,
    val onManual: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * La hauteur du cadre d'image.
 *
 * Fixe, et c'est ce qui rend la page stable : un cadre qui prendrait la place restante
 * sauterait de taille à chaque ligne tapée, et un cadre au rapport de la photo
 * changerait de hauteur selon qu'elle est prise en portrait ou en paysage.
 */
private val FrameHeight = 260.dp

/** Assez pour une phrase de repas, pas assez pour cacher le bouton sous le clavier. */
private const val TEXT_LINES = 3
