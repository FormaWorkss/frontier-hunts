package com.formaworks.frontierhunts.weather;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Seasonal-weather settings. The values are defined inside HuntConfig's server and client specs (one hook line each,
 * see HuntConfig "[weather]") so they live in the normal frontierhunts config files.
 */
public final class WeatherConfig {
    public enum Quality { AUTO, PERFORMANCE, BALANCED, CINEMATIC }

    // server (world) settings
    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.IntValue FREQUENCY;
    public static ModConfigSpec.IntValue INTENSITY;
    public static ModConfigSpec.IntValue BLIZZARD_VISIBILITY;
    // client settings
    public static ModConfigSpec.EnumValue<Quality> QUALITY;
    public static ModConfigSpec.BooleanValue OVERLAY;
    public static ModConfigSpec.BooleanValue SOUNDS;

    private WeatherConfig() {}

    /** Called from HuntConfig's server builder inside push("weather"). */
    public static void server(ModConfigSpec.Builder b) {
        ENABLED = b.comment("Seasonal severe weather: blizzards in snowy regions in winter, thunderstorms and rain squalls, desert dust storms, valley fog and fall wind storms. Off = vanilla weather only.")
                .define("seasonalWeather", true);
        FREQUENCY = b.comment("How often weather events happen, in percent of normal (0 = only forced events, 100 = intended, 300 = very stormy).")
                .defineInRange("eventFrequencyPercent", 100, 0, 400);
        INTENSITY = b.comment("How strong weather events get, in percent of normal. Lower values keep visibility better at the peak of storms.")
                .defineInRange("eventIntensityPercent", 100, 10, 150);
        BLIZZARD_VISIBILITY = b.comment("How far you can see at the height of a blizzard, in blocks. Synced to every player so nobody can see further than anyone else.")
                .defineInRange("blizzardMinimumVisibility", 5, 3, 48);
    }

    /** Called from HuntConfig's client builder inside push("weather"). */
    public static void client(ModConfigSpec.Builder b) {
        QUALITY = b.comment("Weather effects budget. AUTO follows the Frontier graphics quality. PERFORMANCE keeps the essential cues (fog, visibility, sound) with far fewer particles; CINEMATIC adds denser snow, drifting ground snow and ice crystals. Cosmetic only.")
                .defineEnum("weatherQuality", Quality.AUTO);
        OVERLAY = b.comment("Screen-space storm layer (whiteout, dust haze, frost at the screen edges). Needed for storms to hide distant terrain when a shader pack ignores Minecraft fog.")
                .define("weatherOverlay", true);
        SOUNDS = b.comment("Storm sound layers: blizzard roar and howling gusts, downpours, dust and wind storms.")
                .define("weatherSounds", true);
    }

    // ------------------------------------------------------------------ safe getters (configs may not be loaded yet)
    public static boolean enabled() { try { return ENABLED == null || ENABLED.get(); } catch (Throwable t) { return true; } }

    public static float frequency() { try { return FREQUENCY == null ? 1f : FREQUENCY.get() / 100f; } catch (Throwable t) { return 1f; } }

    public static float intensity() { try { return INTENSITY == null ? 1f : INTENSITY.get() / 100f; } catch (Throwable t) { return 1f; } }

    public static float blizzardVisibility() { try { return BLIZZARD_VISIBILITY == null ? 5f : BLIZZARD_VISIBILITY.get(); } catch (Throwable t) { return 5f; } }

    public static Quality quality() { try { return QUALITY == null ? Quality.AUTO : QUALITY.get(); } catch (Throwable t) { return Quality.AUTO; } }

    public static boolean overlay() { try { return OVERLAY == null || OVERLAY.get(); } catch (Throwable t) { return true; } }

    public static boolean sounds() { try { return SOUNDS == null || SOUNDS.get(); } catch (Throwable t) { return true; } }
}
