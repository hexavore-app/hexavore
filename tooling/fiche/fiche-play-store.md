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
« Illimitée » ne s'écrit qu'avec son renvoi : l'offre gratuite de Google a un
plafond quotidien, que la description et la sixième capture disent toutes deux.

**La description parle à qui l'installe** ([D180](../../docs/11-decisions.md)).
Ce qu'on y gagne d'abord — noter un repas en une photo ou une phrase, une IA
gratuite —, comment ça marche ensuite, et ce que l'application ne fait pas en
dernier. L'ancienne ouvrait sur un constat (« la saisie coûte trop cher ») et
décrivait des mécanismes : juste, mais écrite pour qui conçoit l'application, pas
pour qui la cherche.

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
Une photo de l'assiette, et l'IA fait les comptes. Gratuit, sans pub ni compte.
```

### Description complète (4000 max)

```
Notez ce que vous mangez en quelques secondes : prenez votre assiette en photo, ou dites simplement ce que vous avez mangé, et Hexavore fait les comptes.

Hexavore est gratuite, sans publicité, sans abonnement et sans compte à créer. Elle suit pour vous six repères à la fois : calories, protéines, glucides, sucres, lipides et fibres.

UNE IA GRATUITE ET ILLIMITÉE*

Photographiez l'assiette, ou écrivez « deux œufs, une tartine et un jus d'orange » : l'IA reconnaît les aliments, estime les quantités, et vous n'avez plus qu'à vérifier. Elle passe par Gemini, l'IA de Google, avec votre propre compte Google : une clé à récupérer une seule fois, en deux minutes, sans carte bancaire. L'application vous guide pas à pas.

*Avec une clé Google Gemini gratuite, dans la limite quotidienne fixée par Google, bien au-delà de ce que demandent vos repas.

Vous préférez Claude, l'IA d'Anthropic ? Elle fonctionne aussi, avec votre propre clé.

QUATRE FAÇONS DE NOTER UN REPAS

Une photo de l'assiette, analysée par l'IA.
Une phrase, comme vous la diriez à quelqu'un.
Un code-barres : la fiche du produit vient d'Open Food Facts.
Une recherche parmi 3 484 aliments, même sans connexion.

Le scan et la photo demandent un téléphone équipé d'un appareil photo.

TOUT EST ÉDITABLE

Chaque proposition se vérifie avant d'être enregistrée : un aliment, une quantité ou une valeur se corrige d'un geste, et ce que l'IA a estimé est signalé. Un repas oublié se rattrape sur n'importe quel jour passé.

VOTRE JOURNÉE D'UN COUP D'ŒIL

L'hexagone se remplit à mesure que vous mangez : un quartier par repère, et ce qu'il vous reste pour la journée. Touchez un quartier pour voir quels aliments l'ont rempli.

DES OBJECTIFS À VOTRE MESURE

Âge, taille, poids, activité, poids visé et échéance : Hexavore calcule vos six objectifs quotidiens, que vous ajustez quand vous voulez. Chaque semaine, votre poids réel est comparé à la trajectoire visée, et l'application vous propose un ajustement ; c'est vous qui décidez.

Les favoris rejouent un plat habituel sans tout ressaisir, le calendrier montre vos journées réussies, et le journal de poids trace votre vraie tendance.

VOS DONNÉES RESTENT CHEZ VOUS

Pas de compte, pas de serveur, pas de publicité, pas de pistage. Tout reste sur votre téléphone : vous l'exportez dans un fichier quand vous voulez, et vous effacez tout d'un geste. Quand vous utilisez l'IA, la demande part directement de votre téléphone chez le fournisseur choisi, sans passer par nous.

Sans clé, le scan, la recherche et tout le reste fonctionnent ; seules la photo et la phrase demandent l'IA.

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
Snap your plate and the AI does the counting. Free, no ads, no account.
```

### Full description (4000 max)

```
Log what you eat in seconds: snap your plate, or just say what you had, and Hexavore does the counting.

Hexavore is free, with no ads, no subscription and no account to create. It tracks six markers at once for you: calories, protein, carbs, sugars, fat and fibre.

A FREE, UNLIMITED AI*

Photograph the plate, or type "two eggs, a slice of toast and an orange juice": the AI recognises the foods, estimates the amounts, and all you have to do is check. It works through Gemini, Google's AI, with your own Google account: a key to fetch once, in two minutes, with no card. The app guides you step by step.

*With a free Google Gemini key, within the daily limit set by Google, well beyond what your meals need.

Prefer Claude, Anthropic's AI? It works too, with your own key.

FOUR WAYS TO LOG A MEAL

