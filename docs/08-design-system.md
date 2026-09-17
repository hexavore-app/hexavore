# 08 — Design system

## Le parti pris

Néon sur noir profond. Mais un néon **fonctionnel** : la lumière sert à hiérarchiser l'information, pas à décorer.

Trois règles qui séparent un néon élégant d'un néon fatigant :

1. **Une seule chose brille à la fois par zone.** Si tout brille, plus rien ne ressort.
2. **Le fond reste sombre et neutre.** Aucune couleur saturée en aplat large — la couleur vit dans les traits, les anneaux et les lueurs.
3. **La couleur porte un sens et un seul.** Chaque macro a sa teinte, partout, sans exception. Une couleur qui change de signification d'un écran à l'autre annule tout le bénéfice.

---

## Couleurs

### Fonds et surfaces

| Rôle | Valeur | Usage |
|---|---|---|
| `background` | `#08080C` | Fond général, presque noir mais légèrement bleuté |
| `surface` | `#111119` | Cartes, feuilles modales |
| `surfaceVariant` | `#1A1A26` | Éléments imbriqués, champs |
| `outline` | `#2A2A3A` | Séparateurs |
| `onSurface` | `#EDEDF5` | Texte principal |
| `onSurfaceVariant` | `#9A9AB0` | Texte secondaire |

Le noir pur `#000000` est écarté : il crée des bords durs autour des lueurs et provoque du smearing sur les dalles OLED lors du défilement. `#08080C` conserve l'économie d'énergie OLED sans les artefacts.

### Macros

Le cœur du système. Ces six valeurs sont la seule source de vérité chromatique.

| Macro | Teinte | Hex | Raison |
|---|---|---|---|
| **Calories** | Cyan | `#00E5FF` | La mesure principale mérite la teinte la plus lumineuse |
| **Protéines** | Magenta | `#FF2D95` | Contraste maximal avec le cyan |
| **Glucides** | Violet | `#9D4EDD` | |
| **Sucres** | Violet clair | `#D9A5FF` | **Dérivé** des glucides : les sucres en sont un sous-ensemble, la parenté doit se voir |
| **Lipides** | Ambre | `#FFB020` | Seule teinte chaude, se détache immédiatement |
| **Fibres** | Vert | `#39FF88` | |

Le lien visuel sucres/glucides n'est pas un détail esthétique : c'est ce qui fait comprendre sans légende que la barre des sucres est une sous-graduation de celle des glucides.

Chaque teinte a trois déclinaisons dérivées par programme, jamais écrites à la main :

```
base    →  la valeur du tableau
glow    →  base à 35 % d'opacité, pour la lueur portée
muted   →  base désaturée de 60 %, pour l'état non atteint
```

### Contraste

Les six teintes dépassent 7:1 sur `#08080C` (niveau AAA pour du texte normal). Elles sont donc utilisables pour du texte, et pas seulement pour des tracés.

**Interdit** : texte foncé sur aplat néon. La combinaison est illisible en extérieur et ruine l'esthétique. Le néon est toujours l'élément *clair* de la paire.

### Daltonisme

Magenta et violet sont proches en deutéranopie, cyan et vert aussi en protanopie. La règle qui résout le problème sans dénaturer la palette :

> **La couleur ne porte jamais seule une information.** Chaque jauge, chaque segment, chaque légende porte un libellé ou une icône.

Là où un libellé ne tient pas, c'est la **position** qui sert de second canal. L'ordre angulaire est donc unique dans toute l'application, et il est celui de l'hexagone :

> **calories, protéines, fibres, glucides, sucres, lipides — dans le sens horaire depuis le haut.**

Une figure qui adopterait un autre ordre annulerait tout le bénéfice : la position ne renseigne que si elle est la même partout. C'est cet ordre que suivent l'hexagone, les pastilles du calendrier et les barres de l'accueil.

### Thème clair

Fourni, mais secondaire — cette application est pensée pour le sombre. Le thème clair inverse les fonds (`#FAFAFC` / `#FFFFFF`) et assombrit les macros de 25 %. **Aucune lueur** en thème clair : un halo sur fond clair ressemble à un défaut d'affichage.

