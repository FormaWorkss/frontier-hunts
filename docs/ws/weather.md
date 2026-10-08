# Workstream `weather` — seasonal severe weather

Branch: `weather`. Real blizzards in snowy regions in winter (visibility ~5 blocks at the peak), and region-appropriate
severe weather in every season, decided by the server and rendered per player. Status: compiles, full jar builds,
offline logic harness passes. **In-game verification not yet done** (see the test script below).

## What it does

### Server: `SeasonalWeather` director (`weather/WeatherDirector.java`)
* Every weather level (sky light, no ceiling) keeps up to 16 regional storms: soft-edged discs (radius 200-620 blocks)
  that drift slowly with the shared `Wilderness.wind`, with a build-up → peak → easing envelope and slow internal
  banding (bands of heavier snow, dust walls, rain bands).
* Around each player, every 5 s, the director rolls **once per weather episode per 640-block cell** (deterministic from
  the world seed, so relogging never rerolls) whether an event happens there:

| Event | Where | Needs | Season odds (per episode/cell, before the frequency multiplier) |
|---|---|---|---|
| Blizzard | snowy region: snowy biomes / `c:is_snowy` / Frontier tundra, anything with climate temp minus altitude lapse ≤ 0.15 (≤ 0.37 in deep winter: taiga, windswept hills, high ridges); frozen peaks/glaciers all year | vanilla precipitation **is snow at that spot** | 12-50% by winterness; high snowy mountains 25-75% and 1.5× longer; off-season only on deep-cold peaks |
| Thunderstorm | anywhere it rains | vanilla thunder | summer 85%, spring 70%, fall 45%, winter 15% |
| Rain squall | anywhere it rains | vanilla rain (no thunder) | per 4-min slot: spring 28%, fall 24%, summer 18%, winter 8% |
| Dust storm | desert/badlands (full), savanna/dry steppe (45%) | no rain there | per day: summer 40%, spring 20%, fall 15%, winter 6%; +35% when a front (vanilla rain) passes |
| Fog / freezing fog | valleys, rivers, swamps, wetlands (forest 50%, open ground 30%); winter lowlands that are not snowy regions get freezing fog | forms 20500-1200 day time, burns off 3000-5500 | fall 60%, spring 40%, winter 35%, summer 15% (halved in rain) |
| Wind storm | any land except deserts | daytime | fall 35%, spring 22%, winter 15%, summer 10% |

* Precipitation events ease out (30-80 s) when the vanilla rain/thunder they need stops. Fog sits under a ceiling
  (valley floor + 10-22 blocks): climb out and you look down on it.
* Applicability is evaluated per position on both sides (`Climate`): a blizzard disc that drifts over a warm valley or a
  desert does nothing there. A player in a desert never sees a blizzard.
* Synced to each player every 2 s (storms within radius + 320 blocks, wind, blizzard visibility floor; a few dozen bytes)
  plus immediately on changes, login, respawn and dimension change. Clients only evaluate/render what applies at their
  own camera. Rolls, spawns and upkeep are O(players + storms); nothing runs per entity or per block.

### Client presentation (`weather/client/*`)
* Per-kind severity at the camera, smoothed ~2-3 s (no pops at storm edges, biome edges or doorways), then gated by
  **shelter**: 6-sample open-sky test above the camera (leaves do not count as a roof) and sky light (caves = off).
* **Vanilla fog** (`ViewportEvent.RenderFog` / `ComputeFogColor`): log-space blend from the normal far plane to each
  kind's floor — blizzard = server config (default **5 blocks**), dust 13, fog 20, freezing fog 30, thunderstorm 44,
  squall 60, wind 150 — sphere fog, near plane pulled in, applied to terrain, entities and sky. Colour blends to the
  storm air (white-grey blizzard, orange-brown dust, slate thunderstorm...), darkened by daylight and rain, lifted by
  lightning flashes. Indoors the fog relaxes (~24 blocks in a blizzard) so cabins stay usable.
* **Shader-independent layers** (Iris packs ignore vanilla fog):
  * *Veils*: large soft depth-tested billboards in a 3-30 block shell around the camera (snow / dust / fog / rain mist).
    Near things stay visible, distant terrain and mobs vanish behind many overlapping veils. Works with any pack.
  * *Screen layer* (`WeatherOverlay`, GUI layer `frontierhunts:weather_storm`, below the whole HUD, still drawn with F1):
    per-row whiteout using a ground-distance estimate from camera pitch/FOV/eye height (`1-exp(-3d/vis)`: close
    ground clear, horizon and sky washed out), strong with shaders or Distant Horizons, a light veil without; two
    tileable gust sheets scrolling with the cross-wind and locked to the view direction; frost creeping in from the
    screen edges after ~20 s outside in a blizzard.
