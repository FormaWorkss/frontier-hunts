package com.formaworks.frontierhunts.weather.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.ShaderState;
import com.formaworks.frontierhunts.weather.WeatherConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * Shader-independent storm layer, drawn under the whole HUD (and still drawn with F1, it is part of the view).
 *
 * <ol>
 *   <li><b>Whiteout / haze profile.</b> Shader packs usually ignore Minecraft fog, so the screen is washed with the
 *   storm colour by a per-row depth estimate: rows that look at the ground close to the camera stay clear, rows at
 *   and above the horizon (distant terrain, sky) wash out with {@code 1 - exp(-3 d / visibility)}. With shaders or
 *   Distant Horizons the layer is strong (it has to hide the distance on its own); without them vanilla fog does
 *   the depth work and this is only a light veil.</li>
 *   <li><b>Gust sheets.</b> Two tileable streaky noise layers scrolling with the wind relative to the camera and
 *   locked to the view direction, so turning your head moves them like part of the world.</li>
 *   <li><b>Frost</b> creeping in from the screen edges during a blizzard.</li>
 * </ol>
 * Depth-correct haze on actual geometry comes from the veil particles; this layer complements them.
 */
public final class WeatherOverlay {
    static final ResourceLocation SHEET = FrontierHunts.id("textures/gui/weather/gust_sheet.png");
    static final ResourceLocation FROST = FrontierHunts.id("textures/gui/weather/frost_vignette.png");

    /** Whiteout cap per kind with shaders / Distant Horizons (they rely on this layer) and without (vanilla fog does the work). */
    // [blizzard2] blizzard: strong cap 0.9 -> 0.92 (+0.05 in a whiteout gust), vanilla 0.2 -> 0.24 (+0.22 in a gust),
    // gust sheets 0.26 -> 0.36 opacity and 1.0 -> 1.45 scroll speed
    // [shelter] a fair-weather wind storm (no snow, no rain, no dust in the air) gets no gust sheets and only a faint
    // horizon haze: the screen layer belongs to real storms (was cap 0.12 / 0.03, sheet 0.04)
    private static final float[] CAP_STRONG = {0f, 0.92f, 0.5f, 0.45f, 0.86f, 0.8f, 0.72f, 0.05f};
    private static final float[] CAP_VANILLA = {0f, 0.24f, 0.06f, 0.06f, 0.2f, 0.1f, 0.1f, 0.015f};
    /** Gust sheet opacity per kind and scroll speed factor. */
    private static final float[] SHEET_ALPHA = {0f, 0.36f, 0.05f, 0.06f, 0.28f, 0.08f, 0.08f, 0f};
    private static final float[] SHEET_SPEED = {0f, 1.45f, 0.6f, 0.8f, 0.9f, 0.12f, 0.12f, 0.8f};
    private static final int ROWS = 20;

    private static float scrollA, scrollB, fallA, fallB, prevScrollA, prevScrollB, prevFallA, prevFallB;
    private static boolean distantHorizons;
    private static boolean checkedMods;

    private WeatherOverlay() {}

    /** Per-tick scroll integration (called by {@link WeatherClient}). */
    static void tick(boolean active) {
        prevScrollA = scrollA;
        prevScrollB = scrollB;
        prevFallA = fallA;
        prevFallB = fallB;
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        // lateral wind across the view: + = moving to the right on screen
        float lx = cam.getLeftVector().x(), lz = cam.getLeftVector().z();
        float lateral = -(WeatherClient.windUnitX * lx + WeatherClient.windUnitZ * lz);
        float speed = 0f, sum = 0f;
        for (int k = 1; k < WeatherClient.K; k++) {
            float s = WeatherClient.sev[k];
            speed += s * SHEET_SPEED[k];
            sum += s;
        }
        speed = sum > 0f ? speed / sum : 0f;
        float v = (0.004f + 0.0022f * WeatherClient.windPresent) * (0.55f + 0.7f * WeatherClient.gust) * speed * (1f + 0.5f * WeatherClient.whiteout);
        scrollA = wrap(scrollA - lateral * v);
        scrollB = wrap(scrollB - lateral * v * 1.7f);
        fallA = wrap(fallA - v * 0.35f);
        fallB = wrap(fallB - v * 0.55f);
        if (Math.abs(scrollA - prevScrollA) > 0.5f) prevScrollA = scrollA;
        if (Math.abs(scrollB - prevScrollB) > 0.5f) prevScrollB = scrollB;
        if (Math.abs(fallA - prevFallA) > 0.5f) prevFallA = fallA;
        if (Math.abs(fallB - prevFallB) > 0.5f) prevFallB = fallB;
    }

