package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAdjustmentSettings
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog
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
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WorkActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * L'adaptation hebdomadaire, et **le silence comme cas normal**.
 *
 * Trois conditions doivent être réunies, et [docs/12][plan] en fait un critère de fin
 * de tranche : *« les conditions de déclenchement — adhérence, persistance, nombre de
 * pesées — sont testées »*. Chacune a donc son cas, et chacun le décor complet d'une
 * suggestion qui, sans elle, paraîtrait.
 *
 * Le décor : un objectif qui annonce **un demi-kilo par semaine** (dix kilos en vingt
 * semaines), et un journal où l'on n'en perd qu'**un cinquième** — soit 0,3 kg/semaine
 * d'écart, bien au-delà des 0,15 qui comptent.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
class SuggestGoalAdjustmentTest {
    private val weights = InMemoryWeightLog()
    private val diary = InMemoryDiaryRepository()
    private val goals = InMemoryGoals()
    private val profiles = InMemoryProfiles()
    private val settings = InMemoryAdjustmentSettings()

    // --- Le cas ou la carte parait ----------------------------------------------

    @Test
    fun `un ecart persistant sur un journal tenu donne une suggestion`() = runTest {
        decor()

        val suggestion = suggest().first()

        assertNotNull(suggestion, "l'ecart est de 0,3 kg par semaine, deux semaines de suite")
        assertEquals(-0.2, suggestion!!.actualWeeklyKg, TOLERANCE)
        assertEquals(-0.5, suggestion.aimedWeeklyKg, TOLERANCE)
    }

    @Test
    fun `perdre moins que prevu fait baisser l objectif`() = runTest {
        decor()

        val suggestion = suggest().first()!!

        assertTrue(suggestion.deltaKcal < 0, "on mange trop pour le rythme vise, or delta = ${suggestion.deltaKcal}")
        assertEquals(suggestion.current.kcal + suggestion.deltaKcal, suggestion.proposed.kcal, TOLERANCE)
    }

    @Test
    fun `la correction est bornee a cent cinquante kilocalories`() = runTest {
        // 0,3 kg/semaine demanderait 330 kcal. La borne existe pour que le systeme ne
        // surcorrige pas : l'objectif oscillerait et on cesserait d'y croire.
        decor()

        assertEquals(-150, suggest().first()!!.deltaKcal)
    }

    @Test
    fun `un garde-fou a le dernier mot sur la correction`() = runTest {
        // A 2 200 kcal, retirer 150 ferait passer l'objectif sous les 25 % d'ecart au
        // TDEE que docs/03 autorise. La correction est alors reduite -- et c'est ce
        // chiffre-la que la carte doit annoncer, puisque c'est celui qui sera ecrit.
        decor(kcal = SOUS_LE_GARDE_FOU)

        val delta = suggest().first()!!.deltaKcal

        assertTrue(delta < 0, "on mange toujours trop pour le rythme vise, or delta = $delta")
        assertTrue(delta > -MAX_CORRECTION, "le garde-fou a mordu, or la correction vaut $delta")
    }

    @Test
    fun `une correction entierement absorbee ne se propose pas`() = runTest {
        // L'objectif est deja pose exactement sur la borne des 25 % : la correction
        // demandee y est integralement rabotee, et proposer un changement de zero
        // kilocalorie demanderait un geste pour ne rien faire.
        decor(kcal = SUR_LE_GARDE_FOU)

        assertNull(suggest().first())
    }

    // --- Adherence ---------------------------------------------------------------

    @Test
    fun `un journal troue ne declenche rien`() = runTest {
        // Neuf jours notes sur quatorze : 64 %. On ne saurait pas si l'ecart vient du
        // metabolisme ou de la saisie, et corriger le premier pour un defaut du second
        // eloigne du but.
        decor(joursNotes = 9)

        assertNull(suggest().first())
    }

    @Test
    fun `dix jours notes sur quatorze suffisent`() = runTest {
        decor(joursNotes = 10)

        assertNotNull(suggest().first(), "70 % exactement : le seuil appartient au verdict du haut")
    }

