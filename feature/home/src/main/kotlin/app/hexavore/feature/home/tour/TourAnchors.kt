package app.hexavore.feature.home.tour

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import app.hexavore.domain.nutrition.Macro

/**
 * Ce que le tour désigne, et où ça se trouve à l'écran.
 *
 * ### Pourquoi mesurer plutôt que décrire
 *
 * Le tour parle de l'application **réelle** et non d'une maquette ([D141][decisions]) :
 * la bulle doit donc tomber à côté de l'élément dont elle parle, et le trou de lumière
 * dessus. Or ces positions dépendent de la taille de l'écran, de la langue, du clavier
 * et de la barre système — rien de tout cela ne se devine au moment d'écrire le texte.
 *
 * Chaque élément concerné se mesure donc lui-même, par [tourAnchor], et dépose son
 * rectangle ici. Le tour lit ce qu'il trouve : un élément qui n'est pas à l'écran n'a
 * pas d'ancre, et l'étape qui le vise se contente alors de parler au centre.
 *
 * ### Un état partagé, et non un paramètre de plus
 *
 * Il traverse l'accueil jusqu'à la barre du bas et jusqu'aux six barres de macros. Le
 * faire descendre comme paramètre aurait ajouté une ligne à une dizaine de signatures
 * qui n'ont rien à voir avec un tour.
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

/**
 * Pose une ancre de tour, ou rien du tout.
 *
 * `null` est le cas courant : il n'y a pas de tour en cours, et l'accueil n'a alors
 * aucune raison de se mesurer à chaque recomposition.
 */
fun Modifier.tourAnchorOrNot(anchors: TourAnchors?, target: TourTarget): Modifier =
    if (anchors == null) this else tourAnchor(anchors, target)

/**
 * Les endroits que le tour montre du doigt.
 *
 * **Un par élément, et non un par zone.** Éclairer la barre du bas entière pour parler
 * du champ de description revient à ne rien désigner : l'œil ne sait pas lequel des
 * trois boutons on lui montre ([D143][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
enum class TourTarget {
    /** Le bloc de la journée : l'hexagone et ce qu'il porte. */
    DAY,

    /** Le grand chiffre, qui est un nombre de calories. */
    CALORIES,

    PROTEIN,
    CARBS,
    SUGARS,
    FAT,
    FIBER,

    /** Le bandeau des sept jours. */
    CALENDAR,

    /** Le champ de description, celui qui parle à l'IA. */
    FIELD,

    /** L'appareil photo. */
    CAMERA,

    /** Le « + », et les trois façons d'ajouter qu'il cache. */
    MORE,

    /** L'accès aux réglages, en haut à droite. */
    SETTINGS,
}

/**
 * La cible qui correspond à une macro.
 *
 * Les six sont nommées une par une plutôt que portées par un paramètre : une
 * énumération se parcourt, se complète et se lit dans l'ordre du tour, et c'est elle
 * qui dit **que ces six-là sont des cibles** — un type générique l'aurait laissé
 * entendre sans jamais le garantir.
 */
internal fun Macro.tourTarget(): TourTarget = when (this) {
    Macro.CALORIES -> TourTarget.CALORIES
    Macro.PROTEIN -> TourTarget.PROTEIN
    Macro.CARBS -> TourTarget.CARBS
    Macro.SUGARS -> TourTarget.SUGARS
    Macro.FAT -> TourTarget.FAT
    Macro.FIBER -> TourTarget.FIBER
}
