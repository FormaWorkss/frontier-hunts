# Workstream `wood`: every tree gives its own log, and mod logs work like vanilla wood (branch `wood`)

User decision (replaces trees2's "mod logs drop vanilla logs"): *"the block needs to stay the same type as the tree after
breaking and the whole tree gives the same block, don't make it confusing with different blocks; also there needs to
be a way to get regular MC wood like birch oak spruce"*. Code is marked `// [wood]`. Nothing here was run in game.

## 1. What changed

**One log per tree.** I checked every tree generator.
- `AlpineTrees` and `HuntingForest` already build each tree from one log block.
- `WildTrees` (forest stands, alpine ecology, Ranger Academy plots, cascades) built **maples and willows out of
  aspen logs**. They now use the mod's maple log (`alpine_maple_log`), as the alpine generator does
  (`environment/WildTrees`, see §3).
- Snags, leaners and stumps already use one log each.
- So every log block of any tree drops the same item.

**Drops.**
- **Logs.** All 10 mod logs drop **themselves** (vanilla log loot, no Silk Touch needed). Examples: cedar tree →
  Cedar Log, aspen → Aspen Log, lodgepole → Lodgepole Pine Log, maple → Bigleaf Maple Log.
- **Leaves.** The mod has no saplings, so leaves drop the **vanilla sapling of the tree's wood family**:
  - conifers → spruce sapling
  - aspen / birch / cottonwood → birch sapling
  - maple / alder / rowan / willow → oak sapling

  This uses vanilla oak-leaves chances (5 % / Fortune). Sticks drop at vanilla chances. Shears or Silk Touch keep
  the leaves.
- **Left as is, on purpose:**
  - `deadfall_log`: the half-height rotten tail of a fallen log. It is not a full log block, has no species, and
    breaks into 2–4 sticks.
  - `branch_stub` and `sapling_pole`: twigs that break into a stick.

**Vanilla wood.** Each mod log belongs to a vanilla wood family:

| Mod logs | Vanilla family |
|---|---|
| pine, mountain spruce, lodgepole pine | spruce |
| cedar | dark oak |
| aspen, paper birch, cottonwood | birch |
| bigleaf maple, alder, rowan | oak |

- **Tags (fragments).** Each mod log is in `#minecraft:logs`, in `#minecraft:logs_that_burn` (fuel, charcoal,
  campfire), and in `#minecraft:<family>_logs`. The vanilla planks recipe therefore takes it: **1 mod log → 4 vanilla
  planks**, and then every vanilla recipe works (tables, sticks, tools, boats, doors). Each log also has its own species
  tag, `#frontierhunts:<species>_logs` (cedar_logs, aspen_logs, paper_birch_logs, maple_logs, …).
- **Conversion recipes.** `frontierhunts:<log>_to_vanilla_<family>_log`: **2 mod logs → 2 vanilla logs**, shapeless.
  This is still 1:1. It uses 2 logs because a 1-log recipe would match the same grid as the 1 log → 4 planks recipe,
  and the two would conflict. Recipe-book unlocks are generated.
- **Axe stripping** (`timber/WildLogStripping`, unchanged) gives the family's stripped vanilla log. The mod has no
  stripped logs.
- **Pine and cedar planks** are real blocks that the reserve builds with: stairs, slabs, fences, roofs, door,
  furniture. **Decision: keep them, but make them in the stonecutter** (1 pine/cedar log → 4). The recipe ids stay
  `reserve_pine_planks` and `reserve_cedar_planks`. This way the crafting grid only ever turns a log into vanilla
  planks.
- **Removed recipes** (`patch/_remove/wood.txt` and their unlocks): `reserve_pine_log` (1 spruce log → 1 pine log) and
  `reserve_cedar_log` (dark oak → cedar). They fought the vanilla planks recipe for the same single log, and trees now
  drop pine and cedar logs anyway.
- **Rename.** The mod's `birch_log` and `birch_leaves` were named exactly like vanilla's ("Birch Log"). They are now
  **Paper Birch Log / Paper Birch Leaves** (lang fragment), so the two items are never confused.

**Vanilla trees.**
- The default world (alpine preset) generates only mod trees. The Reserve preset (vanilla noise and biomes) still has
  every vanilla tree.
- Rule:
  - **oak, birch and spruce saplings** drop from mod leaves.
  - **jungle, acacia, dark oak, cherry saplings and mangrove propagules** come from the vanilla wandering trader, which
    the mod does not touch.
- Vanilla saplings grow normally: no mod code touches saplings or tree features, and grass is in `#dirt`.
- On Ultra they draw round: the realistic pack lists all 8 vanilla logs, their stripped logs and their leaves, and
  TreeLook's oak and cherry fixtures pass.

**Unchanged from trees2:** TreeScars ghosts (no blocky fallback), cube log items on Ultra, flare and foot.

## 2. Ultra look change (WildTrees maples and willows only)
The crown form is chosen by log id: aspen and birch logs give the slender form 1. Maples built from aspen logs drew
that form. With maple logs they now draw the broad form 0 that all alpine maples use, and they have maple bark.
- `TreeLook report`: wild_maple wood quads 936 → 1,168. The mean NEAR over all 259 fixtures is 6,187 → 6,222
  (+0.6 %). Every other fixture is unchanged.
- Preview: `docs/ws/wood/wild_maple_before_after.png`.

## 3. Files
- **New:**
  - `src/.../environment/WildTrees.java`: the decompiled class. Its recompiled bytecode matches the original's
    call, constant and arithmetic sequence exactly (771/771), and only the branch layout differs. Edits: the
    `woodPalette` default falls back to the block registry for ids the reserve does not register, and the
    `Broadleaf.MAPLE/WILLOW` log is now `alpine_maple_log`.
  - Generated by `tools/trees2/gen_wood_data.py` (rewritten):
    - `patch/data/frontierhunts/recipe/*_to_vanilla_*_log.json` (10)
    - `patch/data/frontierhunts/tags/item/*_logs.json` (10)
    - `patch/_merge/data/minecraft/tags/item/{logs,logs_that_burn,oak_logs,spruce_logs,birch_logs,dark_oak_logs}.json/wood.json`
      (replace trees2's fragments)
    - `patch/_remove/wood.txt`
  - Generated by `tools/recipes/gen_unlocks.py`: 10 unlock advancements.
  - `patch/_merge/assets/frontierhunts/lang/en_us.json/wood.json`
- **Regenerated:**
  - 24 loot tables
  - `reserve_pine_planks.json` and `reserve_cedar_planks.json` (now stonecutting)
- **Deleted:** the unlocks for `reserve_pine_log` and `reserve_cedar_log`.
- **Edited:**
  - `timber/WildLogStripping` (comment only)
  - `tools/recipe_audit.py`: `cedar_log` added as a WildTrees worldgen block. It was reachable only through the
    removed recipe before.
  - `tools/perf/TreeLodBench.java` and `tools/trees2/TreeLook.java`: the wild_maple port uses the maple log.
- No settings, mixins, client code, sounds or textures changed.

## 4. Checks
- `compile.sh` → exit=0.
- `build.py` → `check_jar --base master` → 0 errors, 0 warnings.
- `recipe_audit.py` → **PASS** (0 unobtainable, 0 errors; 256 recipes, all with unlocks).
- Conflict scan: no crafting recipe in the jar other than the 10 conversions takes 1 or 2 logs. Vanilla has none
  either; its planks recipes take 1 log through a tag.
- `TreeLook chop`: identical to master (1,994 breaks, 0 regrowths, 0 cubes; replant 56/56).
- `TreeLook report`: 259 trees, missing 0.

## 5. IN-GAME TEST SCRIPT
1. New survival world, default (alpine) preset, Vanilla graphics. Go to an aspen grove, a cedar/pine stand, a maple
   wood and a spruce forest (`/locate biome frontierhunts:aspen_woodland`, `cedar_valley`, `maple_woodland`,
   `pine_highlands`).
2. Fell a whole tree of each kind with a plain axe. Check every log block of the tree: trunk, branches and foot logs
   all drop the same item (Aspen Log, Cedar Log or Lodgepole Pine Log, Bigleaf Maple Log, Mountain Spruce Log). Look
   for a maple with paler, aspen-like bark (an old WildTrees maple): new chunks should have none. Its logs give Bigleaf
   Maple Log.
3. Hover a mod birch log: it reads "Paper Birch Log". A vanilla birch log still reads "Birch Log".
4. Put 1 Cedar Log in the grid → 4 Dark Oak Planks. Put 1 Aspen Log → 4 Birch Planks. Put 1 Alder Log → 4 Oak Planks.
   Put 1 Pine Log → 4 Spruce Planks. Then make a crafting table, sticks, a wooden pickaxe, a dark oak boat and a birch
   door.
5. Put 2 Cedar Logs in the grid → 2 Dark Oak Logs (repeat for 2 Aspen Logs → 2 Birch Logs). Recipe book: the
   conversion appears after picking up a mod log.
6. Put a Pine Log in a stonecutter → 4 Pine Planks. Put a Cedar Log in → 4 Cedar Planks. A single Spruce Log in the
   grid gives only Spruce Planks (no Pine Log option any more).
7. Furnace: Aspen Log as fuel. Smelt a Rowan Log → Charcoal. Build a campfire with a Lodgepole Pine Log.
8. Right-click a Cedar Log with an axe → Stripped Dark Oak Log, keeping its axis.
9. Break 40+ mod leaves by hand: conifers give spruce saplings, aspens give birch saplings, maples give oak saplings,
   and you occasionally get sticks. With shears, the leaves themselves drop.
10. Plant the spruce, birch and oak saplings on grass and bonemeal them: vanilla trees grow, and their logs are vanilla
    logs. Switch to the **Ultra** preset: those vanilla trees are round. Chop one log: the tree stays round (TreeScars).
    Log items in hand and inventory are cubes with their bark (mod and vanilla).
11. Find a wandering trader (`/summon minecraft:wandering_trader` for a quick check): the sapling trades are still
    there (jungle/acacia/dark oak/cherry/mangrove when offered). Plant a jungle sapling: it grows.
12. Ultra: look at maples in alpine woodlands. They have broad spreading crowns and maple bark. Chopping still shows
    no blocky fallback.

## 6. Notes
- Trees already generated in existing worlds keep their blocks. Old WildTrees maples stay aspen-barked and still drop
  Aspen Log, which is consistent with their blocks. Only new chunks get maple-log maples.
- Players who hold old Pine/Cedar Logs from the vanilla→mod recipe keep them. Those logs now craft spruce or dark oak
  planks in the grid, and pine or cedar planks in the stonecutter.
