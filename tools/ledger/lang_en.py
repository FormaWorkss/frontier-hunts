#!/usr/bin/env python3
"""[ledger] Writes the Expedition journal lang fragment from the L("key", "English") calls in ExpeditionScreen.

python3 tools/ledger/lang_en.py <repo>
"""
import json, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
SRC = os.path.join(ROOT, 'src/com/formaworks/frontierhunts/client/ExpeditionScreen.java')
OUT = os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/ledger.json')
P = 'expedition.frontierhunts.ui.'
src = open(SRC).read()
lang = {'screen.frontierhunts.expedition': 'Frontier Expeditions'}
for m in re.finditer(r'\bL\(\s*"([a-z0-9_.]+)"\s*,\s*"((?:[^"\\]|\\.)*)"', src):
    k, v = m.group(1), m.group(2).encode().decode('unicode_escape').encode('latin-1').decode('utf-8')
    if v:
        lang[P + k] = v
DYN = {
    'tab.campaign': 'Campaign', 'tab.contracts': 'Contracts', 'tab.stores': 'Stores', 'tab.training': 'Training', 'tab.lodge': 'Lodge',
    'tab.ledger': 'Ledger', 'tab.seasons': 'Seasons',
    'title.campaign': "Mara's Campaign", 'title.contracts': 'Contracts', 'title.stores': 'Field Stores', 'title.training': 'Field Training',
    'title.lodge': 'The Lodge', 'title.ledger': 'Expedition Ledger', 'title.seasons': 'Seasons & Tags',
    'training.marksman': 'Marksman', 'training.tracker': 'Tracker', 'training.survivalist': 'Survivalist', 'training.bowfisher': 'Bowfisher',
    'training.marksman.task': 'Challenge: two heart/lung recoveries shot from 12 m or more.',
    'training.marksman.perk': 'Each rank: 8 % less shot spread.',
    'training.tracker.task': 'Challenge: inspect three wildlife clues.',
    'training.tracker.perk': 'Each rank: +0.6 blocks clue inspection range.',
    'training.survivalist.task': 'Challenge: craft three Frontier Hunts items.',
    'training.survivalist.perk': 'Each rank: slow healing while fed near an expedition station.',
    'training.bowfisher.task': 'Challenge: land two fish.',
    'training.bowfisher.perk': 'Each rank: fish tire faster on the line.',
    'season.spring': 'Spring', 'season.summer': 'Summer', 'season.fall': 'Fall', 'season.winter': 'Winter',
}
for k, v in DYN.items():
    lang.setdefault(P + k, v)
# every L("...") key used in the source must exist (dynamic prefixes are covered above)
used = set(re.findall(r'\bL\(\s*"([a-z0-9_.]+)"', src))
missing = [k for k in used if P + k not in lang and not k.endswith(".")]
assert not missing, missing
os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump(dict(sorted(lang.items())), open(OUT, 'w'), indent=1, ensure_ascii=False)
print(OUT, len(lang), 'keys')
