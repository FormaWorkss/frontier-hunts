# Changelog

## 1.5.0
- Official GitHub source release and Modrinth launch version.
- Includes the 1.4.1 skinning crash fix: tagged whitetail carcasses render the coat and muscle passes sequentially instead of attempting a nested build.
- Public author identifiers use FormaWorks. Audio recordings and gameplay content are unchanged from the supplied 1.5.0 release.

## 1.4.0 (launch)
- **Phone Camera replaces the Field Camera.** Open Camera from the phone's dock. The whole view becomes the
  viewfinder, so you can walk, crouch and turn while you frame the shot.
  - **Controls:** click to shoot, right-click for the front camera, the wheel zooms up to 8x, F or middle-click
    changes the filter, R sets a 3-second timer, and the phone key or Esc puts it away.
  - **Filters:** Natural, B&W, Warm print, Vivid, Trail cam, Vintage and Golden hour.
  - **Saving:** photos save full size in your screenshots folder, as Field Camera photos always did. The Camera app
    shows them in a gallery and a sideways viewer, where you can delete them or send them.
- **Selfies with poses:** in the front camera the wheel picks one of 14 poses: Classic, Peace, Antlers, Flex, Dab,
  Wave, Trophy, T-pose, Moose call, Shocked, Sneaky, Dance, Facepalm and Shrug. Shift + wheel is a selfie stick.
  Hunters nearby see your pose too.
- **Photos and emoji in texts:**
  - **Photos:** send any photo from the gallery, or tap the camera in a thread to take a selfie for that hunter
    (Enter sends it, Backspace retakes). Photos show inline in the thread; tap one to see it full size.
  - **Emoji:** 32 original emoji (faces, hands, deer, antlers, tracks, campfire...) from the composer's emoji board.
    ":)", "<3" and the other usual shortcuts turn into emoji, and a text of only one to three emoji shows big.
  - **Limits:** the server checks every photo, up to 640 px and 90 KB, one every 6 seconds, 20 an hour. Only the
    two hunters in the conversation can fetch it.
- **The Field Camera is retired:** it's gone from creative and has no recipe. Existing ones still work and open the
  phone camera.
- **Phone home screen:** Camera, Trail Cams, Maps and Messages are in the dock. The Torch is the quick toggle on the
  lock screen and in the pull-down shade.
- **Snowmobile off the snow:** it now crawls along slowly on bare ground (about 7 km/h, like a boat on land), forward
  and in reverse, so you can always drive back to the snow. On water it still won't go.
- **Running with shader packs:**
  - **Trees and grass:** with an Iris shader pack on, trees draw one "Distant trees" step lighter (Ultra draws as
    Standard) and thick pasture grass draws as normal. That's about 40% of the tree geometry, which a pack draws
    twice (shadows and scene). Toggle: "Lighter with shader packs" on the Presets and Performance pages, on by
    default. Without a pack nothing changes.
  - **Flashlight:** draws its terrain with one shader setup per pass instead of one per tile.
  - **Deer rubs and scrapes** are no longer drawn in the shadow pass.
- **Launch:** version 1.4.0, an up-to-date mod description, and authors credited as FormaWorks.

## 1.3.1
- **Field Phone** (Frontier Workbench, Hunting gear) replaces the Camera Base Station:
  - lock and home screens, real in-game time, signal and battery;
  - apps: Trail Cams (all the base station did, plus a live view), Weather, Clock with solunar times, Compass, Maps
    with directions to the beginner area, Contracts (accept, track, file), Licences, Journal, Trophies, Calls and
    Messages between hunters;
  - games: Lodge Chess (40 puzzles, engine at 3 levels, online), Dice (vs AI and online) and Flush! (solo, vs AI and an
    online race).
  - Placed base stations open the phone's Trail Cams.
- **Carbon base layer:** right-click a carbon piece to wear it under your clothes. The full set cuts your scent for
  every animal by up to 88%. The Field Gear panel has a Base layer tab, and a Layers screen shows warmth and scent.
  New "Scent & packs" tab at the Frontier Workbench.
- **Clothing:** layered warmth (wind and wet count, fur warmest), and clothing pieces no longer clip into each other.
- **Shooting sticks:** set them up on the ground at standing, kneeling or sitting height, then rest a rifle, shotgun
  or handgun in the yoke. Sway almost gone, you swivel within a natural arc, recoil settles into the yoke. Sneak +
  use folds them.
- **Deer herds like real life:** doe family groups, bachelor bucks that split up for the rut, lone bucks, winter
  yards. Elk cow herds with harem bulls, moose mostly alone. Groups follow a leader loosely and bolt together.
- **Arrowheads:** the Reloading Bench's Arrows tab makes arrows with the head already on, and the "Fit arrowheads"
  tab swaps heads on arrows you have.
- **Blood-tracking lamp:** new high-detail model in hand.
- **Food tab checked:** complete (pemmican, honey pemmican, game sausage; everything else is cooked).

## 1.3.0
- **Three workbenches instead of nine stations.** The Weapons Workbench, Optics & Attachment Workbench, Ammo Reloader,
  Bow Tuning Rack, Fishing Station, Field Clothing Workbench and Tent Bench are retired. Every Frontier item is now
  made at one of three new two-block benches, all crafted at a crafting table from vanilla materials:
  - **Frontier Workbench:** everything that isn't a weapon or ammo, with tabs for Hunting gear, Clothing (incl. furs,
    hides and sewing), Camp & stations, Fishing, Vehicles, Building, Range and Food.
  - **Gunsmith's Bench:** Rifles, Shotguns, Handguns, Bows, Blades, Attachments, and a Fit tab to fit and remove parts
    on the weapon in your hand.
  - **Reloading Bench:** Cartridges, Arrows, Arrow tips, Refit arrows (change the heads on a stack) and Darts & flares,
    with Craft ×5 on every ammo recipe.
  - **New models and one new screen** for all three: tab rail, searchable item grid with "can craft" markers, a
    detail panel with have/need for every material, keyboard control.
  - **Crafting table:** Frontier items are no longer made there (only the three benches, the Handbook and vanilla
    conversions).
  - **Old benches in existing worlds** turn into their successor the first time you use them. Old bench items build
    the new bench when placed. Villages and camps place the new benches.
  - **Handbook and journal:** the first-hour steps, recipe cards and every text that named a bench are updated.
