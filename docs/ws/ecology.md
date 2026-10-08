# Workstream `ecology` — predators hunt prey, kill sites you can read, rare bone finds

Branch: `ecology` (clone `/home/claude/work/ecology`). `tools/compile.sh` → exit=0; `python3 tools/build.py ecology-test .`
builds; `tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Offline harness
`tools/ecology/harness` → ALL PASS (see below). **Not run in-game here** (no client): awaiting in-game verification.

## 1. Predation (server)

Package `ecology`. Every wildlife mob gets `HuntGoal` (priority 2); for prey species it returns false at once.

* **Who hunts what** (`Predator`, `Prey`):
  * **Wolves** — packs (the starter recruits up to 5 calm wolves within 24 blocks). Whitetail (yearlings first), elk
    (needs 2+, cows/yearlings; healthy bulls rarely), moose (needs 3+, mostly yearlings), pronghorn, **weak/old bison
    only** (14% of bison are "old" by uuid, plus anything below 60% health), boar (pack). Walk up openly (head-low
    testing posture), the herd watches, then bolts; long chase, **20-35% success**.
  * **Coyotes** — rabbits, grouse, ducks by stalk + high mouse-pounce (~42%); a pair may run a yearling deer (12%).
  * **Cougars / panthers** — ambush: slow crouched stalk; detection rolls (distance², wind from predator to prey ×2,
    cover ×0.55, grazing prey ×0.6) usually blow it at range (prey bolts, cat gives up); within ~8 blocks a short
    explosive rush, a leap onto the prey and a neck bite (~50% of rushes).
  * **Lions** (1-3) stalk-and-rush from further out; bison only with 2+. **Cheetahs** hunt by day: a walk-up then a very
    fast short sprint (2.9× speed controller) and a trip; pronghorn first.
  * **Bears** mostly scavenge carcasses (64 blocks), sometimes run down a yearling/calf (15-18%). Polar bears only
    scavenge.
* **When**: a hunger clock per predator (saved in its persistent data, `frontierhunts_fed`; a meal lasts 1-3 in-game
  days), a time-of-day mood (night hunters at dawn/dusk/night; ×0.12 at noon; cheetah the reverse), a per-check rate,
  **only within 64 blocks of a player and inside the perf full-rate band**, never within 12 blocks of one, never in
  pressured country (PressureStore level > 0.55), at most one hunt start per 128×128 area per 1800 ticks, **at most
  `killsPerAreaPerDay` (2) kills per area per day** (small game not counted), the last animal of a herd is avoided.
  A failed hunt rests the predator 2-4 minutes before it may try again.
* **Phases** (`Hunt`): RALLY (wolves at dusk: pack gathers, howl/chorus, 4 s) → APPROACH (canids/bears) or STALK
  (cats, cheetahs, small game) → RUN (outcome rolled at the start; an adaptive speed controller matches the prey's
  measured ground speed and gains ×1.16 on a kill, or fades to ×0.72 after a burst) or STANDOFF (healthy bull
  moose 70% / bull elk 40% turn and stand: stance + stomps; wolves circle 8-11 s and leave) → STRIKE (one physical
  leap/lunge impulse, contact check) → FEED (members walk to slots around the body, feed with tugging, growls,
  eating sounds; blood soaks out and hair is scattered over time) → they walk 20 blocks away. Every phase has a time
  limit; a hunt ends when its prey is shot/removed, a member is hurt, or a player comes close (the normal wildlife
  flee from players still works: predators break off).
* **Prey react through their own systems**: deer/elk/moose get `Whitetail.alarm()` (stance/snort/blow, then the bolt;
  the herd within 20 blocks bolts too); wildlife prey get `WildlifeMob.ecoScare` (flee, birds flush) — up to two
  healthy adult bison or boar **defend and charge** (their ResponseGoal attack); a predator hit by prey backs off
  (`EcologyHooks.yields`). Rabbits dash away. A deer bolting from a predator is stamped `fh_spook_at` first, so the
  routines' pressure ledger does not count wolves as hunters (predators would otherwise slowly "pressure" an area).
* **Perf**: a predator considers hunting at most every 10-15 s (cheap gates first, then one bounded prey query
  ≤ 64 blocks), max 8 running hunts, each a few members, ticked once per server tick; paths re-issued every 5-10
  ticks, long targets approached by 24-block waypoints (follow range 32). All loops bounded; positions checked finite.

## 2. Kills and sign

* **Deer, elk, moose**: the Whitetail goes down through its own `die()` (collapse with momentum — the existing downed
  body). The kill record lives in its persistent data (`frontierhunts_kill`). It lies for `carcassDays` (2) of world
  time (the 1.5-day hunter-kill limit does not apply), then turns into bones (skull with antlers if it was an
  antlered bull/buck, ribs, legs/spine) and disappears.
* **Pronghorn, bison, hogs**: new entity `frontierhunts:kill_carcass` (`KillCarcass`): drawn by the species' own
  wildlife renderer (Classic/Balanced/Ultra) through a still client-side stand-in rolled onto its side; a fresh kill tips
  over in 0.6 s. Same lifetime and bones. Creative left-click removes it.
* **Small game** is taken whole: a drop of blood and a pinch of fur/feathers (+ item particles).
* **Sign** (`tracking/KillSign`, written into the tracking store): a matted bloody scuffle bed where the prey was
  caught, spray around it, **drag/scuffle scratch marks** (TrailMark style SCRATCH) from the catch point to where the
  body came to rest, **tufts of hair** (style FUR) whose existing inspection reads `WOLF KILL · SHED FUR` /
  `Wolf kill · whitetail doe · pack of 3` / `About a day old · predator sign`, gut-type blood pools growing under the body
  while it is fed on. Predators' own prints come from TrackPrints as they mill about. FUR/SCRATCH marks now last 2
  days (`TrailMark.lifetime`).
* **Inspection**: use the carcass with an empty hand → 3 chat lines, e.g. `WOLF KILL · whitetail doe` / `Pack of 3 ·
  about a day old · half eaten` / `Torn at the hindquarters and flank… · scavenged since by grizzly`. **No trophy**:
  with the skinning knife a fresh, little-eaten deer kill gives a few venison (and the hide if < 35% eaten) once.
* **Scavengers** (bears always, coyotes/wolves when very hungry) find open kill sites (`KillSiteStore`, max 64 per
  dimension) and feed; their names are added to the reading.

## 3. Bone finds

Blocks (no collision, break instantly-ish, bone sounds; right-click reads them in the action bar):
`whitetail_skull`, `elk_skull`, `moose_skull` (antlers on/off), `bison_skull` (horns), `scattered_bones`
(`part` = ribs / spine / legs), `shed_antler`. Properties `facing`, `mossy`, `natural` (`natural=false` = kill
remains: fresh greasy bone texture, they crumble away after a few weeks of random ticks). Antlered skulls, bison
skulls and sheds drop themselves (placeable decor, World creative tab after the deadfall/branches); bare skulls and
scattered bones drop 1-3 bones. Models are original Blockbench-style JSON (11-37 elements, sunk 0.4-1.5 px into the
ground; elk/moose racks reach outside the block), Classic 16 px and realistic-pack 64 px textures (weathered,
mossy, mossy top, fresh, antler, burr, bison horn, socket). Generator: `tools/ecology/gen_bones.py`; preview
renderer `tools/ecology/preview_bones.py <repo> out.png [classic|real] [yaw] [pitch] [names]`.

**Worldgen**: placed feature `frontierhunts:bone_site` (rarity 1/160 × 50% in the feature × `boneSiteFrequency`
≈ **one site per ~320 suitable chunks**) in `#frontierhunts:has_bone_sites` (whitetail/elk/moose habitats, bison and
pronghorn ranges, badlands, snowy plains, savanna). Species from the biome (deer + sheds in woods, elk/moose north,
bison on plains/badlands, pronghorn remains). Never on structures (`hasAnyStructureAt`), water, mud or man-made
ground; only on soil/sand/terracotta/snow/gravel/forest floor with air or a short plant above.

