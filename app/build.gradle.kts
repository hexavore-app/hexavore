import java.util.Properties

plugins {
    id("hexavore.android.application")
    id("hexavore.android.hilt")
}

/**
 * La cle de televersement, quand cette machine en a une.
 *
 * **Hors du depot, et le .gitignore le tient** : une cle de signature dans un historique
 * public est une cle perdue, et celle-ci est ce qui prouve au Play Store que la mise a
 * jour vient bien de son auteur.
 *
 * **Le fichier peut manquer, et le build reussit quand meme.** La CI construit, analyse
 * et teste sans jamais signer ; un `release` qui exigerait la cle ferait echouer le vert
 * sur chaque machine qui ne publie pas. Sans elle, le bundle sort non signe, ce qui est
 * exactement ce qu'on veut : le Play Store le refuse, et personne ne peut le prendre
 * pour une version publiable.
 */
val signingProperties: Properties? =
    rootProject
        .file("keystore.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { stream -> load(stream) } } }

android {
    namespace = "app.hexavore"

    defaultConfig {
        // Verrouille des la premiere publication sur le Play Store : le changer
        // ensuite cree une application entierement nouvelle, sans ses installations
        // ni ses mises a jour. Voir docs/10-qualite-et-livraison.md.
        applicationId = "app.hexavore"
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode =
            libs.versions.versionCode
                .get()
                .toInt()
        versionName = libs.versions.versionName.get()
    }

    // AGP 8 ne genere plus BuildConfig sans qu'on le demande. Un seul champ y est lu :
    // VERSION_NAME, que le User-Agent d'Open Food Facts exige (D26). C'est aussi la
    // raison pour laquelle il est lu ici et pas dans le module d'integration -- la
    // version est celle du binaire, suffixe de variante compris.
    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        // `create` et non `getByName` : `release` n'existe pas d'office, contrairement
        // a `debug` qui porte la cle de developpement que le SDK genere.
        signingProperties?.let { properties ->
            create("release") {
                // Le chemin s'ecrit avec des barres obliques, meme sous Windows : un
                // fichier .properties traite la contre-oblique comme un echappement,
                // et `C:\Users\moi\cle.jks` y devient `C:Usersmoicle.jks`.
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Suffixe pour que la version de developpement cohabite avec celle
            // installee depuis une release.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Nulle sur une machine sans cle : le bundle sort alors non signe.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.feature.home)
    implementation(projects.feature.entry)
    implementation(projects.feature.scan)
    implementation(projects.feature.capture)
    implementation(projects.feature.search)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.settings)
    implementation(projects.feature.weight)
    implementation(projects.data.diary)
    implementation(projects.data.food)
    implementation(projects.data.profile)
    implementation(projects.integration.openfoodfacts)
    implementation(projects.integration.ai)
    implementation(projects.data.settings)
    implementation(projects.data.backup)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
