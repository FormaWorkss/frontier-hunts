#!/bin/bash
# [polish] ATV reverse-gear check (needs a compile of src/ first: tools/compile.sh <repo> /tmp/claude-0/cc-polish)
R=$(cd "$(dirname "$0")/../.." && pwd); C=${1:-/tmp/claude-0/cc-polish}; O=/tmp/claude-0/rev-harness
rm -rf "$O"; mkdir -p "$O"
javac -proc:none --release 21 -nowarn -cp "$C:$(cat /home/claude/fh/cp62.txt)" -d "$O" "$R/tools/polish/harness/com/formaworks/frontierhunts/landscape/ride/RevHarness.java" 2>&1 | grep -v JAVA_TOOL
java -cp "$O:$C:$(cat /home/claude/fh/cp62.txt)" com.formaworks.frontierhunts.landscape.ride.RevHarness 2>&1 | grep -v JAVA_TOOL
