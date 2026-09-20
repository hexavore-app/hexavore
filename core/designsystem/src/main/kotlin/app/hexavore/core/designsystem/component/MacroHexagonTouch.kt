package app.hexavore.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.hexavore.domain.nutrition.Macro
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

// Ce qu'un doigt désigne sur la figure, et où se pose ce qui la commente. Deux
// questions de géométrie, séparées du tracé pour la même raison que le reste : elles
// se raisonnent au crayon, et aucun écran n'est nécessaire pour les éprouver.

/**
 * Le quartier qu'un appui désigne, ou `null` quand l'appui tombe hors de la figure.
 *
 * **Par l'angle, et non par le triangle.** Un test d'appartenance au triangle exact
 * refuserait les appuis tombés entre l'arête et la lettre qui la surplombe, alors que
 * viser une lettre est la façon la plus naturelle de désigner sa macro. Les six
 * secteurs se partagent donc tout le disque : un appui dans la zone désigne toujours
 * quelqu'un, et c'est la distance seule qui dit s'il est dans la zone.
 *
 * **Le centre n'a pas de zone morte.** Les six pointes s'y touchent, donc l'angle y
 * est instable au pixel près — mais un appui au centre exact reste un appui qui doit
 * répondre quelque chose plutôt que rien, et personne ne vise le point où six
 * triangles se rejoignent en espérant un résultat précis.
 *
 * @param reach rayon au-delà duquel l'appui n'est plus sur la figure. C'est celui des
 *   lettres et non celui du contour : elles font partie de ce qu'on vise.
 */
internal fun macroAt(tap: Offset, centre: Offset, reach: Float): Macro? {
    val dx = tap.x - centre.x
    // L'ordonnee descend a l'ecran, les angles de la figure montent : le signe
    // s'inverse ici, une fois, comme dans `pointAt`.
    val dy = centre.y - tap.y
    if (hypot(dx, dy) > reach) return null

    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return Macro.entries.minBy { angleBetween(degrees, it.axisDegrees) }
}

/**
 * Le point d'un quartier qu'une bulle désigne, dans le repère de la figure.
 *
 * **Aux deux tiers du rayon, et non au sommet.** Un quartier est un triangle qui part
 * du centre : sa pointe extérieure est le seul endroit qu'il ne partage avec personne,
 * mais c'est aussi le plus étroit, et une pointe de bulle qui y aboutirait semblerait
 * désigner la lettre plutôt que le triangle. Aux deux tiers, elle désigne le corps.
 *
 * Le rayon vient de la plus petite dimension : l'hexagone est à sommet plat, donc plus
 * large que haut, et c'est la hauteur qui le limite.
 */
fun Rect.macroAnchor(macro: Macro): Offset =
    pointAt(center, minOf(width, height) / 2f * ANCHOR_REACH, macro.axisDegrees)

private const val ANCHOR_REACH = 0.66f

/** L'écart entre deux directions, au plus court : 350° et 10° sont à vingt degrés. */
private fun angleBetween(from: Float, to: Float): Float {
    val ecart = abs(from - to) % FULL_TURN
    return if (ecart > HALF_TURN) FULL_TURN - ecart else ecart
}

private const val FULL_TURN = 360f

private const val HALF_TURN = FULL_TURN / 2f

/**
 * Où se pose la bulle, et de quel côté pointe sa pointe.
 *
 * [tailX] est mesuré depuis le bord gauche de la bulle, [x] et [y] depuis celui du
 * conteneur.
 */
internal data class BubbleSpot(val offset: IntOffset, val tailX: Int, val tailOnTop: Boolean)

/**
 * La place d'une bulle qui commente un quartier.
 *
 * **Elle se pose du côté opposé au quartier**, et c'est la seule règle qui compte : une
 * bulle qui recouvrirait ce qu'elle explique ferait disparaître la surbrillance et le
 * grossissement au moment précis où on vient de les demander. Un quartier du haut
 * renvoie donc la bulle sous le centre, un quartier du bas au-dessus.
 *
 * **La pointe suit le quartier, le corps suit l'écran.** Les deux se désolidarisent dès
 * que la bulle bute sur un bord : le corps s'arrête, la pointe continue de désigner. Le
 * contraire — une bulle qui sort de l'écran pour rester centrée sur sa pointe — était la
 * seule chose à éviter sur une figure aussi large que l'écran.
 *
 * @param anchor le point visé, sur l'axe du quartier.
 * @param centre le centre de la figure : c'est lui qui sépare le haut du bas.
 * @param tailInset de combien la pointe se tient à l'écart des angles arrondis.
 */
internal fun bubbleSpot(
    anchor: Offset,
    centre: Offset,
    bubble: IntSize,
    container: IntSize,
    margin: Int,
    tailInset: Int,
): BubbleSpot {
    val dessous = anchor.y < centre.y
    val brut = if (dessous) centre.y + margin else centre.y - margin - bubble.height
    val y = brut.toInt().coerceIn(0, (container.height - bubble.height).coerceAtLeast(0))
    val x = (anchor.x - bubble.width / 2f).toInt().coerceIn(0, (container.width - bubble.width).coerceAtLeast(0))

    return BubbleSpot(
        offset = IntOffset(x, y),
        // Bornee dans la bulle : une pointe posee sur un angle arrondi ne tient a
        // rien, et une pointe hors de la bulle flotte toute seule.
        tailX = (anchor.x - x).toInt().coerceIn(tailInset, (bubble.width - tailInset).coerceAtLeast(tailInset)),
        tailOnTop = dessous,
    )
}
