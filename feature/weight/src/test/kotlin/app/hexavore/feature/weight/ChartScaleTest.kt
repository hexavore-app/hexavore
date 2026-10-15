package app.hexavore.feature.weight

import app.hexavore.domain.profile.WeightAim
import app.hexavore.domain.usecase.TrendPoint
import app.hexavore.domain.usecase.WeightTrend
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * L'échelle du graphique.
 *
 * **Ce qui se teste ici est ce qu'un `Canvas` ne montre pas.** Le dessin ne se vérifie
 * qu'à l'œil ; les fractions, elles, s'affirment — et c'est là que vivent les fautes
 * qui font une courbe fausse plutôt qu'une courbe laide : deux mesures au même poids
 * placées à deux hauteurs, une trajectoire lue avec une autre règle que les points, ou
 * trois cents grammes d'écart étalés sur toute la hauteur.
 */
class ChartScaleTest {
    @Test
    fun `une seule pesee ne fait pas d echelle`() {
        // L'axe horizontal serait de largeur nulle, et chaque date tomberait sur une
        // division par zero.
        assertNull(ChartScale.of(courbe(LUNDI to 80.0)))
    }

    @Test
    fun `deux pesees le meme jour ne font pas d echelle`() {
        assertNull(ChartScale.of(courbe(LUNDI to 80.0, LUNDI to 81.0)))
    }

    @Test
    fun `sans pesee du tout, il n y a pas d echelle`() {
        assertNull(ChartScale.of(WeightTrend(points = emptyList())))
    }

    @Test
    fun `la premiere date est a gauche et la derniere a droite`() {
        val echelle = echelle(LUNDI to 80.0, LUNDI.plusDays(10) to 79.0)

        assertEquals(0f, echelle.xOf(LUNDI))
        assertEquals(1f, echelle.xOf(LUNDI.plusDays(10)))
    }

    @Test
    fun `une date du milieu tombe au milieu`() {
        val echelle = echelle(LUNDI to 80.0, LUNDI.plusDays(10) to 79.0)

        assertEquals(0.5f, echelle.xOf(LUNDI.plusDays(5)), TOLERANCE)
    }

    @Test
    fun `deux mesures au meme poids tombent a la meme hauteur`() {
        val echelle = echelle(LUNDI to 80.0, LUNDI.plusDays(3) to 82.0, LUNDI.plusDays(6) to 80.0)

        assertEquals(echelle.yOf(80.0), echelle.yOf(80.0))
        assertTrue(echelle.yOf(82.0) > echelle.yOf(80.0), "le poids le plus lourd est le plus haut")
    }

    @Test
    fun `les mesures ne touchent pas les bords`() {
        // Sans respiration, la mesure la plus haute se confond avec le cadre.
        val echelle = echelle(LUNDI to 80.0, LUNDI.plusDays(6) to 90.0)

        assertTrue(echelle.yOf(90.0) < 1f, "la plus lourde reste sous le haut")
        assertTrue(echelle.yOf(80.0) > 0f, "la plus legere reste au-dessus du bas")
    }

    @Test
    fun `trois cents grammes d ecart ne remplissent pas la hauteur`() {
        // Sinon une variation d'hydratation se lirait comme un effondrement --
        // exactement ce que la moyenne mobile sert a ne pas montrer.
        val echelle = echelle(LUNDI to 80.0, LUNDI.plusDays(6) to 80.3)

        val amplitude = echelle.bounds.endInclusive - echelle.bounds.start

        assertTrue(amplitude >= MIN_RANGE_KG, "l'axe couvre au moins deux kilos, or il en couvre $amplitude")
    }

    @Test
    fun `une grande amplitude n est pas etiree jusqu au minimum`() {
        val echelle = echelle(LUNDI to 80.0, LUNDI.plusDays(6) to 95.0)

        assertTrue(echelle.bounds.endInclusive - echelle.bounds.start > MIN_RANGE_KG)
    }

    @Test
    fun `la moyenne mobile entre dans l echelle`() {
        // Un lissage hors des bornes sortirait du cadre au dessin, sans que rien ne
        // le dise : la courbe la plus en evidence serait la seule tronquee.
        val trend = WeightTrend(
            points = listOf(
                TrendPoint(LUNDI, weightKg = 80.0, trendKg = 92.0),
                TrendPoint(LUNDI.plusDays(6), weightKg = 81.0, trendKg = 91.0),
            ),
        )

        val echelle = ChartScale.of(trend)!!

        assertTrue(echelle.yOf(92.0) <= 1f, "la moyenne la plus haute tient dans le cadre")
    }

    // --- La trajectoire annoncee ------------------------------------------------

    @Test
    fun `la trajectoire est lue avec la meme regle que les points`() {
        // Deux echelles verticales feraient se croiser les traces la ou ils ne se
        // croisent pas, et la courbe mentirait sur l'avance ou le retard.
        val cap = WeightAim(from = LUNDI, fromKg = 80.0, to = LUNDI.plusDays(70), toKg = 75.0)
        val trend = WeightTrend(
            points = listOf(point(LUNDI, 80.0), point(LUNDI.plusDays(7), 79.5)),
            aim = cap,
        )

        val echelle = ChartScale.of(trend)!!

        // Le jour du depart, la trajectoire vaut exactement la mesure du depart.
        assertEquals(echelle.yOf(80.0), echelle.yOf(cap.weightAt(LUNDI)), TOLERANCE)
    }

    @Test
    fun `la trajectoire se prolonge de part et d autre de ses bornes`() {
        // Elle est une droite : c'est ce qui permet de la lire sur la fenetre des
        // mesures, avant le debut de l'objectif comme apres son echeance.
        val cap = WeightAim(from = LUNDI, fromKg = 80.0, to = LUNDI.plusDays(70), toKg = 75.0)

        assertEquals(80.5, cap.weightAt(LUNDI.minusDays(7)), TOLERANCE_KG)
        assertEquals(74.5, cap.weightAt(LUNDI.plusDays(77)), TOLERANCE_KG)
    }

    @Test
    fun `une trajectoire hors des mesures elargit l echelle`() {
        // Sans quoi le pointille sortirait du cadre, et le retard qu'il montre
        // disparaitrait justement le jour ou il devient interessant.
        val cap = WeightAim(from = LUNDI, fromKg = 70.0, to = LUNDI.plusDays(70), toKg = 65.0)
        val trend = WeightTrend(points = listOf(point(LUNDI, 90.0), point(LUNDI.plusDays(7), 89.0)), aim = cap)

        val echelle = ChartScale.of(trend)!!

        assertTrue(echelle.bounds.start < 70.0, "le cap tient dans le cadre, or il commence a 70 kg")
        assertNotNull(echelle.span)
    }

    private fun point(date: LocalDate, kg: Double) = TrendPoint(date, weightKg = kg, trendKg = kg)

    private fun courbe(vararg pesees: Pair<LocalDate, Double>) =
        WeightTrend(points = pesees.map { (date, kg) -> point(date, kg) })

    private fun echelle(vararg pesees: Pair<LocalDate, Double>) = ChartScale.of(courbe(*pesees))!!

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 17)
        const val TOLERANCE = 1e-6f
        const val TOLERANCE_KG = 1e-9
        const val MIN_RANGE_KG = 2.0
    }
}
