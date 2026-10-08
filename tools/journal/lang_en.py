#!/usr/bin/env python3
"""[journal] Generates the English lang fragment for the Hunter's Journal overhaul and checks it covers every
checklist entry and every key the Java code uses.

python3 tools/journal/lang_en.py [repo]   ->  patch/_merge/assets/frontierhunts/lang/en_us.json/journal.json
"""
import json, os, re, sys

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..'))
P = 'journal.frontierhunts.'
L = {}


def k(key, text):
    L[P + key] = text


# ------------------------------------------------------------------------------------------------ chrome
k('brand1', 'FRONTIER HUNTS')
k('brand2', "Hunter's Journal")
k('loading', 'Opening your journal… (the server sends your record when the journal opens)')
k('side.school', 'Field School')
k('side.tokens', '%s tokens')
for key, text in [('home', 'Home'), ('checklist', 'Checklist'), ('skills', 'Skills'), ('species', 'Species'), ('records', 'Records'),
                  ('notes', 'Field notes'), ('reserve', 'Reserve')]:
    k('tab.' + key, text)
for key, text in [('home', "Hunter's Journal"), ('checklist', 'Checklist'), ('skills', 'Hunter skills'), ('species', 'Species log'),
                  ('records', "Hunter's record"), ('notes', 'Field notes'), ('reserve', 'The reserve')]:
    k('title.' + key, text)
k('sub.home', '%s · the reserve keeps track of every hunt')
k('sub.checklist', '%s of %s done · %s%%')
k('sub.skills', '%s skill XP · skills grow by doing')
k('sub.species', '%s of %s species taken · %s seen · %s photographed')
k('sub.records', 'Every shot, trail and trophy')
k('sub.notes', '%s entries, newest first')
k('sub.reserve', 'Conditions, survey and the expedition')
k('date', '%1$s · %2$s %3$s, reserve year %4$s · %5$s')
for i, m in enumerate(['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']):
    k('month.%d' % i, m)
for s, t in [('spring', 'Spring'), ('summer', 'Summer'), ('fall', 'Fall'), ('winter', 'Winter')]:
    k('season.' + s, t)

# ------------------------------------------------------------------------------------------------ home
k('home.rank', 'RANK %s OF %s')
k('home.xp', '%s XP · %s more to %s')
k('home.xp_top', '%s XP · the highest rank there is')
k('home.rank_tip_head', 'Ranks')
k('home.rank_tip', 'Greenhorn → Woodsman (400 XP) → Tracker (1,500) → Guide (4,000) → Master Hunter (10,000) → Legend (22,000).\n'
  'Every skill XP and every checklist reward counts toward your rank.')
k('home.skills_tip_head', 'Skills')
k('home.skills_tip', 'Five skills that grow as you hunt. Click to see levels and perks.')
k('home.checklist', 'CHECKLIST')
k('home.checklist_value', '%s / %s · %s%%')
k('home.next', 'Up next')
k('home.next_school', 'Field School · lesson %s of 7')
k('home.next_campaign', "Mara's campaign · report %s of %s")
k('home.contract_active', 'Ranger contract · in progress')
k('home.contract_ready', 'Ranger contract · ready to collect at the board')
k('home.recent', 'Recent')

# ------------------------------------------------------------------------------------------------ checklist
for key, text in [('all', 'All'), ('species', 'Species'), ('hunting', 'Hunting'), ('gear', 'Gear'), ('world', 'World'), ('camp', 'Camp'),
                  ('survival', 'Survival'), ('school', 'Field School')]:
    k('cat.' + key, text)
k('check.done', 'Done')
k('check.lesson', 'Lesson %s')
k('check.lesson_tip', 'Click to open this Field School lesson.')
k('check.reward', 'Reward: %s XP')
k('check.species', 'Take a %s')
k('check.species.hint', 'Harvest a %s with your own shot. Deer, elk and moose count when you field-dress them; other game when it falls.')

