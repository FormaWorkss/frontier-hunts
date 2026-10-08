# Workstream `seasons` - the world follows the reserve calendar

Branch: `seasons` (clone `/home/claude/work/seasons`). Builds on the shared `SeasonClock` (unchanged).
Compile: `/home/claude/fh/tools/compile.sh /home/claude/work/seasons /tmp/claude-0/cc-seasons` -> `exit=0`.
Jar: `python3 /home/claude/fh/tools/build.py seasons-test /home/claude/work/seasons` -> `.build/*.jar` (builds, merges verified).

**Status: built and offline-checked only. Not yet run in game** (no client here). Everything below under
"In-game test script" still needs a real run.

## What it does

| Feature | How |
|---|---|
| Fall colour (realistic trees) | `client/tree/SeasonalFoliage` recolours a *copy* of each baked foliage card while a section is meshed (no regrowth, no rebake). Per-species palettes (`season/FoliageSeason.Profile`): maple scarlet/orange/yellow-orange/crimson, oak russet/bronze/red-oak/ochre, dark oak brown, birch/aspen/cottonwood gold, cherry red-bronze, rowan orange-red, willow/alder muted, larch gold; spruce/pine/fir/cedar, jungle, acacia, mangrove, azalea stay green. Per tree: a colour and an earlier/later timing from its 6-block "patch" (6 % of trees turn ~2 weeks early). Per card: sun-exposed and high sprays turn and fall first. Timeline: late-August olive tinge -> turn from mid-September -> peak early/mid October (oaks late October) -> browning while leaves fall -> bare by mid November (oaks late November; young oaks keep a few dry leaves all winter, as real ones do). |
| Painted leaf textures | The mod's own leaves (maple, birch, aspen, cottonwood, rowan, willow, alder, golden aspen, autumn maple, larch) and cherry are pre-coloured, so tint alone cannot turn them red. `season/client/SeasonalLeafSprites` (a block-atlas sprite source, `frontierhunts:seasonal_leaves`) derives a grey variant from whatever texture is loaded (any pack) plus the factor that turns it back into the original, so the swap is invisible in summer. Golden aspen / autumn maple / larch / cherry are green in summer while seasons run (their textures are their fall/blossom look). |
| Leaf drop | Cards drop out one by one (both sides of a card and its far-LOD copy together) until the crown is bare - the wood (limbs, twigs) is never touched. Falling-leaf particles: `LeafFall` + `LeafParticle` - flat 3D leaves (4 original sprites: maple, oak, oval, narrow) that pendulum-swing, rock, spin, sink at leaf speed, drift with the mod's wind (`FrontierClient.state` wind, gusting), land flat / float on water and shrink away. Coloured like their tree (same patch/timing/palette). Heaviest late fall; a few dry oak leaves in winter. |
| Bare winter | Deciduous crowns empty; conifers unchanged. Cube leaves (Classic/Balanced): `SeasonalLeafModel` thins leaf blocks in late fall (65/35/15 % variants, dithered per block) and shows crossed twig planes (`textures/block/seasonal/twigs.png`) when bare. |
| Winter snow | `SeasonBiomeTemperatureMixin` (Biome#getTemperature, common) cools seasonal biomes Nov 11 -> Dec 5, full Dec 5 - Feb 18, thaw -> Mar 12; deep winter takes every seasonal biome below the snow line (warmer biomes freeze later/thaw earlier). Seasonal = temperate Overworld land/fresh water + all reserve (frontierhunts) land biomes; never oceans/coasts/beaches, jungle, savanna, badlands, desert, mushroom, mangrove, or any vanilla biome with base temp >= 0.9. Weather rendering, snow accumulation, water freezing and world-gen snow all follow it; server and clients agree (same synced calendar). `SeasonPrecipitationMixin` (ServerLevel#tickPrecipitation HEAD): snow falls *through* bare deciduous crowns onto the forest floor; short grass/ferns are buried while it snows; in spring snow melts a layer per visit (slower at night) and surface ice over water turns back to water; ~30 % of melted spots on grass sprout a tuft. `SeasonSnowLayerMixin` (SnowLayerBlock#canSurvive): snow never rests on natural deciduous leaves (no floating slabs over bare branches); conifer crowns still catch snow. All of it uses the vanilla per-chunk precipitation sample (amortised, ~1 column/chunk/16 ticks). |
| Spring | Bud-burst from ~Mar 1 (per-species, per-tree): sparse, small (35 %) chartreuse sprays -> leaf-out over ~6 weeks to fresh green -> summer green by early June. Cherry blossoms pink in spring, then goes green. |
| Grass & ground | `SeasonalColors` wraps (LOWEST priority, after the alpine handlers) grass block, short/tall grass, ferns, vine and alpine pasture/turf/overgrowth: winter dull straw, thaw, spring fresh, summer unchanged, fall tan/olive - keeping each biome's brightness. |
| Coats | `DeerTraits.greyCoat()` -> `SeasonCoats.grey(coat)`: winter coat share rises Aug 18 -> Oct 18, falls Apr 6 -> Jun 1; each animal's `coat` value is its own moult date (a herd moults over weeks). Individual shade/warmth untouched. Seasons off = the old random trait. |

Rebuilds: all seasonal looks are drawn at a quantised stage time (quarter-month stages; high summer Jun 18 - Jul 24 and
deep winter Dec 15 - Feb 15 are single stages). Mesh threads record which sections hold seasonal content
(`SeasonView.track`); on a stage change `SeasonalClient` dirties only those, nearest first, 6/tick normally and 24/tick
after a calendar jump (`/season set`). Realistic trees cache their seasonal cells per tree and stage
(`Tree.seasonal`), so LOD switches and block updates never re-derive.

## Performance (measured offline / estimated)
- Seasonal card derivation: ~0.3-0.5 us per foliage card (JIT, this sandbox); a dense forest section (5-10k cards)
  costs 2-5 ms extra on a Sodium worker thread **once per stage** (then cached). Summer and bare winter take the
  shortcut (canonical cell returned, or foliage dropped) - no per-card work.
- Leaf-block colour/look: ~0.2 us per block (memoised per block across its quads).
- Sweep: only sections with seasonal content (~surface sections in range). Normal time: one sweep every ~1.75 MC days.
- Leaf particles: PERFORMANCE 3 samples/tick & <= 50 leaves, BALANCED 6 / 140, CINEMATIC 12 / 320; vanilla Particles
  Decreased/Minimal scales down further. No per-frame work anywhere.
- Server: a few block reads per precipitation sample; melt only in seasonal biomes when warm.

## Files
New: `season/FoliageSeason.java` (pure phenology/palettes/snow/coat/grass curves), `season/SeasonState.java`
(per-side live state, seasonal-biome classification), `season/SeasonalSnow.java`, `season/SeasonCoats.java`,
`season/client/{SeasonView, SeasonalClient, SeasonalColors, SeasonalLeafSprites, SeasonalLeafModel, LeafFall, LeafParticle}.java`,
`client/tree/SeasonalFoliage.java`, `client/mixin/{SeasonBiomeTemperatureMixin, SeasonPrecipitationMixin, SeasonSnowLayerMixin}.java`,
`patch/assets/frontierhunts/textures/block/seasonal/{twigs, leaf_maple, leaf_oak, leaf_oval, leaf_narrow}.png` (original, generated).
Merge fragments: `patch/_merge/frontierhunts.client.mixins.json/seasons.json` (3 common mixins in `"mixins"`),
`patch/_merge/assets/minecraft/atlases/blocks.json/seasons.json` (the sprite source).

Shared-file edits (all marked `// [seasons]`):
- `HuntConfig.java`: fields `SEASONS`, `SEASONAL_SNOW`, `LEAF_FALL`; SERVER section `[seasons] seasons=true, seasonalSnow=true`
  (inserted before `SERVER = var1.build()`); CLIENT `world.leafFall = BALANCED` (after `mountainSpindrift`).
- `client/tree/TrunkModel.java`: `treeCell` - `if (cell != null) return SeasonalFoliage.apply(tree, pos, lod, cell); if (attempt > 0) return null;`
  (was `if (cell != null || attempt > 0) return cell;`); `foliageTint` uses `SeasonalColors.baseColor(...)` (the colour
  without the season); new `static int bakedTint(Tree)`.
- `client/tree/TreeGrowth.java`: `Tree` gets `public volatile Object seasonal;` (per-tree seasonal cell cache).
- `hunting/DeerTraits.java` (copied from dec62g8, API identical - javap diff empty): `greyCoat()` body only.

## For the coordinator
1. **Settings screen** (not in src; not copied to avoid a 1.3k-line shared file): in `FrontierSettingsScreen.buildPage`,
   `case ATMOSPHERE`, after the spindrift toggle add
   `this.choice("Falling leaves", "Autumn leaves drifting down", HuntConfig.LEAF_FALL, HuntConfig.Quality.values(), FrontierSettingsScreen::title, "PERFORMANCE drifts a few leaves, BALANCED is the intended look, CINEMATIC fills the air on windy late-October days. Cosmetic only.");`
   `seasons` / `seasonalSnow` are server settings (serverconfig/frontierhunts-server.toml, synced to clients).
2. **perf impostors**: a far impostor card of a tree must follow the season too. API (client.tree):
   `SeasonalFoliage.foliageKeep(tree)` -> 0..1 share of foliage drawn (skip the card below ~0.1, scale coverage);
   `SeasonalFoliage.tint(tree)` -> {r,g,b} multiplier relative to the tree's canonical baked foliage colours (clamp
   after multiplying). Impostor sections must call `SeasonView.track(x, y, z)` while building so a stage change
   rebuilds them, and should rebuild when `SeasonView.key()` changes. Merge note: `TrunkModel.treeCell` hook line.
