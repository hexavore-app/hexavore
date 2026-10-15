package app.hexavore.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.heightProblem
import app.hexavore.core.designsystem.component.targetProblem
import app.hexavore.core.designsystem.component.weightProblem
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.BodyBounds
import app.hexavore.domain.profile.UnitSystem
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
 * **Le refus referme aussi le clavier** ([D160][decisions]) : un champ qu'on quitte dit
 * ce qui ne va pas sous lui, et c'est ainsi que le chiffre fautif se désigne en rouge au
 * moment où la barre en parle.
 *
 * **Le retour du système recule d'une étape**, comme le bouton « Retour »
 * ([D161][decisions]). Il fermait l'application, et les réponses déjà données partaient
 * avec elle : on rouvrait sur la première question. À la première étape, il n'y a rien
 * derrière, et le système reprend la main.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val blocked = state.blocker?.let { blockerMessage(it, state.answers) }

    BackHandler(enabled = state.step != OnboardingStep.WELCOME, onBack = actions.onBack)

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

                    OnboardingStep.RESULT -> ResultStep(state.plan, state.answers.strategy, actions)
                }
                if (state.failed) Body(stringResource(R.string.onboarding_save_failed))
            }

            Navigation(
                state = state,
                actions = actions,
                onBlocked = {
                    focus.clearFocus()
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
private fun ResultStep(plan: GoalPlan?, strategy: GoalStrategy?, actions: OnboardingActions) {
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
        // Le plancher a retenu tout le deficit : il n'y a pas de date a proposer, et
        // le dire evite de chercher pourquoi la perte demandee ne change rien (D160).
        if (plan.withholdingFloorKcal != null) Pace(plan)
        if (plan.carbsBelowMinimum) Body(stringResource(R.string.onboarding_carbs_low))

        GoalLines(plan.goal, strategy)
    }
}

@Composable
private fun GoalLines(goal: DailyGoal, strategy: GoalStrategy?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Macro.entries.forEach { macro ->
            Text(
                text = stringResource(macro.lineRes, goal[macro].roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Body(stringResource(macro.explanationRes(strategy)))
        }
    }
}

/**
 * La phrase de la barre, pour ce qui bloque.
 *
 * **Une valeur invraisemblable reprend la phrase de son champ** ([D160][decisions]),
 * mot pour mot : la barre et le rouge sous le champ disent la même plage, parce qu'ils
 * la tiennent de la même fonction.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun blockerMessage(blocker: OnboardingBlocker, answers: OnboardingAnswers): String = when (blocker) {
    OnboardingBlocker.DISCLAIMER -> stringResource(R.string.onboarding_blocked_disclaimer)
    OnboardingBlocker.IDENTITY -> stringResource(R.string.onboarding_blocked_identity)
    OnboardingBlocker.AGE ->
        stringResource(R.string.onboarding_blocked_age, BodyBounds.AGES.first, BodyBounds.AGES.last)
    OnboardingBlocker.HEIGHT -> heightProblem(answers.heightCm, UnitSystem.METRIC)?.text.orEmpty()
    OnboardingBlocker.WEIGHT ->
        weightProblem(answers.currentWeightKg, answers.heightCm, UnitSystem.METRIC)?.text.orEmpty()
    OnboardingBlocker.ACTIVITY -> stringResource(R.string.onboarding_blocked_activity)
    OnboardingBlocker.OBJECTIVE -> stringResource(R.string.onboarding_blocked_objective)
    OnboardingBlocker.TARGET -> targetProblem(answers.targetProblem, UnitSystem.METRIC)?.text.orEmpty()
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

/**
 * La phrase qui explique un compteur, selon l'objectif ([D162][decisions]).
 *
 * **Un maintien n'a ni échéance ni poids cible** — le choisir les efface. Il s'entendait
 * dire que ses calories étaient « ajustées à votre échéance », et ses protéines
 * « calculées sur votre poids cible », alors qu'elles le sont sur le poids actuel.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun Macro.explanationRes(strategy: GoalStrategy?): Int {
    val maintaining = strategy == GoalStrategy.MAINTAIN
    return when (this) {
        Macro.CALORIES ->
            if (maintaining) R.string.onboarding_why_calories_maintain else R.string.onboarding_why_calories
        Macro.PROTEIN ->
            if (maintaining) R.string.onboarding_why_protein_maintain else R.string.onboarding_why_protein
        Macro.FIBER -> R.string.onboarding_why_fiber
        Macro.CARBS -> R.string.onboarding_why_carbs
        Macro.SUGARS -> R.string.onboarding_why_sugars
        Macro.FAT -> R.string.onboarding_why_fat
    }
}
