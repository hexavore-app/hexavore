package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.TokenUsage
import app.hexavore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64

/**
 * Une classe pour quatre fournisseurs — OpenAI, DeepSeek, Mistral, et tout ce qui
 * parle comme eux.
 *
 * **Ce n'est pas une économie de lignes, c'est un constat** : les quatre envoient le
 * même JSON à la même route et lisent la même réponse. Quatre classes auraient fait
 * quatre endroits à corriger pour un champ, et — plus grave — auraient laissé croire
 * que le quatrième est un fournisseur alors que c'est **une porte** : une URL de base
 * et un nom de modèle suffisent à y brancher OpenRouter, Groq, un Ollama du réseau
 * local ou un service qui n'existe pas encore ([docs/05][ia] § Fournisseurs).
 *
 * **La seule variation est [strictSchema]**, et elle est un paramètre plutôt qu'un
 * `when` sur le fournisseur : la fabrique est le seul endroit du projet qui ait le
 * droit de savoir qui est qui ([docs/12][plan]), et un `when` ici l'aurait dupliquée.
 * OpenAI accepte un schéma complet ; les autres ne promettent que « du JSON », et
 * c'est le prompt qui porte alors la forme — il la décrit déjà, avec un exemple.
 *
 * **Ni température ni pénalités.** La régularité vient du schéma chez les six
 * fournisseurs ; deux réglages pour la même intention finiraient par diverger.
 *
 * [ia]: docs/05-ia.md
 * [plan]: docs/12-plan-de-developpement.md
 */
internal class OpenAiCompatibleRecognizer(
    private val api: OpenAiApi,
    private val prompt: SystemPrompt,
    private val estimatePrompt: SystemPrompt,
    private val dispatchers: DispatcherProvider,
    private val strictSchema: Boolean,
) : ProviderRecognizer {
    override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
        withContext(dispatchers.io) {
            try {
                api
                    .chatCompletions(
                        configuration.chatEndpoint(),
                        BEARER + configuration.apiKey.value,
                        configuration.chatRequest(input, prompt, strictSchema),
                    )
                    .toChatOutcome()
            } catch (timeout: SocketTimeoutException) {
                timeout.reducedTo(AiError.Timeout)
            } catch (offline: IOException) {
                offline.reducedTo(AiError.NoNetwork)
            }
        }

    override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
        withContext(dispatchers.io) {
            try {
                api
                    .chatCompletions(
                        configuration.chatEndpoint(),
                        BEARER + configuration.apiKey.value,
                        configuration.chatEstimateRequest(labels, estimatePrompt, strictSchema),
                    )
                    .toChatEstimation()
            } catch (timeout: SocketTimeoutException) {
                timeout.estimationReducedTo(AiError.Timeout)
            } catch (offline: IOException) {
                offline.estimationReducedTo(AiError.NoNetwork)
            }
        }
}

private fun AiConfiguration.chatEstimateRequest(labels: List<String>, prompt: SystemPrompt, strictSchema: Boolean) =
    OpenAiRequest(
        model = model,
        messages = listOf(
            OpenAiMessage(role = SYSTEM_ROLE, content = OpenAiContent.Text(prompt.text())),
            OpenAiMessage(role = USER_ROLE, content = OpenAiContent.Text(labels.asRequest())),
        ),
        responseFormat = responseFormat(strictSchema, ESTIMATION_SCHEMA_NAME) { estimationSchema(strict = true) },
        maxCompletionTokens = CHAT_MAX_TOKENS,
    )

private fun Response<OpenAiResponse>.toChatEstimation(): EstimationOutcome {
    val choice = body()?.choices?.firstOrNull()
    return when {
        !isSuccessful -> EstimationOutcome.Failed(chatError())
        choice == null -> EstimationOutcome.Failed(AiError.Unparseable)
        else -> parseEstimation(choice.message?.content.orEmpty(), body()?.usage?.toChatUsage())
    }
}

/**
 * `…/v1/chat/completions`, quelle que soit la façon dont l'utilisateur a saisi sa base.
 *
 * **Le `/v1` n'est pas ajouté s'il est déjà là.** C'est le premier fournisseur dont
 * l'URL se saisit vraiment à la main — les relais s'annoncent tantôt
 * `https://relais/v1`, tantôt `https://relais` —, et coller un second `/v1` rendrait
 * un `404` que personne ne rapporterait à cette ligne.
 */
private fun AiConfiguration.chatEndpoint(): String {
    val base = baseUrl.trimEnd('/')
    return if (base.endsWith(VERSION_PATH)) base + CHAT_PATH else base + VERSION_PATH + CHAT_PATH
}

private fun AiConfiguration.chatRequest(input: RecognitionInput, prompt: SystemPrompt, strictSchema: Boolean) =
    OpenAiRequest(
        model = model,
        messages = listOf(
            OpenAiMessage(role = SYSTEM_ROLE, content = OpenAiContent.Text(prompt.text())),
            OpenAiMessage(role = USER_ROLE, content = input.chatContent()),
        ),
        responseFormat = responseFormat(strictSchema, SCHEMA_NAME) { recognitionSchema(strict = true) },
        maxCompletionTokens = CHAT_MAX_TOKENS,
    )

