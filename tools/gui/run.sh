#!/bin/bash
# [gui] Offline checks for the settings screen + sound mixer.
#   tools/gui/run.sh [repo root]
# 1. LayoutDump: settings-screen layout at every window size / GUI scale (overlaps, off-screen, scroll reach).
# 2. SoundMapCheck: every frontierhunts sound event (base jar sounds.json + all merge fragments) has a volume group.
R=$(cd "${1:-$(dirname "$0")/../..}" && pwd)
O=/tmp/claude-0/gui-tools
rm -rf "$O"; mkdir -p "$O"
javac -proc:none --release 21 -nowarn -d "$O" \
  "$R/src/com/formaworks/frontierhunts/client/settings/SettingsLayout.java" \
  "$R/src/com/formaworks/frontierhunts/sound/FrontierSoundCategory.java" \
  "$R/tools/gui/LayoutDump.java" "$R/tools/gui/SoundMapCheck.java" 2>&1 | grep -v JAVA_TOOL
echo "== layout"
java -cp "$O" LayoutDump 2>&1 | grep -v JAVA_TOOL; L=${PIPESTATUS[0]}
echo "== sound groups"
python3 - "$R" <<'EOF' | java -cp "$O" SoundMapCheck 2>&1 | grep -v JAVA_TOOL
import json, zipfile, glob, sys, os
r = sys.argv[1]
names = set(json.loads(zipfile.ZipFile('/home/claude/fh/orig62.jar').read('assets/frontierhunts/sounds.json')))
p = os.path.join(r, 'patch/assets/frontierhunts/sounds.json')
if os.path.exists(p): names |= set(json.load(open(p)))
for f in glob.glob(os.path.join(r, 'patch/_merge/assets/frontierhunts/sounds.json/*.json')): names |= set(json.load(open(f)))
print('\n'.join(sorted(names)))
EOF
S=${PIPESTATUS[1]}
echo "layout=$L sounds=$S"
[ "$L" = 0 ] && [ "$S" = 0 ]
