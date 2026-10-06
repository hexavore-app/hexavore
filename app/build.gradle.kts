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

/**
 * Les paliers d'Android que la livraison dit porter, et sur lesquels on le verifie.
 *
 * De `minSdk` a `targetSdk` : 26 parce que c'est le plancher declare, 36 parce que c'est
 * la cible, et entre les deux un palier par rupture connue -- 30 pour le stockage
 * cloisonne, 31 pour les alarmes et le materiel graphique, 33 pour les notifications et
 * la langue par application, 34 pour les services en avant-plan, 35 pour le bord-a-bord
 * impose.
 */
private val matriceAndroid = mapOf(
    "api26" to 26,
    "api30" to 30,
    "api31" to 31,
    "api33" to 33,
    "api34" to 34,
    "api35" to 35,
    "api36" to 36,
)

/**
 * En deca, il n'existe pas d'image de test allegee : on prend l'image ordinaire.
 *
 * `val` et non `const val` : un script de construction n'est pas un fichier Kotlin
 * ordinaire, et Kotlin n'y accepte pas de constante de compilation hors objet nomme.
 */
private val premierAtd = 30

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

        // Le lanceur des tests sur appareil. `AndroidJUnitRunner` et non un lanceur
        // Hilt : le tour de l'application se joue sur **le vrai graphe**, celui que
        // `HexavoreApplication` assemble. Un graphe de test aurait remplace les
        // adaptateurs par des faux, et c'est precisement les adaptateurs qu'on veut
        // voir tourner sur chaque version d'Android (D139).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // **Chaque methode part d'une installation neuve.**
        //
        // Le tour guide ne se montre qu'a qui ne l'a pas vu, et son souvenir ne s'oublie
        // pas -- c'est une decision produit (D141), et c'est une contrainte de test : sans
        // remise a neuf, seule la premiere methode executee voit un tour, et les autres
        // passent au vert sans sujet.
        //
        // Ce qu'elle repare par la meme occasion : les plats qu'une methode note
        // restaient dans le journal de la suivante, qui ouvrait alors  le premier plat
        // d'une journee qu'elle n'avait pas remplie.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    // La langue par application, declaree au systeme.
    //
    // **Genere et non ecrit a la main.** AGP rassemble les `values-<langue>/` de ce
    // module et de ses quatorze dependances, ecrit `locales_config.xml` et le reference
    // dans le manifeste. Une liste tenue a la main aurait oublie la troisieme langue le
    // jour de son ajout, et le selecteur d'Android ne l'aurait simplement pas proposee.
    //
    // Le `resources.properties` voisin declare la langue du `values/` sans qualificatif.
    // Sans lui, AGP ne sait pas quelle langue portent les ressources par defaut.
    androidResources {
        generateLocaleConfig = true
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

    testOptions {
        // L'orchestrateur : un processus par methode, et `clearPackageData` qui va avec.
        // C'est lui qui rend le premier lancement rejouable autant de fois qu'il y a de
        // methodes.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"

        /**
         * Les versions d'Android sur lesquelles le tour d'ecrans se joue.
         *
         * **Declarees ici plutot que dans `~/.android/avd/`.** Les AVD de la machine de
         * developpement ont ete ecrits a la main, portent des noms qui n'existent que la,
         * et ne disent nulle part lesquels comptent : une version verifiee ne se lisait
         * donc dans aucun fichier du depot. Gradle les telecharge et les cree lui-meme, ce
         * qui rend la matrice identique sur une autre machine et dans la CI.
         *
         * **API 26 est la borne basse et elle manque aux AVD locaux**, qui commencent a 30
         * alors que `minSdk` vaut 26 : quatre versions que l'application declare porter
         * n'avaient jamais ete ouvertes. Les bugs de compatibilite vivent aux extremites.
         *
         * `aosp-atd` la ou il existe -- une image sans services Google, taillee pour les
         * tests, qui demarre en quelques secondes. API 26 n'en a pas et prend l'image
         * ordinaire.
         *
         * Ce qu'aucune de ces images ne donne : One UI. Samsung ne publie pas d'image
         * systeme pour l'emulateur, et le telephone du signalement tourne sous One UI 8.5.
         * Voir docs/10 pour le chemin qui y mene (Remote Test Lab).
         */
        managedDevices {
            localDevices {
                matriceAndroid.forEach { (nom, palier) ->
                    create(nom) {
                        device = "Pixel 6"
                        apiLevel = palier
                        systemImageSource = if (palier >= premierAtd) "aosp-atd" else "aosp"
                    }
                }
            }
            groups {
                create("matrice") {
                    matriceAndroid.keys.forEach { targetDevices.add(localDevices[it]) }
                }
                // Les deux bornes, pour une pull request : c'est la ou vivent les bugs de
                // compatibilite, et vingt minutes d'emulateur ne tiennent pas dans la
                // cible de huit minutes.
                create("bornes") {
                    targetDevices.add(localDevices["api26"])
                    targetDevices.add(localDevices["api36"])
                }
            }
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
    implementation(projects.feature.progress)
    implementation(projects.data.diary)
    implementation(projects.data.food)
    implementation(projects.data.profile)
    implementation(projects.data.progress)
    implementation(projects.integration.openfoodfacts)
    implementation(projects.integration.ai)
    implementation(projects.integration.reminders)
    implementation(projects.integration.reports)
    implementation(projects.data.settings)
    implementation(projects.data.backup)

    // La fabrique de travailleurs de Hilt, et le `Configuration.Provider` que
    // `HexavoreApplication` implemente : c'est `:app` qui assemble les deux (D134).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // AppCompat pour `setApplicationLocales`, et pour rien d'autre : aucun widget, aucun
    // fragment, aucune ressource de ce paquet ne sert ici. Voir D129.
    implementation(libs.androidx.appcompat)
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

    // Le tour de l'application, joue sur un appareil ou un emulateur.
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    // UiAutomator et non le testeur de Compose : celui-ci ne voit que la fenetre de
    // l'activite, et la feuille du  +  est une fenetre a elle (D139).
    androidTestImplementation(libs.androidx.test.uiautomator)
    // `androidTestUtil` et non `androidTestImplementation` : l'orchestrateur s'installe a
    // cote de l'APK de test, il n'en fait pas partie.
    androidTestUtil(libs.androidx.test.orchestrator)
}
