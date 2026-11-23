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

    // Le fournisseur de fichiers du manifeste, et ses chemins, pour Robolectric.
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(projects.domain)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
