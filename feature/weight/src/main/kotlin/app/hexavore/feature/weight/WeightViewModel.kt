package app.hexavore.feature.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.AdjustmentResponse
import app.hexavore.domain.usecase.GetWeightTrend
import app.hexavore.domain.usecase.RespondToAdjustment
import app.hexavore.domain.usecase.SuggestGoalAdjustment
import app.hexavore.domain.usecase.TrendPoint
import app.hexavore.domain.usecase.WeightTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Le journal de poids.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@HiltViewModel
internal class WeightViewModel @Inject constructor(
    getWeightTrend: GetWeightTrend,
    readings: ProfileReadings,
    suggestGoalAdjustment: SuggestGoalAdjustment,
    private val gestures: WeighingGestures,
    private val respondToAdjustment: RespondToAdjustment,
    private val clock: Clock,
    private val trendDisplay: TrendDisplay,
) : ViewModel() {
    val uiState: StateFlow<WeightUiState> = combine<WeightTrend, UnitSystem, Double?, Boolean, WeightUiState>(
        getWeightTrend(),
        readings.units(),
        readings.height(),
        trendDisplay.observe(),
    ) {
            trend,
            units,
            heightCm,
            trendShown,
        ->
        WeightUiState.Loaded(trend, clock.today(), units, trendShown, heightCm)
    }
        // Une lecture qui echoue ne se deguise pas en journal vide : « vous ne vous
        // etes jamais pese » est une affirmation, pas une absence de reponse.
        .catch { emit(WeightUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = WeightUiState.Loading,
        )

    /**
     * La correction que l'adaptation propose, ou `null` — ce qui est le cas normal.
     *
     * **Hors de [uiState]** : la courbe peut être illisible pendant que la suggestion
     * tient, et l'inverse autant. Les fondre ferait deux échecs indépendants dans une
     * même hiérarchie.
     */
    val suggestion: StateFlow<AdjustmentSuggestion?> = suggestGoalAdjustment()
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /**
     * Répond à la suggestion affichée.
     *
     * Elle vient de l'état et non de l'écran : entre l'affichage et l'appui, une pesée
     * a pu changer la correction, et écrire celle que l'écran tenait écrirait un
     * chiffre périmé.
     */
    fun onAdjustment(response: AdjustmentResponse) {
        val shown = suggestion.value ?: return
        viewModelScope.launch { respondToAdjustment(response, shown) }
    }

    /** Le jour proposé par défaut dans la boîte de saisie. */
    fun today(): LocalDate = clock.today()

    fun onRecord(date: LocalDate, weightKg: Double) {
        viewModelScope.launch { gestures.record(WeightEntry(date, weightKg)) }
    }

    /** La pesée qu'on vient de retirer, tant que la barre d'annulation peut la rendre. */
    private val retired = MutableStateFlow<WeightEntry?>(null)

    val undoable: StateFlow<WeightEntry?> = retired.asStateFlow()

    /**
     * Retire la pesée de ce jour ([D165][decisions]).
     *
     * **Tout de suite**, et la barre qui suit la rend : une suppression différée
     * disparaîtrait avec un processus tué. Un échec n'offre rien à annuler, puisque rien
     * n'est parti.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onDelete(point: TrendPoint) {
        val entry = WeightEntry(point.date, point.weightKg)
        viewModelScope.launch {
            runCatching { gestures.delete(entry.date) }.onSuccess { retired.value = entry }
        }
    }

    fun onUndo() {
        val entry = retired.value ?: return
        retired.value = null
        viewModelScope.launch { runCatching { gestures.restore(entry) } }
    }

    /** La fenêtre d'annulation est passée : la pesée cesse d'être rattrapable. */
    fun onUndoExpired() {
        retired.value = null
    }

    /**
     * La case sous la courbe. Le choix est retenu d'une visite à l'autre (D159).
     *
     * Un échec d'écriture laisse la case où elle était : la préférence relue la remet
     * d'accord avec le disque, et la courbe ne ment pas sur ce qu'elle trace.
     */
    fun onTrendShown(shown: Boolean) {
        viewModelScope.launch { runCatching { trendDisplay.show(shown) } }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Ce que l'écran affiche. */
internal sealed interface WeightUiState {
    data object Loading : WeightUiState

    /**
     * La lecture n'a pas abouti.
     *
     * Distinct d'un journal vide, et pour la même raison que sur l'accueil : une liste
     * vide dit « rien n'a été noté », ce qui serait un mensonge sur une base illisible.
     */
    data object Error : WeightUiState

    data class Loaded(
        val trend: WeightTrend,
        val today: LocalDate,
        /** Le système d'unités : la courbe et la saisie le suivent, la base garde ses kilogrammes. */
        val units: UnitSystem,
        /** La tendance est-elle tracée ? Allumée par défaut, et retenue ([D159][decisions]). */
        val trendShown: Boolean = true,
        /**
         * La taille du profil : c'est sur elle qu'une pesée se juge avant de s'écrire
         * ([D160][decisions]). `null` sans profil, et la pesée se juge alors sur les
         * bornes de toutes les tailles.
         */
        val heightCm: Double? = null,
    ) : WeightUiState {
        /** `true` quand aucune pesée n'a jamais été notée. */
        val empty: Boolean get() = trend.points.isEmpty()

        /**
         * `true` quand il n'y a qu'une pesée : une courbe en demande deux.
         *
         * L'écran le dit plutôt que de laisser chercher où est passé le graphique. C'était
         * « pas encore de tendance », sous trois pesées dans la même semaine ; la tendance
         * existe désormais dès la première pesée, et c'est la courbe entière qui attend la
         * deuxième ([D159][decisions]).
         *
         * [decisions]: docs/11-decisions.md
         */
        val curveMissing: Boolean get() = trend.points.size == 1
    }
}
