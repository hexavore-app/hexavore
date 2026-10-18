package app.hexavore.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Les barres du système se règlent dans le thème de fenêtre, et l'encoche ne s'ouvre
 * jamais en `SHORT_EDGES` ([D170][decisions]).
 *
 * `enableEdgeToEdge()` les réglait par le code, avec trois appels qu'Android 15 rend
 * obsolètes et que le Play Store relève. Le thème dit la même chose sans un appel ; ce
 * qui est éprouvé ici est qu'il le dise, version par version, et qu'aucune variante ne
 * vienne le défaire. La règle detekt `DeprecatedSystemBars` tient l'autre moitié : que le
 * code n'y revienne pas.
 *
 * Il lit le dépôt, comme `LauncherIconTest` : ce qui est éprouvé est ce qui est écrit.
 *
 * [decisions]: docs/11-decisions.md
 */
class SystemBarsThemeTest {
    @Test
    fun `le theme de fenetre pose les barres et l encoche`() {
        val theme = style("values/themes.xml", "Theme.Hexavore")

        assertEquals("@android:color/transparent", theme["android:statusBarColor"])
        assertEquals("@color/system_navigation_bar", theme["android:navigationBarColor"])
        assertEquals("@integer/system_cutout_mode", theme["android:windowLayoutInDisplayCutoutMode"])
    }

    @Test
    fun `le theme sombre ne change que le fond, jamais les barres`() {
        // Un `Theme.Hexavore` dans values-night remplacerait celui de values en entier, et
        // les barres avec lui, pour le seul mode sombre du systeme.
        assertFalse(styles("values-night/themes.xml").containsKey("Theme.Hexavore"))
    }

    @Test
    fun `la barre de navigation garde un voile sombre sous l API 29, puis devient transparente`() {
        assertEquals("#801B1B1B", resource("values", "color", "system_navigation_bar"))
        assertEquals("@android:color/transparent", resource("values-v29", "color", "system_navigation_bar"))
    }

    @Test
    fun `l encoche suit le mode par defaut, puis ALWAYS a partir de l API 30, jamais SHORT_EDGES`() {
        assertEquals(CUTOUT_DEFAULT, resource("values", "integer", "system_cutout_mode"))
        assertEquals(CUTOUT_ALWAYS, resource("values-v30", "integer", "system_cutout_mode"))

        File(RES).listFiles { dir -> dir.name.startsWith("values") }.orEmpty().forEach { dir ->
            val mode = resourceOrNull(dir.name, "integer", "system_cutout_mode")
            assertNotEquals(CUTOUT_SHORT_EDGES, mode, "${dir.name} ouvre l'encoche en SHORT_EDGES")
        }
    }
}

private const val RES = "src/main/res"

/** Les valeurs de `LAYOUT_IN_DISPLAY_CUTOUT_MODE_*`, telles que le thème les écrit. */
private const val CUTOUT_DEFAULT = "0"
private const val CUTOUT_SHORT_EDGES = "1"
private const val CUTOUT_ALWAYS = "3"

/** Les éléments d'un style, par nom d'attribut. */
private fun style(path: String, name: String): Map<String, String> =
    styles(path)[name] ?: error("$name est absent de $path")

private fun styles(path: String): Map<String, Map<String, String>> =
    elements(File("$RES/$path"), "style").associate { style -> style.getAttribute("name") to items(style) }

private fun items(style: Element): Map<String, String> =
    elements(style, "item").associate { it.getAttribute("name") to it.textContent.trim() }

private fun resource(folder: String, type: String, name: String): String =
    resourceOrNull(folder, type, name) ?: error("$type/$name est absent de $folder")

/** Une ressource simple, cherchée dans tous les fichiers d'un dossier `values*`. */
private fun resourceOrNull(folder: String, type: String, name: String): String? =
    File("$RES/$folder").listFiles { file -> file.extension == "xml" }.orEmpty()
        .flatMap { elements(it, type) }
        .firstOrNull { it.getAttribute("name") == name }
        ?.textContent
        ?.trim()

private fun elements(file: File, tag: String): List<Element> {
    check(file.exists()) { "${file.absolutePath} est introuvable : le repertoire courant des tests a change" }
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    return elements(document.documentElement, tag)
}

private fun elements(parent: Element, tag: String): List<Element> {
    val nodes = parent.getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as Element }
}
