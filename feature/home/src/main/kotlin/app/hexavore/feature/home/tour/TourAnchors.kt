package app.hexavore.feature.home.tour

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Ce que le tour désigne, et où ça se trouve à l'écran.
 *
 * ### Pourquoi mesurer plutôt que décrire
 *
 * Le tour parle de l'application **réelle** et non d'une maquette ([D141][decisions]) :
 * la bulle doit donc tomber à côté du bouton dont elle parle, et le trou de lumière
 * dessus. Or ces positions dépendent de la taille de l'écran, de la langue, du clavier
 * et de la barre système — rien de tout cela ne se devine au moment d'écrire le texte.
 *
 * Chaque élément concerné se mesure donc lui-même, par [tourAnchor], et dépose son
 * rectangle ici. Le tour lit ce qu'il trouve : un élément qui n'est pas à l'écran n'a
 * pas d'ancre, et l'étape qui le vise se contente alors de parler au centre.
 *
 * ### Un état partagé, et non un paramètre de plus
 *
 * Il traverse l'accueil jusqu'à la barre du bas. Le faire descendre comme paramètre
 * aurait ajouté une ligne à six signatures qui n'ont rien à voir avec un tour.
 *
 * [decisions]: docs/11-decisions.md
 */
@Stable
class TourAnchors {
    private val rects = mutableStateMapOf<TourTarget, Rect>()

    operator fun get(target: TourTarget): Rect? = rects[target]

    internal fun place(target: TourTarget, rect: Rect) {
        rects[target] = rect
    }
}

/**
 * Déclare qu'un élément est ce dont le tour parle.
 *
 * `boundsInRoot` et non `boundsInWindow` : le voile du tour est posé à la racine de
 * l'accueil, et les deux doivent compter depuis la même origine, sinon le trou se
 * décale de la hauteur de la barre d'état.
 */
fun Modifier.tourAnchor(anchors: TourAnchors, target: TourTarget): Modifier =
    onGloballyPositioned { anchors.place(target, it.boundsInRoot()) }

/** Les quatre endroits que le tour montre du doigt. */
enum class TourTarget {
    /** Le bloc de la journée : l'hexagone et les six compteurs. */
    DAY,

    /** Le champ de description, celui qui parle à l'IA. */
    FIELD,

    /** L'appareil photo. */
    CAMERA,

    /** Le « + », et les trois façons d'ajouter qu'il cache. */
    MORE,
}

/**
 * Pose une ancre de tour, ou rien du tout.
 *
 * `null` est le cas courant : il n'y a pas de tour en cours, et l'accueil n'a alors
 * aucune raison de se mesurer à chaque recomposition.
 */
fun Modifier.tourAnchorOrNot(anchors: TourAnchors?, target: TourTarget): Modifier =
    if (anchors == null) this else tourAnchor(anchors, target)
