# -*- coding: utf-8 -*-
"""Fabrique les archives de demonstration qui remplissent l'application pour les
captures du Play Store.

**Une archive de sauvegarde, et non une base ecrite a la main.** Le format du
fichier est documente (docs/09) et tenu par des tests ; les encodages de Room ne
le sont que par les mappeurs. Semer par l'import fait donc ecrire la base par
l'application elle-meme, avec ses propres conversions : une capture ne peut pas
montrer un etat que l'application n'aurait pas pu produire.

**Les valeurs nutritionnelles sont vraies.** Elles sont lues dans le ciqual.db du
depot, jamais recopiees : un chiffre faux sur une capture de fiche Play Store est
une promesse fausse. Les noms viennent du meme endroit, dans les deux langues, ce
qui est la raison pour laquelle deux archives sortent d'ici et non une.

**Le profil est fictif.** Les captures sont publiques.

Usage :
    python tooling/fiche/demo_data.py --today 2026-09-27 --out <dossier>
"""

from __future__ import annotations

import argparse
import gzip
import json
import random
import sqlite3
import unicodedata
import zipfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
CIQUAL = REPO / "core" / "database" / "src" / "main" / "assets" / "ciqual.db"

# docs/09 : la version du format que cette archive declare. Un fichier plus recent
# que l'application est refuse avant toute ecriture, donc ce numero se suit.
FORMAT_VERSION = 1
APP_VERSION = "0.6.0"

# Le decalage entre l'heure qu'on veut lire sur la capture et l'instant ecrit dans
# le fichier.
#
# **Zero, parce que l'emulateur est en GMT et qu'il ne veut pas en changer** :
# `setprop persist.sys.timezone` est refuse sur une image `google_apis_playstore`.
# Rien de visible n'en depend -- l'horloge de la barre d'etat est posee par le mode
# demonstration -- mais une heure de plat qui ne correspond pas a son instant se
# verrait, elle : un petit-dejeuner a 06:12. Ce qui compte est que la donnee et
# l'affichage s'accordent, pas le fuseau choisi.
UTC_OFFSET_HOURS = 0

# L'aliment scanne, celui qui porte une pastille de code-barres.
#
# Le code est dans le prefixe **20-29**, que GS1 reserve a la circulation
# interne : il n'appartient a aucun produit reel, donc la capture ne fait de
# publicite a personne et ne montre pas la marque d'un tiers. Les teneurs sont
# celles d'un skyr nature ordinaire.
SCANNED = {
    "id": "food-skyr",
    "source": "OFF",
    "sourceRef": "2000000000015",
    "name": {"fr": "Skyr nature", "en": "Plain skyr"},
    "brand": None,
    "per100g": {"kcal": 63.0, "protein": 10.3, "carbs": 4.0, "sugars": 4.0, "fat": 0.2, "fiber": 0.0},
    "defaultServingG": 150.0,
}

# Les aliments du catalogue CIQUAL utilises par les repas, par code ANSES.
CODES = [
    "32140",  # Flocons d'avoine
    "19033",  # Lait demi-ecreme
    "13005",  # Banane
    "26038",  # Saumon, cuit a la vapeur
    "9104",   # Riz blanc, cuit
    "20304",  # Brocoli, cuit a la vapeur
    "17270",  # Huile d'olive vierge extra
    "15000",  # Amande
    "22500",  # Omelette nature, faite maison
    "7110",   # Pain complet
    "12115",  # Emmental
    "20353",  # Carotte, cuite
    "25604",  # Salade verte, crue
    "20030",  # Haricot vert, cuit
    "4048",   # Pomme de terre, cuite
    "20360",  # Lentille, bouillie
    "13039",  # Pomme
    "19501",  # Fromage blanc nature
    "26181",  # Thon albacore, au naturel
    "31074",  # Chocolat noir 70 %
    "2070",   # Jus d'orange, pur jus
    "17130",  # Huile de colza
    # Les aliments des deux repas photographies. Voir photos/PROVENANCE.md :
    # chacun decrit ce que l'image montre, et pas l'inverse.
    "7200",   # Pain de mie blanc, preemballe
    "22011",  # Oeuf poche -- CIQUAL n'a pas d'oeuf au plat, ni frit ni poele
    "20385",  # Tomate sans precision, crue (aliment moyen)
    "28900",  # Jambon cuit, superieur
    "20116",  # Chou blanc, cru
]

