# Reprise — le tour d'avant publication, après D163

Ce fichier dit **où en est le travail et comment le reprendre**, sans rien supposer de
la conversation qui l'a produit. Il se supprime quand ses chantiers sont faits : ce
qui doit durer vit dans `docs/`, et les décisions dans `docs/11-decisions.md`.

---

## 1. État de la branche

- **Branche** : `fix/la-courbe-le-tour-et-le-premier-ecran`, partie de `main` après
  la PR #100 (D157, version 0.9.0). Elle porte **D158 à D163** en un seul commit :
  les décisions et leurs raisons sont dans `docs/11-decisions.md`, du titre
  « D158 » à la fin du fichier.
- **Version** : toujours `0.9.0` / `versionCode 7` dans `gradle/libs.versions.toml`.
  La monter est une décision de publication, pas prise ici.
- **Vérifié** : `gradlew clean check` vert, sans cache de build, cas exécutés comptés ;
  campagne de défaite de D162 (12 sabotages, 12 tombés) et de D163 (résultat dans
  l'entrée D163).
- **Pas vérifié** : aucune correction de D163 n'a été revue à l'émulateur **après**
  correction (les défauts, eux, l'ont été avant) ; plusieurs points de D162 non plus —
  la liste est dans les entrées D162 et D163, § « Ce que le vert ne prouve pas ». Les
  tests instrumentés (`connectedAndroidTest`) n'ont pas tourné.
- **Site** (`hexavore-site`, dépôt à part) : la phrase « estimations » de D162 sur la
  page d'accueil, dans les deux langues. Voir § 5.

## 2. Règles de travail sur ce dépôt (à relire avant de toucher à quoi que ce soit)

- **Appareils** : n'utiliser **que** l'émulateur `emulator-5554` (AVD `Hexavore_API36`).
  Un téléphone personnel peut être branché (série `c0104279`) : ne jamais le cibler,
  toujours `adb -s emulator-5554`. Ne pas lancer `connectedAndroidTest`.
- **Clés d'API** : ne jamais en taper dans un champ, ni en écrire une nulle part. Les
  fonctions d'IA se testent quand l'auteur a collé sa clé dans l'émulateur.
- **Gradle sous Windows** : `gradlew.bat` par chemin absolu ; `--no-build-cache` ;
  `clean` complet dès qu'un paramètre par défaut apparaît (le cache a déjà rendu des
  classes périmées) ; compter les cas **exécutés** dans `build/test-results` avant de
  conclure qu'un vert veut dire quelque chose. Si KSP dit « Storage already
  registered » : arrêter les démons (`gradlew --stop`, et le `KotlinCompileDaemon`),
  supprimer `build/kspCaches` des modules en cause, relancer.
- **Ne jamais modifier une source pendant qu'un build tourne** : le résultat ne veut
  plus rien dire.
- **Style** : KDoc en français accentué, commentaires de code en ASCII sans accents,
  messages de commit en ASCII ; ktlint (`intellij_idea`, 120 colonnes, imports triés,
  alias en dernier) ; detekt strict (`LongMethod` < 60 lignes, `TooManyFunctions` dès
  11 par fichier, `ReturnCount` 2) ; règles maison `HardcodedColor` (pas de couleur hors
  du design system) et `TimeOutsideClock` (l'heure se lit sur le port `Clock`).
- **`strings.xml` se modifie à la main**, jamais par script ; fins de ligne LF.
- **Chaque correction** : une décision `D164`, `D165`… dans `docs/11-decisions.md`
  (`## DNNN — titre · ✓ validée`), la doc concernée mise à jour (l'ancien texte barré
  `~~…~~` quand il était faux), un test par règle, et une **campagne de défaite** —
  saboter la correction, vérifier qu'un cas tombe, défaire (voir § 6).
- Aucun pronom pour l'auteur, Charly Flu, dans les textes.

## 3. Les chantiers, par ordre d'importance

### 3.1 La forme qu'on mange : une table de noms courants — **prioritaire**

**Le défaut.** `FoodRanking` multiplie la proximité par la brièveté du libellé, en
supposant que la fiche générique porte le nom le plus court de sa famille. Sur CIQUAL,
c'est souvent faux, et l'IA — qui écrit des noms simples, « riz », « café » — en hérite
par le même score (D74). Mesuré sur la base livrée :

| Requête | Ce qui sort premier | Ce qu'il fallait |
|---|---|---|
| riz | Riz blanc, cru (350 kcal/100 g) | Riz blanc, cuit (155) |
| pâtes | Pâtes sèches, crues (364) | Pâtes sèches, cuites (167) |
| café | Café, moulu (341) | Café prêt à boire (6) |
| thé | Thé, feuille (232) | Thé infusé (0,4) |
| avocat | Huile d'avocat (900) | Avocat, chair (203) |
| pomme | Pomme, sèche (247) | Pomme, chair et peau, crue (54) |
| fromage | Fromage de tête | Fromage (aliment moyen) |
| eau | Eau de coco | Eau du robinet |
| chicken | Chicken fat (898) | Chicken, breast, grilled |
| salmon | Salmon oil (900) | Salmon, cooked |
| rice | Rice bran (398) | Rice, white, cooked |
| milk | Milk, bulk | Milk, semi-skimmed, UHT |

« lait → Laitue » est déjà corrigé par D163 (palier des préfixes de lettres).

**La conception retenue** (commencée puis retirée de D163 faute de table) :

1. **Les fichiers** `tooling/ciqual/common-names-fr.csv` et `common-names-en.csv`,
   en-tête `code_ciqual,nom_courant`, une ligne par nom, plusieurs noms possibles par
   code, commentaires en `#` comme `short-names-fr.csv`. Relus à la main.
2. **La lecture** : un `CommonNamesCsv` calqué sur `ShortNamesCsv`
   (`tooling/ciqual-import/.../ShortNamesCsv.kt`), qui **arrête l'import en nommant la
   ligne** si : le code n'existe pas ; le nom est vide une fois normalisé ; le même nom
   normalisé désigne deux codes dans une langue (un nom ne désigne qu'une fiche) ; le
   nom normalisé est égal au libellé normalisé d'**une autre** fiche (elles seraient à
   égalité) ou de la sienne (inutile). Normaliser avec `SearchText.normalise`, la même
   fonction qu'à la lecture.
3. **`CatalogueFiles.commonNames(language)`** dans `CiqualImport.kt`, lu dans `main`
   comme les titres courts, et un compte rendu par langue dans `report`.
4. **L'écriture** (`CiqualDatabaseWriter`) : une colonne `common_names TEXT` dans
   `ciqual_name` (noms normalisés séparés par `|`, `NULL` sans nom) ; et **l'index plein
   texte reçoit le libellé suivi des noms courants**, sans quoi « café noir », nom courant
   d'une fiche qui ne contient pas « noir », ne la trouverait pas.
5. **`REVISION` 7 → 8** dans `CiqualDatabase` (`core/database`) : sans cela un appareil
   installé garde sa copie, sans la colonne. Lire la colonne dans `SELECT_COLUMNS`
   (après `group_name`, en comptant les rangs dans `toFoodRow`), dans `annotationsOf`, et
   l'ajouter à `CiqualFoodRow` et à `CiqualAnnotations`.
6. **Le domaine** : `Food.commonNames: Set<String> = emptySet()` (normalisés ; propriété
   de la référence, relue par code comme le titre court ; vide pour un aliment personnel
   ou un produit scanné), rempli dans les deux `toDomain` de
   `data/food/.../FoodMapper.kt`.
7. **Le score** : le meilleur des noms de la fiche, pesé par la même fonction — un nom
   courant **est** un nom, pas une règle à côté :

   ```kotlin
   fun score(food: Food, normalisedQuery: String): Double {
       val closest = (food.commonNames + SearchText.normalise(food.name))
           .maxOf { name -> proximity(name, normalisedQuery) * brevity(name) }
       return closest * food.source.weight * familiarity(food)
   }
   ```

   « riz » vaut alors à la fiche du riz cuit une égalité de nom mesurée sur trois
   lettres (4 × 30/33 ≈ 3,6 → confiance 0,86, automatique), et la saisie « ri » la met
   en tête au palier des lettres.
8. **Régénérer** : `gradlew.bat :tooling:ciqual-import:importCiqual`. Vérifier que seules
   `ciqual_name` et l'index changent (comparer avec une copie de l'ancienne base :
   `ciqual_food` et `ciqual_serving` identiques ligne à ligne).
9. **Tests** : dans `FoodRankingTest`, « un nom courant rend la forme qu'on mange »
   (riz cuit devant riz cru, pour « riz » et « riz blanc »), « un nom courant se remplit
   tout seul », « un nom courant ne donne rien à une requête qui ne le nomme pas » ; dans
   les tests de l'outil, chaque refus de `CommonNamesCsv` et la colonne écrite ; dans
   `RoomFoodCatalogTest` (base réelle), « riz », « café », « lait », « avocat »,
   « chicken », « salmon » rendent la bonne fiche en premier ; le résolveur
   (`LabelMatch`) sur les mêmes mots. **Sabotages** : retirer le `max` sur les noms
   courants ; retirer les noms de l'index plein texte ; oublier la colonne à la lecture.
10. **Docs** : `docs/04` (§ Classement et § Résolution), `docs/07` (colonne), une
    décision.

**Une première table, vérifiée contre la base livrée** (code → libellé français /
anglais). Les noms courants proposés sont des points de départ à relire :

| Code | Libellé (fr) | Libellé (en) | Noms courants fr | Noms courants en |
|---|---|---|---|---|
| 9104 | Riz blanc, cuit, sans sel ajouté | Rice, white, cooked, no added salt | riz, riz blanc | rice, white rice |
| 9103 | Riz complet, cuit, sans sel ajouté | Rice, wholegrain, cooked, no added salt | riz complet | brown rice, wholegrain rice |
| 9125 | Riz basmati, cuit, sans sel ajouté | Rice, basmati, cooked, no added salt | riz basmati | basmati rice |
| 9811 | Pâtes sèches, standard, cuites, sans sel ajouté | Pasta, dry, regular, cooked, no added salt | pâtes, spaghetti | pasta, spaghetti |
| 19041 | Lait demi-écrémé, UHT | Milk, semi-skimmed, UHT | lait | milk |
| 18004 | Café, non instantané, sans sucres ajoutés, prêt à boire | Coffee, non instant no added sugars, ready-to-drink | café, café noir | coffee, black coffee |
| 18020 | Thé infusé, sans sucres ajoutés | Tea, brewed, no added sugars | thé | tea |
| 18066 | Eau du robinet | Water, municipal | eau | water |
| 7001 | Pain blanc (par ex. : baguette, boule…) | Bread, French bread, baguette | pain, baguette | bread, baguette |
| 7200 | Pain de mie blanc, préemballé | Sandwich loaf, prepacked | pain de mie | sandwich bread |
| 12999 | Fromage (aliment moyen) | Cheese (average) | fromage | cheese |
| 19593 | Yaourt ou lait fermenté, nature | Yogurt or fermented milk, plain | yaourt | yogurt, yoghurt |
| 19501 | Fromage blanc, nature ou aux fruits (aliment moyen) | Quark, plain or with fruits (average) | fromage blanc | quark |
| 16400 | Beurre à 80% MG minimum, doux | Butter, 80% fat minimum, unsalted | beurre | butter |
| 17001 | Huile végétale (aliment moyen) | Vegetable oil (average) | huile | oil |
| 13039 | Pomme, chair et peau, crue | Apple, flesh and skin, raw | pomme | apple |
| 13005 | Banane, chair sans peau, crue | Banana, flesh without skin, raw | banane | banana |
| 13034 | Orange, chair sans peau, sans pépins, crue | Orange, flesh without skin, without seeds, raw | orange | orange |
| 13004 | Avocat, chair sans peau, sans noyau, cru | Avocado, flesh without skin, pitted, raw | avocat | avocado |
| 20385 | Tomate sans précision, crue (aliment moyen) | Tomato, raw (average) | tomate | tomato |
| 25604 | Salade verte, crue, sans assaisonnement | Green salad, raw, without seasoning | salade, salade verte | salad, green salad |
| 20030 | Haricot vert, cuit | French bean, cooked | haricots verts, haricot vert | green beans |
| 20037 | Petits pois, cuits | Garden peas, cooked | petits pois | peas |
| 20351 | Brocoli, cuit (aliment moyen) | Broccoli, cooked (average) | brocoli | broccoli |
| 20027 | Épinard, cuit | Spinach, cooked | épinards | spinach |
| 4048 | Pomme de terre, cuite (aliment moyen) | Potato, cooked (average food) | pomme de terre, pommes de terre | potato, potatoes |
| 4032 | Frites de pommes de terre, surgelées, cuites en friteuse | French fries or chips, frozen, deep-fried | frites | fries, french fries |
| 4047 | Purée de pomme de terre (aliment moyen) | Mashed potatoes (average) | purée | mashed potatoes |
| 36018 | Poulet, filet sans peau grillé/poêlé | Chicken, breast, without skin, grilled/pan-fried | poulet, blanc de poulet | chicken, chicken breast |
| 6259 | Boeuf, steak haché, cuit (aliment moyen) | Beef, ground, cooked (average) | steak haché | burger patty, ground beef |
| 6200 | Boeuf, steak ou bifteck grillé/poêlé | Beef, steak or beef steak, grilled/pan-fried | steak, boeuf | steak, beef |
| 28910 | Jambon cuit, choix | Cooked ham, choice | jambon, jambon blanc | ham |
| 25996 | Saumon, cuit, sans précision (aliment moyen) | Salmon, cooked (average) | saumon | salmon |
| 26039 | Thon, au naturel, appertisé, égoutté | Tuna, canned in brine, drained | thon | tuna |
| 26140 | Poisson cuit (aliment moyen) | Fish, cooked (average) | poisson | fish |
| 6999 | Viande cuite (aliment moyen) | Meat, cooked (average) | viande | meat |
| 28205 | Porc, viande, cuite (aliment moyen) | Pork, meat, cooked (average) | porc | pork |
| 22010 | Oeuf dur | Egg, hard-boiled | oeuf, œuf | egg |
| 2070 | Jus d'orange, pur jus | Orange juice, pure juice | jus d'orange | orange juice |
| 5001 | Bière "coeur de marché" (4-5° alcool) | Beer, regular (4-5° alcohol) | bière | beer |
| 5210 | Vin (aliment moyen) | Wine (average) | vin | wine |
| 18018 | Cola, sucré | Cola, with sugar(s) | coca, cola, soda | cola, coke, soda |
| 25516 | Pizza (aliment moyen) | Pizza (average) | pizza | pizza |
| 25969 | Soupe (aliment moyen) | Soup (average) | soupe | soup |
| 13212 | Compote de pomme, … (aliment moyen) | Apple compote, … (average) | compote | apple sauce |
| 31120 | Chocolat, tablette (aliment moyen) | Chocolate, bar (average) | chocolat | chocolate |
| 31006 | Confiture ou marmelade ou gelée, … (aliment moyen) | Jam or marmalade, … (average) | confiture | jam |
| 31016 | Sucre blanc | Sugar, white | sucre | sugar |
| 4004 | Chips de pommes de terre nature ou aromatisées | Potato, crisps, plain or flavoured, standard | chips | crisps, chips |
| 32003 | Céréales pour petit déjeuner (aliment moyen) | Breakfast cereals (average) | céréales | cereal, cereals |
| 32140 | Flocons d'avoine | Oat flakes | flocons d'avoine | oats, oatmeal |
| 39515 | Glace ou crème glacée, en bac | Ice cream, in box | glace | ice cream |

À trancher en relisant : « oeuf » (dur, ou laisser « Oeuf cru », de mêmes teneurs) ;
« poulet » (filet grillé, ou viande et peau rôties) ; « courgette », « carotte » (crue
ou cuite) ; la crème fraîche, introuvable sous ce nom (« Crème de lait, … ») ; puis une
seconde passe sur les mots qu'un repas photographié fait écrire au modèle. Le script de
l'annexe B rejoue le classement sur la base livrée pour une liste de mots : le relancer
avant et après, et garder sa sortie dans la décision.

### 3.2 La saisie ne survit pas à la mort du processus

**Le défaut.** `docs/02` le promettait ; [D40](docs/11-decisions.md#d40--ce-que-la-tranche-2-ne-construit-pas---validée) l'a reporté « avec le deuxième écran qui
a une saisie longue », et il y en a cinq. Vérifié à l'émulateur sur l'aliment personnel :
Hexavore en arrière-plan, `adb -s emulator-5554 shell am kill app.hexavore`, retour par
les applications récentes — seul le nom, venu de la route, revient. D163 n'a sauvé que
la **photo** en cours de prise.

**Le plan.** Un mécanisme transverse plutôt qu'un écran à la fois :

1. Dans chaque `ViewModel` de saisie, l'état du formulaire en `@Serializable` (des DTO
   à côté du `ViewModel` quand le domaine n'est pas sérialisable — `Food`,
   `Suggestion`… ne le sont pas), écrit en JSON dans **son** `SavedStateHandle` à chaque
   changement, relu à la construction **avant** l'ouverture depuis la route. Attention au
   piège de [D52](docs/11-decisions.md#d52--deux-savedstatehandle-une-seule-saisie-manuelle-des-grammes-entiers---validée) : le `SavedStateHandle` du `ViewModel` n'est pas celui de l'entrée de
   pile.
2. Par ordre de coût perdu : le **brouillon de plat** (`feature/entry`, `EntryForm` et ses
   lignes — y compris un plat proposé par l'IA, qui a coûté un appel), le **texte de
   l'analyse** (`feature/capture`, la photo étant déjà en mémoire dans l'état), l'**aliment
   personnel** (`feature/search/CustomFoodViewModel`), l'**onboarding**, le **profil**, la
   **pesée**.
3. Garder un œil sur la taille : quelques kilo-octets par écran, jamais d'image.
4. Tests : pour chaque `ViewModel`, un aller-retour — remplir, recréer avec le même
   `SavedStateHandle`, retrouver la saisie. À l'émulateur : la procédure ci-dessus, écran
   par écran.
5. Docs : `docs/02` § Restauration d'état (retirer la phrase de D163 qui dit que ce
   n'est pas tenu), une décision.

### 3.3 Une pesée ne se supprime pas

Le port `WeightLog` (`domain/.../profile/Profiles.kt`) n'a que `record`. Une pesée tapée
sur la mauvaise date reste dans la courbe, la pente de l'adaptation et le poids du calcul.
Plan : `suspend fun delete(date: LocalDate)` dans le port, le DAO, l'adaptateur Room et
`InMemoryWeightLog` ; un cas d'usage ; sur l'écran de poids, un appui long sur une ligne
de la liste ouvre « Supprimer », suivi de la barre d'annulation comme pour un plat
(`Timing.UNDO_WINDOW_MILLIS`, la suppression immédiate et la restauration par un
second geste, comme `onDeleteDish`). Tests du port, du cas d'usage et du `ViewModel`.

### 3.4 Petits défauts relevés, non corrigés

- **La liste des favoris** (`feature/settings/FavoritesSettingsScreen.kt` et
  `feature/search/FavoritesScreen.kt`) affiche `components.joinToString { it.name }`, les
  noms figés à l'enregistrement, quand l'éditeur montre ceux de la fiche vivante : après
  un changement de langue, l'une est en anglais et l'autre en français. Relire les noms
  par la fiche (`foodId`) au moment de lister.
- **La célébration sans animations** ne dure qu'une image (`Celebration.kt` appelle
  `onDone` tout de suite quand la durée vaut zéro), alors que son commentaire la dit
  « remplacée par le seul texte ». Un délai qui n'est pas une animation appartient à
  `Timing`, pas à `Motion`.
- **Le paysage** reste « non optimisé » (`docs/01`) : titre et calendrier fixes, barre du
  bas fixe, il reste peu de hauteur au journal. Ne rien faire sans décision.

## 4. Vérifications à l'émulateur qui restent dues

Construire la release (`gradlew.bat :app:assembleRelease`), l'installer sur
`emulator-5554` par-dessus la précédente (`adb -s emulator-5554 install -r …`), puis :

**D163.**
- Restaurer le mois d'essai (annexe A) : la file des paliers passe sans rester figée
  (animations coupées sur cet émulateur : `settings get global animator_duration_scale`
  rend 0) ; la carte « Ajuster votre objectif ? » ne propose plus « Augmenter … de
  7 kcal » ; elle ne paraît plus au-dessus d'« Hier ».
- Descendre jusqu'aux plats d'hier, revenir à aujourd'hui : la page repart du haut.
- Réglages → Notifications : la permission y est refusée pour de bon
  (`dumpsys package app.hexavore | grep POST_NOTIF` montre `USER_FIXED`) ; « Autoriser
  les notifications » doit ouvrir l'écran des notifications d'Hexavore dans Android, et
  l'avertissement disparaître au retour après les avoir permises.
- Scanner et « Favoris » (bouton « + ») : rien sous l'heure ni sous la barre de geste.
- Pivoter l'écran (`settings put system accelerometer_rotation 0`, puis
  `settings put system user_rotation 1` ; remettre `0` et `1` après) : la semaine ne
  prend plus la hauteur de l'écran.
