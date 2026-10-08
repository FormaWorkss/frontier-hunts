# Workstream `regions` — arrival cards (region / biome title card)

Branch: `regions` (from master 0bf1526). `tools/compile.sh` → exit=0. Jar `python3 tools/build.py regions-test /home/claude/work/regions`
builds; `tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Offline harness `tools/regions/harness/run.sh` →
ALL 26 CHECKS PASSED. **Not run in-game here** (no client): the test script below is still to do.

## What it does
User request: the join cinematic that shows your region should also play every time you enter a new region or biome, and tell
you the biome.

* **The join card it mirrors** is `FrontierClient`'s arrival card (letterbox bars grow in, "F R O N T I E R   H U N T S" in cream,
  region name in gold, 16-tick fade-in / 22-tick fade-out, no camera control). The arrival card copies that look exactly
  (same colours `0xE9E4D5` / `0xD9B878`, bar black `0xD5000000`, bar height 7.5 % of the screen, same fade lengths) and adds:
  letter-spaced region headline whose tracking opens up during the fade-in, gold hairlines drawing out either side, the biome
  name as a 2× gold title that rises 6 px into place, and a delayed muted subtitle (season + notable game). A faint dark band
  behind the text keeps it readable on snow / bright shader skies. Bars are fractional-height (smooth at every GUI scale) with
  feathered inner edges, and are drawn **below the vanilla HUD** (layer registered below `CROSSHAIR`), so hotbar, hearts and
  crosshair stay visible. It never takes the camera or input; you keep walking.
* **Styles**: new region / join → full bars; new biome inside the same region → thinner bars (5 %); riding fast (> 9 m/s on a
  vehicle/horse), gliding faster than 18 m/s or elytra/wingsuit flight → **compact** card (no bars, two small lines at 14 % height,
  75 % of the length). Reduced motion → no bars / no tracking / no rise (same rule as the join card).
* **Text**: headline = reserve region from the mod's synced region registry (`HuntDefinitions.region`, e.g. "Pinewood Forest",
  unmatched overworld biomes give the mod's own "Uncharted country"); other dimensions use the dimension name (`dimension.ns.path`
  lang key, else "The Nether"/"The End"/title-cased id). Title = biome name via its `biome.ns.path` lang key (vanilla, the mod's
  own and any modded biome), else a tidy title-cased id. Subtitle = "Early Fall" / "Fall" / "Late Fall" (only if seasons are on)
  + up to 3 notable game from the mod's species habitat biome tags (`moose_habitat`, `elk_habitat`, `whitetail_habitat`,
  `wildlife2026/*`; big game first), e.g. `Early Fall   —   Elk  ·  Whitetail  ·  Ruffed Grouse`.
* **Join**: if the reserve's own join card plays, its timing is read from `FrontierClient.arrivalTicks` (reflection, read-only,
  no edit to that class) and the **biome + subtitle lines are added under it** on its exact fade. If it does not play (reserve
  mode off, Nether/End/modded dimensions) the arrival card shows the starting place itself ~2.5 s after the world appears.
  Same on every dimension change. The training grounds never show anything.
* **Anti-spam** (`regions/ArrivalTrigger`, pure Java, harness-tested): biome sampled 4×/s at the feet (only in loaded chunks);
  a new biome must be held **3 s and 16 blocks walked inside it** (or 10 s standing still; teleport distance counts) — border
  flicker and short detours never fire. Same biome not repeated within **5 min** (setting); a different region always shows,
  except the same region again within 3 min (stops ping-pong along a region border). At least 12 s between two cards; a card
  waiting on suppression shows as soon as it clears, or is dropped silently after 15 s so nothing appears late.
* **Suppressed** (held, not lost): any open screen/menu, F1, combat (damage taken or entity attacked in the last 10 s), aiming
  (bow/crossbow/trident/spyglass draw, rifle aim with a scope, expedition aim, +2 s), kill cam, hunt cinematics, glider launch
  cinematic, camera lens, darkroom, academy fades/home card, the reserve join card, asleep/dead. A card that is up fades out in
  0.4 s when you take damage, aim or a cinematic starts. Paused game freezes it (like the join card).
* **Cost**: one cached biome lookup every 5 ticks; per-biome name/region/game cache (cleared on logout, tag sync, language
  change); while a card is up ~30 text draws + a few quads per frame. No world rendering, no mixins, no network traffic, no
  server code → identical in singleplayer and on a dedicated server; Distant Horizons / Xaero / Iris / Sodium unaffected.

## Settings (Interface page, new section "Arrival cards") — client config `frontierhunts-client.toml` `[arrivalCards]`
| Row | Config | Values | Default |
|---|---|---|---|
| Arrival cards | `mode` | Off / Regions only / Regions + biomes | Regions + biomes |
| Arrival card length | `durationSeconds` | 3–8 s | 5 s |
| Repeat after | `repeatMinutes` | 1–30 min | 5 min |

Off also hides the biome lines under the reserve join card (the join card itself is unchanged). Regions only still shows the
join/dimension card.

## Client command `/arrivalcard`
`/arrivalcard` or `status` (mode, here: biome id, region, game; trigger state; what is blocking a card right now), `show`
(region card for this spot now), `biome` (biome style), `compact`, `reset` (forget the repeat memory), `off|regions|biomes`
(set the mode, saved).

## Files
New (owned): `src/com/formaworks/frontierhunts/regions/RegionsConfig.java` (client options), `regions/ArrivalTrigger.java`
(state machine), `regions/client/ArrivalCards.java` (tick, suppression, join hook, GUI layer `frontierhunts:arrival_card`,
command), `regions/client/ArrivalCard.java` (drawing), `regions/client/PlaceInfo.java` (names, region, game, season),
`patch/_merge/assets/frontierhunts/lang/en_us.json/regions.json`, `tools/regions/harness/` (run.sh + ArrivalHarness).
Shared-file hooks (all marked `[regions]`):
* `HuntConfig.java` client builder, after the academy line:
  `var2.push("arrivalCards"); com.formaworks.frontierhunts.regions.RegionsConfig.client(var2); var2.pop(); // [regions] ...`
* `client/FrontierSettingsScreen.java` INTERFACE page: section "Arrival cards" + 1 choice + 2 sliders, inserted before the
  "Kill cam" section.
* `academy/client/TrainingHud.java`: `static boolean showing()` (home card / fade up); `academy/client/AcademyClient.java`:
  `public static boolean cinematicShowing()` forwarding to it (arrival cards wait for the academy return card).

## IN-GAME TEST SCRIPT
Use a fresh creative world (Frontier Hunts world type or vanilla), then a dedicated server with 2 clients for steps 12–13.
1. Join. Reserve mode on (`/gamerule frontierHunting true` in an existing world, then rejoin): the usual "FRONTIER HUNTS /
   <region>" card plays **and** two new lines appear under it: the biome name and e.g. "Early Fall — Elk · Whitetail".
   They fade with it. No second card afterwards. `/arrivalcard status` → `lastCard=…s ago`.
2. `/gamerule frontierHunting false`, rejoin: ~2.5 s after the world appears the arrival card shows (bars grow in, spaced
   region headline with hairlines, gold biome title, subtitle), ~5 s total. Hotbar/hearts stay visible over the bars.
3. `/arrivalcard show`, `biome`, `compact` — check each style; F1 hides it; in singleplayer Esc pauses and freezes it.
4. Walk (not sprint-fly) across a biome border (`/locate biome minecraft:taiga` etc.): card ~3 s after crossing.
   Region change (e.g. forest → plains) = full bars; biome inside the same region (forest → birch forest) = thinner bars.
5. Stand exactly on a border and jiggle back and forth for 30 s: **no** card. Step 3 blocks in and stand still: card after ~10 s.
6. Walk back into the previous biome within 5 min: no card (same region) — `status` shows it as current. Cross into a different
   region and back several times within 3 min: one card per region at most.
7. Cross two biomes quickly: the second card waits until 12 s after the first, then shows (if you are still there).
8. Open the inventory right as you cross: no card while it is open; it appears when you close it (within 15 s).
9. Let a zombie hit you / hit an animal while crossing: no card for 10 s; a card that is up fades out quickly when you are hit.
   Draw a bow or raise the rifle scope during a card: it fades out at once.
10. Kill a deer with a lethal shot near a border (kill cam): nothing during the kill cam.
11. Ride an ATV/horse fast or fly an elytra across biomes: compact card at the top. Teleport `/tp ~2000 ~ ~` (or `/spreadplayers`):
    card for the new biome after ~3 s.
12. Settings → Interface → Arrival cards: Off (nothing, join lines gone), Regions only (biome changes silent), length 3 s/8 s,
    Repeat after 1 min (`/arrivalcard reset` to clear memory). Reset page restores Regions + biomes / 5 s / 5 min.
13. Dedicated server, 2 clients: both see their own cards for their own crossings; region names and game lines appear (they come
    from synced registry/tags). Modded biomes (Terralith/BOP if installed) show their proper names.
14. Nether portal: card "The Nether / Nether Wastes" after arrival. Back: overworld card (or reserve join card + lines).
15. Ranger Academy: enter the training grounds → no cards there; return home → the academy home card first, the arrival/join
    card after it (never on top).
16. With Iris shaders (Photon etc.), Distant Horizons and Xaero's minimap: the card renders the same; check `latest.log` for
    errors mentioning `regions` / `ArrivalCards`.

## Known limits / notes
* No sound cue (the join card has none either) — could add a soft swell later.
* Region names come from the region datapack (English strings in data, like the join card).
* Game lines come from habitat tags, so a server that removes a species via datapack tags also removes it here; species
  disabled only by config still show if their tag lists the biome.
* Join augmentation relies on the private field name `FrontierClient.arrivalTicks`; if that class is ever rewritten the lines
  just stop appearing (`/arrivalcard status` says "join-card hook unavailable") and the arrival card itself still works.
