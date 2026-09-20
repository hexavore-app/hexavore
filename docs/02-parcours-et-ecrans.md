# 02 — Parcours et écrans

Carte de navigation, puis chaque écran en détail : ce qu'il montre, ce qu'on peut y faire, et ce qui se passe quand ça se passe mal.

## Carte

```
Onboarding (première ouverture uniquement)
   └─> Accueil ─┬─> Réglages ─┬─> Profil et objectifs
                │             ├─> Fournisseurs d'IA
                │             ├─> Sauvegarde
                │             ├─> Apparence
                │             └─> À propos
                ├─> Journal de poids
                └─> [FAB] ─┬─> Scan code-barres      ┐
                           ├─> Photo                 │ 4 modales
                           ├─> Recherche             │ qui convergent
                           └─> Texte libre           ┘ vers ↓
                                                Validation d'entrée
```

Une seule règle structurante : **les quatre modes de saisie se rejoignent sur le même écran de validation**. Un seul composant à concevoir, à tester et à corriger, et un geste identique quel que soit le chemin emprunté.

Le nœud « Réglages » n'existe pas encore : quatre de ses cinq sections dépendent des tranches à venir, et l'accueil ouvre pour l'instant **directement** « Profil et objectifs » ([D59](11-decisions.md)). **Sans clé d'IA, l'appareil photo et le crayon expliquent d'abord**, puis y mènent — droit à la section « Intelligence artificielle », et non au hub : quelqu'un qui appuie sur l'appareil photo cherche l'endroit où mettre une clé, pas la liste des réglages. L'explication précède le déplacement, parce qu'une explication qui arrive après arrive chez quelqu'un qui se demande d'abord où il est ([D106](11-decisions.md#d106--une-clé-sobtient-quelque-part-et-les-barres-du-haut-passaient-sous-lhorloge---validée)).

---

## Onboarding

Cinq étapes, une question par écran, barre de progression en haut. **L'application ouvre ici tant qu'aucun objectif ne court** ([D56](11-decisions.md)) : il n'y a pas d'écran intermédiaire à traverser pour arriver aux questions.

~~Bouton « Passer » sur toutes les étapes sauf la première : un utilisateur pressé doit pouvoir arriver à l'accueil. Les champs sautés prennent une valeur par défaut raisonnable et sont signalés dans les réglages.~~ **Chaque étape exige ses champs** ([D56](11-decisions.md)). Un objectif calculé sur des valeurs par défaut est l'objectif de quelqu'un d'autre, affiché avec l'autorité d'un chiffre personnel. Un « Continuer » refusé affiche une barre qui dit **ce qui** manque — pas qu'il manque quelque chose.

**1. Accueil.** Nom de l'application, une phrase, et l'**hexagone des macros** rempli d'une journée d'exemple : la figure qui donne son nom à l'application montre en une image ce qu'un paragraphe raconterait. Affiche l'avertissement : *« Hexavore est un outil de suivi personnel. Ce n'est pas un dispositif médical et il ne remplace pas l'avis d'un professionnel de santé. »* — à accepter pour continuer, **la phrase entière étant la cible tactile** et non la seule case.

**2. Vous.** Date de naissance (**vrai sélecteur de date**, jamais une saisie au format imposé), sexe, taille, poids actuel.

Le sexe propose **Homme / Femme / Je préfère ne pas répondre**. La formule de Mifflin-St Jeor n'a que deux variantes ; la troisième option applique la moyenne des deux, et l'écran le dit : *« Nous utiliserons une estimation intermédiaire, que vous pourrez ajuster ensuite. »* Cacher ce détail produirait un chiffre inexplicable.

~~Unités : kg et cm par défaut, lb et ft/in disponibles dans les réglages, conversion à l'affichage uniquement.~~ **L'onboarding est métrique, sans réglage possible** : le système d'unités vit sur le profil que ces questions créent, donc il n'existe pas encore quand elles se posent. Il se choisit juste après, dans Apparence, et rien n'est perdu — **le stockage est toujours métrique** (voir [07](07-modele-de-donnees.md)), et pour le corps la conversion n'est qu'un affichage. Les quantités d'aliments, elles, ne se convertissent pas : l'once est une unité de saisie de plus ([D114](11-decisions.md#d114--lonce-est-une-unité-de-saisie-la-livre-un-affichage---validée)).

**3. Activité.** Cinq niveaux, chacun décrit par un exemple concret plutôt que par un adjectif. « Modérément actif » ne veut rien dire ; « sport 3 à 5 fois par semaine » se répond en une seconde.

**4. Objectif.** Trois cartes : *Perdre du poids* · *Maintenir* · *Prendre du poids*. Puis poids cible et échéance — **trois pastilles seulement**, +3 mois / +6 mois / +12 mois. ~~date libre~~ La date libre disparaît ([D56](11-decisions.md)) : une échéance exacte n'a aucune valeur en soi, ce qui compte est le rythme.

Un aperçu se met à jour en direct : « ≈ 0,6 kg par semaine », dérivé du budget retenu **après** garde-fous et non de l'échéance demandée. Si le rythme sort des bornes de sécurité ([03](03-nutrition-calculs.md#garde-fous)), l'écran ne bloque pas : la date atteignable apparaît en **quatrième pastille**, et un tap s'y cale.

**5. Vos objectifs.** Les six chiffres calculés, chacun avec une phrase d'explication en une ligne. Bouton « Ajuster » vers l'édition manuelle, bouton « C'est parti » vers l'accueil.

---

## Accueil

L'écran par défaut, celui qu'on ouvre dix fois par jour. Il tient en un défilement vertical.

### Bandeau calendrier (fixe en haut)

Sept pastilles : **une semaine calendaire**, du premier jour de la locale au dernier, et non sept jours glissants ([D93](11-decisions.md)). Le défilement horizontal va de semaine en semaine. Aujourd'hui est affiché par défaut ; le jour affiché porte son numéro en gras et en encre pleine, **sur un disque plein** ([D121](11-decisions.md#d121--le-changement-de-jour-se-voit---validée)). Le gras seul ne se voyait pas au milieu de sept anneaux colorés, qui attirent l'œil bien davantage. Le disque de la cellule qu'on quitte se rétracte pendant que celui qu'on rejoint s'ouvre : l'œil lit un déplacement.

