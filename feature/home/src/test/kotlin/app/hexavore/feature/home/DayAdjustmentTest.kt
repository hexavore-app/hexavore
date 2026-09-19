package app.hexavore.feature.home

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAdjustmentSettings
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.usecase.AdjustmentResponse
import app.hexavore.domain.usecase.RespondToAdjustment
import app.hexavore.domain.usecase.SuggestGoalAdjustment
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * La carte d'adaptation : ce qu'elle propose, et ce qu'on lui répond.
 *
 * **Ce que ce cas défend n'est pas une règle du domaine** — elle est éprouvée là-bas —
 * mais le **branchement**. Le regroupement de deux cas d'usage a suffi à ce qu'une
 * propriété prenne le nom de la méthode qui l'appelle : la méthode s'appelait alors
 * elle-même, le compilateur l'acceptait sans un mot, et répondre à la carte aurait fait
 * sauter la pile sur l'appareil. C'est [D111][decisions] une fois de plus — une règle
 * se vérifie **aux portes**.
 *
 * [decisions]: docs/11-decisions.md
 */
class DayAdjustmentTest {
    @Test
    fun `arreter l adaptation atteint le reglage`() = runTest {
        adjustment().respond(AdjustmentResponse.STOP, SUGGESTION)

        assertFalse(settings.setup.enabled)
    }

    @Test
    fun `ignorer une proposition date le refus`() = runTest {
        adjustment().respond(AdjustmentResponse.IGNORE, SUGGESTION)

        assertEquals(JOUR, settings.setup.lastIgnoredOn)
    }

    // --- Décor ------------------------------------------------------------------

    private val settings = InMemoryAdjustmentSettings()
    private val clock = FixedClock.atNoon(JOUR)

    private fun adjustment() = DayAdjustment(
        suggest = SuggestGoalAdjustment(
            weights = InMemoryWeightLog(),
            diary = InMemoryDiaryRepository(),
            goals = InMemoryGoals(),
            profiles = InMemoryProfiles(),
            settings = settings,
            clock = clock,
        ),
        respondTo = RespondToAdjustment(
            goals = InMemoryGoals(),
            settings = settings,
            ids = SequentialIdGenerator("objectif"),
            clock = clock,
        ),
    )

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 3, 15)

        /** Les chiffres n'entrent pas en jeu : arrêter et ignorer ne les lisent pas. */
        val SUGGESTION = AdjustmentSuggestion(
            actualWeeklyKg = -0.2,
            aimedWeeklyKg = -0.5,
            current = InMemoryGoals.maintenance(JOUR).daily,
            proposed = InMemoryGoals.maintenance(JOUR).daily,
        )
    }
}
