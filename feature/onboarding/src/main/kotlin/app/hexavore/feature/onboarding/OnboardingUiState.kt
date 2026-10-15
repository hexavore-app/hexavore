package app.hexavore.feature.onboarding

import androidx.compose.runtime.Immutable
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.goal.TargetProblem
import app.hexavore.domain.goal.checkTarget
import app.hexavore.domain.profile.BodyMeasure
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.profile.implausibleMeasure
import app.hexavore.domain.usecase.GoalPlan
import java.time.LocalDate

/**
 * Les cinq étapes, dans l'ordre.
 *
 * Une énumération plutôt qu'un entier : « étape 3 » ne dit rien à la lecture, et un
 * décalage d'indice se corrigerait en silence sur le mauvais écran.
 */
enum class OnboardingStep {
    /** Nom, la figure des six compteurs, et l'avertissement à accepter. */
    WELCOME,

    /** Date de naissance, sexe, taille, poids actuel. */
    ABOUT_YOU,

    /** Cinq niveaux, chacun décrit par un exemple concret. */
    ACTIVITY,

    /** Perdre / Maintenir / Prendre, puis poids cible et échéance. */
    OBJECTIVE,

    /** Les six chiffres calculés, avec une phrase par compteur. */
    RESULT,
    ;

    val index: Int get() = ordinal

    companion object {
        val COUNT = entries.size
    }
}

/**
 * Ce qui manque pour franchir l'étape courante — ou ce qui ne tient pas.
 *
 * Une énumération et non un booléen : un bouton qui refuse sans dire pourquoi est
 * exactement le défaut qu'on corrige. C'est l'écran qui traduit en phrase — `:feature`
 * connaît les ressources, l'état non.
 *
 * [AGE], [HEIGHT], [WEIGHT] et [TARGET] ne disent pas qu'il manque quelque chose, mais
 * qu'une valeur sort de ce qu'un corps peut être ([D160][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
enum class OnboardingBlocker {
    DISCLAIMER,
    IDENTITY,
    AGE,
    HEIGHT,
    WEIGHT,
    ACTIVITY,
    OBJECTIVE,
    TARGET,
}

/**
 * Ce que l'utilisateur a répondu jusqu'ici.
 *
 * **Tout est nullable, et rien n'est pré-rempli d'une valeur plausible.** Un poids par
 * défaut à 70 kg serait accepté par distraction, et l'objectif calculé serait celui de
 * quelqu'un d'autre.
 */
@Immutable
data class OnboardingAnswers(
    val disclaimerAccepted: Boolean = false,
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    /**
     * Le métier, et le sport qui s'y ajoute ([D137][decisions]).
     *
     * Le métier est la seule des deux réponses qui bloque : chacun en a un, même « assis
     * toute la journée ». Les séances valent zéro tant qu'on n'y touche pas, et zéro est
     * une réponse — la plus fréquente.
     *
     * [decisions]: docs/11-decisions.md
     */
    val work: WorkActivity? = null,
    val sessions: WeeklySessions = WeeklySessions.NONE,
    val strategy: GoalStrategy? = null,
    val targetWeightKg: Double? = null,
    val targetDate: LocalDate? = null,
) {
    /** Ce que l'étape « Vous » demande, et sans quoi aucun calcul n'est possible. */
    val identityComplete: Boolean
        get() = birthDate != null && sex != null && heightCm != null && currentWeightKg != null

    /**
     * Le maintien n'a ni poids cible ni échéance : il n'y a rien à atteindre.
     *
     * Les deux autres stratégies les demandent — sans quoi l'écart calorique vaudrait
     * zéro, et « perdre du poids » produirait un objectif de maintien portant une
     * étiquette qui ment.
     */
    val objectiveComplete: Boolean
        get() = when (strategy) {
            null -> false
            GoalStrategy.MAINTAIN -> true
            else -> targetWeightKg != null && targetDate != null
        }

    /**
     * Ce que le poids visé a de faux pour la stratégie choisie, ou `null`.
     *
     * Un poids du mauvais côté, sous le seuil de la maigreur, ou au-delà de ce que ce
     * corps peut peser ([D160][decisions]). Calculé ici plutôt que dans l'écran : le
     * `ViewModel` refuse d'avancer sur la même réponse que celle que le champ affiche.
     *
     * [decisions]: docs/11-decisions.md
     */
    val targetProblem: TargetProblem?
        get() {
            val chosen = strategy
            val height = heightCm
            val current = currentWeightKg
            return if (chosen == null || height == null || current == null) {
                null
            } else {
                checkTarget(chosen, height, current, targetWeightKg)
            }
        }
}

