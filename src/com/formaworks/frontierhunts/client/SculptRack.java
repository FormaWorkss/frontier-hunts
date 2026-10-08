package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [1.1.9] Elk and moose antlers from the original sculpt, reshaped for every bull.
 *
 * <p>The sculpted racks (in the shipped elk / moose meshes) are real antlers: a 6x6 elk with brow, bez, trez, royal
 * and sur-royal, a moose with palms. tools/meshes/export_racks.py finds the beam and each tine on them (models/entity
 * /&lt;species&gt;.fhrk). Here every bull's rack is that sculpt bent to his own: wider or narrower, taller, swept back
 * or forward, tips turning in or out, every tine its own length and lean (left and right never the same), heavier with
 * age, points missing on a young bull, and extra points grown out of the beam on a non-typical. A legend is something
 * else: far bigger than any bull in the herd, massive at the bases, every tine long, and extra points off the beam and
 * crown or the palm rim, different on every legend.
 *
 * <p>Drawn on the head the same way the body is skinned (head scale about the head centre, the head bone's skin
 * matrix), with the coat texture the sculpt was painted on.
 */
public final class SculptRack {
   private SculptRack() {
   }

   /** what the exporter wrote for one species */
   private static final class Base {
      int[] mesh;
      byte[] side;
      float[] g;
      short[] branch;
      byte[] bSide, bRank;
      float[] bJ, bA, bLen, bJg;
      int[][] lods;
      int[] tinesPerSide = new int[2];
      int[] rankOrder;
      /** per side: the point farthest out (its uv is the ivory of a tine tip) */
      int[] tipVertex = new int[2];
   }

   private static final Map<GameSpecies, Base> BASES = new EnumMap<>(GameSpecies.class);
   private static final Map<GameSpecies, Boolean> FAILED = new EnumMap<>(GameSpecies.class);

   /** a rack ready to draw: bind-space triangles */
   static final class Mesh {
      float[] pos, nrm, uv, col;
      int[] tri;
      int triangles;
   }

   private record Key(GameSpecies species, int age, int frame, int genes, int seed, int abnormal, int lod) {
   }

