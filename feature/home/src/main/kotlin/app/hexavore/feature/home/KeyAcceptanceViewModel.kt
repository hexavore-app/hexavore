package app.hexavore.feature.home

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
 * Ce que l'accueil a à dire quand on revient de l'écran des clés.
 *
 * **C'est le retour le plus fréquent**, et c'est celui qui manquait : le tour guidé et le
 * bouton d'analyse grisé mènent tous deux à l'écran des clés **depuis l'accueil**, et la
 * nouvelle ne savait se dire que sur le hub des réglages. On validait, on revenait, et
 * rien ne disait que c'était fait ([D157][decisions]).
 *
 * **Un modèle à part** plutôt qu'un flux de plus sur `HomeViewModel` : celui-ci lit une
 * journée, celui-là porte une nouvelle qui ne la concerne pas. Le hub a le sien,
 * identique — les deux modules d'écran ne se voient pas, et c'est voulu
 * ([D149][decisions]).
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
