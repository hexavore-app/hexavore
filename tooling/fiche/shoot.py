# -*- coding: utf-8 -*-
"""Les huit captures de la fiche Play Store, dans une langue.

    python tooling/fiche/shoot.py --language fr --work <dossier> [--reseed]

`--reseed` refait le semis par l'import d'archive, puis fige la base obtenue.
Sans lui, la base figee est reposee telle quelle : le selecteur de documents
sort de la boucle, et un tir dure une minute au lieu de trois.

**L'ordre raconte l'application, il n'est pas l'ordre de navigation.** Le Play
Store ne montre que trois ou quatre vignettes dans ses resultats, et c'est
l'hexagone -- la figure qui donne son nom a l'application -- qui doit tenir la
premiere.
"""

from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from driver import Driver  # noqa: E402
from flows import LABELS, import_archive, restore_data, snapshot_data  # noqa: E402

LOCALES = {"fr": "fr-FR", "en": "en-GB"}

# Les lignes de la bulle d'un quartier : « 5,4 g », « 0,1 g ». L'en-tete dit
# « 12,7 g sur 30 g » et ne correspond pas, les barres de macro non plus.
BUBBLE_ROW = re.compile(r"^\d+(?:[.,]\d+)?\s*g$")

# Le recul de l'ecran de validation. Le formulaire est plus long que l'ecran et
# la barre Annuler/Enregistrer le recouvre : a cette position, la photo reste
# entiere et aucune valeur n'est tranchee en plein chiffre.
EDIT_SCROLL = 220

# Les sondes de recherche. Le francais montre au passage que « creme » trouve
# « creme brulee » : la recherche compare des formes sans accent des deux cotes
# (D49). L'anglais n'a pas de quoi le montrer -- les libelles de la table y sont
# sans diacritiques -- donc il montre simplement l'etendue du catalogue.
SEARCH_PROBE = {"fr": "creme", "en": "chicken"}

# Le plat ouvert pour montrer l'ecran de validation : celui du midi, celui qui
# vient d'une analyse photo.
LUNCH = {"fr": "Déjeuner", "en": "Lunch"}


def shoot(d: Driver, language: str, out: Path) -> list[Path]:
    labels = LABELS[language]
    shots: list[Path] = []

    def snap(name: str) -> None:
        """Capturer, mais seulement une fois l'ecran arrive.

        **Sans cette attente, un tir peut rendre un ecran noir sans echouer.**
        Un changement de langue recree les activites, et une capture prise
        pendant la recreation produit un fichier parfaitement valide, simplement
        vide. Rien dans la suite ne l'aurait signale : c'est en regardant les
        images qu'on l'a vu, ce qui est exactement ce qu'une chaine est censee
        eviter.

        Ce que la capture attend est ce que l'ecran **declare**, pas un seuil de
        pixels : un fond presque noir est legitime ici.
        """
        d.wait(labels["expect"][len(shots)])
        shots.append(d.capture(out / f"{len(shots) + 1:02d}-{name}.png"))

    # 1. L'accueil, hexagone rempli.
    d.launch(fresh=True, settle=5)
    snap("accueil")

    # 2. Un quartier touche : la bulle dit les aliments qui l'ont rempli.
    #
    #    La barre pleine largeur sert de cible plutot que le triangle : c'est celle
    #    que le lecteur d'ecran annonce, donc celle qui porte une etiquette stable.
    #
    #    **Les 200 pixels ne sont pas un tatonnement laisse la.** Quand la bulle
    #    s'ouvre, l'application recale la page sur l'hexagone, et a cette position
    #    la bulle deborde du bas d'un ecran 16:9 -- le format que la Console impose,
    #    plus court que n'importe quel telephone recent. Ce recul est le seul qui
    #    montre l'hexagone entier **et** la bulle entiere. Il se remesure si la
    #    figure ou la bulle changent de hauteur.
    d.tap(d.bring_into_view(labels["macro_bar"], low=900, high=1400), settle=1.6)
    fit_bubble(d)
    snap("quartier")
    d.launch(fresh=True, settle=4)

    # 3. La journee lue plat par plat, et les quatre boutons de saisie a droite.
    #
    #    **Jusqu'au bout de la page, et c'est la que la marge se voit.** La liste
    #    defile desormais au-dela des boutons flottants : au bout, les trois plats
    #    sont entierement lisibles, calories comprises. A mi-hauteur ils passent
    #    encore sous la colonne, ce qui est le comportement normal d'un bouton
    #    flottant -- ce qui ne l'etait pas, c'est qu'aucun defilement ne puisse les
    #    en degager.
    d.scroll_to_end()
    time.sleep(0.8)
    snap("journee")

    # 4. L'ecran de validation, atteint en ouvrant le plat du midi. C'est le meme
    #    ecran que celui ou aboutissent les quatre modes de saisie.
    d.tap(LUNCH[language], exact=True, settle=2.0)
    d.drag(EDIT_SCROLL, settle=1.2)
    snap("validation")
    d.back(settle=1.5)

    # 5. La recherche, hors ligne.
    d.launch(fresh=True, settle=4)
    d.tap(labels["add"], exact=True, settle=2.0)
    d.type_text(SEARCH_PROBE[language], settle=2.0)
    # Le clavier couvre la moitie basse : un retour arriere le referme sans
    # quitter l'ecran, et les resultats reprennent la place.
    d.back(settle=1.2)
    snap("recherche")
    d.back(settle=1.0)

    # 6. Le mois deplie : chaque jour porte l'atteinte de ses six objectifs.
    d.launch(fresh=True, settle=4)
    d.tap(labels["month"], settle=1.8)
    snap("mois")

    # 7. Le journal de poids et sa tendance.
    d.launch(fresh=True, settle=4)
    d.tap(labels["weight"], settle=2.0)
    snap("poids")

    # 8. Ce que l'application fait de vos donnees : un fichier, exportable et
    #    effacable, sur l'appareil et nulle part ailleurs.
    d.launch(fresh=True, settle=4)
    d.tap(labels["settings"], settle=1.5)
    d.tap(labels["backup"], exact=True, settle=1.8)
    snap("donnees")

    return shots


