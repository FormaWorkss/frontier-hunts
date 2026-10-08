#!/bin/bash
# [survival] offline harness for the pure survival maths. usage: tools/survival/harness/run.sh [repo]
R=${1:-$(cd "$(dirname "$0")/../../.." && pwd)}
O=/tmp/claude-0/survival-harness
rm -rf "$O"; mkdir -p "$O"
javac -nowarn -d "$O" "$R/src/com/formaworks/frontierhunts/survival/SurvivalMath.java" "$R/tools/survival/harness/SurvivalHarness.java" 2>&1 | grep -v JAVA_TOOL
java -cp "$O" com.formaworks.frontierhunts.survival.SurvivalHarness 2>&1 | grep -v JAVA_TOOL
