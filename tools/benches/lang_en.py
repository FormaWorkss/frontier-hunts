"""[benches] English strings of the three benches (written to the lang merge fragment). python3 tools/benches/lang_en.py <repo>"""
import json, os, sys

R = sys.argv[1] if len(sys.argv) > 1 else '.'
L = {}

# ------------------------------------------------------------------ blocks / items
L.update({
    'block.frontierhunts.frontier_workbench': 'Frontier Workbench',
    'block.frontierhunts.gunsmith_bench': "Gunsmith's Bench",
    'block.frontierhunts.reloading_bench': 'Reloading Bench',
    'bench.frontierhunts.frontier_workbench.tip': 'Makes gear, clothing, camp, fishing, vehicles and building blocks',
    'bench.frontierhunts.gunsmith_bench.tip': 'Makes guns, bows, blades and attachments, and fits parts to your weapon',
    'bench.frontierhunts.reloading_bench.tip': 'Makes cartridges, shells, arrows, tips, darts and flares, and refits arrows',
    'bench.frontierhunts.frontier_workbench.subtitle': 'Gear · clothing · camp · fishing · vehicles · building',
    'bench.frontierhunts.gunsmith_bench.subtitle': 'Guns · bows · blades · attachments · fitting',
    'bench.frontierhunts.reloading_bench.subtitle': 'Cartridges · shells · arrows · tips · darts · flares',
})

# ------------------------------------------------------------------ tabs (rail label + one-line hint over the grid)
TABS = {
    'hunting': ('Hunting gear', 'Calls, scent, optics, cameras, kit and stands'),
    'clothing': ('Clothing', 'Ghillie suits, camo, scent control, furs and sewing'),
    'camp': ('Camp', 'Tents, camp gear, cooking and the lodge'),
    'fishing': ('Fishing', 'Rod, reel and tackle'),
    'vehicles': ('Vehicles', 'ATV, snow machines and boats'),
    'building': ('Building', 'Timber, roofing, canvas, stone and fixtures'),
    'range': ('Range', 'Targets for practice'),
    'food': ('Food', 'Trail food that keeps'),
    'misc': ('Other', 'Everything without a tab of its own'),
    'rifles': ('Rifles', 'Bolt, lever, semi-auto and tranquilizer'),
    'shotguns': ('Shotguns', 'Pump, semi-auto and double barrel'),
    'handguns': ('Handguns', 'Pistol, revolver and flare gun'),
    'bows': ('Bows', 'Recurves, compound, crossbow and specialty'),
    'blades': ('Blades', 'Field knife and spear'),
    'attachments': ('Attachments', 'Sights, scopes, magazines, muzzle and grips'),
    'fit': ('Fit', 'Fit parts to the weapon in your hand'),
    'cartridges': ('Cartridges', 'Rifle, pistol and shotgun ammunition'),
    'arrows': ('Arrows', 'Hunting, primitive and bowfishing arrows'),
    'tips': ('Arrow tips', 'Broadheads, points and tracers'),
    'refit': ('Refit arrows', 'Change the heads on your arrows'),
    'special': ('Darts & flares', 'Darts, flares and launcher bait'),
}
for k, (n, h) in TABS.items():
    L['bench.frontierhunts.tab.' + k] = n
    L['bench.frontierhunts.tab.%s.hint' % k] = h
WHERE = {'frontier_workbench': ['hunting', 'clothing', 'camp', 'fishing', 'vehicles', 'building', 'range', 'food', 'misc'],
         'gunsmith_bench': ['rifles', 'shotguns', 'handguns', 'bows', 'blades', 'attachments', 'fit'],
         'reloading_bench': ['cartridges', 'arrows', 'tips', 'refit', 'special']}
NAMES = {'frontier_workbench': 'Frontier Workbench', 'gunsmith_bench': "Gunsmith's Bench", 'reloading_bench': 'Reloading Bench'}
for b, tabs in WHERE.items():
    for t in tabs:
        L['bench.frontierhunts.where.' + t] = '%s · %s' % (NAMES[b], TABS[t][0])

