package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WorkActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le journal de poids : ce qu'on y écrit, et la courbe qu'on en lit.
 */
class WeightJournalTest {
    private val weights = InMemoryWeightLog()
    private val goals = InMemoryGoals()
    private val profiles = InMemoryProfiles()

    // --- Noter une pesee --------------------------------------------------------

    @Test
    fun `une pesee du jour est notee`() = runTest {
        assertTrue(recordWeight()(WeightEntry(AUJOURD_HUI, 82.0)))

        assertEquals(listOf(WeightEntry(AUJOURD_HUI, 82.0)), weights.entries)
    }

    @Test
    fun `une pesee datee de demain est refusee`() = runTest {
        // docs/02 interdit la saisie dans le futur. Une pesee a venir dechirerait en
        // plus la moyenne mobile, dont la fenetre finit toujours au jour dit.
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI.plusDays(1), 82.0)))

        assertEquals(emptyList<WeightEntry>(), weights.entries)
    }

    @Test
    fun `un poids nul ou negatif est refuse`() = runTest {
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI, 0.0)))
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI, -5.0)))

        assertEquals(emptyList<WeightEntry>(), weights.entries)
    }

    @Test
    fun `un poids qu aucun adulte ne pese est refuse`() = runTest {
        // D160 : 999 kg entraient dans la courbe, puis dans le calcul de l'objectif, qui
        // lit la derniere pesee.
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI, 999.0)))
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI, 10.0)))

        assertEquals(emptyList<WeightEntry>(), weights.entries)
    }

    @Test
    fun `une pesee se juge sur la taille du profil`() = runTest {
        // 300 kg passent sans taille connue ; a 175 cm, c'est un IMC de 98. La meme
        // regle que l'onboarding : une pesee que le profil refuserait ne s'ecrit pas.
        profiles.save(PROFIL_175_CM)

        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI, 300.0)))
        assertTrue(recordWeight()(WeightEntry(AUJOURD_HUI, 82.0)))
    }

    @Test
    fun `la taille se lit pour juger, et son absence ne bloque rien`() = runTest {
        assertNull(ObserveHeight(profiles)().first(), "sans profil, pas de taille")

        profiles.save(PROFIL_175_CM)

        assertEquals(175.0, ObserveHeight(profiles)().first())
    }

    @Test
    fun `une pesee ancienne est acceptee`() = runTest {
        // Rattraper une pesee oubliee est courant, et la moyenne mobile la veut.
        assertTrue(recordWeight()(WeightEntry(AUJOURD_HUI.minusDays(3), 82.0)))
    }

    // --- Retirer une pesee (D165) -----------------------------------------------

    @Test
    fun `retirer une pesee la sort du journal, et seulement elle`() = runTest {
        // Une pesee tapee sur le mauvais jour restait dans la courbe, dans la pente de
        // l'adaptation et dans le poids du calcul : il fallait tout effacer.
        peser(AUJOURD_HUI.minusDays(2), 82.0)
        peser(AUJOURD_HUI, 91.0)

        DeleteWeight(weights)(AUJOURD_HUI)

        assertEquals(listOf(WeightEntry(AUJOURD_HUI.minusDays(2), 82.0)), weights.entries)
        assertEquals(82.0, weights.latest?.weightKg, "le poids du calcul redevient celui d'avant")
    }

    @Test
    fun `annuler rend la pesee retiree`() = runTest {
        val retiree = WeightEntry(AUJOURD_HUI, 91.0)
        peser(retiree.date, retiree.weightKg)
        DeleteWeight(weights)(retiree.date)

        assertTrue(RestoreWeight(weights)(retiree))

        assertEquals(listOf(retiree), weights.entries)
    }

    @Test
    fun `annuler ne recouvre pas une pesee notee entre-temps`() = runTest {
        // La derniere pesee du jour gagne : celle qu'on vient de noter est plus recente
        // que celle qu'on annule.
        val retiree = WeightEntry(AUJOURD_HUI, 91.0)
        peser(retiree.date, retiree.weightKg)
        DeleteWeight(weights)(retiree.date)
        peser(AUJOURD_HUI, 80.5)

        assertFalse(RestoreWeight(weights)(retiree))

        assertEquals(listOf(WeightEntry(AUJOURD_HUI, 80.5)), weights.entries)
    }

    @Test
    fun `annuler ne juge pas la pesee une seconde fois`() = runTest {
        // Elle a ete acceptee quand on l'a notee. Une taille de profil changee depuis ne
        // doit pas faire echouer en silence le « Annuler » qu'on vient de toucher.
        val ancienne = WeightEntry(AUJOURD_HUI.minusDays(30), 140.0)

        assertTrue(RestoreWeight(weights)(ancienne))

        assertEquals(listOf(ancienne), weights.entries)
    }

    // --- La courbe --------------------------------------------------------------

    @Test
    fun `sans pesee, la courbe est vide`() = runTest {
        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(emptyList<TrendPoint>(), tendance.points)
        assertNull(tendance.latest)
    }

    @Test
    fun `chaque pesee donne un point, du plus ancien au plus recent`() = runTest {
        peser(AUJOURD_HUI, 80.0)
        peser(AUJOURD_HUI.minusDays(2), 82.0)

        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(listOf(AUJOURD_HUI.minusDays(2), AUJOURD_HUI), tendance.points.map { it.date })
        assertEquals(WeightEntry(AUJOURD_HUI, 80.0), tendance.latest)
    }

    /**
     * **Chaque pesée a sa tendance**, la plus isolée comprise (D159).
     *
     * C'était une moyenne sur sept jours, absente sous trois pesées : une pesée isolée
     * d'il y a un mois n'en avait pas, et le tracé lissé n'existait que par fragments.
     * La première vaut la pesée elle-même ; après un mois de silence, la tendance a
     * presque rejoint la nouvelle pesée — l'ancienne ne disait plus rien.
     */
    @Test
    fun `chaque pesee a sa tendance, meme apres un long silence`() = runTest {
        peser(AUJOURD_HUI.minusDays(30), 90.0)
        peser(AUJOURD_HUI, 80.0)

        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(90.0, tendance.points.first().trendKg, "la premiere pesee est sa propre tendance")
        assertEquals(80.0, tendance.points.last().trendKg, PROCHE_D_UN_DEMI_KILO)
    }

    @Test
    fun `la courbe porte la trajectoire de l objectif courant`() = runTest {
        peser(DEBUT, 90.0)
        goals.replace(objectif())

        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(DEBUT, tendance.aim?.from)
        assertEquals(90.0, tendance.aim?.fromKg)
        assertEquals(80.0, tendance.aim?.toKg)
    }

    @Test
    fun `sans objectif courant, la courbe n a pas de trajectoire`() = runTest {
        peser(DEBUT, 90.0)

        assertNull(GetWeightTrend(weights, goals)().first().aim)
    }

    @Test
    fun `la pente reelle se lit sur la courbe`() = runTest {
        // Trois pesees a 81 la semaine d'avant, trois a 80 celle-ci : un kilo par
        // semaine, et c'est ce chiffre que l'adaptation comparera au cap annonce.
        repeat(3) { peser(AUJOURD_HUI.minusDays(7L + it), 81.0) }
        repeat(3) { peser(AUJOURD_HUI.minusDays(it.toLong()), 80.0) }

        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(-1.0, tendance.weeklySlopeOn(AUJOURD_HUI)!!, TOLERANCE)
    }

    private suspend fun peser(date: LocalDate, kg: Double) = weights.record(WeightEntry(date, kg))

    private fun recordWeight() = RecordWeight(weights, profiles, FixedClock.atNoon(AUJOURD_HUI))

    private fun objectif() = Goal(
        id = GoalId("g"),
        startedAt = DEBUT,
        origin = GoalOrigin.CALCULATED,
        strategy = GoalStrategy.LOSE,
        targetWeightKg = 80.0,
        targetDate = DEBUT.plusDays(140),
        daily = DailyGoal(kcal = 2000.0, protein = 150.0, carbs = 200.0, sugars = 50.0, fat = 60.0, fiber = 30.0),
    )

    private companion object {
        /** Apres un mois de silence, la tendance est a moins d'un demi-kilo de la pesee. */
        const val PROCHE_D_UN_DEMI_KILO = 0.5

        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 22)
        val DEBUT: LocalDate = LocalDate.of(2026, 7, 1)
        const val TOLERANCE = 1e-9

        val PROFIL_175_CM = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 175.0,
            activity = Activity(WorkActivity.DESK),
        )
    }
}