C = {
    # species group
    'species_8': ('Take 8 different species', 'Any eight species from the species log, by your own shot.'),
    'species_all': ('Every species on the list', 'Take one of every species in the species log.'),
    'species_photo5': ('Photograph 5 species', 'Trail camera photos of five different species (your own cameras).'),
    # hunting
    'clean_1': ('A clean kill', 'Drop an animal where it stands with a heart or lung shot — the kill cam plays.'),
    'clean_10': ('10 clean kills', 'Ten heart or lung shots that drop the animal on the spot.'),
    'clean_50': ('50 clean kills', 'Fifty animals dropped on the spot.'),
    'shot_50': ('A 50 m shot', 'Bring down an animal from 50 metres or more.'),
    'shot_100': ('A 100 m shot', 'Bring down an animal from 100 metres or more.'),
    'shot_200': ('A 200 m shot', 'Bring down an animal from 200 metres. A scope and a steady rest help.'),
    'shot_300': ('A 300 m shot', 'Bring down an animal from 300 metres. Go prone and read the wind.'),
    'bow_1': ('A bow kill', 'Take an animal with a bow or crossbow.'),
    'bow_30': ('A 30 m bow kill', 'Take an animal with a bow from 30 metres or more.'),
    'recover_1': ('Follow a blood trail', 'Recover an animal that ran after the shot by following its blood and tracks.'),
    'recover_10': ('10 tracked recoveries', 'Recover ten animals that ran after the shot.'),
    'trail_75': ('A 75 m blood trail', 'Recover an animal 75 metres or more from where you hit it.'),
    'signs_25': ('Read 25 signs', 'Inspect prints, blood, droppings, rubs and other sign (aim and press use).'),
    'stalk_12': ('Get within 12 m unseen', 'Close to 12 metres of an animal that has not noticed you and hold there a few seconds.'),
    'stalk_6': ('Get within 6 m unseen', 'Close to 6 metres of an unaware animal. Crouch, move into the wind.'),
    'unaware': ('It never knew you were there', 'Take an animal that was calm and unaware when you shot.'),
    'call': ('Call one in', 'Use a grunt tube, bleat call or rattling antlers until a deer, elk or moose comes to the call.'),
    'called_kill': ('Take an animal you called', 'Harvest an animal within five minutes of calling it in.'),
    'trophy_130': ('A 130" whitetail', 'Harvest a whitetail buck scoring 130 inches or more.'),
    'trophy_160': ('A 160" whitetail', 'Harvest a whitetail buck scoring 160 inches or more — a true giant.'),
    'harvest_25': ('Field-dress 25 animals', 'Skin and recover 25 animals with a skinning knife (or take 25 game animals).'),
    # gear
    'gear_bow': ('A hunting bow', 'Carry a field bow, recurve, compound or crossbow.'),
    'gear_rifle': ('A hunting rifle', 'Carry the Ridgeline bolt rifle or another hunting rifle.'),
    'gear_scope': ('Glass on the rifle', 'Own a riflescope or optic (attachment workbench).'),
    'gear_prone': ('Shoot from prone', 'Go prone (default Z) and take a shot — the Ridgeline\'s bipod unfolds and the sway settles.'),
    'gear_glass': ('Binoculars', 'Carry binoculars or a rangefinder and glass before you walk.'),
    'gear_wind': ('A wind checker', 'Carry a wind checker to see where your scent goes.'),
    'gear_call': ('A game call', 'Carry a grunt tube, deer caller, bleat call, rattling antlers or predator call.'),
    'gear_ghillie': ('Camouflage', 'Own a ghillie or carbon scent-control piece (clothing workbench).'),
    'gear_knife': ('A skinning knife', 'Carry the Contour Skinning Knife (weapons workbench).'),
    'placed_trail_camera': ('Hang a trail camera', 'Place a trail camera on a trail or near sign.'),
    'gear_stand': ('Sit a tree stand', 'Climb into a tree stand seat.'),
    'placed_blind': ('Set up a blind', 'Place a hub ground blind or a tower blind.'),
    'placed_tent': ('Pitch a tent', 'Pitch any tent — your camp away from camp.'),
    'gear_atv': ('Ride the ATV', 'Climb onto an ATV.'),
    'gear_jerry_can': ('Carry fuel', 'Own a jerry can to keep the ATV running.'),
    'gear_cargo_box': ('Rig the ATV', 'Own an ATV cargo box or can carrier.'),
    'gear_hound': ('A tracking hound', 'Own a hound lead — a hound can follow blood you cannot see.'),
    'gear_pack': ('Pack for the field', "Own a hunter's field pack or quiver."),
    # world
    'biomes_10': ('Survey 10 habitats', 'Visit ten different biomes in the reserve.'),
    'bone_site': ('Find a bone site', 'Find old bones or a skull in the wild and look closely (use on it).'),
    'shed': ('Find a shed antler', 'Find an antler a buck or bull dropped and pick it up or look at it.'),
    'predator_kill': ('Find a predator kill', 'Come upon the remains of an animal taken by wolves, a cougar or a bear.'),
    'rub': ('Read a rub or scrape', 'Use a buck rub or scrape to read who made it and when.'),
    'seasons': ('Hunt all four seasons', 'Spend time in the field in spring, summer, fall and winter.'),
    'blizzard': ('Endure a blizzard', 'Stay out in a blizzard for a full minute.'),
    'winter': ('Survive a winter', 'Spend 30 minutes outdoors in winter.'),
    'walk_10': ('10 km on foot', 'Cover ten kilometres on foot in the reserve.'),
    'photos_50': ('50 trail camera photos', 'Your trail cameras catch fifty photos of wildlife.'),
    # camp
    'camp_join': ('Join a hunting camp', 'Found a camp with a Camp Post or accept an invite (/camp).'),
    'camp_outfit': ('Outfit the camp', 'Raise your camp to rank 1 or higher (Outfit the camp).'),
    'guided': ('A guided hunt', 'Fill a guided-hunt contract, as the guide or the client.'),
    'board': ('On the Big-Buck Board', 'Make the top ten of any record-book board this season.'),
    'event': ('A weekend podium', 'Finish in the top three of a weekend event.'),
    'assist': ('There for a campmate', 'Be within 96 m when a campmate takes an animal.'),
    'placed_plinth': ('Display a trophy', 'Place a trophy plinth for your mounts.'),
    'provisions_5': ('Supply the rangers', 'Deliver provisions to a ranger board five times.'),
    # survival
    'eat_game': ('Eat what you took', 'Eat wild game: venison, backstrap, bear meat, wild fowl, jerky or pemmican.'),
    'gear_cooked': ('Cook the meat', 'Cook or smoke venison or backstrap (smokehouse or fire).'),
    'gear_tanned': ('Tan a hide', 'Turn a hide into tanned buckskin at the tanning rack.'),
    'gear_aged': ('Age a quarter', 'Hang a venison quarter on a game pole until it is aged.'),
    # [integ4] Frontier Survival
    'wear_furs': ('Dress for the cold', 'Wear a hide or fur garment (buckskin, fur hat, mukluks, a bear-fur coat or robe) or anything with a fur lining sewn in.'),
    'preserve_8': ('Put meat by', 'Preserve 8 pieces of meat: jerky from the drying rack, smoked meat from the smokehouse, salt-cured meat or pemmican.'),
    'winter_night': ('Through a winter night', 'Be out in the reserve at dusk on a winter night and still be alive at dawn. A hide bedroll helps.'),
    # field school / expedition
    'fieldschool': ('Graduate Field School', 'Finish the seven Field School lessons (H).'),
    'campaign': ("Finish Mara's campaign", 'Complete all nine reports of the Expedition Guide campaign (N).'),
    'contracts_3': ('Three ranger contracts', 'Complete three timed contracts from the Expedition Guide.'),
}
for cid, (t, h) in C.items():
    k('check.' + cid, t)
    k('check.%s.hint' % cid, h)

