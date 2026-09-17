package app.hexavore.feature.home

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Ce qu'un défilement cède au repli du calendrier.
 *
 * **Deux défauts rapportés à l'usage, et un seul d'entre eux se lit ici.** Le premier
 * est une condition — quels gestes replient — et ces cas la tiennent. Le second était
 * une question de *portée* : la connexion englobait le calendrier, donc défiler dans
 * le mois déplié le refermait. Cela ne se corrige pas par une condition mais par la
 * disposition, et aucun cas ne peut le voir.
 */
class CalendarCollapseTest {
    @Test
    fun `deplie, un doigt qui monte est consomme par le repli`() {
        // Consomme et non ignore : sans cela la page se deplacerait pendant que la
        // hauteur du calendrier s'anime, et le contenu ferait un bond.
        val doigt = Offset(0f, -30f)

        assertEquals(doigt, collapsingDelta(expanded = true, available = doigt))
    }

    @Test
    fun `deplie, un doigt qui descend ne replie rien`() {
        // On remonte dans la page : refermer ce qu'on vient d'ouvrir a ce moment-la
        // serait le contraire du geste.
        assertEquals(Offset.Zero, collapsingDelta(expanded = true, available = Offset(0f, 30f)))
    }

    @Test
    fun `replie, aucun geste ne consomme quoi que ce soit`() {
        // Il n'y a rien a fermer, et manger le delta ferait une page qui ne defile pas.
        assertEquals(Offset.Zero, collapsingDelta(expanded = false, available = Offset(0f, -30f)))
        assertEquals(Offset.Zero, collapsingDelta(expanded = false, available = Offset(0f, 30f)))
    }

    @Test
    fun `un geste horizontal ne replie rien`() {
        // C'est le bandeau qui change de semaine.
        assertEquals(Offset.Zero, collapsingDelta(expanded = true, available = Offset(-40f, 0f)))
    }

    @Test
    fun `un geste immobile ne replie rien`() {
        // Le cas limite du precedent : un `y` a zero n'est pas un doigt qui monte.
        assertEquals(Offset.Zero, collapsingDelta(expanded = true, available = Offset.Zero))
    }

    @Test
    fun `ce que la page n a pas pu defiler s accumule`() {
        // Le delta arrive intact : la page etait deja en haut, donc elle n'en a rien
        // pris. C'est cela, « etre en haut » -- aucun etat a lire.
        val premier = pulledBy(previous = 0f, expanded = false, available = Offset(0f, 20f), byUser = true)

        assertEquals(20f, premier)
        assertEquals(50f, pulledBy(premier, expanded = false, available = Offset(0f, 30f), byUser = true))
    }

    @Test
    fun `un defilement lance qui bute en haut n accumule rien`() {
        // Sans cette regle, toute lecture rapide finirait par deplier le mois : un
        // elan qui touche le haut rendrait exactement le meme delta qu'une traction.
        assertEquals(0f, pulledBy(previous = 40f, expanded = false, available = Offset(0f, 30f), byUser = false))
    }

    @Test
    fun `deplie, il n y a plus rien a tirer`() {
        assertEquals(0f, pulledBy(previous = 40f, expanded = true, available = Offset(0f, 30f), byUser = true))
    }

    @Test
    fun `un doigt qui remonte efface la traction`() {
        // Tirer a moitie, repartir vers le haut, puis tirer de nouveau ne doit pas
        // ouvrir le mois d'un demi-geste.
        assertEquals(0f, pulledBy(previous = 40f, expanded = false, available = Offset(0f, -10f), byUser = true))
    }

    @Test
    fun `au milieu de la page, la traction ne commence pas`() {
        // La page a tout consomme : il ne reste rien, et un doigt qui descend au
        // milieu d'une journee chargee remonte la page, il ne demande pas un mois.
        assertEquals(0f, pulledBy(previous = 0f, expanded = false, available = Offset.Zero, byUser = true))
    }
}
