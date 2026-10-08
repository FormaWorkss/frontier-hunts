# Workstream `perf2`: Ultra-preset hot-path audit, fixes and a hitch logger (branch `perf2`)

User report: "it still needs a performance overhaul, there are random glitches, fps loss etc, needs to be butter
smooth in ultra preset, and I only get this when on ultra". Everything here is marked `// [perf2]`.
Nothing was run in game (no client in the sandbox). The tree changes were checked offline with the existing
`tools/perf/TreeLodBench` fixtures: the geometry is byte-identical to master (`TreeLodBench dump`, all 56 fixture
trees, every level), and concurrency/eviction harnesses passed (see "Offline checks").

## 1. Findings, ranked by likely impact (static audit of what Ultra turns on)

1. **Realistic-tree cache thrash (Ultra only: realistic world + ULTRA bands 48/160).** The cache budget was a fixed
   700k quads / 120k member blocks. A full tree is ~6-10k quads and Ultra grew every tree within 76 blocks with full
   detail, plus the distant crowns out to 168: several million quads in a dense forest. Trees were evicted (least
   recently used) and grown again on nearly every rebuild of their sections. Each full growth costs ~5-7 ms of
   worker CPU and allocates ~3 MB (measured, `GrowAlloc`/`GrowProf` harness). The result is saturated
   Sodium workers, late LOD swaps and garbage-collector pauses on the render thread: the FPS loss and stutter.
   Three things made it worse:
   - **The 200k cliff.** Once the cell map passed 200k entries, counting negative entries for loose leaves and
     timber, `TreeGrowth` cleared the *whole* cache. Every tree then regrew in the next rebuilds: a multi-second
     burst, likely one of the "random glitches".
   - **A lookup dropped cached trees by a distance guess** (`growNeed`). It did this even when the level the
     section actually draws was cached.
   - **Too much was grown with full detail.** `growBoth = NEAR_OUT + 16` and `card = impOut + 8` grew distant crowns
     and full trees that were never drawn.
2. **The same tree was grown 2-3 times at once.** A tree's LOD swap dirties all its sections in the same tick, and
   Sodium builds them on different workers. Each worker grew the whole tree. (Harness: 6 workers for one tree gave up
   to 6 growths. Now it is 1.)
3. **Impostor fitting sorted the crown's card samples about 20 times per growth** (`TreeImpostor.percentile`). That
   was 27% of an impostor-only growth's CPU and ~10% of a full one. It runs for every tree in view, out to the render
   distance.
