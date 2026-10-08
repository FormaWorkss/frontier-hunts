# Workstream `academy` — Ranger Academy: training grounds dimension, rebuilt assignments dossier, assignment fixes

Branch: `academy`. `tools/compile.sh` → exit=0. Jar `python3 tools/build.py academy-test /home/claude/work/academy` builds;
`tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Offline harness `tools/academy/harness/run.sh` → ALL PASS
(130 checks: start/finish for every course, abandon, death, disconnect, restart, stale backup after a crash, lost record,
stranded visitor, forced exit by another teleport, failed teleport rollback, double start refused, course definitions).
Art reviewed as rendered (`docs/ws/academy/*.jpg`, icons.jpg). **Not run in-game here** (no client) — the test script below is still to do.

## 1. Assignment audit (offline table — every assignment, how it completes)
| Assignment (id) | Accept | Progress from | Completes | Reward | Cancel | Was broken? / fix |
|---|---|---|---|---|---|---|
| Field patrol (`field_patrol`) | dossier → Accept | `Whitetail.harvest` (your shot, your dressing, after accept) | 1 harvest | 40 tokens, 60 XP | Abandon | **needed `frontierHunting` gamerule**: harvests only counted in an "active reserve". Now any Overworld survival game. |
| One animal, one trail (`trail_reading`) | ✓ | `TrailService.inspect` of a mark created after accept, same animal | 3 marks | 15 / 35 | ✓ | same gate removed (blood marks from wounded animals exist in any world; prints need the reserve rule — said in the dossier) |
| A measured shot (`clean_recovery`) | ✓ | harvest with HEART/LUNG/DOUBLE_LUNG/CHEST shot region | 1 | 60 / 80 | ✓ | briefing claimed "field bow" only — any qualified weapon counts; text fixed |
| A short field window (`before_nightfall`) | ✓ | harvest within 12000 ticks of accepting (expires → FAILED, can re-accept or cancel) | 1 | 65 / 80 | ✓ | gate removed |
| The old timber bucks (`trophy_survey`) | ✓ | harvest ≥ minimum mass | 1 | 120 / 120 | ✓ | **practically impossible**: 120 kg needs a ~0.7 % giant-frame whitetail. Now 110 kg (big mature buck, or any elk/moose bull — the record never checked species, now the text says so) |
| Supply the patrol (`patrol_provisions`) | ✓ | `ContractMenu` delivery (4 roasted venison or 2 leather) | 2 | 20 / 30 | ✓ | **board refused all trades outside the reserve** → `ContractMenu` copied, gate = Overworld |
| Collect reward | READY → Collect | — | tokens + Ranger XP, cooldown starts | | | ok |
| Certification "Steady hold" | | | | | | **did nothing**: `fatigueMultiplier` was never called. Now `BowHold.strain` (server shot spread + client sway) × 0.65 |
| Certification "Field dressing" / "Trail ages" | | | | | | worked only in the reserve → now everywhere |
Certifications are now **earned by passing their academy course** (Range → Steady hold, Field Dressing → Field dressing,
Blood Trail → Trail ages). Old saves keep bought certifications; the 20-token respec is retired (packet ignored); the load
check `points ≥ 0` was relaxed so certifications without points never invalidate the ledger.

## 2. Ranger Academy (training grounds)
* Dimension `frontierhunts:training_grounds` (data JSON): flat (bedrock/stone/dirt/grass, ground y 20), own biome (no
  precipitation, no spawns, warm grass/fog colours), `fixed_time` 11650 (golden hour), `natural=false` (no seasonal snow),
  no beds/anchors/raids. Weather cycle skipped by `AcademyWeatherMixin` (common mixin on `ServerLevel.advanceWeatherCycle`),
  WeatherDirector skips it, so it never rains/darkens. A normal dimension for Distant Horizons / Xaero.
* Each session gets its own plot (slot n at x = 4096 + 1024 n, z = 0), built once per slot from code with the mod's own blocks
  and tree generator (`WildTrees`), reused afterwards (`TrainingStore` slot table; version bump → new slots). Chunk region
  tickets keep the plot entity-ticking for the session (non-persistent). Up to `maxSessions` hunters at once.
* Courses (curriculum order) — `academy/Course.java`, one kit class each:
  | # | Course | Plot | Objectives (server-counted) | Rewards (first pass) |
  |---|---|---|---|---|
  | 1 | Glass & Call | lookout tower over a meadow, buck + 2 does (AI, fenced) | find 3 deer through binoculars (hold ~0.7 s each, LOS), range the buck, call him in to ≤25 m (grunt/bleat → `approachCall`, nudged) | 60 Ranger XP, 90 Stalking XP |
  | 2 | The Stalk | open meadow with broken cover, feeding doe ~75 m | within 15 m while alertness < 0.45, hold 3 s; winded/seen (alert ≥ 0.72 or fleeing) → "busted", dip to black, back to the start mark, fresh doe, attempts counted | 70 / 120 Stalking |
  | 3 | The Range | covered firing line, paper targets (mod's ShootingTarget) 25/50/100 m, berm, wind flags (banners turn across the wind), 3D lanes with live NoAI whitetails at 50/100 m | 3 hits @25, 2 in the blue @50, 1 hit @100, heart/lung shot on each lane (drop + kill cam; other hits called out: "Gut — it would run…", lane resets). Only from behind the firing line. Steel ring per hit, delayed by sound travel | 90 / 140 Marksmanship + **Steady hold** |
  | 4 | The Blood Trail | pine woodland; scripted liver-blood trail (~75 m of real `TrailMark`s: impact, dense, drips with gaps, bed + pool) to a downed buck | read hit site, read 4 more marks, find the bed, recover the buck (≤3.5 m) | 80 / 120 Tracking + **Trail ages** |
  | 5 | Field Dressing | ranger camp (lean-to, fire, game pole), downed buck + lung blood | read the blood, dress it with the Contour Skinning Knife (the mod's own skinning; in training it yields nothing) | 60 / 80 Butchery + **Field dressing** |
  Repeat passes pay a quarter once per `repeatRewardHours` (default 24 in-game h), otherwise "practice run". Creative = practice.
* **Save / restore** (`TrainingFlow`, `HomeState`): inventory incl. armour/offhand, selected slot, XP, health, absorption,
  food/saturation/exhaustion, effects, game mode, dimension + position + rotation, fire/air/freeze/fall are captured into the
  player's own `PlayerPersisted` data (saved atomically with the player file and kept through the death clone) **and** the
  `frontierhunts_academy` SavedData backup. In training: inventory emptied, effects off, Adventure mode, healed/fed, course
  kit lent (each stack tagged `frontierhunts_academy_lent`, lore "Range issue"). Restored on: pass, leave, timeout, death
  (cancelled in the grounds → sent home), disconnect / crash / restart (next login), respawn, or leaving by another
  mod's teleport (items/stats back, position kept). A backup is only used if the player is inside the grounds without their
  own record; a backup older than the player file is discarded (no double restore → no dupes).
* Sealed grounds: no block breaking/placing, no block use except pulling arrows from targets, no item drops (tossed items
  return to the inventory), item/XP-orb entities never spawn there, no non-academy mobs, no player damage (except /kill),
  hunger/nutrition/body heat frozen (Survival hook), no journal credit (journal hook; rewards are paid once home), no
  campaign/ledger credit for the scripted sign/carcass. Lent gear found anywhere outside (ItemEntity join or inventory sweep
  every 5 s) is removed. Leftover academy entities after a crash are discarded when their chunk loads.
* Network: `academy_action` (sync / begin / leave; server-side validation, 8-tick rate limit, only for yourself), `academy_state`,
  `academy_hud`, `academy_cue` (all reads clamped). Start checks: enabled, grounds present, alive, not riding/sleeping/flying-
  falling, not hurt in the last 5 s, not already training, slot free.
* Presentation (client, vanilla GUI, shader independent): departure fade with "Heading to the training grounds · …",
  arrival title card (course name, gold rule, tagline, objectives, time), field card top-left (eyebrow, live timer
  amber/red, objectives with ticks and bars, wind arrow relative to view + m/s + compass, firing-line warning, attempts,
  hold-to-leave ring), gold objective banners, coaching lines, stalk "busted" dip, COURSE PASSED stamp with sparks and
  shimmer + new-best chip, TIME'S UP stamp, return fade and result card (rewards, certification, "everything as you left it").
  Original synthesized sounds: steel ring / bullseye ring, tick, passed fanfare, arrival, whoosh, deer snort, page.
  Field School card steps aside while training.

## 3. Rebuilt assignments dossier (`client/AssignmentScreen`, same class/ctor/`refresh()`/`send(0,"",0)`)
Leather dossier in the journal style: sidebar with RANGER SERVICES brand, tokens/XP, 3 certification medals (tooltip),
grouped list (Ranger Academy / Field work / Bounties / Supply runs) with custom painted icons and status chips (TRAINING,
CERTIFIED, PASSED, NEW, ACTIVE, READY (pulsing), FAILED, cooldown clock), animated sliding selection, hover, scrollbars,
"Hunter's Journal" back button. Page: painted illustration (taped photo), kicker, title, status line, briefing, objectives
checklist with live bars (assignment count/timer; course progress from the HUD while training), reward chips, kit/how-it-works,
fixed action bar (Begin training / Leave training (2-click) / Accept / Collect reward / Abandon (2-click)), server refusals as
a toast. Keys: ↑/↓/Tab select, Enter primary, PgUp/PgDn, wheel, Esc or K close. Lays out from the window (tested sizes in
reasoning: 427×240 → sidebar 145, plate 75 px high; content scrolls).
Art: `tools/academy/gen_art.py` (11 plates 768×384, Field School watercolour engine) + `tools/academy/gen_icons.py` (atlas).

## Files
New: `src/.../academy/{Academy,AcademyCommands,AcademyConfig,AcademyNetwork,AcademySounds,Course,CourseKit,DressingKit,GlassingKit,
HomeState,Plot,RangeKit,Scenery,Session,StalkKit,TrackingKit,TrainingFlow,TrainingService,TrainingStore}.java`,
`src/.../academy/client/{AcademyClient,DossierArt,TrainingHud}.java`, `src/.../client/mixin/AcademyWeatherMixin.java`,
`patch/data/frontierhunts/{dimension,dimension_type,worldgen/biome}/training_grounds.json`,
`patch/data/frontierhunts/frontierhunts/assignment/{trophy_survey,patrol_provisions,clean_recovery}.json` (overrides),
`patch/assets/frontierhunts/textures/gui/academy/*.png`, `patch/assets/frontierhunts/sounds/academy/*.ogg`,
fragments `lang/en_us.json/academy.json` (208 keys, `tools/academy/lang_en.py`), `sounds.json/academy.json`,
`frontierhunts.client.mixins.json/academy.json` (`{"mixins":["AcademyWeatherMixin"]}`), tools under `tools/academy/`.
Replaced (owned): `client/AssignmentScreen.java`, `progression/{Assignment,AssignmentNetwork,AssignmentProgress,AssignmentService}.java`.
Copied from dec62g8 + edited (`// [academy]`): `camp/ContractMenu.java` (2 gates), `expedition/BowHold.java` (1 line).
Shared-file hook lines (all `// [academy]`):
* `HuntConfig.java`: `var1.push("academy"); …AcademyConfig.server(var1); var1.pop();` after journal; client the same after survival.
* `hunting/Whitetail.java` harvest(): `if (Academy.dressedInTraining(var1, this)) { this.dressingHunter = null; return true; }` after
  `entityData.set(DRESSER, -1)`; `AssignmentService.harvest(...)` moved out of the `HuntRules.active` block (ledger stays inside).
* `tracking/TrailService.java` inspect(): `boolean academy = Academy.signRead(var0, var5); if (!academy) ExpeditionService.record(...)`;
  `AssignmentService.trail(var0, var5)` moved out of the reserve block.
* `journal/JournalService.java` record(): `if (Academy.journalBlocked(p)) return null;`
* `survival/SurvivalService.java` update(): frozen early-return when `Academy.survivalFrozen(p)`.
* `weather/WeatherDirector.java` weatherLevel(): `&& !Academy.weatherBlocked(level)`.
* `guide/client/ObjectiveCard.java` busy(): `|| AcademyClient.training()`.

## Config
Server `[academy]`: `trainingGrounds` (true), `maxSessions` (24), `repeatRewardHours` (24). Client `[academy]`: `cinematic`, `trainingHud`.
**Not added to FrontierSettingsScreen** (shared file) — coordinator may add two toggles in INTERFACE:
`toggle("Training card", …, AcademyConfig.HUD, …)`, `toggle("Academy cinematics", …, AcademyConfig.CINEMATIC, …)`.
Keys: **K** Ranger assignments (new), **Backspace (hold 1.5 s)** leave training.

## Commands
`/frontierhunts academy` (status) · `academy begin <glassing|stalk|range|tracking|dressing>` · `academy leave` ·
op: `academy return <targets>` (force home + restore) · `academy complete` (finish the running course's objectives) · `academy reset <targets>`.

## IN-GAME TEST SCRIPT
Setup: survival world (normal Overworld; `frontierHunting` NOT required), op. Put recognisable items in every slot, armour on,
an effect (`/effect give @s speed 600`), some XP (`/xp add @s 5 levels`), lower health/hunger, note your position.
1. **Dossier**: press K (or Journal → Assignments). Leather dossier, Ranger Academy group (5 courses, NEW chips), Field work /
   Bounties / Supply runs. Click rows: selection slides, page fades, plate art, objectives, rewards. ↑/↓, Enter, wheel, PgDn.
   GUI scale 1-4 and an 854×480 window: nothing clips; page scrolls.
2. **Begin**: Range → Begin training. Fade to black "Heading to the training grounds · The Range", ~1 s later arrival:
   golden-hour range, title card, field card top-left (5 objectives, 12:00 timer, wind). Inventory = lent kit with "Range issue"
   lore; rifle loaded; Adventure mode; full health/food; no effects. Log: `built the range plot in slot 0 (... ms)` (first time).
3. **Range**: shoot paper 25 m ×3 (ring sound, gold banners), 50 m two in blue (outer hits say "outside the blue"), 100 m one hit.
   Walk past the firing line and shoot → red warning, no credit. 3D lane 50 m: gut shot → "Gut…" line, lane resets ~3 s; heart/lung
   → kill cam + drop, objective done; same at 100 m. COURSE PASSED stamp + sparks + fanfare, ~4 s, fade, home **exactly** where
   you stood with every item/armour/effect/XP/health/hunger back; result card "+90 Ranger XP · +140 Marksmanship XP · Certified:
   Steady hold"; journal XP ticker appears now (not during training). Dossier: Range CERTIFIED, medal lit.
4. **Leave**: begin Stalk, hold Backspace 1.5 s (ring fills) → fade home, "Training left early", all restored. Also via dossier
   (Leave training, click twice).
5. **Stalk**: walk upright at the doe → "She saw you move"/"winded", dip to black, back at start, Attempt 2. Crouch downwind
   (wind line on the card), get inside 15 m, hold 3 s → passed.
6. **Glass & Call**: on the tower use binoculars on each deer (Spotted ×3), rangefinder on the buck, blow the grunt tube → buck
   walks in; ≤25 m → passed.
7. **Blood Trail**: aim at the blood at the hit site + Use (mod's reading HUD: liver) → objective; follow 4 marks, find the bed,
   walk up to the buck → passed. **Field Dressing**: read the blood, skinning knife on the buck, stay ~8 s → passed; no items drop.
8. **Sealed**: in any course try breaking/placing blocks, opening the campfire, Q-dropping the rifle (comes back), `/kill @s`
   (→ sent home, everything restored, no death screen), lava/fall damage none. Survival HUD meters don't move.
9. **Disconnect**: start a course, quit to title, rejoin → you are back home, gear restored, message "interrupted". Same with
   stopping the server mid-course (dedicated: `stop`), restart, rejoin.
10. **Forced exit**: start a course, `/execute in minecraft:overworld run tp @s 0 100 0` → own items back at that spot, lent gear gone.
11. **Assignments**: accept Field patrol in a normal world, kill + skin a deer with your own shot → READY chip pulses → Collect
    reward (tokens, XP). Trail reading: wound a deer, inspect 3 of its blood marks → READY. Supply: Ranger Contract Board, deliver
    twice (4 roasted venison). Before nightfall: time bar counts down; let it expire → FAILED chip, Abandon/accept again.
12. **Multiplayer** (dedicated + 2 clients): A and B start courses at the same time → different plots (log slot 0 / 1), neither
    sees the other; B's actions never move A's objectives; only the starter can begin/leave their own session; both return
    correctly. `/frontierhunts academy return B` (op) brings B home.
13. **Steady hold**: with the certification, a long full-draw field bow hold sways visibly less; arrows group tighter.
14. DH/Xaero/shaders: training dimension renders normally, golden-hour sky, no rain even while it rains in the Overworld.

## Known limits / for the coordinator
* Not launched in-game; dimension/biome JSON, mixin target (`ServerLevel.advanceWeatherCycle`) and plot builds are verified by
  compile/check_jar and code review only. Plot looks were designed in code, not seen.
* Curios / backpack-mod slots are not part of the snapshot: lent gear moved into them is taken back by the 5 s sweep once it
  reaches the inventory; their own contents are untouched (they are not emptied for training).
* Track prints only exist with the reserve rule; outside it the trail assignment works with blood marks of a wounded animal.
* Settings-screen toggles not added (see Config).