- **First hunt, beginner area:** always open meadow, plains or easy woods with deer, never snow, mountains, swamps or
  steep ground. A compass ribbon at the top of the screen and a soft light column show the way without the Handbook.
- **Smoother after fast travel:** tree growth makes about 5× less garbage (far fewer GC pauses while flying), trees
  ahead of a fast camera are grown at the right detail once instead of twice, the tree cache stops evicting trees you
  can still see, and the snow-print refresh is spread out instead of re-dirtying thousands at once.
- **Clean log:** the "limits mip level" texture warning is gone (material atlas regions now match the textures).

## 1.2.9
- **Licence and tags, by the rules:**
  - **No free starter permit.** The first-hunt page no longer hands out a licence, a tag and a knife.
  - **The Hunting Licence is never sold.** The Ranger Academy issues it when you finish the whole Academy (all six
    courses, not just archery), and a new one every season after that, automatically. Licence counters refuse it
    until then ("Finish the whole Ranger Academy").
  - **A deer tag is only ever bought**, at a ranger counter (12 tokens).
  - The Legend rank no longer gives a free licence.
  - Every text that mentioned the archery-course licence or the starter permit is updated.
- **First Hunt page redesigned:** a dark header with the seven steps as a labelled trail and the reward. The step you're
  on is a highlighted card. Two cards sit side by side: your paperwork, with ticks and crosses and what to do next, and
  where to look, with a compass needle that turns as you do. Paper-styled buttons replace Minecraft's grey ones. The
  Hunting rules page is four numbered cards.
- **Handbook first page:**
  - **START HERE:** the first hunt is now a big dark-green card with a breathing gold edge, the step you're on in large
    type, step pips and a gold Start / Continue button with arrows leading to it.
  - **Visual settings:** now a card of its own, with an icon, the current preset as a coloured pill and the key drawn
    as a key cap.
  - **Next-step card:** turns quiet paper while the first hunt is unfinished, so the two don't compete.
- **Faster Frontier windows:** rounded corners were drawn row by row at screen resolution, one draw call per row, which
  at 4K GUI scale meant thousands of draw calls a frame. Each shape (and each check mark, line and badge) is now one
  batch.

## 1.2.8
- **Plain Minecraft water.** The mod no longer draws its own foam and whitewater over running water. That was the
  white/teal streaking on streams, drawn by a client overlay that also scanned every chunk you loaded.
  - The whitewater and water-foam blocks are invisible, and no new whitewater cascades are added to rivers.
  - Frontier biomes now use vanilla water colours instead of teal.
  - Waterfalls themselves are unchanged.
- **The snowmobile no longer vanishes when you get off.** It wasn't despawning: sneak + use picked it up (code shared
  with the toboggan), and you get off by sneaking, so a use-click right after put it in your pack. In creative it was
  deleted outright.
  - Now it comes back as an item only when you break it, like the ATV.
  - A creative player removes it with sneak + hit; the driver's own swing never hits it.
- **Vehicles together in the creative tab:** ATV, rowboat, jon boat, then the snowmobile.
- **Frontier windows have their own GUI scale.** The Handbook, journals, Academy, settings, camp, workbench and other
  Frontier windows are drawn at the largest whole scale that leaves 640 × 360 GUI pixels, whatever your Minecraft GUI
  scale is: 6 on a 4K screen, 4 at 1440p, 3 at 1080p. Pages no longer overlap at big GUI scales. Your own scale comes
  back when the window closes.
- **Visual settings on a key: F7,** bound out of the box (it was unbound). It's shown on the welcome card and in a new
  row on the Handbook's first page, which also shows the current preset and opens the settings when clicked.
  - New players start on Ultra (unchanged default).
- **Licence, tag and knife, spelled out:**
  - **Hunting rules page:** the first-hunt screen has a new **Hunting rules** page covering:
    - what you need before you shoot;
    - how to get it, including the free licence from passing the Ranger Academy archery course (still automatic,
      and free every season after);
    - tagging the deer within 3 minutes of reaching it;
    - skinning it with the Contour Skinning Knife, without which nothing drops.
  - **Deer down:** the moment your deer goes down, chat tells you what to do next:
    - tag it with your Deer Tag, or that you carry no tag and skinning it would be poaching;
    - then skin it with the knife, or that you need one and how to craft it.

    Hunters with fewer than five harvests get it in chat; others on the action bar.
  - **After tagging,** chat says to skin it with the knife.
  - **First-hunt steps:** a shot also completes the signs and wind steps, so the card moves straight on to tracking
    and skinning.

## 1.2.7
- **A first hunt for new players.** Getting the Handbook now gives one clear objective, "Start your first hunt", in
  seven short steps: get legal permission, find signs, approach with the wind, take a shot, track, harvest, claim the
  reward.
  - **One step at a time.** The objective card shows only the current step, with a line on what to do next. You can
    hide it (and bring it back) from the Handbook or the first-hunt screen. It opens once after the welcome and is
    always on the Handbook's first page.
  - **Steps can be done out of order.** A hunter who skips the wind step still completes the track step by walking
    up on their deer. A harvest completes every step before it.
  - **Worlds from before 1.2.7:** players who already have the Handbook and have never harvested anything are
    offered the first hunt once.
