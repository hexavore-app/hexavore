package app.hexavore.domain.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que l'énumération promet aux écrans qui la lisent.
 *
 * **Trois propriétés, et elles se cassent en silence.** Un fournisseur proposé sans
 * console laisse quelqu'un devant un champ vide sans savoir où prendre une clé ; deux
 * recommandés ne recommandent rien ; une URL qui n'est pas une adresse fait un lien
 * mort. Aucune de ces trois n'échoue à la compilation, et aucune ne se voit avant
 * qu'un utilisateur bute dessus.
 */
class AiProviderTest {
    @Test
    fun `tout fournisseur propose dit ou prendre une cle`() {
        // C'est la condition pour que l'aide serve a quelque chose : elle ne s'affiche
        // que s'il y a une console, et un fournisseur propose sans console laisserait
        // le champ nu, exactement comme avant.
        AiProvider.entries
            .filter { it.status == ProviderStatus.READY }
            .forEach { assertTrue(it.consoleUrl.isNotBlank(), "${it.displayName} ne dit pas ou prendre une cle") }
    }

    @Test
    fun `une console est une adresse et non un texte`() {
        AiProvider.entries
            .filter { it.consoleUrl.isNotEmpty() }
            .forEach { assertTrue(it.consoleUrl.startsWith("https://"), "${it.displayName} : ${it.consoleUrl}") }
    }

    @Test
    fun `un seul fournisseur est recommande`() {
        // Sans quoi le mot ne veut plus rien dire, et l'ecran affiche deux etoiles
        // entre lesquelles il faudrait encore choisir.
        assertEquals(1, AiProvider.entries.count { it.recommended })
    }

    @Test
    fun `le recommande est propose`() {
        // Mettre en avant un fournisseur tenu en reserve serait le pire des deux
        // mondes : une etoile sur une carte qui ne se deplie pas.
        val recommande = AiProvider.entries.single { it.recommended }

        assertEquals(ProviderStatus.READY, recommande.status)
    }

    @Test
    fun `le recommande est gratuit, et c est Gemini que les invitations nomment`() {
        // L'invitation de l'accueil et le tour disent « Gemini, l'IA de Google » et
        // « gratuit » en toutes lettres, et « Obtenir ma cle » ouvre le guide du
        // recommande (D178). En changer sans recrire ces textes les ferait mentir.
        val recommande = AiProvider.entries.single { it.recommended }

        assertEquals(AiProvider.GEMINI, recommande)
        assertTrue(recommande.free, "l'invitation promet une IA gratuite")
    }

    @Test
    fun `le relais generique ne pretend pas avoir de console`() {
        // Personne ne sait quel service il designe : proposer un lien reviendrait a
        // envoyer quelqu'un chez un fournisseur qu'il n'a pas choisi.
        assertEquals("", AiProvider.COMPATIBLE.consoleUrl)
    }
}
