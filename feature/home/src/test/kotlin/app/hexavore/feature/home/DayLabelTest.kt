package app.hexavore.feature.home

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Ce que le titre dit du jour regardé, et ce qu'il fait pendant qu'on en change.
 *
 * **Deux réponses au même reproche** — « on ne sait pas trop où on se situe ». Le
 * libellé répond à *quel jour* sans qu'on ait à déchiffrer une date ; la progression
 * répond à *quelque chose est en train de se passer*, en emmenant le titre avec la
 * journée. L'une est un mot, l'autre un mouvement, et aucune ne remplace l'autre.
 */
class DayLabelTest {
    @Test
    fun `aujourd hui se dit par son nom, qu il soit porte ou sous-entendu`() {
        // `null` est la convention de l'ecran, mais le calendrier peut renvoyer la
        // date du jour : deux chemins qui se contrediraient seraient pires qu'une
        // redondance.
        assertEquals(DayLabel.Today, dayLabelOf(day = null, today = JOUR))
        assertEquals(DayLabel.Today, dayLabelOf(day = JOUR, today = JOUR))
    }

    @Test
    fun `les deux jours qui ont un nom le portent`() {
        assertEquals(DayLabel.Yesterday, dayLabelOf(JOUR.minusDays(1), JOUR))
        assertEquals(DayLabel.BeforeYesterday, dayLabelOf(JOUR.minusDays(2), JOUR))
    }

    @Test
    fun `au-dela, la date reprend la main`() {
        // Aucun mot ne s'impose pour le troisieme jour : « il y a trois jours » se lit
        // moins vite qu'une date, et se compte au lieu de se lire.
        val avant = JOUR.minusDays(3)

        assertEquals(DayLabel.On(avant), dayLabelOf(avant, JOUR))
    }

    @Test
    fun `hier traverse un changement de mois`() {
        // La regle porte sur des dates et non sur des numeros de jour : le 1er mars,
        // hier est le 28 fevrier, et rien dans le calcul ne le sait a l'avance.
        val premierMars = LocalDate.of(2026, 3, 1)

        assertEquals(DayLabel.Yesterday, dayLabelOf(LocalDate.of(2026, 2, 28), premierMars))
    }

    @Test
    fun `sans largeur connue, le titre ne bouge pas`() {
        // Le premier rendu, avant que la journee ait ete mesuree. Une division par
        // zero rendrait un infini, et le titre disparaitrait avant tout geste.
        assertEquals(0f, DaySwipeState().progress)
    }

    @Test
    fun `la progression est la part de la largeur parcourue`() = runTest {
        // C'est elle qui dit au titre de combien s'effacer, sans qu'il ait a connaitre
        // ni la largeur de l'ecran ni le sens du geste.
        val etat = DaySwipeState().apply { width = 400f }

        etat.offset.snapTo(-200f)
        assertEquals(0.5f, etat.progress)

        etat.offset.snapTo(100f)
        assertEquals(0.25f, etat.progress)
    }

    @Test
    fun `la progression ne depasse pas un`() = runTest {
        // Un geste insistant peut mener au-dela de la largeur -- rien ne borne le
        // deplacement, seule la resistance le divise. Une opacite negative ferait
        // disparaitre le titre pour de bon, et il ne reviendrait plus.
        val etat = DaySwipeState().apply { width = 100f }

        etat.offset.snapTo(-250f)
        assertEquals(1f, etat.progress)
    }

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 3, 15)
    }
}
