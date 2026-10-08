# phone — the Field Phone (replaces the Camera Base Station)

Branch `phone`. A rugged outdoor smartphone you carry: the time, the weather, your trail cams, directions, contracts,
your licence, texts with other hunters and games — solo, against an AI and against other hunters on the server.
The Camera Base Station is retired the way the 1.3.0 workbenches were.

## What the hunter gets
- **Item** `frontierhunts:field_phone` ("Field Phone"): original Blockbench-style 3D model (rubber bumpers, orange
  power key, camera island, lit screen) with first/third-person, ground, fixed and head transforms and a flat GUI icon
  (`neoforge:separate_transforms`). Made at the **Frontier Workbench → Hunting gear → cameras** (leather, glass pane,
  iron, redstone, copper). Use it, or press **U** (rebindable "Field Phone") with a phone anywhere in the inventory.
  Battery shown as an item bar and tooltip.
- **Battery**: an hour of screen time; the torch costs extra. Charges by a lit campfire, a camp post or lodge stove,
  on a vehicle, and to full over a night's sleep (server side, `PhoneBattery`).
- **Signal** 0–4 bars (`PhoneSignal`, same rule on both sides): full near the reserve's heart, fewer far out, a bar
  more on ridges, less in valleys, none deep underground or in the Nether/End, one less in a thunderstorm. Online
  features need a bar; games already running carry on.
- **Phone screen** (`PhoneScreen` → `PhoneUi`, registered in `FrontierGuiScale`): portrait phone with bezel, punch-hole
  camera, status bar (real in-game time h:mm AM/PM or 24 h, signal bars, battery %), **lock screen** (live nature
  wallpaper lit by the in-game hour: night sky with stars and moon phase, dawn, day, dusk, misty ranges, snow peaks,
  lake reflections, rain/snow; big clock, date, weather line, latest notifications, torch and Trail Cams shortcuts;
  click or swipe up to unlock; it relocks after 20 s in the pocket), home grid of 12 original icons, dock (Trail Cams,
  Maps, Contracts, Weather), badges, pull-down shade with quick toggles and all notifications, banners, toasts, HUD
  notices while the phone is put away. Open/close slides, app zooms from its icon, rotates to landscape for Flush!;
  all of it instant with `HuntConfig.REDUCED_MOTION`. Esc = back (closes from home or the lock screen), U = put away.
- **Apps**
  - *Clock*: big clock, date and day in the reserve, prime hunting hours (dawn/dusk), legal light, **Solunar** major
    and minor periods with a star rating, alarm (rings for a minute, snooze), timer with hunting presets.
  - *Weather*: now, high/low/feels, storm / blizzard / wind-storm warnings with arrival time, next hours, 5-day
    outlook, wind and what it means for your scent, sun and moon (phase), hunting outlook with the rut phase.
  - *Trail Cams*: everything the Camera Base Station did — your cameras nearest first, battery, frames on the card,
    live/logging, best buck, the roll, photos full screen (developed in the darkroom behind the phone), delete,
    clear roll, live view. Only your own cameras, ever.
  - *Maps*: a topo map rendered from the world around you with your places; *Places*: the beginner area (from the
    first hunt), camp post, ranger check station, reserve gate, your bed, **last harvest**, trail cameras and your own
    pins. "Go" puts the place on the first-hunt **compass ribbon** (`FirstHuntCompass.phoneTarget`) with live bearing
    and distance until you arrive.
  - *Compass*: heading, wind arrow and verdict, position, distance and bearing to the navigation target.
  - *Contracts*: Mara's story, the running contract (progress, time left, hand in, abandon), this half-month's board
    (accept), ranger assignments (accept, claim, cancel) — all through the journal's own rules.
  - *Licences*: hunting licence card, big-game tags (in pack / bought, seasons, open now), bird stamps.
  - *Trophies*: filled-tag log (seized ones flagged). *Journal*: rank, XP to next rank, stats, the challenges closest
    to done (from the journal checklist), game records.
  - *Messages*: texts between hunters on the server (threads, unread badges, quick replies, share your position).
  - *Calls*: the mod's real call recordings with when each works now. *Torch*: the phone's light (shines from a
    hand). *Settings*: units, 24 h clock, red night light, on-screen guidance, sound level, silent, notifications.
