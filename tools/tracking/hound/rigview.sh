#!/bin/bash
# [hound3] compile HoundRig + the dump driver against the game classes (no full compile needed)
R=$(cd "$(dirname "$0")/../../.." && pwd)
O=/tmp/claude-0/h4/rigdump
mkdir -p $O
CP="$(cat /home/claude/fh/cp62.txt):/home/claude/fh/orig62.jar"
javac -proc:none --release 21 -nowarn -cp "$CP" -d $O \
  "$R/src/com/formaworks/frontierhunts/tracking/client/HoundRig.java" \
  "$R/src/com/formaworks/frontierhunts/wildlife2026/client/SkinnedMesh.java" "$R/src/com/formaworks/frontierhunts/wildlife2026/client/MeshSkinner.java" \
  "$R/tools/tracking/hound/rigdump/com/formaworks/frontierhunts/wildlife2026/client/HoundRigDump.java" 2>&1 | grep -v JAVA_TOOL
java -cp "$O:$CP" com.formaworks.frontierhunts.wildlife2026.client.HoundRigDump "$@" 2>&1 | grep -v JAVA_TOOL
