# Workstream `camps` — multiplayer hunting camps

Branch: `camps` (clone `/home/claude/work/camps`). Compiles clean with `tools/compile.sh`; full jar builds with
`tools/build.py` (`.build/frontier-hunts-…-camps-test.jar`). Offline logic harness (ranking, archive, NBT round-trips,
event windows, escrow terms) passes — see "Offline checks". **Not yet run in game** (no client here): everything below
the test script is awaiting in-game verification.

## What was built

1. **Shared camps** — new block **Camp Post** (`frontierhunts:camp_post`, 2 blocks tall: mossy cairn, pine pole,
   carved name plaque, iron flag rings). Placing it without a camp opens the camp ledger to *name the camp*; the plaque
   shows the name + rank + head-count and a cloth pennant in the camp colour (16 dye colours, stitched antler emblem)
   ripples from the pole (BER, 17×5 vertex sheet, cutout, no-cull). Camp = name, owner, members (default max 12),
   shared rank 0-4, post position, log, per-member respawn flag. Invite / accept / decline / kick / leave / rename /
   recolour / disband (strike camp) / ask-to-join through the ledger screen **and** `/camp …`. Ownership passes to the
   longest-standing member; the last one out closes the camp. **Rank is camp-level**: `camp.tier = max(camp, members'
   old ExpeditionLedger.Hunter.camp)` and is written back into every member's `Hunter.camp`, so the guide's Lodge tab and
   old worlds stay consistent (worlds without camps are untouched). "Outfit the camp" buys the next station with the same
   costs/items as the guide (25/45/65/85 tokens). Stations never had owner locks — every member can use every station at
   camp. **Notifications** to online members: `[Ridge Camp] Jake tagged a 142 3/8" 5x5 buck · 96 kg · 212 m`, joins,
   leaves, upgrades, flag raised/taken down, event placings (chime sound). **Respawn at post** (toggle): sets a forced
   respawn on clear ground next to the post; cleared automatically if the post is broken/moved, the hunter leaves, or
   they sleep in a bed. No claims or protection of any kind.
