package com.formaworks.frontierhunts.landscape.mapsync;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MapColumns {
   public static final int MAX_LAYERS = 8;
   private static final int AIR = 0;
   private static final int PASS = 1;
   private static final int STOP = 2;

   private MapColumns() {
   }

   public static ToIntFunction<String> biomeIds(Registry<Biome> var0) {
      return var1 -> {
         ResourceLocation var2 = ResourceLocation.tryParse(var1);
         Biome var3 = var2 == null ? null : (Biome)var0.get(var2);
         return var3 == null ? -1 : var0.getId(var3);
      };
   }

   public static boolean complete(String var0) {
      if (var0 == null) {
         return false;
      } else {
         int var1 = var0.indexOf(58);
         String var2 = var1 >= 0 ? var0.substring(var1 + 1) : var0;
         return var2.equals("full") || var2.equals("spawn") || var2.equals("light") || var2.equals("initialize_light");
      }
   }

   static int kind(BlockState var0) {
      if (var0.isAir()) {
         return 0;
      } else if (!var0.getFluidState().isEmpty()) {
         return 1;
      } else {
         Block var1 = var0.getBlock();
         if (var1 instanceof LeavesBlock || var0.is(BlockTags.LEAVES) || var1 instanceof SnowLayerBlock) {
            return 2;
         } else if (!(var1 instanceof HalfTransparentBlock) && !var0.is(BlockTags.ICE)) {
            return var0.canOcclude() ? 2 : 1;
         } else {
            return 1;
         }
      }
   }

   public static byte[] encode(CompoundTag var0, HolderGetter<Block> var1, ToIntFunction<String> var2, int var3, Map<CompoundTag, BlockState> var4) {
      if (!complete(var0.getString("Status"))) {
         return null;
      } else {
         ListTag var5 = var0.getList("sections", 10);
         if (var5.isEmpty()) {
            return null;
         } else {
            ArrayList var6 = new ArrayList(var5.size());

            for (int var7 = 0; var7 < var5.size(); var7++) {
               var6.add(var5.getCompound(var7));
            }

            var6.sort((var0x, var1x) -> Integer.compare(var1x.getByte("Y"), var0x.getByte("Y")));
            int[] var36 = new int[256];
            int[][] var8 = new int[256][8];
            int[][] var9 = new int[256][8];
            int[][] var10 = new int[256][8];
            boolean[] var11 = new boolean[256];
            int var12 = 256;
            ArrayList var13 = new ArrayList();
            HashMap var14 = new HashMap();
            String[] var15 = new String[16];
            int[] var16 = new int[0];

            for (CompoundTag var18 : var6) {
               if (var12 == 0) {
                  break;
               }

               byte var19 = var18.getByte("Y");
               CompoundTag var20 = var18.getCompound("block_states");
               ListTag var21 = var20.getList("palette", 10);
               if (!var21.isEmpty()) {
                  BlockState[] var22 = new BlockState[var21.size()];
                  var16 = new int[var21.size()];
                  boolean var23 = true;

                  for (int var24 = 0; var24 < var22.length; var24++) {
                     CompoundTag var25 = var21.getCompound(var24);
                     BlockState var26 = var4 == null ? null : (BlockState)var4.get(var25);
                     if (var26 == null) {
                        try {
                           var26 = NbtUtils.readBlockState(var1, var25);
                        } catch (RuntimeException var35) {
                           var26 = Blocks.AIR.defaultBlockState();
                        }

                        if (var4 != null && var4.size() < 8192) {
                           var4.put(var25.copy(), var26);
                        }
                     }

                     var22[var24] = var26;
                     var16[var24] = kind(var26);
                     if (var16[var24] != 0) {
                        var23 = false;
                     }
                  }

                  if (!var23) {
                     int var57 = var22.length <= 1 ? 0 : Math.max(4, 32 - Integer.numberOfLeadingZeros(var22.length - 1));
                     MapColumns.Section var60 = new MapColumns.Section(
                        var22, var57 == 0 ? new long[0] : var20.getLongArray("data"), var57, var18.contains("biomes", 10) ? var18.getCompound("biomes") : null
                     );

                     for (int var62 = 0; var62 < 256; var62++) {
                        if (!var11[var62]) {
                           int var27 = var62 & 15;
                           int var28 = var62 >> 4;

                           for (int var29 = 15; var29 >= 0; var29--) {
                              BlockState var30 = var60.get(var27, var29, var28);
                              int var31 = kind(var30);
                              if (var31 != 0) {
                                 int var32 = (var19 << 4) + var29;
                                 int var33 = var36[var62];
                                 Integer var34 = (Integer)var14.get(var30);
                                 if (var34 == null) {
                                    var34 = var13.size();
                                    var13.add(var30);
                                    var14.put(var30, var34);
                                 }

                                 if (var33 > 0 && var9[var62][var33 - 1] == var34 && var8[var62][var33 - 1] - var10[var62][var33 - 1] == var32) {
                                    var10[var62][var33 - 1]++;
                                 } else {
                                    if (var33 == 8) {
                                       var11[var62] = true;
                                       var12--;
                                       break;
                                    }

                                    var8[var62][var33] = var32;
                                    var9[var62][var33] = var34;
                                    var10[var62][var33] = 1;
                                    var36[var62] = var33 + 1;
                                    if (var33 == 0 && (var27 & 3) == 1 && (var28 & 3) == 1) {
                                       var15[(var28 >> 2) * 4 + (var27 >> 2)] = var60.biome(var27 >> 2, var29 >> 2, var28 >> 2);
                                    }
                                 }

                                 if (var31 == 2) {
                                    var11[var62] = true;
                                    var12--;
                                    break;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            String var38 = null;

            for (String var45 : var15) {
               if (var45 != null) {
                  var38 = var45;
                  break;
               }
            }

            if (var38 == null) {
               return null;
            } else {
               ArrayList var40 = new ArrayList();
               HashMap var42 = new HashMap();
               int[] var44 = new int[16];

               for (int var46 = 0; var46 < 16; var46++) {
                  String var48 = var15[var46] == null ? var38 : var15[var46];
                  Integer var53 = (Integer)var42.get(var48);
                  if (var53 == null) {
                     int var58 = Math.max(0, var2.applyAsInt(var48));
                     var53 = var40.size();
                     var40.add(var58);
                     var42.put(var48, var53);
                  }

                  var44[var46] = var53;
               }

               MapColumns.Out var47 = new MapColumns.Out(1024);
               var47.varint(var13.size());

               for (BlockState var54 : var13) {
                  var47.varint(Block.getId(var54));
               }

               var47.varint(var40.size());

               for (int var55 : var40) {
                  var47.varint(var55);
               }

               for (int var51 = 0; var51 < 16; var51++) {
                  var47.write(var44[var51]);
               }

               int[] var52 = new int[256];

               for (int var56 = 0; var56 < 256; var56++) {
                  int var59 = var36[var56];
                  var47.write(var59);
                  int var61 = var56 & 15;
                  int var63 = var56 >> 4;
                  int var64 = var61 > 0 ? var52[var56 - 1] : (var63 > 0 ? var52[var56 - 16] : 64);
                  int var65 = 0;

                  for (int var66 = 0; var66 < var59; var66++) {
                     if (var66 == 0) {
                        var47.zigzag(var8[var56][0] - var64);
                        var52[var56] = var8[var56][0];
                     } else {
                        var47.varint(Math.max(0, var65 - var8[var56][var66]));
                     }

                     var47.varint(var9[var56][var66]);
                     var47.varint(var10[var56][var66]);
                     var65 = var8[var56][var66] - var10[var56][var66];
                  }

                  if (var59 == 0) {
                     var52[var56] = var64;
                  }
               }

               return var47.toByteArray();
            }
         }
      }
   }

   public static MapColumns.Decoded decode(int var0, int var1, byte[] var2, int var3, int var4, int var5) {
      MapColumns.In var6 = new MapColumns.In(var2, var3, var4);
      int var7 = var6.varint();
      if (var7 >= 0 && var7 <= 4096) {
         BlockState[] var8 = new BlockState[var7];

         for (int var9 = 0; var9 < var7; var9++) {
            BlockState var10 = Block.stateById(var6.varint());
            var8[var9] = var10 == null ? Blocks.AIR.defaultBlockState() : var10;
         }

         int var23 = var6.varint();
         if (var23 >= 1 && var23 <= 16) {
            int[] var24 = new int[var23];

            for (int var11 = 0; var11 < var23; var11++) {
               var24[var11] = var6.varint();
            }

            MapColumns.Decoded var25 = new MapColumns.Decoded(var0, var1, var8, var24);

            for (int var12 = 0; var12 < 16; var12++) {
               int var13 = var6.read();
               if (var13 >= var23) {
                  var13 = 0;
               }

               var25.quartBiome[var12] = (byte)var13;
            }

            int[] var26 = new int[256];

            for (int var27 = 0; var27 < 256; var27++) {
               int var14 = var6.read();
               if (var14 > 8) {
                  throw new IllegalArgumentException("layers");
               }

               var25.layers[var27] = (byte)var14;
               var25.top[var27] = new short[var14];
               var25.state[var27] = new short[var14];
               var25.run[var27] = new short[var14];
               int var15 = var27 & 15;
               int var16 = var27 >> 4;
               int var17 = var15 > 0 ? var26[var27 - 1] : (var16 > 0 ? var26[var27 - 16] : 64);
               int var18 = 0;

               for (int var19 = 0; var19 < var14; var19++) {
                  int var20 = var19 == 0 ? var17 + var6.zigzag() : var18 - var6.varint();
                  if (var19 == 0) {
                     var26[var27] = var20;
                  }

                  var25.top[var27][var19] = (short)var20;
                  int var21 = var6.varint();
                  var25.state[var27][var19] = (short)(var21 < var7 ? var21 : 0);
                  int var22 = Math.max(1, Math.min(var6.varint(), 1024));
                  var25.run[var27][var19] = (short)var22;
                  var18 = var20 - var22;
               }

               if (var14 == 0) {
                  var26[var27] = var17;
               }
            }

            return var25;
         } else {
            throw new IllegalArgumentException("biomes");
         }
      } else {
         throw new IllegalArgumentException("palette");
      }
   }

   static byte[] deflate(byte[] var0) {
      Deflater var1 = new Deflater(6);
      var1.setInput(var0);
      var1.finish();
      ByteArrayOutputStream var2 = new ByteArrayOutputStream(var0.length / 3 + 64);
      byte[] var3 = new byte[16384];

      while (!var1.finished()) {
         int var4 = var1.deflate(var3);
         var2.write(var3, 0, var4);
      }

      var1.end();
      return var2.toByteArray();
   }

   static byte[] inflate(byte[] var0, int var1) {
      Inflater var2 = new Inflater();
      var2.setInput(var0);
      ByteArrayOutputStream var3 = new ByteArrayOutputStream(var0.length * 3);
      byte[] var4 = new byte[16384];

      try {
         while (!var2.finished()) {
            int var5 = var2.inflate(var4);
            if (var5 == 0 && (var2.needsInput() || var2.needsDictionary())) {
               break;
            }

            var3.write(var4, 0, var5);
            if (var3.size() > var1) {
               throw new IllegalArgumentException("too large");
            }
         }
      } catch (DataFormatException var9) {
         throw new IllegalArgumentException(var9);
      } finally {
         var2.end();
      }

      return var3.toByteArray();
   }

   public static final class Decoded {
      public final int chunkX;
      public final int chunkZ;
      public final BlockState[] palette;
      public final int[] biomeIds;
      public final byte[] quartBiome = new byte[16];
      public final byte[] layers = new byte[256];
      public final short[][] top = new short[256][];
      public final short[][] state = new short[256][];
      public final short[][] run = new short[256][];

      Decoded(int var1, int var2, BlockState[] var3, int[] var4) {
         this.chunkX = var1;
         this.chunkZ = var2;
         this.palette = var3;
         this.biomeIds = var4;
      }

      public int biomeAt(int var1, int var2) {
         return this.biomeIds[this.quartBiome[((var2 & 15) >> 2) * 4 + ((var1 & 15) >> 2)] & 0xFF];
      }
   }

   public static final class In {
      private final byte[] buf;
      private int pos;
      private final int end;

      public In(byte[] var1, int var2, int var3) {
         this.buf = var1;
         this.pos = var2;
         this.end = var2 + var3;
      }

      public int read() {
         if (this.pos >= this.end) {
            throw new IllegalArgumentException("short");
         } else {
            return this.buf[this.pos++] & 0xFF;
         }
      }

      public int varint() {
         int var1 = 0;
         byte var2 = 0;

         do {
            int var3 = this.read();
            var1 |= (var3 & 127) << var2;
            if ((var3 & 128) == 0) {
               return var1;
            }

            var2 += 7;
         } while (var2 <= 28);

         throw new IllegalArgumentException("varint");
      }

      public int zigzag() {
         int var1 = this.varint();
         return var1 >>> 1 ^ -(var1 & 1);
      }

      public int position() {
         return this.pos;
      }

      public void skip(int var1) {
         this.pos += var1;
      }

      public boolean more() {
         return this.pos < this.end;
      }
   }

   public static final class Out {
      private byte[] buf;
      private int size;

      public Out(int var1) {
         this.buf = new byte[var1];
      }

      public void write(int var1) {
         if (this.size == this.buf.length) {
            this.buf = Arrays.copyOf(this.buf, this.buf.length * 2);
         }

         this.buf[this.size++] = (byte)var1;
      }

      public void varint(int var1) {
         while ((var1 & -128) != 0) {
            this.write(var1 & 127 | 128);
            var1 >>>= 7;
         }

         this.write(var1);
      }

      public void bytes(byte[] var1) {
         for (byte var5 : var1) {
            this.write(var5);
         }
      }

      public void zigzag(int var1) {
         this.varint(var1 << 1 ^ var1 >> 31);
      }

      public int size() {
         return this.size;
      }

      public byte[] toByteArray() {
         return Arrays.copyOf(this.buf, this.size);
      }
   }

   private static final class Section {
      final BlockState[] palette;
      final long[] data;
      final int bits;
      final CompoundTag biomes;

      Section(BlockState[] var1, long[] var2, int var3, CompoundTag var4) {
         this.palette = var1;
         this.data = var2;
         this.bits = var3;
         this.biomes = var4;
      }

      BlockState get(int var1, int var2, int var3) {
         if (this.bits == 0) {
            return this.palette[0];
         } else {
            int var4 = var2 << 8 | var3 << 4 | var1;
            int var5 = 64 / this.bits;
            int var6 = var4 / var5;
            int var7 = var4 % var5 * this.bits;
            if (var6 >= this.data.length) {
               return this.palette[0];
            } else {
               int var8 = (int)(this.data[var6] >>> var7 & (1L << this.bits) - 1L);
               return var8 < this.palette.length ? this.palette[var8] : this.palette[0];
            }
         }
      }

      String biome(int var1, int var2, int var3) {
         if (this.biomes == null) {
            return null;
         } else {
            ListTag var4 = this.biomes.getList("palette", 8);
            if (var4.isEmpty()) {
               return null;
            } else if (var4.size() == 1) {
               return var4.getString(0);
            } else {
               long[] var5 = this.biomes.getLongArray("data");
               int var6 = 32 - Integer.numberOfLeadingZeros(var4.size() - 1);
               int var7 = var2 << 4 | var3 << 2 | var1;
               int var8 = 64 / var6;
               int var9 = var7 / var8;
               int var10 = var7 % var8 * var6;
               if (var9 >= var5.length) {
                  return var4.getString(0);
               } else {
                  int var11 = (int)(var5[var9] >>> var10 & (1L << var6) - 1L);
                  return var4.getString(Math.min(var11, var4.size() - 1));
               }
            }
         }
      }
   }
}
