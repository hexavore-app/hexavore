# 06 — Architecture

## Principe directeur

Le métier ne connaît personne ; tout le monde connaît le métier.

Le module `:domain` est du Kotlin pur — pas d'`import android.*`, pas de Room, pas de Retrofit, pas de Compose. Il définit ce que l'application *sait faire* et, sous forme d'interfaces, ce dont elle *a besoin*. Les autres modules fournissent les implémentations.

C'est l'inversion de dépendance appliquée à l'échelle du projet, et c'est ce qui permet de tester toute la logique métier en quelques millisecondes, sans émulateur.

```
       ┌──────────────────────────────────────────────┐
       │   :feature:*   (Compose + ViewModels)        │
       └───────────────────┬──────────────────────────┘
                           │ dépend de
       ┌───────────────────▼──────────────────────────┐
       │   :domain      (Kotlin pur — cas d'usage,    │
       │                 modèles, PORTS)              │
       └───────────────────▲──────────────────────────┘
                           │ implémente
       ┌───────────────────┴──────────────────────────┐
       │   :data:*  ·  :integration:*   (ADAPTATEURS) │
       └──────────────────────────────────────────────┘
```

La flèche du bas remonte : `:data` dépend de `:domain`, jamais l'inverse. Un `:feature` ne connaît aucun adaptateur ; il ne voit que des cas d'usage.

---

## Modules Gradle

```
:app                        Application, graphe Hilt racine, navigation, variantes

:core:model                 Modèles partagés, Kotlin pur
:core:common                Result, implémentations de Clock, DispatcherProvider et
                            IdGenerator, extensions de formatage
:core:designsystem          Thème néon, tokens, composants Compose réutilisables
:core:database              Room : entités, DAO, migrations, base CIQUAL embarquée
:core:datastore             Préférences (DataStore) et stockage chiffré des clés
:core:testing               Fakes, règles JUnit, jeux de données de test

:domain                     Cas d'usage + ports, dont Clock et DispatcherProvider.
                            Aucune dépendance Android.

:data:diary                 Journal : plats, entrées, favoris
:data:food                  Catalogue d'aliments (CIQUAL + cache OFF + personnels)
:data:profile               Profil, objectifs, poids
:data:settings              Cles d IA, chiffrees par le Keystore
:data:backup                Instantané, sérialisation, planification

:integration:openfoodfacts  Client Retrofit + DTO + correspondances
:integration:ai             FoodRecognizer et ses six implémentations
:integration:drive          Google Drive appDataFolder
:integration:scanner        CameraX + zxing-cpp, et la seule composable d un adaptateur

:feature:onboarding
:feature:scan               Modale de scan, ses quatre etats
:feature:capture            Modales photo et texte : une analyse, un depot, deux modes
:feature:home               Accueil, journée, calendrier
:feature:entry              Écran de validation
:feature:search             Recherche, récents, favoris, aliment personnel
:feature:weight
:feature:settings           Profil et objectifs, recalcul, compteurs fixés à la main

:tooling:ciqual-import      Trois tâches Gradle, hors APK : XML ANSES → SQLite, les
                            titres courts (D88) et les teneurs complétées (D89)
```