    private static float wrap(float v) {
        v %= 64f;
        return v;
    }

    static boolean strong() {
        if (!checkedMods) {
            checkedMods = true;
            try { distantHorizons = ModList.get().isLoaded("distanthorizons"); } catch (Throwable t) { distantHorizons = false; }
        }
        return ShaderState.on || distantHorizons;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!WeatherConfig.overlay() || !WeatherClient.active && WeatherClient.frost <= 0.004f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        if (!cam.isInitialized() || cam.getFluidInCamera() != FogType.NONE) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        boolean strong = strong();
        // [shelter] tents, blinds, cabins and caves fade the whole layer (whiteout, dim, sheets, frost) over ~1-2 s
        float open = com.formaworks.frontierhunts.shelter.client.ShelterClient.open(pt);
        float gate = WeatherClient.underground * open;

        // ---------------------------------------------------------------- 1. whiteout profile
        float cap = 0f, vis = 9999f, sheet = 0f, dim = 0f;
        float wo = WeatherClient.lerp(WeatherClient.prevWhiteout, WeatherClient.whiteout, pt) * gate;
        for (int k = 1; k < WeatherClient.K; k++) {
            float e = WeatherClient.lerp(WeatherClient.prevSev[k], WeatherClient.sev[k], pt) * gate;
            if (k == WeatherClient.BLIZZARD) e = WeatherClient.drive(e); // [blizzard2] 0.87+ = full strength
            if (e <= 0.002f) continue;
            float f = (float) Math.pow(Math.min(1f, e), 0.8);
            float kc = strong ? CAP_STRONG[k] : CAP_VANILLA[k];
            float floor = WeatherClient.floorFor(k);
            if (k == WeatherClient.BLIZZARD) {
                // [blizzard2] same 1.1x-floor fade distance as the fog (rowAlpha adds 1.3x), halved in a whiteout gust
                kc += (strong ? 0.05f : 0.22f) * wo;
                floor *= 0.85f * (1f - 0.5f * wo);
                dim = (strong ? 0.16f : 0.1f) * e * (1f - 0.4f * wo);
            }
            cap = Math.max(cap, f * kc);
            float kv = (float) Math.exp(Math.log(256.0) + (Math.log(floor) - Math.log(256.0)) * Math.pow(Math.min(1f, e), 0.6));
            vis = Math.min(vis, kv);
            sheet = Math.max(sheet, e * SHEET_ALPHA[k] * (k == WeatherClient.BLIZZARD ? 1f + 0.4f * wo : 1f));
        }
        float cr = WeatherClient.lerp(WeatherClient.prevColR, WeatherClient.colR, pt);
        float cg = WeatherClient.lerp(WeatherClient.prevColG, WeatherClient.colG, pt);
        float cb = WeatherClient.lerp(WeatherClient.prevColB, WeatherClient.colB, pt);
        if (dim > 0.004f) {
            // [blizzard2] the storm swallows the daylight: a soft darkening under the whiteout (also with shader packs)
            g.fill(0, 0, w, h, ((int) (Math.min(0.3f, dim) * 255f) & 255) << 24 | 0x0A0D12);
            g.flush();
        }
        if (cap > 0.004f) {
            double tanHalf = Math.tan(Math.toRadians(WeatherClient.fov) * 0.5);
            double pitch = Math.toRadians(cam.getXRot());
            float eye = WeatherClient.eyeHeight;
            int rgb = ((int) (cr * 255f) & 255) << 16 | ((int) (cg * 255f) & 255) << 8 | ((int) (cb * 255f) & 255);
            int prevY = 0;
            int prevA = rowAlpha(0, h, tanHalf, pitch, eye, vis, cap, strong);
            for (int i = 1; i <= ROWS; i++) {
                int y = i * h / ROWS;
                int a = rowAlpha(y, h, tanHalf, pitch, eye, vis, cap, strong);
                if (a > 0 || prevA > 0) g.fillGradient(0, prevY, w, y, (prevA << 24) | rgb, (a << 24) | rgb);
                prevY = y;
                prevA = a;
            }
            g.flush();
        }

        float frost = WeatherClient.lerp(WeatherClient.prevFrost, WeatherClient.frost, pt) * open; // [shelter] gone indoors in 1-2 s
        if (sheet <= 0.004f && frost <= 0.004f) return;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f m = g.pose().last().pose();

        // ---------------------------------------------------------------- 2. gust sheets (locked to view direction)
        if (sheet > 0.004f && WeatherClient.quality > 0 || sheet > 0.004f && strong) {
            double hfov = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(WeatherClient.fov) * 0.5) * w / Math.max(1, h)));
            float yaw = cam.getYRot(), pitchDeg = cam.getXRot();
            float sr = Math.min(1f, cr * 1.08f + 0.03f), sg = Math.min(1f, cg * 1.08f + 0.03f), sb = Math.min(1f, cb * 1.08f + 0.03f);
            RenderSystem.setShaderTexture(0, SHEET);
            float spanA = 1.35f, spanB = 0.8f;
            float uA = (float) (yaw / hfov) * spanA + WeatherClient.lerp(prevScrollA, scrollA, pt);
            float vA = (float) (pitchDeg / WeatherClient.fov) * spanA * h / w + WeatherClient.lerp(prevFallA, fallA, pt);
            quad(m, w, h, uA, vA, spanA, spanA * h / w, sr, sg, sb, sheet);
            float uB = (float) (yaw / hfov) * spanB + WeatherClient.lerp(prevScrollB, scrollB, pt) + 0.37f;
            float vB = (float) (pitchDeg / WeatherClient.fov) * spanB * h / w + WeatherClient.lerp(prevFallB, fallB, pt) + 0.61f;
            quad(m, w, h, uB, vB, spanB, spanB * h / w, sr, sg, sb, sheet * 0.7f);
        }

        // ---------------------------------------------------------------- 3. frost at the edges
        if (frost > 0.004f) {
            // [blizzard2] toned down: opacity capped at 0.5 (was 0.9), thinner/softer texture band, and it creeps in -
            // the quad starts 14% larger than the screen (only the outermost rime shows) and settles as the frost builds
            float k = Math.min(1f, frost / WeatherClient.FROST_MAX);
            float grow = 0.14f * (1f - k * k);
            float ox = w * grow * 0.5f, oy = h * grow * 0.5f;
            RenderSystem.setShaderTexture(0, FROST);
            quadRect(m, -ox, -oy, w + ox, h + oy, 0.93f, 0.96f, 1f, Math.min(WeatherClient.FROST_MAX, frost));
        }

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Whiteout alpha (0..255) for a screen row: open for near ground, full for the horizon and sky. */
    private static int rowAlpha(int y, int h, double tanHalf, double pitch, float eye, float vis, float cap, boolean strong) {
        double ndc = (y / (double) Math.max(1, h)) * 2.0 - 1.0; // +1 = bottom of the screen
        double below = pitch + Math.atan(ndc * tanHalf);       // angle of this row below the horizon
        double t;
        if (below <= 0.004) {
            t = 1.0;
        } else {
            double d = eye / Math.sin(Math.min(below, Math.PI * 0.5));
            t = 1.0 - Math.exp(-3.0 * d / Math.max(1.0, vis * 1.3)); // same "faint at the floor distance" as the fog
        }
        double a = strong ? cap * t : cap * (0.55 + 0.45 * t);
        return (int) Math.round(Math.max(0.0, Math.min(1.0, a)) * 255.0);
    }

    private static void quad(Matrix4f m, int w, int h, float u0, float v0, float uSpan, float vSpan, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(m, 0f, h, 0f).setUv(u0, v0 + vSpan).setColor(r, g, b, a);
        bb.addVertex(m, w, h, 0f).setUv(u0 + uSpan, v0 + vSpan).setColor(r, g, b, a);
        bb.addVertex(m, w, 0f, 0f).setUv(u0 + uSpan, v0).setColor(r, g, b, a);
        bb.addVertex(m, 0f, 0f, 0f).setUv(u0, v0).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void quadRect(Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(m, x0, y1, 0f).setUv(0f, 1f).setColor(r, g, b, a);
        bb.addVertex(m, x1, y1, 0f).setUv(1f, 1f).setColor(r, g, b, a);
        bb.addVertex(m, x1, y0, 0f).setUv(1f, 0f).setColor(r, g, b, a);
        bb.addVertex(m, x0, y0, 0f).setUv(0f, 0f).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent event) {
            event.registerBelowAll(FrontierHunts.id("weather_storm"), WeatherOverlay::render);
        }
    }
}
