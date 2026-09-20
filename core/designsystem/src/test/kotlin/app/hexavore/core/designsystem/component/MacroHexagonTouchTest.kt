package app.hexavore.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import app.hexavore.domain.nutrition.Macro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce qu'un doigt désigne sur la figure, et où se pose ce qui la commente.
 *
 * **Toucher un triangle est un geste que rien n'annonce**, et c'est pour cela qu'il a
 * droit à une cible généreuse : les six secteurs se partagent tout le disque, lettres
 * comprises. Ce que ces cas défendent est qu'un appui désigne toujours la macro qu'on
 * croit viser, et que la bulle ne recouvre jamais ce qu'elle explique.
 */
class MacroHexagonTouchTest {
    @Test
    fun `le haut designe les calories, le bas les glucides`() {
        // L'ordre de toute l'application : calories en haut, puis sens horaire. Il
        // sert de second canal la ou un libelle ne tient pas, et ne renseigne que
        // s'il est le meme partout.
        assertEquals(Macro.CALORIES, macroAt(Offset(100f, 40f), CENTRE, REACH))
        assertEquals(Macro.CARBS, macroAt(Offset(100f, 160f), CENTRE, REACH))
    }

    @Test
    fun `les quatre obliques se distinguent`() {
        // Protéines en haut a droite, fibres en bas a droite, lipides en haut a
        // gauche, sucres en bas a gauche.
        assertEquals(Macro.PROTEIN, macroAt(pointAt(CENTRE, 40f, 30f), CENTRE, REACH))
        assertEquals(Macro.FIBER, macroAt(pointAt(CENTRE, 40f, 330f), CENTRE, REACH))
        assertEquals(Macro.FAT, macroAt(pointAt(CENTRE, 40f, 150f), CENTRE, REACH))
        assertEquals(Macro.SUGARS, macroAt(pointAt(CENTRE, 40f, 210f), CENTRE, REACH))
    }

    @Test
    fun `la frontiere entre deux quartiers ne laisse pas de trou`() {
        // Trente degres separent deux axes de leur frontiere commune : de part et
        // d'autre, chacun des deux voisins, et jamais rien.
        assertEquals(Macro.CALORIES, macroAt(pointAt(CENTRE, 40f, 61f), CENTRE, REACH))
        assertEquals(Macro.PROTEIN, macroAt(pointAt(CENTRE, 40f, 59f), CENTRE, REACH))
    }

    @Test
    fun `au-dela de la portee, l appui ne designe personne`() {
        // C'est ce qui permet a un appui a cote de fermer : sans cette borne, un
        // ecran entier se partagerait en six secteurs et plus rien ne serait « hors ».
        assertNull(macroAt(pointAt(CENTRE, REACH + 1f, 90f), CENTRE, REACH))
    }

    @Test
    fun `la lettre appartient a sa macro`() {
        // Les lettres sont posees hors du contour : viser un « F » est la facon la
        // plus naturelle de designer les fibres, et le triangle exact la refuserait.
        val lettre = pointAt(CENTRE, REACH * 0.95f, 330f)

        assertEquals(Macro.FIBER, macroAt(lettre, CENTRE, REACH))
    }

    @Test
    fun `un appui au centre designe quelqu un`() {
        // Les six pointes s'y touchent : l'angle y est instable, mais repondre rien
        // du tout se lirait comme un appui rate.
        assertTrue(macroAt(CENTRE, CENTRE, REACH) != null)
    }

    @Test
    fun `le point vise est dans le quartier, du bon cote du centre`() {
        // Ce que la pointe de la bulle designe. Dans la figure -- une pointe qui
        // aboutirait hors du contour semblerait montrer la lettre -- et du cote de sa
        // macro, sans quoi la bulle se poserait systematiquement du mauvais cote.
        val figure = Rect(0f, 0f, 200f, 173f)

        assertTrue(figure.macroAnchor(Macro.CALORIES).y < figure.center.y, "les calories sont en haut")
        assertTrue(figure.macroAnchor(Macro.CARBS).y > figure.center.y, "les glucides sont en bas")
        assertTrue(figure.contains(figure.macroAnchor(Macro.PROTEIN)), "le point vise sort de la figure")
    }

