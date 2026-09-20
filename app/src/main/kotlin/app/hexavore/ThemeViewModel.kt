package app.hexavore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.appearance.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Le thème, pour la racine de l'application.
 *
 * **Distinct de celui de l'écran Apparence**, qui règle. Celui-ci ne fait que lire, et il
 * vit aussi longtemps que l'activité : c'est ce qui permet à un changement de thème de se
 * voir immédiatement, sans quitter l'écran qui vient de le demander.
 *
 * **`SYSTEM` comme valeur de départ**, et non le thème du disque : le magasin porte déjà
 * la valeur lue au démarrage et l'émet aussitôt, si bien que la première composition la
 * reçoit. Mettre autre chose ici ferait clignoter l'application entre deux thèmes.
 */
@HiltViewModel
internal class ThemeViewModel @Inject constructor(settings: AppearanceSettings) : ViewModel() {
    val mode: StateFlow<ThemeMode> = settings
        .observeTheme()
        .catch { emit(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
