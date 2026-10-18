package app.hexavore.feature.home.tour

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

    /**
     * Le quartier que le tour met en avant, ou `null` quand il ne parle pas d'une macro.
     *
     * **La figure s'éclaire elle-même.** Les six étapes de macros désignaient leur barre
     * en bas d'écran ; or une barre nommée et chiffrée n'a rien à expliquer, alors que
     * le triangle qui lui correspond dans l'hexagone, lui, ne se devine pas
     * ([D147][decisions]). Le tour pose donc ici la macro dont il parle, et l'hexagone
     * la met en avant **avec son propre mécanisme** — celui du doigt, un quartier qui
     * avance et cinq qui reculent.
     *
     * Passer par la mise en avant de la figure plutôt que par un découpage du voile a
     * une conséquence qui vaut le détour : le tour montre le comportement réel de
     * l'application, et non une imitation qui s'en écarterait au premier changement.
     *
     * [decisions]: docs/11-decisions.md
     */
    var spotlight: Macro? by mutableStateOf(null)
        internal set
}

/**
 * Déclare qu'un élément est ce dont le tour parle.
 *
 * `boundsInRoot` et non `boundsInWindow` : l'ancre et le voile doivent compter depuis la
 * même origine, sinon le trou se décale de la hauteur de la barre d'état. Le voile
 * ramène ensuite l'ancre à sa propre place, par [seenFrom].
 */
fun Modifier.tourAnchor(anchors: TourAnchors, target: TourTarget): Modifier =
    onGloballyPositioned { anchors.place(target, it.boundsInRoot()) }

/**
 * Le rectangle d'une ancre vu depuis ce qui le dessine, posé en [origin] de la racine.
 *
 * Les ancres comptent depuis la racine de la composition ; le voile, depuis sa propre
 * place. Les deux coïncidaient tant que l'accueil commençait au coin de l'écran. En
 * paysage, le cadre des écrans l'en écarte de la largeur de l'encoche ou de la barre de
 * navigation, et le trou se serait décalé d'autant sur le côté ([D171][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun Rect.seenFrom(origin: Offset): Rect = translate(-origin)

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

    /**
     * La figure elle-même, pour les six étapes de macros.
     *
     * Une seule cible pour six étapes : ce qui change d'une à l'autre n'est pas l'endroit
     * mais le **quartier mis en avant**, que [TourAnchors.spotlight] porte (D147).
     */
    HEXAGON,

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
