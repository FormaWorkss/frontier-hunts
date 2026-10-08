# Workstream `guide` — Field School: the new-player guide (welcome card, 8-lesson course, field notes)

Branch: `guide`. Compile `tools/compile.sh` → exit=0. Jar `python3 tools/build.py guide-test /home/claude/work/guide` builds;
`tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Offline harness `tools/guide/harness/GuideHarness.java`
→ ALL PASS (lesson masks, saved-record round trip + clamping, payload codecs). Layout mock `tools/guide/mock_screen.py`
(Inter fonts from the jar) reviewed. **Not run in-game here** (no client) — everything under the test script is still to do.

## What it does (player flow)
1. **First join** — after ~6 s in the world a short welcome card opens (painted dawn banner, two lines of text, starter-gear
   note): **Begin Field School** / **Skip, I know the ropes** (Esc = Begin). New hunters get a starter kit at login:
   the Expedition Guide's existing one-time starting equipment (guide, field bow, 24 arrows, field knife, Contour Skinning
   Knife, float rod, expedition board — issued through `ExpeditionService.request(8)`, so it is never doubled) plus a
   **Wind Checker** and **Binoculars** (configurable list). Veterans (>30 min played, or already claimed the expedition kit)
   get the card but no kit. **[onboard] superseded:** the first-join kit is now the Frontier Handbook, the Field Recurve Bow and 3
   arrows only (`firstJoinItems`), the welcome card opens the Handbook and H opens the Handbook — see docs/ws/onboard.md.
2. **Field School** — 7 lessons + 1 optional, per player, saved server-side (`data/frontierhunts_fieldschool.dat`), counted
   in any order; the card shows the first one missing:
   | # | Lesson | Completes when (server) |
   |---|---|---|
   | 1 | Read the wind | Wind Checker used, or held 3 s (scent cone) |
   | 2 | Read the sign | a print/blood/sign inspected (TrailService.inspect hook) or a rub/scrape (DeerSign) right-clicked |
   | 3 | Glass before you walk | any binoculars/rangefinder used, or a Trail Camera placed |
   | 4 | Close the distance | within 20 blocks of a calm (alertness < 0.3) deer for 3 s; being winded/spotted resets + coaches |
   | 5 | Place the shot | the player's projectile hits a Whitetail-family or 2026 wildlife animal; toast says "Clean hit" (dropped) or "It ran" |
   | 6 | Follow the blood | 2 blood marks inspected, or hound put on blood, or walking up on the animal that ran in 5 (≥10 blocks from the hit) |
   | 7 | Field-dress & take the trophy | carcass skinned (Whitetail.harvest hook) then the Game Trophy is in the inventory |
   | 8 | Field tips (optional) | opening its guide page (camps & tents, ATV & fuel, seasons & weather, crouch / prone + bipod) |
   Graduation after 1-7 (toast). Skippable: welcome card, "Skip Field School" (two-click confirm) in the guide sidebar,
   "Skip this lesson" (two-click) per page, `/frontierhunts tutorial skip`. Resume from the guide.
3. **HUD objective card** (top-left, 182 px): eyebrow `FIELD SCHOOL 4/7`, 7 progress pips, lesson icon + title; opens to
   the one-line objective + `H · guide` + `1 / 3` for 14 s after a change, or while holding the lesson's tool, then folds to
   two lines. Hidden while any screen is open, F1/F3, using an item (aiming, drawing, glassing), kill cam, cinematics,
   optics; fades/slides (Reduced motion = instant). **Toasts** (vanilla toast stack, journal palette, item icon, time bar):
   lesson complete (+ "Next: …", soft bell), coaching hints (winded / spotted / clean hit / it ran / skinned / good read,
   max one per 20 s), graduation.
4. **Guide** (`H`, or the new **Field School** entry drawn in the Hunter's Journal and Expedition Guide sidebars, with
   `3/7`) — Hunter's Journal notebook look: sidebar of 8 lessons with done/current badges, illustrated plate per lesson
   (original ink art, labels drawn from lang at anchors with leader lines), short plain-language paragraphs, an
   "IN THE FIELD" box (objective + live status/progress). ←/→ pages, scroll, Esc/H closes (back to the Journal/Guide).
   Plates: wind & scent cone (top-down), track ID chart (8 species), rub/scrape, glassing + trail camera, stalking side
   view, deer vitals with aim point, blood types + trail/bed, field-dressing + trophy, 4 tip vignettes, welcome banner
   (`tools/guide/gen_art.py` + `fieldart.py`, `whitetail.py`, `scenery.py`, `tracks.py`; redrawn by the `fieldbook` workstream, see docs/ws/fieldbook.md).
5. **Field notes** (one-time, throttled: ≥90 s apart by default, none in the first 30 s after joining; disable per client
   in Settings → Interface → "Field notes", or per server): first deer in view (≤40 blocks, LOS), first blood within 10
   blocks, first time winded (scent bust hook), first blizzard (severity > 0.35), first season change, first predator
   sign inspected / predator kill site within 24 blocks. A note that is throttled is not marked seen, so it comes later.

Cost: event hooks + one staggered check per player every 20 ticks only while a lesson needs it (stalk: one AABB entity
query within 20 blocks), tips every 40 ticks only until all six were seen (≤3 LOS raycasts, one TrailStore cell lookup,
weather every 6 s, season every 10 s). Payloads: State (4 varints) on change; Notice (6 bytes); Action (2 bytes, server
rate-limited 4 ticks, validated).

## Config
Server (`frontierhunts-server.toml`, `[fieldSchool]`): `fieldSchool=true` (welcome, course, card, kit), `contextTips=true`,
`tipCooldownSeconds=90`, `starterKit=true`, `issueExpeditionEquipment=true`, `starterItems=["frontierhunts:wind_checker","frontierhunts:binoculars"]`
(`id` or `id*count`). Client (`[fieldSchool]`): `objectiveCard=true`, `fieldNotes=true` — both also in Settings → Interface.

## Commands
`/frontierhunts tutorial` (status) · `/frontierhunts tutorial reset [targets]` (course, notes and welcome card back to new;
kit never re-issued; targets need op) · `/frontierhunts tutorial skip [targets]`.

## Files
New: `src/.../guide/{FieldSchool,FieldSchoolData,GuideConfig,GuideNetwork,Lesson,Tip}.java`,
`src/.../guide/client/{GuideClient,ObjectiveCard,GuideToast,FieldSchoolScreen,WelcomeScreen,GuideArt,GuideUi}.java`,
`patch/assets/frontierhunts/textures/gui/field_school/*.png` (10 plates), lang fragment
`patch/_merge/assets/frontierhunts/lang/en_us.json/guide.json` (191 keys, generated by `tools/guide/lang_en.py`), tools under `tools/guide/`.
Shared-file hook lines (all `// [guide]`):
* `HuntConfig.java`: `var1.push("fieldSchool"); GuideConfig.server(var1); var1.pop();` after the atvFuel line; `var2.push("fieldSchool"); GuideConfig.client(var2); var2.pop();` after the atvGrime line.
* `hunting/Whitetail.java` perceive(): first line inside `if (var34 < 0.7F && var36 >= 0.7F) {` →
  `FieldSchool.deerSpooked(this, var6, routineScent > routineOther || RoutineHooks.scentBusted(this));`; harvest(): after `ExpeditionService.record(var1, "harvest", …)` → `FieldSchool.harvested(var1);`
* `tracking/TrailService.java` inspect(): after `ExpeditionService.record(var0, "clue", …)` → `FieldSchool.markInspected(var0, var5);`
* `tracking/hound/HoundCommands.java` track(): after the "drops nose" message → `FieldSchool.houndTrack(p, m);`
* `client/FrontierSettingsScreen.java` INTERFACE: two `toggle(...)` rows after the kill cam rows (Field School card, Field notes).
Key: `key.frontierhunts.field_school`, default **H**, category Frontier Hunts.

## IN-GAME TEST SCRIPT
Setup: new survival world (`/gamerule frontierHunting true` for the wilderness HUD), `/time set 1000`.
1. **Welcome + kit**: join a fresh world. ~6 s later the welcome card (banner, text, gold starter-gear note). Inventory has
   Expedition Guide, field bow, 24 arrows, field knife, Contour Skinning Knife, float rod, expedition board, Wind Checker,
   Binoculars. Click **Begin** → card closes; top-left objective card appears ("Read the wind", open with objective, then
   folds after ~14 s). Open the Expedition Guide: "Collect starting equipment" is gone (already issued).
2. **Lesson 1**: hold the Wind Checker 3 s (scent cone lines) → toast "LESSON 1 COMPLETE · Read the wind · Next: Read the
   sign" + bell; card moves to lesson 2 with a gold pulse. `/frontierhunts tutorial reset` then right-click the checker → also completes.
3. **Lesson 2**: `/summon frontierhunts:whitetail ~6 ~ ~` on dirt/snow; let it walk; look at a print, press Use → completes.
4. **Lesson 3**: hold Use with Binoculars → completes (or `/give @s frontierhunts:trail_camera` and place it).
5. **Lesson 4**: find/summon a deer ~40 blocks away. Walk upright toward it downwind → "Spotted!"/"Winded!" hint toast
   (and the one-time "You were winded" field note if it smelled you). Crouch into the wind to ≤20 blocks while it stays
   calm 3 s → completes (card shows `1 / 3`… progress while it counts).
6. **Lesson 5**: shoot a deer in the gut/leg with the bow → toast lesson 5 + "It ran". (Heart/lung → kill cam + "Clean hit".)
7. **Lesson 6**: inspect one blood mark → "Good read" hint, card `1 / 2`; a second → completes. Alternative: walk up on the
   deer after it dies ≥10 blocks away, or put a hound (`/give @s frontierhunts:hound_lead`) on the blood.
8. **Lesson 7**: right-click the downed deer with the Contour Skinning Knife and stay → "Skinned · pick up the trophy";
   pick it up → lesson 7 + "FIELD SCHOOL · Course complete" toast; card disappears.
9. **Guide**: press H. Check every page: plate renders smooth (not pixelated) with readable labels and leader lines,
   paragraphs wrap, IN THE FIELD box shows Done/progress, sidebar ticks, ←/→, scroll, Esc. Open page 8 → lesson 8 toast.
   Hunter's Journal (J) and Expedition Guide (N): a "Field School 7/7" entry in the sidebar under the tabs opens the
   guide; Esc returns to that screen. Try GUI scale 1-4 and a small window (entry falls back next to the X if it does not fit).
10. **Skip paths**: `/frontierhunts tutorial reset` → welcome card again → **Skip** → no card, guide shows PAUSED +
    "Resume Field School". In the guide: "Skip this lesson" needs two clicks. `/frontierhunts tutorial skip`.
11. **Field notes**: (reset first) first deer in view → "Deer spotted"; blood within 10 blocks → "Blood trail" (≥90 s later);
    snowy biome `/weather rain` + `/frontierweather blizzard` → "Blizzard"; `/season set winter` (from fall) → "The season
    has turned · Winter has come…"; inspect wolf/cougar prints → "Predator sign". Settings → Interface → Field notes off →
    no notes; Field School card off → no card.
12. **Multiplayer** (dedicated server + 2 clients): each player has own welcome, kit, progress, card and toasts; player B's
    actions never complete A's lessons; `/frontierhunts tutorial reset B` (op) resets only B. Server log: no
    `field school` warnings. Relog keeps progress.
13. **Config**: `fieldSchool=false` → no welcome/card/kit/lesson toasts (guide still readable, says turned off);
    `starterItems=["minecraft:bread*8"]` on a fresh world → bread instead of checker/binoculars.

## Known limits / for the coordinator
* Not launched in-game; layout verified only by the offline mock. Toast and card are vanilla-GUI only (shader-independent).
* Predators in the mod rarely kill game, so the predator note mostly comes from inspecting predator prints.
* The "steady" tip and lesson 5 teach crouch, then prone (rifle workstream key, default Z, named live from the key binding): no sway, bipod unfolds. [integ3]
* Lesson 4 counts in creative too (deer ignore creative players).
* Mara's Campaign "Read the ground"/"The first harvest" missions overlap lessons 2/7 by design; both progress independently.
