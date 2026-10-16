# La fiche Play Store, dans les deux langues

Les textes à coller dans la Console, et les contraintes qu'ils respectent. Ils
vivent ici et non dans la Console seule, parce qu'une fiche promet des
fonctionnalités : le jour où l'une d'elles change, c'est ce fichier qu'un diff
signale.

**Un paragraphe par ligne, jamais de coupure à 80 colonnes.** Le Play Store
conserve les retours à la ligne de la description : une phrase repliée pour être
lisible ici s'afficherait brisée en plein milieu sur la fiche. Les blocs
ci-dessous sont donc longs à lire dans un éditeur sans retour à la ligne
automatique, et c'est le prix à payer pour qu'un copier-coller soit fidèle.

**Aucun tiret cadratin.** Le ton est celui du site, et la ponctuation aussi.

**Aucune promesse que l'application ne tienne.** « Hors ligne » ne s'écrit que
là où c'est vrai : la recherche CIQUAL et le journal fonctionnent sans réseau,
le scan d'un produit inconnu et l'analyse par IA n'en sont pas capables.

---

## Français

### Nom de l'application (30 max)

```
Hexavore : suivi alimentaire
```

28 caractères. `Hexavore` seul en fait 8, mais le titre est le signal de
référencement le plus fort du Play Store, et le mot « Hexavore » ne dit à
personne ce que l'application fait. Le suffixe descriptif reste dans ce que les
Règles sur les métadonnées autorisent : il décrit, il ne vante pas.

### Brève description (80 max)

```
Six compteurs, pas seulement les calories. Sans compte, sans pub, sans pistage.
```

### Description complète (4000 max)

```
Suivre son alimentation échoue presque toujours pour la même raison : la saisie coûte trop cher. Trente secondes par repas, trois repas par jour, l'utilisation de l'IA est toujours payante.

Hexavore est construite autour de cette seule volonté : permettre à n'importe qui de rapidement rentrer un repas, et de pouvoir utiliser l'IA qu'il souhaite afin d'accélérer le processus.

QUATRE FAÇONS DE NOTER UN REPAS

Scanner un code-barres. La fiche vient d'Open Food Facts. Scannée une fois, elle reste disponible sans réseau.

Photographier l'assiette. Un modèle d'IA reconnaît les aliments et leurs quantités. Chacun est cherché dans les bases publiques ; quand aucune fiche ne correspond, le modèle estime les valeurs, et la ligne le signale.

Chercher un aliment. 3 484 aliments de la table CIQUAL de l'ANSES, hors ligne, dès la deuxième lettre. « creme brulee » trouve « crème brûlée ».

Décrire en une phrase. « Deux œufs, une tranche de pain et un verre de jus d'orange. »

Le scan et la photo demandent un téléphone équipé d'un appareil photo.

SIX COMPTEURS, UNE FIGURE

Calories, protéines, glucides, sucres, lipides, fibres. L'hexagone qui donne son nom à l'application montre les six d'un coup d'œil : un quartier par compteur, rempli depuis le centre, le contour marquant l'objectif du jour.

Touchez un quartier et il dit ce qui l'a rempli, aliment par aliment.

VOS OBJECTIFS, CALCULÉS PUIS AJUSTABLES

Âge, taille, poids, niveau d'activité, poids visé et échéance : six objectifs quotidiens calculés, tous modifiables à la main à tout moment.

Chaque semaine, votre poids réel est comparé à la trajectoire visée. L'application propose un ajustement ; vous décidez.

UN HISTORIQUE QUI SE LIT

Un calendrier où chaque jour porte l'atteinte de ses six objectifs. N'importe quelle journée passée se consulte et se corrige. Le journal de poids trace la tendance réelle sous les mesures brutes.

CE QU'ELLE NE FAIT PAS

Pas de compte. Pas de serveur. Pas de publicité. Pas d'abonnement. Pas de télémétrie.

Tout est stocké sur votre téléphone. Vous exportez l'ensemble dans un fichier lisible quand vous le voulez, et vous effacez tout d'un geste.

L'IA est facultative. Si vous vous en servez, la requête part de votre téléphone avec votre propre clé, directement chez le fournisseur que vous avez choisi : le projet ne voit rien passer. Sans clé, le scan et la recherche fonctionnent ; seules la photo et la phrase en demandent une.

LIBRE, ET SOURCÉE

Le code est public, sous licence GPL-3.0. Hors estimations de l'IA, signalées comme telles, les valeurs nutritionnelles viennent de deux sources ouvertes, créditées dans l'application :

Table CIQUAL 2025 de l'ANSES, sous Licence Ouverte Etalab 2.0
Open Food Facts, par ses contributeurs, sous licence ODbL 1.0

Hexavore est un outil de suivi personnel. Ce n'est pas un dispositif médical : il ne diagnostique, ne traite, ne guérit ni ne prévient aucune maladie. Pour un avis, un diagnostic ou un traitement, consultez un professionnel de santé.
```

