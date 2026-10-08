#!/bin/bash
# [meshes] CPU microbenchmark of the Ultra wildlife render path, master vs this branch: tools/meshes/bench.sh [species height]
R=$(cd "$(dirname "$0")/../.." && pwd)
CC=${CC:-/tmp/claude-0/cc-meshes}
H=/tmp/claude-0/meshes-bench-classes
MASTER=/tmp/claude-0/meshes-master
[ -d "$CC" ] || /home/claude/fh/tools/compile.sh "$R" "$CC" | tail -1
CP="$CC:/home/claude/fh/merged62g8.jar:$(cat /home/claude/fh/cp62.txt)"
mkdir -p $MASTER $H
for f in $(git -C "$R" ls-tree --name-only 644f37a patch/assets/frontierhunts/models/wildlife/); do
  [ -f $MASTER/$(basename $f) ] || git -C "$R" show 644f37a:$f > $MASTER/$(basename $f)
done
javac -proc:none -nowarn --release 21 -cp "$CP" -d "$H" "$R/tools/meshes/harness/com/formaworks/frontierhunts/wildlife2026/client/MeshBench.java" 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
java -Xmx2g -cp "$H:$CP" com.formaworks.frontierhunts.wildlife2026.client.MeshBench $MASTER "$R/patch/assets/frontierhunts/models/wildlife" ${1:-wolf} ${2:-0.95} 2>&1 | grep -v -e JAVA_TOOL -e SLF4J
