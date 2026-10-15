package app.hexavore.domain.goal

import app.hexavore.domain.profile.Sex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Les quatre garde-fous, chacun sur ses **deux** bornes.
 *
 * Un garde-fou ne se teste pas en vérifiant qu'il existe : il se teste juste en
 * dessous et juste au-dessus de son seuil. Éprouvé d'un seul côté, un seuil peut être
 * placé n'importe où — trop haut, il ne protège de rien ; trop bas, il bride une
 * demande légitime — et rien ne le dirait.
 *
 * Les valeurs sont posées directement plutôt que dérivées d'un profil : c'est ce qui
 * permet d'isoler **un** garde-fou à la fois. Avec un profil réel, deux d'entre eux
 * mordent souvent ensemble, et le test ne dirait plus lequel.
 *
 * @see docs/03-nutrition-calculs.md
 */
class GoalSafetyPolicyTest {
    // --- 1. Vitesse : 1 % du poids par semaine en perte, 0,5 % en prise ---------

    @Test
    fun `une perte juste sous 1 pourcent par semaine passe`() {
        // 80 kg -> 0,8 kg/sem -> 880 kcal/jour. On demande 870.
        val budget = apply(rawDeltaKcal = -870.0, tdee = 4_000.0, currentWeightKg = 80.0)

        assertEquals(-870.0, budget.dailyDeltaKcal, TOLERANCE)
        assertFalse(budget.capped)
    }

    @Test
    fun `une perte au-dela de 1 pourcent par semaine est ramenee au seuil`() {
        val budget = apply(rawDeltaKcal = -900.0, tdee = 4_000.0, currentWeightKg = 80.0)

        assertEquals(-880.0, budget.dailyDeltaKcal, TOLERANCE)
        assertEquals(setOf(GoalGuard.SPEED), budget.guardsApplied)
    }

    @Test
    fun `la prise est bornee deux fois plus bas que la perte`() {
        // 0,5 % et non 1 % : prendre trop vite, c'est prendre du gras. La borne est
        // asymetrique parce que les deux phenomenes ne le sont pas.
        val budget = apply(rawDeltaKcal = 600.0, tdee = 10_000.0, currentWeightKg = 80.0)

        assertEquals(440.0, budget.dailyDeltaKcal, TOLERANCE)
        assertTrue(GoalGuard.SPEED in budget.guardsApplied)
    }

    // --- 2. Ecart maximal au TDEE : 25 % ---------------------------------------

    @Test
    fun `un ecart juste sous 25 pourcent du TDEE passe`() {
        // TDEE 2 000 -> 500 kcal. Le poids est choisi pour que la vitesse ne morde pas.
        val budget = apply(rawDeltaKcal = -490.0, tdee = 2_000.0, currentWeightKg = 100.0, sex = Sex.FEMALE)

        assertEquals(-490.0, budget.dailyDeltaKcal, TOLERANCE)
        assertFalse(budget.capped)
    }

    @Test
    fun `un ecart au-dela de 25 pourcent du TDEE est ramene au seuil`() {
        val budget = apply(rawDeltaKcal = -600.0, tdee = 2_000.0, currentWeightKg = 100.0, sex = Sex.FEMALE)

        assertEquals(-500.0, budget.dailyDeltaKcal, TOLERANCE)
        assertEquals(setOf(GoalGuard.DEVIATION), budget.guardsApplied)
    }

    // --- 3. Plancher absolu : 1 200 / 1 500 / 1 350 kcal -----------------------

    @Test
    fun `un objectif juste au-dessus du plancher passe`() {
        // TDEE 1 500, femme : le plancher est a 1 200, soit un ecart de -300.
        val budget = apply(rawDeltaKcal = -290.0, tdee = 1_500.0, currentWeightKg = 60.0, sex = Sex.FEMALE)

        assertEquals(-290.0, budget.dailyDeltaKcal, TOLERANCE)
        assertFalse(budget.capped)
    }

    @Test
    fun `un objectif sous le plancher y est remonte`() {
        val budget = apply(rawDeltaKcal = -350.0, tdee = 1_500.0, currentWeightKg = 60.0, sex = Sex.FEMALE)

        assertEquals(-300.0, budget.dailyDeltaKcal, TOLERANCE)
        assertEquals(setOf(GoalGuard.FLOOR), budget.guardsApplied)
        assertEquals(1_200.0, 1_500.0 + budget.dailyDeltaKcal, TOLERANCE)
    }

