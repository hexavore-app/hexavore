package app.hexavore.integration.openfoodfacts

import app.hexavore.domain.language.ContentLanguage
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * L'API v2 d'Open Food Facts, réduite à ce que l'application demande.
 *
 * Elle rend un [Response] et non le corps décodé : un code inconnu se signale par un
 * `404`, et un service surchargé par un `429` ou un `5xx`. Ces trois-là appellent
 * trois conduites différentes, qu'un corps seul ne permettrait pas de distinguer —
 * Retrofit lèverait une exception pour les trois.
 */
internal interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun product(@Path("barcode") barcode: String, @Query("fields") fields: String): Response<ProductEnvelope>

    /**
     * La recherche par nom, sur l'ancien point d'entrée.
     *
     * `cgi/search.pl` et non `api/v2/search` : le second filtre sur des étiquettes —
     * catégories, marques, pays — et n'accepte pas de texte libre. Celui-ci le fait
     * depuis toujours, et c'est ce qu'on lui demande. Il rend les mêmes objets
     * produit, donc la même correspondance les lit.
     */
    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms") terms: String,
        @Query("page_size") limit: Int,
        @Query("fields") fields: String,
        @Query("json") json: Int = 1,
    ): Response<SearchEnvelope>
}

/**
 * Les champs demandés, et rien de plus.
 *
 * Une fiche complète d'Open Food Facts pèse plusieurs dizaines de kilo-octets ;
 * celle-ci en pèse quelques centaines d'octets, et c'est ce qui tient le budget de
 * deux secondes sur une connexion mobile. Les champs absents de cette liste sont
 * absents du modèle : en demander un que rien ne lit serait le même travers qu'une
 * colonne que rien ne remplit.
 */
internal val PRODUCT_FIELDS =
    (
        listOf("code", "product_name") +
            ContentLanguage.entries.map { "product_name_${it.tag}" } +
            listOf("brands", "serving_size", "serving_quantity", "nutriments")
        ).joinToString(",")

/** L'instance publique, celle qui sert toutes les langues. */
internal const val OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/"
