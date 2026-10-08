package com.formaworks.frontierhunts.client.rack;

import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [1.1.8] Draws a whitetail, elk or moose rack from {@link RackGen} on the head bone. The rack is built once per
 * animal (traits + level of detail) and cached; each frame it is only transformed: pose * head bone * antler frame.
 */
public final class RackDraw {
   private RackDraw() {
   }

   private record Key(GameSpecies species, int pl, int pr, int al, int ar, int size, int mass, int spread, int height, int depth, int variant,
      int seed, int sides) {
   }

   private static final Map<Key, RackGen.Mesh> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Key, RackGen.Mesh> e) {
         return this.size() > 160;
      }
   };
   private static final Vector3f P = new Vector3f(), N = new Vector3f();

   static RackGen.Params params(DeerTraits t) {
      RackGen.Params p = new RackGen.Params();
      GameSpecies sp = t.species();
      p.family = sp == GameSpecies.ELK ? RackGen.Family.ELK : sp == GameSpecies.MOOSE ? RackGen.Family.MOOSE : RackGen.Family.WHITETAIL;
      p.pointsL = t.typicalPoints(false);
      p.pointsR = t.typicalPoints(true);
      p.abnL = Math.min(8, t.abnormalPoints(false));
      p.abnR = Math.min(8, t.abnormalPoints(true));
      float rs = t.rackScale();
      // [1.2.0] a big mature rack is heavier, not swollen: beams and tines stay the girth real antlers have
      p.size = Math.min(1.22F, Math.max(0.3F, rs));
      p.mass = Math.max(0.75F, Math.min(1.22F, t.rackMass() * (1.0F + Math.max(0.0F, rs - 1.22F) * 0.4F)));
      p.spread = Math.max(0.9F, Math.min(1.12F, t.spreadScale()));
      p.height = Math.max(0.92F, Math.min(1.1F, t.rackHeightScale()));
      p.depth = Math.max(0.9F, Math.min(1.12F, t.rackDepthScale()));
      // [1.2.0] a legend of the reserve: a record-book rack, about a fifth past the herd's best - long tines, heavy
      // bases, a little more spread - never a cartoon
      float legend = Math.max(0.0F, Math.min(1.0F, (t.rackGenes() - 188) / 34.0F));
      if (legend > 0.0F && sp == GameSpecies.WHITETAIL) {
         // [1.2.2] the Old Ridge Buck: the rack of a lifetime, far past anything in the herd - wide, tall and heavy, every
         // tine long, the bases thick as a wrist
         p.size = 1.22F + 0.55F * legend;
         p.mass = Math.min(1.5F, p.mass + 0.28F * legend);
         p.spread = Math.min(1.28F, p.spread + 0.12F * legend);
         p.height = Math.min(1.2F, p.height + 0.1F * legend);
         p.pointsL = Math.max(p.pointsL, legend > 0.5F ? 6 : 5);
         p.pointsR = Math.max(p.pointsR, legend > 0.5F ? 6 : 5);
      } else if (legend > 0.0F) {
         p.size = 1.22F + 0.26F * legend;
         p.mass = Math.min(1.32F, p.mass + 0.12F * legend);
         p.spread = Math.min(1.16F, p.spread + 0.04F * legend);
         p.height = Math.min(1.12F, p.height + 0.04F * legend);
      }
      if (sp != GameSpecies.WHITETAIL) {
         p.size *= 0.9F; // [1.2.2] a tenth smaller across the board, as the sculpted racks
      }
      p.variant = Math.floorMod(t.seed() >>> 9, 5);
      p.seed = t.seed();
      try {
         p.pedicle = DeerSkeleton.of(sp).pedicleHalfSpacing;
      } catch (RuntimeException e) {
         p.pedicle = 0.075F;
      }
      return p;
   }

   static RackGen.Mesh mesh(DeerTraits t, int sides) {
      RackGen.Params p = params(t);
      Key k = new Key(t.species(), p.pointsL, p.pointsR, p.abnL, p.abnR, Math.round(p.size * 200), Math.round(p.mass * 200), Math.round(p.spread * 200),
         Math.round(p.height * 200), Math.round(p.depth * 200), p.variant, t.seed(), sides);
      synchronized (CACHE) {
         RackGen.Mesh m = CACHE.get(k);
         if (m == null) {
            m = RackGen.build(p, sides);
            CACHE.put(k, m);
         }
         return m;
      }
   }

   /**
    * Draws the rack of {@code t} (nothing for a doe or a cow) on the head bone {@code head} (model space, as posed).
    * {@code segments} is the renderer's level of detail (4 far .. 8 close); {@code tint} an optional rgb multiplier
    * (thermal view), null for the antler's own colour.
    */
   public static void draw(PoseStack ps, VertexConsumer vc, int light, DeerTraits t, Matrix4f head, int segments, float[] tint) {
      if (!t.buck() || t.species().antlers == GameSpecies.Antlers.NONE) {
         return;
      }
      RackGen.Mesh m = mesh(t, Math.max(4, Math.min(10, segments + (segments >= 8 ? 2 : 0))));
      Matrix4f frame;
      try {
         frame = DeerSkeleton.of(t.species()).antlerFrame;
      } catch (RuntimeException e) {
         frame = new Matrix4f();
      }
      PoseStack.Pose pose = ps.last();
      Matrix4f mat = new Matrix4f(pose.pose()).mul(head).mul(frame);
      Matrix3f nm = new Matrix3f(pose.normal()).mul(new Matrix3f(head).mul(new Matrix3f(frame)));
      float tr = tint == null ? 1.0F : tint[0], tg = tint == null ? 1.0F : tint[1], tb = tint == null ? 1.0F : tint[2];
      int overlay = OverlayTexture.NO_OVERLAY;
      for (int i = 0; i < m.triangles * 3; i++) {
         int v = m.tri[i];
         mat.transformPosition(m.pos[v * 3], m.pos[v * 3 + 1], m.pos[v * 3 + 2], P);
         nm.transform(m.nrm[v * 3], m.nrm[v * 3 + 1], m.nrm[v * 3 + 2], N);
         N.normalize();
         // a touch darker and warmer at the base, the texture brings the ivory tips
         float b = 0.86F + 0.14F * m.shade[v];
         int r = (int)(255 * Math.min(1.0F, b * tr)), g = (int)(255 * Math.min(1.0F, b * 0.98F * tg)), bl = (int)(255 * Math.min(1.0F, b * 0.95F * tb));
         int color = 0xFF000000 | r << 16 | g << 8 | bl;
         vc.addVertex(P.x, P.y, P.z, color, m.uv[v * 2], m.uv[v * 2 + 1], overlay, light, N.x, N.y, N.z);
      }
   }

   public static void clear() {
      synchronized (CACHE) {
         CACHE.clear();
      }
   }
}
