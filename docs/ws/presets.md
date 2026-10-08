# Workstream `presets`: two graphics presets, Vanilla and Ultra

Branch `presets` (from master 0bf1526). The Balanced graphics preset is removed. The mod now has two presets:
**Vanilla** (stored in the config as `CLASSIC`, so old files still load) and **Ultra**.

## What changed

**Presets and migration** (`client/FrontierGraphics`)
- `of()` returns the Vanilla bundle for CLASSIC and the Ultra bundle for everything else. Ultra's bundle is unchanged.
- `migrate()` runs on the first client tick (RealisticWorld) and whenever the settings screen opens:
  - `graphicsPreset = BALANCED` (or the older HIGH) becomes Ultra through `apply(ULTRA)`. This is logged once:
    `Frontier Hunts: the Balanced graphics preset has been retired - switched to Ultra...`
  - `animalStyle = BLOCKY` (the Minecraft+ look, which only Balanced or a Custom setup used) becomes REALISTIC.
    This is also logged once.
  - Packs picked for the Balanced slot (`presetPacks.balancedResourcePack/ShaderPack`) move to the Ultra slot when
    Ultra has none, and the Balanced slot is cleared.
- `animalStyle()`, `vanillaAnimals()` and `realisticAnimals()` treat BLOCKY (or an unreadable config) as REALISTIC.
  `stylizedAnimals()` always returns false. This makes every Balanced render path unreachable, even if someone edits
  the config file by hand later.
- `matching()` and `matches()`: **Ultra stays Ultra while its performance settings are lowered.** Those settings are
  effects quality, animal detail, grass thickness, waterfall detail and distant trees. Vanilla ignores animal detail
  and tree distance because they do nothing in a Minecraft world. Every other change still makes the preset Custom.
  `migrateTrees()` was removed: it would have undone a lowered tree distance.

**Config** (`HuntConfig`, `perf/PerfConfig`; every edit is marked `// [presets]`)
- The defaults are now exactly the Ultra preset: graphicsPreset ULTRA, quality CINEMATIC, animalStyle REALISTIC,
  animalDetail ULTRA, worldLook REALISTIC, grassHeight 110, grassThickness THICK, waterfallDetail ULTRA,
  treeDetail ULTRA. Before this change they were the Balanced values.
- The enum constants `GraphicsPreset.BALANCED/HIGH` and `AnimalStyle.BLOCKY` stay (marked @Deprecated) only so old
  files parse. The config comments now describe the two presets.

**Settings screen** (`client/FrontierSettingsScreen`, `settings/SettingsLayout`)
- There are two preset cards, Vanilla and Ultra, side by side (`cardColumns()` is now 2). The detail panel, footer and
  pack rows show "Vanilla" through a new `title(GraphicsPreset)`. The Balanced card and its pack rows are gone.
- New section on the preset page, **Tune Ultra for your PC**: Distant trees, Animal detail, Grass density, Waterfall
  detail and Effects quality. Each is also on its own page. Changing them keeps Ultra selected, and the GPU-load meter
  drops (fully lowered: 43 vs 100).
- Animal style offers only Minecraft and Realistic. Minecraft+ is gone.
- The middle quality level (stored as BALANCED) is labelled **Standard** everywhere: Effects quality, Falling leaves,
  Storm effects and Distant trees, so no setting looks like the old preset. Survival difficulty "Balanced" is gameplay
  and is unchanged.
- `ChoiceRow.current()` now finds the value's position in the values the row shows. Before, it used the enum ordinal,
  which broke rows that offer only some of the enum's values.

**Renderers**
- `WildlifeRenderer` and `HoundRenderer`: anything that isn't Vanilla is Ultra. The Balanced pixel-art coats are no
  longer referenced. **The `*_bal.fhsk` low meshes stay**, because they are Ultra's distant level of detail (with
  `real/*_far.png`).
- `BlockyCoats` (new src override): always the `*_vanilla_*` coats. Box deer are drawn only on Vanilla now.
- `CamoRifleModel`: Standard (BALANCED) effects use the close rifle mesh. Ultra (Cinematic) is unchanged.
- `RutFightClient.preset()` is 0 (Vanilla) or 2 (Ultra).
- Comments mentioning Classic/Balanced were updated in BoxFightModels, FightFits, RutFightNet, KillCarcassRenderer,
  SignTrunkProbe, SeasonalLeafModel, WildlifeRig and DeerSignRenderer.

