# gui — Frontier settings screen: Sound page (volume mixer) + quality pass

Branch `gui`. User request: "there should be a volume amount select in the Frontier Hunts GUI to change sounds of
everything the normal game can't, like your new sound effects, wind gusts etc; also make sure that GUI is well optimized
and things are in the right spot".

## 1. Sound page / Frontier sound mixer

Nine sliders (0-100 %, step 5; a short preview plays on release or arrow key, at the volume of the slider being tested,
cut after 3.5 s; previous preview is stopped first):

| Slider | Group of `frontierhunts:` sound events (170 today, every one mapped) | Preview |
|---|---|---|
| Frontier master | multiplies every group below | bird chirp |
| Wildlife vocalizations (12) | deer_* (bleat, blow, grunt, snort, stomp, wheeze), elk_bark/bugle/mew, moose_call/grunt/threat | deer_snort |
| Hunting calls (7) | antler_rattle, bleat_call, deer_call, grunt_tube, predator_call, horse_whistle, wind_puff | grunt_tube |
| Firearms & bows (90) | every gun's shot/_far/_suppressed/open/close/load/dry/draw, rifle_* bolt + mags, casings, bow_release, arrow_impact, optic.zoom_* | rifle_shot |
| Weather & wind (8) | weather_* loops and gusts (blizzard, rain, dust, gale, gust howl/rush), amb_wind_high, amb_wind_trees | weather_gust_rush |
| Ambient nature (29) | amb_* birds/insects/owls/coyotes/frogs/creek, cascade*, falls_*, fish_splash, wildlife_flush, branch_snap/crackle, brush_rustle | amb_song_chickadee |
| Vehicles (16) | atv_* (engine layers, start/stop/crank/sputter/stall/whine), fuel_pour, jerry_can_fill, rig_attach, glider_*, canopy_open, wingsuit_flutter | atv_stop |
| Kill cam & cinematic (8) | killcam.* | killcam.heartbeat |
| UI & notifications | vanilla events the mod plays as UI sounds (forUI/MASTER source): note_block.chime (low fuel), note_block.bit (glider vario), armor.equip_leather (field-gear tab) | note_block.chime |

Sound events added later by other features are grouped by name rules (`FrontierSoundCategory.guess`, e.g. `hound_bay` ->
Wildlife, `weather_thunder_far` -> Weather, `crossbow_shot` -> Firearms, `ui.*` -> UI); anything unmatched still follows
the master slider. `tools/gui/run.sh` fails if any shipped event has no group.

How it works: `client/mixin/FrontierSoundEngineMixin` (inject-only, no redirects) multiplies the final volume Minecraft
computes for a sound (after its own clamp) by master x group:
* when a sound starts (`SoundEngine.play` -> `calculateVolume(float, SoundSource)`; the instance used is the one after
  NeoForge's PlaySoundEvent, so the kill cam's ducking wrapper is covered too),
* on every tick of every tickable sound (`calculateVolume(SoundInstance)`): weather beds (WeatherAudio.Bed), ATV engine
  layers (AtvSound), waterfall voices (AlpineWaterfallClient.Voice, CascadeAmbience.Voice), ambience beds
  (AlpineAmbience.Bed), glider wind / wingsuit flutter — all of them follow a slider live with no change to their classes,
* on demand for everything already playing (`FrontierSoundVolumes.frontierhunts$refreshVolumes`, added to SoundManager by
  `FrontierSoundManagerMixin`) — called on every slider change, so non-tickable loops follow too. Volumes are set on the
  channels directly (a loop at 0 % keeps running silently and comes back when raised).
No SoundInstance is wrapped or replaced, so code that keeps its instances (stop/isActive) is unaffected. Hot path: a
namespace compare + one ConcurrentHashMap lookup, no allocation.

