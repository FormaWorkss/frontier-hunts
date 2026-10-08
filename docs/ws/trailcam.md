# Workstream `trailcam` — trail cameras that take real photos

Branch: `trailcam` (clone `/home/claude/work/trailcam`). Compile: `tools/compile.sh` → `exit=0`. Full jar:
`python3 tools/build.py trailcam-test /home/claude/work/trailcam` builds OK and the mixin fragment merges.
Nothing here was launched in Minecraft (the sandbox cannot run it). Treat everything as **awaiting in-game verification**.

## What it does

1. **PIR trigger (server, `expedition/TrailCamera`)**: every 10 ticks the camera checks the photo frame (70° × 43° lens, tilted
   down automatically toward the ground about 8 m out). It triggers on a warm body that **moves** inside the frame: every Frontier Hunts
   animal (`Whitetail` = deer/elk/moose, all 14 `WildlifeMob` species), **players**, and vanilla mobs if `vanillaMobs=true`.
   The range is 20 m by day and 15 m on infrared (the light level in front of the lens is below 8). There is a 60 s cooldown per subject,
   a 3 s recovery between shots, and line of sight is required. The battery costs are unchanged (3 per shot, 5 on IR, 1 per 400 ticks), and a roll holds 48 frames.
   Every warm body in the frame is recorded (up to 6). The one that tripped the sensor is the headline.
