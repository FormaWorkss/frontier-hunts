"""[guide] Writes the Field School English lang fragment (patch/_merge/.../en_us.json/guide.json)."""
import json, os, sys

L = {}
P = 'guide.frontierhunts.'

def lesson(key, title, short, card, objective, *paras):
    L[P + 'lesson.' + key + '.title'] = title
    L[P + 'lesson.' + key + '.short'] = short
    L[P + 'lesson.' + key + '.card'] = card
    L[P + 'lesson.' + key + '.objective'] = objective
    for i, p in enumerate(paras, 1):
        L[P + 'lesson.' + key + '.p' + str(i)] = p

lesson('wind', 'Read the wind', 'The wind',
       'Right-click the Wind Checker, or hold it to see your scent cone.',
       'Right-click the Wind Checker for a puff of powder, or just hold it for a few seconds and watch your scent cone.',
       'Deer trust their noses more than their eyes. Wherever the wind carries your scent, a deer can smell you long before it sees you.',
       'Your scent drifts downwind in a cone that gets wider and weaker the farther it goes. Hold the Wind Checker and drifting lines show that cone: any animal inside it can smell you. Right-click it for a puff of powder that shows the wind and the slope thermals.',
       'In a reserve, the bar at the top of the screen shows which way the wind is blowing and how hard. Keep the wind blowing from the deer to you: come in from downwind.',
       'Thermals: on slopes, air warmed by the sun rises from mid-morning to late afternoon and carries your scent uphill. As the ground cools in the evening, through the night and into early morning, the air sinks and carries it downhill. Rain dampens scent and thermals, and scent-control clothing or Scent Cover shrink your cone.')

lesson('sign', 'Read the sign', 'The sign',
       'Look at a track, rub or scrape up close and press Use.',
       'Find a hoofprint, rub or scrape, look at it from a few steps away and press Use (right-click) to read it.',
       'Animals leave a record wherever they walk: tracks in mud, sand and snow, droppings, beds, and in the fall the rubs and scrapes bucks make.',
       'Look at a print and press Use. Your notes give the species, its size, its pace, which way it was heading and how old the print is. The pointed tips of a deer print point the way it went. Fresh prints have crisp edges; rain and snow soften them and then fill them in.',
       'Rubs and scrapes need no tools, just sharp eyes. A rub is a sapling or small tree with the bark scraped off by a buck\'s antlers, roughly knee to chest high; the pale wood shows from a distance. A scrape is a patch of pawed bare earth, about a metre across, under a low overhanging "licking" branch the buck chews and rubs. Look along field edges, trails and wood lines.',
       'Bucks make them from before the rut through the rut, and come back to check their scrapes again and again. A fresh scrape (dark, damp soil, often with a track in it) or a line of fresh rubs is a good place to wait or to set a trail camera.')

lesson('glass', 'Glass before you walk', 'Glassing',
       'Look through Binoculars, or place a Trail Camera.',
       'Hold Use with Binoculars to look through them, or place a Trail Camera facing a trail, rub or scrape.',
       'Most deer are found by looking, not by walking. Find somewhere with a view and glass the edges: field edges, timber lines and creek bottoms, especially in the first and last hour of light, when deer move most.',
       'Hold Use with Binoculars to look through them. A Rangefinder tells you how far away the animal is, and the Anatomy Binoculars show where the heart and lungs sit on the animal you are watching.',
       'A Trail Camera keeps watch while you are away. Fix it to a tree about waist high, angled along a trail or at a scrape rather than straight across it, and not facing the rising or setting sun. Check its photos to learn when deer pass and which way they go.')

lesson('stalk', 'Close the distance', 'Stalking',
       'Get within 20 blocks of a calm deer without spooking it.',
       'Get within 20 blocks of a deer that has not noticed you, and stay unnoticed for three seconds.',
       'Move with the wind in your face, so your scent blows away behind you. If the wind is at your back, circle around first.',
       'Crouch. You are quieter, lower and much harder to spot. Walking upright is seen from far away; sprinting can be heard.',
       'Use cover: trees, brush, ridges, blinds and tree stands. Move while the deer\'s head is down feeding and freeze when it looks up. A deer staring at you is deciding whether to run.',
       'Push too close too fast and it snorts and bolts, and the rest of the group goes with it.')

