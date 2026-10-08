# Workstream `atv2`: ATV playtest fixes (black ATV under shaders, grime over time, Forza water, water depth, icons)

Branch `atv2`. Builds on `atvgrime` + `atvfuel` (both on master). Compiles clean (`tools/compile.sh`); full jar builds with
`tools/build.py`. `check_jar.py --base master` reports one error, and it is expected (see "Check notes"). Nobody has run
this in game yet: there is no client here.

## 1. Black ATV with shaders: root cause and fix
**Cause.** The `atvgrime` wet look was a separate full-body overlay. It used the texture `atv_grime/wet.png`, a tile whose
every texel is near-black (RGB ~16) at 15-50 % alpha, with brighter beads. That overlay was drawn with
`RENDERTYPE_ENTITY_TRANSLUCENT` and colour-only writes over every part of the realistic mesh. On the blocky model it went
through a custom `VertexConsumer` wrapper (`AtvGrimeRender$UvScale`).
* With shaders off, blending makes that tile a faint darkening.
* Iris shader packs draw entity layers into the opaque g-buffer, where blending is off or alpha is used as material data.
  There, the near-black film became a solid black shell over the whole ATV. The beads showed up as the white specks in
  the screenshot. This only happened after water (wet > 0.03), and the jerry cans and cargo box stayed normal because the
  film was never drawn over the rig.
* Sodium's `VertexConsumerTracker` warning in the log was the `UvScale` wrapper. It disabled Sodium's fast vertex path.

**Fix.**
* The translucent wet layer, its render type, its texture and the wrapper are all deleted.
* Wetness is now a vertex-colour darkening of the vehicle's own paint (`AtvGrimeRender.bodyTint`, at most about -24 %
  red, -22 % green and -17 % blue). Every pack multiplies this colour into albedo, so it looks the same with shaders on and off. Wet mud gets
  a little darker too.
* The only remaining overlays are the mud and snow cutout masks. They use the vanilla entity-cutout shader with binary
  alpha and every vertex attribute written directly into the buffer, like armour layers.
* Hooks: `RealisticAtv.render(..., tint)` is a new overload; `RealisticAtv` was imported from dec62g8 and its decompiled
  `byte` loop index fixed to `int`. The blocky `renderToBuffer` colour `-1` becomes `bodyTint`.

## 2. Grime over time (`AtvGrime.simulate`)
* **Parked or on clean ground:** fresh mud dries to a crust in about 2.5 min, slower when cold. The crust then flakes off,
  a little faster with vibration from driving. A heavy coat is around 0.1 after about 8 min and gone after about 10 min
  in a temperate climate. Wet mud only slumps slowly, so it never disappears instantly. Snow keeps its existing melt times.
* **Rain under open sky:** a heavy coat goes in 60-90 s. The top clears in about 60 s and the wheel wells in about 85 s.
* **Hub-deep water (about 0.35):** the tyres and lower body clean in about 6 s parked and about 3-4 s driving. The body is
  sluiced by wheel spray in about 6 s at speed.

Timings come from `tools/atvgrime_harness/GrimeHarness.java`; its new `[atv2]` sections print them.

## 3. Forza-style water (client, cosmetic)
`grime/client/AtvWaterFx`, `AtvWaterSound`, a new particle `frontierhunts:atv_spray`, and `ScreenSplatter`.
* **Wheel fans:** drops, mist and spray plumes come off every tyre that is in the water. The rear tyres throw a rooster
  tail. A plume rolls each tick so its streak follows its own screen-space velocity, so it never draws as a sideways slash.
* **Bow wave:** a foam crest with a curtain of drops and big plumes at the leading end.
* **Wake:** surface foam behind the ATV.
* **Entry splash:** ploughing into water, or dropping into deeper water, throws a crown of drops, plumes and foam.
* **Sounds:** a layered splash one-shot (thump, sheet, fall-back, droplet tail), looping churn and spray layers (volume and
  pitch follow speed and depth), sloshes while creeping or labouring, and a flood gurgle.
* **Particle budget:** per-kind caps scaled by Effects quality, the Particles option and distance, the same scheme as
  before.
* **Driver screen (first person only):** fine spray specks, droplet clusters, runs, and translucent sheets that slide off
  from the top corners. At speed the airflow pushes drops outward.
  * Water lives 1-4 s.
  * It is spawned away from the centre and drawn at no more than 35 % of its strength in the centre of the view.
  * Total water coverage is capped (`WATER_BUDGET`).
  * Water rinses mud and snow splats off.
  * Nothing is drawn in third person.

## 4. Water depth (`wade/AtvWater`, same code on the driver client, the server and observers)
Depth means the water surface above the ATV's bottom. A 1-block ford is about 0.89 deep and a 2-block ford about 1.89.

| Depth | What happens |
| --- | --- |
| Up to 1 block | Small drag. Top speed is about 51 km/h instead of about 63. |
| About 2 blocks | Heavy v² drag and 50 % thrust: about 10 km/h. The engine surges and bogs. The rider stands on the pegs (+0.22) so their head stays above water. |
| 2.3 or deeper (3 blocks) | The engine floods (`atv.flooded`): no propulsion, plus a gurgle, the stall sound, bubbles and an "Engine flooded" message. The ATV sinks to the bottom and comes to a stop about 1.5 blocks in. |

