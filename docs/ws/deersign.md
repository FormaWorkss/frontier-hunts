# Workstream `deersign` — rubs and scrapes only where a buck actually makes them, and you can watch him do it

Branch `deersign` (clone `/home/claude/work/deersign`, from master 1650414). `tools/compile.sh` exit=0;
`tools/build.py deersign-test` builds; `check_jar.py <jar> . --base master` → 0 errors, 0 warnings;
`tools/deersign/run.sh` → 51 checks, 0 failed; dedicated-server smoke (below). Not launched with a client here:
**awaiting in-game verification**.

User: "I didn't see any rubs on trees … The deer should actually scrape there for it to show."

## Why nothing showed before
1. There was never any worldgen sign, but the rut calendar only opened marking on **October 1** (`Rut` pre-rut), and a
   new world starts on **September 1** — a whole reserve month with no sign at all.
2. The old `BuckSign` walk was issued once a second from `rutWork`, while the priority-3 routine goal re-pathed the
   deer every tick, so bucks rarely reached a tree; the mark then popped in instantly with a head-bob.
3. `/frontierhunts sign rub|scrape` created sign instantly with no deer involved.

## What it does now
- **Only deer make sign.** The single creation path is `sign.work.SignWork` (server): a buck picks a small natural tree
  (rub; trunk height limit by age: yearling ≤6 logs, 2.5 yr ≤9, mature ≤14) or a spot under a tree's canopy (scrape),
  or a scrape near him (revisit), walks there (new priority-1 `SignGoal`, owns MOVE/LOOK/JUMP so routine/forage/stroll
  wait), glides onto the exact spot square to the trunk/branch (legs walk it, `move()` with collisions), then works it.
  `DeerSign.leave()` now only makes beds; the command makes a buck walk over and do it.
- **Rub** (6–15 s, longer for mature bucks): smells the trunk (~1–1.7 s), then bouts of up-and-down strokes with the head
  twisting side to side (bouts 1.6–3.4 s, 0.8–1.5 strokes/s, short pauses where the head eases off). The rub block is
  placed when he starts; the decal **grows stroke by stroke** (`Mark.workFrom/workTo`); spooked mid-rub = a part rub
  (`cap`, reported when read). Height: the band is computed from **this buck's own sculpted head + rack**
  (`SignGeometry.contact`, `UltraFightModels`, built off-thread while he walks) ± his stroke amplitude, stored in the mark
  (`lo/hi`) and drawn exactly there. Per frame each client poses its preset so the antlers touch the bark at that height:
  Ultra: neck reach to a head target (`FightPose.reach`), Vanilla: box-head nod; and slides the drawn body so the rack
  meets the bark of the trunk it draws (round stem via `SignTrunkProbe`, or the block face). Bark chips (log + stripped-log
  particles) and an antler-on-bark rasp on every stroke.
- **Scrape** (~12–18 s): smells the spot, **paws** with alternating front hooves (lift, fold, reach, strike, drag back;
  soil + leaf-litter particles, hoof scuff + block step sound; the pawed patch grows), works the **licking branch** with
  head and neck raised (nuzzle/twist/bob; rustles, leaf bits, the tip snaps — chewed tip only after he worked it; the
  branch tip is placed where this buck's nose reaches), then **rub-urinates** (hind legs drawn under, hocks together and
  rubbing, rump lowered so hooves stay planted; a few drips, quiet trickle). Revisits run the same sequence on an
  existing scrape and freshen it while pawing.
- **Season** (`SignSeason`, reserve calendar): whitetail rubs from velvet shedding ~Sep 3 (30 %→100 % through Sept), peak
  October pre-rut, tailing through the rut to mid-Dec; scrapes from late Sept, peak late Oct–early Nov, drop in peak rut.
  Maturity: ≥40 mo 100 %, ≥28 mo 60 %, ≥16 mo 33 %. Elk/moose rub from their own velvet shed (mid/late Aug), no scrapes.
  A buck looks every 12–22 s while calm on his feet; after a rub 45 % chance of another within 10–25 s (rub line), else
  45–115 s rest. Monte Carlo (harness): mature buck **~6–7 sign per 10 active minutes in October**, ~4 in early September.
- **Multiplayer**: the server decides and moves the deer; one synced `CompoundTag` (`Whitetail.SIGN_WORK`, set once per
  act) carries kind/start/sign pos/seed/phases; every client derives the identical pose, strokes, sounds and particles
  from it (no per-stroke packets). Sign block state syncs via the existing block-entity update tag (new fields clamped).
  No client classes in common code (check_jar e1–e3 clean; smoke class-load list empty).