lesson('shot', 'Place the shot', 'The shot',
       'Hit a game animal with an arrow or bullet. Aim for the heart and lungs.',
       'Land a shot on any game animal with a bow or rifle. Aim for the heart and lungs.',
       'Wait for the animal to stand still, broadside (side-on) or quartering away. Aim just behind the front leg, about a third of the way up the body: the heart and lungs, the biggest vital target.',
       'Quartering away, aim farther back so the arrow or bullet goes through the lungs toward the far front leg. With a bow, pass on an animal facing you or quartering toward you: the shoulder and leg bones shield the vitals.',
       'Heart or lungs: the animal drops on the spot and the kill cam shows the shot. This is the clean, ethical shot.',
       'Any other hit and it runs off bleeding. Liver, gut, neck and head hits are fatal, but only after a run; shoulder and leg hits often live. Don\'t chase at once: a pushed deer runs farther.',
       'Steady first. Crouching cuts the sway of your aim. Lying prone (%s) takes almost all of it away, and a fitted Bipod unfolds to rest the rifle.')

lesson('trail', 'Follow the blood', 'Blood trail',
       'Read blood sign with Use, or put a tracking hound on it.',
       'Read two spots of blood with Use, put a tracking hound on a blood line, or walk up on an animal that ran after your shot.',
       'Start where the animal stood when you shot. Read the first blood and any cut hair: they tell you what you hit.',
       'Bright red, sprayed on both sides: heart. Bright to pinkish and frothy with tiny bubbles: lungs. Dark red drops: liver. Thin, dull blood flecked with green or brown stomach matter: gut. Bright drips that thin out and stop: muscle or leg, and it may live.',
       'Give it time. Real hunters wait about half an hour on a lung hit they did not see fall, three to four hours on a liver hit and eight to twelve on a gut hit. Here wounds play out faster: give a liver hit a minute or two and a gut hit a few minutes, then follow quietly.',
       'Remember the last blood and look ahead in the direction the animal was going. Liver- and gut-hit deer often lie down in a bed along the trail, and walking up too fast makes them jump and run again.',
       'A tracking hound follows the animal\'s own line. Craft a Hound Lead and use it to call your hound, then hold the lead, look at the blood and press Use.')

lesson('harvest', 'Field-dress & take the trophy', 'Harvest',
       'Skin the animal with the Contour Skinning Knife, then pick up the trophy.',
       'Right-click the downed animal with the Contour Skinning Knife, stay beside it until skinning finishes, then pick up the trophy it leaves.',
       'Walk up to the downed animal. With the Contour Skinning Knife in your main hand, right-click it and stay close, knife in hand, until skinning is done. Walking away pauses it.',
       'Skinning gives venison, a quarter, backstraps, the hide and a Game Trophy that remembers the animal: its weight, antlers, the shot distance and where you hit it.',
       'Put trophies on a Trophy Plinth at your lodge, or bring them to your camp\'s Big-Buck Board.')

lesson('tips', 'Field tips', 'Field tips',
       'Optional: read the field tips in the guide.',
       'Optional reading. Opening this page counts. Come back whenever you need it.',
       'A few more things that make a season go well. None of them are needed to finish Field School.')

for sec, title, body in [
    ('camps', 'Camps & tents', 'Place a Camp Post to found a camp. Friends can join it, share its stations and respawn at the post. Pitch a tent for shelter far from home.'),
    ('atv', 'ATV & fuel', 'ATVs run on gasoline. Fill a Jerry Can at a lava source, then use the ATV while holding the can to pour. A can carrier holds spare cans that top up the tank when it runs dry.'),
    ('seasons', 'Seasons & weather', 'The year turns. Fall brings the rut, when bucks answer calls and rattling. Winter snow holds crisp tracks, and blizzards cut visibility to a few blocks. Deer bed down in storms; so should you.'),
    ('steady', 'Get low, get steady', 'Crouch to cut the sway of your aim. Better still, press %s to go prone: lying flat, almost all the sway is gone, a fitted Bipod unfolds to rest the rifle, and the kick is much smaller. Press it again to get up.'),
]:
    L[P + 'lesson.tips.' + sec + '.title'] = title
    L[P + 'lesson.tips.' + sec + '.body'] = body

# field notes (contextual tips)
for key, title, body in [
    ('deer', 'Deer spotted', 'Stop. Check the wind before you move, then glass it and plan your approach from downwind.'),
    ('blood', 'Blood trail', 'Read it before you follow it: the colour and spray tell you where the animal was hit. Look at it and press Use.'),
    ('winded', 'You were winded', 'That animal smelled you. Keep the wind blowing from it to you; hold the Wind Checker to see your scent cone.'),
    ('blizzard', 'Blizzard', 'Visibility falls to a few blocks and fresh snow fills old tracks. Deer bed down until it passes. Find shelter and wait it out.'),
    ('season', 'The season has turned', '%s has come. Animals change their habits with the season; check the calendar in the Expedition Guide.'),
    ('predator', 'Predator sign', 'Wolves, coyotes, cougars and bears hunt here too. Game nearby will be warier, so keep your eyes open.'),
]:
    L[P + 'tip.' + key + '.title'] = title
    L[P + 'tip.' + key + '.body'] = body
