# 09 — Données et sauvegarde

## Où vivent les données

| Donnée | Emplacement | Sauvegardée ? |
|---|---|---|
| Journal, aliments, objectifs, poids | `hexavore.db` (Room) | oui |
| Préférences d'affichage | DataStore | oui |
| Clés API | `EncryptedSharedPreferences` (Keystore) | **non, jamais** |
| Photos analysées | `cacheDir`, supprimées immédiatement | sans objet |
| Base CIQUAL | `assets/`, lecture seule | non, elle est dans l'APK |

`android:allowBackup="false"` dans le manifeste : la sauvegarde automatique d'Android est désactivée au profit d'un mécanisme explicite. La sauvegarde système exfiltrerait les clés API vers Google Drive sans que l'utilisateur en soit informé — exactement ce que ce document s'emploie à empêcher.

---

## Format de sauvegarde

Un JSON unique, compressé en gzip. Lisible, inspectable, réparable à la main — pour un projet libre qui héberge les données de santé de ses utilisateurs, c'est une propriété qui vaut les quelques kilo-octets supplémentaires face à un format binaire.

```json
{
  "formatVersion": 1,
  "appVersion": "1.0.0",
  "exportedAt": "2026-08-02T14:32:11Z",
  "attribution": {
    "openFoodFacts": "Contient des données d'Open Food Facts, sous licence ODbL 1.0",
    "ciqual": "Table CIQUAL 2025 — ANSES, Licence Ouverte Etalab 2.0"
  },
  "profile":   { },
  "goals":     [ ],
  "weights":   [ ],
  "dishes":    [ ],
  "entries":   [ ],
  "foods":     [ ],
  "favorites": [ ],
  "adjustment": { }
}
```

**Un plat porte son titre et son moment** quand il en a ([D118](11-decisions.md)) : la sauvegarde porte ce que la base tient. Les deux champs sont **facultatifs**, donc le format ne change pas de version — un fichier écrit avant eux se relit sans eux, et ses plats retrouvent le nom que leur heure leur donne, exactement comme les lignes d'avant dans la base.

