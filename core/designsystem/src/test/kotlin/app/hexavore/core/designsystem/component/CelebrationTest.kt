package app.hexavore.core.designsystem.component

import app.hexavore.core.designsystem.theme.Motion
import app.hexavore.core.designsystem.theme.Timing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Combien de temps une célébration reste à l'écran ([D167][decisions]).
 *
 * La durée se décide hors de Compose, comme la règle d'un champ numérique : c'est une
 * fonction sur un nombre, et c'est elle qui faisait passer le palier en une image.
 *
 * [decisions]: docs/11-decisions.md
 */
class CelebrationTest {
    @Test
    fun `animee, elle dure sept entrees de contenu`() {
        assertEquals(1_400L, celebrationMillis(Motion.Standard.contentEnterMillis))
    }

    @Test
    fun `sans animations, elle reste le temps d etre lue`() {
        // Le defaut : la duree tombait a zero avec `Motion.None`, et le titre ne
        // restait qu'une image.
        assertEquals(Timing.CELEBRATION_READING_MILLIS, celebrationMillis(Motion.None.contentEnterMillis))
    }

    @Test
    fun `le temps de lecture ne se compte pas en images`() {
        // Un titre et une ligne : deux secondes au moins, sans quoi on les voit passer
        // sans les lire. Et pas plus de cinq, la fenetre d'annulation : une
        // celebration ne doit pas rester plus longtemps qu'un message qui demande un
        // geste.
        assertTrue(Timing.CELEBRATION_READING_MILLIS >= 2_000L)
        assertTrue(Timing.CELEBRATION_READING_MILLIS <= Timing.UNDO_WINDOW_MILLIS)
    }
}
