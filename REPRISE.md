# Reprise — le tour d'avant publication, après D164

Ce fichier dit **où en est le travail et comment le reprendre**, sans rien supposer de
la conversation qui l'a produit. Il se supprime quand ses chantiers sont faits : ce
qui doit durer vit dans `docs/`, et les décisions dans `docs/11-decisions.md`.

---

## 1. État de la branche

- **Branche** : `fix/la-courbe-le-tour-et-le-premier-ecran`, partie de `main` après
  la PR #100 (D157, version 0.9.0). Elle porte **D158 à D163** en un seul commit :
  les décisions et leurs raisons sont dans `docs/11-decisions.md`, du titre
  « D158 » à la fin du fichier. **D164**, la table des noms courants (l'ancien chantier
  3.1), est faite par-dessus, sur la branche `claude/sweet-shannon-2trz20` partie de
  ce commit, sans commit à ce jour.
- **Version** : toujours `0.9.0` / `versionCode 7` dans `gradle/libs.versions.toml`.
  La monter est une décision de publication, pas prise ici.
- **Vérifié** : `gradlew clean check` vert, sans cache de build, cas exécutés comptés ;
  campagne de défaite de D162 (12 sabotages, 12 tombés), de D163 et de D164 (résultat
  dans leurs entrées).
- **Pas vérifié** : aucune correction de D163 ni de D164 n'a été revue à l'émulateur **après**
  correction (les défauts, eux, l'ont été avant) ; plusieurs points de D162 non plus —
  la liste est dans les entrées D162 et D163, § « Ce que le vert ne prouve pas ». Les
  tests instrumentés (`connectedAndroidTest`) n'ont pas tourné.
- **Site** (`hexavore-site`, dépôt à part) : la phrase « estimations » de D162 sur la
  page d'accueil, dans les deux langues. Voir § 5.

## 2. Règles de travail sur ce dépôt (à relire avant de toucher à quoi que ce soit)

- **Appareils** : n'utiliser **que** l'émulateur `emulator-5554` (AVD `Hexavore_API36`).
  Un téléphone personnel peut être branché (série `c0104279`) : ne jamais le cibler,
  toujours `adb -s emulator-5554`. Ne pas lancer `connectedAndroidTest`.
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
  du design system) et `TimeOutsideClock` (l'heure se lit sur le port `Clock`).
- **`strings.xml` se modifie à la main**, jamais par script ; fins de ligne LF.
- **Chaque correction** : une décision `D164`, `D165`… dans `docs/11-decisions.md`
  (`## DNNN — titre · ✓ validée`), la doc concernée mise à jour (l'ancien texte barré
  `~~…~~` quand il était faux), un test par règle, et une **campagne de défaite** —
  saboter la correction, vérifier qu'un cas tombe, défaire (voir § 6).
- Aucun pronom pour l'auteur, Charly Flu, dans les textes.

## 3. Les chantiers, par ordre d'importance

### 3.1 La saisie ne survit pas à la mort du processus

**Le défaut.** `docs/02` le promettait ; [D40](docs/11-decisions.md#d40--ce-que-la-tranche-2-ne-construit-pas---validée) l'a reporté « avec le deuxième écran qui
a une saisie longue », et il y en a cinq. Vérifié à l'émulateur sur l'aliment personnel :
Hexavore en arrière-plan, `adb -s emulator-5554 shell am kill app.hexavore`, retour par
les applications récentes — seul le nom, venu de la route, revient. D163 n'a sauvé que
la **photo** en cours de prise.

**Le plan.** Un mécanisme transverse plutôt qu'un écran à la fois :

1. Dans chaque `ViewModel` de saisie, l'état du formulaire en `@Serializable` (des DTO
   à côté du `ViewModel` quand le domaine n'est pas sérialisable — `Food`,
   `Suggestion`… ne le sont pas), écrit en JSON dans **son** `SavedStateHandle` à chaque
   changement, relu à la construction **avant** l'ouverture depuis la route. Attention au
   piège de [D52](docs/11-decisions.md#d52--deux-savedstatehandle-une-seule-saisie-manuelle-des-grammes-entiers---validée) : le `SavedStateHandle` du `ViewModel` n'est pas celui de l'entrée de
   pile.
2. Par ordre de coût perdu : le **brouillon de plat** (`feature/entry`, `EntryForm` et ses
   lignes — y compris un plat proposé par l'IA, qui a coûté un appel), le **texte de
   l'analyse** (`feature/capture`, la photo étant déjà en mémoire dans l'état), l'**aliment
   personnel** (`feature/search/CustomFoodViewModel`), l'**onboarding**, le **profil**, la
   **pesée**.
3. Garder un œil sur la taille : quelques kilo-octets par écran, jamais d'image.
4. Tests : pour chaque `ViewModel`, un aller-retour — remplir, recréer avec le même
   `SavedStateHandle`, retrouver la saisie. À l'émulateur : la procédure ci-dessus, écran
   par écran.
5. Docs : `docs/02` § Restauration d'état (retirer la phrase de D163 qui dit que ce
   n'est pas tenu), une décision.

### 3.2 Une pesée ne se supprime pas

Le port `WeightLog` (`domain/.../profile/Profiles.kt`) n'a que `record`. Une pesée tapée
sur la mauvaise date reste dans la courbe, la pente de l'adaptation et le poids du calcul.
Plan : `suspend fun delete(date: LocalDate)` dans le port, le DAO, l'adaptateur Room et
`InMemoryWeightLog` ; un cas d'usage ; sur l'écran de poids, un appui long sur une ligne
de la liste ouvre « Supprimer », suivi de la barre d'annulation comme pour un plat
(`Timing.UNDO_WINDOW_MILLIS`, la suppression immédiate et la restauration par un
second geste, comme `onDeleteDish`). Tests du port, du cas d'usage et du `ViewModel`.

### 3.3 Petits défauts relevés, non corrigés

- **La liste des favoris** (`feature/settings/FavoritesSettingsScreen.kt` et
  `feature/search/FavoritesScreen.kt`) affiche `components.joinToString { it.name }`, les
  noms figés à l'enregistrement, quand l'éditeur montre ceux de la fiche vivante : après
  un changement de langue, l'une est en anglais et l'autre en français. Relire les noms
  par la fiche (`foodId`) au moment de lister.
- **La célébration sans animations** ne dure qu'une image (`Celebration.kt` appelle
  `onDone` tout de suite quand la durée vaut zéro), alors que son commentaire la dit
  « remplacée par le seul texte ». Un délai qui n'est pas une animation appartient à
  `Timing`, pas à `Motion`.
- **Le paysage** reste « non optimisé » (`docs/01`) : titre et calendrier fixes, barre du
  bas fixe, il reste peu de hauteur au journal. Ne rien faire sans décision.

## 4. Vérifications à l'émulateur qui restent dues

Construire la release (`gradlew.bat :app:assembleRelease`), l'installer sur
`emulator-5554` par-dessus la précédente (`adb -s emulator-5554 install -r …`), puis :

**D163.**
- Restaurer le mois d'essai (annexe A) : la file des paliers passe sans rester figée
  (animations coupées sur cet émulateur : `settings get global animator_duration_scale`
  rend 0) ; la carte « Ajuster votre objectif ? » ne propose plus « Augmenter … de
  7 kcal » ; elle ne paraît plus au-dessus d'« Hier ».
- Descendre jusqu'aux plats d'hier, revenir à aujourd'hui : la page repart du haut.
- Réglages → Notifications : la permission y est refusée pour de bon
  (`dumpsys package app.hexavore | grep POST_NOTIF` montre `USER_FIXED`) ; « Autoriser
  les notifications » doit ouvrir l'écran des notifications d'Hexavore dans Android, et
  l'avertissement disparaître au retour après les avoir permises.
- Scanner et « Favoris » (bouton « + ») : rien sous l'heure ni sous la barre de geste.
- Pivoter l'écran (`settings put system accelerometer_rotation 0`, puis
  `settings put system user_rotation 1` ; remettre `0` et `1` après) : la semaine ne
  prend plus la hauteur de l'écran.
