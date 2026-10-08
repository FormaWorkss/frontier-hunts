# Workstream `trees2`: breaking realistic trees, vanilla wood drops, clean bases (branch `trees2`)

> **Superseded in part by `docs/ws/wood.md`:** mod logs now drop themselves (not vanilla logs); see that doc for wood drops, tags and recipes.

User report (Ultra): "trees turn blocky when u break them … they need to stay the same even when broke and then those
blocks u get should be normal blocky blocks … usable for crafting … like regular mc wood – also the trees still have
some weird bulky spots … some are like 2 blocks deep and weird looking bases". Code is marked `// [trees2]`.
Only Ultra draws round trees (`worldLook = REALISTIC`); Classic and Balanced use block trees and are not affected.
Nothing here was run in game. Offline checks are listed below.

## 1. Root causes

1. **Blocky after breaking.** A cached tree is checked against the world on every section rebuild
   (`TreeGrowth.valid`). Any changed member (a log or leaf broken, or a leaf that decayed) dropped the tree, and the
   tree was grown again from what was left. Without the rooted foot log there were no `bottoms`. A broken trunk log
   split the log component, and a part could have fewer than 6 leaves. In both cases the blueprint was rejected, a
   negative cache entry was stored for the whole component, and **every log and leaf of the tree fell back to cube
   models**. As other sections of the tree were rebuilt (leaf decay, LOD), more of the tree turned blocky. Each break
   also cost a full growth (5–7 ms) inside the player's important section rebuild. Harness, without the fix: 1,994
   breaks gave 1,994 regrowths, and 262 of those turned the tree into cubes.
2. **Round item icons.** On Ultra the realistic pack replaces the log *block* models (vanilla and mod logs) with round
   prisms. The item models use those block models as their parent, so log items in the hand, inventory and on the
   ground were round.
3. **Wood not craftable.** The mod's natural trees use 10 mod log blocks: `pine/cedar/aspen/birch_log` and
   `alpine_spruce/pine/maple/alder/rowan/cottonwood_log`. They dropped themselves.
   - `aspen/birch/pine/cedar_log` were not in the item tag `#minecraft:logs`. Only 3 of the 10 were in
     `#logs_that_burn`.
   - Only pine and cedar had a planks recipe, so no crafting table, no charcoal from most of them, and no stripping.
4. **Bases 1–2 blocks "deep" / weird.**
   - **Bark curtain on slopes.** `trunk()` dragged the lowest ring's downhill vertices to the lower ground, so a sheet
     of bark hung up to **1.12 blocks** down the face of the step under every tree on a slope (`TreeLook report`:
     skirt 1.12 on every slope fixture).
   - **Sunk on the uphill side.** The flare was measured from the foot block only. Where the soil was higher (uphill,
     or a trunk sitting in a hollow), the trunk went into the soil like a post. In a hollow, a square pit of the foot
     block showed around the trunk.
   - **Bulb.** The flare was 1.55× over 0.45 blocks (a base about 1.6 blocks across under a single log, 3.1 under a
     2×2 trunk), and there was a ±8% / ±3% bark-ridge step at 1 block that left a ledge in every ridge.
   - **Root tubes.** Four or five of them sat mostly hidden inside the flare.
   - **Knob on broadleaf trunks.** The broadleaf trunk tip ring (0.02) was wider than the ring below it, so a knob
     floated above every broadleaf trunk.

## 2. Fixes

- **`client/tree/TreeScars` (new).** A member of a cached tree that turns into air is recorded as a *ghost* (its kind,
  axis, species, conifer flag and crown form). The tree stays valid and nothing is regrown. The broken block draws
  nothing, and every other block keeps its baked share, so the tree stays exactly as it was (floating parts too, like
  vanilla). Any later growth (eviction, shader switch with released quads, LOD regrowth) reads the world through
  `TreeScars.over()`, so the same tree grows again. Details:
  - Light changes in the 4 s after a break are accepted without a regrowth.
  - Once a tree's last log is gone (felled), its ghosts stop shaping growths, so a sapling replanted and bonemealed
    at the stump grows its own tree. A log set where a felled trunk was is a new tree.
  - `sweep()` (from `TreeLod.tick`, 2,048 block reads per second at most) forgets ghosts under real blocks and drops
    the scars of trees with nothing left, or felled more than 10 minutes ago.
  - Capped at 120k ghosts. The F3 tree line shows the count.
