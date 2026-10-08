#!/bin/bash
# [herds] offline checks of the social model: tools/herds/offline.sh [compiled mod classes dir]
H=$(cd "$(dirname "$0")" && pwd); R=$(cd "$H/../.." && pwd); C=${1:-/tmp/claude-0/cc-herds}
CP="$(cat /home/claude/fh2/.infra/cp62.txt 2>/dev/null || cat $R/.infra/cp62.txt)"
T=$(mktemp -d); javac -proc:none --release 21 -nowarn -cp "$C:$CP" -d $T "$H/HerdOffline.java" 2>&1 | grep -v JAVA_TOOL
java -cp "$T:$C:$CP" com.formaworks.frontierhunts.hunting.herd.HerdOffline 2>&1 | grep -v JAVA_TOOL; rc=${PIPESTATUS[0]}; rm -rf $T; exit $rc