Config (client, `[sound]` in frontierhunts-client.toml): `masterVolume`, `wildlifeVolume`, `callsVolume`,
`firearmsVolume`, `weatherVolume`, `ambientVolume`, `vehiclesVolume`, `killcamVolume`, `uiVolume` (0-100, default 100).
The old hidden `presentation.ambientVolume` (0-200) is migrated into the Ambient nature slider the first time the screen
opens (old value x slider, capped at 100) and reset to 100, so nothing is scaled twice. The Sound page also holds the
"Wilderness ambience" (ambientSounds) and "Storm sounds" (weatherSounds) on/off toggles.

## 2. Settings screen quality pass

Pages (sidebar; compact "< Page >" selector in the header when the window is too narrow/short):
1. **Graphics preset** — Classic / Balanced / Ultra cards, what the hovered preset does, then "Texture & shader packs per
   preset": Iris installed / shader pack active status, one info line, and the six per-preset pack pickers
   (old "Packs per Preset" + "Shader Packs" pages merged here).
2. **Trees & world** — World look (RELOAD, textures); Meadow grass: style, height, width, thickness (RELOAD, chunks);
   New terrain: forest floor density, hunting forests, forests outside reserves, forest density, ranger camps (RESTART).
3. **Wildlife** — animal style, realistic detail, small wildlife.
4. **Atmosphere & weather** — breath vapor, mountain spindrift; Storms: seasonal weather (WORLD), storm frequency
   (WORLD), storm effects, storm screen layer.
5. **Seasons** — seasons (WORLD), seasonal snow (WORLD), falling leaves.
6. **Effects** — effects quality, waterfall detail; ATV grime: screen splatter, wheel spray.
7. **Sound** — the mixer above + ambience/storm sound toggles.
8. **Interface & HUD** — wilderness HUD, hunt cinematics, reduced motion; Kill cam: mode, X-ray, length.
9. **Gameplay aids** — field assistance (realism), hunting pressure, starter journal (all WORLD).
10. **Performance** — distant trees, distant animal animation; Diagnostics: hitch logger, hitch threshold.

* Every option: label, one-line description, tooltip = one or two sentences + a performance line ("no cost / light /
  moderate / heavy - costs frames / rises with each step") + a reload/restart line where it applies. Badges: RELOAD
  (chunk/texture reload, amber), RESTART, WORLD (server-config option, editable because you host this world), SERVER
  (read-only: set by the server). World options are only editable when an integrated server is running.
* Batched apply: options take effect in memory immediately; the client/worldgen/server config files are written, the
  grass chunk rebuild (`AlpineGrassModels.apply`), preset pack switching (`PresetPacks.apply`) and the realistic-world
  pack sync run ONCE when the screen closes (Done, Escape, or anything replacing the screen — `removed()`). Previously
  every click saved the config and triggered a chunk/texture reload. The footer shows "ON CLOSE: Chunks rebuild /
  Textures reload" while such a change is pending (compared with the state when the screen opened, so changing a value
  back clears it).
* Preset marking: every change recomputes `FrontierGraphics.matching()` -> the preset or CUSTOM; preset cards use the new
  `FrontierGraphics.apply(preset, false)` (no save/reload) and queue that preset's packs for close.
* Performance: only the open page is built (on open/page switch/resize); every string drawn is fitted and measured once
  (`Txt` cache, re-fitted only when text or width changes); slider value text cached per value; footer (preset, GPU cost,
  pending notice) recomputed only when a setting changes; preset card GPU cost computed once; scrolling moves widgets
  without re-measuring; no Component/String/lambda allocation per frame in Frontier's own drawing (vanilla widget bookkeeping and
  FrontierUi.rect's pose push remain).
* Input: rows/cards outside the scrolled viewport can't be clicked or hovered (clipped hit-tests); mouse wheel, PageUp/
  PageDown scroll; keyboard Tab focus scrolls the focused row into view; arrows/Enter/Space work on every control;
  Reset page (all pages except Graphics preset) resets the page's editable options; Done.
