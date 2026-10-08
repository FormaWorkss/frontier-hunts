package com.formaworks.frontierhunts.livingworld.plan;

/**
 * [livingworld] A small frontier building: stone or post foundation that follows the slope, plank floor, log walls with
 * notched corners, two-pane casement windows (Frontier Structures), a cabin door, a shingled gable roof with overhangs,
 * optional porch and stove pipe. Cabin-local coordinates: x 0..w-1 across the front, z 0..d-1 from the front wall back;
 * the front faces {@code front}; (ox, oz) is the front-centre cell (the door).
 */
public final class Cabin {
   public final Ctx c;
   public final int ox;
   public final int oz;
   public final Dir front;
   public final int w;
   public final int d;
   /** first air above the floor deck */
   public final int y;
   public final int wallH;
   public String log = "minecraft:spruce_log";
   public String corner = "minecraft:stripped_spruce_log";
   public String floor = "frontierhunts:pine_planks";
   public String foundation = "frontierhunts:fieldstone";
   public String roofStairs = "frontierhunts:roof_stairs";
   public String roofSlab = "frontierhunts:roof_slab";
   public String gable = "minecraft:spruce_planks";
   public String window = "frontierstructures:spruce_casement_window";
   public boolean stilts;

   public Cabin(Ctx c, int ox, int oz, Dir front, int w, int d, int y, int wallH) {
      this.c = c;
      this.ox = ox;
      this.oz = oz;
      this.front = front;
      this.w = w;
      this.d = d;
      this.y = y;
      this.wallH = wallH;
   }

   /** ctx-local x, z of a cabin cell */
   public int[] at(int cx, int cz) {
      int[] o = Kit.off(cx - this.w / 2, cz, this.front);
      return new int[]{this.ox + o[0], this.oz + o[1]};
   }

   /** a cabin-local direction (NORTH = out of the front) in ctx-local terms */
   public Dir dir(Dir cabinLocal) {
      return Kit.rel(cabinLocal, this.front);
   }

   public Plan.Block set(int cx, int yy, int cz, String spec) {
      int[] p = this.at(cx, cz);
      return this.c.set(p[0], yy, p[1], spec);
   }

   public void air(int cx, int yy, int cz) {
      int[] p = this.at(cx, cz);
      this.c.air(p[0], yy, p[1]);
   }

   static String axis(Dir d) {
      return d.dx != 0 ? "x" : "z";
   }

