# Workstream `meshes`: smooth Ultra wildlife bodies, animation audit, faster skinning

Branch `meshes` (from master 644f37a). Applies to the **Ultra** (realistic) wildlife: wolf, coyote, cougar, panther,
lion, cheetah, grizzly, black bear, polar bear, bison, boar, pronghorn and duck. The grouse was audited, but its rebuild was rejected and it keeps master's meshes. The hound is owned by `hound3`
and is untouched (its meshes and HoundRenderer are not edited). Whitetail, elk and moose use their own high-poly
`DeerMeshData` meshes (4 LODs, 1.6-2.8 MB each) and renderer. They are not part of this pass (see Known limitations).
Vanilla box models are unchanged.

## 1. Smoothness (meshes rebuilt offline: `tools/meshes/smooth.py`)
The shipped sculpts were decimated scans. In game that showed up as small polygon facets and lumps. Each
`<species>_ultra.fhsk` and `_bal.fhsk` was rebuilt from master, and a new `_mid.fhsk` level was added:
1. Corner vertices are welded by position. UV seams stay split, so the coat mapping is identical.
2. Degenerate triangles and debris are dropped. Inconsistent winding is repaired (for example, the wolf and coyote each
   had 414 back-to-front triangles).
3. Concave crumple folds are relaxed (wolf: 161 folds down to 9). Isolated spikes and pits are removed (37 down to 0).
4. Volume-preserving Taubin smoothing is applied, damped on crisp convex features: ear rims, hooves, claws and horns.
5. Curved and long edges are refined with curved (Phong) midpoints, within a fixed budget of about 1.45x the
   triangles (wolf 9000 to 13050, bison 10000 to 15500). UVs and skin weights are interpolated for every new vertex.
6. Normals are angle-weighted on the welded surface. Hard edges are kept only on crisp convex creases
   (hooves, horns, claws, beak).
7. Skin weights are the sculpt's own (heat) weights: at most 4 per vertex, normalised. Two alternatives were tried and
   rejected after the audit and contact sheets: re-solving the weights from the skeleton, and dropping the cross-part
   leaks while cutting tail/leg junctions. Both opened tears at the shoulders, hocks and tail root in the gallop, pounce
   and howl poses.
8. `_mid` is the smoothed ultra body decimated to about half of master's triangles (wolf 4500), with the same coat and
   UVs.

Before/after clay renders show the facets gone (cougar shoulder and back, duck breast, wolf flank). See the contact
sheets listed below.

## 2. Animation audit (`tools/meshes/audit.sh`, harness `MeshAudit.java`)
The audit drives the real `WildlifeRig`, ecology `HuntPose` and wingshot `BirdPose` code through these states: idle,
walk, trot, run, gallop, leap, graze, graze-walk, rest, rest+look, alert, alert+look, curious, warn, look left / right-up /
down, flee+look, stalk, stalk-still, chase, pounce, tear, howl, swim, and the bird flight cycle (flap
top/down/bottom/up, takeoff, glide, landing, drum, dead fall, dead down).

It measures these on the skinned vertices:
- edge stretch (p99.9 and max)
- the minimum determinant of the blended skin matrix (candy-wrapper twist / collapse)
- flipped triangles
- vertices buried in the body (winding number)
- feet sinking into the ground

**Defects found and fixed:**
- **Baked grazing poses** (`tools/meshes/feedfix.py`, which edits mesh headers only):
  - The grizzly's front legs had wrapped IK angles: fl_upper -4.86 rad, fr_foot -4.38 rad, and elbows at 180 deg.
    While a bear lowered its head the legs spun almost a full turn and the forearms collapsed into a sheet.
    In graze, skin det went from 0.006 to 0.657, flipped triangles from 91 to 6 and buried vertices from 13 to 0.
    In graze-walk, det went from 0.000 to 0.803.
  - Every angle is now wrapped the short way round, the bear front legs get a shallow bend, and the forage stoop is
    halved.
- Mirroring the opposite-sign knees of the canids and cats was tried. The audit showed it pushes a paw into the soil:
  those signs are the IK planting the paws. It was reverted.
- Pose numbers per species are below (master, then this branch, worst over all states).

