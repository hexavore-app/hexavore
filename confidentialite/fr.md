# Politique de confidentialité

*Dernière mise à jour : 24 septembre 2026*

Hexavore est une application Android de suivi alimentaire, libre et gratuite. Cette page dit quelles données l'application manipule, où elles vont, et comment les effacer.

## En bref

- **Hexavore n'a pas de serveur.** Le projet ne reçoit, ne stocke et ne consulte aucune de vos données.
- **Pas de compte, pas de publicité, pas de statistiques d'usage, pas de rapport de plantage automatique.**
- **Tout ce que vous saisissez reste sur votre téléphone.**
- Deux services extérieurs peuvent être contactés, et seulement quand vous faites le geste qui les appelle : **Open Food Facts** et **le fournisseur d'intelligence artificielle que vous avez choisi**.

## Qui est responsable

**Charly Flu** développe Hexavore à titre personnel et non commercial, et en est le responsable de traitement au sens du RGPD. Il y a peu à traiter : le projet n'a pas de serveur et ne reçoit aucune de vos données.

Le code source est public : [github.com/hexavore-app/hexavore](https://github.com/hexavore-app/hexavore).

Pour toute question sur cette politique : [contact@hexavore.app](mailto:contact@hexavore.app).

## Ce que l'application garde sur votre téléphone

- **Votre profil** : date de naissance, sexe (ou « je préfère ne pas répondre »), taille, niveau d'activité, système d'unités.
- **Vos pesées et vos objectifs.**
- **Votre journal** : plats, aliments, quantités, valeurs nutritionnelles, heure de saisie.
- **Les photos de vos plats**, quand vous validez un repas scanné ou analysé. Elles restent sur le téléphone et ne partent nulle part. Vous pouvez retirer celle d'un plat, cesser d'en garder, ou les effacer toutes : **Réglages → Photos des plats**.
- **Vos aliments personnels, vos favoris**, et les fiches Open Food Facts déjà consultées.
- **Vos réglages** : clés d'API des fournisseurs d'IA, identifiant et mot de passe de votre compte Open Food Facts si vous en avez saisi un, préférences d'affichage.

Ce sont des informations de santé. Elles ne quittent pas le téléphone, hors des cas décrits plus bas. La sauvegarde automatique d'Android est désactivée pour Hexavore : elles ne partent pas non plus vers votre compte Google sans que vous le sachiez.

## Ce qui quitte votre téléphone

Uniquement vers les destinations ci-dessous, et uniquement quand vous faites le geste correspondant. Toutes les connexions sont chiffrées (HTTPS). Comme pour toute connexion à Internet, le service contacté voit l'adresse IP de votre appareil.

### Open Food Facts

[Open Food Facts](https://world.openfoodfacts.org) est une base de données ouverte et collaborative de produits alimentaires.

- **Quand vous scannez un produit que l'application ne connaît pas encore** : le code-barres.
- **Quand vous touchez « Chercher dans Open Food Facts »** : le texte cherché.
- **Quand vous choisissez de contribuer une fiche que vous avez créée** : son nom, sa marque, son code-barres, sa portion et ses valeurs nutritionnelles, avec l'identifiant et le mot de passe de **votre** compte Open Food Facts. La fiche devient publique, sous licence ODbL. Rien ne part sans votre accord explicite.

Les deux premiers envois ne portent aucun identifiant : Open Food Facts ne peut pas les relier à vous. [La politique de confidentialité d'Open Food Facts](https://world.openfoodfacts.org/privacy) s'applique à ce qu'il reçoit.

### Le fournisseur d'intelligence artificielle que vous avez choisi

Seulement si vous avez enregistré une clé d'API, et à chaque analyse que vous lancez :

- **la photo de votre repas**, réduite à 1 024 pixels, **et/ou la phrase que vous avez écrite** ;
- en **analyse approfondie**, des extraits du catalogue d'aliments de l'application : des noms et des valeurs nutritionnelles, rien de personnel ;
- **votre clé d'API**, qui désigne votre compte chez ce fournisseur.

L'envoi part directement de votre téléphone vers le fournisseur : le projet ne voit rien passer. Le fournisseur facture ces appels sur votre compte, et sa propre politique de confidentialité s'applique à ce qu'il reçoit. Selon votre choix, il s'agit d'Anthropic, de Google (Gemini), d'OpenAI, de DeepSeek, de Mistral AI, ou du service dont vous avez saisi l'adresse vous-même.

Avant le premier envoi d'une photo, l'application vous demande votre accord et nomme le fournisseur. Une photo prise depuis Hexavore est écrite dans un dossier temporaire, lue, puis supprimée aussitôt : elle n'entre jamais dans votre galerie. Une image choisie dans votre galerie est lue sans être modifiée.

Si vous validez le repas, une copie de l'image est gardée **sur votre téléphone**, avec le plat. Elle ne part nulle part, et le paragraphe ci-dessus dit comment l'effacer.

### Rien d'autre

- **Le lecteur de codes-barres fonctionne entièrement sur le téléphone.** Aucune image de la caméra n'est envoyée. Celle qui a porté la lecture est gardée sur le téléphone si vous validez le plat, comme ci-dessus.
- **Aucune bibliothèque de publicité, de statistiques ou de suivi.** Aucune vérification de mise à jour, aucun signal envoyé au démarrage.
- **Le mode debug** des réglages d'IA garde les derniers échanges avec le fournisseur en mémoire seulement. Rien n'est écrit, et tout disparaît à la fermeture de l'application.

## Vos sauvegardes

« Exporter mes données » produit une archive que vous enregistrez où vous le décidez. Elle contient votre profil, vos objectifs, vos pesées, votre journal, vos aliments, vos favoris et les photos de vos plats, et **jamais** vos clés d'API ni votre compte Open Food Facts. Si elle part ensuite vers un service de stockage ou une messagerie, c'est par l'application que vous avez choisie, selon ses propres règles.

## Sécurité

- Connexions chiffrées (HTTPS), sans exception.
- Clés d'API et mot de passe Open Food Facts chiffrés par le trousseau sécurisé d'Android (Keystore).
- Le projet ne détient aucune donnée : aucune ne peut fuiter de chez lui.

## Conservation et effacement

- Vos données restent sur votre téléphone tant que vous ne les effacez pas.
- **Réglages → Photos des plats** permet de cesser d'en garder, et d'effacer celles qui sont là.
- **Réglages → Sauvegarde → « Effacer toutes mes données »** efface le journal, les photos, le profil, les clés d'API, le compte Open Food Facts et les autres réglages. Les fichiers déjà exportés ne sont pas touchés.
- **Désinstaller Hexavore** efface tout ce que l'application a stocké.
- Ce qui a été envoyé à un service extérieur relève de sa politique : une fiche contribuée à Open Food Facts reste publique, et ce que reçoit un fournisseur d'IA est conservé selon ses conditions.

## Vos droits

Le responsable ne détient aucune donnée vous concernant : il n'y a rien à consulter, rectifier ou supprimer de son côté. Tout se fait dans l'application. Pour ce qui a été transmis à Open Food Facts ou à votre fournisseur d'IA, adressez-vous à eux. Vous pouvez aussi saisir la [CNIL](https://www.cnil.fr).

## Public

Hexavore s'adresse aux adultes. Ce n'est pas un dispositif médical, et elle ne remplace pas l'avis d'un professionnel de santé.

## Modifications

Cette politique change quand l'application change, et en même temps qu'elle. [Son historique complet est public](https://github.com/hexavore-app/hexavore/commits/main/confidentialite/fr.md).
