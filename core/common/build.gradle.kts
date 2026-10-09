plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
}

android {
    namespace = "app.hexavore.core.common"
}

dependencies {
    // api : ce module implemente des ports declares par le domaine, et ses
    // liaisons Hilt exposent ces types-la.
    api(projects.domain)

    implementation(libs.kotlinx.coroutines.core)

    // Le jour regarde a deux implementations -- celle-ci et le faux de :core:testing --
    // et D53 veut qu'un seul jeu de cas les eprouve toutes les deux.
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
