package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.CameraHub;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * [phone] The retired Camera Base Station (replaced by the Field Phone's Trail Cams app). Like the retired workbenches of
 * 1.3.0 its block and item stay registered so old worlds and inventories load, but it is gone from creative and has no
 * recipe, its tooltip says what replaced it ({@code phone.client.PhoneClient}), and a placed one keeps working: using it
 * opens the phone's Trail Cams app on the hunter's screen (even without a phone), with the hunter's own cameras.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class OldBaseStation {
   public static final String ID = "camera_base_station";

   private OldBaseStation() {
   }

   public static boolean is(BlockState s) {
      return s != null && s.getBlock() instanceof CameraHub;
   }

   public static boolean retired(Item item) {
      return item != null && item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).equals(FrontierHunts.id(ID));
   }

   static void hideFromCreative(BuildCreativeModeTabContentsEvent e) {
      // [1.4.0] the Field Camera retired too: the phone's Camera app replaced it
      for (String id : new String[]{ID, "field_camera"}) {
         Item it = BuiltInRegistries.ITEM.get(FrontierHunts.id(id));
         if (it == Items.AIR) {
            continue;
         }
         ItemStack s = new ItemStack(it);
         if (e.getParentEntries().contains(s) || e.getSearchEntries().contains(s)) {
            e.remove(s, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
         }
      }
   }

   /**
    * Using a placed base station opens the phone's Trail Cams for this station. Cancelled on both sides (the client must
    * not run the old block's own use, nor place a held block against it); the server then sends the phone open.
    * Sneaking with an item in hand still places against it, as before.
    */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void use(PlayerInteractEvent.RightClickBlock e) {
      BlockState st = e.getLevel().getBlockState(e.getPos());
      Player player = e.getEntity();
      if (!is(st) || player.isShiftKeyDown() && !player.getItemInHand(e.getHand()).isEmpty()) {
         return;
      }
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.SUCCESS);
      if (e.getLevel().isClientSide() || !(player instanceof ServerPlayer sp) || e.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }
      PhoneServer.openedStation(sp, e.getPos());
      if (PhoneServer.station(sp) == null) {
         return;
      }
      CompoundTag t = new CompoundTag();
      t.putString("app", "cams");
      t.putLong("station", e.getPos().asLong());
      PhoneNet.send(sp, PhoneNet.K_OPEN, t);
   }
}