- **Remnant fallback.** If a tree is regrown without ghosts (a new session), a floating remnant is grown as cut stems
  (no flare, starting at its lowest upright log). This needs a natural crown: at least 6 non-persistent leaves, and at
  least 80% of its leaves non-persistent (`World.natural`).
- **Ground-following foot (`TreeGrowth.Foot`).** It samples the soil in 32 directions around each stem:
  - *lift*: the soil beyond the foot's own columns. Where it is higher, the trunk fills its column up to the soil (no
    pit) and flares from there.
  - *cap*: where the ground drops, the flare stops at the foot block's edge. No more bark curtain.
  - The flare is now 1.38× (1.22× for wide trunks) over 0.4 blocks, plus 5 or 7 buttress lobes (gentler on young
    trees).
  - Bark ridges change smoothly with height.
  - The root tubes are removed and the tip knob is fixed.
  - `SignTrunkProbe.flare` (rub decals) and `StandTrunk.radius` mirror the new flare.
- **Drops and crafting (`tools/trees2/gen_wood_data.py` generates the data).**
  - **Log loot tables.** Each mod log drops its vanilla equivalent:
    - pine, alpine spruce and alpine pine → `spruce_log`
    - cedar → `dark_oak_log`
    - aspen, birch and cottonwood → `birch_log`
    - maple, alder and rowan → `oak_log`

    Silk Touch keeps the mod log.
  - **Leaves loot tables (14).** Shears or Silk Touch drop the leaves, as before. Otherwise they drop the vanilla
    sapling (spruce, birch or oak) with vanilla Fortune chances, and sticks with vanilla chances.
  - **Item tag fragments.** All 10 mod logs are added to `#minecraft:logs` and `#minecraft:logs_that_burn`. The
    species tags are `#oak_logs` (maple, alder, rowan), `#spruce_logs` (alpine spruce and pine) and `#birch_logs`
    (aspen, birch, cottonwood), so vanilla plank recipes, fuel, charcoal and the campfire all work. Pine and cedar keep
    their own plank recipes and are left out of the species tags, so recipes don't conflict.
  - **`timber/WildLogStripping` (new, common).** An axe strips a mod log into the matching vanilla stripped log and
    keeps its axis.
  - **Realistic pack item models.** The 8 vanilla and 10 mod round logs are drawn as `cube_column` items, with the
    same textures a placed building log uses on Ultra.
  - **`round_blocks.txt`.** The 8 vanilla stripped logs are added, so a log stripped inside a living tree keeps
    drawing its round share instead of showing a cube in the trunk. A placed stripped log still draws as a plain block.
  - Every tree-related block has a loot table (audited).

## 3. Files
- New:
  - `client/tree/TreeScars.java`
  - `timber/WildLogStripping.java`
  - `tools/trees2/TreeLook.java` (harness)
  - `tools/trees2/tree_preview.py` (renderer)
  - `tools/trees2/gen_wood_data.py`
  - `docs/ws/trees2/*.png`
- Patch data:
  - `patch/data/frontierhunts/loot_table/blocks/` (24 files)
  - `patch/_merge/data/minecraft/tags/item/{logs,logs_that_burn,oak_logs,spruce_logs,birch_logs}.json/trees2.json`
  - `patch/resourcepacks/realistic_world/assets/{minecraft,frontierhunts}/models/item/*_log.json`
  - `patch/resourcepacks/realistic_world/assets/frontierhunts/realistic_world/round_blocks.txt` (full override of
    the base file, plus 8 stripped logs)
- Edited:
  - `TreeGrowth` (scars, valid, foot, remnant, tip)
  - `LiveWorld.natural`
  - `TreeLod`: one sweep block in `tick()` and a scar count in `debugLine()`
  - `SignTrunkProbe.flare`
  - `expedition/StandTrunk` (one line: the flare mirror)
- No lang, sound, mixin or settings changes.

## 4. Offline checks
- `tools/compile.sh` → exit=0.
- `check_jar --base master` → 0 errors, 0 warnings.
- `TreeLook chop`:
  - 101 trees chopped log by log, then leaf by leaf (1,994 breaks): the cached tree was kept every time, with
    **0 regrowths and 0 cube fallbacks**.
  - Forced eviction: the tree regrows identically for every block still standing (93/93; 8 trees were felled
    completely).
  - New session: the floating remnant grows as cut stems in 85/93 cases.
  - Replant + bonemeal at a felled stump: 56/56 grow their own tree.