    @Test
    fun `le plancher d un sexe non precise est la moyenne des deux`() {
        // 1 350, et non un repli sur le plus permissif des deux : le plancher protege
        // d'une carence, et choisir 1 200 « au cas ou » serait choisir le moins sur.
        //
        // TDEE 1 700 : le plancher (-350) mord avant l'ecart de 25 % (-425). Au-dessus
        // de 1 800, c'est l'ecart qui mordrait le premier et ce test ne dirait rien du
        // plancher.
        val budget = apply(rawDeltaKcal = -400.0, tdee = 1_700.0, currentWeightKg = 100.0, sex = Sex.UNSPECIFIED)

        assertEquals(setOf(GoalGuard.FLOOR), budget.guardsApplied)
        assertEquals(1_350.0, 1_700.0 + budget.dailyDeltaKcal, TOLERANCE)
    }

    // --- 3 bis. Le plancher retient un deficit, il ne cree jamais de surplus -----

    @Test
    fun `sous le plancher, une perte tombe a zero au lieu de devenir une prise`() {
        // D160. Femme, depense de 1 000 kcal : le plancher a 1 200 remontait l'objectif
        // de 200 kcal au-dessus de la depense, et faisait d'une perte une prise.
        val budget = apply(rawDeltaKcal = -150.0, tdee = 1_000.0, currentWeightKg = 45.0, sex = Sex.FEMALE)

        assertEquals(0.0, budget.dailyDeltaKcal, TOLERANCE)
        assertEquals(setOf(GoalGuard.FLOOR), budget.guardsApplied)
        assertEquals(1_200.0, budget.withholdingFloorKcal, "l'ecran doit pouvoir dire quel plancher a tout retenu")
    }

    @Test
    fun `sous le plancher, un maintien reste un maintien`() {
        // Il devenait une prise de la meme facon, sous l'etiquette « maintenir ».
        val budget = apply(rawDeltaKcal = 0.0, tdee = 1_000.0, currentWeightKg = 45.0, sex = Sex.FEMALE)

        assertEquals(0.0, budget.dailyDeltaKcal, TOLERANCE)
        assertFalse(budget.capped)
        assertNull(budget.withholdingFloorKcal)
    }

    @Test
    fun `sous le plancher, une prise garde ses propres bornes`() {
        // Depense de 900 kcal : la vitesse autorise 247,5 kcal, l'ecart 225, le plafond
        // de prise 180. Le plancher portait la prise a 300 par-dessus les trois, et la
        // date proposee ne levait alors jamais l'alerte.
        val budget = apply(rawDeltaKcal = 600.0, tdee = 900.0, currentWeightKg = 45.0, sex = Sex.FEMALE)

        assertEquals(180.0, budget.dailyDeltaKcal, TOLERANCE)
        assertFalse(GoalGuard.FLOOR in budget.guardsApplied, "le plancher n'a rien a dire d'une prise")
    }

    @Test
    fun `au-dessus du plancher, il ne retient qu une part du deficit et n annonce rien`() {
        // Le cas d'avant D160, inchange : le deficit est reduit, pas annule, et une date
        // reste a proposer.
        val budget = apply(rawDeltaKcal = -350.0, tdee = 1_500.0, currentWeightKg = 60.0, sex = Sex.FEMALE)

        assertEquals(-300.0, budget.dailyDeltaKcal, TOLERANCE)
        assertNull(budget.withholdingFloorKcal)
    }

    // --- 4. Plafond de prise : +20 % du TDEE -----------------------------------

    @Test
    fun `une prise juste sous 20 pourcent du TDEE passe`() {
        // TDEE 2 500 -> plafond 500. Poids 100 kg -> la vitesse autorise 550.
        val budget = apply(rawDeltaKcal = 490.0, tdee = 2_500.0, currentWeightKg = 100.0)

        assertEquals(490.0, budget.dailyDeltaKcal, TOLERANCE)
        assertFalse(budget.capped)
    }

    @Test
    fun `une prise au-dela de 20 pourcent du TDEE est ramenee au seuil`() {
        val budget = apply(rawDeltaKcal = 520.0, tdee = 2_500.0, currentWeightKg = 100.0)

        assertEquals(500.0, budget.dailyDeltaKcal, TOLERANCE)
        assertEquals(setOf(GoalGuard.GAIN_CEILING), budget.guardsApplied)
    }

