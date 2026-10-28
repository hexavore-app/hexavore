# Reprise — la 1.1.1, après D182

Ce fichier dit **où en est le travail et comment le reprendre**, sans rien supposer de
la conversation qui l'a produit. Il se supprime quand ses chantiers sont faits : ce
qui doit durer vit dans `docs/`, et les décisions dans `docs/11-decisions.md`.

---

## 1. Où en est le travail

- **En production : la 1.0.2** (`versionCode 10`). Le 25 octobre 2026, la fiche
  `play.google.com/store/apps/details?id=app.hexavore` répond au public et affiche 1.0.2.
  La pull request #102 qui la porte a été fusionnée le même jour : `main` la contient.
- **Le site** : hexavore-site#3 et #4 fusionnées le 25 octobre ; `hexavore.app` dit
  « Disponible sur Google Play » et mène à la fiche, en français et en anglais.
- **La 1.1.0** (`versionCode 11`) est fusionnée dans `main` le 26 octobre 2026
  (hexavore#103), et l'auteur en a téléversé le bundle dans la Console le même jour. Elle
  porte les retours de l'auteur du 25 octobre :
  - **D176** — une seule attente : l'estimation de ce que le catalogue ne connaît pas se
    fait pendant l'analyse, et l'écran de validation ne passe plus par le noir ;
  - **D177** — passé dix secondes, une note nomme l'IA qui fait attendre ;
  - **D178** — l'IA gratuite se dit en mots de tous les jours, et « Obtenir ma clé » mène
    au guide de la clé Gemini ;
  - **D179** — une astuce, ou la demande d'un avis, toutes les quelques ouvertures ;
  - **D180** — la fiche du Play Store parle à qui l'installe, et l'IA gratuite y a sa
    capture ;
  - **D181** — l'écran de validation paraît sur l'écran qu'on quitte : plus d'aplat
    sombre le temps qu'il se compose, à chaque ouverture, avec ou sans IA ;
  - la version et ses notes dans les quatre langues.
- **Vérifié** : `gradlew clean check bundleRelease --no-build-cache --no-configuration-cache` vert
  le 26 octobre 2026 : **2 759 cas exécutés**, aucun échec, aucun ignoré ; le bundle 1.1.0
  (code 11) se construit. Campagnes de défaite : D176 7/7, D177 7/7, D178 2/2, D179 16/16,
  D180 2/2, D181 1/1. **Revu à l'émulateur** le 25 octobre, consigné dans chaque décision :
  sans clé, le tour, le dialogue, les astuces, l'interrupteur et le lien de contact ;
  avec la clé de l'auteur et un réseau ralenti, la note des dix secondes et le passage à
  l'écran de validation, filmés ; la demande d'avis et son étoile. Les seize captures
  sont refaites (§ 3). Le bundle signé construit sur la branche passe le contrôle
  `dexdump` du § 3 : les trois recherches ne rendent rien. **Reste** : l'œil de l'auteur
  sur un téléphone (§ 5), et le collage dans la Console (§ 3).
- **La branche `fix/1-1-1-hexavore-au-masculin`**, une pull request vers `main`, porte la
  1.1.1 (`versionCode 12`) : **D182**, Hexavore s'accorde au masculin — trois textes
  français de l'application, la description française de la fiche, et
  `HexavoreGenderTest` qui garde la règle. Campagne de défaite 6/6. Le code 11 était déjà
  téléversé : la correction ne pouvait pas y entrer. Contrôle complet vert le 28 octobre
  2026 : **2 763 cas exécutés**, aucun échec ; le bundle signé (code 12) passe le
  `dexdump`. Un premier passage était tombé sur un `NoClassDefFoundError` passager dans
  `HomeViewModelTest`, que le module relancé seul, puis le contrôle entier, n'ont pas
  reproduit.
- **Site** : hexavore-site#5 en attente, § 4.

## 2. Règles de travail sur ce dépôt (à relire avant de toucher à quoi que ce soit)

- **Appareils** : n'utiliser **que** l'émulateur `emulator-5554` (AVD `Hexavore_API36`,
  ou `Hexavore_API30` démarré sur le même port, `-port 5554`, quand une version d'avant
  Android 15 est en cause). Un téléphone personnel peut être branché (série `c0104279`) :
  ne jamais le cibler, toujours `adb -s emulator-5554`. Ne pas lancer
  `connectedAndroidTest`.
- **Clés d'API** : ne jamais en taper dans un champ, ni en écrire une nulle part. Les
  fonctions d'IA se testent quand l'auteur a collé sa clé dans l'émulateur.
- **Gradle sous Windows** : `gradlew.bat` par chemin absolu ; `--no-build-cache` ;
  `clean` complet dès qu'un paramètre par défaut apparaît (le cache a déjà rendu des
  classes périmées) ; compter les cas **exécutés** dans `build/test-results` avant de
  conclure qu'un vert veut dire quelque chose. Si KSP dit « Storage already
  registered » : arrêter les démons (`gradlew --stop`, et le `KotlinCompileDaemon`),
  supprimer `build/kspCaches` des modules en cause, relancer.
