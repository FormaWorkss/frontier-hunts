# Workstream `bows` - precise bow sights, arrow tips at the bow workbench, fishing line side

Branch `bows` (from master 0bf1526). Designed for the Vanilla and Ultra presets alike (nothing here is
preset-dependent; it is aiming, HUD and workbench UI).

## 1. Bow aiming

**Sight picture (first person, `client/FieldBowPresentation`, `client/FieldBows`, `client/BowSight`, `client/BowSightHud`)**
- Traditional bows (Field Recurve Bow `field_bow`, Recurve Bow `recurve_bow`): at anchor the bow is canted 11 deg
  (top limb tipped LEFT, out of the sight line), the bow fist sits ~10-15 deg below centre, riser and limbs stay
  >= ~1.5-5 deg left of centre. The arrow runs straight out under the eye; its tip sits exactly on the point-on
  mark. The old setup put the grip and fist dead centre (recurve "can't see anything").
- Compound (`compound_bow`): held level, riser left, a calibrated multi-pin sight: the housing and each glowing
  fibre pin are placed (in the hand pass, corrected for the hand-vs-world FOV) at the exact angle where the arrow
  lands at that distance; HUD adds the peep ring around the housing and each pin's distance label.
- Crossbow: keeps its pose; HUD shows one coloured range dot per distance.
- Crosshair: hidden at full draw while an aid is on (it marks the launch line, not the impact).
- Pin maths: `archery/BowBallistics` mirrors the server integrators step for step (FieldArrow: spawn 0.1 below eye,
  quadratic drag, g 0.024525; HuntProjectile: eye, x0.993, g 0.035) and solves each pin as a fixed point at the
  bow's current angle (shots up/down hill hit too). Client reads it through an interpolated 0.5-deg table.
- The bow is locked to the view at anchor (vanilla hand-lag rotation cancelled), the old model-only sway and
  "creep" are gone.

**Precision (server)** `archery/BowBallistics.spread` replaces the bow inaccuracy in `hunting/FieldBow` and
`expedition/ExpeditionWeapon.shoot`: full draw + standing still on the ground = **0 random spread**. Short draw
(0.6 x (1-draw)), fatigue tremor (0.12 x strain), moving and airborne add spread. Before release
`client/BowSway` sends the exact current rotation (only when standing still) so the server launches along the
view the player saw.

**Hold sway** `client/BowSway`: the rifles' `HoldSway` curve applied to the real view at bow amplitudes (standing
~0.12 deg drift / 0.15 deg breath; sneaking x0.55; prone x0.3; moving x1.8; fresh-anchor settle over ~1 s; fatigue
after the 3 s steady window grows drift up to x3.4, capped). Smooth, frame-rate independent; Reduced Motion = none.
`expedition/BowHold.strain`: prone now x0.35 (sneak stays x0.45).

**Rangefinder readout**: with a `frontierhunts:rangefinder` anywhere in the inventory, at full draw the distance
(m) to the living thing on the sight line (block-occluded, 150 m) shows beside the sight.

**Offline harness** `tools/bows/BallisticHarness.java` - every bow profile x every arrow head x every pin set x bow
angles -40..+40 deg x 5 headings, flown with an independent transcription of the server code (Mth sine table,
float rotations): **27,265 shots, worst miss 0.0045 block (limit 0.1)**, plus the interpolated table (2,680 random
shots, worst 0.0056), the FOV mapping (exact) and zero spread at a steady full draw.
```
cd <repo> && javac -d /tmp/bh src/com/formaworks/frontierhunts/archery/BowBallistics.java \
  src/com/formaworks/frontierhunts/archery/SightOptics.java tools/bows/BallisticHarness.java && java -cp /tmp/bh BallisticHarness
```
Arrow heads change damage/bleeding/recovery only, never the flight, so one profile per bow covers every head.
Pin angles at level (deg), 20/30/40 m: field recurve 1.95/2.84/3.81, recurve 2.29/3.66/5.07, compound
1.32/2.13/2.97, crossbow 0.93/1.53/2.13.

**Settings** (Interface page, new "Archery" section; client config `[archery]` in frontierhunts-client.toml):
Compound pin sight (also crossbow dots), Pin distances (10/20/30, 20/30/40 default, 20/30/40/50, 30/40/50/60 m),
Peep sight, Arrow-tip mark, Gap marks, Bow range readout. All default on.

