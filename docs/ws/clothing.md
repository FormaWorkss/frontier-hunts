# Workstream `clothing`: carbon base layer under clothing, one scent function, Scent & packs tab, layering, warmth

Branch `clothing` (from fh2-gear20 6f5ad6f). `compile.sh` → exit=0; QA harness compiles against these classes.

Austin: "The scent-control clothing, the carbon suit stuff, should have a separate tab in the little tab (at the Frontier
Workbench) with the backpack and quiver, and can go UNDERNEATH clothing and actually hides your scent from all animals.
And any other clothing can be worn on top in the normal Minecraft slots without visual errors or cloth glitching. Make
sure clothing does actually keep you warm, but realistic."

## 1. Carbon base layer (under the armour slots)
- `clothing/BaseLayer` - NeoForge data attachment `frontierhunts:base_layer` (registered via `RegisterEvent`): hood, top
  (jacket **or** the one-piece Carbon Scent Suit, which then covers hood + trousers too), trousers, plus sweat. Saved with
  the player (nothing written when empty), `copyOnDeath`; `BaseLayerService.drops` (LivingDropsEvent, HIGH) drops the pieces
  like armour when keepInventory is off, so the copy is empty; with keepInventory it is kept; End return / dimension change
  keep it (non-death copy / same entity).
- `ScentControl` is no longer an `ArmorItem`: `carbon_hood/jacket/trousers` and **`scent_suit`** (was a `Coverall`; now
  `new ScentControl(Piece.SUIT)`, same id) can't go in armour slots. **Right-click** wears it (swaps; the suit takes hood and
  trousers off, a hood/trousers takes the suit off). Old saves: a carbon piece found in an armour slot moves into the base
  layer on login / within a second (chat note).
- **Field Gear panel** (the little panel beside the inventory, `client/FieldGearInventoryTab`, rewritten): two tabs -
  *Field gear* (pack + quiver, unchanged behaviour) and *Base layer* (hood / top / trousers slots; click = put on / swap /
  take off to cursor, shift-click = to inventory; the suit fills all three; scent % + bar under the slots). Both tabs have
  a **Layers** button → `clothing/client/LayersScreen` (house paper style, 640×360 design space): outer clothing (4 slots,
  warmth / wind / water each), base layer (charge bar + share), pack & quiver, a Warmth card (insulation, comfortable range,
  windproof/water, wet clothes, felt temperature) and a Scent card (the `Scent` breakdown: carbon cover & charge, motion,
  sweat, wet, spray time left, perks) with a tip line.
- Sync (`BaseLayerService` payloads, optional registrar): `base_layer` items → the wearer in full; → other players **only
  the pieces they can see** (a piece under an outer garment is not sent); `scent_state` (sweat, wet, spray end) → wearer only;
  `base_layer_action` (client → server: validated - alive, not spectator, inventory menu open unless creative, slot 0..2,
  item must fit the slot, 1 click / 2 ticks). Resent on login, respawn, dimension change, StartTracking.

## 2. Scent: one function for every animal (`clothing/Scent`)
`ScentControl.scentMultiplier(Player)` (old API, called by base-jar classes) → `Scent.factor`:
carbon = Σ share × (0.25 + 0.75 × charge) (hood .20, top .45, trousers .35, suit 1); cut = 0.88 × carbon × motion (still 1,
walking .85, sneaking .95, sprinting .60) × (1 − .45 sweat) × (1 − .40 wet); factor = (1 − cut)(1 + .45 sweat) × spray ×
journal/meal perks, clamped 0.03..1.5. Spray (`frontier_scent_cover`, 2 min): −55%, fading over the last 80 s. Full fresh set,
still: **0.12 (−88%)**; + spray 0.054; sprinting soaked 0.90. Table: `docs/ws/clothing/scent_table.md`.
- Routed: `Whitetail.perceive` (all GameSpecies: whitetail, elk, moose...; also fixes the old "spray in cover = zero scent"),
  `WindCheck.mask` (readout), `ScentOverlay` (scent cone, via scentMultiplier), and **new** `clothing/Noses` for the 2026
  wildlife (`WildlifeMob`: bears 1.6, wolf 1.4, coyote/boar 1.3, elk/moose 1.15, bison 1.0, cougar/pronghorn .8, big cats .7;
  birds 0): winded when plume × factor × nose > 0.40 within 48 blocks → flees (state 5, pressure). Plain clothes are smelled
  from 48 blocks straight downwind; a full fresh carbon layer standing still never (bear at 5 blocks: 0.18).
