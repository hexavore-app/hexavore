package app.hexavore.feature.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Spacing

/**
 * La légende de la courbe, et la case de la tendance.
 *
 * ### Pourquoi une légende
 *
 * Deux traits sans nom — un gris, un coloré — se lisaient mal : on se demandait lequel
 * était la mesure et lequel la moyenne ([D159][decisions]). Chaque tracé est nommé ici,
 * avec une pastille dessinée **exactement** comme lui, tirée de la même encre
 * ([ChartInk]).
 *
 * ### Pourquoi la case est dans la légende
 *
 * Le geste qui éteint la tendance se pose à côté de son nom : une case « tendance »
 * ailleurs sur l'écran demanderait de deviner à quel trait elle s'applique. Toute
 * l'entrée bascule, et TalkBack l'annonce comme une case à cocher nommée « Tendance ».
 *
 * La trajectoire visée n'apparaît dans la légende que quand elle est tracée : nommer un
 * trait absent ferait chercher ce qui n'est pas là.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChartLegend(ink: ChartInk, aimShown: Boolean, trendShown: Boolean, onTrendShown: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LegendEntry(
                label = stringResource(R.string.weight_legend_measures),
                modifier = Modifier.align(Alignment.CenterVertically),
            ) { MeasureSwatch(ink.measure) }

            TrendEntry(
                color = ink.trend,
                shown = trendShown,
                onShown = onTrendShown,
                modifier = Modifier.align(Alignment.CenterVertically),
            )

            if (aimShown) {
                LegendEntry(
                    label = stringResource(R.string.weight_legend_aim),
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) { AimSwatch(ink.aim) }
            }
        }

        // Ce que « tendance » veut dire, en une ligne : le mot seul ne dit pas pourquoi
        // le trait colore ne passe pas par les points.
        Text(
            text = stringResource(R.string.weight_legend_trend_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Une entrée de légende : sa pastille, et son nom. */
@Composable
private fun LegendEntry(label: String, modifier: Modifier = Modifier, swatch: @Composable () -> Unit) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        swatch()
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * L'entrée de la tendance, **qui se coche**.
 *
 * Toute l'entrée bascule et non la seule case : une cible de 24 dp sous un graphique est
 * une cible qu'on rate. `onCheckedChange = null` : la case ne reçoit plus le clic, c'est
 * l'entrée qui le porte — sans quoi TalkBack annoncerait deux cibles pour une décision.
 */
@Composable
private fun TrendEntry(color: Color, shown: Boolean, onShown: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .toggleable(value = shown, role = Role.Checkbox, onValueChange = onShown)
            .padding(end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Checkbox(checked = shown, onCheckedChange = null)
        TrendSwatch(color)
        Text(
            text = stringResource(R.string.weight_legend_trend),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** La pastille des pesées : un trait fin et effacé, et un point posé dessus. */
@Composable
private fun MeasureSwatch(color: Color) {
    Canvas(modifier = Modifier.size(SwatchWidth, SwatchHeight)) {
        val middle = size.height / 2f
        drawLine(
            color = color.copy(alpha = MEASURE_LINE_ALPHA),
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = MeasureLineWidth.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = color, radius = MeasureRadius.toPx(), center = Offset(size.width / 2f, middle))
    }
}

/** La pastille de la tendance : le trait épais et coloré de la courbe. */
@Composable
private fun TrendSwatch(color: Color) {
    Canvas(modifier = Modifier.size(SwatchWidth, SwatchHeight)) {
        val middle = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = TrendWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** La pastille de la trajectoire : ses pointillés. */
@Composable
private fun AimSwatch(color: Color) {
    Canvas(modifier = Modifier.size(SwatchWidth, SwatchHeight)) {
        val middle = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = AimWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(AIM_DASH, AIM_GAP)),
        )
    }
}

/** Assez large pour qu'un pointillé y montre deux tirets, assez court pour rester une pastille. */
private val SwatchWidth: Dp = 24.dp
private val SwatchHeight: Dp = 12.dp
