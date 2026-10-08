# Workstream `hound4`: hound looks (in-game honest), Sit / Stop / Dismiss, owner can kill

Branch `hound4` (from master 6cbba9d). User feedback after gear.16: "hound still looks awful, should be a way to make
him stop, sit and should be able to remove and kill it".

## 1. Looks

**How it was judged this time.** `tools/tracking/hound/ingame.py` renders the real `.fhsk`, posed by the real
`HoundRig.java`, with the real coat, the way vanilla Minecraft lights an entity (shaders off):
`min(1, 0.4 + 0.6 (n.L0 + n.L1))`, L0 = (0.2, 1, -0.7), L1 = (-0.2, 1, 0.7), per-vertex (Gouraud), full daylight
lightmap, mipmapped bilinear coat, grass-block ground with the vanilla round shadow. Cameras: first person from eye
height 1.62 at 2, 3.5 and 6 blocks, and the default third-person camera (4 blocks behind a player 2.5 blocks away);
each frame is cropped round the dog at 2x (the label gives his height in pixels on a 720p screen).
`python3 ingame.py ultra redbone|bluetick out.png stand,trot_1`, `python3 ingame.py vanilla redbone out.png`.

**What was wrong (BEFORE renders).** Under Minecraft's flat side light (about 0.5 on a flank) the redbone was a
near-black silhouette. The coat median was sRGB (82, 32, 15) with a 10th-90th spread of only 42-47 (flat), and the
baked AO (x0.62 in crevices) blackened the legs and the neck. The legs read as sticks at 3-6 blocks, the neck was thin,
and the head and ears were a little small. The gait skated: the planted paw covered only 0.76-0.82 of the body's advance
in the walk and trot (a per-leg reach cap and a speed factor the renderer's phase did not share).

**Fixes**
* Sculpt (`hound3.py` prims, re-exported; same skeleton, so the rig and the poses are unchanged):
  * more bone in the legs: forearm 3.7/2.45 cm radius (was 3.1/2.0), pasterns +20%, hind tibia and metatarsus +22%,
    paws x1.13 / x1.09
  * fuller shoulder, arm, thigh and gaskin muscle; broader ribs, forechest, loin and croup
  * a thicker neck and throat
  * the skull about 12% bigger, fuller cheeks, a longer and deeper muzzle and flews, a bigger nose
  * ear leathers longer (0.205), wider and thicker, hung slightly out from the cheeks (the `ear_*` joint x is
    0.056), so they hang to the jaw instead of reading as flaps
  * a thicker tail root
  * 17.2k tris (was ~13.4k), smooth shared normals
* Coats (`coat3.py`, `hound3.py coat` re-bakes only the coats):
  * Redbone is now a bright rich red, median sRGB (147, 64, 28) (was 82, 32, 15), with a light under-line and legs
    and a milder dark topline.
  * Bluetick: brighter ground, ticks and tan points.
  * The hair lie runs tail-ward on the body and down the legs.
  * Gentler AO (0.80 + 0.20 ao), plus a soft baked top light so the muscle reads under the flat entity light
    (and still reads under shaders).
  * The gloss sheen is doubled.
* No skating (`HoundRig.strideFor`, used by both the rig and `HoundRenderer`'s phase): the planted paw now travels
  exactly stride x duty. Measured planted-paw speed over body speed (sole point), before -> after:
  * walk front / hind: 0.76 / 0.78 -> 0.94 / 0.97
  * trot: 0.82 / 0.98 -> 0.97 / 1.01
  * gallop: 0.68 / 0.91 -> 0.82 / 0.99

  The Vanilla box model's swing angle is now derived from the same stride: `asin(reach / 0.84)`.
* Vanilla box hound (`classic3.py` -> `hound_redbone.png`): same near-black problem, so the red is brighter:
  base (170, 70, 30), was (128, 44, 19). The layout and geometry are unchanged.

Renders in `docs/ws/hound4/`:
* `ingame_ultra_redbone_BEFORE.jpg` / `_AFTER.jpg`
* `ingame_ultra_bluetick_AFTER.jpg`
* `ingame_vanilla_redbone_BEFORE.jpg` / `_AFTER.jpg`
* `closeup_before_left_after_right_pass1.jpg`
* `poses_ultra_redbone_AFTER.jpg`: stand, walk, trot, gallop, sniff, sniff-walk, bay, sit, sit-bay, lie and tuck, with
  the paws on the ground (lowest vertex between -0.001 and -0.008)

## 2. Controls (all server-validated; the client only asks)
* **Right-click with an empty hand** (owner only) toggles **Sit / Stand**, like a vanilla wolf. Standing up puts him
  back at heel. Right-clicking with any other item passes to that item (a rifle still aims).
* **Sneak-right-click**, or right-clicking him while holding the Hound Lead, opens the command wheel. Holding ` and
  using the lead also still open it.
* **Wheel** (keys 1-7): Heel, **Sit**, **Stop**, Track, Search, Come, **Dismiss**.
  * Stop (`TrackingHound.halt`, new mode `STOP = 11`): drops any job and stands still right there, with no heel and no
    teleport, until the next order. It survives a reload (`HoundStop` NBT), and the HUD shows "Stopped".
  * Dismiss asks for a second click ("Sure?", in red, within 4 s; the wheel stays open even when it was opened by
    holding the key).
* **Dismiss** (`HoundCommands.dismiss`, `TrackingHound.dismiss`): poof particles, a parting bark, and the hound is
  discarded. The registry entry becomes "home" and keeps only his name and coat. **The Hound Lead brings the same dog
  back** ("Belle is back from the kennel"). If he is in unloaded chunks, the entry is marked home and he leaves when his
  chunk loads: a hound whose registry entry is missing, home, or for another hound discards itself (tick check every
  5 s). `find()` never returns a kennelled hound.
* **Kill**: the owner can always hurt and kill his hound (the owner-immunity in `hurt()` is removed). On death the
  registry entry is removed and the owner gets the red line "Your hound Belle died · use the Hound Lead for a new hound"
  (vanilla adds its own death line when showDeathMessages is on). The lead then gives a new hound.
* **Commands** (`/hound`, any player, own hound only): `dismiss`, `sit`, `stop`, `heel`, `come`. They use the same
  path as the wheel. `/hound summon` (op level 2) does exactly what using a Hound Lead does (testing / cheats).
* No orphaned data: a dismissed or dead hound leaves only the registry line (home, or removed). Work state is entity
  memory. `HoundWounds` is per hunter. The client HUD drops a removed entity.

## Files
Changed (owned by this workstream):
* `tracking/hound/`: `TrackingHound` (halt, dismiss, restore, mobInteract, hurt, die, tick orphan check, STOP work and
  save), `HoundCommands` (dismiss, STOP and DISMISS orders, `/hound`, kennel return in `useLead`), `HoundRegistry`
  (home/name/variant + `kennel()`), `HoundNet` (`STOP = 6, DISMISS = 7, LAST`), `HoundBrain` (`STOP = 11` constant only)
* `tracking/client/`: `HoundWheelScreen` (7 slots, dismiss confirm), `HoundRenderer` (strideFor phase, "stop" text),
  `HoundRig` (`strideFor`, `STEP_CAP`), `HoundModel` (stride-derived swing)
* assets: `hound_{ultra,bal}.fhsk`, `real/hound_{redbone,bluetick}{,_far}.png`, `hound_redbone.png`
* lang: `patch/_merge/.../en_us.json/hound3.json` (edited labels and tips), new `hound4.json`
* tools: `tools/tracking/hound/{ingame.py (new), hound3.py, coat3.py, classic3.py, rigview.py/.sh (MeshSkinner on the
  classpath, out dir h4)}`, `tools/qa/hound_smoke.commands` (new)

No shared files were touched.

## Checks
* `tools/compile.sh`: exit=0. `tools/build.py hound4-test` OK.
* `check_jar --base master`: 0 errors, 0 warnings.
* `tools/tracking/harness/run_hound.sh`: ALL PASS. Dead deer found in 119 s, the live deer bayed at 4.7 m, the dead
  end gave up after 61 s, and search pointed at 25.6 m.
* Server smoke: see the result line at the end of this file.

## IN-GAME TEST SCRIPT
1. Creative world. Run `/give @s frontierhunts:hound_lead` and use it: a hound appears, and the chat line lists the
   new controls.
2. Ultra preset, daylight, shaders off. Stand 2, 4 and 6 blocks away, and also try F5. Check:
   * a red (not black-brown) redbone with visible muscle
   * legs with real bone, a long deep muzzle, leathers hanging to the jaw, the tail up
   * as you walk away slowly, then sprint, the paws plant and roll and do not skate
   * repeat on a second player or check `HoundVariant` 1 for the bluetick: ticked blue body, black head, tan points
3. Shaders on (Iris + Photon): the coat looks like a red dog, not orange or neon.
4. Right-click him with an empty hand: he sits ("Belle · sit"). Right-click again: he stands, at heel. Right-click with
   a rifle in hand: the rifle aims, and nothing happens to the dog.
5. Sneak-right-click him: the wheel opens with 7 slots. Press 3 (Stop): he stands still. Walk 30 blocks away: he stays
   standing (no teleport). Tap ` : he comes.
6. Put him on a track (wound a `/summon frontierhunts:whitetail ~20 ~ ~`, wheel -> Track), then wheel -> Stop: he quits
   the line and stands. Wheel -> Sit: he sits.
7. Wheel -> Dismiss: the slot turns red ("Sure?"). Click it again: a puff, he is gone, and chat says he went home. Use
   the Hound Lead: the **same name and coat** come back. `/hound dismiss`, then `/hound dismiss` again -> "already home".
8. Hit him with a sword (no sneak needed) until he dies: the red line "Your hound X died". Use the lead: a new hound.
9. `/hound sit|stop|heel|come` all work. Dedicated server with 2 players: player B cannot sit, stop or dismiss A's
   hound (right-click passes, and the commands only touch your own hound).
10. Vanilla preset: the box hound is a brighter red, and the legs swing in step with the ground speed.

## Known limits
* No client was launched here. The renders emulate vanilla entity lighting; Iris/Photon will look different.
* The Vanilla box layout is unchanged from hound3: only the colour and the swing amplitude changed.

## Server smoke result
`tools/qa/server_smoke.sh` (private copy, port 25741, own work dir) with `fhqa registry` + `tools/qa/hound_smoke.commands`
(the FHQA fake player is the owner): summon -> 1 hound; sit / stop / heel / come / sit; dismiss -> 0 hounds; dismiss again
(already home); summon -> the hound is back; owner `/damage ... minecraft:player_attack by @s` -> "Applied 100.0 damage",
hound dead; sit with no hound; summon -> a new hound; dismiss -> gone. No ERROR/WARN lines, no frontier exceptions, no
client classes loaded, no crash reports. (The dead body stays in the count because a server with no real player stops
ticking entities after 15 s; this is vanilla behaviour.)
