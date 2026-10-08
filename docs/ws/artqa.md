# Workstream `artqa` - every player-facing 2D image checked, defects fixed

Branch: `artqa` (from master 0bf1526). `tools/compile.sh` -> exit=0. `tools/build.py artqa-test` builds.
`tools/check_jar.py <jar> . --base master` -> **0 errors, 0 warnings**. Not run in-game (no client here). The in-game script is at the end.

## How it was checked
* Extracted the current build (`build.py artqa-base`) and looked at every GUI texture, every item model as the inventory
  draws it, the HUD sprites, toast/card icon atlases, slot icons, settings preset art, weather/ATV screen overlays.
* `tools/artqa/items.py` - offline inventory renderer: resolves each item model (parents, `neoforge:separate_transforms`,
  `neoforge:obj`, the mod's `field_shelter` quad loader, block-atlas `single`/`unstitch` sprites) and draws it in a slot
  at GUI scale 2/3/4 with the model's `display.gui` transform, nearest sampling and approximate GUI item lighting.
* `tools/artqa/sheets.py` - before/after sheets that simulate how the game samples each texture (nearest; old plain
  bilinear without mips; new mipmapped trilinear). `tools/artqa/plate_preview.py` - Field School plates with their
  lang labels drawn the way `GuideArt` draws them.
* Cross-checked plate content against the guide/dossier text and the mod's mechanics (vitals = heart/lung kill-cam,
  shoulder/leg non-vital, trail camera "waist high", "all prints to scale", course descriptions in docs/ws/academy.md).

## Defects found and fixed
| Image | Defect | Fix |
|---|---|---|
| academy/plate_clean ("A measured shot") | **Factual**: reticle on the top of the shoulder blade (a non-vital hit in the mod) | reticle on the vitals aim point (behind the front leg, lower third) from the shared anatomy landmarks |
| academy/plate_dressing (Field Dressing) | **Factual**: hunter kneeling beside a *standing, live* buck; game-pole loads were featureless red ovals; hunter covered the campfire | downed buck on its side, hunter at the hindquarters with a knife, two quarters in white game bags, fire moved clear |
| academy/plate_range (The Range) | **Factual**: prone "rifleman" holding nothing | scoped rifle on its bipod |
| academy/plate_tracking (Blood Trail) | **Factual**: the liver-blood trail ended at a *standing* buck (course ends at a downed buck); blood too bright for liver | downed buck past the bed; liver-dark drops |
| field_school/tracks | **Factual/visual**: black-bear front+hind prints spilled out of their mud patch over the cell divider; front pad too shallow | wider patch for the bear cell, prints re-placed inside it, metacarpal pad ~half as deep as wide (still to the common 9 px/cm scale) |
| field_school/harvest | **Anatomy/visual**: downed buck's front hoof jammed into its cheek (black wedge), throat patch floating as a white triangle; rolled hide drawn with log end-grain (read as a sawn log); backstrap a flat red mat | new downed pose (head to the ground, near fore leg forward clear of the jaw), throat/muzzle whites clipped to the head; hide laid flat hair-side up with parcels and a proper dark-red backstrap on it |
| field_school/glass | **Factual**: tree/camera at 190 px/m but deer at 84 px/m, so the "waist high" camera sat above the deer's head | one scale for tree, camera and deer; camera at ~0.9 m (deer's back); label anchor moved (`GuideArt` GLASS camera, `// [artqa]`) |
| whitetail.py (all plates using it) | throat/muzzle/chin whites could float outside the head in strong neck poses | whites clipped to the body/head silhouette (standing poses unchanged) |
| survival/hud.png #0 protein | read as a red ball/tomato at 9 px | raw steak with round bone and fat rim, same pixel style |
| survival/hud.png #6 vigor | antler read as a yellow twig | antler: burr, beam, brow tine + two points |
| item/moose_skull | **Factual**: palmate moose antlers seen edge-on looked like forked tines | re-rendered from the block model from above (`tools/polish/bone_icons.py` angle) so the palms show |
| item/aged_venison ("Aged Venison Quarter") | **Wrong thing**: a round salami slice | the venison quarter's silhouette, dry-aged (burgundy crust, ivory-yellow fat) |
| item/paraglider | 16x16 line drawing among 32x32 icons, no canopy | 32x32 ram-air canopy with cell stripes, risers, pilot |
| item/forest_litter (Leaf Litter) | used the 64x64 opaque block texture: an opaque square in the inventory | 32x32 leaf-pile icon + new item model |
| item/undergrowth (Expedition creative-tab icon) | used the 64x64 bracken block texture, cut flat at the tile edges, blurry | bracken clump in the mod's plant-icon style (from its own fern icons) + new item model |
| slot/quiver_slot (Curios quiver slot) | arrow with no head | outline quiver with three fletched arrows |
| journal/icons.png at GUI scale 3 | 32 px pixel art drawn at 48 px with bilinear = soft/blurred | `icons_x2.png` (2x nearest) sampled bilinear at in-between sizes ("sharp bilinear"); nearest at whole multiples as before |
| all bilinear GUI art (Field School plates + icons, dossier icons, journal atlas) | transparent texels stored black -> dark fringe/halo under bilinear filtering (edge luminance 0-60 vs 115-165 inside) | alpha bleed (`tools/artqa/bleed.py`), now built into `fieldart.Plate.save`, `academy/gen_icons.py`, `ledger/icons.py` |
| plates, dossier icons, guide icons, preset cards (runtime) | no mipmaps: 64 px icons drawn at 9-16 GUI px and 1024 px art drawn ~120-420 GUI px wide alias (jaggies/sparkle) at GUI scale 1-2 and small windows | `artqa/client/GuiArtTexture`: same ResourceLocation, alpha-weighted mip chain, trilinear, LOD bias -0.5; hooked in `GuideArt.filtered`, `GuideIcons.draw`, `DossierArt.smooth`, `FrontierSettingsScreen.PresetCard`, `JournalIcons.draw` (each one line, `// [artqa]`) |

