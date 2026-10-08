# Workstream `tracking` — readable sign, truthful blood, tracking hound

Branch: `tracking` (clone `/home/claude/work/tracking`). Compile: `tools/compile.sh` → exit=0. Full jar built OK with
`python3 tools/build.py tracking-test /home/claude/work/tracking` (not in-game tested — no client here).

## What was built

### 1. One print system (decals are authoritative)
* **`tracking/TrackPrints`** replaces the block prints. `AlpineTrackPrints` (every 4th tick per entity) now only calls
  `TrackPrints.tick`. No new `AlpineTracks` blocks are placed; old ones in existing worlds still age out by random tick
  (block stays registered for world compatibility).
* Makers: Whitetail/elk/moose, all 14 wildlife2026 species, the hound, players (boots), vanilla rabbit, fox, wolf,
  polar bear. One print group (`TrailMark`, style 0) per stride; running is detected from real speed (longer stride,
  bounding/gallop groups).
* Surfaces: vanilla **snow layers** (incl. one layer on grass — print lies on the snow's visible top; single-layer snow
  has no collision so both prints and blood are lifted onto it), snow block, powder snow, mud, clay, farmland, dirt,
  coarse/rooted dirt, podzol, path, mycelium, forest duff, sand, gravel (big animals only), grass (faint pressed prints,
  medium+ animals only). Boots only on soft ground.
* Ageing: prints last 1.5 in-game days dry; weather adds wear per exposed mark by precipitation type (snowfall fills
  snow prints x6, rain slumps mud/sand x3.5, snow over bare ground buries marks; blood x5–7). Client crossfades
  crisp → softened → filled art and fades the last 40 %.
* New print kinds (art rows in `textures/entity/track_prints.png`): deer, elk, moose, bison (large round cloven),
  boar (cloven + wide dewclaws), canid (wolf/coyote/hound, claws), fox (chevron pad), feline (cougar/panther/lion/
  cheetah, 3-lobed pad, no claws), bear front + hind (grizzly/black/polar), grouse (3-toed), duck (webbed), rabbit
  hop group, boot, wound bed. Generator: `tools/tracking/gen_prints.py` (+ `preview_prints.py`, `sim_trail.py`).
* Inspection (existing aim + use flow, journal "clue" record kept) now reads e.g.
  `ELK · HOOFPRINTS` / `11.8 cm print — big and round, likely a bull · walking · 1.6 m stride` /
  `Made last night · heading north-east · prey sign`. Age phrases: fresh — minutes old / under an hour / a few hours
  old / made last night / half a day / about a day / more than a day. Size/sex is an *estimate from measurements*.
* Sync is now incremental (`TrailNetwork.Delta`, registrar version **"5"**): only new/re-weathered marks and removals
  travel; client keeps up to 192 marks within 32 blocks, blood first.

### 2. Rubs and scrapes
Verified existing `DeerSign` RUB (stripped-bark panel on the trunk side facing the deer, 4-stage ageing, report on
right-click) and SCRAPE (pawed ground patch) — no code gaps found; unchanged.

### 3. Blood by shot placement (`tracking/BloodTrail`, `WildlifeBleeding`)
* `TrailMark` gained `sign` (blood type / print kind), `stride`, `scale`; saved in NBT (`kind`,`stride`,`scale`,
  schema 4, old saves load with defaults) and in the codec.
* HEART: bright red, dense on **both sides** each bound + exit spray, chest-height brush spray, short death run.
  LUNG: pink froth with bubbles (bubble overlay pops within ~2 h), brush at chest height, heavy then tapering.
  LIVER: dark red steady drops. GUT: sparse dark drops with stomach-matter flecks, long trail **with beds**
  (gut/liver/flesh-hit deer now stop running sooner and lie down; bed = matted oval + stain; walking up makes them
  jump). MUSCLE (leg/body/shoulder): bright drips that thin and stop; animal usually lives. NECK: arterial spurts.
