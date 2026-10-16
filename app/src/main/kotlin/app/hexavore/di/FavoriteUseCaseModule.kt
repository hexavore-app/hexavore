package app.hexavore.di

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.food.FoodLookup
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.GetDishDraft
import app.hexavore.domain.usecase.GetFavoriteDraft
import app.hexavore.domain.usecase.ObserveFavoriteDishes
import app.hexavore.domain.usecase.ProposeFavoriteName
import app.hexavore.domain.usecase.RemoveFavoriteDish
import app.hexavore.domain.usecase.SaveFavoriteDish
import app.hexavore.domain.usecase.ToggleDishFavorite
import app.hexavore.domain.usecase.UpdateDish
import app.hexavore.domain.usecase.UpdateFavoriteDish
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage des **plats favoris**.
 *
 * Un troisième module de cas d'usage, pour la même raison que [GoalUseCaseModule] :
 * le seuil de fonctions de detekt se franchit vite, et la réponse du projet est de
 * découper selon ce que les choses sont plutôt que de relever le seuil. L'ouverture d'un
 * brouillon, qui vivait ici, a rejoint [DraftUseCaseModule] quand les favoris ont gagné
 * leur lecture nommée (D167).
 */
@Module
@InstallIn(SingletonComponent::class)
object FavoriteUseCaseModule {
    @Provides
    fun saveFavoriteDish(favorites: FavoriteDishes, ids: IdGenerator): SaveFavoriteDish =
        SaveFavoriteDish(favorites, ids)

    /** Le titre du plat, et le premier rang libre s'il est déjà pris : « Déjeuner 2 ». */
    @Provides
    fun proposeFavoriteName(favorites: FavoriteDishes): ProposeFavoriteName = ProposeFavoriteName(favorites)

    /**
     * La modification d'un favori, qui touche aux deux : le modèle et la provenance
     * des plats qui le citaient.
     */
    @Provides
    fun updateFavoriteDish(favorites: FavoriteDishes, diary: DiaryRepository): UpdateFavoriteDish =
        UpdateFavoriteDish(favorites, diary)

    @Provides
    fun toggleDishFavorite(
        drafts: GetDishDraft,
        update: UpdateDish,
        save: SaveFavoriteDish,
        remove: RemoveFavoriteDish,
    ): ToggleDishFavorite = ToggleDishFavorite(drafts, update, save, remove)

    @Provides
    fun removeFavoriteDish(favorites: FavoriteDishes): RemoveFavoriteDish = RemoveFavoriteDish(favorites)

    /** Les favoris, nommés comme leurs fiches se nomment aujourd'hui (D167). */
    @Provides
    fun observeFavoriteDishes(favorites: FavoriteDishes, foods: FoodLookup): ObserveFavoriteDishes =
        ObserveFavoriteDishes(favorites, foods)

    @Provides
    fun getFavoriteDraft(
        favorites: FavoriteDishes,
        foods: FoodLookup,
        create: CreateDraft,
        ids: IdGenerator,
    ): GetFavoriteDraft = GetFavoriteDraft(favorites, foods, create, ids)
}