# ------------------------------------------------------------------------------------------------ skills, perks, ranks
S = {
    'marksmanship': ('Marksmanship', 'Trained by clean heart and lung kills — more for a longer ethical shot (bows 10-37 m, rifles 30-270 m).'),
    'stalking': ('Stalking', 'Trained by getting close to unaware animals, calling them in, and taking animals that never knew you were there.'),
    'tracking': ('Tracking', 'Trained by reading sign, recovering animals that ran (more for longer trails) and trail camera photos of new species.'),
    'butchery': ('Butchery', 'Trained by field dressing and recovering game — bigger animals teach more.'),
    'woodcraft': ('Woodcraft', 'Trained by time and travel in the wild: new habitats and seasons, blizzards, winter, bone sites and kill sites, helping campmates and guiding.'),
}
for key, (n, how) in S.items():
    k('skill.%s.name' % key, n)
    k('skill.%s.how' % key, how)
PK = {
    'steady_hands': ('Steady Hands', 'Rifle hold drift through the scope is 20% smaller.'),
    'quick_settle': ('Quick Settle', 'The extra wobble right after you raise the rifle is halved.'),
    'controlled_breath': ('Controlled Breath', 'Breathing sway is 35% smaller and drift a further 15% smaller.'),
    'soft_steps': ('Soft Steps', 'Deer, elk and moose hear you moving 20% less far; other game 10%.'),
    'low_profile': ('Low Profile', 'Moving in the open is 15% less noticeable to deer, elk and moose.'),
    'ghost': ('Ghost', 'Your scent carries 25% less and you are a further 20% harder to hear.'),
    'keen_eye': ('Keen Eye', 'Sign and blood show up from 25% farther away.'),
    'trail_sense': ('Trail Sense', 'Inspecting blood or prints tells you which way the animal went.'),
    'bloodhound': ('Bloodhound', 'Inspecting fresh sign tells you roughly how far ahead the animal is (up to 128 m).'),
    'clean_cuts': ('Clean Cuts', '25% more venison (at least one more) from deer, elk and moose.'),
    'quick_knife': ('Quick Knife', 'Field dressing is 30% faster.'),
    'master_skinner': ('Master Skinner', 'One more hide and one more venison quarter from every deer, elk and moose.'),
    'trail_legs': ('Trail Legs', 'Moving outdoors makes you 15% less hungry (and drains Survival nutrition less).'),
    'provider': ('Provider', 'Wild game you eat restores 2 more hunger and extra saturation (and 25% more protein, fat and energy with Survival).'),
    'thick_skin': ('Thick Skin', 'Freezing builds 40% slower (and cold exposure 30% slower with Survival).'),
}
for key, (n, d) in PK.items():
    k('perk.%s.name' % key, n)
    k('perk.%s.desc' % key, d)
