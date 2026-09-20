# 12 — Plan de développement

Dans quel ordre construire, et comment savoir qu'une étape est finie.

## Principe : des tranches verticales

Le découpage naturel — « d'abord toute l'interface, ensuite on branche » — découpe le travail en couches horizontales. Une couche n'est jamais terminée, donc jamais confrontée à la réalité : on dessine des écrans contre des formes de données imaginées, et le jour où le domaine arrive avec ses vraies formes, il faut refaire les écrans.

Chaque itération traverse donc **toutes** les couches et livre une capacité utilisateur complète, installable le soir même sur un vrai téléphone.

Une exception assumée : l'itération 0, qui est horizontale parce que son contenu ne peut pas être ajouté après coup.

### La technique qui réconcilie les deux

À l'intérieur d'une tranche, commencer par l'écran est une bonne idée — à condition de l'écrire **contre les interfaces du domaine, avec une implémentation en mémoire**. L'écran est réel, les contrats sont réels, et remplacer le faux par Room est un changement d'une ligne dans le module Hilt.

C'est le rôle de `:core:testing` : les implémentations bidon n'y sont pas des béquilles de test, ce sont les premières implémentations des ports.

## Définition de « terminé »

Elle s'applique à chaque tranche, sans exception. Une tranche qui coche cinq critères sur six n'est pas terminée à 83 % — elle n'est pas terminée.

- La capacité fonctionne sur un appareil réel, pas seulement dans un aperçu.
- `./gradlew check` passe : tests, ktlint, detekt, Android Lint.
- Les règles du domaine touchées par la tranche sont testées, y compris leurs cas limites.
- Aucune couleur, durée ou dimension codée en dur hors de `:core:designsystem`.
- Les tranches précédentes fonctionnent toujours.
- Toute décision prise en route est inscrite dans [11](11-decisions.md), même en trois lignes.

---

## Itération 0 — Le socle

La seule itération qui ne livre aucune fonctionnalité. Son contenu est précisément ce qu'on ne peut pas rétro-installer.

**Contenu**

