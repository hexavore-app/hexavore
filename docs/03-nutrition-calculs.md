# 03 — Calculs nutritionnels

Toutes les formules de l'application, avec leurs sources et leurs limites. Ce document est la référence unique : aucun chiffre magique ne doit apparaître ailleurs dans le code sans renvoyer ici.

Ces calculs vivent dans le module `:domain`, en Kotlin pur, sans aucune dépendance Android. C'est ce qui permet de les tester exhaustivement en quelques millisecondes.

---

## Métabolisme de base (BMR)

**Mifflin-St Jeor**, la formule de référence depuis 1990 et celle que recommande l'Academy of Nutrition and Dietetics pour la population générale. Elle bat Harris-Benedict en précision sur les populations modernes et, contrairement à Katch-McArdle, ne demande pas un taux de masse grasse que personne ne connaît.

```
Homme :  BMR = 10 × poids(kg) + 6,25 × taille(cm) − 5 × âge + 5
Femme :  BMR = 10 × poids(kg) + 6,25 × taille(cm) − 5 × âge − 161
Non précisé : moyenne des deux, soit le terme constant à −78
```

Le poids utilisé est le **poids actuel** (dernière pesée, ou celui de l'onboarding).

Marge d'erreur intrinsèque : ±10 % environ sur un individu donné. C'est précisément pourquoi l'ajustement hebdomadaire existe — la formule sert de point de départ, la réalité corrige.

## Dépense énergétique totale (TDEE)

```
TDEE = BMR × facteur d'activité
```

~~Cinq niveaux, d'un seul tenant.~~ **Le facteur se compose de deux réponses**
([D137](11-decisions.md)) : le métier donne le socle, chaque séance de sport ajoute.

```
facteur = socle du métier + 0,05 × séances hebdomadaires, plafonné à 1,90
```

| Métier | Libellé affiché | Socle |
|---|---|---|
| `DESK` | Assis la majeure partie du temps — bureau, conduite, études | 1,20 |
| `ON_FEET` | Debout, sans porter de charge — vente, service, enseignement | 1,40 |
| `PHYSICAL` | Effort physique soutenu — bâtiment, manutention, agriculture | 1,60 |

Les séances vont de 0 à 5. Au-delà, l'écart entre six et sept ne change presque rien au
besoin calculé, et prétendre le contraire donnerait une précision que la formule ne
tient pas. Le plafond de 1,90 n'est pas décoratif : au-delà, Mifflin-St Jeor sort du
domaine où elle a été validée.

**Une seule question mélangeait deux choses.** Un maçon sans sport dépense davantage
qu'un cadre qui court trois fois par semaine, et les deux se reconnaissaient dans
« modérément actif ». Le travail occupe huit heures par jour : c'est un socle, et il ne
se devine pas depuis un nombre de séances.

**Les trois socles et l'incrément ont été choisis ensemble** pour que « debout, trois
séances » retombe exactement sur **1,55** — le facteur de l'exemple de référence plus
bas, qui n'a donc pas bougé.

Un profil écrit avant cette version se traduit à la migration, à moins de trois
centièmes de facteur près.

L'exercice est intégré ici, pas ajouté au jour le jour. Un utilisateur qui note ses séances finit systématiquement par surestimer sa dépense et par « manger ses calories brûlées » — les montres et les applications de sport surévaluent la dépense de 20 à 90 % selon les études. Un multiplicateur stable est moins précis un jour donné, mais plus juste sur un mois.

## Les mesures acceptées

Rien ne bornait la saisie : 1 cm, 120 ans ou 999 kg entraient tels quels dans Mifflin-St Jeor ([D160](11-decisions.md)). Les bornes sont celles de **l'impossible ou presque** — elles écartent une faute de frappe, pas un corps rare. Elles vivent dans `BodyBounds`, et l'onboarding, le profil et le journal de poids les lisent au même endroit.

| Mesure | Plage | Pourquoi |
|---|---|---|
| Âge | 16 à 100 ans, au jour près | La dépense, les planchers et les seuils d'IMC sont ceux d'un adulte. |
| Taille | 100 à 250 cm | Hors de là, une taille tapée en mètres, ou à un zéro près. |
| Poids | 25 à 350 kg | Ce que pèse un adulte, quelle que soit sa taille. |
| IMC | 12 à 80 | Deux mesures plausibles peuvent faire un couple qui ne l'est pas : 100 cm et 300 kg. |

**Le poids se juge sur la taille**, et c'est la plage en kilos que l'écran annonce : « pour 175 cm, entre 37 et 245 kg » se corrige, « IMC hors limites » ne dit rien à personne. Les bornes de poids sont des **kilos entiers, arrondis vers l'intérieur** : ce que l'écran annonce est exactement ce que la règle accepte.

---

## Objectif calorique

```
Δpoids       = poids_cible − poids_actuel          (kg, signé)
jours        = date_cible − aujourd'hui            (jours)
Δkcal/jour   = (Δpoids × 7700) / jours
objectif     = TDEE + Δkcal/jour
```

7 700 kcal par kilo est l'équivalent énergétique communément retenu pour le tissu adipeux (≈ 7 000 kcal de lipides purs, plus la fraction d'eau et de protéines du tissu). C'est une approximation linéaire qui perd en validité au-delà de six mois : d'où, encore une fois, l'ajustement hebdomadaire.

