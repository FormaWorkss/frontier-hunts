# Workstream `herds` — deer, elk and moose live in real social groups

Branch `herds` (clone `/home/claude/work/herds`). `.infra/compile.sh` → exit=0; QA harness builds; offline model checks
`tools/herds/offline.sh` → ALL PASS. **Not run on a server or in-game here** (the rules forbid starting servers in this
sandbox): `tools/qa/herds_run.sh` is ready for the coordinator and prints the measured numbers.

The request: *"the deer never truly travel in herds now. I don't want all deer in herds, just some: realistic deer
behaviour like real life."*

## 1. Why groups did not hold together (root cause)

1. **A "herd" was only a shared home range.** `DeerRoutine.herd()` put every animal into the range of whatever deer of
   its species stood within 24 blocks (`herdRadius`): bucks, does, strangers alike. Sharing a range meant sharing
   three anchor points (bed, feed, water), nothing more.
2. **Nobody followed anybody.** Each animal ran its own copy of the schedule: its own departure (staggered 0-20 s by
   UUID), its own path along the trail, its own random feeding steps up to 11 blocks from the anchor, its own bed spot.
   The only cohesion force in the mod — the random-stroll goal's pull toward `herdCentre()` — is switched off as soon as
   the routine runs (`RoutineHooks.suppressStroll`). So animals that started together drifted apart within minutes and
   arrived at anchors one by one from different directions.
3. **Flight scattered them.** A bolt propagated (28 blocks, line of sight) but every animal fled on its own line away
   from the threat with its own ±35° zigzag, then came back on its own.
4. **No social structure at spawn.** Packs were "bachelor" (92% bucks) or "mixed" (22% bucks) with nothing kept after
   `finalizeSpawn`; moose had no grouping at all. No seasons, no leaders, no mothers.
5. Throttling (`AiThrottle`) was not the cause: it only delays goal selection for calm animals far from players.

## 2. The model (all numbers in `hunting/herd/HerdTuning.java`)

Every animal belongs to one **group**: `FAMILY` (whitetail doe group / elk cow herd / moose cow + calf), `BACHELOR`, or
`SOLO`. The group's **leader runs the daily routine** (bedding, feeding, water, game trails — unchanged `DeerRoutine`);
the others **follow it**:

* **Travelling**: each follower walks at its own place *along the leader's own path* (the leader keeps a breadcrumb trail,
  72 crumbs 1.5 m apart, only while someone follows it). Place = rank × spacing × a personal factor 0.8-1.3, so the group
  strings out loosely on the same trail, not as a blob; each follower drifts slowly to the side of the line (a sine with its
  own phase and period 130-250 ticks), never a marching file. Young follow their **mother** 2.6 m behind (more for
  siblings). Followers stop now and then to browse (~1/240 per tick near their place, 1.5-5 s), then catch up (1.18× walk
  over 8 m behind, a trot over 20 m). A travelling leader stops and looks back when a follower is more than its place +
  `straggle` behind (at most 45 s per leg). When the leader sets off from bed, its followers rise in their own time
  (0.75-4 s) and fall in behind it — the classic single file leaving the bedding cover.
* **Stopped**: followers feed within `feedSpread` of the leader (not of an anchor), bed within `bedSpread` of it (each at
  its own angle around it), take turns at the drinking stand, and don't wander off to browse while the group is moving.
* **Alarm**: a stamp or snort (stance cues) puts every group mate within `alertRadius` on alert (alertness 0.38: heads up,
  turned to the danger, no flight). A bolt sends every mate within `boltRadius` running (0.78) and gives the whole group
  **one flight heading** (away from the danger ±20°); every mate steers 68% onto it, so the group flees together, tails up.
  A member that goes down (shot) makes the rest bolt from the shooter; the group closes up without it, under the next
  leader, and goes back to its routine (feeding area at the next feeding time).
* **More eyes, still fair**: in a group of 3+, when every mate within 25 m has its head down, a grazing animal keeps its
  head up 70% of the time (decision held 3-11 s). The extra alert only raises heads; only a real bolt makes the group run.

