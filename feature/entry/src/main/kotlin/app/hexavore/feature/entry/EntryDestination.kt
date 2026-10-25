package app.hexavore.feature.entry

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.food.FoodId
import kotlinx.serialization.Serializable

/**
 * L'écran de validation, comme destination.
 *
 * `dishId` nul veut dire « nouvelle saisie » ; `foodId` renseigné veut dire qu'elle
 * démarre sur une fiche choisie ailleurs.
 *
 * **Ce sont deux identités, pas deux modes.** L'écran ne reçoit toujours rien qui
 * lui permette de brancher sur la provenance : il ne lit ni l'un ni l'autre, c'est
 * le `ViewModel` qui en fait un brouillon, et le brouillon reste la seule chose que
 * l'écran connaisse. C'est ce qui évite d'avoir à le généraliser à chaque mode.
 *
 * Des identifiants plutôt que le brouillon lui-même : une route est sérialisée dans
 * l'état de navigation, et y faire transiter un plat entier reviendrait à en tenir
 * une seconde copie que rien ne tiendrait à jour.
 *
 * **Ce chemin s'arrête à une ligne.** La photo de la tranche 6 en produira cinq, et
 * une route ne les portera pas : il faudra un brouillon en attente, partagé entre
 * l'écran qui le produit et celui qui le valide. Ajouter ici un argument par mode
 * serait la première marche vers l'écran à quatre branches que le projet refuse.
 */
@Serializable
data class EntryDestination(
    val dishId: String? = null,
    val foodId: String? = null,
    /** Le favori à rejouer, quand la saisie part de la liste des favoris. */
    val favoriteId: String? = null,
    /**
     * La fiche d'un produit **scanné**, déjà versée au catalogue.
     *
     * Un argument de plus que [foodId] pour la même charge, et ce n'est pas une
     * redite : ce qui diffère est la **source** du plat, `BARCODE` au lieu de
     * `MANUAL`, et elle ne se réécrit jamais ([D32][decisions]). Une seule route
     * pour les deux obligerait l'écran à deviner d'où vient la fiche, c'est-à-dire à
     * connaître les modes de saisie — précisément ce qu'il ne fait pas.
     *
     * [decisions]: docs/11-decisions.md
     */
    val scannedFoodId: String? = null,
    /**
     * `true` quand le brouillon attend dans le dépôt des propositions.
     *
     * **Un drapeau et non une charge**, seul de tous les arguments de cette route :
     * ce qu'un modèle a proposé ne tient pas dans un état de navigation, et
     * `PendingProposal` le porte à côté, déjà résolu (D176). C'est ce que le paragraphe ci-dessus
     * annonçait — « il faudra un brouillon en attente, partagé entre l'écran qui le
     * produit et celui qui le valide » —, et ça n'a pas ajouté de mode à l'écran :
     * une cinquième origine, comme les quatre autres.
     */
    val proposal: Boolean = false,
    /**
     * `true` quand on vient **modifier le favori lui-même**, et non le rejouer.
     *
     * Le brouillon est le même — c'est le contenu du favori —, mais l'enregistrement
     * change de sens : il réécrit le modèle et n'ajoute aucun repas au journal. Un
     * drapeau plutôt qu'une seconde destination, parce que c'est le même écran, la
     * même liste de lignes et la même étoile ; seul le bouton d'enregistrement répond
     * à une autre question.
     */
    val editingFavorite: Boolean = false,
) {
    companion object {
        /**
         * Le nom sous lequel l'argument arrive dans le `SavedStateHandle`.
         *
         * Lu dans le descripteur du sérialiseur plutôt que recopié : c'est le nom
         * de la propriété ci-dessus, et le recopier créerait deux endroits à
         * renommer là où le compilateur n'en signalerait qu'un.
         *
         * Pourquoi ne pas passer par `toRoute<EntryDestination>()`, qui donnerait
         * l'objet entier : son décodeur construit un `android.os.Bundle`, donc il
         * exige un appareil ou Robolectric. Un `ViewModel` qu'on ne peut pas
         * instancier dans un test JVM pour lire un argument est un mauvais échange.
         */
        internal val DISH_ID: String = EntryDestination::dishId.name

        /** Idem, pour la fiche d'où part une saisie neuve. */
        internal val FOOD_ID: String = EntryDestination::foodId.name

        /** Idem, pour le favori qu'une saisie rejoue. */
        internal val FAVORITE_ID: String = EntryDestination::favoriteId.name

        /** Idem, pour le mode qui modifie le favori au lieu de le rejouer. */
        internal val EDITING_FAVORITE: String = EntryDestination::editingFavorite.name

        /** Idem, pour le produit qu'un scan vient de verser au catalogue. */
        internal val SCANNED_FOOD_ID: String = EntryDestination::scannedFoodId.name

        /** Idem, pour la proposition qui attend dans le dépôt. */
        internal val PROPOSAL: String = EntryDestination::proposal.name

        /**
         * La clé sous laquelle la recherche dépose la fiche choisie pour cet écran.
         *
         * **Elle se lit sur le `NavBackStackEntry`, jamais dans le `ViewModel`.** Le
         * `SavedStateHandle` qu'un `ViewModel` reçoit et celui que porte l'entrée de
         * pile sont deux objets **différents**, construits chacun de leur côté
         * depuis le même registre : écrire dans l'un ne se voit pas dans l'autre. Le
         * `ViewModel` observait donc une clé que personne ne remplissait, et
         * « Ajouter un aliment » ne faisait rien ([D52][decisions]).
         *
         * Les arguments de route, eux, passent bien par les deux : ils sont dans le
         * `Bundle` d'arguments par défaut. C'est pourquoi le **premier** aliment
         * arrivait et pas les suivants.
         *
         * Ce chemin ne porte **qu'une** fiche, et c'est tout ce qu'on lui demande :
         * un brouillon entier passe par le dépôt des propositions, pas par ici.
         *
         * [decisions]: docs/11-decisions.md
         */
        const val PICKED_FOOD: String = "picked_food"
    }
}

