package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.hunting.Whitetail;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * [ecology] A deer, elk or moose killed by predators: using it reads the kill site instead of starting the skinning;
 * with the skinning knife you salvage what the predators left (some meat, the hide if little was eaten) and never a
 * trophy.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class EcologyEvents {
   private EcologyEvents() {
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void interact(PlayerInteractEvent.EntityInteract e) {
      if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getTarget() instanceof Whitetail w) || !w.downed()
         || !(e.getLevel() instanceof ServerLevel level)) {
         return;
      }
      KillRecord r = KillSites.record(w);
      if (r == null) {
         return;
      }
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.SUCCESS);
      Player p = e.getEntity();
      if (p.getMainHandItem().is(HuntContent.SKINNING_TOOL.get()) && p.distanceToSqr(w) < 16.0) {
         salvage(level, w, r, p);
      } else {
         KillSites.read(p, r, level.getGameTime());
      }
   }

   private static void salvage(ServerLevel level, Whitetail w, KillRecord r, Player p) {
      Predator k = r.predatorKind();
      String who = k == null ? "predators" : k.title().toLowerCase(java.util.Locale.ROOT) + (r.pack > 1 ? "s" : "");
      if (r.salvaged) {
         p.displayClientMessage(Component.literal("Nothing worth taking is left on it.").withStyle(ChatFormatting.GRAY), true);
         return;
      }
      if (r.fed > 0.65F || level.getGameTime() - r.killedAt > 30000L) {
         p.displayClientMessage(Component.literal("Too far gone · the " + who + " have had most of it").withStyle(ChatFormatting.GRAY), true);
         return;
      }
      int meat = Math.max(1, Math.round((1.0F - r.fed) * (w.massKg() / 30.0F)));
      meat = Math.min(meat, 6);
      ItemStack[] drops = r.fed < 0.35F
         ? new ItemStack[]{new ItemStack(HuntContent.VENISON.get(), meat), new ItemStack(HuntContent.DEER_HIDE.get(), 1)}
         : new ItemStack[]{new ItemStack(HuntContent.VENISON.get(), meat)};
      for (ItemStack s : drops) {
         ItemEntity it = w.spawnAtLocation(s);
         if (it != null) {
            it.setTarget(p.getUUID());
            it.setNoPickUpDelay();
         }
      }
      r.salvaged = true;
      KillSites.record(w, r);
      p.getMainHandItem().hurtAndBreak(1, p, EquipmentSlot.MAINHAND);
      p.displayClientMessage(Component.literal("Salvaged what the " + who + " left · no trophy from a predator kill").withStyle(ChatFormatting.GRAY), true);
   }
}
