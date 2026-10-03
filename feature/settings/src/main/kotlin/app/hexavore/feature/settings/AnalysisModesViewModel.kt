package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.DebugSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Comment on analyse : en profondeur, et en gardant trace.
 *
 * **Sorti d'`AiSettingsViewModel` quand son seuil de fonctions a mordu**, et le
 * découpage suit ce que les choses sont : là-bas ce qu'on saisit pour un fournisseur —
 * une clé, un modèle, une URL —, ici deux façons d'analyser qui n'appartiennent à aucun
 * fournisseur en particulier et qui ne se rangent pas au même endroit.
 *
 * Les deux modèles cohabitent sur le même écran, chacun sur sa section. C'est le cas
 * normal d'un écran qui règle deux sujets voisins.
 */
@HiltViewModel
internal class AnalysisModesViewModel @Inject constructor(
    private val debug: DebugSettings,
    credentials: AiCredentials,
) : ViewModel() {
    val uiState: StateFlow<ModesUiState> = combine(
        debug.observe(),
        credentials.observe(),
    ) { tracing, setup ->
        ModesUiState(
            debug = tracing,
            toolingAvailable = setup.active?.tooling == true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ModesUiState())

    fun onDebug(enabled: Boolean) {
        viewModelScope.launch { debug.setEnabled(enabled) }
    }
}

/**
 * Ce que les deux sections montrent.
 *
 * [toolingAvailable] dit si le fournisseur actif sait appeler des outils. Ce n'est plus
 * un réglage depuis [D142][decisions] : l'analyse approfondie se fait dès qu'elle est
 * possible, et l'écran ne fait que dire laquelle des deux voies sera prise.
 *
 * [decisions]: docs/11-decisions.md
 */
internal data class ModesUiState(val debug: Boolean = false, val toolingAvailable: Boolean = false)