- Recherche « nutella » → « Chercher dans Open Food Facts » : en cas d'échec,
  « Réessayer » relance.
- La photo : nouvelle saisie → « Ajouter une photo » → « Prendre une photo » ; quand
  l'appareil photo du système est ouvert, `adb -s emulator-5554 shell am kill
  app.hexavore` ; prendre et valider ; la vignette doit apparaître.

**D162**, jamais vus : la question des notifications sur un onboarding neuf (après
`adb -s emulator-5554 shell pm clear app.hexavore`, ce qui efface tout) ; la boîte de la
caméra refusée pour de bon ; la barre d'un lien quand aucun navigateur ne répond (désactiver
Chrome le temps de l'essai, puis le réactiver) ; les libellés en impérial (axe du graphique,
lecture d'une pesée, carte d'ajustement, rythme du profil) ; la version écrite dans un
export ; l'énergie du calvados et du pastis allongé à la recherche.

Ce qui est vu se consigne dans la décision, § « Ce que le vert ne prouve pas ».

## 5. Le site

Le dépôt `hexavore-site` porte la phrase de D162 (« des estimations… ») dans
`fr.tsx` et `en.tsx`, sur une branche du même nom que celle-ci. Le statut « pas encore
publiée » reste jusqu'à ce que la fiche Play soit publique, et la politique de
confidentialité n'est en ligne qu'après fusion dans `main`.

## 6. Le harnais de défaite

Pour chaque correction : un script applique un remplacement de texte dans la source,
lance les tests du module **et seulement ceux de la classe visée**, compte les cas
exécutés dans `build/test-results` (plus récents que le lancement), note « TOMBE » ou
« SURVIT », puis remet la source telle qu'elle était, dans un `finally`. Commande type :

```
gradlew.bat -p <dépôt> :domain:test --tests app.hexavore.domain.food.FoodRankingTest --no-build-cache --no-configuration-cache -q
```

Un sabotage qui survit veut dire un test à affûter, pas une campagne réussie.

---

## Annexe A — Un mois de journal pour l'émulateur

Exporter une sauvegarde par l'écran Sauvegarde, la récupérer
(`adb -s emulator-5554 pull /sdcard/Download/hexavore-AAAA-MM-JJ.zip`), la compléter
avec ce script (`python -I enrich_backup.py export.zip essai.zip AAAA-MM-JJ`), la
renvoyer (`adb -s emulator-5554 push essai.zip /sdcard/Download/hexavore-essai.zip` ;
sous Git Bash, préfixer `MSYS_NO_PATHCONV=1` et donner le chemin local à la Windows),
puis la restaurer par l'écran. L'objectif doit être une **perte** avec poids cible et
échéance, sans quoi la carte d'ajustement ne paraît pas.

```python
"""Complete un export reel d'Hexavore avec un mois de vie.

- l'objectif courant demarre il y a 30 jours ;
- une pesee par jour sur 30 jours, a plat : l'objectif de perte annonce un rythme que
  le poids ne suit pas, ce qui doit faire parler l'adaptation ;
- trois plats par jour sur les 25 derniers jours, un jour saute sur sept ;
- un jour tres au-dessus de l'objectif.
"""
import datetime as dt
import gzip
import io
import json
import random
import sys
import zipfile

