# Workstream `hound2` — tracking hound visual overhaul

Branch `hound2`. User feedback: "the hound dog looks awful needs an overhaul looks nothing like a dog and mesh is all weird".
Compiles (`tools/compile.sh` exit=0); jar built with `tools/build.py hound2-test`; `check_jar.py --base master`: 0 errors, 0 warnings.
No gameplay / network / registration changes: only the hound's meshes, textures, box model and the two client rig classes.

## What changed

### Ultra + Balanced mesh (`tools/tracking/hound/hound.py export`)
Rebuilt from the CLEAN `wolfsrc` canine with smooth deformation fields only:
* The wolf's bushy brush is sculpted against the backs of the thighs (it is one surface with them), which is what tore
  and fanned the old tail. Its triangles are now deleted and the opening is closed with a recessed zipper strip (reads as
  the shadowed gap between the thighs); all leftover tail skin weights are handed to the legs / pelvis.
* New **thin whip tail**: separate closed tube (root buried in the rump), carried up in a sabre curve at rest, weighted
  `pelvis -> tail1 -> tail2`.
* Erect wolf ears folded under a smooth **domed hound skull**; new **long leaf-shaped ear leathers** (closed thin shells,
  slight inward curl) hang from the skull sides past the jaw, each on a new bone `ear_l` / `ear_r` (child of `head`).
* Longer, squarer, level-topped **muzzle with deep flews**, slimmer cheeks (wolf ruff gone), flattened mane / hackles,
  throat bib pulled in, lighter frame, **deeper chest**, tucked waist, slightly **longer legs**; Taubin smoothing removes the
  wolf's guard-hair shards on the crown / nape / cheeks / rear thighs. Scale: **0.63 block at the withers** (head top 0.70).
* Ultra 9190 verts / 10586 tris; Balanced 2393 / 2430. Ear + tail + fill UVs live in a strip at the right of the atlas
  (wolf islands squeezed to u < 0.82).

### Coats (same script)
Procedural, short and glossy (no wolf fur noise; only the wolf's low-pass form shading and dark facial detail kept).
Redbone: solid mahogany, darker topline, lighter chest / underside / muzzle, black nose, dark eyes. Bluetick: blue-grey
ticking, black hood / ears / saddle / spots, tan brows, cheeks, muzzle and lower legs. Island colours are bled into empty
texels (no grey seams in mips). Balanced pixel coats via the shared `export.pixel_art`.

### Classic box model (`tools/tracking/hound/classic.py`, `HoundModel.java`)
New vanilla-style hound: deep chest + tucked loin, neck, square skull, long deep muzzle with nose leather, 1x7x3 floppy
ears hanging past the jaw, meaty hind thighs, thin two-piece sabre tail; 64x64 redbone / bluetick textures. `classic.py`
holds the same layout + `setupAnim` mirror and renders textured pose previews. (Old `classic_tex.py` / `classic_view.py`
removed: they wrote the old layout.)

### Rig (`HoundRig.java`, mirrored by `houndrig.py`)
* Tail: rest pose is already carried up; carry = `0.22 sniff + 0.15 bay - 1.45 tuck` (was 0.55 + ...).
* Sit rebuilt: pelvis 0.85, neck/head -0.3 (head level), front legs -0.85 (vertical), hind 0.8 / -1.3 / 0.9 (hocks + rump on
  the ground), tail trails on the ground, drop 0.415 H (front paws stay planted).
* Ears: counter the summed pelvis..head pitch (x 0.9, clamped) so the leathers hang with gravity when sniffing / baying,
  swing with the gait and flare a little when trotting / baying. Uses `m.bone("ear_l")`; meshes without the bones skip it.

## Files
* `src/.../tracking/client/HoundModel.java`, `HoundRig.java` (owned by tracking; no shared files touched)
* `patch/.../models/wildlife/hound_{ultra,bal}.fhsk`, `textures/entity/wildlife/{,real/,bal/}hound_{redbone,bluetick}*.png`
* `tools/tracking/hound/{hound.py,houndrig.py,classic.py}`; previews in `docs/ws/hound2/`

## Previews (`docs/ws/hound2/`)
`turn_ultra_{redbone,bluetick}.png` (side / front / 3-4 / other side / rear), `head_ultra_*.png`, `poses_ultra_*.png` and
`poses_balanced_redbone.png` (stand, trot, gallop, sniff, sniff-walk, sit, bay, sit-bay, tuck), `turn_balanced_pixel.png`,
`classic_{redbone,bluetick}.png` (stand, trot, sniff, sit, bay, tuck; side / 3-4 / front).

## IN-GAME TEST SCRIPT
1. `/give @s frontierhunts:hound_lead`, use it -> hound. Repeat on a second player (or check both coats via `HoundVariant`).
2. Settings -> animal style **Ultra**: look from side, front, behind, close (< 22 blocks) and far (LOD swap): long ears
   hanging past the jaw, square muzzle, thin tail carried up, no torn / stretched triangles, no grey seams.
3. Right-click hound -> **sit**: rump and hocks on the ground, front paws planted (not floating / sunk), head level, tail on
   the ground behind. Sneak-right-click -> stands again.
4. Put it on blood (TRACK): nose down sweeping, ears hang toward the ground (not pointing forward), tail up and wagging.
   At a deer -> **bay**: head up, ears swing back down along the neck.
5. `/summon frontierhunts:grizzly ~8 ~ ~` -> slink: tail clamped down between the legs.
6. Repeat 2-5 with **Balanced** (pixel coat) and **Classic** (box hound: floppy ear boxes, sabre tail, sit / sniff / bay).
7. Shaders on (Iris + Photon): hound renders in all three styles.
