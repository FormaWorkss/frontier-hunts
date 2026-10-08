# Workstream `blizzard2` - brutal blizzards, gentler frost

Branch: `blizzard2` (on top of the `weather` workstream; see `docs/ws/weather.md`). Compiles, full jar builds,
`check_jar.py --base master`: 0 errors / 0 warnings. **Not yet verified in game.**

User feedback: "when in snow biome with blizzard needs to be more stronger blizzard, and the ice effects on the screen
are just a little bit overwhelming but keep them, just tone them down a bit".

## 1. Stronger blizzard

| What | Before | After |
|---|---|---|
| Strength mapping | severity used as-is; banding (pulse 12%) and natural peaks 0.92 kept it ~0.85-0.95 | `SeasonalWeather.blizzardDrive(s) = min(1, 1.15 s)`: 0.87+ is full strength; blizzard pulse 12% -> 7% |
| Vanilla fog end at peak (floor 5) | floor x1.3 = 6.5 only at severity 1.0; in practice ~6.6-8.4 blocks | floor x1.1 = **5.5 blocks** (3 blocks hazy, 5 nearly gone); **~2.75 blocks** in a whiteout gust |
| Indoors (closed hut) | ~27 blocks | ~21 blocks (still usable) |
| Shader screen layer (strong) | cap 0.90, fade distance ~8.7 blocks | cap 0.92 (+0.05 in a whiteout), fade distance 5.5 (2.75 in a whiteout) |
| Screen layer without shaders | cap 0.20 | 0.24 (+0.22 briefly in a whiteout) |
| Gust sheets | opacity 0.26, speed x1.0 | 0.36 (x1.4 in a whiteout), speed x1.45 (x1.5 in a whiteout) |
| Darkness | air colour 0.80/0.82/0.86 | 0.74/0.77/0.82, up to 20% darker with strength + a dim layer (0.16 alpha with shaders, 0.10 without; outdoors only) |
| Whiteout gusts | - | new: on strong gust edges in a heavy blizzard outdoors, 2-4.5 s walls of snow (vis ~2 blocks), max one per 10-25 s, deep howl + rush from upwind |
| Gusts | shared rhythm | blizzard adds faster/deeper swings (x1.35 amplitude) |
| Snow streaks | 0.3-1.2 blocks/tick, 220/520/880 (Perf/Bal/Cine), cap 1100 | 1.3-1.9 b/t at the peak (+25% in whiteouts), **269/640/1088**, cap 1300, spawned upwind so the volume is centred on the camera |
| Near-face streaks | - | new role `NEAR`: large long streaks spawned 2.5-7 blocks upwind blowing past/at the camera, **50/120/204**, cap 260, outdoors only |
| Ground drift | alpha 0.30, 46/110/187, lift <=0.6 | alpha 0.42, 25% wider, **63/150/255**, cap 300, gusts lift it up to 1.4 blocks |
| Flakes | 0/70/119 | 0/90/153 (cap 180) |
| Veils | 39/72/104, 3.5-26 blocks, alpha 0.33 | **52/96/139**, cap 170, 2.6-22 blocks, alpha 0.40 (+25% in whiteouts) |
| Sound | roar bed 0.72-1.0 x severity | 0.85-1.0 at the peak + the gale bed layered on top (0.2-1.0 with gusts/whiteouts); howls every 1.5-4.5 s (was 2.5-7) at 0.55-1.0 volume; whiteout = deep howl + rush |
| Natural frequency (winter) | snowy 12-50%, high snowy mountains 25-75%, deep-cold min 25/40% | **18-65%**, **32-87%**, min 30/45% |
| Natural duration | ramp 45-75 s, hold 3-7.5 min (x1.5 high) | ramp **25-45 s**, hold 4-9 min x1.6 on high snow x up to 1.3 in deep winter; radius 400-640 |
| Forced `/frontierweather blizzard` | build-up <= 20 s | build-up <= 12 s, full strength (drive 1.0) for the whole hold |

All effects stay shader-safe (vanilla fog + depth-tested veils + the screen layer). Fill-rate cost: ~33% more veils and
the dim layer is one full-screen quad; particle counts are the budgets above.

## 2. Frost toned down
* Max opacity **0.90 -> 0.50** (frost target 0.85 -> 0.50 x strength).
* Build-up time constant **18 s -> 35 s**; melt indoors 8 s -> 6 s (still fades out under a roof / in caves).
* New texture (`tools/gen_weather_art.py <root> frost`): shorter ferns, band ~14% of the edge (was ~20%), corners 26%
  (was 32%), softer glow/haze, texture alpha max 0.85 (was 1.0), mean coverage 0.077 (was 0.168), and a hard clear
  ellipse over the middle 62% of the screen (crosshair/scope never frosted).
* It creeps in: the frost quad starts 14% larger than the screen and settles to the edges as the frost builds.

## Files (all in the weather workstream's own packages; no shared files touched)
`weather/SeasonalWeather.java` (blizzardDrive, visibilityFor), `weather/Storm.java` (pulse), `weather/WeatherDirector.java`
(chances, durations, forced ramp), `weather/client/WeatherClient.java` (drive, whiteout gusts, gust swings, colour, frost,
fog), `weather/client/WeatherOverlay.java` (profile, dim, sheets, frost), `weather/client/WeatherEffects.java`
(budgets, near streaks, drift, veils), `weather/client/WeatherParticles.java` (NEAR role), `weather/client/WeatherAudio.java`,
`tools/gen_weather_art.py` + `textures/gui/weather/frost_vignette.png`.

## IN-GAME TEST SCRIPT
1. `/locate biome minecraft:frozen_peaks` (or snowy_slopes / frontierhunts:glacial_peaks), tp, open ground,
   `/time set noon`, `/season set winter mid`, `/weather clear`.
2. `/frontierweather blizzard 240`: full strength within ~12 s. `/frontierweather` -> "visibility about 5 blocks".
   Blocks at 2/3/5/8 blocks: 2 clear, 3 hazy, 5 a faint ghost, 8 gone. Sky gone, light noticeably greyer/darker than
   before. Dense horizontal snow racing past, big streaks blowing at your face when you look upwind, heavy ground drift.
3. Wait ~1 min outdoors: every 10-25 s a whiteout gust (deep howl + rush) drops visibility to ~2 blocks for 2-4 s.
   Roar is clearly louder, gale swells with gusts.
4. Frost: after ~20-30 s thin rime appears at the edges only, max ~half as strong as before, centre always clear.
   Walk into a hut: fog relaxes to ~20 blocks, no snow indoors, frost melts within ~6 s, no whiteout gusts indoors.
5. Repeat 2-4 with a shader pack (Photon/Complementary): distant terrain hidden, dim + gust sheets + veils visible.
6. F3 FPS: Balanced and Cinematic blizzard vs `/frontierweather clear` - expect a moderate drop only.
7. Natural: `/frontierweather clear 0`, `/weather rain`; on frozen peaks in mid winter most snow episodes become
   blizzards (~87%), snowy taiga ~65%. `/frontierweather list` shows longer holds.
