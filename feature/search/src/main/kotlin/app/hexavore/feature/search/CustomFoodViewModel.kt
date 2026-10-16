package app.hexavore.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.core.ui.FormMemory
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.ContributionOutcome
import app.hexavore.domain.food.CustomFoodDraft
import app.hexavore.domain.food.FoodContribution
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.usecase.OfferContribution
import app.hexavore.domain.usecase.SaveCustomFood
import app.hexavore.domain.usecase.SendContribution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Le formulaire d'aliment personnel.
 *
 * **Du texte et non des nombres**, pour la même raison que l'écran de validation :
 * un champ qui rendrait un `Double` reformaterait « 12, » en « 12 » à la frappe
 * suivante, et « 12,5 » deviendrait impossible à saisir.
 *
 * Il s'ouvre avec le nom déjà tapé dans la recherche : c'est le parcours « aucun
 * résultat », et retaper « pâtes de mamie » serait la première chose que
 * l'utilisateur reprocherait à cet écran.
 */
@HiltViewModel
internal class CustomFoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveCustomFood: SaveCustomFood,
    private val offerContribution: OfferContribution,
    private val sendContribution: SendContribution,
) : ViewModel() {
    /**
     * Le code-barres est relu **une fois** et ne devient jamais un champ : il n'a pas
     * été tapé, il a été lu, et le laisser modifier ferait porter à la fiche un code
     * qui n'est pas celui de l'emballage qu'on a devant soi.
     */
    private val barcode: Barcode? =
        savedStateHandle.get<String>(CustomFoodDestination.BARCODE)?.let(Barcode::of)

    /**
     * Le formulaire tel qu'Android l'avait quand il a tué l'application ([D166][decisions]).
     *
     * Relu avant le nom que la route propose : ce qu'on a tapé depuis vaut mieux que ce
     * qu'on avait cherché. Le code-barres, lui, vient toujours de la route — il se lit et
     * ne se tape pas.
     *
     * [decisions]: docs/11-decisions.md
     */
    private val memory = FormMemory(savedStateHandle, MEMORY_KEY, CustomFoodMemory.serializer())

    private val form = MutableStateFlow(
        memory.recall()?.let { kept -> runCatching { kept.toForm(barcode) }.getOrNull() }
            ?: CustomFoodForm(
                name = savedStateHandle.get<String>(CustomFoodDestination.NAME).orEmpty(),
                barcode = barcode,
            ),
    )
    private val status = MutableStateFlow(Status.EDITING)

    /**
     * La proposition, quand il y en a une.
     *
     * Un flux a part plutot qu'un statut de plus : elle se superpose a
     * l'enregistrement reussi, et l'ecran doit pouvoir la refuser sans defaire ce qui
     * est ecrit. Un `Status.OFFERING` aurait fait de la contribution une etape de
     * l'ecriture, alors qu'elle en est la suite facultative.
     */
    private val offer = MutableStateFlow<CustomFoodUiState.Offering?>(null)

    val uiState: StateFlow<CustomFoodUiState> =
        combine(form, status, offer) { form, status, offer ->
            when {
                offer != null -> offer
                status == Status.EDITING -> CustomFoodUiState.Editing(form, saving = false)
                status == Status.SAVING -> CustomFoodUiState.Editing(form, saving = true)
                status == Status.FAILED -> CustomFoodUiState.Error(form)
                else -> CustomFoodUiState.Saved(checkNotNull(saved))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = CustomFoodUiState.Editing(form.value, saving = false),
        )

    private var saved: FoodId? = null

    init {
        viewModelScope.launch {
            form.collect { current -> if (saved == null) memory.keep(CustomFoodMemory.of(current)) }
        }
    }

    fun onNameChange(value: String) = form.update { it.copy(name = value) }

    fun onBrandChange(value: String) = form.update { it.copy(brand = value) }

    fun onServingChange(value: String) = form.update { it.copy(serving = value) }

    fun onMacroChange(macro: Macro, value: String) = form.update { it.copy(macros = it.macros + (macro to value)) }

    /** Après un échec d'écriture : la saisie n'a pas bougé, il n'y a qu'à réessayer. */
    fun onRetry() {
        status.value = Status.EDITING
    }

    fun onSave() {
        val draft = form.value.toDraft()
        if (!draft.complete || status.value == Status.SAVING) return

        status.value = Status.SAVING
        viewModelScope.launch {
            val written = runCatching { saveCustomFood(draft) }
            saved = written.getOrNull()
            // Enregistree, la fiche n'a plus rien a rendre.
            if (saved != null) memory.forget()
            status.value = if (written.isSuccess) Status.SAVED else Status.FAILED
            // La proposition est cherchee **apres** l'ecriture, jamais avant : une
            // fiche qui n'a pas ete enregistree n'a rien a offrir, et l'echec de la
            // question ne doit pas faire echouer l'enregistrement.
            saved?.let { id ->
                runCatching { offerContribution(id) }.getOrNull()?.let { contribution ->
                    offer.value = CustomFoodUiState.Offering(id, contribution)
                }
            }
        }
    }

    /**
     * L'envoi, une fois la proposition acceptee.
     *
     * L'ecran reste ouvert pendant et apres : ce qui part est public et definitif, et
     * se refermer sur un ecran de recherche ne dirait pas si c'est parti.
     */
    fun onContribute() {
        val pending = offer.value ?: return
        if (pending.sending) return

        offer.value = pending.copy(sending = true)
        viewModelScope.launch {
            val issue = runCatching { sendContribution(pending.contribution) }
                .getOrDefault(ContributionOutcome.Unreachable)
            offer.value = offer.value?.copy(sending = false, outcome = issue)
        }
    }

    /** Refuser, ou fermer apres coup : la fiche reste enregistree dans les deux cas. */
    fun onDeclineContribution() {
        offer.value = null
    }

    private enum class Status { EDITING, SAVING, SAVED, FAILED }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** Distincte des arguments de route, qui vivent dans le même `SavedStateHandle`. */
        const val MEMORY_KEY = "hexavore.customfood.memory"
    }
}

