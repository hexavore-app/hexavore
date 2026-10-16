package app.hexavore.feature.weight

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.NeonTheme

/**
 * L'encre et les traits de la courbe, **partagés avec sa légende**.
 *
 * Une légende dont la pastille n'aurait pas exactement la teinte, l'épaisseur ou les
 * pointillés du trait qu'elle nomme serait une légende fausse ([D159][decisions]). Les
 * deux lisent donc ici, et nulle part ailleurs : un changement de palette ou de trait
 * les emporte ensemble.
 *
 * [decisions]: docs/11-decisions.md
 */
@Immutable
internal data class ChartInk(val measure: Color, val trend: Color, val aim: Color)

/** L'encre du thème courant : les pesées en gris, la tendance au cyan des calories. */
@Composable
internal fun chartInk() = ChartInk(
    measure = MaterialTheme.colorScheme.onSurfaceVariant,
    trend = NeonTheme.macros.calories.base,
    aim = MaterialTheme.colorScheme.outlineVariant,
)

/** L'épaisseur de la tendance : c'est le trait qu'on lit, il est le plus épais. */
internal val TrendWidth: Dp = 2.5.dp
internal val AimWidth: Dp = 1.5.dp
internal val MeasureRadius: Dp = 2.dp
internal val MeasureLineWidth: Dp = 1.dp

/** Assez pour suivre le trait, assez peu pour qu'il reste sous la tendance. */
internal const val MEASURE_LINE_ALPHA = 0.55f
internal const val AIM_DASH = 6f
internal const val AIM_GAP = 6f
