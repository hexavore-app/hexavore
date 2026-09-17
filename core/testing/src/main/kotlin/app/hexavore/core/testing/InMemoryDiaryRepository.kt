package app.hexavore.core.testing

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.food.FoodCitations
import app.hexavore.domain.food.FoodId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Le journal, en mémoire.
 *
 * Première implémentation de [DiaryRepository], pas une béquille : l'accueil est
 * écrit contre elle avant que Room n'existe, ce qui confronte les contrats du
 * domaine à un écran réel pendant qu'ils sont encore faciles à corriger. Room la
 * remplace en changeant une ligne du module Hilt, et aucun appelant ne s'en aperçoit
 * — c'est précisément la propriété que cette classe sert à démontrer.
 *
 * Elle survit à ce remplacement : les tests d'écran continuent de s'en servir.
 *
 * **Elle porte aussi [FoodCitations]**, et pas par commodité : le compte des
 * citations se dérive des entrées de journal, donc de ce que cette classe contient
 * déjà. Il vivait dans le faux du catalogue, qui n'a pas de journal et ne pouvait
 * l'exposer qu'en carte posée à la main — un faux plus indulgent que le vrai, ce que
 * [D53][decisions] proscrit. Adossé ici, il ne peut plus mentir : y répondre
 * autrement demanderait d'ignorer les plats qu'on vient d'écrire ([D71][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/12-plan-de-developpement.md
 */
class InMemoryDiaryRepository(initial: List<Dish> = emptyList()) :
    DiaryRepository,
    FoodCitations {
    private val state = MutableStateFlow(initial)

    /** Le contenu courant, pour qu'un test vérifie ce qui a été écrit. */
    val dishes: List<Dish> get() = state.value

    /**
     * L'échec que la prochaine opération doit produire, ou `null` pour fonctionner.
     *
     * Room peut échouer — disque plein, base illisible — et un écran qui ne sait
     * pas le dire affiche une journée vide à la place. Ce champ existe pour que ce
     * cas soit **testable** : sans lui, la seule façon de l'éprouver serait de
     * corrompre une vraie base.
     *
     * Il est lu à chaque appel et à chaque abonnement, et non une fois pour toutes,
     * pour qu'un test puisse rétablir la lecture et vérifier qu'une nouvelle
     * tentative aboutit.
     */
    var failure: Throwable? = null

    override fun observeDay(date: LocalDate): Flow<List<Dish>> {
        failure?.let { cause -> return flow { throw cause } }
        return state.asStateFlow().map { dishes ->
            dishes
                .filter { it.date == date }
                .sortedBy { it.loggedAt }
        }
    }

    /**
     * La meme lecture, sur une plage.
     *
     * Le faux **filtre comme le vrai**, bornes incluses des deux cotes : le contrat
     * porte sur cette convention, et un faux plus indulgent la laisserait diverger
     * sans que rien ne tombe ([D53][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Dish>> {
        failure?.let { cause -> return flow { throw cause } }
        return state.asStateFlow().map { dishes ->
            dishes
                .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
                .sortedWith(compareBy({ it.date }, { it.loggedAt }))
        }
    }

    override suspend fun dish(id: DishId): Dish? {
        failure?.let { throw it }
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun save(dish: Dish) {
        failure?.let { throw it }
        state.update { dishes -> dishes.filterNot { it.id == dish.id } + dish }
    }

    override suspend fun deleteDish(id: DishId) {
        failure?.let { throw it }
        state.update { dishes -> dishes.filterNot { it.id == id } }
    }

    override suspend fun unlinkFavorite(favorite: FavoriteDishId) {
        failure?.let { throw it }
        // Les lignes ne bougent pas : ce qui tombe est la provenance, pas le repas.
        state.update { dishes ->
            dishes.map { if (it.favoriteId == favorite) it.copy(favoriteId = null) else it }
        }
    }

    /**
     * Compté sur les lignes réellement présentes, jamais sur une réserve à part.
     *
     * C'est toute la raison d'être de ce port : deux lignes du même repas qui citent
     * la fiche comptent deux, et un plat supprimé fait redescendre le compte, parce
     * que la source est la même que celle que le reste de la classe sert.
     */
    override suspend fun count(id: FoodId): Int {
        failure?.let { throw it }
        return state.value.sumOf { dish -> dish.entries.count { it.foodId == id } }
    }

    /** Remplace tout le contenu. Les observateurs reçoivent immédiatement la suite. */
    fun setContent(dishes: List<Dish>) {
        state.value = dishes
    }
}
