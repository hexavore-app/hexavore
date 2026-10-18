package app.hexavore.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Un champ d'entiers refuse la virgule, et le dit tant que le chiffre qui suit peut en
 * être la suite (D172).
 */
class WholeNumberFieldTest {
    @Test
    fun `le champ d entiers refuse la virgule comme le point`() {
        assertFalse("8,5".isWholeNumberField())
        assertFalse("8.".isWholeNumberField())
        assertTrue("85".isWholeNumberField())
    }

    @Test
    fun `seul un separateur decimal refuse merite la phrase`() {
        assertTrue("8,".hasDecimalSeparator())
        assertTrue("8.".hasDecimalSeparator())
        assertFalse("8a".hasDecimalSeparator(), "une lettre n'a rien a voir avec une decimale")
        assertFalse("8\n".hasDecimalSeparator())
    }

    @Test
    fun `le refus tient tant que le texte ne revient pas avant lui`() {
        // « 8 », puis la virgule refusee : « 85 » est le chiffre qu'il faut voir.
        assertEquals("8", "8".keptAfter("85"))
        assertEquals("8", "8".keptAfter("8"), "le temps de corriger")
        assertEquals("12", "12".keptAfter("125"))
    }

    @Test
    fun `le refus tombe quand le texte repasse avant lui`() {
        assertNull("8".keptAfter(""))
        assertNull("12".keptAfter("1"))
        assertNull(null.keptAfter("85"), "sans refus, un 85 tape est un 85 voulu")
    }
}
