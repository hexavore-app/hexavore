package app.hexavore.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId

/**
 * Les plats enregistrés, et le chemin pour les corriger.
 *
 * ### Pourquoi ils ont leur entrée dans les réglages
 *
 * Un favori se crée depuis l'étoile d'un plat, et se rejoue depuis le « + ». Le
 * corriger, lui, n'avait aucun chemin : il fallait le rejouer dans une journée, le
 * modifier, puis effacer le plat qu'on venait d'écrire — trois gestes, dont deux qui
 * salissent le journal ([D149][decisions]).
 *
 * ### Toucher ouvre le plat lui-même
 *
 * Pas un éditeur à part : **l'écran de saisie**, sur le favori. Il sait déjà tout faire —
 * ajouter une ligne, changer une quantité, vérifier qu'un nom n'est pas pris —, et un
 * second éditeur aurait fait deux endroits où écrire la même chose. Ce qui change est
 * seulement ce qu'enregistrer veut dire.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun FavoritesSettingsRoute(
    onClose: () -> Unit,
    onEdit: (FavoriteDishId) -> Unit,
    viewModel: FavoritesSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    FavoritesSettingsScreen(state = state, onClose = onClose, onEdit = onEdit)
}

@Composable
internal fun FavoritesSettingsScreen(state: FavoritesUiState, onClose: () -> Unit, onEdit: (FavoriteDishId) -> Unit) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_favorites_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.settings_close),
            )
        },
    ) { padding ->
        val favoris = (state as? FavoritesUiState.Loaded)?.favorites

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            // Rien pendant la lecture : une liste vide affichee une demi-seconde
            // annoncerait « vous n'avez aucun favori » a quelqu'un qui en a douze.
            if (favoris == null) return@LazyColumn

            if (favoris.isEmpty()) {
                item { EmptyFavorites() }
            } else {
                items(favoris, key = { it.id.value }) { favori ->
                    FavoriteCard(favori = favori, onClick = { onEdit(favori.id) })
                }
            }
        }
    }
}

/**
 * Ce qu'on lit d'un favori sans l'ouvrir : son nom, et ce qu'il y a dedans.
 *
 * Les lignes sont énumérées plutôt que comptées — « Flocons, Lait, Banane » dit
 * lequel c'est, là où « 3 aliments » demande de l'ouvrir pour savoir.
 */
@Composable
private fun FavoriteCard(favori: FavoriteDish, onClick: () -> Unit) {
    val ouvrir = stringResource(R.string.settings_favorites_edit)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = ouvrir, onClick = onClick)
                .padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = favori.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = favori.components.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Aucun favori : on dit **comment on en fabrique un**, pas qu'il n'y en a pas. */
@Composable
private fun EmptyFavorites() {
    Text(
        text = stringResource(R.string.settings_favorites_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
