package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WorkActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Corriger son profil, et ce qui décide des six chiffres.
 *
 * Deux règles se croisent ici, et chacune protège quelque chose de différent.
 * [app.hexavore.domain.goal.GoalOrigin] protège le travail de l'utilisateur : un
 * objectif saisi à la main n'est le résultat d'aucun calcul, et rien ne doit le
 * recalculer à sa place. `D04` protège son histoire : l'objectif de la semaine dernière
 * ne doit pas être repeint par celui d'aujourd'hui.
 *
 * Les deux se cassent en silence. Un cas d'usage qui recalculerait par prudence ne
 * produirait aucune erreur, seulement six chiffres qui redeviennent ceux du calcul ; une
 * mise à jour en place n'en produirait pas davantage, seulement un mois d'historique
 * jugé sur une règle qu'il n'avait pas.
 */
class ReviseGoalTest {
    @Test
    fun `corriger son profil ecrit une nouvelle ligne et clot celle qui court`() = runTest {
        // D04. Le port n'offre aucun `update`, mais rien n'empecherait un appelant
        // d'ecrire une seconde ligne active : c'est `replace` qui clot la premiere,
        // et ce test dit qu'on passe bien par lui.
        val goals = goals(objectifCourant())

        revise(goals, profile = PLUS_PETIT)

        assertEquals(2, goals.all.size, "une correction ajoute une ligne, elle n en modifie aucune")
        val ancien = goals.all.single { it.id == INITIAL }
        val neuf = goals.all.single { it.active }
        assertEquals(AUJOURD_HUI, ancien.endedAt, "l ancien est clos a la date de debut du nouveau")
        assertEquals(AUJOURD_HUI, neuf.startedAt)
        assertNotEquals(ancien.daily.kcal, neuf.daily.kcal, "quatre centimetres de moins changent la depense")
    }

    @Test
    fun `les six chiffres sont ecrits tels quels, jamais recalcules`() = runTest {
        // Le coeur de D60, vu du domaine. Un objectif manuel n'est le resultat
        // d'aucun calcul : ce cas d'usage n'a donc aucun moyen de le reconstruire, et
        // il ne doit surtout pas essayer. Les chiffres passes ici ne ressemblent a
        // rien de ce que `CalculateDailyGoal` produirait pour ce profil.
        val goals = goals(objectifCourant())

        revise(goals, daily = SAISI_A_LA_MAIN, origin = GoalOrigin.MANUAL)

        val neuf = goals.all.single { it.active }
        assertEquals(SAISI_A_LA_MAIN, neuf.daily, "un recalcul de prudence aurait remplace la saisie")
        assertEquals(GoalOrigin.MANUAL, neuf.origin)
    }

    @Test
    fun `un objectif manuel garde son poids cible et son echeance`() = runTest {
        // Ils ne pilotent plus les six chiffres, mais ils decrivent le cap annonce et
        // le journal de poids en tirera sa trajectoire (tranche 7). Les effacer au
        // passage en manuel supprimerait une information que personne n'a demande de
        // supprimer.
        val goals = goals(objectifCourant())

        revise(goals, daily = SAISI_A_LA_MAIN, origin = GoalOrigin.MANUAL)

        val neuf = goals.all.single { it.active }
        assertEquals(DEMANDE.targetWeightKg, neuf.targetWeightKg)
        assertEquals(DEMANDE.targetDate, neuf.targetDate)
    }

    @Test
    fun `passer en manuel est un changement de cap, meme a chiffres identiques`() = runTest {
        // Les six chiffres ne bougent pas, et pourtant ce n'est plus le meme objectif :
        // le premier suivra la prochaine correction de profil, le second non. Une
        // comparaison qui ignorerait la provenance n'ecrirait rien, et la bascule
        // serait perdue au retour a l'ecran.
        val goals = goals(objectifCourant())

        revise(goals, daily = propose, origin = GoalOrigin.MANUAL)

        assertEquals(2, goals.all.size, "la bascule vers le manuel ouvre bien une version")
        assertEquals(GoalOrigin.MANUAL, goals.all.single { it.active }.origin)
    }

    @Test
    fun `enregistrer sans rien changer n ecrit aucune version`() = runTest {
        // Sinon l'historique des changements de cap devient un journal de
        // consultations, et c'est justement la contrepartie qu'on paie en versionnant.
        val goals = goals(objectifCourant())

        revise(goals)

        assertEquals(1, goals.all.size, "aucune correction, donc aucune ligne")
        assertTrue(goals.all.single().active, "et celui qui courait court toujours")
    }

    @Test
    fun `une pesee n est enregistree que si le poids a change`() = runTest {
        // Le poids affiche est celui de la derniere pesee connue. Le reecrire a la
        // date du jour parce que l'ecran a ete ouvert affirmerait qu'on s'est pese
        // aujourd'hui, et la moyenne mobile compterait une mesure inexistante.
        val goals = goals(objectifCourant())

        revise(goals, profile = PLUS_PETIT)

        assertEquals(1, weights.entries.size, "aucune pesee du jour n a ete inventee")
        assertEquals(DERNIERE_PESEE, weights.latest?.date)
    }

