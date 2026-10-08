# Workstream `archery2` - crossbow sight picture, recurves that hit where you aim, a straight-on bow kill cam

Branch `archery2` (from master 1650414, gear.14). Fixes the gear.14 reports: "the crossbow you can't see at all, just
hands in the way; the recurves don't shoot where I'm aiming, the field recurve's sights / arrow holder are off" and
"the kill cam with the bow is weird, the arrows fly sideways". Preset-independent (Vanilla and Ultra alike).

## Causes found
1. **Arrows sideways (world AND kill cam)**: `FieldArrowRenderer` turned the model with `YP(180 - yRot)`, `XP(-xRot)`
   while Projectile rotations are `yRot = atan2(vx, vz)`, `xRot` up-positive and the model's head points -z. Result
   (measured by `tools/archery2` harness): drawn arrow vs flight = 6 deg N/S (pitch inverted), **90 deg on every
   diagonal (sideways), 180 deg east/west (tail first)**. The kill cam's arrow is a client FieldArrow, so it inherited it.
2. **Recurves missing**: (a) the sight/marks appeared at ~70 % draw (anchor curve completed at draw 0.8, HUD faded in
   from aim 0.55) while the arrow is only full speed at 100 % - a release when the sight "looked ready" flew slow
   (recurve speed x draw) and with spread; (b) the traditional reference was a fixed 30 m point-on mark, so at any
   other range it missed by up to ~0.5 m (15 m) unless you gap-shot; (c) the HUD used the rounded-up GUI size
   (`guiWidth * scale`), up to (scale-1) px off the world's centre.
3. **Field Recurve geometry**: arrow nocked at y 0.02 - through the middle of the grip wrap, under the "rest" box; arrow
   on the right of the riser (left-hand bow), top limb canted left.
4. **Crossbow**: the right arm box (vanilla 25 cm arm) sat over the view centre, the rear sight block covered the
   centre, the bolt floated 2 cm over the rail, the stirrup stood up into the sight line.

## What changed
- **Right-handed hold for all hand bows** (`FieldBowPresentation`, pure `anchor/place/draw`): bow model mirrored with
  one negative scale (meshes unchanged; third-person/display untouched), arrow on the shelf against the LEFT side of
  the riser, riser and upper limb right of the sight line, traditional cant 11 deg top-limb RIGHT, arrow ~7.5 deg under
  the eye (realistic anchor; keeps the 10-40 m aiming area clear of the bow hand). Anchor is reached exactly at full draw.
- **Field Recurve** (`FieldPrimitiveBow`): leather-padded shelf on top of the grip wrap, strike plate, string nocking
  point and brass nock set on the arrow line (`ARROW_X/ARROW_Y`); third-person arrow moved onto the shelf too.
- **Impact dot** (recurves): `BowAim` flies the arrow the server will launch (`archery/ArrowFlight`: shootFromRotation
  float/sine-table maths, spawn eye or eye-0.1, FieldArrow quadratic drag with the synced wind / HuntProjectile linear
  drag, water) through the client world (block COLLIDER clip, deer anatomy, entity boxes) from the server's eye and
  rotation; `BowSightHud` projects the impact with the camera and world FOV (`SightOptics.project`) and draws one dot +
  faint ring. Any distance, uphill/downhill, wind. Rangefinder readout = distance to the dot's animal.
- **Full draw only**: all sights/dots/pins show only after the client has held the full draw time; server
  (`FieldBow`, `ExpeditionWeapon`) treats >= 95 % as full (one tick of jitter is still a full-power, zero-spread shot).
- **Crossbow**: shouldered, eye behind a reflex sight on a bridge mount; its window is sized/placed (hand-FOV mapped)
  to frame the range dots and the centre; bolt and string on the rail, stirrup hangs low, latch travel kept on the rail;
  right hand on the pistol grip, left under the fore-end, both below the sight line. Arms on bows drawn at 0.56 girth
  (`FieldPlayerArms.reach`).
- **Arrows in the world**: `FieldArrowRenderer` orients along velocity in flight, the hit rotation when stuck.
- **Bow kill cam** (`KillCamReplay`): camera rides straight behind and 11 cm above the arrow, looking down its line, no
  drift/roll, eases to a stop ~2.8 m short and watches it go in; arrow point exactly on the path (per-head reach), per
  frame; it sinks 20 cm in at the real hit point, then the deer carries it as its impact mark (bone-anchored, so it
  rides the collapse; wildlife: fixed to the body incl. death roll); orbit glides out of the chase. Gun/rifle kill cam
  unchanged.
- **Impact marks** draw the real arrow model (head/shaft/bolt that was shot, `ArrowMarks` stamp) at the same depth.
- Settings: "Arrow-tip mark" -> **"Impact dot"**; "Gap marks" row removed (config key kept, unused).

## Files
New: `archery/ArrowFlight`, `archery/ArrowMarks`, `client/BowAim`, `tools/archery2/*` (view harness, aim harness,
arrow/kill-cam harness, FMLEnvironment stub for offline runs).
Changed (src): `client/FieldBowPresentation` (rewritten), `FieldBowHands` (rewritten), `FieldBows` (crossbow sight,
string, stirrup), `BowSight`, `BowSightHud`, `FieldBowAim` (aim curve), `KillCamReplay`, `KillCamStandIn`,
`KillCamClient` (2 helpers), `archery/SightOptics` (+project), `archery/BowConfig` (comments).
Copied from dec62g8 and changed: `client/FieldPlayerArms` (reach/girth + harness hook), `FieldPrimitiveBow`,
`FieldArrowRenderer`, `ImpactMarkRenderer`, `ExpeditionItemRenderer` (bolt height).
Shared-file hook lines (marked `// [archery2]`):
- `hunting/Whitetail.projectileHit`, after `this.recordImpact(var3, var37, var4, var26, var28, false);`:
  `this.entityData.set(IMPACTS, com.formaworks.frontierhunts.archery.ArrowMarks.stamp(this.entityData.get(IMPACTS), var2));`
