#!/bin/bash
# builds the test-only QA harness mod jar: tools/qa/harness/build.sh <out.jar>
# [clothing] QA_MOD_CP (freshly compiled mod classes) goes BEFORE the base jar on the classpath, so changed classes win
H=$(cd "$(dirname "$0")" && pwd); OUT=${1:-/home/claude/qaserver/frontierqa-harness.jar}
T=$(mktemp -d); javac -proc:none --release 21 -nowarn -cp "${QA_MOD_CP:+$QA_MOD_CP:}$(cat /home/claude/fh/cp62.txt 2>/dev/null || cat $H/../../../.infra/cp62.txt)" -d $T $(find $H/src -name '*.java') 2>&1 | grep -v JAVA_TOOL
cp -r $H/res/. $T/; rm -f "$OUT"; (cd $T && jar cf "$OUT" .) && echo "built $OUT"; rm -rf $T