- `TreeLook report` (259 placements: 14 species × 4 ages × flat / 1:2 / 1:1 / diagonal slope / hollow):
  - Bark hanging below the foot over a drop: max **0.30** (the lowest ring under a leaning stem), down from 1.12.
  - Mean NEAR quads: 6,158 → 6,187. The hollow fixtures were added after the first count, so this is not like for
    like. On the bench's 56 trees: 6,288 → 6,224.
- `TreeLodBench`:
  - NEAR 6,288 → 6,224 quads per tree, FAR 1,733 → 1,708, impostor unchanged (16).
  - `unsafe=0`.
  - Growth 7.06 → about 6.0 ms per tree.
- Previews (looked at): `docs/ws/trees2/before_bases_slope.png` and `after_bases_slope.png` (side, downhill, uphill
  and from above), `after_bases_pit_slope.png`, `after_all_species_slope.png`.

Run:
```
bash /home/claude/fh/tools/compile.sh REPO /tmp/cc
javac -proc:none -d /tmp/tl -cp /tmp/cc:/home/claude/fh/orig62.jar REPO/tools/perf/TreeLodBench.java REPO/tools/trees2/TreeLook.java
java -cp /tmp/tl:/tmp/cc:/home/claude/fh/orig62.jar com.formaworks.frontierhunts.client.tree.TreeLook report|chop|chop noghosts|dump FILE REGEX [far]
python3 REPO/tools/trees2/tree_preview.py FILE <realistic pack textures/block> out.png base|tree|wood|trunk
```

## 5. IN-GAME TEST SCRIPT (coordinator)
1. Ultra preset, Sodium + Iris with a shader pack, survival world. Go to an aspen grove and a pine/spruce forest.
   Press F3 (the Frontier trees line).
2. Punch the **bottom log** of an aspen. Only that trunk segment disappears. The rest of the tree stays round and
   unchanged: no flicker, no cube logs or leaves. F3 shows `1 broken-out blocks remembered`. Chop the trunk upward log
   by log: each step removes only that segment (and branches hosted there). The crown floats like vanilla, and the
   leaves decay one by one as cards. Repeat on a pine and a 2×2 tree. Watch FPS: there should be no hitch per break.
3. Break single leaves of a standing tree: only those leaf sprays vanish. Toggle the shader pack off and on: the
   chopped trees keep their shape (regrow path).
4. Inventory: the drops are **vanilla** logs (aspen → Birch Log, pine/spruce → Spruce Log, maple/alder/rowan → Oak
   Log, cedar → Dark Oak Log). They look like normal cube logs in hand, inventory, item frames and on the ground (also
   vanilla oak/spruce logs on Ultra). Craft planks, then a crafting table, sticks and a wooden pickaxe. Smelt a log
   into charcoal and burn a log as fuel.
5. Silk Touch axe on an aspen log: the mod's Aspen Log drops. It also crafts birch planks, burns, and smelts to
   charcoal. Right-click it with an axe: Stripped Birch Log. Strip a log inside a living tree: the trunk stays round.
6. Leaves without shears: an occasional vanilla sapling (spruce/birch/oak) and rare sticks. With shears or Silk Touch:
   the leaves block.
7. Fell a whole tree, plant the dropped sapling at the stump and bonemeal it: the new tree is a normal young tree,
   not a copy of the old one.
8. **Bases:** walk hills and steep alpine slopes. No bark hangs down the face of the block under a trunk. Downhill,
   the trunk stands on its block. Uphill, it flares into the soil. A trunk in a 1-block hollow fills its hole (no
   square pit). Look at old wild trees (buttress logs) and 2×2 trunks: modest lobed flares, no bulbs. There is no
   knob above broadleaf trunk tips.
9. Rejoin the world after chopping half a tree: the floating remnant is still round (cut stems), not cubes.
10. Deer rubs (`/frontierhunts sign rub` at a round trunk) still wrap the trunk without sinking into the bark. A tree
    stand placed on a round trunk still fits.

## 6. Notes / limits
- Ghosts last one client session. After a rejoin, a remnant without a natural crown, or a lone stump, is a block
  (as before, like world-gen stumps and snags).
- A block placed into a broken trunk cell (dirt, etc.) makes the tree grow again from what is there.
- Dropping vanilla logs also applies to the reserve's pine/cedar cabin logs. Silk Touch, or the existing spruce→pine
  and dark oak→cedar recipes, give them back.
