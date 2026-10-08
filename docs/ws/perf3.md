# Workstream `perf3`: allocation, fast travel and spike sources (branch `perf3`)

The request was "make sure lag is perfect: no spikes or FPS drops wherever you are and whatever you are doing". The
evidence is the 1.2.8 hitch log (`logs/frontierhunts-perf.log`) and client log from the author's PC: Ultra preset,
realistic world, render distance 10, Sodium + Iris + Distant Horizons, 16 cores, 8 GB heap. Every change here is
marked `// [perf3]`. Nothing was run in the game, because the sandbox has no client. Each change was measured offline
with the harnesses in `tools/perf/` and checked to produce bit-identical output. The game checks the author should
run are at the end.

## 1. What the log shows

| window | fps | what the counters say | reading |
|---|---|---|---|
| before flying (03:59:04-24) | 90-133 | 8-16 trees grown/s, 2-3 GC pauses / 10 s | normal |
| fast creative flight, 5x (03:59:34) | 47, worst frame 461 ms | 345 trees grown/s, 1,628 ms/s growth CPU, **22 GC pauses = 929 ms in 10 s**, 44 evicted/s, 40 regrown-for-level/s, the heap rising ~500 MB in fractions of a second | **proven: allocation from tree growth and baking drives the GC pauses** (see section 2 for the bytes per tree) |
| just after landing (03:59:44-04:00:05) | 24-37, p50 41 ms | 87-164 trees grown/s, 0-23 evicted/s, cache 3.0-3.3M of 3.6M quads, GC only 2-7 pauses / 10 s | **likely mostly not ours**: frames stay at about 40 ms even in frames that grow nothing and have no GC. The probable cause is the GPU drawing a dense Ultra forest with a shader pack at 4K (186 full-detail trees plus 760 distant 3D trees). The CPU load from generating new terrain after a 5x flight (the integrated server, DH LODs and Sodium meshing) adds to it. The `PERFORMANCE` tree detail at 04:01:15 gave 68 fps in the same place. |
| 04:00:15-04:01:05 and 04:01:55-04:03 | exactly ~31 and exactly 15 | every hitch is marked `menu/loading`; frame times are flat (p50 32 / 66.75 ms), even with 0 trees cached (04:02) | a frame cap with a screen open. This is consistent with **Dynamic FPS**, and with nothing the mod draws. The client log also shows **three copies of the Dynamic FPS jar** (`dynamic-fps-…neoforge.jar`, `… - Copy.jar`, `… - Copy - Copy.jar`); the author should delete the two copies. |
| steady, any window | - | `sectionsDirty` 190-350/s with no flight | vanilla light and block updates (counted on every `scheduleRebuild` call); only `lodDirty` is ours, and it is governed |

## 2. Measurements (before = master 1.2.9, after = perf3; sandbox: 2 shared cores, so CPU numbers carry ±10% noise)

### Tree growth, per grown tree (`tools/perf/GrowBench`, 56 fixture trees × 16 rounds, the mean of the second half)

| level | allocated MB/tree before | after, pool warm (steady state) | after, pool empty (worst) | CPU ms/tree before | after (warm / empty) |
|---|---|---|---|---|---|
| impostor only (16 quads) | 0.622 | **0.079** (7.9×) | 0.082 | 1.84-2.14 | **0.90-1.11** |
| distant crown (1,732 quads) | 1.19-1.24 | **0.117** (10×) | 0.429 (2.8×) | 2.84-3.09 | **1.45-2.01** / 2.52 |
| full detail (7,897 quads) | 2.794 | **0.184** (15×) | 1.629 (1.7×) | 5.42-5.53 | **3.66-4.17** / 5.03 |
| cached lookup (every log and leaf block of every rebuilt section) | 24 B/call | **0 B/call** | | ~1.1-1.2 µs (validation dominates) | unchanged |

The bench world was itself allocating, because it boxed a `Long` on every block query. That inflated the old
`GrowAlloc` numbers by about 10%. It now reads a primitive index and gives the same answers, with the same geometry
hash.

### Baking (`tools/perf/BakeCheck`, every quad of the 56 trees, 2 shader states × 2 tints × 3 rounds)

| | before | after |
|---|---|---|
| bytes allocated per baked quad | 632-672 (a new `QuadBakingVertexConsumer` builds an IdentityHashMap and an int[32] for every quad) | **176** (only the kept BakedQuad and its data) |
| BakedQuads compared | | 5,306,484, **0 different** (vertex ints, tint, direction, sprite, shade, AO), including after quads abandoned half-way |

The same reusable consumer now serves the forest-floor dressing, which is re-baked on every rebuild, smooth snow on
every snow block, and loose leaves.

### What a grown tree costs the game in total (growth + bake; the old GrowAlloc saw only the growth)

