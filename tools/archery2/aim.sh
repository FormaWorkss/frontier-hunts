#!/bin/bash
# [archery2] end-to-end aim harness: tools/archery2/aim.sh [compiled classes dir]
R=$(cd "$(dirname "$0")/../.." && pwd); C=${1:-/tmp/claude-0/cc-archery2}; O=/tmp/claude-0/archery2-aimh
rm -rf "$O"; mkdir -p "$O"
javac -proc:none --release 21 -nowarn -cp "$C:$(cat /home/claude/fh/cp62.txt)" -d "$O" "$R/tools/archery2/AimHarness.java" $(find "$R/tools/archery2/stubs" -name '*.java') 2>&1 | grep -v JAVA_TOOL
java -cp "$O:$C:$(cat /home/claude/fh/cp62.txt)" AimHarness 2>&1 | grep -v -e JAVA_TOOL -e SLF4J