---

## English

### App name (30 max)

```
Hexavore: calories & macros
```

### Short description (80 max)

```
Six counters, not just calories. No account, no ads, no tracking.
```

### Full description (4000 max)

```
Food tracking almost always fails for the same reason: logging costs too much. Thirty seconds a meal, three meals a day, and AI always comes at a price.

Hexavore is built around a single aim: let anyone log a meal quickly, using whatever AI they choose to speed it up.

FOUR WAYS TO LOG A MEAL

Scan a barcode. The product comes from Open Food Facts. Scanned once, it stays available with no connection.

Photograph the plate. An AI model recognises the foods and their quantities. Each one is looked up in the public databases; when no entry matches, the model estimates the values, and the line says so.

Search for a food. 3,484 foods from the French ANSES CIQUAL table, offline, from the second letter typed. Accents are ignored on both sides.

Describe it in a sentence. "Two eggs, a slice of bread and a glass of orange juice."

Scanning and photos need a phone with a camera.

SIX COUNTERS, ONE FIGURE

Calories, protein, carbs, sugars, fat, fibre. The hexagon the app is named after shows all six at a glance: one quarter per counter, filled from the centre, the outline marking the day's target.

Touch a quarter and it says what filled it, food by food.

TARGETS THAT ARE CALCULATED, THEN YOURS TO ADJUST

Age, height, weight, activity level, target weight and horizon: six daily targets calculated, every one of them editable by hand at any time.

Each week your real weight is compared with the intended trajectory. The app suggests an adjustment; you decide.

A HISTORY THAT READS

A calendar where every day carries how close it came to its six targets. Any past day can be read and corrected. The weight log draws the real trend beneath the raw readings.

WHAT IT DOES NOT DO

No account. No server. No ads. No subscription. No telemetry.

Everything is stored on your phone. You export the lot to a readable file whenever you want, and you erase everything in one gesture.

The AI is optional. If you use it, the request leaves your phone with your own key, straight to the provider you chose: the project never sees it. Without a key, scanning and search still work; only photos and sentences need one.

FREE SOFTWARE, WITH SOURCES

The code is public, under the GPL-3.0 licence. Apart from AI estimates, which are marked as such, the nutrition values come from two open sources, credited inside the app:

CIQUAL 2025 table by ANSES, under Licence Ouverte Etalab 2.0
Open Food Facts, by its contributors, under the ODbL 1.0 licence

Hexavore is a personal tracking tool. It is not a medical device and does not diagnose, treat, cure, or prevent any medical condition. Consult a healthcare professional for medical advice, diagnosis, or treatment.
```

---

## Les deux endroits où la traduction n'est pas un décalque

**« creme brulee » trouve « crème brûlée » ne se dit pas en anglais.** Les
libellés anglais de la table CIQUAL sont sans diacritiques, et « creme » y rend
**zéro** résultat : l'exemple qui prouve la recherche insensible aux accents en
français ne prouverait rien. La ligne anglaise énonce donc la règle — *accents
are ignored on both sides* — plutôt qu'un exemple qui ne marche pas. La capture
anglaise cherche « chicken » pour la même raison.

**« Fibres » devient « fibre » et non « fiber ».** Les 594 chaînes anglaises de
l'application sont en anglais britannique — *favourite*, *recognised*,
*centre* — et la fiche suit. C'est aussi pourquoi les captures anglaises sont
prises en `en-GB` : la semaine y commence le lundi, comme en français.

