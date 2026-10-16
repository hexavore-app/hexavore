package app.hexavore.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.BirthDateField
import app.hexavore.core.designsystem.component.BodyWeightField
import app.hexavore.core.designsystem.component.HeightFields
import app.hexavore.core.designsystem.component.NeonChip
import app.hexavore.core.designsystem.component.heightProblem
import app.hexavore.core.designsystem.component.targetProblem
import app.hexavore.core.designsystem.component.weightProblem
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.GoalHorizon
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.usecase.GoalPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **2. Vous.** Date de naissance, sexe, taille, poids actuel.
 *
 * Rien n'est pré-rempli, et **tout est exigé** ([D56][decisions]). Une valeur plausible
 * affichée dans un champ serait acceptée par distraction, et une valeur par défaut
 * appliquée en silence donnerait l'objectif de quelqu'un d'autre.
 *
 * **Et tout est borné** ([D160][decisions]) : la naissance par le sélecteur lui-même, la
 * taille et le poids par une phrase sous le champ, qui donne la plage en chiffres. Le
 * poids se juge sur la taille — c'est leur couple qui fait un IMC impossible.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun AboutYouStep(answers: OnboardingAnswers, today: LocalDate, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_you_title))
        // **A l'endroit ou l'on commence a saisir**, et une seule fois : ce qu'on
        // demande ici ne part nulle part, et personne n'a a le deviner (D137).
        Body(stringResource(R.string.onboarding_privacy))

        BirthDateField(
            value = answers.birthDate,
            onValueChange = { onAnswers(answers.copy(birthDate = it)) },
            label = stringResource(R.string.onboarding_birth_date),
            today = today,
            modifier = Modifier.fillMaxWidth(),
        )

        Body(stringResource(R.string.onboarding_sex_label))
        ChoiceRow(
            options = Sex.entries,
            selected = answers.sex,
            label = { stringResource(it.labelRes) },
            onSelect = { onAnswers(answers.copy(sex = it)) },
        )
        // La troisieme option applique la moyenne des deux formules, et l'ecran le
        // dit : cacher ce detail produirait un chiffre inexplicable.
        if (answers.sex == Sex.UNSPECIFIED) Body(stringResource(R.string.onboarding_sex_unspecified_note))

        // Les champs du design system, en metrique : ce sont ceux du profil et du
        // journal, et leurs bornes se disent avec les memes phrases (D160).
        HeightFields(
            centimetres = answers.heightCm,
            units = UnitSystem.METRIC,
            label = stringResource(R.string.onboarding_height),
            onCentimetres = { onAnswers(answers.copy(heightCm = it)) },
            problem = heightProblem(answers.heightCm, UnitSystem.METRIC),
        )
        BodyWeightField(
            kilograms = answers.currentWeightKg,
            units = UnitSystem.METRIC,
            label = stringResource(R.string.onboarding_weight),
            onKilograms = { onAnswers(answers.copy(currentWeightKg = it)) },
            problem = weightProblem(answers.currentWeightKg, answers.heightCm, UnitSystem.METRIC),
        )
    }
}

