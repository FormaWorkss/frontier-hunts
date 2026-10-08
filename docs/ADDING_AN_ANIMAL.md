# Adding an animal to Frontier Hunts

Everything that has to change when a new species joins the game, or when a beta animal is finished and goes into the
wild. Paths are relative to the repo root; `src/` is `src/com/formaworks/frontierhunts/`.

## 1. The animal itself
- **Entity and spawn egg:** `src/wildlife2026/WildlifeSpecies.java`. Add the species (id, hitbox, health, speed, colour, scale).
  `WildlifeContent` registers the entity type and the spawn egg from it.
- **Model, textures, animations:** `assets/frontierhunts/models/wildlife/<id>.*`, `textures/entity/wildlife/real/<id>.png` (and `_far`).
- **Sounds:** `sounds/wildlife/<id>_*.ogg`, plus entries in `sounds.json` and subtitles in the lang file.
- **Lang:** `entity.frontierhunts.<id>` and `item.frontierhunts.<id>_spawn_egg`.

## 2. Beta or finished
- **`src/wildlife2026/Beta.java`:** a beta animal is in `BETA`; a finished one is in `FINISHED`.
  - Beta: spawn egg only, no natural spawns, contracts not posted, hunt card says beta, no journal goals.
- **Lang:** remove the "(Beta)" from the egg (`patch/_merge/.../zzzzz_beta.json`) and from any tag that now covers a finished animal.

## 3. Spawning in the wild (finished animals only)
- **Biome modifier:** `patch/data/frontierhunts/neoforge/biome_modifier/wildlife2026/<id>.json` (weight, group size).
  - Delete the line for it in `patch/_remove/beta_animals.txt`.
- **Biome tag:** `data/frontierhunts/tags/worldgen/biome/wildlife2026/<id>.json` lists where it lives.
- **Spawn placement rules:** `WildlifeContent` (ground and light).

## 4. Hunting rules
- **Licence rules:** `src/licence/Regulations.java`.
  - The species rule: `big(...)` with its tag kind and open months, or `bird(...)` with a stamp.
  - Varmints get `put(new Rule(... Group.VARMINT ...))`.
  - A new tag kind goes in `TagKind`. Its item, icon and lang come with `LicenceContent`.
- **Hand tagging:** `src/licence/Tagging.java`.
  - Big game is tagged by hand. Deer, elk and moose go through `Whitetail`; wildlife mobs through `Tagging.death`.
  - A new big-game wildlife mob needs the same `tagAction` / `watch` hooks as `Whitetail`.

## 5. Hunting content
- **Hunt book:** `src/hunts/HuntBook.java`.
  - `hunt(Quarry.X, "<spawn tag>", new Contract(...), milestones...)`.
  - Add a `Quarry` entry in `src/camps/Quarry.java` (title, male/female names, kind, base weight).
- **Hunt card text:** `hunts.frontierhunts.<id>.brief`, `.tagline`, and the milestone titles and hints (`journal.frontierhunts.check.hunt_<id>_*`).
- **Journal checklist:** `src/journal/Checklist.java`.
  - The species entry `sp_<id>` and its XP.
  - Bump `FINISHED_SPECIES` for "Every species".
- **Assignments (optional):** `patch/data/frontierhunts/frontierhunts/assignment/*.json`.
  - The hunts harness counts the species assignments; update its expected number.
- **Weekend events:** `src/camps/HuntEvents.java` (scoring per species) and `CampsConfig.rotation`.
- **Trophies and records:**
  - `Quarry` weights.
  - Antler or skull scoring if it has a rack.
  - `big_buck_board` and trophy plinth if it can be mounted.

## 6. What it leaves behind
- **Harvest yields:** `src/survival/SurvivalHarvest.java` (meat kg) and `src/recipes/PeltDrops.java` (pelt or hide).
  - Plus its loot table, and nutrition in `NutritionTable`.
- **Tracking:**
  - Track prints in `tools/tracking/gen_prints.py` and the field guide's tracks page.
  - Blood trail settings, if it can be wounded and tracked.
- **Trail camera:** `src/trailcam/TrailcamScene.java` (framing scale).
- **Kill cam:** `src/killcam/KillCamServer.java` (which species get a replay).
- **Ecology:** predators and prey relations and alarm calls in `src/ecology/`.

## 7. Check it
- **Harnesses:** hunts, journal, licence, academy, wingshot, rutfight and livingworld (`tools/*/harness/run.sh`, run from the tools copy).
- **Jar checks:** `tools/check_jar.py` and `tools/recipe_audit.py` on the built jar.
- **In a test world:**
  - `/locate` its biome.
  - Watch it spawn, hunt it, tag it, skin it.
  - Check the hunt card, the journal and the trophy board.