| | whitetail | elk | moose |
|---|---|---|---|
| family max (outside winter) | 6 (doe, yearlings, grown daughters) | 30 cow herd (fission-fusion within 40 m) | cow + calf |
| bachelor group | 2-4 bucks | 2-6 bulls | — |
| winter yard / herd max | 20 (all kinds gather) | 45 (cow herds) | — |
| loner females / solitary males / solitary mature males (54 mo+) | 10% / 35% / 55% | 4% / 25% / 40% | all solitary |
| young males leaving their mother | 80%, mid-May to mid-Oct | 50% of spikes, Sep | calf driven off in May |
| travel spacing / side drift (max) | 5 m / 1.4 m (3) | 4 m / 3.5 m (12) | 3 m / 0.8 m |
| feed spread / bed spread / straggle | 12 / 6 / 26 m | 22 / 10 / 40 m | 7 / 4 / 20 m |
| join radius / yard radius | 48 / 96 m | 80 / 128 m | 48 m |
| alert / bolt radius | 40 / 64 m | 64 / 96 m | 32 / 48 m |

**The social year** (calendar month position, 0 = 1 Jan; per-animal dates spread by UUID):

* whitetail — bachelor groups form mid-April and hold through August; they **break up one buck at a time through October**
  (pre-rut); from then **bucks are alone** (cruising, trailing does: the existing `RutEngine`). Yearling bucks leave their
  mothers mid-May to mid-October and may join bachelors. **Yards from mid-December to mid-March**: families, lone does and
  bucks within 96 m gather under the biggest family (up to 20); the families' leaders follow the yard's leader, so a yard
  moves as a loose column to the root's (sheltered: the bedding anchor is the densest cover with canopy) bedding thicket.
* elk — bachelor bands October to August, split late August to mid-September; **herd bulls** (36 mo+) take a cow herd
  without a bull from September to early October (one per herd; others stay satellites), trailing it at the back; cow
  herds meeting within 40 m merge up to 30; winter herds December-March.
* moose — solitary; a cow keeps her calf (under 18 months) until she drives it off in May.

**Natural spawns come as groups** (`HerdSpawn`): the first animal of a spawn pack rolls the group (summer whitetail: 58%
family, 18% bachelor, 14% lone buck, 10% lone doe; rut/winter: 68% family, 26% lone buck, 6% lone doe; elk 72% cow herd,
20% bachelor band, 8% lone bull — a herd bull joins the pack in the rut; moose 40% cow + calf, 35% bull, 25% cow) and each
further animal takes the next place: lead doe 3-9 years, a yearling, a grown daughter 2-5 years, yearlings... Pack sizes:
whitetail 1-5 (was 2-4), elk 3-8 (was 3-6), moose 1-2, with the vanilla cluster cap raised to 6 / 10 / 2. Group data is
written only to the animal (thread-safe for world generation); the group registers when its first member is loaded.

**Old worlds**: animals without a group are **adopted gradually** (100-500 ticks after loading, at most 4 searches per
server tick): a doe joins the nearest family with room within 48 m (or starts one; 10% stay loners), a buck in the bachelor
season pairs with a lone buck or joins a bachelor group (35-55% stay alone), an orphan calf finds a lone cow. **Nobody is
teleported**: a new follower walks to its leader. Spawn-egg / summoned animals are adopted the same way. Academy animals
(`frontierhunts.academy` tag) and NoAI animals are never grouped.

