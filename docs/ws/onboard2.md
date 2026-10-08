# onboard2 — Archery last, Handbook Assignments tab, no first-join journal, seats

Branch `onboard2` (from master 644f37a). Compiles (`exit=0`), `build.py onboard2-test` builds,
`tools/check_jar.py <jar> . --base master` → **0 errors, 0 warnings**, `tools/academy/harness/run.sh` → **ALL PASS (215)**,
`tools/onboard2/run_harness.sh` → **SEATS ALL PASS (22)** + window check OK, `tools/recipe_audit.py <jar>` → **PASS**,
`tools/economy/value_model.py <jar> --repo .` → **0 outliers, 0 grid conflicts**, `tools/onboard2/icon_sheet.py` (gui_audit
renderer) → seat icons 11–14 px of 16, centred within 0.5 px. `tools/qa/server_smoke.sh` (port 25637, + setblock of every seat state) → **0 ERROR/WARN,
no exceptions, no client classes on the server, no crash**.

## 1. The Archery Range is the last course
- `academy/Course.java`: `CURRICULUM = {GLASSING, STALK, TRACKING, DRESSING, RANGE, ARCHERY}` (others keep their order;
  ordinals unchanged so saved records are safe). Assignments screen, dossier, journal checklist and `step()` all read it.
- `onboard/Handbook.java`: new walking order `ORDER`/`order()` (task ordinals = synced bits stay stable). ARCHERY task moved
  from step 2 to the end of step 8; ARROWS/TIPS no longer link the archery course; SHOT lesson practises on The Range
  (it lends a bow and has the 3D deer lanes). `next()`/`tasks()` and HandbookClient toasts use path order.
- Lang (`onboard.json` fragment): step 2 now points at a Shooting Target (recipe added to the step 2 page); step 8 gets
  p3 + title "Rifles, expeditions & archery"; archery brief/task texts say "last course".
- Academy harness: archery last, other order unchanged, every `step()`, no earlier task recommends archery, the walked
  path recommends archery only after every other course.

## 2. Handbook "Assignments" tab
`guide/client/HandbookScreen.java`: page 9 = Assignments, drawn as the last tab under a rule at the bottom of the left
column (custom icon `textures/gui/handbook/assignments_tab.png`, n/6 counter, soft gold pulse while courses remain).
Page: what assignments are, a dark card (n of 6 passed, segment bar, next/in-training course, button "Open Ranger
Assignments (K)"), all six courses in teaching order (number, icon, tagline, PASSED/NEXT/TRAINING, click opens that course),
the field assignment taken. Step 8 "Next" leads to it. Layout fits at the 427×240 minimum GUI.

## 3. First join = Handbook + Field Recurve Bow + 3 arrows
All first-join hooks checked: FieldSchool kit (`firstJoinItems`, already handbook/bow/3 arrows), Onboarding, Journal,
Assignment/Training/Camps/Hunts/Survival/Trail logins (no items), Expedition campaign kit (only by explicit request, and
blocked by `starter=true` set on first join). The one remaining grant was HuntService's starter **Hunter's Journal**:
`HuntConfig` key renamed `starterJournal`(true) → `issueJournalOnJoin` (default **false**, so existing config files pick up
the new default). Players who already got a journal keep it. Settings screen toggle relabelled "Journal on first join".

## 4. Seats (package `seating/`, new)
| Block | Use | Eye level (seated) |
|---|---|---|
| Stump Seat (log + leather) | outdoor, swivel | 1.50 |
| Camp Chair (4 canvas wall + 2 nuggets) | outdoor, back + arms | 1.50 |
| Trail Bench (4 planks + 2 sticks → 2) | outdoor, joins side by side | 1.50 |
| Blind Swivel Chair (3 wool, iron, 2 nuggets) | ground blind | 1.50 = window 1.20–1.80 centre |
| Tower Swivel Chair (3 wool, 2 iron, 2 nuggets) | tower blinds; auto *sunk* in the small tower, *raised* in the big one; sneak+use toggles the lift | 1.535 / 1.70 = window centres |

Windows measured from the real shapes (`tools/onboard2/blind_windows.py`: HubGroundBlind construction bands,
TowerBlindShapeData cabins); FS lookout decks: rail top 1.25, seated eye clears it. Tree/ladder stands keep their own seat.
Mechanics: invisible `SeatEntity` (exists only while occupied, no summon, saved with the rider on logout), right-click
empty-handed to sit (holding a block places it instead), sneak to stand, dismount searches clear floor (works in tight
cabins), body square to chairs with a back (head ±105°), swivel seats turn with you and the swivel top rotates with the body
(entity renderer; OCCUPIED block state shows only the base, self-healing block tick). Seated = still for deer perception,
"on foot" for hunts, a stable rifle rest. Vanilla + Ultra (`realistic_world`) textures; World tab after the Lodge Chair,
blind chairs also in Field Equipment after the blinds; axe-mineable; loot + recipe unlocks.
Existing seat fix: Lodge Chair (`expedition/TreeStandSeat.java`) — hunter no longer sinks 0.16 into the cushion / floats off
the backrest; body stays square to the chair (legs no longer swing through the arms).

## Shared-file hook lines (all marked `// [onboard2]`)
`HuntConfig` (issueJournalOnJoin), `client/FrontierSettingsScreen` (toggle label), `hunting/Whitetail` L1359,
`hunts/HuntContext.onFoot`, `rifle/RifleActions.stableMount` (+ `SeatEntity.isSeat`), `client/AssignmentScreen` (comment),
`guide/client/HandbookClient` (order), `academy/AcademyCommands` (message). Fragments: `lang/en_us.json/onboard2.json`,
`onboard.json` (edited), `tags/block/mineable/axe.json/onboard2.json`. `tools/economy/model.py` targets for the 5 seats.

## In-game test script
1. New world or a never-seen player name: inventory = Frontier Handbook, Field Recurve Bow,
   3 arrows. **No** Hunter's Journal / Expedition Guide appears after walking around a reserve for a minute.
2. Press **K**: Ranger Academy lists Glassing, Stalk, Tracking, Dressing, The Range, **The Archery Range last** ("course 6 of 6").
3. Open the Handbook (H): bottom of the left tabs, under a thin rule, **Assignments 0/6** (gold pulse). Click it: card
   "Next course: Glassing", six course rows, field-work row. Click a course row → Assignments screen with it selected.
   Step 2 page mentions the Shooting Target (recipe shown), step 8 ends with the Archery Range paragraph; "Next" on step 8 → Assignments.
4. `/frontierhunts academy begin archery` then `/frontierhunts academy complete`, leave: tab shows 1/6, Archery row PASSED.
5. `/give @s frontierhunts:log_stump_seat`, then `camp_chair`, `trail_bench 3`, `blind_chair`, `tower_chair`. Check creative World tab (after Lodge Chair) and Field Equipment (after the blinds): icons centred.
6. Place 3 trail benches in a row: they join into one bench. Right-click each seat: sit, F5 shows the sitting pose,
   look around/draw the bow, sneak → stand beside the seat (never inside it). Two players: second gets "already sitting".
7. Deploy a hub ground blind, place a Blind Swivel Chair inside, sit: eyes centred in the window band; turn — the chair top turns too.
8. Tower blinds (small and big): place the Tower Swivel Chair on the cabin floor, sit: eye level at the window middle
   (action bar on sneak+use: raised/lowered). Stand up: you land on the cabin floor.
9. Lodge Chair: sit — thighs on the cushion, back against the backrest, legs do not swing through the arms when looking around.
10. Break a chair while seated (other player): the sitter stands up; the swivel chair never stays "topless".
