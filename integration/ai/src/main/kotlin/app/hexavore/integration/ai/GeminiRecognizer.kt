package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.CatalogueTool
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
 * Google Gemini — le deuxième fournisseur, et la preuve que le prix annoncé tient.
 *
 * Une classe, une entrée d'énumération, une branche dans le `when` de la fabrique :
 * les trois étapes de [docs/05][ia], et rien d'autre. Aucun écran n'a bougé, aucun
 * test existant n'a été retouché, et le domaine ne sait toujours pas qu'il existe
 * plus d'un fournisseur.
 *
 * **Ce qu'il ne partage pas avec Anthropic** : le modèle voyage dans l'URL, la clé
 * dans un autre en-tête, la consigne système dans un champ à part, et le schéma
 * n'accepte pas `additionalProperties`. **Ce qu'il partage** : le prompt, le schéma de
 * réponse, le parseur, l'intercepteur de redaction, la traduction des codes HTTP et
 * la pile HTTP entière. C'est le partage que [D76][decisions] cherchait en préférant
 * la sortie structurée à un outil forcé.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
internal class GeminiRecognizer(
    private val api: GeminiApi,
    private val prompts: AiPrompts,
    private val dispatchers: DispatcherProvider,
) : ProviderRecognizer {
    override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
        withContext(dispatchers.io) {
            try {
                api
                    .generateContent(
                        configuration.geminiEndpoint(),
                        configuration.apiKey.value,
                        configuration.geminiRequest(input, prompts.extract),
                    )
                    .toGeminiOutcome()
            } catch (timeout: SocketTimeoutException) {
                // Avant IOException, dont elle herite : le meme ordre que chez
                // Anthropic, et pour la meme raison.
                timeout.reducedTo(AiError.Timeout)
            } catch (offline: IOException) {
                offline.reducedTo(AiError.NoNetwork)
            }
        }

    override suspend fun deepRecognize(
        input: RecognitionInput,
        configuration: AiConfiguration,
        catalogue: CatalogueTool,
    ): RecognitionOutcome = withContext(dispatchers.io) {
        try {
            api.deepRecognize(input, configuration, prompts.deep, catalogue)
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
                    .generateContent(
                        configuration.geminiEndpoint(),
                        configuration.apiKey.value,
                        configuration.geminiEstimateRequest(labels, prompts.estimate),
                    )
                    .toGeminiEstimation()
            } catch (timeout: SocketTimeoutException) {
                timeout.estimationReducedTo(AiError.Timeout)
            } catch (offline: IOException) {
                offline.estimationReducedTo(AiError.NoNetwork)
            }
        }
}

private fun AiConfiguration.geminiEstimateRequest(labels: List<String>, prompt: SystemPrompt) = GeminiRequest(
    contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = labels.asRequest()))).asJson()),
    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = prompt.text()))),
    generationConfig = GeminiGenerationConfig(
        responseSchema = GEMINI_ESTIMATION_SCHEMA,
        maxOutputTokens = GEMINI_MAX_TOKENS,
    ),
)

private fun Response<GeminiResponse>.toGeminiEstimation(): EstimationOutcome {
    val candidate = body()?.candidates?.firstOrNull()
    return when {
        !isSuccessful -> EstimationOutcome.Failed(geminiError())
        candidate == null -> EstimationOutcome.Failed(AiError.Unparseable)
        else -> parseEstimation(candidate.text(), body()?.usageMetadata?.toDomain())
    }
}

/**
 * `…/v1beta/models/{modèle}:generateContent`.
 *
 * Le modèle est encodé tel quel : il ne contient jamais de caractère à échapper, et le
 * `:` qui suit fait partie du chemin — c'est la convention de nommage des méthodes de
 * l'API de Google, pas un séparateur de port.
 */
private fun AiConfiguration.geminiEndpoint(): String =
    baseUrl.trimEnd('/') + "/v1beta/models/" + model + ":generateContent"

private fun AiConfiguration.geminiRequest(input: RecognitionInput, prompt: SystemPrompt) = GeminiRequest(
    contents = listOf(GeminiContent(role = "user", parts = input.geminiParts()).asJson()),
    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = prompt.text()))),
    generationConfig = GeminiGenerationConfig(responseSchema = GEMINI_SCHEMA, maxOutputTokens = GEMINI_MAX_TOKENS),
)