**Persistence**: groups are SavedData `frontierhunts_herds` per dimension (members in walking order with role, sex, age,
mother, last seen; yard parent; the group's home range; a redirect table for merged groups); each animal carries its
group id (`fh_group`). Unloaded members keep their place; a leader not seen for 5 min while others are hands over to the
next in line; a member unseen for 3 in-game days while the rest are is dropped; a group nobody has seen for 20 days is
forgotten. Group members share their leader's home range (RoutineStore): a follower never surveys one of its own, a buck
leaving for the rut surveys a range of his own.

**Cost**: per animal per tick a few field reads and one distance check (breadcrumbs only when followed); a membership
check every 100-131 ticks; a group update every 20 ticks (entity lookup by UUID per member); slower social rules every
400 ticks per group; adoption budgeted server-wide. No per-tick neighbour searches. Followers re-plan short paths only
when their place has moved more than 3.5 m (~1/s on the move). Groups whose members are all unloaded drop their
entity references within 30 s (no unloaded animals kept in memory). `/fhqa herds perf` measures it (see 5).

## 3. Files

New (`src/com/formaworks/frontierhunts/hunting/herd/`): `HerdTuning` (all numbers), `HerdKind`, `HerdRole`, `HerdGroup`,
`HerdStore` (SavedData), `HerdMember` (per-animal state, breadcrumbs), `HerdService` (membership, seasons, yards,
harems, alarms, queries), `HerdSpawn` (natural spawn composition), `HerdEvents` (season cache, housekeeping),
`HerdCommand` (op commands), `HerdConfig`.
Resources: `patch/data/frontierhunts/neoforge/biome_modifier/whitetail_spawns.json` (1-5), `elk_spawns.json` (3-8).
Tests: `tools/qa/harness/.../HerdTest.java` (+ registration and `HerdTest.hook()` in `FrontierQa`),
`tools/qa/herds.commands`, `tools/qa/herds_restart.commands`, `tools/qa/herds_run.sh`, `tools/herds/HerdOffline.java` +
`offline.sh`.

Shared-file hooks (all marked `// [herds]`):
* `hunting/Whitetail.java`: `routineWake(int)` (new: shortens `bedTicks`); `getMaxSpawnClusterSize()` override
  (`HerdService.maxCluster`); `finalizeSpawn`: `SpawnGroupData herdsPack = HerdSpawn.finalize(this, var3, var4); if
  (herdsPack != null) return herdsPack;`; `die`: `HerdService.downed(this, var1)`; flee start: `HerdService.bolted(this,
  this.threat)` after `RoutineHooks.bolted`; flee direction: `var4 = HerdService.fleeHeading(this, var4)`; stance stamp and
  snort: `HerdService.alerted(this, this.threat)`; `calmForForage`: `&& !HerdService.holdForage(this)`; `startGrazing`:
  `&& !HerdService.sentinel(this)`.
* `hunting/routine/DeerRoutine.java`: `public final HerdMember social`; `travelling()`, `localTask()`; `serverTick`:
  `HerdService.tick(...)`; `herd()`: waits while the animal is `settling`, uses the group's range (`rangeFor`), no
  proximity join for grouped animals, reports `leaderRange`; `goalCanUse/Continue/Stop/Tick`: follower mode
  (`followerCanUse`, `followerCanContinue`, `T_FOLLOW`/`tickFollow`, `startBedNear`, `standFree`); `tickTravel`: the leader
  pauses while `leaderShouldWait`; `debugWalkTo(BlockPos)` (command / QA); null-range guards in `advance`/`arrive`.
* `hunting/routine/RoutineHooks.java`: `ownsBedding` / `suppressStroll` also true for an animal following its leader.
* `HuntConfig.java`: `var1.push("herds"); HerdConfig.server(var1); var1.pop();` before `wildlifeRoutines`.

Config (server): `[herds] socialHerds = true` (off = every animal goes its own way, the old behaviour; the routine then
falls back to the old proximity ranges). Not on the settings screen (server config, like the routines).

## 4. Commands (op 2)
* `/frontierhunts herd` — nearest animal: group kind and size, yard, members in walking order (role, sex, age, distance,
  walking/bedded/running), whom it follows and at what gap, the season.
* `/frontierhunts herd show` — 20 s of particles: a green line from each animal to the one it follows (blue: young to
  mother), a gold column over leaders.
* `/frontierhunts herd stats [radius]` — groups around you by species, kind and size; number of yards.
* `/frontierhunts herd startle` — the nearest animal bolts from you (watch the group run with it).
* `/frontierhunts herd lead <x> <z>` — the nearest group's leader walks there (the group follows).
* `/frontierhunts herd on|off|config` — force social groups on / off (A/B comparisons), or back to the config.
Season jumps: the existing `/season ...` commands (`/season month 7` = July, `/season set fall late` = peak rut).

## 5. Tests

**Offline** (`tools/herds/offline.sh`, run here: ALL PASS). Calendar windows across the new year, per-animal fractions
uniform (mean 0.5013), the travel line (whitetail ranks 1-5 at 5.3 / 10.5 / 15.8 / 18.4 / 21.0 m behind the leader,
side drift 1.4-2.2 m; a 30-elk herd ≤ 67 m front to back), the spawn mix from 20,000 rolled packs (whitetail summer:
FAMILY 58%, BACHELOR 18%, SOLO 24%; rut and winter: FAMILY 68%, SOLO 32%, no bachelors; elk 72/20/8; moose 40/60),
planned ages, leader ranking (oldest doe; absent matriarch hands over; herd bull trails; oldest buck leads bachelors),
store bookkeeping (orphaned young, emptied yard roots), save/load round trip, crash duplicates, merge redirects.

**Dedicated server** (`bash tools/qa/herds_run.sh <mod jar> <classes dir> /tmp/fh-herds`; two runs: the second restarts
the first run's world). Steps and pass rules (all print the measured numbers first):
1. `setup` finds whitetail country 320+ m from spawn, force-loads 15×15 chunks (chunk-generation spawns are natural
   packs too), July; `natural 14` spawns 14 whitetail, 4 elk and 4 moose packs through the vanilla pack loop.
2. after 1800 ticks, `composition`: ≤5% ungrouped; whitetail families of 2-6 led by an adult doe and no adult buck;
   bachelor groups of 2-4 bucks; some lone bucks; 90% of mates within 48 m of their leader; elk herds led by a cow;
   moose alone or cow + calf.
3. `cohesion`: up to 3 whitetail families (3+) and one elk herd; each leader walks 110+ m across the square; every 10
   ticks while the leader moves: follower → followed distance (median, p90), share within its place + 18 m (elk +32 m)
   — pass ≥ 85%; nearest-mate median ≥ 1.8 m (not a blob); side offset median 0.15-8 m (not a marching file); leader
   path ≥ 100 m.
4. `alarm` (startle one member) and `down` (kill the leader): all mates bolt within 2 s; flight heading coherence ≥ 0.7;
   spread ≤ 48 m after 6 s; 90 s later all calm, back with their leader, one group; after `down` a new leader.
5. `persist save` → `unload` (forced chunks released, entities saved out) → `reload` → `persist check`: every animal in
   its group, same leaders, **positions at load = positions when saved out** (≤ 2 m). Second run (restart): the same
   check against the positions written at server stop.
6. `season 8.8` → `harem` (elk herd bulls hold cow herds, at most one per herd); `season 10.4` → `rut` (no bachelor
   groups left, every adult buck alone, families intact); `season 0.6` → `yards` (a yard of 8-20, none over 20).
7. `perf 600`: mean server tick with social groups on / off / on at 20 TPS, and the herd logic's own time per tick and
   per animal-tick — pass: on ≤ off × 1.15 + 0.3 ms and the herd logic < 0.25 ms per tick.

## 6. In-game test script (the coordinator)
Survival observer (creative players are ignored by deer senses). A world with forest and meadow.
1. `/season month 7`. Spawn a family: `/summon frontierhunts:whitetail ~3 ~ ~ {deer_traits:{schema:2,buck:0b,age_months:70,frame:60,condition:80,rack_genes:50,seed:3,abnormal:0,coat:10}}`,
   two more does (ages 30, 26) and two yearlings (`age_months:15`, one `buck:1b`) within 10 m. Within ~30 s
   `/frontierhunts herd` lists one family of 5 led by the 70-month doe; yearlings follow mothers. `/frontierhunts herd show`.
2. `/frontierhunts herd lead <x> <z>` 80 m away: the lead doe walks off; the others rise/turn and fall in behind her one
   after another, 4-20 m apart, on her line but not in a perfect file, pausing to browse and catching up at a trot. She
   stops and looks back if one lags. At the far end they spread to feed around her (≤ 12 m).
3. Dawn/dusk (`/time set 1200`, then `/time set 9600`): the group leaves for bed / the field as one, on the same trail
   day after day; they bed within ~6 m of her.
4. Stalk them in survival: one stamps/snorts → every head comes up toward you; one bolts → all run the same way, tails
   up. `/frontierhunts herd startle` does the same from your position. Shoot one: the rest bolt; a minute or two later
   they are calm and together again (`/frontierhunts herd`: new leader if it was her).
5. Bucks: summon 3 bucks (ages 30-60) together → a bachelor group (or some stay alone). `/season month 10` → over the
   month they split one by one (`/frontierhunts herd stats`), `/season set fall late` → all alone, cruising.
6. `/season month 1` with several families nearby (or a natural forest after a while): `herd stats` shows yards; a
   yard moves as a loose column behind its leader to dense cover.
7. Elk: summon 6 cows + 2 calves (`species:"elk"`, entity `frontierhunts:elk`) → a cow herd; a mature bull in September
   (`/season month 9`) joins as herd bull and trails it. Moose: a cow + a 14-month calf → the calf follows her.
8. Dedicated server, 2 clients: both see the same movement; restart: `/frontierhunts herd` unchanged, nobody jumps.
9. `/frontierhunts herd off` vs `on` with F3 / `/debug`: no visible tick-time difference with 50+ animals loaded.

## 7. Limits / notes for the coordinator
* The mod has no fawn age class (traits clamp ages to 12+ months, one mesh for all): "young" are yearlings (12-23 mo,
  moose calves < 18 mo) and they follow their mother. Real spotted fawns would need art and a traits change.
* Not run on a server here; the harness only compiled. If cohesion shares come out low, the knobs are `spacing`,
  `straggle` and the catch-up speeds in `DeerRoutine.tickFollow` (1.18×/1.55× walk).
* Old worlds: grouped animals keep the shared old home range they already had (no reset), so neighbouring groups that
  shared one keep visiting the same bed/feed areas, like overlapping real ranges.
* `/frontierhunts routine reset` still works: the leader re-surveys, followers switch with it.