# Les trois repas de la journee visible, decrits a part de la rotation qui
# remplit l'historique.
#
# **La photo commande la composition, jamais le contraire.** Deux d'entre eux
# portent une image de repas reelle ; leurs aliments et leurs quantites decrivent
# ce qu'on y voit. Un plat qui listerait autre chose que sa photo serait un
# mensonge que la premiere personne a comparer les deux verrait.
#
# Le gouter n'a pas de photo, et c'est voulu : il vient d'un scan, et l'image
# qu'un scan garde est la vue qui a lu le code-barres, pas une assiette (D127).
TODAY = [
    (
        "BREAKFAST", "PHOTO_AI", "petit-dejeuner",
        [("7200", 90), ("22011", 60), ("20385", 60), ("17270", 5)],
    ),
    (
        "LUNCH", "PHOTO_AI", "dejeuner",
        [("7200", 100), ("12115", 35), ("28900", 60), ("20116", 100)],
    ),
    (
        "SNACK", "BARCODE", None,
        [(SCANNED["id"], 200), ("15000", 25)],
    ),
]

# Les repas de l'historique, en (code, grammes). La journee visible ne pioche
# pas ici : elle est decrite par TODAY.
BREAKFASTS = [
    [("32140", 60), ("19033", 200), ("13005", 120)],
    [("7110", 80), ("12115", 30), ("2070", 200)],
    [("22500", 150), ("7110", 60), ("2070", 150)],
]
LUNCHES = [
    [("26038", 130), ("9104", 180), ("20304", 150), ("17270", 10)],
    [("20360", 200), ("20353", 120), ("17130", 10), ("7110", 50)],
    [("26181", 120), ("4048", 200), ("20030", 150), ("17270", 10)],
    [("22500", 180), ("25604", 80), ("7110", 60), ("17130", 8)],
]
SNACKS = [
    [(SCANNED["id"], 150), ("15000", 25)],
    [("13039", 150), ("31074", 20)],
    [("19501", 120), ("13005", 100)],
]
DINNERS = [
    [("9104", 150), ("20030", 200), ("12115", 30), ("17270", 8)],
    [("4048", 250), ("26038", 110), ("25604", 70), ("17130", 8)],
    [("20360", 220), ("20353", 100), ("12115", 25)],
]

# L'origine de chaque repas, pour que les pastilles de la liste varient comme
# elles varient dans un vrai journal.
BREAKFAST_SOURCES = ["FAVORITE", "MANUAL", "TEXT_AI"]
LUNCH_SOURCES = ["PHOTO_AI", "TEXT_AI", "MANUAL", "PHOTO_AI"]
SNACK_SOURCES = ["BARCODE", "MANUAL", "BARCODE"]
DINNER_SOURCES = ["TEXT_AI", "PHOTO_AI", "MANUAL"]

# L'heure locale de chaque repas.
HOURS = {"BREAKFAST": (8, 12), "LUNCH": (12, 45), "SNACK": (16, 30), "DINNER": (19, 55)}

# Le titre du plat favori, celui qu'on rejoue.
FAVORITE_NAME = {"fr": "Mon petit-dejeuner", "en": "My breakfast"}
FAVORITE_ID = "fav-breakfast"

# L'objectif qui court. Six chiffres coherents entre eux :
# 145x4 + 258x4 + 60x9 = 2152 kcal, soit la cible a trois kcal pres.
GOAL = {
    "kcal": 2150.0,
    "protein": 145.0,
    "carbs": 258.0,
    "sugars": 54.0,
    "fat": 60.0,
    "fiber": 30.0,
}

PROFILE = {
    "birthDate": "1991-04-12",
    "sex": "MALE",
    "heightCm": 178.0,
    "activityLevel": "MODERATE",
    "unitSystem": "METRIC",
}

HISTORY_DAYS = 49  # sept semaines : de quoi remplir le calendrier deplie
WEIGHT_WEEKS = 11
# Lundi, mercredi, vendredi, dimanche : les jours ou la balance sert.
WEIGH_IN_DAYS = {0, 2, 4, 6}

ATTRIBUTION = {
    "openFoodFacts": "Contient des donnees d'Open Food Facts, sous licence ODbL 1.0",
    "ciqual": "Table CIQUAL 2025 - ANSES, Licence Ouverte Etalab 2.0",
}


