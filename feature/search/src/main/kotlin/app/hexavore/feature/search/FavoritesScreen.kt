package app.hexavore.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId

/** La liste des plats favoris, branchée sur le graphe d'injection. */
@Composable
internal fun FavoritesRoute(
    onPick: (FavoriteDishId) -> Unit,
    onEdit: (FavoriteDishId) -> Unit,
    onClose: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    FavoritesScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onPick = onPick,
        onEdit = onEdit,
        onDelete = viewModel::onDelete,
        onClose = onClose,
    )
}

/**
 * Choisir un plat déjà composé, pour le rejouer.
 *
 * **Un appui long y ouvre « Modifier » et « Supprimer »**, comme sur un plat de
 * l'accueil. C'est un revirement sur [D62][decisions], qui réservait ces gestes à
 * l'étoile de l'écran de validation pour n'avoir qu'un endroit à tenir : la liste est
 * pourtant le seul endroit où l'on regarde ses favoris **en tant que liste**, donc le
 * seul où l'on s'aperçoit qu'il y en a un de trop ou un à corriger — et y arriver par
 * l'étoile demandait de rejouer le favori, c'est-à-dire d'ouvrir un repas qu'on ne
 * voulait pas noter.
 *
 * Choisir ouvre l'écran de validation **prérempli et modifiable** : un favori est un
 * modèle, pas un raccourci d'écriture. Corriger la quantité avant d'enregistrer est le
 * cas courant, pas l'exception.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun FavoritesScreen(
    state: FavoritesUiState,
    onQueryChange: (String) -> Unit,
    onPick: (FavoriteDishId) -> Unit,
    onEdit: (FavoriteDishId) -> Unit,
    onDelete: (FavoriteDishId) -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Comme la recherche, sa voisine : sans cela, le titre passait sous
                // l'heure et « Retour » sous la barre de geste (D163).
                .safeDrawingPadding()
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.lg),
            )

            when (state) {
                FavoritesUiState.Loading -> Unit
                FavoritesUiState.Error -> Body(stringResource(R.string.favorites_error))
                is FavoritesUiState.Content -> {
                    DraftTextField(
                        initial = state.query,
                        onValueChange = onQueryChange,
                        label = stringResource(R.string.favorites_search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FavoriteList(state, onPick, onEdit, onDelete)
                }
            }

            NeonButton(
                text = stringResource(R.string.favorites_close),
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
            )
        }
    }
}

@Composable
private fun ColumnScope.FavoriteList(
    state: FavoritesUiState.Content,
    onPick: (FavoriteDishId) -> Unit,
    onEdit: (FavoriteDishId) -> Unit,
    onDelete: (FavoriteDishId) -> Unit,
) {
    if (state.favorites.isEmpty()) {
        // Une liste vide dit quoi faire, pas seulement qu'elle est vide -- et elle
        // distingue « rien ne correspond » de « vous n'en avez pas encore ».
        Body(stringResource(if (state.filteredOut) R.string.favorites_none_matching else R.string.favorites_empty))
        return
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(items = state.favorites, key = { it.id.value }) { favorite ->
            FavoriteRow(
                favorite = favorite,
                onPick = { onPick(favorite.id) },
                onEdit = { onEdit(favorite.id) },
                onDelete = { onDelete(favorite.id) },
            )
        }
    }
}

/**
 * Une ligne : le nom, puis ce qu'il contient.
 *
 * Les aliments sous le nom plutôt qu'un total de calories : le total serait celui du
 * jour de l'enregistrement, et un favori qui cite des fiches vivantes ne vaut pas
 * forcément la même chose aujourd'hui. Annoncer un chiffre qui peut avoir bougé est
 * pire que ne pas en annoncer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(favorite: FavoriteDish, onPick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val contents = favorite.components.joinToString(separator = ", ") { it.name }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = stringResource(R.string.favorites_pick),
                onLongClickLabel = stringResource(R.string.favorites_menu),
                onLongClick = { menuOpen = true },
                onClick = onPick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "${favorite.name}. $contents" }
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FavoriteMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onEdit = onEdit,
            onDelete = onDelete,
        )
        Text(
            text = favorite.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = contents,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Les deux gestes qu'un appui long ouvre.
 *
 * **« Modifier » ouvre l'écran de validation sur le favori lui-même** : même écran,
 * même liste de lignes, mais enregistrer y réécrit le modèle au lieu de noter un
 * repas. C'est ce qui évite un second éditeur pour la même chose.
 *
 * La suppression **ne demande pas confirmation**, à la différence d'un plat du
 * journal : un favori est un modèle, pas un fait. Le supprimer ne perd aucune donnée
 * — les repas déjà notés gardent leurs lignes — et le refaire coûte une étoile.
 */
@Composable
private fun FavoriteMenu(expanded: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.favorites_menu_edit)) },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.favorites_menu_delete)) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}
