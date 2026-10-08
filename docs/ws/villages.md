# Workstream `villages`: the user's buildings as planned hunting villages

(Finished by the coordinator after the agent was interrupted.)

* Removed from world generation: every livingworld building kind (structure sets living_posts, living_relics,
  living_spots deleted; structures and code kept so old worlds load). Kept: tent camps (`living_camps`, 20/8 chunks)
  and the single `tree_stand` (`living_stands`, 9/4 chunks). The standalone FS `buildings`/`hamlets` sets are deleted:
  the user's buildings only appear inside villages, plus the Crowsnest lookout as a rare standalone (`lookouts`,
  44/30 chunks, kept 14 chunks away from villages).
* Villages (`fs/java/.../Village*.java`, set `frontierstructures:villages`, 52/36 chunks): `village_outpost`,
  `village_lakeside`, `village_crowsnest`, `village_junction`. VillagePlan lays the user's templates (unchanged) around a
  central green with firepit/notice board, radial gravel paths, yards graded by TerrainFit, decorations by
  VillageDeco (fences, lanterns, benches, wood piles, gardens), terrain blend by VillageGround. Planning uses
  natural-height samples only and a per-tick budget (`Village.Budget`) so /locate never freezes.
* Verified on the headless server: all 4 villages and the lookout locate within seconds (nearest 1100-2300 blocks in
  the test seed); a crowsnest village generated naturally (rendered from the saved region files:
  docs/ws/villages/generated_crowsnest.png); no ERROR/WARN lines, no client classes, no crash.

## In-game test
1. New world. `/locate structure frontierstructures:village_outpost` (also lakeside, crowsnest, junction), teleport.
2. Check buildings sit on graded yards (no floating/buried doors), paths connect to the green, decorations look intentional.
3. `/locate structure frontierhunts:hunting_camp` and `frontierhunts:tree_stand` still work; old building kinds no longer generate.