   /** foundation, floor, walls, door and windows (call {@link #roof} after furnishing) */
   public Cabin shell() {
      Dir along = this.dir(Dir.EAST);
      Dir depth = this.dir(Dir.SOUTH);
      for (int cx = -1; cx <= this.w; cx++) {
         for (int cz = -1; cz <= this.d; cz++) {
            int[] p = this.at(cx, cz);
            boolean inside = cx >= 0 && cx < this.w && cz >= 0 && cz < this.d;
            if (!inside) {
               Plan.Shape s = this.c.shapeAt(p[0], p[1]);
               if (s == null || s.weight < 0.9) {
                  s = this.c.shape(p[0], p[1], this.y - 1, 0.7);
                  s.clear = Math.max(s.clear, 4);
                  s.canopy = Math.max(s.canopy, 30);
               }
               continue;
            }
            boolean post = (cx == 0 || cx == this.w - 1) && (cz == 0 || cz == this.d - 1)
               || (cx == 0 || cx == this.w - 1) && cz == this.d / 2
               || (cz == 0 || cz == this.d - 1) && cx == this.w / 2;
            if (!this.stilts || post) {
               Plan.Shape s = this.c.shape(p[0], p[1], this.y - 1, 1.0);
               s.fill = this.stilts ? "minecraft:spruce_log[axis=y]" : this.foundation;
               s.surface = this.stilts ? "minecraft:spruce_log[axis=y]" : this.foundation;
               s.clear = this.wallH + 6;
               s.canopy = 40;
               s.maxFill = 16;
            } else {
               Plan.Shape s = this.c.shape(p[0], p[1], Math.min(this.y - 2, this.c.floor(p[0], p[1])), 0.0);
               s.clear = this.wallH + 6;
               s.canopy = 40;
            }
            boolean wallX = cx == 0 || cx == this.w - 1;
            boolean wallZ = cz == 0 || cz == this.d - 1;
            // [structures2] the walls stand on a visible stone sill course (cobble, moss, fieldstone), the room on planks
            boolean door = cz == 0 && cx == this.w / 2;
            this.set(cx, this.y - 1, cz, (wallX || wallZ) && !door && !this.stilts ? this.sill(cx, cz) : this.floor);
            for (int h = 0; h < this.wallH; h++) {
               int yy = this.y + h;
               if (wallX && wallZ) {
                  this.set(cx, yy, cz, this.corner + "[axis=y]");
               } else if (wallZ) {
                  this.set(cx, yy, cz, this.log + "[axis=" + axis(along) + "]");
               } else if (wallX) {
                  this.set(cx, yy, cz, this.log + "[axis=" + axis(depth) + "]");
               } else {
                  this.air(cx, yy, cz);
               }
            }
         }
      }
      // [structures2] saddle-notched corners: wall logs run one block past the corners on alternate courses
      if (!this.stilts) {
         for (int h = 0; h < this.wallH; h++) {
            int yy = this.y + h;
            if ((h & 1) == 0) {
               for (int cz : new int[]{0, this.d - 1}) {
                  for (int cx : new int[]{-1, this.w}) {
                     if (h == 0) {
                        // a footing stone under the lowest log end
                        int[] p = this.at(cx, cz);
                        Kit.ground(this.c, p[0], p[1], this.y - 1, 3);
                        this.set(cx, this.y - 1, cz, this.sill(cx, cz));
                     }
                     this.set(cx, yy, cz, this.log + "[axis=" + axis(along) + "]");
                  }
               }
            } else {
               for (int cx : new int[]{0, this.w - 1}) {
                  this.set(cx, yy, this.d, this.log + "[axis=" + axis(depth) + "]");
               }
            }
         }
      }
      // door in the front centre
      int dx = this.w / 2;
      Dir in = this.dir(Dir.SOUTH);
      this.set(dx, this.y, 0, "frontierhunts:cabin_door[facing=" + in.id() + ",half=lower,hinge=left,open=false,powered=false]");
      this.set(dx, this.y + 1, 0, "frontierhunts:cabin_door[facing=" + in.id() + ",half=upper,hinge=left,open=false,powered=false]");
      return this;
   }

   /** [structures2] mottled stone for the sill course and footings */
   String sill(int cx, int cz) {
      int[] p = this.at(cx, cz);
      Rnd r = new Rnd(Rnd.mix(this.c.seed, Plan.col(p[0], p[1]) * 31 + 7));
      double v = r.nextDouble();
      return v < 0.4 ? "minecraft:mossy_cobblestone" : v < 0.75 ? "minecraft:cobblestone" : v < 0.9 ? "frontierhunts:fieldstone" : "minecraft:andesite";
   }

