# bugs — floating "D" boxes, field photo errors, server shot-path safety audit

Branch `bugs`. Compile exit=0, `tools/check_jar.py --base master`: 0 errors, 0 warnings.

## 1. Floating dark "D" boxes over the water

**They aren't drawn by Frontier Hunts.** All of the mod's world-space text and label code was audited:
- `camps/client/WorldText` (camp post plaque, trophy board). This text sits flat on the block faces, uses only ASCII
  plus "—"/"…"/"·", and has no background box.
- Hound and wildlife name tags. These are vanilla `renderNameTag`, which only shows a custom name, and only when you
  look straight at the animal. The hound's names are full words such as "Boone".
- TrailClient and TrackPrints. Prints, blood and clue meshes have no text, and the inspect read-outs are HUD only.
- Weather, perf/perf2, trailcam, camps, killcam, DeerSign. None of these draws text in the world. Every other
  `drawString` in the mod is a GUI/HUD call.
- No class in the mod spawns text-display or armor-stand entities. The mod does not set custom names, apart from the
  hound's.
- There are no supplementary-plane characters in any string. The 1.21 font falls back to unifont, so BMP symbols
  render correctly.

The client has Xaero's Minimap installed (seen in the log). Xaero's in-world **deathpoints** are drawn as small black
rounded squares with the initial **"D"**, and the "old deathpoints" option keeps the earlier ones too. That matches the
screenshot. Test: open Xaero's waypoints screen and delete the deathpoints, or turn off in-world deathpoints. The boxes
should disappear.

## 2. Field photo "Not an image file" spam
Root cause: the two damaged files (2026-09-27 03:50 and 03:56) came from dev.60/61. That build's `FieldPhotoMode` wrote
straight to the final `.png` with `NativeImage.writeToFile`, so a crash or kill mid-write left a zero-filled or
truncated file. The current `FieldPhotoStore.save` already writes to `.part` and then renames atomically. The gallery
tried to decode the bad files again on every visit and logged a full stack trace each time.

Fixes:
- `FieldPhotoStore.decode` detects the format from the file's magic bytes: PNG goes to stb first and falls back to
  ImageIO to recover damaged rows; JPEG, GIF and BMP go straight to ImageIO; anything else fails at once.
- A file that can't be decoded is logged once, as one WARN line, and remembered by a size+mtime signature. After that,
  `list()` hides it and `decode` doesn't retry it. If the file changes on disk, it gets another chance.
- Files under 8 bytes are skipped. Failures that aren't the file's fault (OOM, native library errors) aren't
  remembered; they log once.
- `FieldPhotoMode.accept` drops a failed photo from the current gallery list, so browsing never comes back to it.
- `TrailCameraScreen.save` (the trail-camera export to screenshots/) now also writes to `.part` and then renames
  atomically.
- The trailcam JPEG cache (`PhotoCache`) already used temp+rename and lives in its own folder. It never writes `.png`
  files into screenshots/.

