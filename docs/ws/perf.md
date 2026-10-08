# Workstream `perf`: performance (branch `perf`)

The user's priority #1: realistic trees drawn as flat cutouts at distance, fewer animal updates far away,
and a "runs better than you think" mod-page section. Everything here is marked `// [perf]`.

## 1. Tree impostors (third tree LOD)

* **New `client/tree/TreeImpostor.java`** (pure Java). While TreeGrowth grows a tree, every crown foliage
  card is also handed to TreeImpostor, so the impostor is fitted to the real grown crown, not to the
  leaf blocks:
  * **conifers:** 3 crossed vertical planes (60 degrees apart), each made of 2 stacked trapezoid tiers.
    The tiers are fitted to the crown's measured width at each tier edge (99th percentile) and taper to
    the leader. They show the dense middle of the species' fringe sprite.
  * **broadleaves:** 3 crossed planes plus a horizontal top card, using the whole fringe sprite. Its round
    clump gives the crown outline. A crown too tall for one card is sliced, with the sprite sliced across
    the slices so it stays continuous.
  * a 3-sided tapering bark prism per stem (at most 3 stems);
  * bent vertex normals (lit top, shaded base) for no-shader and shader lighting. Foliage light is sampled
    above the crown.
  * Any piece taller or wider than 14 blocks is split, so every quad fits a chunk section (Sodium's
    compact vertex range).
  * Quads are ordinary `TreeShape.Quad`s: texture 2 (foliage) is hosted by the tree's own leaves, and
    texture 0 (bark) by its logs. They go in the same `solid` / `cutoutMipped` layers, with no custom
    render pass.
* **`TreeGrowth`:** adds `LOD_IMPOSTOR = 4` and `LOD_EVERY`, `Tree.impostor` and `impostorAt()`, and
  `growMask()`. Every growth includes the impostor. A tree needed only as an impostor is grown with it
  alone: no tubes and no cards, about 2.4 ms instead of 4.3 ms for far+impostor. The `crown()` / `card()`
  hooks feed the samples. A distant-only quad with no host is dropped (it used to fail the tree). There is
  a new cache bound, `memberBudget = 120k` member blocks, because the quad budget alone no longer bounds
  the tree count.
* **`TreeLod`:** adds `IMPOSTOR = 2`, and `configure(near, impostor, rebuildsPerTick)` sets the bands.
  NEAR keeps its 32/40 hysteresis at Balanced. The impostor band has 8% hysteresis, for example
  88 in / 96 out. `retarget` walks two band edges. The first level of a new subject includes IMPOSTOR.
  `growNeed` returns `LOD_IMPOSTOR` beyond `impOut + 8`. Rebuild ranks: gaining NEAR, then gaining 3D
  over the impostor, then losing detail, within the per-tick budget (4, or 6 at Ultra/Maximum).
  `applyDetail()` follows the config every 20 ticks. `debugLine()` shows the F3 line.
* **`TrunkModel.bakedCell/treeCell`:** `levels = new List[3][]`, and `TreeLod.bits(lod)` handles
  the regrow and level checks. The regrow path is unchanged: float quads are released after baking; a
  shader flip or rebake sees `released` and regrows with the needed level's bits.
* **`ForestFloorModel`:** at the IMPOSTOR level the floor under the canopy is not dressed. That dressing is
  about 1.2 quads per shaded ground block at the FAR level (`tools/perf/FloorQuadSim.java`), about 300 per
  shaded chunk column. That is several times the cutout trees standing on the same column.

### Measured quads per tree (`tools/perf/TreeLodBench.java`, 56 fixture trees: 14 species × 4 ages)

