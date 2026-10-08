"""[academy] Writes patch/_merge/assets/frontierhunts/lang/en_us.json/academy.json (English strings)."""
import json, os, sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
L = {}
A = 'academy.frontierhunts.'
def k(key, v): L[A + key] = v

courses = {
 'glassing': ('Glass & Call', 'Find them before they find you, then bring the buck to you.',
   'A ranger spends more time behind glass than behind a rifle. From the lookout, sweep the meadow with your binoculars until you have found all three deer - hold steady on each until you are sure. Range the buck so you know the shot, then blow the grunt tube or the bleat and let him come to you. Most hunts are won right here, before anyone moves.',
   'Binoculars · Rangefinder · Grunt tube · Doe bleat call · Wind checker',
   {'spot': 'Find all three deer through the binoculars', 'range': 'Range the buck with the rangefinder', 'call': 'Call the buck in to within 25 m of the tower'}),
 'stalk': ('The Stalk', 'Close the distance. The wind decides, cover hides you, patience does the rest.',
   'A doe is feeding out in the open meadow. Check the wind first - she will smell you long before she sees you if it carries your scent to her. Crouch, move while her head is down, use every shrub and log, and get within 15 metres. Then hold still for three seconds while she stays calm. If she catches you, you are walked back to the start mark and a new doe settles in.',
   'Woodland ghillie suit (worn) · Wind checker · Binoculars · 2 × scent cover',
   {'close': 'Get within 15 m of the doe while she stays calm', 'hold': 'Hold there, unseen, for 3 seconds'}),
 'range': ('The Range', 'Paper to a hundred metres, then a clean heart-lung shot on the 3D lanes.',
   'Every shot at a living animal is a promise to make it count. Shoot from the covered firing line: put rounds on paper at 25 and 50 metres, read the flags and hold for the wind at 100, then place a heart or lung shot on each 3D deer lane. A vital hit drops the deer with the kill cam; anything else is called out and the lane resets. Shots from in front of the firing line do not count.',
   'Ridgeline bolt rifle (3-9× scope, loaded) · 40 × .308 · Field recurve bow · 24 arrows · Rangefinder · Wind checker',
   {'paper25': 'Paper · 25 m: three hits on the target', 'paper50': 'Paper · 50 m: two hits in the blue or better', 'paper100': 'Paper · 100 m: one hit - hold for the wind',
    'deer50': '3D lane · 50 m: a heart or lung shot', 'deer100': '3D lane · 100 m: a heart or lung shot'}),
 'tracking': ('The Blood Trail', 'An hour ago a buck was hit behind the ribs. Find him.',
   'Read the hit site first: the colour and texture of the blood tell you where the animal was hit and how long to wait. Then follow the drops through the pines - aim at each mark and press Use to read it. Where the trail goes quiet, stop at the last blood and search in widening circles. A liver-hit deer often lies down not far ahead: find the bed, then recover the buck.',
   'Binoculars · Wind checker · Field knife',
   {'hitsite': 'Read the blood at the hit site', 'follow': 'Follow the trail: read 4 more blood marks', 'bed': 'Find the bed where it lay down', 'recover': 'Recover the buck'}),
 'dressing': ('Field Dressing', 'A clean kill deserves clean work. Respect the animal, waste nothing.',
   'A buck lies at the edge of camp, dropped by a lung shot. Read the blood beside it first - pink and frothy means lungs, a quick and ethical kill. Then take the Contour Skinning Knife in your main hand, use the carcass and stay with it until the work is done; walking away pauses it. In training nothing is kept: this is about the method.',
   'Contour Skinning Knife · Field knife',
   {'read': 'Read the blood beside the buck', 'dress': 'Field-dress the buck with the skinning knife'}),
}
for key, (title, tag, brief, kit, objs) in courses.items():
    k(f'course.{key}.title', title); k(f'course.{key}.tagline', tag); k(f'course.{key}.brief', brief); k(f'course.{key}.kit', kit)
    for o, v in objs.items(): k(f'course.{key}.objective.{o}', v)

k('lent', 'Range issue · returned when you leave the training grounds')
for k2, v in {'glassing': 'LOOKOUT · glass, range, call', 'stalk': 'START MARK · watch the wind', 'paper25': '25 m', 'paper50': '50 m', 'paper100': '100 m',
              'deer50': 'DEER · 50 m', 'deer100': 'DEER · 100 m', 'firing_line': 'FIRING LINE', 'hit_site': 'HIT SITE · buck, quartering away',
              'dressing': 'FIELD DRESSING · knife in hand, use the carcass'}.items():
    k('sign.' + k2, v)
