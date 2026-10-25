package app.hexavore.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

/** Un nombre s'écrit dans la langue de l'application, et se relit quel que soit le séparateur (D173). */
class DecimalTextTest {
    @Test
    fun `le separateur est celui de la langue`() {
        assertEquals("65,3", 65.3.asDecimalText(locale = Locale.FRENCH))
        assertEquals("65.3", 65.3.asDecimalText(locale = Locale.ENGLISH))
    }

    @Test
    fun `un entier s ecrit sans decimale`() {
        assertEquals("64", 64.0.asDecimalText(locale = Locale.FRENCH))
    }

    @Test
    fun `une decimale au plus, la moitie vers le haut`() {
        assertEquals("65,3", 65.25.asDecimalText(locale = Locale.FRENCH))
        assertEquals("65,2", 65.24.asDecimalText(locale = Locale.FRENCH))
    }

    @Test
    fun `sans separateur de milliers, pour qu un champ prerempli reste modifiable`() {
        assertEquals("1736", 1736.0.asDecimalText(locale = Locale.FRENCH))
        assertEquals("1736", 1736.0.asDecimalText(locale = Locale.ENGLISH))
    }

    @Test
    fun `une quantite garde ses trois decimales`() {
        assertEquals("0,25", 0.25.asDecimalText(fractionDigits = 3, locale = Locale.FRENCH))
        assertEquals("12,345", 12.345.asDecimalText(fractionDigits = 3, locale = Locale.FRENCH))
    }

    @Test
    fun `un champ se relit avec la virgule comme avec le point`() {
        assertEquals(12.5, "12,5".toDecimalOrNull())
        assertEquals(12.5, "12.5".toDecimalOrNull())
        assertEquals(64.0, "64,".toDecimalOrNull(), "une virgule en attente ne change pas le nombre")
        assertEquals(7.0, " 7 ".toDecimalOrNull())
        assertNull("".toDecimalOrNull(), "vide n'est pas zero")
        assertNull("a".toDecimalOrNull())
    }
}