* Colours dry from wet red to brown over ~2.5 h; atlas `blood_trail_v4.png` (4x5, `tools/tracking/gen_blood.py`).
* **[integration, user decision] superseded:** heart / double-lung hits drop on the spot again (no death run; DeerWound
  HEART/DOUBLE_LUNG back to 1600). Original text: Heart / double-lung hits now usually make a short **death run** (heart 65 %, both lungs 85 %: ~7 s / ~13 s)
  instead of always dropping on the spot (DeerWound durations for those two regions changed accordingly).
* Wildlife2026 animals bleed: region from where the projectile struck the body; vital wounds keep costing health.
* **[vital] superseded:** single-lung deer hits now drop on the spot too (kill cam); wildlife projectile hits are traced
  through `vital/WildlifeVitals` (heart/lung = instant death + kill cam, other zones capped, bleed via this class). See docs/ws/vital.md.

### 4. Tracking hound (`tracking/hound/*`, `tracking/client/*`)
* Get one: craft **Hound Lead** (shapeless: lead + leather + bone + raw beef; also in the Field Equipment creative
  tab) and use it → your own hound (redbone or bluetick, named). One per player (`frontierhunts_hounds` saved data);
  a hound not seen for a whole in-game day can be replaced.
* Lead: use = come (whistle), sneak-use = off the line / heel. Right-click hound = sit/stay toggle; sneak-right-click
  = heel. Feeding meat heals it. Owner's hits/shots never hurt it. Never attacks anything. Runs to owner from big
  predators / monsters. Swims. Waits (whining) if the owner falls >80 blocks behind.
