# items workstream: anatomy binoculars removed, new-item icons, creative tab placement

User feedback: "remove anatomy binos completely" and "the new items u added have huge icons and need creative tab fixing".

## 1. Anatomy binoculars removed
- `expedition/ExpeditionContent` (copied from the dec62g8 decompile, one decompile fix at the `PIECE` registration:
  explicit `StructurePieceType.StructureTemplateType` cast) no longer registers `anatomy_binoculars`.
- **Old worlds:** `HuntContent.ITEMS.addAlias(frontierhunts:anatomy_binoculars -> frontierhunts:binoculars)`, the same
  NeoForge registry alias the mod already uses for the retired tents. Any saved stack (inventories, chests, item
  frames, dropped items) loads as plain Binoculars, with no "unknown item" errors and no items turning into air.
  The alias works on both sides, and `MappedRegistry.get/getHolder` resolve it.
- `client/ExpeditionOptics`: the organ X-ray view mode, the anatomy HUD (organ index, reticle focus, heart/lung rates),
  the "ANATOMY / FIELD STUDY" title and `focusedOrgan()` (no callers) are gone. Thermal and night-vision views are unchanged.
  `DeerAnatomyView` (client) is kept in the jar but unused. The kill cam's X-ray (`KillCamOverlay`) uses
  `hunting/DeerAnatomy`, `DeerAnatomyMesh` and `DeerOrgan`, all of them untouched.
- `workshop/WorkshopKind`: dropped from the optics bench list. `expedition/Campaign`: the "A clean shot" mission text no
  longer mentions the item.
- Assets: model, texture and recipe are dropped from the jar through `patch/_remove/items.txt`. The lang key is removed
  through a `null` value in `patch/_merge/.../en_us.json/items.json`.
- **tools/build.py (shared):** new support for `patch/_remove/<ws>.txt` (base-jar entries to drop) and for `null`
  values in merge fragments (which delete the key). Both are marked `[items]`.
- One harmless log line is possible: a player who had already unlocked `expedition_anatomy_binoculars` in the recipe book
  gets vanilla's one-time "Tried to load unrecognized recipe ... removed now" when they first join.

## 2. Icons
The Camp Post and the Big-Buck Board item models used GUI scales of 0.95 and 1.25 on models that fill a whole block, so
they overflowed the slot (the board was about 23 px wide). Both are now `neoforge:separate_transforms` models, the same
pattern as the landing net:
- in the inventory, hotbar and creative tab, a flat 32x32 icon (`textures/item/camp_post.png`, `big_buck_board.png`);
- everywhere else, the original 3D model (`models/block/<name>_inventory.json`, same elements) with vanilla block-item
  transforms: ground 0.25, frame 0.5, third person 0.375, first person 0.4.
- Hound Lead: the 16x16 square-spiral icon is redrawn as a 32x32 coiled leather lead with a brass snap, in the same style.
- Art generators: `tools/items_icons.py` (new), and `tools/camps_art.py` now writes the wrapper and inventory models.

## 3. Creative tab (Frontier Hunts / field_equipment)
- New `items/TabPlacement.after(event, item, anchors...)`: inserts after the first anchor that is present, skips items
  that are already listed (no duplicate crash) and appends when no anchor is present.
- Camp Post goes after the Game Pole, with the Big-Buck Board after it (`camps/CampsContent.creative`).
- Hound Lead goes after the Wind Checker (`tracking/hound/HoundContent.creative`).
- Frontier Structures' window entries are untouched.

## In-game test script
1. Old world: in a world that still has anatomy binoculars from the old jar, start with this jar. The item is now
   Binoculars and the log has no errors about `anatomy_binoculars`.
2. `/give @s frontierhunts:anatomy_binoculars` should give Binoculars (alias). JEI/EMI and the recipe book show no anatomy
   binoculars or recipe.
3. Use the binoculars and the thermal binoculars: the normal and thermal overlays work and there is no anatomy HUD.
4. Shoot a deer in the heart: the kill cam X-ray still shows the organs.
5. Open the Frontier Hunts creative tab: the Camp Post and the Big-Buck Board sit right after the Game Pole, and the
   Hound Lead sits right after the Wind Checker, each listed once. Their slot icons are the same size as the other
   icons.
6. Hold each one in first and third person, drop it, and put it in an item frame. Each shows the 3D model at a normal
   block-item size.
