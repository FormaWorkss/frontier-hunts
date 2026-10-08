package com.formaworks.frontierhunts.weather;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server -> client weather state: the storms near this player, the wind and the blizzard visibility floor. */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class WeatherNetwork {
    public static final int MAX_STORMS = 24;

    private WeatherNetwork() {}

    public record Sync(ResourceLocation dimension, float windEast, float windSouth, float minVisibility, List<Storm> storms) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(FrontierHunts.id("weather_sync"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of(Sync::write, Sync::read);

        private static void write(FriendlyByteBuf buf, Sync s) {
            buf.writeResourceLocation(s.dimension);
            buf.writeFloat(s.windEast);
            buf.writeFloat(s.windSouth);
            buf.writeFloat(s.minVisibility);
            int n = Math.min(MAX_STORMS, s.storms.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) s.storms.get(i).write(buf);
        }

        private static Sync read(FriendlyByteBuf buf) {
            ResourceLocation dim = buf.readResourceLocation();
            float we = finite(buf.readFloat(), 30f), ws = finite(buf.readFloat(), 30f);
            float vis = Math.max(2f, Math.min(64f, finite(buf.readFloat(), 64f)));
            int n = Math.min(MAX_STORMS, Math.max(0, buf.readVarInt()));
            List<Storm> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(Storm.read(buf));
            return new Sync(dim, we, ws, vis, list);
        }

        private static float finite(float v, float lim) { return Float.isFinite(v) ? Math.max(-lim, Math.min(lim, v)) : 0f; }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Sync.TYPE, Sync.CODEC, (s, ctx) -> ctx.enqueueWork(() -> SeasonalWeather.acceptSync(s)));
    }
}
