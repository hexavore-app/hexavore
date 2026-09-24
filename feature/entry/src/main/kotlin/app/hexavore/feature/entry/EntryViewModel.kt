package app.hexavore.feature.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.diary.DaySummary
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.diary.impactOf
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.DraftOrigin
import app.hexavore.domain.usecase.GetDaySummary
import app.hexavore.domain.usecase.SaveDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L'écran de validation.
 *
 * Il ne sait pas d'où vient ce qu'il modifie. Un brouillon vierge, un plat relu, et
 * demain une proposition de modèle ou un produit scanné : tous arrivent sous la
 * forme d'un [app.hexavore.domain.diary.EntryDraft], et rien ici ne teste la
 * provenance. C'est ce qui évite d'avoir à généraliser cet écran trois fois.
 *
 * @see docs/12-plan-de-developpement.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class EntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val composition: DraftComposition,
    private val getDaySummary: GetDaySummary,
    private val saveDraft: SaveDraft,
    private val favorites: DraftFavorites,
    private val clock: Clock,
) : ViewModel() {
    private val dishId: DishId? = savedStateHandle.get<String>(EntryDestination.DISH_ID)?.let(::DishId)
    private val foodId: FoodId? = savedStateHandle.get<String>(EntryDestination.FOOD_ID)?.let(::FoodId)
    private val favoriteId: FavoriteDishId? =
        savedStateHandle.get<String>(EntryDestination.FAVORITE_ID)?.let(::FavoriteDishId)
    private val scannedFoodId: FoodId? =
        savedStateHandle.get<String>(EntryDestination.SCANNED_FOOD_ID)?.let(::FoodId)
    private val proposal: Boolean = savedStateHandle.get<Boolean>(EntryDestination.PROPOSAL) == true
    private val editingFavorite: Boolean = savedStateHandle.get<Boolean>(EntryDestination.EDITING_FAVORITE) == true

    private val form = MutableStateFlow<EntryForm?>(null)
    private val status = MutableStateFlow(Status.LOADING)

    /**
     * L'image de ce brouillon, et le souvenir de l'avoir retirée.
     *
     * **Deux champs et non un**, parce que `null` y dirait deux choses : « ce plat n'a
     * jamais eu de photo » et « on vient de la retirer ». Le second doit effacer un
     * fichier à l'enregistrement, le premier n'a rien à effacer, et les confondre
     * ferait relire le dossier à chaque sauvegarde.
     *
     * **Rien n'est effacé avant l'enregistrement.** Annuler doit rendre l'écran comme
     * il était, photo comprise.
     */
    private val photo = MutableStateFlow<PhotoFile?>(null)
    private var photoRemoved = false

    /**
     * L'étoile et sa boîte de nom, avec leur état.
     *
     * Elles vivent à part parce qu'elles sont à part : ce `ViewModel` porte la saisie
     * d'un repas, [DraftNaming] porte le modèle qu'on en tire. Elle écrit dans le même
     * formulaire — c'est elle qui sait qu'un favori enregistré le rattache.
     */
    val favorite = DraftNaming(favorites, viewModelScope, form)

    /**
     * La journée visée, relue une seule fois.
     *
     * Elle ne dépend que de la date, jamais du contenu des champs : sans ce
     * `distinctUntilChanged`, chaque frappe relancerait une lecture de la base pour
     * un chiffre qui n'a pas bougé.
     */
    private val day: Flow<DaySummary?> =
        form
            .map { it?.date }
            .distinctUntilChanged()
            .flatMapLatest { date ->
                if (date == null) {
                    flowOf(null)
                } else {
                    // Un echec de lecture ne prive que du restant. Le brouillon,
                    // lui, reste saisissable et enregistrable : refuser la saisie
                    // parce qu'on n'a pas pu lire la journee serait perdre le repas
                    // pour une information de confort.
                    getDaySummary(date)
                        .map<DaySummary, DaySummary?> { it }
                        .catch { emit(null) }
                }
            }

    val uiState: StateFlow<EntryUiState> =
        combine(form, status, day, favorite.naming, composition.units()) { form, status, day, naming, units ->
            when {
                status == Status.UNAVAILABLE -> EntryUiState.Unavailable
                status == Status.SAVED -> EntryUiState.Saved
                form == null -> EntryUiState.Loading
                status == Status.FAILED -> EntryUiState.Error(form)
                else -> EntryUiState.Content(
                    form = form,
                    units = units,
                    impact = day?.impactOf(form.toDraft()),
                    saving = status == Status.SAVING,
                    favoriteNameTaken = naming.taken,
                    favoriteProposal = naming.proposal,
                    editingFavorite = editingFavorite,
                    today = clock.today(),
                )
            }
        }
            // La photo se greffe apres coup plutot que dans la combinaison : `combine`
            // s'arrete a cinq flux, et surtout elle ne concerne qu'un seul des cinq
            // etats. L'ajouter au-dessus garde la regle qui les distingue lisible.
            .combine(photo) { state, image ->
                if (state is EntryUiState.Content) state.copy(photo = image) else state
            }
            // Aucun `flowOn` ici, contrairement a l'accueil, et c'est deliberé.
            // Ce que produit ce flux a chaque frappe tient en une conversion de
            // quelques lignes et deux additions ; le passer sur un autre
            // dispatcher n'economise rien et coute une image de latence. Cette
            // image, c'est le curseur du champ qui la paie.
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = EntryUiState.Loading,
            )

    init {
        viewModelScope.launch { open() }
    }

    /**
     * La fiche que la recherche vient de rendre : elle s'ajoute au plat en cours.
     *
     * **Elle ne démarre pas un nouveau brouillon.** « Ajouter un aliment » rouvre la
     * même recherche que le bouton de l'accueil, et c'est ce qui rend les deux
     * gestes identiques à apprendre.
     *
     * L'écran la lui passe plutôt que de la lire ici : le `SavedStateHandle` d'un
     * `ViewModel` et celui d'une entrée de pile sont deux objets différents, et
     * l'observer d'ici revenait à écouter une clé que personne ne remplissait
     * ([D52][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onFoodPicked(id: FoodId) {
        viewModelScope.launch {
            val line = composition.line(id) ?: return@launch
            // Ajouter une ligne detache du favori : le plat n'est plus celui que le
            // favori decrit (D62).
            form.update { current ->
                current?.copy(lines = current.lines + EntryFormLine.of(line), favoriteId = null)
            }
        }
    }

    fun onLineEdit(id: DraftLineId, edit: LineEdit) {
        form.update { current -> current?.update(id) { line -> line.apply(edit) } }
    }

    fun onRemoveLine(id: DraftLineId) {
        // Retirer une ligne detache aussi du favori : le plat n'est plus celui que
        // le favori decrit (D62).
        form.update { current -> current?.copy(lines = current.lines.filterNot { it.id == id }, favoriteId = null) }
    }

    /**
     * Le titre du plat, tel qu'on vient de l'écrire.
     *
     * Aucune retenue sur le lien au favori, contrairement aux gestes sur les lignes :
     * renommer un plat ne change pas ce qu'il contient, donc il reste celui que le
     * favori décrit ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onTitle(text: String) {
        form.update { it?.copy(title = text.trim().takeIf(String::isNotEmpty)) }
    }

    /**
     * Une pastille de moment a été touchée.
     *
     * **Elle efface le titre écrit**, sans quoi le nom affiché ne changerait pas et la
     * pastille semblerait sans effet. C'est le geste de quelqu'un qui dit « c'était le
     * dîner » : ce qu'il veut est le nom du dîner.
     */
    fun onMoment(moment: MealMoment) {
        form.update { it?.copy(moment = moment, title = null) }
    }

    /** Après un échec d'écriture : le brouillon n'a pas bougé, il n'y a qu'à réessayer. */
    fun onRetry() {
        status.value = Status.EDITING
    }

    /**
     * La croix sur la photo.
     *
     * **Elle n'efface rien tout de suite.** Annuler la modification doit rendre le plat
     * tel qu'il était ; ce n'est qu'à l'enregistrement que le fichier part.
     */
    fun onRemovePhoto() {
        photoRemoved = true
        photo.value = null
    }

    /**
     * Enregistrer, et ce que le mot veut dire ici.
     *
     * **Deux sens pour un bouton**, selon d'où l'on vient. Le cas courant note un repas
     * au journal ; celui qui vient de la liste des favoris réécrit le **modèle**, sans
     * rien ajouter à la journée — on est venu corriger, pas manger.
     */
    fun onSave() {
        val current = form.value ?: return
        val draft = current.toDraft()
        if (!draft.saveable || status.value == Status.SAVING) return

        status.value = Status.SAVING
        viewModelScope.launch {
            val written =
                runCatching { if (editingFavorite) favorites.rewrite(draft).let { null } else saveDraft(draft) }
            // La photo apres le plat, et seulement s'il en reste un : l'ecriture du
            // journal est ce qui compte, et un fichier qui ne se range pas ne doit pas
            // faire echouer un repas deja note. Un favori reecrit ne rend aucun plat,
            // donc ne range aucune image : ce n'est pas un repas.
            written.getOrNull()?.let { dish -> runCatching { composition.attachPhoto(dish, !photoRemoved) } }
            status.value = if (written.isSuccess) Status.SAVED else Status.FAILED
        }
    }

    private suspend fun open() {
        val origin = origin(proposal, dishId, favoriteId, scannedFoodId, foodId)
        val relu = composition.open(origin)
        // Meme quand le plat a disparu : c'est cet appel qui ecarte l'image qu'une
        // analyse abandonnee aurait laissee dans le depot.
        photo.value = runCatching { composition.photo(origin) }.getOrNull()

        if (relu == null) {
            status.value = Status.UNAVAILABLE
        } else {
            form.value = EntryForm.of(relu)
            status.value = Status.EDITING
        }
    }

    /**
     * Là où en est l'écran.
     *
     * Séparé du contenu du formulaire, parce que les deux ne changent pas pour les
     * mêmes raisons : le formulaire bouge à chaque frappe, l'étape seulement aux
     * moments qui comptent.
     */
    private enum class Status {
        LOADING,
        EDITING,
        SAVING,
        SAVED,

        /** Le plat visé n'a pas pu être rouvert : supprimé, ou illisible. */
        UNAVAILABLE,
        FAILED,
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Ce que la destination désigne. Un seul endroit qui lit les cinq arguments.
 *
 * Hors de la classe : c'est une lecture d'arguments de route, pas une capacité de
 * l'écran, et le seuil de fonctions n'a pas à compter ce qui n'est pas un geste.
 */
private fun origin(
    proposal: Boolean,
    dishId: DishId?,
    favoriteId: FavoriteDishId?,
    scannedFoodId: FoodId?,
    foodId: FoodId?,
): DraftOrigin = when {
    proposal -> DraftOrigin.Proposed
    dishId != null -> DraftOrigin.Dish(dishId)
    favoriteId != null -> DraftOrigin.Favorite(favoriteId)
    scannedFoodId != null -> DraftOrigin.Scanned(scannedFoodId)
    else -> DraftOrigin.New(foodId)
}
