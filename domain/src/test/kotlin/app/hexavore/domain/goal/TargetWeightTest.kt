package app.hexavore.domain.goal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Le poids visé : il suit le sens de la stratégie, et une perte s'arrête au seuil de la
 * maigreur (D160).
 *
 * Chaque refus porte son chiffre, et c'est aussi ce qui s'éprouve : un refus qui ne dirait
 * pas « au moins 62 kg » laisserait l'écran sans rien à proposer.
 */
class TargetWeightTest {
    // --- Le sens -----------------------------------------------------------------

    @Test
    fun `perdre vers un poids plus haut est refuse`() {
        // Le calcul en faisait une prise, sous l'etiquette « perdre ».
        assertEquals(
            TargetProblem.NotBelowCurrent(88.0),
            checkTarget(GoalStrategy.LOSE, heightCm = 182.0, currentKg = 88.0, targetKg = 95.0),
        )
        assertEquals(
            TargetProblem.NotBelowCurrent(88.0),
            checkTarget(GoalStrategy.LOSE, heightCm = 182.0, currentKg = 88.0, targetKg = 88.0),
            "le meme poids n'est pas une perte",
        )
    }

    @Test
    fun `prendre vers un poids plus bas est refuse`() {
        assertEquals(
            TargetProblem.NotAboveCurrent(60.0),
            checkTarget(GoalStrategy.GAIN, heightCm = 175.0, currentKg = 60.0, targetKg = 55.0),
        )
    }

    // --- Le seuil de la maigreur ------------------------------------------------

    @Test
    fun `une perte s arrete au seuil de la maigreur, et le seuil lui-meme est permis`() {
        // 182 cm : 18,5 x 1,82 x 1,82 = 61,3 kg, soit 62 au kilo superieur.
        assertEquals(
            TargetProblem.BelowLean(heightCm = 182.0, leanKg = 62.0),
            checkTarget(GoalStrategy.LOSE, heightCm = 182.0, currentKg = 88.0, targetKg = 61.0),
        )
        assertNull(checkTarget(GoalStrategy.LOSE, heightCm = 182.0, currentKg = 88.0, targetKg = 62.0))
    }

    @Test
    fun `deja au seuil, perdre n est plus propose, des le choix de la strategie`() {
        // 175 cm, 55 kg : sous le seuil de 57 kg. Le poids vise n'a rien a y changer,
        // et le dire avant qu'il soit tape evite de chercher celui qui passerait.
        val attendu = TargetProblem.NoRoomToLose(heightCm = 175.0, leanKg = 57.0)

        assertEquals(attendu, checkTarget(GoalStrategy.LOSE, heightCm = 175.0, currentKg = 55.0, targetKg = null))
        assertEquals(attendu, checkTarget(GoalStrategy.LOSE, heightCm = 175.0, currentKg = 55.0, targetKg = 50.0))
        assertEquals(
            attendu,
            checkTarget(GoalStrategy.LOSE, heightCm = 175.0, currentKg = 57.0, targetKg = null),
            "au seuil tout juste, il ne reste rien a perdre",
        )
    }

    // --- Ce qu'un corps peut peser ----------------------------------------------

    @Test
    fun `une prise au-dela de ce que ce corps peut peser est refusee`() {
        // 175 cm : 245 kg au plus, un IMC de 80.
        assertEquals(
            TargetProblem.AboveRange(heightCm = 175.0, maxKg = 245.0),
            checkTarget(GoalStrategy.GAIN, heightCm = 175.0, currentKg = 70.0, targetKg = 300.0),
        )
        assertNull(checkTarget(GoalStrategy.GAIN, heightCm = 175.0, currentKg = 70.0, targetKg = 245.0))
    }

    // --- Ce qui ne se juge pas ici ----------------------------------------------

    @Test
    fun `un maintien n a pas de poids vise a juger`() {
        assertNull(checkTarget(GoalStrategy.MAINTAIN, heightCm = 175.0, currentKg = 70.0, targetKg = 10.0))
    }

    @Test
    fun `un poids vise absent manque, il n est pas faux`() {
        assertNull(checkTarget(GoalStrategy.LOSE, heightCm = 182.0, currentKg = 88.0, targetKg = null))
        assertNull(checkTarget(GoalStrategy.GAIN, heightCm = 182.0, currentKg = 88.0, targetKg = null))
    }

    @Test
    fun `une taille ou un poids actuel invraisemblables ne se comparent a rien`() {
        // C'est eux que l'ecran signale ; un second message sur le poids vise ne ferait
        // que brouiller le premier.
        assertNull(checkTarget(GoalStrategy.GAIN, heightCm = 175.0, currentKg = 999.0, targetKg = 1_000.0))
        assertNull(checkTarget(GoalStrategy.LOSE, heightCm = 1.0, currentKg = 70.0, targetKg = 60.0))
    }
}