- **Ne jamais modifier une source pendant qu'un build tourne** : le résultat ne veut
  plus rien dire.
- **Style** : KDoc en français accentué, commentaires de code en ASCII sans accents,
  messages de commit en ASCII ; ktlint (`intellij_idea`, 120 colonnes, imports triés,
  alias en dernier) ; detekt strict (`LongMethod` < 60 lignes, `TooManyFunctions` dès
  11 par fichier, `ReturnCount` 2) ; règles maison `HardcodedColor` (pas de couleur hors
  du design system), `TimeOutsideClock` (l'heure se lit sur le port `Clock`) et
  `DeprecatedSystemBars` (les barres se règlent dans le thème de fenêtre, D170).
- **`strings.xml` se modifie à la main**, jamais par script ; fins de ligne LF.
- **Chaque correction** : une décision `D176`, `D177`… dans `docs/11-decisions.md`
  (`## DNNN — titre · ✓ validée`), la doc concernée mise à jour (l'ancien texte barré
  `~~…~~` quand il était faux), un test par règle, et une **campagne de défaite** —
  saboter la correction, vérifier qu'un cas tombe, défaire (voir § 7).
- Aucun pronom pour l'auteur, Charly Flu, dans les textes.

## 3. Publier la 1.1.0, puis la 1.1.1

Dans l'ordre. Les textes à coller sont dans
[`tooling/fiche/fiche-play-store.md`](tooling/fiche/fiche-play-store.md).

1. ~~**Fusionner la pull request** une fois la CI verte.~~ **Fait le 26 octobre** : #103
   fusionnée, et la politique republiée seule ([D179](docs/11-decisions.md)) — l'artefact
   déployé dit « 25 octobre 2026 ». `hexavore.app` ne répondait pas depuis le poste de
   travail (connexion coupée) : un coup d'œil depuis un autre réseau le confirmera.
2. **Construire le bundle signé** depuis `main` : `gradlew.bat :app:bundleRelease`, avec
   la clé de téléversement de la machine de l'auteur. Le bundle sort non signé sans elle.
3. **Vérifier ce que le Play Store relève, avant de téléverser** ([D170](docs/11-decisions.md)) :
   les trois dernières commandes doivent ne rien rendre. `dexdump` est dans
   `build-tools/35.0.1` du SDK.

   ```
   unzip -o app/build/outputs/bundle/release/app-release.aab 'base/dex/*' -d dex
   dexdump -d dex/base/dex/classes*.dex > dump.txt
   grep "Window;.set\(StatusBarColor\|NavigationBarColor\|NavigationBarDividerColor\)" dump.txt
   grep -B1 "layoutInDisplayCutoutMode:I" dump.txt | grep "#int 1 "
   grep "EdgeToEdgeApi" app/build/outputs/mapping/release/mapping.txt
   ```

4. **Fiche principale, dans les deux langues** : la brève description **et** la
   description complète sont réécrites ([D180](docs/11-decisions.md)). Les recoller toutes
   les deux ; le nom de l'application ne change pas. **La description française a encore
   changé avec D182** (« Hexavore est gratuit… Il suit pour vous… ») : coller celle de
   la branche 1.1.1. La fiche ne dépend pas du bundle, elle se corrige tout de suite.
5. **Les captures** : les seize montages sont faits, dans `tooling/fiche/out/fr/` et
   `out/en/` (les précédents rangés dans `out/1.0.0/`). La 4 dit « Tout est modifiable »,
   la 6 montre l'écran d'analyse et porte un renvoi en pied d'image ; les six autres sont
   refaites à l'identique. Remplacer les huit de chaque langue, dans l'ordre des numéros.
   Le bandeau `presentation-1024x500.png` ne change pas.
6. **Production → Créer une release** : téléverser le bundle, coller les **notes de
   version 1.1.0** (fr-FR, fr-CA, en-GB, en-US), déploiement progressif.
7. **Le formulaire Data Safety ne change pas** : le registre des ouvertures reste sur le
   téléphone, et la fiche du Play Store ne s'ouvre que sous le doigt de l'utilisateur.

**La 1.1.1**, une fois sa pull request fusionnée : bundle et `dexdump` comme aux étapes 2
et 3, code 12. Deux chemins. Si la release 1.1.0 n'est pas encore envoyée à l'examen, y
remplacer le bundle 11 par le 12 et garder les notes 1.1.0 : le 11 reste consommé, sans
jamais être publié. Sinon, une release de plus, avec les **notes de version 1.1.1** de la
fiche.

## 4. Le site

hexavore-site#3 et #4 ont été fusionnées le 25 octobre 2026 : l'accueil dit
« Disponible sur Google Play, pour Android 8 et plus », et son premier bouton mène à la
fiche, en français comme en anglais. La politique s'est republiée seule à la fusion de
la 1.1.0, le 26 octobre. **En attente** : hexavore-site#5, qui accorde Hexavore au
masculin dans les titres de l'accueil, « Ce qu'il fait » et « Ce qu'il ne fait pas »
(D182).

## 5. Vérifications à l'émulateur qui restent dues

Aucune ne bloque. Ce qui a été vu est consigné dans chaque décision, § « Ce que le vert
ne prouve pas ».

**La 1.1.0, sur un téléphone** :
- **D181** : le passage vers l'écran de validation, à l'œil. L'émulateur l'a mesuré ; la
  vitesse de composition d'un vrai téléphone est la seule qui compte.
- **D176, D177** : une vraie lenteur de Gemini. L'émulateur l'a simulée par
  `adb -s emulator-5554 emu network delay 8000`, Wi-Fi coupé
  (`svc wifi disable`, `svc data enable`) : la latence n'agit que sur les données
  mobiles. `network delay none` et `svc wifi enable` ensuite.

**Avec la clé de l'auteur** :
- **D164** : la même assiette **en photo** — l'appareil photo de l'émulateur ne montre
  qu'une pièce de synthèse, et la galerie n'a pas de photo d'assiette.
- **D168** : le courriel préparé, dans un client de messagerie qui a un compte ; sur
  l'émulateur, Gmail n'en a pas. Ne pas l'envoyer.

**Sans clé** :
- **D163** : « Réessayer » sur Open Food Facts quand la recherche répond — elle renvoyait
  503 à toute requête le 16 octobre au soir.
- **D167** : une ligne tapée à la main dans un favori garde son nom après le passage en
  anglais ; la gerbe, animations remises
  (`adb -s emulator-5554 shell settings put global animator_duration_scale 1`).
- **D165** : sous TalkBack, la ligne d'une pesée ouvre le même menu. **D172** : sous
  TalkBack, la phrase d'un champ d'entiers s'annonce.
- **D162** : la carte d'ajustement en impérial.
- **D170** : les API 26 à 29, faute d'image système sur ce poste — le voile sombre de la
  barre de navigation en mode clair, l'encoche au mode par défaut.
- **D173** : le format des nombres sous Android 8 à 12, où c'est `AppCompatDelegate` qui
  impose la langue.
- **D174** : l'onboarding et la boîte de pesée, par la procédure ci-dessous.

**La mort du processus, la procédure** (D166) : remplir l'écran, passer Hexavore en
arrière-plan (bouton d'accueil), `adb -s emulator-5554 shell am kill app.hexavore`, puis
revenir par les applications récentes. La saisie doit revenir telle qu'elle était.

L'émulateur API 36 garde le mois d'essai restauré le 16 octobre — objectif de perte,
carte d'ajustement en attente —, en français, ses animations coupées. Sa variante
`debug` ~~a été effacée le 18 octobre pour jouer le tour : elle repart d'un premier
lancement, en anglais~~ est en français, sans clé ; les astuces y sont éteintes depuis
la revue du 25 octobre (« Ne plus afficher d'astuces »). L'émulateur de tir,
`Hexavore_Shots`, porte la clé de l'auteur et la base de démonstration anglaise ; la
chaîne des captures y coupe les animations, `release_screen` les rend.

## 6. Ce qui reste ~~après la 1.0.2~~ après la 1.1.0

- **Le paysage** reste « non optimisé » (`docs/01`) : titre et calendrier fixes, barre du
  bas fixe, il reste peu de hauteur au journal. Ne rien changer à la mise en page sans
  décision. Les côtés sont tenus depuis D171 ; pendant le tour, la bande à côté de
  l'encoche n'est pas voilée.
- **AGP 9** : la Console le recommande ; il attend detekt 2.0 stable (D15, D170).
- **Tests d'image** et **`CONTRIBUTING.md`**, prévus pour la 1.0 dans `docs/10` et
  partis sans elle.
- **La sauvegarde Google Drive**, repoussée (`docs/09`).
- **Sans clé, l'écran d'IA dit « Le fournisseur actif ne sait pas appeler d'outils »**,
  alors qu'aucun fournisseur n'est actif (vu le 25 octobre). Une phrase à réserver au cas
  où un fournisseur sans outils est réellement choisi.
- **Un profil de référence** (*baseline profile*) : la première composition de l'écran
  de validation coûte 0,1 à 0,3 s sur l'émulateur, optimisée ; D181 en masque l'effet,
  pas le coût.
- **Le contenu généré par une IA** : Hexavore se tient hors du champ de la règle
  ([D168](docs/11-decisions.md)). Si l'examen en juge autrement, le bouton de signalement
  existe déjà et porte le motif ; ce qui manquerait serait un signalement qui ne passe
  pas par une messagerie.

## 7. Le harnais de défaite

Pour chaque correction : un script applique un remplacement de texte dans la source,
lance les tests du module **et seulement ceux de la classe visée**, compte les cas
exécutés dans `build/test-results` (plus récents que le lancement), note « TOMBE » ou
« SURVIT », puis remet la source telle qu'elle était, dans un `finally`. Commande type :

```
gradlew.bat -p <dépôt> :domain:test --tests app.hexavore.domain.food.FoodRankingTest --no-build-cache --no-configuration-cache -q
```

Un sabotage qui survit veut dire un test à affûter, pas une campagne réussie. Une règle
detekt se sabote de la même façon : le verdict est alors l'échec de `:module:detekt`,
avec le nom de la règle dans sa sortie — et `gradlew --stop` avant, si la règle elle-même
vient de changer (docs/10).

**Un test qui lit le dépôt** — des ressources, la fiche, des assets, une table — n'est
relancé par Gradle que si ce qu'il lit est déclaré en entrée de sa tâche ; sinon, un
sabotage qui ne touche que ce fichier « survit » sans que le test ait tourné
([D168](docs/11-decisions.md)). Les six tests de ce genre déclarent leurs entrées
(`SystemBarsThemeTest`, de D170, lit les ressources de `:core:designsystem`, déjà
déclarées) ; un septième doit faire de même. Un sabotage de `strings.xml` se fait et se
défait **à la main**, comme toute modification de ces fichiers.

---

## Annexe A — Un mois de journal pour l'émulateur

Exporter une sauvegarde par l'écran Sauvegarde, la récupérer
(`adb -s emulator-5554 pull /sdcard/Download/hexavore-AAAA-MM-JJ.zip`), la compléter
avec ce script (`python -I enrich_backup.py export.zip essai.zip AAAA-MM-JJ`), la
renvoyer (`adb -s emulator-5554 push essai.zip /sdcard/Download/hexavore-essai.zip` ;
sous Git Bash, préfixer `MSYS_NO_PATHCONV=1` et donner le chemin local à la Windows),
puis la restaurer par l'écran. L'objectif doit être une **perte** avec poids cible et
échéance, sans quoi la carte d'ajustement ne paraît pas.

```python
"""Complete un export reel d'Hexavore avec un mois de vie.

- l'objectif courant demarre il y a 30 jours ;
- une pesee par jour sur 30 jours, a plat : l'objectif de perte annonce un rythme que
  le poids ne suit pas, ce qui doit faire parler l'adaptation ;
- trois plats par jour sur les 25 derniers jours, un jour saute sur sept ;
- un jour tres au-dessus de l'objectif.
"""
import datetime as dt
import gzip
import io
import json
import random
import sys
import zipfile

source, target, today_text = sys.argv[1], sys.argv[2], sys.argv[3]
today = dt.date.fromisoformat(today_text)
random.seed(162)

with zipfile.ZipFile(source) as archive:
    journal = json.loads(gzip.decompress(archive.read("hexavore.json.gz")).decode("utf-8"))
    others = {name: archive.read(name) for name in archive.namelist() if name != "hexavore.json.gz"}

goal = next(g for g in journal["goals"] if g.get("endedAt") is None)
start = today - dt.timedelta(days=30)
goal["startedAt"] = start.isoformat()

first_weight = journal["weights"][0]["weightKg"] if journal["weights"] else 80.0
journal["weights"] = [
    {"date": (start + dt.timedelta(days=i)).isoformat(), "weightKg": round(first_weight + random.uniform(-0.3, 0.3), 1)}
    for i in range(31)
]

kcal_goal = goal["kcal"]
meals = [
    ("BREAKFAST", "08:10", 0.25, [("Flocons d'avoine", 60, 0.6), ("Lait demi-écrémé", 200, 0.4)]),
    ("LUNCH", "12:45", 0.40, [("Riz blanc, cuit", 200, 0.5), ("Poulet, filet, rôti", 150, 0.5)]),
    ("DINNER", "19:50", 0.35, [("Pâtes, cuites", 180, 0.6), ("Sauce tomate", 100, 0.4)]),
]

dishes, entries = [], []
for back in range(1, 26):
    day = today - dt.timedelta(days=back)
    if back % 7 == 3:
        continue
    factor = 1.6 if back == 5 else random.uniform(0.9, 1.05)
    for moment, clock, share, lines in meals:
        dish_id = f"essai-{day.isoformat()}-{moment.lower()}"
        hour, minute = map(int, clock.split(":"))
        logged = dt.datetime(day.year, day.month, day.day, hour, minute, tzinfo=dt.timezone.utc)
        dishes.append({"id": dish_id, "date": day.isoformat(), "source": "MANUAL",
                       "loggedAt": logged.isoformat().replace("+00:00", "Z"), "moment": moment})
        for index, (name, grams, part) in enumerate(lines):
            entries.append({
                "id": f"{dish_id}-{index}", "dishId": dish_id, "displayName": name,
                "quantity": grams, "unit": "g", "grams": grams,
                "kcal": round(kcal_goal * share * part * factor),
                "protein": round(goal["protein"] * share * part * factor, 1),
                "carbs": round(goal["carbs"] * share * part * factor * 0.9, 1),
                "sugars": round(goal["sugars"] * share * part * factor * 0.6, 1),
                "fat": round(goal["fat"] * share * part * factor * 0.9, 1),
                "fiber": round(goal["fiber"] * share * part * factor, 1),
            })

journal["dishes"] = journal.get("dishes", []) + dishes
journal["entries"] = journal.get("entries", []) + entries

buffer = io.BytesIO()
with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as out:
    out.writestr("hexavore.json.gz", gzip.compress(json.dumps(journal, ensure_ascii=False).encode("utf-8")))
    for name, data in others.items():
        out.writestr(name, data)
open(target, "wb").write(buffer.getvalue())
```

Les heures sont écrites en UTC : l'émulateur, réglé sur UTC, les affiche telles quelles.

## Annexe B — Rejouer le classement sur la base livrée

Ce script reproduit la normalisation (`SearchText`), la requête plein texte et le score
de `FoodRanking` tel qu'il est **après D164** — le meilleur du libellé et des noms
courants qui nomment la requête, palier des lettres compris —, puis le verdict du
résolveur (seuils 0,75 et 0,40, saturation 0,6). Il lit `common_names` quand la base l'a,
et l'ignore sinon : il rejoue donc aussi une base d'avant D164. Il sert à mesurer une
table de noms courants avant de la livrer, ou une seconde passe sur d'autres mots ; sa
sortie avant/après est dans D164.

```python
"""python -I resolve.py core/database/src/main/assets/ciqual.db fr riz café lait ..."""
import sqlite3, sys, unicodedata
sys.stdout.reconfigure(encoding="utf-8")

def normalise(text):
    text = text.replace("œ", "oe").replace("Œ", "oe").replace("æ", "ae")
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    return " ".join("".join(ch if ("a" <= ch <= "z" or "0" <= ch <= "9") else " " for ch in text).split())

ARTICLES = {"fr": {"de", "du", "des", "d", "le", "la", "les", "un", "une", "l"},
            "en": {"the", "a", "an", "of", "some"}}
SOMEWHERE = 1.0

def proximity(name, query):
    if name == query: return 4.0
    if name.startswith(query + " "): return 2.0
    if query in name.split(" "): return 1.5
    if name.startswith(query): return 1.25
    return SOMEWHERE

def weigh(name, query):
    return proximity(name, query) * (30.0 / (30.0 + len(name)))

def score(label, common, query):
    # Le meilleur des noms : le libelle toujours, un nom courant s'il nomme la requete (D164).
    named = [n for n in (common or "").split("|") if n and proximity(n, query) > SOMEWHERE]
    return max(weigh(n, query) for n in [normalise(label)] + named)

def verdict(c):
    return "AUTO" if c >= 0.75 else ("REVUE" if c >= 0.40 else "AUCUNE")

db, lang, labels = sys.argv[1], sys.argv[2], sys.argv[3:]
c = sqlite3.connect(db)
columns = [r[1] for r in c.execute("pragma table_info(ciqual_name)")]
common = "n.common_names" if "common_names" in columns else "NULL"
for label in labels:
    words = [w for w in normalise(label).split(" ") if w]
    while words and words[0] in ARTICLES[lang]:
        words.pop(0)
    q = " ".join(words)
    rows = c.execute(
        f"select n.code, n.name, f.kcal_100, {common} from ciqual_fts join ciqual_name n on n.rowid = ciqual_fts.docid "
        "join ciqual_food f on f.code = n.code where ciqual_fts match ? and n.language = ?",
        (" ".join(w + "*" for w in words), lang)).fetchall()
    ranked = sorted(rows, key=lambda r: (-score(r[1], r[3], q), r[1]))
    if not ranked:
        print(f"{label:>16} -> rien"); continue
    best = ranked[0]; s = score(best[1], best[3], q)
    print(f"{label:>16} -> {verdict(s / (s + 0.6)):6} {s / (s + 0.6):.2f}  {best[1][:60]}  ({best[2]} kcal)")
```
