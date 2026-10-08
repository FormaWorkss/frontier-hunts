# Workstream `routines` — animal routines, game trails, wind, hunting pressure, rut

Branch: `routines` (clone `/home/claude/work/routines`). Compiles with `tools/compile.sh` (exit=0) and packs with
`tools/build.py` (jar built as a check; nothing launched in-game yet: **awaiting in-game verification**).

## What was built

1. **Home range + daily routine (deer, elk, moose).** Each herd (herd mates within the species herd radius share one;
   moose are solitary) surveys a home range around where it lives and keeps it in SavedData
   (`frontierhunts_routines`, per dimension). A range has three anchors:
   - a **bedding thicket**, scored for canopy, dense low cover (tall grass, ferns, bushes, saplings, logs and the mod's
     thicket/undergrowth/bush/shrub/bramble/fern/brush/deadfall/branch/seedling blocks), benches/slopes and edge
     position;
   - a **feeding area**: open sky, grass/flowers/crops/mod forage around it, near a field edge (elk: big open meadows;
     moose: next to water with browse at head height);
   - the **nearest drinkable bank** to the middle of the range (searched outward ring by ring).

   The survey is incremental (6 candidates per call, 14 per server tick for all herds, loaded chunks only), so it never
   scans a big area in one tick. A schedule (`Schedule`, pure function of day time) drives each animal:
   dawn feeding in the field, walk to bed mid-morning, bedded through midday with short stretches/browsing near the bed
   (it rises, takes 1-3 steps, lies back down), late-afternoon rise, water visit, out to the field for dusk, and
   through the night mixed feeding with rests lying out in the field. Elk hold the field later and bed in timber;
   moose feed by water and bed for a shorter midday spell. Mature bucks in the seeking/peak rut **cruise** at midday
   between doe herds' bedding/feeding areas instead of lying up. Herd mates leave for each leg staggered by 0-6 s x 70
   ticks, so they come in strung out single-file. Flee/alert/stance, call responses, lures and rut interactions all
   interrupt the routine (they take the animal out of the calm state or pre-empt its goal); it resumes from wherever
   the animal ends up. Anchors are re-checked on arrival (cover cut down, water gone -> re-survey that anchor).
2. **Game trails.** The first trip on a leg walks via two edge/cover-line points (columns 7-14 blocks off the straight
   line where canopy meets open ground) and records the line the animal actually takes. The recording is simplified
   (collinear crumbs dropped, waypoints <= 7 blocks apart) and stored per herd (bed-feed, bed-water, water-feed; the
   reverse trip walks it backwards). Every later trip - by every herd member - follows the same waypoints, so animals
   come in on the same line day after day. Animals displaced off-range join the nearest trail that leads home. Walking a
   trail leaves a track print at ~55% of waypoints on top of the normal 35-tick prints, so worn trails read in the
   tracking system. Trails that become impassable (3 unreachable waypoints in a row) are forgotten and re-worn. Travel
   pauses now and then (look around / graze) and hangs up at the field edge before stepping out.
3. **Scent and wind.** `perceive()` now records which sense dominated for the strongest hunter. When scent dominates:
   the visual cap no longer holds it down, a strong whiff (plume intensity x species spook >= `scentBustThreshold`)
   busts the animal at once, and any scent-driven bust **always** blows (whitetail blow; elk bark / moose grunt), skips
   the snort, bolts straight away from the hunter with the tail up and alarms the herd, even if it never saw the hunter.
   A buck circling downwind to a call walks into the plume and busts. Upwind the plume is x0.06, so deer walk in close
   with the wind in the hunter's face. ScentControl / scent-cover / blind / stand multipliers are unchanged.
