# Workstream `shaderperf`: running well under any shader pack (branch `shaderperf`)

Report: a friend plays with a shader pack (Iris, likely with Sodium) and is "laggy most of the time, and certain spots get
worse". Goal for 1.4.0: run well with any pack, with no change to how things look without shaders. Every change is marked
`// [shaderperf]`. There is no client in the sandbox, so nothing was run in game. The findings come from reading the
source (and the decompiled base-jar classes) and from the per-tree quad counts measured in `perf.md`.

## Why shaders hurt this mod in particular

An Iris shader pack draws the terrain at least twice: once into the sun's shadow map (out to the pack's shadow distance,
often 128-256 blocks) and once for the scene. Many packs add more passes on top. Block entities and entities are drawn
again in the shadow pass too. Particles, the GUI and `RenderLevelStageEvent` handlers are not drawn twice. So a feature
that costs **chunk geometry** costs about twice as much with a pack. Ultra's realistic world is mostly chunk geometry:
3D trees, their distant crowns and the dressed forest floor.

## Findings, ranked by expected impact

### 1. Realistic tree geometry in chunk meshes, drawn in both passes (the "laggy most of the time" cause)
- **Evidence:** `perf.md` measured an average of **6,288 quads** for a full-detail tree, **1,733** for a distant 3D tree
  and **16** for a cutout (56 fixtures, `TreeLodBench`). The author's 1.2.8 hitch log (perf3 section 1) was taken with
  a shader pack at 4K, Ultra, render distance 10. In a dense forest it showed **186 full-detail trees + 760 distant 3D
  trees**, about **2.5M tree quads**, and ~40 ms frames even with no growth and no GC. `PERFORMANCE` gave 68 fps in the
  same place. With the pack's shadow pass that is ~5M tree quads drawn per frame, plus alpha-tested overdraw.
- Tree quads per tree detail, from the log's own densities (0.0257 trees/m² inside the full-detail band, 0.0104
  beyond it, out to 160 blocks):

  | tree detail | full-detail trees | distant 3D | cutouts | tree quads | with a shadow pass |
  |---|---|---|---|---|---|
  | Ultra (48 / 160), the default | 186 | 760 | 0 | ~2.49M | ~5.0M |
  | Standard (32 / 96) | ~83 | ~268 | ~500 | ~1.0M (40%) | ~2.0M |
  | Performance (24 / 64) | ~46 | ~115 | ~650 | ~0.5M (20%) | ~1.0M |

  The forest-floor dressing (~1.2 quads per shaded ground block, `FloorQuadSim`) follows the same bands. Beyond the
  cutout distance, it is not drawn at all.
- The geometry lives in Sodium's section meshes. Iris draws those same meshes in the shadow pass, so the mod cannot drop
  quads from the shadow pass alone. The lever is how much 3D tree there is.
- **Changed:** a new `performance.lighterWithShaderPacks` setting, on by default. While an Iris pack is on, trees use the
  next lower *Distant trees* step: Ultra draws as Standard, Maximum as Ultra, Standard as Performance. Under shaders the
  Ultra preset then draws about 2.0M tree quads per frame. That is less than Ultra without shaders (2.5M). Without a
  pack, nothing changes.

### 2. Thick pasture grass in the shadow pass
- The pasture block is 8 quads, and `GrassThickness.THICK` (the Ultra preset value) doubles it to 16
  (`AlpineGrassModels.Sward.redraw`). `ShaderFoliage` gives these blocks the pack's grass id, so packs wave them and
  cast their shadows. That means 16 overlapping alpha-tested cards per block, drawn in both passes, over whole meadows.
- **Changed:** the same setting draws Thick as Normal while a pack is on.