SECTIONS = {
    'calls': 'Calls', 'scent': 'Scent & wind', 'optics': 'Optics', 'cameras': 'Cameras & lights', 'kit': 'Field kit', 'stands': 'Stands & blinds',
    'ghillie': 'Ghillie suits', 'camo': 'Camo coveralls', 'scent_control': 'Scent control', 'furs': 'Furs & hides', 'sewing': 'Sewing',
    'tents': 'Tents', 'camp_gear': 'Camp gear', 'cooking': 'Cooking & curing', 'lodge': 'Lodge & displays',
    'tackle': 'Tackle', 'atv': 'ATV', 'snow': 'Snow', 'boats': 'Boats',
    'timber': 'Timber & railings', 'roofing': 'Roofing', 'canvas': 'Canvas', 'stone': 'Stone', 'ground': 'Ground', 'fixtures': 'Doors, windows & fixtures',
    'targets': 'Targets', 'trail_food': 'Trail food',
    'rifles': 'Rifles', 'shotguns': 'Shotguns', 'handguns': 'Handguns', 'bows': 'Bows', 'specialty': 'Specialty', 'blades': 'Blades',
    'sights': 'Red dots & prisms', 'scopes': 'Scopes', 'magazines': 'Magazines', 'muzzle': 'Muzzle', 'furniture': 'Grips, stocks & tools',
    'rifle': 'Rifle', 'pistol': 'Pistol', 'shotgun': 'Shotgun', 'arrows': 'Arrows', 'broadheads': 'Broadheads', 'points': 'Points',
    'primitive': 'Primitive heads', 'tracers': 'Tracers', 'special': 'Darts, flares & bait', 'other': 'Other',
}
for k, v in SECTIONS.items():
    L['bench.frontierhunts.section.' + k] = v

# ------------------------------------------------------------------ screen
L.update({
    'bench.frontierhunts.ui.search': 'Search this bench…',
    'bench.frontierhunts.ui.can_craft': 'Can craft',
    'bench.frontierhunts.ui.results': '%s results',
    'bench.frontierhunts.ui.result': '1 result',
    'bench.frontierhunts.ui.results_hint': 'across every tab of this bench',
    'bench.frontierhunts.ui.no_results': 'Nothing here is called "%s".',
    'bench.frontierhunts.ui.no_results_hint': 'Try a shorter word, or the other benches: guns and parts at the Gunsmith\'s Bench, ammunition at the Reloading Bench, everything else at the Frontier Workbench.',
    'bench.frontierhunts.ui.none_ready': 'Nothing on this tab can be made from what you carry yet.',
    'bench.frontierhunts.ui.none_ready_hint': 'Turn off "Can craft" to see everything and what each needs.',
    'bench.frontierhunts.ui.materials': 'MATERIALS',
    'bench.frontierhunts.ui.have_need': 'HAVE / NEED',
    'bench.frontierhunts.ui.any_of': 'any %s',
    'bench.frontierhunts.ui.makes': 'Makes %s',
    'bench.frontierhunts.ui.variant': 'Recipe %s of %s',
    'bench.frontierhunts.ui.craft': 'Craft',
    'bench.frontierhunts.ui.craft5': 'Craft ×5',
    'bench.frontierhunts.ui.sew': 'Sew it on',
    'bench.frontierhunts.ui.ready': 'Ready to craft',
    'bench.frontierhunts.ui.ready5': 'Ready · you can make 5 batches',
    'bench.frontierhunts.ui.creative': 'Creative: materials are free',
    'bench.frontierhunts.ui.missing': 'Missing %s material(s)',
    'bench.frontierhunts.ui.missing_one': 'Missing 1 material',
    'bench.frontierhunts.ui.no_room': 'Your inventory is full',
    'bench.frontierhunts.ui.sew_hold': 'Hold a coat, ghillie, coveralls or armour to sew into',
    'bench.frontierhunts.ui.sew_target': 'Sews into the %s in your hand',
    'bench.frontierhunts.ui.sew_done': 'The %s in your hand already has it',
    'bench.frontierhunts.ui.sew_mittens_chest': 'Mittens sew onto a chest piece only',
    'bench.frontierhunts.ui.sew.lining': 'Sew in a Fur Lining',
    'bench.frontierhunts.ui.sew.mittens': 'Sew on Fur Mittens',
    'bench.frontierhunts.ui.fit_held': 'Fit to %s',
    'bench.frontierhunts.ui.also_table': 'Also made at a crafting table (data pack recipe)',
    'bench.frontierhunts.ui.footer': 'Materials come straight from your inventory',
    'bench.frontierhunts.ui.footer_fit': 'Parts come from and go back to your inventory',
    'bench.frontierhunts.ui.footer_refit': 'Missing heads are made from your materials',
    'bench.frontierhunts.ui.key.craft': 'craft',
    'bench.frontierhunts.ui.key.select': 'select',
    'bench.frontierhunts.ui.key.tab': 'next tab',
    'bench.frontierhunts.ui.key.close': 'close',
    'bench.frontierhunts.ui.key.fit': 'fit',
    'bench.frontierhunts.ui.key.refit': 'refit all',
    'bench.frontierhunts.ui.close': 'Close',
})

