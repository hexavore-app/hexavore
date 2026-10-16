plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
    // Les DTO sont des data class @Serializable : c'est ce plugin qui leur fabrique
    // un decodeur.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.hexavore.integration.ai"
}

dependencies {
    // Ce module implemente un port declare par le domaine : la fleche remonte.
    api(projects.domain)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // La meme pile que pour Open Food Facts, et c'est l'argument de D73 : six
    // fournisseurs derriere un seul client, donc un seul intercepteur de redaction.
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
    // Un vrai serveur sur la boucle locale, pas un faux client : c'est le seul
    // montage qui eprouve ce qui vit sous l'API de Retrofit -- l'intercepteur de
    // redaction, les en-tetes, et le corps reellement serialise.
    //
    // JUnit 4 vient avec lui et non pour ecrire des tests : MockWebServer herite
    // d'ExternalResource, qui est une regle JUnit 4. Sans cette ligne, la classe
    // manque a l'execution -- pas a la compilation.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// `PromptAssetsTest` lit les prompts dans le depot, et un test JVM ne declare que ses
// classes : un fichier retire laissait Gradle juger le test a jour. Les assets sont donc
// une entree de la tache (D168).
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets").withPropertyName("assets").withPathSensitivity(PathSensitivity.RELATIVE)
}