- Recherche « nutella » → « Chercher dans Open Food Facts » : en cas d'échec,
  « Réessayer » relance.
- La photo : nouvelle saisie → « Ajouter une photo » → « Prendre une photo » ; quand
  l'appareil photo du système est ouvert, `adb -s emulator-5554 shell am kill
  app.hexavore` ; prendre et valider ; la vignette doit apparaître.

**D164.** Installer **par-dessus** une version en r7 (la 0.9.0, ou une release d'avant
D164), pour voir la recopie de la base : rechercher « riz », « café », « café noir »,
« lait », « avocat » — la forme qu'on mange en premier. Puis, avec la clé de l'auteur
collée, une photo d'assiette (riz, poulet, haricots verts) : les lignes se remplissent
sans relecture. Passer l'application en anglais et chercher « chicken », « salmon » : le
filet grillé, le saumon cuit.

**D162**, jamais vus : la question des notifications sur un onboarding neuf (après
`adb -s emulator-5554 shell pm clear app.hexavore`, ce qui efface tout) ; la boîte de la
caméra refusée pour de bon ; la barre d'un lien quand aucun navigateur ne répond (désactiver
Chrome le temps de l'essai, puis le réactiver) ; les libellés en impérial (axe du graphique,
lecture d'une pesée, carte d'ajustement, rythme du profil) ; la version écrite dans un
export ; l'énergie du calvados et du pastis allongé à la recherche.

Ce qui est vu se consigne dans la décision, § « Ce que le vert ne prouve pas ».

## 5. Le site

Le dépôt `hexavore-site` porte la phrase de D162 (« des estimations… ») dans
`fr.tsx` et `en.tsx`, sur une branche du même nom que celle-ci. Le statut « pas encore
publiée » reste jusqu'à ce que la fiche Play soit publique, et la politique de
confidentialité n'est en ligne qu'après fusion dans `main`.

## 6. Le harnais de défaite

Pour chaque correction : un script applique un remplacement de texte dans la source,
lance les tests du module **et seulement ceux de la classe visée**, compte les cas
exécutés dans `build/test-results` (plus récents que le lancement), note « TOMBE » ou
« SURVIT », puis remet la source telle qu'elle était, dans un `finally`. Commande type :

```
gradlew.bat -p <dépôt> :domain:test --tests app.hexavore.domain.food.FoodRankingTest --no-build-cache --no-configuration-cache -q
```

Un sabotage qui survit veut dire un test à affûter, pas une campagne réussie.

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
