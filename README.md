# Hexavore

Suivi alimentaire pour Android. Libre, gratuit, sans compte, sans publicité, sans télémétrie.

On note ce qu'on mange en quelques secondes — scan d'un code-barres, photo de l'assiette, recherche dans une base de 3 500 aliments, ou simple phrase en français — et l'application tient à jour six compteurs : **calories, protéines, glucides, sucres, lipides, fibres**.

> **Statut** : itération 0 — le socle. Le projet compile, s'installe et affiche une galerie de composants ; il ne suit encore aucun repas. La conception complète est dans `docs/`, le plan de construction dans [12](docs/12-plan-de-developpement.md).

---

## Ce que fait l'application

| | |
|---|---|
| **Objectif personnalisé** | Âge, sexe, taille, poids, activité, poids cible et échéance → six objectifs quotidiens calculés, modifiables à la main à tout moment. |
| **Quatre façons de saisir** | Code-barres · photo analysée par IA · recherche par nom · description écrite en langage naturel. |
| **Toujours corrigeable** | Chaque ligne du journal reste éditable : quantité, macros, aliment associé. Aucune donnée n'est figée. |
| **Vue du jour** | Ce qu'il reste à atteindre, repas par repas, en un écran. |
| **Historique** | Calendrier horizontal, chaque jour coloré selon l'atteinte des objectifs, consultable et modifiable. |
| **Objectif vivant** | Le poids réel est comparé chaque semaine à la trajectoire visée ; l'app propose un ajustement, l'utilisateur décide. |
| **Vos données restent à vous** | Tout est stocké localement. Sauvegarde optionnelle sur votre Google Drive, ou export dans un fichier. |

## Ce qu'elle ne fait pas

Pas de compte utilisateur. Pas de serveur. Pas de suivi publicitaire. Pas d'abonnement. Les appels aux modèles d'IA partent de votre téléphone avec **votre** clé API, directement vers le fournisseur que vous avez choisi — nous ne voyons rien passer.

## Documentation

La conception est découpée en douze documents. Lisez-les dans l'ordre pour comprendre le projet, ou piochez celui qui vous concerne.

| # | Document | Contenu |
|---|---|---|
| 01 | [Périmètre](docs/01-perimetre.md) | Ce qui est dans la v1, ce qui n'y est pas, et pourquoi. |
| 02 | [Parcours et écrans](docs/02-parcours-et-ecrans.md) | Chaque écran, chaque geste, chaque cas d'erreur. |
| 03 | [Calculs nutritionnels](docs/03-nutrition-calculs.md) | Formules, garde-fous, adaptation hebdomadaire. |
| 04 | [Sources de données](docs/04-sources-de-donnees.md) | Open Food Facts, CIQUAL, résolution des correspondances, licences. |
| 05 | [Intelligence artificielle](docs/05-ia.md) | Contrat d'extraction, fournisseurs, prompts, sécurité des clés. |
| 06 | [Architecture](docs/06-architecture.md) | Couches, modules, ports et adaptateurs, application des principes SOLID. |
| 07 | [Modèle de données](docs/07-modele-de-donnees.md) | Tables, invariants, migrations. |
| 08 | [Design system](docs/08-design-system.md) | Palette néon, composants, animation, accessibilité. |
| 09 | [Données et sauvegarde](docs/09-donnees-et-sauvegarde.md) | Stockage local, Google Drive, export, confidentialité. |
| 10 | [Qualité et livraison](docs/10-qualite-et-livraison.md) | Tests, CI, variantes de build, versions. |
| 11 | [Journal des décisions](docs/11-decisions.md) | Les choix structurants et leur justification. |
| 12 | [Plan de développement](docs/12-plan-de-developpement.md) | Dans quel ordre construire, et comment savoir qu'une étape est finie. |

## Technique en une ligne

Kotlin · Jetpack Compose · Room · Hilt · CameraX + zxing-cpp · Retrofit · WorkManager · minSdk 26.

## Construire

JDK 17 et le SDK Android (plateforme 35). Aucune clé, aucun compte, aucun secret n'est nécessaire pour compiler.

```
./gradlew check          # ktlint, detekt, Android Lint, tests
./gradlew assembleDebug  # APK de développement
./gradlew installDebug   # installe sur l'appareil branché
```

`installDebug` **installe par-dessus** plutôt que de remplacer. C'est ce qu'il faut : une migration Room et la recopie de `ciqual.db` ne s'éprouvent que sur une base déjà présente, et désinstaller d'abord les rend intestables. Les pièges de ce genre sont rassemblés dans [10](docs/10-qualite-et-livraison.md#travailler-sur-ce-dépôt).

Le projet est bâti sur Gradle 8.10 et AGP 8.7 ; le choix du palier et la marche à suivre pour en changer sont expliqués en [D15](docs/11-decisions.md#d15--chaîne-de-construction-alignée-sur-loutillage-installé---par-défaut). Toutes les versions vivent dans `gradle/libs.versions.toml` — aucune n'est écrite dans un `build.gradle.kts`.

Trois règles [detekt](build-logic/detekt-rules) maison font échouer le build sur ce que la relecture laisse passer : une couleur écrite hors du design system, un import Android dans `:domain`, une lecture directe de l'horloge système.

### Modules

Quatorze, nés au fur et à mesure qu'ils avaient un fichier à contenir.

| Module | Rôle |
|---|---|
| `:app` | Application, graphe Hilt, navigation |
| `:domain` | Kotlin pur — modèles, ports, calculs. Aucune dépendance Android, et le build le vérifie. |
| `:core:common` | Implémentations de `Clock`, `DispatcherProvider`, `IdGenerator` |
| `:core:designsystem` | Palette néon, thème, animations, composants Compose |
| `:core:database` | Room, migrations, schémas exportés, et la table de l'ANSES en lecture seule |
| `:core:testing` | Premières implémentations des ports, et les décors partagés |
| `:data:diary` | Le journal alimentaire |
| `:data:food` | Le catalogue d'aliments |
| `:data:profile` | Profil, journal de poids, objectifs versionnés |
| `:feature:home` | L'accueil et son hexagone |
| `:feature:entry` | L'écran de validation, point de convergence des modes de saisie |
| `:feature:search` | La recherche et le bandeau de rayons |
| `:feature:onboarding` | Les cinq questions |
| `:tooling:ciqual-import` | Convertit le XML de l'ANSES en `ciqual.db`. Le seul module qui n'entre dans aucun APK. |

`build-logic` s'y ajoute sans y figurer : c'est un build inclus, qui porte les plugins de convention et les règles detekt. Le découpage et ses raisons sont en [06](docs/06-architecture.md).

## Licence

Code sous **GPL-3.0**. Voir [11-decisions.md](docs/11-decisions.md#d10--licence-gpl-30---par-défaut) pour le raisonnement.

Les données embarquées ou consultées ont leurs propres licences, respectées et créditées :

- **CIQUAL 2025** — ANSES, Licence Ouverte Etalab 2.0.
- **Open Food Facts** — contributeurs Open Food Facts, ODbL 1.0.

L'écran « À propos » affiche ces attributions ; elles ne sont pas optionnelles.
