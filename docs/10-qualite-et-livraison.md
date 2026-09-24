# 10 — Qualité et livraison

## Stratégie de test

Pyramide classique, mais avec un déséquilibre assumé : l'essentiel de l'effort porte sur `:domain`, parce que c'est là que les erreurs coûtent cher et que les tests coûtent le moins.

| Niveau | Portée | Outillage | Objectif |
|---|---|---|---|
| Unitaire JVM | `:domain`, correspondances, parseurs | JUnit 5, Turbine, MockK | **couverture ≥ 90 %** sur `:domain` |
| Base de données | DAO, migrations | Room testing, Robolectric | toutes les migrations, toutes les requêtes |
| Composant UI | Composants du design system | Compose UI test | états et accessibilité |
| Image de référence | Écrans complets | Roborazzi | non-régression visuelle |
| Bout en bout | 5 parcours critiques | Compose UI test sur émulateur | ils ne cassent jamais |

### Ce qui doit être couvert sans exception

Ces cas ont été identifiés pendant la conception comme les endroits où un bug passe inaperçu et fait des dégâts durables :

- **Parseur CIQUAL** — `traces`, `< 0,5`, `-`, `NC`, virgule décimale, chaîne vide. Un `null` traité comme `0` fausse des mois de journal ([04](04-sources-de-donnees.md)).
- **Calculs d'objectif** — chaque garde-fou, ses deux bornes, et l'exemple complet de [03](03-nutrition-calculs.md#exemple-complet) comme test de référence.
- **Conversions d'unités** — chaque unité, chaque densité, chaque repli sur valeur par défaut.
- **Correspondance Open Food Facts** — champs absents, énergie en kJ seulement, `serving_size` non parsable, produit sans nom.
- **Robustesse du parsing IA** — JSON entouré de texte, tronqué, unité inconnue, confiance hors bornes, tableau vide.
- **Migrations Room** — chaîne complète `1 → N` sur une base peuplée.
- **Format de sauvegarde** — aller-retour export/import à l'identique, et refus d'une `formatVersion` supérieure.
- **Frontière jour/nuit** — une entrée à 23 h 59 appartient au bon jour, y compris au changement d'heure. `Clock` injecté partout ([06](06-architecture.md#cas-dusage)).

### Parcours de bout en bout

Cinq, pas plus — ils sont lents et fragiles, on les réserve à ce qui ne doit jamais casser :

1. Onboarding complet → objectifs calculés et affichés.
2. Recherche → ajout → visible dans le journal du jour avec les bons totaux.
3. Édition manuelle d'une entrée → totaux mis à jour, valeur verrouillée.
4. Navigation vers un jour passé → ajout → retour à aujourd'hui sans perte.
5. Export → effacement → import → état identique.

Le scan et l'IA ne sont pas testés de bout en bout : ils dépendent de la caméra et d'un service tiers. Leurs adaptateurs sont testés unitairement contre des réponses enregistrées.

### Les tests de contrat

Un port qui gagne une seconde implémentation **rejoint un jeu de tests de contrat** : les cas sont écrits une fois et exécutés sur les deux implémentations, côte à côte dans le même rapport.

Ce n'est pas une élégance. Quatre défauts livrés avaient la même forme — le faux était plus indulgent que le vrai, les tests étaient écrits contre le faux, et ils éprouvaient un chemin que l'application n'emprunte jamais ([D53](11-decisions.md#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée)). Une propriété que le faux s'autorise à ne pas tenir devient une ligne rouge à côté d'une verte, et non une découverte sur l'appareil.

| Contrat | Ports couverts | Où |
|---|---|---|
| `FoodCatalogContract` | `FoodSearch`, `FoodLookup`, `RecentFoods`, `FavoriteFoods`, `FoodStore`, `FoodUsage` | `:data:food` |
| `ProfileStoreContract` | `Profiles`, `WeightLog`, `Goals` | `:data:profile` |
| `DiaryContract` | `DiaryRepository`, `FoodCitations` | `:data:diary` |
| `FavoriteDishContract` | `FavoriteDishes` | `:data:diary` |

**Un port qui ne peut pas être doublé honnêtement est un port mal placé.** `FoodCitations` est arrivé dans `DiaryContract` par ce chemin : il vivait sur `FoodStore`, donc sur un catalogue qui ne connaît pas le journal, et son faux ne pouvait qu'exposer une carte posée à la main. Le déplacer là où la donnée se dérive n'a pas seulement rendu le faux honnête — cela a rendu la propriété **énonçable** : « noter un plat fait monter le compte » n'était écrivable dans aucun des deux contrats précédents ([D71](11-decisions.md#d71--le-compte-des-citations-quitte-le-catalogue-parce-quun-faux-ne-peut-pas-linventer---validée)).

**Ils vivent dans le module de l'adaptateur, pas dans `:core:testing`** : c'est ce qui fait compiler et exécuter les deux implémentations sous la même commande. Placés dans `:core:testing`, ils n'auraient jamais vu Room.

**Ce qu'un contrat ne peut pas porter.** Ce qui n'est pas atteignable par le port n'y a pas sa place, et l'y forcer demanderait d'élargir une interface du domaine pour les besoins d'un test. Deux exemples éprouvés ailleurs : le repli d'une énumération inconnue, qui suppose une base déjà écrite (`ProfileMapperTest`), et la borne de fin d'un objectif, qu'aucune séquence d'appels au port ne rend observable (`GoalBoundsTest`, `GoalCoverageTest` — voir [D57](11-decisions.md#d57--le-contrat-des-trois-ports-et-une-règle-que-deux-tris-masquaient---validée)).

**Éprouver un flux, pas une relecture.** `firstAfter` dans `:core:testing` rend ce qu'un flux émet **après** une écriture. Une relecture après coup passerait même si le flux n'avait jamais ré-émis, c'est-à-dire même si le port était resté une lecture unique — le défaut d'origine. La fonction a ses propres tests : défaillante, elle rendrait vert tout ce qu'elle touche.

### Deux moteurs de test, et lequel s'applique où

**JUnit 5 partout, sauf dans les modules qui ont besoin de Robolectric** : `:core:database`, `:data:food`, `:data:profile`, `:data:diary`. Robolectric est un lanceur JUnit 4 ; ces modules déclarent donc `junit4` et le moteur `junit-vintage`, qui rassemble les deux sous `./gradlew check` ([D35](11-decisions.md#d35--le-test-de-migration-tourne-sur-la-jvm-pas-sur-un-appareil---validée), [D53](11-decisions.md#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée)).

**Les assertions JUnit 4 prennent le message en premier argument, JUnit 5 en dernier.** C'est la faute la plus fréquente en passant d'un module à l'autre, et elle est silencieuse : `assertEquals(attendu, obtenu)` compile des deux côtés, seul le message part au mauvais endroit — jusqu'au jour où un `assertEquals("message", a, b)` compare le message à autre chose.

Un test Robolectric voit les assets de `:core:database`, dont `ciqual.db`, grâce à `testOptions { unitTests.isIncludeAndroidResources = true }`. Sans cette ligne, le contrat exigerait un appareil, donc ne tournerait pas.

---

## Analyse statique

| Outil | Rôle | Bloquant en CI |
|---|---|---|
| **ktlint** | Formatage | oui |
| **detekt** | Complexité, code mort, mauvaises pratiques | oui |
| **Android Lint** | Spécifique Android, accessibilité | avertissements bloquants sur les règles a11y |
| **Dependency Analysis** | Dépendances déclarées inutiles ou manquantes | non, rapport |

Trois règles detekt personnalisées, qui encodent les décisions de cette documentation au lieu de compter sur la vigilance :

1. **Pas de couleur codée en dur** hors `:core:designsystem` — interdit `Color(0x…)` ailleurs ([08](08-design-system.md#ressources)).
2. **Pas d'import Android dans `:domain`** — doublon volontaire de la contrainte Gradle, avec un message d'erreur qui explique pourquoi.
3. **Pas de `System.currentTimeMillis()` ni de `LocalDate.now()`** hors des implémentations de `Clock` — c'est ce qui garantit que le temps reste testable.

Ces règles vivent dans `build-logic/detekt-rules` et sont couvertes par leurs propres tests unitaires : une règle qu'on croit active sans l'avoir éprouvée ne protège rien.

**detekt analyse tous les jeux de sources, pas seulement `main` et `test`.** Sa tâche par défaut ne regarde que ces deux-là ; la source est donc fixée explicitement à `src` dans le `build.gradle.kts` racine. Sans cette ligne, déplacer un fichier de `src/main` vers `src/debug` le sortait de l'analyse sans qu'aucun build ne le signale ([D38](11-decisions.md)).

**Après avoir modifié une règle, `./gradlew --stop`.** detekt s'exécute dans un worker dont le chargeur de classes est mis en cache par le démon Gradle, et le chemin du jar de règles ne change pas d'un build à l'autre : le démon continue donc d'appliquer l'ancienne version, sans rien signaler. Les tests du module, eux, voient toujours le code à jour — c'est ce décalage qui rend le piège coûteux. La CI n'est pas concernée, elle démarre sur un démon neuf.

**Ce que l'outillage ne couvre pas.** La définition de « terminé » exige qu'aucune couleur, **durée ou dimension** ne soit codée en dur hors de `:core:designsystem`. Seules les couleurs sont vérifiées par une règle. Une règle sur les littéraux `.dp` et `.sp` a été écartée : en Compose, elle signale autant de faux positifs que de vrais, et une règle qu'on finit par désactiver est pire qu'une règle absente. Les durées et les dimensions restent donc tenues par la revue — c'est une faiblesse connue, écrite ici pour ne pas être découverte plus tard.

### Les seuils qui mordent, et la réponse qui n'est pas de les relever

`config/detekt/detekt.yml` ne déclare que les **écarts** à la configuration par défaut (`buildUponDefaultConfig = true`), ce qui fait qu'aucun de ces seuils n'y figure. Ils mordent quand même, et ce sont ceux qu'on rencontre :

| Règle | Seuil | Ce qui surprend |
|---|---|---|
| `TooManyFunctions` | 11 | Compté **par classe et par fichier**. Onze échoue déjà — le seuil est un maximum, pas une borne atteignable. |
| `LongMethod` | 60 lignes | |
| `ReturnCount` | 2 | Un `?: return` de plus fait échouer. |
| `CyclomaticComplexMethod` | 15 | Un `when` de neuf branches y passe **dès que cinq d'entre elles portent un `?:`** : chaque elvis compte. |
| `MatchingDeclarationName` | — | Un fichier qui contient **une seule** déclaration de type doit porter son nom. Des fonctions de premier niveau à côté ne le dispensent pas. |
| `SwallowedException` | — | Un `catch` qui ne **nomme** pas l'exception échoue, même quand la perdre est le comportement voulu. |
| `MagicNumber` | — | Frappe les **arguments de constructeur d'énumération** : `LIGHT(1.375)` est un constat. |

**La bonne réponse à `TooManyFunctions` est de sortir du type ce qui n'est pas une capacité de l'objet**, en fonctions privées de premier niveau — c'est ce que font `RoomFoodCatalog` et `InMemoryFoodCatalog`. Relever le seuil déplace le problème d'un cran et le rend invisible au suivant.

**La réponse à `SwallowedException` est de nommer la réduction, pas de vider le `catch`.** Réduire une `IOException` à une issue métier est souvent exactement ce qu'on veut — le code HTTP ne doit pas remonter à l'écran — mais la règle ne peut pas distinguer un choix d'un oubli. Une fonction d'extension sur l'exception (`offline.reducedTo(NoNetwork)`, `offline.toUnreachable()`) satisfait la règle **et** documente la perte : c'est la forme qu'ont prise `:integration:openfoodfacts` et `:integration:ai`.

**La bonne réponse à `CyclomaticComplexMethod` n'est pas davantage de relever le seuil.** Un `when` sur une énumération est lisible à neuf branches ; ce qui coûte les points est l'elvis répété dans chacune. Sortir le repli dans une fonction nommée fait tomber le compte **et** dit ce que le repli est — c'est ce qu'a fait la conversion des quantités, où chaque `?:` était un forfait à signaler.

Pour `MagicNumber` sur une énumération, la réponse est une constante de premier niveau nommée : les cinq facteurs d'`ActivityLevel` sont déclarés ainsi, et ils se retrouvent d'un coup d'œil le jour où une relecture de la littérature les fait bouger.

**`MagicNumber` et `TooManyFunctions` ne s'appliquent pas aux jeux de sources de test**, qui en sont exclus par la configuration par défaut. Un contrat de vingt cas ne déclenche donc rien. `:core:testing` va plus loin et désactive `MagicNumber` sur son `main` : ses nombres sont des valeurs d'exemple, et `PAIN_COMPLET_KCAL_POUR_100G` ne dirait rien que la ligne ne dise déjà.

ktlint suit le style `intellij_idea` ([D22](11-decisions.md#d22--style-ktlint--intellij_idea-pas-ktlint_official---par-défaut)). `./gradlew ktlintFormat` est sûr et rapide — mais il **reformate parfois une signature juste après qu'on l'a écrite**, donc on relit le diff avant de commiter.

---

## Travailler sur ce dépôt

Les pièges déjà payés. Chacun a coûté au moins une session, et aucun ne se devine.

### Éditer un fichier

**Ne jamais réécrire un fichier Kotlin ou Markdown avec `Set-Content` ou `Out-File`.** Ces cmdlets mutilent les accents : un fichier accentué recopié ainsi devient illisible et il faut le restaurer depuis git. Utiliser l'éditeur, ou à défaut :

```powershell
[System.IO.File]::WriteAllText($p, $c, (New-Object System.Text.UTF8Encoding $false))
```

**Les messages de console et de commit sont en ASCII sans accents** ; le KDoc et ce dossier `docs/` sont en français accentué. La console Windows et les hooks Git ne garantissent pas l'encodage, une documentation si.

**Ne jamais réécrire un `strings.xml` par un script.** Toute apostrophe y est échappée — `Aujourd\'hui` — et un `replace` sur des chaînes contenant des antislashs mange l'échappement une ligne sur deux, y compris sur les lignes qu'on ne visait pas. Le fichier reste un XML valide et le module compile ; c'est `mergeDebugResources` qui échoue, en désignant des ressources **intactes** dans un artefact de `build/`, avec un message qui parle d'`Invalid unicode escape sequence`. On cherche alors la faute là où elle n'est pas.

Ces fichiers s'éditent à la main. En cas de doute, `git checkout HEAD -- <fichier>` puis réappliquer : c'est plus rapide que de retrouver quels antislashs manquent.

### Gradle

**Après avoir modifié une règle detekt, `./gradlew --stop`** — voir la section précédente, le démon garde l'ancien jar en cache.

**Après avoir déplacé un fichier entre jeux de sources**, supprimer l'état incrémental du module :

```bash
rm -rf <module>/build/tmp/kotlin-classes <module>/build/kotlin
```

**Une nouvelle version de schéma Room demande un build avant que le test de migration la voie.** Les schémas exportés sont un `assets.srcDir` du jeu de sources de test, et la fusion des assets peut passer avant que KSP ait écrit le `N.json` neuf : le premier `test` après un changement de `VERSION` échoue alors sur un `FileNotFoundException` qui ne dit rien de la migration. Relancer suffit — il n'y a rien à corriger.

**Ne pas utiliser `--rerun`.** Sur ce projet, il régénère `domain/build/libs/domain.jar` **vide** — 261 octets, le manifeste seul — et tout module qui en dépend échoue ensuite sur des `Unresolved reference 'domain'` qui n'ont aucun rapport apparent avec ce qu'on venait de changer. Le remède est de supprimer le jar. Pour forcer une ré-exécution de tests, `cleanTest` fait le même travail sans le risque.

**`includeBuild("build-logic")` est déclaré deux fois dans `settings.gradle.kts`, et ce n'est pas une redite.** Celle de `pluginManagement` rend les identifiants `hexavore.*` résolubles ; celle de la racine substitue le projet local à la coordonnée `app.hexavore.buildlogic:detekt-rules` que le build racine déclare en `detektPlugins`. Retirer l'une casse l'autre.

**Le cache de configuration est actif.** Une lambda écrite dans un `build.gradle.kts` qui capture l'objet du script n'est pas sérialisable : les chemins se résolvent à la configuration, pas à l'exécution.

**Le cache de build peut retenir un APK amputé, et `clean` ne le défait pas.** Constaté : l'application plantait à l'ouverture du scan sur `NoClassDefFoundError: RoomFoodCatalog`, alors que la classe était bien compilée, bien présente dans le `classes.jar` de l'AAR et bien dexée par le module. Elle disparaissait à la **fusion** — `:app:mergeLibDexDebug` — dont le résultat fautif avait été **enregistré dans le cache de build** et se restaurait à chaque build, `clean` compris. Le cache vit dans `~/.gradle/caches/build-cache-1`, hors du projet ; c'est pour ça que `clean` n'y peut rien.

Ce qui rend ce piège coûteux : **le vert ne bouge pas**. `check` compile, teste et passe ; c'est l'APK seul qui est amputé, et le défaut ne se voit qu'à l'exécution, sur la première classe manquante qu'on demande.

Le diagnostic tient en deux commandes. Comparer ce qu'un module a dexé avec ce que l'APK définit :

```bash
./gradlew clean assembleDebug --no-build-cache
```

Si l'APK est sain sans le cache et amputé avec, l'entrée fautive se nomme :

```bash
./gradlew clean assembleDebug --info | grep "cache entry for task ':app:mergeLibDexDebug'"
```

Le fichier `~/.gradle/caches/build-cache-1/<clé>` se supprime seul, sans vider les 25 000 autres entrées — elles sont partagées avec les autres projets de la machine.

**La vérification qui compte porte sur la table `class_defs` du dex**, et non sur une recherche de chaîne : un nom de classe apparaît aussi dans le dex qui la *référence*. Dans le cas constaté, les dix-huit classes **internes** de `RoomFoodCatalog` étaient présentes et la classe englobante absente — une recherche naïve aurait conclu qu'elle était là.

**Les plugins de convention sont des classes Kotlin, pas des scripts précompilés** ([D37](11-decisions.md#d37--plugins-de-convention-gradle---validée)).

**`gradlew` doit rester en mode `100755` dans l'index git**, sinon la CI ne peut pas l'exécuter.

**Une `value class` ne traverse pas Dagger.** Une fonction `@Provides` qui prend une classe en ligne en paramètre reçoit un nom décoré — `client-UZs_jxI` — qui n'est pas un identifiant Java valide, et la génération échoue sur `IllegalArgumentException: not a valid name`, sans nommer ni la fonction ni le type en cause. Une `data class` autour du même champ passe. Les `value class` du domaine ne sont pas concernées : elles voyagent dans des signatures, pas dans des liaisons.

**Une `value class` ne peut pas non plus être un paramètre `vararg`**, `FoodId` compris — c'est le même mécanisme, un cran plus tôt. Utiliser une `List`. Le cas se rencontre surtout dans une fonction d'aide de test, là où l'on écrirait volontiers `aliments(FoodId("a"), FoodId("b"))`.

**Room refuse à la compilation une requête dont un paramètre nommé ne sert pas.** C'est une bonne nouvelle mal placée : elle arrive sous la forme d'un échec de KSP au milieu d'autre chose. La rencontre typique est de **défaire** une règle pour vérifier qu'un test tombe — retirer le filtre d'une clause `WHERE` laisse son `:parametre` orphelin, et la défaite n'atteint jamais l'exécution ([D71](11-decisions.md#d71--le-compte-des-citations-quitte-le-catalogue-parce-quun-faux-ne-peut-pas-linventer---validée)). La couverture ne vient pas toujours d'un test.

### Sérialiser du JSON

**`encodeDefaults = true` écrit aussi les `null`.** Les deux réglages se lisent comme un seul et n'en font pas le même travail : `encodeDefaults` fait partir les valeurs par défaut, `explicitNulls = false` fait taire les absences. Sans le second, un champ nullable à `null` part quand même — `"inlineData":null` sur chaque part de texte envoyée à Gemini, `"text":null` sur chaque image ([D79](11-decisions.md#d79--gemini-entre-au-prix-annoncé-et-deux-tests-attrapent-ce-que-la-relecture-avait-laissé-passer---validée)).

Ce qui rend le piège coûteux : le code compile, les DTO se relisent sans rien remarquer, et le fournisseur répond — jusqu'à celui qui refuse. La faute se voit en dépliant un corps de requête, ce qu'on ne fait pas spontanément. **Un cas qui affirme l'absence d'un champ** est le seul moyen de la voir sans appareil ni compte.

**L'ordre des champs d'un objet JSON ne veut rien dire.** Il vaut celui de la déclaration Kotlin, et une assertion qui compare des positions de sous-chaînes dans le corps sérialisé fige la `data class` au lieu de la règle : elle survit aux vraies régressions et tombe le jour où quelqu'un réordonne deux propriétés. L'ordre d'un **tableau**, lui, est porteur — un modèle lit ses blocs de contenu dans l'ordre où ils arrivent. Affirmer l'un se fait en dépliant le JSON ; affirmer l'autre ne se fait pas.

### Vérifier sur un appareil

Il n'y a pas d'émulateur dans l'environnement de développement assisté : `./gradlew check` ne prouve que la compilation, les tests et leurs hypothèses. **Ce qui s'affiche n'est jamais prouvé par un vert**, et un compte rendu de travail dit ce que le vert ne prouve pas.

```bash
./gradlew installDebug
```

**Installer par-dessus, sans désinstaller d'abord**, quand la modification touche une migration Room ou la copie de `ciqual.db` : ces deux chemins ne s'éprouvent que sur une base déjà présente, et une désinstallation les rend intestables jusqu'à ce qu'un journal soit reconstruit à la main.

### Lire la base de l'appareil

Une migration qui « passe » n'est prouvée que par la base d'après. Il n'y a pas de `sqlite3` sur un téléphone du commerce ; on rapatrie le fichier et on l'ouvre ici.

**`adb shell` corrompt le binaire, `adb exec-out` non.** Sous Windows, `adb shell run-as … cat base.db > fichier` traduit les fins de ligne : le fichier arrive plus **gros** que l'original — 92 octets de trop sur 143 Ko dans le cas constaté — et SQLite répond `database disk image is malformed`. On cherche alors une migration cassée qui n'existe pas. La bonne forme :

```bash
adb exec-out run-as app.hexavore.debug cat databases/hexavore.db > hexavore.db
```

**Rapatrier aussi le `-wal`**, sinon on lit un état antérieur au dernier lancement.

Ce qu'on regarde ensuite, dans l'ordre où ça informe : `PRAGMA user_version` — c'est la version Room —, `PRAGMA integrity_check`, le nombre de lignes par table, puis **les index**, qu'aucune validation Room ne vérifie ([D60](11-decisions.md#d60--un-objectif-est-calculé-ou-saisi-et-on-consulte-avant-de-corriger---validée), [D62](11-decisions.md#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée)).

**Ce qui dit quelles migrations ont vraiment tourné** n'est pas la version d'arrivée mais **l'âge des lignes**. Une base recréée de zéro arrive en version courante sans avoir migré quoi que ce soit. Comparer la plus ancienne `created_at` à la date du commit qui a introduit chaque `VERSION` nomme la chaîne exacte que ces données ont traversée — et `firstInstallTime` de `dumpsys package` dit depuis quand l'application est là.

---

## Intégration continue

GitHub Actions.

**Sur chaque pull request** — cible : moins de 8 minutes.

```
ktlint → detekt → tests unitaires JVM → tests Room (Robolectric)
       → tests d'image → assembleDebug → rapport de couverture
```

**Sur `main`** : ce qui précède, plus les tests instrumentés sur émulateur API 26 et API 34 (la borne basse et une borne haute — les bugs de compatibilité vivent aux extrémités).

**Sur un tag `v*`** : build release signé, `bundleRelease` pour le Play Store, APK universel pour GitHub Releases, notes de version extraites du `CHANGELOG`, publication en brouillon sur la piste interne du Play Store.

Les clés de signature sont des secrets de dépôt. Le trousseau n'est **jamais** versionné, et une règle `.gitignore` explicite le rappelle.

---

## Variantes de build

Deux `productFlavors` sur la dimension `distribution`, parce que le règlement du Play Store interdit les liens de don externes pour un développeur qui n'est pas une association reconnue.

| | `github` | `play` |
|---|---|---|
| Lien de don | affiché | absent du binaire |
| Vérification de mise à jour | comparaison à la dernière release GitHub | déléguée au Play Store |
| Sauvegarde Drive | oui | oui |
| Identifiant applicatif | identique | identique |

Le lien de don n'est pas masqué à l'exécution : il est absent de la variante `play`, via une implémentation différente de `DonationLinkProvider`. Un contenu simplement caché reste dans l'APK et reste détectable lors de l'examen.

Types de build : `debug` (journalisation détaillée, suffixe `.debug` pour cohabiter), `release` (R8 avec réduction de ressources, journalisation neutralisée).

---

## Identité de l'application

Trois identifiants à ne pas confondre.

| | Exemple | Modifiable ? |
|---|---|---|
| **Nom affiché** | Hexavore | oui, à tout moment |
| **`applicationId`** | `app.hexavore` | **non**, dès la première publication sur le Play Store |
| **Paquets Kotlin** | `app.hexavore.feature.home` | oui, simple remaniement |

L'`applicationId` est l'identité de l'application pour Android et pour le Play Store — c'est lui qu'on lit dans l'URL d'une fiche (`play.google.com/store/apps/details?id=…`). Le changer après publication crée **une application entièrement nouvelle** : nouvelle fiche, zéro installation, et les utilisateurs existants ne reçoivent plus aucune mise à jour. Aucune procédure de renommage n'existe.

Valeur retenue : **`app.hexavore`**, DNS inversé de `hexavore.app`.

Deux précisions qui évitent des malentendus :

- **Rien ne vérifie ce nom.** Android ne fait aucune résolution DNS ; la convention du DNS inversé n'existe que pour garantir l'unicité mondiale. L'utiliser avant de posséder le domaine ne casse rien.
- **Mais le domaine doit être sécurisé avant la première publication.** Tant que rien n'est sur le Play Store, changer d'identifiant reste un remaniement mécanique. Après, c'est définitif.

L'identifiant ne mentionne ni pseudonyme, ni hébergeur : il survit à un changement de propriétaire, à un passage en association, ou à un départ de GitHub.

---

## Signature

`keystore.properties`, à la racine, dit où est la clé de téléversement et comment l'ouvrir. Le fichier et le trousseau sont ignorés par git, et ils doivent le rester : une clé de signature dans un historique public est une clé perdue. `keystore.properties.example` sert de modèle.

**Le fichier peut manquer, et le build réussit quand même.** La CI construit, analyse et teste sans jamais signer ; un `release` qui exigerait la clé ferait échouer le vert sur chaque machine qui ne publie pas. Sans elle, `bundleRelease` rend un bundle **non signé**, ce qui est exactement ce qu'on veut : le Play Store le refuse, donc personne ne peut le prendre pour une version publiable.

**Le chemin s'écrit avec des barres obliques, même sous Windows.** Un fichier `.properties` traite la contre-oblique comme un échappement : `C:\\Users\\moi\\cle.jks` y est lu `C:Usersmoicle.jks`, et Gradle cherche un fichier sous un nom que personne ne reconnaît. Le piège a été rencontré, pas supposé.

**Deux clés, et une seule se rattrape.** La signature d'applications par Google Play sépare la clé de *téléversement*, qui prouve qu'une mise à jour vient de son auteur, et la clé d'*application*, que Google détient et qui signe ce que les téléphones installent. Perdre la première se règle avec le support ; perdre la seconde, sans ce mécanisme, condamne l'application. Il s'active à la création de la fiche, et pas après.

---

## Versionnement

**SemVer** pour le nom (`1.4.2`), `versionCode` entier strictement croissant, calculé depuis le tag Git.

`CHANGELOG.md` au format Keep a Changelog, rédigé pour des utilisateurs et non pour des développeurs : *« Le scan reconnaît maintenant les codes-barres à 8 chiffres »*, pas *« refactor du BarcodeAnalyzer »*.

Les notes du Play Store sont générées depuis le changelog via les métadonnées Fastlane, versionnées dans `fastlane/metadata/android/{fr-FR,en-US}/`.

---

## Feuille de route

Ordre choisi pour qu'une version utilisable existe le plus tôt possible, et que chaque étape soit livrable même si la suivante n'arrive jamais.

| Version | Contenu | Livrable |
|---|---|---|
| **0.1** | Modèle de données, onboarding, calcul d'objectifs, journal manuel, recherche CIQUAL, design system | Déjà utilisable au quotidien |
| **0.2** | Scan de code-barres, Open Food Facts, cache, aliments personnels | Le mode de saisie le plus fréquent |
| **0.3** | IA photo et texte, fournisseurs, résolution, écran de validation | La fonctionnalité différenciante |
| **0.4** | Calendrier étendu, journée passée, journal de poids, adaptation hebdomadaire | Le suivi dans la durée |
| **0.5** | Sauvegarde Drive, export/import, chiffrement optionnel | Les données deviennent sûres |
| **1.0** | Accessibilité, traduction anglaise, tests d'image, `LICENSE`, `CONTRIBUTING` | Application finie, distribuée en APK via GitHub Releases |
| **1.1** | Widget, favoris avancés, contribution à Open Food Facts | |
| **Play** | Domaine, politique de confidentialité, formulaire Data Safety, compte développeur, 12 testeurs pendant 14 jours | Étape distincte et facultative, déclenchée quand l'application le mérite — voir D14 dans [11](11-decisions.md) |

La publication sur le Play Store est délibérément sortie de la 1.0 : elle dépend d'un domaine, d'un compte payant et d'un recrutement de testeurs, c'est-à-dire de trois choses qui n'ont rien à voir avec la qualité du logiciel. Une 1.0 distribuée en APK est une vraie 1.0.

La 0.1 contient déjà le design system complet. Repousser le style à la fin d'un projet « néon » revient à ne jamais le faire, et à découvrir trop tard que la structure des écrans ne s'y prête pas.

---

## Contribution

`CONTRIBUTING.md` couvre :

- Comment construire le projet (JDK 17, Android Studio, `./gradlew assembleGithubDebug`).
- La règle des dépendances : `:domain` reste pur, tout PR qui y ajoute une dépendance Android est refusé sans discussion.
- La table des portions (`servings.csv`) comme point d'entrée pour contribuer sans écrire de Kotlin — c'est le fichier le plus utile et le plus accessible du dépôt.
- Format des messages de commit : Conventional Commits, qui alimente le changelog.
- Ce qui ne sera pas accepté : SDK d'analytics, dépendance à un service payant, collecte de données, fonctionnalité exigeant un serveur.

Modèles d'issue : bogue (avec version, appareil, étapes), demande de fonctionnalité (avec le besoin avant la solution), donnée nutritionnelle incorrecte (redirigée vers Open Food Facts ou vers `servings.csv` selon la source).

---

## Documentation

Ce dossier `docs/` **est** la documentation de conception. Il est versionné avec le code et mis à jour dans le même PR que le changement qu'il décrit. Une documentation qui dérive du code est pire que pas de documentation : elle ment avec autorité.

Dans le code, on documente **pourquoi**, jamais **quoi** :

```kotlin
// Non : le code le dit déjà.
/** Calcule le BMR. */

// Oui : ça, le code ne le dit pas.
/**
 * Mifflin-St Jeor. Retenue plutôt que Harris-Benedict pour sa meilleure précision sur les
 * populations actuelles, et plutôt que Katch-McArdle qui exige un taux de masse grasse
 * dont on ne dispose pas. Marge d'erreur ≈ ±10 % : c'est l'ajustement hebdomadaire qui
 * corrige, voir docs/03-nutrition-calculs.md.
 */
```

KDoc obligatoire sur tout ce qui est public dans `:domain` et sur chaque port. Ailleurs, au jugé — un nom clair vaut mieux qu'un commentaire qui l'explique.

### Les ancres de `docs/11`, et la seule autorité en la matière

Un titre de décision finit par `· ✓ validée`. Les deux symboles **disparaissent** du slug, mais leurs **trois** espaces deviennent **trois** tirets : l'ancre est `#dNN--titre---validée`, et non `--validée`. Le tiret cadratin qui suit le numéro produit de même un double tiret. Cinquante liens ont été cassés pour cette raison.

Ne pas deviner. La seule autorité est l'API de rendu, qui donne l'ancre exacte en une commande :

```bash
gh api -X POST markdown -f mode=markdown -f text='## D59 — Un titre · ✓ validée'
```
