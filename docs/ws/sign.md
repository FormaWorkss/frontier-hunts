# Workstream `sign` — rubs and scrapes you can see, no tool, no mock scrape kit

Branch: `sign` (clone `/home/claude/work/sign`). `tools/compile.sh` → exit=0; `tools/build.py sign-test` builds;
`tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Not launched in-game (no client here):
**awaiting in-game verification**. Offline geometry previews: `docs/ws/sign/*.png` (`tools/sign/preview.py`).

User feedback: rubs and scrapes must be visible on trees in every preset and look realistic; you must not need a tool
to find sign; the mock scrape kit is removed — you find scrapes at trees.

## What changed

1. **Rubs drawn on the real trunk (all presets).** `DeerSign` RUB/SCRAPE blocks no longer render a block model
   (`RenderShape.INVISIBLE`; beds keep theirs). New block entity renderer `sign/client/DeerSignRenderer`:
   - Bark stripped from one side of the trunk from knee to waist/chest height: whitetail 0.26–0.40 m → 0.78–1.20 m,
     wider/higher with the buck's size (`Mark.size()` from body mass + antler points); elk 0.55–1.55 m, moose 0.65–1.75 m.
   - Ultra (realistic round trunks): the decal wraps the drawn stem using its real centre, radius, base flare and lean
     (`client/tree/SignTrunkProbe` → new `TreeGrowth.cachedSocket`, read-only, never grows a tree); width is a strip of
     the girth (≈1.05–1.6 × radius of arc). Classic/Balanced (block logs): flat on the log face, 0.30–0.62 m wide.
   - Ages continuously (client computes age from synced `freshened` + rain `wear`): pale wet cambium with sheen (day 0–1)
     → yellow-tan drying (1–2.6) → oxidised orange-brown (2.6–5) → grey weathered scar with cracks (5+).
   - 3–8 peeled bark curls at the torn top/bottom edges (pale inner side out, bark side in; fewer with age) and a
     scatter of shavings on the ground at the foot of the trunk (fresh → darker, gone after ~4.5 days).
2. **Scrapes**: ground decal (~0.85–1.15 m oval of pawed bare soil, drag marks, a print or two, crumbly berm, leaf
   litter / torn grass / clods kicked out behind, away from the tree). It drapes block-column by block-column over the
   real ground top (snow layers included, skips walls/drops/water). Fresh dark wet → drier → dry and dusty with leaves
   blowing in → mostly covered. Above it a **licking branch** grows from the tree's trunk (round stem or log face) at
   1.6–1.9 m and arches out over the scrape to ~1.3–1.6 m, side twigs with leaf sprays, chewed/broken pale tip
   (greys after 2 days). Old scrapes without a remembered tree find the nearest trunk once (cached) or hang a twig
   from the canopy above.
3. **No tool to see sign.** Rubs/scrapes are drawn for everyone. `TrailService.visibleTo` now always returns true
   (removed the crouch-with-journal "scent trace" gate, `scentAssist`). The block outline of the invisible rub/scrape
   hit box is suppressed; looking at one shows `Use · Read the rub/scrape` (bait in hand on a scrape: `Freshen the
   scrape with bait`). Right-click inspection unchanged (journal clue record kept) but never needed to see sign.
4. **Mock scrape kit removed**: item registration (`ExpeditionContent` gear list), `ExpeditionGear` use + tooltip +
   stack size entry, `DeerSign.openMockScrape`, recipe/model/texture (`patch/_remove/sign.txt`), lang key (null in
   `patch/_merge/.../en_us.json/sign.json`). Old worlds: `HuntContent.ITEMS.addAlias(mock_scrape_kit → bait)` (same
   pattern as the anatomy binoculars). Old hunter-made scrapes still load and read "opened by a hunter".
   (The decompiled-only `client/FieldSupplyModel` still has a dead `mock_scrape_kit` mesh branch; never reached.)
5. **Deer make sign often, on the right trees** (`hunting/BuckSign`, `DeerSign.findSite`): a rutting buck checks every
   20–45 s (was 30–70), chance `Rut.markChance × 0.6` (was × 0.3; mature buck in pre-rut = 60 %), picks the nearest
   site within 9 blocks, walks to it, faces the trunk/branch and works it (head-bob cue for a rub, paw/stomp for a
   scrape, sometimes a grunt), then rests 2–4 min. ≈4–6 marks per in-game day for a mature pre-rut buck, fewer for
   young bucks and in peak/post rut. Revisits of his scrapes also walk there (the old approach stopped 6 blocks short).
   - Rub trees: upright (axis Y), unstripped log standing on natural ground (dirt/grass/podzol/moss/sand/snow/gravel/
     clay), 1×1 trunk of 2–16 logs, **natural** (non-persistent) leaves at its top, no planks/stairs/slabs/doors beside
     its foot, an open side at ground level, one rub per tree, none within 2 blocks. Never player builds.
   - Scrape spots: natural ground 1–2 blocks from such a tree (any trunk size), leaves overhead (2–7 up), no scrape
     within 6. The tree is stored in the block entity (`tree`) and synced.
6. **Sync**: `DeerSign.Mark` now sends its visual state (`getUpdateTag`/`getUpdatePacket`: species, size data,
   created/freshened/wear, visits, seed, tree) — on chunk load, when opened, when worked/freshened, on age-stage change
   and when rain adds wear. Maker UUID / description stay server-side.

## Files
- New: `hunting/BuckSign.java`, `sign/SignCommand.java`, `sign/client/SignClient.java`, `sign/client/DeerSignRenderer.java`,
  `client/tree/SignTrunkProbe.java`, `patch/assets/frontierhunts/textures/entity/deer_sign.png` + `deer_sign_far.png`
  (`tools/sign/gen_sign_art.py`), `patch/_remove/sign.txt`, `patch/_merge/assets/frontierhunts/lang/en_us.json/sign.json`,
  `tools/sign/preview.py`, `docs/ws/sign/*.png`.
- Replaced from dec62g8 (whole class now in src): `hunting/DeerSign.java`.
- Shared files (small, marked `[sign]`):
  - `hunting/Whitetail.java` `rutWork()`: sign block of the method (BuckSign.work / plan / revisit, timings).
  - `tracking/TrailService.java`: `visibleTo` → `true`, `scentAssist` removed.
  - `client/tree/TreeGrowth.java`: new package-private `cachedSocket(x, y, z)` (read-only cache peek).
  - `expedition/ExpeditionContent.java`: `"mock_scrape_kit"` dropped from the gear list; alias line to `bait`.
  - `expedition/ExpeditionGear.java`: kit removed from stackables, `useOn` branch, tooltip.

## Commands (op)
- `/frontierhunts sign rub` / `scrape` — the nearest buck (48 blocks) opens a rub/scrape at the nearest suitable tree
  to YOU (10 blocks); says why when there is none.
- `/frontierhunts sign age <days>` — rubs/scrapes within 24 blocks age that many days; `/frontierhunts sign fresh` resets.

## In-game test script
1. New or existing world, Classic preset. Stand within 10 blocks of a small natural oak/birch (1×1 trunk, natural
   leaves) with a buck nearby (`/summon frontierhunts:whitetail` until a buck, or near a herd).
   `/frontierhunts sign rub` → bark stripped on the side facing you, knee to waist height, pale and wet, bark curls
   hanging at the top/bottom edges, shavings at the foot. No block outline when you look at it; prompt
   `Use · Read the rub`; right-click → `Rub · <buck> · … · made within the hour`.
2. Repeat 1 in Balanced and Ultra (Frontier settings → preset). Ultra: the rub wraps the round trunk (no floating
   panel, no gap, no flicker walking from 2 to 40 m; texture swaps to the far copy at 14 blocks).
3. `/frontierhunts sign age 1.5` → yellow-tan; `age 1.5` again → orange-brown, shavings darker; `age 3` → grey scar,
   1–2 curls, no shavings. `fresh` → back to fresh.
4. Near a tree with leaves overhead: `/frontierhunts sign scrape` → ~1 m pawed dark oval on the ground 1–2 blocks from
   the trunk, litter kicked out away from the tree, a licking branch from the trunk arching over it with a chewed pale
   tip and leaf sprays. On a slope/steps it follows each block top; with snow layers around it sits on the snow.
   `age 2`, `age 2` → drier, leaves blowing in, mostly covered.
5. Holding bait, look at the scrape → `Freshen the scrape with bait`; use → `Scrape freshened`, back to fresh.
6. Natural sign: `/season month 10` (pre-rut), follow a mature buck in woodland for a few in-game minutes: he walks to a
   small tree, head-bobs and a rub appears; or paws under a tree and a scrape appears. Over an in-game day several
   appear along his route. Not on stripped logs, log walls, fence-post logs, or trees with player-placed leaves.
7. Old world containing a Mock Scrape Kit: it loads as Bait. `/give @s frontierhunts:mock_scrape_kit` → unknown item.
   JEI/recipe book: no kit recipe. Old rubs/scrapes from dev.62 still show (drawn by the new renderer).
8. Shaders: Iris + Photon (and one other pack): rub/scrape/branch visible, lit by sun and torches, correct in shadow,
   no z-fighting; shaders off: same.
9. Dedicated server + 2 clients: client B sees client A's `sign rub`/`scrape` at once and the same age after `sign age`.
10. Performance: 20+ rubs/scrapes in view (`sign rub` at several trees) — no measurable FPS change (a few hundred
    vertices each, culled beyond 64 blocks).

## Notes / limits
- Not tested in-game; previews are an offline port of the renderer geometry.
- Rubs are one per trunk; the rub's inspect hit box sits at the log face (the round trunk's surface is behind it).
- A plan to walk to a tree can be pre-empted by the routine goal; it then expires after 30 s (no sign that time).