## Files
New: `sign/work/{SignAct,SignMotion,SignPlan,SignSeason,SignGeometry,SignPose,SignShapes,SignWork,SignGoal,SignSounds}.java`,
`sign/client/SignWorkClient.java`, `patch/assets/frontierhunts/sounds/deersign/*.ogg` (19, synthesized by
`tools/deersign/gen_sounds.py`), `patch/_merge/.../sounds.json/deersign.json`, `patch/_merge/.../lang/en_us.json/zz_deersign.json`
(subtitles + Field School sign lesson p4–p6; `zz_` so it wins over guide.json's p4), `tools/deersign/{run.sh,harness/*}`,
previews `docs/ws/deersign/*.png`. Deleted: `hunting/BuckSign.java`.

Shared-file hooks (all marked `// [deersign]`):
- `hunting/Whitetail.java`: `SIGN_WORK` accessor + define, `signWork()/setSignWork()`, `SignPose.input(this, var1)` at the end
  of `animatorInput`, `addGoal(1, new SignGoal(this))`, `rutWork()` sign block replaced by `SignWork.rutCheck(this, var1)`.
- `hunting/DeerAnimator.java`: `SignPose.legs(...)` before the final pose; Input fields `signPhase/signAge/signDur/signSeed/signW/signPitch/signYaw/signDrop`.
- `client/McAnimalPose.java`: leg pitch `+= SignPose.boxLeg(var1, var10)`; head `var24 -= signPitch; var23 += signYaw; var27 += signDrop`.
- `client/WhitetailRenderer.java`: `SignWorkClient.prepare(...)` after `RutFightClient.prepare`; `SignWorkClient.slide(...)` after
  the body yaw in both render paths.
- `hunting/DeerSign.java` (sign ws's class): `findSites`, `leave` beds only, Mark fields `lo/hi/workFrom/workTo/cap/lickY/lickF/lickAt`
  (+save/load/onLoad, `progress()`), never-begun sign removed on tick, partial-rub report.
- `sign/client/DeerSignRenderer.java`: worked band, progressive reveal, branch tip from the mark, unchewed leafy tip until worked.
- `sign/SignCommand.java`: commands below.

No config or settings-screen entries (particle counts follow Effects quality: Performance ½, Cinematic 1½).

## Commands (op)
- `/frontierhunts sign rub` / `scrape` — the nearest buck (48 blocks; scrape: whitetail) walks to the nearest suitable tree
  to YOU (10 blocks) and rubs it / paws a scrape under it now (season and age ignored). No sign appears until he works it.
- `/frontierhunts sign revisit` — nearest whitetail buck works the scrape nearest you (12 blocks).
- `/frontierhunts sign status` — nearest buck: idle/resting/next look, or walking/settling/phase + seconds; season × maturity rates.
- `/frontierhunts sign age <days>` / `fresh` — unchanged.

## In-game test script
Mature buck: `/summon frontierhunts:whitetail ~4 ~ ~ {deer_traits:{schema:2,buck:1b,age_months:54,frame:70,condition:80,rack_genes:80,seed:11,abnormal:0,coat:0}}`.
Survival or creative (creative players don't alarm deer). `/time set day`.
1. **Ultra preset.** `/season set fall early`. Stand next to a small natural birch/oak (1×1 trunk, natural leaves), buck
   within 20 blocks. `/frontierhunts sign rub` → chat names the tree. The buck walks over, slows, turns square to the
   trunk, lowers his nose to it (~1 s), then rubs 6–15 s: head low, rack against the bark, up-and-down strokes with
   twisting, pauses; a rasp + bark/pale-wood chips on every stroke. The rub appears at the antler height from the first
   strokes and grows; at the end he raises his head and walks on. Rack touches the bark — no gap, no rack inside the trunk
   (side and top views). `sign status` during it: `working: rubbing x/y s`.
2. Repeat 1 on **Vanilla**: box head nods/turns with the strokes, box rack on the log face, rub on that face at the same height.
3. Spook test: during a rub sprint at him in survival → he bolts at once, the rub stays partial; read it: `only partly rubbed`.
4. **Scrape.** Near a tree with leaves 2–7 above the ground 1–2 blocks out: `/frontierhunts sign scrape`. He walks under
   the branch facing the tree, smells the ground (head down), paws (front hooves alternate: lift, reach, strike, drag back;
   dirt and leaf bits fly back, scuffs), the pawed patch grows; then head and neck up working the licking branch (the
   branch tip is at his nose, rustling, leaf bits, a snap; tip turns chewed/pale), then hind legs drawn under with hocks
   together (a few drips, quiet trickle). Check on Ultra and Vanilla.
5. `/frontierhunts sign revisit` near that scrape → he works it again; after pawing it is fresh again (dark soil).
6. **Natural.** `/season set fall early` or `/season month 10`, follow a mature buck through woodland in daylight: within a
   few minutes (`sign status` shows `next look in`) he goes to a tree and rubs, often another rub soon after (rub line);
   in late October scrapes and revisits too. `/season set summer mid` (or `/season month 7`) → `sign status` rates 0, no sign.
   Yearlings (`age_months:18`) mark about a third as often.
7. **New world** (or any): no rubs/scrapes anywhere a buck hasn't made one; none appear by worldgen.
8. **Dedicated server + 2 clients**: B watches A's commanded rub: same strokes in time, hears the rasp, sees the chips;
   B on Vanilla, A on Ultra: each sees its own model touching its own trunk. Rejoin mid-rub → pose resumes from the
   synced start. No client classes on the server (smoke log clean).
9. **Robustness.** (a) Two bucks, `sign rub` twice quickly near one tree → the second gets
   `another buck is already working there` (or picks another tree); never two rubs on one trunk at once.
   (b) Tree with a low canopy (oak, leaves at 2 blocks): `sign rub` → he walks under and rubs; antlers may poke into
   leaves, he never ends up inside the trunk. (c) Tree behind a fence: he gives up within ~7 s (`sign status` idle ·
   resting), no sign. (d) Mid-rub `/tp` far away (>10 chunks) and back → partial rub, buck idle, no stuck pose.
   (e) Mid-rub `/kill` the buck → part rub stays, no errors in the log.
10. Shaders on/off (Iris + Photon): particles are vanilla block particles; decal unchanged from `sign`.
11. Performance: 5 bucks rubbing in view — per buck per frame one contact pass over the rack points (~0.1 ms); no
    server spikes (site search once per decision; head geometry built off-thread).

## Offline harness (`tools/deersign/run.sh`)
Real classes from the jar + src: stroke/paw motion, SignAct sync round-trip and malformed-tag rejection, phase order and
durations (rub 6.1–15.0 s), simulated walk→settle→work→done and abort, season Monte Carlo, ray vs round stem / block
face / turned block, rub band per species and age from the real meshes, **contact height through a whole stroke on
Ultra and Vanilla for all species** (≤6 cm Ultra, ≤12 cm Vanilla), hooves on the ground while pawing / rub-urinating.
Previews: `rub_ultra.png`, `rub_vanilla.png`, `paw.png`, `lick.png`, `urinate.png`.

## Robustness (second pass)
- **Low canopy**: the stand point needs room only up to 1.7 blocks (antlers / back push through leaves); when the
  path ends short under a canopy he settles from ≤1.8 blocks, pushing through leaves only (never a trunk, wall or
  fence). Settling that times out >0.3 blocks short aborts (`could not reach the spot`) — never a rub on thin air.
- **Stuck walk**: re-path every 1.5 s (0.5 s after a path ends short; no per-tick path searches); no progress for 7 s
  or past the walk deadline → gives up, rests.
- **Interrupted**: alarm / call / fight / bed / goal taken → abort; a rub begun stays a part rub, one barely begun is
  removed. **Chunk unload / death / dimension change** (`EntityLeaveLevelEvent`) aborts the same way; the abort never
  loads a chunk (a sign in an unloading chunk is capped by `Mark.onLoad`).
- **No duplicates**: a buck won't start on a spot within 2 blocks of another buck's job; two bucks heading for one
  tree → the second fails at `make` (site no longer free) and rests. `sign rub|scrape` reports why no stand point fit.

## Limits / notes
- Not run with a client. Sounds are synthesized (5 events, 19 variants) — judge them in game; volumes in `SignWorkClient`.
- Ultra contact uses the trunk drawn at the rub's height; the server's band assumes a 0.12-block stem radius (16 for
  elk/moose) — on thicker stems the client slide still makes contact at the stored height.
- Vanilla box necks cannot reach a high licking branch exactly; the box head points up at it.
- A buck reloaded mid-work (server restart) leaves the part sign he made; the act itself is not saved.
