package app.hexavore.domain.profile

import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le lissage, la pente, et la trajectoire annoncée.
 *
 * **Trois pesées, sinon rien.** C'est le plancher que [docs/03][calculs] pose pour la
 * pente, et il vaut aussi pour la courbe : une moyenne sur sept jours calculée depuis
 * une seule mesure *est* cette mesure, et la tracer en évidence à côté des points
 * bruts affirmerait un lissage qui n'a pas eu lieu.
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
class WeightTrendTest {
    // --- La tendance (D159) -------------------------------------------------------

    @Test
    fun `la premiere pesee est sa propre tendance`() {
        assertEquals(80.0, listOf(pesee(LUNDI, 80.0)).trendByDate().getValue(LUNDI))
    }

    /**
     * Un jour fait parcourir un dixième du chemin : c'est ce qui efface l'eau et le sel
     * d'un jour à l'autre chez qui se pèse tous les matins.
     */
    @Test
    fun `un jour fait parcourir un dixieme du chemin`() {
        val journal = listOf(pesee(LUNDI.minusDays(1), 80.0), pesee(LUNDI, 90.0))

        assertEquals(81.0, journal.trendByDate().getValue(LUNDI), PRECISION)
    }

    /**
     * Après trois semaines de silence, la tendance a presque rejoint la nouvelle pesée.
     *
     * C'est la propriété qui manquait : l'ancienne tendance ne dit plus rien du poids
     * d'aujourd'hui, et la garder ferait traîner la courbe derrière la réalité.
     */
    @Test
    fun `apres un long silence la tendance rejoint la nouvelle pesee`() {
        val journal = listOf(pesee(LUNDI.minusDays(21), 90.0), pesee(LUNDI, 80.0))

        // 1 - 0,9^21 : un peu plus de 89 % du chemin.
        assertEquals(81.09, journal.trendByDate().getValue(LUNDI), PRECISION)
    }

    @Test
    fun `l ordre du journal ne change rien`() {
        val dans = listOf(pesee(LUNDI.minusDays(1), 80.0), pesee(LUNDI, 90.0))

        assertEquals(dans.trendByDate(), dans.reversed().trendByDate())
    }

    // --- La moyenne mobile ------------------------------------------------------

    @Test
    fun `la moyenne porte sur les sept jours qui finissent au jour dit`() {
        // Sept jours : LUNDI-6 .. LUNDI. La pesee de LUNDI-7 est dehors, et c'est elle
        // qui fait le test -- avec une fenetre de huit jours, la moyenne descendrait.
        val journal = listOf(
            pesee(LUNDI.minusDays(7), 100.0),
            pesee(LUNDI.minusDays(6), 80.0),
            pesee(LUNDI.minusDays(1), 80.0),
            pesee(LUNDI, 80.0),
        )

        assertEquals(80.0, journal.movingAverageOn(LUNDI))
    }

    @Test
    fun `la moyenne ignore ce qui vient apres le jour dit`() {
        // Une fenetre qui deborderait sur la suite ferait dependre le passe de
        // l'avenir : le point d'hier changerait encore demain.
        val journal = listOf(
            pesee(LUNDI.minusDays(2), 80.0),
            pesee(LUNDI.minusDays(1), 80.0),
            pesee(LUNDI, 80.0),
            pesee(LUNDI.plusDays(1), 40.0),
        )

        assertEquals(80.0, journal.movingAverageOn(LUNDI))
    }

    @Test
    fun `deux pesees dans la fenetre ne font pas de moyenne`() {
        val journal = listOf(pesee(LUNDI.minusDays(1), 80.0), pesee(LUNDI, 82.0))

        assertNull(journal.movingAverageOn(LUNDI), "sous trois pesees, il n'y a rien a lisser")
    }

    @Test
    fun `trois pesees suffisent`() {
        val journal = listOf(pesee(LUNDI.minusDays(2), 81.0), pesee(LUNDI.minusDays(1), 80.0), pesee(LUNDI, 79.0))

        assertEquals(80.0, journal.movingAverageOn(LUNDI))
    }

    // --- La pente ---------------------------------------------------------------

