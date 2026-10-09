package app.hexavore.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Deux feuilles superposées : le signe de ce qui se **recopie**.
 *
 * ### Pourquoi pas le calendrier
 *
 * Le bouton portait une icône de calendrier, parce que la boîte qu'il ouvre en est un.
 * C'était désigner l'écran d'après plutôt que l'action : on y lisait « choisir une
 * date », sans savoir ce que la date allait servir à faire ([D144][decisions]). Deux
 * feuilles décalées se lisent depuis trente ans comme « copier », et n'ont pas besoin
 * d'être apprises.
 *
 * ### Et pourquoi un tracé
 *
 * `material-icons-core` n'a pas `ContentCopy`, et c'est la cinquième fois — après
 * `StarBorder` ([D62][decisions]), le code-barres, l'appareil photo et les étincelles
 * ([SparkleGlyph]), la réponse ne change pas : vingt lignes de tracé plutôt que
 * `material-icons-extended`, qui embarque plusieurs milliers d'icônes pour en utiliser
 * une.
 *
 * **La feuille de dessous est percée**, et non simplement cachée : son contour est
 * détouré de la feuille de devant, de sorte qu'aucun trait ne traverse celle-ci. Deux
 * rectangles dessinés l'un sur l'autre auraient donné une croix au milieu du glyphe.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun CopyGlyph(contentDescription: String, modifier: Modifier = Modifier, size: Dp = CopyGlyphSize) {
    val ink = LocalContentColor.current

    Canvas(
        modifier = modifier
            .size(size)
            // Un glyphe est une seule information : le lecteur d'ecran annonce
            // l'action, pas la forme.
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        drawCopy(ink)
    }
}

private val CopyGlyphSize: Dp = 24.dp

/** Tout en fractions de la boîte : le glyphe suit la taille demandée sans jeu d'assets. */
private fun DrawScope.drawCopy(ink: Color) {
    val trait = size.minDimension * STROKE
    val coin = CornerRadius(size.minDimension * CORNER, size.minDimension * CORNER)

    val devant = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = androidx.compose.ui.geometry.Rect(
                    offset = Offset(size.width * FRONT_X, size.height * FRONT_Y),
                    size = Size(size.width * SHEET_W, size.height * SHEET_H),
                ),
                cornerRadius = coin,
            ),
        )
    }
    val derriere = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = androidx.compose.ui.geometry.Rect(
                    offset = Offset(size.width * BACK_X, size.height * BACK_Y),
                    size = Size(size.width * SHEET_W, size.height * SHEET_H),
                ),
                cornerRadius = coin,
            ),
        )
    }

    // Ce qui depasse de la feuille de devant, et rien d'autre : le trait de la feuille
    // du fond s'arrete la ou l'autre commence, comme deux papiers poses sur une table.
    val visible = Path().apply { op(derriere, devant, PathOperation.Difference) }

    drawPath(visible, ink, style = Stroke(width = trait))
    drawPath(devant, ink, style = Stroke(width = trait))
}

/** L'épaisseur du trait, et le rayon des coins — les deux en fraction du côté. */
private const val STROKE = 0.075f
private const val CORNER = 0.09f

/** La feuille, et le décalage qui fait qu'on en voit deux. */
private const val SHEET_W = 0.52f
private const val SHEET_H = 0.62f
private const val BACK_X = 0.14f
private const val BACK_Y = 0.10f
private const val FRONT_X = 0.34f
private const val FRONT_Y = 0.28f
