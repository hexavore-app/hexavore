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
        # La recherche s'ouvre par le menu du « + » de la barre du bas.
        "more_ways": "Autres façons d'ajouter",
        "by_hand": "Ajouter à la main",
        "search_field": "Chercher un aliment",
        # Les deux boutons de la barre du bas, qui dit ou s'arrete la page visible.
        "bar_buttons": ["Autres façons d'ajouter", "Photographier le repas"],
        "month": "Afficher le mois",
        # L'ecran d'analyse de la capture 6 (D180) : l'appareil photo de la barre du bas,
        # le bouton de la galerie dans le cadre, et le champ qui precise la photo.
        "shoot": "Photographier le repas",
        "pick": "Choisir une image",
        "note_field": "Un détail à préciser",
        # La barre des fibres et non celle des proteines : sa bulle ne liste que
        # cinq aliments, sans ligne « n autres », et c'est la seule qui tienne
        # entiere sous l'hexagone en 1080x1920. Voir shoot.py, ecran 2.
        "macro_bar": "Fibres",
        "dishes": "Goûter",
        # Les boutons de la visite guidee, dans l'ordre ou elle les montre.
        "tour": ["Passer", "Plus tard", "J'ai compris"],
        # Ce qui doit etre a l'ecran avant qu'on declenche, ecran par ecran.
        "expect": [
            "Aujourd'hui", "Fibres", "Goûter", "Enregistrer", "Saisie manuelle",
            "La photo de votre repas", "Journal de poids", "Sauvegarde",
        ],
    },
    "en": {
        "settings": "Profile and targets",
        "backup": "Backup",
        "choose_file": "Choose a file",
        "photos": "Dish photos",
        "weight": "Weight log",
        "more_ways": "Other ways to add",
        "by_hand": "Add by hand",
        "search_field": "Search for a food",
        "bar_buttons": ["Other ways to add", "Photograph the meal"],
        "month": "Show the month",
        "shoot": "Photograph the meal",
        "pick": "Choose an image",
        "note_field": "Anything to add",
        "macro_bar": "Fibre,",
        "dishes": "Snack",
        "tour": ["Skip", "Later", "Got it"],
        "expect": [
            "Today", "Fibre", "Snack", "Save", "Enter by hand",
            "The photo of your meal", "Weight log", "Backup",
        ],
    },
}

# DocumentsUI, toujours dans la langue de l'appareil.
PICKER_ROOTS = "Show roots"
PICKER_DOWNLOADS = "Downloads"
PICKER_LIST_VIEW = "List view"


def dismiss_tour(d: Driver, language: str) -> None:
    """Passer la visite guidee, comme quelqu'un qui la connait deja.

    Elle s'ouvre sur l'accueil d'une application qui ne l'a jamais montree, avec
    des repas d'exemple dans la journee, puis enchaine deux cartes sur la cle
    d'IA. Rien de cela n'a sa place dans une capture : on appuie sur ce qui la
    referme jusqu'a ce que plus rien ne le propose, deux lectures de suite.

    **Libelles exacts.** « Passer » se lit aussi dans des phrases, et une
    recherche par sous-chaine viserait la mauvaise.
    """
    buttons = LABELS[language]["tour"]
    d.launch(fresh=True, settle=4)
    quiet = 0
    for _ in range(12):
        nodes = d.tree()
        hit = next((n for label in buttons for n in nodes if label in (n.text, n.desc)), None)
        if hit is None:
            quiet += 1
            if quiet >= 2:
                return
            time.sleep(1.0)
            continue
        quiet = 0
        d.tap(hit, settle=1.2)
    raise NotFound("La visite guidee ne se referme pas")


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

    # Le selecteur met plus ou moins longtemps a s'ouvrir : on attend son tiroir.
    d.tap(d.wait(PICKER_ROOTS), settle=2.0)
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


# La photo de la capture 6 (D180) : le diner du jour visible, que la galerie de l'AVD
# n'a pas. Elle y est poussee, nommee pour se reconnaitre, puis annoncee au MediaStore :
# le selecteur de photos ne liste que ce qu'il a indexe.
ANALYSIS_PHOTO = Path(__file__).resolve().parent / "photos" / "steak.jpg"
ANALYSIS_PHOTO_ON_DEVICE = "/sdcard/Pictures/hexavore-fiche-steak.jpg"

# Ce qu'on ecrit sous la photo, en ASCII : `input text` ne sait pas taper un accent.
ANALYSIS_NOTE = {"fr": "steak de 200 g, sauce au poivre", "en": "a 200 g steak, pepper sauce"}


def push_analysis_photo(d: Driver) -> None:
    """Poser la photo du diner dans la galerie de l'appareil."""
    d.run("push", str(ANALYSIS_PHOTO), ANALYSIS_PHOTO_ON_DEVICE)
    d.shell(f"am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://{ANALYSIS_PHOTO_ON_DEVICE}")
    time.sleep(2.0)


def open_analysis(d: Driver, language: str) -> None:
    """L'ecran d'analyse, une assiette choisie dans la galerie et un mot pour la preciser.

    **Par l'appareil photo de la barre, faute d'autre chemin** : c'est le seul bouton qui
    ouvre l'ecran d'analyse, et il declenche l'appareil du systeme dans la foulee
    (D131). On le referme -- le cadre reste alors vide, et c'est un ecran utilisable --,
    puis on passe par la galerie. Rien ne part : la capture montre ce qu'on envoie, pas
    ce qu'un modele rend.

    La permission de la camera est accordee d'avance, comme celle des notifications est
    refusee d'avance : une boite systeme se poserait sur l'ecran, ou volerait l'appui.
    """
    labels = LABELS[language]
    d.shell(f"pm grant {_PACKAGE} android.permission.CAMERA")
    d.tap(d.wait(labels["shoot"]), settle=3.0)
    # L'appareil du systeme, et les boites de son premier lancement : on recule jusqu'a
    # retrouver le cadre vide.
    for _ in range(4):
        if d.present(labels["pick"]):
            break
        d.back(settle=1.5)
    d.tap(d.wait(labels["pick"]), settle=3.0)
    pick_latest_photo(d)
    d.tap(d.wait(labels["note_field"]), settle=0.8)
    d.type_text(ANALYSIS_NOTE[language], settle=1.0)
    d.hide_keyboard()


def pick_latest_photo(d: Driver, timeout: float = 12.0) -> None:
    """Toucher la premiere photo du selecteur de photos du systeme.

    Il est dans la langue de l'appareil, comme DocumentsUI, et chaque vignette s'y annonce
    « Photo taken on ... ». La plus recente vient en tete : c'est celle qu'on vient de
    pousser.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        photos = [n for n in d.tree() if n.desc.startswith("Photo")]
        if photos:
            d.tap(photos[0], settle=3.0)
            return
        time.sleep(1.0)
    raise NotFound("Le selecteur de photos ne montre aucune photo")