/**
 * Le schéma quand le fournisseur le prend, « du JSON » sinon.
 *
 * `strict` va avec le schéma et pas sans lui : demander la rigueur sur un format qui
 * ne décrit rien n'aurait aucun sens, et certains relais refusent le champ.
 */
private fun responseFormat(strictSchema: Boolean, name: String, schema: () -> JsonObject): OpenAiResponseFormat = when {
    strictSchema -> OpenAiResponseFormat(
        type = JSON_SCHEMA_FORMAT,
        jsonSchema = OpenAiJsonSchema(name = name, schema = schema()),
    )

    else -> OpenAiResponseFormat(type = JSON_OBJECT_FORMAT)
}

/**
 * Une chaîne pour une description, un tableau dès qu'il y a une image.
 *
 * L'image d'abord et la consigne ensuite, comme chez les deux autres fournisseurs et
 * pour la même raison : un modèle qui lit la demande avant d'avoir vu l'image la
 * rapporte moins bien à ce qu'il regarde.
 */
private fun RecognitionInput.chatContent(): OpenAiContent = when (this) {
    is RecognitionInput.Photo -> OpenAiContent.Parts(
        listOf(
            OpenAiPart(
                type = IMAGE_PART,
                imageUrl = OpenAiImageUrl(
                    JPEG_DATA_URI + Base64.getEncoder().encodeToString(jpeg),
                ),
            ),
            OpenAiPart(type = TEXT_PART, text = note?.let { "$CHAT_PHOTO_REQUEST\n\n$it" } ?: CHAT_PHOTO_REQUEST),
        ),
    )

    is RecognitionInput.Text -> OpenAiContent.Text(description)
}

private fun Response<OpenAiResponse>.toChatOutcome(): RecognitionOutcome {
    val choice = body()?.choices?.firstOrNull()
    return when {
        !isSuccessful -> RecognitionOutcome.Failed(chatError())
        choice == null -> RecognitionOutcome.Failed(AiError.Unparseable)
        choice.finishReason in INCOMPLETE_REASONS -> RecognitionOutcome.Failed(AiError.NothingRecognized)
        else -> parseRecognition(choice.message?.content.orEmpty(), body()?.usage?.toChatUsage())
    }
}

private fun OpenAiUsage.toChatUsage() = TokenUsage(input = promptTokens, output = completionTokens)

/**
 * Les codes, traduits comme partout ailleurs.
 *
 * `402` rejoint le quota, et le corps n'accompagne que le fourre-tout : c'est la même
 * règle que pour les deux premiers fournisseurs, et elle est **d'autant plus utile
 * ici** que l'URL de base est saisie à la main — un relais mal orthographié répond
 * n'importe quoi, et ce n'importe quoi est la seule chose qui puisse le dire.
 */
private fun Response<OpenAiResponse>.chatError(): AiError = when (code()) {
    CHAT_UNAUTHORIZED, CHAT_FORBIDDEN -> AiError.InvalidKey
    CHAT_PAYMENT_REQUIRED, CHAT_TOO_MANY_REQUESTS -> AiError.QuotaExceeded
    // Un relais devant un modele sature rend ce code tel quel : la traduction est la
    // meme que chez les deux autres, et l'embouteillage se lit partout pareil (D156).
    CHAT_UNAVAILABLE, CHAT_OVERLOADED -> AiError.ProviderBusy
    else -> AiError.Server(code(), chatDetail())
}

private fun Response<OpenAiResponse>.chatDetail(): String? =
    runCatching { errorBody()?.string() }.getOrNull()?.trim()?.take(CHAT_DETAIL_LIMIT)?.takeIf { it.isNotEmpty() }

/**
 * Une réponse tronquée ou refusée n'est pas une réponse illisible.
 *
 * `length` dit qu'il manque la fin du JSON — le parseur échouerait sur un texte
 * parfaitement bien formé jusqu'à sa coupure —, `content_filter` que le fournisseur a
 * décliné. Ni l'un ni l'autre n'invite à soupçonner un défaut technique, et les deux
 * appellent le même geste que le refus d'Anthropic ([D76][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private val INCOMPLETE_REASONS = setOf("length", "content_filter")

private const val BEARER = "Bearer "
private const val VERSION_PATH = "/v1"
private const val CHAT_PATH = "/chat/completions"
private const val SYSTEM_ROLE = "system"
private const val USER_ROLE = "user"
private const val TEXT_PART = "text"
private const val IMAGE_PART = "image_url"
private const val JPEG_DATA_URI = "data:image/jpeg;base64,"
private const val JSON_SCHEMA_FORMAT = "json_schema"
private const val JSON_OBJECT_FORMAT = "json_object"
private const val SCHEMA_NAME = "recognition"
private const val ESTIMATION_SCHEMA_NAME = "estimation"
private const val CHAT_MAX_TOKENS = 4096
private const val CHAT_UNAUTHORIZED = 401
private const val CHAT_PAYMENT_REQUIRED = 402
private const val CHAT_FORBIDDEN = 403
private const val CHAT_TOO_MANY_REQUESTS = 429
private const val CHAT_UNAVAILABLE = 503
private const val CHAT_OVERLOADED = 529
private const val CHAT_DETAIL_LIMIT = 400
private const val CHAT_PHOTO_REQUEST = "Analyse ce repas."
