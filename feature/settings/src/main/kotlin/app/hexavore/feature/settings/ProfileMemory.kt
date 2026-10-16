package app.hexavore.feature.settings

import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Le profil en cours de modification, sous une forme qu'Android range avec l'écran
 * ([D166][decisions]).
 *
 * **Seulement en modification** : en consultation, l'écran relit la base, qui dit déjà
 * tout. Ce qui se perdait, c'étaient les corrections pas encore enregistrées.
 *
 * [decisions]: docs/11-decisions.md
 */
@Serializable
internal data class ProfileMemory(
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
    val manual: Boolean = false,
    /** Une valeur nulle est un champ vidé, et elle se garde comme telle. */
    val macros: Map<String, Double?> = emptyMap(),
) {
    fun toForm() = ProfileForm(
        birthDate = birthDate?.let(LocalDate::ofEpochDay),
        sex = sex?.let(Sex::valueOf),
        heightCm = heightCm,
        currentWeightKg = currentWeightKg,
        work = work?.let(WorkActivity::valueOf),
        sessions = WeeklySessions(sessions),
        strategy = strategy?.let(GoalStrategy::valueOf),
        targetWeightKg = targetWeightKg,
        targetDate = targetDate?.let(LocalDate::ofEpochDay),
        manual = manual,
        macros = macros.mapKeys { (macro, _) -> Macro.valueOf(macro) },
    )

    companion object {
        fun of(form: ProfileForm) = ProfileMemory(
            birthDate = form.birthDate?.toEpochDay(),
            sex = form.sex?.name,
            heightCm = form.heightCm,
            currentWeightKg = form.currentWeightKg,
            work = form.work?.name,
            sessions = form.sessions.count,
            strategy = form.strategy?.name,
            targetWeightKg = form.targetWeightKg,
            targetDate = form.targetDate?.toEpochDay(),
            manual = form.manual,
            macros = form.macros.mapKeys { (macro, _) -> macro.name },
        )
    }
}