| level | before | after (pool warm) | after (pool empty) | garbage only (excludes the 176 B/quad baked mesh the cache keeps), before → after warm |
|---|---|---|---|---|
| full | ~8.1 MB | **~1.58 MB (5.1×)** | ~3.0 MB (2.7×) | 6.7 MB → 0.19 MB (35×) |
| distant | ~2.4 MB | **~0.42 MB (5.6×)** | ~0.73 MB (3.2×) | 2.1 MB → 0.12 MB (17×) |
| impostor | ~0.63 MB | **~0.08 MB (7.4×)** | | 0.63 → 0.08 MB (7.7×) |

The log's flight grew 345 trees/s, and at ~5 MB of garbage per tree that is ~1.7 GB/s: the 500 MB heap jumps and the 22
pauses. At the after-rates the same flight would make roughly 70-350 MB/s.

### Smooth snow (`tools/perf/SnowCheck`, 6,400 synthetic snow blocks)

| | before | after |
|---|---|---|
| allocated per snow block mesh build (field + crown pillow, before baking) | 16.6 KB | **3.5 KB** |
| quads, every field compared | identical (the same all-blocks hash from both builds), 0 blocks differ when built from pooled quads | |

On top of this, the bake itself saves ~500 B per snow quad.

### Fast travel (`tools/perf/FlightSim`): real growths, a 14-block grid of the fixture trees, the Ultra bands, a cache budget scaled to the forest's density

| speed | phase | trees grown/s before → after | **trees grown more than once** | regrown for a level | growth CPU ms/s | growth MB/s |
|---|---|---|---|---|---|---|
| 54.6 b/s (5× fly) | 15 s flight | 147.9 → 117.5 | **537 → 81** | 328 → 0 | 583 → 381 | 223.5 → 19.2 |
| | 10 s landed | 3.7 → 0.7 | 37 → 7 | 0 → 0 | 10 → 2 | 5.4 → 1.3 |
| 25 b/s (elytra) | flight | 83.9 → 68.9 | **270 → 46** | 145 → 0 | 360 → 260 | 126.6 → 12.6 |
| 12 b/s (fly / ride) | flight | 54.9 → 50.1 | **141 → 69** | 64 → 43 | 254 → 198 | 82.9 → 9.8 |
| 5.6 b/s (sprint) | flight | 32.3 → 31.5 | 67 → 51 | 36 → 36 | 148 → 107 | 49.9 → 6.8 |

The cost of the fast-travel changes is memory. During a fast flight the cache peaks at the new hard cap, 125% of the
budget: about 4.5M quads (~840 MB) instead of 3.6M (~670 MB) on an 8 GB heap. It stays near 120% once the player has
landed. The model has limits: chunks arrive at once, and Sodium's own build order, frustum and upload limits are not
modelled. The counts are relative, not a frame-time prediction.

## 3. What changed and why

### Tree growth allocation (`client/tree/TreeGrowth`, `TreeCrown`, `TreeImpostor`, `LiveWorld`)
- **Per-thread scratch** (`TreeGrowth.Scratch`). The block maps (component, leaf distances, owned leaves, light and
  ground caches, foreign logs), queues, sorted key arrays, holder indexes, the quad list, the impostor sampler and the
  ring and point buffers were allocated for every growth: 0.6 MB for an impostor-only tree. Each mesh worker now keeps
  one set and clears it, and `LongMap.clear` shrinks a map that a giant tree blew up. If a thread's scratch is still in
  use, the growth takes a fresh one. That is never expected to happen.
- **Pooled float quads** (`TreeGrowth.newQuad`/`recycle`). `TrunkModel.bakedCell` already dropped a cell's float quads
  right after baking it. It now hands them to its thread's pool (at most 16,384 quads, ~2.8 MB per thread) before
  clearing the lists, and growths on that thread reuse them. A pooled quad is reset to exactly the state of a new one.
  A quad that both levels draw is pooled once: it is marked through its `light`, which is reset on reuse. Impostor
  quads are pooled too. `/frontierperf` shows `treeQuads` and `treeQuadsNew`, and `1 - new/quads` is the pool hit rate.
- **No boxing or vector temporaries**:
  - The leaf BFS queues and the leaf and kept lists are `long[]`, no longer `ArrayDeque<Long>`/`List<Long>`.
  - `findLog` uses no allocation.
  - The majority foliage count uses two small arrays instead of a `HashMap<Object,Integer>`. The first material to lead
    still wins ties.
  - The limb and trunk tubes are the old `sub/add/scale/cross/norm` code written out on floats, operation for
    operation. Java float arithmetic is strict, so the bits are the same.
  - Rings and polylines are flat reused buffers.
  - The crown's per-card centre and axes and its limb points live in reused buffers, one per nesting depth.
  - Each stem's foot centre is computed once, no longer per leaf × stem.
  - The impostor's percentile arrays are reused.
  - Cell lists are sized from a counting pass, so they no longer grow from 10 and copy.
