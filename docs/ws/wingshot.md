# Workstream `wingshot` — ducks and grouse really fly; graphic wing shots

Branch `wingshot` (clone `/home/claude/work/wingshot`, from master 1650414). `tools/compile.sh` → exit=0;
`python3 tools/build.py wingshot-test .` builds; `tools/check_jar.py <jar> . --base master` → **0 errors, 0 warnings**;
`tools/qa/server_smoke.sh <jar> tools/wingshot/qa/smoke.commands` → see "Checks". `tools/gui/run.sh` → LAYOUT OK, every
sound event grouped. **Not run in a game client here** — the in-game test script below is still to do.

User request: *"the duck and grouse should be able to realistically fly, I also want a super cool animation or effect
when u shoot those little guys with a shotgun or whatever, they explode, I want it to be as graphic as u can make it"*.

## 1. Flight (server, `wingshot.Flight` + `wingshot.BirdFlight`)
* `Flight` is a pure point-mass flight model + pilot (no Minecraft types; offline-tested). Heading turns with a rate
  limited by the load the bird can pull at its speed plus an angular-acceleration limit (no snapping), speed tracks its
  target with accel/decel limits, height follows a rate-limited loop (no overshoot/bobbing). Real units: mallard cruise
  0.95 b/t = 19 m/s, approach 12 m/s, touchdown 4 m/s, turn radius ~15 m; grouse 15.6 m/s, very agile.
* Terrain from heightmaps under and ahead (ducks fly over the canopy, grouse under it among the trunks); obstacles from
  three short ray probes a body-width apart; when blocked it scores headings either side (free distance, small
  deviation, toward the goal, stick with the chosen side), brakes, pulls up (ducks) or ducks under a bough / hops a log
  (grouse). Unloaded country counts as a wall; a flight heading off the loaded area turns back.
* **Mallard**: jump take-off off the water ("puddle duck", splash), climbs out away from the threat to ~22 blocks over
  the canopy, flies 90-170 blocks, asks for open water (search ahead), circles it with **set wings** 1-2 laps losing
  height (a few wingbeats now and then), turns onto a final leg **into the wind** (`Wilderness.wind`), cupped wings,
  feet down, splash-down and a short skid. **Flocks**: ducks within 13 blocks go up a beat after the first and hold
  loose **V or echelon** slots (gentle wander), land beside the leader. Shot leader → mates flare and go on alone.
  Spooked in the air → **flare** (turn away and climb). Lured (Duck Call / decoys, `LureGoal`) → the same approach,
  circling and landing on the spread; a flock already coming in is joined.
* **Grouse**: explosive **flush** (roar of wings sound), low and fast through the trees for 20-60 m, wings set for the
  last stretch, glide in, **land running** (flee state on foot). Re-flushes if you walk at it again.
* **Drumming**: spring (Mar-May) and autumn (Sep-Oct) dawn/dusk, a grouse near a player walks to a fallen log (or a
  stump; else the ground), hops up and drums 2-3 bouts (the NPS recordings; the wing pulses are timed to the measured
  beats of each recording), long still pauses; anything alarming flushes it off the log.
* **Ambient**: now and then (mostly dawn/dusk) a duck flock near a player relocates to other water; never off a decoy spread.
* **Movement**: the flight moves the entity with Minecraft's own `move()` (collision) from a `travel` hook — no vanilla
  flying jitter; flying birds use `Pose.FALL_FLYING` with a wider, flatter **hitbox** (duck 0.72×0.32, grouse 0.55×0.28)
  so the spread wings can be hit. Each tick: 2-3 heightmap reads + 1-3 short clips per flying bird.
* **Shot birds**: every pellet is its own wound (no shared i-frames between pellets of one shot); a bird killed in the
  air keeps its momentum plus a shove from the shot and **falls ballistically** (real gravity, drag, glances off trunks),
  floats if it lands on water; its **loot drops where it lands**, it lies ~3.5 s, then the vanilla death. Blood pool on
  landing through the tracking system (`TrailService.pool`). Hurt but alive in the air: flinch; below 50 % health it
  sails down and lands. Hunt milestones/journal unchanged (they read the hit/death events).
* Stranded in the air with no pilot (chunk reload mid-flight) → glides down.

