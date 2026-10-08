# Workstream `camload`: trail cameras keep their area loaded

Branch `camload`. Compiles (`exit=0`); `build.py camload-test` builds; `check_jar.py` reports 0 errors and 0 warnings. Not launched in game.

## What it does
The user asked that each camera keep its own chunks loaded without lag. Every placed trail camera that has **battery > 0** and a
**roll that is not full (< 48)** keeps the chunks its lens cone covers loaded **and entity-ticking**. Animals keep walking and the PIR
trigger keeps firing with no player near, and this carries over across restarts.

* `camload/CameraChunkLoader`: a NeoForge `TicketController` (`frontierhunts:trail_camera_area`). It uses **persistent** block-owned
  tickets (`forceChunk(level, cameraPos, cx, cz, add, ticking=true)`).
  * **Footprint** (`camload/CameraFootprint`, pure geometry) keeps at most **4 chunks**. The camera's own chunk always comes first, so its block
    entity ticks. The rest are the chunks carrying the most of the 70° cone's ground area out to max(dayRange, nightRange). A chunk
    under 3% of the cone is skipped. `tools/camload/FootprintCheck.java` runs every block position, facing and range: 0 failures.
    At the 20 m default the kept chunks cover 98.4% of the cone on average (87.8% worst). 58% of cameras need 4 chunks, the rest need fewer.
  * **Dedupe**: each chunk is held by exactly one ticket. A shared chunk stays with its current holder. On hand-over the new ticket is
    taken before the old one is dropped, so the chunk never unloads in between.
  * **Caps** (oldest placed first): per owner `trailCameraChunkLoadingPerPlayer` (6), total `trailCameraChunkLoadingTotal` (48).
  * **Release**: the camera is broken or taken down (`CameraRegistry.remove` hook, next tick), the battery dies, the roll fills,
    the camera is off (ACTIVE=false means no charge), the config is toggled off, or the caps shrink. The periodic pass catches these within 5 s.
  * **Restart**: the validation callback runs per level before NeoForge re-applies tickets. It drops tickets whose camera is missing from the
    registry, no longer qualifies, or the toggle is off. It also drops duplicate holders of a chunk. What survives is mirrored in memory.
  * **No lag**: one reconcile every 100 ticks, or the tick after a camera is placed or removed. A chunk that is not in memory first gets a
    short-lived, non-persistent **warm-up ticket** (async load, expires after 300 ticks). It is forced only once `getChunkNow` returns it, so
    taking a ticket never blocks the server thread. The limits are 4 new tickets and 8 warm-ups per tick. The stale-block check uses `getChunkNow` too, so it never does a blocking load.
    The camera's PIR scan is unchanged (1 AABB query per 10 ticks).
  * **No extra spawning**: nothing is spawned. Vanilla natural spawning in these chunks still needs a player within 128 blocks,
    because `LocalMobCapCalculator` has nobody to count against. The mod has no chunk-based spawner.
  * **Despawn guard**: `WildlifeMob` (PathfinderMob, `removeWhenFarAway`=true) would be discarded instantly in a held chunk whenever a
    player is online more than 128 blocks away. `MobDespawnEvent` → DENY only for a WildlifeMob in a held chunk with no player within 128 blocks.
    This keeps exactly what an unloaded chunk would keep. Whitetails are never despawned anyway.
* **AI throttle check (perf)**: `AiThrottle.nearestPlayer` has no player within 128 blocks (or none in the dimension, which gives `Double.MAX_VALUE`),
  so calm animals in camera chunks run at **MINIMAL**: goal selection every 8 ticks, perception scans every 20 ticks. Navigation and move control are
  untouched, so strolling animals still move every tick. The camera's motion test (0.2 block displacement or velocity per 10-tick sample)
  still triggers. Strolls start about 4× less often than at full rate, as perf intends. Alarmed animals run FULL.
* **Screen**: the status line reads `LIVE · KEEPS ITS AREA LOADED` when the camera holds its area (`CamLoadNet` query/status payloads,
  optional registrar; ownership is checked, rate limited to 1 per 4 ticks, and no client classes are involved).

## Files
New: `camload/CameraChunkLoader.java`, `camload/CameraFootprint.java`, `camload/CamLoadConfig.java`, `camload/CamLoadNet.java`,
`tools/camload/FootprintCheck.java`.
Shared hook lines (all `// [camload]`):
* `HuntConfig.java`: `com.formaworks.frontierhunts.camload.CamLoadConfig.server(var1);` after the perf line.
* `expedition/CameraRegistry.java`: `CameraChunkLoader.changed();` in `place()` and `remove()`, plus a new `all()` accessor.
* `client/TrailCameraScreen.java`: `CamLoadNet.query(roll.pos())` in `init()` (the first time), and the status text in `header()`.

## Config (`serverconfig/frontierhunts-server.toml`, `[trailCameras]`)
`trailCameraChunkLoading` = true, `trailCameraChunkLoadingPerPlayer` = 6 (0–64), `trailCameraChunkLoadingTotal` = 48 (0–1024).

## In-game test script
1. Build it, start a creative world, then `/gamerule doMobSpawning true` and `/time set 6000`.
2. Place a trail camera on a trunk facing open ground (with a battery). Open it: the status should read `LIVE · KEEPS ITS AREA LOADED`.
3. `/forceload query` shows only vanilla forceloads. Check the NeoForge tickets with F3 or the debug screen instead: after `/tp` 400+ blocks away, run
   `/execute in minecraft:overworld run data get block <camera pos>`. It succeeds (the chunk is loaded) instead of failing with "not loaded".
4. With the camera 400+ blocks away (well past simulation distance), summon a deer and a bear walking in front of it
   (`/summon frontierhunts:whitetail <x> <y> <z>` 8 m in front, using `/execute positioned` from the tp spot). Wait 2–3 minutes, then fly back and open the camera.
   There should be new photos taken while you were away, and the battery should have dropped. The bear must still exist (despawn guard).
5. Save and quit, reopen the world, and stay away. `latest.log` shows `Trail cameras in minecraft:overworld: 1 keep their area loaded, 0 released`.
   Photos keep arriving.
6. Break the camera: step 3's `data get` now fails (the chunk unloads). Likewise when the battery drains to 0 or the roll reaches 48 photos:
   the area is released within 5 s, and the status goes back to `ON THE NETWORK · LIVE` or `OFF-GRID`.
7. Place 7 cameras as one player: only the 6 oldest show `KEEPS ITS AREA LOADED`. Two cameras side by side share chunks (one ticket each).
8. Set `trailCameraChunkLoading=false` in the server config: every area is released within 5 s. Restart: the log shows the tickets released.
9. Performance: with 48 cameras, the server MSPT (`/debug start`/`stop`, or the F3 integrated-server graph) should barely move. Chunk loads are
   async (no stall when the pass takes tickets). The F3 AI-tier counters show the camera-area animals as MINIMAL.
