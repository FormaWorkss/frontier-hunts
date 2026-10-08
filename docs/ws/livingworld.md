# Workstream `livingworld`: a lived-in frontier (new chunks only)

14 world-generated hunter sites, built programmatically from the mod's own blocks (`livingworld/plan/kinds/*`), placed
by 4 structure sets (`living_camps` 20/7 chunks, `living_posts` 15/5, `living_spots` 11/4, `living_relics` 14/5),
biome-tagged, terrain-checked (rejects steep/wet ground; duck blinds need open water, glassing points a lookout).
Sites: hunting_camp (wall/bell/cabin tents, cook area, meat pole, ATV with fuel and cargo), elk_camp, outfitter_post,
ranger_station (lectern with a Frontier Handbook), trapper_cabin (tanning rack, furs), meat_shed, trailhead (board,
posters), stand_line, ground_blind_plot, duck_blind, glassing_point, fence_line, abandoned_camp, antler_cache.
Loot tables in `data/frontierhunts/loot_table/chests/living/`.

Commands: `/frontierhunts sites` (list), `sites locate <kind|all>`, `sites place <kind>` (needs suitable ground
within 64 blocks), vanilla `/locate structure frontierhunts:<kind>`, `/place structure frontierhunts:<kind>`.

Coordinator fixes (integration): jerry-can component id `frontierhunts:fuel_liters`; worldgen no longer queues
placeholder block entities for multi-part blocks (tents, posts, wide stations) whose secondary parts have none
(was 112 "Tried to load a block entity" warnings per camp).

Verified on the headless server (tools/livingworld/server_test.sh): all 14 kinds locate (nearest 200-1400 blocks
from spawn in the test seed), natural generation and /place succeed, no ERROR/WARN from our mods, no client classes.
Offline harness tools/livingworld/harness: ALL OK. Not seen with a client.

## In-game test
1. New world. `/frontierhunts sites locate all`, teleport to a few; check tents sit on the ground, boards and posters
   readable, chests have loot, the ranger station lectern holds a Handbook.
2. Fly around with Distant Horizons on: no floating or half-buried camps.
