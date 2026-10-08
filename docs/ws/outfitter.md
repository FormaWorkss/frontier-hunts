# Workstream `outfitter`: worn gear that fits, Frontier Handbook cover, inventory icon sizes

Branch `outfitter` (from master 1650414). `tools/compile.sh` → exit=0. `python3 tools/build.py outfitter-test .` builds;
`tools/check_jar.py <jar> . --base master` → **0 errors, 0 warnings**. `tools/qa/server_smoke.sh <jar>` (run on its own
port/work dir because another workstream's server held 25599) → items ok=331 bad=0, recipes ok=259 bad=0, no ERROR/WARN
lines, no exceptions in frontier code, **no client-only classes loaded on the dedicated server**, no crash report (the
harness's loot-table / addFreshEntity notes are unchanged from master). Offline checks: all 36 outfit tables parse and all 72 outfit models (wide + slim) bake with the real
Minecraft classes in a plain JVM; GUI audit 337 item models, 0 flagged. **Not run in-game here** (no client).

User feedback: "make all the clothing / gear much better … high quality hunting gear … fits on character really well,
also works with all movement, no weird clothing flapping around — it did this with the bear jacket" · "frontier
handbook looks a little boring on the outside" · "decoy for duck is too big in creative tab".

## 1. Why the bear jacket flapped
Not a physics sim: the bear coat's skirt (and the hide robe's 18 px back drape, the buckskin coat's skirt) were boxes on
the **body** part hanging 3-6 px below the hips. The body only leans (sneak 0.5 rad) and never follows the legs, so the
legs swung straight through the skirt when walking/sprinting, the skirt kicked out behind the legs when sneaking and
stuck out at odd angles when swimming, prone or riding.

## 2. New worn-gear system (one spec for everything)
`tools/outfitter/outfit.py` defines every worn piece as boxes on the vanilla parts, paints its textures and writes
`outfitter/client/OutfitGeometry.java` (string tables, parsed once). `OutfitModel` builds a `HumanoidModel` from it.
Rules: every box hangs on head/body/arm/leg at the vanilla pivots (rigid, nothing animates on its own); **coat hems and
robe skirts are drawn on the thighs** (each leg carries its piece; the model shows the legs while the chest slot
renders), so they move with walk/sprint/sneak/swim/prone/riding and nothing passes through them; dangling bits (fringe,
fur shag, ghillie tufts) are rigid cards that never hang past the part they are tied to; shells +0.30-0.45 px like
vanilla armour, heavy fur +0.7-0.95; details are separate boxes ≥ 0.03 px off the shell (no coplanar faces); left limbs
have their own UVs (the print does not mirror); **slim (Alex) skins get 3 px sleeves** (own boxes); leggings fringe that
a long coat covers is hidden. Textures: `textures/entity/outfitter/<group>_hd.png` (4 texels/px, Ultra/Custom) and
`<group>.png` (1 texel/px, quantised, picked on the **Vanilla** preset) via `outfitter/OutfitTextures`.

| piece | look |
|---|---|
| 7 camo coveralls + Carbon Scent Suit | open-face hood with stiffened brim (hidden under a helmet), zipped jacket with flap chest pockets + zip pull, hand-warmer slashes, elastic waist, articulated elbows/knees, cuffs with tabs, cargo pockets, knee pads, ankle cuffs. Camo printed continuously across panels at real scale: Timber (bark + oak leaves), Autumn, Marsh (reeds, cattails), Prairie (grass), Snow, Ridgeline Digital (1 px/2 px blocks), Blaze (orange + leafy break-up), Carbon (charcoal ripstop + tonal leaves) |
| Carbon hood / jacket / trousers | same tailoring as separate pieces (grey zips, chest zip, hem band, waistband with belt loops and fly) |
| Ghillie hood / jacket / trousers ×4 patterns | separate model per piece (the trousers no longer draw the jacket), shell in the pattern's own photo camo + knotted net, ~100 rigid jute/leaf tufts angled out, veil over the neck; new 32×32 icons (old 3D model kept for hands/ground) |
| Fur trapper hat | fur crown, folded-up brim, ear and neck flaps with shag edge, sinew ties |
| Buckskin coat | smoked buckskin, dark yoke with fringe front and back, horn-button placket, sleeve fringe, beaded cuffs, belt with iron buckle, fringed hem on the thighs |
| Buckskin leggings | fringe on the outer seam (hidden under a long coat), beaded garters, laced waist |
| Bear fur coat | clumped bear pelt, ruff with shag, horn toggles, belt, big fur cuffs with shag, knee-length hem on the thighs |
| Heavy hide robe | painted hide (red/ochre/black/blue band on back, cape and hem), bison-fur collar, cape edge and cuffs, finger-woven sash with tassel, hem to the knee |
| Fur mukluks | smoked hide boot, rawhide wraps, bead band, soft toe, fur cuff with shag |
| Extras (any chest piece) | sewn **fur mittens** and **fur-lined collar** now show (third and first person) |
| Hunter's Field Pack | shoulder straps (ladder lock), sternum strap, padded hip belt; the pack and quiver sit on the actual depth of the worn chest layer (was 0 / +0.06 for anything) |

First person: the worn chest piece's sleeve (coveralls, hide coats, ghillie/carbon jackets) and mittens are drawn on
the arm with vanilla's exact `renderHand` pose (`OutfitClient.Arms`).

## 3. Frontier Handbook
Inventory: new 32×32 pixel icon (leather book, gold title, antler-and-compass emblem, brass corners, strap, ribbon).
Hand / ground / item frame: a 3D leather field book (`models/item/frontier_handbook_3d.json`: boards, rounded spine with
raised bands, page block, brass corners, buckled strap, ribbon; 8 texels/unit). Item model = `neoforge:separate_transforms`.
Handbook screen: the plain "HANDBOOK" header is now a tooled-leather cover plate (emblem, brass corners, strap) with the
title gold-stamped on it (`outfitter/client/HandbookCover`, text stays translatable).

## 4. GUI sizes
`tools/outfitter/gui_audit.py` renders every item model like `GuiGraphics.renderItem` (display.gui, element rotations,
field_shelter faces, separate_transforms) at GUI scale 2 and measures the slot fill. Mallard decoy: gui scale 1.2 →
0.9, centred (was 15×13 px and clipped at the top; now 11.5×9.6). Everything else was within a normal slot (largest:
fishing station 0.35 px over - left as is, its block model has a pre-existing missing particle texture
`frontierhunts:particles/steel`). 70 code-rendered (`builtin/entity`) items can only be checked in game (list in audit.json).

## Files
New: `src/.../outfitter/OutfitTextures.java`, `outfitter/client/{OutfitModel,Outfits,OutfitClient,OutfitGeometry(generated),HandbookCover}.java`;
`patch/assets/frontierhunts/textures/entity/outfitter/**` (19 groups × Vanilla/HD), `textures/item/handbook/*`,
`textures/gui/handbook/cover_plate.png`, `models/item/frontier_handbook_3d.json`, `textures/item/ghillie/*.png`,
`models/item/ghillie*_3d.json` (copies of the old 3D models); `patch/_remove/outfitter.txt` (8 old coverall skins).
Tools: `tools/outfitter/{outfit,materials,mcr,wear,preview,before,gui_audit,handbook_art,ghillie_icons}.py`.
Replaced/edited (all marked `[outfitter]`):
* `client/CoverallLayer.java`, `client/GhillieModel.java` - rewritten on OutfitModel (same public names; FrontierClient's
  `new GhillieModel.Extensions()` registration is unchanged, item set unchanged).
* `client/PackLayer.java` - `offset()` from `Outfits.backDepth`, `harness(...)` before the pack; `client/QuiverLayer.java` - one offset line.
* `expedition/GhillieSuit.java` (`getArmorTexture` → `OutfitTextures.of("ghillie/<pattern>")`), `expedition/ScentControl.java`
  (+`getArmorTexture` → `carbon/carbon`) - copied from dec62g8.
* `survival/item/GarmentItem.java` getArmorTexture (1 line); `survival/client/GarmentModels.java` registers `OutfitClient.Armour`
  for the six garments (its own sleeve handler moved to OutfitClient). Removed `GarmentGeometry.java`, `tools/survival/garments.py`,
  `textures/models/armor/survival/*`.
* `guide/client/HandbookScreen.java` sidebar(): header → cover plate (7 lines); `tools/onboard/mock_handbook.py` mirrors it.
* `patch/.../models/block/mallard_decoy.json` display.gui; `models/item/frontier_handbook.json` + its texture.
Client extensions: the carbon pieces get their own registration (new, disjoint set); garments and ghillie keep theirs.

## Previews (docs/ws/outfitter/)
`compare_*.png` before/after (front/side/back, walk, sprint, sneak, swim, prone, riding, sleeping, bow, rifle, slim, sprint
from behind), `after_*.png`, `after_vanilla_preset.png`, `after_all_coveralls_ghillies.png`, `compare_pack.png`,
`handbook_after.png`, `handbook_screen_640x360.png`, `ghillie_icons_after.png`, `gui_items_before_after.png`,
`inventory_grid_{before,after}.png`. Regenerate: `python3 tools/outfitter/outfit.py .` then `preview.py . <dir> [hd|vanilla]`.

## IN-GAME TEST SCRIPT
Setup: creative, `/gamerule doDaylightCycle false`, a mirror-like spot (F5 front and back), Ultra preset first.
1. **Bear coat (the reported bug)**: `/give @s frontierhunts:bear_fur_coat`, wear it. F5: walk, sprint, sneak (hold),
   sneak-walk, jump into water and swim, crawl prone (prone key), ride an ATV, sleep in a tent/bedroll. Expect: the
   knee-length fur hem sits on each thigh and moves with that leg; nothing flaps, nothing pokes through the legs, nothing
   sticks out behind when sneaking.
2. Repeat 1 with `buckskin_coat` (yoke + hem fringe), `hide_robe` (painted band, fur collar/cape edge, sash).
3. **Trapper kit**: `fur_hat`, `buckskin_coat`, `buckskin_leggings`, `fur_mukluks`. Leggings fringe is hidden while the
   coat is on and shows when the coat comes off. Mukluk fur cuff sits below the coat hem.
4. **Lining/mittens**: `/give @s frontierhunts:timber_camo_coveralls[frontierhunts:lining=3]` → fur collar at the neck,
   fur mittens on the hands (third and first person). Same on a vanilla iron chestplate.
5. **Coveralls**: each of `timber/autumn/marsh/prairie/snow/digital/blaze_camo_coveralls` and `scent_suit`: hood with the
   face open, pockets, knee pads; camo continuous over body/legs. Put on a helmet: the hood disappears.
6. **Carbon** `carbon_hood/jacket/trousers` and **ghillie** (`ghillie_jacket`, `ghillie_snow_trousers` …): trousers alone
   show only waist + legs; tufts stay on their limb in every pose; inventory icons are garment shapes.
7. **Slim skin**: switch to an Alex/slim skin: sleeves of every jacket/coat hug the 3 px arms (no gap, no overhang).
8. **First person** with an empty hand: the sleeve of the worn coat/coverall/jacket covers the arm.
9. **Vanilla preset**: Video Settings → Frontier Hunts → Vanilla: all gear switches to the 16 px-per-block look (reload
   not needed for armour; coveralls/extras switch at once). Back to Ultra.
10. **Pack**: wear the Hunter's Field Pack (Field Gear tab or Curios back slot): straps over the shoulders, sternum strap,
    hip belt; the pack rests on the back with bare skin, coveralls and the bear coat (no gap, no sinking in). Add the
    quiver: it still sits on the pack / back.
11. **Handbook**: inventory icon (leather book), hold it (3D book with strap and brass corners) in first/third person, drop
    it, put it in an item frame. Press H: the sidebar starts with the leather cover plate and the gold title.
12. **Creative tab**: Mallard Decoy now the size of a normal block item, centred.
13. **Multiplayer**: dedicated server + 2 clients (one slim): each sees the other's gear, hems and harness correctly.

## Known limits / for the coordinator
* Not seen in game; geometry/pose checks are offline renders with the vanilla setupAnim maths (docs sheets).
* Shaders: textures are plain cutout entity textures (no emissive/translucency), so Iris packs treat them like armour.
* The 70 code-rendered item icons were not measurable offline; nothing in them was changed.
* Other mods' armour stands/mobs wearing these pieces use the wide-arm models (vanilla behaviour).