Before/after: `docs/ws/artqa/academy_*.jpg`, `fieldschool_{harvest,tracks,glass}.jpg` (+ labelled `fs_*.jpg`), `hud.png`,
`items_changed.png`, `quiver_slot.png`, `journal_icons_scale3.png`, `dossier_icons_small.png`, `fieldschool_icons.png`,
`sampling_preset_card.jpg`, `sampling_fieldschool_small.jpg`; full inventory record `inventory_all_0..5.png`.

## Every image checked
### GUI textures
| Texture | Verdict | Notes |
|---|---|---|
| gui/field_school/vitals | OK (+bleed, mips) | heart low behind the elbow, lungs above/behind, diaphragm, liver behind it, rumen, intestines; aim 1/3 up behind the leg; quartering-away inset line through near ribs to far shoulder - all correct and matching labels |
| gui/field_school/wind | OK (+bleed, mips) | wind/scent cone/upwind/downwind deer and thermals (rising day, sinking evening) match `Wilderness.thermal` and the text |
| gui/field_school/tracks | **fixed** | other 8 cells checked against real sizes (whitetail 5-9, elk 10-12, moose 13-16 + dewclaws, bison 12-15, boar 5-7 + wide dewclaws, wolf/coyote claws, cougar no claws, grouse, webbed duck) |
| gui/field_school/sign | OK (+bleed, mips) | rub on a sapling, licking branch over a pawed scrape with a print in it |
| gui/field_school/glass | **fixed** | binocular dawn view OK |
| gui/field_school/stalk | OK (+bleed, mips) | wind in the face (blowing deer -> hunter), cover, head-down deer |
| gui/field_school/blood | OK (+bleed, mips) | heart bright/heavy, lung pink froth, liver dark, gut thin green-brown, muscle drips; trail, flagged last blood, bed |
| gui/field_school/harvest | **fixed** | plaque/rack OK |
| gui/field_school/tips | OK (+bleed, mips) | wall tent + stovepipe, ATV + jerry can, four seasons, prone with bipod |
| gui/field_school/welcome (1152x384) | OK (+mips) | 3:1 banner, backlit buck - silhouette a little stiff but correct |
| gui/field_school/icons (4x4, 64 px) | OK (+bleed, mips) | 16 medallions all distinct and on-topic |
| gui/academy/plate_glassing, stalk, harvest, timed, trophy, supply, track | OK (+mips) | supply crate parcels read as wrapped meat/leather; track prints are pairs along the trail |
| gui/academy/plate_clean, dressing, range, tracking | **fixed** | |
| gui/academy/icons (8x3, 64 px) | OK (+bleed, mips) | drawn tinted at 9-16 px; HARVEST vs TROPHY deer heads similar but different rack sizes |
| gui/journal/icons (32 px, 70 icons + bright half) | OK (+bleed, +x2 copy) | species prints: cloven hooves (moose/boar dewclaws), canids with claws, felids without (cheetah with), bears 5 toes (grizzly long claws, polar furred), grouse 3+1, duck webbed |
| gui/survival/hud (11 sprites) | 2 **fixed**, 9 OK | thermometers, fat, energy, wet, fire, shelter, frost read fine at 9 px |
| gui/settings/preset_classic, preset_ultra | OK (+mips) | Classic = vanilla-look scene, Ultra = realistic buck (anatomy OK) |
| gui/settings/preset_balanced | skipped | Balanced preset is being removed (presets workstream) |
| gui/settings/preset_high (512x288) | unused | no HIGH card is shown; left for the presets workstream to delete |
| gui/weather/frost_vignette, gust_sheet | OK | |
| gui/atv_grime/splatter, water | OK | mud/water atlas, already colour-bled |
| slot/quiver_slot | **fixed** | |
| effect/muzzle_flash_v2 | not GUI | world effect |
| Code-drawn GUI (no texture): temperature/fuel gauges, archery/scope HUD, Field School card frame, toasts frames, trail-camera screen and camera hub (blit the photo DynamicTextures at their own size), Expedition/journal panels | n/a | no image asset; blit sizes checked (`SurvivalHud` 18 px cells/256x32, `JournalIcons` 32/512 (+64/1024), `GuideIcons` 64/256, `DossierArt` 64/512 + 768x384, `GuideArt` 1024x512 / 1152x384, preset cards 1024x576 normalised UVs) - all match the files |

