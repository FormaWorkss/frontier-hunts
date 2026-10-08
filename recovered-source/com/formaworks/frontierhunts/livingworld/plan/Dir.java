package com.formaworks.frontierhunts.livingworld.plan;

/** [livingworld] Horizontal direction, Minecraft convention (north = -z, east = +x). */
public enum Dir {
   NORTH(0, -1),
   EAST(1, 0),
   SOUTH(0, 1),
   WEST(-1, 0);

   public final int dx;
   public final int dz;

   Dir(int dx, int dz) {
      this.dx = dx;
      this.dz = dz;
   }

   public Dir cw() {
      return values()[(this.ordinal() + 1) & 3];
   }

   public Dir ccw() {
      return values()[(this.ordinal() + 3) & 3];
   }

   public Dir opposite() {
      return values()[(this.ordinal() + 2) & 3];
   }

   /** rotated clockwise by quarter turns */
   public Dir rot(int quarters) {
      return values()[(this.ordinal() + quarters) & 3];
   }

   public String id() {
      return this.name().toLowerCase(java.util.Locale.ROOT);
   }

   public static Dir of(String id) {
      for (Dir d : values()) {
         if (d.id().equals(id)) {
            return d;
         }
      }
      return null;
   }

   /** yaw in degrees for an entity looking this way (Minecraft: south = 0, west = 90, north = 180, east = 270) */
   public float yaw() {
      return switch (this) {
         case SOUTH -> 0.0F;
         case WEST -> 90.0F;
         case NORTH -> 180.0F;
         case EAST -> 270.0F;
      };
   }
}
