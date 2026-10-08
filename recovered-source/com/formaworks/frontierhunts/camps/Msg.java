package com.formaworks.frontierhunts.camps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;

/** Chat, title and sound helpers with the mod's warm "lodge" colours. */
public final class Msg {
   public static final int BRASS = 0xD8B25A;
   public static final int PAPER = 0xE9DFC6;
   public static final int MOSS = 0x9DB37A;
   public static final int RUST = 0xD0764E;
   public static final int MUTED = 0xA99C82;

   private Msg() {
   }

   public static MutableComponent text(String s, int rgb) {
      return Component.literal(s).withStyle(Style.EMPTY.withColor(rgb));
   }

   public static MutableComponent tag(String label, int rgb) {
      return Component.literal("[" + label + "] ").withStyle(Style.EMPTY.withColor(rgb).withBold(true));
   }

   /** Camp-coloured "[Camp Name] " prefix. */
   public static MutableComponent camp(CampRegistry.Camp c) {
      return tag(c.name, dyeText(c.color));
   }

   public static int dyeText(int color) {
      DyeColor d = DyeColor.byId(Math.floorMod(color, 16));
      int rgb = d.getTextColor();
      // very dark dyes are unreadable in chat: lift them
      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      if (r + g + b < 180) {
         r = Math.min(255, r + 90);
         g = Math.min(255, g + 90);
         b = Math.min(255, b + 90);
      }
      return r << 16 | g << 8 | b;
   }

   public static MutableComponent button(String label, String command, String hover, int rgb) {
      return Component.literal("[" + label + "]")
         .withStyle(
            Style.EMPTY
               .withColor(rgb)
               .withBold(true)
               .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
               .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover)))
         );
   }

   public static void tell(ServerPlayer p, Component c) {
      if (p != null) {
         p.sendSystemMessage(c);
      }
   }

   public static void bar(ServerPlayer p, String s) {
      if (p != null) {
         p.displayClientMessage(text(s, PAPER), true);
      }
   }

   public static void broadcast(MinecraftServer server, Component c) {
      server.getPlayerList().broadcastSystemMessage(c, false);
   }

   public static void title(ServerPlayer p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
      if (p == null || p.connection == null) {
         return;
      }
      p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
      p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
      p.connection.send(new ClientboundSetTitleTextPacket(title));
   }

   public static void sound(ServerPlayer p, SoundEvent s, float volume, float pitch) {
      if (p != null && s != null) {
         p.playNotifySound(s, SoundSource.PLAYERS, volume, pitch);
      }
   }

   public static MutableComponent gray(String s) {
      return Component.literal(s).withStyle(ChatFormatting.GRAY);
   }
}