   /**
    * [structures2] Settles the cabin into its site: a worn path from the door (coarse dirt, packed earth, a few gravel
    * stones), ferns, grass and brush hugging the footings and a rain barrel or wood at a back corner. Called by {@link #roof}.
    */
   public void dress() {
      Rnd r = this.c.rnd.fork(0xD2E55L);
      int dx = this.w / 2;
      for (int k = 1; k <= 7; k++) {
         int[] p = this.at(dx + (k > 4 ? r.range(-1, 1) : 0), -k);
         Kit.surface(this.c, p[0], p[1], r.chance(0.55) ? "minecraft:coarse_dirt" : r.chance(0.6) ? "minecraft:dirt_path" : "minecraft:gravel");
         if (k > 2 && r.chance(0.5)) {
            int[] q = this.at(dx + (r.chance(0.5) ? 1 : -1), -k);
            Kit.surface(this.c, q[0], q[1], r.chance(0.6) ? "minecraft:coarse_dirt" : "minecraft:rooted_dirt");
         }
      }
      for (int cx = -2; cx <= this.w + 1; cx++) {
         for (int cz = -1; cz <= this.d + 1; cz++) {
            boolean ring = cx == -2 || cx == this.w + 1 || cz == this.d + 1 || (cz == -1 && (cx == -2 || cx == this.w + 1));
            if (!ring || !r.chance(0.45)) {
               continue;
            }
            int[] p = this.at(cx, cz);
            double v = r.nextDouble();
            String plant = v < 0.32 ? "minecraft:fern" : v < 0.55 ? "minecraft:short_grass" : v < 0.72 ? Kit.FH + "spreading_fern[size=" + r.nextInt(3) + "]"
               : v < 0.9 ? Kit.FH + "woodland_bush[size=" + r.nextInt(2) + "]" : "minecraft:sweet_berry_bush[age=" + r.range(1, 3) + "]";
            this.c.drop(p[0], this.y - 1, p[1], plant);
         }
      }
      // rain barrel at a back corner
      int[] b = this.at(r.chance(0.5) ? -1 : this.w, this.d);
      if (!this.c.used(b[0], b[1]) && !this.stilts) {
         Kit.ground(this.c, b[0], b[1], this.y - 1, 3);
         this.c.set(b[0], this.y - 1, b[1], "minecraft:barrel[facing=up,open=false]");
      }
   }

   /** a two-pane window in a wall cell (sill at floor + 1) */
   public void window(int cx, int cz, Dir outward) {
      Dir o = this.dir(outward);
      this.set(cx, this.y + 1, cz, this.window + "[facing=" + o.id() + ",joined_above=true,joined_below=false]");
      if (this.wallH > 3) {
         this.set(cx, this.y + 2, cz, this.window + "[facing=" + o.id() + ",joined_above=false,joined_below=true]");
      } else {
         this.set(cx, this.y + 1, cz, this.window + "[facing=" + o.id() + ",joined_above=false,joined_below=false]");
      }
   }

   /** default windows: one each side of the door, one on each side wall, one at the back */
   public Cabin windows() {
      int dx = this.w / 2;
      if (this.w >= 5) {
         this.window(dx - 2, 0, Dir.NORTH);
         this.window(dx + 2, 0, Dir.NORTH);
      }
      if (this.d >= 4) {
         this.window(0, this.d / 2, Dir.WEST);
         this.window(this.w - 1, this.d / 2, Dir.EAST);
      }
      if (this.w >= 5) {
         this.window(dx, this.d - 1, Dir.SOUTH);
      }
      return this;
   }

   /** gable roof with a ridge running across the front (along x), one block of overhang all round */
   public Cabin roof() {
      int base = this.y + this.wallH;
      Dir up = this.dir(Dir.SOUTH);
      Dir down = this.dir(Dir.NORTH);
      int zf = -1;
      int zb = this.d;
      int k = 0;
      while (zf <= zb) {
         int yy = base + k;
         for (int cx = -1; cx <= this.w; cx++) {
            if (zf == zb) {
               this.set(cx, yy, zf, this.roofSlab + "[type=bottom,waterlogged=false]");
            } else {
               this.set(cx, yy, zf, this.roofStairs + "[facing=" + up.id() + ",half=bottom,shape=straight,waterlogged=false]");
               this.set(cx, yy, zb, this.roofStairs + "[facing=" + down.id() + ",half=bottom,shape=straight,waterlogged=false]");
            }
         }
         // gable ends between the slopes
         for (int cz = zf + 1; cz <= zb - 1; cz++) {
            if (cz >= 0 && cz < this.d) {
               this.set(0, yy, cz, this.gable);
               this.set(this.w - 1, yy, cz, this.gable);
               for (int cx = 1; cx < this.w - 1; cx++) {
                  if (k == 0 && (cz == 0 || cz == this.d - 1)) {
                     // wall plate under the eaves
                     this.set(cx, yy, cz, this.log + "[axis=" + axis(this.dir(Dir.EAST)) + "]");
                  } else {
                     this.air(cx, yy, cz);
                  }
               }
            }
         }
         if (zf + 1 == zb) {
            // even depth: cap the ridge
            for (int cx = -1; cx <= this.w; cx++) {
               this.set(cx, yy + 1, zf, this.roofSlab + "[type=bottom,waterlogged=false]");
               this.set(cx, yy + 1, zb, this.roofSlab + "[type=bottom,waterlogged=false]");
            }
         }
         zf++;
         zb--;
         k++;
      }
      this.dress();
      return this;
   }