### Item icons (every `frontierhunts` item model, rendered in a slot at GUI scale 2/3/4)
* **fixed**: moose_skull, aged_venison, paraglider, forest_litter, undergrowth (see above).
* **flat 32x32 - OK**: alpine_pasture, arching_fern, attachment_tool, autumn_camo_coveralls, backstrap, bait, bear_fur, bear_fur_coat, bear_meat, bear_pelt, big_buck_board, bison_skull, blaze_camo_coveralls, bleat_call, broadleaf_thicket, buckskin_coat, buckskin_leggings, camp_post, carbon_hood, carbon_jacket, carbon_trousers, chum_bucket, cooked_backstrap, cooked_bear_meat, cooked_game, cooked_organ_meat, cooked_venison, cooked_wild_fowl, cottongrass, deer_call, deer_hide, digital_camo_coveralls, drying_rack, elk_skull, expedition_guide, fireweed, fish_finder, fishing_drag_kit, flowering_bramble, fur_hat, fur_lining, fur_mittens, fur_mukluks, fur_pelt, game_fat, game_meat, glow_lure, grunt_tube, heather, heavy_hide, hide_bedroll, hide_robe, hound_lead, huckleberry_shrub, hunter_journal, jerky, juniper_shrub, landing_net, marsh_camo_coveralls, medkit, organ_meat, pemmican, prairie_camo_coveralls, predator_call, rattling_antlers, river_brush, sagebrush, salt, scattered_bones, scent_cover, scent_suit, shed_antler, snow_camo_coveralls, spoiled_meat, spreading_fern, spruce_seedling, tallow, tanned_fur, tanned_heavy_hide, tanned_hide, timber_camo_coveralls, venison, venison_quarter, whitetail_skull, wild_fowl, wind_checker, wingsuit, woodland_bush.
* **flat 16x16 - OK** (vanilla-size twigs/sapling, readable): branch_stub, fallen_branch, forest_sticks, sapling_pole.
* **flat 256x256 - OK**: reeds (block texture as icon; mipmapped atlas in game).
* **3D block/OBJ/quad models - OK**: alder_leaves, alpine_alder_log, alpine_cottonwood_log, alpine_maple_log, alpine_pine_log, alpine_rock, alpine_rowan_log, alpine_spruce_log, ammo_reloader, aspen_leaves, aspen_log, attachment_workbench, atv, atv_can_carrier, atv_cargo_box, autumn_maple_leaves, backpacker_dome_tent, bell_tent, birch_leaves, birch_log, blue_spruce_boughs, bow_stand, bow_tuning_rack, cabin_door, cabin_lantern, camera_base_station, camp_cot, canvas_wall, canvas_wall_tent, cedar_log, cedar_planks, clothing_workbench, contract_board, cottonwood_leaves, deadfall_log, expedition_board, family_cabin_tent, field_blind, field_spotlight, fieldstone, fieldstone_stairs, fir_needles, fishing_station, forest_duff, forest_loam, game_pole, ghillie_grassland_hood, ghillie_grassland_jacket, ghillie_grassland_trousers, ghillie_hood, ghillie_jacket, ghillie_snow_hood, ghillie_snow_jacket, ghillie_snow_trousers, ghillie_trousers, ghillie_wetland_hood, ghillie_wetland_jacket, ghillie_wetland_trousers, golden_aspen_leaves, gun_rack, horse_whistle, hunters_canvas_tent, jerry_can, larch_needles, lit_lodge_table, lodge_chair, lodge_stores, lodge_stove, lodge_table, lookout_brace, lookout_cross_brace, lookout_deck_rail, lookout_stair_rail, maple_leaves, moss_floor, mossy_river_boulder, mossy_river_pebbles, mossy_river_stone, mossy_stone, pine_fence, pine_log, pine_needles, pine_planks, pine_slab, pine_stairs, pup_tent, ranger_window, reserve_granite, river_boulder, river_pebbles, river_stone, roof_shingles, roof_slab, roof_stairs, rope_ladder, rowan_leaves, shooting_target, smokehouse, solo_ridge_tent, spruce_boughs, stacked_firewood, stove_flue, stove_roof_flashing, tanning_rack, tent_bench, timber_brace, timber_cross_brace, timber_stair_guard, tower_blind, trail_camera, trail_dome_tent, trail_sign, tree_stand, trophy_plinth, weapons_workbench, weathered_planks, weathered_river_rock, whitetail_scent_decoy, willow_leaves, woodland_camp_tent. (tents/game pole/lookout parts/ranger window use vanilla textures: shown magenta/flat offline, fine in game.)
* **code loaders (reserve roof) - not offline-renderable**: roof_gable_high, roof_gable_low, roof_ridge_high, roof_ridge_low, roof_slope_high, roof_slope_low, tent_end_ridge, tent_gable, tent_ridge, tent_slope.
* **builtin/entity (code meshes: firearms, bows, optics, attachments, knives...) - not offline-renderable, outside 2D scope** (bows/rifles have their own workstreams): angled_foregrip, bait_launcher, binoculars, bipod, bone_point, bowfishing_arrow, bowfishing_bow, compound_bow, crossbow, cut_on_contact_broadhead, double_barrel, eight_power_scope, extended_magazine, field_arrow, field_battery_pack, field_bow, field_camera, field_fishing_rod, field_flashlight, field_knife, field_pistol, field_point, field_tent, fixed_broadhead, flare_gun, flare_round, flint_point, four_power_optic, holographic_sight, hunter_pack, hunters_quiver, hunting_spear, judo_point, lever_rifle, mechanical_broadhead, micro_red_dot, muzzle_brake, night_vision_binoculars, obsidian_point, pistol_magazine, pistol_round, primitive_arrow, pump_shotgun, rangefinder, recurve_bow, reflex_sight, reserve_308, revolver, ridgeline_rifle, ridgeline_scope, rifle_round, semi_auto_rifle, semi_auto_shotgun, shotgun_shell, six_power_scope, skinning_tool, sniper_magazine, steady_stock, suppressor, thermal_binoculars, thermal_scope, tracer_arrow, tracer_broadhead, tracer_field_point, tracer_ice_broadhead, tranquilizer_dart, tranquilizer_rifle, twelve_power_scope, two_power_prism, whitetail_trophy.
* **spawn eggs - vanilla template**: bison_spawn_egg, black_bear_spawn_egg, boar_spawn_egg, cheetah_spawn_egg, cougar_spawn_egg, coyote_spawn_egg, duck_spawn_egg, elk_bull_spawn_egg, elk_cow_spawn_egg, elk_spawn_egg, grizzly_spawn_egg, grouse_spawn_egg, lion_spawn_egg, moose_bull_spawn_egg, moose_cow_spawn_egg, moose_spawn_egg, panther_spawn_egg, polar_bear_spawn_egg, pronghorn_spawn_egg, whitetail_buck_spawn_egg, whitetail_doe_spawn_egg, whitetail_spawn_egg, wolf_spawn_egg.
* `frontierstructures`: dark_oak_lattice_window, spruce_casement_window, spruce_picture_window (flat front view like vanilla panes), lookout_brace/cross_brace/deck_rail/stair_rail - OK.
* Creative tabs: Hunting = whitetail buck spawn egg (vanilla template), Expedition = undergrowth (**fixed**), Journal = Hunter's Journal (OK).
* Noted, not changed (style, not error): some vegetation icons (juniper_shrub, river_brush, sagebrush, woodland_bush, spruce_seedling) are noisier and outline-free like vanilla plant icons; bear_fur is a folded bundle like tanned_fur.