Un artefact vit à côté de cette liste sans y figurer : `build-logic/detekt-rules`, qui contient les trois règles d'analyse statique de [10](10-qualite-et-livraison.md#analyse-statique). Ce n'est pas un module du projet mais un **build inclus** : son code tourne sur la JVM de Gradle, pas sur un téléphone, et il n'a rien à faire dans le graphe de dépendances de l'application ([D16](11-decisions.md#d16--les-règles-detekt-vivent-dans-un-build-inclus---par-défaut)).

**Où vivent `Clock` et `DispatcherProvider`.** Les *interfaces* sont dans `:domain` : ce sont des ports, et un port appartient au métier qui l'exige. Les *implémentations* — celles qui lisent vraiment l'horloge de l'appareil — sont dans `:core:common`. La règle detekt qui interdit `LocalDate.now()` ailleurs nomme explicitement les fichiers autorisés.

### Pourquoi autant de modules

Trois raisons concrètes, pas un goût pour la symétrie :

1. **La compilation.** Modifier un écran ne recompile pas la couche données. Sur un projet Compose, c'est la différence entre 8 secondes et 90.
2. **Les dépendances deviennent des faits.** `:domain` ne *peut pas* importer Room : Gradle le refuse. Une règle appliquée par l'outillage tient dans le temps ; une règle écrite dans un fichier de conventions, non.
3. **La testabilité.** `:domain` se teste en JVM pure.

Règle de découpage : **un module = une raison de changer**. Si deux modules changent toujours ensemble, ils n'en font qu'un — la fusion est alors une amélioration, pas un renoncement.

---

## Les principes SOLID, appliqués

Pas une récitation : où chaque principe se manifeste, et ce qui indique qu'il est violé.

### S — Responsabilité unique

Une classe, une raison de changer.

Contre-exemple typique à éviter, courant dans les applications Android : un `FoodRepository` qui appelle l'API, parse le JSON, écrit en base, convertit les unités et formate pour l'affichage. Il change quand l'API change, quand le schéma change, quand l'affichage change.

Ici, la chaîne est éclatée :

| Classe | Sa seule raison de changer |
|---|---|
| `OpenFoodFactsApi` | Le contrat HTTP d'Open Food Facts |
| `OffProductMapper` | La correspondance DTO → modèle de domaine |
| `FoodDao` | Le schéma de la base |
| `FoodCatalogRepository` | La stratégie de résolution local/distant |
| `QuantityConverter` | Les règles de conversion d'unités |
| `MacroFormatter` | La présentation d'un nombre à l'écran |

**Signal d'alerte** : un nom de classe contenant « et », ou une classe de plus de 200 lignes sans raison forte.

### O — Ouvert / fermé

Ajouter un comportement ne doit pas modifier le code existant.

Trois points d'extension identifiés dès la conception, parce que ce sont les trois choses qui vont réellement bouger :

- **Fournisseur d'IA** → une classe + une entrée d'énumération ([05](05-ia.md#ajouter-un-fournisseur)).
- **Destination de sauvegarde** → une implémentation de `BackupTarget`. Drive et fichier local en sont déjà deux ; WebDAV ou Nextcloud n'exigeraient rien d'autre.
- **Stratégie d'objectif** → une implémentation de `MacroDistributionPolicy`. « Cétogène » ou « végétarien haute-protéine » s'ajoutent sans toucher au calculateur.

**Signal d'alerte** : un `when` sur un type qu'on doit compléter à chaque ajout, ailleurs que dans la fabrique prévue pour ça.

### L — Substitution de Liskov

Toute implémentation d'un port doit être interchangeable sans que l'appelant s'en aperçoive.

Application concrète : `FoodRecognizer` ne déclare **aucune** exception. Toutes les implémentations retournent `Result`, et les erreurs sont un type fermé commun (`AiError`). Une implémentation qui lèverait `IOException` là où une autre retourne `Result.failure` casserait tous les appelants — c'est la violation classique, et elle est rendue impossible par la signature.

Même exigence sur `BackupTarget` : Drive comme fichier local se comportent pareil face à une écriture impossible.

**Signal d'alerte** : un appelant qui teste le type concret de son port (`if (target is DriveBackupTarget)`).

### I — Ségrégation des interfaces

Un client ne doit pas dépendre de méthodes qu'il n'utilise pas.

L'interface fourre-tout `FoodRepository` est refusée. À la place, quatre ports étroits :

```kotlin
fun interface FoodLookup      { suspend fun byId(id: FoodId): Food? }
fun interface BarcodeLookup   { suspend fun byBarcode(code: Barcode): Food? }
interface     FoodSearch      { fun search(q: String, f: FoodFilter, limit: Int): Flow<List<Food>> }
interface     RecentFoods     { fun observeRecent(limit: Int): Flow<List<Food>> }
interface     FavoriteFoods   { fun observeFavorites(): Flow<List<Food>>
                                suspend fun setFavorite(id: FoodId, favorite: Boolean) }
interface     FoodStore       { suspend fun place(food: Food): Food
                                suspend fun save(food: Food): FoodId
                                suspend fun delete(id: FoodId) }
interface     FoodUsage       { suspend fun remember(foods: Collection<Food>, at: Instant) }
fun interface FoodCitations   { suspend fun count(id: FoodId): Int }
```

**`FoodCitations` est un port du catalogue dont l'adaptateur vit dans `:data:diary`**, et c'est la seule inversion de ce genre du projet. Le compte se dérive des entrées de journal ; l'avoir laissé sur `FoodStore` obligeait son faux à exposer une carte posée à la main, là où Room comptait de vraies lignes. Un port qui ne peut pas être doublé honnêtement est un port mal placé ([D71](11-decisions.md#d71--le-compte-des-citations-quitte-le-catalogue-parce-quun-faux-ne-peut-pas-linventer---validée)).

`BarcodeLookup` existe depuis la tranche 5, et **il a son propre adaptateur** : c'est la seule lecture du catalogue qui ait à savoir que `source_ref` range deux espaces de noms dans une même colonne ([D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée)). **Il ne se confond pas avec `ProductSource`**, le port distant qui interroge Open Food Facts : le catalogue local répond en quelques millisecondes ou ne répond pas, l'autre demande le réseau et rend trois issues — trouvé, inconnu, injoignable — dont aucune n'est une exception ([D63](11-decisions.md#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée)). Les deux se lisent dans cet ordre, et c'est ce qui rend le deuxième scan instantané.

Les autres existent, et un seul adaptateur les implémente tous — la séparation paie du côté des **appelants**, pas du côté de l'implémentation. `LogDish` ne dépend que de `FoodUsage`, l'écran de recherche que de quatre ports sur six.

`FoodSearch` rend un `Flow` et non une liste : le catalogue change sous les yeux de celui qui regarde ses résultats, et une lecture unique ne peut pas se démentir ([D53](11-decisions.md)). Son `FoodFilter` est une classe du domaine et non une clause SQL — le filtre par rayon est une règle de ce que l'utilisateur voit, et une règle se teste sur la JVM ([D54](11-decisions.md)).

Un seul adaptateur pour six ports a une contrepartie que le projet a payée deux fois : la **seconde** implémentation, celle en mémoire, peut diverger sans que rien ne le dise. Elle est donc tenue par un jeu de tests de contrat exécuté sur les deux, `FoodCatalogContract` dans `:data:food` ([D53](11-decisions.md)).

L'écran de scan ne dépend que de `BarcodeLookup`. Son test a donc besoin d'une fonction, pas d'un faux objet à quinze méthodes. C'est là que ce principe se paie : dans le volume de code de test.

Une même classe peut implémenter plusieurs de ces ports — la ségrégation concerne les interfaces vues par les clients, pas le nombre de classes.

### D — Inversion de dépendance

Déjà décrite en tête. Sa traduction Gradle :

```kotlin
// :domain/build.gradle.kts
dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
    // et rien d'autre. Aucun plugin Android.
}
```

`:domain` est un module Kotlin/JVM, pas un module Android. La contrainte est structurelle : personne ne peut la contourner par distraction.

---

## Cas d'usage

Une classe par intention métier, un `operator fun invoke`, une responsabilité.

```kotlin
class LogFoodEntry(
    private val diary: DiaryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(request: LogRequest): Result<EntryId> = ...
}

class CalculateDailyGoal(
    private val energy: EnergyExpenditureCalculator,
    private val macros: MacroDistributionPolicy,
    private val safety: GoalSafetyPolicy,
) {
    operator fun invoke(profile: Profile, target: WeightTarget): GoalProposal = ...
}

class SuggestGoalAdjustment(
    private val weights: WeightLog,
    private val diary: DiaryRepository,
    private val goals: Goals,
    private val profiles: Profiles,
    private val settings: AdjustmentSettings,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<AdjustmentSuggestion?> = ...
}
```

~~`SuggestGoalAdjustment` lisait trois dépôts et rendait une valeur.~~ **Un flux et cinq sources** ([D95](11-decisions.md#d95--lajustement-se-propose-ne-sapplique-jamais-et-se-tait-presque-toujours---validée)) : la carte paraît et disparaît sur deux écrans à la fois, donc une lecture unique aurait obligé chacun à relire ; la correction repasse par les garde-fous, qui demandent la dépense — donc le profil et l'objectif ; et un refus doit se rappeler, ce que rien d'autre ne porte.

Le `Clock` injecté n'est pas un excès de zèle : sans lui, la moitié de la logique de cette application — jours, semaines, tendances — serait intestable ou testée avec des `Thread.sleep`.

Liste des cas d'usage prévus : `LogDish`, `UpdateDish`, `DeleteEntry`, `RestoreDish`, `GetDishDraft`, `CreateDraft`, `GetDaySummary`, `GetDateRangeSummary`, `SearchFoods`, `LookupBarcode`, `RecognizeFood`, `ResolveNutrition`, `CalculateDailyGoal`, `OverrideGoal`, `SuggestGoalAdjustment`, `RecordWeight`, `GetWeightTrend`, `SaveFavoriteMeal`, `ApplyFavoriteMeal`, `CreateBackup`, `RestoreBackup`, `ExportData`, `ImportData`.

Les six premiers portent le vocabulaire du **plat** et non de la ligne : une ligne n'entre jamais seule dans le journal, elle appartient à un plat qui porte la source et l'heure. Un `LogFoodEntry` aurait laissé croire le contraire ([D43](11-decisions.md)).

---

## Présentation

Un `ViewModel` par écran, exposant un `StateFlow<UiState>` unique et acceptant des événements typés.

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class  Content<T>(val data: T) : UiState<T>
    data class  Error(val message: UiText, val retry: (() -> Unit)?) : UiState<Nothing>
}
```

Un état unique plutôt que trois champs indépendants (`isLoading`, `data`, `error`) : les combinaisons impossibles — chargement *et* erreur — cessent de compiler.

`UiText` encapsule soit une ressource string, soit un texte brut. Les `ViewModel` ne manipulent jamais de `Context`, donc restent testables en JVM.

Les composables sont sans état : ils reçoivent un état et émettent des événements. Chaque écran a une variante « stateless » exposée pour les aperçus et les tests d'image.

---

## Injection de dépendances

Hilt. Portées :

- `@Singleton` — base, DataStore, OkHttp, dépôts.
- `@ViewModelScoped` — rien par défaut ; on n'ajoute une portée qu'avec une raison.

Chaque module Gradle expose son propre module Hilt, qui lie ses adaptateurs aux ports du domaine. `:app` ne fait qu'assembler.

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class FoodDataModule {
    @Binds abstract fun barcodeLookup(impl: FoodCatalogRepository): BarcodeLookup
    @Binds abstract fun foodSearch(impl: FoodCatalogRepository): FoodSearch
}
```

---

## Concurrence

- `suspend` partout dans le domaine ; aucun `Flow` dans les ports d'écriture.
- `Flow` pour l'observation de données (journal du jour, récents, tendance de poids).
- Dispatchers injectés via une interface `DispatcherProvider` — un test remplace tout par `UnconfinedTestDispatcher` sans règle globale.
- Aucun `GlobalScope`. Les travaux qui doivent survivre à l'écran passent par WorkManager (sauvegarde), pas par un scope détaché.

---

## Navigation

Navigation Compose avec routes typées (`@Serializable`). Les quatre modales de saisie sont des `bottom sheet destinations`, pas des écrans : le retour arrière les referme sans quitter l'accueil.

L'écran de validation est atteignable depuis les quatre modales et depuis le journal ; il prend un `EntryDraft` en argument et ignore d'où il vient — ce qui le rend testable isolément.

---

## Journalisation

`Timber` en `debug`, arbre vide en `release`. **Aucun crash reporting automatique** : ce serait de la collecte de données, exclue par le périmètre. Les réglages proposent un bouton « Copier le rapport de diagnostic » qui produit un texte anonymisé, que l'utilisateur colle lui-même dans un ticket s'il le souhaite.

---

## Ce qu'il ne faut pas faire

Consigné parce que ce sont les dérives qui arrivent réellement sur ce type de projet :

- Faire remonter un type Room ou un DTO Retrofit jusqu'à un composable. Les correspondances existent pour ça.
- Ajouter un port « au cas où ». Une interface avec une seule implémentation et aucune perspective d'une seconde est un coût sans contrepartie.
- Mettre de la logique métier dans un `ViewModel`. Si un calcul mérite un test, il appartient à `:domain`.
- Sur-abstraire les écrans. Le domaine est stable et mérite des ports ; l'interface bouge tout le temps et doit rester directe.