### 3. Flashlight beam: up to ~1,800 full shader set-ups per frame (night, "certain spots")
- `FieldLightProjection` draws the beam's terrain tiles (`FieldLightTerrain`, 4x4x4-block meshes, up to `LIMIT = 896`
  per player) twice per frame: a depth pass and a light pass. Each tile used `VertexBuffer.drawWithShader`. That call
  sets ~15 default uniforms and the samplers, applies the program, uploads the uniforms, draws, then unbinds the
  samplers. It also made a `new Matrix4f` and a `new AABB` for each tile. A forest or cave fills hundreds of tiles,
  so a lit flashlight cost thousands of GL calls per frame. A pack's driver state makes each call heavier.
- **Changed:** the shader is applied once per pass. Each tile then re-uploads only `ModelViewMat` and draws, the way
  vanilla draws chunk sections. The light pass also skips tiles the beam's depth pass did not draw. Its fragment shader
  (`field_surface_light.fsh`) discards every fragment outside the beam's shadow frustum, so those tiles never showed.
  Tile bounds and the matrix are reused. The pixels are the same; there are about 3 GL calls per tile instead of ~15-20.

### 4. Deer sign drawn again in the shadow pass
- `DeerSignRenderer` rebuilds a rub or scrape every frame, with a few hundred vertices and block-light lookups. Iris's
  shadow pass calls it again. The art sits millimetres off bark and ground, so its shadow cannot be seen.
- **Changed:** it returns early in the Iris shadow pass (`HuntShaderCompat.shadowPass()`). This is a pure win, and it
  matters most in rut country, where sign is dense.

### 5. Re-meshing when the pack is toggled
- `ShaderState.tick` calls `allChanged()` when the pack switches, and Iris reloads the world too. If the tree bands and
  grass changed afterwards, the world would be re-meshed a second time over the following seconds.
- **Changed:** `ShaderState` calls `ShaderPerf.shaderPackChanged()` first. It applies the tree detail, moves the LOD
  subjects to the new bands, and sets the grass thickness without a separate re-mesh. The re-mesh that was already
  coming then uses the new values. Changing the setting while a pack is on works like changing *Distant trees*:
  subjects move within the per-tick rebuild budget, and grass re-meshes once when the settings screen closes.

### Checked, already shader-aware, no change needed
- **Wildlife, deer and the tracking hound:** wildlife and whitetail renderers reuse the main pass's mesh, pose and skin
  decisions in the shadow pass. An animal seen only by the shadow camera uses the low mesh (`WildlifeRenderer`
  `MeshLod.frame() - skin.mainFrame > 2`). Impact marks and rifle optics skip the shadow pass. The hound (one or two
  per player) still skins in both passes. That is a few hundred µs, so it was left alone.
- **Entity render types:** `HuntRenderTypes` use the vanilla `RENDERTYPE_ENTITY_CUTOUT_SHADER`, so Iris replaces them
  with the pack's program. There is no custom core shader on animals, blood, gear or tents.
- **Custom core shaders** (`alpine_water`, `falls_sheet`, `field_light*`, `flare_glow`, `tracer_glow`, kill cam): each
  is either skipped in the shadow pass, already switched to vanilla render types under a pack
  (`AlpineWaterMaterial.drawCompatible`), or only drawn briefly (kill cam, flares, tracers).
- **Second world renders / readbacks:** only the trail-camera darkroom, and only while its developing screen is open,
  once per photo. The kill cam swaps the camera but does not render twice. There is no per-frame `glReadPixels`.
- **Per-frame textures:** none. `PhoneMap` uploads once per finished map. Trail-cam and photo textures are made once per
  photo. Coats are decoded off-thread (perf2).
- **Water overlays:** `AlpineWaterMesh` is retired. `AlpineImpactWater.render` has no caller.
- **Weather overlay:** 3-5 full-screen GUI quads, only in storms or frost. That is fill rate only, and small next to a
  pack's own composites.

### Found, not changed (with reasons)
- **Grass bent along animal paths (`PassageField`).** It dirties the sections whose bend changed on each trail delta, at
  most every 10 ticks, and every 100 ticks as grass recovers. Near a moving herd that is a steady trickle of section
  re-meshes. Iris's extended vertex format makes each one dearer on the mesh workers. Coalescing would delay the
  bending, which would change gameplay without shaders. If F3 shows constant `sectionsDirty` near herds under shaders,
  the fix is to batch `PassageField.dirty` to ~1 s while `ShaderPerf.lighter()`.