/**
 * Le formulaire, sous une forme qu'Android range avec l'écran ([D166][decisions]).
 *
 * Du texte, comme le formulaire : « 12, » revient « 12, ». Les compteurs par leur nom.
 *
 * [decisions]: docs/11-decisions.md
 */
@Serializable
internal data class CustomFoodMemory(
    val name: String = "",
    val brand: String = "",
    val serving: String = "",
    val macros: Map<String, String> = emptyMap(),
) {
    fun toForm(barcode: Barcode?) = CustomFoodForm(
        name = name,
        brand = brand,
        serving = serving,
        macros = macros.mapKeys { (macro, _) -> Macro.valueOf(macro) },
        barcode = barcode,
    )

    companion object {
        fun of(form: CustomFoodForm) = CustomFoodMemory(
            name = form.name,
            brand = form.brand,
            serving = form.serving,
            macros = form.macros.mapKeys { (macro, _) -> macro.name },
        )
    }
}

/** Ce que le formulaire porte, en texte. */
internal data class CustomFoodForm(
    val name: String = "",
    val brand: String = "",
    val serving: String = "",
    val macros: Map<Macro, String> = emptyMap(),
    /** Lu, jamais tapé : il s'affiche et ne s'édite pas. */
    val barcode: Barcode? = null,
) {
    fun toDraft(): CustomFoodDraft = CustomFoodDraft(
        name = name,
        brand = brand,
        barcode = barcode,
        // Un champ vide vaut inconnu, jamais zero. C'est la regle du projet,
        // appliquee la ou elle est le plus facile a trahir : il suffirait de lire
        // un champ vide comme « 0 » pour eviter tout traitement du cas nul.
        per100g = Macro.entries.fold(NutrientValues()) { values, macro ->
            values.with(macro, number(macros[macro].orEmpty()))
        },
        defaultServingG = number(serving),
    )

    val complete: Boolean get() = toDraft().complete
}

/** Où en est le formulaire. */
internal sealed interface CustomFoodUiState {
    data class Editing(val form: CustomFoodForm, val saving: Boolean) : CustomFoodUiState

    data class Error(val form: CustomFoodForm) : CustomFoodUiState

    /** Enregistré : l'écran se referme sur la fiche, qui devient une ligne de saisie. */
    data class Saved(val id: FoodId) : CustomFoodUiState

    /**
     * Enregistré, **et il y a quelque chose à offrir**.
     *
     * L'écran ne se referme pas encore : il montre ce qui partirait, et attend. C'est
     * le seul moment où la question a un sens — la fiche vient d'être créée pour un
     * produit que le scan n'a pas trouvé, donc pour un produit qui manque à Open Food
     * Facts ([D91][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    data class Offering(
        val id: FoodId,
        val contribution: FoodContribution,
        val sending: Boolean = false,
        /** Ce que le service a répondu, quand il a répondu. */
        val outcome: ContributionOutcome? = null,
    ) : CustomFoodUiState
}

/**
 * Un nombre tel qu'un clavier français le produit.
 *
 * La virgule est acceptée au même titre que le point. Une chaîne vide rend `null` :
 * le champ n'a pas été renseigné, ce qui n'est pas zéro.
 */
private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
