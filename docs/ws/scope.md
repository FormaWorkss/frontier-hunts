# Workstream `scope` - variable-power riflescopes

Long-range scopes now zoom: hold aim, scroll the mouse wheel, the power ring clicks through its detents and the
view eases to the new power. Purely client-side presentation: no packets, no server code, no item data written.

## Power ranges (ScopePower)

| Optic | Range | Starts at | Steps | Reticle (SFP) mils true at |
|---|---|---|---|---|
| Six Power Scope (field guns) | 3-9x | 6x | 0.5x | 9x |
| Eight Power Scope (field guns) | 4-16x | 8x | 0.5x below 10x, then 1x | 16x |
| Twelve Power Scope (field guns) | 5-25x | 12x | 0.5x below 10x, then 1x | 25x |
| Thermal Scope (field guns + Ridgeline) | 2-8x digital e-zoom | 4x | 1x | - |
| Ridgeline stock scope | 3-9x | 3x | 0.5x | 9x |
| Ridgeline + 4x Field Optic (renamed "4-12× Field Optic") | 4-12x | 4x | 0.5x / 1x | 12x |
| Fixed (unchanged) | red dots 1x, 2x prism, tranquilizer built-in 3x, iron sights 1.5x | | | |

"Starts at" is the old fixed power, so nothing changes until the player scrolls.

## Controls / behaviour
- Wheel while aiming (aim key held, gun at least 20% raised, first person, no screen): up = more power.
  Only for variable optics, and then the scroll event is cancelled, so the hotbar slot never changes under a raised
  scope. Fixed optics and everything else scroll the hotbar as before. Trackpad fractions accumulate like vanilla.
- Eased transition (log space, ~0.2 s); raise/lower interpolates zoom in log space as well.
- Readout: at about half past four just outside the lens, a small log-scaled power-ring arc (tick per 1x, long ticks at
  ends and multiples of 5), a pointer, "12.0×" and "MIL @ 25×" (brightens green exactly at the calibrated power;
  "E-ZOOM" on the thermal). Full for 1.4 s after scoping in or a change, fades out over 0.9 s. Bottom caption shows the
  optic's range and name ("5-25×  /  LONG RANGE"). Thermal overlay's bottom line shows the live power.
- Reticle: second focal plane - same apparent size at every power; mil marks are spaced for the calibrated power.
- Sensitivity: fixed optics keep the mod's existing feel; a variable optic feels exactly as before at its lowest
  power and then the turn rate scales 1:1 with magnification (same on-screen speed at every power).
- Hold sway while scoped (magnified optics >= 2x): slow breathing cycle (~14/min), wandering drift and a faint pulse.
  Constant angle (~0.07 deg standing), so on screen it grows in proportion to the power, like a real scope; crouch
  x0.55, crouch + bipod x0.18, steady stock x0.8, moving x1.8, larger for ~1 s right after shouldering. It moves the
  player's actual look direction (as the existing recoil code does), so the reticle is always honest. Off with
  Reduced Motion. Never applied while a screen, the kill cam or the trail-cam darkroom owns the view.
- Click: original synthesized power-ring detent click (2 variants, pitch rises with power), duller end-stop knock.
- Memory: last power per weapon item + optic in `config/frontierhunts-scope-power.properties` (client only, saved
  async a moment after the last change and on logout).

## How the zoom is applied (FOV interplay)
- `ScopeZoom.fov` - `ViewportEvent.ComputeFov`, priority HIGH, world pass only (`usedConfiguredFov`): sets
  `fov' = 2 atan(tan(fov/2) / power^raise)`. No 0.1 modifier floor (25x needs ~3.2 deg), frame-smooth.
- `ScopeZoom.stripLegacyZoom` - `ComputeFovModifierEvent`, LOWEST: divides out exactly the factor that the unchanged
  base-jar `ExpeditionClient.fov` / `RifleClient.fov` multiply in (same formula, same inputs, same tick), first person
  only. (Those two classes were not copied into src: their decompiled @Mod constructors do not compile.)
  **If someone later edits those two fov handlers, update `stripLegacyZoom` to match.** Third-person Ridgeline
  zoom is left as it was.
