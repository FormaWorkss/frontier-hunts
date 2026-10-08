# Workstream `benches`: three workbenches replace every old one

Branch `benches` (on top of `benchart`, which delivered the models, atlases, item models and GUI emblems).

Austin: "too many workbenches will confuse people". Every Frontier Hunts item is now made at exactly one of three
benches, never at the vanilla crafting table:

| bench | id | makes | crafting-table recipe (vanilla materials only) |
|---|---|---|---|
| **Frontier Workbench** | `frontierhunts:frontier_workbench` | everything that is not a weapon or ammo: hunting gear, clothing (incl. the old Clothing Table's furs, hides and sewing), camp, fishing, vehicles, building, range, food | `SSS / PPP / L L` (sticks over planks on log legs) |
| **Gunsmith's Bench** | `frontierhunts:gunsmith_bench` | rifles, shotguns, handguns, bows (incl. crossbow, bowfishing bow, bait launcher), blades, attachments; **Fit** tab fits parts to the weapon in your hand | `SFS / PPP / L L` (stick, flint, stick) |
| **Reloading Bench** | `frontierhunts:reloading_bench` | cartridges, shells, arrows, arrow tips, darts, flares, launcher bait (Craft ×5 on all of it); **Refit arrows** tab | `CCC / PPP / L L` (any cobblestone-type stone) |

`P` = any planks, `L` = any logs. All three are two blocks wide (WideStationBlock), wood sound, axe tool, strength 2.5,
push reaction BLOCK, drop themselves (survives_explosion).

## Design

### Recipes (bench-only)
- New recipe type `frontierhunts:bench` with serializers `frontierhunts:bench_shaped` / `frontierhunts:bench_shapeless`
  (`benches/BenchRecipes.java`): subclasses of `ShapedRecipe` / `ShapelessRecipe` whose `getType()` is the bench type,
  so the crafting table (which asks for `RecipeType.CRAFTING`) never matches them; they stay `CraftingRecipe`s
  (ingredients / width / height keep working for the Handbook cards). Codecs and stream codecs mirror vanilla's.
  `showNotification()` is false (no "check your recipe book" toast). Client: `RegisterRecipeBookCategoriesEvent` maps
  the type to `RecipeBookCategories.UNKNOWN` (never in the green book, no "Unknown recipe category" log).
- `tools/benches/gen_data.py <repo>` rewrites every `crafting_shaped` / `crafting_shapeless` recipe of frontierhunts and
  frontierstructures whose result is a mod item to the bench type (only the `"type"` string changes, the rest of the file
  is byte-identical), into `patch/data/frontierhunts/recipe/...` (same paths) and `fs/resources/...` (in place). Kept at
  the crafting table: the three benches, `frontier_handbook`, and vanilla-result recipes (log -> vanilla log, tanned hide
  -> leather). **212 converted** (205 frontierhunts + 7 frontierstructures) **+ 14 Clothing Table recipes = 226 bench lines.** Of the 225 frontierhunts crafting recipes, the other 20 are the 7 retired benches (removed), the Handbook and 12 vanilla-result conversions (kept at the crafting table).
  The generator is idempotent: **re-run it after merging any branch that adds crafting recipes**
  (`python3 tools/benches/gen_data.py /home/claude/fh2`). Until then such a recipe is still listed at its bench (safety
  net in `BenchCatalog`, marked "also made at a crafting table") and `/fhqa bench recipes` reports it.
- Removed (`patch/_remove/benches.txt`, plus 7 deleted patch files): the seven old bench recipes and their unlock
  advancements, the crafting-grid `arrow_refit` recipe (arrow heads are refitted at the Reloading Bench only).
  `salt_cure` and `butcher` (food processing in the grid) are unchanged.
- Which bench / tab / section: the explicit, reviewed table in `benches/BenchCatalog.java` (`put(tab, section, ids...)`,
  display order). Unknown items fall back by kind (ammo -> Reloading "Darts & flares", attachment -> Attachments,
  bow -> Bows, weapon -> Rifles, anything else -> Frontier "Other", a tab that only shows when it has something).
  `python3 tools/benches/table.py <repo>` prints the table below and fails if a recipe is unplaced or a tab is empty.

### Crafting (server-authoritative)
`benches/BenchMenu.java` (no slots; works from the 36 main inventory slots). The screen sends vanilla container button
clicks: `i` craft entry i, `10000+i` craft ×5 (Reloading Bench only, all or nothing), `20000+part*2+(fit?0:1)` fit /
remove a part on the held item, `40000+(slot*16+head)*2+(all?1:0)` refit an arrow stack. The server checks the player,
`containerMenu == this`, spectator, `stillValid` (the block at the menu's position is that bench, or a retired one whose
successor it is, within 8 blocks), the anti-spam delay (8 ticks for crafts, 4 for fit/refit, as the old workbench),
recomputes the plan from its own inventory (`benches/BenchCrafting.java`) and applies it. Each craft:
`ItemStack.onCraftedBy` (crafted stat + item hook), `triggerRecipeCrafted`, `EventHooks.firePlayerCraftingEvent`
(`PlayerEvent.ItemCraftedEvent`, with a container of the consumed items: the Handbook tracker and food spoilage stamp the
product before it lands in the inventory), `ExpeditionService.record(sp, "craft", "*", n, 0)`, smithing-table sound.
While a bench is open the server sends the whole inventory when it changes (`broadcastChanges` fingerprint), because the
client only takes hotbar slot updates of the inventory menu while another menu is open.

Capabilities re-homed from the old benches:
| old | now |
|---|---|
| Weapons Workbench (firearms, blades, skinning knife) | Gunsmith's Bench (knife moved to Frontier "Hunting gear") |
| Optics & Attachment Workbench: crafting parts; fit parts to the held weapon; AttachmentMenu tray | Gunsmith's Attachments tab + **Fit** tab (fits / removes / swaps every part on the held weapon, hotbar picker; "make one" jumps to the recipe). The AttachmentMenu tray is not needed: nothing lived only there. |
| Ammo Reloader (cartridges, shells, darts; Assemble ×5) | Reloading Bench, Craft ×5 on every recipe |
| Bow Tuning Rack (bows, arrows, tips; arrow-tip refit; bow display) | bows -> Gunsmith's Bench; arrows, tips, refit -> Reloading Bench (**Refit arrows** tab: per stack, missing heads made from materials, old heads back, "Heads off"). Bow display: use the Bow Display (`bow_stand`). |
| Fishing Station (rod, tackle; drag-kit fitting) | Frontier "Fishing" tab; selecting the Fishing Drag Kit shows "Fit to <held rod>" |
| Field Clothing Workbench (+ clothing_table furs/hides/sewing) | Frontier "Clothing" tab (sewing = "Sew it on" into the held garment) |
| Tent Bench (tents) | Frontier "Camp" tab |

### Old benches in existing worlds (`benches/OldBenches.java`)
- Blocks and items stay registered (worlds and inventories load), hidden from creative (search too), no recipes.
- **Using** a placed old bench (right click, `PlayerInteractEvent.RightClickBlock`, HIGH; sneak + item still places
  against it) rebuilds it in place as its successor: weapons / attachment workbench and bow tuning rack -> Gunsmith's
  Bench, ammo reloader -> Reloading Bench, fishing station / clothing workbench / tent bench -> Frontier Workbench.
  Same facing; a pair is rebuilt as a pair (left at the old origin, right at `facing.getClockWise()`, exactly the old
  halves); a single-block old bench becomes two-wide only if the block to its left is replaceable, otherwise it stays
  and the successor's screen opens on it (its `stillValid` accepts the old block). Bows on a Bow Tuning Rack go back to
  the player. A gold action-bar line says what happened. The successor's screen opens straight away.
- **Placing** an old bench item builds its successor (`BlockEvent.EntityPlaceEvent`, LOW): a pair is swapped in place;
  a single needs the block to its left (otherwise the placement is cancelled and the item kept, with a message).
- Tooltip on old items: "Retired workbench: replaced by the X" / "Placing or using it builds a X." (client).
- Old code that hands out stations (expedition lodge upgrade in base `ExpeditionService`, `camps/CampService`) gives the
  successor instead: mixin `client/mixin/BenchGiveMixin` (common list) modernises the stack in `ExpeditionService.give`.
- Generators: `livingworld` OutfitterPost / HuntingCamp place the new benches; the Frontier Structures settlement
  templates (`fs/resources/.../settlement_{hamlet,outfitter,fishing,smokehouse}.nbt`, and the unused frontierhunts copies
  under `patch/data/frontierhunts/structure/expedition/`) had their palettes swapped to the successors (a single ammo
  reloader in the outfitter got its right half; block-entity data of those positions dropped). Old placed benches in
  already generated chunks convert when used.

### The bench screen (`benches/client/BenchScreen.java`, `BenchFitPanel`, `BenchRefitPanel`, `BenchDraw`)
One screen for the three menus, drawn with FrontierUi (Inter fonts, rounded rects, batched), laid out for the 640 x 360
GUI pixels `FrontierGuiScale` guarantees (panel <= 624 x 344, centred; registered in FrontierGuiScale.SCREENS).
- Header band in the bench's colour (gold oak / felt green / press red): emblem, bench name, what it makes, search box
  (typing anywhere searches the whole bench; results grouped by tab), "Can craft" filter, close.
- Tab rail: item icon, name, craftable/total (Fit: parts fitted; Refit: arrow stacks). Mouse wheel over the rail
  changes tabs.
- Grid of tiles (5 per row, section headers inside a tab): real item icons at 1.5x, name, a green dot when you can make
  it now, dimmed when materials are missing, output count, "recipe 2 of 2" marker, hover glow, accent outline on the
  selection, a short glow on the tile you just made. Smooth wheel scroll with a scrollbar.
- Paper detail card (the Handbook's paper): big icon (hover = full tooltip), name, "Makes 4" / tab · section, what the
  item does (its own tooltip lines), every material with icon and HAVE / NEED in green / red ("any planks" for tags,
  scroll when many), status line (ready / missing n / inventory full / creative / sewing target), buttons: Craft (Enter),
  Craft ×5 (Shift+Enter) at the Reloading Bench, "Sew it on" for sewing recipes, "Fit to <held>" when the item is a part
  that fits what you hold.
- Fit tab: hotbar picker (selects the slot you hold), the held weapon big on a paper card with its fitted parts and its
  own tooltip, one row per slot (optic, muzzle, magazine, grip, stock, bipod, reel) with the fitted part + Remove and a
  chip for every part that fits (count you carry; click = fit / swap; "+" = not owned, click to jump to its recipe).
- Refit arrows tab: your arrow stacks (shaft, head, count), the head grid with counts (last cell = take heads off), current
  vs new head bars (penetration, blood trail, damage, recovery), summary, status, Refit 1 / Heads off / Refit all (Enter).
- Keyboard: Esc closes; letters go to the search (E never closes it); arrows move the selection (in Refit: stacks and
  heads); Enter crafts; Tab / Shift+Tab change tabs; Backspace / Ctrl+Backspace / Delete edit the search; Ctrl+V pastes;
  PageUp / PageDown scroll.
- Respects `reducedMotion` (no open slide, no hover / scroll easing, steady caret). Nothing is filtered or recomputed per
  frame: lists, availability (a quick have/need test, then a full plan), the detail card and the panels are recomputed
  on tab / search / filter / selection / inventory change only (the client compares an inventory fingerprint each tick).
  A click pauses the buttons 420 ms (the server takes one craft per 8 ticks).
- Offline mock: `tools/benches/mock_bench.py` mirrors the layout constants and colours (real Inter fonts, real emblems,
  flat item textures where they exist; vanilla stand-ins for the 3D-rendered items). Renders in
  `tools/benches/mocks/*.png` (1920 x 1080 = GUI scale 3).

### Handbook / journal / onboarding
- Step 1 "Crafting table & your three benches": recipes oak planks, stick, crafting table, the three benches; tasks
  TABLE and BENCH. `Handbook.Task.BENCH` (ordinal kept) moved from step 3 (Bow Tuning Rack) to step 1: done when each of
  the three benches was crafted, placed (item used) or worked at (journal counter `bench.opened.<id>`,
  `benches/BenchProgress`), or when the old task was already credited. Path order: TABLE, BENCH, ARROWS, TIPS, ...
- Step 2 "Bow & arrows": field bow (Gunsmith), hunting + primitive arrows (Reloading), target. Step 3 "Arrow tips":
  field point, broadhead, refit card. Step 7 drops the clothing workbench card. Step 8: Ridgeline rifle + .308.
- Recipe cards (`guide/client/HandbookUi`): bench recipes are drawn as their ingredients (no grid at a bench) and
  labelled "Gunsmith's Bench · Bows" etc. (`bench.frontierhunts.where.<tab>`); Clothing Table cards say "Frontier
  Workbench · Clothing"; the tip-fit card says "Reloading Bench · Refit arrows".
- Lang (fragment `patch/_merge/assets/frontierhunts/lang/en_us.json/zzzzzzz_benches.json`, generated by
  `tools/benches/lang_en.py`): step / task texts, journal checklist hints, expedition texts, item notes (furs, lining,
  mittens, jerry can), first-hunt knife lines, ridgeline sight line. Java strings: arrow / tip / weapon / magazine /
  thermal tooltips, the campaign mission "Equipment for the valley", camp screen line, expedition lodge perks.

## Files

### New (owned by this workstream)
- `src/com/formaworks/frontierhunts/benches/`: `Bench`, `BenchTab`, `BenchRecipes`, `BenchBlock`, `BenchContent`
  (self-registering: blocks, items, menus, recipe type + serializers, creative tab placement), `BenchCatalog`,
  `BenchCrafting`, `BenchMenu`, `BenchProgress`, `BenchLabels`, `OldBenches`.
- `src/com/formaworks/frontierhunts/benches/client/`: `BenchScreen`, `BenchFitPanel`, `BenchRefitPanel`, `BenchDraw`,
  `BenchClient` (screens, recipe-book category, retired tooltip, cache reset).
- `src/com/formaworks/frontierhunts/client/mixin/BenchGiveMixin.java` + `patch/_merge/frontierhunts.client.mixins.json/zzzzzzz_benches.json` (`{"mixins": ["BenchGiveMixin"]}`).
- Data: `patch/assets/frontierhunts/blockstates/{frontier_workbench,gunsmith_bench,reloading_bench}.json` (facing n/e/s/w
  -> y 0/90/180/270, `bench_part=single` -> left model), `patch/data/frontierhunts/loot_table/blocks/<id>.json`,
  `patch/data/frontierhunts/recipe/<id>.json` + `advancement/recipes/unlock/<id>.json` (unlocked by holding any log),
  `patch/_merge/data/minecraft/tags/block/mineable/axe.json/zzzzzzz_benches.json`, `patch/_remove/benches.txt`,
  the converted recipe JSONs, `patch/data/frontierhunts/structure/expedition/*.nbt`, lang fragment.
- Tools: `tools/benches/gen_data.py`, `nbt.py`, `lang_en.py`, `table.py`, `mock_bench.py`, `recipes.json`, `mocks/`.
- QA: `tools/qa/harness/src/com/formaworks/frontierqa/BenchTest.java`, `tools/qa/bench.commands`.

### Shared files (small edits, each marked `[benches]`)
| file | change |
|---|---|
| `client/FrontierGuiScale.java` | `"com.formaworks.frontierhunts.benches.client.BenchScreen", // [benches] the three workbenches` added to SCREENS |
| `guide/client/HandbookUi.java` | in `build()`: `String bench = com.formaworks.frontierhunts.benches.BenchLabels.where(r, result); if (bench != null) { ...packed ingredients...; return new View(id, w, h, packed, result, bench); }` |
| `guide/client/HandbookScreen.java` | `RECIPES` rows 1, 2, 3, 7, 8 (see above) |
| `onboard/Handbook.java` | `BENCH(1, null, null, "frontierhunts:frontier_workbench")`; `ORDER` starts `Task.TABLE, Task.BENCH, Task.ARROWS, Task.TIPS, ...` |
| `onboard/Onboarding.java` | checklist icon `frontierhunts:frontier_workbench`; `if (counter(p, C_BENCH) > 0 \|\| com.formaworks.frontierhunts.benches.BenchProgress.allThree(p, st))` |
| `livingworld/plan/kinds/OutfitterPost.java` | `Kit.wide(c, "gunsmith_bench" ...)`, `"reloading_bench"`, `"frontier_workbench"` |
| `livingworld/plan/kinds/HuntingCamp.java` | `"reloading_bench"` (was bow_tuning_rack), `"frontier_workbench"` (was tent_bench) |
| `camps/CampService.java` | `UPGRADE_TITLES = {"Lodge stores", "Reloading Bench", "Gunsmith's Bench", "Frontier Workbench"}` (items modernised by the mixin) |
| `client/ExpeditionScreen.java` | lodge icons `OldBenches.modernise(...)`; three fallback strings |
| `camps/client/CampScreen.java`, `expedition/Campaign.java`, `expedition/ExpeditionWeapon.java`, `expedition/ExpeditionGear.java`, `hunting/ArrowTipItem.java`, `hunting/HuntArrowItem.java` | one user-facing string each |
| `fs/resources/data/frontierstructures/recipe/*.json` (7) | type -> `frontierhunts:bench_shaped` |
| `fs/resources/data/frontierstructures/structure/expedition/settlement_{hamlet,outfitter,fishing,smokehouse}.nbt` | palette swap (gen_data.py) |
| `patch/data/frontierhunts/recipe/**` (existing files) | `"type"` line only |
| `tools/qa/harness/.../FrontierQa.java` | `.then(Commands.literal("bench")...BenchTest.run(...))` |

Old code left in place (unreachable for players, keeps old saves loading): `workshop/WorkbenchMenu`, `WorkshopKind`,
`EquipmentCatalog`, `client/WorkbenchScreen`, base `AttachmentMenu` / `AttachmentScreen`, `ExpeditionStation` bench ids.

## In-game test script (coordinator)
Build the jar as usual, then a dedicated server with the QA harness (`QA_MOD_CP=<classes> bash tools/qa/harness/build.sh <jar>`):
1. Console: run `tools/qa/bench.commands` (`fhqa bench recipes`, `table`, `craft`, `batch`, `refit`, `old`). Every line must
   read `[FHQA] bench PASS ...`. (A FakePlayer ignores stats and never opens menus; those two are checked below.)
2. Log: no `Unknown recipe category`, no recipe parse errors for `frontierhunts:bench_*`, no missing model / blockstate
   for the three benches, no mixin errors for `BenchGiveMixin`.
3. Client, new world (survival): the Handbook's first page says "Crafting table & your three benches". Punch logs, make
   a crafting table; the green recipe book shows the three benches (unlocked by holding a log). Make all three (sticks /
   flint / cobblestone on top of planks and logs); place them (two blocks wide; they face you). Handbook step 1 ticks
   BENCH once all three are made / placed.
4. At the crafting table, lay out a field bow (stick/leather/string as before): nothing. The recipe book shows no
   Frontier items except the three benches and the handbook.
5. Open each bench: header colour + emblem + subtitle, tabs with counts, tiles with green dots for what you can make,
   search ("bow", "scope"), the Can craft filter, arrows/Enter/Tab/Esc, wheel scroll. Hover the big icon and a material
   row (tooltips). Resize the window (1280x720 ... 4K): nothing overlaps.
6. Gunsmith: craft a Field Recurve Bow (statistics screen: "Crafted" counts it; Handbook task progress). Get a
   Ridgeline (`/give @s frontierhunts:ridgeline_rifle`) and a six power scope; Fit tab: pick the rifle in the hotbar strip,
   click the scope chip (swaps the factory scope, which comes back), Remove, magazine, bipod. Click a "+" chip: jumps to
   that part's recipe on Attachments.
7. Reloading: Craft and Craft ×5 rifle rounds; Refit arrows: pick a stack, click Field Point, Refit all; Heads off.
8. Frontier: sew a fur lining (hold a coat: "Sew it on"); make pemmican (spoilage stamp: tooltip shows freshness);
   select the Fishing Drag Kit with a rod in hand: "Fit to Field Fishing Rod".
9. Old world with old benches: right-click each old bench -> rebuilt as its successor, both halves, same facing, screen
   opens, gold message; a Bow Tuning Rack with a bow on it gives the bow back. `/give @s frontierhunts:ammo_reloader`:
   tooltip says retired; place it with a free block to its left -> a Reloading Bench; against a wall on the left ->
   refused with a message, item kept. Creative tabs: no old benches; the three new ones follow the Handbook.
10. `/place structure frontierstructures:settlement_outfitter` (or find a village): new benches inside, no half benches.
11. Expedition journal > Lodge: upgrade icons are the new benches; buying rank 2 gives a Reloading Bench.

## Risks / notes for the coordinator
- **Re-run `python3 tools/benches/gen_data.py <repo>` (and `tools/benches/table.py`) after merging any branch that adds
  or edits crafting recipes** (guns2, fishing2 ...). Unconverted ones still work at their bench (and the table), are
  placed by the fallback rule, and `/fhqa bench recipes` names them; add new items to `BenchCatalog.put(...)`.
- Converted recipe files that already lived in `patch/` changed only on their `"type"` line; a branch editing the same
  files may conflict on that line: take theirs, then re-run gen_data.py.
- The old Bow Tuning Rack / Ammo Reloader / Fishing Station counted as "expedition stations" (for buying at the stores
  within 6 blocks); the new benches do not (the Expedition Board, Lodge Stores, Trophy Plinth and Gun Rack still do).
- Arrow heads can no longer be swapped in a crafting grid (by design: Reloading Bench only).

## Every bench recipe (tools/benches/table.py)

| # | Bench | Tab | Section | Recipe | Result | Count |
|---|---|---|---|---|---|---|
| 1 | Frontier Workbench | Hunting gear | Calls | `frontierhunts:bleat_call` | `frontierhunts:bleat_call` | 1 |
| 2 | Frontier Workbench | Hunting gear | Calls | `frontierhunts:expedition_deer_call` | `frontierhunts:deer_call` | 1 |
| 3 | Frontier Workbench | Hunting gear | Calls | `frontierhunts:grunt_tube` | `frontierhunts:grunt_tube` | 1 |
| 4 | Frontier Workbench | Hunting gear | Calls | `frontierhunts:rattling_antlers` | `frontierhunts:rattling_antlers` | 1 |
| 5 | Frontier Workbench | Hunting gear | Calls | `frontierhunts:expedition_predator_call` | `frontierhunts:predator_call` | 1 |
| 6 | Frontier Workbench | Hunting gear | Calls | `frontierhunts:duck_call` | `frontierhunts:duck_call` | 1 |
| 7 | Frontier Workbench | Hunting gear | Calls | `frontierhunts:horse_whistle` | `frontierhunts:horse_whistle` | 1 |
| 8 | Frontier Workbench | Hunting gear | Scent & wind | `frontierhunts:wind_checker` | `frontierhunts:wind_checker` | 1 |
| 9 | Frontier Workbench | Hunting gear | Scent & wind | `frontierhunts:expedition_scent_cover` | `frontierhunts:scent_cover` | 1 |
| 10 | Frontier Workbench | Hunting gear | Scent & wind | `frontierhunts:whitetail_scent_decoy` | `frontierhunts:whitetail_scent_decoy` | 1 |
| 11 | Frontier Workbench | Hunting gear | Scent & wind | `frontierhunts:mallard_decoy` | `frontierhunts:mallard_decoy` | 2 |
| 12 | Frontier Workbench | Hunting gear | Optics | `frontierhunts:expedition_binoculars` | `frontierhunts:binoculars` | 1 |
| 13 | Frontier Workbench | Hunting gear | Optics | `frontierhunts:expedition_rangefinder` | `frontierhunts:rangefinder` | 1 |
| 14 | Frontier Workbench | Hunting gear | Optics | `frontierhunts:expedition_thermal_binoculars` | `frontierhunts:thermal_binoculars` | 1 |
| 15 | Frontier Workbench | Hunting gear | Optics | `frontierhunts:night_vision_binoculars` | `frontierhunts:night_vision_binoculars` | 1 |
| 16 | Frontier Workbench | Hunting gear | Cameras & lights | `frontierhunts:trail_camera` | `frontierhunts:trail_camera` | 1 |
| 17 | Frontier Workbench | Hunting gear | Cameras & lights | `frontierhunts:camera_base_station` | `frontierhunts:camera_base_station` | 1 |
| 18 | Frontier Workbench | Hunting gear | Cameras & lights | `frontierhunts:field_camera` | `frontierhunts:field_camera` | 1 |
| 19 | Frontier Workbench | Hunting gear | Cameras & lights | `frontierhunts:expedition_field_flashlight` | `frontierhunts:field_flashlight` | 1 |
| 20 | Frontier Workbench | Hunting gear | Cameras & lights | `frontierhunts:field_spotlight` | `frontierhunts:field_spotlight` | 1 |
| 21 | Frontier Workbench | Hunting gear | Cameras & lights | `frontierhunts:field_battery_pack` | `frontierhunts:field_battery_pack` | 2 |
| 22 | Frontier Workbench | Hunting gear | Field kit | `frontierhunts:skinning_tool` | `frontierhunts:skinning_tool` | 1 |
| 23 | Frontier Workbench | Hunting gear | Field kit | `frontierhunts:expedition_medkit` | `frontierhunts:medkit` | 1 |
| 24 | Frontier Workbench | Hunting gear | Field kit | `frontierhunts:hunter_pack` | `frontierhunts:hunter_pack` | 1 |
| 25 | Frontier Workbench | Hunting gear | Field kit | `frontierhunts:hunters_quiver` | `frontierhunts:hunters_quiver` | 1 |
| 26 | Frontier Workbench | Hunting gear | Field kit | `frontierhunts:wingsuit` | `frontierhunts:wingsuit` | 1 |
| 27 | Frontier Workbench | Hunting gear | Stands & blinds | `frontierhunts:field_blind` | `frontierhunts:field_blind` | 1 |
| 28 | Frontier Workbench | Hunting gear | Stands & blinds | `frontierhunts:tree_stand` | `frontierhunts:tree_stand` | 1 |
| 29 | Frontier Workbench | Hunting gear | Stands & blinds | `frontierhunts:tower_blind` | `frontierhunts:tower_blind` | 1 |
| 30 | Frontier Workbench | Hunting gear | Stands & blinds | `frontierhunts:blind_chair` | `frontierhunts:blind_chair` | 1 |
| 31 | Frontier Workbench | Hunting gear | Stands & blinds | `frontierhunts:tower_chair` | `frontierhunts:tower_chair` | 1 |
| 32 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_hood` | `frontierhunts:ghillie_hood` | 1 |
| 33 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_jacket` | `frontierhunts:ghillie_jacket` | 1 |
| 34 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_trousers` | `frontierhunts:ghillie_trousers` | 1 |
| 35 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_grassland_hood` | `frontierhunts:ghillie_grassland_hood` | 1 |
| 36 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_grassland_jacket` | `frontierhunts:ghillie_grassland_jacket` | 1 |
| 37 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_grassland_trousers` | `frontierhunts:ghillie_grassland_trousers` | 1 |
| 38 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_wetland_hood` | `frontierhunts:ghillie_wetland_hood` | 1 |
| 39 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_wetland_jacket` | `frontierhunts:ghillie_wetland_jacket` | 1 |
| 40 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_wetland_trousers` | `frontierhunts:ghillie_wetland_trousers` | 1 |
| 41 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_snow_hood` | `frontierhunts:ghillie_snow_hood` | 1 |
| 42 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_snow_jacket` | `frontierhunts:ghillie_snow_jacket` | 1 |
| 43 | Frontier Workbench | Clothing | Ghillie suits | `frontierhunts:ghillie_snow_trousers` | `frontierhunts:ghillie_snow_trousers` | 1 |
| 44 | Frontier Workbench | Clothing | Camo coveralls | `frontierhunts:timber_camo_coveralls` | `frontierhunts:timber_camo_coveralls` | 1 |
| 45 | Frontier Workbench | Clothing | Camo coveralls | `frontierhunts:autumn_camo_coveralls` | `frontierhunts:autumn_camo_coveralls` | 1 |
| 46 | Frontier Workbench | Clothing | Camo coveralls | `frontierhunts:marsh_camo_coveralls` | `frontierhunts:marsh_camo_coveralls` | 1 |
| 47 | Frontier Workbench | Clothing | Camo coveralls | `frontierhunts:prairie_camo_coveralls` | `frontierhunts:prairie_camo_coveralls` | 1 |
| 48 | Frontier Workbench | Clothing | Camo coveralls | `frontierhunts:snow_camo_coveralls` | `frontierhunts:snow_camo_coveralls` | 1 |
| 49 | Frontier Workbench | Clothing | Camo coveralls | `frontierhunts:digital_camo_coveralls` | `frontierhunts:digital_camo_coveralls` | 1 |
| 50 | Frontier Workbench | Clothing | Camo coveralls | `frontierhunts:blaze_camo_coveralls` | `frontierhunts:blaze_camo_coveralls` | 1 |
| 51 | Frontier Workbench | Clothing | Scent control | `frontierhunts:scent_suit` | `frontierhunts:scent_suit` | 1 |
| 52 | Frontier Workbench | Clothing | Scent control | `frontierhunts:clothing_table/carbon_hood` | `frontierhunts:carbon_hood` | 1 |
| 53 | Frontier Workbench | Clothing | Scent control | `frontierhunts:clothing_table/carbon_jacket` | `frontierhunts:carbon_jacket` | 1 |
| 54 | Frontier Workbench | Clothing | Scent control | `frontierhunts:clothing_table/carbon_trousers` | `frontierhunts:carbon_trousers` | 1 |
| 55 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/fur_hat` | `frontierhunts:fur_hat` | 1 |
| 56 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/buckskin_coat` | `frontierhunts:buckskin_coat` | 1 |
| 57 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/buckskin_leggings` | `frontierhunts:buckskin_leggings` | 1 |
| 58 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/fur_mukluks` | `frontierhunts:fur_mukluks` | 1 |
| 59 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/hide_robe` | `frontierhunts:hide_robe` | 1 |
| 60 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/bear_fur_coat` | `frontierhunts:bear_fur_coat` | 1 |
| 61 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/fur_lining` | `frontierhunts:fur_lining` | 1 |
| 62 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/fur_lining_from_bear_fur` | `frontierhunts:fur_lining` | 1 |
| 63 | Frontier Workbench | Clothing | Furs & hides | `frontierhunts:clothing_table/fur_mittens` | `frontierhunts:fur_mittens` | 1 |
| 64 | Frontier Workbench | Clothing | Sewing | `frontierhunts:clothing_table/sew_fur_lining` | `frontierhunts:fur_lining` | 1 |
| 65 | Frontier Workbench | Clothing | Sewing | `frontierhunts:clothing_table/sew_fur_mittens` | `frontierhunts:fur_mittens` | 1 |
| 66 | Frontier Workbench | Camp | Tents | `frontierhunts:trail_dome_tent` | `frontierhunts:trail_dome_tent` | 1 |
| 67 | Frontier Workbench | Camp | Tents | `frontierhunts:pup_tent` | `frontierhunts:pup_tent` | 1 |
| 68 | Frontier Workbench | Camp | Tents | `frontierhunts:solo_ridge_tent` | `frontierhunts:solo_ridge_tent` | 1 |
| 69 | Frontier Workbench | Camp | Tents | `frontierhunts:backpacker_dome_tent` | `frontierhunts:backpacker_dome_tent` | 1 |
| 70 | Frontier Workbench | Camp | Tents | `frontierhunts:woodland_camp_tent` | `frontierhunts:woodland_camp_tent` | 1 |
| 71 | Frontier Workbench | Camp | Tents | `frontierhunts:hunters_canvas_tent` | `frontierhunts:hunters_canvas_tent` | 1 |
| 72 | Frontier Workbench | Camp | Tents | `frontierhunts:canvas_wall_tent` | `frontierhunts:canvas_wall_tent` | 1 |
| 73 | Frontier Workbench | Camp | Tents | `frontierhunts:bell_tent` | `frontierhunts:bell_tent` | 1 |
| 74 | Frontier Workbench | Camp | Tents | `frontierhunts:family_cabin_tent` | `frontierhunts:family_cabin_tent` | 1 |
| 75 | Frontier Workbench | Camp | Camp gear | `frontierhunts:camp_post` | `frontierhunts:camp_post` | 1 |
| 76 | Frontier Workbench | Camp | Camp gear | `frontierhunts:camp_chair` | `frontierhunts:camp_chair` | 1 |
| 77 | Frontier Workbench | Camp | Camp gear | `frontierhunts:reserve_camp_cot` | `frontierhunts:camp_cot` | 1 |
| 78 | Frontier Workbench | Camp | Camp gear | `frontierhunts:survival/hide_bedroll` | `frontierhunts:hide_bedroll` | 1 |
| 79 | Frontier Workbench | Camp | Camp gear | `frontierhunts:log_stump_seat` | `frontierhunts:log_stump_seat` | 1 |
| 80 | Frontier Workbench | Camp | Camp gear | `frontierhunts:trail_bench` | `frontierhunts:trail_bench` | 2 |
| 81 | Frontier Workbench | Camp | Camp gear | `frontierhunts:reserve_stacked_firewood` | `frontierhunts:stacked_firewood` | 4 |
| 82 | Frontier Workbench | Camp | Cooking & curing | `frontierhunts:dutch_oven` | `frontierhunts:dutch_oven` | 1 |
| 83 | Frontier Workbench | Camp | Cooking & curing | `frontierhunts:smokehouse` | `frontierhunts:smokehouse` | 1 |
| 84 | Frontier Workbench | Camp | Cooking & curing | `frontierhunts:survival/drying_rack` | `frontierhunts:drying_rack` | 1 |
| 85 | Frontier Workbench | Camp | Cooking & curing | `frontierhunts:tanning_rack` | `frontierhunts:tanning_rack` | 1 |
| 86 | Frontier Workbench | Camp | Cooking & curing | `frontierhunts:game_pole` | `frontierhunts:game_pole` | 1 |
| 87 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:lodge_stores` | `frontierhunts:lodge_stores` | 1 |
| 88 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:gun_rack` | `frontierhunts:gun_rack` | 1 |
| 89 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:bow_stand` | `frontierhunts:bow_stand` | 1 |
| 90 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:trophy_plinth` | `frontierhunts:trophy_plinth` | 1 |
| 91 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:big_buck_board` | `frontierhunts:big_buck_board` | 1 |
| 92 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:contract_board` | `frontierhunts:contract_board` | 1 |
| 93 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:expedition_board` | `frontierhunts:expedition_board` | 1 |
| 94 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:reserve_lodge_table` | `frontierhunts:lodge_table` | 1 |
| 95 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:lit_lodge_table` | `frontierhunts:lit_lodge_table` | 1 |
| 96 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:reserve_lodge_chair` | `frontierhunts:lodge_chair` | 1 |
| 97 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:reserve_lodge_stove` | `frontierhunts:lodge_stove` | 1 |
| 98 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:reserve_stove_flue` | `frontierhunts:stove_flue` | 4 |
| 99 | Frontier Workbench | Camp | Lodge & displays | `frontierhunts:stove_roof_flashing` | `frontierhunts:stove_roof_flashing` | 1 |
| 100 | Frontier Workbench | Fishing | Tackle | `frontierhunts:field_fishing_rod` | `frontierhunts:field_fishing_rod` | 1 |
| 101 | Frontier Workbench | Fishing | Tackle | `frontierhunts:expedition_fishing_drag_kit` | `frontierhunts:fishing_drag_kit` | 1 |
| 102 | Frontier Workbench | Fishing | Tackle | `frontierhunts:glow_lure` | `frontierhunts:glow_lure` | 1 |
| 103 | Frontier Workbench | Fishing | Tackle | `frontierhunts:chum_bucket` | `frontierhunts:chum_bucket` | 1 |
| 104 | Frontier Workbench | Fishing | Tackle | `frontierhunts:landing_net` | `frontierhunts:landing_net` | 1 |
| 105 | Frontier Workbench | Fishing | Tackle | `frontierhunts:fish_finder` | `frontierhunts:fish_finder` | 1 |
| 106 | Frontier Workbench | Vehicles | ATV | `frontierhunts:atv` | `frontierhunts:atv` | 1 |
| 107 | Frontier Workbench | Vehicles | ATV | `frontierhunts:atv_cargo_box` | `frontierhunts:atv_cargo_box` | 1 |
| 108 | Frontier Workbench | Vehicles | ATV | `frontierhunts:atv_can_carrier` | `frontierhunts:atv_can_carrier` | 1 |
| 109 | Frontier Workbench | Vehicles | ATV | `frontierhunts:jerry_can` | `frontierhunts:jerry_can` | 1 |
| 110 | Frontier Workbench | Vehicles | ATV | `frontierhunts:jerry_can_from_lava` | `frontierhunts:jerry_can` | 1 |
| 111 | Frontier Workbench | Vehicles | Snow | `frontierhunts:sled` | `frontierhunts:sled` | 1 |
| 112 | Frontier Workbench | Vehicles | Snow | `frontierhunts:snowmobile` | `frontierhunts:snowmobile` | 1 |
| 113 | Frontier Workbench | Vehicles | Boats | `frontierhunts:jon_boat` | `frontierhunts:jon_boat` | 1 |
| 114 | Frontier Workbench | Vehicles | Boats | `frontierhunts:rowboat` | `frontierhunts:rowboat` | 1 |
| 115 | Frontier Workbench | Building | Timber & railings | `frontierhunts:reserve_weathered_planks` | `frontierhunts:weathered_planks` | 2 |
| 116 | Frontier Workbench | Building | Timber & railings | `frontierhunts:reserve_pine_slab` | `frontierhunts:pine_slab` | 6 |
| 117 | Frontier Workbench | Building | Timber & railings | `frontierhunts:reserve_pine_stairs` | `frontierhunts:pine_stairs` | 4 |
| 118 | Frontier Workbench | Building | Timber & railings | `frontierhunts:reserve_pine_fence` | `frontierhunts:pine_fence` | 3 |
| 119 | Frontier Workbench | Building | Timber & railings | `frontierhunts:timber_brace` | `frontierhunts:timber_brace` | 4 |
| 120 | Frontier Workbench | Building | Timber & railings | `frontierhunts:timber_cross_brace` | `frontierhunts:timber_cross_brace` | 1 |
| 121 | Frontier Workbench | Building | Timber & railings | `frontierhunts:timber_stair_guard` | `frontierhunts:timber_stair_guard` | 1 |
| 122 | Frontier Workbench | Building | Timber & railings | `frontierhunts:lookout_brace` | `frontierhunts:lookout_brace` | 6 |
| 123 | Frontier Workbench | Building | Timber & railings | `frontierhunts:lookout_cross_brace` | `frontierhunts:lookout_cross_brace` | 6 |
| 124 | Frontier Workbench | Building | Timber & railings | `frontierhunts:lookout_deck_rail` | `frontierhunts:lookout_deck_rail` | 6 |
| 125 | Frontier Workbench | Building | Timber & railings | `frontierhunts:lookout_stair_rail` | `frontierhunts:lookout_stair_rail` | 6 |
| 126 | Frontier Workbench | Building | Timber & railings | `frontierstructures:lookout_brace` | `frontierstructures:lookout_brace` | 6 |
| 127 | Frontier Workbench | Building | Timber & railings | `frontierstructures:lookout_cross_brace` | `frontierstructures:lookout_cross_brace` | 6 |
| 128 | Frontier Workbench | Building | Timber & railings | `frontierstructures:lookout_deck_rail` | `frontierstructures:lookout_deck_rail` | 6 |
| 129 | Frontier Workbench | Building | Timber & railings | `frontierstructures:lookout_stair_rail` | `frontierstructures:lookout_stair_rail` | 6 |
| 130 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_shingles` | `frontierhunts:roof_shingles` | 8 |
| 131 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_slab` | `frontierhunts:roof_slab` | 6 |
| 132 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_stairs` | `frontierhunts:roof_stairs` | 4 |
| 133 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_slope_low` | `frontierhunts:roof_slope_low` | 6 |
| 134 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_slope_high` | `frontierhunts:roof_slope_high` | 6 |
| 135 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_gable_low` | `frontierhunts:roof_gable_low` | 6 |
| 136 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_gable_high` | `frontierhunts:roof_gable_high` | 6 |
| 137 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_ridge_low` | `frontierhunts:roof_ridge_low` | 6 |
| 138 | Frontier Workbench | Building | Roofing | `frontierhunts:reserve_roof_ridge_high` | `frontierhunts:roof_ridge_high` | 6 |
| 139 | Frontier Workbench | Building | Canvas | `frontierhunts:reserve_canvas_wall` | `frontierhunts:canvas_wall` | 8 |
| 140 | Frontier Workbench | Building | Canvas | `frontierhunts:reserve_tent_slope` | `frontierhunts:tent_slope` | 6 |
| 141 | Frontier Workbench | Building | Canvas | `frontierhunts:reserve_tent_gable` | `frontierhunts:tent_gable` | 6 |
| 142 | Frontier Workbench | Building | Canvas | `frontierhunts:reserve_tent_ridge` | `frontierhunts:tent_ridge` | 6 |
| 143 | Frontier Workbench | Building | Canvas | `frontierhunts:tent_end_ridge` | `frontierhunts:tent_end_ridge` | 1 |
| 144 | Frontier Workbench | Building | Stone | `frontierhunts:reserve_fieldstone` | `frontierhunts:fieldstone` | 2 |
| 145 | Frontier Workbench | Building | Stone | `frontierhunts:reserve_fieldstone_stairs` | `frontierhunts:fieldstone_stairs` | 4 |
| 146 | Frontier Workbench | Building | Stone | `frontierhunts:reserve_reserve_granite` | `frontierhunts:reserve_granite` | 1 |
| 147 | Frontier Workbench | Building | Ground | `frontierhunts:reserve_forest_loam` | `frontierhunts:forest_loam` | 2 |
| 148 | Frontier Workbench | Building | Ground | `frontierhunts:reserve_moss_floor` | `frontierhunts:moss_floor` | 2 |
| 149 | Frontier Workbench | Building | Ground | `frontierhunts:alpine_pasture` | `frontierhunts:alpine_pasture` | 3 |
| 150 | Frontier Workbench | Building | Ground | `frontierhunts:fallen_branch` | `frontierhunts:fallen_branch` | 2 |
| 151 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierhunts:reserve_cabin_door` | `frontierhunts:cabin_door` | 3 |
| 152 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierhunts:reserve_ranger_window` | `frontierhunts:ranger_window` | 4 |
| 153 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierstructures:spruce_casement_window` | `frontierstructures:spruce_casement_window` | 4 |
| 154 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierstructures:spruce_picture_window` | `frontierstructures:spruce_picture_window` | 4 |
| 155 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierstructures:dark_oak_lattice_window` | `frontierstructures:dark_oak_lattice_window` | 4 |
| 156 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierhunts:reserve_rope_ladder` | `frontierhunts:rope_ladder` | 4 |
| 157 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierhunts:reserve_cabin_lantern` | `frontierhunts:cabin_lantern` | 1 |
| 158 | Frontier Workbench | Building | Doors, windows & fixtures | `frontierhunts:reserve_trail_sign` | `frontierhunts:trail_sign` | 1 |
| 159 | Frontier Workbench | Range | Targets | `frontierhunts:shooting_target` | `frontierhunts:shooting_target` | 1 |
| 160 | Frontier Workbench | Range | Targets | `frontierhunts:foam_deer_target` | `frontierhunts:foam_deer_target` | 1 |
| 161 | Frontier Workbench | Range | Targets | `frontierhunts:steel_deer_target` | `frontierhunts:steel_deer_target` | 1 |
| 162 | Frontier Workbench | Range | Targets | `frontierhunts:steel_gong` | `frontierhunts:steel_gong` | 1 |
| 163 | Frontier Workbench | Range | Targets | `frontierhunts:steel_popper` | `frontierhunts:steel_popper` | 1 |
| 164 | Frontier Workbench | Range | Targets | `frontierhunts:steel_spinner` | `frontierhunts:steel_spinner` | 1 |
| 165 | Frontier Workbench | Range | Targets | `frontierhunts:range_marker` | `frontierhunts:range_marker` | 3 |
| 166 | Frontier Workbench | Food | Trail food | `frontierhunts:survival/pemmican` | `frontierhunts:pemmican` | 3 |
| 167 | Frontier Workbench | Food | Trail food | `frontierhunts:campcook/honey_pemmican` | `frontierhunts:honey_pemmican` | 3 |
| 168 | Frontier Workbench | Food | Trail food | `frontierhunts:campcook/raw_game_sausage` | `frontierhunts:raw_game_sausage` | 4 |
| 169 | Gunsmith's Bench | Rifles | Rifles | `frontierhunts:ridgeline_rifle` | `frontierhunts:ridgeline_rifle` | 1 |
| 170 | Gunsmith's Bench | Rifles | Rifles | `frontierhunts:expedition_lever_rifle` | `frontierhunts:lever_rifle` | 1 |
| 171 | Gunsmith's Bench | Rifles | Rifles | `frontierhunts:expedition_semi_auto_rifle` | `frontierhunts:semi_auto_rifle` | 1 |
| 172 | Gunsmith's Bench | Rifles | Rifles | `frontierhunts:expedition_tranquilizer_rifle` | `frontierhunts:tranquilizer_rifle` | 1 |
| 173 | Gunsmith's Bench | Shotguns | Shotguns | `frontierhunts:expedition_pump_shotgun` | `frontierhunts:pump_shotgun` | 1 |
| 174 | Gunsmith's Bench | Shotguns | Shotguns | `frontierhunts:expedition_semi_auto_shotgun` | `frontierhunts:semi_auto_shotgun` | 1 |
| 175 | Gunsmith's Bench | Shotguns | Shotguns | `frontierhunts:expedition_double_barrel` | `frontierhunts:double_barrel` | 1 |
| 176 | Gunsmith's Bench | Handguns | Handguns | `frontierhunts:expedition_field_pistol` | `frontierhunts:field_pistol` | 1 |
| 177 | Gunsmith's Bench | Handguns | Handguns | `frontierhunts:expedition_revolver` | `frontierhunts:revolver` | 1 |
| 178 | Gunsmith's Bench | Handguns | Handguns | `frontierhunts:expedition_flare_gun` | `frontierhunts:flare_gun` | 1 |
| 179 | Gunsmith's Bench | Bows | Bows | `frontierhunts:field_bow` | `frontierhunts:field_bow` | 1 |
| 180 | Gunsmith's Bench | Bows | Bows | `frontierhunts:expedition_recurve_bow` | `frontierhunts:recurve_bow` | 1 |
| 181 | Gunsmith's Bench | Bows | Bows | `frontierhunts:expedition_compound_bow` | `frontierhunts:compound_bow` | 1 |
| 182 | Gunsmith's Bench | Bows | Bows | `frontierhunts:expedition_crossbow` | `frontierhunts:crossbow` | 1 |
| 183 | Gunsmith's Bench | Bows | Specialty | `frontierhunts:expedition_bowfishing_bow` | `frontierhunts:bowfishing_bow` | 1 |
| 184 | Gunsmith's Bench | Bows | Specialty | `frontierhunts:expedition_bait_launcher` | `frontierhunts:bait_launcher` | 1 |
| 185 | Gunsmith's Bench | Blades | Blades | `frontierhunts:field_knife` | `frontierhunts:field_knife` | 1 |
| 186 | Gunsmith's Bench | Blades | Blades | `frontierhunts:expedition_hunting_spear` | `frontierhunts:hunting_spear` | 1 |
| 187 | Gunsmith's Bench | Attachments | Red dots & prisms | `frontierhunts:expedition_reflex_sight` | `frontierhunts:reflex_sight` | 1 |
| 188 | Gunsmith's Bench | Attachments | Red dots & prisms | `frontierhunts:micro_red_dot` | `frontierhunts:micro_red_dot` | 1 |
| 189 | Gunsmith's Bench | Attachments | Red dots & prisms | `frontierhunts:holographic_sight` | `frontierhunts:holographic_sight` | 1 |
| 190 | Gunsmith's Bench | Attachments | Red dots & prisms | `frontierhunts:two_power_prism` | `frontierhunts:two_power_prism` | 1 |
| 191 | Gunsmith's Bench | Attachments | Scopes | `frontierhunts:four_power_optic` | `frontierhunts:four_power_optic` | 1 |
| 192 | Gunsmith's Bench | Attachments | Scopes | `frontierhunts:ridgeline_scope` | `frontierhunts:ridgeline_scope` | 1 |
| 193 | Gunsmith's Bench | Attachments | Scopes | `frontierhunts:expedition_six_power_scope` | `frontierhunts:six_power_scope` | 1 |
| 194 | Gunsmith's Bench | Attachments | Scopes | `frontierhunts:expedition_eight_power_scope` | `frontierhunts:eight_power_scope` | 1 |
| 195 | Gunsmith's Bench | Attachments | Scopes | `frontierhunts:expedition_twelve_power_scope` | `frontierhunts:twelve_power_scope` | 1 |
| 196 | Gunsmith's Bench | Attachments | Scopes | `frontierhunts:expedition_thermal_scope` | `frontierhunts:thermal_scope` | 1 |
| 197 | Gunsmith's Bench | Attachments | Magazines | `frontierhunts:expedition_sniper_magazine` | `frontierhunts:sniper_magazine` | 1 |
| 198 | Gunsmith's Bench | Attachments | Magazines | `frontierhunts:expedition_pistol_magazine` | `frontierhunts:pistol_magazine` | 1 |
| 199 | Gunsmith's Bench | Attachments | Magazines | `frontierhunts:expedition_extended_magazine` | `frontierhunts:extended_magazine` | 1 |
| 200 | Gunsmith's Bench | Attachments | Muzzle | `frontierhunts:expedition_suppressor` | `frontierhunts:suppressor` | 1 |
| 201 | Gunsmith's Bench | Attachments | Muzzle | `frontierhunts:muzzle_brake` | `frontierhunts:muzzle_brake` | 1 |
| 202 | Gunsmith's Bench | Attachments | Grips, stocks & tools | `frontierhunts:angled_foregrip` | `frontierhunts:angled_foregrip` | 1 |
| 203 | Gunsmith's Bench | Attachments | Grips, stocks & tools | `frontierhunts:expedition_steady_stock` | `frontierhunts:steady_stock` | 1 |
| 204 | Gunsmith's Bench | Attachments | Grips, stocks & tools | `frontierhunts:expedition_bipod` | `frontierhunts:bipod` | 1 |
| 205 | Gunsmith's Bench | Attachments | Grips, stocks & tools | `frontierhunts:attachment_tool` | `frontierhunts:attachment_tool` | 1 |
| 206 | Reloading Bench | Cartridges | Rifle | `frontierhunts:expedition_rifle_round` | `frontierhunts:rifle_round` | 8 |
| 207 | Reloading Bench | Cartridges | Rifle | `frontierhunts:reserve_308` | `frontierhunts:reserve_308` | 8 |
| 208 | Reloading Bench | Cartridges | Pistol | `frontierhunts:expedition_pistol_round` | `frontierhunts:pistol_round` | 8 |
| 209 | Reloading Bench | Cartridges | Shotgun | `frontierhunts:expedition_shotgun_shell` | `frontierhunts:shotgun_shell` | 8 |
| 210 | Reloading Bench | Arrows | Arrows | `frontierhunts:field_arrow` | `frontierhunts:field_arrow` | 4 |
| 211 | Reloading Bench | Arrows | Arrows | `frontierhunts:primitive_arrow` | `frontierhunts:primitive_arrow` | 4 |
| 212 | Reloading Bench | Arrows | Arrows | `frontierhunts:expedition_bowfishing_arrow` | `frontierhunts:bowfishing_arrow` | 8 |
| 213 | Reloading Bench | Arrow tips | Broadheads | `frontierhunts:fixed_broadhead` | `frontierhunts:fixed_broadhead` | 4 |
| 214 | Reloading Bench | Arrow tips | Broadheads | `frontierhunts:mechanical_broadhead` | `frontierhunts:mechanical_broadhead` | 4 |
| 215 | Reloading Bench | Arrow tips | Broadheads | `frontierhunts:cut_on_contact_broadhead` | `frontierhunts:cut_on_contact_broadhead` | 4 |
| 216 | Reloading Bench | Arrow tips | Points | `frontierhunts:field_point` | `frontierhunts:field_point` | 4 |
| 217 | Reloading Bench | Arrow tips | Points | `frontierhunts:judo_point` | `frontierhunts:judo_point` | 4 |
| 218 | Reloading Bench | Arrow tips | Primitive heads | `frontierhunts:flint_point` | `frontierhunts:flint_point` | 3 |
| 219 | Reloading Bench | Arrow tips | Primitive heads | `frontierhunts:obsidian_point` | `frontierhunts:obsidian_point` | 6 |
| 220 | Reloading Bench | Arrow tips | Primitive heads | `frontierhunts:bone_point` | `frontierhunts:bone_point` | 3 |
| 221 | Reloading Bench | Arrow tips | Tracers | `frontierhunts:tracer_broadhead` | `frontierhunts:tracer_broadhead` | 4 |
| 222 | Reloading Bench | Arrow tips | Tracers | `frontierhunts:tracer_field_point` | `frontierhunts:tracer_field_point` | 4 |
| 223 | Reloading Bench | Arrow tips | Tracers | `frontierhunts:tracer_ice_broadhead` | `frontierhunts:tracer_ice_broadhead` | 4 |
| 224 | Reloading Bench | Darts & flares | Darts, flares & bait | `frontierhunts:expedition_tranquilizer_dart` | `frontierhunts:tranquilizer_dart` | 8 |
| 225 | Reloading Bench | Darts & flares | Darts, flares & bait | `frontierhunts:expedition_flare_round` | `frontierhunts:flare_round` | 8 |
| 226 | Reloading Bench | Darts & flares | Darts, flares & bait | `frontierhunts:expedition_bait` | `frontierhunts:bait` | 1 |

Total: 226 recipes (212 bench recipes + 14 Clothing Table recipes). Per tab: HUNTING 31, CLOTHING 34, CAMP 34, FISHING 6, VEHICLES 9, BUILDING 44, RANGE 7, FOOD 3, MISC 0, RIFLES 4, SHOTGUNS 3, HANDGUNS 3, BOWS 6, BLADES 2, ATTACHMENTS 19, CARTRIDGES 4, ARROWS 3, TIPS 11, SPECIAL 3
