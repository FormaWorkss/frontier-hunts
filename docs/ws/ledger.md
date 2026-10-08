# Workstream `ledger` — journal icon art + the Expedition journal rebuilt

Branch: `ledger`. `tools/compile.sh` → exit=0 (797 classes). Jar `python3 tools/build.py ledger-test /home/claude/work/ledger` builds;
`tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. **Not run in-game here** (no client): layout checked with offline
mocks (real Inter fonts + the real icon atlas) at 854×480 GUI 2 (427×240), 1920×1080 GUI 4 (480×270) and 1920×1080 GUI 2.

## 1. Original pixel-art icons (user: "icons … suck, need to be their own, not original MC")
* `tools/ledger/px.py` + `tools/ledger/icons.py`: 70 icons drawn procedurally at 32×32 — hard-edged shapes at pixel centres, banded
  upper-left light, rim light/core shadow, dark separators between parts, 1 px warm-dark outline, one limited warm palette
  (brass, leather, wood, paper, forest green, blood, steel, slate, flame, bone, earths). A brightened copy of every icon (hover / selected)
  sits in the lower half of the atlas `patch/assets/frontierhunts/textures/gui/journal/icons.png` (512×512).
  `python3 tools/ledger/icons.py . docs/ws/ledger/icons` regenerates the atlas, rewrites the `NAMES` array in `JournalIcons.java` and writes the sheets.
* Set: journal tabs (cabin **home**, clipboard+pencil **checklist**, crossed rifle/arrow + star **skills**, whitetail head **species**,
  antler plaque **records**, leather notebook **notes**, folded map + pin **reserve**, campfire + roast haunch **survival**); expedition tabs
  (letter + wax seal, pinned contract, field pack, target, log lodge, open ledger, calendar + maple leaf); skills (reticle + heart, eye in grass,
  hoofprints + blood, skinning knife, hatchet in stump); 6 rank medals (bronze sprout / bronze hatchet / silver hoof / silver compass /
  gold antlers / gold star in laurel, ribbons per rank); checklist categories (bow, pack, landscape roundel, tent); entries (blood drops,
  rubbed sapling, snowflake, thermometer, boot, rosette, predator track, deer skull, antlers, leaf, quill, binoculars, wind checker, trout);
  **17 species tracks** pressed into ground discs (cloven hooves with dewclaws where real, canid/felid/bear/bird prints; polar bear on snow,
  cheetah/lion/pronghorn on sand); UI (Field School campaign hat, pine token, signpost, lock, star, party, map pin, pocket watch, ranger badge).
* `journal/client/JournalIcons` draws them: nearest filtering when the on-screen size is a whole multiple of 32 px (16 px at GUI 2/4,
  32 px always) so pixels stay crisp, bilinear otherwise; `drawLifted` = brightened art raised 1 px over a soft shadow (hover/selected);
  `drawRef("icon:<name>" | item id)` so checklist gear rows still show the real item.
* Where they replace vanilla/item icons: journal sidebar tabs (+ the Survival page tab), Field School button + token line, Home rank emblem
  (32 px medal), Up-next rows, checklist rows + new category icons on each section header, Field School lesson rows (own icons per lesson),
  skill cards, species log (track per species, unknown species faded), field notes (icon per note: antlers harvest, tick checklist, star rank/perk,
  snowflake blizzard, antlers shed, skull bones, predator kill site, rosette event, plaque season summary, leaf season, quill other),
  all journal toasts (rank toast shows the 24 px medal). No `minecraft:` icon remains in Checklist/Rank/Skill (gear entries keep the mod's items).

## 2. Expedition journal ("Frontier Expeditions", N / Expedition Guide item / `/expedition`) — rebuilt, audited
`client/ExpeditionScreen.java` replaced (same class, `new ExpeditionScreen()`, `refresh()` kept). Journal look (`JournalUi.notebook`,
palette, buttons), row layout, smooth scroll, drag bar, keys 1-7 / Tab / PgUp/PgDn, tooltips explaining every disabled button.
* **Header on every page**: page icon + title and a **NEXT** line (icon, objective, `n / m`, contract timer) — click jumps to the page that does
  it; hover shows how it counts. Priority: collect starting kit → collect contract payment → file a finished report → running contract →
  current mission → "take a contract". Gold dot on Campaign/Contracts tabs when something is ready to collect.
* **Campaign**: Next-objective card (eyebrow, title, plain "how it counts" text, progress bar, the button that completes it: *Collect starting
  equipment* / *File report · N tokens* / *Collect payment* / *Open contracts*), chapter line + 9 report pips grouped by chapter, current
  report with reward, Mara's last entry, nearby party.
* **Contracts**: running contract card (state chip, story + how it counts, progress bar, live countdown bar turning red under 3 min,
  *Collect payment*, *Abandon/Forfeit* with click-again confirm); board of 5 cards with icon, reward, how it counts, *Take contract* (reason when blocked).
* **Stores**: station status banner, item icon + name + pack size, price, *Buy* (disabled with reason: no station / tokens short / inventory full).
* **Training**: expedition XP bar to the next point, the 4 challenges with rank pips, task + perk, live progress/timer, start button with reason.
* **Lodge**: camp rank with the 4 station items (ticks), next upgrade + cost, party accept/leave (confirm), shared marker, lodge records table.
* **Ledger**: stats grid + filed-report timeline. **Seasons**: live reserve date and season from `SeasonClock` (progress through the season,
  days left), 12-month open-season chart per species (current month outlined), conservation tags with buy buttons.
* Sidebar bottom: Field School (own button; `GuideClient` overlay disabled for this screen), **Journal** and **Ranger** (Ranger Assignments,
  `AssignmentScreen.send(0,"",0)` — the academy screen itself untouched).
* **Server audit** (every button traced through `ExpeditionService.request` / `CampaignProgress` / `ReservePermits` / record call sites):
  all actions work as labelled. Problems were presentation, fixed client-side: (1) the first mission (visit 3 biomes) never counts until the
  starting kit is collected (`ExpeditionService.tick` gates on `starter`) — now the first Next objective with its button; (2) "drop a
  whitetail with a heart/lung shot" and harvest objectives only count on field-dressing — every objective now says exactly how it counts;
  (3) contract/training timers froze after the server's 60 s view window — countdowns tick locally and the screen polls a no-op action (17)
  every 10 s after 55 s (never within 1.5 s of a click, which the server's 3-tick rate limit would swallow); (4) purchases failed silently —
  buttons now disable with the reason (incl. full inventory); (5) the old calendar ignored `SeasonClock` — Seasons uses it live.
  No server file changed.

## Files
New: `journal/client/JournalIcons.java`, `patch/.../textures/gui/journal/icons.png`, `patch/_merge/.../en_us.json/ledger.json` (155 keys,
`python3 tools/ledger/lang_en.py .` regenerates from the `L("key","English")` calls), `tools/ledger/{px,icons,lang_en,mock_expedition}.py`.
Replaced: `client/ExpeditionScreen.java`. Changed (journal ws files, icon refs only): `client/JournalScreen.java` (tab/row/emblem/notes
icons, `entryIcon`/`noteIcon` public), `journal/client/{JournalToast,JournalClient}.java`, `journal/{Checklist,Rank,Skill}.java` (icon strings),
`tools/journal/mock_journal.py` (atlas icons). Shared hook: `guide/client/GuideClient.java` `layoutEntry` → `return false` for
ExpeditionScreen (`// [ledger]`).
Previews: `docs/ws/ledger/icons_4x.png` (labelled), `icons_1x2x.png` (normal + bright, 1x/2x on leather / paper / toast),
`journal_*.png` (journal at 1920×1080 GUI 4), `exp_854x480_*.png`, `exp_1080p_gui4_*.png`, `exp_1080p_gui2_training.png`.

