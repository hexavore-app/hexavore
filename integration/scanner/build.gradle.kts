plugins {
    id("hexavore.android.library.compose")
}

android {
    namespace = "app.hexavore.integration.scanner"
}

dependencies {
    api(projects.domain)
    // Pour les espacements du viseur : aucune valeur de style ne se decide ici.
    implementation(projects.core.designsystem)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.cpp)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