/**
 * L'état de l'écran d'onboarding.
 *
 * [plan] est recalculé à chaque réponse : c'est ce qui permet à l'écran de résultat de
 * suivre sans qu'un second calcul existe quelque part.
 */
@Immutable
data class OnboardingUiState(
    /**
     * La journée de l'horloge, portée par l'état plutôt que relue par l'écran.
     *
     * Les trois pastilles d'échéance et les bornes du sélecteur de date en dépendent.
     * Un `LocalDate.now()` dans une composable serait la seule lecture d'horloge non
     * injectée du projet, et elle rendrait ces écrans invérifiables.
     */
    val today: LocalDate,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val plan: GoalPlan? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * Ce qui manque ici, ou `null` si l'étape est franchissable.
     *
     * **Chaque étape exige ses champs** ([D56][decisions]). [docs/02][parcours]
     * promettait un bouton « Passer » sur les quatre dernières ; il disparaît, parce
     * qu'un objectif calculé sur des valeurs par défaut est l'objectif de quelqu'un
     * d'autre, affiché avec l'autorité d'un chiffre personnel.
     *
     * Une seule règle, lue à deux endroits : le `ViewModel` refuse d'avancer, l'écran
     * s'en sert pour dire quoi. Les deux interrogent cette propriété, donc ils ne
     * peuvent pas diverger.
     *
     * [decisions]: docs/11-decisions.md
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    val blocker: OnboardingBlocker?
        get() = when (step) {
            OnboardingStep.WELCOME -> OnboardingBlocker.DISCLAIMER.takeUnless { answers.disclaimerAccepted }
            OnboardingStep.ABOUT_YOU -> answers.identityBlocker(today)
            OnboardingStep.ACTIVITY -> OnboardingBlocker.ACTIVITY.takeIf { answers.work == null }
            OnboardingStep.OBJECTIVE -> answers.objectiveBlocker()
            OnboardingStep.RESULT -> null
        }

    val canContinue: Boolean get() = blocker == null
}

/**
 * L'étape « Vous » : rien ne doit manquer, puis tout doit tenir.
 *
 * **Dans cet ordre** : une taille absente n'est pas invraisemblable, elle manque — et
 * « renseignez votre taille » dit mieux ce qu'il faut faire qu'une plage de poids
 * jugée sur rien.
 */
private fun OnboardingAnswers.identityBlocker(today: LocalDate): OnboardingBlocker? = if (!identityComplete) {
    OnboardingBlocker.IDENTITY
} else {
    when (implausibleMeasure(birthDate, heightCm, currentWeightKg, today)) {
        BodyMeasure.BIRTH_DATE -> OnboardingBlocker.AGE
        BodyMeasure.HEIGHT -> OnboardingBlocker.HEIGHT
        BodyMeasure.WEIGHT -> OnboardingBlocker.WEIGHT
        null -> null
    }
}

/**
 * L'étape « Objectif » : le poids visé se juge **avant** ce qui manque.
 *
 * Quelqu'un déjà au seuil de la maigreur qui choisit « Perdre » n'a pas de poids visé à
 * compléter : lui demander « un poids cible et une échéance » l'enverrait remplir un
 * champ qui ne pourra jamais aboutir.
 */
private fun OnboardingAnswers.objectiveBlocker(): OnboardingBlocker? = when {
    targetProblem != null -> OnboardingBlocker.TARGET
    !objectiveComplete -> OnboardingBlocker.OBJECTIVE
    else -> null
}
