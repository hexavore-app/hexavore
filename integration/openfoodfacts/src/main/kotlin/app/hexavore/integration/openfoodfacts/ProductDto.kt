package app.hexavore.integration.openfoodfacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * L'enveloppe de l'API v2 : un état et, s'il vaut 1, un produit.
 *
 * **Les deux sont lus.** La v2 rend un `404` pour un code inconnu, mais la même base
 * a longtemps répondu `200` avec `status = 0`, et un service collaboratif qui a déjà
 * changé de convention peut en changer encore. Vérifier les deux coûte une ligne ;
 * ne vérifier que le code HTTP ferait annoncer « trouvé » une fiche absente.
 */
@Serializable
internal data class ProductEnvelope(val status: Int = 0, val product: ProductDto? = null)

/**
 * L'enveloppe de la recherche par nom.
 *
 * Pas d'état ici : le service rend une liste, et une liste vide **est** une réponse.
 */
@Serializable
internal data class SearchEnvelope(val products: List<ProductDto> = emptyList())

/**
 * Un produit, tel qu'Open Food Facts le publie.
 *
 * **Tous les champs sont facultatifs, sans exception.** Ce n'est pas de la prudence :
 * la base est alimentée par ses utilisateurs, et une fiche qui n'a qu'un code-barres
 * et une marque existe pour de bon. Un champ déclaré non nul ferait échouer le
 * décodage entier — donc perdrait aussi les valeurs présentes.
 */
@Serializable
internal data class ProductDto(
    val code: String? = null,
    @SerialName("product_name") val name: String? = null,
    /**
     * Les noms traduits, un champ par langue de l'application.
     *
     * Déclarés et non déduits : kotlinx.serialization veut des champs écrits. C'est
     * [localisedName] qui porte le `when` exhaustif, donc une langue de plus ne compile
     * pas tant qu'elle n'a pas son champ ici et sa branche là-bas.
     */
    @SerialName("product_name_fr") val nameFr: String? = null,
    @SerialName("product_name_en") val nameEn: String? = null,
    val brands: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    /**
     * Un nombre… ou une chaîne. Les deux écritures existent dans la base, pour le
     * même champ. Déclaré en [JsonElement] et converti à la lecture : typé `Double`,
     * une fiche sur dix ferait échouer le décodage de tout le produit.
     */
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    val nutriments: NutrimentsDto? = null,
)

/**
 * Les teneurs pour 100 g — ou pour 100 ml, la base ne les distingue pas.
 *
 * Ce n'est pas un défaut à corriger : une boisson dont les valeurs sont données pour
 * 100 ml et dont la portion est écrite « 250 ml » se calcule juste, à condition de
 * traiter les deux unités de la même façon. C'est ce que fait [gramsOfServing].
 *
 * Chaque valeur est un [JsonElement] pour la raison donnée sur `serving_quantity`, et
 * pour une seconde : un champ qui arrive en objet ou en tableau — ça se voit — ne doit
 * pas emporter les cinq autres avec lui.
 */
@Serializable
internal data class NutrimentsDto(
    @SerialName("energy-kcal_100g") val energyKcal: JsonElement? = null,
    @SerialName("energy-kj_100g") val energyKj: JsonElement? = null,
    @SerialName("energy_100g") val energy: JsonElement? = null,
    @SerialName("proteins_100g") val proteins: JsonElement? = null,
    @SerialName("carbohydrates_100g") val carbohydrates: JsonElement? = null,
    @SerialName("sugars_100g") val sugars: JsonElement? = null,
    @SerialName("fat_100g") val fat: JsonElement? = null,
    @SerialName("fiber_100g") val fiber: JsonElement? = null,
)
