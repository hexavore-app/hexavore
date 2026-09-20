package app.hexavore.core.designsystem.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.hexavore.domain.nutrition.Macro

// Ce que la sélection fait à la figure : ce qu'elle met en avant, ce qu'elle éteint,
// et les deux façons de la déclencher — le doigt et la voix. Séparé du tracé parce que
// ce sont deux sujets : l'un dit à quoi ressemble un hexagone, l'autre ce qui arrive
// quand on le touche.

/**
 * Ce que la sélection fait aux six quartiers : un qui avance, cinq qui reculent.
 *
 * **Deux signaux et non un**, parce qu'un seul ne suffit pas. Le grossissement seul ne
 * se voit pas sur un quartier presque vide — huit pour cent de très peu restent très
 * peu —, et l'extinction seule ne dit pas *lequel* on a touché quand deux macros sont
 * au même niveau. Ensemble, ils tiennent à tous les remplissages.
 *
 * Tout revient à un quand rien n'est sélectionné : la figure au repos est celle qu'on
 * connaît, et aucune macro n'y est mise en avant.
 */
@Composable
internal fun rememberEmphasis(selected: Macro?, spec: AnimationSpec<Float>): Emphasis = Emphasis(
    dim = Macro.entries.associateWith { macro ->
        val cible = if (selected == null || selected == macro) 1f else FADED_ALPHA
        animateFloatAsState(cible, spec, label = "extinction ${macro.name}").value
    },
    zoom = Macro.entries.associateWith { macro ->
        animateFloatAsState(if (selected == macro) SELECTED_ZOOM else 1f, spec, label = "zoom ${macro.name}").value
    },
)

/** L'état d'attention de chaque quartier : ce qu'il garde de couleur, ce qu'il prend de place. */
internal data class Emphasis(val dim: Map<Macro, Float>, val zoom: Map<Macro, Float>)

/**
 * Le quartier que le doigt désigne, ou le vide quand il tombe à côté.
 *
 * **`null` est une réponse**, et c'est elle qui referme : un appui dans la zone mais
 * hors de la figure — un coin, l'espace entre deux lettres — dit qu'on a fini de
 * regarder. La règle est dans [macroAt], ici il n'y a que le branchement.
 */
internal fun Modifier.quarterTaps(labelExtent: Float, onSelect: (Macro?) -> Unit): Modifier = composed {
    // Relu a chaque appui : le geste survit aux recompositions, pas la lambda.
    val choisir by rememberUpdatedState(onSelect)

    pointerInput(labelExtent) {
        detectTapGestures { tap ->
            val place = hexagonZone(size.width.toFloat(), size.height.toFloat(), labelExtent)
            choisir(
                macroAt(
                    tap = tap,
                    centre = Offset(size.width / 2f, size.height / 2f),
                    // Les lettres font partie de ce qu'on vise : viser un « F » est la
                    // facon la plus naturelle de designer les fibres.
                    reach = place.labelRadius + labelExtent,
                ),
            )
        }
    }
}

/**
 * Les six quartiers, dits à la voix.
 *
 * **Une figure ne se découpe pas en six vues.** Six zones tactiles invisibles posées
 * par-dessus un dessin se disputeraient le doigt avec lui, et leurs rectangles ne
 * suivraient pas des triangles. Des actions personnalisées disent la même chose sans
 * rien ajouter à l'écran : le lecteur fait ce que l'appui fait.
 *
 * Les six **valeurs** restent dans les barres, sous une forme qui se lit bien mieux à
 * la voix — c'est pourquoi la figure ne décrit que ce qu'elle est et ce qu'elle permet.
 */
internal fun Modifier.quarterActions(label: (Macro) -> String, onSelect: (Macro?) -> Unit): Modifier = semantics {
    customActions = Macro.entries.map { macro ->
        CustomAccessibilityAction(label(macro)) {
            onSelect(macro)
            true
        }
    }
}

/** Ce qu'un quartier éteint garde de sa couleur. Assez pour rester lisible, trop peu pour attirer l'œil. */
private const val FADED_ALPHA = 0.28f

/** Ce que le quartier touché prend de plus. Un rien : il avance, il ne saute pas. */
private const val SELECTED_ZOOM = 1.08f