- **No licence confusion before the shot.** The first-hunt screen states the species (whitetail), whether your
  hunting licence and deer tag cover it, and whether the season is open (or how many days until it opens). It uses
  the same read-only check the warden makes when you tag a deer, so the screen and the warden always agree.
  - **No permission?** It gives the route: the coordinates and distance of the hunting camp's ranger counter, and
    which other counters sell licences.
  - **Free introductory permit,** once per player: a hunting licence for this season (if you have none), one deer
    tag and a skinning knife (if you have none). The tag counts against the season's bag like a bought one.
- **A beginner hunting area that never gives the animal away.** A broad 64-block circle about 100–200 blocks off,
  with a compass bearing on the card and advice for the ground ("Look for tracks along the meadow edge", forest,
  water, bedding cover).
  - **Only where whitetails really are:** live deer within 320 blocks, or else established home ranges within 480.
    With neither, the card says to scout and shows no marker.
  - **Never centred on a deer:** the circle sits 15–40 blocks off the animals it was drawn from.
  - **If the area goes empty** (a minute in it with no deer and no recently seen home range), the card says so.
    **Find another area** is always on the screen and picks somewhere at least 128 blocks from the old circle.
  - The wind, shot and tracking steps are checked against real deer, so the last part of the hunt is still yours.
- **Progress survives crashes and disconnects.** Rewards and purchases touch two saves at once: your inventory (the
  player file) and the mod's books (licences, tokens, contracts, course passes, written only at autosave). A crash
  between the two could hand out a licence the books never sold, or pay a reward twice.
  - **Now every milestone saves both together at the end of that tick**, like the five-minute autosave does, at most
    once every 4 seconds: licence, tag and permit issued, starter kit, Academy pass, tag claimed, harvest, contract,
    mission and assignment paid, hunt-milestone rewards, queued rewards delivered, first-hunt reward.
  - **The starter kit and the free permit are marked in your own player file,** next to the items, so a world that
    lost its last minutes can't hand them out twice.
  - **A claim that arrives after you disconnected is dropped.** That covers the first-hunt reward, licence counter,
    contract, mission and assignment claims. It never pays into a save that's already written.
- **Tested on a dedicated server, crash after each milestone:** the server was killed with no shutdown after each of
  licence purchase, Academy completion, a whitetail shot / tagged / skinned, a contract payout and the first-hunt
  reward, then started again. Every time, the tokens, inventory, licence and tag records, course passes, contract,
  harvest count and first-hunt record came back exactly as they were. Claiming everything again paid nothing and
  logging in again gave no second kit. A player who disconnected with three claims in flight got nothing, while one
  who stayed was paid. With the new saves switched off, the same crash after a logout loses the licence and tag
  records and the tokens, which is the bug this fixes.

## 1.2.6
- **Snowmobile, from your first rides:**
  - **Two seats for real.** A second player can now climb on behind the driver. The toboggan's click handler used to
    turn anyone away once a driver was on.
  - **Snow and ice only.** Off the snow the track has nothing to grip: no drive and no reverse, and it drags to a stop
    (about 60 blocks from top speed). A hint says why if you try.
  - **It gets snowed up and cleans itself.** Snow packs on as you ride through powder (more in deep snow) and settles
    on it while it snows. It melts off over minutes: about 8 in the cold, 3 in mild weather, 1 in the warm, and at
    once in water.
    - It's drawn as three cutout layers on the low, upward- and forward-facing parts, so it's safe with shader packs.
    - Clumps fall off as you ride.
  - **Snow on the view, gently.** The driver's view gets the ATV's snow smears, at a much lower rate and smaller.
  - **The rider stays centred on hills.** The seat is now placed where it really is as the machine pitches and rolls.
    Before, you sat ahead of the seat on a climb and behind it going down.
- **Recurve and field bows hit what's under the crosshair.** An arrow flies a real arc: it starts a hand below your
  eye and drops with distance.
  - **Before:** fired straight along the view, it landed 17 cm low at 10 m, about 2 to 3.5 blocks low at 40 m, and 6
    to 8.5 blocks low at 60 m.
  - **Now:** the bow is raised by exactly the holdover for the first block or animal under the crosshair (up to 120
    blocks). The bow test flies the server's own arrow physics: within 7 mm at every distance from 3 to 80 m and
    every angle from 60° up to 60° down.
  - **Aimed at open sky** it still shoots straight along the view.
  - **Moving targets:** you still lead them yourself.
- **`/locate biome` no longer stalls Frontier worlds.** Vanilla's search tried up to 16 heights in every column across
  a 6,400-block radius, millions of lookups for a rare biome. It also never found the snow-country biomes, because
  those are laid over the chunks afterwards.
  - **The real cost** is working out the land itself over the whole radius. It takes 0.4–9 s per 1,600-block square,
    more the further from the world's centre: several minutes for a full search, so the watchdog stopped the server.
  - **One lookup per column:** Frontier chunks take one biome per column, so the search now asks exactly that, with
    snow country included. It finds the same place the chunks hold.
  - **Spare cores and memory:** the search runs on the server's spare cores (up to 4) and remembers what it has
    worked out, so another `/locate` nearby is instant.
  - **A 10-second cap:** past 10 s it stops ("not found within reasonable distance") and logs how far it got. The
    server is never held long enough for the watchdog.
  - **Measured on a 2-core test server, all 40 biomes:**
    - 36 found, 32 of them in under 5 ms;
    - every answer is the biome the generated chunk holds there;
    - the four rarest stopped at 10 s, after searching 1,000–1,900 blocks out.
  - **Codec:** every Frontier world uses this view of its biome source, and its codec is registered for mods that
    save it.
  - **World generation is unchanged:** fresh chunks at six spots hash identically on 1.2.5 and 1.2.6.
