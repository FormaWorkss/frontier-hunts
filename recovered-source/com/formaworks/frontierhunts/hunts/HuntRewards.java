package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.camps.Quarry;
import com.formaworks.frontierhunts.camps.Tokens;
import com.formaworks.frontierhunts.journal.HuntBridge;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * [hunts] Pays a completed milestone: reserve tokens (the expedition stores' currency, works offline), the gear reward
 * (straight into the inventory, or queued in {@link HuntStore} until the hunter logs in), a field note, and for a master
 * milestone the species title, a chat announcement and a short fanfare. Journal XP is paid by the checklist itself.
 */
public final class HuntRewards {
   private static final Logger LOG = LogUtils.getLogger();

   private HuntRewards() {
   }

   public static int tokens(HuntBook.Milestone m) {
      return (int)Math.round(m.tokens() * HuntsConfig.tokenMultiplier());
   }

   public static void completed(MinecraftServer server, UUID id, ServerPlayer online, HuntBook.Milestone m) {
      try {
         com.formaworks.frontierhunts.progression.Durability.commit(server, "hunt milestone"); // [1.2.7] the reward and the checklist saved together
         int tokens = tokens(m);
         boolean pay = HuntsConfig.rewards();
         if (pay && tokens > 0) {
            Tokens.credit(server.overworld(), id, tokens);
         }
         boolean item = pay && !m.item().isEmpty() && m.count() > 0 && item(m.item()) != null;
         if (item) {
            if (online != null) {
               give(online, m.item(), m.count());
            } else {
               HuntStore.get(server).queue(id, new HuntStore.Pending(m.item(), m.count(), m.id()));
            }
         }
         if (online == null) {
            return;
         }
         if (pay && item) {
            HuntBridge.note(online, "@hunts.frontierhunts.note.reward|check:" + m.id() + "|" + tokens + "|" + m.count() + "|key:" + itemKey(m.item()), (byte)1);
         } else if (pay && tokens > 0) {
            HuntBridge.note(online, "@hunts.frontierhunts.note.reward_tokens|check:" + m.id() + "|" + tokens, (byte)1);
         }
         if (m.master()) {
            master(online, m);
         }
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts hunts: reward for {} failed", m.id(), e);
      }
   }

   private static void master(ServerPlayer p, HuntBook.Milestone m) {
      Quarry q = Quarry.find(m.species());
      HuntBridge.note(p, "@hunts.frontierhunts.note.master|" + m.species() + "|key:" + HuntBook.titleKey(m.species()), (byte)2);
      p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.5F, 1.1F);
      if (HuntsConfig.announceMasters() && q != null) {
         MutableComponent msg = Component.literal("[Journal] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xD8B25A)))
            .append(Component.translatable("hunts.frontierhunts.chat.master", p.getDisplayName(), Component.translatable("entity.frontierhunts." + q.id),
                  Component.translatable(HuntBook.titleKey(q.id)))
               .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xE9DFC6))));
         p.server.getPlayerList().broadcastSystemMessage(msg, false);
      }
   }

   /** Gives queued gear rewards (login). */
   public static void deliver(ServerPlayer p) {
      List<HuntStore.Pending> l = HuntStore.get(p.server).take(p.getUUID());
      for (HuntStore.Pending x : l) {
         give(p, x.item(), x.count());
      }
      if (!l.isEmpty()) {
         p.displayClientMessage(Component.translatable("hunts.frontierhunts.msg.delivered", l.size()), false);
         com.formaworks.frontierhunts.progression.Durability.commit(p.server, "queued rewards"); // [1.2.7] out of the queue and into the pack, saved together
      }
   }

   static void give(ServerPlayer p, String id, int count) {
      Item it = item(id);
      if (it == null) {
         return;
      }
      int left = Math.clamp(count, 1, 64);
      while (left > 0) {
         int n = Math.min(left, it.getDefaultMaxStackSize());
         ItemStack st = new ItemStack(it, n);
         if (!p.getInventory().add(st)) {
            p.drop(st, false);
         }
         left -= n;
      }
   }

   static Item item(String id) {
      ResourceLocation rl = ResourceLocation.tryParse(id);
      if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
         return null;
      }
      Item it = BuiltInRegistries.ITEM.get(rl);
      return it == Items.AIR ? null : it;
   }

   static String itemKey(String id) {
      Item it = item(id);
      return it == null ? id : it.getDescriptionId();
   }
}
