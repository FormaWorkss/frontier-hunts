# Workstream `qa` - error sweep of gear.13

Branch `qa` (from master 0bf1526). Scope: find errors / fatal mistakes in the shipped gear.13 jar using the user's
real logs, a headless dedicated-server boot, and a static sweep; fix root causes with small `// [qa]` changes.

## What was tested

1. **The user's real logs** (staged read-only from `C:\Users\austi\AppData\Roaming\.minecraft`): `latest.log` +
   `debug.log` of the gear.13 session (multiplayer, server 173.240.156.137:9040, Sodium + Iris + Photon + DH),
   the gear.9-gear.12 logs of 10-01/10-02 and the last two crash reports (09-25, dev.41/dev.43 - both already fixed in
   current code). No crash, no mixin failure, no registry/recipe/loot/tag/advancement parse error from our mods.
2. **Headless NeoForge 21.1.248 dedicated server** assembled offline from the user's Gradle caches + MDG artifacts
   (see "Server smoke test"). Boots with the gear.13 jar in ~30 s, generates a world, and runs 120+ console
   commands: every frontierhunts/frontierstructures command root, 171 blocks placed + ticked + drops rolled, all 28
   entity types summoned (2 each) and ticked through day and night, spawns in the training grounds and the nether,
   items/recipes/loot audit, /reload, /datapack list, a FakePlayer running the player-only commands (shelter, camp,
   guide, journal, ecology, routine, sign, records, expedition, academy...) and all five academy courses started for
   real (plot built in `frontierhunts:training_grounds`, hunter teleported, ticked, leave). JVM class-load log proves
   **no client class is ever loaded on the dedicated server** (client classes are stripped from the server jar, as in
   production).
3. **Static sweep** of the jar (`tools/qa/static_sweep.py`): 4050 JSON files parse with no duplicate keys; every
   blockstate -> model -> parent -> texture reference resolves (incl. the built-in realistic_world pack); every
   registered item has an item model and a name, every entity a name, every sound event a sounds.json entry, every
   sound file exists; particle/atlas/shader references resolve. Plus: all 25 listed mixins' target classes and
   methods exist in 1.21.1 (javap), no Vineflower byte/short loop-counter artefacts in recompiled classes, no
   unguarded `getCurrentServer()/getSingleplayerServer()` dereferences in client code, payload registration classes
   are all common, all three config specs (client/server/worldgen) round-trip stable through NightConfig.
4. **All existing harnesses**: academy (130 checks), gui (layout 0 / sounds 0), journal, shelter, survival, rutfight
   (432 checks), ATV reverse, ATV wading, ATV grime, vital + fuzz (642k calls), tracking, hound, ecology - PASS.
   guide harness had 1 stale check (fixed, see below). `tools/check_jar.py` 0 errors; `tools/recipe_audit.py` PASS.

## Findings

