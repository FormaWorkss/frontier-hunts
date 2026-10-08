#!/bin/bash
# [1.1.3] village grading check (tools/villages/GradeCheck.java).  usage: gradecheck.sh <repo> <seed> <outdir> [count]
R=$(readlink -f ${1:-.}); shift
O=/tmp/claude-0/gradecheck; CP="$(cat $R/.infra/cp62.txt):/home/claude/qaserver/libs/modlauncher-11.0.5.jar"
rm -rf $O/cls; mkdir -p $O/cls
javac -proc:none --release 21 -nowarn -encoding UTF-8 -cp "/tmp/claude-0/cc-g19:$CP" -d $O/cls $(find $R/fs/java -name '*.java') $R/tools/villages/GradeCheck.java 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
cd $R && java $GC_OPTS -Dfs.templates=$R/fs/resources/data/frontierstructures/structure/expedition -cp "$O/cls:/tmp/claude-0/cc-g19:$CP" GradeCheck "$@" 2>&1 | grep -v JAVA_TOOL
