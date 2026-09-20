package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodLookup
import app.hexavore.domain.identity.IdGenerator

/**
 * Rejoue un plat favori sous la forme que l'écran de validation manipule.
 *
 * **C'est ici que le favori tient sa promesse de modèle vivant.** Une ligne qui cite
 * une fiche est reconstruite depuis la fiche **courante**, ramenée à la quantité du
 * favori : corriger les valeurs de ses flocons corrige tous les petits-déjeuners à
 * venir, ce que [docs/07][modele] demandait.
 *
 * **Une fiche disparue ne fait pas disparaître la ligne.** Les valeurs enregistrées
 * avec le favori servent alors de repli, et la ligne rejoue ce qu'elle valait au jour
 * de l'enregistrement. Refuser la ligne, ou rejouer un plat amputé sans le dire,
 * seraient deux façons de perdre un favori pour un aliment supprimé.
 *
 * Le brouillon porte le lien vers son favori : c'est lui qui allume l'étoile, et il
 * tombera à la première ligne touchée ([D62][decisions]). **Il en porte aussi le nom**,
 * qui devient le titre du plat ([D118][decisions]) : c'est déjà le nom que l'utilisateur
 * a choisi pour ce contenu.
 *
 * **Le brouillon est fabriqué par [CreateDraft] et non ici.** C'est elle qui sait sur
 * quel jour un brouillon s'écrit — celui qu'on regarde, sinon aujourd'hui —, et le
 * favori le datait d'aujourd'hui pour avoir bâti le sien à la main. Rejouer un plat
 * depuis la veille l'enregistrait alors sur le jour courant, en silence
 * ([D111][decisions]).
 *
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
class GetFavoriteDraft(
    private val favorites: FavoriteDishes,
    private val foods: FoodLookup,
    private val create: CreateDraft,
    private val ids: IdGenerator,
) {
    /** @return `null` si le favori a été supprimé entre le choix et l'ouverture. */
    suspend operator fun invoke(id: FavoriteDishId): EntryDraft? {
        val favorite = favorites.byId(id) ?: return null

        val lines = favorite.components.map { it.toLine(DraftLineId(ids.next()), lookUp(it)) }

        // Composer son plat depuis ses favoris, c'est le composer soi-meme : il n'y a
        // rien de devine ici, donc rien qui merite une pastille a part.
        //
        // **Le favori donne son nom au plat.** « Flocons du matin » est deja le nom
        // que l'utilisateur a choisi pour ce contenu ; le rejouer sous « Petit-dejeuner »
        // perdrait ce qu'il avait ecrit, et la liste des plats ne dirait plus lequel de
        // ses modeles il a rejoue.
        return create(EntrySource.MANUAL, lines).copy(favoriteId = favorite.id, title = favorite.name)
    }

    /** La fiche citée, si elle existe encore et si la ligne en citait une. */
    private suspend fun lookUp(component: FavoriteComponent): Food? =
        component.foodId?.let { id -> runCatching { foods.byId(id) }.getOrNull() }
}

/**
 * Une ligne de brouillon, depuis la fiche vivante ou depuis les valeurs figées.
 *
 * Avec fiche : la ligne naît d'elle, puis `measured` la ramène à la quantité du
 * favori — ce qui recalcule les six valeurs sur les teneurs **actuelles**. Sans fiche :
 * les valeurs du favori sont posées telles quelles, et la référence de recalcul est
 * reconstruite depuis elles, exactement comme pour un plat relu.
 */
private fun FavoriteComponent.toLine(id: DraftLineId, food: Food?): DraftLine = when (food) {
    null -> DraftLine(
        id = id,
        foodId = foodId,
        name = name,
        quantity = quantity,
        unit = unit,
        values = values,
        reference = DraftLine.referenceOf(values, grams),
    )

    else -> DraftLine.of(id, food).measured(quantity, unit)
}