L[P + 'tip.predator.body_kill'] = 'Something made a kill near here. Predators come back to a fresh kill, and the game nearby will be on edge.'
for i, s in enumerate(['Spring', 'Summer', 'Fall', 'Winter']):
    L[P + 'season.' + str(i)] = s

# coaching hints
for key, title, body in [
    ('winded', 'Winded!', 'It smelled you. Circle until the wind blows from the deer to you, then try again.'),
    ('spotted', 'Spotted!', 'It saw or heard you. Crouch, slow down and keep cover between you.'),
    ('dropped', 'Clean hit', 'Heart or lungs drop an animal on the spot. Read the blood where it stood.'),
    ('ran', 'It ran', 'That was not heart or lungs. Read the blood where it stood, give it time, then follow quietly.'),
    ('dressed', 'Skinned', 'Pick up the Game Trophy lying at the carcass.'),
    ('blood', 'Good read', 'One more. Keep following the blood in the direction it was going.'),
]:
    L[P + 'hint.' + key + '.title'] = title
    L[P + 'hint.' + key + '.body'] = body

L.update({
    P + 'toast.next': 'Next: %s',
    P + 'toast.lesson_done': 'LESSON %s COMPLETE',
    P + 'toast.lesson_skipped': 'LESSON %s SKIPPED',
    P + 'toast.hint': 'FIELD SCHOOL',
    P + 'toast.note': 'FIELD NOTE',
    P + 'toast.graduated_eyebrow': 'FIELD SCHOOL',
    P + 'toast.graduated': 'Course complete',
    P + 'toast.graduated_body': 'You know the basics. The guide stays one key away (%s), with optional field tips.',
    P + 'entry': 'Field School',
    P + 'card.eyebrow': 'FIELD SCHOOL  %s/%s',
    P + 'card.key': '%s · guide',
    P + 'screen.title': 'Field School',
    P + 'screen.brand_top': 'FIELD SCHOOL',
    P + 'screen.brand_bottom': 'A hunter\'s first season',
    P + 'screen.course': '%s of %s lessons',
    P + 'screen.eyebrow': 'LESSON %s OF %s',
    P + 'screen.eyebrow_optional': 'LESSON %s · OPTIONAL',
    P + 'screen.pill_done': 'DONE',
    P + 'screen.pill_current': 'NOW',
    P + 'screen.pill_optional': 'OPTIONAL',
    P + 'screen.pill_paused': 'PAUSED',
    P + 'screen.in_the_field': 'IN THE FIELD',
    P + 'screen.waiting': 'Waiting for the server…',
    P + 'screen.disabled': 'Field School objectives are turned off on this server. The pages are still here to read.',
    P + 'screen.done': 'Done',
    P + 'screen.paused': 'Field School is paused. Resume it from the button on the left.',
    P + 'screen.progress': 'In progress · %s / %s',
    P + 'screen.current': 'Your current lesson. The card in the top-left corner tracks it.',
    P + 'screen.any_order': 'Not done yet. Lessons count in any order.',
    P + 'screen.next': 'Next ›',
    P + 'screen.prev': '‹ Back',
    P + 'screen.skip_lesson': 'Skip this lesson',
    P + 'screen.skip_confirm': 'Click again to skip',
    P + 'screen.skip_course': 'Skip Field School',
    P + 'screen.resume_course': 'Resume Field School',
    P + 'screen.begin_course': 'Begin Field School',
    P + 'screen.footer': '%s or Esc to close · ← → to turn pages',
    P + 'screen.footer_scroll': 'Scroll to read · %s or Esc to close',
    P + 'welcome.eyebrow': 'FRONTIER HUNTS',
    P + 'welcome.title': 'Welcome to the frontier',
    P + 'welcome.p1': 'Hunting here works like the real thing. Animals smell you on the wind, read the land and only drop to a well-placed shot.',
    P + 'welcome.p2': 'Field School teaches you while you play, in seven short lessons. Follow the card in the top-left corner, and open the illustrated guide any time with %s.',
    P + 'welcome.kit': 'Starter gear is in your inventory: a Wind Checker, Binoculars and the Expedition Guide\'s field kit.',
    P + 'welcome.begin': 'Begin Field School',
    P + 'welcome.skip': 'Skip, I know the ropes',
    P + 'welcome.footer': 'Change your mind any time: %s opens the guide · /frontierhunts tutorial reset',
    P + 'settings.card': 'Field School card',
    P + 'settings.card_desc': 'Objective card, top-left',
    P + 'settings.card_help': 'Shows the current Field School lesson while the course is running. The guide (H) works either way.',
    P + 'settings.section': 'Field School',
    P + 'settings.tips': 'Field notes',
    P + 'settings.tips_desc': 'One-time first-encounter hints',
    P + 'settings.tips_help': 'A short note the first time you spot a deer, find blood, get winded, meet a blizzard, a new season or predator sign.',
    P + 'cmd.status': 'Field School: %s of %s lessons done. %s',
    P + 'cmd.current': 'Now on lesson %s: %s.',
    P + 'cmd.done': 'Course complete.',
    P + 'cmd.skipped': 'Skipped (use /frontierhunts tutorial reset to start again).',
    P + 'cmd.disabled': 'Turned off on this server.',
    P + 'cmd.reset': 'Field School reset for %s hunter(s).',
    P + 'cmd.skip': 'Field School skipped for %s hunter(s).',
    'key.frontierhunts.field_school': 'Field School guide',
})

