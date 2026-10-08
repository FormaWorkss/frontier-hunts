package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

public final class Cascade extends Feature<NoneFeatureConfiguration> {
   private static final int MIN_DROP = 14;
   private static final int MAX_DROP = 160;
   private static final int EDGE = 3;
   private static final int TRIES = 12;
   private static final byte NONE = 0;
   private static final byte AIR = 1;
   private static final byte ROCK = 2;
   private static final byte WATER = 3;
   private static final byte OUTLET = 4;
   private static final byte BANK = 5;
   private static final byte SHORE = 6;

   public Cascade() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
      if (!(Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get()) {
         return false;
      } else {
         WorldGenLevel level = context.level();
         RandomSource random = context.random();
         Long2IntOpenHashMap heights = new Long2IntOpenHashMap(4096);
         heights.defaultReturnValue(Integer.MIN_VALUE);

         for (int i = 0; i < 12; i++) {
            BlockPos probe = i == 0 ? context.origin() : context.origin().offset(random.nextInt(19) - 9, 0, random.nextInt(19) - 9);

            for (Direction way : Plane.HORIZONTAL) {
               if (build(level, probe, way, random, heights)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static int room(WorldGenLevel level, BlockPos from, Direction way, int want) {
      for (int i = 1; i <= want; i++) {
         if (!Terrain.writable(level, from.relative(way, i))) {
            return i - 1;
         }
      }

      return want;
   }

   private static boolean build(WorldGenLevel level, BlockPos origin, Direction down, RandomSource random, Long2IntOpenHashMap heights) {
      if (!Terrain.writable(level, origin)) {
         return false;
      } else {
         Direction across = down.getClockWise();
         int left = room(level, origin, across.getOpposite(), 20);
         int right = room(level, origin, across, 20);
         int ahead = room(level, origin, down, 30);
         int behind = room(level, origin, down.getOpposite(), 16);
         int W = Math.min(Math.min(left, right), 16);
         if (W >= 6 && ahead >= 12 && behind >= 6) {
            Cascade.Sculpt sculpt = new Cascade.Sculpt(level, origin, across, down, W, behind, ahead, random, heights);
            if (!sculpt.readLand()) {
               return false;
            } else if (!sculpt.shape()) {
               return false;
            } else {
               sculpt.seal();
               sculpt.emit();
               sculpt.dress();
               return true;
            }
         } else {
            return false;
         }
      }
   }

   private static final class Sculpt {
      private final WorldGenLevel level;
      private final BlockPos origin;
      private final Direction across;
      private final Direction down;
      private final RandomSource random;
      private final int W;
      private final int D0;
      private final int D1;
      private final int dSpan;
      private int Y0;
      private int Y1;
      private int ySpan;
      private byte[] cell;
      private final int[] ground;
      private BlockState nativeRock = Blocks.STONE.defaultBlockState();
      private final int[] lip;
      private final int[] drop;
      private int w0;
      private int w1;
      private int surfaceY;
      private int lipD;
      private int floorY;
      private int poolBack;
      private int poolFront;
      private final List<int[]> benches = new ArrayList<>();
      private final boolean[] notch;
      private int wide;

      Sculpt(
         WorldGenLevel level, BlockPos origin, Direction across, Direction down, int W, int behind, int ahead, RandomSource random, Long2IntOpenHashMap heights
      ) {
         this.level = level;
         this.origin = origin;
         this.across = across;
         this.down = down;
         this.random = random;
         this.W = W;
         this.D0 = -behind;
         this.D1 = ahead;
         this.dSpan = this.D1 - this.D0 + 1;
         this.ground = new int[(W * 2 + 1) * this.dSpan];
         this.lip = new int[W * 2 + 1];
         this.drop = new int[W * 2 + 1];
         this.notch = new boolean[W * 2 + 1];
         MutableBlockPos cursor = new MutableBlockPos();

         for (int w = -W; w <= W; w++) {
            for (int d = this.D0; d <= this.D1; d++) {
               int x = origin.getX() + across.getStepX() * w + down.getStepX() * d;
               int z = origin.getZ() + across.getStepZ() * w + down.getStepZ() * d;
               long key = BlockPos.asLong(x, 0, z);
               int height = heights.get(key);
               if (height == Integer.MIN_VALUE) {
                  height = level.getHeight(level instanceof WorldGenRegion ? Types.OCEAN_FLOOR_WG : Types.OCEAN_FLOOR, x, z);

                  for (int descent = 0; descent < 96 && height > level.getMinBuildHeight() + 1; descent++) {
                     BlockState top = level.getBlockState(cursor.set(x, height - 1, z));
                     if (!top.isAir() && !Terrain.grown(top)) {
                        break;
                     }

                     height--;
                  }

                  heights.put(key, height);
               }

               this.ground[(w + W) * this.dSpan + (d - this.D0)] = height;
            }
         }
      }

      private BlockPos raw(int w, int d) {
         return this.origin.relative(this.across, w).relative(this.down, d);
      }

      private BlockPos at(int w, int d, int y) {
         return this.raw(w, d).atY(y);
      }

      private int soil(int w, int d) {
         int cw = Mth.clamp(w, -this.W, this.W);
         int cd = Mth.clamp(d, this.D0, this.D1);
         return this.ground[(cw + this.W) * this.dSpan + (cd - this.D0)];
      }

      private boolean in(int w, int d, int y) {
         return Math.abs(w) <= this.W && d >= this.D0 && d <= this.D1 && y >= this.Y0 && y <= this.Y1;
      }

      private byte get(int w, int d, int y) {
         return this.in(w, d, y) ? this.cell[((w + this.W) * this.dSpan + (d - this.D0)) * this.ySpan + (y - this.Y0)] : 0;
      }

      private void set(int w, int d, int y, byte v) {
         if (this.in(w, d, y)) {
            this.cell[((w + this.W) * this.dSpan + (d - this.D0)) * this.ySpan + (y - this.Y0)] = v;
         }
      }

      private int[] wander(int amplitude) {
         int n = this.W * 2 + 1;
         float[] raw = new float[n];

         for (int i = 0; i < n; i++) {
            raw[i] = this.random.nextFloat();
         }

         int[] out = new int[n];

         for (int i = 0; i < n; i++) {
            float s = 0.0F;

            for (int k = -2; k <= 2; k++) {
               s += raw[Mth.clamp(i + k, 0, n - 1)];
            }

            out[i] = Math.round((s / 5.0F - 0.5F) * 2.0F * (float)amplitude);
         }

         return out;
      }

      boolean readLand() {
         for (int w = -this.W; w <= this.W; w++) {
            int best = 0;
            int bestD = -1;

            for (int d = 0; d <= this.D1 - 3 - 6; d++) {
               int fall = this.soil(w, d) - this.soil(w, d + 3);
               if (fall > best) {
                  best = fall;
                  bestD = d;
                  int steepest = Integer.MIN_VALUE;

                  for (int edge = d; edge < d + 3; edge++) {
                     int step = this.soil(w, edge) - this.soil(w, edge + 1);
                     if (step > steepest) {
                        steepest = step;
                        bestD = edge;
                     }
                  }
               }
            }

            this.lip[w + this.W] = bestD;
            this.drop[w + this.W] = best;
         }

         int bestLen = 0;
         int bestStart = 0;

         for (int s = -this.W; s <= this.W; s++) {
            if (this.drop[s + this.W] >= 14) {
               int e = s;

               while (e + 1 <= this.W && this.drop[e + 1 + this.W] >= 14 && Math.abs(this.lip[e + 1 + this.W] - this.lip[e + this.W]) <= 2) {
                  e++;
               }

               if (e - s + 1 > bestLen) {
                  bestLen = e - s + 1;
                  bestStart = s;
               }

               s = e;
            }
         }

         if (bestLen < 7) {
            return false;
         } else {
            this.w0 = bestStart;
            this.w1 = bestStart + bestLen - 1;
            if (bestLen > 23) {
               int centre = (this.w0 + this.w1) / 2;
               this.w0 = centre - 11;
               this.w1 = centre + 11;
            }

            this.w0 = Math.max(this.w0, -this.W + 2);
            this.w1 = Math.min(this.w1, this.W - 2);
            if (this.w1 - this.w0 + 1 < 5) {
               return false;
            } else {
               int mid = (this.w0 + this.w1) / 2;
               this.lipD = this.lip[mid + this.W];
               int[] tops = new int[this.w1 - this.w0 + 1];

               for (int w = this.w0; w <= this.w1; w++) {
                  tops[w - this.w0] = this.soil(w, this.lip[w + this.W]);
               }

               Arrays.sort(tops);
               this.surfaceY = tops[Math.max(0, tops.length / 3)];
               int y = this.surfaceY;
               int dx = this.lipD + 1;

               while (dx <= this.D1 - 2) {
                  int g = this.bandGround(dx);
                  if (g <= y - 3) {
                     int flat = 0;

                     while (dx + flat <= this.D1 && Math.abs(this.bandGround(dx + flat) - g) <= 1) {
                        flat++;
                     }

                     if (flat >= 3) {
                        if (y - g >= 6 && this.benches.size() < 5) {
                           this.benches.add(new int[]{dx, g});
                        }

                        y = g;
                        dx += flat;
                        continue;
                     }
                  }

                  dx++;
               }

               int bottom = this.surfaceY;

               for (int[] b : this.benches) {
                  bottom = b[1];
               }

               int footD = this.benches.isEmpty() ? this.lipD + 1 : this.benches.get(this.benches.size() - 1)[0];
               int run = footD;
               int level0 = this.bandGround(Math.min(this.D1, footD));

               for (int i = footD; i <= this.D1 - 1; run = i++) {
                  int g = this.bandGround(i);
                  if (g > level0 + 2) {
                     break;
                  }

                  level0 = Math.min(level0, g);
               }

               if (this.benches.size() > 0 && this.benches.get(this.benches.size() - 1)[0] >= run - 2) {
                  this.benches.remove(this.benches.size() - 1);
               }

               this.floorY = level0;

               while (!this.benches.isEmpty() && ((int[])this.benches.get(this.benches.size() - 1))[1] <= this.floorY + 2) {
                  this.benches.remove(this.benches.size() - 1);
               }

               this.poolBack = Math.min(this.D1 - 4, (this.benches.isEmpty() ? this.lipD : this.benches.get(this.benches.size() - 1)[0]) + 1);
               this.poolBack = Math.max(this.poolBack, 1);
               this.poolFront = Math.min(this.D1 - 1, Math.max(this.poolBack + 5, run));
               if (this.surfaceY - this.floorY < 14 || this.surfaceY - this.floorY > 160) {
                  return false;
               } else if (this.poolFront - this.poolBack < 4) {
                  return false;
               } else {
                  this.Y0 = this.floorY - 6;
                  this.Y1 = this.surfaceY + 10;
                  this.ySpan = this.Y1 - this.Y0 + 1;
                  this.cell = new byte[(this.W * 2 + 1) * this.dSpan * this.ySpan];
                  BlockState under = this.level.getBlockState(this.at(0, this.lipD, (this.surfaceY + this.floorY) / 2));
                  if (under.isSolid() && !under.is(BlockTags.DIRT) && under.getFluidState().isEmpty()) {
                     this.nativeRock = under;
                  }

                  return true;
               }
            }
         }
      }

      private int bandGround(int d) {
         int sum = 0;
         int n = 0;

         for (int w = this.w0; w <= this.w1; w++) {
            sum += this.soil(w, d);
            n++;
         }

         return n == 0 ? 0 : Math.round((float)sum / (float)n);
      }

      boolean shape() {
         int span = (this.w1 - this.w0) / 2;
         this.wide = Mth.clamp(2 + this.random.nextInt(4), 2, Math.max(2, span - 1));
         int mid = (this.w0 + this.w1) / 2;
         int streams = 1 + (this.w1 - this.w0) / 7 + this.random.nextInt(2);
         int placed = 0;

         for (int s = 0; s < streams * 4 && placed < streams; s++) {
            int c = mid + this.random.nextInt(span * 2 + 1) - span;
            int hw = this.random.nextInt(this.wide) + 1;
            if (c - hw >= this.w0 + 1 && c + hw <= this.w1 - 1) {
               boolean clear = true;

               for (int w = c - hw - 2; w <= c + hw + 2; w++) {
                  if (Math.abs(w) <= this.W && this.notch[w + this.W]) {
                     clear = false;
                  }
               }

               if (clear) {
                  for (int wx = c - hw; wx <= c + hw; wx++) {
                     this.notch[wx + this.W] = true;
                  }

                  placed++;
               }
            }
         }

         if (placed == 0) {
            for (int wx = mid - 1; wx <= mid + 1; wx++) {
               this.notch[wx + this.W] = true;
            }
         }

         int shallowest = this.D1;

         for (int wx = this.w0; wx <= this.w1; wx++) {
            shallowest = Math.min(shallowest, this.lip[Mth.clamp(wx, -this.W, this.W) + this.W]);
         }

         int reach = Math.min(5 + this.random.nextInt(6), shallowest - this.D0 - 2);
         if (reach < 3) {
            return false;
         } else {
            int[] drift = this.wander(2);
            int centreW = (this.w0 + this.w1) / 2;

            for (int d = -reach; d <= 0; d++) {
               double t = (double)(d + reach) / (double)reach;
               int halfW = Math.round(Mth.lerp((float)t, 1.6F, (float)(this.w1 - this.w0) / 2.0F));
               int shift = drift[Mth.clamp(centreW, -this.W, this.W) + this.W]
                  + (int)Math.round(
                     (1.0 - t)
                        * 3.0
                        * (double)(drift[Mth.clamp(centreW + 3, -this.W, this.W) + this.W] - drift[Mth.clamp(centreW - 3, -this.W, this.W) + this.W])
                  );
               int lo = Mth.clamp(centreW + shift - halfW, this.w0, this.w1);
               int hi = Mth.clamp(centreW + shift + halfW, this.w0, this.w1);

               for (int wx = lo - 1; wx <= hi + 1; wx++) {
                  int edge = this.lip[Mth.clamp(wx, -this.W, this.W) + this.W];
                  int dd = edge + d;

                  for (int y = this.surfaceY; y <= this.surfaceY + 9; y++) {
                     this.set(wx, dd, y, (byte)1);
                  }

                  this.set(wx, dd, this.surfaceY - 1, (byte)2);

                  for (int i = 2; i <= 6; i++) {
                     BlockState here = this.level.getBlockState(this.at(wx, dd, this.surfaceY - i));
                     if (here.isSolid() && !Terrain.grown(here)) {
                        break;
                     }

                     this.set(wx, dd, this.surfaceY - i, (byte)2);
                  }
               }

               for (int wx = lo; wx <= hi; wx++) {
                  int edge = this.lip[Mth.clamp(wx, -this.W, this.W) + this.W];
                  this.set(wx, edge + d, this.surfaceY, (byte)3);
               }
            }

            for (int wx = this.w0; wx <= this.w1; wx++) {
               int edge = this.lip[Mth.clamp(wx, -this.W, this.W) + this.W];
               if (this.get(wx, edge, this.surfaceY) == 3) {
                  if (!this.notch[wx + this.W]) {
                     this.set(wx, edge, this.surfaceY, (byte)2);
                  } else {
                     for (int o = 1; o <= 2; o++) {
                        this.set(wx, edge + o, this.surfaceY, (byte)4);
                     }
                  }
               }
            }

            int prevFront = this.lipD;

            for (int[] b : this.benches) {
               int bd = b[0];
               int by = b[1];
               int flat = 1;

               while (bd + flat <= this.D1 && Math.abs(this.bandGround(bd + flat) - by) <= 1) {
                  flat++;
               }

               int frontD = Math.min(bd + Math.max(2, flat - 1), this.D1 - 4);
               if (frontD > bd) {
                  for (int wxx = this.w0 - 1; wxx <= this.w1 + 1; wxx++) {
                     for (int d = bd - 1; d <= frontD + 1; d++) {
                        for (int y = by + 1; y <= by + 4; y++) {
                           this.set(wxx, d, y, (byte)1);
                        }

                        this.set(wxx, d, by, (byte)2);
                        this.set(wxx, d, by - 1, (byte)2);
                     }
                  }

                  for (int wxx = this.w0 + 1; wxx <= this.w1 - 1; wxx++) {
                     for (int d = bd; d <= frontD; d++) {
                        this.set(wxx, d, by + 1, (byte)3);
                     }

                     for (int o = 1; o <= 3; o++) {
                        this.set(wxx, frontD + o, by + 1, (byte)4);
                     }
                  }

                  prevFront = frontD;
               }
            }

            int[] bank = this.wander(2);
            int poolHalf = Math.max(2, (this.w1 - this.w0) / 2 - 1);
            int centre = (this.poolBack + this.poolFront) / 2;

            for (int wxx = this.w0 - 1; wxx <= this.w1 + 1; wxx++) {
               int edge = Mth.clamp(this.poolFront + bank[Mth.clamp(wxx, -this.W, this.W) + this.W], this.poolBack + 3, this.D1 - 2);
               double ew = ((double)wxx - (double)(this.w0 + this.w1) / 2.0) / ((double)poolHalf + 0.5);

               for (int d = this.poolBack; d <= edge; d++) {
                  double ed = (double)(d - centre) / ((double)(edge - centre) + 0.5);
                  double r = Math.sqrt(ew * ew + ed * ed);
                  if (r > 1.0) {
                     if (r < 1.28 && this.soil(wxx, d) <= this.floorY + 2) {
                        this.set(wxx, d, this.floorY, (byte)6);

                        for (int y = this.floorY + 1; y <= this.floorY + 3; y++) {
                           this.set(wxx, d, y, (byte)1);
                        }
                     }
                  } else {
                     int deep = r < 0.42 ? 3 : (r < 0.76 ? 2 : 1);

                     for (int i = 0; i < deep; i++) {
                        this.set(wxx, d, this.floorY - i, (byte)3);
                     }

                     this.set(wxx, d, this.floorY - deep, (byte)2);

                     for (int y = this.floorY + 1; y <= this.floorY + 5; y++) {
                        this.set(wxx, d, y, (byte)1);
                     }
                  }
               }
            }

            int lastFront = this.benches.isEmpty() ? this.lipD : prevFront;

            for (int wxx = this.w0; wxx <= this.w1; wxx++) {
               for (int o = 1; o <= 4; o++) {
                  int dx = Math.min(this.D1 - 2, lastFront + o);
                  if (dx >= this.poolBack) {
                     for (int i = 0; i < 2; i++) {
                        this.set(wxx, dx, this.floorY - i, (byte)3);
                     }

                     this.set(wxx, dx, this.floorY - 2, (byte)2);

                     for (int y = this.floorY + 1; y <= this.floorY + 5; y++) {
                        this.set(wxx, dx, y, (byte)1);
                     }
                  }
               }
            }

            for (int wxx = this.w0; wxx <= this.w1; wxx++) {
               if (this.notch[wxx + this.W]) {
                  int edge = this.lip[Mth.clamp(wxx, -this.W, this.W) + this.W];

                  for (int ox = 1; ox <= 3; ox++) {
                     int dx = edge + ox;

                     for (int y = this.surfaceY; y > this.floorY && this.get(wxx, dx, y) != 3; y--) {
                        if (this.get(wxx, dx, y) == 0) {
                           this.set(wxx, dx, y, (byte)1);
                        }
                     }
                  }
               }
            }

            for (int wxxx = -this.W; wxxx <= this.W; wxxx++) {
               for (int dx = this.D0; dx <= this.D1; dx++) {
                  for (int yx = this.Y1; yx >= this.Y0; yx--) {
                     if (this.get(wxxx, dx, yx) == 4) {
                        boolean lands = false;

                        for (int u = yx - 1; u >= this.floorY - 3 && !lands; u--) {
                           if (this.get(wxxx, dx, u) == 3) {
                              lands = true;
                           }
                        }

                        if (!lands) {
                           if (dx >= this.poolBack
                              && dx <= this.D1 - 2
                              && Math.abs(wxxx) <= this.W - 2
                              && yx > this.floorY
                              && this.soil(wxxx, dx) >= this.floorY - 2) {
                              for (int i = 0; i < 2; i++) {
                                 this.set(wxxx, dx, this.floorY - i, (byte)3);
                              }

                              this.set(wxxx, dx, this.floorY - 2, (byte)2);

                              for (int ux = this.floorY + 1; ux <= yx; ux++) {
                                 if (this.get(wxxx, dx, ux) == 0) {
                                    this.set(wxxx, dx, ux, (byte)1);
                                 }
                              }
                           } else {
                              this.set(wxxx, dx, yx, (byte)2);
                           }
                        }
                     }
                  }
               }
            }

            return true;
         }
      }

      void seal() {
         int[][] sides = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

         for (int w = -this.W; w <= this.W; w++) {
            for (int d = this.D0; d <= this.D1; d++) {
               for (int y = this.Y0; y <= this.Y1; y++) {
                  if (this.get(w, d, y) == 3) {
                     for (int[] s : sides) {
                        byte n = this.get(w + s[0], d + s[1], y);
                        if (n != 3 && n != 4) {
                           this.set(w + s[0], d + s[1], y, (byte)2);
                        }
                     }

                     if (this.get(w, d, y - 1) != 3) {
                        this.set(w, d, y - 1, (byte)2);
                     }
                  }
               }
            }
         }

         for (int w = -this.W; w <= this.W; w++) {
            for (int d = this.D0; d <= this.D1; d++) {
               for (int yx = this.Y1; yx >= this.Y0; yx--) {
                  if (this.get(w, d, yx) == 4) {
                     for (int u = yx - 1; u >= this.Y0 && this.get(w, d, u) != 3; u--) {
                        this.set(w, d, u, (byte)1);
                     }
                  }
               }
            }
         }
      }

      private BlockState rock(double wet) {
         float f = this.random.nextFloat();
         if ((double)f < wet) {
            return this.random.nextFloat() < 0.55F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState();
         } else if ((double)f < wet + 0.34F) {
            return this.nativeRock;
         } else {
            return this.random.nextFloat() < 0.5F
               ? Blocks.COBBLESTONE.defaultBlockState()
               : (this.random.nextFloat() < 0.5F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
         }
      }

      private boolean near(int w, int d, int y) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dw = -1; dw <= 1; dw++) {
               for (int dd = -1; dd <= 1; dd++) {
                  if (this.get(w + dw, d + dd, y + dy) == 3) {
                     return true;
                  }
               }
            }
         }

         return false;
      }

      void emit() {
         BlockState water = Blocks.WATER.defaultBlockState();
         BlockState air = Blocks.AIR.defaultBlockState();

         for (int w = -this.W; w <= this.W; w++) {
            for (int d = this.D0; d <= this.D1; d++) {
               for (int y = this.Y0; y <= this.Y1; y++) {
                  byte v = this.get(w, d, y);
                  if (v != 0) {
                     BlockPos pos = this.at(w, d, y);
                     if (Terrain.writable(this.level, pos)) {
                        switch (v) {
                           case 1:
                           case 4:
                              if (!this.level.getBlockState(pos).isAir()) {
                                 Terrain.set(this.level, pos, air);
                              }
                              break;
                           case 2:
                              Terrain.set(this.level, pos, this.rock(this.near(w, d, y) ? 0.44 : 0.12));
                              break;
                           case 3:
                              Terrain.set(this.level, pos, water);
                              this.level.scheduleTick(pos, Fluids.WATER, 0);
                              break;
                           case 5:
                              Terrain.set(this.level, pos, Blocks.GRASS_BLOCK.defaultBlockState());
                              break;
                           case 6:
                              Terrain.set(
                                 this.level, pos, this.random.nextFloat() < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState()
                              );
                        }
                     }
                  }
               }
            }
         }
      }

      void dress() {
         BlockState litter = ((ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get()).defaultBlockState();
         BlockState growth = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();
         BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
         BlockState boulder = ((ForestFloor.Boulder)ExpeditionContent.MOSSY_STONE.get()).defaultBlockState();
         Direction facing = this.down.getOpposite();
         BlockState vine = (BlockState)Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(facing), true);

         for (int w = -this.W; w <= this.W; w++) {
            for (int d = this.D0; d <= this.D1; d++) {
               for (int y = this.Y0 + 1; y <= this.Y1; y++) {
                  if (this.get(w, d, y) == 2) {
                     byte over = this.get(w, d, y + 1);
                     if (over == 1 || over == 0) {
                        BlockPos above = this.at(w, d, y + 1);
                        if (Terrain.writable(this.level, above) && this.level.getBlockState(above).isAir()) {
                           boolean wet = this.near(w, d, y);
                           float roll = this.random.nextFloat();
                           if (roll < (wet ? 0.6F : 0.36F)) {
                              Terrain.set(this.level, this.at(w, d, y), moss);
                              if (this.random.nextFloat() < 0.78F) {
                                 Terrain.set(
                                    this.level,
                                    above,
                                    (BlockState)growth.setValue(
                                       ForestFloor.Undergrowth.VARIANT, this.random.nextFloat() < 0.55F ? this.random.nextInt(2) : 5 + this.random.nextInt(2)
                                    )
                                 );
                              }
                           } else if (roll < 0.46F) {
                              Terrain.set(this.level, above, boulder);
                           } else if (roll < 0.55F) {
                              Terrain.set(this.level, above, (BlockState)litter.setValue(ForestFloor.Litter.VARIANT, this.random.nextInt(3)));
                           }
                        }
                     }
                  }
               }
            }
         }

         for (int w = this.w0 - 2; w <= this.w1 + 2; w++) {
            if (!(this.random.nextFloat() < 0.35F)) {
               int edge = this.lip[Mth.clamp(w, -this.W, this.W) + this.W] + 1 + this.random.nextInt(2);
               int start = this.surfaceY - 1 - this.random.nextInt(3);

               for (int i = 0; i < 4 + this.random.nextInt(10); i++) {
                  BlockPos pos = this.at(w, edge, start - i);
                  if (start - i <= this.floorY + 1
                     || !Terrain.writable(this.level, pos)
                     || !this.level.getBlockState(pos).isAir()
                     || !this.level.getBlockState(pos.relative(facing)).isSolid()) {
                     break;
                  }

                  Terrain.set(this.level, pos, vine);
               }
            }
         }

         for (int n = 0; n < 14; n++) {
            int wx = this.w0 - 3 + this.random.nextInt(this.w1 - this.w0 + 7);
            int d = this.D0 + this.random.nextInt(Math.max(1, -this.D0 + 2));
            BlockPos spot = Terrain.soil(this.level, this.at(wx, d, this.surfaceY));
            if (spot != null) {
               ForestStand.plant(
                  this.level, spot, this.random.nextBoolean() ? WildTrees.Kind.FIR : WildTrees.Kind.PINE, this.random, 0.3F + this.random.nextFloat() * 0.5F
               );
            }
         }

         for (int nx = 0; nx < 10; nx++) {
            int wx = this.w0 - 3 + this.random.nextInt(this.w1 - this.w0 + 7);
            int d = this.poolFront + 1 + this.random.nextInt(4);
            BlockPos spot = Terrain.soil(this.level, this.at(wx, d, this.floorY));
            if (spot != null) {
               ForestStand.plant(
                  this.level,
                  spot,
                  this.random.nextFloat() < 0.4F ? WildTrees.Kind.WILLOW : WildTrees.Kind.FIR,
                  this.random,
                  0.25F + this.random.nextFloat() * 0.5F
               );
            }
         }

         BlockState foam = ((WhiteWater)ExpeditionContent.WHITEWATER.get()).defaultBlockState();
         BlockState mist = ((CascadeMist)ExpeditionContent.CASCADE_MIST.get()).defaultBlockState();

         for (int wx = this.w0 - 1; wx <= this.w1 + 1; wx++) {
            boolean stream = Math.abs(wx) <= this.W && this.notch[Mth.clamp(wx, -this.W, this.W) + this.W];
            int edge = this.lip[Mth.clamp(wx, -this.W, this.W) + this.W];

            for (int o = 0; o <= 4; o++) {
               int d = edge + o;
               if (o < 1 || o > 2 || !stream) {
                  for (int yx = this.surfaceY; yx > this.floorY; yx--) {
                     BlockPos pos = this.at(wx, d, yx);
                     if (Terrain.writable(this.level, pos)
                        && this.level.getBlockState(pos).isAir()
                        && this.wetNear(wx, d, yx)
                        && this.random.nextFloat() < (stream ? 0.62F : 0.26F)) {
                        Terrain.set(this.level, pos, foam);
                     }
                  }
               }
            }
         }

         for (int nxx = 0; nxx < 40; nxx++) {
            int wx = this.w0 + this.random.nextInt(Math.max(1, this.w1 - this.w0 + 1));
            int d = this.poolBack + this.random.nextInt(Math.max(1, Math.min(6, this.poolFront - this.poolBack + 1)));
            BlockPos pos = this.at(wx, d, this.floorY + 1);
            if (Terrain.writable(this.level, pos) && this.level.getBlockState(pos).isAir() && this.level.getBlockState(pos.below()).getFluidState().isSource()) {
               Terrain.set(this.level, pos, foam);
            }
         }

         for (int wx = this.w0; wx <= this.w1; wx++) {
            if (this.notch[Mth.clamp(wx, -this.W, this.W) + this.W]) {
               int edge = this.lip[Mth.clamp(wx, -this.W, this.W) + this.W];
               if (this.random.nextFloat() < 0.55F) {
                  this.spray(this.at(wx, edge + 1, this.surfaceY + 1), mist, 0);
               }

               int steps = Math.max(1, (this.surfaceY - this.floorY) / 6);

               for (int i = 1; i <= steps; i++) {
                  int yxx = this.surfaceY - i * 6 + this.random.nextInt(3);
                  if (yxx <= this.floorY + 1) {
                     break;
                  }

                  this.spray(this.at(wx, edge + 2 + this.random.nextInt(2), yxx), mist, 1);
               }
            }
         }

         int cloud = 12 + this.loudness() * 7;

         for (int nxxx = 0; nxxx < cloud; nxxx++) {
            int wxx = this.w0 + this.random.nextInt(Math.max(1, this.w1 - this.w0 + 1));
            int d = this.poolBack + this.random.nextInt(Math.max(1, Math.min(7, this.poolFront - this.poolBack + 1)));
            this.spray(this.at(wxx, d, this.floorY + 1 + this.random.nextInt(3 + this.loudness())), mist, 2);
         }
      }

      private boolean wetNear(int w, int d, int y) {
         for (int dd = -2; dd <= 2; dd++) {
            BlockPos pos = this.at(w, d + dd, y);
            if (Terrain.writable(this.level, pos) && !this.level.getBlockState(pos).getFluidState().isEmpty()) {
               return true;
            }
         }

         return false;
      }

      private int loudness() {
         int drop = this.surfaceY - this.floorY;
         return drop >= 34 ? 2 : (drop >= 22 ? 1 : 0);
      }

      private void spray(BlockPos pos, BlockState mist, int where) {
         if (Terrain.writable(this.level, pos) && this.level.getBlockState(pos).isAir()) {
            Terrain.set(this.level, pos, (BlockState)((BlockState)mist.setValue(CascadeMist.WHERE, where)).setValue(CascadeMist.SIZE, this.loudness()));
         }
      }
   }
}