- Charge (on the stack, custom data `frontierhunts_carbon`, 1% steps): −0.009%/s worn (+0.04%/s × sweat, +0.006%/s moving);
  washing in water +4%/s, within 2 blocks of a real fire (Thermal.heatOf ≥ 9) +1%/s, asleep +5%/s. Sweat: sprinting, walking in
  heavy clothes, an overheated body; washed off in water. Tooltips show charge, share and warmth.

## 3. Frontier Workbench "Scent & packs" tab
`BenchTab.SCENT` (icon: scent suit) after Clothing; `BenchCatalog`: sections *Carbon base layer* (hood, jacket, trousers,
suit), *Scent cover*, *Pack & quiver*; removed from Clothing / Hunting. `tools/benches/table.py` → SCENT 7, no problems.
Mock: `tools/benches/mock_bench.py` (stand-in icon added). Lang: `bench.frontierhunts.tab.scent(.hint)`, 3 sections.

## 4. Worn-gear visuals: layering without clipping
- **Base layer render** (`clothing/client/BaseLayerRender`, player layer): each piece only where no outer garment covers it
  (hood ↔ head slot / coverall hood, top ↔ chest garment, trousers ↔ legs garment / coverall). Same outfit models, textures
  and `entityCutoutNoCull` as the other worn layers (Iris treats them alike), wide + slim, no per-frame allocation.
- **Give-way tags** (generator flags, `OutfitModel` child groups `hide_<mask>`, posed cards included - before, posed cards
  ignored the under-hem flag): 1 under a long coat's hem, 4 inside a boot, 8 under sewn-on mittens, 16 under the knee-length
  robe. Set by the callers: `OutfitClient.Armour` (legs: hem / boot / robe, chest: mittens; a legs item's waistband hidden
  under any chest garment), `CoverallLayer` (its legs give way to a legs-slot garment), `BaseLayerRender`, first-person sleeves.
- **Geometry fixes** (`tools/outfitter/outfit.py`, regenerated `OutfitGeometry` + textures): leg shells' inner face grows with
  thickness (was 2.35 for every shell → z-fight between the legs when walking); buckskin leggings 0.30→0.33 (skin overlay);
  coverall knee pad 4.0..6.0; mukluk cuff from y 7.0 at 0.70, toe cap a slab in front of the shaft; mittens 0.78 from y 8.7
  (coat cuffs give way to them); lining collar 0.5 / wider (not drawn on the bear coat / hide robe, they have their own ruff);
  ghillie thigh / shin / forearm tufts and the leggings fringe / sleeve fringe ends tagged; carbon / coverall pockets tagged.
- **Audit** `tools/clothing/audit.py`: every pair of garments (713 outfits, wide + slim, skin overlay included) with the game's
  visibility rules: coplanar faces (< 0.06 px) and inner garments pushing through outer shells → **0 findings** (559 before).
- Previews: `docs/ws/clothing/combo_*.png` (8 combinations × front/side/back/walk/sneak/riding/swim/sprint + slim),
  `docs/ws/clothing/single/after_*.png`. `python3 tools/clothing/preview_combos.py . <dir>`.
- Pack / quiver rest on the carbon top / suit when no chest garment (`Outfits.backDepth`); first-person sleeve = outer chest
  garment, else the carbon top / suit. Outfit ids cached per item (no string building per frame).

