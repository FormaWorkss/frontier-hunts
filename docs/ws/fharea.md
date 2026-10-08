# fharea — the first hunt's beginner area: an easy spot, and the way there without the book

Branch `fharea`. User request: "when it takes people to the beginner area in the first hunt, that should take them to an
actual easy spot for deer like plains or easy forest, not snowy mountains. Make that actually work. And have it better
marked or track their direction outside of the book, because they can't have the book open and walk. But keep what's
in the book, I like it."

Compiles (`.infra/compile.sh` → `exit=0`), QA harness builds (`QA_MOD_CP=… tools/qa/harness/build.sh` → built). No
server or client was started here (shared machine); the dedicated-server checks below are for the coordinator, and the
HUD was checked with an offline mock (`tools/fharea/mock_hud.png`, `mock_hud_states.png`).

## 1. An easy beginner area (server)

New class `firsthunt/BeginnerArea.java`; `FirstHunt.assignArea` now asks it instead of taking the nearest whitetail.

**Where the deer are** (unchanged guarantee): live whitetails within 340 blocks (loaded land) and established home ranges
(seen in the last 4 days) are grouped into 32-block cells (at most 16 groups, nearest to the ideal ~130-block walk first).
For each group 6 candidate centres 26 blocks off the animals are looked at, so the deer are always inside the 64-block
circle and the circle is never centred on them.

**Biome tiers** (`BeginnerArea.tier`, cached per biome, rebuilt on `TagsUpdatedEvent`):

| Tier | Frontier Hunts biomes (the Alpine / default world has only these) | Vanilla / other mods |
|---|---|---|
| 1 open | valley_meadow, wildflower_glade, aspen_parkland, sagebrush_bench | plains, sunflower_plains; `c:is_plains`, `frontierhunts:regions/plains`, savanna; names with plain/meadow/prairie/grassland/steppe/field/pasture/glade/clearing/parkland/shrubland/heath |
| 2 easy woods | birch_grove, maple_woodland, aspen_woodland, golden_aspen_grove, autumn_maple_hollow, cedar_valley, riverwood, fireweed_burn, birch_heath, woodland_lakeshore, cottonwood_bottom | forest, birch_forest, flower_forest, old_growth_birch_forest, meadow, savanna; `c:is_forest`, `c:is_birch_forest`, `c:is_flower_forest`, `minecraft:is_forest`, deciduous; forest/wood/grove/orchard/birch/maple/aspen/oak names |
| 3 fair (only if nothing better has deer) | alpine_meadow, heather_moor, pine_highlands, larch_highlands | taiga, cherry_grove, sparse_jungle; taiga / coniferous / `frontierhunts:whitetail_habitat`; highland/plateau versions of tier 1-2 |
| never | snowy_foothills, snowy_pine_forest, glacial_peaks, alpine_fellfield, boulder_talus, limestone_bluff, rocky_foothills, rowan_slope, cascade_gorge, misty_falls, hidden_grotto, mosswood, verdant_karst, alpine_river, marsh_meadow, fern_wetland, muskeg_bog, alder_carr, coastal_ocean, deep_ocean, wild_coast, training_grounds | dark_forest, old-growth taigas, grove, all peaks/slopes, windswept_*, savanna_plateau, swamps, jungles, badlands, desert, snowy_*, ice_spikes, beaches, stony_shore, rivers, mushroom, caves. Any biome tagged `c:is_snowy/is_icy`, base temperature < 0.15, `c:is_mountain/_peak/_slope`, `c:is_slope`, `c:is_peak`, `minecraft:is_mountain/is_hill`, windswept, badlands, swamp, ocean, river, beach, stony shores, jungle, old growth, dense vegetation, cave/underground, wasteland, dead, spooky, mushroom, desert, aquatic, nether/end, `frontierhunts:regions/wetlands`, `regions/shadow_woods`, `regions/tundra`; or with snow/frozen/ice/glacier/tundra/peak/mountain/slope/cliff/canyon/gorge/bluff/talus/badland/swamp/marsh/bog/ocean/river/beach/coast/lake/jungle/dark/shadow/cave/grotto/karst/old_growth… in its name |

