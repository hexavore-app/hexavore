package app.hexavore.feature.weight

import app.hexavore.domain.usecase.TrendPoint
import app.hexavore.domain.usecase.WeightTrend
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Les deux bouts écrits sous la courbe.
 *
 * **Ce cas naît d'une panne, pas d'une précaution.** Ces libellés associaient la
 * première date au poids le plus bas de l'axe vertical et la dernière au plus haut :
 * deux plages indépendantes, collées par un format « date · poids » qui se lit comme
 * une mesure. Sur une trajectoire descendante — le cas dominant d'une application qui
 * calcule des objectifs de perte — ils annonçaient donc une prise de poids sous une
 * courbe qui descend, au-dessus d'une liste qui disait le contraire.
 *
 * Rien ne tombait, parce que rien ne regardait : `ChartScaleTest` éprouve l'échelle,
 * et l'échelle était juste. C'est l'appariement qui était faux.
 */
class AxisEndsTest {
    @Test
    fun `une descente se lit comme une descente`() {
        val (premier, dernier) = descente().axisEnds()!!

        assertTrue(
            dernier.weightKg < premier.weightKg,
            "la courbe descend de 82,5 a 79,2 kg : les deux bouts doivent le dire",
        )
    }

    @Test
    fun `chaque bout porte le poids de sa propre date`() {
        // L'invariant que l'ancien code violait : le poids ecrit a cote d'une date
        // doit etre celui de cette date, et non une borne de l'axe vertical.
        val courbe = descente()

        val (premier, dernier) = courbe.axisEnds()!!

        assertEquals(courbe.points.first(), premier)
        assertEquals(courbe.points.last(), dernier)
    }

    @Test
    fun `les deux bouts sont de vraies pesees`() {
        val courbe = descente()

        val (premier, dernier) = courbe.axisEnds()!!

        assertTrue(premier in courbe.points, "le bout gauche doit etre une mesure du journal")
        assertTrue(dernier in courbe.points, "le bout droit doit etre une mesure du journal")
    }

    @Test
    fun `sans pesee, il n y a pas de bouts a ecrire`() {
        assertNull(WeightTrend(points = emptyList()).axisEnds())
    }

    private fun descente() = WeightTrend(
        points = listOf(
            TrendPoint(LUNDI, weightKg = 82.5, averageKg = null),
            TrendPoint(LUNDI.plusDays(30), weightKg = 80.4, averageKg = 80.6),
            TrendPoint(LUNDI.plusDays(60), weightKg = 79.2, averageKg = 79.5),
        ),
    )

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 7, 13)
    }
}
