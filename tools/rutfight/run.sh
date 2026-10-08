#!/bin/bash
# [rutfight] Offline locked-antler contact harness: every species x preset x antler size, real meshes from the jar.
#   tools/rutfight/run.sh [repo root] [png output dir]
# Exit code 0 when every check passes. Writes rutfight_<preset>.png previews (side | top view of two locked bulls).
R=$(cd "${1:-$(dirname "$0")/../..}" && pwd)
OUT=${2:-/tmp/claude-0/rutfight-preview}
C=/tmp/claude-0/rutfight-classes
H=/tmp/claude-0/rutfight-harness
mkdir -p "$OUT"
/home/claude/fh/tools/compile.sh "$R" "$C" | tail -1
CP="$C:/home/claude/fh/merged62g8.jar:$(cat /home/claude/fh/cp62.txt)"
rm -rf "$H"; mkdir -p "$H"
javac -proc:none -nowarn --release 21 -cp "$CP" -d "$H" "$R"/tools/rutfight/harness/*.java 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
java -Djava.awt.headless=true -cp "$H:$CP" RutFightHarness "$OUT" 2>&1 | grep -v -e JAVA_TOOL -e SLF4J
