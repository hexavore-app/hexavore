package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.appearance.DishDisplayStyle
import app.hexavore.domain.appearance.ThemeMode
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.usecase.ChooseUnitSystem
import app.hexavore.domain.usecase.ObserveDishStyle
import app.hexavore.domain.usecase.ObserveUnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce que l'écran Apparence montre : le thème retenu, et le système d'unités. */
internal data class AppearanceUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val units: UnitSystem = UnitSystem.METRIC,
    val dishStyle: DishDisplayStyle = DishDisplayStyle.SIMPLE,
)

/**
 * Les trois réglages de l'apparence, qui ne vivent pourtant pas au même endroit.
 *
 * **Le thème et le style d'affichage sont des préférences d'appareil, les unités une
 * propriété du profil.** L'écran
 * les montre côte à côte parce que c'est là qu'on les cherche ; le modèle, lui, va les
 * chercher chacun chez soi. Les réunir dans un magasin commun aurait fait voyager le
 * thème dans la sauvegarde, ou empêché les unités d'y voyager.
 *
 * **Un magasin illisible retombe sur les défauts plutôt que sur un écran vide.** C'est la
 * même règle que partout : l'apparence est un confort, et rien n'y justifie de refuser
 * l'écran à quelqu'un dont un fichier a été abîmé.
 */
@HiltViewModel
internal class AppearanceViewModel @Inject constructor(
    private val settings: AppearanceSettings,
    private val chooseUnits: ChooseUnitSystem,
    observeUnits: ObserveUnitSystem,
    observeDishStyle: ObserveDishStyle,
) : ViewModel() {
    val uiState: StateFlow<AppearanceUiState> =
        combine(settings.observeTheme(), observeUnits(), observeDishStyle()) { theme, units, style ->
            AppearanceUiState(theme = theme, units = units, dishStyle = style)
        }
            .catch { emit(AppearanceUiState()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceUiState())

    fun onTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /**
     * **Sans profil, rien ne bouge**, et le bouton reste où il était.
     *
     * Le cas ne se présente pas dans l'application — l'onboarding précède le hub — mais
     * le réglage vit sur le profil, et inventer un profil vide pour y ranger une
     * préférence y écrirait un poids et une taille que personne n'a donnés.
     */
    fun onUnits(system: UnitSystem) {
        viewModelScope.launch { chooseUnits(system) }
    }

    fun onDishStyle(style: DishDisplayStyle) {
        viewModelScope.launch { settings.setDishStyle(style) }
    }
}
