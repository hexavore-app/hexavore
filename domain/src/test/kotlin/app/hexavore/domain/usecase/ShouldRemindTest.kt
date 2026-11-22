package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryReminderSettings
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSetup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Un rappel ne sonne que s'il a quelque chose à dire.
 *
 * Un rappel qui sonne pour rien finit coupé au niveau du système, et le canal entier
 * se perd avec lui : chaque silence ci-dessous protège les rappels qui servent.
 */
class ShouldRemindTest {
    private val diary = InMemoryDiaryRepository()
    private val settings = InMemoryReminderSettings()
    private val shouldRemind = ShouldRemind(diary, settings, FixedClock.atNoon(TODAY))

    @Test
    fun `un rappel eteint se tait`() = runTest {
        settings.setEnabled(Reminder.BREAKFAST, enabled = false)

        assertFalse(shouldRemind(Reminder.BREAKFAST))
    }

    @Test
    fun `un repas rien note sonne`() = runTest {
        assertTrue(shouldRemind(Reminder.BREAKFAST))
    }

    @Test
    fun `un repas deja note se tait, et lui seul`() = runTest {
        diary.setContent(listOf(dish(TODAY, at = LocalTime.of(8, 0))))

        assertFalse(shouldRemind(Reminder.BREAKFAST))
        assertTrue(shouldRemind(Reminder.LUNCH), "le petit-dejeuner ne couvre pas le dejeuner")
    }

    @Test
    fun `le moment ecrit sur le plat l'emporte sur son heure`() = runTest {
        // Note a 13 h, mais range au petit-dejeuner par l'utilisateur : c'est ce que
        // l'accueil affiche, donc c'est ce qui fait taire le rappel.
        diary.setContent(listOf(dish(TODAY, at = LocalTime.of(13, 0), moment = MealMoment.BREAKFAST)))

        assertFalse(shouldRemind(Reminder.BREAKFAST))
        assertTrue(shouldRemind(Reminder.LUNCH))
    }

    @Test
    fun `l'heure d'un plat se lit dans le fuseau de l'horloge`() = runTest {
        // 4 h 30 UTC, soit 5 h 30 a Paris en mars : un petit-dejeuner. Lue en UTC, elle
        // serait encore la nuit, donc un diner.
        val loggedAt = Instant.parse("2026-03-15T04:30:00Z")
        diary.setContent(listOf(dish(TODAY, loggedAt = loggedAt)))

        assertFalse(shouldRemind(Reminder.BREAKFAST))
        assertTrue(shouldRemind(Reminder.DINNER))
    }

    @Test
    fun `un plat d'un autre jour ne fait taire personne`() = runTest {
        diary.setContent(listOf(dish(TODAY.minusDays(1), at = LocalTime.of(8, 0))))

        assertTrue(shouldRemind(Reminder.BREAKFAST))
    }

    @Test
    fun `la serie sonne quand elle court et que la journee est vide`() = runTest {
        diary.setContent(listOf(dish(TODAY.minusDays(2)), dish(TODAY.minusDays(1))))

        assertTrue(shouldRemind(Reminder.STREAK))
    }

    @Test
    fun `la serie se tait quand la journee est notee`() = runTest {
        diary.setContent(listOf(dish(TODAY.minusDays(1)), dish(TODAY)))

        assertFalse(shouldRemind(Reminder.STREAK))
    }

    @Test
    fun `la serie se tait quand rien n'a commence`() = runTest {
        assertFalse(shouldRemind(Reminder.STREAK))
    }

    @Test
    fun `la serie se tait quand hier l'a deja cassee`() = runTest {
        // Rien hier : la serie est perdue. Le rappel dirait qu'un plat suffit a la
        // garder, et ce serait faux.
        diary.setContent(listOf(dish(TODAY.minusDays(3)), dish(TODAY.minusDays(2))))

        assertFalse(shouldRemind(Reminder.STREAK))
    }

    @Test
    fun `la serie eteinte se tait meme quand elle court`() = runTest {
        diary.setContent(listOf(dish(TODAY.minusDays(1))))
        settings.setEnabled(Reminder.STREAK, enabled = false)

        assertFalse(shouldRemind(Reminder.STREAK))
    }

    @Test
    fun `seul le reglage du rappel compte, pas celui des autres`() = runTest {
        val seul = ShouldRemind(
            diary,
            InMemoryReminderSettings(ReminderSetup(enabled = setOf(Reminder.DINNER))),
            FixedClock.atNoon(TODAY),
        )

        assertTrue(seul(Reminder.DINNER))
        assertFalse(seul(Reminder.LUNCH))
    }

    private fun dish(
        date: LocalDate,
        at: LocalTime = LocalTime.of(12, 0),
        loggedAt: Instant = FixedClock.atNoon(date).instant.atZone(PARIS).with(at).toInstant(),
        moment: MealMoment? = null,
    ): Dish = Dish(
        id = DishId("$date-$at-$loggedAt"),
        date = date,
        source = EntrySource.MANUAL,
        loggedAt = loggedAt,
        entries = emptyList(),
        moment = moment,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 15)
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