2. **Scene record (`trailcam/TrailcamScene`)**: stored with each capture (`CameraRegistry.Capture.scene`, saved in SavedData and
   in the item's `camera_log`). It holds the lens pose, the game time and day time to the tick (so the minute and the moon are right), rain and thunder,
   a temperature computed from the biome, the `SeasonClock` season, the time of day, rain and altitude, the calendar date, and the frame number. Each subject
   stores its entity type, the **synced entity data exactly as the network sends it** (DeerTraits via the `TRAITS` compound, so new trait fields
   such as the trophies legendary flag flow through), position, body, head and pitch, walk-animation phase, whitetail gait and graze state, velocity, gear, and for
   players their name, UUID and skin texture property.
3. **No more invented frames**: `CameraRegistry.catchUp` now only drains the battery for time spent unloaded. Old rolls with no scene
   (possibly fabricated) are **dropped when loaded**.
4. **Darkroom (client, `client/trailcam/Darkroom`)**: runs while the (now opaque) camera screen or hub is open. For each photo it:
   - temporarily sets the client level's game time and day time plus rain and thunder to the recorded moment. Infrared frames render at midday and are
     re-lit by the camera's flash in post.
   - adds client-only stand-ins (the exact entity type plus the recorded synced data, pose and gear; `RemotePlayer` with the recorded or online skin),
     frozen via `EntityTickEvent.Pre`, and pre-rolls the deer animator so the gait and graze pose settle.
   - swaps the render camera to the lens (`setCameraEntity` on a Marker plus a `Camera.setup` TAIL mixin for the exact pose), sets the FOV so the
     centred 16:9 crop is the lens angle, cancels the hand and name tags, and hides every other living thing through a
     `EntityRenderDispatcher.shouldRender` mixin (this also covers Iris shadows).
   - waits 5 warm-up frames (16 with an Iris shader pack) and until `hasRenderedAllSections()`, then reads the colour (and depth for IR) at
     `RenderLevelStageEvent.AFTER_LEVEL`. That point is after Iris's final pass and before the hand and GUI.
   - restores everything in `RenderFrameEvent.Post` (so ticks and the sound listener never see the lens). The photo is post-processed on a worker
     (`TrailcamLook`): 16:9 crop, a slight barrel wide-angle, the day colour grade, or the IR look (flash fall-off by depth, bright foliage,
     black background, eyeshine, motion smear), vignette, sharpening, noise, and a burned-in strip:
     `CAMERA NAME · moon icon · 54°F 12°C · 10/05/01 · 02:14 AM · #0042`.
   - caches it as a JPEG plus NBT sidecar in `<game dir>/frontierhunts/trailcam/<sp-world|mp-server>/<camera>_<photo>.jpg`, with LRU at 256 MB.
     JPEG was used instead of PNG to keep a noisy 640×360 frame near 80 KB. "Save to screenshots" writes a PNG.
   - fallbacks: a photo that throws, times out, or reads back an empty frame (a shader pipeline that never fills the main target) becomes
     FAILED. Deer show the old composite and everything else shows "No image". If the depth buffer is unusable, IR uses a ground-plane distance estimate.
5. **Uplink for far cameras**: if the lens area (±4 chunks) is not loaded on the client, the gallery reuses the existing `LensView`
   chunk streaming. It asks the server (`TrailcamNet` action UPLINK) to open the lens link at the spot the photo was taken, keeps the gallery up
   (`ScreenEvent.Opening` cancels the live monitor), develops the photos with a distance fog at 56 blocks, then sends `LensView.CloseRequest`.
6. **Gallery (`client/TrailCameraScreen`, override)**: a thumbnail grid with the time, species or player name and an IR badge. Clicking a thumbnail opens the viewer, which has
   `<` `>` buttons, arrow keys, mouse wheel and click-left/right navigation, **Save to screenshots** (a PNG plus a clickable chat link), and **Delete** (confirm).
   Labels show the species and sex, then age · points · score · kg from traits for deer, the player name, and "with …" for the other animals in frame. Clear roll, View lens and
   All cameras are kept. The old composite "Activity" tab was removed because the real live view is still under "View lens".
   **Hub (`client/CameraHubScreen`, override)**: each camera row shows its latest photo as a thumbnail, developed there too if loaded.
7. **Network (`trailcam/TrailcamNet`, registrar "1")**: C2S `trailcam_request` (ROLL/DELETE/UPLINK/HUB plus up to 128 cached ids) and S2C
   `trailcam_photos` (≤64 entries; a scene is sent only if the client lacks it; ≤640 KB per packet, with "more" to page). The server checks
   reach to the console (12 blocks), a real camera or hub block, `mayInteract`, owner/op (as in the existing `owns`), a 2-tick rate limit, and 3 s between uplinks.
   Photos develop for anyone allowed to view the camera.

## Files

New: `trailcam/TrailcamScene.java`, `trailcam/TrailcamNet.java`, `trailcam/TrailcamConfig.java`,
`client/trailcam/{Darkroom, DarkroomActors, DarkroomHost, Photo, PhotoCache, TrailcamClient, TrailcamLook, Font57, TrailcamPlayer}.java`,
`client/TrailcamBridge.java` (package access to `WhitetailRenderer.pose` and the `CameraPhoto` fallback),
`client/mixin/TrailcamCameraMixin.java`, `client/mixin/TrailcamEntityMixin.java`,
`patch/_merge/frontierhunts.client.mixins.json/trailcam.json` (`{"client":["TrailcamCameraMixin","TrailcamEntityMixin"]}`).

Overrides copied from the dec62g8 decompile (whole classes, not previously in `src/`):
- `expedition/CameraRegistry.java`: `Capture` gets a trailing `CompoundTag scene` component, and the old 10-argument constructor is kept. `Station.shots`
  is added, the scene is saved and loaded in `saveRoll`/`loadRoll`, scene-less frames are dropped, `catchUp` is battery-only, and `herd`/`frame`/`hourCurve` are removed. Every change is marked `// [trailcam]`.
- `expedition/TrailCamera.java`: detection rewritten, and the public API is unchanged.
- `client/TrailCameraScreen.java`: the gallery, keeping the `open(Roll, BlockPos)` and `console()` signatures.
- `client/CameraHubScreen.java`: the jar version plus an opaque background, thumbnails and the `DarkroomHost` interface, with the changes marked `// [trailcam]`.
No edits to Whitetail, WildlifeMob, HuntConfig, DeerTraits, ScoutingNetwork or lang files.

## Config

`config/frontierhunts-trailcam.properties` is created on first use:
- Client side: `photoWidth=640` (320–1280; the height is width×9/16), `jpegQuality=0.9` and `cacheLimitMB=256`.
- Server side: `dayRange=20`, `nightRange=15`, `subjectCooldownSeconds=60`, `triggerDelaySeconds=3` and `vanillaMobs=false`.

## Coordinator notes
- **seasons**: the darkroom sets the client game time to the capture time for a few frames at a time, restored at `RenderFrameEvent.Post`.
  If seasonal code triggers chunk rebuilds when the season changes, drive that from the client tick, not from render events, or skip it while
  `Darkroom.active()`.
- **trophies**: traits reach the photo through the synced `TRAITS` compound and `DeerTraits.save/load`. `ScoutingNetwork` was not touched.
- A camera only records while its chunk ticks (a player is within simulation distance, or the chunk is `/forceload`ed). This is intended now that
  fabrication is gone.

## In-game test script (awaiting verification)
1. Build with `python3 tools/build.py trailcam-test /home/claude/work/trailcam`. Start a creative singleplayer world with Sodium, first with shaders off.
2. `/give @s frontierhunts:trail_camera`, `/give @s frontierhunts:camera_base_station` (hub),
   `/give @s frontierhunts:whitetail_buck_spawn_egg` and `/give @s frontierhunts:black_bear_spawn_egg`. Place the camera on the side of a tree trunk at head
   height, facing an open trail. Run `/time set 6000` and `/weather clear`. (You can also use `/summon frontierhunts:whitetail ~ ~ ~8` and `/summon frontierhunts:black_bear ~ ~ ~12`
   while you stand next to the camera, adjusting the offset to the direction it faces.)
3. Stand behind the camera. Spawn a whitetail about 8 m in front of the lens (eggs spawn natural traits) and let it walk. Within about 1 s it should trigger
   (the battery drops by 3). Spawn a black bear about 12 m out, then walk through the frame yourself about 6 m away.
4. Right-click the camera. The screen is opaque. Tiles show a red "DEVELOPING" safelight and fill in within about 1 s each. **Expected**: the tiles are real
   renders of that exact spot and time, showing the deer in its walking or grazing pose with its antlers, the bear, and you with your skin, armour and held item. Only recorded subjects appear:
   the live deer standing there now and your own body are **not** in the photo. The colours are slightly muted and warm, with vignette and grain,
   a barrelled wide-angle look, and a black strip reading `CAMERA … ◑ 70°F 21°C 09/03/01 12:0x PM #0001`.
5. Click a photo. Check `<`/`>`, the arrow keys and the mouse wheel. **Save to screenshots** should post a chat link, and a PNG should appear in `screenshots/`.
   **Delete** should require Confirm and then remove the frame. The labels should read, for example, "Whitetail buck · Mature · 8 pt · score 131 · 92 kg" or your name, "Player".
6. Night test: `/time set 18000`. Walk past, then spawn a deer walking toward the camera about 6 m out. The photo should be **grayscale IR**:
   bright foreground grass and leaves, the subject well exposed, fading to black by about 20 m, a black sky, **glowing eyes** on the deer if it faces the lens,
   a slight smear if it moved, and the time to the minute (for example `12:00 AM`). The moon icon should follow `/time set` days (full moon on day 0, 8, …).
7. Rain: `/weather rain`, `/time set 1000`, then trigger. There should be rain streaks and a cooler temperature in the strip.
8. Hub: place the hub, open it and check the latest-photo thumbnail per camera. Click a camera to open its gallery, and check that "All cameras" goes back.
9. Far camera: from the hub, open a camera more than 16 chunks away that has undeveloped photos (delete the cache folder `frontierhunts/trailcam/` to force this).
   The status should read "Calling the camera over the uplink…", then "Uplink open…". Photos should develop (terrain beyond about 50 blocks fades into fog), the link should close,
   and the world should reload around you. Closing the gallery mid-uplink must return the view normally.
10. Reopen the gallery. Photos should load instantly from the cache, with no re-render. Relog and they should still be cached.
11. Enable an Iris shader pack (Photon or Complementary) and repeat 4 and 6. It should look like the shader's world. Warm-up is about 16 frames. If a pack leaves the main target empty,
    the tile shows the fallback and `latest.log` shows `The renderer returned an empty frame`. Check the log for `Trail camera` errors.
12. Multiplayer (dedicated server plus 2 clients): client B walks past A's camera. A's gallery shows B with B's skin, even after B logs off.
    B cannot open A's roll (owner check). Check the server log for errors.
13. Performance: while the gallery develops, the frame time should stay about the same (one normal level render per frame) plus a hitch of about 10–30 ms per photo
    for the read-back. Outside the gallery there is no cost apart from the PIR scan (1 AABB query per camera every 10 ticks).

## Known limitations
- Vanilla renderer (no Sodium): developing from a camera far from the player re-centres the section grid, so a few edge chunks rebuild after the
  gallery closes.
- Shader packs: IR frames are rendered at midday, so hard sun shadows can remain faintly in IR shots. Eye adaptation or TAA may need the extra warm-up
  frames. Depth may be unusable with some packs, which gives the ground-plane IR fall-off.
- Eyeshine positions use a head estimate (forward of the body box, low when grazing or bedded), not the animated head bone.
- Photos of the local player use a stand-in, so client-only cosmetics from other mods may not appear.
