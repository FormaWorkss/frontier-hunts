# Workstream `hound3`: tracking hound look, control and tracking

Branch `hound3` (from master 644f37a). User feedback: "the hound dog still looks and stands weird and make sure it's easy
to control and he actually finds deer and trails them".

## 1. Look and stance
* **Ultra** (`hound_ultra.fhsk`, `real/hound_{redbone,bluetick}.png`, `HoundRig.java`, `HoundRenderer.java`): smoothed
  coonhound mesh (smooth normals) and new coats; rig rebuilt with a square stance (straight forelegs, feet under the body,
  angled hocks, level topline), gait by real ground speed (walk / trot / gallop with stride phase), nose-down sniff with
  sweep, sniff-walk, bay (head up), point, sit, lie, slink, look, swim; ears hang with gravity, sabre tail. Paws on the
  ground (lowest vertex -0.002 in stand / walk / trot). Preview: `docs/ws/hound3/poses_ultra_redbone.jpg`
  (`tools/tracking/hound/rigview.py` poses the real `.fhsk` with the real `HoundRig.java`).
* **Vanilla** box hound (`HoundModel.java`): two-piece legs (straight forelegs, thigh / gaskin / plumb rear pastern), neck
  carried up, square skull, long muzzle with flews and nose leather, hanging leathers, sabre tail. Fixed: neck/head pitch
  signs were inverted (sniff threw the head up, bay was level). New 64x64 coats for the new UV layout (the old textures
  were painted for the hound2 layout): `tools/tracking/hound/classic3.py` (layout + setupAnim mirror + previews,
  `docs/ws/hound3/vanilla_{redbone,bluetick}.jpg`).

