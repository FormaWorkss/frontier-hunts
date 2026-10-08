# Workstream `shelter` — storms only in storms, shelter keeps them out and keeps you warm

Branch: `shelter` (clone `/home/claude/work/shelter`). `tools/compile.sh` → exit=0; `python3 tools/build.py shelter-test .`
builds; `tools/check_jar.py <jar> . --base master` → 0 errors, 1 warning (the changed `AlpineAmbience$Bed`, a
Dist.CLIENT-only sound class as in the base jar). Offline harness `tools/shelter/harness/run.sh` → ALL PASS
(`tools/survival/harness/run.sh` still ALL PASS). **Not run in game** (no client here): everything below is awaiting
in-game verification.

User requests: "the storm weather effects only should happen in storms or certain events not normally and when in tents
and structures even in harsh conditions it should go away" · "tents and structures should prevent the cold and keep u
warm same with fires ect".

## 1. What triggered storm effects in normal conditions (audit) and the fixes
| Trigger found | Fix |
|---|---|
| **Survival frost vignette** (vanilla powder-snow texture, up to 0.85 alpha) whenever body heat < −40, i.e. on any cold clear night, also indoors | Only in a blizzard at the camera, or as a last warning when freezing damage is near (heat ≤ −82); max 0.45; × outdoors (fades out in 1-2 s in shelter); frame-rate independent easing (was 0.03 per frame) |
| **Freezing fog** (calm winter morning event) put the weather frost on the screen (0.18 × severity) | Weather frost now comes from blizzards only |
| **Natural blizzards on 65 % (snowy) / 87 % (high snowy mountains) of every winter snowfall**, so a quiet snowy day was almost always a whiteout | Blizzard is a storm: when vanilla weather turns to **thunder** 45-85 % / 60-95 % (deep cold ≥ 70 %); quiet snowfall only 6-22 % / 12-35 % (deep cold ≥ 12/20 %) |
| **Fair-weather wind storm** (daytime gale, no rain needed) drew gust sheets and a horizon haze (cap 0.12 with shaders) | No gust sheets, haze cap 0.05 / 0.015 (leaves, ground drift and gale sound stay: it is an event) |
| **Tents counted as outdoors**: the old 5-column open-sky test starts one block above the eye; in a tent the canvas is at eye level → full blizzard, frost, snow and roar inside the tent | Shared shelter detector (below) recognises tents/blinds |
| Indoors the storm fog only relaxed to 45 %, the storm bed kept 28 % volume | Fog 30 % indoors; open storm sound fades to a 6 % leak (20 % in a tent) and a muffled layer takes over |
| `AlpineAmbience` high-altitude wind / tree wind beds at full volume in cabins with windows and in tents (sky-light gated only) | × (1 − 0.8 shelter); creek/birds × (1 − 0.55 shelter) |
Not storm effects, left as is: mountain spindrift (mild flurries above y 360 under open sky), vanilla rain/snow
rendering (stops under tents and roofs by itself), the survival shiver tremble (body state; eases at once while warming
in shelter, see 3).

## 2. One shared shelter detector (`shelter/ShelterScan` pure + `shelter/Shelter` live adapter, common code)
Evaluated at the **camera** on the client (`shelter/client/ShelterClient`, 4×/s, eased with τ ≈ 0.7 s → 1-2 s fades) and
at the **player's eye** on the server (`Thermal.shelter`, once a second). Same block classification on both sides
(cached per BlockState): tents (`CompactTent`, `CampingTent`) and blinds (`HubGroundBlind`, `TowerBlind`) by class and
OPEN flag; open doors/trapdoors/gates = openings; leaves = canopy only; anything with a collision body ≥ 0.45 high and
more than a post = solid (walls, glass, panes, fences, slabs, furniture); carpets, single snow layers, torches, lanterns not.
* roof: 9 columns (eye ×2, axis at 2, corners at (2,2) ×0.5) up to 20 blocks; MOTION_BLOCKING height map shortcut (open
  sky = no block reads); a ceiling beyond 20 counts 0.75 when sky light < 12 (caverns).
