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

    // `enableEdgeToEdge` et ses styles de barres : les barres du systeme suivent le
    // theme de l'application, et leurs voiles sont des couleurs, donc d'ici (D162).
    implementation(libs.androidx.activity.compose)

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

// `LauncherIconTest` et `MedicalDisclaimerTest` lisent le depot -- les ressources, et la
// fiche du Play Store qui doit reprendre l'avertissement mot pour mot. Un test JVM ne
// declare que ses classes : changer un texte laissait Gradle juger le test a jour, et le
// vert ne disait plus rien. Ce qu'ils lisent est donc une entree de la tache (D168).
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/res").withPropertyName("resources").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("../../tooling/fiche/fiche-play-store.md"))
        .withPropertyName("fiche")
        .withPathSensitivity(PathSensitivity.NONE)
}