    // --- Persistance --------------------------------------------------------------

    @Test
    fun `un ecart d une seule semaine ne declenche rien`() = runTest {
        // La semaine d'avant est pile sur la trajectoire. Une seule semaine ne prouve
        // rien : la moyenne mobile attenue le bruit, elle ne l'efface pas.
        decor(perteHebdoKg = listOf(0.5, 0.5, 0.2))

        assertNull(suggest().first())
    }

    @Test
    fun `deux ecarts de sens opposes ne sont pas une persistance`() = runTest {
        // Une semaine trop lente, la suivante trop rapide : c'est une oscillation, et
        // la corriger l'amplifierait.
        decor(perteHebdoKg = listOf(0.5, 0.9, 0.2))

        assertNull(suggest().first())
    }

    @Test
    fun `un ecart sous le seuil ne declenche rien`() = runTest {
        // 0,1 kg/semaine d'ecart : du bruit, meme lisse sur sept jours.
        decor(perteHebdoKg = listOf(0.4, 0.4, 0.4))

        assertNull(suggest().first())
    }

    // --- Nombre de pesees ----------------------------------------------------------

    @Test
    fun `deux pesees par semaine ne suffisent pas a une pente`() = runTest {
        decor(peseesParSemaine = 2)

        assertNull(suggest().first(), "sous trois pesees par fenetre, il n'y a pas de pente")
    }

    @Test
    fun `trois pesees par semaine suffisent`() = runTest {
        decor(peseesParSemaine = 3)

        assertNotNull(suggest().first())
    }

    // --- Le silence apres une reponse ----------------------------------------------

    @Test
    fun `ne plus proposer fait taire l adaptation`() = runTest {
        decor()
        settings.stop()

        assertNull(suggest().first())
    }

    @Test
    fun `treize jours apres un refus, la carte se tait encore`() = runTest {
        decor()
        settings.ignored(AUJOURD_HUI.minusDays(13))

        assertNull(suggest().first())
    }

    @Test
    fun `quatorze jours apres un refus, la carte revient`() = runTest {
        decor()
        settings.ignored(AUJOURD_HUI.minusDays(14))

        assertNotNull(suggest().first())
    }

    @Test
    fun `treize jours apres un ajustement accepte, la carte se tait`() = runTest {
        // Il faut deux semaines pour que la moyenne mobile reflete le nouvel objectif :
        // corriger avant reviendrait a corriger une correction qu'on n'a pas mesuree.
        decor()
        settings.accepted(AUJOURD_HUI.minusDays(13))

        assertNull(suggest().first())
    }

    // --- Ce qui manque -------------------------------------------------------------

    @Test
    fun `sans objectif courant, il n y a rien a corriger`() = runTest {
        decor(objectif = false)

        assertNull(suggest().first())
    }

    @Test
    fun `un objectif sans poids cible n annonce aucune trajectoire`() = runTest {
        decor(cible = null)

        assertNull(suggest().first(), "sans cap annonce, il n'y a rien a quoi comparer")
    }

    @Test
    fun `sans profil, la correction ne peut pas repasser les garde-fous`() = runTest {
        decor(profil = false)

        assertNull(suggest().first())
    }

    // --- Le decor ------------------------------------------------------------------

    private fun suggest() =
        SuggestGoalAdjustment(weights, diary, goals, profiles, settings, FixedClock.atNoon(AUJOURD_HUI))()

