package com.formaworks.frontierhunts.landscape.ride.grime;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for ATV mud / snow / water effects. Defined inside HuntConfig's client spec (one hook line, see
 * HuntConfig "[atvgrime]") so they live in the normal frontierhunts client config file.
 */
public final class AtvGrimeConfig {
    public static ModConfigSpec.BooleanValue SCREEN_SPLATTER;
    public static ModConfigSpec.BooleanValue SPRAY;

    private AtvGrimeConfig() {}

    /** Called from HuntConfig's client builder inside push("atvGrime"). */
    public static void client(ModConfigSpec.Builder b) {
        SCREEN_SPLATTER = b.comment("First person on an ATV: mud, snow and water splash onto your view when you drive fast through them, then dry, melt or run off. Third person never shows it.")
                .define("screenSplatter", true);
        SPRAY = b.comment("Mud clods, snow powder and water spray thrown from ATV wheels. The amount follows Effects quality and Minecraft's Particles setting.")
                .define("wheelSpray", true);
    }

    public static boolean screenSplatter() {
        try { return SCREEN_SPLATTER == null || SCREEN_SPLATTER.get(); } catch (Throwable t) { return true; }
    }

    public static boolean spray() {
        try { return SPRAY == null || SPRAY.get(); } catch (Throwable t) { return true; }
    }
}
