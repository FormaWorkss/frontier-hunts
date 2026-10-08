"""[onboard] English lang fragment for the Frontier Handbook, the Archery Range and the unified learning path.
python3 tools/onboard/lang_en.py <repo root>  ->  patch/_merge/assets/frontierhunts/lang/en_us.json/onboard.json
Keys under guide.* override the Field School texts that described the old starter kit (the fragment merges after guide.json)."""
import json, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
L = {}
def s(k, v): L[k] = v

# ------------------------------------------------------------------------------------------ item, key
s('item.frontierhunts.frontier_handbook', 'Frontier Handbook')
s('item.frontierhunts.frontier_handbook.tip', 'Your first hour, step by step, with every recipe.')
s('item.frontierhunts.frontier_handbook.use', 'Right-click to open, or press H')
s('key.frontierhunts.field_school', 'Frontier Handbook & Field School')

# ------------------------------------------------------------------------------------------ Field School texts that changed
s('guide.frontierhunts.welcome.begin', 'Open the Handbook')
s('guide.frontierhunts.welcome.p2', 'Your Frontier Handbook shows what to do first, step by step, with every recipe. The card in the top-left corner always shows the one next step. Press %s any time to open the Handbook.')
s('guide.frontierhunts.welcome.kit', 'In your pack: the Frontier Handbook, a Field Recurve Bow and 3 arrows. Everything else you make or earn.')
s('guide.frontierhunts.welcome.footer', 'Change your mind any time: %s opens the Handbook · /frontierhunts tutorial reset')
s('guide.frontierhunts.card.key', '%s · Handbook')
s('guide.frontierhunts.toast.graduated_body', 'You know the basics. The Handbook (%s) shows what comes next.')
s('guide.frontierhunts.settings.card_help', 'Shows the one next step of the Frontier Handbook (a Field School lesson or a Handbook task). The Handbook (H) works either way.')
s('guide.frontierhunts.lesson.wind.objective', 'Right-click the Wind Checker for a puff of powder, or just hold it for a few seconds and watch your scent cone. No Wind Checker yet? Handbook step 4 has the recipe, and The Stalk lends you one.')
s('guide.frontierhunts.lesson.glass.objective', 'Hold Use with Binoculars to look through them, or place a Trail Camera facing a trail, rub or scrape. No Binoculars yet? Handbook step 4 has the recipe, and Glass & Call lends you a pair.')
s('guide.frontierhunts.lesson.harvest.objective', 'Right-click the downed animal with the Contour Skinning Knife, stay beside it until skinning finishes, then pick up the trophy it leaves. Handbook step 6 shows how to make the knife.')

# ------------------------------------------------------------------------------------------ the Archery Range
A = 'academy.frontierhunts.'
s(A + 'course.archery.title', 'The Archery Range')
s(A + 'course.archery.tagline', 'Paper from 10 to 40 yards, 3D deer, a walking deer and a tree-stand shot.')
s(A + 'course.archery.brief', 'The last Ranger Academy course puts every bow shot together. Shoot field points into the paper at 10, 20, 30 and 40 yards, then switch to broadheads for the deer: a heart or lung shot on the two 3D deer, on the walking deer, and from the tree stand on the deer below it. A vital hit drops the deer with the kill cam; anything else is called out and the deer resets. Watch the wind flags at long range. Shots only count from behind the shooting line (the stand deer only from the stand).')
s(A + 'course.archery.kit', 'Field Recurve Bow · Compound Bow · 32 field-point arrows · 16 broadhead arrows · Rangefinder · Wind checker')
s(A + 'course.archery.objective.paper', 'Paper · hit 10, 20, 30 and 40 yd (inside the blue up close)')
s(A + 'course.archery.objective.deer', '3D deer · 15 and 25 yd: a heart or lung shot on each')
s(A + 'course.archery.objective.moving', 'Walking deer · a heart or lung shot')
s(A + 'course.archery.objective.stand', 'Tree stand · a heart or lung shot from up there')
s(A + 'archery.intro', 'Field points for paper, broadheads for the deer. Hold Use to draw, let go to shoot.')
s(A + 'archery.paper_gold', 'Bullseye at %s yd · +%s · score %s')
s(A + 'archery.paper_red', 'Red ring at %s yd · +%s · score %s')
s(A + 'archery.paper_blue', 'Blue ring at %s yd · +%s · score %s')
s(A + 'archery.paper_black', 'Black ring at %s yd · +%s · score %s')
s(A + 'archery.paper_white', 'On paper at %s yd · +%s · score %s')
s(A + 'archery.paper_wide', '%s yd: too far out (+%s, score %s). Group them closer to the middle.')
s(A + 'archery.deer_clean', 'Clean %s shot at %s yd · down where it stood · score %s')
s(A + 'archery.moving_clean', 'Clean %s shot on the walking deer at %s yd · score %s')
s(A + 'archery.stand_clean', 'Clean %s shot from the stand at %s yd · score %s')
s(A + 'archery.moving_miss', '%s: off the vitals. Lead a walking deer a little, or wait for it to stop.')
s(A + 'archery.stand_miss', '%s: from above, aim a touch lower so the arrow goes through both lungs.')
s(A + 'archery.stand_only', 'That deer is for the tree stand: climb the ladder and shoot from up there.')
s(A + 'archery.broadhead_paper', 'Broadheads tear targets up. Field points for paper, broadheads for game.')
for y in (10, 20, 30, 40):
    s(A + 'archery.sign.paper%d' % y, '%d YD' % y)
