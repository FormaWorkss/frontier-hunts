#!/bin/bash
# [phone] Offline engine tests: chess perft and rules, AI, dice scoring and AI, flush determinism and replay.
set -e
R=$(cd "$(dirname "$0")/../.." && pwd)
B=$(mktemp -d)
javac -proc:none --release 21 -nowarn -encoding UTF-8 -d "$B" $(find "$R/src/com/formaworks/frontierhunts/phone/games" -name '*.java') "$R"/tools/phone/test/*.java 2>&1 | grep -v JAVA_TOOL || true
java -cp "$B" EngineTest "$@" 2>&1 | grep -v JAVA_TOOL
rm -rf "$B"
