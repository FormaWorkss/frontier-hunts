package com.formaworks.frontierhunts.weather;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

/**
 * What kind of place a block position is, for weather purposes. Side-agnostic (server director, client presentation
 * and the public query API all use the same rules, so everybody agrees on where a storm applies).
 *
 * "Regional" coldness deliberately uses the biome's own climate temperature (not a season-adjusted one) minus an
 * altitude lapse, so a blizzard stays a thing of snowy biomes, taiga in winter and high mountains, while the live
 * precipitation check ({@link #precip}) follows whatever the season is doing to snowfall.
 */
public final class Climate {
    static final TagKey<Biome> TUNDRA = TagKey.create(Registries.BIOME, FrontierHunts.id("regions/tundra"));
    static final TagKey<Biome> WETLANDS = TagKey.create(Registries.BIOME, FrontierHunts.id("regions/wetlands"));
    static final TagKey<Biome> HIGHLANDS = TagKey.create(Registries.BIOME, FrontierHunts.id("regions/highlands"));

    /** Unmodified biome climate temperature. */
    public float rawTemp;
    /** rawTemp minus 0.0016 per block above y 80 (same lapse the alpine ambience uses). */
    public float cold;
    public float downfall;
    public boolean snowTag, wet, forest, mountain, ocean, hasPrecipitation;
    /** 1 = desert/badlands, ~0.45 = savanna/steppe, 0 = anything with real rainfall. */
    public float dryness;
    /** Live precipitation at this position (includes season snowfall changes by other systems). */
    public Biome.Precipitation precip = Biome.Precipitation.NONE;
    public int y;

    public Climate sample(Level level, BlockPos pos) {
        Holder<Biome> h = level.getBiome(pos);
        Biome b = h.value();
        Biome.ClimateSettings cs;
        try { cs = b.getModifiedClimateSettings(); } catch (Throwable t) { cs = null; }
        rawTemp = cs != null ? cs.temperature() : b.getBaseTemperature();
        downfall = cs != null ? cs.downfall() : 0.5f;
        y = pos.getY();
        cold = rawTemp - Math.max(0, y - 80) * 0.0016f;
        hasPrecipitation = b.hasPrecipitation();
        precip = b.getPrecipitationAt(pos);
        snowTag = h.is(Tags.Biomes.IS_SNOWY) || h.is(Tags.Biomes.IS_ICY) || h.is(TUNDRA);
        ocean = h.is(BiomeTags.IS_OCEAN) || h.is(Tags.Biomes.IS_OCEAN);
        mountain = h.is(Tags.Biomes.IS_MOUNTAIN) || h.is(BiomeTags.IS_MOUNTAIN) || h.is(HIGHLANDS) || y > 260;
        forest = h.is(BiomeTags.IS_FOREST) || h.is(BiomeTags.IS_TAIGA) || h.is(Tags.Biomes.IS_FOREST) || h.is(Tags.Biomes.IS_TAIGA);
        int words = h.unwrapKey().map(Climate::words).orElse(0);
        wet = h.is(BiomeTags.IS_RIVER) || h.is(Tags.Biomes.IS_RIVER) || h.is(Tags.Biomes.IS_SWAMP) || h.is(WETLANDS)
                || h.is(Tags.Biomes.IS_WET_OVERWORLD) || (words & 1) != 0;
        if (!forest) forest = (words & 2) != 0;
        if (h.is(Tags.Biomes.IS_DESERT) || h.is(BiomeTags.IS_BADLANDS) || h.is(Tags.Biomes.IS_BADLANDS)) dryness = 1f;
        else if (!hasPrecipitation && rawTemp > 1.0f) dryness = 0.9f;
        else if (h.is(Tags.Biomes.IS_SAVANNA) || h.is(BiomeTags.IS_SAVANNA) || h.is(Tags.Biomes.IS_DRY_OVERWORLD) || (words & 4) != 0) dryness = 0.45f;
        else dryness = 0f;
        return this;
    }

    private static final String[] WET_WORDS = {"river", "swamp", "marsh", "bog", "wetland", "carr", "lakeshore", "bottom", "falls", "gorge", "muskeg", "mangrove"};
    private static final String[] FOREST_WORDS = {"wood", "forest", "grove", "hollow", "pine", "cedar", "aspen", "birch", "maple", "larch", "rowan", "taiga"};

    private static final java.util.concurrent.ConcurrentHashMap<ResourceKey<Biome>, Integer> WORDS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Name-derived hints for modded/Frontier biomes without common tags (cached per biome key; no per-call strings). */
    private static int words(ResourceKey<Biome> key) {
        Integer cached = WORDS.get(key);
        if (cached != null) return cached;
        String path = key.location().getPath();
        int w = (containsAny(path, WET_WORDS) ? 1 : 0) | (containsAny(path, FOREST_WORDS) ? 2 : 0) | (path.contains("sagebrush") || path.contains("steppe") ? 4 : 0);
        WORDS.put(key, w);
        return w;
    }

    private static boolean containsAny(String s, String[] words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    /** Snowy region for blizzard purposes. {@code winterness} 0..1 widens it in deep winter (taiga, windswept hills, high ridges). */
    public boolean snowyRegion(float winterness) {
        return snowTag || cold <= 0.15f + 0.22f * winterness;
    }

    /** Places cold enough for blizzards outside winter (frozen peaks, glaciers, the tundra). */
    public boolean deepCold() {
        return cold <= -0.25f || (snowTag && cold <= 0.05f);
    }

    /** High, exposed snowy mountains: blizzards are more frequent, longer and harder here. */
    public boolean highSnow() {
        return (mountain || y > 200) && cold <= 0.3f;
    }

    /**
     * 0..1: does an event of this kind actually happen at this place (ignoring the storm's own envelope)?
     * {@code rain} is the vanilla rain level (0..1) at this place's level.
     */
    public float applicability(WeatherKind kind, float winterness, float rain) {
        float rainGate = Math.min(1f, rain * 1.25f);
        return switch (kind) {
            // [1.1.8] only in snowy biomes themselves (and frozen peaks): winter no longer spreads blizzards into taiga and
            // forest next door; the camera-side smoothing fades the storm out as you walk out of the biome
            case BLIZZARD -> precip == Biome.Precipitation.SNOW && (snowTag || deepCold()) ? rainGate : 0f;
            case THUNDERSTORM, SQUALL -> precip == Biome.Precipitation.RAIN ? rainGate : 0f;
            // [1.1.8] sandstorms only in deserts and badlands (dry grassland and savanna no longer get them)
            case DUST_STORM -> precip == Biome.Precipitation.RAIN && rain > 0.2f || dryness < 0.9f ? 0f : 1f;
            case FOG, FREEZING_FOG -> dryness >= 0.9f ? 0f : 1f;
            case WIND_STORM -> 1f;
            case NONE -> 0f;
        };
    }

    public String describe(float winterness) {
        StringBuilder sb = new StringBuilder();
        if (snowyRegion(winterness)) sb.append(highSnow() ? "high snowy mountains" : "snowy region");
        else if (dryness >= 0.9f) sb.append("desert");
        else if (dryness > 0f) sb.append("dry grassland");
        else if (wet) sb.append("wet lowland");
        else if (forest) sb.append("forest");
        else if (ocean) sb.append("ocean");
        else sb.append("temperate");
        sb.append(String.format(java.util.Locale.ROOT, " (cold %.2f, precip %s)", cold, precip.name().toLowerCase(java.util.Locale.ROOT)));
        return sb.toString();
    }
}
