package app.hexavore.feature.home

import app.hexavore.feature.home.tour.TourStep
import app.hexavore.feature.home.tour.hidesKeyWarning
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Quand l'alerte rouge de la barre se tait pendant le tour.
 *
 * **Le tour présente le champ et l'appareil photo** : les montrer en rouge au même
 * moment accusait une clé manquante à quelqu'un qui découvre l'application, et
 * contredisait la bulle posée au-dessus (D158). La dernière étape sans clé est
 * l'exception, et c'est elle qui compte le plus : elle dit que ces deux gestes
 * « restent en rouge », et le rouge doit être là quand elle le dit.
 */
class TourWarningTest {
    @Test
    fun `les etapes qui presentent les gestes taisent le rouge`() {
        assertTrue(TourStep.DESCRIBE.hidesKeyWarning())
        assertTrue(TourStep.PHOTO.hidesKeyWarning())
        assertTrue(TourStep.AI.hidesKeyWarning())
    }

    @Test
    fun `la derniere etape sans cle montre le rouge qu elle explique`() {
        assertFalse(TourStep.DEGRADED.hidesKeyWarning())
    }

    @Test
    fun `hors du tour, rien ne change`() {
        assertFalse(null.hidesKeyWarning())
    }
}
