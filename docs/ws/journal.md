# Workstream `journal` — Hunter's Journal overhaul, hunter skills & ranks, progress tracking

Branch: `journal`. `tools/compile.sh` → exit=0. Jar `python3 tools/build.py journal-test /home/claude/work/journal` builds;
`tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Offline harness `tools/journal/harness/run.sh` → ALL PASS
(skill curve, perk unlocks, ranks, perk maths, 84 checklist entries, NBT + sync round trips, detached sync copy, damaged-NBT
clamping, season summary text, compass). Layout mock `tools/journal/mock_journal.py` (Inter fonts from the jar) reviewed at
854×480 ×2, 640×360 ×2 and 455×256 ×3. **Not run in-game here** (no client) — the test script below is still to do.

## What the player gets
1. **Hunter's Journal (J / the journal item)** — rebuilt: leather notebook, sidebar of pages (icons, 1-9 / Tab keys, wheel over
   the sidebar flips pages), smooth scrolling content (wheel, PgUp/PgDn/arrows/Home/End, draggable brass scrollbar), hover
   tooltips, works at every GUI scale (rows lay out from the panel width; columns drop on narrow panels). Pages:
   * **Home** — rank emblem (6 pips) + rank name + XP bar "1,240 XP · 260 more to Tracker", reserve date/season/time,
     5 skill bars (click → Skills), checklist total bar (click → Checklist), **Up next** (current Field School lesson → opens it,
     Mara's campaign report → opens Expedition Guide, active ranger contract, and the checklist entries closest to done →
     jump to their category), **Recent** (last 4 field notes).
   * **Checklist** — 84 entries in 7 categories with filter chips (`Species 5/20 …`), per-category bar, rows with check box,
     item icon, title (struck through when done), progress (`214 / 300 m`, `4.2 / 10 km`, `138" / 160"`), mini bar, XP
     reward; tooltip = how to do it. **Field School** category shows the 7 lessons live from the guide's state (click →
     that lesson) plus Graduate / Mara's campaign / 3 contracts — integrated, not duplicated.
   * **Skills** — five skill cards (level, XP bar, how it trains) with their three perks (unlocked/locked/"Off on this server").
   * **Species** — 17 species: seen / photos / taken / best (score or weight) / first taken date; unknown species greyed.
   * **Records** — shooting (shots, hits, hit %, kills, clean kills, clean-kill %, average + longest shot, bow/gun, prone),
     tracking & fieldcraft (recovered, lost, recovery %, longest trail, sign, stalks, calls, photos, km on foot / ATV, time in
     the field, field dressed), trophy room (best whitetail/elk/moose rack, heaviest animal, species taken) and **season
     summaries** with *Copy text* (clipboard) and *Print (1 paper)* → a signed written book.
   * **Field notes** — auto-entries, newest first: "Oct 12, 6:42 AM — 8-point whitetail buck, 96 kg, 142 m, double lung.
     Scored 138". Recovered after 40 m of trail." plus checklist ticks, perks, ranks, kill sites, sheds, bones, blizzards,
     season summaries, event podiums (48 kept).
   * **Reserve** — the old journal's content, condensed: wind/rain, habitat survey + claim, first-hunt contract + claim,
     Mara's campaign report and contract status, buttons *Expedition Guide*, *Assignments*, *Field School*, *Settings*,
     recent habitats, a short fieldcraft card.
   * Sidebar bottom: **Field School 5/7** button and the token balance.
2. **Skills (server-authoritative, per player, saved)** — use-based XP, levels 0-10 (cumulative 60/160/300/480/700/960/1260/1600/1980/2400),
   perks unlock automatically at 2/5/8 and apply at once (see `journal/Perk.java`):
   | Skill | XP sources | Lv2 | Lv5 | Lv8 |
   |---|---|---|---|---|
   | Marksmanship | clean heart/lung kill 40 + range bonus (bow +1.5/m over 10 m ≤40, rifle +0.25/m over 30 m ≤60); other kills 12 | Steady Hands: scope drift −20 % | Quick Settle: raise wobble ×0.5 | Controlled Breath: breath −35 %, drift −15 % more |
   | Stalking | within 12 m of an unaware animal for ~3 s 12 (≤6 m 25; once per animal, ≥30 s apart); animal called in 20; kill of an unaware animal +20 | Soft Steps: deer hearing radius ×0.8, other game alarm ×0.9 | Low Profile: seen moving ×0.85 | Ghost: scent ×0.75, hearing ×0.8 more |
   | Tracking | sign read 4-5 (new marks, ≤1 per 2 s); rub/scrape 6; recovery 30 + trail/2 (≤50); trail-cam photo 3 (≤30/day); new species photographed 40 | Keen Eye: sign sync radius ×1.25 | Trail Sense: "the animal went NE" on inspect | Bloodhound: "~60 m NE" to the live animal (≤128 m, fresh sign) |
   | Butchery | field dressing 25 + kg/8 (≤30); wildlife taken 10 + kg/10 | Clean Cuts: +25 % venison (≥+1) | Quick Knife: dressing ×0.7 | Master Skinner: +1 hide, +1 quarter |
   | Woodcraft | 1 per 100 m on foot (≤150/day); new biome 15; new season 40; winter 20 per 10 min outdoors; blizzard endured 50; bone site 40; shed 30; predator kill site 30; campmate assist 25; guided hunt 60 guide / 30 client | Trail Legs: −15 % hunger moving outdoors | Provider: game food +2 hunger +2.4 sat | Thick Skin: freezing ×0.6 (+ `coldExposure` 0.7 for Survival) |
   **Ranks** by total XP (skills + checklist rewards): Greenhorn 0 → Woodsman 400 → Tracker 1,500 → Guide 4,000 → Master Hunter
   10,000 → Legend 22,000; toast + field note + chat announcement. Checklist rewards total 5,830 XP.
3. **Toasts** (vanilla stack, journal palette): new rank, skill level (+ next perk), perk unlocked (bell), checklist ticked
   (`+60 XP`), season summary. **XP ticker**: "+62 XP Marksmanship" beside the hotbar, merged per skill, fades in 2.8 s.

## Checklist (84) — `journal/Checklist.java`
Species 20 (17 species, 8 species, all species, 5 species photographed) · Hunting 21 (clean kill 1/10/50, shots 50/100/200/300 m,
bow kill, 30 m bow kill, recoveries 1/10, 75 m trail, 25 signs, stalk 12/6 m, unaware kill, call in, called-in kill, 130"/160"
whitetail, 25 field dressed) · Gear 18 (bow, rifle, scope, prone shot, glass, wind checker, call, camo, skinning knife, trail
camera hung, tree stand sat, blind placed, tent pitched, ATV ridden, jerry can, cargo box/can carrier, hound lead, pack) ·
World 10 (10 biomes, bone site, shed, predator kill, rub/scrape, 4 seasons, blizzard, winter 30 min, 10 km on foot, 50 photos) ·
Camp 8 (join, outfit rank 1, guided hunt, Big-Buck Board top ten, event podium, campmate assist, trophy plinth, 5 deliveries) ·
Survival 4 (eat game, cooked, tanned, aged) · Field School 3 (graduate, Mara's campaign, 3 contracts) + the 7 live lesson rows.

## Hooks into other systems
Tracking is in `ProgressStore` SavedData (`data/frontierhunts_journal.dat`): string-keyed counters (`journal/Stat.java`),
skill XP, done set, species log, notes, summaries, pending wounds (lost after 1 in-game day), season snapshot. Sync: the owner
gets a detached copy (~1.3 KB) when the journal opens and at most once a second while open (walk/time counters are "quiet").
Events (no shared edits): projectile spawns (shots, prone shots; pellets = 1 shot), wildlife hit/death (`ShotVitals.lastHit`),
right-click DeerSign/BoneBlock, block placements (incl. multi-place), mounts (tree stand seat, ATV), eating, per-player staggered
checks (1 s travel; 2 s sightings ≤40 m + ≤3 LOS rays, stalk, KillSiteStore; 5 s inventory gear, HunterLedger/ExpeditionLedger/
FieldSchoolData/CampRegistry, seasons, winter, blizzard, wounds).

**Shared-file hook lines (all marked `// [journal]`):**
* `HuntConfig.java`: `var1.push("journal"); JournalConfig.server(var1); var1.pop();` after the fieldSchool line; `var2.push("journal"); JournalConfig.client(var2); var2.pop();` after the client fieldSchool line.
* `hunting/Whitetail.java`: projectileHit — `float journalCalm = this.alertness();` after the killcam `beforeDeerHit` line and `JournalHooks.deerHit(this, var1, var2, var4, var23, var25, journalCalm, this.shotDistance);` after `afterDeerHit`; perceive — `var19 *= HunterSkills.hearing(var11);`, `var28 *= HunterSkills.scent(var11);`, `var23 *= HunterSkills.sight(var11);`; approachCall — `JournalHooks.called(this, var1);` after the camps line; interact — `DRESS_DURATION` re-set through `HunterSkills.dressTicks(...)`; harvest — `var4 = HunterSkills.meat(...); var5 = …quarters(...); var6 = …hides(...);` and `JournalHooks.deerHarvest(var1, this, var2, this.shooter, this.shotDistance, this.shotRegion, this.shotAt);` after the camps line.
* `wildlife2026/WildlifeMob.java`: `alarm *= HunterSkills.wildlifeAlarm(p);`.
* `tracking/TrailService.java`: inspect — `JournalHooks.signRead(var0, var5);`; visible — view radius `TrailStore.VIEW_RADIUS * HunterSkills.signRadius(var0)`.
* `expedition/TrailCamera.java`: `JournalHooks.trailcamPhoto(server, station.owner, subjects);` after `registry.record`.
* `camps/GuidedService.java`: `JournalHooks.guidedHunt(sp, sp == guide);` in the success loop; `camps/EventService.java`: `JournalHooks.eventPlaced(server, e.id, i + 1);` after the prize credit.
* `client/ScopeZoom.java` settleSway: settle ×`HunterSkills.settle`, drift ×`swayDrift`, breath ×`swayBreath` (client perk mask).
* `guide/client/GuideClient.java`: `layoutEntry` returns false for the JournalScreen (the journal has its own Field School entry); new `fieldSchoolDone()` / `fieldSchoolFlags()` accessors.
* `client/FrontierSettingsScreen.java` INTERFACE: "Hunter's Journal" section with *Journal toasts* and *XP ticker* toggles.
* `client/JournalScreen.java`: replaced entirely (same class name, `new JournalScreen()`, `refresh()`, `biomeName()` kept).