3. `trophies` coat variants: `DeerTraits.greyCoat()` now asks `SeasonCoats.grey(coat)`; legendary variants can keep
   overriding on top.
4. Sodium: re-registering the leaves colour handlers makes Sodium log "had its color provider replaced" once per
   leaves block and use per-block (vanilla-blended) instead of per-vertex foliage colour for them. Expected.

## In-game test script
Setup: build the jar, singleplayer, creative, cheats on. `/gamerule doDaylightCycle false`, `/time set noon`,
`/weather clear`. Test A with the **Ultra** preset (realistic world), test B with **Balanced** (cube leaves).
Stand at the edge of a mixed forest with a view over a hillside: a reserve biome (`/locate biome frontierhunts:maple_woodland`,
`frontierhunts:aspen_parkland`, `frontierhunts:autumn_maple_hollow`) and a vanilla `minecraft:forest` / `minecraft:dark_forest`.

1. `/season set summer mid` - wait 5 s. Trees look exactly as before this branch (summer = canonical look). No falling leaves.
2. `/season set fall early` (Sep 2) - within ~2-5 s near sections update: greens slightly olive/duller; a few scattered
   trees already red/orange. Few or no falling leaves.
3. `/season set fall mid` (Oct 9) - peak: a patchwork - maples scarlet/orange/yellow-orange (different per tree), birch/aspen
   gold, oaks bronze-green to russet, spruce/pine/fir green. Inside one crown the top/outside is more coloured than
   the inside. Leaves flutter down around you (tumbling, swinging, drifting downwind), coloured like their tree; they
   lie on the ground a few seconds and shrink away. F3: no frame hitch during the update; `/season set` again to
   compare the leaf density in `leafFall = PERFORMANCE / CINEMATIC` (client config).