## 3. Server safety audit (shooting / killing WildlifeMob and Whitetail)
Fixed:
- **`WildlifeVitals.classify` could hang the server thread.** The `for (double k = k0 - reach; k <= k0 + reach;
  k += step)` loop never ends when k0 is ±Infinity, or when |k0| > ~1e13 (then `k += step` doesn't change k). That
  happens when a projectile's position or velocity, or the mob's origin, is non-finite or absurd. The ServerHangWatchdog
  then kills the server. The offline fuzz harness reproduced the hang on the old code ("did not terminate", stopped
  after 50 calls). Fix:
  - reject null or non-finite point, origin, dir, yaw, energy or scale, and lines more than 4096 units from the body;
  - clamp scale to 0.05..64;
  - march with an integer index, capped at 4096 samples (normal shots take ~270-520);
  - return a null entry if it isn't finite.

  `ShotVitals` (in the damage event) and `KillCamPredictor` (on projectile spawn) both reach this code.
- **KillCamServer:**
  - A NaN distance passed the `distance < MIN || > MAX` test, so it's now `!(d >= MIN && d <= MAX)`.
  - Non-finite dir, exit and samples are dropped before the Shot packet is sent.
  - A NaN projectile velocity on wildlife death now falls back to the shooter's line.
  - The deer snapshot skips non-finite dir and point.
- **WildlifeMob:** no threat point is taken from an attacker with a non-finite position, and a corrupt saved threat
  (NaN) is dropped. Otherwise the flee and flight code would push NaN into the animal's motion.
- **Client hardening:**
  - The `KillCamStandIn.drive` catch-up is capped at 400 virtual ticks per frame; before, a huge virtual time could
    stall the render thread.
  - The `Darkroom` glGetError drain loop is capped at 32 calls.

Audited, no issue found:
- `ShotVitals`: try/catch around the trace, finite damage check, weak per-tick map.
- `KillCamPredictor`: at most 80/120 ticks, with range, chunk and finite checks.
- `WildlifeBleeding`, `TrailService`, `TrailStore`.nearby: copies the list, clamps radius and count.
- `TrackPrints`, which runs from `EntityTickEvent`, so it's safe to add and remove entities there.
- `CampsEvents`/`CampHooks`/`RecordService`/`RecordBook`: everything is in try/catch and every loop is bounded.
- `Quarry`: covers every species.
- Routines: `PressureEvents`/`PressureStore` check for finite values; prune keeps it bounded.
- `AiThrottle` mixins: server thread only, flag reset on every enter.
- Camload: the client query is rate-limited and owner-checked; config range is clamped (6-32).
- `MapSyncServer`: daemon worker, stopped and joined on `ServerStoppingEvent`; Hello is capped at 65,536 entries.
- Per-species tables: all switches have a default.
- No whole-level entity iteration that modifies the level.
- Threads: every thread the mod creates is a daemon (MapSync worker, HitchLogger), or comes from the common, IO or
  background pools.
- `check_jar` (e/e2/e3): nothing changed that is client-only and reachable on a dedicated server.

Not ours: the reported dedicated-server crash. It was a ServerHangWatchdog during shutdown, with non-daemon Distant
Horizons/WorldEdit threads keeping the JVM alive, on a heavily swapping host. Even so, the classify hang above was a
real way for a single shot to freeze the server tick.

## Offline checks
```
CP="$(cat /home/claude/fh/cp62.txt)"; C=/tmp/claude-0/cc-bugs   # compile.sh output
javac -proc:none -cp "$CP:$C" -d /tmp/vf tools/vital/harness/VitalFuzz.java tools/vital/harness/VitalHarness.java
java -cp "$CP:$C:/tmp/vf" VitalFuzz      # 642272 calls, ALL PASS (old code: hang)
java -cp "$CP:$C:/tmp/vf" VitalHarness   # ALL PASS (shot placement unchanged)
```

## In-game test script
1. Client: open the Field Camera gallery with the two damaged `frontier-photo-20260927-*.png` files still present.
   The gallery shows the newest readable photo, and the log has exactly one "Skipping damaged field photograph ..."
   line per file. Browse back and forth: no further log lines appear.
2. Take a field photo and then view it; it saves and opens normally. Copy a JPEG into screenshots/ and rename it to
   `frontier-photo-20260101-000000-000.png`; the gallery opens it.
3. On the trail camera screen, use Save. `screenshots/trailcam-*.png` appears, and no `.part` file is left behind.
4. Dedicated server: shoot a bison, then a grizzly, grouse and duck, with the rifle, field arrows, a bolt and a TACZ
   gun. Check that:
   - heart and lung hits drop the animal and play the kill cam;
   - other hits make it run and bleed;
   - no server tick warnings ("Can't keep up" spikes) appear;
   - `/stop` shuts down cleanly as far as Frontier Hunts is concerned. No `FrontierHunts map preload` thread should
     remain.
5. Xaero's Minimap: delete the deathpoints. The floating "D" boxes go away.
