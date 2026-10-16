package app.hexavore.core.designsystem.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Les barres du système, réglées sur le thème **de l'application** ([D162][decisions]).
 *
 * `enableEdgeToEdge()` sans argument choisit la couleur des icônes d'après le mode du
 * **système**. Tant que le thème le suivait, c'était la même chose. Depuis qu'il se
 * force ([D113][decisions]), un thème sombre sur un téléphone clair laissait l'heure et
 * la batterie en noir sur fond noir — et l'inverse en clair.
 *
 * **Ici et non dans `:app`** : les deux voiles de la barre de navigation sont des
 * couleurs, et les couleurs ne s'écrivent que dans le design system. Ce sont celles
 * qu'Android pose par défaut ; seul change ce qui décide entre les deux.
 *
 * [decisions]: docs/11-decisions.md
 */
fun ComponentActivity.edgeToEdgeFollowing(darkTheme: Boolean) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
        navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
    )
}

/** Le voile clair que pose Android derrière une barre de navigation à trois boutons. */
private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)

/** Et le sombre. */
private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
