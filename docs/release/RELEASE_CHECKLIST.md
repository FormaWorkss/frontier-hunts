# Release checklist

Never mark a check as passed unless it was actually done. Write PENDING or BLOCKED next to it instead.

## 1. Build and automatic checks (cloud workspace or PC)
- [ ] `VERSION` bumped and `docs/release/CHANGELOG.md` updated.
- [ ] Jar built with `tools/release/BuildRelease.java`, and its SHA-256 recorded in `tools/release/expected/<version>.sha256`.
- [ ] `tools/check_jar.py` shows 0 errors, and `tools/recipe_audit.py` shows PASS.
- [ ] The harnesses pass: academy, hunts, journal, licence, livingworld, survival, wingshot, regions, presets, rutfight
      and hound.
- [ ] Dedicated-server smoke test (`tools/qa/server_smoke.sh`): starts, has no Frontier exceptions and loads no
      client classes.

## 2. Client test (dev client: `Minecraft-Mod-Builder/frontier-combat-master`, `runClient`)
Do it in a new world (Frontier — Alpine Wilderness), first without a shader pack, then with Sodium + Iris + a pack.
- [ ] The game reaches the title screen, and the log has no `frontierhunts` ERROR lines.
- [ ] A world loads. The Handbook welcome screen appears, and the HUD card shows step 1.
- [ ] **Snow:** stand in snow and walk up a 1-block snowy rise without jumping. The snow surface is smooth, footprints
      appear, and you see sun glints on a clear day.
- [ ] **Water:** swim into a fast river. You are carried smoothly, with no snapping back. Check FPS by a waterfall.
- [ ] **Boats:** place, board and row both boats. The steering keys work and you can get out.
- [ ] **Optics:** binoculars and a scope zoom. The rangefinder reads a distance on animals.
- [ ] **Weapon icons:** guns and bows show large and readable in the hotbar.
- [ ] **Comfort:** Camera shake at 0 means no recoil kick. Blood effects Off means no blood spray, but the trail marks
      still show.
- [ ] **Benchmark:** run `/frontierperf benchmark` in a forest and once by a waterfall, and keep the files for the
      performance guide.
- [ ] **Shaders:** with a shader pack on, the snow, grass, water and weapon icons all render, with no black or missing
      surfaces.

## 3. First 20 minutes (fresh players)
Give two or three people who have never played the mod a fresh survival world with no help. Watch them, or have them
record their screen. Note the minute they get stuck and on what.

| Minute | Expected | Watch for |
|---|---|---|
| 0–2 | Welcome screen → Handbook → crafting table | Do they find the Handbook again (right-click it, or the HUD card key)? |
| 2–6 | Arrows: primitive or hunting | Flint, feathers: do they know where to get them? |
| 6–10 | Step 3: Bow Tuning Rack (string) | Do they skip it when there is no string, as the card says? |
| 6–15 | Read sign, close the distance | **Finding animals:** do they go to a forest or meadow? Do they understand the wind? |
| 10–20 | First shot | **Shot placement:** do they aim behind the front leg? |
| after the shot | Follow the blood, dress the deer, take the trophy | **Recovery:** do they press Use on blood? Do they find a skinning knife recipe? |
| any time | Rewards | **Claiming:** do they notice Journal → Up next → Claim? |

Afterwards ask each player:
- Where did you get lost?
- What did you think the mod wanted from you?
- Would you play another session, and what would you do next?

## 4. Publish
- [ ] The same jar goes on the mod page and the server.
- [ ] The release page lists the requirements and the known issues.
- [ ] The changelog is pasted.
- [ ] The source zip and base parts are copied to `release-kit` on the PC.
