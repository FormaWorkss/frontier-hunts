# Workstream `economy` — recipe costs vs item power, store and board prices

Branch `economy` (from master 1650414). User request: *"make sure crafting recipes for items aren't too cheap or too
expensive for what that item is"*. Data-only rebalance (64 recipe JSONs) + one tiny Java class for store prices + 4
constants on the ranger board. Every recipe id, category, group and workbench list is unchanged. Not run in game here.

Checks: `tools/compile.sh` → exit=0 · `tools/build.py economy-test .` builds · `tools/check_jar.py <jar> . --base master`
→ **0 errors, 0 warnings** · `python3 tools/recipe_audit.py <jar>` → **PASS** (0 unobtainable, 0 errors, 259 recipes,
all with unlocks) · `tools/qa/server_smoke.sh <jar>` → **clean** (`recipes ok=259 bad=0`, no ERROR/WARN lines, no
exceptions, no client classes; the FHQA loot/addFreshEntity WARNs are the known ones in qa.md) ·
`python3 tools/economy/value_model.py <jar> --repo .` → **0 outliers, 0 grid conflicts, 0 unrated** (before: 31
outliers, 111 grid conflicts) · `tools/economy/ShopHarness.java` → PASS.

## 1. What was wrong (master)
* **One template for a dozen items.** Suppressor, extended magazine, steady stock, bipod, reflex sight, deer caller,
  predator call, medkit, scent cover, bait, expedition guide and fishing drag kit were all *leather + string + iron
  nugget* (7 pt). A suppressor cost less than a stack of arrows.
* **Every firearm was "2 iron + stick + string + X"** (23–38 pt): an assault rifle (20 rounds, automatic) cost less than
  a vanilla iron chestplate; the 6×, 8× and 12× scopes, binoculars and rangefinder were all *2 glass + copper + iron*
  (15 pt); thermal scope and thermal binoculars were that + 1 redstone.
