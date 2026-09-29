package app.hexavore.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.Progress

/**
 * La barre du bas, en un seul objet : ce qu'elle montre et ce qu'elle déclenche.
 *
 * Regroupés plutôt que passés un par un, comme [HomeActions] : quatre paramètres de
 * plus sur une signature qui en compte déjà quinze ne se lisent plus, et ceux-ci vont
 * ensemble — un état d'envoi sans son annulation ne veut rien dire.
 */
@Immutable
data class QuickEntry(
    val state: QuickEntryUiState = QuickEntryUiState(),
    val onSend: (String) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/**
 * La progression, telle que l'accueil en a besoin : le bandeau et la célébration.
 *
 * Les deux vont ensemble parce qu'ils viennent du même modèle et se contredisent
 * autrement : un bandeau qui annoncerait un palier que la célébration n'a pas encore
 * fêté ferait lire deux fois la même nouvelle, dans le désordre.
 */
@Immutable
data class ProgressPanel(
    val progress: Progress = Progress.NONE,
    /**
     * Le palier qui vient de tomber, ou `null` — ce qui est le cas normal.
     *
     * Un seul à la fois : la file vit dans le modèle, et chacun a droit à son animation
     * ([D133][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val celebrating: Badge? = null,
    val onCelebrated: () -> Unit = {},
    val onOpen: () -> Unit = {},
)

/**
 * La barre du bas, branchée sur son modèle.
 *
 * **Sortie de [HomeRoute] quand le seuil de longueur a mordu**, et le découpage suit ce
 * que les choses sont : l'accueil assemble un écran, cette fonction câble un dispositif
 * — un champ, un envoi, une sortie vers la validation.
 */
@Composable
internal fun quickEntryPanel(onProposal: () -> Unit): QuickEntry {
    val viewModel: QuickEntryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProposalHandoff(proposed = state.proposed, onNavigated = viewModel::onNavigated, onProposal = onProposal)

    return QuickEntry(
        state = state,
        onSend = viewModel::onSend,
        onCancel = viewModel::onCancel,
        onDismissError = viewModel::onDismissError,
    )
}

/**
 * La progression, branchée sur son modèle — **et c'est ici qu'elle avance**.
 *
 * L'accueil est l'écran qu'on ouvre : figer un palier depuis celui qu'on visite une
 * fois par semaine le figerait une fois par semaine, et la célébration arriverait sept
 * jours après le geste qui l'a méritée ([D133][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun progressPanel(onOpen: () -> Unit): ProgressPanel {
    val viewModel: HomeProgressViewModel = hiltViewModel()
    val progress by viewModel.uiState.collectAsStateWithLifecycle()
    val celebrating by viewModel.celebrating.collectAsStateWithLifecycle()

    return ProgressPanel(
        progress = progress,
        celebrating = celebrating.firstOrNull(),
        onCelebrated = viewModel::onCelebrated,
        onOpen = onOpen,
    )
}

/**
 * L'accueil cède la place à la validation, une fois.
 *
 * Exactement ce que l'écran d'IA fait pour la sienne : la proposition attend dans le
 * dépôt, et ce drapeau dit seulement qu'il y a quelque chose à aller y chercher.
 * `onNavigated` le referme, sans quoi revenir de la validation repartirait aussitôt
 * vers un dépôt qu'elle vient de vider.
 */
@Composable
private fun ProposalHandoff(proposed: Boolean, onNavigated: () -> Unit, onProposal: () -> Unit) {
    LaunchedEffect(proposed) {
        if (!proposed) return@LaunchedEffect
        onNavigated()
        onProposal()
    }
}