- Projet Gradle, catalogue de versions dans `gradle/libs.versions.toml`, aucune version écrite en dur dans un `build.gradle.kts`.
- **Trois modules seulement** : `:app`, `:domain`, `:core:designsystem`. Les treize autres du doc [06](06-architecture.md) naissent quand ils ont un contenu — seize modules vides, c'est de l'architecture en vitrine.
- `:domain` déclaré en module **Kotlin/JVM**, sans le plugin Android.
- Hilt câblé de bout en bout, avec un seul point d'injection pour prouver que ça marche.
- ktlint, detekt et les trois règles personnalisées du doc [10](10-qualite-et-livraison.md#analyse-statique), plus le workflow GitHub Actions.
- Design system complet : `MacroColors`, `NeonTheme`, `Motion`, puis `MacroRing`, `MacroBar`, `NeonButton`, `SourceBadge`. `MacroHexagon` s'y ajoute en tranche 1, quand il a des chiffres réels à montrer.

**Terminé quand**

- Un `import android.*` ajouté dans `:domain` **fait échouer le build**, et le message dit pourquoi.
- Une couleur écrite en dur dans `:app` fait échouer detekt.
- L'application affiche une galerie des composants sur un appareil, en thème sombre et clair.
- La CI est verte sur une pull request, en moins de 8 minutes.

**Piège** : céder à la tentation de créer les seize modules « pour être tranquille ». Un module se crée le jour où on a un fichier à y mettre.

---

## Tranche 1 — « Je vois ma journée »

**Contenu**

- `:core:database` : Room avec les tables `dish` et `food_entry`. Migrations actives dès la version 1, schémas exportés et versionnés. La table `food` attend la tranche 3, qui est la première à la remplir ([D34](11-decisions.md)).
- `:domain` : les modèles `Macros`, `MacroTotals`, `FoodEntry`, `Dish`, `DaySummary` ; le port `DiaryRepository` ; le cas d'usage `GetDaySummary` ; les abstractions `Clock` et `DispatcherProvider`.
- `:feature:home` : l'écran d'accueil réel, avec l'hexagone des macros ([D33](11-decisions.md)) et les barres chiffrées.
- Deux implémentations du port : `InMemoryDiaryRepository` d'abord, `RoomDiaryRepository` ensuite.
- Un objectif **codé en dur** (2 000 kcal et sa répartition), remplacé en tranche 4. C'est une dette assumée, et elle est écrite ici.

**Terminé quand**

- L'accueil affiche les six compteurs alimentés par Room, et chaque plat expose ses six apports — pas seulement ses calories.
- Basculer entre l'implémentation en mémoire et Room ne change **qu'une ligne** dans le module Hilt.
- `GetDaySummary` est testé avec une horloge figée.
- Un test de migration existe, même trivial, pour que le mécanisme soit en place.

**Pièges** : un `LocalDate.now()` écrit en dur quelque part ; `fallbackToDestructiveMigration` « le temps du développement ».

---

## Tranche 2 — « J'ajoute un aliment à la main »

C'est le vrai premier jalon : à la fin de cette tranche, **l'application est utilisable au quotidien**. Objectif en dur et saisie manuelle, mais tu peux t'en servir — et donc juger l'ergonomie pour de bon.

**Contenu**

- `:feature:entry` : l'**écran de validation** décrit en [02](02-parcours-et-ecrans.md#écran-de-validation-dentrée). Lignes éditables, quantité, macros dépliables, date. Pas de repas de destination : les lignes forment un plat ([D31](11-decisions.md)).
- `EntryDraft` : le modèle d'entrée de cet écran, **indépendant de la source**.
- Cas d'usage `LogDish`, `UpdateDish`, `DeleteEntry`, `RestoreDish`, `GetDishDraft`, `CreateDraft` — le plat est l'unité de saisie, la ligne l'unité de suppression ([D43](11-decisions.md)).
- Navigation Compose à routes typées : l'accueil et la validation deviennent deux destinations.
- Suppression par balayage, avec annulation par `Snackbar`.

~~Le port `CustomFoodStore` et le formulaire de création d'un aliment personnel.~~ Reporté en tranche 3, avec la table `food` qui lui donne un sens et la recherche qui le rend utile ([D40](11-decisions.md)).

**Terminé quand**

- On ajoute, modifie et supprime une ligne, et les totaux suivent immédiatement.
- L'écran de validation ne contient **aucune** référence à un mode de saisie particulier.
- Un test prouve que modifier un aliment ne change pas les entrées déjà enregistrées ([D05](11-decisions.md)). *Sans table `food`, la forme éprouvable de cette règle est celle-ci : rouvrir un plat rend les valeurs **figées à l'enregistrement**, et corriger une ligne n'en réécrit aucune autre. La version complète arrive en tranche 3, quand un aliment existera pour être modifié.*

**Le piège central du projet est ici.** Cet écran est le point de convergence des quatre modes de saisie. Écrit « pour la saisie manuelle », il faudra le généraliser trois fois. Il doit accepter dès maintenant *n* lignes venant de *n'importe quelle* source.

---

## Tranche 3 — « Je cherche un aliment »

**Contenu**

- `:tooling:ciqual-import` : tâche Gradle qui convertit le XML de l'ANSES en `ciqual.db`.
- `CiqualValueParser` et ses tests : `traces`, `< n`, `-`, `NC`, virgule décimale, chaîne vide, **et l'écriture inconnue**, qui arrête l'import.
- ~~Table FTS5 avec `unicode61 remove_diacritics 2`.~~ FTS4 sur un nom normalisé à l'import : ni l'un ni l'autre ne tient sous `minSdk 26` ([D49](11-decisions.md)).
- `servings.csv`, la table des portions usuelles.
- Table `food`, migration 1 → 2, colonne `food_entry.food_id` — les dettes échues de [D40](11-decisions.md).
- Modale de recherche, récents, favoris, formulaire d'aliment personnel. Ports `FoodSearch`, `FoodLookup`, `RecentFoods`, `FavoriteFoods`, `CustomFoodStore`, `FoodUsage`.

**Terminé quand**

- Des résultats apparaissent en moins de 150 ms, hors-ligne, dès le deuxième caractère et 120 ms après la dernière frappe ([D23](11-decisions.md)).
- « creme brulee » trouve « crème brûlée ».
- Chaque convention d'écriture de CIQUAL a son cas de test, et `null` ne se confond jamais avec `0`.
- Un test prouve que modifier un aliment ne change pas les entrées déjà enregistrées ([D05](11-decisions.md)) — la forme complète, que la tranche 2 ne pouvait pas écrire.

**Piège** : traiter une valeur inconnue comme un zéro. C'est l'erreur la plus facile à commettre et la plus difficile à repérer — elle fausse des mois de journal en silence.

> **Livrée.** 3 484 aliments et 67 portions en 824 Ko, `creme brulee` vérifié sur les données livrées. Ce que la tranche ne construit pas est écrit en [D50](11-decisions.md).

---

## Tranche 4 — « L'application connaît mon objectif »

**Contenu**

- `:domain` : `EnergyExpenditureCalculator`, `MacroDistributionPolicy`, `GoalSafetyPolicy`, `CalculateDailyGoal`.
- Tables `profile`, `weight_entry`, et `goal` **versionnée**.
- `:feature:onboarding` : les cinq étapes.
- `:feature:settings` : réglages profil, recalcul, édition manuelle avec verrouillage des champs édités.

**Terminé quand**

- L'exemple chiffré du doc [03](03-nutrition-calculs.md#exemple-complet) passe en test, au kcal près, **contrôle de cohérence énergétique compris** — c'est lui qui a révélé les 70 kcal de fibres comptées deux fois ([D24](11-decisions.md)).
- Chaque garde-fou est testé sur ses deux bornes.
- Une journée passée est comparée à l'objectif **actif ce jour-là**.
- ~~L'objectif codé en dur de la tranche 1 a disparu, et la dette correspondante est rayée.~~ **Fait** : `DailyGoal.Placeholder` n'existe plus, `GetDaySummary` lit l'objectif actif du jour, et `DaySummary.goal` est nullable ([D55](11-decisions.md)).
- On relit et corrige son profil ; un recalcul suit, et un objectif **saisi à la main** y survit et se voit comme tel ([D59](11-decisions.md), [D60](11-decisions.md)).

**Piège** : mettre à jour un objectif en place « parce que c'est plus simple ». Voir [D04](11-decisions.md).

> **Livrée.** Le calcul, les tables versionnées, les cinq étapes et les réglages profil. L'exemple de [03](03-nutrition-calculs.md#exemple-complet) passe au kcal près, et chaque garde-fou est éprouvé sur ses deux bornes. La colonne `manual_fields` a vécu le temps d'une décision : le verrou par compteur est devenu un mode porté par `origin`, et elle est partie en migration 3 → 4 ([D60](11-decisions.md)). Ce que la tranche ne construit pas est écrit en [D55](11-decisions.md) et [D59](11-decisions.md).

---

## Tranche 5 — « Je scanne »

**Contenu**

- `:integration:scanner` : CameraX et ML Kit, formats EAN/UPC uniquement, anti-rebond à deux lectures identiques.
- `:integration:openfoodfacts` : Retrofit, DTO, correspondance, `User-Agent` obligatoire, retrait exponentiel.
- Mise en cache permanente dans `food`, parcours complet du produit absent.

**Terminé quand**

- Un scan affiche la fiche en moins de 2 s ; le deuxième scan du même produit est instantané et fonctionne en mode avion.
- Un produit sans valeur de fibres s'enregistre quand même, avec le trou visible.
- Un produit absent d'Open Food Facts se crée à la main **en conservant son code-barres**.

**Piège** : oublier le `User-Agent`. Open Food Facts bloque les clients anonymes, et le symptôme ressemble à une panne réseau.

> **En cours.** Le client Open Food Facts ([D63](11-decisions.md#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée)), puis le cache : `LookupBarcode` lit le catalogue avant le réseau, une fiche récupérée y est versée avec sa date, et `is_liquid` et `fetched_at` arrivent en migration 5 → 6 ([D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée)). Puis `:integration:scanner` : le décodage, l'anti-rebond éprouvé sur la JVM, un aliment personnel qui garde son code-barres, et un cinquième `DraftOrigin` que l'écran de validation a accepté **sans être touché** ([D65](11-decisions.md#d65--le-décodeur-est-un-module-à-part-et-sa-seule-règle-tient-sur-la-jvm---validée)). Puis la modale elle-même, ses quatre états et le graphe qui réunit les modes de saisie ([D66](11-decisions.md#d66--la-modale-de-scan-et-les-trois-modes-de-saisie-réunis-dans-le-graphe---validée)).

Enfin la suggestion « Chercher dans Open Food Facts », dernière dette de [D50](11-decisions.md#d50--ce-que-la-tranche-3-ne-construit-pas---validée) : une recherche **par nom**, offerte en dernière ligne de résultats et déclenchée sur un tap ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)).

> **Ce qui a tourné sur appareil.** Le scan de bout en bout : caméra, permission, décodage, appel réel à Open Food Facts, mise en cache, et création manuelle d'un produit absent reconnu au scan suivant. Deux défauts d'affichage y ont été trouvés et corrigés — une surimpression illisible ([D68](11-decisions.md#d68--un-voile-est-une-surface-et-il-reste-sombre-dans-les-deux-thèmes---validée)) et un aperçu qui continuait de tourner sous elle ([D69](11-decisions.md#d69--laperçu-se-fige-sur-la-trame-qui-a-porté-la-lecture---validée)) ; aucun des deux n'était visible autrement qu'en tenant le téléphone, et **les deux corrections y ont été reconfirmées** en thème sombre : le panneau se lit, et la trame figée coïncide avec ce que l'aperçu montrait — même cadrage, bien droite. C'est le seul point qui séparait `ContentScale.Crop` du `FILL_CENTER` de `PreviewView`, et aucun test ne pouvait le dire.

> **Les migrations 3 → 4, 4 → 5 et 5 → 6 ont tourné sur une base peuplée**, et ce n'est plus une supposition. La base du Fairphone porte des lignes écrites le 11 août à midi, c'est-à-dire sous la **version 3** du schéma — `VERSION = 4` date du même soir, `5` de la nuit, `6` du lendemain. Elle est aujourd'hui en `user_version = 6`, `integrity_check` à `ok`, avec ses 7 plats, ses 13 lignes de journal et son profil intacts. Les deux index de `food` sont là, dont l'unique `(source, source_ref)` — c'est le piège de [D60](11-decisions.md#d60--un-objectif-est-calculé-ou-saisi-et-on-consulte-avant-de-corriger---validée) et [D62](11-decisions.md#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée), qu'aucune validation Room ne rattrape. Les six fiches Open Food Facts portent toutes leur `fetched_at`, ce qui éprouve [D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée) là où il compte, et `source_ref` range bien ses deux espaces de noms — 7 codes CIQUAL, 6 codes-barres ([D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée)).

> **Ce qui n'a toujours pas tourné.** La suggestion « Chercher dans Open Food Facts » et sa liste distante ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)). Les écrans de [D59](11-decisions.md#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) à [D62](11-decisions.md#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée) : réglages profil, menu contextuel, étoile, liste des favoris. Et **le thème clair**, sur lequel se jugent les deux corrections ci-dessus : l'appareil de test est en thème sombre, donc rien de ce que [D68](11-decisions.md#d68--un-voile-est-une-surface-et-il-reste-sombre-dans-les-deux-thèmes---validée) décide pour le thème clair n'a été vu.

---

## Tranche 6 — « Je photographie ou je décris »

**Contenu**

- `:integration:ai` : `FoodRecognizer`, `RecognitionInput` en `Photo | Text`, les six implémentations.
- Prompt versionné dans les assets, parseur tolérant, `NutritionResolver`.
- Clés dans `EncryptedSharedPreferences`, intercepteur de redaction, compteur de coût.
- Première implémentation de `FoodContributionTarget` ([D70](11-decisions.md#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée)).

**Terminé quand**

- Une photo produit une proposition éditable en moins de 10 s.
- Les modes photo et texte partagent **le même pipeline** : seule l'entrée diffère.
- Ajouter un septième fournisseur ne demande qu'une classe et une entrée d'énumération.
- Un test prouve que le fichier temporaire est supprimé, y compris quand l'appel échoue.
- Aucune clé n'apparaît dans les journaux, en debug comme en release.
- Une fiche saisie à la main **peut** être reversée à Open Food Facts, et rien ne part sans un geste explicite. **Fait** : le port et le client ([D90](11-decisions.md#d90--la-contribution-part-sous-le-compte-de-lutilisateur-et-jamais-sans-lui---validée)), puis la proposition au moment de l'enregistrement et son récapitulatif ([D91](11-decisions.md#d91--la-contribution-se-propose-au-moment-où-la-fiche-vient-dêtre-écrite---validée)).

**Piège** : un `when` sur le fournisseur ailleurs que dans la fabrique. C'est le signal que l'abstraction a fui.

> **Livrée.** Les six fournisseurs, les deux modales, la résolution, le compteur de coût, et la contribution à Open Food Facts. Deux fournisseurs sur six sont éprouvés sur un vrai compte ; les quatre autres restent en réserve ([D85](11-decisions.md)). Aucun envoi réel n'a encore atteint Open Food Facts — le bac à sable existe pour ça ([D90](11-decisions.md#d90--la-contribution-part-sous-le-compte-de-lutilisateur-et-jamais-sans-lui---validée)).
>
> Le détail, dans l'ordre où il a été construit. Le contrat de reconnaissance et son parseur ([D72](11-decisions.md#d72--le-contrat-de-reconnaissance-et-un-parseur-qui-ne-croit-pas-le-modèle-sur-parole---validée)) : la partie qui s'éprouve entièrement sur la JVM, livrée avant tout réseau — le même ordre que la tranche 5, où le client a précédé l'écran. Trois signatures de [05](05-ia.md) n'ont pas survécu au contact du code des tranches 1 à 5, et un neuvième cas d'erreur est apparu.

Puis la **conversion des quantités** ([D73](11-decisions.md#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée)) : la charnière entre le vocabulaire d'estimation du modèle et les grammes du journal. Elle a corrigé le tableau de [04](04-sources-de-donnees.md#conversion-des-quantités), qui se trompait d'un facteur six sur un bol de céréales, et **la colonne `density` n'est pas venue** — rien ne l'écrirait, ce qui est exactement le critère de [D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée).

Puis le **score de décision** ([D74](11-decisions.md#d74--un-seul-score-pour-trier-et-pour-décider-et-le-tri-nen-bouge-pas---validée)) : les seuils de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) s'appliquaient à un score qui n'existait pas. Les poids de l'étape « candidats » étaient en fait déjà écrits, dans le `FoodRanking` de l'écran de recherche ; il monte dans `:domain` et sert les deux appelants, sans que l'ordre affiché change d'un rang — la mise à l'échelle est strictement croissante, et un test le tient.

Puis la **normalisation et la recherche de candidats** ([D75](11-decisions.md#d75--la-normalisation-retire-les-articles-tout-de-suite-et-les-pluriels-seulement-en-second-recours---validée)) : ce qui branche enfin le score et le verdict, que la livraison précédente laissait sans appelant. L'étape 1 de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) s'est révélée avoir deux moitiés qui se livrent à deux moments — les articles partent avant la première requête, parce que les deux recherches sont conjonctives et qu'un article gardé rend une réponse vide ; les pluriels ne partent qu'après son échec, parce que l'index de l'ANSES garde les siens. Les trois alternatives de la zone de relecture sont soumises au même seuil que la décision, ce qui laisse 0,40 dans un seul fichier.

Puis le **premier fournisseur et toute la structure qui l'entoure** ([D76](11-decisions.md#d76--trois-prescriptions-de-docs05-tombent-au-contact-de-lapi-et-le-raisonnement-reste-actif---validée)) : la pile HTTP, la fabrique, le prompt en asset, l'intercepteur de redaction, et Anthropic — celui dont [D73](11-decisions.md#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée) a fait le défaut. Trois prescriptions de [05](05-ia.md) y sont tombées au contact de l'API : `temperature` rend un `400`, le raisonnement est actif par défaut et se règle par l'effort plutôt qu'en le coupant, et `max_tokens` plafonne raisonnement et réponse ensemble. La sortie structurée y a remplacé l'outil forcé, ce qui garde **un seul parseur** pour les six fournisseurs.

Puis **l'espace des clés et le bouton Tester** ([D77](11-decisions.md#d77--la-clé-va-dans-le-keystore-en-direct-et-le-bouton-tester-est-une-vraie-analyse---validée)) : le hub de réglages naît enfin, à l'échéance que [D59](11-decisions.md#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) avait fixée. Une quatrième prescription de [05](05-ia.md) y est tombée — `EncryptedSharedPreferences` est dépréciée depuis juin 2025 —, et la clé va donc dans le Keystore en direct. Le bouton **Tester** est une vraie reconnaissance et non un appel allégé : un bouton qui dit oui à tort est pire que pas de bouton.

Puis **le diagnostic et le deuxième fournisseur** ([D78](11-decisions.md#d78--le-fournisseur-garde-la-parole-parce-quun-message-inventé-ne-se-vérifie-pas---validée), [D79](11-decisions.md#d79--gemini-entre-au-prix-annoncé-et-deux-tests-attrapent-ce-que-la-relecture-avait-laissé-passer---validée)) : le premier essai réel a échoué sur un message qui ne permettait rien, donc le fournisseur garde désormais la parole sous le message traduit. Et Gemini est entré **au prix annoncé** — une classe, une entrée d'énumération, une branche dans le `when` —, ce qui vérifie pour la première fois la promesse d'ouverture-fermeture de cette tranche au lieu de l'affirmer. Un `null` sérialisé de trop y a été attrapé par un test, pas par une relecture.

Puis **la modale texte, et le premier appel payant qui parte d'ailleurs que des réglages** ([D80](11-decisions.md#d80--la-proposition-passe-par-un-dépôt-et-lécran-de-validation-ne-change-pas-dun-mot---validée)) : quatre livraisons sans appelant se chaînent enfin, et l'écran de validation n'a pas bougé d'un mot. Une route ne portant pas cinq lignes, la proposition passe par un dépôt et `DraftOrigin` gagne sa cinquième variante — la seule sans charge. L'accueil gagne « Décrire », grisé sans clé et tapable quand même ; « Photographier » attend sa modale.

Puis **les quatre fournisseurs restants** ([D81](11-decisions.md#d81--quatre-fournisseurs-pour-une-classe-parce-quils-parlent-la-même-langue---validée)) : une seule classe les sert, parce qu'ils parlent tous `chat/completions`. Le dernier n'est pas un fournisseur mais **une porte** — une URL et un modèle suffisent à y brancher un relais ou un modèle local. La seule variation, le schéma, est un paramètre de construction et non un `when` : la fabrique reste le seul endroit qui sache qui est qui.

Puis **la modale photo, et l'accord qui se demande une fois** ([D82](11-decisions.md#d82--la-prise-de-vue-est-déléguée-et-laccord-se-demande-une-fois---validée)) : le quatrième mode de saisie, et le seul endroit où une donnée personnelle quitte l'appareil sans qu'un code-barres l'ait demandée. L'aperçu CameraX de [02](02-parcours-et-ecrans.md#écran-dia) n'est pas là — l'appareil photo du système prend la photo, écrit dans notre cache et donne la galerie sans une ligne, là où un viseur intégré aurait été une seconde implémentation entièrement invérifiable ici.

Puis **le repli IA groupé** ([D83](11-decisions.md#d83--le-repli-invente-des-chiffres-une-seule-fois-et-en-le-disant---validée)) : l'étape 4 de [04](04-sources-de-donnees.md), et l'exception à la règle qui veut que le modèle identifie et que les bases calculent. Un seul appel pour toutes les lignes non résolues, rien qui entre au catalogue — une estimation ne porte aucune fiche, donc l'interdit est structurel —, et un badge sur chaque ligne concernée.

Puis **le compteur de coût** ([D84](11-decisions.md#d84--le-compteur-dit-ce-qui-est-facturé-dans-la-devise-où-la-facture-tombe---validée)) : par modèle et non par fournisseur, parce que les tarifs sont attachés aux modèles ; il compte ce qui est **facturé** et non ce qui est tenté ; et il annonce des dollars, la devise où la facture tombe.

Le reste de la tranche : la contribution à Open Food Facts ([D70](11-decisions.md#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée)).

**Trois points arbitrés d'avance**, écrits en [D73](11-decisions.md#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée) : les boutons IA restent visibles et **grisés** sans clé, le modèle par défaut est `claude-opus-5`, et les appels passent par **Retrofit** comme Open Food Facts — une seule pile HTTP pour les six fournisseurs, donc un seul intercepteur de redaction.

**La contribution n'est pas là par affinité de sujet.** Elle est là parce que le `NutritionResolver` apporte `density`, donc la première fiche Open Food Facts qui se consulte, donc le premier écran d'où elle puisse s'offrir — le même que celui qu'attend `user_edited_fields` depuis [D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée). Trois points restent ouverts et sont écrits en [D70](11-decisions.md#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée) : compte ou anonyme, ce qu'on envoie d'une fiche partielle, et la forme du consentement — c'est la première écriture sortante de l'application.

---

## Tranche 7 — « Je consulte mon historique »

**Contenu**

- Bandeau calendrier, calendrier étendu, écran Journée.
- Journal de poids, moyenne mobile sur 7 jours, `SuggestGoalAdjustment`.

**Terminé quand**

- Une journée sans saisie est visuellement neutre, et n'est **jamais** comptée comme une journée à zéro.
- Aucune suggestion d'ajustement n'est appliquée sans accord explicite.
- Les conditions de déclenchement — adhérence, persistance, nombre de pesées — sont testées.

> **Livrée.** Le calendrier, l'écran Journée, le journal de poids et l'adaptation hebdomadaire. Les trois critères sont tenus ; rien de ce qui se voit ni de ce qui se touche n'a été éprouvé ailleurs que dans un compilateur.
>
> Le bandeau, le mois et l'écran Journée ([D92](11-decisions.md#d92--une-journée-sans-saisie-na-pas-de-ligne-et-cest-structurel---validée), refondus en [D93](11-decisions.md#d93--le-calendrier-vient-dune-bibliothèque-et-la-semaine-est-calendaire---validée) : semaine calendaire, mois déplié dans l'accueil, pastilles dimensionnées à l'écran). Le premier critère est tenu **structurellement** — une journée sans saisie n'a pas de ligne dans le calendrier, donc rien à dessiner.
>
> Puis le **journal de poids** et sa courbe ([D94](11-decisions.md#d94--le-journal-de-poids-se-lit-en-trois-traits-et-la-tendance-se-tait-sous-trois-pesées---validée)) : trois tracés dessinés à la main, une échelle séparée du dessin pour être éprouvable, et le plancher de trois pesées de [03](03-nutrition-calculs.md#signal) étendu au lissage affiché.
>
> Enfin l'**adaptation hebdomadaire** ([D95](11-decisions.md#d95--lajustement-se-propose-ne-sapplique-jamais-et-se-tait-presque-toujours---validée)). Le deuxième critère tient à une propriété : *accepter* est le seul chemin qui écrit un objectif, et les trois issues vivent dans un seul cas d'usage pour que cela ait un endroit où se tester. Le troisième est couvert par vingt-neuf cas — adhérence, persistance, nombre de pesées, silence après une réponse, et les garde-fous que la correction repasse avant d'être annoncée.
>
> Ce que le vert ne prouve pas : **que la carte se voie un jour**. Toutes ses conditions réunies demandent trois semaines de pesées régulières et dix jours notés sur quatorze ; le premier vrai déclenchement dira si le seuil de 0,15 kg/semaine parle trop, ou trop peu.

---

## Tranche 8 — « Mes données sont à l'abri »

**Contenu**

- Port `BackupTarget`, instantané JSON versionné avec sa chaîne de migrations.
- Export et import de fichier par le Storage Access Framework **d'abord**, Drive ensuite.
- WorkManager, rotation sur cinq fichiers, chiffrement optionnel.

**Terminé quand**

- Export, effacement complet, import : l'état est identique.
- Un test vérifie qu'aucune clé API ne figure dans le fichier produit.
- Drive et le fichier local sont deux implémentations du même port, interchangeables.

**Ordre volontaire** : le fichier local avant Drive. Il valide tout le format sans dépendre d'OAuth, et c'est lui qui garantit la réversibilité du projet.

> **En cours.** Le **format** et sa plomberie ([D96](11-decisions.md#d96--la-sauvegarde-emprunte-les-mappeurs-et-le-format-ne-porte-que-ce-que-la-base-tient---validée)) : le JSON compressé, sa chaîne de migrations, les trois ports, le magasin Room transactionnel, la cible interne et les trois cas d'usage. Le deuxième critère est tenu par deux cas — l'un cherche une vraie clé dans les octets produits, l'autre énumère les sections du fichier et tombe dès qu'une s'y ajoute. Le premier l'est aussi, sur la vraie base et par les vrais dépôts.
>
> Restent les **écrans** — export et import par le Storage Access Framework, bouton d'effacement — puis le **chiffrement optionnel**, puis **Drive**. Ce dernier attend un projet Google Cloud, un identifiant OAuth et une empreinte SHA-1 enregistrée : rien de cela ne se fabrique depuis le dépôt, et c'est Charly qui l'ouvre. Le troisième critère de fin reste donc ouvert, mais le port existe et une implémentation l'éprouve.

---

## Demandes issues de l'usage réel

Cinq demandes formulées après les premières analyses réelles, et qui ne rentrent dans
aucune tranche existante. Elles sont écrites ici plutôt que dans une tranche parce que
les tranches décrivent des **capacités** et que celles-ci corrigent la façon dont une
capacité déjà livrée se comporte quand on s'en sert vraiment.

### Les calories proposées à partir des macros

Corriger les macros d'une ligne laisse l'énergie inchangée, et c'est elle qui décide
si la ligne est enregistrable. Quand les quatre valeurs qui la déterminent sont
saisies, l'écran doit **proposer** — jamais imposer — le calcul correspondant.

Les facteurs sont ceux du règlement européen 1169/2011 : 4 kcal/g pour les protéines et
les glucides, 9 pour les lipides, 2 pour les fibres. Les glucides y sont déclarés
**hors fibres**, ce qui est la convention de CIQUAL comme d'Open Food Facts : les
additionner tels quels ne compte donc rien deux fois.

Si l'utilisateur choisit de recalculer les calories, la nouvelle mesure doit remplacer l'ancienne valeur.

> **Livrée** ([D87](11-decisions.md#d87--les-calories-se-proposent-et-une-valeur-minorée-se-dit-au-lieu-de-se-taire---validée)).
> Une pastille sous les six champs, qui n'apparaît que quand l'énergie manque ou
> qu'elle contredit les macros de plus de 10 kcal **et** de plus de 10 %. La valeur
> acceptée est marquée corrigée à la main : elle cesse de suivre la quantité et
> survit au rejeu d'un favori.
>
> Deux points ont été tranchés en route, contre la lettre écrite ici. Les fibres
> absentes **n'empêchent pas** de proposer quand l'énergie manque — sans quoi la
> pastille serait muette sur les lignes venues d'un modèle, qui ont le droit de se
> taire sur les fibres ([D83](11-decisions.md)) — et la valeur dit alors qu'elle est
> minorée. En revanche, quand l'énergie **est** là et que les fibres manquent, rien
> n'est proposé : l'écart peut n'être que les fibres qu'on ignore, et quinze fiches
> de l'ANSES y auraient perdu une mesure au profit d'un calcul incomplet.

### Un titre court sur chaque fiche

Les libellés de l'ANSES décrivent une préparation — « Poulet, blanc, sans peau, cuit au
four, sans matière grasse ajoutée » — et c'est ce qui rend une liste de trois aliments
illisible. Une seconde colonne, courte, tenue à côté du libellé d'origine.

**Le libellé d'origine ne bouge jamais** : c'est lui qui relie la fiche à sa source, et
c'est sur lui que la recherche compare. Le titre court est un affichage.

> **Livrée** ([D88](11-decisions.md#d88--le-titre-court-se-fabrique-hors-de-lapplication-et-il-ne-touche-jamais-au-libellé---validée)).
> La chaîne entière : un CSV versionné à côté de `servings.csv`, une colonne de
> `ciqual.db`, `Food.shortName`, et l'affichage dans les listes comme sur les
> nouvelles lignes de journal. L'écran de validation rappelle le libellé d'origine
> sous le champ.
>
> **La passe ne tourne pas dans l'application** : `./gradlew generateShortNames
> -PanthropicApiKey=...` produit le fichier, qu'on relit et corrige à la main avant
> de relancer `importCiqual`.
>
> ~~Six titres y sont écrits à la main ; les 2 085 autres attendent que la tâche soit
> lancée.~~ **1 289 titres sont écrits** ([D100](11-decisions.md#d100--un-titre-court-se-construit-et-deux-fiches-voisines-nen-partagent-jamais-un---validée)),
> et la longueur moyenne de ce qui s'affiche tombe de 41,4 à 30,7 caractères. Les
> 402 fiches longues restantes gardent leur libellé : une absence de titre est l'état
> d'avant, pas une régression, et la chaîne ne redemande que les codes absents.
>
> Le coût a été mesuré avant d'arbitrer, et il ne dissuade rien : **0,20 à 0,40 $**
> pour les 3 484 fiches. Ce qui coûte, ce sont les soixante-dix requêtes.

### La complétion des valeurs manquantes, sans écraser la source

CIQUAL laisse des trous — la feta sans énergie déterminée, les câpres —, et Open Food
Facts en laisse bien plus. Un petit modèle peut les combler.

**La règle qui commande toute la conception : ne jamais écraser une valeur d'origine.**
Une valeur complétée et une valeur mesurée ne se rangent pas au même endroit, sans quoi
un nouvel import de la table de l'ANSES écraserait les complétions, ou pire, les
prendrait pour des mesures. Il faut donc des colonnes distinctes et une lecture qui
préfère l'originale quand elle existe.

C'est le raisonnement de [D83](11-decisions.md#d83--le-repli-invente-des-chiffres-une-seule-fois-et-en-le-disant---validée),
poussé un cran plus loin : là, une estimation ne devenait jamais une fiche ; ici elle
entre au catalogue, et doit donc porter sa provenance **valeur par valeur**.

> **Livrée** ([D89](11-decisions.md#d89--une-valeur-complétée-ne-se-range-pas-où-une-valeur-mesurée-se-range---validée)).
> Six colonnes distinctes dans `ciqual.db`, une lecture qui préfère toujours
> l'originale, `Food.estimated` qui dit valeur par valeur d'où chaque teneur vient,
> et un contour en pointillés sur le champ concerné. Corriger une valeur efface sa
> marque, et elle seule.
>
> **Trois barrières, et elles ne se recouvrent pas** : on ne demande que les trous,
> le fichier refuse une complétion sur une teneur publiée, et la lecture préfère
> l'originale. La troisième n'était éprouvée par personne — la campagne de défaite
> l'a trouvée, parce que la situation ne peut pas exister dans la base livrée.
>
> **La passe ne tourne pas dans l'application** : `./gradlew generateCompletions
> -PanthropicApiKey=...`, puis relecture à la main et `importCiqual`. Deux
> complétions y étaient écrites à la main ; ~~les 553 autres attendent la tâche~~
> **elles sont écrites** ([D99](11-decisions.md#d99--les-trous-de-ciqual-sont-comblés-et-lénergie-ne-sy-devine-jamais---validée)).
> `ciqual.db` ne porte plus aucune valeur inconnue : 143 énergies, 223 sucres,
> 70 glucides, 70 fibres, 29 protéines et 20 lipides, toutes dans les colonnes `_est`.
>
> **Aucune des 143 énergies n'a été devinée.** Le règlement UE 1169/2011 la donne dès
> que les quatre teneurs qui la déterminent sont connues : 57 l'étaient déjà, les
> 86 autres le sont devenues une fois leurs macros complétées.

### Un nom trop long qu'on ne pouvait pas relire

Quatrième demande, et la plus courte à dire : *« le titre des aliments est parfois trop
long, et impossible de scroller pour voir le début — on voit la fin du titre, et la
seule manière de voir le début est de supprimer la fin. »*

> **Livrée** ([D97](11-decisions.md#d97--un-nom-daliment-se-replie-sur-deux-lignes-et-ne-contient-jamais-de-retour-à-la-ligne---validée)).
> Le champ de nom se replie sur **deux lignes** au lieu de défiler horizontalement, sur
> l'écran de validation comme sur celui d'un aliment personnel. Le curseur restait posé
> à la fin du texte et le champ se calait dessus : le début n'était jamais montré.
>
> La cause de fond, elle, reste la même : les libellés sont longs parce que la passe de
> titres courts n'a pas encore tourné. Le repli rend lisible ce qui restera long après.

**Ce qui a été arbitré :**

- **Quand.** Hors de l'application, comme le titre court : une tâche qu'on lance à la
  main, un fichier versionné. Seules **313 fiches sur 3 484** ont un trou — 0,04 $.
- **Avec quoi.** La même tâche et le même modèle que le titre court
  ([D88](11-decisions.md#d88--le-titre-court-se-fabrique-hors-de-lapplication-et-il-ne-touche-jamais-au-libellé---validée)),
  mais **une passe distincte** : un titre est un affichage, une valeur complétée est
  un chiffre inventé, et les deux n'appellent ni la même prudence ni la même relecture.
- **Ce que l'écran en dit.** Valeur par valeur, au contour pointillé de
  [D25](11-decisions.md) — jamais une couleur, et jamais une marque de fiche : une
  fiche dont trois valeurs sur six sont complétées doit dire **lesquelles**.
- **Ce qu'il advient d'une complétion quand la source se met à jour.** Elle est
  **effacée**. Une estimation a été produite contre un état précis de la fiche ;
  l'état change, elle ne décrit plus cette fiche-là, et la garder ferait resurgir un
  chiffre périmé si la mesure repartait. La refaire coûte quelques centimes.

Ce qui reste à faire est donc l'essentiel : les colonnes distinctes, la lecture qui
préfère l'originale, la marque à l'écran, et la seconde passe.

### « Abricot » qui devient « Jus d'abricot »

Cinquième demande, et la seule qui portait sur la justesse plutôt que sur le confort :
*« résoudre le problème des imprécisions des IA où "Abricot" devient "Jus d'abricot"
dans l'appli. »*

> **Livrée** ([D109](11-decisions.md#d109--le-modèle-choisit-la-fiche-parce-quil-en-sait-plus-que-notre-score---validée)).
> Le modèle n'avait jamais voix au chapitre sur la fiche retenue : il écrivait
> « abricot », et **notre score** choisissait parmi vingt candidats. La confiance restant
> haute, la ligne se remplissait sans même être signalée.
>
> Une case **« Analyse approfondie »**, éteinte par défaut, lui donne deux outils : il
> interroge le catalogue, reçoit jusqu'à six fiches par libellé avec leurs teneurs pour
> 100 g, et **choisit**. S'il n'y trouve pas son compte, il estime lui-même — comme
> avant. Trois recherches au maximum, puis l'analyse ordinaire reprend la main si la
> boucle échoue.

**Ce qui reste à vérifier** : que les choix soient effectivement meilleurs. Les cas
éprouvent la conversation — combien de tours partent, ce que l'outil reçoit, ce que la
boucle fait d'une réponse — et pas la qualité d'un choix, qu'aucun test ne peut
affirmer. Il faut photographier un abricot, avec une vraie clé, et regarder le mode
debug.

---

## Les quatre décisions qu'on ne rattrape pas

Tout le reste se corrige à peu de frais. Ces quatre-là contaminent l'ensemble du code si elles arrivent tard.

| | Décision | Pourquoi c'est irréversible en pratique |
|---|---|---|
| 1 | `Clock` injecté dès la première ligne | Jours, semaines, tendances : la moitié de cette application dépend du temps. Le rattraper, c'est rouvrir chaque fichier. |
| 2 | Migrations Room dès la première table | Le jour où trois semaines de repas sont sur un vrai téléphone, il est trop tard pour prendre l'habitude. |
| 3 | Écran de validation générique en tranche 2 | Sinon quatre écrans divergents, et une généralisation à faire trois fois. |
| 4 | Aucune valeur de style hors du design system | Une règle ajoutée après 5 000 lignes ne s'applique pas rétroactivement : elle produit 200 avertissements qu'on finit par désactiver. |

## Sur la dette technique

Viser zéro dette produit son propre défaut : l'abstraction préventive, le port avec une seule implémentation, l'interface « au cas où ». C'est de la dette aussi, simplement plus flatteuse.

L'objectif utile est différent : **de la dette choisie, écrite, et placée là où elle est peu coûteuse à défaire.** L'objectif codé en dur de la tranche 1 en est l'exemple — c'est un raccourci, il est documenté, et sa date de péremption est connue.

Quand un raccourci est pris, il reçoit une entrée `~ par défaut` dans [11](11-decisions.md). Trois lignes, et il cesse d'être un piège pour devenir une décision.

## Correspondance avec la feuille de route

| Tranches | Version du doc [10](10-qualite-et-livraison.md#feuille-de-route) |
|---|---|
| Itération 0 à tranche 4 | 0.1 |
| Tranche 5 | 0.2 |
| Tranche 6 | 0.3 |
| Tranche 7 | 0.4 |
| Tranche 8 | 0.5 |
