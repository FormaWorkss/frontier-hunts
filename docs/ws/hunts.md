# Workstream `hunts` — species hunts: a real hunt for every animal, tied into the journal, contracts and assignments

Branch: `hunts` (clone `/home/claude/work/hunts`, from master 0bf1526). `tools/compile.sh` → exit=0 (958 classes).
`python3 tools/build.py hunts-test .` builds; `tools/check_jar.py <jar> . --base master` → **0 errors, 0 warnings**.
`tools/hunts/harness/run.sh` → **ALL PASS (1506 checks)**; `tools/journal/harness/run.sh` → ALL PASS (172 checklist entries).
`python3 tools/hunts/audit.py <jar> .` → **PASS** (every milestone: species spawns naturally, hook compiled in, needed and
reward items obtainable in survival) — table in `docs/ws/hunts/audit.md`. `tools/recipe_audit.py <jar>` → PASS.
**Not run in-game here** (no client) — the test script below is still to do.

User request: *"make sure all animals have real hunts things to accomplish and get through and tie them all in with the mod
correctly and challenges etc"*.

## 1. What the player gets
* **17 species hunts × 5 milestones = 85** (every record-book species, `camps.Quarry`; fish are not record-book quarry and
  are not part of this). Each hunt: **Scout** (photo / sighting / glassing / flush) → **First take** (clean heart-lung kill;
  birds: any) → **Technique** (the way that animal is really hunted, using the mod's own gear) → **Quality** (mature
  antlers, a heavy animal, prime winter fur, a bag limit, the wing shot) → **Master** (the technique and quality together,
  pays a **title** + a piece of gear, announced in chat). Rewards: journal XP (through the checklist, 9,580 XP in all),
  reserve tokens (2,060 in all, the expedition stores' currency) and useful gear.
* **Hunter's Journal → Species**: each row now has 5 hunt pips (gold medal when mastered); **click a species → its hunt
  card**: track icon, tagline, progress pips by tier colour, master title chip, your record (seen / on camera / taken /
  best), *How it's hunted* (real-world, 2-3 plain sentences), where it lives, its season contract, then the five
  milestones (rule, gear it needs, progress bar for counts and bag limits, rewards). ‹ All species / ‹ prev · next ›.
* **Journal → Checklist**: new **Hunts** category (chip "Hunts n/85"), grouped by species with a header row (track, pips;
  click → that hunt card); rows use the new milestone icons; toasts and field notes as for every checklist entry, plus a
  reward note ("Called in: +20 tokens and 3× Scent Cover") and a master note/chat line.
* **Expedition journal → Contracts**: the five standing contracts **plus up to 4 species contracts posted for this half of
  the reserve month**, in season only, shortest seasons first (every species gets posted in its season); a line above the
  board says which are posted now. They work like the standing ones (20 minutes, shared with the party, tokens).
* **Ranger Assignments (K)**: 6 new field assignments driven by hunt events: *Predator control* (2 coyotes/wolves to the
  call), *Hog control* (2 boars dusk-dawn), *Problem bear* (bear over your bait), *Waterfowl count* (3 mallards over the
  spread), *Upland count* (2 grouse on the wing), *Glassing survey* (glass 3 animals ≥100 m).
* **Mechanics wired up so every technique is real and achievable** (they were missing or did nothing for wildlife):
  * **Glassing**: holding binoculars / rangefinder / thermal or NV binoculars steady (~0.7 s) on an animal in clear sight
    up to 192 blocks stamps it "glassed by you" (10 min) and counts (distance recorded).
  * **Predator Locator Call** now calls predators: coyotes/wolves trot in (85 % after dark, 45 % by day), cats (60/25 %,
    cheetah by day), bears (35 %); they **swing to the downwind side** (Wilderness wind) and hang up 8-18 blocks out,
    stand and look, then drift off; call-shy for a day. Their own senses stay in charge (they bolt when they see you).
  * **Bait** (existing item, use on the ground) now makes a **bait pile** (2 in-game days, 4 feedings, max 3 per hunter):
    black bears, grizzlies, boars, panthers, lions within 64 blocks come in (45 % dusk-dawn, 12 % by day), feed, linger.
  * **New: Duck Call** (planks + stick + string + gold nugget) and **Mallard Decoy** (planks ×3 + green dye → 2, floats on
    water like a lily pad, original 3D drake model): ducks that hear the call fly in high, set their wings and land among
    the decoys (or on open water near you); decoys alone draw passing ducks; a duck spooked off the water **flares**
    (lifts off and away — the passing shot). Original synthesized hail-call audio (3 variants).
  * **Hound**: a take counts as "behind the hound" when your tracking hound is trailing/casting/baying/found on that animal.
  * Plus existing mechanics read at the shot: deer calls (journal call stamp), tree stand seat, ground/tower blind (blind
    blocks around you), snow camo worn, on foot vs riding, rut phase, month, time of day, snow underfoot, water nearby,
    herd size, charge (WARN + targeting you), on the wing, one-shot, recovery trail.

## 2. Species table (rules exactly as shown in game)
Tiers: scout / take / technique / quality / master. "tok" = reserve tokens.

| Species | Milestones (title — rule) | Master title | Season contract |
|---|---|---|---|
| Whitetail | 1. **Whitetail on camera** (scout) — Hang a trail camera on a trail, field edge or scrape. Any whitetail it photographs counts. _[+30 XP, 5 tok]_<br>2. **A clean whitetail** (take) — Take a whitetail with a heart or lung shot, then field-dress it. _[+50 XP, 10 tok, 10× reserve_308]_<br>3. **Called in** (technique) — Grunt, bleat or rattle a deer in, then take it within 5 minutes. Calls work best in the rut. _[+90 XP, 20 tok, 3× scent_cover]_<br>4. **A mature buck** (quality) — Take a buck with 8 points or more. _[+110 XP, 25 tok]_<br>5. **Rut hunter** (master) — During the rut, take a buck with 8 points or more from a tree stand or blind, with a heart or lung shot. _[+250 XP, 60 tok, 1× eight_power_scope]_ | Rut Hunter | "Stand hunt": standblind ×1, 40 tokens (Oct, Nov, Dec) |
| Elk | 1. **Glass the high parks** (scout) — Hold binoculars or a rangefinder steady on an elk 100 m or more away. _[+30 XP, 5 tok]_<br>2. **A clean elk** (take) — Take an elk with a heart or lung shot, then field-dress it. _[+60 XP, 10 tok, 10× reserve_308]_<br>3. **Called in** (technique) — Call an elk in with the Doe Bleat Call (a cow call) or the Grunt Tube, then take it within 5 minutes. _[+100 XP, 20 tok, 3× scent_cover]_<br>4. **A herd bull** (quality) — Take a bull with 5 points a side or better that weighs 270 kg or more. _[+120 XP, 25 tok]_<br>5. **Bugle season** (master) — During the rut, take a bull with 5 points a side or better that came to your call, with a heart or lung shot. _[+260 XP, 60 tok, 1× rangefinder]_ | Elk Caller | "Bugle season": called ×1, 50 tokens (Aug, Sep, Oct) |
| Moose | 1. **Close encounter** (scout) — See a moose clearly from 40 m or closer. _[+25 XP, 5 tok]_<br>2. **A clean moose** (take) — Take a moose with a heart or lung shot, then field-dress it. _[+60 XP, 10 tok, 10× reserve_308]_<br>3. **Called in** (technique) — Call a moose in with the Doe Bleat Call (a cow call) or the Grunt Tube, then take it within 5 minutes. _[+100 XP, 20 tok, 4× fixed_broadhead]_<br>4. **A big bull** (quality) — Take a bull with 7 points a side or more. _[+120 XP, 25 tok]_<br>5. **Rut bull by the water** (master) — During the rut, call in a bull near water and take him with a heart or lung shot. _[+260 XP, 60 tok, 1× game_pole]_ | Moose Caller | "Moose camp": clean ×1, 45 tokens (Sep, Oct) |
| Pronghorn | 1. **Glass a pronghorn** (scout) — Hold binoculars or a rangefinder steady on a pronghorn 100 m or more away. _[+30 XP, 5 tok]_<br>2. **A clean pronghorn** (take) — Take a pronghorn with a heart or lung shot. _[+50 XP, 10 tok, 10× reserve_308]_<br>3. **From the blind** (technique) — Take a pronghorn while you sit in a ground blind or tower blind. _[+90 XP, 20 tok, 10× reserve_308]_<br>4. **A heavy buck** (quality) — Take a pronghorn that weighs 54 kg or more. _[+100 XP, 25 tok]_<br>5. **Open-country stalk** (master) — Glass a pronghorn, stalk it on foot (no stand or blind) and take it from 100 m or more with a heart or lung shot. _[+240 XP, 60 tok, 1× twelve_power_scope]_ | Plains Stalker | "Speed goat": long100 ×1, 45 tokens (Aug, Sep, Oct) |
| Bison | 1. **Watch the herd** (scout) — Glass a bison with at least two more bison near it. _[+30 XP, 5 tok]_<br>2. **A clean bison** (take) — Take a bison with a heart or lung shot. _[+70 XP, 10 tok, 10× reserve_308]_<br>3. **Fair chase** (technique) — On foot (no stand, blind or vehicle), take a bison from a herd of three or more before it notices you. _[+100 XP, 20 tok, 10× reserve_308]_<br>4. **A big bull** (quality) — Take a bison that weighs 730 kg or more. _[+120 XP, 25 tok]_<br>5. **One shot** (master) — On foot, take an unaware bison from a herd of three or more with one clean shot. _[+260 XP, 60 tok, 1× bipod]_ | Plainsman | "Winter meat": onfoot ×1, 50 tokens (Nov, Dec, Jan, Feb) |
| Wild boar | 1. **Hogs on camera** (scout) — Get a wild boar on a trail camera. _[+25 XP, 5 tok]_<br>2. **A clean hog** (take) — Take a wild boar with a heart or lung shot. _[+50 XP, 10 tok, 10× reserve_308]_<br>3. **Night hunt** (technique) — Take a wild boar between dusk and dawn. _[+90 XP, 20 tok, 1× field_flashlight]_<br>4. **A big boar** (quality) — Take a wild boar that weighs 92 kg or more. _[+100 XP, 25 tok]_<br>5. **Dogs at night** (master) — Between dusk and dawn, take a wild boar your tracking hound trailed or bayed. _[+240 XP, 60 tok, 1× night_vision_binoculars]_ | Hog Hunter | "Hog control": take ×2, 35 tokens (all year) |
| Black bear | 1. **Bear on camera** (scout) — Get a black bear on a trail camera. A camera on a bait pile works well. _[+30 XP, 5 tok]_<br>2. **A clean bear** (take) — Take a black bear with a heart or lung shot. _[+70 XP, 10 tok, 10× reserve_308]_<br>3. **Over bait** (technique) — Use Bait on the ground to set a bait pile, hunt it from cover, and take a black bear that came to it. _[+100 XP, 20 tok, 4× bait]_<br>4. **A big bear** (quality) — Take a black bear that weighs 130 kg or more. _[+120 XP, 25 tok]_<br>5. **Bowhunter's bear** (master) — Take a black bear over your bait with a bow and a heart or lung shot. _[+270 XP, 60 tok, 1× compound_bow]_ | Bear Hunter | "Bear season": take ×1, 45 tokens (May, Jun, Sep, Oct) |
| Grizzly | 1. **Glass a grizzly** (scout) — Hold binoculars or a rangefinder steady on a grizzly 80 m or more away. _[+35 XP, 5 tok]_<br>2. **A clean grizzly** (take) — Take a grizzly with a heart or lung shot. _[+80 XP, 10 tok, 10× reserve_308]_<br>3. **Spot and stalk** (technique) — Glass a grizzly, then close in and take it before it notices you. _[+110 XP, 20 tok, 2× medkit]_<br>4. **A big boar** (quality) — Take a grizzly that weighs 290 kg or more. _[+130 XP, 25 tok]_<br>5. **Stand your ground** (master) — Stop a charging grizzly within 20 m with a heart or lung shot. _[+300 XP, 70 tok, 1× steady_stock]_ | Mountain Hunter | "High-country bear": take ×1, 60 tokens (Apr, May, Sep, Oct) |
| Polar bear | 1. **Glass a polar bear** (scout) — Hold binoculars or a rangefinder steady on a polar bear 80 m or more away. _[+35 XP, 5 tok]_<br>2. **A clean polar bear** (take) — Take a polar bear with a heart or lung shot. _[+90 XP, 10 tok, 10× reserve_308]_<br>3. **White on white** (technique) — Take a polar bear while you wear snow ghillie or snow camo clothing. _[+110 XP, 20 tok, 1× fur_mittens]_<br>4. **A big bear** (quality) — Take a polar bear that weighs 470 kg or more. _[+130 XP, 25 tok]_<br>5. **Arctic stalk** (master) — Glass a polar bear, close in wearing snow camo and take it unaware with a heart or lung shot. _[+300 XP, 70 tok, 1× thermal_binoculars]_ | Ice Walker | "Ice patrol": take ×1, 70 tokens (Dec, Jan, Feb, Mar) |
| Coyote | 1. **Song dog** (scout) — See a coyote clearly from 40 m or closer. _[+25 XP, 5 tok]_<br>2. **A clean coyote** (take) — Take a coyote with a heart or lung shot. _[+50 XP, 10 tok, 10× reserve_308]_<br>3. **Called in** (technique) — Blow the Predator Locator Call and take a coyote that comes to it. _[+90 XP, 20 tok, 10× reserve_308]_<br>4. **Prime winter pelt** (quality) — Take a coyote in December, January or February, when the fur is thickest. _[+100 XP, 25 tok]_<br>5. **Night stand** (master) — Between dusk and dawn, take 3 coyotes that came to your predator call. _[+240 XP, 60 tok, 1× thermal_scope]_ | Song Dog Caller | "Calling season": called ×1, 40 tokens (Dec, Jan, Feb, Mar) |
| Gray wolf | 1. **Glass a wolf** (scout) — Hold binoculars or a rangefinder steady on a gray wolf 60 m or more away. _[+30 XP, 5 tok]_<br>2. **A clean wolf** (take) — Take a gray wolf with a heart or lung shot. _[+70 XP, 10 tok, 10× reserve_308]_<br>3. **Called in** (technique) — Blow the Predator Locator Call and take a gray wolf that comes to it. _[+100 XP, 20 tok, 10× reserve_308]_<br>4. **Prime winter pelt** (quality) — Take a gray wolf in December, January or February. _[+110 XP, 25 tok]_<br>5. **Winter wolf** (master) — In winter, glass a gray wolf and take it from 100 m or more with a heart or lung shot. _[+260 XP, 60 tok, 1× muzzle_brake]_ | Timber Wolf Hunter | "Pack check": take ×1, 50 tokens (Dec, Jan, Feb, Mar) |
| Cougar | 1. **Ghost on camera** (scout) — Get a cougar on a trail camera. _[+35 XP, 5 tok]_<br>2. **A clean cougar** (take) — Take a cougar with a heart or lung shot. _[+80 XP, 10 tok, 10× reserve_308]_<br>3. **Behind the hound** (technique) — Take a cougar your tracking hound trailed or bayed. Put the hound on its blood or prints with the Hound Lead. _[+110 XP, 20 tok, 2× medkit]_<br>4. **A big tom** (quality) — Take a cougar that weighs 65 kg or more. _[+120 XP, 25 tok]_<br>5. **Snow and hounds** (master) — Take a cougar standing on snow, with your hound on its trail. _[+280 XP, 70 tok, 1× fur_hat]_ | Houndsman | "Cat tracks": take ×1, 60 tokens (Dec, Jan, Feb, Mar) |
| Lion | 1. **Glass a lion** (scout) — Hold binoculars or a rangefinder steady on a lion 80 m or more away. _[+35 XP, 5 tok]_<br>2. **A clean lion** (take) — Take a lion with a heart or lung shot. _[+90 XP, 10 tok, 10× reserve_308]_<br>3. **Spot and stalk** (technique) — Glass a lion, then close in and take it before it notices you. _[+110 XP, 20 tok, 10× reserve_308]_<br>4. **A maned male** (quality) — Take a lion that weighs 190 kg or more. _[+130 XP, 25 tok]_<br>5. **Stop the charge** (master) — Stop a charging lion within 20 m with a heart or lung shot. _[+300 XP, 70 tok, 1× holographic_sight]_ | Savanna Hunter | "Pride watch": take ×1, 70 tokens (Jun, Jul, Aug, Sep) |
| Panther | 1. **Panther on camera** (scout) — Get a panther on a trail camera. _[+35 XP, 5 tok]_<br>2. **A clean panther** (take) — Take a panther with a heart or lung shot. _[+80 XP, 10 tok, 10× reserve_308]_<br>3. **Wait in the hide** (technique) — Between dusk and dawn, take a panther from a tree stand or blind. _[+110 XP, 20 tok, 4× bait]_<br>4. **A big male** (quality) — Take a panther that weighs 58 kg or more. _[+120 XP, 25 tok]_<br>5. **Over the bait** (master) — Between dusk and dawn, from a stand or blind, take a panther at your bait with a heart or lung shot. _[+280 XP, 70 tok, 1× suppressor]_ | Night Watcher | "Night watch": night ×1, 60 tokens (Jun, Jul, Aug, Sep) |
| Cheetah | 1. **Glass a cheetah** (scout) — In daylight, hold binoculars or a rangefinder steady on a cheetah 80 m or more away. _[+30 XP, 5 tok]_<br>2. **Cheetah on camera** (scout) — Get a cheetah on a trail camera. _[+40 XP, 10 tok]_<br>3. **Watch a hunt** (technique) — Glass a cheetah while it stalks, chases or feeds on a kill. _[+120 XP, 25 tok, 1× trail_camera]_<br>4. **A clean cheetah** (take) — Take a cheetah with a heart or lung shot. _[+80 XP, 10 tok, 10× reserve_308]_<br>5. **Long shot** (master) — Take a cheetah of 50 kg or more from 100 m or more with a heart or lung shot. _[+260 XP, 60 tok, 1× field_camera]_ | Safari Tracker | "Camera safari": photo ×1, 40 tokens (Jun, Jul, Aug, Sep) |
| Ruffed grouse | 1. **Flush a grouse** (scout) — Walk the edges until a grouse bursts into the air near you. _[+20 XP, 5 tok]_<br>2. **Grouse for supper** (take) — Take a ruffed grouse. _[+40 XP, 10 tok, 12× shotgun_shell]_<br>3. **On the wing** (technique) — Take a grouse in flight. It flushes when it notices you - be quick. _[+80 XP, 20 tok, 16× shotgun_shell]_<br>4. **A day's limit** (quality) — Take 3 grouse in one day. _[+90 XP, 25 tok]_<br>5. **Wingshooter** (master) — Take 5 grouse on the wing. _[+200 XP, 50 tok, 1× double_barrel]_ | Wingshooter | "Upland walk": take ×2, 30 tokens (Sep, Oct, Nov, Dec) |
| Mallard | 1. **Ducks on the water** (scout) — See a mallard clearly from 40 m or closer. _[+20 XP, 5 tok]_<br>2. **A duck for the pot** (take) — Take a mallard. _[+40 XP, 10 tok, 12× shotgun_shell]_<br>3. **Over the spread** (technique) — Take a mallard within 24 blocks of your decoys, or one that came to your Duck Call. _[+90 XP, 20 tok, 2× mallard_decoy]_<br>4. **On the wing** (quality) — Take a mallard in flight. _[+90 XP, 25 tok]_<br>5. **Limit over the spread** (master) — Take 4 mallards over your decoys or call in one day. _[+220 XP, 50 tok, 1× semi_auto_shotgun]_ | Marsh Master | "Opening morning": take ×2, 35 tokens (Oct, Nov, Dec, Jan) |

Thresholds were checked against the mod's own generators: 8+ points ≈ 40 % of naturally spawned whitetail bucks; elk
"herd bull" ≈ 30 % of bulls; moose 7×7+ ≈ 40 % of bulls; wildlife weight tiers sit ~5 % above the species' typical male
weight (above any female), ≈ 15-20 % of animals. Bird sex is not visible on the models, so no sex-based tiers for birds.

## 3. How it is tracked (server)
`hunts.HuntBook` (pure data + rules) · `HuntEvent` (pure event) · `HuntTracker` (pure: event → counter updates, tags,
carry-over) · `HuntContext` (reads the field situation) · `HuntHooks` (entry points) · `HuntEvents` (game bus: glassing,
flushes, bait / predator-call use, lure goal on wildlife, duck flare, login carry-over + queued rewards) · `LureGoal`
(predator call / bait / duck flights) · `Lures` (calls of the last minute) · `HuntStore` (SavedData
`frontierhunts_hunts`: queued gear rewards, bait piles, decoy positions) · `HuntRewards` · `HuntContracts` ·
`HuntAssignments` · `HuntContent` / `DuckCallItem` / `DecoyBlock` · `HuntCommands` · `HuntsConfig` ·
`journal/HuntBridge` (new file: applies counters through the journal's own counting, online or offline) ·
client: `hunts/client/{HuntCardView, HuntIcons, HuntContractText}`.
* At the **first hit of a wound** the animal is stamped (persistent data `frontierhunts_hunts`) with the situation; every
  hit updates weapon / distance / wing / charge. The **take** = deer field-dressed (credited to the shooter like the
  journal) or wildlife death credited by the journal (killer or bleed-out); it becomes a `HuntEvent` → milestones of that
  species; counters `hunt.<species>.<key>` (journal record, synced) → checklist entries (Category.HUNTS) complete → XP,
  toast, note; `HuntRewards` pays tokens (works offline) and gear (inventory, or queued until login), master title.
* Calls / bait / glassing count for a shot within 5 / 5 / 10 minutes and only when they were **yours**.
* **Carry-over (migration)** once per hunter at login: seen → sighting milestone, photographed → camera milestone, taken
  → first-take milestone, heaviest wildlife → weight milestone (rewards paid, one note). Deer antler points were never
  recorded, so deer quality tiers start fresh. Existing checklist entries are untouched (172 entries now).

## 4. Files
New: `src/.../hunts/*` (17 classes), `src/.../hunts/client/*` (3), `src/.../journal/HuntBridge.java`,
`src/.../expedition/CampaignProgress.java` (copied from dec62g8, one hook), assets: `textures/gui/journal/hunts_icons.png`
(16 original icons, journal px style), `textures/item/duck_call.png`, `textures/block/mallard_decoy.png`,
`models/{block,item}/mallard_decoy.json`, `models/item/duck_call.json`, `blockstates/mallard_decoy.json`,
`sounds/hunts/duck_call_{0,1,2}.ogg`; data: `recipe/{duck_call,mallard_decoy}.json`, unlock advancements,
`loot_table/blocks/mallard_decoy.json`, 6 assignment JSONs; fragments `lang/en_us.json/hunts.json` (295 keys,
`tools/hunts/lang_en.py`), `sounds.json/hunts.json`. Tools: `tools/hunts/{harness/, audit.py, mock_card.py, icons.py,
art.py, preview_model.py, duck_call.py, lang_en.py}`. Previews: `docs/ws/hunts/*.png`.

**Shared-file hook lines (all marked `// [hunts]`):**
* `journal/JournalHooks.java`: deerHit after `data.put(HIT, hit);` →
  `HuntHooks.hit(p, deer, projectile, vital, calm < 0.3F, hit.getDouble("d"));` · wildlifeHit after `data.put(HIT, h);` →
  `HuntHooks.hit(p, mob, projectile, vital, calm, h.getDouble("d"));` · deerHarvest before `campmates(...)` →
  `HuntHooks.deerTaken(hunter, deer, region, shotDistance, recovered && trailM >= 5 ? trailM : 0);` · wildlifeDeath before
  `campmates(...)` → `HuntHooks.wildlifeTaken(p, mob, zone, h.getDouble("d"), ran && trailM >= 5 && found ? trailM : 0, kg);` ·
  trailcamPhoto after the final `dirty` → `HuntHooks.photo(level, owner, subjects);`.
* `journal/JournalEvents.java` sightings: `HuntHooks.seen(p, a, sp, Math.sqrt(d2));` after a new sighting.
* `journal/Checklist.java`: enum `HUNTS("hunts", "icon:hunting")`; end of the static block: one loop adding the 85
  `HuntBook.milestones()` as entries (species set, icon `hunt:<name>`).
* `journal/client/JournalClient.java`: checkTitle / checkHint use the entry's own keys for HUNTS.
* `journal/client/JournalIcons.java` drawRef: `"hunt:<name>"` → `HuntIcons.draw`.
* `client/JournalScreen.java`: static `huntSpecies`; species(): open card → `huntCard(...)`, intro line, pips + click on
  each row, name width minus the pips; checklist(): species header rows in HUNTS; entryIcon(): HUNTS icon; two new private
  methods `huntCard`, `huntHeader`.
* `client/ExpeditionScreen.java`: howItCounts / missionIcon for `hunt:` events; the board loop iterates
  `HuntContracts.board(month, half)` with a note line.
* `expedition/Campaign.java`: `CONTRACTS = withSpecies(List.of(...5 standing...))` (appends `HuntContracts.MISSIONS`).
* `expedition/CampaignProgress.java` accept(): `&& HuntHooks.contractOffered(var1)`.
* `progression/Assignment.java`: record field `String hunt` + `Codec.string(0, 200).optionalFieldOf("hunt", "")`;
  instruction() → `HuntAssignments.instruction` when set. `progression/AssignmentService.java`: harvest() ignores deer
  harvests while a hunt assignment runs; new `hunt(player, animal, tags)`.
* `client/AssignmentScreen.java` (one line): the "how it works" text for hunt assignments (`hunts.frontierhunts.assign.how`).
* `tracking/hound/TrackingHound.java`: `public UUID quarry()` accessor.
* `HuntConfig.java`: `var1.push("hunts"); HuntsConfig.server(var1); var1.pop();` after academy.

## 5. Config (server `[hunts]`) / commands
`enabled` (true), `lures` (true: predator call, bait piles, duck call/decoys), `rewards` (true: tokens + gear),
`tokenMultiplier` (1.0), `announceMasters` (true). Not on the settings screen (server options).
`/frontierhunts hunts` (your progress) · op: `hunts test <targets> <milestone>` (feeds the simplest real event that
meets it through the tracker: rewards, toast, notes, card) · `hunts context` (what the hunts read about you and the
animal you look at: stand/blind/camo/on foot, called/glassed/bait/decoy/hound/rut/snow/water/herd) · `hunts call
predator|duck` · `hunts bait` · `hunts reset <targets>`.
**The journal only counts survival/adventure players** (creative needs `[journal] countCreative=true`).

## 6. IN-GAME TEST SCRIPT
Setup: new survival world (or `/gamemode survival`), op. `/give @s frontierhunts:ridgeline_rifle`, `/give @s
frontierhunts:reserve_308 64`, `/give @s frontierhunts:binoculars`, `/give @s frontierhunts:predator_call`,
`/give @s frontierhunts:bait 8`, `/give @s frontierhunts:duck_call`, `/give @s frontierhunts:mallard_decoy 8`,
`/give @s frontierhunts:pump_shotgun`, `/give @s frontierhunts:shotgun_shell 64`, `/give @s frontierhunts:grunt_tube`,
`/give @s frontierhunts:trail_camera`, `/give @s frontierhunts:skinning_tool`, `/give @s frontierhunts:tree_stand`.
1. **Journal**: J → Species: intro line, 5 grey pips on every row; click *Elk* → hunt card (back / prev / next buttons,
   track, tagline, pips, "Master title: Elk Caller", record line, How it's hunted, Where, Season contract, 5 milestone
   cards with rule, Needs, rewards). Check GUI scale 2/3/4 and a 854×480 window: nothing overlaps (arrows shrink to ‹ ›).
   Checklist → chip "Hunts 0/85"; species headers; click one → its card. Hover a row → rule tooltip.
2. **Carry-over**: on a world with old journal progress (species seen/taken), first login → field note "Hunts opened: N
   milestones carried over", tokens up, chat "N hunt reward(s)… added to your pack".
3. **Test command**: `/frontierhunts hunts test @s hunt_elk_glass` → toast *CHECKLIST · Hunts / Glass the high parks*,
   +30 XP ticker, tokens +5, note. `... hunt_elk_called` → also 3× Scent Cover in the pack. `... hunt_elk_master` →
   gold medal pip, chat "[Journal] <you> mastered the Elk hunt - "Elk Caller"", card chip "★ Elk Caller", Rangefinder.
   `hunt_grouse_limit` → counter shows 3/3 "in one day". `/frontierhunts hunts reset @s` → all hunt progress gone.
4. **Glassing**: `/summon frontierhunts:elk ~ ~ ~110` on open ground, hold binoculars on it ~1 s → toast *Glass the high
   parks*. `/frontierhunts hunts context` looking at it → "glassed".
5. **Clean take / field dressing**: shoot the elk heart/lung → kill cam; skin it → *A clean elk* (+ reserve rounds).
6. **Deer call**: whitetail/elk in front, blow the grunt tube until "1 animal is coming", shoot it within 5 min, dress it →
   *Called in*. In the rut (`/season` to Oct–Nov for whitetail; `hunts context` shows "rut") from a tree stand, an 8+
   point buck, clean → *Rut hunter* (master).
7. **Predator call**: plains at dusk (`/time set 12500`), `/summon frontierhunts:coyote ~40 ~ ~`, crouch still, use the
   Predator Locator Call: the coyote trots in, swings downwind, stops 8-12 blocks out and looks; shoot it → *Called in*.
   Move/sprint toward it instead → it bolts. Repeat 3 times between dusk and dawn → *Night stand*.
8. **Bait**: forest, use Bait on the ground → message "Bait pile set…"; `/summon frontierhunts:black_bear ~30 ~ ~`,
   `/time set 13000`, back off 15 blocks and wait (≤ ~20 s checks): the bear walks in and feeds (head down), lingers;
   shoot it → *Over bait*; with a bow and a heart/lung hit → *Bowhunter's bear*.
9. **Ducks**: river/swamp, place 4-6 decoys on still water (they float; right-click one → "Decoy spread · n decoys"),
   hide 10+ blocks back, `/summon frontierhunts:duck ~30 ~5 ~` ×3, use the Duck Call → message with the decoy count;
   ducks lift off, fly in high, drop and land among the decoys and loaf. Stand up / walk at them → they flare off the
   water; shoot one in the air → *On the wing*; one over the decoys → *Over the spread*; 4 in one day → *Limit over the spread*.
10. **Grouse**: forest, walk at a grouse until it flushes → *Flush a grouse*; shoot it in the air with the shotgun →
    *On the wing*.
11. **Hound**: wound a boar (gut shot) at night, give the Hound Lead, aim at its blood + use → hound trails/bays; finish
    it → *Dogs at night* (master) + *Night hunt*.
12. **Charge**: `/summon frontierhunts:grizzly ~25 ~ ~`, wound it (leg/gut) → it charges; heart/lung shot inside 20 m →
    *Stand your ground*.
13. **Trail camera**: place a trail camera facing a trail with boars/whitetails; when it photographs one → *Hogs on
    camera* / *Whitetail on camera* (also when you are offline: the toast is skipped, the progress is kept).
14. **Contracts**: N → Contracts: line "Oct: species contracts posted now - …", 5 standing + up to 4 species cards with
    species track icons and "Counts a … " text; take one in season, meet it → payment ready. `/season` to a different
    half-month → the posted species change; an unposted species contract can't be taken (button sends, server refuses).
15. **Assignments**: K → Field work lists the 6 new assignments with plain objectives ("Take 2 coyotes or gray wolves that
    came to your call."); accept *Predator control*, call in and take 2 → READY → collect. A deer harvest does not count.
16. **Multiplayer** (dedicated + 2 clients): A's calls/bait/glassing never count for B; party members share species
    contracts; master chat line visible to both; restart keeps bait piles, decoy spread, queued rewards; log clean.

## 7. Known limits / for the coordinator
* Not launched in-game. Lure behaviour (approach paths, duck flight easing, flare) and the duck-call audio are reasoned
  and offline-checked only; if the call does not convince by ear, point `duck_call` in `sounds.json/hunts.json` at
  vanilla `minecraft:entity.parrot.ambient` with pitch < 1.
* The duck model has no flight animation of its own: flying ducks use the existing wing-flap of the wildlife models
  (airborne = flapping), so the glide/landing reads as flight but is not bespoke.
* Prints (and so the hound on prints) need the reserve gamerule; in ordinary worlds the hound trails blood.
* Contract titles/stories are English literals in `Campaign.Mission` like the five standing contracts; all UI text is in
  the lang fragment.
* Elk/moose "called" uses the Doe Bleat Call (cow call) / Grunt Tube (bull) — no separate bugle item exists.