k('glassing.spotted', 'Spotted: %s at %s m'); k('glassing.ranged', 'Buck ranged: %s m'); k('glassing.came_in', 'He came in to %s m - well called')
k('glassing.called', 'Call made. Stay still and watch the buck.')
k('stalk.winded', 'She winded you. Get the wind in your face and try again.'); k('stalk.seen', 'She saw you move. Freeze when her head comes up.')
k('stalk.close', 'Inside 15 m (%s m) - now hold still'); k('stalk.held', 'Three seconds at %s m - a perfect stalk')
k('stalk.alert', 'Her head is up. Freeze...')
k('range.off_line', 'Shots only count from behind the firing line.')
k('range.deer_clean', 'Clean %s shot at %s m - down where it stood'); k('range.deer_again', '%s - another clean one')
for r, v in {'liver': 'Liver (%s): fatal, but it would run and need a long track. Aim a hand forward.', 'gut': 'Gut (%s): it would run for hours. Aim tight behind the shoulder.',
             'neck': 'Too high or forward (%s): a wounded animal. Aim at the heart-lung box.', 'leg': 'Leg or shoulder (%s): it would survive wounded. Aim a little back.',
             'body': 'Body (%s): no vitals hit. Aim low behind the front leg.', 'chest': 'Chest (%s), but no clean vital. Aim a little lower.'}.items():
    k('range.deer_' + r, v)
for r, v in {'gold': 'Bullseye! (%s m)', 'red': 'Red ring (%s m)', 'blue': 'Blue ring (%s m)', 'black': 'Black ring (%s m)', 'white': 'On paper (%s m)'}.items():
    k('range.paper_' + r, v)
k('range.paper_low', 'Hit at %s m, outside the blue - tighten the group')
k('tracking.bed', 'The bed: it lay here, bleeding. It is close now.'); k('tracking.site', 'Hit site read: dark, steady drops - liver')
k('tracking.read', 'Good read · %s / %s'); k('tracking.recovered', 'Recovered after about %s m of trail')
k('dressing.read', 'Pink and frothy: lungs. A quick, ethical kill.'); k('dressing.done', 'Field dressed (%s kg). Clean work.')
for r, v in {'disabled': 'The Ranger Academy is turned off on this server.', 'missing': 'The training grounds are not available on this server.',
             'state': 'You cannot leave for training right now.', 'busy': 'You are already in training.', 'riding': 'Dismount and wake up first.',
             'ground': 'Stand on solid ground first.', 'hurt': 'Not while something is attacking you.', 'cooldown': 'One moment...',
             'full': 'Every training plot is in use. Try again shortly.', 'failed': 'Could not reach the training grounds. Nothing was changed.'}.items():
    k('refuse.' + r, v)
k('failed.timeout', "TIME'S UP")
k('recovered', 'Your training session was interrupted. Everything is back where you left it.')
k('stranded', 'You were sent back from the training grounds.')
k('left', 'You left the training grounds. Your own gear, XP and health are back.')
H = {'heading': 'Heading to the training grounds · %s', 'returning': 'Heading home', 'eyebrow': 'RANGER ACADEMY · %s / %s',
     'off_line': "In front of the firing line - shots won't count", 'attempts': 'Attempt %s', 'leave': 'Hold %s to leave training',
     'all_done': 'All done', 'wind': 'Wind %s m/s from %s · %s', 'wind_calm': 'Wind calm', 'wind_tail': 'at your back', 'wind_head': 'in your face',
     'wind_to_right': 'blowing left to right', 'wind_to_left': 'blowing right to left'}
for a, v in H.items(): k('hud.' + a, v)
for c, v in {'n': 'N', 'ne': 'NE', 'e': 'E', 'se': 'SE', 's': 'S', 'sw': 'SW', 'w': 'W', 'nw': 'NW'}.items(): k('compass.' + c, v)
k('title.kicker', 'RANGER ACADEMY · COURSE %s OF %s'); k('title.foot', 'Time %s · hold %s to leave')
k('passed.stamp', 'COURSE PASSED'); k('passed.best', 'NEW BEST')
k('home.title', 'Ranger Academy'); k('home.passed', 'Passed in %s'); k('home.best', 'new best')
k('home.rewards', '+%s Ranger XP · +%s %s XP'); k('home.practice', 'Practice run - rewards are paid once a day for repeat passes.')
k('home.certified', 'Certified: %s'); k('home.abandoned', 'Training left early.'); k('home.timeout', 'Time ran out - try again when you are ready.')
k('home.hurt', 'Training stopped.'); k('home.interrupted', 'Training interrupted.')
k('home.safe', 'Your inventory, XP, health and position are exactly as you left them.')
k('cert.0', 'Steady hold'); k('cert.1', 'Field dressing'); k('cert.2', 'Trail ages')
k('cert.0.effect', 'Steady hold: the extra spread from a tiring full-draw bow hold is reduced by 35%%.')
k('cert.1.effect', 'Field dressing: skinning takes 6.4 s instead of 8.')
k('cert.2.effect', 'Trail ages: inspecting sign shows its exact age.')
S = {'title': 'Ranger Services', 'brand': 'RANGER SERVICES', 'stats': '%s tokens · %s Ranger XP', 'certs': '%s / 3 certified',
     'certs_tip': 'Certifications (pass the course)\n%s\n%s\n%s', 'back': 'Hunter\'s Journal', 'waiting': 'Waiting for your record...'}
