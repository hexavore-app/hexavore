package app.hexavore.domain.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.floor

/**
 * Ce qu'un corps peut mesurer (D160).
 *
 * Chaque borne s'éprouve **de part et d'autre** de son seuil, comme les garde-fous de
 * l'objectif : éprouvée d'un seul côté, une borne peut être posée n'importe où — trop
 * loin, elle laisse passer 999 kg ; trop près, elle refuse quelqu'un de réel — et rien ne
 * le dirait.
 */
class BodyBoundsTest {
    // --- L'age -------------------------------------------------------------------

    @Test
    fun `seize ans se comptent au jour pres`() {
        // La grille des annees laissait choisir, en decembre de l'annee la plus
        // recente, quelqu'un qui n'avait pas l'age plancher.
        val seizeAnsAujourdHui = AUJOURD_HUI.minusYears(16)

        assertNull(implausibleMeasure(seizeAnsAujourdHui, null, null, AUJOURD_HUI))
        assertEquals(
            BodyMeasure.BIRTH_DATE,
            implausibleMeasure(seizeAnsAujourdHui.plusDays(1), null, null, AUJOURD_HUI),
            "seize ans demain, c'est quinze aujourd'hui",
        )
    }

    @Test
    fun `cent ans passent, cent un non`() {
        val centUnAnsAujourdHui = AUJOURD_HUI.minusYears(101)

        assertNull(implausibleMeasure(centUnAnsAujourdHui.plusDays(1), null, null, AUJOURD_HUI))
        assertEquals(BodyMeasure.BIRTH_DATE, implausibleMeasure(centUnAnsAujourdHui, null, null, AUJOURD_HUI))
    }

    @Test
    fun `les naissances acceptees sont celles des ages acceptes`() {
        val naissances = BodyBounds.birthDatesOn(AUJOURD_HUI)

        assertEquals(LocalDate.of(2010, 8, 10), naissances.endInclusive)
        assertEquals(LocalDate.of(1925, 8, 11), naissances.start)
    }

    // --- La taille et le poids ---------------------------------------------------

    @Test
    fun `les trois fautes du signalement sont refusees`() {
        // 1 cm, 120 ans, 999 kg : ce que la saisie laissait passer.
        assertEquals(BodyMeasure.HEIGHT, implausibleMeasure(NAISSANCE, 1.0, 70.0, AUJOURD_HUI))
        assertEquals(BodyMeasure.BIRTH_DATE, implausibleMeasure(AUJOURD_HUI.minusYears(120), 175.0, 70.0, AUJOURD_HUI))
        assertEquals(BodyMeasure.WEIGHT, implausibleMeasure(NAISSANCE, 175.0, 999.0, AUJOURD_HUI))
    }

    @Test
    fun `la taille va de 1 m a 2,50 m`() {
        assertNull(implausibleMeasure(null, 100.0, null, AUJOURD_HUI))
        assertNull(implausibleMeasure(null, 250.0, null, AUJOURD_HUI))
        assertEquals(BodyMeasure.HEIGHT, implausibleMeasure(null, 99.0, null, AUJOURD_HUI))
        assertEquals(BodyMeasure.HEIGHT, implausibleMeasure(null, 251.0, null, AUJOURD_HUI))
    }

    @Test
    fun `deux mesures plausibles peuvent former un couple qui ne l est pas`() {
        // 100 cm et 300 kg passent chacun seuls ; leur IMC de 300, non. C'est pour ce
        // cas que le poids se juge sur la taille.
        assertTrue(100.0 in BodyBounds.HEIGHTS_CM && 300.0 in BodyBounds.WEIGHTS_KG)

        assertEquals(BodyMeasure.WEIGHT, implausibleMeasure(NAISSANCE, 100.0, 300.0, AUJOURD_HUI))
    }

    @Test
    fun `a 175 cm, un poids plausible va de 37 a 245 kg`() {
        // IMC de 12 : 36,75 kg, arrondis vers l'interieur. IMC de 80 : 245 kg tout rond.
        val plage = BodyBounds.weightsFor(175.0)

        assertEquals(37.0, plage.start)
        assertEquals(245.0, plage.endInclusive)
        assertNull(implausibleMeasure(null, 175.0, 37.0, AUJOURD_HUI))
        assertEquals(BodyMeasure.WEIGHT, implausibleMeasure(null, 175.0, 36.9, AUJOURD_HUI))
    }

    @Test
    fun `les bornes de poids sont des kilos entiers`() {
        // Ce que l'ecran annonce est exactement ce que la regle accepte : un « 37 kg »
        // qui laisserait passer 36,8 serait une regle que personne ne lit.
        listOf(152.0, 163.5, 170.0, 182.0, 199.0, 213.0).forEach { taille ->
            val plage = BodyBounds.weightsFor(taille)

            assertEquals(floor(plage.start), plage.start, "borne basse a $taille cm")
            assertEquals(floor(plage.endInclusive), plage.endInclusive, "borne haute a $taille cm")
        }
    }

    @Test
    fun `les petites tailles retombent sur le poids de tout adulte`() {
        // 120 cm : un IMC de 12 donnerait 18 kg, qu'aucun adulte ne pese.
        assertEquals(25.0, BodyBounds.weightsFor(120.0).start)
    }

    @Test
    fun `sans taille plausible, le poids se juge sur les bornes de tout adulte`() {
        // La taille fausse se signale d'abord ; le poids ne se juge pas sur elle.
        assertEquals(BodyBounds.WEIGHTS_KG, BodyBounds.weightsFor(null))
        assertEquals(BodyBounds.WEIGHTS_KG, BodyBounds.weightsFor(1.0))
    }

    @Test
    fun `quand tout est faux, la taille se dit d abord`() {
        // 1 cm et 999 kg, le signalement lui-meme : les deux sont faux, et c'est la
        // taille qu'il faut corriger d'abord, puisque le poids se jugera sur elle. Un
        // poids plausible a cote ne distinguerait pas l'ordre -- seul ce couple le fait.
        assertEquals(BodyMeasure.HEIGHT, implausibleMeasure(null, 1.0, 999.0, AUJOURD_HUI))
    }

    @Test
    fun `une mesure absente n est pas invraisemblable`() {
        // Elle manque, et l'ecran le demande deja : la dire fausse serait un second
        // message pour une seule absence.
        assertNull(implausibleMeasure(null, null, null, AUJOURD_HUI))
    }

    // --- Le seuil de la maigreur ------------------------------------------------

    @Test
    fun `le seuil de la maigreur se prend au kilo superieur`() {
        // 18,5 x 1,75 x 1,75 = 56,66 kg. Arrondi en dessous, la limite proposee serait
        // deja un poids de maigreur.
        assertEquals(57.0, BodyBounds.leanWeightFor(175.0))
        assertEquals(62.0, BodyBounds.leanWeightFor(182.0))
    }

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)
        val NAISSANCE: LocalDate = LocalDate.of(1991, 3, 4)
    }
}
