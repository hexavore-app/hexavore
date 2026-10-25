package app.hexavore.di

import app.hexavore.domain.ai.NutritionEstimator
import app.hexavore.domain.ai.PendingProposal
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
        pending: PendingProposal,
    ): OpenDraft = OpenDraft(dishes, favorites, create, foods, pending)

    /**
     * La résolution d'une proposition, et le résolveur de libellés qu'elle chaîne.
     *
     * Toujours ici, bien que leur appelant soit désormais l'analyse et non plus
     * l'ouverture d'un brouillon (D176) : ce qu'elles fabriquent est un brouillon, et
     * c'est ce module qui dit comment on en obtient un.
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
