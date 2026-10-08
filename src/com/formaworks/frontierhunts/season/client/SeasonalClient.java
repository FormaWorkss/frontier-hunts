package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.season.FoliageSeason;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.season.SeasonState;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client tick of the seasons: keeps {@link SeasonState} in step with the synced calendar (biome temperature for
 * snowfall, coats), publishes the meshing stage ({@link SeasonView}) and, when the stage changes, rebuilds the chunk
 * sections that hold seasonal content - nearest first, a few per tick (6 per tick as time passes normally, 24 per
 * tick after a calendar jump such as {@code /season set}, so the new look appears within a few seconds without a
 * rebuild storm). Also runs {@link LeafFall}. Nothing here runs per frame.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class SeasonalClient {
    private SeasonalClient() {}

    // [perf2] SLOW was 6: a natural stage step is not urgent (a stage lasts ~1.75 days), so spread it thinner
    static final int SLOW = 4, FAST = 24;
    private static Object lastLevel;
    private static double lastYear = Double.NaN;
    private static long[] queue = new long[0];
    private static int queueAt, perTick = SLOW;
    private static int ticks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        try {
            tick();
        } catch (RuntimeException e) {
            // cosmetic only: never break the client tick
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            lastLevel = null;
            lastYear = Double.NaN;
            queue = new long[0];
            queueAt = 0;
            return;
        }
        if (level != lastLevel) {
            lastLevel = level;
            lastYear = Double.NaN;
            SeasonView.sections().clear();
            LeafParticle.LIVE.set(0); // the particle engine was cleared with the old level
            SeasonState.clientBiomes(level.registryAccess().registryOrThrow(Registries.BIOME));
        }
        // [integration] the trail-camera darkroom swaps the client level's game/day time to a photo's moment for a few
        // render frames (restored in RenderFrameEvent.Post, so a client tick should never see it). Never step the
        // season - and so never queue section rebuilds or reset the published stage - from that borrowed time.
        if (com.formaworks.frontierhunts.client.trailcam.Darkroom.active()) return;
        SeasonState.update(level);
        double year = SeasonState.yearPos();
        boolean on = SeasonState.enabled();
        int key = FoliageSeason.stageKey(year);
        if (key != SeasonView.key() || on != SeasonView.on()) {
            boolean jump = Double.isNaN(lastYear) || on != SeasonView.on() || Math.abs(SeasonClock.monthDelta(year, lastYear)) > 0.2;
            SeasonView.publish(on, key);
            sweep(mc, jump);
        }
        lastYear = year;
        drain(mc);
        if (++ticks % 600 == 0) prune(mc);
        LeafFall.tick(mc, level);
    }

    /** Queue every tracked seasonal section in range, nearest first. */
    private static void sweep(Minecraft mc, boolean fast) {
        Vec3 at = camera(mc);
        if (at == null) return;
        double limit = (mc.options.getEffectiveRenderDistance() + 2) * 16.0, limit2 = limit * limit;
        List<long[]> list = new ArrayList<>();
        for (Iterator<Long> it = SeasonView.sections().iterator(); it.hasNext(); ) {
            long s = it.next();
            double dx = (SeasonView.sx(s) << 4) + 8 - at.x, dz = (SeasonView.sz(s) << 4) + 8 - at.z;
            double d2 = dx * dx + dz * dz;
            if (d2 > limit2) { it.remove(); continue; }
            list.add(new long[]{s, (long) d2});
        }
        list.sort((a, b) -> Long.compare(a[1], b[1]));
        long[] q = new long[list.size()];
        for (int i = 0; i < q.length; i++) q[i] = list.get(i)[0];
        queue = q;
        queueAt = 0;
        perTick = fast ? FAST : SLOW;
    }

    private static void drain(Minecraft mc) {
        if (queueAt >= queue.length || mc.levelRenderer == null) return;
        // [perf2] fewer while frames are spiking (frame-time governor); the rest stay queued for the next ticks
        int budget = com.formaworks.frontierhunts.perf.client.FrameGovernor.budget(perTick);
        if (budget < perTick) com.formaworks.frontierhunts.perf.client.PerfStats.add(com.formaworks.frontierhunts.perf.client.PerfStats.DIRTY_DEFERRED, perTick - budget);
        int end = Math.min(queue.length, queueAt + budget);
        com.formaworks.frontierhunts.perf.client.PerfStats.add(com.formaworks.frontierhunts.perf.client.PerfStats.SEASON_DIRTY, end - queueAt);
        for (; queueAt < end; queueAt++) {
            long s = queue[queueAt];
            mc.levelRenderer.setSectionDirty(SeasonView.sx(s), SeasonView.sy(s), SeasonView.sz(s));
        }
    }

    private static void prune(Minecraft mc) {
        Vec3 at = camera(mc);
        if (at == null) return;
        double limit = (mc.options.getEffectiveRenderDistance() + 3) * 16.0, limit2 = limit * limit;
        SeasonView.sections().removeIf(s -> {
            double dx = (SeasonView.sx(s) << 4) + 8 - at.x, dz = (SeasonView.sz(s) << 4) + 8 - at.z;
            return dx * dx + dz * dz > limit2;
        });
    }

    private static Vec3 camera(Minecraft mc) {
        var camera = mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
        if (camera != null && camera.isInitialized()) return camera.getPosition();
        return mc.player != null ? mc.player.position() : null;
    }
}
