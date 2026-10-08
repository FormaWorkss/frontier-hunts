#!/bin/bash
# [presets] Offline check of the two-preset logic. usage: tools/presets/run.sh [repo root]
R=$(cd "${1:-$(dirname "$0")/../..}" && pwd)
O=/tmp/claude-0/presets-check
/home/claude/fh/tools/compile.sh "$R" "$O/classes" >/dev/null
CP="$O/classes:$(cat /home/claude/fh/cp62.txt)"
javac -proc:none --release 21 -nowarn -cp "$CP" -d "$O" "$R/tools/presets/PresetCheck.java" 2>&1 | grep -v -e JAVA_TOOL -e '^Note:'
java -cp "$O:$CP" PresetCheck 2>&1 | grep -v -e JAVA_TOOL -e SLF4J
J=${PIPESTATUS[0]}
# a fresh config (all defaults) must be exactly the Ultra preset (night-config is not on the offline classpath, so this
# reads the defaults from the source)
python3 - "$R" <<'PY'
import re, sys
r = sys.argv[1] + '/src/com/formaworks/frontierhunts/'
src = open(r + 'HuntConfig.java').read() + open(r + 'perf/PerfConfig.java').read()
want = {'graphicsPreset': 'ULTRA', 'quality': 'CINEMATIC', 'animalStyle': 'REALISTIC', 'animalDetail': 'ULTRA', 'worldLook': 'REALISTIC',
        'grassStyle': 'FRONTIER', 'grassHeightPercent': '110', 'grassWidthPercent': '100', 'grassThickness': 'THICK',
        'waterfallDetail': 'ULTRA', 'wildlifeLife': 'true', 'breathVapor': 'true', 'mountainSpindrift': 'true', 'treeDetail': 'ULTRA'}
bad = 0
for k, v in want.items():
    m = re.search(r'(?:defineEnum|defineInRange|define)\("' + k + r'",\s*([^,)]+)', src)
    got = m.group(1).strip().split('.')[-1] if m else None
    ok = got == v
    bad += not ok
    print(('ok   ' if ok else 'FAIL ') + f'default {k} = {got} (Ultra: {v})')
sys.exit(1 if bad else 0)
PY
P=$?
echo "logic=$J defaults=$P"
[ "$J" = 0 ] && [ "$P" = 0 ]