k('perk.read.heading', 'Trail Sense · the animal went %s')
k('perk.read.range', 'Bloodhound · the animal is %s')
for key, n in [('greenhorn', 'Greenhorn'), ('woodsman', 'Woodsman'), ('tracker', 'Tracker'), ('guide', 'Guide'), ('master_hunter', 'Master Hunter'),
               ('legend', 'Legend')]:
    k('rank.' + key, n)
k('skills.intro', 'Skills grow by doing — every one levels to 10. Perks unlock on their own at levels 2, 5 and 8 and work right away.')
k('skills.level', 'Level %s')
k('skills.xp', '%s / %s XP to level %s')
k('skills.max', '%s XP · mastered')
k('skills.at', 'Lv %s')
k('skills.off', 'Off on this server')

# ------------------------------------------------------------------------------------------------ species, records, notes
k('species.name', 'SPECIES')
k('species.seen', 'SEEN')
k('species.photos', 'PHOTOS')
k('species.taken', 'TAKEN')
k('species.best', 'BEST')
k('species.first', 'FIRST TAKEN')
k('species.tip', 'Seen %s · photographed %s · taken %s')
k('species.tip_shot', 'Longest shot: %s m')
k('species.tip_score', 'Best rack: %s"')
k('species.tip_weight', 'Heaviest: %s')
k('species.tip_first', 'First taken: %s')
k('species.tip_unknown', 'Not yet observed. Glass open country at dawn and dusk, or hang a trail camera.')
k('species.footer', '"Seen" counts each animal you had in clear view once. Best is the top score for antlered game, else the heaviest.')
k('records.shooting', 'Shooting')
k('records.tracking', 'Tracking & fieldcraft')
k('records.trophies', 'Trophy room')
k('records.seasons', 'Season summaries')
k('records.no_seasons', 'A summary of each reserve season is written here when the season turns.')
k('records.best_rack', 'Best %s rack')
k('records.heaviest', 'Heaviest animal')
k('records.species', 'Species taken')
k('records.copy', 'Copy text')
k('records.print', 'Print (1 paper)')
k('records.copied', 'Season summary copied to the clipboard')
k('print.paper', 'You need a sheet of paper to print the summary')
k('print.done', 'Printed "%s"')
ST = {'shots': 'Shots fired', 'hits': 'Hits on game', 'hit_rate': 'Hit rate', 'kills': 'Animals brought down', 'clean': 'Clean kills',
      'clean_rate': 'Clean-kill rate', 'avg_shot': 'Average shot', 'longest_shot': 'Longest shot', 'bow_rifle': 'Bow / gun kills',
      'prone': 'Shots from prone', 'recovered': 'Tracked recoveries', 'lost': 'Wounded and lost', 'recovery_rate': 'Recovery rate',
      'longest_trail': 'Longest blood trail', 'signs': 'Sign read', 'stalks': 'Close stalks', 'calls': 'Called in', 'photos': 'Trail camera photos',
      'walked': 'On foot', 'ridden': 'By ATV', 'field_time': 'Time in the field', 'harvests': 'Field dressed'}