## 2. Control
* **Call key** `key.frontierhunts.hound`, default **` (grave)**, Frontier Hunts category (J K H N Z G V I O untouched).
  **Tap = come** (calls him in), **hold = command wheel**, let go on an order.
* **Command wheel** (`HoundWheelScreen`): Heel / Stay / Track / Search / Come around a hub with his name and state.
  Mouse + click, keys 1-5, or release the call key. Hovering Track lists the animals you hit lately (label, how long ago,
  distance) - click one to put him on that one. No blur, no pause. Opened by: holding the key, **right-clicking your hound**,
  or **using the Hound Lead** while he is within 48 blocks. Sneak-right-click = stay / heel toggle; sneak-use lead = come.
* **Track** (server `HoundCommands.trackOrder`): the animal picked in the wheel, else the newest animal you hit
  (`HoundWounds`, from `LivingIncomingDamageEvent`, last 6 per hunter, 1 in-game day) from the spot it was hit, else the
  nearest fresh blood / prints within 32 blocks. Aiming the lead at blood / prints still works as before.
* **Feedback**: the new state over his head for ~3.5 s (owner only), a HUD readout right of the hotbar while he is within
  96 blocks (name, arrow + distance, state and quarry, gold when hot / baying / found), and action-bar lines.
* Teleports to the owner like a wolf when > 20 blocks behind at heel (and > 96 in Search); never while trailing.
* Server-safe: client hooks are `Consumer`/`Predicate` fields on `HoundNet` set from `HoundClient.Setup`; orders validated
  (owner, alive, earshot 160, rate 3 ticks; info has its own rate).

## 3. Tracking (`HoundBrain`, pure logic; `TrackingHound` senses + drives navigation)
* Runs the wounded animal's real line (ScentLedger path + its blood / prints from `TrailStore`) in time order, a few points
  ahead; pace ~2.6-4.8 m/s (a walking hunter keeps up), faster / louder as it freshens; casts a forward-biased widening
  spiral at gaps and water; skips ahead / casts round unreachable stretches (stuck = 2.5 s without progress, jumps,
  re-paths); waits and looks back (whining) when the hunter is > 30 blocks behind, resumes at 15; bays a live animal from
  ~4 m (holds a wounded one), stands over a dead one bawling until you walk up, then lies by it.
* Fixed (found by the harness): the cast picked the line up at the very point it was lost and re-cast forever (stalled
  at every gap); `advance` sent the hound back to the oldest point when put on a line mid-way. Now pickup takes the newest
  point strictly after the cursor, advance takes the newest point he stands on (cuts loops like a real hound).
* Search: quarters ahead of the hunter, strikes fresh deer / elk / moose / pronghorn / boar scent on the ground or on the
  wind (up to ~88 blocks downwind), runs it and goes on **point** 15-26 blocks short of the deer; drops game that flees.
* Hunts integration unchanged (`TrackingHound.quarry()`).

## Files
New: `tracking/hound/{HoundBrain,HoundNet,HoundWounds}.java`, `tracking/client/{HoundClient,HoundWheelScreen}.java`,
`patch/_merge/assets/frontierhunts/lang/en_us.json/hound3.json`, `tools/tracking/hound/{classic3,rigview,r3,...}.py`,
`tools/tracking/harness/run_hound.sh`. Changed (owned): `TrackingHound`, `HoundCommands`, `HoundLeadItem`, `HoundModel`,
`HoundRenderer`, `HoundRig`, hound meshes / coats. Shared: `tracking/ScentLedger.java` + `recent(...)` (marked [hound3]).
Harness `HoundHarness` rewritten for `HoundBrain` (the old one tested the removed `TrackingHound.HoundTrail`).

## Checks
* `tools/compile.sh` exit=0. `tools/tracking/harness/run_hound.sh`: ALL PASS - dead deer through a dead-end loop, creek,
  rock ledge (grid pathing) and a 22-block scent gap to FOUND in ~2 min, waits while the hunter stops, never > 38 blocks
  ahead, no spinning; live bedded deer bayed from 4.5 m; line that runs out -> casts and comes back in ~1 min; Search
  strikes and points at ~25 m without spooking.

* `tools/build.py hound3-test` OK (built into tmpfs - the shared disk was full); `check_jar.py --base master`: 0 errors,
  0 warnings. `TrackingHarness`: ALL PASS. `tools/qa/server_smoke.sh` (private copy, port 25673, work dir on tmpfs, plus
  summon / kill of a hound and a whitetail): no ERROR/WARN lines, no frontier exceptions, no client classes loaded, no
  crash reports.

## IN-GAME TEST SCRIPT
1. Creative world, `/gamerule frontierTracking true`. `/give @s frontierhunts:hound_lead`, use it -> hound + chat line.
2. Ultra, then Vanilla style: look from all sides at stand, walk (walk away slowly), trot, run (sprint): square stance,
   level back, paws on the ground, ears hanging, tail up. Hold-key -> Stay: sits; wait 25 s -> lies down.
3. Controls: tap ` -> "come here", he trots in. Hold ` -> wheel; point Heel, release. Right-click him -> wheel; 1-5 keys.
   Sneak-right-click -> stay / heel. Walk 30 blocks with him at heel and `/tp @s ~40 ~ ~` -> he teleports to you.
   Check Options > Controls > Frontier Hunts: "Call hound" on `, no red conflict.
4. Overhead label (orders) and the HUD line right of the hotbar (name, arrow, metres, state).
5. Track: `/summon frontierhunts:whitetail ~20 ~ ~`, wound it with a gut / leg shot, let it run off. Hold ` -> hover
   Track: the deer is listed; release on Track. Expect nose down, following the blood line at a walkable pace, bawling,
   waiting / whining if you stop, casting at gaps / creeks, baying a bedded deer (deer held) or standing over a dead one
   until you walk up, then lying down beside it. Walk away 14 blocks -> he comes back to heel.
6. Search: in a deer area hold ` -> Search. He quarters ahead, "strikes fresh scent", runs it and goes on point short of
   the deer (deer should not bolt). Shoot it -> he takes the blood a moment later.
7. Dedicated server + 2 players: each sees only their own HUD / label; the wheel of player B never orders A's hound.
8. Hunts: Cougar "Behind the hound" with a tracked cougar still credits.

## Known limits
Not launched in-game here (no client). Default key ` may differ on non-US layouts (rebindable). Search strikes from the
server scent ledger (fresh lines of loaded animals); after a restart only blood / prints remain for Track.
