package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DaySummary
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.DishSummary
import app.hexavore.domain.diary.titles
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.nutrition.MacroTotals
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Le résumé d'une journée : ses plats, ce que chacun a apporté, et l'objectif du jour.
 *
 * L'horloge est injectée, et c'est tout l'intérêt : la journée par défaut est celle
 * de [Clock], jamais `LocalDate.now()`. Sans cela, la règle « une entrée de 23 h 59
 * appartient au bon jour » ne se testerait qu'en attendant minuit.
 *
 * **L'objectif est celui qui valait ce jour-là**, et non celui d'aujourd'hui
 * ([D04][decisions]). C'est la raison d'être des objectifs versionnés : sans elle, le
 * jour où l'on passe de 2 500 à 2 200 kcal, tout le mois écoulé se repeindrait en
 * dépassement. Il vaut `null` pour une journée antérieure au premier objectif — une
 * journée notée avant qu'un objectif existe n'a rien à quoi se comparer.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/06-architecture.md
 */
class GetDaySummary(private val diary: DiaryRepository, private val goals: Goals, private val clock: Clock) {
    /**
     * @param date journée demandée. Par défaut celle de l'horloge, évaluée à chaque
     *   appel : un écran resté ouvert pendant la nuit ne doit pas continuer
     *   d'afficher la veille.
     */
    operator fun invoke(date: LocalDate = clock.today()): Flow<DaySummary> =
        combine(diary.observeDay(date), goals.observeGoalOn(date)) { dishes, goal ->
            val zone = clock.zone()
            // Les titres se calculent ici parce qu'ils se calculent **ensemble** : le
            // rang d'un deuxieme dejeuner est une propriete de la journee, pas du plat.
            val titres = dishes.titles(zone)

            DaySummary(
                date = date,
                zone = zone,
                goal = goal?.daily,
                // Recalculé depuis les lignes et non par somme des sous-totaux :
                // additionner des totaux ferait perdre la trace des valeurs
                // inconnues, qui est justement ce qu'on cherche à conserver.
                totals = MacroTotals.of(dishes.flatMap { it.entries }.map { it.macros }),
                dishes =
                dishes.mapIndexed { index, dish ->
                    DishSummary(
                        dish = dish,
                        totals = MacroTotals.of(dish.entries.map { it.macros }),
                        title = titres[index],
                    )
                },
            )
        }
}
