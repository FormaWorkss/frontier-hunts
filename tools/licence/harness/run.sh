#!/bin/bash
# [licence] offline harness: regulation tables and licence-season calendar
R=$(cd "${1:-$(dirname "$0")/../../..}" && pwd)
O=/dev/shm/licence-harness; rm -rf $O; mkdir -p $O
javac -nowarn -d $O "$R/src/com/formaworks/frontierhunts/licence/Regulations.java" "$R/tools/licence/harness/RegHarness.java" 2>&1 | grep -v JAVA_TOOL
java -cp $O RegHarness 2>&1 | grep -v JAVA_TOOL
