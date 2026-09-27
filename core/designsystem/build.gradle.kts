plugins {
    id("hexavore.android.library.compose")
}

android {
    namespace = "app.hexavore.core.designsystem"
}

dependencies {
    // Pour le seul theme de fenetre de `values/themes.xml`, dont `AppCompatActivity`
    // exige un parent `Theme.AppCompat` (D129). Aucune classe de ce paquet n'est
    // utilisee ici : c'est une dependance de ressources.
    implementation(libs.androidx.appcompat)

    // api : le vocabulaire des macros fait partie de la signature des composants.
    // Un module qui affiche une MacroBar doit pouvoir nommer la macro concernee.
    api(projects.domain)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // La regle de filtrage d'un champ numerique se teste sans Compose : c'est une
    // fonction sur une chaine, et c'est elle qui decide si une saisie est possible.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
