#!/bin/bash
# [wingshot] pose dump + checks for the Ultra bird meshes (needs a compiled tree: tools/compile.sh <repo> /tmp/claude-0/cc-wingshot)
R=$(cd "$(dirname "$0")/../../.." && pwd)
CC=${CC:-/tmp/claude-0/cc-wingshot}
O=${TMPDIR:-/tmp}/wingshot-poses
JAR=${JAR:-/home/claude/fh/merged62g8.jar}
rm -rf "$O"; mkdir -p "$O/classes" "$O/out"
CP="$CC:$(cat /home/claude/fh/cp62.txt)"
javac -proc:none -nowarn --release 21 -cp "$CP" -d "$O/classes" "$R/tools/wingshot/harness/com/formaworks/frontierhunts/wingshot/client/PoseHarness.java" 2>&1 | grep -v JAVA_TOOL
java -cp "$O/classes:$CP" com.formaworks.frontierhunts.wingshot.client.PoseHarness "$JAR" "$O/out" 2>&1 | grep -v JAVA_TOOL
echo "poses: $O/out"
