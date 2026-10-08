#!/bin/bash
# usage: compile.sh <repo root containing src/> <output class dir>
R=${1:-/home/claude/fh}; O=${2:-/tmp/claude-0/cc}
CP="$(cat /home/claude/fh/cp62.txt)"
rm -rf "$O"; mkdir -p "$O"
javac -proc:none --release 21 -nowarn -encoding UTF-8 -cp "$CP" -d "$O" $(find "$R/src" -name '*.java') 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
echo "exit=${PIPESTATUS[0]} classes=$(find "$O" -name '*.class' | wc -l)"
