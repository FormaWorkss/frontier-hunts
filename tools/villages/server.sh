#!/bin/bash
# [villages] interactive headless server for worldgen checks (same runtime as tools/livingworld/server_test.sh).
#   server.sh start <jar> <dir> [seed]   boot in the background (waits for "Done")
#   server.sh cmd <dir> "<command>"      send a console command (prints new console lines after ~N s, N=$WAIT default 3)
#   server.sh stop <dir>                 save, stop, print the ERROR/WARN summary
set -u
S=/home/claude/qaserver
case "$1" in
start)
  JAR=$(readlink -f "$2"); W=$3
  rm -rf "$W"; mkdir -p "$W/mods"; ln -s "$JAR" "$W/mods/"
  echo "eula=true" > "$W/eula.txt"
  cat > "$W/server.properties" <<P
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
level-seed=${4:-frontierqa}
level-type=${LW_TYPE:-minecraft\:normal}
server-port=${LW_PORT:-25743}
enable-command-block=true
sync-chunk-writes=false
max-tick-time=60000
P
  mkfifo "$W/console.in"
  sleep 1000000 > "$W/console.in" 2>/dev/null &
  echo $! > "$W/holdpid"
  QA_JVM_EXTRA="-Xmx${LW_XMX:-2300M} -Xlog:class+load=info:file=$W/classload.txt" nohup $S/run.sh "$W" < "$W/console.in" > "$W/console.out" 2>&1 &
  echo $! > "$W/pid"
  t=0; while ! grep -qE 'Done \([0-9.]+s\)!' "$W/console.out" 2>/dev/null; do sleep 2; t=$((t+2)); if [ $t -ge 1200 ] || ! kill -0 $(cat $W/pid) 2>/dev/null; then echo "boot failed"; tail -20 "$W/console.out"; exit 1; fi; done
  echo "booted in ${t}s";;
cmd)
  W=$2; n=$(wc -l < "$W/console.out"); echo "$3" > "$W/console.in"; sleep ${WAIT:-3}; tail -n +$((n+1)) "$W/console.out" | sed -E 's/^\[[^]]*\] \[[^]]*\] \[[^]]*\]: //' | cut -c1-300;;
stop)
  W=$2; echo "save-all flush" > "$W/console.in"; sleep 5; echo "stop" > "$W/console.in"
  for i in $(seq 1 90); do kill -0 $(cat $W/pid) 2>/dev/null || break; sleep 1; done
  kill $(cat $W/pid) $(cat $W/holdpid) 2>/dev/null
  LOG="$W/logs/debug.log"; [ -f "$LOG" ] || LOG="$W/console.out"
  echo "===== ERROR/WARN lines (vanilla noise filtered)"
  grep -hE "/(WARN|ERROR|FATAL)\]" "$LOG" | grep -vE "Yggdrasil|OFFLINE/INSECURE|make no attempt|internet access|online-mode|Ambiguity between arguments|uses unexpected schema|JarJar which was passed in as source|Can't keep up" \
     | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn | head -40
  echo "===== exceptions"; grep -nE "^\s+at .*(formaworks|frontier)" "$LOG" | head -20
  echo "===== client classes"; grep -oE "^\[[^]]*\]\[info\]\[class,load\] [^ ]+" "$W/classload.txt" | awk '{print $NF}' | grep -E "^(net\.minecraft\.client\.|com\.mojang\.blaze3d\.|com\.formaworks\.[a-z.]*\.client\.)" | grep -v LanServerPinger | sort -u | head
  echo "===== crash reports"; ls "$W/crash-reports" 2>/dev/null || echo none;;
esac
