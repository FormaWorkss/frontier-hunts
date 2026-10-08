package com.formaworks.frontierhunts.wildlife2026.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * A skinned animal mesh baked in Blender (.fhsk): model space in blocks, y up, head toward -z, feet at y = 0.
 * Up to four bone influences per vertex; bones are listed parents-first with their rest-pose joint positions.
 */
public final class SkinnedMesh {
   private static final Map<ResourceLocation, SkinnedMesh> CACHE = new HashMap<>();
   private static final SkinnedMesh MISSING = new SkinnedMesh();

   public boolean bird;
   /** height, legLen, belly, headYawFix, feedAngle, strideFreq, headPitchFix, spare */
   public final float[] meta = new float[8];
   public String[] names = new String[0];
   public int[] parent = new int[0];
   public float[] joint = new float[0];
   /** constant rest-pose correction per bone (x, y, z radians), e.g. squaring up legs sculpted mid-stride */
   public float[] rest = new float[0];
   /** grazing pose per bone (x, y, z radians): body tipped at the hips, neck/head lowered, legs re-solved to stay planted */
   public float[] feed = new float[0];
   public int vertexCount;
   public float[] pos = new float[0];
   public float[] nrm = new float[0];
   public float[] uv = new float[0];
   public byte[] bone = new byte[0];
   public float[] weight = new float[0];
   public int[] tris = new int[0];
   private final Map<String, Integer> index = new HashMap<>();
   /** [meshes] compact influence list for MeshSkinner, built on first use */
   MeshSkinner.Compiled compiled;
   /** [anims] skeleton measures for WildlifeGait, built on first use */
   WildlifeGait.Info gait;

   private SkinnedMesh() {
   }

   public boolean valid() {
      return this.vertexCount > 0 && this.names.length > 0;
   }

   public int bone(String name) {
      Integer i = this.index.get(name);
      return i == null ? -1 : i;
   }

   /** Loaded once per resource; a missing or broken file yields an empty mesh so the renderer falls back to Classic. */
   public static SkinnedMesh get(ResourceLocation loc) {
      SkinnedMesh m = READY.get(loc); // [perf2] lock-free once loaded (every animal asks every frame)
      if (m != null) return m;
      synchronized (SkinnedMesh.class) {
         m = CACHE.get(loc);
         if (m == null) {
            try {
               m = read(loc);
            } catch (Exception e) {
               m = MISSING;
            }
            CACHE.put(loc, m);
            READY.put(loc, m);
         }
         return m;
      }
   }

   /**
    * [perf2] Whether the mesh is loaded; if not, it is read on a background thread (the gzip parse of a sculpted body
    * took several ms on the render thread the first time an animal came close). Use another mesh meanwhile.
    */
   public static boolean ready(ResourceLocation loc) {
      if (READY.containsKey(loc)) return true;
      if (PREFETCHING.add(loc)) {
         net.minecraft.Util.backgroundExecutor().execute(() -> {
            try {
               get(loc);
            } finally {
               PREFETCHING.remove(loc);
            }
         });
      }
      return false;
   }

   private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, SkinnedMesh> READY = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.util.Set<ResourceLocation> PREFETCHING = java.util.concurrent.ConcurrentHashMap.newKeySet();

   public static synchronized void clear() {
      CACHE.clear();
      READY.clear(); // [perf2]
   }

   private static SkinnedMesh read(ResourceLocation loc) throws IOException {
      try (InputStream raw = Minecraft.getInstance().getResourceManager().open(loc)) {
         return parse(raw);
      }
   }

   static SkinnedMesh parse(InputStream raw) throws IOException {
      try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw, 65536)))) {
         SkinnedMesh m = new SkinnedMesh();
         int magic = in.readInt();
         if (magic != 0x46485331 && magic != 0x46485332) {
            throw new IOException("not an fhsk mesh");
         }
         boolean v2 = magic == 0x46485332;
         m.bird = (in.readInt() & 1) != 0;
         for (int i = 0; i < 8; i++) {
            m.meta[i] = in.readFloat();
         }
         int nb = in.readShort();
         m.names = new String[nb];
         m.parent = new int[nb];
         m.joint = new float[nb * 3];
         m.rest = new float[nb * 3];
         m.feed = new float[nb * 3];
         for (int b = 0; b < nb; b++) {
            byte[] s = new byte[in.readShort()];
            in.readFully(s);
            m.names[b] = new String(s, java.nio.charset.StandardCharsets.UTF_8);
            m.index.put(m.names[b], b);
            m.parent[b] = in.readShort();
            if (m.parent[b] >= b) {
               // WildlifeRig.pose composes each bone onto its parent's matrix from this frame: parents must come first
               throw new IOException("bone " + m.names[b] + " listed before its parent");
            }
            m.joint[b * 3] = in.readFloat();
            m.joint[b * 3 + 1] = in.readFloat();
            m.joint[b * 3 + 2] = in.readFloat();
            m.rest[b * 3] = in.readFloat();
            m.rest[b * 3 + 1] = in.readFloat();
            m.rest[b * 3 + 2] = in.readFloat();
            if (v2) {
               m.feed[b * 3] = in.readFloat();
               m.feed[b * 3 + 1] = in.readFloat();
               m.feed[b * 3 + 2] = in.readFloat();
            }
         }
         int nv = in.readInt();
         m.vertexCount = nv;
         m.pos = new float[nv * 3];
         m.nrm = new float[nv * 3];
         m.uv = new float[nv * 2];
         m.bone = new byte[nv * 4];
         m.weight = new float[nv * 4];
         for (int v = 0; v < nv; v++) {
            for (int k = 0; k < 3; k++) {
               m.pos[v * 3 + k] = in.readFloat();
            }
            for (int k = 0; k < 3; k++) {
               m.nrm[v * 3 + k] = in.readFloat();
            }
            m.uv[v * 2] = in.readFloat();
            m.uv[v * 2 + 1] = in.readFloat();
            for (int k = 0; k < 4; k++) {
               int b = in.readUnsignedByte();
               m.bone[v * 4 + k] = (byte)(b < nb ? b : 0);
            }
            float sum = 0.0F;
            for (int k = 0; k < 4; k++) {
               sum += m.weight[v * 4 + k] = in.readUnsignedByte() / 255.0F;
            }
            if (sum <= 0.0F) {
               // unweighted vertex: pin it to its first bone instead of collapsing it onto the model origin
               m.weight[v * 4] = 1.0F;
            } else {
               // byte quantisation leaves sums like 253/255, which would shrink the skinned vertex toward the origin
               for (int k = 0; k < 4; k++) {
                  m.weight[v * 4 + k] /= sum;
               }
            }
         }
         int nt = in.readInt();
         m.tris = new int[nt * 3];
         for (int i = 0; i < nt * 3; i++) {
            int t = in.readUnsignedShort();
            m.tris[i] = t < nv ? t : 0;
         }
         return m;
      }
   }
}