    @Test
    fun `la pente compare deux moyennes espacees de sept jours`() {
        // Une semaine a 81, la suivante a 80 : un kilo perdu par semaine.
        val journal = semaineA(LUNDI.minusDays(7), 81.0) + semaineA(LUNDI, 80.0)

        assertEquals(-1.0, journal.weeklySlopeOn(LUNDI)!!, TOLERANCE)
    }

    @Test
    fun `sans moyenne il y a sept jours, il n y a pas de pente`() {
        // La fenetre d'avant est trop pauvre. Une pente contre une moyenne qu'on ne
        // connait pas serait une pente contre rien -- docs/03 prefere le silence.
        val journal = listOf(pesee(LUNDI.minusDays(7), 81.0)) + semaineA(LUNDI, 80.0)

        assertNull(journal.weeklySlopeOn(LUNDI))
    }

    @Test
    fun `sans moyenne aujourd hui, il n y a pas de pente`() {
        val journal = semaineA(LUNDI.minusDays(7), 81.0) + listOf(pesee(LUNDI, 80.0))

        assertNull(journal.weeklySlopeOn(LUNDI))
    }

    // --- La trajectoire annoncee ------------------------------------------------

    @Test
    fun `la trajectoire part du poids connu au debut de l objectif`() {
        // Et non du poids d'aujourd'hui : le cap part d'ou l'on etait quand on l'a fixe.
        val journal = listOf(pesee(LUNDI.minusDays(30), 95.0), pesee(LUNDI, 90.0), pesee(LUNDI.plusDays(20), 88.0))

        val trajectoire = objectif(cible = 80.0, echeance = LUNDI.plusDays(140)).declaredAim(journal)

        assertEquals(90.0, trajectoire?.fromKg)
        assertEquals(LUNDI, trajectoire?.from)
    }

    @Test
    fun `la pente annoncee est un rythme hebdomadaire`() {
        // Dix kilos en cent-quarante jours, soit vingt semaines : un demi-kilo par
        // semaine, en negatif parce que c'est une perte.
        val journal = listOf(pesee(LUNDI, 90.0))

        val trajectoire = objectif(cible = 80.0, echeance = LUNDI.plusDays(140)).declaredAim(journal)

        assertEquals(-0.5, trajectoire!!.weeklySlope, TOLERANCE)
    }

    @Test
    fun `sans pesee anterieure a l objectif, il n y a pas de trajectoire`() {
        // Une droite tracee depuis un point qu'on ne connait pas n'est pas un repere.
        val journal = listOf(pesee(LUNDI.plusDays(1), 90.0))

        assertNull(objectif(cible = 80.0, echeance = LUNDI.plusDays(140)).declaredAim(journal))
    }

    @Test
    fun `sans poids cible ni echeance, il n y a pas de trajectoire`() {
        val journal = listOf(pesee(LUNDI, 90.0))

        assertNull(objectif(cible = null, echeance = LUNDI.plusDays(140)).declaredAim(journal))
        assertNull(objectif(cible = 80.0, echeance = null).declaredAim(journal))
    }

    @Test
    fun `une echeance qui ne suit pas le debut ne trace rien`() {
        // Sans quoi la pente diviserait par zero, ou remonterait le temps.
        val journal = listOf(pesee(LUNDI, 90.0))

        assertNull(objectif(cible = 80.0, echeance = LUNDI).declaredAim(journal))
    }

    private fun pesee(date: LocalDate, kg: Double) = WeightEntry(date, kg)

    /** Trois pesées au même poids, dans la fenêtre qui finit à [fin]. */
    private fun semaineA(fin: LocalDate, kg: Double) =
        listOf(pesee(fin.minusDays(2), kg), pesee(fin.minusDays(1), kg), pesee(fin, kg))

    private fun objectif(cible: Double?, echeance: LocalDate?) = Goal(
        id = GoalId("g"),
        startedAt = LUNDI,
        origin = GoalOrigin.CALCULATED,
        strategy = GoalStrategy.LOSE,
        targetWeightKg = cible,
        targetDate = echeance,
        daily = DailyGoal(kcal = 2000.0, protein = 150.0, carbs = 200.0, sugars = 50.0, fat = 60.0, fiber = 30.0),
    )

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 17)
        const val TOLERANCE = 1e-9

        /** Les tendances se comparent au centieme de kilo : au-dela, c'est du bruit. */
        const val PRECISION = 0.01
    }
}
