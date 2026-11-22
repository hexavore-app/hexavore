import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Module Kotlin/JVM, sans plugin Android. C'est structurel : personne ne peut
    // importer android.* par distraction, Gradle ne met tout simplement pas ces
    // classes sur le classpath. Voir docs/06-architecture.md.
    id("hexavore.jvm.library")
}

dependencies {
    // Et rien d'autre. Chaque ligne ajoutee ici doit se justifier en revue :
    // c'est la frontiere qui rend le metier testable en quelques millisecondes.
    implementation(libs.kotlinx.coroutines.core)

    // Les fausses implementations des ports servent aux tests du domaine avant de
    // servir aux ecrans. Elles restent hors du classpath de production.
    testImplementation(projects.core.testing)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// --- Couverture --------------------------------------------------------------
//
// Un plancher, pas un objectif : il suit ce qui est atteint, un peu en dessous. Le
// relever quand la couverture monte ; ne jamais le baisser pour faire passer un build.

kover {
    reports {
        variant("hexavore") {
            verify {
                rule { minBound(95) }
            }
        }
    }
}

// --- Purete du domaine -------------------------------------------------------
//
// Sans plugin Android, un import android.* echoue deja a la compilation, mais le
// compilateur ne sait dire que "unresolved reference". Cette tache s'execute avant
// lui et explique la raison de l'interdit. La regle detekt AndroidImportInDomain
// porte le meme message : elle couvre le cas ou seule l'analyse statique tourne.

abstract class VerifyDomainPurity : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val forbidden = listOf("import android.", "import androidx.", "import com.android.", "import dalvik.")

        val offenders =
            sources.files
                .filter { it.isFile }
                .flatMap { file ->
                    file
                        .readLines()
                        .mapIndexed { index, line -> (index + 1) to line.trim() }
                        .filter { (_, line) -> forbidden.any { line.startsWith(it) } }
                        .map { (lineNumber, line) -> "  ${file.name}:$lineNumber   $line" }
                }

        if (offenders.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Import d'une API Android dans :domain :")
                    offenders.forEach { appendLine(it) }
                    appendLine()
                    appendLine("Pourquoi c'est interdit :")
                    appendLine("  :domain est du Kotlin pur. Cette purete achete trois choses.")
                    appendLine("  1. Les regles metier se testent en JVM, en quelques millisecondes,")
                    appendLine("     sans emulateur. C'est ce qui rend une couverture de 90 % tenable.")
                    appendLine("  2. Aucun type Room ni DTO Retrofit ne peut remonter jusqu'au metier :")
                    appendLine("     le jour ou la source de donnees change, le domaine ne bouge pas.")
                    appendLine("  3. La logique reste portable.")
                    appendLine()
                    appendLine("Quoi faire a la place :")
                    appendLine("  Le code qui a besoin d'Android appartient a un adaptateur. Declarer")
                    appendLine("  dans :domain le port dont le metier a besoin (une interface), et")
                    appendLine("  l'implementer dans le module Android concerne.")
                    appendLine()
                    append("Voir docs/06-architecture.md.")
                },
            )
        }

        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText("${sources.files.size} fichier(s) verifie(s), aucun import Android.\n")
        }
    }
}

val verifyDomainPurity =
    tasks.register<VerifyDomainPurity>("verifyDomainPurity") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Echoue si un fichier de :domain importe une API Android."
        sources.from(fileTree("src") { include("**/*.kt") })
        report.set(layout.buildDirectory.file("reports/domain-purity/result.txt"))
    }

// Avant la compilation, pour que le message explicatif arrive avant le message
// du compilateur, qui lui ne dit que "unresolved reference".
tasks.withType<KotlinCompile>().configureEach {
    dependsOn(verifyDomainPurity)
}

tasks.named("check") {
    dependsOn(verifyDomainPurity)
    dependsOn("koverVerifyHexavore")
}
