package com.formaworks.frontierhunts.weather;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /frontierweather                       what the weather is doing where you stand
 * /frontierweather list                  every storm in this dimension (op)
 * /frontierweather blizzard|thunderstorm|squall|duststorm|fog|freezingfog|windstorm [seconds]   force an event on you (op)
 * /frontierweather clear [seconds]       ease every storm out and hold natural events off (op, default 600 s)
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class WeatherCommands {
    private WeatherCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("frontierweather").executes(ctx -> query(ctx.getSource()));
        root.then(Commands.literal("query").executes(ctx -> query(ctx.getSource())));
        root.then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(ctx -> list(ctx.getSource())));
        root.then(Commands.literal("clear").requires(s -> s.hasPermission(2))
                .executes(ctx -> clear(ctx.getSource(), 600))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                        .executes(ctx -> clear(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))));
        for (WeatherKind k : WeatherKind.VALUES) {
            if (k == WeatherKind.NONE) continue;
            root.then(Commands.literal(k.id).requires(s -> s.hasPermission(2))
                    .executes(ctx -> force(ctx.getSource(), k, 300))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 7200))
                            .executes(ctx -> force(ctx.getSource(), k, IntegerArgumentType.getInteger(ctx, "seconds")))));
        }
        event.getDispatcher().register(root);
    }

    private static boolean usable(CommandSourceStack src) {
        if (!WeatherDirector.weatherLevel(src.getLevel())) {
            src.sendFailure(Component.literal("This dimension has no sky weather."));
            return false;
        }
        return true;
    }

    private static int force(CommandSourceStack src, WeatherKind kind, int seconds) {
        if (!usable(src)) return 0;
        ServerLevel level = src.getLevel();
        Vec3 p = src.getPosition();
        BlockPos pos = BlockPos.containing(p);
        ServerPlayer player = src.getPlayer();
        Storm s = WeatherDirector.force(level, player, p.x, p.z, pos, kind, seconds);
        if (s == null) {
            src.sendFailure(Component.literal("Could not start " + kind.title()));
            return 0;
        }
        Climate c = new Climate().sample(level, pos);
        float w = SeasonClock.winterness(level);
        boolean natural = switch (kind) {
            case BLIZZARD -> c.snowyRegion(w) || c.deepCold();
            case DUST_STORM -> c.dryness > 0.3f;
            case THUNDERSTORM, SQUALL -> c.precip != net.minecraft.world.level.biome.Biome.Precipitation.SNOW && c.hasPrecipitation;
            default -> true;
        };
        String note = natural ? "" : " (note: this place is " + c.describe(w) + " - natural " + kind.title().toLowerCase(Locale.ROOT) + "s never happen here; forced events ignore that)";
        src.sendSuccess(() -> Component.literal("Forced " + kind.title() + " for " + seconds + " s, radius " + Math.round(s.radius)
                + " blocks. Builds up over " + s.rampIn / 20 + " s." + note), true);
        return 1;
    }

    private static int clear(CommandSourceStack src, int seconds) {
        if (!usable(src)) return 0;
        int n = WeatherDirector.clear(src.getLevel(), seconds);
        src.sendSuccess(() -> Component.literal("Easing out " + n + " weather event(s); natural events held off for " + seconds + " s. (Vanilla rain/snow is untouched - use /weather clear too.)"), true);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        List<Storm> storms = WeatherDirector.list(level);
        long t = level.getGameTime();
        if (storms.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No weather events in " + level.dimension().location() + "."), false);
            return 1;
        }
        Vec3 p = src.getPosition();
        src.sendSuccess(() -> Component.literal(storms.size() + " weather event(s):"), false);
        for (Storm s : storms) {
            double dx = s.centerX(t) - p.x, dz = s.centerZ(t) - p.z;
            String line = String.format(Locale.ROOT, " #%d %s%s - %s, %d%% strength, centre %d blocks away, radius %d, %ds left",
                    s.id, s.kind.title(), s.forced ? " (forced)" : "", s.phase(t), Math.round(s.peak * s.envelope(t) * 100),
                    Math.round(Math.sqrt(dx * dx + dz * dz)), Math.round(s.radius), Math.max(0, (s.end() - t) / 20));
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return storms.size();
    }

    private static int query(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        SeasonClock.Season season = SeasonClock.season(level);
        float w = SeasonClock.winterness(level);
        Climate c = new Climate().sample(level, pos);
        String vanilla = level.isThundering() ? "thunder" : level.isRaining() ? (c.precip == net.minecraft.world.level.biome.Biome.Precipitation.SNOW ? "snowing" : c.precip == net.minecraft.world.level.biome.Biome.Precipitation.RAIN ? "raining" : "dry front") : "clear";
        if (!WeatherDirector.weatherLevel(level)) {
            src.sendSuccess(() -> Component.literal("No sky weather in this dimension."), false);
            return 1;
        }
        if (!WeatherConfig.enabled()) {
            src.sendSuccess(() -> Component.literal("Seasonal weather is switched off in the server config (weather.seasonalWeather)."), false);
            return 1;
        }
        SeasonalWeather.Sample s = SeasonalWeather.sample(level, pos);
        long t = level.getGameTime();
        String head = season.title() + " · " + c.describe(w) + " · vanilla " + vanilla;
        src.sendSuccess(() -> Component.literal(head), false);
        if (s.calm()) {
            src.sendSuccess(() -> Component.literal("No weather event here right now."), false);
        } else {
            float vis = SeasonalWeather.visibilityFor(s.kind(), s.severity(), WeatherConfig.blizzardVisibility());
            String line = String.format(Locale.ROOT, "%s (%s): %d%% here, visibility about %s blocks, %ds left",
                    s.kind().title(), s.storm().phase(t), Math.round(s.severity() * 100), vis > 400 ? "open" : String.valueOf(Math.round(vis)),
                    Math.max(0, (s.storm().end() - t) / 20));
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
