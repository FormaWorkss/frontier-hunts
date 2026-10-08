package com.formaworks.frontierhunts.season;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The live seasonal state both sides read without a level at hand (biome temperature, deer coats, chunk
 * meshing threads). Refreshed every tick from {@link SeasonClock}: by the server tick on the server and by the
 * client tick (SeasonalClient) on a client. In singleplayer both write the same calendar, so they agree; a
 * remote client derives it from the calendar and config the server synced.
 */
public final class SeasonState {
    private SeasonState() {}

    private static volatile double yearPos = 8.0;
    private static volatile boolean enabled = true;
    private static volatile boolean snow = true;
    /** Winter cold 0..1 applied to seasonal biomes' temperature (0 outside winter). */
    private static volatile float cold;

    public static double yearPos() { return yearPos; }
    /** Seasons change the world's look, coats and snow (server config "seasons", synced to clients). */
    public static boolean enabled() { return enabled; }
    /** Seasonal snow, freezing and spring melt in temperate biomes (server config "seasonalSnow"). */
    public static boolean snow() { return enabled && snow; }
    /** Winter cold 0..1 (0 = biome temperatures untouched). */
    public static float winterCold() { return cold; }

    public static void update(Level level) {
        if (level == null) return;
        double y = SeasonClock.yearPosition(level);
        boolean on = config(true), sn = config(false);
        yearPos = y;
        enabled = on;
        snow = sn;
        cold = on && sn ? FoliageSeason.snowCold(y) : 0F;
    }

    private static boolean config(boolean seasons) {
        try {
            return seasons ? HuntConfig.SEASONS.get() : HuntConfig.SEASONAL_SNOW.get();
        } catch (RuntimeException e) {
            return true; // config not loaded yet (title screen, early join): the defaults
        }
    }

    // ------------------------------------------------------------------ seasonal biomes
    private static final Map<Biome, Boolean> SEASONAL = new ConcurrentHashMap<>();
    private static volatile Registry<Biome> serverBiomes, clientBiomes;

    /**
     * Temperature seen by weather for a biome: its own plus the seasonal offset when the biome has seasons
     * (temperate Overworld land and fresh water; never oceans, beaches, jungles, savannas, badlands, deserts or
     * mangroves, which stay snow-free).
     */
    public static float adjust(Biome biome, float temperature) {
        float c = cold;
        if (c == 0F || !seasonal(biome)) return temperature;
        // deep winter brings every seasonal biome below the snow line (0.15); warmer biomes freeze later in the
        // fall and thaw earlier in spring than cooler ones
        return temperature - c * Math.max(0.9F, biome.getBaseTemperature() + 0.05F);
    }

    public static boolean seasonal(Biome biome) {
        Boolean known = SEASONAL.get(biome);
        if (known != null) return known;
        Boolean v = classify(biome);
        if (v == null) return false; // registry or tags not bound yet: ask again later
        SEASONAL.put(biome, v);
        return v;
    }

    private static Boolean classify(Biome biome) {
        if (!biome.hasPrecipitation()) return false;
        Registry<Biome> s = serverBiomes, c = clientBiomes;
        for (Registry<Biome> registry : new Registry[]{s, c}) {
            if (registry == null) continue;
            Optional<ResourceKey<Biome>> key = registry.getResourceKey(biome);
            if (key.isEmpty()) continue;
            String path = key.get().location().getPath();
            if (path.contains("ocean") || path.contains("coast") || path.contains("beach") || path.contains("shore")) return false;
            if (key.get().location().getNamespace().equals(FrontierHunts.ID)) {
                // the reserve's own biomes are temperate/northern (their high temperatures only tune grass colour)
                return !(path.contains("desert") || path.contains("savanna") || path.contains("badlands") || path.contains("jungle"));
            }
            if (biome.getBaseTemperature() >= 0.9F) return false;
            if (registry.getTag(BiomeTags.IS_OVERWORLD).isEmpty()) return null;
            Optional<Holder.Reference<Biome>> h = registry.getHolder(key.get());
            if (h.isEmpty()) return null;
            Holder<Biome> holder = h.get();
            if (!holder.is(BiomeTags.IS_OVERWORLD)) return false;
            if (holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_DEEP_OCEAN) || holder.is(BiomeTags.IS_BEACH)
                || holder.is(BiomeTags.IS_JUNGLE) || holder.is(BiomeTags.IS_SAVANNA) || holder.is(BiomeTags.IS_BADLANDS)
                || key.get() == Biomes.MANGROVE_SWAMP || key.get() == Biomes.DESERT || key.get() == Biomes.MUSHROOM_FIELDS) return false;
            return true;
        }
        return null;
    }

    /** Client: the biome registry of the joined world (tags arrive with it). */
    public static void clientBiomes(Registry<Biome> registry) {
        if (registry != clientBiomes) {
            clientBiomes = registry;
            SEASONAL.clear();
        }
    }

    @EventBusSubscriber(modid = FrontierHunts.ID)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void starting(ServerAboutToStartEvent event) {
            serverBiomes = event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
            SEASONAL.clear();
            update(event.getServer().overworld());
        }

        @SubscribeEvent
        public static void stopped(ServerStoppedEvent event) {
            serverBiomes = null;
            SEASONAL.clear();
        }

        @SubscribeEvent
        public static void tick(ServerTickEvent.Pre event) {
            update(event.getServer().overworld());
        }
    }
}
