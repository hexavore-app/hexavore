#!/usr/bin/env bash
# Toutes les versions, l'une après l'autre, puis le tableau de ce qu'elles ont dit.
#
# Usage : toutes-les-versions.sh [nom-d-avd ...]
#         sans argument, tous les AVD dont le nom commence par « Hexavore_ ».
set -u

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
SORTIE="${SORTIE:-$RACINE/build/revue-versions}"

if [ "$#" -gt 0 ]; then
  AVDS=("$@")
else
  EMU="$SDK/emulator/emulator"
  [ -x "$EMU" ] || EMU="$EMU.exe"
  mapfile -t AVDS < <(cd "$SDK/emulator" && "$EMU" -list-avds 2>/dev/null | grep '^Hexavore_')
fi

[ "${#AVDS[@]}" -gt 0 ] || { echo "Aucun AVD à parcourir."; exit 1; }

for avd in "${AVDS[@]}"; do
  echo "######## $avd"
  SORTIE="$SORTIE" bash "$ICI/une-version.sh" "$avd"
done

echo
echo "######## Ce que chaque version a dit"
for f in "$SORTIE"/*.txt; do
  [ -f "$f" ] || continue
  entete=$(head -1 "$f")
  verdict=$(grep -E "^(OK \(|Tests run:)" "$f" | tr '\n' ' ')
  fatales=$(grep -c "FATAL EXCEPTION" "$f")
  echo "$entete"
  echo "    $verdict"
  echo "    exceptions fatales : $fatales"
done