# ------------------------------------------------------------------ Fit tab (Gunsmith's Bench)
L.update({
    'bench.frontierhunts.fit.hotbar': 'Your hotbar',
    'bench.frontierhunts.fit.in_hand': 'IN YOUR HAND',
    'bench.frontierhunts.fit.nothing': 'Nothing to fit',
    'bench.frontierhunts.fit.nothing_hint': 'Pick a gun or a fishing rod from your hotbar above. Only weapons in the hotbar can be worked on: move it there from your pack first.',
    'bench.frontierhunts.fit.no_parts': 'This takes no parts',
    'bench.frontierhunts.fit.no_parts_hint': 'Bows, knives and tools have no attachment slots.',
    'bench.frontierhunts.fit.reloading': 'Finish reloading before you change parts.',
    'bench.frontierhunts.fit.empty': 'Empty',
    'bench.frontierhunts.fit.standard': 'Standard',
    'bench.frontierhunts.fit.remove': 'Remove',
    'bench.frontierhunts.fit.fit': 'Fit',
    'bench.frontierhunts.fit.swap': 'Swap in',
    'bench.frontierhunts.fit.owned': 'You have %s',
    'bench.frontierhunts.fit.not_owned': "You don't have one",
    'bench.frontierhunts.fit.craft_it': 'Click to make one in Attachments',
    'bench.frontierhunts.fit.click_fit': 'Click to fit it',
    'bench.frontierhunts.fit.click_swap': 'Click to swap it in: the %s comes off',
    'bench.frontierhunts.fit.click_remove': 'Fitted · click Remove to take it off',
    'bench.frontierhunts.fit.cant': "Can't fit it right now (full inventory or reloading)",
    'bench.frontierhunts.fit.note': 'Fitting a part takes one from your inventory; removing it puts it back. A new sight or magazine swaps out the old one.',
    'bench.frontierhunts.fit.legend': 'Number = parts you carry · + = not owned, click to make one · outlined = fitted',
    'bench.frontierhunts.fit.slot.optic': 'Optic',
    'bench.frontierhunts.fit.slot.muzzle': 'Muzzle',
    'bench.frontierhunts.fit.slot.magazine': 'Magazine',
    'bench.frontierhunts.fit.slot.grip': 'Grip',
    'bench.frontierhunts.fit.slot.stock': 'Stock',
    'bench.frontierhunts.fit.slot.bipod': 'Bipod',
    'bench.frontierhunts.fit.slot.reel': 'Reel',
})

