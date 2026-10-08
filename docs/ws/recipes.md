# Workstream `recipes`: fur clothing at the Clothing Table, survival recipe audit

Branch `recipes`. `tools/compile.sh` → exit=0; `tools/build.py recipes-test .` builds; `tools/check_jar.py <jar> . --base master`
→ 0 errors, 0 warnings; `python3 tools/recipe_audit.py <jar>` → **PASS (0 unobtainable, 0 errors, 0 structure-only, 26
documented exceptions, 0 recipe-book recipes without an unlock)**. Not run in game here.

User request: "new gear u just added the fur stuff should only be in clothing table not in create tab and should be craftable
from there make sure everything in the mod has the necessary crafting recipes for when in survival".

## 1. Fur & hide clothing → Clothing Table only
The mod's Clothing Table is the existing **`frontierhunts:clothing_workbench`** (`WorkshopKind.CLOTHING`, the "Field clothing"
`WorkbenchMenu`/`WorkbenchScreen`, where ghillie, camo coveralls and the carbon suit are made).
* New recipe type **`frontierhunts:clothing_table`** (`recipes/ClothingTableRecipe`, registered in `recipes/RecipesContent`):
  shaped-recipe JSON (pattern/key/result/category/group) + optional `"sew"`. Own `RecipeType`, so **a crafting table can never
  make these**; `matches()` is always false and the recipe is `isSpecial()` (never in / never unlocked by the vanilla recipe
  book). Synced to clients with its own stream codec.
* `workshop/EquipmentCatalog`: the CLOTHING kind lists its crafting recipes (unchanged order) **followed by** the
  Clothing Table recipes, sorted by group (furs → linings/mittens → sewing → carbon layer) then id. Same list on client and
  server, so button indices agree. Icons = result item; categories = recipe `category` + group order.
* Server side: crafting goes through the existing `WorkbenchMenu.clickMenuButton` flow (player is the menu's ServerPlayer,
  `stillValid` = same station kind within 8 blocks, not spectator, index range, 8-tick rate limit, inventory plan recomputed
  on the server from the real inventory, consumes exactly the ingredients). Added in `plan()`: a Clothing Table recipe is
  refused at any other station; the product (never the list icon) is handed out.
* **Removed** the crafting-table recipes `recipe/survival/{fur_hat,buckskin_coat,buckskin_leggings,bear_fur_coat,hide_robe,
  fur_mukluks,fur_lining,fur_lining_from_bear_fur,fur_mittens}.json` and the grid sewing recipe `survival/fur_lining_sewing.json`.
* New `recipe/clothing_table/*.json` (string added as thread where the survival recipe had none):

| item | pattern | key |
|---|---|---|
| Fur Trapper Hat | ` F `/`FHF`/` S ` | F #tanned_furs, H tanned buckskin, S string |
| Buckskin Coat | `H H`/`HHH`/`HSH` | H tanned buckskin, S string |
| Buckskin Leggings | `HSH`/`H H`/`H H` | same |
| Bear Fur Coat | `B B`/`BHB`/`HSH` | B tanned bear fur, H buckskin, S string |
| Heavy Hide Robe | `TFT`/`TST` | T tanned heavy hide, F #tanned_furs, S string |
| Fur Mukluks | `F F`/`HSH` | F #tanned_furs, H #tanned_hides, S string |
| Fur Lining | `FFH` or `BH` | 2 tanned fur + buckskin, or bear fur + buckskin |
| Fur Mittens | `FSF` | tanned fur, string |
| Sew Fur Lining (into the garment in your hand) | `L` | fur lining |
| Sew Fur Mittens (onto the chest piece in your hand) | `M` | fur mittens |
| Carbon Hood / Jacket / Trousers (had no recipe) | `LCL`/`T T` · `L L`/`TCT`/`LTL` · `TCT`/`L L`/`L L` | L leather, C charcoal, T buckskin |

* **Sewing moved from the crafting grid to the Clothing Table** (`"sew": "lining" | "mittens"`): hold the garment (selected
  hotbar slot) and press the sewing entry. Same rule as the old `LiningRecipe` (`recipes/ClothingTable.canSew`): one worn
  piece (head/chest/legs/feet, any mod or vanilla armour, camo, ghillie, coveralls, the fur garments), not already lined;
  mittens only on chest pieces. Validated server-side in `plan()`; the lining is consumed, the garment keeps name,
  enchantments and damage. The entry is greyed (not ready) while no eligible piece is in your hand.
* **Creative tabs**: the 8 items above (`recipes/ClothingTableItems`) are no longer added to the Field Equipment tab (nor the
  search tab) — same convention as ghillie/camo/carbon (`EquipmentCatalog.creative`). Still registered (`/give`, old worlds).
* **Hide Bedroll decision**: it is camp gear (a placed bed block), not clothing → **stays in the creative tab and in normal
  crafting** (`survival/hide_bedroll.json`, unchanged). Drying rack and all raw/tanned materials likewise unchanged.
* Pelts and hides now drop **even with Frontier Survival = Off** (`recipes/PeltDrops`, called from `SurvivalHarvest.drops`):
  bears → bear pelt, furbearers → fur pelt, bison → heavy hide, pronghorn → deer hide. Before, with survival off no fur
  clothing could ever be made. Meat/fat/organ conversions still follow the survival setting (design of `survival`).
* Lang (`_merge/.../z_recipes.json`, renamed at integ5 so it merges after survival.json): the two sewing entry names, and updated notes of fur lining, mittens, tanned fur,
  bear fur, tanned heavy hide ("Clothing Table: ...").

## 2. Recipe audit (all 328 items of frontierhunts + frontierstructures)
Tool: **`tools/recipe_audit.py <jar>`** (offline; see its docstring). It validates every recipe JSON (parses, known type,
1.21.1 `"result": {"id", "count"}`, equal-width patterns, keys used/defined, every item exists, every tag exists — mod jar
tags, NeoForge `c:` tags, vanilla tag names from `ItemTags`), loot tables (item names / table references), advancement
criteria items and recipe unlocks; then computes **survival reachability as a fix-point** from vanilla items, natural
world generation (data features + structure templates + code features), mob drops, structure chests, recipes of every type
(incl. the Clothing Table, which needs the Clothing Table item itself), block drops, and code sources (harvest, tanning
rack, game pole, drying rack, smokehouse, spoilage, butchering, ranger-board shop). Code sources carry evidence (class +
constant-pool tokens): if the code changes, the source is reported STALE instead of silently counted.
`tools/recipes/gen_unlocks.py <jar> .` writes missing recipe-book unlock advancements (idempotent).

**Before (dev.62 master build)**: 9 items with no survival source + 1 obtainable only by breaking it out of a settlement,
and 215 recipe-book recipes without an unlock. Root causes and fixes:

| item | problem | fix |
|---|---|---|
| attachment_tool | no recipe | crafting `" I "/"NIN"/" L "` (iron ingots, iron nuggets, leather); listed at the Attachment Workbench |
| carbon_hood / jacket / trousers | no recipe | Clothing Table (above) |
| trophy_plinth | only from settlements (needed by the "Homeward" mission) | crafting `SSS`/` L `/`LLL` (wooden slabs, planks) |
| alpine_pasture (Meadow Grass) | block has no loot table | shapeless 2 short grass + fern → 3 |
| forest_litter (Leaf Litter) | dropped nothing | shears or silk touch → itself |
| undergrowth | dropped 0-1 stick | shears or silk touch → itself, else as before |
| deadfall_log | dropped sticks | silk touch → itself, else as before |
| scattered_bones | dropped bones | silk touch → itself, else as before |
| fur_hat … fur_mittens | were crafting-table recipes | Clothing Table only |
| 215 recipes (both mods) | no recipe-book unlock | `advancement/recipes/unlock/<recipe>.json` (pick up a key material or have the recipe) |

All 248 recipe JSONs parse and reference existing items/tags (no errors found in the existing ones).
**Tree items** (alpine/aspen/birch/pine logs, leaves, boughs): all have a source (natural trees; leaves with shears/silk
touch). Nothing changed there (`trees2` owns tree loot/tags); the audit only records `landscape/AlpineTrees` /
`environment/HuntingForest` as evidence that they generate.

**Creative tabs**: the only change is removing the 8 Clothing-Table items. Reviewed every tab builder/listener
(HuntContent, AlpineRegistration, RideContent, BowStandContent, Camps, Ecology, Hound, Rig, Wildlife, Survival,
WorldTabPolish, FS windows): late additions go through `TabPlacement` (skips duplicates); no technical block has an item
(mist, whitewater, flow, foam, spray, outcrops, signs, hub blind, mounted stand, structure_space, workshop_floor are
block-only). Legacy items (tracer arrow, field tent, paraglider) are not in any tab.

### Intentional exceptions (creative/admin only)
| item | reason |
|---|---|
| 23 spawn eggs (`*_spawn_egg`) | spawn eggs are creative/admin only, like vanilla |
| tracer_arrow ("Tracer Arrow (old)") | legacy; tracers are now arrow tips fitted at the Bow Workshop |
| field_tent | legacy pre-Tent-Bench kit, hidden from creative and workshops on purpose; kept for old worlds / Woodcraft icon |
| paraglider | retired at the user's request (wingsuit + canopy replaced it); kept registered for old stacks |

