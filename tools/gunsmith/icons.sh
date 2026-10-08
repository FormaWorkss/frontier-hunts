#!/bin/bash
# [1.1.2] Hotbar icon bench (tools/gunsmith/IconBench.java): renders the weapons as the hotbar draws them, before and
# after the icon shader.  usage: tools/gunsmith/icons.sh <out dir> <classes of a src compile> <assets root(s)...>
set -e
R=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$1; CC=$2; shift 2
O=/tmp/claude-0/iconbench-classes
CP="$CC:$(cat $R/.infra/cp62.txt):/home/claude/qaserver/libs/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar"
rm -rf "$O"; mkdir -p "$O"
javac -proc:none --release 21 -nowarn -encoding UTF-8 -cp "$CP" -d "$O" "$R/tools/gunsmith/GunBench.java" "$R/tools/gunsmith/IconBench.java" 2>&1 | grep -v -e JAVA_TOOL -e '^Note:' || true
HB=/tmp/claude-0/vsh/assets/minecraft/textures/gui/sprites/hud/hotbar.png
java -Djava.awt.headless=true -Dbench.hotbar=$HB -Xmx3g -cp "$O:$CP" com.formaworks.frontierhunts.client.IconBench "$OUT" "$@" 2>&1 | grep -v JAVA_TOOL