# ------------------------------------------------------------------ Refit arrows tab (Reloading Bench)
L.update({
    'bench.frontierhunts.refit.arrows': 'Your arrows',
    'bench.frontierhunts.refit.arrows_hint': 'pick a stack',
    'bench.frontierhunts.refit.none': 'No arrows in your inventory',
    'bench.frontierhunts.refit.none_hint': 'Make Hunting or Primitive Arrows on the Arrows tab, or take some out of your quiver.',
    'bench.frontierhunts.refit.plain': 'no hunting head',
    'bench.frontierhunts.refit.hunting': 'Hunting Arrow',
    'bench.frontierhunts.refit.primitive': 'Primitive Arrow',
    'bench.frontierhunts.refit.vanilla': 'Arrow',
    'bench.frontierhunts.refit.new_head': 'NEW HEAD',
    'bench.frontierhunts.refit.owned': 'number = heads you have',
    'bench.frontierhunts.refit.stock': 'Stock head',
    'bench.frontierhunts.refit.stock_hint': 'Take the fitted heads off: they go back to your inventory and the shafts get their stock head.',
    'bench.frontierhunts.refit.penetration': 'Penetration',
    'bench.frontierhunts.refit.bleed': 'Blood trail',
    'bench.frontierhunts.refit.damage': 'Damage',
    'bench.frontierhunts.refit.recovery': 'Recovery',
    'bench.frontierhunts.refit.one': 'Refit 1',
    'bench.frontierhunts.refit.all': 'Refit all (%s)',
    'bench.frontierhunts.refit.all0': 'Refit all',
    'bench.frontierhunts.refit.heads_off': 'Heads off (%s)',
    'bench.frontierhunts.refit.heads_off0': 'Heads off',
    'bench.frontierhunts.refit.make': 'Make %s heads',
    'bench.frontierhunts.refit.pick': 'Pick a stack of arrows on the left',
    'bench.frontierhunts.refit.same': 'These arrows already carry this head',
    'bench.frontierhunts.refit.creative': 'Creative: heads are free',
    'bench.frontierhunts.refit.status_have': 'You have %s heads · old heads come back',
    'bench.frontierhunts.refit.status_make': 'You have %s · %s more made from your materials',
    'bench.frontierhunts.refit.status_none': 'No heads, and not enough material to make any',
    'bench.frontierhunts.refit.status_full': 'No room in your inventory',
    'bench.frontierhunts.refit.status_stock': 'Heads back to your inventory · shafts get their stock head',
    'bench.frontierhunts.refit.status_stock_none': 'These arrows carry their stock head: nothing to take off',
})

# ------------------------------------------------------------------ retired benches
L.update({
    'bench.frontierhunts.retired': 'Retired workbench: replaced by the %s',
    'bench.frontierhunts.retired.place': 'Placing or using it builds a %s.',
    'bench.frontierhunts.retired.rebuilt': 'Your old %s was rebuilt as a %s.',
    'bench.frontierhunts.retired.cramped': 'No room beside this old bench for the two-block %s: opening it here as it is.',
    'bench.frontierhunts.retired.no_room': 'The %s is two blocks wide: it needs a free block to its left.',
})

