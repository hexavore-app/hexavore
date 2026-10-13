plugins {
    id("hexavore.android.library.compose")
}

android {
    namespace = "app.hexavore.core.media"
}

dependencies {
    // Le declencheur photo et le selecteur de galerie sont deux contrats de resultat
    // d'activite : c'est le systeme qui prend la photo, pas nous.
    implementation(libs.androidx.activity.compose)

    // FileProvider : depuis Android 7, remettre un chemin brut a une autre
    // application leve FileUriExposedException.
    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.coroutines.core)

    // La reduction se teste sans appareil : ce sont deux fonctions sur des entiers,
    // et ce sont elles qui decident de la memoire qu'une photo occupe.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
