package app.hexavore.feature.home

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * La semaine que le bandeau montre, et les jours qu'il n'ouvre pas.
 *
 * **Une semaine calendaire, pas sept jours glissants.** Le bandeau plaçait
 * aujourd'hui toujours à droite, ce qui se lisait « il y a six jours, il y a cinq
 * jours… » et obligeait à compter pour retrouver hier. Une semaine se lit d'un coup
 * d'œil, et sa géométrie ne bouge pas d'un jour à l'autre.
 */
class CalendarUiStateTest {
    @Test
    fun `la semaine commence au premier jour donne`() {
        // **Deux locales et non une**, et c'est ce qui fait le test : sur une machine
        // francaise, comparer au premier jour de la locale laissait passer un « lundi »
        // ecrit en dur. La campagne de defaite l'a montre.
        assertEquals(DayOfWeek.MONDAY, semaine(DayOfWeek.MONDAY).first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, semaine(DayOfWeek.SUNDAY).first().dayOfWeek)
    }

    @Test
    fun `aujourd hui reste dans la semaine, quel que soit le premier jour`() {
        assertTrue(MERCREDI in semaine(DayOfWeek.MONDAY))
        assertTrue(MERCREDI in semaine(DayOfWeek.SUNDAY))
        assertTrue(MERCREDI in semaine(DayOfWeek.SATURDAY))
    }

    @Test
    fun `la semaine d un premier jour dominical commence la veille du lundi`() {
        // Un mercredi, avec la semaine commencant dimanche : elle part du dimanche
        // precedent, pas du lundi.
        assertEquals(MERCREDI.minusDays(3), semaine(DayOfWeek.SUNDAY).first())
    }

    @Test
    fun `la semaine porte sept jours consecutifs`() {
        val semaine = semaine(DayOfWeek.MONDAY)

        assertEquals(7, semaine.size)
        assertEquals(semaine.first().plusDays(6), semaine.last())
    }

    @Test
    fun `aujourd hui est dans la semaine, et pas forcement a la fin`() {
        // C'est tout le changement : un mercredi, aujourd'hui est au milieu.
        val semaine = semaine(DayOfWeek.MONDAY)

        assertTrue(MERCREDI in semaine)
        assertTrue(semaine.last().isAfter(MERCREDI), "la semaine continue apres aujourd'hui")
    }

    @Test
    fun `la semaine ne bouge pas d un jour a l autre`() {
        // La geometrie du bandeau est stable : sept cases, aux memes dates, tant qu'on
        // reste dans la meme semaine. Avec sept jours glissants, tout se decalait
        // chaque matin.
        val lundi = CalendarUiState(today = MERCREDI.with(DayOfWeek.MONDAY), firstDayOfWeek = DayOfWeek.MONDAY).week
        val jeudi = CalendarUiState(today = MERCREDI.with(DayOfWeek.THURSDAY), firstDayOfWeek = DayOfWeek.MONDAY).week

        assertEquals(lundi, jeudi)
    }

    @Test
    fun `un jour a venir est reconnu comme tel`() {
        val etat = CalendarUiState(today = MERCREDI)

        assertTrue(etat.isFuture(MERCREDI.plusDays(1)))
        assertFalse(etat.isFuture(MERCREDI), "aujourd'hui n'est pas a venir")
        assertFalse(etat.isFuture(MERCREDI.minusDays(1)))
    }

    // --- La taille des pastilles ------------------------------------------------

    @Test
    fun `sept cellules font exactement la largeur`() {
        // **Le defaut rapporte a l'usage** : la largeur demandee depassait la place, le
        // parent la rognait sans toucher a la hauteur, et les pastilles sortaient ovales.
        //
        // **Ce cas comptait faux, exactement comme le code.** Il reservait huit
        // intervalles la ou `DayCell` en depensait deux par cellule, soit quatorze : il
        // passait donc pendant que les pastilles etaient ecrasees a l'ecran. Un cas qui
        // reprend l'arithmetique de ce qu'il verifie ne verifie rien.
        //
        // L'egalite, et non l'inegalite : trop petit gaspille la largeur, trop grand la
        // depasse, et les deux se voient.
        LARGEURS.forEach { largeur ->
            assertEquals(largeur.value, (cellFootprint(largeur) * JOURS).value, EPSILON, "sur $largeur")
        }
    }

    @Test
    fun `l anneau tient dans la place de sa cellule`() {
        // La regle qui remplace le compte partage : `DayCell` ne calcule plus rien, elle
        // prend sa place et loge l'anneau dedans. Le desaccord des deux comptes est
        // devenu impossible parce qu'il n'y a plus qu'un compte.
        LARGEURS.forEach { largeur ->
            val place = cellFootprint(largeur)

            assertTrue(ringDiameter(place) <= place, "sur $largeur, un anneau de ${ringDiameter(place)} dans $place")
        }
    }

    @Test
    fun `l anneau laisse ses deux marges`() {
        val place = cellFootprint(360.dp)

        assertEquals(place - CellPadding * 2, ringDiameter(place))
    }

    @Test
    fun `la pastille grandit avec la largeur, jusqu a une borne`() {
        assertTrue(
            ringDiameter(cellFootprint(360.dp)) > ringDiameter(cellFootprint(320.dp)),
            "un ecran plus large donne des pastilles plus grandes",
        )
        assertEquals(
            MaxCellDiameter,
            ringDiameter(cellFootprint(1200.dp)),
            "une tablette ne montre pas sept medaillons",
        )
    }

    @Test
    fun `une cellule de tablette ne prend pas la hauteur de l ecran`() {
        // L'anneau etait borne, la cellule et son disque non : couche, un telephone
        // montrait une semaine de 130 dp de haut, et il ne restait au journal qu'une
        // bande de quelques pixels (D163).
        val place = cellFootprint(914.dp)

        assertEquals(MaxCellHeight, cellHeight(place))
        assertTrue(
            cellHeight(place) >= ringDiameter(place) + CellPadding * 2,
            "le disque doit encore entourer l'anneau",
        )
    }

    @Test
    fun `une cellule de telephone reste carree`() {
        // La borne ne touche pas le telephone tenu debout : sa cellule, plus etroite
        // que la borne, garde la hauteur de sa largeur.
        listOf(320.dp, 360.dp, 411.dp).forEach { largeur ->
            val place = cellFootprint(largeur)

            assertEquals(place, cellHeight(place), "sur $largeur")
        }
    }

    @Test
    fun `la pastille ne descend jamais sous sa taille minimale`() {
        // En deca, le chiffre du jour cesse d'etre lisible : mieux vaut une pastille qui
        // touche ses voisines que sept points illisibles.
        assertEquals(MinCellDiameter, ringDiameter(cellFootprint(240.dp)))
    }

    private fun semaine(first: DayOfWeek) = CalendarUiState(today = MERCREDI, firstDayOfWeek = first).week

    private companion object {
        /** Un mercredi : au milieu de sa semaine, donc des jours avant et après. */
        val MERCREDI: LocalDate = LocalDate.of(2026, 8, 19)

        /** Du plus étroit qui existe encore à la tablette, en passant par les téléphones courants. */
        val LARGEURS = listOf(120.dp, 240.dp, 280.dp, 320.dp, 360.dp, 412.dp, 600.dp)

        const val JOURS = 7

        /**
         * Un cheveu de tolérance : `Dp` porte un flottant, et `120 / 7 × 7` ne retombe
         * pas forcément sur `120` au bit près. Ce que le cas garde, c'est qu'aucune
         * marge oubliée ne s'ajoute — pas la précision de l'IEEE 754.
         */
        const val EPSILON = 0.01f
    }
}