A photo of the plate, analysed by the AI.
A sentence, the way you would say it to someone.
A barcode: the product comes from Open Food Facts.
A search among 3,484 foods, even with no connection.

Scanning and photos need a phone with a camera.

EVERYTHING IS EDITABLE

Every suggestion is checked before it is saved: a food, an amount or a value is corrected in one gesture, and whatever the AI estimated is marked as such. A forgotten meal can be added to any past day.

YOUR DAY AT A GLANCE

The hexagon fills up as you eat: one quarter per marker, and what you have left for the day. Touch a quarter to see which foods filled it.

TARGETS THAT FIT YOU

Age, height, weight, activity, target weight and horizon: Hexavore calculates your six daily targets, which you can adjust whenever you like. Each week your real weight is compared with the intended trajectory, and the app suggests an adjustment; you decide.

Favourites replay a usual meal without typing it all again, the calendar shows your successful days, and the weight log draws your real trend.

YOUR DATA STAYS WITH YOU

No account, no server, no ads, no tracking. Everything stays on your phone: export it to a file whenever you want, and erase everything in one gesture. When you use the AI, the request goes straight from your phone to the provider you chose, without going through us.

Without a key, scanning, search and everything else still work; only photos and sentences need the AI.

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

## Notes de version 1.1.0 (500 max)

Les quatre langues que la Console demande : fr-CA reprend fr-FR, en-US reprend
en-GB, mot pour mot.

### fr-FR et fr-CA

```
Nouveautés.

• Après une analyse, l'écran de saisie s'ouvre aussitôt, déjà rempli : fini l'écran noir quand l'IA prend son temps.
• Quand l'IA met plus de dix secondes à répondre, l'application le dit, et pourquoi.
• Activer l'IA gratuite est expliqué plus simplement, et « Obtenir ma clé » mène droit au guide.
• De temps en temps, une astuce à l'ouverture, et la possibilité de noter Hexavore sur le Play Store.
```

### en-GB et en-US

```
What's new.

• After an analysis, the entry screen opens straight away, already filled in: no more black screen when the AI takes its time.
• When the AI takes more than ten seconds to answer, the app says so, and why.
• Turning on the free AI is explained more simply, and "Get my key" leads straight to the guide.
• Now and then, a tip when you open the app, and a chance to rate Hexavore on the Play Store.
```

---

## Notes de version 1.0.2 (500 max)

La 1.0.1 (code 9) n'est jamais partie : la 1.0.2 (code 10) est la première version
publiée après la 1.0.0, et ses notes disent tout ce qui a changé depuis elle.

Les quatre langues que la Console demande : fr-CA reprend fr-FR, en-US reprend
en-GB, mot pour mot. La ligne sur la virgule n'a pas d'équivalent anglais : en
anglais, le séparateur était déjà le bon.

### fr-FR et fr-CA

```
Corrections.

• Le guide du premier lancement s'arrête après « Configurer » et ne revient plus.
• En paysage, le contenu et la barre d'ajout ne passent plus sous l'encoche ni sous la barre de navigation.
• Les nombres s'écrivent avec une virgule : « 65,3 kg ».
• Un champ de nombres entiers dit pourquoi il refuse une virgule, au lieu de l'ignorer.
• Un poids à moitié tapé est gardé quand Android ferme l'application en arrière-plan.
• Une application plus légère.
```

### en-GB et en-US

```
Fixes.

• The first-launch guide ends after "Set it up" and no longer comes back.
• In landscape, content and the add bar no longer slip under the camera cutout or the navigation bar.
• A whole-number field now says so when you type a decimal separator, instead of silently ignoring it.
• A half-typed weight is kept when Android closes the app in the background.
• A lighter app.
```

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

### fr-CA et en-US

La Console demande aussi ces deux langues. Le français canadien reprend le texte
de fr-FR tel quel ; l'anglais américain reprend en-GB, à un mot près —
*analyzed* et non *analysed*.

```
Première version publique d'Hexavore.

• Quatre façons de noter un repas : code-barres, recherche hors ligne dans la table CIQUAL, photo ou phrase analysées par l'IA de votre choix, avec votre propre clé.
• La recherche trouve la forme qu'on mange : « riz » propose le riz cuit, « café » le café qu'on boit.
• Une pesée se supprime depuis le journal de poids, avec annulation.
• Une saisie en cours survit quand Android ferme l'application en arrière-plan.
```

```
First public release of Hexavore.

• Four ways to log a meal: barcode, offline search in the CIQUAL table, or a photo or sentence analyzed by the AI of your choice, with your own key.
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