   /** a log tie beam across the room under the roof at cabin row cz (something for lanterns to hang from) */
   public void tieBeam(int cz) {
      for (int cx = 1; cx < this.w - 1; cx++) {
         this.set(cx, this.y + this.wallH, cz, this.corner + "[axis=" + axis(this.dir(Dir.EAST)) + "]");
      }
   }

   /** a lantern hanging under the tie beam at (cx, cz) */
   public void hangingLantern(int cx, int cz) {
      this.set(cx, this.y + this.wallH - 1, cz, "minecraft:lantern[hanging=true,waterlogged=false]");
   }

   /** top y of the roof surface above cabin cell cz (for stove pipes) */
   public int roofY(int cz) {
      int fromFront = cz + 1;
      int fromBack = this.d - cz;
      return this.y + this.wallH + Math.min(fromFront, fromBack) - 1;
   }

   /** wood stove against an inside wall with its flue through the roof */
   public void stove(int cx, int cz, Dir faceInto) {
      Dir f = this.dir(faceInto);
      this.set(cx, this.y, cz, "frontierhunts:lodge_stove[bench_part=single,facing=" + f.id() + ",lit=false]");
      int top = this.roofY(cz);
      for (int yy = this.y + 1; yy <= top + 2; yy++) {
         this.set(cx, yy, cz, yy == top ? "frontierhunts:stove_roof_flashing[facing=" + f.id() + "]" : "frontierhunts:stove_flue[facing=" + f.id() + "]");
      }
   }

   /** a covered front porch {@code depth} deep across the whole front: deck, posts, rails, flat shingle roof, steps */
   public void porch(int depth, boolean rails) {
      Dir out = this.dir(Dir.NORTH);
      int dx = this.w / 2;
      for (int cx = -1; cx <= this.w; cx++) {
         for (int k = 1; k <= depth; k++) {
            int cz = -k;
            int[] p = this.at(cx, cz);
            boolean post = (cx == -1 || cx == this.w) && k == depth;
            Plan.Shape s = this.c.shape(p[0], p[1], this.y - 1, 1.0);
            s.fill = post ? "minecraft:spruce_log[axis=y]" : null;
            s.surface = post ? "minecraft:spruce_log[axis=y]" : null;
            s.clear = this.wallH + 5;
            s.canopy = 40;
            this.set(cx, this.y - 1, cz, "minecraft:spruce_planks");
            if (post) {
               for (int h = 0; h < this.wallH; h++) {
                  this.set(cx, this.y + h, cz, "minecraft:stripped_spruce_log[axis=y]");
               }
            } else if (rails && (cx == -1 || cx == this.w || k == depth && Math.abs(cx - dx) > 1)) {
               this.set(cx, this.y, cz, "minecraft:spruce_fence");
            }
            this.set(cx, this.y + this.wallH, cz, this.roofSlab + "[type=bottom,waterlogged=false]");
         }
      }
      // steps down from the porch at the door line
      for (int sx = dx - 1; sx <= dx + 1; sx++) {
         int[] st = this.at(sx, -depth - 1);
         Plan.Shape s = this.c.shapeAt(st[0], st[1]);
         if (s == null || s.weight < 1.0) {
            s = this.c.shape(st[0], st[1], this.y - 1, 1.0);
         }
         s.clear = Math.max(s.clear, 4);
         this.c.set(st[0], this.y - 1, st[1], "minecraft:spruce_stairs[facing=" + this.dir(Dir.SOUTH).id() + ",half=bottom,shape=straight,waterlogged=false]");
      }
      for (int k = 1; k <= depth; k++) {
         this.air(dx, this.y, -k);
      }
   }
}
