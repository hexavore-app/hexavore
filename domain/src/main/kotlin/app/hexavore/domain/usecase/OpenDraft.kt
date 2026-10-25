package app.hexavore.domain.usecase

import app.hexavore.domain.ai.PendingProposal
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodLookup

/**
 * Ce que l'écran de validation ouvre : un plat, un favori, une fiche, un produit
 * scanné, ou rien.
 *
 * **Les entrées de l'écran, en un seul objet.** C'est le point de convergence
 * que [docs/12][plan] désigne depuis la conception, et il n'existait jusqu'ici que
 * comme une suite de branches dans le `ViewModel` : chaque nouveau mode de saisie en
 * ajoutait une, et la tranche 6 en apporte encore deux. Ici, il y a **un** endroit qui
 * sait d'où peut venir un brouillon — la tranche 5 l'a vérifié en en ajoutant une
 * cinquième sans toucher au `ViewModel` de l'écran.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
sealed interface DraftOrigin {
    /** Un plat déjà enregistré, qu'on rouvre pour le corriger. */
    data class Dish(val id: DishId) : DraftOrigin

    /** Un plat favori, qu'on rejoue. */
    data class Favorite(val id: FavoriteDishId) : DraftOrigin

    /** Une saisie neuve, éventuellement préremplie d'une fiche choisie ailleurs. */
    data class New(val food: FoodId? = null) : DraftOrigin

    /**
     * Un produit lu au code-barres, déjà versé au catalogue par `LookupBarcode`.
     *
     * Distinct de [New] malgré la même charge — un identifiant de fiche — parce que
     * ce n'est pas la même **source** : un plat scanné porte `BARCODE`, et cette
     * origine ne se réécrit jamais ([D32][decisions]). Les confondre effacerait la
     * seule trace de la façon dont le plat est entré dans le journal.
     *
     * [decisions]: docs/11-decisions.md
     */
    data class Scanned(val food: FoodId) : DraftOrigin

    /**
     * Ce qu'un modèle vient de proposer, déjà résolu, déposé par l'analyse.
     *
     * **Sans charge**, seule des cinq : la proposition ne tient pas dans une route
     * ([PendingProposal]), et l'origine ne fait que dire où aller la chercher. Une
     * cinquième branche ici plutôt qu'un chemin à part, parce que c'est exactement la
     * même question — d'où vient ce brouillon — et que l'écran de validation ne doit
     * pas apprendre à en distinguer deux sortes.
     */
    data object Proposed : DraftOrigin
}

/**
 * Ouvre le brouillon que désigne une origine.
 *
 * `null` quand ce qui est désigné n'existe plus — plat supprimé pendant qu'on
 * l'ouvrait, favori retiré entre le choix et l'ouverture. Le cas n'est pas théorique :
 * la suppression est immédiate depuis l'accueil.
 *
 * **Une saisie neuve, elle, ne rend jamais `null`.** Une fiche introuvable donne un
 * brouillon vierge plutôt qu'un écran d'erreur : il reste plus utile de saisir à la
 * main que de repartir de zéro, et la ligne vide dit déjà qu'il n'y a rien.
 *
 * **Aucune origine n'appelle un modèle** ([D176][decisions]). Ouvrir est une lecture —
 * une base, un dépôt —, et c'est ce qui permet à l'écran de validation de s'ouvrir
 * aussitôt. L'estimation d'une proposition se faisait ici, et l'écran restait noir le
 * temps qu'elle revienne ; cette classe n'a plus de quoi la faire.
 *
 * [decisions]: docs/11-decisions.md
 */
class OpenDraft(
    private val dishes: GetDishDraft,
    private val favorites: GetFavoriteDraft,
    private val create: CreateDraft,
    private val foods: FoodLookup,
    private val pending: PendingProposal,
) {
    suspend operator fun invoke(origin: DraftOrigin): EntryDraft? = when (origin) {
        is DraftOrigin.Dish -> dishes(origin.id)
        is DraftOrigin.Favorite -> favorites(origin.id)
        is DraftOrigin.New -> newDraft(origin.food)
        is DraftOrigin.Scanned -> scannedDraft(origin.food)
        DraftOrigin.Proposed -> proposedDraft()
    }

    /**
     * Le brouillon d'une proposition, **tel que l'analyse l'a résolu**.
     *
     * La résolution reste en un seul endroit, `ResolveRecognition`, et c'est l'analyse
     * qui l'appelle depuis [D176][decisions] : le premier libellé introuvable n'a donc
     * toujours qu'un comportement possible.
     *
     * `null` quand le dépôt est vide — une deuxième ouverture, ou un processus
     * redémarré entre les deux écrans. C'est le même `null` qu'un plat supprimé
     * pendant qu'on l'ouvrait, et il mène au même écran : il n'y a rien à valider.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun proposedDraft(): EntryDraft? = pending.take()

    /**
     * `null` si la fiche a disparu, là où [newDraft] rendrait un brouillon vierge.
     *
     * La différence est voulue : une saisie neuve reste utile sans fiche — on tape à
     * la main — alors qu'un brouillon de scan sans son produit n'a plus rien à
     * valider, et il porterait une pastille « code-barres » sur un plat que personne
     * n'a scanné.
     */
    private suspend fun scannedDraft(food: FoodId): EntryDraft? = runCatching { foods.byId(food) }
        .getOrNull()
        ?.let { fiche -> create(EntrySource.BARCODE, fiche) }

    /**
     * La source est la même dans les deux cas : chercher un aliment ou taper ses
     * valeurs, c'est composer son plat soi-même. Ce qui mérite une pastille à part est
     * ce qu'un modèle a **proposé**, et ça n'existe pas encore.
     */
    private suspend fun newDraft(food: FoodId?): EntryDraft = food
        ?.let { id -> runCatching { foods.byId(id) }.getOrNull() }
        ?.let { fiche -> create(EntrySource.MANUAL, fiche) }
        ?: create(EntrySource.MANUAL)
}

/**
 * Ajoute au plat en cours une ligne bâtie sur une fiche choisie dans la recherche.
 *
 * **Elle ne démarre pas un nouveau brouillon.** « Ajouter un aliment » rouvre la même
 * recherche que le bouton de l'accueil, et c'est ce qui rend les deux gestes identiques
 * à apprendre.
 *
 * `null` quand la fiche a disparu entre le choix et le retour : la ligne n'est alors
 * pas ajoutée, plutôt que d'entrer vide dans un plat qu'on croyait complété.
 */
class AddFoodLine(private val foods: FoodLookup, private val create: CreateDraft) {
    suspend operator fun invoke(id: FoodId): DraftLine? =
        runCatching { foods.byId(id) }.getOrNull()?.let { create.line(it) }
}
