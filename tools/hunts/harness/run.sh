#!/bin/bash
# [hunts] compiles the workstream and runs the offline species-hunts harness (writes docs/ws/hunts/milestones.json)
R=${1:-$(cd "$(dirname "$0")/../../.." && pwd)}
O=/tmp/claude-0/hunts-harness
/home/claude/fh/tools/compile.sh "$R" "$O/classes" | tail -1
CP="$O/classes:$(cat /home/claude/fh/cp62.txt):/home/claude/fh/orig62.jar"
mkdir -p "$O/h"
javac -proc:none --release 21 -nowarn -cp "$CP" -d "$O/h" "$R"/tools/hunts/harness/com/formaworks/frontierhunts/hunts/HuntsHarness.java 2>&1 | grep -v JAVA_TOOL
java -Dhunts.repo="$R" -cp "$O/h:$CP" com.formaworks.frontierhunts.hunts.HuntsHarness "$R" 2>&1 | grep -v JAVA_TOOL