## Files
New: `src/com/formaworks/frontierhunts/artqa/client/GuiArtTexture.java`; `tools/artqa/{items,sheets,plate_preview,bleed,hud_icons,item_icons}.py`;
`patch/.../textures/gui/journal/icons_x2.png`; `patch/.../textures/item/{aged_venison,paraglider,forest_litter,undergrowth}.png`;
`patch/.../models/item/{forest_litter,undergrowth}.json`; `patch/.../textures/slot/quiver_slot.png`; `docs/ws/artqa/*`.
Changed art: academy plate_{clean,dressing,range,tracking}, academy icons (bleed), field_school harvest/tracks/glass (+ bleed of all
plates/icons), journal icons (bleed), survival hud.png, item/moose_skull.
Changed code (one hook each, `// [artqa]`): `guide/client/GuideArt.java` (filtered(); GLASS camera anchor 0.895/0.663),
`guide/client/GuideIcons.java`, `academy/client/DossierArt.java` (smooth()), `journal/client/JournalIcons.java` (draw(): atlas pick +
`ATLAS_X2`), `client/FrontierSettingsScreen.java` (PresetCard: one line).
Generators: `tools/guide/{gen_art,whitetail,tracks,fieldart}.py`, `tools/academy/{gen_art,gen_icons}.py`, `tools/ledger/icons.py`,
`tools/polish/bone_icons.py` (moose angle). Re-run: `python3 tools/guide/gen_art.py . harvest tracks glass`,
`python3 tools/academy/gen_art.py . clean dressing range tracking`, `python3 tools/artqa/hud_icons.py .` (applies to the current hud.png),
`python3 tools/artqa/item_icons.py . <extracted base jar>`.

