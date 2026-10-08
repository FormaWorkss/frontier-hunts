#!/bin/bash
# [academy] compiles the workstream and runs the offline training state-machine harness
R=${1:-$(cd "$(dirname "$0")/../../.." && pwd)}
O=/tmp/claude-0/academy-harness
/home/claude/fh/tools/compile.sh "$R" "$O/classes" | tail -1
CP="$O/classes:$(cat /home/claude/fh/cp62.txt):/home/claude/fh/orig62.jar"
mkdir -p "$O/h"
javac -proc:none --release 21 -nowarn -cp "$CP" -d "$O/h" "$R"/tools/academy/harness/com/formaworks/frontierhunts/academy/AcademyHarness.java 2>&1 | grep -v JAVA_TOOL
java -cp "$O/h:$CP" com.formaworks.frontierhunts.academy.AcademyHarness 2>&1 | grep -v JAVA_TOOL
