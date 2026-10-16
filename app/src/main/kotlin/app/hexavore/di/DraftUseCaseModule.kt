package app.hexavore.di

import app.hexavore.domain.ai.NutritionEstimator
import app.hexavore.domain.ai.PendingRecognition
import app.hexavore.domain.food.FoodLookup
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.usecase.AddFoodLine
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.GetDishDraft
import app.hexavore.domain.usecase.GetFavoriteDraft
import app.hexavore.domain.usecase.OpenDraft
import app.hexavore.domain.usecase.ResolveFoodLabel
import app.hexavore.domain.usecase.ResolveRecognition
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction de **l'ouverture d'un brouillon** : ce que l'écran de validation reçoit,
 * d'où qu'il vienne.
 *
 * Ces quatre fournisseurs vivaient avec les favoris, parce que le rejeu d'un favori est
 * l'une des entrées. Ils en sont sortis quand les favoris ont gagné un cas d'usage de
 * plus et que le seuil de fonctions a mordu (D167) : la réponse du projet est de
 * découper selon ce que les choses sont, et ceci est l'ouverture d'un brouillon.
 */
@Module
@InstallIn(SingletonComponent::class)
object DraftUseCaseModule {
    /**
     * Les quatre entrées de l'écran de validation, en un objet.
     *
     * Le rejeu d'un favori en est une, et vient de [FavoriteUseCaseModule] ;
     * l'ouverture d'un brouillon les rassemble toutes.
     */
    @Provides
    fun openDraft(
        dishes: GetDishDraft,
        favorites: GetFavoriteDraft,
        create: CreateDraft,
        foods: FoodLookup,
        pending: PendingRecognition,
        resolve: ResolveRecognition,
    ): OpenDraft = OpenDraft(dishes, favorites, create, foods, pending, resolve)

    /**
     * La résolution d'une proposition, et le résolveur de libellés qu'elle chaîne.
     *
     * Les deux ici parce qu'ils n'ont qu'un appelant — l'ouverture d'un brouillon — et
     * que `ResolveFoodLabel` n'a jamais eu de fournisseur : il attendait depuis sa
     * livraison quelqu'un pour l'appeler.
     */
    @Provides
    fun resolveRecognition(
        resolve: ResolveFoodLabel,
        create: CreateDraft,
        estimate: NutritionEstimator,
        languages: ContentLanguages,
    ): ResolveRecognition = ResolveRecognition(resolve, create, estimate, languages)

    @Provides
    fun resolveFoodLabel(foods: FoodSearch, languages: ContentLanguages): ResolveFoodLabel =
        ResolveFoodLabel(foods, languages)

    @Provides
    fun addFoodLine(foods: FoodLookup, create: CreateDraft): AddFoodLine = AddFoodLine(foods, create)
}
