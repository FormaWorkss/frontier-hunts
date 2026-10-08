# Workstream `sticks` — shooting sticks that actually work

Austin: "The shooting sticks need to actually work: placeable on the ground and then you can attach a gun to it and it
removes the shakiness, realistic movements as well."

Before: `shooting_sticks` was an outfitter-counter item whose only effect was "offhand: -40 % sway standing still".
Now it is a real tripod you set up, rest a gun in, swivel on, and fold away.

## What the player does
1. **Make** them at the Frontier Workbench, *Hunting gear · Shooting rests* (` L ` / `NIN` / `ISI`: leather yoke pad,
   2 iron nuggets for the leg locks, 3 iron ingots for the legs, string for the strap). They left the outfitter's
   counter (it only sells what can't be made).
2. **Set up**: use the item on the top of a block. The tripod stands where you clicked, one leg out front (the way you
   faced). Sneak while placing = kneeling height. Sound: legs swing out, tubes slide, three lever locks snap, feet tap.
   Creative keeps the item. Refused (message) on a wall face, without room, or right on top of another set.
3. **Height**: use the set with an empty hand (any non-gun item): standing → kneeling → sitting → standing
   (legs slide over ~0.4 s, a lock clicks, action-bar line names the height). Needs head room.
4. **Rest a gun**: walk up holding a rifle, shotgun or handgun (every field firearm except flare gun / bait launcher,
   and the Ridgeline) and use the sticks. The forend goes into the padded V; you settle in behind it over 8 ticks
   (body moves into place, the view eases into the limits). Hint line: "On the sticks · swivel to follow · Shift to
   stand up · Space or a step to leave". Others see you standing / kneeling / sitting at the sticks with the gun in
   the yoke.
5. **While rested**
   - Scope hold sway: drift ×0.05, breathing ×0.2 of standing (prone is 0 / 0.06, so sticks sit between crouched-bipod
     and prone, as in real life) plus a faint **pulse** (lub-dub, ~70 bpm, 0.003°) that only shows at high power. Offline (`tools/sticks/SticksCheck.java`, 30 s at 60 fps): standing 10.8 MOA pitch p-p, crouched 6.3, **sticks 1.6**, prone 0.4; frame-to-frame acceleration on the sticks below standing (no twitch).
     Shivering/exhaustion shake ×0.35. **Wind no longer pushes the muzzle.**
   - Recoil: camera kick ×0.55, the permanent muzzle climb ×0.3 (the gun settles back into the yoke), viewmodel
     kick ×0.6. Spread: field guns ×0.5 (bipod 0.45), movement term ignored (swivelling isn't walking);
     Ridgeline aimed 0.012 / unaimed 0.42 (standing 0.035 / 0.65).
   - **Swivel**: the gun pivots about the yoke; your body walks round the pivot (stays 0.95 m behind the yoke along
     your line of fire). Arc ±40° from the direction you came in, narrowed by anything your body would stand in
     (sampled every 5° at rest time). Tilt: standing 20° up / 24° down, kneeling 24/20, sitting 28/16. The last 9° of
     every limit stiffen (each mouse movement outward gets through less and less) and stop; turning back is free.
   - First person: not aimed, the gun lies lower-right in the yoke with a hair of cant, head off the stock; aimed, the
     sight line is exactly the normal one. The yoke and legs are drawn in the hand pass under the forend (they glide in
     from exactly where the world shows them, so nothing jumps), so they stay under the gun with any FOV / zoom.
     Behind a magnified scope the gun (and so the yoke) is hidden, as before.
6. **Leave**: sneak (vanilla dismount), jump, hold any movement key (2 ticks), put the gun away / switch item, die,
   change dimension, log out. Kneeling / sitting: you stand up onto the ground line, the camera rises smoothly.
   The yoke stays pointed where you left it.
7. **Fold**: sneak + use, or punch. Gives back exactly the item it was made from (renamed pairs stay renamed); full
   inventory → drops at the sticks; creative: no second copy. Nobody can fold sticks someone else is resting on.
   No ground under them → they topple and drop as the item. Explosions do the same. Projectiles pass through.

## Design
- `sticks/ShootingSticksEntity`: an `Entity` (not a block: free yaw, three heights, no grid). The shooter **rides** it
  (vanilla passenger sync to every client, movement locked, server-authoritative); `positionRider` keeps the body
  behind the yoke along the rider's yaw (`riderSpot`), lowered for kneel (0.42) / sit (0.62); `shouldRiderSit` only
  for sitting. Synced: height, arc centre / left / right, yoke yaw, deploy time. Saved: height, yoke yaw, the item.
  `clampRider`: soft limits for the local rider (client), a hard guard 2° wider on the server. Local rider position
  is corrected per frame (`clientFrame`, also from `onPassengerTurned`) so the swivel is frame-smooth. Client-side
  `removePassenger` puts the local player at the same dismount spot the server uses (the server never sends it).
- `sticks/ShootingSticks`: rules + numbers (heights, arc, hold factors, `supports`, `soft`, item placement).
- `sticks/SticksNetwork`: C2S `Use(entity, fold)` (a gun's use key aims, so with a gun in hand the client asks the
  server itself; validated: entity, `canInteractWithEntity`, gun, rider, 4-tick rate limit) and `Release` (jump).
  Logout listener releases before vanilla saves/removes the "vehicle".
- Client (`sticks/client`): `ShootingSticksRenderer` + `SticksMesh` (mesh `models/entity/shooting_sticks.fhsk`,
  3 legs × 3 telescoping sections + head + strap + swivelling yoke; legs slide between heights, swing out when set up;
  no per-frame allocation), `SticksView` (first-person rest pose API for the guns + hand-pass drawing + world-copy
  hiding), `SticksClient` (use/jump/move input, hint, per-frame clamp, camera ease when standing up), `SticksPose`
  (third-person arms laid along the gun to the yoke, kneel/sit legs).
- Art: `tools/sticks/sticks_art.py` (texture, mesh, folded item model, painted icon, previews), `tools/sticks/sounds.py`
  (5 events, 9 oggs), `tools/sticks/mock_views.py` (third/first-person mocks with the real lever-rifle mesh).

## Files
New: `src/.../sticks/{ShootingSticks,ShootingSticksEntity,SticksContent,SticksNetwork}.java`,
`src/.../sticks/client/{ShootingSticksRenderer,SticksMesh,SticksView,SticksClient,SticksPose}.java`,
`src/.../client/mixin/{SticksCameraMixin,SticksPlayerModelMixin}.java`,
`patch/assets/frontierhunts/{textures/entity/shooting_sticks.png, textures/item/sticks/*.png, models/entity/shooting_sticks.fhsk, sounds/sticks/*.ogg}`,
`patch/data/frontierhunts/recipe/shooting_sticks.json`, `patch/data/frontierhunts/advancement/recipes/unlock/shooting_sticks.json`,
merge fragments `patch/_merge/.../lang/en_us.json/zzzzzzzz_sticks.json`, `.../sounds.json/zzzzzzzz_sticks.json`,
`patch/_merge/frontierhunts.client.mixins.json/zzzzzzzz_sticks.json` (`SticksCameraMixin`, `SticksPlayerModelMixin`),
`tools/sticks/*`, `tools/qa/sticks*.commands`, `tools/qa/harness/.../SticksTest.java`.
Replaced: `patch/.../models/item/shooting_sticks_3d.json` (folded bundle), `patch/.../textures/item/shooting_sticks.png` (icon).

Shared files (hook lines marked `// [sticks]`):
- `outfitter/OutfitterItem`: `useOn` → `ShootingSticks.place(ctx)`; tooltip from lang (`desc1..4`, no "counter only");
  `sticks(Player)` now returns 1 (the old offhand bonus is gone; ScopeZoom reads the sticks itself).
- `economy/ShopPrices`: `shooting_sticks` removed from `ADDED` (counter sells only what can't be made).
- `benches/BenchCatalog`: `put(BenchTab.HUNTING, "rests", "shooting_sticks")`.
- `expedition/ExpeditionWeapon` (fire): `boolean sticks = ...; var16 *= SPREAD`; movement term 0 when rested.
- `rifle/RifleActions`: `stableMount` accepts the sticks; aimed / unaimed cone on the sticks.
- `client/ScopeZoom`: `stance()` rested drift/breath, no move penalty; wind exposure 0; shake damped; `SWAY.pulse(...)`.
- `client/HoldSway`: optional heartbeat (`pulse(target)`, `heartbeat()`, `PULSE_DEGREES`); 0 unless rested.
- `client/FieldGunEffects.viewRecoil`: recoil ×`RECOIL`, climb ×`CLIMB` when rested.
- `client/FieldWeaponFirstPerson`: `impulse` ×0.6 rested; in `hand` the rested hold block (`SticksView.pose/anchor`),
  idle bob and mouse-lag sway removed while rested, `+ sticksTilt / sticksYaw / sticksRoll` in the three rotations.
- `client/RifleClient`: camera kick ×`RECOIL` rested; in `hand` the same rested-hold block + one guarded YP rotation.
- `tools/qa/harness/.../FrontierQa.java`: `fhqa sticks <case>`; `tools/recipe_audit.py`: sticks off the shop list.
**If another branch also edits FieldWeaponFirstPerson.hand / RifleClient.hand / ScopeZoom.stance, merge by hand.**

## QA (dedicated server, coordinator)
- `tools/qa/sticks.commands` → `fhqa sticks all`: place (survival uses item, one per spot, sneak = kneeling,
  empty-hand height cycle, creative keeps item), rest (rides, settles at the spot, hold factors at target, Ridgeline
  stableMount, second hunter refused, no fold while in use, flare gun refused, Ridgeline accepted), arc (yaw/pitch
  clamped, body stays at reach after a swivel, soft stop maths, a wall narrows the arc), release (movement key after
  2 ticks, back on the ground, kneeling stands up when the gun is put away, jump request, logout leaves the sticks
  standing), fold (exactly one item back, no double fold, punch, renamed pair, full inventory drops one, creative no
  dup, topple drops one), save (NBT round trip identical, rider never saved as passenger). Expect only `sticks PASS`.
- Across a real restart: `server_smoke.sh <jar> tools/qa/sticks_persist1.commands /tmp/st1`, then
  `QA_WORLD=/tmp/st1/world server_smoke.sh <jar> tools/qa/sticks_persist2.commands /tmp/st2` → `persist_check PASS`.
- Harness compiles (`javac` with cp62 + this branch's classes). Note: `tools/qa/harness/build.sh` looks for
  `.infra/cp62.txt` next to the repo, so run it from `/home/claude/fh2` after the merge.

## In-game test script
1. `/give @s frontierhunts:shooting_sticks`, `/give @s frontierhunts:lever_rifle`, `/give @s frontierhunts:rifle_round 20`,
   `/give @s frontierhunts:ridgeline_rifle`, a 12× scope on the lever rifle (Gunsmith's Bench Fit tab).
   Tooltip: four lines, no "Outfitter's counter only". Inventory icon: tripod with V yoke. Held: folded bundle.
2. Right-click grass: tripod unfolds (legs swing out ~0.5 s), deploy sound. Right-click a wall face: "Set the sticks up
   on the ground". Sneak-place a second set: kneeling height.
3. Empty hand, right-click the set three times: kneeling → sitting → standing, legs slide, click sound, action bar.
4. Hold the lever rifle, walk up from any side, right-click the sticks: you step in behind them (no snap), the gun
   comes down into the yoke (first person: forend in the V, legs below, strap). Hint line shows.
5. Aim (12×): reticle almost still — slow breathing rise/fall, a faint pulse; compare standing (big sway) and prone.
   `/weather thunder` + windy day: no wind lean on the sticks. Fire: smaller kick, the view settles back.
6. Swivel with the mouse: body walks round the yoke; near ±40° the turn stiffens and stops; up/down limited the same
   way. Place a block beside you and rest again: the arc stops short of it.
7. F5: you stand at the sticks, rifle along your arms lying in the V; at kneeling height one knee down, sitting legs out.
   A second player sees the same and the yoke turning with your aim. They can't use or fold your sticks.
8. Leave by: Shift; Space; W/A/S/D; scroll to another hotbar slot. Kneeling/sitting: camera rises smoothly, feet on the
   ground. Yoke stays pointing where you left it.
9. Sneak + right-click (or punch): folds, one item back. Creative: no extra copy. Break the block under a set: it
   drops as the item. TNT next to it: drops as the item.
10. Rest on sticks, `/stop` (or Save & Quit) and reload: the sticks are where they were, you are standing beside them.
11. Ridgeline, shotgun, revolver: all rest; flare gun / bows: right-click the sticks does nothing special.
12. Frontier Workbench → Hunting gear → Shooting rests: recipe shown and craftable. Handbook → Field tips → "Get low,
    get steady" and lesson "Place the shot" mention the sticks.

## Previews (tools/sticks/previews)
`placed_heights.png` (standing / kneeling / sitting), `head_closeup.png`, `locks_closeup.png`, `feet_closeup.png`,
`item_folded.png`, `icon.png`, `texture.png`, `thirdperson_heights.png` (pose maths with the real rifle mesh),
`firstperson_rested.png` (ready / iron sights, real rifle mesh; arms not drawn in the mock).

## Known / risks
- No "hold breath" mechanic exists in the mod, so none was added.
- Bows and crossbows don't rest (optional in the brief; their draw/hold system is separate).
- The deer's movement detection treats a swivelling (orbiting) shooter like any moving player.
- Third-person gun angle comes from the arm pointing at the yoke, not from the head pitch (keeps the gun in the V).
- Sitting feet may dip ~3 cm into the ground (single-bone Minecraft legs).
