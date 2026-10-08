# Workstream `rifle` - smooth hold sway, Ridgeline sight swap + bipod, prone stance

## 1. Smooth sway (`client/HoldSway.java`, `client/ScopeZoom.java`)
Why the old sway stuttered: it was evaluated from the client's *game tick counter* + partial tick (the client copy
jumps every time the server re-syncs the time), it had a sharp "pulse twitch" spike, stance changes stepped its
amplitude, and tiny per-frame yaw increments were lost to float rounding on a large accumulated yaw.

Now: `HoldSway` is one continuous curve on its own real-time clock (advanced by the frame's nanoTime delta, frozen
while paused): breathing (asymmetric, ~12.5-15.5 breaths/min, rate wanders via an integrated phase), plus a drift of
four incommensurate sines per axis (0.07-0.41 Hz). No pulse. Amplitudes follow stance targets through critically
damped springs (value and rate continuous). `ScopeZoom.settleSway` applies exactly the change since last frame to
the player's real look (xRot/xRotO, yRot/yRotO), carrying any float/clamp residue into the next frame, so the reticle
and the bullet follow the same curve at any FPS. Offline check: `tools/rifle/HoldSwayCheck.java` (30/60/144/360 fps
give the same curve to 5e-5 deg; acceleration stays bounded as fps rises = no kinks; prone 0.23 MOA, standing ~6 MOA).

Stance targets (drift / breathing, x standing): standing 1/1; crouched 0.55/0.6; crouched + bipod 0.18/0.25;
**prone 0 / 0.06 (0.04 with bipod) - no drift, only a faint breathing bob**; steady stock drift x0.8; moving x1.8.
Raise envelope and the ~1 s "just shouldered" boost as before. Reduced Motion = no sway. Applies only to our scoped
weapons (field guns + Ridgeline, magnified optics as before). `ScopePower.sway()` removed (moved to HoldSway).

## 2. Ridgeline sight swap + bipod
- Accepted parts (`rifle/RidgelineOptics.PARTS`): factory scope (new part item `frontierhunts:ridgeline_scope`,
  "Ridgeline Hunting Scope (3-9x)"), 4-12x Field Optic, Six/Eight/Twelve Power, Thermal, Reflex, Micro Red Dot,
  Holographic, 2x Prism, 5-round magazine, bipod. No optic = flip-up iron sights.
- Workflow = existing Attachment Workbench (gun + part in the fitting slots -> Install). Installing a sight on a
  Ridgeline that already has one **swaps** them: the old optic (factory scope included) goes back to the inventory.
  Click "x Ridgeline Hunting Scope" in the INSTALLED list to take the factory scope off -> iron sights. Same works
  from the Optics & attachments bench "fit to selected weapon" buttons (`AttachmentFitting.plan`).
- Item data (CUSTOM_DATA): field-optic booleans as on field guns, legacy `frontier_four_power`, and
  `ridgeline_scope_off` (factory scope removed). Old rifles keep their factory scope; an old rifle with a thermal /
  4x fitted shows that optic (the factory scope "comes back" when it is removed - no duplication).
- Rendering: the supplied rifle mesh is **not modified**. `CamoRifleModel` splits the factory scope's triangles out
  of part 0 at load (centroid classifier, verified on all three LODs) and simply skips them (+ their lens glass) when
  another sight is fitted; the Weaver bases stay. `RidgelineSights` draws the field optic meshes (same parts / glass
  as the field guns, scaled 0.78 / 0.86 to the slim rifle) with the rings clamped on the bases (red dots/prism on the
  rear base), or the iron sights (hooded-ramp front post with brass bead, U-notch rear leaf on a barrel band).
  Loose scope item = the split-off scope triangles (`CamoRifleModel.drawScope`).
- Sight picture: per-sight sight-line height and eye relief (`RidgelineSights.axisY/eyeZ`), first-person aim pose
  aligns to it. Magnified optics -> eyepiece overlay with that optic's ScopeZoom range (factory 3-9x, 4-12x, 3-9x,
  4-16x, 5-25x, thermal 2-8x e-zoom); red dots -> rifle stays up with the field guns' reticle overlay
  (ExpeditionOptics); prism -> prism view; iron sights -> the rifle itself (1.5x like field irons).
- Bipod (already supported on the Ridgeline): renders folded under the fore-end, unfolds when crouched **or prone**.

## 3. Prone (`prone/Prone.java`, `prone/ProneNetwork.java`, `client/prone/*`, `client/mixin/ProneCameraMixin`)
- Key **Z** ("Go prone / get up", Frontier Hunts category) toggles. Server-authoritative: validates (alive, on the
  ground, not riding/sleeping/gliding/in water or lava/swimming/climbing/flying, not inside a ground blind, fall
  distance; getting up needs room - else "No room to get up here"), rate-limited, then holds vanilla's crawl pose via
  NeoForge `Player#setForcedPose(SWIMMING)` (0.6 hitbox, vanilla crawl speed; we slow it to x0.8, no jump, no
  sprint) and syncs to the player + everyone in range (+ StartTracking). Clients mirror the forced pose, so others
  see the player prone. Auto-exit (checked in PlayerTickEvent.Pre, before vanilla picks the pose): mounting
  (EntityMountEvent too), sleeping, water/lava/swimming, ladders (tree stand / tower blind ladders), elytra, flying,
  falling > 2.5, ground blinds, death, respawn, dimension change, logout. Swimming/elytra keep vanilla behaviour.
