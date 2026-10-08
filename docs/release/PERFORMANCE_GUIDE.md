# FrontierHunts performance guide

This guide is about measured frame rates, not promises. Every number here comes from the built-in benchmark on a
named PC, with the settings written next to it. Your numbers will differ. The benchmark lets you measure your own and
compare.

## Measure it yourself
1. Stand somewhere typical: a forest edge, a meadow, a river or a waterfall.
2. Type `/frontierperf benchmark`. The view turns slowly once around over 60 seconds. Don't touch anything.
3. The result appears in chat, and the full report is saved to `logs/frontierhunts-benchmark-<time>.txt`:
   - average FPS and the **1% low** (the average of the slowest 1% of frames, which is what stutter feels like);
   - frame-time percentiles and memory use;
   - the settings that matter: preset, render distance, Sodium, Iris, shader pack and Distant Horizons.

Compare runs from the same spot, and change one setting at a time. `/frontierperf benchmark 120` runs for longer.

## Measured results

**No results yet.** No benchmark has been run on a real game client for this guide, so it makes no frame-rate claims.
The first rows will come from the author's PC. Each row comes from one benchmark file, kept in
`docs/release/benchmarks/`, and quotes it exactly: hardware, settings, average FPS, 1% low and heap peak.

| PC (CPU / GPU / RAM) | Scene | Preset | Render distance | Sodium / Iris / shader pack / DH | Avg FPS | 1% low | Heap peak |
|---|---|---|---|---|---|---|---|

The three standard scenes, so rows compare:
1. **Forest edge:** stand at the edge of a forest with a meadow in view.
2. **Waterfall:** stand within 20 blocks of a waterfall.
3. **Camp:** stand in a camp with tents, a fire and other players' gear around you.

Run each on your usual settings, then once more with the shader pack off.

## Shader packs tested
Only packs that have a benchmark row above and a visual check (snow, water, grass, animals, scopes, HUD) count as
tested. None are listed yet. Other packs may work, but they haven't been checked.

## Settings that matter most (biggest first)
1. **Render distance and Distant Horizons quality.** These cost the most in any modpack.
2. **Shader pack.** It often costs more than everything the mod draws.
3. **Graphics preset:** *Vanilla* draws plain Minecraft animals, grass and trees. *Ultra* draws sculpted animals,
   realistic trees, meadow grass and full effects.
4. **Tune Ultra for your PC** (Performance page):
   - *Distant trees*: where trees turn into cutouts.
   - *Realistic detail*: full-detail animal distance and fur size.
   - *Distant animal animation*.
5. **Waterfall detail** and **Storm effects.** Both scale with Minecraft's Particles setting.
6. **Frontier snow style.** Vanilla snow is cheaper to build when chunks load.

## Memory
The jar is about 200 MB, but the download size says little about memory use. The benchmark file records heap average
and peak; use that to decide how much memory (`-Xmx`) to give the game.

## Hitches (short freezes)
Turn on the **Hitch logger** (Performance → Diagnostics, or `/frontierperf on`). Every frame slower than the threshold
is written to `logs/frontierhunts-perf.log` with what the mod was doing at that moment. Attach that file to bug
reports about stutter.
