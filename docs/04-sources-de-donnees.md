# 04 — Sources de données alimentaires

Trois sources, trois rôles distincts, un catalogue local unifié qui les fait cohabiter.

| Source | Rôle | Accès | Licence |
|---|---|---|---|
| **CIQUAL 2025** (ANSES) | Aliments et plats génériques | Embarqué dans l'APK | Licence Ouverte Etalab 2.0 |
| **Open Food Facts** | Produits emballés, par code-barres | API en ligne + cache permanent | ODbL 1.0 |
| **Aliments personnels** | Ce que l'utilisateur crée lui-même | Base locale | — |

**La table de l'ANSES est bilingue, et elle l'était déjà** ([D129](11-decisions.md#d129--langlais-est-le-repli-le-français-une-traduction-et-la-langue-est-une-donnée---validée)). `alim_nom_eng` est présent sur 3 484 lignes sur 3 484, `alim_grp_nom_eng` et `alim_ssgrp_nom_eng` aussi : le catalogue anglais ne coûte ni passe d'IA, ni traduction à relire, ni licence nouvelle. Dans `ciqual.db`, **la langue est une ligne et non une colonne** — une table `ciqual_name` à une ligne par aliment et par langue —, de sorte qu'une troisième langue n'ajoute que des lignes. Ce qui n'a pas d'anglais est nommé : les **titres courts**, dont la passe n'a tourné qu'en français ; l'affichage retombe alors sur le libellé anglais de l'ANSES, en moyenne plus court que le français.

Pourquoi ce partage plutôt qu'une source unique : Open Food Facts référence des *produits de marque*. Chercher « lasagnes » y renvoie quarante barquettes industrielles, pas une lasagne. CIQUAL fait exactement l'inverse : des aliments et des plats représentatifs de la consommation française, avec des valeurs de référence, mais aucun code-barres. Les deux sont complémentaires, aucun ne remplace l'autre.

---

## CIQUAL

### La donnée

3 484 aliments, publiés par l'ANSES, avec les six macros dont l'application a besoin, plus les acides gras, minéraux et vitamines qu'elle stocke sans les afficher.

Colonnes utilisées, sous leur intitulé exact dans le fichier source :

| Champ interne | Colonne CIQUAL |
|---|---|
| `kcal100` | `Energie, Règlement UE N° 1169/2011 (kcal/100 g)` |
| `protein100` | `Protéines, N x facteur de Jones (g/100 g)` |
| `carb100` | `Glucides (g/100 g)` |
| `sugar100` | `Sucres (g/100 g)` |
| `fat100` | `Lipides (g/100 g)` |
| `fiber100` | `Fibres alimentaires (g/100 g)` |

### Import au build

Une tâche Gradle (`:tooling:ciqual-import:importCiqual`) transforme le XML de l'ANSES en base SQLite pré-construite, livrée dans `core/database/src/main/assets/ciqual.db` — **928 Ko**, et non les 4 Mo estimés ici : sur les 74 constituants publiés, huit sont retenus.

Le fichier source brut est versionné dans le dépôt sous `tooling/ciqual/`, et la base générée l'est aussi : sans elle, un `git clone` suivi d'un build donnerait une application vide. Les quatre fichiers de l'ANSES y sont sous forme d'archive, lue sans être décompressée sur disque : `compo.xml` pèse 69 Mo pour 257 816 enregistrements, et 2 Mo compressés.

L'empreinte SHA-256 vérifiée est celle de la **source**, avant lecture. Une base SQLite n'est pas garantie octet pour octet d'une exécution à l'autre, donc un contrôle sur la sortie produirait de fausses alertes — et de fausses alertes finissent par se désactiver. `tooling/ciqual/SOURCE.sha256` porte aussi les empreintes des quatre fichiers publiés, qui sont celles qu'on vérifie contre un téléchargement neuf.

La tâche n'est branchée sur aucun cycle de vie de Gradle : la regénérer à chaque compilation produirait un binaire modifié dans chaque diff. On la lance quand l'ANSES publie.

### Le piège des valeurs textuelles

CIQUAL n'est pas un fichier de nombres. Les valeurs arrivent sous forme de chaînes, avec une virgule décimale et plusieurs conventions à interpréter. C'est **la** source de bugs sur ce jeu de données.

| Valeur brute | Interprétation | Justification |
|---|---|---|
| `12,5` | `12.5` | Virgule décimale française |
| `traces` | `0.0` | Présence négligeable |
| `< n` | `n / 2` | Milieu de l'intervalle, sans biaiser vers le haut |
| `-` | `null` | Non déterminé — **différent de zéro** |
| `NC` | `null` | Non communiqué |
| chaîne vide | `null` | Idem |
| autre chose | **échec** | Voir ci-dessous |

`null` et `0` ne sont pas interchangeables. Un aliment dont les fibres sont inconnues ne doit pas faire croire à zéro fibre : l'interface affiche « — » et le total de la journée signale que des lignes sont incomplètes.

**Le seuil de `<` est quelconque.** Cette ligne disait `< 0,5 → 0.25` ; c'est un exemple et non la convention. L'édition 2025 compte 250 seuils distincts, de `< 0,0001` à `< 700`, pour 16 000 valeurs — un parseur qui n'aurait reconnu que l'exemple cité aurait perdu ou inventé toutes les autres. Dépouillé sur le fichier, pas déduit du document.

**`NC` et la chaîne vide n'apparaissent pas dans le XML 2025** ; elles apparaissent dans l'export XLS de la même table. Elles sont traitées quand même : deux lignes de code contre le jour où l'une de ces écritures arrive par un chemin auquel on n'avait pas pensé.

**Une écriture inconnue arrête l'import.** C'est la dernière ligne du tableau et la plus importante. Un parseur a trois issues et non deux : la valeur, l'inconnu déclaré, et ce qu'il ne sait pas lire. Ranger la troisième avec l'inconnu effacerait une colonne entière en silence le jour où l'ANSES change de convention ; la ranger avec zéro en inventerait une. `CiqualValueParser` la nomme `Unrecognised`, et la tâche d'import échoue en listant les cas ([D49](11-decisions.md)).

Le parseur est isolé (`CiqualValueParser`), et chacune de ces lignes est un cas de test.

### Recherche

Table `ciqual_fts` en **FTS4**, tokenizer `simple`, sur une colonne `name_search` **déjà normalisée à l'import** — décomposition Unicode, marques diacritiques retirées, ligatures défaites, minuscules, ponctuation devenue coupure de mot. C'est cette normalisation qui fait que « creme brulee » trouve « crème brûlée » — indispensable pour une saisie au clavier mobile, où personne ne tape les accents.

Ce paragraphe demandait FTS5 avec `unicode61 remove_diacritics 2`. Ni l'un ni l'autre ne tient sous `minSdk 26` : FTS5 n'est compilé dans le SQLite embarqué d'aucune version d'Android, et `remove_diacritics 2` exige SQLite 3.27, soit l'API 29. Le travail est donc fait par la JVM au build, une fois, avec une couverture Unicode plus large que celle qu'on attendait de SQLite ([D49](11-decisions.md)). **La seule règle qui compte : la même normalisation est appliquée au nom indexé et à la saisie.**

La requête part dès le **2ᵉ caractère**, après 120 ms sans frappe ([02](02-parcours-et-ecrans.md#modale--recherche)). Deux caractères suffisent parce que la recherche est locale : le coût d'une requête inutile est une lecture SQLite, pas un aller-retour réseau.

Classement : remontée des aliments courts et déjà consommés par l'utilisateur. Sans ce critère, « pomme » renvoie « pomme de terre à chair farineuse, crue » avant « pomme, chair et peau, crue ». BM25 n'est pas disponible — c'est une fonction de FTS5 — et son absence coûte peu ici : sur 3 484 libellés courts, la pondération par fréquence départage mal, alors que la longueur du nom et l'usage réel départagent bien. C'était déjà le critère décisif quand BM25 était prévu.

### Portions usuelles

CIQUAL ne donne que des valeurs pour 100 g. Or personne ne pèse une pomme.

Le dépôt maintient donc `tooling/ciqual/servings.csv` — table écrite et relue à la main, associant un code CIQUAL à une ou plusieurs portions nommées :

```csv
code_ciqual,libelle,grammes,par_defaut
13039,1 pomme moyenne,150,true
22000,1 œuf,50,true
7003,1 tranche,30,true
7003,1 baguette,250,false
18066,1 verre,200,true
```

**67 portions à ce jour**, pas les 250 annoncées ici. Chaque ligne a été vérifiée contre un code CIQUAL réel : une portion rattachée au mauvais aliment fausse une saisie sans que rien ne le dise, et 250 lignes écrites de mémoire en produiraient. La table grandit par contributions, et c'est le point d'entrée idéal pour quelqu'un qui veut aider sans écrire de Kotlin.

Le lecteur est sévère pour cette raison : code inexistant, poids nul, colonne manquante ou deuxième portion par défaut pour un même aliment arrêtent l'import en nommant le numéro de ligne. Une contribution mal formée doit échouer à la relecture, pas disparaître en silence.

Un aliment sans portion déclarée propose 100 g par défaut.

---

## Open Food Facts

### Appel

```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
    ?fields=code,product_name,product_name_fr,brands,
            serving_size,serving_quantity,nutriments
```

`quantity` et `image_front_small_url` figuraient ici et n'y sont plus : rien ne les lit. Demander un champ inutilisé est le même travers qu'une colonne que rien ne remplit, et il se paie en octets sur une connexion mobile contre un budget de deux secondes ([D63](11-decisions.md#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée)).

**Le code-barres demandé est mis sous forme canonique avant l'appel** — UPC-A complété en EAN-13 par un zéro, clé de contrôle vérifiée — et c'est ce code-là, et non celui que la réponse renvoie, qui est enregistré comme référence. Sans quoi le second scan d'un produit ne retrouverait pas la fiche mise en cache, et le défaut ne se verrait qu'en mode avion ([D63](11-decisions.md#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée)).

**En-tête `User-Agent` obligatoire** : `Hexavore/<version> (github.com/hexavore-app/hexavore)`. Open Food Facts bloque les clients anonymes, et c'est légitime : l'en-tête est leur seul moyen de joindre l'auteur d'un client qui se comporte mal. Cet en-tête est posé par un intercepteur OkHttp, pas au cas par cas.

L'adresse est figée sur l'organisation GitHub du projet, réservée pour cela ([D14](11-decisions.md#d14--domaine-et-publication-reportés-après-la-05---validée)) : elle survit à un changement de propriétaire, contrairement à un compte personnel. La version vient du `versionName`, pour qu'un rapport de la part d'Open Food Facts désigne un binaire précis.

Pas de clé, pas de compte, pas de quota commercial. Limite de courtoisie : 100 requêtes par minute — hors d'atteinte pour un usage normal, mais un retrait exponentiel s'applique quand même sur `429` et `5xx`, trois tentatives maximum.

~~L'intercepteur applique le retrait.~~ Il vit dans la fonction suspendue et non dans un intercepteur : celui-ci attendrait avec `Thread.sleep`, donc en immobilisant un fil du répartiteur d'OkHttp, là où `delay` suspend — et c'est aussi ce qui rend les trois tentatives éprouvables en temps virtuel ([D63](11-decisions.md#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée)). **Une panne réseau n'est pas réessayée** : le retrait sert à laisser passer une surcharge du service, pas à faire attendre quelqu'un debout devant un rayon.

### Champs récupérés

| Champ interne | Chemin JSON | Absent ? |
|---|---|---|
| `name` | `product_name_fr` sinon `product_name` | Bloquant : sans nom, la fiche est inutilisable |
| `brand` | `brands` (première valeur avant la virgule) | Toléré |
| `kcal100` | `nutriments["energy-kcal_100g"]`, sinon `nutriments["energy_100g"]` ÷ 4,184 | Toléré |
| `protein100` | `nutriments.proteins_100g` | Toléré |
| `carb100` | `nutriments.carbohydrates_100g` | Toléré |
| `sugar100` | `nutriments.sugars_100g` | Toléré |
| `fat100` | `nutriments.fat_100g` | Toléré |
| `fiber100` | `nutriments.fiber_100g` | Toléré — **manquant très souvent** |
| `servingG` | `serving_quantity`, sinon parsing de `serving_size` | Toléré |

Deux réflexes obligatoires face à cette base collaborative :

1. **L'énergie peut n'exister qu'en kilojoules.** Conversion `kJ ÷ 4,184`, et jamais l'inverse : si `energy-kcal_100g` existe, il fait foi.
2. **Tout champ nutritionnel peut être absent.** La fiche est quand même créée, en état *incomplet*. L'écran de validation met les trous en évidence et invite à les compléter à la main ; la valeur saisie est conservée localement pour toujours.

### Recherche par nom

`GET /cgi/search.pl?search_terms=…&json=1&page_size=20&fields=…` — l'ancien point d'entrée et non `api/v2/search`, qui filtre sur des étiquettes et n'accepte pas de texte libre. Il rend les mêmes objets produit, donc la même correspondance les lit.

Elle part **sur un tap**, depuis la dernière ligne des résultats, et sans retrait exponentiel : le geste est délibéré et l'écran montre déjà son issue. Un produit dont le code n'est pas lisible est écarté — sans code canonique, la fiche ne pourrait ni être mise en cache sans doublon ni être retrouvée par un scan ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)).

### Cache

Toute fiche récupérée est écrite dans le catalogue local (`food`, `source = OFF`) et n'en sort plus. **La date de récupération est posée par le client**, qui est le seul à savoir quand il a interrogé le service : deux chemins de récupération existent — le code-barres et le nom — et la faire poser par l'appelant obligeait chacun à y penser ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)). Conséquences : un produit scanné une fois est disponible hors-ligne à vie, et le second scan est instantané.

Rafraîchissement : proposé, jamais imposé, sur une fiche de plus de 90 jours ouverte manuellement. Un rafraîchissement **n'écrase jamais** un champ que l'utilisateur a corrigé à la main, ni les macros déjà figées dans des entrées de journal.

### Produit absent

Environ 1 produit sur 10 en France, davantage sur les marques régionales et les produits frais. Le parcours est décrit en [02](02-parcours-et-ecrans.md#modale--scan-de-code-barres) ; l'essentiel est que l'aliment créé à la main conserve son code-barres, et devient donc scannable comme n'importe quel autre.

**Ce « 1 sur 10 » est un chiffre français, et il ne voyage pas.** Mesuré sur l'API : 1 257 548 produits en France, 950 725 aux États-Unis, 420 711 en Allemagne, 42 808 au Japon, **10 911 en Thaïlande**, 8 616 en Indonésie, 1 545 au Viêt Nam. La base est collaborative : sa couverture suit les contributeurs, pas les marchés. Hors d'Europe, le produit absent n'est plus l'exception mais le cas courant, et la création manuelle cesse d'être un repli pour devenir la route principale.

**Contribuer la fiche à Open Food Facts** est ce qui empêche alors chaque saisie de rester sur un seul téléphone. `FoodContributionTarget` et son implémentation arrivent en **tranche 6**, avec le premier écran où une fiche Open Food Facts se consulte — donc le premier d'où elle peut s'offrir ([D70](11-decisions.md#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée)).

**L'écriture exige une authentification** — le `User-Agent` suffit à lire, pas à écrire — et elle part donc sous **le compte Open Food Facts de l'utilisateur**, jamais sous un compte de l'application : celui-ci aurait son mot de passe dans un APK publié. Seuls les aliments personnels partent, avec un code-barres valide et **aucune valeur estimée** : un chiffre complété par un modèle ([D89](11-decisions.md#d89--une-valeur-complétée-ne-se-range-pas-où-une-valeur-mesurée-se-range---validée)) qui entre dans une base publique y devient une mesure pour tout le monde. Un réglage bascule l'envoi vers l'instance de test, `world.openfoodfacts.net`, le temps de vérifier qu'un envoi aboutit ([D90](11-decisions.md#d90--la-contribution-part-sous-le-compte-de-lutilisateur-et-jamais-sans-lui---validée)).

---

## Aliments personnels

Créés depuis la recherche (« Créer *« … »* »), depuis un scan infructueux, ou depuis les réglages. Champs : nom, marque, macros pour 100 g, portions nommées, code-barres facultatif.

Ils sont prioritaires dans les résultats de recherche : ce que l'utilisateur a pris la peine de saisir est ce qu'il mange vraiment.

Un aliment personnel utilisé dans le journal ne peut pas être supprimé sans confirmation ; et sa suppression n'efface pas l'historique, les macros y étant figées ([07](07-modele-de-donnees.md)).

---

## Résolution : du texte de l'IA à un aliment

Étape charnière entre [05](05-ia.md) et le journal. L'IA rend `{ « jus d'orange », 200, ML }` ; il faut en tirer des macros.

`NutritionResolver` applique quatre étapes.

**1. Normalisation.** Minuscules, accents retirés, ponctuation supprimée — c'est `SearchText.normalise`, la normalisation de l'index elle-même et non une seconde ([D49](11-decisions.md#d49--la-recherche-normalise-à-limport-pas-au-tokenizer---validée)) — puis **articles de tête retirés** (« du », « de la », « un »).

**Les pluriels naïfs français (`-s`, `-x`, `-aux` → `-al`) ne sont pas ici**, contrairement à ce que cette liste écrivait, et la différence n'est pas de rangement. Les deux recherches sont conjonctives : le catalogue local compare une sous-chaîne entière (`LIKE '%du pain%'`) et la table de l'ANSES exige que tous les termes du `MATCH` soient présents. Un article gardé ne dégrade donc pas le classement — il rend une réponse **vide**, et doit partir tout de suite. Un pluriel, à l'inverse, est ce que l'index porte lui-même : 32 % des 3 484 libellés en contiennent un, et dépluraliser d'emblée ferait perdre « haricots verts ». La dépluralisation est donc l'objet d'un **second essai**, déclenché seulement si la première recherche n'a rien rendu ([D75](11-decisions.md#d75--la-normalisation-retire-les-articles-tout-de-suite-et-les-pluriels-seulement-en-second-recours---validée)).

**2. Candidats.** Recherche FTS sur les trois sources, fusionnée, avec ces poids :

| Origine | Multiplicateur |
|---|---|
| Aliment déjà consommé par l'utilisateur | × 1,5 |
| Aliment personnel | × 1,3 |
| CIQUAL | × 1,0 |
| Produit Open Food Facts en cache | × 0,8 |

Le produit de marque est délibérément dévalorisé : « riz » doit tomber sur le riz CIQUAL, pas sur un paquet de riz scanné il y a trois mois.

**3. Décision.**

- score ≥ **0,75** → correspondance retenue automatiquement ;
- **0,40 – 0,75** → meilleur candidat retenu, mais la ligne est signalée et propose jusqu'à 3 alternatives ;
- < **0,40** → aucune correspondance, on passe à l'étape 4.

**Le score de ces seuils est celui de `FoodRanking`, ramené dans `[0, 1]`** — c'est le même que celui qui trie les résultats de recherche, et non un second juge écrit à côté ([D74](11-decisions.md#d74--un-seul-score-pour-trier-et-pour-décider-et-le-tri-nen-bouge-pas---validée)). Les poids de l'étape 2 entrent donc dans la décision, comme cette page le veut : « Riz blanc, cuit » sur « riz » vaut 0,69 — relecture — et 0,77 dès qu'on l'a déjà mangé, donc automatique. Une confiance égale à un seuil appartient au verdict du haut.

**Les trois alternatives sont soumises au même seuil de 0,40** : ce sont les candidats suivants du classement que l'on retiendrait aussi, et non les trois suivants quels qu'ils soient. Proposer comme solution de rechange un candidat qu'on refuserait comme correspondance remplirait la liste de bruit ; la contrepartie est qu'elle est parfois vide ([D75](11-decisions.md#d75--la-normalisation-retire-les-articles-tout-de-suite-et-les-pluriels-seulement-en-second-recours---validée)).

Ces deux valeurs datent de la conception et **n'ont été calibrées contre rien**. Ce qui les rend exploitables est l'échelle : elle est réglée pour qu'un nom exact passe et qu'un simple préfixe ne passe pas seul. De vraies reconnaissances diront s'il faut les bouger.

**4. Repli IA.** Toutes les lignes non résolues partent en **un seul second appel** groupé, qui demande une estimation des macros pour 100 g. Le résultat est marqué `source = AI_ESTIMATE`, porte un badge distinct dans l'interface, et n'est jamais versé au catalogue partagé — c'est une estimation, pas une référence.

Sans clé API valide à ce moment-là, la ligne est présentée à zéro avec une invitation à la compléter ou à la remplacer.

### Le catalogue outillé : quand le modèle choisit lui-même

Les quatre étapes ci-dessus ont un angle mort, et « Abricot » qui devient « Jus d'abricot » le montre : le modèle a vu l'assiette, il a écrit le libellé, puis **notre score** choisit parmi vingt candidats sans lui redonner la parole. La confiance restant haute, la ligne se remplit sans même être signalée.

L'analyse approfondie de [05](05-ia.md#analyse-approfondie) renverse cet ordre ([D109](11-decisions.md#d109--le-modèle-choisit-la-fiche-parce-quil-en-sait-plus-que-notre-score---validée)). Le modèle **interroge** le catalogue et **choisit**.

**Ce qu'il demande** : une liste de libellés, en une fois — « semoule, merguez grillée, carottes cuites, oignons cuits, coriandre ».

**Ce qu'il reçoit**, pour chaque libellé, jusqu'à **six** fiches :

| Champ | Pourquoi |
|---|---|
| `reference` | Ce par quoi il désignera son choix — la référence de la source, pas un identifiant interne |
| `nom` | Le nom **long**, jamais le titre court : ce qui distingue deux fiches voisines est précisément ce que le raccourci enlève |
| `rayon` | La catégorie, quand la fiche en a une |
| `pour_100g` | Les **six** teneurs, `null` compris |

Les teneurs ne sont pas décoratives : elles permettent d'écarter un jus sans deviner — près de zéro protéine et beaucoup de sucres d'un côté, des fibres de l'autre. Sans elles, le modèle choisirait sur le seul libellé, c'est-à-dire sur la même information que le score qu'on remplace.

**La recherche est celle de l'étape 1**, moins la décision : même normalisation, même second essai au singulier, même index. Ce qui disparaît est l'étape 3 — aucun score, aucun verdict, aucune alternative.

**Une fiche sans référence est écartée.** Un aliment personnel sans origine serait proposé sans pouvoir être désigné, et le modèle passerait un tour à le nommer.

**Ce que son choix produit.** La fiche désignée donne une ligne au verdict automatique, sans alternatives : les valeurs viennent de la table et non du modèle, donc le marqueur d'estimation mentirait. **Sa confiance est conservée telle quelle** — elle ne devient pas 1 sous prétexte qu'il a choisi dans une liste, parce qu'il a pu choisir le moins mauvais.

**Une référence qu'il invente** ne correspond à aucune fiche montrée, et la ligne repart vers l'étape 4. On ne remplit pas une ligne avec une fiche qu'on n'a pas.

**Rien n'est écrit.** Comme la résolution, c'est une lecture ; c'est l'enregistrement du brouillon qui verse une fiche au catalogue.

### Conversion des quantités

```
G      → grammes tels quels
OZ     → 28,349523125 g          FL OZ → 29,5735295625 ml × densité
ML     → grammes × densité (1,00 par défaut ; 1,04 jus ; 1,03 lait ; 0,92 huile)
PIECE  → table des portions usuelles ; à défaut, le poids que le modèle a estimé ; à défaut 100 g
SLICE  → table des portions ; à défaut 30 g
TBSP   → 15 g × densité      TSP → 5 g × densité
BOWL   → 250 g               PLATE → 350 g       GLASS → 200 g × densité
```

Toute conversion appuyée sur un défaut plutôt que sur une donnée réelle est signalée dans l'écran de validation. L'utilisateur doit voir quand l'application devine.

**Le poids que le modèle estime passe avant tout forfait, jamais avant une mesure** ([D112](11-decisions.md#d112--le-modèle-pèse-ce-quil-compte-parce-quune-pièce-ne-dit-rien-de-sa-taille---validée)). Les autres unités portent leur ordre de grandeur — une tranche fait trente grammes à peu près toujours —, mais « une pièce » n'en porte aucun : une cacahuète pèse moins d'un gramme, une pomme cent cinquante, et les cent grammes du tableau ci-dessus faisaient **cinq cents grammes de cinq cacahuètes**. Le modèle, lui, a vu l'assiette. Chaque ligne reconnue porte donc un poids total, et le forfait ne sert plus que lorsque personne ne s'est prononcé. La ligne reste signalée comme estimée : il a estimé, il n'a pas pesé.

**Ces forfaits ne sont qu'un repli : la portion nommée de la fiche l'emporte toujours.** Le tableau ci-dessus ne réservait cette clause qu'à `PIECE` et `SLICE`, et il se trompait — `servings.csv` porte « 1 bol » à **40 g** pour des céréales, contre les 250 g du forfait. La règle vaut pour toutes les unités nommées : tranche, bol, verre, cuillère à soupe, cuillère à café. L'assiette est la seule exception, et c'est volontaire — elle n'est pas une propriété de l'aliment, donc aucune fiche ne peut la mesurer ([D73](11-decisions.md#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée)).

**Les deux unités impériales sont des unités de saisie**, pas une conversion d'affichage ([D114](11-decisions.md#d114--lonce-est-une-unité-de-saisie-la-livre-un-affichage---validée)) : une ligne garde ce qui a été tapé, avec le poids d'une unité qui voyage avec elle — exactement comme une portion nommée. Le réglage décide de la paire proposée, et un plat déjà noté garde la sienne.

**La densité n'est pas une colonne de `food`.** Aucune source ne la publie — CIQUAL ne la donne pas, Open Food Facts pas davantage — et les trois valeurs ci-dessus ne se rattachent à une fiche qu'en devinant à partir de son nom. Elle est donc un paramètre de la conversion, nul aujourd'hui : un millilitre pèse un gramme, et le dit. La colonne naîtra avec ce qui l'écrira, un `densities.csv` sur le modèle de `servings.csv` étant le candidat le plus simple.

---

## Licences et obligations

Ce ne sont pas des formalités : elles conditionnent ce que le projet a le droit de faire.

**CIQUAL — Licence Ouverte Etalab 2.0.** Réutilisation libre, y compris commerciale. Obligation : citer la paternité et la date de version. L'écran « À propos » affiche *« Table de composition nutritionnelle CIQUAL 2025 — ANSES »*.

**Open Food Facts — ODbL 1.0.** Trois obligations, dont deux souvent ignorées :

1. **Attribution** — mention visible dans « À propos ».
2. **Partage à l'identique** — toute *base de données dérivée* qui serait redistribuée doit l'être sous ODbL. Ici, l'application ne redistribue aucune base : elle interroge l'API et met en cache pour l'utilisateur. Aucune obligation ne se déclenche.
3. **Maintien de la clause** — si une base dérivée était un jour publiée (un jeu de portions agrégé, par exemple), elle devrait porter la même licence.

**Sauvegardes et exports.** Un fichier de sauvegarde contient des fiches issues d'Open Food Facts. Il est produit *par* l'utilisateur, *pour* l'utilisateur, et n'est pas publié : ce n'est pas une redistribution au sens ODbL. La mention de source est néanmoins incluse dans l'en-tête du fichier exporté — coût nul, ambiguïté levée.

**Interdiction ferme.** Ne jamais publier dans le dépôt un extrait massif de la base Open Food Facts. Le cache est local, produit à l'usage, et n'est pas versionné.
