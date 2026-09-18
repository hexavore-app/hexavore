package app.hexavore.data.backup

import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.nutrition.Macros
import java.time.LocalDate

/** Les plats et leurs lignes. */
internal fun Dish.toDto() = DishDto(
    id = id.value,
    date = date.toString(),
    source = source.name,
    loggedAt = loggedAt.toString(),
    favoriteId = favoriteId?.value,
    title = title,
    moment = moment?.name,
)

internal fun DishDto.toDomain(entries: List<EntryDto>) = Dish(
    id = DishId(id),
    date = LocalDate.parse(date),
    source = EntrySource.entries.firstOrNull { it.name == source } ?: EntrySource.MANUAL,
    loggedAt = loggedAt.toInstantOrEpoch(),
    entries = entries.map { it.toDomain() },
    favoriteId = favoriteId?.let(::FavoriteDishId),
    title = title,
    // Un moment inconnu -- fichier plus recent, valeur abimee -- laisse l'heure du
    // plat decider, comme pour un plat d'avant les moments.
    moment = MealMoment.entries.firstOrNull { it.name == moment },
)

internal fun FoodEntry.toDto() = EntryDto(
    id = id.value,
    dishId = dishId.value,
    foodId = foodId?.value,
    displayName = displayName,
    quantity = quantity,
    unit = unit,
    grams = grams,
    kcal = macros.kcal,
    protein = macros.protein,
    carbs = macros.carbs,
    sugars = macros.sugars,
    fat = macros.fat,
    fiber = macros.fiber,
)

internal fun EntryDto.toDomain() = FoodEntry(
    id = EntryId(id),
    dishId = DishId(dishId),
    foodId = foodId?.let(::FoodId),
    displayName = displayName,
    quantity = quantity,
    unit = unit,
    grams = grams,
    macros = Macros(kcal = kcal, protein = protein, carbs = carbs, sugars = sugars, fat = fat, fiber = fiber),
)
