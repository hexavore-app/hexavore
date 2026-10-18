package app.hexavore.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/** Un champ de mesure reprend ce qui avait été tapé, tant que cela dit la valeur gardée (D174). */
class BodyFieldTextTest {
    @Test
    fun `une virgule en attente revient telle quelle`() {
        assertEquals("64,", fieldText(typed = "64,", value = 64.0, locale = Locale.FRENCH))
        assertEquals("65,3", fieldText(typed = "65,3", value = 65.3, locale = Locale.FRENCH))
    }

    @Test
    fun `la valeur gardee l emporte sur un texte qui dit autre chose`() {
        assertEquals("64", fieldText(typed = "70", value = 64.0, locale = Locale.FRENCH))
    }

    @Test
    fun `sans texte garde, la valeur s ecrit dans la langue`() {
        assertEquals("65,3", fieldText(typed = null, value = 65.3, locale = Locale.FRENCH))
        assertEquals("65.3", fieldText(typed = null, value = 65.3, locale = Locale.ENGLISH))
    }

    @Test
    fun `un champ vide reste vide`() {
        assertEquals("", fieldText(typed = null, value = null, locale = Locale.FRENCH))
        assertEquals("", fieldText(typed = "", value = null, locale = Locale.FRENCH))
    }
}