- **Cache-hit lookups allocate nothing.** A per-thread `Probe` replaces the `CacheKey` that was created for every log
  and leaf block of every section rebuild. ConcurrentHashMap compares `probe.equals(storedKey)`, so it matches
  exactly. The key is still created on insert.
- `LiveWorld.construction`: no `Direction.values()` clone and no `new BlockPos` × 6 per log.

### Baking (`perf/client/FastBake`, new; `TrunkModel`, `LeafModel`, `ForestFloorModel`, `season/client/SmoothSnowModel`)
- There is one reusable `QuadBakingVertexConsumer` per thread. `bakeQuad()` copies the data out and zero-fills its
  buffer. `FastBake.begin()` restores every other property a new consumer starts with: tint -1, direction DOWN, the
  unit sprite, no shade and no AO. A quad begun but never baked marks the slot busy, and the next `begin` then uses a
  brand-new consumer.
- `TrunkModel.split`: `get`/`put` replace a capturing lambda per quad.

### Fast travel (`TreeLod`, `TreeGrowth.trim`)
- **Range-aware cache trim.** Each trim used to cut the cache to 80% of its budget, furthest first. A dense Ultra forest
  keeps more than 80% inside the render distance (3.0-3.3M of 3.6M in the log), so every trim evicted visible trees.
  The next rebuild of their sections grew them again, and the log shows that churn after landing. Now:
  - trees beyond the render distance plus 2 chunks go first, down to 95%;
  - trees in range may use up to `HARD_CAP` = 1.25 × the budget before any of them is evicted, and then only down to
    95% of the cap;
  - a trim that frees nothing is not retried for 250 ms.

  `TreeLod` publishes the range (`TreeGrowth.focusRange`).
- **Velocity look-ahead for growth levels** (`TreeLod.growNeed(…, vx, vz)`). Faster than 8 b/s, which is faster than
  sprinting, a tree whose closest approach along the camera's path in the next 6 s is inside the full-detail band is
  grown with both levels at its first build. A tree that will be seen in 3D is not grown impostor-only first. Before,
  each tree near a fast path was grown 2-3 times within seconds. **What is drawn is unchanged**: the drawn level is still
  `treeLod`'s, by distance. Only the levels a growth contains change. A jump of more than 32 blocks in one tick (a
  teleport) resets the velocity.
- **Snow-print fill sweep spread out** (`season/client/SnowPrints`). While it snowed, the mod re-dirtied the sections of
  *every* print in the store, up to 24,000, in one tick every 64 ticks. That made a rebuild burst every 3.2 s in snow
  country. Now each tick sweeps 1/64 of the store with a resumable iterator, so each print is still swept once per ~64
  ticks at the same fill rate.

### Smooth snow (`SnowField`, `CrownSnow`, `SmoothSnowModel`)
- Snow quads have ten arrays each and live only from build to bake in the same call. They are now pooled per thread:
  reset on reuse, and handed back right after the bake. The `int[4][2]` corner tables are gone too. Snow quads are
  bit-identical (SnowCheck).

### Main-thread audit (goal 3): result
I checked the always-on client tick and frame subscribers in `src`: weather fog and colour, sounds and ambience,
bird, hound, sled and fishing scans, snow glints, the mesh, FOV and animation level of detail, the HUD, and TreeLod's
tick and schedule. They are tick-rate scans over `entitiesForRendering` or small fixed loops with no per-frame
allocation worth fixing. The perf2 workstream had already fixed the deer and wildlife render paths. The only
main-thread spike source I found was the snow-print sweep above. I found no per-frame O(n) scan of trees or LOD
subjects outside F3 and the hitch-log window flush. Classes that exist only in the base jar (not in `src`) were not
audited.

## 4. What is proven offline, and what is not
- **Proven (bit-exact or measured):**
  - Tree geometry is identical. `GrowHash` covers 56 fixtures × 3 levels, every float of every quad, the NEAR/FAR
    sharing, members, expected kinds and sockets. Rounds 2-3 are built from recycled quads, and the hash is the same
    as master's.
  - Bakes, snow quads and allocation are identical and measured as in the tables above.
  - The concurrency harnesses still pass. `ConcTest`: 1 growth per tree with 6 workers and no mismatches. `TrimTest`:
    the cache stays within the hard cap.
- **Likely, not proven:**
  - Fewer and shorter GC pauses during flight, from 4-35× less garbage per tree.
  - Less worker CPU during fast travel.
  - No 3.2 s rebuild bursts while it snows over prints.

  Frame times cannot be measured without the client.