- **Ride physics fixes found by the new server checks** (snowmobile, plus one shared with the toboggan):
  - **No more rocket take-offs:** a snowmobile that snapped up a step in one tick used to keep that height as upward
    speed when it left the ground. It now leaves with the climb its speed allows.
  - **Faces steeper than about 55° are walls** to the track. It can no longer hop up a rock face either: it settles
    onto the snow at most 0.6 a tick.
  - **Sleds and snowmobiles keep falling** after brushing a trunk in mid-air. They used to hang there (shared ride
    code; the toboggan's normal rides are unchanged).
  - **Result:** 0 refused moves in 72 real-mountain rides, and every cheat test is still refused.
- **Mipmaps:** eleven textures were 1254×1254 (workshop wood, ghillie camo, packs, shelters, materials, the field
  equipment sheet, the camo rifle). Minecraft cut every block and item texture down to one mipmap level because of
  them. They're now 1024×1024, so distant blocks get their full mipmaps back. All their models use relative texture
  coordinates, so nothing moves. The jar is about 8 MB smaller.
- **Docs:** the performance guide no longer implies measured numbers it doesn't have. It sets out the three standard
  benchmark scenes. The release page lists only tested shader packs (none yet) instead of "any shader pack".
- **The Iris log line** ("Opaque + Translucent pass ran with shaders on") isn't caused by FrontierHunts: the mod never
  changes the graphics mode. It comes from Iris when *Fabulous* graphics is switched off under it (Distant Horizons
  does that). It still needs a look in a real client.

## 1.2.5
- **Snowmobile** (new). A two-up mountain sled, crafted from iron, glass, leather, a blast furnace (the engine), a
  piston and dried kelp blocks (the track). The item is in the gear tab.
  - **The ride:** it uses the toboggan's own ride code (`SledPhysics`, motor mode), so it glides over the block steps
    and up mountainsides on the same smoothed snow surface, rather than climbing blocks like a mob.
    - The track drives it: full grip at low speed, then the engine's power. About 0 to 100 km/h in 6 s on the flat,
      115 km/h top speed on the flat, faster downhill (capped at the toboggan's 160).
    - The skis steer it, only while it moves. With the throttle off the track holds it back, and it parks on gentle
      ground. Back is the brake, then reverse at a walk.
    - It climbs a 45 degree face at a crawl and loses grip past about 50 degrees. Bare ground slows it to a crawl.
  - **Fuel:** it burns the same gasoline as the ATV. Use a jerry can on it to pour (hold to keep pouring). 30 L tank;
    the item keeps its fuel when picked up. Fuel runs low with a sputter, and the engine stalls when the tank is dry.
    Creative drivers and `requireFuel=false` servers run free.
  - **The look:** a full 3D model (`tools/snowmobile/snowmobile_model.py`, 3,600 triangles). It has a red cowl with
    side graphics, angular headlights that glow, a tinted see-through windshield and a two-up seat with grab handles.
    Running boards, A-arm front suspension with coil-overs, skis and bars that turn with the steering, and a lugged
    track that runs at ground speed.
  - **Sounds** are new and synthesized for it (`tools/snowmobile/snowmobile_audio.py`): an idle burble, the two-stroke
    on the pipe and the track. It also has a fuel gauge, a speed readout, exhaust smoke and a rooster tail of snow
    when the track digs in.
  - **Use:** right-click to ride (a second player rides behind). Sneak + use picks it up. It takes four hits to wreck
    one back into its item.
- **Server checks on toboggan and snowmobile moves.** The rider's game drives these (that's what fixed the sled in
  1.2.4), so the server now checks every move before taking it. It refuses a move that is:
  - faster than the ride can go;
  - rising or hovering well clear of the ground, unless it slows like a thrown body (a launch off a crest);
  - passing the rider's body through solid rock, wood or the like.

  The real-mountain test also replays every honest move through these checks: 0 refused in 72 rides. Cheat moves
  were all refused: a 12-block teleport, a steady climb into the sky, hovering 10 blocks up, and noclip into the
  ground.
- **Walking in snow is hard work.** Even ankle-deep snow costs about a quarter of your pace. Knee deep is about half,
  and a full block of drift leaves about a third. Sprinting through it barely helps, jumps are shorter, and ploughing
  through snow uses up food.
- **Warming up is realistic:**
  - **A fire warms you whatever the weather.** Standing next to a single campfire is about 24 °C felt, and next to
    lava about 27 °C. The warmth falls off over about four blocks and dries you as well.
  - **A hole in the ground (or a snow cave)** gets you out of the wind and the snow, so you stop losing heat.
  - **A closed house with a roof** holds its air well above the frost outside: about 10 °C inside at −35 °C outside,
    before any fire.
  - **Going to sleep** in a bed, a bedroll or a tent's bed always warms you up.
- **Recurve and other traditional bows aim with your normal crosshair.** The separate impact dot is gone; it
  showed up in a different spot from the crosshair. That dot also vanished when you aimed steeply up or down (no
  landing point in range), and your crosshair was hidden at the same time. That's why you couldn't aim past a
  certain height. There's no limit on the angle now. The rangefinder readout measures along the crosshair.
- **Water rapids removed:** the whitewater look, the spray and the push are gone. Rivers keep only a gentle current.
- Test servers can turn off the watchdog for the long single-command tests (`QA_MAX_TICK=-1` in `server_smoke.sh`).

## 1.2.4
- **The real reason the toboggan stuck and stuttered in your game.** The rider's game drives the sled, and the server
  checks every move it sends. If the sled's box overlaps a block, the server throws the sled back to where it was.
  The sled rides a smoothed surface that dips a little into the corners of the steps under the snow, so on a real
  mountain the server threw it back every few ticks. Single player runs the same check.
  - The real-mountain test now counts these: **20 to 63 throw-backs in every 15 s ride** on 1.2.3.
  - Fix: blocks never collide with a toboggan (common mixin `SledCollisionMixin`). The sled finds its own way over
    the snow, so Minecraft's block collision had nothing to add. Same test on 1.2.4: **0 throw-backs** on all 36
    rides.
  - The sled carries its own gravity, so a server never mistakes a rider on a sled for a flying player (which gets
    kicked after a few seconds in the air).
  - **The server needs this jar too:** the fix lives on the server side of the game.

## 1.2.3
- **Toboggan tested on real generated land.** A new server test (`fhqa sledtest`) finds snowy slopes in a freshly
  generated world: gentle, medium and steep. On each it rides the real sled code for 15 s, straight and weaving, and
  reports speed, airtime, hits and every place it stopped, with the blocks around it. On 1.2.2, 19 of 36 rides got
  stuck. The fixes:
  - **Ground versus obstacles.** Only things standing out of the ground (trunks, fences, walls, posts) block the sled.
    Rock, earth and snow are the surface it rides; bushes and leaves are pushed through. Before, the sled's box caught
    on the uphill side of every side slope.
  - **Snow banks.** A rise of up to about two blocks is a snow bank it rides up, losing speed as it climbs, and it
    flies off the top. Only a sheer rise of three blocks or more is a wall.
  - **The fall line.** Left to itself, a sled swings round to the fall line and to the way it's moving, as a real
    sled does. Before, it held its first heading while the mountain turned under it, ended up crossing the slope and
    ran into the uphill side. To cross a slope, you hold it there with the steering.
  - **Turning no longer lifts or drops the sled.** Its height over the steps is averaged over a disc the size of the
    sled, the same whichever way it points.
  - **Climbs cost speed, drops give it back** (energy accounted over every bank and dip).
  - **Fewer jumps:** only a real crest or drop launches it, not every bump.
  - **The view is smoothed:** the sled and your eyes ride a spring over the steps under the snow.
  - **Camera chatter at speed** is a hum now, not a shake.
- **Result on the same land:** 13 of 36 rides ended before 15 s. Those stopped on flat benches, on bare earth or
  sand at the bottom of the snow, or against real rock walls. Steep runs average 45–55 km/h over rough peaks, with
  bursts to 80–95. Offline hills: 27° reaches 90 km/h in 5 s; 45° reaches 105 km/h in 5 s and 150 by 12 s.
- **Smooth snow for everyone.** The Minecraft-style preset drew blocky snow layers you could walk up without
  jumping (the snowpack is soft), so they looked wrong. Every preset now draws the smooth snowpack. Games set to
  Minecraft snow are moved over once; it can still be switched back in the settings.
- **Real antlers on the box animals.** Every buck and bull on the Minecraft-style animals now carries the same racks
  as Ultra: the procedural whitetail racks, the sculpted elk and moose racks, legends included. The racks sit where
  the box head's antlers grow and stay on the head in every pose. The box legends' own racks from 1.2.2 are gone; the
  legend coats and glint stay.

## 1.2.2
- **Toboggan rebuilt from the ground up** (`sled/SledPhysics`), tested offline on made-up snowy hills before shipping.
  - It is now a body sliding on a smooth surface laid over the blocks and the snow, not a mob walking over steps.
    Gravity pulls it down the fall line, the runners carve (a turn redirects the speed instead of losing it), and only
    snow friction and the air hold it back.
  - The rider's game runs the ride and the server takes the rider's positions as they are. Before, the server re-ran
    every move through its own collision, which doesn't know a sled rides on the snow.
  - Harness speeds, straight down: 27 degrees reaches about 80 km/h in 5 s and 115 by 12 s; 45 degrees reaches 95 km/h
    in 5 s and 130 by 12 s; a gentle 9 degree run reaches 50 km/h in 8 s. No snags on stairs, bumps or snow layers.
  - It rides a surface averaged over its own length (as the snow fills the steps), so a staircase of single blocks is
    a smooth ramp, not a rattle. Off a real drop it flies and lands further down. Trunks and rock faces stop it; a
    glancing hit only costs the part of the speed that went into it.
  - Steering eases in and out, so a tap is a small correction. Brake: about 3 s from 80 km/h.
  - Left alone it stays where it is (no runaway sleds).
  - Smaller in your view (one rider, about two blocks long); you sit with the curl a stride ahead.
- **Hauling game on the toboggan is gone.** Animals can no longer be loaded onto it.
- **Snow on trees.**
  - Realistic (Ultra) trees: clumps of snow rest on every spray that faces the sky, so a snowy spruce is white on top
    and green underneath. The rest of the crown takes a cold frost, which is stronger at a distance.
  - A realistic tree is as snowy as the ground around it, or wherever it's cold enough to snow. The ground is the
    reliable sign, so the trees always match the forest floor.
  - Minecraft-style trees: the snow on a crown is a soft pillow that runs across the snowy neighbours, rounds over the
    edges and hangs a wavy lip down the open sides, instead of a flat slab. This applies on both snow looks.
- **Legends.**
  - The Old Ridge Buck (Ultra) now carries a rack far past anything in the herd: about 45% bigger than the best buck,
    much heavier, wider and taller, with at least six points a side.
  - Elk and moose racks are a tenth smaller across the board.
  - Box (Minecraft-style) animals: the legends have their own racks.
    - The Old Ridge Buck: a heavy six-by-six with drop tines and a kicker.
    - The Ghost Bull: a seven-by-seven royal with sword fourths.
    - The Bog King: wide, solid, cupped palms rimmed with points.
  - Box legends also have their own coats (grizzled, ash-pale, near black) and a faint golden glint drifting off them.
- **Box animals lying down:** the head and antlers floated above the neck when bedded on the balanced preset. The deer
  skeleton's lying pose doesn't fit the box body, so box animals are now always posed on their own box rig, and the
  antlers follow the head in every pose.

## 1.2.1
- **Toboggan speed fixed at the root.** The probe that read the slope started one block up, so on a hill every uphill
  step looked like a wall: the sled saw no slope and only ever moved at push speed.
  - Now the drop itself feeds the speed: height lost becomes speed, a climb costs it.
  - A steep run passes 120 km/h within a couple of seconds.
- **Toboggan glides.**
  - It rides on the snow surface as shown, because snow's collision is the full visible height for sleds.
  - It steps up 1.5 blocks and glances off obstacles instead of stopping, keeping the speed that still goes somewhere
    and turning with it.
  - The hitbox is narrowed to one block, so it threads between trees.
  - Steering holds firmer at speed.
- **Snow on trees.**
  - Crowns carry one thin layer again: the Minecraft look on the vanilla preset. Old piles shrink to one layer.
  - On the Ultra snow look that layer is hidden (no squares, no tents), and the tree frosts white instead.
  - Frost now follows vanilla's own snow test, season and height included. 1.2.0 read the biome's base temperature,
    so in a winter season, or up a mountain, nothing frosted.
  - The forest floor under crowns gets its snow too.
- **Legend spawn command** finds room for a big animal near where you look (up to 8 blocks around, on the surface)
  instead of failing.

## 1.2.0
- **Toboggan.**
  - Much faster, up to about 140 km/h on a long steep run. It builds speed the longer you ride: snow friction is next
    to nothing, and the runners glide better as a run goes on.
  - Rides on top of the snow instead of sinking into it.
  - Fast runs are moved in half-block steps, so it follows every drop and never tunnels through anything.
  - Sounds like runners, not footsteps: a hissing glide loop and a spray loop in hard turns, new synthesised sounds.
  - Bigger (1.35x), so a carcass fits behind the rider.
  - Looking at a downed animal next to an empty toboggan shows how to load it, and so does the tooltip.
- **Snow on trees.** No more snow layers stacked on leaves; they showed as white squares and tents in the crowns. Snow
  comes down to the ground under the trees, and the trees frost over instead:
  - Minecraft-style leaves are tinted cold and pale where it snows.
  - The realistic trees turn their sky-facing sprays snow-white.
  - Old snow on crowns is cleared when a chunk loads.
- **Snow country (new worlds).** Tighter aprons under the ranges, and about a third of the snowy ranges carry none at
  all. Worlds made with 1.1.9 keep their snow country as it was.
- **Legend racks.**
  - Record-book bulls, about a quarter past the best in the herd; the 1.1.9 legends were 1.8x.
  - No more swollen antlers: real beam and tine girth for every whitetail rack, no extra thickening on elk and moose.
  - Each legend is one of a kind, with its own mark: a crown fork, drop tines, sword-length royals or a kicker on elk;
    wide palms, many points, a drop point or deep palms on moose.
- **Legends are the endgame.**
  - Each legend taken legally gives 500 tokens, a lot of Journal XP, its gleaming legendary trophy, 5% off at the
    licence counter for good, a challenge advancement, the title on screen and a server-wide announcement.
  - All three make you a Master of the Reserve: 2,000 tokens, everything at the licence counter free for life, and a
    gold star by your name.
  - There's a new "Legends of the Reserve" advancement tab, and the Hunter's Path ends there.
- **QA.** `fhqa crowns <x> <z>` counts snow on crowns versus the ground.

## 1.1.9
- **Toboggan.**
  - A real 3D toboggan in the world and in your inventory: birch slats, a steamed curl lashed back, crossbars, rails
    on posts and a rope.
  - It now glides: it rides over block steps instead of dropping down them, follows the fall line, and pitches and
    rolls with the slope. Before, the server's own settling fought the rider's movement and made it step along.
  - The ride is cinematic: the view widens with speed, leans into turns and chatters over the snow; wind roars past;
    snow sprays off the runners (more to the outside of a turn). Your view turns with the sled. Camera effects follow
    the Camera shake and FOV effects settings.
- **Grass and blood.**
  - Grass bends with Sodium and Iris too. The bend is now found from the block's position when the renderer skips the
    model data.
  - Blood drops land on the tops and sides of the grass blades (more of them, both faces drawn), not just flat.
- **Gear.** Every new piece has a detailed 32x32 icon and a 3D model in the hands, on the ground and in item frames:
  - Blood-Tracking Lamp: knurled body, cooling fins, red-and-white LED lens.
  - Shooting Sticks: crossed legs, rubber V-yoke, twist locks, feet and a strap.
  - Flagging Tape: a roll with its tail hanging. The trail flag is a twig with a knotted strip of tape.
  - Hand Warmers: two printed packets.
  - Coffee Thermos: green flask, steel cup-lid, handle, label.
  - Estrus Lure: amber drop bottle with a doe on the label.
  - Milkweed Floaters: split pods with silk spilling out.
- **Hound Lead** costs 150 tokens, the most expensive thing at the counter.
- **Spawn eggs of animals that aren't finished** are gone from the creative tab. One already in an inventory says the
  animal isn't ready yet. Server operators can still use them.
- **Elk and moose antlers.**
  - The original sculpted racks are back: a real 6x6 elk and palmed moose antlers.
  - Every bull's rack is reshaped from them: spread, height, sweep, tips in or out, every tine its own length and
    lean, and left never matches right.
  - Young bulls lack some points. Non-typicals grow drop tines and stickers off the beam.
- **Legends of the Reserve** are freakishly big: about 1.6x a top bull from the herd, massive and pearled at the
  bases, every tine long, with extra crown, kicker or palm points that differ on every legend. Whitetail legends grow
  far past the herd's biggest too.
- **Snow country (new worlds).**
  - Around and below the big snow-capped massifs, the land lies under snow: the foothills, benches and timbered slopes
    under the cliffs, as far out as the mountain is big. Two snowy biomes cover it: Snowy Foothills (new) and Snowy
    Pine Forest.
  - The edge breaks up into patches before the green country. Inside, rivers and lakes stay open, and a few sheltered
    pockets melt out.
  - The terrain is unchanged; the snow is the reserve's own deep snowpack.
  - Worlds made before 1.1.9 keep their land as it was.
- **QA.**
  - `fhqa column <x> <z>` prints a column's top blocks and biome.
  - `QA_WORLD=<dir>` lets the server smoke test start from an existing world.
  - `tools/snowcountry` previews snow country on the real terrain.
  - `tools/meshes/export_racks.py` exports the sculpted racks.

## 1.1.8
- **Deer call.** Your original mouth-call recording is back (it is credited as yours in AUDIO_CREDITS.txt).
- **Animals and sign.**
  - Grass and brush now really bend where deer, elk and moose walk and lie flat where one goes down. (Deer move with
    their own walking system, so the old code never saw them move.)
  - Blood in grass also lands on the blades and stems, not only flat on the soil.
- **Deer, elk and moose look.**
  - Smoother bodies: the small lumps and the patchy little facets are gone at every distance.
  - New antler racks for all three: curved tines in the right order, five rack styles per species (wide, tall,
    basket, heavy, sweeper for whitetail; dagger and whale-tail for elk; butterfly and heavy palms for moose), and
    non-typical heads grow drop tines, kickers and stickers instead of longer spikes.
- **Aim.** The scoped hold sways in a slow, natural figure-eight, and wind leans and buffets it (less crouched, prone,
  on sticks or a bipod).
- **Weather.** Sandstorms only in deserts and badlands, blizzards only in snowy biomes. Morning fog is lighter and less
  frequent.
- **Hound.** Bought at the outfitter's counter only (no recipe). Its outline now works on the Ultra preset too.
- **Outfitter's counter.** Range targets are crafted now. New at the counter: Milkweed Floaters (watch the wind 30 m
  out) and a Roll of Flagging Tape (tie a strip at the last blood).
