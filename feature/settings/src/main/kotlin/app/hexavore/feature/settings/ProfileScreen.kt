package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.heightProblem
import app.hexavore.core.designsystem.component.targetProblem
import app.hexavore.core.designsystem.component.weightProblem
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.profile.BodyBounds
import kotlinx.coroutines.launch

/** Les réglages profil, branchés sur le graphe d'injection. */
@Composable
internal fun ProfileRoute(onClose: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        state = state,
        actions = remember(viewModel, onClose) {
            ProfileActions(
                onEdit = viewModel::onEdit,
                onForm = viewModel::onForm,
                onManual = viewModel::onManual,
                onMacroChange = viewModel::onMacroChange,
                // L'ecran se referme sur l'accueil une fois l'ecriture aboutie, et
                // seulement alors : un retour immediat laisserait croire qu'un echec
                // d'ecriture a ete pris en compte.
                onSave = { viewModel.onSave(onClose) },
                onConfirm = { viewModel.onConfirm(onClose) },
                onDismissConfirmation = viewModel::onDismissConfirmation,
                onClose = onClose,
            )
        },
    )
}

/**
 * **On consulte par défaut ; le crayon ouvre la modification** ([D60][decisions]).
 *
 * Un écran de réglages en édition permanente invite à corriger ce qu'on venait relire,
 * et il n'offre aucun moment où l'on puisse simplement vérifier un chiffre. Le crayon
 * est la seule porte, switch de mode compris — deux portes vers le même état auraient
 * fait de la bascule calculé/manuel une modification qui ne se déclare pas.
 *
 * Le refus se voit : appuyer sur « Enregistrer » alors qu'il manque quelque chose
 * affiche une barre qui dit **quoi** ([D28][decisions]), par le canal que
 * [D56][decisions] a retenu — ce qui interrompt le regard est ce qui se lit. Il referme
 * aussi le clavier : le champ qu'on quitte se colore et dit sa plage ([D160][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ProfileScreen(state: ProfileUiState, actions: ProfileActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val blocked = state.blocker?.let { blockerMessage(it, state) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Header(state, actions.onEdit)
                Sections(state, actions)
            }

            if (state.loaded && !state.unreadable) {
                ActionArea(state, actions) {
                    focus.clearFocus()
                    scope.launch {
                        // Une seule barre a la fois : trois appuis empilaient trois
                        // fois le meme message, le troisieme arrivant dix secondes
                        // plus tard.
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(blocked.orEmpty())
                    }
                }
            }
        }
    }

    state.pending?.let { pending ->
        ConfirmationDialog(
            before = state.saved,
            after = pending,
            onConfirm = actions.onConfirm,
            onDismiss = actions.onDismissConfirmation,
        )
    }
}

/** Le titre, et le crayon — qui disparaît une fois l'édition ouverte. */
@Composable
private fun Header(state: ProfileUiState, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.loaded && !state.unreadable && !state.editing) {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.profile_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Sections(state: ProfileUiState, actions: ProfileActions) {
    when {
        state.unreadable -> Unreadable()
        // Rien tant qu'on ne sait pas : un formulaire vide affiche une seconde
        // laisserait croire qu'aucun profil n'existe.
        !state.loaded -> Unit
        state.editing -> ProfileEditor(state, actions)
        else -> ProfileSummary(state)
    }
}

/**
 * L'écran qui dit « je n'ai pas pu lire », plutôt que d'afficher un profil vide.
 *
 * Un formulaire vide serait ici plus qu'un mensonge d'affichage : enregistré, il
 * écraserait le profil qui est en base par des champs que personne n'a saisis.
 */
@Composable
private fun Unreadable() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.profile_unreadable_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Body(stringResource(R.string.profile_unreadable_body))
    }
}

/** En consultation, une seule sortie. En édition, enregistrer ou renoncer. */
@Composable
private fun ActionArea(state: ProfileUiState, actions: ProfileActions, onBlocked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.editing) {
            NeonButton(
                text = stringResource(if (state.saving) R.string.profile_saving else R.string.profile_save),
                // Le bouton indisponible reagit et **repond** : c'est lui qui declenche
                // l'explication, pas un texte permanent (D28, D56).
                onClick = { if (state.canSave) actions.onSave() else onBlocked() },
                modifier = Modifier.fillMaxWidth(),
                style = NeonButtonStyle.FILLED,
                availability = when {
                    state.saving -> NeonButtonAvailability.DISABLED
                    state.canSave -> NeonButtonAvailability.AVAILABLE
                    else -> NeonButtonAvailability.UNAVAILABLE
                },
            )
        }
        NeonButton(
            // Renoncer ferme l'ecran plutot que de revenir a la consultation : les
            // corrections vivent dans le ViewModel, et un retour qui les garderait a
            // l'ecran sans les avoir ecrites laisserait un profil affiche qui n'est
            // pas celui qui est enregistre.
            text = stringResource(if (state.editing) R.string.profile_cancel else R.string.profile_close),
            onClick = actions.onClose,
            modifier = Modifier.fillMaxWidth(),
            style = NeonButtonStyle.OUTLINED,
        )
    }
}

/**
 * La phrase de la barre, pour ce qui bloque.
 *
 * **Une valeur invraisemblable reprend la phrase de son champ** ([D160][decisions]) : la
 * barre et le rouge sous le champ disent la même plage, dans les mêmes unités, parce
 * qu'ils la tiennent de la même fonction.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun blockerMessage(blocker: ProfileBlocker, state: ProfileUiState): String {
    val form = state.form
    return when (blocker) {
        ProfileBlocker.IDENTITY -> stringResource(R.string.profile_blocked_identity)
        ProfileBlocker.AGE -> stringResource(R.string.profile_blocked_age, BodyBounds.AGES.first, BodyBounds.AGES.last)
        ProfileBlocker.HEIGHT -> heightProblem(form.heightCm, state.units)?.text.orEmpty()
        ProfileBlocker.WEIGHT -> weightProblem(form.currentWeightKg, form.heightCm, state.units)?.text.orEmpty()
        ProfileBlocker.ACTIVITY -> stringResource(R.string.profile_blocked_activity)
        ProfileBlocker.STRATEGY -> stringResource(R.string.profile_blocked_strategy)
        ProfileBlocker.TARGET -> targetProblem(form.targetProblem, state.units)?.text.orEmpty()
        ProfileBlocker.HORIZON -> stringResource(R.string.profile_blocked_horizon)
        ProfileBlocker.EMPTY_MACRO -> stringResource(R.string.profile_blocked_macro)
    }
}
