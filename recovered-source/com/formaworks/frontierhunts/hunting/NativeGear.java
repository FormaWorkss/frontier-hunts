package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class NativeGear {
   private static final String PACK = "frontier_worn_pack";
   private static final String QUIVER = "frontier_worn_quiver";

   public static boolean fitsPack(ItemStack var0) {
      if (var0.getItem() instanceof ExpeditionGear var1 && var1.id.equals("hunter_pack")) {
         return true;
      }

      return false;
   }

   public static boolean fitsQuiver(ItemStack var0) {
      return var0.getItem() instanceof QuiverItem;
   }

   private static void slot(ServerPlayer var0, int var1, ItemStack var2) {
      if (var0.isAlive() && !var0.isSpectator() && var1 >= 0 && var1 <= 1) {
         boolean var3 = var0.isCreative();
         if (var3 || var0.containerMenu == var0.inventoryMenu) {
            AbstractContainerMenu var4 = var0.containerMenu;
            ItemStack var5 = var3 ? var2.copy() : var4.getCarried();
            String var6 = var1 == 0 ? "frontier_worn_pack" : "frontier_worn_quiver";
            ItemStack var7 = read(var0, var6);
            if (var5.isEmpty() || (var1 == 0 ? fitsPack(var5) : fitsQuiver(var5))) {
               if (!var5.isEmpty() || !var7.isEmpty()) {
                  write(var0, var6, var5.isEmpty() ? ItemStack.EMPTY : var5.copyWithCount(1));
                  if (var3) {
                     if (!var7.isEmpty()) {
                        give(var0, var7);
                     }
                  } else {
                     ItemStack var8 = var5.isEmpty() ? ItemStack.EMPTY : var5.copyWithCount(var5.getCount() - 1);
                     if (var8.isEmpty()) {
                        var4.setCarried(var7);
                     } else {
                        var4.setCarried(var8);
                        if (!var7.isEmpty()) {
                           give(var0, var7);
                        }
                     }

                     var4.broadcastChanges();
                  }
               }
            }
         }
      }
   }

   private static ItemStack read(ServerPlayer var0, String var1) {
      CompoundTag var2 = var0.getPersistentData();
      return var2.contains(var1, 10) ? ItemStack.parseOptional(var0.registryAccess(), var2.getCompound(var1)) : ItemStack.EMPTY;
   }

   private static void write(ServerPlayer var0, String var1, ItemStack var2) {
      if (var2.isEmpty()) {
         var0.getPersistentData().remove(var1);
      } else {
         var0.getPersistentData().put(var1, var2.copyWithCount(1).save(var0.registryAccess()));
      }

      sync(var0);
   }

   public static ItemStack pack(Player var0) {
      return var0 instanceof ServerPlayer var1 ? read(var1, "frontier_worn_pack") : NativeGearClientHook.pack(var0.getId());
   }

   public static ItemStack quiver(Player var0) {
      return var0 instanceof ServerPlayer var1 ? read(var1, "frontier_worn_quiver") : NativeGearClientHook.quiver(var0.getId());
   }

   public static boolean equipPack(ServerPlayer var0, ItemStack var1) {
      if (!var1.isEmpty() && pack(var0).isEmpty()) {
         write(var0, "frontier_worn_pack", var1.copyWithCount(1));
         var1.shrink(1);
         return true;
      } else {
         return false;
      }
   }

   public static boolean equipQuiver(ServerPlayer var0, ItemStack var1) {
      if (!var1.isEmpty() && quiver(var0).isEmpty()) {
         write(var0, "frontier_worn_quiver", var1.copyWithCount(1));
         var1.shrink(1);
         return true;
      } else {
         return false;
      }
   }

   public static boolean unequipPack(ServerPlayer var0) {
      ItemStack var1 = pack(var0);
      if (var1.isEmpty()) {
         return false;
      } else {
         write(var0, "frontier_worn_pack", ItemStack.EMPTY);
         give(var0, var1);
         return true;
      }
   }

   public static boolean unequipQuiver(ServerPlayer var0) {
      ItemStack var1 = quiver(var0);
      if (var1.isEmpty()) {
         return false;
      } else {
         write(var0, "frontier_worn_quiver", ItemStack.EMPTY);
         give(var0, var1);
         return true;
      }
   }

   public static void updateQuiver(ServerPlayer var0, ItemStack var1) {
      if (!read(var0, "frontier_worn_quiver").isEmpty()) {
         write(var0, "frontier_worn_quiver", var1);
      }
   }

   private static void request(ServerPlayer var0, int var1) {
      if (var0.isAlive() && !var0.isSpectator() && (var0.containerMenu == var0.inventoryMenu || var0.isCreative())) {
         switch (var1) {
            case 0:
               ItemStack var5 = var0.getMainHandItem();
               if (var5.getItem() instanceof ExpeditionGear var3 && var3.id.equals("hunter_pack")) {
                  equipPack(var0, var5);
               }
               break;
            case 1:
               ItemStack var2 = var0.getMainHandItem();
               if (var2.getItem() instanceof QuiverItem) {
                  equipQuiver(var0, var2);
               }
               break;
            case 2:
               unequipPack(var0);
               break;
            case 3:
               unequipQuiver(var0);
         }
      }
   }

   private static void give(ServerPlayer var0, ItemStack var1) {
      if (!var0.getInventory().add(var1)) {
         var0.drop(var1, false);
      }
   }

   private static NativeGear.Sync snapshot(ServerPlayer var0) {
      return new NativeGear.Sync(var0.getId(), pack(var0), quiver(var0));
   }

   private static void sync(ServerPlayer var0) {
      if (var0.connection != null && var0.connection.hasChannel(NativeGear.Sync.TYPE)) {
         PacketDistributor.sendToPlayersTrackingEntityAndSelf(var0, snapshot(var0), new CustomPacketPayload[0]);
      }
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         sync(var1);
      }
   }

   @SubscribeEvent
   public static void tracking(StartTracking var0) {
      if (var0.getEntity() instanceof ServerPlayer var1
         && var0.getTarget() instanceof ServerPlayer var2
         && var1.connection != null
         && var1.connection.hasChannel(NativeGear.Sync.TYPE)) {
         PacketDistributor.sendToPlayer(var1, snapshot(var2), new CustomPacketPayload[0]);
      }
   }

   @SubscribeEvent
   public static void clone(Clone var0) {
      CompoundTag var1 = var0.getOriginal().getPersistentData();
      CompoundTag var2 = var0.getEntity().getPersistentData();

      for (String var6 : new String[]{"frontier_worn_pack", "frontier_worn_quiver"}) {
         if (var1.contains(var6, 10)) {
            var2.put(var6, var1.getCompound(var6).copy());
         }
      }
   }

   private NativeGear() {
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void payloads(RegisterPayloadHandlersEvent var0) {
         var0.registrar("1")
            .playToClient(NativeGear.Sync.TYPE, NativeGear.Sync.CODEC, (var0x, var1) -> var1.enqueueWork(() -> NativeGearClientHook.receive(var0x)));
         var0.registrar("1").playToServer(NativeGear.Slot.TYPE, NativeGear.Slot.CODEC, (var0x, var1) -> var1.enqueueWork(() -> {
               if (var1.player() instanceof ServerPlayer var2) {
                  NativeGear.slot(var2, var0x.which(), var0x.creativeCursor());
               }
            }));
         var0.registrar("1").playToServer(NativeGear.Request.TYPE, NativeGear.Request.CODEC, (var0x, var1) -> var1.enqueueWork(() -> {
               if (var1.player() instanceof ServerPlayer var2) {
                  NativeGear.request(var2, var0x.action());
               }
            }));
      }
   }

   public static record Request(int action) implements CustomPacketPayload {
      public static final int EQUIP_PACK = 0;
      public static final int EQUIP_QUIVER = 1;
      public static final int REMOVE_PACK = 2;
      public static final int REMOVE_QUIVER = 3;
      public static final Type<NativeGear.Request> TYPE = new Type(FrontierHunts.id("worn_gear_request"));
      public static final StreamCodec<FriendlyByteBuf, NativeGear.Request> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeVarInt(var1.action), var0 -> new NativeGear.Request(var0.readVarInt())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Slot(int which, ItemStack creativeCursor) implements CustomPacketPayload {
      public static final Type<NativeGear.Slot> TYPE = new Type(FrontierHunts.id("worn_gear_slot"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NativeGear.Slot> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.which);
         ItemStack.OPTIONAL_STREAM_CODEC.encode(var0, var1.creativeCursor);
      }, var0 -> new NativeGear.Slot(var0.readVarInt(), (ItemStack)ItemStack.OPTIONAL_STREAM_CODEC.decode(var0)));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Sync(int playerId, ItemStack pack, ItemStack quiver) implements CustomPacketPayload {
      public static final Type<NativeGear.Sync> TYPE = new Type(FrontierHunts.id("worn_gear"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NativeGear.Sync> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeVarInt(var1.playerId);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(var0, var1.pack);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(var0, var1.quiver);
         },
         var0 -> new NativeGear.Sync(
               var0.readVarInt(), (ItemStack)ItemStack.OPTIONAL_STREAM_CODEC.decode(var0), (ItemStack)ItemStack.OPTIONAL_STREAM_CODEC.decode(var0)
            )
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
