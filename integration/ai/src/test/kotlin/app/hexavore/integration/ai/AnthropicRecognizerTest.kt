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
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Le fournisseur devant un vrai serveur, sur la boucle locale.
 *
 * **Pas un faux `AnthropicApi`.** Ce qui compte vit sous cette interface : le corps
 * réellement sérialisé — dont les champs à valeur par défaut, que
 * `kotlinx.serialization` omet volontiers —, les en-têtes, et la traduction des codes
 * HTTP. Un faux à ce niveau rendrait vert un client dont le corps manque de la moitié
 * de ses champs.
 *
 * Le serveur ne juge rien : c'est le test qui relit ce qu'il a reçu.
 */
class AnthropicRecognizerTest {
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
        assertEquals(1, recognition.items.size)
        assertEquals("jus d'orange", recognition.items.single().label)
        assertEquals(EstimatedUnit.ML, recognition.items.single().unit)
        assertEquals(INPUT_TOKENS, recognition.usage?.input)
    }

    @Test
    fun `les champs a valeur par defaut sont bien envoyes`() = runTest {
        // Le piege d'encodeDefaults : sans lui, "base64" et "json_schema"
        // disparaissent du corps et le fournisseur rend un 400 qui parle d'un champ
        // manquant qu'on croit pourtant ecrit.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Photo(byteArrayOf(1, 2, 3), note = null))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""type":"base64""""), body)
        assertTrue(body.contains(""""type":"json_schema""""), body)
    }

    @Test
    fun `le corps ne porte aucun parametre d echantillonnage`() = runTest {
        // docs/05 prescrivait temperature = 0,2. Les modeles actuels rendent un 400
        // des qu'elle est presente : ce cas est la pour qu'une relecture de docs/05
        // ne la reintroduise pas.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("temperature"), body)
        assertFalse(body.contains("top_p"), body)
    }

    @Test
    fun `un modele sans effort ne le recoit pas`() = runTest {
        // Claude Haiku 4.5 n'a pas ce parametre et refuse le corps d'un 400 : la liste
        // le proposait, et chaque analyse echouait (D162). Le schema, lui, part toujours.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"), model = "claude-haiku-4-5")

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("effort"), body)
        assertTrue(body.contains(""""type":"json_schema""""), body)
    }

    @Test
    fun `un modele actuel recoit l effort`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"), model = "claude-haiku-5-5")

        assertTrue(server.takeRequest().body.readUtf8().contains(""""effort":"low""""))
    }

    @Test
    fun `les modeles d avant l effort se reconnaissent a leur prefixe, dates compris`() {
        listOf("claude-haiku-4-5-20251001", "claude-sonnet-4-5", "claude-opus-4-20250514", "claude-3-7-sonnet-latest")
            .forEach { assertEquals(null, effortFor(it), it) }
        listOf("claude-opus-4-6", "claude-sonnet-4-6", "claude-opus-5", "claude-sonnet-5-5", "claude-haiku-5-5")
            .forEach { assertEquals("low", effortFor(it), it) }
    }

    @Test
    fun `la cle et la version d API voyagent en en-tete`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        val request = server.takeRequest()
        assertEquals(KEY, request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
    }

    @Test
    fun `le schema part avec additionalProperties`() = runTest {
        // Le pendant exact du cas de Gemini, et la raison d'etre du parametre `strict` :
        // depuis que les deux fournisseurs partagent le meme schema, l'inverser ne coute
        // qu'un mot. Anthropic **exige** la cloture ; Gemini la refuse.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        assertTrue(server.takeRequest().body.readUtf8().contains("additionalProperties"))
    }

    @Test
    fun `une base sans barre oblique finale atteint quand meme le point d entree`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"), baseUrl = server.url("/").toString().trimEnd('/'))

        assertEquals("/v1/messages", server.takeRequest().path)
    }

    @Test
    fun `une photo part en image, avant la consigne`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Photo(byteArrayOf(1, 2, 3), note = "l assiette fait 24 cm"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.indexOf(""""image"""") < body.indexOf(""""text""""), "l image doit preceder la consigne")
        assertTrue(body.contains("AQID"), "le JPEG doit etre encode en base64")
        assertTrue(body.contains("24 cm"), "la note de l utilisateur doit accompagner la demande")
    }

    @Test
    fun `une description ne fabrique aucun bloc image`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("un bol de riz"))

        assertFalse(server.takeRequest().body.readUtf8().contains(""""image""""))
    }

    @Test
    fun `le texte se recolle, et un bloc de raisonnement vide ne le masque pas`() = runTest {
        // Un bloc de raisonnement precede le texte et porte une chaine vide : prendre
        // le premier bloc rendrait du vide, donc Unparseable sur une reponse valide.
        server.enqueue(ok(ONE_ITEM))

        val outcome = recognize(RecognitionInput.Text("un jus"))

        assertTrue(outcome is RecognitionOutcome.Recognized)
    }

    @Test
    fun `un refus n est pas une reponse illisible`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"content":[],"stop_reason":"refusal"}"""),
        )

        val outcome = recognize(RecognitionInput.Text("un jus"))

        assertEquals(RecognitionOutcome.Failed(AiError.NothingRecognized), outcome)
    }

    @Test
    fun `les codes HTTP deviennent des issues, jamais des chiffres a l ecran`() = runTest {
        assertEquals(AiError.InvalidKey, failure(UNAUTHORIZED))
        assertEquals(AiError.InvalidKey, failure(FORBIDDEN))
        assertEquals(AiError.QuotaExceeded, failure(PAYMENT_REQUIRED))
        assertEquals(AiError.QuotaExceeded, failure(TOO_MANY_REQUESTS))
        assertEquals(SERVER_ERROR, (failure(SERVER_ERROR) as AiError.Server).status)
    }

    /**
     * **Deux codes pour un seul etat**, et c'est le piege de ce fournisseur.
     *
     * Anthropic a le sien -- `529`, `overloaded_error` -- en plus du `503` que tout le
     * monde rend. Les traduire ensemble evite que le meme embouteillage se lise de deux
     * facons selon le fournisseur (D156).
     */
    @Test
    fun `les deux codes de surcharge disent la meme chose`() = runTest {
        assertEquals(AiError.ProviderBusy, failure(SERVICE_UNAVAILABLE))
        assertEquals(AiError.ProviderBusy, failure(OVERLOADED))
    }

    @Test
    fun `ce que le fournisseur reproche est conserve`() = runTest {
        // Sans lui, un 400 qui refuse un champ et un 400 qui annonce un compte sans
        // credits se disent tous les deux « le service est indisponible » -- un
        // message qui n'aide ni celui qui configure ni celui qui doit corriger.
        val explication = """{"error":{"message":"credit balance is too low"}}"""
        server.enqueue(MockResponse().setResponseCode(BAD_REQUEST).setBody(explication))

        val error = (recognize(RecognitionInput.Text("un jus")) as RecognitionOutcome.Failed).error

        assertTrue((error as AiError.Server).detail?.contains("credit balance") == true, error.toString())
    }

    @Test
    fun `une issue deja precise ne traine pas la phrase du fournisseur`() = runTest {
        // « Votre cle a ete refusee. Verifiez-la » dit deja quoi faire ; y accoler
        // l'anglais du fournisseur n'ajoute qu'un bruit.
        server.enqueue(MockResponse().setResponseCode(UNAUTHORIZED).setBody("""{"error":{"message":"bad key"}}"""))

        val error = (recognize(RecognitionInput.Text("un jus")) as RecognitionOutcome.Failed).error

        assertEquals(AiError.InvalidKey, error)
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
        val recognizer = AnthropicRecognizer(
            api = anthropicApi(aiClient(NetworkLog.Silent, SilentExchanges)),
            prompts = AiPrompts(extract = { PROMPT }, estimate = { ESTIMATE_PROMPT }, deep = { PROMPT }),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
        )
        val outcome = recognizer.estimate(
            labels,
            AiConfiguration(
                AiProvider.ANTHROPIC,
                ApiKey(KEY),
                model = "claude-opus-5",
                baseUrl = server.url("/").toString(),
            ),
        )
        return (outcome as EstimationOutcome.Estimated).foods
    }

    private suspend fun recognize(
        input: RecognitionInput,
        baseUrl: String = server.url("/").toString(),
        model: String = "claude-opus-5",
    ): RecognitionOutcome {
        val recognizer = AnthropicRecognizer(
            api = anthropicApi(aiClient(NetworkLog.Silent, SilentExchanges)),
            prompts = AiPrompts(extract = { PROMPT }, estimate = { ESTIMATE_PROMPT }, deep = { PROMPT }),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
        )
        return recognizer.recognize(
            input,
            AiConfiguration(AiProvider.ANTHROPIC, ApiKey(KEY), model = model, baseUrl = baseUrl),
        )
    }

    /**
     * Une réponse telle qu'Anthropic la rend : un bloc de raisonnement au texte vide,
     * puis le JSON contraint par le schéma.
     */
    private fun ok(items: String) = MockResponse().setResponseCode(200).setBody(
        """
        {
          "content": [
            {"type": "thinking", "text": ""},
            {"type": "text", "text": ${JsonPrimitive(items)}}
          ],
          "stop_reason": "end_turn",
          "usage": {"input_tokens": $INPUT_TOKENS, "output_tokens": 80}
        }
        """.trimIndent(),
    )

    private companion object {
        const val KEY = "sk-ant-de-test"
        const val PROMPT = "Tu identifies les aliments."
        const val ESTIMATE_PROMPT = "Tu estimes des macros."
        const val INPUT_TOKENS = 1200
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val PAYMENT_REQUIRED = 402
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
        const val SERVICE_UNAVAILABLE = 503
        const val OVERLOADED = 529

        val ESTIMATION = """
            {"foods":[{"label":"tofu fume au sesame","kcal":180,"protein":16}]}
        """.trimIndent()

        val ONE_ITEM = """
            {"items":[{"label":"jus d'orange","quantity":200,"unit":"ML","confidence":0.9}]}
        """.trimIndent()
    }
}
