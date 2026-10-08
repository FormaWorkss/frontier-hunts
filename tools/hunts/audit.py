#!/usr/bin/env python3
"""[hunts] Offline achievability audit of every species-hunt milestone, against a built jar.

python3 tools/hunts/audit.py <jar> <repo> [out.md]

For every milestone (docs/ws/hunts/milestones.json, written by tools/hunts/harness/run.sh):
  * the species spawns naturally in survival: its spawn biome modifier is in the jar and points at a non-empty biome tag
    (whitetail / elk / moose also need the whitetailsOutsideReserve config, default true; wildlife always);
  * the gameplay hook that reports the event is compiled into the jar (constant-pool evidence in the calling class);
  * every item it needs and its reward item are obtainable in survival (tools/recipe_audit.py over the same jar);
and writes the table (milestone -> rule -> hook -> needs -> reward -> spawn) to docs/ws/hunts/audit.md.
Exit 1 on any failure.
"""
import json
import os
import subprocess
import sys
import zipfile

JAR, REPO = sys.argv[1], sys.argv[2]
OUT = sys.argv[3] if len(sys.argv) > 3 else os.path.join(REPO, 'docs/ws/hunts/audit.md')
z = zipfile.ZipFile(JAR)
names = set(z.namelist())
MS = json.load(open(os.path.join(REPO, 'docs/ws/hunts/milestones.json')))
fails = []

P = 'com/formaworks/frontierhunts/'


def cls_has(path, *tokens):
    try:
        b = z.read(P + path + '.class')
    except KeyError:
        return False
    return all(t.encode() in b for t in tokens)


# hook name -> (what reports it, evidence: class + constant-pool tokens that must all be present)
HOOKS = {
    'take': ('Deer: field dressing (Whitetail.harvest -> JournalHooks.deerHarvest -> HuntHooks.deerTaken). Wildlife: death credited '
             'to the shooter (LivingDeathEvent -> JournalHooks.wildlifeDeath -> HuntHooks.wildlifeTaken); shot context from the first hit '
             '(JournalHooks.deerHit / wildlifeHit -> HuntHooks.hit)',
             [('journal/JournalHooks', 'HuntHooks', 'deerTaken', 'wildlifeTaken', 'hit'), ('hunting/Whitetail', 'deerHarvest', 'deerHit'),
              ('journal/JournalEvents', 'wildlifeDeath', 'wildlifeHit')]),
    'seen': ('Sighting within 40 m in clear view, once per animal per session (JournalEvents.sightings -> HuntHooks.seen)',
             [('journal/JournalEvents', 'HuntHooks', 'seen')]),
    'trailcam': ('Trail camera photo (TrailCamera -> JournalHooks.trailcamPhoto -> HuntHooks.photo), works with the owner offline',
                 [('journal/JournalHooks', 'HuntHooks', 'photo'), ('expedition/TrailCamera', 'trailcamPhoto')]),
    'glass': ('Binoculars / rangefinder held steady on the animal ~0.7 s with clear line of sight (HuntEvents.glassing -> HuntHooks.glassed)',
              [('hunts/HuntEvents', 'glassing', 'frontierhunts:binoculars', 'frontierhunts:rangefinder'), ('hunts/HuntHooks', 'glassed')]),
    'flush': ('A grouse bursts into the air within 16 blocks (HuntEvents.flushes -> HuntHooks.flushed)', [('hunts/HuntEvents', 'flushes'), ('hunts/HuntHooks', 'flushed')]),
    'deercall': ('Grunt tube / bleat call / rattling antlers answered (GameCalls -> Whitetail.approachCall -> JournalHooks.called stamps the deer); '
                 'a take within 5 min of the stamp is CALLED', [('hunting/Whitetail', 'called'), ('journal/JournalHooks', 'frontierhunts_journal_call'),
                                                               ('hunts/HuntContext', 'frontierhunts_journal_call')]),
    'predatorcall': ('Predator Locator Call use (HuntEvents.useItem -> Lures.blow); predators answer through LureGoal and are stamped',
                     [('hunts/HuntEvents', 'frontierhunts:predator_call'), ('hunts/LureGoal', 'startCall'), ('hunts/Lures', 'heard')]),
    'bait': ('Bait used on the ground sets a bait pile (HuntEvents.useItem -> HuntStore.bait); bears / hogs / cats visit through LureGoal and are stamped',
             [('hunts/HuntEvents', 'frontierhunts:bait'), ('hunts/LureGoal', 'arriveAtBait'), ('hunts/HuntStore', 'baitsNear')]),
    'decoy': ('Mallard Decoy spread within 24 blocks or a duck that came to the Duck Call (DecoyBlock registry, DuckCallItem -> Lures, LureGoal flights)',
              [('hunts/DecoyBlock', 'decoyPlaced'), ('hunts/DuckCallItem', 'blow'), ('hunts/LureGoal', 'startFlight')]),
    'hound': ('The hunter\'s tracking hound trails / bays the animal (TrackingHound.quarry, checked at the first hit and at the take)',
              [('tracking/hound/TrackingHound', 'quarry'), ('hunts/HuntContext', 'quarry')]),
}