### Garde-fous

Appliqués dans cet ordre, chacun pouvant réduire l'ambition mais jamais l'augmenter.

1. **Vitesse maximale.** Perte ≤ 1 % du poids corporel par semaine. Prise ≤ 0,5 %. Au-delà, la part de masse maigre perdue (ou de masse grasse prise) grimpe fortement.
2. **Écart maximal au TDEE.** |Δkcal| ≤ 25 % du TDEE.
3. **Plancher absolu.** 1 200 kcal (femme) / 1 500 kcal (homme) / 1 350 kcal (non précisé). En dessous, couvrir les besoins en micronutriments devient irréaliste sans supplémentation. **Il retient un déficit, il ne crée jamais de surplus** ([D160](11-decisions.md)) : quand la dépense estimée est déjà sous le plancher — un petit gabarit, un grand âge —, aucun déficit n'est proposé, l'objectif reste la dépense, et l'écran le dit en conseillant un avis professionnel. Remonter l'objectif jusqu'au plancher faisait d'une perte une prise, et d'un maintien aussi.
4. **Plafond de prise.** +20 % du TDEE. Au-delà, le surplus part majoritairement en gras.

Quand un garde-fou mord, l'application **ne refuse pas** : elle recalcule la date atteignable et la propose. *« Perdre 12 kg en 2 mois demanderait un déficit trop important. À un rythme sûr, ce serait atteint vers le 14 mars. »* — bouton **Utiliser cette date**, et possibilité de conserver la date initiale en acceptant un objectif tronqué, clairement signalé.

L'interdiction pure et simple pousse les gens à mentir sur leur poids pour contourner l'outil. Expliquer et proposer fonctionne mieux.

### Le poids visé

Les garde-fous bornent un **rythme**, et l'application ne refuse pas une échéance. Le **poids visé**, lui, se refuse quand il ne tient pas ([D160](11-decisions.md)) — c'est une valeur qu'on corrige, pas une ambition qu'on modère :

- **il suit le sens de la stratégie** : plus bas que le poids actuel pour perdre, plus haut pour prendre. Le calcul n'en produit plus d'écart du mauvais côté — un poids visé à contresens y vaut zéro —, et l'écran dit pourquoi il refuse ;
- **une perte s'arrête au seuil de la maigreur**, un IMC de 18,5, au kilo supérieur : 62 kg pour 182 cm. Quelqu'un qui y est déjà ne se voit plus proposer de perte du tout ;
- **une prise s'arrête à ce que ce corps peut peser**, la borne haute de la plage du poids.

