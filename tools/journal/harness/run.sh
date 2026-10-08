#!/bin/bash
# [journal] compiles the workstream and runs the offline journal harness
R=${1:-$(cd "$(dirname "$0")/../../.." && pwd)}
O=/tmp/claude-0/journal-harness
/home/claude/fh/tools/compile.sh "$R" "$O/classes" | tail -1
CP="$O/classes:$(cat /home/claude/fh/cp62.txt):/home/claude/fh/orig62.jar"
mkdir -p "$O/h"
javac -proc:none --release 21 -nowarn -cp "$CP" -d "$O/h" "$R"/tools/journal/harness/com/formaworks/frontierhunts/journal/JournalHarness.java 2>&1 | grep -v JAVA_TOOL
java -cp "$O/h:$CP" com.formaworks.frontierhunts.journal.JournalHarness 2>&1 | grep -v JAVA_TOOL