4. `/season month 11` then `/season set fall late` (Nov 12) - maples/birches bare: bark limbs and twigs visible, no
   leaves; oaks brown and thinned; heavy leaf fall under oaks.
5. `/season set winter early` (Dec 2) - bare deciduous forest, conifers green; young oaks keep a few dry brown leaves
   (and drop one now and then). Grass dull straw-coloured. `/weather rain` in a forest or plains biome: it **snows**
   (flakes, not rain). Over 1-3 minutes snow layers cover the ground, **also under bare trees**; no snow slabs on
   top of bare crowns; short grass tufts are buried; spruce crowns catch snow. `/tp` to a desert / jungle / savanna /
   badlands / ocean: rain or nothing, never snow. Lakes/rivers slowly ice over from the banks.
6. `/weather clear`, `/season set spring early` (Mar 2) - sparse small yellow-green buds on birches/willows/maples,
   oaks still bare; in a cherry grove pink blossom. `/weather rain` now rains in plains/forest. Over the next minutes
   the snow melts patchily (layer by layer, faster by day) and river/lake surface ice turns back to water.
7. `/season set spring mid` (Apr 10) - crowns filling in, light fresh green. `/season set spring late` - full fresh
   green. `/season set summer mid` - back to step 1.
8. Coats: `/summon frontierhunts:whitetail ~ ~ ~` (also `elk`, `moose`) x3 in summer -> red summer coats; `/season set
   winter mid` -> grey winter coats (render updates immediately); `/season set fall early` -> a mix.
9. Test B (Balanced/Classic): repeat 2-7. Cube leaves recolour per tree patch (fall), thin out block by block in late
   fall, become crossed twig planes when bare, and come back thin/yellow-green in spring. Mod maple/aspen leaves go
   red/gold too (grey-variant swap). Player-placed (persistent) leaves never change.
10. Config (server, `serverconfig/frontierhunts-server.toml` `[seasons]`, reload the world): `seasons=false` -> summer
    look all year, random coats, no seasonal snow. `seasonalSnow=false` -> visuals run, but winter rain stays rain.
11. Shaders: Iris + a pack (e.g. Complementary/Photon) - repeat 3, 4, 6: colours carried by vertex colours; falling
    leaves render (terrain-sheet particles); snow as usual. Also shaders off.
12. Multiplayer: dedicated server + 2 clients. Op runs `/season set fall mid` - both clients update within seconds and
    see the same tree colours (deterministic by position) and the same snowfall in winter; a non-op cannot run it.
13. Logs: no "Frontier seasons:" warnings (texture derivation) and no mixin apply errors at startup.