s(A + 'archery.sign.deer15', '3D DEER · 15 YD')
s(A + 'archery.sign.deer25', '3D DEER · 25 YD')
s(A + 'archery.sign.moving', 'WALKING DEER · 20 YD')
s(A + 'archery.sign.stand', 'TREE STAND · climb the ladder')
s(A + 'archery.sign.line', 'SHOOTING LINE')

# ------------------------------------------------------------------------------------------ the Handbook
O = 'onboard.frontierhunts.'
s(O + 'screen.title', 'Frontier Handbook')
s(O + 'screen.brand_top', 'HANDBOOK')
s(O + 'screen.brand_bottom', 'Your first hour')
s(O + 'start', 'Start here')
s(O + 'start.eyebrow', 'FRONTIER HANDBOOK')
s(O + 'start.title', 'Your first hour')
s(O + 'start.p1', 'Welcome, hunter. Hunting here works like the real thing: animals smell you on the wind and only drop to a well-placed shot. This book gets you there one step at a time. Do the next step and it ticks itself.')
s(O + 'start.pack', 'You start with this Handbook, a Field Recurve Bow and 3 arrows. Everything else you make or earn, and every recipe is in this book.')
s(O + 'start.card_hint', 'The card in the top-left corner always shows this same next step. Press %s to open this book.')
s(O + 'steps', 'YOUR FIRST HOUR')
s(O + 'steps_done', '%s of %s steps done')
s(O + 'next.eyebrow', 'NEXT STEP · STEP %s OF %s')
s(O + 'next.done.eyebrow', 'ALL DONE')
s(O + 'next.done.title', 'Every step is done')
s(O + 'next.done.body', 'You know the frontier. Keep going with the Hunter\'s Journal checklist, Mara\'s expedition campaign and the Ranger Academy.')
s(O + 'step.eyebrow', 'STEP %s OF %s')
s(O + 'section.make', 'HOW TO MAKE IT')
s(O + 'section.do', 'DO THIS')
s(O + 'task.done', 'Done')
s(O + 'task.skipped', 'Skipped')
s(O + 'task.lesson', 'Field School · lesson %s')
s(O + 'pill.done', 'DONE')
s(O + 'pill.now', 'NOW')
s(O + 'btn.show', 'Show me how')
s(O + 'btn.lesson', 'Open the lesson')
s(O + 'btn.practice', 'Practice: %s')
s(O + 'btn.practice_done', 'Practice again: %s')
s(O + 'btn.skip', 'Skip')
s(O + 'btn.skip_confirm', 'Click again to skip')
s(O + 'btn.unskip', 'Undo skip')
s(O + 'btn.next_step', 'Next: %s ›')
s(O + 'btn.back_start', '‹ Start here')
s(O + 'link.journal', 'Journal (%s)')
s(O + 'link.expedition', 'Expedition (%s)')
s(O + 'link.school', 'Field School')
s(O + 'link.assignments', 'Assignments (%s)')
s(O + 'recipe_missing', 'No recipe for this in this world (a data pack may have changed it).')
s(O + 'waiting', 'WAITING FOR THE SERVER…')
s(O + 'lessons_off', 'Field School is turned off on this server, so these steps tick themselves.')
s(O + 'station.hand', 'Crafting grid (inventory or table)')
s(O + 'station.table', 'Crafting table')
s(O + 'station.campfire', 'Campfire: right-click the fire with the meat')
s(O + 'station.furnace', 'Furnace or smoker')
s(O + 'station.clothing', 'Clothing Table')
s(O + 'station.fit', 'Crafting grid, or whole stacks at the Bow Tuning Rack')
s(O + 'card.eyebrow', 'HANDBOOK · STEP %s/%s')
s(O + 'toast.eyebrow', 'HANDBOOK · STEP %s OF %s')
s(O + 'toast.all_done', 'Every Handbook step is done. Well hunted!')
s(O + 'journal.next', 'Frontier Handbook · step %s of %s')
s(O + 'school.eyebrow', 'RANGER ACADEMY')
s(O + 'school.practice', 'Practise it on the range: %s')
s(O + 'school.credit', 'Free practice with lent gear. Passing the course also completes this lesson.')
s(O + 'school.passed', 'You passed this course. Practise again any time.')
s(O + 'school.handbook_only', 'Part of the Frontier Handbook, step %s')
s(O + 'school.open_handbook', 'Open the Handbook')
s(O + 'dossier.credits', 'Passing it also completes Field School: %s.')
s(O + 'cmd.status', 'Handbook: %s of %s steps done. Next: step %s, %s.')
s(O + 'cmd.done', 'Handbook: every step is done.')
s(O + 'cmd.unskip', 'Handbook skips cleared for %s hunter(s).')

