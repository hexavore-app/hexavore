package app.hexavore.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface
import app.hexavore.core.designsystem.theme.MacroPalette
import app.hexavore.core.designsystem.theme.Motion
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.saturate
import app.hexavore.domain.nutrition.Macro

/**
 * L'hexagone des macros : six compteurs, six quartiers.
 *
 * La figure qui donne son nom au projet, et celle qui répond à une seule question —
 * comment va ma journée. Elle ne dit pas « 87 / 144 g » : c'est le rôle des barres
 * qui l'accompagnent, et les deux ne se concurrencent pas.
 *
 * Hexagone à sommet plat. Les calories occupent le quartier du haut, puis on tourne
 * dans le sens horaire.
 *
 * **Le contour est l'objectif**, et un quartier peut le dépasser. Pour que rien ne
 * sorte de la zone, le dessin entier se met à l'échelle : au plafond de 150 %,
 * l'hexagone cible garde les deux tiers de sa taille. Ce rétrécissement est
 * lui-même le signal — on voit qu'on a débordé avant d'avoir lu quelle macro.
 *
 * **Les six quartiers brillent**, quel que soit leur niveau. Voir [drawQuarter].
 *
 * Le rayon est proportionnel à la valeur, **pas la surface**. Même convention que
 * l'anneau et les barres ; l'incohérence serait d'en changer d'un composant à
 * l'autre.
 *
 * @see docs/08-design-system.md — section `MacroHexagon`
 * @see docs/11-decisions.md — D33
 */
@Composable
fun MacroHexagon(quarters: Map<Macro, MacroQuarter>, modifier: Modifier = Modifier) {
    val palettes = Macro.entries.associateWith { NeonTheme.macros[it] }
    val outline = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    // Grasse et de la taille d'un titre : ces six lettres sont le second canal
    // exige par la regle de daltonisme, et un second canal qu'il faut chercher des
    // yeux n'en est pas un. En labelSmall, elles se lisaient a peine sur le fond.
    val initialStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val spec = tween<Float>(durationMillis = NeonTheme.motion.gaugeValueMillis, easing = Motion.GaugeEasing)

    // L'ajustement s'anime comme les quartiers : sans cela, la figure sauterait de
    // taille au moment precis ou une macro franchit son objectif.
    val fit by animateFloatAsState(
        targetValue = 1f / maxOf(1f, Macro.entries.maxOf { cappedRatio(quarters[it]) }),
        animationSpec = spec,
        label = "ajustement de l hexagone",
    )
    val ratios = Macro.entries.associateWith { macro ->
        animateFloatAsState(cappedRatio(quarters[macro]), spec, label = "quartier ${macro.name}").value
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(HEXAGON_ASPECT)
            // Exclu de l'arbre d'accessibilite : les six memes valeurs sont juste
            // en dessous, dans les barres, sous une forme qui se lit bien mieux a
            // la voix. Cette exclusion tient tant que les barres restent.
            .clearAndSetSemantics { },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(HEXAGON_ASPECT)) {
            val centre = Offset(size.width / 2f, size.height / 2f)

            // Ce qu'une lettre occupe autour de son centre. La plus grande des deux
            // dimensions : elle est posee par son centre, et on ignore de quel cote
            // elle debordera.
            val labelExtent = measurer.measure(Macro.CALORIES.initial, initialStyle).size.let {
                maxOf(it.width, it.height) / 2f
            }
            // La lueur, l'intervalle et les lettres sont reserves ici, une fois. Sans
            // cette reserve, la lueur du quartier le plus rempli sortait de la zone et
            // se faisait rogner net -- un neon coupe au couteau, ce qu'aucun neon ne
            // fait -- et la lettre du haut avec elle.
            val place = hexagonFit(
                width = size.width,
                height = size.height,
                labelExtent = labelExtent,
                glow = GlowRoom.toPx(),
                gap = LabelGap.toPx(),
            )
            val radius = place.radius
            val target = radius * fit

            drawQuarters(centre, target, ratios, palettes)

            // Le contour par-dessus les quartiers : c'est la reference a laquelle
            // tout se compare, elle ne doit jamais etre masquee.
            drawPath(hexagonPath(centre, target), outline, style = Stroke(width = OutlineWidth.toPx()))

            drawInitials(centre, place.labelRadius, measurer, initialStyle, palettes)
        }
    }
}

