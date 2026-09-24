package app.hexavore.data.diary

import app.hexavore.core.database.dao.CalendarDao
import app.hexavore.core.database.dao.DiaryDao
import app.hexavore.core.database.dao.FavoriteDishDao
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Le journal, lu et écrit dans Room.
 *
 * Seconde implémentation du port `DiaryRepository`, après celle en mémoire. Le
 * passage de l'une à l'autre n'a demandé qu'une ligne dans le module Hilt de
 * `:app`, et aucun appelant ne s'en est aperçu — c'est cette propriété, et non le
 * nombre de modules, qui dit si le découpage tient.
 *
 * Aucune agrégation ici : le DAO rend les lignes entières et le domaine totalise.
 * Un `SUM` en SQL traiterait `NULL` comme absent, ce qui est arithmétiquement juste
 * mais perdrait l'information qu'une valeur manquait.
 *
 * L'horloge sert aux seules colonnes techniques — `created_at`, `updated_at` — que
 * le domaine ne porte pas et n'a aucune raison de porter : ce sont des faits sur
 * l'écriture, pas sur le repas. Elle reste injectée pour la même raison qu'ailleurs,
 * et parce qu'une règle detekt l'exige.
 *
 * @see docs/06-architecture.md
 * @see docs/11-decisions.md — D29
 */
class RoomDiaryRepository @Inject constructor(
    private val dao: DiaryDao,
    private val calendar: CalendarDao,
    private val favorites: FavoriteDishDao,
    private val clock: Clock,
) : DiaryRepository {
    override fun observeDay(date: LocalDate): Flow<List<Dish>> =
        dao.observeDay(date.toString()).map { dishes -> dishes.map { it.toDomain() } }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Dish>> =
        calendar.observeRange(from.toString(), to.toString()).map { dishes -> dishes.map { it.toDomain() } }

    override suspend fun dish(id: DishId): Dish? = dao.dish(id.value)?.toDomain()

    override suspend fun dishIds(): Set<DishId> = dao.dishIds().mapTo(mutableSetOf(), ::DishId)

    override suspend fun save(dish: Dish) {
        val now = clock.now().toEpochMilli()
        dao.saveDish(dish.toEntity(now), dish.entries.map { it.toEntity(now) })
    }

    override suspend fun deleteDish(id: DishId) = dao.deleteDish(id.value)

    override suspend fun unlinkFavorite(favorite: FavoriteDishId) = favorites.unlinkFromDishes(favorite.value)
}