/**
 * L'image d'abord, la consigne ensuite — le même ordre que chez Anthropic, pour la
 * même raison : un modèle qui lit la demande avant d'avoir vu l'image la rapporte
 * moins bien à ce qu'il regarde.
 */
private fun RecognitionInput.geminiParts(): List<GeminiPart> = when (this) {
    is RecognitionInput.Photo -> listOf(
        GeminiPart(inlineData = GeminiInlineData(data = Base64.getEncoder().encodeToString(jpeg))),
        GeminiPart(text = note?.let { "$GEMINI_PHOTO_REQUEST\n\n$it" } ?: GEMINI_PHOTO_REQUEST),
    )

    is RecognitionInput.Text -> listOf(GeminiPart(text = description))
}

private fun Response<GeminiResponse>.toGeminiOutcome(): RecognitionOutcome {
    val candidate = body()?.candidates?.firstOrNull()
    return when {
        !isSuccessful -> RecognitionOutcome.Failed(geminiError())
        candidate == null -> RecognitionOutcome.Failed(AiError.Unparseable)
        candidate.finishReason != null && candidate.finishReason != STOP ->
            RecognitionOutcome.Failed(AiError.NothingRecognized)

        else -> parseRecognition(candidate.text(), body()?.usageMetadata?.toDomain())
    }
}

/**
 * Un arrêt qui n'est pas `STOP` n'est pas une réponse illisible.
 *
 * `SAFETY`, `RECITATION`, `MAX_TOKENS` : la réponse est parfaitement lisible et ne
 * contient rien d'exploitable. C'est le même raisonnement que le refus d'Anthropic
 * ([D76][decisions]), sur un champ qui porte un autre nom.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun GeminiCandidate.text(): String = content
    ?.asGeminiContent()
    ?.parts
    ?.mapNotNull { it.text }
    ?.joinToString(separator = "")
    .orEmpty()

internal fun GeminiUsage.toDomain() = TokenUsage(input = promptTokenCount, output = candidatesTokenCount)

/**
 * Les codes de Google, traduits vers les mêmes issues.
 *
 * `400` avec une clé invalide et `403` disent tous deux « la clé ne va pas » chez ce
 * fournisseur — mais `400` sert aussi à refuser un champ, donc lui seul garde le
 * corps de l'erreur. Sans quoi une requête mal formée s'annoncerait comme une clé
 * refusée, et personne ne chercherait au bon endroit.
 */
internal fun Response<GeminiResponse>.geminiError(): AiError = when (code()) {
    GEMINI_UNAUTHORIZED, GEMINI_FORBIDDEN -> AiError.InvalidKey
    GEMINI_TOO_MANY_REQUESTS -> AiError.QuotaExceeded
    // Le code que les modeles gratuits les plus demandes rendent aux heures de pointe.
    // Il n'a rien a voir avec la cle, et il se resout en attendant (D156).
    GEMINI_UNAVAILABLE -> AiError.ProviderBusy
    else -> AiError.Server(code(), geminiDetail())
}

private fun Response<GeminiResponse>.geminiDetail(): String? =
    runCatching { errorBody()?.string() }.getOrNull()?.trim()?.take(GEMINI_DETAIL_LIMIT)?.takeIf { it.isNotEmpty() }

/** Gemini refuse `additionalProperties` : son sous-ensemble de schéma ne le connaît pas. */
private val GEMINI_SCHEMA: JsonObject = recognitionSchema(strict = false)

/** Meme refus du mot-cle pour l'estimation : c'est le sous-ensemble qui est etroit, pas le schema. */
private val GEMINI_ESTIMATION_SCHEMA: JsonObject = estimationSchema(strict = false)

private const val GEMINI_MAX_TOKENS = 4096
private const val STOP = "STOP"
private const val GEMINI_UNAUTHORIZED = 401
private const val GEMINI_FORBIDDEN = 403
private const val GEMINI_TOO_MANY_REQUESTS = 429
private const val GEMINI_UNAVAILABLE = 503
private const val GEMINI_DETAIL_LIMIT = 400
private const val GEMINI_PHOTO_REQUEST = "Analyse ce repas."