def fit_bubble(d: Driver, *, step: int = 100, tries: int = 12) -> int:
    """Descendre juste assez pour que la bulle tienne entiere, et pas plus.

    **Mesure plutot que constante.** Un recul ecrit en dur marchait tant que la
    bulle avait cinq lignes ; un sixieme aliment fibreux la faisait deborder
    sans que rien n'echoue. Ici on descend par petits pas tant que de nouvelles
    lignes apparaissent, et on s'arrete des qu'il n'en vient plus : c'est le
    plus petit recul qui montre tout, donc celui qui ampute le moins l'hexagone.

    Il faut ce compromis parce que la Console impose du 16:9, plus court que
    n'importe quel telephone recent : sur un vrai appareil, les deux tiennent.
    """
    seen, stable = -1, 0
    for _ in range(tries):
        rows = sum(1 for node in d.tree() if BUBBLE_ROW.match(node.text.strip()))
        # **Deux lectures stables, pas une.** Un pas ne revele pas toujours une
        # ligne entiere : s'arreter au premier compte inchange laisse la bulle
        # coupee au tiers, et le tir reussit quand meme.
        stable = stable + 1 if rows == seen else 0
        if rows and stable >= 2:
            return _lift(d, rows)
        seen = rows
        d.drag(step, settle=0.9)
    return seen


# La derniere ligne de la bulle doit affleurer ce bord, juste au-dessus de la
# barre de navigation gestuelle.
BUBBLE_FLOOR = 1855


def _lift(d: Driver, rows: int) -> int:
    """Remonter le trop-plein, une fois la bulle entiere connue.

    La boucle depasse d'un pas -- il en faut un de plus pour savoir qu'il n'y a
    plus rien a reveler -- et ce pas est pris sur le haut de l'hexagone. Une
    fois toutes les lignes a l'ecran, la plus basse donne le vrai bas de la
    bulle, donc de combien on peut redescendre la page sans rien perdre.
    """
    bottom = max(
        (node.bounds[3] for node in d.tree() if BUBBLE_ROW.match(node.text.strip())),
        default=0,
    )
    slack = BUBBLE_FLOOR - bottom
    if slack > 20:
        d.drag(-slack, settle=1.0)
    return rows


def silence_ai_notice(d: Driver, language: str) -> None:
    """Eteindre la pastille « aucune IA configuree ».

    C'est un reglage de l'application, pas un trucage : quelqu'un sans cle voit
    cette pastille, et quelqu'un qui n'en veut pas l'eteint ici. Une fiche Play
    Store n'a pas a s'ouvrir sur un point rouge qui signale un reste a faire.
    """
    labels = LABELS[language]
    notices = {"fr": "Notifications", "en": "Notifications"}[language]
    d.launch(fresh=True, settle=4)
    d.tap(labels["settings"], settle=1.5)
    d.tap(notices, exact=True, settle=1.5)
    row = d.find("IA" if language == "fr" else "No AI configured")
    # L'interrupteur est a droite de la ligne, hors de son propre libelle.
    d.tap_at(927, (row.bounds[1] + row.bounds[3]) // 2, settle=1.2)
    d.back(settle=0.8)
    d.back(settle=0.8)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", required=True, choices=sorted(LOCALES))
    parser.add_argument("--work", required=True, type=Path, help="Dossier de travail")
    parser.add_argument("--reseed", action="store_true", help="Refaire le semis par l'import")
    options = parser.parse_args()

    language = options.language
    seed = options.work / "seed" / f"base-{language}"
    out = options.work / "shots" / language
    out.mkdir(parents=True, exist_ok=True)

    d = Driver()
    d.prepare_screen()
    d.set_language(LOCALES[language])

    if options.reseed:
        import_archive(d, language, f"hexavore-demo-{language}.zip")
        snapshot_data(d, seed)
    else:
        restore_data(d, seed)

    silence_ai_notice(d, language)
    shots = shoot(d, language, out)
    for path in shots:
        print(f"  {path.name}  {path.stat().st_size // 1024} Ko")


if __name__ == "__main__":
    main()
