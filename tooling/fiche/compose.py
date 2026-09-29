# -*- coding: utf-8 -*-
"""Les elements graphiques de la fiche Play Store, fabriques depuis les sources.

    python tooling/fiche/compose.py --work <dossier> --font <Roboto-Regular.ttf>

Produit, par langue : huit montages 1080x1920 (bandeau puis telephone) et une
image de presentation 1024x500. Et une fois : l'icone 512x512.

**Rien n'est recopie a la main.** Les six teintes viennent de `colors.xml`, la
geometrie de l'hexagone de `ic_launcher_foreground.xml`. Une icone redessinee
dans ce fichier aurait diverge de celle de l'application des la premiere
retouche, et personne ne l'aurait vu avant de comparer les deux sur un
telephone.

**Le format du Play Store est plus court qu'un telephone.** La Console exige du
16:9 ou du 9:16, alors que les appareils recents sont en 9:20. Le montage
resout cela sans rogner : le canevas est en 9:16, et la capture y tient entiere,
a l'echelle, sous sa phrase.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DESIGN = REPO / "core" / "designsystem" / "src" / "main" / "res"
COLORS = DESIGN / "values" / "colors.xml"
FOREGROUND = DESIGN / "drawable" / "ic_launcher_foreground.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

# Le canevas d'une capture montee, et le format que la Console accepte.
CANVAS = (1080, 1920)
# La capture, posee sous sa phrase. Sa largeur decoule de sa hauteur : un
# telephone garde ses proportions, sinon l'interface ment sur ses espacements.
SHOT_HEIGHT = 1500
SHOT_TOP = 350
CORNER = 44
CAPTION_TOP = 96
CAPTION_SIZE = 62
CAPTION_WIDTH = 880

FEATURE = (1024, 500)


def palette() -> dict[str, str]:
    """Les couleurs du design system, lues la ou elles sont definies."""
    tree = ET.parse(COLORS)
    return {
        node.get("name"): "#" + (node.text or "").strip().lstrip("#")[2:]
        for node in tree.getroot().findall("color")
        if (node.text or "").strip().startswith("#FF")
    }


def hexagon_svg(size: int, background: str | None) -> str:
    """L'hexagone de la marque, converti depuis le vecteur Android.

    Le `pathData` d'un VectorDrawable **est** de la syntaxe de chemin SVG : il
    n'y a donc rien a traduire, seulement a rebrancher les references de couleur
    sur les valeurs que `colors.xml` porte.
    """
    tints = palette()
    tree = ET.parse(FOREGROUND)
    root = tree.getroot()
    viewport = float(root.get(f"{ANDROID_NS}viewportWidth", "108"))

    paths = []
    for node in root.findall("path"):
        data = node.get(f"{ANDROID_NS}pathData")
        fill = node.get(f"{ANDROID_NS}fillColor", "")
        name = fill.removeprefix("@color/")
        colour = tints.get(name, "#FFFFFF")
        paths.append(f'<path d="{data}" fill="{colour}" />')

    plate = f'<rect width="{viewport}" height="{viewport}" fill="{background}" />' if background else ""
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 {viewport} {viewport}">{plate}{"".join(paths)}</svg>'
    )


def run(*args: str | Path) -> None:
    """Appeler ImageMagick, chemins en barres obliques.

    **Meme sous Windows.** ImageMagick lit `-font C:\\Users\\...` en avalant les
    contre-obliques, et se plaint alors d'une police « C:Userscharl... » que
    personne ne reconnait -- puis continue avec la police par defaut, sans
    echouer. Le piege est le meme que celui de `keystore.properties`.
    """
    normalised = [a.as_posix() if isinstance(a, Path) else a for a in args]
    subprocess.run(["magick", *normalised], check=True)


def icon(out: Path) -> Path:
    """L'icone 512x512 de la fiche : le fond plein, la figure dessus.

    **Sans transparence**, que la Console refuse, et sans masque arrondi : le
    Play Store applique le sien, et en arrondir un second donnerait un bord
    double sur les appareils qui recoupent.
    """
    tints = palette()
    source = out.parent / "icone-512.svg"
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text(hexagon_svg(512, tints["neon_background"]), encoding="utf-8")
    run(source, "-background", tints["neon_background"], "-alpha", "remove", "-alpha", "off", out)
    source.unlink()
    return out


def rounded_shot(shot: Path, target: Path, height: int) -> tuple[int, int]:
    """La capture, mise a l'echelle et aux coins arrondis, avec un lisere.

    Le lisere separe un ecran presque noir d'un fond presque noir. Sans lui, la
    capture flotte sans bord et on ne voit plus ou commence le telephone.
    """
    probe = subprocess.run(
        ["magick", "identify", "-format", "%w %h", str(shot)],
        capture_output=True, check=True, text=True,
    ).stdout.split()
    width = round(int(probe[0]) * height / int(probe[1]))
    edge = palette()["neon_outline"] if "neon_outline" in palette() else "#2A2A3A"

    run(
        shot, "-resize", f"{width}x{height}!",
        "(", "+clone", "-alpha", "transparent", "-background", "none",
        "-fill", "white", "-draw", f"roundrectangle 0,0,{width - 1},{height - 1},{CORNER},{CORNER}", ")",
        "-compose", "DstIn", "-composite",
        "-compose", "over",
        "-fill", "none", "-stroke", edge, "-strokewidth", "3",
        "-draw", f"roundrectangle 1,1,{width - 2},{height - 2},{CORNER},{CORNER}",
        target,
    )
    return width, height


def montage(shot: Path, caption: str, font: Path, out: Path, accent: str) -> Path:
    """Un bandeau de texte, puis la capture. Le format de la fiche.

    Le degrade et la lueur ne sont pas du decor : une capture presque noire
    posee sur un fond noir disparait dans la vignette de resultat de recherche,
    qui est l'endroit ou cette image travaille.
    """
    tints = palette()
    work = out.parent / f".{out.stem}"
    device = work.with_suffix(".device.png")
    width, height = rounded_shot(shot, device, SHOT_HEIGHT)
    left = (CANVAS[0] - width) // 2

    background = work.with_suffix(".bg.png")
    run(
        "-size", f"{CANVAS[0]}x{CANVAS[1]}",
        f"gradient:{tints['neon_surface_variant'] if 'neon_surface_variant' in tints else '#161622'}-{tints['neon_background']}",
        background,
    )

    glow = work.with_suffix(".glow.png")
    run(
        "-size", f"{CANVAS[0]}x{CANVAS[1]}", "xc:none",
        "-fill", accent, "-draw",
        f"roundrectangle {left - 6},{SHOT_TOP - 6},{left + width + 6},{SHOT_TOP + height + 6},{CORNER},{CORNER}",
        "-blur", "0x26", "-channel", "A", "-evaluate", "multiply", "0.32", "+channel",
        glow,
    )

    label = work.with_suffix(".caption.png")
    run(
        "-background", "none", "-fill", "#EDEDF5", "-font", font,
        "-pointsize", str(CAPTION_SIZE), "-size", f"{CAPTION_WIDTH}x190",
        "-gravity", "center", f"caption:{caption}",
        label,
    )

    run(
        background,
        glow, "-geometry", "+0+0", "-composite",
        device, "-geometry", f"+{left}+{SHOT_TOP}", "-composite",
        label, "-geometry", f"+{(CANVAS[0] - CAPTION_WIDTH) // 2}+{CAPTION_TOP}", "-composite",
        out,
    )
    for temporary in (device, background, glow, label):
        temporary.unlink(missing_ok=True)
    return out


def feature_graphic(tagline: str, font: Path, out: Path) -> Path:
    """L'image de presentation 1024x500 : la figure, le nom, une phrase.

    Le Play Store recadre cette image selon les surfaces et pose parfois ses
    propres elements dessus : rien d'essentiel ne touche les bords.
    """
    tints = palette()
    work = out.parent / f".{out.stem}"
    mark_svg = work.with_suffix(".mark.svg")
    mark = work.with_suffix(".mark.png")
    mark_svg.write_text(hexagon_svg(340, None), encoding="utf-8")
    run("-background", "none", mark_svg, "-resize", "340x340", mark)

    background = work.with_suffix(".bg.png")
    run("-size", f"{FEATURE[0]}x{FEATURE[1]}", f"gradient:#161622-{tints['neon_background']}", background)

    glow = work.with_suffix(".glow.png")
    run(
        "-size", f"{FEATURE[0]}x{FEATURE[1]}", "xc:none",
        "-fill", tints["neon_calories"], "-draw", "circle 250,250 250,110",
        "-blur", "0x60", "-channel", "A", "-evaluate", "multiply", "0.35", "+channel", glow,
    )

    name = work.with_suffix(".name.png")
    run(
        "-background", "none", "-fill", "#EDEDF5", "-font", font, "-pointsize", "92",
        f"label:Hexavore", name,
    )
    line = work.with_suffix(".line.png")
    run(
        "-background", "none", "-fill", "#9A9AB0", "-font", font, "-pointsize", "36",
        "-size", "520x140", "-gravity", "west", f"caption:{tagline}", line,
    )

    run(
        background,
        glow, "-geometry", "+0+0", "-composite",
        mark, "-geometry", "+80+80", "-composite",
        name, "-geometry", "+470+150", "-composite",
        line, "-geometry", "+474+260", "-composite",
        out,
    )
    for temporary in (mark_svg, mark, background, glow, name, line):
        temporary.unlink(missing_ok=True)
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work", required=True, type=Path)
    parser.add_argument("--font", required=True, type=Path)
    parser.add_argument("--captions", type=Path, default=Path(__file__).resolve().parent / "captions.json")
    options = parser.parse_args()

    captions = json.loads(options.captions.read_text(encoding="utf-8"))
    tints = palette()
    accents = [
        tints["neon_calories"], tints["neon_fiber"], tints["neon_protein"], tints["neon_calories"],
        tints["neon_carbs"], tints["neon_sugars"], tints["neon_calories"], tints["neon_fiber"],
    ]

    store = options.work / "store"
    store.mkdir(parents=True, exist_ok=True)
    print(icon(store / "icone-512.png"))

    for language, texts in captions.items():
        shots = sorted((options.work / "shots" / language).glob("*.png"))
        out = store / language
        out.mkdir(parents=True, exist_ok=True)
        for index, shot in enumerate(shots):
            caption = texts["captures"][index]
            print(montage(shot, caption, options.font, out / f"{shot.stem}.png", accents[index]))
        print(feature_graphic(texts["tagline"], options.font, out / "presentation-1024x500.png"))


if __name__ == "__main__":
    main()
