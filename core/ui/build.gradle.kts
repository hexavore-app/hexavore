plugins {
    id("hexavore.android.library")
    // Pour les cas seulement, qui ecrivent leur propre etat @Serializable : le code de
    // ce module n'en declare aucun.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.hexavore.core.ui"
}

dependencies {
    // api : les deux types paraissent dans la signature de FormMemory. Un ecran lui
    // passe son SavedStateHandle et le serialiseur de son etat.
    api(libs.androidx.lifecycle.viewmodel.savedstate)
    api(libs.kotlinx.serialization.core)

    // Le format, lui, reste ici : aucun ecran n'a a savoir que c'est du JSON.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