def catalogue(language: str) -> dict[str, dict]:
    """Les aliments, lus dans la base embarquee du depot.

    `COALESCE` sur les colonnes `_est` parce que l'application affiche la teneur
    completee quand la mesure manque, et qu'une capture doit montrer ce qu'elle
    affiche. Une valeur qui reste nulle le reste : un trou est une donnee.
    """
    db = sqlite3.connect(CIQUAL)
    db.text_factory = lambda raw: raw.decode("utf-8", "replace")
    placeholders = ",".join("?" for _ in CODES)
    rows = db.execute(
        f"""SELECT n.code, COALESCE(n.short_name, n.name),
                   COALESCE(f.kcal_100, f.kcal_100_est),
                   COALESCE(f.protein_100, f.protein_100_est),
                   COALESCE(f.carb_100, f.carb_100_est),
                   COALESCE(f.sugar_100, f.sugar_100_est),
                   COALESCE(f.fat_100, f.fat_100_est),
                   COALESCE(f.fiber_100, f.fiber_100_est)
            FROM ciqual_name n JOIN ciqual_food f ON f.code = n.code
            WHERE n.language = ? AND n.code IN ({placeholders})""",
        [language, *CODES],
    ).fetchall()
    db.close()

    found = {
        code: {
            "id": f"ciqual-{code}",
            "source": "CIQUAL",
            "sourceRef": code,
            "name": name,
            "brand": None,
            "per100g": {
                "kcal": kcal, "protein": protein, "carbs": carbs,
                "sugars": sugars, "fat": fat, "fiber": fiber,
            },
            "defaultServingG": None,
        }
        for code, name, kcal, protein, carbs, sugars, fat, fiber in rows
    }
    missing = [code for code in CODES if code not in found]
    if missing:
        raise SystemExit(f"Codes CIQUAL absents de la base : {missing}")

    found[SCANNED["id"]] = {**SCANNED, "name": SCANNED["name"][language]}
    return found


def instant(day: date, hour: int, minute: int) -> str:
    """L'heure locale voulue, ecrite en instant UTC comme le format l'attend."""
    local = datetime(day.year, day.month, day.day, hour, minute, tzinfo=timezone.utc)
    return (local - timedelta(hours=UTC_OFFSET_HOURS)).isoformat().replace("+00:00", "Z")


def macros_of(food: dict, grams: float) -> dict[str, float | None]:
    """Les teneurs d'une ligne, mises a l'echelle depuis les 100 g.

    Une teneur inconnue reste inconnue : la mettre a zero ferait passer un trou
    pour une mesure, ce que le projet refuse partout ailleurs.
    """
    factor = grams / 100.0
    return {
        key: (None if value is None else round(value * factor, 2))
        for key, value in food["per100g"].items()
    }


def search_key(raw: str) -> str:
    """La normalisation du nom d'un plat favori, celle de SearchText.

    Recopiee et non partagee, parce que rien ici ne tourne sur la JVM. Elle ne
    sert qu'a l'index d'unicite du favori ; l'application la refait a sa facon
    des qu'elle ecrit.
    """
    expanded = raw.lower().replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
    stripped = "".join(c for c in unicodedata.normalize("NFD", expanded) if not unicodedata.combining(c))
    return " ".join("".join(c if c.isalnum() else " " for c in stripped).split())


