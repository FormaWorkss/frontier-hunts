#!/bin/bash
# [herds] Social-group test on a dedicated server, then the same world restarted:
#   tools/qa/herds_run.sh <mod jar> <class dir the jar was compiled to (.infra/compile.sh output)> [work dir]
Q=$(cd "$(dirname "$0")" && pwd); JAR=$(readlink -f "$1"); export QA_MOD_CP=$(readlink -f "$2"); D=${3:-/tmp/fh-herds}
mkdir -p "$D"; unset QA_WORLD
bash "$Q/server_smoke.sh" "$JAR" "$Q/herds.commands" "$D/w1" > "$D/run1.out" 2>&1
export QA_WORLD=$D/w1/world
bash "$Q/server_smoke.sh" "$JAR" "$Q/herds_restart.commands" "$D/w2" > "$D/run2.out" 2>&1
for r in w1 w2; do
  echo "== $r: $(grep -c 'herds PASS' "$D/$r/console.out") pass, $(grep -c 'herds FAIL' "$D/$r/console.out") fail"
  grep -h "\[FHQA\] herds" "$D/$r/console.out" | sed -E 's/^\[[^]]*\] \[[^]]*\] \[[^]]*\]: //' | cut -c1-400
done