## IN-GAME TEST SCRIPT
Setup: survival world, `/gamerule frontierHunting true`, `/give @s frontierhunts:expedition_guide`.
1. **J** (journal) at GUI scale 2, 3, 4 and a 854×480 window: sidebar tabs show the pixel icons (cabin, clipboard, crossed rifle, deer head,
   plaque, notebook, map, campfire for Survival); hovering/selecting lifts the icon 1 px and brightens it; crisp at GUI 2/4 (soft at 3 is expected).
   Home: rank medal in the emblem; Up next rows with icons. Checklist: category header icons; clean kills = reticle, recoveries = blood, rub =
   sapling, blizzard = snowflake, walk = boot; gear rows still show the items. Species: a track per species, unknown faded. Field notes: per-note icons.
2. `/frontierhunts journal xp @s woodcraft 400` → rank toast with the bronze hatchet medal; level/perk toasts show the skill icon; a checklist tick
   toast shows that entry's icon. `/frontierhunts journal summary @s` → plaque icon.
3. **N** (or use the Expedition Guide): Campaign page opens; header NEXT line visible on every tab; at 854×480 nothing overlaps (scroll if needed).
   Fresh hunter without the kit: NEXT = "Collect your starting equipment" → button gives the kit once; NEXT moves to "I · First light 0 / 3".
4. Walk into 3 biomes → bar fills (watch it update while open) → *File report · 25 tokens* → chat "Report filed", tokens +25, pips advance.
5. Contracts: *Take contract* on Camp provisions → card shows IN THE FIELD, 20:00 counting down live (leave the screen open > 1 min: still
   ticks and updates). Other *Take* buttons disabled with tooltip. Recover 2 whitetails → PAYMENT READY, gold dot on the tab → *Collect payment*.
   Take another, *Abandon contract* needs a second click within 4 s.
6. Stores: away from a station all *Buy* disabled ("Stand within 6 blocks…"); at a station with tokens, buying adds 12 rounds; full inventory → disabled "inventory full".
7. Training: with ≥100 expedition XP start Tracker → inspect 3 clues → rank pip fills. Lodge: upgrade at a station → station item ticked; `/expedition invite <B>`,
   B accepts on Lodge; *Leave party* confirm; *Share a marker here*. Seasons: date/season match `/season`; `/season month 10` → chart outline moves;
   at an expedition board *Buy 3 tags · 8* when in season. Ledger lists filed reports.
8. Sidebar: Field School opens the guide (Esc returns); **Journal** opens the Hunter's Journal; **Ranger** opens Ranger Assignments.
9. Dedicated server + 2 clients: each sees own state; party sharing works; no errors in log.

## Known limits / coordinator
* Not launched in-game. Icons at GUI scale 3 (16 px → 48 screen px) use bilinear filtering (1.5× of 32 px art).
* The poll uses the unused expedition action id 17 (server: no-op + snapshot). If another workstream gives 17 a meaning, change `ACTION_POLL`.
* Mission titles/stories still come from `Campaign` (English literals, unchanged); all new UI text is in the lang fragment.
