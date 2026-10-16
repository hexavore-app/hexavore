package app.hexavore.domain.usecase

import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.food.FoodLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Les plats favoris, leurs composants nommés **comme leur fiche se nomme aujourd'hui**
 * ([D167][decisions]).
 *
 * Un favori fige le nom de chaque composant le jour où on l'enregistre, et les deux listes
 * l'affichaient tel quel ; l'éditeur, lui, relit la fiche vivante. Après un changement de
 * langue, la liste disait « Rice, white, cooked » et l'éditeur « Riz blanc, cuit » pour le
 * même plat. Le nom affiché est désormais celui de l'éditeur : le titre court de la fiche
 * s'il existe, sinon son libellé, dans la langue en cours.
 *
 * **Le nom figé reste le repli** : un composant tapé à la main n'a pas de fiche, et une
 * fiche supprimée depuis n'en a plus. Une lecture qui échoue retombe sur lui aussi — un
 * nom d'hier vaut mieux qu'une liste qui ne s'affiche pas.
 *
 * [decisions]: docs/11-decisions.md
 */
class ObserveFavoriteDishes(private val favorites: FavoriteDishes, private val foods: FoodLookup) {
    operator fun invoke(): Flow<List<FavoriteDish>> =
        favorites.observeAll().map { all -> all.map { favorite -> favorite.copy(components = favorite.named()) } }

    private suspend fun FavoriteDish.named(): List<FavoriteComponent> = components.map { component ->
        val live = component.foodId?.let { id -> runCatching { foods.byId(id) }.getOrNull() }
        live?.let { component.copy(name = it.displayName) } ?: component
    }
}
