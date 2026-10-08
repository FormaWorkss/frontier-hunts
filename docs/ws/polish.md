# Workstream `polish` — creative-tab icons, World tab tidy-up, ATV reverse, sound mixer + default loudness

Branch `polish`. `tools/compile.sh` → exit=0; `tools/build.py polish-test .` builds; `check_jar.py <jar> . --base master`
→ 0 errors, 0 warnings. Offline checks: `tools/gui/run.sh .` (layout + every sound event has a group: 177/177),
`tools/polish/rev_harness.sh` (reverse gear, ALL PASS), sound gain/trim unit check (in the test notes below).
**Not run in game** (no client here).

User feedback addressed: icons of the bones and of the posts/railings in the creative tab; the "mossy stone looking
thing" at the start of the World tab; ATV reverse "barely moves"; too many volume sliders, 0 must be 0; wind/gusts too
loud; good out-of-the-box defaults.

## 1. Creative-tab icons (rendered and checked offline)
New tool `tools/polish/icon_preview.py`: renders item models exactly as the 1.21.1 ItemRenderer draws them in a slot
(display.gui translate/rotate/scale, -0.5 offset, ortho, z-buffer, back-face cull, nearest texels, element rotations,
uv/uv-rotation, item/generated layers, `neoforge:separate_transforms`, `neoforge:obj`, GUI item lighting). Sheets at GUI
scale 3: `docs/ws/polish/world_tab_before.png`, `world_tab_after.png`, `fitted_after.png`, `bone_icons_32px.png`
(magenta = custom-loader models the tool cannot draw: roof/tent pieces, cot, chair — untouched). A scan of all 292
frontierhunts item models for tiny / off-centre / overflowing icons found only the ones fixed below.

