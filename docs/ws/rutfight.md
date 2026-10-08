# Workstream `rutfight` — locked-antler rut fights that actually touch

Branch `rutfight` (clone `/home/claude/work/rutfight`). `tools/compile.sh` exit=0. Offline harness passes (see below).
Not launched in-game here: **awaiting in-game verification** (test script at the end).

User report: "when deer fight antlers dont actually touch". Cause: the old fight placed the bulls at a guessed reach
(`Engagement.reach`, two constants per species) and played the looping `spar` clip, whose butting moves the rack back
and forth (gap on every back-swing); the Classic preset ignored the spar clip entirely (head stayed up), and the box
models' racks are far bigger than the sculpted ones, so one distance could never be right on all presets.

## What was built

1. **Contact from the real antler geometry** (`hunting/rutfight/`, common code):
   - `FightModel` — one fighter's head for one preset: canonical locked head matrix, antler + skull surfaces sampled
     every 2.2 cm in head-bone space, model→entity scale.
   - `UltraFightModels` — from the sculpted mesh (whitetail: procedural `AntlerGeometry` on the antler frame; elk/moose:
     mesh antler region skinned with the renderer's neck girth / head scale / rack scale). Used by the server and Ultra.
   - `client/rutfight/BoxFightModels` — Balanced (BLOCKY cube model on the deer skeleton) and Classic (VANILLA,
     `McAnimalPose`) from exactly the cubes `ClassicAntlers` draws, with the same rack scaling.
   - `FightFit` — slides the rival in along the shared axis until antler surfaces first touch, searching small
     up/down/sideways head offsets (half each) for the deepest engagement where beams and tines mesh, presses
     `INTERLOCK` 3.5 cm further, and never lets an antler reach the rival's skull (`SKULL_MARGIN`). Works for any two
     sizes (spike vs. 6x6 included). Result: locked feet-to-feet distance, head lifts/sides, contact point, lever.
   - `FightFits` — fits run on `Util.backgroundExecutor()` (0.1–0.6 s per pair) and are cached under one key
     (`preset|traitsA|traitsB`, server = preset 2, so an integrated server and an Ultra client share a fit); nothing on
     the tick or render thread. Ultra head models are built outside the cache lock (a render/tick-thread lookup never
     waits for another animal's build), and each `FightFit` keeps the two models it was solved for, so the renderer
     never rebuilds a model per frame. Box models (client) are built on the render thread (cube data is not thread
     safe); only their solve runs in the background.
2. **Locked posture + exact head reach**: `FightPose` holds the spar clip's lowest frame (no more butting loop) and a
   CCD neck solve (`reach`) moves the head bone to an exact target; what the neck can't reach is returned as a
   residual. `FightAim` builds the per-frame target: lifted to the shared height, twisted/heaved about the contact
   point (both heads turn about the same point, so the racks stay in contact), clash jolt, ground-step share, and a
   slide for any distance error. Classic gets the same target through its rigid head part.
3. **Server fight** (`RutEngine.Engagement`, rewritten): posture → circle → close (heads come down, straight-line walk,
   last stretch a lunge) → **clash** (crack, dust, shared cue) → **locked**: both bodies placed every tick exactly on the
   locked line (A behind the contact by its share, B in front), shove moves the contact point (pusher walks forward,
   pushed backs up), twisting bouts swing the pair round the contact, terrain-blocked moves keep the distance exact →
   periodic **disengage** (back off a step each, pause) and **re-clash** → loser breaks off and runs, winner displays.
   Distance = the nearest watching player's preset fit (`RutFightNet` report, validated: proximity, same fight, band
   check, rate limit), else the server's sculpted-mesh fit.
4. **Sync**: new `RUT_PARTNER` int on Whitetail (rival id), new rut poses 4/5/6 (close/lock/break) in the existing
   `RUT_POSE` byte, clash time in the existing cue timestamp under its own cue id 8 (`RutFightState.CUE_CLASH`; the
   animator maps no clip to it, so a clash never replays the old spar clip, cue 7, after the fight). Clients compute twist/heave from the shared clock.
5. **Client** (`client/rutfight/RutFightClient`): per frame finds the rival, fits the pair for the current preset,
   gives the animator the head target, renders the body facing the rival exactly, slides it by the neck's horizontal
   shortfall, feeds vertical shortfall to the rival so the racks stay level, and reports its distance to the server.
6. **Sounds**: `deer_antler_clash` (4 single clacks) and `deer_antler_grind` (3 locked-rack scrapes) cut from the
   mod's own `antler_rattle` recordings (`tools/rutfight/cut_sounds.py`), pitched down for elk/moose.

## Files
New: `hunting/rutfight/{FightModel,FightFit,FightFits,FightAim,FightPose,UltraFightModels,RutFightState,RutFightNet,RutFightSounds}.java`,
`client/rutfight/{RutFightClient,BoxFightModels}.java`, `patch/assets/frontierhunts/sounds/rutfight/*.ogg`,
merge fragments `patch/_merge/assets/frontierhunts/sounds.json/rutfight.json`, `.../lang/en_us.json/rutfight.json`,
`tools/rutfight/{run.sh,cut_sounds.py,harness/*}`.

Shared-file hooks (all marked `// [rutfight]`):
- `hunting/Whitetail.java`: `RUT_PARTNER` accessor + define, `rutPartner()/setRutPartner()`, last line of
  `animatorInput`: `RutFightState.input(this, var1)`.
- `hunting/DeerAnimator.java`: `fightW` + `FightPose.blendLock(...)` after the cue section; head look x `(1 - fightW)`;
  `FightPose.reach(...)` before the skin loop; `fightResidual`; Input fields `fightLower/fightHead/fightReach`.
- `client/McAnimalPose.java`: species field, `fight` weight, head part pitch/drop, `RutFightClient.classicHead(...)`.
- `client/WhitetailRenderer.java`: `RutFightClient.prepare(...)` after `animatorInput` in `pose()`; body yaw via
  `RutFightClient.bodyYaw(...)` and `RutFightClient.slide(...)` after the scale in both render paths.
- `hunting/routine/RutEngine.java`: Engagement rewritten, partner sync in `tick`, `clash`/`grind`/`report`/`fightInfo`,
  `startFight(..., direct, ...)`. `hunting/routine/RoutineCommand.java`: `rut fight lock|info`.

Config: unchanged (`rutFights` in RoutineConfig still switches fights off). No settings-screen entries.

## Commands
- `/frontierhunts rut fight` — two nearest calm mature bucks/bulls of one species square off (full fight).
- `/frontierhunts rut fight lock` — same, straight to closing in and locking (debug).
- `/frontierhunts rut fight info` — phase, locked distance (mesh fit / player preset fit) and current distance.

## Offline harness
`tools/rutfight/run.sh` — every species x preset (Classic/Balanced/Ultra) x 6 pairs (4 equal sizes 30–100 months
incl. weak and huge racks, a spike vs a monster, two mismatched) x 8 cases (locked, interpolation +0.12/-0.08,
twist±/heave, clash jolt, ground step +0.5/-0.3) = 432 checks. Each pose goes through the real animator (fight layer +
neck reach, or McAnimalPose) and the renderer's geometry from the jar's meshes/cubes. Pass = closest antler-to-antler
distance <= 0.05, rack-in-rack depth <= 0.09, antler/skull clearance > 0. Previews: `rutfight_{classic,balanced,ultra}.png`
and `rutfight_ultra_twist.png` (side | top of two locked bulls per species).

## In-game test script
1. Creative, flat open ground, `/time set day`, `/season set fall late` (peak rut). Graphics preset **Ultra**.
2. Spawn two mature whitetail bucks (spawn egg, buck, >= 30 months) ~8 blocks apart. `/frontierhunts rut fight lock`.
   Expect: both turn to face, walk in heads lowering, last half block a lunge, a sharp antler **crack** + dust at
   contact. Locked: racks meshed (tines between tines), no gap and no antler through a skull, from the side and from
   above. `/frontierhunts rut fight info` shows "locked antlers (locked distance x.xx, ...)".
3. Watch 10–20 s: shoving (one walks forward, the other backs up, legs walking backwards), heads twist and heave
   together while the racks stay in contact, the pair swings round the contact point, grinding/ticking sounds, every
   few seconds both back off a step and crash together again (crack). Ends with the loser turning and running and
   the winner standing displaying/calling.
4. Repeat with `/frontierhunts rut fight` (full fight with posturing and circling first).
5. Repeat 2–3 on the **Balanced** and **Classic** presets (switch in Frontier settings): heads go low, racks meet and
   mesh at the box-model size (bulls stand further apart than on Ultra — the box racks are bigger).
6. Different sizes: a 2.5-year buck vs a big old buck (spawn several, use `rut fight lock` near the pair): the smaller
   rack sits inside/against the bigger one, still touching, no skull poke-through.
7. Elk (`frontierhunts:elk` bulls) and moose: same checks; clash is deeper pitched; big racks twist less.
8. Slope / step: start a fight on a gentle hill or across a one-block step: heads meet at one height (one neck up,
   one down), contact held while shoving up/down the slope.
9. Interrupts: sprint at / shoot near a locked pair: they break and flee; no stuck poses.
10. **Dedicated server, 2 clients**: client 1 Ultra standing close, client 2 Balanced further away. Both see the fight
    in phase; client 1 sees exact contact (server uses the nearest player's preset distance); client 2 also sees racks
    touching (its renderer slides the bodies by the difference). Swap who stands closer: the bulls re-space smoothly.
    Server log: no errors; no client classes loaded on the server.

## Review pass (finishing agent)
Reviewed the interrupted WIP end to end (threading, client/server split, NaN paths, sounds). Fixed: clash cue id
collided with the spar cue (7 -> 8); Ultra model builds held a global lock (render-thread stalls); render thread could
rebuild an evicted Ultra model synchronously (now uses the fit's models); server/client fit keys differed (double
work in singleplayer); Box model cache keyed by a truncated hash (collisions) -> record key. `check_jar` 0 errors;
harness 432/432; sounds (4 clash, 3 grind, mono 44.1 kHz vorbis) present in the jar and referenced by the sounds.json
fragment + subtitles in the lang fragment.

## Known limitations
- Not run in-game here. Mixed presets: only the nearest watcher gets server-exact spacing; other presets' bodies are
  slid visually by up to ~0.9 block (hit detection stays on the true position).
- Fit takes 0.1–0.6 s on a background thread; `rut fight lock` waits (still circling) up to 5 s for it.
