package app.hexavore.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.domain.nutrition.Macro

/**
 * Les six macros sur un seul anneau, un quartier chacune.
 *
 * **C'est l'hexagone réduit à ce qui survit à 44 dp.** [docs/02][parcours] le demande
 * pour la pastille du bandeau et la case du mois : à cette taille, les six quartiers
 * d'un hexagone ne se distinguent plus, alors que six arcs de cercle restent lisibles.
 * L'ordre angulaire est celui de l'hexagone et des barres de l'accueil — la position
 * sert de second canal en cas de daltonisme, et elle ne renseigne que si elle est la
 * même partout ([08][design]).
 *
 * **Une journée sans saisie ne s'appelle pas ici.** Ce composant dessine des
 * progressions ; l'absence de journée est l'affaire de l'écran, qui ne le compose
 * simplement pas. Lui passer six zéros dessinerait un anneau vide **identique** à
 * celui d'un jour de jeûne, et c'est exactement la confusion que la tranche 7 existe
 * pour éviter.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [design]: docs/08-design-system.md
 */
@Composable
fun MacroSegmentRing(
    progress: Map<Macro, Float>,
    modifier: Modifier = Modifier,
    diameter: Dp = MacroRingDefaults.CalendarDiameter,
    strokeWidth: Dp = SegmentStrokeWidth,
    contentDescription: String? = null,
    /**
     * L'anneau se ferme : les six arcs se touchent et font un tour complet.
     *
     * **Une forme, pas une septième couleur.** Ce thème s'interdit un rôle de couleur
     * de plus, et signale ce qui sort de l'ordinaire par le dessin — c'est déjà ce
     * que fait le contour pointillé d'une valeur estimée. Une journée parfaite est un
     * anneau entier là où les autres sont brisés, et une série se lit d'un coup d'œil
     * comme une suite de cercles pleins ([D143][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    closed: Boolean = false,
    center: @Composable () -> Unit = {},
) {
    val palettes = Macro.entries.associateWith { NeonTheme.macros[it].base }
    val trackColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .size(diameter)
            // Un anneau est rond, ou ce n'est pas un anneau. Quand le parent
            // refuse la largeur demandee, la hauteur suit au lieu de rester
            // entiere -- sans quoi une erreur de calcul de marges se voit comme
            // un ovale, et se cherche dans le dessin plutot que dans le calcul.
            .aspectRatio(1f)
            .let { base ->
                contentDescription
                    ?.let { text -> base.semantics { this.contentDescription = text } }
                    ?: base
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            Macro.entries.forEachIndexed { index, macro ->
                val start = START_ANGLE + index * SEGMENT_SWEEP
                if (closed) {
                    // Un demi-degre de recouvrement : sans lui, l'arrondi des traits
                    // laisse six cheveux de fond entre les arcs, et l'anneau n'a plus
                    // l'air entier.
                    drawSegment(palettes.getValue(macro), start, SEGMENT_SWEEP + CLOSING_OVERLAP, stroke)
                } else {
                    drawSegment(trackColor, start, SEGMENT_SWEEP - SEGMENT_GAP, stroke)
                    val filled = (progress[macro] ?: 0f).coerceIn(0f, 1f)
                    if (filled > 0f) {
                        drawSegment(palettes.getValue(macro), start, (SEGMENT_SWEEP - SEGMENT_GAP) * filled, stroke)
                    }
                }
            }
        }
        center()
    }
}

/**
 * Un arc, sur la même géométrie que [MacroRing].
 *
 * Le décalage d'un demi-trait évite que le tracé déborde du nœud et se fasse rogner :
 * un arc est centré sur son chemin, donc la moitié de son épaisseur sort du cercle.
 */
private fun DrawScope.drawSegment(color: Color, startAngle: Float, sweep: Float, strokeWidth: Float) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        style = Stroke(width = strokeWidth),
    )
}

/** Plus fin que l'anneau de l'accueil : six segments à 8 dp se toucheraient. */
private val SegmentStrokeWidth: Dp = 4.dp

private const val START_ANGLE = -90f
private const val SEGMENTS = 6
private const val SEGMENT_SWEEP = 360f / SEGMENTS

/** Le vide entre deux quartiers. Sans lui, les six couleurs formeraient un dégradé. */
private const val SEGMENT_GAP = 6f

/** Ce dont deux arcs se chevauchent quand l'anneau se ferme. */
private const val CLOSING_OVERLAP = 0.5f

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun MacroSegmentRingPreview() {
    PreviewSurface {
        MacroSegmentRing(
            progress = mapOf(
                Macro.CALORIES to 0.9f,
                Macro.PROTEIN to 0.6f,
                Macro.CARBS to 1f,
                Macro.SUGARS to 0.3f,
                Macro.FAT to 0.75f,
                Macro.FIBER to 0.45f,
            ),
        )
    }
}
