package com.formaworks.frontierhunts.client.rutfight;

import com.formaworks.frontierhunts.client.CubeAnimalData;
import com.formaworks.frontierhunts.client.ElkCubes;
import com.formaworks.frontierhunts.client.ElkVanillaCubes;
import com.formaworks.frontierhunts.client.McAnimalPose;
import com.formaworks.frontierhunts.client.MooseCubes;
import com.formaworks.frontierhunts.client.MooseVanillaCubes;
import com.formaworks.frontierhunts.client.WhitetailCubes;
import com.formaworks.frontierhunts.client.WhitetailVanillaCubes;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.rutfight.FightModel;
import com.formaworks.frontierhunts.hunting.rutfight.FightPose;
import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [rutfight] {@link FightModel}s of the two box-model presets, built from exactly what WhitetailRenderer draws:
 * the retired Balanced look (BLOCKY, detailed cube model on the deer skeleton; no longer drawn) and Vanilla (VANILLA, Minecraft-mob parts posed by
 * {@link McAnimalPose}). Antler cubes are the ones ClassicAntlers draws, with the same rack scaling about the antler
 * base; skull cubes are the other head-bone cubes.
 */
public final class BoxFightModels {
   private record Key(DeerTraits traits, boolean vanilla) {
   }

   private static final Map<Key, FightModel> CACHE = new LinkedHashMap<Key, FightModel>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Key, FightModel> e) {
         return this.size() > 48;
      }
   };

   private BoxFightModels() {
   }

   public static FightModel of(DeerTraits t, boolean vanilla) {
      Key key = new Key(t, vanilla);
      synchronized (CACHE) {
         FightModel m = CACHE.get(key);
         if (m == null) {
            m = build(t, vanilla);
            CACHE.put(key, m);
         }

         return m;
      }
   }

   /** First antler cube (ClassicAntlers.start). */
   static int antlerStart(GameSpecies s, boolean vanilla) {
      if (vanilla) {
         return s == GameSpecies.ELK ? ElkVanillaCubes.ANTLER_START : (s == GameSpecies.MOOSE ? MooseVanillaCubes.ANTLER_START : 21);
      } else {
         return s == GameSpecies.ELK ? ElkCubes.ANTLER_START : (s == GameSpecies.MOOSE ? MooseCubes.ANTLER_START : WhitetailCubes.ANTLER_START);
      }
   }

   /** Head bone of the cube data (ClassicAntlers.headBone). */
   static int headBone(GameSpecies s) {
      return s == GameSpecies.WHITETAIL ? 10 : 7;
   }

   /** The rack-scaled head skin ClassicAntlers draws the antler cubes with. */
   public static Matrix4f[] rackSkin(DeerTraits t, Matrix4f[] skin, boolean vanilla) {
      GameSpecies s = t.species();
      int hb = headBone(s);
      float scale = Math.max(0.6F, Math.min(1.2F, t.rackScale()));
      float[] base = vanilla
         ? (s == GameSpecies.ELK ? ElkVanillaCubes.ANTLER_BASE : (s == GameSpecies.MOOSE ? MooseVanillaCubes.ANTLER_BASE : WhitetailVanillaCubes.ANTLER_BASE))
         : (s == GameSpecies.ELK ? ElkCubes.ANTLER_BASE : (s == GameSpecies.MOOSE ? MooseCubes.ANTLER_BASE : WhitetailCubes.ANTLER_BASE));
      float[] standHead = vanilla
         ? (s == GameSpecies.ELK ? ElkVanillaCubes.STAND_SKIN : (s == GameSpecies.MOOSE ? MooseVanillaCubes.STAND_SKIN : WhitetailVanillaCubes.STAND_SKIN))[hb]
         : (s == GameSpecies.ELK ? ElkCubes.STAND_HEAD_SKIN : (s == GameSpecies.MOOSE ? MooseCubes.STAND_HEAD_SKIN : WhitetailCubes.STAND_HEAD_SKIN));
      Vector3f p = new Matrix4f().set(standHead).invert().transformPosition(new Vector3f(base[0], base[1], base[2]));
      Matrix4f[] out = skin.clone();
      out[hb] = new Matrix4f(skin[hb]).translate(p).scale(scale).translate(-p.x, -p.y, -p.z);
      return out;
   }

   /**
    * Canonical locked pose of a box preset: {{locked head, head the skin was posed with}, skin}. Head-bone space is the
    * same for both heads (the cubes ride rigidly on the head bone).
    */
   public static Matrix4f[][] canonical(DeerTraits t, boolean vanilla) {
      GameSpecies s = t.species();
      if (vanilla) {
         DeerAnimator.Input in = new DeerAnimator.Input();
         in.buck = t.buck();
         in.fightLower = 1.0F;
         McAnimalPose mp = McAnimalPose.still(s, in, t.frameLength());
         return new Matrix4f[][]{{new Matrix4f(mp.head), new Matrix4f(mp.head)}, mp.skin};
      } else {
         DeerSkeleton sk = DeerSkeleton.of(s);
         Matrix4f[] model = FightPose.canonical(sk);
         Matrix4f[] skin = new Matrix4f[model.length];

         for (int i = 0; i < model.length; i++) {
            skin[i] = new Matrix4f(model[i]).mul(sk.inverseBind[i]);
         }

         // the cube head carries its rack higher on the skull than the sculpt: lift the face so the rack points at the
         // rival instead of hanging to the ground (the client's head reach turns the head to this)
         Matrix4f head = new Matrix4f(model[sk.head]);
         Vector3f hp = head.getTranslation(new Vector3f());
         head = new Matrix4f().translate(hp).rotateX(blockyPitch(s)).translate(-hp.x, -hp.y, -hp.z).mul(head);
         return new Matrix4f[][]{{head, new Matrix4f(model[sk.head])}, skin};
      }
   }

   /** Extra face lift of the Balanced (BLOCKY) locked head over the sculpt's held spar frame (radians). */
   static float blockyPitch(GameSpecies s) {
      return switch (s) {
         case ELK -> 0.12F;
         case MOOSE -> 0.5F;
         default -> 0.8F;
      };
   }

   private static FightModel build(DeerTraits t, boolean vanilla) {
      GameSpecies s = t.species();
      Matrix4f[][] c = canonical(t, vanilla);
      Matrix4f head = c[0][0];
      Matrix4f[] skin = c[1];
      Matrix4f[] rack = t.buck() ? rackSkin(t, skin, vanilla) : skin;
      Matrix4f toHead = new Matrix4f(c[0][1]).invert();
      CubeAnimalData d = CubeAnimalData.of(s, vanilla);
      int start = antlerStart(s, vanilla);
      int hb = headBone(s);
      FightModel.Points antler = new FightModel.Points();
      FightModel.Points skull = new FightModel.Points();
      float[] data = d.data;

      for (int k = 0; k < d.bones.length; k++) {
         int bone = d.bones[k];
         boolean isAntler = k >= start;
         if (bone < 0 || bone >= skin.length || isAntler && !t.buck() || !isAntler && bone != hb) {
            continue;
         }

         Matrix4f m = new Matrix4f(toHead).mul(isAntler ? rack[bone] : skin[bone]);
         int o = k * d.stride;
         Vector3f origin = m.transformPosition(new Vector3f(data[o], data[o + 1], data[o + 2]));
         Vector3f e0 = m.transformDirection(new Vector3f(data[o + 3], data[o + 4], data[o + 5]));
         Vector3f e1 = m.transformDirection(new Vector3f(data[o + 6], data[o + 7], data[o + 8]));
         Vector3f e2 = m.transformDirection(new Vector3f(data[o + 9], data[o + 10], data[o + 11]));
         (isAntler ? antler : skull).box(origin, e0, e1, e2);
      }

      return new FightModel(head, t.frameWidth(), t.frameHeight(), t.frameLength(), antler.toArray(), skull.toArray());
   }
}
