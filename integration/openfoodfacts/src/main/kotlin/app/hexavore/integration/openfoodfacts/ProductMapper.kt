package app.hexavore.integration.openfoodfacts

import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/**
 * Un kilocalorie vaut 4,184 kilojoules. Nommé parce qu'il apparaît deux fois et que
 * le voir écrit évite qu'un jour l'un des deux devienne 4,18.
 */
private const val KJ_PER_KCAL = 4.184

/**
 * La fiche que ce produit décrit, ou `null` s'il n'en décrit aucune.
 *
 * **Le nom est le seul champ bloquant**, et il l'est parce qu'une fiche sans nom n'est
 * ni affichable ni retrouvable ([docs/04][sources]). Les cinq teneurs manquantes, en
 * revanche, ne bloquent rien : elles restent inconnues, l'écran les montre en creux,
 * et le total du jour se déclare minoré ([D29][decisions]). C'est la règle du projet,
 * et c'est ici qu'elle se trahirait le plus facilement — les fibres manquent très
 * souvent dans cette base, et les mettre à zéro ferait paraître complète une journée
 * qui ne l'est pas.
 *
 * **La date de récupération est posée ici**, et non par l'appelant. C'est le module
 * qui interroge le service qui sait quand il l'a fait ; la laisser à un cas d'usage
 * obligerait **chaque** chemin de récupération à y penser, et le second — la recherche
 * par nom — a montré que ça s'oublie.
 *
 * **La référence enregistrée est le code demandé, pas celui que la réponse renvoie.**
 * C'est ce code-là que le prochain scan présentera au catalogue local ; y ranger la
 * variante publiée par le service rendrait le cache muet au deuxième passage, et ça
 * ne se verrait qu'en mode avion.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
internal fun ProductDto.toFood(barcode: Barcode, id: FoodId, fetchedAt: Instant, language: ContentLanguage): Food? =
    displayName(language)?.let { name ->
        Food(
            id = id,
            source = FoodSource.OFF,
            sourceRef = barcode.value,
            name = name,
            brand = firstBrand(),
            // Un produit emballe n'a pas de rayon : les huit du bandeau viennent des
            // groupes de la table de l'ANSES, et « ne pas avoir de rayon » est une
            // reponse legitime que Food documente deja.
            category = null,
            per100g = nutriments.toNutrientValues(),
            defaultServingG = defaultServing(),
            isLiquid = servingOf(servingSize)?.liquid,
            fetchedAt = fetchedAt,
        )
    }

/**
 * La même fiche, mais dont le code vient de la **réponse** et non de la demande.
 *
 * C'est le cas de la recherche par nom : on n'a pas présenté de code, le service en
 * rend un. `null` quand il n'est pas lisible — sans code canonique, la fiche ne peut
 * ni être mise en cache sans doublon ni être retrouvée par un scan, et elle
 * reviendrait du réseau à chaque recherche.
 */
internal fun ProductDto.toFound(id: FoodId, fetchedAt: Instant, language: ContentLanguage): Food? =
    code?.let(Barcode::of)?.let { barcode -> toFood(barcode, id, fetchedAt, language) }

/**
 * Le nom dans la langue en cours d'abord, l'international ensuite.
 *
 * Une chaîne vide compte comme absente : la base en contient, et « " " » afficherait
 * une ligne sans titre au lieu d'ouvrir le formulaire de création.
 *
 * **Le repli est `product_name` et non l'anglais.** Ce champ-là est celui que le
 * contributeur a rempli dans la langue du paquet qu'il tenait : pour un produit vendu en
 * France, c'est du français, et c'est le nom écrit sur la boîte qu'on a scannée. Il aide
 * davantage à reconnaître une fiche qu'une traduction absente.
 */
private fun ProductDto.displayName(language: ContentLanguage): String? = localisedName(language)
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: name?.trim()?.takeIf(String::isNotEmpty)

/** Sans branche `else` : une langue de plus ne compile pas sans son champ dans le DTO. */
private fun ProductDto.localisedName(language: ContentLanguage): String? = when (language) {
    ContentLanguage.FRENCH -> nameFr
    ContentLanguage.ENGLISH -> nameEn
}

/**
 * La première marque déclarée.
 *
 * `brands` est une liste séparée par des virgules, et elle contient couramment la
 * marque, le groupe et la déclinaison. La première est celle qui est écrite sur le
 * paquet, c'est-à-dire celle qui aide à reconnaître la fiche dans une liste.
 */
private fun ProductDto.firstBrand(): String? = brands?.substringBefore(',')?.trim()?.takeIf(String::isNotEmpty)

/**
 * La quantité proposée à l'ouverture : la portion de l'emballage, sinon rien.
 *
 * Rien et non 100 g : c'est [Food.defaultServingG] qui porte déjà cette convention,
 * et l'écrire deux fois ferait deux endroits à tenir d'accord.
 *
 * Une portion nulle est écartée, alors qu'une teneur nulle ne l'est pas : « 0 g de
 * fibres » est une mesure, « une portion de 0 g » est une saisie ratée.
 */
private fun ProductDto.defaultServing(): Double? =
    servingQuantity.asMeasurement()?.takeIf { it > 0.0 } ?: servingOf(servingSize)?.grams

/**
 * Les six teneurs, l'énergie ramenée en kilocalories.
 *
 * **`energy-kcal_100g` fait foi quand il existe.** La conversion ne va que dans un
 * sens : diviser par 4,184 une valeur déjà exprimée en kilocalories donnerait un
 * quart des calories réelles, ce qui est un chiffre plausible — donc un défaut qu'on
 * ne remarque pas.
 */
private fun NutrimentsDto?.toNutrientValues(): NutrientValues = NutrientValues(
    kcal = this?.kilocalories(),
    protein = this?.proteins.asMeasurement(),
    carbs = this?.carbohydrates.asMeasurement(),
    sugars = this?.sugars.asMeasurement(),
    fat = this?.fat.asMeasurement(),
    fiber = this?.fiber.asMeasurement(),
)

private fun NutrimentsDto.kilocalories(): Double? = energyKcal.asMeasurement()
    ?: (energyKj.asMeasurement() ?: energy.asMeasurement())?.div(KJ_PER_KCAL)

/**
 * La valeur, quand c'en est une.
 *
 * Nombre ou chaîne, les deux se lisent ; un objet, un tableau ou `null` rendent
 * `null`. Une valeur négative aussi : elle n'existe pas en nutrition, et l'accepter
 * ferait entrer un chiffre faux dans un journal qui, lui, fige ce qu'on lui donne.
 *
 * **Zéro, en revanche, est une valeur** — une eau minérale a zéro calorie, et c'est
 * une mesure, pas une absence.
 */
private fun JsonElement?.asMeasurement(): Double? = (this as? JsonPrimitive)
    ?.contentOrNull
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.takeIf { it >= 0.0 }
