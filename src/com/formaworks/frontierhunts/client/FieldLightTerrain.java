package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/** Flashlight terrain tiles (4x4x4 block meshes) for the projected beam. [shaderperf] copied from the base jar to batch its draws. */
final class FieldLightTerrain implements AutoCloseable {
   private static final int LIMIT = 896;
   private final Map<BlockPos, FieldLightTerrain.Tile> tiles = new HashMap<>();
   private ClientLevel level;
   private long scanTick = -1L;
   private final RandomSource random = RandomSource.create(0L);
   private final PoseStack pose = new PoseStack();
   private final MutableBlockPos cursor = new MutableBlockPos();

   void update(ClientLevel var1, Vec3 var2, Vec3 var3) {
      if (this.level != var1) {
         this.close();
         this.level = var1;
      }

      long var4 = this.level.getGameTime();
      if (this.scanTick != var4) {
         this.scanTick = var4;
         this.tiles.values().forEach(var0 -> var0.wanted = false);
         ArrayList<BlockPos> var6 = new ArrayList<>();
         int var7 = (int)Math.floor(var2.x / 4.0) * 4;
         int var8 = (int)Math.floor(var2.y / 4.0) * 4;
         int var9 = (int)Math.floor(var2.z / 4.0) * 4;

         for (int var10 = -10; var10 <= 10; var10++) {
            for (int var11 = -10; var11 <= 10; var11++) {
               for (int var12 = -10; var12 <= 10; var12++) {
                  double var13 = (double)(var7 + var10 * 4 + 2) - var2.x;
                  double var15 = (double)(var8 + var11 * 4 + 2) - var2.y;
                  double var17 = (double)(var9 + var12 * 4 + 2) - var2.z;
                  double var19 = var13 * var13 + var15 * var15 + var17 * var17;
                  if (!(var19 > 1849.0) && !(var13 * var3.x + var15 * var3.y + var17 * var3.z < Math.sqrt(var19) * 0.68 - 4.0)) {
                     BlockPos var21 = new BlockPos(var7 + var10 * 4, var8 + var11 * 4, var9 + var12 * 4);
                     if (!this.level.isOutsideBuildHeight(var21) && this.level.hasChunkAt(var21)) {
                        var6.add(var21);
                     }
                  }
               }
            }
         }

         var6.sort(Comparator.comparingDouble((BlockPos var1x) -> var1x.distToCenterSqr(var2)));
         HashSet<BlockPos> var25 = new HashSet<>(var6.subList(0, Math.min(896, var6.size())));
         this.tiles.entrySet().removeIf(var1x -> {
            if (!var25.contains(var1x.getKey())) {
               var1x.getValue().close();
               return true;
            } else {
               return false;
            }
         });

         for (BlockPos var29 : var25) {
            this.tiles.computeIfAbsent(var29, FieldLightTerrain.Tile::new).wanted = true;
         }
      }

      long var22 = System.nanoTime() + 1500000L;
      int var23 = 12;
      Iterator<FieldLightTerrain.Tile> var24 = this.tiles
         .values()
         .stream()
         .filter(var2x -> var2x.wanted && (var2x.checked == 0L || var4 - var2x.checked > 20L))
         .sorted(Comparator.comparingDouble((FieldLightTerrain.Tile var1x) -> (double)(var1x.checked == 0L ? -10000 : 0) + var1x.origin.distToCenterSqr(var2)))
         .iterator();

      while (var24.hasNext() && var23-- > 0 && System.nanoTime() < var22) {
         FieldLightTerrain.Tile var26 = var24.next();
         int var28 = 1;

         for (int var30 = -1; var30 <= 4; var30++) {
            for (int var31 = -1; var31 <= 4; var31++) {
               for (int var14 = -1; var14 <= 4; var14++) {
                  this.cursor.set(var26.origin.getX() + var30, var26.origin.getY() + var31, var26.origin.getZ() + var14);
                  var28 = 31 * var28 + System.identityHashCode(this.level.getBlockState(this.cursor));
               }
            }
         }

         if (var26.checked == 0L || var28 != var26.signature) {
            this.build(var26);
            var26.signature = var28;
         }

         var26.checked = Math.max(1L, var4);
      }
   }

