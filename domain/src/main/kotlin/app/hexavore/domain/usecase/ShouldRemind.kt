package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.momentIn
import app.hexavore.domain.progress.loggingStreak
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSettings
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Ce rappel a-t-il encore quelque chose à dire ?
 *
 * **La question se pose au moment de sonner, pas au moment de planifier.** Un rappel
 * placé la veille à minuit ne peut pas savoir ce qui sera noté à midi ; c'est en
 * arrivant qu'il regarde, et qu'il se tait le plus souvent.
 *
 * C'est la contrepartie qui rend ces notifications tenables ([D134][decisions]). Un
 * rappel qui sonne tous les jours à la même heure quoi qu'on fasse devient prévisible,
 * donc ignoré, puis coupé au niveau du système — et l'on perd alors le canal entier, y
 * compris celui qui aurait servi.
 *
 * **Trois raisons de se taire**, et elles se lisent dans l'ordre du coût :
 *
 * 1. Le rappel est éteint dans les réglages.
 * 2. Le repas qu'il couvre est **déjà noté** — c'est [MealMoment] qui le dit, celui-là
 *    même qui nomme les plats de l'accueil ([D118][decisions]), donc rien de neuf à
 *    décider et rien qui puisse diverger de ce qui est affiché.
 * 3. Pour la série : la journée est déjà notée — il n'y a plus rien en danger — ou
 *    aucune série ne court, auquel cas il réclamerait une saisie à quelqu'un qui n'a
 *    rien commencé.
 *
 * [decisions]: docs/11-decisions.md
 */
class ShouldRemind(
    private val diary: DiaryRepository,
    private val settings: ReminderSettings,
    private val clock: Clock,
) {
    suspend operator fun invoke(reminder: Reminder): Boolean {
        if (reminder !in settings.current()) return false

        val today = clock.today()
        val dishes = diary.observeDay(today).first()

        return when (val moment = reminder.moment) {
            // `momentIn` est celui que l'accueil affiche : le moment ecrit sur le plat
            // s'il y en a un, son heure sinon. Refaire la regle ici aurait donne un
            // rappel qui se tait pour un plat que l'ecran nomme autrement.
            null -> dishes.isEmpty() && hasStreak(today)
            else -> dishes.none { it.momentIn(clock.zone()) == moment }
        }
    }

    /**
     * Une série court-elle ?
     *
     * **Lue sur les jours clos**, et non sur aujourd'hui : c'est justement parce
     * qu'aujourd'hui est vide que ce rappel existe. Une fenêtre courte suffit — il
     * s'agit de savoir s'il y a quelque chose à perdre, pas de compter jusqu'où.
     */
    private suspend fun hasStreak(today: LocalDate): Boolean {
        val from = today.minusDays(STREAK_LOOKBACK)
        val logged = diary.observeRange(from, today.minusDays(1)).first().map { it.date }.toSet()
        return loggingStreak(logged, today = today.minusDays(1)) > 0
    }

    private companion object {
        /** Assez pour savoir qu'une série court. Le compter exactement ne sert à rien ici. */
        const val STREAK_LOOKBACK = 7L
    }
}
