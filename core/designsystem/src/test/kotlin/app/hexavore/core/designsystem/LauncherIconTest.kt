package app.hexavore.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.hypot

/**
 * L'icône de lancement dit la même chose que le thème, et tient où le système la montre.
 *
 * **Trois choses qu'aucun compilateur ne vérifie.** Les couleurs de l'icône sont
 * écrites deux fois — en Kotlin pour Compose, en ressources pour le système, qui lit
 * l'icône avant que Compose n'existe — et rien ne relie les deux écritures. La
 * géométrie, elle, se juge à l'œil sur un canevas de 108 alors que le système n'en
 * garantit que 66. Et la variante thématique est un second fichier qui porte le même
 * tracé, donc un second fichier qu'on oublie de corriger.
 *
 * **Les fichiers sont lus comme du texte**, et non chargés en ressources : ce qui est
 * éprouvé ici est ce qui est écrit dans le dépôt, pas ce qu'un moteur en aurait fait.
 * C'est la seule forme qui attrape une couleur corrigée d'un côté et pas de l'autre.
 */
class LauncherIconTest {
    @Test
    fun `les six teintes de l icone sont celles du theme`() {
        assertEquals(themeTints(), iconTints(), "les ressources et MacroColors.kt ont diverge")
    }

    @Test
    fun `les six quartiers tiennent dans le disque garanti visible`() {
        val debordements = corners(FOREGROUND)
            .map { hypot(it.first - CENTRE, it.second - CENTRE) }
            .filter { it > SAFE_RADIUS }

        assertTrue(debordements.isEmpty(), "des sommets sortent du disque de 66 : $debordements")
    }

    @Test
    fun `les six quartiers sont la, et six`() {
        assertEquals(MACROS, pathData(FOREGROUND).size)
        assertEquals(MACROS, iconTints().size)
    }

    @Test
    fun `la variante monochrome porte la meme figure`() {
        assertEquals(
            pathData(FOREGROUND),
            pathData(MONOCHROME),
            "l'icône thématique ne montre plus le même hexagone que l'icône colorée",
        )
    }
}

/** Les six teintes telles que le système les lira, rangées par nom de macro. */
private fun iconTints(): Map<String, String> = Regex("""<color name="neon_(\w+)">#FF([0-9A-Fa-f]{6})</color>""")
    .findAll(read("src/main/res/values/colors.xml"))
    .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    .filterKeys { it in MACRO_NAMES }

/** Les six teintes telles que Compose les peindra, rangées sous le même nom. */
private fun themeTints(): Map<String, String> {
    val source = read("src/main/kotlin/app/hexavore/core/designsystem/theme/MacroColors.kt")
    val block = source.substringAfter("private object MacroBaseColors {").substringBefore("\n}")

    return Regex("""val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)""")
        .findAll(block)
        .associate { it.groupValues[1].lowercase() to it.groupValues[2].uppercase() }
}

/** Les tracés d'un vecteur, dans l'ordre du fichier. */
private fun pathData(drawable: String): List<String> = Regex("""android:pathData="([^"]+)"""")
    .findAll(read("src/main/res/drawable/$drawable"))
    .map { it.groupValues[1] }
    .toList()

/** Tous les points cités par les tracés, sans se soucier de la commande qui les porte. */
private fun corners(drawable: String): List<Pair<Float, Float>> = pathData(drawable).flatMap { trace ->
    Regex("""(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
        .findAll(trace)
        .map { it.groupValues[1].toFloat() to it.groupValues[2].toFloat() }
        .toList()
}

/**
 * Un fichier du module, lu depuis la racine du module.
 *
 * Gradle lance les tests avec le dossier du module pour répertoire courant. Le
 * message d'erreur le rappelle : une exception de fichier introuvable, ici, veut dire
 * que cette hypothèse a changé, et non que l'icône a disparu.
 */
private fun read(path: String): String {
    val file = File(path)
    check(file.exists()) { "${file.absolutePath} est introuvable : le repertoire courant des tests a change" }
    return file.readText()
}

private const val FOREGROUND = "ic_launcher_foreground.xml"
private const val MONOCHROME = "ic_launcher_monochrome.xml"
private const val CENTRE = 54f
private const val SAFE_RADIUS = 33f
private const val MACROS = 6
private val MACRO_NAMES = setOf("calories", "protein", "carbs", "sugars", "fat", "fiber")
