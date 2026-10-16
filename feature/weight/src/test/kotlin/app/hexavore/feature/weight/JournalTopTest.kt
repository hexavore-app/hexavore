package app.hexavore.feature.weight

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * La tête de la liste des pesées ([D169][decisions]).
 *
 * **Ce qui se teste ici est la règle, pas la liste.** Le défilement d'une liste paresseuse
 * ne se compose pas dans ces tests ; la décision de remonter, elle, s'affirme — et c'est
 * là que vivait la faute : une pesée rendue par « Annuler » qui restait au-dessus du bord.
 *
 * [decisions]: docs/11-decisions.md
 */
class JournalTopTest {
    @Test
    fun `une pesee plus recente que la tete ramene la liste en haut quand on la lisait depuis le haut`() {
        // « Annuler » rend la pesee du 14 au-dessus de celle du 13, la premiere visible.
        assertTrue(keepsNewestInView(shownNewest = LE_13, newest = LE_14, atTop = true))
    }

    @Test
    fun `une liste qu on a fait defiler ne bouge pas`() {
        assertFalse(keepsNewestInView(shownNewest = LE_13, newest = LE_14, atTop = false))
    }

    @Test
    fun `une tete inchangee ne fait rien remonter`() {
        // Une pesee plus ancienne s'insere plus bas, une valeur corrigee garde sa date.
        assertFalse(keepsNewestInView(shownNewest = LE_14, newest = LE_14, atTop = true))
    }

    @Test
    fun `une premiere pesee ne demande rien`() {
        // La liste nait avec elle, deja en haut.
        assertFalse(keepsNewestInView(shownNewest = null, newest = LE_14, atTop = true))
    }

    private companion object {
        val LE_13: LocalDate = LocalDate.of(2026, 10, 13)
        val LE_14: LocalDate = LocalDate.of(2026, 10, 14)
    }
}