## 5. Warmth
- `Clothing.outfit` adds the base layer; wind / water now layer **per region** (head, torso, legs, feet: 1 − (1−a)(1−b)),
  region-weighted. Carbon base layer is thin (hood .15, jacket .35, trousers .25, suit .60); coveralls 1.6 → 1.4 (below
  hides). Order: fur > buckskin > coveralls > ghillie > carbon.
- `SurvivalMath.exertion`: running in heavy clothes lowers the comfortable ceiling (≤ 9 °C; bear-coat outfit sprinting at
  18 °C gets hot after ~6 min, never standing). `SurvivalMath.weatherShield`: a wind/waterproof shell keeps up to 57% of the
  driven-snow/rain chill out. Wet clothes / drying by fire: unchanged (survival).
- Offline table + checks: `docs/ws/clothing/warmth_table.md` (`tools/clothing/WarmthTable.java`, all checks pass).

## 6. Text
Lang fragment `patch/_merge/.../en_us.json/zzzzzzzz_clothing.json`: item tooltips, panel, Layers screen, messages, bench tab,
Field School wind lesson p4/p5 (carbon goes under clothes), field tips p2 (layers), winded/cold tips, journal gear hint,
onboard step 7, scent-cover tooltip.

## Files
New: `src/.../clothing/{BaseLayer,BaseLayerView,BaseLayerService,Scent,Noses}.java`, `clothing/client/{BaseLayerClient,
BaseLayerRender,LayersScreen}.java`, `expedition/WindCheck.java` (copied from base, mask → scentMultiplier),
`tools/clothing/{audit.py,preview_combos.py,mock_layers.py,WarmthTable.java,ScentTable.java}`,
`tools/qa/harness/.../ClothingTest.java`, `tools/qa/clothing.commands`, docs.
Rewritten (owned): `expedition/ScentControl.java`, `client/FieldGearInventoryTab.java` (base class replaced),
`outfitter/client/{OutfitModel,Outfits}.java`, `OutfitGeometry.java` (generated), outfitter textures.
**Shared-file hook lines** (all marked `[clothing]`):
- `hunting/Whitetail.java` perceive: `double var28 = ScentControl.scentMultiplier(var11); if (var16 != 0) {` (3 lines replaced)
- `wildlife2026/WildlifeMob.java` aiStep alarm scan: one `if (threatTicks == 0 ...) { Player w = Noses.winded(this); ... }` block
- `expedition/ExpeditionContent.java`: scent_suit registered as `new ScentControl(ScentControl.Piece.SUIT)`
- `expedition/ExpeditionGear.java`: scent_cover tooltip (2 lines)
- `benches/BenchTab.java` (+SCENT), `benches/BenchCatalog.java` (3 puts, 3 lines edited)
- `survival/Clothing.java` (values, `outfit` per region, `total/layer/builtIn`), `survival/SurvivalMath.java` (`exertion`,
  `weatherShield`), `survival/SurvivalService.java` (1 line: `* weatherShield`)
- `client/CoverallLayer.java`, `client/PackLayer.java`, `outfitter/client/OutfitClient.java` (flags), `client/FrontierGuiScale.java` (+LayersScreen)
- `workshop/WorkshopKind.java`, `workshop/EquipmentCatalog.java` (scent suit keeps its old classification)
- `tools/qa/harness/build.sh`: `QA_MOD_CP` now goes BEFORE the base jar (changed classes win); `FrontierQa.java`: `clothing` command
- `tools/outfitter/{outfit,preview}.py` (reference jar path: env `FH_REF_JAR`, default the 1.3.0 build), `tools/benches/{table,mock_bench}.py`

