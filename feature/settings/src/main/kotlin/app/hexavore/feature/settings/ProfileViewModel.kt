package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.CalculateDailyGoal
import app.hexavore.domain.usecase.GoalRevision
import app.hexavore.domain.usecase.ReviseGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Relire son profil, le corriger, et voir l'objectif suivre.
 *
 * **Le profil, la pesée et l'objectif sont lus une seule fois**, à l'ouverture, et non
 * observés. Un flux ferait revenir l'état enregistré par-dessus une saisie en cours :
 * il suffirait que l'écriture aboutisse pendant qu'on tape pour que le champ reparte en
 * arrière. C'est le même raisonnement qu'en [D56][decisions] pour la destination de
 * départ — ce qui se décide à l'ouverture se lit à l'ouverture.
 *
 * **Deux modes, et un seul décide des six chiffres** ([D60][decisions]). En calculé,
 * ils suivent le profil en direct ; en manuel, ils viennent de la saisie et **aucun
 * recalcul n'y touche**. Le plan continue d'être calculé dans les deux cas, parce que
 * revenir au calcul ne doit pas demander de quitter l'écran.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val profiles: Profiles,
    private val weights: WeightLog,
    private val goals: Goals,
    private val calculate: CalculateDailyGoal,
    private val reviseGoal: ReviseGoal,
    clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(ProfileUiState(today = clock.today()))
    val uiState: StateFlow<ProfileUiState> = state

    init {
        viewModelScope.launch {
            val outcome = runCatching { read() }
            state.update { current ->
                outcome.fold(
                    onSuccess = { lu ->
                        current
                            .copy(loaded = true, form = lu.form, saved = lu.goal, units = lu.units)
                            .planned(calculate)
                    },
                    // Un echec de lecture se dit (D39). Presenter un formulaire vide
                    // laisserait croire qu'aucun profil n'existe, et l'enregistrer
                    // ecraserait celui qui est en base.
                    onFailure = { current.copy(loaded = true, unreadable = true) },
                )
            }
        }
    }

    /** Le crayon. C'est la seule porte vers la modification, switch de mode compris. */
    fun onEdit() {
        state.update { it.copy(editing = true) }
    }

    /**
     * Les réponses, remplacées en bloc.
     *
     * Choisir « Maintenir » efface le poids cible et l'échéance, comme aux cinq
     * questions : les garder ferait calculer un écart calorique pour une stratégie qui
     * n'en veut pas, et l'utilisateur verrait un déficit sous une étiquette « maintien ».
     */
    fun onForm(form: ProfileForm) {
        val cleaned = if (form.strategy == GoalStrategy.MAINTAIN) {
            form.copy(targetWeightKg = null, targetDate = null)
        } else {
            form
        }
        state.update { it.copy(form = cleaned).planned(calculate) }
    }

    /**
     * Bascule entre objectif calculé et objectif saisi à la main.
     *
     * Passer en manuel **part des six chiffres affichés** plutôt que de six champs
     * vides : ce qu'on veut corriger est presque toujours ce qui est là, de quelques
     * grammes. Revenir au calcul les jette — les garder ferait réapparaître une vieille
     * saisie au prochain aller-retour, sans que rien ne dise d'où elle sort.
     */
    fun onManual(manual: Boolean) {
        state.update { current ->
            val macros = if (manual) current.plan?.goal.toMacros() else emptyMap()
            current.copy(form = current.form.copy(manual = manual, macros = macros))
        }
    }

    fun onMacroChange(macro: Macro, value: Double?) {
        state.update { it.copy(form = it.form.copy(macros = it.form.macros + (macro to value))) }
    }

    /**
     * Demande à enregistrer.
     *
     * **Si les six chiffres changent, l'écran le dit avant d'écrire** : la correction
     * part en attente et la boîte de confirmation les montre face aux anciens. Corriger
     * sa taille de deux centimètres déplace un objectif quotidien, et c'est le genre de
     * conséquence qu'on ne doit pas découvrir sur l'accueil.
     *
     * Quand ils ne changent pas, il n'y a rien à annoncer et l'écriture part
     * directement — un dialogue qui répète ce qu'on vient de lire s'apprend à fermer
     * sans le lire.
     */
    fun onSave(onDone: () -> Unit) {
        val current = state.value
        val next = current.daily?.takeIf { current.canSave } ?: return
        if (next == current.saved) write(next, onDone) else state.update { it.copy(pending = next) }
    }

    fun onConfirm(onDone: () -> Unit) {
        val pending = state.value.pending ?: return
        state.update { it.copy(pending = null) }
        write(pending, onDone)
    }

    /** Renoncer à la confirmation ramène au formulaire, corrections intactes. */
    fun onDismissConfirmation() {
        state.update { it.copy(pending = null) }
    }

    private fun write(daily: DailyGoal, onDone: () -> Unit) {
        val revision = state.value.revisionFor(daily) ?: return
        state.update { it.copy(saving = true, failed = false) }

        viewModelScope.launch {
            val outcome = runCatching { reviseGoal(revision) }
            state.update { it.copy(saving = false, failed = outcome.isFailure) }
            if (outcome.isSuccess) onDone()
        }
    }

    /** Ce qu'une lecture rapporte : le formulaire, l'objectif enregistré, et les unités. */
    private data class Loaded(val form: ProfileForm, val goal: DailyGoal?, val units: UnitSystem)

    private suspend fun read(): Loaded {
        val profile = profiles.observeProfile().first()
        val weight = weights.observeLatest().first()
        val goal = goals.observeCurrent().first()

        val form = ProfileForm(
            birthDate = profile?.birthDate,
            sex = profile?.sex,
            heightCm = profile?.heightCm,
            // Le poids vient du journal de pesees et non du profil : c'est la
            // derniere mesure connue qui entre dans le calcul.
            currentWeightKg = weight?.weightKg,
            work = profile?.activity?.work,
            sessions = profile?.activity?.sessions ?: WeeklySessions.NONE,
            strategy = goal?.strategy,
            targetWeightKg = goal?.targetWeightKg,
            targetDate = goal?.targetDate,
            manual = goal?.origin == GoalOrigin.MANUAL,
            macros = if (goal?.origin == GoalOrigin.MANUAL) goal.daily.toMacros() else emptyMap(),
        )
        return Loaded(form, goal?.daily, profile?.unitSystem ?: UnitSystem.METRIC)
    }
}

// Hors de la classe : ce sont des conversions d'etat, pas des capacites de l'ecran.
// C'est aussi la reponse du projet au seuil de fonctions de detekt -- sortir ce qui
// n'appartient pas au type plutot que relever le seuil (docs/10).

/**
 * Ce que le calcul propose pour ce formulaire, ou `null` s'il y manque une réponse.
 *
 * Calculé **même en mode manuel** : c'est ce qui permet de revenir au calcul sans
 * quitter l'écran, et de montrer au passage ce qu'il proposerait.
 */
private fun ProfileUiState.planned(calculate: CalculateDailyGoal): ProfileUiState {
    val profile = form.toProfile()
    val request = form.toRequest()
    return copy(plan = if (profile != null && request != null) calculate(profile, request) else null)
}

private fun ProfileUiState.revisionFor(daily: DailyGoal): GoalRevision? {
    val profile = form.toProfile()
    val request = form.toRequest()
    return if (profile != null && request != null) {
        GoalRevision(profile = profile, request = request, daily = daily, origin = origin)
    } else {
        null
    }
}

private fun DailyGoal?.toMacros(): Map<Macro, Double?> =
    this?.let { goal -> Macro.entries.associateWith { goal[it] } }.orEmpty()
