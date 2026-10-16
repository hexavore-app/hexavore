# Reprise — la 1.0.0, après D168

Ce fichier dit **où en est le travail et comment le reprendre**, sans rien supposer de
la conversation qui l'a produit. Il se supprime quand ses chantiers sont faits : ce
qui doit durer vit dans `docs/`, et les décisions dans `docs/11-decisions.md`.

---

## 1. État de la branche

- **Branche** : `claude/sweet-shannon-2trz20`, partie du commit de D158 à D163
  (`b586485`, branche `fix/la-courbe-le-tour-et-le-premier-ecran`). Elle porte **D164 à
  D168**, un commit par décision, poussés, et une pull request vers `main`.
  Les décisions et leurs raisons sont dans `docs/11-decisions.md`, du titre « D164 » à
  la fin du fichier.
- **Version** : `1.0.0` / `versionCode 8` dans `gradle/libs.versions.toml` ([D168](docs/11-decisions.md)).
  Aucun code au-dessus de 7 n'était connu pour être parti ; si la Console refuse le 8,
  monter au premier libre et le commenter à côté des autres.
- **Vérifié** : `gradlew clean check --no-build-cache --no-configuration-cache` vert
  le 16 octobre 2026, **2 653 cas exécutés** (variantes debug et release), aucun échec,
  aucun ignoré — les tests de `build-logic`, build inclus que `clean` ne nettoie pas,
  sont hors compte. Campagnes de défaite de D164 (16 sabotages, 16 tombés), D165 (9/9),
  D166 (19/19), D167 (9/9) et D168 (7/7), détaillées dans leurs entrées. Le bundle de
  release se construit (R8) : code 8, nom 1.0.0, cible 36, et ses bibliothèques natives
  64 bits sont alignées sur 16 Ko.
- **Pas vérifié** : rien de D163 à D168 n'a été revu à l'émulateur **après** correction —
  l'environnement qui les a faites n'avait pas d'émulateur. La liste est au § 4, et elle
  passe avant le téléversement. Les tests instrumentés (`connectedAndroidTest`) n'ont pas
  tourné.
- **Site** (`hexavore-site`, dépôt à part) : deux branches à fusionner à deux moments
  différents, voir § 5.

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
- **Chaque correction** : une décision `D169`, `D170`… dans `docs/11-decisions.md`
  (`## DNNN — titre · ✓ validée`), la doc concernée mise à jour (l'ancien texte barré
  `~~…~~` quand il était faux), un test par règle, et une **campagne de défaite** —
  saboter la correction, vérifier qu'un cas tombe, défaire (voir § 7).
- Aucun pronom pour l'auteur, Charly Flu, dans les textes.

## 3. Publier la 1.0.0 en production

Dans l'ordre. Le test fermé est terminé et les formulaires sont remplis ; ce qui suit
est ce que la 1.0.0 change ou demande encore. Les textes à coller sont tous dans
[`tooling/fiche/fiche-play-store.md`](tooling/fiche/fiche-play-store.md).

1. **Les vérifications à l'émulateur du § 4**, sur la release, avant tout téléversement.
2. **Fusionner la pull request dans `main`.** Le workflow `politique.yml` republie alors
   la politique de confidentialité sur le site : vérifier que
   `https://hexavore.app/confidentialite/` est datée du **16 octobre 2026** et dit
   « fausse ou choquante ». Si le workflow échoue, la clé `SITE_DEPLOY_KEY` est en cause
   (README du site, § Publier).
3. **Construire le bundle signé** depuis `main` : `gradlew.bat :app:bundleRelease`, avec
   la clé de téléversement de la machine de l'auteur. Le bundle sort non signé sans elle.