def build(language: str, today: date) -> dict:
    """Le journal complet : profil, objectif, poids, plats, lignes, catalogue."""
    foods = catalogue(language)
    rng = random.Random(20260927)  # deterministe : deux tirs donnent la meme fiche

    dishes: list[dict] = []
    entries: list[dict] = []
    usage: dict[str, list[int]] = {}

    def log(day: date, moment: str, template: list, source: str, index: int, scale: float) -> str:
        hour, minute = HOURS[moment]
        dish_id = f"dish-{day.isoformat()}-{moment.lower()}"
        dishes.append({
            "id": dish_id,
            "date": day.isoformat(),
            "source": source,
            "loggedAt": instant(day, hour, minute),
            # Le lien vers le favori rejoue, quand le plat en vient un.
            #
            # **Il avait ete coupe ici**, parce que `RoomSnapshotStore.replace`
            # inserait les plats avant les favoris alors que `dish.favorite_id` est
            # une cle etrangere : l'archive entiere etait refusee, sur « La
            # restauration a echoue » et rien de plus. L'ordre est corrige et le cas
            # est couvert par `BackupRoundTripTest` ; le lien peut revenir, et il
            # eprouve le correctif a chaque semis.
            "favoriteId": FAVORITE_ID if source == "FAVORITE" else None,
            "title": None,   # deduit de l'heure : c'est le comportement par defaut (D118)
            "moment": moment,
        })
        for position, (code, base_grams) in enumerate(template):
            grams = round(base_grams * scale / 5.0) * 5.0 or 5.0
            food = foods[code]
            macros = macros_of(food, grams)
            entries.append({
                "id": f"entry-{dish_id}-{position}",
                "dishId": dish_id,
                "foodId": food["id"],
                "displayName": food["name"],
                "quantity": grams,
                "unit": "g",
                "grams": grams,
                "kcal": macros["kcal"] or 0.0,
                "protein": macros["protein"],
                "carbs": macros["carbs"],
                "sugars": macros["sugars"],
                "fat": macros["fat"],
                "fiber": macros["fiber"],
            })
            usage.setdefault(code, []).append(index)
        return dish_id

    # La journee visible, a l'echelle 1. Le diner manque, et c'est le sujet de
    # l'ecran -- « ce qu'il reste a manger ».
    for moment, source, _, template in TODAY:
        log(today, moment, template, source, 0, 1.0)

    # L'historique, du plus recent au plus ancien.
    for offset in range(1, HISTORY_DAYS + 1):
        day = today - timedelta(days=offset)
        scale = rng.uniform(0.88, 1.08)
        log(day, "BREAKFAST", rng.choice(BREAKFASTS), rng.choice(BREAKFAST_SOURCES), offset, scale)
        log(day, "LUNCH", rng.choice(LUNCHES), rng.choice(LUNCH_SOURCES), offset, scale)
        if rng.random() < 0.55:
            log(day, "SNACK", rng.choice(SNACKS), rng.choice(SNACK_SOURCES), offset, scale)
        log(day, "DINNER", rng.choice(DINNERS), rng.choice(DINNER_SOURCES), offset, scale)

    # Le catalogue ne porte que ce qui a servi, comme une vraie base locale : un
    # aliment n'y entre que le jour ou on l'a choisi.
    catalogue_rows = []
    for code, offsets in usage.items():
        food = foods[code]
        last = today - timedelta(days=min(offsets))
        catalogue_rows.append({
            "id": food["id"],
            "source": food["source"],
            "sourceRef": food["sourceRef"],
            "name": food["name"],
            "brand": food["brand"],
            **food["per100g"],
            "defaultServingG": food["defaultServingG"],
            "isLiquid": None,
            "fetchedAt": instant(today - timedelta(days=HISTORY_DAYS), 9, 0),
            "lastUsedAt": instant(last, 12, 45),
            "useCount": len(offsets),
            "favorite": code in {"32140", "26038", SCANNED["id"]},
        })

    # Le poids : une trajectoire qui descend, avec le bruit d'une vraie balance.
    #
    # **Quatre pesees par semaine et non une.** L'ecran ne trace la courbe lissee
    # qu'a partir de trois mesures dans la meme semaine, et sans elle il ne reste
    # que des points gris : le tir montrerait la fonctionnalite eteinte. Se peser
    # plusieurs fois par semaine est de toute facon ce que l'application demande,
    # puisque c'est de la tendance que l'ajustement hebdomadaire se derive.
    weights = []
    start = 82.4
    for day_offset in range(WEIGHT_WEEKS * 7):
        day = today - timedelta(days=WEIGHT_WEEKS * 7 - 1 - day_offset)
        if day.weekday() not in WEIGH_IN_DAYS:
            continue
        trend = start - day_offset * (2.9 / (WEIGHT_WEEKS * 7))
        weights.append({"date": day.isoformat(), "weightKg": round(trend + rng.uniform(-0.4, 0.4), 1)})

    goal_start = today - timedelta(weeks=10)
    favorite_components = [
        {
            "foodId": foods[code]["id"],
            "name": foods[code]["name"],
            "quantity": float(grams),
            "unit": "g",
            "grams": float(grams),
            **macros_of(foods[code], grams),
        }
        for code, grams in BREAKFASTS[0]
    ]

    return {
        "formatVersion": FORMAT_VERSION,
        "appVersion": APP_VERSION,
        "exportedAt": instant(today, 9, 0),
        "attribution": ATTRIBUTION,
        "profile": PROFILE,
        "goals": [{
            "id": "goal-current",
            "startedAt": goal_start.isoformat(),
            "endedAt": None,
            "origin": "CALCULATED",
            "strategy": "LOSE",
            "targetWeightKg": 76.0,
            "targetDate": (today + timedelta(weeks=14)).isoformat(),
            **GOAL,
        }],
        "weights": weights,
        "dishes": dishes,
        "entries": entries,
        "foods": catalogue_rows,
        "favorites": [{
            "id": FAVORITE_ID,
            "name": FAVORITE_NAME[language],
            "useCount": 14,
            "components": favorite_components,
        }],
        "adjustment": {"enabled": True, "lastAcceptedOn": None, "lastIgnoredOn": None},
    }


