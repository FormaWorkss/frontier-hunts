# Workstream `fieldbook` — Field School guide: field-guide art, fact check, no scrape kit

Branch: `fieldbook`. Compile `tools/compile.sh` → exit=0. Jar `python3 tools/build.py fieldbook-test /home/claude/work/fieldbook` builds;
`tools/check_jar.py <jar> . --base master` → 0 errors, 0 warnings. Not run in-game here (no client); layout checked with the offline
mocks below.

## What changed
1. **Every plate redrawn** in one ink-and-watercolour field-guide style (new engine `tools/guide/fieldart.py`: supersampled 3x,
   premultiplied Lanczos downsample + light unsharp mask, transparent watercolour washes with pigment variation, granulation and pooled
   edges, form shading from an upper-left light, sepia contour whose weight swells on the shadow side, fine pen fur/grass strokes).
   Plates are now **1024 x 512** (banner 1152 x 384) for crisper text-free art at GUI scale 3-4.
   * `whitetail.py` — anatomically proportioned whitetail (shoulder 1.0 m, brisket 0.47 m, body 1.18 m, 8-point rack, white throat
     patch / muzzle band / eye ring / belly, hooves + dewclaws), poses: standing, alert, feeding, lying (harvest), ghosted (vitals).
   * vitals: ghosted broadside buck with vertebral column + spinous processes, scapula/humerus over the front of the chest, ribs,
     lungs, heart low behind the elbow, diaphragm, liver behind it, rumen, intestines; aim point one-third up behind the front leg
     with a 20 cm vital zone; inset: overhead quartering-away shot line from the hunter through the near ribs to the far front leg.
   * wind: scent plume widening/fading downwind on the ground, winded buck inside it, upwind doe outside it, crouched bowhunter;
     two slope insets: thermals rising (sun) / sinking (moon).
   * tracks: 10 prints **drawn to one scale** (9 px/cm, 10 cm scale bar): whitetail, elk, moose (+dewclaws), bison, wild boar
     (wide dewclaws), wolf + coyote (4 toes, claws, two-lobed heel), cougar (no claws, three-lobed heel, leading toe), black bear
     front + hind (5 toes, smallest inside), grouse, duck (webbed). Depth-shaded impressions in mud.
   * rub & scrape (no kit): stripped sapling with antler gouges and shredded bark; licking branch with chewed tips over a natural
     pawed scrape with a track in it.
   * glassing (binocular view of a buck at a timber edge at first light) + trail camera strapped waist-high, cone along the trail.
   * stalk, blood colour guide (heart spray, frothy lung, dark liver, gut with stomach matter, leg drips; trail with hair at the
     hit site, flagged last blood, wound bed), harvest (downed buck, kneeling hunter with skinning knife, rack plaque, hide, venison),
     tips (wall tent + camp post + fire, ATV + jerry can, the year in four trees, prone with rifle on bipod), welcome banner.
2. **Icons**: new 4x4 atlas `textures/gui/field_school/icons.png` (64 px parchment medallions in the same style). The objective
   card and all toasts draw these instead of item icons (`guide/client/GuideIcons.java`).
3. **Fact check** of every guide lang line against field references and the mod's code (see table). Lang regenerated with
   `tools/guide/lang_en.py` (203 keys).
4. **Mock scrape kit**: no guide text or art refers to it; lesson 2 now teaches finding natural rubs and scrapes by eye
   (stripped saplings knee-chest high; pawed bare earth under an overhanging licking branch along field edges, trails, wood lines).

