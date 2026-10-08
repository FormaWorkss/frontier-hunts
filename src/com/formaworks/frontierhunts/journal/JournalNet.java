package com.formaworks.frontierhunts.journal;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * [journal] Journal payloads. The server owns every number; the client asks for its own record when the journal opens
 * and gets pushed updates (at most once a second) while it stays open, plus small notices for toasts and the XP ticker.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class JournalNet {
   public static Consumer<Data> dataReceiver = d -> {
   };
   public static Consumer<Notice> noticeReceiver = n -> {
   };
   public static Consumer<Perks> perksReceiver = p -> {
   };

   // Ask actions
   public static final byte A_OPEN = 0;
   public static final byte A_CLOSE = 1;
   public static final byte A_PRINT = 2;

   // Notice kinds
   public static final byte N_XP = 0;
   public static final byte N_RANK = 1;
   public static final byte N_LEVEL = 2;
   public static final byte N_PERK = 3;
   public static final byte N_CHECK = 4;
   public static final byte N_SUMMARY = 5;

   private JournalNet() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent e) {
      PayloadRegistrar r = e.registrar("1");
      r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            JournalService.ask(sp, p.action(), p.arg());
         }
      }));
      r.playToClient(Data.TYPE, Data.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dataReceiver.accept(p)));
      r.playToClient(Notice.TYPE, Notice.CODEC, (p, ctx) -> ctx.enqueueWork(() -> noticeReceiver.accept(p)));
      r.playToClient(Perks.TYPE, Perks.CODEC, (p, ctx) -> ctx.enqueueWork(() -> perksReceiver.accept(p)));
   }

   static void send(ServerPlayer p, CustomPacketPayload payload) {
      if (p.connection != null && p.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(p, payload, new CustomPacketPayload[0]);
      }
   }

   public record Ask(byte action, int arg) implements CustomPacketPayload {
      public static final Type<Ask> TYPE = new Type<>(FrontierHunts.id("journal_ask"));
      public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((b, a) -> {
         b.writeByte(a.action);
         b.writeVarInt(a.arg);
      }, b -> new Ask(b.readByte(), b.readVarInt()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** One extra page's data (see {@link JournalPages}). */
   public record PageData(String id, CompoundTag tag) {
   }

   /**
    * The owner's record plus the bits of other ledgers the journal shows: Mara's campaign stage/progress, the active
    * ranger contract (state 0 none, 1 active, 2 ready, 3 expired) and the token balance.
    */
   public record Data(HunterRecord record, int campaignStage, int campaignCount, int contract, int contractCount, int contractState, int tokens,
      List<PageData> pages) implements CustomPacketPayload {
      public static final Type<Data> TYPE = new Type<>(FrontierHunts.id("journal_data"));
      public static final StreamCodec<FriendlyByteBuf, Data> CODEC = StreamCodec.of((b, d) -> {
         d.record.writeSync(b);
         b.writeVarInt(d.campaignStage);
         b.writeVarInt(d.campaignCount);
         b.writeVarInt(d.contract + 1);
         b.writeVarInt(d.contractCount);
         b.writeVarInt(d.contractState);
         b.writeVarInt(d.tokens);
         b.writeVarInt(d.pages.size());
         for (PageData pd : d.pages) {
            b.writeUtf(pd.id, 32);
            b.writeNbt(pd.tag);
         }
      }, b -> {
         HunterRecord r = HunterRecord.readSync(b);
         int stage = b.readVarInt();
         int count = b.readVarInt();
         int contract = b.readVarInt() - 1;
         int cc = b.readVarInt();
         int cs = b.readVarInt();
         int tokens = b.readVarInt();
         int pn = b.readVarInt();
         if (pn < 0 || pn > 16 || stage < 0 || stage > 64 || cs < 0 || cs > 3) {
            throw new IllegalArgumentException("Invalid journal data");
         }
         List<PageData> pages = new ArrayList<>(pn);
         for (int i = 0; i < pn; i++) {
            String id = b.readUtf(32);
            CompoundTag tag = (CompoundTag)b.readNbt(NbtAccounter.create(65536L));
            pages.add(new PageData(id, tag == null ? new CompoundTag() : tag));
         }
         return new Data(r, stage, count, contract, cc, cs, tokens, List.copyOf(pages));
      });

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /**
    * Toast / ticker notice. N_XP: a = skill ordinal (-1 checklist), b = amount. N_RANK: a = rank. N_LEVEL: a = skill,
    * b = level. N_PERK: a = perk. N_CHECK: id = entry, b = xp. N_SUMMARY: id = title.
    */
   public record Notice(byte kind, String id, int a, int b) implements CustomPacketPayload {
      public static final Type<Notice> TYPE = new Type<>(FrontierHunts.id("journal_notice"));
      public static final StreamCodec<FriendlyByteBuf, Notice> CODEC = StreamCodec.of((buf, n) -> {
         buf.writeByte(n.kind);
         buf.writeUtf(n.id, 64);
         buf.writeVarInt(n.a + 1);
         buf.writeVarInt(n.b);
      }, buf -> new Notice(buf.readByte(), buf.readUtf(64), buf.readVarInt() - 1, buf.readVarInt()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** The owner's active perks (after the server's disabled list) and the server's perk strength. */
   public record Perks(int mask, float strength) implements CustomPacketPayload {
      public static final Type<Perks> TYPE = new Type<>(FrontierHunts.id("journal_perks"));
      public static final StreamCodec<FriendlyByteBuf, Perks> CODEC = StreamCodec.of((b, p) -> {
         b.writeVarInt(p.mask);
         b.writeFloat(p.strength);
      }, b -> {
         int m = b.readVarInt();
         float s = b.readFloat();
         return new Perks(m, Float.isFinite(s) ? Math.clamp(s, 0.0F, 2.0F) : 1.0F);
      });

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