source, target, today_text = sys.argv[1], sys.argv[2], sys.argv[3]
today = dt.date.fromisoformat(today_text)
random.seed(162)

with zipfile.ZipFile(source) as archive:
    journal = json.loads(gzip.decompress(archive.read("hexavore.json.gz")).decode("utf-8"))
    others = {name: archive.read(name) for name in archive.namelist() if name != "hexavore.json.gz"}

goal = next(g for g in journal["goals"] if g.get("endedAt") is None)
start = today - dt.timedelta(days=30)
goal["startedAt"] = start.isoformat()

first_weight = journal["weights"][0]["weightKg"] if journal["weights"] else 80.0
journal["weights"] = [
    {"date": (start + dt.timedelta(days=i)).isoformat(), "weightKg": round(first_weight + random.uniform(-0.3, 0.3), 1)}
    for i in range(31)
]

kcal_goal = goal["kcal"]
meals = [
    ("BREAKFAST", "08:10", 0.25, [("Flocons d'avoine", 60, 0.6), ("Lait demi-écrémé", 200, 0.4)]),
    ("LUNCH", "12:45", 0.40, [("Riz blanc, cuit", 200, 0.5), ("Poulet, filet, rôti", 150, 0.5)]),
    ("DINNER", "19:50", 0.35, [("Pâtes, cuites", 180, 0.6), ("Sauce tomate", 100, 0.4)]),
]

