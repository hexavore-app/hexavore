package app.hexavore.domain.progress

import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroTotal
import app.hexavore.domain.nutrition.MacroTotals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La fourchette d'une journée parfaite, et **le sens dans lequel elle penche**.
 *
 * Ce qui s'éprouve ici est la seule chose que cette règle ait de subtil : se tromper
 * dans un sens ne coûte pas ce que coûte se tromper dans l'autre, et la fourchette le
 * dit. Une règle symétrique passerait la moitié de ces cas et se tromperait sur
 * l'autre moitié — sans que rien ne le signale, puisque les deux produisent un booléen.
 */
internal class PerfectDayTest {
    // --- Perte : large en dessous, serre au-dessus -------------------------------

    @Test
    fun `en perte, manger un cinquieme de moins reste parfait`() {
        assertTrue(perfect(kcal = 1600.0, protein = 120.0, strategy = GoalStrategy.LOSE))
    }

    @Test
    fun `en perte, manger un dixieme de trop reste parfait`() {
        assertTrue(perfect(kcal = 2190.0, protein = 131.0, strategy = GoalStrategy.LOSE))
    }

    @Test
    fun `en perte, manger un cinquieme de trop ne l est pas`() {
        // Le depassement efface le deficit qui fait tout le travail : c'est le cote
        // serre, et il l'est a dix pour cent.
        assertFalse(perfect(kcal = 2400.0, protein = 144.0, strategy = GoalStrategy.LOSE))
    }

    // --- Prise : l'inverse, exactement -------------------------------------------

    @Test
    fun `en prise, manger un cinquieme de trop reste parfait`() {
        assertTrue(perfect(kcal = 2400.0, protein = 144.0, strategy = GoalStrategy.GAIN))
    }

    @Test
    fun `en prise, manger un cinquieme de moins ne l est pas`() {
        // Le surplus est ce qui construit : le manquer est ce qui coute.
        assertFalse(perfect(kcal = 1600.0, protein = 120.0, strategy = GoalStrategy.GAIN))
    }

    // --- Maintien : serre des deux cotes -----------------------------------------

    @Test
    fun `en maintien, un dixieme d ecart passe des deux cotes`() {
        assertTrue(perfect(kcal = 1820.0, protein = 110.0, strategy = GoalStrategy.MAINTAIN))
        assertTrue(perfect(kcal = 2190.0, protein = 131.0, strategy = GoalStrategy.MAINTAIN))
    }

    @Test
    fun `en maintien, un cinquieme d ecart ne passe ni d un cote ni de l autre`() {
        assertFalse(perfect(kcal = 1600.0, protein = 120.0, strategy = GoalStrategy.MAINTAIN))
        assertFalse(perfect(kcal = 2400.0, protein = 144.0, strategy = GoalStrategy.MAINTAIN))
    }

    // --- Ce qui se juge, et ce qui ne se juge pas --------------------------------

    @Test
    fun `les quatre autres compteurs ne decident de rien`() {
        // Juger les six rendrait la serie si rare qu'elle ne recompenserait plus rien :
        // les fibres, en particulier, se tiennent mal sans y penser toute la journee.
        val totals = totalsOf(kcal = 2000.0, protein = 120.0, fiber = 0.0, sugars = 300.0)

        assertTrue(PerfectDay.of(totals, GOAL, GoalStrategy.LOSE))
    }

    @Test
    fun `une limite ne se rate que par le haut`() {
        // La regle est ecrite entiere parce qu'elle est une regle sur une macro, pas
        // sur la liste du jour : aucune limite n'entre aujourd'hui dans JUDGED.
        assertTrue(PerfectDay.within(value = 5.0, target = 50.0, macro = Macro.SUGARS, strategy = GoalStrategy.LOSE))
        assertFalse(PerfectDay.within(value = 70.0, target = 50.0, macro = Macro.SUGARS, strategy = GoalStrategy.LOSE))
    }

    @Test
    fun `une cible manquee de trop se rate par le bas`() {
        assertFalse(PerfectDay.within(value = 5.0, target = 50.0, macro = Macro.FIBER, strategy = GoalStrategy.LOSE))
    }

    @Test
    fun `sans objectif, aucune journee n est parfaite`() {
        // Elle n'a rien a quoi se comparer (D04), et « parfaite par defaut » donnerait
        // une serie a qui n'a pas encore repondu aux cinq questions.
        assertFalse(PerfectDay.of(totalsOf(2000.0, 120.0), goal = null, strategy = GoalStrategy.LOSE))
    }

    @Test
    fun `un objectif a zero ne se rate pas`() {
        // Diviser par lui n'aurait pas de sens, et un compteur qu'on ne vise pas ne
        // peut pas etre manque.
        assertTrue(PerfectDay.within(value = 0.0, target = 0.0, macro = Macro.PROTEIN, strategy = GoalStrategy.LOSE))
    }

    private fun perfect(kcal: Double, protein: Double, strategy: GoalStrategy): Boolean =
        PerfectDay.of(totalsOf(kcal, protein), GOAL, strategy)

    private fun totalsOf(kcal: Double, protein: Double, fiber: Double = 30.0, sugars: Double = 40.0) = MacroTotals(
        calories = MacroTotal(kcal, complete = true),
        protein = MacroTotal(protein, complete = true),
        carbs = MacroTotal(200.0, complete = true),
        sugars = MacroTotal(sugars, complete = true),
        fat = MacroTotal(60.0, complete = true),
        fiber = MacroTotal(fiber, complete = true),
    )

    private companion object {
        val GOAL = DailyGoal(kcal = 2000.0, protein = 120.0, carbs = 220.0, sugars = 50.0, fat = 67.0, fiber = 30.0)
    }
}
