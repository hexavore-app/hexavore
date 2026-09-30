package app.hexavore.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.usecase.GoalPlan
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Les cinq étapes, branchées sur le graphe d'injection. */
@Composable
internal fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val askNotifications = rememberNotificationRequest(onSettled = { viewModel.onFinish(onDone) })

    OnboardingScreen(
        state = state,
        actions = remember(viewModel, askNotifications) {
            OnboardingActions(
                onAnswers = viewModel::onAnswers,
                onUseReachableDate = viewModel::onUseReachableDate,
                onNext = viewModel::onNext,
                onBack = viewModel::onBack,
                onFinish = askNotifications,
            )
        },
    )
}

/**
 * La permission de notification, demandée **au dernier geste de l'onboarding**.
 *
 * ### Pourquoi ici, et pas ailleurs
 *
 * Au démarrage, elle arriverait avant qu'on sache à quoi elle sert : on la refuse, et
 * Android ne la redemande plus. Plus tard, elle n'arriverait jamais — personne ne va
 * dans les réglages pour autoriser quelque chose dont il ignore l'existence.
 *
 * Ici, elle arrive **après** les cinq questions et l'écran des six objectifs : quelqu'un
 * qui vient de poser un objectif comprend ce qu'un rappel de repas viendra faire. C'est
 * le moment où la question a le plus de chances d'être lue ([D134][decisions]).
 *
 * ### Elle ne bloque rien
 *
 * Accordée ou refusée, on arrive à l'accueil : `onSettled` est appelé dans les deux cas.
 * Un refus n'empêche pas d'utiliser l'application — il éteint seulement les rappels, et
 * l'écran des réglages le dira quand on y passera.
 *
 * En dessous d'Android 13, il n'y a rien à demander : le lanceur rend immédiatement, et
 * le geste ne se voit pas.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun rememberNotificationRequest(onSettled: () -> Unit): () -> Unit {
    val settled = rememberUpdatedState(onSettled)
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        settled.value()
    }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settled.value()
        }
    }
}

/**
 * Une question par écran, barre de progression en haut.
 *
 * **Chaque étape exige ses champs** ([D56][decisions]), et le refus se voit : appuyer
 * sur « Continuer » alors qu'il manque quelque chose affiche une barre qui dit **quoi**.
 * Un bouton qui ne fait rien laisse croire que l'application n'a pas reçu l'appui — et
 * c'est exactement ce que [D28][decisions] interdisait déjà.
 *
 * La barre plutôt qu'un texte sous les boutons : la version discrète existait, et elle
 * était invisible. Ce qui interrompt le regard est ce qui se lit.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val blocked = state.blocker?.let { stringResource(it.messageRes) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            LinearProgressIndicator(
                progress = { (state.step.index + 1f) / OnboardingStep.COUNT },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when (state.step) {
                    OnboardingStep.WELCOME -> WelcomeStep(state.answers, actions.onAnswers)
                    OnboardingStep.ABOUT_YOU -> AboutYouStep(state.answers, state.today, actions.onAnswers)
                    OnboardingStep.ACTIVITY -> ActivityStep(state.answers, actions.onAnswers)
                    OnboardingStep.OBJECTIVE ->
                        ObjectiveStep(state.answers, state.today, state.plan, actions.onAnswers)

                    OnboardingStep.RESULT -> ResultStep(state.plan, actions)
                }
                if (state.failed) Body(stringResource(R.string.onboarding_save_failed))
            }

            Navigation(
                state = state,
                actions = actions,
                onBlocked = {
                    scope.launch {
                        // `dismissPrevious` implicite : une seule barre a la fois,
                        // sinon trois appuis empilent trois messages identiques.
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(blocked.orEmpty())
                    }
                },
            )
        }
    }
}

@Composable
private fun Navigation(state: OnboardingUiState, actions: OnboardingActions, onBlocked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.step == OnboardingStep.RESULT) {
            NeonButton(
                text = stringResource(R.string.onboarding_start),
                onClick = actions.onFinish,
                modifier = Modifier.fillMaxWidth(),
                style = NeonButtonStyle.FILLED,
                availability = if (state.saving) {
                    NeonButtonAvailability.DISABLED
                } else {
                    NeonButtonAvailability.AVAILABLE
                },
            )
        } else {
            NeonButton(
                text = stringResource(R.string.onboarding_next),
                // Le bouton indisponible reagit et **repond** : c'est lui qui
                // declenche l'explication, pas un texte permanent (D28, D48).
                onClick = { if (state.canContinue) actions.onNext() else onBlocked() },
                modifier = Modifier.fillMaxWidth(),
                style = NeonButtonStyle.FILLED,
                availability = if (state.canContinue) {
                    NeonButtonAvailability.AVAILABLE
                } else {
                    NeonButtonAvailability.UNAVAILABLE
                },
            )
        }
        if (state.step != OnboardingStep.WELCOME) {
            NeonButton(
                text = stringResource(R.string.onboarding_back),
                onClick = actions.onBack,
                modifier = Modifier.fillMaxWidth(),
                style = NeonButtonStyle.OUTLINED,
            )
        }
    }
}

/**
 * **5. Vos objectifs.** Les six chiffres, chacun avec une phrase d'explication.
 *
 * Quand un garde-fou a mordu, l'écran ne bloque pas : il affiche la date atteignable
 * et propose de s'y caler. L'interdiction pure pousse les gens à mentir sur leur poids
 * pour contourner l'outil ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
@Composable
private fun ResultStep(plan: GoalPlan?, actions: OnboardingActions) {
    if (plan == null) {
        Body(stringResource(R.string.onboarding_result_unavailable))
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_result_title))

        plan.reachableOn?.let { date ->
            Body(stringResource(R.string.onboarding_capped, date.formatLong()))
            NeonButton(
                text = stringResource(R.string.onboarding_use_reachable_date),
                onClick = actions.onUseReachableDate,
                style = NeonButtonStyle.OUTLINED,
            )
        }
        if (plan.carbsBelowMinimum) Body(stringResource(R.string.onboarding_carbs_low))

        GoalLines(plan.goal)
    }
}

@Composable
private fun GoalLines(goal: DailyGoal) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Macro.entries.forEach { macro ->
            Text(
                text = stringResource(macro.lineRes, goal[macro].roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Body(stringResource(macro.explanationRes))
        }
    }
}

private val OnboardingBlocker.messageRes: Int
    get() = when (this) {
        OnboardingBlocker.DISCLAIMER -> R.string.onboarding_blocked_disclaimer
        OnboardingBlocker.IDENTITY -> R.string.onboarding_blocked_identity
        OnboardingBlocker.ACTIVITY -> R.string.onboarding_blocked_activity
        OnboardingBlocker.OBJECTIVE -> R.string.onboarding_blocked_objective
    }

private val Macro.lineRes: Int
    get() = when (this) {
        Macro.CALORIES -> R.string.onboarding_goal_calories
        Macro.PROTEIN -> R.string.onboarding_goal_protein
        Macro.FIBER -> R.string.onboarding_goal_fiber
        Macro.CARBS -> R.string.onboarding_goal_carbs
        Macro.SUGARS -> R.string.onboarding_goal_sugars
        Macro.FAT -> R.string.onboarding_goal_fat
    }

private val Macro.explanationRes: Int
    get() = when (this) {
        Macro.CALORIES -> R.string.onboarding_why_calories
        Macro.PROTEIN -> R.string.onboarding_why_protein
        Macro.FIBER -> R.string.onboarding_why_fiber
        Macro.CARBS -> R.string.onboarding_why_carbs
        Macro.SUGARS -> R.string.onboarding_why_sugars
        Macro.FAT -> R.string.onboarding_why_fat
    }