# ------------------------------------------------------------------ Handbook / onboarding / journal (existing keys, now accurate)
L.update({
    'onboard.frontierhunts.step.1.title': 'Crafting table & your three benches',
    'onboard.frontierhunts.step.1.short': 'Benches',
    'onboard.frontierhunts.step.1.p1': 'Punch a tree for logs, make planks, sticks and a crafting table. At the crafting table make your three benches: every Frontier Hunts item is made at one of them.',
    'onboard.frontierhunts.step.1.p2': "Frontier Workbench (sticks over planks on log legs): gear, clothing, camp, fishing, vehicles and building. Gunsmith's Bench (stick, flint, stick on top): guns, bows, blades and attachments. Reloading Bench (stone on top): cartridges, shells, arrows, tips, darts and flares.",
    'onboard.frontierhunts.step.1.p3': 'Each bench is two blocks wide. Use it, pick a tab, pick an item and press Craft: the materials come straight from your inventory.',
    'onboard.frontierhunts.step.2.title': 'Bow & arrows',
    'onboard.frontierhunts.step.2.short': 'Arrows',
    'onboard.frontierhunts.step.2.p1': "You have a Field Recurve Bow and only 3 arrows, so make more at the Reloading Bench (Arrows tab): Hunting Arrows from an iron nugget, a stick and a feather, Primitive Arrows from flint, a stick and a feather. Both make 4. A new bow comes from the Gunsmith's Bench (Bows tab).",
    'onboard.frontierhunts.step.3.title': 'Arrow tips',
    'onboard.frontierhunts.step.3.short': 'Arrow tips',
    'onboard.frontierhunts.step.3.p2': "Make tips on the Reloading Bench's Arrow tips tab. To change the heads on your arrows, open its Refit arrows tab: pick a stack, click a head and press Refit all. Missing heads are made from your materials and the old heads come back.",
    'onboard.frontierhunts.step.6.p1': "Make a Contour Skinning Knife at the Frontier Workbench (Hunting gear). Hold it, right-click the downed animal and stay close until skinning is done. You get meat, hide and a trophy.",
    'onboard.frontierhunts.step.7.p1': 'Tan hides and furs on a Tanning Rack. At the Frontier Workbench (Clothing tab), turn them into fur hats, coats and boots for the cold, or sew a fur lining into what you wear.',
    'onboard.frontierhunts.step.7.p2': 'Pitch a tent to rest and keep your gear out in the wild. Tents and canvas are on the Frontier Workbench too.',
    'onboard.frontierhunts.step.8.p1': "Ready for bigger game? Make a rifle at the Gunsmith's Bench and its cartridges at the Reloading Bench, then sight it in on The Range in the Ranger Academy. The Gunsmith's Fit tab puts scopes and parts on the gun in your hand.",
    'onboard.frontierhunts.station.clothing': 'Frontier Workbench · Clothing',
    'onboard.frontierhunts.station.fit': 'Reloading Bench · Refit arrows',
    'onboard.frontierhunts.station.hand': 'Crafting grid (inventory or table)',
    'onboard.frontierhunts.station.table': 'Crafting table',
    'onboard.frontierhunts.task.table.title': 'Craft a crafting table',
    'onboard.frontierhunts.task.bench.title': 'Make your three benches',
    'onboard.frontierhunts.task.bench.card': "At the crafting table: Frontier Workbench, Gunsmith's Bench and Reloading Bench.",
    'onboard.frontierhunts.task.bench.how': "Make all three at a crafting table and place them (each is two blocks wide). Frontier Workbench: sticks over planks on log legs. Gunsmith's Bench: stick, flint, stick over planks on log legs. Reloading Bench: three stone over planks on log legs.",
    'onboard.frontierhunts.task.arrows.card': 'Reloading Bench, Arrows tab: Hunting Arrows (nugget, stick, feather) or Primitive Arrows (flint, stick, feather).',
    'onboard.frontierhunts.task.arrows.how': 'At the Reloading Bench (Arrows tab) make Hunting Arrows (iron nugget, stick, feather) or Primitive Arrows (flint, stick, feather).',
    'onboard.frontierhunts.task.tips.card': 'Reloading Bench: make field points, then fit them on the Refit arrows tab.',
    'onboard.frontierhunts.task.tips.how': 'Make field points on the Arrow tips tab of the Reloading Bench, then fit them to your arrows on its Refit arrows tab.',
    'onboard.frontierhunts.task.furs.card': 'Make fur clothing at the Frontier Workbench (Clothing) and wear it.',
    'onboard.frontierhunts.task.furs.how': 'Tan a hide, make a fur hat or coat at the Frontier Workbench (Clothing tab) and put it on.',
    'onboard.frontierhunts.task.tent.card': 'Make a tent at the Frontier Workbench (Camp), then pitch it.',
    'onboard.frontierhunts.task.tent.how': 'At the Frontier Workbench make canvas (Building) and a tent (Camp), and place it out in the wild.',
    'onboard.frontierhunts.task.rifle.card': "Make a rifle at the Gunsmith's Bench, then practise on The Range.",
    'onboard.frontierhunts.task.rifle.how': "At the Gunsmith's Bench (Rifles tab) make a Ridgeline Rifle, and its cartridges at the Reloading Bench, then practise on The Range.",
    'onboard.frontierhunts.task.camp_cook.how': 'Make a Camp Dutch Oven at the Frontier Workbench (Camp), set it on a campfire (or feed it coals), add meat and vegetables - bowls for stews - and take out the dish. Camp meals give a short buff.',
    'journal.frontierhunts.check.hb_bench': 'Your three benches',
    'journal.frontierhunts.check.hb_bench.hint': "Frontier Handbook step 1: the Frontier Workbench, Gunsmith's Bench and Reloading Bench.",
    'journal.frontierhunts.check.hb_arrows.hint': 'Frontier Handbook step 2: make Hunting or Primitive Arrows at the Reloading Bench.',
    'journal.frontierhunts.check.hb_tips.hint': "Frontier Handbook step 3: fit a field point (or another tip) to your arrows at the Reloading Bench.",
    'journal.frontierhunts.check.gear_ghillie.hint': 'Own a ghillie or carbon scent-control piece (Frontier Workbench, Clothing).',
    'journal.frontierhunts.check.gear_knife.hint': 'Carry the Contour Skinning Knife (Frontier Workbench, Hunting gear).',
    'journal.frontierhunts.check.gear_scope.hint': "Own a riflescope or optic (Gunsmith's Bench, Attachments).",
    'expedition.frontierhunts.ui.how.craft': "Counts any Frontier Hunts item you make at the Frontier Workbench, the Gunsmith's Bench or the Reloading Bench.",
    'expedition.frontierhunts.ui.lodge.next': "Next upgrade: %s (%s tokens). Upgrades issue storage, then the Reloading Bench, the Gunsmith's Bench and the Frontier Workbench.",
    'expedition.frontierhunts.ui.lodge.perk2': 'Rank 2 - Reloading Bench, and venison you deliver on the ranger board pays 25%% more.',
    'expedition.frontierhunts.ui.lodge.perk3': "Rank 3 - Gunsmith's Bench, and expedition contracts give you 30 minutes instead of 20.",
    'expedition.frontierhunts.ui.lodge.perk4': 'Rank 4 - Frontier Workbench, and one more deer tag every season (the landowner\'s tag).',
    'expedition.frontierhunts.ui.stores.bench_text': "Three benches make everything. Frontier Workbench: gear, clothing, camp, fishing, vehicles and building. Gunsmith's Bench: guns, bows, blades and attachments (its Fit tab fits parts to the weapon in your hand). Reloading Bench: cartridges, shells, arrows, tips, darts and flares.",
    'item.frontierhunts.jerry_can.tip_empty': 'Empty. Draw a lava source into it (or make one at the Frontier Workbench with a lava bucket) - the heat cracks it to fuel.',
    'item.frontierhunts.ridgeline_scope.fit': "Gunsmith's Bench, Fit tab: hold the rifle and fit or swap its scope.",
    'rifle.frontierhunts.sight': "Sight: %s · swap at the Gunsmith's Bench (Fit tab)",
    'firsthunt.frontierhunts.step.harvest.how': "First use your deer tag on the deer (the law says a deer is tagged before it's moved or skinned). Then hold the Contour Skinning Knife, right-click the deer and stay beside it until skinning finishes: you get venison, the hide and a trophy. No knife? Make one at the Frontier Workbench (Hunting gear) from an iron ingot, a stick and leather.",
    'firsthunt.frontierhunts.rules.knife': 'Hold the Contour Skinning Knife, use it on the deer (%s) and stay beside it until the bar fills. Without the knife nothing happens and nothing drops. No knife? Make one at the Frontier Workbench (Hunting gear) from an iron ingot, a stick and leather.',
    'licence.frontierhunts.down.no_knife': 'To skin it you need a Contour Skinning Knife (Frontier Workbench: iron ingot, stick, leather): nothing drops without it.',
    'item.frontierhunts.tanned_heavy_hide.note': 'Wind- and waterproof. Robes and mukluks at the Frontier Workbench (Clothing); bedrolls.',
    'item.frontierhunts.tanned_fur.note': 'Frontier Workbench, Clothing tab: fur hats, mittens, linings, mukluks.',
    'item.frontierhunts.bear_fur.note': 'The warmest fur there is: bear fur coats at the Frontier Workbench (Clothing).',
    'item.frontierhunts.fur_lining.note': 'Frontier Workbench, Clothing tab: hold any worn piece (camo, ghillie, coveralls, armour) and sew it in: +0.6 warmth.',
    'item.frontierhunts.fur_mittens.note': 'Frontier Workbench, Clothing tab: hold any chest piece and sew them on: warmer hands, steadier aim in the cold.',
})

out = os.path.join(R, 'patch/_merge/assets/frontierhunts/lang/en_us.json/zzzzzzz_benches.json')
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump(L, open(out, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('wrote', out, len(L))
