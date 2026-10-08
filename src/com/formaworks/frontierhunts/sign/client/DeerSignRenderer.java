package com.formaworks.frontierhunts.sign.client;

import com.formaworks.frontierhunts.client.tree.SignTrunkProbe;
import com.formaworks.frontierhunts.hunting.DeerSign;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [sign] Draws buck rubs and scrapes where they really are, for everyone, with no tool:
 * <ul>
 * <li>RUB: a strip of bark torn off one side of the trunk from knee to waist / chest height (higher and wider for a
 *     big-bodied buck, elk and moose higher still): pale wet cambium when fresh, drying yellow-tan, then oxidised
 *     orange-brown and finally a grey weathered scar. It follows the trunk surface: on a realistic round trunk (Ultra,
 *     {@link SignTrunkProbe}) it wraps the stem's real centre, radius, flare and lean; on a block log (Vanilla,
 *     Minecraft world) it lies on the log face. Peeled bark curls hang from its torn edges (pale inner side out) and shavings lie
 *     on the ground at the foot of the tree.</li>
 * <li>SCRAPE: a pawed oval of bare soil about a metre long with leaf litter, torn grass and clods kicked out behind,
 *     a print or two inside; it drapes over the ground block by block (steps, snow layers) and fades as it dries and
 *     leaves blow back over it. Above it a low licking branch reaches out from the tree with a chewed, broken tip.</li>
 * </ul>
 * Everything is a cut-out decal / small mesh in the entity pipeline: offset off the surface by a distance-scaled gap
 * (no z-fighting, near or far), real normals (shader packs light it like any entity), a pre-filtered far texture beyond
 * 14 blocks (entity textures have no mipmaps). Geometry is a few hundred vertices per sign.
 */
public final class DeerSignRenderer implements BlockEntityRenderer<DeerSign.Mark> {
   static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/entity/deer_sign.png");
   static final ResourceLocation TEX_FAR = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/entity/deer_sign_far.png");
   private static final float AW = 1024.0F;
   private static final float AH = 512.0F;
   private static final int DAY = 24000;

   public DeerSignRenderer(BlockEntityRendererProvider.Context ctx) {
   }

   // ------------------------------------------------------------------------------------------------ entry

   @Override
   public void render(DeerSign.Mark mark, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
      Level level = mark.getLevel();
      BlockState state = mark.getBlockState();
      if (level == null || !(state.getBlock() instanceof DeerSign sign) || sign.kind == DeerSign.Kind.BED) {
         return;
      }
      // [shaderperf] a rub or scrape is bark / ground art a few millimetres off its surface: its shadow is invisible, so
      // a shader pack's shadow pass (which draws block entities again) skips rebuilding the few hundred vertices
      if (com.formaworks.frontierhunts.client.HuntShaderCompat.shadowPass()) {
         return;
      }
      try {
         Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         BlockPos pos = mark.getBlockPos();
         double dist = Math.sqrt(cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
         VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(dist > 14.0 ? TEX_FAR : TEX));
         float gap = 0.006F + (float)dist * 0.0005F;
         float days = mark.age(level.getGameTime()) / (float)DAY;
         Mesh mesh = new Mesh(vc, pose.last(), pos);
         if (sign.kind == DeerSign.Kind.RUB) {
            rub(mark, state, level, mesh, days, gap);
         } else {
            scrape(mark, state, level, mesh, days, gap);
         }
      } catch (RuntimeException e) {
         // a sign is decoration: never break the frame over one
      }
   }

   @Override
   public int getViewDistance() {
      return 64;
   }

   @Override
   public AABB getRenderBoundingBox(DeerSign.Mark mark) {
      BlockPos p = mark.getBlockPos();
      boolean rub = mark.getBlockState().getBlock() instanceof DeerSign s && s.kind == DeerSign.Kind.RUB;
      return rub ? new AABB(p).inflate(1.0, 0.0, 1.0).expandTowards(0.0, 2.2, 0.0).expandTowards(0.0, -1.0, 0.0)
         : new AABB(p).inflate(3.2, 0.0, 3.2).expandTowards(0.0, 3.5, 0.0).expandTowards(0.0, -0.6, 0.0);
   }

   // ------------------------------------------------------------------------------------------------ helpers

   /** Deterministic 0..1 noise for shape variety. */
   static float rnd(int seed, int i) {
      int h = seed * 0x9E3779B1 + i * 0x85EBCA6B;
      h ^= h >>> 15;
      h *= 0x2C1B3C6D;
      h ^= h >>> 12;
      h *= 0x297A2D39;
      h ^= h >>> 15;
      return (h >>> 8) / (float)(1 << 24);
   }

   private static int lightAt(Level level, double x, double y, double z) {
      return LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
   }