* After a flood, the ATV restarts (crank, then start) once it has been back under 2.0 deep for 1.5 s.
* `flooded` is now computed in `tick()` on every side. Before, the server never set it for a driven ATV.
* The ATV is never pushed by fluid currents.
* The rider is dismounted only when their eyes are in the fluid (`canBeRiddenUnderFluidType` returns false).
* Getting on a flooded ATV cranks the engine without starting it, and does not use the reserve cans.

Offline check: `tools/atv2_harness/WaterHarness.java`.

## 5. Icons
The GUI scale of each rig item was re-fitted so its largest on-screen extent is about 0.86 of a slot. The Can Carrier
was 1.10 and overflowed the slot.

| Item | Old scale | New scale |
| --- | --- | --- |
| Can Carrier | 1.0 | 0.78 |
| Cargo Box | 1.0 | 0.88 |
| Jerry Can | 0.88 | 0.84 |

`tools/atvfuel_art.py` was updated to match.

## Files
**New**
* `wade/AtvWater.java`, `wade/WadeContent.java` (5 sounds)
* `grime/client/AtvWaterFx.java`, `grime/client/AtvWaterSound.java`
* `tools/atv2_art.py` (`particle/atv_spray_0..3`, `particles/atv_spray.json`, `gui/atv_grime/water.png`)
* `tools/atv2_audio.py` (`sounds/ride/atv_water_*`)
* `tools/atv2_harness/WaterHarness.java`

**Merge fragments:** `sounds.json/atv2.json`, `lang/en_us.json/atv2.json` (5 subtitles and `message.frontierhunts.atv_flooded`).

**Deleted:** `textures/entity/atv_grime/wet.png`.

**Shared-file hook lines (`// [atv2]`)**
* `Atv.java`:
  * a `wade` field;
  * `AtvWater.tick(this)` in `tick()` before the control block;
  * `isPushedByFluid` returns false;
  * `canBeRiddenUnderFluidType` returns false;
  * passenger y `+ AtvWater.standUp(this)`;
  * in `drive()`:
    * `var17 = this.wade.depth` (the old `flooded =` line is removed);
    * thrust scaling (`var20raw` keeps the driver's input for `throttleShown`);
    * the old `*0.9` water damping replaced by `AtvWater.slow` on both axes;
    * `AtvWater.rpm` on the rpm target.
* `AtvFuel.onMount`: a flooded guard.
* `AtvRenderer`: the tint argument on both draws.

## Check notes
`check_jar` reports `ERROR [asset] .../atv_grime/wet.png (changed in patch/) is not in the jar`. The file was deleted on
purpose and nothing references it. The checker lists deleted paths as "changed", so this error is expected.

## In-game test script
Test with shaders off and on (Iris + Photon or Complementary), at the Balanced (blocky) and Ultra (realistic) presets.
1. **Black ATV.** `/atvgrime wet 1`, then `/atvgrime mud 0.6 0.3 1`.
   * Expected with shaders on and off: a slightly darker wet ATV, never black or speckled. The mud reads correctly.
   * Expected in `latest.log`: no Sodium `AtvGrimeRender$UvScale` warning.
2. **Fade.** `/atvgrime mud 1 1 1`, then park in the open with clear weather. `/atvgrime` every 2 min.
   * Expected: moisture falls over about 2.5 min, then the mud steps down. About 0.1 at 8 min, clean at about 10 min.
3. **Rain.** `/atvgrime mud 1 1 1`, then `/weather rain`.
   * Under open sky, expected: clean in 60-90 s, with the top level clearing first.
   * Under a roof, expected: no rinse.
4. **Water wash.** `/atvgrime mud 1 1 1`, then drive into a 1-block-deep river at walking pace.
   * Expected: the low mud is gone in about 3-4 s. Parked hub-deep, about 6 s.
5. **Forza water.** Drive into a 1-block river at full speed, in first person.
   * Expected effects: an entry splash with sound, wheel fans, a rooster tail, bow-wave foam, a wake, and churn and spray
     loops.
   * Expected on screen: droplets, specks and occasional sheets that clear in 1-4 s. The centre stays clear. Mud splats on
     the view rinse off.
   * Press F5: no screen water in third person.
   * A second player watching sees and hears the spray but gets no screen water.
6. **Depth.**
   * 1-block ford: about 50 km/h.
   * 2-block pool: about 10 km/h, the engine surges, the rider rises on the pegs and their view stays above water.
   * 3-block pool: drive in. Expected: gurgle, stall, "Engine flooded"; the ATV stops about a block or two in and sits on
     the bottom. The rider stays seated until their head is under, then floats off.
   * A river current does not carry the ATV.
   * Pull it out, or let it roll back to shallower water: the engine restarts after 1.5 s.
7. **Icons.** In the inventory, the Can Carrier, Cargo Box and Jerry Can fit inside their slots, the same size as the
   items around them.
8. **Dedicated server with 2 clients.** Repeat 6. Expected: the observer hears the stall and restart at the same time,
   and the fuel gauge stops dropping while flooded.
