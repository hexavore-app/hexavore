plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
}

android {
    namespace = "app.hexavore.data.progress"

    // Robolectric a besoin des ressources Android pour ouvrir une base SQLite sur la
    // JVM. Sans cela, le contrat du port exigerait un appareil.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Ce module implemente un port declare par le domaine : la fleche remonte,
    // :data depend de :domain et jamais l'inverse.
    api(projects.domain)
    implementation(projects.core.database)

    implementation(libs.kotlinx.coroutines.core)

    // JUnit 4 et le moteur vintage pour la meme raison qu'en :core:database (D35) :
    // Robolectric est un lanceur JUnit 4, et le contrat se joue sur la vraie base.
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
