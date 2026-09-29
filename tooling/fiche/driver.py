# -*- coding: utf-8 -*-
"""Piloter l'emulateur par adb : trouver un element, le toucher, capturer.

**Par l'arbre d'accessibilite, jamais par des coordonnees ecrites a la main.**
Une capture pilotee au pixel se casse au premier changement de marge, et se
casse en silence : elle rend une image, simplement pas la bonne. Ici, chaque
geste vise un `content-desc` ou un texte, c'est-a-dire ce que l'application
declare d'elle-meme -- et si l'etiquette disparait, le tir s'arrete avec le nom
de ce qu'il cherchait.

Effet de bord voulu : une capture qui echoue signale une regression
d'accessibilite. On ne peut pas photographier ce qu'un lecteur d'ecran ne sait
pas nommer.

**Sous Git Bash, `MSYS_NO_PATHCONV` n'est pas une precaution.** Sans lui, un
chemin distant `/data/local/tmp` part vers adb en `C:/Program Files/Git/data/...`,
et `push` echoue sur un message qui parle du mauvais fichier.
"""

from __future__ import annotations

import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ADB = os.environ.get(
    "ADB",
    str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"),
)
PACKAGE = "app.hexavore.debug"
ACTIVITY = f"{PACKAGE}/app.hexavore.MainActivity"

_ENV = {**os.environ, "MSYS_NO_PATHCONV": "1", "MSYS2_ARG_CONV_EXCL": "*"}
_BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")


