# Workstream `structures2`: Austin's buildings in the world, one tree stand, better sites, working doors

Branch `structures2` (from master 644f37a / gear.15).

## 1. Frontier Structures (Austin's buildings) now generate

**Why they never showed up (root cause, verified on the headless server):**
`frontierstructures:ExpeditionSite.findGenerationPoint` sampled a 7 x 7 grid with `getBaseColumn` (on the alpine
generator: a full 384-block column + slope sampling per point) and demanded <= 2 blocks of relief over the building
*plus* a 6-block apron. Measured offline on the alpine layout (tools/structures2 `Acc`, seed 12345, 400 candidates,
28 x 28 footprint): 0.5 % of candidates pass <= 2 blocks; 38 % touch water. So virtually nothing generated, and
`/locate structure frontierstructures:settlement_lodge` on gear.15 **hung the server** (watchdog crash, 60 s tick).
The alpine generator has no Beardifier, so `terrain_adaptation` cannot help either. Biome tags, config and structure
placement itself were fine (the alpine generator runs structure placement - the living-world sites prove it).

**Fix (fs/java):**
- `ExpeditionSite`: cheap height samples (`Heights`: one alpine layout sample per column, cached), centre+corner
  early-out, then a 5..8 grid over the footprint; rejects open water under the building (fishing hut may touch a shore);
  per-structure `max_relief` (11, hamlet 14); yard level = upper median of the samples.
- `TerrainFit` (new): real terrain adaptation in `postProcess`, chunk-local and deterministic (natural heights from the
  generator): inside the footprint natural terrain/plants/trees above the yard are cut (no hill in a room, no buried
  door) and voids below are filled to solid ground (no floating foundations); a 7-block apron eases the ground to the
  yard level with a smooth falloff (graded yard instead of a cliff or a pit); trees on regraded columns are removed;
  water columns are never regraded. Piece box inflated by the apron so neighbouring chunks run it.
- Sets: `buildings` spacing 16 / separation 6 (was 32/16), weights lodge 3, outfitter 2, trapper 4, homestead 3,
  lookout 4, smokehouse 2, fishing 2; `hamlets` 48/20 (was 100/50). `has_settlement` now lists every Frontier land/forest
  biome (mosswood, snowy_pine_forest, larch_highlands, riverwood, rowan_slope, alder_carr ...) + `#c:is_forest`.
- `patch/_remove/structures2.txt`: drops the base jar's `frontierhunts:expedition_buildings` / `frontier_hamlets`
  sets - older pre-1.2 copies of the same eight buildings that would generate twice (structure + templates kept so
  `/place` and old saves still resolve).
- PC folder check (`C:\Users\austi\Documents\Minecraft-Mod-Builder\frontier-structures`, read-only): the authoritative
  player export `player-exports/20260927-all-buildings` (+ revision3 pond/moss) is byte-identical (sha256) to the eight
  templates already in fs/. PLAYER_EDIT_REVIEW.md: "no new watchtowers" - the Crowsnest lookout is the tower. No other
  exported .nbt exist there (only QA backups / older revisions), so nothing was missing from the jar.

### Structure catalogue
| id (`/locate structure ...`) | name | size |
|---|---|---|
| frontierstructures:settlement_lodge | Three Pines Lodge | 26 x 30 |
| frontierstructures:settlement_outfitter | Pine Junction Outfitters | 29 x 25 |
| frontierstructures:settlement_trapper | Alder Run Trapper Cabin | 23 x 21 |
| frontierstructures:settlement_homestead | Birch Hollow Homestead | 25 x 30 |
| frontierstructures:settlement_lookout | Crowsnest Timber Lookout (watchtower) | 20 x 21, 37 high |
| frontierstructures:settlement_smokehouse | Split Antler Smokehouse and Tannery | 27 x 21 |
| frontierstructures:settlement_fishing | Reedbank Fishing Hut and Pond Dock | 36 x 29 |
| frontierstructures:settlement_hamlet | Pine Junction Hunting Hamlet (rare) | 125 x 79 |
| frontierhunts:tree_stand | single tree stand (new) | ~16 x 16 |
| frontierhunts:hunting_camp / elk_camp / abandoned_camp / outfitter_post / ranger_station / trapper_cabin / meat_shed / trailhead / ground_blind_plot / duck_blind / glassing_point / fence_line / antler_cache | living-world sites | |

## 2. Living world
- **Tree stand**: new kind `tree_stand` (`plan/kinds/TreeStand.java`, variants hang_on / ladder_stand): ONE stand on a
  planted big tree (mod tree generator in Frontier biomes), foliage cleared around ladder/platform, a faint wandering game
  trail with two pawed scrapes and sticks in front, sometimes a trail camera tree or stand sign. No clearing/plot. Own
  set `living_stands` (spacing 7 / sep 3, `#has_structure/living_land` = forests + meadows) so you stumble on them often.
  `stand_line` is no longer generated (kind kept so saved pieces rebuild; removed from `living_spots`).
- **Buildings** (`Cabin` - outfitter post, ranger station, trapper cabin): stone sill course of mossy cobble /
  cobble / fieldstone under the log walls, saddle-notched corner log ends with footing stones, worn path from the door
  (coarse dirt / path / gravel), ferns, grass, brush and berry bushes hugging the footings, rain barrel at a corner.
  Meat shed posts on stone footings.
