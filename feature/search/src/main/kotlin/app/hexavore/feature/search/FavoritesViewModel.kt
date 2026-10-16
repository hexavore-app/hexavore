package app.hexavore.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.food.SearchText
import app.hexavore.domain.usecase.ObserveFavoriteDishes
import app.hexavore.domain.usecase.RemoveFavoriteDish
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L'état de la liste des plats favoris.
 *
 * Un état unique plutôt que trois champs indépendants : les combinaisons impossibles
 * — chargement *et* contenu — cessent de compiler au lieu d'être évitées à la main.
 */
internal sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState

    data class Content(val query: String, val favorites: List<FavoriteDish>) : FavoritesUiState {
        /** Vrai quand la liste est vide **parce qu'on a filtré**, et non parce qu'il n'y a rien. */
        val filteredOut: Boolean get() = favorites.isEmpty() && query.isNotBlank()
    }

    /** La lecture a échoué. Rien d'autre à dire : le geste est le même, réessayer. */
    data object Error : FavoritesUiState
}

/**
 * Choisir un plat favori pour le rejouer.
 *
 * **Le filtrage se fait ici et non dans le port.** La liste tient sur un écran ;
 * demander une requête à la base pour trier vingt lignes aurait exigé un index et une
 * normalisation de plus, pour un gain qui n'existe pas. La comparaison utilise la même
 * normalisation que la recherche d'aliments — « creme » trouve « Crème » ([D49][decisions]).
 *
 * **Les composants portent le nom de leur fiche d'aujourd'hui**, celui que l'éditeur
 * montre ([D167][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class FavoritesViewModel @Inject constructor(
    observeFavoriteDishes: ObserveFavoriteDishes,
    private val removeFavoriteDish: RemoveFavoriteDish,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<FavoritesUiState> =
        combine(observeFavoriteDishes(), query) { all, typed ->
            FavoritesUiState.Content(query = typed, favorites = all.matching(typed))
        }
            .map<FavoritesUiState.Content, FavoritesUiState> { it }
            .catch { emit(FavoritesUiState.Error) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = FavoritesUiState.Loading,
            )

    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * Retire un favori de la liste.
     *
     * **Un second chemin vers la même décision**, et c'est un revirement assumé :
     * [D62][decisions] réservait la suppression à l'étoile de l'écran de validation,
     * pour n'avoir qu'un endroit à tenir. La liste est pourtant le seul endroit où
     * l'on regarde ses favoris en tant que liste, donc le seul où l'on s'aperçoit
     * qu'il y en a un de trop — et l'atteindre par l'étoile demandait de le rejouer
     * d'abord, c'est-à-dire d'ouvrir un repas qu'on ne voulait pas noter.
     *
     * La liste ne se recharge pas : elle observe, et le favori disparaît de lui-même.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onDelete(id: FavoriteDishId) {
        viewModelScope.launch { runCatching { removeFavoriteDish(id) } }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Les favoris dont le nom contient la saisie.
 *
 * Sur le nom seul, pas sur les aliments qu'il contient : on cherche « petit-déj »,
 * pas « le favori qui contient du lait ». Chercher dans les composants ferait remonter
 * la moitié de la liste au premier mot courant.
 */
private fun List<FavoriteDish>.matching(query: String): List<FavoriteDish> {
    val normalised = SearchText.normalise(query)
    return if (normalised.isBlank()) this else filter { SearchText.normalise(it.name).contains(normalised) }
}
