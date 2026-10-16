package app.hexavore.feature.entry

import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.nutrition.Macro
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Ce que l'écran de validation garde pour survivre à la mort du processus ([D166][decisions]).
 *
 * **Le formulaire tel qu'il est tapé**, champs de texte compris : « 12, » doit revenir
 * « 12, », pas « 12 ». Et le formulaire tel qu'il s'était ouvert, sans quoi la question
 * « quitter perd-il quelque chose ? » n'aurait plus de point de comparaison.
 *
 * **Le chemin de la photo, jamais ses octets** : elle attend dans le dépôt, que le
 * balayage du lancement épargne.
 *
 * **Des types à part et non le domaine** : `Food` et `Suggestion` ne sont pas
 * sérialisables, et les rendre tels ferait dépendre le domaine d'un format qui ne regarde
 * que cet écran. Les énumérations passent par leur nom ; un nom inconnu rend la mémoire
 * illisible, et l'écran repart alors de sa route.
 *
 * [decisions]: docs/11-decisions.md
 */
@Serializable
internal data class EntryMemory(
    val form: FormMemoryState,
    val opened: FormMemoryState? = null,
    val photo: String? = null,
    val photoRemoved: Boolean = false,
    val photoChoices: Int = 0,
)

/** La mémoire relue, dans les types de l'écran. */
internal data class RecalledEntry(
    val form: EntryForm,
    val opened: EntryForm?,
    val photo: PhotoFile?,
    val photoRemoved: Boolean,
    val photoChoices: Int,
)

internal fun EntryMemory.recalled(): RecalledEntry = RecalledEntry(
    form = form.toForm(),
    opened = opened?.toForm(),
    photo = photo?.let(::PhotoFile),
    photoRemoved = photoRemoved,
    photoChoices = photoChoices,
)

@Serializable
internal data class FormMemoryState(
    val dishId: String? = null,
    val date: Long,
    val source: String,
    val lines: List<LineMemory>,
    val favoriteId: String? = null,
    val title: String? = null,
    val moment: String,
)

internal fun EntryForm.toMemory() = FormMemoryState(
    dishId = dishId?.value,
    date = date.toEpochDay(),
    source = source.name,
    lines = lines.map { it.toMemory() },
    favoriteId = favoriteId?.value,
    title = title,
    moment = moment.name,
)

private fun FormMemoryState.toForm() = EntryForm(
    dishId = dishId?.let(::DishId),
    date = LocalDate.ofEpochDay(date),
    source = EntrySource.valueOf(source),
    lines = lines.map { it.toLine() },
    favoriteId = favoriteId?.let(::FavoriteDishId),
    title = title,
    moment = MealMoment.valueOf(moment),
)

@Serializable
internal data class LineMemory(
    val id: String,
    val entryId: String? = null,
    val foodId: String? = null,
    val food: FoodMemory? = null,
    val name: String = "",
    val quantity: String = "",
    val unit: UnitMemory = UnitMemory.GRAM,
    val macros: Map<String, String> = emptyMap(),
    val servings: List<UnitMemory> = emptyList(),
    val reference: ValuesMemory? = null,
    val edited: Set<String> = emptySet(),
    val estimated: Set<String> = emptySet(),
    val revision: Int = 0,
    val substitutions: Int = 0,
    val suggestion: SuggestionMemory? = null,
)

private fun EntryFormLine.toMemory() = LineMemory(
    id = id.value,
    entryId = entryId?.value,
    foodId = foodId?.value,
    food = food?.toMemory(),
    name = name,
    quantity = quantity,
    unit = unit.toMemory(),
    macros = macros.mapKeys { (macro, _) -> macro.name },
    servings = servings.map { it.toMemory() },
    reference = reference?.toMemory(),
    edited = edited.mapTo(mutableSetOf()) { it.name },
    estimated = estimated.mapTo(mutableSetOf()) { it.name },
    revision = revision,
    substitutions = substitutions,
    suggestion = suggestion?.toMemory(),
)

private fun LineMemory.toLine() = EntryFormLine(
    id = DraftLineId(id),
    entryId = entryId?.let(::EntryId),
    foodId = foodId?.let(::FoodId),
    food = food?.toFood(),
    name = name,
    quantity = quantity,
    unit = unit.toUnit(),
    macros = macros.mapKeys { (name, _) -> Macro.valueOf(name) },
    servings = servings.map { QuantityUnit.Serving(it.code, it.grams) },
    reference = reference?.toValues(),
    edited = edited.mapTo(mutableSetOf(), Macro::valueOf),
    estimated = estimated.mapTo(mutableSetOf(), Macro::valueOf),
    revision = revision,
    substitutions = substitutions,
    suggestion = suggestion?.toSuggestion(),
)