@dataclass(frozen=True)
class Node:
    text: str
    desc: str
    bounds: tuple[int, int, int, int]

    @property
    def centre(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return (left + right) // 2, (top + bottom) // 2

    def __str__(self) -> str:
        return f"{self.desc or self.text!r} @ {self.centre}"


class NotFound(LookupError):
    """L'element cherche n'est pas a l'ecran. Le message porte ce qu'on cherchait."""


class Driver:
    def __init__(self, serial: str | None = None) -> None:
        self.prefix = [ADB] + (["-s", serial] if serial else [])

    # --- adb brut -------------------------------------------------------------

    def run(self, *args: str, binary: bool = False) -> bytes | str:
        result = subprocess.run(
            self.prefix + list(args), capture_output=True, check=True, env=_ENV
        )
        return result.stdout if binary else result.stdout.decode("utf-8", "replace")

    def shell(self, command: str) -> str:
        return self.run("shell", command)

    # --- lecture de l'ecran ---------------------------------------------------

    def tree(self) -> list[Node]:
        """L'arbre d'accessibilite courant, aplati.

        `uiautomator dump` ecrit sur le disque de l'appareil puis on relit : c'est
        plus lent qu'un flux, mais `dump /dev/tty` melange parfois sa sortie aux
        messages du service, et un XML tronque se manifeste bien plus loin.
        """
        self.shell("uiautomator dump /sdcard/window_dump.xml")
        raw = self.run("exec-out", "cat", "/sdcard/window_dump.xml", binary=True)
        nodes: list[Node] = []
        for element in ET.fromstring(raw.decode("utf-8", "replace")).iter("node"):
            match = _BOUNDS.match(element.get("bounds", ""))
            if not match:
                continue
            nodes.append(
                Node(
                    text=element.get("text", ""),
                    desc=element.get("content-desc", ""),
                    bounds=tuple(int(g) for g in match.groups()),  # type: ignore[arg-type]
                )
            )
        return nodes

    def find(self, needle: str, *, exact: bool = False, index: int = 0) -> Node:
        """Le n-ieme element dont le texte ou la description porte `needle`."""
        matches = [n for n in self.tree() if _matches(n, needle, exact)]
        if len(matches) <= index:
            raise NotFound(f"« {needle} » absent de l'ecran ({len(matches)} trouve(s))")
        return matches[index]

    def present(self, needle: str, *, exact: bool = False) -> bool:
        return any(_matches(n, needle, exact) for n in self.tree())

    def wait(self, needle: str, *, exact: bool = False, timeout: float = 12.0) -> Node:
        deadline = time.monotonic() + timeout
        last: Exception | None = None
        while time.monotonic() < deadline:
            try:
                return self.find(needle, exact=exact)
            except NotFound as error:
                last = error
                time.sleep(0.4)
        raise NotFound(f"« {needle} » toujours absent apres {timeout:.0f} s") from last

    # --- gestes ---------------------------------------------------------------

    def tap(self, target: str | Node, *, exact: bool = False, index: int = 0, settle: float = 0.7) -> None:
        node = target if isinstance(target, Node) else self.find(target, exact=exact, index=index)
        x, y = node.centre
        self.shell(f"input tap {x} {y}")
        time.sleep(settle)

    def tap_at(self, x: int, y: int, *, settle: float = 0.7) -> None:
        self.shell(f"input tap {x} {y}")
        time.sleep(settle)

    def back(self, *, settle: float = 0.7) -> None:
        self.shell("input keyevent KEYCODE_BACK")
        time.sleep(settle)

    def swipe(self, x1: int, y1: int, x2: int, y2: int, ms: int = 300, *, settle: float = 0.7) -> None:
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")
        time.sleep(settle)

    def drag(self, distance: int, *, x: int = 120, ms: int = 900, settle: float = 1.0) -> None:
        """Faire defiler de `distance` pixels, vers le bas si positif.

        **Un glissement lent, et loin du centre.** Un `swipe` rapide est traite
        comme une lancee, dont l'inertie depend du moment ou le systeme echantillonne
        le geste : deux appels identiques ne rendent pas le meme decalage. Et au
        centre de l'ecran, l'hexagone intercepte le geste -- il a ses propres
        zones tactiles -- si bien que la page ne bouge pas du tout.
        """
        start = 1500 if distance > 0 else 700
        self.swipe(x, start, x, start - distance, ms, settle=settle)

    def bring_into_view(self, needle: str, *, low: int = 700, high: int = 1500, tries: int = 8) -> Node:
        """Amener un element dans une bande sure de l'ecran, puis le rendre.

        Une cible collee au bord bas chevauche la barre de navigation gestuelle :
        l'appui part au systeme et non a l'application, sans que rien ne le dise.
        """
        for _ in range(tries):
            try:
                node = self.find(needle)
            except NotFound:
                # Pas encore dans l'arbre : il est sous le pli, on descend a l'aveugle.
                self.drag(400)
                continue
            centre = node.centre[1]
            if low <= centre <= high:
                return node
            self.drag(int(centre - (low + high) // 2))
        raise NotFound(f"« {needle} » ne vient pas dans la bande {low}-{high}")

    def scroll_to_end(self, *, tries: int = 6) -> None:
        """Descendre jusqu'a ce que l'ecran cesse de changer.

        Viser le dernier element par [bring_into_view] ne marche pas pour lui :
        une fois la page au bout, il reste ou la page le laisse, et aucune bande
        de hauteur ne peut etre exigee.
        """
        previous: list[tuple] = []
        for _ in range(tries):
            current = [(n.text, n.desc, n.bounds) for n in self.tree()]
            if current == previous:
                return
            previous = current
            self.drag(500)

    def type_text(self, text: str, *, settle: float = 0.6) -> None:
        self.shell(f"input text {text.replace(' ', '%s')}")
        time.sleep(settle)

    # --- cycle de vie ---------------------------------------------------------

    def launch(self, *, fresh: bool = False, settle: float = 3.0) -> None:
        if fresh:
            self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -n {ACTIVITY}")
        time.sleep(settle)

    def set_language(self, tag: str | None) -> None:
        """La langue de l'application, par la porte d'Android.

        Depuis Android 13 c'est `LocaleManager` qui detient cette langue, et
        `StoredLanguageSettings.restore` s'abstient d'y toucher quand elle en
        trouve une : la regler ici est donc equivalent a la choisir dans
        l'ecran Apparence, sans avoir a l'atteindre.
        """
        locales = tag or ""
        self.shell(f"cmd locale set-app-locales {PACKAGE} --locales {locales}")
        time.sleep(1.5)

    # --- capture --------------------------------------------------------------

    def capture(self, destination: Path) -> Path:
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(self.run("exec-out", "screencap", "-p", binary=True))
        return destination

    def prepare_screen(self, *, clock: str = "1200") -> None:
        """Le decor fixe de toutes les captures.

        Mode sombre, animations coupees -- une animation a mi-course produit une
        capture floue -- et la barre d'etat en mode demonstration : heure fixe,
        batterie pleine, aucune notification. C'est le mecanisme d'Android prevu
        pour cela, et non un trucage de l'image.
        """
        self.shell("cmd uimode night yes")
        for scale in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.shell(f"settings put global {scale} 0")
        self.shell("settings put global sysui_demo_allowed 1")
        demo = "am broadcast -a com.android.systemui.demo -e command"
        self.shell(f"{demo} enter")
        self.shell(f"{demo} clock -e hhmm {clock}")
        self.shell(f"{demo} battery -e level 100 -e plugged false")
        self.shell(f"{demo} network -e wifi show -e level 4")
        self.shell(f"{demo} network -e mobile hide")
        self.shell(f"{demo} notifications -e visible false")

    def release_screen(self) -> None:
        self.shell("am broadcast -a com.android.systemui.demo -e command exit")
        for scale in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.shell(f"settings put global {scale} 1")


def _matches(node: Node, needle: str, exact: bool) -> bool:
    haystacks = (node.text, node.desc)
    if exact:
        return needle in haystacks
    lowered = needle.lower()
    return any(lowered in h.lower() for h in haystacks if h)
