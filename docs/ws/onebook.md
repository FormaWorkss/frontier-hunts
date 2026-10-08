# onebook — the Frontier Handbook is the only book

Branch `onebook` (from master 6cbba9d). User: "keep all the contents of expedition guide and journal, just remove them where
the only book is the handbook … you can still access those from inside the handbook".

Checks: `tools/compile.sh` exit=0 · `build.py onebook-test` builds · `check_jar.py <jar> . --base master` **0 errors, 0 warnings** ·
`recipe_audit.py` **PASS** (0 unobtainable, 28 documented exceptions) · academy harness **ALL PASS (215)** · journal harness
**ALL PASS** · licence harness **ALL PASS (219)** · hunts harness **ALL PASS (1506)** + `hunts/audit.py` **PASS** ·
`tools/gui/run.sh` layout=0 sounds=0 · `economy/value_model.py` 0 outliers, 0 grid conflicts · server smoke (port 25731)
**clean** (0 ERROR/WARN, no exceptions, no client classes, no crash) + a fake-player run: `/give` journal + guide → after 60
ticks 0 journals, 0 guides, 1 Handbook; `/frontier journal_replacement` → still 1 Handbook, 0 journals.

## What changed
1. **One book item.** Hunter's Journal (`hunter_journal`) and Expedition Guide (`expedition_guide`) are legacy: still
   registered (old chests/inventories load), named "(old)", but **no recipe** (`patch/_remove/onebook.txt` + patch recipe and
   unlock advancements deleted), **no loot** (expedition_supplies, living/outfitter; `gen_loot.py` updated), **not in any
   creative tab** (HuntContent lists the Handbook in their place; the Field Equipment tab icon is the Handbook), hidden from
   recipe viewers (`c:hidden_from_recipe_viewers`). There is no separate Field School item (it was always a screen).
2. **Old stacks become the Handbook** (`onebook/OneBook.java`, chosen over keeping them: one book, nothing to explain).
   On login and every 2 s a legacy book in a hunter's inventory is replaced by a Frontier Handbook, or removed when the hunter
   already has one; one chat line per session says so. This also catches old grant paths in unchanged base classes
   (`/frontier journal_replacement`, the legacy `issueJournalOnJoin` option, Mara's "Collect starting equipment").
   **No data loss**: journal (ProgressStore `frontierhunts_journal`), hunter/licence (`frontierhunts_hunters`, LicenceStore),
   expedition (`frontierhunts_expeditions`), Field School and Handbook progress are per-player SavedData; neither item has
   data components (plain `Item` / `ExpeditionGear`).
3. **Nothing needed the item.** Audit (grep of ids/classes over src + dec62g8): J/N/K/H send the same requests without an
   item; journal pages, Licence & Tags, hunts, sign reading (TrailService no longer checks the journal) never test the
   inventory. Item-icon references changed: Handbook task SIGN + Field School lesson SIGN → Handbook icon, task REPORT →
   Expedition Board; the SIGN objective card no longer "wakes" on holding a journal; Handbook step 8 no longer shows the
   Expedition Guide recipe.
4. **Handbook = the books' home** (`HandbookScreen`): footer on every page with four icon buttons — Journal (J),
   Expedition (N), Field School, Assignments (K) — and on *Start here* an "INSIDE THIS HANDBOOK" section (icon, name, what
   it holds, key). **Back**: a book opened from the Handbook returns to that Handbook page on Esc / close / its hotkey
   (`onebook/client/OneBookClient`, `onClose()` hook in Journal/Expedition/Assignment screens); Assignments' back button reads
   "Handbook". Moving between books keeps the way home; opened from the world with J/N/K they close to the game as before;
   leaving an Academy course closes to the game.
5. **Text** (`lang/en_us.json/zzzz_onebook.json`, wins over earlier fragments): Handbook tooltip, welcome card, Start-here
   pack note, step 8 / report task, season tip, checklist hints, journal reserve lines ("Expedition journal" instead of the
   Expedition Guide), Mara's starter kit line, key names. Settings toggle "Journal on first join" removed (config key kept,
   comment says legacy).

## Shared-file hook lines (`// [onebook]`)
`HuntContent` (copied from dec62g8, javap-identical members/static order; tab icon + Handbook instead of the 2 books; typed
lambda decompile fix) · `client/JournalScreen`, `ExpeditionScreen`, `AssignmentScreen` (`onClose` override; Assignments back
label/click, LEAVE → `forget()`) · `guide/client/HandbookScreen` (footer, books section, `arm()`) · `guide/Lesson`,
`onboard/Handbook`, `guide/client/ObjectiveCard` (icons) · `licence/LicenceContent` (tab anchor) · `onboard/OnboardContent`
(comment) · `HuntConfig` (comment) · `client/FrontierSettingsScreen` (toggle removed). Tools: `recipe_audit.py` exceptions,
`economy/value_model.py`, `economy/gen_recipes.py`, `livingworld/gen_loot.py`, `onboard/mock_handbook.py` (new footer).

Known leftover: base `ExpeditionService` party invite says "Open the expedition guide to accept" (hard-coded; still true as
the screen's name, N or the Handbook button).

## In-game test script
1. Fresh world: kit = Handbook, bow, 3 arrows. Creative Field Equipment tab: icon is the Handbook, Handbook first, **no**
   Hunter's Journal / Expedition Guide (also not in search). Recipe book / crafting table: book+feather still makes the
   Handbook; no journal or guide recipe.
2. Press **H**: footer shows Journal J · Expedition N · Field School · Assignments K (icons). Scroll *Start here*: section
   "INSIDE THIS HANDBOOK" with the four rows.
3. Click **Journal**: Hunter's Journal opens with all pages (Licence & Tags, checklist…). Press **Esc** → back on the same
   Handbook page. Repeat with the X button and with J.
4. Click **Expedition** → Expedition journal; click its sidebar *Journal* → journal; Esc → Handbook.
5. Click **Assignments** → Ranger Assignments; bottom-left button reads **Handbook**; click it → Handbook. From step pages,
   "Practice: <course>" / Assignments tab rows behave the same.
6. Click **Field School** → lessons; Esc → Handbook.
7. Close everything, press **J**, **N**, **K** in the world: each opens; Esc closes to the game (no Handbook).
8. Old world: `/give @s frontierhunts:hunter_journal` and `/give @s frontierhunts:expedition_guide` → within 2 s both vanish
   (you already have a Handbook) and chat says your "Hunter's Journal (old) is now part of your Frontier Handbook…". Drop your
   Handbook, give a journal again → it turns into a Handbook. Journal XP/checklist/licence data unchanged (J).
9. Handbook step 4: "Read the sign" task shows the Handbook icon; step 8: report task shows the Expedition Board, no
   Expedition Guide recipe. Settings → no "Journal on first join" toggle.