## 4. Animations (client)

`ecology/client/HuntPose`: smoothed weights from the synced wildlife state (new states 7-12: STALK, CHASE, POUNCE,
TEAR, HOWL, TRAVEL). Sculpted rig: crouch (cats full, cheetah 0.6, canids 0.45, bears 0.2), feeding stance with head
tugging, half-sitting howl with muzzle up, pounce (forelegs reaching, hind legs driving, airborne), chase keeps the
rotary gallop. **Paws stay planted**: per-leg two-joint IK (Gauss-Newton on upper/lower joint, foot counter-turned)
holds each paw at its pre-pose position while the body drops, then a pelvis pitch + height solve. Classic box models
get the same poses in box form. Howl sound: original synthesis (`tools/ecology/gen_howl.py`: three solo howls, two
pack choruses, mono 44.1 kHz ogg; heard ~96 blocks).

## Files

New: `src/.../ecology/*` (EcologyConfig, EcologyContent, EcologyHooks, EcologyEvents, EcologyCommand, Predator, Prey,
PreyReaction, PredationService, Hunt, HuntGoal, KillCarcass, KillRecord, KillSites, KillSiteStore, bones/*),
`ecology/client/*` (HuntPose, KillCarcassRenderer, EcologyClient), `tracking/KillSign.java`, `tools/ecology/*`,
patch assets (blockstates, models/block/bones, models/item, textures/block + item, realistic pack textures, sounds),
data (loot tables, worldgen configured/placed feature, biome modifier, biome tag), fragments
`_merge/.../lang/en_us.json/ecology.json`, `_merge/.../sounds.json/ecology.json`.

Shared-file hooks (all marked `// [ecology]`):
* `wildlife2026/WildlifeMob.java`: state constants `STALK..TRAVEL`; `addGoal(2, new HuntGoal(this))`; `hunting()`,
  `ecoState(int)`, `ecoScare(Vec3, LivingEntity, boolean)`; `&& !this.hunting()` on the curious/alert glance and on the
  random feed/rest roll; `EcologyHooks.yields` in `hurt`; ResponseGoal.canUse excludes hunt states.
* `wildlife2026/client/WildlifeRig.java`: `Input.eco[5]`; `dy += HuntPose.rig(m, in, s.rx, s.ry, s.rz);` before the
  matrices. `WildlifeRenderer.animate`: `HuntPose.weights(e, in, now);`. `WildlifeModel.setupAnim` end:
  `HuntPose.classic(...)`.
* `hunting/Whitetail.java` downed tick: `if (EcologyHooks.carcassGone(this, this.downTicks)) { discard(); return; }` and
  `&& !EcologyHooks.predatorKill(this)` on the 36000-tick discard.
* `tracking/TrailMark.java` `lifetime()`: FUR / SCRATCH → 2 days.
* `HuntConfig.java`: `EcologyConfig.server(var1);` after the perf line.

## Config (server, `[ecology]`)
`predation` (true), `huntRate` (1.0), `killsPerAreaPerDay` (2), `carcassDays` (2.0), `scavengers` (true), `howls`
(true), `boneSites` (true), `boneSiteFrequency` (1.0), `killBones` (true). Nothing on the settings screen.

## Commands (op 2)
* `/frontierhunts ecology hunt [success|fail|howl]` — nearest predator (64 blocks) hunts the best prey near it now
  (pack recruited; `success`/`fail` fixes the outcome; `howl` = wolves rally and howl first).
* `/frontierhunts ecology kill <wolf|coyote|cougar|panther|lion|cheetah|grizzly|black_bear|polar_bear>` — the nearest
  prey animal within 32 blocks is found killed by that predator (carcass + sign).
* `/frontierhunts ecology status` — running hunts with phase, nearest predator hunger/mood, kills here today, sites.
* `/frontierhunts ecology hunger <0..3>` — nearest predator's hunger (≥ 0.55 hunts on its own).
* `/frontierhunts ecology age <hours>` — kill carcasses within 32 blocks become older (48 h → bones).
* `/frontierhunts ecology bones <whitetail|elk|moose|bison|pronghorn|shed|remains> [kill]` — lay a bone site here.

## Offline checks
`tools/ecology/harness/.../EcologyHarness.java` (compile against `/tmp/claude-0/cc-ecology` + cp62; run with the
merged jar for the meshes): for all 9 predator meshes × ultra/bal × stalk/tear/howl at weight 0.35 and 1, the posed
paw tips stay within 3% of body height of their standing position, no NaN in any matrix; odds/appetite rules (lone
wolf never takes moose, wolves never a healthy bison, odds < 0.6); KillRecord NBT round trip, aging, clamping.

## IN-GAME TEST SCRIPT
Setup: creative world, `/gamerule frontierHunting true` (prints/fur show; blood always), `/gamerule frontierTracking
true`, survival for observing at 20-40 blocks (creative players are ignored by wildlife senses).
1. **Wolf pack hunt.** Open field near trees. `/summon frontierhunts:whitetail ~20 ~ ~` ×4,
   `/summon frontierhunts:wolf ~-15 ~ ~` ×3. `/time set 12500`, back off ~25 blocks. `/frontierhunts ecology hunt howl`:
   wolves stop, raise muzzles (half-sitting), chorus heard; then walk in head-low; the deer stare/stamp, then bolt
   tail-up (herd too); wolves gallop after them. Repeat a few times: most chases end with wolves dropping back.
   `/frontierhunts ecology hunt success`: a wolf lunges, the deer collapses (bleat), the pack trots to the body and
   feeds (heads down, tugging, growls). After ~2 min they walk off. No sliding feet at any point.
2. **Read it.** Walk up: matted blood bed where it was caught, scratch line to the body, hair tufts, wolf prints. Aim
   at a hair tuft + use → `WOLF KILL · SHED FUR` panel. Use the carcass (empty hand) → 3 lines (pack of 3, fresh,
   partly eaten). Skinning knife → a little venison, "no trophy"; again → "nothing worth taking".
3. **Cougar ambush.** `/summon frontierhunts:cougar ~-20 ~ ~`, deer at `~10 ~ ~`. `/frontierhunts ecology hunt`: low
   crouched stalk (belly near the ground, tail low with twitching tip, paws planted), then either the deer blows and
   runs (cat stands up and leaves) or a 2-3 s rush and a leap onto it. Try Classic/Balanced/Ultra animal styles.
4. **Cheetah / lion / coyote / bear.** Pronghorn + cheetah by day (very fast sprint); lion + bison
   (`/summon frontierhunts:bison`, set one weak: `/data merge entity @e[type=frontierhunts:bison,limit=1,sort=nearest] {Health:20f}`);
   adult bison may charge the lions (lion backs off). Coyote + `/summon minecraft:rabbit`: stalk and high pounce.
   Moose + 3 wolves: the moose usually turns and stands (stance/stomps), wolves circle and leave.
5. **Wildlife carcass.** `/frontierhunts ecology kill wolf` next to a pronghorn: it tips onto its side (correct species
   model in all three looks), sign around it; use it to read.
6. **Scavenger.** `/summon frontierhunts:grizzly ~10 ~ ~`, `/frontierhunts ecology hunger 2`, `/frontierhunts ecology
   age 2` (scavengers wait until a kill is over an hour old), wait up to ~30 s: the bear walks to the carcass and feeds; reading lists "grizzly".
7. **Decay.** `/frontierhunts ecology age 47`, wait 1 s: carcass gone, bones in its place (antlered skull for a buck,
   fresh greasy texture), inspect them. They crumble away over weeks (random ticks).
8. **Natural bone finds.** `/frontierhunts ecology bones elk`, `... moose`, `... bison`, `... whitetail`, `... shed`,
   `... pronghorn` on grass: check models in Classic and with the realistic world pack, sunk into the grass, mossy
   variants, facing variety; break an antlered skull → item (16 px icon) placeable again; bare skull → bones. New
   chunks: fly through forests/plains for a while (`/locate` not available): roughly one site per ~18×18 chunks; never
   in villages/structures or water.
9. **Caps.** `/frontierhunts ecology status` after two kills: `2/2`; no natural hunt starts in that area until the
   next day. Shoot a rifle several times there (pressure > 0.55): no hunts start nearby.
10. **Multiplayer / dedicated server.** Two clients see the same chase, feeding pose and carcass; restart the server:
    carcasses, kill records (reading), bones persist; no client classes on the server (check_jar clean).

## Notes for the coordinator
* Howl audio is synthesized (spectrogram looks like a real howl: pure fundamental 270-660 Hz with weak harmonics,
  rise/hold/fall and breaks); if it does not convince by ear, point `wolf_howl`/`wolf_chorus` in
  `_merge/.../sounds.json/ecology.json` at `minecraft:entity/wolf/howl1` etc.
* The carcass stand-in is rolled about the body axis and lifted by 0.42 × species width; tune in
  `KillCarcassRenderer` if a body floats or sinks.
* `check_jar` only sees two block ids in loop-registered code; lang keys for all six blocks are in the fragment.
