#!/bin/bash
# Frontier Hunts headless dedicated-server smoke test (QA workstream).
#
#   tools/qa/server_smoke.sh <mod jar> [commands file] [work dir]
#
# Boots a NeoForge 21.1.248 dedicated server (dev launch target "forgeserverdev" on the user's MDG-built
# neoforge-21.1.248.jar with all client-only packages stripped, so any client class our mod touches on the
# server fails exactly like in production), with <mod jar> + the test-only QA harness in mods/, waits for
# "Done", feeds the console commands (one per line; "#sleep N" pauses N seconds; "#wait REGEX" waits up to
# 120 s for a log line), stops the server and prints a verdict:
#   - every ERROR/WARN/exception line in logs/debug.log that is not on the known-vanilla allowlist
#   - every [FHQA] FAIL/WARN line from the harness
#   - every client-only class loaded by the JVM (net.minecraft.client.*, com.mojang.blaze3d.*,
#     com.formaworks.*.client.*) - must be empty
# Runtime lives in /home/claude/qaserver (libs/ modules/ run.sh, assembled from the user's Gradle caches).
set -u
trap "" PIPE # [1.2.7] a halted server (the crash test) closes the console pipe: keep going to the verdict
JAR=$(readlink -f "${1:?mod jar}")
H=$(cd "$(dirname "$0")" && pwd)
CMDS=${2:-$H/server_smoke.commands}
W=${3:-/tmp/claude-0/qa-smoke}
S=/home/claude/qaserver
[ -d $S/libs ] || { echo "missing $S (server runtime)"; exit 2; }
rm -rf "$W"; mkdir -p "$W/mods"
# [1.1.9] QA_WORLD=<dir>: start from a copy of an existing world (upgrade test)
[ -n "${QA_WORLD:-}" ] && cp -r "$QA_WORLD" "$W/world"
cp "$JAR" "$W/mods/"
bash "$H/harness/build.sh" "$W/mods/frontierqa-harness.jar" >/dev/null || exit 2
echo "eula=true" > "$W/eula.txt"
cat > "$W/server.properties" <<'P'
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
level-seed=frontierqa
server-port=25599
enable-command-block=true
sync-chunk-writes=false
P
# long single-command tests (sledtest) run inside one tick: QA_MAX_TICK=-1 turns the watchdog off for them
echo "max-tick-time=${QA_MAX_TICK:-60000}" >> "$W/server.properties"
mkfifo "$W/console.in"
sleep 100000 > "$W/console.in" &
HOLD=$!
QA_JVM_EXTRA="-Xlog:class+load=info:file=$W/classload.txt" $S/run.sh "$W" < "$W/console.in" > "$W/console.out" 2>&1 &
PID=$!
exec 3> "$W/console.in"
waitfor() { # regex timeout
   local t=0; while ! grep -qE "$1" "$W/console.out" 2>/dev/null; do sleep 1; t=$((t+1)); if [ $t -ge ${2:-120} ] || ! kill -0 $PID 2>/dev/null; then echo "!! timeout waiting for: $1"; return 1; fi; done
}
echo "booting server (log: $W/console.out)"
if waitfor 'Done \([0-9.]+s\)!' 400; then
   while IFS= read -r line; do
      case "$line" in
         ''|'#'[^sw]*) ;;
         '#sleep '*) sleep "${line#\#sleep }";;
         '#wait '*) waitfor "${line#\#wait }" 120;;
         *) echo "> $line"; echo "$line" >&3; sleep 2;;
      esac
   done < "$CMDS"
   echo "stop" >&3
fi
for i in $(seq 1 120); do kill -0 $PID 2>/dev/null || break; sleep 1; done
kill $PID 2>/dev/null; kill $HOLD 2>/dev/null; exec 3>&-
LOG="$W/logs/debug.log"; [ -f "$LOG" ] || LOG="$W/console.out"
echo; echo "===== [FHQA] results"
grep -h "\[FHQA\]" "$W/console.out" | sed -E 's/^\[[^]]*\] \[[^]]*\] \[[^]]*\]: //' | cut -c1-400
echo; echo "===== ERROR/WARN lines (vanilla noise filtered)"
grep -hE "/(WARN|ERROR|FATAL)\]" "$LOG" | grep -vE "Yggdrasil|OFFLINE/INSECURE|make no attempt|internet access|online-mode|Ambiguity between arguments|uses unexpected schema|JarJar which was passed in as source|Can't keep up" \
   | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn
echo; echo "===== exceptions mentioning frontier code"
grep -nE "^\s+at .*(formaworks|frontier)" "$LOG" | head -60
grep -nE "Exception|Error:" "$LOG" | grep -vE "Yggdrasil|MinecraftClientException|JsonSyntaxException|Expected BEGIN_OBJECT" | head -40 | cut -c1-300
echo; echo "===== client-only classes loaded on the dedicated server (must be empty)"
grep -oE "^\[[^]]*\]\[info\]\[class,load\] [^ ]+" "$W/classload.txt" | awk '{print $NF}' \
   | grep -E "^(net\.minecraft\.client\.|com\.mojang\.blaze3d\.|com\.formaworks\.[a-z.]*\.client\.|com\.formaworks\.frontierhunts\.client\.)" \
   | grep -vE "^net\.minecraft\.client\.server\.LanServerPinger" | sort -u
echo; echo "===== crash reports"; ls "$W/crash-reports" 2>/dev/null || echo none
