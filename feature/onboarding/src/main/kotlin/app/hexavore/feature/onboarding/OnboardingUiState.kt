package app.hexavore.feature.onboarding

import androidx.compose.runtime.Immutable
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
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
 * Ce qui manque pour franchir l'étape courante.
 *
 * Une énumération et non un booléen : un bouton qui refuse sans dire pourquoi est
 * exactement le défaut qu'on corrige. C'est l'écran qui traduit en phrase — `:feature`
 * connaît les ressources, l'état non.
 */
enum class OnboardingBlocker {
    DISCLAIMER,
    IDENTITY,
    ACTIVITY,
    OBJECTIVE,
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
        get() = when {
            step == OnboardingStep.WELCOME && !answers.disclaimerAccepted -> OnboardingBlocker.DISCLAIMER
            step == OnboardingStep.ABOUT_YOU && !answers.identityComplete -> OnboardingBlocker.IDENTITY
            step == OnboardingStep.ACTIVITY && answers.work == null -> OnboardingBlocker.ACTIVITY
            step == OnboardingStep.OBJECTIVE && !answers.objectiveComplete -> OnboardingBlocker.OBJECTIVE
            else -> null
        }

    val canContinue: Boolean get() = blocker == null
}
