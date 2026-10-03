package app.hexavore.domain.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La dérivation qui relie ce qui est enregistré à ce qu'on envoie.
 *
 * **La règle éprouvée ici est « demandée et possible »** : la case de l'analyse
 * approfondie peut rester cochée pendant qu'on bascule sur un fournisseur qui ne sait pas
 * appeler d'outils, et c'est ce `&&` — écrit une fois, ici — qui empêche d'envoyer des
 * outils à qui n'en attend pas.
 *
 * Les deux lectures défensives se vérifient aussi : l'état vient d'un fichier que
 * l'application ne contrôle pas seule, et une combinaison incohérente doit se lire comme
 * « aucun fournisseur utilisable » plutôt que faire tomber l'écran.
 */
class AiCredentialsTest {
    @Test
    fun `un fournisseur qui sait appeler des outils recoit l analyse approfondie`() {
        val configuration = setup(AiProvider.ANTHROPIC).activeConfiguration()

        assertTrue(configuration?.deepAnalysis == true)
    }

    @Test
    fun `un fournisseur sans outillage ne la recoit pas`() {
        // Ce n'est plus un reglage : la seule question est de savoir si le fournisseur
        // sait appeler des outils (D142).
        assertFalse(AiProvider.OPENAI.tooling, "le cas suppose un fournisseur sans outillage")

        val configuration = setup(AiProvider.OPENAI).activeConfiguration()

        assertFalse(configuration?.deepAnalysis == true)
    }

    @Test
    fun `la configuration porte ce qui a ete enregistre`() {
        val configuration = setup(AiProvider.GEMINI).activeConfiguration()

        assertEquals(AiProvider.GEMINI, configuration?.provider)
        assertEquals("modele-de-test", configuration?.model)
    }

    @Test
    fun `aucun fournisseur actif ne donne aucune configuration`() {
        assertNull(AiSetup().activeConfiguration())
    }

    @Test
    fun `un fournisseur actif sans cle ne donne aucune configuration`() {
        // Aucune operation ne produit cet etat ; il vient d'un fichier abime, et se lit
        // comme un manque plutot qu'en faisant tomber l'ecran.
        val setup = AiSetup(active = AiProvider.ANTHROPIC, credentials = emptyMap())

        assertNull(setup.activeConfiguration())
    }

    private fun setup(provider: AiProvider) = AiSetup(
        active = provider,
        credentials = mapOf(
            provider to ProviderCredentials(
                apiKey = ApiKey("cle-de-test"),
                model = "modele-de-test",
                baseUrl = "https://exemple.test/",
            ),
        ),
    )
}
