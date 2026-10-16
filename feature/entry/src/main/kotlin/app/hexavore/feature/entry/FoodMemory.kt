package app.hexavore.feature.entry

import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.diary.Suggestion
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodCategory
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodServing
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.resolution.MatchVerdict
import kotlinx.serialization.Serializable
import java.time.Instant

// Les pieces d'une ligne que l'ecran ne saisit pas -- la fiche, sa suggestion, ses
// unites, ses valeurs --, gardees avec elle pour survivre a la mort du processus (D166).
// A part de EntryMemory.kt pour la seule raison du seuil de fonctions par fichier : la
// regle de la memoire est la-bas.

/**
 * Une unité, avec ce qui la distingue d'une portion.
 *
 * **Un drapeau et non le seul code** : une portion peut s'appeler « g » sur une fiche
 * d'Open Food Facts, et la relire comme le gramme lui ferait perdre son poids.
 */
@Serializable
internal data class UnitMemory(val code: String, val grams: Double, val serving: Boolean) {
    companion object {
        val GRAM = UnitMemory(QuantityUnit.Gram.code, QuantityUnit.Gram.gramsPerUnit, serving = false)
    }
}

internal fun QuantityUnit.toMemory() = UnitMemory(code, gramsPerUnit, serving = this is QuantityUnit.Serving)

internal fun UnitMemory.toUnit(): QuantityUnit = when {
    serving -> QuantityUnit.Serving(code, grams)
    else -> listOf(QuantityUnit.Gram, QuantityUnit.Millilitre, QuantityUnit.Ounce, QuantityUnit.FluidOunce)
        .first { it.code == code }
}

@Serializable
internal data class ValuesMemory(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugars: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

internal fun NutrientValues.toMemory() = ValuesMemory(kcal, protein, carbs, sugars, fat, fiber)

internal fun ValuesMemory.toValues() = NutrientValues(kcal, protein, carbs, sugars, fat, fiber)

@Serializable
internal data class FoodMemory(
    val id: String,
    val source: String,
    val sourceRef: String? = null,
    val name: String,
    val shortName: String? = null,
    val commonNames: Set<String> = emptySet(),
    val brand: String? = null,
    val category: String? = null,
    val per100g: ValuesMemory,
    val estimated: Set<String> = emptySet(),
    val servings: List<ServingMemory> = emptyList(),
    val defaultServingG: Double? = null,
    val isLiquid: Boolean? = null,
    val fetchedAt: Long? = null,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
    val favorite: Boolean = false,
)

@Serializable
internal data class ServingMemory(val label: String, val grams: Double, val isDefault: Boolean = false)

internal fun Food.toMemory() = FoodMemory(
    id = id.value,
    source = source.name,
    sourceRef = sourceRef,
    name = name,
    shortName = shortName,
    commonNames = commonNames,
    brand = brand,
    category = category?.name,
    per100g = per100g.toMemory(),
    estimated = estimated.mapTo(mutableSetOf()) { it.name },
    servings = servings.map { ServingMemory(it.label, it.grams, it.isDefault) },
    defaultServingG = defaultServingG,
    isLiquid = isLiquid,
    fetchedAt = fetchedAt?.toEpochMilli(),
    lastUsedAt = lastUsedAt?.toEpochMilli(),
    useCount = useCount,
    favorite = favorite,
)

internal fun FoodMemory.toFood() = Food(
    id = FoodId(id),
    source = FoodSource.valueOf(source),
    sourceRef = sourceRef,
    name = name,
    shortName = shortName,
    commonNames = commonNames,
    brand = brand,
    category = category?.let(FoodCategory::valueOf),
    per100g = per100g.toValues(),
    estimated = estimated.mapTo(mutableSetOf(), Macro::valueOf),
    servings = servings.map { FoodServing(it.label, it.grams, it.isDefault) },
    defaultServingG = defaultServingG,
    isLiquid = isLiquid,
    fetchedAt = fetchedAt?.let(Instant::ofEpochMilli),
    lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    useCount = useCount,
    favorite = favorite,
)

@Serializable
internal data class SuggestionMemory(
    val confidence: Float,
    val verdict: String,
    val alternatives: List<FoodMemory> = emptyList(),
    val estimated: Boolean,
    val estimatedMacros: Boolean = false,
)

internal fun Suggestion.toMemory() = SuggestionMemory(
    confidence = confidence,
    verdict = verdict.name,
    alternatives = alternatives.map { it.toMemory() },
    estimated = estimated,
    estimatedMacros = estimatedMacros,
)

internal fun SuggestionMemory.toSuggestion() = Suggestion(
    confidence = confidence,
    verdict = MatchVerdict.valueOf(verdict),
    alternatives = alternatives.map { it.toFood() },
    estimated = estimated,
    estimatedMacros = estimatedMacros,
)
