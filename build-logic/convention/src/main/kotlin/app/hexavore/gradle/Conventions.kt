package app.hexavore.gradle

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Le catalogue de versions, vu depuis un plugin de convention.
 *
 * Les accesseurs `libs.versions.compileSdk` ne sont pas generes ici : ils
 * n'existent que dans les scripts du build principal. La lecture passe donc par
 * l'extension, ce qui ne change rien a la regle — le catalogue reste la seule
 * source de verite, et aucun numero n'est ecrit dans ce repertoire.
 */
private val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Une version du catalogue.
 *
 * Echoue avec le nom manquant plutot qu'avec un `NoSuchElementException` : une
 * faute de frappe dans un alias se lit alors dans le message d'erreur.
 */
internal fun Project.catalogVersion(alias: String): String = libs
    .findVersion(alias)
    .orElseThrow { GradleException("Version '$alias' absente de gradle/libs.versions.toml.") }
    .requiredVersion

/** Une bibliotheque du catalogue, pour les dependances qu'une convention apporte. */
internal fun Project.catalogLibrary(alias: String): Provider<MinimalExternalModuleDependency> = libs
    .findLibrary(alias)
    .orElseThrow { GradleException("Bibliotheque '$alias' absente de gradle/libs.versions.toml.") }

/**
 * Les coordonnees d'un BOM, sous la forme que `platform()` accepte.
 *
 * Dans un `build.gradle.kts`, `platform(libs.androidx.compose.bom)` prend le
 * fournisseur du catalogue directement ; l'extension qui le permet appartient au
 * DSL des scripts et n'existe pas dans un fichier `.kt` ordinaire. On resout donc
 * le fournisseur ici — a la configuration, ou le catalogue est deja lu — et on
 * passe une notation. Le numero de version reste dans le catalogue.
 */
internal fun Project.catalogPlatform(alias: String): String = catalogLibrary(alias).get().let {
    "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}"
}

/**
 * Ce que tout module Android du projet partage.
 *
 * C'est le bloc qui etait recopie a l'identique dans cinq `build.gradle.kts`. Une
 * divergence entre deux copies ne se voyait qu'en compilant celle qui avait
 * diverge : `compileSdk` n'apparait donc plus qu'ici.
 *
 * Le parametre est le `CommonExtension` et non `LibraryExtension` : application et
 * bibliotheque partagent exactement cette configuration-la, et rien de plus.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
    val jvm = catalogVersion("jvmToolchain")

    extension.apply {
        compileSdk = catalogVersion("compileSdk").toInt()

        defaultConfig {
            minSdk = catalogVersion("minSdk").toInt()
        }

        compileOptions {
            sourceCompatibility = JavaVersion.toVersion(jvm)
            targetCompatibility = JavaVersion.toVersion(jvm)

            // Le désucrage de la bibliothèque standard, pour tout module Android.
            //
            // **Android n'a pas le `java.time` qu'on compile.** Celui de l'API 26 s'arrête
            // à Java 8 ; les ajouts de Java 9 n'arrivent qu'à l'API 31. Un appel comme
            // `LocalTime.ofInstant` compile, passe toute l'analyse statique, et lève un
            // `NoSuchMethodError` sur un téléphone sous Android 11 — c'est arrivé
            // ([D139][decisions]), et rien dans le dépôt ne pouvait l'attraper :
            // `NewApi` ne regarde pas `:domain`, qui est du Kotlin pur.
            //
            // D8 réécrit désormais ces appels vers une implémentation embarquée. Le
            // désucrage porte sur **tout ce qui est dexé**, donc sur les modules JVM
            // comme sur les autres : c'est ce qui en fait un garde-fou et non un
            // rappel ([D140][decisions]).
            //
            // [decisions]: docs/11-decisions.md
            isCoreLibraryDesugaringEnabled = true
        }
    }

    dependencies {
        add("coreLibraryDesugaring", catalogLibrary("desugar-jdk-libs"))
    }

    // Sur la tache plutot que sur l'extension Kotlin : la meme ligne vaut alors
    // pour un module Android comme pour un module JVM, et il n'y a qu'un endroit
    // ou le niveau de bytecode se decide.
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvm))
    }
}

/**
 * JUnit 5 comme plateforme de test.
 *
 * `:core:database` y ajoute le moteur *vintage* pour `MigrationTestHelper`, qui est
 * une regle JUnit 4 ([D35][decisions]). Les deux moteurs cohabitent sous cette
 * meme plateforme : la ligne ci-dessous reste vraie pour lui aussi.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun Project.configureTestPlatform() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