/**
 * Les six quartiers, puis les six lueurs. **Deux passes, et l'ordre compte.**
 *
 * Dessinée quartier par quartier, la lueur d'une arête latérale se fait recouvrir par
 * le remplissage du quartier voisin, et il ne reste de néon que sur l'arête extérieure.
 *
 * Sortie de la composable, qui atteignait le seuil de longueur : ce qu'elle garde est
 * l'animation et la place de la figure, ce qui part est le tracé. Deux sujets, deux
 * fonctions.
 */
private fun DrawScope.drawQuarters(
    centre: Offset,
    radius: Float,
    ratios: Map<Macro, Float>,
    palettes: Map<Macro, MacroPalette>,
) {
    Macro.entries.forEach { macro ->
        drawQuarter(
            centre = centre,
            radius = radius * ratios.getValue(macro),
            axis = macro.axisDegrees,
            palette = palettes.getValue(macro),
            ratio = ratios.getValue(macro),
        )
    }

    Macro.entries.forEach { macro ->
        val ratio = ratios.getValue(macro)
        if (ratio <= 0f) return@forEach
        drawQuarterGlow(
            centre = centre,
            radius = radius * ratio,
            axis = macro.axisDegrees,
            palette = palettes.getValue(macro),
            intensity = ratio.coerceAtMost(1f),
        )
    }
}

private const val OVERSHOOT_SATURATION = 0.30f
private const val ZIGZAG_TEETH = 7
private const val ZIGZAG_DEPTH = 0.05f

/**
 * Le nombre de couches de lueur.
 *
 * Même technique que `NeonButton` : des contours de plus en plus larges et de moins
 * en moins opaques, faute d'un flou disponible partout — `BlurMaskFilter` n'est pas
 * accéléré matériellement et imposerait un rendu logiciel à chaque image animée.
 * Trois couches suffisent à ce que l'œil ne distingue plus les paliers.
 */
private const val GLOW_LAYERS = 3

private val OutlineWidth: Dp = 2.dp

/** Ce que la lueur déborde vers l'extérieur, et qu'il faut donc réserver. */
private val GlowRoom: Dp = 12.dp

/** Entre la lueur et la lettre, pour que la seconde ne baigne pas dans la première. */
private val LabelGap: Dp = 6.dp

private val GlowSpread: Dp = GlowRoom / GLOW_LAYERS

/**
 * Part du rayon au-delà de laquelle la lueur cesse de grandir.
 *
 * Sa largeur est fixe, celle du quartier ne l'est pas : sur un quartier presque
 * vide, une lueur de pleine largeur serait plus grande que lui, tacherait le centre
 * et empiéterait sur ses voisins.
 */
private const val GLOW_MAX_FRACTION = 0.12f

/**
 * Un quartier.
 *
 * **Les six brillent, quel que soit le niveau.** Les limites ne restent plus
 * sourdes sous leur seuil : une figure où trois macros sur six s'allument et trois
 * non se lit comme un défaut d'affichage plutôt que comme une information. Ce que
 * la distinction objectif/limite perd ici, elle le garde dans les barres — le
 * suffixe `max` sur la valeur et la phrase annoncée par TalkBack ([D47][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun DrawScope.drawQuarter(centre: Offset, radius: Float, axis: Float, palette: MacroPalette, ratio: Float) {
    if (ratio <= 0f) return
    val colour = if (ratio > 1f) palette.base.saturate(OVERSHOOT_SATURATION) else palette.base

    drawPath(quarterPath(centre, radius, axis), SolidColor(colour))

    if (ratio >= RATIO_CAP) {
        drawTruncation(centre, radius, axis, colour)
    }
}

/**
 * La lueur d'un quartier, tracée **sur ses trois arêtes**.
 *
 * Un contour et non une silhouette élargie, et c'est toute la différence. La
 * silhouette — un second triangle un peu plus grand, posé derrière — ne se voyait
 * que là où elle dépassait, c'est-à-dire sur la seule arête extérieure : les deux
 * arêtes latérales sont mitoyennes, et le quartier voisin recouvrait ce qui
 * dépassait de son côté. Elle avait en outre un bord franc, puisqu'un triangle
 * plein s'arrête là où il s'arrête. Une lueur qui s'arrête net n'est pas une lueur.
 *
 * Le tracé est centré sur le chemin : chaque couche déborde donc de part et
 * d'autre, à l'intérieur du quartier comme au-dehors, et les six lueurs se
 * rejoignent au centre où les six pointes se touchent.
 */
