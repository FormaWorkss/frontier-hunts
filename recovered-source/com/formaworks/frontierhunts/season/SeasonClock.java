package com.formaworks.frontierhunts.season;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The one shared reserve calendar for seasons, weather, coats, rut and events.
 *
 * Calendar ticks = overworld game time + an admin offset (SavedData "frontierhunts_season").
 * One reserve month = {@code daysPerReserveMonth} Minecraft days (server config, default 7); the calendar starts
 * in September. {@link #yearPosition} is a continuous month position 0..12 (0 = start of January, 8 = September).
 * Seasons: spring Mar-May, summer Jun-Aug, fall Sep-Nov, winter Dec-Feb.
 *
 * Works on both sides: on the server it reads the live config and SavedData; on the client it uses the values the
 * server last synced ({@link Sync}), falling back to the client's own config copy in singleplayer.
 */
public final class SeasonClock {
    public enum Season {
        SPRING, SUMMER, FALL, WINTER;
        public String title() { return switch (this) { case SPRING -> "Spring"; case SUMMER -> "Summer"; case FALL -> "Fall"; case WINTER -> "Winter"; }; }
    }

    /** Client-side copy of the server calendar (set by {@link Sync}). */
    private static volatile long clientOffset = 0L;
    private static volatile int clientDaysPerMonth = -1;

    private SeasonClock() {}

    // ------------------------------------------------------------------ pure math
    /** Continuous month position 0..12 (0 = Jan 1) for a calendar tick count. */
    public static double yearPosition(long calendarTicks, int daysPerMonth) {
        long monthTicks = 24000L * Math.max(1, daysPerMonth);
        long t = Math.max(0L, calendarTicks);
        long month = Math.floorMod(t / monthTicks + 8L, 12L);
        return month + (double) (t % monthTicks) / (double) monthTicks;
    }

    public static Season seasonAt(double yearPos) {
        int m = (int) Math.floor(yearPos) % 12;
        return Season.values()[Math.floorMod(m - 2, 12) / 3];
    }

    /** 0..1 progress through the current three-month season. */
    public static float progressAt(double yearPos) {
        double p = Math.floorMod((long) Math.floor((yearPos - 2.0) * 1000.0), 12000L) / 1000.0; // months since Mar 1
        return (float) ((p % 3.0) / 3.0);
    }

    /** Signed month distance a-b wrapped to (-6, 6]. */
    public static double monthDelta(double a, double b) {
        double d = (a - b) % 12.0;
        if (d > 6.0) d -= 12.0;
        if (d <= -6.0) d += 12.0;
        return d;
    }

    /**
     * Smooth "how wintry is it" 0..1 (1 = mid January). Useful for temperature offsets and coats.
     */
    public static float winterness(double yearPos) {
        double d = Math.abs(monthDelta(yearPos, 0.5));
        return (float) Math.max(0.0, Math.min(1.0, (4.0 - d) / 3.0));
    }

    // ------------------------------------------------------------------ level access
    public static long calendarTicks(Level level) {
        if (level == null) return 0L;
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            MinecraftServer server = sl.getServer();
            return server.overworld().getGameTime() + Data.get(server).offset;
        }
        return level.getGameTime() + clientOffset;
    }

    public static int daysPerMonth(Level level) {
        if (level != null && level.isClientSide && clientDaysPerMonth > 0) return clientDaysPerMonth;
        try { return Math.max(1, HuntConfig.DAYS_PER_MONTH.get()); } catch (Throwable t) { return 7; }
    }

    public static double yearPosition(Level level) { return yearPosition(calendarTicks(level), daysPerMonth(level)); }
    public static Season season(Level level) { return seasonAt(yearPosition(level)); }
    public static float progress(Level level) { return progressAt(yearPosition(level)); }
    public static float winterness(Level level) { return winterness(yearPosition(level)); }

    // ------------------------------------------------------------------ persistence
    public static final class Data extends SavedData {
        long offset;
        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "frontierhunts_season");
        }
        static Data load(CompoundTag tag, HolderLookup.Provider p) { Data d = new Data(); d.offset = tag.getLong("offset"); return d; }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) { tag.putLong("offset", offset); return tag; }
    }

    /** Jump the calendar to a month position (0..12, 0 = Jan 1) by changing the offset. Never rewinds game time. */
    public static void setYearPosition(MinecraftServer server, double targetPos) {
        int dpm = Math.max(1, HuntConfig.DAYS_PER_MONTH.get());
        long monthTicks = 24000L * dpm;
        long yearTicks = monthTicks * 12L;
        long now = server.overworld().getGameTime();
        Data d = Data.get(server);
        double cur = yearPosition(now + d.offset, dpm);
        double ahead = ((targetPos - cur) % 12.0 + 12.0) % 12.0; // always move forward in the calendar
        d.offset += (long) Math.round(ahead * monthTicks);
        d.offset = Math.floorMod(d.offset, yearTicks * 1000L);
        d.setDirty();
        broadcast(server);
    }

    // ------------------------------------------------------------------ sync
    public record Sync(long offset, int daysPerMonth) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(FrontierHunts.id("season_sync"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (buf, s) -> { buf.writeLong(s.offset); buf.writeVarInt(s.daysPerMonth); },
                buf -> new Sync(buf.readLong(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new Sync(Data.get(server).offset, Math.max(1, HuntConfig.DAYS_PER_MONTH.get())));
    }

    @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void payloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").optional().playToClient(Sync.TYPE, Sync.CODEC, (s, ctx) -> ctx.enqueueWork(() -> {
                clientOffset = s.offset();
                clientDaysPerMonth = Math.max(1, s.daysPerMonth());
            }));
        }
    }

    @EventBusSubscriber(modid = FrontierHunts.ID)
    public static final class GameEvents {
        private static int tick;

        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
                PacketDistributor.sendToPlayer(sp, new Sync(Data.get(sp.getServer()).offset, Math.max(1, HuntConfig.DAYS_PER_MONTH.get())));
            }
        }

        @SubscribeEvent
        public static void serverTick(ServerTickEvent.Post event) {
            if (++tick % 6000 == 0) broadcast(event.getServer()); // config changes / drift safety, every 5 minutes
        }

        @SubscribeEvent
        public static void commands(RegisterCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("season")
                .executes(ctx -> {
                    ServerLevel l = ctx.getSource().getLevel();
                    double y = yearPosition(l);
                    Season s = seasonAt(y);
                    String[] months = {"January","February","March","April","May","June","July","August","September","October","November","December"};
                    int m = (int) Math.floor(y) % 12;
                    int day = (int) Math.floor((y - Math.floor(y)) * daysPerMonth(l)) + 1;
                    ctx.getSource().sendSuccess(() -> Component.literal(s.title() + " (" + Math.round(progressAt(y) * 100) + "%) · "
                            + months[m] + " " + day + " · " + daysPerMonth(l) + " days per reserve month"), false);
                    return 1;
                })
                .then(Commands.literal("set").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("season", StringArgumentType.word())
                        .suggests((c, b) -> { for (String x : new String[]{"spring","summer","fall","winter"}) b.suggest(x); return b.buildFuture(); })
                        .executes(ctx -> setSeason(ctx.getSource(), StringArgumentType.getString(ctx, "season"), "early"))
                        .then(Commands.argument("stage", StringArgumentType.word())
                            .suggests((c, b) -> { for (String x : new String[]{"early","mid","late"}) b.suggest(x); return b.buildFuture(); })
                            .executes(ctx -> setSeason(ctx.getSource(), StringArgumentType.getString(ctx, "season"), StringArgumentType.getString(ctx, "stage"))))))
                .then(Commands.literal("month").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("month", IntegerArgumentType.integer(1, 12))
                        .executes(ctx -> {
                            setYearPosition(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "month") - 1 + 0.02);
                            ctx.getSource().sendSuccess(() -> Component.literal("Reserve calendar moved to month " + IntegerArgumentType.getInteger(ctx, "month")), true);
                            return 1;
                        }))));
        }

        private static int setSeason(net.minecraft.commands.CommandSourceStack src, String season, String stage) {
            double start = switch (season.toLowerCase()) {
                case "spring" -> 2.0; case "summer" -> 5.0; case "fall", "autumn" -> 8.0; case "winter" -> 11.0; default -> -1.0; };
            if (start < 0) { src.sendFailure(Component.literal("Unknown season: " + season)); return 0; }
            double off = switch (stage.toLowerCase()) { case "mid" -> 1.3; case "late" -> 2.4; default -> 0.05; };
            setYearPosition(src.getServer(), (start + off) % 12.0);
            src.sendSuccess(() -> Component.literal("Season set to " + stage + " " + season), true);
            return 1;
        }
    }
}