| group | NEAR | FAR | IMPOSTOR (max) | impostor/FAR | impostor/NEAR |
|---|---|---|---|---|---|
| all | 6288 | 1733 | 16 (24) | 0.93% | 0.25% |
| conifer | 10081 | 2763 | 16 (24) | 0.61% | 0.17% |
| broadleaf | 2293 | 646 | 14 (20) | 2.2% | 0.63% |
| birch/aspen | 2644 | 748 | 16 (23) | 2.2% | 0.63% |
| cherry | 2312 | 643 | 11 | 1.7% | 0.48% |

The harness also checks: unsafe (outside section range) = 0, impostor wood on leaves = 0, impostor
foliage on logs = 0, and the impostor-only growth is identical to the impostor level of the full growth.
Visual check: `tools/perf/impostor_preview.py` renders FAR next to IMPOSTOR (side, elevated and 1/6
scale) with the real fringe sprites. The silhouettes match. Impostor crowns are a little denser, which
is what the FAR crown also becomes once it is mipmapped at that range.

Run:
```
bash tools/compile.sh <repo> /tmp/cc
javac -d /tmp/b -cp /tmp/cc:/home/claude/fh/orig62.jar tools/perf/TreeLodBench.java
java -cp /tmp/b:/tmp/cc:/home/claude/fh/orig62.jar com.formaworks.frontierhunts.client.tree.TreeLodBench [v]
java ... TreeLodBench dump quads.txt && python3 tools/perf/impostor_preview.py quads.txt <realistic_world textures/block dir> out.png
```

**Seasons merge note:** the impostor foliage is texture-2 quads baked through `TrunkModel.split` /
`bakeWith` with the tree's `foliageTint`, the same path as every other foliage quad. A seasonal tint or a
bare-winter filter applied there also covers impostors. If seasons instead filters by crown
card inside TreeCrown, it must also skip `this.impostor.card(...)` in `TreeGrowth.card()`, or winter
impostors keep a crown.

## 2. Wildlife AI throttling (server)

* **New `perf/AiThrottle.java`.** It caches each animal's nearest-player distance: re-measured every
  16-23 ticks (staggered by entity id), or every 5 ticks within 32 blocks of the full-rate edge. Tiers:
  FULL < 48, REDUCED < 128, MINIMAL beyond (both configurable). **Only calm animals are throttled.**
  Anything hurt, dying, targeting, in water, riding, downed, bleeding, sedated, alert (≥ 0.02), not
  idle/bedded, answering or approaching a call runs at FULL, from that same tick.
* **Goal selection:** new common mixins `client/mixin/PerfMobAiMixin` (HEAD/RETURN of the final
  `Mob.serverAiStep`) and `PerfGoalSelectorMixin` (HEAD of `GoalSelector.tick`, cancellable). On
  throttled ticks the selection pass is skipped and `tickRunningGoals(true)` runs instead, exactly what
  `tick()` would tick after selecting. The result is selection every 4 ticks (REDUCED) or 8 (MINIMAL)
  instead of 2. Navigation, move, look and jump control are untouched. Both injectors have
  `require = 0`: if another mod reshapes those methods, throttling is simply off.
  Mixin list fragment: `patch/_merge/frontierhunts.client.mixins.json/perf.json`.
* **Perception:** the whitetail's every-5-ticks player scan (reach 72) runs every 20 ticks while a calm
  deer has no player within 80. The wildlife mob's every-10-ticks alarm scan (reach 24) is skipped
  outside the full-rate band, and always runs within 32. These are exact: the scans could not find
  anyone.
* **Not throttled, on purpose:** `Whitetail.tick()` (blood trail, `TrailService`, `TrackClue.leave`
  cadence), `rutWork()` and `AlpineTrackPrints` placement. rutWork is one counter decrement per second
  plus a small scan every 10-23 s for rutting bucks only, so halving it would halve the scrapes and rubs
  hunters find: a gameplay change for almost no saving.
* **`landscape/AlpineTrackPrints`** (copied from dec62g8): cheapest rejections first. For every entity on
  both sides it now checks tick & 3, `isClientSide`, `onGround` and the 4 track-maker types before any
  map lookup or block read. It places the same prints as before.

