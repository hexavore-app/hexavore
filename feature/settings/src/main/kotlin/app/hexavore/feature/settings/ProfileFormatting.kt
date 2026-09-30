package app.hexavore.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.hexavore.domain.goal.GoalHorizon
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Une ligne de consultation : ce qu'on relit, sans champ ni bordure autour. */
@Composable
internal fun ReadLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

internal fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** « 14 mars 2027 » et non « 2027-03-14 » : l'ISO n'existe que pour le stockage. */
internal fun LocalDate.formatLong(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

internal val Sex.labelRes: Int
    get() = when (this) {
        Sex.MALE -> R.string.profile_sex_male
        Sex.FEMALE -> R.string.profile_sex_female
        Sex.UNSPECIFIED -> R.string.profile_sex_unspecified
    }

internal val WorkActivity.labelRes: Int
    get() = when (this) {
        WorkActivity.DESK -> R.string.profile_work_desk
        WorkActivity.ON_FEET -> R.string.profile_work_on_feet
        WorkActivity.PHYSICAL -> R.string.profile_work_physical
    }

/**
 * Le nombre de séances, écrit.
 *
 * « 5 et plus » pour la dernière : au-delà, l'écart ne change presque rien au besoin
 * calculé, et prétendre le contraire donnerait une précision que la formule ne tient pas.
 */
@Composable
internal fun sessionLabel(sessions: WeeklySessions): String = when (sessions.count) {
    0 -> stringResource(R.string.profile_sessions_none)
    WeeklySessions.MAX_SESSIONS -> stringResource(R.string.profile_sessions_max, sessions.count)
    else -> pluralStringResource(R.plurals.profile_sessions, sessions.count, sessions.count)
}

internal val GoalStrategy.labelRes: Int
    get() = when (this) {
        GoalStrategy.LOSE -> R.string.profile_strategy_lose
        GoalStrategy.MAINTAIN -> R.string.profile_strategy_maintain
        GoalStrategy.GAIN -> R.string.profile_strategy_gain
    }

internal val GoalHorizon.labelRes: Int
    get() = when (this) {
        GoalHorizon.THREE_MONTHS -> R.string.profile_horizon_three
        GoalHorizon.SIX_MONTHS -> R.string.profile_horizon_six
        GoalHorizon.TWELVE_MONTHS -> R.string.profile_horizon_twelve
    }

internal val Macro.labelRes: Int
    get() = when (this) {
        Macro.CALORIES -> R.string.profile_macro_calories
        Macro.PROTEIN -> R.string.profile_macro_protein
        Macro.CARBS -> R.string.profile_macro_carbs
        Macro.SUGARS -> R.string.profile_macro_sugars
        Macro.FAT -> R.string.profile_macro_fat
        Macro.FIBER -> R.string.profile_macro_fiber
    }

/** Les calories sont en kcal, les cinq autres en grammes entiers ([D52][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal val Macro.unitRes: Int
    get() = if (this == Macro.CALORIES) R.string.profile_unit_kcal else R.string.profile_unit_gram
