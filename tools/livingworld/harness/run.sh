#!/bin/bash
# [livingworld] offline plan harness + previews.  run.sh <repo> [kind|all] [variant|all] [terrain|all] [seed]
R=$(readlink -f ${1:-.}); shift
O=/tmp/claude-0/lw-harness
rm -rf $O/cls; mkdir -p $O/cls $O/out
cd $O && javac -sourcepath "" -nowarn -encoding UTF-8 -d $O/cls $(find $R/src/com/formaworks/frontierhunts/livingworld/plan -name '*.java') $R/tools/livingworld/harness/Harness.java || exit 1
java -cp $O/cls Harness $O/out "$@"
