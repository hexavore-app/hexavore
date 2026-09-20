package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryAppearanceSettings
import app.hexavore.domain.appearance.DishDisplayStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Le style d'affichage des plats, lu par une seule porte.
 *
 * **Une question, un endroit.** L'accueil la pose pour dessiner ses plats, les réglages
 * pour cocher la bonne case. Chacun lisant le magasin pour son compte, chacun aurait
 * décidé ce que vaut un fichier illisible — et l'un des deux aurait fini par afficher
 * une liste dans un style que la case ne montre pas.
 */
class ObserveDishStyleTest {
    @Test
    fun `sans rien de choisi, le simplifie`() = runTest {
        assertEquals(DishDisplayStyle.SIMPLE, ObserveDishStyle(InMemoryAppearanceSettings())().first())
    }

    @Test
    fun `le magasin dit lequel`() = runTest {
        val magasin = InMemoryAppearanceSettings(initialStyle = DishDisplayStyle.DETAILED)

        assertEquals(DishDisplayStyle.DETAILED, ObserveDishStyle(magasin)().first())
    }

    @Test
    fun `un magasin illisible rend le simplifie`() = runTest {
        // Le detaille serait le pire des deux replis : plus long a lire, et un reglage
        // qu'on croit avoir mis se retrouverait ignore sans un mot.
        val abime = InMemoryAppearanceSettings(initialStyle = DishDisplayStyle.DETAILED, failure = true)

        assertEquals(DishDisplayStyle.SIMPLE, ObserveDishStyle(abime)().first())
    }
}
