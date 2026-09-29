# -*- coding: utf-8 -*-
"""Une base juste assez remplie pour que l'application s'ouvre sur l'accueil.

**Pourquoi ce detour.** L'application ouvre sur l'onboarding tant qu'aucun
objectif ne court (D56), et l'onboarding n'a pas de sortie vers les reglages :
on ne peut donc pas atteindre l'import de sauvegarde sans avoir d'abord repondu
aux cinq questions, dont une par selecteur de date. Automatiser ce selecteur
serait le maillon le plus fragile de toute la chaine de capture.

**Ce que ce fichier ecrit, et ce qu'il n'ecrit pas.** Deux lignes : un profil et
un objectif. Rien du journal, rien du catalogue. Tout le contenu visible sur les
captures passe ensuite par l'import d'archive, c'est-a-dire par le code de
l'application. Un ecrit direct en base peut produire un etat que l'application
n'aurait pas pu produire ; ces deux lignes-la sont assez simples pour qu'on
puisse l'affirmer, et elles sont de toute facon ecrasees par l'import.

**Le schema n'est pas recopie.** Il est lu dans `core/database/schemas`, que Room
exporte a chaque compilation : une migration future fait donc suivre ce script
sans qu'on y pense, et l'empreinte d'identite avec.

Usage :
    python tooling/fiche/bootstrap_db.py --out <chemin>/hexavore.db
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import time
from datetime import date, timedelta
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCHEMAS = REPO / "core" / "database" / "schemas" / "app.hexavore.core.database.HexavoreDatabase"

# ProfileEntity.SINGLETON et GoalEntity.ACTIVE : la ligne unique du profil, et la
# valeur d'`active_key` d'un objectif qui court (D55).
SINGLETON = "singleton"
ACTIVE = "1"


def latest_schema() -> dict:
    """Le schema le plus recent qu'ait exporte Room."""
    versions = sorted((int(p.stem), p) for p in SCHEMAS.glob("*.json"))
    if not versions:
        raise SystemExit(f"Aucun schema exporte dans {SCHEMAS}")
    return json.loads(versions[-1][1].read_text(encoding="utf-8"))["database"]


def build(destination: Path) -> None:
    schema = latest_schema()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.unlink(missing_ok=True)

    db = sqlite3.connect(destination)
    for entity in schema["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

    # La table que Room interroge a l'ouverture. Une empreinte qui ne correspond
    # pas fait echouer le demarrage avec un message parlant de migration.
    db.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    db.execute(
        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
        (schema["identityHash"],),
    )
    db.execute(f"PRAGMA user_version = {schema['version']}")

    now = int(time.time() * 1000)
    today = date.today()
    db.execute(
        """INSERT INTO profile (id, birth_date, sex, height_cm, activity_level, unit_system,
                                created_at, updated_at)
           VALUES (?, '1991-04-12', 'MALE', 178.0, 'MODERATE', 'METRIC', ?, ?)""",
        (SINGLETON, now, now),
    )
    db.execute(
        """INSERT INTO goal (id, started_at, ended_at, active_key, origin, strategy,
                             target_weight_kg, target_date, kcal, protein_g, carb_g,
                             sugar_g, fat_g, fiber_g, created_at)
           VALUES ('goal-bootstrap', ?, NULL, ?, 'CALCULATED', 'LOSE', 76.0, ?,
                   2150.0, 145.0, 258.0, 54.0, 60.0, 30.0, ?)""",
        ((today - timedelta(weeks=10)).isoformat(), ACTIVE, (today + timedelta(weeks=14)).isoformat(), now),
    )
    db.commit()
    db.close()
    print(f"{destination} : schema v{schema['version']}, empreinte {schema['identityHash']}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    build(parser.parse_args().out)


if __name__ == "__main__":
    main()
