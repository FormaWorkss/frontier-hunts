package com.formaworks.frontierhunts.shelter;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.survival.SurvivalApi;
import com.formaworks.frontierhunts.survival.Thermal;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [shelter] {@code /shelter} (anyone, read-only): what the shared shelter detector sees at your eye and what it does to
 * your warmth - for testing tents, cabins, caves and fires in game.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class ShelterCommands {
    private ShelterCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shelter").executes(ctx -> query(ctx.getSource())));
    }

    private static int query(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("Players only."));
            return 0;
        }
        ShelterScan.Result r = Shelter.scan(p, p.getEyePosition(), new ShelterScan.Result());
        Thermal.Shelter s = Thermal.shelter(p);
        BlockPos head = BlockPos.containing(p.getEyePosition());
        float fire = Thermal.heat(p.serverLevel(), p.blockPosition(), head, s.enclosure());
        String kind = s.tent() ? "in a tent" : r.fabric > 0.5f ? "in a blind" : s.enclosed() ? "indoors" : s.roofed() ? "under a roof" : "in the open";
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Shelter: %s - enclosure %.0f%%, wind kept off %.0f%%, snow/rain kept off %.0f%% (roof %.2f, walls %.2f, canopy %.2f, sky light %d)",
                kind, r.enclosure * 100f, r.windBlock * 100f, r.precipBlock * 100f, r.roof, r.walls, r.canopy, s.sky())), false);
        float air = Thermal.air(p.serverLevel(), head);
        float still = Thermal.sheltered(air, s);
        String felt = "";
        try {
            SurvivalApi.Status st = SurvivalApi.status(p);
            felt = String.format(Locale.ROOT, ", feels like %.1f C, body heat %.0f", st.feltTemperature(), st.bodyHeat());
        } catch (RuntimeException ignored) {
        }
        String f = felt;
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Warmth: outside air %.1f C, shelter air %.1f C, fires +%.1f C%s",
                air, still, fire, f)), false);
        return 1;
    }
}
