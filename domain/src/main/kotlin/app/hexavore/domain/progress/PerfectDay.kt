package app.hexavore.domain.progress

import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroGoalKind
import app.hexavore.domain.nutrition.MacroTotals

/**
 * Ce qu'une journée doit tenir pour être **parfaite**.
 *
 * ### Pourquoi une fourchette, et pourquoi elle penche
 *
 * Un objectif ne se touche pas au gramme près : exiger l'exactitude rendrait la série
 * parfaite inatteignable, donc décorative. Il faut une fourchette, et **elle n'est pas
 * symétrique** — parce que se tromper dans un sens ne coûte pas ce que coûte se
 * tromper dans l'autre.
 *
 * En **perte de poids**, manger moins que prévu ne compromet rien ; manger plus efface
 * le déficit qui fait tout le travail. La fourchette est donc large en dessous et
 * serrée au-dessus. En **prise de masse**, c'est l'inverse, exactement : le surplus est
 * ce qui construit, et le manquer est ce qui coûte. En **maintien**, aucune direction
 * n'est privilégiée, et rester sur place demande justement de ne dériver ni d'un côté
 * ni de l'autre : la fourchette est serrée des deux.
 *
 * ### Ce qui se juge, et ce qui ne se juge pas
 *
 * **Deux compteurs sur six** ([JUDGED]) : les calories et les protéines. Ce sont ceux
 * qui décident du résultat — le budget, et la masse maigre qu'il préserve. Juger les
 * six rendrait la série parfaite si rare qu'elle ne récompenserait plus rien : les
 * fibres, en particulier, se tiennent mal sans y penser toute la journée. Les quatre
 * autres restent lisibles sur l'hexagone, où ils n'ont jamais cessé d'être.
 *
 * **Une limite ne se rate que par le haut.** Glucides, sucres et lipides sont des
 * limites ([MacroGoalKind.LIMIT]) : rester dessous n'est pas un défaut, et exiger d'en
 * manger au moins les trois quarts serait absurde. Le plancher ne s'applique donc
 * qu'aux cibles. Aucune limite n'entre aujourd'hui dans [JUDGED] — la règle est écrite
 * entière parce qu'elle est une règle sur une macro, pas sur la liste du jour.
 *
 * ### Une journée sans objectif n'est jamais parfaite
 *
 * Elle n'a rien à quoi se comparer ([D04][decisions]), et « parfaite par défaut »
 * donnerait une série à qui n'a pas encore répondu aux cinq questions. Elle n'est pas
 * non plus ratée : elle **casse** la série parfaite sans compter comme un échec, parce
 * qu'une série est une suite ininterrompue et que celle-ci l'a été.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/03-nutrition-calculs.md
 */
object PerfectDay {
    /**
     * Les compteurs qui décident d'une journée parfaite.
     *
     * Une liste et non « toutes les macros » : c'est un choix de politique, et il se
     * lit en un endroit plutôt que de se déduire d'une boucle.
     */
    val JUDGED: Set<Macro> = setOf(Macro.CALORIES, Macro.PROTEIN)

    /**
     * Cette journée est-elle parfaite ?
     *
     * @param goal l'objectif **qui valait ce jour-là**, ou `null` s'il n'y en avait
     *   aucun — auquel cas la journée n'est pas parfaite, faute de repère.
     */
    fun of(totals: MacroTotals, goal: DailyGoal?, strategy: GoalStrategy): Boolean {
        if (goal == null) return false
        return JUDGED.all { macro -> within(totals[macro].value, goal[macro], macro, strategy) }
    }

    /**
     * Cette valeur est-elle dans la fourchette de cette macro ?
     *
     * **Un objectif à zéro ne se juge pas** : diviser par lui n'aurait pas de sens, et
     * un compteur qu'on ne vise pas ne peut pas être manqué.
     */
    fun within(value: Double, target: Double, macro: Macro, strategy: GoalStrategy): Boolean {
        if (target <= 0.0) return true

        val tolerance = Tolerance.of(strategy)
        val underCeiling = value <= target * (1 + tolerance.over)
        // Une limite n'a pas de plancher : 20 g de sucres sur une limite a 50 g reste
        // une bonne journee, et en exiger 37 serait demander d'en manger davantage.
        val overFloor = macro.goal == MacroGoalKind.LIMIT || value >= target * (1 - tolerance.under)

        return underCeiling && overFloor
    }
}

/**
 * De combien on a le droit de s'écarter, et de quel côté.
 *
 * Les deux nombres sont des fractions de l'objectif, jamais des grammes : une marge en
 * grammes serait large sur les protéines et invisible sur les calories.
 */
data class Tolerance(
    /** Ce qu'on a le droit d'avoir en moins, pour une **cible**. */
    val under: Double,
    /** Ce qu'on a le droit d'avoir en plus. */
    val over: Double,
) {
    companion object {
        /**
         * La fourchette d'une stratégie.
         *
         * `when` sans branche `else` : une quatrième stratégie ne compilera pas tant
         * qu'on n'aura pas dit de quel côté elle penche.
         */
        fun of(strategy: GoalStrategy): Tolerance = when (strategy) {
            GoalStrategy.LOSE -> Tolerance(under = WIDE, over = TIGHT)
            GoalStrategy.MAINTAIN -> Tolerance(under = TIGHT, over = TIGHT)
            GoalStrategy.GAIN -> Tolerance(under = TIGHT, over = WIDE)
        }

        /** Le côté qui ne coûte rien : un quart de l'objectif. */
        private const val WIDE = 0.25

        /** Le côté qui coûte le résultat : un dixième. */
        private const val TIGHT = 0.10
    }
}