hook_ok = {}
for h, (_, ev) in HOOKS.items():
    ok = all(cls_has(c, *t) for c, *t in ev)
    hook_ok[h] = ok
    if not ok:
        fails.append('hook %s: evidence missing %s' % (h, [(c, t) for c, *t in ev if not cls_has(c, *t)]))

# spawn rules
SPAWN = {}
for m in MS:
    sp, tag = m['species'], m['spawn']
    if sp in SPAWN:
        continue
    ns, path = tag.split(':')
    mod = {'whitetail': 'whitetail_spawns', 'elk': 'elk_spawns', 'moose': 'moose_spawns'}.get(sp, 'wildlife2026/' + sp)
    bm = 'data/frontierhunts/neoforge/biome_modifier/%s.json' % mod
    tj = 'data/%s/tags/worldgen/biome/%s.json' % (ns, path)
    ok = bm in names and tj in names
    info = ''
    if ok:
        b = json.loads(z.read(bm))
        t = json.loads(z.read(tj))
        vals = t.get('values', [])
        ok = b.get('biomes') == '#' + tag and b['spawners']['type'] == 'frontierhunts:' + sp and len(vals) > 0
        vanilla = [v for v in vals if isinstance(v, str) and v.startswith(('minecraft:', '#minecraft:'))]
        info = 'weight %s, groups %s-%s; %d biome entries (%s%s)' % (b['spawners']['weight'], b['spawners']['minCount'], b['spawners']['maxCount'],
                                                                  len(vals), ', '.join(vanilla[:4]), '…' if len(vanilla) > 4 else '')
        if not vanilla:
            ok = False
            info += ' - NO VANILLA BIOME'
    SPAWN[sp] = (ok, info)
    if not ok:
        fails.append('spawn %s: %s %s' % (sp, bm, info))

# obtainability through the recipe audit
aj = '/tmp/claude-0/hunts/audit_recipes.json'
os.makedirs(os.path.dirname(aj), exist_ok=True)
subprocess.run([sys.executable, os.path.join(REPO, 'tools/recipe_audit.py'), JAR, '--json', aj], capture_output=True, text=True)
rows = {r['item']: r for r in json.load(open(aj))['rows']}


def obtain(item):
    r = rows.get(item)
    if r is None:
        return False, 'not an item'
    return r['status'] == 'OK', r['via'] or r['status']


lines = ['# Species hunts - achievability audit', '',
         'Generated by `tools/hunts/audit.py` from the built jar `%s` and `docs/ws/hunts/milestones.json` (harness).' % os.path.basename(JAR), '',
         '## Event hooks', '', '| hook | what reports it | compiled in jar |', '|---|---|---|']
for h, (desc, _) in HOOKS.items():
    lines.append('| `%s` | %s | %s |' % (h, desc, 'yes' if hook_ok[h] else '**MISSING**'))
lines += ['', '## Natural spawns (survival worlds)', '', '| species | spawn rule | ok |', '|---|---|---|']
for sp, (ok, info) in SPAWN.items():
    lines.append('| %s | %s | %s |' % (sp, info, 'yes' if ok else '**NO**'))
lines += ['', 'Whitetail, elk and moose spawn outside Frontier reserve worlds while `whitetailsOutsideReserve` is true (the default). '
          'Natural spawns need light > 8 and solid ground (polar bears: snowy plains / ice spikes / snowy beaches, not open ice).', '',
          '## Milestones', '', '| milestone | rule (as shown in game) | event / hook | needs (survival source) | reward |', '|---|---|---|---|---|']
for m in MS:
    needs = []
    for n in m['needs']:
        alts = []
        ok_any = False
        for it in n.split('|'):
            ok, via = obtain(it)
            ok_any |= ok
            alts.append('%s (%s)' % (it.split(':')[1], 'ok' if ok else 'NO: ' + via))
        if not ok_any:
            fails.append('%s needs %s: none obtainable' % (m['id'], n))
        needs.append(' or '.join(alts))
    if m['item']:
        ok, via = obtain(m['item'])
        if not ok:
            fails.append('%s reward %s not obtainable/registered (%s)' % (m['id'], m['item'], via))
    reward = '+%d XP, %d tokens%s' % (m['xp'], m['tokens'], (', %d× %s' % (m['count'], m['item'].split(':')[1])) if m['item'] else '')
    if m['tier'] == 'master':
        reward += ', master title'
    if not hook_ok.get(m['hook'], False):
        fails.append('%s hook %s missing' % (m['id'], m['hook']))
    if not SPAWN[m['species']][0]:
        fails.append('%s species does not spawn' % m['id'])
    tgt = '' if m['target'] == 1 else (' ×%d%s' % (m['target'], ' in one day' if m['mode'] == 'DAY' else ''))
    lines.append('| `%s` %s%s | %s | %s `%s` | %s | %s |' % (m['id'], m['title'], tgt, m['rule'], m['kind'], m['hook'], '; '.join(needs) or '-', reward))
lines += ['', '## Result', '', ('**PASS** - %d milestones: every species spawns naturally, every hook is in the jar, every needed and reward item is '
                                'obtainable in survival.' % len(MS)) if not fails else '**FAIL**\n\n' + '\n'.join('* ' + f for f in fails), '']
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w').write('\n'.join(lines))
print(OUT)
print('PASS' if not fails else 'FAIL:\n' + '\n'.join(fails))
sys.exit(1 if fails else 0)