- **Not addressed (outside this code, or a quality trade-off):**
  - The ~31/15 fps windows: a frame cap with a screen open.
  - The 24-37 fps after landing in a dense forest: GPU and terrain generation. Cutting it needs fewer quads, which is
    what the `PERFORMANCE`/`BALANCED` tree detail does.
  - Uploading large tree section meshes: Sodium.
  - Each section rebuild still re-validates every tree it draws against the world: ~1,000 block reads per tree per
    section. That is worker CPU only.

## 5. Risks
- **Memory:**
  - The tree cache may now hold up to 125% of its old budget while everything in range is needed: ~+170 MB at most on
    an 8 GB heap at Ultra.
  - Per-thread pools add up to ~2.8 MB (tree quads) + ~0.3 MB (snow quads) per chunk-mesh worker.
  - Scratch maps are kept per worker.
- **Quad pool aliasing:** only `TrunkModel.bakedCell` hands quads back, inside its per-cell lock, right before it
  clears the cell's lists. Nothing else reads a tree's float quads (checked across `src`). A double hand-back is guarded
  by the mark.
- **The look-ahead grows full detail earlier** for trees the camera is heading towards. If the player turns away, that
  growth was wasted. Each such growth is still cheaper than the old distant-then-full double growth.

## 6. In-game check (`/frontierperf on`, Ultra, the same seed and path as the 1.2.8 log)
1. Stand in a dense forest for 20 s, then run `/frontierperf mark standing`. Expect `treesGrown` ~0, and `treeQuadsNew`
   well below `treeQuads` once a few trees have been baked (the pool hit rate). Expect no change in look, including
   with Iris and a shader pack toggled. Trees must have no missing or garbled leaves or bark.
2. Fly at 5× for 10-15 s as in the log, then run `/frontierperf mark flight`. Compare with the 1.2.8 window: expect
   `GC` pauses well under 22 per window and under 929 ms, a lower worst frame, `treesRegrownForLod` near 0, and fewer
   `treesGrown`.
3. Land and hover for 30 s, then run `mark landed`. Expect `treesEvicted` ~0 and growth falling off quickly. The fps
   there is mostly GPU- and terrain-bound (see section 1). Compare with `treeDetail = BALANCED` to see the geometry
   cost.
4. In snow country while it snows, after walking around a lot (many prints): watch for regular hitches every ~3 s. They
   should be gone. Prints should still fill in slowly as before.
5. Close menus during measurements. With Dynamic FPS installed, an open screen or an unfocused window caps the fps
   (the 31 and 15 fps windows). Remove the two duplicate Dynamic FPS jars from the mods folder.
6. F3 with the logger on also shows the two new counters.

## Files
- New:
  - `perf/client/FastBake.java`
  - harnesses `tools/perf/GrowHash.java`, `GrowBench.java`, `BakeCheck.java` (needs the offline-only stub in
    `tools/perf/stub/`, which is never shipped: `build.py` compiles `src/` and `fs/java` only), `SnowCheck.java`,
    `FlightSim.java`
- Shared files with small `[perf3]` edits:
  - `TrunkModel`
  - `LeafModel`
  - `ForestFloorModel`
  - `SmoothSnowModel`
  - `SnowPrints`
  - `SnowField`
  - `CrownSnow`
  - `PerfStats`, with two counters added: `treeQuads` and `treeQuadsNew`
- Rewritten internals with the same output: `TreeGrowth`, `TreeCrown`, `TreeImpostor`, `TreeLod` (`growNeed`,
  velocity, focus range).
- Untouched: GUI, workbench, first hunt, Handbook.

Run the harnesses (from the repo root, after `compile.sh <repo> <classes>`):
```
CP=<classes>:$(cat /home/claude/fh2/.infra/cp62.txt)
javac -d /tmp/b -cp $CP tools/perf/TreeLodBench.java tools/perf/GrowHash.java tools/perf/GrowBench.java tools/perf/FlightSim.java tools/perf/ConcTest.java tools/perf/TrimTest.java tools/perf/BakeCheck.java
java -cp /tmp/b:$CP com.formaworks.frontierhunts.client.tree.GrowHash 3 recycle     # diff the per-tree lines against master's
java -cp /tmp/b:$CP com.formaworks.frontierhunts.client.tree.GrowBench 16 recycle
java -Xmx1500m -cp /tmp/b:$CP com.formaworks.frontierhunts.client.tree.FlightSim 54.6 15 10
javac -d /tmp/stub -cp $CP tools/perf/stub/UnitTextureAtlasSprite.java && java -cp /tmp/stub:/tmp/b:$CP com.formaworks.frontierhunts.client.tree.BakeCheck
javac -d /tmp/s -cp $CP tools/perf/SnowCheck.java && java -cp /tmp/s:$CP com.formaworks.frontierhunts.season.client.SnowCheck
```