- **Toboggan.** Craft one (string over two rows of planks), sit on it and point it down a snowy slope - it runs fast.
  Left/right steer, back brakes. Sneak + use a downed deer, elk or moose next to it to lash the carcass on.
- **Legends command.** `/frontierhunts legend <whitetail|elk|moose> [typical|nontypical]` (operators) spawns a Legend of
  the Reserve where you look.
- **Camps** spread out at random and keep clear of villages.
- **Ranger Services** icons redrawn in the Journal's style.
- **Coming Soon** lists only what's coming, as teasers, with the Ko-fi link.
- **Debug:** `/frontierhunts sign passage` counts grass sign near you.

## 1.1.7
- **Calls.** The deer call is the grunt tube from 1.1.5 again. The duck call gives one quack per click.
- **Journal.**
  - **Hunter's Path** sits right under Home and is redrawn as a trail: a card with the stage you're on and a progress bar,
    then three chapters (Greenhorn, Hunter, Master) with the current stage opened up. The freaks are now **Legends of the
    Reserve**.
  - **Coming Soon** has its own hourglass icon and lists only what's coming, not what's already in.
  - **Checklist** no longer lists challenges for animals that aren't in the mod yet.
- **Ranger Assignments.** Field control, upland count and glassing survey each have their own picture and icon (grouse
  flush, decoy spread, glassing).
