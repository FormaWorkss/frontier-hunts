package com.formaworks.frontierhunts.landscape;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class AlpineColumn {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState STONE = Blocks.STONE.defaultBlockState();
   private static final BlockState DEEP = Blocks.DEEPSLATE.defaultBlockState();
   private static final BlockState WATER = Blocks.WATER.defaultBlockState();
   private static final BlockState FALL = (BlockState)Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
   private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
   private final AlpineLayout terrain;
   private final AlpineLayout.Sample sample;
   private final int x;
   private final int z;
   private final int top;
   private final int skinDepth;
   private final double slope;
   private final double patch;
   private final double bandPhase;
   private final double caveCenter;
   private final double caveRadius;
   private final BlockState water;
   private final BlockState wetSurface;
   private final boolean alpineDistrict;
   private final double exposedBase;
   private final double jointPhase;
   private final double jointStrength;
   private final int site;
   private final int cavityBottom;
   private final int cavityTop;
   private final int cavityWater;

   public AlpineColumn(AlpineLayout var1, AlpineLayout.Sample var2, double var3, int var5, int var6) {
      this(
         var1,
         var2,
         var3,
         var5,
         var6,
         var1.version() >= 2 && !(var3 <= 1.6) && !var2.wet()
            ? Math.min(
               Math.min(var1.sample((double)(var5 - 4), (double)var6).ground(), var1.sample((double)(var5 + 4), (double)var6).ground()),
               Math.min(var1.sample((double)var5, (double)(var6 - 4)).ground(), var1.sample((double)var5, (double)(var6 + 4)).ground())
            )
            : var2.ground()
      );
   }

   public AlpineColumn(AlpineLayout var1, AlpineLayout.Sample var2, double var3, int var5, int var6, double var7) {
      this.terrain = var1;
      this.sample = var2;
      this.slope = var3;
      this.x = var5;
      this.z = var6;
      this.top = var2.floor();
      this.skinDepth = Math.min(128, Math.max(5, (int)Math.ceil(var3 * 8.0) + 2));
      this.patch = var1.noise((double)var5 / 23.0, (double)var6 / 23.0, 905L);
      this.bandPhase = var1.noise((double)var5 / 74.0, (double)var6 / 74.0, 907L) * 2.0;
      this.caveCenter = -4.0 + 32.0 * var1.noise((double)var5 / 137.0, (double)var6 / 137.0, 811L);
      this.caveRadius = 3.2 + 2.0 * var1.noise((double)var5 / 41.0, (double)var6 / 41.0, 813L);
      this.water = var2.fall() > 1.0 ? FALL : WATER;
      this.alpineDistrict = var1.version() >= 3
         && var2.distance() > var2.width() + 45.0
         && var2.moisture() < 0.63
         && var1.noise((double)var5 / 780.0, (double)var6 / 780.0, 919L) > 0.12;
      this.wetSurface = (var1.version() >= 6
            ? (this.patch > 0.42 ? Blocks.MOSSY_COBBLESTONE : (this.patch > 0.02 ? Blocks.GRAVEL : (this.patch < -0.46 ? Blocks.CLAY : Blocks.STONE)))
            : (this.patch > 0.1 ? Blocks.GRAVEL : Blocks.CLAY))
         .defaultBlockState();
      this.exposedBase = var1.version() >= 2 && !var2.wet() && var3 > 1.6 ? var7 + 2.0 : (double)this.top;
      this.jointPhase = this.exposedBase < (double)this.top ? var1.noise((double)var5 / 37.0, (double)var6 / 37.0, 1101L) * 2.6 : 0.0;
      this.jointStrength = this.exposedBase < (double)this.top ? 0.5 + 0.5 * var1.noise((double)var5 / 13.0, (double)var6 / 13.0, 1103L) : 0.0;
      this.site = var2.site();
      long var9 = var2.cavity();
      if (AlpineFalls.hasCavity(var9)) {
         this.cavityBottom = AlpineFalls.cavityBottom(var9);
         this.cavityTop = Math.min(this.top - 1, AlpineFalls.cavityTop(var9));
         this.cavityWater = AlpineFalls.cavityWater(var9);
      } else {
         this.cavityBottom = this.cavityTop = 0;
         this.cavityWater = Integer.MIN_VALUE;
      }
   }

   public BlockState at(int var1) {
      if (var1 > this.top) {
         return var1 <= this.sample.water() ? this.water : AIR;
      } else if (this.cavityTop > this.cavityBottom && var1 > this.cavityBottom && var1 <= this.cavityTop) {
         return var1 <= this.cavityWater ? WATER : AIR;
      } else if (var1 != -64 && (var1 >= -60 || !(this.terrain.variation(this.x, this.z, (long)(900 + var1)) > 0.35))) {
         if (var1 >= -51
            && var1 <= 54
            && Math.abs((double)var1 - this.caveCenter) < this.caveRadius
            && Math.abs(this.terrain.noise((double)this.x / 63.0 + (double)var1 * 0.018, (double)this.z / 63.0, 809L)) < 0.046) {
            return AIR;
         } else if (var1 < 0) {
            return DEEP;
         } else {
            int var2 = this.top - var1;
            if (this.site != 0) {
               return this.scenic(var1, var2);
            } else if (var2 > this.skinDepth) {
               return STONE;
            } else if (this.terrain.version() >= 4 && AlpineLayout.sea(this.sample.biome())) {
               if (var2 > 2) {
                  return STONE;
               } else if (this.top < 52) {
                  return (this.patch > 0.1 ? Blocks.GRAVEL : Blocks.CLAY).defaultBlockState();
               } else {
                  return !(this.slope > 1.2) && !(this.patch < -0.18) ? Blocks.SAND.defaultBlockState() : this.riverStone(var1, var2);
               }
            } else if (this.terrain.version() >= 3 && AlpineLayout.sea(this.sample.biome())) {
               return var2 < 3 ? Blocks.SAND.defaultBlockState() : STONE;
            } else if (this.terrain.version() >= 3 && this.sample.rock() > 0.35) {
               return this.riverStone(var1, var2);
            } else if (this.terrain.version() >= 15
               && AlpineLayout.soil(this.sample.biome())
               && !this.sample.wet()
               && var2 <= 3
               && this.sample.ground() < this.sample.snowLine()
               && this.slope < 1.6) {
               return this.districtSurface(var2);
            } else if (this.sample.wet()) {
               if (this.terrain.version() >= 20 && (this.sample.fallStyle() >>> 31 & 1) != 0) {
                  return var2 == 0 ? (this.patch > -0.2 ? Blocks.MUD : Blocks.CLAY).defaultBlockState() : (var2 < 3 ? Blocks.DIRT.defaultBlockState() : STONE);
               } else {
                  return var2 == 0 ? this.wetSurface : STONE;
               }
            } else if (this.terrain.version() >= 2 && this.sample.biome() == 9 && this.slope < 1.4) {
               return var2 == 0 ? Blocks.PODZOL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            } else {
               if (var2 >= 3 && (double)var1 > this.exposedBase && this.jointStrength > 0.28) {
                  double var3 = Math.sin((double)var1 * 0.23 + this.jointPhase) + 0.34 * Math.sin((double)var1 * 0.071 - this.jointPhase * 1.7);
                  if (var3 > 0.76 + this.jointStrength * 0.22) {
                     return AIR;
                  }
               }

               if (this.terrain.version() >= 20 && (this.sample.fallStyle() & 7) != 0 && (this.sample.fallStyle() >> 3 & 63) > 12) {
                  return this.mountainSurface20(var2, var1);
               } else if (this.terrain.version() >= 19 && (this.sample.fallStyle() & 7) != 0 && (this.sample.fallStyle() >> 3 & 63) > 12) {
                  return this.mountainSurface(var2, var1);
               } else {
                  boolean var11 = this.terrain.version() >= 4
                     && this.sample.ground() < this.sample.snowLine() - 18.0
                     && this.slope < 2.3 + this.patch * 0.8
                     && this.sample.rock() < 0.35;
                  if (var11 || !(this.slope > 1.15) && !(this.sample.ground() > this.sample.snowLine() - 24.0)) {
                     if (var2 == 0) {
                        if (this.sample.distance() < this.sample.width() + (this.terrain.version() >= 4 ? 2.0 + this.patch * 2.0 : 9.0)) {
                           if (this.terrain.version() >= 6) {
                              return this.patch > 0.18 ? Blocks.MOSS_BLOCK.defaultBlockState() : this.riverStone(var1, var2);
                           } else {
                              return (this.patch > 0.0 ? Blocks.MUD : Blocks.GRAVEL).defaultBlockState();
                           }
                        } else {
                           if (this.terrain.version() >= 20
                              && this.slope < 0.65
                              && !this.sample.wet()
                              && this.sample.ground() < this.sample.snowLine() - 90.0
                              && (this.sample.fallStyle() & 7) == 0) {
                              double var12 = this.terrain.trail((double)this.x, (double)this.z);
                              if (var12 > 0.45) {
                                 double var14 = this.terrain.noise((double)this.x / 3.1, (double)this.z / 3.1, 7791L);
                                 return (var14 > 0.35 ? Blocks.DIRT : (var14 < -0.55 ? Blocks.PODZOL : Blocks.COARSE_DIRT)).defaultBlockState();
                              }
                           }

                           if (this.terrain.version() >= 21 && this.sample.forest() > 0.42) {
                              double var13 = AlpineLayout.smooth(0.42, 0.82, this.sample.forest());
                              double var6 = this.terrain.noise((double)this.x / 9.0, (double)this.z / 9.0, 7801L)
                                 + 0.55 * this.terrain.noise((double)this.x / 3.3, (double)this.z / 3.3, 7803L);
                              if (var6 > 0.62 - 1.05 * var13) {
                                 double var8 = this.terrain.noise((double)this.x / 5.0, (double)this.z / 5.0, 7805L)
                                    + 0.35 * this.terrain.noise((double)this.x / 2.0, (double)this.z / 2.0, 7807L);
                                 if (var8 > -0.05) {
                                    return Blocks.PODZOL.defaultBlockState();
                                 } else if (var8 > -0.52) {
                                    Block var10 = AlpineRegistration.prop("forest_duff");
                                    return var10 != Blocks.AIR ? var10.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
                                 } else {
                                    return (var8 > -0.7 ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT).defaultBlockState();
                                 }
                              } else {
                                 return this.patch > 0.3 && this.sample.moisture() > 0.45
                                    ? Blocks.MOSS_BLOCK.defaultBlockState()
                                    : Blocks.GRASS_BLOCK.defaultBlockState();
                              }
                           } else if (this.terrain.version() >= 3 && this.sample.forest() > 0.68 && this.patch > 0.2) {
                              return Blocks.MOSS_BLOCK.defaultBlockState();
                           } else {
                              return this.terrain.version() < 4 && this.sample.forest() > 0.6 && this.patch > 0.1
                                 ? (this.patch > 0.4 ? Blocks.COARSE_DIRT : Blocks.PODZOL).defaultBlockState()
                                 : Blocks.GRASS_BLOCK.defaultBlockState();
                           }
                        }
                     } else {
                        return this.terrain.version() < 5
                              || var2 > 2
                              || !(this.slope > (this.terrain.version() >= 6 ? 0.18 : 0.75))
                              || this.sample.wet()
                              || !(this.sample.ground() < this.sample.snowLine() - 8.0)
                              || !(this.sample.rock() < 0.35)
                              || this.terrain.version() < 6 && !(this.terrain.noise((double)this.x / 11.0, (double)this.z / 11.0, 1207L) > -0.45)
                           ? Blocks.DIRT.defaultBlockState()
                           : Blocks.MOSS_BLOCK.defaultBlockState();
                     }
                  } else if (this.terrain.version() >= 17) {
                     return this.summitSurface(var1, var2);
                  } else if (var2 == 0 && this.slope < 1.4 && this.sample.ground() > this.sample.snowLine()) {
                     return Blocks.SNOW_BLOCK.defaultBlockState();
                  } else if (this.terrain.version() >= 3) {
                     return this.alpineDistrict && this.terrain.version() < 8
                        ? ((AlpineRock)AlpineRegistration.ROCK.get()).at(this.x, var1, this.z)
                        : this.riverStone(var1, var2);
                  } else if (var2 == 0 && this.sample.ground() < 460.0 && this.slope < 2.2 && this.patch > 0.08) {
                     return Blocks.MOSS_BLOCK.defaultBlockState();
                  } else {
                     double var4 = Math.sin((double)var1 * 0.11 + this.bandPhase);
                     if (this.sample.distance() < this.sample.width() + 38.0 && this.sample.ground() < 460.0 && this.patch < -0.2 && var4 < -0.2) {
                        return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                     } else {
                        return var4 < -0.84 ? Blocks.TUFF.defaultBlockState() : ((AlpineRock)AlpineRegistration.ROCK.get()).at(this.x, var1, this.z);
                     }
                  }
               }
            }
         }
      } else {
         return BEDROCK;
      }
   }

   private BlockState summitSurface(int var1, int var2) {
      if (this.terrain.version() >= 18) {
         return this.summitSurface18(var1, var2);
      } else {
         double var3 = this.terrain.noise((double)this.x / 8.5, (double)this.z / 8.5, 7357L);
         double var5 = this.terrain.variation(this.x, this.z, 7355L);
         double var7 = this.sample.ground() - this.sample.snowLine();
         if (var2 == 0 && var7 > -2.0) {
            double var9 = (var7 + 4.0) / 34.0 + (1.15 - this.slope) * 0.42 + var3 * 0.55;
            if (var9 > 0.72) {
               return Blocks.SNOW_BLOCK.defaultBlockState();
            }
         }

         double var13 = this.terrain.noise((double)this.x / 1400.0, (double)this.z / 1400.0, 7351L)
            + 0.35 * this.terrain.noise((double)this.x / 430.0, (double)this.z / 430.0, 7353L);
         int var11 = var13 < -0.42 ? 0 : (var13 < -0.12 ? 1 : (var13 < 0.18 ? 2 : (var13 < 0.48 ? 3 : 4)));
         boolean var12 = var3 > 0.44 && var5 < 0.42;
         switch (var11) {
            case 0:
               if (var12) {
                  return Blocks.GRAVEL.defaultBlockState();
               } else if (var5 < 0.34) {
                  return Blocks.CALCITE.defaultBlockState();
               } else {
                  if (var5 < 0.62) {
                     return Blocks.DIORITE.defaultBlockState();
                  }

                  return Blocks.TUFF.defaultBlockState();
               }
            case 1:
               if (var12) {
                  return Blocks.COBBLESTONE.defaultBlockState();
               } else if (var5 < 0.46) {
                  return Blocks.STONE.defaultBlockState();
               } else {
                  if (var5 < 0.78) {
                     return Blocks.ANDESITE.defaultBlockState();
                  }

                  return Blocks.GRAVEL.defaultBlockState();
               }
            case 2:
               if (var12) {
                  return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
               } else if (var5 < 0.4) {
                  return Blocks.DEEPSLATE.defaultBlockState();
               } else {
                  if (var5 < 0.72) {
                     return Blocks.BASALT.defaultBlockState();
                  }

                  return Blocks.TUFF.defaultBlockState();
               }
            case 3:
               if (var12) {
                  return Blocks.GRAVEL.defaultBlockState();
               } else if (var5 < 0.48) {
                  return Blocks.GRANITE.defaultBlockState();
               } else {
                  if (var5 < 0.76) {
                     return Blocks.TUFF.defaultBlockState();
                  }

                  return Blocks.COBBLESTONE.defaultBlockState();
               }
            default:
               if (var2 == 0 && var7 < 8.0 && this.slope < 1.9 && var5 < 0.46) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               } else if (var5 < 0.3) {
                  return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
               } else if (var5 < 0.58) {
                  return Blocks.STONE.defaultBlockState();
               } else {
                  return var12 ? Blocks.GRAVEL.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
               }
         }
      }
   }

   private BlockState summitSurface18(int var1, int var2) {
      double var3 = this.terrain.noise((double)this.x / 8.5, (double)this.z / 8.5, 7357L);
      double var5 = this.terrain.noise((double)this.x / 28.0, (double)this.z / 28.0, 7361L)
         + 0.45 * this.terrain.noise((double)this.x / 12.0, (double)this.z / 12.0, 7363L);
      double var7 = this.terrain.noise((double)this.x / 7.0, (double)this.z / 7.0 + (double)var1 * 0.06, 7365L) + 0.3 * var3;
      double var9 = this.sample.ground() - this.sample.snowLine();
      if (var2 == 0 && var9 > -2.0) {
         double var11 = (var9 + 4.0) / 34.0 + (1.15 - this.slope) * 0.42 + var5 * 0.4;
         if (var11 > 0.72) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
         }
      }

      double var15 = this.terrain.noise((double)this.x / 1400.0, (double)this.z / 1400.0, 7351L)
         + 0.35 * this.terrain.noise((double)this.x / 430.0, (double)this.z / 430.0, 7353L);
      int var13 = var15 < -0.42 ? 0 : (var15 < -0.12 ? 1 : (var15 < 0.18 ? 2 : (var15 < 0.48 ? 3 : 4)));
      boolean var14 = var3 > 0.4 && var5 < 0.05 && this.slope < 1.6;
      switch (var13) {
         case 0:
            if (var14) {
               return Blocks.GRAVEL.defaultBlockState();
            } else if (var7 > 0.25) {
               return Blocks.ANDESITE.defaultBlockState();
            } else {
               if (var7 > -0.2) {
                  return Blocks.STONE.defaultBlockState();
               }

               return Blocks.TUFF.defaultBlockState();
            }
         case 1:
            if (var14) {
               return Blocks.COBBLESTONE.defaultBlockState();
            }

            return (var7 < 0.12 ? Blocks.STONE : Blocks.ANDESITE).defaultBlockState();
         case 2:
            if (var14) {
               return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            }

            return (var7 < 0.0 ? Blocks.DEEPSLATE : (var7 < 0.45 ? Blocks.BASALT : Blocks.TUFF)).defaultBlockState();
         case 3:
            if (var14) {
               return Blocks.GRAVEL.defaultBlockState();
            }

            return (var7 < 0.15 ? Blocks.GRANITE : Blocks.TUFF).defaultBlockState();
         default:
            if (var2 == 0 && var9 < 8.0 && this.slope < 1.9 && var7 < 0.25) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else {
               return var14
                  ? Blocks.GRAVEL.defaultBlockState()
                  : (var7 < -0.15 ? Blocks.MOSSY_COBBLESTONE : (var7 < 0.35 ? Blocks.STONE : Blocks.ANDESITE)).defaultBlockState();
            }
      }
   }

   private BlockState mountainSurface(int var1, int var2) {
      int var3 = this.sample.fallStyle();
      int var4 = var3 & 7;
      double var5 = (double)(var3 >> 9 & 63) / 63.0;
      double var7 = (double)(var3 >> 15 & 63) / 63.0;
      double var9 = this.sample.ground() - this.sample.snowLine();
      double var11 = this.terrain.noise((double)this.x / 8.5, (double)this.z / 8.5, 7357L);
      double var13 = this.terrain.noise((double)this.x / 28.0, (double)this.z / 28.0, 7361L)
         + 0.45 * this.terrain.noise((double)this.x / 12.0, (double)this.z / 12.0, 7363L);
      double var15 = this.terrain.noise((double)this.x / 9.0, (double)this.z / 9.0 + (double)var2 * 0.05, 7365L) + 0.3 * var11;
      double var17 = Math.sin((double)var2 * 0.19 + this.bandPhase * 1.7 + this.terrain.noise((double)this.x / 60.0, (double)this.z / 60.0, 7367L) * 2.2)
         + 0.35 * this.terrain.noise((double)this.x / 21.0, (double)this.z / 21.0 + (double)var2 * 0.04, 7369L);
      boolean var19 = var5 > 0.78 && var7 > 0.5;
      double var20 = (var4 == 1 ? 1.55 : (var4 == 4 ? 1.3 : (var4 == 2 ? 1.38 + 0.55 * AlpineLayout.smooth(30.0, 150.0, var9) : 1.12))) + 0.3 * this.patch;
      boolean var22 = this.slope > var20 || var19 && this.slope > 0.55;
      if (var22) {
         return this.mountainRock(var4, var2, var15, var17, var11);
      } else {
         if (var1 == 0 && var9 > -8.0) {
            double var23 = Math.min(1.4, (var9 + 8.0) / 35.0) + (1.25 - this.slope) * 0.45 + var13 * 0.32 - (var19 ? 0.3 : 0.0);
            if (var23 > 0.7) {
               return Blocks.SNOW_BLOCK.defaultBlockState();
            }
         }
         boolean var26 = switch (var4) {
            case 1 -> var9 > -25.0 && var7 > 0.85;
            case 2 -> var7 > 0.55 || var9 > -70.0;
            case 3 -> var7 > 0.35;
            default -> var7 > 0.5;
         };
         if (var22 || var26) {
            return this.mountainRock(var4, var2, var15, var17, var11);
         } else if (var1 == 0) {
            double var24 = switch (var4) {
               case 1 -> 0.62;
               default -> 0.34;
               case 3 -> 0.28;
               case 4 -> 0.4;
            };
            if (var13 * 0.6 + var11 * 0.5 > var24 + (1.0 - var7) * 0.25) {
               return this.mountainRock(var4, var2, var15, var17, var11);
            } else {
               return var4 == 3 && var11 > 0.38 && var13 < 0.0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            }
         } else if (var1 <= 2 && this.slope > 0.35) {
            return var4 != 1 && (var4 != 4 || !(var7 < 0.3)) ? this.mountainRock(var4, var2, var15, var17, var11) : Blocks.MOSS_BLOCK.defaultBlockState();
         } else {
            return var1 <= 3 ? Blocks.DIRT.defaultBlockState() : this.mountainRock(var4, var2, var15, var17, var11);
         }
      }
   }

   private BlockState mountainSurface20(int var1, int var2) {
      int var3 = this.sample.fallStyle();
      int var4 = var3 & 7;
      double var5 = (double)(var3 >> 15 & 63) / 63.0;
      double var7 = (double)(var3 >> 21 & 31) / 15.5 - 1.0;
      double var9 = (double)(var3 >> 26 & 3) / 3.0;
      double var11 = this.sample.ground() - this.sample.snowLine();
      double var13 = this.terrain.noise((double)this.x / 8.5, (double)this.z / 8.5, 7357L);
      double var15 = this.terrain.noise((double)this.x / 46.0, (double)this.z / 46.0, 7361L)
         + 0.35 * this.terrain.noise((double)this.x / 17.0, (double)this.z / 17.0, 7363L);
      boolean var17 = this.terrain.version() >= 21;
      double var18 = (var4 == 1 ? (var17 ? 1.85 : 1.5) : (var4 == 4 ? 1.28 : (var4 == 2 ? 1.35 + 0.5 * AlpineLayout.smooth(30.0, 150.0, var11) : 1.08)))
         + 0.25 * this.patch;
      boolean var20 = this.slope > var18;
      boolean var21 = var7 > 0.42;
      if (var1 == 0) {
         double var22 = var11 + (var7 < -0.15 ? (-var7 - 0.15) * 115.0 : 0.0) + var9 * 55.0 - (var21 ? (var7 - 0.42) * 60.0 : 0.0) + var15 * 14.0;
         double var24 = Math.min(1.4, (var22 + 8.0) / 35.0) + (1.25 - this.slope) * 0.4;
         if (var20) {
            var24 -= var7 < -0.25 ? 0.1 : 0.6;
         }

         if (var24 > 0.7) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
         }
      }

      if (var1 == 0
         && var11 < -45.0
         && this.slope < 1.25 + 0.3 * this.patch
         && var13 + var15 * 0.55 + (var4 == 1 ? 0.35 : 0.0) > 0.22 + AlpineLayout.smooth(-45.0, -160.0, var11) * -0.25) {
         return Blocks.GRASS_BLOCK.defaultBlockState();
      } else if (var20) {
         return this.bedRock20(var4, var2, var13);
      } else {
         double var29 = (double)(var3 >> 9 & 63) / 63.0;
         boolean var30 = (var7 < -0.3 || var29 < 0.35 && this.slope < 1.0) && var5 > 0.12 && var5 < 0.8;
         if (var4 != 1 && var1 <= 1 && var30 && this.slope > 0.3 && this.slope < 1.25) {
            double var32 = var13 + 0.4 * var15;
            return (var32 > 0.35 ? Blocks.COBBLESTONE : (var32 > -0.1 ? Blocks.GRAVEL : (var32 > -0.45 ? Blocks.ANDESITE : Blocks.TUFF))).defaultBlockState();
         } else {
            double var25 = switch (var4) {
               case 1 -> var17 ? 0.97 : 0.9;
               case 2 -> 0.55;
               case 3 -> 0.34;
               default -> 0.48;
            };
            var25 -= var15 * 0.1 + (this.slope - 0.6) * 0.18;
            if (this.slope < 0.5 && var5 < var25 + 0.3 && var4 != 1) {
               if (var1 == 0) {
                  double var33 = var15 * 0.7 + var13 * 0.5;
                  if (var33 > 0.45) {
                     return (var13 > 0.2 ? Blocks.GRAVEL : Blocks.ANDESITE).defaultBlockState();
                  } else {
                     return var33 > 0.25 && var5 > var25 ? Blocks.STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                  }
               } else {
                  return var1 <= 2 ? Blocks.DIRT.defaultBlockState() : this.bedRock20(var4, var2, var13);
               }
            } else if (!(var5 > var25) && (!var21 || !(this.slope > (var4 == 1 ? (var17 ? 1.3 : 0.95) : 0.55)))) {
               if (var1 == 0) {
                  double var27 = switch (var4) {
                     case 1 -> var17 ? 0.82 : 0.7;
                     default -> 0.36;
                     case 3 -> 0.3;
                     case 4 -> 0.42;
                  };
                  return var15 * 0.6 + var13 * 0.45 + var7 * 0.25 > var27 + (1.0 - var5) * 0.3
                     ? this.bedRock20(var4, var2, var13)
                     : Blocks.GRASS_BLOCK.defaultBlockState();
               } else if (var1 <= 2 && this.slope > 0.35) {
                  return var4 != 1 && (var4 != 4 || !(var5 < 0.35)) ? this.bedRock20(var4, var2, var13) : Blocks.MOSS_BLOCK.defaultBlockState();
               } else {
                  return var1 <= 3 ? Blocks.DIRT.defaultBlockState() : this.bedRock20(var4, var2, var13);
               }
            } else {
               return this.bedRock20(var4, var2, var13);
            }
         }
      }
   }

   private BlockState bedRock20(int var1, int var2, double var3) {
      double var5 = (double)var2
         + 38.0 * this.terrain.noise((double)this.x / 620.0, (double)this.z / 620.0, 7371L)
         + 9.0 * this.terrain.noise((double)this.x / 140.0, (double)this.z / 140.0, 7373L)
         + 2.2 * var3;
      double var7 = 8.5 + 3.5 * this.terrain.noise((double)this.x / 900.0, (double)this.z / 900.0, 7375L);
      int var9 = (int)Math.floor(var5 / var7);
      double var10 = this.terrain.variation(var9, var1, (long)(7377 + (int)Math.floor(var5 / (var7 * 9.0))));
      if (this.slope < 0.85 && var10 > 0.64) {
         var10 = 0.3 + (var10 - 0.64) * 0.9;
      }

      switch (var1) {
         case 1:
            if (var3 > 0.34) {
               return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            }

            return (var10 < 0.55 ? Blocks.STONE : (var10 < 0.85 ? Blocks.ANDESITE : Blocks.TUFF)).defaultBlockState();
         case 2:
            return (var10 < 0.5 ? Blocks.STONE : (var10 < 0.78 ? Blocks.ANDESITE : (var10 < 0.92 ? Blocks.TUFF : Blocks.DEEPSLATE))).defaultBlockState();
         case 3:
            if (var3 > 0.42) {
               return Blocks.COBBLESTONE.defaultBlockState();
            } else if (var10 < 0.4) {
               return (var3 < -0.45 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
            } else if (var10 < 0.64) {
               return (var3 < -0.45 ? Blocks.STONE : Blocks.ANDESITE).defaultBlockState();
            } else if (var10 < 0.8) {
               return Blocks.TUFF.defaultBlockState();
            } else {
               if (var10 < 0.9) {
                  return (var3 > 0.2 ? Blocks.COBBLESTONE : Blocks.STONE).defaultBlockState();
               }

               return Blocks.DEEPSLATE.defaultBlockState();
            }
         default:
            if (var10 < 0.46) {
               return ((AlpineRock)AlpineRegistration.ROCK.get()).at(this.x, var2, this.z);
            } else {
               return var10 < 0.72 ? Blocks.STONE.defaultBlockState() : (var10 < 0.88 ? Blocks.ANDESITE : Blocks.TUFF).defaultBlockState();
            }
      }
   }

   private BlockState mountainRock(int var1, int var2, double var3, double var5, double var7) {
      switch (var1) {
         case 1:
            if (var3 > 0.38) {
               return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            }

            return (var5 < -0.55 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
         case 2:
            if (var5 > 0.58) {
               return Blocks.ANDESITE.defaultBlockState();
            } else {
               if (var5 < -0.8 && var3 < 0.2) {
                  return Blocks.TUFF.defaultBlockState();
               }

               return Blocks.STONE.defaultBlockState();
            }
         case 3:
            if (this.slope < 1.0 && var7 > 0.35) {
               return Blocks.GRAVEL.defaultBlockState();
            } else if (var5 > 0.45) {
               return Blocks.ANDESITE.defaultBlockState();
            } else {
               if (var3 > 0.5) {
                  return Blocks.COBBLESTONE.defaultBlockState();
               }

               return Blocks.STONE.defaultBlockState();
            }
         default:
            if (this.slope < 1.0 && var7 > 0.4) {
               return Blocks.GRAVEL.defaultBlockState();
            } else {
               return var5 > 0.62 ? Blocks.STONE.defaultBlockState() : ((AlpineRock)AlpineRegistration.ROCK.get()).at(this.x, var2, this.z);
            }
      }
   }

   private BlockState districtSurface(int var1) {
      double var2 = this.terrain.variation(this.x, this.z, 1301L);
      double var4 = this.terrain.noise((double)this.x / 7.0, (double)this.z / 7.0, 1303L);
      switch (this.sample.biome()) {
         case 28:
            if (var1 > 0) {
               return (var2 < 0.3 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
            } else if (this.patch > 0.34 && var2 < 0.55) {
               return Blocks.COARSE_DIRT.defaultBlockState();
            } else if (this.patch < -0.42 && var2 < 0.35) {
               return this.riverStone(this.top, 0);
            } else if (var4 > 0.45 && var2 < 0.28) {
               return Blocks.PODZOL.defaultBlockState();
            } else {
               if (var4 < -0.5 && var2 < 0.3) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               }

               return Blocks.GRASS_BLOCK.defaultBlockState();
            }
         case 29:
            if (this.terrain.version() >= 19) {
               double var6 = this.terrain.noise((double)this.x / 14.0, (double)this.z / 14.0, 1305L) + 0.5 * var4;
               if (var1 <= 0) {
                  if (var6 > 0.52) {
                     return Blocks.COARSE_DIRT.defaultBlockState();
                  } else if (var6 < -0.66) {
                     return Blocks.GRAVEL.defaultBlockState();
                  } else {
                     return this.patch > 0.42 && var4 > 0.28 ? this.riverStone(this.top, 0) : Blocks.GRASS_BLOCK.defaultBlockState();
                  }
               } else {
                  return var1 <= 2 && var6 <= 0.52 && this.slope > 0.18
                     ? Blocks.MOSS_BLOCK.defaultBlockState()
                     : (var4 > 0.2 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
               }
            } else if (var1 > 0) {
               return (var2 < 0.45 ? Blocks.COARSE_DIRT : (var2 < 0.75 ? Blocks.DIRT : Blocks.GRAVEL)).defaultBlockState();
            } else if (this.patch > 0.2 && var2 < 0.62) {
               return Blocks.COARSE_DIRT.defaultBlockState();
            } else if (var4 > 0.35 && var2 < 0.3) {
               return Blocks.GRAVEL.defaultBlockState();
            } else {
               return var4 < -0.55 && var2 < 0.25 ? this.riverStone(this.top, 0) : (var2 < 0.72 ? Blocks.GRASS_BLOCK : Blocks.COARSE_DIRT).defaultBlockState();
            }
         case 30:
            if (var1 > 0) {
               return var2 < 0.2 ? Blocks.GRAVEL.defaultBlockState() : this.riverStone(this.top - var1, var1);
            } else if (this.patch > 0.3 && var2 < 0.3) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else if (var4 > 0.4 && var2 < 0.35) {
               return Blocks.GRAVEL.defaultBlockState();
            } else {
               if (var2 > 0.93) {
                  return Blocks.COARSE_DIRT.defaultBlockState();
               }

               return this.riverStone(this.top, 0);
            }
         case 31:
         default:
            if (var1 > 0) {
               return (var2 < 0.35 ? Blocks.MUD : (var2 < 0.62 ? Blocks.DIRT : Blocks.CLAY)).defaultBlockState();
            } else if (this.patch > 0.3 && var2 < 0.55) {
               return Blocks.MUD.defaultBlockState();
            } else if (var4 < -0.3 && var2 < 0.45) {
               return Blocks.PODZOL.defaultBlockState();
            } else {
               if (var2 < 0.55) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               }

               return Blocks.GRASS_BLOCK.defaultBlockState();
            }
         case 32:
            if (var1 > 0) {
               return var2 < 0.42 ? Blocks.GRAVEL.defaultBlockState() : this.riverStone(this.top - var1, var1);
            } else if (this.patch > 0.18 && var2 < 0.52) {
               return this.riverStone(this.top, 0);
            } else if (var4 > 0.42 && var2 < 0.44) {
               return Blocks.GRAVEL.defaultBlockState();
            } else {
               if (var4 < -0.46 && var2 < 0.38) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               }

               return (var2 < 0.62 ? Blocks.GRASS_BLOCK : Blocks.COARSE_DIRT).defaultBlockState();
            }
         case 33:
            if (var1 > 0) {
               return Blocks.DIRT.defaultBlockState();
            } else if (var4 < -0.52 && var2 < 0.26) {
               return Blocks.PODZOL.defaultBlockState();
            } else {
               if (this.patch > 0.46 && var2 < 0.18) {
                  return Blocks.COARSE_DIRT.defaultBlockState();
               }

               return Blocks.GRASS_BLOCK.defaultBlockState();
            }
         case 34:
            if (this.terrain.version() >= 18) {
               if (var1 > 0) {
                  return (var4 < -0.1 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
               } else if (this.patch > 0.34 && var4 > -0.05) {
                  return this.riverStone(this.top, 0);
               } else {
                  if (this.patch > 0.22 && var4 > 0.3) {
                     return Blocks.COARSE_DIRT.defaultBlockState();
                  }

                  return Blocks.GRASS_BLOCK.defaultBlockState();
               }
            } else if (var1 > 0) {
               return (var2 < 0.55 ? Blocks.CALCITE : (var2 < 0.8 ? Blocks.COARSE_DIRT : Blocks.DIRT)).defaultBlockState();
            } else if (this.patch > 0.26 && var2 < 0.48) {
               return Blocks.CALCITE.defaultBlockState();
            } else if (var4 > 0.48 && var2 < 0.34) {
               return this.riverStone(this.top, 0);
            } else {
               if (var2 > 0.9) {
                  return Blocks.COARSE_DIRT.defaultBlockState();
               }

               return Blocks.GRASS_BLOCK.defaultBlockState();
            }
         case 35:
            if (var1 > 0) {
               return (var2 < 0.48 ? Blocks.MUD : Blocks.DIRT).defaultBlockState();
            } else if (this.patch > 0.1 && var2 < 0.42) {
               return Blocks.MUD.defaultBlockState();
            } else if (var4 < -0.35 && var2 < 0.5) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else {
               if (var2 > 0.94) {
                  return Blocks.PODZOL.defaultBlockState();
               }

               return Blocks.GRASS_BLOCK.defaultBlockState();
            }
         case 36:
            if (var1 > 0) {
               return Blocks.DIRT.defaultBlockState();
            } else if (var4 > 0.46 && var2 < 0.28) {
               return Blocks.COARSE_DIRT.defaultBlockState();
            } else {
               if (this.patch > 0.4 && var2 < 0.2) {
                  return this.riverStone(this.top, 0);
               }

               return Blocks.GRASS_BLOCK.defaultBlockState();
            }
         case 37:
            if (var1 > 0) {
               return (var2 < 0.3 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
            } else if (this.patch < -0.38 && var2 < 0.3) {
               return Blocks.PODZOL.defaultBlockState();
            } else {
               if (var4 > 0.5 && var2 < 0.22) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               }

               return Blocks.GRASS_BLOCK.defaultBlockState();
            }
         case 38:
            if (var1 > 0) {
               return (var2 < 0.38 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
            } else if (this.patch > 0.24 && var2 < 0.44) {
               return Blocks.PODZOL.defaultBlockState();
            } else if (var4 < -0.48 && var2 < 0.32) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else {
               return var2 > 0.92 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            }
      }
   }

   private BlockState scenic(int var1, int var2) {
      BlockState var3 = this.scenic14(var1, var2);
      if (this.terrain.version() < 15) {
         return var3;
      } else {
         boolean var4 = this.sample.water() > this.top && var2 <= 2;
         if (!var3.is(Blocks.TUFF)) {
            if (var3.is(Blocks.CALCITE) && var4 && this.terrain.variation(this.x, this.z, (long)(959 + var1)) > 0.25) {
               return Blocks.GRAVEL.defaultBlockState();
            } else {
               return this.terrain.version() >= 18
                     && var3.is(Blocks.CALCITE)
                     && this.terrain.noise((double)this.x / 5.0, (double)this.z / 5.0 + (double)var1 * 0.05, 967L) < 0.18
                  ? (var4 ? Blocks.GRAVEL : Blocks.TUFF).defaultBlockState()
                  : var3;
            }
         } else {
            return (var4 && this.terrain.variation(this.x, this.z, (long)(957 + var1)) > 0.5 ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState();
         }
      }
   }

   private BlockState scenic14(int var1, int var2) {
      int var3 = AlpineFalls.kind(this.site);
      int var4 = AlpineFalls.palette(this.site) - 1;
      boolean var5 = this.terrain.version() >= 14;
      double var6 = (double)AlpineFalls.wet(this.site) / 255.0;
      boolean var8 = this.sample.water() > this.top;
      double var9 = this.terrain.noise((double)this.x / 5.0 + (double)var1 * 0.07, (double)this.z / 5.0, 951L);
      double var11 = this.terrain.variation(this.x + var1 * 31, this.z - var1 * 17, 953L);
      if (var2 > Math.max(this.skinDepth, 72)) {
         return var4 == 3 ? Blocks.DEEPSLATE.defaultBlockState() : STONE;
      } else {
         boolean var13 = var3 == 4 || var3 == 5 || var3 == 8 || var3 == 12 || var3 == 6 && !AlpineFalls.island(this.site) || var3 == 10 && !var8;
         if (var2 == 0) {
            if (var8) {
               return switch (var3) {
                  case 2, 9, 10 -> var5 ? creekBed(var4, var9, var11) : creekBed13(var4, var9, var11);
                  default -> var5 ? poolBed(var4, var9, var11, this.sample.water() - this.top) : poolBed13(var4, var9, var11, this.sample.water() - this.top);
                  case 5 -> var5 ? this.lipStone(var4, var1, var6) : lipStone13(var4);
               };
            } else if (var3 == 3) {
               return var5 ? shore(var4, var9, var11, var6) : shore13(var4, var9, var11, var6);
            } else if (!var13) {
               if (this.slope > 1.45) {
                  return var5 ? this.rock(var4, var1, var6, var9, var11) : this.rock13(var4, var1, var6, var9, var11);
               } else if (var3 == 6) {
                  return (var4 == 2 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState();
               } else if (var6 > 0.55 && var11 < 0.6) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               } else {
                  return var11 < 0.05 && this.sample.moisture() > 0.5 ? Blocks.PODZOL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
               }
            } else if (this.slope < 1.6 && (var6 > 0.25 || var11 < 0.35) && var3 != 12) {
               return (var4 == 2 && var11 < 0.5 ? Blocks.MOSS_BLOCK : (!(var6 > 0.5) && var4 != 1 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK))
                  .defaultBlockState();
            } else {
               return var5 ? this.rock(var4, var1, var6, var9, var11) : this.rock13(var4, var1, var6, var9, var11);
            }
         } else if (var8 && var2 <= 2 && !var13) {
            return var5
               ? (var4 == 2 && var2 == 1 ? Blocks.SAND : Blocks.GRAVEL).defaultBlockState()
               : (var2 == 1 && var4 == 2 ? Blocks.CALCITE.defaultBlockState() : (var4 == 2 ? Blocks.SAND : Blocks.GRAVEL).defaultBlockState());
         } else if (!var13 && !var8 && var2 <= 3 && this.slope < 1.45 && var3 != 3) {
            return (var2 == 1 && var6 > 0.4 ? Blocks.MOSS_BLOCK : Blocks.DIRT).defaultBlockState();
         } else {
            return var5 ? this.rock(var4, var1, var6, var9, var11) : this.rock13(var4, var1, var6, var9, var11);
         }
      }
   }

   private BlockState rock(int var1, int var2, double var3, double var5, double var7) {
      double var9 = this.terrain.noise((double)this.x / 2.6, (double)this.z / 2.6 + (double)var2 * 0.012, 961L)
         + 0.35 * this.terrain.noise((double)this.x / 7.0, (double)this.z / 7.0, 963L);
      switch (var1) {
         case 1:
            if (var3 > 0.3 && var9 > 0.15 - var3 * 0.55) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else {
               if (var3 > 0.15 && var7 < var3 * 0.5) {
                  return (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get())
                     .defaultBlockState()
                     .setValue(AlpineWeatheredRock.WEATHER, 3);
               }

               return this.riverStone(var2, 1);
            }
         case 2:
            if (var3 > 0.42 && var9 > 0.55 - var3 * 0.6) {
               return (var7 < 0.7 ? Blocks.CALCITE : Blocks.DRIPSTONE_BLOCK).defaultBlockState();
            } else {
               if (var3 > 0.55 && var7 < var3 * 0.3) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               }

               return this.riverStone(var2, 1);
            }
         case 3:
            if (var3 < 0.08 && var7 < 0.6) {
               return this.riverStone(var2, 1);
            } else {
               double var11 = Math.sin((double)var2 * 0.29 + this.bandPhase * 2.2) + 0.35 * Math.sin((double)var2 * 0.83 - this.bandPhase);
               if (var11 < -1.1) {
                  return Blocks.SMOOTH_BASALT.defaultBlockState();
               } else {
                  if (var3 > 0.55 && var7 < 0.12) {
                     return Blocks.MOSS_BLOCK.defaultBlockState();
                  }

                  int var13 = Math.floorMod((int)((double)this.x * 0.62 + (double)this.z * 0.38) + (int)((double)this.z * 0.71 - (double)this.x * 0.28), 3);
                  return (var13 == 0 ? Blocks.POLISHED_BASALT : Blocks.BASALT).defaultBlockState();
               }
            }
         default:
            if (var3 > 0.55 && var9 > 0.35 - var3 * 0.4) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else {
               return var3 > 0.3
                  ? (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get())
                     .defaultBlockState()
                     .setValue(AlpineWeatheredRock.WEATHER, var5 > 0.0 ? 3 : 2)
                  : this.riverStone(var2, 1);
            }
      }
   }

   private BlockState lipStone(int var1, int var2, double var3) {
      return switch (var1) {
         case 2 -> Blocks.CALCITE.defaultBlockState();
         case 3 -> Blocks.SMOOTH_BASALT.defaultBlockState();
         default -> (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get()).defaultBlockState().setValue(AlpineWeatheredRock.WEATHER, 3);
      };
   }

   private static BlockState poolBed(int var0, double var1, double var3, int var5) {
      return switch (var0) {
         case 1 -> (var3 < 0.5 ? Blocks.GRAVEL : (var3 < 0.75 ? Blocks.CLAY : Blocks.MOSS_BLOCK)).defaultBlockState();
         case 2 -> (var3 < 0.35 ? Blocks.CALCITE : (var3 < 0.75 ? Blocks.SAND : Blocks.GRAVEL)).defaultBlockState();
         case 3 -> (var3 < 0.55 ? Blocks.GRAVEL : (var3 < 0.85 ? Blocks.SMOOTH_BASALT : Blocks.TUFF)).defaultBlockState();
         default -> (var5 > 3 && var3 < 0.3
            ? Blocks.STONE
            : (var1 > 0.35 ? Blocks.TUFF : (var3 < 0.65 ? Blocks.GRAVEL : (var3 < 0.85 ? Blocks.CLAY : Blocks.STONE))))
         .defaultBlockState();
      };
   }

   private static BlockState creekBed(int var0, double var1, double var3) {
      return var0 == 2
         ? (var3 < 0.35 ? Blocks.CALCITE : (var3 < 0.7 ? Blocks.GRAVEL : Blocks.SAND)).defaultBlockState()
         : (var1 > 0.3 ? Blocks.STONE : (var3 < 0.6 ? Blocks.GRAVEL : (var3 < 0.8 ? Blocks.TUFF : Blocks.CLAY))).defaultBlockState();
   }

   private static BlockState shore(int var0, double var1, double var3, double var5) {
      if (var0 == 2) {
         return (var3 < 0.5 ? Blocks.GRAVEL : (var3 < 0.7 ? Blocks.SAND : Blocks.MOSS_BLOCK)).defaultBlockState();
      } else {
         return var5 > 0.5 && var3 < 0.45
            ? Blocks.MOSS_BLOCK.defaultBlockState()
            : (var1 > 0.25 ? Blocks.GRAVEL : (var3 < 0.4 ? Blocks.COARSE_DIRT : (var3 < 0.7 ? Blocks.GRAVEL : Blocks.MOSS_BLOCK))).defaultBlockState();
      }
   }

   private BlockState rock13(int var1, int var2, double var3, double var5, double var7) {
      double var9 = Math.sin((double)var2 * 0.29 + this.bandPhase * 2.2) + 0.35 * Math.sin((double)var2 * 0.83 - this.bandPhase);
      switch (var1) {
         case 1:
            if (var3 > 0.35 && var7 < 0.3 + var3 * 0.4) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else if (var5 > 0.25) {
               return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            } else {
               if (var9 < -1.05) {
                  return Blocks.TUFF.defaultBlockState();
               }

               return (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get())
                  .defaultBlockState()
                  .setValue(AlpineWeatheredRock.WEATHER, !(var3 > 0.25) && !(var5 > -0.1) ? 2 : 3);
            }
         case 2:
            if (var3 > 0.45 && var7 < var3 * 0.35) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            } else if (var9 < -0.85) {
               return Blocks.DRIPSTONE_BLOCK.defaultBlockState();
            } else {
               if (var9 > 1.15 && var5 > 0.1) {
                  return Blocks.TUFF.defaultBlockState();
               }

               return Blocks.CALCITE.defaultBlockState();
            }
         case 3:
            if (var9 < -1.1) {
               return Blocks.SMOOTH_BASALT.defaultBlockState();
            } else {
               if (var3 > 0.55 && var7 < 0.12) {
                  return Blocks.MOSS_BLOCK.defaultBlockState();
               }

               int var12 = Math.floorMod((int)((double)this.x * 0.62 + (double)this.z * 0.38) + (int)((double)this.z * 0.71 - (double)this.x * 0.28), 3);
               return (var12 == 0 ? Blocks.POLISHED_BASALT : Blocks.BASALT).defaultBlockState();
            }
         default:
            if (var3 > 0.6 && var7 < 0.22) {
               return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            } else if (var9 < -1.0) {
               return Blocks.ANDESITE.defaultBlockState();
            } else if (var9 > 1.2 && var5 < -0.2) {
               return Blocks.TUFF.defaultBlockState();
            } else {
               int var11 = var3 > 0.45 ? (var5 > 0.0 ? 3 : 2) : (var3 > 0.15 ? (var5 > 0.2 ? 2 : 1) : (var5 > 0.35 ? 1 : 0));
               return (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get())
                  .defaultBlockState()
                  .setValue(AlpineWeatheredRock.WEATHER, var11);
            }
      }
   }

   private static BlockState lipStone13(int var0) {
      return switch (var0) {
         case 1 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
         case 2 -> Blocks.CALCITE.defaultBlockState();
         case 3 -> Blocks.SMOOTH_BASALT.defaultBlockState();
         default -> (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get()).defaultBlockState().setValue(AlpineWeatheredRock.WEATHER, 2);
      };
   }

   private static BlockState poolBed13(int var0, double var1, double var3, int var5) {
      return switch (var0) {
         case 1 -> (var1 > 0.2 ? Blocks.MOSSY_COBBLESTONE : (var3 < 0.5 ? Blocks.GRAVEL : (var3 < 0.75 ? Blocks.CLAY : Blocks.MOSS_BLOCK))).defaultBlockState();
         case 2 -> (var3 < 0.62 ? Blocks.CALCITE : (var3 < 0.86 ? Blocks.SAND : Blocks.CLAY)).defaultBlockState();
         case 3 -> (var3 < 0.55 ? Blocks.GRAVEL : (var3 < 0.85 ? Blocks.SMOOTH_BASALT : Blocks.TUFF)).defaultBlockState();
         default -> (var5 > 3 && var3 < 0.3
            ? Blocks.STONE
            : (var1 > 0.35 ? Blocks.MOSSY_COBBLESTONE : (var3 < 0.65 ? Blocks.GRAVEL : (var3 < 0.85 ? Blocks.CLAY : Blocks.COBBLESTONE))))
         .defaultBlockState();
      };
   }

   private static BlockState creekBed13(int var0, double var1, double var3) {
      return var0 == 2
         ? (var3 < 0.5 ? Blocks.CALCITE : (var3 < 0.8 ? Blocks.SAND : Blocks.GRAVEL)).defaultBlockState()
         : (var1 > 0.3 ? Blocks.MOSSY_COBBLESTONE : (var3 < 0.6 ? Blocks.GRAVEL : (var3 < 0.8 ? Blocks.COBBLESTONE : Blocks.CLAY))).defaultBlockState();
   }

   private static BlockState shore13(int var0, double var1, double var3, double var5) {
      if (var0 == 2) {
         return (var3 < 0.45 ? Blocks.SAND : (var3 < 0.75 ? Blocks.CALCITE : Blocks.MOSS_BLOCK)).defaultBlockState();
      } else {
         return var5 > 0.5 && var3 < 0.45
            ? Blocks.MOSS_BLOCK.defaultBlockState()
            : (var1 > 0.25 ? Blocks.GRAVEL : (var3 < 0.4 ? Blocks.COARSE_DIRT : (var3 < 0.7 ? Blocks.GRAVEL : Blocks.MOSS_BLOCK))).defaultBlockState();
      }
   }

   private BlockState riverStone(int var1, int var2) {
      double var3 = this.terrain.noise((double)this.x / 8.0 + (double)var1 * 0.035, (double)this.z / 8.0, 917L);
      boolean var5 = this.sample.moisture() > 0.46 || this.sample.distance() < this.sample.width() + 12.0;
      if (this.terrain.version() >= 8) {
         if (this.terrain.version() >= 9) {
            if (var5 && var3 > 0.16 && this.sample.ground() < this.sample.snowLine() - 20.0) {
               return Blocks.MOSS_BLOCK.defaultBlockState();
            }

            if (var3 < -0.15) {
               return STONE;
            }
         }

         int var6 = var5 ? (var3 > 0.2 ? 3 : (var3 > -0.18 ? 2 : 1)) : (var3 > 0.28 ? 2 : 0);
         return (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get()).defaultBlockState().setValue(AlpineWeatheredRock.WEATHER, var6);
      } else {
         return var5 && var3 > 0.12
            ? (var2 == 0 && this.slope < 1.1 ? Blocks.MOSS_BLOCK : Blocks.MOSSY_COBBLESTONE).defaultBlockState()
            : (var3 < -0.12 ? Blocks.COBBLESTONE : Blocks.STONE).defaultBlockState();
      }
   }
}
