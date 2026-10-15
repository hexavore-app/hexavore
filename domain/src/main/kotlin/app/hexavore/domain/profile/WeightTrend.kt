package app.hexavore.domain.profile

import app.hexavore.domain.goal.Goal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** Les sept jours de la fenêtre de lissage. */
const val TREND_WINDOW_DAYS = 7

/**
 * Le nombre de pesées qu'il faut dans une fenêtre pour qu'elle dise quelque chose.
 *
 * [docs/03][calculs] pose ce plancher pour la pente : « il faut au moins 3 pesées dans
 * chaque fenêtre pour que la pente soit calculée. Sinon, pas de suggestion — un silence
 * vaut mieux qu'un conseil fondé sur une seule pesée. »
 *
 * **Le même plancher vaut pour la courbe**, et c'est le même argument : une moyenne
 * mobile sur sept jours calculée à partir d'une seule pesée *est* cette pesée. La
 * tracer en évidence, à côté des points bruts qu'elle est censée lisser, affirmerait un
 * lissage qui n'a pas eu lieu. Sous trois pesées, il n'y a pas de tendance — et l'écran
 * le dit plutôt que d'en dessiner une.
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
const val MIN_WEIGHINGS_PER_WINDOW = 3

/**
 * La moyenne mobile au jour dit, sur la fenêtre de sept jours **qui finit ce jour-là**.
 *
 * Une fenêtre qui déborderait sur les jours suivants ferait dépendre le passé de
 * l'avenir : le point d'hier changerait encore demain, et la courbe se réécrirait
 * derrière le doigt.
 *
 * `null` sous [MIN_WEIGHINGS_PER_WINDOW] pesées dans la fenêtre.
 */
fun List<WeightEntry>.movingAverageOn(day: LocalDate): Double? {
    val floor = day.minusDays(TREND_WINDOW_DAYS - 1L)
    val window = filter { !it.date.isBefore(floor) && !it.date.isAfter(day) }
    return if (window.size < MIN_WEIGHINGS_PER_WINDOW) null else window.sumOf { it.weightKg } / window.size
}

/**
 * Ce qu'un jour écoulé fait parcourir à la tendance vers la nouvelle pesée : un dixième.
 *
 * **La valeur de *The Hacker's Diet***, celle que reprennent les applications de pesée
 * qui l'ont suivie : assez lente pour effacer l'eau et le sel d'un jour à l'autre,
 * assez rapide pour qu'une vraie perte se voie en une semaine. Elle se lit en jours et
 * non en pesées, et c'est tout son intérêt avec des pesées espacées ([D159][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
const val TREND_DAILY_SMOOTHING = 0.10

/**
 * La tendance au jour de chaque pesée : une moyenne qui oublie **à proportion du temps
 * écoulé**.
 *
 * ### Pourquoi elle remplace la moyenne sur sept jours dans la courbe
 *
 * La moyenne mobile ne se calcule que là où la fenêtre porte trois pesées. Avec des
 * pesées espacées, elle n'existait que par fragments : un bout de trait isolé au milieu
 * de la courbe, « comme un cheveu sur la soupe », et rien ailleurs ([D159][decisions]).
 *
 * Celle-ci existe dès la première pesée, et ne s'interrompt jamais. Chaque pesée
 * rapproche la tendance d'elle-même d'une part qui grandit avec le temps écoulé depuis
 * la précédente — `1 − 0,9^jours` :
 *
 * - **pesées quotidiennes** : un dixième du chemin par jour, et le bruit s'efface ;
 * - **une pesée par semaine** : un peu plus de la moitié du chemin ;
 * - **après trois semaines de silence** : presque tout le chemin, parce que l'ancienne
 *   tendance ne dit plus rien du poids d'aujourd'hui.
 *
 * ### Ce qu'elle ne remplace pas
 *
 * **La pente de l'adaptation hebdomadaire** garde sa règle — deux moyennes de sept jours,
 * trois pesées chacune ([weeklySlopeOn]) — parce qu'un conseil sur l'objectif doit se
 * taire quand les données sont maigres, là où une courbe doit montrer ce qu'il y a.
 *
 * @return une valeur par date de pesée. Une date pesée deux fois garde la dernière.
 *
 * [decisions]: docs/11-decisions.md
 */
