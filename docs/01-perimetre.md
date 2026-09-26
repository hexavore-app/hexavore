# 01 — Périmètre

## L'intention

Une personne qui veut suivre son alimentation abandonne presque toujours pour la même raison : **la saisie coûte trop cher**. Trente secondes par repas, trois repas par jour, et au bout de deux semaines l'application est désinstallée.

Tout le reste découle de là. Chaque décision de conception se juge à une question : *est-ce que ça rapproche ou éloigne du « manger noté en cinq secondes » ?*

Conséquences concrètes, visibles partout dans le reste de la documentation :

- Le chemin le plus court est toujours disponible depuis l'écran d'accueil, sans navigation.
- Les aliments récents et les plats favoris passent avant la recherche.
- L'IA propose, elle ne décide pas : un écran de validation, éditable, précède tout enregistrement — mais il est pré-rempli, donc valider coûte un tap.
- Une donnée absente n'est jamais bloquante. Un produit sans valeur de fibres s'enregistre quand même, avec le trou visible.

## Utilisateur cible

Adulte en bonne santé qui suit son alimentation pour une raison personnelle : perte de poids, prise de masse, curiosité, discipline sportive. Il sait lire une étiquette mais n'est pas diététicien.

**Ce n'est pas** un outil clinique. Pas de pathologie, pas de prescription, pas de suivi médical. Un avertissement le dit explicitement à l'onboarding et dans les réglages.

## Dans la v1

### Profil et objectifs
- Onboarding : âge (via date de naissance), sexe, taille, poids actuel, niveau d'activité.
- Poids cible et échéance (+3 / +6 / +12 mois, ou date libre).
- Calcul automatique des six objectifs quotidiens.
- Modification manuelle de n'importe quel objectif, à tout moment.
- Journal de poids et courbe de tendance.
- Suggestion d'ajustement hebdomadaire, acceptée ou refusée par l'utilisateur.

### Saisie
- **Code-barres** — caméra, décodage à la volée, fiche Open Food Facts.
- **Photo** — l'IA liste les aliments et les quantités, la base fournit les macros.
- **Recherche** — 3 500 aliments CIQUAL + produits déjà scannés + aliments personnels, recherche insensible aux accents, résultats instantanés hors-ligne.
- **Texte libre** — « deux œufs, une tranche de pain et un verre de jus d'orange ».
- **Récents et favoris** — ré-ajout en un tap, quelle que soit l'origine de la saisie initiale.
- **Plats favoris** — enregistrer un plat sous un nom, le rejouer plus tard.
- Édition manuelle intégrale de toute ligne enregistrée.
- Création d'un aliment personnel de toutes pièces.

### Consultation
- Écran du jour : restant sur les six compteurs, plats et leurs apports.
- Bande calendrier horizontale, coloration selon l'atteinte, extensible en vue mensuelle.
- Consultation et modification de n'importe quelle journée passée.

### Réglages
- Thème, langue.
- Clés API des fournisseurs d'IA, choix du modèle, compteur de consommation.
- Sauvegarde Google Drive, export et import de fichier.
- Liens dépôt, licences, don (variante hors Play Store), avertissement médical.

## Hors v1

Écarté sciemment. Chaque ligne indique la raison, pour que la décision puisse être rejugée plus tard sur autre chose qu'une impression.

