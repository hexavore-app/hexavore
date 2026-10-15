package app.hexavore.domain.goal

import app.hexavore.domain.profile.BodyBounds

/**
 * Ce qui ne va pas dans un poids visé : son sens, ou ce qu'un corps peut viser.
 *
 * **Chaque cas porte de quoi se dire en chiffres** ([D160][decisions]). « Visez au moins
 * 57 kg » se corrige ; « poids cible invalide » ne dit pas dans quel sens aller.
 *
 * [decisions]: docs/11-decisions.md
 */
sealed interface TargetProblem {
    /** Perdre, vers un poids qui n'est pas plus bas que l'actuel. */
    data class NotBelowCurrent(val currentKg: Double) : TargetProblem

    /** Prendre, vers un poids qui n'est pas plus haut que l'actuel. */
    data class NotAboveCurrent(val currentKg: Double) : TargetProblem

    /** Une perte qui mènerait sous un IMC de 18,5 : la maigreur. */
    data class BelowLean(val heightCm: Double, val leanKg: Double) : TargetProblem

    /** Une prise au-delà de ce que ce corps peut peser. */
    data class AboveRange(val heightCm: Double, val maxKg: Double) : TargetProblem

    /**
     * Déjà au seuil de la maigreur, ou dessous : il n'y a rien à perdre.
     *
     * **Indépendant du poids tapé** : c'est la stratégie elle-même qui ne tient pas, et
     * l'écran le dit dès qu'on la choisit plutôt qu'après une saisie qui ne pouvait pas
     * aboutir.
     */
    data class NoRoomToLose(val heightCm: Double, val leanKg: Double) : TargetProblem
}

/**
 * Ce que ce poids visé a de faux pour cette stratégie, ou `null` s'il tient.
 *
 * ### Le poids suit le sens de la stratégie
 *
 * Rien ne le vérifiait. Perdre vers un poids plus haut calculait une **prise**, sous
 * l'étiquette « perdre », et l'aperçu annonçait son rythme sans signe : 0,3 kg par
 * semaine, dans le mauvais sens. Le calcul ne le fait plus de son côté — un écart du
 * mauvais côté y vaut zéro —, et c'est ici que l'écran apprend pourquoi.
 *
 * ### Une perte s'arrête au seuil de la maigreur
 *
 * Un IMC de 18,5, et c'est un refus ([D160][decisions]) : une application qui calcule
 * des déficits n'aide personne à descendre sous ce seuil. Quelqu'un qui y est déjà ne se
 * voit plus proposer de perte du tout.
 *
 * ### Ce qui n'est pas jugé ici
 *
 * Un poids visé absent : il manque, ce que l'écran dit déjà. Une taille ou un poids
 * actuel invraisemblables : c'est eux qu'il faut corriger, et un second message sur le
 * poids visé ne ferait que brouiller le premier.
 *
 * [decisions]: docs/11-decisions.md
 */
fun checkTarget(strategy: GoalStrategy, heightCm: Double, currentKg: Double, targetKg: Double?): TargetProblem? {
    if (heightCm !in BodyBounds.HEIGHTS_CM || currentKg !in BodyBounds.weightsFor(heightCm)) return null

    return when (strategy) {
        GoalStrategy.MAINTAIN -> null
        GoalStrategy.LOSE -> lossProblem(heightCm, currentKg, targetKg)
        GoalStrategy.GAIN -> gainProblem(heightCm, currentKg, targetKg)
    }
}

private fun lossProblem(heightCm: Double, currentKg: Double, targetKg: Double?): TargetProblem? {
    val leanKg = BodyBounds.leanWeightFor(heightCm)
    return when {
        currentKg <= leanKg -> TargetProblem.NoRoomToLose(heightCm, leanKg)
        targetKg == null -> null
        targetKg >= currentKg -> TargetProblem.NotBelowCurrent(currentKg)
        targetKg < leanKg -> TargetProblem.BelowLean(heightCm, leanKg)
        else -> null
    }
}

private fun gainProblem(heightCm: Double, currentKg: Double, targetKg: Double?): TargetProblem? {
    val heaviestKg = BodyBounds.weightsFor(heightCm).endInclusive
    return when {
        targetKg == null -> null
        targetKg <= currentKg -> TargetProblem.NotAboveCurrent(currentKg)
        targetKg > heaviestKg -> TargetProblem.AboveRange(heightCm, heaviestKg)
        else -> null
    }
}
