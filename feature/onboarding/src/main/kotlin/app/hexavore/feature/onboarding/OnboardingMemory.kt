package app.hexavore.feature.onboarding

import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Les réponses et l'étape, sous une forme qu'Android range avec l'écran ([D166][decisions]).
 *
 * L'onboarding est la première chose qu'on fait, souvent en allant chercher sa taille ou
 * son poids dans une autre application : c'est le moment où Android tue le plus volontiers
 * la nôtre, et elle repartait de l'accueil de l'onboarding. Le plan, lui, ne se garde pas :
 * il se recalcule depuis les réponses, comme à chaque réponse.
 *
 * Les énumérations par leur nom, les dates en jours : un nom inconnu rend la mémoire
 * illisible, et l'onboarding repart alors du début, comme avant.
 *
 * [decisions]: docs/11-decisions.md
 */
@Serializable
internal data class OnboardingMemory(
    val step: String,
    val disclaimerAccepted: Boolean = false,
    val birthDate: Long? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val work: String? = null,
    /** Le nombre de séances, entre 0 et 5 : un nombre hors plage rend la mémoire illisible. */
    val sessions: Int = 0,
    val strategy: String? = null,
    val targetWeightKg: Double? = null,
    val targetDate: Long? = null,
) {
    /** L'état d'un onboarding neuf, avec ces réponses et à cette étape. */
    fun applyTo(fresh: OnboardingUiState): OnboardingUiState = fresh.copy(
        step = OnboardingStep.valueOf(step),
        answers = OnboardingAnswers(
            disclaimerAccepted = disclaimerAccepted,
            birthDate = birthDate?.let(LocalDate::ofEpochDay),
            sex = sex?.let(Sex::valueOf),
            heightCm = heightCm,
            currentWeightKg = currentWeightKg,
            work = work?.let(WorkActivity::valueOf),
            sessions = WeeklySessions(sessions),
            strategy = strategy?.let(GoalStrategy::valueOf),
            targetWeightKg = targetWeightKg,
            targetDate = targetDate?.let(LocalDate::ofEpochDay),
        ),
    )

    companion object {
        fun of(state: OnboardingUiState): OnboardingMemory = with(state.answers) {
            OnboardingMemory(
                step = state.step.name,
                disclaimerAccepted = disclaimerAccepted,
                birthDate = birthDate?.toEpochDay(),
                sex = sex?.name,
                heightCm = heightCm,
                currentWeightKg = currentWeightKg,
                work = work?.name,
                sessions = sessions.count,
                strategy = strategy?.name,
                targetWeightKg = targetWeightKg,
                targetDate = targetDate?.toEpochDay(),
            )
        }
    }
}
