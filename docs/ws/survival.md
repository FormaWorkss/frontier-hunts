# Workstream `survival` — Frontier Survival: you hunt to live

Branch: `survival` (clone `/home/claude/work/survival`). `tools/compile.sh` → exit=0; `python3 tools/build.py survival-test .`
builds; `tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Offline maths harness
`tools/survival/harness/run.sh` → ALL PASS. **Not run in-game here** (no client): everything below is awaiting in-game verification.

User vision: "a player can't just play the game and have fun, they will die or slowly lose health … you have to hunt to live".
Difficulty toggle **Off / Light / Balanced (default) / Hardcore** (server config, Settings → Gameplay aids).

## 1. Nutrition (protein, fat, energy)
Three meters 0-100 next to vanilla hunger (which still works). Every food has values from a **data-driven table**
(`data/<ns>/survival/foods/*.json`; item entries > tag entries > built-in heuristic from the food component size, item name
and `c:foods/*` tags, so any mod's food gets sensible values). Sources: **game** (wild meat, fat, organs, jerky, pemmican, rabbit),
**fish**, **farm** (domestic meat, milk), **crop**, **forage**. Raw meat gives 85 %, food past 60 % of its shelf life down to 70 %.

Drain (Balanced, per in-game day): idle protein 28 / fat 18 / energy 40, plus work (vanilla exhaustion: sprinting, mining,
fighting, healing ×0.25/0.25/0.8 per point), plus shivering (burns fat and energy) and heat (sweat costs energy). A working day
≈ P38 F28 E72 → about two venison steaks, some fat (fat trimmings, tallow, bear, pork, salmon, pemmican) and 3 bread.
Metabolism also adds 0.05 exhaustion/s to the vanilla bar so an idle body gets hungry and can eat again. A night of sleep costs E10 P6 F6.

| | Light | Balanced | Hardcore |
|---|---|---|---|
| drain | ×0.7 | ×1.0 | ×1.25 |
| farm protein/fat | 100 % | 80 % | 50 % |
| crops (all) / fish | 100 / 100 % | 95 / 100 % | 75 / 80 % |
| deficits (≤30 low, ≤8 empty) | no penalties | energy low: attack speed −10 %, mining −15 %; empty: move −15 %, attack speed −20 %, mining −35 %. protein low: damage −10 %, regen ×0.6; empty: damage −25 %, regen ×0.25. fat empty: energy drain ×1.5, protein ×1.3, regen ×0.6 | same, protein empty: **no natural regen** |
| malnutrition damage | never | 1 HP / 30 s with **two** meters empty, never below ½ heart | 1 HP / 20 s with one empty, / 10 s with two, **can kill** |
| Hunter's Vigor (P≥65, F≥55, E≥50, wild game eaten within 1.5 days) | +2 hearts, regen ×1.35, move +3 % | same | same |

## 2. Body temperature
Air = summer temperature from the biome climate (piecewise: frozen peaks −15, snowy 0 → −2, taiga 10, plains 20, jungle 26,
desert 36 °C) − seasonal swing (22 °C in seasonal biomes by mid-January via `SeasonClock`, 10 cold / 6 hot non-seasonal, none
with seasons off) + day/night (±4.5, deserts ±10) − 0.08 °C/block above y 80. Nether 42, End ~8.
Felt = air (roof: blends toward 11 °C by missing sky light, +1.5; walled & roofed: +4) + nearby heat (9×5×9 scan every 3 s:
campfire 15, fire 12, lava 16, lit furnace 9, lodge stove 16, working smokehouse 6, torch 1.5; fades over 5.5 blocks, cap 30)
− rain/thunder 2.5/4 and `SeasonalWeather` blizzard severity×10 (exposed) − wind chill (wind incl. blizzard/windstorm
severity and altitude × 1.25 × exposure × (1 − windproof)) − wetness×9 (rain, swimming; dries by fire/sun) ; in water: water
temperature and clothing ×0.3; sleeping +10 (bed/cot) or **+18 (hide bedroll)**.
Comfort floor = 15 − 5 × insulation (bare clothes 1.0 → 10 °C; fat reserve ±0.4; wet clothes lose up to 60 % unless water-resistant).
Body heat H −100..+100 drifts 0.022 × deficit per s (× difficulty cold: 0.6 / 1.0 / 1.3); exercise warms; fire rewarms fast.
Thresholds: chilly −30 (HUD), **shiver −50** (aim sway ×2+, view tremble, move −8 %, mining −15 %), hypothermia −75 (move −20 %,
mining −35 %, regen ×0.5), **freezing −88** (Balanced: 1 HP / 4 s; Hardcore from −80, 1 HP / 2.5 s; Light: no damage and never
below −85). Heat: hot +50 (energy drain), heat stroke +80 (move −10 %), no damage.
Offline numbers (standing still, Balanced): January plains night bare → shiver 2.4 min / freezing 4.2 min; leather → 6.3 / 11.1;
buckskin coat+leggings+hat+mukluks → never; taiga January night in buckskin → 6.3 / 11.1; bear-fur kit → never; frozen-peak
blizzard in a fully fur-lined kit → 12.6 / 22 min. A fire rewarms a freezing body in ~2 min.

## 3. Hides keep you warm (new garments, 3D models)
| item | slot | warmth | windproof | water | recipe |
|---|---|---|---|---|---|
| Fur Trapper Hat | head | 1.0 | 60 % | 40 % | ` F ` / `FHF` (F tanned fur / bear fur, H buckskin) |
| Buckskin Coat (fringe, beaded cuffs) | chest | 1.6 | 60 | 50 | `H H`/`HHH`/`HSH` |
| Bear Fur Coat (ruff, cuffs) | chest | 3.0 | 80 | 50 | `B B`/`BHB`/`HHH` (B tanned bear fur) |
| Heavy Hide Robe (back drape, painted band) | chest | 2.4 | 90 | 70 | `TFT`/`T T` (T tanned heavy hide) |
| Buckskin Leggings (fringe) | legs | 1.0 | 50 | 40 | `HSH`/`H H`/`H H` |
| Fur Mukluks (fur cuff, beadwork) | feet | 0.8 | 50 | 50 | `F F`/`H H` |
Layering: **Fur Lining** (+0.6, `tanned fur ×2 + buckskin` or `bear fur + buckskin`) and **Fur Mittens** (+0.3 and 30 % less shiver
sway, `FSF`) are sewn into ANY worn piece in the crafting grid (camo coveralls, ghillie, carbon layer, vanilla armour…), so camo
stays outside and fur goes under. Built-in warmth for vanilla armour (leather 2.0 total, metal ~0.2 per piece), ghillie, carbon
layer, insulated camo coveralls (1.6, cover the legs too). Data override: `data/<ns>/survival/clothing/*.json`
(`{"entries":{"id":{"insulation":..,"wind":..,"water":..}}}`). Models: `survival/client/GarmentModels` builds humanoid armour
models from `GarmentGeometry` (generated with the textures by `tools/survival/garments.py`, 64×64 UV painted at 4×), cutout fringe;
chest garments draw their sleeve on the first-person arm.
**Hide Bedroll** (`FFF`/`HHH`): 2-block bed (spruce-bough mattress, fur blanket, hide pillow), 9 px high like a bed; skips the
night, +18 °C while sleeping, never sets spawn. Sleeping cold (felt < comfort − 3) wakes you chilled (H −55, Light −35).

## 4. Meat spoils + preserving
Perishables carry `frontierhunts:freshness` {made (bucketed to ¼ h so one hunt stacks), store, cure}. Shelf at room temperature:
raw meat 3 days (fowl/fish 2, heart & liver 1.5, quarters 4, aged 6), cooked meat 5, fish 3, fat trimmings 6, tallow 60, jerky 40,
pemmican never. Store rates: warm (≥26 °C) ×1.5, cool (≤10, cellars) ×0.6, cold (≤2, ice/snow next to the chest) ×0.3,
frozen (≤ −4, packed/blue ice ×2 or deep-winter cold) ×0.04. Items in your inventory follow the air around you (meat freezes
in a winter pack); containers are re-stored when opened/closed (ice/snow/packed ice neighbours, cellar sky light).
Spoiled stacks turn into **Spoiled Meat** (hunger, nausea, Hardcore poison; composts). Eating food past 80 % may make you queasy.
Stacks: same-age meat stacks; stacks within ¼ of the shelf life are folded together every 10 s with a **count-weighted
average** stamp (snapped older): splitting and re-merging gains nothing. Crafting inherits the oldest ingredient's age.
Preserving: **smokehouse** now smokes any raw meat/fish into its cooked form, marked *smoked* (×4 shelf; contracts still count
cooked venison); **drying rack** (`SSS`/`STS`/`S S`): hang up to 4 strips of raw game, dries to **Jerky** in ~1 day of sun/wind
(night ×0.35, roofed ×0.25, by a fire +1.0 day and night, humid ×0.6, dry ×1.5, rain soaks it back); **Salt** (smelt or campfire a
water bottle) + up to 8 raw pieces → *salt-cured* (×5 shelf); fat trimmings render to **Tallow**; **Pemmican** = 2 jerky + tallow +
berries → 3 (never spoils, P16 F22 E16). The game pole is cold storage as before (taken-down quarters are fresh).

## 5. Lean seasons
Condition by month (×animal trait): Jan .90, Feb .82, **Mar .76**, Apr .80, May .88, Jun .96, Jul 1.02, Aug 1.08, Sep 1.14,
**Oct 1.20**, Nov 1.16, Dec 1.02. Meat yield ×0.83 (Mar) … ×1.11 (Oct). Fat trimmings: deer 0 (Jan-Jun) → 2 (Oct-Nov), elk 4,
bears 8 (hyperphagia). Natural spawns of game turned away: Jan 11 %, Feb 27 %, Mar 40 %, Apr 32 %, May 15 %. Harvest adds
Heart & Liver and a condition line ("Prime condition… 2 fat trimmings" / "Lean late-winter animal…"). Elk/moose give a
**Heavy Hide**; 2026 wildlife drops: bison → wild game ×1.5 + heavy hide, boar → wild game, bears → bear meat + **Bear Pelt**,
wolves/coyotes/cats → **Fur Pelt**, ducks/grouse → **Wild Fowl**, pronghorn leather → deer hide; plus fat and organs.
The tanning rack tans heavy hides (bone → tanned heavy hide, flint → 3 leather), fur pelts (→ tanned fur), bear pelts (→ 2 bear fur).

## 6. UI
* HUD (`survival/client/SurvivalHud`, vanilla-style, uses the Gui's left/right heights): right above hunger: protein / fat /
  energy icons + bars (amber + shaking icon when low, red pulsing frame when empty), gold antler while Hunter's Vigor; left above
  armour: thermometer + body-heat gauge (blue→comfort→red, needle, threshold ticks, warming/cooling arrow, felt air temperature
  in °C/°F) and status glyphs (fire, roof, wet). Frost vignette (vanilla powder-snow texture) below −40, gentle first-person
  tremble while shivering (off with Reduced motion or the option). Hidden in creative/spectator/F1/mode Off/HUD option off.
* Tooltips: nutrition at the server's difficulty, source, freshness ("Fresh · keeps 3 days" / "Good · 2 days left" /
  "Getting old" / "Spoiling" / "Spoiled") + smoked / salt-cured / kept cold / frozen; warmth dots + wind/water on any worn piece.
* Settings (`FrontierSettingsScreen.buildPage`): Gameplay aids → Frontier survival (choice), Meat spoils, Body temperature,
  Lean seasons (WORLD rows). Interface → Survival HUD, Temperature units, Frost and shivers.
* Field School: four one-time field notes (`Tip.HUNGER`, `COLD`, `SPOILED`, `STOCKUP`) via `FieldSchool.survivalTip`.

## 7. API for the journal (`survival/SurvivalApi`)
`status(ServerPlayer)` / `clientStatus()` → difficulty, protein, fat, energy, bodyHeat, coreTemperature, feltTemperature,
insulation, wetness, huntersVigor, sheltered, nearFire. `stats(ServerPlayer)` (kept through death) → meals, gameMeals, fishMeals,
farmMeals, spoiledEaten, spoiledLost, preserved, frozenToDeath, coldNights, coldestBodyHeat, secondsWithVigor, secondsHungry,
`gameShare()`. `seasonYield(Level)` → phase 0-4, condition, meatMultiplier, typicalDeerFat, spawnDenial, langKey
(`survival.frontierhunts.season.<phase>`). `food(stack, level)`, `spoilage(stack, level)`, `clientSway()`, `difficulty(level)`.

## Config
Server `[survival]`: `difficulty` (OFF|LIGHT|BALANCED|HARDCORE, default BALANCED), `spoilage`, `bodyTemperature`, `leanSeasons`
(true), `drainPercent` (100, 25-300). Client `[survival]`: `survivalHud`, `temperatureUnits` (CELSIUS|FAHRENHEIT), `frostOverlay`.

## Commands
`/survival` (status, anyone) · `/survival season` (yield outlook) · op: `/survival set <protein|fat|energy|all> <0-100>`,
`/survival heat <-100..100>`, `/survival age <hours>` (ages the held food), `/survival difficulty <off|light|balanced|hardcore>` (saves the server config).

## Files
New package `survival/`: `SurvivalMath` (pure), `SurvivalConfig`, `SurvivalSync`, `FoodValues`, `NutritionTable`, `Freshness`,
`Perishable`, `Clothing`, `Thermal`, `PlayerSurvival`, `SurvivalService` (driver), `SurvivalNetwork` (`survival_state`,
`survival_foods` payloads), `SurvivalHarvest`, `SurvivalStations`, `SurvivalApi`, `SurvivalCommands`, `SurvivalContent`
(registration via RegisterEvent), `item/{GarmentItem,SurvivalItem,LiningRecipe,SaltCureRecipe}`,
`block/{DryingRackBlock,DryingRackEntity,HideBedrollBlock}`, `client/{SurvivalHud,SurvivalTooltips,SurvivalClient,SurvivalText,
GarmentModels,GarmentGeometry(generated)}`. Assets/data generated by `tools/survival/{icons.py,garments.py,data_gen.py}`
(previews: `tools/survival/model_preview.py`, `tools/survival/preview/*.png`).
Copied from dec62g8 and edited: `camp/CampStation.java` (4 hook lines, `// [survival]`).
Shared-file hook lines (all marked `// [survival]`):
* `HuntConfig.java`: `var1.push("survival"); …SurvivalConfig.server(var1); var1.pop();` after the fieldSchool server line;
  `var2.push("survival"); …SurvivalConfig.client(var2); var2.pop();` after the sound client line.
* `hunting/Whitetail.java` harvest(): `var4 = SurvivalHarvest.meat(this, var4); var5 = SurvivalHarvest.meat(this, var5);`;
  the hide stack is `SurvivalHarvest.hide(this, var6)`; `SurvivalHarvest.extras(this, var1);` before the knife damage line.
* `client/ScopeZoom.java` settleSway: drift/breath targets × `SurvivalApi.clientSway()`.
* `client/FrontierSettingsScreen.java`: 4 rows at the end of AIDS, 3 rows after the guide rows in INTERFACE.
* `guide/Tip.java`: 4 new constants at the end; `guide/FieldSchool.java`: `public static void survivalTip(ServerPlayer, Tip)`.
Merge fragments: lang `en_us.json/survival.json` (122 keys); `data/c/tags/item/foods{,/raw_meat,/cooked_meat}.json`,
`data/minecraft/tags/damage_type/{bypasses_armor,no_knockback,bypasses_shield}.json`, `data/neoforge/data_maps/item/compostables.json`.
New data: `damage_type/malnutrition`, recipes under `recipe/survival/`, tags `tanned_furs`/`tanned_hides`, block loot tables,
`survival/foods/{vanilla,frontierhunts,common_tags}.json`.

## IN-GAME TEST SCRIPT
Setup: survival world, op, `/gamerule doDaylightCycle false`, `/time set noon`, `/weather clear`. Settings → Gameplay aids →
Frontier survival = Balanced.
1. **HUD**: right above the hunger bar three small bars (red protein, cream fat, gold energy); left above the armour row a
   thermometer + gauge with a white needle near the centre and the felt temperature (~20° on plains in summer). F1 hides,
   creative hides, `Frontier survival = Off` hides and stops everything (vanilla hunger only).
2. **Tooltips**: hover bread (`Protein 4 Fat 1 Energy 21`, "Crops & baking"), cooked venison ("Wild game", "Fresh · keeps 5 days").
3. **Drain/eat**: `/survival set all 25` → bars amber, icons shake, action-bar "You are craving meat and fat", a Field note
   "Hungry for meat". Eat cooked venison → protein jumps ~22. `/survival set all 5` → red frames; after ~30 s you lose 1 HP
   (Balanced stops at ½ heart). `/survival set all 90` + eat game → gold antler (Hunter's Vigor), max health 24.
4. **Cold**: `/season set winter mid`, `/time set 18000`, stand on open plains without armour: needle slides left, arrow ▼,
   after ~2-3 min "You are shivering" + frost at the screen edges + slight view tremble; raise a rifle scope: sway clearly
   larger. ~4 min: "You are freezing!", 1 HP every 4 s. Light a campfire next to you (fire glyph) → needle returns within ~2 min.
   Build a 3×3 hut with a roof → roof glyph, warms. `/survival heat -95` for quick checks.
5. **Clothing**: give `buckskin_coat`, `buckskin_leggings`, `fur_hat`, `fur_mukluks` (`/give @s frontierhunts:...`): 3D look in
   F5 (fringe on sleeves/legs, beaded cuffs, trapper hat flaps, fur-cuffed boots); tooltip warmth dots. Repeat 4 → no shivering
   on plains. `bear_fur_coat` (shaggy ruff), `hide_robe` (back drape with a red/ochre band). First person: coat sleeve on the arm.
   Craft `fur_lining` + a camo coverall → "Fur-lined"; mittens + any chest → "Fur mittens sewn on".
6. **Wet/wind**: swim in a river in winter → wet glyph, rapid cooling; stand by a fire → dries. `/frontierweather blizzard 120`
   on a snowy peak → felt temperature plunges.
7. **Spoilage**: hold raw venison, `/survival age 60` → tooltip "Getting old"; `/survival age 20` → within 10 s it becomes
   Spoiled Meat (action-bar note). Eat spoiled meat → nausea + hunger. Put venison in a chest with packed ice on two sides,
   close/open → tooltip "· frozen"; in a desert summer "· in the heat".
8. **Preserving**: smokehouse with 4 raw game meat + coal → 4 Roast Wild Game "· smoked", keeps ~20 days. Drying rack: place,
   right-click with venison 4× (sneak = all) → red strips; in sun ~1 in-game day (`/time add 24000`) → dark jerky, take with
   an empty hand. Rain darkens progress. Smelt a water bottle → Salt; salt + 4 raw venison in the grid → "· salt-cured".
   Fat trimmings → furnace → Tallow; 2 jerky + tallow + sweet berries → 3 Pemmican (no freshness line).
9. **Lean seasons**: `/survival season` in March (`/season month 3`): ~40 % spawns turned away, "Late winter: the lean time".
   Harvest a deer in March → chat "Lean late-winter animal…", no fat; in October (`/season month 10`) "Prime condition… 2 fat
   trimmings", more venison. Kill a black bear in fall → bear meat, bear pelt, ~8 fat, heart & liver.
10. **Hides**: tanning rack: heavy hide + bone → tanned heavy hide; fur pelt → tanned fur; bear pelt → 2 tanned bear fur.
11. **Bedroll**: craft (3 fur over 3 hides), place (2 blocks), sleep at night: no "Respawn point set" message, night skips,
    you lie on top of it. Sleep in the open in winter without it → "You slept cold…" and the needle starts low.
12. **Difficulties**: Light → no damage at all, cold slowdowns halved; Hardcore → farm beef shows ~half protein in its tooltip,
    one empty meter costs 1 HP / 20 s and can kill.
13. **Multiplayer** (dedicated + 2 clients): each player has own bars/temperature; tooltips match the server difficulty; a client
    with a different local config still sees the server's mode. Relog keeps meters; death restarts at ≥60/60/70.
14. **Perf**: F3 with 5+ players: server tick unaffected (one update per player per second, heat/shelter scans every 3 s).

## Known limits / for the coordinator
* Not launched in-game. Datapack clothing overrides apply on the server; client tooltips show built-in warmth.
* Vigor's +2 hearts are a transient modifier: relogging while above 20 HP trims back to 20.
* The block model preview tool is crude (isometric painter); check the bedroll and rack in game.
* Recipes have no recipe-book advancements (JEI/EMI show them; the recipe book unlocks on first craft).