**The ground** (`BeginnerArea.judge`): a 16-block grid over the circle (49 points). Biome at every point; where the chunk
is **loaded** (`ServerChunkCache.getChunkNow`, never loads or generates), the ground height from the chunk's own
heightmap (stepping down through trunks/leaves), water/ice at the surface and leaves overhead. Unloaded points get their
biome from the biome source (pure maths, no chunk). A circle is turned down when: the centre is a "never" biome, any
point is snowy, more than 20 % rough biome points, more than 30 % river, and (with ≥ 8 points of known ground) a height
range > 30 blocks, a step > 10 blocks between 16-block neighbours (cliff/bank), > 22 % steep neighbour pairs (> 5 blocks),
> 30 % water, or > 92 % unbroken canopy in woods. Scoring prefers open > woods > fair, small height range, few steep
pairs, little water, a forest edge (12-60 % canopy) over solid canopy or bare ground, and ground already seen.

**The way there** (`BeginnerArea.path`): a sample every 16 blocks on the straight line from the hunter: no 4+ samples of
water in a row (64 blocks: a lake), no 3 ocean samples (the sea), not more than 36 blocks above the hunter; ascent along
the way is a penalty. Distance: ideal 130 blocks, a penalty past 250, never past 340.

**Nothing easy with deer near** → no area, the hunter scouts (the existing scouting state, no marker anywhere), with the
flag `F_ROUGH` and the text "The deer near you are up in rough country: explore lower, open ground and a beginner area
will be marked." "Find another area" applies the same rules (and says so when only rough country has deer).

**Checked again on arrival** (`FirstHunt.verifyArea`): an area chosen before its ground was loaded (`areaV` = 1) is
judged on the ground every 10 s while the hunter is within 2.5 r until 80 % of it is seen (`areaV` = 2); an area saved
by an earlier version (no `areaV`) is judged at once by its biomes. A rough one is moved ("That spot was rougher than it
looked: a new beginner area is marked.") or dropped to scouting — a new hunter is never left pointed at a mountain.

**Cost**: only when an area is assigned (rate-limited as before: at most every 30 s without an area, the button every
10 s) or verified (every 10 s near an unverified area): ≤ 16 groups × 6 centres × 49 points + 25-point paths, inside an
8 ms budget (stops early once 6 circles have been judged); per point a chunk-map lookup, a heightmap read and 1-3 block
reads; biome traits cached; static arrays reused. Nothing per tick otherwise.

Advice line: open centre → "meadow edge"; bedding anchor → "bedding"; lakeshore/bottom/river names → "by the water";
else "forest edge" (`FirstHunt.adviceFor`, no chunk lookups any more).

Op command `/frontierhunts firsthunt areainfo`: the judge's one-line verdict on the hunter's area and on the ground they
stand on (biome + tier, height range, biggest step, steep pairs, water, canopy, rough/river/snowy points, ground known).
`/frontierhunts firsthunt` (status) now also says "(ground checked)" / "(ground checked on arrival)" / "deer only in
rough country".

## 2. Finding the way without the book (client)

**Compass ribbon** (`guide/client/FirstHuntCompass.java`, GUI layer `frontierhunts:first_hunt_compass`, registered below
the boss bar): top centre, 176 × 13 GUI px, a soft dark band fading out at both ends (cream hairline on top, gold
hairline under), N/E/S/W (Inter semibold, cream) and NE/SE/SW/NW (small, muted) sliding as the camera turns (160° in
view), minor ticks every 15°, a small cream notch at the centre. A **gold diamond** with a dark rim sits on the area's
bearing; out of view it becomes a **gold arrow at the ribbon's end** on the side to turn to (kept on its side while the
area is roughly behind, so it never flickers). Under the marker a dark pill with the distance in gold (**"210 m"**: the
same "to the middle of the broad circle" number as the card/Handbook, exact under 50, then 5 m / 10 m steps), which
slides open to **"210 m · Beginner area"** while the hunter faces it (within 10°, until 15°) and for the ribbon's first
6 seconds. A compass letter under the marker steps back.

Shown while: the first hunt runs, the step is Sign / Wind / Shot, the hunter has an area in this dimension (new flag
`F_AREA_HERE`), it hasn't gone quiet, the steps aren't hidden (`F_HIDDEN`), and the hunter is outside the circle (r, out
again at r + 6). Steps aside (fades) with any screen, F1, F3, using an item (drawing the bow), scopes/optics, the kill cam,
hunt cinematics and Academy training — the same moments as the objective card (`ObjectiveCard.busy`, now
package-private). Reduced motion: no fades.

