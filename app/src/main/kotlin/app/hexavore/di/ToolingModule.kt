package app.hexavore.di

import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.usecase.LookUpCandidates
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Le catalogue, tel qu un modele peut l interroger.
 *
 * Un module a part, et ici plutot que dans `:integration:ai` : c est `:app` qui
 * assemble, et l integration d IA n a pas a connaitre le cas d usage qui la sert -- ni
 * le port de recherche sur lequel il repose.
 */
@Module
@InstallIn(SingletonComponent::class)
object ToolingModule {
    @Provides
    fun catalogueTool(foods: FoodSearch, languages: ContentLanguages): CatalogueTool =
        LookUpCandidates(foods, languages)
}
