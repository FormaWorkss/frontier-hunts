# Workstream `vital` — heart / lung drops, everything else runs (all animals)

Branch: `vital`. Compile `tools/compile.sh` → exit=0; jar `python3 tools/build.py vital-test .` builds;
`tools/check_jar.py` → 0 errors, 0 warnings. Offline harness `tools/vital/harness/VitalHarness.java` → ALL PASS
(14 species x 3 body yaws: broadside heart/lungs, quartering, liver, gut, ham, high back, leg, head, over-the-back
graze, weak arrow → near lung only). Not run in-game here.

## The rule (user, verbatim)
"i want the deer to run that far if its not a lung or heart shot; if it is a lung or heart shot kill cam comes on and
they drop — this applies for all animals"

## Behaviour per region
| Hit (real projectile, lethal tip, energy ≥ 0.08) | Deer / elk / moose | 2026 wildlife |
|---|---|---|
| Heart | drops on the spot, kill cam | dies on the spot, kill cam (card: species · yd / HEART) |
| Both lungs | drops, kill cam | dies, kill cam (DOUBLE LUNG) |
| **One lung** | **drops, kill cam (was a delayed death run)** | dies, kill cam (SINGLE LUNG) |
| Liver / gut / neck / head / brain / spine | runs, bleeds (tracking blood), dies after the DeerWound run/bed | runs (dangerous species still charge), bleeds, dies after the run (liver ~35 s, gut ~100 s, neck/head ~12 s) |
| Shoulder / body / leg | runs, may survive | runs, bleeds a little, survives and heals 1 hp / 10 s |
| Bird body core | – | dies on the spot, kill cam |
| Bird wing / tail / leg | – | flees, survives |

* Blunt/judo tips and too-weak hits never drop (deer: BODY as before; wildlife: vital zone → flesh, or a weak
  lung wound that bleeds out).
* Brain / spine did not drop instantly before; unchanged (short fatal run, no kill cam).
* No other path kills a standing animal on a non-vital hit: deer already clamp non-vital damage (incl. FirearmDamage's
  3rd-hit rule) to health − 1.5; wildlife non-vital projectile damage is capped at a per-zone share of max health and
  never below 1 hp. An animal already at ≤ 25 % health that takes another body hit bleeds out within ~8 s (a short
  run, still no drop). Melee / fire / explosions / tranquilizer darts / flares / bait untouched.

## Wildlife anatomy (`vital/WildlifeVitals`)
Per-species torso, head, neck and leg columns from the classic box models × classicScale (`tools/vital/gen_torso.py`),
in the entity's rotated body frame (yBodyRot, entity scale, lowered when bedded). Organs as ellipsoids in torso
coordinates: heart low (30 % up) just behind the elbow line, two lung lobes above/behind it (both crossed = double
lung), liver behind the diaphragm, gut in the rear belly. The projectile's line (position + velocity; falls back to
shooter eye → impact) is marched through the body from its entry up to an energy-dependent depth
(0.3 + 1.1·energy blocks); the most severe zone wins.

## Files
New: `src/.../vital/ShotVitals.java` (rule, `LivingDamageEvent.Pre` LOWEST for WildlifeMob, heal tick, deer helper),
`src/.../vital/WildlifeVitals.java`, `tools/vital/gen_torso.py`, `tools/vital/harness/VitalHarness.java`.
Shared-file hooks (all `// [vital]`):
* `hunting/Whitetail.java` projectileHit: `var23 = ShotVitals.deerDrops(var4) && var5 >= 0.08F && (tip lethal)`.
* `killcam/KillCamServer.java`: `afterDeerHit` confirms only if `deerDrops(p.region)`; `death` (wildlife) confirms only
  if `ShotVitals.lastHit(mob).drops()`, sends its region and traced entry point; predicted wildlife organ = -1.
* `killcam/KillCamPredictor.java`: deer lethal = `deerDrops(region)`; wildlife lethal = `WildlifeVitals.classify(...).drops()`
  (same energy scale as the real hit), so the camera still starts the tick the shot is fired.
* `client/KillCamOverlay.java`: wildlife card shows `SPECIES · N YD` / organ / verdict when a region is sent.
* `tracking/WildlifeBleeding.java`: `classify` uses the traced zone's blood type first; new `hasten(mob, ticks)`.

## In-game test script
Setup: creative flat world, `/gamerule frontierHunting true`, `/time set 1000`, rifle + ammo and field bow + arrows
(see docs/ws/killcam.md step 1). Kill cam on (`/killcam lethal`). Survival for tests (creative is fine for kill cam).
1. `/summon frontierhunts:whitetail ~ ~ ~-60 {NoAI:1b,Rotation:[90f,0f]}` — shoot low behind the front leg (heart):
   kill cam, drops. Repeat with a shot centred behind the shoulder (double lung): kill cam, drops.
2. Quartering-away deer (`Rotation:[135f,0f]`), shoot the rear of the ribcage so only one lung is hit: **kill cam,
   drops** (previously ran).
3. New deer: shoot liver (just behind the ribs, mid-height), gut (rear belly), shoulder, leg, neck → no kill cam; it
   runs with the matching blood; liver/gut/neck die after the run or bed; leg/shoulder usually live.
4. Same deer: 3 more non-vital hits → never drops on the spot (bleeds out quickly instead).
5. Wildlife: `/summon frontierhunts:grizzly ~ ~ ~-50 {Rotation:[90f,0f]}` (no NoAI). Shoot its gut → no kill cam, bear
   charges/runs, dark sparse blood, dies after ~1.5 min. New bear: heart (low, right behind front leg) → dies on the
   spot, kill cam, card `<SPECIES> · N YD` / `HEART`.
6. Repeat 5 with wolf, pronghorn, bison, boar with rifle and bow: lungs (just behind shoulder, mid-height) → kill cam
   + instant death; hindquarter / leg → flees, survives; `/data get entity @e[type=frontierhunts:wolf,limit=1,sort=nearest] Health`
   rises 1 every 10 s afterwards.
7. Grouse / duck: body shot → dies, kill cam; wing tip / tail → flies off.
8. Melee a coyote with a sword → vanilla behaviour (can kill; no kill cam).
9. Predicted misses: shoot a wolf's rear leg at 80 m → no kill cam (or a brief fade if the animal moved).
10. Dedicated server + 2 clients: only the shooter sees the kill cam; both see the same drop / run and blood.
