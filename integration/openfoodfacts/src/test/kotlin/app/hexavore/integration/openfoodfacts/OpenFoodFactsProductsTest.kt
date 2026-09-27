package app.hexavore.integration.openfoodfacts

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.FixedLanguage
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.food.ProductLookup
import app.hexavore.domain.food.ProductResults
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Le client devant un vrai serveur, sur la boucle locale.
 *
 * **Pas un faux `OpenFoodFactsApi`.** Ce qu'on veut éprouver vit précisément
 * *sous* cette interface : l'en-tête que [D26][decisions] rend obligatoire, le
 * décodage tolérant, la distinction entre « inconnu » et « injoignable », et le
 * retrait exponentiel. Un faux à ce niveau aurait rendu vert un client sans
 * `User-Agent` — c'est-à-dire le piège que [docs/12][plan] annonce depuis la
 * conception.
 *
 * L'attente du retrait est du **temps virtuel** : les trois tentatives s'exécutent
 * en millisecondes, et le cas reste lisible.
 *
 * [decisions]: docs/11-decisions.md
 * [plan]: docs/12-plan-de-developpement.md
 */
class OpenFoodFactsProductsTest {
    private val server = MockWebServer()
    private lateinit var baseUrl: String

    @BeforeEach
    fun start() {
        server.start()
        baseUrl = server.url("/").toString()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `un produit complet devient une fiche`() = runTest {
        server.enqueue(ok(NUTELLA))

        val lookup = products().byBarcode(nutella)

        val food = (lookup as ProductLookup.Found).food
        assertEquals("Nutella", food.name)
        assertEquals("Ferrero", food.brand)
        assertEquals(FoodSource.OFF, food.source)
        assertEquals(FoodId("id-1"), food.id)
        assertEquals(539.0, food.per100g.kcal)
        assertEquals(58.4, food.per100g.sugars)
        assertEquals(15.0, food.defaultServingG)
        assertEquals(false, food.isLiquid)
    }

    @Test
    fun `une portion en millilitres marque le produit comme liquide`() = runTest {
        server.enqueue(ok("""{"product_name":"Cola","serving_size":"33 cl"}"""))

        val food = (products().byBarcode(nutella) as ProductLookup.Found).food

        assertEquals(true, food.isLiquid)
        assertEquals(330.0, food.defaultServingG)
    }

    @Test
    fun `sans portion ecrite, on ne sait pas si le produit est liquide`() = runTest {
        // `null` et non `false` : la fiche ne le dit pas, et l'affirmer serait
        // inventer. Rien ne le rattrapera ensuite -- le cache ne redemande pas.
        server.enqueue(ok("""{"product_name":"Quelque chose","serving_quantity":30}"""))

        val food = (products().byBarcode(nutella) as ProductLookup.Found).food

        assertNull(food.isLiquid)
        assertEquals(30.0, food.defaultServingG)
    }

    @Test
    fun `une teneur absente reste inconnue et ne devient pas zero`() = runTest {
        server.enqueue(ok(NUTELLA))

        val food = (products().byBarcode(nutella) as ProductLookup.Found).food

        // Les fibres manquent tres souvent dans cette base. A zero, elles feraient
        // passer pour complete une journee qui ne l'est pas -- la regle la plus
        // couteuse du projet, et celle qui se trahit le plus discretement.
        //
        // Les proteines sont affirmees a cote : c'est la paire qui rend la confusion
        // visible. Un mappeur qui remplacerait l'inconnu par zero passerait un test
        // qui ne regarderait que le champ absent.
        assertNull(food.per100g.fiber)
        assertEquals(6.3, food.per100g.protein)
    }

    @Test
    fun `la reference enregistree est le code demande`() = runTest {
        // La reponse annonce le code sur douze chiffres ; le catalogue local sera
        // interroge avec les treize. Retenir celui de la reponse rendrait le cache
        // muet au deuxieme scan, et ca ne se verrait qu'en mode avion.
        server.enqueue(ok("""{"code":"012345678905","product_name":"Conserve"}"""))

        val food = (products().byBarcode(upcA) as ProductLookup.Found).food

        assertEquals("0012345678905", food.sourceRef)
    }

    @Test
    fun `l'en-tete User-Agent part avec la requete`() = runTest {
        server.enqueue(ok(NUTELLA))

        products().byBarcode(nutella)

        // Sans lui, Open Food Facts refuse, et le symptome ressemble a une panne
        // reseau : on cherche alors un defaut la ou il n'est pas (D26).
        assertEquals(USER_AGENT, server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `la requete ne demande que les champs lus`() = runTest {
        server.enqueue(ok(NUTELLA))

        products().byBarcode(nutella)

        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.startsWith("/api/v2/product/3017620422003.json"), path)
        assertTrue(path.contains("fields="), path)
        assertTrue(path.contains("nutriments"), path)
    }

    @Test
    fun `un code inconnu du service rend Unknown`() = runTest {
        server.enqueue(MockResponse().setResponseCode(NOT_FOUND).setBody("""{"status":0}"""))

        assertEquals(ProductLookup.Unknown, products().byBarcode(nutella))
    }

    @Test
    fun `un etat a zero rend Unknown, meme avec un code 200 et un produit`() = runTest {
        // L'enveloppe fait foi. Le produit est fourni **exprès** : un corps vide
        // ferait passer ce cas meme si l'etat n'etait plus lu, et on croirait tenir
        // une regle qu'on n'a pas -- c'est le defaut que D57 a nomme.
        server.enqueue(ok("""{"code":"3017620422003","product_name":"Nutella"}""", status = 0))

        assertEquals(ProductLookup.Unknown, products().byBarcode(nutella))
    }

    @Test
    fun `un etat a un sans produit rend Unknown`() = runTest {
        server.enqueue(ok(product = null))

        assertEquals(ProductLookup.Unknown, products().byBarcode(nutella))
    }

    @Test
    fun `un produit sans nom rend Unknown`() = runTest {
        // Le nom est le seul champ bloquant : une fiche qu'on ne peut ni afficher ni
        // retrouver n'en est pas une, et l'annoncer trouvee remplacerait le
        // formulaire de creation par un ecran vide.
        server.enqueue(ok("""{"code":"3017620422003","brands":"Ferrero","product_name":"  "}"""))

        assertEquals(ProductLookup.Unknown, products().byBarcode(nutella))
    }

    @Test
    fun `une energie donnee en kilojoules seulement est convertie`() = runTest {
        server.enqueue(ok("""{"product_name":"Jus","nutriments":{"energy-kj_100g":190}}"""))

        val food = (products().byBarcode(nutella) as ProductLookup.Found).food

        assertEquals(190.0 / 4.184, food.per100g.kcal)
    }

    @Test
    fun `des kilocalories presentes ne sont jamais reconverties`() = runTest {
        // Le defaut serait invisible : diviser 190 kcal par 4,184 donne 45, un chiffre
        // parfaitement plausible pour un jus.
        server.enqueue(ok("""{"product_name":"Jus","nutriments":{"energy-kcal_100g":45,"energy_100g":190}}"""))

        val food = (products().byBarcode(nutella) as ProductLookup.Found).food

        assertEquals(45.0, food.per100g.kcal)
    }

    @Test
    fun `une valeur ecrite en chaine se lit comme un nombre`() = runTest {
        // Les deux ecritures existent dans la base, pour le meme champ.
        server.enqueue(ok("""{"product_name":"Biscuit","serving_quantity":"12.5","nutriments":{"fat_100g":"3,2"}}"""))

        val food = (products().byBarcode(nutella) as ProductLookup.Found).food

        assertEquals(12.5, food.defaultServingG)
        assertEquals(3.2, food.per100g.fat)
    }

    @Test
    fun `un service en panne est reessaye trois fois, puis declare injoignable`() = runTest {
        repeat(ATTEMPTS) { server.enqueue(MockResponse().setResponseCode(SERVER_ERROR)) }

        assertEquals(ProductLookup.Unreachable, products().byBarcode(nutella))
        assertEquals(ATTEMPTS, server.requestCount)
    }

    @Test
    fun `un refus temporaire suivi d'une reponse rend la fiche`() = runTest {
        server.enqueue(MockResponse().setResponseCode(TOO_MANY_REQUESTS))
        server.enqueue(ok(NUTELLA))

        assertTrue(products().byBarcode(nutella) is ProductLookup.Found)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `hors ligne, on ne reessaye pas`() = runTest {
        // Le retrait sert a laisser passer une surcharge du service. Sans reseau, il
        // ne ferait que retarder d'une seconde et demie une phrase qu'on peut dire
        // tout de suite, a quelqu'un debout devant un rayon.
        server.shutdown()

        assertEquals(ProductLookup.Unreachable, products().byBarcode(nutella))
    }

    @Test
    fun `la fiche recuperee porte sa date`() = runTest {
        // Posee par ce module et non par un cas d'usage : lui seul sait quand il a
        // interroge le service. C'est le second chemin de recuperation -- la recherche
        // par nom -- qui a montre que la laisser a l'appelant s'oublie.
        server.enqueue(ok(NUTELLA))

        val food = (products().byBarcode(nutella) as ProductLookup.Found).food

        assertEquals(RECUPERE_LE, food.fetchedAt)
    }

    // --- La recherche par nom ------------------------------------------------------

    @Test
    fun `une recherche par nom rend des fiches`() = runTest {
        server.enqueue(searchOk("""[{"code":"3017620422003","product_name":"Nutella","brands":"Ferrero"}]"""))

        val trouve = products().byName("nutella", LIMIT) as ProductResults.Found

        assertEquals(listOf("Nutella"), trouve.products.map { it.name })
        assertEquals("3017620422003", trouve.products.single().sourceRef)
        assertEquals(RECUPERE_LE, trouve.products.single().fetchedAt)
    }

    @Test
    fun `un produit dont le code n est pas lisible est ecarte`() = runTest {
        // Sans code canonique, la fiche ne peut ni etre mise en cache sans doublon ni
        // etre retrouvee par un scan : elle reviendrait du reseau a chaque recherche.
        server.enqueue(
            searchOk(
                """[{"code":"pas-un-code","product_name":"Bizarre"},
                   {"code":"3017620422003","product_name":"Nutella"}]""",
            ),
        )

        val trouve = products().byName("x", LIMIT) as ProductResults.Found

        assertEquals(listOf("Nutella"), trouve.products.map { it.name })
    }

    @Test
    fun `un nom que le service ne connait pas rend une liste vide`() = runTest {
        // Une liste vide **est** une reponse. Lui donner un cas a part ferait dire a
        // l'ecran deux fois la meme chose.
        server.enqueue(searchOk("[]"))

        assertEquals(emptyList<Any>(), (products().byName("zzz", LIMIT) as ProductResults.Found).products)
    }

    @Test
    fun `une recherche hors ligne est injoignable`() = runTest {
        server.shutdown()

        assertEquals(ProductResults.Unreachable, products().byName("nutella", LIMIT))
    }

    @Test
    fun `la recherche ne reessaye pas`() = runTest {
        // Contrairement au code-barres : elle part d'un tap delibere, et l'ecran montre
        // deja le resultat. Faire attendre une seconde et demie ne gagnerait rien.
        server.enqueue(MockResponse().setResponseCode(SERVER_ERROR))

        assertEquals(ProductResults.Unreachable, products().byName("nutella", LIMIT))
        assertEquals(1, server.requestCount)
    }

    // --- Decor -------------------------------------------------------------------

    private val nutella = requireNotNull(Barcode.of("3017620422003"))
    private val upcA = requireNotNull(Barcode.of("012345678905"))

    /** La vraie pile HTTP, montée devant le serveur local — pas une copie. */
    private fun TestScope.products() = OpenFoodFactsProducts(
        api = openFoodFactsApi(baseUrl, openFoodFactsClient(ClientIdentity(USER_AGENT))),
        // Le francais : les fiches de ce fichier portent `product_name_fr`.
        languages = FixedLanguage(),
        ids = SequentialIdGenerator(),
        clock = FixedClock(RECUPERE_LE),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun searchOk(products: String) = MockResponse()
        .setResponseCode(OK)
        .setBody("""{"count":1,"page":1,"products":$products}""")

    private fun ok(product: String?, status: Int = 1) = MockResponse()
        .setResponseCode(OK)
        .setBody("""{"code":"3017620422003","status":$status,"status_verbose":"ok","product":${product ?: "null"}}""")
}

private val RECUPERE_LE: java.time.Instant = java.time.Instant.parse("2026-08-12T09:00:00Z")

private const val USER_AGENT = "Hexavore/0.2.0 (github.com/hexavore-app/hexavore)"
private const val OK = 200
private const val NOT_FOUND = 404
private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR = 503
private const val ATTEMPTS = 3
private const val LIMIT = 20

/**
 * Une réponse relevée sur la base, allégée mais **pas nettoyée** : `nutrition_grades`
 * et `_id` n'ont rien à faire dans le modèle, et c'est exactement pour ça qu'ils sont
 * là. Sans `ignoreUnknownKeys`, une clé de trop ferait échouer le décodage de toutes
 * les fiches d'un coup, pour un champ que personne ne lit.
 */
private val NUTELLA = """
    {
      "_id": "3017620422003",
      "code": "3017620422003",
      "product_name": "Nutella",
      "product_name_fr": "Nutella",
      "brands": "Ferrero, Nutella",
      "nutrition_grades": "e",
      "serving_size": "15 g",
      "serving_quantity": 15,
      "nutriments": {
        "energy-kcal_100g": 539,
        "energy_100g": 2252,
        "proteins_100g": 6.3,
        "carbohydrates_100g": 57.5,
        "sugars_100g": 58.4,
        "fat_100g": 30.9,
        "nova-group": 4
      }
    }
""".trimIndent()