fun List<WeightEntry>.trendByDate(): Map<LocalDate, Double> {
    val trends = LinkedHashMap<LocalDate, Double>()
    var previous: WeightEntry? = null
    var trend = 0.0

    sortedBy { it.date }.forEach { entry ->
        trend = previous?.let { before ->
            // Au moins un jour : deux pesees le meme jour ne doivent pas laisser la
            // seconde sans effet.
            val days = ChronoUnit.DAYS.between(before.date, entry.date).coerceAtLeast(1)
            val step = 1.0 - (1.0 - TREND_DAILY_SMOOTHING).pow(days.toDouble())
            trend + step * (entry.weightKg - trend)
        } ?: entry.weightKg
        trends[entry.date] = trend
        previous = entry
    }
    return trends
}

/**
 * La pente en kilogrammes par semaine, au jour dit.
 *
 * **Deux moyennes espacées de sept jours**, et non une régression sur la série : c'est
 * ce que [docs/03][calculs] prescrit, et c'est ce qui se raconte en une phrase à
 * quelqu'un qui demande d'où sort le chiffre.
 *
 * `null` dès que l'une des deux fenêtres est trop pauvre. Les deux conditions comptent :
 * une pente calculée contre une moyenne d'il y a sept jours qu'on ne connaît pas serait
 * une pente contre rien.
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
fun List<WeightEntry>.weeklySlopeOn(day: LocalDate): Double? {
    val now = movingAverageOn(day)
    val before = movingAverageOn(day.minusDays(TREND_WINDOW_DAYS.toLong()))
    return if (now == null || before == null) null else now - before
}

/**
 * La trajectoire annoncée : d'où l'on est parti, où l'on a dit qu'on allait.
 *
 * Elle se trace en pointillés sous la courbe, et sa pente est celle à laquelle
 * l'adaptation hebdomadaire compare la pente réelle.
 */
data class WeightAim(val from: LocalDate, val fromKg: Double, val to: LocalDate, val toKg: Double) {
    /**
     * Le rythme annoncé, en kilogrammes par semaine. Négatif pour une perte.
     *
     * **Il ne bouge pas quand on prend du retard.** Le recalculer depuis aujourd'hui —
     * ce qu'il reste à perdre divisé par ce qu'il reste de temps — durcirait la cible à
     * chaque jour manqué, donc grossirait la correction proposée, qui ferait rater
     * davantage : c'est la boucle qui fait osciller un objectif et perdre confiance.
     * Ce que la pente décrit est le cap annoncé, et un cap ne se déplace pas tout seul.
     */
    val weeklySlope: Double
        get() = (toKg - fromKg) / ChronoUnit.DAYS.between(from, to) * TREND_WINDOW_DAYS
}

/**
 * La trajectoire que cet objectif annonce, éclairée par le journal.
 *
 * Le poids de départ est **la dernière pesée connue à la date de début de l'objectif**,
 * et non le poids d'aujourd'hui : la trajectoire part d'où l'on était quand on a fixé
 * le cap. Elle reste donc immobile pendant qu'on avance dessus, ce qui est tout
 * l'intérêt d'un repère.
 *
 * `null` quand l'objectif ne vise aucun poids, quand son échéance ne suit pas son
 * début, ou quand aucune pesée n'est antérieure à lui — un cap tracé depuis un point
 * qu'on ne connaît pas ne serait qu'une droite posée sur le graphique.
 */
fun Goal.declaredAim(weights: List<WeightEntry>): WeightAim? {
    val toKg = targetWeightKg
    val to = targetDate?.takeIf { it.isAfter(startedAt) }
    val fromKg = weights.weightKnownOn(startedAt)
    return if (toKg == null || to == null || fromKg == null) {
        null
    } else {
        WeightAim(from = startedAt, fromKg = fromKg, to = to, toKg = toKg)
    }
}

/**
 * Le poids connu à cette date : la dernière pesée qui ne lui est pas postérieure.
 *
 * `null` quand le journal ne commence qu'après — on ne devine pas vers l'arrière.
 */
private fun List<WeightEntry>.weightKnownOn(date: LocalDate): Double? =
    filter { !it.date.isAfter(date) }.maxByOrNull { it.date }?.weightKg