- **Tents, benches, trophy boards, camp posts** re-tessellate per frame, and the tent re-renders a baked model in
  set-up. They are drawn in the shadow pass as well. They are few per camp and they cast real shadows, so they were
  left alone.
- **Flashlight terrain scan.** While a flashlight is held, even switched off, `FieldLightTerrain.update` rebuilds up to
  12 tiles per frame within 1.5 ms. That is unchanged, so the beam is ready the moment it is switched on.
- **Hound shadow skinning:** see above.

## Files
- New: `perf/client/ShaderPerf.java`, which holds the step-down, the hooks and the log line.
- Copied from the base jar into `src` and edited. Bytecode of the unedited copies matches the jar's instruction counts.
  - `client/ShaderState.java`: the hook before `allChanged`.
  - `landscape/AlpineGrassModels.java`: effective thickness, and `followShaderPack()` with no re-mesh of its own.
  - `client/FieldLightTerrain.java`: batched draw and beam cull. The generics the decompiler lost were restored.
- Edited:
  - `perf/PerfConfig.java`: `SHADER_LIGHTER`, `shaderLighter()` and `lighter(TreeDetail)`.
  - `client/tree/TreeLod.java`: `applyDetail` uses `ShaderPerf.treeDetail()`. New `followShaderPack()`. The F3 and
    log lines show the drawn level.
  - `sign/client/DeerSignRenderer.java`: skipped in the shadow pass.
  - `client/FrontierSettingsScreen.java`: the "Lighter with shader packs" toggle on the Presets page (shader-packs
    section) and the Performance page. It is not part of the preset match, so it never makes a preset Custom.
  - `perf/client/HitchLogger.java`: the header shows `[shader pack on: trees drawn BALANCED (set ULTRA)]`.
- **Safety:** Iris is only reached through `ShaderState` (reflection, failure cached as "no pack") and
  `HuntShaderCompat` (Iris API via `ModList`, failure cached). Without Iris, with Oculus or with another shader mod,
  `packOn()` is false and everything behaves as before. Iris and Sodium are never a hard dependency.

## Config
| key | file | default | meaning |
|---|---|---|---|
| `performance.lighterWithShaderPacks` | `config/frontierhunts-client.toml` | true | while an Iris pack is on: trees one *Distant trees* step lighter, Thick grass drawn Normal |

## In-game check list (with Iris + a shader pack, e.g. Complementary or BSL)
1. Use the Ultra preset in a dense forest, render distance 10-12. Run `/frontierperf on` and open F3. The trees line
   ends with `lighter for shader pack: BALANCED`, and `cutouts beyond 96`.
2. Note the F3 frame time / fps. Then toggle **Settings > Performance > Lighter with shader packs** off: within ~1 s
   trees out to 48 blocks regain full detail, and the line says `cutouts beyond 160`. Write down both fps values, then turn it
   back on.
3. Toggle the pack off in the Iris menu (`O` key): trees go back to Ultra in the same reload. The log shows a single
   `allChanged` from `ShaderState.tick` (besides Iris's own), not a second wave of `lodDirty`. Turn the pack back on.
4. On a meadow of frontier pasture grass, the grass looks a little less dense with the pack (Normal), and as before
   without it.
5. At night, hold a lit flashlight in a forest and in a cave. The beam must look exactly as before, with nothing lit
   outside the beam. FPS with the beam on should drop much less than in 1.3.1.
6. During the rut, near rubs and scrapes: the sign still shows. With a pack, rubs cast no shadow (they never visibly
   did).
7. Run `/frontierperf mark shaders forest` and send `logs/frontierhunts-perf.log`. Its header now shows the shader state
   and the drawn tree detail.
8. Without Iris installed, check that nothing changed, and that the log has no `Frontier Hunts` errors or warnings
   about Iris.
