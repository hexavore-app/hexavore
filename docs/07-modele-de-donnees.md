# 07 — Modèle de données

Base Room `hexavore.db`, plus une base annexe `ciqual.db` livrée en lecture seule dans les assets.

## Vue d'ensemble

```
profile ──1:N── weight_entry
   │
   └──1:N── goal (versionné)

dish ──1:N── food_entry ──N:1── food ──1:N── food_serving
                                   │
favorite_dish ──1:N── favorite_component ──N:1──┘
```

## Conventions

- Identifiants : `String` UUIDv4, générés côté application. Un entier auto-incrémenté rendrait toute fusion de sauvegardes impossible et interdirait les identifiants stables entre appareils.
- Dates sans heure : `LocalDate` en `TEXT` ISO-8601 (`2026-08-02`). Triables en SQL, lisibles à l'œil dans un export.
- Instants : `Instant` en `INTEGER` (millisecondes UTC).
- Poids et masses : `REAL`, toujours en **grammes** ou **kilogrammes**. Le système impérial est une conversion d'affichage, jamais de stockage — sinon les arrondis s'accumulent.
- Tout `created_at` / `updated_at` en millisecondes UTC : ce sont eux qui rendront une fusion de sauvegardes possible un jour.

---

## Les deux invariants qui structurent tout

### 1. Une entrée de journal fige ses macros

`food_entry` **duplique** les six valeurs nutritionnelles au moment de l'enregistrement, au lieu de les recalculer depuis `food`.

C'est une dénormalisation assumée. Sans elle :

- un fabricant reformule son produit, Open Food Facts est mis à jour, et le journal d'il y a six mois change tout seul ;
- une correction de valeur repeint rétroactivement tout l'historique ;
- la suppression d'un aliment personnel effacerait des repas déjà consommés.

Un journal alimentaire est un **registre d'événements**. Ce qui est écrit est écrit.

`food_id` reste renseigné, mais comme un lien de provenance — utile pour « ré-ajouter la même chose », inutile au calcul.

### 2. Un objectif n'est jamais modifié, il est remplacé