4. **Fiche principale**, dans les deux langues : recoller les **deux descriptions
   complètes**. Elles ont changé ([D168](docs/11-decisions.md)) : la phrase de la photo
   (les estimations de l'IA se disent), la ligne de l'appareil photo, la phrase « sans
   clé », celle des sources, et l'avertissement santé — dont Google Play exige les mots
   dans la description.
5. **Contenu de l'application → Accès à l'application** : « Tout ou partie des
   fonctionnalités sont soumises à restrictions », et le texte de la fiche (§ Accès à
   l'application, pour l'examen). Aucune clé n'est fournie.
6. **Contenu de l'application → Applications de santé** : la catégorie « Nutrition et
   gestion du poids » ; rien de médical.
7. **Sécurité des données** : relire, sans rien changer. D164 à D168 n'ajoutent aucun
   flux réseau ; le signalement change de motif, pas de contenu.
8. **Production → Créer une release** : téléverser le bundle, coller les **notes de
   version 1.0.0** (fiche, deux langues), puis envoyer pour examen. Un déploiement
   progressif (20 % d'abord) laisse le temps de voir un plantage avant tout le monde ;
   les traces de plantage n'arrivent que par les courriels que les utilisateurs
   choisissent d'envoyer, et la Console ne montre que ce qu'Android remonte.
9. **Une fois la 1.0.0 en ligne**, le site : § 5, seconde branche.

## 4. Vérifications à l'émulateur qui restent dues

Construire la release (`gradlew.bat :app:assembleRelease`), l'installer sur
`emulator-5554` par-dessus la précédente (`adb -s emulator-5554 install -r …`), puis
regarder ce qui suit. Les fonctions d'IA demandent la clé de l'auteur, collée par
l'auteur dans l'émulateur.

**La mort du processus, la procédure** (D166) : remplir l'écran, passer Hexavore en
arrière-plan (bouton d'accueil), `adb -s emulator-5554 shell am kill app.hexavore`, puis
revenir par les applications récentes. La saisie doit revenir telle qu'elle était.

**D168.**
- L'accueil d'un premier lancement (`adb -s emulator-5554 shell pm clear app.hexavore`,
  ce qui efface tout) : l'avertissement allongé se lit en entier, en français puis en
  anglais, et à `adb -s emulator-5554 shell settings put system font_scale 1.3` (remettre
  `1.0` après) ; « Continuer » reste atteignable par le défilement. Le même texte dans
  Réglages → À propos.
- Une proposition d'IA (photo ou phrase) : le bouton en forme d'insecte s'annonce
  « Signaler une proposition fausse ou choquante » ; la boîte titre « Proposition fausse
  ou choquante ? » ; le courriel préparé a pour objet « Proposition d'IA fausse ou
  choquante ». Ne pas l'envoyer. Même chose en anglais.

**D167.**
- Enregistrer un favori en français, passer l'application en anglais (Réglages →
  Apparence), puis Réglages → Favoris **et** « + » → Favoris : les composants portent les
  noms anglais, les mêmes que l'éditeur ouvert sur ce favori. Une ligne tapée à la main
  garde son nom.
- Animations coupées (`adb -s emulator-5554 shell settings put global
  animator_duration_scale 0`) : franchir un palier — enregistrer un plat avec l'étoile
  allumée donne « Premier favori » sur un journal qui n'en a pas ; le titre et sa ligne
  restent environ deux secondes et demie, puis disparaissent. Animations remises (`1`),
  la gerbe passe comme avant.

**D166**, écran par écran, avec la procédure ci-dessus :
- **le brouillon de plat**, lignes remplies et champs à moitié tapés (« 150, ») ; puis
  avec une photo ajoutée ; puis une proposition d'IA ouverte et pas encore enregistrée
  (le cas le plus cher : elle ne se rejoue pas) ;
- **l'analyse**, une phrase tapée et pas envoyée ;
- **l'aliment personnel**, nom, marque, portion et valeurs ;
- **l'onboarding**, arrêté à la troisième étape : il reprend à cette étape, avec ses
  réponses ;
- **le profil en cours de modification** ;
- **la boîte de pesée**, ouverte, avec un jour et un poids changés ;
- et le contraire : un repas **enregistré** ne revient pas après la mort du processus.

**D165.**
- Journal de poids : un appui, ou un appui long, sur une pesée ouvre son menu,
  « Supprimer » ; la pesée quitte la liste et la courbe, et « Annuler » la remet pendant
  cinq secondes ; passé ce délai, elle reste partie. Avec TalkBack, la ligne ouvre le
  même menu.
- Retirer la seule pesée : le profil se retrouve sans poids, et l'écran du profil comme
  les pastilles le disent.

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

Le dépôt `hexavore-site` a **deux changements à fusionner à deux moments** :

1. **Maintenant** : la branche `fix/la-courbe-le-tour-et-le-premier-ecran` (D161, D162),
   déjà poussée. Elle retire du site la phrase fausse que la fiche portait aussi — les
   valeurs « pas de sa mémoire » — et dit que l'application ne transmet que les courriels
   qu'on envoie. Aucune pull request n'est ouverte pour elle.
2. **Une fois la 1.0.0 en ligne en production** : la branche `fix/production-1-0-0`, faite
   par-dessus la première ([D168](docs/11-decisions.md)) — « Disponible sur Google Play,
   pour Android 8 et plus », le bouton principal vers la fiche, l'avertissement santé de
   l'application, les estimations de l'IA dans la phrase des sources. Avant la mise en
   ligne, le lien de la fiche ne s'ouvre pas pour le public. **Elle n'a pas pu être
   poussée** : l'application GitHub de Claude n'a pas accès à `hexavore-site`. Le commit
   est en **annexe C** ; le copier tel quel dans un fichier `hexavore-site-1.0.0.patch`,
   puis, depuis une copie du site :

   ```
   git fetch origin
   git checkout -b fix/production-1-0-0 origin/fix/la-courbe-le-tour-et-le-premier-ecran
   git am chemin/vers/hexavore-site-1.0.0.patch
   npm run check && npm run build
   git push -u origin fix/production-1-0-0
   ```

   Vérifié avant livraison : `npm run check` (57 tests) et `npm run build` (six pages,
   aucun JavaScript), la politique lue depuis cette branche-ci de l'application.

La politique de confidentialité, elle, ne passe par aucune de ces branches : elle se
republie seule quand `confidentialite/` change sur `main` (§ 3, étape 2).

## 6. Ce qui reste après la 1.0.0

Rien de ceci ne bloque la production.

- **Le paysage** reste « non optimisé » (`docs/01`) : titre et calendrier fixes, barre du
  bas fixe, il reste peu de hauteur au journal. Ne rien faire sans décision.
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

Un sabotage qui survit veut dire un test à affûter, pas une campagne réussie.

**Un test qui lit le dépôt** — des ressources, la fiche, des assets, une table — n'est
relancé par Gradle que si ce qu'il lit est déclaré en entrée de sa tâche ; sinon, un
sabotage qui ne touche que ce fichier « survit » sans que le test ait tourné
([D168](docs/11-decisions.md)). Les cinq tests de ce genre déclarent leurs entrées ; un
sixième doit faire de même. Un sabotage de `strings.xml` se fait et se défait **à la
main**, comme toute modification de ces fichiers.

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

## Annexe C — Le correctif du site

Le commit `fix/production-1-0-0` du dépôt `hexavore-site`, au format de `git am` (§ 5).
Il s'applique par-dessus `fix/la-courbe-le-tour-et-le-premier-ecran`.

````
From dca8393c4baec702ee5481b50eb969084d9c8bda Mon Sep 17 00:00:00 2001
From: Claude <noreply@anthropic.com>
Date: Fri, 16 Oct 2026 19:15:42 +0000
Subject: [PATCH] feat(accueil): la 1.0.0 sur Google Play, et l'avertissement
 que Play exige -- D168

- Hero: "Disponible sur Google Play, pour Android 8 et plus", primary
  button to the store listing (site.store); the code stays linked in the
  header and footer.
- Health disclaimer in the words of the Play Health Content and Services
  policy, the same text as the app and the store listing.
- Open sources: "apart from AI estimates, which are marked as such".

To merge once the 1.0.0 is live in production: before that, the store
link does not open for the public.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PyJF7hzbuvHyJranqQ86k4
---
 src/i18n/en.tsx         | 10 +++++-----
 src/i18n/fr.tsx         | 11 ++++++-----
 src/pages/home/Hero.tsx |  6 ++++--
 src/site.ts             |  3 +++
 4 files changed, 18 insertions(+), 12 deletions(-)

diff --git a/src/i18n/en.tsx b/src/i18n/en.tsx
index c68e851..14d5869 100644
--- a/src/i18n/en.tsx
+++ b/src/i18n/en.tsx
@@ -33,8 +33,8 @@ export const en: Dictionary = {
       eyebrow: 'Food tracking for Android',
       title: 'Hexavore',
       lead: 'Log what you eat in seconds. Six counters, and nothing else: no account, no ads, no telemetry.',
-      status: 'In development, not released yet.',
-      primaryAction: 'Follow the project on GitHub',
+      status: 'Available on Google Play, for Android 8 and later.',
+      primaryAction: 'Get it on Google Play',
       secondaryAction: 'What leaves your phone',
     },
     entry: {
@@ -112,8 +112,8 @@ export const en: Dictionary = {
       title: 'Open, and sourced',
       body: (
         <>
-          The <a href={site.repository}>app’s source code</a> is public, under the GPL-3.0 license. Nutrition values
-          come from two open sources, credited in the app:
+          The <a href={site.repository}>app’s source code</a> is public, under the GPL-3.0 license. Apart from AI
+          estimates, which are marked as such, nutrition values come from two open sources, credited in the app:
         </>
       ),
       sources: [
@@ -126,7 +126,7 @@ export const en: Dictionary = {
       ],
     },
     disclaimer:
-      'Hexavore is a personal tracking tool. It is not a medical device and does not replace the advice of a health professional.',
+      'Hexavore is a personal tracking tool. It is not a medical device and does not diagnose, treat, cure, or prevent any medical condition. Consult a healthcare professional for medical advice, diagnosis, or treatment.',
   },
 
   privacy: {
diff --git a/src/i18n/fr.tsx b/src/i18n/fr.tsx
index d776996..c1e4704 100644
--- a/src/i18n/fr.tsx
+++ b/src/i18n/fr.tsx
@@ -33,8 +33,8 @@ export const fr: Dictionary = {
       eyebrow: 'Suivi alimentaire pour Android',
       title: 'Hexavore',
       lead: 'Notez ce que vous mangez en quelques secondes. Six compteurs, et rien d’autre : pas de compte, pas de publicité, pas de télémétrie.',
-      status: 'En développement, pas encore publiée.',
-      primaryAction: 'Suivre le projet sur GitHub',
+      status: 'Disponible sur Google Play, pour Android 8 et plus.',
+      primaryAction: 'Télécharger sur Google Play',
       secondaryAction: 'Ce qui quitte votre téléphone',
     },
     entry: {
@@ -113,8 +113,9 @@ export const fr: Dictionary = {
       title: 'Libre, et sourcée',
       body: (
         <>
-          Le <a href={site.repository}>code de l’application</a> est public, sous licence GPL-3.0. Les valeurs
-          nutritionnelles viennent de deux sources ouvertes, créditées dans l’application :
+          Le <a href={site.repository}>code de l’application</a> est public, sous licence GPL-3.0. Hors
+          estimations de l’IA, signalées comme telles, les valeurs nutritionnelles viennent de deux sources
+          ouvertes, créditées dans l’application :
         </>
       ),
       sources: [
@@ -128,7 +129,7 @@ export const fr: Dictionary = {
       ],
     },
     disclaimer:
-      'Hexavore est un outil de suivi personnel. Ce n’est pas un dispositif médical et il ne remplace pas l’avis d’un professionnel de santé.',
+      'Hexavore est un outil de suivi personnel. Ce n’est pas un dispositif médical : il ne diagnostique, ne traite, ne guérit ni ne prévient aucune maladie. Pour un avis, un diagnostic ou un traitement, consultez un professionnel de santé.',
   },
 
   privacy: {
diff --git a/src/pages/home/Hero.tsx b/src/pages/home/Hero.tsx
index 6b9bc58..09487ed 100644
--- a/src/pages/home/Hero.tsx
+++ b/src/pages/home/Hero.tsx
@@ -10,7 +10,9 @@ import './Hero.css'
  * Le titre de la page, ce que fait l'application, et où en est le projet.
  *
  * **Le statut est dit en premier regard**, pas en bas de page : une vitrine qui laisse
- * chercher un bouton de téléchargement qui n'existe pas encore trompe par omission.
+ * chercher un bouton de téléchargement qui n'existe pas encore trompe par omission. Depuis
+ * la 1.0.0, ce bouton existe, et c'est le premier ; le code reste lié dans l'en-tête et le
+ * pied de page.
  */
 export function Hero() {
   const { locale, t } = useLocale()
@@ -26,7 +28,7 @@ export function Hero() {
           {hero.status}
         </p>
         <div className="hero__actions">
-          <ButtonLink href={site.repository} variant="primary">
+          <ButtonLink href={site.store} variant="primary">
             {hero.primaryAction}
           </ButtonLink>
           <ButtonLink href={href('privacy', locale)} variant="secondary">
diff --git a/src/site.ts b/src/site.ts
index 92afd37..a67a278 100644
--- a/src/site.ts
+++ b/src/site.ts
@@ -10,6 +10,8 @@ export interface SiteFacts {
   readonly origin: string
   readonly repository: string
   readonly siteRepository: string
+  /** La fiche de l'application sur Google Play, par son identifiant applicatif. */
+  readonly store: string
   readonly contact: string
   /**
    * L'éditeur.
@@ -34,6 +36,7 @@ export const site: SiteFacts = {
   origin: 'https://hexavore.app',
   repository: 'https://github.com/hexavore-app/hexavore',
   siteRepository: 'https://github.com/hexavore-app/hexavore-site',
+  store: 'https://play.google.com/store/apps/details?id=app.hexavore',
   contact: 'contact@hexavore.app',
   publisher: 'Charly Flu',
   host: {
-- 
2.43.0
````
