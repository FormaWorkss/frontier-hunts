# Workstream `atvgrime`: ATV mud, snow and water grime

Driving the ATV through mud gets it muddy, from the tyres up. Fresh mud is dark and wet, dries over a few minutes to a
light crust, and the crust very slowly flakes off. Snow packs into the treads and powders the top surfaces, then melts.
Fording water about 1 block deep (or less, with less effect) rinses the mud off from the bottom up and clears the snow.
Rain keeps the mud wet and rinses a little off over time. Wheels throw mud clods and mist, snow powder and water spray.
In first person the driver gets mud, snow and water splashed onto the view.

## What was built

| Part | Where |
| --- | --- |
| Grime state + server simulation, wheel sampling, persistence, sync throttling | `landscape/ride/grime/AtvGrime.java` |
| Sync payload (5 bytes + entity id), particle types, `/atvgrime` command, start-tracking sync | `landscape/ride/grime/AtvGrimeNet.java` |
| Client config (`screenSplatter`, `wheelSpray`) | `landscape/ride/grime/AtvGrimeConfig.java` |
| Client: snapshot receive, wet/dry smoothing, wheel spray emission, driver feed | `landscape/ride/grime/client/AtvGrimeClient.java` |
| Spray particles (clod / mist / drop) + providers + live budget | `landscape/ride/grime/client/AtvGrimeParticles.java` |
| Body overlays for the blocky and realistic ATV | `landscape/ride/grime/client/AtvGrimeRender.java`, `AtvGrimeRenderTypes.java` |
| Driver screen splatter (HUD layer below everything) | `landscape/ride/grime/client/ScreenSplatter.java` |
| Art generator (all textures, culling table, particle json) + geometry/preview helper | `tools/gen_atvgrime_art.py`, `tools/atvgrime_geom.py` |

### State (server authoritative, saved, synced)
Five values 0..1 per ATV (`Atv.grime`): `mudLow` (tyres, wheel wells, footwells, lower body), `mudHigh` (spray on
fenders, body, racks, seat; only builds at speed), `moist` (1 wet dark, 0 dry crust), `snow`, `wet` (water film).
* Each server tick the ground under all four wheels is sampled (block at and under each contact point). Mud sources:
  mud 1.0, muddy mangrove roots 0.85, clay 0.7, soul soil 0.45, `frontierhunts:forest_loam` 0.4, `forest_duff` 0.22,
  `moss_floor` 0.12; dirt / coarse dirt / podzol / farmland / path become mud in rain (0.55, grass 0.3) and in wetland
  biomes (0.35, grass 0.12); hydrated farmland 0.35. Snow sources: snow layers (by depth), snow block, powder snow.
* Mud gain ∝ distance driven × source; source intensity also caps how dirty it gets (wet dirt never cakes it fully,
  real mud does). Spray onto the upper body only above ~0.25 blocks/tick. Nothing sticks while the wheels are > 0.3
  under water.
* Drying ~4 min (faster when warm, slower in winter, never in rain or water). Dry crust flakes off over ~30-60 min,
  faster when driving (vibration).
* Snow: packs in at any speed, sprays up at speed, settles on the ATV while it snows. Melts in ~1 min (hot biomes),
  ~2.5 min (temperate), ~4 min (cool), ~10 min (snowy biomes), slower in winter (SeasonClock), faster in rain;
  **never** melts in a snowy biome while it is snowing.
* Water (`depth` = water above the wheels): > 0.08 starts washing the low zone, > 0.45 the high zone; speed churns it
  faster. In ~1 block of water at walking pace the ATV is clean in about 10 s. Clears snow; sets `wet`.
* Rain: `wet` up to 0.55, keeps mud moist, rinses a little off the top.
* Persisted in the entity's NeoForge persistent data (`frontierhunts:atv_grime`), written at most every 2 s when it
  changed. Synced to players in view when a quantised value changes (max every 10 ticks per ATV), plus a 10 s
  heartbeat while dirty, plus once on start-tracking. ~8 bytes per packet.

### Rendering (all shaders / Iris)
The ATV is redrawn with its own geometry per grime layer, using vanilla entity shaders (so shader packs treat them
like armour/wool layers): mud and snow are **cutout** masks (alpha-tested, no blending), the wet film is entity
translucent. Each layer is pulled toward the camera by a view-space scale (vanilla armour-layering trick, one step
per layer) so nothing z-fights and the order holds if a renderer reorders batches.
* Masks are pre-baked levels in the vehicle's own UV layout: mud 5 levels, snow 3 levels. They come from a 3D grime
  field evaluated at each texel's model-space position (so splats continue across UV seams): tyres and wheel wells
  first, a bottom-up gradient on the lower body, radial spray streaks thrown off each tyre, ~1100 discrete splats with
  satellite droplets arriving at different levels, snow on up-facing surfaces and packed in treads. Coverage per level
  is calibrated (low zone 22/42/62/80/94 %, high zone 6/16/29/43/58 %).