# plate labels ([fieldbook] redrawn plates; anchors in GuideArt.java)
for art, labels in {
    'wind': {'wind': 'Wind', 'you': 'You', 'cone': 'Your scent cone: wider and weaker downwind', 'winded': 'Downwind deer: it smells you',
             'upwind': 'Upwind deer: it can\'t smell you', 'thermal_up': 'Mid-morning to late afternoon: warm air rises, scent drifts uphill',
             'thermal_down': 'Evening, night & early morning: cool air sinks, scent drifts downhill'},
    'tracks': {'whitetail': 'Whitetail · 5–9 cm, heart-shaped, pointed', 'elk': 'Elk · 10–12 cm, rounder, blunter', 'moose': 'Moose · 13–16 cm, long; dewclaws in mud',
               'bison': 'Bison · 12–15 cm, almost round', 'boar': 'Wild boar · 5–7 cm, round toes, dewclaws wide',
               'canine': 'Wolf · coyote: 4 toes, claws show', 'feline': 'Cougar · 4 toes, no claws, 3-lobed heel pad', 'bear': 'Black bear · 5 toes; hind print like a human foot',
               'grouse': 'Grouse · 3 toes forward, 1 back', 'duck': 'Duck · webbed, 3 toes', 'scale': 'All prints to scale · bar = 10 cm'},
    'sign': {'rub': 'Rub: bark stripped from a sapling by antlers', 'licking': 'Licking branch: chewed, broken tips', 'scrape': 'Scrape: pawed bare earth under the branch',
             'where': 'Look along field edges, trails and wood lines'},
    'glass': {'edge': 'Glass the edges at first and last light', 'camera': 'Trail Camera: waist high, angled along the trail'},
    'stalk': {'wind': 'Wind in your face', 'crouch': 'Crouch, move slowly', 'cover': 'Keep cover between you', 'deer': 'Move while its head is down'},
    'vitals': {'lungs': 'Lungs: drops on the spot (kill cam)', 'heart': 'Heart: drops on the spot (kill cam)', 'liver': 'Liver: runs, dies later. Wait.',
               'gut': 'Stomach & guts: runs far, beds. Wait longest.', 'aim': 'Aim: behind the front leg, a third up',
               'quarter': 'Quartering away: aim back, through to the far front leg'},
    'blood': {'heart': 'Heart: bright red, heavy spray', 'lungs': 'Lungs: pink-red froth, bubbles', 'liver': 'Liver: dark red drops', 'gut': 'Gut: thin, dull, green-brown flecks',
              'muscle': 'Leg / muscle: bright drips that stop', 'hit': 'Where it stood: hair and first blood', 'flag': 'Mark the last blood', 'bed': 'Wound bed'},
    'harvest': {'knife': 'Skinning knife: right-click, stay close', 'trophy': 'Trophy, venison & hide'},
}.items():
    for k, v in labels.items():
        L[P + 'art.' + art + '.' + k] = v

root = sys.argv[1] if len(sys.argv) > 1 else '.'
path = os.path.join(root, 'patch/_merge/assets/frontierhunts/lang/en_us.json/guide.json')
with open(path, 'w', encoding='utf-8') as f:
    json.dump(dict(sorted(L.items())), f, ensure_ascii=False, indent=1)
print(len(L), 'keys ->', path)
