#!/bin/bash
# [villages] offline planner harness on the real alpine terrain + top-down previews.
#   harness.sh <repo> <seed> <auto|all|outpost|lakeside|crowsnest|junction> [step-chunks] [count]
R=$(readlink -f ${1:-.}); shift
O=/tmp/claude-0/villages-harness; CP="$(cat /home/claude/fh/cp62.txt):/home/claude/qaserver/libs/modlauncher-11.0.5.jar"
rm -rf $O/cls $O/out; mkdir -p $O/cls $O/out
javac -proc:none --release 21 -nowarn -encoding UTF-8 -cp "$CP" -d $O/cls $(find $R/fs/java -name '*.java') $R/tools/villages/VillageHarness.java 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
cd $R && java -Dfs.templates=$R/fs/resources/data/frontierstructures/structure/expedition -cp "$O/cls:$CP" VillageHarness "$1" "$2" $O/out "${@:3}" 2>&1 | grep -v JAVA_TOOL
for f in $O/out/*.json; do python3 $R/tools/villages/topdown.py $f ${f%.json}.png 4; done
echo "previews: $O/out"