/**
 * **3. Activité.** Deux questions : le métier, puis le sport.
 *
 * **Une seule question mélangeait deux choses** ([D137][decisions]). Un maçon qui ne
 * fait aucun sport dépense davantage qu'un cadre qui court trois fois par semaine, et
 * les deux se reconnaissaient dans « modérément actif » : chacun choisissait au jugé.
 *
 * Le métier se dit par un exemple concret et non par un adjectif — « debout, sans
 * porter de charge » se répond en une seconde. Le sport se compte en séances, ce qui
 * ne demande de se comparer à personne.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ActivityStep(answers: OnboardingAnswers, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StepTitle(stringResource(R.string.onboarding_work_title))
            Body(stringResource(R.string.onboarding_work_body))

            WorkActivity.entries.forEach { work ->
                NeonChip(
                    label = stringResource(work.labelRes),
                    selected = work == answers.work,
                    onClick = { onAnswers(answers.copy(work = work)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StepTitle(stringResource(R.string.onboarding_sessions_title))
            Body(stringResource(R.string.onboarding_sessions_body))
            SessionPicker(answers.sessions, onPick = { onAnswers(answers.copy(sessions = it)) })
        }
    }
}

/**
 * **4. Objectif.** Trois cartes, puis poids cible et échéance.
 *
 * L'échéance est **trois pastilles** et non un calendrier ([D56][decisions]) : une date
 * exacte n'a aucune valeur en soi, ce qui compte est le rythme. Une quatrième pastille
 * apparaît quand les garde-fous ont calculé une date atteignable — c'est ce qui
 * remplace la « date libre » que la conception prévoyait.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ObjectiveStep(
    answers: OnboardingAnswers,
    today: LocalDate,
    plan: GoalPlan?,
    onAnswers: (OnboardingAnswers) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_objective_title))

        ChoiceRow(
            options = GoalStrategy.entries,
            selected = answers.strategy,
            label = { stringResource(it.labelRes) },
            onSelect = { onAnswers(answers.copy(strategy = it)) },
        )

        if (answers.strategy != null && answers.strategy != GoalStrategy.MAINTAIN) {
            BodyWeightField(
                kilograms = answers.targetWeightKg,
                units = UnitSystem.METRIC,
                label = stringResource(R.string.onboarding_target_weight),
                onKilograms = { onAnswers(answers.copy(targetWeightKg = it)) },
                problem = targetProblem(answers.targetProblem, UnitSystem.METRIC),
            )

            Body(stringResource(R.string.onboarding_horizon_label))
            val chosen = GoalHorizon.of(today, answers.targetDate)
            GoalHorizon.entries.forEach { horizon ->
                NeonChip(
                    label = stringResource(horizon.labelRes),
                    selected = horizon == chosen,
                    onClick = { onAnswers(answers.copy(targetDate = horizon.dateFrom(today))) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // La date que les garde-fous ont calculee, quand ils ont mordu. Elle ne
            // correspond a aucune des trois, donc elle merite sa propre pastille --
            // sans quoi s'y caler donnerait un bandeau ou rien n'est selectionne. Pas
            // pour un poids vise refuse : ce serait la date d'un objectif impossible.
            plan?.reachableOn?.takeIf { answers.targetProblem == null }?.let { date ->
                NeonChip(
                    label = stringResource(R.string.onboarding_horizon_reachable, date.formatLong()),
                    selected = answers.targetDate == date,
                    onClick = { onAnswers(answers.copy(targetDate = date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // L'apercu de rythme que docs/02 demande. Il vient du plan et non d'un
            // calcul local : deux calculs du meme nombre finissent par diverger, et
            // celui-ci doit dire la meme chose que l'ecran suivant. Il se tait sur un
            // poids vise refuse : son rythme serait celui d'un objectif qu'on ne
            // pourra pas enregistrer.
            if (answers.targetDate != null && plan != null && answers.targetProblem == null) Pace(plan)
        }
    }
}

/**
 * Le rythme que l'objectif produit — ou pourquoi il n'en produit aucun.
 *
 * Quand la dépense estimée est déjà sous le plancher calorique, aucun déficit ne tient
 * ([D160][decisions]) : l'aperçu annonçait alors « 0,00 kg par semaine » sous « Perdre du
 * poids », ce qui se lisait comme une panne.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun Pace(plan: GoalPlan) {
    val floor = plan.withholdingFloorKcal
    if (floor != null) {
        Body(stringResource(R.string.onboarding_no_deficit, plan.tdee.roundToInt(), floor.roundToInt()))
    } else {
        Body(stringResource(R.string.onboarding_pace, abs(plan.weeklyWeightChangeKg)))
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { option ->
            NeonChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun StepTitle(text: String) {
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

/** « 14 mars 2027 » et non « 2027-03-14 » : l'ISO n'existe que pour le stockage. */
internal fun LocalDate.formatLong(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private val Sex.labelRes: Int
    get() = when (this) {
        Sex.MALE -> R.string.onboarding_sex_male
        Sex.FEMALE -> R.string.onboarding_sex_female
        Sex.UNSPECIFIED -> R.string.onboarding_sex_unspecified
    }

private val WorkActivity.labelRes: Int
    get() = when (this) {
        WorkActivity.DESK -> R.string.onboarding_work_desk
        WorkActivity.ON_FEET -> R.string.onboarding_work_on_feet
        WorkActivity.PHYSICAL -> R.string.onboarding_work_physical
    }

private val GoalStrategy.labelRes: Int
    get() = when (this) {
        GoalStrategy.LOSE -> R.string.onboarding_strategy_lose
        GoalStrategy.MAINTAIN -> R.string.onboarding_strategy_maintain
        GoalStrategy.GAIN -> R.string.onboarding_strategy_gain
    }

private val GoalHorizon.labelRes: Int
    get() = when (this) {
        GoalHorizon.THREE_MONTHS -> R.string.onboarding_horizon_three
        GoalHorizon.SIX_MONTHS -> R.string.onboarding_horizon_six
        GoalHorizon.TWELVE_MONTHS -> R.string.onboarding_horizon_twelve
    }
