package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * **Une journée est comparée à l'objectif actif ce jour-là**, et non à celui d'aujourd'hui.
 *
 * C'est ce que [D04][decisions] achète, et c'est le critère de fin de la tranche 4.
 * Sans lui, le jour où l'on passe de 2 500 à 2 200 kcal, tout le mois écoulé se
 * repeindrait en dépassement — et l'historique deviendrait incompréhensible au moment
 * exact où l'on cherche à comprendre ce qui a marché.
 *
 * Le raccourci que ce test interdit tient en une ligne : lire l'objectif **courant**
 * plutôt que celui de la date. Il marche parfaitement tant qu'on n'a jamais changé
 * d'objectif, c'est-à-dire pendant tout le développement.
 *
 * [decisions]: docs/11-decisions.md
 */
class GoalOfTheDayTest {
    private val diary = InMemoryDiaryRepository()

    @Test
    fun `une journee passee garde l objectif de son epoque`() = runTest {
        val goals = InMemoryGoals(listOf(ancien, courant))

        val mai = summaryOn(MI_MAI, goals)
        val juillet = summaryOn(MI_JUILLET, goals)

        assertEquals(2_500.0, mai.goal?.kcal, "le 15 mai relevait de l ancien objectif")
        assertEquals(2_200.0, juillet.goal?.kcal, "le 15 juillet releve du nouveau")
    }

    @Test
    fun `le jour du changement appartient au nouvel objectif`() = runTest {
        // Borne de debut incluse, borne de fin exclue. Sans cette convention, le
        // 1er juin releverait des deux, et le resume dependrait de l'ordre de lecture.
        val summary = summaryOn(PREMIER_JUIN, InMemoryGoals(listOf(ancien, courant)))

        assertEquals(2_200.0, summary.goal?.kcal)
    }

    @Test
    fun `une journee anterieure au premier objectif n en a aucun`() = runTest {
        // Et non l'objectif courant applique retroactivement : une journee notee avant
        // qu'un objectif existe n'a rien a quoi se comparer. `null` est la reponse
        // exacte, et l'accueil affiche alors ses totaux sans jauge.
        val summary = summaryOn(LocalDate.of(2025, 12, 31), InMemoryGoals(listOf(courant)))

        assertNull(summary.goal)
    }

    @Test
    fun `sans aucun objectif, la journee reste lisible`() = runTest {
        // L'etat d'avant l'onboarding. Les totaux sont exacts ; c'est la comparaison
        // qui manque, pas la mesure.
        val summary = summaryOn(MI_JUILLET, InMemoryGoals())

        assertNull(summary.goal)
        assertEquals(MI_JUILLET, summary.date)
    }

    @Test
    fun `une journee anterieure au premier objectif le sait`() = runTest {
        // D161 : sans cette marque, l'accueil invitait a repondre aux cinq questions sur
        // chaque jour d'avant l'installation -- chez quelqu'un qui venait d'y repondre,
        // et qui relancait l'onboarding en touchant le bouton.
        val summary = summaryOn(LocalDate.of(2025, 12, 31), InMemoryGoals(listOf(courant)))

        assertTrue(summary.beforeFirstGoal)
    }

    @Test
    fun `sans aucun objectif, rien ne precede rien`() = runTest {
        // L'invitation reste juste ici : il n'y a vraiment pas d'objectif a comparer.
        assertFalse(summaryOn(MI_JUILLET, InMemoryGoals()).beforeFirstGoal)
    }

    @Test
    fun `une journee couverte par un objectif ne precede rien`() = runTest {
        assertFalse(summaryOn(MI_MAI, InMemoryGoals(listOf(ancien, courant))).beforeFirstGoal)
        assertFalse(summaryOn(MI_JUILLET, InMemoryGoals(listOf(ancien, courant))).beforeFirstGoal)
    }

    @Test
    fun `remplacer un objectif clot le precedent au lieu de l ecraser`() = runTest {
        // L'invariant du port : au plus un objectif actif, et l'ancien garde sa
        // periode. Un `update` en place aurait perdu ce que valait l'objectif de mai.
        val goals = InMemoryGoals(listOf(ancien.copy(endedAt = null)))

        goals.replace(courant)

        assertEquals(2, goals.all.size, "l ancien objectif a ete ecrase au lieu d etre clos")
        assertEquals(PREMIER_JUIN, goals.all.first { it.id == ancien.id }.endedAt)
        assertEquals(2_500.0, summaryOn(MI_MAI, goals).goal?.kcal)
    }

    private suspend fun summaryOn(date: LocalDate, goals: InMemoryGoals) =
        GetDaySummary(diary, goals, FixedClock.atNoon(date))(date).first()

    private val ancien = goal("g-ancien", DEBUT_ANNEE, PREMIER_JUIN, kcal = 2_500.0)
    private val courant = goal("g-courant", PREMIER_JUIN, null, kcal = 2_200.0)

    private fun goal(id: String, from: LocalDate, to: LocalDate?, kcal: Double) = Goal(
        id = GoalId(id),
        startedAt = from,
        endedAt = to,
        origin = GoalOrigin.CALCULATED,
        strategy = GoalStrategy.LOSE,
        daily = DailyGoal(kcal = kcal, protein = 144.0, carbs = 312.0, sugars = 63.0, fat = 70.0, fiber = 35.0),
    )

    private companion object {
        val DEBUT_ANNEE: LocalDate = LocalDate.of(2026, 1, 1)
        val MI_MAI: LocalDate = LocalDate.of(2026, 5, 15)
        val PREMIER_JUIN: LocalDate = LocalDate.of(2026, 6, 1)
        val MI_JUILLET: LocalDate = LocalDate.of(2026, 7, 15)
    }
}
