#!/bin/bash
# [phone] Renders the Field Phone UI offline: tools/phone/mock.sh [scene...]  -> tools/phone/mocks/phone_<scene>.png
# Compiles the pure UI (phone/client/ui), the game engines (phone/games) and the Java2D mock canvas with plain javac:
# no Minecraft on the classpath, which also proves the UI layer never touches game classes.
set -e
R=$(cd "$(dirname "$0")/../.." && pwd)
JAR=${JAR:-/home/claude/fh2/.build/FrontierHunts-1.3.0.jar}
OUT=${OUT:-$R/tools/phone/mocks}
B=$(mktemp -d)
SRC=$(find "$R/src/com/formaworks/frontierhunts/phone/client/ui" "$R/src/com/formaworks/frontierhunts/phone/games" -name "*.java" 2>/dev/null || true)
javac -proc:none --release 21 -nowarn -encoding UTF-8 -d "$B" $SRC "$R"/tools/phone/mock/src/*.java 2>&1 | grep -v JAVA_TOOL || true
java -DflushFrames=${FF:-230} -Djava.awt.headless=true -cp "$B" MockMain "$R" "$JAR" "$OUT" "$@" 2>&1 | grep -v JAVA_TOOL
rm -rf "$B"