| species | stretch p99.9 (worst state) | stretch max | min skin det (twist/collapse) | flipped tris | buried verts |
|---|---|---|---|---|---|
| wolf | 278% -> 418% (pounce) | 734% -> 1621% | 0.04 -> 0.03 | 602 -> 818 | 9 -> 12 |
| coyote | 280% -> 406% (pounce) | 756% -> 995% | 0.04 -> 0.03 | 643 -> 857 | 9 -> 9 |
| cougar | 332% -> 385% (stalk) | 468% -> 606% | 0.00 -> 0.00 | 455 -> 551 | 10 -> 26 |
| panther | 323% -> 380% (stalk) | 468% -> 579% | 0.00 -> 0.00 | 443 -> 546 | 9 -> 21 |
| lion | 116% -> 210% (alert_look) | 238% -> 462% | 0.05 -> 0.03 | 33 -> 94 | 15 -> 22 |
| cheetah | 243% -> 306% (pounce) | 420% -> 414% | 0.01 -> 0.00 | 353 -> 554 | 56 -> 76 |
| grizzly | 318% -> 496% (pounce) | 842% -> 910% | 0.00 -> 0.41 | 187 -> 216 | 22 -> 7 |
| black_bear | 278% -> 352% (pounce) | 465% -> 540% | 0.28 -> 0.28 | 132 -> 257 | 10 -> 24 |
| polar_bear | 564% -> 520% (pounce) | 1175% -> 1124% | 0.31 -> 0.31 | 151 -> 242 | 21 -> 35 |
| bison | 296% -> 332% (leap) | 409% -> 663% | 0.63 -> 0.62 | 488 -> 672 | 39 -> 49 |
| boar | 218% -> 324% (leap) | 365% -> 528% | 0.36 -> 0.36 | 33 -> 37 | 1 -> 1 |
| pronghorn | 102% -> 149% (graze) | 448% -> 770% | 0.23 -> 0.22 | 20 -> 39 | 14 -> 19 |
| duck | 70% -> 101% (graze) | 161% -> 268% | 0.54 -> 0.45 | 7 -> 34 | 2 -> 6 |
| grouse | rebuild rejected: graze stretch rose 214% -> 725% (most likely the tail fan welded to the body); master mesh kept | | | | |

Reading the table (ultra level, worst over all states; full per-state rows: `docs/ws/meshes/audit_master.csv`,
`audit_after.csv`, which also cover the mid and bal levels):
- **Fixed:** the grizzly's collapsed forearms. Graze det went from 0.006 to 0.657 and graze-walk from 0.000 to 0.803.
  Flipped triangles in graze went from 91 to 31, and buried vertices from 13 to 0.
- **Higher numbers that are not new defects:**
  - Stretch percentages and flipped-triangle counts are higher on the rebuilt meshes because they have about 45% more
    and shorter edges near the joints. A relative stretch on a 2 mm edge reads large, and the flipped count scales with
    the triangle count.
  - The skin weights are master's, so the bends have the same character as master. The pose contact sheets confirm
    this: `docs/ws/meshes/poses_{wolf,cougar,duck}.png`, with no tears.
- **Not fixed** (pose amplitudes in code owned by `ecology`):
  - The cats' deep stalk crouch folds the hip skin flat (det 0.00 at bl_upper in `HuntPose` stalk, the same in master).
  - The canid and lion pounce/alert-look blends sit at det ~0.03.
  - Clamping the stalk fold (`HuntPose.rig`, the `front/hind[1..2] = ±2*st` lines) to about ±1.4 rad would fix both.
    This was left to the ecology owner.
- **Grouse** keeps master's meshes.


## 3. Performance (`MeshSkinner`, `MeshLod`, `WildlifeRenderer`)
- **Three levels:**
  - the full smoothed body for the closest animals: effective distance (blocks / animal height / zoom) under
    5/7/9/12 for Animal detail Low/Medium/High/Ultra, at most 1/2/2/3 animals
  - the `_mid` body up to the existing swap distance
  - the `_bal` body with the distant coat beyond it
  - Hysteresis is 12% each way. At Ultra a wolf keeps its full body within about 11 blocks and a bison within about
    23. Scopes and binoculars count through the zoom.
- **Compact influences:** each mesh is compiled once into non-zero influences per vertex, with no 4-slot loop.
- **Fused skinning:** an animal posed this frame is skinned straight into camera space. Bone matrices are
  pre-multiplied by the pose and normal matrices once per bone, so there is one pass over the vertices instead of
  two. When a shader-pack shadow pass also draws the animal, or when it will hold its pose, the model-space skin is
  kept and reused, as before.
- **Still animals** (no gait, leap, hunt pose, flight or swim; head look unchanged within 1.5 deg) re-pose at most 30
  times a second, even up close. Head turns are still every frame. This follows the existing "Distant animal
  animation" performance toggle.
- **Shadow-only animals:** an animal that only the shadow camera sees (off screen) casts its shadow with the low body
  at 20 poses a second.
- No per-frame allocations: buffers are reused per renderer and per animal. Emission still goes through the vertex
  consumer's fast path, so it works with Sodium, Iris and any shader pack.

**Microbenchmark** (`tools/meshes/bench.sh`): thread CPU time, best of 5 alternating runs, 100 FPS simulated, Ultra
animal detail, wolves at fixed distances. The machine is shared, so treat these as relative numbers.