4. **Hunting pressure.** `PressureStore` (SavedData `frontierhunts_pressure`, per dimension) holds units per 64x64
   cell, decaying lazily with a configurable half-life (3 days). Sources: gunshots 1.0 (suppressed 0.6, tranq 0.3,
   flare 0.6), bow shots 0.35 (at the hunter), hits on game 1.5 and kills 3.0 (at the animal), explosions 1.5, animals a
   hunter busted 0.25 (once per animal per 30 s). A fifth spills into each edge neighbour, a tenth into diagonals.
   Level = 1 - e^(-units/4). Effects: the schedule shifts nocturnal (out of the field before light, not rising until
   after dark: at full pressure no daylight field feeding at all), hunters are noticed from up to 1.5x further, alarm
   builds up to 1.25x faster, and when the bed or feed cell holds >= `pressureRelocateAt` (6) units at the morning
   switch, the herd re-surveys a new home range around the quietest of 8 directions (at most once per 2 days).
   Prey among the new wildlife (pronghorn, bison, boar, grouse, duck): alarm distance up to 1.5x, their flushes add
   pressure, and a goal drifts them into the quietest neighbouring cell.
5. **Rut.** (a) Calls/rattling still use `approachCall` (phase-weighted by `Rut.answer`); mature bucks coming to
   rattling/snort-wheeze now come in with the rut display on the head/neck and snort-wheeze where they stop. Busy
   animals (fighting, tending) ignore calls. (b) **Fights**: two mature bucks/bulls (>= 30 months) that meet in
   pre-rut (sparring, gentler), seeking or peak rut: square off (rut_display held, snort-wheeze / bugle / grunt),
   circle broadside round each other (legs on the walk, display on the upper body), close, **lock antlers** (both
   `spar` loops start on the same tick; positions and yaws are driven by the server each tick) and shove back and forth
   along the shared axis - the pushed animal walks backwards with the walk cycle reversed - with antler clashes (the
   real `antler_rattle` recordings, pitched down for elk/moose) every ~5 s, breaks and re-clashes, hoof scuffing
   (block particles + step sounds), grunts. Stamina from mass, maturity, points and health decides it (10-20 s,
   sparring shorter); a badly outclassed buck backs down after posturing. The loser breaks off and runs; the winner
   holds ground displaying and calls; 12% chance the loser takes a small tine wound (short blood trail). A real fight
   draws other mature bucks in like rattling does. (c) **Estrus and chasing**: does/cows cycle for ~0.8 day each
   inside the seeking-to-post-rut window (deterministic per animal). Bucks >= 18 months find them (28 blocks), trail
   them nose-down, and when the buck crowds her the doe bolts through the woods in curving runs while he chases at a
   run, grunting; then she stops and he stands tending her. A satellite buck challenges the tending buck; the winner
   takes the doe.

## Files

New (all `src/com/formaworks/frontierhunts/`):
- `hunting/RoutineAccess.java` - public bridge to Whitetail's package-private cue/speed/thirst/head-down methods.
- `hunting/routine/` - `DeerRoutine` (per-animal state + tasks), `RoutineGoal`, `Schedule`, `HomeRange`,
  `RoutineStore`, `AnchorSearch`, `Habitat`, `RoutineTrails`, `PressureStore`, `PressureEvents` (self-registering
  event subscriber: shots, hits/kills, explosions, housekeeping), `PressureAvoidGoal`, `RutEngine` (+`Engagement`),
  `RutGoal`, `RoutineHooks` (all hook call sites), `RoutineConfig`, `RoutineCommand` (self-registering commands).

Shared files (every change marked `// [routines]`):
- `hunting/Whitetail.java`: `RUT_POSE` synced byte (field + `define`); `routine()`, `rutPose()`, `setRutPose()`,
  `routineBed()`, `routineCancelCall()`; `animatorInput` sets `cueHold`, tending head posture, `backing`;
  `approachCall` refuses when rut-busy + `RoutineHooks.onCall`; `rutWork` and `respondToCall` skip when rut-busy;
  `registerGoals` adds `RutGoal` (priority 1) and `RoutineGoal` (priority 3); `calmForWander` +
  `!suppressStroll`; `customServerAiStep` ends with `RoutineHooks.serverTick(this)`; `perceive()` distance divided by
  `alertRange`, dominant-sense tracking, scent lifts the visual cap, gain x `wariness`, `afterPerceive` scent bust,
  snort skipped on a scent bust; flee start calls `RoutineHooks.bolted` (pressure + always blow on scent);
  `updateBehavior` default case: early `break` while rut-busy, random bedding skipped when `ownsBedding`, old solo
  rival-display cue skipped when fights are on.
