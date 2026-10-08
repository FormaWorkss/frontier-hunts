#!/bin/bash
# [onboard2] compiles the workstream and runs the offline seat checks (+ the window measurement)
R=${1:-$(cd "$(dirname "$0")/../.." && pwd)}
O=/tmp/claude-0/onboard2-harness
/home/claude/fh/tools/compile.sh "$R" "$O/classes" | tail -1
CP="$O/classes:$(cat /home/claude/fh/cp62.txt):/home/claude/fh/orig62.jar"
mkdir -p "$O/h"
javac -proc:none --release 21 -nowarn -cp "$CP" -d "$O/h" "$R"/tools/onboard2/harness/com/formaworks/frontierhunts/seating/SeatHarness.java 2>&1 | grep -v JAVA_TOOL
java -cp "$O/h:$CP" com.formaworks.frontierhunts.seating.SeatHarness 2>&1 | grep -v JAVA_TOOL
python3 "$R"/tools/onboard2/blind_windows.py "$R" | tail -3