   private static final Map<Key, Mesh> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Key, Mesh> e) {
         return this.size() > 48;
      }
   };

   public static boolean has(GameSpecies sp) {
      return (sp == GameSpecies.ELK || sp == GameSpecies.MOOSE) && base(sp) != null;
   }

   private static synchronized Base base(GameSpecies sp) {
      if (BASES.containsKey(sp)) {
         return BASES.get(sp);
      }
      if (FAILED.containsKey(sp)) {
         return null;
      }
      String name = sp == GameSpecies.ELK ? "elk" : "moose";
      try (InputStream in = SculptRack.class.getResourceAsStream("/assets/frontierhunts/models/entity/" + name + ".fhrk")) {
         if (in == null) {
            throw new IllegalStateException("no " + name + ".fhrk");
         }
         DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in));
         byte[] magic = new byte[8];
         d.readFully(magic);
         if (!"FHRK0001".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("bad magic");
         }
         DeerMeshData m = DeerMeshData.of(sp);
         Base b = new Base();
         int nv = d.readInt();
         b.mesh = new int[nv];
         b.side = new byte[nv];
         b.g = new float[nv];
         b.branch = new short[nv];
         for (int i = 0; i < nv; i++) {
            b.mesh[i] = d.readInt();
            b.side[i] = d.readByte();
            b.g[i] = d.readFloat();
            b.branch[i] = d.readShort();
            int mi = b.mesh[i];
            // the mesh the game loaded must be the one the rack was exported from: every point an antler point
            if (mi < 0 || mi >= m.vertices || (m.region[mi] & DeerMeshData.REGION_ANTLER) == 0) {
               throw new IllegalStateException("mesh mismatch at " + i);
            }
         }
         int nb = d.readInt();
         b.bSide = new byte[nb];
         b.bRank = new byte[nb];
         b.bJ = new float[nb * 3];
         b.bA = new float[nb * 3];
         b.bLen = new float[nb];
         b.bJg = new float[nb];
         for (int i = 0; i < nb; i++) {
            b.bSide[i] = d.readByte();
            for (int k = 0; k < 3; k++) {
               b.bJ[i * 3 + k] = d.readFloat();
            }
            for (int k = 0; k < 3; k++) {
               b.bA[i * 3 + k] = d.readFloat();
            }
            b.bLen[i] = d.readFloat();
            b.bJg[i] = d.readFloat();
            b.bRank[i] = d.readByte();
            b.tinesPerSide[b.bSide[i]]++;
         }
         int nl = d.readInt();
         b.lods = new int[nl][];
         for (int l = 0; l < nl; l++) {
            int nt = d.readInt();
            int[] f = new int[nt * 3];
            for (int k = 0; k < f.length; k++) {
               f[k] = d.readInt();
               if (f[k] < 0 || f[k] >= nv) {
                  throw new IllegalStateException("bad index");
               }
            }
            b.lods[l] = f;
         }
         float[] best = {-1.0F, -1.0F};
         for (int i = 0; i < nv; i++) {
            if (b.g[i] > best[b.side[i]]) {
               best[b.side[i]] = b.g[i];
               b.tipVertex[b.side[i]] = i;
            }
         }
         BASES.put(sp, b);
         return b;
      } catch (Exception e) {
         FAILED.put(sp, Boolean.TRUE);
         org.slf4j.LoggerFactory.getLogger("frontierhunts").warn("sculpted {} rack unavailable, using the drawn racks: {}", name, e.toString());
         return null;
      }
   }

   /** 0 for a bull from the herd, rising to 1 for a legend of the reserve (rack genes past anything that breeds) */
   static float legend(DeerTraits t) {
      return Math.max(0.0F, Math.min(1.0F, (t.rackGenes() - 188) / 34.0F));
   }

   // ============================================================================================ building a rack

   static Mesh mesh(DeerTraits t, int lod) {
      Base b = base(t.species());
      if (b == null) {
         return null;
      }
      lod = Math.max(0, Math.min(b.lods.length - 1, lod));
      Key k = new Key(t.species(), t.ageMonths(), t.frame(), t.rackGenes(), t.seed(), t.abnormal(), lod);
      synchronized (CACHE) {
         Mesh m = CACHE.get(k);
         if (m == null) {
            m = build(b, t, lod);
            CACHE.put(k, m);
         }
         return m;
      }
   }

   private static float clamp(float v, float lo, float hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   private static float range(Random r, float lo, float hi) {
      return lo + r.nextFloat() * (hi - lo);
   }

   /** rotate (x, y, z) about the unit axis (ax, ay, az) by a radians, into out */
   private static void rotate(float x, float y, float z, float ax, float ay, float az, float a, float[] out) {
      float c = (float)Math.cos(a), s = (float)Math.sin(a), d = (ax * x + ay * y + az * z) * (1.0F - c);
      out[0] = x * c + (ay * z - az * y) * s + ax * d;
      out[1] = y * c + (az * x - ax * z) * s + ay * d;
      out[2] = z * c + (ax * y - ay * x) * s + az * d;
   }

   private static Mesh build(Base b, DeerTraits t, int lod) {
      GameSpecies sp = t.species();
      boolean moose = sp == GameSpecies.MOOSE;
      DeerMeshData md = DeerMeshData.of(sp);
      Random rnd = new Random(t.seed() * 0x9E3779B97F4A7C15L + sp.ordinal() * 7919L);
      float L = legend(t);
      float maturity = clamp(t.maturity(), 0.0F, 1.0F);
      // overall size: the herd tops out where the sculpt was made; a legend is a record-book bull, about a quarter past
      // the best of the herd (a real giant, never a cartoon)
      // [1.2.2] a tenth smaller across the board: the racks read too big on the bulls
      float S = DeerDraw.meshRackScale(t) * (1.0F + 0.24F * L) * 0.9F;
      // [1.2.0] what makes this legend one of a kind (fixed for the animal)
      int signature = L > 0.0F ? Math.floorMod(t.seed() >>> 5, 4) : -1;
      float[][] ped = {{md.pedicleLeft.x, md.pedicleLeft.y, md.pedicleLeft.z}, {md.pedicleRight.x, md.pedicleRight.y, md.pedicleRight.z}};
      float sxBase = clamp(t.spreadScale(), 0.9F, 1.12F) * range(rnd, 0.92F, 1.08F) + 0.04F * L + (moose && signature == 0 ? 0.08F * L : 0.0F);
      float syBase = clamp(t.rackHeightScale(), 0.92F, 1.1F) * range(rnd, 0.93F, 1.08F) + 0.03F * L + (moose && signature == 3 ? 0.07F * L : 0.0F);
      float szBase = clamp(t.rackDepthScale(), 0.9F, 1.12F) * range(rnd, 0.94F, 1.06F);
      float sweepBase = (float)Math.toRadians(range(rnd, -7.0F, 9.0F));
      float curlBase = (float)Math.toRadians(range(rnd, -9.0F, 10.0F) - 5.0F * L);
      float[] sx = new float[2], sy = new float[2], sz = new float[2], sweep = new float[2], curl = new float[2];
      for (int s = 0; s < 2; s++) {
         sx[s] = sxBase * range(rnd, 0.96F, 1.04F);
         sy[s] = syBase * range(rnd, 0.96F, 1.04F);
         sz[s] = szBase * range(rnd, 0.97F, 1.03F);
         sweep[s] = sweepBase + (float)Math.toRadians(range(rnd, -2.5F, 2.5F));
         curl[s] = curlBase + (float)Math.toRadians(range(rnd, -3.0F, 3.0F));
      }
      // every tine its own: length and lean, never the same left and right
      int nb = b.bLen.length;
      float[] tineLen = new float[nb], tineLean = new float[nb], leanAxis = new float[nb * 3];
      for (int i = 0; i < nb; i++) {
         float f = range(rnd, 0.86F, 1.14F) + 0.12F * (maturity - 0.6F) + 0.16F * L * range(rnd, 0.8F, 1.2F);
         if (!moose && signature == 2 && b.bRank[i] == 3) {
            f += 0.3F * L; // "the daggers": royal tines like swords
         }
         tineLen[i] = clamp(f, 0.6F, 1.5F);
         tineLean[i] = (float)Math.toRadians(range(rnd, -10.0F, 10.0F));
         // lean about an axis across the tine (perpendicular to it and to up)
         float ax = b.bA[i * 3], ay = b.bA[i * 3 + 1], az = b.bA[i * 3 + 2];
         float cx = ay * 0 - az * 1, cy = az * 0 - ax * 0, cz = ax * 1 - ay * 0;
         float cl = (float)Math.sqrt(cx * cx + cy * cy + cz * cz);
         if (cl < 1.0E-4F) {
            cx = 1.0F;
            cy = 0.0F;
            cz = 0.0F;
            cl = 1.0F;
         }
         if (rnd.nextBoolean()) {
            // or sideways: about the tine's own up-axis cross
            float ux = ay * cz - az * cy, uy = az * cx - ax * cz, uz = ax * cy - ay * cx;
            float ul = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (ul > 1.0E-4F) {
               cx = ux;
               cy = uy;
               cz = uz;
               cl = ul;
            }
         }
         leanAxis[i * 3] = cx / cl;
         leanAxis[i * 3 + 1] = cy / cl;
         leanAxis[i * 3 + 2] = cz / cl;
      }
      // points: a young bull lacks some of the sculpt's tines (they shrink to nubs); a bigger count grows extra ones
      int[] extraRim = new int[2];
      for (int s = 0; s < 2; s++) {
         int want = Math.max(1, t.typicalPoints(s == 1)) - 1;
         int have = b.tinesPerSide[s];
         if (L > 0.0F) {
            int extra = moose ? (signature == 1 ? 3 + (s == 0 ? 1 : 0) : signature == 0 ? 2 : 1) : (signature == 0 ? (s == 0 ? 2 : 1) : signature == 2 ? 0 : 1);
            want = Math.max(want, have + extra);
         }
         int drop = Math.max(0, have - want);
         int[] order = moose ? new int[]{0, 4, 5, 3, 1, 2} : new int[]{4, 1, 3, 2, 0};
         for (int o = 0; o < order.length && drop > 0; o++) {
            for (int i = 0; i < nb; i++) {
               if (b.bSide[i] == s && b.bRank[i] == order[o]) {
                  tineLen[i] = 0.12F;
                  drop--;
                  break;
               }
            }
         }
         extraRim[s] = Math.max(0, want - have);
      }
      float massK = clamp(t.rackMass() - 0.92F, 0.0F, 0.6F);
      float thick = (moose ? 0.002F : 0.004F) * massK + (moose ? 0.0025F : 0.004F) * L;
      float hs = t.headScale();
      float hcx = md.headCentre.x, hcy = md.headCentre.y, hcz = md.headCentre.z;

      int[] faces = b.lods[lod];
      int nv = b.mesh.length;
      // procedural points, decided before allocating
      java.util.List<float[]> extras = new java.util.ArrayList<>();
      Mesh out = new Mesh();
      float[] pos = new float[nv * 3], nrm = new float[nv * 3];
      float[] tmp = new float[3], tmp2 = new float[3];
      for (int i = 0; i < nv; i++) {
         int mi = b.mesh[i];
         float px = md.pos[mi * 3], py = md.pos[mi * 3 + 1], pz = md.pos[mi * 3 + 2];
         float nx = md.nrm[mi * 3], ny = md.nrm[mi * 3 + 1], nz = md.nrm[mi * 3 + 2];
         int s = b.side[i];
         float g = b.g[i];
         int br = b.branch[i];
         if (br >= 0) {
            float jx = b.bJ[br * 3], jy = b.bJ[br * 3 + 1], jz = b.bJ[br * 3 + 2];
            float ax = b.bA[br * 3], ay = b.bA[br * 3 + 1], az = b.bA[br * 3 + 2];
            float dx = px - jx, dy = py - jy, dz = pz - jz;
            float proj = Math.max(0.0F, dx * ax + dy * ay + dz * az);
            float u = Math.min(1.0F, proj / Math.max(1.0E-3F, b.bLen[br]));
            float f = tineLen[br] - 1.0F;
            dx += ax * proj * f;
            dy += ay * proj * f;
            dz += az * proj * f;
            float lean = tineLean[br] * u;
            rotate(dx, dy, dz, leanAxis[br * 3], leanAxis[br * 3 + 1], leanAxis[br * 3 + 2], lean, tmp);
            px = jx + tmp[0];
            py = jy + tmp[1];
            pz = jz + tmp[2];
            rotate(nx, ny, nz, leanAxis[br * 3], leanAxis[br * 3 + 1], leanAxis[br * 3 + 2], lean, tmp);
            nx = tmp[0];
            ny = tmp[1];
            nz = tmp[2];
         }
         // mass first (thicker at the burr than at the tips), then the shape of the whole side, then the size
         float m = thick * (1.0F - 0.7F * g);
         if (g < 0.22F && L > 0.3F) {
            // a legend's bases are gnarled with pearling
            float n = (float)(Math.sin(px * 211.0F) * Math.sin(py * 173.0F) * Math.sin(pz * 197.0F));
            m += 0.0025F * L * n * (1.0F - g / 0.22F);
         }
         px += nx * m;
         py += ny * m;
         pz += nz * m;
         float qx = px - ped[s][0], qy = py - ped[s][1], qz = pz - ped[s][2];
         float w = Math.min(1.0F, g * 1.15F);
         qx *= 1.0F + (sx[s] - 1.0F) * w;
         qy *= 1.0F + (sy[s] - 1.0F) * w;
         qz *= 1.0F + (sz[s] - 1.0F) * w;
         // sweep: tips back (+) or forward, about the lateral axis
         float a = sweep[s] * w;
         rotate(qx, qy, qz, 1.0F, 0.0F, 0.0F, a, tmp);
         rotate(nx, ny, nz, 1.0F, 0.0F, 0.0F, a, tmp2);
         // curl: tips turning in (+) or out, about the vertical
         float c = curl[s] * w * (s == 0 ? -1.0F : 1.0F);
         rotate(tmp[0], tmp[1], tmp[2], 0.0F, 1.0F, 0.0F, c, tmp);
         rotate(tmp2[0], tmp2[1], tmp2[2], 0.0F, 1.0F, 0.0F, c, tmp2);
         px = ped[s][0] + tmp[0] * S;
         py = ped[s][1] + tmp[1] * S;
         pz = ped[s][2] + tmp[2] * S;
         // the head's own scale about its centre, as the body is skinned
         pos[i * 3] = hcx + (px - hcx) * hs;
         pos[i * 3 + 1] = hcy + (py - hcy) * hs;
         pos[i * 3 + 2] = hcz + (pz - hcz) * hs;
         nrm[i * 3] = tmp2[0];
         nrm[i * 3 + 1] = tmp2[1];
         nrm[i * 3 + 2] = tmp2[2];
      }
      // extra points: rim / crown points for a bigger count or a legend, stickers and drop tines on a non-typical
      for (int s = 0; s < 2; s++) {
         int abn = Math.min(8, t.abnormalPoints(s == 1));
         // a legend's own mark: drop tines (elk 1, moose 2) or a kicker (elk 3)
         int legendDrops = L > 0.25F && (!moose && signature == 1 || moose && signature == 2 && s == 1) ? 1 : 0;
         int legendKickers = L > 0.25F && !moose && signature == 3 && s == 0 ? 1 : 0;
         for (int e = 0; e < extraRim[s] + abn + legendDrops + legendKickers; e++) {
            boolean rim = e < extraRim[s];
            boolean forcedDrop = !rim && e < extraRim[s] + legendDrops;
            // a moose grows extra points off the palm's rim, not spikes out of its face
            if (moose && !rim && !forcedDrop) {
               rim = rnd.nextFloat() < 0.8F;
            }
            boolean drop = forcedDrop || !rim && e >= extraRim[s] + legendDrops + legendKickers && rnd.nextFloat() < 0.35F;
            float gLo = rim ? (moose ? 0.58F : 0.86F) : 0.22F, gHi = rim ? (moose ? 0.96F : 1.0F) : 0.78F;
            int pick = -1;
            for (int tries = 0; tries < 60 && pick < 0; tries++) {
               int i = rnd.nextInt(nv);
               if (b.side[i] == s && b.branch[i] < 0 && b.g[i] >= gLo && b.g[i] <= gHi) {
                  pick = i;
               }
            }
            if (pick >= 0) {
               float len = (rim ? range(rnd, 0.1F, 0.19F) : drop ? range(rnd, 0.1F, 0.18F) : range(rnd, 0.06F, 0.16F)) * (1.0F + 0.15F * L);
               extras.add(new float[]{pick, s, rim ? 0 : drop ? 1 : 2, len, rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()});
            }
         }
      }
      int sides = lod == 0 ? 7 : 5, rings = lod == 0 ? 6 : 4;
      int perTine = sides * (rings + 1) + 1;
      int extraV = extras.size() * perTine, extraT = extras.size() * (sides * rings * 2 + sides);
      out.pos = new float[(nv + extraV) * 3];
      out.nrm = new float[(nv + extraV) * 3];
      out.uv = new float[(nv + extraV) * 2];
      out.col = new float[(nv + extraV) * 3];
      out.tri = new int[faces.length + extraT * 3];
      System.arraycopy(pos, 0, out.pos, 0, nv * 3);
      System.arraycopy(nrm, 0, out.nrm, 0, nv * 3);
      for (int i = 0; i < nv; i++) {
         int mi = b.mesh[i];
         out.uv[i * 2] = md.uv[mi * 2];
         out.uv[i * 2 + 1] = md.uv[mi * 2 + 1];
         out.col[i * 3] = md.col[mi * 3];
         out.col[i * 3 + 1] = md.col[mi * 3 + 1];
         out.col[i * 3 + 2] = md.col[mi * 3 + 2];
      }
      System.arraycopy(faces, 0, out.tri, 0, faces.length);
      int vi = nv, ti = faces.length;
      for (float[] e : extras) {
         int a = (int)e[0], s = (int)e[1], kind = (int)e[2];
         float len = e[3] * S * hs;
         float bx = out.pos[a * 3], by = out.pos[a * 3 + 1], bz = out.pos[a * 3 + 2];
         float nx = out.nrm[a * 3], ny = out.nrm[a * 3 + 1], nz = out.nrm[a * 3 + 2];
         float ox = bx - ped[s][0], oy = by - ped[s][1], oz = bz - ped[s][2];
         float ol = (float)Math.sqrt(ox * ox + oy * oy + oz * oz) + 1.0E-6F;
         ox /= ol;
         oy /= ol;
         oz /= ol;
         float jx = e[4] - 0.5F, jy = e[5] - 0.5F, jz = e[6] - 0.5F;
         float dx, dy, dz;
         if (kind == 0) {
            dx = ox * 0.6F + nx * 0.45F + jx * 0.3F;
            dy = oy * 0.6F + ny * 0.45F + 0.35F + jy * 0.3F;
            dz = oz * 0.6F + nz * 0.45F + jz * 0.3F;
         } else if (kind == 1) {
            dx = nx * 0.5F + jx * 0.25F;
            dy = ny * 0.3F - 0.85F;
            dz = nz * 0.5F + jz * 0.25F;
         } else {
            dx = nx + jx * 0.45F;
            dy = ny + 0.2F + jy * 0.45F;
            dz = nz + jz * 0.45F;
         }
         float dl = (float)Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-6F;
         dx /= dl;
         dy /= dl;
         dz /= dl;
         // a frame across the tine: u toward the surface normal it grew from (the palm's normal), r across
         float nd = nx * dx + ny * dy + nz * dz;
         float ux = nx - dx * nd, uy = ny - dy * nd, uz = nz - dz * nd;
         float ul = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
         if (ul < 1.0E-3F) {
            ux = -dz;
            uy = 0.0F;
            uz = dx;
            ul = (float)Math.sqrt(ux * ux + uz * uz) + 1.0E-6F;
         }
         ux /= ul;
         uy /= ul;
         uz /= ul;
         float rx = uy * dz - uz * dy, ry = uz * dx - ux * dz, rz = ux * dy - uy * dx;
         float r0 = (moose ? 0.028F : 0.022F) * (float)Math.pow(S, 0.8) * hs * (kind == 0 ? 1.0F : 0.85F);
         // a palm point is flat, in the plane of the palm
         float flat2 = moose && kind == 0 ? 0.5F : 1.0F;
         float bend = kind == 1 ? -0.35F : 0.45F; // drop tines hook down, the rest curve up
         float baseU = out.uv[a * 2], baseV = out.uv[a * 2 + 1];
         int tip = b.tipVertex[s];
         float tipU = md.uv[b.mesh[tip] * 2], tipV = md.uv[b.mesh[tip] * 2 + 1];
         int first = vi;
         float cx = bx - nx * r0 * 0.75F, cy = by - ny * r0 * 0.75F, cz = bz - nz * r0 * 0.75F;
         float tx = dx, ty = dy, tz = dz;
         for (int ring = 0; ring <= rings; ring++) {
            float u = ring / (float)rings;
            float rad = r0 * (1.0F - (float)Math.pow(u, 1.6) * 0.72F);
            if (ring > 0) {
               float step = len / rings;
               cx += tx * step;
               cy += ty * step;
               cz += tz * step;
               // bend the direction a little toward up (or down) each ring
               ty += bend * step / Math.max(0.05F, len) * 0.9F;
               float tl = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
               tx /= tl;
               ty /= tl;
               tz /= tl;
            }
            boolean ivory = ring >= rings - 1;
            for (int k = 0; k < sides; k++) {
               double ang = Math.PI * 2.0 * k / sides;
               float ca = (float)Math.cos(ang), sa = (float)Math.sin(ang);
               float ex = rx * ca + ux * sa, ey = ry * ca + uy * sa, ez = rz * ca + uz * sa;
               float fx = rx * ca + ux * sa * flat2, fy = ry * ca + uy * sa * flat2, fz = rz * ca + uz * sa * flat2;
               out.pos[vi * 3] = cx + fx * rad;
               out.pos[vi * 3 + 1] = cy + fy * rad;
               out.pos[vi * 3 + 2] = cz + fz * rad;
               out.nrm[vi * 3] = ex;
               out.nrm[vi * 3 + 1] = ey;
               out.nrm[vi * 3 + 2] = ez;
               out.uv[vi * 2] = ivory ? tipU : baseU;
               out.uv[vi * 2 + 1] = ivory ? tipV : baseV;
               float lit = ivory ? 0.86F : 0.62F;
               out.col[vi * 3] = lit;
               out.col[vi * 3 + 1] = lit * 0.93F;
               out.col[vi * 3 + 2] = lit * 0.84F;
               vi++;
            }
         }
         int apex = vi;
         // a rounded tip, not a needle
         out.pos[vi * 3] = cx + tx * r0 * 0.4F;
         out.pos[vi * 3 + 1] = cy + ty * r0 * 0.4F;
         out.pos[vi * 3 + 2] = cz + tz * r0 * 0.4F;
         out.nrm[vi * 3] = tx;
         out.nrm[vi * 3 + 1] = ty;
         out.nrm[vi * 3 + 2] = tz;
         out.uv[vi * 2] = tipU;
         out.uv[vi * 2 + 1] = tipV;
         out.col[vi * 3] = 0.9F;
         out.col[vi * 3 + 1] = 0.86F;
         out.col[vi * 3 + 2] = 0.78F;
         vi++;
         for (int ring = 0; ring < rings; ring++) {
            for (int k = 0; k < sides; k++) {
               int a0 = first + ring * sides + k, a1 = first + ring * sides + (k + 1) % sides;
               int b0 = a0 + sides, b1 = a1 + sides;
               out.tri[ti++] = a0;
               out.tri[ti++] = a1;
               out.tri[ti++] = b1;
               out.tri[ti++] = a0;
               out.tri[ti++] = b1;
               out.tri[ti++] = b0;
            }
         }
         for (int k = 0; k < sides; k++) {
            out.tri[ti++] = first + rings * sides + k;
            out.tri[ti++] = first + rings * sides + (k + 1) % sides;
            out.tri[ti++] = apex;
         }
      }
      out.triangles = ti / 3;
      return out;
   }

   // ============================================================================================ drawing

   private static final Vector3f P = new Vector3f(), N = new Vector3f();

   /**
    * Draws the bull's rack on his head. {@code buffers} gives the coat-textured buffer; {@code single}, if not null, is
    * used instead (the thermal view draws everything into one). {@code lod} 0 (close) .. 3; {@code tint} an optional
    * rgb multiplier. Returns false if nothing was drawn (no sculpt for this species), so the caller can fall back.
    */
   public static boolean draw(PoseStack ps, MultiBufferSource buffers, VertexConsumer single, int light, DeerTraits t, DeerAnimator an, int lod,
      float[] tint) {
      return draw(ps, buffers, single, light, t, an.skin, lod, tint);
   }

   /** the same, from the skin matrices of the pose */
   public static boolean draw(PoseStack ps, MultiBufferSource buffers, VertexConsumer single, int light, DeerTraits t, Matrix4f[] skins, int lod,
      float[] tint) {
      if (!t.buck() || !has(t.species()) || skins == null) {
         return false;
      }
      Mesh m = mesh(t, lod);
      if (m == null) {
         return false;
      }
      DeerMeshData md = DeerMeshData.of(t.species());
      int ml = Math.max(0, Math.min(md.lodMode.length - 1, lod));
      boolean flat = md.lodMode[ml] == 1;
      VertexConsumer vc = single;
      if (vc == null) {
         ResourceLocation tex = flat ? DeerDraw.MATERIAL : RealisticCoats.coat(t);
         vc = buffers.getBuffer(HuntRenderTypes.sculpt(tex));
      }
      int head = DeerSkeleton.of(t.species()).head;
      if (head < 0 || head >= skins.length || skins[head] == null) {
         return false;
      }
      Matrix4f skin = skins[head];
      PoseStack.Pose pose = ps.last();
      Matrix4f mat = new Matrix4f(pose.pose()).mul(skin);
      Matrix3f nm = new Matrix3f(pose.normal()).mul(new Matrix3f(skin));
      float tr = tint == null ? 1.0F : tint[0], tg = tint == null ? 1.0F : tint[1], tb = tint == null ? 1.0F : tint[2];
      int overlay = OverlayTexture.NO_OVERLAY;
      synchronized (P) {
         for (int i = 0; i < m.triangles * 3; i++) {
            int v = m.tri[i];
            mat.transformPosition(m.pos[v * 3], m.pos[v * 3 + 1], m.pos[v * 3 + 2], P);
            nm.transform(m.nrm[v * 3], m.nrm[v * 3 + 1], m.nrm[v * 3 + 2], N);
            N.normalize();
            float cr = flat ? m.col[v * 3] : 1.0F, cg = flat ? m.col[v * 3 + 1] : 1.0F, cb = flat ? m.col[v * 3 + 2] : 1.0F;
            int r = (int)(255 * Math.min(1.0F, cr * tr)), g = (int)(255 * Math.min(1.0F, cg * tg)), bl = (int)(255 * Math.min(1.0F, cb * tb));
            vc.addVertex(P.x, P.y, P.z, 0xFF000000 | r << 16 | g << 8 | bl, m.uv[v * 2], m.uv[v * 2 + 1], overlay, light, N.x, N.y, N.z);
         }
      }
      return true;
   }

   public static void clear() {
      synchronized (CACHE) {
         CACHE.clear();
      }
   }
}