    // --- La propriete commune aux quatre ---------------------------------------

    @Test
    fun `aucun garde-fou n augmente jamais l ambition`() {
        // La propriete qui rend leur ordre sans importance. Un garde-fou capable de
        // relever un deficit serait un garde-fou qui l'aggrave dans certains cas, et
        // personne ne le verrait -- l'objectif resterait plausible.
        //
        // Sur trois depenses, dont deux sous le plancher d'un homme : c'est la que la
        // propriete ne tenait pas, et un seul TDEE confortable ne pouvait pas le voir
        // (D160).
        val demandes = listOf(-2_000.0, -900.0, -350.0, -10.0, 0.0, 10.0, 350.0, 900.0, 2_000.0)
        val depenses = listOf(900.0, 1_400.0, 2_400.0)

        depenses.forEach { tdee ->
            demandes.forEach { raw ->
                val retenu = apply(rawDeltaKcal = raw, tdee = tdee, currentWeightKg = 75.0).dailyDeltaKcal
                assertTrue(
                    kotlin.math.abs(retenu) <= kotlin.math.abs(raw) + TOLERANCE,
                    "depense $tdee, demande $raw, retenu $retenu : le garde-fou a augmente l ambition",
                )
                assertTrue(retenu * raw >= 0, "depense $tdee, demande $raw, retenu $retenu : le sens a change")
            }
        }
    }

    @Test
    fun `un maintien ne declenche aucun garde-fou`() {
        val budget = apply(rawDeltaKcal = 0.0, tdee = 2_400.0, currentWeightKg = 75.0)

        assertEquals(0.0, budget.dailyDeltaKcal, TOLERANCE)
        assertFalse(budget.capped)
    }

    // --- Ce que l'ecran propose quand un garde-fou mord ------------------------

    @Test
    fun `un garde-fou qui mord propose une date atteignable`() {
        // L'application ne refuse jamais : elle recalcule la date et la propose.
        // L'interdiction pure pousse les gens a mentir sur leur poids pour contourner
        // l'outil.
        val budget = GoalSafetyPolicy.apply(
            rawDeltaKcal = -2_000.0,
            tdee = 2_400.0,
            currentWeightKg = 80.0,
            targetWeightKg = 72.0,
            sex = Sex.MALE,
            from = AUJOURD_HUI,
        )

        // 8 kg a 600 kcal/jour (25 % de 2 400) : 8 x 7 700 / 600 = 103 jours.
        assertTrue(budget.capped)
        assertEquals(AUJOURD_HUI.plusDays(103), budget.reachableOn)
    }

    @Test
    fun `une echeance tenable ne propose aucune autre date`() {
        val budget = GoalSafetyPolicy.apply(
            rawDeltaKcal = -300.0,
            tdee = 2_400.0,
            currentWeightKg = 80.0,
            targetWeightKg = 72.0,
            sex = Sex.MALE,
            from = AUJOURD_HUI,
        )

        assertFalse(budget.capped)
        assertNull(budget.reachableOn, "rien n a ete borne : il n y a pas d autre date a proposer")
    }

    @Test
    fun `la date proposee est arrondie au jour superieur`() {
        // Proposer un jour trop tot reviendrait a proposer une echeance qu'on vient
        // precisement de declarer intenable.
        val budget = GoalSafetyPolicy.apply(
            rawDeltaKcal = -5_000.0,
            tdee = 2_000.0,
            currentWeightKg = 70.0,
            targetWeightKg = 69.0,
            sex = Sex.MALE,
            from = AUJOURD_HUI,
        )

        // 1 kg a 500 kcal/jour : 15,4 jours -> 16.
        assertNotNull(budget.reachableOn)
        assertEquals(AUJOURD_HUI.plusDays(16), budget.reachableOn)
    }

    private fun apply(
        rawDeltaKcal: Double,
        tdee: Double,
        currentWeightKg: Double,
        sex: Sex = Sex.MALE,
    ): SafeEnergyBudget = GoalSafetyPolicy.apply(
        rawDeltaKcal = rawDeltaKcal,
        tdee = tdee,
        currentWeightKg = currentWeightKg,
        targetWeightKg = null,
        sex = sex,
        from = AUJOURD_HUI,
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)
        const val TOLERANCE = 0.01
    }
}