```
wolf tris: master ultra 9000 / bal 2200 | branch ultra 13050 / mid 4500 / bal 2200
per animal per frame, posed every frame, best of 4 (ms): master ultra 0.845  master bal 0.225  branch ultra 0.806  branch mid 0.321  branch bal 0.166  
scene (100 FPS, ULTRA detail)            | before ms/frame | after ms/frame | speed-up | before/after ms per animal
 1 wolves (all walking)                |           0.821 |          0.810 |    1.01x | 0.821 / 0.810
 5 wolves (all walking)                |           3.933 |          2.276 |    1.73x | 0.787 / 0.455
20 wolves (all walking)                |          10.013 |          6.168 |    1.62x | 0.501 / 0.308
 1 wolves (herd: half grazing)         |           0.838 |          0.812 |    1.03x | 0.838 / 0.812
 5 wolves (herd: half grazing)         |           4.126 |          2.098 |    1.97x | 0.825 / 0.420
20 wolves (herd: half grazing)         |           9.786 |          5.610 |    1.74x | 0.489 / 0.280
```

A single close animal costs the same as before even though it has 45% more triangles. Scenes with several animals in
front of the player are **1.6x to 2.0x** cheaper. The 2-3x target was not reached for every walking scene: every
triangle corner must still be written to the entity buffer each frame, and that write is now the floor.

## Files
- New: `wildlife2026/client/MeshSkinner.java`, `wildlife2026/client/MeshLod.java` (self-registering client
  frame hook).
- New: `patch/.../models/wildlife/<species>_mid.fhsk` (13; not the grouse).
- Rebuilt: `<species>_{ultra,bal}.fhsk` (13 x 2).
- Tools: `tools/meshes/` (smooth, feedfix, decimate, render, compare, lods, pose_sheet, audit, bench, tables, harness).
- Shared edits:
  - `WildlifeRenderer.java`, all marked `// [meshes]`: the mid level and tier selection, the kept pose, the fused or
    cached draw, the still/shadow rate.
  - `SkinnedMesh.java`: one field, `MeshSkinner.Compiled compiled`.

## Checks
- `tools/compile.sh`: exit=0
- `tools/rutfight/run.sh` (private class dirs): 432 checks, 0 failed
- wingshot flight harness: ALL PASS (896)
- Audit and bench as above

- wingshot pose harness (`tools/wingshot/harness/poses.sh` against a zip of this branch's duck and grouse meshes):
  POSES OK
- **Not run, blocked by a full disk:** the jar build, `check_jar --base master` and `tools/qa/server_smoke.sh`.
  `/` had 90-230 MB free during this session, and the jar is about 176 MB. No server-side class was touched: the new
  classes are client-only (`wildlife2026/client`, an `@EventBusSubscriber(value = Dist.CLIENT)`), and the meshes are
  resources only. The coordinator must build the jar and run check_jar and the smoke test after merging.

## In-game test script (Ultra preset, Animal detail Ultra)
1. `/summon frontierhunts:wolf ~3 ~ ~` and the same for cougar, grizzly, bison, pronghorn, duck and grouse. Walk
   around each at 2-4 blocks in daylight, with shaders off and then on (Iris + any pack). Check:
   - no faceted shoulders or flanks and no lumpy ridges
   - the coats map exactly as before (eyes, muzzle, hooves)
   - hooves, claws, horns and ears are still crisp
2. Watch each one walk, trot, flee (hit it once) and graze. Check:
   - no leg spins or folds when an animal lowers its head
   - **grizzly:** the front legs stay as legs while it forages
3. Lie-down check: wait for a REST state, or use `/frontierhunts` ecology/test commands where they exist. The body
   settles without the legs crossing through the torso.
4. **Predators:** let a wolf and a cougar hunt (stalk, chase, pounce, tear; wolves howl at night). The body stays one
   piece, the same as master. The tail-to-hock web in a pounce is unchanged from master.
5. **Birds:** flush a grouse and shoot a duck. Wingbeats, the glide and the tumble look the same as before, with a
   smoother body.
6. **LOD:** stand 6 blocks from a wolf and back away slowly to 60.
   - About 11 blocks: full body to mid. About 52: mid to the distant body and coat.
   - No pop or flicker when standing on a boundary.
   - Never a missing texture.
7. **Performance:** `/summon` 10 wolves and 10 bison in front of you within 30 blocks. Compare F3 frame time (and the
   perf overlay's wildlife-skinned count) with master. It should be clearly lower, and a grazing herd lower still.
8. Shadows (Iris pack with shadows): animals behind you still cast shadows and nothing flickers.
9. Vanilla preset: the box models are unchanged.

## Known limitations
- Whitetail, elk and moose (DeerMeshData, separate pipeline) were not rebuilt in this pass.
- The audit's stretch metric counts tiny refined edges near joints. Use the pose contact sheets for the visual check.
  The tail-to-hind-leg web in gallop and pounce comes from the sculpt's weights and is unchanged from master. Fixing
  it needs re-weighting in the source sculpt.
- The grizzly forage pose still dips the front paws or muzzle ~0.2 below the ground surface (it was 0.08, but with
  collapsed forearms).
- Only offline renders and numbers so far. In-game verification on AMD/Nvidia with and without shaders is pending.
