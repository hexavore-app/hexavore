package app.hexavore.feature.weight

import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.usecase.TrendPoint
import app.hexavore.domain.usecase.WeightTrend
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La courbe : les pesées, leur tendance, le cap annoncé — et la légende qui les nomme.
 *
 * **Trois tracés qui ne se disputent pas la lecture.** Les pesées sont discrètes — des
 * points reliés d'un trait fin, parce qu'elles varient de deux kilos avec l'hydratation
 * et décourageraient sans raison —, la tendance porte l'information, et la trajectoire
 * annoncée passe en pointillés derrière les deux : c'est un repère, pas une mesure
 * ([D25][decisions] : une forme, jamais une couleur seule).
 *
 * **Une légende, parce que deux traits sans nom se lisent mal.** Le trait gris et le
 * trait coloré se ressemblaient assez pour qu'on se demande lequel était quoi
 * ([D159][decisions]). La légende les nomme sous le graphique, et c'est elle qui porte
 * la case de la tendance : le geste qui l'éteint se pose à côté du nom de ce qu'il éteint.
 *
 * **La tendance existe partout**, depuis qu'elle n'est plus une moyenne sur sept jours
 * mais une moyenne qui oublie à proportion du temps écoulé : plus de bout de trait isolé
 * au milieu d'une courbe aux pesées espacées.
 *
 * **Dessiné à la main plutôt qu'emprunté.** Le besoin est petit et entièrement connu :
 * trois polylignes, pas de zoom, pas de sélection, pas de second type de graphique.
 * Une bibliothèque apporterait des axes et des animations dont rien ici n'a besoin, et
 * demanderait en échange de plier son thème au nôtre.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun WeightChart(
    trend: WeightTrend,
    units: UnitSystem,
    trendShown: Boolean,
    onTrendShown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember(trend) { ChartScale.of(trend) } ?: return
    val ink = chartInk()
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
                drawAim(scale, fromKg, toKg, ink.aim)
            }
            // Le trait des pesees **sous** la tendance : c'est elle qui porte
            // l'information, et un trait fin pose par-dessus la couperait.
            drawMeasureLine(scale, trend.points, ink.measure)
            if (trendShown) drawTrend(scale, trend.points, ink.trend)
            drawMeasures(scale, trend.points, ink.measure)
        }
        // `ChartScale.of` a deja refuse une courbe de moins de deux pesees.
        trend.axisEnds()?.let { (first, last) -> Axis(first, last, units) }
        ChartLegend(ink = ink, aimShown = trend.aim != null, trendShown = trendShown, onTrendShown = onTrendShown)
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
 * avec la liste. Les mesures brutes et non la tendance, pour la même raison : ce sont
 * elles que la liste montre.
 *
 * **Et comme la liste les écrit** ([D162][decisions]) : en livres pour l'impérial, et
 * la date dans l'ordre de la langue. L'axe disait « 14/10 · 80,0 kg » au-dessus d'une
 * liste en livres, et un jour/mois fixe se lit à l'envers en anglais américain.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun Axis(first: TrendPoint, last: TrendPoint, units: UnitSystem) {
    val style = MaterialTheme.typography.labelSmall
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    // Le motif « jour et mois » que la langue prefere : 14/10 en francais, 10/14 en
    // anglais americain.
    val dayMonth = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dM"), locale)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label(dayMonth.format(first.date), first.weightKg, units), style = style, color = ink)
        Text(text = label(dayMonth.format(last.date), last.weightKg, units), style = style, color = ink)
    }
}

@Composable
private fun label(date: String, weightKg: Double, units: UnitSystem) =
    stringResource(R.string.weight_axis_label, date, weightLabel(weightKg, units))

private val ChartHeight = 160.dp

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
 * La tendance, en évidence, **d'un bout à l'autre**.
 *
 * Elle n'a plus de trous : elle existe à chaque pesée, et suit les pesées espacées sans
 * leur inventer de progression — après un long silence, elle a presque rejoint la
 * nouvelle pesée ([D159][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun DrawScope.drawTrend(scale: ChartScale, points: List<TrendPoint>, color: Color) {
    points.zipWithNext { previous, next ->
        drawLine(
            color = color,
            start = Offset(size.width * scale.xOf(previous.date), height(scale.yOf(previous.trendKg))),
            end = Offset(size.width * scale.xOf(next.date), height(scale.yOf(next.trendKg))),
            strokeWidth = TrendWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Les pesées reliées entre elles, **quel que soit l'écart**.
 *
 * C'est la mesure et non un lissage : relier deux pesées séparées de trois jours ne
 * prétend rien d'autre que ce qu'elles disent. Fin et à moitié effacé, pour rester sous
 * la tendance sans lui disputer l'œil ([D158][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun DrawScope.drawMeasureLine(scale: ChartScale, points: List<TrendPoint>, color: Color) {
    points.zipWithNext { previous, next ->
        drawLine(
            color = color.copy(alpha = MEASURE_LINE_ALPHA),
            start = Offset(size.width * scale.xOf(previous.date), height(scale.yOf(previous.weightKg))),
            end = Offset(size.width * scale.xOf(next.date), height(scale.yOf(next.weightKg))),
            strokeWidth = MeasureLineWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Les mesures, discrètes : des points, posés sur leur trait. */
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