    /**
     * Un décor complet, dont chaque test retire une pièce.
     *
     * @param perteHebdoKg le poids perdu chaque semaine, de la plus ancienne des trois
     *   fenêtres à celle qui finit aujourd'hui. Trois fenêtres, parce que la
     *   persistance compare deux pentes, et qu'une pente compare deux moyennes.
     */
    private suspend fun decor(
        perteHebdoKg: List<Double> = listOf(0.2, 0.2, 0.2),
        peseesParSemaine: Int = 3,
        joursNotes: Int = ADHERENCE_TOTALE,
        objectif: Boolean = true,
        cible: Double? = POIDS_CIBLE,
        profil: Boolean = true,
        kcal: Double = LOIN_DU_GARDE_FOU,
    ) {
        if (profil) profiles.save(PROFIL)
        if (objectif) goals.replace(objectifDe(cible, kcal))
        // La trajectoire part du poids connu au debut de l'objectif : sans cette
        // pesee-la, l'objectif n'annonce aucun cap et rien ne se declenche.
        weights.record(WeightEntry(DEBUT, POIDS_DEPART))
        peser(perteHebdoKg, peseesParSemaine)
        repeat(joursNotes) { noter(AUJOURD_HUI.minusDays(it.toLong())) }
    }

    /**
     * Le journal de pesées, construit à rebours depuis aujourd'hui.
     *
     * Trois fenêtres de sept jours : `[J-20, J-14]`, `[J-13, J-7]`, `[J-6, J]`. La
     * dernière donne la pente d'aujourd'hui, l'avant-dernière celle d'il y a une
     * semaine — et il faut les deux pour que la persistance ait un sens.
     */
    private suspend fun peser(perteHebdoKg: List<Double>, parSemaine: Int) {
        var poids = POIDS_DEPART
        perteHebdoKg.forEachIndexed { semaine, perte ->
            poids -= perte
            val fin = AUJOURD_HUI.minusDays(((perteHebdoKg.size - 1 - semaine) * SEMAINE).toLong())
            repeat(parSemaine) { weights.record(WeightEntry(fin.minusDays(it.toLong()), poids)) }
        }
    }

    private suspend fun noter(date: LocalDate) {
        diary.save(
            Dish(
                id = DishId("plat-$date"),
                date = date,
                loggedAt = Instant.parse("2026-08-22T12:00:00Z"),
                source = EntrySource.MANUAL,
                entries = listOf(
                    FoodEntry(
                        id = EntryId("ligne-$date"),
                        dishId = DishId("plat-$date"),
                        displayName = "Riz",
                        quantity = 100.0,
                        unit = "g",
                        grams = 100.0,
                        macros = Macros(
                            kcal = 130.0,
                            protein = 3.0,
                            carbs = 28.0,
                            sugars = 0.1,
                            fat = 0.3,
                            fiber = 0.4,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun objectifDe(cible: Double?, kcal: Double) = Goal(
        id = GoalId("g"),
        startedAt = DEBUT,
        origin = GoalOrigin.CALCULATED,
        strategy = GoalStrategy.LOSE,
        targetWeightKg = cible,
        // Dix kilos en cent-quarante jours, soit un demi-kilo par semaine.
        targetDate = DEBUT.plusDays(140),
        daily = DailyGoal(kcal = kcal, protein = 150.0, carbs = 255.0, sugars = 60.0, fat = 67.0, fiber = 30.0),
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 22)

        /** Avant les trois fenêtres de pesées, pour que le cap parte d'un poids connu. */
        val DEBUT: LocalDate = AUJOURD_HUI.minusDays(30)

        const val SEMAINE = 7
        const val POIDS_DEPART = 90.0
        const val POIDS_CIBLE = 80.0
        const val ADHERENCE_TOTALE = 14
        const val TOLERANCE = 1e-9
        const val MAX_CORRECTION = 150

        /**
         * Trois objectifs, et leur distance à la borne des 25 % d'écart au TDEE.
         *
         * Le profil du décor dépense ≈ 2 885 kcal, ce qui place cette borne à
         * ≈ 2 164 kcal. [LOIN_DU_GARDE_FOU] laisse passer la correction entière,
         * [SOUS_LE_GARDE_FOU] la fait raboter, [SUR_LE_GARDE_FOU] l'annule.
         */
        const val LOIN_DU_GARDE_FOU = 2400.0
        const val SOUS_LE_GARDE_FOU = 2200.0
        const val SUR_LE_GARDE_FOU = 2164.0

        val PROFIL = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 182.0,
            activity = Activity(WorkActivity.ON_FEET, WeeklySessions(3)),
        )
    }
}
