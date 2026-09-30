package app.hexavore.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.MacroHexagon
import app.hexavore.core.designsystem.component.MacroQuarter
import app.hexavore.core.designsystem.component.NeonChip
import app.hexavore.core.designsystem.component.NeonDateField
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.GoalHorizon
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.usecase.GoalPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/**
 * **1. Accueil.** La figure des six compteurs, une phrase, et l'avertissement.
 *
 * L'hexagone plutôt qu'un paragraphe de plus : c'est la figure qui donne son nom à
 * l'application, elle existe déjà, et elle montre en une image ce qu'un texte
 * raconterait — six compteurs, pas seulement des calories. Les valeurs sont un
 * échantillon, et l'écran ne prétend pas le contraire.
 *
 * **La phrase de l'avertissement est cliquable, pas seulement la case.** Une cible de
 * 24 dp au milieu d'un paragraphe de quatre lignes est une cible qu'on rate ; c'est la
 * ligne entière qui bascule, et TalkBack l'annonce comme une seule case à cocher.
 */
@Composable
internal fun WelcomeStep(answers: OnboardingAnswers, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        StepTitle(stringResource(R.string.onboarding_welcome_title))
        Body(stringResource(R.string.onboarding_welcome_body))

        MacroHexagon(quarters = SAMPLE_DAY, modifier = Modifier.padding(vertical = Spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = answers.disclaimerAccepted,
                    role = Role.Checkbox,
                    onValueChange = { onAnswers(answers.copy(disclaimerAccepted = it)) },
                ).padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            // `onCheckedChange = null` : la case ne recoit plus le clic, c'est la
            // ligne qui le porte. Sans cela, TalkBack annoncerait deux cibles pour
            // une seule decision.
            Checkbox(checked = answers.disclaimerAccepted, onCheckedChange = null)
            Body(stringResource(R.string.onboarding_disclaimer))
        }
    }
}

/**
 * **2. Vous.** Date de naissance, sexe, taille, poids actuel.
 *
 * Rien n'est pré-rempli, et **tout est exigé** ([D56][decisions]). Une valeur plausible
 * affichée dans un champ serait acceptée par distraction, et une valeur par défaut
 * appliquée en silence donnerait l'objectif de quelqu'un d'autre.
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

        NeonDateField(
            value = answers.birthDate,
            onValueChange = { onAnswers(answers.copy(birthDate = it)) },
            label = stringResource(R.string.onboarding_birth_date),
            // Une naissance ne se cherche pas en feuilletant les mois : la grille des
            // annees est le seul chemin praticable, et la borner evite d'en derouler
            // deux mille.
            yearRange = (today.year - OLDEST_YEARS)..(today.year - YOUNGEST_YEARS),
            latest = today,
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

        NumberField(
            value = answers.heightCm,
            label = stringResource(R.string.onboarding_height),
            onValueChange = { onAnswers(answers.copy(heightCm = it)) },
        )
        NumberField(
            value = answers.currentWeightKg,
            label = stringResource(R.string.onboarding_weight),
            onValueChange = { onAnswers(answers.copy(currentWeightKg = it)) },
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
            NumberField(
                value = answers.targetWeightKg,
                label = stringResource(R.string.onboarding_target_weight),
                onValueChange = { onAnswers(answers.copy(targetWeightKg = it)) },
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
            // sans quoi s'y caler donnerait un bandeau ou rien n'est selectionne.
            plan?.reachableOn?.let { date ->
                NeonChip(
                    label = stringResource(R.string.onboarding_horizon_reachable, date.formatLong()),
                    selected = answers.targetDate == date,
                    onClick = { onAnswers(answers.copy(targetDate = date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // L'apercu de rythme que docs/02 demande. Il vient du plan et non d'un
            // calcul local : deux calculs du meme nombre finissent par diverger, et
            // celui-ci doit dire la meme chose que l'ecran suivant.
            if (answers.targetDate != null && plan != null) {
                Body(stringResource(R.string.onboarding_pace, abs(plan.weeklyWeightChangeKg)))
            }
        }
    }
}

@Composable
private fun NumberField(value: Double?, label: String, onValueChange: (Double?) -> Unit) {
    DraftTextField(
        initial = value?.let { formatDecimal(it) }.orEmpty(),
        onValueChange = { text -> onValueChange(text.replace(',', '.').toDoubleOrNull()) },
        label = label,
        keyboardType = KeyboardType.Decimal,
        // Le champ refuse une frappe non numerique au lieu de l'accepter puis de la
        // nettoyer : nettoyer obligerait a reecrire le texte affiche, donc a
        // repositionner le curseur (D45).
        accept = { text -> text.isEmpty() || text.matches(DECIMAL) },
        modifier = Modifier.fillMaxWidth(),
    )
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

private val DECIMAL = Regex("""\d{0,3}([.,]\d{0,2})?""")

/** Bornes de la grille des années, en années révolues avant aujourd'hui. */
private const val OLDEST_YEARS = 110
private const val YOUNGEST_YEARS = 13

/**
 * Une journée d'exemple, pour que la figure d'accueil montre quelque chose.
 *
 * Des valeurs plausibles et volontairement inégales : six quartiers identiques
 * ressembleraient à un motif décoratif plutôt qu'à des compteurs.
 */
private val SAMPLE_DAY = mapOf(
    Macro.CALORIES to MacroQuarter(0.61f),
    Macro.PROTEIN to MacroQuarter(0.78f),
    Macro.FIBER to MacroQuarter(0.34f),
    Macro.CARBS to MacroQuarter(0.72f),
    Macro.SUGARS to MacroQuarter(0.45f),
    Macro.FAT to MacroQuarter(0.55f),
)

internal fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

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