## 2. Arrow tips at the Bow Tuning Rack (Arrow tips tab, `client/WorkbenchScreen`, `workshop/WorkbenchMenu`)
Rebuilt as three clicks: **(1)** pick a stack of your arrows (left list: shaft, current head, count),
**(2)** click the head (grid of every head with the number you own, tracer stripe, green corner = head already on
these arrows; hover = full tooltip), **(3)** "Fit all (N)" / "Fit to 1". The panel shows current -> new head with
Penetration / Blood trail / Damage / Recovery bars (dark tick = current, % change) and a status line (heads owned,
how many will be made from materials). Missing heads are made automatically from materials (whole recipe batches,
up to 16); "Make N heads" crafts one batch on demand. Reverse: the "stock head" cell (or "Take heads off (N)")
returns the fitted heads to the inventory and gives the shafts their stock head (fixed broadhead / flint point) -
the stock head is never handed out as an item, so no duplication. Old heads always come back on a refit.
Server: new button range `40000 + (slot*16 + code)*2 + all` -> `WorkbenchMenu.refitPlan` recomputed from the server
inventory (slot 0-35 must hold arrows, menu open, in reach, not spectator, 4-tick rate limit). The crafting-grid
refit recipe still works; the old auto-pick-largest-stack UI (30000 range, still accepted) is gone from the screen.
Tooltips: arrows lead with "Head: <name> (stock)" + penetration/bleeding/recovery; heads point to the tab.