- `hunting/FieldBow.releaseUsing` and `expedition/ExpeditionWeapon.releaseUsing`: `if (draw >= 0.95F) draw = 1.0F;`
- `client/FrontierSettingsScreen` INTERFACE/Archery: "Impact dot" row replaces "Arrow-tip mark" + "Gap marks".
- `client/HuntEquipmentRenderer`: field bow third-person arrow at `FieldPrimitiveBow.ARROW_X/Y`.

## Offline checks (all pass)
- `tools/archery2/aim.sh` - 624 aimed shots (both recurves, 10-40 m, up/down hill, any heading, FOV 50-110 zoomed,
  3 screens, calm/wind/rain): dot put on the bullseye -> independent server transcription -> **worst miss 0.0043 block**;
  release one tick early unchanged; projection == GameRenderer projection*view (0.0003 px).
- `MAIN=com.formaworks.frontierhunts.client.ArrowCamHarness tools/archery2/view.sh` - orientation exact (20000 dirs),
  head reach exact for all heads; bow kill cam at 60/144 fps: view <= 1.8 deg off the flight line, arrow <= 7.2 deg off
  centre, per-frame turn <= 0.4 deg, point exactly at the hit, camera stops >= 2.8 m short.
- `tools/archery2/view.sh [classes] [out] [fov]` - renders the game's own first-person code (PNG): crossbow/recurves
  centre clear. `tools/bows` BallisticHarness still PASS (needs joml on the classpath now:
  `-cp /home/claude/fh/lib/joml-1.10.5.jar`).
- compile exit=0, check_jar --base master 0 errors, server smoke: see report.

## IN-GAME TEST SCRIPT
Setup: creative superflat, day, `/gamerule doDaylightCycle false`, face north (F3 -Z), FOV 70 then 90.
1. `/give @s frontierhunts:crossbow`, `/give @s frontierhunts:field_arrow 64`. Hold use. Expect: crossbow rises to the
   shoulder, at full draw a clear view through a small round reflex-sight window, three coloured dots in it (labels
   20/30/40 beside), limbs as a bar below, rail and bolt below, hands only at the bottom edge. Nothing over the centre.
2. Target wall 30 m: `/fill ~-3 ~ ~-30 ~3 ~4 ~-30 minecraft:white_wool`, `/setblock ~ ~1 ~-30 minecraft:red_wool`.
   Crossbow: yellow (30) dot on the red block, release -> hit.
3. `/give @s frontierhunts:recurve_bow`. Draw: bow in the LEFT fist, riser and upper limb to the RIGHT, top limb tipped
   right, arrow on the left of the riser. The crosshair stays until the draw is complete, then a small warm dot with a
   faint ring appears ON the wall/ground where the arrow will land. Put the dot on the red block, release -> hits the
   red block (x5, tight). Walk to 12 m, 20 m, 40 m and repeat: the dot moves with range, every shot hits the dot.
   Aim at the floor 8 m away: the dot sits on the floor where the arrow sticks.
4. `/give @s frontierhunts:field_bow` - same as 3; zoom in (F1 off, look closely): arrow lies on a leather shelf at the
   top of the grip wrap, against the riser's left flank, nock on the string's brass nocking point.
5. Release the moment the dot appears (no waiting): still a full-speed hit. Release before the dot (partial draw): the
   arrow falls short (expected).
6. Uphill/downhill: `/tp ~ ~8 ~` and shoot the wall below; dot on block -> hit.
7. Settings > Interface > Archery: "Impact dot" off -> crosshair stays at full draw, no dot; on -> dot.
8. Arrow orientation in play: shoot field-bow arrows north, east, south, west and NE/SW: every flying and stuck arrow
   points head-first along its flight (before: east/west tail-first, diagonals sideways). Same with the recurve.
9. Bow kill cam: `/summon frontierhunts:whitetail ~ ~ ~-30 {NoAI:1b,Rotation:[90f,0f]}`, field bow, full draw, dot
   behind the shoulder, release. Expect: camera rides directly behind/above the arrow, nock toward you, head forward,
   no sideways arrow, no jitter; it slows ~3 m short and watches the arrow go in; the arrow stays in the deer exactly
   where it struck and stays in the body through the X-ray and the collapse; camera glides to the side for the X-ray.
   Repeat at 45 deg headings (NE) and with the recurve (confirmed-path start) and the crossbow (a short bolt).
10. Rifle kill cam (`/give @s frontierhunts:ridgeline_rifle`, killcam.md step 3): unchanged behaviour.
11. Wildlife: `/summon frontierhunts:grizzly ~ ~ ~-20 {NoAI:1b}`, kill with arrows: kill cam arrow sticks in the bear and
    rolls with it.
12. Third person (F5): bows held normally, field bow arrow sits on the shelf; crossbow bolt lies on the rail.
13. Multiplayer: client B sees A's arrows point the right way in flight and stuck in deer.
