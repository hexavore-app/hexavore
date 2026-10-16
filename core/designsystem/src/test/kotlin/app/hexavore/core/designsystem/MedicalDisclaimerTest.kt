package app.hexavore.core.designsystem

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * L'avertissement médical dit ce que Google Play exige, et la fiche le reprend mot pour
 * mot ([D168][decisions]).
 *
 * La règle *Contenus et services de santé* demande à une application de santé qui n'est
 * pas un dispositif médical de le dire dans sa description — elle ne diagnostique, ne
 * traite, ne guérit ni ne prévient aucune maladie — et de rappeler de consulter un
 * professionnel de santé. L'application fait accepter la même promesse à l'accueil :
 * deux textes d'une même promesse finiraient par en dire deux, et c'est la fiche, collée à
 * la main dans la Console, qui dériverait la première.
 *
 * Il lit le dépôt, comme `LauncherIconTest` : ce qui est éprouvé est ce qui est écrit.
 *
 * [decisions]: docs/11-decisions.md
 */
class MedicalDisclaimerTest {
    @Test
    fun `l avertissement anglais porte les mots que Google Play exige`() {
        val texte = disclaimer("values")

        assertTrue(
            texte.contains("not a medical device and does not diagnose, treat, cure, or prevent any medical condition"),
            texte,
        )
        assertTrue(texte.contains("Consult a healthcare professional"), texte)
    }

    @Test
    fun `l avertissement francais les traduit`() {
        val texte = disclaimer("values-fr")

        assertTrue(texte.contains("pas un dispositif médical"), texte)
        assertTrue(texte.contains("ne diagnostique, ne traite, ne guérit ni ne prévient"), texte)
        assertTrue(texte.contains("consultez un professionnel de santé"), texte)
    }

    @Test
    fun `la fiche reprend l avertissement de l application, mot pour mot`() {
        val fiche = read(FICHE).lines()

        listOf("values", "values-fr").forEach { dossier ->
            val texte = disclaimer(dossier)
            assertTrue(fiche.any { it == texte }, "la fiche ne reprend pas l'avertissement de $dossier : $texte")
        }
    }
}

/** La fiche Play Store, depuis le module : le répertoire courant des tests. */
private const val FICHE = "../../tooling/fiche/fiche-play-store.md"

/** L'avertissement tel que l'écran l'affiche : l'apostrophe échappée pour aapt ne l'est plus. */
private fun disclaimer(folder: String): String = Regex("""<string name="ds_medical_disclaimer">(.*?)</string>""")
    .find(read("src/main/res/$folder/strings.xml"))
    ?.groupValues
    ?.get(1)
    ?.replace("\\'", "'")
    ?: error("ds_medical_disclaimer est absent de $folder")

private fun read(path: String): String {
    val file = File(path)
    check(file.exists()) { "${file.absolutePath} est introuvable : le repertoire courant des tests a change" }
    return file.readText()
}