    @Test
    fun `la bulle se centre sur le quartier quand elle a la place`() {
        // Le cas ordinaire, celui qu'on voit neuf fois sur dix : rien ne bute, et la
        // bulle est posee sous le quartier et non a cote.
        val spot = bubbleSpot(Offset(CENTRE.x, 20f), CENTRE, BULLE, ZONE, MARGE, POINTE)

        assertEquals((CENTRE.x - BULLE.width / 2f).toInt(), spot.offset.x)
    }

    @Test
    fun `un quartier du haut renvoie la bulle en dessous`() {
        // La regle qui porte tout le reste : la bulle ne recouvre jamais le quartier
        // qu'elle explique, sinon la surbrillance disparait au moment ou on la demande.
        val spot = bubbleSpot(
            anchor = pointAt(CENTRE, 80f, 90f),
            centre = CENTRE,
            bubble = BULLE,
            container = ZONE,
            margin = MARGE,
            tailInset = POINTE,
        )

        assertTrue(spot.tailOnTop, "la pointe doit etre en haut de la bulle")
        assertTrue(spot.offset.y >= CENTRE.y.toInt(), "la bulle doit commencer sous le centre")
    }

    @Test
    fun `un quartier du bas renvoie la bulle au-dessus`() {
        val spot = bubbleSpot(
            anchor = pointAt(CENTRE, 80f, 270f),
            centre = CENTRE,
            bubble = BULLE,
            container = ZONE,
            margin = MARGE,
            tailInset = POINTE,
        )

        assertTrue(!spot.tailOnTop, "la pointe doit etre en bas de la bulle")
        assertTrue(spot.offset.y + BULLE.height <= CENTRE.y.toInt(), "la bulle doit finir au-dessus du centre")
    }

    @Test
    fun `la bulle ne sort pas du conteneur`() {
        // Un quartier de bord tire la bulle vers l'exterieur : c'est le corps qui
        // s'arrete, jamais l'ecran qui s'agrandit.
        val spot = bubbleSpot(
            anchor = Offset(ZONE.width - 2f, 20f),
            centre = CENTRE,
            bubble = BULLE,
            container = ZONE,
            margin = MARGE,
            tailInset = POINTE,
        )

        assertTrue(spot.offset.x >= 0, "la bulle sort a gauche")
        assertTrue(spot.offset.x + BULLE.width <= ZONE.width, "la bulle sort a droite")
    }

    @Test
    fun `la pointe continue de designer quand le corps bute`() {
        // Les deux se desolidarisent, et c'est le but : une bulle recentree sur sa
        // pointe sortirait de l'ecran, une pointe recentree sur la bulle designerait
        // le mauvais quartier. L'ancrage est choisi la ou le corps bute et ou la
        // pointe, elle, a encore de la course -- sinon le cas n'eprouve rien.
        val vers = Offset(170f, 20f)
        val spot = bubbleSpot(vers, CENTRE, BULLE, ZONE, MARGE, POINTE)

        assertEquals(ZONE.width - BULLE.width, spot.offset.x, "le corps devait buter sur le bord droit")
        assertEquals((vers.x - spot.offset.x).toInt(), spot.tailX)
        assertTrue(spot.tailX != BULLE.width / 2, "une pointe restee au milieu ne designe plus rien")
    }

    @Test
    fun `la pointe reste dans la bulle, loin des angles`() {
        // Une pointe posee sur un angle arrondi ne tient a rien, et une pointe hors
        // de la bulle flotte toute seule.
        val spot = bubbleSpot(Offset(0f, 20f), CENTRE, BULLE, ZONE, MARGE, POINTE)

        assertTrue(spot.tailX >= POINTE, "la pointe touche l angle gauche")
        assertTrue(spot.tailX <= BULLE.width - POINTE, "la pointe touche l angle droit")
    }

    @Test
    fun `une bulle plus grande que la zone reste posee a l origine`() {
        // Cas limite d'un tres gros caractere : mieux vaut une bulle rognee en bas
        // qu'une bulle posee a une coordonnee negative, invisible en entier.
        val enorme = IntSize(ZONE.width + 100, ZONE.height + 100)
        val spot = bubbleSpot(pointAt(CENTRE, 80f, 90f), CENTRE, enorme, ZONE, MARGE, POINTE)

        assertEquals(0, spot.offset.x)
        assertEquals(0, spot.offset.y)
    }

    private companion object {
        val CENTRE = Offset(100f, 100f)
        const val REACH = 90f

        val ZONE = IntSize(200, 200)
        val BULLE = IntSize(120, 60)
        const val MARGE = 8
        const val POINTE = 16
    }
}
