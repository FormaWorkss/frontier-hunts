# Workstream `tent`: sleeping in a tent lies you on the bed roll

Branch `tent`. Compiles (`exit=0`), `build.py tent-test` builds, `check_jar.py --base master` 0 errors / 0 warnings,
`python3 tools/tent/sleep_geometry.py .` prints `OK 0`. Not launched in game.

User bug: "when sleeping in a tent you're not actually on the bed and the visuals are like in the ground".

## Cause
1. Vanilla parks a sleeper at the bed-cell centre, `y + 0.6875`, and the **server** keeps it there (move packets of a
   sleeping player are ignored). The **local client keeps applying gravity** to its own player while asleep
   (`LivingEntity.travel` runs for the local player). On a vanilla bed that is a 0.125 drop onto the mattress; the
   compact tents have no collision under the bed roll, so the local copy fell **0.69 to the tent floor**.
   `TentSleepRender` (body) and `TentSleepView` (first-person eye) were both offsets from that live position, so the
   sleeper's own body was drawn ~0.33 *below the ground surface* (dome/ridge) and the first-person eye sat ~0.01 above
   the dirt (inside the cot in the canvas tent).
   Other players saw the body ~0.02-0.12 into the bag, because they saw the server's position.
2. The per-tent `sleep` offsets were also off the bed roll: dome head 0.15 out through the back wall, ridge head
   in the back wall and the wall-side arm 0.11 through the fabric, canvas-tent arm 0.10 through the side wall and
   the head through the back wall.
3. Backpacker dome art bug: two black cords ran wall-to-wall *through* the dome interior at knee height (an X over
   the bed roll, visible in every interior view) and pierced a sleeper's torso and legs.

## Fix
- `landscape/tent/TentSleepPose` (new, common): the body's model origin is derived **only from the bed block**
  (bed-cell centre + 0.6875 + the tent contract's `sleep` offset rotated by facing; head direction =
  `getBedDirection`). Every client and the server agree on the block, so the result is identical for the sleeper,
  other players and late joiners, independent of where the entity's live position drifted. Also covers beds
  standing in a camping tent (vanilla height; fixes the local 0.125 sink) and the reserve `camp_cot`
  (sheet at 6.8/16, vanilla floated the body 0.14 above it).
- `TentSleepRender`: translates the model by `pose origin - interpolated render position` (not by a fixed offset).
- `TentSleepView` + `client/mixin/TentSleepCameraMixin`: first person, eye = origin + head*0.12 + up*0.30
  (0.42 above the pad, just over the face), looking **up 40 deg** at the ceiling, towards the feet/door. Third
  person (F5 before going to bed): the orbit pivot is the face on the pad and the zoom raycast is re-run from there.
- `patch/data/frontierhunts/compact_tents/*.json`: refitted `sleep` (north-facing, relative to vanilla pos):
  | tent | old | new |
  |---|---|---|
  | solo_ridge_tent | 0, -0.3275, 0.03 | 0.10, -0.300, -0.20 |
  | backpacker_dome_tent | 0, -0.3275, 0.05 | 0.00, -0.300, -0.19 |
  | hunters_canvas_tent | -0.48, -0.0625, -0.14 | -0.60, -0.051, -0.25 |
- `patch/assets/frontierhunts/models/block/compact_tent/backpacker_dome_tent_{0..8}[_open].json`: the 96 quads of
  the two stray interior cords removed (exterior renders pixel-identical, checked).
- No shared-file edits (no lang, config, mixin-list or registration changes). Wake-up stand spot logic unchanged;
  it already collision-checks every candidate (`TentContent.Wake.standInside`), and the offline replay
  (tentcheck/wakecheck) still picks a free spot inside each tent (dome crouch (0.5,2.0), ridge crouch (1.0,2.25),
  canvas stand (0.5,3.25)).

## Offline geometry check (`tools/tent/sleep_geometry.py`, all four facings identical)
Pad = highest bed-roll / cot surface under torso+legs; body back = origin - 0.117 (lying torso half-depth);
clip = deepest tent geometry inside the body (pillow under the back of the head excluded, same as vanilla beds).

| tent | pad top | body back | gap | worst clip | eye above pad | eye clearance |
|---|---|---|---|---|---|---|
| solo ridge (after) | 0.2665 | 0.2705 | +0.004 | 0.013 (wall-side arm edge, 0.2 px) | 0.421 | 0.358 |
| backpacker dome (after) | 0.2666 | 0.2705 | +0.004 | 0 | 0.421 | 0.424 |
| hunters canvas cot (after) | 0.5157 | 0.5195 | +0.004 | 0.019 (arm edge vs side wall) | 0.421 | 0.280 |
| ridge, before, server pos | 0.2667 | 0.2430 | -0.024 | 0.127 | 0.393 | 0.170 |
| dome, before, server pos | (cords) | 0.2430 | - | 0.116 | 0.300 | 0.203 |
| canvas, before, server pos | 0.5157 | 0.5080 | -0.008 | 0.175 | 0.409 | 0.170 |
| any tent, before, local player | - | 0.71 (dome/ridge) / 0.70 (canvas) below the pad | - | - | eye 0.01 above the floor (dome/ridge), inside the cot (canvas) | - |

Body lies centred on the bag lengthwise (dome bag z 0.72-2.72, body 0.79-2.66; ridge bag 0.83-2.80, body
0.78-2.65); canvas cot is 1.56 long so the feet overhang its foot end by 0.3 rather than the head going through
the back wall. Ridge body is 0.10 towards the ridge from the bag centre and canvas 0.13 off the cot centre:
Minecraft's 0.94-wide arms otherwise poke through the low sloped wall / side wall.

## In-game test script
1. `/time set 13000`, `/gamerule doDaylightCycle false`. Give the three kits: `/give @s frontierhunts:solo_ridge_tent`,
   `frontierhunts:backpacker_dome_tent`, `frontierhunts:hunters_canvas_tent`.
2. Pitch each one facing a different direction (stand facing N, E, S, W when placing), on flat grass.
3. Right-click inside each tent (not the door) to sleep. First person: the view is from just above your face,
   looking up at the tent ceiling towards the door; no dirt/grass, no black X across the dome.
4. Before sleeping press F5 once (third person) and sleep again: the body lies on the sleeping bag / cot, back on
   its surface, head at the back (pillow / bag hood), feet towards the door; the camera orbits the body, not a
   point in the ground.
5. Second client: stand at the open door / break a wall-adjacent block and look at the sleeper: same pose as in 4.
   Leave and re-enter tracking range (walk 100 blocks away and back) while they sleep: still on the pad.
6. Wake (leave bed / morning `/time set 0`): you stand up inside the tent (crouched in dome/ridge, standing in the
   canvas tent) without suffocation damage, door reopens.
7. Camping tent: place a vanilla bed inside a pitched camping tent and sleep: body on the mattress, eye just above
   the pillow looking up at the canvas.
8. Repeat 3-4 with shaders on (Iris/Photon): body shadow comes from the body on the pad.
