#!/bin/bash
# [regions] compiles the workstream and runs the offline arrival-card trigger harness
R=${1:-$(cd "$(dirname "$0")/../../.." && pwd)}
O=/tmp/claude-0/regions-harness
/home/claude/fh/tools/compile.sh "$R" "$O/classes" | tail -1
CP="$O/classes:$(cat /home/claude/fh/cp62.txt)"
mkdir -p "$O/h"
javac -proc:none --release 21 -nowarn -cp "$CP" -d "$O/h" "$R"/tools/regions/harness/com/formaworks/frontierhunts/regions/ArrivalHarness.java 2>&1 | grep -v JAVA_TOOL
java -cp "$O/h:$CP" com.formaworks.frontierhunts.regions.ArrivalHarness 2>&1 | grep -v JAVA_TOOL
