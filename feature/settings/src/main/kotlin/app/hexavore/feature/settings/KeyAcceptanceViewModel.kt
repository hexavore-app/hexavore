package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AcceptedKey
import app.hexavore.domain.ai.KeyAcceptance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Ce que le hub a à dire quand on revient de l'écran des clés.
 *
 * **Un modèle à part** plutôt qu'un flux de plus sur celui des pastilles, que le hub
 * emprunte déjà : les pastilles décrivent un état qui dure, celui-ci porte une nouvelle
 * qu'on dit une fois. Les fondre aurait donné au même objet deux rythmes ([D157][decisions]).
 *
 * L'accueil a le sien, identique : les deux modules d'écran ne se voient pas, et c'est
 * voulu ([D149][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class KeyAcceptanceViewModel @Inject constructor(private val acceptance: KeyAcceptance) : ViewModel() {
    val accepted: StateFlow<AcceptedKey?> = acceptance
        .observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), null)

    /** La barre est passée, ou l'écran est parti : la nouvelle ne doit pas reparaître. */
    fun onShown() = acceptance.consume()

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
