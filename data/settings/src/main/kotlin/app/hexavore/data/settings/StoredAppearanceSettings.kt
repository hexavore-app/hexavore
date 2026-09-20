package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.appearance.DishDisplayStyle
import app.hexavore.domain.appearance.ThemeMode
import app.hexavore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Ce qui a été réglé sur l'apparence, dans son propre fichier de préférences.
 *
 * **Lu au démarrage, sans suspendre.** `MutableStateFlow` porte déjà la valeur du disque
 * quand la première composition arrive : un thème lu de façon asynchrone ferait
 * apparaître l'application en clair pendant un instant avant de basculer, et ce
 * clignotement se voit.
 *
 * **Un nom inconnu retombe sur le défaut** plutôt que de faire tomber le démarrage. Le
 * fichier n'est pas contrôlé par l'application seule, et une valeur qu'on ne sait pas
 * lire se lit comme « on n'a rien choisi ».
 */
internal class StoredAppearanceSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : AppearanceSettings {
    private val theme = MutableStateFlow(preferences.readThemeMode())
    private val dishStyle = MutableStateFlow(preferences.readDishStyle())

    override fun observeTheme(): Flow<ThemeMode> = theme

    override suspend fun setThemeMode(mode: ThemeMode) = withContext(dispatchers.io) {
        preferences.edit { putString(THEME_MODE, mode.name) }
        theme.value = mode
    }

    override fun observeDishStyle(): Flow<DishDisplayStyle> = dishStyle

    override suspend fun setDishStyle(style: DishDisplayStyle) = withContext(dispatchers.io) {
        preferences.edit { putString(DISH_STYLE, style.name) }
        dishStyle.value = style
    }
}

private fun SharedPreferences.readThemeMode(): ThemeMode {
    val stored = getString(THEME_MODE, null)
    return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
}

private fun SharedPreferences.readDishStyle(): DishDisplayStyle {
    val stored = getString(DISH_STYLE, null)
    return DishDisplayStyle.entries.firstOrNull { it.name == stored } ?: DishDisplayStyle.SIMPLE
}

private const val THEME_MODE = "appearance.theme_mode"

private const val DISH_STYLE = "appearance.dish_style"