* walls: 16 rays (8 directions × eye and chest height) up to 6 blocks; open tent flap 0.85, open blind windows 0.9, leaves 0.35.
* fabric: eye/feet inside a compact tent (1, flap open 0.72) or a blind cabin (0.95/0.8; a tower blind's lone ladder 0.3);
  walk-in camping tent recognised by its canvas roof + walls; sleeping in a tent (or a bed/bedroll in one) = 1.
* enclosure = max(roof × (0.2 + 0.8 walls^1.5), fabric, sealed-by-sky-light); wind block, precipitation block derived.

Harness results (synthetic grids): open field 0; cabin door closed 1.00; door open (centre) 0.85, by the door 0.64,
in the doorway 0.45; 2 blocks outside 0.10; lodge with one open window 0.93; compact tent closed 1.00 / flap open 0.72;
camping tent closed 1.00 / open 0.83; cave deep 1.00, near the mouth 0.72; under a tree 0 (precip 0.35); rock overhang
0.29 (precip 0.75); roofless walled pen 0 (wind 0.50); ground blind 0.95. Worst case 133 block reads per scan; 1-2 µs
of logic.

## 3. Storm presentation in shelter (client)
`WeatherClient.exposure` = 1 − enclosure (every tick). Fades over 1-2 s, partial in doorways:
screen layer (whiteout, dim, gust sheets, frost) × open; weather frost melts; fog gate 0.3 + 0.7·open; whiteout gusts
outdoors only; particle budgets × (0.25 + 0.75 open) (flakes/sparkles/splashes × open); **storm particles closer than
3.5 blocks fade out at render time, 3.5-7 partly** (canvas does not stop particles) while the storm outside a window or
the door keeps blowing; sound: open beds/gusts fade to a leak and **muffled beds + muffled gusts** (new, low-passed,
narrower stereo versions of the mod's own storm audio, `tools/shelter/gen_muffled_audio.py`; seamless loops by circular
FFT filtering; spectrum preview `tools/shelter/preview/muffled_spectra.png`) crossfade in (louder in a tent than a cabin).
Cost: one smoothstep per storm particle per frame inside shelter; the detector runs 4×/s off the render path.

## 4. Warmth (server, `Thermal` + pure maths in `SurvivalMath`)
* `shelterAir`: caves settle toward 11 °C (as before) + roof 2 + enclosure 6 + **tent 9** °C while it is below 18 °C.
* `heatedAir`: a fire in an enclosed space heats the room toward 20 °C (90 % with a campfire/stove a few blocks away;
  the first 3° of "fire" — torches, lanterns — do not count); `radiant` = fire × (1 − 0.4 enclosure).
* Fire scan 13×5×13 (was 9×5×9), fade-out 5.5 blocks in the open → 9 in a closed room; a solid opaque wall between the fire
  and your head passes only 30 %. New sources: soul torch/lantern 1, cabin lantern / lit lodge table 1.5.
* Driven snow/rain cold × (1 − max(wind, precip block)); wind chill × (1 − wind block); no wetting from rain under cover.
* Sleeping: bed 10 / hide bedroll 18, **+4 in a tent**. Body heat: in shelter heat loss up to 50 % slower and rewarming up
  to 1.8× faster. Shelter + fires re-scanned every second (was 3 s).
* Harness (Hardcore/Balanced/Light identical where comfortable), −32 °C ridge blizzard: open −61 °C felt → freezing in
  < 10 min; closed tent −17; tent + campfire at the door +15; cabin + campfire/stove +24 → from −95 shivering stops in
  32-39 s, comfortable (0) in ~1.1-1.7 min, bare-handed, on every difficulty. Sleeping in a closed tent on a hide
  bedroll in buckskin is comfortable even there. −17 °C plains blizzard: tent in buckskin is comfortable.
* HUD: roof glyph/flags as before (F_SHELTER = precip block ≥ 0.6, F_INDOORS = enclosure ≥ 0.6), new F_TENT; the journal
  chip reads "In a tent" / "Indoors" / "Sheltered"; journal heat descriptions, shiver/freezing/cold-night messages and the
  cold Field-School tip now say tent/cabin + fire.

## Commands
`/shelter` (anyone, read-only): what the detector sees at your eye (kind, enclosure %, wind/snow kept off, roof, walls,
canopy, sky light) and the warmth pieces (outside air, shelter air, fires, felt, body heat).

## Files
New: `src/com/formaworks/frontierhunts/shelter/{ShelterScan,Shelter,ShelterSounds,ShelterCommands}.java`,
`shelter/client/ShelterClient.java`; `patch/assets/frontierhunts/sounds/weather/muffled_{blizzard,wind,dust,rain}_loop.ogg`,
`muffled_gust_{0,1,2}.ogg`; fragments `patch/_merge/assets/frontierhunts/sounds.json/shelter.json`,
`patch/_merge/assets/frontierhunts/lang/en_us.json/zz_shelter.json` (**named `zz_` on purpose**: fragments merge in
file-name order and it overrides 4 `survival.json` and 4 `integ_survival_journal.json` strings);
`tools/shelter/{gen_muffled_audio.py, harness/ShelterHarness.java, harness/run.sh, preview/muffled_spectra.png}`.
Changed (all lines marked `// [shelter]`):
* weather: `WeatherClient` (exposure from ShelterClient, `nearShelter`, fog gate, blizzard-only frost, `blizzardAtCamera()`,
  old open-sky exposure removed), `WeatherOverlay` (gate by shelter, wind-storm caps/sheets), `WeatherEffects` (budgets),
  `WeatherParticles` (`shelterFade` in Streak/Flake/Leaf render), `WeatherAudio` (muffled crossfade), `WeatherDirector`
  (blizzard odds).
* survival: `Thermal` (Shelter record now `(roof, enclosure, windBlock, precipBlock, tent, sky)` from the shared detector;
  `shelter(Player)`; `heat(level, feet, head, enclosure)` + old 2-arg overload), `SurvivalMath` (shelterAir, heatedAir,
  radiant, sleepWarmth, stepHeat 8-arg overload; 7-arg kept), `SurvivalService` (wiring, 1 s scans, F_TENT),
  `SurvivalNetwork` (**flags byte → var-int**, `F_TENT = 256`), `client/SurvivalHud` (frost), `client/SurvivalClient`
  (tremble eases while warming in shelter), `client/SurvivalJournalView` (chip text).
* `landscape/AlpineAmbience.java` copied from dec62g8 (first commit untouched) + shelter muffling of its beds.
No HuntConfig / settings / mixin / registration-class changes (sounds self-register in `ShelterSounds`).

## IN-GAME TEST SCRIPT
Setup: creative + op (survival mode for steps 6-9), Survival = Hardcore (Settings → Gameplay aids), `/gamerule doDaylightCycle false`,
`/time set 18000`, `/season set winter mid`. Snowy biome: `/locate biome minecraft:snowy_plains` (or frozen peaks), tp.
Gear: `/give @s frontierhunts:backpacker_dome_tent`, `frontierhunts:hunters_canvas_tent`, `minecraft:campfire 2`, some planks/glass/door.
1. **Normal days stay calm**: `/frontierweather clear 0`, `/weather clear` → no frost, no streaks, no whiteout, no storm
   bed. Survival mode, bare, wait until shivering (`/survival heat -60` to skip): HUD shows shivering, view trembles, but
   **no screen frost**. `/weather rain` (quiet snowfall): vanilla snow only; `/frontierweather` normally reports no event
   (quiet snow becomes a blizzard only 6-22 % of the time). `/weather thunder` in deep winter: a blizzard usually rolls
   within ~5-10 s (`/frontierweather list`).
2. **Forced blizzard**: `/frontierweather blizzard 600` → full whiteout, streaks, roar, gust howls, frost after ~20 s;
   now with body heat ≤ −50 (`/survival heat -70`) the survival frost also shows.
3. **Tent**: pitch the dome tent, walk in, close the door (use the door). Within 1-2 s: whiteout, dim, gust sheets, frost
   and survival frost fade away, no snow blowing past your face, fog relaxes, the roar turns into a muffled low roar
   with dull thumps on gusts. `/shelter` → "in a tent - enclosure 100%". Open the flap: partial (~70 %) storm returns
   gently. Step out: everything fades back in over 1-2 s. Repeat in the hunters canvas tent; sleep in it (night): no storm on
   screen, muffled sound.
4. **Cabin**: build a 5×5 cabin (walls 3 high, roof, glass window, door). Inside with the door closed: as in 3, the
   storm still visible blowing outside the window at a distance; door open: mostly sheltered (~85 %), in the doorway
   ~45 % (partial fog/sound). Also a Frontier Structures cabin, a cave (dig 6 blocks into a hill), under a tree (no
   shelter), under an overhang (partial ~30 %). `/shelter` in each.
5. **Shaders**: repeat 3-4 with Iris + Photon/Complementary: the strong whiteout layer must disappear inside.
6. **Warmth (Hardcore, survival mode, bare)**: in the blizzard outside: felt about −45…−60 °C, body heat slides to freezing.
   `/survival heat -95`, walk into the closed tent: felt jumps to about −5…−20 within 1-2 s (HUD roof glyph, ▲/▼),
   cooling slows. Place a campfire just outside the tent door: felt ≥ +15 °C, needle rises immediately, shivering stops
   in under a minute, comfortable within ~2 min. Journal → Survival page: chip "In a tent", "By a fire".
7. Cabin + campfire or a lodge stove inside, door closed: felt ≈ +20…25 °C, `/survival heat -95` → shivering stops in
   ~30 s. A campfire **behind** a cabin wall (outside) adds almost nothing indoors (wall blocks radiant heat).
8. Walk-in camping tent (`/give @s frontierhunts:woodland_camp_tent`, pitch it): `/shelter` → "in a tent". Place a hide
   bedroll inside (`/give @s frontierhunts:hide_bedroll`), wear the buckskin kit (`buckskin_coat`, `buckskin_leggings`,
   `fur_hat`, `fur_mukluks`), sleep in the blizzard at night: no "slept cold" message.
9. Light / Balanced: same as 6-7 (difficulty only changes how fast you cool outside).
10. Multiplayer (dedicated + 2 clients): A in a tent, B outside in the same blizzard: A sees/hears it muffled, B full;
    each HUD matches its own shelter. Relog inside a tent: no fade-in pop (shelter applied at once on join).
11. Perf: F3 / frame time in a Cinematic blizzard outside vs inside a cabin: inside should be cheaper (fewer particles).

## Known limits
* Not tested in game; shader/GPU/multiplayer unverified. No OpenAL filter: "muffled" = crossfade to pre-filtered audio.
* Vehicles: the mod's ride vehicles have no roof, so no vehicle shelter. Modded blocks with context-dependent collision
  (e.g. powder snow) classify as open.
* The FX side uses the camera position: in third person with the camera outside a tent you see the storm.
