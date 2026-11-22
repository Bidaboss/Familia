#!/usr/bin/env bash
# One-off: copies, from the sources of material-icons-extended, only the icons the app uses, into
# app/src/main/java/androidx/compose/material/icons/ (same packages and names, so no import changes).
# Icons that already ship in material-icons-core (kept by Material 3) are left out.
set -euo pipefail
cd "$(dirname "$0")/.."
G=https://dl.google.com/dl/android/maven2
bom=$(grep -oP 'composeBom = "\K[^"]+' gradle/libs.versions.toml)
ver=$(curl -fsSL "$G/androidx/compose/compose-bom/$bom/compose-bom-$bom.pom" | grep -A1 'material-icons-extended' | grep -oP '<version>\K[^<]+' | head -1)
echo "bom=$bom icons-extended=$ver"
work=$(mktemp -d)
fetch() { # artifact, output name
  local art=$1 out=$2 ok=""
  for dir in "$art" "$art-android"; do
    for name in "$art-$ver-sources.jar" "$art-android-$ver-sources.jar"; do
      if curl -fsSL "$G/androidx/compose/material/$dir/$ver/$name" -o "$work/$out.jar" 2>/dev/null; then ok=1; break 2; fi
    done
  done
  [ -n "$ok" ] || { echo "no sources jar for $art $ver"; exit 1; }
  mkdir -p "$work/$out"; unzip -q "$work/$out.jar" -d "$work/$out"
}
fetch material-icons-extended ext
fetch material-icons-core core
grep -rhoE 'Icons\.(AutoMirrored\.)?(Outlined|Filled|Rounded|Default|Sharp|TwoTone)\.[A-Za-z0-9]+' app/src/main app/src/test \
  | sed -E 's/^Icons\.//; s/^Default\./Filled./; s/^AutoMirrored\.Default\./AutoMirrored.Filled./' | sort -u > "$work/used.txt"
dest=app/src/main/java/androidx/compose/material/icons
rm -rf "$dest"; mkdir -p "$dest"
missing=0; copied=0; core=0
while IFS= read -r line; do
  name=${line##*.}; style=${line%.*}
  if [[ $style == AutoMirrored.* ]]; then rel="automirrored/$(echo "${style#AutoMirrored.}" | tr 'A-Z' 'a-z')/$name.kt"
  else rel="$(echo "$style" | tr 'A-Z' 'a-z')/$name.kt"; fi
  if [ -n "$(find "$work/core" -path "*material/icons/$rel" | head -1)" ]; then core=$((core+1)); continue; fi
  src=$(find "$work/ext" -path "*material/icons/$rel" | head -1)
  if [ -z "$src" ]; then echo "MISSING $line ($rel)"; missing=$((missing+1)); continue; fi
  mkdir -p "$dest/$(dirname "$rel")"; cp "$src" "$dest/$rel"; copied=$((copied+1))
done < "$work/used.txt"
echo "used=$(wc -l < "$work/used.txt") copied=$copied core=$core missing=$missing"
echo "$ver" > "$dest/VERSION.txt"
du -sh "$dest"