| # | Sev | Symptom | Root cause | Fix | Verified how |
|---|-----|---------|------------|-----|--------------|
| 1 | MED | User log 11:34: `WARN LeafModel: Frontier realistic leaves: could not shape the leaves at (1340,86,3371)` - `ReportedException: Getting block state` / `MissingPaletteEntryException: Missing Palette entry for index 21` (Sodium chunk mesher thread). That leaves block falls back to a plain cube until its section is re-meshed. | Realistic trees read the **live** `ClientLevel` from chunk-builder threads (`LiveWorld`, `TrunkModel` neighbour reads, `foliageTint`). A chunk packet applied on the render thread refills a section palette in place; a read racing it throws. | `LiveWorld.state()` - race-safe read that retries a few spins (the refill takes microseconds), then lets the existing fallback handle it; used by every LiveWorld read, `TrunkModel` neighbour reads and `foliageTint`. | Compiles; code review of the race (`PalettedContainer.read` reuses `Data`); fallback path unchanged. Needs in-game confirmation (rare race). |
| 2 | LOW | Every client start: `Shader frontierhunts:falls_sheet could not find sampler named Sampler0` / `uniform named CameraPos` (x3 per reload). | `falls_sheet.json` declares a sampler and a uniform the procedural waterfall-sheet program never uses (GLSL drops them). | `patch/assets/frontierhunts/shaders/core/falls_sheet.{json,vsh,fsh}`: removed the unused declarations (no Java binds them). | static_sweep shader check (now 0 warnings); program text otherwise identical. |
| 3 | LOW | Crash report / server pack list: `mod/frontierhunts,frontierstructures (incompatible)`. | `pack.mcmeta` has `pack_format 34` (1.21.1 resources) only; as a data pack (48) the mod is flagged incompatible. | `patch/pack.mcmeta`: `pack_format 34`, `supported_formats [34, 48]`. | gear.13 crash report listed the pack `(incompatible)`; on the qa jar `/fhqa packs` reports `compat=COMPATIBLE`. |
| 4 | LOW | Server log: `[entity-deserializer/ERROR] ItemStack: Tried to load invalid item: 'No key id in MapLike[{}]'` when an empty wall trophy's chunk loads. | `TrophyMount` saves no `Trophy` tag when empty but reads it back with `ItemStack.parse` (logs an ERROR on `{}`). | `src/.../expedition/TrophyMount.java` (copied from dec62g8): `ItemStack.parseOptional`. | Seen once in a smoke run on the qa jar before the fix; 0 occurrences in the final smoke run. |
| 5 | LOW | 10 blocks show raw `block.frontierhunts.*` keys (Jade/WAILA, `/setblock` feedback): alpine_flow, alpine_foam, alpine_overgrowth, alpine_spray, giant_boulder, mossy_giant_boulder, mossy_outcrop, river_outcrop, spotlight_beam, stand_ladder. | No lang entries for blocks without items. | `patch/_merge/assets/frontierhunts/lang/en_us.json/qa.json`. | static_sweep with registry ids: 0 missing names. |
| 6 | test | `tools/guide/harness` failed `id bounds`. | Stale test: survival added Field School tips (10 now), test hard-coded 6. | Bound by `Tip.values().length`. | Harness ALL PASS. |
| - | info | `Texture frontierhunts:particles/canvas with size 282x282 limits mip level from 4 to 1` (user log). | 15 break-particle sprites are unstitched 0.9 of a 1254 px cell (282 px) and 3 atlas textures are 1254 px. In 1.21.1 this is only a log line: `SpriteLoader` computes the limit but still builds 4 mip levels (`Created: 8192x4096x4`). | Not changed: fixing needs resampling shared art (artqa territory). If wanted: resample field_materials/reserve_materials/workshop wood_v1 to 1280 px (UVs are normalised; particle sprites become 288 px). | Decompiled `SpriteLoader.stitch` (1.21.1). |
| - | info | `Configuration file frontierhunts-client.toml is not correct. Correcting` on each launch. | Every launch in the logs was a new build with new client keys. Spec is stable (offline round-trip: 2nd correction pass = 0 for client/server/worldgen). | none | CfgRoundTrip test. |
| - | info | Sodium: `AtvGrimeRender$UvScale does not support optimized vertex writing` (gear.10 log). | Wrapper VertexConsumer without Sodium's writer API: slower path for the ATV grime layer only. | none (would need Sodium API on the classpath). | - |
| - | info | Not ours: goat-horn "Missing sound" (vanilla 1.21.1), Iris "Opaque + Translucent pass", Xaero "No texture ...block/original/..." (Xaero probing a code-generated atlas sprite), Sound Physics "Unknown sound frontierhunts:elk_call" (stale entries in the user's Sound Physics config). | | | |

FakePlayer note (not a shipped bug): any `hasChannel()` on a NeoForge FakePlayer NPEs (its Connection has no netty
channel). Our code only calls it from player ticks / commands, which FakePlayers never run in a real server, so this is
only relevant to the harness - it wires the fake player with an EmbeddedChannel.

Final smoke run on the qa jar: items 328/0 bad, recipes 248/0, blocks 171/0, spawns 56+10+28/0, 5 academy courses
started/ticked/left, 0 ERROR/WARN lines from our mods, 0 client classes loaded, no crash report.

## Files changed (all small, marked `// [qa]`)
- `src/com/formaworks/frontierhunts/client/tree/LiveWorld.java` - new `state()` helper; 3 reads use it.
- `src/com/formaworks/frontierhunts/client/tree/TrunkModel.java` - 2 one-line hooks (`at()` neighbour read, `foliageTint`).
- `src/com/formaworks/frontierhunts/expedition/TrophyMount.java` - new override (from dec62g8), one line.
- `patch/assets/frontierhunts/shaders/core/falls_sheet.{json,vsh,fsh}`, `patch/pack.mcmeta`,
  `patch/_merge/assets/frontierhunts/lang/en_us.json/qa.json`.
- `tools/guide/harness/GuideHarness.java` (test only).
- New: `tools/qa/` (smoke test, harness mod, static sweep, server assembly).

## Server smoke test (rerun on the final jar)

```
tools/qa/server_smoke.sh <mod jar> [tools/qa/server_smoke.commands] [work dir=/tmp/claude-0/qa-smoke]
python3 tools/qa/static_sweep.py <mod jar> /tmp/claude-0/qa-smoke/fhqa_ids.json
```
- Takes ~10 min on this container (2 CPUs). Prints `[FHQA]` results, every non-vanilla WARN/ERROR in `logs/debug.log`,
  exception frames in our code, client classes loaded on the server (must be empty) and crash reports.
  **Pass = no `FAIL`, no ERROR lines, empty client-class list, no crash report.** Expected noise: harness `WARN block ...
  no loot table` for stand_ladder / 3 compact tents / 2 workshop blocks (they drop via code), `INFO entity ... no default
  loot table` (drops via code), `refused by addFreshEntity` in the training grounds (the academy seals the grounds),
  console "A player is required" / "Unknown or incomplete command" for commands given without args.
- Runtime: `/home/claude/qaserver` (libs/, modules/, run.sh). If it is missing, `tools/qa/assemble_server.sh` rebuilds
  it from the files staged in `/mnt/user-data/uploads` (from `C:\Users\austi\.gradle\caches\modules-2` and the MDG
  `build/moddev/artifacts`); stage them again with the device tools if /mnt/user-data was cleared.
- Launch = MDG's `forgeserverdev` target on the user's `neoforge-21.1.248.jar` with `net.minecraft.client.*`,
  `com.mojang.blaze3d.*` and realms stripped (production server has none of them).
- `tools/qa/harness` = test-only mod `frontierqa` (never shipped): `/fhqa registry|packs|items|recipes|loot|blocks <dim>|
  spawn <n> <dim>|count <dim>|player <cmd>|tickplayer <n>|academy <course>`.
- Limits: no real client connects, so client rendering, payload decoding on the client and GUI are not exercised
  (those are covered by the user's client log). FakePlayers are not in the player list, so player-list-driven code
  (academy departure fade, logins) is only partly exercised.

## In-game checks for the coordinator (gear.14 on the user's PC)
1. Start the client, open the log: no `falls_sheet could not find` lines.
2. Join the server, fly over a realistic-trees forest with chunks loading fast (elytra/spectator): no
   `could not shape the leaves` WARN; no plain-cube leaf blocks inside realistic crowns.
3. Look at a stand ladder / giant boulder with Jade (if installed): proper names.
