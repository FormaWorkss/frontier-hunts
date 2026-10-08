package com.formaworks.frontierhunts.freak;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.camps.Tokens;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.journal.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * [1.2.0] The legends are the end of the road, and taking one is the biggest thing that happens on the reserve.
 *
 * <p>Each legend (legal, tagged):
 * <ul>
 * <li>{@value #LEGEND_TOKENS} reserve tokens and a lot of Journal XP;</li>
 * <li>a legendary trophy: named for him, gleaming, with his real rack (mount it: it's that animal, one of a kind);</li>
 * <li>a standing {@value #LEGEND_DISCOUNT}% off at the licence counter, for good (on top of the rank discount);</li>
 * <li>a challenge advancement, the title across the screen, and the whole server hears it.</li>
 * </ul>
 * All three: <b>Master of the Reserve</b> - the last step of the Hunter's Path. {@value #MASTER_TOKENS} tokens, everything
 * at the licence counter free for life (licence, tags, stamps, reports), a gold star by your name, and the reserve's
 * thanks.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class LegendRewards {
   private LegendRewards() {
   }

   public static final int LEGEND_TOKENS = 500, MASTER_TOKENS = 2000, LEGEND_DISCOUNT = 5;

   /** legends this hunter has taken (0..3) */
   public static int taken(ServerPlayer p) {
      int n = 0;
      for (String sp : FreakQuest.SPECIES) {
         n += FreakQuest.stage(p, sp) >= FreakQuest.DONE ? 1 : 0;
      }
      return n;
   }

   public static boolean master(ServerPlayer p) {
      try {
         return taken(p) >= 3;
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** extra percent off at the licence counter from legends taken */
   public static int discount(ServerPlayer p) {
      try {
         return taken(p) * LEGEND_DISCOUNT;
      } catch (RuntimeException e) {
         return 0;
      }
   }

   /** FreakQuest.harvested: a legend was taken legally */
   static void taken(ServerPlayer p, String sp, String title, int kg, int points) {
      Tokens.credit(p.serverLevel(), p.getUUID(), LEGEND_TOKENS);
      JournalApi.xp(p, Skill.TRACKING, 800);
      JournalApi.xp(p, Skill.MARKSMANSHIP, 800);
      JournalApi.xp(p, Skill.STALKING, 600);
      String name = capital(title);
      String text = "You took " + name + " - " + kg + " kg" + (points > 0 ? ", " + points + " points" : "")
         + ". The reserve will talk about this one for years. +" + LEGEND_TOKENS + " tokens, and " + LEGEND_DISCOUNT
         + "% off at the licence counter for good.";
      p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
      JournalApi.note(p, text);
      award(p, "legend/" + key(sp));
      show(p, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
         Component.literal("Legend of the Reserve · taken").withStyle(ChatFormatting.YELLOW));
      celebrate(p);
      p.server.getPlayerList().broadcastSystemMessage(Component.literal("[Reserve] " + p.getGameProfile().getName() + " took " + title + ", a Legend of the Reserve.")
         .withStyle(ChatFormatting.GOLD), false);
      if (taken(p) >= 3 && JournalApi.counter(p, "freak.master") == 0) {
         JournalApi.count(p, "freak.master", 1);
         master(p, name);
      }
   }

   private static void master(ServerPlayer p, String last) {
      Tokens.credit(p.serverLevel(), p.getUUID(), MASTER_TOKENS);
      award(p, "legend/master_of_the_reserve");
      p.refreshDisplayName();
      p.refreshTabListName();
      // the title comes up after the legend's own
      p.server.tell(new net.minecraft.server.TickTask(p.server.getTickCount() + 120, () -> {
         if (!p.isRemoved()) {
            show(p, Component.literal("Master of the Reserve").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
               Component.literal("The Old Ridge Buck · the Ghost Bull · the Bog King").withStyle(ChatFormatting.YELLOW));
            celebrate(p);
         }
      }));
      String text = "Master of the Reserve. All three legends - the Old Ridge Buck, the Ghost Bull and the Bog King - taken fair, by you. +"
         + MASTER_TOKENS + " tokens. Everything at the licence counter is free for you now: licence, tags, stamps and reports.";
      p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
      JournalApi.note(p, text);
      p.sendSystemMessage(Component.literal("Thank you for playing Frontier Hunts to the end of the trail. More country, game and legends are coming (Journal > Coming Soon). ")
         .withStyle(ChatFormatting.GRAY)
         .append(Component.literal("Support it on Ko-fi").withStyle(st -> st.withColor(ChatFormatting.GOLD).withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://ko-fi.com/formaworks26954")))));
      p.server.getPlayerList().broadcastSystemMessage(Component.literal("[Reserve] " + p.getGameProfile().getName()
         + " is a Master of the Reserve: all three legends taken.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
   }

   private static String key(String sp) {
      return switch (sp) {
         case "elk" -> "ghost_bull";
         case "moose" -> "bog_king";
         default -> "old_ridge_buck";
      };
   }

   private static String capital(String s) {
      return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   static void award(ServerPlayer p, String path) {
      try {
         var holder = p.server.getAdvancements().get(FrontierHunts.id(path));
         if (holder != null) {
            for (String c : holder.value().criteria().keySet()) {
               p.getAdvancements().award(holder, c);
            }
         }
      } catch (RuntimeException ignored) {
      }
   }

   static void show(ServerPlayer p, Component title, Component subtitle) {
      p.connection.send(new ClientboundSetTitlesAnimationPacket(15, 90, 30));
      p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
      p.connection.send(new ClientboundSetTitleTextPacket(title));
   }

   static void celebrate(ServerPlayer p) {
      p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 0.9F);
      p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 90, 0.6, 0.9, 0.6, 0.35);
   }

   /** Whitetail.harvest: the legend's trophy - named, gleaming, epic */
   public static void trophy(ItemStack st, String sp, int kg, int points) {
      String title = switch (sp) {
         case "elk" -> "The Ghost Bull";
         case "moose" -> "The Bog King";
         default -> "The Old Ridge Buck";
      };
      st.set(DataComponents.CUSTOM_NAME, Component.literal("★ " + title + " ★ · " + kg + " kg" + (points > 0 ? " · " + points + " points" : ""))
         .withStyle(ChatFormatting.GOLD));
      st.set(DataComponents.RARITY, Rarity.EPIC);
      st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
   }

   /** a gold star before the name of a Master of the Reserve (chat and the player list) */
   @SubscribeEvent
   public static void name(PlayerEvent.NameFormat e) {
      if (e.getEntity() instanceof ServerPlayer p && master(p)) {
         e.setDisplayname(Component.literal("★ ").withStyle(ChatFormatting.GOLD).append(e.getDisplayname()));
      }
   }

   @SubscribeEvent
   public static void tabName(PlayerEvent.TabListNameFormat e) {
      if (e.getEntity() instanceof ServerPlayer p && master(p)) {
         e.setDisplayName(Component.literal("★ ").withStyle(ChatFormatting.GOLD).append(p.getName()));
      }
   }
}