- **Games** (sounds for every action; logic ticks at 20 Hz, drawing interpolates):
  - *Flush!* (hunting arcade, landscape): 60 s over the marsh, lead your birds, don't shoot hens and hawks, 2-shell
    reloads, streak multiplier. Solo runs are **ranked on a server leaderboard** (server seed, the run's shot log is
    replayed on the server before posting). vs AI marksman (3 levels). Online: a live race on a shared seed, scored by
    server replay.
  - *Hunter's Dice* (re-skinned Yahtzee): solo best card, vs AI (expected-value hold planner, 3 levels), online (the
    server rolls the dice).
  - *Lodge Chess*: 40 mate-in-two puzzles, vs engine (alpha-beta with quiescence, transposition table, 3 strengths, on
    a worker thread), online with full rules (castling, en passant, promotion, 50-move, threefold, insufficient
    material).
  - Online games: invite from the lobby, invite banner/notification, a minute to answer, up to 4 games at once,
    resign, 2 minutes to come back after a disconnect, 15 minutes per move, **rematch** (both ask, sides swap).
    Results in both players' records; high scores per player in `PhoneStore` (world SavedData).

## Server rules (every payload validated)
`PhoneNet.Ask` (op + 3 longs + ≤400-char string + ≤1200 ints + ≤128 longs, bounded in the codec) → `PhoneServer.handle`:
a token bucket per player (24, refills 12/s; texts/invites cost 4), alive and not spectating, a **charged phone in the
inventory** (or standing at a retired base station for camera ops), a bar of signal for anything online. Then:
- contracts via `CampaignProgress.accept/claimContract`, `AssignmentService.request` (board, one-at-a-time, serial and
  completion checks are the journal's own);
- cameras via `PhoneCams.mine/own` (owner only, this dimension) and the photo channel accepts the phone console
  `PhoneCams.PHONE` only through `PhoneCams.mayUse` + `own`;
- games: the server's copy decides (`PhoneGames.apply`: player in the game, their turn, legal move, bounded input);
- texts: cleaned (no § codes or control chars, 200 chars), recipient must have used a phone here and not be you, 1
  per 1.5 s and 12 per minute (`PhoneMessages.allow`), offline delivery;
- pins: cleaned names, 32 max per hunter.

## Retired Camera Base Station (`OldBaseStation`)
Still registered (old worlds/inventories load), hidden from creative, recipe and unlock advancement removed
(`patch/_remove/phone.txt`), tooltip "Retired: the Field Phone replaced it", gone from the bench catalog (the phone
took its slot). Using a placed one opens the phone's Trail Cams for your own cameras (even without a phone) while you
stand within 10 blocks of it. Journal checklist "50 trail camera photos" now shows the phone icon; Handbook lesson
text (glass p3) points at the phone's Trail Cams app.

## Files
- `src/com/formaworks/frontierhunts/phone/`: `PhoneContent` (registration, creative), `FieldPhoneItem`,
  `OldBaseStation`, `PhoneNet`, `PhoneServer`, `PhoneBattery`, `PhoneSignal`, `PhoneStore` (SavedData),
  `PhoneWeather`, `PhonePlaces`, `PhoneWallet`, `PhoneContracts`, `PhoneCams`, `PhoneGames`, `PhoneMessages`.
- `phone/games/`: pure-Java engines `Chess`, `ChessAi`, `ChessPuzzles`, `Dice`, `DiceAi`, `Flush`, `GameKind`.
- `phone/client/`: `PhoneClient` (key, open/close, alarm, ticks, tooltip), `PhoneScreen`, `GuiCanvas` (batched
  renderer, no per-frame allocation), `PhoneFeed` (server records → model), `ClientActions`, `PhoneHud`, `PhoneMap`,
  `PhoneSettings`, `PhoneTextures`.
- `phone/client/ui/` (pure Java, no Minecraft classes — also rendered offline): `PhoneUi`, `Frame`, `Canvas`, `Ui`,
  `Theme`, `Wallpaper`, `TopoStyle`, `G` glyphs, `Txt`, `Anim`, `Scroll`, `App`, `PhoneModel`, `PhoneActions`,
  `apps/*` (16 apps).
- `licence/LicencePhone.java` (wallet data). Assets: `patch/assets/frontierhunts/models/item/field_phone*.json`,
  `textures/item/field_phone.png`, `textures/item/phone/`, `textures/gui/phone/` (app icons), `font/phone_*.json`,
  `sounds/phone/*.ogg` (28 synthesized UI/game sounds). Data: `patch/data/frontierhunts/recipe/field_phone.json` +
  unlock advancement, `patch/_remove/phone.txt`. Merge fragments: `patch/_merge/.../lang/en_us.json/zzzzzzzz_phone.json`,
  `patch/_merge/.../sounds.json/zzzzzzzz_phone.json`.
- Tools: `tools/phone/item.py` (model + texture + preview), `art.py` (icons), `audio.py` (sounds), `mock.sh` +
  `mock/` (renders the real UI offline at 1920x1080 with Java2D), `test.sh` + `test/` (engine tests: perft, AI, dice,
  flush replay), `mocks/` (lock, home, weather, cams, chess, flush). Full set: `/tmp/claude-0/phone-mocks/`.
- QA: `tools/qa/harness/.../PhoneTest.java`, `tools/qa/phone.commands`.

## Shared-file hook lines (all marked `// [phone]`)
- `benches/BenchCatalog.java:152` — `field_phone` in Hunting gear "cameras" (where the base station was).
- `client/FrontierGuiScale.java:48` — `PhoneScreen` in the 640x360 design space list.
- `client/trailcam/Darkroom.java:180`, `DarkroomHost.java:16` (`develops()`), `TrailcamClient.java:609` — the phone is
  a darkroom host only while its gallery is up.
- `trailcam/TrailcamNet.java:91` — the phone console on the photo channel (owner-only).
- `guide/client/FirstHuntCompass.java` (phoneTarget / phoneTick / caption), `guide/client/FirstHuntClient.java:31`.
- `journal/Checklist.java:159` — photos_50 icon.
- `tools/qa/harness/.../FrontierQa.java:106` — `/fhqa phone <case>`.
- `tools/benches/recipes.json` regenerated by `gen_data.py` (camera_base_station out, field_phone in).

## Tests
- Offline: `bash tools/phone/test.sh` → `ALL PASS` (perft suites, puzzles unique mate-in-two, AI levels, dice
  scoring/AI, flush replay determinism and tamper rejection).
- Dedicated server: `tools/qa/phone.commands` (`fhqa phone recipe|station|contracts|cams|games|messages`, plus
  `fhqa bench recipes|craft`), each line `[FHQA] phone PASS/FAIL ...`. Covers: recipe at the bench and a full new
  phone; base station registered, no recipe, hidden in creative, still opens cameras, closes when removed, refused
  from 20 blocks; contracts refused without a phone, off-board, out of range, second while running, unfinished or
  wrong-serial hand-in, paid when finished, abandon; trail cams owner-only (list, find, clear); chess move order,
  illegal/out-of-range moves, fool's mate, no moves after the end, rematch (wait, swap, once, not while running);
  dice roll/score order and a full game's winner; disconnect grace then forfeit, move timeout; text rate limit and
  dropped texts to self/unknown/malformed.

## In-game test script
1. Frontier Workbench → Hunting gear: craft a Field Phone; tooltip shows Battery 100%. Hold it: model in both hands
   and third person looks right; drop it: ground model.
2. Right-click / press U: phone slides up on the lock screen (time matches the world, wallpaper matches the hour).
   Click → home. Esc on home closes; U closes anywhere. Turn on Reduced motion: no slides.
3. Place two trail cameras (and have a friend place one). Trail Cams lists only yours; open a roll, view a photo
   full screen, delete it, clear a roll, live view. Walk into a cave: signal drops to 0, cameras say no signal.
4. Place an old Camera Base Station (`/give @s frontierhunts:camera_base_station`): not in creative, tooltip says
   retired; right-click without a phone → the phone opens on Trail Cams.
5. Maps → Places → Beginner area → Go: the compass ribbon guides you and says when you arrive. Drop a pin, rename,
   remove it. Shoot a deer: "Last harvest" appears.
6. Contracts: accept a board contract, try a second (refused toast), hand in when complete.
7. Weather during a storm: warning card, banner and lock-screen notice. Clock: set an alarm a minute ahead.
8. Two players: Messages (send, unread badge, notification while the phone is away; spam is slowed). Chess
   challenge → accept → play; disconnect one player and rejoin within 2 min (game resumes); finish → Rematch on
   both → new game with colours swapped. Dice and Flush! online likewise.
9. Flush! solo with signal → result posted to the server leaderboard; Dice/Chess vs AI on Hard.
10. Dedicated server: no client-class errors in the log; no warnings from the phone.