- After us: FieldGunEffects recoil kick (NORMAL, relative), ObservationView, FieldPhotoMode (NORMAL; aim is released
  when its screen opens), Darkroom (NORMAL, absolute - wins), KillCamClient (LOWEST, absolute - wins), and the LOWEST
  recorders WildlifeFov / ViewProjection / WeatherClient / KillCamClient(idle) all see the scoped FOV.
- `WildlifeFov.zoom()` clamp raised 12 -> 30 so the animal LOD keeps the full mesh through a 25x scope; whitetail
  LOD already uses ViewProjection.scale(), which records the final FOV.

## Files
New: `client/ScopePower.java` (pure maths + profiles), `client/ScopeZoom.java` (runtime), `tools/gen_scope_audio.py`,
`patch/assets/frontierhunts/sounds/optic/zoom_click_0|1.ogg, zoom_stop.ogg`,
`patch/_merge/.../sounds.json/scope.json` (`optic.zoom_click`, `optic.zoom_stop`),
`patch/_merge/.../lang/en_us.json/scope.json` (subtitle + four_power_optic renamed "4-12× Field Optic").

Copied from dec62g8 into src and edited (hook lines marked `// [scope]`):
- `client/OpticInput.java`: `turn` uses `ScopePower.eventSensitivity(..., ScopeZoom.turnScale())` for scoped guns;
  `magnification()` returns `ScopeZoom.power()` for scoped guns (bob scale follows live power); `instrument` shows
  the live power and calls `ScopeZoom.readout`.
- `client/FieldScopeView.java`: uses the live power, SFP mil scale (`ScopeZoom.reticlePower`), caption, readout.

Shared files (one line each):
- `client/KillCamClient.java` fov(): `playerFov = (float)ScopeZoom.scopedFov(playerFov); // [scope]` (kill cam
  returns into the scope's zoom instead of popping).
- `wildlife2026/client/WildlifeFov.java`: `Math.min(12.0, ...)` -> `Math.min(30.0, ...)`.

## In-game test script
1. `/give @s frontierhunts:semi_auto_rifle` (any scoped-capable field gun) and fit a Twelve Power Scope at the
   attachment workbench. Hold aim (right mouse): view shows 12x, readout "12.0×" + "MIL @ 25×" at lower right of the
   lens, fading after ~2 s. Caption "5-25×  /  LONG RANGE".
2. While aiming, scroll up: clicks, power steps 12 -> 13 ... 25 smoothly; at 25 a dull end-stop knock. Scroll down to
   5x (half steps below 10). The hotbar slot must not change. Release aim and scroll: hotbar scrolls normally.
3. At 25x "MIL @ 25×" turns green; mil marks keep their screen spacing at every power.
4. Mouse feel: at 5x it matches the old six-power feel roughly; at 25x the view moves the same screen distance per
   mouse inch as at 5x.
5. Stand still scoped at 25x: slow vertical breathing drift and small wander; crouch -> smaller; crouch with bipod ->
   nearly still. Settings > Reduced Motion on -> no sway.
6. Switch to another weapon and back: power snaps to the remembered value. Quit and relaunch: still remembered.
7. Eight Power Scope: 4-16x; Six Power Scope: 3-9x; Thermal scope (powered): 2-8x e-zoom, bottom line shows "4.0×".
8. Ridgeline rifle (`/give @s frontierhunts:ridgeline_rifle`, crouch/rest so it can aim): 3-9x; fit the 4x Field
   Optic (item now named "4-12× Field Optic") -> 4-12x. Two-power prism and red dots: wheel still changes the hotbar.
9. Zoom to 25x on a deer ~150 blocks out (Ultra animals): the full sculpted mesh stays (no low-LOD pop).
10. Kill shot (heart/lung) while scoped at 18x: kill cam plays and returns into the scoped 18x view without a FOV pop.
11. Open the trail-cam darkroom / photo mode while aiming: their own FOV is used, no compounded zoom, no sway.
12. Multiplayer (dedicated server + 2 clients): no errors in server log; each client's power is independent.
