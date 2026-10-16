package app.hexavore.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `hexavore.android.feature` — un module d'ecran.
 *
 * Il apporte ce que tout `:feature` a par definition : le domaine, le design
 * system, Compose, le cycle de vie et Hilt. Ce sont les dependances qui decoulent
 * de ce qu'est un ecran, pas des choix propres a l'un d'eux.
 *
 * Ce qui n'y figure **pas** est aussi voulu : aucun adaptateur. Un `:feature` ne
 * voit que des cas d'usage, jamais un depot concret ni un type Room. Un module qui
 * en aurait besoin devrait l'ecrire lui-meme, donc se faire remarquer en revue.
 * Voir docs/06-architecture.md.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("hexavore.android.library.compose")
            pluginManager.apply("hexavore.android.hilt")
            // Chaque :feature declare sa propre destination, sous la forme d'une
            // data class @Serializable. C'est ce plugin qui lui fabrique un
            // serialiseur, donc ce qui rend la route typee plutot qu'une chaine.
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":domain"))
                "implementation"(project(":core:designsystem"))
                // La memoire d'une saisie a travers la mort du processus (D166) : tout
                // ecran qui a un formulaire en a besoin, et la regle n'est ecrite qu'une fois.
                "implementation"(project(":core:ui"))

                "implementation"(platform(catalogPlatform("androidx-compose-bom")))
                "implementation"(catalogLibrary("androidx-compose-material3"))
                "implementation"(catalogLibrary("androidx-compose-material-icons-core"))
                "implementation"(catalogLibrary("androidx-compose-ui"))
                "implementation"(catalogLibrary("androidx-compose-ui-tooling-preview"))
                "implementation"(catalogLibrary("androidx-lifecycle-runtime-compose"))
                "implementation"(catalogLibrary("androidx-lifecycle-viewmodel-compose"))
                "implementation"(catalogLibrary("androidx-hilt-navigation-compose"))
                "implementation"(catalogLibrary("androidx-navigation-compose"))
                // Declaree explicitement : la navigation l'embarque, mais c'est
                // notre code qui porte @Serializable, et dependre d'une transitive
                // pour une annotation qu'on ecrit soi-meme est une dette qui se
                // paie au premier changement de version de la navigation.
                "implementation"(catalogLibrary("kotlinx-serialization-core"))

                "debugImplementation"(catalogLibrary("androidx-compose-ui-tooling"))

                // Les fausses implementations des ports ne sont pas des bequilles
                // de test : ce sont les premieres implementations, celles contre
                // lesquelles un ecran est ecrit avant que la base n'existe.
                "testImplementation"(project(":core:testing"))
                "testImplementation"(catalogLibrary("junit-jupiter"))
                "testImplementation"(catalogLibrary("kotlinx-coroutines-test"))
                "testRuntimeOnly"(catalogLibrary("junit-platform-launcher"))
            }
        }
    }
}