private fun DrawScope.drawQuarterGlow(
    centre: Offset,
    radius: Float,
    axis: Float,
    palette: MacroPalette,
    intensity: Float,
) {
    if (palette.glow.alpha == 0f) return
    val path = quarterPath(centre, radius, axis)
    val spread = minOf(GlowSpread.toPx(), radius * GLOW_MAX_FRACTION)

    repeat(GLOW_LAYERS) { layer ->
        val colour = palette.glow.copy(
            alpha = palette.glow.alpha * intensity / (GLOW_LAYERS * (layer + 1)),
        )
        drawPath(
            path = path,
            color = colour,
            style = Stroke(width = spread * (layer + 1) * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
}

/**
 * L'arête d'un quartier tronqué, en dents de scie.
 *
 * Convention de rupture d'échelle des graphiques : elle dit que la valeur continue
 * au-delà, sans prétendre montrer jusqu'où.
 */
private fun DrawScope.drawTruncation(centre: Offset, radius: Float, axis: Float, colour: Color) {
    val depth = radius * ZIGZAG_DEPTH
    val path = Path()
    for (tooth in 0..ZIGZAG_TEETH) {
        val fraction = tooth.toFloat() / ZIGZAG_TEETH
        val degrees = axis - HALF_SECTOR + fraction * SECTOR_DEGREES
        val point = pointAt(centre, radius + if (tooth % 2 == 0) depth else -depth, degrees)
        if (tooth == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, colour, style = Stroke(width = OutlineWidth.toPx(), cap = StrokeCap.Round))
}

/**
 * Les six initiales, posées à l'extérieur de la zone.
 *
 * Second canal exigé par la règle de daltonisme : la couleur ne renseigne jamais
 * seule. Une lettre tient là où un libellé complet ne tiendrait pas, même à 200 %
 * de police — à condition qu'elle se voie, d'où une graisse et une taille de titre
 * plutôt que la légende de 12 points qu'elles portaient d'abord.
 *
 * Leur rayon est celui de la **zone** et non du contour : elles ne bougent pas
 * quand l'hexagone cible rétrécit sous l'effet d'un dépassement. Six repères qui se
 * déplaceraient à chaque saisie ne seraient plus des repères.
 *
 * @param radius le rayon auquel poser le centre des lettres, tel que [hexagonFit] le
 *   calcule : mesuré depuis l'arête, qu'une lettre regarde en face, et non depuis le
 *   sommet, qui est plus loin d'un huitième.
 */
private fun DrawScope.drawInitials(
    centre: Offset,
    radius: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    palettes: Map<Macro, MacroPalette>,
) {
    Macro.entries.forEach { macro ->
        val layout = measurer.measure(macro.initial, style)
        val anchor = pointAt(centre, radius, macro.axisDegrees)
        drawText(
            textLayoutResult = layout,
            color = palettes.getValue(macro).base,
            topLeft = Offset(anchor.x - layout.size.width / 2f, anchor.y - layout.size.height / 2f),
        )
    }
}

/**
 * L'initiale d'une macro.
 *
 * Écrite ici et non en ressource : ce sont des symboles de figure, pas des libellés
 * à traduire — les six sont les mêmes en français comme en anglais.
 */
private val Macro.initial: String
    get() = when (this) {
        Macro.CALORIES -> "C"
        Macro.PROTEIN -> "P"
        Macro.CARBS -> "G"
        Macro.SUGARS -> "S"
        Macro.FAT -> "L"
        Macro.FIBER -> "F"
    }

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun MacroHexagonJourneeNormalePreview() {
    PreviewSurface {
        MacroHexagon(
            mapOf(
                Macro.CALORIES to MacroQuarter(0.61f),
                Macro.PROTEIN to MacroQuarter(0.78f),
                Macro.FIBER to MacroQuarter(0.34f),
                Macro.CARBS to MacroQuarter(0.72f),
                Macro.SUGARS to MacroQuarter(0.65f),
                Macro.FAT to MacroQuarter(0.58f),
            ),
        )
    }
}

@NeonPreviews
@Composable
private fun MacroHexagonDepassementPreview() {
    PreviewSurface {
        MacroHexagon(
            mapOf(
                Macro.CALORIES to MacroQuarter(1.12f),
                Macro.PROTEIN to MacroQuarter(0.9f),
                Macro.FIBER to MacroQuarter(0.4f),
                Macro.CARBS to MacroQuarter(1.4f),
                Macro.SUGARS to MacroQuarter(2.3f),
                Macro.FAT to MacroQuarter(0.8f),
            ),
        )
    }
}
