import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    base
    // Aucun de ces quatre n'est applique par un module directement : ce sont les
    // plugins de convention de build-logic qui s'en chargent. Ils restent declares
    // ici parce que `apply false` fait une chose de plus que rien -- il les pose
    // sur le chemin de classes du build racine, le seul que voient detekt et
    // ktlint, appliques ci-dessous par cross-configuration. Sans cette ligne,
    // ktlint cherche `com.android.build.gradle.BaseExtension` dans un chargeur de
    // classes qui ne l'a pas, et le build echoue sur un nom de classe sans dire
    // d'ou il vient.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// L'analyse statique reste posee par cross-configuration, et c'est un choix.
// Un plugin `hexavore.quality` serait plus idiomatique, mais il s'appliquerait
// module par module : oublier de l'appeler desactiverait silencieusement detekt
// sur un module entier, sans qu'aucun build n'echoue pour le signaler. Ici,
// l'oubli est impossible. Voir D37 dans docs/11-decisions.md.
subprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.kover.get().pluginId)

    // La couverture ne mesure que les tests JVM, et du debug seulement : le release
    // rejoue les memes tests sur le meme code.
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { coverageVariant("jvm") }
    pluginManager.withPlugin("com.android.library") { coverageVariant("debug") }
    pluginManager.withPlugin("com.android.application") { coverageVariant("debug") }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        // Le premier fichier porte les reglages communs ; le second, quand il
        // existe, n'active ou ne desactive que ce qui est propre au module.
        // Un filtre par chemin (includes / excludes) aurait fait la meme chose
        // en apparence, mais les motifs glob de detekt se comportent mal sur
        // les separateurs Windows.
        config.setFrom(rootProject.files("config/detekt/detekt.yml") + moduleDetektConfig())
    }

    extensions.configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.get())
        // Les rapports de ktlint dans le repertoire de build suffisent ; on ne
        // veut pas d'un formatage automatique silencieux pendant un `check`.
        outputToConsole.set(true)
    }

    dependencies {
        add("detektPlugins", rootProject.libs.hexavore.detekt.rules)
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = rootProject.libs.versions.jvmToolchain.get()

        // La tache detekt par defaut ne regarde que src/main et src/test. Verifie
        // sur ce projet : un `Color(0x...)` ecrit en src/debug passait l'analyse
        // sans un mot. Ce n'est pas une hypothese de configuration, c'est la
        // difference entre un jeu de sources analyse et un jeu de sources qui ne
        // l'est pas -- et deplacer du code d'un repertoire a l'autre ne doit pas
        // le faire sortir de la revue automatique.
        setSource(files("src"))
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**")

        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            md.required.set(false)
            txt.required.set(false)
        }
    }
}

/**
 * Reglages detekt propres a un module, le cas echeant.
 *
 * `:domain` interdit les imports Android ; `:core:designsystem` est le seul
 * endroit ou une couleur a le droit d'etre ecrite.
 */
fun Project.moduleDetektConfig(): ConfigurableFileCollection {
    val overlay =
        when (path) {
            ":domain" -> "config/detekt/detekt-domain.yml"
            ":core:designsystem" -> "config/detekt/detekt-designsystem.yml"
            ":core:testing" -> "config/detekt/detekt-testing.yml"
            else -> null
        }
    return rootProject.files(listOfNotNull(overlay))
}

/** La variante de couverture `hexavore`, nourrie par une seule source de tests. */
fun Project.coverageVariant(source: String) {
    extensions.configure<KoverProjectExtension> {
        currentProject { createVariant("hexavore") { add(source) } }
    }
}


// Le rapport commun : `./gradlew :koverHtmlReportHexavore`. Ni les faux de
// `:core:testing` ni l'outil d'import CIQUAL n'entrent dans un APK.
dependencies {
    subprojects
        .filter { it.buildFile.exists() && it.path !in setOf(":core:testing", ":tooling:ciqual-import") }
        .forEach { kover(project(it.path)) }
}

kover {
    currentProject { createVariant("hexavore") {} }
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                // Le code que Hilt, Room et Compose ecrivent a notre place, le cablage
                // Hilt (verifie a la compilation), et la galerie, qui n'existe qu'en debug.
                classes(
                    "app.hexavore.di.*",
                    "app.hexavore.*.di.*",
                    "app.hexavore.gallery.*",
                    "*_Factory",
                    "*_Factory\$*",
                    "*_MembersInjector",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "hilt_aggregated_deps.*",
                    "dagger.*",
                    "*_Impl",
                    "*_Impl\$*",
                    "*ComposableSingletons*",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}

// build-logic est un build inclus : sans ces liens explicites, sa verification ne
// tournerait jamais. Une regle detekt non testee est une regle qu'on croit active,
// et un plugin de convention non verifie est une regle de build qu'on croit
// appliquee.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":detekt-rules:check"))
    dependsOn(gradle.includedBuild("build-logic").task(":convention:check"))
}