* Realistic ATV (Ultra / realistic world): 1024² body masks + 256² wheel masks; per-triangle zone and
  first-visible-level table (`models/entity/atv_real_grime.bin`) so only triangles with mud at the current level are
  re-emitted (light mud = a few hundred extra triangles). Low-zone triangles use the `mudLow` level, high-zone ones
  the `mudHigh` level, so water visibly washes from the bottom up.
* Blocky ATV: 128² masks per zone (low / high) in the atv.png layout, Minecraft-style flat tones.
* Wet mud vs dry crust: the masks are painted as dry crust; vertex colour multiplies a dark wet-mud tint that fades as
  `moist` drops (smooth on the client).
* Wet look: tileable translucent film + beads + run-off trails, alpha = wetness (skipped at Effects quality PERFORMANCE).

### Effects (client only, cosmetic)
* Wheel spray: mud clods (cutout chunks with gravity, block collision, spin; settle and crumble) + brown mist; snow
  powder puffs + white chunks; water drops (gone on impact with a vanilla splash) + spray mist; a big splash when
  ploughing into water fast; a wet ATV drips from its fenders. Emitted from the trailing edge of each tyre, follows
  reversing. Budget: per-kind live caps scaled by Effects quality (PERFORMANCE 0.4x, CINEMATIC 1.7x), Minecraft's
  Particles setting (decreased 0.5x, minimal 0.15x) and distance (½ beyond 16 blocks, ¼ beyond 32, none beyond 56).
* Screen splatter (driver, first person only): fixed 64-slot pool, one draw call. Mud splats / drips from the front
  wheels at speed (reversing: rear), start dark and wet, sag, dry to a light crust and fade over ~10-20 s; snow
  smears / clumps fade in 3-6 s (some melt into a droplet); water beads and runs stream down; splashing water rinses
  mud splats off. Splats keep aging (4x) when you get off or switch to third person; nothing is drawn then.
  Splat count: PERFORMANCE 22, BALANCED 40, CINEMATIC 64.

## Shared files touched (hook lines, all marked `// [atvgrime]`)
* `src/.../landscape/ride/Atv.java`
  * field: `public final com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime grime = new ...AtvGrime(); // [atvgrime]`
  * last line of `tick()`: `com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime.tick(this); // [atvgrime]`
* `src/.../landscape/ride/client/AtvRenderer.java` — **new in src** (first commit on the branch imports the unchanged
  dec62g8 decompile, so a parallel workstream importing the same file merges cleanly), then two hook lines:
  * after `RealisticAtv.render(...)`: `...grime.client.AtvGrimeRender.realistic(var1, var4, var5, var6, var1.steerShown, <same wheel spin>);`
  * after the blocky `this.model.renderToBuffer(... TEXTURE ...)`: `...grime.client.AtvGrimeRender.blocky(var1, this.model, var4, var5, var6);`
* `src/.../HuntConfig.java` (client builder, after the weather line):
  `var2.push("atvGrime"); ...grime.AtvGrimeConfig.client(var2); var2.pop(); // [atvgrime]`
* `src/.../client/FrontierSettingsScreen.java` (ATMOSPHERE section, after "Storm sounds"): two `this.toggle(...)` rows
  "ATV screen splatter" and "ATV wheel spray".

No lang keys, sounds, tags or mixins. New registrations: particle types `frontierhunts:atv_clod`, `atv_mist`,
`atv_drop`; payload `frontierhunts:atv_grime` (optional channel); GUI layer `frontierhunts:atv_splatter`.

## Config (client, `[atvGrime]` in the frontierhunts client config)
* `screenSplatter` (true) — driver screen splatter. Settings screen: Atmosphere → "ATV screen splatter".
* `wheelSpray` (true) — wheel spray particles. Settings screen: Atmosphere → "ATV wheel spray".
* Amounts follow `presentation.quality` (Effects quality) and Minecraft's Particles option.

