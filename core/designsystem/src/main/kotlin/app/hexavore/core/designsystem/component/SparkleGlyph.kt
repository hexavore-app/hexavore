package app.hexavore.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Deux étincelles : le signe de ce qui est **proposé par un modèle**.
 *
 * **`material-icons-core` n'en a pas**, et c'est la quatrième fois — après `StarBorder`
 * ([D62][decisions]), le code-barres et l'appareil photo, la réponse ne change pas :
 * vingt lignes de tracé plutôt que `material-icons-extended`, qui embarque plusieurs
 * milliers d'icônes pour en utiliser une.
 *
 * **Deux étincelles et non une étoile.** Une étoile à cinq branches est déjà prise par
 * les favoris, et une seule étincelle se lit comme une décoration ; deux, de tailles
 * différentes, sont devenues le signe usuel de ce qu'une machine a produit.
 *
 * Chaque étincelle est un losange à côtés creusés : quatre pointes fines, obtenues en
 * tirant les côtés vers le centre plutôt qu'en traçant huit segments.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun SparkleGlyph(contentDescription: String, modifier: Modifier = Modifier, size: Dp = SparkleGlyphSize) {
    val ink = LocalContentColor.current

    Canvas(
        modifier = modifier
            .size(size)
            // Un glyphe est une seule information : le lecteur d'ecran annonce
            // l'action, pas la forme.
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        drawSparkles(ink)
    }
}

private val SparkleGlyphSize: Dp = 24.dp

/** Tout en fractions de la boîte : le glyphe suit la taille demandée sans jeu d'assets. */
private fun DrawScope.drawSparkles(ink: Color) {
    drawPath(sparkle(Offset(size.width * BIG_X, size.height * BIG_Y), size.minDimension * BIG_RADIUS), ink)
    drawPath(sparkle(Offset(size.width * SMALL_X, size.height * SMALL_Y), size.minDimension * SMALL_RADIUS), ink)
}

/**
 * Une étincelle à quatre pointes.
 *
 * Les quatre sommets sont sur les axes, à [radius] du centre ; entre eux, le chemin
 * passe par un point de contrôle **proche du centre**, ce qui creuse les côtés et
 * affine les pointes. Un losange aux côtés droits donnerait un cerf-volant.
 */
private fun sparkle(centre: Offset, radius: Float): Path = Path().apply {
    val waist = radius * WAIST
    moveTo(centre.x, centre.y - radius)
    quadraticTo(centre.x + waist, centre.y - waist, centre.x + radius, centre.y)
    quadraticTo(centre.x + waist, centre.y + waist, centre.x, centre.y + radius)
    quadraticTo(centre.x - waist, centre.y + waist, centre.x - radius, centre.y)
    quadraticTo(centre.x - waist, centre.y - waist, centre.x, centre.y - radius)
    close()
}

/** Ce qui reste de largeur à mi-chemin d'une pointe : plus c'est petit, plus c'est fin. */
private const val WAIST = 0.16f

private const val BIG_X = 0.42f
private const val BIG_Y = 0.46f
private const val BIG_RADIUS = 0.34f
private const val SMALL_X = 0.76f
private const val SMALL_Y = 0.22f
private const val SMALL_RADIUS = 0.16f