2. **Guided-hunt contracts** — guide offers a client: quarry (any of 17 species), min score (antlered), min weight,
   1-10 reserve days (reserve-calendar time, so `/season` jumps count), fee 0-500 **tokens** (the mod's HunterLedger currency). Client accepts → fee escrowed in
   SavedData `frontierhunts_guided`. Success = client recovers a qualifying animal with the guide online, same
   dimension, within `guideRange` (96 m) → fee to guide, both get title + toast. Timeout → full refund. Guide release →
   full refund; client call-off → 75 % back / 25 % day rate to guide. Offers lapse after 5 min. **Outfitter contracts**
   (NPC): 3 postings per reserve day (deterministic per world seed/day) — booking deposit, purse + deposit back on
   success, deposit forfeited on timeout. Booked at a **Contract Board**: new "Guided hunts ›" button in its screen.
3. **Season record book** — SavedData `frontierhunts_records`. Every harvest (deer via the Whitetail harvest hook,
   2026 wildlife via player kills) → hunter name+UUID, species, sex, score (eighths), points L/R, weight, shot distance,
   reserve date, season, legendary flag/name, dimension+xyz, camp, guide, called-in flag, rack traits. Boards per
   season and all-time: whitetail / elk / moose racks, heaviest bears / boar / bison, longest shot, most harvests.
   Seasons follow the shared SeasonClock calendar and run **September→August** (season N = reserve hunting season).
   Rollover archives the season (top tens + harvest counts, last 24 kept) and announces champions (chat + title).
   Announcements: new season leader, all-time record, top-5 board entry, legendary animals.
4. **Big-Buck Board** — `frontierhunts:big_buck_board`, 3×2 free-standing multiblock (baked model, 6 parts; walnut
   header, planked back, brass plates, green felt honour roll, shingled cap). BER draws the **real trophy mounts**
   (same plaque/head/rack art as wall trophies, every graphics preset) for ranks 1-5 (podium order, #1 centre),
   engraved plates (rank + hunter, score + points, date), and ranks 6-10 on the felt. Live-updates via a small public
   sync payload. Sneak-use cycles whitetail / elk / moose; use opens the record book. `/records board spawn` (op)
   raises one on clear level ground 5-9 blocks from world spawn facing spawn; `/records board here` 2 blocks ahead.
5. **Weekend events** — real-time scheduler (checked once a second, server time zone by default,
   Fri 18:00 → Sun 23:59). Weekly rotation: Big Buck Weekend (best whitetail rack), Predator Weekend (coyote 1 / wolf 2
   / cougar 3 pts), Waterfowl Weekend (ducks & grouse), Rut Rally (whitetail/elk/moose that answered a call ≤5 min
   before the shot). Start: chat rules + title + goat horn. Live standings in the ledger's Events tab and a compact HUD
   card (hold the player-list key; also flashes 8 s on lead changes). Lead-change broadcasts. End: podium, prizes to top
   3 (tokens always, even offline; item prizes delivered on next login), history kept. Admin force start/stop/cancel and
   config. Events only *observe* recorded harvests — no spawn/AI/rule changes.

## Commands
Key: *Hunting camp ledger* (Controls → Frontier Hunts, unbound by default to avoid clashing with map mods).
- `/camp` (ledger) · `found <name>` · `invite <player>` · `accept [camp]` · `decline [camp]` · `leave` · `disband` ·
  `kick <member>` · `rename <name>` · `color <dye>` · `respawn true|false` · `upgrade` · `info`
- `/guide` (ledger) · `offer <client> <species> <minScore> <minWeight> <days> <fee>` · `accept <id>` · `decline <id>`
  · `cancel <id>` · `book <1-3>` (at a Contract Board) · `status`
- `/records` · `top <category> [alltime]` · `recent` · op: `board spawn` · `board here` · `remove <id>`
- `/huntevent` / `status` · op: `start <big_buck|predator|waterfowl|rut_rally> [30m|2h|3d]` · `stop` (award) ·
  `cancel` (no prizes) · `config [key value]` · `reload`

## Config — `config/frontierhunts-camps.json` (server; created on first start, edited by `/huntevent config`)
`events.enabled, timezone ("system" or IANA id), startDay/startTime, endDay/endTime, rotation, prizeTokens [150,90,50],
prizeItems ["frontierhunts:eight_power_scope","frontierhunts:rifle_round*24","frontierhunts:scent_cover*4"],
announceLeadChanges` · `records.countCreative (false), announceRecords` · `camps.maxMembers (12), guideRangeBlocks (96)`.
Keys for `/huntevent config`: enabled timezone startDay startTime endDay endTime rotation prizeTokens prizeItem1..3
announceLeads countCreative announceRecords maxMembers guideRange.

## Files
New (owned): `src/com/formaworks/frontierhunts/camps/**` (22 classes), `camps/client/**` (7 classes),
`client/CampTrophyArt.java` (public bridge to package-private `TrophyDisplay`), `tools/camps_art.py` (generates all
models/blockstates/textures below), `patch/assets/frontierhunts/{blockstates/camp_post,big_buck_board}.json`,
`models/block/camp_post_{lower,upper}.json`, `models/block/big_buck_board_p0..p5.json`, `models/item/{camp_post,big_buck_board}.json`,
`textures/block/{trophy_board_planks,trophy_board_header,camp_flag_item}.png`, `textures/entity/camp_flag{,_emblem}.png`,
`patch/data/frontierhunts/{recipe,loot_table/blocks,advancement/recipes/camp}/{camp_post,big_buck_board}.json`.
Merge fragments: `patch/_merge/assets/frontierhunts/lang/en_us.json/camps.json`,
`patch/_merge/data/minecraft/tags/block/mineable/axe.json/camps.json`.

**Shared-file edits (all marked `// [camps]`):**
- `hunting/Whitetail.java` — 2 lines:
  - `harvest()`, right after the "Harvest recorded" action-bar line:
    `com.formaworks.frontierhunts.camps.CampHooks.deerHarvest(var1, this, var2, this.shooter, this.shotDistance, this.shotAt);`
  - `approachCall()`, after `this.approachTarget = var9;`: `com.formaworks.frontierhunts.camps.CampHooks.called(this);`
- `client/ContractScreen.java` — copied from dec62g8 (first commit is the untouched copy), adds the
  "Guided hunts ›" header button and narrows the title width.

**Reconciling with `trophies`:** the hook reads the score from the trophy item's `CUSTOM_DATA` — first of
`bc_net, net_score, score_net, bc_score, bc_gross, gross_score, score_inches` (double inches), else `trophy_score`
(recovering the eighths from `AntlerDesign` when it rounds to the same value). Legendary = `legendary` boolean or
`legend_name`/`legendary_name`. If `trophies` ships a central `HarvestEvents`, replace the Whitetail hook line with a
listener calling `CampHooks.deerHarvest(...)` (same arguments). Wildlife records come from `LivingDeathEvent`
(player killer, LOWEST priority, cancelled events ignored); weight is deterministic per animal UUID (80-122 % of a
species norm) until a wildlife mass model exists. Tokens are credited through `camps/Tokens.java` (reflection on
`HunterLedger.Hunter.tokens`; swap to a real `HunterLedger.credit` if one is added).

## In-game test script
Setup: survival world (or `/huntevent config countCreative true` to let creative harvests count). Give kit:
`/give @s frontierhunts:camp_post 2`, `/give @s frontierhunts:big_buck_board`, `/give @s frontierhunts:ridgeline_rifle`,
`/give @s frontierhunts:skinning_tool`, `/give @s frontierhunts:contract_board`, rifle ammo `/give @s frontierhunts:reserve_308 32`
(or bow + arrows), a call `/give @s frontierhunts:grunt_tube`.
Single player:
1. Place a **Camp Post**. Expect: 2-block post; ledger opens on "Found a camp". Type `Ridge Camp`, cycle colour to
   Green, *Found camp*. Plaque reads RIDGE CAMP / SPIKE CAMP · 1 HUNTER; green pennant with cream rack ripples east of
   the pole. Walk around: text readable from the front only, flag double-sided, no z-fighting; check at night + with a
   shader pack.
2. Break the upper half in survival → exactly one Camp Post drops, both halves gone, chat "camp post … taken down".
   Re-place → with a camp it binds immediately (chat "raised the camp flag").
3. Ledger (`/camp`) → *Respawn at camp post: ON*. `/kill` → respawn beside the post. Break the post → respawn cleared
   (chat). Toggle OFF restores normal spawn.
4. *Outfit (25 tokens)* (earn tokens at a Contract Board: deliver 4 cooked venison = 12). Expect Lodge stores ×5,
   rank "Tent camp", plaque updates, guide's Lodge tab shows rank 1.
5. Place **Big-Buck Board** (needs 3×2 clear). Expect board model, header "BIG BUCK BOARD · SEASON 1", "#1 open"…
   plates, felt text "NO RACKS ENTERED THIS SEASON". `/records board spawn` places a second one near spawn.
6. `/summon frontierhunts:whitetail ~ ~ ~6 {deer_traits:{schema:2,buck:1b,age_months:66,frame:80,condition:85,rack_genes:150,seed:7,abnormal:0,coat:60}}`
   shoot, wait, skin with the knife. Expect chat `[Record book] 185 1/8" 7x7 buck · 99 kg · N m · #1 whitetail this
   season`; board shows the mount at #1 with plate `#1 <NAME> / 185 1/8"  7x7 / <date>`. Repeat with
   `rack_genes:90,age_months:54,frame:60,condition:70,seed:3` (≈128 7/8" 4x5) → #2 left of centre, broadcast
   "takes #2 on the whitetail board". Sneak-use the board → shows BULL ELK BOARD.
7. Right-click board → Records tab: whitetail rows with details; tabs for Elk…Harvests; *All time*.
8. Wildlife: `/summon frontierhunts:black_bear ~ ~ ~5`, kill it → Bears board entry (weight). Duck kill → action bar only.
9. Events: `/huntevent start rut_rally 10m`. Expect chat rules + title + horn. Use a grunt tube near a deer, shoot and
   recover it → HUD card (hold Tab) shows you 1st; Events tab live. `/huntevent stop` → podium, +150 tokens, 8× scope
   item (or queued if offline). `/huntevent` shows next weekend window; `/huntevent config timezone America/Chicago`.
   Also `/huntevent start big_buck 5m`, harvest a buck, wait 5 min → auto-finish.
10. Season rollover: `/season month 9` (moves forward to next September) → within 5 s chat "Season 1 is closed.
    Champions: …", title "SEASON 2", board resets to empty, Records → *Past seasons* shows Season 1.
11. Outfitter contract: stand at a Contract Board → *Guided hunts ›* → book posting 1 (deposit). Harvest a qualifying
    animal → "CONTRACT FILLED" title, purse + deposit credited. Book another and jump the reserve calendar past its days
    (`/season` to read the month, then `/season month <next month>`; one month = 7 reserve days by default, avoid
    crossing September unless you also want a season rollover) → "Time ran out", deposit forfeit.
Two players (dedicated server):
12. A founds camp, invites B (ledger *Send invite*); B clicks **[Join]** in chat. Both see member list, online dots;
    B's harvest → A gets `[Ridge Camp] B tagged a …` + chime. A kicks B (confirm) → B notified; B's camp respawn cleared.
13. A (guide) → Guided hunts → Client B, Whitetail, min score 100, days 3, fee 20 → B gets chat offer with
    **[Accept]**; accept → 20 tokens leave B. B shoots/recovers a ≥100" buck with A within 96 m → both "SUCCESSFUL
    HUNT", A +20. Repeat with A > 96 m away → "wasn't within 96 m", contract stays open. Let one time out
    (`/season month <next month>`, deadlines run on the reserve calendar) → B refunded. B *Call off* → 15 back to B, 5 to A.
14. Leaderboards on both clients update live (board + ledger); relog B → board still shows, HUD card works.

## Offline checks performed
`T.java` harness (scratch, compiled against the built classes): inches formatting, ranking/ties, doe exclusion,
dedupe, save/load identity of all-time lists, archive, admin remove, trim cap (2500), guided qualification & terms,
outfitter determinism, event points per species/called flag, scheduler window edges (Fri 18:00, Sun 23:58, Mon),
weekly rotation, camp save/load, ownership hand-off, orphaned respawn cleanup. All pass. Models/textures previewed
with an offline rasteriser (board front/¾, post, flag in 4 colours).

## Known limitations / for the coordinator
- No in-game verification yet (client/server, shaders, AMD/Nvidia, FPS). Board cost = 5 trophy mounts (cached meshes)
  + ~30 text runs, culled at 64 blocks; pennant = 64 quads, culled at 96.
- Trail cameras keep their per-owner privacy (not shared with the camp) — `trailcam` workstream owns ScoutingNetwork.
- Wildlife weights are synthetic until a mass model exists; wildlife records need a player kill (no skinning step).
- No settings-screen entries needed (all server-side config).
- Camp members are not automatically an expedition *party* (`/expedition invite`), so the campaign's "Group hunt"
  contract still uses parties; linking them would need an edit to `ExpeditionService.members()` (not in src).
