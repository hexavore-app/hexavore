package app.hexavore.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.hexavore.domain.diary.DaySummary
import app.hexavore.domain.diary.MacroSources
import app.hexavore.domain.diary.sourcesOf
import app.hexavore.domain.nutrition.Macro

/**
 * Le quartier qu'on regarde de près — celui dont la bulle dit les sources.
 *
 * Un seul état pour deux chemins : le quartier de la figure et la barre qui le répète
 * en dessous ouvrent la même bulle, parce que **toucher un triangle est un geste que
 * rien n'annonce**. La barre est la porte visible, le triangle le raccourci de qui le
 * connaît — la règle que le projet applique déjà au retour à aujourd'hui.
 */
@Stable
internal class MacroFocus {
    var macro: Macro? by mutableStateOf(null)
        private set

    /** Ce qu'un appui sur un quartier ou une barre change. La règle est dans [focusAfterTap]. */
    fun tapped(summary: DaySummary, on: Macro?) {
        macro = summary.focusAfterTap(macro, on)
    }

    /** Ce qu'un appui ailleurs fait, et ce que fait un changement de jour. */
    fun clear() {
        macro = null
    }
}

@Composable
internal fun rememberMacroFocus(): MacroFocus = remember { MacroFocus() }

/**
 * Le quartier montré après un appui, en partant de celui qui l'était.
 *
 * Quatre cas, et chacun pour sa raison :
 *
 * - **l'appui tombe hors de la figure** — c'est un appui à côté, il referme ;
 * - **le quartier n'a rien à montrer** — il ne répond pas, et ne referme donc pas non
 *   plus ce qui était ouvert. Un quartier vide est un quartier qui n'est pas là ;
 * - **c'est celui qu'on regardait** — le même geste referme ce qu'il a ouvert, ce qui
 *   donne un second chemin de sortie à qui n'a pas pensé à toucher à côté ;
 * - **c'est un autre** — la bulle reste et change de contenu. Aucun aller-retour par le
 *   vide : c'est ce qui permet de parcourir les six sans jamais rien refermer.
 *
 * **« Rien à montrer » n'est pas « le quartier est à zéro ».** Une macro qu'aucun
 * aliment n'a apportée n'a rien à dire ; une macro dont un aliment ne renseigne pas la
 * valeur en a beaucoup, même si le quartier est dessiné vide dans les deux cas
 * ([MacroSources]).
 */
internal fun DaySummary.focusAfterTap(current: Macro?, tapped: Macro?): Macro? = when {
    tapped == null -> null
    sourcesOf(tapped, MacroSources.DETAILED).isEmpty -> current
    tapped == current -> null
    else -> tapped
}
