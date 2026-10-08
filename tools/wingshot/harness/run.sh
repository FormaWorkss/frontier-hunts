#!/bin/bash
# [wingshot] offline flight harness: compiles the pure flight model with the harness and runs it.
R=$(cd "$(dirname "$0")/../../.." && pwd)
O=${TMPDIR:-/tmp}/wingshot-harness
rm -rf "$O"; mkdir -p "$O/out"
javac -nowarn --release 21 -d "$O" "$R/src/com/formaworks/frontierhunts/wingshot/Flight.java" \
   "$R/tools/wingshot/harness/com/formaworks/frontierhunts/wingshot/FlightHarness.java" 2>&1 | grep -v JAVA_TOOL
java -Dout="$O/out" -cp "$O" com.formaworks.frontierhunts.wingshot.FlightHarness 2>&1 | grep -v JAVA_TOOL
echo "tracks: $O/out"
