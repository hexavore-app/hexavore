# -*- coding: utf-8 -*-
"""Les chemins propres a Hexavore : semer, puis atteindre chaque ecran a capturer.

Separe de `driver.py`, qui ne sait qu'appuyer et regarder. Ici vivent les
libelles de l'application -- donc ce qui change quand un texte change.

**Les libelles du selecteur de documents ne sont pas ici.** DocumentsUI suit la
langue de l'**appareil**, que ce tir ne touche pas ; seule celle de
l'application bascule. Les deux jeux d'etiquettes n'ont donc pas la meme source,
et les melanger ferait echouer la moitie des tirs sans qu'on voie pourquoi.
"""

from __future__ import annotations

import time
from pathlib import Path

from driver import Driver, NotFound

# Les libelles de l'application, par langue.
LABELS = {
    "fr": {
        "settings": "Profil et objectifs",
        "backup": "Sauvegarde",
        "choose_file": "Choisir un fichier",
        "photos": "Photos des plats",
        "weight": "Journal de poids",
        "add": "Ajouter",
        "month": "Afficher le mois",
        # La barre des fibres et non celle des proteines : sa bulle ne liste que
        # cinq aliments, sans ligne « n autres », et c'est la seule qui tienne
        # entiere sous l'hexagone en 1080x1920. Voir shoot.py, ecran 2.
        "macro_bar": "Fibres",
        "dishes": "Goûter",
        # Ce qui doit etre a l'ecran avant qu'on declenche, ecran par ecran.
        "expect": ["Aujourd'hui", "Fibres", "Goûter", "Enregistrer", "Saisie manuelle", "2026", "Journal de poids", "Sauvegarde"],
    },
    "en": {
        "settings": "Profile and targets",
        "backup": "Backup",
        "choose_file": "Choose a file",
        "photos": "Dish photos",
        "weight": "Weight log",
        "add": "Add",
        "month": "Show the month",
        "macro_bar": "Fibre,",
        "dishes": "Snack",
        "expect": ["Today", "Fibre", "Snack", "Save", "Enter by hand", "2026", "Weight log", "Backup"],
    },
}

# DocumentsUI, toujours dans la langue de l'appareil.
PICKER_ROOTS = "Show roots"
PICKER_DOWNLOADS = "Downloads"
PICKER_LIST_VIEW = "List view"


def import_archive(d: Driver, language: str, file_name: str) -> None:
    """Semer par l'import de sauvegarde, c'est-a-dire par le code de l'application.

    Le selecteur s'ouvre sur « Recent », dont le contenu depend de l'indexation
    du MediaStore : on passe donc **toujours** par le tiroir des racines puis
    par Downloads, qui liste le dossier tel qu'il est. Un fichier pousse il y a
    deux secondes y figure, alors qu'il peut manquer des recents.
    """
    labels = LABELS[language]
    d.launch(fresh=True, settle=4)
    # **Attendre l'etiquette, pas un delai.** Changer la langue recree les activites,
    # et le premier appui partait parfois vers un ecran encore en francais : le tir
    # s'arretait sur « Profile and targets absent de l'ecran », ce qui est vrai mais
    # ne dit pas que c'est une question de moment.
    d.tap(d.wait(labels["settings"]))
    d.tap(labels["backup"], exact=True)
    d.tap(labels["choose_file"])          # la carte « Restaurer une sauvegarde »
    time.sleep(1.0)
    d.tap(labels["choose_file"])          # le bouton du dialogue de confirmation
    time.sleep(3.0)

    d.tap(PICKER_ROOTS, settle=2.0)
    # « Downloads » apparait deux fois quand le tiroir couvre la liste : celui du
    # tiroir est le dernier de l'arbre.
    roots = [n for n in d.tree() if n.text == PICKER_DOWNLOADS]
    if not roots:
        raise NotFound("Le tiroir des racines ne propose pas Downloads")
    d.tap(roots[-1], settle=2.5)
    if d.present(PICKER_LIST_VIEW):
        d.tap(PICKER_LIST_VIEW, settle=1.5)
    d.tap(file_name, settle=1.0)

    # La restauration ecrit toute la base en une transaction : on laisse le temps.
    time.sleep(4.0)


# Room ouvre en WAL : la base n'est complete qu'avec son journal. Les copier
# separement et n'en remettre qu'une donnerait un etat vieux de quelques
# ecritures, ou une base illisible.
DATABASE_FILES = ("hexavore.db", "hexavore.db-wal", "hexavore.db-shm")


def snapshot_data(d: Driver, folder: Path) -> Path:
    """Sortir la base ecrite par l'application, pour rejouer un tir sans le selecteur.

    **`exec-out run-as ... cat`, et non un `cp` vers `/data/local/tmp`.** `run-as`
    prend l'identite de l'application, qui n'a pas le droit d'ecrire dans le
    dossier du shell : le `cp` echoue sur un code de retour et rien d'autre. Et
    `exec-out` plutot que `shell`, parce que `shell` traduit les fins de ligne et
    rend une base « malformed » dont le message ne dit pas d'ou vient le mal.
    """
    d.shell(f"am force-stop {_PACKAGE}")
    time.sleep(1.5)
    folder.mkdir(parents=True, exist_ok=True)
    present = d.shell(f"run-as {_PACKAGE} ls databases")
    for name in DATABASE_FILES:
        target = folder / name
        if name not in present:
            target.unlink(missing_ok=True)
            continue
        target.write_bytes(d.run("exec-out", "run-as", _PACKAGE, "cat", f"databases/{name}", binary=True))
    return folder


def restore_data(d: Driver, folder: Path) -> None:
    """Reposer une base deja semee, journal compris."""
    d.shell(f"am force-stop {_PACKAGE}")
    d.shell(f"run-as {_PACKAGE} sh -c 'rm -f databases/hexavore.db*'")
    for name in DATABASE_FILES:
        source = folder / name
        if not source.is_file():
            continue
        d.run("push", str(source), f"/data/local/tmp/{name}")
        d.shell(f"run-as {_PACKAGE} sh -c 'cp /data/local/tmp/{name} databases/{name}'")


_PACKAGE = "app.hexavore.debug"
