# Workstream `onboard` — Frontier Handbook, new starter kit, the Archery Range, one learning path

Branch: `onboard` (from master 0bf1526). `tools/compile.sh` → exit=0. Jar `python3 tools/build.py onboard-test .` builds;
`tools/check_jar.py <jar> . --base master` → **0 errors, 0 warnings**. `tools/academy/harness/run.sh` → **ALL PASS (203 checks)**
(130 old + the Archery course in every flow test + archery scoring / walking-deer path / stand box / plot geometry + Handbook path
logic: one next step, step order, lesson ↔ course links both ways). `python3 tools/recipe_audit.py <jar>` → **PASS** (handbook has a
recipe and an unlock). Layout mocks (real Inter fonts): `docs/ws/onboard/handbook_*.png` (427×240 GUI 2, 640×360 GUI 3).
**Not run in-game here** (no client) — the test script below is still to do.

## What the player gets
1. **Starter kit (first join)**: exactly the **Frontier Handbook**, the **Field Recurve Bow** (`field_bow`, the simplest bow) and
   **3 Hunting Arrows** (broadheads). Nothing else. Mara's expedition campaign is opened without its old kit (the ledger's `starter`
   flag is set, so the Expedition journal never offers "Collect starting equipment" and the first mission counts biomes at once).
   Old worlds: hunters whose Field School record says the kit was handled (anyone who joined since the guide) get nothing again;
   veterans (>30 min played or old expedition kit claimed) get nothing. Config `[fieldSchool] firstJoinItems`
   (new key, so existing server configs pick up the new kit; old `starterItems` / `issueExpeditionEquipment` are retired).
2. **Frontier Handbook** (item, `book + feather`, stacks to 1; right-click or **H**): Hunter's Journal notebook / Field School look.
   *Start here*: welcome line, the **NEXT STEP** card (one task, "Show me how", "Open the lesson" when it is a lesson, "Practice: <course>"
   when an Academy course trains it), the 8 steps with badges and n/m ticks, the starter-pack note. Step pages: plain text, a plate
   where it helps (4 wind, 5 vitals, 6 harvest), **HOW TO MAKE IT** recipe cards read live from the client recipe manager (real item
   icons, tags cycle, counts, hover tooltips, station named: crafting grid / table / campfire / Clothing Table / "arrow + tip"),
   **DO THIS** tasks with tick boxes, how-to, buttons (lesson, practice course, two-click Skip / Undo skip for Handbook-only tasks),
   "Next: step n ›". Footer: **Journal (J) · Expedition (N) · Field School · Assignments (K)**. ←/→, wheel (sidebar wheel turns pages),
   PgUp/PgDn, Home, Esc/H close.
   | Step | Tasks (tick source, server) | Recipes shown | Practice |
   |---|---|---|---|
   | 1 Crafting table | crafted/placed crafting table (vanilla stats) | planks, sticks, crafting table | – |
   | 2 Arrows | crafted field/primitive arrows · passed **The Archery Range** (or a bow kill) | primitive arrow, hunting arrow, field bow | Archery Range |
   | 3 Arrow tips | Bow Tuning Rack crafted/placed · a tip crafted or a fitted arrow carried | rack, field point, broadhead, arrow+tip | Archery Range |
   | 4 Find game | Field School 1-4 (wind, sign, glass, stalk) | wind checker, binoculars | Stalk, Blood Trail, Glass & Call |
   | 5 First hunt | Field School 5-6 (shot, blood) | (vitals plate) | Archery Range, Blood Trail |
   | 6 Butcher & cook | Field School 7 · game eaten / cooked game (journal) | skinning knife, campfire, campfire venison | Field Dressing |
   | 7 Camp & furs | tent placed (journal `placed.tent` / stats) · fur clothing worn | tanning rack, clothing table, fur hat, canvas, pup tent | – |
   | 8 Rifles & expeditions | rifle carried (journal `gear.rifle`) · first expedition report | weapons workbench, rifle, expedition guide | The Range |
3. **One path everywhere**: the **top-left objective card** shows the Handbook's one next step (a lesson exactly as before, or a Handbook
   task with its real item, `HANDBOOK · STEP 2/8`, 8 step pips, "H · Handbook"); lesson toasts say "Next: <handbook next>"; the
   **Journal Home "Up next"** row is the Handbook step (opens the Handbook there); every **Field School lesson page** has a RANGER
   ACADEMY block ("Practise it on the range: <course>", passed/credit line, *Practice* and *Open the Handbook* buttons); every Academy
   **dossier course page** says which lessons passing completes. **Passing a course** (once home) completes its lessons
   (Archery/Range → 5, Glass & Call → 3, Stalk → 1+4, Blood Trail → 2+6, Dressing → 7) and ticks its journal checklist entry.
   Journal checklist (Field School category) gains `hb_table, hb_arrows, hb_bench, hb_tips` and `academy_<course>` ×6 (their own journal
   toast is suppressed: the Handbook toast / academy result card already says it). Handbook toasts (vanilla stack, item icon,
   "Next: …") for each newly done Handbook-only task. Welcome card: "Open the Handbook" / "Skip, I know the ropes"; texts describe the new kit.