## Files
New: `src/.../recipes/{ClothingTableRecipe,RecipesContent,ClothingTable,ClothingTableItems,PeltDrops}.java`,
`patch/data/frontierhunts/recipe/clothing_table/*.json` (14), `recipe/{attachment_tool,trophy_plinth,alpine_pasture}.json`,
`loot_table/blocks/{forest_litter,undergrowth,deadfall_log}.json`, `advancement/recipes/unlock/*.json` (208 frontierhunts in
patch/, 7 in `fs/resources/data/frontierstructures/advancement/recipes/unlock/`), lang fragment `z_recipes.json`,
`tools/recipe_audit.py`, `tools/recipes/gen_unlocks.py`.
Copied from dec62g8: `workshop/WorkbenchMenu.java` (first commit: generics only; then the `// [recipes]` block in `plan()`).
Shared-file hook lines (`// [recipes]`):
* `workshop/EquipmentCatalog.java`: `clear()` also clears `ClothingTable`; `recipes(Level, WorkshopKind)` appends
  `ClothingTable.recipes(...)` for `WorkshopKind.CLOTHING`.
* `workshop/WorkshopKind.java`: CLOTHING description text.
* `survival/SurvivalContent.java` `creative()`: skips `ClothingTableItems.only(id)`.
* `survival/SurvivalHarvest.java` `drops()`: with survival off calls `PeltDrops.apply(e, m)` instead of returning.
* `patch/data/frontierhunts/loot_table/blocks/scattered_bones.json` (ecology's file): silk-touch alternative.
Deleted: the 10 `recipe/survival/*.json` listed in section 1.

## For the coordinator
After merging all branches: `python3 tools/build.py <v> .` → `python3 tools/recipes/gen_unlocks.py <jar> .` (adds unlocks
for recipes other branches added) → rebuild → `python3 tools/recipe_audit.py <jar>` must print `RESULT: PASS`.
A new item without a survival source shows up as UNOBTAINABLE (add a recipe, or an EXCEPTIONS / CODE_SOURCES entry with
evidence). Options: `--md out.md` / `--json out.json` (full table), `-v`, `--vanilla <neoforge jar>`.

## IN-GAME TEST SCRIPT
Setup: survival world, op, Frontier survival = Balanced. Have a Clothing Table (`/give @s frontierhunts:clothing_workbench`
or craft: 3 copper ingots over planks `III`/`L L`/`LLL`).
1. **Creative tab**: switch to creative, open Frontier Hunts (field equipment) and search: no fur hat, buckskin coat/leggings,
   bear fur coat, hide robe, mukluks, fur lining, fur mittens. The Hide Bedroll, Drying Rack, pelts and tanned furs are
   still there. No crash opening any tab. Back to survival.
2. **Crafting table refuses**: `/give @s frontierhunts:tanned_hide 16`, `/give @s frontierhunts:tanned_fur 8`,
   `/give @s frontierhunts:bear_fur 4`, `/give @s frontierhunts:tanned_heavy_hide 6`, `/give @s minecraft:string 8`. In a
   crafting table lay out the old fur-hat shape (` F `/`FHF`): no output. The recipe book shows no fur garments.
3. **Clothing Table**: open it. After the ghillie/camo/carbon-suit entries: Fur Trapper Hat, Buckskin Coat, Buckskin
   Leggings, Bear Fur Coat, Heavy Hide Robe, Fur Mukluks, then Fur Lining (×2 recipes), Fur Mittens, then the two Sew
   entries, then Carbon Hood/Jacket/Trousers — each with its icon, materials "Have / Need". Search "fur" filters them.
   Craft each garment: materials leave the inventory, the garment appears (workbench sound); craft again with
   too few materials → button inactive. Spam-click → at most one craft per 8 ticks.
4. **Sewing**: craft a Fur Lining and Fur Mittens. Hold a camo coverall (or any chestplate) in the selected hotbar slot:
   "Sew Fur Lining into the garment in your hand" becomes ready → press: lining gone, the coverall tooltip says Fur-lined.
   Press again → not ready (already lined). Mittens: hold a helmet → not ready; hold a chest piece → "Fur mittens sewn on".
   In a crafting grid, garment + fur lining gives nothing now.
5. **Dedicated server + 2 clients**: both see the same Clothing Table list; crafting works for both; a client walking more
   than 8 blocks away gets the screen closed.
6. **Survival off**: Settings → Frontier survival = Off, kill a wolf and a black bear: Fur Pelt / Bear Pelt still drop
   (meat stays vanilla beef). Tan them on the tanning rack → tanned fur / 2 bear fur.
7. **New recipes**: crafting table: Attachment Tool (`" I "/"NIN"/" L "`), Trophy Plinth (slabs over planks
   `SSS`/` L `/`LLL`), Meadow Grass (2 short grass + fern → 3). The Attachment Workbench lists the Attachment Tool.
   Clothing Table: Carbon Hood/Jacket/Trousers.
8. **Silk touch / shears**: in a forest break Leaf Litter and Undergrowth with shears → the block drops; by hand → nothing
   / sticks as before. Deadfall Log with a silk-touch axe → the log item; normal axe → sticks. Scattered Bones (find a bone
   site, or place one from creative) with silk touch → itself; otherwise bones.
9. **Recipe book unlocks**: new world, pick up a deer hide → recipe toast / book shows ghillie suits; pick up iron → the
   workbenches; frontierstructures windows unlock with their planks. Log has no "unknown recipe" / advancement errors.
10. **Old world**: load a world that had unlocked `frontierhunts:survival/fur_hat` etc.: at most a one-time vanilla
    "Tried to load unrecognized recipe" log line; existing fur items, linings and mittens keep working.

## Full audit table (final jar, `tools/recipe_audit.py --md`)

| item | name | status | survival source (first found) | all sources |
|---|---|---|---|---|
| `frontierhunts:aged_venison` | Aged Venison Quarter | OK | Game Pole: hang a venison quarter in cool weather | Game Pole: hang a venison quarter in cool weather |
| `frontierhunts:alder_leaves` | Alder Leaves | OK | breaking natural alder_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:alder_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:alpine_alder_log` | Alder Log | OK | breaking natural alpine_alder_log (world generation (trees)) | breaking natural frontierhunts:alpine_alder_log (world generation (trees)) |
| `frontierhunts:alpine_cottonwood_log` | Cottonwood Log | OK | breaking natural alpine_cottonwood_log (world generation (trees)) | breaking natural frontierhunts:alpine_cottonwood_log (world generation (trees)) |
| `frontierhunts:alpine_maple_log` | Bigleaf Maple Log | OK | breaking natural alpine_maple_log (world generation (trees)) | breaking natural frontierhunts:alpine_maple_log (world generation (trees)) |
| `frontierhunts:alpine_pasture` | Meadow Grass | OK | crafting (frontierhunts:alpine_pasture) | crafting: frontierhunts:alpine_pasture |
| `frontierhunts:alpine_pine_log` | Lodgepole Pine Log | OK | breaking natural alpine_pine_log (world generation (trees)) | breaking natural frontierhunts:alpine_pine_log (world generation (trees)) |
| `frontierhunts:alpine_rock` | Weathered Alpine Rock | OK | breaking natural alpine_rock (world generation (alpine terrain columns)) | breaking natural frontierhunts:alpine_rock (world generation (alpine terrain columns)) |
| `frontierhunts:alpine_rowan_log` | Rowan Log | OK | breaking natural alpine_rowan_log (world generation (trees)) | breaking natural frontierhunts:alpine_rowan_log (world generation (trees)) |
| `frontierhunts:alpine_spruce_log` | Mountain Spruce Log | OK | breaking natural alpine_spruce_log (world generation (trees)) | breaking natural frontierhunts:alpine_spruce_log (world generation (trees)) |
| `frontierhunts:ammo_reloader` | Ammo Reloader | OK | crafting (frontierhunts:ammo_reloader) | crafting: frontierhunts:ammo_reloader; breaking frontierhunts:ammo_reloader found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:angled_foregrip` | Angled Rail Grip | OK | crafting (frontierhunts:angled_foregrip) | crafting: frontierhunts:angled_foregrip |
| `frontierhunts:arching_fern` | Sword Fern | OK | breaking natural arching_fern (worldgen feature frontierhunts:stream_bank_greenery) | breaking natural frontierhunts:arching_fern (worldgen feature frontierhunts:stream_bank_greenery) |
| `frontierhunts:aspen_leaves` | Aspen Leaves | OK | breaking natural aspen_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:aspen_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:aspen_log` | Aspen Log | OK | breaking natural aspen_log (world generation (trees)) | breaking natural frontierhunts:aspen_log (world generation (trees)) |
| `frontierhunts:attachment_tool` | Attachment Tool | OK | crafting (frontierhunts:attachment_tool) | crafting: frontierhunts:attachment_tool |
| `frontierhunts:attachment_workbench` | Optics & Attachment Workbench | OK | crafting (frontierhunts:attachment_workbench) | crafting: frontierhunts:attachment_workbench |
| `frontierhunts:atv` | ATV | OK | crafting (frontierhunts:atv) | crafting: frontierhunts:atv |
| `frontierhunts:atv_can_carrier` | ATV Can Carrier | OK | crafting (frontierhunts:atv_can_carrier) | crafting: frontierhunts:atv_can_carrier |
| `frontierhunts:atv_cargo_box` | ATV Cargo Box | OK | crafting (frontierhunts:atv_cargo_box) | crafting: frontierhunts:atv_cargo_box |
| `frontierhunts:autumn_camo_coveralls` | Autumn Leaf Camo Coveralls | OK | crafting (frontierhunts:autumn_camo_coveralls) | crafting: frontierhunts:autumn_camo_coveralls |
| `frontierhunts:autumn_maple_leaves` | Autumn Maple Leaves | OK | breaking natural autumn_maple_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:autumn_maple_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:backpacker_dome_tent` | Backpacker Dome Tent | OK | crafting (frontierhunts:backpacker_dome_tent) | crafting: frontierhunts:backpacker_dome_tent |
| `frontierhunts:backstrap` | Backstrap | OK | field dressing a whitetail / elk / moose with the Skinning Tool | field dressing a whitetail / elk / moose with the Skinning Tool |
| `frontierhunts:bait` | Bait | OK | crafting (frontierhunts:expedition_bait) | crafting: frontierhunts:expedition_bait; settlement chest: frontierhunts:chests/expedition_supplies; settlement chest: frontierhunts:chests/fishing_supplies; settlement chest: frontierhunts:chests/outpost_supplies … |
| `frontierhunts:bait_launcher` | Bait Launcher | OK | crafting (frontierhunts:expedition_bait_launcher) | crafting: frontierhunts:expedition_bait_launcher |
| `frontierhunts:bear_fur` | Tanned Bear Fur | OK | Tanning Rack: bear pelt -> 2 | Tanning Rack: bear pelt -> 2 |
| `frontierhunts:bear_fur_coat` | Bear Fur Coat | OK | Clothing Table (frontierhunts:clothing_table/bear_fur_coat) | Clothing Table: frontierhunts:clothing_table/bear_fur_coat |
| `frontierhunts:bear_meat` | Raw Bear Meat | OK | black bear / grizzly / polar bear | black bear / grizzly / polar bear |
| `frontierhunts:bear_pelt` | Bear Pelt | OK | black bear / grizzly / polar bear (always one pelt) | black bear / grizzly / polar bear (always one pelt) |
| `frontierhunts:bell_tent` | Cotton Bell Tent | OK | crafting (frontierhunts:bell_tent) | crafting: frontierhunts:bell_tent |
| `frontierhunts:big_buck_board` | Big-Buck Board | OK | crafting (frontierhunts:big_buck_board) | crafting: frontierhunts:big_buck_board |
| `frontierhunts:binoculars` | Binoculars | OK | crafting (frontierhunts:expedition_binoculars) | crafting: frontierhunts:expedition_binoculars |
| `frontierhunts:bipod` | Bipod | OK | crafting (frontierhunts:expedition_bipod) | crafting: frontierhunts:expedition_bipod; ranger board shop (expedition tokens) |
| `frontierhunts:birch_leaves` | Birch Leaves | OK | breaking natural birch_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:birch_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:birch_log` | Birch Log | OK | breaking natural birch_log (world generation (trees)) | breaking natural frontierhunts:birch_log (world generation (trees)) |
| `frontierhunts:bison_skull` | Bison Skull | OK | breaking natural bison_skull (world generation (bone sites)) | breaking natural frontierhunts:bison_skull (world generation (bone sites)) |
| `frontierhunts:bison_spawn_egg` | Bison Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:black_bear_spawn_egg` | Black Bear Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:blaze_camo_coveralls` | Blaze Orange Camo Coveralls | OK | crafting (frontierhunts:blaze_camo_coveralls) | crafting: frontierhunts:blaze_camo_coveralls |
| `frontierhunts:bleat_call` | Doe Bleat Call | OK | crafting (frontierhunts:bleat_call) | crafting: frontierhunts:bleat_call |
| `frontierhunts:blue_spruce_boughs` | Blue Spruce Boughs | OK | breaking natural blue_spruce_boughs with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:blue_spruce_boughs with shears or silk touch (world generation (trees)) |
| `frontierhunts:boar_spawn_egg` | Wild Boar Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:bone_point` | Bone Point | OK | crafting (frontierhunts:bone_point) | crafting: frontierhunts:bone_point |
| `frontierhunts:bow_stand` | Bow Display | OK | crafting (frontierhunts:bow_stand) | crafting: frontierhunts:bow_stand; breaking frontierhunts:bow_stand found in generated building frontierstructures:expedition/settlement_outfitter |
| `frontierhunts:bow_tuning_rack` | Bow Tuning Rack | OK | crafting (frontierhunts:bow_tuning_rack) | crafting: frontierhunts:bow_tuning_rack; breaking frontierhunts:bow_tuning_rack found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:bowfishing_arrow` | Bowfishing Arrow | OK | crafting (frontierhunts:expedition_bowfishing_arrow) | crafting: frontierhunts:expedition_bowfishing_arrow; ranger board shop (expedition tokens) |
| `frontierhunts:bowfishing_bow` | Bowfishing Bow | OK | crafting (frontierhunts:expedition_bowfishing_bow) | crafting: frontierhunts:expedition_bowfishing_bow |
| `frontierhunts:branch_stub` | Dead Branch | OK | breaking natural branch_stub with silk touch (world generation (alpine forest)) | breaking natural frontierhunts:branch_stub with silk touch (world generation (alpine forest)) |
| `frontierhunts:broadleaf_thicket` | Red Osier Dogwood | OK | breaking natural broadleaf_thicket (worldgen feature frontierhunts:stream_bank_greenery) | breaking natural frontierhunts:broadleaf_thicket (worldgen feature frontierhunts:stream_bank_greenery) |
| `frontierhunts:buckskin_coat` | Buckskin Coat | OK | Clothing Table (frontierhunts:clothing_table/buckskin_coat) | Clothing Table: frontierhunts:clothing_table/buckskin_coat |
| `frontierhunts:buckskin_leggings` | Buckskin Leggings | OK | Clothing Table (frontierhunts:clothing_table/buckskin_leggings) | Clothing Table: frontierhunts:clothing_table/buckskin_leggings |
| `frontierhunts:cabin_door` | Cabin Door | OK | crafting (frontierhunts:reserve_cabin_door) | crafting: frontierhunts:reserve_cabin_door |
| `frontierhunts:cabin_lantern` | Cabin Lantern | OK | crafting (frontierhunts:reserve_cabin_lantern) | crafting: frontierhunts:reserve_cabin_lantern; breaking frontierhunts:cabin_lantern found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:camera_base_station` | Camera Base Station | OK | crafting (frontierhunts:camera_base_station) | crafting: frontierhunts:camera_base_station |
| `frontierhunts:camp_cot` | Camp Cot | OK | crafting (frontierhunts:reserve_camp_cot) | crafting: frontierhunts:reserve_camp_cot; breaking frontierhunts:camp_cot found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:camp_post` | Camp Post | OK | crafting (frontierhunts:camp_post) | crafting: frontierhunts:camp_post |
| `frontierhunts:canvas_wall` | Canvas Wall | OK | crafting (frontierhunts:reserve_canvas_wall) | crafting: frontierhunts:reserve_canvas_wall |
| `frontierhunts:canvas_wall_tent` | Outfitter Canvas Wall Tent | OK | crafting (frontierhunts:canvas_wall_tent) | crafting: frontierhunts:canvas_wall_tent |
| `frontierhunts:carbon_hood` | Carbon Hood | OK | Clothing Table (frontierhunts:clothing_table/carbon_hood) | Clothing Table: frontierhunts:clothing_table/carbon_hood |
| `frontierhunts:carbon_jacket` | Carbon Jacket | OK | Clothing Table (frontierhunts:clothing_table/carbon_jacket) | Clothing Table: frontierhunts:clothing_table/carbon_jacket |
| `frontierhunts:carbon_trousers` | Carbon Trousers | OK | Clothing Table (frontierhunts:clothing_table/carbon_trousers) | Clothing Table: frontierhunts:clothing_table/carbon_trousers |
| `frontierhunts:cedar_log` | Cedar Log | OK | crafting (frontierhunts:reserve_cedar_log) | crafting: frontierhunts:reserve_cedar_log |
| `frontierhunts:cedar_planks` | Cedar Planks | OK | crafting (frontierhunts:reserve_cedar_planks) | crafting: frontierhunts:reserve_cedar_planks |
| `frontierhunts:cheetah_spawn_egg` | Cheetah Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:chum_bucket` | Chum Bucket | OK | crafting (frontierhunts:chum_bucket) | crafting: frontierhunts:chum_bucket |
| `frontierhunts:clothing_workbench` | Field Clothing Workbench | OK | crafting (frontierhunts:clothing_workbench) | crafting: frontierhunts:clothing_workbench; breaking frontierhunts:clothing_workbench found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:compound_bow` | Compound Bow | OK | crafting (frontierhunts:expedition_compound_bow) | crafting: frontierhunts:expedition_compound_bow |
| `frontierhunts:contract_board` | Ranger Contract Board | OK | crafting (frontierhunts:contract_board) | crafting: frontierhunts:contract_board |
| `frontierhunts:cooked_backstrap` | Seared Backstrap | OK | furnace (frontierhunts:cooked_backstrap) | furnace: frontierhunts:cooked_backstrap; campfire: frontierhunts:cooked_backstrap_from_campfire_cooking; smoker: frontierhunts:cooked_backstrap_from_smoking |
| `frontierhunts:cooked_bear_meat` | Roast Bear | OK | furnace (frontierhunts:survival/cooked_bear_meat) | furnace: frontierhunts:survival/cooked_bear_meat; campfire: frontierhunts:survival/cooked_bear_meat_from_campfire; smoker: frontierhunts:survival/cooked_bear_meat_from_smoking |
| `frontierhunts:cooked_game` | Roast Wild Game | OK | furnace (frontierhunts:survival/cooked_game) | furnace: frontierhunts:survival/cooked_game; campfire: frontierhunts:survival/cooked_game_from_campfire; smoker: frontierhunts:survival/cooked_game_from_smoking |
| `frontierhunts:cooked_organ_meat` | Seared Heart & Liver | OK | furnace (frontierhunts:survival/cooked_organ_meat) | furnace: frontierhunts:survival/cooked_organ_meat; campfire: frontierhunts:survival/cooked_organ_meat_from_campfire; smoker: frontierhunts:survival/cooked_organ_meat_from_smoking |
| `frontierhunts:cooked_venison` | Roasted Venison | OK | settlement chest loot (frontierhunts:chests/fishing_supplies) | campfire: frontierhunts:campfire_venison; furnace: frontierhunts:cooked_venison; smoker: frontierhunts:smoked_venison; settlement chest: frontierhunts:chests/fishing_supplies … |
| `frontierhunts:cooked_wild_fowl` | Roast Wild Fowl | OK | furnace (frontierhunts:survival/cooked_wild_fowl) | furnace: frontierhunts:survival/cooked_wild_fowl; campfire: frontierhunts:survival/cooked_wild_fowl_from_campfire; smoker: frontierhunts:survival/cooked_wild_fowl_from_smoking |
| `frontierhunts:cottongrass` | Cottongrass | OK | breaking natural cottongrass (world generation (alpine vegetation)) | breaking natural frontierhunts:cottongrass (world generation (alpine vegetation)) |
| `frontierhunts:cottonwood_leaves` | Cottonwood Leaves | OK | breaking natural cottonwood_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:cottonwood_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:cougar_spawn_egg` | Cougar Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:coyote_spawn_egg` | Coyote Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:crossbow` | Crossbow | OK | crafting (frontierhunts:expedition_crossbow) | crafting: frontierhunts:expedition_crossbow |
| `frontierhunts:cut_on_contact_broadhead` | Cut-on-Contact Broadhead | OK | crafting (frontierhunts:cut_on_contact_broadhead) | crafting: frontierhunts:cut_on_contact_broadhead |
| `frontierhunts:deadfall_log` | Deadfall Log | OK | breaking natural deadfall_log with silk touch (worldgen feature frontierhunts:deadfall) | breaking natural frontierhunts:deadfall_log with silk touch (worldgen feature frontierhunts:deadfall) |
| `frontierhunts:deer_call` | Deer Caller | OK | crafting (frontierhunts:expedition_deer_call) | crafting: frontierhunts:expedition_deer_call |
| `frontierhunts:deer_hide` | Whitetail Hide | OK | field dressing a whitetail (Skinning Tool) | field dressing a whitetail (Skinning Tool); pronghorn drop |
| `frontierhunts:digital_camo_coveralls` | Ridgeline Digital Camo | OK | crafting (frontierhunts:digital_camo_coveralls) | crafting: frontierhunts:digital_camo_coveralls |
| `frontierhunts:double_barrel` | Double Barrel | OK | crafting (frontierhunts:expedition_double_barrel) | crafting: frontierhunts:expedition_double_barrel |
| `frontierhunts:drying_rack` | Drying Rack | OK | crafting (frontierhunts:survival/drying_rack) | crafting: frontierhunts:survival/drying_rack |
| `frontierhunts:duck_spawn_egg` | Mallard Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:eight_power_scope` | Eight Power Scope | OK | crafting (frontierhunts:expedition_eight_power_scope) | crafting: frontierhunts:expedition_eight_power_scope; ranger board shop (expedition tokens) |
| `frontierhunts:elk_bull_spawn_egg` | Elk Bull Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:elk_cow_spawn_egg` | Elk Cow Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:elk_skull` | Elk Skull | OK | breaking natural elk_skull (world generation (bone sites)) | breaking natural frontierhunts:elk_skull (world generation (bone sites)) |
| `frontierhunts:elk_spawn_egg` | Elk Spawn Egg (Random) | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:expedition_board` | Expedition Board | OK | crafting (frontierhunts:expedition_board) | crafting: frontierhunts:expedition_board; breaking frontierhunts:expedition_board found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:expedition_guide` | Expedition Guide | OK | crafting (frontierhunts:expedition_expedition_guide) | crafting: frontierhunts:expedition_expedition_guide; settlement chest: frontierhunts:chests/expedition_supplies |
| `frontierhunts:extended_magazine` | Extended Magazine | OK | crafting (frontierhunts:expedition_extended_magazine) | crafting: frontierhunts:expedition_extended_magazine; ranger board shop (expedition tokens) |
| `frontierhunts:fallen_branch` | Fallen Branch | OK | crafting (frontierhunts:fallen_branch) | crafting: frontierhunts:fallen_branch |
| `frontierhunts:family_cabin_tent` | Family Cabin Tent | OK | crafting (frontierhunts:family_cabin_tent) | crafting: frontierhunts:family_cabin_tent |
| `frontierhunts:field_arrow` | Hunting Arrow | OK | crafting (frontierhunts:field_arrow) | crafting: frontierhunts:field_arrow; settlement chest: frontierhunts:chests/expedition_supplies; settlement chest: frontierhunts:chests/fishing_supplies; settlement chest: frontierhunts:chests/outpost_supplies … |
| `frontierhunts:field_battery_pack` | Field Battery Pack | OK | crafting (frontierhunts:field_battery_pack) | crafting: frontierhunts:field_battery_pack |
| `frontierhunts:field_blind` | Woodland Hub Blind | OK | crafting (frontierhunts:field_blind) | crafting: frontierhunts:field_blind |
| `frontierhunts:field_bow` | Field Recurve Bow | OK | crafting (frontierhunts:field_bow) | crafting: frontierhunts:field_bow |
| `frontierhunts:field_camera` | Field Camera | OK | crafting (frontierhunts:field_camera) | crafting: frontierhunts:field_camera |
| `frontierhunts:field_fishing_rod` | Field Fishing Rod | OK | crafting (frontierhunts:field_fishing_rod) | crafting: frontierhunts:field_fishing_rod |
| `frontierhunts:field_flashlight` | Field Flashlight | OK | crafting (frontierhunts:expedition_field_flashlight) | crafting: frontierhunts:expedition_field_flashlight |
| `frontierhunts:field_knife` | Field Knife | OK | crafting (frontierhunts:field_knife) | crafting: frontierhunts:field_knife |
| `frontierhunts:field_pistol` | Frontier 2011 Combat Pistol | OK | crafting (frontierhunts:expedition_field_pistol) | crafting: frontierhunts:expedition_field_pistol |
| `frontierhunts:field_point` | Field Point | OK | crafting (frontierhunts:field_point) | crafting: frontierhunts:field_point |
| `frontierhunts:field_spotlight` | Portable Field Floodlight | OK | crafting (frontierhunts:field_spotlight) | crafting: frontierhunts:field_spotlight; breaking frontierhunts:field_spotlight found in generated building frontierstructures:expedition/settlement_lodge |
| `frontierhunts:field_tent` | Field Tent | EXCEPTION | legacy pre-tent-bench kit, hidden from creative and the workshops on purpose (tents are made at the Tent Bench); kept registered for old worlds and as the Woodcraft skill icon |  |
| `frontierhunts:fieldstone` | Fieldstone | OK | crafting (frontierhunts:reserve_fieldstone) | crafting: frontierhunts:reserve_fieldstone |
| `frontierhunts:fieldstone_stairs` | Fieldstone Stairs | OK | crafting (frontierhunts:reserve_fieldstone_stairs) | crafting: frontierhunts:reserve_fieldstone_stairs |
| `frontierhunts:fir_needles` | Fir Boughs | OK | breaking natural fir_needles with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:fir_needles with shears or silk touch (world generation (trees)) |
| `frontierhunts:fireweed` | Fireweed | OK | breaking natural fireweed (world generation (alpine vegetation)) | breaking natural frontierhunts:fireweed (world generation (alpine vegetation)) |
| `frontierhunts:fish_finder` | Sonar Fish Finder | OK | crafting (frontierhunts:fish_finder) | crafting: frontierhunts:fish_finder |
| `frontierhunts:fishing_drag_kit` | Fishing Drag Kit | OK | crafting (frontierhunts:expedition_fishing_drag_kit) | crafting: frontierhunts:expedition_fishing_drag_kit; ranger board shop (expedition tokens) |
| `frontierhunts:fishing_station` | Fishing Station | OK | crafting (frontierhunts:fishing_station) | crafting: frontierhunts:fishing_station; breaking frontierhunts:fishing_station found in generated building frontierhunts:expedition/fishing_shallows |
| `frontierhunts:fixed_broadhead` | Fixed-Blade Broadhead | OK | crafting (frontierhunts:fixed_broadhead) | crafting: frontierhunts:fixed_broadhead |
| `frontierhunts:flare_gun` | Flare Gun | OK | crafting (frontierhunts:expedition_flare_gun) | crafting: frontierhunts:expedition_flare_gun |
| `frontierhunts:flare_round` | Flare Round | OK | crafting (frontierhunts:expedition_flare_round) | crafting: frontierhunts:expedition_flare_round |
| `frontierhunts:flint_point` | Knapped Flint Point | OK | crafting (frontierhunts:flint_point) | crafting: frontierhunts:flint_point |
| `frontierhunts:flowering_bramble` | Wild Rose Bramble | OK | breaking natural flowering_bramble (world generation (alpine vegetation)) | breaking natural frontierhunts:flowering_bramble (world generation (alpine vegetation)) |
| `frontierhunts:forest_duff` | Forest Duff | OK | breaking natural forest_duff (generated building frontierstructures:expedition/settlement_trapper) | breaking natural frontierhunts:forest_duff (generated building frontierstructures:expedition/settlement_trapper) |
| `frontierhunts:forest_litter` | Leaf Litter | OK | breaking natural forest_litter with shears or silk touch (worldgen feature frontierhunts:forest_litter) | breaking natural frontierhunts:forest_litter with shears or silk touch (worldgen feature frontierhunts:forest_litter) |
| `frontierhunts:forest_loam` | Forest Loam | OK | crafting (frontierhunts:reserve_forest_loam) | crafting: frontierhunts:reserve_forest_loam |
| `frontierhunts:forest_sticks` | Fallen Sticks | OK | breaking natural forest_sticks with silk touch (world generation (alpine vegetation)) | breaking natural frontierhunts:forest_sticks with silk touch (world generation (alpine vegetation)) |
| `frontierhunts:four_power_optic` | 4-12× Field Optic | OK | crafting (frontierhunts:four_power_optic) | crafting: frontierhunts:four_power_optic |
| `frontierhunts:fur_hat` | Fur Trapper Hat | OK | Clothing Table (frontierhunts:clothing_table/fur_hat) | Clothing Table: frontierhunts:clothing_table/fur_hat |
| `frontierhunts:fur_lining` | Fur Lining | OK | Clothing Table (frontierhunts:clothing_table/fur_lining) | Clothing Table: frontierhunts:clothing_table/fur_lining; Clothing Table: frontierhunts:clothing_table/fur_lining_from_bear_fur |
| `frontierhunts:fur_mittens` | Fur Mittens | OK | Clothing Table (frontierhunts:clothing_table/fur_mittens) | Clothing Table: frontierhunts:clothing_table/fur_mittens |
| `frontierhunts:fur_mukluks` | Fur Mukluks | OK | Clothing Table (frontierhunts:clothing_table/fur_mukluks) | Clothing Table: frontierhunts:clothing_table/fur_mukluks |
| `frontierhunts:fur_pelt` | Fur Pelt | OK | wolf / coyote / cougar / panther / lion / cheetah (always one pelt) | wolf / coyote / cougar / panther / lion / cheetah (always one pelt) |
| `frontierhunts:game_fat` | Fat Trimmings | OK | fat trimmings from harvests and wildlife drops (by season) | fat trimmings from harvests and wildlife drops (by season) |
| `frontierhunts:game_meat` | Raw Wild Game | OK | bison / boar / pronghorn / predators | bison / boar / pronghorn / predators |
| `frontierhunts:game_pole` | Game Pole | OK | crafting (frontierhunts:game_pole) | crafting: frontierhunts:game_pole |
| `frontierhunts:ghillie_grassland_hood` | Grassland Ghillie Hood | OK | crafting (frontierhunts:ghillie_grassland_hood) | crafting: frontierhunts:ghillie_grassland_hood |
| `frontierhunts:ghillie_grassland_jacket` | Grassland Ghillie Jacket | OK | crafting (frontierhunts:ghillie_grassland_jacket) | crafting: frontierhunts:ghillie_grassland_jacket |
| `frontierhunts:ghillie_grassland_trousers` | Grassland Ghillie Trousers | OK | crafting (frontierhunts:ghillie_grassland_trousers) | crafting: frontierhunts:ghillie_grassland_trousers |
| `frontierhunts:ghillie_hood` | Woodland Ghillie Hood | OK | crafting (frontierhunts:ghillie_hood) | crafting: frontierhunts:ghillie_hood |
| `frontierhunts:ghillie_jacket` | Woodland Ghillie Jacket | OK | crafting (frontierhunts:ghillie_jacket) | crafting: frontierhunts:ghillie_jacket |
| `frontierhunts:ghillie_snow_hood` | Snowfield Ghillie Hood | OK | crafting (frontierhunts:ghillie_snow_hood) | crafting: frontierhunts:ghillie_snow_hood |
| `frontierhunts:ghillie_snow_jacket` | Snowfield Ghillie Jacket | OK | crafting (frontierhunts:ghillie_snow_jacket) | crafting: frontierhunts:ghillie_snow_jacket |
| `frontierhunts:ghillie_snow_trousers` | Snowfield Ghillie Trousers | OK | crafting (frontierhunts:ghillie_snow_trousers) | crafting: frontierhunts:ghillie_snow_trousers |
| `frontierhunts:ghillie_trousers` | Woodland Ghillie Trousers | OK | crafting (frontierhunts:ghillie_trousers) | crafting: frontierhunts:ghillie_trousers |
| `frontierhunts:ghillie_wetland_hood` | Wetland Ghillie Hood | OK | crafting (frontierhunts:ghillie_wetland_hood) | crafting: frontierhunts:ghillie_wetland_hood |
| `frontierhunts:ghillie_wetland_jacket` | Wetland Ghillie Jacket | OK | crafting (frontierhunts:ghillie_wetland_jacket) | crafting: frontierhunts:ghillie_wetland_jacket |
| `frontierhunts:ghillie_wetland_trousers` | Wetland Ghillie Trousers | OK | crafting (frontierhunts:ghillie_wetland_trousers) | crafting: frontierhunts:ghillie_wetland_trousers |
| `frontierhunts:glow_lure` | Glow Lure | OK | crafting (frontierhunts:glow_lure) | crafting: frontierhunts:glow_lure |
| `frontierhunts:golden_aspen_leaves` | Golden Aspen Leaves | OK | breaking natural golden_aspen_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:golden_aspen_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:grizzly_spawn_egg` | Grizzly Bear Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:grouse_spawn_egg` | Ruffed Grouse Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:grunt_tube` | Grunt Tube | OK | crafting (frontierhunts:grunt_tube) | crafting: frontierhunts:grunt_tube |
| `frontierhunts:gun_rack` | Walnut Gun Rack | OK | crafting (frontierhunts:gun_rack) | crafting: frontierhunts:gun_rack; breaking frontierhunts:gun_rack found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:heather` | Heather | OK | breaking natural heather (world generation (alpine vegetation)) | breaking natural frontierhunts:heather (world generation (alpine vegetation)) |
| `frontierhunts:heavy_hide` | Heavy Hide | OK | field dressing an elk / moose (Skinning Tool) | field dressing an elk / moose (Skinning Tool); bison drop |
| `frontierhunts:hide_bedroll` | Hide Bedroll | OK | crafting (frontierhunts:survival/hide_bedroll) | crafting: frontierhunts:survival/hide_bedroll |
| `frontierhunts:hide_robe` | Heavy Hide Robe | OK | Clothing Table (frontierhunts:clothing_table/hide_robe) | Clothing Table: frontierhunts:clothing_table/hide_robe |
| `frontierhunts:holographic_sight` | Watchpost Holographic Sight | OK | crafting (frontierhunts:holographic_sight) | crafting: frontierhunts:holographic_sight |
| `frontierhunts:horse_whistle` | Horse Whistle | OK | crafting (frontierhunts:horse_whistle) | crafting: frontierhunts:horse_whistle |
| `frontierhunts:hound_lead` | Hound Lead | OK | crafting (frontierhunts:hound_lead) | crafting: frontierhunts:hound_lead |
| `frontierhunts:huckleberry_shrub` | Huckleberry Shrub | OK | breaking natural huckleberry_shrub (world generation (alpine vegetation)) | breaking natural frontierhunts:huckleberry_shrub (world generation (alpine vegetation)) |
| `frontierhunts:hunter_journal` | Hunter's Journal | OK | crafting (frontierhunts:hunter_journal) | crafting: frontierhunts:hunter_journal |
| `frontierhunts:hunter_pack` | Hunter's Field Pack | OK | crafting (frontierhunts:hunter_pack) | crafting: frontierhunts:hunter_pack |
| `frontierhunts:hunters_canvas_tent` | Hunter's Canvas Tent | OK | crafting (frontierhunts:hunters_canvas_tent) | crafting: frontierhunts:hunters_canvas_tent |
| `frontierhunts:hunters_quiver` | Hunter's Quiver | OK | crafting (frontierhunts:hunters_quiver) | crafting: frontierhunts:hunters_quiver |
| `frontierhunts:hunting_spear` | Hunting Spear | OK | crafting (frontierhunts:expedition_hunting_spear) | crafting: frontierhunts:expedition_hunting_spear |
| `frontierhunts:jerky` | Jerky | OK | Drying Rack: hang strips of raw game | Drying Rack: hang strips of raw game |
| `frontierhunts:jerry_can` | Jerry Can | OK | crafting (frontierhunts:jerry_can) | crafting: frontierhunts:jerry_can; crafting: frontierhunts:jerry_can_from_lava |
| `frontierhunts:judo_point` | Judo Point | OK | crafting (frontierhunts:judo_point) | crafting: frontierhunts:judo_point |
| `frontierhunts:juniper_shrub` | Common Juniper | OK | breaking natural juniper_shrub (world generation (alpine vegetation)) | breaking natural frontierhunts:juniper_shrub (world generation (alpine vegetation)) |
| `frontierhunts:landing_net` | Landing Net | OK | crafting (frontierhunts:landing_net) | crafting: frontierhunts:landing_net |
| `frontierhunts:larch_needles` | Larch Needles | OK | breaking natural larch_needles with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:larch_needles with shears or silk touch (world generation (trees)) |
| `frontierhunts:lever_rifle` | Lever Rifle | OK | crafting (frontierhunts:expedition_lever_rifle) | crafting: frontierhunts:expedition_lever_rifle |
| `frontierhunts:lion_spawn_egg` | Lion Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:lit_lodge_table` | Lantern Lodge Table | OK | crafting (frontierhunts:lit_lodge_table) | crafting: frontierhunts:lit_lodge_table; breaking frontierhunts:lit_lodge_table found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:lodge_chair` | Lodge Chair | OK | crafting (frontierhunts:reserve_lodge_chair) | crafting: frontierhunts:reserve_lodge_chair; breaking frontierhunts:lodge_chair found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:lodge_stores` | Lodge Stores | OK | crafting (frontierhunts:lodge_stores) | crafting: frontierhunts:lodge_stores; breaking frontierhunts:lodge_stores found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:lodge_stove` | Lodge Stove | OK | crafting (frontierhunts:reserve_lodge_stove) | crafting: frontierhunts:reserve_lodge_stove; breaking frontierhunts:lodge_stove found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:lodge_table` | Lodge Table | OK | crafting (frontierhunts:reserve_lodge_table) | crafting: frontierhunts:reserve_lodge_table; breaking frontierhunts:lodge_table found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:lookout_brace` | Timber Diagonal Brace | OK | crafting (frontierhunts:lookout_brace) | crafting: frontierhunts:lookout_brace; breaking frontierhunts:lookout_brace found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:lookout_cross_brace` | Timber Crossing Brace | OK | crafting (frontierhunts:lookout_cross_brace) | crafting: frontierhunts:lookout_cross_brace; breaking frontierhunts:lookout_cross_brace found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:lookout_deck_rail` | Timber Deck Railing | OK | crafting (frontierhunts:lookout_deck_rail) | crafting: frontierhunts:lookout_deck_rail; breaking frontierhunts:lookout_deck_rail found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:lookout_stair_rail` | Timber Stair Railing | OK | crafting (frontierhunts:lookout_stair_rail) | crafting: frontierhunts:lookout_stair_rail; breaking frontierhunts:lookout_stair_rail found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:maple_leaves` | Maple Leaves | OK | breaking natural maple_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:maple_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:marsh_camo_coveralls` | Marsh Camo Coveralls | OK | crafting (frontierhunts:marsh_camo_coveralls) | crafting: frontierhunts:marsh_camo_coveralls |
| `frontierhunts:mechanical_broadhead` | Mechanical Broadhead | OK | crafting (frontierhunts:mechanical_broadhead) | crafting: frontierhunts:mechanical_broadhead |
| `frontierhunts:medkit` | Field First-Aid Kit | OK | crafting (frontierhunts:expedition_medkit) | crafting: frontierhunts:expedition_medkit; settlement chest: frontierhunts:chests/expedition_supplies; settlement chest: frontierhunts:chests/fishing_supplies; settlement chest: frontierhunts:chests/outpost_supplies … |
| `frontierhunts:micro_red_dot` | Trailpoint Micro Red Dot | OK | crafting (frontierhunts:micro_red_dot) | crafting: frontierhunts:micro_red_dot |
| `frontierhunts:moose_bull_spawn_egg` | Moose Bull Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:moose_cow_spawn_egg` | Moose Cow Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:moose_skull` | Moose Skull | OK | breaking natural moose_skull (world generation (bone sites)) | breaking natural frontierhunts:moose_skull (world generation (bone sites)) |
| `frontierhunts:moose_spawn_egg` | Moose Spawn Egg (Random) | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:moss_floor` | Moss Floor | OK | crafting (frontierhunts:reserve_moss_floor) | crafting: frontierhunts:reserve_moss_floor |
| `frontierhunts:mossy_river_boulder` | Mossy River Boulder | OK | breaking natural mossy_river_boulder (world generation (alpine rivers)) | breaking natural frontierhunts:mossy_river_boulder (world generation (alpine rivers)) |
| `frontierhunts:mossy_river_pebbles` | Mossy River Pebbles | OK | breaking natural mossy_river_pebbles (worldgen feature frontierhunts:stream_bank_stones) | breaking natural frontierhunts:mossy_river_pebbles (worldgen feature frontierhunts:stream_bank_stones) |
| `frontierhunts:mossy_river_stone` | Mossy River Stone | OK | breaking natural mossy_river_stone (worldgen feature frontierhunts:stream_bank_stones) | breaking natural frontierhunts:mossy_river_stone (worldgen feature frontierhunts:stream_bank_stones) |
| `frontierhunts:mossy_stone` | Mossy Stone | OK | breaking natural mossy_stone (worldgen feature frontierhunts:deadfall) | breaking natural frontierhunts:mossy_stone (worldgen feature frontierhunts:deadfall) |
| `frontierhunts:muzzle_brake` | Ported Muzzle Brake | OK | crafting (frontierhunts:muzzle_brake) | crafting: frontierhunts:muzzle_brake |
| `frontierhunts:night_vision_binoculars` | Nightwatch Dual-Tube Optics | OK | crafting (frontierhunts:night_vision_binoculars) | crafting: frontierhunts:night_vision_binoculars |
| `frontierhunts:obsidian_point` | Obsidian Point | OK | crafting (frontierhunts:obsidian_point) | crafting: frontierhunts:obsidian_point |
| `frontierhunts:organ_meat` | Heart & Liver | OK | field dressing a whitetail / elk / moose with the Skinning Tool / wildlife drops | field dressing a whitetail / elk / moose with the Skinning Tool / wildlife drops |
| `frontierhunts:panther_spawn_egg` | Black Panther Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:paraglider` | Paraglider | EXCEPTION | retired at the user's request (replaced by the Wingsuit + canopy); item kept registered so old stacks still work, recipe retired, not in creative |  |
| `frontierhunts:pemmican` | Pemmican | OK | crafting (frontierhunts:survival/pemmican) | crafting: frontierhunts:survival/pemmican |
| `frontierhunts:pine_fence` | Pine Fence | OK | crafting (frontierhunts:reserve_pine_fence) | crafting: frontierhunts:reserve_pine_fence |
| `frontierhunts:pine_log` | Pine Log | OK | crafting (frontierhunts:reserve_pine_log) | crafting: frontierhunts:reserve_pine_log; breaking natural frontierhunts:pine_log (world generation (hunting forest trees)) |
| `frontierhunts:pine_needles` | Pine Needles | OK | breaking natural pine_needles with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:pine_needles with shears or silk touch (world generation (trees)) |
| `frontierhunts:pine_planks` | Pine Planks | OK | crafting (frontierhunts:reserve_pine_planks) | crafting: frontierhunts:reserve_pine_planks |
| `frontierhunts:pine_slab` | Pine Slab | OK | crafting (frontierhunts:reserve_pine_slab) | crafting: frontierhunts:reserve_pine_slab |
| `frontierhunts:pine_stairs` | Pine Stairs | OK | crafting (frontierhunts:reserve_pine_stairs) | crafting: frontierhunts:reserve_pine_stairs |
| `frontierhunts:pistol_magazine` | 2011 Extended Magazine | OK | crafting (frontierhunts:expedition_pistol_magazine) | crafting: frontierhunts:expedition_pistol_magazine |
| `frontierhunts:pistol_round` | Pistol Round | OK | crafting (frontierhunts:expedition_pistol_round) | crafting: frontierhunts:expedition_pistol_round; ranger board shop (expedition tokens) |
| `frontierhunts:polar_bear_spawn_egg` | Polar Bear Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:prairie_camo_coveralls` | Prairie Camo Coveralls | OK | crafting (frontierhunts:prairie_camo_coveralls) | crafting: frontierhunts:prairie_camo_coveralls |
| `frontierhunts:predator_call` | Predator Locator Call | OK | crafting (frontierhunts:expedition_predator_call) | crafting: frontierhunts:expedition_predator_call |
| `frontierhunts:primitive_arrow` | Primitive Arrow | OK | crafting (frontierhunts:primitive_arrow) | crafting: frontierhunts:primitive_arrow |
| `frontierhunts:pronghorn_spawn_egg` | Pronghorn Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:pump_shotgun` | Pump Shotgun | OK | crafting (frontierhunts:expedition_pump_shotgun) | crafting: frontierhunts:expedition_pump_shotgun |
| `frontierhunts:pup_tent` | Classic Pup Tent | OK | crafting (frontierhunts:pup_tent) | crafting: frontierhunts:pup_tent |
| `frontierhunts:rangefinder` | Rangefinder | OK | crafting (frontierhunts:expedition_rangefinder) | crafting: frontierhunts:expedition_rangefinder |
| `frontierhunts:ranger_window` | Ranger Window | OK | crafting (frontierhunts:reserve_ranger_window) | crafting: frontierhunts:reserve_ranger_window; breaking frontierhunts:ranger_window found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:rattling_antlers` | Rattling Antlers | OK | crafting (frontierhunts:rattling_antlers) | crafting: frontierhunts:rattling_antlers |
| `frontierhunts:recurve_bow` | Recurve Bow | OK | crafting (frontierhunts:expedition_recurve_bow) | crafting: frontierhunts:expedition_recurve_bow |
| `frontierhunts:reeds` | Reeds | OK | breaking natural reeds (world generation (alpine lakes)) | breaking natural frontierhunts:reeds (world generation (alpine lakes)) |
| `frontierhunts:reflex_sight` | Reflex Sight | OK | crafting (frontierhunts:expedition_reflex_sight) | crafting: frontierhunts:expedition_reflex_sight |
| `frontierhunts:reserve_308` | Reserve .308 Cartridge | OK | crafting (frontierhunts:reserve_308) | crafting: frontierhunts:reserve_308 |
| `frontierhunts:reserve_granite` | Reserve Granite | OK | crafting (frontierhunts:reserve_reserve_granite) | crafting: frontierhunts:reserve_reserve_granite |
| `frontierhunts:revolver` | Revolver | OK | crafting (frontierhunts:expedition_revolver) | crafting: frontierhunts:expedition_revolver |
| `frontierhunts:ridgeline_rifle` | Ridgeline Bolt Rifle | OK | crafting (frontierhunts:ridgeline_rifle) | crafting: frontierhunts:ridgeline_rifle |
| `frontierhunts:ridgeline_scope` | Ridgeline Hunting Scope (3-9×) | OK | crafting (frontierhunts:ridgeline_scope) | crafting: frontierhunts:ridgeline_scope |
| `frontierhunts:rifle_round` | Rifle Round | OK | crafting (frontierhunts:expedition_rifle_round) | crafting: frontierhunts:expedition_rifle_round; settlement chest: frontierhunts:chests/expedition_supplies; settlement chest: frontierhunts:chests/outpost_supplies; ranger board shop (expedition tokens) |
| `frontierhunts:river_boulder` | River Boulder | OK | breaking natural river_boulder (world generation (alpine rivers)) | breaking natural frontierhunts:river_boulder (world generation (alpine rivers)) |
| `frontierhunts:river_brush` | Willow Scrub | OK | breaking natural river_brush (worldgen feature frontierhunts:stream_bank_greenery) | breaking natural frontierhunts:river_brush (worldgen feature frontierhunts:stream_bank_greenery) |
| `frontierhunts:river_pebbles` | River Pebbles | OK | breaking natural river_pebbles (worldgen feature frontierhunts:stream_bank_stones) | breaking natural frontierhunts:river_pebbles (worldgen feature frontierhunts:stream_bank_stones) |
| `frontierhunts:river_stone` | River Stone | OK | breaking natural river_stone (worldgen feature frontierhunts:stream_bank_stones) | breaking natural frontierhunts:river_stone (worldgen feature frontierhunts:stream_bank_stones) |
| `frontierhunts:roof_gable_high` | Roof Gable High | OK | crafting (frontierhunts:reserve_roof_gable_high) | crafting: frontierhunts:reserve_roof_gable_high |
| `frontierhunts:roof_gable_low` | Roof Gable Low | OK | crafting (frontierhunts:reserve_roof_gable_low) | crafting: frontierhunts:reserve_roof_gable_low |
| `frontierhunts:roof_ridge_high` | Roof Ridge High | OK | crafting (frontierhunts:reserve_roof_ridge_high) | crafting: frontierhunts:reserve_roof_ridge_high |
| `frontierhunts:roof_ridge_low` | Roof Ridge Low | OK | crafting (frontierhunts:reserve_roof_ridge_low) | crafting: frontierhunts:reserve_roof_ridge_low |
| `frontierhunts:roof_shingles` | Roof Shingles | OK | crafting (frontierhunts:reserve_roof_shingles) | crafting: frontierhunts:reserve_roof_shingles |
| `frontierhunts:roof_slab` | Roof Slab | OK | crafting (frontierhunts:reserve_roof_slab) | crafting: frontierhunts:reserve_roof_slab |
| `frontierhunts:roof_slope_high` | Roof Slope High | OK | crafting (frontierhunts:reserve_roof_slope_high) | crafting: frontierhunts:reserve_roof_slope_high |
| `frontierhunts:roof_slope_low` | Roof Slope Low | OK | crafting (frontierhunts:reserve_roof_slope_low) | crafting: frontierhunts:reserve_roof_slope_low |
| `frontierhunts:roof_stairs` | Roof Stairs | OK | crafting (frontierhunts:reserve_roof_stairs) | crafting: frontierhunts:reserve_roof_stairs |
| `frontierhunts:rope_ladder` | Rope Ladder | OK | crafting (frontierhunts:reserve_rope_ladder) | crafting: frontierhunts:reserve_rope_ladder |
| `frontierhunts:rowan_leaves` | Rowan Leaves | OK | breaking natural rowan_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:rowan_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:sagebrush` | Sagebrush | OK | breaking natural sagebrush (world generation (alpine vegetation)) | breaking natural frontierhunts:sagebrush (world generation (alpine vegetation)) |
| `frontierhunts:salt` | Salt | OK | furnace (frontierhunts:survival/salt) | furnace: frontierhunts:survival/salt; campfire: frontierhunts:survival/salt_from_campfire |
| `frontierhunts:sapling_pole` | Pole Tree | OK | breaking natural sapling_pole with silk touch (world generation (alpine forest)) | breaking natural frontierhunts:sapling_pole with silk touch (world generation (alpine forest)) |
| `frontierhunts:scattered_bones` | Scattered Bones | OK | breaking natural scattered_bones with silk touch (world generation (bone sites)) | breaking natural frontierhunts:scattered_bones with silk touch (world generation (bone sites)) |
| `frontierhunts:scent_cover` | Scent Cover | OK | crafting (frontierhunts:expedition_scent_cover) | crafting: frontierhunts:expedition_scent_cover; settlement chest: frontierhunts:chests/expedition_supplies; settlement chest: frontierhunts:chests/refuge_supplies; ranger board shop (expedition tokens) |
| `frontierhunts:scent_suit` | Carbon Scent Suit | OK | crafting (frontierhunts:scent_suit) | crafting: frontierhunts:scent_suit |
| `frontierhunts:semi_auto_rifle` | Assault Rifle | OK | crafting (frontierhunts:expedition_semi_auto_rifle) | crafting: frontierhunts:expedition_semi_auto_rifle |
| `frontierhunts:semi_auto_shotgun` | Semi Auto Shotgun | OK | crafting (frontierhunts:expedition_semi_auto_shotgun) | crafting: frontierhunts:expedition_semi_auto_shotgun |
| `frontierhunts:shed_antler` | Shed Antler | OK | breaking natural shed_antler (world generation (bone sites)) | breaking natural frontierhunts:shed_antler (world generation (bone sites)) |
| `frontierhunts:shooting_target` | Shooting Target | OK | crafting (frontierhunts:shooting_target) | crafting: frontierhunts:shooting_target |
| `frontierhunts:shotgun_shell` | Shotgun Shell | OK | crafting (frontierhunts:expedition_shotgun_shell) | crafting: frontierhunts:expedition_shotgun_shell; settlement chest: frontierhunts:chests/expedition_supplies; settlement chest: frontierhunts:chests/outpost_supplies; ranger board shop (expedition tokens) |
| `frontierhunts:six_power_scope` | Six Power Scope | OK | crafting (frontierhunts:expedition_six_power_scope) | crafting: frontierhunts:expedition_six_power_scope; ranger board shop (expedition tokens) |
| `frontierhunts:skinning_tool` | Contour Skinning Knife | OK | crafting (frontierhunts:skinning_tool) | crafting: frontierhunts:skinning_tool |
| `frontierhunts:smokehouse` | Field Smokehouse | OK | crafting (frontierhunts:smokehouse) | crafting: frontierhunts:smokehouse; breaking frontierhunts:smokehouse found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:sniper_magazine` | Ridgeline 5-Round Magazine | OK | crafting (frontierhunts:expedition_sniper_magazine) | crafting: frontierhunts:expedition_sniper_magazine |
| `frontierhunts:snow_camo_coveralls` | Snow Camo Coveralls | OK | crafting (frontierhunts:snow_camo_coveralls) | crafting: frontierhunts:snow_camo_coveralls |
| `frontierhunts:solo_ridge_tent` | Solo Ridge Tent | OK | crafting (frontierhunts:solo_ridge_tent) | crafting: frontierhunts:solo_ridge_tent |
| `frontierhunts:spoiled_meat` | Spoiled Meat | OK | any raw or cooked meat left past its shelf life | any raw or cooked meat left past its shelf life |
| `frontierhunts:spreading_fern` | Bracken Fern | OK | breaking natural spreading_fern (worldgen feature frontierhunts:stream_bank_greenery) | breaking natural frontierhunts:spreading_fern (worldgen feature frontierhunts:stream_bank_greenery) |
| `frontierhunts:spruce_boughs` | Spruce Boughs | OK | breaking natural spruce_boughs with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:spruce_boughs with shears or silk touch (world generation (trees)) |
| `frontierhunts:spruce_seedling` | Spruce Seedling | OK | breaking natural spruce_seedling (world generation (alpine vegetation)) | breaking natural frontierhunts:spruce_seedling (world generation (alpine vegetation)) |
| `frontierhunts:stacked_firewood` | Stacked Firewood | OK | crafting (frontierhunts:reserve_stacked_firewood) | crafting: frontierhunts:reserve_stacked_firewood; breaking frontierhunts:stacked_firewood found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:steady_stock` | Steady Stock | OK | crafting (frontierhunts:expedition_steady_stock) | crafting: frontierhunts:expedition_steady_stock; ranger board shop (expedition tokens) |
| `frontierhunts:stove_flue` | Stove Flue | OK | crafting (frontierhunts:reserve_stove_flue) | crafting: frontierhunts:reserve_stove_flue; breaking frontierhunts:stove_flue found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:stove_roof_flashing` | Stove Roof Flashing | OK | crafting (frontierhunts:stove_roof_flashing) | crafting: frontierhunts:stove_roof_flashing; breaking frontierhunts:stove_roof_flashing found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:suppressor` | Suppressor | OK | crafting (frontierhunts:expedition_suppressor) | crafting: frontierhunts:expedition_suppressor; ranger board shop (expedition tokens) |
| `frontierhunts:tallow` | Rendered Tallow | OK | furnace (frontierhunts:survival/tallow) | furnace: frontierhunts:survival/tallow; campfire: frontierhunts:survival/tallow_from_campfire; smoker: frontierhunts:survival/tallow_from_smoking |
| `frontierhunts:tanned_fur` | Tanned Fur | OK | Tanning Rack: fur pelt | Tanning Rack: fur pelt |
| `frontierhunts:tanned_heavy_hide` | Tanned Heavy Hide | OK | Tanning Rack: heavy hide + bone | Tanning Rack: heavy hide + bone |
| `frontierhunts:tanned_hide` | Tanned Buckskin | OK | Tanning Rack: whitetail hide + bone | Tanning Rack: whitetail hide + bone |
| `frontierhunts:tanning_rack` | Tanning Rack | OK | crafting (frontierhunts:tanning_rack) | crafting: frontierhunts:tanning_rack; breaking frontierhunts:tanning_rack found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:tent_bench` | Tent Bench | OK | crafting (frontierhunts:tent_bench) | crafting: frontierhunts:tent_bench; breaking frontierhunts:tent_bench found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:tent_end_ridge` | Closed Canvas Ridge | OK | crafting (frontierhunts:tent_end_ridge) | crafting: frontierhunts:tent_end_ridge |
| `frontierhunts:tent_gable` | Tent Gable | OK | crafting (frontierhunts:reserve_tent_gable) | crafting: frontierhunts:reserve_tent_gable |
| `frontierhunts:tent_ridge` | Tent Ridge | OK | crafting (frontierhunts:reserve_tent_ridge) | crafting: frontierhunts:reserve_tent_ridge |
| `frontierhunts:tent_slope` | Tent Slope | OK | crafting (frontierhunts:reserve_tent_slope) | crafting: frontierhunts:reserve_tent_slope |
| `frontierhunts:thermal_binoculars` | Thermal Binoculars | OK | crafting (frontierhunts:expedition_thermal_binoculars) | crafting: frontierhunts:expedition_thermal_binoculars |
| `frontierhunts:thermal_scope` | Thermal Scope | OK | crafting (frontierhunts:expedition_thermal_scope) | crafting: frontierhunts:expedition_thermal_scope; ranger board shop (expedition tokens) |
| `frontierhunts:timber_brace` | Diagonal Timber Brace | OK | crafting (frontierhunts:timber_brace) | crafting: frontierhunts:timber_brace |
| `frontierhunts:timber_camo_coveralls` | Timber Camo Coveralls | OK | crafting (frontierhunts:timber_camo_coveralls) | crafting: frontierhunts:timber_camo_coveralls |
| `frontierhunts:timber_cross_brace` | Crossed Timber Joint | OK | crafting (frontierhunts:timber_cross_brace) | crafting: frontierhunts:timber_cross_brace |
| `frontierhunts:timber_stair_guard` | Timber Stair Guard | OK | crafting (frontierhunts:timber_stair_guard) | crafting: frontierhunts:timber_stair_guard |
| `frontierhunts:tower_blind` | Elevated Tower Blind | OK | crafting (frontierhunts:tower_blind) | crafting: frontierhunts:tower_blind |
| `frontierhunts:tracer_arrow` | Tracer Arrow (old) | EXCEPTION | legacy item "Tracer Arrow (old)": kept registered so old stacks load; tracers are now arrow tips fitted at the Bow Workshop (tracer broadhead / field point / glacier tracer) |  |
| `frontierhunts:tracer_broadhead` | Tracer Broadhead | OK | crafting (frontierhunts:tracer_broadhead) | crafting: frontierhunts:tracer_broadhead |
| `frontierhunts:tracer_field_point` | Tracer Field Point | OK | crafting (frontierhunts:tracer_field_point) | crafting: frontierhunts:tracer_field_point |
| `frontierhunts:tracer_ice_broadhead` | Glacier Tracer Broadhead | OK | crafting (frontierhunts:tracer_ice_broadhead) | crafting: frontierhunts:tracer_ice_broadhead |
| `frontierhunts:trail_camera` | Trail Camera | OK | crafting (frontierhunts:trail_camera) | crafting: frontierhunts:trail_camera |
| `frontierhunts:trail_dome_tent` | Trail Dome Tent | OK | crafting (frontierhunts:trail_dome_tent) | crafting: frontierhunts:trail_dome_tent |
| `frontierhunts:trail_sign` | Trail Sign | OK | crafting (frontierhunts:reserve_trail_sign) | crafting: frontierhunts:reserve_trail_sign; breaking frontierhunts:trail_sign found in generated building frontierhunts:expedition/settlement_fishing |
| `frontierhunts:tranquilizer_dart` | Tranquilizer Dart | OK | crafting (frontierhunts:expedition_tranquilizer_dart) | crafting: frontierhunts:expedition_tranquilizer_dart; ranger board shop (expedition tokens) |
| `frontierhunts:tranquilizer_rifle` | Tranquilizer Rifle | OK | crafting (frontierhunts:expedition_tranquilizer_rifle) | crafting: frontierhunts:expedition_tranquilizer_rifle |
| `frontierhunts:tree_stand` | Trunk-Mounted Tree Stand | OK | crafting (frontierhunts:tree_stand) | crafting: frontierhunts:tree_stand |
| `frontierhunts:trophy_plinth` | Trophy Plinth | OK | crafting (frontierhunts:trophy_plinth) | crafting: frontierhunts:trophy_plinth; breaking frontierhunts:trophy_plinth found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:twelve_power_scope` | Twelve Power Scope | OK | crafting (frontierhunts:expedition_twelve_power_scope) | crafting: frontierhunts:expedition_twelve_power_scope; ranger board shop (expedition tokens) |
| `frontierhunts:two_power_prism` | Ridgeview 2× Prism | OK | crafting (frontierhunts:two_power_prism) | crafting: frontierhunts:two_power_prism |
| `frontierhunts:undergrowth` | Undergrowth | OK | breaking natural undergrowth with shears or silk touch (worldgen feature frontierhunts:undergrowth) | breaking natural frontierhunts:undergrowth with shears or silk touch (worldgen feature frontierhunts:undergrowth) |
| `frontierhunts:venison` | Raw Venison | OK | mob drop (frontierhunts:entities/pronghorn) | mob drop: frontierhunts:entities/pronghorn; field dressing a whitetail / elk / moose with the Skinning Tool; butchering a quarter with a knife (crafting grid) |
| `frontierhunts:venison_quarter` | Venison Quarter | OK | field dressing a whitetail / elk / moose with the Skinning Tool | field dressing a whitetail / elk / moose with the Skinning Tool |
| `frontierhunts:weapons_workbench` | Weapons Workbench | OK | crafting (frontierhunts:weapons_workbench) | crafting: frontierhunts:weapons_workbench; breaking frontierhunts:weapons_workbench found in generated building frontierhunts:expedition/settlement_hamlet |
| `frontierhunts:weathered_planks` | Weathered Planks | OK | crafting (frontierhunts:reserve_weathered_planks) | crafting: frontierhunts:reserve_weathered_planks |
| `frontierhunts:weathered_river_rock` | Weathered River Rock | OK | breaking natural weathered_river_rock (world generation (alpine rivers)) | breaking natural frontierhunts:weathered_river_rock (world generation (alpine rivers)) |
| `frontierhunts:whitetail_buck_spawn_egg` | Whitetail Buck Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:whitetail_doe_spawn_egg` | Whitetail Doe Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:whitetail_scent_decoy` | Whitetail Scent Decoy | OK | crafting (frontierhunts:whitetail_scent_decoy) | crafting: frontierhunts:whitetail_scent_decoy |
| `frontierhunts:whitetail_skull` | Whitetail Skull | OK | breaking natural whitetail_skull (world generation (bone sites)) | breaking natural frontierhunts:whitetail_skull (world generation (bone sites)) |
| `frontierhunts:whitetail_spawn_egg` | Whitetail Spawn Egg (Random) | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:whitetail_trophy` | Game Trophy | OK | field dressing a whitetail / elk / moose with the Skinning Tool (the trophy of the animal) | field dressing a whitetail / elk / moose with the Skinning Tool (the trophy of the animal) |
| `frontierhunts:wild_fowl` | Raw Wild Fowl | OK | ducks and grouse | ducks and grouse |
| `frontierhunts:willow_leaves` | Willow Leaves | OK | breaking natural willow_leaves with shears or silk touch (world generation (trees)) | breaking natural frontierhunts:willow_leaves with shears or silk touch (world generation (trees)) |
| `frontierhunts:wind_checker` | Wind Checker | OK | crafting (frontierhunts:wind_checker) | crafting: frontierhunts:wind_checker |
| `frontierhunts:wingsuit` | Wingsuit | OK | crafting (frontierhunts:wingsuit) | crafting: frontierhunts:wingsuit |
| `frontierhunts:wolf_spawn_egg` | Gray Wolf Spawn Egg | EXCEPTION | spawn egg (creative/admin only, like vanilla) |  |
| `frontierhunts:woodland_bush` | Hazel Shrub | OK | breaking natural woodland_bush (world generation (alpine vegetation)) | breaking natural frontierhunts:woodland_bush (world generation (alpine vegetation)) |
| `frontierhunts:woodland_camp_tent` | Woodland Camp Tent | OK | crafting (frontierhunts:woodland_camp_tent) | crafting: frontierhunts:woodland_camp_tent |
| `frontierstructures:dark_oak_lattice_window` | Dark Oak Lattice Window | OK | crafting (frontierstructures:dark_oak_lattice_window) | crafting: frontierstructures:dark_oak_lattice_window; breaking frontierstructures:dark_oak_lattice_window found in generated building frontierstructures:expedition/settlement_hamlet |
| `frontierstructures:lookout_brace` | Lookout Timber Brace | OK | crafting (frontierstructures:lookout_brace) | crafting: frontierstructures:lookout_brace; breaking frontierstructures:lookout_brace found in generated building frontierstructures:expedition/settlement_hamlet |
| `frontierstructures:lookout_cross_brace` | Lookout Cross Brace | OK | crafting (frontierstructures:lookout_cross_brace) | crafting: frontierstructures:lookout_cross_brace; breaking frontierstructures:lookout_cross_brace found in generated building frontierstructures:expedition/settlement_hamlet |
| `frontierstructures:lookout_deck_rail` | Lookout Deck Rail | OK | crafting (frontierstructures:lookout_deck_rail) | crafting: frontierstructures:lookout_deck_rail; breaking frontierstructures:lookout_deck_rail found in generated building frontierstructures:expedition/settlement_hamlet |
| `frontierstructures:lookout_stair_rail` | Lookout Stair Rail | OK | crafting (frontierstructures:lookout_stair_rail) | crafting: frontierstructures:lookout_stair_rail; breaking frontierstructures:lookout_stair_rail found in generated building frontierstructures:expedition/settlement_hamlet |
| `frontierstructures:spruce_casement_window` | Spruce Casement Window | OK | crafting (frontierstructures:spruce_casement_window) | crafting: frontierstructures:spruce_casement_window; breaking frontierstructures:spruce_casement_window found in generated building frontierstructures:expedition/settlement_hamlet |
| `frontierstructures:spruce_picture_window` | Spruce Picture Window | OK | crafting (frontierstructures:spruce_picture_window) | crafting: frontierstructures:spruce_picture_window; breaking frontierstructures:spruce_picture_window found in generated building frontierstructures:expedition/settlement_fishing |
