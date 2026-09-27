package app.hexavore.integration.ai.di

import android.content.Context
import app.hexavore.domain.ai.AiProbe
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.AiUsageLog
import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.NotingRecognizer
import app.hexavore.domain.ai.NutritionEstimator
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.notice.KeyRejection
import app.hexavore.integration.ai.AiPrompts
import app.hexavore.integration.ai.AnthropicRecognizer
import app.hexavore.integration.ai.AssetSystemPrompt
import app.hexavore.integration.ai.ConfiguredRecognizer
import app.hexavore.integration.ai.GeminiRecognizer
import app.hexavore.integration.ai.OpenAiCompatibleRecognizer
import app.hexavore.integration.ai.ProviderRecognizers
import app.hexavore.integration.ai.SystemPrompt
import app.hexavore.integration.ai.deepPromptAsset
import app.hexavore.integration.ai.estimatePromptAsset
import app.hexavore.integration.ai.extractPromptAsset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Ce que ce module lie : un port du domaine, et rien qui sorte d'ici.
 *
 * `:app` ne voit que [FoodRecognizer]. Le client, les DTO et les fournisseurs restent
 * internes — exposer `OkHttpClient` aurait fait de ce module la porte d'entrée réseau
 * du projet, et le premier appelant pressé y aurait branché autre chose.
 *
 * **[NetworkLog] vient de `:app`**, comme `ClientIdentity` pour Open Food Facts :
 * c'est l'application qui sait si elle est en `debug`, et [docs/05][ia] veut qu'un
 * journal réseau détaillé n'existe que là. Ce module n'a pas à connaître les
 * variantes de build.
 *
 * [ia]: docs/05-ia.md
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiModule {
    @Provides
    @Singleton
    @Named(EXTRACT_PROMPT)
    fun systemPrompt(@ApplicationContext context: Context, languages: ContentLanguages): SystemPrompt =
        AssetSystemPrompt(context, languages, ::extractPromptAsset)

    @Provides
    @Singleton
    @Named(ESTIMATE_PROMPT)
    fun estimatePrompt(@ApplicationContext context: Context, languages: ContentLanguages): SystemPrompt =
        AssetSystemPrompt(context, languages, ::estimatePromptAsset)

    @Provides
    @Singleton
    @Named(DEEP_PROMPT)
    fun deepPrompt(@ApplicationContext context: Context, languages: ContentLanguages): SystemPrompt =
        AssetSystemPrompt(context, languages, ::deepPromptAsset)

    /**
     * Les trois prompts, en un objet.
     *
     * Le même remède que pour les trois interfaces Retrofit : passés un par un, ils
     * poussaient la fabrique au-delà du seuil de paramètres. Et le regroupement dit
     * quelque chose de vrai — ce sont les trois textes que les fournisseurs partagent,
     * là où le reste de leurs dépendances leur est propre.
     */
    @Provides
    @Singleton
    fun prompts(
        @Named(EXTRACT_PROMPT) extract: SystemPrompt,
        @Named(ESTIMATE_PROMPT) estimate: SystemPrompt,
        @Named(DEEP_PROMPT) deep: SystemPrompt,
    ) = AiPrompts(extract, estimate, deep)

    /**
     * **Une seule instance pour deux ports.** Analyser et sonder empruntent le même
     * chemin — c'est ce qui rend le bouton « Tester » digne de confiance —, et deux
     * objets auraient fini par diverger d'un délai ou d'une traduction de code.
     */
    @Provides
    @Singleton
    fun configured(
        settings: AiSettings,
        languages: ContentLanguages,
        usage: AiUsageLog,
        catalogue: CatalogueTool,
        apis: AiApis,
        prompts: AiPrompts,
        dispatchers: DispatcherProvider,
    ): ConfiguredRecognizer = ConfiguredRecognizer(
        settings = settings,
        languages = languages,
        usage = usage,
        catalogue = catalogue,
        providers = ProviderRecognizers(
            anthropic = AnthropicRecognizer(apis.anthropic, prompts, dispatchers),
            gemini = GeminiRecognizer(apis.gemini, prompts, dispatchers),
            // Deux instances d'une meme classe, et la difference tient en un booleen :
            // OpenAI prend un schema complet, les trois autres ne promettent que du JSON.
            openAi = openAiLike(apis, prompts, dispatchers, strictSchema = true),
            compatible = openAiLike(apis, prompts, dispatchers, strictSchema = false),
        ),
    )

    /**
     * Le reconnaisseur, **enveloppe** de ce qui retient un refus de clé.
     *
     * La décoration est ici et non chez l'appelant : les deux écrans d'IA passent par
     * ce port, et le troisieme qui viendra aussi. Un décorateur pose la règle une fois ;
     * deux lignes dans deux `ViewModel` l'auraient posée deux fois et oubliée la
     * troisième.
     */
    @Provides
    fun recognizer(configured: ConfiguredRecognizer, rejection: KeyRejection): FoodRecognizer =
        NotingRecognizer(configured, rejection)

    @Provides
    fun probe(configured: ConfiguredRecognizer): AiProbe = configured

    /** Le repli de l'étape 4, servi par le même objet et donc par le même fournisseur. */
    @Provides
    fun estimator(configured: ConfiguredRecognizer): NutritionEstimator = configured
}

/**
 * Les deux qualificatifs des prompts.
 *
 * Ils ne sont pas interchangeables et ne partent pas dans les mêmes appels : sans eux,
 * deux `SystemPrompt` nus se seraient laissés intervertir en silence.
 */
private const val EXTRACT_PROMPT = "extract"
private const val ESTIMATE_PROMPT = "estimate"

/** Le troisieme prompt : celui qui parle des deux outils. */
private const val DEEP_PROMPT = "deep"

/**
 * Les deux fournisseurs qui parlent la langue d OpenAI.
 *
 * Une fabrique nommee plutot que deux appels recopies : la difference tient en un
 * booleen, et deux lignes identiques a un mot pres se corrigent une fois sur deux.
 */
private fun openAiLike(apis: AiApis, prompts: AiPrompts, dispatchers: DispatcherProvider, strictSchema: Boolean) =
    OpenAiCompatibleRecognizer(apis.openAi, prompts.extract, prompts.estimate, dispatchers, strictSchema)
