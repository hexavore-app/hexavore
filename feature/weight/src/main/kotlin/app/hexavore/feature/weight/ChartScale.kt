package app.hexavore.feature.weight

import app.hexavore.domain.profile.WeightAim
import app.hexavore.domain.usecase.WeightTrend
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * L'échelle du graphique : où tombe une date, où tombe un poids.
 *
 * **Séparée du dessin, et c'est tout l'intérêt.** Un `Canvas` ne se vérifie qu'à
 * l'œil ; les fractions, elles, s'affirment. Ce qui se teste ici est ce qui fait la
 * différence entre une courbe juste et une courbe jolie : deux mesures au même poids
 * doivent tomber à la même hauteur, une mesure du milieu au milieu, et la trajectoire
 * annoncée doit être lue avec la **même** règle que les points — sans quoi les deux
 * tracés se croiseraient là où ils ne se croisent pas.
 *
 * Les deux fonctions rendent des fractions de `[0, 1]`, [y][yOf] compté depuis le
 * **bas** : le dessin les retourne lui-même, parce que l'inversion est une affaire
 * d'écran et non d'échelle.
 */
internal class ChartScale private constructor(
    private val from: LocalDate,
    private val to: LocalDate,
    private val low: Double,
    private val high: Double,
) {
    /** Où tombe cette date, de la gauche (0) à la droite (1). Débordements compris. */
    fun xOf(date: LocalDate): Float =
        (ChronoUnit.DAYS.between(from, date).toDouble() / ChronoUnit.DAYS.between(from, to)).toFloat()

    /** Où tombe ce poids, du bas (0) au haut (1). */
    fun yOf(weightKg: Double): Float = ((weightKg - low) / (high - low)).toFloat()

    /** Le poids le plus bas et le plus haut de l'échelle, tels que l'axe les affiche. */
    val bounds: ClosedFloatingPointRange<Double> get() = low..high

    /** La plage de dates couverte. */
    val span: ClosedRange<LocalDate> get() = from..to

    companion object {
        /**
         * La respiration au-dessus et au-dessous des mesures, en fraction de leur
         * amplitude. Sans elle, la mesure la plus haute touche le bord et se confond
         * avec le cadre.
         */
        private const val PADDING_FRACTION = 0.12

        /**
         * L'amplitude minimale de l'axe vertical, en kilogrammes.
         *
         * Trois pesées à 300 grammes d'écart rempliraient sinon toute la hauteur, et
         * une variation d'hydratation se lirait comme un effondrement — exactement ce
         * que la moyenne mobile sert à ne pas montrer.
         */
        private const val MIN_RANGE_KG = 2.0

        /**
         * L'échelle d'une courbe, ou `null` si elle n'a rien à montrer.
         *
         * **L'axe des dates s'arrête aux mesures**, et ne va pas jusqu'à l'échéance :
         * deux semaines de pesées devant six mois d'horizon seraient tassées sur un
         * douzième de la largeur, et la courbe qu'on est venu lire deviendrait
         * illisible pour ménager une date qui ne bougera pas. La trajectoire annoncée
         * est une droite : la dessiner sur la fenêtre des mesures dit la même chose,
         * là où on regarde.
         *
         * `null` sous deux pesées — un point unique n'a ni pente ni durée, et l'axe
         * horizontal serait de largeur nulle.
         */
        fun of(trend: WeightTrend): ChartScale? {
            val span = trend.span() ?: return null
            val shown = trend.valuesOver(span)
            val low = shown.min()
            val high = shown.max()
            // La plus large des deux respirations : celle qui aere une grande
            // amplitude, et celle qui empeche une petite de remplir l'ecran.
            val padding = maxOf((high - low) * PADDING_FRACTION, (MIN_RANGE_KG - (high - low)) / 2)
            return ChartScale(
                from = span.start,
                to = span.endInclusive,
                low = low - padding,
                high = high + padding,
            )
        }

        /**
         * La fenêtre de dates que la courbe couvre : de la première pesée à la dernière.
         *
         * `null` sous deux jours distincts — l'axe horizontal serait de largeur nulle,
         * et chaque date y tomberait sur une division par zéro.
         */
        private fun WeightTrend.span(): ClosedRange<LocalDate>? {
            val first = points.firstOrNull()?.date ?: return null
            return (first..points.last().date).takeIf { it.start != it.endInclusive }
        }

        /**
         * Tout ce que le graphique doit contenir : les mesures, leur tendance, et les
         * deux bouts de la trajectoire **tels qu'ils tombent dans la fenêtre**.
         *
         * La trajectoire compte : hors des bornes, le pointillé sortirait du cadre, et
         * le retard qu'il montre disparaîtrait le jour où il devient intéressant.
         */
        private fun WeightTrend.valuesOver(span: ClosedRange<LocalDate>): List<Double> =
            points.flatMap { listOf(it.weightKg, it.trendKg) } +
                aim?.let { listOf(it.weightAt(span.start), it.weightAt(span.endInclusive)) }.orEmpty()
    }
}

/**
 * Le poids que la trajectoire annonce pour ce jour-là.
 *
 * Elle est une droite, donc elle se prolonge de part et d'autre de ses deux bornes :
 * c'est ce qui permet de la lire sur la fenêtre des mesures, y compris avant le début
 * de l'objectif ou après son échéance.
 */
internal fun WeightAim.weightAt(date: LocalDate): Double =
    fromKg + weeklySlope * ChronoUnit.DAYS.between(from, date) / DAYS_PER_WEEK

private const val DAYS_PER_WEEK = 7.0