- **The outfitter's counter** only sells things you can't make. Everything with a recipe is gone from it. New:
  - **Blood-Tracking Lamp:** hold it and blood within 20 m shows bright red, old drops too, day or night.
  - **Shooting Sticks:** in the offhand, standing still, about 40% less sway. A bipod is steadier.
  - **Doe Estrus Lure:** during the whitetail rut, sets a scent wick; bucks within 64 m may come to check it for ten
    minutes. Ignored outside the rut.
  - **Thermos of Camp Coffee** (3 cups): warm at once, chill held off for five minutes.
  - **Hand Warmers** (4 packs): ten minutes of warmth each.
- **Range targets** (at the counter):
  - **Steel Gong:** rings when hit; at long range the ring reaches you late, like real sound.
  - **Steel Popper:** falls when hit, stands back up after three seconds.
  - **Steel Deer Silhouette:** tells you whether you hit the painted vitals.
  - **Steel Spinner:** spins over the top on a hit.
  - **3D Foam Deer:** life size, two blocks long, scored 12 / 10 / 8 (vitals) / 5.
  - **Distance Marker:** right-click to set 25 to 500 m.
  The result and the distance show above your hotbar after each hit.
- **Tracking hound.** Walks around fences, logs and thickets instead of pushing into them.
- **Handbook.** Streamlined: Start here shows the next step and your path as one row; each step shows its first
  paragraph with *Read more*; finished tasks fold to one line. The old layout is under Interface & HUD → Comfort →
  *Classic Handbook*.