* TRACK: hold the lead, aim at blood or prints, press use → "drops nose to the …". It follows that animal's own line
  in time order (server scent ledger of the animal's path + its saved prints/blood), casts in widening circles at
  gaps, occasionally overruns old lines and casts back, bawls more often and higher as the scent freshens, chops when
  close. Found dead/downed → barks, sits beside it until you walk up. Alive → bays it at ~3.5 m and holds a wounded
  animal (slowness). Gives up after 8 in-game minutes or a failed 30–45 s cast. Respects `frontierTracking`.
* Visuals: Classic box model (`HoundModel`, 64² textures), Balanced low-poly + pixel coat, Ultra sculpted mesh
  derived from `wolfsrc` (droopy ears folded from the wolf flaps, longer muzzle, thin sabre tail, sleek coat) —
  `tools/tracking/hound/hound.py export`. Rig `HoundRig` (sniff nose-down sweep, sit, bay, tuck, wag) mirrors
  `tools/tracking/hound/houndrig.py`. Sounds: vanilla fox sniff / wolf howl / bark / whine pitched for a hound (no
  synthesized audio).

## Shared files touched (all marked `// [tracking]`)
* `hunting/Whitetail.java`: + `shotRegion()` getter, + `woundBed(int)`; `BloodTrail.wounded(this)` in the bleed tick;
  death-run term on `var23` (vital) in `projectileHit`; flee memory via `BloodTrail.fleeMemory(...)`.
* `hunting/DeerWound.java` (copied from dec62g8): HEART 150 / DOUBLE_LUNG 260 ticks.
* `landscape/AlpineTrackPrints.java` (copied, body replaced by delegation — **perf workstream**: the per-entity
  filter is now `tickCount % 4` + `TrackPrints.kindOf` instanceof chain; please merge your optimisation into
  `TrackPrints.tick/kindOf` rather than restoring block placement).
* `client/TrailClient.java`, `tracking/*` (owned). `.gitignore` gained `!/docs/`.
* Lang fragment `patch/_merge/assets/frontierhunts/lang/en_us.json/tracking.json`.

## Offline checks
`tools/tracking/harness` (compile against `/tmp/claude-0/cc-tracking` + cp62 + rtlib): `TrackingHarness` (NBT/codec
round trips, old-save defaults, lifetimes, age phrases incl. "last night", compass, estimates, region mapping,
death-run odds) and `HoundHarness` (line following with a 30-block gap → cast pick-up → end reached): all PASS.

## IN-GAME TEST SCRIPT
Setup: new creative world, `/gamerule frontierHunting true`, `/gamerule frontierTracking true`, `/time set 1000`.
1. **Snow layers**: go to a snowy plains area (single snow layers on grass) or `/fill ~-6 ~-1 ~-6 ~6 ~-1 ~6 grass_block`
   then `/fill ~-6 ~ ~-6 ~6 ~ ~6 snow[layers=1]`. Switch to survival, walk and sprint across → boot prints on the
   snow top (blue-grey relief), sprinting spacing longer. `/summon frontierhunts:whitetail ~3 ~ ~` and
   `/summon frontierhunts:wolf ~-3 ~ ~` etc.; let them walk → cloven pairs / canid line with claws. Try bison, boar,
   cougar, grizzly, grouse, duck, rabbit.
2. **Mud/soil/sand**: repeat on mud, podzol, sand, gravel (only big animals), grass (faint).
3. **Inspect**: aim at a print group, press use → HUD panel (species, cm, estimate, gait, stride, age, heading).
   Check at `/time add 3000` ("a few hours old"), print made at night read next morning ("made last night").
4. **Ageing**: `/time add 6000` repeatedly: crisp → softened → filled → gone (~36000 ticks). `/weather thunder`
   over snow: prints soften/fill much faster; snow over bare-ground prints removes them.
5. **Blood**: survival, field bow + arrows (`/give @s frontierhunts:field_bow`, `frontierhunts:field_arrow 32`).
   Shoot deer in: heart (bright spray both sides, short run, crash), lungs (pink froth + bubbles, brush spray at
   chest height, tapering), liver (dark steady drops), gut (sparse dark + green/brown flecks, deer slows, beds;
   find the matted bed stain), leg (bright drips that stop). Inspect blood: type reading + wet/tacky/dried; skip
   time 3000–6000 ticks and see it dry brown. Shoot a wolf/boar: bleeds, heart/lung hits kill after a short trail.
6. **Hound**: craft or `/give @s frontierhunts:hound_lead`; use it → hound appears (chat explains controls). Walk:
   heels; right-click → sits/stays; sneak-right-click → heel; walk 40 blocks away, use lead → comes. Ride/fly away
   → teleports when >24 blocks. Hit it yourself / shoot it → no damage. `/summon frontierhunts:grizzly ~8 ~ ~`
   → hound runs to you, tail tucked.
7. **Track**: wound a deer (gut or leg shot works best), wait until it is out of sight (or `/time add 600`), stand
   at the hit site holding the lead, aim at the first blood → HUD says "Put the hound on this blood", press use.
   Expect: nose-down sniff pose with wag, follows the actual path (compare with the blood), casts in circles where it
   loses it, bawls more often as it closes; at a bedded wounded deer it bays and the deer is held; at a dead deer it
   barks and sits beside it until you walk within ~3 blocks. Also try from a fresh print (unwounded deer: bays,
   deer bolts, hound resumes then gives up). `/gamerule frontierTracking false` mid-track → hound stops.
8. **Presets**: Settings → animal style Classic / Balanced / Ultra: hound switches box model / low-poly pixel coat /
   sculpted coat (both coats: spawn several hounds on different players or check `HoundVariant` NBT).
9. **Multiplayer**: dedicated server + 2 clients: both see the same prints/blood appear and age; only the owner can
   command/track with their hound; the other player's lead gives them their own hound.
10. **Shaders**: repeat 1, 5, 7 with Iris + Photon: decals (entityTranslucent, drawn AFTER_ENTITIES) visible, no
    z-fighting; hound renders in all presets.

## Known limits / for the coordinator
* Not launched in-game here; shader/AMD/NVIDIA/multiplayer untested.
* Scent ledger (the path between visible sign) is server memory; after a restart the hound uses saved prints/blood
  only and casts across gaps.
* The heart/lung death run changes kill timing (killcam/trophies workstreams: death now happens via `fatalTicks`
  a few seconds after the hit in most heart/double-lung shots).
* A hound left in unloaded chunks cannot be called ("out of earshot, last seen near x, z") until reloaded or 1 day.
