@file:Suppress("UnstableApiUsage")

pluginManagement {
    // Les regles detekt maison et les plugins de convention sont un build inclus,
    // pas un module du projet : ils s'executent sur la JVM de Gradle et n'ont rien
    // a faire dans le graphe de dependances de l'application. Voir D16 dans
    // docs/11-decisions.md.
    //
    // Ici, c'est ce qui rend les plugins `hexavore.*` resolubles par identifiant.
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Un module qui declare son propre depot contourne le catalogue et la revue :
    // on l'interdit plutot que de le decouvrir six mois plus tard.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Le meme build, declare une seconde fois hors de pluginManagement, et ce n'est pas
// une redite : les deux declarations ne font pas la meme chose. Celle de
// pluginManagement resout les identifiants de plugin ; celle-ci substitue le projet
// local a la coordonnee `app.hexavore.buildlogic:detekt-rules` que le build racine
// declare en detektPlugins. Sans elle, Gradle va chercher cette coordonnee sur Maven
// Central, ou elle n'existe evidemment pas.
includeBuild("build-logic")

// Permet d'ecrire projects.core.designsystem plutot que project(":core:designsystem") :
// une faute de frappe devient une erreur de compilation du script.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "hexavore"

// Trois modules, et pas un de plus. Les treize autres de docs/06-architecture.md
// naissent le jour ou ils ont un fichier a contenir. Voir docs/12-plan-de-developpement.md.
include(":app")
include(":domain")
include(":core:designsystem")

// Tranche 1. Les implementations en memoire qui vivent ici ne sont pas des
// bequilles de test : ce sont les premieres implementations des ports, celles
// contre lesquelles l'ecran est ecrit avant que Room n'existe.
include(":core:testing")

// :core:common naît maintenant qu'il a de quoi exister : les implementations de
// Clock et DispatcherProvider, qui vivaient dans :app faute de mieux (D19).
include(":core:common")
include(":core:database")
include(":data:diary")
include(":feature:home")

// Tranche 2. L'ecran de validation : le point de convergence des quatre modes de
// saisie, ecrit pour n lignes des le premier jour.
include(":feature:entry")

// Tranche 3. La recherche.
//
// :tooling:ciqual-import est le seul module qui n'entre dans aucun APK : il
// convertit le XML de l'ANSES en base SQLite, sur la machine de developpement.
// Il est ici et non dans build-logic parce que son parseur porte une regle du
// projet -- une valeur inconnue n'est pas un zero -- et qu'une regle du projet se
// teste avec `./gradlew check`.
include(":tooling:ciqual-import")
include(":data:food")
include(":feature:search")

// Tranche 4. Le profil, le journal de poids et les objectifs versionnes.
//
// :data:profile et non une extension de :data:diary : un objectif n'est pas une
// entree de journal, et le nom d'un module est ce qui empeche d'y ranger n'importe
// quoi. Il porte trois ports que rien d'autre ne lit.
include(":data:profile")

// :data:settings et non :integration:ai : ranger une cle d'API est un probleme de
// stockage local, pas d'adaptation a un service tiers. Le mettre avec le fournisseur
// aurait fait du module d'integration le gardien du secret, alors qu'il n'en est que
// l'utilisateur -- et le jour ou un second reglage se range chiffre, il n'aurait rien
// eu a faire chez Anthropic.
include(":data:settings")
include(":feature:onboarding")

// :feature:settings naît avec la seule section qui a du contenu, « Profil et
// objectifs ». Les quatre autres que docs/02 prevoit dependent des tranches 6 et 8 ;
// le hub qui les rassemble naitra avec la deuxieme, faute de quoi il serait un ecran
// de transit vers une destination unique.
include(":feature:settings")

// Tranche 5. Le scan.
//
// :integration et non :data : un module :data adapte un port a un stockage qui
// nous appartient, celui-ci adapte un service tiers dont on ne decide ni le
// schema ni la disponibilite. Le nom dit lequel des deux on lit quand une reponse
// surprend.
include(":integration:openfoodfacts")

// :integration:scanner porte une composable, ce qu'aucun autre adaptateur ne fait.
// Une camera n'est pas une source de donnees qu'on puisse mettre derriere un port :
// c'est une surface, et l'abstraire demanderait au domaine de connaitre un type de
// vue. Le module fournit la surface et le decodage ; l'ecran garde le reste.
include(":integration:scanner")
include(":feature:scan")

// Tranche 6. La reconnaissance par photo ou par description.
//
// Six fournisseurs derriere un seul port, et un parseur commun : le modele rend du
// JSON qu'il entoure parfois de texte, et cette tolerance est la seule regle du
// module qui s'eprouve sans reseau.
include(":integration:ai")

// La ou l'utilisateur decrit ou photographie son repas. Les deux modes partagent le
// meme contrat de reconnaissance, donc le meme ecran d'attente, les memes erreurs et
// la meme sortie : les separer en deux modules ferait deux fois le meme etat.
include(":feature:capture")

// Tranche 7. Le journal de poids, sa courbe, et l'adaptation hebdomadaire.
//
// Un module a lui plutot qu'une section de :feature:settings : le journal se consulte
// depuis l'accueil et non depuis les reglages, il porte un trace qui n'existe nulle
// part ailleurs, et c'est lui qui recevra la carte d'ajustement -- laquelle parle
// d'objectifs sans que les reglages aient a la connaitre.
include(":feature:weight")

// Tranche 8. La sauvegarde.
//
// Un module a lui parce qu'il touche toutes les tables a la fois : les cinq autres
// :data adaptent chacun un port a un domaine de donnees, celui-ci lit et remplace
// l'ensemble en une transaction. Il emprunte leurs mappeurs plutot que d'en ecrire un
// second jeu -- deux traductions de la meme chose finissent par diverger, et la
// divergence corromprait de vraies donnees.
include(":data:backup")

// Addictive update. La progression : trois planchers et les paliers franchis.
//
// Un module a lui plutot qu'une section de :data:profile : ce que celui-ci range est
// ce que l'utilisateur **est** -- son age, son poids, son objectif -- la ou celui-ci
// range ce qu'il a **traverse**. Les deux n'ont ni la meme duree de vie ni la meme
// source : un profil se saisit, une progression se derive du journal et se fige.
include(":data:progress")

// Addictive update. L'ecran de progression : la serie, les paliers, le niveau.
//
// Un module a lui plutot qu'une section de :feature:home : l'accueil en montre trois
// lignes et rien de plus -- c'est ce qui le garde lisible -- la ou cet ecran deploie
// dix-huit paliers et leur histoire. Les deux lisent le meme cas d'usage.
include(":feature:progress")

// Addictive update. Les rappels : le travail planifie et la notification.
//
// Un :integration parce que c'est ce qu'il est -- un adaptateur vers le systeme, au
// meme titre que la camera ou le reseau. Le domaine dit quels rappels courent et a
// quelle heure ; comment cela survit a un redemarrage ne le regarde pas.
include(":integration:reminders")

// Les deux rapports par courriel : un plantage, une proposition d'IA incorrecte.
//
// Un :integration parce que c'est ce qu'il est -- une intention Android, un
// fournisseur de fichiers, un gestionnaire d'exceptions. Le domaine sait seulement
// qu'un rapport se propose, et que rien ne part sans un geste.
include(":integration:reports")