### Shared-file hook lines
* `hunting/Whitetail.java`:
  `public final com.formaworks.frontierhunts.perf.AiThrottle.State perfAi = new ...State(); // [perf]` (after `private int memory;`)
  and in `customServerAiStep`: `if (this.tickCount % 5 == 0 && com.formaworks.frontierhunts.perf.AiThrottle.perceive(this)) { // [perf]`
* `wildlife2026/WildlifeMob.java`: the same `perfAi` field (after `private Vec3 threat;`) and in `aiStep`:
  `if (this.tickCount % 10 == this.getId() % 10 && com.formaworks.frontierhunts.perf.AiThrottle.alarmScan(this)) { // [perf]`
* `HuntConfig.java`: `PerfConfig.server(var1);` before `SERVER = var1.build();` and
  `PerfConfig.client(var2);` before `CLIENT = var2.build();`

## 3. Client animation and render budget

* **New `perf/client/AnimationLod`** (frame hook, F3 counters) and **`RenderBudget`** (per-frame
  top-N by screen size, one frame late, 10% hysteresis). The effective distance is camera distance
  divided by animal height and zoom. Under 20 the animal is posed every frame. Further out it is posed
  at most 60 / 30 / 20 times per second, and the animator then gets the whole elapsed time, so the gait
  never slows.
* **`client/WhitetailRenderer`** (copied from dec62g8, compiled bytecode is identical before the edit):
  `pose()` keeps the last DeerAnimator pose when not due. This never happens when downed or hurt.
  Scopes count through `ViewProjection.scale()`. The existing skin cache (`notePose`/`poseId`) then
  also skips re-skinning automatically.
* **`wildlife2026/client/WildlifeRenderer`:** a full-mesh budget of 2/4/6/10 animals (by Animal detail
  LOW..ULTRA; the rest use the low mesh) and a full-rate budget of 8/12/16/24. Skinning now goes into a
  per-animal model-space cache (`Skin`), and a far animal re-poses and re-skins only when due. The pose
  transform still runs every frame, so the body turns and moves smoothly. The shadow pass reuses the
  main pass's decisions.

## 4. Config and presets

| key | file | default | meaning |
|---|---|---|---|
| `performance.treeDetail` | `config/frontierhunts-client.toml` | BALANCED | PERFORMANCE 24/64, BALANCED 32/96, ULTRA 48/160, MAXIMUM 64/never (full-detail / cutout distance) |
| `performance.animalAnimationLod` | client | true | reduced pose/skin rate for far animals |
| `performance.wildlifeAiThrottle` | `<world>/serverconfig/frontierhunts-server.toml` | true | AI throttling on/off |
| `performance.wildlifeFullRateDistance` | server | 48 | full rate within |
| `performance.wildlifeMinimalRateDistance` | server | 128 | lowest rate beyond |

`FrontierGraphics.Bundle` gains a last component, `trees`: Classic uses PERFORMANCE, Balanced uses
BALANCED, and Ultra uses ULTRA. `apply()` sets it, `cost()` counts it (only with the realistic world),
and `migrate()` gives existing configs their preset's value, so the preset stays selected. A
`withTrees()` helper was added.

**Settings-screen entry the coordinator should add** (FrontierSettingsScreen is not copied into src, to
avoid an add/add conflict). In `case GRASS:` right after the "World look" choice:
```java
// [perf]
this.choice(
   "Distant trees",
   "Where realistic trees turn into flat cutouts",
   com.formaworks.frontierhunts.perf.PerfConfig.TREE_DETAIL,
   com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.values(),
   FrontierSettingsScreen::title,
   "Realistic world only. PERFORMANCE: full detail to 24 blocks, flat cutouts beyond 64. BALANCED: 32 / 96. ULTRA: 48 / 160. MAXIMUM: 64, never cutouts (heaviest). A cutout tree is about 16 quads instead of 1,700-10,000. Works with any shader pack."
);
```
Optionally, in `case WILDLIFE:` add
`this.toggle("Distant animal animation", "Far animals animate at a lower rate", PerfConfig.ANIMAL_ANIMATION_LOD, "...")`.
`choice()`/`toggle()` already call `FrontierGraphics.changed()`, so the preset turns Custom correctly.

