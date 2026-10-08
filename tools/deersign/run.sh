#!/bin/bash
# [deersign] Offline harness: sign-work state machine, season rates, strokes, contact of antlers with the bark and the
# rub / paw / licking-branch / rub-urination postures on the real rigs and meshes (Ultra) and box model (Vanilla).
#   tools/deersign/run.sh [repo root] [png output dir]      exit 0 when every check passes; writes previews.
R=$(cd "${1:-$(dirname "$0")/../..}" && pwd)
OUT=${2:-/tmp/claude-0/deersign-preview}
C=/tmp/claude-0/deersign-classes
H=/tmp/claude-0/deersign-harness
mkdir -p "$OUT"
/home/claude/fh/tools/compile.sh "$R" "$C" | tail -1
CP="$C:/home/claude/fh/merged62g8.jar:$(cat /home/claude/fh/cp62.txt)"
rm -rf "$H"; mkdir -p "$H"
javac -proc:none -nowarn --release 21 -cp "$CP" -d "$H" "$R"/tools/deersign/harness/*.java 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
java -Djava.awt.headless=true -cp "$H:$CP" SignHarness "$OUT" 2>&1 | grep -v -e JAVA_TOOL -e SLF4J
