package app.hexavore.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos

/**
 * La figure et ses six lettres tiennent dans la zone qu'on leur donne.
 *
 * **Ce que ces cas défendent est un défaut constaté à l'écran** : le « C » des
 * calories sortait par le haut, rogné net par le défilement qui encadre l'hexagone, et
 * les cinq autres lettres flottaient loin du contour. Les deux venaient de la même
 * cause — la marge des lettres était comptée depuis le cercle des sommets, alors
 * qu'une lettre se pose face à une arête, plus proche du centre d'un facteur `√3/2`.
 */
class MacroHexagonGeometryTest {
    @Test
    fun `la lettre du haut tient entiere dans la zone`() {
        val place = hexagonFit(width = WIDTH, height = HEIGHT, labelExtent = EXTENT, glow = GLOW, gap = GAP)

        // La lettre du haut est posee par son centre a `labelRadius` au-dessus du
        // centre de la zone ; son bord superieur est donc une demi-lettre plus haut.
        assertTrue(
            place.labelRadius + EXTENT <= HEIGHT / 2f,
            "la lettre du haut deborde de ${place.labelRadius + EXTENT - HEIGHT / 2f} px",
        )
    }

    @Test
    fun `les lettres obliques et la lueur tiennent en largeur`() {
        val place = hexagonFit(width = WIDTH, height = HEIGHT, labelExtent = EXTENT, glow = GLOW, gap = GAP)

        // Les quatre lettres obliques sont a trente degres de l'horizontale.
        val bordDeLaLettre = place.labelRadius * cos(Math.toRadians(HALF_SECTOR.toDouble())).toFloat() + EXTENT
        assertTrue(bordDeLaLettre <= WIDTH / 2f, "une lettre oblique deborde de ${bordDeLaLettre - WIDTH / 2f} px")

        // Les deux sommets lateraux, eux, sont a `radius`, et leur lueur deborde.
        assertTrue(place.radius + GLOW <= WIDTH / 2f, "la lueur d'un sommet deborde")
    }

    @Test
    fun `la lettre se pose au-dela de l arete, pas du sommet`() {
        val place = hexagonFit(width = WIDTH, height = HEIGHT, labelExtent = EXTENT, glow = GLOW, gap = GAP)

        // C'est toute la correction : l'axe d'un quartier traverse le milieu d'une
        // arete, et c'est de la que se compte l'intervalle. Le mesurer depuis le
        // sommet eloignait les six lettres d'un huitieme du rayon.
        val arete = place.radius * APOTHEM_RATIO
        assertEquals(GLOW + GAP + EXTENT, place.labelRadius - arete, TOLERANCE)
    }

    @Test
    fun `une zone large est bornee par la lettre du haut`() {
        // Le rayon vaut alors exactement ce que la lettre du haut laisse. Sans cette
        // egalite, la figure serait plus petite que la place disponible -- ce qui se
        // verrait tout autant qu'un debordement.
        val place = hexagonFit(width = WIDTH * 4f, height = HEIGHT, labelExtent = EXTENT, glow = GLOW, gap = GAP)

        assertEquals(HEIGHT / 2f, place.labelRadius + EXTENT, TOLERANCE)
    }

    @Test
    fun `une zone haute est bornee par la lueur des sommets`() {
        // Les sommets lateraux sont a `radius` du centre, et leur lueur deborde
        // encore : c'est elle qui touche le bord, pas le contour.
        val place = hexagonFit(width = WIDTH, height = WIDTH * 10f, labelExtent = EXTENT, glow = GLOW, gap = GAP)

        assertEquals(WIDTH / 2f, place.radius + GLOW, TOLERANCE)
    }

    @Test
    fun `une zone haute et etroite est bornee par les lettres obliques`() {
        // Quand les lettres sont grandes devant la largeur, ce sont elles qui butent
        // les premieres -- avant la lueur des sommets, qui est pourtant plus loin du
        // centre. Les trois contraintes sont donc bien trois.
        val place = hexagonFit(width = 400f, height = 4000f, labelExtent = 30f, glow = 20f, gap = 10f)

        val bordDeLaLettre = place.labelRadius * cos(Math.toRadians(HALF_SECTOR.toDouble())).toFloat() + 30f
        assertEquals(200f, bordDeLaLettre, TOLERANCE)
        assertTrue(place.radius + 20f < 200f, "la lueur des sommets ne touche pas le bord, elle a de la place")
    }

    @Test
    fun `une zone minuscule ne rend pas un rayon negatif`() {
        // Deux lettres ne tiennent pas dans dix pixels. Un rayon negatif dessinerait
        // un hexagone retourne, ce qui est bien pire qu'une figure absente.
        val place = hexagonFit(width = 10f, height = 10f, labelExtent = EXTENT, glow = GLOW, gap = GAP)

        assertEquals(0f, place.radius, TOLERANCE)
    }

    private companion object {
        /** Une largeur d'ecran ordinaire en pixels, marges de l'ecran deduites. */
        const val WIDTH = 984f
        const val HEIGHT = WIDTH * APOTHEM_RATIO
        const val EXTENT = 36f
        const val GLOW = 36f
        const val GAP = 18f
        const val TOLERANCE = 0.01f
    }
}
