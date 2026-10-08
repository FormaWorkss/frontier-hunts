# Workstream `atvfuel` — ATV gasoline, lava fuel, rear-rack cargo + jerry cans

Branch `atvfuel` (clone `/home/claude/work/atvfuel`). Compiles clean with `tools/compile.sh`. Full jar builds with `tools/build.py`,
and `tools/check_jar.py --base master` reports 0 errors and 0 warnings. **Nobody has run this in game yet** (there is no client here), so every
behaviour below still needs in-game checking with the test script at the end.
Previews (software-rasterised from the exported mesh and textures, with back-face culling enabled to check winding):
`docs/ws/atvfuel/prev_all.png`, `prev_rear.png`, `icons.png`.

## What was built
1. **Fuel tank (15 L)**: the server owns the tank. It burns fuel from what it can observe of the ride: an idle term, a speed term
   (`^1.25`), an acceleration (load) term and a climbing term. At the default rate a tank lasts about 4 h idling, 72 min at
   32 km/h, 37 min at 65 km/h and 26 min flat-out. The exact level is saved in NBT as `FhFuel`. A copy rounded to 0.01 L syncs
   through `Atv.DATA_FUEL`. Every side derives "engine running" the same way:
   seated driver + not flooded + (fuel > 0 or creative/spectator driver or `requireFuel=false`).
   - Below 0.35 L the engine **misfires**. The driver's throttle cuts for 3-10 ticks at a time, each client hears pops and the
     engine loop dips, and smoke puffs from the exhaust. **Dry**: you hear the stall sound, the engine loop, exhaust smoke and
     headlights stop, the ATV coasts, the brakes still work and reverse does not. A "Out of gas" message shows.
   - Climbing on with an empty tank plays a starter crank that doesn't catch (instead of the start sound). Refuelling while
     someone is seated restarts the engine.
   - **Creative drivers** never burn fuel and always run, even on an empty tank.
   - A new ATV placed from a crafted item starts at 25 % (configurable). Breaking or picking up an ATV stamps the tank level
     on the item (`frontierhunts:fuel_liters`, shown in the tooltip), and placing it again restores that level.
     ATVs saved before this change load with 25 %.
   - **HUD** (own GUI layer, one line above the existing km/h read-out): a pump glyph and an E–F bar with a needle and
     quarter ticks, the litres, and the reserve (`+20`). The gauge is amber below 15 % (with one soft chime), flashes LOW FUEL
     below 7.5 % and shows OUT OF GAS when dry. Creative drivers see a grey gauge with no warnings.
2. **Gas from lava**: the **Jerry Can** is a 10 L red steel can with a 3D item model (X-pressed sides, triple handle, spout cap,
   hazard label), and its fuel bar uses the item's durability bar. There are two ways to fill it:
   - right-click a **lava source** with the can. This uses up the source the way a bucket does, with sizzle, smoke and a
     fill sound.
   - craft **can + lava bucket**. The empty bucket comes back.
   To fuel the ATV, **use the can on the ATV**: each use pours 0.5 L, and holding the use key keeps pouring at about
   2.5 L/s, with a glug per pour, splash particles and a running "Tank x/15 L" read-out. Partial pours work. In creative the
   can is not drained.
