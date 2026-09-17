package app.hexavore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.activeConfiguration
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.SelectedDay
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.usecase.AdjustmentResponse
import app.hexavore.domain.usecase.FavoriteOutcome
import app.hexavore.domain.usecase.GetDaySummary
import app.hexavore.domain.usecase.RespondToAdjustment
import app.hexavore.domain.usecase.SuggestGoalAdjustment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * L'accueil : une journée, ses totaux, ses repas.
 *
 * Aucun calcul ici. Le cas d'usage produit un [app.hexavore.domain.diary.DaySummary]
 * complet et le ViewModel se contente de l'emballer dans un état d'écran — si un
 * calcul mérite un test, il appartient au domaine.
 *
 * L'agrégation tourne sur le dispatcher de calcul et non sur le fil principal :
 * une journée chargée peut contenir quelques dizaines de lignes aujourd'hui, et
 * beaucoup plus le jour où l'écran Journée affichera un mois.
 *
 * @see docs/06-architecture.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    getDaySummary: GetDaySummary,
    dispatchers: DispatcherProvider,
    credentials: AiCredentials,
    suggestGoalAdjustment: SuggestGoalAdjustment,
    private val respondToAdjustment: RespondToAdjustment,
    private val gestures: DishGestures,
    private val selected: SelectedDay,
) : ViewModel() {
    /**
     * Le déclencheur de relecture.
     *
     * Un flux qui a émis une exception est terminé : le relancer demande un nouvel
     * abonnement, pas un simple `retry` d'appel. Chaque valeur poussée ici en
     * fabrique un, et `flatMapLatest` abandonne le précédent.
     */
    private val attempts = MutableStateFlow(0)

    /**
     * La journee regardee, ou `null` pour aujourd'hui.
     *
     * **Elle vient d'un port et non plus d'un argument de route** : l'ecran Journee a
     * disparu, et l'accueil porte lui-meme la date. Le calendrier reste donc a l'ecran
     * pendant qu'on se promene dans l'historique, ce qu'un second ecran ne permettait
     * pas -- il le laissait derriere lui.
     *
     * `null` et non `clock.today()` : la date par defaut est evaluee a chaque lecture
     * par `GetDaySummary`, donc un ecran reste ouvert pendant la nuit ne continue pas
     * d'afficher la veille.
     */
    private val day: StateFlow<LocalDate?> = selected
        .observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, selected.current())

    /**
     * Y a-t-il une clé, oui ou non.
     *
     * **Un booléen et rien d'autre** : l'accueil n'a pas à savoir quel fournisseur est
     * actif, ni sous quel modèle. Ce qu'il décide est l'état du bouton « Décrire »
     * ([D73][decisions]) — visible et grisé sans clé, plutôt que caché —, et une
     * question binaire mérite une réponse binaire.
     *
     * Hors de [uiState] parce que ça n'en est pas un état : le journal peut être
     * illisible pendant qu'une clé est parfaitement configurée, et l'inverse tout
     * autant. Les fondre en un seul objet ferait deux échecs indépendants dans une
     * même hiérarchie.
     *
     * [decisions]: docs/11-decisions.md
     */
    val aiConfigured: StateFlow<Boolean> = credentials
        .observe()
        .map { it.activeConfiguration() != null }
        .catch { emit(false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = false,
        )

    /**
     * La correction que l'adaptation hebdomadaire propose, s'il y en a une.
     *
     * **Hors de [uiState]**, comme [aiConfigured] et pour la même raison : le journal
     * peut être illisible pendant que la suggestion tient parfaitement, et l'inverse
     * autant. `null` est le cas normal — toutes les conditions doivent être réunies
     * pour qu'une carte paraisse ([docs/03][calculs]).
     *
     * Elle n'est montrée que par l'accueil. L'écran Journée partage ce modèle, mais
     * une carte qui parle de trois semaines n'a rien à faire au-dessus d'un jour
     * passé qu'on est venu relire.
     *
     * [calculs]: docs/03-nutrition-calculs.md
     */
    val suggestion: StateFlow<AdjustmentSuggestion?> = suggestGoalAdjustment()
        // Une lecture qui echoue ne fabrique pas de conseil : le silence est deja
        // le cas normal, et c'est le bon repli.
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    val uiState: StateFlow<HomeUiState> =
        attempts
            .combine(day) { _, jour -> jour }
            .flatMapLatest { date ->
                // Les parentheses ne sont pas decoratives : sans elles, le `.map`
                // ne s'appliquerait qu'a la seconde branche.
                (if (date == null) getDaySummary() else getDaySummary(date))
                    .map<_, HomeUiState> { HomeUiState.Content(it) }
                    // `catch` **dans** le flatMapLatest, et c'est ce qui rend le
                    // bouton Reessayer utile. Un flux qui a rattrape une exception
                    // est termine : place a l'exterieur, il ferait terminer la
                    // source de `stateIn`, qui resterait alors bloquee sur Error
                    // quoi qu'on pousse dans `attempts`. Ici, seul le flux interne
                    // se termine ; celui des tentatives, lui, ne finit jamais.
                    .catch { emit(HomeUiState.Error) }
            }
            .flowOn(dispatchers.default)
            .stateIn(
                scope = viewModelScope,
                // La collecte s'arrête cinq secondes après le dernier observateur :
                // assez pour traverser une rotation sans relire la base, trop peu
                // pour qu'un écran quitté continue d'écouter en arrière-plan.
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = HomeUiState.Loading,
            )

    /**
     * Le plat à remettre si l'utilisateur annule.
     *
     * La suppression part **immédiatement** et c'est ce plat qui permet d'y revenir,
     * plutôt que de la retarder pendant les cinq secondes du `Snackbar`. Une
     * suppression différée disparaît si le processus est tué entre-temps, et
     * l'utilisateur retrouve alors une ligne qu'il croyait supprimée. Ici, le pire
     * cas est l'inverse : la ligne est bien partie, et c'est ce que l'écran montrait.
     */
    private val undoable = MutableStateFlow<Dish?>(null)

    private val nameTaken = MutableStateFlow(false)

    val pendingUndo: StateFlow<Dish?> = undoable.asStateFlow()

    /**
     * Répond à la suggestion affichée.
     *
     * La suggestion vient de l'état et non de l'écran : entre l'affichage et l'appui,
     * une pesée a pu changer la correction, et écrire celle que l'écran tenait
     * écrirait un chiffre périmé. `null` si elle a disparu entre-temps — il n'y a
     * alors rien à accepter.
     */
    fun onAdjustment(response: AdjustmentResponse) {
        val shown = suggestion.value ?: return
        viewModelScope.launch { respondToAdjustment(response, shown) }
    }

    /**
     * Le jour affiché, tel que l'écran le montre. `null` : aujourd'hui.
     *
     * Exposé à part de [uiState] : il est connu **avant** que le journal ne soit lu, et
     * le titre ne doit pas attendre une base pour s'écrire.
     */
    val selectedDay: StateFlow<LocalDate?> = day

    /**
     * Change le jour affiché. `null` revient à aujourd'hui.
     *
     * Le calendrier ne bouge pas : c'est le contenu en dessous qui se recharge. C'est
     * toute la raison pour laquelle l'écran Journée a disparu.
     *
     * Toucher la pastille d'aujourd'hui range `null` — mais c'est le port qui le
     * décide, pas cet écran : la règle est une propriété du jour regardé.
     */
    fun onSelectDay(date: LocalDate?) {
        selected.select(date)
    }

    /** Relit le journal après un échec. */
    fun retry() {
        attempts.update { it + 1 }
    }

    /**
     * Supprime le plat entier.
     *
     * Le plat part **immédiatement**, et c'est lui qui permet d'y revenir : une
     * suppression différée pendant les cinq secondes de la barre disparaîtrait avec un
     * processus tué, et l'utilisateur retrouverait un plat qu'il croyait supprimé. La
     * confirmation a déjà été demandée par l'écran ; la barre qui suit ne protège plus de l'accident mais du regret, et
     * elle ne coûte rien puisque `RestoreDish` remet le plat et ses lignes en place.
     */
    fun onDeleteDish(dish: Dish) {
        viewModelScope.launch {
            runCatching { gestures.deleteDish(dish.id) }
                // Pas d'annulation a proposer sur un echec : rien n'a ete supprime.
                .onSuccess { undoable.value = dish }
        }
    }

    /**
     * Bascule le plat dans les favoris, ou l'en retire.
     *
     * Le nom vient de l'écran, qui le fait saisir ; `null` veut dire « retire-le ».
     * Un nom déjà pris remonte dans [favoriteNameTaken] plutôt que par une valeur de
     * retour, pour que la boîte reste ouverte avec le nom refusé dedans.
     */
    fun onToggleFavorite(dish: Dish, name: String?) {
        nameTaken.value = false
        viewModelScope.launch {
            val outcome = runCatching { gestures.toggleFavorite(dish, name) }.getOrNull()
            nameTaken.value = outcome is FavoriteOutcome.NameTaken
        }
    }

    /** Le nom proposé était déjà pris : l'écran le dit et laisse corriger. */
    val favoriteNameTaken: StateFlow<Boolean> get() = nameTaken.asStateFlow()

    fun onDismissFavoriteError() {
        nameTaken.value = false
    }

    fun onUndo() {
        val dish = undoable.value ?: return
        undoable.value = null
        viewModelScope.launch { runCatching { gestures.restoreDish(dish) } }
    }

    /** La fenêtre d'annulation est passée. Le plat cesse d'être rattrapable. */
    fun onUndoExpired() {
        undoable.value = null
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