STEPS = {
    1: ('Make a crafting table', 'Crafting table', [
        'Punch a tree to get logs. Turn logs into planks, planks into sticks, and four planks into a crafting table.',
        'Place the table and use it: almost everything in Frontier Hunts is made on it.']),
    2: ('Make arrows', 'Arrows', [
        'You have a Field Recurve Bow and only 3 arrows, so make more. Primitive Arrows need flint, a stick and a feather. Hunting Arrows need an iron nugget, coal and a feather, and you get 4 at a time.',
        'Practise on a Shooting Target, then walk up and pull your arrows back out. The Ranger Academy\'s Archery Range comes later, as its last course.',
        'To shoot, hold Use to draw and let go to release. A full draw flies flatter and hits harder.']),
    3: ('Bow workbench & arrow tips', 'Arrow tips', [
        'The tip decides what an arrow does. Field points are for practice: they punch clean holes in targets. Broadheads are for hunting: their blades make animals bleed. Your starter arrows have broadheads.',
        'To change a tip, put an arrow and a tip in a crafting grid. The Bow Tuning Rack fits whole stacks at once and also makes bows.']),
    4: ('Find game & read sign', 'Find game', [
        'Deer smell you long before they see you. Check the wind with a Wind Checker and walk with the wind in your face.',
        'Look for tracks, rubs and scrapes, and press Use on one up close to read it. Glass open ground from high up with Binoculars, then sneak in crouched.',
        'These are Field School lessons 1 to 4. Each one has a free practice course.']),
    5: ('Your first hunt', 'First hunt', [
        'Wait until the deer stands calm and side-on. Aim just behind the front leg, a third of the way up: the heart and lungs.',
        'Hit there and the deer drops on the spot and you see the kill cam. Hit anywhere else and it runs. Then follow the blood: press Use on the drops to read them.']),
    6: ('Butcher, cook & stay alive', 'Butcher & cook', [
        'Make a Contour Skinning Knife. Hold it, right-click the downed animal and stay close until skinning is done. You get meat, hide and a trophy.',
        'Cook meat on a campfire: right-click the fire with it and wait. Raw meat goes bad and the cold hurts in winter, so eat game and keep warm.']),
    7: ('Camp, clothing & furs', 'Camp & furs', [
        'Tan hides and furs on a Tanning Rack. At the Clothing Table, turn them into fur hats, coats and boots for the cold.',
        'Pitch a tent to rest and keep your gear out in the wild. Tents are made from canvas.']),
    8: ('Rifles, expeditions & archery', 'Rifles & more', [
        'Ready for bigger game? Build a Weapons Workbench and a rifle, then sight it in on The Range in the Ranger Academy.',
        'Open the Expedition journal for Mara\'s campaign: missions across the reserve, contracts and rewards. Your first report counts the biomes you visit.',
        'Then finish the Ranger Academy with its last course, The Archery Range: paper from 10 to 40 yards, 3D deer, a walking deer and a tree-stand shot. A deer you take with a bow counts too.']),
}
for n, (title, short, paras) in STEPS.items():
    s(O + 'step.%d.title' % n, title)
    s(O + 'step.%d.short' % n, short)
    for i, p in enumerate(paras, 1):
        s(O + 'step.%d.p%d' % (n, i), p)