   /** Species / size heights of a rub: {bottom, top, width} in metres. */
   private static float[] rubSize(DeerSign.Mark mark) {
      float s = mark.size();
      int seed = mark.seed;
      float lo;
      float hi;
      float w;
      if ("elk".equals(mark.species)) {
         lo = 0.55F;
         hi = 1.55F;
         w = 0.24F;
      } else if ("moose".equals(mark.species)) {
         lo = 0.65F;
         hi = 1.75F;
         w = 0.28F;
      } else {
         lo = 0.26F + 0.14F * s;
         hi = 0.78F + 0.42F * s;
         w = 0.10F + 0.09F * s;
      }
      lo += (rnd(seed, 1) - 0.5F) * 0.08F;
      hi += (rnd(seed, 2) - 0.5F) * 0.12F;
      w *= 0.85F + 0.3F * rnd(seed, 3);
      if (mark.worked()) {
         // [deersign] exactly the band the buck's antlers worked
         lo = mark.lo;
         hi = mark.hi;
      }
      return new float[]{lo, hi, w};
   }

   /** Rub art stage by age in days: fresh, drying, oxidised, grey scar; and the progress within it. */
   private static float[] rubStage(float days) {
      float[] edges = {0.0F, 1.0F, 2.6F, 5.0F, 7.5F};
      for (int i = 0; i < 4; i++) {
         if (days < edges[i + 1] || i == 3) {
            return new float[]{i, Math.min(1.0F, (days - edges[i]) / (edges[i + 1] - edges[i]))};
         }
      }
      return new float[]{3, 1};
   }

   // ------------------------------------------------------------------------------------------------ trunk

   /** The trunk a rub is on: a round realistic stem (sockets) or a block log. */
   private static final class Trunk {
      final float groundY;
      final float cx;
      final float cz;
      /** round stem samples {y, x, z, core radius} bottom-up; null for a block log */
      final float[][] stem;

      Trunk(float groundY, float cx, float cz, float[][] stem) {
         this.groundY = groundY;
         this.cx = cx;
         this.cz = cz;
         this.stem = stem;
      }

      boolean round() {
         return this.stem != null;
      }

      /** {centre x, centre z, radius} at world height y */
      float[] at(float y) {
         if (this.stem == null) {
            return new float[]{this.cx, this.cz, 0.5F};
         }
         float[] a = this.stem[0];
         float[] b = this.stem[this.stem.length - 1];
         if (y <= a[0] || this.stem.length == 1) {
            b = a;
         } else {
            for (int i = 1; i < this.stem.length; i++) {
               if (y <= this.stem[i][0]) {
                  a = this.stem[i - 1];
                  b = this.stem[i];
                  break;
               }
               a = this.stem[i];
               b = this.stem[i];
            }
         }
         float t = b[0] - a[0] > 1.0E-4F ? Math.clamp((y - a[0]) / (b[0] - a[0]), 0.0F, 1.0F) : 0.0F;
         float r = (a[3] + (b[3] - a[3]) * t) * SignTrunkProbe.flare(y - this.groundY);
         return new float[]{a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, r};
      }
   }

   private static BlockPos trunkBase(Level level, BlockPos log) {
      BlockPos base = log;
      for (int i = 0; i < 8 && level.getBlockState(base.below()).is(BlockTags.LOGS); i++) {
         base = base.below();
      }
      return base;
   }

   private static Trunk trunk(Level level, BlockPos base) {
      float[][] samples = new float[4][];
      int n = 0;
      for (int i = 0; i < 4; i++) {
         BlockPos p = base.above(i);
         BlockState s = level.getBlockState(p);
         if (!s.is(BlockTags.LOGS)) {
            break;
         }
         float[] st = SignTrunkProbe.stemAt(p, s);
         if (st == null) {
            break;
         }
         samples[n++] = new float[]{st[1], st[0], st[2], st[3] / SignTrunkProbe.flare(st[1] - base.getY())};
      }
      if (n == 0) {
         return new Trunk(base.getY(), base.getX() + 0.5F, base.getZ() + 0.5F, null);
      }
      return new Trunk(base.getY(), samples[0][1], samples[0][2], java.util.Arrays.copyOf(samples, n));
   }

   // ------------------------------------------------------------------------------------------------ rub