Voir [03](03-nutrition-calculs.md#versionnement-des-objectifs). Chaque changement crée une ligne ; l'ancienne reçoit `ended_at`. La journée du 12 mars est jugée avec l'objectif du 12 mars.

---

## Tables

### `profile`

Ligne unique, `id = "singleton"`.

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | toujours `"singleton"` |
| `birth_date` | TEXT | l'âge se déduit, il ne se stocke pas |
| `sex` | TEXT | `MALE` · `FEMALE` · `UNSPECIFIED` |
| `height_cm` | REAL | |
| `activity_level` | TEXT | `SEDENTARY` … `VERY_ACTIVE` |
| `unit_system` | TEXT | `METRIC` · `IMPERIAL` — affichage seulement |
| `created_at`, `updated_at` | INTEGER | |

Stocker la date de naissance et non l'âge évite un objectif qui se périme silencieusement.

### `weight_entry`

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `date` | TEXT | **UNIQUE** — une pesée par jour, la dernière remplace |
| `weight_kg` | REAL | |
| `created_at` | INTEGER | |

Index sur `date DESC` : toutes les lectures sont des fenêtres récentes.

### `goal`

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `started_at` | TEXT | date de prise d'effet |
| `ended_at` | TEXT NULL | `NULL` = objectif courant |
| `origin` | TEXT | `CALCULATED` · `MANUAL` · `ADJUSTMENT` — **d'où viennent les six chiffres** |
| `target_weight_kg` | REAL NULL | |
| `target_date` | TEXT NULL | |
| `strategy` | TEXT | `LOSE` · `MAINTAIN` · `GAIN` |
| `kcal` | INTEGER | |
| `protein_g`, `carb_g`, `sugar_g`, `fat_g`, `fiber_g` | REAL | |
| ~~`manual_fields`~~ | TEXT | liste des champs édités à la main, séparés par `,` — **retirée en migration 3 → 4** |
| `created_at` | INTEGER | |

~~`manual_fields` protège le travail de l'utilisateur : un recalcul ne réécrit pas un champ qu'il a fixé lui-même, sauf accord explicite.~~ C'est **`origin` qui porte cette protection**, et il la porte pour l'objectif entier ([D60](11-decisions.md)) : un objectif est calculé — il suit le profil, le poids visé et l'échéance — ou saisi à la main, et alors plus rien ne le recalcule. Le verrou par compteur a existé le temps d'une décision ; il obligeait l'écran à expliquer un troisième état et le poids cible à piloter trois compteurs sur six.

`target_weight_kg` et `target_date` restent renseignés en mode manuel, où ils ne pilotent rien : ils décrivent le **cap annoncé**, dont le journal de poids tire sa trajectoire.

**Invariant** : au plus une ligne avec `ended_at IS NULL`. Tenu par l'index unique sur `active_key`, et éprouvé par un test de migration — la validation de schéma de Room ne voit pas un index perdu dans une recréation de table.

### `food`

> **Créée en tranche 3** par la migration 1 → 2, avec `food_entry.food_id` ([D34](11-decisions.md)). `is_liquid` et `fetched_at` l'ont rejointe en migration 5 → 6, avec le cache qui les remplit ; `density` et `user_edited_fields` n'y sont toujours pas. La ligne de partage n'est pas « ce que le cache écrit » mais **ce qui se perd si on ne le note pas tout de suite** : les deux premières ne sont connaissables qu'au moment de la récupération, les deux autres s'ajouteront le jour où quelque chose les lira ([D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée)). Une colonne `name_search` s'y ajoute en revanche : le nom sous sa forme cherchable, calculé à l'écriture ([D49](11-decisions.md)).

Catalogue unifié : CIQUAL importé à la demande, produits Open Food Facts mis en cache, aliments personnels.

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `source` | TEXT | `CIQUAL` · `OFF` · `CUSTOM` |
| `source_ref` | TEXT NULL | code CIQUAL ou code-barres |
| `name` | TEXT | |
| `brand` | TEXT NULL | |
| `kcal_100` | REAL NULL | |
| `protein_100`, `carb_100`, `sugar_100`, `fat_100`, `fiber_100` | REAL NULL | |
| `saturated_fat_100`, `salt_100` | REAL NULL | stockés, non affichés en v1 |
| `default_serving_g` | REAL NULL | |
| ~~`density`~~ | REAL | g/ml, défaut `1.0` — **pas encore là**, arrive avec le résolveur |
| `is_liquid` | INTEGER **NULL** | `NULL` veut dire *on ne sait pas*, jamais *solide* |
| ~~`user_edited_fields`~~ | TEXT | protégé contre l'écrasement au rafraîchissement — **pas encore là** |
| `last_used_at` | INTEGER NULL | alimente « Récents » |
| `use_count` | INTEGER | alimente le classement de recherche |
| `is_favorite` | INTEGER | |
| `fetched_at` | INTEGER NULL | pour la péremption du cache Open Food Facts |
| `created_at`, `updated_at` | INTEGER | |

`NULL` signifie **inconnu**, jamais zéro. L'interface affiche « — » et non « 0 g ». Cette distinction est testée : c'est l'erreur la plus facile à introduire et la plus difficile à repérer.

Index unique sur `(source, source_ref)` quand `source_ref` n'est pas nul — empêche le doublon au double scan. Il n'empêche pas, en revanche, qu'un aliment personnel et un produit en cache portent le même code-barres : c'est un cas réel — on crée la fiche hors ligne, on rescanne connecté — et c'est la lecture par code-barres qui les départage, en donnant la main à celle que l'utilisateur a saisie ([D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée)).

**`source_ref` range deux espaces de noms** : des codes de la table de l'ANSES et des codes-barres. Une seule lecture a besoin de le savoir, et elle a sa propre classe pour cette raison.

Les colonnes `saturated_fat_100` et `salt_100` existent alors que la v1 ne les affiche pas : la donnée est disponible dans les deux sources, la collecter maintenant coûte zéro et évite une migration le jour où on décide de les montrer.

### `food_serving`

> **Pas encore créée.** Les portions de CIQUAL se lisent dans `ciqual_serving` par le code source, sans copie ; une fiche personnelle a `default_serving_g`, qui couvre le cas courant. Cette table naîtra quand un aliment personnel aura besoin de plusieurs portions nommées ([D50](11-decisions.md)).

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `food_id` | TEXT FK → `food.id`, CASCADE | |
| `label` | TEXT | « 1 tranche », « 1 verre » |
| `grams` | REAL | |
| `is_default` | INTEGER | |

### `dish`

Un **plat** : plusieurs aliments, entrés en une fois. Pas de repas nommé, pas de catégorie à choisir avant d'enregistrer ([D31](11-decisions.md)).

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `date` | TEXT | journée locale à laquelle le plat est rattaché |
| `source` | TEXT | `MANUAL` · `BARCODE` · `PHOTO_AI` · `TEXT_AI` · `FAVORITE` |
| `logged_at` | INTEGER | ordonne les plats de la journée |
| `title` | TEXT NULL | le nom écrit à la main ; `NULL` dans le cas courant |
| `moment` | TEXT NULL | `BREAKFAST` · `LUNCH` · `SNACK` · `DINNER`, retenu à la saisie |
| `created_at`, `updated_at` | INTEGER | |

Index sur `(date, logged_at)` : c'est l'ordre d'affichage de l'accueil.

**`title` à `NULL` ne veut pas dire « sans titre »** ([D118](11-decisions.md)) : le plat s'appelle alors du nom de son `moment`, composé à l'affichage et numéroté quand deux plats du même moment se suivent — « Déjeuner », puis « Déjeuner 2 ». Y écrire ce nom aurait figé des mots français dans la base, là où l'utilisateur n'a rien dit, et l'aurait fait pour chaque plat.

**`moment` à `NULL` non plus** : c'est l'état des plats écrits avant que les moments existent, et l'heure du plat le dit alors. La migration ne les remplit pas — affirmer un moment que personne n'a choisi, pour des mois de journal d'un coup, serait exactement l'erreur que ce projet évite sur les valeurs nutritionnelles.

`SEARCH` a existé et n'existe plus : elle se confondait avec `MANUAL`, puisqu'un même plat mêle couramment un aliment trouvé dans la table et un autre saisi à la main ([D52](11-decisions.md)). Une base antérieure en porte encore ; elle se relit en `MANUAL`.

**`source` n'est jamais réécrite.** Un plat reste éditable à la main indéfiniment ; son origine est un fait historique, pas un état. Corriger une quantité sur une proposition de l'IA ne doit pas la faire passer pour une saisie manuelle — ce serait perdre la seule trace de ce qui a été deviné ([D32](11-decisions.md)).

Une journée sans saisie ne produit aucune ligne, ce qui permet de distinguer « rien mangé de noté » de « journée à zéro » ([02](02-parcours-et-ecrans.md#calendrier-déplié)).

**Aucune colonne pour la photo**, alors qu'un plat scanné ou analysé en garde une ([D127](11-decisions.md)). Elle vit dans `filesDir/plats/<id du plat>.jpg`, et le nom du fichier est le seul lien : le disque est la seule vérité, donc rien ne peut se désaccorder. Une colonne aurait pu annoncer une image absente — fichier effacé par ailleurs, restauration sans photos — et l'accueil aurait montré un cadre vide sans savoir pourquoi. Voir [09](09-donnees-et-sauvegarde.md#les-photos-et-le-seul-endroit-où-elles-seffacent).

### `food_entry`

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `dish_id` | TEXT FK → `dish.id`, CASCADE | |
| `food_id` | TEXT NULL FK → `food.id`, SET NULL | provenance, pas source de calcul |
| `display_name` | TEXT | figé — survit à la suppression de l'aliment |
| `quantity` | REAL | |
| `unit` | TEXT | unité choisie par l'utilisateur |
| `grams` | REAL | quantité convertie, base de tout calcul |
| `kcal` | REAL | **figé** |
| `protein_g`, `carb_g`, `sugar_g`, `fat_g`, `fiber_g` | REAL NULL | **figés** |
| `ai_confidence` | REAL NULL | |
| `is_manually_edited` | INTEGER | verrouille tout recalcul |
| `created_at`, `updated_at` | INTEGER | |

Index sur `dish_id`.

**Aucune source ici.** Elle appartient au plat : une ligne n'entre jamais dans le journal toute seule. La distinction « ce chiffre vient de CIQUAL » contre « ce chiffre est une estimation » reste nécessaire — [05](05-ia.md) prévoit qu'un plat photographié résolve certaines lignes dans les bases et estime les autres — mais elle arrive **en tranche 6**, avec le résolveur qui la produit, sous la forme minimale d'un marqueur `is_estimated`. Une colonne que rien ne remplit n'est pas une préparation, c'est du bruit.

### `favorite_dish` et `favorite_component`

Un plat enregistré pour être rejoué. C'est le seul endroit où un nom est demandé : un favori sans nom serait introuvable.

| `favorite_dish` | Type |
|---|---|
| `id` | TEXT PK |
| `name` | TEXT — tel qu'il a été tapé |
| `name_search` | TEXT **UNIQUE** — normalisé, c'est lui qui porte l'unicité |
| `use_count` | INTEGER |
| `created_at` | INTEGER |

| `favorite_component` | Type |
|---|---|
| `favorite_id`, `position` | TEXT / INTEGER — **clé composite** |
| `food_id` | TEXT FK NULL, SET NULL |
| `display_name` | TEXT |
| `quantity`, `unit`, `grams` | REAL / TEXT / REAL |
| `kcal`, `protein_g`, `carb_g`, `sugar_g`, `fat_g`, `fiber_g` | REAL NULL |

**Pas d'identifiant de composant** : un composant n'existe pas hors de son plat et n'est jamais désigné seul. La position devait de toute façon être stockée pour que l'ordre des lignes survive à un rejeu.

Un favori référence des aliments **vivants** : « mes flocons du matin » doit refléter la fiche courante quand on le rejoue. Il produit ensuite des `food_entry` qui, eux, figent leurs valeurs. La différence de traitement est intentionnelle — un modèle réutilisable d'un côté, un registre d'événements de l'autre.

**Mais les six valeurs sont enregistrées quand même** ([D62](11-decisions.md)). Une ligne tapée à la main n'a pas de fiche derrière elle, et une fiche citée peut être supprimée : elles servent de contenu dans le premier cas, de repli dans le second. Sans elles, un favori pourrait rejouer une ligne sans le moindre chiffre.

`dish.favorite_id` relie un plat du journal au favori dont il a été rejoué, en `SET NULL`. C'est lui qui rallume l'étoile en rouvrant un plat, et il **tombe dès qu'une ligne est modifiée** : le plat n'est plus celui que le favori décrit.

### `app_state`

Table clé-valeur pour ce qui ne mérite pas sa table : date de dernière sauvegarde, dernière suggestion d'ajustement présentée, version de prompt en cours, drapeau d'onboarding.

Les préférences utilisateur vont dans DataStore, pas ici. Cette table ne contient que de l'état technique, sauvegardé avec les données.

---

## Base CIQUAL embarquée

`assets/ciqual.db`, lecture seule, jamais migrée — remplacée en bloc à chaque nouvelle version de la table ANSES.

```sql
CREATE TABLE ciqual_food (
    rowid INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL, name_search TEXT NOT NULL,
    short_name TEXT, group_name TEXT, category TEXT,
    kcal_100 REAL, protein_100 REAL, carb_100 REAL,
    sugar_100 REAL, fat_100 REAL, fiber_100 REAL,
    saturated_fat_100 REAL, salt_100 REAL,
    -- Les teneurs complétées par un modèle, **jamais mêlées aux mesures**.
    kcal_100_est REAL, protein_100_est REAL, carb_100_est REAL,
    sugar_100_est REAL, fat_100_est REAL, fiber_100_est REAL
);
CREATE INDEX index_ciqual_food_category ON ciqual_food(category);
CREATE VIRTUAL TABLE ciqual_fts USING fts4(name_search, content="", tokenize=simple);
CREATE TABLE ciqual_serving (
    code TEXT NOT NULL, label TEXT NOT NULL, grams REAL NOT NULL, is_default INTEGER NOT NULL
);
CREATE INDEX index_ciqual_serving_code ON ciqual_serving(code);
```

Trois écarts avec ce qui était prévu, tous documentés en [D49](11-decisions.md) :

- **FTS4 et non FTS5**, tokenizer `simple` et non `unicode61 remove_diacritics 2`. Aucun des deux ne tient sous `minSdk 26`. Le travail est fait à l'import, dans `name_search`, et la même normalisation s'applique à la saisie.
- **L'index est sans contenu.** On n'en attend qu'un `docid`, que `rowid` relie au catalogue. Le `rowid` est donc attribué explicitement des deux côtés : le laisser à SQTLite marcherait par coïncidence.
- **Pas de colonne `density`.** CIQUAL ne la publie pas, et rien ne la calcule avant le résolveur de la tranche 6. Une colonne que rien ne remplit n'est pas une préparation. Cette base n'étant jamais migrée mais remplacée en bloc, l'ajouter le jour venu ne coûte rien.

Des colonnes se sont ajoutées depuis, et **aucune n'est copiée dans `food`** : `category`, le rayon du bandeau de recherche ([D54](11-decisions.md)), `short_name`, le titre court ([D88](11-decisions.md#d88--le-titre-court-se-fabrique-hors-de-lapplication-et-il-ne-touche-jamais-au-libellé---validée)), et les six `*_est`, les teneurs complétées ([D89](11-decisions.md#d89--une-valeur-complétée-ne-se-range-pas-où-une-valeur-mesurée-se-range---validée)). Ce sont des propriétés de la **référence** et non de la copie : les stocker dans `food` figerait la valeur du jour où la fiche a été copiée, et les corriger n'atteindrait jamais celles qu'on utilise déjà. Elles se relisent ici, par code, en un seul lot.

**Les six colonnes de complétion sont distinctes des six mesures, et c'est la règle qui commande toute la seconde passe.** Mêlées, un nouvel import de la table de l'ANSES écraserait les complétions — ou pire, les prendrait pour des mesures. La lecture préfère toujours l'originale, et `Food.estimated` retient laquelle a servi.

Une colonne ajoutée à cette base ne demande pas de migration, mais elle demande d'incrémenter `CiqualDatabase.REVISION` : c'est ce qui change le nom du fichier copié et force la recopie sur un appareil déjà installé. Sans cela, la première requête sur la colonne neuve échouerait chez l'utilisateur et jamais en développement, où l'installation est toujours fraîche.

Un aliment CIQUAL est **copié** dans `food` la première fois qu'il est réellement consommé. Copier les 3 484 lignes à l'installation gonflerait la base, les sauvegardes et la recherche avec 99 % de contenu jamais utilisé.

---

## Requêtes structurantes

**Résumé d'un jour** — la requête la plus fréquente de l'application, appelée à chaque défilement du calendrier :

```sql
SELECT d.id, d.source, d.logged_at,
       SUM(e.kcal)      AS kcal,
       SUM(e.protein_g) AS protein,
       SUM(e.carb_g)    AS carb,
       SUM(e.sugar_g)   AS sugar,
       SUM(e.fat_g)     AS fat,
       SUM(e.fiber_g)   AS fiber
FROM dish d LEFT JOIN food_entry e ON e.dish_id = d.id
WHERE d.date = :date
GROUP BY d.id ORDER BY d.logged_at;
```

⚠️ `SUM` traite `NULL` comme absent, ce qui est correct, mais **perd l'information qu'une valeur manquait**. Le domaine remonte donc les lignes et agrège lui-même, en retenant quels totaux sont minorés ([D29](11-decisions.md)). Cette requête sert au calendrier, où seul l'ordre de grandeur compte.

**Objectif actif à une date** :

```sql
SELECT * FROM goal
WHERE started_at <= :date AND (ended_at IS NULL OR ended_at > :date)
ORDER BY started_at DESC LIMIT 1;
```

**Récents** : `food` trié par `last_used_at DESC`, limité à 20, `WHERE last_used_at IS NOT NULL`. Le filtre n'est pas une précaution : une fiche créée et jamais servie n'a rien à faire là, et l'y mettre à sa date de création mélangerait deux informations différentes.

**Recherche dans le catalogue local** : `LIKE '%…%'` sur `name_search`, sans index plein texte. Quelques centaines de fiches contre 3 484 pour CIQUAL, qui a le sien : une seconde table FTS demanderait ses triggers de synchronisation et un second chemin de normalisation à tenir aligné, pour balayer six cents chaînes courtes.

Toutes les requêtes d'affichage renvoient un `Flow` : Room notifie sur invalidation, aucun rafraîchissement manuel n'est nécessaire.

---

## Migrations

Migrations Room explicites, **jamais** `fallbackToDestructiveMigration`. Perdre le journal alimentaire d'un utilisateur est une faute non rattrapable.

- Schémas exportés dans `:core:database/schemas/`, versionnés dans Git.
- Chaque migration testée avec `MigrationTestHelper` : on part du schéma N avec des données réelles, on migre, on vérifie le contenu — pas seulement que ça ne plante pas.
- Un test parcourt toute la chaîne `1 → N` sur une base peuplée.

Règle de conception : **préférer une colonne nullable à une table nouvelle**, et une table nouvelle à un renommage. Le renommage est ce qui casse les sauvegardes.

---

## Empreinte

Estimation pour un utilisateur régulier sur un an :

| | Volume |
|---|---|
| `food_entry` | ~5 500 lignes (15/jour) ≈ 1,5 Mo |
| `food` | ~600 aliments ≈ 0,3 Mo |
| `weight_entry` | ~300 lignes, négligeable |
| Base applicative | **≈ 2 Mo** |
| `ciqual.db` (assets) | ≈ 4 Mo |
| Sauvegarde compressée | **< 400 Ko** |

Aucune stratégie d'archivage n'est nécessaire à cette échelle, et aucune n'est prévue : ce serait de la complexité pour un problème qui n'existe pas.