3. **Rear-rack rig** (fits both looks: a tan lid on the realistic mesh, a woodland-camo lid on the classic cube ATV, each
   anchored to that model's actual rack):
   - **ATV Cargo Box**: a 27-slot lockable-look dry box. It has a charcoal body with moulded ribs, a ribbed lid with hinges,
     zinc latches, a hasp, side handles, reflectors and rack clamp feet. Use the item on the ATV to fit it. The lid swings
     open for everyone while anyone has the box open.
   - **ATV Can Carrier**: two steel cradles on the outer rails. Each cradle holds one jerry can, shown strapped in with
     ratchet straps.
   - **Reserve fuel**: cans in the cradles, and jerry cans stored in the box, refill the tank automatically when it runs
     dry (and when you mount an empty ATV). The **Pour cans** button tops the tank up by hand.
   - **Menu**: sneak-use the ATV, or press the inventory key while seated. It has the box slots and a rack panel with a
     tank gauge, two can slots, **Pour cans**, **Unstrap box** (the box comes off with everything still inside, stored in
     the vanilla `container` component; shulker boxes and other container items pop out loose instead of nesting) and
     **Remove cans** (the carrier comes off and the cans go with it).
   - Server-authoritative: storage is the ATV's existing container (slots 0-26 box, 27-28 cradles). Every slot checks what
     is fitted right now. Unstrapping closes every viewer's menu before items move. Hoppers obey the same rules
     (`canPlaceItem`). When the ATV is destroyed or discarded, the box drops packed with its contents and the cans and
     carrier drop loose. NBT key: `FhRig`. **Migration**: an old ATV with items in its built-in storage loads with a cargo box
     already fitted holding those items, so nothing is lost.

## Controls
- Use the ATV holding a **jerry can**: pour (hold the key to keep pouring). Use it holding a **Cargo Box** or **Can Carrier**:
  fit it.
- **Sneak-use** the ATV, or press the **inventory key while riding**: open the rig menu. With nothing fitted, a hint shows instead.
- Use a jerry can on a **lava source**: fill it.

## Recipes (`patch/data/frontierhunts/recipe/`)
- `jerry_can`: ` NI / IRI / III` (N iron nugget, I iron ingot, R red dye) gives an empty can.
- `jerry_can_from_lava`: shapeless, jerry can + lava bucket gives a full 10 L can, and the bucket is returned.
- `atv_cargo_box`: `LDL / ICI / LIL` (leather, black dye, iron, chest).
- `atv_can_carrier`: `I I / ILI / I I` (iron, leather).
All four items are in the **Field Equipment** creative tab, including a pre-filled can.

## Config (server, `[atvFuel]` in the HuntConfig SERVER spec)
`requireFuel` (true), `fuelUseMultiplier` (1.0, range 0-10), `newAtvFuelPercent` (25).

## Files
New (owned): `src/.../landscape/ride/rig/{AtvFuel,AtvFuelConfig,AtvFuelTooltip,AtvRig,RigMenu,RigContent,JerryCanItem,CargoBoxItem,CanCarrierItem}.java`,
`rig/client/{AtvRigRender,RigScreen,AtvFuelHud,RigClient}.java`, `tools/atvfuel_art.py` (meshes, textures, item models),
`tools/atvfuel_audio.py` (sounds), `patch/assets/frontierhunts/models/entity/atv_rig.fhrg`,
`textures/entity/atv_rig{,_classic}.png`, `textures/item/atv_rig_3d.png`, `models/item/{jerry_can,atv_cargo_box,atv_can_carrier}.json`,
`sounds/ride/{atv_stall,atv_sputter_1/2,atv_crank,fuel_pour_1-3,rig_attach,jerry_can_fill}.ogg`, 4 recipes.
Merge fragments: `patch/_merge/assets/frontierhunts/lang/en_us.json/atvfuel.json` (it also overrides `item.frontierhunts.atv.tip2`
so the tip mentions fuel), `patch/_merge/assets/frontierhunts/sounds.json/atvfuel.json`.

**Shared-file edits (all marked `// [atvfuel]`)**
- `HuntConfig.java`: 1 line, `var1.push("atvFuel"); AtvFuelConfig.server(var1); var1.pop();` (after the weather line).
- `landscape/ride/Atv.java` (the file `atvgrime` also edits):
  - the cargo list is sized `AtvRig.SLOTS` (29) in 2 places;
  - `DATA_FUEL` / `DATA_RIG` synched accessors and a `tank` field;
  - a `defineSynchedData` override;
  - `destroy()` now calls `AtvRig.destroyed`;
  - `remove()` calls `AtvRig.dropAll` (it used to call `Containers.dropContents`);
  - `interact()` hooks `AtvRig.interact` at the top, and `AtvFuel.onMount` replaces the start-sound line;
  - `tick()` calls `AtvFuel.tick(this)` after the `shownSpeed` update;
  - `drive()` gets 2 lines (`throttle`/`brake` filters) and `!AtvFuel.running(this)` in the rpm target;
  - `present()`: the remote rpm and exhaust smoke use `AtvFuel.running`;
  - save/load call `AtvFuel.save/load` and `AtvRig.save/load`;
  - `openCustomInventoryScreen` calls `AtvRig.open`, and `createMenu` returns a `RigMenu`;
  - `getContainerSize` is now `AtvRig.SLOTS`, a new `canPlaceItem` override was added, and `stillValid` requires something
    fitted.
  - If `atvgrime` also adds synched data, keep all `defineId` lines inside `Atv` (textual order = id order on both sides).
- `landscape/ride/AtvItem.java` (copied from dec62g8): `AtvFuel.fromItem` before spawning, plus a fuel tooltip line.
- `landscape/ride/client/AtvRenderer.java` (copied from dec62g8): `AtvRigRender.render(...)` after both the realistic and
  the classic draw; the classic headlights now use `AtvFuel.running` instead of `isVehicle && !flooded`.
- `landscape/ride/client/AtvSound.java` (copied from dec62g8): the engine-on test uses `AtvFuel.running`, and the gain is
  multiplied by `AtvFuel.soundGain` (dips on misfires).

## Known limits
- As with vanilla boats, the driver's client is authoritative for movement. Fuel use, refills and inventories are all
  server-side, but a modified client could still drive with a dry tank.
- Misfire timing is randomised on each client, so the driver and observers hear different pops. It is purely cosmetic.

## In-game test script
1. `/give @s frontierhunts:atv`, then place it. Mount it: you hear the start sound, and the HUD gauge sits at about 3.8 L, a
   quarter of the bar. Break the ATV in survival and check the item tooltip reads "Fuel: x / 15 L". Place it again: the
   level is unchanged.
2. Switch to survival. Run the tank down quickly with `/summon frontierhunts:atv ~ ~ ~ {FhFuel:0.5f}` and mount it, or keep
   driving. Expect the following:
   - Below 0.35 L, check for misfire pops, throttle stumbles and exhaust puffs.
   - At 0 L, check for the stall sound and the "out of gas" message. The engine loop, smoke and (classic) headlights stop,
     the ATV coasts, S still brakes, and reverse does nothing.
   - Get off and back on: the starter cranks without starting.
3. Switch to creative and get on the dry ATV: it runs and the tank doesn't drop. In survival, gauge below 15 % → amber +
   chime; below 7.5 % → flashing LOW FUEL.
4. Crafting:
   - craft the jerry can; it should be named "Empty Jerry Can".
   - craft it with a lava bucket: you get a full 10 L can plus a bucket.
   - right-click a lava source with an empty can: the source is consumed, the can fills, and there's sizzle and smoke.
   - right-click while looking at flowing lava: nothing happens.
5. Hold use on the dry ATV with a full can: it pours 0.5 L per tick-pulse with glugs and the action bar counts up. With a
   rider seated, the engine restarts. The tank stops at 15 L ("Tank is full").
6. Fit the box and carrier:
   - Craft the ATV Cargo Box and Can Carrier and use each on the ATV. Check they are seated on the rear rack in both
     World Look settings (Minecraft/classic and Realistic), with no clipping into the seat or rail.
   - Sneak-use opens "ATV Cargo Box". Put 2 jerry cans in the cradles: they appear strapped on the rack.
   - A second player sees everything, including the lid swinging open while the menu is open.
7. Reserve fuel: with cans in the cradles, set the tank low with
   `/data merge entity @e[type=frontierhunts:atv,limit=1,sort=nearest] {FhFuel:0.2f}` and drive. When the tank hits 0, "poured x L from the reserve
   cans" appears and the engine restarts. The **Pour cans** button does the same by hand.
8. Fill the box with items, including a shulker box, then press **Unstrap box**. The box item's tooltip lists the contents,
   and the shulker box comes out loose. Fit the box again: everything is back. Then:
   - **Remove cans**: the carrier and cans go to your inventory.
   - Hopper test: a hopper under an ATV with no box inserts nothing.
9. Fit everything, then break the ATV in survival. Expect drops of the ATV (with its fuel), the box (packed with its
   contents), the cans and the carrier, and no duplicated items.
10. Inventory key while riding opens the menu; with nothing fitted you get the hint.
11. Restart the world or server and check that fuel, box, carrier, cans and contents all persist. Legacy check: load an ATV
    from before this branch that has items in its storage; it should have a box fitted with those items.
12. Dedicated server with 2 clients:
    - one drives to dry while the other watches: the observer hears the stall and the engine stops on both;
    - two players open the menu, then one unstraps the box: both menus close and nothing is duplicated.
    - Run it with shaders on and off: the rig uses the same render type as the realistic ATV mesh.
