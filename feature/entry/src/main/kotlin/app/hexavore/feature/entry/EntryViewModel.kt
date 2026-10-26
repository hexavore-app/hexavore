package app.hexavore.feature.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.core.ui.FormMemory
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
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.onStart
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
    private val dishActions: DraftFiling,
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
     * Le brouillon tel qu'Android l'avait quand il a tué l'application ([D166][decisions]).
     *
     * Relu **avant** toute ouverture depuis la route : une proposition d'IA ne s'ouvre
     * qu'une fois — le dépôt se vide à la première lecture —, et la rouvrir après la mort
     * du processus rendait un écran « plus disponible » à la place du repas payé.
     *
     * [decisions]: docs/11-decisions.md
     */
    private val memory = FormMemory(savedStateHandle, MEMORY_KEY, EntryMemory.serializer())

    /**
     * Le formulaire tel qu'il s'est ouvert.
     *
     * Il ne sert qu'à une question : quitter perdrait-il quelque chose sur un plat déjà
     * écrit ([losesOnExit], [D161][decisions]) ?
     *
     * [decisions]: docs/11-decisions.md
     */
    private var opened: EntryForm? = null

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
     * Combien de fois l'image a été choisie à la main sur cet écran.
     *
     * **Un compteur et non un booléen** : le dépôt n'a qu'un emplacement, donc deux
     * images choisies de suite portent le même chemin, et le composant qui les affiche
     * mémorise son décodage par chemin. Ce qui change doit donc changer à **chaque**
     * choix, pas une seule fois ([D156][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    private val photoChoices = MutableStateFlow(0)

    /**
     * L'étoile et sa boîte de nom, avec leur état.
     *
     * Elles vivent à part parce qu'elles sont à part : ce `ViewModel` porte la saisie
     * d'un repas, [DraftNaming] porte le modèle qu'on en tire. Elle écrit dans le même
     * formulaire — c'est elle qui sait qu'un favori enregistré le rattache.
     */
    val favorite = DraftNaming(favorites, viewModelScope, form)

    /**
     * Les trois gestes de l'en-tete, derriere un porteur.
     *
     * Comme [favorite], et pour la meme raison : ils forment un tout, et les
     * laisser au premier plan poussait ce `ViewModel` au-dela du seuil de
     * fonctions -- un seuil qui dit precisement qu'une classe fait trop de
     * choses a la fois (D143).
     */
    val filing = DraftHeaderActions(dishActions, dishId, viewModelScope, form, photo, status)

    /**
     * La journée visée, relue une seule fois.
     *
     * Elle ne dépend que de la date, jamais du contenu des champs : sans ce
     * `distinctUntilChanged`, chaque frappe relancerait une lecture de la base pour
     * un chiffre qui n'a pas bougé.
     *
     * **Le brouillon n'attend pas la journée** ([D181][decisions]). Elle ne se lit
     * qu'une fois la date connue, donc après le brouillon : deux lectures de la base à
     * la suite, et l'écran restait vide tout ce temps, à chaque ouverture. Il paraît
     * désormais dès que le brouillon est prêt, et le restant le rejoint.
     *
     * [decisions]: docs/11-decisions.md
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
                        .onStart { emit(null) }
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
            .combine(photoChoices) { state, choices ->
                if (state is EntryUiState.Content) state.copy(photoRevision = choices) else state
            }
            // La copie se greffe de la meme facon, et pour la meme raison : c'est une
            // nouvelle a annoncer une fois, pas une sixieme piece de l'etat.
            .combine(filing.copied) { state, jour ->
                if (state is EntryUiState.Content) state.copy(copiedTo = jour) else state
            }
            // Et l'echec d'un signalement, pour la meme raison : une nouvelle a dire une
            // fois, pas une piece de plus de l'etat (D153).
            .combine(filing.reportFailed) { state, rate ->
                if (state is EntryUiState.Content) state.copy(reportFailed = rate) else state
            }
            // Ce que quitter perdrait, en dernier : la regle lit le formulaire, l'image,
            // et le souvenir de l'avoir retiree, que rien d'autre ne lit (D161).
            .map { state ->
                if (state is EntryUiState.Content) {
                    state.copy(unsaved = state.losesOnExit(opened, photoRemoved))
                } else {
                    state
                }
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

    /**
     * L'ouverture du brouillon, gardée pour qu'une photo puisse l'attendre ([D163][decisions]).
     *
     * Ouvrir un brouillon qui ne vient ni d'une analyse ni d'un scan **vide le dépôt des
     * photos**. Quand Android a tué Hexavore pendant la prise de vue, l'image revient à
     * un écran recréé, donc pendant cette ouverture : déposée d'abord, elle était jetée
     * juste après.
     *
     * [decisions]: docs/11-decisions.md
     */
    private val opening: Job = viewModelScope.launch {
        // Le brouillon garde d'abord ; la route seulement s'il n'y en a pas, ou qu'il ne
        // se relit plus. La photo ne repasse pas par l'ouverture, qui viderait le depot ou
        // elle attend : elle ne revient que si le depot ou le plat la portent encore.
        val kept = memory.recall()?.let { runCatching { it.recalled() }.getOrNull() }
        if (kept == null) {
            open()
        } else {
            opened = kept.opened
            form.value = kept.form
            photoRemoved = kept.photoRemoved
            photo.value = kept.photo?.let { runCatching { composition.keptPhoto(it, kept.form.dishId) }.getOrNull() }
            photoChoices.value = kept.photoChoices
            status.value = Status.EDITING
        }
    }

    init {
        // Apres l'ouverture et a part d'elle : la garde ne finit jamais, et une photo
        // choisie attend la fin de l'ouverture, pas celle de l'ecran. Le souvenir d'avoir
        // retire l'image se lit au passage : il ne change jamais sans que l'image change.
        viewModelScope.launch {
            opening.join()
            combine(form, photo, photoChoices) { current, image, choices ->
                current?.takeIf { status.value != Status.SAVED }?.let {
                    EntryMemory(it.toMemory(), opened?.toMemory(), image?.path, photoRemoved, choices)
                }
            }.collect { kept -> kept?.let(memory::keep) }
        }
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
     * L'image retirée.
     *
     * **Elle n'efface rien tout de suite.** Annuler la modification doit rendre le plat
     * tel qu'il était ; ce n'est qu'à l'enregistrement que le fichier part.
     */
    fun onRemovePhoto() {
        photoRemoved = true
        photo.value = null
    }

    /**
     * L'image que l'utilisateur vient de prendre ou de choisir.
     *
     * **Elle efface le souvenir d'avoir retiré la précédente**, et c'est la ligne qui
     * compte : sans elle, retirer une image puis en choisir une autre déposerait la
     * nouvelle et l'enregistrement la jetterait aussitôt, parce qu'il croirait obéir à
     * la suppression ([D156][decisions]).
     *
     * **Rien n'est attaché au plat ici** : l'image va au dépôt, et l'enregistrement la
     * range. Un écran qu'on quitte sans enregistrer ne laisse donc que le dépôt, qu'une
     * ouverture suivante écarte d'elle-même.
     *
     * Le réglage « garder les photos » n'est pas consulté : il dit *n'en garde pas
     * automatiquement*, pas *refuse celles que je te donne*.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onPhotoChosen(jpeg: ByteArray) {
        viewModelScope.launch {
            opening.join()
            val staged = runCatching { composition.choosePhoto(jpeg) }.getOrNull() ?: return@launch
            photoRemoved = false
            photo.value = staged
            photoChoices.update { it + 1 }
        }
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
            // `favoriteId` vient de la route et non du brouillon : le lien que celui-ci
            // porte tombe a la premiere ligne touchee (D62), c'est-a-dire au geste meme
            // qu'on vient faire ici (D135).
            val written = runCatching {
                val editing = favoriteId.takeIf { editingFavorite }
                if (editing != null) favorites.rewrite(draft, editing).let { null } else saveDraft(draft)
            }
            // La photo apres le plat, et seulement s'il en reste un : l'ecriture du
            // journal est ce qui compte, et un fichier qui ne se range pas ne doit pas
            // faire echouer un repas deja note. Un favori reecrit ne rend aucun plat,
            // donc ne range aucune image : ce n'est pas un repas.
            written.getOrNull()?.let { dish -> runCatching { composition.attachPhoto(dish, !photoRemoved) } }
            // Ecrit, le brouillon n'a plus rien a rendre : le garder ferait reparaitre un
            // repas deja note si Android recreait l'ecran avant qu'il se ferme.
            if (written.isSuccess) memory.forget()
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
            val ouvert = EntryForm.of(relu)
            opened = ouvert
            form.value = ouvert
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
    internal enum class Status {
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

        /** Distincte des arguments de route, qui vivent dans le même `SavedStateHandle`. */
        const val MEMORY_KEY = "hexavore.entry.memory"
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

/**
 * Quitter l'écran perdrait-il quelque chose ? ([D161][decisions])
 *
 * **Deux questions, selon ce que le brouillon prolonge.** Un repas pas encore noté
 * n'existe nulle part ailleurs : tout ce qu'il porte se perd, même intact — c'est le cas
 * d'une proposition d'IA, payée, et qui ne se rejoue pas. Un plat déjà écrit, ou le
 * favori qu'on corrige, est en sécurité dans la base : seul se perd ce qui a changé
 * depuis l'ouverture.
 *
 * Une ligne vierge ne compte pas : c'est celle qu'ouvre une saisie neuve, et demander
 * confirmation pour quitter un formulaire vide serait une question sans objet.
 *
 * Hors de la classe, comme [origin] : c'est une règle sur l'état, pas un geste de
 * l'écran, et le seuil de fonctions n'a pas à la compter.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun EntryUiState.Content.losesOnExit(opened: EntryForm?, photoRemoved: Boolean): Boolean =
    if (form.dishId == null && !editingFavorite) {
        photo != null || form.toDraft().lines.any { !it.blank }
    } else {
        form != opened || photoRemoved || photoRevision > 0
    }
