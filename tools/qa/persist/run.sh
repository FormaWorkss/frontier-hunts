#!/bin/bash
# [1.2.7] Progress-survives-interruptions test on a dedicated server (tools/qa/server_smoke.sh + the QA harness).
#   tools/qa/persist/run.sh <mod jar> <class dir the jar was compiled to (.infra/compile.sh output)> [work dir]
# r1..r5: one milestone each (licence, academy, harvest, contract, first-hunt reward), then the JVM is killed
#         (Runtime.halt, no shutdown) the moment no progress save is pending; each next run starts from that world,
#         compares the state with the snapshot and tries every claim again. r6: disconnect test + clean stop; r7: check.
# c1/c2: the same crash after a logout with the commit saves switched off (shows what they prevent: expect 4 FAILs: tokens, licence, deer tag, academy).
# flow: the first hunt step by step on a fresh world.
P=$(cd "$(dirname "$0")" && pwd); JAR=$(readlink -f "$1"); export QA_MOD_CP=$(readlink -f "$2"); D=${3:-/tmp/fh-persist}
mkdir -p "$D"; prev=""
for r in r1 r2 r3 r4 r5 r6 r7 c1 c2 flow; do
  case $r in r1|c1|flow) prev="";; esac
  if [ -n "$prev" ]; then export QA_WORLD=$D/w-$prev/world; else unset QA_WORLD; fi
  bash "$P/../server_smoke.sh" "$JAR" "$P/$r.commands" "$D/w-$r" > "$D/$r.out" 2>&1
  echo "$r: $(grep -c 'persist PASS\|flow PASS' "$D/w-$r/console.out") pass, $(grep -c 'persist FAIL\|flow FAIL' "$D/w-$r/console.out") fail"
  prev=$r
done
