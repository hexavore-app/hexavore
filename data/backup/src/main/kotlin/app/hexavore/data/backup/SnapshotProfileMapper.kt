package app.hexavore.data.backup

import app.hexavore.domain.goal.AdjustmentSetup
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import java.time.LocalDate

/**
 * Le profil, les objectifs, et l'état de l'adaptation.
 *
 * Séparé des deux autres mappeurs parce que ce sont trois sujets : ce qui décrit la
 * personne, ce qu'elle a mangé, et ce que son catalogue contient. Ensemble, ils
 * faisaient un fichier de dix-neuf fonctions qu'on ne relit pas.
 */
internal fun UserProfile.toDto() = ProfileDto(
    birthDate = birthDate.toString(),
    sex = sex.name,
    heightCm = heightCm,
    activityLevel = activity.work.name,
    leisureSessions = activity.sessions.count,
    unitSystem = unitSystem.name,
)

internal fun ProfileDto.toDomain() = UserProfile(
    birthDate = LocalDate.parse(birthDate),
    sex = Sex.entries.firstOrNull { it.name == sex } ?: Sex.UNSPECIFIED,
    heightCm = heightCm,
    // Un fichier ecrit avant D137 porte un niveau unique et aucune seance : le
    // domaine sait le traduire, et c'est lui qui le sait -- deux endroits qui
    // porteraient cette regle finiraient par en donner deux versions.
    activity = leisureSessions
        ?.let {
            Activity(
                WorkActivity.entries.firstOrNull { w ->
                    w.name == activityLevel
                } ?: WorkActivity.DESK,
                WeeklySessions(it.coerceIn(0, WeeklySessions.MAX_SESSIONS)),
            )
        }
        ?: Activity.ofLegacy(activityLevel),
    unitSystem = UnitSystem.entries.firstOrNull { it.name == unitSystem } ?: UnitSystem.METRIC,
)

internal fun Goal.toDto() = GoalDto(
    id = id.value,
    startedAt = startedAt.toString(),
    endedAt = endedAt?.toString(),
    origin = origin.name,
    strategy = strategy.name,
    targetWeightKg = targetWeightKg,
    targetDate = targetDate?.toString(),
    kcal = daily.kcal,
    protein = daily.protein,
    carbs = daily.carbs,
    sugars = daily.sugars,
    fat = daily.fat,
    fiber = daily.fiber,
)

internal fun GoalDto.toDomain() = Goal(
    id = GoalId(id),
    startedAt = LocalDate.parse(startedAt),
    endedAt = endedAt?.let(LocalDate::parse),
    origin = GoalOrigin.entries.firstOrNull { it.name == origin } ?: GoalOrigin.CALCULATED,
    strategy = GoalStrategy.entries.firstOrNull { it.name == strategy } ?: GoalStrategy.MAINTAIN,
    targetWeightKg = targetWeightKg,
    targetDate = targetDate?.let(LocalDate::parse),
    daily = DailyGoal(kcal = kcal, protein = protein, carbs = carbs, sugars = sugars, fat = fat, fiber = fiber),
)

internal fun AdjustmentSetup.toDto() = AdjustmentDto(
    enabled = enabled,
    lastAcceptedOn = lastAcceptedOn?.toString(),
    lastIgnoredOn = lastIgnoredOn?.toString(),
)

internal fun AdjustmentDto.toDomain() = AdjustmentSetup(
    enabled = enabled,
    lastAcceptedOn = lastAcceptedOn?.let(LocalDate::parse),
    lastIgnoredOn = lastIgnoredOn?.let(LocalDate::parse),
)
