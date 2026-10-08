#!/bin/bash
# [archery2] first-person view harness: tools/archery2/view.sh <compiled classes dir> [outDir] [userFov]
R=$(cd "$(dirname "$0")/../.." && pwd); C=${1:-/tmp/claude-0/cc-archery2}; O=/tmp/claude-0/archery2-viewh
rm -rf "$O"; mkdir -p "$O"
javac -proc:none --release 21 -nowarn -cp "$C:$(cat /home/claude/fh/cp62.txt)" -d "$O" $(find "$R/tools/archery2/harness" "$R/tools/archery2/stubs" -name '*.java') 2>&1 | grep -v JAVA_TOOL
java $JOPTS -Djava.awt.headless=true -cp "$O:$C:$(cat /home/claude/fh/cp62.txt)" ${MAIN:-com.formaworks.frontierhunts.client.ViewHarness} "${2:-/tmp/claude-0/archery2-views}" "${3:-70}" 2>&1 | grep -v JAVA_TOOL