* **Particles** (camera-relative volumes, hard budgets, spawned only under open sky, die in blocks):
  blizzard = velocity-aligned motion-blurred snow streaks gusting along the wind + close tumbling flakes + blowing
  ground snow on snow surfaces + veils; downpour = long rain streaks with ground splashes + extra splashes + mist veils
  (vanilla rain rendering untouched); dust = sand streaks + sand drift + orange veils; fog = slow fog veils kept under
  the fog ceiling (+ glinting ice crystals in freezing fog); wind storm = season-coloured tumbling leaves torn from
  nearby canopies and lifted off the forest floor, dust or snow streams on bare ground. No tree swaying.
  Budgets at full severity (Performance / Balanced / Cinematic): snow streaks ~220/520/880 (cap 1100), veils 39/72/104
  (cap 150), ground drift 46/110/187, flakes 0/70/119, rain 134/320/544, leaves 50/120/204.
* **Sound** (WEATHER channel, all original, synthesized by `tools/gen_weather_audio.py`): seamless stereo beds
  (blizzard roar with drifting howl resonances and snow hiss; downpour; gritty dust storm; gale with leaf rustle) that
  follow severity and gusts and duck indoors, plus positional gust one-shots from upwind on each gust (3 howls, 3
  rushes).
* No per-frame allocation in the fog/overlay/particle render paths (particles write quad corners directly, light is
  sampled once at spawn).

### Gameplay hooks
* API (`weather/SeasonalWeather.java`, works on both sides):
  `event(level,pos)`, `severity(level,pos)`, `severity(level,pos,kind)`, `visibility(level,pos)` (blocks),
  `beddingWeather(level,pos)` (cheap, cached per chunk for 2 s), `trackFillRate(level,pos)` (1 = normal; blizzard up
  to 9×, dust 6×, downpour 4×, gale 2.5× — for the tracking workstream), `storms(level)`, `windEast/windSouth`.
* Deer and wildlife bed down in blizzards/dust storms (>45%), thunderstorms/squalls (>60%) and violent gales (>85%) and
  stay bedded while it lasts. No damage, no freezing.

## Files
New (all mine): `src/com/formaworks/frontierhunts/weather/` — `WeatherKind`, `Storm`, `Climate`, `SeasonalWeather`
(API + client copy), `WeatherDirector` (server), `WeatherNetwork` (payload `frontierhunts:weather_sync`),
`WeatherCommands`, `WeatherConfig`, `WeatherRegistry` (4 particle types, 6 sound events);
`weather/client/` — `WeatherClient` (state, fog), `WeatherEffects` (spawning), `WeatherParticles` (particles +
providers), `WeatherOverlay` (GUI layer), `WeatherAudio`.
Assets: `patch/assets/frontierhunts/particles/weather_{streak,veil,flake,leaf}.json`,
`textures/particle/weather_*.png`, `textures/gui/weather/{gust_sheet,frost_vignette}.png(+.mcmeta)`,
`sounds/weather/*.ogg`; fragments `patch/_merge/assets/frontierhunts/sounds.json/weather.json`,
`patch/_merge/assets/frontierhunts/lang/en_us.json/weather.json` (subtitles). Generators: `tools/gen_weather_art.py`,
`tools/gen_weather_audio.py`.

Shared files (one-line hooks, marked `// [weather]`):
* `HuntConfig.java` — server builder, after the `reserve` pop:
  `var1.push("weather"); com.formaworks.frontierhunts.weather.WeatherConfig.server(var1); var1.pop(); // [weather] ...`
  client builder, after the `world` pop:
  `var2.push("weather"); com.formaworks.frontierhunts.weather.WeatherConfig.client(var2); var2.pop(); // [weather] ...`
* `hunting/Whitetail.java` — bedded case: `if (this.bedTicks > 200 || !SeasonalWeather.beddingWeather(...)) this.bedTicks--;`
  and the bed-down chance uses `restingHours() || SeasonalWeather.beddingWeather(...) ? 240 : 2400`.
* `wildlife2026/WildlifeMob.java` — idle choice: `boolean storm = SeasonalWeather.beddingWeather(...)`;
  `state(storm || random 1/5 ? REST : ...)`, `calmTicks = (storm ? 400 : 80) + ...`.

## Config
Server (`frontierhunts-server.toml`, `[weather]`): `seasonalWeather` (true), `eventFrequencyPercent` (100, 0-400),
`eventIntensityPercent` (100, 10-150), `blizzardMinimumVisibility` (5, 3-48; synced to all players).
Client (`frontierhunts-client.toml`, `[weather]`): `weatherQuality` (AUTO = follows Frontier quality |
PERFORMANCE | BALANCED | CINEMATIC), `weatherOverlay` (true), `weatherSounds` (true).
**Not in the Frontier settings screen yet** (it is not in `src/`). Suggested rows for the ambience page next to
"Mountain spindrift": a choice row for `WeatherConfig.QUALITY` ("Storm effects"), toggles for `WeatherConfig.OVERLAY`
("Storm screen layer — needed for whiteouts with shader packs") and `WeatherConfig.SOUNDS` ("Storm sounds").