TASKS = {
    'table': ('Craft a crafting table', 'Turn logs into planks, then four planks into a crafting table.', 'Logs to planks, four planks to a crafting table.'),
    'arrows': ('Make arrows', 'Craft Primitive Arrows (flint, stick, feather) or Hunting Arrows (iron nugget, coal, feather).', 'Primitive Arrows: flint, stick, feather. Hunting Arrows: nugget, coal, feather.'),
    'archery': ('Pass the Archery Range', 'The last Ranger Academy course: paper from 10 to 40 yards, 3D deer and a tree-stand shot. The gear is lent and you get everything back. A deer taken with a bow counts too.', 'Press K and begin The Archery Range, or take a deer with a bow.'),
    'bench': ('Build a Bow Tuning Rack', 'Three string over planks. It fits tips to whole stacks and makes bows.', 'Craft a Bow Tuning Rack: string over planks.'),
    'tips': ('Fit field points', 'Make field points and fit them to arrows for practice, in a crafting grid or at the Bow Tuning Rack.', 'Put an arrow and a field point in a crafting grid.'),
    'wind': ('Read the wind', 'Use a Wind Checker and see where your scent blows.', ''),
    'sign': ('Read the sign', 'Find a hoofprint, rub or scrape and press Use on it from a few steps away.', ''),
    'glass': ('Glass before you walk', 'Look through Binoculars, or place a Trail Camera facing a trail.', ''),
    'stalk': ('Close the distance', 'Get within 20 blocks of a calm deer without it noticing you.', ''),
    'shot': ('Place the shot', 'Hit a game animal with an arrow or a bullet. Aim for the heart and lungs.', ''),
    'trail': ('Follow the blood', 'Read two spots of blood with Use, or walk up on an animal that ran after your shot.', ''),
    'harvest': ('Field-dress & take the trophy', 'Skin the animal with the Contour Skinning Knife, then pick up the trophy.', ''),
    'cook': ('Cook & eat game', 'Cook venison on a campfire (or in a furnace) and eat it.', 'Cook venison on a campfire, then eat it.'),
    'tent': ('Pitch a tent', 'Craft canvas, then a tent, and place it out in the wild.', 'Craft canvas and a tent, then pitch it.'),
    'furs': ('Wear fur clothing', 'Tan a hide, make a fur hat or coat at the Clothing Table and put it on.', 'Make fur clothing at the Clothing Table and wear it.'),
    'rifle': ('Get a rifle', 'Craft a Ridgeline Rifle (it takes iron blocks), then practise on The Range.', 'Craft a rifle, then practise on The Range.'),
    'report': ('File your first expedition report', 'Open the Expedition journal (N), visit three biomes and file the report.', 'Visit three biomes, then file the report in the Expedition journal.'),
}
for k, (title, how, card) in TASKS.items():
    s(O + 'task.%s.title' % k, title)
    s(O + 'task.%s.how' % k, how)
    if card:
        s(O + 'task.%s.card' % k, card)

# ------------------------------------------------------------------------------------------ journal checklist entries
J = 'journal.frontierhunts.check.'
s(J + 'hb_table', 'A crafting table')
s(J + 'hb_table.hint', 'Frontier Handbook step 1: craft a crafting table.')
s(J + 'hb_arrows', 'Your own arrows')
s(J + 'hb_arrows.hint', 'Frontier Handbook step 2: craft Primitive Arrows or Hunting Arrows.')
s(J + 'hb_bench', 'A Bow Tuning Rack')
s(J + 'hb_bench.hint', 'Frontier Handbook step 3: three string over planks.')
s(J + 'hb_tips', 'Arrow tips')
s(J + 'hb_tips.hint', 'Frontier Handbook step 3: fit a field point (or another tip) to an arrow.')
COURSES = {'archery': 'The Archery Range', 'glassing': 'Glass & Call', 'stalk': 'The Stalk', 'tracking': 'The Blood Trail', 'dressing': 'Field Dressing', 'range': 'The Range'}
for k, t in COURSES.items():
    s(J + 'academy_' + k, 'Academy: ' + t)
    s(J + 'academy_' + k + '.hint', 'Pass %s in the Ranger Academy (Ranger Assignments, K).' % t)

out = os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/onboard.json')
with open(out, 'w', encoding='utf-8') as f:
    json.dump(dict(sorted(L.items())), f, ensure_ascii=False, indent=1)
    f.write('\n')
print(len(L), 'keys ->', out)
