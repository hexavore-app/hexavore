# Politique de confidentialité

*Dernière mise à jour : 16 octobre 2026*

Hexavore est une application Android de suivi alimentaire, libre et gratuite. Cette page dit quelles données l'application manipule, où elles vont, et comment les effacer.

## En bref

- **Hexavore n'a pas de serveur.** Rien ne remonte au projet de lui-même.
- **Pas de compte, pas de publicité, pas de statistiques d'usage, pas de rapport de plantage automatique.**
- **Tout ce que vous saisissez reste sur votre téléphone.**
- Deux services extérieurs peuvent être contactés, et seulement quand vous faites le geste qui les appelle : **Open Food Facts** et **le fournisseur d'intelligence artificielle que vous avez choisi**.
- Le projet ne reçoit que ce que vous lui écrivez vous-même, y compris les deux courriels que l'application sait préparer : **un signalement** de proposition fausse ou choquante et **une trace de plantage**.

## Qui est responsable

**Charly Flu** développe Hexavore à titre personnel et non commercial, et en est le responsable de traitement au sens du RGPD. Il y a peu à traiter : le projet n'a pas de serveur, et ne reçoit que les courriels que vous choisissez de lui envoyer.

Le code source est public : [github.com/hexavore-app/hexavore](https://github.com/hexavore-app/hexavore).

Pour toute question sur cette politique : [contact@hexavore.app](mailto:contact@hexavore.app).

## Ce que l'application garde sur votre téléphone

- **Votre profil** : date de naissance, sexe (ou « je préfère ne pas répondre »), taille, niveau d'activité, système d'unités.
- **Vos pesées et vos objectifs.**
- **Votre journal** : plats, aliments, quantités, valeurs nutritionnelles, heure de saisie.
- **Les photos de vos plats**, quand vous validez un repas scanné ou analysé, ou quand vous en ajoutez une vous-même à un plat. Elles restent sur le téléphone et ne partent nulle part. Vous pouvez retirer celle d'un plat, cesser d'en garder, ou les effacer toutes : **Réglages → Photos des plats**.
- **Vos aliments personnels, vos favoris**, et les fiches Open Food Facts déjà consultées.
- **Vos réglages** : clés d'API des fournisseurs d'IA, préférences d'affichage.

Ce sont des informations de santé. Elles ne quittent pas le téléphone, hors des cas décrits plus bas. La sauvegarde automatique d'Android est désactivée pour Hexavore : elles ne partent pas non plus vers votre compte Google sans que vous le sachiez.

## Ce qui quitte votre téléphone

Uniquement vers les destinations ci-dessous, et uniquement quand vous faites le geste correspondant. Toutes les connexions sont chiffrées (HTTPS). Comme pour toute connexion à Internet, le service contacté voit l'adresse IP de votre appareil.

### Open Food Facts

[Open Food Facts](https://world.openfoodfacts.org) est une base de données ouverte et collaborative de produits alimentaires.

- **Quand vous scannez un produit que l'application ne connaît pas encore** : le code-barres.
- **Quand vous touchez « Chercher dans Open Food Facts »** : le texte cherché.

Ces envois ne portent aucun identifiant : Open Food Facts ne peut pas les relier à vous. L'application n'écrit rien dans sa base. [La politique de confidentialité d'Open Food Facts](https://world.openfoodfacts.org/privacy) s'applique à ce qu'il reçoit.

### Le fournisseur d'intelligence artificielle que vous avez choisi

Seulement si vous avez enregistré une clé d'API, et à chaque analyse que vous lancez :

- **la photo de votre repas**, réduite à 1 024 pixels, **et/ou la phrase que vous avez écrite** ;
- en **analyse approfondie**, des extraits du catalogue d'aliments de l'application : des noms et des valeurs nutritionnelles, rien de personnel ;
- **votre clé d'API**, qui désigne votre compte chez ce fournisseur.

L'envoi part directement de votre téléphone vers le fournisseur : le projet ne voit rien passer. Le fournisseur facture ces appels sur votre compte, et sa propre politique de confidentialité s'applique à ce qu'il reçoit. Selon votre choix, il s'agit de Google (Gemini) ou d'Anthropic.

Avant le premier envoi d'une photo, l'application vous demande votre accord et nomme le fournisseur. Une photo prise depuis Hexavore est écrite dans un dossier temporaire, lue, puis supprimée aussitôt : elle n'entre jamais dans votre galerie. Une image choisie dans votre galerie est lue sans être modifiée.

Si vous validez le repas, une copie de l'image est gardée **sur votre téléphone**, avec le plat. Elle ne part nulle part, et le paragraphe ci-dessus dit comment l'effacer.

### Les courriels que vous choisissez d'envoyer au projet

L'application n'envoie jamais de courriel. Elle en prépare un, adressé à [contact@hexavore.app](mailto:contact@hexavore.app), dans la messagerie de votre choix : vous le relisez, vous pouvez le modifier, et c'est vous qui appuyez sur « Envoyer ». Il part alors par votre messagerie, avec votre adresse d'expéditeur.

- **Signaler une proposition fausse ou choquante**, sur une proposition de l'IA pas encore enregistrée : l'échange avec le fournisseur (ce qui lui a été demandé et ce qu'il a répondu, sans votre clé), la photo s'il y en a une, et les lignes telles que l'écran les montrait.
- **Après un plantage**, au lancement suivant, l'application vous propose d'envoyer la trace qu'elle a gardée : les classes et les lignes de code en cause avec le message d'erreur, les versions de l'application et d'Android, la marque et le modèle du téléphone. Que vous acceptiez ou non, la trace est ensuite effacée du téléphone.

Ces courriels servent uniquement à comprendre et à corriger ce qu'ils signalent. Ils ne sont transmis à personne, sont conservés le temps de le traiter, puis supprimés. Vous pouvez en demander la suppression à tout moment, à la même adresse.

### Rien d'autre

- **Le lecteur de codes-barres fonctionne entièrement sur le téléphone.** Aucune image de la caméra n'est envoyée. Celle qui a porté la lecture est gardée sur le téléphone si vous validez le plat, comme ci-dessus.
- **Aucune bibliothèque de publicité, de statistiques ou de suivi.** Aucune vérification de mise à jour, aucun signal envoyé au démarrage.
- **Le dernier échange avec le fournisseur d'IA** est gardé en mémoire seulement, pour pouvoir être joint si vous signalez une proposition fausse ou choquante. Rien n'est écrit, il disparaît à la fermeture de l'application, et il ne part que dans le courriel de signalement que vous envoyez vous-même.

## Vos sauvegardes

« Exporter mes données » produit une archive que vous enregistrez où vous le décidez. Elle contient votre profil, vos objectifs, vos pesées, votre journal, vos aliments, vos favoris et les photos de vos plats, et **jamais** vos clés d'API. Si elle part ensuite vers un service de stockage ou une messagerie, c'est par l'application que vous avez choisie, selon ses propres règles.

## Sécurité

- Connexions chiffrées (HTTPS), sans exception.
- Clés d'API chiffrées par le trousseau sécurisé d'Android (Keystore).
- Le projet ne détient aucune donnée de l'application : aucune ne peut fuiter de chez lui. Il ne garde que les courriels que vous lui avez envoyés, le temps de les traiter.

## Conservation et effacement

- Vos données restent sur votre téléphone tant que vous ne les effacez pas.
- **Réglages → Photos des plats** permet de cesser d'en garder, et d'effacer celles qui sont là.
- **Réglages → Sauvegarde → « Effacer toutes mes données »** efface le journal, les photos, le profil, les clés d'API et les autres réglages. Les fichiers déjà exportés ne sont pas touchés.
- **Désinstaller Hexavore** efface tout ce que l'application a stocké.
- Ce qui a été envoyé à un service extérieur relève de sa politique : ce que reçoivent Open Food Facts ou un fournisseur d'IA est conservé selon leurs conditions.

## Vos droits

Le responsable ne détient de données vous concernant que dans les courriels que vous lui avez envoyés : vous pouvez en demander la copie, la correction ou la suppression à [contact@hexavore.app](mailto:contact@hexavore.app). Tout le reste se fait dans l'application. Pour ce qui a été transmis à Open Food Facts ou à votre fournisseur d'IA, adressez-vous à eux. Vous pouvez aussi saisir la [CNIL](https://www.cnil.fr).

## Public

Hexavore s'adresse aux personnes de 16 ans et plus : c'est l'âge à partir duquel l'application calcule des objectifs. Ce n'est pas un dispositif médical, et elle ne remplace pas l'avis d'un professionnel de santé.

## Modifications

Cette politique change quand l'application change, et en même temps qu'elle. [Son historique complet est public](https://github.com/hexavore-app/hexavore/commits/main/confidentialite/fr.md).
