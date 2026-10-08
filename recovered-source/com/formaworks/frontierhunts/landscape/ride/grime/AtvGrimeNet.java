package com.formaworks.frontierhunts.landscape.ride.grime;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * ATV grime networking (one tiny snapshot payload), the spray particle types and the {@code /atvgrime} test command.
 */
public final class AtvGrimeNet {
    public static final String[] PARTICLES = {"atv_clod", "atv_mist", "atv_drop", "atv_spray"}; // [atv2] + atv_spray
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOD = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("atv_clod"));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("atv_mist"));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DROP = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("atv_drop"));
    /** [atv2] Velocity-aligned spray plumes / fans (wheels, bow wave, entry splash). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRAY = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("atv_spray"));

    private AtvGrimeNet() {}

    /** Server -> client grime snapshot for one ATV (all values 0..255). */
    public record Sync(int entity, byte mudLow, byte mudHigh, byte moist, byte snow, byte wet) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(FrontierHunts.id("atv_grime"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of((b, s) -> {
            b.writeVarInt(s.entity);
            b.writeByte(s.mudLow);
            b.writeByte(s.mudHigh);
            b.writeByte(s.moist);
            b.writeByte(s.snow);
            b.writeByte(s.wet);
        }, b -> new Sync(b.readVarInt(), b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void sendTracking(Atv atv, Sync payload) {
        for (ServerPlayer p : AtvGrime.watchers(atv)) {
            send(p, payload);
        }
    }

    static void send(ServerPlayer p, CustomPacketPayload payload) {
        if (p.connection != null && p.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    // ================================================================== mod bus: payloads + particle types
    @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void payloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").optional().playToClient(Sync.TYPE, Sync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> AtvGrime.clientSync.accept(p)));
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            for (String id : PARTICLES) event.register(Registries.PARTICLE_TYPE, FrontierHunts.id(id), () -> new SimpleParticleType(false));
        }
    }

    // ================================================================== game bus: tracking + commands
    @EventBusSubscriber(modid = FrontierHunts.ID)
    public static final class GameEvents {
        private GameEvents() {}

        /** A player starts seeing an ATV: send its grime right away (the spawn packet went out just before). */
        @SubscribeEvent
        public static void startTracking(PlayerEvent.StartTracking event) {
            if (event.getTarget() instanceof Atv atv && event.getEntity() instanceof ServerPlayer sp) {
                atv.grime.ensureLoaded(atv);
                Sync s = atv.grime.snapshot(atv);
                if (s.mudLow() != 0 || s.mudHigh() != 0 || s.snow() != 0 || s.wet() != 0) send(sp, s);
            }
        }

        @SubscribeEvent
        public static void commands(RegisterCommandsEvent event) {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("atvgrime").executes(ctx -> query(ctx));
            root.then(Commands.literal("clean").requires(s -> s.hasPermission(2)).executes(ctx -> set(ctx, 0f, 0f, 0f, 0f, 0f)));
            root.then(Commands.literal("mud").requires(s -> s.hasPermission(2))
                    .then(Commands.argument("low", FloatArgumentType.floatArg(0f, 1f))
                            .executes(ctx -> mud(ctx, FloatArgumentType.getFloat(ctx, "low"), -1f, 1f))
                            .then(Commands.argument("high", FloatArgumentType.floatArg(0f, 1f))
                                    .executes(ctx -> mud(ctx, FloatArgumentType.getFloat(ctx, "low"), FloatArgumentType.getFloat(ctx, "high"), 1f))
                                    .then(Commands.argument("moisture", FloatArgumentType.floatArg(0f, 1f))
                                            .executes(ctx -> mud(ctx, FloatArgumentType.getFloat(ctx, "low"), FloatArgumentType.getFloat(ctx, "high"),
                                                    FloatArgumentType.getFloat(ctx, "moisture")))))));
            root.then(Commands.literal("snow").requires(s -> s.hasPermission(2))
                    .then(Commands.argument("amount", FloatArgumentType.floatArg(0f, 1f)).executes(ctx -> {
                        Atv a = target(ctx);
                        if (a == null) return 0;
                        AtvGrime g = a.grime;
                        return set(ctx, g.mudLow, g.mudHigh, g.moist, FloatArgumentType.getFloat(ctx, "amount"), g.wet);
                    })));
            root.then(Commands.literal("wet").requires(s -> s.hasPermission(2))
                    .then(Commands.argument("amount", FloatArgumentType.floatArg(0f, 1f)).executes(ctx -> {
                        Atv a = target(ctx);
                        if (a == null) return 0;
                        AtvGrime g = a.grime;
                        return set(ctx, g.mudLow, g.mudHigh, g.moist, g.snow, FloatArgumentType.getFloat(ctx, "amount"));
                    })));
            event.getDispatcher().register(root);
        }

        /** The ATV the player rides, else the nearest one within 8 blocks of the command source. */
        private static Atv target(CommandContext<CommandSourceStack> ctx) {
            CommandSourceStack src = ctx.getSource();
            Entity e = src.getEntity();
            if (e != null && e.getVehicle() instanceof Atv a) return a;
            List<Atv> list = src.getLevel().getEntitiesOfClass(Atv.class, new net.minecraft.world.phys.AABB(src.getPosition(), src.getPosition()).inflate(8.0));
            Atv best = null;
            double bd = Double.MAX_VALUE;
            for (Atv a : list) {
                double d = a.distanceToSqr(src.getPosition());
                if (d < bd) {
                    bd = d;
                    best = a;
                }
            }
            if (best == null) src.sendFailure(Component.literal("No ATV here: ride one or stand within 8 blocks of it."));
            return best;
        }

        private static int mud(CommandContext<CommandSourceStack> ctx, float low, float high, float moist) {
            Atv a = target(ctx);
            if (a == null) return 0;
            AtvGrime g = a.grime;
            return set(ctx, low, high < 0f ? Math.max(0f, low - 0.25f) : high, moist, g.snow, g.wet);
        }

        private static int set(CommandContext<CommandSourceStack> ctx, float low, float high, float moist, float snow, float wet) {
            Atv a = target(ctx);
            if (a == null) return 0;
            a.grime.force(a, low, high, moist, snow, wet);
            return query(ctx);
        }

        private static int query(CommandContext<CommandSourceStack> ctx) {
            Atv a = target(ctx);
            if (a == null) return 0;
            AtvGrime g = a.grime;
            g.ensureLoaded(a);
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "ATV grime: mud low %.2f (level %d), high %.2f (level %d), moisture %.2f, snow %.2f (level %d), wet %.2f",
                    g.mudLow, AtvGrime.mudLevel(g.mudLow), g.mudHigh, AtvGrime.mudLevel(g.mudHigh), g.moist, g.snow,
                    AtvGrime.snowLevel(g.snow), g.wet)), false);
            return 1;
        }
    }
}