## Commands
* `/atvgrime` — show the grime of the ATV you ride (or the nearest within 8 blocks).
* `/atvgrime mud <low> [high] [moisture]` (op) — set mud (0..1). `high` defaults to `low - 0.25`, moisture to 1 (wet).
* `/atvgrime snow <amount>`, `/atvgrime wet <amount>`, `/atvgrime clean` (op).

## In-game test script
Setup: creative, a world with a swamp/mangrove area, a snowy biome and a river. Get an ATV (`/give @s frontierhunts:atv`),
place it, F3 off. Test at Graphics preset **Balanced** (blocky ATV) and **Ultra** (realistic ATV), shaders off and on.

1. **Levels / overlay alignment.** Stand next to the ATV: `/atvgrime mud 0.2 0 1` → wet dark mud on tyres and the
   underside only. `/atvgrime mud 0.6 0.3 1` → lower body/footwells caked, streaks on fender undersides and edges.
   `/atvgrime mud 1 1 1` → heavily caked, upper body speckled/streaked but paint still showing on top. Walk around it:
   no z-fighting/flicker, masks sit exactly on the model (tyres too, while steering and rolling), no gaps on wheels.
2. **Drying.** `/atvgrime mud 1 1 1`, wait ~4 min (or `/atvgrime mud 1 1 0`): the mud turns from dark brown to a light
   grey-tan crust. `/atvgrime` shows moisture falling.
3. **Driving in mud.** `/atvgrime clean`, build a 40-block strip of `minecraft:mud`. Drive slowly through it: tyres get
   caked, little reaches the body, brown clods + mist come off the trailing edge of the tyres. Drive through it fast
   (full throttle): roost behind the ATV, spray on fenders/body appears, `/atvgrime` mud high > 0. Reverse through it:
   spray goes forward.
4. **Screen splatter.** First person, drive fast through the mud strip: mud splats/drips hit the view, start dark,
   sag a little, dry lighter and fade in 10-20 s. F5 to third person: no overlay. Back to first person: remaining splats
   reappear and keep fading. Settings → Atmosphere → "ATV screen splatter" off: nothing is drawn.
5. **Water wash.** Muddy ATV (`/atvgrime mud 1 1 1`), drive into a ~1-block-deep river: splash burst + drops on entry,
   water beads/runs on the view, mud splats on the view rinse off quickly. Keep driving in the water ~10 s: tyres and
   lower body clean first, then the upper body (`/atvgrime` low drops before high). Drive out: wet beaded film on the
   ATV that dries in ~1 min, drips from the fenders while parked.
6. **Snow.** In a snowy biome drive through snow layers / powder snow: white powder puffs + chunks, snow packs into the
   treads, then powder on the fenders/seat/rack tops at speed; snow smears on the view that fade in a few seconds.
   `/weather rain` in the snowy biome (snowfall): park — the snow stays and slowly builds. `/weather clear` and drive
   to a plains biome: it melts over a couple of minutes (desert: ~1 min). `/atvgrime snow 1` then drive into water:
   snow is cleared.
7. **Rain mud.** Plains, `/weather rain`, drive fast over dirt/grass for a minute: moderate mud builds (never fully
   caked), stays wet while it rains.
8. **Persistence.** Make the ATV muddy, `/atvgrime` to note values, save & quit, reload: same values and look.
   Break and re-place: clean (state lives on the entity).
9. **Multiplayer (dedicated server, 2 clients).** Client A drives through mud; client B (watching) sees the same mud
   levels on the ATV within ~0.5 s and the wheel spray; B gets **no** screen splatter. B walks away > view distance and
   comes back: the ATV still shows its mud immediately. A non-op cannot use `/atvgrime mud`.
10. **Performance.** Effects quality CINEMATIC, drive full speed through a long mud field with 2-3 ATVs: frame time
    stays smooth; particles are capped (F3 particle count stays bounded). PERFORMANCE: noticeably fewer particles,
    no wet film layer.
11. **Shaders (Iris + e.g. Photon / Complementary).** Repeat 1, 5, 6: overlays are lit like the ATV, no flicker,
    the wet film is visible but subtle, splatter draws above the world and below the HUD.

## Known limitations
* Mud/snow masks are discrete levels (5 mud, 3 snow): coverage grows in steps (the wet/dry colour is continuous).
* The realistic wheel texture is a tiled material atlas, so tyre mud repeats around the tyre (varies per lug).
* Spray particles collide with blocks, not with the ATV body.
* Not tested in game here (no client available in this environment); the logic was checked with an offline harness
  (`tools/atvgrime_harness/GrimeHarness.java`) and preview renders of all masks on both models.