## Runtime notes / risks
* `GuiArtTexture` replaces the SimpleTexture at the same id the first time the art is drawn (render thread); a resource reload
  re-runs its `load` (vanilla reset -> register). Reads RGBA explicitly; any decode/mip failure is an IOException, so vanilla
  falls back to the missing texture instead of crashing; a failed id is never retried (no log spam). Only GUI code touches it.
* Memory: mip chains add 1/3 per texture (largest: journal icons_x2 1024x1024 = 4 MB once).

## IN-GAME TEST SCRIPT
1. GUI scale 3 (Options > Video): open the Hunter's Journal (J) - sidebar/tab icons and species prints are crisp pixel art
   (no blur); same at scale 2 and 4. Expedition journal: tab and row icons crisp.
2. GUI scale 1 and 2, small window (e.g. 854x480): press H (Field School): plates are clean, no jaggies or sparkle on ink lines;
   no dark rim around shapes. Lesson 2 track chart: bear front+hind prints inside their mud patch. Lesson 6/7 harvest plate: downed
   buck with head on the ground, flat hide with parcels. Glassing/trail-camera plate: camera at the deer's back height, label line
   ends on the camera.
3. Objective card + toasts (`/frontierhunts tutorial reset`): medallion icons clean on the dark card at scale 1-4.
4. Ranger dossier (K): sidebar ACADEMY/TOKEN/MEDAL icons and list icons (9-16 px) clean, no dark fringe. Plates: A measured
   shot - reticle behind the front leg low; Field Dressing - buck lying down, hunter at it, game bags on the pole; The Range -
   prone shooter with rifle on bipod; Blood Trail - downed buck at the end.
5. Frontier settings > Graphics preset: card art smooth (no shimmering pixels) at scale 2.
6. Frontier Survival HUD active (survival game mode): protein icon is a steak with bone; vigor (well-fed) icon an antler.
7. Creative tabs: Expedition tab icon = fern clump; Leaf Litter, Undergrowth, Paraglider, Aged Venison Quarter, Moose Skull icons
   as in docs/ws/artqa/items_changed.png. Curios quiver slot shows the quiver outline.
8. F3+T (resource reload) with the journal / guide open afterwards: art still draws (re-uploaded), no missing textures, no log spam.