4. **CPU-skinned wildlife emission.** An Ultra sculpted body has 8.6k vertices and 27k indices. Each index was
   written with six chained `VertexConsumer` calls, in every pass (twice with a shader pack's shadow pass). About 10
   animals at Ultra detail meant roughly 270k chained calls per pass. Deer already use the bulk path (`FastEmit`);
   wildlife2026 and the tracking hound did not.
5. **First-sight hitches.** The first time a wildlife species came close, its 1024x1024 photographic coat (PNG decode
   plus mip chain, ~20-40 ms) and its sculpted mesh (gzip parse, a few ms) loaded on the render thread. This happened
   once per species per session, which looks random to a player.
6. **Section rebuild storms were count-based.** Tree LOD swaps (6 per tick at Ultra) and season stage sweeps (6 per
   tick for *every* seasonal section, once per quarter-month stage) took no account of what the frames cost.
   `TreeLod` also had a rare memo bug: a section could stay recorded at an assumed level it was never drawn at, and
   was then rebuilt every tick.
7. **Per-block allocation in meshing.** Construction timber and loose leaves (bushes, hedges, builds) baked a fresh
   6-quad cube per block on every rebuild, and `getQuads(side)` streamed and filtered it for every face.
8. **Smaller per-frame and per-growth waste.** A 12k-entry buffer for every growth (impostor-only ones emit ~20
   quads). Long boxing per placed quad. `RenderBudget` copied its score array every frame. `SkinnedMesh.get` was
   `synchronized` for every animal every frame.

**Not changed (reported for the profiling pass):**
- Each section rebuild re-validates every tree it draws against the live world (member kinds + leaf light,
  ~0.1-0.3 ms per tree).
- Forest-floor dressing is re-baked on every rebuild.
- Full growths still allocate ~3 MB each.
- `AlpineWaterMesh` makes per-frame per-patch AABB, block and fluid checks near alpine flows.
- Uploading dense NEAR-tree sections is inherent to the geometry.
- `AlpineLook` calls `allChanged()` on the first frontierhunts-biome visit per world, without a shader pack. The hitch
  logger names the caller of every `allChanged`.
- The weather veils' fill-rate (Cinematic) is only active in storms.

## 2. Fixes

### Trees (`client/tree`)
- **`TreeGrowth`**:
  - **Budgets follow the heap and the detail level** (`configureBudget`, set by `TreeLod.budget`). Quads: 8% of
    max heap at ULTRA/MAXIMUM (5% below), at ~190 B per quad, clamped to 0.7M-4M (2.5M below Ultra). Members: 2% of
    the heap, 120k-1M.
  - **Eviction:**
    - It walks a live-tree set instead of every member entry.
    - Only one worker trims at a time (CAS), down to 80% of the budget.
    - It drops the trees **furthest from the camera** first (`focus()`, published by `TreeLod` each tick).
    - It never drops a tree drawn in the last 1.5 s unless it must.
    - Quad pressure only evicts trees that hold real geometry. Member pressure evicts any tree.
  - **No more whole-cache cliff:** expired negative entries are purged at most 4x/s, young negatives next, and the
    map is cleared only past twice its budget.
  - **One growth per tree at a time:**
    - After its log-component BFS, a growth claims `(world, lowest log)` in `GROWING`.
    - A second worker waits (at most 250 ms) for that growth instead of growing the tree again. It then takes the
      tree from the cache, or adopts a tree published just before its claim.
    - Claims are released in `finally`, so waiters wake even on exceptions.
  - New `lookup(..., need, anyCached)`: returns any valid cached growth, and the caller regrows only for the level
    it really draws.
  - Growth buffers are sized by mask. The holder key is boxed once per run of quads.
- **`TrunkModel.treeCell`**:
  - The first lookup uses `anyCached = true`.
  - The regrow (level missing) asks `bits(lod) | growNeed` and counts `treesRegrownForLod`.
  - Timber cubes are cached per block state (`CubeQuads` with ready per-face lists, `face()`).
  - `LeafModel` does the same for loose leaves.
- **`TreeImpostor`**: the percentile is now a linear selection that returns exactly the sorted value (verified on
  200k random arrays). Scratch arrays are reused.
  - Measured CPU time per growth (sandbox, `GrowProf`): impostor-only 2.1 -> 1.56 ms, full 5.8 -> 5.2 ms.
- **`TreeLod`**:
  - `growBoth = NEAR_OUT + 4` (was +16). Impostor-only growth starts at `impOut + 2` (was +8).
  - `schedule()` scans without per-entry allocation (primitive scratch, static consumers, partial selection). It
    asks the frame governor for its per-tick budget, counts `lodDirty` and `dirtyDeferred`, and times itself
    (`lodTickMs`).
  - It invalidates the builders' memos after dirtying, which fixes the "rebuilt every tick" case.
  - `cacheLine()` reports the cache for the log.
- **`SeasonalFoliage`** counts derived cells. **`SeasonalClient`**: the natural stage sweep is 4 per tick (was 6),
  and both sweeps are governed and counted.

### Frame-time governor (`perf/client/FrameGovernor`, new)
- Times every frame and client tick.
- A frame longer than `1.6 x typical + 4 ms` (and > 12 ms) halves the share of the per-tick rebuild budgets the mod
  may use, down to 25%. Each smooth tick gives back 5%.
- `TreeLod` and `SeasonalClient` request `FrameGovernor.budget(base)` sections, always at least 1. Held-back
  rebuilds stay queued.

### Wildlife
- **`WildlifeRenderer`** and `tracking/client/HoundRenderer` emit each vertex with the single 11-argument
  `addVertex` (BufferBuilder's fast path; same data).
- **`WildlifeTexture`** (rewritten): `ensure()` decodes and mipmaps the coat on `Util.backgroundExecutor()` and
  returns false until it is ready. The render thread only uploads, and that upload is timed. `ensureNow()` keeps the
  old synchronous path for the small 256 px distant coats.
- **`SkinnedMesh`**: `get()` is lock-free once a mesh is loaded. `ready()` prefetches a mesh on a background thread.
- The renderer starts both prefetches from twice the near-swap distance. Until they are in, the animal keeps its
  distant look rather than stalling a frame.
- `RenderBudget` reuses its sort scratch.

### Instrumentation (new: `perf/client/PerfStats`, `HitchLogger`, `PerfCommand`; mixins `PerfLevelRendererMixin`, `PerfSodiumSectionsMixin`)
- `PerfStats` keeps 18 atomic counters: trees grown and their ms, shared, evicted, trim ms, regrown for LOD; season
  cells; LOD, season and all dirtied sections; dirty deferred; wildlife skinned; deer posed; textures decoded; upload
  ms; `allChanged`; LOD tick ms; cubes baked. Each counter costs one atomic add per event, never per block or per
  vertex.
- The "all sections dirtied" counter has two hooks:
  - With Sodium, a `@Pseudo` mixin on `RenderSectionManager.scheduleRebuild(IIIZ)` counts it. This is optional
    (require = 0).
  - Without Sodium, `LevelRenderer.setSectionDirty(IIIZ)` counts it (priority 2000, require = 0). The same mixin
    logs `allChanged` with its caller.

## 3. Hitch logger: how to use it
- Turn it on with `/frontierperf on`, which lasts until `off` or a restart. Or set `performance.hitchLogger = true`
  in `config/frontierhunts-client.toml`.
- Other commands: `/frontierperf off`, `config` (follow the toml again), `flush` (write the window now),
  `mark <note>` (write the window now with a note, e.g. `mark flying over spruce forest`), and `/frontierperf` for
  the status.
- The threshold is `performance.hitchThresholdMs = 25`.
- Every 10 s it appends a summary to **`logs/frontierhunts-perf.log`** on a background thread. The log starts over
  past 16 MB. The summary has:
  - fps; p50/p95/p99/worst frame time; the hitch count;
  - client tick avg/max; GC pauses (count/ms, from the GarbageCollectorMXBean, concurrent cycles excluded);
  - the lowest rebuild share;
  - every counter per second;
  - the tree cache line (trees, quads and members against their budgets, LOD subjects and bands);
  - one entry per hitch (up to 96): its ms, tick ms, GC pauses in that frame, heap MB, particle count, and the
    counters **during that frame** and **in the ~0.5 s before it**.
- `allChanged()` reloads are logged with the calling classes.
- The header records the preset, world look, tree detail, animal style/detail, effects, render distance, shader pack
  on/off, Sodium/Iris/DH, GPU, heap, cores and the GC.
- F3, while it is on, shows three `Frontier perf log` lines: hitches this window, last/typical frame, fps, rebuild
  share, then the per-second counters.
- Cost when off: one boolean test per frame and a config read every 128 frames. No allocation, no MXBean calls.

## 4. Shared-file hook lines
- `client/WhitetailRenderer.java`: one counter line in `pose()` after `var2.lastTime = var3;`:
  `com.formaworks.frontierhunts.perf.client.PerfStats.inc(...DEER_POSED); // [perf2]`
- `perf/PerfConfig.java`: `HITCH_LOGGER`, `HITCH_THRESHOLD_MS` (+ getters), defined after `animalAnimationLod`.
- `perf/client/PerfDebugOverlay.java`: `HitchLogger.debugLines(right); // [perf2]`
- Mixin fragment: `patch/_merge/frontierhunts.client.mixins.json/perf2.json`
  (`{"client": ["PerfLevelRendererMixin", "PerfSodiumSectionsMixin"]}`).
- Copied from the decompile into src: none new. `WildlifeTexture`, `SkinnedMesh`, `WildlifeRenderer` and
  `HoundRenderer` were already in src.
- No lang, sound or asset changes.

## 5. Offline checks
- `tools/compile.sh` exit=0. `build.py perf2-test` builds; `check_jar.py --base master`: 0 errors, 0 warnings, and the
  merged mixin json lists both new mixins.
- `TreeLodBench`: same quad counts. Its `dump` of all levels of all 56 fixtures is byte-identical to master. There is
  no IMPOSTOR-ONLY MISMATCH, and `unsafe=0`.
- Concurrency harness: 6 workers look up the same tree from 3 heights. Results: 168 growths for 168 tree-rounds
  (1 per tree), 0 different `Tree` objects returned, no deadlock.
- Eviction harness: 56 trees in one world under a 150k-quad / 20k-member budget, 4 workers. The cache stays within
  budget, counters re-sync, and no lookup fails.
- Harnesses added in `tools/perf/`: `GrowProf <imp|far|near> <rounds>` (growth CPU time per tree), `GrowAlloc`
  (bytes allocated per growth), `ConcTest` (shared growth across workers) and `TrimTest` (eviction under a small
  budget). Compile them together with `TreeLodBench.java` (header line of each file).
- The quickselect percentile matches the sort-based one on 200k random arrays (with duplicates). The hitch-log
  summary and F3 formats were exercised.

## 6. In-game test script (coordinator)
1. Build: `python3 tools/build.py perf2-test /home/claude/work/perf2`. Use the **Ultra** preset, Sodium, render
   distance 16, singleplayer. Run `/frontierperf on`. Chat should name `logs/frontierhunts-perf.log`. F3 should show
   three `Frontier perf log` lines.
2. **Baseline.** Stand still in a dense forest (taiga / hunting forest) for 20 s. Then `/frontierperf mark standing
   in forest`. In the log, `treesGrown` per second should be ~0 once loaded, there should be no hitches, and the tree
   cache line should be below budget.
3. **Walk / sprint / ride an ATV** 300 blocks through the forest, then `mark walking`. Expect:
   - `treesShared` > 0 (workers no longer double-grow) and `treesEvicted` small.
   - `treesRegrownForLod` mostly when trees cross 48 blocks.
   - Hitches, if any, list their causes per frame.
   - Compare FPS / p99 with master (same seed and path) and with `/frontierperf off`. The logger itself should cost
     nothing measurable.
4. **Fly with elytra** fast along a forest edge. The F3 rebuild share dips during spikes and recovers. No tree should
   stay half NEAR / half FAR for more than a moment, and no section should be rebuilt constantly: F3 `dirty lod` goes
   to 0 when you stop.
5. **Seasons.** Run `/season set fall mid`. The log shows `seasonDirty` at up to 24 per tick, and lower when frames
   spike. Then stand still through a natural stage change (`/time add` about 2 days): `seasonDirty` should be at most
   ~4 per tick, with no stall.
6. **Wildlife.** Summon 12 wolves and a bison nearby (`/summon frontierhunts:wolf` ...). The first time a species
   walks up, there should be no hitch: the low mesh shows a frame or two longer, then the sculpted body. F3 shows
   `skinned` and `textures`. Compare FPS with ~10 sculpted animals in view against master. Repeat with a shader pack
   (shadow pass). The hurt flash (red overlay) must still show, and so must the glowing (spectral arrow) outline.
7. **Shader toggle.** Turn an Iris pack on and off. The log shows `world renderer reload (allChanged) from
   ShaderState.tick < ...` and trees regrow (expected).
8. **Cabins / timber and bushes.** Look at log cabins, timber piles and hedges/bushes: they must look exactly as
   before (cube logs, leaf cubes).
9. **Memory.** Watch heap in F3 at Ultra: the tree cache takes up to ~8% of max heap (the `quads` budget is in the
   tree cache line). Send the coordinator `logs/frontierhunts-perf.log` from the user's PC.
10. Run `/frontierperf off`. The F3 lines go away, and the log ends with `logger off`.
