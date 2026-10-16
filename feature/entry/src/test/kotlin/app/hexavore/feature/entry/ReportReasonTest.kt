package app.hexavore.feature.entry

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Le signalement couvre ce qui choque, pas seulement ce qui est faux ([D168][decisions]).
 *
 * La règle de Google Play sur le contenu généré par une IA demande qu'on puisse signaler
 * un contenu offensant sans quitter l'application. Hexavore se tient hors de son champ : un
 * modèle y reconnaît des aliments, il ne crée rien qu'on publie. Mais un modèle peut rendre
 * ce qui n'a rien à faire dans un journal, et le même bouton porte les deux motifs pour le
 * prix de quelques mots. Revenir à « incorrecte » ferait d'une réponse insultante un défaut
 * qu'on ne peut plus dire : c'est ce que ce test garde, dans chaque langue et à chaque
 * endroit où le motif s'écrit.
 *
 * Il lit le dépôt : un test JVM n'a pas de `Context`, et ce qui est éprouvé est ce qui
 * est écrit.
 *
 * [decisions]: docs/11-decisions.md
 */
class ReportReasonTest {
    @Test
    fun `le signalement anglais dit faux ou offensant`() {
        val chaines = reportStrings("values")

        chaines.forEach { (nom, texte) ->
            assertTrue(texte.contains("wrong or offensive", ignoreCase = true), "$nom : $texte")
        }
    }

    @Test
    fun `le signalement francais dit faux ou choquant`() {
        val chaines = reportStrings("values-fr")

        chaines.forEach { (nom, texte) ->
            assertTrue(texte.contains("fausse ou choquante", ignoreCase = true), "$nom : $texte")
        }
    }
}

/** Les quatre endroits où le motif s'écrit : le bouton, la boîte, l'objet et le corps. */
private val REPORT_STRINGS = listOf(
    "entry_report",
    "entry_report_explain_title",
    "entry_report_subject",
    "entry_report_body",
)

private fun reportStrings(folder: String): Map<String, String> {
    val file = File("src/main/res/$folder/strings.xml")
    check(file.exists()) { "${file.absolutePath} est introuvable : le repertoire courant des tests a change" }
    val source = file.readText()
    return REPORT_STRINGS.associateWith { nom ->
        Regex("""<string name="$nom">(.*?)</string>""").find(source)?.groupValues?.get(1)
            ?: error("$nom est absent de $folder")
    }
}