## For other workstreams (Survival)
* `journal/JournalPages.register(id, titleKey, iconItem, order, player -> CompoundTag)` (common) + `journal/client/JournalPageView.Views.register(id, view)` (client) → an extra sidebar page; `JournalUi` has the palette/helpers. Lang `journal.frontierhunts.page.survival` exists.
* `journal/JournalApi.count / xp / note / counter`, `Checklist.register(id, Category.SURVIVAL, counter, target, xp, icon)` (both sides, common setup).
* Perk queries: `HunterSkills.coldExposure(player)` (Thick Skin 0.7) and `HunterSkills.nutrition(player)` (Provider 1.25).

## Config
Server `[journal]`: `skills` (true), `xpMultiplier` (1.0, 0.1-10), `perkStrength` (1.0, 0-2: effect = 1-(1-base)·s),
`disabledPerks` ([] ids), `countCreative` (false), `seasonSummaries` (true), `announceRanks` (true).
Client `[journal]`: `xpTicker` (true), `toasts` (true) — both in Settings → Interface.

## Commands (`/frontierhunts journal …`)
`journal` (status: rank, XP, checklist n/84, skill levels) · op: `xp <targets> <skill> <amount>` · `complete <targets> <entry>` ·
`summary <targets>` (closes the current season now → summary) · `reset <targets>`.