- Camera: eye height follows a smootherstep (~0.45 s down / 0.55 s up) instead of vanilla's halving, plus a slight
  head nod (camera only; off with Reduced Motion). Ground step sound + leather rustle on the transition.
- Our weapons while prone: bipod deploys, zero sway, recoil x0.35 with bipod / x0.6 without (Ridgeline camera kick),
  field guns x0.45 / x0.65; spread: Ridgeline aimed 0.008 (bipod) / 0.012 (prone), field guns bipod x0.45 / prone
  x0.65. Any other item (TACZ etc.): pose only.

## Files
New: `rifle/RidgelineOptics, RidgelineScopeItem`, `prone/Prone, ProneNetwork`, `client/HoldSway, RidgelineSights`,
`client/prone/ProneClient, ProneCamera`, `client/mixin/ProneCameraMixin`, `tools/rifle/HoldSwayCheck.java`,
`patch/assets/frontierhunts/models/item/ridgeline_scope.json`, `patch/data/frontierhunts/recipe/ridgeline_scope.json`,
`patch/_merge/.../lang/en_us.json/rifle.json`, `patch/_merge/frontierhunts.client.mixins.json/rifle.json`.

Copied from dec62g8 into src (edits marked `// [rifle]`): `client/RifleClient` (decompiled @Mod constructor fixed
with typed listeners; `fov` handler unchanged, so `ScopeZoom.stripLegacyZoom` still matches), `client/RifleRenderer`,
`client/RidgelineModel`, `client/CamoRifleModel`, `client/FieldAttachmentHardware` (bipodDeploy ORs prone),
`client/FieldGunEffects` (prone recoil, 1 line), `rifle/RifleContent` (+SCOPE item), `rifle/RifleActions` (prone
spread), `rifle/RifleItem` (tooltip), `workshop/AttachmentFitting`, `workshop/EquipmentCatalog` ("ridgeline_scope"
appended LAST to PARTS - button ids are indices), `expedition/AttachmentSpec` (SIGHTS += ridgeline_scope),
`expedition/ExpeditionWeapon` (prone spread; `fit()` now checks supports() before looking up the part item - the
old order would NPE for non-expedition parts).
Shared src files touched (small): `ScopeZoom` (profile per Ridgeline sight + sway), `ScopePower` (sway fn removed),
`ExpeditionOptics.mode` (Ridgeline red dot / prism), `RidgelineDraw` (passes sight), `ExpeditionGear` (bipod tooltip).
**If another workstream also copies RifleClient / AttachmentFitting / ExpeditionWeapon / FieldGunEffects, merge by hand.**
Known: the legacy WorkbenchMenu "4x optic" quick-fit button (base jar) still adds the 4x without removing another
sight; the rifle then shows the 4x (precedence thermal > 4x > others) - no item loss.

## In-game test script
1. `/give @s frontierhunts:ridgeline_rifle`, `/give @s frontierhunts:twelve_power_scope`, `/give @s frontierhunts:holographic_sight`,
   `/give @s frontierhunts:bipod`, `/give @s frontierhunts:reserve_308 30`, `/give @s frontierhunts:attachment_workbench` (place it).
   Tooltip of the rifle: "Sight: Ridgeline Hunting Scope (3-9x)" and "Z: go prone ...".
2. Workbench: rifle + Twelve Power Scope -> Install. The factory scope item appears in the inventory; the rifle now
   carries the 12x on its bases (check first person, F5, inventory icon, dropped item). Aim: 5-25x readout, wheel zooms.
3. Rifle + Holographic -> Install: the 12x comes back to the inventory; aim: rifle stays up, holo ring centred in the
   window. Click "x Watchpost Holographic Sight" -> iron sights: aim shows brass bead in the rear notch, centred.
4. Rifle + Ridgeline Hunting Scope -> Install: back to the original look/zoom (3-9x). Fit the bipod: folded under the fore-end.
5. Sway: stand + aim at 9x on a far block: slow breathing + wander, completely smooth at 144+ fps (no stepping, no
   twitch); crouch -> smaller, crouch+bipod -> nearly still; press Z (prone) -> no drift, only a faint bob. Reduced Motion -> none.
6. Prone: Z -> camera eases down, player lies flat, crawl (slow), Space/Ctrl do nothing; under a 1-block gap, Z
   again -> "No room to get up here". With the bipod fitted the legs unfold; fire: much smaller kick.
7. Auto-exit: walk prone into water / onto a ladder / mount a horse or ATV / sleep in a bed / step into a deployed
   ground blind -> stance drops, vanilla takes over. Prone with a TACZ gun or any item: pose works.
8. Dedicated server + 2 clients: A goes prone -> B sees A crawling (also after B walks away and back into range,
   and after relog); A gets up -> B sees standing. No errors in server log.
