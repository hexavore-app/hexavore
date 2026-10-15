plugins {
    id("hexavore.android.feature")
}

android {
    namespace = "app.hexavore.feature.onboarding"
}

dependencies {
    // `BackHandler` : le retour du systeme recule d'une etape au lieu de fermer
    // l'application (D161). `rememberLauncherForActivityResult`, qui demande les
    // notifications au dernier geste, venait deja de ce module sans qu'il soit declare.
    implementation(libs.androidx.activity.compose)
}