dishes, entries = [], []
for back in range(1, 26):
    day = today - dt.timedelta(days=back)
    if back % 7 == 3:
        continue
    factor = 1.6 if back == 5 else random.uniform(0.9, 1.05)
    for moment, clock, share, lines in meals:
        dish_id = f"essai-{day.isoformat()}-{moment.lower()}"
        hour, minute = map(int, clock.split(":"))
        logged = dt.datetime(day.year, day.month, day.day, hour, minute, tzinfo=dt.timezone.utc)
        dishes.append({"id": dish_id, "date": day.isoformat(), "source": "MANUAL",
                       "loggedAt": logged.isoformat().replace("+00:00", "Z"), "moment": moment})
        for index, (name, grams, part) in enumerate(lines):
            entries.append({
                "id": f"{dish_id}-{index}", "dishId": dish_id, "displayName": name,
                "quantity": grams, "unit": "g", "grams": grams,
                "kcal": round(kcal_goal * share * part * factor),
                "protein": round(goal["protein"] * share * part * factor, 1),
                "carbs": round(goal["carbs"] * share * part * factor * 0.9, 1),
                "sugars": round(goal["sugars"] * share * part * factor * 0.6, 1),
                "fat": round(goal["fat"] * share * part * factor * 0.9, 1),
                "fiber": round(goal["fiber"] * share * part * factor, 1),
            })