## Commands
* `/frontierweather` (or `query`) — anyone: season, place type, vanilla weather, event here, phase, strength, visibility, time left.
* `/frontierweather list` — op: every event in the dimension.
* `/frontierweather <blizzard|thunderstorm|squall|duststorm|fog|freezingfog|windstorm> [seconds]` — op: force an event
  centred on you (radius ≥ 480, stationary, builds up over ≤ 20 s, default 300 s). Blizzard/squall also start vanilla
  rain, thunderstorm starts thunder. Forced events ignore climate (the reply warns when the spot is not a natural one).
* `/frontierweather clear [seconds]` — op: ease every event out over 10 s and hold natural events off (default 600 s).
  Vanilla weather is untouched (`/weather clear` for that).

## IN-GAME TEST SCRIPT (coordinator)
Setup: creative, op, a world with vanilla mountains (or the Frontier alpine world's glacial peaks). Test shaders OFF
first, then with an Iris pack (e.g. Photon/Complementary) — steps 3-6 again.
1. `/locate biome minecraft:frozen_peaks` (or `snowy_slopes` / `frontierhunts:glacial_peaks`), teleport there, stand on
   open ground at noon (`/time set noon`). `/season set winter mid`.
2. `/frontierweather` → expect "Winter · high snowy mountains … precip snow". `/weather clear`.
3. `/frontierweather blizzard 240` → over ~20 s: vanilla snow starts, sky and fog go white-grey, horizontal snow streaks
   blow along one direction and gust, low ground drift streams over snow, howling gusts arrive from upwind over a
   roaring bed. At peak `/frontierweather` says visibility about 5 blocks. Place a block/armour stand 3, 5, 8 and 15
   blocks away: 3 visible, 5 a faint silhouette, 8+ gone; mobs 10 blocks away invisible. Distant mountains must not
   show, looking up shows no sky. After ~20 s frost creeps in at the screen edges.
4. With the blizzard running: walk into a closed hut/cave → within ~2 s fog relaxes (cabin interior clear, window shows
   white), no snow particles inside, sound ducks; step out → it closes in again smoothly. F5 third person: same look.
5. `/frontierweather clear` → eases out over ~10 s, frost melts within ~10 s. No particle or sound left over.
6. Natural blizzard: `/frontierweather clear 0`, `/weather rain` and wait (or `/frontierweather list` every minute).
   In deep winter on frozen peaks roughly 3 out of 4 snow episodes turn into a blizzard; in a snowy taiga about half.
   `server.toml` `eventFrequencyPercent=400` makes it near-certain.
7. Region check: while a blizzard runs on the peak, `/tp` 500+ blocks down into plains/forest (non-snowy): no blizzard
   (maybe rain), `/frontierweather` shows no event. Desert: `/tp` into a desert → never a blizzard.
8. Other events (forced, 120 s each, each from a fitting biome): `thunderstorm` in plains (dark slate fog ~40 blocks,
   long rain streaks, ground splashes, downpour bed, vanilla thunder); `duststorm` in a desert (orange-brown haze,
   visibility ~13 blocks, sand streaks and drift, gritty hiss); `fog` in a river valley (grey fog ~20 blocks, slow fog
   veils; fly 25 blocks up → you rise out of it and see the bank below); `/season set winter mid` + `freezingfog` in a
   plains lowland (blue-white fog, glinting crystals); `/season set fall mid` + `windstorm` in an oak/birch forest
   (orange/red leaves torn off the canopy, tumbling downwind, gale bed and rush gusts; `/season set summer mid` gives
   green leaves).
9. Wildlife: during a forced blizzard near deer (`/summon` whitetail or natural), within ~1 min calm deer bed down and
   stay bedded until it eases.
10. Multiplayer (dedicated server, 2 clients): client A forces a blizzard at the peak; client B 300 blocks away in the
    same storm sees it too, client B in the desert does not. Relog B mid-storm: it comes back within 2 s.
11. Settings: `weatherQuality=PERFORMANCE` (fewer particles, no sheets without shaders) / `CINEMATIC` (denser);
    `weatherOverlay=false` (shaders ON: distant terrain shows through again → proves the overlay is what hides it);
    `blizzardMinimumVisibility=12` on the server → peak visibility ~12.
12. Performance: F3 FPS in a Cinematic blizzard with a shader pack vs the same spot after `/frontierweather clear`;
    note the difference (veils are large translucent quads: fill-rate is the main cost).

## Known limitations / notes for the coordinator
* Not tested in game yet; shader/GPU/multiplayer coverage unverified.
* Blizzards need vanilla precipitation to be **snow** at the spot. If the `seasons` workstream makes temperate winter
  snow via `getTemperature`/`coldEnoughToSnow`, taiga/high ridges get winter blizzards automatically; region detection
  reads `Biome.getModifiedClimateSettings().temperature()`, so seasons should not change that value (or blizzard
  regions widen to every biome that gets seasonal snow).
* Storms are not persisted: a server restart starts calm (rolls are deterministic, so the same episode/cell does not
  reroll during a session).
* Distant Horizons LODs ignore vanilla fog; the overlay switches to the strong profile when DH is loaded, but LOD
  terrain may still peek through at the edges.
* Birds/meadow ambience from `AlpineAmbience` keep playing under dust storms and fog (not touched here).
