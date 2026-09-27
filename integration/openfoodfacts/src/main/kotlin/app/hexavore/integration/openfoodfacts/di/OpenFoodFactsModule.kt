package app.hexavore.integration.openfoodfacts.di

import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.food.ContributionSettings
import app.hexavore.domain.food.FoodContributionTarget
import app.hexavore.domain.food.ProductSearch
import app.hexavore.domain.food.ProductSource
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.time.Clock
import app.hexavore.integration.openfoodfacts.ClientIdentity
import app.hexavore.integration.openfoodfacts.ContributionApi
import app.hexavore.integration.openfoodfacts.OPEN_FOOD_FACTS_BASE_URL
import app.hexavore.integration.openfoodfacts.OpenFoodFactsApi
import app.hexavore.integration.openfoodfacts.OpenFoodFactsContributor
import app.hexavore.integration.openfoodfacts.OpenFoodFactsProducts
import app.hexavore.integration.openfoodfacts.contributionApi
import app.hexavore.integration.openfoodfacts.openFoodFactsApi
import app.hexavore.integration.openfoodfacts.openFoodFactsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Ce que ce module lie : un port du domaine, une implémentation, et rien qui sorte
 * d'ici.
 *
 * Le client, le convertisseur et l'API restent **internes** : `:app` ne voit que
 * [ProductSource], et un écran encore moins. Exposer `OkHttpClient` aurait fait de ce
 * module la porte d'entrée réseau du projet, et le premier appelant pressé y aurait
 * branché autre chose.
 *
 * Le module ne fait qu'appeler le montage — il est **la portée**, pas la recette.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object OpenFoodFactsModule {
    @Provides
    @Singleton
    fun client(identity: ClientIdentity): OkHttpClient = openFoodFactsClient(identity)

    @Provides
    @Singleton
    fun api(client: OkHttpClient): OpenFoodFactsApi = openFoodFactsApi(OPEN_FOOD_FACTS_BASE_URL, client)

    /**
     * **Une seule instance pour deux ports.** Les deux interrogent le même service
     * avec le même client ; les séparer côté domaine sert les appelants — l'écran de
     * scan ne voit que le code-barres — pas le nombre d'objets.
     */
    @Provides
    @Singleton
    fun products(
        api: OpenFoodFactsApi,
        languages: ContentLanguages,
        ids: IdGenerator,
        clock: Clock,
        dispatchers: DispatcherProvider,
    ): OpenFoodFactsProducts = OpenFoodFactsProducts(api, languages, ids, clock, dispatchers)

    @Provides
    fun productSource(products: OpenFoodFactsProducts): ProductSource = products

    @Provides
    fun productSearch(products: OpenFoodFactsProducts): ProductSearch = products

    /**
     * L'écriture, montée sur **le même client** que la lecture.
     *
     * L'intercepteur d'identification y est donc, et c'est le point : Open Food Facts
     * bloque les clients anonymes, et une écriture qui l'oublierait échouerait sous
     * les traits d'une panne réseau ([D26][decisions]).
     *
     * L'URL de base passée ici est formelle — l'appel porte la sienne, parce qu'un
     * réglage peut le faire viser le bac à sable sans rien reconstruire.
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    @Singleton
    fun contributionApi(client: OkHttpClient): ContributionApi = contributionApi(OPEN_FOOD_FACTS_BASE_URL, client)

    /**
     * La cible de contribution, qui lit le réglage à chaque envoi.
     *
     * **À chaque envoi et non une fois pour toutes** : basculer sur le bac à sable
     * doit prendre effet sans redémarrer, sinon la vérification qu'il sert à faire
     * demanderait justement ce qu'on veut éviter.
     */
    @Provides
    @Singleton
    fun contributionTarget(
        api: ContributionApi,
        settings: ContributionSettings,
        dispatchers: DispatcherProvider,
    ): FoodContributionTarget = OpenFoodFactsContributor(
        api = api,
        dispatchers = dispatchers,
        sandbox = { settings.observe().first().sandbox },
    )
}