**Arrival note**: walking in swaps the ribbon for a short pill at the same spot: gold ring + "You're in the beginner area"
and what to do now ("Look at the ground for deer sign." / "Keep the wind in your face as you close in." / "Wait for a calm
deer standing side-on."), 5.6 s, then it fades. Shown again only after leaving and coming back (or for a new area).

**Light column** (`guide/client/FirstHuntBeacon.java`, `RenderLevelStageEvent` AFTER_BLOCK_ENTITIES): a soft gold column
over the area's middle — a wide faint glow (17 %) and a narrow warm core (62 %), each a camera-facing ribbon whose
outer edges are clear (vertex alpha, no hard edge), fading in from the ground, thinning into the sky by 150 blocks up,
a slow shimmer running up it. Fades out between ~84 and 14 blocks from the centre (gone in the middle). Drawn with the
vanilla `beacon_beam` render type (translucent, no depth write, full bright; texture `textures/misc/first_hunt_beam.png`,
plain white) so Iris/shader packs render it with their beacon program (emissive, their fog) and it looks right without
shaders too. Beyond 62 % of the render distance it is drawn nearer along the same line of sight at the same apparent
size, so it shows past the fog/render distance; it widens slowly with distance so it never thins to a flickering hair.
Never drawn with F1, `F_HIDDEN`, in the Iris shadow pass, during the kill cam or a hunt cinematic. 128 vertices a frame
(both windings, the culled one costs nothing), no allocation of its own; the ground height is looked up once a second.

**The card** keeps everything; its little arrow is left out while the ribbon shows the way (`FirstHuntClient.renderCard`:
`showArrow … && !FirstHuntCompass.guiding()`). The **Handbook First Hunt page** is untouched (its "Where to look" card,
compass needle, text and button are exactly as before; `where()` only has the new "rough country" line for the new
state).

**Per frame**: state, distance text (`FormattedCharSequence`, rebuilt only when the rounded metres change) and labels
(once per language) are worked out 20×/s in a client tick; the frame draws plain `fill`s at the screen's own pixels
inside one `FrontierUi.batch` (static `Runnable`s, no capture), switching the pose to pixels in place (a saved `Matrix4f`,
no push), and a handful of cached text lines. No `String.format`, no arrays, no lambdas per frame.

## Files

New:
- `src/com/formaworks/frontierhunts/firsthunt/BeginnerArea.java` — tiers, judge, path, choose.
- `src/com/formaworks/frontierhunts/guide/client/FirstHuntCompass.java` — HUD ribbon + arrival note.
- `src/com/formaworks/frontierhunts/guide/client/FirstHuntBeacon.java` — the light column.
- `patch/assets/frontierhunts/textures/misc/first_hunt_beam.png` — 16×16 white (the beam's texture).
- `patch/_merge/assets/frontierhunts/lang/en_us.json/zzzzzzz_fharea.json` — 17 keys (`firsthunt.frontierhunts.hud.*`,
  `area.rough`, `area.rough_short`, `area.moved`).
- `tools/fharea/mock_hud.py`, `tools/fharea/mock_hud.png`, `tools/fharea/mock_hud_states.png` — offline mock.
- `tools/qa/harness/src/com/formaworks/frontierqa/AreaTest.java`, `tools/qa/fharea.commands` — dedicated-server checks.

Changed (small, marked `[fharea]`):
- `firsthunt/FirstHunt.java` — `assignArea` → `BeginnerArea.choose`; new `verifyArea` (called from `areaCheck`);
  `adviceFor(String, int, int)` replaces the old `score`/`adviceFor` (no chunk lookups); `Live.verifyAt`; flags
  `F_ROUGH`/`F_AREA_HERE` in `sync`; "rough" message for "Find another area"; `areainfo` command; status line;
  `TagsUpdatedEvent` clears the tier cache. Saved keys added: `areaV` (int), `rough` (bool).
- `firsthunt/FirstHuntNetwork.java` — `F_ROUGH = 65536`, `F_AREA_HERE = 131072` (flags only; the payload is unchanged).
- `guide/client/FirstHuntClient.java` — card arrow only without the ribbon; `where()` rough text.
- `guide/client/ObjectiveCard.java` — `busy()` package-private.
- `tools/qa/harness/.../FlowTest.java` — routes `easyherd|easyherd2|easy|rough|legacy|tiers`; `empty` marks its area
  ground-checked (the test is about deer leaving).
- `tools/qa/persist/flow.commands` — `herd`/`herd2` → `easyherd`/`easyherd2` (the old fixed offsets could land in rough
  country, where an area is now correctly refused) + `easy` checks after `area` and `another`.

## Dedicated-server checks (coordinator)

```
python3 tools/build.py fharea-test /home/claude/work/fharea          # or the coordinator's merged build
/home/claude/fh2/.infra/compile.sh /home/claude/work/fharea /tmp/claude-0/cc-fharea
bash tools/qa/server_smoke.sh .build/<jar> tools/qa/fharea.commands /tmp/fh-fharea      # QA_MAX_TICK=-1 if the watchdog trips on 'rough'
grep -E "flow (PASS|FAIL|SKIP)" /tmp/fh-fharea/console.out
bash tools/qa/persist/run.sh .build/<jar> /tmp/claude-0/cc-fharea   # the whole persist + flow suite (flow uses easyherd now)
```
Expected `flow PASS` lines: easy site found ×2 (info), area assigned near the deer, `easy area (first)`, 'Find another
area' ≥ 100 blocks away + rate-limited, `easy area (another)`, legacy rough area moved to an easy spot,
`easy area (legacy)`, deer only in snowy/mountain country → no area + scouting + rough, 'Find another area' there →
still no area, old area on rough ground there → dropped. `flow tiers` prints every biome's tier (check the table).
`rough`/`legacy` print `flow SKIP` if the world has no wide snowy country within ~3,600 blocks of spawn (then nothing
failed). No ERROR/WARN, no client classes on the server.

## In-game test script (coordinator, client)

1. New default (Alpine) world, op, survival. `/frontierhunts licence mode off` (no paperwork, so the first hunt moves to
   step 2 "Find deer sign"). `/frontierhunts firsthunt reset @s`. Wait ~2 s.
2. If whitetails are near: within a few seconds the top-left card shows step 2 and a **compass ribbon** appears at the top
   centre with a gold diamond and "N m · Beginner area" (the caption shows for 6 s, then only while facing it). If none
   are near: `/summon frontierhunts:whitetail ~120 ~ ~20` ×3 on open ground (meadow/parkland), `/frontierhunts firsthunt area`.
3. `/frontierhunts firsthunt areainfo`: the area's biome is tier 1-2 (valley_meadow, wildflower_glade, aspen_parkland,
   birch_grove, maple_woodland…), "easy", height range ≤ 30, no snowy points.
4. Turn around slowly: letters slide; the diamond follows the area; past ~74° it becomes a gold arrow at the ribbon's end
   on the side to turn to; behind you it stays on one side. Card: no little arrow next to the title any more.
5. Look toward the area: a soft gold light column rises over it (also visible far beyond the render distance, slimmer
   and fainter; hidden behind nearby hills). Toggle shaders (Iris + Photon / Complementary) on and off: the column
   looks like a gentle beacon glow in both, no hard edges, no flicker, no z-fighting, never in shadows.
6. Walk to it: the distance counts down (exact under 50 m). Crossing the edge: the ribbon fades and "You're in the
   beginner area · Look at the ground for deer sign." shows ~5 s then fades; the column fades away as you near the middle.
   Walk out and back in: the ribbon returns; the note shows again.
7. F1: ribbon and column gone. Open the inventory / Handbook / any screen, draw the bow, use binoculars: the ribbon fades
   out and back. Handbook › First hunt: the page and its "Where to look" card are exactly as before. "Hide steps": ribbon
   and column gone; "Show": back.
8. `/locate biome frontierhunts:glacial_peaks` (or snowy_foothills / boulder_talus), `/tp` there, kill every whitetail
   near spawn's area (or go > 400 blocks away), `/summon frontierhunts:whitetail` ×3 on the snow, then
   Handbook › First hunt › "Find another area" (or `/frontierhunts firsthunt area`): action bar "The deer near you are up
   in rough country…", the card/page say the same, **no ribbon, no column**. `/frontierhunts firsthunt` → "no area: deer only
   in rough country".
9. Walk back toward open, lower ground with deer: within 30 s an easy area is marked again (ribbon returns).
10. Debug overlay (F3) frame time with the ribbon + column showing vs `Hide steps`: no measurable difference;
    `/frontierhunts firsthunt area` spammed: rate-limited (one per 10 s), no tick spike in `/neoforge tps` or the
    debug pie.
11. `/frontierhunts licence mode relaxed` to restore.

## Risks / not verified here
- No in-game run (no client here): the ribbon's exact text baselines (Inter TTF shifts) and the column's look under
  each shader pack are from the mock and the code, not a screenshot. The mock uses the real fonts and constants.
- Terrain limits (range 30, step 10, 22 % steep pairs) are tuned by reasoning about the Alpine generator, not measured;
  if the coordinator's `flow easy` / `easyherd` lines show many "too steep" verdicts on gentle land, raise `MAX_RANGE`.
- An area chosen around home ranges in unloaded land is judged by biome first and on the ground when the hunter gets
  near; a move on arrival is possible (with a clear message), by design.
- Boss bars draw over the ribbon (rare in this mod). The column uses the beacon render type; a shader pack that hides
  beacon beams would hide it too (the ribbon still shows the way).
