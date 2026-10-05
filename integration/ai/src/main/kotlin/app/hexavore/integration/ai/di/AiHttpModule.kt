package app.hexavore.integration.ai.di

import app.hexavore.domain.ai.AiExchangeLog
import app.hexavore.domain.time.Clock
import app.hexavore.integration.ai.AnthropicApi
import app.hexavore.integration.ai.ExchangeInterceptor
import app.hexavore.integration.ai.GeminiApi
import app.hexavore.integration.ai.NetworkLog
import app.hexavore.integration.ai.OpenAiApi
import app.hexavore.integration.ai.aiClient
import app.hexavore.integration.ai.anthropicApi
import app.hexavore.integration.ai.geminiApi
import app.hexavore.integration.ai.openAiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

/**
 * La pile HTTP des fournisseurs d'IA : un client, trois interfaces.
 *
 * **Séparé de [AiModule]** parce que ce sont deux choses différentes — ici le
 * transport, là les ports du domaine et la fabrique qui les sert. Le découpage est
 * arrivé quand le seuil de fonctions a mordu, et il suit ce que les choses sont plutôt
 * qu'un compte : c'est la réponse du projet à ce seuil, jamais de le relever.
 *
 * **Trois interfaces pour six fournisseurs**, et c'est le bon compte : trois protocoles
 * existent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiHttpModule {
    /**
     * L'intercepteur qui note les echanges, branche toujours.
     *
     * Il ne consulte plus le reglage de mise au point : c'est le journal qui decide de
     * la profondeur qu'il garde, parce que c'est une question de retention (D153). Le
     * reconstruire au changement de reglage obligerait de toute facon a reconstruire le
     * client, donc les trois interfaces Retrofit, au milieu d'une analyse en cours.
     */
    @Provides
    @Singleton
    @Named(AI_EXCHANGES)
    fun exchanges(log: AiExchangeLog, clock: Clock): Interceptor = ExchangeInterceptor(log, clock)

    @Provides
    @Singleton
    @Named(AI_CLIENT)
    fun client(log: NetworkLog, @Named(AI_EXCHANGES) exchanges: Interceptor): OkHttpClient = aiClient(log, exchanges)

    @Provides
    @Singleton
    fun anthropic(@Named(AI_CLIENT) client: OkHttpClient): AnthropicApi = anthropicApi(client)

    @Provides
    @Singleton
    fun gemini(@Named(AI_CLIENT) client: OkHttpClient): GeminiApi = geminiApi(client)

    @Provides
    @Singleton
    fun openAi(@Named(AI_CLIENT) client: OkHttpClient): OpenAiApi = openAiApi(client)

    /**
     * Les trois interfaces, en un objet.
     *
     * Passées une par une à la fabrique, elles poussaient sa fonction au-delà du seuil
     * de paramètres.
     */
    @Provides
    @Singleton
    fun apis(anthropic: AnthropicApi, gemini: GeminiApi, openAi: OpenAiApi) = AiApis(anthropic, gemini, openAi)
}

/** Les trois protocoles que les six fournisseurs se partagent. */
internal data class AiApis(val anthropic: AnthropicApi, val gemini: GeminiApi, val openAi: OpenAiApi)

/**
 * Le qualificatif qui sépare ce client de celui d'Open Food Facts.
 *
 * Les deux modules fournissent un `OkHttpClient` dans le même composant, et sans
 * qualificatif Dagger refuse de choisir. Les partager serait pire que les séparer :
 * ils n'ont ni les mêmes délais ni les mêmes intercepteurs, et le `User-Agent`
 * d'Open Food Facts n'a rien à faire dans un appel payant.
 */
internal const val AI_CLIENT = "ai"

/** Ce qui distingue l'intercepteur de mise au point de tout autre `Interceptor` du graphe. */
internal const val AI_EXCHANGES = "ai-exchanges"