---

## Répartition des macronutriments

Calculée dans cet ordre : protéines d'abord (le besoin le plus contraint), lipides ensuite (un plancher physiologique), **fibres** puis glucides en solde.

Les fibres passent avant les glucides parce qu'elles **consomment de l'énergie** : 2 kcal/g au sens du règlement UE 1169/2011, et elles sont comptées séparément des glucides dans CIQUAL comme dans Open Food Facts. Les calculer après le solde reviendrait à distribuer deux fois les mêmes calories ([D24](11-decisions.md#d24--les-fibres-sont-déduites-du-solde-glucidique---validée)).

### Protéines

Exprimées en g par kg de **poids cible** — sinon une personne en surpoids important se voit attribuer un objectif protéique irréaliste.

| Phase | g/kg de poids cible | Raison |
|---|---|---|
| Déficit | 1,8 | Préserve la masse maigre quand l'apport énergétique est bas |
| Maintien | 1,6 | Couvre largement les besoins d'un adulte actif |
| Prise | 2,0 | Optimum pour la synthèse protéique musculaire |

Bornes : minimum 0,8 g/kg (apport de sécurité), maximum 2,5 g/kg (au-delà, aucun bénéfice démontré, et les glucides restants deviennent trop faibles).

### Lipides

- **Déficit** : 25 % des calories.
- **Maintien et prise** : 30 %.
- **Plancher** : 0,6 g/kg de poids corporel, quelle que soit la phase — en dessous, l'absorption des vitamines liposolubles et la production hormonale sont compromises. Ce plancher prime sur le pourcentage.

### Fibres

14 g pour 1 000 kcal (repère de l'Institute of Medicine), avec un plancher de 25 g et un plafond de 50 g.

### Glucides

Le solde, **fibres déduites** :

```
glucides = (kcal − 4 × protéines − 9 × lipides − 2 × fibres) / 4
```

Si le solde tombe sous **100 g**, l'application rééquilibre en réduisant les lipides jusqu'à leur plancher, puis les protéines jusqu'à 1,4 g/kg. Les fibres ne sont jamais réduites pour dégager des glucides : leur plancher de 25 g est un besoin, pas une variable d'ajustement. Si le solde reste insuffisant, c'est que l'objectif calorique est trop bas : un avertissement s'affiche.

### Sucres

**Plafond, pas objectif.** ≤ 10 % des calories totales, repère de l'OMS pour les sucres libres. Faute de donnée fiable sur les sucres libres, l'application applique ce seuil aux sucres totaux, ce qui est plus strict — et le dit dans l'infobulle du compteur.

La distinction cible / plafond est portée jusqu'à l'interface : les cibles se remplissent, le plafond ne s'allume qu'au dépassement ([08](08-design-system.md)).

### Objectifs et limites

Les six compteurs ne se lisent pas de la même façon, et l'application doit le dire — sans quoi remplir sa jauge de sucres ressemble à une réussite.

| Compteur | Nature | Pourquoi |
|---|---|---|
| Calories | **objectif** | C'est la mesure de référence ; l'écran affiche le restant. |
| Protéines | **objectif** | Sous-consommer en déficit coûte de la masse maigre. |
| Fibres | **objectif** | Manquer de fibres se paie sur le transit et la satiété. |
| Glucides | **limite** | Le solde du budget. Personne n'a besoin d'« atteindre ses glucides ». |
| Sucres | **limite** | Plafond OMS. La limite la plus stricte des six. |
| Lipides | **limite** | Le plancher physiologique est garanti par le calcul de l'objectif, pas par la saisie du jour. |

Cette nature appartient au **domaine**, pas à l'interface : c'est une règle nutritionnelle, et la laisser à chaque écran garantit qu'un écran finira par se tromper. Sa traduction visuelle est décrite en [08](08-design-system.md#macrobar).

Le cas des lipides mérite d'être explicité, parce qu'il est le moins évident. Il existe bien un plancher — 0,6 g/kg — mais il est appliqué **au moment du calcul de l'objectif**, une fois pour toutes. Au jour le jour, l'utilisateur n'a rien à atteindre : il a un budget à ne pas dépasser.

### Cohérence énergétique

Une fois les fibres déduites du solde, la somme `4 P + 9 L + 2 F + 4 G` retombe sur l'objectif calorique à l'arrondi près — quelques kcal, jamais davantage. Règle : **les calories font foi**, les macros sont des répartitions indicatives. L'écart n'est jamais affiché ni corrigé artificiellement.

Facteurs d'Atwater utilisés partout : **protéines 4 · glucides 4 · lipides 9 · fibres 2 kcal/g**. Les fibres à 2 kcal/g suivent le règlement UE 1169/2011, cohérent avec CIQUAL et Open Food Facts. L'alcool (7 kcal/g) n'est pas modélisé en v1 ; les boissons alcoolisées sont saisies via leur fiche produit, dont les calories sont déjà justes.

---

## Exemple complet

Homme, 35 ans, 182 cm, 88 kg, sport 3 à 5 fois par semaine, veut atteindre 80 kg en 6 mois.

```
BMR   = 10×88 + 6,25×182 − 5×35 + 5           = 1 847,5 kcal
TDEE  = 1 847,5 × 1,55                        = 2 863,6 kcal

Δpoids = −8 kg ; jours = 182
Δkcal/jour = (−8 × 7700) / 182                = −338,5 kcal/jour

Garde-fous :
  vitesse = 8/26 semaines = 0,31 kg/sem = 0,35 %/sem      ✓ (< 1 %)
  écart   = 338,5 / 2863,6 = 11,8 %                       ✓ (< 25 %)
  plancher = 2 525 kcal                                   ✓ (> 1 500)

Objectif calorique = 2 525 kcal

Protéines = 1,8 × 80              = 144 g  → 576 kcal
Lipides   = 25 % × 2 525 / 9      =  70 g  → 630 kcal
            plancher 0,6×88 = 52,8 g                      ✓
Fibres    = 14 × 2,525            =  35 g  →  70 kcal
Glucides  = (2525 − 576 − 630 − 70) / 4 = 312 g → 1 248 kcal
Sucres    ≤ 10 % × 2 525 / 4      =  63 g  (plafond)

Contrôle : 576 + 630 + 70 + 1 248 = 2 524 kcal, soit 1 kcal d'arrondi.
```

Ce cas sert de test de référence dans `:domain` (`GoalCalculatorTest`). Le contrôle de cohérence en fait partie : c'est lui qui aurait attrapé les 70 kcal de fibres distribuées deux fois.

---

## Adaptation hebdomadaire

Le mécanisme qui fait qu'un objectif reste juste au bout de trois mois. Il tourne côté domaine, sans réseau, et **ne modifie jamais rien tout seul**.

### Signal

Le poids brut est inexploitable : ±2 kg d'un jour à l'autre selon l'hydratation, le sel, le transit. On travaille sur la **moyenne mobile sur 7 jours**, et on compare deux moyennes espacées de 7 jours pour obtenir une pente en kg/semaine.

Il faut au moins **3 pesées** dans chaque fenêtre pour que la pente soit calculée. Sinon, pas de suggestion — un silence vaut mieux qu'un conseil fondé sur une seule pesée.

~~**Ce plancher gouverne aussi la courbe** ([D94](11-decisions.md#d94--le-journal-de-poids-se-lit-en-trois-traits-et-la-tendance-se-tait-sous-trois-pesées---validée)) : sous trois pesées, le tracé lissé a un trou — et l'écran dit pourquoi.~~ **La courbe ne suit plus ce plancher** ([D159](11-decisions.md)) : elle trace une tendance qui oublie à proportion du temps écoulé, présente dès la première pesée, et qui suit les pesées espacées sans bout de trait isolé. Les trois pesées restent la règle du **conseil** : un silence vaut mieux qu'un ajustement fondé sur une seule pesée, là où une courbe doit montrer ce qu'il y a.

La **pente visée**, à laquelle on compare, est celle que l'objectif a annoncée : `(poids cible − poids connu au début de l'objectif) ÷ semaines entre son début et son échéance`. Elle ne se recalcule pas depuis aujourd'hui. Le faire durcirait la cible à chaque jour manqué, donc grossirait la correction proposée, qui ferait rater davantage — la boucle exacte que la borne de ±150 kcal sert à contenir.

### Conditions de déclenchement

Toutes doivent être réunies :

1. Au moins **14 jours** depuis le dernier ajustement accepté.
2. **Adhérence ≥ 70 %** : au moins 10 des 14 derniers jours ont au moins une saisie. Un objectif ne se corrige pas sur la base d'un journal troué — on ne saurait pas si l'écart vient du métabolisme ou de la saisie.
3. **Écart persistant** : |pente réelle − pente visée| > 0,15 kg/semaine sur **deux** semaines consécutives. Une seule semaine ne prouve rien.

### Correction proposée

```
écart_kg_sem  = pente_visée − pente_réelle
Δkcal_proposé = écart_kg_sem × 7700 / 7
```

Bornée à **±150 kcal** par ajustement. Une correction plus brutale rend le système instable : il surcorrige, l'utilisateur voit son objectif osciller et perd confiance.

Le nouvel objectif repasse par tous les garde-fous. **Ils peuvent annuler la correction, jamais l'inverser** ([D163](11-decisions.md)) : leurs bornes se recalculent sur la dépense du jour, et un objectif posé sur la borne quand on pesait un peu moins se retrouve sous la borne d'aujourd'hui. Le garde-fou y ramenait la correction — « augmenter l'objectif de 7 kcal » à quelqu'un dont le poids ne bouge pas sous un objectif de perte. Une correction retenue de sens contraire à l'écart ne se propose pas.

### Restitution

Une carte, sur l'écran de poids et en tête de l'accueil **quand il montre aujourd'hui** ([D163](11-decisions.md)) — une carte qui parle de trois semaines n'a rien à faire au-dessus d'un jour passé qu'on relit —, qui explique le raisonnement en deux phrases et laisse trois issues : **Accepter** (crée un nouvel objectif versionné), **Ignorer** (représentée dans deux semaines), **Ne plus proposer** (désactive l'adaptation).

Aucune notification n'est envoyée pour ça. La carte suffit.

---

## Versionnement des objectifs

Un objectif n'est **jamais modifié en place**. Toute modification — recalcul, édition manuelle, ajustement accepté — crée une nouvelle ligne, l'ancienne recevant une date de fin.

Ce choix coûte une table et une jointure. Il achète :

- une journée passée toujours comparée à l'objectif qui était le sien ;
- un historique lisible des changements de cap ;
- un `UNDO` naturel : on réactive la version précédente.

Voir [07](07-modele-de-donnees.md#goal) pour le schéma et [11](11-decisions.md#d04--objectifs-versionnés-plutôt-que-mis-à-jour-en-place---validée) pour la décision.

---

## Ce qui n'est volontairement pas modélisé

- **Thermogenèse adaptative.** Réelle mais non prédictible individuellement ; c'est l'ajustement hebdomadaire qui l'absorbe empiriquement.
- **Effet thermique des aliments.** Déjà inclus dans les facteurs d'activité.
- **Cycle menstruel.** Influence la rétention d'eau et donc le poids affiché. Le lissage sur 7 jours en atténue l'effet ; une modélisation explicite demanderait des données intimes que l'application n'a pas à collecter.
- **Composition corporelle.** Sans mesure de masse grasse fiable, tout calcul serait faux avec une fausse précision.

Chacun de ces points est une extension possible, pas un oubli.
