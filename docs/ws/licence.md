# Workstream `licence`: hunting licences, tags & stamps; hunting-camp cooking with meal buffs

Branch `licence` (from master 644f37a). Not run in game here (no client available). Every check below was run on the
final jar `.build/frontier-hunts-neoforge-1.21.1-0.1.0-dev.62-landscape.62-licence-test.jar`:

| Check | Result |
| --- | --- |
| `tools/compile.sh` | exit=0 |
| `python3 tools/build.py licence-test .` | built |
| `python3 tools/check_jar.py <jar> . --base master` | **0 errors, 0 warnings** |
| `python3 tools/recipe_audit.py <jar>` | **PASS** (0 unobtainable, 0 errors, 274 recipes) |
| `python3 tools/economy/value_model.py <jar> --repo .` | **0 outliers, 0 grid conflicts, 0 unrated** |
| `tools/gui/run.sh` | layout OK, 221 sound events, 0 without a group |
| `tools/licence/harness/run.sh` | ALL PASS (219 checks: rules, seasons, calendar maths) |
| `tools/qa/server_smoke.sh` (copy on port 25641) | clean (details at the end) |

## 1. Hunting licences, tags and stamps (`licence/`)

**Regulations** (`Regulations.java`, plain data): reserve months from the expedition `HuntingCalendar` (SeasonClock).
A licence season is 3 reserve months (spring Mar-May, summer Jun-Aug, fall Sep-Nov, winter Dec-Feb).

| Item | Covers / open months | Bag (per hunter per season) | Price (tokens / emeralds) |
| --- | --- | --- | --- |
| Hunting Licence | everything except predators & varmints | 1 | 6 / 1, **free after the Academy Archery course** (auto-issued once on passing) |
| Deer Tag | whitetail: Sep-Jan | 2 | 8 / 1 |
| Elk Tag | elk: Sep-Nov | 1 | 14 / 2 |
| Moose Tag | moose: Sep-Oct | 1 | 16 / 2 |
| Pronghorn Tag | pronghorn: Aug-Oct | 1 | 10 / 1 |
| Bison Tag | bison: Nov-Feb | 1 | 18 / 3 |
| Bear Tag | black bear, grizzly: Apr-May, Sep-Oct; polar bear Dec-Mar | 1 | 15 / 2 |
| Big Cat Tag | cougar Dec-Mar; lion/panther/cheetah Jun-Sep | 1 | 18 / 3 |
| Upland Bird Stamp | ruffed grouse: Sep-Dec, 4 birds/day | - | 4 / 1 |
| Waterfowl Stamp | mallard: Oct-Jan, 6 birds/day | - | 5 / 1 |
| Filled Tag | made automatically from a tag | - | record only |
| (none) | coyote, gray wolf, wild boar: predators/varmints, all year, no licence or tag | - | - |

Permits carry a `PermitData` component (hunter UUID + licence season); they only count for the hunter they were
issued to, in that season. Bought on the journal's **Licence & Tags** page while standing within 6 blocks of an
Expedition Board, Ranger Contract Board, Ranger Window or Lodge Stores (server-validated: vendor proximity, payment,
bag, rate limit, mode). A lost licence is replaced free.

**Claiming** (`Tagging.java`): a deer/elk/moose when it is field-dressed (`Whitetail.harvest` hook), any 2026 wildlife
animal when it dies to a hunter (or bleeds out from his wound). Legal big game: one tag is notched (punch sound) and
the hunter gets a **Filled Tag: <species>** with weight, points, date, place (x, z + biome), hunter and season. Legal
birds: count against the daily bag. Otherwise it is **poaching** (no licence / closed season / no tag / no stamp / over
the bag / suspended) and the warden acts per `huntingRegulations`:

| Mode | Effect |
| --- | --- |
| Off | nothing is checked |
| **Relaxed** (default) | first violation of the season = written warning; later ones a small token fine (8 big game, 3 birds; x1.5 closed season) |
| Strict | fine (2x tag price) + a strike; half the meat and the trophy/pelt seized; 3 strikes suspend the licence for the season |

