package app.hexavore.integration.openfoodfacts

import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.ProductLookup
import app.hexavore.domain.food.ProductResults
import app.hexavore.domain.food.ProductSearch
import app.hexavore.domain.food.ProductSource
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * Open Food Facts, vu depuis le domaine.
 *
 * @see docs/04-sources-de-donnees.md
 */
internal class OpenFoodFactsProducts(
    private val api: OpenFoodFactsApi,
    private val languages: ContentLanguages,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : ProductSource,
    ProductSearch {
    override suspend fun byBarcode(code: Barcode): ProductLookup = withContext(dispatchers.io) {
        retrying { attemptOnce(code) }
    }

    /**
     * La recherche par nom.
     *
     * **Aucun retrait exponentiel ici**, contrairement au code-barres : celle-ci part
     * d'un tap délibéré et l'écran montre déjà le résultat. Faire attendre une seconde
     * et demie avant de dire « pas de connexion » ne gagnerait rien — l'utilisateur
     * peut simplement retoucher la ligne.
     *
     * Une liste vide **est** une réponse : le service ne connaît rien de ce nom.
     */
    override suspend fun byName(query: String, limit: Int): ProductResults = withContext(dispatchers.io) {
        try {
            val fetchedAt = clock.now()
            api
                .search(terms = query, limit = limit, fields = PRODUCT_FIELDS)
                .body()
                ?.products
                ?.mapNotNull {
                    it.toFound(id = FoodId(ids.next()), fetchedAt = fetchedAt, language = languages.current())
                }
                ?.let(ProductResults::Found)
                ?: ProductResults.Unreachable
        } catch (offline: IOException) {
            offline.toUnreachableSearch()
        }
    }

    /**
     * Une tentative. `null` veut dire « recommence », et rien d'autre.
     *
     * Trois issues du service, trois conduites : `404` — ou `200` avec un état à
     * zéro — est une réponse et n'est pas à réessayer ; `429` et `5xx` disent
     * « plus tard » ; une `IOException` dit qu'on n'a pas pu demander.
     *
     * **Une panne réseau n'est pas réessayée.** Le retrait sert à laisser passer une
     * surcharge du service ; hors ligne, il ne ferait que retarder d'une seconde et
     * demie une phrase qu'on peut dire tout de suite, et l'utilisateur est debout
     * devant un rayon avec son téléphone à la main.
     */
    private suspend fun attemptOnce(code: Barcode): ProductLookup? = try {
        api.product(code.value, PRODUCT_FIELDS).let { response ->
            if (response.code().retryable) null else response.toLookup(code)
        }
    } catch (offline: IOException) {
        offline.toUnreachable()
    }

    /**
     * Une fiche, ou rien.
     *
     * **Rien couvre trois cas qui n'en font qu'un pour l'utilisateur** : le service
     * répond `404`, il répond `200` avec un état à zéro — l'ancienne convention de la
     * base, encore possible — ou il rend un produit **sans nom**, donc inutilisable.
     * Tous trois ouvrent le même écran : le code lu, et la proposition de créer la
     * fiche à la main.
     */
    private fun Response<ProductEnvelope>.toLookup(code: Barcode): ProductLookup {
        val food = body()
            ?.takeIf { envelope -> envelope.status == FOUND_STATUS }
            ?.product
            ?.toFood(
                barcode = code,
                id = FoodId(ids.next()),
                fetchedAt = clock.now(),
                language = languages.current(),
            )

        return food?.let(ProductLookup::Found) ?: ProductLookup.Unknown
    }
}

/**
 * Le retrait exponentiel : trois tentatives, l'attente doublant après chaque refus.
 *
 * **Il vit ici et non dans un intercepteur**, contrairement à ce qu'annonçait
 * [docs/04][sources]. Un intercepteur attend avec `Thread.sleep`, c'est-à-dire en
 * immobilisant un fil du répartiteur d'OkHttp ; ici, `delay` suspend. Et c'est ce qui
 * rend les trois tentatives **éprouvables** : sous `runTest`, l'attente est du temps
 * virtuel, et le cas s'exécute en millisecondes au lieu d'une seconde et demie.
 *
 * On n'attend pas après la dernière tentative : il n'y a plus rien derrière.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
private suspend fun retrying(attempt: suspend () -> ProductLookup?): ProductLookup {
    var outcome: ProductLookup? = null
    var made = 0

    while (outcome == null && made < MAX_ATTEMPTS) {
        outcome = attempt()
        made++
        if (outcome == null && made < MAX_ATTEMPTS) delay(FIRST_BACKOFF_MILLIS shl (made - 1))
    }

    // Trois refus d'affilee ne disent rien de plus qu'un seul : le service ne repond
    // pas maintenant, et c'est exactement ce que « injoignable » veut dire.
    return outcome ?: ProductLookup.Unreachable
}

/**
 * Hors ligne, DNS muet, connexion coupée en plein transfert : l'écran dit la même
 * chose des trois, et ce projet n'a aucun journal où en écrire le détail — c'est la
 * contrepartie assumée de « aucune télémétrie ». La fonction existe pour que la
 * réduction soit **écrite** plutôt que faite en silence dans un `catch` vide.
 */
private fun IOException.toUnreachable(): ProductLookup = ProductLookup.Unreachable

/** Même réduction, pour la recherche. Voir ci-dessus. */
private fun IOException.toUnreachableSearch(): ProductResults = ProductResults.Unreachable

private const val MAX_ATTEMPTS = 3
private const val FIRST_BACKOFF_MILLIS = 300L
private const val FOUND_STATUS = 1
private const val TOO_MANY_REQUESTS = 429
private const val FIRST_SERVER_ERROR = 500

/** Ce qui vaut la peine d'être redemandé : la limite de courtoisie, et les pannes du service. */
private val Int.retryable: Boolean get() = this == TOO_MANY_REQUESTS || this >= FIRST_SERVER_ERROR
