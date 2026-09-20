package app.hexavore.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * La forme du fichier, **distincte des types du domaine**.
 *
 * Un `@Serializable` posé sur `Dish` ou sur `Food` aurait lié le format de fichier aux
 * noms de champs du code : renommer une propriété pour la clarté aurait rendu illisible
 * toute sauvegarde antérieure, sans que rien ne le signale. Ici, un renommage dans le
 * domaine se voit à la compilation du mappeur, et le fichier ne bouge pas.
 *
 * C'est la même séparation qu'entre le domaine et Room, et qu'entre le domaine et les
 * réponses d'Open Food Facts. Elle coûte un mappeur ; elle achète que le format soit
 * une décision, et non une conséquence.
 *
 * Les noms suivent [docs/09][donnees], qui montre le JSON attendu.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
@Serializable
internal data class SnapshotDto(
    @SerialName("formatVersion") val formatVersion: Int,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("exportedAt") val exportedAt: String,
    @SerialName("attribution") val attribution: Map<String, String> = emptyMap(),
    @SerialName("profile") val profile: ProfileDto? = null,
    @SerialName("goals") val goals: List<GoalDto> = emptyList(),
    @SerialName("weights") val weights: List<WeightDto> = emptyList(),
    @SerialName("dishes") val dishes: List<DishDto> = emptyList(),
    @SerialName("entries") val entries: List<EntryDto> = emptyList(),
    @SerialName("foods") val foods: List<FoodDto> = emptyList(),
    @SerialName("favorites") val favorites: List<FavoriteDto> = emptyList(),
    @SerialName("adjustment") val adjustment: AdjustmentDto = AdjustmentDto(),
)

@Serializable
internal data class ProfileDto(
    val birthDate: String,
    val sex: String,
    val heightCm: Double,
    val activityLevel: String,
    val unitSystem: String,
)

@Serializable
internal data class GoalDto(
    val id: String,
    val startedAt: String,
    val endedAt: String? = null,
    val origin: String,
    val strategy: String,
    val targetWeightKg: Double? = null,
    val targetDate: String? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val sugars: Double,
    val fat: Double,
    val fiber: Double,
)

@Serializable
internal data class WeightDto(val date: String, val weightKg: Double)

@Serializable
internal data class DishDto(
    val id: String,
    val date: String,
    val source: String,
    val loggedAt: String,
    val favoriteId: String? = null,
    /**
     * Le nom écrit à la main, et le moment retenu à la saisie.
     *
     * **Facultatifs, donc le format ne change pas de version** : un fichier écrit
     * avant [D118][decisions] se relit sans eux, et ses plats retrouvent le nom que
     * leur heure leur donne. C'est exactement ce que fait la base pour ses propres
     * lignes d'avant.
     *
     * [decisions]: docs/11-decisions.md
     */
    val title: String? = null,
    val moment: String? = null,
)

/**
 * Une ligne de journal.
 *
 * **À plat, et non imbriquée dans son plat**, comme [docs/09][donnees] la montre : un
 * fichier qu'on veut « lisible, inspectable, réparable à la main » se relit mieux en
 * tables parallèles, et `dishId` suffit à rattacher.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
@Serializable
internal data class EntryDto(
    val id: String,
    val dishId: String,
    val foodId: String? = null,
    val displayName: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugars: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

@Serializable
internal data class FoodDto(
    val id: String,
    val source: String,
    val sourceRef: String? = null,
    val name: String,
    val brand: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugars: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    /**
     * Ni les portions nommées, ni les teneurs complétées, ni le rayon, ni le titre
     * court.
     *
     * **Ce sont des propriétés de la référence, pas de la copie** ([D54][decisions]) :
     * une fiche de l'ANSES les relit dans la base embarquée par son code, et la table
     * locale ne les stocke pas. Les écrire dans le fichier reviendrait à y mettre des
     * listes toujours vides — ou pire, à figer la correspondance du jour de l'export.
     *
     * Le poids proposé par défaut, lui, appartient bien à la copie : c'est ce que la
     * fiche a annoncé quand on l'a récupérée.
     *
     * [decisions]: docs/11-decisions.md
     */
    val defaultServingG: Double? = null,
    val isLiquid: Boolean? = null,
    val fetchedAt: String? = null,
    val lastUsedAt: String? = null,
    val useCount: Int = 0,
    val favorite: Boolean = false,
)

@Serializable
internal data class FavoriteDto(
    val id: String,
    val name: String,
    val useCount: Int = 0,
    val components: List<ComponentDto> = emptyList(),
)

@Serializable
internal data class ComponentDto(
    val foodId: String? = null,
    val name: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugars: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

@Serializable
internal data class AdjustmentDto(
    val enabled: Boolean = true,
    val lastAcceptedOn: String? = null,
    val lastIgnoredOn: String? = null,
)

/**
 * Les licences des deux sources, écrites dans chaque fichier.
 *
 * Elles ne servent à rien au code, et c'est le but : un fichier qui transporte des
 * données d'Open Food Facts et de l'ANSES doit dire d'où elles viennent, y compris
 * quand il finit sur une clé USB dix ans plus tard.
 */
internal val ATTRIBUTION = mapOf(
    "openFoodFacts" to "Contient des données d'Open Food Facts, sous licence ODbL 1.0",
    "ciqual" to "Table CIQUAL 2025 — ANSES, Licence Ouverte Etalab 2.0",
)
