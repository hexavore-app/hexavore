package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProgressStore
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macros
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.Points
import app.hexavore.domain.progress.StoredProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * La progression, du journal à ce qui s'affiche — et ce qui en est figé.
 *
 * Ce qui s'éprouve ici est la chose la plus facile à casser de cette mécanique : **ce
 * qui se dérive et ce qui se range ne sont pas la même chose**, et la lecture doit
 * garder le plus grand des deux. Un plancher qui redescendrait ne se verrait qu'un
 * jour, chez quelqu'un qui vient de corriger un vieux plat et qui a perdu un niveau.
 */
internal class ProgressTest {
    private val clock = FixedClock.atNoon(TODAY)
    private val store = InMemoryProgressStore()

    // --- Ce qui se derive --------------------------------------------------------

    @Test
    fun `chaque plat rapporte des points le jour meme`() = runTest {
        // La recompense est immediate : c'est ce qui la rend une recompense.
        val progress = observe(dishesOn(TODAY, count = 2))

        assertEquals(2L * Points.PER_DISH, progress.points)
    }

    @Test
    fun `une journee close ajoute ce qu'une journee en cours n'ajoute pas`() = runTest {
        // Payer d'avance ferait monter puis redescendre le total si le dernier repas
        // n'etait finalement pas note.
        val progress = observe(dishesOn(TODAY.minusDays(1), count = 1))

        assertEquals(Points.PER_DISH + Points.PER_LOGGED_DAY.toLong(), progress.points)
    }

    @Test
    fun `une journee close et parfaite rapporte les deux`() = runTest {
        val progress = observe(dishesOn(TODAY.minusDays(1), count = 1, kcal = 2000.0, protein = 120.0))

        assertEquals(
            Points.PER_DISH + Points.PER_LOGGED_DAY + Points.PER_PERFECT_DAY.toLong(),
            progress.points,
        )
        assertEquals(1, progress.streaks.perfect)
    }

    @Test
    fun `les modes essayes se lisent dans les plats eux-memes`() = runTest {
        // L'origine d'un plat est un fait qui ne se reecrit jamais (D32) : rien a
        // ranger pour savoir qu'on a deja scanne.
        val progress = observe(dishesOn(TODAY, count = 1, source = EntrySource.BARCODE))

        assertTrue(Badge.FIRST_SCAN in progress.tally.discovered)
        assertFalse(Badge.FIRST_PHOTO in progress.tally.discovered)
    }

    // --- Ce qui se range ---------------------------------------------------------

    @Test
    fun `un plancher range l'emporte sur un journal qui a maigri`() = runTest {
        // Corriger une erreur de l'an dernier ne doit pas couter un niveau, sans quoi
        // la correction serait punie.
        val store = InMemoryProgressStore(StoredProgress(points = 5_000, bestStreak = 120))
        val progress = ObserveProgress(
            InMemoryDiaryRepository(dishesOn(TODAY, count = 1)),
            InMemoryGoals(listOf(GOAL)),
            store,
            clock,
        )().first()

        assertEquals(5_000L, progress.points)
        assertEquals(120, progress.tally.bestStreak)
    }

    @Test
    fun `avancer fige les paliers atteints, et les rend une seule fois`() = runTest {
        val diary = InMemoryDiaryRepository(dishesOn(TODAY, count = 10))
        val observe = ObserveProgress(diary, InMemoryGoals(listOf(GOAL)), store, clock)
        val advance = AdvanceProgress(store, clock)

        val fallen = advance(observe().first())
        val again = advance(observe().first())

        assertTrue(Badge.DISHES_10 in fallen)
        assertTrue(again.isEmpty())
        assertEquals(TODAY, store.stored.unlocked[Badge.DISHES_10])
    }

    @Test
    fun `un releve qui n'apporte rien n'ecrit rien`() = runTest {
        // C'est ce qui ferme la boucle : ecrire reemet le depot, qui relance le calcul,
        // qui ferait avancer de nouveau (D132).
        val observe = ObserveProgress(
            InMemoryDiaryRepository(dishesOn(TODAY, count = 1)),
            InMemoryGoals(listOf(GOAL)),
            store,
            clock,
        )
        val advance = AdvanceProgress(store, clock)
        advance(observe().first())
        val writes = store.writes

        advance(observe().first())

        assertEquals(writes, store.writes)
    }

    @Test
    fun `un palier deja pris garde sa date`() = runTest {
        // Sans quoi l'ecran annoncerait que la serie de cent jours a ete obtenue ce
        // matin.
        val advance = AdvanceProgress(store, clock)
        val observe = ObserveProgress(
            InMemoryDiaryRepository(dishesOn(TODAY, count = 10)),
            InMemoryGoals(listOf(GOAL)),
            store,
            clock,
        )
        advance(observe().first())

        clock.instant = TODAY.plusDays(3).atTime(12, 0).atZone(ZoneOffset.UTC).toInstant()
        AdvanceProgress(store, clock)(observe().first())

        assertEquals(TODAY, store.stored.unlocked[Badge.DISHES_10])
    }

    private suspend fun observe(dishes: List<Dish>) = ObserveProgress(
        InMemoryDiaryRepository(dishes),
        InMemoryGoals(listOf(GOAL)),
        store,
        clock,
    )().first()

    private fun dishesOn(
        date: LocalDate,
        count: Int,
        kcal: Double = 10.0,
        protein: Double = 1.0,
        source: EntrySource = EntrySource.MANUAL,
    ): List<Dish> = List(count) { index ->
        Dish(
            id = DishId("plat-$date-$index"),
            date = date,
            source = source,
            loggedAt = date.atTime(12, index % 60).toInstant(ZoneOffset.UTC),
            entries = listOf(
                FoodEntry(
                    id = EntryId("ligne-$date-$index"),
                    dishId = DishId("plat-$date-$index"),
                    displayName = "Aliment",
                    quantity = 100.0,
                    unit = "g",
                    grams = 100.0,
                    // Les macros de la journee sont portees par le premier plat : les
                    // suivants n'ajoutent rien, pour que le compte de plats et le
                    // verdict de justesse se reglent separement.
                    macros = if (index == 0) {
                        Macros(kcal, protein, 200.0, 40.0, 60.0, 30.0)
                    } else {
                        Macros(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                    },
                ),
            ),
        )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 29)

        val GOAL = Goal(
            id = GoalId("objectif"),
            startedAt = TODAY.minusYears(1),
            origin = GoalOrigin.CALCULATED,
            strategy = GoalStrategy.LOSE,
            daily = DailyGoal(kcal = 2000.0, protein = 120.0, carbs = 220.0, sugars = 50.0, fat = 67.0, fiber = 30.0),
        )
    }
}
