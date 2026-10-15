plugins {
    id("hexavore.android.feature")
}

android {
    namespace = "app.hexavore.feature.settings"

    // La version qu'affiche « A propos » (D161). Lue dans le catalogue de versions,
    // comme celle des traces de plantage de :integration:reports : il n'y a ainsi qu'un
    // endroit ou la monter, et l'ecran ne peut pas annoncer une version que l'APK ne
    // porte pas.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "VERSION_LABEL", "\"${libs.versions.versionName.get()}\"")
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