## QA (dedicated server, coordinator)
`bash tools/qa/server_smoke.sh <jar> tools/qa/clothing.commands /tmp/fh-clothing` → `[FHQA] clothing PASS ...`:
wear (base layer + ghillie jacket both worn; carbon can't enter armour slots; right-click suit swap; old chest-slot jacket
migrates), scent (plain 1.0 → full carbon 0.12 for deer; spray stacks; sweat; spent charge; a grizzly 20 blocks downwind
winds plain clothes but not the carbon layer), persist (save/load with charge; death without keepInventory drops 3 pieces;
with keepInventory kept; End return kept), bench (7 recipes in Scent & packs, none left in Clothing/Hunting), warmth (3
outfits, snowy plains January: insulation order, arctic comfortable, plain not).

## IN-GAME TEST SCRIPT
1. `/give @s frontierhunts:carbon_hood`, `carbon_jacket`, `carbon_trousers`, `scent_suit`, `ghillie_jacket`, `fur_hat`,
   `buckskin_leggings`, `fur_mukluks`, `bear_fur_coat`, `timber_camo_coveralls`, `scent_cover 4`.
2. Right-click the hood, jacket, trousers: action bar "… on, under your clothes"; F5: the carbon layer shows. Try to
   shift-click / drag the jacket into the chest armour slot: refused.
3. Equip the ghillie jacket (chest), fur hat, buckskin leggings, mukluks: the carbon hood / jacket / trousers vanish under
   them (nothing pokes through, no flicker). Walk, sprint, sneak, swim, crawl (prone), ride an ATV, F5 front/back; slim skin.
4. Open the inventory: the Field Gear panel on the left has two tabs; *Base layer* shows the three pieces and a scent %
   (≈12% still). Click the jacket out to the cursor and back; shift-click the hood to the bag; drop the scent suit on any slot
   (hood and trousers come off). Creative inventory: the same.
5. Click the **Layers** button: outer clothing, base layer (charge bars), pack/quiver, Warmth and Scent cards; Esc / E back.
6. Hold the Wind Checker: the cone is much fainter with the carbon layer; readout "scent 12%". Sprint for 20 s: the panel's
   scent climbs (sweat). Spray scent cover: ~5%, back up after two minutes.
7. Wade into water with the layer on: charge rises to 100% ("washed"); stand by a campfire: rises slowly ("aired out").
8. Deer: crouch 25 blocks upwind of a whitetail/elk with and without the layer (deer should only wind you without). Bears /
   wolves (2026 wildlife): standing upwind without carbon they flee; with the fresh layer, still, they don't.
9. Death: `/gamerule keepInventory false`, die: the 3 pieces drop with your armour; `true`: kept after respawn. Relog: kept.
   Nether and back: kept, still visible to a second player.
10. Multiplayer (2 clients): the other player sees your carbon pieces only where not covered; F5 matches.
11. Frontier Workbench: *Scent & packs* tab (scent-suit icon): carbon hood/jacket/trousers/suit, scent cover, pack, quiver;
    Clothing and Hunting tabs no longer list them.
12. Warmth: snowy biome at night with plain clothes vs the arctic outfit (hat, bear coat with lining + mittens, leggings,
    mukluks, suit underneath): HUD comfort range and the Layers Warmth card; sprint in the bear coat on a mild day → slowly
    gets warm; stand still → fine. Get soaked → Warmth card shows wet clothes; dry by a fire.
13. Iris + a shader pack (Photon): all worn layers render and cast shadows like armour.

## Risks / notes
- Not run in game (no client here); geometry checked offline (audit + renders with the vanilla setupAnim maths).
- Carbon pieces saved in armour slots migrate on login; anything that equips them by code into armour slots is migrated
  within a second. `Coverall.Style.SCENT_SUIT` still exists (unregistered as a Coverall).
- Survival balance touched only through clothing values, `exertion` and `weatherShield` (see the warmth table).
- The 2026 wildlife now smells hunters (before: sight and sound only); without carbon they wind you from up to 48 blocks
  straight downwind - by design ("hides your scent from all animals" needs noses to hide from).