- **Weathering pass** (`plan/Weather.java`, applied to every site in `LivingSite.plan` and the harness): one roof palette
  per site (dark oak shingles / spruce shakes with dark patches / old mossy roof) replacing the flat charcoal roof
  blocks, partly bark-stripped wall logs, mossy cobble, partly weathered planks.
- Tents/camps unchanged (already in forest biomes via `living_land`). Kinds kept; none cut.
- Note: a site that was half-generated before this update rebuilds its plan with the new materials in the remaining
  chunks (material seam possible on that one site).

## 3. Doors
Door-like blocks audited: `frontierhunts:cabin_door` (vanilla `DoorBlock`, used by all living-world cabins), vanilla
doors/trapdoors/gates in the templates, multi-part flaps of tents / hub ground blind / tower blind (shape tables per
part/open/facing, all parts toggled together - unchanged).
**Bug:** the cabin door's blockstate drew the leaf on the opposite edge of the block from DoorBlock's collision/outline
box in all 32 states (13 px off): you clicked "through" the drawn door, it swung the wrong way, ghost collision where
nothing was drawn. **Fix:** `tools/structures2/gen_doors.py` regenerates `blockstates/cabin_door.json` +
`models/block/cabin_door_{lower,upper}_{left,right}.json` from the vanilla shape table (leaf on the shape box, handle at
the swinging end on both faces, open states use the opposite-handed model like vanilla). Behaviour (both halves,
sounds `BlockSetType.SPRUCE`, redstone, multiplayer sync) is vanilla DoorBlock. Also fixed: the loot table dropped a
door from BOTH halves (dupe) - now lower half only.
Offline check: `python3 tools/structures2/door_shapes_check.py <jar>` - every door-like blockstate (facing+half+hinge+open)
vs vanilla shapes, handle away from the hinge; vanilla spruce_door as control. gear.15: 32/32 bad; now DOOR SHAPES OK.

## Files
- fs/java: `ExpeditionSite.java` (rewritten site check/piece), new `TerrainFit.java`, `Heights.java`; fs/resources
  worldgen structure/structure_set JSON, `has_settlement` tag.
- src livingworld: `plan/Cabin.java`, `plan/Plan.java` (+`restyle`), new `plan/Weather.java`, new
  `plan/kinds/TreeStand.java`, `plan/Kinds.java` (+1 line), `plan/kinds/MeatShed.java` (footings),
  `LivingSite.java` (1 hook line `Weather.apply(c)`).
- patch: `data/frontierhunts/worldgen/structure/tree_stand.json`, `structure_set/living_stands.json`,
  `structure_set/living_spots.json` (stand_line removed), `_remove/structures2.txt`,
  `assets/frontierhunts/blockstates/cabin_door.json`, 4 door models, `loot_table/blocks/cabin_door.json`.
- tools/structures2: `gen_doors.py`, `door_shapes_check.py`, `server_test.sh` (= livingworld test, symlinks the jar,
  20 min boot timeout), `Acc.java` (offline acceptance of FS sites on the alpine layout);
  tools/livingworld/harness/Harness.java (+Weather pass).

## Verification (headless NeoForge server, tools/structures2/server_test.sh, seed frontierqa)
- gear.15: `/locate structure frontierstructures:settlement_lodge` -> watchdog crash (60 s tick).
- this branch: all 8 FS structures locate (lodge 368 blocks, trapper 283, lookout 256, homestead 893, smokehouse 816,
  outfitter 1384, fishing 1699, hamlet 5064; 0-14 s each), `frontierhunts:tree_stand` 128 blocks, living sites as before.
- Natural generation: the lodge chunks forceloaded and generated, dumped from the region files (mca_dump) and rendered:
  sits on a graded yard cut into a hillside, terraced grass apron, path, no floating/buried parts.
- `/place` smokehouse at its located spot succeeded; /place on steep/wet ground answers "Failed to place structure".
- Logs: 0 ERROR/WARN from our mods (after the orphan block-entity fix), 0 client classes, no crash report.
- Offline: livingworld harness ALL OK, door_shapes_check DOOR SHAPES OK, check_jar 0 errors, recipe_audit PASS.
- Not run: the full tools/qa/server_smoke.sh (machine heavily loaded, disk nearly full); not seen with a client.

## In-game test script
1. New world (Frontier world type). `/locate structure frontierstructures:settlement_lodge` (and the other seven) -
   each should answer within a few seconds; `/tp` there. Check: building sits on a graded yard, no floating foundation,
   no hill inside rooms, doors not buried, trees cut around it.
2. `/place structure frontierstructures:<id> X ~ Z` runs the same site check as world generation: it answers "Failed to
   place structure" on water or ground steeper than the relief limit (11 blocks over the footprint). Use a /locate
   result or gentle ground; check foundations reach the ground and the apron is eased.
3. Walk/fly through a Frontier forest (mosswood, riverwood, snowy pine): tree stands every ~100-200 blocks, one stand
   each, with a trail in front; `/locate structure frontierhunts:tree_stand`.
4. `/frontierhunts sites place trapper_cabin` and `... ranger_station`: stone sill, notched corners, brown/mossy roof,
   path, ferns at the walls.
5. Cabin door: open/close from both sides, both hinge sides (place a `frontierhunts:cabin_door` facing each way):
   the drawn door matches the hitbox (F3+B), swings around its hinge, both halves move, redstone (lever) works, a second
   player sees it; break it in survival -> exactly one door drops.
