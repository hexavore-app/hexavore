# 11 — Journal des décisions

Les choix structurants, leur raison, et ce qu'ils coûtent. Une décision sans contrepartie explicitée n'est pas une décision, c'est une préférence.

Format : contexte, choix, alternatives écartées, conséquences. Court. Une décision future ajoute une entrée, elle ne réécrit pas les anciennes — savoir ce qu'on croyait à l'époque a de la valeur.

**Statut des entrées** : `✓ validée` par l'auteur du projet · `~ par défaut` retenue faute de contre-indication, ouverte à révision · `⊘ remplacée par Dxx` révisée depuis, texte conservé.

Une entrée remplacée n'est **jamais** supprimée ni réécrite. Savoir ce qu'on croyait à l'époque, et pourquoi le raisonnement a cédé, est ce qui permet de rejuger la question sur autre chose qu'une impression le jour où elle revient. Le marqueur dans le titre suffit à ne pas s'y tromper en lisant.

---

## D01 — Kotlin natif et Jetpack Compose · ✓ validée

**Contexte.** Application Android uniquement, forte dépendance à la caméra, au décodage de codes-barres et à une interface animée.

**Choix.** Kotlin + Jetpack Compose, natif.

**Écarté.** *Flutter* : ouvre iOS, mais caméra et ML Kit passent par des greffons tiers et la couche native reste à écrire pour Keystore et les widgets. *Compose Multiplatform* : même promesse, écosystème moins mûr sur les points sensibles ici. *React Native* : mal adapté au traitement d'image et à une interface fortement animée.

**Conséquences.** Un portage iOS demanderait une réécriture de l'interface. Le découpage en modules ([06](06-architecture.md)) garde `:domain` en Kotlin pur, donc réutilisable via Kotlin Multiplatform si la question se pose un jour. Le coût de sortie est limité à ce qui devrait de toute façon être réécrit.

---

## D02 — CIQUAL embarqué, Open Food Facts en ligne · ✓ validée

**Contexte.** Deux besoins qu'une source unique ne couvre pas : reconnaître un produit emballé par son code-barres, et trouver un plat générique par son nom.

**Choix.** CIQUAL 2025 en base SQLite dans l'APK pour la recherche ; API Open Food Facts pour les codes-barres, avec cache local permanent.

**Écarté.** *Open Food Facts seul* : la recherche de « lasagne » y renvoie des barquettes industrielles, et rien ne fonctionne hors-ligne. *Dump Open Food Facts complet* : APK de plusieurs centaines de mégaoctets, données figées. *FatSecret ou Nutritionix* : meilleure couverture de codes-barres, mais clé commerciale impossible à publier dans un dépôt libre.

**Conséquences.** +4 Mo d'APK. Recherche instantanée et hors-ligne. Environ 1 produit sur 10 absent d'Open Food Facts, compensé par la création manuelle qui conserve le code-barres. Obligations de licence documentées en [04](04-sources-de-donnees.md#licences-et-obligations).

---

## D03 — L'IA extrait, la base calcule · ✓ validée

**Contexte.** Deux façons d'exploiter une photo : demander directement les macros au modèle, ou lui demander seulement d'identifier les aliments.

**Choix.** Le modèle ne rend qu'une liste `{aliment, quantité, unité, confiance}`. Les macros viennent de CIQUAL ou d'Open Food Facts, avec un repli sur estimation IA uniquement pour les lignes sans correspondance.

**Écarté.** *Macros directement par le modèle* : plus simple, plus souple sur les plats exotiques, mais deux analyses de la même assiette donnent deux résultats différents, et aucun chiffre n'est traçable. *Les deux côte à côte* : le plus transparent, mais double les appels et charge l'écran de validation.

**Conséquences.** Résultats reproductibles et sourçables, sortie de modèle 4 fois plus courte donc moins chère. En contrepartie, une étape de résolution à écrire et à régler ([04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment)), et une dépendance à la qualité de la correspondance textuelle.

---

## D04 — Objectifs versionnés plutôt que mis à jour en place · ✓ validée

**Contexte.** Un objectif change : recalcul, édition manuelle, ajustement hebdomadaire. Une journée passée doit-elle être jugée sur l'objectif d'aujourd'hui ?

**Choix.** Non. Chaque modification crée une ligne dans `goal`, l'ancienne reçoit une date de fin. Une journée est toujours comparée à l'objectif actif ce jour-là.

**Écarté.** *Mise à jour en place* : une table plus simple, mais le calendrier se repeint entièrement à chaque changement d'objectif, et l'historique devient incompréhensible.

**Conséquences.** Une jointure sur toute lecture de journée passée, absorbée par un index. Historique des changements de cap gratuit, et retour arrière naturel.

---

## D05 — Les entrées de journal figent leurs valeurs · ✓ validée

**Contexte.** Une entrée référence un aliment. Si l'aliment change, l'entrée doit-elle suivre ?

**Choix.** Non. Les six macros sont copiées dans `food_entry` à l'enregistrement. Le lien vers l'aliment ne sert plus qu'à la provenance et au ré-ajout.

**Écarté.** *Normalisation stricte* : base plus propre, mais un fabricant qui reformule son produit réécrit un journal vieux de six mois, et supprimer un aliment amputerait l'historique.

**Conséquences.** Environ 40 octets par entrée, soit moins de 250 Ko par an. Un journal alimentaire est un registre d'événements : ce qui est écrit est écrit.

---

## D06 — Repas nommés plutôt que liste chronologique · ⊘ remplacée par D31

**Choix.** Petit-déjeuner, déjeuner, dîner, collation, avec sous-totaux, renommables et extensibles.

**Écarté.** *Liste chronologique* : une décision de moins à la saisie, mais on perd les sous-totaux et les repas favoris réutilisables — qui sont le principal levier de rapidité de saisie. *Mode hybride* : deux affichages à concevoir, à tester et à maintenir pour un gain marginal.

**Conséquences.** Le repas est pré-sélectionné selon l'heure, donc la décision supplémentaire coûte zéro tap dans le cas courant.

> **Remplacé par [D31](#d31--un-plat-pas-un-repas-nommé---validée).** Le raisonnement tenait tant qu'on regardait les sous-totaux ; à l'usage, la case à choisir arrive avant l'enregistrement et ne sert à rien. Le regroupement par plat donne les mêmes sous-totaux sans la question.

---

## D07 — Une couleur par macro · ✓ validée

**Choix.** Six teintes néon, stables dans toute l'application, celle des sucres dérivée de celle des glucides pour matérialiser l'inclusion.

**Écarté.** *Cyan/magenta seuls* : plus identitaire, moins lisible d'un coup d'œil. *Coloration selon l'atteinte (rouge → vert)* : parlante sur le calendrier, mais moralisante sur la journée en cours et inutilisable en daltonisme.

**Conséquences.** La couleur devient porteuse d'information, donc elle ne peut plus être utilisée pour décorer. Contrainte d'accessibilité assumée : la couleur ne porte jamais seule une information ([08](08-design-system.md#daltonisme)).

---

## D08 — Objectif adaptatif, appliqué sur accord seulement · ✓ validée

**Choix.** L'application compare la tendance de poids réelle à la trajectoire visée et **propose** un ajustement borné à ±150 kcal. Elle ne l'applique jamais d'elle-même.

**Écarté.** *Objectif figé* : simple, mais dérive à mesure que le métabolisme baisse avec le poids perdu. *Ajustement automatique* : l'objectif changerait sans que l'utilisateur comprenne pourquoi, ce qui détruit la confiance dans l'outil.

**Conséquences.** Nécessite un journal de poids et une logique de tendance. Des conditions de déclenchement strictes (adhérence, persistance) évitent les suggestions absurdes ([03](03-nutrition-calculs.md#adaptation-hebdomadaire)).

---

## D09 — Deux variantes de distribution · ✓ validée

**Contexte.** Le règlement du Play Store interdit les liens de don externes hors association reconnue.

**Choix.** Deux `productFlavors`. Le lien de don n'est **compilé** que dans la variante GitHub.

**Écarté.** *Masquage à l'exécution* : le lien reste dans l'APK et reste détectable à l'examen. *Renoncer au Play Store* : coupe la majorité des utilisateurs potentiels. *Renoncer au don* : inutile, la variante GitHub n'a aucune contrainte.

**Conséquences.** Une dimension de variante, une interface `DonationLinkProvider` à deux implémentations. Politique de confidentialité et formulaire Data Safety obligatoires pour la variante Play.

---

## D10 — Licence GPL-3.0 · ~ par défaut

**Choix.** GPL-3.0 pour le code.

**Raison.** Le copyleft garantit qu'une reprise du projet reste libre — cohérent avec l'esprit d'Open Food Facts, dont les données sont sous ODbL, une licence à partage à l'identique. Une application financée par les dons et bâtie sur des données communautaires a peu à gagner à autoriser une reprise propriétaire.

**Écarté.** *MIT / Apache-2.0* : adoption plus large et contributions d'entreprise facilitées, au prix de la possibilité qu'un fork fermé et monétisé s'appuie sur ce travail.

**À trancher.** Si l'objectif est la diffusion maximale du code plutôt que la protection du projet, Apache-2.0 est le meilleur choix. **Décision réversible tant qu'aucun contributeur externe n'a poussé de code** — après, il faut l'accord de chacun. C'est donc à figer avant la première contribution.

---

## D11 — Photos supprimées immédiatement · ~ par défaut

**Choix.** L'image est écrite dans le cache, envoyée, puis supprimée dans un bloc `finally`. Elle n'entre jamais dans la galerie et ne peut pas être revue.

**Raison.** L'énoncé initial excluait les photos du stockage. Une photo de repas est une donnée intime ; ne pas la conserver supprime toute la question de sa protection.

**À trancher.** Une vignette locale (≈ 30 Ko, purgeable, jamais sauvegardée) permettrait de revoir un repas et de relancer une analyse ratée sans reprendre la photo. C'est un vrai confort. Le modèle de données l'accueillerait sans migration (une colonne `thumbnail_path` nullable). **Dis-le si tu le veux : c'est à décider avant la 0.3.**

---

## D12 — Aucun serveur, jamais · ✓ validée

**Choix.** Pas de backend. Les clés API partent du téléphone vers le fournisseur ; les sauvegardes vont sur le Drive de l'utilisateur.

**Conséquences.** Pas de compte, pas de coût d'hébergement, pas de surface d'attaque, pas de RGPD à gérer côté serveur. En contrepartie : pas de synchronisation temps réel entre appareils, et aucune possibilité de mutualiser un cache d'analyses.

Cette décision conditionne toutes les autres. Toute fonctionnalité qui exigerait un serveur est hors périmètre par construction, et pas seulement hors v1.

---

## D13 — Nom : Hexaphore · ⊘ remplacée par D105

**Contexte.** Le premier nom envisagé, *Macronaut*, était déjà pris : domaine `macronaut.app` enregistré et identifiant `com.macronaut` publié sur le Play Store. Le second candidat a donc été vérifié avant d'être adopté.

**Choix.** **Hexaphore**. *Hexa* = six, soit exactement le nombre de compteurs de l'application — le nom porte sa propre justification.

**Vérifications effectuées.** Aucun logiciel de ce nom. `github.com/hexaphore` libre. Homonymes sans rapport et sans conflit de classe : une photographie de Jean-Pierre Sudre (1964), une lampe d'artisan, et une société indienne « Hexaphor Technologies » — orthographe différente, activité différente.

**Reste à vérifier avant la 1.0.** Disponibilité de `hexaphore.app`, absence de marque déposée à l'INPI en classes 9 et 42, absence d'application homonyme sur le Play Store.

**Leçon retenue.** Un nom se vérifie sur quatre fronts simultanément — dépôt, domaine, magasin d'applications, registre des marques — **avant** d'écrire la première ligne. Un seul des quatre suffit à tout invalider.

---

## D14 — Domaine et publication reportés après la 0.5 · ✓ validée

**Contexte.** Rien n'oblige à acheter un domaine ni à ouvrir un compte Play pour développer. La question est de savoir ce que ce report coûte.

**Choix.** Construire d'abord. Le domaine et le compte développeur n'interviennent qu'une fois les bases solides.

**Ce que le report ne coûte rien.** Le compte Play (25 $) n'apporte rien tant qu'il n'y a rien à publier, et l'ouvrir tôt ne contourne pas la règle des 12 testeurs. La politique de confidentialité, le formulaire Data Safety et les métadonnées Fastlane ne servent qu'à la publication.

**Ce que le report coûte.** Un risque unique : que `hexaphore.app` soit enregistré par quelqu'un d'autre entre-temps. Conséquence limitée — l'`applicationId` n'est verrouillé qu'à la première publication, donc un renommage resterait un simple remaniement mécanique. Le coût est une demi-journée de refactorisation, pas une impasse.

**Mesure de précaution immédiate, gratuite.** Réserver l'**organisation GitHub `hexaphore`** aujourd'hui. Deux minutes, zéro euro, et c'est exactement l'étape qui a manqué pour Macronaut.

**Conséquences.** La feuille de route de [10](10-qualite-et-livraison.md#feuille-de-route) sépare désormais la 1.0 (application finie, distribuée en APK) de la publication sur le Play Store, qui devient une étape ultérieure et facultative.

---

## D15 — Chaîne de construction alignée sur l'outillage installé · ~ par défaut

**Contexte.** L'itération 0 doit choisir un couple Gradle / AGP / Kotlin. Deux paliers existent : le courant (Gradle 9.6, AGP 9.3, Kotlin 2.4) et celui que comprend l'Android Studio installé sur la machine de développement, Ladybug 2024.2.1, qui refuse de synchroniser un projet en AGP 9.

**Choix.** Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, compileSdk 35.

**Ce qui a réellement tranché.** Pas l'IDE : detekt. La seule ligne stable de detekt est la 1.23, publiée pour Gradle 8 et Kotlin 2.0 ; la 2.0 n'existe qu'en `alpha`, sous un autre identifiant de groupe (`dev.detekt`) et avec une API de règles différente. Or les trois règles personnalisées de [10](10-qualite-et-livraison.md#analyse-statique) ne sont pas négociables. Le palier courant aurait donc imposé soit d'abandonner detekt, soit de bâtir l'outillage qualité du projet sur une version alpha. Que ce palier corresponde aussi à l'IDE installé est une coïncidence commode, pas la raison.

**Écarté.** *Palier courant* : oblige à mettre à jour Android Studio et laisse detekt sans version stable. *Palier plus ancien* : aucun gain.

**Conséquences.** Le catalogue de versions rend la montée mécanique : cinq lignes dans `gradle/libs.versions.toml`. **À rejuger quand detekt 2.0 sera stable** — c'est le seul événement qui débloque le reste de la chaîne.

---

## D16 — Les règles detekt vivent dans un build inclus · ~ par défaut

**Contexte.** Les trois règles personnalisées demandent un artefact Kotlin compilé contre `detekt-api`. L'itération 0 n'autorise que trois modules.

**Choix.** Un build composite `build-logic`, déclaré par `includeBuild` et non par `include`. `settings.gradle.kts` continue de ne lister que `:app`, `:domain` et `:core:designsystem`.

**Raison.** Ces règles s'exécutent sur la JVM de Gradle, pas sur un téléphone. Les mettre dans le graphe de dépendances de l'application mélangerait deux cycles de vie qui n'ont rien à voir. Le build inclus lit le même `libs.versions.toml`, donc la règle du catalogue unique reste vraie.

**Conséquences.** Une racine Gradle de plus dans le dépôt. Le `check` racine dépend explicitement de `:detekt-rules:check` : sans ce lien, les tests des règles ne tourneraient jamais, et une règle non testée est une règle qu'on croit active.

---

## D17 — Configuration detekt par surcouche de fichiers, pas par filtres de chemin · ~ par défaut

**Contexte.** Deux des trois règles ne concernent qu'un module : les imports Android pour `:domain`, les couleurs pour tout sauf `:core:designsystem`. La façon idiomatique est un filtre `includes` / `excludes` en motif glob.

**Choix.** Trois fichiers — `detekt.yml` commun, plus `detekt-domain.yml` et `detekt-designsystem.yml` — combinés selon le module.

**Raison.** Les motifs glob de detekt s'appliquent à des chemins, et un chemin Windows ne se sépare pas comme un chemin Linux. Une règle qui ne se déclencherait qu'en CI ne protège personne pendant qu'on écrit le code. Le filtre de la troisième règle porte pour la même raison sur un **nom de fichier**, jamais sur un chemin.

**Conséquences.** La configuration se lit dans trois fichiers au lieu d'un. En contrepartie, ce qui est actif dans quel module est explicite plutôt que déduit d'un motif.

---

## D18 — Deux ambiguïtés du design system, tranchées · ⊘ en partie remplacée par D25

Le document [08](08-design-system.md) se contredit sur deux points mineurs. Les deux sont tranchées en faveur de la règle la plus structurante.

**L'ambre du `SourceBadge`.** Le document dit que la pastille de source est monochrome *parce que les six couleurs sont réservées aux macros*, puis que « Estimation IA » s'affiche **en ambre** — qui est la teinte des lipides. Retenu à ce stade : un rôle `warning` distinct, défini dans `NeonTheme`, visuellement ambré mais indépendant de la palette des macros.

> **Remplacé par [D25](#d25--lestimation-ia-se-signale-par-une-forme-pas-par-une-couleur---validée).** Une septième couleur restait une septième couleur. Le badge se distingue désormais par la forme.

**Le fond du `NeonButton` principal.** Le document interdit le texte foncé sur aplat néon, puis décrit le bouton principal comme portant « un fond dégradé plein ». Un aplat néon plein imposerait précisément un texte foncé. Retenu : un dégradé de la teinte à faible opacité sur le fond sombre. Le bouton se distingue nettement des boutons à contour, et le néon reste l'élément clair de la paire.

---

## D19 — `Clock` et `DispatcherProvider` : port dans `:domain`, implémentation dans `:app` · ~ par défaut

**Contexte.** [06](06-architecture.md) place ces deux abstractions dans `:core:common` ; [12](12-plan-de-developpement.md) les demande dans `:domain` dès la tranche 1. Aucun des deux modules d'accueil n'existe encore.

**Choix.** Les interfaces vont dans `:domain` — ce sont des ports, et un port appartient au métier. `SystemClock` et `DefaultDispatcherProvider` vont dans `:app`, faute d'ailleurs.

**Dette assumée, datée.** Ces deux implémentations déménagent dans `:core:common` le jour où ce module naît, c'est-à-dire quand il aura un second fichier à contenir. Le déplacement est mécanique : deux fichiers et une ligne dans `config/detekt/detekt.yml`, où `SystemClock.kt` est nommément autorisé à lire l'horloge.

> **Réglée en tranche 1.** `:core:common` existe, les deux implémentations y sont, et le module Hilt qui les lie a suivi. La règle detekt n'a pas eu à bouger : elle filtre sur un nom de fichier et non sur un chemin, ce qui a rendu le déménagement invisible pour elle.

**`DispatcherProvider` n'a aucun appelant** à ce stade. C'est une exception délibérée au refus de l'abstraction préventive : il figure parmi les décisions que [12](12-plan-de-developpement.md) désigne comme non rattrapables, et l'ajouter tard ne coûte pas une refonte mais une centaine d'appels à corriger un par un.

---

## D20 — Inter embarquée, et rien d'autre en ressource de police · ~ par défaut

**Choix.** La police variable Inter (`Inter[opsz,wght].ttf`, SIL Open Font License 1.1) est versionnée dans `:core:designsystem`, et les quatre graisses utilisées sont dérivées de l'axe `wght`.

**Raison.** Un seul fichier au lieu de quatre statiques, et surtout aucun appel réseau à un service de polices — cohérent avec « aucun trafic sortant non déclaré ». Les axes variables demandent l'API 26, ce qui est exactement le minimum du projet.

**Conséquences.** +860 Ko dans l'APK avant compression. La licence est versionnée dans `third-party-licenses/`, et l'écran « À propos » devra la citer au même titre que CIQUAL et Open Food Facts.

---

## D21 — Ce que l'itération 0 ne construit pas · ~ par défaut

Trois éléments décrits ailleurs dans la documentation sont volontairement absents du socle. Ils sont listés ici pour cesser d'être des oublis.

| Absent | Raison | Quand |
|---|---|---|
| Les deux `productFlavors` `github` / `play` | Elles n'existent que pour compiler ou non le lien de don ([D09](#d09--deux-variantes-de-distribution---validée)). Aucune ligne de code ne les distingue encore, et une dimension de variante double le nombre de tâches Gradle. | Avec `DonationLinkProvider` |
| Tests d'image et rapport de couverture en CI | [10](10-qualite-et-livraison.md#intégration-continue) les prévoit dans le pipeline. Il n'y a rien à couvrir ni à figer : `:domain` ne contient que des interfaces. | Tranche 1 |
| Les plugins de convention Gradle | Trois modules ne justifient pas une couche d'indirection pour dix lignes de configuration partagée. Elle est posée dans le `build.gradle.kts` racine. | Vers le sixième module |

> **Échéance atteinte.** Les deux premières lignes restent d'actualité. La troisième est réglée par [D37](#d37--plugins-de-convention-gradle---validée) : le projet compte huit modules, et le bloc partagé était recopié cinq fois.

---

## D22 — Style ktlint : `intellij_idea`, pas `ktlint_official` · ~ par défaut

**Contexte.** ktlint propose deux styles. `ktlint_official` est le sien ; `intellij_idea` est celui que produit le formatage automatique d'Android Studio.

**Choix.** `intellij_idea`, déclaré dans `.editorconfig`.

**Raison.** Deux, dont une décisive. D'abord, avec `ktlint_official`, un `Ctrl+Alt+L` dans l'IDE **crée** des violations : l'outil de formatage et l'outil de vérification ne sont pas d'accord, et c'est le développeur qui arbitre dix fois par jour. Ensuite, `ktlint_official` éclate `class Depot @Inject constructor(...)` sur quatre lignes indentées, en décalant tout le corps de la classe. Sur un projet où presque chaque classe a un constructeur injecté, c'est une taxe de lisibilité permanente pour un gain nul.

**Conséquences.** Quelques règles de mise en forme en moins (`function-signature`, `class-signature`, `multiline-expression-wrapping`). Aucune ne portait sur autre chose que le placement des retours à la ligne.

**Convention d'écriture qui va avec.** Le français **accentué** dans tout le Kotlin — KDoc, commentaires, chaînes affichées — parce que c'est de la documentation et que le compilateur lit en UTF-8. L'**ASCII** dans les fichiers d'outillage (Gradle, YAML, XML, `.properties`) et dans les messages imprimés par Gradle ou detekt : un fichier `.properties` est spécifié en Latin-1, et une console Windows n'est pas en UTF-8 par défaut. La frontière est donc « ce que lit le compilateur Kotlin » contre « ce que lit ou affiche l'outillage », et pas une préférence.

---

## D23 — Recherche dès le 2ᵉ caractère, après une pause de frappe · ✓ validée

**Contexte.** [01](01-perimetre.md#critères-dacceptation-de-la-v1) exigeait des résultats « dès le 3ᵉ caractère », [02](02-parcours-et-ecrans.md#modale--recherche) et [12](12-plan-de-developpement.md) « à partir du 2ᵉ ». Contradiction franche, dans le document qui définit les critères d'acceptation.

**Choix.** **Deux caractères**, et la requête part **120 ms après la dernière frappe**, pas à chaque touche. Une frappe qui arrive avant l'échéance annule la précédente.

**Pourquoi deux et pas trois.** La recherche est locale : le coût d'une requête inutile est une lecture SQLite, pas un aller-retour réseau. Et deux caractères suffisent pour « riz », « thé », « œuf » — des aliments courants qu'un seuil à trois rendrait introuvables tant que le mot n'est pas fini.

**Pourquoi l'attente.** Sans elle, taper « chocolat » lance huit recherches dont sept sont jetées, et les résultats clignotent pendant la frappe. L'anti-rebond n'est pas une optimisation : c'est ce qui rend la liste lisible pendant qu'on écrit.

**Conséquences.** Le budget de 150 ms de [01](01-perimetre.md) se compte **à partir de la fin de l'anti-rebond**. Les deux délais s'additionnent à l'usage (≈ 270 ms au pire), et c'est assumé : l'un est une contrainte de performance, l'autre un choix d'ergonomie.

---

## D24 — Les fibres sont déduites du solde glucidique · ✓ validée

**Contexte.** [03](03-nutrition-calculs.md) pose les facteurs d'Atwater — dont **fibres 2 kcal/g** — puis calcule les glucides en solde sans retirer les fibres. Sur l'exemple de référence, 35 g de fibres représentent **70 kcal distribuées deux fois**. Le document présentait cet écart comme « quelques kcal » d'arrondi ; ce n'en était pas un.

**Choix.** L'ordre de calcul devient : protéines, lipides, **fibres**, puis glucides en solde.

```
glucides = (kcal − 4 × protéines − 9 × lipides − 2 × fibres) / 4
```

**Écarté.** *Ne pas compter l'énergie des fibres* : cohérent avec certaines conventions, mais incohérent avec CIQUAL et Open Food Facts, qui appliquent le règlement UE 1169/2011. Les valeurs saisies et les objectifs n'auraient plus parlé la même langue. *Inclure les fibres dans les glucides* : c'est la convention américaine ; elle contredirait les deux sources de données du projet.

**Conséquences.** L'exemple de référence passe de 330 g à **312 g** de glucides. Les fibres ne sont jamais réduites pour dégager des glucides — leur plancher de 25 g est un besoin, pas une variable d'ajustement. Le test de référence de `GoalCalculatorTest` gagne un **contrôle de cohérence énergétique** : c'est lui qui aurait attrapé l'erreur.

---

## D25 — L'estimation IA se signale par une forme, pas par une couleur · ✓ validée

**Contexte.** [08](08-design-system.md) demandait un badge « Estimation IA » en ambre, tout en réservant les six teintes aux macros. **D18** avait proposé une septième couleur dédiée.

**Choix.** Aucune couleur. Tous les `SourceBadge` sont neutres. `Estimation IA` porte un **contour en pointillés** et un **glyphe en vague** de 16 dp.

**Raison.** Une septième couleur reste une septième couleur : elle occupe l'espace chromatique, elle demande une déclinaison claire et sombre, et elle finit par ressembler à l'ambre des lipides sur un écran mal calibré. Surtout, la règle de daltonisme du projet interdit qu'une couleur porte seule une information — un badge coloré aurait donc eu besoin d'un second canal de toute façon. Autant n'avoir que celui-là.

**Pourquoi le pointillé plutôt qu'une icône d'alerte.** Un triangle d'avertissement dit « attention, problème ». Une estimation n'est pas un problème, c'est une valeur moins précise. Un contour discontinu et une vague le disent sans dramatiser, et sans légende.

**Conséquences.** Le rôle `warning` et tout le mécanisme `NeonExtendedColors` disparaissent du thème : Material 3 suffit. Le design system revient à exactement six teintes plus les fonds, ce qu'annonçait [D07](#d07--une-couleur-par-macro---validée).

---

## D26 — Le `User-Agent` d'Open Food Facts est figé · ⊘ en partie remplacée par D105

**Contexte.** [04](04-sources-de-donnees.md) exigeait un `User-Agent` obligatoire tout en laissant un `<compte>` non résolu. Or Open Food Facts bloque les clients anonymes, et le symptôme ressemble à une panne réseau — le piège est signalé dans [12](12-plan-de-developpement.md) et resterait armé.

**Choix.** `Hexaphore/<version> (github.com/hexaphore/hexaphore)`, l'organisation réservée en [D14](#d14--domaine-et-publication-reportés-après-la-05---validée).

**Raison.** Une organisation survit à un changement de propriétaire, à un passage en association et à un départ de son auteur ; un compte personnel, non. La version vient du `versionName`, pour qu'un signalement d'Open Food Facts désigne un binaire précis plutôt que « l'application ».

---

## D27 — Objectif ou limite : la nature appartient à la macro · ⊘ en partie remplacée par D47

**Contexte.** Constaté sur appareil : les six jauges se ressemblent trop. [08](08-design-system.md) ne distinguait qu'un seul plafond, les sucres, et le reste se remplissait de la même façon. Une jauge de sucres qui monte ressemble alors à une réussite — le contresens exact que la distinction était censée empêcher.

**Choix.** Trois objectifs — **calories, protéines, fibres** — et trois limites — **glucides, sucres, lipides**. La nature est portée par l'énumération `Macro` dans `:domain`, pas par un paramètre de composant.

**Pourquoi dans le domaine.** C'est une règle nutritionnelle, pas un choix d'affichage. Tant qu'elle est un paramètre, elle est un oubli possible : il suffit qu'un écran instancie une barre de sucres sans le préciser pour que le contresens revienne. Portée par la macro, la question ne se pose plus.

**Pourquoi les lipides du côté des limites.** Ils ont bien un plancher physiologique de 0,6 g/kg, mais il est appliqué **au calcul de l'objectif**, une fois pour toutes. Au jour le jour, l'utilisateur n'a rien à atteindre : il a un budget à ne pas dépasser.

**Conséquences.** Trois signaux redondants distinguent les deux familles : le suffixe `max` sur la valeur, le comportement de la jauge, et la phrase annoncée par TalkBack — « sur un objectif de » contre « sur une limite de ». Le paramètre `mode` de `MacroBar` disparaît de l'API publique : il n'y avait aucune raison légitime de le forcer.

> **Le second signal est remplacé par [D47](#d47--les-six-macros-brillent---en-partie-remplacée-par-d48).** La nature reste portée par la macro, et c'est l'essentiel de cette entrée. Ce qui cède est l'**extinction** de la jauge sous le seuil : constaté sur appareil, trois macros allumées et trois éteintes se lisent comme un défaut d'affichage. Le repère de seuil et l'échelle élargie restent.

---

## D28 — Un bouton indisponible réagit quand même · ✓ validée

**Contexte.** Constaté sur appareil : appuyer sur un bouton grisé ne produit strictement rien, et on ne sait pas si l'application a reçu l'appui ou si elle est figée.

**Choix.** `NeonButton` distingue désormais trois disponibilités au lieu d'un booléen : **disponible**, **indisponible**, **désactivé**. Un bouton *indisponible* est grisé et sans lueur au repos, mais il réagit à l'appui — réduction d'échelle, lueur brève — puis appelle son action, à qui il revient d'expliquer ce qui manque.

**Raison.** Le cas existait déjà dans la spécification sans avoir de support : [02](02-parcours-et-ecrans.md#écran-dia) demande que les modes IA sans clé restent « visibles mais grisés ; un tap ouvre une explication courte ». Un booléen `enabled` ne pouvait pas exprimer ça.

**Écarté.** *Masquer le bouton* : laisse croire que la fonctionnalité n'existe pas — le document l'excluait déjà. *Garder un seul état éteint* : il faut bien pouvoir rendre un bouton réellement inerte pendant qu'une action est en cours, et l'annoncer comme tel au lecteur d'écran.

**Conséquences.** Un état de plus à choisir à chaque appel. En contrepartie, le choix est explicite : `DISABLED` engage à ce qu'il n'y ait rien à expliquer. TalkBack annonce « indisponible » sur le second état, sans quoi il se présenterait comme un bouton ordinaire.

---

## D29 — Un total incomplet se signale au lieu de se taire · ✓ validée

**Contexte.** `null` signifie *inconnu* et jamais zéro ([04](04-sources-de-donnees.md#le-piège-des-valeurs-textuelles)). La règle est claire pour une ligne isolée ; elle ne dit rien de ce qui arrive quand on en additionne dix. Or c'est là qu'elle se perd : additionner des `null` comme des zéros donne un nombre parfaitement plausible, et plus rien ensuite ne permet de savoir qu'il est faux.

**Choix.** Un cumul n'est pas un nombre mais un couple : la somme de ce qu'on sait, et un drapeau `complete` qui tombe dès qu'une seule ligne du cumul avait une valeur inconnue. `MacroTotals.of` le calcule ; il n'existe pas d'autre chemin pour totaliser des macros.

**Écarté.** *Rendre le total nullable* : une journée entière deviendrait « inconnue » parce qu'un seul produit ne déclare pas ses fibres, alors que le total partiel reste utile. *Ne rien signaler* : c'est l'erreur que [12](12-plan-de-developpement.md) désigne comme la plus difficile à repérer — elle fausse des mois de journal en silence.

**Conséquences.** L'interface doit dire qu'un total est minoré ; un chiffre affiché sans mention vaut promesse d'exactitude. Une liste vide, elle, rend des totaux **complets** à zéro : ne rien avoir noté est une information exacte, pas une lacune. Trois tests couvrent ces trois cas.

> **L'accueil ne le dit plus** ([D119](#d119--deux-styles-daffichage-une-part-par-chiffre-et-plus-de-total-minoré-à-laccueil---validée)), sur demande explicite et sur cet écran seulement. Ce que cette décision établit tient toujours : le cumul reste un couple, `MacroTotals.of` reste le seul chemin, et l'écran de validation continue de désigner le champ qui manque là où on peut encore le remplir. Ce qui change est ce que l'accueil **montre** — une valeur non renseignée s'y lit comme zéro —, pas ce que l'application **sait**.

---

## D30 — Objectif provisoire en dur, avec sa date de péremption · ~ par défaut

**Contexte.** L'accueil de la tranche 1 doit afficher six objectifs. Leur calcul réel demande un profil, un poids cible et une échéance, qui n'existent qu'en tranche 4.

**Choix.** `DailyGoal.Placeholder` — 2 000 kcal et sa répartition, dans `:domain`.

**Ce n'est pas un chiffre arbitraire.** La répartition suit les règles de [03](03-nutrition-calculs.md) pour un maintien à 2 000 kcal sur un poids de référence de 70 kg : protéines 1,6 g/kg, lipides 30 % des calories, fibres 14 g pour 1 000 kcal, glucides en solde une fois les fibres déduites. Le contrôle de cohérence retombe à 1 kcal près. L'appliquer ici sert aussi de première vérification de [D24](#d24--les-fibres-sont-déduites-du-solde-glucidique---validée).

**Date de péremption.** Sa disparition est un critère de fin de la tranche 4, déjà écrit dans [12](12-plan-de-developpement.md). Un seul point du code le référence, et le nom `Placeholder` le désigne comme tel dans chaque complétion de l'IDE.

> **Échéance tenue.** `DailyGoal.Placeholder` n'existe plus ([D55](#d55--lobjectif-est-calculé-daté-et-parfois-absent---validée)). `GetDaySummary` lit l'objectif **actif ce jour-là**, et `DaySummary.goal` est devenu nullable : une journée sans objectif ne se compare à rien plutôt que d'être jugée sur une règle qu'elle n'avait pas. Les valeurs elles-mêmes survivent dans `InMemoryGoals.maintenance`, où elles servent de décor de test — ce qui a disparu est le fait qu'un **écran** s'en serve.

---

## D31 — Un plat, pas un repas nommé · ✓ validée

**Contexte.** **D06** avait retenu quatre repas nommés — petit-déjeuner, déjeuner, dîner, collation. Vérifié sur l'accueil réel, le choix ne tient pas : la catégorie doit être fixée **avant** d'enregistrer, pour répondre à une question que personne ne se pose. Ce qui compte est ce qu'on a mangé aujourd'hui, pas à quel repas.

**Choix.** L'unité de saisie est le **plat** : plusieurs aliments, entrés en une fois. Aucune catégorie, aucun nom obligatoire. Les plats s'affichent dans l'ordre où ils ont été notés, et l'heure les situe.

**Ce que ça ne coûte pas.** Les sous-totaux, qui étaient l'argument principal de D06, restent : ils sont désormais ceux du plat. Les plats favoris réutilisables aussi — un favori est un plat enregistré, pas un repas nommé.

**Ce que ça coûte.** Le tri chronologique remplace un ordre fixe : deux plats notés dans le désordre s'affichent dans le désordre. C'est le comportement attendu d'un journal.

**Conséquences.** La table `meal` devient `dish` et perd `type`, `custom_name` et `sort_index` ; elle gagne `logged_at`. Le champ « repas de destination » disparaît de l'écran de validation, ce qui lui retire une décision. Le réglage « renommer et ajouter des repas » disparaît des préférences.

> **Un plat porte de nouveau un nom, et cette décision tient quand même** ([D118](#d118--un-plat-porte-un-titre-et-cest-le-nom-que-le-favori-propose---validée)). Ce que D31 a écarté est la **case à choisir avant d'enregistrer** ; le titre, lui, se déduit de l'heure, ne coûte aucun geste, et ne range le plat nulle part — les plats restent une liste chronologique. Ce qui revient est un **nom**, pas une catégorie.

---

## D32 — La source appartient au plat, et ne change jamais · ✓ validée

**Contexte.** [07](07-modele-de-donnees.md) portait deux sources sur chaque **ligne** : `entry_source` (par quel chemin elle est arrivée) et `nutrition_source` (d'où viennent ses chiffres). L'accueil affichait donc une pastille par aliment.

**Choix.** Une seule source, portée par le **plat**. On ne photographie pas une assiette aliment par aliment.

**Elle ne change jamais.** Un plat reste éditable à la main indéfiniment, mais son origine reste ce qu'elle était : c'est un fait historique, pas un état. Sans cela, corriger une quantité sur une proposition de l'IA la ferait passer pour une saisie manuelle, et on perdrait la seule trace de ce qui a été deviné.

**Ce qui disparaît.** `nutrition_source` par ligne. La distinction « ce chiffre vient de CIQUAL » contre « ce chiffre est une estimation » reste nécessaire — [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) prévoit qu'un plat photographié résolve certaines lignes dans les bases et estime les autres — mais elle n'a **aucun porteur avant la tranche 6**, où le résolveur existera. La réintroduire maintenant serait une colonne que rien ne remplit. Elle reviendra sous la forme minimale qui suffit : un marqueur « estimée » par ligne, pas une seconde énumération de sources.

**Conséquences.** `SourceBadge` se pose une fois par plat. Un contenu **proposé** — photo ou description — porte le contour en pointillés ; une recherche, un code-barres ou une saisie manuelle non. Le badge devient donc lisible : sur l'ancien écran, cinq pastilles voisines ne distinguaient plus rien.

---

## D33 — Un hexagone en tête d'accueil, et un seul ordre angulaire · ✓ validée

**Contexte.** Le projet s'appelle Hexaphore parce qu'il tient six compteurs. Rien dans l'interface ne le montrait : l'accueil ouvrait sur un anneau de calories, c'est-à-dire sur **un** compteur, les cinq autres relégués en barres.

**Choix.** `MacroHexagon` remplace l'anneau en tête d'accueil. Six quartiers de 60°, hexagone à sommet plat, chaque macro remplissant sa part depuis le centre, le contour marquant l'objectif. Spécification complète en [08](08-design-system.md#macrohexagon).

**Les barres restent.** L'hexagone donne la forme d'un coup d'œil ; il ne peut pas dire « 87 / 144 g », ni le `max` d'une limite, ni le `≥` d'un total minoré. Les deux ne se concurrencent pas, ils répondent à deux questions différentes : *comment va ma journée* et *combien exactement*.

**Écarté.** *Remplacer aussi les barres* : il aurait fallu réintroduire les chiffres en étiquettes autour de la figure, ce qui ne tient pas à 200 % de police. *Ajouter l'hexagone au-dessus de l'anneau* : la même information dite trois fois.

**Le dépassement rétrécit la figure.** Le contour de l'objectif n'est pas la limite du dessin ; c'est le dessin entier qui se met à l'échelle pour que le plus grand débordement tienne. L'hexagone cible qui rapetisse **est** le signal. Plafonné à 200 % — sans quoi une saisie erronée à 2 000 % réduirait la cible à un point, précisément au moment où il faut la lire pour corriger. *(Plafond ramené à 150 % par [D47](#d47--les-six-macros-brillent---en-partie-remplacée-par-d48) : à 200 % la cible tombait à la moitié de sa taille, et un dépassement de moitié se voit déjà largement.)*

**Conséquence structurante : un seul ordre angulaire.** [08](08-design-system.md#daltonisme) fixait pour les pastilles du calendrier un ordre différent de celui demandé ici. Deux ordres pour les mêmes six macros annuleraient le bénéfice recherché : la position ne renseigne que si elle est la même partout. L'ordre de l'hexagone devient donc celui de toute l'application — **calories, protéines, fibres, glucides, sucres, lipides, sens horaire depuis le haut** — et les barres de l'accueil s'y alignent.

**Ce qui reste ouvert.** La pastille du calendrier devient-elle un mini-hexagone en tranche 7 ? À 44 dp, six quartiers restent probablement lisibles ; à 28 dp en vue mensuelle, sûrement pas. À trancher sur maquette, pas sur intuition.

---

## D34 — La table `food` attend la tranche qui la remplit · ~ par défaut

**Contexte.** [12](12-plan-de-developpement.md) liste `dish`, `food_entry` **et** `food` dans le contenu de la tranche 1.

**Choix.** Seules `dish` et `food_entry` sont créées. `food` naît en tranche 3, avec l'import CIQUAL qui la peuple.

**Raison.** Rien n'écrit dans `food` avant la tranche 3, et rien ne la lit : les entrées de journal figent leurs macros et n'ont pas besoin de la fiche d'origine pour s'afficher. Une table vide dans le schéma exporté n'est pas une préparation, c'est une ligne de plus à migrer le jour où sa vraie forme se révèle différente de celle qu'on avait devinée.

**Conséquences.** `food_entry.food_id` — le lien de provenance de [07](07-modele-de-donnees.md) — arrive avec elle, en colonne nullable. C'est précisément le type de migration que la règle de conception privilégie : *une colonne nullable plutôt qu'une table nouvelle, une table nouvelle plutôt qu'un renommage*.

---

## D35 — Le test de migration tourne sur la JVM, pas sur un appareil · ✓ validée

**Contexte.** `MigrationTestHelper` de Room est une règle **JUnit 4**, et le projet teste en JUnit 5. Le chemin habituel est le test instrumenté, sur émulateur.

**Choix.** Robolectric, plus JUnit 4 et le moteur *vintage* pour `:core:database` uniquement. Le test entre ainsi dans `./gradlew check` et dans la CI, sans appareil.

**Raison.** Un test de migration qu'il faut brancher un téléphone pour exécuter est un test qu'on n'exécute pas — et le seul moment où il compte est celui où on a oublié de le lancer.

**Écarté.** *Test instrumenté* : plus fidèle, mais absent de la CI et donc absent de la revue. *Se passer de `MigrationTestHelper`* en vérifiant seulement la présence du fichier de schéma : cela n'aurait rien éprouvé du mécanisme, seulement de l'export.

**Conséquences.** JUnit 4 revient dans le projet, cantonné à ce module. Deux moteurs de test cohabitent sous la même plateforme et la même commande.

---

## D36 — L'application démarre sur un journal vide · ✓ validée

**Contexte.** L'accueil était alimenté par un jeu de démonstration tant que Room n'existait pas. Room branché, fallait-il le garder pour que l'écran reste peuplé ?

**Choix.** Non. Le jeu de démonstration disparaît avec la bascule, et `:app` ne dépend plus de `:core:testing`.

**Raison.** Afficher des plats que l'utilisateur n'a pas notés serait mentir sur l'état de son journal — dans une application dont tout le propos est de dire la vérité sur ce qu'on a mangé, y compris quand la donnée manque. La journée vide est le comportement exact tant que la tranche 2 n'a pas apporté la saisie, et l'état vide de l'accueil est écrit pour ça.

**Ce que ça coûte.** L'hexagone ne se vérifie plus avec des données depuis l'accueil. Il se vérifie depuis la **galerie**, qui en montre trois cas dont un dépassement au plafond — c'est-à-dire mieux qu'un seul jeu figé.

---

## D37 — Plugins de convention Gradle · ✓ validée

**Contexte.** [D21](#d21--ce-que-litération-0-ne-construit-pas---par-défaut) reportait la question « vers le sixième module ». Il y en a huit. Le bloc `android { compileSdk / minSdk / compileOptions }` et `kotlin { jvmTarget }` était recopié **à l'identique dans cinq** `build.gradle.kts`, et le module suivant en aurait produit un sixième exemplaire.

Ce n'est pas la répétition qui coûte, c'est ce qu'elle rend possible : une divergence entre deux copies ne se voit qu'en compilant celle qui a divergé. Un module resté en `compileSdk 34` compile parfaitement — jusqu'à ce qu'un autre utilise une API de 35.

**Choix.** Six plugins dans `build-logic/convention` : `hexavore.jvm.library`, `hexavore.android.library`, `hexavore.android.library.compose`, `hexavore.android.application`, `hexavore.android.hilt`, `hexavore.android.feature`. `compileSdk` et `jvmTarget` n'apparaissent plus qu'une fois chacun, dans `Conventions.kt`. Les versions restent lues dans `gradle/libs.versions.toml` — aucun numéro n'est écrit dans `build-logic`.

**Des classes, pas des scripts précompilés.** Un fichier `hexavore.android.library.gradle.kts` se lit mieux, et c'est ce qui a été écrit d'abord. Il ne fonctionne pas ici : un script précompilé résout les identifiants de son propre bloc `plugins { }` sur **son** chemin de classes d'exécution, ce qui obligerait à embarquer AGP dans `build-logic` en `implementation`. On se retrouve alors avec deux AGP — celui du build racine et celui du build inclus — et Gradle échoue sur `com.android.build.gradle.BaseExtension` sans dire lequel des deux il cherchait. Une classe applique par identifiant, résolu sur le chemin du module cible : un seul AGP, et `compileOnly` suffit à compiler contre son API.

**Ce que le build racine garde.** Les sept `alias(...) apply false` restent, et ils ne sont pas décoratifs : c'est eux qui posent AGP et Kotlin sur le chemin de classes du build racine — le seul que voient detekt et ktlint, appliqués par cross-configuration. Sans eux, ktlint cherche `BaseExtension` dans un chargeur de classes qui ne l'a pas.

**Deux `includeBuild` pour un seul build inclus.** Celui de `pluginManagement` résout les identifiants `hexavore.*`. Celui de la racine substitue le projet local à la coordonnée `app.hexavore.buildlogic:detekt-rules`. Contrairement à ce qu'on pourrait croire, le premier ne fait pas le second : sans la seconde ligne, Gradle va chercher les règles detekt maison sur Maven Central.

**L'analyse statique reste en cross-configuration.** Un plugin `hexavore.quality` serait plus idiomatique — c'est même exactement ce que les plugins de convention sont censés remplacer. Il s'appliquerait module par module, donc **oublier de l'appliquer désactiverait detekt sur un module entier sans qu'aucun build n'échoue**. Le `subprojects { }` rend l'oubli impossible. La cohérence perd, la garantie gagne.

**Conséquences.** `feature/home/build.gradle.kts` passe de 60 lignes à 7. Le prochain `:feature` en coûtera autant. En contrepartie, ce qu'un module reçoit ne se lit plus dans son propre fichier : il faut ouvrir la convention. C'est le prix, et il est payé une fois pour toutes les huit.

---

## D38 — La galerie vit dans `src/debug`, et detekt l'y suit · ✓ validée

**Contexte.** La galerie des composants était dans `src/main` de `:app` alors que seule `GalleryActivity`, déclarée par la variante `debug`, l'utilise. R8 la supprimait de la release parce que rien ne la référençait — mais c'est une élimination qu'il fallait espérer, pas une garantie. Du code que la release n'atteint jamais n'a rien à faire dans son jeu de sources.

**Choix.** Les quatre fichiers et leurs chaînes passent en `src/debug`. `app/src/main/res/values/strings.xml` ne contient plus que `app_name`.

**Ce que le déplacement a révélé.** La tâche `detekt` par défaut n'analyse que `src/main` et `src/test`. Vérifié sur ce projet plutôt que supposé : un `Color(0xFF123456)` écrit dans `src/debug` passait l'analyse **sans un mot**, alors que la règle `HardcodedColor` est active. Déplacer 500 lignes les aurait donc sorties de la revue automatique, en silence et sans qu'aucun build ne change de couleur.

`Detekt.setSource(files("src"))` corrige le point pour tous les modules à la fois. La même sonde échoue désormais, avec le message de la règle.

**Conséquences.** Le jeu de sources d'un module n'a plus d'incidence sur ce qui est analysé. C'est ce qu'on croyait déjà, et c'est maintenant vrai. Le coût est nul : detekt lisait déjà tous les fichiers Kotlin du module, il en ignorait simplement une partie sur un critère de répertoire.

---

## D39 — Un échec de lecture se dit · ✓ validée

**Contexte.** `HomeUiState` n'avait que `Loading` et `Content`, et son KDoc l'assumait : « la lecture du journal ne peut pas échouer tant qu'elle vient de la mémoire ». Room a changé cette phrase sans que l'état d'écran suive. Un `SQLiteException` remontait donc dans un flux qui n'avait aucun moyen de le représenter.

**Ce que ça produisait.** Une journée vide. Or une journée vide n'est pas une absence de réponse, c'est une **affirmation** : « vous n'avez rien noté aujourd'hui ». Une application qui refuse de confondre `null` avec zéro sur une valeur de fibres ne peut pas confondre « je n'ai pas pu lire » avec « il n'y a rien » sur une journée entière.

**Choix.** `HomeUiState.Error`, sans détail. Une base illisible, un disque plein et un fichier corrompu appellent le même geste — réessayer — et un message plus précis n'apporterait que des mots dont personne ne peut rien faire. Un encart inline avec un bouton *Réessayer*, pas un dialogue : rien n'est détruit, rien n'est irréversible.

**Le point technique qui rend le bouton utile.** `catch` est placé **à l'intérieur** du `flatMapLatest`. Un flux qui a rattrapé une exception est terminé ; à l'extérieur, il ferait terminer la source de `stateIn`, qui resterait figée sur `Error` quoi qu'on pousse dans le déclencheur de relecture. Le bouton aurait alors existé sans rien faire — pire qu'absent, parce qu'il aurait promis quelque chose.

**Conséquences.** `InMemoryDiaryRepository` gagne un champ `failure`. Ce n'est pas du décor de test : sans lui, la seule façon d'éprouver ce cas serait de corrompre une vraie base. Deux tests couvrent l'échec et la reprise.

---

## D40 — Ce que la tranche 2 ne construit pas · ✓ validée

Trois éléments décrits ailleurs sont volontairement absents. Listés ici pour cesser d'être des oublis, comme [D21](#d21--ce-que-litération-0-ne-construit-pas---par-défaut) l'avait fait pour le socle.

| Absent | Raison | Quand |
|---|---|---|
| Le port `CustomFoodStore` et le formulaire d'aliment personnel | [D34](#d34--la-table-food-attend-la-tranche-qui-la-remplit---par-défaut) a reporté la table `food` en tranche 3. Un aliment personnel n'a de sens que réutilisable, donc trouvable : sans recherche, il serait écrit dans une table que rien ne lit, derrière un port à une seule implémentation. C'est exactement l'abstraction préventive que le projet refuse. | Tranche 3, avec `food` et la recherche |
| Le choix de la date sur l'écran de validation | La saisie est possible aujourd'hui et dans le passé, mais **aucun écran ne mène à un jour passé** avant la tranche 7. Un sélecteur de date servirait à corriger un champ que rien ne peut encore mal remplir. La date est affichée, et vient de l'horloge. | Tranche 7, avec l'écran Journée |
| La survie d'une saisie à la mort du processus | Le `ViewModel` couvre la rotation et le passage en arrière-plan, qui sont les cas fréquents. Persister le formulaire demande une représentation sérialisable des types du domaine, dans **chaque** écran de saisie — c'est un mécanisme transverse, pas une ligne à ajouter ici. Le construire pour un seul écran, c'est le construire deux fois. | Avec le deuxième écran qui a une saisie longue |

**Ce que ça ne coûte pas.** `docs/12` annonçait `CustomFoodStore` en tranche 2 ; la capacité annoncée par la tranche — « j'ajoute un aliment à la main » — reste entière. On saisit un nom, une quantité et des valeurs, et le plat entre au journal. Ce qui manque est la **réutilisation** de cet aliment, qui est le sujet de la tranche 3.

---

## D41 — `IdGenerator`, port comme l'horloge · ✓ validée

**Contexte.** Les identifiants sont des UUIDv4 générés côté application ([07](07-modele-de-donnees.md)). Un `UUID.randomUUID()` écrit au milieu de `LogDish` aurait été le chemin court.

**Choix.** Un port `IdGenerator` dans `:domain`, `UuidGenerator` dans `:core:common`, une génération séquentielle dans `:core:testing`.

**Raison.** Exactement celle de `Clock` : une entrée non déclarée rend le résultat invérifiable. Sans le port, un test de `LogDish` ne peut affirmer que « un plat a été enregistré » ; avec lui, il affirme « ce plat-là, avec ces lignes-là, rattachées à ce plat-là ». Le lien entre un plat et ses lignes est précisément ce qu'une erreur d'écriture casserait en silence.

**Ce qui écarte le soupçon d'abstraction préventive.** Deux implémentations existent le jour où le port naît, et la seconde n'est pas un décor : elle est ce qui rend les quatre tests d'écriture possibles.

**Écarté.** *Générer les identifiants dans l'adaptateur Room*, comme une base attribuerait une clé. Défendable, mais le domaine ne pourrait alors plus construire un `Dish` complet, et `LogDish` rendrait un identifiant qu'il n'a pas choisi — la logique « ce que devient un brouillon » se serait déplacée dans la couche de persistance.

---

## D42 — Une ligne de brouillon porte des valeurs absolues · ~ par défaut

**Contexte.** [02](02-parcours-et-ecrans.md#écran-de-validation-dentrée) demande que « les macros se recalculent en direct » quand la quantité change. Ce recalcul suppose une **référence pour 100 g**, celle d'une fiche d'aliment. La saisie à la main n'en a aucune : il n'existe pas de fiche derrière une ligne tapée au clavier.

**Choix.** `DraftLine` porte les six valeurs **telles qu'elles ont été saisies**, pour la quantité indiquée. Changer la quantité ne les recalcule pas.

**Raison.** La seule référence disponible serait celle que l'utilisateur vient de taper, et s'en servir pour réécrire ses propres chiffres reviendrait à inventer une règle qu'il n'a pas demandée : « 200 g, 300 kcal » deviendrait « 400 g, 600 kcal » alors qu'il corrigeait peut-être une erreur de pesée. Le recalcul arrive avec ce qui le justifie — une fiche d'aliment et ses valeurs pour 100 g, en tranche 3.

**Deux unités seulement, g et ml.** Les portions nommées — « 1 tranche », « 1 verre » — appartiennent à une fiche. Un millilitre vaut un gramme, ce qui est la densité par défaut de [04](04-sources-de-donnees.md) et la seule dont on dispose ; la conversion est isolée dans `QuantityUnit` pour qu'il n'y ait qu'un endroit à corriger.

**Un champ vide vaut inconnu, jamais zéro.** C'est la règle du projet, appliquée là où elle est le plus facile à trahir : il aurait suffi de lire un champ vide comme `0` pour éviter tout traitement du cas nul, et le journal aurait porté des zéros que personne n'a saisis. Seule l'énergie est obligatoire — une fiche sans énergie n'est pas exploitable. Trois tests couvrent le point.

---

## D43 — Les cas d'usage d'écriture parlent de plats · ✓ validée

**Contexte.** [06](06-architecture.md#cas-dusage) et [12](12-plan-de-developpement.md) nomment `LogFoodEntry`, `UpdateFoodEntry`, `DeleteFoodEntry`. Ces noms datent d'avant [D31](#d31--un-plat-pas-un-repas-nommé---validée), qui a fait du **plat** l'unité de saisie.

**Choix.** `LogDish`, `UpdateDish`, `DeleteEntry`, `RestoreDish`, `GetDishDraft`, `CreateDraft`. On enregistre un plat, on modifie un plat ; on supprime en revanche une **ligne**, parce que c'est bien une ligne qu'on balaie dans le journal.

**Pourquoi ça compte plus qu'un nom.** `LogFoodEntry` laisserait croire qu'une ligne entre seule dans le journal. Elle ne le peut pas : elle appartient à un plat, qui porte la source et l'heure. Le nom qui ment ici est celui par lequel on finirait par écrire une ligne orpheline.

**Une conséquence, tirée du même raisonnement.** `DeleteEntry` supprime le plat quand il perd sa dernière ligne. Un plat vide s'afficherait avec son heure et sa pastille de source, à zéro calorie, indiscernable d'une saisie réelle qui n'aurait rien apporté.

---

## D44 — La lueur de l'hexagone est un contour, pas une silhouette · ✓ validée

**Contexte.** Constaté sur appareil : le néon de l'hexagone ne se voyait que sur l'arête extérieure des quartiers, avec un bord franc, et se faisait rogner quand un quartier remplissait la zone.

**La cause.** La lueur était un **second triangle un peu plus grand**, en teinte `glow`, posé derrière le quartier. Trois défauts en découlaient, et ils étaient tous inévitables avec cette forme :

- elle ne dépassait visiblement que d'un côté — les deux arêtes latérales sont mitoyennes, et le remplissage du quartier voisin recouvrait ce qui dépassait de son côté ;
- un triangle plein s'arrête où il s'arrête : le bord était net, et **une lueur qui s'arrête net n'est pas une lueur** ;
- son rayon valait 106 % de celui du quartier, or le quartier le plus rempli atteint déjà le bord de la zone. Les 6 % de trop sortaient du dessin et se faisaient couper au ras.

**Choix.** La lueur est un **tracé en contour sur les trois arêtes**, en trois passes de plus en plus larges et de moins en moins opaques — la technique déjà employée par `NeonButton`. Le tracé étant centré sur le chemin, chaque couche déborde de part et d'autre, et les six lueurs se rejoignent au centre où les six pointes se touchent.

Deux conséquences de forme, sans lesquelles le remède serait incomplet :

- **Deux passes de dessin.** Les six quartiers d'abord, les six lueurs ensuite. Dessinée quartier par quartier, chaque lueur latérale se ferait recouvrir par le remplissage du suivant — c'est-à-dire exactement le défaut d'origine, sous une autre forme.
- **Une réserve dans le rayon.** La zone déduit désormais la lueur, un intervalle et la lettre. Rien ne peut plus être rogné par le bord.

**Écarté.** *Un vrai flou* (`BlurMaskFilter`) : ce serait le rendu juste, mais il n'est pas accéléré matériellement et imposerait un rendu logiciel à chaque image d'une figure animée à 400 ms. Trois couches suffisent à ce que l'œil ne distingue plus les paliers.

**Conséquences.** Environ cinquante tracés par image contre six, tous sur des chemins triangulaires simples. Le dégradé des totaux minorés s'applique désormais à la lueur comme au remplissage, sans quoi une arête volontairement floue se serait retrouvée soulignée d'un trait de néon parfaitement net.

---

## D45 — Un champ de saisie tient son texte lui-même · ✓ validée

**Contexte.** Constaté sur appareil : taper vite dans un champ de l'écran de validation mélangeait les lettres. « Bolognaise » donnait « Boognaseil » — le curseur reculait de deux caractères en cours de frappe.

**La cause.** La forme habituelle, `value = état.texte` et `onValueChange = { viewModel.change(it) }`, suppose que l'état revienne avant la frappe suivante. Il ne revenait pas : entre la frappe et le nouvel état, il y avait un `MutableStateFlow`, un `combine`, un `flowOn(default)` et une recomposition. Une frappe arrivée avant la fin du trajet trouvait un champ réaffiché avec un texte d'il y a deux caractères, et la position du curseur repartait avec lui.

**Choix.** Le texte affiché vit dans le champ, en `TextFieldValue` local. Chaque frappe s'y applique immédiatement ; le `ViewModel` est prévenu ensuite et ne renvoie rien. **Il n'y a plus qu'un seul écrivain, donc plus de course.**

Le `flowOn(dispatchers.default)` disparaît aussi de cet écran. Ce qu'il produisait à chaque frappe tient en une conversion de quelques lignes et deux additions : le passer sur un autre dispatcher n'économisait rien et coûtait une image de latence.

**Ce que ça ne casse pas.** La valeur initiale n'est lue qu'à la première composition, et c'est suffisant : une ligne est identifiée par son `DraftLineId` dans la liste, donc rouvrir un plat ou replier les valeurs reconstruit le champ avec le bon texte. Rien d'autre ne réécrit ce que l'utilisateur tape.

**Une règle qui suit.** Le champ **refuse** une frappe non numérique au lieu de l'accepter puis de la nettoyer. Nettoyer obligerait à réécrire le texte affiché, donc à repositionner le curseur — le défaut qu'on vient de corriger. Le filtrage disparaît en conséquence de `LineEdit` : deux règles pour la même chose divergent le jour où l'une accepte ce que l'autre supprime.

**Portée.** Cette décision vaut pour tout champ de saisie du projet, pas seulement pour celui-ci. Elle contredit en apparence la forme de [06](06-architecture.md#présentation) — « un `StateFlow<UiState>` unique » — mais seulement en apparence : l'état d'écran reste unique et reste un flux ; c'est le **texte en cours de frappe** qui n'y transite plus, parce qu'il n'a pas le temps.

---

## D46 — Une action destructrice ne s'atteint jamais par le seul balayage · ✓ validée

**Contexte.** L'écran de validation ne proposait le retrait d'une ligne que par balayage.

**Choix.** Une corbeille visible à droite du nom, **en plus** du balayage.

**Raison.** Un geste sans représentation visible est introuvable pour qui ne le connaît pas, hors d'atteinte au lecteur d'écran, et difficile pour une main qui tient mal le téléphone. Le balayage reste ce qu'il doit être : le raccourci de celui qui le connaît.

**Ce qui reste ouvert.** L'accueil n'a, lui, que le balayage sur ses lignes de journal, et la même critique s'y applique. La différence est qu'il y existe déjà un appui long prévu par [02](02-parcours-et-ecrans.md#liste-des-plats) — dupliquer, déplacer, mettre en favori — et que la suppression y a sa place. À traiter avec ce menu, pas avant : deux chemins ajoutés séparément en feraient trois.

---

## D47 — Les six macros brillent · ⊘ en partie remplacée par D48

**Contexte.** Constaté sur appareil : trois macros allumées et trois éteintes ne se lisent pas comme une information mais comme un défaut d'affichage. L'utilisateur ne voit pas une règle nutritionnelle, il voit un rendu qui marche à moitié.

**Ce que disait la règle précédente.** [D27](#d27--objectif-ou-limite--la-nature-appartient-à-la-macro---en-partie-remplacée-par-d47) : une limite reste sourde sous son seuil et ne s'allume qu'au dépassement. « Ne pas allumer une limite, c'est déjà réussir. » L'idée est juste ; ce qui ne tient pas est de la porter par l'**absence** d'un effet. Une absence ne se distingue pas d'une panne.

**Choix.** Les six macros prennent leur teinte vive et leur lueur, à tout niveau, dans l'hexagone comme dans les barres. La distinction objectif / limite reste portée par `Macro` dans le domaine — c'est le cœur de D27 et il ne bouge pas — mais elle s'exprime désormais par ce qui est **écrit**, pas par ce qui est éteint : le suffixe `max` sur la valeur, le repère de seuil, l'échelle élargie à 125 %, et la phrase annoncée par TalkBack.

**Ce qui est perdu, et qui est réel.** Une journée bien tenue ne se reconnaît plus d'un coup d'œil à ses trois quartiers sourds. Il faut lire les valeurs. C'est le prix d'une figure qui ne ressemble plus à un rendu incomplet, et il est assumé.

**Trois corrections qui accompagnent.**

- **Plafond ramené de 200 % à 150 %.** À 200 %, la cible tombait à la moitié de sa taille et les six lettres se retrouvaient loin d'une figure devenue petite. Un dépassement de moitié se voit largement.
- **Le dégradé d'un total minoré devient linéaire**, le long de l'axe du quartier. Radial, il suivait un cercle : les deux sommets — à `R` du centre — disparaissaient entièrement pendant que le milieu de l'arête — à `√3/2 · R` — restait presque opaque. Le quartier paraissait rongé par les coins au lieu d'être estompé sur son bord.
- **La lueur cesse de grandir sous 12 % du rayon.** Sa largeur est fixe, celle du quartier ne l'est pas : sur un quartier presque vide, elle était plus grande que lui et tachait le centre.

**Reste ouvert.** Sur les barres, le repère de seuil et l'échelle à 125 % subsistent — ce ne sont pas des effets de néon et ils portent la seule distinction visuelle restante. Les retirer aussi rendrait une limite strictement indiscernable d'une cible hors du texte.

> **Ce dernier point est remplacé par [D48](#d48--la-barre-pleine-vaut-lobjectif---validée).** L'échelle permanente à 125 % cède, et avec elle la dernière distinction visuelle entre une cible et une limite. Tout le reste de cette entrée — les six macros brillent, le plafond à 150 %, le dégradé linéaire, la lueur qui cesse de grandir — tient toujours.

---

## D48 — La barre pleine vaut l'objectif · ✓ validée

**Contexte.** [D47](#d47--les-six-macros-brillent---en-partie-remplacée-par-d48) avait laissé aux barres deux signaux visuels : une échelle élargie à 125 % sur les limites, et un repère planté au seuil. Ces deux-là étaient permanents — donc présents à zéro, alors qu'ils ne parlent que du dépassement.

**Ce que ça produisait.** Une barre dont le remplissage ne se lisait pas seul. À 100 %, la jauge de sucres était aux quatre cinquièmes, et il fallait avoir compris le repère pour savoir que c'était le plafond et non 80 % de celui-ci. Le signal censé lever un doute en créait un.

**Choix.** La barre pleine vaut l'objectif, pour les six macros. Au-delà, **l'échelle suit la valeur** : le remplissage recule à mesure que la quantité monte, et un repère apparaît là où l'objectif se situe désormais. En dessous, il n'y a rien à interpréter.

**C'est le mécanisme de l'hexagone**, et il emprunte son plafond de 150 % pour la même raison — une saisie erronée à 2 000 % tasserait tout contre l'origine, précisément au moment où il faut lire la barre pour corriger ([D33](#d33--un-hexagone-en-tête-daccueil-et-un-seul-ordre-angulaire---validée)). Les deux composants disent désormais le dépassement de la même façon, ce qui est une raison de plus : ils sont l'un au-dessus de l'autre sur l'accueil.

**Ce qui est perdu, et qui est réel.** Plus rien de visuel ne distingue une limite d'une cible. C'est exactement ce que D47 refusait de céder. Restent le suffixe `max` sur la valeur et la phrase de TalkBack — deux canaux textuels, dont un seul est visible à l'œil. Un dépassement de sucres se voit désormais parce que la barre a rétréci, pas parce que c'était une limite.

**La lueur perd sa dernière condition.** Elle était atténuée proportionnellement au remplissage ; elle est désormais pleine à tout niveau. C'était le même défaut que celui traité par D47, sous une forme continue plutôt que binaire : une barre peu remplie paraissait mal rendue plutôt que basse. La transparence en thème clair reste, mais c'est une propriété de la palette et non une condition de la barre.

**Deux corrections d'atteinte, prises au même moment.**

- **Le plat entier est la cible tactile de l'accueil.** Seules les lignes d'aliment l'étaient ; l'heure, la pastille, le total et les apports — la moitié de la surface — ne répondaient pas, sans que rien ne dise pourquoi. Le plat est l'unité de saisie ([D31](#d31--un-plat-pas-un-repas-nommé---validée)), donc l'unité de correction. Conséquence assumée : une cible tactile fusionne les nœuds d'accessibilité qu'elle contient, et un plat devient un seul arrêt de TalkBack au lieu de *n* + 4. La phrase de chaque ligne est ce qui rend cette annonce lisible, et c'est pour ça qu'elle reste.
- **Enregistrer et Annuler flottent au-dessus de la liste.** En pied de défilement, ils s'éloignaient à mesure que le plat grossissait : à cinq lignes dépliées, enregistrer demandait de faire défiler un écran entier. La réserve laissée sous la liste est **mesurée** et non déclarée — une hauteur écrite en dur ferait passer le dernier champ sous les boutons à 200 % de police. L'explication de ce qui manque quitte l'affichage permanent pour redevenir la **réponse** du bouton indisponible à un appui, ce que [D28](#d28--un-bouton-indisponible-réagit-quand-même---validée) demandait déjà : épinglée, elle occuperait quatre lignes à chaque saisie neuve pour dire ce que les champs vides disent déjà.

---

## D49 — La recherche normalise à l'import, pas au tokenizer · ✓ validée

**Contexte.** [04](04-sources-de-donnees.md) et [07](07-modele-de-donnees.md) demandaient une table FTS5 avec `unicode61 remove_diacritics 2`, et c'est ce réglage qui devait faire que « creme brulee » trouve « crème brûlée ». Vérifié avant d'écrire la première ligne : il ne tient pas sous `minSdk 26`, et pour deux raisons indépendantes.

**Ce qui ne tient pas.** FTS5 n'est compilé dans le SQLite embarqué d'**aucune** version d'Android — c'est précisément pourquoi Room n'expose que `@Fts3` et `@Fts4`, et pourquoi il existe des bibliothèques dont le seul objet est d'embarquer un SQLite qui l'a. Et `remove_diacritics 2` demande SQLite 3.27, donc l'API 29 : les API 26 à 28 échoueraient même si FTS5 était là. Un défaut de ce genre ne se voit pas ici : il se voit chez l'utilisateur, sous la forme d'une recherche qui ne rend jamais rien.

**Choix.** La colonne indexée est un nom **déjà normalisé au build** — décomposition Unicode, marques diacritiques retirées, ligatures défaites, minuscules, ponctuation devenue coupure de mot. L'index est une table FTS4 sans contenu, tokenizer `simple`. La même fonction est appliquée à la saisie, et c'est la seule règle qui compte : un nom indexé sans elle, ou une saisie comparée sans elle, ne se rencontrent jamais.

**Écarté.** *Embarquer SQLite* (requery, `androidx.sqlite` bundled) : garderait la lettre de la spécification, au prix de 4 à 5 Mo d'APK, d'une dépendance native, et d'une fabrique d'ouverture à rebrancher — ce qui toucherait aussi `hexavore.db`. *Remonter `minSdk` à 29* : ne réglerait que la moitié du problème, celle qui n'était pas la plus grave.

**Ce que ça gagne, en plus de fonctionner.** La normalisation est faite une fois, au build, par la JVM, dont la couverture Unicode dépasse largement le latin-1 auquel `remove_diacritics 2` se limite. Elle se teste en JVM pure. Et `œ` — l'un des trois exemples de [D23](#d23--recherche-dès-le-2ᵉ-caractère-après-une-pause-de-frappe---validée) — est traité, ce qu'aucun réglage de tokenizer n'aurait fait : `NFD` sépare une lettre de son accent, mais `œ` n'est pas un `o` accenté.

**Ce que ça coûte.** `bm25()` est une fonction de FTS5 : le classement est calculé côté Kotlin. Le coût est faible parce que [04](04-sources-de-donnees.md) exigeait déjà un second critère par-dessus BM25 — remontée des aliments courts et déjà consommés — et que c'est lui qui départage vraiment 3 484 libellés courts. `tokenize=simple` plutôt qu'`unicode61` pour la même raison que le reste : `name_search` est de l'ASCII minuscule séparé par des espaces, les deux tokenizers y font le même découpage, et `simple` est le seul dont la présence ne se discute pas.

**Trois autres points tranchés dans la même passe.**

- **Une écriture de teneur inconnue arrête l'import.** Le parseur a trois issues et non deux : la valeur, l'inconnu déclaré, et ce qu'il ne sait pas lire. Ranger la troisième avec l'inconnu effacerait une colonne entière en silence le jour où l'ANSES change de convention ; la ranger avec zéro en inventerait une. Les deux replis sont aussi graves, et aucun ne se voit avant des mois de journal faussé.
- **Le seuil de `<` est quelconque.** [04](04-sources-de-donnees.md) ne citait que `< 0,5`. Dépouillement du fichier réel : 250 seuils distincts, de `< 0,0001` à `< 700`, pour 16 000 valeurs. La règle est `< n → n / 2`, et l'exemple n'était qu'un exemple.
- **Le code de constituant fait foi, l'intitulé le vérifie.** Désigner une colonne par son libellé accentué ferait dépendre l'import d'une chaîne qui bouge ; ne se fier qu'au code laisserait une renumérotation remplir les lipides avec autre chose. Les deux sont déclarés, et l'import échoue si l'un dément l'autre. C'est la seule vérification qui protège d'une erreur qu'aucun test ne verrait : la base se génère, l'application se lance, et les chiffres sont faux.

**Un constat qui a changé une intention.** 143 aliments sur 3 484 n'ont pas d'énergie déterminée. L'intention était de les écarter — [D42](#d42--une-ligne-de-brouillon-porte-des-valeurs-absolues---par-défaut) dit qu'une fiche sans énergie n'est pas exploitable. Regardés de près, ce sont la feta, les câpres, la canneberge, le pruneau cuit, l'estragon frais. Les écarter aurait retiré des aliments courants du catalogue pour appliquer une règle écrite à propos d'une ligne tapée à la main. Ils restent, avec leur trou visible : c'est exactement le comportement que le projet demande partout ailleurs.

---

## D50 — Ce que la tranche 3 ne construit pas · ✓ validée

Listés ici pour cesser d'être des oublis, comme [D21](#d21--ce-que-litération-0-ne-construit-pas---par-défaut) et [D40](#d40--ce-que-la-tranche-2-ne-construit-pas---validée) l'ont fait avant.

| Absent | Raison | Quand |
|---|---|---|
| ~~Les **plats** favoris (`favorite_dish`, `favorite_component`)~~ | [02](02-parcours-et-ecrans.md#modale--recherche) dit « favoris : aliments **et** plats ». Les aliments favoris existent ; les plats demandent deux tables, une action « enregistrer comme favori » sur l'écran de validation, et un rejeu qui reconstruit un brouillon à partir de fiches vivantes. C'est une capacité, pas une case à cocher. | **Fait** en [D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée) |
| `food_serving`, les portions nommées d'un **aliment personnel** | Les portions de CIQUAL se lisent dans `ciqual_serving` par le code source, sans copie. Une fiche personnelle a `default_serving_g`, qui couvre le cas courant. Une table que rien ne remplirait serait exactement ce que [D34](#d34--la-table-food-attend-la-tranche-qui-la-remplit---par-défaut) refusait. | Quand un aliment personnel aura besoin de plusieurs portions |
| Les colonnes `density`, `is_liquid`, `user_edited_fields`, `fetched_at` de `food` | Même raison, appliquée colonne par colonne. La densité arrive avec le résolveur (tranche 6), les trois autres avec le cache Open Food Facts (tranche 5), qui est ce qui les remplit. La règle du projet préfère une colonne nullable ajoutée plus tard à une colonne vide ajoutée trop tôt. | Tranches 5 et 6 |
| La suggestion « Chercher dans Open Food Facts » en dernière ligne de résultats | Elle suppose un client réseau, qui est le contenu de la tranche 5. Une ligne qui n'ouvre rien n'est pas une avance. | Tranche 5 |
| Un brouillon **multi-lignes** transmis d'un écran à l'autre | La recherche produit une ligne, et une route la porte par son identifiant. La photo en produira cinq, et une route ne les portera pas : il faudra un brouillon en attente, partagé. Ajouter un argument de route par mode serait la première marche vers l'écran à quatre branches que le projet refuse. | Tranche 6 |

**Ce que ça ne coûte pas.** La capacité annoncée par la tranche — « je cherche un aliment » — est entière : on cherche, on trouve hors-ligne, on reprend un récent, on épingle un favori, on crée ce qui manque, et la quantité recalcule les valeurs.

**Une décision prise en route, qui mérite d'être écrite.** Un aliment de la table de l'ANSES entre au catalogue au moment où le plat qui le cite est enregistré, et pas au moment où on le choisit dans la liste. C'est `LogDish` qui appelle `FoodUsage.remember`, **avant** l'écriture du plat — une entrée qui désigne une fiche absente n'existe pas, la base la refuse, et l'ordre inverse aurait paru plus prudent sans jamais fonctionner. Marquer au tap aurait été plus simple et ferait remonter dans « Récents » un aliment qu'on a regardé puis abandonné : cette liste dit ce qu'on mange.

---

## D51 — Une seule porte, et la quantité qui recalcule · ✓ validée

**Contexte.** Constaté sur appareil : choisir un aliment dans la recherche ouvrait un écran de saisie **vide**. Et changer la quantité d'une ligne ne changeait rien à ses valeurs.

**La cause du premier défaut, qui était une faute de conception.** Un résultat de la table de l'ANSES recevait un identifiant **provisoire**, régénéré à chaque recherche : il n'entrait dans `food` qu'à l'enregistrement du plat. La route ne transportait que cet identifiant, et l'écran de validation le cherchait dans une table où il n'existait pas. Il ne trouvait rien et retombait sur un brouillon vierge. Cela ne marchait que pour un récent, un favori ou un aliment personnel — les fiches déjà écrites — c'est-à-dire pour les cas qu'un test en mémoire couvre et qu'un appareil ne montre qu'une fois le catalogue rempli.

**Choix.** La fiche est **versée au catalogue au moment du choix**, et c'est son identifiant définitif qui voyage. Le port `CustomFoodStore` devient `FoodStore` et gagne `place`, qui écrit si la fiche est absente et rend celle qui est là — jamais l'inverse, sans quoi choisir un aliment remettrait ses compteurs d'usage à zéro. Le rapprochement se fait par `(source, source_ref)` et non par l'identifiant, précisément parce que celui-ci était le provisoire.

**Écarté.** *Un identifiant déterministe* du genre `ciqual:13039` : stable, mais il abandonne la convention UUIDv4 de [07](07-modele-de-donnees.md) et n'est pas réversible, donc la recherche par identifiant aurait quand même eu besoin d'un repli. *Faire transiter la fiche entière par la route* : une route est sérialisée dans l'état de navigation, et y mettre un objet du domaine y ferait entrer Android.

**Ce que ça coûte.** Une fiche regardée puis abandonnée reste au catalogue, avec `last_used_at` nul — donc absente de « Récents », et indiscernable de la ligne de l'ANSES qu'elle est. Le coût est une ligne par aliment réellement ouvert, et il achète un identifiant qui désigne toujours quelque chose.

**Le second défaut, et sa règle.** Les valeurs d'une ligne étaient calculées une fois, à sa naissance. Une ligne porte désormais sa **référence pour 100 g**, et changer la quantité ou l'unité recalcule. La référence est capturée à la naissance de la ligne et **reconstruite depuis les valeurs figées** quand on rouvre un plat : la règle de trois ne relit jamais la fiche, qui a pu être corrigée ou supprimée depuis ([D05](#d05--les-entrées-de-journal-figent-leurs-valeurs---validée)). Les valeurs affichées à l'ouverture restent exactement celles enregistrées, puisque la quantité n'a pas bougé.

**Une valeur corrigée à la main ne bouge plus**, ce que [02](02-parcours-et-ecrans.md#écran-de-validation-dentrée) demandait depuis la conception et que rien ne tenait. Vider un champ compte comme une correction : c'est une affirmation — « je ne sais pas » — et la quantité n'a pas à la contredire au gramme suivant. Le marqueur est posé par macro et par ligne.

**Le point technique qui rend le recalcul visible.** Un champ de saisie tient son propre texte et ne relit sa valeur initiale qu'à la première composition ([D45](#d45--un-champ-de-saisie-tient-son-texte-lui-même---validée)). Sans un signal supplémentaire, le recalcul aurait mis à jour le brouillon **sans que l'écran bouge**. Chaque ligne porte donc un compteur de révisions, incrémenté par le recalcul et par lui seul : il sert de clé de composition aux six champs. Une frappe ne l'incrémente pas, donc le curseur ne bouge pas.

**Une seule porte pour ajouter.** L'accueil n'a plus qu'un bouton, et il ouvre la recherche. La saisie manuelle y est une action permanente plutôt qu'une porte à côté, et **elle crée une fiche** : un aliment tapé à la main se cherche, se reprend et se recalcule comme les autres. « Ajouter un aliment » depuis l'écran de saisie rouvre la même recherche, dont le choix revient au brouillon en cours par le canal que la navigation prévoit pour un résultat.

**Ce que la saisie manuelle coûte sous cette forme.** Elle se saisit **pour 100 g** et non pour la quantité mangée : c'est le prix de la fiche, et c'est ce qui rend la ligne recalculable. Pour « reste d'hier, 300 kcal », il faut donc penser en pour-cent-grammes une fois — et une seule, puisque la fiche revient ensuite.

**Ce que ça rend nécessaire, et qui est fait.** Le catalogue accumule ce qu'on saisit. Une fiche créée par l'utilisateur se **voit** dans les listes et porte une **corbeille** ; une ligne de la table de l'ANSES n'en a pas, c'est une référence publiée. La suppression demande confirmation, et la phrase change selon l'usage : une fiche citée par douze entrées annonce que celles-ci sont conservées telles quelles, avec leurs valeurs figées.

---

## D52 — Deux `SavedStateHandle`, une seule saisie manuelle, des grammes entiers · ✓ validée

**Contexte.** Constaté sur appareil : « Ajouter un aliment » depuis une saisie en cours ne faisait **rien**. On revenait à l'écran avec la seule ligne de départ, et il devenait impossible de composer un plat de plus d'un aliment.

**La cause, et c'est un piège d'API.** Le `SavedStateHandle` qu'un `ViewModel` reçoit et celui que porte un `NavBackStackEntry` sont **deux objets différents**. Tous deux sont construits par `createSavedStateHandle` depuis le même registre, mais sous deux clés distinctes : ils ne partagent aucun état. La recherche écrivait dans celui de l'entrée de pile, le `ViewModel` observait le sien, et personne ne remplissait jamais la clé qu'il écoutait.

**Ce qui rendait l'erreur crédible.** Le **premier** aliment arrivait, lui. Les arguments de route passent par les deux handles — ils sont dans le `Bundle` d'arguments par défaut — donc le chemin « accueil → recherche → saisie » fonctionnait, et seul l'ajout d'une deuxième ligne échouait. Un défaut qui marche à moitié est plus difficile à voir qu'un défaut qui ne marche pas.

**Choix.** Le résultat se lit sur le `NavBackStackEntry`, dans la composable de la destination, et l'écran le passe au `ViewModel`. C'est le chemin que la navigation Compose prévoit ; le lire dans le `ViewModel` était une commodité qui n'existe pas.

**Ce que le test manquait.** Il écrivait dans le `SavedStateHandle` qu'il construisait lui-même, celui du `ViewModel` — donc il éprouvait un chemin qui n'existe nulle part. Il passait pendant que l'écran ne faisait rien. Les tests appellent désormais la méthode que la composable appelle, et trois cas de plus couvrent ce que la ligne ajoutée doit porter.

**La recherche n'est plus une source.** `EntrySource.SEARCH` disparaît. Elle se confondait avec `MANUAL` : un même plat mêle couramment un aliment trouvé dans la table et un autre saisi à la main, et un plat porte **une** source ([D32](#d32--la-source-appartient-au-plat-et-ne-change-jamais---validée)) — distinguer les deux revenait à choisir laquelle mentir. Le typage reste, parce que ce qu'il devra dire un jour est autre chose : ce qui a été **proposé** par un modèle mérite un regard que ne mérite pas ce qu'on a composé soi-même, et `proposed` le porte déjà. Une base antérieure porte encore `SEARCH` ; elle se relit en `MANUAL`, ce que la lecture prudente du mapper faisait déjà.

**Les six valeurs sont des grammes entiers.** Personne ne compte les demi-grammes de lipides, et une décimale affichée est une précision promise que la source ne tient pas : CIQUAL donne 0,25 g de protéines pour une pomme parce que la mesure est sous le seuil de quantification, pas parce qu'elle vaut un quart de gramme. Le séparateur décimal quitte donc le clavier **et** le filtre de ces champs — l'accepter pour arrondir ensuite obligerait à réécrire le texte affiché, donc à repositionner le curseur ([D45](#d45--un-champ-de-saisie-tient-son-texte-lui-même---validée)).

**L'arrondi a lieu à l'aller, pas seulement à l'écran.** Ce qui est affiché est ce qui sera enregistré. Arrondir à la seule présentation ferait diverger le chiffre lu de celui écrit dans le journal — la définition d'un écran qui ment. La référence pour 100 g, elle, garde sa précision : c'est elle qui recalcule, et l'arrondir la ferait dériver à chaque changement de quantité. La quantité garde aussi ses décimales : 12,5 g d'huile est une pesée, pas une approximation.

**Le rognage du plat disparaît de l'accueil.** [D48](#d48--la-barre-pleine-vaut-lobjectif---validée) avait ajouté des coins arrondis pour borner l'ondulation du tap. Ils coupaient la pastille de source et le total de calories, qui sont aux deux extrémités de la première ligne. L'ondulation déborde donc en rectangle, et c'est le prix à payer pour que rien ne soit tronqué.

---

## D53 — La recherche est un flux, et le faux est tenu par un contrat · ✓ validée

**Contexte.** Constaté sur appareil : dans la recherche, épingler un aliment ou supprimer une fiche personnelle **ne se voyait pas**. Il fallait relancer la recherche. Les raccourcis — récents et favoris — se rafraîchissaient bien, eux.

**La cause, et l'asymétrie qui la rendait visible.** `SearchUiState.Results` venait d'un appel unique à `FoodSearch.search`, une `suspend fun`. Les raccourcis venaient de `Flow`. Écrire dans le catalogue n'invalidait donc rien du côté des résultats : une lecture unique rend un instantané, et **un instantané ne peut pas se démentir**.

**Choix.** `FoodSearch.search` rend un `Flow<List<Food>>`. Room invalide sur écriture, exactement comme pour `observeRecent` — le mécanisme existait déjà à trois lignes de là.

**Écarté.** *Un déclencheur de relecture* poussé après chaque écriture : c'est un `Flow` reconstruit à la main, avec une invalidation à ne pas oublier à chaque nouvelle écriture. Room la connaît déjà et ne l'oublie jamais. *Recomposer la liste côté écran* à partir des favoris observés : ça n'aurait couvert que l'étoile, pas la suppression ni le versement au catalogue, et ça aurait mis la fusion des provenances dans le `ViewModel`.

**Le flux vient du catalogue local, et lui seul.** La table de l'ANSES est livrée en lecture seule et ne change jamais ; elle est relue à chaque invalidation, ce qui coûte deux requêtes sur des libellés courts. C'est ce qui garde le dédoublonnage juste au moment précis où une fiche vient d'être copiée — sans quoi elle apparaîtrait deux fois pendant une image.

**Un second défaut, de la même famille, que la correction a mis à nu.** Épingler un aliment de la table de l'ANSES **non encore copié** n'allumait aucune étoile, et pour une raison indépendante du flux : `setFavorite` recevait l'identifiant **provisoire** ([D51](#d51--une-seule-porte-et-la-quantité-qui-recalcule---validée)) et ne mettait à jour aucune ligne. L'écran verse donc la fiche au catalogue avant d'épingler, comme il le fait déjà pour la choisir. L'état épinglé est lu sur la fiche **rendue** par le catalogue, jamais sur celle qu'on affiche.

### Le faux était plus indulgent que le vrai, et c'est ça qu'il fallait corriger

Trois défauts de suite avaient la même forme, et **à chaque fois le test passait**. La cause n'est pas la paresse du test : c'est que `InMemoryFoodCatalog` ne rendait que des fiches **déjà écrites**, alors que `RoomFoodCatalog` en fabrique qui n'y sont pas encore. Le faux ne modélisait pas la moitié du monde, donc un test écrit contre lui éprouvait un chemin que l'application n'emprunte jamais.

**Choix, en deux parties indissociables.**

- **Le faux gagne une table de référence.** `InMemoryFoodCatalog(initial, reference)` : la seconde liste joue la table de l'ANSES — trouvable, jamais écrite, avec un identifiant provisoire **régénéré à chaque recherche**. Son `place` rapproche par `(source, source_ref)` et non par l'identifiant, comme le vrai. Sans cette moitié-là, le test qui attrape le défaut de l'étoile ne pouvait même pas s'écrire.
- **Un jeu de tests de contrat, écrit une fois et exécuté deux fois.** `FoodCatalogContract` couvre **six des sept ports** du projet — c'est la même paire de classes qui les porte tous — et `RoomFoodCatalogTest` comme `InMemoryFoodCatalogTest` en héritent. Une propriété que le faux s'autorise à ne pas tenir devient une ligne rouge à côté d'une verte, et non une découverte sur l'appareil.

**Il vit dans `:data:food`, pas dans `:core:testing`.** Les deux implémentations sont ainsi compilées et exécutées **côte à côte**, sous la même commande et dans le même rapport. En contrepartie, JUnit 4 et Robolectric entrent dans un second module — le contrat a besoin de Room, donc d'Android, et Robolectric est un lanceur JUnit 4. C'est l'extension de [D35](#d35--le-test-de-migration-tourne-sur-la-jvm-pas-sur-un-appareil---validée) et non sa contradiction : JUnit 4 reste cantonné aux modules qui ne peuvent pas s'en passer, et le moteur vintage les rassemble sous `./gradlew check`.

**La table de l'ANSES n'est pas garnissable**, puisqu'elle est livrée dans l'APK. Les fiches de référence du contrat sont donc de **vraies lignes** — codes 13039, 13037, 39213 — et la sous-classe Room **vérifie que le code et l'intitulé concordent** avant de jouer quoi que ce soit. Sans ce contrôle, une fixture périmée rendrait zéro résultat et la moitié des cas passeraient en ne mesurant rien : exactement le vert qui a déjà coûté deux corrections.

**Les deux corrections ont été éprouvées en les défaisant.** Remettre la recherche en lecture unique fait tomber quatre tests, et **du seul côté Room** — le faux, lui, passait toujours, ce qui est la démonstration littérale du problème. Retirer le versement au catalogue avant l'épinglage fait tomber le test du `ViewModel`.

**Ce que ça a révélé, et qui était un vrai défaut latent.** Un test de la tranche 3 affirmait que choisir un aliment rendait l'identifiant **provisoire** qu'on lui présentait. Il n'éprouvait que l'indulgence du faux : sur Room, choisir un aliment dont le code CIQUAL est déjà au catalogue rend l'identifiant **existant**, et c'est le comportement correct — l'inverse recopierait la fiche et remettrait ses compteurs d'usage à zéro. Le test disait donc le contraire de la règle qu'il croyait garder.

**Conséquences.** `:core:testing` gagne `TestDispatchers`, qui était recopié en privé dans les modules de test. Le port `FoodSearch` n'a plus de `suspend`, ce qui touche son unique appelant. Il reste **un** port à deux implémentations non couvert par un contrat, `DiaryRepository` ; la tranche 4 en ajoutera, et ils rejoindront le même dispositif.

---

## D54 — Un bandeau de rayons, et deux familles qui ne se combinent pas pareil · ✓ validée

**Contexte.** La recherche ne se parcourt pas : il faut savoir quoi taper. Un bandeau de pastilles sous la barre donne une entrée pour ceux qui ne cherchent pas un aliment précis mais **une sorte** d'aliment.

**Choix.** Huit rayons — Fruits, Légumes, Féculents, Viandes et poissons, Produits laitiers, Boissons, Desserts, Snacks — plus deux qualités, « Favori » et « Mon aliment ».

**Huit, et pas quarante-cinq.** La nomenclature de l'ANSES compte 45 sous-groupes ; c'est une classification de laboratoire, pas un bandeau qu'on parcourt au pouce. La correspondance est une **table maison versionnée**, `CiqualCategories` dans `:tooling:ciqual-import`, appliquée à l'import.

### La catégorie descend jusqu'au domaine

`Food.category` est un `FoodCategory?` du domaine, et le filtre est `FoodFilter`, une classe de `:domain` avec sa méthode `matches`. Un tag qui n'aurait été qu'une clause `WHERE` dans l'adaptateur ne s'éprouverait que sur un appareil, alors que **c'est une règle de ce que l'utilisateur voit**. Le SQL n'en est que l'accélération — sur 3 484 lignes, filtrer en Kotlin obligerait à toutes les lire pour en rendre trente — et le contrat de `FoodSearch` ([D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée)) vérifie que les deux disent la même chose.

**Deux familles, deux combinaisons.** Les rayons entre eux en **OU** : aucun aliment n'étant à la fois un fruit et un légume, l'intersection ne rendrait jamais rien. Les qualités en **ET** par-dessus : « Favori + Fruits » montre les fruits épinglés.

**Rien dans un texte ne dit qu'il y a deux familles**, donc trois canaux le disent, et aucun ne travaille seul : la **position** — les qualités ouvrent le bandeau —, un **trait** qui sépare les deux blocs, et la **forme** — une qualité est une pastille ronde à icône, un rayon un rectangle de texte. Un quatrième existe pour ceux qui ne voient rien : TalkBack annonce « Favori, qualité » contre « Fruits, rayon ». La règle de [08](08-design-system.md#daltonisme) est la même que partout — la couleur de sélection ne porte jamais seule une information.

### Le rayon n'est pas stocké dans `food`

Une fiche copiée au catalogue ne porte pas sa catégorie : elle est relue dans `ciqual.db` par `(source, source_ref)`, en un seul lot, exactement comme les portions le sont déjà.

**Raison.** Une copie figerait la correspondance du jour où elle a été faite. Corriger un rayon dans `CiqualCategories` n'atteindrait jamais les fiches déjà copiées, et **aucune migration ne pourrait le rattraper** : les deux bases sont deux fichiers, et une migration Room ne lit pas l'autre. Le rayon est une propriété de la **référence**, pas de la copie — à l'inverse exact des six valeurs, que le journal fige exprès ([D05](#d05--les-entrées-de-journal-figent-leurs-valeurs---validée)).

**Conséquence heureuse** : aucune colonne ajoutée à `food`, donc aucune migration, donc aucun problème de reprise sur une base existante. Le coût est une requête `IN (...)` par affichage.

**Écarté.** *Une colonne `category` dans `food`*, avec migration 2 → 3 : filtrage en SQL des deux côtés, mais les fiches déjà copiées seraient restées à `NULL` — donc muettes sous leur pastille — sans moyen honnête de les rattraper.

### Un tag seul est un mode parcours

Champ vide et une pastille : la liste des aliments du rayon. `FoodSearch.search` rend donc quelque chose pour une requête vide **si le filtre ne l'est pas** ; vides tous les deux, rien — le catalogue entier n'est pas une réponse.

**Les sections « Favoris » et « Récents » disparaissent dès qu'une pastille est active**, remplacées par la liste parcourue. Elles ne sont pas perdues pour autant : `FoodRanking` fait déjà remonter ce qui a un `useCount > 0`, donc « Fruits » liste les fruits qu'on mange d'abord. L'alternative — garder les deux sections filtrées **plus** la liste — faisait apparaître trois fois un fruit épinglé et récemment mangé.

**Une qualité écarte la table de l'ANSES.** Une ligne non versée au catalogue n'est ni personnelle ni épinglée ; l'interroger quand même rendrait des résultats que le filtre rejetterait juste après, et « Mon aliment » proposerait de supprimer une référence publiée.

### Un aliment personnel ne porte aucun rayon

Décision de l'auteur, appliquée telle quelle. **Contestation, pour mémoire** : la conséquence est que des « pâtes de mamie » ne sortent pas sous « Féculents », alors que l'utilisateur qui les a saisies sait très bien ce que c'est. Elle est acceptée parce que l'alternative — un sélecteur de catégorie au formulaire de création — ajoute un champ à chaque saisie manuelle pour un gain qui ne se voit qu'en mode parcours, et que le formulaire est déjà le point de friction du parcours. Le champ reste `null` en base : le jour où la question revient, il n'y a rien à migrer.

### Trois points d'outillage, dont un piège désamorcé

- **L'import échoue si la nomenclature bouge.** Même dispositif que `Nutrient` : le code fait foi, l'intitulé le vérifie. Deux dérives sont attrapées — une renumérotation, qui rangerait les poissons dans les desserts, et un **sous-groupe ajouté**, qui n'aurait aucun rayon sans que personne l'ait décidé. Un silence qui ressemble à un arbitrage est ce qu'il fallait rendre impossible.
- **Le nom du fichier copié porte une révision de schéma**, `ciqual-2025-11-03-r2.db`. Sans elle, ajouter une colonne sans que l'ANSES ait republié laissait le nom inchangé : un appareil déjà installé aurait gardé sa copie, et la première requête sur `category` aurait échoué **chez lui seulement** — jamais ici, où l'installation est toujours fraîche.
- **Le décompte par rayon est imprimé à chaque import.** 191 fruits, 352 légumes, 325 boissons, 754 sans rayon. Un rayon à douze aliments quand on en attendait deux cents se lit d'un coup d'œil, là où aucun test ne dirait qu'on s'est trompé de case.

**Ce que ça coûte.** `ciqual.db` passe de 824 à 924 Ko : la colonne porte le **nom** de l'énumération et non un entier, parce que la base est lue par une version de l'application qui n'est pas forcément celle qui l'a écrite, et qu'un client SQLite doit pouvoir la lire le jour où une pastille ne rend rien.

---

## D55 — L'objectif est calculé, daté, et parfois absent · ✓ validée

**Contexte.** La tranche 4 remplace l'objectif en dur de [D30](#d30--objectif-provisoire-en-dur-avec-sa-date-de-péremption---par-défaut) par un calcul réel : profil, dépense, garde-fous, répartition. Trois questions se posaient en même temps, et c'est leur combinaison qui décide.

### Le calcul est découpé en quatre objets, pas en un

`EnergyExpenditureCalculator`, `GoalSafetyPolicy`, `MacroDistributionPolicy`, puis `CalculateDailyGoal` qui les enchaîne et **ne décide de rien**. C'est ce qui permet d'éprouver un garde-fou sur ses deux bornes sans construire un profil complet : avec un profil réel, deux garde-fous mordent souvent ensemble, et le test ne dirait plus lequel.

**L'exemple de référence de [03](03-nutrition-calculs.md#exemple-complet) passe au kcal près**, contrôle de cohérence compris : 576 + 630 + 70 + 1 248 = 2 524, soit 1 kcal d'arrondi sur 2 525. C'est ce contrôle qui avait révélé les 70 kcal de fibres distribuées deux fois ([D24](#d24--les-fibres-sont-déduites-du-solde-glucidique---validée)), et il est désormais un test permanent plutôt qu'une vérification faite une fois.

**Les grammes sont entiers, et le solde se calcule sur eux.** Calculer les glucides sur des valeurs non arrondies puis arrondir donnerait un contrôle de cohérence qui ne retombe pas sur ce qui est **affiché** — même raisonnement qu'en [D52](#d52--deux-savedstatehandle-une-seule-saisie-manuelle-des-grammes-entiers---validée), et il vaut pour l'objectif comme pour la saisie.

### Une journée sans objectif n'est pas une journée à zéro

`DaySummary.goal` devient **nullable**, et c'est la conséquence directe de [D04](#d04--objectifs-versionnés-plutôt-que-mis-à-jour-en-place---validée). Une journée notée avant que le premier objectif existe n'a rien à quoi se comparer ; lui appliquer l'objectif courant la jugerait sur une règle qu'elle n'avait pas. L'accueil affiche alors les six totaux **sans jauge** — ce qui a été mangé reste exact, c'est la comparaison qui manque — et propose les cinq questions.

**Écarté.** *Un objectif par défaut écrit à la migration* : l'accueil aurait toujours eu une jauge, au prix d'un objectif que personne n'a demandé et d'un mois d'historique repeint. *Garder un `Placeholder` non nul* : c'était exactement la dette qu'il fallait lever, et elle se serait réinstallée sous un autre nom.

**La migration 2 → 3 n'écrit donc aucune donnée**, seulement trois tables. Rien d'existant ne bouge : un journal de six mois traverse sans qu'une ligne soit réécrite.

### L'invariant « au plus un objectif actif » est tenu par la base

`goal.active_key` vaut `1` tant que l'objectif court, et l'identifiant une fois clos. Un index unique dessus fait entrer deux objectifs actifs en collision.

**Pourquoi pas un index sur `ended_at`.** SQLite ne compare jamais deux `NULL` comme égaux : un index unique sur cette colonne ne contraindrait strictement rien, et l'invariant reposerait sur la discipline d'écriture. C'est le même mécanisme que l'index `(source, source_ref)` de `food`, retourné — là, la permissivité des `NULL` était ce qu'on voulait ; ici, c'est ce qu'il fallait contourner. Le port n'offre d'ailleurs **aucun** `update` : `replace` clôt et écrit en une transaction, et c'est le seul chemin.

### Ce que la tranche 4 ne construit pas

| Absent | Raison | Quand |
|---|---|---|
| ~~L'écran de **réglages profil**, avec verrouillage des champs édités~~ | La colonne `manual_fields` existe, le domaine la porte (`Goal.manualFields`), et le mapper la sérialise — mais **rien ne l'écrit encore**, faute d'écran d'édition. C'est une capacité à part entière : relire le profil, recalculer, et distinguer ce que l'utilisateur a fixé de ce que le calcul propose. | **Fait** en [D59](#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60), sous la forme que [D60](#d60--un-objectif-est-calculé-ou-saisi-et-on-consulte-avant-de-corriger---validée) lui a donnée |
| L'**adaptation hebdomadaire** (`SuggestGoalAdjustment`) | Elle demande une moyenne mobile sur 7 jours et une mesure d'adhérence, donc un historique de pesées que personne n'a encore. [12](12-plan-de-developpement.md) la place en tranche 7, avec le journal de poids. | Tranche 7 |
| ~~Un jeu de tests de **contrat** pour `Profiles`, `WeightLog` et `Goals`~~ | Le dispositif existe depuis [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) et ces trois ports ont deux implémentations chacun. Ils ne l'ont pas encore. | **Fait**, et il a attrapé un défaut latent plus une règle que rien ne gardait ([D57](#d57--le-contrat-des-trois-ports-et-une-règle-que-deux-tris-masquaient---validée)) |

**Ce que ça ne coûte pas.** La capacité annoncée par la tranche — « l'application connaît mon objectif » — est entière : on répond à cinq questions, l'objectif est calculé, il est daté, et chaque journée est jugée sur le sien.

---

## D56 — L'onboarding ouvre l'application, exige ses réponses, et dit non à voix haute · ✓ validée

**Contexte.** Constaté sur appareil : l'onboarding livré en [D55](#d55--lobjectif-est-calculé-daté-et-parfois-absent---validée) fonctionne et se traverse mal. Six défauts, tous d'ergonomie, aucun de calcul.

### L'application ouvre sur les questions

Il fallait passer par l'accueil, y lire « pas encore d'objectif », et appuyer sur un bouton. Un écran de transit qui ne sert qu'à en désigner un autre est un écran de trop.

`StartDestinationViewModel` lit **une seule fois** si un objectif court, et pose la destination de départ. La lecture est unique et non observée : `NavHost` reconstruit son graphe quand sa destination de départ change, ce qui **vide la pile de navigation** — l'objectif apparaissant à la fin de l'onboarding, un flux aurait fait se disputer deux mécanismes la même transition, avec un gagnant dépendant de l'ordre de recomposition.

Rien ne s'affiche tant que la réponse n'est pas connue. Poser l'accueil puis sauter vers l'onboarding ferait clignoter un journal vide à **chaque** lancement.

### Chaque étape exige ses champs

[02](02-parcours-et-ecrans.md#onboarding) promettait un bouton « Passer » sur les quatre dernières étapes. Il disparaît.

**Raison.** Un objectif calculé sur 30 ans, 170 cm et 70 kg est l'objectif de quelqu'un d'autre, et il s'affiche avec l'autorité d'un chiffre personnel. Les valeurs par défaut restent dans le code, mais comme garde-fou défensif — plus aucun parcours ne les atteint.

**Ce que ça coûte, et c'est réel.** Il n'y a plus moyen d'entrer dans l'application sans avoir répondu. Le « utilisateur pressé » de la conception n'a plus de raccourci. C'est assumé : cette application ne sert à rien sans objectif, et la traversée dure une minute.

### Un refus se voit

Appuyer sur « Continuer » sans avoir coché ne produisait **rien**. Le même défaut existait sur « Enregistrer » de l'écran de saisie, où [D48](#d48--la-barre-pleine-vaut-lobjectif---validée) avait pourtant prévu une explication : elle existait, sous forme d'un `labelSmall` gris glissé au-dessus des boutons — c'est-à-dire sous le pouce, au moment exact où l'œil est sur le bouton.

**Choix.** Les deux écrans répondent par une **barre**, et la phrase dit **ce qui** manque : « Renseignez votre date de naissance, votre sexe, votre taille et votre poids », pas « complétez le formulaire ». Le principe de [D28](#d28--un-bouton-indisponible-réagit-quand-même---validée) ne bouge pas — le bouton indisponible réagit et répond — c'est le canal qui change. Ce qui interrompt le regard est ce qui se lit.

Une seule barre à la fois : trois appuis empilaient trois fois le même message, le troisième arrivant dix secondes plus tard.

### Une date se choisit, elle ne se tape pas

`NeonDateField` : un champ en lecture seule qui ouvre le sélecteur de Material 3. Demander « AAAA-MM-JJ » au clavier fait porter à l'utilisateur un format que la machine sait deviner — il se trompe d'un tiret, et le champ reste vide sans dire pourquoi.

Trois points techniques qui ne vont pas de soi :

- **Une surface transparente reçoit le tap.** `OutlinedTextField` ne relaie pas les clics en `readOnly`, et le désactiver le grise — ce qui dirait « indisponible » alors qu'il est utilisable.
- **Le sélecteur raisonne en millisecondes UTC**, et il faut le prendre au mot. Convertir dans le fuseau local ferait basculer d'un jour toute personne à l'ouest de Greenwich : le 4 mars choisi reviendrait le 3.
- **La grille des années est bornée** à 110 ans en arrière. Une naissance ne se cherche pas en feuilletant les mois.

### L'échéance devient trois pastilles

`docs/02` prévoyait « +3 mois / +6 mois / +12 mois / **date libre** ». La date libre disparaît : une échéance exacte n'a aucune valeur en soi, ce qui compte est le rythme, et « six mois » l'exprime aussi bien qu'un 14 février choisi au hasard.

Ce qui la remplace est meilleur : quand un garde-fou mord, la **date atteignable** calculée par `GoalSafetyPolicy` apparaît en quatrième pastille. C'est la seule date arbitraire qui ait une justification.

**Écarté.** *Garder la date libre repliée derrière un lien* : elle sert le cas du mariage ou des vacances, mais c'est un chemin de plus à tenir pour un objectif dont la précision est de toute façon illusoire à ±10 % près ([03](03-nutrition-calculs.md#métabolisme-de-base-bmr)).

### Deux ajouts de confort, tirés de la conception

- **L'hexagone remplit l'écran d'accueil.** La figure qui donne son nom à l'application existait déjà et n'apparaissait nulle part avant le premier repas noté. Une journée d'exemple, volontairement inégale — six quartiers identiques ressembleraient à un motif décoratif.
- **L'aperçu de rythme** que `docs/02` demandait depuis la conception : `GoalPlan.weeklyWeightChangeKg`, dérivé du budget retenu **après** garde-fous. Le rythme réel, pas celui qu'on espérait — un second calcul dans l'écran aurait fini par annoncer autre chose que l'étape suivante.

**Conséquences.** `docs/02` est corrigé sur trois points, et l'un d'eux est un renoncement explicite. `OnboardingUiState.blocker` porte la règle de blocage : le `ViewModel` refuse d'avancer, l'écran s'en sert pour dire quoi, et les deux interrogent la même propriété — ils ne peuvent donc pas diverger.

---

## D57 — Le contrat des trois ports, et une règle que deux tris masquaient · ✓ validée

**Contexte.** `Goals`, `Profiles` et `WeightLog` ont deux implémentations chacun depuis la tranche 4 et aucun jeu de tests partagé — la dette que [D55](#d55--lobjectif-est-calculé-daté-et-parfois-absent---validée) inscrivait en toutes lettres. Le dispositif de [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) leur est appliqué tel quel : `ProfileStoreContract`, écrit une fois, joué sur les faux de `:core:testing` et sur `RoomProfileStore` sous Robolectric, côte à côte dans le même rapport.

### Ce que le contrat a attrapé, du seul côté du faux

`InMemoryWeightLog` ne gardait **qu'une pesée** — un `MutableStateFlow<WeightEntry?>`. Il tenait donc trois propriétés du port par accident : la limite ne bornait rien, le tri n'existait pas, et `observeLatest` rendait la dernière pesée **écrite** là où Room rend celle du jour le plus **récent**.

La conséquence n'était pas cosmétique : rattraper le lendemain une pesée oubliée aurait fait recalculer l'objectif sur un poids périmé. Rien ne le disait, parce que le seul appelant — l'onboarding — n'écrit jamais qu'une pesée. C'est la forme exacte du défaut que [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) a nommée, sur un autre port.

### La découverte : une règle publiée que deux tris masquaient

**La borne de fin exclue n'est pas observable par le port.** Vérifié en défaisant, ce qui est la seule façon de le savoir : passer la requête de `ended_at > :date` à `>=` laisse les 49 cas du contrat au vert. La raison est que `replace` clôt toujours un objectif à la date de début de son successeur — les deux périodes se touchent sans trou — et que le `ORDER BY started_at DESC LIMIT 1` rend alors le bon objectif de toute façon.

Le même aveuglement existait côté domaine, par un autre mécanisme : `InMemoryGoals.observeGoalOn` filtre par `coversOn` puis prend le `maxByOrNull { startedAt }`. **Deux tris différents masquaient la même règle des deux côtés**, et `Goal.coversOn` — publique, avec son KDoc qui affirme la convention — n'avait aucun test.

Ce qui la rend observable est un objectif clos **sans successeur qui reprenne le jour même**. `replace` n'en produit pas ; une sauvegarde restaurée le fera (tranche 8). La règle est éprouvée à deux endroits, hors contrat parce qu'elle n'y est pas atteignable : `GoalCoverageTest` interroge `coversOn` seule, et `GoalBoundsTest` écrit l'entité directement. Les deux tombent quand la borne est relâchée ; les cas voisins restent verts, faute de quoi une requête qui ne rendrait jamais rien passerait en ne mesurant rien.

**C'est aussi un cas de figure de la tranche suivante.** Corriger son objectif le jour même clôt l'ancien à sa propre date de début : il ne couvre alors aucune journée, et le nouveau prend le jour entier. Le cas est éprouvé avant d'exister.

### Ce que le contrat ne porte pas, et pourquoi

**Le repli d'une énumération inconnue.** Les énumérations du domaine sont fermées : aucun appelant de `Profiles` ou de `Goals` ne peut soumettre une valeur que le mapper aurait à replier. Le seul chemin qui l'atteint est une base **déjà écrite** — une sauvegarde restaurée depuis une version plus récente, ou une rétrogradation. La propriété appartient donc à la sérialisation et non au port, et `ProfileMapperTest` l'éprouve en écrivant l'entité directement. Elle était documentée depuis la tranche 4 et rien ne la vérifiait, alors qu'un repli qui plante rend le profil inaccessible, donc l'application entière.

**Écarté.** *Forcer ces deux propriétés dans le contrat* en ajoutant au port une écriture de bas niveau : ce serait élargir une interface du domaine pour les besoins d'un test, et le port cesserait de décrire ce que les appelants font.

### Trois délégués plutôt qu'un

`ProfileStoreView` prend `Profiles`, `WeightLog` et `Goals` séparément, là où `FoodCatalogView` n'a qu'un paramètre générique. Côté Room c'est le même objet trois fois ; côté mémoire ce sont trois classes distinctes. **Écarté** : *un `InMemoryProfileStore` unique* qui aurait porté les trois, pour la symétrie — il aurait imposé aux faux une forme d'assemblage qui n'existe que pour le test, alors que les écrans les injectent séparément.

**Conséquences.** `InMemoryProfiles` et `InMemoryWeightLog` quittent le test d'onboarding pour `:core:testing`, où les autres modules peuvent s'en servir. Le `recorded` du faux devient `latest` et `entries` : il rendait la dernière écriture, il rend maintenant un journal trié, et le nom devait le dire. `DiaryRepository` reste le seul port à deux implémentations sans contrat — jusqu'à [D58](#d58--le-septième-port-et-un-champ-redondant-que-la-fixture-a-trahi---validée).

---

## D58 — Le septième port, et un champ redondant que la fixture a trahi · ✓ validée

**Contexte.** `DiaryRepository` était le dernier port à deux implémentations sans contrat — celui que [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) mettait de côté et que [D57](#d57--le-contrat-des-trois-ports-et-une-règle-que-deux-tris-masquaient---validée) a reconduit. C'est aussi le plus ancien, celui de la tranche 1 : ses deux côtés ont eu le plus de temps pour diverger sans que rien ne le dise.

19 cas, joués sur `InMemoryDiaryRepository` et sur `RoomDiaryRepository`. `:data:diary` n'avait **aucun** test et n'était pas outillé pour Robolectric ; il l'est désormais, comme `:data:profile`.

### `FoodEntry.dishId` est redondant, et les deux côtés n'en font pas le même usage

C'est la découverte, et elle est venue de la **fixture**, pas d'un cas de test. Un plat porte ses lignes, et chaque ligne porte en plus l'identifiant de son plat. Le faux ne lit jamais ce champ — ses lignes vivent dans l'objet plat — là où Room s'en sert comme **clé de rattachement**.

Conséquence : un `copy(id = …)` sur un plat, pour en fabriquer un second, produit un objet dont les lignes désignent encore le premier. Le faux l'accepte sans broncher ; la base le refuse, par violation de clé étrangère ou de clé primaire selon l'ordre d'écriture. Trois cas sont tombés du seul côté Room avant que la fixture soit corrigée.

**Ce n'est pas un défaut du code de production** — aucun appelant ne fabrique un plat ainsi — mais c'est une arête sur laquelle on se coupe, et un test la nomme désormais : les lignes relues sont rattachées à leur plat. **Écarté** : *retirer `dishId` de `FoodEntry`*, qui supprimerait la redondance à la racine. C'est une modification du domaine pour un problème qui ne s'est manifesté que dans un test, et la tranche 5 n'a pas besoin de ça.

### Ce que le contrat garde, éprouvé en le défaisant

Deux fois, et du seul côté Room dans les deux cas : retirer `deleteEntriesOfDish` de la transaction d'écriture fait tomber deux tests — une ligne supprimée à l'écran resterait en base ; passer `ORDER BY logged_at` de `ASC` à `DESC` en fait tomber un.

Le contrat éprouve aussi la règle la plus coûteuse du projet, celle que [D29](#d29--un-total-incomplet-se-signale-au-lieu-de-se-taire---validée) protège : une valeur inconnue reste inconnue et ne devient jamais zéro. La fixture pose des sucres à `0.0` **à côté** de fibres à `null`, dans la même ligne — c'est la paire qui rend la confusion visible si elle a lieu.

**Ce qu'il ne porte pas.** La suppression d'un plat vidé de sa dernière ligne : c'est `DeleteEntry` qui en décide, pas le port. Le port laisse un plat vide, des deux côtés, et le contrat l'affirme — si le port le faisait aussi, la règle serait tenue à deux endroits et il suffirait qu'un seul change.

**Conséquences.** Les sept ports à deux implémentations sont couverts. Le dispositif de [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) n'a plus de dette : tout port qui gagnera une seconde implémentation rejoint un mécanisme déjà en place dans trois modules.

---

## D59 — Le profil se corrige, et le verrou survit au recalcul · ⊘ en partie remplacée par D60

**Contexte.** La dernière capacité annoncée par la tranche 4 et non livrée ([D55](#d55--lobjectif-est-calculé-daté-et-parfois-absent---validée)) : relire son profil, le corriger, et distinguer ce que l'utilisateur a fixé de ce que le calcul propose. La colonne `goal.manual_fields` existait, `Goal.manualFields` la portait, `ProfileStoreContract` éprouvait son aller-retour — et **rien ne l'écrivait**, faute d'écran.

### Pas d'écran « Réglages » au-dessus

[02](02-parcours-et-ecrans.md#réglages) décrit cinq sections. Quatre dépendent des tranches 6 et 8 — fournisseurs d'IA, sauvegarde, apparence, à propos. L'accueil ouvre donc **directement** « Profil et objectifs », par une icône à côté du titre du jour.

C'est le raisonnement de [D56](#d56--lonboarding-ouvre-lapplication-exige-ses-réponses-et-dit-non-à-voix-haute---validée) appliqué une seconde fois : un écran de transit qui ne désigne qu'une destination est un écran de trop, et quatre entrées qui n'ouvrent rien ne sont pas une avance. Le hub naîtra avec la deuxième section qui aura du contenu ; il coûtera alors une composable et un rappel de plus.

### Un aperçu vivant, un seul bouton, un seul calcul

[02](02-parcours-et-ecrans.md#réglages) promettait un bouton « Recalculer mes objectifs » **à côté** de l'édition manuelle des six valeurs. Il disparaît, et ce n'est pas un renoncement : les six compteurs suivent chaque correction en direct, comme aux cinq questions. Un bouton de recalcul n'aurait rien eu à recalculer que l'écran n'ait déjà fait.

**Ce que ça achète est plus important que le bouton.** Il n'existe qu'**un** calcul, et c'est celui qui est affiché qui est écrit : `GoalRevision` porte les six chiffres que l'écran montrait au moment de l'appui, et `ReviseGoal` ne recalcule rien. Un cas d'usage qui aurait refait le calcul au moment d'écrire ouvrait la porte à enregistrer autre chose que ce qu'on venait de lire — un écart qui n'apparaît qu'une fois la ligne écrite. Même raison que l'aperçu de rythme de [D56](#d56--lonboarding-ouvre-lapplication-exige-ses-réponses-et-dit-non-à-voix-haute---validée).

**Écarté.** *Le bouton explicite de `docs/02`* : il obligeait à afficher, entre la correction et l'appui, six chiffres qui ne correspondent plus au profil qu'on lit juste au-dessus. Un écran qui se contredit pendant quelques secondes apprend à ne pas croire ce qu'il affiche.

### Le verrou est une pastille, et elle porte l'état **et** le geste

Un compteur fixé à la main se signale par une pastille sélectionnée, et c'est la même pastille qu'on touche pour le rendre au calcul. C'est la « confirmation explicite » que [02](02-parcours-et-ecrans.md#réglages) exige avant qu'un recalcul reprenne la main. Un marqueur d'un côté et un bouton de l'autre auraient laissé croire à deux notions distinctes.

**Quand un compteur est fixé, l'écran affiche aussi ce que le calcul proposerait.** Sans ce repère, un compteur verrouillé il y a trois semaines reste un chiffre sans référence, et on ne sait plus s'il vaut encore la peine d'être tenu.

La règle elle-même est dans le domaine, en une fonction : `DailyGoal.overriddenBy`. Elle est éprouvée en la défaisant — neutralisée, un seul test tombe, et c'est celui qui la nomme.

**Ce que ça ne fait pas, et c'est voulu.** Fixer les protéines sans toucher aux calories creuse l'écart que `GoalPlan.energyGap` mesure. Rien ne le signale à l'écran : **les calories font foi**, les macros sont des répartitions indicatives ([03](03-nutrition-calculs.md)), et cet écart-là est voulu par celui qui l'a saisi. Un avertissement transformerait une décision en faute.

### Deux écritures qu'on n'écrit pas

**Enregistrer sans avoir rien changé n'ouvre aucune version.** `Goal.sameAimAs` compare le cap — stratégie, poids visé, échéance, six chiffres, verrous — en ignorant l'identifiant, les dates de validité et la provenance, qui sont des faits sur la ligne et non sur le cap. Sans cette comparaison, ouvrir les réglages et ressortir écrirait une ligne de plus à chaque visite, et l'historique des changements de cap — la contrepartie qu'on paie en versionnant ([D04](#d04--objectifs-versionnés-plutôt-que-mis-à-jour-en-place---validée)) — deviendrait un journal de consultations.

**Une pesée n'est écrite que si le poids a changé.** Le champ affiche la dernière mesure connue, parce que c'est elle qui entre dans le calcul ; la réécrire à la date du jour parce que l'écran a été ouvert affirmerait qu'on s'est pesé aujourd'hui, et la moyenne mobile sur sept jours de la tranche 7 compterait alors une mesure que personne n'a faite.

Ces deux règles sont dans `ReviseGoal` et non dans l'écran : ce sont des règles sur ce qu'on enregistre, et un second appelant — une sauvegarde restaurée, une suggestion d'ajustement acceptée — les trouvera en place.

### `GoalHorizon` monte dans `:domain`, les prédicats de complétude restent en double

Deux écrans proposent désormais les mêmes échéances. Deux listes d'horizons finiraient par ne plus dire la même chose, et c'est un écart qu'aucun test ne signale : les deux écrans seraient verts, simplement pas d'accord. L'énumération quitte donc `:feature:onboarding`.

`identityComplete` et `objectiveComplete`, en revanche, sont **réécrits** dans `:feature:settings`. Les mettre en commun demanderait de remanier `OnboardingAnswers`, qui porte en plus un avertissement à accepter et une étape courante ; c'est une duplication choisie, et son échéance est le jour où une troisième forme apparaîtra — la sauvegarde restaurée de la tranche 8, qui aura elle aussi à juger si un profil est complet.

**Conséquences.** La tranche 4 est terminée. `manual_fields` cesse d'être une colonne que seul un test remplit. `:feature:settings` naît avec une seule section, et l'accueil gagne sa première porte secondaire. Deux textes de l'onboarding qui contredisaient encore [D56](#d56--lonboarding-ouvre-lapplication-exige-ses-réponses-et-dit-non-à-voix-haute---validée) sont corrigés au passage : « vous pouvez en sauter quatre » et le format `AAAA-MM-JJ` demandé sous un champ qui ouvre un sélecteur.

---

## D60 — Un objectif est calculé ou saisi, et on consulte avant de corriger · ✓ validée

**Contexte.** L'écran livré en [D59](#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) fonctionne et se lit mal. Quatre reproches, tous d'ergonomie, dont deux touchent au modèle de données.

### Le verrou par compteur devient un **mode**

[D59](#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) permettait de figer les protéines à l'intérieur d'un objectif calculé. C'était le prolongement direct de la colonne `manual_fields`, et c'était un troisième état : ni calculé, ni saisi — calculé *sauf trois*. L'écran devait l'expliquer six fois, une par ligne, et le poids cible pilotait trois compteurs sur six sans que rien ne dise lesquels.

**Choix.** Un objectif est **calculé** ou **saisi à la main**, et un interrupteur bascule de l'un à l'autre. Passer en manuel ouvre les six champs, en partant des chiffres affichés. `GoalOrigin` portait déjà la distinction ; elle devient la seule.

**Écarté.** *Garder `manualFields` rempli des six* : aucune migration, mais `origin` et l'ensemble auraient dit la même chose de deux façons, et rien n'aurait empêché une ligne où les deux se contredisent — la redondance que [D58](#d58--le-septième-port-et-un-champ-redondant-que-la-fixture-a-trahi---validée) a nommée, réintroduite volontairement. *Garder le grain fin sous le mode* : la souplesse survivait, le troisième état aussi.

**La colonne part donc en migration 3 → 4**, et la table est recréée : `ALTER TABLE … DROP COLUMN` n'existe dans SQLite que depuis la 3.35, livrée avec Android 14, et le projet descend à `minSdk 26`.

**Ce que la migration a appris.** Les index ne suivent pas une table renommée. Les oublier laisse la migration réussir, laisse le schéma exporté valide — `runMigrationsAndValidate` **ne l'a pas vu** — et fait disparaître l'invariant « au plus un objectif actif » chez ceux qui migrent, chez eux seulement. C'est un test de comportement qui l'attrape, pas la validation de schéma : la vérification a été faite en retirant la ligne.

### Le poids cible reste, et l'écran dit qu'il ne pilote plus rien

En saisie manuelle, le poids visé et l'échéance ne produisent plus aucun chiffre. Ils restent pourtant modifiables et enregistrés : ils décrivent le **cap annoncé**, et c'est de là que le journal de poids tirera sa trajectoire en pointillés (tranche 7).

Deux champs qui ne font rien sans le dire feraient croire qu'une correction d'échéance déplace les compteurs ; une phrase le dit donc à l'endroit exact où la question se pose. Ils cessent aussi d'être **exigés** en mode manuel — exiger une date qui ne sert à rien serait exiger pour la forme.

**Écarté.** *Les masquer* : le cap disparaîtrait de l'écran et de la trajectoire. *Les effacer à la bascule* : revenir au calcul obligerait à tout ressaisir.

### On consulte, et le crayon ouvre la modification

L'écran ouvre en **lecture** : des lignes, pas des champs. Un écran de réglages entièrement saisissable invite à corriger ce qu'on venait relire, et n'offre aucun moment où l'on puisse simplement vérifier un chiffre.

Le crayon commande **tout**, interrupteur de mode compris. Laisser la bascule active en consultation aurait ouvert l'édition de fait — les six champs apparaissent — c'est-à-dire deux portes vers le même état, dont une qui ne se déclare pas.

### Un changement d'objectif se montre avant de s'écrire

Corriger sa taille de quatre centimètres déplace un objectif quotidien. À la validation, si les six chiffres changent, une boîte les affiche **face aux anciens** et rien n'est écrit tant qu'elle n'a pas été acceptée.

**C'est un écart avec [02](02-parcours-et-ecrans.md#comportements-transverses)**, qui réserve le dialogue au destructif et à l'irréversible, et il est assumé : il n'y a aucun autre endroit où poser six lignes de chiffres. Une barre n'en porterait pas trois, et un encart replié sous les champs serait sous le pouce — le défaut exact que [D56](#d56--lonboarding-ouvre-lapplication-exige-ses-réponses-et-dit-non-à-voix-haute---validée) a corrigé. L'action reste réversible, mais elle ouvre une version de l'objectif, et c'est cela qu'on annonce.

**La boîte n'apparaît que si les six chiffres bougent.** Basculer en manuel sans rien retoucher écrit une nouvelle version — `origin` a changé, donc le cap a changé — mais sans dialogue : un dialogue qui répète ce qu'on vient de lire s'apprend à fermer sans le lire, et il ne protégerait plus rien le jour où il aurait quelque chose à dire.

### Ce que `sameAimAs` compare désormais

`origin` entre dans la comparaison. Deux objectifs qui portent les mêmes six chiffres ne disent pas la même chose selon leur provenance : le premier suivra la prochaine correction de profil, le second non. Sans cela, la bascule vers le manuel n'aurait rien écrit et serait perdue au retour à l'écran.

**Conséquences.** `Goal.manualFields` et `DailyGoal.overriddenBy` disparaissent ; `DailyGoal.with` reste, et sert maintenant à construire un objectif manuel compteur par compteur. La règle « un recalcul ne réécrit pas ce qui est saisi » change de forme sans changer de fond : elle était une fusion dans le domaine, elle est maintenant l'absence de calcul — `ReviseGoal` écrit les six chiffres **tels quels**, et un test le prouve en lui passant des chiffres qu'aucun calcul ne produirait.

---

## D61 — Un plat vidé se supprime, et l'appui long ouvre ses actions · ✓ validée

**Contexte.** Deux gestes que l'application refusait ou n'offrait pas, constatés à l'usage.

### Retirer la dernière ligne d'un plat le supprime, partout

`DeleteEntry` supprimait déjà le plat vidé de sa dernière ligne — c'était sa règle dès la tranche 2. L'écran de validation, lui, opposait un refus : `EntryDraft.saveable` exigeait au moins une ligne, donc « Enregistrer » restait indisponible et expliquait qu'il en fallait une.

Le même geste réussissait donc par un chemin et échouait par l'autre, pour une règle que le projet avait déjà tranchée : **un plat sans contenu n'est pas un plat à zéro calorie, c'est une saisie qui n'a pas eu lieu.** `UpdateDish` supprime désormais quand le brouillon est vide.

**Une saisie neuve vidée ne supprime rien**, faute d'avoir quoi que ce soit à supprimer, et c'est `EntryDraft.editing` qui fait la différence. Sans elle, « Enregistrer » serait devenu actif sur un écran où l'on n'a encore rien tapé.

**Le bouton change de libellé** — « Supprimer ce plat » — dès qu'il ne reste aucune ligne. Laisser « Enregistrer » ferait disparaître le plat sous un mot qui annonce le contraire, et le geste est exactement celui qui, une ligne plus tôt, enregistrait une correction.

### L'appui long ouvre un menu, et il double le tap

Le plat entier est une cible tactile depuis [D48](#d48--la-barre-pleine-vaut-lobjectif---validée), et le tap ouvre la modification. L'appui long ouvre un menu — **Modifier**, **Supprimer** — qui donne accès à ce qui n'a pas sa place sur la surface du plat.

« Modifier » y figure bien qu'il double le tap : un menu dont la moitié des entrées manque oblige à se souvenir de quel geste sert à quoi, et c'est exactement ce qu'un menu sert à éviter.

### Supprimer un plat se confirme, et reste annulable

Le balayage d'une ligne se contente de sa barre d'annulation. Supprimer un plat en emporte *n* d'un coup : un dialogue le demande d'abord, et **il dit le nombre**.

**C'est un écart avec [02](02-parcours-et-ecrans.md#comportements-transverses)**, qui réserve le dialogue au destructif et à l'irréversible — or une suppression annulable pendant cinq secondes ne l'est pas. Il est assumé : ce qui distingue ce cas du balayage n'est pas la réversibilité, c'est le volume.

**La barre reste offerte ensuite**, et les deux ne font pas double emploi : la confirmation évite l'accident, la barre rattrape le regret. Le mécanisme existait déjà — `RestoreDish` remet le plat et ses lignes en place — et le retirer aurait été enlever une sécurité pour n'en gagner aucune.

**Conséquences.** `DeleteDish` naît comme cas d'usage, pour un seul appel de port : un `:feature` ne voit que des cas d'usage, et l'exception se serait payée à la première règle qu'on aurait voulu y mettre. `DomainModule` se scinde en deux — journal et objectif — parce qu'il atteignait le seuil de fonctions de detekt ; la coupure existait déjà dans la lecture.

---

## D62 — Un favori est un modèle vivant, et l'étoile est son seul interrupteur · ✓ validée

**Contexte.** La dette la plus ancienne de [D50](#d50--ce-que-la-tranche-3-ne-construit-pas---validée) : [02](02-parcours-et-ecrans.md#modale--recherche) promet « favoris : aliments **et** plats », et seuls les aliments existaient. Cinq questions se posaient ensemble, et c'est leur combinaison qui décide.

### Un favori porte un nom, proposé puis réécrit

[07](07-modele-de-donnees.md) le disait déjà : « c'est le seul endroit où un nom est demandé ». Il est **proposé** depuis les trois premiers aliments — « Flocons, Lait, Banane » — et librement réécrit en « Petit-déj ».

**Écarté.** *Un nom entièrement dérivé des lignes* : aucune saisie, un geste de moins, mais deux plats aux mêmes aliments et aux quantités différentes porteraient le même nom et ne se distingueraient plus. *Un champ vide* : on renonce à l'étoile une fois sur deux.

**Le nom est unique**, à la casse et aux accents près — deux « Petit-déj » dans une liste ne se distinguent plus, et choisir devient un pari. L'unicité porte sur le nom **normalisé** par la fonction de [D49](#d49--la-recherche-normalise-à-limport-pas-au-tokenizer---validée), et elle est tenue **deux fois** : le cas d'usage la vérifie pour pouvoir répondre une phrase, l'index unique la garantit. C'est le raisonnement de `goal.active_key` ([D55](#d55--lobjectif-est-calculé-daté-et-parfois-absent---validée)) : une règle tenue par la seule discipline d'écriture n'en est pas une.

### Hybride : la fiche quand il y en a une, les valeurs sinon

Un composant référence une fiche **vivante** quand la ligne en vient d'une : rejouer « mes flocons du matin » reflète la fiche courante, ce que [07](07-modele-de-donnees.md) demandait. Une ligne tapée à la main n'a pas de fiche derrière elle, et ses valeurs sont donc figées.

**Les six valeurs sont enregistrées dans les deux cas**, et c'est ce qui rend le favori increvable : contenu d'une ligne sans fiche, et **repli** le jour où la fiche citée est supprimée. Sans elles, un favori pourrait rejouer une ligne sans le moindre chiffre.

**Écarté.** *L'instantané pur* — le favori copie tout et ne suit rien : le plus prévisible, mais corriger une fiche ne corrigerait jamais les plats qui la citent, ce que `docs/07` voulait précisément éviter. *Le strict* — refuser un plat contenant une ligne sans fiche : fidèle au modèle, mais l'étoile refuserait sur un critère **invisible**, puisque rien à l'écran ne distingue une ligne tapée d'une ligne issue d'une fiche.

### Le lien plat ↔ favori est une colonne, et il tombe à la première retouche

`dish.favorite_id` dit de quel favori un plat a été rejoué. Sans lui, rien ne pourrait rallumer l'étoile en rouvrant un plat, ni répondre à « ce plat est-il un favori ? ».

**Il tombe dès qu'une ligne est touchée** — ajoutée, corrigée, supprimée. Le brouillon cesse alors d'être celui que le favori décrit, et l'étoile s'éteint. Le lien **ne se rétablit pas** en revenant en arrière, et c'est assumé : comparer le brouillon courant à celui d'origine ferait dépendre l'étoile d'une égalité sur des flottants.

**Le favori d'origine, lui, survit.** C'est ce qui donne son sens au refus « Un plat en favori porte déjà ce nom » quand on rallume l'étoile après avoir modifié un plat rejoué.

**Supprimer un favori délie les plats qui en venaient**, par `ON DELETE SET NULL` : un journal est un registre d'événements, et le modèle qui a servi à composer un repas n'a pas à emporter le repas en disparaissant. Même règle que pour un aliment personnel supprimé.

### Éteindre l'étoile supprime le favori

C'est le **seul** chemin pour retirer un plat de la liste. La liste des favoris ne sert qu'à en choisir un ; lui ajouter un geste de suppression aurait fait deux endroits pour la même décision, donc deux endroits à tenir d'accord.

### Ce que la migration 4 → 5 a coûté

`dish` est **recréée**. Ajouter une colonne se fait bien en `ALTER TABLE ADD COLUMN`, mais pas une clé étrangère — SQLite ne sait pas en ajouter une après coup. Room exécute ses migrations avec `PRAGMA foreign_keys = FALSE`, ce qui rend le `DROP` / `RENAME` sûr, et le test de la cascade `dish → food_entry` — écrit en tranche 1 — couvre le cas sans avoir été retouché.

**Conséquences.** `FavoriteDishes` naît **avec** son jeu de tests de contrat, 13 cas joués des deux côtés : c'est la règle de [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée), et l'écrire après coup aurait laissé le temps aux deux implémentations de diverger. Il a payé tout de suite — le faux comparait les noms bruts.

Le seuil de paramètres de detekt a forcé trois regroupements dans les `ViewModel`, et les trois sont des gains : `OpenDraft` rassemble les **quatre entrées** de l'écran de validation — plat, favori, fiche, rien — que la tranche 6 aurait encore multipliées ; `SaveDraft` retire à l'écran le choix entre créer et corriger ; `ToggleDishFavorite` porte la bascule et son ordre d'écriture. Le point de convergence que [12](12-plan-de-developpement.md) désigne depuis la conception existe enfin comme un objet, et non comme une suite de branches.

---

## D63 — Le code-barres est une clé, et le client s'éprouve devant un vrai serveur · ✓ validée

**Contexte.** Le premier tiers de la tranche 5 : le client Open Food Facts, sans écran ni base. La tranche entière tenait trois capacités — lire un code, interroger le service, mettre en cache — et une seule pull request les aurait rendues illisibles. Celle-ci s'arrête à la frontière du domaine.

### Un code-barres est une clé, donc un type

`Barcode` a un **constructeur privé** et une fabrique qui refuse. Une chaîne aurait suffi à transporter la valeur ; elle n'aurait pas garanti que deux lectures du même produit donnent la même clé — et c'est exactement ce dont dépend la promesse de la tranche : *« le deuxième scan du même produit est instantané et fonctionne en mode avion »*. Le catalogue local retrouve une fiche par son `source_ref` ; si la première lecture y écrit douze chiffres et la seconde en cherche treize, le cache ne sert jamais, et le défaut ne se voit **que** hors ligne.

**UPC-A devient EAN-13**, par un zéro devant. La conversion est exacte et ne change pas la clé de contrôle — le chiffre ajouté tombe sur un poids 1. La même fonction vérifie donc les trois symbologies, parce que les poids se comptent depuis la droite.

**UPC-E est refusé, et c'est un écart assumé avec [02](02-parcours-et-ecrans.md#modale--scan-de-code-barres).** Huit chiffres ne disent pas s'ils sont un EAN-8 ou un UPC-E compressé, ML Kit ne décompresse pas, et lire un UPC-E comme un EAN-8 produirait un code **plausible désignant un autre produit** — la pire des issues, puisqu'elle afficherait une fiche fausse au lieu de dire « introuvable ». La clé de contrôle le rattraperait neuf fois sur dix, ce qui est une coïncidence et non une règle. Le symbole est rare en France ; le jour où l'un est réellement scanné, la symbologie descendra dans le domaine et la décompression avec.

**Écarté.** *Accepter n'importe quelle chaîne de chiffres* : la mise sous forme canonique aurait alors vécu chez chaque appelant, c'est-à-dire nulle part.

### `ProductSource` n'est pas `BarcodeLookup`

[06](06-architecture.md#i--ségrégation-des-interfaces) rangeait `BarcodeLookup` parmi les six ports du catalogue **local**, tous tenus par un seul adaptateur. La source distante en est un septième, et les deux se lisent dans cet ordre : le local répond en millisecondes ou ne répond pas, l'autre demande le réseau. Les confondre aurait fait dépendre l'écran de scan d'un port qui peut mettre deux secondes à répondre.

**Trois issues et pas deux** — trouvé, inconnu, injoignable. [02](02-parcours-et-ecrans.md#modale--scan-de-code-barres) en attend trois écrans différents : « le produit n'existe pas là-bas » invite à le créer, « je n'ai pas pu demander » invite à le créer **aussi**, mais dit que la question reste posée. Les confondre ferait annoncer une absence qu'on n'a pas vérifiée.

**Aucune exception ne franchit la frontière**, comme pour `FoodRecognizer` ([06](06-architecture.md#l--substitution-de-liskov)) : une panne réseau est une réponse du port, pas un accident.

**Une fiche sans nom compte comme inconnue.** Le nom est le seul champ bloquant de [04](04-sources-de-donnees.md#champs-récupérés) ; l'annoncer trouvée remplacerait le formulaire de création par un écran vide.

### Le retrait exponentiel n'est pas un intercepteur

[04](04-sources-de-donnees.md#appel) l'y plaçait. Il vit dans la fonction suspendue, pour deux raisons qui vont ensemble : un intercepteur attend avec `Thread.sleep`, donc immobilise un fil du répartiteur d'OkHttp, là où `delay` suspend ; et c'est ce qui rend les trois tentatives **éprouvables** — sous `runTest`, l'attente est du temps virtuel, et le cas s'exécute en millisecondes au lieu d'une seconde et demie.

**Hors ligne, on ne réessaie pas.** Le retrait sert à laisser passer une surcharge du service. Sans réseau, il ne ferait que retarder une phrase qu'on peut dire tout de suite, à quelqu'un debout devant un rayon.

### Le montage HTTP est sorti du module Hilt, pour que le test monte le vrai

`openFoodFactsClient` et `openFoodFactsApi` sont des fonctions ; le module Hilt n'est plus que la portée. Le test dresse donc **la même pile** devant un serveur local, intercepteur compris.

C'est ce qui donne sa valeur à l'assertion sur le `User-Agent`. Recopié dans le test, le montage aurait éprouvé un client qui *ressemble* au vrai, et l'en-tête que [D26](#d26--le-user-agent-dopen-food-facts-est-figé---en-partie-remplacée-par-d105) rend obligatoire aurait pu manquer en production avec un test vert. C'est la forme de défaut que [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) a nommée, appliquée à un service tiers au lieu d'une base.

**Le `User-Agent` est fourni par `:app`.** Le numéro qu'exige [D26](#d26--le-user-agent-dopen-food-facts-est-figé---en-partie-remplacée-par-d105) est celui du **binaire**, suffixe de variante compris ; un module de bibliothèque qui le relirait dans le catalogue de versions donnerait un second endroit à tenir d'accord.

### Un test qui passait aussi bien sans la règle

Le cas « un état à zéro rend `Unknown` » envoyait un état à zéro **et** un produit nul. Le produit nul suffisait à le faire passer : neutralisée, la lecture de l'état ne faisait tomber personne. Corrigé — la réponse porte désormais un produit complet, et l'enveloppe seule décide.

C'est la troisième fois que cette forme apparaît, après [D57](#d57--le-contrat-des-trois-ports-et-une-règle-que-deux-tris-masquaient---validée) et [D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée), et elle ne se voit **que** par la mise à l'épreuve : cinq règles ont été défaites une à une, quatre ont fait tomber les cas qui les nomment, la cinquième non.

### Ce que la requête demande, et ce qu'elle ne demande plus

`quantity` et `image_front_small_url` sortent de la liste de [04](04-sources-de-donnees.md#appel) : rien ne les lit. Demander un champ inutilisé est le même travers qu'une colonne que rien ne remplit ([D34](#d34--la-table-food-attend-la-tranche-qui-la-remplit---par-défaut)), et il se paie ici en octets sur une connexion mobile, contre un budget de deux secondes.

**Conséquences.** `:integration:openfoodfacts` naît avec sa permission `INTERNET` — déclarée par le module qui en a besoin, et non dans `:app`, pour qu'elle disparaisse avec lui. `ClientIdentity` est une `data class` et non une `value class` : Dagger décore le nom d'une fonction qui prend une classe en ligne, et la génération échoue alors sur un `IllegalArgumentException: not a valid name` qui ne dit rien de sa cause. Rien n'appelle encore ce port : le cache, le catalogue local et l'écran de scan sont les deux tranches de travail suivantes.

---

## D64 — Le cache prend date, et un code-barres ne traverse pas deux espaces de noms · ✓ validée

**Contexte.** Le deuxième tiers de la tranche 5 : ce qui rend le scan **utile deux fois**. [D63](#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée) a livré un client que rien n'appelait ; ici, un code-barres devient une fiche, et la fiche reste.

### `LookupBarcode` : le catalogue d'abord, et l'ordre **est** la fonctionnalité

C'est lui qui tient les deux promesses de la tranche — une fiche en moins de deux secondes, et un deuxième scan instantané qui marche en mode avion. Interroger le service d'abord les perdrait toutes les deux, et **aucun test de correspondance ne s'en apercevrait** : le mappeur, les DTO et l'intercepteur seraient tous verts. Les cas de ce cas d'usage comptent donc les appels au réseau, et l'un d'eux joue **deux** scans d'affilée — un test qui ne jouerait que le premier laisserait passer un cache qui n'écrit rien.

### Une fiche récupérée est versée au catalogue tout de suite

C'est un écart délibéré avec la règle des aliments de la table de l'ANSES, qui n'y entrent qu'à l'enregistrement du plat ([D50](#d50--ce-que-la-tranche-3-ne-construit-pas---validée)). La raison tient en une phrase : là-bas la source est embarquée et toujours disponible, ici elle est à l'autre bout d'un réseau qu'on n'a peut-être plus.

Elle n'entre pas pour autant dans « Récents » : `last_used_at` reste nul tant que rien n'a été mangé. Un produit repose sur l'étagère aussi souvent qu'il finit dans un plat, et cette liste dit ce qu'on mange.

### Deux colonnes, et une seule des deux règles de [D50](#d50--ce-que-la-tranche-3-ne-construit-pas---validée)

`is_liquid` et `fetched_at` arrivent ; `density` et `user_edited_fields` non. La ligne de partage n'est pas celle qu'annonçait [D50](#d50--ce-que-la-tranche-3-ne-construit-pas---validée) — elle disait « les trois autres avec le cache » — et elle est plus juste : **est-ce que l'information se perd si on ne la note pas maintenant ?**

- `fetched_at` et `is_liquid` ne sont connaissables **qu'au moment de la récupération**. Sans elles, toutes les fiches mises en cache d'ici la tranche 6 seraient sans âge, donc indistinguables d'une fiche d'hier, et leur nature de boisson demanderait de réinterroger le service produit par produit.
- `user_edited_fields` enregistre une action **future** de l'utilisateur, et rien n'est perdu à l'ajouter le jour où le rafraîchissement existe — d'autant qu'aucun écran ne permet aujourd'hui d'ouvrir une fiche Open Food Facts pour la corriger. `density` attend le résolveur qui la lit.

**`is_liquid` est nullable, contrairement à ce qu'annonçait [07](07-modele-de-donnees.md).** Trois états et non deux, pour la même raison que les huit teneurs : rien ne dit d'un aliment de l'ANSES s'il est liquide, et un `0` par défaut l'affirmerait de 3 484 lignes que personne n'a regardées.

**La migration 5 → 6 est la première à ne pas recréer sa table.** `ALTER TABLE … ADD COLUMN` existe partout ; ce sont la suppression d'une colonne ([D60](#d60--un-objectif-est-calculé-ou-saisi-et-on-consulte-avant-de-corriger---validée)) et l'ajout d'une clé étrangère ([D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée)) qui l'imposaient. Les index de `food` ne sont donc pas touchés — et un test de comportement l'affirme quand même, parce que c'est la propriété qui compte, pas le chemin.

### `source_ref` range deux espaces de noms, et une seule lecture le sait

Un code de la table de l'ANSES et un code-barres vivent dans la même colonne. La requête par code-barres est la **seule** à devoir le savoir : c'est elle qui porte la clause `source <> 'CIQUAL'`.

C'est pourquoi elle a sa propre classe des deux côtés — `RoomBarcodeLookup` et `InMemoryBarcodeLookup` — plutôt qu'un septième chapeau sur un catalogue qui en porte déjà six. Le seuil de fonctions de detekt a posé la question ; la réponse n'est pas mécanique : les six autres capacités traitent `source_ref` comme opaque, celle-ci non. Une classe pour une requête nomme la couture au lieu de la cacher.

**Un aliment personnel passe devant un produit en cache** quand les deux portent le même code — on crée la fiche hors ligne, on rescanne connecté, et l'index unique porte sur le couple, donc les laisse cohabiter. Ce que l'utilisateur a saisi lui-même gagne, comme dans les résultats de recherche.

### Deux tests qui passaient sans leur règle

Le premier : « un code CIQUAL n'est pas un code-barres » comparait `13039` à `13039141`. Une égalité ne se laisse pas piéger par un préfixe — le cas passait aussi bien avec la clause que sans elle. Corrigé par une fixture **impossible aujourd'hui** : une fiche de l'ANSES dont la référence *est* le code-barres. Elle est artificielle et c'est le propos — la collision n'est empêchée que par une coïncidence de longueurs, cinq chiffres contre treize, qui n'est écrite nulle part.

Le second est le même que celui de [D63](#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée), et c'est ce qui rend la méthode plus convaincante que son résultat : sur dix règles défaites en deux tranches, deux tests n'ont pas bougé.

**Conséquences.** `FoodDao` se scinde : `FoodMarksDao` prend les récents, les favoris et l'écriture qui les alimente — les trois ports que le domaine sépare déjà. `DomainModule` se scinde une troisième fois, en `FoodUseCaseModule`. **Une dette est ouverte** : `InMemoryFoodCatalog.usageCount` lit une carte posée à la main, là où Room compte de vraies lignes de journal — le faux est donc plus indulgent que le vrai, exactement ce que [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) proscrit. La sortir du catalogue demande un port, un DAO et deux contrats retouchés ; elle mérite sa propre livraison.

---

## D65 — Le décodeur est un module à part, et sa seule règle tient sur la JVM · ✓ validée

**Contexte.** `:integration:scanner`, plus les deux pièces de domaine que l'écran de scan appellera. L'écran lui-même vient après : il n'y a pas d'émulateur ici, et une caméra ne s'éprouve pas autrement qu'en la tenant.

### ML Kit à **modèle embarqué**

`com.google.mlkit:barcode-scanning` et non `com.google.android.gms:play-services-mlkit-barcode-scanning`. Le modèle pèse quelques mégaoctets dans l'APK et fonctionne **sans les services Google** — donc sur un téléphone dégooglisé comme sur un autre, et dès le premier scan, sans réseau. La variante légère les exige, et son premier scan télécharge : les deux propriétés contredisent la tranche, qui promet un scan hors ligne dès le deuxième passage.

**À noter pour plus tard** : c'est un binaire propriétaire lié à du GPL-3.0. Sans conséquence tant que l'auteur distribue lui-même, mais c'est ce qui fermerait la porte de F-Droid. Le décodeur est isolé dans un module ; le remplacer par ZXing coûterait une classe.

### Trois symbologies, pas quatre

EAN-13, EAN-8, UPC-A. **UPC-E est retiré de la liste que [02](02-parcours-et-ecrans.md#modale--scan-de-code-barres) annonçait**, et c'est la conséquence directe de [D63](#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée) : ML Kit ne décompresse pas, huit chiffres ne disent pas de quelle symbologie ils viennent, et un UPC-E lu comme un EAN-8 donne un code **plausible désignant un autre produit**. Refuser à la source vaut mieux que compter sur la clé de contrôle, qui ne rattraperait que neuf fois sur dix.

Restreindre la liste n'est pas une économie : ML Kit rendrait volontiers un QR code ou un code de rayonnage, et un faux positif ressemble à un scan réussi.

### L'anti-rebond est sorti du décodeur, parce que c'est la seule chose qu'on puisse éprouver

Tout le reste du module — le liage CameraX, la rotation de l'image, la torche, la fermeture de l'`ImageProxy` — ne se vérifie que sur un appareil. `SteadyBarcode` est pur, et il porte les **deux** moitiés d'une règle qu'on croit simple :

1. **Deux lectures d'accord**, ce qui écarte la lecture douteuse.
2. **Puis on s'arrête**, jusqu'à ce que l'écran redemande. C'est cette moitié-là qu'on oublie, et c'est elle qui empêche la rafale : le décodeur rend une lecture par image.

**Une lecture que `Barcode` refuse ne compte pas et casse la suite.** Elle n'est pas « rien » : deux codes valides séparés par une lecture fausse n'ont pas été lus consécutivement, et les accepter reviendrait à valider un accord que l'optique n'a pas donné.

**La reprise efface la mémoire, pas seulement le verrou.** Sans cela, la première image qui suit une reprise confirmerait le code déjà traité, et l'écran rouvrirait une fiche sans qu'on ait rien visé.

### Une composable dans un `:integration`, et c'est le seul cas

`BarcodeCamera` vit ici et non dans un `:feature`. Une caméra n'est pas une source de données qu'on puisse mettre derrière un port : c'est une **surface**, et l'abstraire demanderait au domaine de connaître un type de vue — exactement ce que l'architecture lui interdit. Le module fournit donc la surface et le décodage ; l'écran qui viendra garde la permission, les états et la navigation.

**Écarté.** *Mettre l'écran entier ici* : le module deviendrait un `:feature` déguisé, avec ses chaînes et sa navigation. *Un port qui rendrait un `Flow<Barcode>` sans surface* : il faudrait quand même une vue pour l'aperçu, et elle traverserait la frontière autrement.

### Un cinquième `DraftOrigin`, et le `ViewModel` n'a pas bougé

`DraftOrigin.Scanned` porte la même charge que `New` — un identifiant de fiche — et n'est pas la même chose : un plat scanné porte la source `BARCODE`, et cette origine ne se réécrit jamais ([D32](#d32--la-source-appartient-au-plat-et-ne-change-jamais---validée)). Les confondre effacerait la seule trace de la façon dont le plat est entré dans le journal.

C'est la vérification que [D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée) annonçait sans pouvoir la faire : **une entrée de plus, et rien à toucher dans l'écran de validation**. Le point de convergence que [12](12-plan-de-developpement.md) désigne depuis la conception a tenu son premier vrai passage.

`Scanned` rend `null` quand la fiche a disparu, là où `New` rendrait un brouillon vierge : une saisie neuve reste utile sans fiche — on tape à la main — alors qu'un brouillon de scan sans son produit porterait une pastille « code-barres » sur un plat que personne n'a scanné.

### Un aliment personnel garde son code-barres

`CustomFoodDraft.barcode` devient la `source_ref` de la fiche. C'est ce qui fait tenir le dernier critère de la tranche : le produit absent d'Open Food Facts cesse d'être un cas particulier après **une seule** saisie, puisque le catalogue le retrouve ensuite par son code, sans réseau. Un `Barcode` et non une chaîne — le code doit être le même que celui que le prochain scan présentera.

**Conséquences.** Le module déclare `CAMERA` et `uses-feature required="false"` : les trois autres modes de saisie n'ont pas besoin d'objectif, et sans cette ligne la déclaration de permission écarterait l'application des tablettes qui n'en ont pas. Quatre règles ont été défaites, sept cas sont tombés, et les deux qui n'ont pas bougé sont les moitiés « témoin » de leurs paires — une fiche sans code-barres n'en gagne pas, une saisie neuve reste manuelle.

---

## D66 — La modale de scan, et les trois modes de saisie réunis dans le graphe · ✓ validée

**Contexte.** Ce qui manquait pour que « Je scanne » existe : un écran. Il est presque entièrement invérifiable ici — pas d'émulateur, pas de caméra — et c'est ce constat qui a décidé de sa forme.

### Ce qui pouvait être un `ViewModel` en est un, et le reste est mince

`ScanViewModel` ne connaît ni la caméra ni le réseau : il reçoit un `Barcode` déjà confirmé par l'anti-rebond et le passe à `LookupBarcode`, qui décide seul de l'ordre ([D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée)). Quatre états, six cas de test sur la JVM. Tout ce qui reste dans la composable — l'aperçu, la permission, la lampe, le retour haptique — n'a aucun équivalent hors appareil, et il n'y a donc **rien** à y mettre qui puisse s'y cacher.

**La permission n'est pas dans le `ViewModel`.** C'est un état de l'appareil ; l'y faire entrer demanderait un `Context`, donc ferait perdre la seule chose que cet écran ait de vérifiable.

**La garde « une lecture à la fois » est tenue deux fois** — dans `SteadyBarcode` et dans le `ViewModel`. Ce n'est pas une redite : la première appartient au décodeur et disparaîtrait avec lui, la seconde appartient à l'écran. Deux codes lus coup sur coup ouvriraient sinon deux fiches.

**Un catalogue illisible aboutit à `Unreachable`.** Ce n'est pas exact — une base qui refuse de se lire n'est pas un réseau absent — mais le geste utile est le même : créer la fiche à la main. Sans ce repli, l'exception remonterait et l'écran resterait bloqué sur « Recherche… ».

### La permission est demandée à l'ouverture, sans écran d'explication devant

L'écran n'a **aucun** contenu sans caméra. Faire tapoter une explication avant la seule question qui compte serait un écran de transit de plus — le raisonnement de [D56](#d56--lonboarding-ouvre-lapplication-exige-ses-réponses-et-dit-non-à-voix-haute---validée) et [D59](#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60), appliqué une troisième fois. L'explication apparaît **après** un refus, avec le seul chemin qui reste : les réglages système, puisque Android ne rouvre pas sa boîte une fois la permission refusée deux fois.

### Le graphe se scinde sur ce que `docs/02` pose en tête

`captureScreens` regroupe validation, scan et recherche. Ils partagent une règle et une seule : **ils s'effacent derrière la validation**. Revenir en arrière depuis un plat en cours doit rendre l'accueil, jamais l'aperçu caméra ou la liste de résultats qu'on vient de quitter.

Le seuil de longueur de detekt a posé la question ; la réponse n'est pas mécanique. Écrits au milieu des autres destinations, les trois `popBackStack` se lisaient comme trois précautions séparées. Regroupés, ils sont la traduction en graphe de la règle structurante de [02](02-parcours-et-ecrans.md) — les modes de saisie convergent sur un seul écran — et le quatrième s'y ajoutera sans que le reste bouge.

### Le code lu voyage jusqu'au formulaire

C'est ce qui fait tenir le dernier critère de la tranche. `CustomFoodDestination` gagne un argument, et le champ **s'affiche sans s'éditer** : ce code n'a pas été tapé, il a été lu. Le rendre modifiable ferait porter à la fiche un code qui n'est pas celui de l'emballage qu'on a devant soi, et le prochain scan ne la retrouverait pas.

### Un glyphe de code-barres, dessiné

`material-icons-core` n'en a pas — le même manque que `StarBorder` en [D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée), et la même réponse : tracer plutôt qu'ajouter la bibliothèque étendue pour un seul glyphe, ou en détourner un qui veut dire autre chose. Des barres de largeurs inégales, parce que cinq traits réguliers se lisent comme un menu.

**Le bouton est le troisième, pas le premier.** « Ajouter » reste le geste principal : la recherche porte aussi la saisie manuelle, donc couvre tout ce qui n'a pas de code-barres. Toujours pas l'arc de quatre actions de [02](02-parcours-et-ecrans.md) — les deux modes d'IA n'existent pas, et un bouton qui n'ouvre rien n'est pas une avance.

### Un troisième test qui passait sans sa règle, et ce qu'il a révélé

« Un catalogue illisible aboutit à la même porte de sortie » ne mesurait rien : `InMemoryBarcodeLookup` lisait la réserve du faux **sans regarder son drapeau d'échec**, donc le cas tombait sur un catalogue simplement vide, et c'est la source distante qui répondait à sa place. Le repli neutralisé, le test restait vert.

C'est la troisième occurrence de cette forme dans la tranche, après [D63](#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée) et [D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée), et la cause est chaque fois la même : **le faux était plus indulgent que le vrai** — le diagnostic de [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée), qui se vérifie encore. `RoomBarcodeLookup` échoue sur une base qu'on ne peut pas ouvrir ; son pendant en mémoire ne le pouvait pas. Il le peut désormais.

**Conséquences.** `:feature:scan` déclare `implementation(projects.integration.scanner)`, la seule dépendance d'un `:feature` vers un `:integration` du projet. Elle est écrite dans son `build.gradle.kts` pour se faire remarquer en revue, et sa raison est en [D65](#d65--le-décodeur-est-un-module-à-part-et-sa-seule-règle-tient-sur-la-jvm---validée). **La tranche 5 est fonctionnellement complète**, sauf la suggestion « Chercher dans Open Food Facts » de [D50](#d50--ce-que-la-tranche-3-ne-construit-pas---validée), qui est une recherche **par nom** — un second point d'appel et une seconde liste de résultats, donc sa propre livraison.

---

## D67 — La recherche par nom se demande, et la date appartient à celui qui récupère · ✓ validée

**Contexte.** La dernière dette de [D50](#d50--ce-que-la-tranche-3-ne-construit-pas---validée) : la suggestion « Chercher dans Open Food Facts » en dernière ligne de résultats. Elle voisine le scan dans cette liste et n'a rien de commun avec lui — c'est une recherche **par nom**, donc un second point d'appel et une seconde liste.

### Elle part sur un tap, jamais à la frappe

La recherche locale part 120 ms après la dernière touche parce qu'elle coûte une lecture SQLite ([D23](#d23--recherche-dès-le-2ᵉ-caractère-après-une-pause-de-frappe---validée)). Celle-ci coûte un aller-retour réseau : huit requêtes pour taper « chocolat » seraient huit de trop, et Open Food Facts a une limite de courtoisie qu'on n'a aucune raison d'approcher.

Le seuil de deux caractères reste, lui : un caractère ne désigne rien, et le service rendrait des centaines de produits au hasard.

### La ligne est offerte même hors ligne

**Écart avec [02](02-parcours-et-ecrans.md#modale--recherche)**, qui la conditionne à « si le réseau est disponible ». Un test de connectivité ment — un portail captif se déclare connecté — et une ligne qui disparaît sans raison visible déroute plus qu'une phrase qui dit « pas de connexion ». La ligne reste, et l'issue parle.

**Aucun retrait exponentiel** non plus, contrairement au code-barres : la recherche part d'un tap délibéré et l'écran montre déjà son résultat. Faire attendre une seconde et demie avant de dire « pas de connexion » ne gagnerait rien.

### `RemoteSearch` vit à côté de `SearchUiState`, pas dedans

Elle se pose **par-dessus** les résultats locaux plutôt qu'à leur place. La faire entrer dans l'état d'écran obligerait `Results` et `Empty` à la porter toutes les deux, et les trois autres variantes à expliquer pourquoi elles ne l'ont pas. C'est le même arrangement que `query` et `filter`, et pour la même raison — le `ViewModel` le disait déjà de son bandeau de pastilles.

**Quatre états et non trois** : « offerte » et « rien trouvé » ne disent pas la même chose, et les confondre ferait reproposer une recherche qu'on vient de faire. Une liste vide **est** une réponse.

**Elle repart à zéro dès que la requête change**, sans quoi des produits d'un mot qu'on vient d'effacer resteraient affichés sous la liste locale du mot suivant.

### Un produit dont le code n'est pas lisible est écarté

Sans code canonique ([D63](#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée)), une fiche ne peut ni être mise en cache sans doublon, ni être retrouvée par un scan : elle serait une ligne qu'on ne peut choisir qu'une fois, et qui reviendrait du réseau à chaque recherche. La liste en perd quelques-unes ; elle n'en garde aucune qu'on ne puisse rejouer.

### La date de récupération change de main, et c'est le second appelant qui l'a montré

[D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée) la posait dans `LookupBarcode`. Un second chemin de récupération est apparu — la recherche par nom — et il ne passe pas par ce cas d'usage : les fiches qu'il rend seraient entrées au catalogue **sans âge**, et le rafraîchissement de la tranche 6 n'aurait rien à comparer.

Elle est donc posée par le module qui interroge le service, qui est le seul à savoir quand il l'a fait. La règle cesse d'être une chose dont chaque appelant doit se souvenir.

C'est aussi ce que `docs/06` appelle un signal : une propriété que deux chemins doivent tenir séparément appartient à ce qu'ils ont en commun.

**Conséquences.** `OpenFoodFactsProducts` porte les deux ports — même service, même client, et la séparation sert les appelants, pas le nombre d'objets. `cgi/search.pl` et non `api/v2/search` : le second filtre sur des étiquettes et n'accepte pas de texte libre. Deux seuils de detekt ont forcé deux découpages, et les deux sont des gains : la suggestion a son fichier — c'est une **source** de plus, pas un morceau de l'écran de recherche — et le constructeur de flux de résultats sort du `ViewModel`, parce que c'est une façon de construire un flux et non une chose que l'écran déclenche. **La tranche 5 est complète.**

---

## D68 — Un voile est une surface, et il reste sombre dans les deux thèmes · ✓ validée

**Contexte.** Constaté sur appareil : la surimpression du scan affiche du **texte noir sur un voile sombre**. Le premier écran du projet posé sur une image qu'on ne maîtrise pas est aussi le premier à découvrir que Material 3 ne devine pas la couleur de l'encre.

### La cause est qu'un voile avait été peint, pas posé

`Overlay` et `Hint` teintaient le fond avec `Modifier.background(surface.copy(alpha = …))`. Un `background` peint des pixels et rien d'autre : il ne pose pas `LocalContentColor`, qui reste alors au noir par défaut. Une `Surface` le pose — c'est ce qu'elle est, un fond **et** son encre.

`Hint` échappait au défaut parce qu'il fixait sa couleur à la main, ce qui est exactement le contournement qui empêche de voir le problème : il corrigeait sa propre ligne et laissait les voisines fausses.

**Le composant est donc dans `:core:designsystem`, et il n'y a pas de jeton public.** Un `NeonTheme.scrim` qu'on irait peindre soi-même laisserait la prochaine surimpression refaire la même erreur — deux décisions séparées dont l'une s'oublie. `ScrimSurface` les lie : on ne peut pas obtenir le fond sans l'encre.

**Écarté.** *Poser `LocalContentColor` à la racine de l'écran de scan* : ça soigne un écran et laisse le suivant, et ça ne dit nulle part pourquoi.

### Le voile est sombre en thème clair aussi, et c'est du contraste, pas du goût

Une image de caméra n'est pas un fond dont on hérite : le panneau apporte le sien. Le suivre le thème donnerait, en thème clair, un voile blanc — et [08](08-design-system.md#contraste) veut que le néon soit toujours l'élément **clair** de la paire.

Le chiffre tranche. Le thème clair assombrit les macros de 25 % « pour préserver le contraste sur fond blanc » : le cyan des calories devient `#00ABBF`, qui tient **2,8:1** sur du blanc — sous le AA de 4,5:1, et même sous le 3:1 du grand texte. Or c'est la teinte des libellés de `TextButton`, du `NeonButton` et de l'indicateur de progression. Sur le voile sombre, la même teinte en tient **6,7:1**, dans les deux thèmes.

**Cela révèle une lacune plus large de [08](08-design-system.md#thème-clair), qui n'est pas corrigée ici** : la phrase sur les 25 % est fausse dès qu'une teinte néon porte du texte sur un fond clair, ce qui arrive sur tous les écrans. Le voile la contourne là où elle se voyait le plus ; la corriger pour de bon demande de choisir des teintes de texte propres au thème clair, donc une décision sur la palette et non sur un écran. Elle est écrite dans [08](08-design-system.md#thème-clair) pour ne pas être redécouverte.

### L'opacité passe de 0,85 à 0,92, et ce n'est pas une affaire de contraste

À 0,85, sur le pire fond possible — un aperçu blanc — le texte tenait déjà 10:1. Ce qui gêne n'est pas la clarté du fond mais son **bruit** : la texture d'une image de caméra passe sous les lettres. Ce qu'on gagne à laisser deviner l'image sous un panneau de trois lignes ne vaut pas ce qu'on perd à lire par-dessus un rayon de supermarché — d'autant que l'image reste entière au-dessus du panneau.

La valeur quitte le `:feature` : c'était une valeur de style hors du design system, catégorie que l'outillage ne vérifie pas ([10](10-qualite-et-livraison.md#analyse-statique)).

### Le bouton « Fermer » était le seul sans rien sous lui

Il flotte en haut à gauche, directement sur l'aperçu, et son libellé est cyan. Il reçoit son propre voile en pastille. Sur l'écran de permission refusée, en revanche, il n'y a pas de caméra derrière : le voile y serait une pastille posée sur rien, et il en est retiré.

**Conséquences.** Rien de tout cela ne s'éprouve par un test, et rien n'a été ajouté qui le prétende : la vérification est sur appareil, dans les deux thèmes. `ScrimSurface` expose un aperçu sur aplat blanc — sur le fond de l'application, un voile sombre sur du sombre ne prouverait rien. Le composant est le point d'accroche de l'écran photo de la tranche 7, qui pose la même surimpression sur la même sorte d'image.

---

## D69 — L'aperçu se fige sur la trame qui a porté la lecture · ✓ validée

**Contexte.** Constaté à l'usage : quand un code est lu, l'aperçu continue de tourner sous la surimpression. On ne sait donc pas *ce que* l'appareil a lu, et quand la lecture n'aboutit à rien — produit inconnu, service injoignable — rien ne permet de juger si le cadrage était en cause. L'écran affiche un code et une caméra qui filme déjà autre chose.

### Figer n'est pas couper, et c'est ce qui lève l'objection de [D66](#d66--la-modale-de-scan-et-les-trois-modes-de-saisie-réunis-dans-le-graphe---validée)

L'aperçu restait lié dans tous les états parce que l'éteindre ferait croire que la caméra a lâché — et [02](02-parcours-et-ecrans.md#modale--scan-de-code-barres) veut un chargement inline, pas un dialogue qui masque. L'argument tenait contre un rectangle noir ; il ne tient pas contre une image immobile. La trame reste, elle cesse de bouger, et elle en dit plus que l'aperçu vivant n'en disait.

### La trame se capture à la confirmation, et là seulement

`SteadyBarcode` ne rend un code qu'à la seconde lecture d'accord, puis se tait. Cette ligne s'exécute donc une fois par scan, sur l'image même qui a porté l'accord. Capturer plus tôt — à chaque image — coûterait une conversion trente fois par seconde ; plus tard, l'`ImageProxy` est déjà refermé, puisqu'il **doit** l'être pour ne pas bloquer le flux.

**CameraX ne redresse pas la trame d'analyse.** `toBitmap()` rend les pixels du capteur ; `imageInfo.rotationDegrees` s'applique à la main, sans quoi l'image figée est couchée alors que celle qu'on vient de quitter ne l'était pas — l'écran se mettrait à mentir sur ce qu'il a lu, exactement le contraire du but.

### Le `Bitmap` appartient à la surface qui l'a produit

La réponse évidente — le mettre dans `ScanUiState` — coûterait la seule chose que cet écran ait de vérifiable : `ScanViewModel` cesserait d'être éprouvable sur la JVM, ce que [D66](#d66--la-modale-de-scan-et-les-trois-modes-de-saisie-réunis-dans-le-graphe---validée) avait acheté en gardant la permission dehors. Il vit donc dans `CameraSession`, à l'intérieur de `:integration:scanner`. L'écran ne sait pas qu'une trame existe, et `:domain` encore moins.

**Écarté.** *Le tenir dans le `:feature`, sous la composable* : le module deviendrait responsable d'un objet dont il ne sait ni quand il naît ni quand il meurt. *Le rendre survivant à une rotation*, par `rememberSaveable` ou par le `ViewModel` : plusieurs mégaoctets dans l'état sauvé, pour une image qui n'a de sens que le temps d'une issue.

### Une seule règle gouverne la caméra : elle tourne tant que rien n'est figé

Deux signaux qui diraient la même chose — l'état de l'écran et la confirmation du décodeur — finiraient par ne plus être d'accord, et la caméra resterait allumée derrière une issue ou éteinte devant un viseur. La reprise reste ce que [D66](#d66--la-modale-de-scan-et-les-trois-modes-de-saisie-réunis-dans-le-graphe---validée) avait posé : `resumeKey`, un compteur et non un booléen, parce que rescanner le **même** produit doit remarcher. Ouvrir et rouvrir sont désormais littéralement le même chemin.

**Sans trame, on ne fige rien.** Une conversion peut échouer ; l'écran retombe alors sur le comportement d'avant, aperçu qui tourne. Délier la caméra sans avoir d'image à mettre à la place donnerait un rectangle noir, la seule chose pire que l'aperçu qui bouge.

### Ce que ça coûte, écrit ici pour ne pas être découvert

**Une rotation d'écran perd la trame et rallume l'aperçu.** L'activité est recréée, la composable avec elle, tandis que l'état de recherche survit dans le `ViewModel` : on se retrouve avec une caméra vivante derrière un « Produit inconnu ». L'écran reste utilisable et « Scanner à nouveau » repart normalement. Le corriger demanderait soit de faire survivre le `Bitmap`, soit de donner à la caméra un second maître — les deux choses que cette décision refuse. C'est un geste rare sur un écran qu'on tient contre un emballage.

**Conséquences.** La trame est réduite à 720 px sur le côté long, ce qui la plafonne à un mégaoctet et demi ; c'est un budget de pixels et non une valeur de style, il vit donc dans le module et non dans `:core:designsystem`, comme les 1024 px de l'image envoyée à un modèle vivent dans [02](02-parcours-et-ecrans.md#écran-dia). L'écouteur de ML Kit s'exécute sur le fil principal faute d'exécuteur donné, et c'est ce qui rend légal d'y délier la caméra. `BarcodeAnalyzer` gagne un `close()` : le client natif est maintenant retenu pour toute la vie de l'écran, le refermer est la contrepartie. **Une règle nouvelle s'éprouve sur la JVM** — la réduction, dans `frameScale` — et un quatrième cas a été **retiré** : « une trame exactement à la borne » ne bougeait sous aucune des deux règles défaites, parce que 720 ⁄ 720 vaut 1 avec ou sans la garde. Un test qui ne tombe jamais n'est pas une sécurité.

---

## D70 — Contribuer à Open Food Facts entre en tranche 6, parce que la couverture n'est pas la même partout · ✓ validée

**Contexte.** Mesuré sur l'API : Open Food Facts compte **1 257 548 produits en France** contre **10 911 en Thaïlande** — États-Unis 950 725, Allemagne 420 711, Japon 42 808, Indonésie 8 616, Viêt Nam 1 545. La base est collaborative : sa couverture suit les contributeurs, pas les marchés.

### Le chiffre déplace le repli, pas la fonctionnalité

La décision par défaut n° 13 classait la contribution hors v1, et [01](01-perimetre.md#hors-v1) la range dans « excellente idée, vrai travail ». Les deux raisonnaient sur un utilisateur pour qui le produit absent est l'exception. Pour un utilisateur hors d'Europe, **c'est le cas courant** : le chemin de repli — créer la fiche à la main en gardant son code-barres — devient la route principale, et chaque saisie reste alors sur un seul téléphone. C'est la même quantité de travail humain, dépensée une fois par personne au lieu d'une fois pour toutes.

`FoodContributionTarget` était déjà défini sans implémentation ([04](04-sources-de-donnees.md#produit-absent)) : ce qui change ici n'est pas la conception, c'est l'échéance.

### Tranche 6 et non plus tard, parce que c'est là que l'écran existe

C'est l'argument décisif, et il n'est pas de priorité mais de lieu. La tranche 6 apporte le `NutritionResolver`, donc `density`, donc la première fois qu'une fiche Open Food Facts a **un écran où vivre** — le même écran d'où un bouton « contribuer » a un sens. [D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée) avait déjà noté que `user_edited_fields` attendait ce même écran. Programmer la contribution en tranche 7 ou 8 la séparerait de la seule surface qui la rend offrable.

**Écarté.** *L'acter maintenant et la livrer plus tard* : une chose repoussée avec un argument fort se repousse deux fois, et la tranche 6 est celle qui construit son point d'accroche. *La laisser hors v1* : le constat de couverture ne se dément pas, et le refus reviendrait à décider que l'application est faite pour l'Europe.

### Ce qui reste ouvert, et qui se tranchera dans la tranche

Trois questions, écrites ici pour ne pas être improvisées :

- **Compte ou anonyme.** Open Food Facts accepte les deux ; une contribution anonyme est acceptée mais signalée, et ses contributeurs la relisent. Un compte demande un identifiant à saisir dans les réglages, donc un secret de plus à ranger — `EncryptedSharedPreferences` arrive de toute façon dans cette tranche pour les clés d'IA.
- **Ce qu'on envoie.** Une fiche saisie à la main porte un nom, un code-barres et six valeurs. Ce n'est pas une fiche Open Food Facts complète, et envoyer du partiel est un choix à assumer plutôt qu'un défaut.
- **Le consentement.** Rien ne part sans un geste explicite. C'est une écriture sortante, la première de l'application, et [01](01-perimetre.md#contraintes-fermes) n'en prévoit aucune.

**Conséquences.** [12](12-plan-de-developpement.md#tranche-6---je-photographie-ou-je-décris-) gagne la contribution dans son contenu et un critère de fin. La décision par défaut n° 13 est **remplacée par celle-ci** et sa ligne le dit. [04](04-sources-de-donnees.md#produit-absent) cesse d'écrire que l'interface n'aura aucune implémentation. Rien n'est construit à ce stade : c'est une décision d'échéance, prise pour que la tranche 6 la trouve écrite au lieu de la reposer.

---

## D71 — Le compte des citations quitte le catalogue, parce qu'un faux ne peut pas l'inventer · ✓ validée

**Contexte.** La dette ouverte en [D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée), et le dernier endroit du dépôt où le faux s'autorisait à être plus indulgent que le vrai. `FoodStore.usageCount` comptait de vraies lignes de journal côté Room ; côté faux, il lisait `InMemoryFoodCatalog.usages`, une carte qu'un test posait à la main. La forme exacte que [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) proscrit, et par laquelle quatre défauts sont passés.

### Ce n'était pas un mauvais faux, c'était un port mal placé

Un catalogue ne connaît pas le journal. Tant que le compte vivait sur `FoodStore`, **aucun faux honnête n'était possible** : la seule source dont `InMemoryFoodCatalog` dispose est sa réserve de fiches, où le nombre de citations n'existe pas. Son propre KDoc l'admettait.

C'est le diagnostic qui compte, et il se généralise : **un port qui ne peut pas être doublé honnêtement est un port mal placé.** La correction n'est donc pas d'améliorer le faux mais de déplacer le port là où la donnée se dérive.

`FoodCitations` est un port du catalogue dont l'adaptateur vit dans `:data:diary` — la seule inversion de ce genre du projet, et elle est écrite dans [06](06-architecture.md#i--ségrégation-des-interfaces) pour ne pas passer pour un rangement approximatif.

### Ce que le déplacement a rendu **énonçable**

Avant, `usageCount` n'était couvert par **aucun** contrat, et il ne pouvait pas l'être : `FoodCatalogContract` n'a aucun moyen d'écrire dans le journal. La propriété qui compte — *noter un plat fait monter le compte* — n'était écrivable nulle part.

Adossé au journal, il rejoint `DiaryContract` et cette phrase devient un cas, joué sur les deux implémentations côte à côte. C'est le gain réel : pas un faux plus fidèle, une propriété qui existe.

**Écarté.** *Donner au faux du catalogue une référence vers le faux du journal* : le vrai catalogue n'en a pas, et la dépendance n'aurait existé que dans le double — c'est-à-dire encore une asymétrie, mieux cachée. *Laisser le compte sur `FoodStore` et lui écrire un contrat à part* : il aurait fallu que ce contrat sache écrire des entrées, donc dépende du journal, donc soit le contrat du journal sous un autre nom.

### Un DAO d'une seule requête, et le seuil n'y est pour rien

La requête interroge `food_entry` pour répondre d'une fiche : elle n'appartient franchement ni à `FoodDao` ni à `DiaryDao`. Le seuil de fonctions a posé la question — `DiaryDao` est à dix, un de plus échoue — mais la réponse ne vient pas de lui : une classe pour une requête **nomme la couture** au lieu de la cacher, comme `RoomBarcodeLookup` pour `source_ref` en [D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée).

### Une asymétrie qui reste, et qu'on écrit

`food_entry.food_id` porte une clé étrangère : Room refuse une citation vers une fiche absente, le faux l'accepte. Le contrat contourne en faisant exister la fiche avant le plat, ce qui est ce que l'application fait de toute façon ([D50](#d50--ce-que-la-tranche-3-ne-construit-pas---validée)). **Le faux ne tient donc pas cette contrainte-là**, et il ne le peut pas sans se donner un catalogue. C'est écrit ici plutôt que découvert : aucun cas de contrat n'exerçait `foodId` avant celui-ci, et la clé étrangère n'était vérifiée nulle part.

### Cinq cas écrits, quatre gardés

Les cinq règles ont été défaites une à une. Chaque cas gardé tombe sous une défaite qui lui est propre : *noter un plat fait monter le compte* et *deux lignes comptent deux* sous un compteur aveugle au journal — l'ancien faux, exactement — et sous la confusion avec `food.use_count`, qui existe et porte presque le même nom ; *une ligne qui cite une autre fiche ne compte pas* sous un compteur qui ne filtre plus ; *supprimer le plat fait redescendre le compte* sous un compte mémorisé au premier appel, et sous elle seule.

**« Une fiche que rien ne cite compte zéro » n'a bougé sous aucune des cinq**, et il est retiré. C'est le deuxième cas retiré pour cette raison en deux livraisons ; un test qui ne tombe jamais n'est pas une sécurité, c'est une ligne verte de plus.

Une défaite n'a même pas compilé : Room refuse une requête dont le paramètre nommé ne sert pas. La couverture ne vient pas toujours d'un test.

**Conséquences.** `FoodStore` perd une fonction et redevient ce que son nom annonce — l'écriture du catalogue. `SearchViewModel` prend un port de plus et son test un **bouchon**, ce qui est la bonne division : le `ViewModel` doit prouver qu'il demande le compte et le transmet, le contrat prouve que ce compte suit le journal. `InMemoryFoodCatalog.usages` disparaît. `HexavoreDatabase` gagne un septième DAO, sans changer de version : aucune table ne bouge, la requête déménage.

---

## D72 — Le contrat de reconnaissance, et un parseur qui ne croit pas le modèle sur parole · ✓ validée

**Contexte.** Le premier tiers de la tranche 6 : le port que les six fournisseurs implémenteront, et le parseur qu'ils partageront. Aucun réseau, aucun écran — c'est la partie qui s'éprouve entièrement sur la JVM, et [10](10-qualite-et-livraison.md#ce-qui-doit-être-couvert-sans-exception) en écrivait déjà les cas obligatoires avant qu'une ligne n'existe.

### Trois écarts avec [05](05-ia.md), dont deux que le code d'aujourd'hui imposait

`docs/05` a été écrit avant les tranches 1 à 5. Trois de ses signatures ne survivent pas au contact.

**`EstimatedUnit` et non `QuantityUnit`.** Le journal a déjà un `QuantityUnit` depuis [D42](#d42--une-ligne-de-brouillon-porte-des-valeurs-absolues---par-défaut) : un type fermé qui **porte un poids en grammes**, pour qu'une ligne de l'an dernier reste relisible sans sa fiche. Celui du modèle n'en porte aucun — « un bol » ne pèse rien tant que le résolveur n'a pas décidé ce que pèse un bol. Deux types de même nom auraient fini par se rencontrer dans une signature, et le compilateur n'aurait rien dit d'utile. Le second s'appelle donc `EstimatedUnit`, ce que son rôle décrit mieux : c'est un **vocabulaire d'estimation**, et le convertir est le travail de [04](04-sources-de-donnees.md#conversion-des-quantités).

**`RecognitionOutcome` et non `Result<Recognition>`.** `kotlin.Result` exige une `Throwable` en échec ; `AiError` aurait dû devenir une exception. Or ces issues sont **attendues** — un quota épuisé n'est pas un accident de programmation — et [06](06-architecture.md) § L interdit qu'un port en lève une. C'est exactement le raisonnement de [D63](#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée) pour `ProductSource`, et la deuxième fois qu'il tranche de la même façon : la forme est donc celle de `ProductLookup`.

**`Photo` n'est pas une `data class`.** Une `data class` qui porte un `ByteArray` fabrique une égalité fausse — elle compare les références du tableau, donc deux photos identiques ne sont jamais égales et une même photo l'est toujours. Personne n'a besoin de comparer des photos ; personne ne doit croire qu'il le peut.

### Un neuvième cas d'erreur : « rien reconnu » n'est pas « illisible »

`docs/05` promet « jamais de liste vide silencieuse » sans dire ce qui arrive à la place. `Unparseable` aurait menti : la réponse était parfaitement lisible, elle ne contenait rien. Et les deux n'invitent pas au même geste — une réponse illisible se réessaie telle quelle, une assiette que le modèle n'a pas su lire se rephotographie ou se décrit.

C'est la troisième fois que le projet sépare deux issues qu'un seul cas confondrait, après `Unknown`/`Unreachable` en [D63](#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée) et les quatre états de la recherche distante en [D67](#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée). La règle se répète assez pour être nommée : **deux issues qui appellent des gestes différents sont deux cas.**

### « Le premier bloc équilibré » devient « le premier bloc qui se décode »

L'étape 1 de `docs/05` prend le premier bloc `[...]` ou `{...}` équilibré. Une phrase d'introduction qui contient une accolade — *« voici l'analyse (format {label, quantité}) : [...] »* — détourne alors la lecture vers quelque chose qui n'est pas du JSON, et une réponse valide est déclarée illisible. Les blocs sont donc essayés dans l'ordre jusqu'au premier qui rend un tableau ; la paresse d'une `Sequence` rend le coût nul dans le cas courant, où le premier est le bon.

**Le retrait des clôtures Markdown disparaît de la liste** : il ne servait à rien. Une extraction par blocs équilibrés ignore déjà tout ce qui entoure le JSON, trois accents graves compris.

**Les délimiteurs dans une chaîne ne comptent pas.** Un libellé contenant un crochet solitaire couperait sinon le tableau en deux. Ce n'est pas théorique au point d'être négligeable : c'est le genre de faute qui ne se produit qu'en production, sur un libellé qu'on n'avait pas imaginé.

### Ce que les défaites ont appris

Quatorze règles défaites, seize cas, et **tous tombent** sous au moins une défaite — pas de test à retirer cette fois. Deux résultats méritent d'être écrits :

- **Un cas ne mesurait pas ce qu'il annonçait.** « Un crochet dans un libellé ne referme pas le tableau » utilisait `pain [complet]` — un crochet **équilibré**, dont le compte revient au même qu'on regarde les chaînes ou non. Le test passait avec et sans la règle. Corrigé par un crochet solitaire, il tombe. C'est la même forme que les trois cas témoins de la tranche 5 : un test qui décrit la bonne intention avec la mauvaise donnée.
- **Une défaite n'a rien fait tomber, et c'est un renseignement.** Faire aller un bloc non refermé jusqu'au bout du texte, au lieu de le déclarer perdu, ne change aucune issue observable : le reste ne se décode pas davantage. Ce choix n'est donc pas porteur, et aucun test ne doit prétendre le tenir.

**Conséquences.** `:integration:ai` naît avec son parseur et rien d'autre — les six fournisseurs, le prompt en asset et l'intercepteur de redaction viennent ensuite. Le module est un `:integration` et non un `:data` pour la raison habituelle : on n'y décide ni le schéma ni la disponibilité. Le port vit dans `app.hexavore.domain.ai`, un dixième paquet du domaine, parce que la reconnaissance n'est ni le journal ni le catalogue — elle produit du texte que le second devra résoudre.

---

## D73 — La portion de la fiche l'emporte sur le forfait, et la densité attend son auteur · ✓ validée

**Contexte.** La charnière entre ce que le modèle estime — « un bol de céréales » — et ce que le journal enregistre, qui n'est que des grammes. C'est la première moitié du résolveur de [04](04-sources-de-donnees.md#conversion-des-quantités), et la seule qui ne dépende ni d'une clé d'API ni d'une recherche.

### Le tableau de [04](04-sources-de-donnees.md#conversion-des-quantités) se trompe d'un facteur six sur un bol de céréales

`BOWL → 250 g` y est écrit à plat. Or `servings.csv` porte déjà **« 1 bol » à 40 g** pour un aliment et **50 g** pour un autre : ce sont des céréales, et un bol de céréales ne pèse pas un quart de kilo. Le forfait n'est pas faux en général, il est faux dès que la fiche sait mieux.

La règle devient donc une seule, appliquée à toutes les unités nommées — tranche, bol, verre, cuillère à soupe, cuillère à café : **la portion nommée de la fiche gagne, le forfait est un repli.** Le tableau de `docs/04` ne réservait cette clause qu'à `PIECE` et `SLICE` ; l'étendre supprime un cas particulier au lieu d'en ajouter un.

**L'assiette n'a pas de branche**, et c'est le seul creux volontaire : une assiette n'est pas une propriété de l'aliment, donc aucune fiche ne peut la mesurer. Un test le tient — une fiche qui porte « 1 portion » ne doit pas faire croire qu'elle a mesuré une assiette.

### Les deux cuillères ne se confondent pas

« cuillère à soupe » contient « cuillère ». Une comparaison sur le seul mot commun rendrait la première portion trouvée pour les deux unités — un facteur trois sur du miel ou de l'huile. Le libellé cherché porte donc le mot entier, et un cas l'éprouve sur une fiche qui porte les deux.

La comparaison passe par `SearchText.normalise`, celle de l'index de recherche : c'est ce qui fait que « 1 cuillère à soupe » se reconnaît dans `cuillere a soupe`. Une seconde règle de normalisation aurait divergé de la première le jour où l'une apprend les ligatures et l'autre non — le raisonnement qui a placé `SearchText` dans `:domain`.

### La densité est un paramètre, pas une colonne — [D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée) s'applique à elle aussi

[D64](#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée) annonçait `density` « avec le résolveur ». Le résolveur est là, et la colonne ne vient pas — parce que la règle de D64 est *« est-ce que l'information se perd si on ne la note pas maintenant ? »*, et que **rien ne l'écrirait**. CIQUAL ne publie pas de densité, Open Food Facts pas davantage, et les trois valeurs de [04](04-sources-de-donnees.md#conversion-des-quantités) — 1,04 jus, 1,03 lait, 0,92 huile — ne se rattachent à aucune fiche sans deviner à partir du nom, c'est-à-dire sans faire exactement ce que ce projet refuse de faire en silence.

La densité est donc un **paramètre** de la conversion, nul partout aujourd'hui. Un millilitre pèse un gramme, et se signale comme une supposition. Le jour où une source arrive — un `densities.csv` sur le modèle de `servings.csv`, ou un champ d'Open Food Facts — la règle est déjà juste et la colonne naît avec son auteur.

**Écarté.** *Ajouter la colonne maintenant* : elle serait nulle sur les 3 484 lignes, et une migration ne se défait pas. *Classer par le nom* : « jus de citron » n'a pas la densité d'un jus d'orange, et une règle qui se trompe sans le dire est pire que 1,00 assumé.

### Ce que le drapeau porte

`ConvertedQuantity.guessed` tient la phrase de [04](04-sources-de-donnees.md#conversion-des-quantités) : *« toute conversion appuyée sur un défaut plutôt que sur une donnée réelle est signalée »*. Deux états suffisent, et chaque cas s'y range sans reste — une portion de fiche et une quantité d'emballage sont des données, un forfait n'en est pas une. Un forfait multiplié par une densité connue reste une supposition : c'est le 15 g qui est inventé, pas la densité.

**Conséquences.** `app.hexavore.domain.resolution` naît avec la conversion et rien d'autre ; la recherche de candidats et le repli IA suivront. Onze règles ont été défaites, quatorze cas, **tous tombent** — dont celui du bol, qui ne tient que parce que le forfait a cessé d'être une règle. Deux seuils de detekt ont forcé un découpage : le `when` des neuf unités passait la complexité cyclomatique tant que cinq branches portaient leur propre `?:`, et le type de retour a pris son fichier.

**Trois arbitrages pour la suite de la tranche, tranchés et notés ici pour ne pas être rejoués** : les deux boutons IA restent **visibles et grisés** sans clé, comme [02](02-parcours-et-ecrans.md#écran-dia) et la décision par défaut n° 19 le demandent — `NeonButtonAvailability.UNAVAILABLE` n'existe que pour ce cas ; le modèle par défaut est **`claude-opus-5`** ; et les appels passent par **Retrofit**, comme Open Food Facts, pour que les six fournisseurs partagent une seule pile et un seul intercepteur de redaction.

---

## D74 — Un seul score pour trier et pour décider, et le tri n'en bouge pas · ✓ validée

**Contexte.** La seconde moitié du résolveur commence par une question que [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) ne pose pas : les seuils de décision — 0,75 et 0,40 — s'appliquent à un score, et **aucun score n'existait**. `FoodSearch` rend une liste classée, pas notée.

### Les poids de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) étaient déjà implémentés, ailleurs

`FoodRanking`, écrit pour l'écran de recherche, porte exactement les quatre poids que l'étape « candidats » réclame : ×1,5 pour ce qu'on mange, ×1,3 pour un aliment personnel, ×1,0 pour la table de l'ANSES, ×0,8 pour un produit de marque. L'étape 2 du résolveur était donc faite sans qu'on l'ait su — mais dans `:data:food`, en `internal`, et sur une échelle qui va de 0,24 à 4,7.

**Un seul score, et non deux.** Écrire une seconde règle de ressemblance à côté aurait mis deux juges sur le même couple (nom, requête) : l'utilisateur aurait vu un candidat classé premier que le résolveur refuse, et personne n'aurait su lequel des deux avait tort. `FoodRanking` monte donc dans `:domain` et devient public — le raisonnement de `FoodFilter` en [D54](#d54--un-bandeau-de-rayons-et-deux-familles-qui-ne-se-combinent-pas-pareil---validée) : une règle de ce que l'utilisateur voit se teste sur la JVM.

### La consolidation ne coûte rien, parce que la transformation est monotone

C'était le risque annoncé : ramener le score dans `[0, 1]` en le divisant par un maximum aurait changé l'ordre affiché, donc cassé un écran livré. Il n'y a pas lieu de le prendre. `s / (s + k)` est **strictement croissante** : elle conserve l'ordre de `score` exactement, égalités comprises. Le tri ne bouge pas d'un rang, et un test le tient — les deux classements, par score et par confiance, doivent rendre la même liste.

`k = 0,6` place le seuil haut à un score brut de 1,8 : au-dessus d'une correspondance par préfixe, qui vaut 1,1 à 1,4 sur un libellé de l'ANSES, et en dessous d'une égalité de nom, qui en vaut 2,7 à 3,5. **Un nom exact suffit, un préfixe ne suffit pas seul.** Déplacer `k` déplace les deux seuils ensemble, ce qui est la bonne façon de recalibrer le jour où de vraies reconnaissances diront où ils devraient être — ils viennent de la conception et n'ont été calibrés contre rien.

### Le poids peut faire franchir le seuil, et c'est le sens du ×1,5

Arbitré plutôt que déduit : les poids entrent dans la confiance, pas seulement dans le tri. « Riz blanc, cuit » sur la requête « riz » vaut 0,69 — relecture — et 0,77 dès que l'aliment a déjà été mangé, donc automatique. Deux cas ne diffèrent que par l'usage et le montrent.

C'est la lecture littérale de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment), qui applique les poids au score des candidats et fait porter les seuils sur ce même score. Elle est aussi défendable en soi : une ressemblance moyenne sur un aliment qu'on mange vraiment vaut mieux que la même sur un inconnu. **Écarté** : *plafonner à 1,0*, qui aurait ajouté une borne qu'aucun document ne justifie, et *ne juger que le nom*, qui aurait rendu le ×1,5 décoratif.

### Trois verdicts, et une borne qui appartient au haut

`MatchVerdict` vit dans `domain.resolution` et non avec le classement : le score dit *combien*, le verdict dit *quoi en faire*. Trois issues parce que [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) en veut trois — remplir, remplir en le signalant, ne rien remplir — et que les fusionner coûterait dans les deux sens : deux à deux, on ferait passer un doute pour une certitude, ou on perdrait un candidat souvent correct.

Une confiance **égale** au seuil appartient au verdict du haut. Un cas le tient, parce qu'un `>` à la place d'un `>=` ne se voit pas en relecture.

**Conséquences.** `FoodRanking` et son test changent de module sans changer de contenu ; `RoomFoodCatalog` gagne un import. Sept règles ont été défaites et les sept cas neufs tombent, dont celui de la monotonie — c'est lui qui transforme une consolidation risquée en refonte gratuite. La normalisation des libellés et la recherche de candidats suivent : les pluriels s'y traiteront en interrogeant d'abord le libellé brut, et seulement ensuite sa forme dépluralisée, parce que l'index de l'ANSES garde ses pluriels — 32 % de ses 3 484 libellés en portent un — et qu'une dépluralisation systématique ferait perdre « haricots verts » que la requête brute trouvait.

---

## D75 — La normalisation retire les articles tout de suite, et les pluriels seulement en second recours · ✓ validée

**Contexte.** La suite immédiate de [D74](#d74--un-seul-score-pour-trier-et-pour-décider-et-le-tri-nen-bouge-pas---validée) : le score et le verdict existaient, et **rien ne les appelait**. Cette livraison est ce qui les branche — les étapes 1 à 3 de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment), depuis le libellé du modèle jusqu'à un verdict.

### L'étape 1 de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) a deux moitiés qui ne se livrent pas au même endroit

Elle les énumère d'un trait — minuscules, accents, ponctuation, pluriels, articles de tête — comme si c'était une seule passe. Le contact avec les deux index les sépare, et dans deux directions opposées.

**Les articles partent avant la première requête, et c'est mécanique.** Les deux recherches sont **conjonctives** : le catalogue local compare une sous-chaîne entière — `name_search LIKE '%du pain%'` — et la table de l'ANSES exige que *tous* les termes du `MATCH` soient présents. « du pain » ne rend donc rien, nulle part. Un article gardé n'est pas du bruit dans le classement, comme on pourrait le croire : c'est une réponse vide.

**Les pluriels, eux, ne partent qu'après un échec.** L'index de l'ANSES garde les siens — 32 % de ses 3 484 libellés en portent un, 6 % commencent par un — et dépluraliser d'emblée ferait **perdre** « haricots verts », que la requête telle qu'elle vient trouve. C'est la stratégie que [D74](#d74--un-seul-score-pour-trier-et-pour-décider-et-le-tri-nen-bouge-pas---validée) annonçait, appliquée.

[04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) est corrigé en conséquence : son étape 1 décrivait une passe unique qui n'aurait rien trouvé.

### L'ordre désarme la naïveté de la règle, et c'est le meilleur argument des deux

Une dépluralisation naïve casse plus qu'elle ne répare : « pois » devient « poi », « eaux » devient « eal », et la garde de trois lettres qui sauve « jus » ne sauve ni l'un ni l'autre.

C'est sans conséquence, et la raison est plus forte que les 32 % : **aucun de ces libellés n'atteint jamais la fonction**, parce que tous les trois rendent des résultats à la première requête. Une règle approximative placée derrière une garde qui ne s'ouvre qu'en cas d'échec ne peut dégrader que ce qui était déjà vide. C'est l'ordre qui rend la naïveté acceptable, pas la qualité de la règle — et c'est pourquoi une règle meilleure ne vaudrait pas sa dépendance.

**Écarté.** *Une vraie racinisation* — Snowball français — : une dépendance, une table de règles, et surtout un comportement qu'**aucun des deux index ne partage**. La règle de [D49](#d49--la-recherche-normalise-à-limport-pas-au-tokenizer---validée) est que la même normalisation s'applique aux deux bouts ; raciniser la requête seule la romprait au moment précis où on croirait l'améliorer.

### Le classement est refait sur la confiance, parce que le contrat ne promet pas l'autre

`FoodSearch` promet que ce que l'utilisateur mange vraiment passe devant. Il ne promet pas l'ordre de `FoodRanking`, et ses deux implémentations ne l'ont effectivement pas : la vraie trie par `FoodRanking`, le faux par usage puis longueur du nom. S'appuyer sur l'ordre reçu ferait donc du résolveur une règle **qui change avec l'implémentation** — la forme exacte de défaut que [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) proscrit, et qui n'aurait pas été visible avant l'appareil.

Le tri est stable : l'ordre du port départage encore les ex æquo, ce qui coûte zéro et garde son classement là où le nôtre ne tranche pas.

### Les alternatives sont filtrées par le seuil de la décision — arbitré

**Question posée à Charly**, parce que [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) ne la tranche pas : les trois alternatives de la zone 0,40 – 0,75 sont-elles les trois candidats suivants du classement, ou seulement ceux qui dépassent eux aussi 0,40 ? **Retenu : seulement ceux qui dépassent 0,40.** Le seuil est déjà défini comme *« en dessous, aucune correspondance »* ; proposer comme solution de rechange un candidat qu'on refuserait comme correspondance serait se contredire d'une ligne à l'autre. La contrepartie est assumée et n'est pas cachée : la liste est parfois vide, là où trois lignes à 0,15 auraient rempli l'écran.

**Le filtre s'écrit « verdict autre que `NONE` » et non « ≥ 0,40 ».** Le seuil reste ainsi dans un seul fichier, et le jour de calibrage n'aura qu'un chiffre à bouger — le même raisonnement que la `SATURATION` de [D74](#d74--un-seul-score-pour-trier-et-pour-décider-et-le-tri-nen-bouge-pas---validée), un cran plus bas.

Les alternatives n'existent **qu'en relecture**, comme [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) l'écrit : une correspondance sûre n'a pas de rechange à offrir, une ligne non résolue n'en a aucune. Dans les deux cas, l'écran de validation reste l'endroit où l'on cherche autre chose à la main.

### Un enregistrement plat, là où le projet aurait mis une hiérarchie

Trois issues qui appellent trois gestes : la règle de [D72](#d72--le-contrat-de-reconnaissance-et-un-parseur-qui-ne-croit-pas-le-modèle-sur-parole---validée) demanderait un type scellé. Il n'y en a pas, et c'est parce que **`MatchVerdict` est déjà cette énumération** ([D74](#d74--un-seul-score-pour-trier-et-pour-décider-et-le-tri-nen-bouge-pas---validée)). Un `sealed interface` à côté en ferait une seconde, et deux hiérarchies pour le même fait finissent par ne plus être d'accord — c'est l'argument du score unique, appliqué au verdict.

### Le nom ne promet pas la quatrième étape

`ResolveFoodLabel` et non le `NutritionResolver` de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) : il en tient trois étapes sur quatre, et le repli IA groupé attend d'avoir un fournisseur à appeler. Un nom qui promet quatre étapes pour trois est une documentation fausse à l'endroit le plus difficile à corriger — dans le code.

### Ce que les défaites ont appris

Dix-neuf règles défaites, vingt et un cas, **tous tombent** — aucun à retirer. Deux résultats méritent d'être écrits, et le premier vaut au-delà de cette livraison.

- **Un cas ne tombait que pour la mauvaise raison, et la première série de défaites ne pouvait pas le dire.** « La normalisation ne touche pas aux pluriels » tombait bien — mais sous *« les articles ne partent plus »*, c'est-à-dire sous la règle **voisine**. La défaite qui le tient vraiment est *« la normalisation dépluralise elle aussi »*, autrement dit la défaite du **choix central de cette livraison**, que la première série avait omise. C'est la même forme que le crochet équilibré de [D72](#d72--le-contrat-de-reconnaissance-et-un-parseur-qui-ne-croit-pas-le-modèle-sur-parole---validée), pour une cause différente : là, la donnée du test était mauvaise ; ici, c'est la liste des défaites qui l'était. **Un choix de conception qu'on ne pense pas à défaire est un choix dont on croit à tort les tests garants** — et c'est justement le choix qu'on est le moins enclin à défaire, puisqu'il paraît évident à celui qui vient de le prendre.
- **Trois cas ne tombaient sous aucune des quinze premières défaites**, et les trois se tenaient par des défaites portant non sur le corps d'une règle mais sur ses **gardes** : la liste vide traitée à part, le plafond à trois, la branche du singulier. Défaire ce qu'une fonction fait est le réflexe ; défaire ce qu'elle refuse de faire ne l'est pas.

**Conséquences.** `app.hexavore.domain.resolution` gagne la normalisation et la décision à côté de la conversion de [D73](#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée) ; le domaine compte un dix-neuvième cas d'usage. Aucun port n'est né et aucun n'a bougé : résoudre est une lecture, et `FoodSearch` suffisait — c'est ce que son KDoc annonçait depuis [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée).

**Trois choses restent ouvertes et sont écrites ici plutôt que découvertes.** Une fiche de la table de l'ANSES rendue par la résolution porte un **identifiant provisoire**, comme n'importe quel résultat de recherche : c'est `FoodStore.place` qui la rend désignable par une entrée de journal, et l'écran de validation devra l'appeler ([D51](#d51--une-seule-porte-et-la-quantité-qui-recalcule---validée)). La conversion de [D73](#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée) **n'est toujours appelée par rien** : elle a maintenant de quoi recevoir une fiche, mais c'est la construction du brouillon qui la déclenchera, donc les écrans de saisie. Et le repli IA groupé — l'étape 4 — reste entier.

---

## D76 — Trois prescriptions de docs/05 tombent au contact de l'API, et le raisonnement reste actif · ✓ validée

**Contexte.** Le premier appel réseau de la tranche 6. [D72](#d72--le-contrat-de-reconnaissance-et-un-parseur-qui-ne-croit-pas-le-modèle-sur-parole---validée) avait déjà relevé trois signatures de [05](05-ia.md) qui ne survivaient pas au code des tranches 1 à 5 ; trois de plus tombent ici, cette fois au contact de l'API elle-même. Le fournisseur livré est **Anthropic**, celui dont [D73](#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée) a fait le défaut.

### `temperature` n'est plus un réglage, c'est un `400`

`temperature`, `top_p` et `top_k` sont retirés des modèles Claude actuels : les envoyer fait échouer la requête. Les `temperature = 0.2` de [05](05-ia.md#prompt) ne sont donc pas « déconseillés », ils sont **impossibles**. La régularité qu'on cherchait par là vient maintenant du schéma de sortie, qui contraint la forme au lieu de resserrer le tirage.

**Un cas de test éprouve leur absence**, ce qui a l'air excessif jusqu'à ce qu'on voie par où le défaut reviendrait : personne n'écrirait `temperature` par distraction, mais quelqu'un qui relit [05](05-ia.md) et trouve le réglage manquant le rétablirait **de bonne foi**. Le test est là pour cette relecture-là.

### Le raisonnement reste actif, parce que l'économie a un autre levier

[05](05-ia.md#prompt) écrit « pas de raisonnement demandé — il coûterait des jetons sans améliorer une tâche de perception ». L'intention est juste et rien ici ne la conteste ; c'est le **moyen** qui a cessé d'exister sous cette forme. Le raisonnement est actif par défaut, et le levier recommandé pour dépenser moins est de baisser l'effort plutôt que de couper. Couper est le levier le plus cher et traîne deux modes d'échec documentés sur cette famille de modèles : un appel structuré rendu en texte brut, et des balises de raisonnement qui fuient dans la réponse visible.

**Ni l'un ni l'autre ne nous mordrait forcément** — on ne déclare pas d'outil, et le schéma contraint la sortie —, et l'honnêteté demande de le dire plutôt que d'invoquer un péril qu'on n'a pas mesuré. L'argument tient sans lui : l'effort au plus bas rend l'économie cherchée. Prendre un risque documenté pour zéro bénéfice n'est pas un arbitrage.

**Le niveau retenu est `low`, et il n'a été calibré contre rien.** C'est la lecture la plus proche de l'intention de [05](05-ia.md) et la moins chère, ce qui compte quand c'est l'utilisateur qui paie. Une constante nommée, comme la `SATURATION` de [D74](#d74--un-seul-score-pour-trier-et-pour-décider-et-le-tri-nen-bouge-pas---validée) : de vrais appels diront où elle devrait être.

### `max_tokens = 1024` tronquerait en silence

Le plafond couvre le raisonnement **et** la réponse ensemble. Avec un raisonnement actif, mille jetons se consomment avant que le JSON commence, et ce qui revient est un tableau coupé au milieu d'un libellé — que le parseur déclarerait illisible. C'est la forme de défaut la plus coûteuse du projet : celle qui ne se voit qu'à l'usage, sur une réponse qui a l'air d'un problème de modèle.

### La sortie structurée remplace l'outil forcé, et garde un seul parseur

[05](05-ia.md#fournisseurs) prescrivait pour Anthropic un outil forcé (`tool_choice`). L'outil rend le JSON dans le champ d'entrée d'un bloc d'appel, c'est-à-dire par un chemin que le parseur de [D72](#d72--le-contrat-de-reconnaissance-et-un-parseur-qui-ne-croit-pas-le-modèle-sur-parole---validée) ne lit pas : il aurait fallu une extraction supplémentaire pour ce seul fournisseur, puis une par famille de fournisseurs, et le parseur commun aurait cessé d'être commun.

Avec `output_config.format`, la réponse **est** du texte, et le parseur la lit sans rien savoir d'Anthropic. Le schéma ne borne pas `confidence` entre 0 et 1 — les contraintes numériques ne font pas partie du sous-ensemble accepté —, et c'est le parseur qui continue de la ramener dans l'intervalle. La garde existait avant le schéma et lui survit.

### La troisième étape d'« ajouter un fournisseur » est un `when`, pas une liaison Hilt

[05](05-ia.md#ajouter-un-fournisseur) demandait une liaison dans le module Hilt. Une carte de liaisons est plus savante et **strictement moins sûre** : y oublier un fournisseur donne un plantage à l'exécution, sur l'appareil de quelqu'un. Un `when` exhaustif sur `AiProvider` transforme le même oubli en erreur de compilation — la vérification d'exhaustivité de Kotlin fait gratuitement le travail qu'une carte demanderait de se rappeler.

C'est aussi ce qui explique que **l'énumération ne porte que les fournisseurs implémentés** et grandisse avec eux. Une entrée sans classe ne compilerait pas ; et si l'on forçait le passage, l'écran des réglages offrirait un choix que la fabrique refuserait.

### Une clé refuse de s'imprimer

`ApiKey` est une `value class` dont `toString()` rend `***`. L'intercepteur de redaction couvre le réseau ; il ne couvre pas un `Log.d(configuration.toString())`, ni le message d'une exception qui embarque la configuration, ni un rapport de plantage — c'est-à-dire les chemins par lesquels une clé fuit vraiment, parce que personne ne les a écrits exprès.

**L'intercepteur masque la description, pas la requête**, et c'est la subtilité que son nom cache : retirer l'en-tête ferait échouer l'authentification, et le symptôme ressemblerait à une clé invalide — donc à une faute de l'utilisateur.

### Ce que les défaites ont appris

Dix-neuf règles défaites, dix-huit cas, **tous tombent**. Deux résultats méritent d'être écrits.

- **Une garde de sécurité se défait dans les deux sens.** Le cas « la clé reste lisible pour le seul appelant qui en a besoin » ne tombait sous aucune des dix-sept premières défaites, et paraissait donc à retirer. La défaite qui le tient n'est pas un relâchement mais **un excès de zèle** : une redaction qui déborde sur la valeur elle-même, `val value get() = "***"`. Cela compile, cela satisfait les deux autres cas, et cela empêche toute requête d'aboutir. Une garde éprouvée seulement dans le sens du serrage finit serrée jusqu'à casser ce qu'elle protège.
- **La seule défaite qui traverse deux classes de test est celle qui confond masquer et retirer.** Retirer l'en-tête au lieu de le masquer fait tomber un cas de l'intercepteur *et* un cas du fournisseur. C'est la mesure de ce que la règle a de bilatéral : la clé doit être absente d'un endroit et présente à un autre, et un test qui ne regarde qu'un côté laisse passer la moitié des façons de se tromper.

**Conséquences.** `:integration:ai` cesse d'être un parseur seul : il gagne une pile HTTP, un fournisseur, une fabrique, le prompt en asset et l'intercepteur de redaction. Le domaine gagne `AiProvider`, `AiConfiguration`, `ApiKey` et le port `AiSettings`. `:app` fournit le journal réseau — parce que la variante de build est une propriété de l'application, comme le `User-Agent` d'Open Food Facts.

**`AiSettings` rend `null` pour l'instant, et c'est la vérité** : rien ne permet encore de saisir une clé. Les deux boutons IA sont donc visibles et grisés, ce que [D73](#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée) demande, et toute analyse rendrait `NoProviderConfigured`. Écrire ce faux plutôt qu'un `TODO()` garde le graphe complet et l'application constructible ; le remplacement tient en une liaison.

**Rien de tout cela n'a atteint un vrai serveur.** Les cas tournent devant `MockWebServer`, ce qui éprouve le corps réellement sérialisé, les en-têtes et la traduction des codes — mais pas ce qu'Anthropic pense du corps qu'on lui envoie. Cela ne se vérifiera qu'avec l'écran de saisie des clés, faute de tout autre moyen d'en fournir une.

---

## D77 — La clé va dans le Keystore en direct, et le bouton Tester est une vraie analyse · ✓ validée

**Contexte.** L'espace où l'utilisateur renseigne ses clés, et le premier écran d'où un appel réel puisse partir. C'est aussi ce qui débloque tout ce que les quatre livraisons précédentes ont construit sans appelant.

### `EncryptedSharedPreferences` est dépréciée — quatrième prescription de [05](05-ia.md) à tomber

[05](05-ia.md#sécurité-des-clés) prescrit `EncryptedSharedPreferences`. La bibliothèque qui la porte, `androidx.security:security-crypto`, est **dépréciée depuis juin 2025** — la 1.1.0 de juillet est sa dernière —, et son avis de dépréciation renvoie explicitement à l'usage direct du Keystore d'Android.

**Arbitré par Charly : le Keystore en direct.** L'adopter aujourd'hui reviendrait à prendre une dette sur la donnée qu'on a le moins envie de migrer deux fois, et à écrire dans le même geste le code qui l'utilise et celui qui devra l'en sortir. Ce que la bibliothèque faisait tient d'ailleurs en peu de choses : une clé AES-256 en mode GCM générée dans le trousseau, et le vecteur d'initialisation préfixé au chiffré.

**Écarté.** *La garder quand même* : elle fonctionne et fonctionnera encore longtemps — mais le projet a une règle sur les dépendances qu'on adopte, et « déjà dépréciée le jour de son arrivée » ne la passe pas.

### Seule la clé est chiffrée, et un chiffré illisible se lit comme absent

Le nom du modèle et l'URL de base ne sont pas des secrets ; les chiffrer coûterait deux déchiffrements par lecture pour protéger `claude-opus-5`. Un cas fige ce partage, sans quoi une prudence bien intentionnée l'effacerait.

**La clé du trousseau disparaît pour des raisons banales** — sauvegarde restaurée sur un autre appareil, verrou d'écran retiré. Le chiffré survit alors à la clé qui l'ouvrait, et la seule lecture honnête est « il n'y a rien ici » : l'utilisateur recolle la sienne. Une chaîne vide ferait partir un appel voué au `401`, c'est-à-dire annoncerait une clé refusée là où il n'y a pas de clé.

Rien à changer côté sauvegarde : `data_extraction_rules.xml` exclut déjà **tout**, y compris `sharedpref`, dans les deux sens — nuage et transfert d'appareil à appareil.

### Le bouton Tester est une reconnaissance, pas un appel spécial

Il envoie « un verre d'eau » par le chemin exact qu'empruntera la première photo : même prompt, même schéma, même parseur, même traduction des codes. Un appel allégé écrit pour l'occasion aurait pu réussir là où l'analyse échoue — et **un bouton qui dit oui à tort est pire que pas de bouton**.

**Une réponse illisible est une réussite du sondage.** Le fournisseur a répondu, donc la clé est bonne et le modèle existe ; échouer là-dessus enverrait quelqu'un corriger une clé qui n'a rien. C'est la distinction entre « est-ce que la configuration marche » et « est-ce que cette phrase a été comprise », et elles n'ont pas la même réponse.

**Il éprouve le formulaire, jamais ce qui est enregistré.** Tester après avoir écrit reviendrait à enregistrer une clé fausse pour découvrir qu'elle est fausse. C'est ce qui fait que `AiProbe` prend la configuration en paramètre au lieu de lire les réglages, et le seul cas où l'écran a besoin d'un port que le résolveur n'utilise pas.

### Le hub de réglages naît, à l'échéance que [D59](#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) avait fixée

[D59](#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) écrivait « le hub naîtra avec la deuxième section ». Elle arrive. Les trois sections restantes de [02](02-parcours-et-ecrans.md#réglages) — Sauvegarde, Apparence, À propos — n'y figurent toujours pas, pour la raison inchangée : elles n'ouvriraient rien.

L'écran des fournisseurs est écrit **contre l'énumération** et non contre Anthropic. Le deuxième fournisseur y apparaîtra sans qu'une ligne d'affichage bouge — un cas le tient, en comptant les lignes affichées contre `AiProvider.entries`.

### Ce que les défaites ont appris

Vingt-deux règles défaites, vingt-six cas, **tous tombent**. Trois résultats méritent d'être écrits.

- **Deux gardes protégeaient la même chose, et un seul cas ne pouvait pas les distinguer.** Un état incohérent — une clé illisible pour un fournisseur encore marqué actif — est rattrapé deux fois : à la lecture des préférences, et à la dérivation de la configuration. Défaire la première ne faisait rien tomber, parce que la seconde couvrait. Ce n'est pas que la première soit inutile : sans elle, l'écran annoncerait « Utilisé » à côté d'un fournisseur sans clé. Il a fallu **observer l'état affiché et pas seulement la configuration dérivée** pour que le cas ait prise. *Quand deux gardes protègent la même chose par des chemins différents, un cas qui n'en observe qu'un ne tient que celui-là.*
- **Deux défaites n'ont fait tomber que l'implémentation en mémoire.** Oublier de retirer le fournisseur actif à l'effacement, ou activer un fournisseur non renseigné : le magasin chiffré s'en sort parce que sa lecture défensive recolle l'état avant de le rendre. Les deux honorent le contrat, par des routes différentes — l'un en n'écrivant jamais d'incohérence, l'autre en n'en relisant jamais. C'est le contrat qui rend l'asymétrie visible plutôt que subie.
- **Un cas ne disait pas tout ce qu'il annonçait.** « Au départ, aucun fournisseur n'est configuré » n'observait que l'absence d'actif, et survivait donc à un magasin qui fabriquerait une entrée vide par fournisseur — lequel afficherait « clé enregistrée » sur une installation neuve. Il fallait aussi affirmer que la carte est vide.

**Conséquences.** `:data:settings` naît — le rangement d'un secret est un problème de stockage local, pas d'adaptation à un service tiers, et le mettre dans `:integration:ai` aurait fait du module d'intégration le gardien de la clé qu'il ne fait qu'utiliser. Le domaine gagne `AiCredentials`, `ProviderCredentials`, `AiSetup` et `AiProbe` ; `AiSettings` reste la facette étroite que le résolveur voit. Le port naît **avec son contrat**, joué sur les deux implémentations — sans attendre qu'un défaut le rappelle.

`DraftTextField` gagne une transformation visuelle, pour que le champ de clé se masque sans que la valeur masquée soit ce qu'on enregistre. Le faux `AiSettings { null }` de [D76](#d76--trois-prescriptions-de-docs05-tombent-au-contact-de-lapi-et-le-raisonnement-reste-actif---validée) disparaît, remplacé par une liaison réelle.

**Ce que le vert ne prouve pas, et c'est beaucoup ici.** Le chiffrement lui-même n'est éprouvé par aucun test : le contrat tourne sur un chiffrement de pacotille, parce que le Keystore demande un appareil. `SecretCipher` est la couture qui rend ce partage explicite plutôt que subi — ce qui s'éprouve est le rangement, et le rangement ne dépend pas de l'algorithme. Que la clé soit illisible sur le disque et protégée par le matériel se vérifie sur le Fairphone, pas ici. **Aucun appel réel n'a encore atteint Anthropic** non plus : c'est précisément ce que cet écran rend possible pour la première fois.

## D78 — Le fournisseur garde la parole, parce qu'un message inventé ne se vérifie pas · ✓ validée

**Contexte.** Premier essai réel du bouton **Tester** sur le Fairphone, avec une clé Anthropic et tous les réglages par défaut : « Le service est indisponible. Réessayez plus tard. » L'application avait raison sur la forme — un code HTTP que la traduction ne connaît pas devient un message général — et ce message ne permettait de rien faire.

**Le défaut n'est pas la traduction, c'est qu'elle est infalsifiable.** Un même écran affichait la même phrase pour un modèle qui n'existe pas, un compte sans crédit, un champ refusé et une panne réelle du service. Aucune de ces quatre situations n'appelle le même geste, et trois d'entre elles ne sont pas des pannes. Anthropic, lui, répond `400` avec une phrase qui **nomme** la cause — un solde de crédits insuffisant se dit en toutes lettres —, et le code jetait cette phrase pour lui substituer la sienne, qui ne nomme rien.

Le cas est d'autant plus net que la confusion qu'il produit est réelle : **un abonnement Claude Pro n'est pas un crédit d'API.** Ce sont deux facturations distinctes, et l'une ne recharge pas l'autre. Aucun message général ne peut dire ça ; celui du fournisseur le dit.

**Ce qui remonte, et ce qui reste en bas.** `AiError.Server` gagne un `detail`, borné à 400 caractères — assez pour la phrase du fournisseur, pas assez pour une page d'erreur entière —, et l'écran des clés l'affiche **sous** le message traduit, en petit. Le message traduit reste le premier : il dit ce qu'il faut faire quand on le sait. Le détail dit ce qui s'est passé quand on ne le sait pas.

Les autres issues n'en reçoivent pas. Une clé refusée et un quota dépassé nomment déjà le geste ; leur ajouter la prose du fournisseur ne ferait qu'encombrer.

**Ce que ça n'expose pas.** Le corps d'erreur est celui du fournisseur, jamais la requête : la clé n'y figure pas. L'intercepteur de redaction continue par ailleurs de masquer les trois en-têtes secrets dans les journaux, et n'est pas concerné par ce chemin.

**Ce que le vert ne prouve pas.** Au moment où ceci s'écrit, **aucun appel réel n'a encore abouti**. L'écran dira désormais pourquoi, et c'est tout ce que cette livraison promet.

---

## D79 — Gemini entre au prix annoncé, et deux tests attrapent ce que la relecture avait laissé passer · ✓ validée

**Contexte.** Le deuxième fournisseur, écrit pendant que le premier attendait un diagnostic. [D76](#d76--trois-prescriptions-de-docs05-tombent-au-contact-de-lapi-et-le-raisonnement-reste-actif---validée) et [12](12-plan-de-developpement.md) annonçaient un prix : une classe, une entrée d'énumération, une branche dans le `when` de la fabrique. C'est la première fois que ce prix pouvait être vérifié plutôt qu'affirmé.

**Le prix tient.** Aucun écran n'a bougé, aucun cas d'usage n'a changé, aucun test existant n'a été réécrit pour l'accueillir, et le domaine ignore toujours qu'il existe plus d'un fournisseur. L'écran des clés propose Gemini sans qu'une ligne d'interface ait été touchée : il lit l'énumération.

Ce qui diffère d'Anthropic est entièrement dans l'adaptateur — le modèle voyage dans l'URL et non dans le corps, la clé dans `x-goog-api-key`, la consigne système dans un champ à part, et les blocs de contenu se distinguent par le champ présent au lieu d'un discriminant. Ce qui est partagé est tout le reste : le prompt, le schéma de réponse, le parseur, la traduction des codes HTTP, la réduction des pannes réseau et la pile HTTP entière. C'est exactement le partage que [D76](#d76--trois-prescriptions-de-docs05-tombent-au-contact-de-lapi-et-le-raisonnement-reste-actif---validée) cherchait en préférant la sortie structurée à un outil forcé.

### Le schéma devient partagé, et le paramètre qui les sépare crée son propre risque

Anthropic **exige** `additionalProperties: false` ; le sous-ensemble de schéma de Gemini ne connaît pas ce mot-clé et refuse la requête s'il le trouve. C'est la seule différence entre les deux schémas, d'où `recognitionSchema(strict)` plutôt que deux littéraux JSON à maintenir côte à côte.

Mais un booléen qui dit la seule différence est aussi un mot qu'on peut inverser sans rien casser à la compilation. **Les deux côtés reçoivent donc un cas** : Gemini affirme l'absence, Anthropic la présence. Le second n'existait pas avant — la clôture y était une constante, donc rien ne pouvait la retirer par mégarde. Rendre une chose réglable, c'est la rendre déréglable.

### `encodeDefaults` écrit aussi les `null`, et c'est un test qui l'a su avant moi

Le vrai défaut de cette livraison. Une `GeminiPart` porte du texte **ou** une image, jamais les deux, et le format se lit à quel champ est présent. Avec `encodeDefaults = true` seul, chaque part partait avec l'autre champ à `null` : une image accompagnée de `"text":null`, une description accompagnée de `"inlineData":null`. Le KDoc affirmait le contraire, en toutes lettres, et la relecture ne l'a pas vu.

`explicitNulls = false` répare la sérialisation ; c'est le cas « une description ne joint aucune donnée binaire » qui a fait tomber la faute, sur un corps de requête qu'aucun œil n'avait déplié. Ce que ça aurait coûté sans lui : au mieux du bruit dans chaque requête payante, au pire un refus dont rien n'aurait expliqué la cause — et l'on aurait cherché du côté du modèle. Ajouté aux pièges de [10](10-qualite-et-livraison.md#sérialiser-du-json).

### Deux assertions ne testaient rien, et l'une des deux avait l'air juste

`body.indexOf(PROMPT) < body.indexOf("une pomme")` prétendait vérifier que la consigne système part dans son champ. Elle vérifiait en réalité l'ordre des champs dans le JSON produit — qui est celui de la déclaration Kotlin, et n'est une propriété ni de l'API ni de la règle. L'assertion aurait tenu bon en cas de vraie régression, et serait tombée le jour où quelqu'un réordonne une `data class`. Elle énonce désormais ce qu'elle voulait dire : la consigne est **dans** `systemInstruction` et **pas dans** `contents`.

L'ordre d'un **tableau**, lui, veut dire quelque chose — le modèle lit les `parts` dans l'ordre où elles arrivent, et l'image doit précéder la consigne pour la même raison que chez Anthropic. Le cas de la photo l'affirme maintenant sur le tableau lui-même, et non sur des positions de sous-chaînes.

**Campagne de défaite : treize sabotages, treize cas tombés**, chacun celui qu'on visait. Le modèle retiré de l'URL, la clé déplacée d'en-tête, la clôture du schéma inversée des deux côtés, la consigne sortie de son champ puis ajoutée aux messages, l'arrêt non-`STOP` ignoré, le `403` banalisé, les deux détails d'erreur supprimés, la fabrique routée vers le mauvais fournisseur, l'image passée derrière la consigne, et `explicitNulls` retiré. Aucun cas n'a survécu à la destruction de sa règle.

**Conséquences.** `reducedTo` — la panne réseau réduite à une issue, écrite plutôt que tue — devient commune aux deux fournisseurs : la question « que faire d'un réseau absent » n'a pas deux réponses. `RecognitionSchema.kt` naît pour la même raison.

**Ce que le vert ne prouve pas.** **Aucun appel réel n'a atteint Gemini.** La clé fournie pour les essais n'a pas la forme d'une clé AI Studio — `AIza`, 39 caractères — mais celle d'un jeton OAuth, et le premier appel réel dira si le compte l'accepte. Les identifiants de modèles viennent de la documentation en ligne et non de mémoire, `gemini-3.5-flash-lite` par défaut ; c'est aussi ce premier appel qui dira s'ils sont accessibles à ce compte.

## D80 — La proposition passe par un dépôt, et l'écran de validation ne change pas d'un mot · ✓ validée

**Contexte.** La modale texte : le premier écran d'où un appel payant part vraiment, et la jonction de **quatre livraisons qui n'avaient aucun appelant** — le contrat de reconnaissance, la conversion des quantités, le score de décision et la recherche de candidats existaient chacun avec ses tests et n'étaient branchés à rien.

### Une route ne porte pas cinq lignes

`EntryDestination` l'avait écrit avant que le cas existe, et c'est le seul vrai obstacle de cette livraison. Les arguments de navigation sont sérialisés dans l'état du système : y faire transiter un plat entier reviendrait à en tenir une seconde copie que rien ne tiendrait à jour. Le canal de résultat qui sert à « Ajouter un aliment » ne convient pas davantage — il rend une valeur à l'écran **précédent**, alors qu'ici l'écran destinataire n'existe pas encore au moment où la réponse arrive.

D'où `PendingRecognition`, un dépôt d'**une seule** proposition. Pas de file d'attente : il n'y a jamais deux analyses en vol, l'écran attend la sienne avant d'en laisser partir une autre. La reprise **vide** le dépôt, sans quoi revenir sur la validation par le bouton « retour » ressusciterait un plat qu'on vient d'enregistrer.

**Ce qui est déposé est la réponse du modèle, pas un brouillon.** La résolution demande le catalogue, donc elle appartient à `OpenDraft`, avec les quatre autres origines ; déposer un brouillon déjà résolu aurait fait de l'écran de capture un second endroit qui sait fabriquer un plat, et le premier libellé introuvable aurait eu deux comportements possibles.

**Volatil, et assumé.** Une proposition ne survit pas à la mort du processus : ce qui a été payé au fournisseur est perdu, et l'écran dit qu'il n'y a rien à valider. La persister demanderait de décider où, combien de temps, et sous quelle forme une proposition périmée se relit — trois questions qu'aucun usage ne pose.

### Une cinquième origine, et pas un cinquième écran

L'écran de validation n'a pas bougé. Il reçoit un `EntryDraft` comme il en reçoit depuis la tranche 2, et `DraftOrigin` gagne une variante — la seule **sans charge**, puisque la charge est ailleurs. C'est ce que `OpenDraft` promettait en naissant : un seul endroit qui sait d'où peut venir un brouillon.

Ce qui a changé dans l'écran, en revanche, c'est ce qu'une ligne **dit d'elle-même**.

### Deux incertitudes, affichées séparément

Une ligne proposée porte une `Suggestion`, et elle en garde **deux** : la confiance du modèle sur ce qu'il a compris, et le verdict de la confrontation au catalogue. Elles se trompent séparément — le modèle peut être sûr d'avoir vu du riz complet quand le catalogue n'a que du riz blanc, et l'inverse arrive tout autant. Les moyenner rendrait un chiffre qui ne se rapporte à rien et effacerait le seul cas qui compte : celui où l'une des deux doute.

S'y ajoute le marqueur qu'exige [04](04-sources-de-donnees.md#conversion-des-quantités) — *« toute conversion appuyée sur un défaut plutôt que sur une donnée réelle doit être signalée »*. Sans lui, « 1 bol » converti au forfait s'afficherait avec la même autorité qu'une portion mesurée par la fiche.

Les alternatives se posent **sans obliger à choisir** : la ligne est déjà remplie avec le meilleur candidat, et les trois autres sont là au cas où. En faire une question à trancher ferait payer trois lectures à chaque ligne douteuse, y compris quand le premier candidat était le bon. En choisir une garde la quantité — corriger « riz » en « riz complet » ne doit pas faire retaper 180 g — et **efface la marque** : la ligne n'est plus une proposition mais une décision.

### « Décrire » arrive, « Photographier » non

L'accueil gagne un bouton, pas deux. Les deux modes d'IA partagent tout sauf leur entrée, mais la modale photo n'existe pas encore, et **un bouton qui n'ouvre rien n'est pas une avance** — c'est ce que disait déjà le commentaire de cet écran quand aucun des deux n'existait.

Le bouton est **visible et grisé** sans clé ([D73](#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée)), et **tapable dans les deux cas** : caché, il ne s'apprendrait jamais — personne ne cherche dans les réglages une fonctionnalité dont rien n'indique l'existence — et inerte, il n'apprendrait rien non plus. L'appui ouvre une explication courte, avec le chemin vers les réglages.

Les huit messages d'erreur descendent dans `:core:designsystem` au passage. [02](02-parcours-et-ecrans.md#écran-dia) veut *« mêmes erreurs, mêmes messages »* entre la photo et la description, et le bouton « Tester » pose exactement la même question au même port : trois écrans qui rédigent chacun leur version d'« il n'y a pas de réseau » finissent par en avoir trois, dont deux qui vieillissent mal. C'est le raisonnement de `SourceBadge`, qui traduit déjà une énumération du domaine au même endroit.

### Campagne de défaite : seize sabotages, trois survivants au premier tour

Les trois disaient chacun un vrai trou, et aucun n'était le trou qu'on aurait deviné.

- **Un test qui regardait un drapeau au lieu de compter les appels.** « Un second appui ne repaie pas la même phrase » vérifiait que l'écran affichait encore « analyse en cours » — ce qu'il ferait aussi bien avec deux appels en vol. Il compte désormais les appels, qui sont ce qui se paie.
- **La source du dépôt n'était vérifiée par rien.** Déposer sous `MANUAL` au lieu de `TEXT_AI` passait : le plat aurait perdu la seule trace du passage d'un modèle, et la pastille de l'écran de validation aurait menti.
- **La marque ne traversait pas le formulaire.** Le brouillon la portait, l'écran ne la recevait pas, et rien ne tombait — une supposition se serait affichée avec l'autorité d'un aliment choisi.

Un quatrième sabotage n'a pas compilé, et c'est aussi une réponse : le harnais ne sait pas distinguer « la règle est tenue par le compilateur » de « rien ne la couvre », donc il l'a signalé comme une survie. Il a fallu le rejouer sous une forme qui compile pour savoir laquelle des deux c'était ([D71](#d71--le-compte-des-citations-quitte-le-catalogue-parce-quun-faux-ne-peut-pas-linventer---validée) nommait déjà ce piège dans l'autre sens).

**Conséquences.** `:feature:capture` naît, et il portera les deux modales : elles partagent le contrat de reconnaissance, donc l'attente, les erreurs et la sortie — les séparer ferait deux fois le même état. `DraftTextField` devient multiligne sur demande, `LineEdit` gagne sa sixième variante, et `ResolveFoodLabel` reçoit enfin un fournisseur Hilt : il attendait un appelant depuis sa livraison.

**Ce que le vert ne prouve pas.** **Aucun appel réel n'a encore abouti**, et cet écran n'a jamais été vu sur un téléphone. Le repli IA groupé de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment) § étape 4 n'existe pas : un libellé introuvable arrive avec son nom et sa quantité, sans valeurs, et attend qu'on le complète à la main. La modale photo non plus, ni le compteur de coût.

## D81 — Quatre fournisseurs pour une classe, parce qu'ils parlent la même langue · ✓ validée

**Contexte.** Les quatre derniers de [05](05-ia.md#fournisseurs) : OpenAI, DeepSeek, Mistral, et « compatible OpenAI ». La tranche promettait six fournisseurs derrière un port ; ils y sont.

### Une classe pour quatre, et ce n'est pas une économie de lignes

C'est un constat : les quatre envoient le **même** JSON à la **même** route et lisent la **même** réponse. `/v1/chat/completions` est le format de fait, et c'est précisément ce qui fait du dernier une **porte** plutôt qu'un fournisseur — une URL de base et un nom de modèle suffisent à y brancher OpenRouter, Groq, un Ollama du réseau local, LM Studio, ou un service qui n'existe pas encore. Quatre classes auraient fait quatre endroits à corriger pour un champ, et auraient surtout laissé croire à quatre protocoles là où il n'y en a qu'un.

**La seule variation est le schéma**, et elle est un **paramètre de construction** plutôt qu'un `when` sur le fournisseur. La règle de [12](12-plan-de-developpement.md) est explicite : un `when` sur le fournisseur ailleurs que dans la fabrique est le signal que l'abstraction a fui. La fabrique construit donc **deux instances de la même classe** — l'une qui envoie un schéma complet, l'autre qui demande seulement « du JSON » — et reste le seul endroit du projet qui sache qui est qui.

Ce que le second cas perd, le prompt le rattrape : il décrit déjà la forme attendue, avec un exemple, et c'est ce qui garde **un seul parseur pour les six fournisseurs** — ce que [D76](#d76--trois-prescriptions-de-docs05-tombent-au-contact-de-lapi-et-le-raisonnement-reste-actif---validée) cherchait en préférant la sortie structurée à un outil forcé.

### Le `/v1` qui ne se double pas

Premier fournisseur dont l'URL se saisit **vraiment** à la main : les relais s'annoncent tantôt `https://relais/v1`, tantôt `https://relais`. Coller un second `/v1` rendrait un `404` que personne ne rapporterait à cette ligne — et l'utilisateur soupçonnerait sa clé. Le chemin n'est ajouté que s'il manque.

### `content` est une chaîne **ou** un tableau, et c'est le JSON qui le dit

Troisième convention rencontrée pour une même idée : blocs typés par un discriminant chez Anthropic, champ présent chez Gemini, et ici un champ dont **le type JSON** change — une chaîne pour une description, un tableau de parties dès qu'une image s'y joint.

Un `String?` et une `List?` côte à côte auraient laissé exprimer « les deux à la fois », qui n'existe pas, et « ni l'un ni l'autre », qui rend un `400`. D'où un type scellé et un sérialiseur de dix lignes. Et la chaîne, jamais le tableau, dès qu'il n'y a pas d'image : les deux formes sont légales chez OpenAI, mais un relais compatible n'accepte parfois que la première — et c'est justement celui-là qu'on ne peut pas éprouver.

### Une réponse tronquée n'est pas une réponse illisible

Nuance que les deux premiers fournisseurs n'avaient pas. `finish_reason: "length"` dit qu'il **manque la fin** du JSON : le parseur échouerait sur un texte parfaitement bien formé jusqu'à sa coupure, et l'écran annoncerait un défaut technique là où le modèle a simplement été coupé. `content_filter` dit qu'il a décliné. Les deux rejoignent le refus d'Anthropic.

### Les identifiants de modèles, relevés et non écrits de mémoire — la troisième fois

`gpt-5.6-luna`, `gpt-5.6-terra`, `gpt-5.6-sol` ; `deepseek-v4-flash`, `deepseek-v4-pro` ; `mistral-small-2603`, `mistral-medium-3505`, `mistral-large-2512`. **Aucun** n'est celui que j'aurais écrit de mémoire, comme aucun des identifiants Gemini ne l'était en [D79](#d79--gemini-entre-au-prix-annoncé-et-deux-tests-attrapent-ce-que-la-relecture-avait-laissé-passer---validée). La liste n'est qu'un raccourci de frappe — le champ reste libre — mais un identifiant faux rend un `404` que l'utilisateur lira comme une panne de l'application.

DeepSeek et Mistral entrent en `MODEL_DEPENDENT` : la documentation du premier ne promet pas la lecture d'images, celle du second la promet sur ses généralistes et pas sur le reste de sa gamme. « Selon le modèle » dit exactement ce qu'on sait.

### Le test qui compte les entrées, pas celles qu'on a pensées

Le `when` de la fabrique est exhaustif : une entrée ajoutée **sans** branche ne compile pas. Mais une entrée ajoutée **avec la mauvaise branche** compile très bien — DeepSeek routé vers l'implémentation stricte enverrait un schéma à qui le refuse, et le `400` accuserait la clé.

Le cas de routage énumère donc les six entrées et **affirme d'abord que la table des attentes couvre `AiProvider.entries`**. Un septième fournisseur fera tomber ce cas tant que personne n'aura dit où il va.

**Campagne de défaite : onze sabotages, onze cas tombés.** Le schéma envoyé à tout le monde, la clé privée de son `Bearer`, le `/v1` doublé, une description partie en tableau, la consigne passée derrière le message, l'image passée derrière la consigne, l'arrêt tronqué banalisé, le `402` sorti du quota, le détail d'erreur supprimé, et DeepSeek routé vers le schéma strict — celui-là même qui aurait fait accuser la clé.

**Conséquences.** `:integration:ai` a désormais trois interfaces Retrofit pour six fournisseurs, et c'est le bon compte : trois protocoles existent. L'écran des réglages propose les quatre nouveaux sans qu'une ligne d'interface ait bougé — il lit l'énumération —, et « compatible » y arrive avec une URL de base **vide**, ce qui suffit à interdire le bouton *Tester* tant que l'utilisateur n'en a pas donné une : le formulaire exigeait déjà les trois champs.

**Ce que le vert ne prouve pas.** Aucun appel réel n'a atteint aucun de ces quatre fournisseurs, et il n'existe de clé pour aucun. Le dernier est par nature **inéprouvable** : son service n'a pas d'adresse tant que quelqu'un n'en donne pas une. Ce qui est vérifié est ce qui part sur le réseau, devant un vrai serveur local, et la traduction de ce qui revient.

## D82 — La prise de vue est déléguée, et l'accord se demande une fois · ✓ validée

**Contexte.** Le quatrième mode de saisie, et **le seul endroit de l'application où une donnée personnelle quitte l'appareil sans qu'un code-barres l'ait demandée**. C'est ce qui fait de cette livraison autre chose qu'un écran de plus.

### L'appareil photo du système plutôt qu'un aperçu à nous

[02](02-parcours-et-ecrans.md#écran-dia) décrit un aperçu CameraX avec déclencheur et bascule galerie. **Il n'est pas là**, et c'est le seul écart de forme de cette livraison.

Un aperçu intégré demanderait une **seconde** implémentation de CameraX — la première sert le scan, qui analyse un flux en continu et n'a rien à partager avec une prise unique — pour un écran dont le seul travail est de remettre un JPEG. Elle serait entièrement invérifiable ici : il n'y a pas d'émulateur, et le liage, la rotation et la capture ne s'éprouvent qu'en tenant le téléphone. L'appareil photo du système, lui, apporte la mise au point, le flash et le zoom de l'appareil, écrit directement dans notre cache, et **le sélecteur de médias donne la bascule galerie sans une ligne**.

Ce qui est perdu : le viseur dans l'application, et le conseil de cadrage en surimpression. Le conseil est déplacé dans le corps de l'écran, **où il se lit avant d'appuyer** plutôt que pendant qu'on vise. Le viseur, lui, se rebranchera derrière le même état le jour où quelqu'un le tiendra en main et le voudra.

**La permission reste nécessaire, contre toute attente.** Déléguer une prise de vue n'en demande normalement aucune — sauf quand l'application déclare `CAMERA` dans son manifeste, ce que fait `:integration:scanner` pour le scan. Le système l'exige alors avant de lancer l'appareil photo, même si c'est lui qui prend la photo. Sans ce chemin, le déclencheur échouerait sans rien dire.

### La réduction, et l'ordre qui empêche la mémoire d'exploser

1024 px sur le côté long, JPEG 80 : les deux chiffres viennent de [05](05-ia.md#coût), et c'est la principale réduction de dépense du mode photo — environ 150 Ko et 250 à 400 jetons, contre plusieurs mégaoctets pour la photo d'origine, payés par l'utilisateur.

Trois choses dans cet ordre : **mesurer sans décoder**, décoder **en sautant des pixels**, puis mettre à l'échelle et compresser. Décoder d'abord ferait passer une photo de douze mégapixels par un bitmap de quarante-huit mégaoctets, ce qui est le chemin le plus court vers une `OutOfMemoryError` sur un téléphone qui fait autre chose.

**L'orientation EXIF est appliquée**, et ce n'est pas un détail cosmétique : un téléphone tenu debout écrit souvent une image couchée accompagnée d'une étiquette « tourne-moi ». Sans elle, le modèle reçoit une assiette de profil et estime des quantités sur une image qu'aucun humain ne verrait ainsi. `android.media.ExifInterface` suffit — celle d'AndroidX apporte des formats qu'aucun appareil photo ne produit, pour une dépendance de plus.

**La géométrie est éprouvée, le décodage non**, et c'est la division habituelle : `sampleSizeFor` et `scaledSizeFor` sont de l'arithmétique et tiennent sur la JVM ; `BitmapFactory` demande un appareil.

### Le consentement : une fois, et il nomme le fournisseur

[05](05-ia.md#confidentialité) l'exige avant la première utilisation. Il nomme le fournisseur actif, et c'est ce qui en fait une phrase **vérifiable** : « votre photo sera envoyée à Mistral » se contredit tout seul si c'est faux, là où « à votre fournisseur » ne se vérifie pas.

Il est demandé **au moment de l'envoi** et non à l'ouverture de l'écran : c'est l'envoi qui expose la photo, pas le fait de la prendre. Quelqu'un qui cadre, réfléchit et referme n'a rien envoyé et n'avait donc rien à accepter. Refuser laisse la photo en place — on peut changer d'avis sans la reprendre.

Il est rangé **dans le même fichier que les clés**, non chiffré. Ce n'est pas un secret mais une trace de décision ; et surtout, effacer ses réglages d'IA doit effacer l'accord avec, parce que quelqu'un qui repart de zéro n'a rien accepté.

### Ce qui protège l'argent et le repas

**Annuler coupe vraiment**, comme [02](02-parcours-et-ecrans.md#écran-dia) l'écrit : une requête abandonnée qu'on laisse courir se paie quand même.

**La photo survit à l'échec.** Une clé refusée ou un réseau absent ne doit jamais obliger à ressortir le téléphone au-dessus d'une assiette qu'on est peut-être en train de manger. Et l'échec offre la **saisie manuelle** : un fournisseur en panne ne doit pas empêcher de noter son repas.

**Le fichier meurt dans un `finally`** — succès, échec ou annulation —, et un balayage au démarrage ramasse ce qu'un processus tué aurait laissé. Le nom est fixe plutôt qu'horodaté : deux prises ne peuvent pas être en vol — l'appareil photo du système est une activité modale —, et un nom horodaté aurait demandé de lire l'horloge, ce que le projet réserve au port `Clock`. Bénéfice inattendu : une prise qui écrase un résidu nettoie le cache au lieu de l'encombrer.

Une image **choisie dans la galerie n'est jamais supprimée** : elle ne nous appartient pas.

### Deux regroupements, imposés par le seuil

`LongParameterList` a mordu deux fois : l'écran photo à dix rappels, et l'accueil à huit sorties une fois ses quatre modes en place. La réponse du projet est de découper plutôt que de relever le seuil — d'où `PhotoActions` et `HomeRoutes`, la forme déjà retenue pour l'accueil, la validation et les réglages. Le seuil a fait exactement ce pour quoi il est là.

**Campagne de défaite : quinze sabotages, deux survivants au premier tour**, et les deux étaient des défauts de **test**, pas de code.

- **« Annuler » ne mesurait que le bouton.** Retirer l'annulation du `Job` ne faisait rien tomber : le cas vérifiait que l'écran cesse d'afficher « analyse en cours », ce qu'il fait aussi bien pendant qu'une requête continue de courir — et de se payer. Il vérifie désormais que l'appel **est coupé**. C'est la deuxième fois de la tranche qu'un cas regarde l'affichage au lieu de la dépense ([D80](#d80--la-proposition-passe-par-un-dépôt-et-lécran-de-validation-ne-change-pas-dun-mot---validée) avait le même défaut sur le double appui).
- **Le côté court ne pouvait pas tomber à zéro** avec la fixture choisie : une image de 4000 × 3 se réduit à une hauteur de 0,768, que l'arrondi remonte à 1 tout seul. Il faut descendre à 4000 × 1 pour que le garde-fou serve. Le cas passait donc sans jamais éprouver ce qu'il annonçait.

Les treize autres sont tombés du premier coup : le consentement contourné, l'accord non enregistré, le fournisseur non nommé, le refus qui envoie quand même, la note non nettoyée, la note vide devenue note, l'échec qui efface la photo, la nouvelle photo qui garde l'ancien échec, la source du dépôt qui ment, l'analyse sans photo, et trois sur la géométrie.

**Conséquences.** `:core:designsystem` gagne un `CameraGlyph`, troisième glyphe dessiné après l'étoile creuse et le code-barres, pour la même raison : `material-icons-core` n'en a pas, et `material-icons-extended` embarque des milliers d'icônes pour en utiliser une. L'accueil a **ses quatre modes de saisie**, les deux d'IA grisés ensemble puisque c'est la même clé qui leur manque. `:data:settings` lie un troisième port.

**Ce que le vert ne prouve pas — et c'est l'essentiel ici.** **Aucune photo n'a jamais été prise par ce code.** Le décodage, l'orientation EXIF, le `FileProvider`, la permission et le retour de l'appareil photo du système ne s'éprouvent qu'en tenant le téléphone. Ce qui est vérifié est ce qui coûte de l'argent ou expose une donnée : le consentement, l'annulation, ce qui part avec la photo, ce qui est déposé, et ce qui survit à un échec.

## D83 — Le repli invente des chiffres, une seule fois et en le disant · ✓ validée

**Contexte.** L'étape 4 de [04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment), et **l'exception à la règle la plus structurante du projet**. [05](05-ia.md) pose que le modèle identifie et que les bases calculent ; ici, pour les libellés qu'aucune base ne connaît, on lui demande les macros elles-mêmes.

Le trou était visible depuis la modale texte : « tofu fumé au sésame » n'est ni dans l'ANSES ni dans le cache d'Open Food Facts, et la ligne arrivait avec son nom, sa quantité, et **rien d'autre** — donc non enregistrable, sur un plat qu'on venait de payer pour faire analyser.

### Trois garde-fous, et ils tiennent la porte étroite

1. **Un seul appel groupé.** Cinq lignes non résolues coûtent une requête, pas cinq. Une liste vide — le cas courant, quand tout a été résolu — ne part jamais sur le réseau.
2. **Rien n'entre au catalogue.** Et c'est obtenu **sans aucune règle nouvelle** : `EntryDraft.foods` verse à l'enregistrement les fiches que les lignes portent, or une ligne estimée n'en porte aucune. Elle a un nom, des valeurs et une référence pour 100 g, mais ni `foodId` ni `Food`. L'interdit de [04](04-sources-de-donnees.md) est donc structurel plutôt que surveillé.
3. **La ligne le dit.** Un chiffre inventé qui s'affiche comme un chiffre mesuré est pire que pas de chiffre du tout.

### Pas de `FoodSource.AI_ESTIMATE`, malgré la lettre de docs/04

La spécification demande de marquer le résultat `source = AI_ESTIMATE`. **La source n'a pas été ajoutée**, et c'est le seul écart : une estimation ne devient jamais une fiche, donc elle n'a pas de source à porter. Une valeur d'énumération qu'il faudrait n'écrire nulle part serait un piège tendu au premier qui la persisterait — et `FoodSource` est justement persistée.

Ce que la spécification voulait dire est porté par `Suggestion.estimatedMacros`, à l'endroit exact où la question se pose : la ligne. Elle en porte maintenant **trois** incertitudes distinctes, et le fait qu'elles se trompent séparément est toute la raison de ne pas les fondre — ce que le modèle a compris, d'où vient la quantité, d'où viennent les valeurs.

### « Présentée à zéro » devient « présentée vide »

[04](04-sources-de-donnees.md) écrit qu'en l'absence de clé valide, la ligne est « présentée à zéro avec une invitation à la compléter ». Elle est présentée **vide**. Dans ce projet un champ vide vaut *inconnu* et jamais zéro — c'est la règle qui gouverne les six valeurs nutritionnelles depuis la tranche 2 —, et afficher `0 kcal` sur un tofu serait une affirmation que personne n'a faite. L'invitation, elle, est déjà là : l'écran dit qu'une ligne sans énergie n'est pas enregistrable.

### Un second prompt, un second schéma, et le même tout le reste

Deux fichiers d'asset plutôt qu'un paragraphe ajouté au premier : ce sont deux questions posées dans deux appels, et les mêler ferait payer à chaque reconnaissance les consignes d'une estimation qui n'a le plus souvent pas lieu. Le qualificatif Hilt qui les sépare n'est pas décoratif — deux `SystemPrompt` nus se seraient laissés intervertir en silence.

Le schéma d'estimation **n'exige que le libellé**. Un modèle qui ignore les fibres d'un plat doit pouvoir se taire sur cette valeur plutôt que d'en inventer une pour satisfaire un schéma ; exiger les six aurait fabriqué des zéros que personne n'a mesurés. Le prompt demande d'ailleurs explicitement d'**omettre** un libellé inconnu — « une omission se corrige à la main, un chiffre inventé passe inaperçu » — et c'est pourquoi une **liste vide est ici une réponse valide**, là où elle est un échec pour la reconnaissance.

Le libellé est recopié **à l'identique**, et c'est ce qui permet de recoller l'estimation à sa ligne. Une reformulation — « tofu fumé » pour « tofu fumé au sésame » — rend une estimation qu'on ne peut plus rattacher : elle est écartée plutôt que devinée.

### Deux méthodes sur le contrat interne, deux ports dans le domaine

Le domaine déclare bien deux ports — `FoodRecognizer` et `NutritionEstimator` —, parce que la division qui compte est celle-là : reconnaître et estimer ne sont pas la même question et n'appellent pas la même confiance. En dessous, `ProviderRecognizer` gagne une **seconde méthode** plutôt qu'une seconde hiérarchie : c'est le même fournisseur, la même clé, la même pile HTTP et la même traduction des codes. Deux interfaces auraient fait douze objets pour un appel HTTP de différence.

**Campagne de défaite : treize sabotages, deux vrais trous.**

- **Rien ne vérifiait que l'estimation se recolle par le libellé.** Remplacer la recherche par « prends la première estimation venue » ne faisait rien tomber : les cas n'avaient qu'un seul libellé non résolu à la fois, donc les deux comportements se confondaient. Un modèle qui reformule aurait rempli la mauvaise ligne, et personne ne l'aurait su.
- **Les deux gardes de la fabrique n'étaient éprouvées par personne** : ni « une liste vide ne part pas », ni « sans configuration, rien ne part ». Le test de la fabrique ne parlait que de reconnaissance.

Un troisième sabotage n'a pas compilé et a d'abord été compté comme une survie — le harnais ne sait pas distinguer « tenu par le compilateur » de « rien ne le couvre ». Réécrit sous une forme qui compile, il tombe. Et un quatrième était **ma** faute : l'attente était formulée sur le message d'assertion au lieu du nom du cas, alors que la règle était bien couverte.

Les autres sont tombés du premier coup : le repli lancé sur des lignes déjà résolues, un appel par ligne au lieu d'un seul, l'estimation non signalée, la référence non posée — donc une quantité qui ne recalcule plus —, l'estimation versée au catalogue, la valeur négative devenue zéro, et la liste vide traitée comme un échec.

**Conséquences.** `:integration:ai` porte un second prompt, un second schéma et un second parseur — qui partage la tolérance du premier, dans le même fichier, rebaptisé `AiParsers.kt` pour ne plus mentir sur son contenu. `ResolveRecognition` prend une troisième dépendance, et c'est la dernière : le chaînage de [04](04-sources-de-donnees.md) est complet, des quatre étapes.

**Ce que le vert ne prouve pas.** Aucune estimation réelle n'a jamais été demandée à un modèle, donc **on ne sait pas ce qu'elles valent**. C'est la seule partie du projet dont les chiffres ne sont adossés à rien de traçable, et c'est exactement pourquoi elle le dit sur chaque ligne. Le jour où de vraies estimations tomberont, la question à se poser ne sera pas « est-ce que ça marche » mais « est-ce que c'est assez juste pour être proposé ».

## D84 — Le compteur dit ce qui est facturé, dans la devise où la facture tombe · ✓ validée

**Contexte.** [05](05-ia.md#coût) : *« L'utilisateur paie ses appels : il a le droit de savoir combien. »* Un compteur local, une table de tarifs embarquée, **datée et signalée comme indicative**.

### Par modèle, alors que docs/05 ne demande qu'un compteur par fournisseur

Les tarifs sont attachés aux **modèles** : un compte agrégé par fournisseur ne pourrait plus se convertir en argent. L'écran, lui, affiche une ligne par modèle sous le nom du fournisseur — c'est une question d'affichage, pas de mesure.

### On compte ce qui est facturé, pas ce qui est tenté

Une clé refusée, un quota dépassé, un réseau absent, un délai dépassé : rien n'a été produit chez le fournisseur, et les compter gonflerait un chiffre dont **tout l'intérêt est d'être comparable à une facture**.

Une réponse **illisible ou vide**, en revanche, a été produite — donc payée. Elle est comptée sans ses jetons, que la réponse n'a pas rendus : annoncer zéro jeton serait pire que de n'en annoncer aucun, et c'est la règle qui gouverne déjà les six valeurs nutritionnelles.

### Les prix sont relevés, pas écrits de mémoire — et arrondis vers le haut

Relevés le 19 août 2026 sur les pages de tarifs des cinq fournisseurs. **Deux prudences volontaires**, qui vont dans le même sens — ne jamais annoncer moins cher que la réalité :

- Sonnet 5 est en tarif d'introduction jusqu'au 31 août 2026 ; c'est le tarif **plein** qui est inscrit, parce qu'une estimation qui expire sans prévenir ment le lendemain.
- DeepSeek facture moitié prix hors des heures pleines ; ce sont les **heures pleines** qui sont inscrites, parce que l'application ne sait pas à quelle heure l'appel est parti.

Un modèle absent de la table n'a **pas** de prix, et l'écran affiche ses jetons seuls plutôt qu'une moyenne inventée. C'est le cas de tout modèle saisi à la main, et de tous ceux du fournisseur « compatible » — dont personne ne peut connaître le tarif, puisque personne ne sait quel service il désigne.

### En dollars, et non en euros

[05](05-ia.md#coût) écrit « une estimation en euros ». Les cinq fournisseurs facturent en **dollars**. Convertir demanderait un taux de change que l'application n'a aucun moyen de connaître : il faudrait l'inventer, le figer, et le voir vieillir plus vite que les tarifs eux-mêmes. Une estimation dans la devise où la facture tombe est vérifiable ; une conversion à un taux inventé ne l'est pas. L'écran le dit : *« seule sa facture fait foi »*.

### Pas de Room pour trois entiers

Trois entiers par couple fournisseur-modèle, jamais interrogés autrement qu'en bloc, sans date ni relation. Une table aurait apporté une migration, un DAO et un schéma exporté pour une somme que les préférences portent aussi bien — dans le **même fichier que les clés**, pour qu'un effacement des réglages d'IA remette le compteur à zéro : il ne compte que ce que ces clés-là ont dépensé.

**Conséquences.** Deux seuils ont mordu coup sur coup : la fabrique passait huit paramètres, puis le module Hilt douze fonctions. La pile HTTP part donc dans son propre module — `AiHttpModule` — et les trois interfaces Retrofit voyagent ensemble dans un `AiApis`. Le découpage suit ce que les choses **sont** (le transport d'un côté, les ports du domaine de l'autre), pas un compte à respecter.

**Ce que le vert ne prouve pas.** Les prix eux-mêmes ne sont éprouvés par aucun test — un test qui les recopierait ne vérifierait que ma capacité à copier deux fois la même chose. Ce qui est éprouvé est la règle de trois, l'accumulation, et les deux refus : pas de montant pour un modèle inconnu, pas de zéro à la place d'un inconnu.

## D85 — Deux défauts que seul l'usage réel pouvait montrer, et un écran qui cesse de faire chercher · ✓ validée

**Contexte.** Premier vrai usage de l'application avec une clé qui marche. Gemini répond, la modale texte produit un plat de plusieurs aliments — et il est **impossible de l'enregistrer**. Deux défauts, et une liste de reproches d'ergonomie qui tiennent tous à la même cause : l'écran de validation n'avait jamais eu à porter cinq lignes.

### La fiche que le formulaire perdait

**Le vrai défaut, et il a fallu deux diagnostics pour l'atteindre.** `EntryFormLine` —
la ligne telle que l'écran la manipule — ne portait **pas** la fiche. Elle en gardait
l'identifiant, pas l'objet. Or c'est la fiche que l'enregistrement verse au catalogue :
un brouillon qui la portait entrait dans le formulaire, en ressortait sans, et
l'écriture citait donc un aliment que personne n'avait versé.

Invisible sur les trois autres chemins, parce qu'ils versent **avant** : la recherche
au moment du choix, le scan à la lecture du code-barres. L'IA choisit pour
l'utilisateur et n'écrit rien — résoudre est une lecture, et c'est une bonne règle.

**Ce que ça dit du reste.** Chaque morceau était éprouvé de son côté : la résolution
rendait bien des lignes avec leur fiche, l'enregistrement versait bien les fiches qu'on
lui donnait. Le défaut vivait dans la **couture**, et aucun test ne traversait la
couture. `ProposedDishSavingTest` la traverse maintenant, volontairement à travers
`EntryForm` qui n'est pas du domaine : c'est là que la chose se perdait, et un test qui
l'aurait contourné serait resté vert.

### Un identifiant provisoire dans une entrée de journal

**Une seconde ceinture, et il faut le dire : ce n'était pas le défaut.** Un résultat de recherche de l'ANSES porte un identifiant **provisoire** — il change à chaque recherche, et c'est `place` qui rend la fiche désignable en la versant au catalogue. Or `place` rend la fiche **déjà rangée** quand elle existe, avec l'identifiant qu'elle avait, et `remember` jetait cette information. L'entrée de journal citait donc l'identifiant provisoire, la clé étrangère `food_entry.food_id → food.id` refusait, et l'écran annonçait « l'écriture n'a pas abouti » sans pouvoir dire pourquoi.

La correction reste — elle ne coûte rien et ferme un chemin par lequel une entrée pourrait citer une fiche absente — mais **le cas ne se produit pas** avec les implémentations actuelles : la recherche ne distribue un identifiant provisoire qu'à une fiche qui n'est pas encore rangée. Un test qui prétendait l'éprouver a d'ailleurs été réécrit pour cette raison : sa mise en scène ne pouvait pas produire la situation qu'il annonçait.

**La correction.** `FoodUsage.remember` rend la correspondance « identifiant porté → identifiant rangé », et `toEntries` l'applique. C'est le geste d'écriture qui la découvre : le lui faire rendre est la seule façon de la connaître, et ça corrige **toutes** les origines d'un coup plutôt que d'ajouter un `place` sur le chemin de l'IA.

### Un favori qui reprenait les corrections de son auteur

**Le défaut.** Compléter à la main une ligne mal renseignée — la feta, les câpres, une ligne proposée par un modèle —, mettre le plat en favori, le rejouer : les corrections avaient disparu.

Et c'était **écrit exprès**. [D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée) fait du favori un modèle vivant : une ligne qui cite une fiche se reconstruit depuis la fiche courante, pour que corriger ses flocons corrige tous les petits-déjeuners à venir.

**La correction.** Le modèle vivant est une bonne règle **tant que la fiche dit vrai**. Celui qui a complété lui-même les valeurs a dit le contraire, et le projet a déjà un mot pour ça : `edited`, qui empêche le recalcul de réécrire une valeur corrigée. Une ligne corrigée entre donc dans le favori **déliée** de sa fiche, et ses valeurs figées prennent le relais. La contrepartie est voulue : cette ligne ne suivra plus la fiche, parce qu'elle ne la cite plus.

### Ce que l'écran de validation faisait chercher

Cinq reproches, une cause : l'écran avait été conçu pour une ligne et jugé sur une ligne.

- **Le balayage supprimait sans qu'on l'ait voulu.** Il reste le raccourci d'un geste sur une liste qu'on parcourt — l'accueil le garde — mais cet écran est un **formulaire** : on y fait glisser son doigt pour atteindre un champ. La corbeille suffit, et elle demande de viser.
- **Six champs pleine largeur, puis six autres.** Écrire « 254 » dans un champ de cinquante caractères allonge l'écran pour rien : les valeurs passent **deux par ligne**, dans l'ordre de l'hexagone lu de gauche à droite.
- **Le pliage des macros disparaît.** Cette application sert à suivre des macros ; les cacher demandait un geste de plus par aliment pour voir ce qu'on était venu voir. Le raisonnement d'origine — « les afficher laisserait croire qu'il faut les remplir » — ne tient plus depuis que ce qui manque se **dit** à l'enregistrement.
- **Rien ne séparait deux aliments.** Une carte par ligne : le défaut n'était pas d'être laid mais d'être **continu**, et rien ne disait où finissait le riz et où commençait le poulet.
- **« Un champ est vide » devant vingt-quatre champs.** L'écran **désigne** désormais : il marque la ligne, y fait défiler, colore le champ, et nomme ce qui manque — « Il manque les calories — Riz blanc, cuit ». La marque reste jusqu'au prochain appui : l'effacer à la première frappe la ferait disparaître au moment précis où l'on s'en sert.

### Choisir une alternative changeait le brouillon sans changer l'écran

Troisième défaut trouvé à l'usage, et le même mécanisme que les deux premiers : la
donnée était juste, l'écran ne la montrait pas. Toucher une pastille d'alternative
remplaçait bien la ligne — les pastilles disparaissaient, preuve que le modèle avait
changé — mais le nom et la quantité restaient ceux d'avant.

**Un champ de saisie ne relit son texte initial qu'à la première composition**
([D45](#d45--un-champ-de-saisie-tient-son-texte-lui-même---validée)),
et les seuls champs qui portaient une clé de composition étaient ceux des valeurs. Ils
la portaient pour une autre raison : faire revivre les six macros quand la quantité les
recalcule — et surtout **pas** le champ de quantité lui-même, qu'on est en train de
taper.

D'où **deux compteurs et non un** : `revision` dit que les valeurs ont été recalculées,
`substitutions` dit que ce n'est plus le même aliment. Le premier fait revivre les
valeurs, le second toute la ligne. Les confondre casserait l'un des deux : une clé
unique poserait le curseur ailleurs à chaque frappe de quantité.

### Un numéro plutôt qu'une phrase

Le nom proposé à la mise en favori était la liste des aliments du plat. Les libellés de l'ANSES sont à rallonge, et trois d'entre eux font un titre de cinquante caractères **qu'on efface au lieu de le corriger**. C'est « Plat 3 » désormais — le premier numéro libre, calculé par le domaine ; le mot « Plat » reste une ressource, parce que le domaine n'écrit pas d'interface.

### Quatre fournisseurs en réserve

Anthropic et Gemini ont été éprouvés sur un vrai compte. Les quatre autres sont écrits, testés, et leur campagne de défaite est passée — mais **aucun appel réel ne les a jamais atteints**, et les proposer ferait payer à quelqu'un la découverte d'un défaut que personne n'a cherché.

Leur carte reste **visible et en retrait**, avec une phrase qui dit pourquoi : la cacher laisserait croire qu'ils n'existent pas. Un drapeau sur l'énumération plutôt qu'une suppression — rien n'est perdu, le `when` de la fabrique reste exhaustif, et le jour où l'un d'eux est éprouvé il revient en changeant un mot.

**Conséquences.** Trois seuils ont mordu au passage — la longueur d'une fonction, le nombre de fonctions d'un fichier, le nombre de paramètres d'un constructeur — et la réponse a été la même trois fois : découper selon ce que les choses sont. `MissingFieldGuide.kt` porte le guidage vers le champ manquant, `DraftFavorites` regroupe les trois gestes de l'étoile.

**Ce que le vert ne prouve pas.** Les deux défauts corrigés ont chacun leur cas, sur les deux implémentations du catalogue. L'ergonomie, elle, ne s'éprouve qu'en tenant le téléphone : ce qui est écrit ici est un pari sur ce qui se lira mieux, et il se juge à l'usage.

**Ce qui reste ouvert.** La résolution choisit encore mal : « 5 cacahuètes » devient 500 g — le forfait `PIECE` à 100 g, faute de portion nommée pour une cacahuète — et « une mandarine » tombe sur un nectar. Le modèle ne connaît pas les libellés de l'ANSES, et le score les rapproche à l'aveugle. C'est un sujet à part entière, et il attend sa livraison.

## D86 — La liste des favoris devient l'endroit où on les gère, et le numéro ne recule pas · ✓ validée

**Contexte.** Deux demandes d'usage : gérer ses favoris depuis leur liste, et un nom proposé qui ne réutilise pas un numéro libéré.

### Un revirement assumé sur D62

[D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée) réservait la suppression à **l'étoile de l'écran de validation** : un seul endroit pour la décision, donc rien à tenir d'accord. Le raisonnement était bon et la conséquence mauvaise.

La liste est le **seul endroit où l'on regarde ses favoris en tant que liste**, donc le seul où l'on s'aperçoit qu'il y en a un de trop ou un à corriger. Y arriver par l'étoile demandait de **rejouer** le favori — c'est-à-dire d'ouvrir un repas qu'on ne voulait pas noter, puis de se souvenir d'annuler. Le coût de l'unicité était payé par le geste le plus fréquent.

Un appui long y ouvre donc « Modifier » et « Supprimer », comme sur un plat de l'accueil. La suppression **ne demande pas confirmation**, à la différence d'un plat du journal : un favori est un modèle, pas un fait. Le supprimer ne perd aucune donnée — les repas déjà notés gardent leurs lignes — et le refaire coûte une étoile.

### Le même écran, un autre sens du mot « enregistrer »

« Modifier » ouvre l'écran de validation **sur le favori lui-même**. Même écran, mêmes lignes, même étoile ; ce qui change est ce qu'enregistrer veut dire — réécrire le modèle, sans rien ajouter au journal. On est venu corriger, pas manger.

Un **drapeau de route** plutôt qu'une seconde destination, et un écran qui **le dit** : son titre devient « Modifier le favori » et son bouton « Enregistrer le favori ». Un écran qui annoncerait « Nouvelle saisie » alors qu'il réécrit un modèle mentirait sur ce que l'appui va faire — et c'est exactement le genre de mensonge que les trois défauts précédents ont rendu coûteux.

Le nom, lui, ne se redemande pas : on modifie un favori depuis sa liste, où il est déjà nommé.

### Les plats qui citaient le favori le perdent

**Pas de répercussion en chaîne** : leurs lignes ne bougent pas d'un gramme, et c'est la règle du journal depuis [D05](#d05--les-entrées-de-journal-figent-leurs-valeurs---validée) — un registre d'événements ne se réécrit pas.

Ce qui tombe est la **provenance**. « Rejoué depuis les Flocons du matin » n'est plus vérifiable quand les Flocons du matin ont changé de contenu : un lien qui ment vaut moins qu'un lien absent. Le port du journal gagne donc `unlinkFavorite`, servi par un `UPDATE` d'une colonne — la requête vit dans le DAO **des favoris** bien qu'elle écrive dans `dish`, parce que ce qu'elle répond est une question sur le favori.

### Le compteur qui ne redescend jamais

Le nom proposé était le premier « Plat n » libre. Supprimer « Plat 1 » faisait donc réapparaître ce nom au favori suivant — deux favoris successifs indiscernables dans un historique, et un nom qu'on venait d'écarter qui revient.

`FavoriteNumbering` est un **compteur, pas un décompte** : il avance à chaque proposition et ne compte pas les favoris existants. Il vit dans les préférences et non dans la base — c'est un état d'interface, un entier sans date ni relation, et une table lui aurait apporté une migration et un schéma exporté pour rien.

**Les deux règles se complètent** : le compteur garantit qu'un numéro ne réapparaît pas, et la vérification d'unicité qu'il n'en heurte pas un que quelqu'un a nommé « Plat 4 » à la main. Le verrou du compteur n'est pas décoratif : lire puis écrire n'est pas atomique, et deux ouvertures rapprochées de la boîte rendraient le même numéro.

**Campagne de défaite : six sabotages, six cas tombés.** Le déliement supprimé, le modèle non réécrit, un favori disparu passé pour un succès, un favori vidé accepté, le compteur remis à 1, le nom pris non enjambé.

Un survivant apparent n'en était pas un : `domain.jar` était **vide** — le piège de [10](10-qualite-et-livraison.md#gradle) —, donc rien ne compilait et aucun cas ne pouvait tomber. Le harnais comptait « zéro échec » là où il fallait lire « zéro exécution ». Rejoué après suppression du jar, le cas tombe.

**Conséquences.** `DraftFavorites` porte un quatrième geste — le seuil de fonctions du `ViewModel` a d'ailleurs forcé à l'y déplacer plutôt qu'à l'y laisser. `:data:diary` ouvre son propre fichier de préférences : les réglages d'IA ne rangent pas les noms de plats, et effacer l'un ne doit pas remettre l'autre à zéro. Le graphe de navigation, lui, a dû se couper en deux — la capture d'un côté, la recherche et les favoris de l'autre.

**Ce que le vert ne prouve pas.** L'appui long, le menu et le titre de l'écran ne s'éprouvent qu'en tenant le téléphone. Ce qui est vérifié est ce qui écrit : le modèle réécrit, le journal délié, le repas non créé, et le numéro qui avance.

## D87 — Les calories se proposent, et une valeur minorée se dit au lieu de se taire · ✓ validée

**Contexte.** Première des trois demandes issues de l'usage réel ([12](12-plan-de-developpement.md#demandes-issues-de-lusage-réel)). Corriger les macros d'une ligne laissait l'énergie inchangée — et c'est elle qui décide si la ligne est enregistrable. Les facteurs du règlement UE 1169/2011 la déduisent des quatre autres : 4 kcal/g pour les protéines et les glucides, 9 pour les lipides, 2 pour les fibres. Les glucides y sont déclarés **hors fibres**, comme dans CIQUAL et dans Open Food Facts, si bien que les additionner tels quels ne compte rien deux fois — c'est [D24](#d24--les-fibres-sont-déduites-du-solde-glucidique---validée) prise par l'autre bout.

### Une proposition, et le mot n'est pas décoratif

Une pastille à toucher, jamais un champ qui se remplit. Un écran qui écrirait tout seul dans un champ que quelqu'un vient de remplir ferait exactement ce que `edited` existe pour empêcher, et il le ferait au moment le plus dommageable : celui où l'on est en train de corriger.

### La valeur calculée est marquée corrigée à la main

Arbitré avec Charly, et c'est le seul comportement cohérent : les macros dont elle se déduit ne suivent déjà plus la quantité, puisqu'on vient de les écrire. La laisser suivre la référence pour 100 g la ferait diverger d'elles au gramme suivant.

**La contrepartie ne mord que dans un cas, et il fallait le mesurer avant de trancher.** Une ligne corrigée entre dans un favori **déliée** de sa fiche ([D85](#d85--deux-défauts-que-seul-lusage-réel-pouvait-montrer-et-un-écran-qui-cesse-de-faire-chercher---validée)). Quand ce sont les macros qu'on vient de corriger, la ligne est déjà déliée et le calcul n'y change rien. Le seul cas nouveau est celui d'une fiche dont l'énergie manque **à la source** — la feta, les câpres, 143 fiches de l'ANSES : accepter le calcul y délie la ligne. C'est voulu, et c'est le comportement utile : la fiche n'a toujours pas d'énergie, donc un favori qui la citerait la reprendrait vide au rejeu.

### Trois situations, dont une qui interdit de proposer

- **L'énergie manque.** La ligne n'est pas enregistrable, c'est le cas que la demande nomme en premier.
- **L'énergie contredit les macros.** Le défaut qui a motivé la demande : après correction, l'énergie d'avant reste en place — présente, donc silencieuse, et fausse. Ne proposer que sur une énergie absente l'aurait laissé passer entier.
- **Les fibres manquent et l'énergie est là : on ne propose rien.** L'écart observé peut n'être **que** les fibres qu'on ignore, et remplacer une mesure par un calcul minoré serait une régression. Quinze fiches de l'ANSES sont exactement dans ce cas, et c'est en les comptant que la règle a été écrite.

### Les fibres absentes valent zéro, mais seulement pour débloquer

Exiger les quatre valeurs, comme la lettre du plan le demandait, aurait rendu la proposition muette là où elle sert le plus : une ligne proposée par un modèle a le **droit** de se taire sur les fibres ([D83](#d83--le-repli-invente-des-chiffres-une-seule-fois-et-en-le-disant---validée)), et 55 fiches de l'ANSES n'ont ni énergie ni fibres.

La valeur est donc minorée d'au plus 2 kcal par gramme ignoré, **et l'écran l'écrit** — « hors fibres, non renseignées ». Une valeur minorée qui s'annonce débloque la ligne ; la même valeur silencieuse serait un chiffre inventé de plus. Le zéro de commodité ne s'échappe jamais du calcul : c'est tout le travail du garde-fou ci-dessus.

### Deux seuils, parce qu'un seul a toujours un angle mort

Un seuil relatif seul ferait clignoter la proposition sur les petites lignes — 3 kcal d'écart sur une tisane en font 60 %. Un seuil absolu seul se tairait sur un plat de 2 000 kcal faux de 1 %. Les deux doivent tomber : **10 kcal et 10 %**. En deçà, l'écart n'est pas une erreur mais l'arrondi à l'entier des six champs ([D52](#d52--deux-savedstatehandle-une-seule-saisie-manuelle-des-grammes-entiers---validée)), qui ne se rattrape pas.

### Le geste ne transporte pas le chiffre

`LineEdit.AcceptEnergy` est un `data object` sans valeur : la ligne recalcule au moment où elle applique. Transporter le chiffre affiché ferait de l'écran la source d'une valeur nutritionnelle, alors qu'il n'en est que le miroir, et rendrait possible d'écrire une énergie que la règle n'aurait pas proposée.

### Ce que le compteur de révision achète ici

`revision` avance, sans quoi **rien ne se verrait** : le brouillon porterait la nouvelle énergie pendant que le champ afficherait l'ancienne. C'est la forme exacte des trois derniers défauts trouvés à l'usage — la donnée était juste, l'écran ne la montrait pas — et [D45](#d45--un-champ-de-saisie-tient-son-texte-lui-même---validée) dit pourquoi elle se reproduit : un champ ne relit sa valeur initiale qu'à la première composition.

### Les facteurs d'Atwater déménagent

Ils vivaient dans `goal/MacroDistributionPolicy.kt`, où ils sont nés, et leur propre KDoc les disait « utilisés partout dans le projet ». Ce sont des facteurs de **nutrition** : le calcul d'objectif n'est qu'un de leurs deux usages, et faire dépendre `nutrition` de `goal` pour les atteindre aurait inversé la dépendance qui a du sens. Ils sont dans `nutrition/MacroEnergy.kt`, et les deux fichiers de `goal` les importent.

**Campagne de défaite : treize sabotages, treize cas tombés.** Les quatre facteurs, les sucres comptés deux fois, les glucides absents passés pour zéro, le garde-fou des fibres, l'énergie absente non proposée, chacun des deux seuils séparément, la valeur minorée non signalée, la marque `edited`, le compteur de révision, l'arrondi, et le geste rendu inerte.

Le premier tour a rapporté **treize « rien n'a tourné »**, et c'était le harnais et non le code : `subprocess` ne peut pas exécuter le `gradlew` shell sous Windows. Le compte rendu aurait dit « treize règles non couvertes » si le harnais n'avait pas exigé qu'un nombre de cas **exécutés** soit supérieur à zéro avant de conclure quoi que ce soit. C'est la même leçon que le `domain.jar` vide de [D86](#d86--la-liste-des-favoris-devient-lendroit-où-on-les-gère-et-le-numéro-ne-recule-pas---validée), par une autre porte.

**Conséquences.** Un fichier de domaine, `nutrition/MacroEnergy.kt`, et un fichier d'écran, `feature/entry/EnergyFromMacros.kt`. Le second existe parce que `EntryForm.kt` était **exactement** au seuil de onze fonctions par fichier : la réponse du projet est de découper selon ce que les choses sont, et « la proposition d'énergie » en est une, comme `MissingFieldGuide` l'est.

Accepter le calcul **éteint l'étoile**, comme toute retouche de ligne : le brouillon cesse d'être celui que le favori décrit ([D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée)). Ce n'est pas un effet de bord, c'est la règle qui s'applique.

**Ce que le vert ne prouve pas.** Que la pastille se voie, qu'elle tombe sous le pouce, et qu'elle n'encombre pas une carte qui porte déjà douze champs — rien de tout cela ne s'éprouve sans tenir le téléphone. Ce qui est vérifié est ce que le geste écrit : la valeur, sa marque, son arrondi, et le fait qu'elle survive au rejeu d'un favori — ce dernier cas traversant volontairement la couture jusqu'à `GetFavoriteDraft`, sur le modèle de `ProposedDishSavingTest`.

## D88 — Le titre court se fabrique hors de l'application, et il ne touche jamais au libellé · ✓ validée

**Contexte.** Deuxième demande d'usage ([12](12-plan-de-developpement.md#un-titre-court-sur-chaque-fiche)). Les libellés de l'ANSES décrivent une préparation — « Poulet, blanc, sans peau, cuit au four, sans matière grasse ajoutée » — et **2 091 des 3 484 fiches dépassent trente caractères**. Trois d'entre elles rendent une liste illisible.

### Un revirement, et il vient d'un chiffre

Le plan posait la question « quand la passe tourne » comme si le coût la commandait — « une fois, mais 3 484 fiches à payer ». **Le chiffre a été mesuré avant de trancher, et il ne dissuade rien** : les 3 484 titres valent entre 0,20 $ et 0,40 $ selon le modèle. Ce qui coûte n'est pas l'argent, ce sont les soixante-dix requêtes et leur durée.

La conception a d'abord suivi un bouton dans les Réglages, puis Charly a tranché autrement : **la passe se fait hors de l'application, et son résultat entre au dépôt.** C'est le meilleur des deux, et pour une raison que le coût masquait : la table de l'ANSES ne change qu'à sa publication, donc re-raccourcir les mêmes libellés sur chaque téléphone est un travail refait pour rien. L'application ne gagne ni bouton, ni prompt en asset, ni compteur à payer.

### Deux passes et non une, parce que ce ne sont pas deux facettes d'une chose

Un titre court est un **affichage** qui n'invente aucun chiffre et concerne 2 091 fiches. Une valeur complétée est un **chiffre inventé** qui n'en concerne que 313, et qui exige une provenance valeur par valeur, une migration et un affichage qui le dise. Les livrer ensemble aurait retenu la première — sans risque et utile tout de suite — derrière la seconde. Celle-ci reste ouverte.

### Un CSV versionné, comme les portions

`tooling/ciqual/short-names.csv`, à côté de `servings.csv` et lu par la même tâche. Ce n'est pas une commodité : c'est ce qui rend le résultat **relisible et corrigeable ligne à ligne**, sans rien relancer ni repayer. Un titre raté se répare en éditant une ligne, et la correction survit à la génération suivante — qui ne redemande que les codes absents du fichier.

C'est aussi ce qui rend la passe **reprenable**, la seule propriété qui rende soixante-dix requêtes supportables : le fichier est réécrit après chaque lot, donc une coupure au trentième reprend au trentième.

Le lecteur est aussi sévère que celui des portions, et pour la même raison — ce fichier est écrit par une machine puis corrigé par une personne, donc c'est là que les deux se trompent. Un code inexistant, un titre vide, plus long que quarante caractères ou plus long que le libellé qu'il remplace arrêtent l'import en nommant la ligne.

### Le titre ne vit pas dans le catalogue, et c'est [D54](#d54--un-bandeau-de-rayons-et-deux-familles-qui-ne-se-combinent-pas-pareil---validée) qui l'avait déjà tranché

La table `food` ne le stocke pas. Il se relit dans `ciqual.db` par le code de la fiche, **exactement comme le rayon**, et pour le même raisonnement : une copie figerait le titre du jour où elle a été faite, corriger un titre à rallonge n'atteindrait jamais les fiches déjà utilisées, et une migration ne pourrait pas le rattraper — les deux bases sont deux fichiers. C'est une propriété de la **référence**, pas de la copie.

Le bénéfice est immédiat : **aucune migration Room**, et les fiches déjà versées au catalogue — celles qu'on utilise le plus — reçoivent leur titre sans rien faire. `categoriesOf` devient donc `annotationsOf` et rend les deux en une requête : deux questions posées à la même table pour les mêmes codes, et les séparer aurait fait deux allers-retours par affichage.

### Le libellé d'origine ne bouge jamais

Il relie la fiche à sa source, et **c'est sur lui que l'index de recherche est bâti** : un index sur le titre court ne trouverait plus « poulet cuit au four sans matière grasse ». Une seule propriété décide de ce qui s'affiche — `Food.displayName`, le titre court sinon le libellé — parce que quatre listes, l'écran de validation et le nom d'une nouvelle ligne posent la même question, et que six réponses divergeraient au premier oubli.

Ce nom-là est celui qu'une ligne neuve prend, donc celui que le journal fige : l'accueil devient lisible lui aussi, et c'est l'écran qu'on regarde tous les jours. L'écran de validation, lui, **rappelle le libellé d'origine** sous le champ — c'est lui qui distingue deux préparations du même aliment, et on valide ce qui va entrer au journal. Le rappel disparaît dès que le champ est retouché : sous un nom qu'on a écrit soi-même, un « libellé d'origine » désignerait autre chose que ce qu'on lit.

**Une conséquence assumée** : deux fiches peuvent recevoir le même titre court, et une liste ne les distinguerait plus. Le prompt demande de garder ce qui distingue, mais un modèle qui ne voit qu'un lot de cinquante ne peut pas garantir l'unicité sur trois mille. L'import **les nomme sans bloquer** — en faire une condition rendrait la table impossible à produire, alors qu'un avertissement se corrige à la main.

### La clé appartient à l'utilisateur, y compris dans une tâche Gradle

La tâche la lit d'une propriété passée à l'invocation — `-PanthropicApiKey=…` — et rien d'autre. Elle n'est ni lue d'un fichier, ni écrite dans un fichier, ni journalisée, ni citée dans un message d'erreur : un message qui la porterait la ferait entrer dans un journal de build, c'est-à-dire dans un endroit qu'on partage sans y penser. La tâche ne déclare **ni entrée ni sortie** à Gradle, parce qu'une tâche qu'on paie doit partir quand on la lance, et non quand Gradle l'estime nécessaire.

Le SDK Anthropic entre dans `:tooling:ciqual-import` et **nulle part ailleurs** : l'application parle à ses six fournisseurs par Retrofit et ses propres DTO, et rien de ce SDK n'atteint le graphe de `:app`. Le modèle par défaut est `claude-opus-5`, remplaçable par `-PcatalogueModel=…` ; le choix du modèle appartient à celui qui paie.

**La réponse se lit en texte, pas en JSON.** Une ligne « code, tabulation, titre », et toute ligne sans tabulation est ignorée — ce qui absorbe un préambule ou un commentaire ajouté malgré la consigne. Perdre cinquante titres déjà payés parce que le modèle a écrit « Voici : » serait le pire des deux maux. Le rattachement se fait par **code**, jamais par libellé, exactement comme [D83](#d83--le-repli-invente-des-chiffres-une-seule-fois-et-en-le-disant---validée) l'a établi pour l'estimation : un modèle qui reformule produit un résultat qu'on ne peut plus rattacher, et on l'écarte plutôt que de le deviner.

### Six titres écrits à la main

Le CSV n'est pas vide, et ce n'est pas de la décoration. Tant que la passe n'a pas tourné, une chaîne complète sans une seule donnée ne s'éprouve pas : le test qui part du fichier livré n'aurait rien à lire. Six lignes écrites à la main servent d'exemple de ce qu'on attend **et** rendent la couture vérifiable de bout en bout. La génération ne les redemandera pas.

**Campagne de défaite : dix-neuf sabotages, dix-neuf cas tombés — après quatre corrections.**

Quatre règles n'étaient pas couvertes, et trois d'entre elles portaient sur des cas que je croyais écrits :

- **Un test qui ne testait pas ce qu'il annonçait.** « Un titre plus long que le libellé est écarté » utilisait un libellé de soixante-six caractères, donc un titre plus long dépassait aussi la longueur maximale — et c'est **elle** qui l'écartait. La règle visée n'était jamais atteinte. Il a fallu une fixture de trente-deux caractères pour isoler les deux.
- La virgule dans un titre du CSV, et le titre trop long du CSV : deux cas que je croyais couverts par leurs jumeaux du générateur.
- **La colonne écrite par le writer** n'était éprouvée par personne : le sabotage visait les tests de `:data:food`, qui lisent l'asset **déjà produit** et qu'aucune modification du writer n'atteint. La cible était mauvaise autant que la couverture.

Deux tours ont rapporté « rien n'a tourné » plutôt qu'une survie, et le harnais a eu raison d'exiger un nombre de cas exécutés supérieur à zéro : un sabotage qui cassait un *smart cast* et ne compilait pas, et un `NoClassDefFoundError` dû à l'état incrémental de Kotlin — le piège de [10](10-qualite-et-livraison.md#gradle) sous un troisième visage.

**Conséquences.** `ciqual.db` gagne une colonne, donc `CiqualDatabase.REVISION` passe à 3 — c'est ce qui force la recopie sur un appareil déjà installé, et sans quoi la première requête aurait échoué chez Charly seulement. Le fichier grossit d'une page SQLite, 4 096 octets. Deux constats detekt ont mordu : les cinq chemins de l'import deviennent un type nommé plutôt qu'une déconstruction, et la lecture du CSV perd un `return`.

**Ce que le vert ne prouve pas.** Aucun appel réel n'a été fait : la tâche est écrite, compilée et sa logique éprouvée sans réseau, mais **personne n'a encore vu un titre produit par un modèle**. Ce qui est vérifié est la chaîne — le CSV lu, la colonne écrite, la lecture, le modèle, l'affichage — sur six titres écrits à la main. La qualité des 2 085 autres est une question qui se posera quand ils existeront, et le fichier est fait pour qu'on y réponde en corrigeant des lignes.

Ni la lisibilité des listes ni le rappel sous le champ ne s'éprouvent sans tenir le téléphone.

## D89 — Une valeur complétée ne se range pas où une valeur mesurée se range · ✓ validée

**Contexte.** La seconde moitié de la demande d'usage sur le catalogue, et **l'exception la plus lourde du projet à sa propre règle**. [D83](#d83--le-repli-invente-des-chiffres-une-seule-fois-et-en-le-disant---validée) posait qu'une estimation ne devient jamais une fiche ; ici elle y entre. CIQUAL laisse **313 fiches sur 3 484 avec au moins un trou**, dont 143 sans aucune énergie déterminée — la feta, les câpres, l'oignon au vinaigre. Une ligne sans énergie n'est pas enregistrable, donc ces fiches sont inutilisables en l'état.

### La règle qui commande toute la conception

**Une valeur complétée et une valeur mesurée ne se rangent pas au même endroit.** Six colonnes distinctes dans `ciqual_food`, jamais mêlées aux huit d'origine. Sans cela, un nouvel import de la table de l'ANSES écraserait les complétions — ou pire, les prendrait pour des mesures, et plus rien ne dirait lesquelles.

La lecture préfère toujours l'originale, et le champ `Food.estimated` dit laquelle a servi. **La marque suit ce qui s'affiche, pas ce qui est stocké** : le jour où l'ANSES publie enfin la teneur, la mesure l'emporte et la ligne cesse d'être marquée, même si le fichier porte encore l'estimation.

### Trois barrières plutôt qu'une, et elles ne se recouvrent pas

1. **On ne demande que les trous.** Une teneur publiée n'est jamais soumise : la poser reviendrait à inviter un modèle à contredire une mesure.
2. **Le fichier refuse une complétion sur une teneur publiée**, et arrête l'import en nommant la ligne. Une telle ligne est soit fautive, soit un reliquat, et dans les deux cas elle doit se voir plutôt que dormir en attendant qu'un import ultérieur la fasse resurgir.
3. **La lecture préfère l'originale.** Une seconde ceinture, et il a fallu la campagne de défaite pour découvrir qu'elle n'était éprouvée par personne — voir plus bas.

### La provenance se porte valeur par valeur

`Food.estimated: Set<Macro>`, et non un drapeau de fiche. Une fiche dont trois teneurs sur six viennent d'un modèle n'est ni une fiche mesurée ni une estimation : un drapeau unique aurait menti dans les deux sens. C'est le vocabulaire de `DraftLine.edited`, qui pose déjà la même question pour les six.

La marque **descend jusqu'à la ligne de saisie** et survit au recalcul : une valeur estimée pour 100 g reste estimée pour 180 g. La règle de trois ne transforme pas une supposition en mesure — c'est le pendant exact de « une valeur inconnue le reste ».

**Corriger une valeur efface sa marque, et elle seule.** La valeur est celle de l'utilisateur désormais ; continuer à la présenter comme incertaine serait faux. Les autres teneurs de la même ligne gardent la leur : les effacer ensemble ferait passer pour mesurée une valeur toujours devinée. Les deux gestes qui écrivent une valeur — la saisie et l'acceptation du calcul de [D87](#d87--les-calories-se-proposent-et-une-valeur-minorée-se-dit-au-lieu-de-se-taire---validée) — appliquent la même règle.

### Un contour en pointillés, et pas une couleur

Le champ concerné prend le contour que [D25](#d25--lestimation-ia-se-signale-par-une-forme-pas-par-une-couleur---validée) réserve à l'estimé depuis la tranche 2 : une forme, jamais une teinte qui travaillerait seule. Il est dessiné **par-dessus** la bordure de `OutlinedTextField`, qui ne se laisse pas remplacer sans réécrire le composant — un `drawWithContent` au même rayon la recouvre exactement, là où une réimplémentation coûterait la gestion du focus, de l'erreur et du libellé flottant.

### Deux passes distinctes, pas une symétrie de façade

Même mécanique que [D88](#d88--le-titre-court-se-fabrique-hors-de-lapplication-et-il-ne-touche-jamais-au-libellé---validée) — une tâche lancée à la main, un CSV versionné, une reprise après chaque lot — mais **un fichier, une tâche et une relecture séparés**. Un titre court est un affichage qui n'invente aucun chiffre ; une teneur complétée entrera dans un journal alimentaire. Chacune doit pouvoir être lancée, relue et refusée sans l'autre.

Une ligne par **valeur** et non par fiche : c'est la granularité de la règle, et c'est ce qui rend une complétion supprimable seule. Les lots sont de vingt contre cinquante — un chiffre nutritionnel demande plus d'attention qu'un raccourci de libellé, et il y a dix fois moins de trous que de libellés à raccourcir.

Deux bornes physiques écartent ce qui ne peut pas être : **cent grammes pour cent grammes**, et **950 kcal** pour l'énergie, parce que cent grammes d'huile pure en valent 900. Zéro, lui, est accepté : une huile contient réellement zéro gramme de glucides, et refuser ce zéro ferait redemander éternellement une valeur que le modèle a raison de donner.

### Ce qu'il advient d'une complétion périmée

Elle est **retirée**, et c'est la tâche de génération qui le fait — la seule qui réécrive le fichier. Une estimation a été produite contre un état précis de la fiche ; l'état change, elle ne décrit plus cette fiche-là, et la garder ferait resurgir un chiffre périmé si la mesure repartait. Le retrait est annoncé : une ligne qui disparaît d'un fichier versionné doit s'expliquer, sans quoi le diff se lit comme une perte.

**Campagne de défaite : vingt-huit sabotages, vingt-huit cas tombés — après trois corrections.**

Trois règles n'étaient pas couvertes, et **deux d'entre elles ne pouvaient pas l'être** par les tests existants :

- **La lecture qui préfère l'originale, et la marque qui ne suit que ce qui s'affiche.** Les deux sabotages survivaient, et pour une raison qui est presque un compliment à la conception : une fiche portant à la fois une mesure et une complétion pour la même teneur **ne peut pas sortir de la base livrée**, puisque le lecteur du CSV refuse cette ligne. Aucun test parti du fichier livré ne pouvait donc serrer cette ceinture. `FoodMapperTest` la serre sur une ligne construite à la main — c'est la forme exacte du défaut de [D85](#d85--deux-défauts-que-seul-lusage-réel-pouvait-montrer-et-un-écran-qui-cesse-de-faire-chercher---validée) : une règle couverte en apparence par des cas incapables de la mettre en défaut.
- **Une macro inconnue dans la réponse du modèle.** Le cas existait, mais plaçait la ligne fautive **en premier** : un repli silencieux sur un compteur quelconque était écrasé par la ligne suivante, et `toMap` gardait la bonne valeur. Le cas passait sans rien mesurer. Déplacer la ligne fautive en second le fait tomber.

Deux sabotages ont dû être réécrits parce qu'ils cassaient un *smart cast* et ne compilaient pas, et un tour a rapporté « rien n'a tourné » sur un état incrémental corrompu. Le harnais a distingué les trois cas de la survie, ce qu'il fallait : trois campagnes de suite, ce compte a évité une conclusion fausse.

**Conséquences.** `ciqual.db` gagne six colonnes et passe en `REVISION` 4. `DraftTextField` gagne un paramètre et le tracé qui va avec — le premier élément du design system que cette tranche touche. `Macro.nutrient` relie les deux vocabulaires par un `when` exhaustif : ajouter un septième compteur cessera de compiler plutôt que de se taire.

**Ce que le vert ne prouve pas.** **Aucune estimation réelle n'a été demandée à un modèle.** C'est la même phrase qu'en [D83](#d83--le-repli-invente-des-chiffres-une-seule-fois-et-en-le-disant---validée), et elle pèse plus lourd ici : ces chiffres entreront au catalogue et se retrouveront dans un journal. Les deux complétions du fichier livré sont écrites à la main, et calculées — pas devinées. La question à se poser quand les vraies arriveront n'est pas « est-ce que ça marche » mais « est-ce assez juste pour être compté ».

Le contour en pointillés ne s'éprouve qu'en tenant le téléphone, et il se peut qu'il se confonde avec la bordure de focus du champ.

## D90 — La contribution part sous le compte de l'utilisateur, et jamais sans lui · ✓ validée

**Contexte.** La dernière pièce de la tranche 6, et **la première écriture sortante de l'application** : [01](01-perimetre.md#contraintes-fermes) n'en prévoit aucune, tout le reste ne fait que lire. [D70](#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée) avait laissé trois questions ouvertes pour ne pas les improviser ; elles sont tranchées ici, et **le relevé sur la documentation en a transformé une**.

### « Compte ou anonyme » n'était pas la bonne question

D70 posait le choix comme si Open Food Facts acceptait les deux. Le relevé dit autre chose : les lectures ne demandent que le `User-Agent`, mais **toute écriture exige une authentification**. Il n'y a pas de contribution anonyme.

Restaient deux comptes possibles, et le second se disqualifie tout seul : un **compte global d'application** existe bien — avec `app_name`, `app_version` et un `app_uuid` salé par utilisateur pour que les modérateurs bannissent sélectivement — mais son mot de passe devrait vivre dans l'APK. Ce projet est publié sous GPL-3.0 : ce serait un secret publié, et le premier qui le lirait écrirait dans Open Food Facts au nom de tout le monde.

C'est donc **le compte de l'utilisateur**, saisi dans les réglages et rangé par le même chiffrement que les clés d'IA. Sans compte, le bouton **n'apparaît pas** plutôt que de refuser : c'est la règle des modes d'IA sans clé, et un bouton qui explique pourquoi il ne marche pas encombre un écran qu'on est venu lire.

L'identifiant est chiffré comme le mot de passe. Il est public sur le site, mais il est **la moitié d'un secret** : le laisser en clair réduirait le mot de passe au seul obstacle. `OffAccount.toString()` n'imprime ni l'un ni l'autre — un `data class` imprime tous ses champs par défaut, et c'est ainsi qu'un secret entre dans un journal de plantage.

### Ce qui part : quatre gardes, et aucune n'est décorative

Une fiche est contribuable si elle est **personnelle**, porte un **code-barres valide**, n'a **aucune valeur estimée** et porte un **nom**.

- Personnelle, parce qu'on ne reverse pas la table de l'ANSES à Open Food Facts, et qu'on n'écrit pas par-dessus le travail d'un autre contributeur sans voir ce qu'il avait mis.
- Un code-barres dont `Barcode.of` vérifie la clé de contrôle : une suite de chiffres qui n'en est pas un désignerait un produit qui n'existe pas.
- **Aucune valeur estimée**, et c'est la garde qui compte le plus. Un chiffre complété par un modèle ([D89](#d89--une-valeur-complétée-ne-se-range-pas-où-une-valeur-mesurée-se-range---validée)) qui entre dans une base publique y devient une mesure pour tous ceux qui le liront.
- Un nom, seul champ que le service exige en pratique.

**Une teneur inconnue ne part pas.** Elle n'est pas envoyée à zéro : l'envoyer écrirait dans une base publique une mesure que personne n'a faite. C'est la règle du projet, appliquée là où elle sort de l'appareil.

La règle vit dans `FoodContribution.of` et non dans l'écran, qui l'oublierait — et un type distinct de `Food` la rend structurelle : ce qui n'est pas dans `FoodContribution` ne peut pas partir.

### Le bac à sable, parce que personne n'a jamais vu cet envoi partir

Open Food Facts offre une instance de test sur `world.openfoodfacts.net` : le même logiciel, un jeu de données jetable. Un interrupteur des réglages y bascule l'envoi, **éteint par défaut** — une contribution qui partirait par défaut vers un bac à sable serait une contribution qui n'existe pas, offerte à quelqu'un qui croit contribuer.

Un réglage et non une variante de compilation : c'est en le basculant qu'on vérifie qu'un envoi aboutit, et une `buildConfigField` demanderait de réinstaller l'application pour ça.

### Aucun retrait exponentiel, contrairement à la lecture

Ce n'est pas un oubli. Une lecture rejouée trois fois ne coûte que du temps ; une écriture rejouée sans que personne l'ait demandé est une action sortante de plus. Le service range par code-barres, donc un second envoi ne créerait pas de doublon — et c'est **parce que réessayer est sûr** que le geste peut rester à l'utilisateur, qui voit l'échec et rappuie s'il le veut.

Quatre issues et non deux : envoyé, compte refusé, injoignable, refusé par le service. Elles appellent quatre conduites — rien faire, corriger son compte, attendre, renoncer — et les fondre en « ça n'a pas marché » ferait chercher au hasard. Le refus garde **la phrase du service** plutôt qu'un message inventé, comme les fournisseurs d'IA gardent la leur ([D78](#d78--le-fournisseur-garde-la-parole-parce-quun-message-inventé-ne-se-vérifie-pas---validée)).

### Une URL en dur a fait partir des requêtes réelles depuis les tests

**Le défaut le plus instructif de cette livraison, et il s'est vu tout de suite parce que le test traverse un vrai serveur.** Le contributeur lisait l'instance dans une constante ; le test montait sa pile devant `MockWebServer`, mais l'appel visait `world.openfoodfacts.org`. Les cas partaient donc **pour de vrai** sur la base publique, puis attendaient indéfiniment une requête locale qui n'arriverait jamais.

Une dizaine de `POST` ont ainsi atteint Open Food Facts avant que la cause soit trouvée. Ils portaient un compte inexistant, donc ils ont tous été refusés et **rien n'a été écrit** — mais c'est un coup de chance de conception, pas une précaution.

Les deux instances sont désormais **injectées**. Une URL en dur dans une classe qui écrit dehors n'est pas un détail de câblage : c'est ce qui décide si un test est un test. Et la bascule y gagne deux cas qui l'éprouvent pour de bon, sur deux serveurs locaux.

**Campagne de défaite : vingt-huit sabotages, vingt-huit cas tombés.** Les quatre gardes, le rognage des blancs, la marque vide, la portion, le compte à moitié saisi, l'impression du compte, l'ouverture sans compte, le bac à sable par défaut ; puis chaque nom de champ du corps, chaque unité, la teneur inconnue envoyée à zéro, le compte absent, les quatre issues, la bascule, le chemin et l'en-tête d'identification.

**Neuf tours d'affilée ont rapporté « rien n'a tourné », et la cause était le harnais — encore.** Il supprimait `domain/build/libs/domain.jar` avant chaque tour, en croyant appliquer le remède de [10](10-qualite-et-livraison.md#gradle). Ce remède est **curatif** : il s'applique à un jar *vide*. L'appliquer préventivement fait disparaître un jar sain sans que Gradle s'en aperçoive — la tâche `jar` se croit à jour, et tout module qui dépend du domaine cesse de compiler. Il a fallu vider `.gradle` pour en sortir. Le compte de cas exécutés a évité une quatrième conclusion fausse.

**Conséquences.** `:integration:openfoodfacts` gagne une seconde interface Retrofit — l'écriture n'est pas l'API v2, c'est un script d'édition hérité qui attend un formulaire — montée sur **le même client**, donc avec l'en-tête que [D26](#d26--le-user-agent-dopen-food-facts-est-figé---en-partie-remplacée-par-d105) rend obligatoire. `:data:settings` ouvre son propre fichier de préférences : effacer ses réglages d'IA ne doit pas déconnecter le compte de contribution.

**Ce que le vert ne prouve pas.** **Aucun envoi n'a jamais abouti.** Les quinze cas passent devant un serveur local qui répond ce qu'on lui a dit de répondre ; ce que le vrai service accepte, refuse, ou accepte en ignorant un champ mal nommé, reste à voir — et c'est précisément pour ça que le bac à sable existe. Le premier envoi réel est un travail de vérification à part entière, pas une formalité.

**Ce qui reste à faire.** L'écran : le bouton sur une fiche personnelle, le récapitulatif qui montre les champs exacts avant de partir, et les deux réglages. Le critère de fin de la tranche 6 — « une fiche saisie à la main **peut** être reversée, et rien ne part sans un geste explicite » — n'est donc pas encore atteint : cette livraison en construit la moitié basse, celle qui s'éprouve sans écran.

## D91 — La contribution se propose au moment où la fiche vient d'être écrite · ✓ validée

**Contexte.** La moitié haute de [D90](#d90--la-contribution-part-sous-le-compte-de-lutilisateur-et-jamais-sans-lui---validée), et **la tranche 6 se ferme avec elle**. Le port, le client et le compte existaient ; il manquait le geste.

### Le moment est le sujet, et il n'était dans aucune de mes propositions

J'avais proposé trois emplacements — un bouton dans la liste, un appui long, un écran de fiche — et **les trois étaient de mauvaises réponses à une bonne question mal posée**. Charly a tranché autrement : la proposition arrive **à l'enregistrement d'une fiche créée pour un produit que le scan n'a pas trouvé**.

C'est meilleur pour une raison qu'aucun des trois emplacements ne pouvait offrir : à cet instant, on sait *pourquoi* la fiche existe. Elle a été saisie parce qu'Open Food Facts ne connaissait pas ce code-barres, c'est-à-dire exactement le cas que [D70](#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée) décrit. Un bouton dans une liste aurait posé la question à froid, sur une fiche dont on ne se souvient plus de l'origine ; ici la question suit le travail qui la justifie.

Et cela **supprime du travail** : pas de troisième icône sur une ligne déjà chargée, pas d'écran de fiche à concevoir pour ce seul bouton.

### Trois raisons de ne rien proposer, et aucune n'est une erreur

La fiche n'est pas contribuable, aucun compte n'est configuré, ou la fiche n'a pas pu être relue. Dans les trois cas l'écran se referme comme avant, **sans rien dire** : un message qui expliquerait pourquoi on ne propose pas encombrerait le moment où l'on vient de finir une saisie.

**La fiche est relue plutôt que reconstruite depuis le formulaire.** Ce qui part est ce qui a été enregistré, pas ce qu'on croyait avoir saisi — et c'est aussi ce qui garantit la provenance `CUSTOM`, condition que seule l'écriture connaît.

### Deux cas d'usage et non un

`OfferContribution` regarde s'il y a quelque chose à offrir ; `SendContribution` agit sur le monde extérieur. Les fondre ferait passer une écriture sortante pour une lecture — et c'est la distinction que tout ce chemin sert à tenir.

**Le compte est relu au moment d'envoyer**, pas porté depuis la proposition : entre les deux, il a pu être effacé dans les réglages, et un envoi avec un compte périmé se ferait refuser sans qu'on sache pourquoi.

### Le récapitulatif, à chaque fois

Il montre les champs exacts — code-barres, nom, marque, les teneurs renseignées — et dit que l'envoi est public et définitif. À chaque fois, parce que ce n'est pas la même fiche à chaque fois : un accord donné une seule fois pour toutes porterait sur des données qu'on n'avait pas encore saisies.

**Les teneurs inconnues n'y figurent pas**, parce qu'elles ne partent pas. Les afficher vides laisserait croire qu'on envoie des blancs là où l'on n'envoie rien — et sur une base publique, un blanc effacerait ce que quelqu'un d'autre y aurait mis.

Quatre issues, quatre phrases, et **une seule invite à réessayer** : un service injoignable. Un compte refusé ne se rejoue pas — il se corrige. Le refus du service garde sa propre phrase, fût-elle en anglais.

### La proposition ne défait jamais l'écriture

Un `Status.OFFERING` de plus aurait fait de la contribution une étape de l'enregistrement. C'est un flux à part, superposé à un enregistrement déjà réussi : le refuser referme le dialogue et la fiche reste écrite. La recherche de la proposition est elle-même enveloppée — si elle échoue, l'enregistrement tient quand même.

### Le mot de passe ne revient jamais à l'écran

L'état des réglages dit **s'il y a** un compte, pas lequel. Plus strict que pour les clés d'IA, où le champ se révèle à la demande ([D77](#d77--la-clé-va-dans-le-keystore-en-direct-et-le-bouton-tester-est-une-vraie-analyse---validée)) : ici rien ne le demande, puisqu'on ne relit pas un mot de passe — on le remplace. L'identifiant, lui, s'affiche : c'est la seule façon de savoir sous quel nom on contribue.

**Campagne de défaite : cinq sabotages, cinq cas tombés.** La proposition sans compte, la fiche non contribuable proposée, la fiche absente devenue une contribution vide, le compte non relu à l'envoi, le compte à moitié saisi accepté.

**Conséquences.** `:feature:settings` gagne un quatrième écran, et le hub une quatrième carte. `CustomFoodUiState` gagne un état, et le `when` fermé de l'écran a refusé de compiler tant qu'il n'était pas traité — c'est exactement ce que ce type achète.

**Ce que le vert ne prouve pas.** **Toujours aucun envoi réel.** Rien n'a changé depuis [D90](#d90--la-contribution-part-sous-le-compte-de-lutilisateur-et-jamais-sans-lui---validée) sur ce point : le chemin est complet et éprouvé jusqu'à la frontière du réseau, et ce que le vrai service en fait reste à voir. Le bac à sable est là pour ça.

Le dialogue lui-même ne s'éprouve qu'en tenant le téléphone — et il arrive à un moment délicat, juste après un enregistrement qu'on croyait terminé.

## D92 — Une journée sans saisie n'a pas de ligne, et c'est structurel · ✓ validée

**Contexte.** Première livraison de la tranche 7. Le bandeau des sept derniers jours, la vue mensuelle, et l'écran Journée — de quoi relire un jour passé, ce que l'application ne permettait pas du tout.

### Le critère de fin, tenu par une absence

[12](12-plan-de-developpement.md#tranche-7---je-consulte-mon-historique-) demande qu'une journée sans saisie soit **visuellement neutre et jamais comptée comme une journée à zéro**. La façon de le tenir n'est pas de le vérifier à chaque affichage : c'est de ne produire aucune ligne. `GetCalendar` ne rend que les jours qui portent au moins un plat, et l'absence **est** la neutralité.

Une carte de trente jours dont vingt à zéro obligerait chaque écran à refaire la distinction, et le premier qui l'oublierait afficherait vingt jours de jeûne parfait. C'est la même règle que `null` face à zéro sur une teneur, portée à l'échelle d'une journée — et un `?: MacroTotals.Empty` posé par distraction la défait entièrement.

**Un jour à zéro kilocalorie garde sa ligne.** Quelqu'un a noté un café noir : le confondre avec une journée vide effacerait une saisie réelle.

### Aucun `SUM` en SQL, à l'échelle d'un mois

La règle de [D29](#d29--un-total-incomplet-se-signale-au-lieu-de-se-taire---validée) vaut telle quelle sur une plage : agréger en base traiterait les valeurs inconnues comme absentes, ce qui est juste arithmétiquement mais perd la trace de ce qui manquait. Les plats remontent entiers — quelques centaines de lignes pour un mois — et le domaine totalise jour par jour, en retenant quels totaux sont minorés.

### Chaque jour porte l'objectif qui valait ce jour-là

Sans quoi changer d'objectif repeindrait tout le mois écoulé en dépassement. C'est la raison d'être des objectifs versionnés ([D04](#d04--objectifs-versionnés-plutôt-que-mis-à-jour-en-place---validée)), et le calendrier est le premier écran qui en montre plusieurs à la fois.

Le port des objectifs gagne donc `observeAll()` : trente appels à `observeGoalOn` auraient fait trente flux et trente requêtes pour choisir parmi quelques versions. La liste est courte par nature — un objectif se révise, il ne se crée pas tous les jours.

### Un anneau segmenté, parce qu'un hexagone ne survit pas à 44 dp

[02](02-parcours-et-ecrans.md#bandeau-calendrier-fixe-en-haut) le demandait depuis la conception. `MacroSegmentRing` entre au design system : six arcs dans l'ordre angulaire de l'hexagone, la position servant de second canal en cas de daltonisme.

**Il ne se compose pas pour une journée absente.** Lui passer six zéros dessinerait un anneau vide identique à celui d'un jour de jeûne — la règle se tient donc aussi dans le composant, en ne l'appelant pas.

### Un seul modèle pour l'accueil et la journée relue

`HomeViewModel` lit une date facultative plutôt qu'un second modèle jumeau. C'est le même récapitulatif à une date près, et un `DayViewModel` aurait dupliqué la suppression, la restauration et l'étoile — pour les laisser diverger au premier correctif appliqué d'un seul côté. `DayContent` est partagé pour la même raison.

`null` et non `clock.today()` : la date par défaut est évaluée à chaque lecture, donc un écran resté ouvert pendant la nuit ne continue pas d'afficher la veille.

### Deux seuils ont mordu, deux découpages

`DiaryDao` passait à onze fonctions : la lecture de plage part dans `CalendarDao`. Ce n'est pas pour tenir un compte — le premier sert la journée courante et change quand l'écran de saisie change, le second sert la relecture d'un historique. Deux raisons de changer, deux objets, comme `FoodMarksDao` face à `FoodDao` ([D71](#d71--le-compte-des-citations-quitte-le-catalogue-parce-quun-faux-ne-peut-pas-linventer---validée)).

`HomeViewModel` passait à neuf paramètres : les quatre gestes sur un plat écrit deviennent `DishGestures`. Ce qu'ils ont en commun n'est pas d'être quatre, c'est de porter sur un plat déjà noté — un cinquième geste viendra là, une lecture non. Même mouvement que `DraftFavorites` en [D85](#d85--deux-défauts-que-seul-lusage-réel-pouvait-montrer-et-un-écran-qui-cesse-de-faire-chercher---validée).

**Campagne de défaite : sept sabotages, six cas tombés, et un survivant qui n'en est pas un.**

Les six : la journée vide devenue une journée à zéro, les plats d'un jour non totalisés ensemble, l'ordre chronologique, l'objectif courant appliqué partout, et les deux bornes du faux — qui doit filtrer exactement comme le vrai, sans quoi le contrat diverge en silence ([D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée)).

**Le survivant mérite d'être écrit.** Réécrire la convention de borne dans `activeOn` — fin incluse au lieu d'exclue — ne fait rien tomber, et ce n'est pas un trou : la liste est triée par date de début décroissante, donc le premier objectif qui couvre est de toute façon le plus récent. La convention est éprouvée là où elle vit, sur `Goal.coversOn`, dans `GoalCoverageTest`. C'est le cas que [10](10-qualite-et-livraison.md#gradle) nomme — *la couverture ne vient pas toujours d'un test* — et le commentaire du cas concerné le dit désormais, plutôt que de prétendre vérifier ce qu'il ne vérifie pas.

**Ce que le vert ne prouve pas.** Rien de ce qui s'affiche. Le bandeau, l'anneau segmenté à 44 dp puis à 28 dp, la grille du mois et la lisibilité d'une case vide ne s'éprouvent qu'en tenant le téléphone — et l'anneau segmenté est un composant neuf, dont personne n'a encore vu le rendu.

Le reste de la tranche 7 attend : le journal de poids, la moyenne mobile, et `SuggestGoalAdjustment`.

## D93 — Le calendrier vient d'une bibliothèque, et la semaine est calendaire · ✓ validée

**Contexte.** Trois défauts rapportés à l'usage sur le calendrier livré en [D92](#d92--une-journée-sans-saisie-na-pas-de-ligne-et-cest-structurel---validée), et une exigence de performance : *« l'application ne doit jamais freeze, les chargements se font en arrière-plan »*.

### La septième pastille était écrasée, et c'était arithmétique

Sept pastilles de 44 dp plus leurs marges font 336 dp ; un écran courant en offre 320 moins les marges d'écran. Le diamètre se calcule désormais sur la largeur disponible, borné entre 28 et 48 dp — assez grand pour que le chiffre reste lisible, assez petit pour qu'une tablette ne montre pas sept médaillons.

**Ce défaut n'était couvert par rien**, et la campagne l'a montré : remplacer le calcul par une constante ne faisait tomber aucun cas. Il en a trois maintenant, dont celui qui vérifie que sept pastilles et leurs marges tiennent dans 320 dp.

### Une semaine calendaire, pas sept jours glissants

Le bandeau plaçait aujourd'hui toujours à droite. On n'y lisait pas « lundi, mardi… » mais « il y a six jours, il y a cinq jours… », et retrouver hier demandait de compter. Une semaine se lit d'un coup d'œil, et **sa géométrie ne bouge pas d'un jour à l'autre**.

Les jours à venir de la semaine en cours restent visibles, en retrait, et ne s'ouvrent pas : [02](02-parcours-et-ecrans.md) interdit la saisie dans le futur, et un écran Journée d'un jour à venir ne pourrait rien proposer d'utile tout en laissant croire le contraire. La semaine garde ainsi ses sept cases.

### Une bibliothèque, et **sans monter Kotlin**

`kizitonwose/Calendar`, licence MIT, dans `:feature:home` et nulle part ailleurs. `WeekCalendar` et `VerticalCalendar` sont bâtis sur `LazyRow` et `LazyColumn` : seules les cellules visibles existent. Elle apporte surtout ce qu'on croit simple et qu'on corrige trois fois — pagination des mois, premier jour de semaine selon la locale, débordements de mois. Les cellules restent les nôtres, donc `MacroSegmentRing` ne change pas d'une ligne.

**Le palier retenu est 2.6.2, et la montée de Kotlin a été écartée par les faits.** Charly avait accepté de monter Kotlin pour prendre la dernière version ; deux relevés ont rendu la montée inutile puis indésirable :

- **2.6.2 offre déjà les deux calendriers** et se compile sous Kotlin 2.0.21. La dernière version n'apporte rien dont cet écran ait besoin.
- **detekt 1.23.8 ne lit pas les métadonnées de Kotlin 2.3** (issue detekt#8865). Il faudrait detekt 2.0-alpha, donc réécrire les trois règles maison contre une API instable — pour un gain nul. [D15](#d15--chaîne-de-construction-alignée-sur-loutillage-installé---par-défaut) tient toujours.

La question n'a pas été reposée parce que le fait la tranchait : Charly voulait monter Kotlin **afin d'utiliser cette bibliothèque**, et elle s'utilise sans.

### Le mois vit dans l'accueil, et une poignée porte le geste

Le même calendrier qui change de hauteur, et non deux écrans à tenir d'accord — l'écran mensuel séparé de D92 disparaît. Replié : la semaine. Déplié : les mois, qui défilent.

**La poignée plutôt que le calendrier lui-même.** Déplié, le mois défile verticalement, et un geste vertical sur lui appartient à son défilement. Poser le geste d'ouverture au même endroit aurait fait se disputer les deux, et le perdant aurait changé selon la vitesse du doigt.

### La lecture suit ce qu'on regarde

Charger douze mois d'un coup lirait des milliers de lignes pour en montrer trente. Le mois visible remonte au modèle par `snapshotFlow`, qui relit le journal sur ce mois et ses deux voisins ; `flatMapLatest` abandonne la lecture précédente, donc un défilement rapide ne laisse pas dix lectures en vol.

### Une lecture d'environnement cachée n'est pas éprouvable

Le premier jour de la semaine venait d'un `Locale.getDefault()` enfoui dans un getter. **La campagne l'a montré** : sur une machine française, remplacer la règle par « lundi » écrit en dur ne faisait rien tomber. Il est désormais porté par l'état, fourni par le modèle — et deux cas l'éprouvent, l'un en semaine commençant lundi, l'autre dimanche.

**Campagne de défaite : six sabotages, six cas tombés — après avoir rendu deux règles éprouvables.** Les sept jours glissants, le lundi en dur, la semaine amputée, aujourd'hui compté comme futur, hier compté comme futur, et le diamètre figé.

**Conséquences.** La première dépendance d'interface du projet, et elle n'entre que dans un module. `CalendarMonthScreen` et sa route disparaissent ; `CalendarStrip` est remplacé par `CalendarPane`.

**Ce que le vert ne prouve pas.** **Rien de ce qui se voit ni de ce qui se touche.** Le geste de la poignée, la transition semaine↔mois, la fluidité du défilement et le rendu des pastilles à leur nouvelle taille ne s'éprouvent qu'en tenant le téléphone. Les tests disent que sept pastilles **tiennent** en 320 dp ; ils ne disent pas qu'elles sont belles.

Et la promesse « jamais de freeze » n'est pas mesurée : elle repose sur des `Lazy*` et sur une lecture bornée au mois visible, ce qui est la bonne construction — mais aucun profilage n'a été fait.

## D94 — Le journal de poids se lit en trois traits, et la tendance se tait sous trois pesées · ✓ validée

**Contexte.** La tranche 7 continue : [02](02-parcours-et-ecrans.md#journal-de-poids) demande « liste des pesées et courbe. Deux tracés : les points bruts, discrets, et la **moyenne mobile sur 7 jours**, en évidence », plus la trajectoire visée en pointillés. [03](03-nutrition-calculs.md#adaptation-hebdomadaire) fournit le lissage et la pente, dont l'adaptation hebdomadaire se servira ensuite.

### Le plancher de trois pesées gouverne aussi la courbe

[03](03-nutrition-calculs.md#signal) posait ce plancher pour la **pente** : « il faut au moins 3 pesées dans chaque fenêtre […] un silence vaut mieux qu'un conseil fondé sur une seule pesée. » Il s'applique désormais aussi au tracé lissé, et c'est le même argument : **une moyenne sur sept jours calculée à partir d'une seule pesée *est* cette pesée**. La tracer en évidence, à côté des points bruts qu'elle est censée lisser, affirmerait un lissage qui n'a pas eu lieu.

Conséquence assumée : quelqu'un qui se pèse une fois par semaine ne verra jamais le tracé lissé. C'est juste — on ne lisse pas des données hebdomadaires avec une fenêtre de sept jours — et l'écran le **dit**, plutôt que de laisser chercher pourquoi le trait manque.

Les trous ne se relient pas non plus. Joindre deux moyennes séparées par trois semaines de silence dessinerait une progression que personne n'a mesurée.

### La pente visée est celle qui a été annoncée, et elle ne bouge pas

`(poids cible − poids connu au début de l'objectif) ÷ semaines entre son début et son échéance`. Le poids de départ est **la dernière pesée connue à la date de début de l'objectif**, et non celui d'aujourd'hui : la trajectoire part d'où l'on était quand on a fixé le cap, donc elle reste immobile pendant qu'on avance dessus — ce qui est tout l'intérêt d'un repère.

L'autre lecture possible — ce qu'il reste à perdre divisé par ce qu'il reste de temps — a été écartée : elle durcit la cible à chaque jour manqué, donc grossit la correction proposée, qui fait rater davantage. C'est la boucle que la borne de ±150 kcal de [03](03-nutrition-calculs.md#correction-proposée) sert à contenir ; autant ne pas l'alimenter.

### La courbe est dessinée à la main, contrairement au calendrier

[D93](#d93--le-calendrier-vient-dune-bibliothèque-et-la-semaine-est-calendaire---validée) a pris une bibliothèque ; ici non, et l'asymétrie est raisonnée. Le calendrier apportait de la **pagination paresseuse** sur un nombre de mois non borné, plus une demi-douzaine de règles calendaires qu'on corrige trois fois. La courbe, elle, est trois polylignes sur une série d'au plus une valeur par jour : pas de zoom, pas de sélection, pas de second type de graphique. Une bibliothèque de graphiques apporterait des axes et des animations dont rien ici n'a besoin, et demanderait en échange de plier son thème au nôtre.

Les trois tracés ne se disputent pas la lecture : points bruts discrets, moyenne mobile en évidence, trajectoire en **pointillés** — un repère et non une mesure, dit par une forme et non par une couleur ([D25](#d25--lestimation-ia-se-signale-par-une-forme-pas-par-une-couleur---validée)).

### L'échelle est séparée du dessin, parce qu'un `Canvas` ne se vérifie qu'à l'œil

`ChartScale` ne dessine rien : elle dit où tombe une date, où tombe un poids. C'est là que vivent les fautes qui font une courbe **fausse** plutôt que laide — deux mesures au même poids placées à deux hauteurs, une trajectoire lue avec une autre règle que les points, trois cents grammes d'écart étalés sur toute la hauteur. Treize cas l'éprouvent ; le dessin, lui, ne s'éprouve qu'en tenant le téléphone.

Deux bornes s'y écrivent. L'axe vertical couvre **au moins deux kilos** : sinon trois cents grammes d'écart rempliraient la hauteur, et une variation d'hydratation se lirait comme un effondrement — exactement ce que la moyenne mobile sert à ne pas montrer. Et l'axe horizontal **s'arrête aux mesures** plutôt qu'à l'échéance : deux semaines de pesées devant six mois d'horizon seraient tassées sur un douzième de la largeur. La trajectoire est une droite ; la dessiner sur la fenêtre des mesures dit la même chose, là où on regarde.

### Le port rend le journal entier, et c'est l'inverse du calendrier

`WeightLog.observeHistory()` remplace `observeRecent(limit)`, que rien n'appelait. Le calendrier borne ses lectures parce que le journal alimentaire compte des dizaines de lignes par jour ; ici il y en a **au plus une**, et dix ans de pesées quotidiennes tiennent en trois mille couples date-poids. Une lecture bornée coûterait un débordement de six jours à gauche pour que la moyenne mobile du premier jour existe, et le chargement séparé du poids de départ de l'objectif — pour n'économiser rien de mesurable.

Le nom dit « historique » et non « tout » : `Goals.observeAll` existe déjà, et `RoomProfileStore` porte les deux ports.

### Un module, et un quatrième glyphe

`:feature:weight` plutôt qu'une section des réglages : le journal se consulte depuis l'accueil, il porte un tracé qui n'existe nulle part ailleurs, et c'est lui qui recevra la carte d'ajustement. Une icône dans la barre de l'accueil l'ouvre — `material-icons-core` n'a pas de graphique, comme il n'avait ni code-barres ni `StarBorder` ([D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée)), donc `TrendGlyph` est dessiné : quatrième glyphe tracé après l'étoile creuse, le code-barres et l'appareil photo.

**Campagne de défaite : seize sabotages, seize cas tombés.** La fenêtre à huit jours, la fenêtre qui déborde sur l'avenir, le plancher ramené à une pesée, la pente comparée à la veille, le rythme annoncé compté par jour, la trajectoire partant du dernier poids connu, l'échéance antérieure au début, la pesée de demain acceptée, le poids nul accepté, le journal rendu à l'envers, la hauteur ignorant sa borne basse, les dates lues de droite à gauche, les mesures collées aux bords, l'amplitude minimale annulée, la trajectoire exclue de l'échelle, et l'échelle acceptant une pesée unique.

**Conséquences.** `observeRecent` disparaît du port et de ses deux implémentations. `HomeRoutes` et `HomeActions` gagnent une sortie. `SuggestGoalAdjustment` se branchera sur `weeklySlopeOn` et sur `WeightAim.weeklySlope`, qui existent déjà et sont éprouvés.

**Ce que le vert ne prouve pas.** **Le dessin.** Aucun test ne dit que la courbe est lisible, que les points bruts sont assez discrets, ni que le pointillé se distingue du trait plein sur un écran de téléphone en plein soleil. Les tests disent où tombent les valeurs ; ils ne disent pas ce qu'on voit.

La boîte de saisie non plus : le clavier décimal, la virgule qu'il produit, le sélecteur de date borné à aujourd'hui, et le bouton qui s'active — rien de tout cela n'a tourné ailleurs que dans un compilateur. Et le journal n'a jamais été lu depuis une vraie base contenant des mois de pesées.

## D95 — L'ajustement se propose, ne s'applique jamais, et se tait presque toujours · ✓ validée

**Contexte.** Le dernier morceau de la tranche 7. [03](03-nutrition-calculs.md#adaptation-hebdomadaire) décrit le mécanisme « qui fait qu'un objectif reste juste au bout de trois mois » ; [12](12-plan-de-developpement.md) en fait deux critères de fin : *aucune suggestion n'est appliquée sans accord explicite*, et *les conditions de déclenchement — adhérence, persistance, nombre de pesées — sont testées*.

### Le silence est le cas normal, et c'est la propriété qui structure le code

Cinq conditions doivent être réunies pour qu'une carte paraisse. Le cas d'usage rend `null` presque toujours, et chaque `null` a sa raison — désactivé, réponse récente, journal troué, pas de cap annoncé, écart non persistant, correction absorbée. Écrire cela comme une suite de gardes plutôt que comme un calcul avec des exceptions rend chaque condition **retirable une par une**, ce qui est exactement ce que la campagne de défaite demande.

### La pente visée ne se recalcule pas, et le seuil se juge deux fois

L'écart est `pente visée − pente réelle` ([D94](#d94--le-journal-de-poids-se-lit-en-trois-traits-et-la-tendance-se-tait-sous-trois-pesées---validée) tient la première, la moyenne mobile la seconde), et il doit dépasser 0,15 kg/semaine **deux semaines de suite**.

**Et dans le même sens** — ce que [03](03-nutrition-calculs.md#conditions-de-déclenchement) ne dit pas explicitement, mais qui est la seule lecture cohérente de « persistant ». Une semaine trop lente suivie d'une semaine trop rapide est une **oscillation**, pas une dérive ; la corriger l'amplifierait, ce qui est précisément ce que la borne de ±150 kcal existe pour éviter. Le cas est écrit et le sabotage tombe dessus.

### Ce que la carte annonce est ce qui sera écrit

La correction repasse par **tous** les garde-fous et par la répartition des macros **avant** d'être affichée, et la suggestion transporte l'objectif complet. C'est la règle de [ReviseGoal](#d60--un-objectif-est-calculé-ou-saisi-et-on-consulte-avant-de-corriger---validée), et elle compte doublement ici : la carte dit « réduire de 150 kcal », et accepter doit donner exactement cela.

Ce n'est pas théorique. Le premier décor de test posait un objectif de 2 200 kcal pour un profil qui en dépense 2 885 : la correction de 150 franchissait la borne des 25 % d'écart, et la valeur écrite aurait été 36 kcal en dessous de l'annonce. Deux cas gardent maintenant cette frontière — l'un où le garde-fou rabote, l'autre où il absorbe tout et la suggestion disparaît.

### Trois issues, un seul cas d'usage

`RespondToAdjustment` porte *Accepter*, *Ignorer* et *Ne plus proposer* ensemble, et c'est ce qui rend l'invariant visible : **accepter est le seul chemin qui écrit un objectif**. Trois classes auraient rangé cette propriété nulle part, et le critère de fin de tranche n'aurait eu aucun endroit où se tester.

Accepter ouvre une version, il n'en modifie aucune ([D04](#d04--objectifs-versionnés-plutôt-que-mis-à-jour-en-place---validée)) ; le cap — stratégie, poids visé, échéance — ne bouge pas, seuls les six chiffres changent, et `GoalOrigin.ADJUSTMENT` dit d'où ils viennent. L'objectif s'écrit **avant** le réglage : dans l'autre ordre, une écriture qui échoue laisserait l'adaptation muette pour deux semaines sans avoir rien corrigé.

**Accepter et ignorer imposent le même silence de deux semaines, pour deux raisons différentes.** Après un ajustement accepté, il faut ce délai pour que la moyenne mobile reflète le nouvel objectif — corriger avant reviendrait à corriger une correction qu'on n'a pas encore mesurée. Après un refus, reproposer le lendemain transformerait la carte en insistance. Les deux dates sont **rangées séparément** malgré le délai commun : un refus n'est pas un ajustement, et les fondre ferait dire à l'historique qu'un objectif a été corrigé un jour où l'utilisateur avait refusé.

### La carte vit dans le design system, parce que deux écrans la portent

Le journal de poids et l'accueil, comme [03](03-nutrition-calculs.md#restitution) le demande. Un `:feature` ne dépend jamais d'un autre ; la recopier aurait donné deux cartes qui divergent au premier mot changé. Elle rejoint `MacroBar` et `SourceBadge`, qui connaissent déjà le vocabulaire du domaine.

**Le rythme y est écrit signé** — « −0,2 kg par semaine » — plutôt que raconté. La phrase de [02](02-parcours-et-ecrans.md#journal-de-poids), « vous perdez 0,3 kg par semaine, pour 0,5 visé », se lit mieux mais devient fausse dès que les deux rythmes n'ont pas le même sens : quelqu'un qui vise une prise et qui perd du poids lirait exactement l'inverse de sa situation. Le sens de la **correction**, lui, reste dit par un verbe — *réduire* ou *augmenter* — parce que c'est la phrase sur laquelle on appuie.

### Un fichier de réglages par sujet rangé

`AiSettingsModule` a franchi le seuil de fonctions en accueillant l'adaptation. Il s'est scindé en trois — IA, contribution, ajustement — et le découpage suit ce que les choses sont : chacun de ces sujets avait déjà **son propre fichier de préférences**, pour la raison écrite en [D86](#d86--la-liste-des-favoris-devient-lendroit-où-on-les-gère-et-le-numéro-ne-recule-pas---validée). Le module le dit maintenant aussi.

**Campagne de défaite : vingt-trois sabotages, vingt-trois cas tombés** — après avoir rendu une règle éprouvable.

La borne de la fenêtre d'adhérence survivait à sa propre suppression : le cas d'usage lit déjà le journal borné à quatorze jours, donc rien de trop vieux ne pouvait lui parvenir, et la borne de la règle ne servait qu'à se répéter. Une règle qu'aucun appelant ne peut mettre en défaut n'est pas éprouvée par lui — les trois règles pures ont désormais leurs propres cas, en plus de ceux du cas d'usage.

**Conséquences.** Le domaine gagne un port (`AdjustmentSettings`) et deux cas d'usage. `HomeViewModel` et `WeightViewModel` exposent chacun la suggestion, hors de leur état d'écran : le journal peut être illisible pendant que la suggestion tient. La carte n'est montrée que par l'accueil et par le journal de poids — l'écran Journée partage le modèle de l'accueil, mais une carte qui parle de trois semaines n'a rien à faire au-dessus d'un jour passé qu'on est venu relire.

**Ce que le vert ne prouve pas.** **Que la carte se voit un jour.** Toutes les conditions réunies demandent trois semaines de pesées régulières et dix jours notés sur quatorze : aucun test ne remplace le fait de vivre la situation, et le premier vrai déclenchement dira si le seuil de 0,15 kg/semaine est bien placé ou s'il parle trop, ou trop peu.

Rien de ce qui se touche non plus : les trois boutons, la disparition de la carte au moment de la réponse, et le fait qu'elle ne réapparaisse pas au prochain lancement n'ont tourné que dans des `StateFlow` de test. Et la chaîne complète — répondre, écrire l'objectif, voir les six compteurs de l'accueil changer — n'a jamais été parcourue sur un appareil.

## D96 — La sauvegarde emprunte les mappeurs, et le format ne porte que ce que la base tient · ✓ validée

**Contexte.** Tranche 8. [09](09-donnees-et-sauvegarde.md#format-de-sauvegarde) décrit un JSON compressé, versionné, avec sa chaîne de migrations ; [12](12-plan-de-developpement.md) pose trois critères de fin, et l'**ordre volontaire** : le fichier local avant Drive.

### Drive attend un projet Google Cloud, le reste n'attend rien

Un identifiant OAuth, une empreinte SHA-1 enregistrée, un compte réel pour éprouver l'envoi : rien de tout cela ne se fabrique depuis le dépôt. Cette livraison est donc **tout ce qui ne dépend pas d'OAuth** — le format, ses migrations, les trois ports, le magasin Room, la cible interne, et les cas d'usage. Le troisième critère de fin (« Drive et le fichier local sont deux implémentations du même port, interchangeables ») reste ouvert, mais le port existe et une implémentation l'éprouve.

C'est l'ordre que [12](12-plan-de-developpement.md) appelle déjà volontaire, et la raison qu'elle en donne tient : *« il valide tout le format sans dépendre d'OAuth, et c'est lui qui garantit la réversibilité du projet »*.

### Un second jeu de mappeurs aurait corrompu de vraies données

`RoomSnapshotStore` touche huit tables. Écrire sa propre traduction entre Room et le domaine aurait donné **deux traductions de la même chose**, et leur divergence n'aurait pas produit un affichage bizarre : la sauvegarde aurait écrit des lignes que l'application relit de travers.

Les mappeurs de `:data:diary`, `:data:food` et `:data:profile` passent donc de `internal` à publics, et `:data:backup` dépend des trois. Un `:data` qui dépend d'autres `:data` est une première ; elle se paie une fois, contre une classe de fautes silencieuses.

**Et le contrat l'éprouve** : il écrit par les **vrais** dépôts, capture, efface, restaure, puis relit par les vrais dépôts. Si l'emprunt cessait un jour d'en être un, ce cas tomberait.

### Le format ne porte pas ce que la table ne stocke pas

Le premier passage du contrat a échoué sur une fiche Open Food Facts : ses portions nommées et ses teneurs complétées revenaient vides. La table `food` **n'a pas ces colonnes** — ce sont des propriétés de la référence, relues dans la base de l'ANSES par le code de la fiche, exactement comme le rayon et le titre court ([D54](#d54--un-bandeau-de-rayons-et-deux-familles-qui-ne-se-combinent-pas-pareil---validée)).

Les écrire dans le fichier aurait mis des listes toujours vides, ou pire, figé la correspondance du jour de l'export. Elles ont quitté le format.

*(Cela met au jour un défaut préexistant : les portions nommées d'un produit scanné ne survivent pas à son premier usage. Hors sujet ici, mais signalé.)*

### Ce que le fichier ne porte pas non plus

**Aucune clé d'API** — contrainte ferme, et deux cas la tiennent. Le premier écrit une clé **là où elle vit vraiment**, dans le fichier de préférences de l'IA, puis cherche la chaîne dans les octets produits. Le second est le plus utile des deux : il énumère les **sections de premier niveau** du fichier et tombe dès qu'une apparaît ou disparaît. Le vrai risque n'est pas qu'une clé s'y trouve aujourd'hui, c'est qu'une section s'y ajoute demain sans que personne y pense.

**Pas d'identifiant d'appareil**, que [09](09-donnees-et-sauvegarde.md#format-de-sauvegarde) prévoyait : rien ne le lit, et un fichier que l'utilisateur peut envoyer par courriel n'a pas besoin de porter de quoi relier deux exports au même téléphone.

**Tout le catalogue local, en revanche**, y compris les fiches de l'ANSES. [09](09-donnees-et-sauvegarde.md) voulait n'en garder que le code ; mais une fiche de l'ANSES n'est copiée localement qu'à son premier usage et reçoit alors un identifiant propre à l'installation. L'exclure romprait le lien que chaque ligne de journal tient vers elle. Le filtrage qu'elle visait — rester sous 400 Ko — est déjà obtenu autrement : le catalogue local ne contient que ce qui a servi.

**L'état de l'adaptation hebdomadaire, si.** C'est une décision de l'utilisateur, pas un réglage d'appareil : qui vient de répondre « ne plus proposer » ne doit pas revoir la carte parce qu'il a changé de téléphone.

### Des DTO, et une chaîne de migrations vide

Un `@Serializable` posé sur `Dish` ou `Food` aurait lié le format aux noms de champs du code : renommer une propriété aurait rendu illisible toute sauvegarde antérieure, sans que rien ne le signale. Les DTO coûtent un mappeur ; ils achètent que le format soit une décision et non une conséquence.

La chaîne de migrations est **vide et existe quand même**. La première migration s'écrit toujours dans l'urgence d'un changement, et l'endroit où la mettre doit déjà exister — sans quoi elle finit dans un `if` du lecteur, puis un second, et on ne sait plus dans quel ordre ils s'appliquent.

Le JSON est **indenté** malgré le gzip : « réparable à la main » veut dire qu'on peut ouvrir le fichier décompressé dans un éditeur, et une seule ligne de deux cent mille caractères ne se répare pas. La compression efface le coût.

### Vider est un balayage, pas huit méthodes

Room aurait demandé un `@Query("DELETE FROM …")` par table — huit déclarations qui disent la même chose, et un DAO de plus sur une base qui en compte déjà dix. `eraseUserData()` tient en une liste, et cette liste **est** la question qu'on se pose en la relisant : qu'est-ce qui appartient à l'utilisateur ? Les deux tables en `CASCADE` n'y figurent pas, et leur absence dit que la cascade existe.

### Une date de fichier n'est pas une date

Le contrat de la cible interne a montré que `lastModified()` a une granularité que deux écritures rapprochées franchissent parfois ensemble : la rotation triait alors dans l'ordre du système de fichiers, c'est-à-dire qu'elle pouvait **supprimer la sauvegarde la plus récente en croyant retirer la plus vieille**. L'instant se lit désormais dans le nom, que l'application a écrit et qui survit à une copie.

**Conséquences.** Un module (`:data:backup`), un port de plus dans `:core:database` scindé en deux DAO, trois cas d'usage, et vingt-huit cas. `EraseEverything` est le seul endroit du projet qui touche tous les secrets à la fois.

**Ce que le vert ne prouve pas.** **Rien de ce qui se fait.** Aucun écran n'appelle encore quoi que ce soit : ni export, ni import, ni bouton d'effacement. Le Storage Access Framework, la boîte de confirmation, la saisie du mot `SUPPRIMER` — tout cela vient dans la livraison suivante, et c'est le même ordre que la tranche 5, où le client a précédé l'écran.

Et le format n'a jamais rencontré de vraies données : le contrat travaille sur un décor de sept lignes, pas sur un an de journal. La taille du fichier, le temps de la capture et celui de la restauration sont des inconnues.

## D97 — Un nom d'aliment se replie sur deux lignes, et ne contient jamais de retour à la ligne · ✓ validée

**Contexte.** Rapporté à l'usage : *« le titre des aliments est parfois trop long, et impossible de scroller pour voir le début — on voit la fin du titre, et la seule manière de voir le début est de supprimer la fin. »*

### Le curseur était à la fin, et le champ ne montrait qu'une ligne

Deux causes qui se cumulent, et la description les nomme exactement.

`DraftTextField` pose le curseur à la fin du texte initial — c'est ce qu'on veut quand on vient corriger une valeur. Mais le champ était aussi `singleLine`, ce qui le fait **défiler horizontalement** au lieu de replier : à l'ouverture, il se cale sur le curseur, donc sur la queue du libellé. « Fromage blanc nature, 3% de matière grasse » s'ouvrait sur « de matière grasse ».

Le champ montre désormais **deux lignes** quand il en faut deux. Le début redevient visible sans rien effacer.

**Deux et pas trois** : deux suffisent à la quasi-totalité des libellés de l'ANSES, et une troisième repousserait les six valeurs hors de l'écran sur un téléphone étroit — on lirait le titre en entier au prix de ce qu'on est venu vérifier.

### `maxLines` est un troisième réglage, distinct des deux autres

`minLines` décidait déjà de la hauteur au repos **et** de ce que fait la touche entrée. Y ajouter le repli aurait fait qu'un champ de nom à deux lignes hérite du comportement d'une description de repas, où entrée saute une ligne. Trois questions, trois réglages.

### Un champ d'une ligne refuse le retour à la ligne, même quand il en montre deux

Replié, le champ n'est plus `singleLine` pour Compose : son clavier propose donc une touche entrée. Un nom d'aliment coupé en deux par un saut de ligne se retrouverait tel quel dans le journal, puis dans une sauvegarde, puis dans une recherche qui ne le trouve plus.

Le champ **refuse** la frappe plutôt que de l'accepter puis de la nettoyer — c'est la règle de `isNumberField`, et pour la même raison : nettoyer obligerait à réécrire le texte affiché, donc à repositionner le curseur, exactement le défaut que [D45](#d45--un-champ-de-saisie-tient-son-texte-lui-même---validée) évite.

### La cause de fond n'est pas là

Les libellés de l'ANSES sont longs parce que **le passage de titres courts n'a pas encore tourné** : la base embarquée en compte **6 sur 3 484**. `./gradlew generateShortNames` ([D88](#d88--le-titre-court-se-fabrique-hors-de-lapplication-et-il-ne-touche-jamais-au-libellé---validée)) est ce qui les raccourcira ; le repli sur deux lignes rend seulement lisible ce qui reste long après.

**Campagne de défaite : un sabotage, un cas tombé** — et deux règles qui ne s'éprouvent pas.

**Ce que le vert ne prouve pas.** **Le repli lui-même.** `singleLine`, `maxLines` et la hauteur du champ sont du rendu Compose : sabotés, aucun cas ne tombe, parce qu'aucun test de ce projet ne dessine. Seule la règle du retour à la ligne s'affirme, et c'est elle qui est éprouvée. Le reste se vérifie en tenant le téléphone.

Rien ne dit non plus qu'un libellé de trois lignes se lise mieux qu'avant : il montrera ses lignes 2 et 3, le curseur étant toujours à la fin.

## D98 — Le modèle doit se prononcer sur les six valeurs, et « inconnu » s'écrit · ✓ validée

**Contexte.** Rapporté à l'usage, avec Gemini : *« quand l'IA propose un aliment — ici un mangoustan — “proposé par l'IA, confiance 95 %, quantité estimée, valeurs estimées par IA”, je n'ai ni fibre, ni glucide, ni lipide. »*

### Ce n'était pas la reconnaissance, c'était l'estimation

Deux appels, deux règles opposées, et il fallait savoir lequel avait parlé.

La **reconnaissance** ne produit aucun chiffre, et c'est écrit dans son prompt : *« Ces chiffres viennent de bases traçables, pas de toi. »* Elle rend un nom, une quantité, une unité. C'est toute la raison d'être de la résolution.

L'**estimation** est le seul chemin autorisé à produire des valeurs, et il ne sert qu'aux libellés que le catalogue n'a pas rejoints — un mangoustan n'est pas dans la table de l'ANSES. La mention « valeurs estimées par IA » dit précisément que ce chemin-là a répondu. Le défaut était donc chez lui.

### La permission de se taire est devenue un silence par défaut

Le schéma d'estimation ne rendait obligatoire que le libellé. C'était délibéré, et le raisonnement tenait : *« un modèle qui ne connaît pas les fibres d'un plat doit pouvoir se taire sur cette valeur plutôt que d'en inventer une pour satisfaire un schéma. »*

**L'effet, lui, ne tenait pas.** Le décodage contraint de Gemini lit « pas dans `required` » comme « facultatif », et se tait par défaut. Un mangoustan — dont n'importe quel modèle connaît la composition — revenait sans glucides, sans lipides et sans fibres. La ligne était inutilisable, arrivée par le seul chemin qui avait le droit de la remplir.

**Exiger la clé tout en autorisant `null` dit exactement ce qu'on voulait dire.** Le modèle doit se prononcer sur chacune des six ; « je ne sais pas » reste exprimable ; le silence par omission ne l'est plus. C'est la règle la plus ancienne du projet — `null` veut dire inconnu, jamais zéro — enfin imposée au fournisseur au lieu d'être seulement espérée de lui.

Se taire sur un aliment **entier** reste possible, et c'est le prompt qui le porte : un libellé dont le modèle ne sait rien s'omet de la réponse. Un aliment est tout ou rien ; une valeur est une réponse ou un `null`.

### Deux dialectes pour dire la même chose

Anthropic lit du JSON Schema, où l'union de types est canonique : `"type": ["number", "null"]`. Gemini lit un sous-ensemble d'OpenAPI 3.0 qui ne connaît pas l'union et exige `"nullable": true`. Envoyer la forme de l'un à l'autre fait refuser la requête — ou pire, ignorer la contrainte en silence.

C'est la **seconde** différence entre les deux dialectes, après `additionalProperties`. Le paramètre `strict` les porte toutes les deux plutôt que d'ouvrir un second drapeau qui dirait la même chose.

### Une version par prompt

`PROMPT_VERSION` était unique pour les deux textes. Corriger l'estimation aurait renuméroté l'extraction, qui n'a pas bougé — et un enregistrement d'usage aurait annoncé un changement là où il n'y en avait pas. Deux textes, deux questions, deux compteurs : `extract_fr_v1` et `estimate_fr_v2`.

**Campagne de défaite : huit sabotages, huit cas tombés.** Le libellé seul exigé, les fibres redevenues facultatives, plus aucune valeur nullable, chaque dialecte recevant la forme de l'autre, `additionalProperties` envoyé à Gemini, le libellé rendu nullable, et une valeur inconnue retombant sur zéro.

**Conséquences.** Un schéma ne se vérifie pas à la lecture : il part sur le réseau et c'est le fournisseur qui l'applique. Les cinq cas de `EstimationSchemaTest` affirment donc sa **forme**, dans les deux dialectes — c'est ce qui reste testable, et c'est ce qui tombe si quelqu'un rétablit l'ancienne permission.

**Ce que le vert ne prouve pas.** **Que Gemini obéisse.** Aucun test n'appelle un fournisseur : le schéma est affirmé, son effet est supposé. La preuve est un mangoustan qui revient avec ses six valeurs, et elle demande une vraie clé.

Rien ne dit non plus que les valeurs estimées soient **justes** — elles ne l'ont jamais été et ne le seront jamais : ce sont des estimations, marquées comme telles par un contour en pointillés ([D25](#d25--lestimation-ia-se-signale-par-une-forme-pas-par-une-couleur---validée)). Ce que cette correction achète est qu'elles existent.

## D99 — Les trous de CIQUAL sont comblés, et l'énergie ne s'y devine jamais · ✓ validée

**Contexte.** [D89](#d89--une-valeur-complétée-ne-se-range-pas-où-une-valeur-mesurée-se-range---validée) a bâti la chaîne de complétion et l'a laissée vide : 553 valeurs attendaient que la passe tourne. Charly a demandé qu'elle tourne.

### Elle a tourné ici, sans clé et sans réseau

`generateCompletions` appelle un fournisseur avec la clé de l'utilisateur. Je n'en ai pas, et la contrainte du projet interdit d'en écrire une où que ce soit. **Le modèle qui a produit ces valeurs est celui qui écrit ce texte** — le résultat est le même artefact : un CSV versionné, relu, corrigeable à la main, et repris tel quel par `importCiqual`.

Ce que cela change : la tâche reste là, elle sert toujours pour la prochaine édition de l'ANSES, et rien de son contrat n'a bougé. Ce que cela ne change pas : ces valeurs sont **des estimations**, elles vivent dans les colonnes `_est`, et l'écran les marque une par une.

### L'énergie se calcule, elle ne s'estime pas

Sur les 555 trous, **143 sont énergétiques** — et une valeur énergétique n'a aucune raison d'être devinée quand les quatre teneurs qui la déterminent sont connues. Le règlement UE 1169/2011 la donne : `4×protéines + 4×glucides + 9×lipides + 2×fibres`, la formule que l'application applique déjà ([D87](#d87--les-calories-se-proposent-et-une-valeur-minorée-se-dit-au-lieu-de-se-taire---validée)).

L'ordre du travail suit donc de là : **estimer les cinq autres d'abord, dériver l'énergie ensuite**. Aucune des 143 n'a été devinée — 57 étaient calculables depuis les valeurs publiées, les 86 restantes le sont devenues une fois leurs macros complétées. Une énergie estimée à côté de macros estimées aurait été deux fois inventée, et incohérente avec elles.

### Ce que les estimations suivent

Quatre règles, dans cet ordre :

- **La chair animale ne contient pas de sucres.** Viande, poisson, abats : le glycogène résiduel n'en est pas un au sens du règlement, et les glucides déclarés en charcuterie sont le dextrose de la saumure. C'est la moitié des 223 sucres manquants, et ce ne sont pas des devinettes.
- **Les amidons purs non plus** — fécule, farine, riz.
- **Un fruit, à l'inverse, est presque entièrement du sucre** : ses glucides et ses sucres se suivent à quelques dixièmes près.
- **Une herbe séchée n'est pas un fruit séché** : ses glucides sont surtout des fibres et de l'amidon, et ses sucres restent une petite fraction.

Le reste — 86 fiches d'Outre-mer, les algues, les champignons — a été estimé fiche par fiche.

### Deux vérifications avant d'écrire

Le script d'assemblage refuse d'écrire s'il trouve : une teneur que l'ANSES publie déjà, un trou non comblé, des sucres supérieurs aux glucides, ou une valeur hors des bornes. Les trois premières sont exactement ce que `CompletionsCsv` refuse à l'import ; les avoir en amont évite de découvrir la faute après avoir régénéré une base d'un mégaoctet.

**Le premier passage a mordu deux fois** : deux anchois dont les sucres estimés dépassaient de deux centièmes les glucides publiés.

### Ce qui est déjà dans le fichier ne bouge jamais

Le premier assemblage a **écrasé une ligne écrite à la main** — les 39 kcal des câpres, recalculées à 38. C'est précisément le contrat que la chaîne promet : *une correction survit à la génération suivante, qui ne redemande que les trous absents de ce fichier*. Le script relit désormais le fichier et ne réécrit aucune paire qu'il y trouve.

### La révision de la base de référence était une règle incomplète

`CiqualDatabase.REVISION` déclenche la recopie sur un appareil déjà installé. Sa consigne disait de l'incrémenter quand le **schéma** change, ou quand un rayon est réarbitré. Elle ne disait rien des deux CSV — qui ne touchent pas au schéma mais au contenu, **avec exactement la même conséquence** : un appareil déjà installé aurait gardé une copie sans les valeurs complétées, et rien ne l'aurait dit. L'oubli est d'autant plus facile que la base se régénère ici sans erreur.

**Conséquences.** `ciqual.db` ne porte plus **aucune** valeur inconnue : 143 énergies, 223 sucres, 70 glucides, 70 fibres, 29 protéines et 20 lipides sont désormais lisibles, toutes dans les colonnes `_est`. Les 3 484 fiches de la table sont enregistrables sans saisie manuelle. La révision passe à 5.

**Ce que le vert ne prouve pas.** **Que ces chiffres soient justes.** Ce sont des estimations, et une estimation plausible est indiscernable d'une estimation fausse tant qu'on ne la confronte pas à une mesure. Ce qui est vérifié est leur **cohérence** — sucres sous glucides, énergie conforme au règlement, bornes respectées, aucune mesure écrasée — et leur **existence**, qui est ce qui manquait.

Les plus incertaines sont les 86 fiches relevées à la Martinique : jus de moubin, massissi, kamanioc, ti nain. Elles sont peu documentées ailleurs, et ce sont celles qu'il faudra corriger en premier si un chiffre paraît faux.

## D100 — Un titre court se construit, et deux fiches voisines n'en partagent jamais un · ✓ validée

**Contexte.** [D88](#d88--le-titre-court-se-fabrique-hors-de-lapplication-et-il-ne-touche-jamais-au-libellé---validée) a bâti la chaîne et l'a laissée à six titres écrits à la main sur 3 484 fiches. C'est la cause de fond du défaut que [D97](#d97--un-nom-daliment-se-replie-sur-deux-lignes-et-ne-contient-jamais-de-retour-à-la-ligne---validée) n'a fait que rendre lisible : les libellés sont longs parce que la passe n'avait jamais tourné.

**1 289 titres écrits.** La longueur moyenne de ce qui s'affiche tombe de **41,4 à 30,7 caractères**.

### Trois règles, et ce qu'elles refusent de faire

Le libellé de l'ANSES est un nom suivi de qualificatifs, du plus au moins distinctif — « Poulet, blanc, sans peau, cuit au four, sans matière grasse ajoutée ». La réduction suit cette structure :

- **le nom reste en tête et ne se coupe jamais** ;
- **l'état — cru, cuit, séché, surgelé — se garde même quand la place manque.** C'est le qualificatif qui distingue le plus souvent deux fiches voisines, et le laisser tomber par simple troncature à droite produisait « Coquille Saint-Jacques » deux fois, pour la crue et la cuite ;
- **le reste tombe de la droite vers la gauche**, après que le bruit a été retiré — « préemballé », « fait maison », « aliment moyen », « prélevé à la Martinique » ne distinguent jamais rien.

### Deux fiches voisines ne portent jamais le même titre

C'est la propriété qui compte le plus, et elle ne se voit **qu'en comparant les titres entre eux** — jamais en relisant un titre seul. Une liste de résultats où « Abricot au sirop léger » apparaît deux fois est plus illisible qu'une liste de libellés longs : on a remplacé une phrase trop longue par une ambiguïté.

Le premier jet en produisait **143 groupes, sur 372 fiches**. Chacune récupère désormais le mot qui la distingue — égoutté contre non égoutté, rôtie contre grillée — quitte à raccourcir la tête.

**Et jamais avec du bruit.** La première désambiguïsation allait chercher n'importe quel segment différent, ce qui a donné « Feuilleté, aliment moyen ». Un mot qui ne distingue rien ne peut pas départager : il est écarté de la recherche.

### Ce qui ne se départage pas n'obtient rien

**796 fiches restent sans titre**, et c'est un choix plutôt qu'un manque :

- **383 ont un libellé qui tient déjà en quarante caractères.** Le seuil de génération est à trente ; entre les deux, il n'y a rien à raccourcir.
- **402 ont un libellé long que les règles n'ont pas su réduire** sans le rendre faux ou ambigu — « Spécialité fromagère fondue aromatisée en cubes pour l'apéritif », « Bouchée apéritive de fromage au lait de vache non affiné, fourrée ou enrobée ».

Ces 402 gardent leur libellé, c'est-à-dire l'état d'avant : **une absence de titre n'est pas une régression**, et la chaîne ne redemande que les codes absents du fichier. Une passe suivante les reprendra.

### Ce qui est déjà dans le fichier ne bouge jamais — la deuxième fois

La même faute qu'avec les complétions, et corrigée de la même façon : le premier assemblage a effacé les six titres écrits à la main. Le script relit le fichier avant d'écrire.

**Conséquences.** `short-names.csv` passe de 6 à 1 289 lignes ; la révision de la base de référence passe à 6.

**Ce que le vert ne prouve pas.** **Qu'un titre soit bon.** Ce qui est vérifié est mécanique — quarante caractères, plus court que le libellé, aucun doublon, aucun artefact de ponctuation — et l'échantillon relu se lit bien. Mais « Fromage frais, allégée en matière grasse » garde l'accord féminin du libellé d'origine, et « Eau minérale Nessel, embouteillée » a perdu le mot « gazeuse » faute d'une fiche voisine pour l'exiger. Ce sont des titres corrects, pas des titres écrits.

Rien ne dit non plus que le raccourci **aide** : la longueur moyenne tombe d'un quart, mais la lisibilité d'une liste de résultats se juge sur un téléphone, pas sur une moyenne.

---

## D101 — L'accueil porte une date, et l'écran Journée disparaît · ✓ validée

**Contexte.** Cinq corrections rapportées à l'usage, dont deux qui n'en faisaient qu'une : *« le calendrier doit rester visible quand on se balade dans l'historique »* et *« l'utilisateur doit pouvoir toujours ajouter des plats sur les jours précédents en cas d'oubli »*.

### Un seul écran, avec une date

Les deux demandes poussaient au même endroit. Un second écran ne peut pas garder le calendrier à l'écran — il le laisse derrière lui — et un écran de lecture seule ne peut pas recevoir un plat oublié.

L'écran Journée disparaît donc, et l'accueil porte une date. Toucher une pastille ne navigue plus : elle change la date **sur place**, le calendrier ne bouge pas, les six compteurs et la liste se rechargent, et le bouton d'ajout écrit sur le jour affiché.

Ce n'était pas une perte : [D92](#d92--une-journée-sans-saisie-na-pas-de-ligne-et-cest-structurel---validée) avait déjà noté que l'écran Journée était *« structurellement identique à l'accueil »* et partageait son modèle pour ne pas diverger. Il ne restait de lui que trois différences — un titre, une croix, et l'absence du bouton d'ajout — dont les deux dernières viennent d'être annulées par la demande.

### Le jour voyage par un port, pas par la navigation

Le jour regardé doit atteindre l'**écran de validation**, qui n'est ouvert ni par l'accueil ni directement : entre les deux il y a la recherche, un scan, ou une modale d'IA. Le faire voyager demanderait de le déclarer sur quatre routes dont aucune ne s'en sert.

C'est le choix déjà fait pour la proposition d'un modèle ([D80](#d80--la-proposition-passe-par-un-dépôt-et-lécran-de-validation-ne-change-pas-dun-mot---validée)) : elle attend dans un dépôt plutôt que de traverser le graphe.

**`null` veut dire aujourd'hui, et ce n'est pas la même chose que la date du jour.** Une application laissée ouverte pendant la nuit doit basculer sur la journée neuve ; y ranger la date d'aujourd'hui la ferait afficher la veille jusqu'à ce que quelqu'un touche une pastille. La normalisation vit dans le port et non dans l'écran : c'est une propriété du jour regardé, pas du geste qui le change.

**Rien ne le persiste.** Rouvrir l'application montre aujourd'hui — retrouver un jour d'octobre parce qu'on l'y avait laissé trois semaines plus tôt ferait noter un repas au mauvais endroit sans qu'aucun écran n'ait menti.

### La même ligne, deux tons

Écrire sur un jour passé ouvre un risque : croire qu'on écrit aujourd'hui. L'écran de validation **affichait déjà la date**, en petit gris à côté du badge de source — c'est-à-dire à l'endroit où on ne la lit pas.

Elle ne change pas de place, elle change de ton : discrète pour aujourd'hui, en corps de texte et en encre pleine pour un autre jour, où elle dit *« Sera noté sur le mardi 18 août »*. Une seconde ligne aurait répété ce que la première disait déjà ; une boîte de confirmation aurait pénalisé le cas volontaire, qui est le seul qui existe vraiment.

### Le repli suit le doigt

Déplié, un glissement vers le haut replie le calendrier, **et le même doigt continue** dans la page. Le delta qui déclenche le repli est consommé : sans cela la page se déplacerait pendant que la hauteur s'anime, et le contenu ferait un bond.

**L'état de repli vit dans l'écran, pas dans le calendrier.** C'est l'accueil qui reçoit le défilement, donc lui seul peut replier ce qui est déployé. Le panneau le reçoit en paramètre — l'accueil sait qu'il porte un en-tête escamotable, pas qu'il s'agit d'un calendrier.

### Les pastilles étaient ovales, et deux comptes en étaient la cause

Le calcul du diamètre réservait `4 dp × 8` de marges ; `DayCell` en dépensait **deux par cellule**, soit `4 dp × 14`. La largeur demandée dépassait la place, le parent la rognait, et la hauteur — que rien ne contraignait — restait entière.

**Une pastille plus haute que large est le symptôme d'une largeur refusée, jamais d'un dessin ovale** : l'anneau a toujours été rond.

La première correction fut de mettre les deux comptes d'accord, par une constante partagée. **La campagne de défaite a refusé cette réponse** : écarter la cellule de la constante ne faisait tomber aucun cas, parce qu'aucun cas ne peut voir ce qu'une composable dépense. Une règle qui tient sur la bonne volonté de deux endroits n'est pas une règle.

Il n'y a donc plus qu'un compte. `cellFootprint` divise la largeur par sept — **une place, pas un diamètre** — et la cellule la prend telle quelle. `ringDiameter` retire les marges de cette place et borne le résultat, et c'est le seul endroit du code où une marge est soustraite. La cellule ne calcule plus rien : elle consomme.

L'anneau reçoit en outre un rapport de forme, pour qu'une erreur de dimension se voie désormais comme un rétrécissement et non comme une déformation.

**Le cas qui vérifiait cela encodait la même erreur que le code** — il comptait huit intervalles. Il passait donc pendant que les pastilles étaient écrasées à l'écran. Un cas qui reprend l'arithmétique de ce qu'il vérifie ne vérifie rien. Il affirme maintenant une **égalité** sur sept largeurs, de l'écran le plus étroit à la tablette : trop petit gaspille la place, trop grand la dépasse, et les deux tombent.

### Une unité qui s'appliquait sans se voir

« 1 bol » se choisissait à la saisie puis disparaissait du sélecteur en rouvrant le plat — alors que la quantité restait juste. Rouvrir reconstruit bien l'unité depuis ce qui a été écrit, mais **sans relire la fiche**, donc sans ses portions : le sélecteur ne proposait que les grammes et les millilitres, et ne pouvait pas montrer comme choisie une unité absente de sa propre liste.

La règle manquante : **l'unité qu'une ligne porte fait toujours partie de celles qu'elle propose.** La comparaison porte sur le code et non sur l'unité entière — une portion qui aurait changé de poids depuis apparaîtrait deux fois sous le même nom.

**Campagne de défaite : onze sabotages, dix cas tombés, un hors de portée.**

Celui qui reste debout : écarter la cellule de la place qu'on lui donne — `.size(footprint)` devenu `.size(footprint * 2)`. Aucun test unitaire ne mesure une composable, et le projet n'a pas d'infrastructure de test Compose. Ce qui a été fait à la place est structurel : la cellule ne calcule plus rien, donc il n'y a plus deux nombres à faire diverger — seulement un nombre à recopier. Le risque restant n'est plus une erreur d'arithmétique, c'est une faute de frappe.

**Conséquences.** `DayScreen` et `DayDestination` disparaissent ; le graphe n'a plus qu'une destination pour le journal. `SelectedDay` est un port de plus, implémenté dans `:core:common` à côté de l'horloge — c'est un état d'application, pas un domaine de données. `CreateDraft` prend une dépendance de plus. `HomeScreen` et `EntryScreen` se scindent chacun en deux fichiers, le titre d'un côté, le contenu de l'autre.

**Ce que le vert ne prouve pas.** **Le geste.** Le repli au défilement, la continuité du doigt, la disparition du bond — rien de cela ne s'éprouve sans tenir le téléphone. Le `NestedScrollConnection` est écrit contre la documentation, et sa consommation du premier delta est un pari sur ce qui se sent bien.

Rien ne dit non plus que les pastilles soient **jolies** à leur nouvelle taille : les cas affirment qu'elles tiennent, pas qu'elles se lisent. Et le retour du système qui ramène à aujourd'hui plutôt que de quitter l'application n'a tourné dans aucun test — `BackHandler` est une intégration Android.

**Deux défauts de mise en page de suite ont échappé au vert** — le titre qui débordait ([D97](#d97--un-nom-daliment-se-replie-sur-deux-lignes-et-ne-contient-jamais-de-retour-à-la-ligne---validée)) et les pastilles ovales — et dans les deux cas la suite était verte pendant que l'écran était faux. Une infrastructure de test Compose mettrait ces règles à portée d'un cas ; elle n'entre pas dans cinq corrections, mais le constat est posé.

---

## D102 — Le retour à aujourd'hui a une porte visible, et le jour regardé a un contrat · ✓ validée

**Contexte.** [D101](#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée) a donné une date à l'accueil, et n'a laissé qu'un chemin pour en revenir : le bouton retour du système.

### Un geste ne peut pas être le seul chemin

C'est une règle que le projet applique déjà, et qu'il a écrite pour le balayage qui supprime une ligne : *« un geste sans représentation visible est introuvable pour qui ne le connaît pas, hors d'atteinte au lecteur d'écran, et difficile pour une main qui tient mal le téléphone »*. Le bouton retour du système est exactement ce cas — et il est en plus détourné de son sens habituel, puisqu'il ne quitte pas l'écran.

Une puce **« ← Aujourd'hui »** apparaît donc sous le titre, et seulement quand ce n'est pas aujourd'hui. Pas de bouton grisé pour le cas courant : il occuperait la place et poserait la question de ce qu'il fait là.

**Sous le titre plutôt qu'à côté.** Le titre est déjà une date longue en `headlineMedium` — « vendredi 21 août 2026 » — et la ligne porte en outre les deux portes du poids et du profil. Une puce de plus s'y serait battue pour la largeur, ce qui est précisément le défaut qu'on vient de corriger deux fois.

La pastille d'aujourd'hui reste un troisième chemin, mais elle sort du bandeau dès qu'on remonte de plus d'une semaine ; elle ne pouvait donc pas suffire.

### Le jour regardé a maintenant un contrat, et le faux ne triche plus

`SelectedDay` a deux implémentations depuis D101 — `CurrentSelectedDay`, qui normalise contre l'horloge, et `InMemorySelectedDay`, qui normalise contre une date qu'on lui donne — et **rien ne les éprouvait ensemble**.

Pire : la date du jour du faux était **facultative**. Un appelant qui ne s'en souciait pas — il y en avait six — obtenait un faux qui rangeait la date d'aujourd'hui là où le vrai range `null`. C'est mot pour mot la configuration que [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée) décrit, celle qui avait déjà coûté quatre défauts livrés : un faux plus indulgent que le vrai, et des tests écrits contre le faux.

Deux corrections, et la première rend la seconde difficile à contourner :

- **le paramètre devient obligatoire.** Un faux qui ne peut pas être configuré sans dire quel jour il est ne peut pas être plus indulgent que le vrai ;
- **`SelectedDayContract` est écrit une fois et exécuté deux fois**, sur le vrai et sur le faux, comme `ProfileStoreContract` et `DiaryContract` avant lui. Il éprouve notamment que les deux façons de lire — le flux qu'observe l'écran, et la lecture immédiate dont `CreateDraft` se sert — disent la même chose.

**Campagne de défaite : cinq sabotages, cinq cas tombés.**

**Conséquences.** `:core:common` reçoit une source de test, ce qu'il n'avait pas. Six constructions du faux disent désormais quel jour il est ; toutes le tiraient déjà d'une horloge à portée de main.

**Ce que le vert ne prouve pas.** **Que la puce s'affiche.** Sa condition est la même que celle du `BackHandler`, trois lignes plus haut dans le même fichier, mais aucun test unitaire ne mesure une composable et le projet n'a pas d'infrastructure de test Compose — [D101](#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée) posait déjà ce constat. Rien ne dit non plus qu'une puce soit le bon objet plutôt qu'un bouton texte, ni qu'elle se voie à cet endroit : cela se juge sur un téléphone.

---

## D103 — La portée d'un geste est une affaire de disposition, et une règle ne s'écrit qu'une fois · ✓ validée

**Contexte.** Deux défauts rapportés à l'usage après [D101](#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée), et **les deux sont des corrections qui n'avaient pas pris**.

### Le calendrier se refermait quand on voulait le lire

Déplié, défiler *dans* le mois le repliait au lieu de le faire défiler. La condition était pourtant juste — un doigt qui monte, sur un calendrier déployé — et c'est bien ce qui rendait le défaut difficile à voir : **le problème n'était pas ce que la connexion décidait, mais où elle était posée.**

`onPreScroll` va du parent vers l'enfant. La connexion enveloppait toute la page, calendrier compris ; elle voyait donc le geste avant le mois déplié et le consommait avant qu'il ait pu défiler. Aucune condition n'aurait pu réparer cela : une connexion ne sait pas d'où vient le geste qu'elle intercepte.

**La portée d'un geste se règle par la disposition.** Le titre et le calendrier sortent du défilement — [docs/02](02-parcours-et-ecrans.md#bandeau-calendrier-fixe-en-haut) les voulait fixes en haut depuis toujours — et la connexion ne couvre plus que le contenu qui les suit. Un geste dans le calendrier appartient au calendrier, un geste sous lui replie.

La décision, elle, sort dans `collapsingDelta` : ce qui reste dans l'écran est le seul effet, et la règle se laisse éprouver.

### « 1 bol » a survécu à son propre correctif

L'unité nommée disparaissait toujours du sélecteur en rouvrant un plat, **après** la correction de D101. La raison est nette : la règle avait été corrigée dans le domaine — l'unité qu'une ligne porte fait toujours partie de celles qu'elle propose — mais **l'écran ne lisait pas cette règle-là**. `EntryFormLine` portait sa propre copie, `QuantityUnit.universal + servings`, et personne ne l'avait touchée.

C'est la même forme que les pastilles ovales, à un étage de plus : un compte tenu à deux endroits. Ici la copie était plus discrète, parce que les deux listes étaient *vraies en même temps* jusqu'au jour où l'une a appris quelque chose.

**Une règle écrite à deux endroits n'est pas la même règle**, c'est deux règles qui se ressemblent. `EntryFormLine.units` délègue désormais au brouillon qu'il sait déjà construire — le chemin par lequel il obtient toutes ses autres règles du domaine.

La pastille choisie se compare aussi **par le code**, comme la liste qui la contient : un bol passé de 250 à 260 g depuis l'enregistrement reste le même bol pour qui regarde l'écran, et une pastille « 1 bol » qui ne s'allume pas serait incompréhensible.

### Ce que la campagne précédente n'avait pas su voir

Les cinq cas de D101 éprouvaient `DraftLine.units`, et ils tombaient bien quand on sabotait le domaine. **Ils ne prouvaient rien de ce que l'écran affiche**, parce qu'aucun d'eux n'empruntait le chemin de l'écran. Le nouveau jeu part de `EntryFormLine`, c'est-à-dire de l'objet que le sélecteur lit.

C'est une leçon sur le choix du point d'entrée d'un cas : éprouver la règle à l'endroit où elle est écrite ne dit pas qu'elle est lue.

**Campagne de défaite : huit sabotages, huit cas tombés.** Celui qui compte : remettre à l'écran sa copie de la règle fait tomber deux cas, là où auparavant il n'en faisait tomber aucun.

**Conséquences.** L'accueil se scinde en un en-tête fixe et une zone défilante ; le calendrier ne quitte plus le haut de l'écran quand on descend dans le journal. `collapsingDelta` et `EntryFormLine.chose` sont deux fonctions de plus, et deux règles de moins dans des composables.

**Ce que le vert ne prouve pas.** **La portée du geste.** Que la connexion ne soit plus un ancêtre du calendrier est un fait de disposition, et aucun test unitaire ne mesure une disposition — c'est le même constat qu'en [D101](#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée) et [D102](#d102--le-retour-à-aujourdhui-a-une-porte-visible-et-le-jour-regardé-a-un-contrat---validée), et c'est la troisième fois. Les cas disent quels deltas replient, jamais lesquels arrivent.

Rien ne dit non plus qu'un en-tête fixe soit agréable : sur un petit écran avec le calendrier déplié, ce qui reste au journal est mince.

---

## D104 — La sauvegarde locale a ses écrans, et deux promesses qui n'étaient pas tenues · ✓ validée

**Contexte.** Le socle de la tranche 8 existait depuis [D96](#d96--la-sauvegarde-emprunte-les-mappeurs-et-le-format-ne-porte-que-ce-que-la-base-tient---validée) — format, mappeurs, rotation, copie de sécurité — sans qu'aucun écran ne l'atteigne. En l'y branchant, deux choses se sont révélées fausses.

**Google Drive attend.** Charly l'a repoussé jusqu'à ce que l'application entière fonctionne en local, et cela ne coûte rien : le port `BackupTarget` reste inchangé, et l'export par fichier ne passe pas par lui.

### L'export par fichier n'est pas une cible, et c'est pour cela qu'il manquait

`CreateBackup` écrit dans un `BackupTarget` : un endroit où les sauvegardes s'empilent, se listent et tournent. Le Storage Access Framework n'est rien de tout cela — l'utilisateur désigne **un** document, il n'y a rien à lister et rien à faire tourner. Le port le disait déjà en toutes lettres.

Il manquait donc l'étage en dessous : `ExportBackup`, qui rend des **octets** et ne connaît aucun rangement. `CreateBackup` s'en sert désormais pour remplir une cible, l'écran s'en sert pour remplir un document. Une capture-puis-encodage, deux chemins.

**L'ordre est la règle qui casserait en silence.** Les octets sont produits *avant* que le sélecteur s'ouvre. L'ordre inverse écrirait dans le fichier l'état de l'application au moment où l'utilisateur a fini de parcourir ses dossiers, et non celui où il a demandé l'export — invisible en démonstration, réel si une saisie arrive entre les deux.

### « Effacer toutes mes données » n'effaçait pas toutes les données

Le cas d'usage composait les oublis un à un : la base, les clés d'API fournisseur par fournisseur, le compte Open Food Facts. Il en laissait **trois** derrière lui — l'état de l'adaptation hebdomadaire, le consentement photo, et le compteur d'appels.

Le défaut n'était dans aucune des lignes. Il était dans la forme : **une liste écrite dans un cas d'usage se tait quand on oublie de l'allonger**, et c'est arrivé à chaque réglage ajouté depuis. Un effacement à moitié complet est pire qu'un effacement absent, parce que celui-là ne ment pas.

La liste est donc tenue par celui qui range. `StoredPreferences` est un port à une méthode, implémenté dans `:data:settings` — le seul module qui connaisse ses trois fichiers, **y compris ce que personne n'a modélisé**.

L'implémentation fait deux passes, et chacune répond à un problème distinct :

- **les magasins qui portent un flux** oublient d'abord. `StoredAiCredentials` et `StoredAdjustmentSettings` tiennent un `MutableStateFlow` que seules leurs propres écritures mettent à jour ; vider le fichier sous eux les laisserait annoncer une clé qui n'existe plus, jusqu'au prochain lancement ;
- **les fichiers ensuite**, et c'est la passe qui tient la promesse : elle emporte le réglage qu'on ajoutera l'an prochain sans penser à cette classe.

`commit` et non `apply` : une écriture différée ferait repartir l'appelant en annonçant un effacement qui n'a pas encore eu lieu.

### La restauration laissait tomber l'adaptation

`Snapshot` capture l'état de l'adaptation hebdomadaire depuis toujours, et le fichier le porte. **`replace` ne le reposait nulle part** — il n'y avait pas de méthode pour cela, l'état ne se construisant jusqu'ici que par des *réponses* (`accepted`, `ignored`, `stop`). Quelqu'un qui restaurait retrouvait donc ses repas et ses objectifs sans son « ne plus proposer », et la carte revenait le lendemain sans que rien ne l'explique.

`AdjustmentSettings.restore` pose l'état entier, et **remplace au lieu de fusionner** : rejouer une réponse n'est pas la même chose que reposer ce qu'elle avait produit.

Le cas qui gardait cela s'arrêtait **un pas trop tôt** : il vérifiait que l'état voyage dans le fichier, jamais qu'on le repose en arrivant.

### Un verrou qui demande de lire

L'effacement est le seul geste du projet qu'aucune barre d'annulation ne rattrape, et une double confirmation par boutons s'apprend à traverser en deux frappes. Taper le mot `SUPPRIMER` est le seul verrou qui demande de **lire** ([docs/09](09-donnees-et-sauvegarde.md#suppression)).

La comparaison ignore la casse et les espaces de bord, délibérément : quelqu'un qui écrit « supprimer » sur un clavier sans majuscules automatiques a lu la phrase. Lui refuser le geste ne protège plus rien — cela punit son clavier. C'est une **égalité** et non une inclusion, sans quoi une frappe au hasard finirait par ouvrir le bouton.

### Ce que l'écran écrit, et comment

Le `ViewModel` ne connaît ni `Uri` ni `ContentResolver` : l'écran lit et écrit le document, et ne fait passer que des octets. C'est ce qui rend les trois gestes éprouvables sans Android.

Le document s'ouvre en **`wt`** et non `w`. Un document réutilisé peut être plus grand que ce qu'on y écrit, et le mode par défaut ne tronque pas : la queue de l'ancien fichier survivrait derrière le nouveau JSON compressé, produisant une sauvegarde illisible que rien n'aurait signalée avant le jour où l'on en a besoin.

À l'ouverture, deux types MIME sont acceptés. Bien des fournisseurs de documents — les pièces jointes de courriel, certains nuages — annoncent `application/octet-stream` pour tout ; filtrer strictement rendrait la sauvegarde grisée et impossible à choisir, sans dire pourquoi.

**Campagne de défaite : quinze sabotages, quinze cas tombés.**

**Conséquences.** Le hub de réglages gagne sa quatrième section, celle que [D59](#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) avait laissée dehors faute d'écran. `AdjustmentSettings` gagne une méthode. Deux modules d'injection exposent leur type concret à côté de leur port, comme le faisait déjà celui des clés d'IA — `ErasureModule` a besoin du magasin réel, qui sait remettre son flux d'accord avec le disque.

**Ce que le vert ne prouve pas.** **Le sélecteur de documents.** Ni `CreateDocument`, ni `OpenDocument`, ni la lecture d'un `Uri` n'ont tourné : ce sont des activités du système, et les cas s'arrêtent aux octets. Un fournisseur qui refuse `wt`, une clé USB retirée en cours d'écriture, un fichier de 40 Mo sur un téléphone à court de mémoire — rien de cela n'est éprouvé.

Les **dialogues** non plus : que celui de restauration s'ouvre avant le sélecteur, que le bouton d'effacement reste fermé tant que le mot n'est pas écrit, que la barre de compte rendu apparaisse. Le mot lui-même se teste, son champ non.

Et rien ne dit que le fichier produit se **relise dans trois ans**. La chaîne de migrations existe, elle n'a jamais eu de version à migrer.

---

## D105 — Le nom devient Hexavore, et le dépôt change d'adresse · ✓ validée

**Contexte.** [D13](#d13--nom--hexaphore---remplacée-par-d105) avait retenu *Hexaphore* sur une vérification de disponibilité, pas sur une vérification de sens. À l'usage, le nom ne dit rien : `-phore` — « qui porte », comme dans *sémaphore* — est un suffixe savant que personne ne décode, et un suivi alimentaire qui ne parle pas de manger perd son seul mot gratuit.

**Choix.** **Hexavore**. `-vore` dit manger sans qu'on l'explique, et *Hexa* garde exactement la justification de D13 : six compteurs, que l'hexagone des macros montre à l'écran depuis [D33](#d33--un-hexagone-en-tête-daccueil-et-un-seul-ordre-angulaire---validée). Le nom change de moitié, pas de raison d'être.

**Le moment n'est pas indifférent.** L'`applicationId` n'est verrouillé qu'à la première publication, et rien n'est publié : `versionCode` valait 1. D14 avait chiffré ce renommage à une demi-journée tant que cette fenêtre restait ouverte. Elle l'était. Après la première mise en ligne, changer d'`applicationId` aurait créé une application entièrement nouvelle, sans ses installations ni ses mises à jour.

**Vérifications effectuées.** Aucune application de ce nom sur le Play Store ni sur l'App Store. `app.hexavore` inutilisé. Aucun paquet npm ni PyPI. `hexavore.app` libre. Homonymes sans conflit de classe : une carte de jeu de cartes à collectionner, et le mot circule comme nom de créature au sens de « dévoreur ». `hexavore.com` et `hexavore.org` sont enregistrés depuis 2010 par un tiers, et ne servent aucune page.

### L'organisation ne peut pas porter le nom, et ce n'est pas grave

`github.com/hexavore` est pris par un compte personnel ouvert en 2014, toujours actif. Trois issues ont été écartées, chacune pour une raison qui lui est propre :

- **réclamer un nom dormant** : GitHub ne le fait plus — *« We do not accept requests to release, transfer, or reclaim usernames on the basis that they appear inactive or unused »*. Et le compte n'est pas dormant ;
- **acheter le nom** : explicitement interdit, sous peine de suspension des deux comptes ;
- **la plainte pour marque**, seule exception que GitHub examine : elle demande une marque déposée et un risque de confusion. Un compte personnel antérieur de douze ans, dans un domaine sans rapport, n'en présente aucun.

L'organisation est donc **`hexavore-app`**, avec « Hexavore » en **nom affiché** — GitHub distingue le `login`, qui est l'URL, du `name`, qui est ce que l'interface montre. Le dépôt s'appelle `hexavore`. Ce qui reste de l'ancien nom tient dans quatre caractères d'URL de clone, que seul l'auteur tape.

**Le vieux nom d'organisation est redevenu libre.** Renommer libère l'ancien `login` : les liens vers les *dépôts* redirigent, ceux vers la *page de profil* rendent un 404, et une requête d'API sur l'ancien nom échoue. Rien d'externe n'en dépendait — le seul consommateur était l'en-tête ci-dessous.

### Le `User-Agent` change d'adresse, et le piège était là

[D26](#d26--le-user-agent-dopen-food-facts-est-figé---en-partie-remplacée-par-d105) figeait `Hexaphore/<version> (github.com/hexaphore/hexaphore)`. Il devient :

```
Hexavore/<version> (github.com/hexavore-app/hexavore)
```

**Une substitution mécanique aurait écrit `github.com/hexavore/hexavore`** — c'est-à-dire l'adresse du compte d'un tiers. Un signalement d'Open Food Facts serait alors arrivé chez quelqu'un qui n'a rien à voir avec l'application, et l'en-tête aurait perdu la seule chose qu'il sert à faire. C'est le seul endroit du renommage où `s/hexaphore/hexavore/` donnait un résultat qui compile, passe les tests, et désigne la mauvaise personne. Les deux cas qui figent la chaîne en dur l'ont rattrapé.

Le reste de D26 tient : une organisation survit à un changement de propriétaire, la version vient du `versionName`.

### Trois clés d'accès à des données locales

Le nom ne servait pas qu'à nommer. Trois valeurs sont des **clés d'accès à ce qui est déjà écrit sur l'appareil**, et les renommer laisse les données derrière :

- **`HexavoreDatabase.NAME`** passe de `hexaphore.db` à `hexavore.db`. Room ouvre alors un fichier neuf ; l'ancien reste sur le disque sans que personne le lise ;
- **l'alias Keystore** de `SecretCipher` passe de `hexaphore.ai.secrets` à `hexavore.ai.secrets`. La clé de l'ancien alias existe toujours, mais plus rien ne la demande : **les clés d'API chiffrées deviennent illisibles**, et le format de sauvegarde ne les emporte pas — `Snapshot` ne porte que ce que la base tient ([D96](#d96--la-sauvegarde-emprunte-les-mappeurs-et-le-format-ne-porte-que-ce-que-la-base-tient---validée)). Elles se ressaisissent à la main ;
- **le préfixe des sauvegardes** passe de `hexaphore-` à `hexavore-`. Ici rien ne casse, et c'est [D104](#d104--la-sauvegarde-locale-a-ses-écrans-et-deux-promesses-qui-nétaient-pas-tenues---validée) qui l'avait rendu vrai sans le savoir : `stampOf` retire le préfixe dans un `runCatching` et rend `null` s'il ne le trouve pas. Un ancien fichier reste donc listé et relisible — il retombe sur sa date de modification au lieu de l'instant écrit dans son nom.

Aucune de ces trois pertes ne touche un utilisateur, puisqu'il n'y en a pas encore. C'est précisément ce que la fenêtre de publication rendait gratuit.

**Les schémas Room ont suivi la classe.** Room range les schémas exportés sous le nom pleinement qualifié de la base : `schemas/app.hexaphore.core.database.HexaphoreDatabase/` devenait introuvable, et Room aurait régénéré la version 6 dans un répertoire neuf — perdant l'historique des cinq migrations, sans qu'aucun build n'échoue. Le répertoire a été déplacé avec la classe. Les JSON, eux, ne portent pas le nom : ils n'ont pas bougé d'un octet.

### Le journal garde son histoire

Ce fichier est une archive, et une archive qu'on réécrit ment. La règle appliquée est donc double :

- **le nom comme nom reste** : D13 dit toujours que `github.com/hexaphore` était libre, et D14 qu'il fallait réserver l'organisation `hexaphore`. C'était vrai le jour où c'était écrit, et le contraire serait faux aujourd'hui ;
- **le nom comme identifiant de code est mis à jour** : `HexavoreDatabase`, `app.hexavore.domain.resolution`, `hexavore.android.library`, `hexavore.db`. Sans cela un lecteur qui suit une décision jusqu'au code ne trouve plus rien.

D13 et D26 sont marquées remplacées ; leur texte n'a pas été touché.

**Reste à vérifier avant la 1.0.** Absence de marque déposée à l'INPI et à l'EUIPO en classes 9 et 42 — la vérification que D13 avait reportée n'est pas faite, elle change seulement de cible, et aucune recherche web ne la remplace. Et **réserver `hexavore.app`**, libre au moment du renommage : c'est le domaine qu'implique un `applicationId` en `app.`, et le seul des quatre fronts de D13 qui reste ouvert.

**Conséquences.** 538 entrées touchées : 488 changent de chemin, 532 changent de contenu, et les six schémas Room déménagent sans qu'un octet bouge. Cinquante répertoires — les quarante-neuf paquets de sources, plus celui des schémas — et quatre fichiers changent de nom. Six identifiants de plugins de convention passent en `hexavore.*`, la coordonnée du build inclus en `app.hexavore.buildlogic:detekt-rules`, le jeu de règles detekt maison en `hexavore` — dans son `RuleSetProvider` et dans les trois `config/detekt/*.yml` à la fois, faute de quoi les règles se seraient désactivées en silence. Six chaînes visibles par l'utilisateur changent de nom, dont l'avertissement médical de l'onboarding.

**Leçon retenue.** D13 concluait qu'un nom se vérifie sur quatre fronts avant la première ligne. Elle avait raison sur la méthode et tort sur la liste : il en manquait un cinquième, **le sens**. Un nom disponible partout et compris nulle part est un nom qu'on renomme — et cette fois, la disponibilité de l'espace de noms ne suffisait pas non plus, puisque le front GitHub s'est perdu sans que le nom soit perdu.

---

## D106 — Une clé s'obtient quelque part, et les barres du haut passaient sous l'horloge · ✓ validée

**Contexte.** Sept corrections rapportées à l'usage sur la même séance. Elles se ressemblent : chacune est un endroit où l'application savait quelque chose qu'elle ne disait pas.

### Le trou le plus large du parcours : où prend-on une clé ?

L'écran demandait une clé d'API sans dire ce que c'est, où on l'obtient, ni qui facture — trois questions qu'une personne qui n'en a jamais pris se pose toutes en même temps, et devant lesquelles elle referme l'application. **Le champ vide ne les posait pas, il les laissait deviner.**

Un lien vers la console du fournisseur, et un dialogue de trois paragraphes courts. **Un lien, pas un mode d'emploi** : les consoles changent de forme plusieurs fois par an, et des captures d'écran ou une marche à suivre en six étapes seraient fausses avant la fin de l'année — fausses avec l'autorité d'une documentation. La page du fournisseur, elle, est toujours à jour.

Ce que le dialogue dit de plus important n'est pas la marche à suivre mais **la répartition** : la clé reste sur l'appareil, les appels partent en direct, et c'est le fournisseur qui facture. C'est la seule chose que l'utilisateur ne peut pas déduire de ce qu'il voit.

**L'URL vit dans l'énumération**, à côté de `displayName`, et pour la même raison : ce n'est pas du texte à traduire, et la mettre ailleurs obligerait l'écran à un `when` sur le fournisseur — précisément le signal que `docs/12` nomme comme la fuite de l'abstraction. Il en va de même pour l'étoile du fournisseur mis en avant.

### « Utiliser » plutôt que « Enregistrer », et le formulaire qui reste

Le geste ne range pas une clé : il **choisit qui analysera les prochaines photos**. « Enregistrer » décrivait ce que le code faisait, « Utiliser » décrit ce que l'utilisateur veut.

**Le formulaire ne se referme plus après**, et c'est une réversion assumée. Il se fermait pour ne pas inviter à corriger ce qu'on venait d'écrire — un bon argument, jusqu'à ce que le bouton porte la confirmation : une carte qui se replie emporte sa réponse avec elle, et il ne restait plus rien à l'endroit où le doigt avait appuyé.

« Utilisé » demande **deux conditions, pas une** : que ce fournisseur soit celui qui sert, *et* que le formulaire n'ait pas bougé depuis. La première seule afficherait « Utilisé » sous une clé qu'on vient de modifier sans l'enregistrer — le contraire exact de la vérité. La comparaison ignore la révélation du champ : montrer sa clé ne la modifie pas.

### Deux mots au lieu d'un paragraphe

Une carte en réserve expliquait en trois lignes pourquoi elle n'était pas proposée. Ce texte occupait plus de place que les cartes qui, elles, servent à quelque chose. Il devient **« Bientôt disponible »**, et la carte s'estompe.

La raison n'a pas disparu : elle est dans `AiProvider`, à l'endroit où quelqu'un qui se demande *pourquoi* ira la lire. **L'écran dit ce que l'utilisateur peut faire ; le code dit pourquoi.** C'est la répartition normale, et elle avait glissé.

### L'estimation de coût disparaît

L'application portait une table de tarifs embarquée et affichait un montant daté. La date était là, l'avertissement aussi — et cela ne suffit pas. **Un prix relevé un jour donné vieillit sans prévenir, personne ne le corrige, et un montant approximatif affiché avec l'autorité d'un chiffre est pire qu'aucun montant.**

Ce qui reste est ce que l'application sait de première main : combien d'appels sont partis, et combien de jetons ils ont consommés. Le fournisseur, lui, facture exactement — et c'est chez lui qu'on lit sa facture. `AiPricing` et ses trente lignes de tarifs sont supprimés ; c'était en outre le seul fichier du projet qui exigeait d'être révisé pour rester vrai.

### ~~Une porte devant une porte~~ · corrigé au retour d'usage

Sans clé, l'appareil photo ouvrait un dialogue qui expliquait, puis proposait d'aller aux réglages. ~~Il mène désormais **directement aux réglages d'IA**~~ — la boîte avait été jugée superflue, l'écran d'arrivée devant porter l'explication mieux qu'elle.

**C'était faux, et l'usage l'a dit tout de suite.** Y aller directement dépose quelqu'un dans un écran de réglages sans lui avoir dit pourquoi : ce qu'il cherchait était de photographier une assiette, pas de configurer un fournisseur. Une explication qui arrive *après* le déplacement arrive trop tard — celui qui la lit est déjà ailleurs, et se demande d'abord comment il y est arrivé.

Le dialogue revient donc. Ce qui reste de la correction est ce qu'elle avait de juste : **son bouton ouvre la section d'IA et non le hub**, pour ne pas faire choisir deux fois quelqu'un qui vient de choisir.

### Les barres du haut passaient sous l'horloge

Les croix de fermeture touchaient le bord de l'écran. **La cause n'était pas une marge oubliée sept fois** : `enableEdgeToEdge()` est actif — l'application dessine d'un bord à l'autre, ce qui est voulu — et `Scaffold` **n'applique pas les encoches à ce qu'on met dans son emplacement `topBar`**. Il les retire du corps, en supposant que la barre s'en charge. Aucune des sept ne s'en chargeait.

Un composant partagé plutôt qu'un `statusBarsPadding()` recopié sept fois, parce que c'est la même barre — et parce que les sept copies **avaient déjà divergé** : deux d'entre elles passaient `contentDescription = null` à leur croix, qui ne s'annonçait donc pas au lecteur d'écran. Un bouton de fermeture introuvable pour qui ne voit pas la croix, sur deux écrans, depuis toujours. Personne ne l'avait remarqué parce qu'il n'y avait rien à comparer ; le paramètre est maintenant obligatoire.

C'est la troisième fois qu'une règle recopiée diverge ([D103](#d103--la-portée-dun-geste-est-une-affaire-de-disposition-et-une-règle-ne-sécrit-quune-fois---validée)), et la première où la divergence touche l'accessibilité.

**Campagne de défaite : neuf sabotages, neuf cas tombés.**

**Conséquences.** `AiSettingsScreen` se scinde en deux — ce qu'on écrit d'un côté, quels fournisseurs existent de l'autre — et `BackupScreen` en trois, quand les seuils ont mordu. Le hub de réglages, l'écran de contribution, le journal de poids, la photo et la description passent tous par la même barre.

**Ce que le vert ne prouve pas.** **Les marges.** Qu'une barre commence sous l'horloge est un fait de disposition, et aucun test unitaire ne mesure une disposition — c'est le quatrième constat de suite. Rien ne dit non plus que le lien s'ouvre : `LocalUriHandler` délègue au navigateur du système, et un appareil sans navigateur n'a été éprouvé nulle part.

Et rien ne dit que les deux URL de console soient **les bonnes** : elles ont été fournies, pas vérifiées par un appel. Un lien mort ne se distingue d'un lien juste qu'en l'ouvrant.

---

## D107 — Une pastille désigne une situation, jamais un message · ✓ validée

**Contexte.** Quatre choses que l'application sait et ne dit pas : qu'aucune IA ne sert, qu'une clé vient d'être refusée, qu'aucune pesée n'a été notée depuis une semaine, et qu'hier est resté vide. Toutes les quatre sont **actionnables** — c'est le critère qui a fait la liste.

### Ce qu'une pastille est ici

**Elle désigne une situation, et s'éteint quand la situation cesse.** Aucune ne se rejette : il n'y a pas de bouton « je sais ». Un tel bouton transformerait chaque règle en rappel, et un rappel qu'on peut faire taire ne dit plus rien à personne — c'est le mécanisme par lequel toutes les pastilles finissent par être ignorées.

Cela a une conséquence directe sur ce qui **peut** figurer dans la liste : une situation qu'on ne peut pas faire cesser n'a pas droit à une pastille. C'est ce qui a écarté la cinquième candidate, « vous n'avez jamais exporté vos données » : la sauvegarde est visible dans les réglages, et un point qui rappelle de sauvegarder ressemble à du harcèlement d'antivirus.

**Aucun chiffre.** Un compteur inviterait à vider une file, alors qu'il n'y a rien à empiler : « aucune clé » n'arrive pas trois fois. Il aurait en outre demandé de décider ce qu'on compte.

**Rien ne sort de l'application.** Pas de notification système, pas de permission demandée, pas de travail de fond, pas de `WorkManager`. Ce sont des points colorés que l'on voit en ouvrant un écran. Le jour où l'une d'elles devra sonner sur l'écran de verrouillage, ce sera une autre décision — avec une permission à demander, une politique de fréquence à écrire, et un utilisateur à ne pas fâcher.

### Un seul endroit décide

Quatre règles réparties dans les quatre écrans qui les affichent auraient divergé au premier changement. Pire : **chacune n'aurait été évaluée que là où elle se voit**, donc jamais ailleurs. `ObserveNotices` rend un flux unique, et les écrans y lisent ce qui les concerne.

Le filtre des réglages s'applique **avant** l'évaluation : une pastille éteinte ne doit pas coûter la lecture qui la produirait.

**Les deux pastilles d'IA se posent au même endroit et ne s'allument jamais ensemble.** Elles demandent le même geste — ouvrir les réglages, s'occuper de la clé — et une clé refusée n'a de sens que s'il y a une clé. Les afficher toutes deux dirait deux fois la même chose.

### Le refus de clé passe par un décorateur

Rien n'enregistrait les échecs : le journal d'usage compte ce qui a **réussi**. Un échec d'authentification n'est pas une consommation — ni jetons, ni modèle à additionner — et le ranger là aurait demandé d'inventer des zéros pour les colonnes qui ne s'appliquent pas. C'est donc un port à lui.

L'écriture se fait dans un **décorateur du port de reconnaissance**, et non dans les deux `ViewModel` qui l'appellent. La photo et la description passent par le même port ; leur demander à chacun de noter l'issue aurait fait deux endroits à tenir d'accord, et le troisième chemin — celui qui n'existe pas encore — serait arrivé sans. Ici, la règle est vraie par construction.

**La sonde n'y passe pas**, et c'est voulu : le bouton « Tester » affiche son résultat sous le doigt qui vient d'appuyer. Une pastille ne rompt un silence que là où il y en a un.

**Ce qu'il ne fait pas est le point délicat.** Une panne de réseau ou un quota épuisé ne disent rien de la clé : les traiter comme un succès effacerait une pastille pour la mauvaise raison, les traiter comme un refus en allumerait une qui ment.

### Sept jours, et pas six

La fenêtre de la moyenne mobile fait sept jours. Se plaindre plus tôt réclamerait une pesée dont le calcul n'a pas encore besoin ; attendre un mois laisserait afficher une tendance qui décrit un état révolu. **L'absence totale de pesée compte comme un silence trop long** — celui qui n'en a jamais noté est précisément celui à qui la courbe ne sert à rien, et attendre une première pesée pour en réclamer une serait un silence qui ne se romprait jamais.

### Le réglage montre ce qu'il règle

Un interrupteur par pastille, et non un seul global : celui que l'une d'elles agace ne doit pas renoncer aux trois autres.

**Chaque ligne montre si sa pastille est allumée *en ce moment*.** Un interrupteur seul laisse celui qui vient de l'activer se demander ce qu'il surveille ; le point à côté du libellé répond sans documentation, et c'est exactement ce qu'on verrait en revenant à l'accueil.

**Toutes sont allumées par défaut.** Une pastille éteinte d'office ne serait découverte par personne, et c'est aussi ce qui fait qu'une cinquième s'allumera sans migration : dans le fichier de préférences, absent vaut allumé.

**Campagne de défaite : treize sabotages, treize cas tombés.**

**Conséquences.** Deux ports de plus, un décorateur, un quatrième fichier de préférences — que l'effacement emporte comme les trois autres. `ErasureModule` sort la liste des fichiers dans son propre fournisseur, le seuil de paramètres ayant mordu. Le hub de réglages gagne sa cinquième section.

**Ce que le vert ne prouve pas.** **Que les pastilles se voient.** Leur position sur les icônes, leur taille, le fait qu'un point de huit points ne disparaisse pas sur un écran chargé — rien de cela n'est éprouvé, et c'est le cinquième constat de suite sur une question de disposition.

Rien ne dit non plus que **la liste soit la bonne**. Quatre situations ont été retenues parce qu'elles sont actionnables ; savoir si elles sont *utiles* demande de vivre avec elles quelques semaines. La pastille de la veille est celle dont je doute le plus : elle s'allumera chaque matin chez quelqu'un qui note ses repas le soir.

---

## D108 — Le mode debug montre les corps, et n'écrit rien nulle part · ✓ validée

**Contexte.** L'outillage du modèle arrive : une boucle de trois tours entre l'application et un fournisseur, où ce qui se passe mal se passe mal **à l'intérieur**. Sans un moyen de voir ce qui part et ce qui revient, la mettre au point reviendrait à deviner.

L'instrument se livre donc **avant** ce qu'il sert à régler.

### Les corps, pas les en-têtes

Il existait déjà un `RedactionInterceptor` : il journalise la méthode, l'URL et les en-têtes, en masquant ceux par lesquels une clé voyage. **Ce n'est pas ce qu'on veut voir.** Ce qui explique une réponse inattendue est le corps — le prompt envoyé, le JSON reçu — et le corps est précisément ce que l'autre n'enregistre pas.

Celui-ci fait l'inverse, et c'est délibéré : **aucun en-tête n'est retenu, ni masqué, ni filtré — simplement absent**. C'est plus sûr qu'une liste de noms secrets à tenir à jour, et l'autre en tient une qui a déjà dû être complétée d'avance pour des fournisseurs qui n'existaient pas encore. Un corps, lui, ne porte pas de clé.

### Rien sur le disque, et ce n'est pas une limite

Le journal vit en mémoire, borné à vingt échanges, et meurt avec le processus. **Ce n'est pas une étape vers un fichier.** Ce qu'il contient est la description d'un repas, la photo d'une assiette, les libellés qu'on a cherchés : un fichier de mise au point qui survit à la session est un fichier que personne n'efface, et qui finit dans une sauvegarde.

Fermer l'application est donc la façon la plus sûre de l'effacer. Le bouton « Vider » n'est là que pour ceux qui ne veulent pas attendre.

### Éteint, il ne coûte rien

La première ligne de l'intercepteur rend la main : pas de lecture de corps, pas de copie, pas d'allocation. C'est ce qui permet de le laisser dans la chaîne en permanence, plutôt que de reconstruire le client — donc les trois interfaces Retrofit — au milieu d'une analyse en cours.

Le réglage se lit **sans suspendre**, comme le jour regardé : c'est un intercepteur qui pose la question, sur le fil d'OkHttp, et lui demander une coroutine obligerait à en lancer une par requête.

### Trois pièges, et chacun casse en silence

- **Les images.** Une photo voyage en base64 : quelques centaines de milliers de caractères qui rempliraient la mémoire et noieraient le JSON qu'on cherche. Toute longue suite base64 est remplacée par sa longueur. **Élider d'abord, tronquer ensuite** — l'inverse couperait au milieu de l'image et laisserait quatre mille caractères de bruit à la place du JSON.
- **La réponse.** Elle est lue par `peekBody`, qui en prend une copie sans consommer le flux. La lire autrement la viderait, et l'appelant recevrait une réponse vide : un journal qui casse ce qu'il observe.
- **L'effacement.** Le réglage vit dans le fichier des clés, et part avec elles. Quelqu'un qui efface ses données n'a rien demandé à voir de ce qui reste.

### L'écran ne s'embellit pas

Du texte à chasse fixe, défilant horizontalement, le plus récent en tête. Pas de mise en forme du JSON, pas de coloration, pas de pliage. **Embellir un instrument de diagnostic revient à en cacher une partie, et la partie cachée est toujours celle qu'on cherchait.**

Le code HTTP porte la seule couleur de l'écran, parce que c'est ce qu'on regarde en premier.

**Campagne de défaite : huit sabotages, huit cas tombés.**

**Conséquences.** Deux ports de plus au domaine, un journal en mémoire dans `:core:common` à côté de l'horloge, un intercepteur, un écran. `aiClient` prend un paramètre de plus — **sans valeur par défaut**, et `SilentExchanges` existe pour que les montages qui n'observent rien le disent. Une valeur par défaut aurait laissé passer un câblage oublié en production, où l'absence de journal ressemble exactement à un mode debug qui ne s'allume pas.

**Ce que le vert ne prouve pas.** **Que l'écran se lise.** Un JSON de huit mille caractères en chasse fixe sur un téléphone est un pari : les cas affirment ce qui est retenu, pas qu'on arrive à le lire. Et rien ne dit que vingt échanges soient le bon nombre — c'est de quoi remonter une boucle d'outillage entière, ce qui est une supposition sur une boucle qui n'existe pas encore.

---

## D109 — Le modèle choisit la fiche, parce qu'il en sait plus que notre score · ✓ validée

**Contexte.** « Abricot » devenait « Jus d'abricot ». Le modèle disait *abricot*, l'application cherchait « abricot » dans la table de l'ANSES, en tirait vingt candidats, et **son propre score** tranchait. Pire : la confiance restait haute, donc la ligne se remplissait sans être signalée — le défaut arrivait silencieusement dans le journal.

Le modèle n'avait jamais voix au chapitre sur la ligne retenue, alors qu'il est le seul à avoir vu l'assiette.

### Ce que l'outillage renverse

Le modèle interroge le catalogue et **choisit lui-même**. Il connaît l'assiette, il a écrit le libellé, et on lui montre ce que la table propose : c'est mieux informé que n'importe quel score de ressemblance de chaînes.

Ce qu'il reçoit pour chaque libellé : le nom complet, le rayon, et **les six teneurs pour 100 g**. Les teneurs ne sont pas décoratives — elles permettent d'écarter un jus sans deviner : un jus d'abricot a près de zéro protéine et beaucoup de sucres, un abricot entier a des fibres. Sans elles, il choisirait sur le seul libellé, c'est-à-dire sur la même information que le score qu'on remplace.

Le nom **long** et non le titre court : ce qui distingue deux fiches voisines est précisément ce que le raccourci enlève — « cru », « en conserve, égoutté », « sans matière grasse ».

### Le double outil, et pourquoi la réponse passe par un outil elle aussi

Le chemin ordinaire force la sortie par un schéma — `output_config.format` chez Anthropic, `responseSchema` chez Gemini. **Rien dans la documentation des deux ne dit si ce forçage se combine avec un appel d'outil.** J'ai lu les deux références ; le silence n'est pas une confirmation, et bâtir dessus reviendrait à parier sur une combinaison non vérifiée, dans deux dialectes à la fois.

En mode approfondi il n'y a donc **aucun forçage de sortie** : le modèle rend son repas en appelant `rendre_le_repas`, et le JSON arrive dans les arguments. C'est le même parseur qui le lit — on lui passe la chaîne, il ne sait pas d'où elle vient.

**Un seul appel d'outil par tour**, demandé aux deux fournisseurs. Un tour qui en rendrait trois obligerait à répondre aux trois dans un unique message, et la moindre erreur y apprendrait au modèle à cesser d'appeler en parallèle. On s'épargne le problème.

### Ce que la boucle ne fait pas

**Elle ne relit rien.** Le candidat porte la fiche entière, qui ne voyage pas jusqu'au modèle : celui-ci ne peut choisir que parmi ce qu'on lui a montré, donc la fiche est déjà là quand sa réponse arrive. Aller la rechercher par sa référence coûterait une lecture — et une fiche de l'ANSES pas encore copiée dans le catalogue local ne s'y trouverait pas.

Une référence qu'il **invente** ne correspond alors à aucun candidat, et la ligne repart vers l'estimation. C'est le bon comportement : on ne remplit pas une ligne avec une fiche qu'on n'a pas.

**Elle ne relie pas par position.** C'est le libellé qui rattache une référence à sa ligne, parce que le parseur commun écarte les lignes sans quantité : les index ne correspondent déjà plus quand on en sort. Deux lignes d'un même libellé partagent leur référence — c'est acceptable, elles désignent le même aliment.

**Elle ne compare pas.** Chercher malgré tout pour confronter son choix au nôtre ferait deux juges qui se contredisent, et il faudrait décider lequel a tort — ce que rien ne permet de faire.

**Elle ne signale pas.** Une fiche choisie donne un verdict automatique, sans alternatives : les valeurs viennent de la table de l'ANSES et non du modèle, donc le marqueur pointillé de [D25](#d25--lestimation-ia-se-signale-par-une-forme-pas-par-une-couleur---validée) mentirait. Mais **la confiance affichée reste la sienne** : elle ne devient pas 1 sous prétexte qu'il a choisi dans une liste, parce qu'il a pu choisir le moins mauvais.

### Trois tours, six candidats, et le repli

Trois recherches au maximum, plus un tour pour conclure. Un seul aller-retour ne laisserait pas la possibilité de se rattraper — « merguez grillée » ne trouve rien là où « saucisse de bœuf » trouve, et il inventerait des macros pour une fiche qui existait. Au-delà de trois, une analyse dépasse la minute, et comme chaque tour renvoie toute la conversation — l'assiette comprise —, le quatrième se paierait quatre fois pour un gain que rien ne laisse espérer.

**Six candidats par libellé**, pour la même raison de coût : six multipliés par les cinq aliments d'une assiette font déjà trente fiches à chaque tour.

**Le compteur additionne les tours.** Puisque chaque aller-retour se paie, ne relever que le dernier annoncerait une fraction de la facture — sur le mode qui coûte précisément le plus cher. Un seul tour muet rend le total **inconnu** plutôt que partiel : une somme incomplète présentée comme le total serait un chiffre faux, et « inconnu » est ce que ce projet dit partout ailleurs d'une valeur qu'il n'a pas.

L'analyse compte en revanche pour **une entrée** et non quatre. C'est un choix, et il se discute : ce qu'on compare à une facture est le nombre de jetons, que les fournisseurs facturent, et une photo qui apparaîtrait quatre fois dans la liste raconterait mal ce qui s'est passé. Le mode debug montre les quatre allers-retours pour qui veut les voir.

**Le repli est la moitié qui compte.** Une boucle a plus de façons d'échouer qu'un aller-retour — un modèle qui répond en texte, trois tours sans conclure, un fournisseur qui refuse le champ des outils — et aucune ne justifie de rendre l'utilisateur bredouille alors que le chemin ordinaire, lui, marche.

Trois pannes ne se retentent pas : réseau absent, clé refusée, quota épuisé. Elles se reproduiraient à l'identique, et sur le quota l'appel se paierait quand même.

### Les deux dialectes, relevés et non devinés

Ma mémoire d'une API s'est déjà révélée fausse plusieurs fois sur ce projet, alors la forme de Gemini vient de la référence de `generateContent` : les outils enveloppés dans `functionDeclarations`, l'appel dans une *part* `functionCall`, sa réponse dans une part `functionResponse` d'un contenu de rôle `user` — et cette réponse est un **objet**, pas une chaîne comme chez Anthropic.

**Le même jeu de cas éprouve les deux**, délibérément : ce qui change est la forme du fil, ce qui ne doit pas changer est le comportement. Deux jeux différents auraient laissé l'un dériver sans que rien ne le dise — c'est le raisonnement des contrats partagés de [D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat---validée), appliqué à un protocole.

### Une capacité, pas une supposition

`AiProvider.tooling` dit qui sait appeler des outils. Un fournisseur qui ne le sait pas rendrait du texte là où on attend un appel, et l'analyse échouerait sans que l'utilisateur comprenne. La case se grise plutôt, **sans s'éteindre** : le réglage reste et reprendra effet quand on rebranchera un fournisseur capable — l'éteindre à la bascule ferait perdre un choix que personne n'a défait.

Le `&&` entre « demandée » et « possible » vit dans la dérivation de la configuration, une fois, plutôt que dans chaque reconnaisseur.

**Campagne de défaite : vingt-sept sabotages, vingt-sept cas tombés.**

La première passe en laissait **six debout et deux sans tourner**, et c'est là qu'elle a servi :

- **Quatre lacunes réelles.** Aucune assiette à deux aliments, donc rien ne vérifiait qu'une référence rejoint **sa** ligne. Aucun cas côté Anthropic n'interdisait le schéma de sortie, alors que son jumeau existait côté Gemini — la symétrie que ce jeu revendique n'était pas tenue. La réponse d'outil de Gemini était affirmée « objet », ce qu'un objet à clé unique contenant tout le JSON échappé satisfait tout autant. Et la dérivation « demandée et possible » n'avait de cas nulle part : `activeConfiguration` n'était éprouvée qu'indirectement, par les magasins qui s'en servent.
- **Un cas qui se lisait lui-même.** La borne s'affirmait `<= TOOL_CANDIDATES` — elle bougeait donc avec ce qu'elle vérifiait, et survivait à un passage de six à soixante. **Un cas qui lit la constante qu'il éprouve n'éprouve rien** ; le nombre y est maintenant écrit en toutes lettres.
- **Un sabotage qui n'en était pas un.** Remplacer l'échec d'une réponse en texte par l'analyse d'un objet vide produit… un échec. Il a cédé la place à celui qui discrimine — rendre un succès vide — et à un second qui nomme l'erreur attendue.
- **Deux qui ne compilaient pas**, donc ne prouvaient rien : l'un a été reporté sur le nom de champ qu'il visait vraiment, l'autre transformé en appel qui ne court-circuite plus.

**Aucun seuil n'a été relevé et aucun sabotage n'a été adouci** : les survivants sont tombés parce que des cas ont été ajoutés ou resserrés.

Trois sabotages de plus sont venus avec le comptage des jetons : ne relever que le dernier tour, chez l'un puis chez l'autre, et lire un tour muet comme un zéro.

**Conséquences.** Deux ports de plus au domaine — le catalogue qu'on interroge, le réglage de l'analyse —, deux boucles, un troisième prompt, et une recherche qui présente sans décider. **Cinq seuils detekt ont mordu et produit cinq découpages**, tous selon ce que les choses sont plutôt qu'en relevant le seuil : les trois prompts se groupent comme les trois interfaces Retrofit ; « un tour » sort de chaque boucle, qui ne dit plus que combien de fois ; ce qui **passe** par les outils quitte ce que les outils **sont** ; et les deux façons d'analyser quittent le modèle des clés puis son module, parce qu'elles parlent de comment on analyse et non d'identifiants.

**Ce que le vert ne prouve pas.** **Que les choix soient meilleurs.** Les cas éprouvent la conversation — combien de tours partent, ce que l'outil reçoit, ce que la boucle fait d'une réponse — et pas la qualité d'un choix, qu'aucun test ne peut affirmer. Savoir si « Abricot » cesse vraiment de devenir « Jus d'abricot » demande de photographier un abricot.

Rien ne dit non plus que **six candidats** soient le bon nombre, ni **trois tours** : les deux sont des paris sur un usage qui n'a pas encore eu lieu, et le mode debug de [D108](#d108--le-mode-debug-montre-les-corps-et-nécrit-rien-nulle-part---validée) existe pour les corriger sur pièces.

Et aucun appel réel n'a tourné : les deux boucles sont éprouvées contre un serveur qui récite. Un fournisseur qui refuserait un champ, ou qui n'appellerait jamais l'outil de réponse, ne se découvrira qu'avec une vraie clé.

> **Le premier vrai appel a échoué**, et pour la raison annoncée juste au-dessus : Gemini exige de revoir la **signature de pensée** de son appel d'outil, et nous rejouions son tour en le reconstruisant depuis nos types. Anthropic était cassé de la même façon, sans qu'on l'ait vu. Corrigé par [D110](#d110--le-tour-du-modèle-se-rejoue-tel-quel-jamais-reconstruit---validée).

---

## D110 — Le tour du modèle se rejoue tel quel, jamais reconstruit · ✓ validée

**Contexte.** Le premier vrai appel de l'analyse approfondie a échoué. Premier échange : 200, le modèle appelle `chercher_aliments`. Second : **400**.

> *Function call is missing a `thought_signature`. This is required for tools to work correctly […] function call `chercher_aliments`, position 2.*

Gemini attache à ses appels d'outil une **signature de pensée** qu'il exige de revoir quand on lui rejoue son tour. Aucun de nos champs ne la nommait, et `AI_JSON` ignore les champs inconnus : elle était lue, jetée, et ne repartait jamais.

C'est exactement ce que [D109](#d109--le-modèle-choisit-la-fiche-parce-quil-en-sait-plus-que-notre-score---validée) annonçait comme non prouvé — « aucun appel réel n'a tourné ». Le serveur qui récite récitait ce que nous savions déjà.

### Le défaut n'est pas un champ manquant

Ajouter `thoughtSignature` aux *parts* aurait refermé ce trou-là, et rouvert le même le jour où un fournisseur ajoute un champ. **La cause est que nous reconstruisions le tour du modèle au lieu de le renvoyer.**

Reconstruire depuis nos types revient à promettre qu'on connaît la liste complète des champs du fournisseur. Cette promesse était fausse hier ; rien ne la rendra vraie demain. Les deux API disent la même chose, et je l'ai relu chez les deux plutôt que de m'en souvenir :

- **Gemini** : « You MUST always resend all thought blocks exactly as they were received from the model. »
- **Anthropic** : les blocs de raisonnement se repassent **inchangés**, et en retirer un déclenche une erreur d'ordre ou de signature.

**Le contenu venu du modèle voyage donc en JSON brut.** On le **décode pour lire** — trouver l'appel d'outil, ses arguments —, jamais pour renvoyer. Lire une copie ne perd rien ; réécrire l'original perd exactement ce qu'on ignore.

Seuls les contenus **qu'on fabrique** passent encore par un type : notre question, notre réponse d'outil. Là, on connaît tous les champs, puisque c'est nous qui les écrivons.

### Anthropic était cassé aussi, sans qu'on l'ait vu

`asSent()` écartait tout bloc qui n'était ni du texte ni un appel d'outil, et son commentaire s'en expliquait : « les blocs qu'on ne sait pas rejouer sont écartés, pas devinés ». **Le raisonnement était bon et la conclusion fausse.** Il ne s'agissait jamais de deviner — il s'agissait de ne pas toucher. Un bloc de raisonnement porte une signature ; l'écarter est précisément ce que l'API refuse.

Le défaut n'était pas visible parce que Charly utilise Gemini. Il serait apparu au premier essai avec une clé Anthropic, sous une erreur qui n'aurait rien dit du mode approfondi.

`ToolUseBlock` disparaît avec `asSent()` : il n'existait que pour rebâtir ce qui se recopie maintenant.

### Le cas ne nomme pas le champ, et c'est le point

Le cas de régression envoie, sur le tour du modèle, **un champ qu'aucun type ne déclare** et — côté Anthropic — **un bloc entier dont le module ignore le type**, puis affirme qu'ils reviennent intacts dans la requête suivante.

Nommer `thoughtSignature` aurait fait un cas qui vérifie le champ d'hier. Ne pas le nommer fait un cas qui vérifie **la règle**, et qui vaudra donc pour le champ que le fournisseur ajoutera sans prévenir. C'est la seule forme qui protège de ce qui vient de se produire.

### La seule retouche autorisée

Le rôle du tour est **ajouté s'il manque**, jamais remplacé. Gemini le renvoie normalement dans son contenu, et on le garde tel quel ; mais un tour sans rôle serait attribué au hasard des positions, et c'est maintenant son contenu à lui qui le porte plutôt qu'une valeur qu'on écrivait.

Ajouter ce qu'on sait ne perd rien ; c'est retirer ce qu'on ignore qui casse. La règle interdit le second, pas le premier.

**Campagne de défaite : trente et un sabotages, trente et un cas tombés.** Quatre sont nouveaux, dont un qui reproduit le défaut exact — reconstruire le contenu de Gemini depuis ses *parts* typées.

**Conséquences.** `AnthropicMessage.content`, `AnthropicResponse.content`, `GeminiRequest.contents` et `GeminiCandidate.content` portent du JSON brut. Quatre conversions vivent dans `Verbatim.kt`, qui porte la règle. Les corps envoyés sur le fil sont **identiques** à ceux d'avant — les cas du chemin ordinaire, inchangés, en témoignent.

**Ce que le vert ne prouve pas.** **Que Gemini accepte maintenant le second tour.** Le cas prouve que ce que le modèle a envoyé repart intact ; il ne prouve pas que la signature soit ce qui manquait, ni qu'il n'en manque pas une autre. Un serveur qui récite ne refuse rien. **Seule une photo avec une vraie clé le dira**, et c'est la deuxième fois que cette phrase apparaît pour cette fonctionnalité — la première a eu raison.

Rien ne prouve non plus que le **chemin ordinaire** soit intact au-delà de ses cas : il envoie désormais des contenus encodés en deux temps plutôt qu'un. Les corps sont affirmés identiques par les cas existants, pas par un appel réel.

---

## D111 — Une règle se vérifie aux portes, pas seulement à la fabrique · ✓ validée

**Contexte.** Depuis l'accueil placé sur hier, rejouer un plat favori l'enregistrait sur **aujourd'hui**. En silence, et au mauvais endroit — exactement le défaut que [D102](#d102--le-retour-à-aujourdhui-a-une-porte-visible-et-le-jour-regardé-a-un-contrat---validée) avait nommé et cru refermé.

### La règle existait, et elle était bonne

`CreateDraft` porte la seule décision : *le jour qu'on regarde, sinon aujourd'hui*. Toutes les origines de l'écran de validation passent par elle — saisie neuve, fiche cherchée, produit scanné, proposition d'un modèle. Toutes **sauf une**.

`GetFavoriteDraft` bâtissait son `EntryDraft` à la main, avec `clock.today()`. Il n'avait pas contourné la règle : il ne l'avait jamais rencontrée. C'est la différence entre enfreindre et ignorer, et la seconde ne se voit pas à la relecture d'un fichier — il faut regarder ceux d'à côté.

Le favori passe donc par la fabrique, et rend son horloge : il n'a plus rien à savoir du calendrier. Le lien vers le favori s'attache après coup, parce que c'est la seule chose que la fabrique n'a pas à connaître.

### Ce que le défaut dit du jeu de cas

`DraftDayTest` couvrait la règle depuis [D102](#d102--le-retour-à-aujourdhui-a-une-porte-visible-et-le-jour-regardé-a-un-contrat---validée) : jour choisi, retour à aujourd'hui, brouillon de plusieurs lignes. Cinq cas, tous verts, tous **sur la fabrique**.

**Un cas qui n'éprouve que la fabrique ne dit rien des portes qui l'appellent.** Il affirme qu'une règle est correctement écrite, jamais qu'elle est effectivement empruntée — et c'est par là qu'un chemin oublié passe sans faire tomber quoi que ce soit. Le jeu de cas s'exerce maintenant sur la porte du favori, celle-là même que l'écran ouvre.

C'est le pendant de ce que [D110](#d110--le-tour-du-modèle-se-rejoue-tel-quel-jamais-reconstruit---validée) vient de dire d'un autre défaut : là, un cas nommait le champ d'hier au lieu de la règle ; ici, un cas nommait la fabrique au lieu des portes. Les deux fois, le cas décrivait **l'implémentation** plutôt que la promesse.

### Ce que la campagne a trouvé en plus

Rien n'affirmait qu'un favori rejoué garde la source « composé soi-même ». Le commentaire l'expliquait depuis toujours — *rien n'y est deviné, donc rien n'y mérite une pastille à part* —, et un sabotage qui la changeait en « photo » passait sans un bruit. La pastille aurait menti dans le journal, sur un plat que personne n'a photographié.

**Campagne de défaite : cinq sabotages, cinq cas tombés.**

**Conséquences.** `GetFavoriteDraft` perd son horloge et gagne la fabrique. Une seule `CreateDraft` là où les cas d'écran en construisaient trois : trois instances portaient trois jours regardés indépendants, qui ne pouvaient que se contredire — la même forme de défaut, dans le jeu de cas cette fois.

**Ce que le vert ne prouve pas.** **Que toutes les portes soient éprouvées.** Deux le sont maintenant — la fabrique et le favori — et les trois autres origines ne le sont qu'indirectement, par les cas d'écran. Rien n'interdit à une sixième origine d'arriver demain en bâtissant son brouillon à la main : ce qui l'empêcherait vraiment serait que `EntryDraft` ne puisse pas être construit hors de la fabrique, et ce n'est pas le cas aujourd'hui.

Et rien ne prouve que le jour regardé atteigne l'écran dans **l'application** plutôt que dans les cas : le port n'est pas persisté et vit dans un objet unique, ce que seul un vrai geste sur un vrai téléphone confirme.

---

## D112 — Le modèle pèse ce qu'il compte, parce qu'une pièce ne dit rien de sa taille · ✓ validée

**Contexte.** Cinq cacahuètes entraient dans le journal à **500 g**. Le modèle disait *5 PIECE*, la fiche ne nommait aucune portion, et notre forfait valait cent grammes la pièce.

### Pourquoi « une pièce » n'est pas une tranche

Les autres unités portent leur ordre de grandeur : une tranche fait trente grammes à peu près toujours, une cuillère à soupe quinze, un verre deux cents. Se tromper d'un tiers sur un forfait est supportable.

**« Une pièce » ne porte rien du tout.** Une cacahuète pèse moins d'un gramme, un œuf soixante, une pomme cent cinquante. Trois ordres de grandeur séparent les extrêmes, et aucun nombre ne les résume. Le forfait de cent grammes n'était donc pas un repli approximatif : c'était une **fabrication**, présentée avec l'autorité d'un chiffre.

Le projet refuse partout qu'une absence se dise « zéro ». Ici, c'était le miroir : une absence se disait « cent ».

### Le modèle a vu l'assiette

Il compte les cacahuètes, donc il sait à peu près ce qu'elles pèsent — c'est le même raisonnement que [D109](#d109--le-modèle-choisit-la-fiche-parce-quil-en-sait-plus-que-notre-score---validée), appliqué à la quantité au lieu de la fiche. Chaque ligne reconnue porte donc un **poids total en grammes**, que le modèle donne avec le reste.

**Un poids total, et non un poids par pièce.** C'est ce qu'on peut lui demander sans qu'il ait à diviser, et c'est directement ce dont la conversion a besoin.

### L'ordre, et ce qu'il protège

1. **La portion nommée de la fiche** — c'est une mesure, et [D73](#d73--la-portion-de-la-fiche-lemporte-sur-le-forfait-et-la-densité-attend-son-auteur---validée) lui donne déjà le dernier mot. Une estimation ne l'écrase pas, fût-elle informée.
2. **Le poids du modèle**, quand il s'est prononcé.
3. **Le forfait**, qui ne sert plus que lorsque personne ne s'est prononcé.

La règle est écrite une fois, pour **toutes les unités** et non pour la seule pièce : le poids du modèle remplace ce que nous aurions **deviné**, jamais ce que nous avons mesuré. C'est plus simple qu'un cas particulier, et c'est vrai pour l'assiette — qui n'a jamais de portion nommée, et où le modèle aide donc le plus.

La ligne reste **signalée comme estimée**. Il a estimé, il n'a pas pesé.

### Exigé, et autorisé à valoir inconnu

Le champ est dans `required` **et** accepte `null`. C'est [D98](#d98--le-modèle-doit-se-prononcer-sur-les-six-valeurs-et--inconnu--sécrit---validée) mot pour mot : un champ absent de `required` donne au décodage contraint de Gemini la permission de se taire **par défaut**, et un poids facultatif serait un poids jamais donné. Exiger la clé en autorisant `null` dit ce qu'on voulait dire — *prononce-toi, quitte à dire que tu ne sais pas*.

Un zéro ou un nombre négatif est lu comme une absence, pas comme un poids : le forfait reprend alors la main plutôt que de faire entrer une ligne de zéro gramme dans le journal.

### Ce que la campagne a trouvé en écrivant le code

`unknowable(strict)` existait déjà, pour les six teneurs de l'estimation, et j'en avais écrit un doublon sous un autre nom. Deux fonctions pour une seule règle divergent le jour où un troisième fournisseur exige autre chose ; celle qui restait est passée `internal` et sert les trois schémas.

**Campagne de défaite : neuf sabotages, neuf cas tombés.**

**Conséquences.** Un champ de plus sur la ligne reconnue, dans les deux schémas et les deux prompts — qui passent en `fr_v2`, parce qu'un prompt qui demande autre chose n'est plus le même. Les versions sont enregistrées avec chaque analyse, et c'est exactement à ça qu'elles servent.

**Ce que le vert ne prouve pas.** **Que les poids du modèle soient bons.** Les cas affirment qu'un poids donné est lu, transporté et préféré au forfait ; aucun ne dit qu'un modèle sait ce que pèsent cinq cacahuètes. Il faut les photographier.

Rien ne dit non plus que le modèle **remplira** ce champ. Le schéma l'exige, mais un schéma part sur le réseau et c'est le fournisseur qui l'applique — et les deux prompts qui le demandent n'ont, eux non plus, jamais été essayés contre une vraie clé. Le mode debug est là pour le voir sur pièces.

---

## D113 — Apparence existe, et suivre le système est un choix · ✓ validée

**Contexte.** « Apparence » figurait dans [docs/02][parcours] depuis la conception, et n'avait jamais été ouverte pour une raison écrite : *elle n'ouvrirait rien*. Le thème la remplit — les deux palettes existent depuis la 0.1, mais rien ne permettait d'en imposer une.

C'est la même échéance que D59 avait fixée au hub lui-même : une section s'ouvre le jour où elle a quelque chose à régler, et pas avant.

### Un choix, pas une absence

`SYSTEM` est rangé explicitement. C'est l'écart avec le jour regardé, où `null` **veut dire** aujourd'hui : là, la valeur nulle protégeait d'un écran qui resterait sur la veille après minuit, et l'absence portait donc une information. Ici, « suivre le système » est une intention durable ; la ranger comme un vide obligerait chaque lecteur à savoir ce que le vide signifie, et un fichier neuf serait indiscernable d'un choix fait.

Trois boutons radio plutôt qu'un interrupteur, pour la même raison : un interrupteur « sombre » ne sait pas dire *suivre*, et le troisième état devrait alors se deviner.

### La règle vit dans le domaine

`ThemeMode.isDark(systemIsDark)` prend le réglage d'Android **en paramètre**. La racine de l'application ne fait que le lui fournir.

L'écrire dans la composition aurait mis un `when` là où aucun test ne le lit, et donné à chaque appelant l'occasion d'en écrire un autre. C'est exactement le défaut que [D111](#d111--une-règle-se-vérifie-aux-portes-pas-seulement-à-la-fabrique---validée) vient de nommer, pris à l'endroit où il ne s'est pas encore produit.

**Un seul endroit applique le thème** : `MainActivity`, parce que c'est le seul qui enveloppe tout ce qui s'affiche. Le poser plus bas laisserait un écran hors du réglage, et on ne s'en apercevrait qu'en l'ouvrant.

### Ce qui ne voyage pas

**Le thème ne part pas dans la sauvegarde**, et c'est [D96](#d96--la-sauvegarde-emprunte-les-mappeurs-et-le-format-ne-porte-que-ce-que-la-base-tient---validée) appliquée telle quelle : le format ne porte que ce que la base tient. Un thème vit dans les préférences, et restaurer un export sur un autre téléphone n'a aucune raison d'y imposer le thème du premier.

Le **système d'unités**, lui, voyage — parce qu'il est une colonne du profil. Ce n'est pas une incohérence mais la même règle lue deux fois : les unités disent quelque chose de la personne, le thème dit quelque chose de l'écran qu'elle tient.

~~docs/09 annonçait que « les préférences d'affichage naîtront avec la section Apparence ».~~ Elles naissent sans elle : la section existe, et le champ `preferences` reste vide.

### Son propre fichier

Pas celui des réglages d'IA, contrairement aux deux façons d'analyser. Celles-là se rapportent aux clés et **doivent** disparaître avec elles ; un thème ne se rapporte à rien qu'on efface. L'y ranger l'aurait fait sauter au premier effacement de clés, sans que rien ne l'annonce.

Il est lu **au démarrage, sans suspendre** : le flux porte déjà la valeur du disque quand la première composition arrive. Une lecture asynchrone ferait apparaître l'application en clair pendant un instant avant de basculer, et ce clignotement se voit.

### Ce que la campagne a trouvé

Le défaut de l'état d'écran — `SYSTEM` — **n'était jamais observé** : le faux émettait toujours une valeur, donc la valeur initiale était remplacée avant qu'un cas la lise. Un sabotage qui la passait à « sombre » survivait.

Ce défaut n'est pourtant pas décoratif : c'est le repli quand le magasin casse. Le faux a gagné un mode de panne, le repli est devenu observable, et le sabotage tombe. **Une valeur par défaut qu'aucun cas ne peut voir n'est pas une règle, c'est une décoration** — et celle-ci en était une jusqu'à ce qu'on lui donne la seule situation où elle compte.

**Campagne de défaite : six sabotages, six cas tombés.**

**Conséquences.** Un port et un magasin de plus, un fichier de préférences à eux, une huitième route de réglages, et deux modèles pour un seul réglage : celui de l'écran, qui règle, et celui de la racine, qui ne fait que lire. Les séparer évite que la racine porte un état d'écran, et permet au changement de se voir sans quitter l'écran qui vient de le demander.

**Ce que le vert ne prouve pas.** **Que le thème s'applique vraiment.** Les cas s'arrêtent au modèle : rien n'éprouve `MainActivity`, ni que `NeonTheme` reçoive ce que le port a dit. Le câblage de la racine se vérifie en ouvrant l'application, pas autrement.

Et surtout : **rien ne dit que la palette claire soit lisible.** Elle existe depuis la 0.1 et n'a jamais été autre chose qu'un défaut système que personne n'a forcé. Ce réglage est la première façon de la regarder vraiment — les tests d'image de la 1.0 sont ce qui le dira, et ils n'existent pas encore.

---

## D114 — L'once est une unité de saisie, la livre un affichage · ✓ validée

**Contexte.** `UnitSystem` était modélisé, persisté et écrit dans la sauvegarde depuis la 0.1. **Aucun écran ne le lisait ni ne l'écrivait.** L'application promettait l'impérial dans son modèle de données et le refusait partout ailleurs.

### Deux moitiés qui ne se traitent pas pareil

La question posée était « tout convertir, quantités d'aliments comprises ». En lisant le code, une découverte a changé la réponse : `QuantityUnit` est déjà un type **ouvert** — un code et un poids par unité — et une ligne de journal stocke **la quantité dans l'unité choisie**, pas en grammes. « 1 tranche = 33 g » existait depuis [D42][decisions].

D'où deux traitements, et la différence n'est pas un caprice :

**Les quantités d'aliments : une unité de plus.** `oz` et `fl oz` rejoignent la liste. Ce qui est tapé est ce qui est enregistré, avec son équivalence en grammes qui voyage avec la ligne. Aucune dérive, aucun écran de lecture à changer, et une ligne de journal reste **le registre de ce qui s'est passé** — « j'ai mangé 6 oz ».

**Le corps : un affichage.** La base porte `weight_kg` et `height_cm` ; ces colonnes ne bougent pas. Un poids corporel est une **mesure unique dont l'unité n'appartient qu'à celui qui la lit**, là où une ligne de journal est un événement daté. Convertir au stockage aurait rendu un profil exporté dépendant du réglage de qui le restaure.

### Ce que le réglage ne fait pas

**Il ne convertit rien.** On peut basculer, regarder, et revenir sans qu'un seul chiffre ait bougé. C'est la propriété qui rend le réglage sans danger, et elle vient de ce que ni les lignes ni les colonnes ne dépendent de lui.

**Il ne rend rien illisible.** La paire impériale ne propose plus le gramme — c'est ce qui a été demandé, une liste où l'on choisit son système à chaque ligne n'étant pas un réglage. Mais un plat noté en grammes garde les siens : `DraftLine.units` ajoutait déjà l'unité que la ligne porte, une correction écrite pour « 1 bol » et qui protège ici sans une ligne de plus.

### La dérive, et pourquoi il n'y en a pas

Convertir à l'affichage pose toujours le même piège : 70 kg montré 154,3 lb, ré-enregistré 69,99 kg, et le poids glisse à chaque aller-retour.

Il n'y en a pas ici, et c'est [D45][decisions] qui l'évite : **les champs sont non contrôlés**. Ils tiennent leur propre texte et ne rendent une valeur que si quelqu'un a frappé une touche. Un profil qu'on ouvre puis referme ressort avec le chiffre exact qui y était ; seule une saisie réelle convertit, et ce que l'utilisateur vient de taper fait alors autorité.

### La taille change la forme du formulaire

Deux champs — pieds, pouces — parce que personne n'énonce sa taille en pouces. C'est le seul endroit où le réglage change la **structure** d'un écran et non seulement ses libellés, et c'est le prix d'une saisie qu'on n'a pas à calculer soi-même.

**Les pouces s'arrondissent avant d'être répartis.** Arrondir après la division laisserait douze pouces dans un pied : 182,88 cm s'écrirait « 5 pi 12 po », ce qui ne s'écrit pas.

### Ce qui ne change pas d'unité

**Les macros restent en grammes.** Les étiquettes nutritionnelles américaines les donnent ainsi — « Protein 12 g » — et un objectif de « 4,2 oz de protéines » ne ressemblerait à aucun emballage. Convertir aurait été *moins* familier à un utilisateur impérial, pas plus.

**L'onboarding reste métrique**, et ce n'est pas un oubli : le réglage vit sur le profil que l'onboarding crée, donc il n'existe pas encore quand ses questions se posent. Le choix se fait juste après, et comme rien n'est converti au stockage, rien n'est perdu. Un défaut déduit de la région de l'appareil le comblerait ; il demanderait un port de plus, et cette livraison ne l'a pas ouvert.

### Un endroit qui décide, deux qui lisent

`ObserveUnitSystem` répond à la question, et `ChooseUnitSystem` l'écrit. Le sélecteur d'une ligne, le journal de poids et l'écran de profil la posent tous ; chacun lisant `Profiles` pour son compte, chacun aurait décidé ce que vaut un profil absent — et l'un d'eux aurait fini par décider autrement. C'est [D111](#d111--une-règle-se-vérifie-aux-portes-pas-seulement-à-la-fabrique---validée), appliquée avant qu'elle ne coûte quelque chose.

Les conversions du corps vivent au **bord du champ**, dans un composable partagé du design system. Trois copies dans trois modules auraient divergé le jour où l'une apprend quelque chose — c'est la leçon que ce projet a déjà payée deux fois cette semaine.

**Campagne de défaite : treize sabotages, treize cas tombés.**

**Conséquences.** Deux unités, deux cas d'usage, un fichier de conversions corporelles, deux composables partagés. `QuantityUnit.universal` et `DraftLine.units` prennent un système : c'est la seule rupture d'API, et elle n'avait qu'un appelant de production.

**Ce que le vert ne prouve pas.** **Qu'un utilisateur impérial s'y retrouve.** Les cas affirment des conversions et des listes ; aucun ne dit qu'un formulaire à deux champs se remplit bien au pouce près sur un téléphone. Il faut basculer et saisir sa taille.

Rien ne dit non plus que **l'once soit l'unité qu'on veut** pour un aliment : les emballages américains portent souvent des tasses et des cuillères, que la table des portions couvre déjà par fiche. Six onces de riz est une phrase correcte, pas forcément une phrase courante.

Et **l'onboarding n'a pas été touché** : sa première impression reste métrique pour tout le monde, ce qui est le seul endroit où l'application demande une mesure avant de savoir dans quel système la demander.

---

## D115 — Une lettre de l'hexagone se mesure depuis l'arête · ✓ validée

**Contexte.** Rapporté à l'usage : « le C de calories est hors écran, et les lettres sont assez éloignées de l'hexagone ». Deux symptômes, une seule cause.

**La cause.** La réserve des six lettres — lueur, intervalle, demi-lettre — était retranchée du **circumrayon**, celui des sommets, puis les lettres étaient posées à cette même distance sur les **axes** des quartiers. Or un axe traverse le milieu d'une arête, qui n'est qu'à `√3/2` du sommet : la lettre se retrouvait à un huitième du rayon trop loin du contour, et la lettre du haut sortait de la zone par le haut. Le dessin, lui, ne se plaint pas — c'est le défilement qui encadre la figure qui rognait le « C » net.

**Choix.** Une fonction pure, `hexagonFit`, rend deux rayons : celui du contour et celui des lettres. Trois contraintes, la plus serrée gagne — la lettre du haut et celle du bas dans la hauteur, la lueur des sommets latéraux dans la largeur, les quatre lettres obliques dans la largeur aussi. Le rayon des lettres se compte alors **depuis l'arête** : `R·√3/2 + lueur + intervalle + demi-lettre`.

**Ce que ça coûte.** La figure perd environ 6 % de son rayon dans la zone qu'elle occupe aujourd'hui, parce qu'elle réserve enfin la place qu'elle prenait en dépassant. Elle la regagne en lisibilité : les six lettres sont deux fois plus près du contour, et l'intervalle qu'il avait fallu ajouter sous la figure pour que le « G » cesse de buter contre le grand chiffre n'a plus lieu d'être.

**Pourquoi une fonction et pas trois lignes dans le `Canvas`.** Une géométrie se raisonne au crayon, et celle-ci s'était trompée sans que rien ne le dise : un dessin qui déborde n'échoue pas, il se fait rogner. Sortie du tracé, la règle s'éprouve — une lettre tient dans la zone, ou elle n'y tient pas.

**Campagne de défaite : cinq sabotages, cinq cas tombés.** Les trois contraintes sont bien trois : supprimer celle des lettres obliques ne se voit que sur une zone haute et étroite, et supprimer celle de la lueur que sur une zone haute. Sans ces deux cas-là, deux tiers de la règle n'auraient été gardés par rien.

**Ce que le vert ne prouve pas.** Que la figure soit **belle** à sa nouvelle taille, ni que l'intervalle retenu entre l'arête et la lettre — la lueur, puis 6 dp — soit le bon à l'œil. Les cas disent que rien ne déborde et que la lettre part de l'arête ; ils ne disent pas où l'œil voudrait la voir. Cela se regarde sur un téléphone, pas dans un test.

---

## D116 — Le calendrier s'ouvre à la traction, et sa poignée se voit · ✓ validée

**Contexte.** Rapporté à l'usage : « peu de personnes comprennent que le calendrier est développable et cliquable ». Trois causes distinctes sous une seule phrase, et il a fallu les séparer pour les corriger.

### La poignée ne répondait pas au doigt

`semantics { onClick(label) { … } }` **déclare** une action au lecteur d'écran ; elle n'en installe aucune. Toucher la poignée ne faisait donc rien du tout, et seul le glissement ouvrait le mois — ce qui explique l'essentiel du reproche. `clickable` pose les deux à la fois, l'action réelle et son annonce.

C'est la seconde fois que ce projet paie la même confusion sous une autre forme : une règle affirmée à un seul endroit n'est pas une règle tenue ([D111](#d111--une-règle-se-vérifie-aux-portes-pas-seulement-à-la-fabrique---validée)). Ici, l'affirmation était l'annonce, et personne ne vérifiait qu'elle correspondait à quelque chose.

### Elle ne se voyait pas

Le trait était en `outline`, soit **1,4:1** sur le fond sombre. Une bordure décorative peut vivre à ce contraste ; un élément d'interface qu'on doit trouver, non — le seuil est 3:1. En `onSurfaceVariant`, il tient 7:1 dans les deux thèmes. Un chevron l'accompagne, et se retourne une fois le mois ouvert : un trait dit « on peut me tirer » à qui en a déjà vu un, le chevron dit dans quel sens.

### Le geste que tout le monde connaît manquait

**Tirer la page vers le bas quand elle est déjà en haut** ouvre le mois, après environ un centimètre. C'est le geste du rafraîchissement, connu sans avoir été appris, et il ne demande de viser rien du tout.

**La règle demande « est-on en haut », elle ne le déduit pas** — et la première version faisait l'inverse. Elle lisait ce que le défilement n'avait pas pu consommer, ce qui dit « on est en haut » sans avoir à le demander : plus court, plus joli, et faux. L'effet d'étirement d'Android consomme précisément ce reste pour dessiner son rebond, donc la traction n'accumulait jamais rien. **Le vert ne l'a pas vu ; le téléphone l'a vu en un geste.** La condition est maintenant explicite — la position du défilement — et elle se décide avant l'enfant plutôt qu'après lui.

**Un défilement lancé n'ouvre rien.** Un élan qui bute en haut rend le même delta qu'une traction, à ceci près que sa source est `SideEffect` et non `UserInput`. Sans cette distinction, toute lecture rapide finirait par déplier le mois — et ce serait rapporté comme un défaut, à juste titre.

**Campagne de défaite : quatre sabotages, quatre cas tombés.** Le plus instructif est le quatrième : remplacer l'accumulation par le dernier delta laisse un geste *rapide* ouvrir le mois et un geste *lent* ne rien faire, ce qu'aucun cas n'aurait vu sans un cas qui tire deux fois.

**Aucun de ces cas n'aurait pu voir le défaut de la première version**, et c'est la leçon à retenir : ils éprouvent la règle, pas son branchement. Quatre sabotages tombés sur une règle qui n'était jamais appelée, c'est un vert parfaitement sincère et parfaitement inutile.

**Conséquences.** `pulledBy` rejoint `collapsingDelta` dans le fichier des règles du calendrier : ouvrir et fermer sont deux règles, pas une avec un signe. L'accumulateur vit dans l'écran, là où le geste arrive, comme l'état de repli lui-même.

**Ce que le vert ne prouve pas.** **Que le seuil soit le bon.** Quarante-huit dp est un centimètre de doigt, choisi parce que c'est la distance d'un rafraîchissement ailleurs ; aucun cas ne dit qu'il ne s'ouvre pas trop tôt sur une journée vide, où la page ne défile pas du tout et où **chaque** geste vers le bas est une traction. Cela se règle en tirant, pas en lisant.

---

## D117 — Le balayage change de jour, il ne supprime plus · ✓ validée

**Contexte.** Rapporté à l'usage : « la suppression d'un aliment par balayage n'est au final pas pratique ». Le geste était là depuis la tranche 1, documenté, testé, et il servait peu — là où se promener dans l'historique demandait de viser une pastille de sept millimètres.

### Ce que le balayage fait maintenant

**Vers la gauche le lendemain, vers la droite la veille**, le contenu suivant le doigt. C'est le sens d'une page qu'on tourne, et celui du calendrier posé au-dessus, où le temps va vers la droite.

**Deux bornes, deux raisons.** Vers le futur, rien : le calendrier refuse déjà d'ouvrir un jour à venir, et noter un repas qu'on n'a pas pris n'a pas de sens. Vers le passé, la borne est celle que le calendrier sait montrer — vingt-quatre mois. Au-delà, on se promènerait dans des journées qu'aucune pastille ne désigne, sans moyen visible de revenir.

**Un glissement refusé résiste au lieu de ne rien faire.** Vers demain, la page se décale du tiers et revient : c'est la réponse d'une butée, et elle apprend la règle sans une phrase. Un geste qui ne produit rien du tout se lit comme un geste non reconnu, et on le refait.

**Deux façons d'emporter la journée, et il faut les deux.** La distance — un quart de la largeur — sert le geste appuyé ; la vitesse sert le geste vif, sans quoi remonter cinq jours demanderait de traîner la page cinq fois sur un quart d'écran. La vitesse ne compte que **dans le sens du déplacement** : un doigt qui repart en arrière au dernier moment annule, il ne confirme pas.

**Le calendrier suit.** Le bandeau couvre désormais la même période que le mois déplié et défile jusqu'à la semaine affichée ; sinon le cerne du jour regardé sortait du champ au troisième glissement, et on ne savait plus où l'on était.

### Ce qui part avec le geste

`SwipeToDelete` disparaît du design system. `DeleteEntry` disparaît du domaine, et avec lui `DiaryRepository.deleteEntry`, son implémentation Room, celle en mémoire et la requête du DAO : **plus rien ne les appelait**. Un port garde une capacité tant qu'un cas d'usage la demande ; celle-ci n'existait que pour un geste retiré, et la conserver « au cas où » aurait laissé une méthode que personne n'exerce plus — c'est-à-dire une promesse qu'aucun test ne défend vraiment.

Retirer une ligne reste possible, par le chemin que [02](02-parcours-et-ecrans.md) rendait déjà obligatoire : ouvrir le plat, et la corbeille de la ligne. `UpdateDish` réécrit alors le plat sans elle, et supprime le plat vidé de sa dernière ligne ([D61](#d61--un-plat-vidé-se-supprime-et-lappui-long-ouvre-ses-actions---validée)). La règle vit donc à **un** endroit au lieu de deux.

**La barre d'annulation reste**, pour la suppression d'un plat entier — et elle dit enfin la vérité : elle annonçait « Ligne supprimée » y compris quand un plat de six lignes venait de partir.

### Ce que ça coûte

Le geste qui supprimait était **immédiat** ; celui qui le remplace demande deux gestes de plus — ouvrir le plat, viser la corbeille. C'est assumé : la suppression d'une ligne isolée est rare, la promenade dans l'historique est quotidienne, et un écran n'a qu'un balayage horizontal à distribuer.

**Campagne de défaite : huit sabotages, huit cas tombés.** Les deux règles sont séparées — où va-t-on, et le geste emporte-t-il — précisément pour que la butée du futur ne dépende pas de la force du geste. Un seul calcul aurait rendu ce piège invisible.

**Ce que le vert ne prouve pas.** **Que le geste ne se déclenche pas par accident.** Les cas jugent une distance et une vitesse ; ils ne disent pas qu'un défilement vertical un peu oblique, sur une journée chargée, ne parte pas de travers. C'est le reproche exact qui avait été fait au balayage de suppression, et il se vérifie avec un pouce, pas avec un test.

Rien ne dit non plus que **vingt-quatre mois** soient la bonne borne : c'est celle du calendrier, retenue pour qu'il n'y ait pas deux limites différentes, pas parce que quelqu'un a voulu remonter jusque-là.

---

## D118 — Un plat porte un titre, et c'est le nom que le favori propose · ✓ validée

**Contexte.** L'affichage simplifié demandé pour l'accueil a besoin d'une chose que le modèle n'avait pas : de quoi désigner un plat sans citer ses aliments. Un plat se lisait par son contenu — il fallait tout lire pour savoir de quel repas il s'agissait.

### Ce n'est pas le retour de D06

[D31](#d31--un-plat-pas-un-repas-nommé---validée) a écarté les repas nommés, et cette décision tient. Ce qu'elle a écarté est la **case à choisir avant d'enregistrer**, pour répondre à une question que personne ne se pose. Ici, personne ne choisit rien : le moment se déduit de l'heure, ne coûte aucun geste, et ne range le plat nulle part — les plats restent une liste chronologique. Ce qui revient est un **nom**, pas une catégorie.

### Deux colonnes, et ni l'une ni l'autre n'est un libellé

`title` porte ce que l'utilisateur a **écrit**, `moment` le repas retenu **à la saisie**. À `NULL`, le titre ne veut pas dire « sans titre » : le plat s'appelle du nom de son moment, composé à l'affichage.

**Écrire ce nom dans la base aurait figé des mots français** dans chaque ligne, pour un plat que personne n'a nommé, et rendu l'application intraduisible sur son contenu le plus courant. Le domaine rend un moment et un rang ; les mots vivent dans le design system, où trois surfaces les partagent — la liste des plats, l'écran de validation, la boîte qui propose un nom de favori.

**`moment` est une colonne et non un calcul**, et c'est le cas du dîner d'hier noté ce matin qui l'exige : l'heure d'un plat est celle de sa **saisie**. Sans mémoire du moment choisi, les quatre pastilles de correction n'auraient rien où écrire.

### Le rang se calcule sur la journée, pas sur le plat

Deux plats du même moment — le déjeuner, puis le dessert noté à part — porteraient le même nom dans une liste dont c'est justement le seul repère en affichage simplifié. Le second devient « Déjeuner 2 ».

**Un plat nommé à la main ne consomme aucun rang.** Ce que l'utilisateur a écrit lui appartient : deux « Poke bowl » sont deux « Poke bowl », et un titre ajouté ne doit pas renuméroter le reste de la journée.

C'est une propriété du **voisinage**, donc elle se calcule là où la journée est connue — dans `GetDaySummary`, avec les totaux — et non plat par plat.

### La fusion avec les favoris va dans les deux sens

**Mettre en favori propose le titre du plat.** C'est le nom qu'on a sous les yeux, donc celui qu'on reconnaîtra dans une liste de modèles. Les deux propositions précédentes disparaissent : la liste des trois premiers aliments donnait des titres de cinquante caractères qu'on efface au lieu de les corriger, et « Plat 3 » ne disait rien de rien. `FavoriteNumbering`, son fichier de préférences et `NextFavoriteNumber` partent avec elle.

Le nom d'un favori est unique, et « Déjeuner » a de bonnes chances d'être pris : le rang suit alors le même principe qu'avant — on avance jusqu'au premier libre, et c'est l'écran qui écrit « Déjeuner 2 ».

**Rejouer un favori donne son nom au plat.** « Flocons du matin » est déjà le nom choisi pour ce contenu ; le rejouer sous « Petit-déjeuner » perdrait ce que l'utilisateur avait écrit, et la liste ne dirait plus lequel de ses modèles il a rejoué.

### Deux défauts que la campagne a trouvés

**Le nom proposé ne recalculait pas l'écran.** Il vivait à côté du `combine` et n'était lu qu'à la faveur d'une autre émission : la boîte de nommage s'ouvrait par accident. Le nom proposé et le drapeau « nom déjà pris » répondent à la même question, ils entrent donc dans le même flux — et un cas qui **garde l'écran observé** pendant le geste le prouve, là où une seconde collecte relance le flux et masque exactement ce défaut.

**Un moment illisible retombait sur le petit-déjeuner.** Le repli du mappeur doit rendre `NULL` — l'heure du plat reprend alors la main — et non choisir un repas. C'est l'asymétrie avec `source`, qui retombe sur `MANUAL` : « à la main » n'invente aucune provenance, là où « petit-déjeuner » affirmerait un repas avec l'aplomb d'une valeur choisie.

**Campagne de défaite : vingt et un sabotages, vingt et un cas tombés.** Un vingt-deuxième a été retiré plutôt que gardé : il portait sur l'instant où la boîte s'ouvre — un tour de boucle — et non sur une règle. Le code a été simplifié à sa place : un seul écrivain pour le nom proposé.

**Conséquences.** Base en version 7, deux colonnes ajoutées sans recopie de table. La sauvegarde porte les deux champs, **facultatifs**, donc le format ne change pas de version. `EntryDraft` gagne un titre et un moment ; `GetDishDraft` prend une horloge, pour lire l'heure d'un plat dans le bon fuseau.

**Ce que le vert ne prouve pas.** **Que les bornes soient les bonnes.** 5 h, 11 h, 15 h, 18 h sont des milieux entre deux repas, pas des heures de repas ; rien ne dit qu'un goûter de 14 h 30 ne s'appellera pas « Déjeuner » chez quelqu'un qui déjeune à midi pile. Cela se règle en vivant avec, et le titre se corrige.

Rien ne dit non plus que **le champ de titre ne gêne pas** : c'est un champ de plus en tête d'un écran qui en porte déjà beaucoup, et la seule façon de le savoir est de noter trois repas d'affilée.

---

## D119 — Deux styles d'affichage, une part par chiffre, et plus de total minoré à l'accueil · ✓ validée

**Contexte.** Rapporté à l'usage : « l'affichage actuel des plats est lourd, il peut rapidement contenir beaucoup de texte ». Une journée de cinq plats cite une vingtaine d'aliments, et la question qu'on se pose dix fois par jour n'est pas « quels aliments » — c'est « où j'en suis ».

### Deux styles, pas une densité

Le **simplifié** donne le titre, l'heure, les calories et les cinq apports. Le **détaillé** y ajoute la liste des aliments, et c'est l'affichage d'avant.

**Ce n'est pas une échelle de densité**, et le mot compte : ce qui distingue les deux n'est pas une hauteur de ligne mais ce qu'on lit. Une échelle aurait laissé croire qu'il existe un entre-deux, et il n'y en a pas — on cite les aliments ou on ne les cite pas.

**Le simplifié est le défaut**, y compris pour une installation déjà en service : c'est le sens du changement, et le détaillé reste à un tap dans Apparence. **Un magasin illisible retombe sur le simplifié** pour la même raison — le détaillé serait le pire des deux replis, plus long à lire et sans qu'un mot l'explique.

**Le trait qui sépare l'en-tête part avec les lignes** : sans lignes, il ne séparerait plus rien de rien.

### Une part de journée sous chaque chiffre

Un trait de 3 dp sous le total de calories et sous chacun des cinq apports : **ce plat, c'était combien de ma journée ?** Aucun chiffre ne posait cette question — ils disent des quantités, pas des proportions —, et c'est exactement ce qu'on cherche en relisant un plat.

**Pleine au-delà de l'objectif**, sans rétrécissement d'échelle ni dents de scie, contrairement aux grandes barres : il faudrait les lire, et il n'y a rien à lire ici. **Absente sans objectif** : une barre suppose une cible, et une journée antérieure au premier objectif n'en a aucune ([D55](#d55--lobjectif-est-calculé-daté-et-parfois-absent---validée)). Le chiffre, lui, reste exact et reste affiché.

**Sa largeur est celle du chiffre qu'elle souligne**, pas une colonne de grille : une grille régulière aurait fait un tableau, donc quelque chose à lire de plus.

### Ce que l'accueil cesse de dire

**Le signalement des totaux minorés disparaît de tout l'accueil** — le « ≥ » des apports, la phrase sous les barres, l'estompage des quartiers de l'hexagone. Une valeur non renseignée s'y lit désormais comme zéro.

**C'est un écart assumé avec [D29](#d29--un-total-incomplet-se-signale-au-lieu-de-se-taire---validée)**, demandé explicitement et sur cet écran seulement. Ce qu'il coûte est réel et mérite d'être écrit : une journée où un aliment n'a pas de fibres se lit comme une journée à zéro fibre, et rien ne le dit plus.

Ce qu'il ne coûte pas : **la base continue de distinguer l'inconnu du zéro**. `MacroTotal.complete` existe toujours et reste éprouvé ; l'écran de validation continue de désigner le champ qui manque, là où on peut encore le remplir. Ce qui change est ce que l'accueil **montre**, pas ce que l'application **sait**.

Le code mort part avec l'affichage : `MacroQuarter` perd son drapeau, l'hexagone son dégradé d'estompage et son aperçu de total incomplet, les ressources leurs quatre libellés. Un paramètre que plus personne ne renseigne est une décoration, et une décoration qu'aucun cas n'observe se périme sans qu'on s'en aperçoive.

### Un cas de campagne qui ne prouvait rien

Le sabotage « un magasin illisible rend le détaillé » a **survécu** à son premier cas : le `ViewModel` a son propre repli, qui masquait celui du cas d'usage. La règle ne s'éprouve donc qu'au niveau où elle vit — dans `ObserveDishStyle`, avec un magasin qui jette. Un second repli plus haut ne rend pas le premier inutile ; il le rend invisible, ce qui est pire.

**Campagne de défaite : sept sabotages, sept cas tombés.** Un huitième a été réécrit : remplacer `?: return null` par `?: 0.0` était neutralisé par la ligne suivante, donc ne changeait aucun comportement. Un sabotage qui ne casse rien ne prouve rien non plus.

**Conséquences.** `AppearanceSettings` porte un second réglage et `observe()` devient `observeTheme()` — un port qui répond à deux questions doit dire laquelle. `ObserveDishStyle` naît pour la même raison qu'`ObserveUnitSystem` ([D111](#d111--une-règle-se-vérifie-aux-portes-pas-seulement-à-la-fabrique---validée)) : deux écrans posent la question, un seul endroit y répond.

**Ce que le vert ne prouve pas.** **Que le simplifié suffise au quotidien.** Les cas affirment qu'il montre ce qu'on lui demande de montrer ; ils ne disent pas qu'on n'ira pas ouvrir chaque plat pour retrouver ce qu'on y avait mis. C'est l'usage d'une semaine qui le dira, et le réglage existe précisément pour qu'on puisse changer d'avis.

Rien ne dit non plus qu'un **trait de 3 dp se voie** sur un téléphone, ni que sa piste à 18 % se distingue du fond en plein soleil. Cela se regarde ; aucun test ne le regarde.

---

## D120 — Un seul bouton d'IA, un seul écran · ✓ validée

**Contexte.** Demandé : « les boutons IA seront groupés dans un seul bouton *IA* ; à l'appui on retrouve un menu similaire à celui de l'ajout par photo, mais on peut aussi envoyer sans image ». Et, sur cet écran : « le menu est moche de base pour prendre les photos, très peu moderne, revois l'ensemble ».

### Deux modales qui n'en étaient qu'une

« Photographier » et « Décrire » partageaient le pipeline de reconnaissance, le dépôt des propositions, les messages d'erreur et l'écran de sortie. Ce qui les distinguait tenait en une ligne : quelle variante de `RecognitionInput` partait. Deux `ViewModel`, deux états, deux écrans, deux destinations et deux suites de cas pour cette ligne-là.

**Le nouvel écran porte les deux entrées**, et `analysable` dit quand il y a de quoi analyser : une photo **ou** une phrase. Avec les deux, la photo part et la phrase devient sa précision — *« l'assiette fait 24 cm »* —, ce qui était déjà le rôle du champ de la modale photo.

### L'avertissement ne suit pas l'IA, il suit la photo

**Une phrase part sans avertissement.** Celui qui l'écrit sait exactement ce qu'il envoie ; une image emporte aussi ce qui entoure l'assiette — la table, la pièce, les gens. La règle de [05](05-ia.md) porte sur la photo, et la fusion ne l'étend pas au texte : l'étendre aurait transformé un avertissement utile en formalité qu'on accepte sans lire.

### Ce que l'écran est devenu

Trois zones, de haut en bas : **le cadre de l'image avec deux boutons ronds dedans**, **le champ de texte**, **le bouton d'analyse**.

**Les boutons vivent dans le cadre** plutôt qu'en rangée sous lui : ce qu'on regarde et ce qui le change sont alors au même endroit, et les mêmes boutons remplacent l'image sans qu'on ait à chercher ailleurs. **Une croix retire la photo**, ce qui permet de basculer vers le texte sans quitter l'écran — une photo prise par erreur obligeait sinon à tout refermer.

**Le cadre a une hauteur fixe.** Un cadre qui prendrait la place restante sauterait de taille à chaque ligne tapée ; un cadre au rapport de la photo changerait de hauteur selon qu'elle est prise en portrait ou en paysage. La photo y est rognée plutôt que mise en boîte : ce qu'on juge est le cadrage — l'assiette est-elle entière — et un bord rogné ne le change pas.

**Le champ de texte change de libellé, pas de place.** Sans photo il décrit, avec photo il précise. Deux champs auraient posé la question de savoir lequel remplir.

### Un cinquième glyphe tracé à la main

`material-icons-core` n'a ni étincelles ni image, et `material-icons-extended` embarque plusieurs milliers d'icônes pour en utiliser deux. Après `StarBorder` ([D62](#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée)), le code-barres et l'appareil photo, la réponse ne change pas : vingt lignes de tracé.

**Deux étincelles et non une étoile** : l'étoile à cinq branches est prise par les favoris, et une étincelle seule se lit comme une décoration. Deux, de tailles différentes, sont devenues le signe usuel de ce qu'une machine a produit.

**Campagne de défaite : dix sabotages, dix cas tombés.** Les deux qui comptent le plus sont ceux que la fusion rendait possibles : envoyer une phrase comme une photo, et demander l'accord pour une phrase.

**Conséquences.** Cinq fichiers disparaissent, deux naissent. La colonne de boutons flottants passe de cinq à quatre, et gagne la place qui manquait le plus — celle juste au-dessus du pouce. `HomeActions` perd un rappel, `HomeRoutes` aussi, le graphe de navigation une destination.

**Ce que le vert ne prouve pas.** **Que l'écran soit beau**, ce qui était la demande. Les cas affirment ce qui part et ce qui est demandé avant ; aucun ne dit qu'un cadre de 260 dp avec deux boutons ronds posés dessus se regarde bien sur un téléphone, ni que le texte sous une photo se lit sans que le clavier le cache. Cela se juge avec le pouce, et c'est le seul retour qui compte ici.

Rien ne dit non plus que **le glyphe d'étincelles se reconnaisse** : il est dessiné à la main, à vingt-quatre points, et son sens dépend d'une convention récente.

---

## D121 — Le changement de jour se voit · ✓ validée

**Contexte.** Demandé : « lorsqu'on change de jour en swipant ce n'est pas très clair d'où on se situe, rends plus visible le changement de jour en haut de l'écran ».

### Trois causes sous une phrase

Le balayage de [D117](#d117--le-balayage-change-de-jour-il-ne-supprime-plus---validée) faisait tout ce qu'on lui demandait, et pourtant on arrivait sans savoir où. Trois raisons, indépendantes :

- **le titre ne participait pas au geste** — il se substituait d'un coup, au milieu d'un mouvement ;
- **il ne disait rien de rapide à lire** — « vendredi 18 septembre 2026 » est exact et demande qu'on le déchiffre ;
- **le calendrier ne marquait presque rien** — un chiffre en gras parmi sept anneaux colorés, qui attirent l'œil bien davantage.

Les trois sont corrigées, parce qu'aucune ne suffisait.

### Le titre glisse, mais moins loin que la journée

Il parcourt **le tiers** du déplacement et s'efface à mesure. Le suivre au pixel près le ferait sortir de l'écran par le côté, alors qu'il est précisément le repère qui doit rester lisible pendant le geste ; c'est l'effacement, et non la distance, qui fait sentir que le titre change.

**Il n'est pas dans le geste, il en partage l'état.** Entre le titre et la journée il y a le calendrier, qui a son propre défilement horizontal et ne doit pas bouger : `DaySwipeState` est hoissé hors de `SwipingDay` pour que deux composants distants glissent ensemble sans être voisins.

### Il marque le coup en arrivant

Un glissement lâché à mi-course ramène la même journée ; un jour choisi dans le calendrier n'en fait glisser aucune. Dans ces deux cas, rien ne bouge et pourtant quelque chose s'est passé — ou justement ne s'est pas passé. Une pulsation brève du titre les distingue de l'immobilité.

**Supprimée et non raccourcie** quand l'appareil demande moins de mouvement : un grossissement instantané suivi d'un retour instantané serait un clignotement, ce qui est le contraire du but.

### « Hier » plutôt qu'une date

Les deux jours qui ont un nom le portent. Au-delà d'avant-hier aucun mot ne s'impose — « il y a trois jours » se compte au lieu de se lire — et la date longue reprend la main.

**Un jour égal à aujourd'hui se dit comme aujourd'hui**, alors que la convention de l'écran est de porter `null` pour aujourd'hui. Les deux existent, le calendrier pouvant renvoyer la date du jour, et se contredire d'un chemin à l'autre coûterait plus cher que la redondance.

### Le disque du calendrier

La cellule du jour regardé porte un disque plein, dans la teinte du texte très assourdie — aucune septième couleur dans une figure qui en compte six.

**Il grandit à sa place plutôt que de glisser jusqu'à elle.** Celui qu'on quitte se rétracte pendant que celui qu'on rejoint s'ouvre, et l'œil lit un déplacement : un indicateur qui voyagerait réellement d'une cellule à l'autre supposerait que chacune connaisse la position de sa voisine, ce qu'un `LazyRow` ne dit pas.

**Campagne de défaite : sept sabotages, sept cas tombés.**

**Ce que le vert ne prouve pas.** **Que le glissement du titre se voie**, ni que sa pulsation se remarque : les deux vivent dans la phase de tracé, où aucun cas unitaire n'entre. Ce qui s'éprouve est la progression qui les pilote — bornée, indifférente au sens du geste, nulle tant que la largeur est inconnue — et le libellé qu'ils déplacent.

Rien ne dit non plus **que le disque se voie en plein soleil** à quatorze pour cent d'opacité, ni qu'il ne se confonde pas avec l'anneau qu'il porte.

---

## D122 — Un quartier touché dit ce qui l'a rempli · ✓ validée

**Contexte.** Demandé : « ajouter la possibilité de voir le détail depuis un clic sur l'hexagone : je clique sur le triangle des fibres, il est mis en légère surbrillance et légèrement zoomé, le reste devient un peu plus gris, et une pop-up légère et transparente montre la liste des aliments du jour qui ont donné des fibres, avec à côté de chaque aliment une petite barre comme sous les plats de l'accueil. Au moindre clic à l'extérieur, cette pop-up doit disparaître. »

### La question qu'aucun chiffre ne posait

L'hexagone répond à *comment va ma journée*, les barres à *où j'en suis*. Ni l'un ni l'autre ne dit **d'où ça vient**, et c'est pourtant la seule des trois sur laquelle on puisse agir au repas suivant : savoir qu'il manque dix grammes de fibres ne dit pas s'il faut changer le déjeuner ou ajouter un fruit.

### Ce que la bulle montre, et ce qu'elle tait

**Cinq aliments, du plus gros au plus petit, puis le reste en une ligne.** L'ordre décroissant met la réponse sur la première ligne. Le reste est **cumulé et compté** — « 3 autres, 6 g » — plutôt que tu : sans cette ligne, la somme de ce qu'on lit passerait pour le total du jour, ce qu'elle n'est presque jamais.

**Un aliment, une ligne, doublons cumulés.** Des lentilles au déjeuner et au dîner sont un aliment qui a donné deux fois, pas deux aliments. Le regroupement se fait par **nom affiché** et non par fiche : une fiche manque aux lignes tapées à la main, et la même chose saisie deux fois — une fois depuis une fiche, une fois au clavier — ferait deux lignes que rien ne distingue à l'œil.

**Un zéro connu n'est pas une source** et sort de la liste. Lister le blanc de poulet sous les fibres répondrait à côté de la question.

**Une valeur inconnue est nommée à part, sans barre et sans chiffre.** C'est [D29](#d29--un-total-incomplet-se-signale-au-lieu-de-se-taire---validée) qui revient, et d'une façon qui la sert mieux : depuis [D119](#d119--deux-styles-daffichage-une-part-par-chiffre-et-plus-de-total-minoré-à-laccueil---validée) l'accueil ne signale plus ses totaux minorés, et cette ligne est désormais le seul endroit de l'écran qui dise qu'un total l'est — en disant **par quel aliment**, donc lequel aller corriger. L'information n'est pas revenue là où elle était : elle est arrivée là où elle sert.

**Un aliment mesuré deux fois, dont une sans valeur, est dans les deux listes.** Les deux sont vrais en même temps — il a donné ce qu'on sait, et une part de lui n'est pas renseignée — et taire l'un des deux arrondirait la vérité du mauvais côté.

### La bulle se pose sous la figure, jamais dessus

**C'est la seule règle de placement qui compte.** Recouvrir la figure qu'on vient de mettre en avant reviendrait à annuler la surbrillance au moment précis où elle est demandée.

**La première version se posait du côté opposé au quartier** : un quartier du haut renvoyait la bulle sous le centre, un quartier du bas au-dessus. Elle gardait bien le quartier touché visible, et recouvrait les cinq autres. Sur une journée à six aliments la bulle fait deux cents points de haut ; posée au-dessus du centre elle bute sur le haut de la zone et mange la figure presque entière. **Constaté sur appareil, et invisible depuis les cas** — aucun ne mesure une bulle.

Se poser sous la figure demande de la place, et le bloc de l'hexagone seul n'en a pas : **la zone de placement va de la figure jusqu'au bas des six barres.** C'est ce qui permet à la bulle de descendre plutôt que de remonter.

**La pointe suit le quartier, le corps suit l'écran**, et les deux se désolidarisent dès que la bulle bute sur un bord. L'inverse — une bulle qui sort de l'écran pour rester centrée sur sa pointe — était la seule chose à éviter sur une figure aussi large que l'écran.

**La pointe est tracée par le parent, pas par la bulle.** Sa position le long du bord n'est connue qu'une fois la bulle mesurée et placée, alors que le fond, lui, doit être décrit avant. Faire redescendre cette position dans une forme obligerait à recomposer l'enfant pour un nombre que la mise en page vient de calculer — un aller-retour visible à l'image près. Le parent, lui, dessine après.

**Aucun voile derrière elle.** Une modale assombrit ce qu'elle recouvre pour annoncer qu'un appui à côté la referme ; celle-ci ne le fait pas, et c'est ce qui lui permet de changer de macro sans faire clignoter l'écran entier.

### Le doigt, par l'angle et non par le triangle

Un test d'appartenance au triangle exact refuserait les appuis tombés entre l'arête et la lettre qui la surplombe, alors que **viser un « F » est la façon la plus naturelle de désigner les fibres**. Les six secteurs se partagent donc tout le disque, et c'est la distance seule qui dit si l'appui est dans la zone. Le rayon de cette zone est celui des lettres, calculé par la même fonction que le tracé — deux calculs du même cercle produiraient une figure juste et une cible décalée, écart qu'aucun cas ne verrait.

### Quatre cas pour un appui

- **hors de la figure** — il referme ;
- **un quartier sans rien à montrer** — il ne répond pas, et ne referme donc pas non plus ce qui était ouvert : un quartier vide est un quartier qui n'est pas là ;
- **celui qu'on regardait** — le même geste referme ce qu'il a ouvert ;
- **un autre** — la bulle reste et change de contenu, sans passer par le vide, ce qui permet de parcourir les six sans jamais rien refermer.

**« Rien à montrer » n'est pas « le quartier est à zéro ».** Une macro qu'aucun aliment n'a apportée n'a rien à dire ; une macro dont un aliment ne renseigne pas la valeur en a beaucoup, alors que la figure les dessine toutes les deux vides.

### Les barres ouvrent la même bulle

**Toucher un triangle est un geste que rien n'annonce** — la règle que le projet applique déjà au retour à aujourd'hui ([D102](#d102--le-retour-à-aujourdhui-a-une-porte-visible-et-le-jour-regardé-a-un-contrat---validée)) et qu'il avait opposée au balayage qui supprimait. Une barre pleine largeur est une cible qu'on trouve sans la connaître, et que le lecteur d'écran annonce déjà par son nom et sa valeur. Le triangle reste le raccourci de qui le connaît.

La figure, elle, porte **six actions personnalisées** plutôt que six zones tactiles invisibles : un dessin ne se découpe pas en six vues, et des rectangles posés par-dessus des triangles se disputeraient le doigt avec lui.

**Campagne de défaite : vingt-sept sabotages, vingt-sept cas tombés.**

### Ce que l'appareil a montré, et que le vert taisait

Quatre défauts, dont aucun ne se voit depuis un cas — ils tiennent tous à des tailles et à des couches, que rien ne mesure hors d'un écran.

- **Le placement**, ci-dessus : la bulle mangeait la figure.
- **Les boutons flottants passaient par-dessus.** Ils sont une couche du `Scaffold`, dessinée après le contenu ; posés sur la colonne des chiffres, ils la rendaient illisible. Ils s'effacent désormais le temps d'une lecture — ce qui dit aussi la vérité : on ne note rien pendant qu'on regarde d'où vient une macro.
- **La transparence demandée se retournait contre elle-même.** À 95 %, le restant en calories — blanc, cinquante-sept points — traverse la bulle et se lit aussi bien que ce qu'elle écrit. Deux textes qui se disputent le même pixel ne font pas une bulle légère. À 97 %, on devine qu'il y a quelque chose dessous, ce qui est tout ce qu'on lui demande.
- **Un appui sur la bulle changeait de macro** : elle est posée sur la figure et sur les barres, et le geste passait au travers. Elle avale désormais les appuis. **Un cas reste non expliqué** : la bascule a été observée une fois, puis n'a pas reparu en trois reprises de la même séquence.

**Conséquences.** `DishSummary.share` et la part d'une ligne de bulle sont la même règle, écrite une fois. `MacroHexagon.kt` se sépare en trois : la figure, sa géométrie, et ce que la sélection lui fait. `HomeScreen.kt` se sépare en deux : l'écran, et les compteurs du jour.

**Ce que le vert ne prouve pas.** **Que la bulle soit légère et non envahissante**, ce qui était la demande. Sept écrans ont été regardés ; aucun cas ne juge une carte de 260 dp, ni son fond à trois pour cent de transparence, ni la lisibilité d'un nom d'aliment tronqué sur un écran de 360.

Rien ne dit **qu'un quartier presque vide se laisse viser** : son triangle est fin près du centre, et c'est la portée angulaire qui le sauve — une règle qui s'éprouve mais dont le confort réel ne s'éprouve pas.

Rien ne dit non plus **que l'appui à côté referme dans tous les cas**. La règle est que tout appui non consommé par un enfant referme ; un plat, une barre ou une pastille consomment le leur, donc la bulle leur survit le temps d'une navigation. C'est le comportement voulu, mais il dépend de l'ordre de consommation des événements, qu'aucun cas unitaire ne parcourt — et c'est précisément là qu'un appui sur la bulle a changé de macro une fois, sans qu'on sache le refaire.

---

## D123 — Le scan ne parle plus à Google · ✓ validée

**Contexte.** En préparant la politique de confidentialité, la liste des flux sortants a été relue contre l'APK plutôt que contre [09](09-donnees-et-sauvegarde.md#ce-qui-sort-de-lappareil). Le manifeste fusionné portait un service que personne n'avait écrit : `com.google.android.datatransport`, le transport par lequel ML Kit envoie à Google, en HTTPS, le fabricant et le modèle de l'appareil, sa version d'Android, le nom et la version de l'application, **un identifiant par installation**, des temps de traitement, des événements et des codes d'erreur — « pour diagnostics et analyses d'usage », selon sa propre page de divulgation. Aucun réglage documenté ne le coupe.

Trois promesses écrites le démentaient : « sans télémétrie » en tête du README, « Zéro collecte » dans les contraintes fermes de [01](01-perimetre.md#contraintes-fermes), « aucun autre trafic sortant » dans [09](09-donnees-et-sauvegarde.md#ce-qui-sort-de-lappareil).

**Choix.** Retirer ML Kit, et lire les codes-barres avec **zxing-cpp** : libre (Apache-2.0), maintenu, entièrement sur l'appareil, sans réseau ni services Google.

**Écarté.** *Déclarer l'envoi* : la fiche du Play Store perdait « aucune donnée collectée », et la règle européenne sur l'accès au terminal demande en principe un accord préalable pour ce qui n'est pas strictement nécessaire au service — un accord qu'on n'aurait pas pu honorer, puisque l'envoi ne se coupe pas. *Garder ML Kit en arrachant son transport* : rien ne garantit que la bibliothèque le tolère d'une version à l'autre, et la panne ne se verrait qu'à l'exécution, le vert n'ayant pas bougé — le cas de figure de [10](10-qualite-et-livraison.md#gradle). *ZXing en Java* : sa source de luminance ne sait pas tourner l'image, et il aurait fallu redresser chaque trame à la main.

### La 2.3.0, parce que la chaîne de D15 décide

Les 3.x tirent CameraX 1.5 et `kotlin-stdlib` 2.2 ou 2.3 : le premier exige `compileSdk 36`, le second un compilateur que [D15](#d15--chaîne-de-construction-alignée-sur-loutillage-installé---par-défaut) n'a pas. La 2.3.0 s'accorde à la chaîne actuelle — CameraX 1.4.1, Kotlin 1.9 — et la montée suivra celle de l'outillage. Ses bibliothèques natives 64 bits sont alignées sur 16 Ko, ce que le Play Store exige des applications qui visent Android 15.

### Deux efforts que ML Kit faisait sans qu'on les demande

L'emballage Android de zxing-cpp part avec `tryRotate` et `tryHarder` éteints, là où la bibliothèque C++ les allume. Sans le premier, un paquet tenu de travers n'est plus lu ; ML Kit le lisait dans toutes les orientations. Les deux sont allumés.

### Le fil a changé, et c'est la seule vraie différence

L'écouteur de ML Kit s'exécutait sur le fil principal, et c'est ce qui rendait légal ce que la session en faisait : délier la caméra, écrire l'image figée. zxing-cpp lit **de façon synchrone, sur le fil d'analyse**. Trois conséquences :

- **le code lu traverse jusqu'au fil principal** avant d'atteindre la session ;
- **l'anti-rebond reste sur un seul fil**, celui de l'analyse, y compris sa reprise, que la session y envoie au lieu de la faire elle-même. L'appeler depuis deux fils aurait demandé de le synchroniser ; le confiner n'a rien demandé ;
- **rien n'est livré après la fermeture de l'écran.** Un code confirmé à l'instant où la modale se referme est déjà en route ; ML Kit l'aurait perdu avec son client, zxing-cpp n'en a pas. Un drapeau posé par `release` l'arrête.

La trame figée est toujours capturée une seule fois par scan, sur l'image qui a porté l'accord — désormais sur le fil d'analyse, avant que l'image se referme.

**Conséquences.** [D65](#d65--le-décodeur-est-un-module-à-part-et-sa-seule-règle-tient-sur-la-jvm---validée) l'avait chiffré à « une classe », et c'est ce qu'il a coûté : `BarcodeAnalyzer` change de décodeur, `CameraSession` de fil, `SteadyBarcode` ne change pas d'une ligne de code. Le manifeste fusionné ne porte plus ni `mlkit` ni `datatransport`. [01](01-perimetre.md#contraintes-fermes), [09](09-donnees-et-sauvegarde.md#ce-qui-sort-de-lappareil) et le README disent de nouveau vrai.

~~**Ce que le vert ne prouve pas.** **Que zxing-cpp lise aussi bien que ML Kit** : code de biais, reflet sur un emballage, étiquette froissée, pénombre. C'est la seule inconnue, et elle ne se lève qu'en tenant le téléphone devant de vrais paquets — aucune bibliothèque native ne tourne sur la JVM. Revenir en arrière tiendrait en un commit, mais revenir, ce serait redéclarer l'envoi.~~

**Éprouvé sur l'appareil le 24 septembre 2026** : le scan fonctionne. L'inconnue est levée, et le retour en arrière n'a plus de raison d'être envisagé.

Rien ne dit non plus que la reprise tombe avant la première image : la file d'un exécuteur à un seul fil le garantit, et c'est un raisonnement, pas un cas.

---

## D124 — La politique de confidentialité vit à côté du code, et le site ailleurs · ✓ validée

**Contexte.** Le Play Store exige une politique de confidentialité pour toute application, même une qui ne collecte rien, avec un lien dans la console **et** dans l'application. Google exige de son côté, pour vérifier la marque d'une application qui se connecte à Drive, une page d'accueil et une politique **sur un même domaine** que l'auteur possède et prouve. Ni l'un ni l'autre ne demande de conditions d'utilisation. [09](09-donnees-et-sauvegarde.md#politique-de-confidentialité) prévoyait « un fichier Markdown dans le dépôt, publié via GitHub Pages ».

**Choix.** Deux dépôts, et la ligne de partage passe par ce qui change avec le code.

- **Le site vit dans `hexavore-app/hexavore-site`** : vitrine, mentions légales, et la page qui publie la politique. Il n'a ni le rythme, ni l'outillage, ni la licence du code, et ses images n'ont rien à faire dans l'historique que clone chaque contributeur de l'application.
- **Le texte de la politique vit ici**, dans `confidentialite/fr.md` et `confidentialite/en.md`. C'est le seul texte du site qui devient faux quand le code change — un flux ajouté sans lui, et il ment. Ici, il change dans le même PR que le flux, comme [10](10-qualite-et-livraison.md#documentation) l'exige de toute documentation.
- **Le site le récupère à chaque publication**, et ce dépôt lui demande de se republier quand le fichier change sur `main` (`.github/workflows/politique.yml`). Un déclenchement plutôt qu'une tâche planifiée : GitHub désactive celles d'un dépôt public resté soixante jours sans activité, et un site vitrine l'est souvent.

**Écarté.** *Tout dans ce dépôt* : c'était la première recommandation, faite pour la seule politique ; le reste du site n'avait aucune raison d'y être. *La politique dans le dépôt du site* : deux PR dans deux dépôts pour un seul flux, c'est une règle de vigilance, et [06](06-architecture.md#pourquoi-autant-de-modules) dit ce qu'il en advient. *Un sous-domaine `github.io`* : la vérification de marque de Google exige un domaine qu'on possède et qu'on prouve ; ce sera `hexavore.app`.

**La politique dit ce que l'application fait aujourd'hui**, et rien de ce qu'elle fera. Drive n'y est pas, parce qu'il n'existe pas ; il y entrera avec lui. La vérification de mise à jour que [10](10-qualite-et-livraison.md#variantes-de-build) promet à la variante GitHub n'existe pas davantage — [09](09-donnees-et-sauvegarde.md#ce-qui-sort-de-lappareil) disait le contraire — et y entrera le jour où elle existera.

**Conséquences.** Le tableau des flux de [09](09-donnees-et-sauvegarde.md#ce-qui-sort-de-lappareil) passe de trois lignes à cinq : la recherche par nom ([D67](#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)) et la contribution ([D90](#d90--la-contribution-part-sous-le-compte-de-lutilisateur-et-jamais-sans-lui---validée)) y étaient entrées sans qu'il le dise. **Le lien vers la politique dans l'application reste à poser** : l'écran « À propos » de [02](02-parcours-et-ecrans.md#réglages) n'existe pas, et Play l'exige.

**Ce que le vert ne prouve pas.** Rien, ici, ne s'éprouve depuis le dépôt. Le déclenchement demande un jeton que seul Charly peut créer — `SITE_DISPATCH_TOKEN`, avec le droit d'écrire sur `hexavore-site` — et la publication un domaine qui n'est pas encore acheté. Tant que le jeton manque, le workflow échoue à chaque changement de la politique, et c'est voulu : il ne doit pas se taire.

---

## D125 — Le site se republie par une clé de déploiement, pas par un jeton · ✓ validée

**Contexte.** [D124](#d124--la-politique-de-confidentialité-vit-à-côté-du-code-et-le-site-ailleurs---validée) faisait demander la republication par un appel d'API, authentifié par un jeton à accès fin. Un jeton expire — un an au plus pour ceux-là — et le jour où il expire, la politique publiée prend du retard sur le code sans que personne l'ait décidé.

**Choix.** Une **clé de déploiement** du dépôt du site, en écriture. Ce dépôt y pousse un commit vide quand la politique change sur `main`, et c'est cette poussée qui déclenche la publication.

**La poussée déclenche parce que la clé n'est pas le jeton de l'exécution.** Un commit poussé avec `GITHUB_TOKEN` ne déclenche aucun workflow — c'est la garde de GitHub contre les boucles. Une clé de déploiement n'en est pas un : sa poussée est une poussée comme une autre.

**Écarté.** *Le jeton à accès fin* : un seul secret et aucun changement de workflow, mais une échéance à retenir, et rien ne la rappelle avant la panne. *Une application GitHub* : elle ne périme pas davantage, mais c'est un objet de plus à créer et à entretenir pour une poussée tous les six mois.

**Ce que ça coûte.** Un commit vide dans l'historique du site à chaque changement de politique. Le coût rapporte : cet historique dit désormais quand le site a été republié et pour quelle version de l'application, là où l'appel d'API ne laissait aucune trace.

**Conséquences.** Le secret s'appelle `SITE_DEPLOY_KEY` et porte la moitié privée de la clé ; la moitié publique est une clé de déploiement de `hexavore-site`, avec écriture. Le README du site remplace le jeton par elle.

**Ce que le vert ne prouve pas.** Rien n'est éprouvé tant que le secret n'est pas posé. Sans lui, l'étape échoue — et c'est voulu : une politique en retard sur le code ne doit pas passer inaperçue.

---

## D126 — L'icône de lancement montre les six compteurs · ✓ validée

**Contexte.** L'icône était un hexagone vide : un contour de 6 unités sur un canevas de 108, cyan sur un fond presque noir. Sur un lanceur, à 48 px, il n'en restait qu'un carré sombre. Le tracé était en plus orienté pointe en haut, donc tourné de 30° par rapport à l'hexagone de l'application et à la favicon du site, qui ont une arête horizontale. Et la variante monochrome, celle qu'Android 13 reteint avec le fond d'écran, reprenait ce même contour fin.

**Choix.** Les six quartiers pleins, dans l'ordre angulaire de l'application, séparés par une fente qui laisse passer le fond. C'est la figure de l'accueil à son état plein, celle que le site montre déjà dans son onglet et dans son en-tête.

### La fente n'est pas un ornement

Sucres et glucides sont deux violets parents, et voisins de place : [08](08-design-system.md#macros) dérive délibérément le premier du second pour que la parenté se voie. À 48 px, sans séparation, les deux quartiers se touchent et forment une seule tache violette, et la figure cesse de compter jusqu'à six. La fente est de 3,2 unités mesurées perpendiculairement, soit un dixième du rayon, et elle laisse voir le fond plutôt que de peindre un trait : une fente opaque aurait supposé connaître la couleur qui passe derrière, ce qu'un lanceur ne garantit pas.

### L'icône n'est pas la figure

`MacroHexagon` montre une journée : des quartiers de rayons inégaux, sans fente, et le site l'affiche telle quelle. L'icône montre l'état plein, parce qu'une marque ne représente aucune journée en particulier. Les deux se ressemblent sans être le même objet, et [08](08-design-system.md#licône-de-lancement) dit désormais lequel est lequel.

**Écarté.** *L'anneau à six arêtes*, chaque arête dans sa teinte et le centre sombre : il respectait mieux la deuxième règle du parti pris, celle qui interdit les aplats saturés larges, mais il s'éloignait de la favicon du site pour un gain qui ne se voyait qu'au-dessus de 96 px. *Le contour épaissi*, d'une seule couleur : le minimum de changement, et six compteurs toujours invisibles. *La journée d'exemple*, aux quartiers inégaux : la plus parlante en grand, la plus brouillonne en petit.

**Conséquences.** `colors.xml` porte les six teintes au lieu d'une seule ; elles doivent rester égales à `MacroColors.kt`, qui reste la source. Un second vecteur, `ic_launcher_monochrome`, porte le même tracé pour les icônes thématiques : réduire cette variante à un hexagone plein aurait fait perdre, sur les téléphones où elle est active, la seule chose que l'icône raconte.

`LauncherIconTest` tient les trois choses qu'aucun compilateur ne vérifie : que les teintes des ressources soient celles du thème, que les six quartiers tiennent dans le disque de 66 que le système garantit visible sous tous les masques, et que la variante monochrome porte le même tracé que la colorée. Les fichiers y sont lus comme du texte, et non chargés en ressources : ce qui est éprouvé est ce qui est écrit dans le dépôt, et c'est la seule forme qui attrape une teinte corrigée d'un côté et pas de l'autre.

~~**Ce que le vert ne prouve pas.** Le rendu sur un vrai lanceur : masque de l'OEM, relief, agrandissement au toucher, icônes thématiques activées. La géométrie est tenue, l'apparence ne se juge qu'à l'écran.~~

**Vu sur l'appareil le 24 septembre 2026** : l'icône est à jour et se lit.

---

## D127 — La photo reste avec le plat, sur le téléphone · ✓ validée

**Contexte.** Un scan et une analyse par photo produisent tous deux une image du repas, et l'application la jetait. La quatrième contrainte ferme de [01](01-perimetre.md#contraintes-fermes) l'imposait : « aucune photo n'est conservée ». Or cette phrase disait **deux choses à la fois**, et une seule protégeait quelqu'un. Que rien ne parte chez un tiers sans un geste : c'est la promesse, et elle tient. Que rien ne reste sur le téléphone : ce n'est pas une protection, c'est une perte, et elle était payée par l'utilisateur au nom de sa propre confidentialité. Un journal alimentaire qui garde le nom d'un plat mais jette son image oblige à reconnaître « Déjeuner 2 » sur une liste de titres déduits de l'heure.

**Choix.** À la validation d'un plat scanné ou analysé, **son image reste sur le téléphone**, à côté du journal. L'accueil la montre en vignette, l'écran de validation la montre en grand et permet de la retirer, et une section des réglages permet de n'en garder aucune ou de toutes les effacer.

**Allumé par défaut.** La fonctionnalité éteinte n'existe pas : personne ne va chercher dans les réglages une chose dont il ignore l'existence. Le réglage protège ceux qui n'en veulent pas, et il est visible dans une section qui porte leur nom.

### Ce qui quitte le téléphone n'a pas changé

C'est le point qui décide si la contrainte tombe pour de bon ou se contourne. Le tableau des flux sortants de [09](09-donnees-et-sauvegarde.md#ce-qui-sort-de-lappareil) est **identique** : une photo part chez un fournisseur d'IA quand on lance une analyse, et nulle part ailleurs. Une photo gardée est un fichier de plus dans `filesDir`, au même titre que la base.

L'écran de permission de la caméra le dit maintenant en toutes lettres : le scan lit le code-barres sur le téléphone, aucune image ne part, celle qui a porté la lecture reste avec le plat si on le valide, et se retire d'un geste.

### Le nom du fichier est l'identifiant du plat

Il n'y a **pas de colonne** qui dirait qu'un plat a une photo. Le disque est la seule vérité, et c'est ce qui rend la base et les fichiers impossibles à désaccorder : aucune colonne ne peut annoncer une image absente, aucun fichier ne peut se rattacher au mauvais plat, et une restauration qui ramène les mêmes identifiants retrouve les mêmes photos sans que rien n'ait à les apparier.

*Écarté* : une colonne `photo` sur `dish`. Elle aurait coûté une migration, et surtout elle aurait pu mentir — un fichier effacé par ailleurs, une restauration sans images, et l'accueil montre un cadre vide sans savoir pourquoi.

### L'image attend son plat dans un emplacement unique

Une analyse produit son image avant que le plat existe, donc avant que son identifiant existe. Elle est **déposée** dans un fichier de brouillon, et l'enregistrement la **range** sous le nom du plat, par un renommage.

Un fichier plutôt qu'un objet en mémoire : il survit à un processus tué entre les deux écrans, comme le reste du brouillon. Le prix est le seul vrai défaut du mécanisme : **une analyse abandonnée laisse son image dans le dépôt**, et la saisie manuelle suivante l'aurait adoptée. C'est `OpenDraftPhoto` qui le tient, en écartant le dépôt pour toute origine qui n'apporte pas d'image — saisie neuve, favori rejoué, plat rouvert. Trois cas de `DishPhotoTest` défendent cette règle, et la retirer les fait tomber tous les trois.

### Rien n'est effacé au moment où l'on s'y attendrait

**Supprimer un plat ne supprime pas sa photo.** La suppression est rattrapable par une barre d'annulation, et une image effacée dans l'intervalle ne reviendrait pas.

**Restaurer une sauvegarde non plus.** C'est ce qui laisse revenir à la copie de sécurité avec ses images, alors que celle-ci ne porte que le journal.

C'est le **balayage du démarrage** qui retire les photos dont le plat n'existe plus, et il est le seul à le faire. Les deux règles ci-dessus sont ses seules raisons d'être là et nulle part ailleurs.

### La sauvegarde devient une archive, et ne passe plus par la mémoire

L'export écrit un zip : le journal sous son nom habituel, `hexavore.json.gz`, et un dossier `photos/` à côté. Le journal reste donc extractible et réparable à la main, ce que [09](09-donnees-et-sauvegarde.md#format-de-sauvegarde) exige depuis toujours ; le zip ne le rend pas opaque.

**Le journal est la première entrée de l'archive**, et c'est ce qui permet de refuser un fichier trop récent avant d'avoir écrit la moindre image.

**`formatVersion` ne bouge pas** : le JSON ne change pas d'un champ, c'est l'enveloppe autour de lui qui est neuve. Un ancien `.json.gz` se reconnaît à ses premiers octets et se relit sans rien demander.

**Des flux et non des tableaux d'octets.** Un an de journal tient sous les cent kilo-octets, un an de photos pèse deux cents mégaoctets à trois repas par jour. Porter cela en mémoire ferait tomber l'application chez ceux qui ont le plus à sauvegarder. `ExportBackup` reste en octets pour la copie de sécurité interne, qui ne porte que le journal.

**L'export fige le journal avant d'ouvrir le document**, et c'est pour cela qu'`ExportArchive` rend un écrivain plutôt que d'écrire lui-même : ce qui part décrit l'instant de la demande. Les photos, elles, sont lues à l'écriture — une image prise entre les deux instants entre dans l'archive sans que son plat y soit, en ressort orpheline, et le balayage suivant l'emporte.

**Écarté.** *Base64 dans le JSON* : tout revient, et le fichier cesse d'être lisible à l'œil au moment précis où il devient assez gros pour qu'on veuille l'ouvrir. *Ne rien emporter* : une restauration sur un nouveau téléphone rendrait le journal sans les images, ce qui est exactement le cas où l'on en a le plus besoin.

### Ce que la taille coûte, et pourquoi elle est dite

L'image gardée est celle qui est partie au modèle : 1 024 px, environ 200 Ko. À trois repas par jour, cela fait de l'ordre de 200 Mo au bout d'un an. C'est beaucoup, et c'est pourquoi l'écran des réglages **dit ce que les photos occupent** au lieu de laisser deviner : « garder les photos » ne veut rien dire tant qu'on ne sait pas ce que ça coûte, et personne ne va compter ses fichiers.

**Conséquences.** `DishPhotos` et `PhotoSettings` sont deux ports de `:domain` ; leur adaptateur vit dans `:data:diary`, parce qu'une photo est l'accessoire d'un plat et que la loger ailleurs aurait inventé un module dont le seul contenu serait un dossier. `DiaryRepository` gagne `dishIds()`, que seul le balayage appelle. `SaveDraft` rend désormais le plat écrit, ou `null` quand il vient d'être vidé : une photo rangée sous un plat qui disparaît serait orpheline à la naissance. `RestoreBackup` disparaît au profit de `RestoreArchive`, qui fait la même chose en lisant un flux. La politique de confidentialité change dans la même livraison, et le site se republie avec elle.

~~**Ce que le vert ne prouve pas.** La lecture des fichiers à l'écran : la vignette de 56 dp décodée au huitième de sa taille, la croix qui se vise sur un carré de 132 dp, et ce que deux cents mégaoctets de photos font au démarrage d'un téléphone qui n'est pas neuf.~~

**Éprouvé sur l'appareil le 24 septembre 2026** : le stockage des photos fonctionne, de la capture à l'affichage.

**Ce qu'il reste à voir** : ce que deux cents mégaoctets d'images font au démarrage. Le balayage est éprouvé sur la JVM ; son coût au lancement se mesurera sur un dossier chargé, pas sur un neuf, donc dans plusieurs mois d'usage.

---

## Décisions prises par défaut, à confirmer

Ces points n'ont pas été arbitrés explicitement. J'ai tranché pour que la spécification soit complète et cohérente ; chacun se change sans rien casser à ce stade.

| # | Sujet | Retenu | Où |
|---|---|---|---|
| 1 | Formule de dépense | Mifflin-St Jeor | [03](03-nutrition-calculs.md) |
| 2 | Presets d'objectif | Perdre / Maintenir / Prendre | [02](02-parcours-et-ecrans.md#onboarding) |
| 3 | Garde-fous | 1 %/sem, ±25 % du TDEE, plancher 1200/1500 | [03](03-nutrition-calculs.md#garde-fous) |
| 5 | Séances de sport ponctuelles | Non — multiplicateur d'activité seulement | [03](03-nutrition-calculs.md#dépense-énergétique-totale-tdee) |
| 7 | Unités | g, ml, et portions nommées | [04](04-sources-de-donnees.md#portions-usuelles) |
| 8 | Récents | 20 aliments, tri par date d'usage | [02](02-parcours-et-ecrans.md#modale--recherche) |
| 9 | Plats favoris | Oui, réutilisables en un tap | [07](07-modele-de-donnees.md) |
| 10 | Saisie dans le futur | Non — aujourd'hui et passé uniquement | [02](02-parcours-et-ecrans.md) |
| 12 | Sucres | Sucres totaux, en plafond OMS 10 % | [03](03-nutrition-calculs.md#sucres) |
| 13 | Contribution à Open Food Facts | ⊘ **remplacée par [D70](#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée)** — elle entre en tranche 6 | [04](04-sources-de-donnees.md#produit-absent) |
| 14 | Quantité par défaut au scan | Portion de l'emballage, sinon 100 g | [02](02-parcours-et-ecrans.md) |
| 15 | Fournisseurs d'IA | Gemini, OpenAI, Anthropic, DeepSeek, Mistral + compatible | [05](05-ia.md#fournisseurs) |
| 17 | Compteur de coût | Oui, estimation locale datée | [05](05-ia.md#coût) |
| 19 | Sans clé API | Modes IA visibles mais grisés, avec explication | [02](02-parcours-et-ecrans.md#écran-dia) |
| 21 | Sauvegarde Drive | Quotidienne en Wi-Fi, chiffrement optionnel désactivé par défaut | [09](09-donnees-et-sauvegarde.md) |
| 22 | Export local | JSON complet réimportable + CSV du journal | [09](09-donnees-et-sauvegarde.md#export-et-import-de-fichier) |
| 23 | Versions de sauvegarde | 5, en rotation | [09](09-donnees-et-sauvegarde.md#rotation) |
| 24 | Télémétrie | Aucune, y compris crash reporting | [01](01-perimetre.md#contraintes-fermes) |
| 26 | Progression | Hexagone en tête, barres pour les valeurs — voir D33 | [08](08-design-system.md#macrohexagon) |
| 27 | Langues | Français et anglais dès la 1.0 | [01](01-perimetre.md#plateforme) |
| 28 | Widget et notifications | Hors v1, widget en tête de la 1.1 | [10](10-qualite-et-livraison.md#feuille-de-route) |
| 29 | Android minimum | API 26 | [01](01-perimetre.md#plateforme) |
| 30 | Nom | **Hexavore** — tranché, voir [D105](#d105--le-nom-devient-hexavore-et-le-dépôt-change-dadresse---validée) | — |
| 31 | `applicationId` | `app.hexavore` | [10](10-qualite-et-livraison.md#identité-de-lapplication) |