| Fonctionnalité | Pourquoi pas maintenant |
|---|---|
| Micronutriments (vitamines, minéraux) | Le modèle de données les accueille déjà ; c'est l'interface qui n'est pas prête à afficher trente compteurs sans devenir illisible. |
| Sucres ajoutés | La donnée n'existe quasiment pas, ni dans Open Food Facts ni dans CIQUAL. Afficher un compteur vide à 95 % serait pire que ne rien afficher. |
| Suivi de l'exercice, import de podomètre | Le niveau d'activité couvre 90 % du besoin. Une intégration Health Connect est une fonctionnalité à part entière. |
| ~~Contribution de produits à Open Food Facts~~ | **Rentrée dans le périmètre**, en tranche 6 ([D70](11-decisions.md#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée)). Le travail reste réel — compte OFF, champs obligatoires, conflits — mais la couverture d'Open Food Facts s'effondre hors d'Europe : 10 911 produits en Thaïlande contre 1 257 548 en France. Là-bas, la saisie manuelle est la route principale, et ne pas la reverser condamne chaque utilisateur à la refaire seul. |
| Partage social, communauté, défis | Hors intention. |
| iOS, Wear OS, widget | Le widget est le premier candidat pour la v1.1. |
| Codes-barres de restaurants, menus de chaînes | Dépend de bases commerciales. |
| Jeûne intermittent, fenêtres alimentaires | Fonctionnalité distincte, mérite sa propre conception. |
| Fusion multi-appareils | La restauration v1 remplace, elle ne fusionne pas. La fusion demande une résolution de conflits sérieuse (voir [09](09-donnees-et-sauvegarde.md)). |

## Contraintes fermes

Ces contraintes ne se négocient pas en cours de route ; elles conditionnent l'architecture.

1. **Aucun serveur.** L'application n'a pas de backend. Rien à héberger, rien à payer, rien à faire fuiter. Les seuls appels réseau sortants vont vers Open Food Facts, le fournisseur d'IA choisi par l'utilisateur, et Google Drive.
2. **Utilisable hors-ligne.** Recherche, saisie, consultation, objectifs : tout fonctionne en mode avion. Seuls le scan d'un produit inconnu et l'IA demandent du réseau.
3. **La clé API ne quitte jamais l'appareil.** Elle n'est ni sauvegardée sur Drive, ni exportée, ni journalisée.
4. ~~**Aucune photo n'est conservée.** Le fichier temporaire est supprimé dès la réponse du modèle reçue.~~ **Aucune photo ne quitte l'appareil sans un geste** ([D127](11-decisions.md)). La contrainte disait deux choses à la fois, et une seule protégeait quelqu'un : ce qui part chez un tiers ne part que sur demande, et c'est tenu. Ce qui reste sur le téléphone, en revanche, était effacé au nom de la confidentialité alors que l'utilisateur voulait le garder. La photo envoyée à un modèle est toujours supprimée dès la réponse reçue ; celle qui accompagne un plat validé reste sur le téléphone, s'efface d'un geste, et un réglage permet de n'en garder aucune.
5. **Zéro collecte.** Pas d'analytics, pas de crash reporting automatique, pas d'identifiant publicitaire.

## Critères d'acceptation de la v1

Formulés pour être vérifiables, pas pour faire joli.

- Ajouter un aliment déjà connu depuis l'accueil prend **≤ 3 taps** et **≤ 5 secondes**.
- Un scan de code-barres présent dans Open Food Facts affiche la fiche en **≤ 2 s** sur une connexion 4G correcte, et **instantanément** s'il a déjà été scanné.
- La recherche par nom affiche des résultats en **≤ 150 ms**, hors-ligne, dès le **2ᵉ caractère**. Le délai se compte à partir de la fin de l'anti-rebond, pas de la frappe : les 120 ms d'attente de [02](02-parcours-et-ecrans.md#modale--recherche) sont un choix, pas une lenteur.
- Une photo produit une proposition éditable en **≤ 10 s**.
- Aucun écran ne perd de données saisies lors d'une rotation ou d'un passage en arrière-plan.
- L'application démarre en **≤ 1 s** sur un appareil de milieu de gamme de 2021.
- Toutes les fonctions listées « dans la v1 » sont utilisables intégralement au clavier et au lecteur d'écran TalkBack.

## Plateforme

- **minSdk 26** (Android 8.0, août 2017). Couvre ~99 % du parc actif et évite le désendettement de compatibilité que traînent les API 21-25 (`java.time`, notifications, Keystore).
- **targetSdk 36** (Android 16). Ce n'est pas un confort : le Play Store l'exige d'une nouvelle application depuis le 31 août 2026, et un bundle qui vise moins est refusé ([D128](11-decisions.md)).
- Orientation portrait uniquement en v1 ; le paysage n'est pas bloqué mais n'est pas optimisé.
- Français et anglais. La langue suit le système, forçable dans les réglages.
