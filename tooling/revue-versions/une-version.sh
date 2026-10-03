#!/usr/bin/env bash
# Un tour de l'application sur une version d'Android, du démarrage au verdict.
#
# Usage : une-version.sh <nom-d-avd> [port]
set -u

AVD="${1:?Il faut un nom d’AVD : « emulator -list-avds » les donne.}"
PORT="${2:-5554}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"
RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP="$RACINE/app/build/outputs/apk/debug/app-debug.apk"
TST="$RACINE/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SORTIE="${SORTIE:-$RACINE/build/revue-versions}"
DEV="emulator-$PORT"
OUT="$SORTIE/$AVD.txt"

[ -x "$ADB" ] || ADB="$ADB.exe"
EMU="emulator"
[ -x "$SDK/emulator/$EMU" ] || EMU="emulator.exe"
mkdir -p "$SORTIE"

for apk in "$APP" "$TST"; do
  [ -f "$apk" ] || { echo "Absent : $apk — lancez d'abord ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest"; exit 1; }
done

# La table rase, et on l'attend vraiment. Un émulateur qui s'éteint garde le port
# quelques secondes de plus : la version suivante s'y rebranchait alors sans le
# savoir, et le rapport portait le nom d'un AVD sur les résultats d'un autre.
table_rase() {
  for d in $("$ADB" devices | grep emulator | cut -f1); do "$ADB" -s "$d" emu kill > /dev/null 2>&1; done
  for _ in $(seq 1 20); do
    [ -z "$("$ADB" devices | grep emulator)" ] && break
    sleep 3
  done

  # Et le processus, car il survit a son propre arret. Un emulateur disparu de
  # « adb devices » peut reprendre le port quelques secondes plus tard : la
  # version suivante s'y branchait alors sans rien remarquer.
  if command -v taskkill > /dev/null 2>&1; then
    taskkill //F //IM qemu-system-x86_64.exe > /dev/null 2>&1
    taskkill //F //IM emulator.exe > /dev/null 2>&1
  else
    pkill -f qemu-system > /dev/null 2>&1
    pkill -f "emulator -avd" > /dev/null 2>&1
  fi
  sleep 8

  # Le serveur adb garde en memoire des appareils qui ne repondent plus.
  "$ADB" kill-server > /dev/null 2>&1
  "$ADB" start-server > /dev/null 2>&1
  sleep 2
}

table_rase

# Depuis son propre répertoire : sous Windows, l'émulateur y cherche ses DLL.
(cd "$SDK/emulator" && "./$EMU" -avd "$AVD" -port "$PORT" -no-boot-anim -no-snapshot-save -no-audio > /dev/null 2>&1) &

for _ in $(seq 1 150); do
  [ "$("$ADB" -s "$DEV" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done

REL=$("$ADB" -s "$DEV" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
API=$("$ADB" -s "$DEV" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
NOM=$("$ADB" -s "$DEV" emu avd name 2>/dev/null | head -1 | tr -d '\r')
echo "=== $AVD — Android $REL (API $API) ===" > "$OUT"

if [ -z "$API" ]; then
  echo "L'émulateur n'a pas démarré." >> "$OUT"
  table_rase
  cat "$OUT"; exit 1
fi

# Celui qui a démarré est-il bien celui qu'on demandait ? Sans cette vérification,
# un émulateur survivant se faisait passer pour la version suivante, et le rapport
# déclarait Android 11 vert sur une image Android 12.
if [ "$NOM" != "$AVD" ]; then
  echo "L'émulateur en place est $NOM, pas $AVD : rapport abandonné." >> "$OUT"
  table_rase
  cat "$OUT"; exit 1
fi

# Les boîtes du système passent devant tout : sur certaines images, un service de
# la ROM plante en boucle et son « l'application s'est arrêtée » couvre l'écran.
# Ce n'est pas l'application qu'on essaie.
"$ADB" -s "$DEV" shell settings put global hide_error_dialogs 1 2>/dev/null
"$ADB" -s "$DEV" shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
for echelle in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" -s "$DEV" shell settings put global "$echelle" 0 2>/dev/null
done

# Désinstaller d'abord : chaque version doit voir une première installation, qui
# est ce qu'un utilisateur rencontre.
"$ADB" -s "$DEV" uninstall app.hexavore.debug > /dev/null 2>&1
"$ADB" -s "$DEV" uninstall app.hexavore.debug.test > /dev/null 2>&1
"$ADB" -s "$DEV" install -r -g "$APP" >> "$OUT" 2>&1
"$ADB" -s "$DEV" install -r -g "$TST" >> "$OUT" 2>&1
"$ADB" -s "$DEV" logcat -c 2>/dev/null

RUNNER="app.hexavore.debug.test/androidx.test.runner.AndroidJUnitRunner"

# L'onboarding d'abord : il n'existe que tant qu'aucun objectif n'est écrit, et
# AppJourneyTest en écrit un avant chacun de ses tests.
echo "--- onboarding ---" >> "$OUT"
"$ADB" -s "$DEV" shell am instrument -w -e class app.hexavore.OnboardingJourneyTest "$RUNNER" >> "$OUT" 2>&1

echo "--- le tour d'écrans ---" >> "$OUT"
"$ADB" -s "$DEV" shell am instrument -w -e class app.hexavore.AppJourneyTest "$RUNNER" >> "$OUT" 2>&1

echo "--- plantages ---" >> "$OUT"
"$ADB" -s "$DEV" logcat -d -b crash >> "$OUT" 2>&1
echo "--- exceptions fatales ---" >> "$OUT"
"$ADB" -s "$DEV" logcat -d 2>/dev/null | grep -A 25 "FATAL EXCEPTION" >> "$OUT" 2>&1

table_rase
echo "écrit : $OUT"