## 1.1.6
- **Calls and sounds.**
  - New duck call (real hen-mallard recordings) and deer grunt call (a real grunt tube). Both CC0.
  - Animal calls now carry about 50–70 blocks instead of across the map.
  - Bullets hitting water use Minecraft's own splash sounds.
- **Ridgeline.** The white muzzle burst when firing is gone.
- **Tracking hound.** Your hound gets an orange outline when it's more than a few blocks away, so you can find it in
  thick brush. Toggle: *Hound outline* under Interface & HUD → Comfort.
- **Blood and sign.**
  - Blood no longer floats in the air. It lands on the ground on every Frontier terrain block, and under tall grass it
    goes on the soil beside the stems.
  - Deer, elk and moose push grass, ferns and brush over in the direction they went, harder when running. The plants
    slowly stand back up.
  - Where an animal goes down, the grass under it is pressed flat but not removed.
- **Why tokens, camp and ranks matter.**
  - **Tokens** are the reserve's money. You earn them from contracts, meat and hide deliveries and expedition work. They
    pay for licences, tags, stamps, ranger services, camp upgrades and ammunition. The Licence page explains this.
  - **Camp ranks:**
    1. Your camp post becomes a licence counter.
    2. Venison pays 25% more.
    3. Contracts give you 30 minutes instead of 20.
    4. One more deer tag a season.
  - **Hunter ranks:**
    - Woodsman: the ranger's game report, with where the nearest herds are and what the rut is doing.
    - Tracker: the report counts every group.
    - Guide: 10% off at the counter.
    - Master Hunter: 20% off and one more deer tag.
    - Legend: free licence and free reports.
  - Tag prices and bag limits are rebalanced. Tags for animals that aren't on the reserve yet are listed but can't be
    bought.
  - The store sells only supplies (ammunition, batteries, wind powder, scent cover, bait, first aid). Optics and parts
    are built at the attachment bench.
  - Poached animals no longer pay tokens or count for contracts. Outfitters no longer post hunts for animals that aren't
    in the game yet.
