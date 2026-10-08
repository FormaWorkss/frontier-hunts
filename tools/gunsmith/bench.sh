#!/bin/bash
# [gunsmith] Offline weapon bench. Usage:
#   tools/gunsmith/bench.sh <mode: render|ads|tris|one> <out dir> [before|after]   (default: after)
# "before" = assets of the base jar only; "after" = base jar assets overlaid with this repo's patch/.
# Needs a compile of src/ (tools/compile.sh <repo> /tmp/claude-0/cc-gunsmith) unless CC points elsewhere.
set -e
R=$(cd "$(dirname "$0")/../.." && pwd)
CC=${CC:-/tmp/claude-0/cc-gunsmith}
O=/tmp/claude-0/gunbench-classes
X=/tmp/claude-0/gunbench-base
C=/mnt/user-data/uploads/caches/modules-2/files-2.1
NC=$(find $C -name "*.jar" ! -name "*sources*" 2>/dev/null | grep -v -e minecraft-client -e /neoforge/ | tr "\n" ":")
CP="$CC:$(cat /home/claude/fh/cp62.txt):$NC:/home/claude/qaserver/libs/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar"
if [ ! -d "$X/assets" ]; then
  mkdir -p "$X"; (cd "$X" && unzip -q -o /home/claude/fh/merged62g8.jar 'assets/frontierhunts/models/equipment/*' 'assets/frontierhunts/models/item/camo_rifle_*' \
     'assets/frontierhunts/textures/item/firearms_v2.png' 'assets/frontierhunts/textures/item/field_equipment.png' 'assets/frontierhunts/textures/material/*' \
     'assets/frontierhunts/textures/equipment/rifle/*' 'assets/frontierhunts/textures/entity/material.png' 2>/dev/null || true)
fi
rm -rf "$O"; mkdir -p "$O"
javac -proc:none --release 21 -nowarn -encoding UTF-8 -cp "$CP" -d "$O" "$R/tools/gunsmith/GunBench.java" 2>&1 | grep -v -e JAVA_TOOL -e '^Note:' || true
ROOTS="$X"
[ "${3:-after}" = "after" ] && ROOTS="$X $R/patch"
java $JOPTS -Djava.awt.headless=true -Xmx3g -cp "$O:$CP" com.formaworks.frontierhunts.client.GunBench "$1" "$2" $ROOTS 2>&1 | grep -v JAVA_TOOL
