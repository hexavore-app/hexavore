plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
}

android {
    namespace = "app.hexavore.integration.reports"

    buildFeatures {
        // Le rapport de plantage cite la version de l'application : c'est la premiere
        // chose qu'on regarde en lisant une trace, et la demander a :app ferait
        // dependre un adaptateur de ce qu'il adapte.
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "VERSION_LABEL", "\"${libs.versions.versionName.get()}\"")
    }
}

dependencies {
    api(projects.domain)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Une `Intent` est une classe d'Android : eprouver celle du signalement demande
    // Robolectric, pas un telephone (D35). Lanceur JUnit 4, d'ou le moteur vintage.
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