for a, v in S.items(): k('screen.' + a, v)
for a, v in {'academy': 'Ranger Academy', 'field': 'Field work', 'bounty': 'Bounties', 'supply': 'Supply runs'}.items(): k('group.' + a, v)
for a, v in {'training': 'TRAINING', 'certified': 'CERTIFIED', 'passed': 'PASSED', 'new': 'NEW', 'active': 'ACTIVE', 'ready': 'READY', 'failed': 'FAILED'}.items(): k('chip.' + a, v)
P = {'course_kicker': 'Ranger Academy · course %s of %s · %s min', 'training_now': 'Training now · %s left', 'record': 'Passed %s× · best %s',
     'not_attempted': 'Not attempted yet', 'certified': 'Certified: %s', 'objectives': 'Objectives', 'rewards': 'Rewards',
     'repeat_wait': 'Repeat passes pay a quarter reward once a day (next in %s).', 'repeat_ready': 'A repeat pass now pays a quarter reward.',
     'kit': 'Kit issued at the range', 'how': 'How it works',
     'how_text': 'Begin training and you are taken to your own plot in the training grounds. Your inventory, XP, health, hunger and effects are put safely aside and the course kit is lent to you. Finish, run out of time, or hold %s to leave - you come back to exactly where you stood, with everything as it was.',
     'limit': 'time limit %s', 'no_limit': 'no time limit', 'ready': 'Done - collect your reward', 'failed': 'Time expired - accept again or choose another',
     'active_timed': 'In progress · %s left', 'active': 'In progress', 'cooldown': 'Available again in %s', 'busy': 'Finish your current assignment first',
     'open': 'Open', 'within': 'Within %s of accepting', 'chest': 'Heart or lung shot', 'mass': 'At least %s kg', 'after_accept': 'Only what happens after you accept counts',
     'how_harvest': 'Hunt in Survival in the Overworld: the shot and the field dressing must both be yours, after you accept.',
     'how_track': 'Follow sign from one animal: aim at a print or blood mark and press Use to read it. Marks must be new since you accepted.',
     'how_delivery': 'Trade 4 roasted venison or 2 leather at a Ranger Contract Board.',
     'ineligible': 'Assignments count in Survival in the Overworld.', 'safe_short': 'Your gear and position are kept safe while you train.'}
for a, v in P.items(): k('page.' + a, v)
for a, v in {'ranger': '+%s Ranger XP', 'skill': '+%s %s XP', 'cert': 'Certifies %s', 'tokens': '+%s tokens'}.items(): k('reward.' + a, v)
B = {'leave': 'Leave training', 'leave_confirm': 'Click again to leave', 'closed': 'Academy closed', 'elsewhere': 'Training in progress',
     'departing': 'Departing...', 'begin': 'Begin training', 'claim': 'Collect reward', 'accept': 'Accept assignment', 'in_progress': 'In progress',
     'resting': 'Resting', 'unavailable': 'Unavailable', 'abandon': 'Abandon', 'abandon_confirm': 'Click again'}
for a, v in B.items(): k('button.' + a, v)
L['key.frontierhunts.assignments'] = 'Ranger assignments'
L['key.frontierhunts.leave_training'] = 'Leave training (hold)'
for s, v in {'ring': 'Steel target rings', 'ring_gold': 'Bullseye rings', 'tick': 'Objective done', 'passed': 'Course passed', 'arrive': 'Training grounds',
             'depart': 'Whoosh', 'busted': 'Deer snorts', 'page': 'Dossier page'}.items():
    L['subtitles.frontierhunts.academy.' + s] = v
# percent signs in lang need escaping where formatted with args; plain strings are fine
out = os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json')
os.makedirs(out, exist_ok=True)
json.dump(L, open(os.path.join(out, 'academy.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
print(len(L), 'keys')
