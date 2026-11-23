plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
}

android {
    namespace = "app.hexavore.integration.reminders"

    // Les textes et le canal du module, pour Robolectric.
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    // Ce module implemente un port declare par le domaine : la fleche remonte.
    api(projects.domain)

    // Le travail planifie, et l'injection dans un worker. `hilt-work` a son propre
    // processeur : celui de Hilt ne connait pas `@HiltWorker`.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
