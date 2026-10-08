package com.formaworks.frontierhunts.hunting.rutfight;

import com.formaworks.frontierhunts.hunting.AntlerGeometry;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [rutfight] {@link FightModel} of the realistic (Ultra) sculpted mesh: whitetail racks from the procedural
 * {@link AntlerGeometry} on the skeleton's antler frame, elk / moose racks from the mesh's antler region (skinned with
 * the same neck girth / head size / rack scale the renderer uses), skull from the head-weighted mesh faces. Common code:
 * the server sizes fights with it, Ultra clients use the very same numbers.
 */
public final class UltraFightModels {
   /** WhitetailRenderer's extra realistic-mesh scale for whitetail (the sculpt is modelled larger than the rig). */
   public static final float WHITETAIL_REAL_XZ = 0.829932F;
   public static final float WHITETAIL_REAL_Y = 0.8297214F;
   /** AntlerGeometry detail used for contact (the renderer's close-up level). */
   static final int ANTLER_LOD = 8;
   private static final Map<DeerTraits, FightModel> CACHE = new LinkedHashMap<DeerTraits, FightModel>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<DeerTraits, FightModel> e) {
         return this.size() > 48;
      }
   };

   private UltraFightModels() {
   }

   public static FightModel of(DeerTraits t) {
      synchronized (CACHE) {
         FightModel m = CACHE.get(t);
         if (m != null) {
            return m;
         }
      }

      // built outside the lock (tens of ms on a background thread): a render-thread lookup of a cached model never
      // waits for another animal's build
      FightModel m = build(t);
      synchronized (CACHE) {
         FightModel had = CACHE.putIfAbsent(t, m);
         return had != null ? had : m;
      }
   }

   static float meshRackScale(DeerTraits t) {
      return t.species().meshAntlers() && t.buck() ? Math.max(0.55F, Math.min(1.12F, t.rackScale())) : 1.0F;
   }

   private static FightModel build(DeerTraits t) {
      GameSpecies s = t.species();
      DeerSkeleton sk = DeerSkeleton.of(s);
      Matrix4f[] model = FightPose.canonical(sk);
      Matrix4f[] skin = new Matrix4f[model.length];

      for (int i = 0; i < model.length; i++) {
         skin[i] = new Matrix4f(model[i]).mul(sk.inverseBind[i]);
      }

      Matrix4f head = model[sk.head];
      Matrix4f toHead = new Matrix4f(head).invert();
      DeerMeshData mesh = DeerMeshData.of(s);
      float[] pos = new float[mesh.vertices * 3];
      float[] nrm = new float[mesh.vertices * 3];
      mesh.skin(0, skin, t.neckGirth(), t.headScale(), meshRackScale(t), pos, nrm);
      int[] faces = mesh.faces(0, t.buck());
      FightModel.Points antler = new FightModel.Points();
      FightModel.Points skull = new FightModel.Points();
      Vector3f a = new Vector3f();
      Vector3f b = new Vector3f();
      Vector3f c = new Vector3f();

      for (int i = 0; i + 2 < faces.length; i += 3) {
         int va = faces[i];
         int vb = faces[i + 1];
         int vc = faces[i + 2];
         boolean isAntler = ((mesh.region[va] | mesh.region[vb] | mesh.region[vc]) & DeerMeshData.REGION_ANTLER) != 0;
         boolean isSkull = !isAntler && mesh.headWeight[va] > 0.5F && mesh.headWeight[vb] > 0.5F && mesh.headWeight[vc] > 0.5F;
         if (isAntler || isSkull) {
            toHead.transformPosition(pos[va * 3], pos[va * 3 + 1], pos[va * 3 + 2], a);
            toHead.transformPosition(pos[vb * 3], pos[vb * 3 + 1], pos[vb * 3 + 2], b);
            toHead.transformPosition(pos[vc * 3], pos[vc * 3 + 1], pos[vc * 3 + 2], c);
            (isAntler ? antler : skull).triangle(a, b, c);
         }
      }

      if (t.buck() && s.antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL) {
         AntlerGeometry g = AntlerGeometry.of(t, ANTLER_LOD);
         Matrix4f frame = sk.antlerFrame;

         for (int i = 0; i + 2 < g.tris.length; i += 3) {
            frame.transformPosition(g.pos[g.tris[i] * 3], g.pos[g.tris[i] * 3 + 1], g.pos[g.tris[i] * 3 + 2], a);
            frame.transformPosition(g.pos[g.tris[i + 1] * 3], g.pos[g.tris[i + 1] * 3 + 1], g.pos[g.tris[i + 1] * 3 + 2], b);
            frame.transformPosition(g.pos[g.tris[i + 2] * 3], g.pos[g.tris[i + 2] * 3 + 1], g.pos[g.tris[i + 2] * 3 + 2], c);
            antler.triangle(a, b, c);
         }
      }

      float rx = s == GameSpecies.WHITETAIL ? WHITETAIL_REAL_XZ : 1.0F;
      float ry = s == GameSpecies.WHITETAIL ? WHITETAIL_REAL_Y : 1.0F;
      return new FightModel(head, t.frameWidth() * rx, t.frameHeight() * ry, t.frameLength() * rx, antler.toArray(), skull.toArray());
   }
}
