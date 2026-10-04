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
 * même partout ([08][design]). Il se lit dans [macroDialOrder], qui le **déduit** de la
 * géométrie : ce composant suivait l'ordre de déclaration de l'énumération, et montrait
 * donc les six mêmes couleurs dans un autre ordre que la figure juste au-dessus
 * ([D145][decisions]).
 *
 * **Une journée sans saisie ne s'appelle pas ici.** Ce composant dessine des
 * progressions ; l'absence de journée est l'affaire de l'écran, qui ne le compose
 * simplement pas. Lui passer six zéros dessinerait un anneau vide **identique** à
 * celui d'un jour de jeûne, et c'est exactement la confusion que la tranche 7 existe
 * pour éviter.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [design]: docs/08-design-system.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun MacroSegmentRing(
    progress: Map<Macro, Float>,
    modifier: Modifier = Modifier,
    diameter: Dp = MacroRingDefaults.CalendarDiameter,
    strokeWidth: Dp = SegmentStrokeWidth,
    contentDescription: String? = null,
    /**
     * L'anneau se ferme **en or** : un tour complet, d'un seul tenant.
     *
     * La forme seule avait été préférée à une teinte, pour ne pas ajouter un septième
     * rôle de couleur. À l'usage, ça ne marchait pas : à vingt millimètres de diamètre,
     * dans un bandeau de sept jours, un cercle entier ne se distingue d'un cercle brisé
     * qu'en le cherchant — et ce qui se veut exceptionnel ne se cherche pas
     * ([D146][decisions]).
     *
     * L'or ne dit aucune donnée, et c'est ce qui l'autorise : il ne se pose jamais à
     * côté des six pour être comparé à elles. Les couleurs de macros laissent donc la
     * place — un anneau d'or n'a plus de segments, parce qu'une journée tenue n'est plus
     * six compteurs mais un résultat.
     *
     * [decisions]: docs/11-decisions.md
     */
    closed: Boolean = false,
    center: @Composable () -> Unit = {},
) {
    val palettes = Macro.entries.associateWith { NeonTheme.macros[it].base }
    val trackColor = MaterialTheme.colorScheme.outline
    val or = NeonTheme.gold

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
            // L'ordre du cadran, et non celui de l'enumeration : six couleurs posees
            // dans un autre ordre que l'hexagone juste au-dessus retirent a la
            // position toute valeur d'indice (D145).
            macroDialOrder.forEachIndexed { index, macro ->
                val start = START_ANGLE + index * SEGMENT_SWEEP
                if (closed) {
                    // Un demi-degre de recouvrement : sans lui, l'arrondi des traits
                    // laisse six cheveux de fond entre les arcs, et l'anneau n'a plus
                    // l'air entier.
                    drawSegment(or, start, SEGMENT_SWEEP + CLOSING_OVERLAP, stroke)
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
