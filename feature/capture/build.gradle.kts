plugins {
    id("hexavore.android.feature")
}

android {
    namespace = "app.hexavore.feature.capture"
}

dependencies {
    // L'appareil photo du systeme, la galerie et la reduction JPEG : partages avec
    // l'ecran de validation depuis qu'il sait remplacer l'image d'un plat (D156).
    implementation(projects.core.media)
}
