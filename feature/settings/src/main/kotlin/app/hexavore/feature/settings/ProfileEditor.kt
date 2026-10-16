package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.profile.kilogramsToPounds
import app.hexavore.domain.usecase.GoalPlan
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Le formulaire, une fois le crayon ouvert. */
@Composable
internal fun ProfileEditor(state: ProfileUiState, actions: ProfileActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Body(stringResource(R.string.profile_history_note))
        YouSection(state.form, state.today, state.units, actions.onForm)
        ActivitySection(state.form, actions.onForm)
        ObjectiveSection(state.form, state.today, state.plan, state.units, actions.onForm)
        CountersSection(state, actions)
        if (state.failed) Body(stringResource(R.string.profile_save_failed))
    }
}

/**
 * **Vous.** Les quatre réponses dont le calcul de la dépense a besoin.
 *
 * Le poids est ici et non dans une section « pesée » : c'est la dernière mesure connue,
 * c'est elle qui entre dans le calcul, et la corriger est le geste le plus fréquent de
 * cet écran. Le corriger enregistre une pesée du jour — le laisser tel quel n'en invente
 * aucune, et l'écran le dit.
 *
 * **Les bornes sont celles de l'onboarding** ([D160][decisions]), écrites une fois dans
 * le domaine et dites avec les mêmes phrases : un profil qu'on corrige ne doit pas
 * accepter ce que les cinq questions ont refusé.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun YouSection(form: ProfileForm, today: LocalDate, units: UnitSystem, onForm: (ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.profile_you_title))

        BirthDateField(
            value = form.birthDate,
            onValueChange = { onForm(form.copy(birthDate = it)) },
            label = stringResource(R.string.profile_birth_date),
            today = today,
            modifier = Modifier.fillMaxWidth(),
        )

        Body(stringResource(R.string.profile_sex_label))
        ChoiceColumn(
            options = Sex.entries,
            selected = form.sex,
            label = { stringResource(it.labelRes) },
            onSelect = { onForm(form.copy(sex = it)) },
        )
        if (form.sex == Sex.UNSPECIFIED) Body(stringResource(R.string.profile_sex_unspecified_note))

        HeightFields(
            centimetres = form.heightCm,
            units = units,
            label = stringResource(R.string.profile_height),
            onCentimetres = { onForm(form.copy(heightCm = it)) },
            problem = heightProblem(form.heightCm, units),
        )
        BodyWeightField(
            kilograms = form.currentWeightKg,
            units = units,
            label = stringResource(R.string.profile_weight),
            onKilograms = { onForm(form.copy(currentWeightKg = it)) },
            problem = weightProblem(form.currentWeightKg, form.heightCm, units),
        )
        Body(stringResource(R.string.profile_weight_note))
    }
}

/**
 * **Votre activité.** Le métier, puis le sport.
 *
 * Deux questions là où il n'y en avait qu'une ([D137][decisions]) : un maçon sans sport
 * dépense davantage qu'un cadre qui court trois fois par semaine, et les deux se
 * reconnaissaient dans « modérément actif ».
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun ActivitySection(form: ProfileForm, onForm: (ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Body(stringResource(R.string.profile_privacy))
        SectionTitle(stringResource(R.string.profile_work_title))
        Body(stringResource(R.string.profile_work_body))
        ChoiceColumn(
            options = WorkActivity.entries,
            selected = form.work,
            label = { stringResource(it.labelRes) },
            onSelect = { onForm(form.copy(work = it)) },
        )

        SectionTitle(stringResource(R.string.profile_sessions_title))
        ChoiceColumn(
            options = (0..WeeklySessions.MAX_SESSIONS).map { WeeklySessions(it) },
            selected = form.sessions,
            label = { sessionLabel(it) },
            onSelect = { onForm(form.copy(sessions = it)) },
        )
    }
}

/**
 * **Votre objectif.** La stratégie, le poids visé, et l'échéance.
 *
 * L'échéance en cours s'affiche **telle qu'elle est**, en toutes lettres, et les trois
 * pastilles servent à la déplacer. Elle a été choisie un autre jour : la traduire en
 * « dans 6 mois » la ferait glisser de la durée écoulée depuis, ce qui est faux.
 *
 * **En saisie manuelle, ces deux champs restent modifiables mais ne pilotent plus
 * rien**, et une phrase le dit ([D60][decisions]). Les masquer aurait fait disparaître
 * le cap annoncé, dont le journal de poids tire sa trajectoire ; les laisser sans rien
 * dire aurait laissé croire qu'une correction d'échéance déplace les six compteurs.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun ObjectiveSection(
    form: ProfileForm,
    today: LocalDate,
    plan: GoalPlan?,
    units: UnitSystem,
    onForm: (ProfileForm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.profile_objective_title))

        ChoiceColumn(
            options = GoalStrategy.entries,
            selected = form.strategy,
            label = { stringResource(it.labelRes) },
            onSelect = { onForm(form.copy(strategy = it)) },
        )

        if (form.strategy != null && form.strategy != GoalStrategy.MAINTAIN) {
            if (form.manual) Body(stringResource(R.string.profile_manual_horizon_note))

            BodyWeightField(
                kilograms = form.targetWeightKg,
                units = units,
                label = stringResource(R.string.profile_target_weight),
                onKilograms = { onForm(form.copy(targetWeightKg = it)) },
                problem = targetProblem(form.targetProblem, units),
            )

            form.targetDate?.let { Body(stringResource(R.string.profile_horizon_current, it.formatLong())) }
            Body(stringResource(R.string.profile_horizon_label))
            Horizons(form, today, plan, onForm)

            // Le rythme et les garde-fous decrivent ce que le calcul ferait : en
            // saisie manuelle, ils annonceraient un objectif qui n'est pas celui
            // qu'on enregistre. Sur un poids vise refuse non plus : ce serait le
            // rythme d'un objectif qu'on ne pourra pas enregistrer (D160).
            if (plan != null && !form.manual && form.targetProblem == null) PlanNotes(form, plan, units)
        }
    }
}

/**
 * Ce que le calcul dit de l'objectif : son rythme, la date sûre, les glucides rognés.
 *
 * **Ou pourquoi il n'y a pas de rythme** : quand la dépense estimée est déjà sous le
 * plancher calorique, aucun déficit ne tient ([D160][decisions]), et « 0,00 kg par
 * semaine » sous « Perdre du poids » se lisait comme une panne.
 *
 * **Le rythme se dit dans le système choisi** ([D162][decisions]) : il s'annonçait en
 * kilos sous un poids visé saisi en livres.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun PlanNotes(form: ProfileForm, plan: GoalPlan, units: UnitSystem) {
    val floor = plan.withholdingFloorKcal
    val pace = abs(plan.weeklyWeightChangeKg)
    when {
        floor != null -> Body(stringResource(R.string.profile_no_deficit, plan.tdee.roundToInt(), floor.roundToInt()))
        form.targetDate == null -> Unit
        units == UnitSystem.IMPERIAL -> Body(stringResource(R.string.profile_pace_pounds, kilogramsToPounds(pace)))
        else -> Body(stringResource(R.string.profile_pace, pace))
    }
    plan.reachableOn?.let { Body(stringResource(R.string.profile_capped, it.formatLong())) }
    if (plan.carbsBelowMinimum) Body(stringResource(R.string.profile_carbs_low))
}

@Composable
private fun Horizons(form: ProfileForm, today: LocalDate, plan: GoalPlan?, onForm: (ProfileForm) -> Unit) {
    val chosen = GoalHorizon.of(today, form.targetDate)
    GoalHorizon.entries.forEach { horizon ->
        NeonChip(
            label = stringResource(horizon.labelRes),
            selected = horizon == chosen,
            onClick = { onForm(form.copy(targetDate = horizon.dateFrom(today))) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // La date que les garde-fous ont calculee, quand ils ont mordu. Elle ne
    // correspond a aucune des trois, donc elle merite sa propre pastille -- sauf pour
    // un poids vise refuse, dont elle serait la date impossible (D160).
    if (!form.manual && form.targetProblem == null) {
        plan?.reachableOn?.let { date ->
            NeonChip(
                label = stringResource(R.string.profile_horizon_reachable, date.formatLong()),
                selected = form.targetDate == date,
                onClick = { onForm(form.copy(targetDate = date)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Le switch qui bascule d'un objectif calculé à un objectif saisi à la main.
 *
 * **La ligne entière est la cible tactile**, comme l'avertissement de l'onboarding :
 * une cible de 24 dp à droite d'une phrase est une cible qu'on rate, et TalkBack
 * annonce ainsi une seule case à cocher au lieu de deux.
 */
@Composable
internal fun ModeSwitch(manual: Boolean, onManual: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = manual, role = Role.Switch, onValueChange = onManual)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Body(stringResource(R.string.profile_mode_switch))
        // `onCheckedChange = null` : le switch ne recoit plus le clic, c'est la ligne
        // qui le porte. Sans cela, TalkBack annoncerait deux cibles pour une decision.
        Switch(checked = manual, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChoiceColumn(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
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