## 2. Animation (client) — both looks
* `BirdAnim` (pure) turns the synced phase byte (`WildlifeMob.FLIGHT`: NONE/TAKEOFF/FLAP/GLIDE/SET/LAND/FLUSH/FALL/
  DRUM_A/DRUM_B) and the measured motion into a pose: real wing kinematics (downstroke 55 % of the cycle fully
  extended and pronated, upstroke with wrist flexed and hand swept back), mallard 5.4 Hz cruise / 6.6 Hz take-off,
  grouse 12-15 Hz; glide, shallow-V set wings, cupped landing with back-pedal strokes; bank from the coordinated-turn
  relation, climb pitch; limp tumbling body and flopping wings when shot, settles on its side (lower and less rolled on
  water); drumming stance (upright, tail braced, wing thrusts on every beat).
* **Ultra**: `WingGeometry` — a real wing skeleton (humerus/forearm/hand) carrying ~68 individual cambered feathers per
  wing (10 primaries fanning/closing, secondaries with the mallard's blue speculum, tertials, scapulars, greater /
  median / marginal / primary coverts, alula), each with its own top and **underside** texture (`textures/entity/
  wildlife/wings/{duck,grouse}.png`, generated), back faces culled. Drawn whenever the wings are open, folding onto the
  flank as they close. `BirdPose`: neck stretched out, feet tucked under the tail or dropped for landing, tail fanned
  (bone scale) and pitched, limp head/feet. Flying/dying birds animate every frame (LOD hold skipped).
* **Vanilla**: the box wings got a hand (`wing_l_tip`/`wing_r_tip`, textures extended to 64×64); same stroke/fold/
  cup/twist, tucked legs, tail. Body attitude (bank/pitch/tumble) for both looks in `BirdRender`.

## 3. Hit effect (client, `BirdFx`)
Server folds all pellets of a shot into one `wingshot_hit` payload per bird per tick → everyone tracking it. Then:
dozens of contour feathers in the species' colours (6 mallard + 6 grouse feather sprites) bursting along the shot and
inheriting the bird's speed, braking hard, ~22 % hanging in the air a moment, fluttering down like leaves and
settling flat (floating on water); long flight feathers tumbling down; puffs of down drifting on the wind; blood mist
sprayed out of the far side; droplets flying ballistically that leave **spatter on the ground, trunks and blocks**
(splats on the face they hit, ~50 s) or a pink bloom on water; impact "thwack" sound; the falling body's thud or
splash with a last puff of feathers. Pooled SoA particles (cap 1600, oldest settled recycled), drawn through the
vanilla particle pipeline (one cutout + one translucent "cloud" particle) → works with Iris/shaders. Budget follows the
setting, Effects quality and Minecraft's Particles option.
**Wing-shot moment** (`WingShotMoment`, shooter only): a clean one-shot kill of a flying bird ≥ 6 m → ~1.2 s local
time dilation: feathers/mist on the slowed clock, the bird's real (server) fall replayed slowed from recorded positions
(stays a fraction of a second behind until down), FOV tightens 10 %, vignette + warm wash, low whoomp. Never slows the
world; off in the kill cam, with Reduced motion or the setting. Birds are excluded from the kill cam.

## 4. Sounds
Synthesized (`tools/wingshot/audio.py`, previews looked at): grouse flush ×3, duck take-off ×3, duck wing loop
(5.4 Hz, whistle), grouse wing loop (12 Hz), splash ×2, pellet strike ×3, body thud ×2, body splash ×2, slow-mo.
Drumming reuses the NPS ruffed-grouse recordings (already credited; note added to `CREDITS-SOUNDS.txt`). Alarm quacks
on a spooked take-off reuse the real `duck_quack`. Wing loops follow the 4 nearest flying birds (≤34 blocks).

