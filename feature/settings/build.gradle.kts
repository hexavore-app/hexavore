plugins {
    id("hexavore.android.feature")
}

android {
    namespace = "app.hexavore.feature.settings"
}

dependencies {
    // `rememberLauncherForActivityResult` : la permission de notification se demande
    // a l'ecran, a l'endroit ou quelqu'un vient de regler un rappel (D134).
    implementation(libs.androidx.activity.compose)
}