**Ces 25 % suffisent à un trait, pas à du texte, et c'est une lacune connue.** Le cyan des calories devient `#00ABBF`, qui tient **2,8:1** sur du blanc — sous le AA de 4,5:1, et même sous le 3:1 du grand texte. Or la teinte porte du texte : les libellés de `NeonButton`, ceux des `TextButton` (qui prennent `primary`), l'indicateur de progression. La corriger demande des teintes de texte propres au thème clair, donc une décision sur la palette, et non un correctif d'écran ; elle est écrite ici pour ne pas être redécouverte au premier retour d'utilisateur. Là où le problème se voyait le plus — la surimpression du scan, posée sur un aperçu caméra — il est contourné par un voile sombre dans les deux thèmes ([D68](11-decisions.md#d68--un-voile-est-une-surface-et-il-reste-sombre-dans-les-deux-thèmes---validée)).

---

## Typographie

Une seule famille : **Inter**, variable, embarquée (aucun appel réseau à un service de polices).

| Style | Taille / graisse | Usage |
|---|---|---|
| `display` | 48 / 700 | Le grand chiffre de calories restantes |
| `headline` | 28 / 600 | Titres d'écran |
| `title` | 20 / 600 | Titres de section, en-têtes de plat |
| `body` | 16 / 400 | Texte courant |
| `label` | 14 / 500 | Étiquettes de jauge |
| `caption` | 12 / 400 | Sources, dates, mentions |

**Chiffres tabulaires obligatoires** (`font-feature-settings: "tnum"`) sur tout compteur. Sans ça, un total qui passe de 1 199 à 1 200 fait sauter la mise en page — un défaut discret mais qui donne une impression de bâclé à chaque saisie.

---

## Composants

### `MacroHexagon`

La figure principale de l'accueil, et celle qui donne son nom au projet : six macros, six quartiers.

#### Géométrie

Hexagone régulier **à sommet plat** — deux arêtes horizontales, en haut et en bas. Angles mesurés depuis l'est, sens antihoraire ; en coordonnées écran l'ordonnée descend, donc un point d'angle θ et de rayon r se trouve en `(cx + r·cos θ, cy − r·sin θ)`.

- **Sommets** à 0°, 60°, 120°, 180°, 240°, 300°.
- **Arêtes** centrées sur 30°, 90°, 150°, 210°, 270°, 330°.

Chaque macro occupe le quartier d'un axe, dans le **sens horaire depuis le haut** :

| Macro | Axe | Arête |
|---|---|---|
| Calories | 90° | haut |
| Protéines | 30° | haut-droite |
| Fibres | 330° | bas-droite |
| Glucides | 270° | bas |
| Sucres | 210° | bas-gauche |
| Lipides | 150° | haut-gauche |

Un quartier est le triangle `centre → sommet à (axe − 30°) → sommet à (axe + 30°)`. À 100 %, il coïncide exactement avec l'arête ; le contour de l'hexagone **est** l'objectif.

#### Remplissage

Le quartier d'une macro à un ratio *r* est le même triangle, homothétique de rapport *r* depuis le centre. Le remplissage part donc du milieu, sans trou central : six pointes qui convergent restent lisibles à 220 dp, et un anneau intérieur contredirait « en partant du milieu ».

**Le rayon est proportionnel à la valeur, pas la surface.** Un quartier à 50 % occupe donc le quart de l'aire de sa part. C'est la même convention que l'anneau et les barres — la longueur, pas l'aire — et l'incohérence serait de changer de règle d'un composant à l'autre. À écrire ici parce qu'un lecteur pressé conclura l'inverse.

#### Objectifs et limites

La distinction de [03](03-nutrition-calculs.md#objectifs-et-limites) vaut ici comme ailleurs, sans quoi un quartier de sucres bien rempli se lirait comme une réussite.

Les six quartiers sont traités **de la même façon** : teinte `base`, lueur croissante avec le remplissage, saturation de 30 % au-delà de l'objectif. Une limite ne reste pas sourde sous son seuil ([D47](11-decisions.md)) : une figure où trois macros sur six s'allument et trois non se lit comme un défaut d'affichage plutôt que comme une information.

La distinction objectif / limite ne se joue donc plus ici. Elle se lit sur les barres, qui portent la valeur et peuvent l'écrire.

#### La lueur

Elle est tracée **en contour sur les trois arêtes du quartier**, en trois passes de plus en plus larges et de moins en moins opaques — la technique du `NeonButton`, faute d'un flou disponible partout (`BlurMaskFilter` n'est pas accéléré matériellement et imposerait un rendu logiciel à chaque image animée).

Trois pièges, tous rencontrés sur appareil et tous évités par cette forme :

- **Une silhouette élargie ne luit que d'un côté.** Un second triangle un peu plus grand, posé derrière le quartier, ne dépasse visiblement que sur l'arête extérieure : les deux arêtes latérales sont mitoyennes, et le quartier voisin recouvre ce qui dépasse de son côté.
- **Une lueur pleine a un bord franc.** Un triangle plein s'arrête là où il s'arrête. Une lueur qui s'arrête net n'est pas une lueur.
- **Elle doit avoir la place de déborder.** Le rayon de la zone réserve la lueur, un intervalle, puis la lettre. Sans cette réserve, la lueur du quartier le plus rempli sort de la zone et se fait rogner au ras du bord.

Les six lueurs sont dessinées **après** les six quartiers, en une seconde passe. Dessinées quartier par quartier, chacune se ferait recouvrir par le remplissage du suivant.

Un quartier dont le total est minoré voit sa lueur s'estomper comme son remplissage : le même dégradé s'applique aux deux, sans quoi une arête volontairement floue se retrouverait soulignée d'un trait de néon parfaitement net.

#### Mise à l'échelle

Le contour de l'objectif n'est pas la limite du dessin : un quartier peut le dépasser. Pour que rien ne sorte de la zone allouée :

```
rPlafonné(m) = min(ratio(m), 2.0)
ajustement   = 1 / max(1, max des rPlafonné)
Rcible       = Rzone × ajustement
```

Quand rien ne dépasse, l'hexagone cible remplit la zone. Au plafond, la cible garde les deux tiers de sa taille et le quartier débordant touche le bord. **Le rétrécissement de l'hexagone cible est lui-même le signal** : on voit qu'on a débordé avant même d'avoir lu quelle macro.

Le rayon est **ce que trois contraintes laissent**, la plus serrée gagnant : la lettre du haut et celle du bas doivent tenir entières dans la hauteur, la lueur des deux sommets latéraux dans la largeur, et les quatre lettres obliques aussi — leur abscisse vaut `√3/2` fois leur rayon. La **réserve** entre le contour et une lettre est la lueur, un intervalle, puis la demi-lettre ; elle est déduite du rayon plutôt qu'ajoutée à la boîte, faute de quoi la lueur du quartier le plus rempli se ferait rogner par le bord du dessin.

**Elle se compte depuis l'arête, pas depuis le sommet** ([D115](11-decisions.md)). Une lettre est posée sur l'**axe** d'un quartier, donc face au milieu d'une arête, qui est plus près du centre que les sommets d'un facteur `√3/2`. Comptée depuis le cercle des sommets, la réserve éloignait les six lettres d'un huitième du rayon et faisait sortir celle du haut de la zone — donc de l'écran, rognée net par le défilement qui encadre la figure.

**Plafond à 150 %.** Au-delà, le quartier s'arrête là et son arête extérieure est tracée **en dents de scie**, la convention de rupture d'échelle des graphiques. Sans ce plafond, une saisie erronée à 2 000 % réduirait l'hexagone cible à un point et rendrait toute la figure illisible pour corriger l'erreur — c'est-à-dire au pire moment. À 200 %, la cible tombait à la moitié de sa taille et les six lettres se retrouvaient loin d'une figure devenue petite ; un dépassement de moitié se voit déjà largement.

#### Totaux minorés

Un quartier dont le total est amputé d'une valeur inconnue ([D29](11-decisions.md)) voit son arête extérieure **s'estomper** sur ses huit derniers dp, au lieu d'être nette. On ne sait pas où ça s'arrête, la figure ne prétend donc pas le savoir. Aucune légende n'est nécessaire ; la mention chiffrée reste sous les barres.

Le dégradé est **linéaire, le long de l'axe du quartier**, et non radial. Un dégradé radial suit un cercle : ses lignes d'égale opacité coupent le triangle en arcs, donc les deux sommets — à `R` du centre — disparaissent entièrement pendant que le milieu de l'arête — à `√3/2 · R` seulement — reste presque opaque. Le quartier paraissait rongé par les coins plutôt qu'estompé sur son bord. Le long de l'axe, les lignes d'égale opacité sont parallèles à l'arête, et les trois points du bord s'effacent ensemble.

#### Repères

Le contour de l'objectif est tracé **par-dessus** les quartiers, en `outline`, 2 dp. Il ne doit jamais être masqué : c'est la référence à laquelle tout le reste se compare.

L'initiale de chaque macro est posée **à l'extérieur** de la zone, sur l'axe de son quartier, dans la teinte de la macro. Six lettres suffisent, tiennent à 200 % de police, et donnent le second canal exigé par la règle de daltonisme — la position en donne déjà un.

**Elles sont écrites en `title`, en gras.** Ce n'est pas un choix esthétique : un second canal qu'il faut chercher des yeux n'en est pas un. En taille de légende, ces lettres se lisaient à peine sur le fond, et la règle de daltonisme reposait alors sur la seule position.

Leur rayon est celui de la **zone** et non du contour : elles ne bougent pas quand l'hexagone cible rétrécit sous l'effet d'un dépassement. Six repères qui se déplaceraient à chaque saisie ne seraient plus des repères. Il se mesure depuis l'arête de la zone, augmentée de la lueur et d'un intervalle : c'est là que la lettre regarde.

#### Accessibilité

L'hexagone est **exclu de l'arbre d'accessibilité**. Ce n'est pas un oubli : les mêmes six valeurs sont juste en dessous, dans les barres, sous une forme qui se lit bien mieux à la voix — une phrase par macro, avec son objectif et son pourcentage. Faire annoncer six clauses par une figure dupliquerait l'information et rallongerait la traversée de l'écran.

**Cette exclusion tient tant que les barres restent.** Si elles disparaissaient un jour, l'hexagone devrait reprendre l'annonce.

#### Animation

Le facteur d'ajustement et les six ratios s'animent ensemble, à la durée et à la courbe des jauges (400 ms, `FastOutSlowIn`). Ils tombent à zéro comme le reste quand l'appareil demande moins d'animations.

---

### `MacroRing`

L'anneau d'une macro. **Il n'est plus la figure de tête de l'accueil** — `MacroHexagon` l'a remplacé ([D33](11-decisions.md)) — mais il reste le composant des petites échelles : 44 dp dans le bandeau calendrier, 28 dp en vue mensuelle, et partout où une seule macro doit se lire d'un coup d'œil.

Diamètre de référence 180 dp.

- Piste : `outline`, 8 dp.
- Progression : dégradé de `base` vers `base` éclairci de 20 %, extrémités arrondies.
- Lueur : `glow`, flou 16 dp, opacité proportionnelle à l'avancement — l'anneau s'allume à mesure qu'on approche de l'objectif. C'est la seule récompense visuelle de l'application, et elle suffit.
- Dépassement : un second arc se superpose, teinte `base` saturée, épaisseur 4 dp.
- Centre : emplacement libre, laissé à l'appelant — le numéro du jour dans une pastille de calendrier.

### `MacroBar`

Barre horizontale, hauteur 6 dp, coins arrondis. Étiquette à gauche, `consommé / objectif` à droite.

**La barre pleine vaut l'objectif**, et la lueur est à pleine intensité dès le premier gramme. Rien dans le remplissage ne dépend du niveau ni de la nature de la macro : sous l'objectif, il n'y a aucune graduation à interpréter ([D47](11-decisions.md), [D48](11-decisions.md)).

**Le dépassement rétrécit la barre**, exactement comme il rétrécit l'hexagone posé juste au-dessus. Au-delà de l'objectif, l'échelle suit la valeur — le remplissage recule à mesure que la quantité monte — et un repère de la teinte du fond se creuse dans le néon, là où l'objectif se situe désormais. Même plafond que l'hexagone : **150 %**.

La distinction objectif / limite reste fonctionnelle et reste portée par la macro elle-même ([03](03-nutrition-calculs.md#objectifs-et-limites)), pour qu'aucun écran n'en décide autrement. Elle ne se voit plus que dans le texte :

| | Objectif | Limite |
|---|---|---|
| Valeur affichée | `87 / 144 g` | `41 / 63 g max` |
| Jauge | identique | identique |
| TalkBack | « sur un objectif de 144 » | « sur une limite de 63 » |

C'est un canal de moins qu'avant, et [D48](11-decisions.md) l'assume : un dépassement de sucres se voit désormais parce que la barre a rétréci, pas parce que c'était une limite.

### `DayPill`

Pastille du bandeau calendrier. 44 dp, `MacroRing` segmenté en couronne, jour de la semaine au-dessus, numéro au centre.

**Le calendrier garde l'anneau.** L'hexagone est réservé au récapitulatif d'une journée — accueil et écran Journée. À 44 dp, et plus encore à 28 dp en vue mensuelle, six quartiers ne se distingueraient plus les uns des autres ; un anneau segmenté reste lisible parce que ses segments sont concentriques et non adjacents.

États : sélectionné (contour cyan + lueur), aujourd'hui (numéro en cyan), journalisé (anneaux colorés), non journalisé (anneau `outline` uniquement, sans remplissage — surtout pas un anneau à zéro), futur (opacité 40 %).

### `EntryRow`

Ligne d'aliment dans un plat. Nom, quantité, calories. ~~Balayage vers la gauche pour supprimer, avec un fond magenta qui se révèle progressivement.~~ **Plus aucun balayage** ([D117](11-decisions.md)) : l'horizontal appartient à la journée entière, qui change de jour. `SwipeToDelete` a disparu du design system — il n'avait plus qu'un appelant, et il n'en a plus.

**Aucune pastille de source ici** : elle appartient au plat et se pose une fois en tête ([D32](11-decisions.md)). Cinq pastilles voisines ne distinguaient plus rien.

**Aucun clic non plus.** La cible tactile est le plat entier, y compris son heure, sa pastille, son total et ses apports ([D48](11-decisions.md)) — et sans rognage, qui tronquait la pastille et le total ([D52](11-decisions.md)). Ce que la ligne garde est sa phrase d'accessibilité — nom, quantité, calories en une fois — et c'est elle qui rend l'annonce du plat lisible une fois les nœuds fusionnés.

### Apports d'un plat

Sous les lignes d'un plat, ses cinq autres apports : `P 52 g · G 61 g · S 4,8 g · L 7,9 g · F 1,4 g`.

L'initiale et la teinte portent la même information — une couleur ne renseigne jamais seule — et la teinte est celle de la macro, donc celle des barres du haut : la correspondance se fait sans légende.

Un total amputé d'une valeur inconnue s'écrit `F ≥ 1,4 g`. Le symbole n'est pas décoratif : il dit que la vraie quantité est supérieure, ce qu'une valeur nue laisserait croire exact.

### `NeonButton`

Fond transparent, bordure 1,5 dp en `base`, texte en `base`, lueur externe au repos. À l'appui : fond à 12 % d'opacité, lueur intensifiée, réduction d'échelle à 0,97.

**Trois disponibilités, dont deux façons d'être éteint.** La distinction n'est pas cosmétique — elle répond à un défaut constaté sur appareil : un bouton grisé qui ne bouge pas du tout ne se distingue pas d'une application figée.

| | Au repos | À l'appui | Pour quoi |
|---|---|---|---|
| **Disponible** | teinte pleine, lueur | échelle 0,97, lueur intensifiée | le cas courant |
| **Indisponible** | grisé, sans lueur | **réagit quand même**, puis explique | mode IA sans clé ([02](02-parcours-et-ecrans.md#modale--photo)) |
| **Désactivé** | grisé, sans lueur | rien, et TalkBack l'annonce désactivé | action déjà en cours |

Masquer un bouton indisponible laisserait croire que la fonctionnalité n'existe pas ; le rendre inerte laisse croire que l'appareil ne répond plus. Il reste donc visible, grisé, et répond à l'appui pour dire ce qui manque.

Le bouton principal de chaque écran est le seul à porter un fond plein : un dégradé vertical de la teinte **à faible opacité**, posé sur le fond sombre. Pas un aplat néon — il imposerait du texte foncé par-dessus, ce que la règle de contraste interdit. Le néon reste le trait et le texte ; le fond ne fait que le porter.

Un écran, un bouton plein : cette règle empêche l'inflation visuelle.

### `SourceBadge`

Étiquette d'origine d'un **plat** — un plat, une source, posée une fois en tête. Toutes les sources sont **neutres** : fond `surfaceVariant`, texte et contour `onSurfaceVariant`.

Un contenu **proposé** par un modèle — photo ou description — se distingue par la **forme**, jamais par la teinte : contour en pointillés, et un glyphe en vague de 16 dp devant le libellé. Une recherche, un code-barres ou une saisie manuelle portent un contour plein et discret. Le raisonnement qui écarte une septième couleur est en [D25](11-decisions.md).

**Les tirets se mesurent en dp, pas en pixels.** Six unités de tiret font deux millimètres sur une dalle à densité 1 et un quart de millimètre sur une dalle à densité 4 : à ce stade, le pointillé est un trait plein. C'est le défaut qui a rendu le badge indistinguable sur un téléphone récent. Le tracé est en outre encarté d'une demi-épaisseur, faute de quoi sa moitié extérieure sort des limites du composant et se fait rogner.

### `ScrimSurface`

Le panneau posé sur une image qu'on ne maîtrise pas : l'aperçu du scan, et la photo de la modale IA quand elle existera.

**C'est une `Surface`, et c'est tout l'objet du composant.** Un voile peint au `Modifier.background()` teint le fond sans poser `LocalContentColor` ; Material 3 retombe alors sur du noir, et c'est exactement ainsi que la surimpression du scan a été livrée — texte noir sur voile sombre ([D68](11-decisions.md#d68--un-voile-est-une-surface-et-il-reste-sombre-dans-les-deux-thèmes---validée)). Le fond d'un voile et l'encre qui va dessus ne sont pas deux décisions ; les séparer, c'est en oublier une.

**Le voile est sombre dans les deux thèmes.** Une image de caméra n'est pas un fond dont on hérite : le panneau apporte le sien, et il l'apporte sombre pour que le néon reste l'élément clair de la paire — la règle de la section [Contraste](#contraste), qu'un voile blanc violerait dès qu'une teinte y porte du texte.

Fond `surface` du thème sombre à **92 %**, encre `onSurface` du thème sombre. L'opacité n'est pas un réglage de contraste — à 85 % le texte tenait déjà 10:1 sur un aperçu blanc — mais de bruit : la texture d'une image de caméra passe sous les lettres. L'image reste entière au-dessus du panneau, qui est toujours au ras d'un bord.

La forme appartient à l'appelant : coins hauts en 24 dp pour un panneau au ras du bas, 8 dp pour une pastille de conseil, pleine pour un bouton flottant.

---

## Espacement et formes

Grille de 4 dp. Marge d'écran 16 dp. Espace entre cartes 12 dp. Padding interne 16 dp.

Rayons : 8 dp (champs, badges), 16 dp (cartes), 24 dp (feuilles modales), plein (pastilles, boutons).

Cible tactile minimale : 48 × 48 dp, y compris sur les pastilles du calendrier qui font 44 dp visuellement — la zone tactile déborde le visuel.

---

## Animation

Le néon invite à trop animer. Cadre strict :

| Transition | Durée | Courbe |
|---|---|---|
| Valeur d'une jauge | 400 ms | `FastOutSlowIn` |
| Ouverture de feuille modale | 300 ms | `EmphasizedDecelerate` |
| Appui sur un bouton | 100 ms | `LinearOutSlowIn` |
| Apparition de contenu | 200 ms + décalage de 30 ms par élément | `FastOutSlowIn` |

**Aucune animation en boucle**, à une exception : l'attente d'analyse IA, où un flux néon parcourt le contour du cadre. C'est le seul moment où l'utilisateur attend plusieurs secondes sans rien faire, et où l'animation a une fonction — indiquer que le système travaille.

Le reste du temps, une lueur qui pulse en permanence consomme de la batterie et fatigue.

**Accessibilité** : quand `Settings.Global.ANIMATOR_DURATION_SCALE` vaut 0, ou quand la bascule « animations réduites » des réglages est active, toutes les durées tombent à 0 et l'animation d'attente devient un indicateur statique. Ce n'est pas optionnel : ce réglage existe souvent pour des raisons vestibulaires.

---

## Accessibilité

- **Contraste** : AA minimum partout, AAA sur les compteurs principaux.
- **TalkBack** : chaque jauge annonce une phrase utile — « Protéines, 87 grammes sur 144, 60 % » — et non « barre de progression, 60 % ».
- **Ordre de lecture** : chiffres du jour, puis plats dans l'ordre chronologique.
- **Taille de police** : la mise en page tient jusqu'à 200 % (`sp` partout pour le texte, aucune hauteur fixe sur un conteneur de texte).
- **Groupement** : une ligne de journal est un seul nœud d'accessibilité, avec ses actions personnalisées (modifier, supprimer) — plutôt que six nœuds à traverser.
- **Icônes seules** : toutes ont une `contentDescription`. Les décoratives sont explicitement marquées `null` pour ne pas polluer la lecture.

---

## Ressources

Trois fichiers dans `:core:designsystem`, et rien d'autre ne définit de couleur ou de durée :

```
MacroColors.kt      les six teintes et leurs dérivations
NeonTheme.kt        schéma Material 3, typographie, formes
Motion.kt           durées et courbes
```

Une valeur codée en dur dans un `:feature` est un défaut à corriger, pas un raccourci acceptable. C'est vérifié par une règle detekt personnalisée ([10](10-qualite-et-livraison.md)).

---

## Aperçus

Chaque composant expose un `@Preview` en thème sombre et en thème clair, plus un aperçu avec police à 200 % pour les composants qui affichent du texte. Ces aperçus servent aussi de base aux tests d'image, qui figent le rendu et détectent toute régression visuelle.