for key, t in ST.items():
    k('stat.' + key, t)
k('notes.empty', 'Nothing written yet. Your journal fills itself in as you hunt: every harvest, first, new rank and discovery gets a dated entry.')
k('notes.intro', 'Written as you hunt, dated on the reserve calendar.')
k('note.check', 'Checklist: %s')
k('note.perk', 'Learned %s (%s level %s).')
k('note.rank', 'Reached the rank of %s.')
k('note.summary', '%s is over — the season summary is in the Records page.')
k('note.event', 'Finished #%s in the weekend event.')
k('note.killsite', 'Found a %s kill — what is left of a %s.')
k('note.blizzard', 'Rode out a blizzard in the open.')
k('note.shed', 'Found a shed antler.')
k('note.bones', 'Found old bones in the brush.')
k('note.winter_night', 'Saw a winter night through, dusk to dawn.')  # [integ4]

# ------------------------------------------------------------------------------------------------ toasts, chat
k('toast.rank', 'NEW RANK')
k('toast.rank_body', 'You are now a %s. The reserve has noticed.')
k('toast.rank_top', 'The highest rank on the reserve. Your name will be told around the fires.')
k('toast.level', 'SKILL LEVEL %s')
k('toast.level_next', 'Next perk: %s at level %s')
k('toast.perk', 'PERK · %s %s')
k('toast.check', 'CHECKLIST · %s')
k('toast.summary', 'SEASON SUMMARY')
k('toast.summary_body', "Written into your journal's Records page.")
k('ticker.checklist', 'Checklist')
k('chat.rank', '%s is now a %s')

