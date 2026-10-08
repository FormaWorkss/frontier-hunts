# Workstream `killcam` — slow-motion lethal-shot kill cam (roadmap item 7)

Branch: `killcam`. Build: `/home/claude/fh/tools/compile.sh /home/claude/work/killcam /tmp/claude-0/cc-killcam` → `exit=0`;
full jar `python3 /home/claude/fh/tools/build.py killcam-test /home/claude/work/killcam` → builds (mixin/lang/sounds fragments merge).
**Not yet run in-game** (no client available here) — everything below the "In-game test script" heading is still to be performed.

## What it does

When the player fires a shot that drops an animal, the camera cuts to a slow-motion replay that follows the bullet or
arrow downrange, swings to a side view as it slows into the final metres, shows the strike with a tasteful blood mist /
droplets / far-side spatter and a hair puff, holds an **X-ray** beat (organs + skeleton + glowing wound channel, card like
`DOUBLE LUNG — 212 YD — LETHAL — CLEAN, ETHICAL SHOT` plus the organ's teaching line), then plays the collapse in slow motion
and glides back to the rifle. Only lethal shots, only the shooter sees it, the world and the server never slow down.

### Authority / timing (multiplayer-safe)
* **Prediction (instant start):** `KillCamServer.join` (EntityJoinLevelEvent) tracks every projectile a real player fires.
  For `RifleBullet` and `FieldArrow` it runs `KillCamPredictor` the tick it spawns: the same per-tick integration
  (`RifleBullet.integrate` / `FieldArrow.integrate` with `Wilderness.wind` per future tick), block clip, and
  `DeerAnatomy.intersect` against the animals' current skinned pose. Lethal = `region.vital() && energy>=0.08 && tip lethal`
  (same rule as `Whitetail.projectileHit`), or damage ≥ health for `WildlifeMob`. A predicted-lethal shot sends
  `KillCamNetwork.Shot(confirmed=false)` to the shooter at once — the camera is moving before the bullet lands.
* **Confirmation (the only way a replay finishes):** hooks in `Whitetail.projectileHit` capture the pre-impact pose and
  confirm only if the hit was accepted **and the deer is downed**; `LivingDeathEvent` confirms `WildlifeMob` kills by a
  projectile. The confirm carries the real sampled trajectory, impact, exit, region/organ and pose snapshot.
  External projectiles (TACZ etc.) take the same confirm path (Whitetail.hurt already re-traces them into
  `projectileHit`); their spawn origin is recorded on join, so the replay starts from the muzzle within a tick or two.
* **Divergence:** miss / non-lethal / different animal / projectile gone → `Cancel` → the client fades out and back
  (0.2 s dip). An unconfirmed replay holds at the final approach and aborts after 2.5 s.
* Never for players (targets must be `Whitetail` or `WildlifeMob`), fake players ignored, min range 6 m, max 420 m.

### Client presentation
* Camera: client `Marker` as camera entity, exact per-frame pose via `KillCamCameraMixin` (Camera.setup TAIL) +
  `ComputeCameraAngles` (roll) + `ComputeFov`. Launch ease from the eye, chase behind/alongside with drift and roll,
  blend to a collision-checked side framing, impact shake, X-ray orbit, drop pull-back, 0.45 s return to the eye.
* The real animal is hidden and a **client-only double** (same entity type → same renderer, species, traits, antlers,
  coat, wound decals) stands in. It never ticks; the kill cam drives its clock (`tickCount`, the deer's `*_AT` data,
  `deathTime`, walk animation) and `KillCamLevelRendererMixin` renders it with the replay's own partial tick, so walking,
  hit reaction and collapse play in true slow motion through the normal entity pipeline (shaders/shadows intact).
  Near the end it glides onto the real downed deer and swaps (wildlife: vanilla death puff).
* Bows: a client `FieldArrow` double with the real tip. Firearms: a lit, spinning 3x-scale hunting bullet (copper
  jacket, polymer tip, rifling engraving that visibly turns) with a swirling vapour trail, drawn by `KillCamFx` with its
  own `killcam_fx` shader at AFTER_LEVEL (same path as the mod's tracer trails).
* Blood/hair/smoke are a small particle sim on the replay's slow-motion clock (droplets land and stay as splats).
  Server hit particles/sounds at the animal are held back until the replay reaches the hit.
* Screen: `KillCamOverlay` in the GUI pass (after any shader pack): `killcam_grade` shader (desaturate/tint/vignette/flash,
  fallback = translucent fills), letterbox bars, X-ray (3D in GUI pass with captured matrices; fallback = 2D hit
  marker), shot card. X-ray for deer uses `DeerAnatomyMesh` + `DeerDraw.skin` like the anatomy binoculars; hit organ
  pulses, wound channel + entry/exit rings. Wildlife: hit-point rings/channel instead of organs.
* Audio (original, synthesized by `tools/killcam/synth_audio.py`): slow-mo drop, flight whoosh with spin whirr,
  heartbeat, bullet/arrow impact, X-ray shimmer, body thud (synced to the collapse), return whoosh. World sounds are
  ducked and pitched down; premature hit sounds at the animal are dropped.
* Any key (not F-keys/modifiers) or click skips (fast return); damage, death, screens, dimension change, logout,
  Reduced motion or a 12 s safety timeout end it cleanly. HUD, hand, crosshair and the local player are hidden while it runs.
  1.2 s cooldown; a new shot never stacks on a running replay.

## Settings (client config `frontierhunts-client.toml`, section `presentation`)
| key | values | default |
|---|---|---|
| `killCam` | `OFF` / `LETHAL` / `TROPHY` (Silver-grade racks and better; grizzly, polar/black bear, cougar, lion, panther, bison, elk, moose) | `LETHAL` |
| `killCamXray` | true/false | true |
| `killCamLength` | `SHORT` (~2.7 s) / `NORMAL` (~3.8 s) / `LONG` (~5.2 s) | `NORMAL` |
`reducedMotion=true` disables it. The client tells the server its mode (`Prefs` payload) so no prediction work is done for players who turned it off.

Client command (for testing and players): `/killcam` (show), `/killcam off|lethal|trophy`, `/killcam xray on|off`, `/killcam length short|normal|long`.

### Settings-screen entries NOT added (coordinator)
`FrontierSettingsScreen` is not in `src/` (1362-line decompiled file); copying it would collide with other workstreams.
When it is in `src/`, add to `case INTERFACE:` right after the "Reduced motion" toggle:
```java
this.choice("Kill cam", "Slow-motion replay of lethal shots", HuntConfig.KILLCAM, com.formaworks.frontierhunts.killcam.KillCamMode.values(),
   m -> switch (m) { case OFF -> "Off"; case LETHAL -> "Lethal shots"; case TROPHY -> "Trophy only"; },
   "Follows a lethal bullet or arrow in, with an X-ray of shot placement. Any key or click skips it."); // [killcam]
this.toggle("Kill cam X-ray", "Shot placement view", HuntConfig.KILLCAM_XRAY, "Organs and the wound channel for a moment after the hit."); // [killcam]
this.choice("Kill cam length", "How long it lingers", HuntConfig.KILLCAM_LENGTH, com.formaworks.frontierhunts.killcam.KillCamLength.values(),
   l -> switch (l) { case SHORT -> "Short"; case NORMAL -> "Normal"; case LONG -> "Long"; }, "Only the presentation length changes."); // [killcam]
```

## Files
New (owned): `src/.../killcam/{KillCamNetwork,KillCamServer,KillCamPredictor,KillCamSounds,KillCamMode,KillCamLength}.java`,
`src/.../client/{KillCamClient,KillCamReplay,KillCamPath,KillCamStandIn,KillCamFx,KillCamOverlay,KillCamCommand}.java`,
`src/.../client/mixin/{KillCamCameraMixin,KillCamLevelRendererMixin}.java`,
`patch/assets/frontierhunts/shaders/core/killcam_{fx,grade}.{json,vsh,fsh}`, `patch/assets/frontierhunts/sounds/killcam/*.ogg`,
`patch/_merge/frontierhunts.client.mixins.json/killcam.json`, `patch/_merge/assets/frontierhunts/{sounds.json,lang/en_us.json}/killcam.json`,
`tools/killcam/synth_audio.py`.

Shared-file edits (all marked `// [killcam]`):
* `hunting/Whitetail.java` `projectileHit`: one line before `this.applyingProjectile = true;`
  `Object kcToken = com.formaworks.frontierhunts.killcam.KillCamServer.beforeDeerHit(this, var1, var2, var3, var4, var5); // [killcam]`
  and one line before the final `return var25;`
  `com.formaworks.frontierhunts.killcam.KillCamServer.afterDeerHit(kcToken, var25); // [killcam]`
* `HuntConfig.java`: 3 field declarations after `REDUCED_MOTION`, 3 `define` calls after the `reducedMotion` define.
* `RifleActions` untouched (EntityJoinLevelEvent sees the bullet with its final velocity).
Mixins (client list): `LevelRenderer.renderEntity` HEAD (hide / slow-mo partial), `LevelRenderer.addParticleInternal(...ZZDDDDDD)` HEAD
(hold back hit particles near the target), `Camera.setup` TAIL (pose).

## Offline checks done
* Payload codec round trip + arc-length path + final-approach easing (monotone, continuous speed) in a scratch harness.
* Full jar build; merged `frontierhunts.client.mixins.json`, `sounds.json` (8 events), `en_us.json` (23 keys) verified.
* Audio rendered to waveform/spectrogram and inspected (no clipping, envelopes as designed).

## In-game test script
Setup: creative or survival test world, daytime, flat open ground (a superflat world is easiest), `/gamerule doDaylightCycle false`.
Default settings (`/killcam` should print `Kill cam: Lethal shots · X-ray: On · Length: Normal`).

1. `/give @s frontierhunts:ridgeline_rifle` · `/give @s frontierhunts:reserve_308 40` · `/give @s frontierhunts:field_bow` · `/give @s frontierhunts:field_arrow 32`. Load the rifle (reload key, default R).
2. Face north (F3: facing −Z). Spawn a still, broadside mature buck ~80 blocks out:
   `/summon frontierhunts:whitetail ~ ~ ~-80 {NoAI:1b,Rotation:[90f,0f],deer_traits:{schema:2,buck:1b,age_months:66,frame:70,condition:80,rack_genes:95,seed:7,abnormal:0,coat:60}}`
3. **Lethal rifle shot:** aim (right mouse) just behind the front leg, about a third up the body (heart/lungs) and fire.
   Expect within ~1 frame: letterbox + slight grade, slow-mo swoosh; camera launches after a spinning copper bullet with a
   vapour trail, swings to the deer's side; bullet crawls in and strikes: flash, blood mist + droplets (+ far-side spatter,
   hair puff), impact sound; X-ray beat (organs, pulsing hit organ, glowing wound channel, card `WHITETAIL BUCK · 87 YD` /
   `DOUBLE LUNG` or `HEART` / `LETHAL — CLEAN, ETHICAL SHOT` / teaching line); slow collapse with a thud as it lands;
   camera glides back; the real deer lies downed where the double fell; HUD, hand, crosshair return. Total ≈ 3.5–4 s.
4. **Non-lethal:** summon another (step 2), shoot the hindquarter or a leg. Expect either no kill cam, or (if the
   prediction said lethal) a short fade-out/in without showing the hit. The deer runs off wounded as normal.
5. **Miss:** shoot 2 m over a deer's back → nothing (or a quick fade if predicted). No stuck camera.
6. **Arrow:** summon a deer 35 blocks out (`~ ~ ~-35`), draw the field bow fully, hit behind the shoulder. Expect the same
   sequence with the real arrow model (tip matches the arrow used) and the arrow shaft in the wound on the double.
7. **Skip:** repeat step 3, press any key (e.g. W) or click mid-flight → fast return; nothing fires, camera normal.
8. **Wildlife:** `/summon frontierhunts:grizzly ~ ~ ~-70 {NoAI:1b}` and kill it with one or more rifle shots (the
   killing shot triggers it). Expect replay with hit rings instead of organs, card `GRIZZLY` / `LETHAL`, vanilla
   death roll in slow motion, death puff as the camera leaves.
9. **Settings:** `/killcam trophy` then kill a doe (`buck:0b`) → no kill cam; kill the buck from step 2 → kill cam.
   `/killcam xray off` → no X-ray beat, card still shows. `/killcam length short|long` → noticeably shorter/longer.
   Turn on Reduced motion in Frontier settings → no kill cam. `/killcam lethal` to restore.
10. **Robustness:** during a replay take damage (`/damage @s 1` from a command block or have a mob hit you), open the
    inventory (E) or pause (Esc), or `/kill @s` → ends immediately, camera/HUD restored. Fire two shots quickly at two
    deer → only one replay, no stacking. Change dimension during a replay (`/execute in minecraft:the_nether run tp @s ~ ~ ~`
    from a command block) → ends cleanly.
11. **Shaders:** repeat 3 and 6 with Iris + a shader pack (e.g. Photon/Complementary) and with shaders off: grade and
    X-ray must look the same (they are drawn after the shader pack); bullet/blood visible and depth-correct; no black
    screen after the replay. If the grade shader failed to compile the log says "colour grade unavailable" and a plain
    tint is used instead.
12. **Multiplayer (dedicated server, 2 clients):** A kills a deer; only A sees the kill cam. B sees the normal shot and
    drop in real time. B killing a deer while A watches → only B's client plays it. Check the server log for no
    `Frontier Hunts kill cam:` warnings.
13. **TACZ (if installed):** a lethal TACZ shot on a deer (vital) → kill cam starts a tick or two after the shot from the muzzle.

## Known limitations
* Prediction covers the mod's `RifleBullet` and `FieldArrow`; `HuntProjectile` guns/crossbows and TACZ use the confirmed path (starts ≈1–3 ticks after the shot).
* A deer only "drops" on a vital hit (or damage that kills); fatal-but-running wounds (liver, single lung) intentionally get no kill cam.
* If the target leaves tracking range before the confirm, the replay aborts; after the confirm it finishes on the double.
* True desaturation needs the `killcam_grade` core shader; if a driver rejects it the fallback is a cool translucent wash.
* World sound ducking affects newly started sounds (already playing loops keep their level).
