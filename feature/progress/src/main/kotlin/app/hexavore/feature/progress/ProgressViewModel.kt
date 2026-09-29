package app.hexavore.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.progress.Progress
import app.hexavore.domain.usecase.ObserveProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Où en est la progression, pour l'écran qui la déplie.
 *
 * **Il n'écrit rien.** Faire avancer la progression — figer un palier, relever un
 * plancher — appartient à l'accueil, qui est l'écran qu'on ouvre. Le faire ici aussi
 * donnerait deux endroits qui écrivent la même chose au même moment, pour un écran
 * qu'on visite une fois de temps en temps.
 *
 * `Progress.NONE` en valeur initiale plutôt qu'un état de chargement : la progression
 * d'une installation neuve *est* zéro partout, et un voile de chargement sur un écran
 * qui s'ouvre en quelques millisecondes se verrait comme un clignotement.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(observeProgress: ObserveProgress) : ViewModel() {
    val uiState: StateFlow<Progress> = observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), Progress.NONE)

    private companion object {
        /** Le delai standard du projet : une rotation ne relance pas la lecture. */
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