## 5. Mod page
`docs/MOD_PAGE_PERFORMANCE.md`: a "Runs better than you think" section with only measured numbers and no FPS
claims.

## Build
`/home/claude/fh/tools/compile.sh /home/claude/work/perf /tmp/claude-0/cc-perf` gives `exit=0`.
`python3 /home/claude/fh/tools/build.py perf-test /home/claude/work/perf` builds the jar, and the merged
mixin json lists `PerfMobAiMixin` and `PerfGoalSelectorMixin`. Nothing was run in game: the in-game
checks below are still needed.

## IN-GAME TEST SCRIPT
1. Choose the **Ultra** preset (realistic world). Go to a dense forest, for example a taiga with hunting
   forests. Set render distance 16. Press F3. The right side shows
   `Frontier trees+floor: A full, B 3D-far, C cutout (… cutouts beyond 160)`, plus the animal and AI lines.
2. To see the switch clearly, set `treeDetail = "PERFORMANCE"` in `config/frontierhunts-client.toml`
   while in game. The file reloads, and within about 1 s the F3 line says `cutouts beyond 64`. If the file
   is not picked up live, rejoin the world. Stand at a
   forest edge and walk straight out. Trees 64+ blocks away become flat crossed cards and C rises. Walk
   back in: they become 3D again at about 56 blocks, with no flicker when standing still at the boundary.
   Check that the cards show the right species colour and silhouette (conifer cones, round broadleaf
   crowns), a trunk under each, and no floating crowns or holes.
3. Compare FPS in the same spot and view: `treeDetail = "MAXIMUM"` (no cutouts), then `"BALANCED"`, then
   `"PERFORMANCE"`. Write down the FPS and the F3 counts. These are the only FPS numbers that should go on
   the mod page.
4. **Shaders:** with Iris and a shader pack (Photon, Complementary), repeat step 2. Cutout crowns must
   sway with the leaves and trunks must stay still. Toggle the shader pack off and on: trees rebuild
   without missing pieces (regrow path).
5. **Sodium, far edge:** fly at speed (elytra or ATV) along a forest. Trees ahead change level without
   stalls or a burst of chunk rebuilds, because rebuilds are capped per tick.
6. **AI (singleplayer):** `/summon frontierhunts:whitetail` ×10 and `/summon frontierhunts:wolf` ×5
   nearby. F3 `Frontier AI ticks/s` should be all "full". Fly 150 blocks away and check F3: the counts
   move to reduced, then minimal. Come back: full again. From about 60 blocks, shoot a calm deer: it
   must react and flee at once (full), and the blood trail and tracks look normal. Set
   `wildlifeAiThrottle = false` in serverconfig: everything is full.
7. **Animal animation:** watch a herd walking at 30-60 blocks at high FPS. F3 `Frontier animals: N posed,
   M holding` shows M > 0, and the walk must not visibly stutter. Look through a rifle scope: M drops
   (zoomed animals are posed every frame). Up close M = 0.
8. **Wildlife budget (Ultra, realistic animals):** summon 12 wolves close together. Only the 4 nearest
   (Animal detail MEDIUM) show the full sculpted mesh. The rest use the low mesh, with no flicker as you
   walk among them.
9. On a dedicated server with two clients, repeat step 6 with one player near and one far. Each animal's
   rate follows its nearest player. Check the log for mixin warnings: `PerfMobAiMixin` or
   `PerfGoalSelectorMixin` failing to apply is harmless, but should be reported.