   private static void rub(DeerSign.Mark mark, BlockState state, Level level, Mesh mesh, float days, float gap) {
      Direction face = state.getValue(DeerSign.FACING);
      BlockPos cell = mark.getBlockPos();
      BlockPos log = cell.relative(face.getOpposite());
      if (!level.getBlockState(log).is(BlockTags.LOGS)) {
         return;
      }
      BlockPos base = trunkBase(level, log);
      Trunk trunk = trunk(level, base);
      int seed = mark.seed;
      float[] size = rubSize(mark);
      // [deersign] the rub grows stroke by stroke while the buck works it (an interrupted rub stays partial)
      float made = mark.progress(level.getGameTime());
      if (made <= 0.0F) {
         return;
      }
      float grow = made * made * (3.0F - 2.0F * made);
      float mid = (size[0] + size[1]) * 0.5F;
      float halfH = (size[1] - size[0]) * 0.5F * (0.3F + 0.7F * grow);
      float y0 = trunk.groundY + mid - halfH;
      float y1 = trunk.groundY + mid + halfH;
      // a rub reads across the trunk face: a block log stands in for a sapling, a round trunk gets a strip of its girth
      float width;
      if (trunk.round()) {
         float r = trunk.at((y0 + y1) * 0.5F)[2];
         width = Math.clamp(r * (1.05F + 0.55F * mark.size()) * (0.9F + 0.2F * rnd(seed, 8)), size[2], r * 2.3F);
      } else {
         width = Math.min(0.62F, size[2] * 2.2F + 0.08F);
      }
      width *= 0.45F + 0.55F * grow; // [deersign]
      float[] stage = rubStage(days);
      int st = (int)stage[0];
      // [deersign] a worked rub faces exactly where the buck stood (square to this face)
      float ang0 = (float)Math.atan2(face.getStepZ(), face.getStepX()) + (trunk.round() && !mark.worked() ? (rnd(seed, 4) - 0.5F) * 0.5F : 0.0F);
      float tint = 1.0F - 0.10F * stage[1] - 0.04F * rnd(seed, 5);
      boolean flip = rnd(seed, 6) < 0.5F;
      int light0 = lightAt(level, cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
      int light1 = lightAt(level, cell.getX() + 0.5, cell.getY() + 1.5, cell.getZ() + 0.5);
      int nu = trunk.round() ? 9 : 2;
      int nv = trunk.round() ? 8 : 2;
      float[][][] P = new float[nu][nv][];
      float[][][] N = new float[nu][nv][];
      for (int i = 0; i < nu; i++) {
         float fu = i / (nu - 1.0F) - 0.5F;
         for (int j = 0; j < nv; j++) {
            float fv = j / (nv - 1.0F);
            float y = y1 + (y0 - y1) * fv;
            float[] sp = surface(trunk, ang0, face, fu * width, y, gap);
            P[i][j] = new float[]{sp[0], sp[1], sp[2]};
            N[i][j] = new float[]{sp[3], 0.0F, sp[4]};
         }
      }
      float u0 = st * 128.0F;
      for (int i = 0; i < nu - 1; i++) {
         for (int j = 0; j < nv - 1; j++) {
            float ua = u0 + (flip ? 1 - i / (nu - 1.0F) : i / (nu - 1.0F)) * 128.0F;
            float ub = u0 + (flip ? 1 - (i + 1) / (nu - 1.0F) : (i + 1) / (nu - 1.0F)) * 128.0F;
            float va = j / (nv - 1.0F) * 256.0F;
            float vb = (j + 1) / (nv - 1.0F) * 256.0F;
            int la = P[i][j][1] - trunk.groundY < 1.0F ? light0 : light1;
            int lb = P[i][j + 1][1] - trunk.groundY < 1.0F ? light0 : light1;
            mesh.quad(P[i][j], P[i + 1][j], P[i + 1][j + 1], P[i][j + 1], ua, va, ub, va, ub, vb, ua, vb, N[i][j], tint, la, la, lb, lb,
               new float[][]{N[i][j], N[i + 1][j], N[i + 1][j + 1], N[i][j + 1]});
         }
      }
      // peeled bark curls at the torn edges: fewer as the rub ages (they dry and drop)
      int curls = st >= 3 ? 1 + (int)(rnd(seed, 7) * 2) : 5 + (int)(rnd(seed, 7) * 4) - st;
      curls = (int)(curls * Math.clamp((made - 0.25F) / 0.6F, 0.0F, 1.0F)); // [deersign] bark peels as the work goes on
      float sizeK = 0.7F + 0.5F * mark.size();
      for (int c = 0; c < curls; c++) {
         float r1 = rnd(seed, 20 + c);
         float r2 = rnd(seed, 40 + c);
         boolean top = c % 3 != 2;
         float fu = (r1 - 0.5F) * (top ? 0.7F : 0.9F);
         float y = top ? y1 - (y1 - y0) * (0.04F + 0.06F * r2) : y0 + (y1 - y0) * (0.03F + 0.05F * r2);
         float[] a = surface(trunk, ang0, face, fu * width, y, gap + 0.002F);
         float len = (0.06F + 0.09F * rnd(seed, 60 + c)) * sizeK;
         float wid = (0.02F + 0.022F * rnd(seed, 80 + c)) * sizeK;
         int variant = (int)(rnd(seed, 100 + c) * 2);
         curl(mesh, a, len, wid, top, rnd(seed, 120 + c), variant, st, top ? light1 : light0, tint);
      }
      // shavings on the ground at the foot of the trunk (fresh to a few days)
      if (days < 4.5F && made > 0.3F && cell.getY() == base.getY() && level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
         float[] g = trunk.at(trunk.groundY + 0.05F);
         float r = trunk.round() ? g[2] : 0.5F;
         float nx = (float)Math.cos(ang0);
         float nz = (float)Math.sin(ang0);
         if (!trunk.round()) {
            nx = face.getStepX();
            nz = face.getStepZ();
         }
         float tx = -nz;
         float tz = nx;
         float y = cell.getY() + gap * 0.8F + 0.002F;
         float along = 0.40F * sizeK;
         float across = Math.max(0.5F, width * 1.3F) * sizeK;
         float sx = g[0] + nx * (r + 0.01F);
         float sz = g[1] + nz * (r + 0.01F);
         float[] p0 = {sx - tx * across * 0.5F, y, sz - tz * across * 0.5F};
         float[] p1 = {sx + tx * across * 0.5F, y, sz + tz * across * 0.5F};
         float[] p2 = {p1[0] + nx * along, y, p1[2] + nz * along};
         float[] p3 = {p0[0] + nx * along, y, p0[2] + nz * along};
         float vv = days < 1.5F ? 0.0F : 128.0F;
         float shade = days < 1.5F ? 1.0F : 0.92F - 0.08F * Math.min(1.0F, (days - 1.5F) / 3.0F);
         mesh.quad(p0, p1, p2, p3, 704, vv, 832, vv, 832, vv + 128, 704, vv + 128, new float[]{0, 1, 0}, shade, light0, light0, light0, light0, null);
      }
   }

   /**
    * A point on the trunk surface: u metres of arc across from the rub's centre line, at world height y, lifted gap
    * off the bark. Returns {x, y, z, nx, nz}.
    */
   private static float[] surface(Trunk trunk, float ang0, Direction face, float u, float y, float gap) {
      if (trunk.round()) {
         float[] c = trunk.at(y);
         float r = c[2] + gap + 0.012F; // the stem polygon lies inside its nominal radius; keep the decal clear of it
         float maxArc = r * 1.3F;
         float a = ang0 + Math.clamp(u, -maxArc, maxArc) / r;
         float nx = (float)Math.cos(a);
         float nz = (float)Math.sin(a);
         return new float[]{c[0] + nx * r, y, c[1] + nz * r, nx, nz};
      }
      float nx = face.getStepX();
      float nz = face.getStepZ();
      float tx = -nz;
      float tz = nx;
      float uu = Math.clamp(u, -0.46F, 0.46F);
      return new float[]{trunk.cx + nx * (0.5F + gap) + tx * uu, y, trunk.cz + nz * (0.5F + gap) + tz * uu, nx, nz};
   }

   /** One bark curl: a narrow strip peeling off the edge, curling outward; pale inner side out, bark side in. */
   private static void curl(Mesh mesh, float[] a, float len, float wid, boolean top, float twist, int variant, int stage, int light, float tint) {
      float nx = a[3];
      float nz = a[4];
      float tx = -nz;
      float tz = nx;
      float dirY = top ? -1.0F : -0.55F; // top curls hang over the rub, bottom ones droop below it
      int segs = 4;
      float[][] L = new float[segs + 1][];
      float[][] R = new float[segs + 1][];
      for (int k = 0; k <= segs; k++) {
         float s = k / (float)segs;
         float out = len * (top ? 0.55F : 0.75F) * s * s;
         float down = len * s;
         float w = wid * (1.0F - 0.45F * s) * 0.5F;
         float tw = (twist - 0.5F) * 0.9F * s;
         float wx = tx * (float)Math.cos(tw) + nx * (float)Math.sin(tw);
         float wz = tz * (float)Math.cos(tw) + nz * (float)Math.sin(tw);
         float cx = a[0] + nx * out + tx * (twist - 0.5F) * len * 0.3F * s;
         float cy = a[1] + dirY * down;
         float cz = a[2] + nz * out + tz * (twist - 0.5F) * len * 0.3F * s;
         L[k] = new float[]{cx - wx * w, cy, cz - wz * w};
         R[k] = new float[]{cx + wx * w, cy, cz + wz * w};
      }
      float inner = (stage <= 1 ? 576.0F : 640.0F) + variant * 32.0F;
      float bark = 512.0F + variant * 32.0F;
      float[] out = {nx, 0.25F, nz};
      float[] in = {-nx, -0.25F, -nz};
      for (int k = 0; k < segs; k++) {
         float v0 = k / (float)segs * 128.0F;
         float v1 = (k + 1) / (float)segs * 128.0F;
         mesh.quad(L[k], R[k], R[k + 1], L[k + 1], inner, v0, inner + 32, v0, inner + 32, v1, inner, v1, out, tint, light, light, light, light, null);
         mesh.quad(L[k], R[k], R[k + 1], L[k + 1], bark, v0, bark + 32, v0, bark + 32, v1, bark, v1, in, tint, light, light, light, light, null);
      }
   }

   // ------------------------------------------------------------------------------------------------ scrape

   private static void scrape(DeerSign.Mark mark, BlockState state, Level level, Mesh mesh, float days, float gap) {
      Direction toTree = state.getValue(DeerSign.FACING);
      BlockPos cell = mark.getBlockPos();
      int seed = mark.seed;
      float s = mark.size();
      // [deersign] the pawed patch grows as he paws; the licking branch is there from the start
      float made = mark.progress(level.getGameTime());
      float grow = made * made * (3.0F - 2.0F * made);
      float k = ("elk".equals(mark.species) || "moose".equals(mark.species) ? 1.2F : 0.86F + 0.3F * s) * (0.94F + 0.12F * rnd(seed, 1))
         * (0.35F + 0.65F * grow);
      float q = 1.5F * k;
      float cx = cell.getX() + 0.5F + toTree.getStepX() * 0.08F;
      float cz = cell.getZ() + 0.5F + toTree.getStepZ() * 0.08F;
      int st = days < 1.0F ? 0 : days < 2.5F ? 1 : days < 4.0F ? 2 : 3;
      float shade = 1.0F - 0.06F * rnd(seed, 2);
      float fx = toTree.getStepX();
      float fz = toTree.getStepZ();
      float rx = -fz; // across axis (texture u)
      float rz = fx;
      boolean mirror = rnd(seed, 3) < 0.5F;
      // split the decal square at block-column boundaries; each piece lies on its column's ground height
      float x0 = cx - q * 0.5F;
      float x1 = cx + q * 0.5F;
      float z0 = cz - q * 0.5F;
      float z1 = cz + q * 0.5F;
      float[] xs = cuts(x0, x1);
      float[] zs = cuts(z0, z1);
      float[] up = {0.0F, 1.0F, 0.0F};
      for (int i = 0; i < xs.length - 1 && made > 0.0F; i++) {
         for (int j = 0; j < zs.length - 1; j++) {
            int bx = (int)Math.floor((xs[i] + xs[i + 1]) * 0.5F);
            int bz = (int)Math.floor((zs[j] + zs[j + 1]) * 0.5F);
            float top = groundTop(level, bx, cell.getY(), bz);
            if (Float.isNaN(top)) {
               continue;
            }
            float y = top + gap * 0.8F + 0.004F;
            int light = lightAt(level, bx + 0.5, top + 0.2, bz + 0.5);
            float[] p00 = {xs[i], y, zs[j]};
            float[] p10 = {xs[i + 1], y, zs[j]};
            float[] p11 = {xs[i + 1], y, zs[j + 1]};
            float[] p01 = {xs[i], y, zs[j + 1]};
            float[][] pts = {p00, p10, p11, p01};
            float[] uv = new float[8];
            for (int c = 0; c < 4; c++) {
               float dx = pts[c][0] - cx;
               float dz = pts[c][2] - cz;
               float a = (dx * rx + dz * rz) / q;
               float b = -(dx * fx + dz * fz) / q; // toward the back
               float u = Math.clamp(0.5F + (mirror ? -a : a), 0.0F, 1.0F);
               float v = Math.clamp(0.5F + b, 0.0F, 1.0F);
               uv[c * 2] = st * 256.0F + u * 256.0F;
               uv[c * 2 + 1] = 256.0F + v * 256.0F;
            }
            mesh.quad(p00, p10, p11, p01, uv[0], uv[1], uv[2], uv[3], uv[4], uv[5], uv[6], uv[7], up, shade, light, light, light, light, null);
         }
      }
      licking(mark, level, mesh, cell, toTree, cx, cz, days, s);
   }

   /** Edges of the pieces between a and b: a, every whole block boundary inside, b. */
   private static float[] cuts(float a, float b) {
      int first = (int)Math.floor(a) + 1;
      int last = (int)Math.ceil(b) - 1;
      int n = Math.max(0, last - first + 1);
      float[] out = new float[n + 2];
      out[0] = a;
      for (int i = 0; i < n; i++) {
         out[i + 1] = first + i;
      }
      out[n + 1] = b;
      return out;
   }

   /** Top of the ground in a column near the scrape's height, NaN where it steps too far (a wall, a drop, water). */
   private static float groundTop(Level level, int x, int y, int z) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int yy = y + 1; yy >= y - 2; yy--) {
         m.set(x, yy, z);
         BlockState s = level.getBlockState(m);
         if (s.getBlock() instanceof DeerSign) {
            continue;
         }
         if (!level.getFluidState(m).isEmpty()) {
            return Float.NaN;
         }
         VoxelShape shape = s.is(Blocks.SNOW) ? s.getShape(level, m) : s.getCollisionShape(level, m);
         if (!shape.isEmpty()) {
            float top = yy + (float)shape.max(Direction.Axis.Y);
            return top > y + 0.55F || top < y - 0.55F ? Float.NaN : top;
         }
      }
      return Float.NaN;
   }

   // ------------------------------------------------------------------------------------------------ licking branch

   /** The low branch over the scrape: from the trunk (or the canopy) out over the scrape, chewed and broken at the tip. */
   private static void licking(DeerSign.Mark mark, Level level, Mesh mesh, BlockPos cell, Direction toTree, float cx, float cz, float days, float s) {
      int seed = mark.seed;
      boolean big = "elk".equals(mark.species) || "moose".equals(mark.species);
      // [deersign] the tip where the buck's nose works it (legacy scrapes: the old size rule), chewed once he has
      float[] tp = com.formaworks.frontierhunts.sign.work.SignShapes.lickTip(mark, cell, toTree);
      float tipX = tp[0];
      float tipY = tp[1];
      float tipZ = tp[2];
      boolean chewed = com.formaworks.frontierhunts.sign.work.SignShapes.chewed(mark, level.getGameTime());
      float[] root = null;
      BlockPos tree = mark.tree != null ? mark.tree : findTree(level, cell);
      if (tree != null && level.getBlockState(tree).is(BlockTags.LOGS)) {
         BlockPos base = trunkBase(level, tree);
         float rootY = base.getY() + (big ? 2.3F : 1.6F + 0.3F * s) + rnd(seed, 33) * 0.15F;
         BlockPos at = BlockPos.containing(base.getX(), rootY, base.getZ());
         while (at.getY() > base.getY() && !level.getBlockState(at).is(BlockTags.LOGS)) {
            at = at.below();
         }
         BlockState ls = level.getBlockState(at);
         float[] stem = SignTrunkProbe.stemAt(at, ls);
         float ax = stem != null ? stem[0] : at.getX() + 0.5F;
         float az = stem != null ? stem[2] : at.getZ() + 0.5F;
         float r = stem != null ? stem[3] * 0.85F : 0.45F;
         float dx = tipX - ax;
         float dz = tipZ - az;
         float d = (float)Math.sqrt(dx * dx + dz * dz);
         if (d > 0.05F && d < 4.5F) {
            root = new float[]{ax + dx / d * r, Math.min(rootY, at.getY() + 0.95F), az + dz / d * r};
         }
      }
      if (root == null) {
         // no trunk known: a twig hanging down from the canopy above
         for (int dy = 2; dy <= 6; dy++) {
            if (level.getBlockState(cell.above(dy)).is(BlockTags.LEAVES)) {
               root = new float[]{tipX + 0.15F, cell.getY() + dy, tipZ + 0.1F};
               break;
            }
         }
      }
      if (root == null) {
         return;
      }
      float[] tip = {tipX, tipY, tipZ};
      float len = dist(root, tip);
      if (len < 0.2F) {
         return;
      }
      // arched limb: rises a little off the trunk, then droops to the tip
      float[] mid = {(root[0] + tip[0]) * 0.5F, Math.max(root[1], tip[1]) + 0.12F + 0.08F * len, (root[2] + tip[2]) * 0.5F};
      int n = 7;
      float[][] pts = new float[n + 1][];
      float[] rad = new float[n + 1];
      for (int i = 0; i < n; i++) {
         float t = i / (n - 1.0F);
         float a = (1 - t) * (1 - t);
         float b = 2 * (1 - t) * t;
         float c = t * t;
         pts[i] = new float[]{a * root[0] + b * mid[0] + c * tip[0], a * root[1] + b * mid[1] + c * tip[1], a * root[2] + b * mid[2] + c * tip[2]};
         rad[i] = 0.042F + (0.011F - 0.042F) * (float)Math.pow(t, 0.8);
      }
      // the chewed end hangs down a little (an unworked tip just droops, leafy)
      pts[n] = new float[]{tip[0] + (tip[0] - mid[0]) * 0.04F, tip[1] - 0.09F, tip[2] + (tip[2] - mid[2]) * 0.04F};
      rad[n] = chewed ? 0.007F : 0.005F;
      int light = lightAt(level, tip[0], tip[1], tip[2]);
      for (int i = 0; i < n; i++) {
         tube(mesh, pts[i], pts[i + 1], rad[i], rad[i + 1], 5, light, 832.0F, 0.0F, 64.0F, 64.0F, 0.95F);
      }
      // broken, frayed tip: pale splinters (greying with age)
      float wx = days < 2.0F ? 896.0F : 928.0F;
      float[] end = pts[n];
      if (!chewed) {
         leafSpray(mesh, end, norm(sub(end, pts[n - 1])), 0.3F, (int)(rnd(seed, 45) * 2), light, rnd(seed, 46));
      }
      for (int k = 0; k < (chewed ? 3 : 0); k++) {
         float a = (float)(k * 2.1 + rnd(seed, 40 + k));
         float[] sp = {end[0] + (float)Math.cos(a) * 0.012F, end[1] - 0.025F - 0.015F * rnd(seed, 50 + k), end[2] + (float)Math.sin(a) * 0.012F};
         tube(mesh, end, sp, 0.004F, 0.0012F, 3, light, wx, 0.0F, 32.0F, 64.0F, 1.0F);
      }
      // two or three side twigs with a few leaves; none near the chewed tip
      int twigs = 4 + (int)(rnd(seed, 60) * 2);
      for (int k = 0; k < twigs; k++) {
         float t = 0.25F + 0.55F * (k + rnd(seed, 61 + k)) / twigs;
         int i = Math.min(n - 2, (int)(t * (n - 1)));
         float[] p = pts[i];
         float[] dir = norm(sub(pts[i + 1], pts[i]));
         float side = (k % 2 == 0 ? 1 : -1) * (0.7F + 0.4F * rnd(seed, 70 + k));
         float[] h = norm(new float[]{-dir[2], 0.0F, dir[0]});
         float[] tw = norm(new float[]{dir[0] + h[0] * side, dir[1] + 0.35F, dir[2] + h[2] * side});
         float tl = 0.14F + 0.12F * rnd(seed, 80 + k);
         float[] q = {p[0] + tw[0] * tl, p[1] + tw[1] * tl, p[2] + tw[2] * tl};
         tube(mesh, p, q, 0.008F, 0.004F, 3, light, 832.0F, 0.0F, 64.0F, 64.0F, 0.95F);
         if (days < 6.0F) {
            leafSpray(mesh, q, tw, 0.28F + 0.1F * rnd(seed, 90 + k), (int)(rnd(seed, 95 + k) * 2), light, rnd(seed, 99 + k));
         }
      }
   }

   /** Old scrapes (made before the tree was remembered): the nearest trunk, looked up once per scrape and kept. */
   private static final java.util.Map<Long, Long> TREES = new java.util.HashMap<>();

   private static BlockPos findTree(Level level, BlockPos cell) {
      Long known = TREES.get(cell.asLong());
      if (known != null) {
         return known == Long.MIN_VALUE ? null : BlockPos.of(known);
      }
      if (TREES.size() > 512) {
         TREES.clear();
      }
      BlockPos found = searchTree(level, cell);
      TREES.put(cell.asLong(), found == null ? Long.MIN_VALUE : found.asLong());
      return found;
   }

   private static BlockPos searchTree(Level level, BlockPos cell) {
      BlockPos best = null;
      double bestD = Double.MAX_VALUE;
      for (BlockPos p : BlockPos.betweenClosed(cell.offset(-3, -1, -3), cell.offset(3, 1, 3))) {
         if (level.getBlockState(p).is(BlockTags.LOGS) && !level.getBlockState(p.below()).is(BlockTags.LOGS)) {
            double d = p.distSqr(cell);
            if (d < bestD) {
               bestD = d;
               best = p.immutable();
            }
         }
      }
      return best;
   }

   /** A cut-out spray of leaves at a twig end: two crossed cards, both sides. */
   private static void leafSpray(Mesh mesh, float[] at, float[] dir, float size, int variant, int light, float turn) {
      float u0 = variant == 0 ? 832.0F : 896.0F;
      float[] side = norm(new float[]{-dir[2], 0.0F, dir[0]});
      for (int c = 0; c < 2; c++) {
         float a = turn * 1.5F + c * 1.5708F;
         float[] w = norm(new float[]{side[0] * (float)Math.cos(a) + dir[0] * (float)Math.sin(a) * 0.3F, 0.0F,
            side[2] * (float)Math.cos(a) + dir[2] * (float)Math.sin(a) * 0.3F});
         float hw = size * 0.5F;
         // the spray's stem end (bottom of the sprite) sits at the twig end, leaves fan forward and up
         float[] fwd = norm(new float[]{dir[0], dir[1] + 0.5F, dir[2]});
         float[] b0 = {at[0] - w[0] * hw, at[1] - w[1] * hw, at[2] - w[2] * hw};
         float[] b1 = {at[0] + w[0] * hw, at[1] + w[1] * hw, at[2] + w[2] * hw};
         float[] t1 = {b1[0] + fwd[0] * size, b1[1] + fwd[1] * size, b1[2] + fwd[2] * size};
         float[] t0 = {b0[0] + fwd[0] * size, b0[1] + fwd[1] * size, b0[2] + fwd[2] * size};
         float[] nrm = norm(cross(sub(b1, b0), sub(t0, b0)));
         float[] back = {-nrm[0], -nrm[1], -nrm[2]};
         mesh.quad(t0, t1, b1, b0, u0, 64, u0 + 64, 64, u0 + 64, 128, u0, 128, nrm, 0.95F, light, light, light, light, null);
         mesh.quad(t0, t1, b1, b0, u0, 64, u0 + 64, 64, u0 + 64, 128, u0, 128, back, 0.85F, light, light, light, light, null);
      }
   }

   /** A tapered tube between two points; texture region (u0, v0, w, h) wraps around it once. */
   private static void tube(Mesh mesh, float[] a, float[] b, float ra, float rb, int sides, int light, float u0, float v0, float uw, float vh, float tint) {
      float[] d = sub(b, a);
      float len = (float)Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
      if (len < 1.0E-4F) {
         return;
      }
      float[] t = {d[0] / len, d[1] / len, d[2] / len};
      float[] ref = Math.abs(t[1]) > 0.9F ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
      float[] e1 = norm(cross(t, ref));
      float[] e2 = cross(t, e1);
      float vLen = Math.min(vh, len / 0.2F * vh);
      for (int k = 0; k < sides; k++) {
         double a0 = Math.PI * 2 * k / sides;
         double a1 = Math.PI * 2 * (k + 1) / sides;
         float[] n0 = {(float)(e1[0] * Math.cos(a0) + e2[0] * Math.sin(a0)), (float)(e1[1] * Math.cos(a0) + e2[1] * Math.sin(a0)), (float)(e1[2] * Math.cos(a0) + e2[2] * Math.sin(a0))};
         float[] n1 = {(float)(e1[0] * Math.cos(a1) + e2[0] * Math.sin(a1)), (float)(e1[1] * Math.cos(a1) + e2[1] * Math.sin(a1)), (float)(e1[2] * Math.cos(a1) + e2[2] * Math.sin(a1))};
         float[] pa0 = {a[0] + n0[0] * ra, a[1] + n0[1] * ra, a[2] + n0[2] * ra};
         float[] pa1 = {a[0] + n1[0] * ra, a[1] + n1[1] * ra, a[2] + n1[2] * ra};
         float[] pb1 = {b[0] + n1[0] * rb, b[1] + n1[1] * rb, b[2] + n1[2] * rb};
         float[] pb0 = {b[0] + n0[0] * rb, b[1] + n0[1] * rb, b[2] + n0[2] * rb};
         float ua = u0 + uw * k / sides;
         float ub = u0 + uw * (k + 1) / sides;
         float[] nm = norm(new float[]{n0[0] + n1[0], n0[1] + n1[1], n0[2] + n1[2]});
         mesh.quad(pa0, pa1, pb1, pb0, ua, v0, ub, v0, ub, v0 + vLen, ua, v0 + vLen, nm, tint, light, light, light, light, new float[][]{n0, n1, n1, n0});
      }
   }

   // ------------------------------------------------------------------------------------------------ vector bits

   static float[] sub(float[] a, float[] b) {
      return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
   }

   static float[] cross(float[] a, float[] b) {
      return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   static float[] norm(float[] a) {
      float l = (float)Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
      return l < 1.0E-6F ? new float[]{0, 1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l};
   }

   static float dist(float[] a, float[] b) {
      float dx = a[0] - b[0];
      float dy = a[1] - b[1];
      float dz = a[2] - b[2];
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** Emits quads in world coordinates (relative to the block entity's origin), winding chosen to face the given normal. */
   static final class Mesh {
      final VertexConsumer vc;
      final PoseStack.Pose pose;
      final float ox;
      final float oy;
      final float oz;

      Mesh(VertexConsumer vc, PoseStack.Pose pose, BlockPos origin) {
         this.vc = vc;
         this.pose = pose;
         this.ox = origin.getX();
         this.oy = origin.getY();
         this.oz = origin.getZ();
      }

      void quad(float[] p0, float[] p1, float[] p2, float[] p3, float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
                float[] normal, float tint, int l0, int l1, int l2, int l3, float[][] vn) {
         float[] fn = cross(sub(p1, p0), sub(p3, p0));
         boolean flip = fn[0] * normal[0] + fn[1] * normal[1] + fn[2] * normal[2] < 0.0F;
         float[] n = norm(normal);
         float[] n0 = vn == null ? n : vn[0];
         float[] n1 = vn == null ? n : vn[1];
         float[] n2 = vn == null ? n : vn[2];
         float[] n3 = vn == null ? n : vn[3];
         int c = Math.clamp((int)(tint * 255.0F), 0, 255);
         if (!flip) {
            this.vertex(p0, u0, v0, n0, c, l0);
            this.vertex(p1, u1, v1, n1, c, l1);
            this.vertex(p2, u2, v2, n2, c, l2);
            this.vertex(p3, u3, v3, n3, c, l3);
         } else {
            this.vertex(p0, u0, v0, n0, c, l0);
            this.vertex(p3, u3, v3, n3, c, l3);
            this.vertex(p2, u2, v2, n2, c, l2);
            this.vertex(p1, u1, v1, n1, c, l1);
         }
      }

      private void vertex(float[] p, float u, float v, float[] n, int c, int light) {
         this.vc.addVertex(this.pose, p[0] - this.ox, p[1] - this.oy, p[2] - this.oz)
            .setColor(c, c, c, 255)
            .setUv(u / AW, v / AH)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(this.pose, n[0], n[1], n[2]);
      }
   }
}