* **The other way round:** the Ridgeline rifle (the Handbook's step-8 rifle) cost 3 iron blocks + amethyst (277 pt,
  2.5× the lever rifle's real worth); the skinning knife — needed to field-dress the very first deer — cost 3 iron
  ingots; the hide bedroll 3 pelts + 3 tanned hides; ATV can carrier 6 iron ingots, cargo box 3 ingots, camp cot 2 ingots.
* **111 crafting-grid conflicts** (same ingredients / same pattern ⇒ the grid can only ever give one of them): the 12
  items above, the 4 glass+copper+iron optics, micro red dot = holographic sight = field camera, muzzle brake = angled
  grip, pistol magazine = Ridgeline magazine, ammo reloader = clothing workbench, bowfishing arrow = judo point,
  **primitive arrow = vanilla arrow**, **bone point = vanilla bone meal** (a bone in the grid could stop giving bone
  meal), trail sign = wooden pickaxe, the 6 roof shapes, the 3 tent shapes, timber brace = lookout brace, and the 4
  Frontier Hunts "Timber" braces/rails = the 4 Frontier Structures "Lookout" ones.
* Hunting arrows used **coal as the shaft** (nugget/coal/feather).
* Store/board prices had no relation to value (suppressor 45 tokens for a 7 pt craft; 4 hunting arrows for 8 tokens;
  2 cow leather → 10 tokens = a cow-farm token printer).

## 2. The value model (`tools/economy/model.py`, `value_model.py`)
* **Effort points (pt)**, 1 pt ≈ 6 s of survival effort, calibrated on **1 iron ingot = 10 pt** (~60 ingots an hour of
  mid-game mining ⇒ ~600 pt/hour). Raw values (selection): stick 0.25, plank 0.5, log 2, flint 1.5, feather 1.5,
  string 2, leather 4, wool 2, bone 1.5, coal 2, copper 3, iron nugget 1.1, iron 10, iron block 90, gold 20, redstone 3,
  glass 1.2, amethyst 6, diamond 60, gunpowder 6, slime 15, glowstone 10, phantom membrane 20, tripwire hook 5.4,
  piston 15.7, blast furnace 55; deer hide 6, tanned hide/fur 8, bear fur 20, heavy hide 15. Intermediates (canvas,
  field points, broadheads, battery pack, binoculars…) are priced from their own recipe.
* **Tiers** (target per crafted unit, with a vanilla analog for every item): starter ≤ 15 (first 15 min; vanilla bow
  6.75, arrow 0.8) · early ≤ 60 (first hour, ≤ 1–3 iron; iron sword 20) · mid 30–150 (iron age, copper, redstone,
  amethyst; crossbow 22, iron chestplate 80) · late 100–350 (gold, diamond, iron blocks, nether) · endgame 250+.
  OK band = 0.6–1.7× target (decor/building blocks 0.33–3×). First-hour essentials (Handbook, arrows, field bow, bow
  tuning rack, skinning knife, field knife, field point, flint point, broadhead, tanning rack, canvas, pup/solo tent,
  hide bedroll, wind checker, journal) must use only wood/stone/flint/string/leather/feathers/wool/copper/hides and ≤ 1
  iron ingot.
* **Progression of materials**: wood → stone/flint/bone → iron/copper (knives, bows, binoculars, first scopes, tents) →
  iron blocks + mechanism parts (barrel = iron block, trigger = tripwire hook, lever action = lever, pump/slide/gas
  system = piston, timing = repeater) → gold (8×, holographic, pistol, NV) → diamond (12×, thermal) / nether (tracer
  nocks, glow lure) / phantom membrane (wingsuit).

## 3. Token economy
**Income per typical hunt** (a whitetail: find, stalk, shoot, track, dress, carry back ≈ 20 min):
assignment ≈ 45 (field patrol 40, clean recovery 60, before nightfall 65, trophy survey 120; cooldowns 5–15 min) +
expedition-contract share ≈ 20 (30–50 per contract, 20-minute windows) + provisions ≈ 15 (≈5 venison → 4 cooked = 12
tokens; hide → 2 leather = 5) ⇒ **≈ 80 tokens a hunt** (casual 40–50, everything 110), **≈ 240 tokens/hour** at 3
hunts/hour. One-off: first hunt 60, campaign 340, 85 hunt milestones 2,060. ⇒ **1 token ≈ 2.5 pt**.
**Store price = crafted value ÷ 2.5 × premium** (tokens skip the material gate, so the premium grows with tier):
consumables ×1.2, mid gear ×1.4, late ×1.6, endgame ×2. A thermal scope now costs ~1.3 hunts of tokens *and* is
2× what its materials are worth; a pack of 12 rifle rounds 8 tokens (≈ 10 % of a hunt).

| store item | pack | master price | master crafted | master ratio | **new price** | crafted | fair (÷2.5) | ratio |
|---|---|---|---|---|---|---|---|---|
| rifle_round | 12 | 8 | 16.8 pt | 1.2x | **8** | 16.8 pt | 6.7 | 1.19x |
| shotgun_shell | 12 | 8 | 12.1 pt | 1.7x | **6** | 12.1 pt | 4.8 | 1.24x |
| pistol_round | 12 | 6 | 16.8 pt | 0.9x | **8** | 16.8 pt | 6.7 | 1.19x |
| tranquilizer_dart | 12 | 12 | 25.9 pt | 1.2x | **12** | 25.9 pt | 10.4 | 1.16x |
| bowfishing_arrow | 12 | 8 | 5.0 pt | 4.0x | **3** | 5.4 pt | 2.2 | 1.39x |
| medkit | 1 | 10 | 7.1 pt | 3.5x | **4** | 8.0 pt | 3.2 | 1.25x |
| bait | 1 | 4 | 7.1 pt | 1.4x | **1** | 1.5 pt | 0.6 | 1.67x |
| scent_cover | 1 | 8 | 7.1 pt | 2.8x | **2** | 4.0 pt | 1.6 | 1.25x |
| suppressor | 1 | 45 | 7.1 pt | 15.8x | **36** | 64.2 pt | 25.7 | 1.4x |
| extended_magazine | 1 | 35 | 7.1 pt | 12.3x | **20** | 35.2 pt | 14.1 | 1.42x |
| steady_stock | 1 | 30 | 7.1 pt | 10.6x | **14** | 25.0 pt | 10.0 | 1.4x |
| bipod | 1 | 25 | 7.1 pt | 8.8x | **18** | 32.2 pt | 12.9 | 1.4x |
| six_power_scope | 1 | 40 | 15.4 pt | 6.5x | **22** | 40.2 pt | 16.1 | 1.37x |
| eight_power_scope | 1 | 55 | 15.4 pt | 8.9x | **40** | 63.2 pt | 25.3 | 1.58x |
| twelve_power_scope | 1 | 70 | 15.4 pt | 11.4x | **80** | 123.2 pt | 49.3 | 1.62x |
| thermal_scope | 1 | 85 | 18.4 pt | 11.5x | **105** | 131.2 pt | 52.5 | 2.0x |
| fishing_drag_kit | 1 | 20 | 7.1 pt | 7.0x | **7** | 15.0 pt | 6.0 | 1.17x |

**Ranger contract board** (`camp/ContractMenu` constants `BOARD_*`):

| trade | master | new | why |
|---|---|---|---|
| 4 cooked venison → tokens | 12 | 12 | hunting income (venison only comes from hunts) |
| 2 leather → tokens | 10 | **5** | 2 leather = 8 pt; at 10 a cow farm out-earned hunting (3× value) |
| buy 4 hunting arrows | 8 | **3** | 2.9 pt of arrows cost 7× their value |
| buy 8 Reserve .308 | 16 | **6** | 10 pt of cartridges cost 4× their value |

**Unchanged on purpose:** assignment / contract / hunt-milestone payouts (already scaled with difficulty: 15 for a trail
reading … 120 for a trophy buck); expedition camp upgrades (25/45/65/85 tokens for lodge stores + ammo reloader + bow
tuning rack + fishing station, a progression purchase in `ExpeditionService`, which is not in src); permits (8 tokens
for 3 tags, +5 per tag filed); tanning rack, drying rack, smokehouse, game pole and cooking (1:1 time-gated conversions,
not costs); Clothing Table furs/hides, ghillie, camo, carbon, tents, blinds, stations — all inside their band already.

## 4. Grid conflicts fixed (all 111)
Every recipe above got its own ingredients; shape-only fixes: primitive arrow corner to corner (`F  / S /  P`; straight
down stays the vanilla arrow), bone point = bone + flint, trail sign ` P /PPP/ S `, roof gable-low `SSS/P P/ P `,
ridge-high ` S /SPS/P P`, ridge-low `S S/ S /PPP`, slope-high `S  /SP /SPP`, slope-low `  S/ SS/PPP` (gable-high
unchanged), tent ridge ` C /CSC`, tent gable ` C /C C/ S ` (slope unchanged), timber brace = 2 planks + stick
diagonal, the Frontier Hunts "Timber" brace/cross/deck rail/stair rail from **dark oak slabs** (the Frontier Structures
"Lookout" set keeps dark oak planks). `value_model.py` checks mod↔mod and mod↔vanilla (QA server's resources jar).

## 5. Files
* **New**: `src/.../economy/ShopPrices.java` (MOD-bus `FMLCommonSetupEvent` → writes the prices into
  `ExpeditionService.SHOP`, the map both the store screen and the server's purchase check read; existing keys only);
  `tools/economy/{model.py, value_model.py, gen_recipes.py, ShopHarness.java}`; lang fragment
  `patch/_merge/assets/frontierhunts/lang/en_us.json/zzz_economy.json` (Handbook arrow/rifle texts, board trade lines,
  tanned fur note — sorts after onboard/z_recipes so it wins).
* **Recipes** (`tools/economy/gen_recipes.py . --base <built master jar>` writes them; idempotent): 64 files in
  `patch/data/frontierhunts/recipe/` (60 new overrides of base-jar recipes; edited: `atv_can_carrier`, `atv_cargo_box`,
  `jerry_can`, `survival/hide_bedroll`). Their recipe-book unlocks were deleted and regenerated by
  `tools/recipes/gen_unlocks.py` so they unlock on the new key materials (62 regenerated, 34 of them differ from master;
  `field_arrow` / `ridgeline_rifle` keep their legacy base-jar unlocks: stick / iron ingot).
* **Shared Java (marked `// [economy]`)**: `camp/ContractMenu.java` — 4 `public static final int BOARD_*` constants,
  the payout line `int var10 = var2 == 0 ? BOARD_VENISON_PAY : BOARD_LEATHER_PAY;` and the spend line
  `var2 == 2 ? BOARD_ARROWS_PRICE : BOARD_AMMO_PRICE`; `client/ContractScreen.java` — the two buy buttons' `active`
  checks use the same constants.
* **Handbook**: reads recipes live by id (`HandbookUi.view` → `RecipeManager.byKey`), all its ids are unchanged
  (`primitive_arrow`, `field_arrow`, `field_bow`, `bow_tuning_rack`, `field_point`, `fixed_broadhead`, `wind_checker`,
  `expedition_binoculars`, `skinning_tool`, `tanning_rack`, `clothing_workbench`, `clothing_table/fur_hat`,
  `reserve_canvas_wall`, `pup_tent`, `weapons_workbench`, `ridgeline_rifle`, `expedition_expedition_guide`), so its
  cards show the new costs; station labels follow the shape (skinning knife 2×2 → "Crafting grid (inventory or
  table)", arrows 1×3/3×3 → "Crafting table"). Workbench lists (Weapons, Ammunition, Optics, Bow, Fishing, Clothing,
  Tent) and Bow Tuning Rack head-making also read the recipes live.

## 6. For the coordinator
After merging: rebuild → `python3 tools/recipes/gen_unlocks.py <jar> .` (only if another branch added recipes) → rebuild →
`python3 tools/recipe_audit.py <jar>` (PASS) and `python3 tools/economy/value_model.py <jar> --repo .` (exit 0: no grid
conflicts, no unrated recipe; outliers listed). A branch that adds a recipe must add its item to `TARGET` in
`tools/economy/model.py` (else "UNRATED"). If another branch edited one of the 64 recipe files, keep its new item/id
and re-run `gen_recipes.py` (it only rewrites pattern/key/ingredients/count).

## 7. IN-GAME TEST SCRIPT
Setup: survival world, op, Frontier survival any. Have a crafting table.
1. **Arrows**: `/give @s minecraft:flint 4`, `minecraft:stick 8`, `minecraft:feather 8`, `minecraft:iron_nugget 4`.
   Flint/stick/feather straight down → 4 vanilla **Arrows**; corner to corner (top-left flint, centre stick,
   bottom-right feather) → 4 **Primitive Arrows**; nugget/stick/feather straight down → 4 **Hunting Arrows**.
2. **Bone**: 1 bone alone → 3 **Bone Meal** (every time); bone + flint → 3 **Bone Points**.
3. **Skinning knife** in the 2×2 inventory grid: iron ingot top-right, stick bottom-left, leather bottom-right →
   Contour Skinning Knife. Field-dress a deer with it.
4. **Old template is dead**: leather + string + iron nugget in the grid → no output. Each of: suppressor (`III/NWN/III`),
   extended magazine (`N N/ICI/ I `), bipod (` I /I I/N N`), steady stock (`PI /PIL`), reflex sight (`IRI/CGC`), deer
   caller (note block + redstone + copper + iron), predator call (bone + copper + string), medkit (2 wool + paper +
   string + apple), scent cover (bottle + charcoal + leaves), bait (2 wheat + rotten flesh), expedition guide (book +
   paper + string), drag kit (iron + copper + string) → its own item.
5. **Weapons Workbench**: open it — every firearm lists the new materials ("Have / Need"): lever rifle = iron block,
   iron ingot, lever, tripwire hook, 2 planks; pump = + piston; assault rifle = 2 iron blocks, iron, piston, repeater,
   tripwire hook, 2 planks; Ridgeline = iron block, 2 iron, tripwire hook, 2 planks. Give the materials for one and
   craft it there and in a crafting table.
6. **Optics & Attachment Workbench**: 6× (3 iron, glass, amethyst, copper), 8× (+gold, 2 amethyst), 12× (+diamond),
   thermal scope (diamond, 2 gold, 2 redstone, battery, amethyst, glass, iron), thermal binoculars (binoculars +
   diamond + 2 gold + 2 redstone + battery). Micro red dot (` I /IGI/ R `), holographic (gold on top) and field camera
   (`CRC/IGI`) each give their own item.
7. **Bow Tuning Rack**: recurve bow (2 bone, 2 planks, leather, 3 string), compound (3 iron, 2 nuggets, 3 string,
   leather), crossbow (3 iron, 2 string, tripwire hook, plank). Arrow tips tab: "Make N heads" still crafts field
   points / broadheads; bone points now need flint.
8. **Building**: with roof shingles + pine planks lay out the 6 roof shapes (§4) → 6 different roof blocks; canvas +
   stick → tent slope / ridge / gable; 3 dark oak planks diagonal → *Lookout* Timber Brace (Frontier Structures),
   3 dark oak slabs diagonal → *Timber Diagonal Brace*; 2 planks + stick diagonal → Diagonal Timber Brace.
9. **Handbook (H)**: step 2 text says "corner to corner" and "nugget, stick, feather"; the arrow cards show the new
   shapes; step 4 binoculars card (copper/iron/glass/leather), step 6 skinning knife card (2×2, "Crafting grid
   (inventory or table)"), step 7 pup tent, step 8 Ridgeline (iron block…).
10. **Recipe book**: pick up a diamond → thermal scope / 12× scope recipes appear; pick up a note block → deer caller.
11. **Contract board**: `/give @s frontierhunts:cooked_venison 16`, `minecraft:leather 4`: deliveries pay 12 and **5**
    tokens; "Ranger arrow bundle · costs 3 tokens" and ".308 ×8 · 6 tokens" (buttons enable at 3 / 6 tokens).
12. **Expedition store** (near the station, N → store): prices as in §3 (rifle rounds 8, suppressor 36, 6× 22, 8× 40,
    12× 80, thermal 105 …). Buy one: exactly that many tokens leave. Dedicated server + client: the price shown equals
    the price charged.

## 8. Full before/after table (every judged recipe; `tools/economy/value_model.py --before … --md`)
pt = effort points per crafted unit (1 iron ingot = 10). `=` = unchanged. Conversions (logs, cooking, refills,
sewing, salt cure, butchering) are not judged.

| recipe | tier | before | pt | after | pt | target | ratio | verdict | why |
|---|---|---|---|---|---|---|---|---|---|
| `bleat_call` | starter | 1 iron_nugget, 1 leather, 1 stick -> 1 | 5.35 | = |  | 6 | 0.89 | ok |  |
| `bone_point` | starter | 1 bone -> 3 | 0.5 | **1 bone, 1 flint -> 3** | 1.0 | 0.9 | 1.11 | was TOO CHEAP → ok | knapped with flint; a lone bone is vanilla bone meal |
| `bow_tuning_rack` | starter | 5 #planks, 3 string -> 1 | 8.5 | = |  | 10 | 0.85 | ok |  |
| `camp_post` | starter | 3 stick, 2 cobblestone, 1 #wool -> 1 | 3.35 | = |  | 4 | 0.84 | ok |  |
| `contract_board` | starter | 3 #planks, 3 paper, 2 stick -> 1 | 5.0 | = |  | 5 | 1.0 | ok |  |
| `expedition_bait` | starter | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **2 wheat, 1 rotten_flesh -> 1** | 1.5 | 1.5 | 1.0 | was TOO EXPENSIVE → ok | grain and scraps |
| `expedition_board` | starter | 5 #planks, 3 paper -> 1 | 5.5 | = |  | 6 | 0.92 | ok |  |
| `field_arrow` | starter | 1 #coals, 1 feather, 1 iron_nugget -> 4 | 1.15 | **1 feather, 1 iron_nugget, 1 stick -> 4** `N/S/P` | 0.71 | 0.8 | 0.89 | ok | a stick shaft instead of coal |
| `field_bow` | starter | 3 stick, 2 string, 1 leather -> 1 | 8.75 | = |  | 8 | 1.09 | ok |  |
| `field_fishing_rod` | starter | 3 stick, 2 string, 1 copper_ingot -> 1 | 7.75 | = |  | 7 | 1.11 | ok |  |
| `field_point` | starter | 2 iron_nugget -> 4 | 0.55 | = |  | 0.6 | 0.92 | ok |  |
| `flint_point` | starter | 1 flint, 1 string -> 3 | 1.17 | = |  | 1.2 | 0.97 | ok |  |
| `frontier_handbook` | starter | 1 book, 1 feather -> 1 | 8.5 | = |  | 8 | 1.06 | ok |  |
| `game_pole` | starter | 3 stick, 2 #logs -> 1 | 4.75 | = |  | 6 | 0.79 | ok |  |
| `grunt_tube` | starter | 1 copper_ingot, 1 leather, 1 stick -> 1 | 7.25 | = |  | 8 | 0.91 | ok |  |
| `hunter_journal` | starter | 1 leather, 1 paper -> 1 | 5.0 | = |  | 5 | 1.0 | ok |  |
| `judo_point` | starter | 1 iron_nugget, 1 stick, 1 string -> 4 | 0.84 | = |  | 0.8 | 1.05 | ok |  |
| `primitive_arrow` | starter | 1 feather, 1 flint, 1 stick -> 4 | 0.81 | **1 feather, 1 flint, 1 stick -> 4** `F  / S /  P` | 0.81 | 0.8 | 1.02 | ok | laid corner to corner: straight down is the vanilla arrow recipe, the grid could only make one |
| `survival/drying_rack` | starter | 7 stick, 1 string -> 1 | 3.75 | = |  | 5 | 0.75 | ok |  |
| `tanning_rack` | starter | 4 stick, 3 #planks, 1 string -> 1 | 4.5 | = |  | 6 | 0.75 | ok |  |
| `wind_checker` | starter | 1 bone_meal, 1 glass_bottle, 1 leather -> 1 | 5.7 | = |  | 5 | 1.14 | ok |  |
| `autumn_camo_coveralls` | early | 6 leather, 1 brown_wool, 1 orange_dye -> 1 | 28.0 | = |  | 30 | 0.93 | ok |  |
| `backpacker_dome_tent` | early | 2 green_wool, 2 iron_nugget, 2 string, 1 fh:canvas_wall -> 1 | 15.95 | = |  | 15 | 1.06 | ok |  |
| `bell_tent` | early | 4 fh:canvas_wall, 2 #planks, 2 string, 1 leather -> 1 | 24.0 | = |  | 25 | 0.96 | ok |  |
| `blaze_camo_coveralls` | early | 6 leather, 1 black_dye, 1 orange_wool -> 1 | 29.0 | = |  | 30 | 0.97 | ok |  |
| `canvas_wall_tent` | early | 3 fh:canvas_wall, 3 string, 2 #logs, 1 leather -> 1 | 25.25 | = |  | 26 | 0.97 | ok |  |
| `chum_bucket` | early | 1 #fishes, 1 bucket, 1 rotten_flesh -> 1 | 33.5 | = |  | 34 | 0.99 | ok |  |
| `clothing_workbench` | early | 5 #planks, 3 copper_ingot -> 1 | 11.5 | = |  | 12 | 0.96 | ok |  |
| `cut_on_contact_broadhead` | early | 1 flint, 1 iron_ingot, 1 iron_nugget -> 4 | 3.15 | = |  | 3.2 | 0.98 | ok |  |
| `digital_camo_coveralls` | early | 6 leather, 1 gray_dye, 1 green_wool -> 1 | 28.5 | = |  | 30 | 0.95 | ok |  |
| `duck_call` | early | 1 #planks, 1 gold_nugget, 1 stick, 1 string -> 1 | 4.95 | = |  | 6 | 0.83 | ok |  |
| `expedition_bait_launcher` | early | 2 iron_ingot, 1 hay_block, 1 stick, 1 string -> 1 | 26.75 | = |  | 28 | 0.96 | ok |  |
| `expedition_binoculars` | early | 2 glass, 1 copper_ingot, 1 iron_ingot -> 1 | 15.4 | **2 copper_ingot, 2 glass, 1 iron_ingot, 1 leather -> 1** `CIC/G G/ L ` | 22.4 | 22 | 1.02 | ok | two copper tubes, two lenses, hinge, strap |
| `expedition_bowfishing_arrow` | early | 1 iron_nugget, 1 stick, 1 string -> 8 | 0.42 | **2 stick, 1 iron_nugget, 1 string -> 8** | 0.45 | 0.5 | 0.9 | ok | long shaft (2 sticks); was identical to the judo point |
| `expedition_bowfishing_bow` | early | 2 iron_ingot, 1 cod, 1 stick, 1 string -> 1 | 25.25 | = |  | 22 | 1.15 | ok |  |
| `expedition_expedition_guide` | early | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **1 book, 1 paper, 1 string -> 1** | 10.0 | 10 | 1.0 | ok | a bound guide |
| `expedition_fishing_drag_kit` | early | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **1 copper_ingot, 1 iron_ingot, 1 string -> 1** | 15.0 | 12 | 1.25 | was TOO CHEAP → ok | reel drag washers |
| `expedition_flare_gun` | early | 2 iron_ingot, 1 glowstone_dust, 1 stick, 1 string -> 1 | 32.25 | **2 iron_ingot, 1 copper_ingot, 1 tripwire_hook -> 1** `II/CH` | 28.4 | 28 | 1.01 | ok | no nether glowstone needed for a signal pistol |
| `expedition_flare_round` | early | 1 glowstone_dust, 1 gunpowder, 1 paper -> 8 | 2.12 | **1 gunpowder, 1 paper, 1 redstone -> 8** | 1.25 | 1.3 | 0.96 | ok | red signal flare (redstone) instead of glowstone |
| `expedition_hunting_spear` | early | 2 iron_ingot, 1 flint, 1 stick, 1 string -> 1 | 23.75 | = |  | 22 | 1.08 | ok |  |
| `expedition_medkit` | early | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **2 #wool, 1 apple, 1 paper, 1 string -> 1** | 8.0 | 9 | 0.89 | ok | bandages, tape and a snack |
| `expedition_predator_call` | early | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **1 bone, 1 copper_ingot, 1 string -> 1** | 6.5 | 8 | 0.81 | ok | mouth call with a copper reed |
| `expedition_recurve_bow` | early | 2 iron_ingot, 1 oak_planks, 1 stick, 1 string -> 1 | 22.75 | **3 string, 2 #planks, 2 bone, 1 leather -> 1** `BPT/L T/BPT` | 14.0 | 15 | 0.93 | ok | laminated wood limbs, bone tips, leather grip (no iron) |
| `expedition_scent_cover` | early | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **1 #leaves, 1 charcoal, 1 glass_bottle -> 1** | 4.0 | 4 | 1.0 | was TOO EXPENSIVE → ok | carbon and pine spray |
| `field_blind` | early | 4 fh:canvas_wall, 4 iron_nugget, 1 string -> 1 | 21.4 | = |  | 22 | 0.97 | ok |  |
| `field_knife` | early | 1 iron_ingot, 1 stick -> 1 | 10.25 | = |  | 14 | 0.73 | ok |  |
| `fishing_station` | early | 5 #planks, 3 cod -> 1 | 11.5 | = |  | 12 | 0.96 | ok |  |
| `fixed_broadhead` | early | 1 iron_ingot, 1 iron_nugget -> 4 | 2.77 | = |  | 2.8 | 0.99 | ok |  |
| `horse_whistle` | early | 1 gold_nugget, 1 iron_nugget -> 1 | 3.3 | = |  | 4 | 0.83 | ok |  |
| `hound_lead` | early | 1 beef, 1 bone, 1 lead, 1 leather -> 1 | 19.0 | = |  | 20 | 0.95 | ok |  |
| `hunters_canvas_tent` | early | 3 fh:canvas_wall, 2 stick, 2 string, 1 lantern -> 1 | 25.35 | = |  | 26 | 0.98 | ok |  |
| `hunters_quiver` | early | 4 leather, 2 string -> 1 | 20.0 | = |  | 15 | 1.33 | ok |  |
| `landing_net` | early | 3 string, 2 stick, 1 iron_nugget -> 1 | 7.6 | = |  | 8 | 0.95 | ok |  |
| `lodge_stores` | early | 5 #planks, 3 chest -> 1 | 14.5 | = |  | 14 | 1.04 | ok |  |
| `mallard_decoy` | early | 3 #planks, 1 green_dye -> 2 | 1.5 | = |  | 2 | 0.75 | ok |  |
| `marsh_camo_coveralls` | early | 6 leather, 1 brown_wool, 1 green_dye -> 1 | 28.5 | = |  | 30 | 0.95 | ok |  |
| `prairie_camo_coveralls` | early | 6 leather, 1 brown_dye, 1 yellow_wool -> 1 | 29.0 | = |  | 30 | 0.97 | ok |  |
| `pup_tent` | early | 4 string, 3 fh:canvas_wall, 1 stick -> 1 | 19.5 | = |  | 18 | 1.08 | ok |  |
| `rattling_antlers` | early | 4 bone, 1 string -> 1 | 8.0 | = |  | 8 | 1.0 | ok |  |
| `reserve_camp_cot` | early | 3 fh:deer_hide, 2 iron_ingot, 1 stick -> 1 | 38.25 | **3 fh:deer_hide, 2 stick, 1 #planks -> 1** `HHH/SPS` | 19.0 | 18 | 1.06 | was TOO EXPENSIVE → ok | wooden frame instead of 2 iron ingots |
| `reserve_canvas_wall` | early | 6 string, 3 fh:deer_hide -> 8 | 3.75 | = |  | 3.7 | 1.01 | ok |  |
| `shooting_target` | early | 5 #wool, 3 #planks, 1 hay_block -> 1 | 16.0 | = |  | 16 | 1.0 | ok |  |
| `skinning_tool` | early | 3 iron_ingot, 1 leather, 1 stick -> 1 | 34.25 | **1 iron_ingot, 1 leather, 1 stick -> 1** ` I/SL` | 14.25 | 14 | 1.02 | was NOT FIRST-HOUR → ok | one iron blade: needed to field-dress the first deer |
| `smokehouse` | early | 6 iron_nugget, 2 #logs, 1 smoker -> 1 | 21.0 | = |  | 20 | 1.05 | ok |  |
| `snow_camo_coveralls` | early | 6 leather, 1 light_gray_dye, 1 white_wool -> 1 | 27.0 | = |  | 30 | 0.9 | ok |  |
| `solo_ridge_tent` | early | 2 orange_wool, 2 string, 1 fh:canvas_wall -> 1 | 13.75 | = |  | 15 | 0.92 | ok |  |
| `survival/hide_bedroll` | early | 3 #fh:tanned_furs, 3 #fh:tanned_hides -> 1 | 48.0 | **3 #fh:tanned_hides, 3 #wool -> 1** `WWW/HHH` | 30.0 | 25 | 1.2 | was NOT FIRST-HOUR → ok | wool over hides instead of 3 pelts + 3 hides |
| `survival/pemmican` | early | 2 fh:jerky, 1 #c:foods/berry, 1 fh:tallow -> 3 | 3.5 | = |  | 3.5 | 1.0 | ok |  |
| `tent_bench` | early | 5 #planks, 2 fh:canvas_wall, 1 string -> 1 | 12.0 | = |  | 12 | 1.0 | ok |  |
| `timber_camo_coveralls` | early | 6 leather, 1 brown_dye, 1 green_wool -> 1 | 29.0 | = |  | 30 | 0.97 | ok |  |
| `trail_dome_tent` | early | 3 fh:canvas_wall, 3 string, 2 iron_nugget, 1 leather -> 1 | 23.45 | = |  | 24 | 0.98 | ok |  |
| `weapons_workbench` | early | 3 iron_ingot, 2 #logs, 2 #planks, 1 crafting_table -> 1 | 37.0 | = |  | 40 | 0.93 | ok |  |
| `whitetail_scent_decoy` | early | 2 iron_nugget, 1 fh:bait, 1 oak_sapling, 1 string -> 1 | 12.3 | = |  | 8 | 0.84 | ok |  |
| `ammo_reloader` | mid | 5 #planks, 3 copper_ingot -> 1 | 11.5 | **3 #planks, 2 copper_ingot, 1 iron_ingot, 1 piston -> 1** ` I /CKC/LLL` | 33.2 | 35 | 0.95 | was TOO CHEAP → ok | a reloading press (piston); was identical to the Clothing Workbench recipe |
| `angled_foregrip` | mid | 3 iron_ingot, 1 iron_nugget, 1 leather -> 1 | 35.1 | = |  | 25 | 1.4 | ok |  |
| `attachment_tool` | mid | 2 iron_ingot, 2 iron_nugget, 1 leather -> 1 | 26.2 | = |  | 20 | 1.31 | ok |  |
| `attachment_workbench` | mid | 3 #planks, 2 iron_ingot, 2 stick, 1 copper_ingot -> 1 | 25.0 | = |  | 28 | 0.89 | ok |  |
| `atv_can_carrier` | mid | 6 iron_ingot, 1 leather -> 1 | 64.0 | **4 iron_nugget, 2 iron_ingot, 1 leather -> 1** `N N/ILI/N N` | 28.4 | 28 | 1.01 | was TOO EXPENSIVE → ok | a rack, not 6 iron ingots |
| `atv_cargo_box` | mid | 4 leather, 3 iron_ingot, 1 black_dye, 1 chest -> 1 | 52.0 | **4 leather, 3 iron_nugget, 1 black_dye, 1 chest -> 1** `LDL/NCN/LNL` | 25.3 | 25 | 1.01 | was TOO EXPENSIVE → ok | nugget fittings instead of 3 iron ingots |
| `camera_base_station` | mid | 7 iron_ingot, 1 glass_pane, 1 redstone_block -> 1 | 97.45 | = |  | 85 | 1.15 | ok |  |
| `clothing_table/buckskin_coat` | mid | 7 fh:tanned_hide, 1 string -> 1 (Clothing Table) | 58.0 | = |  | 55 | 1.05 | ok |  |
| `clothing_table/buckskin_leggings` | mid | 6 fh:tanned_hide, 1 string -> 1 (Clothing Table) | 50.0 | = |  | 50 | 1.0 | ok |  |
| `clothing_table/carbon_hood` | mid | 2 fh:tanned_hide, 2 leather, 1 charcoal -> 1 (Clothing Table) | 26.5 | = |  | 22 | 1.2 | ok |  |
| `clothing_table/carbon_jacket` | mid | 4 leather, 3 fh:tanned_hide, 1 charcoal -> 1 (Clothing Table) | 42.5 | = |  | 32 | 1.33 | ok |  |
| `clothing_table/carbon_trousers` | mid | 4 leather, 2 fh:tanned_hide, 1 charcoal -> 1 (Clothing Table) | 34.5 | = |  | 30 | 1.15 | ok |  |
| `clothing_table/fur_hat` | mid | 3 #fh:tanned_furs, 1 fh:tanned_hide, 1 string -> 1 (Clothing Table) | 34.0 | = |  | 34 | 1.0 | ok |  |
| `clothing_table/fur_lining` | mid | 2 fh:tanned_fur, 1 fh:tanned_hide -> 1 (Clothing Table) | 24.0 | = |  | 24 | 1.0 | ok |  |
| `clothing_table/fur_lining_from_bear_fur` | mid | 1 fh:bear_fur, 1 fh:tanned_hide -> 1 (Clothing Table) | 28.0 | = |  | 24 | 1.17 | ok |  |
| `clothing_table/fur_mittens` | mid | 2 fh:tanned_fur, 1 string -> 1 (Clothing Table) | 18.0 | = |  | 18 | 1.0 | ok |  |
| `clothing_table/fur_mukluks` | mid | 2 #fh:tanned_furs, 2 #fh:tanned_hides, 1 string -> 1 (Clothing Table) | 34.0 | = |  | 34 | 1.0 | ok |  |
| `expedition_bipod` | mid | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **3 iron_ingot, 2 iron_nugget -> 1** ` I /I I/N N` | 32.2 | 24 | 1.34 | was TOO CHEAP → ok | steel legs |
| `expedition_compound_bow` | mid | 2 iron_ingot, 1 quartz, 1 stick, 1 string -> 1 | 30.25 | **3 iron_ingot, 3 string, 2 iron_nugget, 1 leather -> 1** `NIT/ILT/NIT` | 42.2 | 50 | 0.84 | ok | steel riser, cams (nuggets), cables |
| `expedition_crossbow` | mid | 2 iron_ingot, 1 stick, 1 string, 1 tripwire_hook -> 1 | 27.65 | **3 iron_ingot, 2 string, 1 #planks, 1 tripwire_hook -> 1** `III/THT/ P ` | 39.9 | 50 | 0.8 | was TOO CHEAP → ok | steel prod, trigger (tripwire hook), stock |
| `expedition_deer_call` | mid | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **1 copper_ingot, 1 iron_ingot, 1 note_block, 1 redstone -> 1** | 23.0 | 25 | 0.92 | was TOO CHEAP → ok | electronic caller: it pulls every deer within 64 blocks |
| `expedition_double_barrel` | mid | 2 iron_ingot, 1 iron_block, 1 stick, 1 string -> 1 | 112.25 | **2 #planks, 1 iron_block, 1 tripwire_hook -> 1** `  B/ P /PH ` | 96.4 | 95 | 1.01 | ok | two barrels (iron block), stock, trigger |
| `expedition_extended_magazine` | mid | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **3 iron_ingot, 2 iron_nugget, 1 copper_ingot -> 1** `N N/ICI/ I ` | 35.2 | 35 | 1.01 | was TOO CHEAP → ok | steel box, copper spring |
| `expedition_field_flashlight` | mid | 2 copper_ingot, 2 iron_ingot, 1 glass_pane, 1 redstone -> 1 | 29.45 | = |  | 22 | 1.34 | ok |  |
| `expedition_lever_rifle` | mid | 2 iron_ingot, 1 lever, 1 stick, 1 string -> 1 | 22.8 | **2 #planks, 1 iron_block, 1 iron_ingot, 1 lever, 1 tripwire_hook -> 1** `  B/PIV/PH ` | 106.95 | 110 | 0.97 | was TOO CHEAP → ok | barrel, receiver, lever action, trigger, stock |
| `expedition_pistol_magazine` | mid | 5 iron_nugget, 1 iron_ingot -> 1 | 15.5 | **4 iron_nugget, 1 copper_ingot, 1 iron_ingot -> 1** `N N/NCN/ I ` | 17.4 | 18 | 0.97 | ok | was identical to the Ridgeline magazine |
| `expedition_pistol_round` | mid | 1 copper_ingot, 1 gold_nugget, 1 gunpowder -> 8 | 1.4 | = |  | 1.3 | 1.08 | ok |  |
| `expedition_pump_shotgun` | mid | 2 iron_ingot, 1 piston, 1 stick, 1 string -> 1 | 37.95 | **2 #planks, 1 iron_block, 1 iron_ingot, 1 piston, 1 tripwire_hook -> 1** `  B/PIK/PH ` | 122.1 | 115 | 1.06 | was TOO CHEAP → ok | pump action = piston |
| `expedition_rangefinder` | mid | 2 glass, 1 copper_ingot, 1 iron_ingot -> 1 | 15.4 | **2 copper_ingot, 2 iron_ingot, 2 redstone, 1 glass -> 1** `CGC/IRI/ R ` | 33.2 | 32 | 1.04 | was TOO CHEAP → ok | laser electronics (redstone) |
| `expedition_reflex_sight` | mid | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **2 copper_ingot, 2 iron_ingot, 1 glass, 1 redstone -> 1** `IRI/CGC` | 30.2 | 28 | 1.08 | was TOO CHEAP → ok | illuminated dot (redstone) |
| `expedition_revolver` | mid | 2 iron_ingot, 1 stick, 1 stone_button, 1 string -> 1 | 22.55 | **5 iron_ingot, 1 #planks, 1 tripwire_hook -> 1** `III/IHI/ P ` | 55.9 | 70 | 0.8 | was TOO CHEAP → ok | steel frame and cylinder, trigger, wooden grip |
| `expedition_rifle_round` | mid | 2 iron_nugget, 1 copper_ingot, 1 gunpowder -> 8 | 1.4 | = |  | 1.4 | 1.0 | ok |  |
| `expedition_shotgun_shell` | mid | 1 gunpowder, 1 iron_nugget, 1 paper -> 8 | 1.01 | = |  | 1.0 | 1.01 | ok |  |
| `expedition_six_power_scope` | mid | 2 glass, 1 copper_ingot, 1 iron_ingot -> 1 | 15.4 | **3 iron_ingot, 1 amethyst_shard, 1 copper_ingot, 1 glass -> 1** ` C /GIA/I I` | 40.2 | 45 | 0.89 | was TOO CHEAP → ok | iron tube, objective (glass), ocular (amethyst like a spyglass), turret |
| `expedition_sniper_magazine` | mid | 5 iron_nugget, 1 iron_ingot -> 1 | 15.5 | = |  | 18 | 0.86 | ok |  |
| `expedition_steady_stock` | mid | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **2 #planks, 2 iron_ingot, 1 leather -> 1** `PI /PIL` | 25.0 | 25 | 1.0 | was TOO CHEAP → ok | wood, steel, leather cheek pad |
| `expedition_suppressor` | mid | 1 iron_nugget, 1 leather, 1 string -> 1 | 7.1 | **6 iron_ingot, 2 iron_nugget, 1 #wool -> 1** `III/NWN/III` | 64.2 | 65 | 0.99 | was TOO CHEAP → ok | steel tube and baffles |
| `expedition_tranquilizer_dart` | mid | 1 glass_bottle, 1 iron_nugget, 1 slime_ball -> 8 | 2.16 | = |  | 2.2 | 0.98 | ok |  |
| `expedition_tranquilizer_rifle` | mid | 2 iron_ingot, 1 slime_ball, 1 stick, 1 string -> 1 | 37.25 | **2 iron_ingot, 1 #planks, 1 glass_bottle, 1 slime_ball, 1 tripwire_hook -> 1** `  I/MIG/PH ` | 42.1 | 50 | 0.84 | ok | gas dart rifle |
| `family_cabin_tent` | mid | 3 fh:canvas_wall, 3 string, 2 iron_ingot, 1 green_wool -> 1 | 40.25 | = |  | 40 | 1.01 | ok |  |
| `field_battery_pack` | mid | 4 copper_ingot, 1 iron_nugget, 1 redstone -> 2 | 8.05 | = |  | 8 | 1.01 | ok |  |
| `field_camera` | mid | 3 iron_ingot, 1 glass, 1 redstone -> 1 | 34.2 | **2 copper_ingot, 2 iron_ingot, 1 glass, 1 redstone -> 1** `CRC/IGI` | 30.2 | 30 | 1.01 | ok | was identical to the micro red dot |
| `fish_finder` | mid | 3 iron_ingot, 2 redstone, 1 copper_ingot, 1 glass_pane -> 1 | 39.45 | = |  | 45 | 0.88 | ok |  |
| `four_power_optic` | mid | 3 glass, 3 iron_ingot, 1 amethyst_shard -> 1 | 39.6 | = |  | 50 | 0.79 | ok |  |
| `ghillie_grassland_hood` | mid | 3 string, 2 fh:deer_hide, 1 yellow_dye -> 1 | 19.0 | = |  | 20 | 0.95 | ok |  |
| `ghillie_grassland_jacket` | mid | 6 string, 2 fh:deer_hide, 1 yellow_dye -> 1 | 25.0 | = |  | 26 | 0.96 | ok |  |
| `ghillie_grassland_trousers` | mid | 4 string, 3 fh:deer_hide, 1 yellow_dye -> 1 | 27.0 | = |  | 26 | 1.04 | ok |  |
| `ghillie_hood` | mid | 3 string, 2 fh:deer_hide -> 1 | 18.0 | = |  | 20 | 0.9 | ok |  |
| `ghillie_jacket` | mid | 6 string, 2 fh:deer_hide -> 1 | 24.0 | = |  | 26 | 0.92 | ok |  |
| `ghillie_snow_hood` | mid | 3 string, 2 fh:deer_hide, 1 white_dye -> 1 | 18.5 | = |  | 20 | 0.93 | ok |  |
| `ghillie_snow_jacket` | mid | 6 string, 2 fh:deer_hide, 1 white_dye -> 1 | 24.5 | = |  | 26 | 0.94 | ok |  |
| `ghillie_snow_trousers` | mid | 4 string, 3 fh:deer_hide, 1 white_dye -> 1 | 26.5 | = |  | 26 | 1.02 | ok |  |
| `ghillie_trousers` | mid | 4 string, 3 fh:deer_hide -> 1 | 26.0 | = |  | 26 | 1.0 | ok |  |
| `ghillie_wetland_hood` | mid | 3 string, 2 fh:deer_hide, 1 green_dye -> 1 | 19.5 | = |  | 20 | 0.97 | ok |  |
| `ghillie_wetland_jacket` | mid | 6 string, 2 fh:deer_hide, 1 green_dye -> 1 | 25.5 | = |  | 26 | 0.98 | ok |  |
| `ghillie_wetland_trousers` | mid | 4 string, 3 fh:deer_hide, 1 green_dye -> 1 | 27.5 | = |  | 26 | 1.06 | ok |  |
| `holographic_sight` | mid | 3 iron_ingot, 1 glass, 1 redstone -> 1 | 34.2 | **2 iron_ingot, 1 glass, 1 gold_ingot, 1 redstone -> 1** ` A /IGI/ R ` | 44.2 | 55 | 0.8 | ok | laser holographic (gold); was identical to the micro red dot and the field camera |
| `hunter_pack` | mid | 7 leather, 1 chest, 1 string -> 1 | 34.0 | = |  | 35 | 0.97 | ok |  |
| `jerry_can` | mid | 6 iron_ingot, 1 iron_nugget, 1 red_dye -> 1 | 62.1 | **4 iron_ingot, 1 iron_nugget, 1 red_dye -> 1** ` N /IRI/I I` | 42.1 | 40 | 1.05 | ok | 4 iron ingots (a bucket is 3) |
| `mechanical_broadhead` | mid | 1 iron_ingot, 1 iron_nugget, 1 string -> 4 | 3.27 | = |  | 3.5 | 0.94 | ok |  |
| `micro_red_dot` | mid | 3 iron_ingot, 1 glass, 1 redstone -> 1 | 34.2 | = |  | 32 | 1.07 | ok |  |
| `muzzle_brake` | mid | 3 iron_ingot, 1 iron_nugget, 1 leather -> 1 | 35.1 | **3 iron_ingot, 2 iron_nugget -> 1** ` I /INI/ N ` | 32.2 | 30 | 1.07 | ok | all steel; was identical to the angled foregrip |
| `obsidian_point` | mid | 1 obsidian -> 6 | 2.0 | = |  | 2.5 | 0.8 | ok |  |
| `reserve_308` | mid | 1 copper_ingot, 1 gunpowder, 1 iron_nugget -> 8 | 1.26 | = |  | 1.4 | 0.9 | ok |  |
| `ridgeline_rifle` | mid | 3 iron_block, 2 #planks, 1 amethyst_shard -> 1 | 277.0 | **2 #planks, 2 iron_ingot, 1 iron_block, 1 tripwire_hook -> 1** `  B/PII/PH ` | 116.4 | 125 | 0.93 | was TOO EXPENSIVE → ok | one barrel (iron block) instead of three iron blocks + amethyst |
| `ridgeline_scope` | mid | 3 iron_ingot, 2 black_dye, 1 amethyst_shard, 1 glass_pane -> 1 | 40.45 | = |  | 42 | 0.96 | ok |  |
| `scent_suit` | mid | 6 leather, 1 fh:tanned_hide, 1 charcoal -> 1 | 34.5 | = |  | 35 | 0.99 | ok |  |
| `tower_blind` | mid | 6 iron_ingot, 2 ladder, 1 fh:canvas_wall -> 1 | 64.95 | = |  | 60 | 1.08 | ok |  |
| `trail_camera` | mid | 4 iron_ingot, 1 glass, 1 leather, 1 redstone -> 1 | 48.2 | = |  | 40 | 1.21 | ok |  |
| `tree_stand` | mid | 6 iron_ingot, 2 ladder, 1 fh:canvas_wall -> 1 | 64.95 | = |  | 50 | 1.3 | ok |  |
| `two_power_prism` | mid | 3 iron_ingot, 1 glass, 1 leather -> 1 | 35.2 | = |  | 38 | 0.93 | ok |  |
| `woodland_camp_tent` | mid | 3 fh:canvas_wall, 3 string, 2 iron_ingot, 1 leather -> 1 | 41.25 | = |  | 40 | 1.03 | ok |  |
| `atv` | late | 4 dried_kelp_block, 2 iron_ingot, 1 blast_furnace, 1 chest, 1 leather -> 1 | 95.0 | **4 dried_kelp_block, 1 blast_furnace, 1 iron_block, 1 leather, 1 piston -> 1** `KLK/BFP/K K` | 176.7 | 180 | 0.98 | was TOO CHEAP → ok | engine (blast furnace + piston), iron-block frame, tyres, seat |
| `clothing_table/bear_fur_coat` | late | 4 fh:bear_fur, 3 fh:tanned_hide, 1 string -> 1 (Clothing Table) | 106.0 | = |  | 100 | 1.06 | ok |  |
| `clothing_table/hide_robe` | late | 4 fh:tanned_heavy_hide, 1 #fh:tanned_furs, 1 string -> 1 (Clothing Table) | 70.0 | = |  | 70 | 1.0 | ok |  |
| `expedition_eight_power_scope` | late | 2 glass, 1 copper_ingot, 1 iron_ingot -> 1 | 15.4 | **3 iron_ingot, 2 amethyst_shard, 1 glass, 1 gold_ingot -> 1** ` U /GIA/IAI` | 63.2 | 75 | 0.84 | was TOO CHEAP → ok | + gold turret, second amethyst lens |
| `expedition_field_pistol` | late | 2 iron_ingot, 1 gold_ingot, 1 stick, 1 string -> 1 | 42.25 | **4 iron_ingot, 1 gold_ingot, 1 piston, 1 tripwire_hook -> 1** `III/GHK/ I ` | 81.1 | 100 | 0.81 | was TOO CHEAP → ok | steel slide on a recoil spring (piston), match trigger (gold) |
| `expedition_semi_auto_rifle` | late | 2 iron_ingot, 1 redstone, 1 stick, 1 string -> 1 | 25.25 | **2 #planks, 2 iron_block, 1 iron_ingot, 1 piston, 1 repeater, 1 tripwire_hook -> 1** `IBB/PKR/PH ` | 217.6 | 230 | 0.95 | was TOO CHEAP → ok | automatic: barrel + receiver (2 iron blocks), gas piston, repeater timing |
| `expedition_semi_auto_shotgun` | late | 2 iron_ingot, 1 repeater, 1 stick, 1 string -> 1 | 27.75 | **2 #planks, 1 gold_ingot, 1 iron_block, 1 iron_ingot, 1 piston, 1 tripwire_hook -> 1** `G B/PKI/PH ` | 142.1 | 175 | 0.81 | was TOO CHEAP → ok | gas system (piston), gold-plated action |
| `expedition_twelve_power_scope` | late | 2 glass, 1 copper_ingot, 1 iron_ingot -> 1 | 15.4 | **3 iron_ingot, 2 amethyst_shard, 1 diamond, 1 glass, 1 gold_ingot -> 1** `DU /GIA/IAI` | 123.2 | 130 | 0.95 | was TOO CHEAP → ok | + diamond-ground lens |
| `field_spotlight` | late | 3 iron_ingot, 2 copper_ingot, 1 glass_pane, 1 redstone_lamp -> 1 | 88.45 | = |  | 60 | 1.47 | ok |  |
| `glow_lure` | late | 1 feather, 1 glowstone_dust, 1 iron_nugget, 1 string -> 1 | 14.6 | = |  | 15 | 0.97 | ok |  |
| `night_vision_binoculars` | late | 4 iron_ingot, 2 glass, 1 fh:field_battery_pack, 1 leather, 1 redstone -> 1 | 57.45 | **2 glass, 2 gold_ingot, 2 iron_ingot, 1 fh:field_battery_pack, 1 leather, 1 redstone -> 1** `ARA/GBG/ILI` | 77.45 | 90 | 0.86 | ok | image intensifier: gold |
| `tracer_broadhead` | late | 4 fh:fixed_broadhead, 1 glowstone_dust, 1 redstone -> 4 | 6.03 | = |  | 6 | 1.0 | ok |  |
| `tracer_field_point` | late | 4 fh:field_point, 1 glowstone_dust, 1 lime_dye -> 4 | 3.55 | = |  | 3.5 | 1.01 | ok |  |
| `tracer_ice_broadhead` | late | 4 fh:cut_on_contact_broadhead, 1 glowstone_dust, 1 light_blue_dye -> 4 | 5.9 | = |  | 6 | 0.98 | ok |  |
| `wingsuit` | late | 2 orange_wool, 2 phantom_membrane, 2 string, 1 leather, 1 white_wool -> 1 | 56.0 | **4 phantom_membrane, 2 orange_wool, 1 leather, 1 string -> 1** `OLO/PTP/P P` | 92.0 | 110 | 0.84 | was TOO CHEAP → ok | elytra-class glider: four phantom membranes |
| `expedition_thermal_binoculars` | endgame | 2 glass, 1 copper_ingot, 1 iron_ingot, 1 redstone -> 1 | 18.4 | **2 gold_ingot, 2 redstone, 1 fh:binoculars, 1 fh:field_battery_pack, 1 diamond -> 1** `ADA/RBR/ K ` | 136.45 | 150 | 0.91 | was TOO CHEAP → ok | binoculars + diamond sensor, gold, redstone, battery |
| `expedition_thermal_scope` | endgame | 2 glass, 1 copper_ingot, 1 iron_ingot, 1 redstone -> 1 | 18.4 | **2 gold_ingot, 2 redstone, 1 fh:field_battery_pack, 1 amethyst_shard, 1 diamond, 1 glass, 1 iron_ingot -> 1** `RKR/GDA/UIU` | 131.25 | 160 | 0.82 | was TOO CHEAP → ok | thermal sensor (diamond), gold, battery |
| `big_buck_board` | decor | 4 #logs, 3 #planks, 1 copper_ingot -> 1 | 12.5 | = |  | 12 | 1.04 | ok |  |
| `bow_stand` | decor | 1 #c:bones, 1 #c:rods/wooden, 1 #wooden_slabs -> 1 | 2.0 | = |  | 2 | 1.0 | ok |  |
| `gun_rack` | decor | 5 #planks, 2 iron_ingot -> 1 | 22.5 | = |  | 8 | 2.81 | ok |  |
| `lit_lodge_table` | decor | 1 fh:cabin_lantern, 1 fh:lodge_table -> 1 | 6.2 | = |  | 9 | 0.69 | ok |  |
| `reserve_cabin_lantern` | decor | 2 glass_pane, 2 iron_nugget, 1 torch -> 1 | 3.7 | = |  | 6 | 0.62 | ok |  |
| `reserve_lodge_chair` | decor | 3 fh:pine_planks, 2 stick -> 1 | 2.0 | = |  | 2 | 1.0 | ok |  |
| `reserve_lodge_stove` | decor | 7 iron_ingot, 1 furnace -> 1 | 72.4 | = |  | 60 | 1.21 | ok |  |
| `reserve_lodge_table` | decor | 4 stick, 3 fh:pine_planks -> 1 | 2.5 | = |  | 2.5 | 1.0 | ok |  |
| `reserve_trail_sign` | decor | 3 fh:pine_planks, 2 stick -> 1 | 2.0 | **4 fh:pine_planks, 1 stick -> 1** ` P /PPP/ S ` | 2.25 | 2 | 1.12 | ok | was the wooden pickaxe pattern |
| `trophy_plinth` | decor | 4 #planks, 3 #wooden_slabs -> 1 | 2.75 | = |  | 3 | 0.92 | ok |  |
| `alpine_pasture` | block | 2 short_grass, 1 fern -> 3 | 0.23 | = |  | 0.2 | 1.17 | ok |  |
| `fallen_branch` | block | 3 stick -> 2 | 0.38 | = |  | 0.4 | 0.94 | ok |  |
| `lookout_brace` | block | 3 dark_oak_planks -> 6 | 0.25 | **3 dark_oak_slab -> 6** | 0.12 | 0.25 | 0.5 | ok | Timber set from dark oak slabs: the Lookout set (Frontier Structures) uses dark oak planks |
| `lookout_cross_brace` | block | 5 dark_oak_planks -> 6 | 0.42 | **5 dark_oak_slab -> 6** | 0.21 | 0.4 | 0.52 | ok |  |
| `lookout_deck_rail` | block | 4 stick, 3 dark_oak_planks -> 6 | 0.42 | **4 stick, 3 dark_oak_slab -> 6** | 0.29 | 0.4 | 0.73 | ok |  |
| `lookout_stair_rail` | block | 3 stick, 2 dark_oak_planks -> 6 | 0.29 | **3 stick, 2 dark_oak_slab -> 6** | 0.21 | 0.3 | 0.69 | ok |  |
| `reserve_cabin_door` | block | 6 fh:pine_planks -> 3 | 1.0 | = |  | 1 | 1.0 | ok |  |
| `reserve_fieldstone` | block | 1 cobblestone, 1 gravel -> 2 | 0.4 | = |  | 0.4 | 1.0 | ok |  |
| `reserve_fieldstone_stairs` | block | 6 fh:fieldstone -> 4 | 0.6 | = |  | 0.6 | 1.0 | ok |  |
| `reserve_forest_loam` | block | 1 dirt, 1 oak_leaves -> 2 | 0.2 | = |  | 0.2 | 1.0 | ok |  |
| `reserve_moss_floor` | block | 1 dirt, 1 moss_block -> 2 | 1.05 | = |  | 1 | 1.05 | ok |  |
| `reserve_pine_fence` | block | 4 fh:pine_planks, 2 stick -> 3 | 0.83 | = |  | 1 | 0.83 | ok |  |
| `reserve_pine_slab` | block | 3 fh:pine_planks -> 6 | 0.25 | = |  | 0.25 | 1.0 | ok |  |
| `reserve_pine_stairs` | block | 6 fh:pine_planks -> 4 | 0.75 | = |  | 0.75 | 1.0 | ok |  |
| `reserve_ranger_window` | block | 8 stick, 1 glass_pane -> 4 | 0.61 | = |  | 0.6 | 1.02 | ok |  |
| `reserve_reserve_granite` | block | 1 granite -> 1 | 0.5 | = |  | 0.5 | 1.0 | ok |  |
| `reserve_roof_gable_high` | block | 3 fh:pine_planks, 3 fh:roof_shingles -> 6 | 0.66 | = |  | 1 | 0.66 | ok |  |
| `reserve_roof_gable_low` | block | 3 fh:pine_planks, 3 fh:roof_shingles -> 6 | 0.66 | **3 fh:pine_planks, 3 fh:roof_shingles -> 6** `SSS/P P/ P ` | 0.66 | 1 | 0.66 | ok | six roof shapes shared one pattern |
| `reserve_roof_ridge_high` | block | 3 fh:pine_planks, 3 fh:roof_shingles -> 6 | 0.66 | **3 fh:pine_planks, 3 fh:roof_shingles -> 6** ` S /SPS/P P` | 0.66 | 1 | 0.66 | ok |  |
| `reserve_roof_ridge_low` | block | 3 fh:pine_planks, 3 fh:roof_shingles -> 6 | 0.66 | **3 fh:pine_planks, 3 fh:roof_shingles -> 6** `S S/ S /PPP` | 0.66 | 1 | 0.66 | ok |  |
| `reserve_roof_shingles` | block | 6 iron_nugget -> 8 | 0.83 | = |  | 0.8 | 1.03 | ok |  |
| `reserve_roof_slab` | block | 3 fh:roof_shingles -> 6 | 0.41 | = |  | 1 | 0.41 | ok |  |
| `reserve_roof_slope_high` | block | 3 fh:pine_planks, 3 fh:roof_shingles -> 6 | 0.66 | **3 fh:pine_planks, 3 fh:roof_shingles -> 6** `S  /SP /SPP` | 0.66 | 1 | 0.66 | ok |  |
| `reserve_roof_slope_low` | block | 3 fh:pine_planks, 3 fh:roof_shingles -> 6 | 0.66 | **3 fh:pine_planks, 3 fh:roof_shingles -> 6** `  S/ SS/PPP` | 0.66 | 1 | 0.66 | ok |  |
| `reserve_roof_stairs` | block | 6 fh:roof_shingles -> 4 | 1.24 | = |  | 1 | 1.24 | ok |  |
| `reserve_rope_ladder` | block | 4 string, 3 stick -> 4 | 2.19 | = |  | 2.2 | 0.99 | ok |  |
| `reserve_stacked_firewood` | block | 3 #logs -> 4 | 1.5 | = |  | 1.5 | 1.0 | ok |  |
| `reserve_stove_flue` | block | 6 iron_nugget -> 4 | 1.65 | = |  | 1.6 | 1.03 | ok |  |
| `reserve_tent_gable` | block | 3 fh:canvas_wall, 1 stick -> 6 | 1.92 | **3 fh:canvas_wall, 1 stick -> 6** ` C /C C/ S ` | 1.92 | 0.7 | 2.74 | ok |  |
| `reserve_tent_ridge` | block | 3 fh:canvas_wall, 1 stick -> 6 | 1.92 | **3 fh:canvas_wall, 1 stick -> 6** ` C /CSC` | 1.92 | 0.7 | 2.74 | ok | three tent shapes shared one pattern |
| `reserve_tent_slope` | block | 3 fh:canvas_wall, 1 stick -> 6 | 1.92 | = |  | 0.7 | 2.74 | ok |  |
| `reserve_weathered_planks` | block | 2 spruce_planks, 1 gray_dye -> 2 | 1.25 | = |  | 1 | 1.25 | ok |  |
| `stove_roof_flashing` | block | 1 fh:stove_flue, 1 iron_ingot -> 1 | 11.65 | = |  | 12 | 0.97 | ok |  |
| `tent_end_ridge` | block | 1 fh:canvas_wall, 1 fh:tent_ridge -> 1 | 5.67 | = |  | 5 | 1.13 | ok |  |
| `timber_brace` | block | 3 #planks -> 4 | 0.38 | **2 #planks, 1 stick -> 4** `  P/ S /P  ` | 0.31 | 0.4 | 0.78 | ok | was the Lookout Timber Brace pattern (dark oak planks) |
| `timber_cross_brace` | block | 2 fh:timber_brace -> 1 | 0.75 | = |  | 0.8 | 0.78 | ok |  |
| `timber_stair_guard` | block | 2 fh:timber_brace, 1 #planks -> 1 | 1.25 | = |  | 1.3 | 0.87 | ok |  |
| `dark_oak_lattice_window` | block | 4 dark_oak_planks, 4 stick, 1 glass_pane -> 4 | 0.86 | = |  | 0.6 | 1.44 | ok |  |
| `lookout_brace` | block | 3 dark_oak_planks -> 6 | 0.25 | = |  | 0.25 | 1.0 | ok |  |
| `lookout_cross_brace` | block | 5 dark_oak_planks -> 6 | 0.42 | = |  | 0.4 | 1.04 | ok |  |
| `lookout_deck_rail` | block | 4 stick, 3 dark_oak_planks -> 6 | 0.42 | = |  | 0.4 | 1.04 | ok |  |
| `lookout_stair_rail` | block | 3 stick, 2 dark_oak_planks -> 6 | 0.29 | = |  | 0.3 | 0.97 | ok |  |
| `spruce_casement_window` | block | 4 spruce_planks, 4 stick, 1 glass_pane -> 4 | 0.86 | = |  | 0.6 | 1.44 | ok |  |
| `spruce_picture_window` | block | 8 spruce_planks, 1 glass_pane -> 4 | 1.11 | = |  | 1.1 | 1.01 | ok |  |
