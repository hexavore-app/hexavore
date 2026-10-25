package app.hexavore.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeprecatedSystemBarsTest {
    @Test
    fun `signale une couleur de barre d etat posee par le code`() {
        val findings =
            DeprecatedSystemBars().lint(
                """
                fun barres(window: Window) {
                    window.statusBarColor = Color.TRANSPARENT
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `signale une couleur de barre de navigation par son setter`() {
        val findings =
            DeprecatedSystemBars().lint(
                """
                fun barres(window: Window) {
                    window.setNavigationBarColor(0)
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `signale le mode d encoche SHORT_EDGES`() {
        val findings =
            DeprecatedSystemBars().lint(
                """
                fun encoche(window: Window) {
                    window.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `signale enableEdgeToEdge une fois, sans compter son import`() {
        val findings =
            DeprecatedSystemBars().lint(
                """
                import androidx.activity.enableEdgeToEdge

                class Ecran : ComponentActivity() {
                    fun ouvrir() {
                        enableEdgeToEdge()
                    }
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `laisse passer les icones par WindowCompat et le mode ALWAYS`() {
        val findings =
            DeprecatedSystemBars().lint(
                """
                fun barres(window: Window, sombre: Boolean) {
                    WindowCompat.getInsetsController(window, window.decorView).run {
                        isAppearanceLightStatusBars = !sombre
                        isAppearanceLightNavigationBars = !sombre
                    }
                    val mode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }
}