**Removed from the jar** (`patch/_remove/presets.txt`; the 16 pixel coats were also deleted from `patch/`)
- `textures/gui/settings/preset_balanced.png`
- `models/item/camo_rifle_balanced.fhrm`
- `textures/entity/blocky/{whitetail,elk,moose}_enhanced_{summer,winter}.png`
- `textures/entity/wildlife/bal/*.png` (14 species plus 2 hound coats)
- `tools/tracking/hound/hound.py` no longer writes the hound pixel coats.

Jar size: **176,309,861 to 173,994,489 bytes (-2.31 MB)**. Kept on purpose:
- `ElkCubes`, `MooseCubes` and `WhitetailCubes`: the Vanilla antler code reads constants from them.
- `StylizedAnimal`: already dead in master (`active()` returns null) and wired into the dec-only DeerMount.

**Docs**: `docs/MOD_PAGE_PERFORMANCE.md` now describes the tree-distance table with Ultra and Tune Ultra.
The Field School, journal and lang files had no preset text.

## Checks
- `tools/compile.sh`: exit=0
- `tools/presets/run.sh` (new): preset bundles, Ultra and Vanilla tolerance, retired values resolve to Ultra, and the
  config defaults equal Ultra. Passes.
- `tools/gui/run.sh`: LAYOUT OK. The preset page spec was updated to 2 cards, the Tune Ultra rows and 2x2 pack rows.
- `tools/rutfight/run.sh`: 432 checks, 0 failed.
- `check_jar.py <jar> <clone> --base master`: 0 errors, 0 warnings. A byte scan of the built jar finds no class or JSON
  that references a removed path.

## In-game test script
1. **Balanced migration.** Close the game. In `config/frontierhunts-client.toml` set `graphicsPreset = "BALANCED"`,
   `animalStyle = "BLOCKY"`, `balancedResourcePack = "<any pack id>"` and leave `ultraResourcePack = ""`.
   Start the game.
   - The log has the "Balanced graphics preset has been retired" line once.
   - At the title screen the realistic world pack switches on (one resource reload).
   - The config now has ULTRA, REALISTIC, ULTRA detail, REALISTIC world, the pack moved to `ultraResourcePack` and
     `balancedResourcePack = ""`.
2. **Fresh config.** Delete the client config and start the game. The preset is Ultra, with no migration log line.
3. Open Video Settings, then **Frontier Hunts...** (or the settings key). The preset page has two cards, **Vanilla**
   and **Ultra**, side by side, Ultra highlighted. Hover each card: the detail panel lists 4 lines. The footer says
   PRESET Ultra.
4. **Tune Ultra.** Set Distant trees to Performance, Animal detail to Low, Grass density to Light, Waterfall detail to
   Low and Effects quality to Performance. The footer still says Ultra and the GPU load meter drops. Close the screen:
   the chunks reload once (grass). Reopen it: still Ultra, and the values are kept. Click the Ultra card: everything is
   maxed again.
5. Change Trees & world > Grass height: the footer says Custom.
6. **Vanilla.** Click Vanilla, then Done.
   - `/summon frontierhunts:whitetail`, `/summon frontierhunts:wolf`, `/summon frontierhunts:grizzly`, plus
     `/summon frontierhunts:tracking_hound`: all are Minecraft-style box mobs.
   - Trees, leaves and grass are plain Minecraft. There are no flushing birds, breath vapor or spindrift.
7. **Ultra.** Click Ultra, then Done.
   - The same mobs are sculpted with photographic coats up close.
   - Walk away about 30-60 blocks: wildlife and the hound swap to the low mesh with the distant coat. They must
     **never** be missing a texture (no purple/black).
   - Round trees and bark are on.
8. Wildlife page: Animal style cycles only Minecraft and Realistic. Effects, Seasons and Atmosphere show
   Performance / **Standard** / Cinematic. Performance page, Distant trees: Performance / Standard / Ultra / Maximum.
9. Set Effects quality to Standard and hold the Ridgeline (camo) rifle in first and third person: it renders (close mesh).
10. Rut fight on both presets: summon two mature bucks, then run `/frontierhunts rut fight lock`. The racks lock on
    Vanilla and on Ultra.

## For the coordinator
- **Fresh installs now start on Ultra**, the heaviest look. That follows "Balanced becomes Ultra". If the onboard
  workstream asks new players to pick a preset, call `FrontierGraphics.apply(CLASSIC or ULTRA)`.
- Edits to shared files are small and marked `// [presets]`: HuntConfig, PerfConfig, FrontierSettingsScreen,
  SettingsLayout, WildlifeRenderer, HoundRenderer, RutFightClient, CamoRifleModel.
