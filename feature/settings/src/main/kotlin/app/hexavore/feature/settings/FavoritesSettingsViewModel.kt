package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * La liste des favoris, telle que les réglages la montrent.
 *
 * **Lecture seule.** Corriger un favori se fait sur l'écran de saisie, qui sait déjà le
 * faire ([navigateToFavoriteEditor][editeur]) : ouvrir un second éditeur ici aurait fait
 * deux endroits où écrire la même chose, et c'est toujours le second qui oublie une
 * règle ([D149][decisions]).
 *
 * **L'ordre est celui du port** — les plus rejoués d'abord —, et non alphabétique : on
 * vient y chercher ce qu'on utilise, pas ce qui commence par A.
 *
 * [editeur]: feature/entry/src/main/kotlin/app/hexavore/feature/entry/EntryDestination.kt
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class FavoritesSettingsViewModel @Inject constructor(favorites: FavoriteDishes) : ViewModel() {
    val uiState: StateFlow<FavoritesUiState> =
        favorites.observeAll()
            .map<List<FavoriteDish>, FavoritesUiState> { FavoritesUiState.Loaded(it) }
            // Une lecture qui echoue laisse une liste vide et non un ecran casse : il
            // n'y a rien a reessayer sur un ecran qui ne fait que montrer.
            .catch { emit(FavoritesUiState.Loaded(emptyList())) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = FavoritesUiState.Loading,
            )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Ce que l'écran des favoris montre. */
internal sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState

    /**
     * La liste, **éventuellement vide**.
     *
     * Vide n'est pas une erreur : c'est l'état de qui n'a encore rien enregistré, et
     * l'écran le dit en expliquant comment on en fabrique un.
     */
    data class Loaded(val favorites: List<FavoriteDish>) : FavoritesUiState
}
