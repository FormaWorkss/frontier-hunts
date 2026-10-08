#!/bin/bash
# [meshes] Animation audit of the Ultra wildlife meshes (+ hound) through every rig state.
#   tools/meshes/audit.sh [before|after] [dump] [species,...]
#   before = master's meshes (git 644f37a) with this tree's code; after = this tree's patch/ meshes.
# Writes $OUT/<which>/audit.csv (+ posed .bin dumps with "dump" for tools/meshes/pose_sheet.py).
R=$(cd "$(dirname "$0")/../.." && pwd)
WHICH=${1:-after}
CC=${CC:-/tmp/claude-0/cc-meshes}
OUT=${OUT:-/tmp/claude-0/meshes-audit}
H=/tmp/claude-0/meshes-audit-classes
[ -d "$CC" ] || /home/claude/fh/tools/compile.sh "$R" "$CC" | tail -1
CP="$CC:/home/claude/fh/merged62g8.jar:$(cat /home/claude/fh/cp62.txt)"
rm -rf "$H"; mkdir -p "$H" "$OUT/$WHICH"
javac -proc:none -nowarn --release 21 -cp "$CP" -d "$H" $(find "$R/tools/meshes/harness" -name '*.java') 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
MESH="$R/patch/assets/frontierhunts/models/wildlife"
if [ "$WHICH" = before ]; then
  MESH=/tmp/claude-0/meshes-master; mkdir -p $MESH
  for f in $(git -C "$R" ls-tree --name-only 644f37a patch/assets/frontierhunts/models/wildlife/); do
    git -C "$R" show 644f37a:$f > $MESH/$(basename $f)
  done
fi
java -Xmx3g -cp "$H:$CP" com.formaworks.frontierhunts.wildlife2026.client.MeshAudit "$MESH" "$OUT/$WHICH" ${2:-nodump} ${3:-} 2>&1 | grep -v -e JAVA_TOOL -e SLF4J
