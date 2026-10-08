#!/usr/bin/env python3
"""[livingworld] English text of the living-world sites (signs, notes, books), written to the lang merge fragment
patch/_merge/assets/frontierhunts/lang/en_us.json/livingworld.json.  usage: lang.py <repo>
Sign lines: standing/wall signs <= 15 characters, hanging signs <= 10."""
import json, os, sys
R = sys.argv[1] if len(sys.argv) > 1 else '.'
L = {}


def lines(group, *ls, title=None):
    for i, t in enumerate(ls, 1):
        L[f'frontierhunts.lw.{group}.{i}'] = t
    if title:
        L[f'frontierhunts.lw.{group}.title'] = title


# ------------------------------------------------------------------ spawn
lines('spawn_sign', 'RANGER', '%s m %s', 'STATION')
for k, v in {'north': 'N', 'north_east': 'NE', 'east': 'E', 'south_east': 'SE', 'south': 'S', 'south_west': 'SW', 'west': 'W', 'north_west': 'NW'}.items():
    L[f'frontierhunts.lw.dir.{k}'] = v

# ------------------------------------------------------------------ hunting camp
lines('camp_rules.0', 'CAMP RULES', 'Guns unloaded', 'Meat hangs high', 'Pack it out')
lines('camp_rules.1', 'DEER CAMP', 'Last one up', 'makes coffee', 'No bears inside')
lines('camp_rules.2', 'HUNTERS CAMP', 'Keep the fire', 'small & tended', 'Respect the elk')
lines('note.camp.0', 'Big buck crossed the creek at dawn.', 'Tall tines, heavy on the left side.', 'Back stand tomorrow, wind out of the west.',
      title='Note from the meat pole')
lines('note.camp.1', 'Gone to glass the far ridge.', 'Coffee is in the blue tin.', 'Back before dark. Leave the lantern lit.', title='Note on the table')
lines('note.camp.2', 'Dragged one in after dark, hung it.', 'Needs skinning first thing.', 'Salt is low, bring more from the post.',
      title='Scrawled note')
lines('note.camp.3', 'Scrape line on the creek bottom', 'is getting hit every night.', 'Trail cam on the big pine, check card.',
      title='Scouting notes')
lines('book.camplog',
      'DEER CAMP LOG\n\nOpening morning. Frost on the tents, fog in the bottoms. Three of us on stands before first light.',
      'Day 2\nSaw does all morning, one small buck chasing. Wind swirled in the afternoon. Spooked a big one off the field edge.',
      'Day 4\nRattled one in at noon of all times. Clean shot, short track. He is on the pole now, a fine eight.',
      'Last day\nCold front coming. Pack the tents dry if you can. Whoever finds this: the camp post is yours to claim. Good hunting.',
      title='Deer Camp Log')

# ------------------------------------------------------------------ abandoned
lines('note.abandoned.0', 'Something tore into the cooler again.', 'Tracks the size of a dinner plate.', "We're moving camp in the morning.",
      title='Water-stained note')
lines('note.abandoned.1', 'Storm took the big tent last night.', 'Couldn\'t find Jim\'s rifle anywhere.', 'Heading down to the post for help.',
      title='Faded note')
lines('note.abandoned.2', 'If you are reading this, the trail', 'down the east ridge washed out.', 'Go around by the river.',
      title='Note pinned to a pole')
lines('book.lastentries',
      'Oct. 3\nSet up on the bench above the creek. Good water, good sign everywhere. Elk bugling all night.',
      'Oct. 6\nBear in camp at 2 am. Banged pots till it left. Moved the meat pole farther out.',
      'Oct. 9\nSnow. A lot of it. Can\'t see the ridge. Fire won\'t keep. We\'ll try for the post at first light.',
      'The rest of the pages are blank.',
      title='Last Entries')
lines('abandoned_sign.0', 'CAMP CLOSED', 'BEAR ACTIVITY', 'Store food', 'properly')
lines('abandoned_sign.1', 'GONE FOR', 'HELP - BACK', 'IN 2 DAYS', '')

# ------------------------------------------------------------------ outfitter / trading post
lines('outfitter_sign', 'OUTFITTER', 'Gear-Guides', 'Licenses')
lines('outfitter_board', 'Daily contracts', 'Ask at the', 'counter', '')
lines('outfitter_hours', 'OPEN', 'Dawn to dusk', 'Ring the bell', 'if nobody\'s in')
lines('note.outfitter.0', 'Wanted: prime winter pelts.', 'Fair prices paid at the counter.', 'No summer hides, they slip.', title='Notice')
lines('note.outfitter.1', 'Guide wanted for the high basin.', 'Must know elk and keep quiet.', 'Ask for the contract board.', title='Help wanted')

