package app.hexavore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Le cadre de tous les écrans : leur fond, et **leurs côtés tenus à l'écart** de
 * l'encoche et d'une barre de navigation posée sur le côté ([D171][decisions]).
 *
 * ### Le défaut vu en paysage
 *
 * Couché, un téléphone à trois boutons pose sa barre de navigation sur le côté, et
 * l'encoche passe sur un côté elle aussi. Chaque écran gérait ses encarts à sa façon :
 * `Scaffold` retire du corps les barres mais pas l'encoche, `ScreenTopBar` ne retire
 * que la barre d'état, et la barre d'ajout de l'accueil ne retirait rien sur les côtés.
 * Le « + » passait sous la barre de navigation, et le contenu sous l'encoche.
 *
 * ### Une fois, à la racine
 *
 * Les côtés sont les mêmes pour tous les écrans : les retirer ici, une fois, les retire
 * partout, et un écran ajouté demain en hérite sans y penser. La marge **consomme** ce
 * qu'elle retire : `Scaffold`, `safeDrawingPadding` et les autres marges d'encarts, plus
 * bas, ne le comptent pas une seconde fois. Le haut et le bas restent l'affaire de chaque
 * écran — la barre d'état, la barre d'ajout et le clavier y ont chacun leurs règles.
 *
 * Tenu debout, un téléphone n'a ni barre ni encoche sur les côtés : la marge vaut zéro, et
 * rien ne bouge. Le fond est peint sous la marge, pour que les bandes laissées sous la
 * barre de navigation suivent le thème de l'application et non celui de la fenêtre.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun ScreenFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        content()
    }
}
