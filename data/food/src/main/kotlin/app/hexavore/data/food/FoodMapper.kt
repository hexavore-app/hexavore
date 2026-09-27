package app.hexavore.data.food

import app.hexavore.core.database.ciqual.CiqualAnnotations
import app.hexavore.core.database.ciqual.CiqualFoodRow
import app.hexavore.core.database.ciqual.CiqualServingRow
import app.hexavore.core.database.entity.FoodEntity
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodCategory
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodServing
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.food.SearchText
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import java.time.Instant

/**
 * La correspondance entre les deux bases et le domaine.
 *
 * Deux origines, un seul type de sortie : un aliment de la table de l'ANSES et un
 * aliment personnel se présentent pareil à l'écran de recherche, et c'est ce qui lui
 * permet de n'avoir aucune branche sur la provenance.
 *
 * **Les huit valeurs traversent telles quelles.** Un `?: 0.0` sur l'une d'elles
 * serait la dernière occasion de perdre la distinction entre inconnu et zéro.
 */
fun FoodEntity.toDomain(
    servings: List<FoodServing> = emptyList(),
    /**
     * Ce que la table de l'ANSES sait de cette fiche, ou `null` si elle n'en vient pas.
     *
     * **Trois choses qui ne vivent pas dans la copie** : le libellé, le rayon et le titre
     * court. Une copie les figerait au jour où elle a été faite — et, depuis que la table
     * est bilingue, à la **langue** de ce jour-là : une fiche copiée en français puis
     * relue en anglais aurait porté un titre court anglais sur un nom français.
     */
    annotations: CiqualAnnotations? = null,
) = Food(
    id = FoodId(id),
    source = source.toFoodSource(),
    sourceRef = sourceRef,
    // Le libellé de la référence quand il y en a une, celui de la copie sinon. Un
    // aliment personnel ou un produit scanné n'a que le second : il a été nommé une
    // fois, par quelqu'un, et aucune table ne le renomme.
    name = annotations?.name ?: name,
    shortName = annotations?.shortName,
    brand = brand,
    category = annotations?.category.toFoodCategory(),
    per100g = NutrientValues(
        kcal = kcal100,
        protein = protein100,
        carbs = carb100,
        sugars = sugar100,
        fat = fat100,
        fiber = fiber100,
    ),
    servings = servings,
    defaultServingG = defaultServingG,
    isLiquid = isLiquid,
    fetchedAt = fetchedAt?.let(Instant::ofEpochMilli),
    lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    useCount = useCount,
    favorite = isFavorite,
)

/**
 * Une provenance inconnue retombe sur [FoodSource.CUSTOM].
 *
 * C'est la lecture la plus prudente : une base écrite par une version plus récente
 * ne doit pas rendre le catalogue illisible, et prendre une fiche inconnue pour un
 * aliment personnel ne lui attribue aucune autorité qu'elle n'a pas.
 */
private fun String.toFoodSource(): FoodSource = FoodSource.entries.firstOrNull { it.name == this } ?: FoodSource.CUSTOM

/**
 * Un rayon inconnu vaut **aucun rayon**.
 *
 * Même lecture prudente que ci-dessus, et le repli est ici sans conséquence : une
 * base écrite par une version plus récente peut nommer un rayon que celle-ci ne
 * connaît pas encore. Le lui inventer une correspondance ferait sortir l'aliment
 * sous une pastille au hasard ; ne rien lui donner le laisse trouvable par son nom.
 */
internal fun String?.toFoodCategory(): FoodCategory? = FoodCategory.entries.firstOrNull { it.name == this }

fun Food.toEntity(now: Long) = FoodEntity(
    id = id.value,
    source = source.name,
    sourceRef = sourceRef,
    name = name,
    // Calculee ici et stockee : la recherche compare une saisie normalisee a cette
    // colonne, et la recalculer a chaque frappe serait le seul endroit du parcours a
    // depenser le budget de 150 ms sans raison.
    nameSearch = SearchText.normalise(name),
    brand = brand,
    kcal100 = per100g.kcal,
    protein100 = per100g.protein,
    carb100 = per100g.carbs,
    sugar100 = per100g.sugars,
    fat100 = per100g.fat,
    fiber100 = per100g.fiber,
    // CIQUAL les publie, Open Food Facts aussi. La v1 ne les affiche pas ; les
    // perdre a la copie obligerait a tout reimporter le jour ou on les montre.
    saturatedFat100 = null,
    salt100 = null,
    defaultServingG = defaultServingG,
    isLiquid = isLiquid,
    fetchedAt = fetchedAt?.toEpochMilli(),
    lastUsedAt = lastUsedAt?.toEpochMilli(),
    useCount = useCount,
    isFavorite = favorite,
    createdAt = now,
    updatedAt = now,
)

/**
 * Un aliment de la table de l'ANSES, tel qu'il se présente **avant** d'être copié
 * dans le catalogue.
 *
 * Son identifiant est provisoire : il n'entre dans `food` que le jour où il est
 * réellement consommé. Copier les 3 484 lignes à l'installation gonflerait la base,
 * les sauvegardes et la recherche avec 99 % de contenu jamais utilisé.
 */
internal fun CiqualFoodRow.toDomain(id: FoodId, servings: List<CiqualServingRow>) = Food(
    id = id,
    source = FoodSource.CIQUAL,
    sourceRef = code,
    name = name,
    shortName = shortName,
    brand = null,
    category = category.toFoodCategory(),
    // **L'originale l'emporte toujours.** La complétion n'intervient que là où
    // l'ANSES n'a rien déterminé, et le champ suivant dit lesquelles ont servi.
    per100g = NutrientValues(
        kcal = kcal100 ?: estimated.kcal100,
        protein = protein100 ?: estimated.protein100,
        carbs = carb100 ?: estimated.carb100,
        sugars = sugar100 ?: estimated.sugar100,
        fat = fat100 ?: estimated.fat100,
        fiber = fiber100 ?: estimated.fiber100,
    ),
    estimated = estimatedMacros(),
    servings = servings.map { FoodServing(label = it.label, grams = it.grams, isDefault = it.isDefault) },
)

/**
 * Les compteurs dont la valeur affichée vient d'un modèle.
 *
 * **Une complétion qui ne sert pas n'est pas signalée.** Le jour où l'ANSES publie
 * enfin la teneur, la mesure l'emporte et la ligne cesse d'être marquée — même si le
 * fichier porte encore l'estimation. C'est ce qui rend la règle sûre : la marque suit
 * ce qui est affiché, pas ce qui est stocké.
 *
 * Le `when` est exhaustif sur [Macro] : ajouter un septième compteur cesserait de
 * compiler ici plutôt que de l'oublier en silence.
 */
private fun CiqualFoodRow.estimatedMacros(): Set<Macro> = Macro.entries
    .filterTo(mutableSetOf()) { macro ->
        val measured = when (macro) {
            Macro.CALORIES -> kcal100
            Macro.PROTEIN -> protein100
            Macro.CARBS -> carb100
            Macro.SUGARS -> sugar100
            Macro.FAT -> fat100
            Macro.FIBER -> fiber100
        }
        val completed = when (macro) {
            Macro.CALORIES -> estimated.kcal100
            Macro.PROTEIN -> estimated.protein100
            Macro.CARBS -> estimated.carb100
            Macro.SUGARS -> estimated.sugar100
            Macro.FAT -> estimated.fat100
            Macro.FIBER -> estimated.fiber100
        }
        measured == null && completed != null
    }

internal fun CiqualServingRow.toDomain() = FoodServing(label = label, grams = grams, isDefault = isDefault)
