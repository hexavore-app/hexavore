package app.hexavore.core.designsystem.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.core.view.WindowCompat

/**
 * Le contenu passe sous les barres du système, et leurs icônes suivent le thème **de
 * l'application** ([D162][decisions], [D170][decisions]).
 *
 * **Les icônes suivent l'application, pas le système.** Depuis que le thème se force
 * ([D113][decisions]), un thème sombre sur un téléphone clair laissait l'heure et la
 * batterie en noir sur fond noir — et l'inverse en clair.
 *
 * **Les couleurs des barres ne sont plus réglées ici**, mais dans `Theme.Hexavore`.
 * `enableEdgeToEdge()` les posait par `Window.setStatusBarColor` et
 * `setNavigationBarColor`, et ouvrait l'encoche en `SHORT_EDGES` : trois appels
 * qu'Android 15 rend obsolètes, et que le Play Store relève dans le code de toute
 * application qui le vise. Ils ne servaient qu'avant Android 15 ; le thème de fenêtre
 * dit la même chose sans un appel. Ce qui reste ici n'est pas obsolète.
 *
 * [decisions]: docs/11-decisions.md
 */
fun Activity.edgeToEdgeFollowing(darkTheme: Boolean) {
    // Android 15 l'impose a qui vise l'API 35 ; avant lui, c'est cet appel qui le fait.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
    WindowCompat.getInsetsController(window, window.decorView).run {
        isAppearanceLightStatusBars = !darkTheme
        // Sous l'API 29, la barre de navigation garde son voile sombre : ses icones
        // restent claires, quel que soit le theme (values/system_bars.xml).
        isAppearanceLightNavigationBars = !darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}

/**
 * Le mode du système : le seul connu au lancement, avant que le réglage de
 * l'application ne soit lu. C'est lui que suit le thème de fenêtre.
 */
fun Activity.systemIsDark(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
