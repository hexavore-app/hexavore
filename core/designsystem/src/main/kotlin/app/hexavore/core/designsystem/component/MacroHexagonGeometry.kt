package app.hexavore.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import app.hexavore.domain.nutrition.Macro
import kotlin.math.cos
import kotlin.math.sin

// Géométrie de l'hexagone, isolée du tracé : elle se raisonne au crayon, et la
// séparer permet de la relire sans traverser les questions de couleur et de lueur.

/** Hauteur d'un hexagone à sommet plat, pour une largeur de 1. */
internal const val SQRT_THREE = 1.7320508f

internal const val HEXAGON_ASPECT = 2f / SQRT_THREE

private const val VERTEX_COUNT = 6

/** Un tour complet divisé par six : l'ouverture d'un quartier. */
internal const val SECTOR_DEGREES = 360f / VERTEX_COUNT

/** Demi-ouverture, de l'axe d'un quartier à l'un de ses deux sommets. */
internal const val HALF_SECTOR = SECTOR_DEGREES / 2f

/**
 * Plafond de représentation.
 *
 * Sans lui, une quantité saisie à 2 000 % réduirait l'hexagone cible à un point,
 * précisément au moment où il faut le lire pour corriger l'erreur.
 *
 * À 150 %, l'hexagone cible garde les deux tiers de sa taille. À 200 %, il n'en
 * gardait que la moitié — assez pour que les six lettres se retrouvent loin d'une
 * figure devenue petite, alors qu'un dépassement de moitié suffit largement à se
 * voir.
 */
internal const val RATIO_CAP = 1.5f

/**
 * Du centre au milieu d'une arête, pour un circumrayon de 1.
 *
 * Les sommets sont à 1, l'arête à `√3/2`. La différence compte dès qu'on veut
 * qu'un effet suive une arête plutôt qu'un cercle.
 */
internal const val APOTHEM_RATIO = SQRT_THREE / 2f

/**
 * L'axe de chaque macro, en degrés depuis l'est, sens antihoraire.
 *
 * Calories en haut, puis sens horaire — donc angles décroissants. Cet ordre est
 * celui de toute l'application : la position sert de second canal là où un libellé
 * ne tient pas, et elle ne renseigne que si elle est la même partout.
 */
internal val Macro.axisDegrees: Float
    get() = when (this) {
        Macro.CALORIES -> 90f
        Macro.PROTEIN -> 30f
        Macro.FIBER -> 330f
        Macro.CARBS -> 270f
        Macro.SUGARS -> 210f
        Macro.FAT -> 150f
    }

internal fun cappedRatio(quarter: MacroQuarter?): Float = (quarter?.ratio ?: 0f).coerceIn(0f, RATIO_CAP)

/**
 * La place de la figure dans sa zone : le rayon du contour, et celui des lettres.
 *
 * Deux rayons parce que ce sont deux cercles : les sommets de l'hexagone sont à
 * [radius], les lettres à [labelRadius] — et la seconde n'est pas la première
 * augmentée d'une marge, puisqu'une lettre se pose sur l'**axe** d'un quartier,
 * c'est-à-dire face au milieu d'une arête.
 */
internal data class HexagonFit(val radius: Float, val labelRadius: Float)

/**
 * Ce qui tient dans une zone, **lueur et lettres comprises**.
 *
 * Le calcul précédent mesurait la marge des lettres depuis le cercle des **sommets**,
 * alors qu'elles se posent face aux **arêtes**, qui sont plus près du centre d'un
 * facteur `√3/2`. Deux conséquences, et les deux se voyaient à l'écran : les lettres
 * flottaient loin du contour — un huitième du rayon de trop —, et le « C » du haut
 * sortait de la zone par le bas du calcul, donc de l'écran par le haut, rogné net par
 * le défilement qui l'encadre.
 *
 * Trois contraintes, et la plus serrée gagne :
 *
 * - **en hauteur**, la lettre du haut et celle du bas doivent tenir entières — d'où la
 *   marge comptée deux fois, une pour aller de l'arête au centre de la lettre, une
 *   pour la lettre elle-même ;
 * - **en largeur**, les deux sommets latéraux et leur lueur ;
 * - **en largeur encore**, les quatre lettres obliques, dont l'abscisse vaut `√3/2`
 *   fois leur rayon.
 *
 * @param labelExtent demi-encombrement d'une lettre, hauteur ou largeur selon la plus
 *   grande : une lettre se pose par son centre, et on ignore de quel côté elle déborde.
 */
internal fun hexagonFit(width: Float, height: Float, labelExtent: Float, glow: Float, gap: Float): HexagonFit {
    // De l'arete au centre d'une lettre : la lueur deborde, un intervalle l'empeche
    // de baigner dedans, et la lettre est posee par son centre.
    val margin = glow + gap + labelExtent
    val fromHeight = (height / 2f - margin - labelExtent) / APOTHEM_RATIO
    val fromWidth = width / 2f - glow
    val fromSideLabels = ((width / 2f - labelExtent) / APOTHEM_RATIO - margin) / APOTHEM_RATIO
    val radius = minOf(fromHeight, fromWidth, fromSideLabels).coerceAtLeast(0f)

    return HexagonFit(radius = radius, labelRadius = radius * APOTHEM_RATIO + margin)
}

/**
 * Un point du plan, à un angle et un rayon donnés.
 *
 * L'ordonnée descend à l'écran : le sinus est donc soustrait, faute de quoi
 * l'hexagone se dessine à l'envers et les calories se retrouvent en bas.
 */
internal fun pointAt(centre: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        x = centre.x + radius * cos(radians).toFloat(),
        y = centre.y - radius * sin(radians).toFloat(),
    )
}

/** Le contour complet, sommets aux multiples de 60° — donc arêtes horizontales en haut et en bas. */
internal fun hexagonPath(centre: Offset, radius: Float): Path = Path().apply {
    val vertices = List(VERTEX_COUNT) { pointAt(centre, radius, it * SECTOR_DEGREES) }
    moveTo(vertices.first().x, vertices.first().y)
    vertices.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** Le triangle d'un quartier : centre, puis les deux sommets qui bordent son arête. */
internal fun quarterPath(centre: Offset, radius: Float, axis: Float): Path = Path().apply {
    val left = pointAt(centre, radius, axis - HALF_SECTOR)
    val right = pointAt(centre, radius, axis + HALF_SECTOR)
    moveTo(centre.x, centre.y)
    lineTo(left.x, left.y)
    lineTo(right.x, right.y)
    close()
}