~~`deviceId`~~ et ~~`preferences`~~ ne sont pas écrits ([D96](11-decisions.md#d96--la-sauvegarde-emprunte-les-mappeurs-et-le-format-ne-porte-que-ce-que-la-base-tient---validée)). Rien ne lit le premier, et un fichier que l'utilisateur peut envoyer par courriel n'a pas besoin de porter de quoi relier deux exports au même téléphone. ~~Le second n'a pas encore de contenu : les préférences d'affichage naîtront avec la section « Apparence ».~~ **La section existe, et le champ reste vide** ([D113](11-decisions.md#d113--apparence-existe-et-suivre-le-système-est-un-choix---validée)) : le thème est une préférence d'appareil, et cette page vient de poser que le format ne porte que ce que la base tient. Le système d'unités, lui, voyage déjà — il est une colonne du profil.

`adjustment` porte l'état de l'adaptation hebdomadaire. C'est une décision de l'utilisateur et non un réglage d'appareil : qui vient de répondre « ne plus proposer » ne doit pas revoir la carte parce qu'il a changé de téléphone.

### Ce qui est inclus, et pourquoi

~~`foods` ne contient pas tout le catalogue : seulement les aliments personnels, et les fiches Open Food Facts référencées. Les aliments CIQUAL sont réimportables depuis les assets, donc seul leur code est conservé.~~

**`foods` contient tout le catalogue local** ([D96](11-decisions.md#d96--la-sauvegarde-emprunte-les-mappeurs-et-le-format-ne-porte-que-ce-que-la-base-tient---validée)), fiches de l'ANSES comprises. Une fiche de l'ANSES n'est copiée localement qu'à son **premier usage** et reçoit alors un identifiant propre à l'installation : l'exclure romprait le lien que chaque ligne de journal tient vers elle.

Le filtrage que ce paragraphe visait est déjà obtenu autrement — le catalogue local ne contient que ce qui a servi. Sans lui, une restauration hors-ligne afficherait un journal d'entrées anonymes.

Ce que `foods` ne porte pas : les **portions nommées**, les **teneurs complétées**, le rayon et le titre court. Ce sont des propriétés de la référence, relues par le code de la fiche, et la table locale ne les stocke pas.

### Ce qui est exclu

- **Clés API.** Contrainte ferme ([01](01-perimetre.md#contraintes-fermes)).
- **Photos.** Elles n'existent plus au moment de la sauvegarde.
- **Cache Open Food Facts non référencé.** Reconstituable.

### Versionnement

`formatVersion` est un entier qui s'incrémente à chaque changement incompatible. L'importeur applique une chaîne de migrations `v1 → v2 → v3`, exactement comme Room. Une sauvegarde de 2026 doit rester lisible en 2029 ; c'est le minimum de respect qu'on doit à quelqu'un qui a noté ses repas pendant trois ans.

Une sauvegarde d'une version **plus récente** que l'application est refusée avec un message clair, jamais importée partiellement.

---

## Google Drive

> **Repoussé.** Drive attend que l'application entière fonctionne en local ([D104](11-decisions.md#d104--la-sauvegarde-locale-a-ses-écrans-et-deux-promesses-qui-nétaient-pas-tenues---validée)). Le port
> `BackupTarget` l'accueillera sans changer : il a été écrit pour deux implémentations, et la première —
> la copie de sécurité interne — s'en sert déjà.


### Emplacement

Dossier `appDataFolder` — espace privé de l'application dans le Drive de l'utilisateur. Invisible dans l'interface Drive, inaccessible aux autres applications, décompté du quota de stockage de l'utilisateur.

Scope demandé : `https://www.googleapis.com/auth/drive.appdata`, **uniquement**.

Ce scope est classé *non sensible* par Google : il n'exige ni évaluation de sécurité CASA, ni vérification approfondie de l'application. Pour un projet libre sans budget, c'est la différence entre une fonctionnalité livrable et une fonctionnalité abandonnée. Demander `drive.file` ou `drive` déclencherait tout l'appareil de validation, pour un bénéfice nul.

### Authentification

Credential Manager avec Sign in with Google, puis échange contre un jeton d'accès Drive. Le jeton de rafraîchissement est conservé dans le stockage chiffré.

La connexion n'est demandée **qu'au moment** où l'utilisateur active la sauvegarde. Jamais au démarrage, jamais à l'onboarding. Une application de suivi alimentaire qui réclame un compte Google avant d'avoir rendu le moindre service perd son utilisateur sur-le-champ.

### Rotation

Cinq fichiers conservés, nommés `hexavore-{ISO8601}.json.gz`. Au-delà, le plus ancien est supprimé.

Cinq et non un seul : une corruption locale sauvegardée écraserait la seule copie saine. Cinq et non vingt : au-delà, l'utilisateur ne sait plus laquelle choisir.

L'écran de restauration liste les fichiers avec date, taille et nombre d'entrées — assez pour reconnaître la bonne sans l'ouvrir.

### Automatisation

`PeriodicWorkRequest` WorkManager, une fois par jour, avec contraintes :

```kotlin
Constraints.Builder()
    .setRequiredNetworkType(NetworkType.UNMETERED)   // Wi-Fi
    .setRequiresBatteryNotLow(true)
    .build()
```

Une sauvegarde est ignorée si rien n'a changé depuis la précédente — comparaison d'une empreinte du contenu, pas de la date.

Échecs : retrait exponentiel, 3 tentatives, puis silence jusqu'au cycle suivant. Aucune notification d'échec — personne n'a envie d'être alerté qu'une sauvegarde a échoué à 3 h du matin. Les réglages affichent la date de la dernière sauvegarde réussie, et un avertissement discret au-delà de 7 jours.

### Restauration

Trois étapes, avec un dialogue de confirmation nommant explicitement ce qui va être perdu :

1. sélection du fichier ;
2. **remplacement complet** de la base locale ;
3. redémarrage de l'écran d'accueil.

Le remplacement, et pas la fusion. La fusion demande une résolution de conflits par entité — même identifiant, contenus différents, dates de modification proches — qui produit des bugs silencieux et des données corrompues. Tant qu'il n'y a pas de scénario multi-appareils réel à servir, la complexité n'est pas justifiée.

Une **sauvegarde de sécurité locale** est produite juste avant l'écrasement, dans le stockage interne, et conservée jusqu'à la restauration suivante. C'est ce qui permet de revenir en arrière quand quelqu'un restaure le mauvais fichier.

Le chemin de la fusion reste ouvert : tous les enregistrements portent `id`, `created_at` et `updated_at`, ce qui suffirait à implémenter un dernier-écrivain-gagne par entité.

---

## Export et import de fichier

Indispensable, et pas seulement pour les utilisateurs sans compte Google.

- **Export** : `ACTION_CREATE_DOCUMENT` via le Storage Access Framework. **Ce chemin n'est pas un `BackupTarget`** : on n'y liste rien et on n'y fait rien tourner, seulement un document par geste ([D104](11-decisions.md#d104--la-sauvegarde-locale-a-ses-écrans-et-deux-promesses-qui-nétaient-pas-tenues---validée)). L'utilisateur choisit l'emplacement — stockage local, Nextcloud, clé USB, peu importe. Aucune permission de stockage n'est requise.
- **Import** : `ACTION_OPEN_DOCUMENT`, même format, mêmes migrations, même confirmation.

C'est la garantie de réversibilité du projet : quelqu'un qui veut partir emporte ses données dans un fichier lisible. Un format ouvert et un export fonctionnel valent mieux que toutes les promesses de non-enfermement.

Un export CSV du seul journal est également proposé, pour les tableurs. Il n'est **pas** réimportable et l'écran le dit — un CSV ne peut pas transporter le modèle complet.

---

## Chiffrement optionnel

Désactivé par défaut. Activable dans les réglages, avec une phrase secrète.

- Dérivation : PBKDF2-HMAC-SHA256, 600 000 itérations, sel de 16 octets aléatoire.
- Chiffrement : AES-256-GCM, nonce de 12 octets, aléatoire par fichier.
- L'en-tête reste en clair (`formatVersion`, `exportedAt`, paramètres de dérivation) pour qu'un fichier reste identifiable.

Désactivé par défaut, parce que le compromis n'est pas évident : `appDataFolder` est déjà privé et protégé par le compte Google, tandis qu'une phrase secrète perdue signifie des données perdues, définitivement. L'écran d'activation l'écrit noir sur blanc, sans euphémisme.

---

## Confidentialité

### Ce qui sort de l'appareil

~~Trois flux~~ **Cinq flux** ([D124](11-decisions.md#d124--la-politique-de-confidentialité-vit-à-côté-du-code-et-le-site-ailleurs---validée)), tous déclenchés par une action de l'utilisateur :

| Destination | Contenu | Quand |
|---|---|---|
| `world.openfoodfacts.org` | Un code-barres | À chaque scan d'un produit inconnu |
| `world.openfoodfacts.org` | Le texte cherché | Sur un tap sur « Chercher dans Open Food Facts » ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)) |
| `world.openfoodfacts.org` | La fiche, avec l'identifiant et le mot de passe du compte Open Food Facts **de l'utilisateur** | Quand il choisit de la contribuer ([D90](11-decisions.md#d90--la-contribution-part-sous-le-compte-de-lutilisateur-et-jamais-sans-lui---validée)) |
| Fournisseur d'IA choisi | Une photo ou un texte, avec la clé de l'utilisateur | À chaque analyse |
| `googleapis.com` (Drive) | Le fichier de sauvegarde | Si la sauvegarde est activée — **pas encore construite** |

Le tableau en comptait trois. La recherche par nom et la contribution y sont entrées sans qu'il le dise, et ML Kit envoyait à Google des diagnostics qu'il ne mentionnait pas davantage — c'est ce qui l'a fait retirer ([D123](11-decisions.md#d123--le-scan-ne-parle-plus-à-google---validée)).

Aucun autre trafic sortant. Pas de SDK publicitaire, pas d'analytics, pas de vérification de mise à jour, pas de « ping » de démarrage. **La vérification de mise à jour que [10](10-qualite-et-livraison.md#variantes-de-build) prévoit pour la variante GitHub n'existe pas** ; le jour où elle existera, elle entrera dans ce tableau et dans la politique, dans le même PR.

Hors contribution, Open Food Facts reçoit un code-barres ou un texte sans identifiant utilisateur : la requête est anonyme et non corrélable.

### Formulaire Data Safety du Play Store

Déclaration prévue, cohérente avec ce qui précède :

- Aucune donnée collectée par le développeur.
- Aucune donnée partagée avec des tiers **par l'application** ; les envois vers le fournisseur d'IA sont initiés par l'utilisateur avec ses propres identifiants, et déclarés comme tels.
- Données chiffrées en transit : oui (HTTPS partout, `cleartextTrafficPermitted="false"`).
- Suppression des données : intégrale et immédiate à la désinstallation, plus un bouton « Effacer toutes mes données » dans les réglages.

### Politique de confidentialité

Obligatoire pour le Play Store, pour toute application, même une qui ne collecte rien — avec un lien dans la console **et** dans l'application. ~~Un fichier Markdown dans le dépôt, publié via GitHub Pages~~ **Deux fichiers Markdown dans ce dépôt, [`confidentialite/fr.md`](../confidentialite/fr.md) et [`confidentialite/en.md`](../confidentialite/en.md), publiés par le site `hexavore-app/hexavore-site`** ([D124](11-decisions.md#d124--la-politique-de-confidentialité-vit-à-côté-du-code-et-le-site-ailleurs---validée)), rédigés dans la même langue que le reste : courte, factuelle, sans clause décorative. Elle dit ce que le tableau ci-dessus dit, et rien de plus.

**Elle change dans le même PR que le flux qu'elle décrit.** C'est pour ça qu'elle vit ici et non dans le dépôt du site : un flux ajouté sans elle la rend fausse, et une politique fausse ment avec autorité à ceux qui la lisent. Un workflow demande au site de se republier dès qu'elle change sur `main`.

**Elle dit ce que l'application fait aujourd'hui.** Drive n'y figure pas, parce qu'il n'existe pas encore ; sa section entrera avec lui, et c'est elle que la vérification de marque de Google lira.

---

## Suppression

- **Bouton « Effacer toutes mes données »** dans les réglages : vide la base, les préférences, les clés, et propose de supprimer aussi les sauvegardes Drive. Double confirmation, avec saisie du mot `SUPPRIMER` — c'est irréversible et la friction est intentionnelle.
- **Désinstallation** : Android efface le stockage de l'application. Les fichiers Drive survivent ; la boîte de dialogue de suppression le rappelle et donne le chemin pour les retirer depuis les paramètres du compte Google.
