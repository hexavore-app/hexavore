package app.hexavore.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La géométrie de la réduction, seule part de la photo qui tienne sur la JVM.
 *
 * Le décodage, la rotation et la compression demandent un appareil ; le calcul qui
 * décide **combien** on réduit, non. C'est aussi là qu'une erreur coûte le plus cher :
 * un facteur trop grand envoie une image floue à un modèle qu'on paie, un facteur trop
 * petit décode un bitmap de quarante mégaoctets sur un téléphone.
 */
class JpegReductionTest {
    @Test
    fun `une image deja petite ne se reduit pas`() {
        assertEquals(1, sampleSizeFor(800, 600))
        assertEquals(800 to 600, scaledSizeFor(800, 600))
    }

    @Test
    fun `le cote long decide, quelle que soit l orientation`() {
        // Un portrait et un paysage de memes dimensions doivent se reduire pareil :
        // c'est le cote long qui coute des jetons.
        assertEquals(sampleSizeFor(4000, 3000), sampleSizeFor(3000, 4000))
        assertEquals(1024 to 768, scaledSizeFor(4000, 3000))
        assertEquals(768 to 1024, scaledSizeFor(3000, 4000))
    }

    @Test
    fun `l echantillonnage ne descend jamais sous la cible`() {
        // Le facteur retenu doit laisser de quoi mettre a l'echelle : descendre en
        // dessous des 1024 px des le decodage rendrait une image qu'il faudrait
        // ensuite agrandir, donc floue.
        listOf(1200, 2048, 3000, 4032, 8000).forEach { longSide ->
            val sample = sampleSizeFor(longSide, longSide / 2)
            assertTrue(longSide / sample >= LONG_SIDE_PX, "$longSide / $sample tombe sous la cible")
        }
    }

    @Test
    fun `l echantillonnage est une puissance de deux`() {
        // Le decodeur d'Android arrondit a la puissance de deux inferieure : un
        // chiffre qu'il corrige en silence est un chiffre qu'on ne controle pas.
        listOf(1024, 1500, 2048, 4032, 12000).forEach { longSide ->
            val sample = sampleSizeFor(longSide, longSide)
            assertEquals(0, sample and (sample - 1), "$sample n est pas une puissance de deux")
        }
    }

    @Test
    fun `les proportions sont gardees`() {
        val (width, height) = scaledSizeFor(4032, 3024)

        assertEquals(LONG_SIDE_PX, width)
        // 4032 / 3024 = 4/3, donc 1024 / 768.
        assertEquals(768, height)
    }

    @Test
    fun `un cote court minuscule ne devient jamais nul`() {
        // Un panorama degenere : 4000 x 1 se reduit d'un facteur 0,256, donc la
        // hauteur arrondie vaut zero -- et un bitmap de hauteur nulle jette. Il faut
        // descendre sous le demi-pixel pour que le cas existe : a trois pixels de
        // haut, l'arrondi rend encore 1 tout seul.
        val (_, height) = scaledSizeFor(4000, 1)

        assertTrue(height >= 1, "une hauteur nulle ferait echouer la mise a l echelle")
    }

    @Test
    fun `une image vide ne fait pas diviser par zero`() {
        assertEquals(1, sampleSizeFor(0, 0))
        assertEquals(0 to 0, scaledSizeFor(0, 0))
    }
}