4. **The Archery Range** (6th Academy course, taught first; ordinal appended so saved home records stay valid; `Course.step()` /
   `curriculum()` give the teaching order used by the dossier, title card and HUD). Plot (`ArcheryKit`, built once per slot, version 1):
   shooting deck with slab roof (beam at the back, nothing over the arrow path), stone shooting line, the mod's shooting targets at
   **10/20/30/40 yd** (9/18/27/37 m) with yardage boards, **3D deer** (NoAI whitetails: broadside 15 yd, quartering-away 25 yd), a
   **walking deer** on a worn trail at ~20 yd (1.3 m/s, pauses and turns at both ends, real walk animation from its motion), a **ladder
   tree stand** (floor 5 m up) with its own deer below at 14 yd, three **wind flags** turned by the live wind, berm, pine belts, boundary.
   Lent kit: Field Recurve Bow, Compound Bow, 32 field-point arrows, 16 broadhead arrows, rangefinder, wind checker (tips via `id*n#tip`).
   Objectives: paper 4 (one per distance; 10/20 yd inside the blue), 3D deer 2, walking deer 1, stand 1. Scoring per shot (gold 10 … white 2,
   clean deer 10) with running score in the call-outs; paper/3D/walking count only from behind the line, the stand deer only from the stand
   (else coached + reset). Heart/lung = drop + kill cam; anything else called out (gut/liver/leg/neck…, "lead a walking deer", "from above
   aim lower") and the deer resets. One-time "broadheads tear targets" note. Rewards 70 Ranger XP / 110 Marksmanship XP (no certification).
   Dossier: own painted plate `plate_archery.png` (archer at full draw, 3D foam deer with arrows in the vitals, bag target, ladder stand,
   wind flag; `tools/academy/gen_art.py . archery`) and atlas icon 24 (bow + nocked arrow, `tools/academy/gen_icons.py`).
   Only public bow/arrow APIs are used (TargetFace hits, Whitetail shot region, ArrowTip.with/byId); no bow rendering file touched.

## Server validation / cost
`handbook_action` (sync / skip / unskip): own player only, 4-tick rate limit, task id range-checked, lessons can't be skipped here
(Field School's own two-click skip). Task state is computed on the server only; evaluation every 2 s per online hunter while unfinished
(+ right after crafting/smelting/placing; skipped in the training grounds), a few map lookups + ≤2 inventory scans. State payload 4 varints.

## Files
New: `src/.../onboard/{Handbook,HandbookItem,OnboardContent,OnboardData,OnboardNetwork,Onboarding}.java`,
`src/.../academy/ArcheryKit.java`, `src/.../guide/client/{HandbookClient,HandbookScreen,HandbookUi}.java`,
`patch/assets/frontierhunts/{models/item/frontier_handbook.json,textures/item/frontier_handbook.png,textures/gui/academy/plate_archery.png}`,
`patch/data/frontierhunts/{recipe/frontier_handbook.json,advancement/recipes/unlock/frontier_handbook.json}`, lang fragment
`patch/_merge/.../en_us.json/onboard.json` (202 keys, `python3 tools/onboard/lang_en.py .`), tools `tools/onboard/{lang_en,item_icon,mock_handbook}.py`.
Regenerated: `textures/gui/academy/icons.png` (+ archery cell). Tools edited: `tools/academy/{gen_art,gen_icons}.py`, `tools/guide/scenery.py`
(`stand_draw` pose), academy harness.
Edited (all marked `// [onboard]`):
* `academy/Course.java` ARCHERY + `curriculum()`/`step()`; `CourseKit.of` case; `HomeState.stack` `#tip` suffix; `AcademyCommands` text;
  `TrainingService.end`: `if (result == PASSED) Onboarding.coursePassed(p, s.course);`
* `academy/client/DossierArt.Icon.ARCHERY`; `academy/client/TrainingHud` eyebrow/kicker use `c.step()`.
* `client/AssignmentScreen`: curriculum order, archery icon, `focus(Course)`, kicker `c.step()`, "passing also completes" line.
* `client/JournalScreen` Home "Up next" = Handbook next (+ rebuild on handbook change); `journal/client/JournalClient` no toast for `hb_*`/`academy_*`.
* `guide/FieldSchool.giveKit` → `Onboarding.giveStarterKit(p)`; `guide/GuideConfig` `firstJoinItems` (default kit), retired 2 keys.
* `guide/client/GuideClient` (H opens the Handbook; lesson toast "Next"), `ObjectiveCard` (Handbook task card), `WelcomeScreen`
  (Begin opens the Handbook), `FieldSchoolScreen` (practice block + buttons), `GuideIcons` (`item:<id>` icons).

## Commands
`/frontierhunts handbook` (status: steps done, next) · `handbook open` · `handbook unskip [targets]` (op for targets) ·
`/frontierhunts academy begin archery` · existing `academy complete` / `tutorial reset|skip`.

## IN-GAME TEST SCRIPT
Setup: NEW survival world (fresh `serverconfig`), op.
1. **Kit**: join. Inventory = Frontier Handbook, Field Recurve Bow, 3 Hunting Arrows, nothing else. ~6 s later the welcome card:
   text names that kit; **Open the Handbook** → card closes, Handbook opens on *Start here*. N (Expedition): no "Collect starting
   equipment"; NEXT = mission I (biomes).
2. **Handbook look**: GUI scale 1-4, 854×480 window: sidebar Start here + 8 steps, NEXT STEP card "Make a crafting table" (step 1),
   steps list with 0/1, 0/2…, footer 4 buttons fit. Step 1-8 pages: recipe cards show real icons (planks, sticks, crafting table, arrows,
   bow, rack, tips, arrow+field point, wind checker, binoculars, skinning knife, campfire, venison → cooked venison, tanning rack,
   clothing table, fur hat (Clothing Table), canvas, pup tent, weapons workbench, rifle, expedition guide). Tag slots cycle each second;
   hover → item tooltip. Scroll, ←/→, sidebar wheel, Esc, H. Check every footer button opens its screen.
3. **Card**: top-left card shows `HANDBOOK · STEP 1/8` "Craft a crafting table" with the table icon; craft one → toast
   "HANDBOOK · STEP 1 OF 8 · Craft a crafting table · Next: Make arrows" + bell, card moves to step 2. Journal (J) Home → Up next
   = "Make arrows · Frontier Handbook · step 2 of 8" → click opens the Handbook on step 2. Checklist → Field School shows "A crafting table" ticked.
4. **Arrows/tips**: craft primitive arrows (flint/stick/feather) → step 2 task 1 ticks; NEXT = "Pass the Archery Range" with
   *Practice: The Archery Range*. `/give @s frontierhunts:field_point 4`, put an arrow + a field point in the crafting grid → step 3 tips ticks.
5. **Skip**: on step 3 "Build a Bow Tuning Rack": *Skip* → "Click again to skip" → click → Skipped, next moves on; *Undo skip* restores.
6. **Archery Range**: Handbook *Practice* (or K) → dossier opens with **The Archery Range** selected, first in the Ranger Academy group,
   painted plate, "course 1 of 6", objectives, kit, "Passing it also completes Field School: Place the shot". *Begin training* →
   golden-hour range, title card, HUD with 4 objectives + wind. Lent kit has lore "Range issue", arrows show field point / broadhead tips.
   Shoot paper at 10/20/30/40 yd from the line (calls with ring, +points, score; 10 yd outside blue → "too far out"); broadhead on paper →
   one reminder. Step in front of the line → red warning, no credit. 3D deer 15/25 yd: gut → "Gut…" + reset ~3.5 s; heart/lung → kill
   cam, drop, tick. Walking deer walks the trail, turns at the ends with a pause; vital hit ticks. Shoot the stand deer from the ground →
   "climb the ladder", reset; climb the ladder (Adventure mode), shoot from the platform → tick. All 4 → COURSE PASSED → home, everything
   restored; result card; then: Field School toast lesson 5 complete, Handbook toast "Pass the Archery Range", journal checklist
   "Academy: The Archery Range" ticked (no extra journal toast). `/frontierhunts academy begin archery` + `academy complete` for a quick pass.
7. **Other courses credit lessons**: pass The Stalk (`academy begin stalk`, `academy complete`) → lessons 1 and 4 done; Blood Trail → 2 and 6;
   Glass & Call → 3; Field Dressing → 7. Field School (H → step 4 → Open the lesson): each lesson page ends with the RANGER ACADEMY block,
   *Practice: …* opens the dossier on that course, *Open the Handbook* returns.
8. **Later steps**: eat cooked venison → step 6 cook; place a tent → step 7 tent; wear a fur hat → furs; carry a rifle → step 8 rifle;
   file the first expedition report → report. All done → card disappears, Start here says "Every step is done".
9. **Old world**: load a world where you already got the old kit (Field School record exists): nothing new is given; the Handbook
   can be crafted (book + feather) and shows your existing progress ticked (stats/journal), no toast flood on login.
10. **Multiplayer** (dedicated + 2 clients): each player's kit, Handbook state, skips and card are their own; both can run the Archery
    Range at once (different slots); a client cannot skip lessons via the handbook packet; server log has no handbook warnings.
11. **Config**: `firstJoinItems=["minecraft:bread*8"]` on a fresh world → bread only. `fieldSchool=false` → no kit/welcome/card;
    Handbook says lessons tick themselves.

## Known limits / for the coordinator
* Not launched in-game; Handbook layout checked with the offline mock only; plot built from code (not seen).
* H now opens the Handbook (Field School is reached from it, the Journal and the footer); key id unchanged (`key.frontierhunts.field_school`).
* Handbook ticks for craft-only tasks come from vanilla statistics, so items made at mod workbenches (tips/racks fitted at a station)
  tick through the inventory checks instead; creative players get ticks but no journal counters (journal ignores creative by config).
* Old configs keep a now-unused `starterItems` line until NeoForge rewrites the file (harmless).
