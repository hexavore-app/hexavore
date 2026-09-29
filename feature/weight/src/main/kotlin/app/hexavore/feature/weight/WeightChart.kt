package app.hexavore.feature.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.usecase.TrendPoint
import app.hexavore.domain.usecase.WeightTrend
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * La courbe : les mesures, leur lissage, et le cap annoncé.
 *
 * **Trois tracés qui ne se disputent pas la lecture.** Les points bruts sont discrets
 * — ils varient de deux kilos avec l'hydratation et décourageraient sans raison — la
 * moyenne mobile porte l'information, et la trajectoire annoncée passe en pointillés
 * derrière les deux : c'est un repère, pas une mesure, et le trait discontinu le dit
 * sans avoir besoin d'une légende ([D25][decisions] : une forme, jamais une couleur
 * seule).
 *
 * **Dessiné à la main plutôt qu'emprunté.** Le besoin est petit et entièrement connu :
 * trois polylignes, pas de zoom, pas de sélection, pas de second type de graphique.
 * Une bibliothèque apporterait des axes et des animations dont rien ici n'a besoin, et
 * demanderait en échange de plier son thème au nôtre.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun WeightChart(trend: WeightTrend, modifier: Modifier = Modifier) {
    val scale = remember(trend) { ChartScale.of(trend) } ?: return
    val palette = NeonTheme.macros
    val raw = MaterialTheme.colorScheme.onSurfaceVariant
    val guide = MaterialTheme.colorScheme.outlineVariant
    val description = pluralStringResource(R.plurals.weight_chart_a11y, trend.points.size, trend.points.size)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                // Une courbe ne se lit pas au doigt : le lecteur d'ecran recoit le
                // nombre de mesures, et la liste en dessous porte les chiffres.
                .clearAndSetSemantics { contentDescription = description },
        ) {
            trend.aim?.let { aim ->
                val fromKg = aim.weightAt(scale.span.start)
                val toKg = aim.weightAt(scale.span.endInclusive)
                drawAim(scale, fromKg, toKg, guide)
            }
            drawSmoothed(scale, trend.points, palette.calories.base)
            drawMeasures(scale, trend.points, raw)
        }
        // `ChartScale.of` a deja refuse une courbe de moins de deux pesees.
        trend.axisEnds()?.let { (first, last) -> Axis(first, last) }
    }
}

/**
 * Les deux bouts que l'axe écrit : la première et la dernière pesée.
 *
 * **Elle rend des [TrendPoint], et c'est tout l'intérêt.** Un point porte sa date et
 * son poids ensemble, donc le couple faux que ces libellés affichaient — une date
 * d'un côté, une borne d'axe de l'autre — ne peut plus s'écrire. C'est le type qui
 * tient la règle, pas une convention qu'il faudrait se rappeler.
 *
 * `null` sans aucune pesée, ce que [WeightChart] a déjà écarté par ailleurs.
 */
internal fun WeightTrend.axisEnds(): Pair<TrendPoint, TrendPoint>? = points.firstOrNull()?.let { it to points.last() }

/**
 * Les deux bouts de la courbe, écrits : sans eux, une pente ne dit pas combien de
 * kilos elle vaut.
 *
 * **Deux vraies pesées, et non les bornes des axes.** Ces libellés associaient la
 * première date au poids le plus bas de l'axe vertical et la dernière au plus haut.
 * Ce sont deux plages indépendantes — `span` porte les dates, `bounds` les poids —
 * mais le format « date · poids » les colle en une mesure, et sur une trajectoire
 * **descendante**, c'est-à-dire le cas dominant d'une application qui calcule des
 * objectifs de perte, le résultat annonçait l'inverse de ce que la courbe montrait.
 * L'écran portait alors trois lectures contradictoires : le tracé qui descend, ces
 * deux libellés qui montent, et la liste juste en dessous qui donne les vrais
 * chiffres.
 *
 * La première et la dernière pesée disent la même chose que les bornes — la
 * différence **est** ce que la pente vaut — en le disant juste, et en s'accordant
 * avec la liste. Les mesures brutes et non les moyennes lissées, pour la même
 * raison : ce sont elles que la liste montre.
 */
@Composable
private fun Axis(first: TrendPoint, last: TrendPoint) {
    val style = MaterialTheme.typography.labelSmall
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label(first.date, first.weightKg), style = style, color = ink)
        Text(text = label(last.date, last.weightKg), style = style, color = ink)
    }
}

@Composable
private fun label(date: LocalDate, weightKg: Double) =
    stringResource(R.string.weight_axis_label, DAY_MONTH.format(date), weightKg)

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

private val ChartHeight = 160.dp
private val SmoothedWidth = 2.5.dp
private val AimWidth = 1.5.dp
private val MeasureRadius = 2.dp
private const val AIM_DASH = 6f
private const val AIM_GAP = 6f

/** Le cap annoncé, en pointillés d'un bord à l'autre : c'est une droite. */
private fun DrawScope.drawAim(scale: ChartScale, fromKg: Double, toKg: Double, color: Color) {
    drawLine(
        color = color,
        start = Offset(0f, height(scale.yOf(fromKg))),
        end = Offset(size.width, height(scale.yOf(toKg))),
        strokeWidth = AimWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(AIM_DASH, AIM_GAP)),
    )
}

/**
 * La moyenne mobile, en évidence.
 *
 * **Les trous ne se relient pas.** Un point sans moyenne coupe le tracé : joindre deux
 * moyennes séparées par trois semaines de silence dessinerait une progression que
 * personne n'a mesurée.
 */
private fun DrawScope.drawSmoothed(scale: ChartScale, points: List<TrendPoint>, color: Color) {
    points.zipWithNext { previous, next ->
        val from = previous.averageKg ?: return@zipWithNext
        val to = next.averageKg ?: return@zipWithNext
        drawLine(
            color = color,
            start = Offset(size.width * scale.xOf(previous.date), height(scale.yOf(from))),
            end = Offset(size.width * scale.xOf(next.date), height(scale.yOf(to))),
            strokeWidth = SmoothedWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Les mesures, discrètes : des points, et aucun trait entre eux. */
private fun DrawScope.drawMeasures(scale: ChartScale, points: List<TrendPoint>, color: Color) {
    points.forEach { point ->
        drawCircle(
            color = color,
            radius = MeasureRadius.toPx(),
            center = Offset(size.width * scale.xOf(point.date), height(scale.yOf(point.weightKg))),
        )
    }
}

/**
 * La hauteur en pixels d'une fraction comptée depuis le bas.
 *
 * L'inversion vit ici et non dans [ChartScale] : un axe vertical qui monte est une
 * affaire d'écran, pas d'échelle.
 */
private fun Size.height(fraction: Float): Float = height * (1f - fraction)

private fun DrawScope.height(fraction: Float): Float = size.height(fraction)
