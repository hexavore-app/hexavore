package app.hexavore.core.testing

import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.appearance.DishDisplayStyle
import app.hexavore.domain.appearance.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Ce qui a été réglé sur l'apparence, en mémoire.
 *
 * Il part sur les défauts d'une installation neuve — [ThemeMode.SYSTEM] et
 * [DishDisplayStyle.SIMPLE] : un faux qui démarrerait sur un réglage explicite
 * laisserait passer un défaut mal câblé.
 */
class InMemoryAppearanceSettings(
    initial: ThemeMode = ThemeMode.SYSTEM,
    initialStyle: DishDisplayStyle = DishDisplayStyle.SIMPLE,
    /** Un fichier de préférences abîmé : la lecture jette, et l'écran doit tenir. */
    var failure: Boolean = false,
) : AppearanceSettings {
    private val state = MutableStateFlow(initial)
    private val style = MutableStateFlow(initialStyle)

    /** Ce que le magasin porte, pour qu'un cas l'affirme sans passer par un flux. */
    val current: ThemeMode get() = state.value

    val currentStyle: DishDisplayStyle get() = style.value

    override fun observeTheme(): Flow<ThemeMode> = state.map {
        if (failure) error("Apparence illisible") else it
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = mode
    }

    override fun observeDishStyle(): Flow<DishDisplayStyle> = style.map {
        if (failure) error("Apparence illisible") else it
    }

    override suspend fun setDishStyle(style: DishDisplayStyle) {
        this.style.value = style
    }
}