journal["dishes"] = journal.get("dishes", []) + dishes
journal["entries"] = journal.get("entries", []) + entries

buffer = io.BytesIO()
with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as out:
    out.writestr("hexavore.json.gz", gzip.compress(json.dumps(journal, ensure_ascii=False).encode("utf-8")))
    for name, data in others.items():
        out.writestr(name, data)
open(target, "wb").write(buffer.getvalue())
```

Les heures sont écrites en UTC : l'émulateur, réglé sur UTC, les affiche telles quelles.

## Annexe B — Rejouer le classement sur la base livrée

Ce script reproduit la normalisation (`SearchText`), la requête plein texte et le score
de `FoodRanking` tel qu'il est **après D163** (palier des lettres compris), puis le
verdict du résolveur (seuils 0,75 et 0,40, saturation 0,6). Il sert à mesurer une table
de noms courants avant de la livrer.

```python
"""python -I resolve.py core/database/src/main/assets/ciqual.db fr riz café lait ..."""
import sqlite3, sys, unicodedata
sys.stdout.reconfigure(encoding="utf-8")

def normalise(text):
    text = text.replace("œ", "oe").replace("Œ", "oe").replace("æ", "ae")
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    return " ".join("".join(ch if ("a" <= ch <= "z" or "0" <= ch <= "9") else " " for ch in text).split())