    @Test
    fun `un poids corrige devient la pesee du jour`() = runTest {
        val goals = goals(objectifCourant())
        val demande = DEMANDE.copy(currentWeightKg = 86.0)

        revise(goals, request = demande, daily = calculate(PROFIL, demande).goal)

        assertEquals(AUJOURD_HUI, weights.latest?.date)
        assertEquals(86.0, weights.latest?.weightKg)
        assertEquals(2, goals.all.size, "deux kilos de moins, donc un objectif de moins")
    }

    @Test
    fun `un objectif corrige le jour meme prend la journee entiere`() = runTest {
        // L'ancien est clos a sa propre date de debut : il ne couvre alors aucune
        // journee, et c'est juste. Le cas etait eprouve sur `coversOn` seule
        // (GoalCoverageTest) avant que l'ecran existe ; il l'est ici de bout en bout.
        val goals = goals(objectifCourant(depuis = AUJOURD_HUI))
        val maintien = DEMANDE.copy(strategy = GoalStrategy.MAINTAIN, targetWeightKg = null)

        revise(goals, request = maintien, daily = calculate(PROFIL, maintien).goal)

        val ancien = goals.all.single { it.id == INITIAL }
        assertEquals(AUJOURD_HUI, ancien.endedAt)
        assertFalse(ancien.coversOn(AUJOURD_HUI), "clos a sa propre date de debut, il ne couvre plus rien")
        assertEquals(goals.all.single { it.active }, goals.observeGoalOn(AUJOURD_HUI).first())
    }

    @Test
    fun `le profil corrige est enregistre, et le reste ne bouge pas`() = runTest {
        val goals = goals(objectifCourant())
        val sedentaire = PROFIL.copy(activity = Activity(WorkActivity.DESK))

        revise(goals, profile = sedentaire, daily = calculate(sedentaire, DEMANDE).goal)

        assertEquals(Activity(WorkActivity.DESK), profiles.saved?.activity)
        assertEquals(PROFIL.birthDate, profiles.saved?.birthDate, "le reste du profil n a pas bouge")
    }

    // --- Montage -----------------------------------------------------------------

    private val clock = FixedClock.atNoon(AUJOURD_HUI)
    private val profiles = InMemoryProfiles(PROFIL)
    private val weights = InMemoryWeightLog(listOf(WeightEntry(DERNIERE_PESEE, POIDS)))
    private val calculate = CalculateDailyGoal(clock)
    private val ids = SequentialIdGenerator("goal")

    /** Ce que le calcul propose pour le profil de référence. */
    private val propose: DailyGoal get() = calculate(PROFIL, DEMANDE).goal

    private fun goals(initial: Goal) = InMemoryGoals(listOf(initial))

    private fun objectifCourant(depuis: LocalDate = AUJOURD_HUI.minusMonths(1)) = Goal(
        id = INITIAL,
        startedAt = depuis,
        origin = GoalOrigin.CALCULATED,
        strategy = DEMANDE.strategy,
        targetWeightKg = DEMANDE.targetWeightKg,
        targetDate = DEMANDE.targetDate,
        daily = propose,
    )

    private suspend fun revise(
        goals: InMemoryGoals,
        profile: UserProfile = PROFIL,
        request: GoalRequest = DEMANDE,
        // Ce que l'ecran affichait au moment de l'appui. Par defaut le calcul, parce
        // que c'est le mode courant ; en manuel, ce que l'utilisateur a tape.
        daily: DailyGoal = calculate(profile, request).goal,
        origin: GoalOrigin = GoalOrigin.CALCULATED,
    ) = ReviseGoal(profiles, weights, goals, clock, ids)(
        GoalRevision(profile = profile, request = request, daily = daily, origin = origin),
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)
        val DERNIERE_PESEE: LocalDate = AUJOURD_HUI.minusDays(3)
        val INITIAL = GoalId("goal-initial")
        const val POIDS = 88.0

        /** L'exemple de docs/03 : homme, 35 ans, 182 cm, 88 kg, 80 kg en 6 mois. */
        val PROFIL = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 182.0,
            activity = Activity(WorkActivity.ON_FEET, WeeklySessions(3)),
        )

        val DEMANDE = GoalRequest(
            strategy = GoalStrategy.LOSE,
            currentWeightKg = POIDS,
            targetWeightKg = 80.0,
            targetDate = AUJOURD_HUI.plusDays(182),
        )

        /** Une correction qui déplace la dépense, donc les six chiffres proposés. */
        val PLUS_PETIT = PROFIL.copy(heightCm = 178.0)

        /**
         * Six chiffres ronds, que le calcul ne produirait pour personne.
         *
         * C'est ce qui rend le test lisible : si l'un d'eux revient différent, c'est
         * qu'un calcul est passé par là.
         */
        val SAISI_A_LA_MAIN = DailyGoal(
            kcal = 2_000.0,
            protein = 150.0,
            carbs = 200.0,
            sugars = 40.0,
            fat = 60.0,
            fiber = 30.0,
        )
    }
}