Chaque pastille porte le jour de la semaine, le numéro, et un **anneau segmenté** reprenant les six couleurs de macro, dans l'ordre angulaire commun à toute l'application ([08](08-design-system.md#daltonisme)). Le calendrier garde l'anneau : à cette taille, les six quartiers d'un hexagone ne se distingueraient plus.

La pastille **se calcule sur la largeur** : sept places égales, l'anneau tient dans la sienne. Un compte de marges tenu à deux endroits les avait rendues ovales — le parent refusait la largeur sans toucher à la hauteur ([D101](11-decisions.md#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée)).

Un segment se remplit à mesure que l'objectif du jour est atteint ; il passe en mode « dépassement » (trait plus épais, teinte saturée) au-delà. Les six segments s'allument de la même façon, limites comprises ([D47](11-decisions.md)).

Le jour de départ d'un objectif porte un liseré : on voit où une nouvelle phase a commencé.

**La veille porte une pastille si elle est restée vide** ([D107](11-decisions.md#d107--une-pastille-désigne-une-situation-jamais-un-message---validée)), sur la journée concernée et non sur une icône : c'est là qu'on la touche pour y noter le repas oublié. Elle s'éteint dès qu'une ligne est notée, et le lendemain la déplace plutôt que de l'accumuler.

~~Tap sur une pastille → écran **Journée**. Tap sur l'en-tête du mois → **Calendrier étendu**.~~

**Tap sur une pastille : l'accueil change de date, sur place** ([D101](11-decisions.md#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée)). Le calendrier ne bouge pas — c'est ce qui permet de se promener dans l'historique sans le perdre — et le bouton d'ajout écrit sur le jour affiché, pour rattraper un oubli. Une poignée déplie le mois ; un glissement vers le haut le replie et la page suit.

**Trois gestes ouvrent le mois, et c'est le nombre qu'il fallait** ([D116](11-decisions.md)) : tirer la poignée, **la toucher** — elle ne répondait pas au doigt, seule l'action d'accessibilité était déclarée — et **tirer la page vers le bas quand elle est déjà en haut**, d'environ un centimètre. Ce dernier est celui que tout le monde connaît sans l'avoir appris ; les deux autres sont pour qui vise la poignée. Un défilement **lancé** qui bute en haut n'ouvre rien : un élan est un arrêt, pas une intention. La poignée elle-même porte un chevron et passe en encre claire — en `outline`, elle tenait 1,4:1 sur le fond sombre, là où un élément d'interface en demande 3.

### Le titre du jour

En tête d'écran, **le jour regardé**, et c'est le seul endroit qui le dise. « Aujourd'hui », « Hier », « Avant-hier », puis la date longue : les deux jours qui ont un nom le portent, parce qu'un mot se lit plus vite qu'une date qu'il faut décoder ([D121](11-decisions.md#d121--le-changement-de-jour-se-voit---validée)).

**Il participe au balayage.** Il glisse avec la journée — au tiers du déplacement, en s'effaçant — puis marque son arrivée d'une brève pulsation. Sans cela, le contenu changeait de jour en glissant pendant que le titre se substituait d'un coup, et on arrivait sans savoir où. La pulsation sert aussi les deux cas où rien ne glisse : un geste lâché à mi-course, et un jour choisi dans le calendrier.

Elle est **supprimée**, et non raccourcie, quand l'appareil demande moins de mouvement : un grossissement instantané suivi d'un retour instantané serait un clignotement.

### Bloc « Reste aujourd'hui »

Le cœur de l'écran, et la figure qui donne son nom à l'application : l'**hexagone des macros**. Six quartiers, un par compteur, remplis depuis le centre, le contour marquant l'objectif du jour ([08](08-design-system.md#macrohexagon)). Il répond à une seule question — comment va ma journée.

Sous l'hexagone, le **restant en calories** en grand chiffre. Le restant et non le consommé : c'est l'information dont on a besoin au moment de décider quoi manger. Le consommé et l'objectif sont écrits en dessous, plus petits. En cas de dépassement, le chiffre devient négatif. Aucun message moralisateur, aucun rouge d'alerte : c'est une donnée, pas un jugement.

Puis les **six barres**, dans le même ordre angulaire que les quartiers. Elles répondent à l'autre question : combien exactement. L'hexagone ne peut dire ni « 87 / 144 g » ni le `max` d'une limite ; les barres ne peuvent pas montrer une journée d'un coup d'œil. Les deux ne se concurrencent pas.

**Toucher un quartier dit ce qui l'a rempli** ([D122](11-decisions.md#d122--un-quartier-touché-dit-ce-qui-la-rempli---validée)). Le quartier grossit légèrement, les cinq autres s'éteignent, et une bulle se pose contre lui : les cinq plus gros aliments de la journée pour cette macro, du plus gros au plus petit, chacun avec la même mini-barre que sous les plats. Le reste est cumulé en une ligne — « 3 autres, 6 g » — plutôt que tu, sans quoi la somme de ce qu'on lit passerait pour le total.

La bulle se pose **entièrement sous la figure** : recouvrir ce qu'on vient de mettre en avant l'annulerait. Toucher un autre quartier change son contenu sans la refermer ; **tout appui ailleurs la referme**, et un appui sur la bulle elle-même ne change rien — on peut la lire. Les boutons flottants s'effacent pendant ce temps, faute de quoi ils se poseraient sur ses chiffres. Un quartier qui n'a rien à montrer ne répond pas — et « rien à montrer » n'est pas « le quartier est à zéro » : une macro dont un aliment ne renseigne pas la valeur a beaucoup à dire.

**Un aliment dont la valeur est inconnue est nommé à part, sans chiffre ni barre.** Depuis [D119](11-decisions.md) l'accueil ne signale plus ses totaux minorés ; cette ligne est le seul endroit de l'écran qui dise encore qu'un total l'est, et le seul qui dise **par quel aliment** — donc lequel aller corriger.

**Les six barres ouvrent la même bulle.** Toucher un triangle est un geste que rien n'annonce ; une barre pleine largeur est une cible qu'on trouve sans la connaître, et que le lecteur d'écran annonce déjà. La figure, elle, porte six actions d'accessibilité — un dessin ne se découpe pas en six vues.

Ce bloc est le même sur l'accueil et sur l'écran **Journée** d'un jour passé : c'est le même récapitulatif, seule la date change.

### Liste des plats

Les plats de la journée, du plus ancien au plus récent. Un **plat** est ce qu'on a saisi en une fois : plusieurs aliments, une seule origine.

Pas de case à choisir avant d'enregistrer : ce qui compte est ce qu'on a mangé aujourd'hui, et l'heure situe déjà chaque plat ([D31](11-decisions.md)).

**Un plat porte quand même un nom** ([D118](11-decisions.md)), et ce n'est pas la même chose : il se déduit de l'heure — petit-déjeuner, déjeuner, goûter, dîner —, ne coûte aucun geste, ne range le plat nulle part, et sert à écrire une liste sans citer tous ses aliments. Deux plats du même moment le même jour se numérotent à l'affichage : « Déjeuner », puis « Déjeuner 2 ». Il se corrige dans l'écran de validation, au clavier ou d'un tap sur l'une des quatre pastilles — l'heure d'un plat étant celle de sa saisie, le dîner d'hier noté ce matin s'appellerait sinon « Petit-déjeuner ».

**En tête de plat** : sa pastille d'origine, **son titre**, son heure, son total de calories.

**En pied de plat** : ses cinq autres apports. Un plat qui ne se lit que par son énergie ne dit pas d'où viennent les protéines ni ce qui a fait grimper les sucres — or c'est exactement la question qu'on se pose en relisant sa journée.

**Sous chaque chiffre, un trait de 3 dp** : la part que ce plat a prise sur l'objectif du jour ([D119](11-decisions.md)). Il répond à une question qu'aucun chiffre ne posait — *ce plat, c'était combien de ma journée ?* — et il disparaît quand la journée n'a pas d'objectif, faute de cible à laquelle se rapporter.

**Deux styles d'affichage**, réglés dans Apparence : le **simplifié** — titre, heure, calories, apports — et le **détaillé**, qui y ajoute la liste des aliments. Le simplifié est le défaut, parce que citer chaque aliment de chaque plat fait beaucoup de texte dès qu'une journée est chargée. Le geste ne change pas d'un style à l'autre : le tap ouvre la modification, l'appui long ouvre le menu.

Chaque ligne d'aliment — en détaillé — montre nom, quantité, calories. **Pas de pastille par ligne** : la source appartient au plat ([D32](11-decisions.md)).

**L'accueil ne signale plus les totaux minorés** ([D119](11-decisions.md)) : plus de « ≥ » sur les apports, plus de phrase sous les barres, plus de quartier estompé. Une valeur non renseignée s'y lit comme zéro. Ce que la base sait reste ce qu'elle sait — la distinction entre inconnu et zéro vit toujours dans les données, et l'écran de validation continue de désigner le champ qui manque.

- **Tap** → ouvre l'écran de validation du plat, en édition. La cible tactile est le plat **entier** — pastille, heure, total et apports compris, pas seulement ses lignes d'aliment ([D48](11-decisions.md)). Sans coins arrondis : ils tronquaient la pastille et le total ([D52](11-decisions.md)).
- ~~**Balayage vers la gauche** → supprimer une ligne.~~ **Le balayage change de jour** ([D117](11-decisions.md)) : vers la gauche le lendemain, vers la droite la veille, le contenu suivant le doigt et s'arrêtant à aujourd'hui. Supprimer une ligne se fait en ouvrant le plat, où chaque ligne porte sa corbeille ; l'accueil ne supprime plus qu'un plat entier, par l'appui long, et la barre d'annulation reste offerte (5 s). Aucune suppression n'est immédiatement définitive.
- **Appui long** → menu du plat : ~~dupliquer, déplacer vers un autre plat,~~ **Modifier**, **Supprimer** ([D61](11-decisions.md)). Supprimer emporte les *n* lignes d'un coup : un dialogue le demande d'abord et **dit le nombre**, puis la barre d'annulation reste offerte. « Modifier » double le tap volontairement — un menu dont la moitié des entrées manque oblige à se souvenir de quel geste sert à quoi. **Mettre en favori** / **Retirer des favoris** ([D62](11-decisions.md)) : mettre demande un nom, retirer n'en demande pas — le favori est déjà désigné par le plat. *Dupliquer et déplacer restent à faire.*

### Bouton d'ajout

Une colonne de boutons flottants en bas à droite. ~~Un tap déploie quatre actions étiquetées, en arc.~~

| | Action | Ouvre |
|---|---|---|
| ✨ | IA | Écran d'IA — une photo, une phrase, ou les deux |
| ⌗ | Scanner | Modale code-barres |
| ★ | Favoris | Liste des plats enregistrés |
| — | **Ajouter** | Modale recherche, qui porte aussi la saisie manuelle |

**Un seul bouton d'IA** ([D120](11-decisions.md)), là où « Photographier » et « Décrire » en occupaient deux : les deux modales ne différaient que par ce qu'elles envoyaient. La colonne y gagne la place qui manquait le plus, juste au-dessus du pouce.

L'ordre est fixe. Un ordre adaptatif « selon vos habitudes » ferait bouger les cibles sous le doigt et détruirait la mémoire musculaire — c'est exactement le contraire du but. « Ajouter » reste le seul à porter un libellé : c'est le geste principal.

---

## Modale : scan de code-barres

Feuille modale plein écran, aperçu caméra, cadre de visée, torche en haut à droite.

Décodage continu par ML Kit sur le flux CameraX. Formats acceptés : EAN-13, EAN-8, UPC-A — les formats de produits alimentaires, rien d'autre, pour éviter les faux positifs sur les QR codes.

~~UPC-E~~ en sort ([D65](11-decisions.md#d65--le-décodeur-est-un-module-à-part-et-sa-seule-règle-tient-sur-la-jvm---validée)) : huit chiffres ne disent pas s'ils sont un EAN-8 ou un UPC-E compressé, ML Kit ne décompresse pas, et lire l'un pour l'autre donne un code **plausible désignant un autre produit** — pire qu'un refus. Le modèle est **embarqué dans l'APK** et non téléchargé par les services Google : le premier scan doit marcher sans réseau, et sur un téléphone dégooglisé.

**Anti-rebond** : un code n'est retenu qu'après deux lectures identiques consécutives, puis le scan se met en pause et le téléphone vibre brièvement. Sans cela, la caméra enchaîne les détections et l'écran clignote.

La règle a **deux** moitiés, et la seconde est celle qu'on oublie : deux lectures d'accord écartent la lecture douteuse, la pause empêche la rafale. Une lecture que la clé de contrôle refuse ne compte pas et **casse la suite** — deux codes valides séparés par une lecture fausse n'ont pas été lus consécutivement ([D65](11-decisions.md#d65--le-décodeur-est-un-module-à-part-et-sa-seule-règle-tient-sur-la-jvm---validée)).

**L'aperçu se fige sur la trame qui a porté la lecture**, dès la confirmation, et la caméra s'arrête. Ce n'est pas la couper : l'image reste, elle cesse de bouger. C'est ce qui dit *ce que* l'appareil a lu, et ce qui permet de juger le cadrage quand la lecture n'aboutit à rien — l'image reste donc affichée sous les deux issues d'échec, jusqu'à la reprise ([D69](11-decisions.md#d69--laperçu-se-fige-sur-la-trame-qui-a-porté-la-lecture---validée)).

Séquence après lecture :

1. Recherche dans le catalogue local → si trouvé, affichage immédiat, aucun réseau.
2. Sinon, appel Open Food Facts avec un état de chargement inline (pas de dialogue bloquant), en surimpression de la trame figée.
3. Fiche trouvée → écran de validation, pré-rempli avec la portion de l'emballage si l'information existe, sinon 100 g.

**Produit introuvable.** Le cas est fréquent et doit rester agréable. L'écran affiche le code lu et trois issues : *Créer cet aliment* (formulaire pré-rempli avec le code-barres, la fiche est enregistrée en local et réutilisable indéfiniment), *Chercher par nom* (bascule vers la recherche), *Annuler*.

**Sans réseau.** Message explicite, code-barres mémorisé, proposition de créer l'aliment à la main. Le code reste associé : au prochain scan connecté, l'app proposera de compléter depuis Open Food Facts.

**Permission caméra refusée.** Explication de l'usage et bouton vers les réglages système. Les trois autres modes restent disponibles.

La permission est demandée **à l'ouverture**, sans écran d'explication devant : cet écran n'a aucun contenu sans caméra, et faire tapoter un préambule avant la seule question qui compte serait un écran de transit de plus. L'explication vient **après** un refus — c'est alors qu'elle sert, puisque Android ne rouvre plus sa boîte ([D66](11-decisions.md#d66--la-modale-de-scan-et-les-trois-modes-de-saisie-réunis-dans-le-graphe---validée)).

---

## Écran d'IA

**Un seul écran pour la photo et pour le texte** ([D120](11-decisions.md)). Il remplace ~~la modale photo~~ et ~~la modale texte libre~~, qui suivaient le même pipeline, déposaient au même endroit, traduisaient les mêmes erreurs et sortaient au même écran : ce qui les distinguait tenait en une ligne de code.

Trois zones, de haut en bas :

1. **Le cadre de l'image**, et **dedans deux boutons ronds** — prendre une photo, choisir une image. Ce qu'on regarde et ce qui le change sont au même endroit. Vide, le cadre dit ce qu'on attend de lui : *« Photographiez l'assiette entière, de préférence vue de dessus — ou décrivez votre repas en dessous. »* C'est le conseil de cadrage que cette page voulait en surimpression d'un aperçu caméra, et il se lit mieux **avant** d'appuyer. Une croix retire la photo, ce qui permet de basculer vers le texte sans quitter l'écran.
2. **Le champ de texte**, qui change de rôle selon le cadre : sans photo il **décrit** le repas, avec photo il le **précise** — *« l'assiette fait 24 cm »*, *« la sauce est allégée »*, le levier de justesse le moins coûteux qui existe. Son libellé le dit ; un champ qui demanderait « décrivez votre repas » sous une photo ferait tout retaper.
3. **Le bouton « Analyser »**, en bas, actif dès qu'il y a **une photo ou une phrase**.

**La prise de vue reste celle du système.** Un aperçu CameraX demanderait une seconde implémentation — la première sert le scan, qui analyse un flux en continu — pour un écran dont le seul travail est de remettre un JPEG. L'appareil du système apporte sa mise au point, son flash et son zoom, et il écrit directement dans notre cache. Le fichier temporaire est supprimé dans un bloc `finally`, que l'appel réussisse, échoue ou soit annulé ; il n'entre jamais dans la galerie.

L'image est réduite (1024 px sur le côté long, JPEG qualité 80) avant l'envoi. **Annuler coupe réellement la requête** : ce qui n'est pas parti n'est pas facturé.

**L'avertissement ne concerne que la photo.** Elle envoie une image de votre repas — et de ce qui l'entoure — à un tiers, et cela se dit une fois avant le premier envoi ([05](05-ia.md)). Une phrase tapée part sans avertissement : celui qui l'écrit sait exactement ce qu'il envoie.

**Aucune clé API configurée.** Le bouton d'IA reste visible mais grisé ; un tap ouvre une explication courte et un raccourci vers la section d'IA des réglages. Le masquer laisserait croire que la fonctionnalité n'existe pas.

**Échec.** Les erreurs sont traduites en langage humain, jamais en code HTTP : clé invalide → « Votre clé pour *Gemini* a été refusée. Vérifiez-la dans les réglages. » ; quota → « Votre fournisseur a refusé la requête : quota atteint. » ; réseau → « Pas de connexion. » Dans tous les cas, **la photo et la phrase sont conservées** le temps de proposer de réessayer, et une porte de sortie vers la saisie manuelle est offerte.

---

## Modale : recherche

Feuille modale qui monte aux deux tiers, champ de recherche focalisé et clavier ouvert immédiatement.

**À l'ouverture, avant toute frappe** — c'est l'écran le plus utilisé de l'application :

- **Récents** : les 20 derniers aliments distincts, tous modes de saisie confondus, triés par date de dernière utilisation.
- **Favoris** : aliments et plats épinglés, en tête.

**Sous la barre, un bandeau de pastilles** qui défile horizontalement et filtre tout ce qui s'affiche ([D54](11-decisions.md)). Deux qualités d'abord — *Favori*, *Mon aliment* — puis un trait, puis huit rayons : *Fruits*, *Légumes*, *Féculents*, *Viandes et poissons*, *Produits laitiers*, *Boissons*, *Desserts*, *Snacks*. Les rayons se cumulent en **OU**, les qualités en **ET** par-dessus. Une pastille seule et le champ vide listent les aliments du rayon : c'est un mode parcours, et les sections *Récents* et *Favoris* cèdent alors la place à cette liste — le classement y fait de toute façon remonter ce qu'on mange vraiment.

**Pendant la frappe** : résultats à partir du **2ᵉ caractère**, une fois écoulées **120 ms sans nouvelle frappe**. La requête n'est jamais lancée à chaque touche : on attend que la saisie se stabilise, et une frappe qui arrive avant l'échéance annule la précédente. Sans cela, taper « chocolat » déclenche sept recherches dont six sont jetées, et les résultats clignotent pendant qu'on écrit.

Recherche locale sur trois sources fusionnées et ordonnées :

1. aliments personnels et produits déjà scannés (ce que l'utilisateur mange vraiment),
2. CIQUAL,
3. suggestion « Chercher *« … »* dans Open Food Facts » en dernière ligne. ~~si le réseau est disponible~~ — **elle est offerte quoi qu'il arrive** ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)) : un test de connectivité ment, un portail captif se déclare connecté, et une ligne qui disparaît sans raison visible déroute plus qu'une phrase qui dit « pas de connexion ». Elle part sur un **tap** et jamais à la frappe : la recherche locale coûte une lecture SQLite, celle-ci un aller-retour réseau.

Accents et casse ignorés (« creme brulee » trouve « crème brûlée »). Chaque résultat affiche nom, marque éventuelle, et calories pour 100 g — assez pour choisir sans ouvrir.

Tap → écran de validation avec la quantité par défaut de l'aliment (voir [04](04-sources-de-donnees.md#portions-usuelles)). La fiche est **versée au catalogue au moment du choix** : un aliment de la table n'y est pas avant, et son identifiant provisoire ne désignerait rien ([D51](11-decisions.md)).

**La saisie manuelle est ici, en permanence.** C'est le seul point d'entrée d'une saisie, et taper un aliment à la main y **crée une fiche** : elle revient ensuite dans cette liste, se reprend en un tap, et sa quantité recalcule ses valeurs comme celle de n'importe quel autre. Le bouton passe en plein quand la recherche ne rend rien, avec le nom déjà tapé.

**Ce que l'utilisateur a saisi lui-même se voit**, et lui seul porte une corbeille — une ligne de la table est une référence publiée. La suppression demande confirmation et dit ce qu'elle coûte : les entrées de journal qui citaient la fiche sont conservées telles quelles, avec leurs valeurs figées ([D51](11-decisions.md)).

---

## Écran de validation d'entrée

Le point de convergence. Une ou plusieurs lignes, chacune éditable, un bouton d'enregistrement.

Chaque ligne présente :

- **Nom de l'aliment**. Pas de source par ligne : elle appartient au plat, et le badge se pose une fois en tête d'écran ([D32](11-decisions.md)). À partir de la tranche 6, une ligne dont les chiffres sont une **estimation** plutôt qu'une correspondance porte un marqueur — l'utilisateur doit savoir quand un chiffre est une supposition, mais c'est une propriété de la valeur, pas une seconde source.
- **Quantité** : champ numérique + sélecteur d'unité (g, ml, et les portions nommées disponibles pour cet aliment : « 1 tranche », « 1 verre »). **L'unité que la ligne porte en fait toujours partie**, même quand la fiche n'est plus lisible : rouvrir un plat reconstruit « 1 bol » depuis ce qui a été écrit, et une unité appliquée qui ne se voit pas est pire qu'une unité absente ([D103](11-decisions.md#d103--la-portée-dun-geste-est-une-affaire-de-disposition-et-une-règle-ne-sécrit-quune-fois---validée)). Les macros se recalculent en direct, à partir de la **référence pour 100 g** que la ligne porte — capturée à sa naissance, et reconstruite depuis les valeurs figées quand on rouvre un plat. Le recalcul ne relit donc jamais la fiche, qui a pu être corrigée ou supprimée depuis ([D51](11-decisions.md)).
- **Confiance IA**, sur les lignes issues d'une analyse : une correspondance faible est visuellement signalée et propose jusqu'à 3 aliments alternatifs, sans obliger à choisir.
- **Macros dépliables** : les six valeurs, chacune éditable, en **grammes entiers** — personne ne compte les demi-grammes, et l'arrondi a lieu à la saisie et non à l'affichage, pour que le chiffre lu soit celui qui est enregistré ([D52](11-decisions.md)). Une valeur modifiée à la main est marquée et ne sera plus jamais recalculée automatiquement pour cette ligne. Vider un champ compte comme une modification : c'est une affirmation, et la quantité n'a pas à la contredire.
- **Date** : aujourd'hui par défaut, ou le jour affiché par l'accueil si on est parti d'un jour passé. Elle ne change pas de place — elle est à côté du badge de source depuis toujours — elle change de **ton** : discrète pour aujourd'hui, en corps de texte et en encre pleine pour un autre jour, où elle dit « Sera noté sur le mardi 18 août » ([D101](11-decisions.md#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée)). Il n'y a **pas** de repas de destination à choisir : les lignes de cet écran forment un plat, et le plat se range tout seul à son heure.

En bas du défilement : total de la saisie, et son impact sur les compteurs du jour (« il vous restera 780 kcal »), avec **Ajouter un aliment** — qui rouvre la même recherche que le bouton de l'accueil, et dont le choix revient au brouillon en cours. **Enregistrer** et **Annuler** n'y sont pas : ils flottent au-dessus de la liste, côte à côte et toujours à l'image, parce qu'en pied de défilement ils s'éloignaient à mesure que le plat grossissait ([D48](11-decisions.md)). Les autres actions — **Supprimer une ligne**, **Enregistrer comme plat favori** — restent auprès de ce sur quoi elles portent. L'étoile est **en haut à droite** de l'écran, et elle n'apparaît que sur un brouillon complet : un favori sans ligne enregistrable ne rejouerait rien. L'allumer demande un nom, **proposé depuis le titre du plat** — et suivi de son premier rang libre si ce nom est déjà pris, « Déjeuner 2 » ([D118](11-decisions.md)) ; ~~proposé depuis les aliments du plat~~ ; l'éteindre **supprime le favori**, et c'est le seul chemin pour l'ôter de la liste ([D62](11-decisions.md)).

**Un plat relu et vidé de toutes ses lignes se supprime** ([D61](11-decisions.md)), et le bouton d'enregistrement le dit : il devient « Supprimer ce plat ». C'est la seule règle sur le sujet depuis que le balayage a disparu — un plat sans contenu n'est pas un plat à zéro calorie, c'est une saisie qui n'a pas eu lieu. Une saisie **neuve** vidée, elle, n'a rien à supprimer et reste non enregistrable.

**Un seul chemin pour supprimer une ligne** : la corbeille visible à droite du nom. Il y en avait deux — la corbeille et le balayage — et c'est le balayage qui part ([D117](11-decisions.md)) : le chemin visible était de toute façon le seul que la règle du projet rendait obligatoire, un geste sans représentation visible étant introuvable pour qui ne le connaît pas, hors d'atteinte au lecteur d'écran, et difficile pour une main qui tient mal le téléphone.

Cet écran est aussi celui qu'on obtient en tapant sur une ligne déjà enregistrée : même composant, en mode édition. Les macros affichées sont alors celles **figées à l'enregistrement**, pas celles recalculées depuis la source — un produit reformulé par son fabricant ne doit pas réécrire le passé.

---

## ~~Écran Journée~~ · fondu dans l'accueil

**Il n'existe plus** ([D101](11-decisions.md#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée)). L'accueil porte une date, et toucher une pastille la change sur place.

Deux demandes l'ont fait disparaître, et elles poussaient au même endroit : *le calendrier doit rester visible quand on se promène dans l'historique* — un second écran le laisse derrière lui — et *on doit pouvoir noter un plat sur un jour passé* — un écran de lecture seule ne le permet pas.

Ce n'était pas une perte. Il était déjà « structurellement identique à l'accueil » et partageait son modèle pour ne pas diverger ; il ne restait de lui qu'un titre, une croix, et l'absence du bouton d'ajout — dont les deux dernières viennent d'être annulées par la demande.

Ce qu'il promettait reste vrai, sur l'accueil : *« l'ajout et l'édition y sont pleinement disponibles : on rattrape un oubli de la veille comme on saisit le repas du jour. »* Le bouton retour du système ramène à aujourd'hui, et une puce **« ← Aujourd'hui »** sous le titre fait la même chose — un geste ne peut pas être le seul chemin ([D102](11-decisions.md#d102--le-retour-à-aujourdhui-a-une-porte-visible-et-le-jour-regardé-a-un-contrat---validée)).


## Calendrier déplié

**Ce n'est pas un écran**, c'est la même bande qui grandit ([D93](11-decisions.md)) — une poignée la déplie en grille mensuelle, une case par jour, chaque case portant l'anneau segmenté en réduction. Défilement vertical entre les mois.

Déplié, il **retient la page** : un glissement vers le haut **dans le contenu** le replie, et le même doigt continue. Le premier delta est consommé par le repli, sans quoi la page se déplacerait pendant que la hauteur s'anime et que le contenu ferait un bond ([D101](11-decisions.md#d101--laccueil-porte-une-date-et-lécran-journée-disparaît---validée)).

**Un geste dans le calendrier appartient au calendrier** : défiler dans le mois le fait défiler, jamais se refermer. C'est pour cela que le bandeau est hors du défilement de la page et non dedans — la portée d'un geste se règle par la disposition, pas par une condition ([D103](11-decisions.md#d103--la-portée-dun-geste-est-une-affaire-de-disposition-et-une-règle-ne-sécrit-quune-fois---validée)).

Toucher une case change le jour affiché **sur place**. Le calendrier reste donc à l'écran pendant qu'on se promène dans l'historique, et le bouton d'ajout écrit sur le jour qu'on regarde.

**Tout ce qui ouvre un brouillon y écrit**, et pas seulement le bouton d'ajout : une fiche cherchée, un produit scanné, une photo analysée, un **plat favori rejoué**. Le jour n'est décidé qu'à un endroit, et chaque porte y passe — le favori ne le faisait pas, et rejouer un plat depuis la veille l'enregistrait sur aujourd'hui ([D111](11-decisions.md#d111--une-règle-se-vérifie-aux-portes-pas-seulement-à-la-fabrique---validée)).

Une journée sans aucune saisie est visuellement neutre — et non « à zéro ». Confondre « je n'ai rien noté » avec « je n'ai rien mangé » fausserait toutes les moyennes, ici comme dans l'algorithme d'adaptation ([03](03-nutrition-calculs.md#adaptation-hebdomadaire)).

---

## Journal de poids

Liste des pesées et courbe. Deux tracés : les points bruts, discrets, et la **moyenne mobile sur 7 jours**, en évidence — le poids brut varie de deux kilos avec l'hydratation et décourage sans raison.

La trajectoire visée est superposée en pointillés, du départ de l'objectif jusqu'à la date cible.

Ajout d'une pesée par un bouton flottant : poids et date, c'est tout.

Si l'algorithme a une suggestion d'ajustement en attente, une carte apparaît en tête : *« Sur les 3 dernières semaines vous perdez 0,3 kg par semaine, pour 0,5 visé. Réduire l'objectif de 120 kcal ? »* — **Accepter** / **Ignorer** / **Ne plus proposer**. Rien n'est jamais appliqué sans accord.

---

## Réglages

Écran simple à sections. L'accueil y mène, et les sections mènent aux écrans. **Il existe depuis que sa deuxième section existe** — c'est l'échéance que [D59](11-decisions.md) avait fixée, et [D77](11-decisions.md#d77--la-clé-va-dans-le-keystore-en-direct-et-le-bouton-tester-est-une-vraie-analyse---validée) l'honore. Il en porte ~~cinq~~ **six** : **Profil et objectifs**, **Intelligence artificielle**, **Contribution**, **Sauvegarde**, **Notifications** et **Apparence**. Cette dernière s'ouvre le jour où le thème devient réglable ([D113](11-decisions.md#d113--apparence-existe-et-suivre-le-système-est-un-choix---validée)). ~~À propos~~ n'y figure toujours pas, pour la raison inchangée : elle n'ouvrirait rien.

**Profil et objectifs** — toutes les données de l'onboarding. **On les consulte d'abord** : l'écran ouvre en lecture, et un crayon ouvre la modification ([D60](11-decisions.md)). Un écran de réglages entièrement saisissable invite à corriger ce qu'on venait relire.

~~Bouton « Recalculer mes objectifs » et~~ édition manuelle des six valeurs. Le bouton de recalcul disparaît : les six compteurs suivent chaque correction **en direct**, et deux chemins de calcul finissent par annoncer deux chiffres ([D59](11-decisions.md)).

~~Un objectif édité à la main est marqué comme tel et n'est plus écrasé par un recalcul sans confirmation explicite.~~ **Un interrupteur bascule entre objectif calculé et objectif saisi à la main** ([D60](11-decisions.md)). Calculé, les six compteurs suivent le profil, le poids visé et l'échéance. Saisi, ils deviennent six champs et plus rien ne les recalcule. Il n'y a pas de troisième état : un compteur figé à l'intérieur d'un objectif calculé obligeait l'écran à l'expliquer six fois, et le poids cible à piloter trois compteurs sur six.

En saisie manuelle, le poids cible et l'échéance **restent modifiables mais ne pilotent plus rien**, et l'écran le dit. Ils décrivent le cap annoncé, dont le journal de poids tire sa trajectoire.

Corriger ses objectifs **ouvre une nouvelle version**, il n'en modifie aucune ([D04](11-decisions.md)), et l'écran le dit en une phrase. Quand les six chiffres changent, une boîte les affiche **face aux anciens** avant d'écrire — seul écart assumé à la règle des dialogues ci-dessous. Corriger son poids enregistre une pesée du jour ; le laisser tel quel n'en invente aucune.

**Intelligence artificielle** — liste des fournisseurs. Pour chacun : clé API (masquée, avec bouton « Tester »), modèle, et l'URL de base. Un fournisseur actif est désigné par défaut — le bouton dit **« Utiliser »**, puis **« Utilisé »**, parce que le geste ne range pas une clé : il choisit qui analysera les prochaines photos ([D106](11-decisions.md#d106--une-clé-sobtient-quelque-part-et-les-barres-du-haut-passaient-sous-lhorloge---validée)).

Sous le champ de clé, **« Comment obtenir une clé ? »** ouvre une explication courte — ce qu'est une clé, où on la prend, qui facture — et un bouton vers la console du fournisseur. Un lien plutôt qu'un mode d'emploi : les consoles changent de forme plusieurs fois par an.

Un fournisseur mis en avant porte une **étoile « Recommandé »**. Ceux qui ne sont pas encore éprouvés sur un vrai compte s'affichent estompés, avec deux mots : **« Bientôt disponible »**.

**Analyse approfondie**, juste avant : une case à cocher, **éteinte par défaut**. Le modèle interroge le catalogue de l'application et choisit lui-même la fiche de chaque aliment, au lieu de laisser une recherche par nom décider — plus lent et plus coûteux, nettement plus juste sur les aliments dont le nom ressemble à celui d'un autre ([D109](11-decisions.md#d109--le-modèle-choisit-la-fiche-parce-quil-en-sait-plus-que-notre-score---validée)). Chez un fournisseur qui ne sait pas appeler d'outils, elle **s'estompe sans s'éteindre**, et le dit : le réglage est conservé et reprendra effet avec un fournisseur qui le sait.

**Mode debug**, tout en bas : un interrupteur qui enregistre ce qui part chez le fournisseur et ce qui en revient, et une porte vers la liste ([D108](11-decisions.md#d108--le-mode-debug-montre-les-corps-et-nécrit-rien-nulle-part---validée)). C'est un instrument qu'on allume le jour où quelque chose ne va pas, pas un réglage qu'on vient poser — d'où sa place, après le reste. **Rien n'est écrit sur le disque** : la liste vit en mémoire, garde les vingt derniers échanges, et disparaît quand on ferme l'application. Les images en sont retirées.

**En bas, compteur d'utilisation** : appels et jetons par fournisseur. ~~Et une estimation de coût, d'après une table de tarifs embarquée et datée.~~ Elle disparaît — un prix relevé un jour donné vieillit sans prévenir, et un montant approximatif affiché avec l'autorité d'un chiffre est pire qu'aucun montant. Seule la facture du fournisseur fait foi, et lui la donne exactement.

L'URL de base est modifiable pour **tous** les fournisseurs et non pour le seul générique : c'est ce qui rend un relais branchable devant n'importe lequel, pour zéro ligne de plus.

**Notifications** — les quatre pastilles, et un interrupteur pour chacune ([D107](11-decisions.md#d107--une-pastille-désigne-une-situation-jamais-un-message---validée)).

Ce sont des **points colorés dans l'application**, et rien d'autre : rien ne sonne, rien n'apparaît sur l'écran de verrouillage, aucune permission n'est demandée. Chacune désigne une **situation** et s'éteint quand la situation cesse — aucune ne se rejette, parce qu'un rappel qu'on peut faire taire ne dit plus rien à personne.

- **Aucune IA configurée** et **clé refusée**, sur l'icône du profil. Les deux ne s'allument jamais ensemble : une clé refusée n'a de sens que s'il y a une clé.
- **Pas de pesée depuis sept jours**, sur l'icône du journal de poids — sept, comme la fenêtre de la moyenne mobile. Une absence totale de pesée compte comme un silence trop long.
- **Hier n'a rien de noté**, sur la pastille d'hier dans le calendrier.

Chaque ligne de l'écran montre si sa pastille est allumée **en ce moment** : un interrupteur seul laisse celui qui vient de l'activer se demander ce qu'il surveille.

**Sauvegarde** — trois gestes, tous locaux ([D104](11-decisions.md#d104--la-sauvegarde-locale-a-ses-écrans-et-deux-promesses-qui-nétaient-pas-tenues---validée)).

- **Exporter mes données** ouvre le sélecteur de documents du système : l'utilisateur choisit où écrire, sans qu'aucune permission de stockage soit demandée. Les octets sont produits **avant** que le sélecteur s'ouvre, pour que le fichier décrive l'instant de la demande et non celui du choix.
- **Restaurer une sauvegarde** prévient d'abord — le remplacement est complet, jamais une fusion — puis ouvre le sélecteur. Une copie de l'état actuel part dans le stockage interne juste avant l'écrasement, et un fichier illisible ou trop récent ne touche à rien.
- **Effacer toutes mes données** demande d'écrire le mot `SUPPRIMER`. C'est le seul geste du projet qu'aucune barre d'annulation ne rattrape ; une double confirmation par boutons s'apprend à traverser en deux frappes, taper un mot demande de lire. Il emporte le journal **et** les réglages — clés d'API, comptes, adaptation, consentement photo. Les fichiers déjà exportés ne sont pas touchés.

~~connexion Google Drive, dernière sauvegarde, bascule automatique, « Sauvegarder maintenant »~~ — **Drive attend que le reste fonctionne en local**, et le port qui l'accueillera est déjà là.

**Apparence** — ~~thème (sombre / clair / système), langue, unités (métrique / impérial), animations réduites~~. **Le thème est livré** ([D113](11-decisions.md#d113--apparence-existe-et-suivre-le-système-est-un-choix---validée)) : trois choix exclusifs, « suivre le système » par défaut et par continuité — c'est ce que l'application faisait avant que le réglage existe. Un interrupteur n'aurait pas su dire « suivre », et le troisième état se serait deviné.

Le réglage vit dans **son propre fichier** — effacer ses clés d'IA n'a aucune raison de changer les couleurs — et **ne part pas dans la sauvegarde** : c'est une préférence d'appareil, là où le système d'unités est une propriété du profil et voyage avec lui.

**Unités** — métrique ou impérial ([D114](11-decisions.md#d114--lonce-est-une-unité-de-saisie-la-livre-un-affichage---validée)). Le réglage **ne convertit rien** : les lignes du journal gardent l'unité dans laquelle elles ont été saisies, la base garde ses kilogrammes et ses centimètres, et l'on peut basculer, regarder, puis revenir sans qu'un chiffre ait bougé.

En impérial, une ligne se saisit en **oz** ou **fl oz** — une unité de plus dans la liste, comme « 1 tranche » —, le corps s'affiche et se saisit en **livres** et en **pieds et pouces**, et la taille prend alors deux champs plutôt qu'un : personne n'énonce sa taille en pouces. Les **macros restent en grammes**, comme sur les étiquettes américaines.

**Affichage des plats** — simplifié ou détaillé ([D119](11-decisions.md)). Le simplifié donne le titre du plat, son heure, ses calories et ses apports ; le détaillé y ajoute la liste de ses aliments. Deux styles et non une échelle de densité : ce qui les distingue n'est pas une hauteur de ligne, c'est ce qu'on lit — *qu'est-ce que j'ai mangé et combien ça pèse* d'un côté, *de quoi était-ce fait* de l'autre. Comme le thème, c'est une préférence d'appareil, et elle ne voyage pas dans la sauvegarde.

*La langue et les animations réduites restent à faire. L'onboarding reste métrique : le réglage vit sur le profil qu'il crée, donc il n'existe pas encore quand ses questions se posent.*

**À propos** — version, lien du dépôt, licence, attributions CIQUAL et Open Food Facts, avertissement médical, lien de don *(variante hors Play Store uniquement, voir [10](10-qualite-et-livraison.md#variantes-de-build))*.

---

## Comportements transverses

**Restauration d'état.** Toute saisie en cours survit à une rotation, à un passage en arrière-plan et à la destruction du processus. Les états d'écran vivent dans un `ViewModel` avec `SavedStateHandle`.

**Erreurs.** Trois niveaux et pas un de plus : `Snackbar` pour le récupérable (« Supprimé »
+ Annuler), encart inline pour l'échec d'une action en cours (avec Réessayer), dialogue uniquement pour ce qui est destructif ou irréversible (restauration d'une sauvegarde, suppression d'un aliment utilisé dans l'historique) — **plus deux écarts assumés** : la confirmation d'un changement d'objectif, seul endroit où six lignes de chiffres doivent être lues avant d'écrire ([D60](11-decisions.md)), et la suppression d'un plat entier, où ce qui justifie le dialogue n'est pas l'irréversibilité mais le volume ([D61](11-decisions.md)).

**Chargements.** Squelettes plutôt que roues, sauf pour les appels IA où l'attente est longue et mérite une animation assumée. Aucun écran bloquant.

**Retour arrière.** Une modale se ferme sur retour. Une modale contenant une saisie non enregistrée demande confirmation avant de se fermer — une fois, sans insister.

**Premier lancement à vide.** Chaque liste vide dit quoi faire, pas seulement qu'elle est vide : « Rien de noté aujourd'hui. Scannez un produit ou décrivez ce que vous avez mangé. »
