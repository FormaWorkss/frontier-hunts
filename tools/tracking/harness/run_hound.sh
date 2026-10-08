#!/bin/bash
# [hound3] HoundBrain offline harness: compiles HoundBrain + the harness alone against the game classpath and runs it.
R=$(cd "$(dirname "$0")/../../.." && pwd)
O=${1:-/tmp/claude-0/hound-harness}
rm -rf "$O"; mkdir -p "$O"
CP="$(cat /home/claude/fh/cp62.txt)"
javac -proc:none --release 21 -nowarn -cp "$CP" -d "$O" "$R/src/com/formaworks/frontierhunts/tracking/hound/HoundBrain.java" \
  "$R/tools/tracking/harness/com/formaworks/frontierhunts/tracking/hound/HoundHarness.java" 2>&1 | grep -v JAVA_TOOL
java -cp "$O:$CP" com.formaworks.frontierhunts.tracking.hound.HoundHarness 2>&1 | grep -v JAVA_TOOL
