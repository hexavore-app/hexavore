package app.hexavore.integration.ai

import app.hexavore.core.testing.FixedClock
import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.ai.AiExchangeLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Ce que le journal de mise au point retient, et surtout ce qu'il ne retient pas.
 *
 * **Trois promesses, et chacune se casserait en silence.** Une clé qui se retrouverait
 * dans le journal n'y serait vue par personne avant qu'une capture d'écran circule ;
 * une image non élidée ferait grossir la mémoire jusqu'à ce que l'application meure
 * sans dire pourquoi ; un corps de réponse consommé par la lecture rendrait vide ce que
 * l'appelant reçoit — un journal qui casse ce qu'il observe.
 */
class ExchangeInterceptorTest {
    private val server = MockWebServer()
    private val log = RecordingLog()

    @BeforeEach
    fun ouvrir() = server.start()

    @AfterEach
    fun fermer() = server.shutdown()

    /**
     * Il note **toujours**, quel que soit le reglage de mise au point.
     *
     * Il sortait a sa premiere ligne quand celui-ci etait eteint, et un signalement ne
     * joignait alors jamais l'echange qu'il promet de porter. La profondeur gardee est
     * l'affaire du journal, pas de l'intercepteur (D153) : voir RecentExchangesTest.
     */
    @Test
    fun `l echange est note sans condition`() {
        server.enqueue(MockResponse().setBody("{}"))

        appeler(corps = "{\"a\":1}")

        assertEquals(1, log.exchanges.size)
    }

    @Test
    fun `les deux corps sont retenus`() {
        server.enqueue(MockResponse().setBody("""{"reponse":"ok"}"""))

        appeler(corps = """{"question":"quoi"}""")

        val exchange = log.exchanges.single()
        assertTrue(exchange.request.contains("question"), exchange.request)
        assertTrue(exchange.response.contains("reponse"), exchange.response)
        assertEquals(200, exchange.status)
    }

    @Test
    fun `aucun en-tete n est retenu, donc aucune cle`() {
        // Les en-tetes ne sont pas masques : ils sont absents. C'est plus sur qu'une
        // liste de noms secrets a tenir a jour, que le septieme fournisseur oublierait.
        server.enqueue(MockResponse().setBody("{}"))

        appeler(corps = "{}", cle = "sk-ant-tres-secrete")

        val exchange = log.exchanges.single()
        assertFalse(exchange.request.contains("sk-ant-tres-secrete"), exchange.request)
        assertFalse(exchange.endpoint.contains("sk-ant-tres-secrete"), exchange.endpoint)
    }

    @Test
    fun `la chaine de requete est retiree de l adresse`() {
        // Certains fournisseurs acceptent la cle en parametre d'URL. Aucun de ceux
        // qu'on appelle ne l'exige, et c'est precisement pourquoi personne ne
        // verifierait ce point le jour ou l'un d'eux s'y met.
        server.enqueue(MockResponse().setBody("{}"))

        appeler(corps = "{}", suffixe = "?key=sk-dans-l-url")

        assertFalse(log.exchanges.single().endpoint.contains("sk-dans-l-url"))
    }

    @Test
    fun `une image est elidee`() {
        // Une photo voyage en base64 : quelques centaines de milliers de caracteres
        // qui rempliraient la memoire et noieraient le JSON qu'on cherche a lire.
        server.enqueue(MockResponse().setBody("{}"))
        val image = "A".repeat(5_000)

        appeler(corps = """{"data":"$image"}""")

        val retenu = log.exchanges.single().request
        assertFalse(retenu.contains(image), "l'image entiere ne doit pas etre retenue")
        assertTrue(retenu.contains("5000"), "l elision doit dire combien elle a retire, or $retenu")
    }

    @Test
    fun `un corps immense est borne`() {
        // L'elision ne mord que sur le base64 ; un JSON pathologique doit quand meme
        // s'arreter quelque part.
        server.enqueue(MockResponse().setBody("{}"))
        val long = List(3_000) { """{"n":$it}""" }.joinToString(",")

        appeler(corps = "[$long]")

        assertTrue(log.exchanges.single().request.length < long.length, "le corps doit etre tronque")
    }

    @Test
    fun `la reponse reste lisible par l appelant`() {
        // `peekBody` en prend une copie sans consommer le flux : le lire autrement le
        // viderait, et l'appelant recevrait une reponse vide.
        server.enqueue(MockResponse().setBody("""{"reponse":"intacte"}"""))

        val recu = appeler(corps = "{}")

        assertEquals("""{"reponse":"intacte"}""", recu)
    }

    private fun client() = OkHttpClient.Builder()
        .addInterceptor(ExchangeInterceptor(log, FixedClock.atNoon(LocalDate.of(2026, 8, 25))))
        .build()

    private fun appeler(
        client: OkHttpClient = client(),
        corps: String,
        cle: String = "peu importe",
        suffixe: String = "",
    ): String {
        val requete = Request.Builder()
            .url(server.url("/v1/messages$suffixe"))
            .header("x-api-key", cle)
            .post(corps.toRequestBody(JSON))
            .build()
        return client.newCall(requete).execute().use { it.body?.string().orEmpty() }
    }

    /** Retient ce qu'on lui donne, pour qu'un cas l'affirme. */
    private class RecordingLog : AiExchangeLog {
        private val state = MutableStateFlow<List<AiExchange>>(emptyList())

        val exchanges: List<AiExchange> get() = state.value

        override fun observe(): Flow<List<AiExchange>> = state

        override fun record(exchange: AiExchange) {
            state.value = state.value + exchange
        }

        override fun clear() {
            state.value = emptyList()
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
