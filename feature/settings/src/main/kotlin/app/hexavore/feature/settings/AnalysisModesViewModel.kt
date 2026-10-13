package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Comment on analyse : en profondeur, quand le fournisseur sait le faire.
 *
 * **Sorti d'`AiSettingsViewModel` quand son seuil de fonctions a mordu**, et le
 * découpage suit ce que les choses sont : là-bas ce qu'on saisit pour un fournisseur —
 * une clé, un modèle, une URL —, ici une façon d'analyser qui n'appartient à aucun
 * fournisseur en particulier.
 *
 * **Il portait aussi le mode de mise au point**, et c'était son second sujet. Il est
 * parti : signaler un plat proposé par un modèle joint déjà l'échange, donc un
 * interrupteur et un écran pour relire ces échanges à la main ne servaient plus
 * personne — et c'était, dans la version publiée, un réglage de plus devant quelqu'un
 * qui n'en cherchait aucun ([D156][decisions]).
 *
 * Ce qui reste n'est d'ailleurs **pas un réglage** : l'écran ne fait que dire laquelle
 * des deux voies sera prise ([D142][decisions]). Le modèle survit à la disparition de
 * l'interrupteur parce que cette lecture-là reste à faire.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class AnalysisModesViewModel @Inject constructor(credentials: AiCredentials) : ViewModel() {
    val uiState: StateFlow<ModesUiState> = credentials
        .observe()
        .map { ModesUiState(toolingAvailable = it.active?.tooling == true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModesUiState())
}

/**
 * Ce que la section d'analyse approfondie montre.
 *
 * [toolingAvailable] dit si le fournisseur actif sait appeler des outils. Ce n'est plus
 * un réglage depuis [D142][decisions] : l'analyse approfondie se fait dès qu'elle est
 * possible, et l'écran ne fait que dire laquelle des deux voies sera prise.
 *
 * [decisions]: docs/11-decisions.md
 */
internal data class ModesUiState(val toolingAvailable: Boolean = false)
