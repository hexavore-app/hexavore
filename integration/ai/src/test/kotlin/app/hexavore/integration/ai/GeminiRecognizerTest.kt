package app.hexavore.integration.ai

import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.EstimatedFood
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Gemini devant un vrai serveur, sur la boucle locale.
 *
 * Les cas visent **ce qui diffère d'Anthropic**, parce que c'est là que le second
 * fournisseur peut casser sans que rien d'existant ne bouge : le modèle dans l'URL, la
 * clé dans un autre en-tête, la consigne système dans un champ à part, et un schéma
 * dont `additionalProperties` doit être **absent**.
 *
 * Ce qu'ils ne revérifient pas : le parseur et le prompt, éprouvés ailleurs et
 * partagés. Les recopier ici ferait deux jeux de cas à corriger pour une seule règle.
 */
class GeminiRecognizerTest {
    private val server = MockWebServer()

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `une reponse bien formee rend des lignes et le compte de jetons`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        val outcome = recognize(RecognitionInput.Text("un verre de jus d'orange"))

        val recognition = (outcome as RecognitionOutcome.Recognized).recognition
        assertEquals("jus d'orange", recognition.items.single().label)
        assertEquals(EstimatedUnit.ML, recognition.items.single().unit)
        assertEquals(PROMPT_TOKENS, recognition.usage?.input)
    }

    @Test
    fun `le modele voyage dans l URL et la cle dans son propre en-tete`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.5-flash-lite:generateContent", request.path)
        assertEquals(KEY, request.getHeader("x-goog-api-key"))
        // Celui d'Anthropic n'a rien a faire ici : deux fournisseurs, deux en-tetes.
        assertEquals(null, request.getHeader("x-api-key"))
    }

    @Test
    fun `le schema part sans additionalProperties`() = runTest {
        // Le sous-ensemble de schema de Gemini ne connait pas ce mot-cle et refuse la
        // requete s'il le trouve. C'est la seule difference entre les deux schemas, et
        // celle qui se paierait par un 400 incomprehensible.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""responseSchema""""), body)
        assertFalse(body.contains("additionalProperties"), body)
    }

    @Test
    fun `la consigne systeme part dans son champ, pas dans le message`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        val corps = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

        // Ou se trouve la consigne, et non ou elle apparait dans le texte : l'ordre
        // des champs d'un JSON ne veut rien dire, et l'affirmer aurait fige la
        // declaration des champs plutot que la regle.
        assertTrue(corps.getValue("systemInstruction").toString().contains(PROMPT))
        assertFalse(corps.getValue("contents").toString().contains(PROMPT), "la consigne n est pas un message")
    }

    @Test
    fun `une photo part en donnee jointe, avant la consigne`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Photo(byteArrayOf(1, 2, 3), note = "l assiette fait 24 cm"))

        val body = server.takeRequest().body.readUtf8()
        val parts = Json.parseToJsonElement(body).jsonObject
            .getValue("contents").jsonArray.first().jsonObject
            .getValue("parts").jsonArray

        // L'ordre d'un tableau, lui, veut dire quelque chose : c'est celui dans lequel
        // le modele lit. L'image d'abord, la consigne ensuite.
        assertTrue(parts.first().jsonObject.containsKey("inlineData"), body)
        assertTrue(parts.last().jsonObject.containsKey("text"), body)
        assertTrue(body.contains(""""mimeType":"image/jpeg""""), body)
        assertTrue(body.contains("AQID"), "le JPEG doit etre encode en base64")
        assertTrue(body.contains("24 cm"), "la note de l utilisateur doit accompagner la demande")
    }

    @Test
    fun `une description ne joint aucune donnee binaire`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("un bol de riz"))

        assertFalse(server.takeRequest().body.readUtf8().contains("inlineData"))
    }

    @Test
    fun `un arret qui n est pas STOP n est pas une reponse illisible`() = runTest {
        // SAFETY, RECITATION, MAX_TOKENS : la reponse est parfaitement lisible et ne
        // contient rien d'exploitable. Le meme raisonnement que le refus d'Anthropic,
        // sur un champ qui porte un autre nom.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"candidates":[{"finishReason":"SAFETY"}]}"""),
        )

        val outcome = recognize(RecognitionInput.Text("un jus"))

        assertEquals(RecognitionOutcome.Failed(AiError.NothingRecognized), outcome)
    }

    @Test
    fun `les codes de Google deviennent les memes issues`() = runTest {
        assertEquals(AiError.InvalidKey, failure(FORBIDDEN))
        assertEquals(AiError.QuotaExceeded, failure(TOO_MANY_REQUESTS))
        assertEquals(BAD_REQUEST, (failure(BAD_REQUEST) as AiError.Server).status)
    }

    /**
     * Le code que les modeles gratuits les plus demandes rendent aux heures de repas.
     *
     * **Il n'a rien a voir avec la cle**, et c'etait pourtant ce que l'ecran laissait
     * croire : « le service est indisponible » affiche pendant qu'on vient de coller sa
     * premiere cle se lit comme un refus de cette cle (D156).
     */
    @Test
    fun `un modele sature est un embouteillage, pas une panne`() = runTest {
        assertEquals(AiError.ProviderBusy, failure(UNAVAILABLE))
    }

    @Test
    fun `ce que Google reproche est conserve`() = runTest {
        val explication = """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}"""
        server.enqueue(MockResponse().setResponseCode(BAD_REQUEST).setBody(explication))

        val error = (recognize(RecognitionInput.Text("un jus")) as RecognitionOutcome.Failed).error

        assertTrue((error as AiError.Server).detail?.contains("API key not valid") == true, error.toString())
    }

    private suspend fun failure(status: Int): AiError {
        server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
        return (recognize(RecognitionInput.Text("un jus")) as RecognitionOutcome.Failed).error
    }

    @Test
    fun `l estimation part avec son propre prompt et son propre schema`() = runTest {
        // L'etape 4 emprunte la meme route et la meme cle, et rien d'autre : deux
        // questions differentes ne se posent pas avec la meme consigne, et un schema
        // de reconnaissance ferait rendre des unites la ou on attend des macros.
        server.enqueue(ok(ESTIMATION))

        val foods = estimate(listOf("tofu fume au sesame", "sauce maison"))

        val corps = server.takeRequest().body.readUtf8()
        assertTrue(corps.contains(ESTIMATE_PROMPT), corps)
        assertFalse(corps.contains(PROMPT), "la consigne d extraction n a rien a faire ici")
        assertTrue(corps.contains("tofu fume au sesame"), corps)
        assertTrue(corps.contains("sauce maison"), "les libelles partent ensemble, en un seul appel")
        assertEquals(180.0, foods.single().per100g.kcal)
    }

    private suspend fun estimate(labels: List<String>): List<EstimatedFood> {
        val recognizer = GeminiRecognizer(
            api = geminiApi(aiClient(NetworkLog.Silent, SilentExchanges)),
            prompts = AiPrompts(extract = { PROMPT }, estimate = { ESTIMATE_PROMPT }, deep = { PROMPT }),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
        )
        val outcome = recognizer.estimate(
            labels,
            AiConfiguration(
                provider = AiProvider.GEMINI,
                apiKey = ApiKey(KEY),
                model = "gemini-3.5-flash-lite",
                baseUrl = server.url("/").toString(),
            ),
        )
        return (outcome as EstimationOutcome.Estimated).foods
    }

    private suspend fun recognize(input: RecognitionInput): RecognitionOutcome {
        val recognizer = GeminiRecognizer(
            api = geminiApi(aiClient(NetworkLog.Silent, SilentExchanges)),
            prompts = AiPrompts(extract = { PROMPT }, estimate = { ESTIMATE_PROMPT }, deep = { PROMPT }),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
        )
        return recognizer.recognize(
            input,
            AiConfiguration(
                provider = AiProvider.GEMINI,
                apiKey = ApiKey(KEY),
                model = "gemini-3.5-flash-lite",
                baseUrl = server.url("/").toString(),
            ),
        )
    }

    private fun ok(items: String) = MockResponse().setResponseCode(200).setBody(
        """
        {
          "candidates": [
            {"content": {"role": "model", "parts": [{"text": ${JsonPrimitive(items)}}]}, "finishReason": "STOP"}
          ],
          "usageMetadata": {"promptTokenCount": $PROMPT_TOKENS, "candidatesTokenCount": 80}
        }
        """.trimIndent(),
    )

    private companion object {
        const val KEY = "AIza-de-test"
        const val PROMPT = "Tu identifies les aliments."
        const val ESTIMATE_PROMPT = "Tu estimes des macros."
        const val PROMPT_TOKENS = 900
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        const val UNAVAILABLE = 503

        val ESTIMATION = """
            {"foods":[{"label":"tofu fume au sesame","kcal":180,"protein":16}]}
        """.trimIndent()

        val ONE_ITEM = """
            {"items":[{"label":"jus d'orange","quantity":200,"unit":"ML","confidence":0.9}]}
        """.trimIndent()
    }
}