ARTICLES = {"fr": {"de", "du", "des", "d", "le", "la", "les", "un", "une", "l"},
            "en": {"the", "a", "an", "of", "some"}}

def proximity(name, query):
    if name == query: return 4.0
    if name.startswith(query + " "): return 2.0
    if query in name.split(" "): return 1.5
    if name.startswith(query): return 1.25
    return 1.0

def score(name, query):
    return proximity(name, query) * (30.0 / (30.0 + len(name)))

def verdict(c):
    return "AUTO" if c >= 0.75 else ("REVUE" if c >= 0.40 else "AUCUNE")

db, lang, labels = sys.argv[1], sys.argv[2], sys.argv[3:]
c = sqlite3.connect(db)
for label in labels:
    words = [w for w in normalise(label).split(" ") if w]
    while words and words[0] in ARTICLES[lang]:
        words.pop(0)
    q = " ".join(words)
    rows = c.execute(
        "select n.code, n.name, f.kcal_100 from ciqual_fts join ciqual_name n on n.rowid = ciqual_fts.docid "
        "join ciqual_food f on f.code = n.code where ciqual_fts match ? and n.language = ?",
        (" ".join(w + "*" for w in words), lang)).fetchall()
    ranked = sorted(rows, key=lambda r: (-score(normalise(r[1]), q), r[1]))
    if not ranked:
        print(f"{label:>18} -> rien"); continue
    best = ranked[0]; s = score(normalise(best[1]), q)
    print(f"{label:>18} -> {verdict(s / (s + 0.6)):6} {s / (s + 0.6):.2f}  {best[1]}  ({best[2]} kcal)")
```

Une fois la colonne `common_names` livrée, faire porter le `max` sur le libellé et les
noms courants de chaque ligne, comme le fera `FoodRanking`.
