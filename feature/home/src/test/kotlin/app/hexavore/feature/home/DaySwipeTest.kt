package app.hexavore.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le glissement qui change de journée.
 *
 * Deux règles, et elles ne répondent pas à la même question : **où va-t-on** —
 * lendemain ou veille, et jusqu'où — et **le geste emporte-t-il**, distance ou
 * vitesse. Les séparer permet de dire que la butée du futur ne dépend pas de la force
 * du geste, ce qui est exactement le piège qu'un seul calcul aurait tendu.
 */
class DaySwipeTest {
    @Test
    fun `vers la gauche, le lendemain`() {
        // Le sens d'une page qu'on tourne, et celui du calendrier au-dessus.
        assertEquals(HIER, dayAfterSwipe(AVANT_HIER, AUJOURD_HUI, PLUS_VIEUX, forward = true))
    }

    @Test
    fun `vers la droite, la veille`() {
        assertEquals(HIER, dayAfterSwipe(AUJOURD_HUI, AUJOURD_HUI, PLUS_VIEUX, forward = false))
    }

    @Test
    fun `depuis aujourd hui, demain n existe pas`() {
        // Le calendrier refuse deja d'ouvrir un jour a venir : noter un repas qu'on
        // n'a pas pris n'aurait aucun sens, et la journee n'a rien a montrer.
        assertNull(dayAfterSwipe(AUJOURD_HUI, AUJOURD_HUI, PLUS_VIEUX, forward = true))
    }

    @Test
    fun `la borne du passe est celle que le calendrier sait montrer`() {
        // Au-dela, on se promenerait dans des journees qu'aucune pastille ne designe,
        // sans moyen visible de revenir.
        assertNull(dayAfterSwipe(PLUS_VIEUX, AUJOURD_HUI, PLUS_VIEUX, forward = false))
        assertEquals(PLUS_VIEUX, dayAfterSwipe(PLUS_VIEUX.plusDays(1), AUJOURD_HUI, PLUS_VIEUX, forward = false))
    }

    @Test
    fun `un quart de la largeur emporte la journee`() {
        assertTrue(swipeCarries(offset = -LARGEUR / 4f, velocity = 0f, width = LARGEUR))
        assertFalse(swipeCarries(offset = -LARGEUR / 5f, velocity = 0f, width = LARGEUR))
    }

    @Test
    fun `un geste vif emporte sans aller au quart`() {
        // Sinon, remonter cinq jours demanderait de trainer la page cinq fois sur un
        // quart de l'ecran.
        assertTrue(swipeCarries(offset = -40f, velocity = -1_200f, width = LARGEUR))
    }

    @Test
    fun `une vitesse a contresens n emporte rien`() {
        // Le doigt est reparti en arriere au dernier moment : il annule, il ne
        // confirme pas. Sans cette regle, le geste le plus naturel pour se raviser
        // serait justement celui qui valide.
        assertFalse(swipeCarries(offset = -40f, velocity = 1_200f, width = LARGEUR))
    }

    @Test
    fun `un doigt pose et releve n emporte rien`() {
        // Le cas limite : ni distance, ni sens. Un tap dans la journee ne doit pas
        // changer de jour.
        assertFalse(swipeCarries(offset = 0f, velocity = 0f, width = LARGEUR))
    }

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 9, 17)
        val HIER: LocalDate = AUJOURD_HUI.minusDays(1)
        val AVANT_HIER: LocalDate = AUJOURD_HUI.minusDays(2)
        val PLUS_VIEUX: LocalDate = AUJOURD_HUI.minusMonths(MONTHS_BACK)
        const val LARGEUR = 1_080f
    }
}
