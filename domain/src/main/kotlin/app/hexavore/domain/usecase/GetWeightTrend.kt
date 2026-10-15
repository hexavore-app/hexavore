package app.hexavore.domain.usecase

import app.hexavore.domain.goal.Goals
import app.hexavore.domain.profile.WeightAim
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.profile.declaredAim
import app.hexavore.domain.profile.trendByDate
import app.hexavore.domain.profile.weeklySlopeOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * La courbe de poids : les pesées, leur tendance, et le cap annoncé.
 *
 * **Deux tracés et non un** ([docs/02][parcours]) : le poids brut varie de deux kilos
 * avec l'hydratation, le sel et le transit, et décourage sans raison. Il reste visible
 * — c'est la mesure, et la cacher reviendrait à décider à la place de l'utilisateur ce
 * qu'il a le droit de voir — mais discret, sous une tendance qui, elle, porte
 * l'information ([D159][decisions]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
class GetWeightTrend(private val weights: WeightLog, private val goals: Goals) {
    operator fun invoke(): Flow<WeightTrend> = combine(weights.observeHistory(), goals.observeCurrent()) { log, goal ->
        val trends = log.trendByDate()
        WeightTrend(
            points = log.map { TrendPoint(it.date, it.weightKg, trends.getValue(it.date)) },
            aim = goal?.declaredAim(log),
        )
    }
}

/**
 * Un point de la courbe : une pesée, et la tendance au même jour.
 *
 * [trendKg] existe pour **chaque** pesée, la première comprise — elle y vaut la pesée
 * elle-même, puisqu'il n'y a rien avant à lisser. C'était une moyenne sur sept jours,
 * absente sous trois pesées : avec des pesées espacées, elle ne laissait qu'un bout de
 * trait isolé au milieu de la courbe ([D159][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
data class TrendPoint(val date: LocalDate, val weightKg: Double, val trendKg: Double)

/**
 * La courbe entière, telle que l'écran la dessine.
 *
 * [points] est vide avant la première pesée — pas d'axe, pas de tracé, un texte qui
 * invite à se peser.
 */
data class WeightTrend(val points: List<TrendPoint>, val aim: WeightAim? = null) {
    /** La dernière pesée connue, celle que l'écran affiche en grand. */
    val latest: WeightEntry?
        get() = points.lastOrNull()?.let { WeightEntry(it.date, it.weightKg) }

    /**
     * La pente réelle au jour dit, en kilogrammes par semaine.
     *
     * `null` tant que les deux fenêtres ne sont pas assez fournies — c'est le silence
     * que [docs/03][calculs] préfère à un conseil fondé sur une seule pesée.
     *
     * [calculs]: docs/03-nutrition-calculs.md
     */
    fun weeklySlopeOn(day: LocalDate): Double? = points.map { WeightEntry(it.date, it.weightKg) }.weeklySlopeOn(day)
}
