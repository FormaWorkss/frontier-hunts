package com.formaworks.frontierhunts.landscape.ride.grime.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the grime layers over the ATV, called from AtvRenderer right after the vehicle itself (same pose stack):
 * <ol>
 *   <li>mud: pre-baked cutout mask levels (1..5) in the vehicle's own UV layout, picked per zone (low = tyres and lower
 *   body, high = spray on the body), tinted from dark wet mud to the light dried crust by vertex colour;</li>
 *   <li>snow: 3 cutout levels on top.</li>
 * </ol>
 * Wetness is not a layer: it darkens the paint (and the mud) through the vertex colour of the vehicle itself
 * ({@link #bodyTint}).
 *
 * <p>[atv2] Shader packs: the old translucent wet-film layer (a near-black, 15-50 % alpha tile with bright beads) drew
 * as an opaque black shell with white specks under Iris, because packs render entity layers into the opaque g-buffer
 * where blending is off. It (and its custom UV-scaling VertexConsumer wrapper, which also disabled Sodium's fast
 * vertex path) is gone; everything here is entity-cutout geometry written straight into the buffer with every vertex
 * attribute (position, colour, uv, overlay, light, normal), exactly like vanilla armour layers.
 *
 * <p>Realistic mesh: geometry is re-emitted with the exact part transforms RealisticAtv uses, skipping triangles whose
 * mask is empty at the current level (per-triangle table baked with the textures), so a lightly muddy ATV costs only
 * a few hundred extra triangles.
 */
public final class AtvGrimeRender {
    private static final String DIR = "textures/entity/atv_grime/";
    private static final ResourceLocation[] BLOCKY_LOW = levels("blocky_mud_low_", AtvGrime.MUD_LEVELS);
    private static final ResourceLocation[] BLOCKY_HIGH = levels("blocky_mud_high_", AtvGrime.MUD_LEVELS);
    private static final ResourceLocation[] BLOCKY_SNOW = levels("blocky_snow_", AtvGrime.SNOW_LEVELS);
    private static final ResourceLocation[] REAL_MUD = levels("real_mud_", AtvGrime.MUD_LEVELS);
    private static final ResourceLocation[] REAL_SNOW = levels("real_snow_", AtvGrime.SNOW_LEVELS);
    private static final ResourceLocation[] WHEEL_MUD = levels("wheel_mud_", AtvGrime.MUD_LEVELS);
    private static final ResourceLocation[] WHEEL_SNOW = levels("wheel_snow_", AtvGrime.SNOW_LEVELS);
    static final ResourceLocation MESH = FrontierHunts.id("models/entity/atv_real.fhvm");
    static final ResourceLocation TABLE = FrontierHunts.id("models/entity/atv_real_grime.bin");

    private static float[][] parts;
    private static float[][] pivots;
    private static int[] textures;
    private static byte[][] table;
    private static boolean failed;
    private static final Quaternionf ROT = new Quaternionf();

    private AtvGrimeRender() {}

    private static ResourceLocation[] levels(String prefix, int n) {
        ResourceLocation[] out = new ResourceLocation[n];
        for (int i = 0; i < n; i++) out[i] = FrontierHunts.id(DIR + prefix + (i + 1) + ".png");
        return out;
    }

    /** Wet mud is dark; it dries to the light crust the textures are painted in. Standing water darkens it a bit more. */
    static int mudColor(float moist, float wet) {
        float m = Math.max(0f, Math.min(1f, moist));
        float w = 1f - 0.12f * Math.max(0f, Math.min(1f, wet));
        float r = (1f - 0.50f * m) * w, g = (1f - 0.55f * m) * w, b = (1f - 0.60f * m) * w;
        return 0xFF000000 | (int) (r * 255f) << 16 | (int) (g * 255f) << 8 | (int) (b * 255f);
    }

    /**
     * [atv2] Vertex colour for the vehicle's own paint: a wet ATV is a little darker and richer (water fills the
     * micro-roughness of paint and plastic), never black. The plain entity colour attribute, which every shader pack
     * multiplies into albedo, so it looks the same with shaders on and off.
     */
    public static int bodyTint(Atv atv) {
        float w = Math.max(0f, Math.min(1f, atv.grime.shownWet));
        if (w < 0.01f) return -1;
        float r = 1f - 0.24f * w, g = 1f - 0.22f * w, b = 1f - 0.17f * w;
        return 0xFF000000 | (int) (r * 255f) << 16 | (int) (g * 255f) << 8 | (int) (b * 255f);
    }

    // ================================================================== blocky model
    /** Blocky path: the posed AtvModel redrawn per layer (about 360 quads each). */
    public static void blocky(Atv atv, EntityModel<Atv> model, PoseStack ps, MultiBufferSource buffers, int light) {
        AtvGrime g = atv.grime;
        int lo = AtvGrime.mudLevel(g.mudLow), hi = AtvGrime.mudLevel(g.mudHigh), sn = AtvGrime.snowLevel(g.snow);
        if (lo == 0 && hi == 0 && sn == 0) return;
        int tint = mudColor(g.shownMoist, g.shownWet);
        if (lo > 0) {
            model.renderToBuffer(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(BLOCKY_LOW[lo - 1], false, false, 1)), light, OverlayTexture.NO_OVERLAY, tint);
        }
        if (hi > 0) {
            model.renderToBuffer(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(BLOCKY_HIGH[hi - 1], false, false, 1)), light, OverlayTexture.NO_OVERLAY, tint);
        }
        if (sn > 0) {
            model.renderToBuffer(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(BLOCKY_SNOW[sn - 1], false, false, 2)), light, OverlayTexture.NO_OVERLAY, -1);
        }
    }

    // ================================================================== realistic mesh
    /** Realistic path: same transforms as RealisticAtv.render (steer = steerShown, spin in radians). */
    public static void realistic(Atv atv, PoseStack ps, MultiBufferSource buffers, int light, float steer, float spin) {
        AtvGrime g = atv.grime;
        int lo = AtvGrime.mudLevel(g.mudLow), hi = AtvGrime.mudLevel(g.mudHigh), sn = AtvGrime.snowLevel(g.snow);
        if (lo == 0 && hi == 0 && sn == 0) return;
        if (!load()) return;
        int tint = mudColor(g.shownMoist, g.shownWet);
        int tr = tint >> 16 & 255, tg = tint >> 8 & 255, tb = tint & 255;
        // mud on the body: one pass per distinct level (low and high triangles may share it)
        if (lo > 0) emitBody(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(REAL_MUD[lo - 1], true, true, 1)), light, steer, spin, lo, hi == lo ? 3 : 1, true, tr, tg, tb, 255);
        if (hi > 0 && hi != lo) emitBody(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(REAL_MUD[hi - 1], true, true, 1)), light, steer, spin, hi, 2, true, tr, tg, tb, 255);
        if (lo > 0) emitWheels(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(WHEEL_MUD[lo - 1], true, true, 1)), light, steer, spin, lo, true, tr, tg, tb, 255);
        if (sn > 0) {
            emitBody(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(REAL_SNOW[sn - 1], true, true, 2)), light, steer, spin, sn, 3, false, 255, 255, 255, 255);
            emitWheels(ps, buffers.getBuffer(AtvGrimeRenderTypes.cutout(WHEEL_SNOW[sn - 1], true, true, 2)), light, steer, spin, sn, false, 255, 255, 255, 255);
        }
    }

    /** zones: 1 = low triangles, 2 = high triangles, 3 = both. Culls triangles whose mask is empty at this level. */
    private static void emitBody(PoseStack ps, VertexConsumer vc, int light, float steer, float spin, int level, int zones, boolean mud,
                                 int r, int g, int b, int a) {
        for (int p = 0; p < 2 && p < parts.length; p++) {
            if (textures[p] != 0) continue;
            begin(ps, p, steer, spin);
            emitPart(ps, vc, p, light, level, zones, mud, r, g, b, a);
            ps.popPose();
        }
    }

    private static void emitWheels(PoseStack ps, VertexConsumer vc, int light, float steer, float spin, int level, boolean mud,
                                   int r, int g, int b, int a) {
        for (int p = 0; p < parts.length; p++) {
            if (textures[p] != 1) continue;
            begin(ps, p, steer, spin);
            emitPart(ps, vc, p, light, level, 3, mud, r, g, b, a);
            ps.popPose();
        }
    }

    /** Mirrors RealisticAtv.render's per-part transform. */
    private static void begin(PoseStack ps, int p, float steer, float spin) {
        ps.pushPose();
        if (p >= 1) {
            float s = steer * 0.55f;
            float yaw = p == 1 ? s * 0.8f : (p < 4 ? s : 0f);
            float roll = p >= 2 ? spin : 0f;
            float[] pv = pivots[p];
            ps.translate(pv[0] / 16f, pv[1] / 16f, pv[2] / 16f);
            ps.mulPose(ROT.rotationZYX(0f, yaw, roll));
            ps.translate(-pv[0] / 16f, -pv[1] / 16f, -pv[2] / 16f);
        }
    }

    private static void emitPart(PoseStack ps, VertexConsumer vc, int p, int light, int level, int zones, boolean mud,
                                 int r, int g, int b, int a) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        float[] v = parts[p];
        byte[] tab = table != null && p < table.length ? table[p] : null;
        int tris = v.length / 24;
        for (int t = 0; t < tris; t++) {
            if (level > 0 && tab != null) {
                int e = tab[t];
                int first = mud ? e & 7 : (e >> 3) & 7;
                if (first == 0 || first > level) continue;
                if (mud && zones != 3) {
                    int zone = (e >> 7) & 1; // 0 low, 1 high
                    if (zones == 1 && zone != 0 || zones == 2 && zone != 1) continue;
                }
            }
            int o = t * 24;
            for (int k = 0; k < 3; k++, o += 8) {
                vc.addVertex(m, v[o] / 16f, v[o + 1] / 16f, v[o + 2] / 16f)
                        .setColor(r, g, b, a)
                        .setUv(v[o + 3], v[o + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, v[o + 5], v[o + 6], v[o + 7]);
            }
        }
    }

    // ================================================================== loading (mesh copy + culling table)
    private static boolean load() {
        if (parts != null) return true;
        if (failed) return false;
        try {
            Resource res = Minecraft.getInstance().getResourceManager().getResource(MESH).orElse(null);
            if (res == null) {
                failed = true;
                return false;
            }
            try (InputStream in = res.open(); DataInputStream d = new DataInputStream(new BufferedInputStream(in))) {
                d.readFully(new byte[4]);
                int version = d.readInt();
                int n = d.readInt();
                float[][] ps = new float[n][];
                float[][] pv = new float[n][3];
                int[] tx = new int[n];
                for (int i = 0; i < n; i++) {
                    for (int k = 0; k < 3; k++) pv[i][k] = d.readFloat();
                    if (version >= 2) tx[i] = d.readInt();
                    int tris = d.readInt();
                    float[] a = new float[tris * 24];
                    for (int k = 0; k < a.length; k++) a[k] = d.readFloat();
                    ps[i] = a;
                }
                pivots = pv;
                textures = tx;
                table = loadTable(ps);
                parts = ps;
            }
            return true;
        } catch (Exception e) {
            failed = true;
            return false;
        }
    }

    /** Per-triangle zone / first-visible-level bytes; null (draw everything) if missing or not matching the mesh. */
    private static byte[][] loadTable(float[][] ps) {
        try {
            Resource res = Minecraft.getInstance().getResourceManager().getResource(TABLE).orElse(null);
            if (res == null) return null;
            try (InputStream in = res.open(); DataInputStream d = new DataInputStream(new BufferedInputStream(in))) {
                d.readFully(new byte[4]);
                d.readInt();
                int n = d.readInt();
                d.readInt();
                if (n != ps.length) return null;
                byte[][] out = new byte[n][];
                for (int i = 0; i < n; i++) {
                    int tris = d.readInt();
                    if (tris * 24 != ps[i].length) return null;
                    out[i] = new byte[tris];
                    d.readFully(out[i]);
                }
                return out;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