## 5. Settings / config
* Client `[wingshot]`: `birdHitEffects` OFF/NORMAL/**GRAPHIC**, `wingShotSlowMo` (true). On the settings screen
  (Wildlife page): "Bird hit effects", "Wing-shot slow motion".
* Server `[wingshot]`: `realisticFlight` (true; false = the old hop and vanilla death), `ambientDuckFlights` (true),
  `grouseDrumming` (true).

## 6. Commands (op 2)
`/frontierhunts birds` (status of birds within 128) · `birds flush` (nearest bird up and away from you) ·
`birds pass [n]` (n mallards spawned 70 blocks to your left, crossing 14 blocks in front of you ~19 up) ·
`birds decoy` (nearest ducks fly in to your decoy spread or nearby open water) · `birds drum` (nearest grouse
displays) · `birds land` (everything comes down).

## 7. Files
New: `src/.../wingshot/{Flight, BirdFlight, BirdLife, BirdHits, BirdNet, BirdCommands, WingshotConfig,
WingshotContent}.java`, `src/.../wingshot/client/{BirdAnim, BirdPose, WingGeometry, BirdWings, BirdRender, BirdFx,
BirdSounds, BirdClient, WingShotMoment}.java`; assets: `textures/entity/wildlife/wings/*.png`,
`textures/particle/wingshot_*.png` (27), `particles/wingshot_fx.json`, `textures/gui/wingshot_vignette.png`,
`sounds/wingshot/*.ogg` (18); fragments `_merge/.../sounds.json/wingshot.json`, `_merge/.../lang/en_us.json/wingshot.json`;
tools `tools/wingshot/{fhsk.py, preview.py, art.py, audio.py, harness/, qa/smoke.commands}`.
Shared-file edits (all marked `// [wingshot]`):
* `wildlife2026/WildlifeMob.java`: `FLIGHT` data byte + `flightPhase()`, `public Object wingshot`, `wingshotThreat()`;
  `BirdFlight.think(this)` at the top of `aiStep`; `BirdFlight.travel` at the top of `travel`; `BirdFlight.startle` in
  place of the two hops (old hop kept as fallback); `!BirdFlight.active(this)` on the glide hack; `beforeHurt/afterHurt`
  around `super.hurt`; overrides `getDefaultDimensions` (FALL_FLYING), `dropAllDeathLoot` (+`wingshotLoot`), `tickDeath`.
* `wildlife2026/client/WildlifeRig.java`: `Input.bird`; `else if (in.bird != null) dy += BirdPose.rig(...)` before the
  old bird branch; `BirdPose.post` after the matrices.
* `wildlife2026/client/WildlifeRenderer.java`: `render` → `BirdRender.push` + `renderBody` (old body); `getFlipDegrees`;
  `in.bird = BirdRender.anim(...)` in `animate`; `BirdWings.build/draw`; LOD-hold and death-rotation guards.
* `wildlife2026/client/WildlifeModel.java` (tip parts, `BirdPose.classic` at the end of `setupAnim`),
  `WildlifeModels.java` (duck/grouse: hand boxes, 64×64 texture).
* `hunts/LureGoal.java` (`start`: `BirdFlight.lure`, `tickFly`: wait then LOAF), `hunts/HuntEvents.java` (`flare` →
  `BirdFlight.flare`), `killcam/KillCamPredictor.eligible` (birds excluded), `HuntConfig.java` (2 hook lines),
  `client/FrontierSettingsScreen.java` (2 rows), `sound/FrontierSoundCategory.java` (groups),
  `patch/.../textures/entity/wildlife/{duck,grouse}.png` (now 64×64, top half unchanged), `CREDITS-SOUNDS.txt` (appended).

## Checks (offline)
* `tools/wingshot/harness/run.sh` — flight harness on a synthetic world (hills, lakes, a 5.5-block trunk forest under
  a canopy, a cliff): **ALL PASS (896 checks)**: 40 spooked ducks all land on water, no collisions, cruise altitude
  hold ±1.6 blocks, no vertical/heading dithering, +20-block climb with no overshoot, 180° turn without overshoot;
  40 lured ducks all land on the spread (worst 0.8 blocks off) into the wind (worst 6°); 6 flocks of 5 never closer
  than 0.7 blocks, land together; shot leader → mates flare and land; 200 grouse flushes through the forest all land,
  13-57 m, ≤ 4.1 blocks up, 8 hard trunk hits / 65 glancing contacts in 200; 30 m dead fall 2.5-3.5 s under terminal
  speed in a forward arc; 300 fuzzed starts (NaN/∞ inputs) stay finite.
* `tools/wingshot/harness/poses.sh` + `tools/wingshot/preview.py` — real meshes posed by the real rig + wings, checked
  for NaN, span/length (duck 1.60, grouse 1.43), rendered from 3 views (fold, 4 stroke positions, take-off, glide/bank,
  set, landing, drum, dead falling / on the ground / on water) and looked at. Art previews: wing atlases, particle
  sheet, box textures; audio waveforms/spectrograms.

## IN-GAME TEST SCRIPT
Setup: creative test world with a pond and a wood, `/gamerule doDaylightCycle false`, `/time set 1000`. Watch in
**survival** for the senses (creative players are ignored). Give `/give @s frontierhunts:double_barrel`,
`/give @s frontierhunts:shotgun_shell 64`, `/give @s frontierhunts:mallard_decoy 8`, `/give @s frontierhunts:duck_call`.
1. **Passing flock** (Ultra): face open country, `/frontierhunts birds pass 5`. A V/echelon of mallards crosses from
   the left ~19 blocks up at ~19 m/s, wings beating ~5 times a second with full strokes (wrist folds on the upstroke),
   whistling wingbeat audible as they pass, banking slightly as they correct. `/frontierhunts birds` shows them
   (flee, phase 2, ~19 m/s).
2. **Wing shot**: shoot one as they cross (lead it). Expect: a puff of 40-60 feathers in duck colours that hangs where
   it was hit while the body folds and drops out of it tumbling, wings limp; blood mist; droplets falling; down drifting
   downwind; slow-motion for ~1 s (FOV tightens, edges darken, whoomp) if it was one clean shot; the others flare and
   climb away. The body lands with a thud (or splash, floating low on its side); loot appears there ~3.5 s later.
   Walk over: blood spatter on the grass/blocks where droplets fell, feathers lying flat, blood sign under the body.
3. **Settings**: Frontier settings → Wildlife: "Bird hit effects" Off / Normal / Graphic; "Wing-shot slow motion".
   Off = only the hurt sound + thwack; Normal = fewer feathers, little blood. Reduced motion → no slow motion.
4. **Take-off**: `/summon frontierhunts:duck` ×4 on the pond, walk at them (not crouching). They jump straight up off
   the water with a splash and alarm quacks, deep fast strokes, climb out away from you as a flock, fly off; some
   time later they circle water and land (cupped wings, feet forward, splash, short skid).
5. **Decoys**: put 6 decoys on the pond, hide 10+ blocks back, use the Duck Call (or `/frontierhunts birds decoy`).
   Ducks come in high, circle the spread with set wings, turn into the wind and splash down among the decoys and loaf.
   Stand up/walk at them on the approach → they flare off. *Over the spread* / *On the wing* milestones still count.
6. **Grouse**: in the wood `/summon frontierhunts:grouse ~6 ~ ~`, walk at it: roar-of-wings flush, a fast low flight
   weaving between trunks for 20-60 m, set wings, lands and runs. Shoot one in the air: grouse-coloured feathers
   (barred brown, buff, black ruff). `/frontierhunts birds flush` repeats it.
7. **Drumming**: near a fallen log, `/frontierhunts birds drum`: the grouse walks to the log, hops up, stands upright
   with tail braced and drums — wing thrusts accelerating with the drum sound. Walk close → it flushes off the log.
8. **Vanilla look**: Animal style Minecraft: box ducks/grouse flap with a two-part wing (hand appears when spread),
   legs tucked, banking; shot birds tumble and lie on their side; no texture errors (64×64 textures).
9. **Shaders**: repeat 2 and 6 with Iris + a pack (Photon/Complementary): feathers/mist/blood/splats lit and depth-
   correct; the feathered wings render (entity cutout); the vignette is drawn in the GUI pass.
10. **Multiplayer** (dedicated + 2 clients): A shoots a passing duck; B (watching) sees the same feather burst, fall
   and splats but no slow motion; flight smooth on both (no jitter/rubber-band); `/frontierhunts birds` from console.
11. **Perf**: `/frontierhunts birds pass 8` four times and shoot into the flocks: frame time steady; F3 particle count
   stays bounded; server tick unaffected (`/frontierhunts birds` status, `/perf` if available).

## Known limits / for the coordinator
* Not launched in a client: wing feather layering, sizes and the stroke amplitudes are tuned from offline renders;
  if a wing reads too big/small adjust `WingGeometry.DUCK/GROUSE`. Synthesized wing sounds judged by spectrogram only.
* The sculpted body keeps its folded wing on the flank while the feathered wings are out (reads as flank feathers).
* Feathers settle on block tops/water; they don't stack on each other or blow around once settled.
* Ducks out of the simulation distance freeze in the air until a player comes closer (vanilla entity ticking).
* `RealVoices` still plays an occasional random drum sound from any grouse (calls workstream); the drumming display
  is separate.