* Layout: `client/settings/SettingsLayout` (pure Java) holds all geometry; `tools/gui/LayoutDump` checks it at 854x480
  and 1920x1080 (and 1280x720, 1366x768, 2560x1440, 640x480, 800x600, 1024x768) at GUI scale 1-4 (including scales
  Minecraft would clamp, as a stress test) plus odd sizes down to 213x120: panel/header/footer/tabs/buttons inside the
  window, no overlaps, at least one full row visible, every page scrolls to its last item, labels never run into
  controls, slider knob never touches its value. Rows stack (control under the label) below 250 px content width.
  Wireframes: docs/ws/gui/*.png. Run: `tools/gui/run.sh` (also checks the sound mapping).

## Files
New: `sound/FrontierSoundCategory.java`, `sound/SoundMixConfig.java`, `client/sound/FrontierSoundMixer.java`,
`client/sound/FrontierSoundVolumes.java`, `client/mixin/FrontierSoundEngineMixin.java`,
`client/mixin/FrontierSoundManagerMixin.java`, `client/settings/SettingsLayout.java`,
`patch/_merge/frontierhunts.client.mixins.json/gui.json`, `tools/gui/{run.sh,LayoutDump.java,SoundMapCheck.java,wireframe.py}`.

Shared files:
* `HuntConfig.java` — one line after the atvGrime hook:
  `var2.push("sound"); com.formaworks.frontierhunts.sound.SoundMixConfig.client(var2); var2.pop(); // [gui] ...`
* `client/FrontierGraphics.java` — `apply(preset)` now calls new `apply(preset, boolean commit)`; save only if commit.
* `client/FrontierSettingsScreen.java` — rewritten (all earlier workstream entries kept, moved to their pages). Other
  workstreams add options with one line in `buildPage()` as before: `this.toggle(label, desc, value, help)`,
  `this.choice(...)`, `this.slider(...)` (old signatures still exist; overloads with `Cost` / `Reload` add the hints).
  When merging a branch that edited the OLD screen, re-add its rows to the matching page of the new `buildPage()`.

No lang keys, textures or sounds added.

## In-game test script
1. Options > Video Settings > "Frontier Hunts..." (or the settings key). Screen opens on the last page used.
2. Sidebar shows 10 pages: Preset, Trees & world, Wildlife, Atmosphere, Seasons, Effects, Sound, Interface, Gameplay
   aids, Performance. Hover a row: tooltip with a "Performance:" line after ~0.3 s.
3. **Sound**: in a world, `/frontierweather blizzard 300` (op) and `/give @s frontierhunts:atv`, place and mount it with
   the engine running (or stand by a waterfall). Open the screen from the pause menu > Options > Video > "Frontier Hunts...". Drag "Weather & wind" to 0 % -> the blizzard bed and gusts fall silent immediately (loop keeps
   running; raise it again -> back). Same with "Vehicles" (engine) and "Ambient nature" (waterfall/creek/birds). Release
   any slider -> its preview plays once at that slider's level. "Frontier master" 0 % -> every Frontier sound silent,
   vanilla sounds (cows, footsteps, vanilla rain) unchanged. Fire a rifle with "Firearms & bows" at 30 %: clearly quieter.
4. Close and reopen: values persist (frontierhunts-client.toml `[sound]`). Restart the game: still applied.
5. **Batching**: Trees & world > Grass thickness: click several times. No chunk reload while the screen is open; footer
   shows "ON CLOSE / Chunks rebuild". Set it back to the original value -> notice disappears. Change it, press Done ->
   ONE chunk rebuild. World look -> Realistic: footer "Textures reload"; Done -> one texture reload.
6. **Presets**: change Grass height -> footer PRESET shows Custom; Graphics preset page shows "Custom settings". Click
   Balanced -> card ACTIVE, preset Balanced; set Grass height back and forth -> stays Balanced when values match.
7. **World options**: singleplayer: Seasons page rows carry WORLD badges and toggle; on a dedicated server they show
   SERVER, greyed, not clickable.
8. **Layout**: resize the window small (e.g. 854x480, GUI scale 2 and Auto) and large; nothing overlaps; at very small
   sizes the sidebar becomes a "< Page >" selector; every page scrolls to its last row with the wheel; rows partly under
   the footer can't be clicked through it.
9. Reset page on Sound resets all sliders to 100 %.
