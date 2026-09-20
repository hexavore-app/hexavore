package app.hexavore.feature.home

import androidx.compose.runtime.Immutable
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId

/**
 * Ce que l'accueil peut déclencher.
 *
 * Regroupées plutôt que passées une par une : la liste des plats est trois niveaux
 * plus bas que l'écran, et six lambdas transmises de main en main feraient de
 * chaque signature intermédiaire une liste de paramètres à rallonge.
 */
@Immutable
data class HomeActions(
    val onAddDish: () -> Unit,
    /**
     * Ouvre la modale de scan.
     *
     * Le troisième bouton et non le premier : « Ajouter » reste le geste principal,
     * parce que la recherche porte aussi la saisie manuelle et couvre donc tout ce
     * qui n'a pas de code-barres.
     */
    val onScan: () -> Unit,
    val onEditDish: (DishId) -> Unit,
    /**
     * Supprime le plat entier, ses *n* lignes avec lui.
     *
     * Atteint par l'appui long, et **confirmé par un dialogue** : il emporte *n*
     * lignes d'un coup et mérite d'être voulu ([D61][decisions]). La barre
     * d'annulation reste offerte ensuite — la confirmation évite l'accident, la barre
     * rattrape le regret. C'est désormais la seule suppression qui parte de l'accueil :
     * une ligne se retire en ouvrant le plat ([D117][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val onDeleteDish: (Dish) -> Unit,
    /**
     * Met le plat en favori sous ce nom, ou l'en retire quand [name] est `null`.
     *
     * Un seul rappel pour les deux sens : l'entrée de menu est une bascule, et deux
     * actions auraient obligé l'écran à choisir laquelle appeler alors que l'état du
     * plat le dit déjà.
     */
    val onToggleFavorite: (Dish, name: String?) -> Unit,
    /**
     * Ouvre l'écran d'IA — une photo, une phrase, ou les deux.
     *
     * **Appelée seulement quand une clé existe.** Sans clé, le bouton reste visible et
     * grisé, et l'appui ouvre l'explication plutôt que l'écran ([D73][decisions]) : un
     * mode d'IA caché ne s'apprend jamais, un mode d'IA qui échoue sans dire pourquoi
     * ne s'utilise qu'une fois.
     *
     * Un seul rappel là où il y en avait deux ([D120][decisions]) : les deux écrans
     * n'en font plus qu'un.
     *
     * [decisions]: docs/11-decisions.md
     */
    val onAnalyse: () -> Unit,
    /** Ouvre la liste des plats favoris, pour en rejouer un. */
    val onOpenFavorites: () -> Unit,
    val onUndo: () -> Unit,
    val onUndoExpired: () -> Unit,
    val onRetry: () -> Unit,
    /** Vers les cinq questions, tant qu'aucun objectif n'a été posé. */
    val onSetUpGoal: () -> Unit,
    /**
     * Vers « Profil et objectifs ».
     *
     * Directement, sans écran de réglages au-dessus : les quatre autres sections que
     * [docs/02][parcours] prévoit dépendent de tranches à venir, et un écran de transit
     * qui ne désigne qu'une destination est un écran de trop ([D59][decisions]).
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     * [decisions]: docs/11-decisions.md
     */
    val onOpenSettings: () -> Unit,
    /**
     * Ouvre les réglages **d'IA**, pas le hub.
     *
     * Une route à part parce que la destination est à part : quelqu'un qui appuie sur
     * l'appareil photo sans avoir de clé cherche l'endroit où en mettre une, pas la
     * liste des réglages. Le déposer sur le hub lui laisserait un choix de plus à faire
     * alors qu'il en faisait déjà un.
     */
    val onConfigureAi: () -> Unit,
    /**
     * Vers le journal de poids.
     *
     * Depuis la barre de l'accueil et non depuis les reglages : c'est un ecran qu'on
     * consulte, pas un ecran ou l'on regle quelque chose, et il porte la carte
     * d'ajustement -- qu'on ne va pas chercher a deux gestes de profondeur.
     */
    val onOpenWeight: () -> Unit,
)

/**
 * Les actions d'un ecran de journee, accueil ou jour passe.
 *
 * **Une seule construction pour les deux**, parce que ce sont les memes gestes sur le
 * meme contenu : supprimer une ligne, annuler, mettre en favori. Les recopier aurait
 * laisse les deux ecrans diverger au premier geste ajoute d'un seul cote.
 */
internal fun HomeRoutes.toActions(viewModel: HomeViewModel) = HomeActions(
    onAddDish = onAddDish,
    onScan = onScan,
    onAnalyse = onAnalyse,
    onEditDish = onEditDish,
    onDeleteDish = viewModel::onDeleteDish,
    onUndo = viewModel::onUndo,
    onUndoExpired = viewModel::onUndoExpired,
    onRetry = viewModel::retry,
    onSetUpGoal = onSetUpGoal,
    onOpenSettings = onOpenSettings,
    onConfigureAi = onConfigureAi,
    onToggleFavorite = viewModel::onToggleFavorite,
    onOpenFavorites = onOpenFavorites,
    onOpenWeight = onOpenWeight,
)
