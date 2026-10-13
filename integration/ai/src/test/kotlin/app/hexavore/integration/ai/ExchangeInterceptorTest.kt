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
 * **Quatre promesses, et chacune se casserait en silence.** Une clé qui se retrouverait
 * dans le journal n'y serait vue par personne avant qu'une capture d'écran circule ;
 * une image non élidée ferait grossir la mémoire jusqu'à ce que l'application meure
 * sans dire pourquoi ; un corps de réponse consommé par la lecture rendrait vide ce que
 * l'appelant reçoit — un journal qui casse ce qu'il observe.
 *
 * Et la quatrième est celle qui a été prise en défaut : **un corps coupé trop tôt**.
 * Elle ne casse rien, elle rend un fichier qui a l'air entier — et c'est ce qui la rend
 * pire que les trois autres, puisqu'on ne la découvre qu'en cherchant longtemps dans ce
 * qu'il ne contient pas (D155).
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
    fun `un tour d analyse approfondie tient en entier`() {
        // La borne valait huit mille caracteres, soit moins qu'un seul tour : une
        // analyse approfondie renvoie la conversation complete a chaque fois, et le
        // signalement partait avec une consigne coupee en son milieu.
        server.enqueue(MockResponse().setBody("{}"))
        val tour = List(1_000) { """{"reference":"$it","kcal":${it * 2}}""" }.joinToString(",")

        appeler(corps = "[$tour]")

        val retenu = log.exchanges.single().request
        assertTrue(tour.length > 8_000, "le decor doit depasser l'ancienne borne, or ${tour.length}")
        assertFalse(retenu.contains("coupés ici"), "il tenait, et n'avait donc pas a etre coupe")
        assertTrue(retenu.contains(""""reference":"999""""), "la fin du corps doit y etre")
    }

    @Test
    fun `un corps immense est borne, et la coupe se nomme`() {
        // L'elision ne mord que sur le base64 ; un JSON pathologique doit quand meme
        // s'arreter quelque part -- mais en disant combien il emporte, et en gardant
        // les deux bouts : la fin d'une requete porte les outils declares, celle d'une
        // reponse sa raison d'arret.
        server.enqueue(MockResponse().setBody("{}"))
        val long = List(20_000) { """{"n":$it}""" }.joinToString(",")

        appeler(corps = """{"debut":"ici","milieu":[$long],"fin":"la"}""")

        val retenu = log.exchanges.single().request
        assertTrue(retenu.length < long.length, "le corps doit etre borne")
        assertTrue(retenu.contains("""{"debut":"ici""""), "le debut doit survivre, or $retenu")
        assertTrue(retenu.contains(""""fin":"la"}"""), "la fin doit survivre, or $retenu")
        assertTrue(retenu.contains("caractères coupés ici"), "la coupe doit se nommer, or $retenu")
    }

    @Test
    fun `la coupe dit combien de caracteres elle emporte`() {
        // Le compte doit boucler : ce qui est retenu plus ce qui est annonce vaut le
        // corps entier. Sans cela, « tronque » ne distingue pas trois lignes de moitie.
        server.enqueue(MockResponse().setBody("{}"))
        val corps = """{"items":[${List(20_000) { """{"n":$it}""" }.joinToString(",")}]}"""

        appeler(corps = corps)

        val retenu = log.exchanges.single().request
        val annonce = Regex("\n…\\((\\d+) caractères coupés ici\\)…\n").find(retenu)
        assertTrue(annonce != null, "la coupe doit s'annoncer, or $retenu")
        assertEquals(
            corps.length,
            retenu.length - annonce!!.value.length + annonce.groupValues[1].toInt(),
            "le compte annonce doit boucler avec ce qui est retenu",
        )
    }

    @Test
    fun `une reponse accentuee n est pas coupee en silence`() {
        // `peekBody` compte en **octets** et coupe sans rien dire. Soixante mille
        // caracteres accentues tiennent sous la borne du journal, et pesaient pourtant
        // deux fois l'ancienne copie : la reponse arrivait amputee, sans aucune marque.
        val reponse = """{"debut":"ici","texte":"${"é".repeat(60_000)}","fin":"la"}"""
        server.enqueue(MockResponse().setBody(reponse))

        appeler(corps = "{}")

        val retenu = log.exchanges.single().response
        assertTrue(retenu.contains(""""fin":"la"}"""), "la fin de la reponse doit survivre")
        assertEquals(reponse.length, retenu.length, "rien ne doit manquer au milieu non plus")
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
