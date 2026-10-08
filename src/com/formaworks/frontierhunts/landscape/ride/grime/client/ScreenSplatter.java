package com.formaworks.frontierhunts.landscape.ride.grime.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrimeConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * Driver screen splatter (first person on an ATV only), drawn under the whole HUD.
 *
 * <p>A fixed pool of splats (struct-of-arrays, no allocation per frame): mud splats and drips hit the view when you
 * drive fast through mud, start dark and wet, slide a little, then dry to a light crust and fade over 10-20 s; snow
 * powder smears and clumps fade in a few seconds (some melt into a droplet). Mud and snow draw in one batch from a 4x4
 * atlas.
 *
 * <p>[atv2] Water (Forza style): driving through water throws fine spray specks, droplet clusters and runs onto the
 * view, a bow wave at speed or a plunge sends translucent sheets of water sliding down from the top corners; at speed
 * the drops are pushed out toward the edges by the airflow. It never blocks the view: water is spawned away from the
 * centre, drawn fainter the nearer it is to the centre, lives only 1-4 s, and the total coverage is capped. Water on
 * the view rinses mud and snow splatter off. Water draws as a second batch from its own atlas ({@code water.png}).
 */
public final class ScreenSplatter {
    static final ResourceLocation ATLAS = FrontierHunts.id("textures/gui/atv_grime/splatter.png");
    static final ResourceLocation WATER = FrontierHunts.id("textures/gui/atv_grime/water.png"); // [atv2]
    private static final int MAX = 64;
    static final byte NONE = 0, MUD = 1, DRIP = 2, SNOW = 3, BEAD = 4, RUN = 5, SHEET = 6, SPECK = 7; // [atv2] + SHEET, SPECK
    /** [atv2] Cap on water coverage: sum of size^2 x alpha over live water splats (~ fraction of the view). */
    private static final float WATER_BUDGET = 0.16f;

    private static final byte[] kind = new byte[MAX];
    private static final byte[] cell = new byte[MAX];
    private static final float[] x = new float[MAX], y = new float[MAX], py = new float[MAX], size = new float[MAX], rot = new float[MAX];
    private static final float[] vy = new float[MAX], a0 = new float[MAX];
    private static final int[] age = new int[MAX], life = new int[MAX];
    private static int live;
    private static float accMud, accSnow, accWater, wash;
    private static float accSpeck, accSheet, airflow; // [atv2]
    private static final RandomSource RND = RandomSource.create();

    private ScreenSplatter() {}

    static void clear() {
        for (int i = 0; i < MAX; i++) kind[i] = NONE;
        live = 0;
        accMud = accSnow = accWater = wash = 0f;
        accSpeck = accSheet = airflow = 0f;
    }

    private static boolean water(byte k) {
        return k >= BEAD;
    }

