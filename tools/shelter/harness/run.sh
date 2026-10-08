#!/bin/bash
# [shelter] offline harness: shared shelter detector on synthetic grids + shelter/fire thermal maths.
# usage: tools/shelter/harness/run.sh [repo]
R=${1:-$(cd "$(dirname "$0")/../../.." && pwd)}
O=/tmp/claude-0/shelter-harness
rm -rf "$O"; mkdir -p "$O"
javac -nowarn -d "$O" "$R/src/com/formaworks/frontierhunts/shelter/ShelterScan.java" "$R/src/com/formaworks/frontierhunts/survival/SurvivalMath.java" \
  "$R/tools/shelter/harness/ShelterHarness.java" 2>&1 | grep -v JAVA_TOOL
java -cp "$O" com.formaworks.frontierhunts.shelter.ShelterHarness 2>&1 | grep -v JAVA_TOOL
exit ${PIPESTATUS[0]}
