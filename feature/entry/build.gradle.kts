plugins {
    id("hexavore.android.feature")
}

android {
    namespace = "app.hexavore.feature.entry"
}

dependencies {
    // Remplacer ou ajouter l'image d'un plat demande le meme appareil photo et le
    // meme selecteur de galerie que l'ecran d'analyse. Ils vivent dans :core:media
    // plutot que dans :feature:capture : un :feature n'en importe pas un autre (D149).
    implementation(projects.core.media)
}
