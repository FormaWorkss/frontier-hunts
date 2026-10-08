package com.formaworks.frontierhunts.perf.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;

/**
 * [perf3] One reusable {@link QuadBakingVertexConsumer} per thread for the mod's per-quad bakes on chunk-mesh workers
 * (grown trees, forest floor, smooth snow, loose leaves).
 *
 * <p>A new QuadBakingVertexConsumer builds an IdentityHashMap of vertex-element offsets and a 32-int buffer every
 * time: about 500 bytes of garbage per baked quad (measured 672 B per quad fresh, 176 B reused: the 176 B are the
 * BakedQuad and its own copy of the data, which is kept). A full-detail realistic tree bakes ~8,000 quads, so that was
 * ~4 MB of garbage per tree baked, more than growing it.
 *
 * <p>The output is identical: {@link QuadBakingVertexConsumer#bakeQuad} copies the data out and resets its vertex
 * buffer and position (zero-filled, as a new one starts), and {@link #begin} puts back every other property a new
 * consumer starts with (tint -1, direction DOWN, the unit sprite, no shade, no ambient occlusion) before each quad.
 * A quad that is begun but never baked (an exception between the two) leaves the slot busy: the next {@link #begin}
 * on that thread then starts from a brand-new consumer, so a half-written buffer is never reused.
 */
public final class FastBake {
   private FastBake() {
   }

   private static final class Slot {
      QuadBakingVertexConsumer consumer = new QuadBakingVertexConsumer();
      boolean busy;
   }

   private static final ThreadLocal<Slot> SLOT = ThreadLocal.withInitial(Slot::new);

   /** A consumer in exactly the state {@code new QuadBakingVertexConsumer()} starts in; finish it with {@link #bake}. */
   public static QuadBakingVertexConsumer begin() {
      Slot slot = SLOT.get();
      if (slot.busy) slot.consumer = new QuadBakingVertexConsumer(); // the last quad on this thread never finished
      slot.busy = true;
      QuadBakingVertexConsumer b = slot.consumer;
      b.setTintIndex(-1);
      b.setDirection(Direction.DOWN);
      b.setSprite(UnitTextureAtlasSprite.INSTANCE);
      b.setShade(false);
      b.setHasAmbientOcclusion(false);
      return b;
   }

   /** The finished quad (the consumer is ready for the next {@link #begin}). */
   public static BakedQuad bake(QuadBakingVertexConsumer b) {
      BakedQuad quad = b.bakeQuad();
      SLOT.get().busy = false;
      return quad;
   }
}
