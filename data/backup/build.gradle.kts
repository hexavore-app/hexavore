plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.hexavore.data.backup"

    // Robolectric a besoin des ressources Android pour ouvrir une base SQLite sur la
    // JVM. Le critere de fin de tranche -- export, effacement, import, etat identique
    // -- se joue sur la vraie base, pas sur un double.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // La version ecrite dans chaque sauvegarde (D162). Lue dans le catalogue de
    // versions, comme celle d'« A propos » et des traces de plantage : tenue a la main,
    // elle avait ete oubliee deux fois.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "VERSION_LABEL", "\"${libs.versions.versionName.get()}\"")
    }
}

dependencies {
    api(projects.domain)
    implementation(projects.core.database)

    // Les trois modules qui traduisent deja entre Room et le domaine. Un second jeu
    // de mappeurs ecrit ici finirait par diverger du leur, et la divergence
    // corromprait de vraies donnees : une sauvegarde ecrirait des lignes que
    // l'application relirait de travers.
    implementation(projects.data.diary)
    implementation(projects.data.food)
    implementation(projects.data.profile)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
