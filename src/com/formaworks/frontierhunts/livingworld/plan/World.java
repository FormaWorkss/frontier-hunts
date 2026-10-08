package com.formaworks.frontierhunts.livingworld.plan;

/**
 * [livingworld] The live blocks a plan is executed against: a world-generation region in game, a voxel map in the offline
 * preview harness. Reads only ever look at the column being written, so the result does not depend on chunk order.
 */
public interface World {
   int AIR = 0;
   int WATER = 1;
   int LAVA = 2;
   /** natural full block: soil, stone, sand, gravel, snow block ... */
   int GROUND = 3;
   int LOG = 4;
   int LEAVES = 5;
   /** replaceable / no-collision vegetation, snow layers, sticks ... */
   int PLANT = 6;
   /** anything else with collision (boulders, fences, furniture of other structures) */
   int OTHER = 7;

   int kind(int x, int y, int z);

   void set(int x, int y, int z, String spec);

   /** copies the block (whole state) from one position to another in the same column, e.g. the natural top soil */
   void copy(int x, int fromY, int z, int toY);

   void setAir(int x, int y, int z);

   /** applies block entity data (SNBT compound) to the block entity at the position, if any */
   void nbt(int x, int y, int z, String snbt);

   void entity(double x, double y, double z, float yaw, String snbt);

   int minY();

   int maxY();
}
