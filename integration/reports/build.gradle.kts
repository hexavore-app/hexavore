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
}