# ------------------------------------------------------------------ ranger station
lines('ranger_sign', 'RANGER', 'STATION')
lines('ranger_check', 'GAME CHECK', 'All harvests', 'must be shown', 'here')
lines('ranger_rules', 'RESERVE RULES', 'ID your target', 'Tag your game', 'Fire danger LOW')
lines('ranger_handbook', 'NEW HUNTERS', 'Take a', 'Frontier', 'Handbook')
lines('book.rangerlog',
      'RANGER STATION LOGBOOK\n\nWelcome to the reserve. New hunters: take a Frontier Handbook from the box by the door. It walks you through everything.',
      'CHECK-IN\nBring every harvest here to be weighed and recorded. The big-buck board out front keeps the season\'s best.',
      'SAFETY\nKnow what is behind your target. Wear orange during rifle season. Tell someone where you are hunting.',
      'NOTES\nBear seen near the north creek. Elk moving down from the high basin. Duck flights heavy at the marsh at dawn.',
      title='Ranger Station Logbook')
lines('note.ranger.0', 'Patrol: north creek, bear sign fresh.', 'Advise hunters to hang meat high.', 'Check again Thursday.', title='Patrol report')
lines('note.ranger.1', 'Harvest tally this week:', '7 whitetail, 2 elk, 11 ducks.', 'One wounding loss, recovered next day.', title='Tally sheet')
lines('note.ranger.2', 'Lookout reports smoke to the east,', 'turned out to be a camp fire.', 'Remind camps to keep fires small.', title='Radio log')

# ------------------------------------------------------------------ trapper
lines('trapper_sign', 'FURS', 'TRADED', 'HERE')
lines('note.trapper.0', 'Line runs up the creek and back', 'over the beaver ponds. 22 sets.', 'Check every second morning.', title='Trap line')
lines('note.trapper.1', 'Marten are thick up high this year.', 'Fox pelts prime by first snow.', 'Salt the hides, don\'t sun them.', title='Trapper\'s notes')

# ------------------------------------------------------------------ meat shed
lines('meat_shed_sign', 'MEAT SHED', 'Hang it cool', 'Keep it clean')
# ------------------------------------------------------------------ elk camp
lines('elk_sign', 'ELK CAMP', 'High Basin', 'Outfit')
lines('note.elk.0', 'Bulls bugling in the dark timber', 'above the basin. Mules need water', 'at the spring before the climb.', title='Elk camp notes')
lines('note.elk.1', 'Packed out two quarters today.', 'Two more and the rack tomorrow.', 'Keep the mules off the creek bank.', title='Packer\'s note')
lines('book.elkcamp',
      'ELK CAMP\n\nRode in four hours from the trailhead. Wall tents up by dark, stove going. Cold and clear.',
      'Day 2\nBugled from the saddle at first light. Three bulls answered. Closed to 300 yards but the wind turned.',
      'Day 3\nBig six-point in the meadow at last light. One shot. Quartered him by lantern, mules packed at dawn.',
      'Day 5\nSnow coming. Breaking camp. Leave the hay for whoever comes next.',
      title='Elk Camp Journal')
# ------------------------------------------------------------------ blinds and stands
lines('stand_sign', 'STAND 3', 'Wind: W/NW', 'Sign in at', 'the board')
lines('blind_sign', 'BLIND', 'Quiet', 'please')
lines('duck_sign', 'DUCK', 'BLIND', 'No wake')
lines('plot_sign', 'FOOD PLOT', 'Clover - oats', 'turnips', 'Do not drive')
# ------------------------------------------------------------------ glassing point
lines('glass_sign', 'GLASSING', 'POINT')
lines('glass_view', 'Basin - north', 'Elk meadow - E', 'Burn - south', 'Creek - west')
# ------------------------------------------------------------------ trailhead
lines('trail_sign', 'TRAILHEAD')
lines('trail_board', 'TRAIL REGISTER', 'Sign in & out', 'Carry water', 'Bears active')
lines('trail_dirs', 'Ridge 2 km', 'Marsh 1 km', 'Basin 4 km', '')
lines('note.trail.0', 'Register: party of 2, deer, 3 days.', 'Party of 1, elk, out by Sunday.', 'Party of 3, ducks, day trip.', title='Trail register page')
lines('note.trail.1', 'Bridge over the creek is out.', 'Ford is knee deep at the bend.', 'Use the upper crossing in spring.', title='Trail notice')
# ------------------------------------------------------------------ relics
lines('fence_sign', 'NO HUNTING', 'Old homestead', '1912')
lines('cache_sign', 'SHED PILE', 'Take one', 'leave one')

out = os.path.join(R, 'patch/_merge/assets/frontierhunts/lang/en_us.json/livingworld.json')
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'w', encoding='utf-8') as f:
    json.dump(L, f, indent=1, ensure_ascii=False)
# sign width check (rough: Minecraft default font ~6 px per char incl. spacing; sign 90 px, hanging sign 60 px)
for k, v in L.items():
    if '.title' in k or k.startswith('frontierhunts.lw.note') or k.startswith('frontierhunts.lw.book'):
        continue
    if len(v) > 15:
        print('long sign line?', k, v)
print(len(L), 'keys ->', out)