   private void build(FieldLightTerrain.Tile var1) {
      try (ByteBufferBuilder var2 = new ByteBufferBuilder(32768)) {
         BufferBuilder var3 = new BufferBuilder(var2, Mode.QUADS, DefaultVertexFormat.BLOCK);
         BlockRenderDispatcher var4 = Minecraft.getInstance().getBlockRenderer();

         for (int var5 = 0; var5 < 64; var5++) {
            int var6 = var5 & 3;
            int var7 = var5 >> 4 & 3;
            int var8 = var5 >> 2 & 3;
            this.cursor.set(var1.origin.getX() + var6, var1.origin.getY() + var7, var1.origin.getZ() + var8);
            BlockState var9 = this.level.getBlockState(this.cursor);
            if (var9.getRenderShape() == RenderShape.MODEL) {
               this.pose.pushPose();
               this.pose.translate((float)var6, (float)var7, (float)var8);
               ModelData var10 = this.level.getModelDataManager().getAt(this.cursor);

               try {
                  var4.renderBatched(var9, this.cursor, this.level, this.pose, var3, true, this.random, var10 == null ? ModelData.EMPTY : var10, null);
               } finally {
                  this.pose.popPose();
               }
            }
         }

         MeshData var18 = var3.build();
         var1.close();
         if (var18 != null) {
            var1.gpu = new VertexBuffer(Usage.STATIC);
            var1.gpu.bind();
            var1.gpu.upload(var18);
            VertexBuffer.unbind();
         }
      }
   }

   // [shaderperf] reused per draw instead of a new Matrix4f / AABB per tile and a new Frustum per pass
   private final Matrix4f tileView = new Matrix4f();
   /** [shaderperf] bumped by every beam (depth) pass; a tile drawn by it carries the value in {@code Tile.beam} */
   private int beamPass;

   /**
    * Draws every built tile visible from {@code var1} with {@code var4}. [shaderperf] The shader is applied once and only
    * the model-view matrix is re-uploaded per tile (as vanilla draws chunk sections), instead of a full
    * {@code drawWithShader} per tile: that re-sent every default uniform and re-bound up to 12 samplers for each of up to
    * 896 tiles, twice per flashlight per frame. The light pass (the shader that has {@code ShadowFromView}) also skips
    * tiles outside the beam: its fragment shader discards everything outside the beam's shadow frustum, so they never
    * showed. Same pixels, far fewer GL calls (worse with a shader pack, whose driver state is heavier).
    */
   int draw(Vec3 var1, Matrix4f var2, Matrix4f var3, ShaderInstance var4) {
      Frustum var5 = new Frustum(var2, var3);
      var5.prepare(var1.x, var1.y, var1.z);
      boolean light = var4.getUniform("ShadowFromView") != null;
      if (!light) {
         this.beamPass++;
      }
      int var6 = 0;
      boolean applied = false;

      try {
         for (FieldLightTerrain.Tile var8 : this.tiles.values()) {
            if (var8.gpu == null) {
               continue;
            }
            // (a tile outside the beam could be skipped in the light pass too, but that relies on the passes' order with
            // several lights about: kept simple and exact)
            if (!var5.isVisible(var8.bounds)) {
               continue;
            }
            if (!light) {
               var8.beam = this.beamPass;
            }
            this.tileView.set(var2).translate(
               (float)((double)var8.origin.getX() - var1.x), (float)((double)var8.origin.getY() - var1.y), (float)((double)var8.origin.getZ() - var1.z)
            );
            var8.gpu.bind();
            if (!applied) {
               var4.setDefaultUniforms(Mode.QUADS, this.tileView, var3, Minecraft.getInstance().getWindow());
               var4.apply();
               applied = true;
            } else if (var4.MODEL_VIEW_MATRIX != null) {
               var4.MODEL_VIEW_MATRIX.set(this.tileView);
               var4.MODEL_VIEW_MATRIX.upload();
            }
            var8.gpu.draw();
            var6++;
         }
      } finally {
         if (applied) {
            var4.clear();
         }
         VertexBuffer.unbind();
      }

      return var6;
   }

   @Override
   public void close() {
      this.tiles.values().forEach(FieldLightTerrain.Tile::close);
      this.tiles.clear();
      this.level = null;
      this.scanTick = -1L;
   }

   private static final class Tile implements AutoCloseable {
      final BlockPos origin;
      VertexBuffer gpu;
      long checked;
      boolean wanted;
      int signature;
      /** [shaderperf] the beam pass that last drew this tile (see FieldLightTerrain.draw) */
      int beam = -1;
      /** [shaderperf] fixed 4x4x4 bounds, made once instead of per tile per pass */
      final AABB bounds;

      Tile(BlockPos var1) {
         this.origin = var1;
         this.bounds = new AABB(
            (double)var1.getX(), (double)var1.getY(), (double)var1.getZ(), (double)(var1.getX() + 4), (double)(var1.getY() + 4), (double)(var1.getZ() + 4)
         );
      }

      @Override
      public void close() {
         if (this.gpu != null) {
            this.gpu.close();
         }

         this.gpu = null;
      }

   }
}