---

## Les autres champs de la Console

| Champ | Valeur | Pourquoi |
|---|---|---|
| Catégorie | Santé et bien-être | *Alimentation et boissons* vise les recettes et la livraison ; *Médecine* déclenche un examen bien plus strict. |
| Tags | Régime, Perte de poids, Santé et remise en forme | Les trois seuls de la liste fermée qui décrivent l'application. Aucun tag « calories », « nutrition » ni « macros » n'existe : ces mots ne peuvent entrer dans l'index que par le titre et les deux descriptions. |
| Vidéo | aucune | Facultatif, et une mauvaise vidéo coûte plus qu'elle ne rapporte. |
| Éléments tablette | aucun | L'application n'a pas de mise en page tablette : des captures étirées promettraient un confort qui n'existe pas. |
| Déclaration des applications de santé | Nutrition et gestion du poids | La catégorie de ce que l'application fait : des repas, des objectifs, un poids. Rien de médical n'est mesuré ni déduit ([D168](../../docs/11-decisions.md)). |

---

## L'avertissement santé, et pourquoi il a deux phrases

La règle *Contenus et services de santé* de Google Play demande à toute application de santé qui n'est pas un dispositif médical **un avertissement clair dans sa description** : elle n'est pas un dispositif médical, et elle ne diagnostique, ne traite, ne guérit ni ne prévient aucune maladie. Elle demande aussi de **rappeler de consulter un professionnel de santé** pour un avis, un diagnostic ou un traitement. Les applications de suivi nutritionnel y sont nommées. Les deux phrases closent donc les deux descriptions, et ce sont, mot pour mot, celles que l'application fait lire à l'accueil et garde dans « À propos » (`ds_medical_disclaimer`) : deux textes d'une même promesse finiraient par en dire deux ([D168](../../docs/11-decisions.md)).

La même règle veut qu'une application qui se sert d'un capteur de l'appareil, la caméra par exemple, le dise dans sa description : c'est la ligne « Le scan et la photo demandent un téléphone équipé d'un appareil photo ».

---

## Notes de version 1.0.0 (500 max)

### fr-FR

```
Première version publique d'Hexavore.

• Quatre façons de noter un repas : code-barres, recherche hors ligne dans la table CIQUAL, photo ou phrase analysées par l'IA de votre choix, avec votre propre clé.
• La recherche trouve la forme qu'on mange : « riz » propose le riz cuit, « café » le café qu'on boit.
• Une pesée se supprime depuis le journal de poids, avec annulation.
• Une saisie en cours survit quand Android ferme l'application en arrière-plan.
```

### en-GB

```
First public release of Hexavore.

• Four ways to log a meal: barcode, offline search in the CIQUAL table, or a photo or sentence analysed by the AI of your choice, with your own key.
• Search finds the form you eat: "rice" suggests cooked rice, "coffee" the coffee you drink.
• A weigh-in can be deleted from the weight log, and undone.
• An entry in progress survives when Android closes the app in the background.
```

---

## Accès à l'application, pour l'examen

**Aucune clé n'est fournie** ([D168](../../docs/11-decisions.md)) : l'IA est facultative, et une clé donnée à l'examen serait une clé de l'auteur, facturée à chaque essai et lisible par quiconque relit la Console. Rien d'autre n'est verrouillé : pas de compte, pas d'abonnement, pas de contenu réservé. Dans la Console, *Accès à l'application* se règle sur « Tout ou partie des fonctionnalités sont soumises à restrictions », avec ce texte :

```
No account, no login, no subscription. Every screen opens without credentials.

Two optional features, the photo analysis and the sentence analysis, call an AI provider with the user's own API key (Google Gemini, Anthropic, OpenAI, Mistral, DeepSeek, or any OpenAI-compatible service). No key is provided: the app has no server, and the request goes straight from the phone to the provider the user chose.

To try them, a free Google Gemini key can be created in about two minutes at aistudio.google.com, without a card. Paste it in Settings > Artificial intelligence. Without a key, those two buttons explain how to add one, and everything else works: barcode scan, offline food search, the journal, targets, weight log, progress, favourites, export and erase.
```