# ------------------------------------------------------------------------------------------------ reserve page (old journal content)
k('reserve.waiting', 'The journal is waiting for the server. Close it and reopen when the world has finished loading.')
k('reserve.inactive', 'Reserve mode is inactive here. Choose Frontier Hunts at world creation, or enable the frontierHunting game rule, to start a reserve expedition. Your skills, checklist and records still work everywhere.')
k('reserve.conditions', 'Field conditions')
k('reserve.wind', 'Wind travels %s at %s m/s.')
k('reserve.rain', 'Rain is moving through the reserve.')
k('reserve.dry', 'No rain over the reserve.')
k('reserve.survey', 'Habitat survey')
k('reserve.survey_value', '%s / 3 habitats · %s token reward')
k('reserve.survey_done', 'Survey complete and paid.')
k('reserve.survey_ready', 'Survey complete — collect your reward.')
k('reserve.survey_more', 'Travel into different biomes to record them.')
k('reserve.claim_survey', 'Collect survey reward')
k('reserve.first_hunt', 'First hunt · 60 tokens')
k('reserve.first_clue', 'Inspect whitetail prints or blood (aim and press use)')
k('reserve.first_harvest', 'Recover your whitetail with a skinning knife')
k('reserve.first_paid', 'Contract paid. Your account and harvest record are saved with this world.')
k('reserve.claim_hunt', 'Collect hunt reward')
k('reserve.expedition', 'The expedition')
k('reserve.mission', 'Report %s of %s · %s (%s/%s)')
k('reserve.campaign_done', "Mara's campaign is complete. Contracts remain open at the Expedition Guide.")
k('reserve.contract.1', 'Contract: %s · %s/%s, in progress')
k('reserve.contract.2', 'Contract: %s · %s/%s, ready — collect it in the Expedition Guide')
k('reserve.contract.3', 'Contract: %s · %s/%s, expired')
k('reserve.no_contract', 'No ranger contract running. Take one from the Expedition Guide (N).')
k('reserve.open_guide', 'Expedition Guide')
k('reserve.open_assignments', 'Assignments')
k('reserve.open_school', 'Field School')
k('reserve.open_settings', 'Settings')
k('reserve.habitats', 'Habitats recorded · %s')
k('reserve.fieldcraft', 'Fieldcraft')
k('reserve.fieldcraft_text', 'Move deliberately and read the land before you enter it. Wind carries your scent downwind: approach from downwind, crouch, and stay off the skyline. '
  'Place the shot behind the front leg — heart and lungs drop an animal where it stands; anything else runs and must be tracked. '
  'Recover game with the Contour Skinning Knife; smoke venison at the field smokehouse and tan hides on the rack. Ranger boards trade provisions for tokens.')
k('page.survival', 'Survival')
k('settings.section', "Hunter's Journal")
k('settings.toasts', 'Journal toasts')
k('settings.toasts_desc', 'New rank, skill level, perk, checklist')
k('settings.toasts_help', 'A toast when you reach a new rank or skill level, unlock a perk, tick off a checklist entry or a season summary is written.')
k('settings.ticker', 'XP ticker')
k('settings.ticker_desc', '"+40 XP" notes beside the hotbar')
k('settings.ticker_help', 'Small notes beside the hotbar when you earn skill XP. They fade after a few seconds.')


def java_keys():
    keys, prefixes = set(), set()
    for root, _, files in os.walk(os.path.join(R, 'src')):
        for f in files:
            if f.endswith('.java'):
                s = open(os.path.join(root, f), encoding='utf-8').read()
                for m in re.finditer(r'"(journal\.frontierhunts\.[a-z0-9_.]*)"', s):
                    (prefixes if m.group(1).endswith('.') or m.group(1).endswith('_') else keys).add(m.group(1))
                for m in re.finditer(r'@(journal\.frontierhunts\.[a-z0-9_.]+)', s):
                    keys.add(m.group(1))
    return keys


def checklist_ids():
    s = open(os.path.join(R, 'src/com/formaworks/frontierhunts/journal/Checklist.java'), encoding='utf-8').read()
    ids = set(re.findall(r'\b[eu]\("([a-z0-9_]+)"', s))
    ids |= {'gear_' + x for x in re.findall(r'\bgear\("([a-z0-9_]+)"', s)}
    ids |= {'gear_' + x for x in re.findall(r'\bgearIn\(Category\.[A-Z]+, "([a-z0-9_]+)"', s)}
    ids |= {'placed_' + x for x in re.findall(r'\bplaced\("([a-z0-9_]+)"', s)}
    return ids


missing = [x for x in sorted(java_keys()) if x not in L]
missing += [P + 'check.' + i for i in sorted(checklist_ids()) if P + 'check.' + i not in L]
for i in ['skill.%s.name' % s for s in S] + ['rank.' + r for r in ['greenhorn']]:
    assert P + i in L
if missing:
    sys.exit('missing lang keys: ' + ', '.join(missing))
out = os.path.join(R, 'patch/_merge/assets/frontierhunts/lang/en_us.json/journal.json')
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'w', encoding='utf-8') as f:
    json.dump(L, f, ensure_ascii=False, indent=1, sort_keys=True)
print('wrote', len(L), 'keys ->', out)
