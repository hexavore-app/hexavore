# Reprise — la 1.0.1, après D175

Ce fichier dit **où en est le travail et comment le reprendre**, sans rien supposer de
la conversation qui l'a produit. Il se supprime quand ses chantiers sont faits : ce
qui doit durer vit dans `docs/`, et les décisions dans `docs/11-decisions.md`.

---

## 1. Où en est le travail

- **En production : la 1.0.0** (`versionCode 8`), fusionnée par la pull request #101
  (D158 à D169). Le 18 octobre 2026, sa fiche ne répondait pas encore au public — le lien
  `play.google.com/store/apps/details?id=app.hexavore` rendait 404 — : en examen, ou en
  attente de publication.
- **La branche `fix/1-0-1-les-retours-du-play-store`**, une pull request vers `main`,
  porte la 1.0.1 (`versionCode 9`) :
  - **D170** — les retours du Play Store : les barres du système passent dans le thème de
    fenêtre, R8 réduit les ressources, AGP monte en 8.13.2 ;
  - **D171** — les côtés de l'écran (l'encoche, une barre de navigation posée sur le
    côté) se retirent une fois, à la racine ;
  - **D172** — un champ d'entiers dit pourquoi il refuse la virgule ;
  - **D173** — un nombre s'écrit dans la langue de l'application ;
  - **D174** — le poids à moitié tapé survit à la mort du processus ;
  - **D175** — « Configurer » répond au tour guidé, qui revenait à chaque lancement
    (retour d'usage du 23 octobre) ;
  - la version, les notes de version 1.0.1 et une accroche reformulée dans la fiche ;
  - les captures de la fiche 1.0.0 (`tooling/fiche`), dans un commit à part.
- **Vérifié** : `gradlew clean check bundleRelease --no-build-cache --no-configuration-cache` vert
  le 23 octobre 2026, D175 comprise : **2 703 cas exécutés** (variantes debug et release), aucun
  échec, aucun ignoré — les 18 cas des règles detekt, dans `build-logic`, passés le même
  jour. Campagnes de défaite : D170 11/11, D171 2/2, D172 4/4, D173 5/5, D174 3/3, D175 3/3. Le
  bundle 1.0.1 (code 9) se construit et se signe, et `dexdump` n'y trouve aucun des trois
  relevés du Play Store (§ 3). Revu à l'émulateur : API 36 pour D170 à D175, API 30 pour
  D170.
- **Site** : deux pull requests sur `hexavore-site`, § 4.

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

## 3. Publier la 1.0.1

Dans l'ordre. Les textes à coller sont dans
[`tooling/fiche/fiche-play-store.md`](tooling/fiche/fiche-play-store.md).

1. **Fusionner la pull request** une fois la CI verte.
2. **Construire le bundle signé** depuis `main` : `gradlew.bat :app:bundleRelease`, avec
   la clé de téléversement de la machine de l'auteur. Le bundle sort non signé sans elle.
3. **Vérifier ce que le Play Store relève, avant de téléverser** ([D170](docs/11-decisions.md)) :
   les trois dernières commandes doivent ne rien rendre. `dexdump` est dans
   `build-tools/35.0.1` du SDK ; rejouées sur le bundle de la 1.0.0, elles retrouvaient
   exactement les trois emplacements relevés.

   ```
   unzip -o app/build/outputs/bundle/release/app-release.aab 'base/dex/*' -d dex
   dexdump -d dex/base/dex/classes*.dex > dump.txt
   grep "Window;.set\(StatusBarColor\|NavigationBarColor\|NavigationBarDividerColor\)" dump.txt
   grep -B1 "layoutInDisplayCutoutMode:I" dump.txt | grep "#int 1 "
   grep "EdgeToEdgeApi" app/build/outputs/mapping/release/mapping.txt
   ```

4. **Fiche principale**, dans les deux langues : l'accroche de la description complète
   est reformulée — elle disait l'IA « toujours payante », et la fiche dit plus bas qu'une
   clé Gemini est gratuite. À relire avant de la recoller ; le reste de la description ne
   change pas.
5. **Production → Créer une release** : téléverser le bundle, coller les **notes de
   version 1.0.1** (fr-FR, fr-CA, en-GB, en-US), déploiement progressif.
6. **Relire les recommandations de la Console** : celle des API obsolètes doit
   disparaître ; celle d'AGP 9 restera, tant que D15 tient la chaîne en Kotlin 2.0.

## 4. Le site

- [hexavore-app/hexavore-site#3](https://github.com/hexavore-app/hexavore-site/pull/3) —
  D161, D162 : la valeur estimée se dit, l'application ne transmet que les courriels
  qu'on envoie. **À fusionner maintenant.**
- [hexavore-app/hexavore-site#4](https://github.com/hexavore-app/hexavore-site/pull/4) —
  D168 : « Disponible sur Google Play », le bouton vers la fiche, l'avertissement santé.
  **En brouillon, empilée sur #3** : quand le lien de la fiche s'ouvre dans une fenêtre
  privée, la passer en « Ready for review » et la fusionner après #3 (GitHub la reporte
  sur `main`).

Vérifiés le 18 octobre : `npm run check` (57 tests) et `npm run build` (six pages,
aucun JavaScript). La politique de confidentialité ne passe par aucune de ces branches :
elle se republie seule quand `confidentialite/` change sur `main` de l'application.

## 5. Vérifications à l'émulateur qui restent dues

Aucune ne bloque. Ce qui a été vu est consigné dans chaque décision, § « Ce que le vert
ne prouve pas ».

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
`debug` a été effacée le 18 octobre pour jouer le tour : elle repart d'un premier
lancement, en anglais.

## 6. Ce qui reste après la 1.0.1

- **Le paysage** reste « non optimisé » (`docs/01`) : titre et calendrier fixes, barre du
  bas fixe, il reste peu de hauteur au journal. Ne rien changer à la mise en page sans
  décision. Les côtés sont tenus depuis D171 ; pendant le tour, la bande à côté de
  l'encoche n'est pas voilée.
- **AGP 9** : la Console le recommande ; il attend detekt 2.0 stable (D15, D170).
- **Tests d'image** et **`CONTRIBUTING.md`**, prévus pour la 1.0 dans `docs/10` et
  partis sans elle.
- **La sauvegarde Google Drive**, repoussée (`docs/09`).
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