Every violation: warden notice in chat + sound, a journal note, the warden record on the journal page (violations,
strikes, fines - the hunter's standing/reputation), and the animal never counts for a species-hunt milestone
(`HuntHooks` hook). Creative/spectator and Academy training are exempt.

Journal: **Licence & Tags** page (licence, tags bought/in pack, stamps + today's bag, what is open this month, warden
record, last 16 filled tags/violations, buy buttons). Checklist (Hunting): *A licensed hunter*, *Tag your animal*,
*Five filled tags*, *By the book* (25 legal harvests). Expedition screen: the hunting chart now lists every regulated
species from the regulations, plus a "Licences & tags" section linking to the page. Handbook: task *Hunting licence*
(step 5, done automatically when regulations are Off).

Config (`frontierhunts-server.toml`, `[licence]`): `huntingRegulations = OFF|RELAXED|STRICT` (also in the Frontier
settings screen, Survival tab), `acceptEmeralds = true`, `fineMultiplier = 1.0`. Commands (op 2):
`/frontierhunts licence` (status), `/frontierhunts licence mode <off|relaxed|strict>`,
`/frontierhunts licence issue <players> <licence|deer_tag|...|upland_stamp|all>`, `/frontierhunts licence reset <players>`.

## 2. Hunting-camp cooking (`campcook/`)

**Camp Dutch Oven** (block, 5 iron + nugget): set it on a campfire (it sits on a grate over the fire) or feed coal /
charcoal into its fuel slot. Screen: 6 ingredient slots, bowls, fuel, output, progress + heat. Cooks one batch at a
time; the dish is stamped no fresher than its oldest ingredient (Frontier Survival spoilage). Lit = glowing coals,
steam from the lid, simmer/lid sounds. Hoppers: top ingredients, sides bowls/fuel, bottom dishes.

| Dish | Recipe (station) | Makes | Hunger | Protein/Fat/Energy | Keeps | Buff |
| --- | --- | --- | --- | --- | --- | --- |
| Venison Stew | 2 venison, carrot, potato, mushroom + 3 bowls (oven) | 3 | 8 | 18/8/20 | 2 d | Camp Warmth 8 min |
| Venison Chili | 2 venison, 2 beetroot, cocoa + 3 bowls (oven) | 3 | 8 | 18/9/22 | 2 d | Trail Stamina 6 min |
| Bear Pot Roast | 2 bear meat, potato, carrot, salt (oven) | 3 | 9 | 20/24/14 | 4 d | Camp Warmth 10 min |
| Backstrap & Mushrooms | backstrap, 2 mushrooms, fat (oven) | 2 | 8 | 26/8/12 | 4 d | Steady Aim 6 min |
| Duck & Berry Roast | 2 wild fowl, 2 sweet berries (oven) | 2 | 7 | 16/6/14 | 4 d | Keen Tracker 6 min |
| Grouse & Wild Rice | wild fowl, 2 wheat, mushroom + 2 bowls (oven) | 2 | 8 | 14/5/24 | 2 d | Quiet Step 6 min |
| Heart & Liver Fry | organ meat, fat, mushroom (oven) | 2 | 6 | 20/10/10 | 3 d | Hearty 5 min |
| Hunter's Breakfast | venison/game meat, egg, potato, fat (oven) | 2 | 10 | 20/16/26 | 3 d | Steady Aim 8 min |
| Smoked Fish Chowder | 2 cooked fish, potato, fat + 3 bowls (oven) | 3 | 7 | 14/10/18 | 1.5 d | Camp Warmth 5 min |
| Camp Bannock | 3 wheat, fat, salt (oven) | 4 | 5 | 3/8/24 | 6 d | Trail Stamina 3 min |
| Honey Pemmican | 2 jerky, tallow, honey bottle (crafting) | 3 | 7 | 14/20/22 | long | Keen Tracker 4 min |
| Raw Game (Boar) Sausage | 2 game meat, fat, salt (crafting) | 4 | 2 | 8/8/3 | 2 d | - |
| Smoked Game Sausage | raw sausage (smoker / campfire) | 1 | 5 | 10/10/6 | 15 d | Smoke-Masked 5 min |

Buffs (real mob effects with icons, shown in each dish's tooltip; milk clears them). **One camp-meal buff at a time:**
eating a dish replaces any other camp-meal buff and never stacks duration.

| Buff | Effect | Hook |
| --- | --- | --- |
| Camp Warmth | body heat lost 30 % slower | `SurvivalJournal.coldMultiplier` |
| Trail Stamina | +4 % speed, 20 % less exertion hunger | effect attribute + `MealBuffs.tick` |
| Steady Aim | scope/bow sway and bow-hold strain x0.75 | `SurvivalApi.clientSway`, `BowHold` |
| Hearty | half a heart every 12 s | `MealEffect` |
| Keen Tracker | tracks/blood seen 30 % further | `HunterSkills.signRadius` |
| Quiet Step | deer hear you 20 % closer, wildlife alarm x0.9 | `HunterSkills.hearing/wildlifeAlarm` |
| Smoke-Masked | scent 20 % weaker | `HunterSkills.scent` |

Journal checklist (Survival, "Camp cook"): first camp meal, 5 kinds, all 10 oven dishes, a harvest while fed.
Handbook: task *Camp meal* (step 6) and the oven + stew in the cooking step's recipe strip.

## Shared files touched (one-line `// [licence]` hooks)
`HuntConfig` (push "licence" server section), `hunting/Whitetail` (claim + Strict seizure, 3 lines),
`hunts/HuntHooks` (poached animals never count), `expedition/BowHold`, `journal/HunterSkills` (4 multipliers),
`survival/SurvivalApi` (client sway), `survival/SurvivalJournal` (cold), `onboard/Handbook` + `Onboarding` (2 tasks,
step ordering), `guide/client/HandbookScreen` + `HandbookUi` (oven recipe view), `journal/client/JournalPageView`
(default `click`), `client/JournalScreen` (forwards page clicks), `client/ExpeditionScreen` (chart from the
regulations, licence section), `client/FrontierSettingsScreen` (Hunting regulations choice).
Fragments: lang `licence.json`, sounds `licence.json` (5 events, all grouped), `tags/block/mineable/pickaxe`.
Tools: `tools/licence/{art,audio,data_gen,lang_en}.py`, harness; `tools/economy/model.py` + `tools/recipe_audit.py`
entries for the new items/station.

## In-game test script (coordinator)
1. New world, survival, op. `/frontierhunts licence` - shows mode Relaxed and the current licence season.
2. Hunter's Journal -> **Licence & Tags**: "No licence", buy buttons disabled with "Stand within 6 blocks...".
3. `/give @s frontierhunts:expedition_board`, place it, stand next to it, reopen the page: buy a licence (tokens or 1
   emerald) -> stamp sound, a Hunting Licence with your name/season in the pack. Buy a Deer Tag twice; a third is refused (bag 2).
4. Set the reserve calendar to an open deer month (Sep-Jan) with `/season month <n>` (check the page's "Open this month"). Spawn and take a whitetail (`/summon frontierhunts:whitetail`), field-dress it: the Deer
   Tag becomes **Filled Tag: Whitetail deer** (hover: weight, date, place); action-bar message; journal page log row.
5. Drop the tags, kill and dress another deer: a written **warning** (Relaxed, first offence). A third deer: a fine of 8
   tokens, warden sound + chat notice; the hunt milestone for it does not count.
6. `/frontierhunts licence mode strict`, repeat without a tag: fine 16, strike 1, half the venison, no hide/trophy.
   Three violations -> licence suspended. `/frontierhunts licence reset @s` and `mode relaxed` to restore.
7. Kill a coyote / wolf / boar with no licence: nothing happens. In Oct-Jan, take a duck with a licence but no Waterfowl Stamp: violation (warning/fine); with a stamp the action bar counts the daily bag (6).
8. Complete the Academy Archery course: a free Hunting Licence is issued, the page says "free every season".
9. Craft a Camp Dutch Oven (iron), place it on a lit campfire (grate model, glowing coals, steam, simmer). Put in 2
   venison, carrot, potato, mushroom and 3 bowls: Venison Stew x3 after ~40 s. Or place it on the ground and fuel it with coal.
10. Hover the stew: "Camp Warmth 8:00" + description. Eat it: effect icon; eat Backstrap & Mushrooms -> Warmth
    replaced by Steady Aim (one at a time); eating two stews does not extend 8:00. Scope sway visibly calmer.
11. Journal checklist: Camp cook entries tick; Handbook shows the Licence (step 5) and Camp meal (step 6) tasks.
12. Dedicated server + 2 clients: buy on client A, check client B's page is independent; tag fill/warden notice only to the hunter.

## Smoke test
`tools/qa/server_smoke.sh` (copy on port 25641, default command list + `frontierhunts licence`, `licence mode strict`,
`licence mode relaxed`, `help frontierhunts licence`): **clean** - registry blocks=173 items=356 effects=7, items 356 ok,
recipes 274 ok, all blocks placed, 0 FHQA FAIL, no ERROR/WARN lines, no exceptions in frontier code, no client classes
loaded on the dedicated server, no crash reports. Mode switches answered "Hunting regulations: Strict (saved)" /
"Relaxed (saved)". (The FHQA WARN lines - tents/ladder without loot tables, wildlife refused by addFreshEntity - are the
same as on master.)

Not verified here (needs the coordinator's client): visuals of the journal page / oven screen / models in game,
shaders, AMD/Nvidia, multiplayer with two clients.
