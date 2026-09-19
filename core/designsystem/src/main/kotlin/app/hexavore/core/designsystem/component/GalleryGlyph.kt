package app.hexavore.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Une image dans un cadre : la galerie du téléphone.
 *
 * Cinquième glyphe tracé à la main, pour la raison inchangée : `material-icons-core`
 * n'a pas d'icône d'image, et `material-icons-extended` embarque plusieurs milliers
 * d'icônes pour en utiliser une.
 *
 * Un cadre, une colline et un soleil — trois formes, parce qu'un cadre seul se lit
 * comme une fenêtre et un cadre avec un soleil comme un lever de soleil.
 */
@Composable
fun GalleryGlyph(contentDescription: String, modifier: Modifier = Modifier, size: Dp = GalleryGlyphSize) {
    val ink = LocalContentColor.current

    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        drawGallery(ink)
    }
}

private val GalleryGlyphSize: Dp = 24.dp

private fun DrawScope.drawGallery(ink: Color) {
    val stroke = Stroke(width = size.minDimension * STROKE_FRACTION)

    drawRect(
        color = ink,
        topLeft = Offset(size.width * FRAME_LEFT, size.height * FRAME_TOP),
        size = Size(size.width * FRAME_SIDE, size.height * FRAME_SIDE),
        style = stroke,
    )
    drawCircle(
        color = ink,
        radius = size.minDimension * SUN_RADIUS,
        center = Offset(size.width * SUN_X, size.height * SUN_Y),
    )
    // La colline : elle part du bord gauche du cadre, culmine au tiers, et redescend
    // jusqu'au bord droit -- pleine, pour se lire a seize points comme a quarante.
    drawPath(
        path = Path().apply {
            moveTo(size.width * FRAME_LEFT, size.height * HILL_BOTTOM)
            lineTo(size.width * HILL_PEAK_X, size.height * HILL_PEAK_Y)
            lineTo(size.width * (FRAME_LEFT + FRAME_SIDE), size.height * HILL_BOTTOM)
            close()
        },
        color = ink,
    )
}

private const val STROKE_FRACTION = 0.09f
private const val FRAME_LEFT = 0.12f
private const val FRAME_TOP = 0.12f
private const val FRAME_SIDE = 0.76f
private const val SUN_RADIUS = 0.07f
private const val SUN_X = 0.36f
private const val SUN_Y = 0.34f
private const val HILL_PEAK_X = 0.58f
private const val HILL_PEAK_Y = 0.42f
private const val HILL_BOTTOM = 0.88f