* **Bone finds** (`shed_antler`, `whitetail_skull`, `elk_skull`, `moose_skull`, `bison_skull`, `scattered_bones`): the 16x16
  sprites ran edge to edge with no outline (looked bigger/flatter than everything else). Replaced by **32x32 icons rendered
  from their own Blockbench models** (`tools/polish/bone_icons.py`: frontal/3-4 view, GUI lighting, 4x4 supersampling,
  1 px warm outline + 1 px margin, like the mod's other 32 px icons). Item models unchanged (same texture paths).
* **Lookout stair rail / deck rail / brace / cross brace** (both the `frontierhunts:` blocks in the World tab and the
  `frontierstructures:` copies): their models had **no display transforms**, so the slot showed a 1:1 side view — a thin
  dark bar off the top and bottom. Item models now carry a 3/4 view across the rails (`[25,110,0]`), centred, ~14 px like
  the timber braces beside them, plus ground/frame/hand transforms scaled to their size.
* Also re-fitted (gui transform only): **game pole** (showed a 5x8 px piece), **trail camera** (half size, off-centre),
  **cabin lantern** (6 px speck), **deadfall log** (sat at the bottom), **river pebbles/stones/boulders + mossy** (half
  size in the bottom third of the slot). Generator: `tools/polish/fit_models.py`.
* Camp Post / Big-Buck Board were already flat 32 px icons (items workstream) — checked, fine.

## 2. World tab
`items/WorldTabPolish` (new, MOD-bus `BuildCreativeModeTabContentsEvent`, priority LOW, never throws):
* **`mossy_stone` removed from creative** (tab + search). It was the first slot (a stepped three-box "mossy stone").
  Still registered — creeks, landmarks, cascades and tree roots place it — so worlds keep it and it drops itself. The
  natural rocks are untouched (sculpted river stones/boulders stay in the tab). The tab icon (undergrowth) is unchanged.
* Railings grouped: pine fence → deck rail → stair rail → stair guard → lookout brace → timber brace → lookout cross
  brace → timber cross brace (they were at the head of the building blocks). Bones stay with the forest-floor finds.

## 3. ATV reverse
Cause: reverse thrust was 0.008 b/t² against ~0.009 rolling resistance on grass, and the "stop snap" zeroed the speed
every tick while the throttle was 0 → ~0.5 km/h. Now `landscape/ride/RevGear` (new) is a governed low gear: it covers
the surface's rolling resistance + drag + the slope pulling against it, then closes on **V_REV = 0.18 b/t (13 km/h)** at
≤ 0.0065 b/t² (~2.6 m/s²); above the target it engine-brakes; torque capped (0.032) so very steep slopes still stop it.
Grip clamp (ice slips), deep-water thrust (`AtvWater.thrust`), flood (no drive), fuel (`AtvFuel.brake`: reverse needs a
running engine) all still apply. Harness: 0→9.5 km/h in 1 s, 13 km/h steady on grass/gravel/snow/mud, backing up 20°
fine, backing down 15° held at ~14 km/h.
Hooks in `Atv.java` (`// [polish]`): `V_REV` 0.22→0.18 + `REV_ACCEL`; reverse branch → `RevGear.push(...) * AtvWater.thrust(depth)`;
`revDrive` flag excludes reverse from the stop snap; engine rpm 0.45-0.75 while reversing (was idle-ish).

## 4. Sound
* **Six groups** (was nine): Frontier master, **Wildlife & calls**, **Weapons**, **Weather & ambience**, Vehicles,
  **Cinematic & UI** (`FrontierSoundCategory`). Config keys of kept groups unchanged (`masterVolume`, `wildlifeVolume`,
  `firearmsVolume`, `weatherVolume`, `vehiclesVolume`, `killcamVolume`) so players' choices stay; `callsVolume`,
  `ambientVolume`, `uiVolume` are dropped by the config corrector. Old names `CALLS/AMBIENT/UI` remain as aliases.
* **Slider taper**: gain = (p/100)^1.66 → 50 % sounds about half as loud (-10 dB), 0 % = exactly 0. The factor is
  applied after Minecraft's clamp, so sounds played at volume > 1 (range boost) are silenced too; a 0 % sound is skipped
  by `SoundEngine.play` (zero volume), loops/tickables follow per tick, everything already playing is re-set on slider
  change. Weather beds now `canStartSilent` (at 0 % they idle silently instead of being re-created every tick).
* **Default mix** (`FrontierSoundCategory.trim`, applied by the mixer after the clamp; measured with ffmpeg EBU R128
  short-term loudness of every shipped .ogg): storm beds/gusts were 7-9 dB above vanilla rain/mob level →
  blizzard ×0.42, wind bed + gust rush ×0.36, gust howl ×0.40, rain ×0.5, dust ×0.45, ridge/tree wind ×0.5 (future
  wind/gust/gale events default to the same). Also: songbirds ×0.6, waterfalls ×0.6-0.8, elk mew/bugle ×0.55/0.6, horse
  whistle ×0.5, moose/calls ×0.8-0.85, hunting rifle shot ×0.7, flare ×0.6, bait launcher ×0.8, ATV engine ×0.65-0.75,
  kill cam ×0.75-0.85. Now nothing exceeds about -10 LUFS short-term at the source; most voices -12…-14.
* Slider defaults stay 100 % (100 % = the designed mix above).
* Other defaults reviewed, unchanged (already performance-safe): Balanced preset/quality, Blocky animals, Minecraft world
  look, weather quality Auto, tree detail Balanced, animal animation LOD on, hitch logger off.
* Not covered: vanilla events the mod's predators use (wolf growl, cat hiss…) follow Minecraft's own sliders.

## Files
New: `items/WorldTabPolish.java`, `landscape/ride/RevGear.java`, `tools/polish/{icon_preview,bone_icons,fit_models}.py`,
`tools/polish/rev_harness.sh` + `harness/.../RevHarness.java`, `docs/ws/polish/*.png` (and a one-line note in `tools/ecology/gen_bones.py`: re-run `bone_icons.py` after it), patch item models
(`lookout_*`, `river_*`, `mossy_river_*`, `game_pole`, `trail_camera`, `cabin_lantern`, `deadfall_log`).
Changed: `sound/FrontierSoundCategory.java` (groups, aliases, trims, taper), `sound/SoundMixConfig.java` (comment),
`client/sound/FrontierSoundMixer.java` (taper + trim), `weather/client/WeatherAudio.java` (Bed.canStartSilent),
`landscape/ride/Atv.java` (hooks above), 6 bone item textures, 4 `fs/resources/.../models/item/lookout_*.json`.

## In-game test script
1. Creative, open the **Frontier Hunts World** tab at GUI scale 2 and 3: first slot is forest duff (no stepped mossy
   stone; `/give @s frontierhunts:mossy_stone` still works). Bones (antler, three antlered skulls, bison skull, ribs) are
   outlined 32 px icons the size of their neighbours. After the pine fence: deck rail, stair rail, stair guard, braces,
   cross braces — all 3/4 views centred in the slot. River pebbles/stones/boulders centred. Compare with
   `docs/ws/polish/world_tab_after.png` (vanilla dark-oak texture is a stand-in colour there).
2. Field equipment tab: game pole and trail camera fill their slots. Hold a lookout rail/brace and a skull in first and
   third person, drop one, put one in an item frame: sensible sizes.
3. **ATV**: on grass, stop, hold S: it backs off briskly, ~13 km/h on the speedo within ~2 s; same on gravel/snow/mud;
   reverse up a moderate hill works; back down a hill: speed held ~14 km/h. Engine note rises in reverse. Engine off /
   out of fuel: S does nothing when stopped. 3-block water: flooded, no reverse. Ice: wheels slip, slow.
4. **Sound page**: six sliders. Drag Weather & ambience to 0 % during `/frontierweather blizzard 300` → bed and gusts
   silent at once; back up → they return. Each slider at 0 % = silence for its group (also Frontier master); 50 % clearly
   about half as loud. Previews play at the slider's level.
5. **Default loudness** (fresh `frontierhunts-client.toml`, or Reset page): a blizzard/gale is clearly quieter than before
   and sits under a rifle shot; wind in the forest is a background bed; birdsong, waterfalls, elk bugles, horse whistle no
   longer jump out; rifle shot still loud but not harsh.
