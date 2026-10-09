package app.hexavore.integration.ai

import app.hexavore.domain.language.ContentLanguage
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Chaque version de prompt déclarée a son fichier.
 *
 * ### Ce que ce test attrape, et que rien d'autre n'attrapait
 *
 * Les versions sont des chaînes, les fichiers sont des assets : **aucun compilateur ne
 * relie les deux**. Une constante bumpée sans le fichier qui va avec compile, passe
 * l'analyse statique, s'installe, et échoue à l'exécution — au fond d'un `runCatching`
 * qui transforme la panne en « le modèle n'a rien rendu ».
 *
 * C'est exactement ce qui s'est produit : un commit a écrit `extract_fr_v3` et
 * `extract_en_v2`, puis a bumpé `estimatePromptVersion` au lieu de
 * `extractPromptVersion`. Pendant cinq semaines, l'extraction a tourné sur l'ancien
 * texte et **l'estimation n'a jamais eu lieu**, puisqu'elle demandait un fichier absent.
 * Toutes les lignes que le catalogue ne rejoignait pas arrivaient vides, et le
 * garde-fou censé les remplir était mort sans un bruit ([D153][decisions]).
 *
 * ### Pourquoi lire le dossier plutôt que les assets
 *
 * C'est un test JVM : il n'y a pas de `Context`, donc pas d'`AssetManager`. Il lit le
 * dépôt, comme `LauncherIconTest`, et c'est même ce qu'on veut — ce qui est éprouvé ici
 * est ce qui est écrit dans le dépôt, pas ce qu'un empaquetage en aurait fait.
 *
 * [decisions]: docs/11-decisions.md
 */
class PromptAssetsTest {
    @Test
    fun `chaque prompt declare existe`() {
        val manquants = ContentLanguage.entries.flatMap { langue ->
            listOf(
                extractPromptAsset(langue),
                estimatePromptAsset(langue),
                deepPromptAsset(langue),
            )
        }.filterNot { File(ASSETS, it).exists() }

        assertTrue(
            manquants.isEmpty(),
            "des prompts sont declares sans fichier : $manquants",
        )
    }

    /**
     * Et aucun fichier ne dort sans être déclaré.
     *
     * L'autre moitié de la même panne : les deux textes d'extraction écrits pour être
     * utilisés sont restés sur le disque sans que personne les appelle. Un fichier
     * orphelin n'est pas une erreur en soi, mais il en signale une — soit on a oublié de
     * bumper, soit on a oublié de supprimer.
     */
    @Test
    fun `aucun prompt ne dort sans etre declare`() {
        val declares = ContentLanguage.entries.flatMap { langue ->
            listOf(extractPromptAsset(langue), estimatePromptAsset(langue), deepPromptAsset(langue))
        }.map { it.substringAfterLast('/') }.toSet()

        val orphelins = File(ASSETS, DOSSIER).listFiles().orEmpty()
            .map { it.name }
            .filterNot { it in declares }

        assertTrue(orphelins.isEmpty(), "des prompts ne servent a personne : $orphelins")
    }

    private companion object {
        val ASSETS = File("src/main/assets")
        const val DOSSIER = "prompts"
    }
}