- `hunting/DeerAnimator.java`: `Input.cueHold` / `Input.backing`; held cue loops (spar is seamless first==last frame)
  with the legs left on the gait while moving (`holdMask` = neck/head/jaw/ears/tail); walk phase runs backwards when
  backing.
- `wildlife2026/WildlifeMob.java`: `PressureAvoidGoal` (priority 3); player search range and alarm distance x
  `preyWariness`; `preySpooked` when a prey animal flushes.
- `HuntConfig.java`: server section `wildlifeRoutines` (below).

No JSON resources, lang keys, textures or sounds were added (existing sounds only).

## Config (server, `[wildlifeRoutines]`)
`routinesEnabled` (true), `homeRangeBlocks` (64; elk x1.5, moose x1.25), `scentBustThreshold` (0.35),
`pressureEnabled` (true), `pressureHalfLifeDays` (3.0), `pressurePerShot` (1.0), `pressureNightShift` (1.0),
`pressureRelocateAt` (6.0), `rutFights` (true), `rutChases` (true). Not on the settings screen (server config).

## Commands (op level 2)
- `/frontierhunts pressure` - this cell: units, behaviour level %, half-life, days until quiet, 3x3 neighbours.
- `/frontierhunts pressure add <units>` / `set <units>` / `clear [radius]`.
- `/frontierhunts routine` - nearest deer/elk/moose: herd, anchors (+distance), trails, plan, task, pressure, rut state.
- `/frontierhunts routine show` - particles for 20 s: green bed, yellow feed, blue water columns, orange trail points.
- `/frontierhunts routine reset` - the herd forgets its range and re-surveys around the nearest animal.
- `/frontierhunts rut estrus` - does/cows within 32 blocks in estrus for one day.
- `/frontierhunts rut fight` - the two nearest calm mature bucks/bulls of one species square off.

Mature animals for testing (summon with NBT so traits stick):
`/summon frontierhunts:whitetail ~3 ~ ~ {deer_traits:{schema:2,buck:1b,age_months:54,frame:70,condition:80,rack_genes:80,seed:11,abnormal:0,coat:0}}`
(doe: `buck:0b,age_months:40`; elk/moose: entity `frontierhunts:elk` / `frontierhunts:moose` and add `species:"elk"` /
`species:"moose"` inside `deer_traits`).

## In-game test script (singleplayer first, then dedicated server + 2 clients)
Use a world with forest next to open meadow and a pond/creek. Survival or adventure mode for the hunter (creative
players are ignored by deer senses and pressure). Let each step settle 20-30 s.

1. **Survey.** Stand at a forest/meadow edge. Summon 3 whitetail does and 1 mature buck (NBT above) within 10 blocks.
   Wait 10 s, run `/frontierhunts routine`: expect `herd xxxxxxxx · ready`, bedding/feeding/water coordinates, trails
   `none`. `/frontierhunts routine show`: green column in thick cover, yellow in the open near an edge, blue on a bank.
   All four deer report the same herd id. Log: no errors.
2. **Morning leg + trail wear.** `/time set 23500` (dawn). Deer walk to the feeding area and feed (graze steps, look
   around). `/time set 1250` and wait: from ~1300 they leave one by one (staggered) and walk
   calmly to the bed, pausing to look / graze. On arrival they lie down near the green anchor (legs fold, no sliding),
   and `/frontierhunts routine` shows `bed-feed N pts/..m`. Repeat `/time set 23500` -> `/time set 1250`: they come
   back along the **same** orange line (`routine show`); fresh prints appear along it (tracking rule on).
3. **Midday.** `/time set 5700`: some rise, take a few steps / browse, lie back down. `/time set 9700`: they rise, go
   to the water (drink: head down, splash, drink sounds), then to the field; they stop at the field edge for a few
   seconds before stepping out. `/time set 16000`: night - some lie out in the field for a while, then feed again.
