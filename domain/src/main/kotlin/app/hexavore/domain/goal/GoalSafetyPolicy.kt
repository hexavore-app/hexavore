package app.hexavore.domain.goal

import app.hexavore.domain.profile.Sex
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * L'équivalent énergétique d'un kilo de tissu adipeux.
 *
 * ≈ 7 000 kcal de lipides purs, plus la fraction d'eau et de protéines du tissu.
 * C'est une approximation **linéaire**, qui perd en validité au-delà de six mois —
 * d'où l'adaptation hebdomadaire ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
const val KCAL_PER_KILOGRAM = 7_700.0

private const val DAYS_PER_WEEK = 7.0

/** Ce qu'un garde-fou a retenu, et lequel a mordu. */
enum class GoalGuard {
    /** Perte ≤ 1 % du poids par semaine, prise ≤ 0,5 %. */
    SPEED,

    /** |Δkcal| ≤ 25 % du TDEE. */
    DEVIATION,

    /**
     * 1 200 kcal (femme) · 1 500 (homme) · 1 350 (non précisé).
     *
     * Il retient un déficit, et ne crée jamais de surplus ([D160][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    FLOOR,

    /** Prise ≤ +20 % du TDEE. */
    GAIN_CEILING,
}

/**
 * Le résultat d'un passage par les garde-fous.
 *
 * [reachableOn] n'est renseignée que si un garde-fou a mordu **et** qu'une date était
 * visée : c'est la date à laquelle le poids cible est atteint au rythme retenu. Elle
 * est ce que l'écran propose — « à un rythme sûr, ce serait atteint vers le 14 mars »,
 * avec un bouton *Utiliser cette date*.
 *
 * [withholdingFloorKcal] est le plancher qui a retenu **tout** le déficit, quand la
 * dépense estimée était déjà dessous ; `null` le reste du temps. Il n'y a alors pas de
 * date à proposer — aucun rythme n'atteint le poids visé sans passer sous le plancher —,
 * et l'écran doit dire pourquoi la perte demandée ne produit rien ([D160][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
data class SafeEnergyBudget(
    val dailyDeltaKcal: Double,
    val guardsApplied: Set<GoalGuard>,
    val reachableOn: LocalDate?,
    val withholdingFloorKcal: Double? = null,
) {
    val capped: Boolean get() = guardsApplied.isNotEmpty()
}

/**
 * Les quatre garde-fous, appliqués dans l'ordre.
 *
 * **Chacun peut réduire l'ambition, jamais l'augmenter.** C'est la propriété qui rend
 * l'ordre sans importance pour le résultat, et elle est éprouvée pour elle-même : un
 * garde-fou qui pourrait relever un déficit serait un garde-fou qui l'aggrave dans
 * certains cas, et personne ne le verrait.
 *
 * **L'application ne refuse jamais.** Quand un garde-fou mord, elle recalcule la date
 * atteignable et la propose. L'interdiction pure et simple pousse les gens à mentir
 * sur leur poids pour contourner l'outil ; expliquer et proposer fonctionne mieux
 * ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
object GoalSafetyPolicy {
    /**
     * Le déficit ou surplus quotidien, ramené à ce qui est sûr.
     *
     * @param rawDeltaKcal ce que l'échéance demanderait, sans limite.
     * @param tdee la dépense totale, qui sert de référence aux bornes relatives.
     * @param currentWeightKg le poids **actuel** : la vitesse est un pourcentage de lui.
     * @param targetWeightKg le poids visé, pour recalculer la date atteignable.
     */
    @Suppress("LongParameterList")
    fun apply(
        rawDeltaKcal: Double,
        tdee: Double,
        currentWeightKg: Double,
        targetWeightKg: Double?,
        sex: Sex,
        from: LocalDate,
    ): SafeEnergyBudget {
        val guards = mutableSetOf<GoalGuard>()
        var delta = rawDeltaKcal

        delta = delta.cappedBySpeed(currentWeightKg, guards)
        delta = delta.cappedByDeviation(tdee, guards)
        delta = delta.cappedByGainCeiling(tdee, guards)
        delta = delta.cappedByFloor(tdee, sex, guards)

        return SafeEnergyBudget(
            dailyDeltaKcal = delta,
            guardsApplied = guards,
            reachableOn = reachableDate(guards, delta, currentWeightKg, targetWeightKg, from),
            // Le plancher a mordu et il ne reste rien : c'est lui, et lui seul, qui a
            // ramene le deficit a zero.
            withholdingFloorKcal = sex.kcalFloor.takeIf { GoalGuard.FLOOR in guards && delta == 0.0 },
        )
    }

    /**
     * Vitesse maximale : 1 % du poids par semaine en perte, 0,5 % en prise.
     *
     * Au-delà, la part de masse maigre perdue — ou de masse grasse prise — grimpe
     * fortement. La borne est asymétrique parce que les deux phénomènes ne le sont pas.
     */
    private fun Double.cappedBySpeed(currentWeightKg: Double, guards: MutableSet<GoalGuard>): Double {
        val maxKgPerWeek = currentWeightKg * if (this < 0) MAX_LOSS_RATIO else MAX_GAIN_RATIO
        val limit = maxKgPerWeek * KCAL_PER_KILOGRAM / DAYS_PER_WEEK
        return coerceInto(-limit, limit, GoalGuard.SPEED, guards)
    }

    /** Écart maximal au TDEE : 25 %, dans les deux sens. */
    private fun Double.cappedByDeviation(tdee: Double, guards: MutableSet<GoalGuard>): Double {
        val limit = tdee * MAX_DEVIATION_RATIO
        return coerceInto(-limit, limit, GoalGuard.DEVIATION, guards)
    }

    /** Plafond de prise : +20 % du TDEE. Au-delà, le surplus part majoritairement en gras. */
    private fun Double.cappedByGainCeiling(tdee: Double, guards: MutableSet<GoalGuard>): Double =
        coerceInto(Double.NEGATIVE_INFINITY, tdee * MAX_GAIN_CEILING_RATIO, GoalGuard.GAIN_CEILING, guards)

    /**
     * Plancher absolu, en calories et non en écart — **pour un déficit, et pour lui seul**.
     *
     * En dessous, couvrir les besoins en micronutriments devient irréaliste sans
     * supplémentation. Il s'exprime sur l'objectif final, donc la borne sur l'écart
     * dépend du TDEE.
     *
     * **Il retient un déficit, il ne crée jamais de surplus** ([D160][decisions]). Quand
     * la dépense estimée est déjà sous le plancher — un petit gabarit, un grand âge, ou
     * une taille de 1 cm —, la borne tombe à zéro. Elle remontait jusqu'au plancher, et
     * faisait d'une perte une **prise**, sous l'étiquette « perdre », dépassant au passage
     * les bornes que les garde-fous précédents venaient de poser :
     * « Utiliser cette date » ne levait alors jamais l'alerte, puisque aucune date ne
     * corrigeait un écart qui ne venait pas d'elle. Un maintien devenait une prise de la
     * même façon. C'est la propriété commune aux quatre — réduire l'ambition, jamais
     * l'augmenter — qui ne valait jusqu'ici que pour une dépense au-dessus du plancher.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun Double.cappedByFloor(tdee: Double, sex: Sex, guards: MutableSet<GoalGuard>): Double =
        coerceInto(minOf(sex.kcalFloor - tdee, 0.0), Double.POSITIVE_INFINITY, GoalGuard.FLOOR, guards)

    /**
     * Borne, et note **qui** a borné.
     *
     * Le drapeau est posé sur la comparaison et non sur une différence de valeur : un
     * écart déjà nul ne doit pas se déclarer borné, et un `!=` sur des flottants
     * signalerait un garde-fou qui n'a rien fait.
     */
    private fun Double.coerceInto(
        minimum: Double,
        maximum: Double,
        guard: GoalGuard,
        guards: MutableSet<GoalGuard>,
    ): Double = when {
        this < minimum -> minimum.also { guards += guard }
        this > maximum -> maximum.also { guards += guard }
        else -> this
    }

    /**
     * La date à laquelle le poids cible est atteint au rythme retenu.
     *
     * `null` quand rien n'a été borné — l'échéance demandée tient — ou quand il n'y a
     * pas de poids cible, cas du maintien. Arrondie au jour **supérieur** : proposer
     * une date d'un jour trop tôt reviendrait à proposer une échéance qu'on vient
     * précisément de déclarer intenable.
     */
    private fun reachableDate(
        guards: Set<GoalGuard>,
        delta: Double,
        currentWeightKg: Double,
        targetWeightKg: Double?,
        from: LocalDate,
    ): LocalDate? {
        val remainingKg = targetWeightKg?.let { abs(it - currentWeightKg) } ?: 0.0
        val computable = guards.isNotEmpty() && remainingKg > 0.0 && delta != 0.0

        return if (computable) {
            from.plusDays(ceil(remainingKg * KCAL_PER_KILOGRAM / abs(delta)).roundToLong())
        } else {
            null
        }
    }

    private val Sex.kcalFloor: Double
        get() = when (this) {
            Sex.MALE -> MALE_FLOOR
            Sex.FEMALE -> FEMALE_FLOOR
            Sex.UNSPECIFIED -> (MALE_FLOOR + FEMALE_FLOOR) / 2
        }

    private const val MAX_LOSS_RATIO = 0.01
    private const val MAX_GAIN_RATIO = 0.005
    private const val MAX_DEVIATION_RATIO = 0.25
    private const val MAX_GAIN_CEILING_RATIO = 0.20

    private const val MALE_FLOOR = 1_500.0
    private const val FEMALE_FLOOR = 1_200.0
}