    private static float smooth(float e0, float e1, float v) {
        float t = Mth.clamp((v - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int cap() {
        try {
            HuntConfig.Quality q = HuntConfig.QUALITY.get();
            return q == HuntConfig.Quality.PERFORMANCE ? 22 : q == HuntConfig.Quality.CINEMATIC ? MAX : 40;
        } catch (Throwable t) {
            return 40;
        }
    }

    /** Driver tick: turn what the wheels are churning into hits on the view. */
    static void feed(AtvGrime.Sample s, float sp, float lastDepth, float signedSpeed) {
        if (!AtvGrimeConfig.screenSplatter()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return;
        // the front wheels throw at the rider; reversing, the rear ones do
        boolean fwd = signedSpeed >= 0f;
        float mudF = fwd ? Math.max(s.mud[0], s.mud[1]) : Math.max(s.mud[2], s.mud[3]);
        float snowF = fwd ? Math.max(s.snow[0], s.snow[1]) : Math.max(s.snow[2], s.snow[3]);
        boolean dry = s.depth < 0.3f;
        float mudI = dry ? mudF * smooth(0.3f, 0.95f, sp) : 0f;
        accMud += mudI * 0.2f;
        while (accMud >= 1f) {
            accMud -= 1f;
            if (RND.nextFloat() < 0.3f) spawn(MUD, (byte) RND.nextInt(4), 0.13f + RND.nextFloat() * 0.15f, 220 + RND.nextInt(160));
            else if (RND.nextFloat() < 0.5f) spawn(DRIP, (byte) (4 + RND.nextInt(4)), 0.07f + RND.nextFloat() * 0.07f, 200 + RND.nextInt(140));
            else spawn(MUD, (byte) RND.nextInt(4), 0.035f + RND.nextFloat() * 0.05f, 160 + RND.nextInt(120));
        }
        float snowI = dry ? snowF * smooth(0.25f, 0.85f, sp) : 0f;
        accSnow += snowI * 0.28f;
        while (accSnow >= 1f) {
            accSnow -= 1f;
            spawn(SNOW, (byte) (8 + RND.nextInt(4)), 0.08f + RND.nextFloat() * 0.16f, 60 + RND.nextInt(70));
        }
        // ---------------------------------------------------------------- [atv2] water
        float d = s.depth;
        float wheels = smooth(0.04f, 0.35f, d) * (1f - 0.5f * smooth(1.7f, 2.3f, d));
        float spd = smooth(0.08f, 0.9f, sp);
        airflow = fwd ? spd : 0f;
        if (lastDepth < 0.1f && d >= 0.15f && sp > 0.25f || d - lastDepth > 0.3f && sp > 0.35f) {
            // ploughing in: a sheet or two from the top corners, a burst of drops, and the mud starts to rinse off
            float e = smooth(0.25f, 1.0f, sp);
            int sheets = e > 0.55f ? 2 : 1;
            for (int k = 0; k < sheets; k++) spawnSheet();
            for (int k = 0; k < 3 + (int) (5 * e); k++) spawnWater(BEAD, (byte) RND.nextInt(4), 0.07f + RND.nextFloat() * 0.08f, 40 + RND.nextInt(40), 0.85f);
            for (int k = 0; k < 2 + (int) (3 * e); k++) spawnWater(SPECK, (byte) (12 + RND.nextInt(4)), 0.14f + RND.nextFloat() * 0.12f, 16 + RND.nextInt(14), 0.75f);
            for (int k = 0; k < 2; k++) spawnWater(RUN, (byte) (4 + RND.nextInt(4)), 0.12f + RND.nextFloat() * 0.08f, 30 + RND.nextInt(30), 0.85f);
            wash = 1f;
        }
        if (wheels > 0.01f && sp > 0.06f) {
            // spray thrown up by the front tyres: fine specks at speed, droplet clusters, the odd run
            accSpeck += wheels * spd * spd * 0.45f;
            while (accSpeck >= 1f) {
                accSpeck -= 1f;
                spawnWater(SPECK, (byte) (12 + RND.nextInt(4)), 0.1f + RND.nextFloat() * 0.12f, 14 + RND.nextInt(16), 0.7f);
            }
            accWater += wheels * spd * 0.3f;
            while (accWater >= 1f) {
                accWater -= 1f;
                if (RND.nextFloat() < 0.72f) spawnWater(BEAD, (byte) RND.nextInt(4), 0.05f + RND.nextFloat() * 0.08f, 40 + RND.nextInt(40), 0.85f);
                else spawnWater(RUN, (byte) (4 + RND.nextInt(4)), 0.1f + RND.nextFloat() * 0.08f, 30 + RND.nextInt(30), 0.85f);
            }
            // the bow wave breaking over the front at speed sends a sheet up now and then
            float bow = smooth(0.2f, 0.7f, d) * smooth(0.45f, 1.0f, sp) * (fwd ? 1f : 0.4f);
            accSheet += bow * 0.035f;
            if (accSheet >= 1f) {
                accSheet -= 1f;
                spawnSheet();
            }
        }
        wash = Math.max(wash, wheels * smooth(0.05f, 0.4f, sp));
    }

    /**
     * [1.2.5] The snowmobile driver: powder off the skis now and then smears on the view (gentler than the ATV's tyres:
     * small, few, gone in a few seconds), more in deep snow and at speed.
     */
    public static void feedSnowmobile(float snowF, float sp) {
        if (!AtvGrimeConfig.screenSplatter()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return;
        airflow = smooth(0.08f, 0.9f, sp);
        accSnow += snowF * smooth(0.3f, 1.0f, sp) * 0.1f;
        while (accSnow >= 1f) {
            accSnow -= 1f;
            spawn(SNOW, (byte) (8 + RND.nextInt(4)), 0.05f + RND.nextFloat() * 0.09f, 45 + RND.nextInt(60));
        }
    }

    /** [1.2.5] driving something that throws splatter at the view: an ATV or a snowmobile */
    private static boolean driving(LocalPlayer p) {
        return p != null && (p.getVehicle() instanceof Atv a && a.getControllingPassenger() == p
            || p.getVehicle() instanceof com.formaworks.frontierhunts.sled.SnowmobileEntity s && s.getControllingPassenger() == p);
    }

    private static void spawnSheet() {
        spawnWater(SHEET, (byte) (8 + RND.nextInt(4)), 0.36f + RND.nextFloat() * 0.18f, 16 + RND.nextInt(12), 0.55f);
    }

    /** Current water coverage on the view. */
    private static float waterCover() {
        float c = 0f;
        for (int i = 0; i < MAX; i++) {
            if (water(kind[i])) c += size[i] * size[i] * a0[i];
        }
        return c;
    }

    /**
     * Water lands away from the centre of the view (rejection sampling on the distance from the centre), and only while
     * the total coverage stays under {@link #WATER_BUDGET}. Sheets start at the top, over the left or right third.
     */
    private static void spawnWater(byte k, byte c, float sz, int lifeTicks, float alpha) {
        if (waterCover() + sz * sz * alpha > WATER_BUDGET * (k == SPECK ? 1.5f : 1f)) return;
        int slot = slot();
        if (slot < 0) return;
        float sx, sy;
        if (k == SHEET) {
            sx = RND.nextBoolean() ? 0.08f + RND.nextFloat() * 0.24f : 0.68f + RND.nextFloat() * 0.24f;
            sy = -0.05f + RND.nextFloat() * 0.18f;
        } else {
            int tries = 0;
            do {
                sx = 0.03f + RND.nextFloat() * 0.94f;
                sy = 0.04f + RND.nextFloat() * 0.9f;
            } while (tries++ < 6 && RND.nextFloat() > smooth(0.22f, 0.7f, centre(sx, sy)));
        }
        if (kind[slot] == NONE) live++;
        kind[slot] = k;
        cell[slot] = (byte) (c & 15);
        x[slot] = sx;
        y[slot] = sy;
        py[slot] = sy;
        size[slot] = sz;
        rot[slot] = (RND.nextFloat() - 0.5f) * (k == SHEET || k == RUN ? 0.2f : 0.5f); // keep the glints top-left
        vy[slot] = k == RUN ? 0.002f + RND.nextFloat() * 0.002f : k == SHEET ? 0.018f + RND.nextFloat() * 0.012f : 0f;
        a0[slot] = alpha * (0.85f + 0.15f * RND.nextFloat());
        age[slot] = 0;
        life[slot] = lifeTicks;
    }

    /** 0 at the centre of the view, ~1 at the edges. */
    private static float centre(float px, float py) {
        float dx = (px - 0.5f) / 0.5f, dy = (py - 0.5f) / 0.5f;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static int slot() {
        int cap = cap();
        int slot = -1, oldest = -1, oldestLeft = Integer.MAX_VALUE;
        for (int i = 0; i < cap; i++) {
            if (kind[i] == NONE) return i;
            int left = life[i] - age[i];
            if (left < oldestLeft) {
                oldestLeft = left;
                oldest = i;
            }
        }
        return oldest;
    }

    private static void spawn(byte k, byte c, float sz, int lifeTicks) {
        int cap = cap();
        int slot = -1, oldest = -1, oldestLeft = Integer.MAX_VALUE;
        for (int i = 0; i < cap; i++) {
            if (kind[i] == NONE) {
                slot = i;
                break;
            }
            int left = life[i] - age[i];
            if (left < oldestLeft) {
                oldestLeft = left;
                oldest = i;
            }
        }
        if (slot < 0) slot = oldest;
        if (slot < 0) return;
        if (kind[slot] == NONE) live++;
        kind[slot] = k;
        cell[slot] = (byte) (c & 15);
        // mud and snow hit low and toward the edges more than the centre of the view
        float fx = RND.nextFloat();
        x[slot] = k == BEAD || k == RUN ? 0.04f + 0.92f * fx : 0.5f + (fx - 0.5f) * 1.05f;
        float fy = (float) Math.pow(RND.nextFloat(), k == SNOW ? 1.0 : 0.75);
        y[slot] = k == RUN ? 0.02f + fy * 0.6f : 0.12f + fy * 0.85f;
        py[slot] = y[slot];
        size[slot] = sz;
        rot[slot] = k == DRIP || k == RUN ? (RND.nextFloat() - 0.5f) * 0.25f : RND.nextFloat() * 6.2832f;
        vy[slot] = k == RUN ? 0.002f + RND.nextFloat() * 0.002f : k == DRIP ? 0.0009f : 0f;
        a0[slot] = k == SNOW ? 0.8f + RND.nextFloat() * 0.15f : k == BEAD || k == RUN ? 0.85f : 0.9f + RND.nextFloat() * 0.1f;
        age[slot] = 0;
        life[slot] = lifeTicks;
    }

    static void tick() {
        if (live == 0) {
            wash = 0f;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean driving = driving(p) && mc.options.getCameraType().isFirstPerson();
        int speedUp = driving ? 1 : 4;
        float air = driving ? airflow : 0f;
        int n = 0;
        for (int i = 0; i < MAX; i++) {
            byte k = kind[i];
            if (k == NONE) continue;
            py[i] = y[i];
            int step = speedUp;
            if ((k == MUD || k == DRIP) && wash > 0.05f) step += (int) (wash * 6f); // water rinses the mud away
            if (k == SNOW && wash > 0.05f) step += 3;
            age[i] += step;
            float t = (float) age[i] / life[i];
            if (k == DRIP) {
                // wet mud sags for the first couple of seconds, then sets
                y[i] += vy[i] * (1f - smooth(0.05f, 0.25f, t));
            } else if (k == RUN) {
                vy[i] = Math.min(vy[i] * 1.03f, 0.012f);
                y[i] += vy[i];
            } else if (k == SHEET) {
                // [atv2] a sheet of water slides off the view fast, accelerating
                vy[i] = Math.min(vy[i] * 1.06f + 0.0008f, 0.05f);
                y[i] += vy[i];
                x[i] += (x[i] - 0.5f) * 0.004f * air;
            } else if (k == BEAD || k == SPECK) {
                // [atv2] at speed the airflow pushes drops out toward the edges of the view
                if (air > 0.05f) {
                    x[i] += (x[i] - 0.5f) * 0.012f * air;
                    y[i] += (y[i] - 0.45f) * 0.008f * air + 0.0006f * air;
                }
                if (k == BEAD && t > 0.35f && RND.nextFloat() < 0.02f) {
                    // a bead gets heavy and starts to run
                    kind[i] = RUN;
                    cell[i] = (byte) (4 + RND.nextInt(4));
                    vy[i] = 0.002f;
                }
            }
            if (age[i] >= life[i] || y[i] > 1.3f || x[i] < -0.2f || x[i] > 1.2f) {
                if (k == SNOW && driving && RND.nextFloat() < 0.3f) {
                    // melted to a droplet
                    float sx = x[i], sy = y[i];
                    kind[i] = BEAD;
                    cell[i] = (byte) RND.nextInt(4);
                    size[i] *= 0.45f;
                    age[i] = 0;
                    life[i] = 40 + RND.nextInt(40);
                    a0[i] = 0.7f;
                    rot[i] = (RND.nextFloat() - 0.5f) * 0.5f;
                    x[i] = sx;
                    y[i] = py[i] = sy;
                    vy[i] = 0f;
                } else {
                    kind[i] = NONE;
                    continue;
                }
            }
            n++;
        }
        live = n;
        wash *= 0.9f;
    }

    // ================================================================== render (two batches: mud/snow, then water)
    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (live == 0 || !AtvGrimeConfig.screenSplatter()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) return;
        if (!driving(p)) return;
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        batch(g, pt, false);
        batch(g, pt, true);
    }

    private static void batch(GuiGraphics g, float pt, boolean waterPass) {
        int w = g.guiWidth(), h = g.guiHeight();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (int i = 0; i < MAX; i++) {
            byte k = kind[i];
            if (k == NONE || water(k) != waterPass) continue;
            float t = Math.min(1f, (age[i] + pt) / life[i]);
            float in = Math.min(1f, (age[i] + pt + 1f) / 2.5f); // impact: pops in over a couple of frames
            float r, gr, b, al;
            float scale = 0.7f + 0.3f * in;
            float cy0 = Mth.lerp(pt, py[i], y[i]);
            if (k == MUD || k == DRIP) {
                float dry = smooth(0.12f, 0.7f, t);
                r = Mth.lerp(dry, 0.42f, 0.98f);
                gr = Mth.lerp(dry, 0.36f, 0.96f);
                b = Mth.lerp(dry, 0.30f, 0.93f);
                al = a0[i] * in * (1f - smooth(0.72f, 1f, t)) * (1f - 0.22f * dry);
            } else if (k == SNOW) {
                r = 0.97f;
                gr = 0.98f;
                b = 1f;
                al = a0[i] * in * (1f - smooth(0.45f, 1f, t));
                scale *= 1f - 0.15f * t;
            } else {
                // [atv2] water: quick fade in, fade out over the last 40 %, and always fainter toward the centre
                r = gr = b = 1f;
                float fadeIn = Math.min(1f, (age[i] + pt + 1f) / (k == SHEET ? 3f : 1.5f));
                al = a0[i] * fadeIn * (1f - smooth(0.6f, 1f, t)) * (0.35f + 0.65f * smooth(0.18f, 0.55f, centre(x[i], cy0)));
                if (k == SHEET) scale = 0.85f + 0.15f * in + 0.1f * t; // spreads as it slides
            }
            if (al <= 0.01f) continue;
            float cx = x[i] * w, cy = cy0 * h;
            float half = size[i] * h * scale * 0.5f;
            float c = Mth.cos(rot[i]) * half, s = Mth.sin(rot[i]) * half;
            int ci = cell[i];
            float u0 = (ci & 3) * 0.25f, v0 = (ci >> 2) * 0.25f, u1 = u0 + 0.25f, v1 = v0 + 0.25f;
            // corners (-1,-1) (-1,1) (1,1) (1,-1) rotated
            bb.addVertex(m, cx + (-c + s), cy + (-s - c), 0f).setUv(u0, v0).setColor(r, gr, b, al);
            bb.addVertex(m, cx + (-c - s), cy + (-s + c), 0f).setUv(u0, v1).setColor(r, gr, b, al);
            bb.addVertex(m, cx + (c - s), cy + (s + c), 0f).setUv(u1, v1).setColor(r, gr, b, al);
            bb.addVertex(m, cx + (c + s), cy + (s - c), 0f).setUv(u1, v0).setColor(r, gr, b, al);
            quads++;
        }
        MeshData mesh = bb.build();
        if (mesh == null) return;
        if (quads == 0) {
            mesh.close();
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, waterPass ? WATER : ATLAS);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent event) {
            event.registerBelowAll(FrontierHunts.id("atv_splatter"), ScreenSplatter::render);
        }
    }
}