4. **Wind.** Watch the HUD wind. Sit in a tree stand on the trail with the wind blowing from the deer toward you
   (you are downwind) at `/time set 1250`: deer walk in close past the stand. Now stand upwind of the trail (your scent
   blows onto it): the first deer to cross the plume blows (distinct blow sound, no snort) and bolts straight away
   from you, tail up, the rest of the herd running too - without having seen you.
5. **Call + scent.** In `/season month 11` (seeking) use the grunt/rattle call with the wind at your back: a mature
   buck circles to come in downwind, head/neck bristling (rut display); when he reaches your scent he busts (blow +
   bolt). With scent-control gear/cover spray he comes in close, snort-wheezes and looks for the fight.
6. **Pressure.** At the feeding area run `/frontierhunts pressure` (0 units). Fire a rifle 3 times there (or
   `/frontierhunts pressure add 3`): ~3.0 units, level ~53%, neighbours 0.6/0.3. `/frontierhunts routine` on the herd:
   `pressure ... feed 3.0`; with `/time set 23500` they leave the field earlier (plan ticks) and at `/time set 10500`
   they stay bedded until after dark. `/frontierhunts pressure add 8` at the field, then `/time set 23000` ->
   `/time set 1300`: at the morning switch the herd re-surveys (`routine`: `moved 1x`, new anchors away from the
   cell). `/frontierhunts pressure clear 2` resets. Pronghorn/bison near a pressured cell flush from further away
   and drift into the quietest neighbouring cell.
7. **Fight.** `/season set fall late` (peak). Summon two mature bucks 8 blocks apart, then `/frontierhunts rut fight`
   (or wait: they start on their own within ~30 s when they see each other). Expect: both stiffen and face each other
   (ears back, snort-wheeze), circle broadside with heads turned to each other (legs walking, no sliding), step in and
   lock heads-down together (spar loops in phase), shove back and forth - the pushed buck's legs walk backwards -
   antler clashes every ~5 s, dirt kicked up, grunts; after 10-20 s one breaks off and runs, the winner stands
   displaying and calls. Other mature bucks within ~48 blocks come toward the fight. Repeat with elk
   (`frontierhunts:elk`, bulls, lower clash pitch, bugles) and moose.
8. **Chase.** Still peak rut: summon 2 mature does + 1 mature buck, `/frontierhunts rut estrus`. The buck walks to a
   doe nose-down, grunting; when he crowds her she bursts into a curving run through the trees and he follows at a
   run grunting; she stops, he stands near her (tending). `/frontierhunts routine` near the doe: `in estrus, tended`.
   Summon a second mature buck nearby: he challenges the tending buck (fight), the winner takes the doe.
9. **Interrupts.** During a trail walk, a fight and a chase: sprint at them / shoot near them: they flee normally
   (fights and chases break up), then return to their routine later. A bedded deer shot at rises hurried and flees.
10. **Multiplayer.** Dedicated server, 2 clients: both observers see the same fight animation in phase, the same
    trail walking, the same bedding; `/frontierhunts` commands only for ops. Restart the server: ranges, trails and
    pressure persist (`routine` / `pressure` output unchanged); deer rejoin their herds.
11. **Performance.** F3 / `/debug` with 20+ deer in loaded chunks: no server tick spikes when a new herd surveys (spread
    over ~1-3 s) or walks trails.

## Known limitations / notes for the coordinator
- Not tested in-game (no client here): contact distance for locked antlers is computed from the rigs
  (`RutEngine.Engagement.reach`: head joint z in the spar pose + antler reach); tweak those two constants if antlers
  visibly overlap or don't touch.
- Trails are only recorded on a trip that starts at an anchor; a freshly spawned herd walks off-range to its first
  anchor directly, the first real leg wears the trail.
- Anchors are only surveyed in loaded chunks; travel waits while the target anchor's chunk is unloaded.
- `perf` throttling: routine/rut logic runs on game time, so fewer ticks only coarsen it; the fight movement is
  applied per goal tick (smoothest when not throttled - fights happen near players anyway).
- Nothing in the settings screen (all server config).
