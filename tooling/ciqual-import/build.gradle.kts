import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    // Kotlin/JVM pur : ce code tourne sur la machine de developpement, jamais sur
    // un telephone. Il n'entre dans aucun APK.
    id("hexavore.jvm.library")
}

dependencies {
    // Pour SearchText, et pour elle seule. La normalisation doit etre exactement la
    // meme a l'ecriture de l'index et a la lecture d'une saisie : deux copies d'une
    // meme regle divergent le jour ou l'une apprend les ligatures et l'autre non.
    implementation(projects.domain)

    // Le pilote embarque sa propre compilation de SQLite. C'est elle qui ecrit la
    // base ; celle d'Android ne fait que la lire.
    implementation(libs.sqlite.jdbc)

    // Pour les deux taches de catalogue, et pour elles seules. L'application, elle,
    // parle a ses six fournisseurs par Retrofit et ses propres DTO : ce SDK n'entre
    // dans aucun APK, et le prendre ici ne lie personne d'autre a Anthropic.
    implementation(libs.anthropic.java)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// --- La tache d'import -------------------------------------------------------
//
// Elle n'est branchee sur aucun cycle de vie de Gradle. La base generee est
// versionnee -- sans elle, un clone suivi d'un build donnerait une application sans
// catalogue -- et la regenerer a chaque compilation produirait un binaire de
// plusieurs megaoctets modifie dans chaque diff. On la lance quand l'ANSES publie.
//
// Les chemins sont resolus ici, en configuration, et passes en chaines. Une lambda
// qui les resoudrait a l'execution capturerait l'objet du script, que le cache de
// configuration ne sait pas serialiser.
//
// **Le dossier et non ses fichiers.** Il y a un fichier de titres courts par langue,
// et les langues sont declarees dans `ContentLanguage` -- que ce script ne peut pas
// lire. Les enumerer ici les aurait fait vivre a deux endroits, et une langue ajoutee
// au domaine aurait laisse son fichier hors des entrees de la tache : Gradle l'aurait
// declaree a jour, et la nouvelle langue serait sortie sans ses titres courts.

val catalogueDirectory = layout.projectDirectory.dir("../ciqual").asFile
val generatedDatabase = rootProject.layout.projectDirectory.file("core/database/src/main/assets/ciqual.db").asFile

tasks.register<JavaExec>("importCiqual") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Convertit le XML de l'ANSES en core/database/src/main/assets/ciqual.db."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.hexavore.tooling.ciqual.CiqualImportKt")

    inputs.dir(catalogueDirectory)
    outputs.file(generatedDatabase)

    args(catalogueDirectory.absolutePath, generatedDatabase.absolutePath)
}

// --- La tache des titres courts ----------------------------------------------
//
// Elle appelle un fournisseur, donc elle coute de l'argent et un compte. Elle n'a
// pour cette raison ni entree ni sortie declaree a Gradle : la marquer a jour la
// rendrait muette le jour ou l'on veut completer ce qui manque, et une tache qu'on
// paie doit partir quand on la lance, pas quand Gradle l'estime necessaire.
//
// La cle vient de la ligne de commande et n'est ni lue d'un fichier, ni ecrite dans
// un fichier, ni conservee. Elle appartient a l'utilisateur.
//
// La langue se nomme, et vaut le francais par defaut : c'est la passe qui a tourne, et
// celle qu'on reprend quand l'ANSES republie.
//
//   ./gradlew generateShortNames -PanthropicApiKey=... [-Planguage=en] [-PcatalogueModel=...]

val catalogueModel = providers.gradleProperty("catalogueModel").getOrElse("claude-opus-5")
val anthropicApiKey = providers.gradleProperty("anthropicApiKey").getOrElse("")
val catalogueLanguage = providers.gradleProperty("language").getOrElse("fr")

tasks.register<JavaExec>("generateShortNames") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Ecrit tooling/ciqual/short-names-<langue>.csv. Demande -PanthropicApiKey=... et depense."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.hexavore.tooling.ciqual.GenerateShortNamesKt")

    args(
        catalogueDirectory.absolutePath,
        catalogueLanguage,
        catalogueModel,
        anthropicApiKey,
    )
}

// --- La tache des teneurs completees -----------------------------------------
//
// Distincte de la precedente, et pas par symetrie : un titre court est un affichage,
// une teneur completee est un chiffre invente qui entrera dans un journal. Chacune
// doit pouvoir etre lancee, relue et refusee sans l'autre.
//
//   ./gradlew generateCompletions -PanthropicApiKey=... [-PcatalogueModel=...]

tasks.register<JavaExec>("generateCompletions") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Ecrit tooling/ciqual/completions.csv. Demande -PanthropicApiKey=... et depense."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.hexavore.tooling.ciqual.GenerateCompletionsKt")

    args(
        catalogueDirectory.absolutePath,
        catalogueModel,
        anthropicApiKey,
    )
}
