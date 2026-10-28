package app.hexavore.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * **Hexavore est masculin** ([D182][decisions]).
 *
 * « Hexavore est gratuite », « elle s'appuie sur Gemini » : trois textes de l'application
 * et la fiche du Play Store l'accordaient au féminin, en pensant « l'application ». L'auteur
 * a tranché : le nom est masculin. « L'application » reste féminine, et c'est la seule
 * façon de dire « elle » en parlant d'Hexavore.
 *
 * Il lit le dépôt, comme [MedicalDisclaimerTest] : tous les `strings.xml` français, la fiche
 * et la politique de confidentialité, c'est-à-dire tout ce qu'un utilisateur lit en français.
 * Ce n'est qu'un tamis — un attribut au féminin juste après « Hexavore est », un « elle »
 * dans la même phrase —, mais c'est la faute qui a été faite, et elle se refait sans y penser.
 *
 * [decisions]: docs/11-decisions.md
 */
class HexavoreGenderTest {
    @Test
    fun `aucun texte francais n accorde Hexavore au feminin`() {
        val fautes = textesFrancais().flatMap { fichier ->
            accordsAuFeminin(lisible(fichier.readText())).map { "${fichier.path} : $it" }
        }

        assertTrue(fautes.isEmpty(), fautes.joinToString("\n"))
    }

    @Test
    fun `le tamis reconnait les deux fautes, et laisse passer le reste`() {
        assertEquals(
            listOf("Hexavore est gratuite"),
            accordsAuFeminin("Hexavore est gratuite et sans publicité."),
        )
        assertEquals(1, accordsAuFeminin("Hexavore n'a pas de serveur : pour la photo, elle s'appuie sur Gemini.").size)

        listOf(
            "Hexavore est gratuit et sans publicité.",
            "Hexavore s'est fermé seul.",
            "Hexavore est une application Android, libre et gratuite.",
            "Une clé relie Hexavore à votre compte. Elle reste sur ce téléphone.",
            "Une photo prise depuis Hexavore est écrite, lue, puis supprimée : elle n'entre jamais dans la galerie.",
            "La sauvegarde est désactivée pour Hexavore : elles ne partent pas non plus.",
        ).forEach { juste -> assertEquals(emptyList<String>(), accordsAuFeminin(juste), juste) }
    }
}

/**
 * Les fautes d'accord d'un texte : Hexavore sujet d'un attribut au féminin, ou repris par
 * « elle » dans la même phrase.
 *
 * Hexavore n'est sujet qu'en tête de phrase ou de proposition : « une photo prise depuis
 * Hexavore est écrite » accorde le participe avec la photo, et c'est juste.
 */
private fun accordsAuFeminin(texte: String): List<String> = texte
    .split(FIN_DE_PHRASE)
    .filter { "Hexavore" in it }
    .flatMap { phrase ->
        listOfNotNull(
            ATTRIBUT.find(phrase)?.takeIf { it.groupValues[1].auFeminin() }?.value?.trim(' ', ':', ';', ',', '«'),
            phrase.trim().takeIf {
                SUJET.containsMatchIn(phrase) && REPRISE.containsMatchIn(phrase.substringAfter("Hexavore"))
            },
        )
    }

/** Un mot en -e qui n'est ni un article ni un adjectif épicène : « libre » vaut pour les deux genres. */
private fun String.auFeminin(): Boolean = lowercase().let { it.endsWith("e") && it !in EPICENES }

/**
 * Le texte que l'écran affiche : sans commentaires ni balises, l'apostrophe échappée pour
 * aapt ne l'est plus. Les commentaires parlent du code, pas à l'utilisateur.
 */
private fun lisible(brut: String): String = brut
    .replace(COMMENTAIRE, "\n")
    .replace(BALISE, "\n")
    .replace("\\'", "'")
    .replace("\\n", "\n")

/** Tous les textes français que lit un utilisateur, depuis le module : le répertoire courant des tests. */
private fun textesFrancais(): List<File> {
    val depot = File("../..")
    val ressources = depot.walkTopDown()
        .onEnter { it.name !in DOSSIERS_IGNORES }
        .filter { it.name == "strings.xml" && it.parentFile.name == "values-fr" }
        .toList()
    check(ressources.size > 10) { "trop peu de strings.xml français trouvés : ${ressources.size}" }
    return ressources + File(depot, "tooling/fiche/fiche-play-store.md") + File(depot, "confidentialite/fr.md")
}

private val FIN_DE_PHRASE = Regex("""[.!?](?:\s|$)|\n""")
private val COMMENTAIRE = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
private val BALISE = Regex("""<[^>]+>""")

/** « Hexavore est … », « Hexavore reste … », en tête de phrase ou de proposition. */
private val ATTRIBUT = Regex("""(?:^\s*|[:;,«(]\s*)Hexavore (?:est|reste|n'est pas|s'est) ([\p{L}-]+)""")

/** Hexavore sujet : en tête de phrase ou de proposition, et non « une photo prise depuis Hexavore ». */
private val SUJET = Regex("""(?:^\s*|[:;,«(]\s*)Hexavore """)

/** « elle » sujet, et non « elles », « celle » ou « quelle ». */
private val REPRISE = Regex("""(?:^|[\s,:;(])elle\b(?!s)""")

private val EPICENES = setOf(
    "une", "libre", "simple", "rapide", "utile", "facile", "disponible", "compatible", "fiable", "sobre",
    "propre", "unique", "pratique", "lisible", "conforme", "autonome",
)

private val DOSSIERS_IGNORES = setOf("build", ".git", ".gradle", "node_modules", ".idea", "out")