## Fact check (corrections made)
| Topic | Before | Now (source / mod) |
|---|---|---|
| Thermals | ok | kept, made explicit: rising mid-morning→late afternoon, sinking evening/night/early morning (`Wilderness.thermal`: falling < 07:12, rising 11:00–16:30, falling after 19:12); rain weakens scent ×0.55 and thermals ×0.3 → "Rain dampens scent and thermals" (was "knocks scent down") |
| Scent cone | "wedge" | widens and weakens downwind (Gaussian plume σ = 4 + 0.18·d, decays over distance) |
| Rubs / scrapes | "Bucks come back to both" | scrapes are revisited (`DeerSign.visit`), rubs mark routes; made before and through the rut (`Rut.markChance`); scrape needs leaves 2–5 blocks overhead (licking branch); no tools needed |
| Tracks | 8 kinds, rabbit | the mod's game species with real sizes (whitetail 5–9, elk 10–12, moose 13–16, bison 12–15, boar 5–7 cm …) |
| Shot placement | broadside only | adds quartering-away aim and "pass on facing / quartering-toward shots with a bow" |
| Non-vital hits | "liver/gut fatal, leg/shoulder often live" | adds neck/head (fatal after a run in the mod), shoulder/leg often live (`DeerWound.fatal`) |
| Recovery wait | "give it a few minutes" | real-world ~30 min lung (no visual), 3–4 h liver, 8–12 h gut; in game liver ≈35–50 s, gut ≈3 min (`DeerWound`) |
| Blood | ok | lungs "bright to pinkish, frothy"; gut "thin, dull, green/brown matter"; first-blood hair added |
| Prone | "no sway at all" | "almost all the sway is gone" (HoldSway prone drift 0, breathing 0.06) |
| Trail camera | "chest height" | "about waist high, angled along the trail, not into the rising/setting sun" |
| Glassing | "dawn and dusk" | "first and last hour of light" |

## Files
New: `src/.../guide/client/GuideIcons.java`, `patch/.../field_school/icons.png`, `tools/guide/{fieldart,whitetail,scenery,tracks,plate_preview}.py`.
Changed (guide package only, no shared files): `GuideArt` (1024x512 dims, all label anchors, new label ids), `FieldSchoolScreen`
(tips quarters from the texture size), `WelcomeScreen` (banner size), `GuideToast`/`ObjectiveCard`/`GuideClient` (atlas icons),
`tools/guide/{gen_art,lang_en,mock_screen}.py`, all 10 plate PNGs, lang fragment. Removed `tools/guide/{inkart,deer}.py`.
Previews: `docs/ws/fieldbook/` — `labelled_<plate>.jpg` (plate + lang labels as GuideArt draws them), `page_<lesson>.jpg`
(guide page mock with card + toast icons), `icons.jpg`, `welcome.jpg`, plain plates.
Regenerate: `python3 tools/guide/gen_art.py . [plate…]` (~3 min all), `python3 tools/guide/lang_en.py .`,
`python3 tools/guide/plate_preview.py . <fonts>`.

## IN-GAME TEST SCRIPT
1. Press **H**: page through all 8 lessons at GUI scale 2, 3 and 4. Plates render smooth (bilinear, no pixel stairs), labels sit
   on clear paper with leader lines ending on the right subject (vitals: lungs/heart/liver/stomach/aim; wind: deer, cone, You).
2. Lesson 2 shows the track chart (10 prints + "All prints to scale" bar) then, after paragraph 2, the rub & scrape plate; text says
   no tools are needed. Lesson 5 shows 5 paragraphs, the last naming the prone key.
3. Lesson 8 tips: four vignettes (tent, ATV, four trees, prone with bipod), each correctly cropped (no half-images).
4. `/frontierhunts tutorial reset` → welcome card banner is the painted dawn ridge with the buck, not stretched (3:1).
5. Objective card (top-left) shows the lesson medallion (wind bottle, hoofprints, binoculars, boot prints, reticle/heart,
   blood drops, knife, tent). Trigger toasts: lesson complete (lesson icon), "It ran"/"Good read" (blood drops), "Winded!"
   (scent waves), "Spotted!" (eye), "Clean hit" (reticle), "Skinned" (antlers), field notes (deer head, snowflake, leaf, wolf
   track), graduation (journal). Icons readable on the dark card at all GUI scales; fade with the card.
