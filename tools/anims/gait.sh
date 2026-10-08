#!/bin/bash
# [anims] Gait audit (no-slide check) of the Ultra wildlife: tools/anims/gait.sh [classes dir] [species,...] [trace dir]
#   classes dir defaults to a fresh compile of this tree; pass another tree's compile for before/after numbers.
R=$(cd "$(dirname "$0")/../.." && pwd)
CC=${1:-/tmp/claude-0/cc-anims}
[ -d "$CC" ] || /home/claude/fh/tools/compile.sh "$R" "$CC" | tail -1
CP="$CC:/home/claude/fh/merged62g8.jar:$(cat /home/claude/fh/cp62.txt)"
H=$(mktemp -d /tmp/claude-0/gait-h.XXXX)
javac -proc:none -nowarn --release 21 -cp "$CP" -d "$H" $(find "$R/tools/anims/harness" -name 'GaitAudit.java') 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
OUT=${OUT:-/tmp/claude-0/anims-gait.csv}
java -Xmx2g $JOPT -cp "$H:$CP" com.formaworks.frontierhunts.wildlife2026.client.GaitAudit "${MESH:-$R/patch/assets/frontierhunts/models/wildlife}" "$OUT" "${2:-}" ${3:-} 2>&1 | grep -v -e JAVA_TOOL -e SLF4J
rm -rf "$H"