/** Ouvre une saisie neuve, ou la modification d'un plat existant. */
fun NavController.navigateToEntry(dishId: DishId? = null) {
    navigate(EntryDestination(dishId = dishId?.value))
}

/** Ouvre une saisie neuve, préremplie depuis une fiche d'aliment. */
fun NavController.navigateToEntryForFavorite(favoriteId: FavoriteDishId) {
    navigate(EntryDestination(favoriteId = favoriteId.value))
}

/**
 * Ouvre le **favori lui-même**, pour le corriger.
 *
 * Même écran, même contenu ; ce qui change est ce qu'enregistrer veut dire.
 */
fun NavController.navigateToFavoriteEditor(favoriteId: FavoriteDishId) {
    navigate(EntryDestination(favoriteId = favoriteId.value, editingFavorite = true))
}

fun NavController.navigateToEntryFor(foodId: FoodId) {
    navigate(EntryDestination(foodId = foodId.value))
}

/**
 * Ouvre la validation sur ce qu'un modèle vient de proposer.
 *
 * Sans argument : la proposition est déjà déposée, et la nommer ici en ferait une
 * seconde copie.
 */
fun NavController.navigateToEntryForProposal() {
    navigate(EntryDestination(proposal = true))
}

/** Ouvre une saisie neuve sur un produit scanné : même écran, autre pastille. */
fun NavController.navigateToEntryForScan(foodId: FoodId) {
    navigate(EntryDestination(scannedFoodId = foodId.value))
}

/**
 * Déclare l'écran dans un graphe.
 *
 * Le module expose son entrée plutôt que sa composable : `:app` assemble des
 * graphes sans avoir à connaître les arguments de chaque écran.
 */
fun NavGraphBuilder.entryScreen(onAddFood: () -> Unit, onClose: () -> Unit) {
    composable<EntryDestination> { entry ->
        val handle = entry.savedStateHandle
        val picked by handle
            .getStateFlow<String?>(EntryDestination.PICKED_FOOD, null)
            .collectAsStateWithLifecycle()

        EntryRoute(
            pickedFood = picked?.let(::FoodId),
            onPickedFoodHandled = { handle[EntryDestination.PICKED_FOOD] = null },
            onAddFood = onAddFood,
            onClose = onClose,
        )
    }
}