## IN-GAME TEST SCRIPT
Setup: survival world, `/gamerule frontierHunting true`, `/time set 1000`. Kit: `/give @s frontierhunts:ridgeline_rifle`,
`/give @s frontierhunts:reserve_308 32`, `/give @s frontierhunts:field_bow`, `/give @s frontierhunts:field_arrow 32`,
`/give @s frontierhunts:skinning_tool`, `/give @s frontierhunts:grunt_tube`, `/give @s frontierhunts:trail_camera`, `/give @s minecraft:paper 4`.
1. **Open** — press J. Home page: Greenhorn emblem, XP bar `0 XP · 400 more to Woodsman`, date line, 5 skill bars, checklist bar,
   Up next (Field School lesson if running, Mara's report 1/9, closest entries), Recent empty. Try GUI scale 1-4 and a small window:
   nothing overlaps, sidebar rows shrink, species columns drop when narrow. 1-7 / Tab switch pages; wheel over sidebar flips pages;
   PgDn/drag scrollbar; hover a checklist row → hint tooltip. J or Esc closes.
2. **Gear** — within 5 s the Gear entries for the items carried tick: toast `CHECKLIST · Gear / A hunting rifle +25 XP`, ticker
   `+25 XP Checklist`. Place the trail camera → *Hang a trail camera*.
3. **Clean kill** — `/summon frontierhunts:whitetail ~ ~ ~40`. Rifle heart/lung shot → kill cam; ticker `+42 XP Marksmanship`
   (more at range); if it never noticed you also `+20 XP Stalking`; toast *A clean kill*. Skin it → `+~37 XP Butchery`; Field notes:
   "Oct …, … — Mature whitetail buck/doe, 87 kg, 40 m, heart. Dropped where it stood."; Species: Whitetail taken 1, first date;
   checklist *Take a Whitetail*; Records: shots 1, hits 1, clean 1, longest shot 40 m.
4. **Blood trail** — summon another, shoot it in the gut/liver (aim back), follow blood, skin it → `+30..80 XP Tracking`, note
   "Recovered after N m of trail", *Follow a blood trail*. Wound a third and let it go: after one in-game day (`/time add 24000`)
   Records → *Wounded and lost* 1.
5. **Sign & perks** — inspect prints/blood (aim + use): `+4 XP Tracking` (once per mark). `/frontierhunts journal xp @s tracking 1600`
   → toasts *SKILL LEVEL*… *PERK · Tracking 5 / Trail Sense*, *Bloodhound*; now inspecting blood says "Trail Sense · the animal went NE"
   or "Bloodhound · the animal is ~60 m NE". Skills page shows the perks unlocked (gold tick) and the rest locked.
6. **Marksmanship perks** — `/frontierhunts journal xp @s marksmanship 1600`; scope in standing: drift/breath visibly smaller, the
   post-raise wobble shorter. `perkStrength=0` in the server config → no change.
7. **Stalking** — `/frontierhunts journal xp @s stalking 1600`; walking (not crouched) near deer, they notice you later (hearing ×0.64).
   Crouch to within 10 m of a grazing deer for 3 s → `+12 XP Stalking` (≤6 m `+25`), checklist *Get within 12 m unseen*.
   Grunt tube near a deer (≤6 m from where you stand) → `+20 XP Stalking`, *Call one in*.
8. **Butchery** — `/frontierhunts journal xp @s butchery 1600`; skinning takes 30 % less time and drops +venison,
   +1 quarter, +1 hide.
9. **Woodcraft** — walk 200 m → `+1 XP Woodcraft` ticks; `/frontierhunts journal xp @s woodcraft 1600`, eat cooked venison → +2 hunger extra;
   stand in powder snow: freezing builds slower. `/season set winter` + stay outdoors 10 min → winter counter; `/frontierweather blizzard`
   in a snowy biome, stay out 60 s → *Endure a blizzard*, note.
10. **Rank** — `/frontierhunts journal xp @s woodcraft 400` from fresh → toast *NEW RANK Woodsman*, chat "[Journal] <name> is now a
    Woodsman", Home emblem 2 pips, note.
11. **Wildlife** — `/summon frontierhunts:wolf ~ ~ ~20`, shoot heart → note "Gray Wolf, 41 kg, 20 m, heart. Dropped where it stood.",
    species log Gray Wolf taken. Gut shot one that runs and dies → "Followed it N m." (if you are within 64 m).
12. **World** — `/frontierhunts ecology bones elk` then use the skull → *Find a bone site*, note; `... bones shed` → *Find a shed antler*;
    a predator kill site within 14 m → *Find a predator kill*; use a rub/scrape → *Read a rub or scrape*.
13. **Season summary** — `/frontierhunts journal summary @s` (or `/season` to the next season) → toast *SEASON SUMMARY*, Records shows
    the card; *Copy text* → clipboard; *Print* → consumes 1 paper, gives a signed written book with the lines.
14. **Reserve page** — conditions, survey bar + claim when 3 biomes, first-hunt steps + claim, campaign report text, buttons open the
    Expedition Guide, Assignments, Field School (Esc returns to the journal) and Settings.
15. **Field School integration** — Checklist → Field School shows lessons ticking as Field School progresses; sidebar button shows 3/7;
    no old "Field School" overlay entry is drawn on the journal (it still appears on the Expedition Guide).
16. **Camps** — `/camp found Test` → *Join a hunting camp*; a campmate harvests within 96 m → *There for a campmate* `+25 XP Woodcraft`;
    `/huntevent start rut_rally 5m` + place → *A weekend podium*; guided hunt success → *A guided hunt*.
17. **Multiplayer** (dedicated + 2 clients): each player's journal shows only their own record; B's kills never count for A; ranks
    announced to all; `/frontierhunts journal reset B` (op) resets only B; relog keeps everything; server log clean.
18. **Settings → Interface → Hunter's Journal**: toasts off → no journal toasts; XP ticker off → no ticker.

## Known limits / for the coordinator
* Not launched in-game; layout reviewed only with the offline mock. Toasts/ticker are vanilla-GUI (shader independent).
* Tanning/smoking at the base-jar rack/smokehouse give no Butchery XP (no event from those menus); their checklist entries tick when the product is in the inventory.
* Gear entries tick on carrying the item (inventory scan), placements on EntityPlace events; a tent placed by a custom path that bypasses NeoForge place events would not count.
* Marksmanship perks affect the rifle scope hold (ScopeZoom); bow aiming (base jar) is unchanged. Stalking perks apply to deer/elk/moose perception and the wildlife alarm radius.
* "Seen" is per animal per session (LOS within 40 m, staggered); relogging can count the same animal again.

## Integration (integ4: polish, sign, fieldbook, survival, journal)
* **Survival page** in the journal (`survival/SurvivalJournal` registers it, `survival/client/SurvivalJournalView` draws it): rules card
  (difficulty, drain), live protein/fat/energy meters and body-temperature gauge (HUD icons, comfort floor, felt/core, shelter/fire/wet
  chips), season outlook with a 12-month game-condition chart, lifetime survival record, and the three Woodcraft perks with their
  Survival effect. Lang: `en_us.json/integ_survival_journal.json`.
* **Checklist → Survival**: `eat_game` now counts any wild game in Survival's food table; new `wear_furs`, `preserve_8`
  (`SurvivalApi.stats().preserved` or preserved meat carried; drying-rack jerky now counts as preserved), `winter_night` (present at
  dusk on a winter night, alive at dawn; sleeping counts). 87 entries.
* **Perks → Survival**: Thick Skin scales Survival's cold heat loss (`HunterSkills.coldExposure`), Provider scales wild-game nutrition
  (`HunterSkills.nutrition`), Trail Legs works through the exhaustion Survival reads (small refunds no longer read as a hunger point).
* **Sign**: the `rub` entry counts only natural rubs/scrapes (not beds, not legacy kit scrapes nobody worked); icon no longer the kit.
In-game: `/frontierhunts journal xp @s woodcraft 2400`, `/season set winter mid`, `/time set 12900`, stay out until dawn → toast
*Through a winter night*; J → Survival page meters/gauge move live; at night bare-skinned the needle falls ~30 % slower than without the perk.
