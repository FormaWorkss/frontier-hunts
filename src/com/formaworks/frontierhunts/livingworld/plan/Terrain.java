package com.formaworks.frontierhunts.livingworld.plan;

/**
 * [livingworld] Natural terrain heights before the structure is built (world coordinates). In world generation these come
 * from the chunk generator (pure functions of the seed), so every chunk of a structure sees the same values.
 */
public interface Terrain {
   /** first non-solid y above the solid ground (water does not count as ground) */
   int floor(int x, int z);

   /** first free y above ground or water surface; {@code surface > floor} means open water */
   int surface(int x, int z);

   /** biome hint for material choices: snowy / dry / wet ground, highlands; never needed for correctness */
   default String biome(int x, int z) {
      return "";
   }
}