## 3. Fishing line side (`client/HandSpace`, `client/FishingClient`, `client/BowfishingClient`)
The field rod / bowfishing line endpoint was taken from the first-person hand pose assuming vanilla's
world-aligned hand pose stack. Shader pipelines (Iris) render the hands with a plain camera-space stack, so the
point got rotated by the player's heading (mirrored to the far side facing south). `HandSpace` now reads which space
the frame's hand pass uses from its base pose (RenderHandEvent) and rescales the point from the hand FOV to the
world FOV so the line meets the rod tip where it is drawn. Captures are skipped in the Iris shadow pass (it used to
overwrite third-person tips with the sun's view), third-person tips expire after 2 ticks, and fallbacks sit on the
rod hand's side (main arm / left-handed / off hand). Vanilla rods: vanilla's own renderer already picks the
main-arm / off-hand side correctly; unchanged.

## Files
New: `archery/BowBallistics`, `archery/SightOptics`, `archery/BowConfig`, `client/BowSight`, `client/BowSightHud`,
`client/BowSway`, `client/HandSpace`, `patch/assets/frontierhunts/textures/gui/bow_peep.png` (`tools/bows/peep_art.py`),
`tools/bows/BallisticHarness.java`.
Copied from dec62g8 and changed: `client/FieldBowPresentation` (rewritten), `client/FieldBows` (dynamic sight),
`client/ArcheryHud` (steadiness bar below the sight), `client/WorkbenchScreen` (tips tab), `client/FishingClient`,
`client/BowfishingClient`, `hunting/FieldBow` (spread), `hunting/HuntArrowItem`, `hunting/ArrowTipItem` (tooltips),
`client/FieldBowAim` (unchanged copy).
Shared-file hook lines (all marked `// [bows]`):
- `HuntConfig` client builder: `var2.push("archery"); ...archery.BowConfig.client(var2); var2.pop();`
- `client/FrontierSettingsScreen` INTERFACE page: "Archery" section, 6 rows, before the Field School section.
- `expedition/ExpeditionWeapon.shoot`: bow inaccuracy = `BowBallistics.spread(...) * skill factor`.
- `expedition/BowHold.strain`: prone x0.35.
- `workshop/WorkbenchMenu`: REFIT constants, `refitAction/canRefit/refitCount/headsToMake/headRecipe/refitPlan`,
  click branch for the 40000 range.

## IN-GAME TEST SCRIPT
Setup: flat creative superflat or field, daytime, survival for steps 9-12. `/gamemode creative`.
1. `/give @s frontierhunts:compound_bow` `/give @s frontierhunts:field_arrow 64` `/give @s frontierhunts:rangefinder`.
   Face north on flat ground. Build a target wall exactly 30 blocks away:
   `/fill ~-3 ~ ~-30 ~3 ~4 ~-30 minecraft:white_wool` then `/setblock ~ ~1 ~-30 minecraft:red_wool` (red block
   centre is ~1.5 above your feet; your eye is at 1.62).
2. Hold use. Expect: as the draw completes the crosshair disappears; a level compound with the riser to the LEFT,
   a round housing with three glowing pins (green 20 / yellow 30 / red 40, labels to their right) inside a dark
   blurry peep ring; stabiliser at the bottom; nothing covers the target. Turning quickly: the sight stays locked.
3. Put the YELLOW (30) pin on the red block centre, wait for the float to settle (~1 s), release. Expect the arrow
   to hit the red block (within ~0.1 block of where the pin was). Repeat x5 - groups tight (no random spread).
   Move to 20 m and 40 m (`/tp ~ ~ ~10` etc.) and use the green / red pins.
4. Hold the draw > 3 s: the sight starts to wander slowly (smooth, no jitter), the steadiness bar under the sight
   drains. Sneak: wander roughly halves; prone key: smallest.
5. Rangefinder: summon `/summon frontierhunts:whitetail ~ ~ ~-30 {NoAI:1b}` and aim at it at full draw:
   "30 m" (±1) beside the sight. Remove the rangefinder from the inventory: readout disappears.
6. `/give @s frontierhunts:recurve_bow` and `frontierhunts:field_bow`. Draw each: bow canted top-left, fist low,
   arrow straight under the view; a faint white "wing - dot - wing" mark on the arrow tip with "30" beside it, faint
   "20" tick above and "40" below. Hold the dot on the red block at 30 m and release: hit. Gap marks at 20/40 m.
7. `/give @s frontierhunts:crossbow`: draw -> three coloured dots with 20/30/40 labels; the 30 dot hits at 30 m.
8. Settings > Interface > Archery: toggle each option (pins off -> crosshair returns on compound; gap marks off;
   tip mark off; pin set 30/40/50/60 -> pins/marks move; peep off; range readout off). All live, no restart.
9. Arrow tips (survival): `/gamemode survival`, `/give @s frontierhunts:bow_tuning_rack`, place, give
   `frontierhunts:field_arrow 32`, `frontierhunts:primitive_arrow 12`, `frontierhunts:mechanical_broadhead 10`,
   `minecraft:iron_ingot 3`, `minecraft:iron_nugget 4`, `minecraft:string 2`. Open the rack -> "Arrow tips" tab.
10. Left list shows "Hunting arrow / Fixed-Blade Broadhead ×32" and "Primitive arrow / Knapped Flint Point ×12".
    Select the hunting stack, click the Mechanical cell (shows 10). Bars compare fixed vs mechanical (+35% blood
    trail etc.). Button reads "Fit all (18)": 10 owned + 2 batches of 4 made from materials (string runs out); the
    status line says "You have 10 · 8 more made from your materials". Click it: 18 arrows convert (14 stay fixed),
    heads and materials consumed, no fixed broadheads returned (stock head). Hover the arrows: "Head: Mechanical Broadhead".
11. With mechanical arrows selected click the "stock" cell (↺) -> "Take heads off (N)": arrows become stock fixed
    broadheads, N mechanical heads return to the inventory. `/give @s minecraft:string` + `minecraft:stick`, click
    the Judo cell -> "Make 4 heads" crafts 4 judo points (nugget + string + stick).
12. Old method still works: crafting grid arrow + head = 1 refitted arrow (old head back).
13. Fishing line: `/give @s frontierhunts:field_fishing_rod`, cast into water (hold use, release). First person:
    the line must leave from the rod tip on the RIGHT side of the screen, facing north, south, east and west.
    Repeat with a shader pack on (Iris + Photon/Complementary): same. F5: line from the rod tip in the right hand.
    Options > Skin Customization > Main Hand: Left -> rod and line on the LEFT. Change FOV 70 -> 100: the line still
    meets the drawn rod. Bowfishing (`frontierhunts:bowfishing_bow` + `bowfishing_arrow`, shoot a fish): line from
    the reel, same checks.
14. Multiplayer (dedicated server, 2 clients): client A shoots the compound at the 30 m wall; client B sees the
    arrow fly and stick at the same spot. Arrow tip refits on A update A's inventory only; a hacked button id from
    a client with no rack open does nothing.

Not verified in game (no client here): visuals/FOV mapping with shaders on, AMD/Nvidia, performance (expected
negligible: a cached pin table, a few HUD quads, one ray pick every 2 ticks while drawn and a rangefinder carried).