- **Contracts.**
  - 10 new species contracts: doe tags, rut ambush, still hunt, bow opener, blood trail, buck census, high park, bow
    bull, water bull and decoy limit. The board shows up to five at a time.
  - Standing contracts are harder: *Long shot* is now 150 m and *River delivery* needs 5 fish.
- **Hunter's Path.** A new Journal page lays out what to do after your first deer and what each step gives you: licence,
  contracts, camp, ranks, mature bucks, elk and moose, mastering a species, then the freaks. The Journal's Up next
  shows your next step.
- **The freaks.** Three once-in-a-lifetime animals: the Old Ridge Buck, the Ghost Bull and the Bog King. You don't find
  them by luck; each takes a quest:
  1. Master the species.
  2. Hear the ranger's rumour.
  3. Find his sign.
  4. Hunt his country in the rut.
  5. Take him legally at dawn or dusk.
- **Bigger antlers, bigger bodies.** A buck's or bull's frame now follows his antler genes.
- **Ultra deer, elk and moose.** The meshes are smoother, so the elk mane is no longer crumpled. Each distance level
  now has its own shading, so animals at mid range no longer look faceted.
- **Handbook art.**
  - All 21 Ranger Academy and Field School images are new.
  - The game draws teaching marks on top: wind arrows, the scent cone, thermal arrows, an aim ring and short callouts.
    The text stays sharp at every interface scale and can be translated.
- **Coming Soon.**
  - A new Journal page says plainly what is finished, what is coming and what the mod doesn't have yet.
  - It includes an optional Ko-fi support link.
  - A one-time chat note mentions it after you fill five tags.

## 1.1.5
- **New-player path.**
  - The Field School's harvest lesson now completes when you dress a deer and take the trophy. Before, it never did.
  - Skipping the Field School no longer freezes the Handbook at step 4.
  - The hunting licence step moved from step 5 to step 8, when expedition tokens can buy it (or the Archery Range
    makes it free). Under default rules your first unlicensed deer only earns a warning.
  - The deer lessons that need no crafted gear come first.
  - The HUD card says when a step can be skipped.
  - Hoofprints can be read in ordinary worlds too.
  - The Handbook names where deer live, shows the hound lead recipe and shows a coat you can actually make.
- **"Up next."** The Journal's Up next now lists rewards waiting to be claimed (Ranger assignment, habitat survey,
  first hunt) and the next Ranger Academy course. A finished Handbook points there.
- **Comfort settings.**
  - New *Camera shake* (0–100%) and *Blood effects* (Full, Reduced, Off) under Interface & HUD → Comfort.
  - The rapids camera toss now follows Reduced motion and Camera shake.
- **Performance tool.** `/frontierperf benchmark [seconds]` records FPS, 1% lows, frame times and memory to
  `logs/frontierhunts-benchmark-*.txt`.
- **Texture quality.** Fixes 282×282 particle sprites that limited the whole block atlas to one mipmap level, which
  caused shimmer on distant blocks.
- **Xaero's World Map** can now read Frontier's built mossy cobblestone (and the other vanilla-look copies) instead of
  falling back to a flat colour.
- **Audio licensing.**
  - One `AUDIO_CREDITS.txt` lists the sounds the jar actually ships.
  - The deer caller now uses a CC0 grunt-call recording.
  - Removed: an unlicensed recording and stale credits.
- **Compatibility.** Loads on any NeoForge 21.1 from 21.1.248 on; before, it needed exactly 21.1.248.
- **Release.** Rebuilt with the new reproducible release builder (`docs/BUILDING.md`).
- **Pack metadata.** The mod's resource pack is no longer described as a "development foundation".

## 1.1.4
- **Snow:** you can walk up snowy rises of up to about 1.3 blocks, and jumps in snow are cut less.
- **Rivers:** the current is applied by your own game, which removes the rubber-banding in rapids. The current is
  worked out less often on the server, and rapids spray is lighter and follows the Particles setting.

## 1.1.3
- **Crash:** fixes the crash near snow (sun glints).
- **Villages:**
  - The levelled ground eases into the land instead of leaving craters and cliffs.
  - Roads blend, with stone retaining walls on deep cuts.
  - No pasted door slabs, no half trees.

## 1.1.2
- **Snow:** rebuilt as one smooth blanket, with sun glints, footprints and deeper fresh snow (*Snow style* setting).
- **Weapon icons:** drawn large, on the diagonal and with their own lighting.
- **New land:**
  - no surface holes;
  - rarer, randomly placed villages;
  - no lone watchtower;
  - camps keep away from villages.
- **Arrival cards** no longer list animals.

## 1.1.1
- **Snow:** smooth surface and lasting footprints.
- **Water:** stronger currents.
- **Boats:** reworked.
- **Villages:** spread further apart.
- **Ground:** no giant pits.

## 1.1.0
- **Boats:** wooden rowboat and jon boat.
- **Snow:** deep snow you wade through.
- **Water:** whitewater rapids.
- **Optics:** see live wildlife out to 640 blocks.
- **Villages:** tidier.
