package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.TopoStyle;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * [phone] The Maps app's topographic map: the ground this client has loaded around the hunter, surveyed a few rows a
 * tick (heights, surface colours, water depth, forest and snow) while Maps is open, then styled by {@link TopoStyle}
 * into a 256 x 256 texture (a block a pixel). It re-surveys when the hunter walks well away from its centre; the old map
 * stays up until the new one is done.
 */
final class PhoneMap {
   static final int N = 256;
   private static final int ROWS_PER_TICK = 16;
   private static final ResourceLocation ID = FrontierHunts.id("phone_map");
   private static DynamicTexture texture;
   private static final int[] HEIGHT = new int[N * N];
   private static final int[] COLOR = new int[N * N];
   private static final byte[] KIND = new byte[N * N];
   private static final byte[] DEPTH = new byte[N * N];
   private static final int[] OUT = new int[N * N];
   private static boolean wanted;
   private static int row = -1;
   private static int buildX, buildZ;
   private static int idle;
   private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

   private PhoneMap() {
   }

   static void wanted(boolean on) {
      wanted = on;
      if (on && row < 0) {
         idle = 0;
      }
   }

   static void reset() {
      wanted = false;
      row = -1;
      if (texture != null) {
         Minecraft.getInstance().getTextureManager().release(ID);
         texture = null;
      }
      PhoneFeed.MODEL.map.texture = null;
   }

   static void tick(Minecraft mc) {
      ClientLevel level = mc.level;
      if (!wanted || level == null || mc.player == null) {
         return;
      }
      PhoneModel.MapData md = PhoneFeed.MODEL.map;
      int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
      if (row < 0) {
         boolean stale = md.texture == null || Math.abs(px - md.centerX) > 64 || Math.abs(pz - md.centerZ) > 64 || ++idle > 20 * 30;
         if (!stale) {
            return;
         }
         idle = 0;
         row = 0;
         buildX = px;
         buildZ = pz;
         if (md.texture == null) {
            md.progress = 0.0F;
         }
      }
      int end = Math.min(N, row + ROWS_PER_TICK);
      for (int y = row; y < end; y++) {
         for (int x = 0; x < N; x++) {
            survey(level, buildX - N / 2 + x, buildZ - N / 2 + y, y * N + x);
         }
      }
      row = end;
      if (md.texture == null) {
         md.progress = row / (float)N * 0.95F;
      }
      if (row >= N) {
         TopoStyle.style(N, HEIGHT, COLOR, KIND, DEPTH, OUT, 0, N);
         upload(mc);
         md.centerX = buildX;
         md.centerZ = buildZ;
         md.size = N;
         md.progress = 1.0F;
         md.texture = ID;
         row = -1;
      }
   }

   private static void survey(ClientLevel level, int x, int z, int i) {
      if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
         KIND[i] = (byte)TopoStyle.NONE;
         HEIGHT[i] = 64;
         return;
      }
      int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
      int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
      BlockState s = level.getBlockState(POS.set(x, top, z));
      byte kind = 0;
      int color;
      if (s.getFluidState().is(FluidTags.WATER)) {
         kind = TopoStyle.WATER;
         int d = 0;
         while (d < 20 && level.getBlockState(POS.set(x, top - d - 1, z)).getFluidState().is(FluidTags.WATER)) {
            d++;
         }
         DEPTH[i] = (byte)d;
         color = 0x4060C0;
         HEIGHT[i] = top;
      } else {
         DEPTH[i] = 0;
         if (s.is(BlockTags.LEAVES)) {
            kind = TopoStyle.FOREST;
         } else if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
            kind = TopoStyle.SNOW;
         }
         color = s.getMapColor(level, POS.set(x, top, z)).col;
         if (color == 0) {
            color = level.getBlockState(POS.set(x, ground, z)).getMapColor(level, POS).col;
         }
         HEIGHT[i] = ground;
      }
      KIND[i] = kind;
      COLOR[i] = color;
   }

   private static void upload(Minecraft mc) {
      if (texture == null) {
         texture = new DynamicTexture(N, N, false);
         mc.getTextureManager().register(ID, texture);
      }
      NativeImage img = texture.getPixels();
      if (img == null) {
         return;
      }
      for (int y = 0; y < N; y++) {
         for (int x = 0; x < N; x++) {
            int c = OUT[y * N + x];
            // ARGB -> the image's ABGR
            img.setPixelRGBA(x, y, c & 0xFF00FF00 | (c >> 16 & 0xFF) | (c & 0xFF) << 16);
         }
      }
      texture.upload();
   }
}