def write_archive(snapshot: dict, destination: Path, photos: dict[str, Path]) -> None:
    """Le zip que l'application sait relire : le journal d'abord, les photos apres.

    L'ordre n'est pas cosmetique : ZipSnapshotArchive lit la premiere entree pour
    refuser un fichier trop recent avant d'ecrire la moindre image.
    """
    journal = gzip.compress(json.dumps(snapshot, ensure_ascii=False, indent=2).encode("utf-8"))
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("hexavore.json.gz", journal)
        for dish_id, source in photos.items():
            archive.write(source, f"photos/{dish_id}.jpg")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--today", required=True, help="La date que l'appareil affichera (AAAA-MM-JJ)")
    parser.add_argument("--out", required=True, type=Path, help="Dossier de sortie")
    parser.add_argument(
        "--photos",
        type=Path,
        default=Path(__file__).resolve().parent / "photos",
        help="Le dossier des photos de repas. Chaque entree de TODAY qui en nomme une "
             "y cherche <nom>.jpg. Absente, le plat garde son origine « photo » mais "
             "n'affiche aucune vignette.",
    )
    options = parser.parse_args()
    today = date.fromisoformat(options.today)

    photos: dict[str, Path] = {}
    for moment, _, name, _ in TODAY:
        if not name:
            continue
        source = options.photos / f"{name}.jpg"
        if not source.is_file():
            raise SystemExit(f"Photo introuvable : {source}")
        photos[f"dish-{today.isoformat()}-{moment.lower()}"] = source

    for language in ("fr", "en"):
        snapshot = build(language, today)
        destination = options.out / f"hexavore-demo-{language}.zip"
        write_archive(snapshot, destination, photos)
        print(
            f"{destination.name} : {len(snapshot['dishes'])} plats, "
            f"{len(snapshot['entries'])} lignes, {len(snapshot['foods'])} aliments, "
            f"{len(snapshot['weights'])} pesees, {len(photos)} photo(s)"
        )
        if language == "fr":
            summarise(snapshot, today)


def summarise(snapshot: dict, today: date) -> None:
    """Ce que la journee visible donnera a l'ecran.

    Imprime a chaque generation, parce que c'est le seul endroit ou une erreur de
    portion se voit avant d'avoir refait tout un tir : un quartier a 8 % ou un
    autre qui deborde ne se rattrape qu'en revenant ici.
    """
    kept = {d["id"] for d in snapshot["dishes"] if d["date"] == today.isoformat()}
    goal = snapshot["goals"][0]
    totals = dict.fromkeys(("kcal", "protein", "carbs", "sugars", "fat", "fiber"), 0.0)
    for entry in snapshot["entries"]:
        if entry["dishId"] in kept:
            for key in totals:
                totals[key] += entry[key] or 0.0
    print("   journee visible :")
    for key, value in totals.items():
        print(f"     {key:8} {value:7.1f} / {goal[key]:6.0f}  {100 * value / goal[key]:5.1f} %")
    print(f"     reste    {goal['kcal'] - totals['kcal']:7.0f} kcal")

    # **Cinq contributeurs aux fibres, pas six.** La capture 2 ouvre la bulle de
    # ce quartier, et `shoot.py` la recale d'un nombre de pixels calibre pour
    # cinq lignes : au-dela, la bulle deborde d'un ecran 16:9, qui est plus court
    # que n'importe quel telephone. Un sixieme aliment fibreux casse le cadrage
    # sans casser le tir, donc rien ne le signalerait ici.
    givers = {
        entry["displayName"]
        for entry in snapshot["entries"]
        if entry["dishId"] in kept and (entry["fiber"] or 0) > 0
    }
    verdict = "ok" if len(givers) <= 5 else "TROP -- la bulle de la capture 2 va deborder"
    print(f"     fibres : {len(givers)} aliment(s) contributeur(s), {verdict}")


if __name__ == "__main__":
    main()
