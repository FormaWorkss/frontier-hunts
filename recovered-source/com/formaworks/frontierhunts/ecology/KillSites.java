package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.ecology.bones.BoneSites;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** [ecology] Reading a kill site, the kill record on a downed deer, and what is left when a carcass has rotted away. */
public final class KillSites {
   static final String DEER_TAG = "frontierhunts_kill";

   private KillSites() {
   }

   // ---------------------------------------------------------------- deer carcasses (Whitetail persistent data)

   public static KillRecord record(Whitetail w) {
      CompoundTag t = w.getPersistentData();
      return t.contains(DEER_TAG, 10) ? KillRecord.load(t.getCompound(DEER_TAG)) : null;
   }

   public static void record(Whitetail w, KillRecord r) {
      w.getPersistentData().put(DEER_TAG, r.save());
   }

   public static boolean predatorKill(Whitetail w) {
      return w.getPersistentData().contains(DEER_TAG, 10);
   }

   // ---------------------------------------------------------------- reading

   static String age(long ticks) {
      if (ticks < 1500L) {
         return "fresh, under an hour old";
      } else if (ticks < 6000L) {
         return "a few hours old";
      } else if (ticks < 14000L) {
         return "half a day old";
      } else if (ticks < 30000L) {
         return "about a day old";
      } else if (ticks < 54000L) {
         return "nearly two days old";
      }
      return "several days old";
   }

   static String eaten(float fed) {
      if (fed < 0.12F) {
         return "barely touched";
      } else if (fed < 0.35F) {
         return "partly eaten";
      } else if (fed < 0.65F) {
         return "half eaten";
      } else if (fed < 0.88F) {
         return "mostly eaten";
      }
      return "picked nearly clean";
   }

   static String who(Predator p, int pack) {
      if (p == null) {
         return "";
      }
      if (pack <= 1) {
         return p.cat() || p.bear() ? "a lone " + p.title().toLowerCase(java.util.Locale.ROOT) : "a single " + p.title().toLowerCase(java.util.Locale.ROOT);
      }
      return switch (p) {
         case WOLF -> "pack of " + pack;
         case COYOTE -> "a pair of coyotes";
         case LION -> pack + " lions";
         default -> pack + " " + p.title().toLowerCase(java.util.Locale.ROOT) + "s";
      };
   }

   static String how(Predator p) {
      if (p == null) {
         return "Something killed it here; the ground is torn up";
      }
      return switch (p) {
         case WOLF -> "Torn at the hindquarters and flank, then opened up; the ground is trampled with wolf prints";
         case COYOTE -> "Bitten at the throat; small canid prints all around, hair scattered";
         case COUGAR, PANTHER -> "A bite to the back of the neck, claw rakes on the shoulders; fed on from the chest, big cat prints";
         case LION -> "Throat bite, claw rakes; prints of big cats, more than one";
         case CHEETAH -> "Tripped at speed and bitten at the throat; long skid marks where it went down";
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> "Knocked down and fed on where it fell; the ground is clawed up";
      };
   }

   /** one short line for the hair tufts at the site (TrailMark individual, max 80 chars) */
   public static String tuft(Predator p, int pack, String prey) {
      String s = (p == null ? "Predator" : p.title()) + " kill · " + prey + (p != null && pack > 1 ? " · " + who(p, pack) : "");
      return s.length() > 80 ? s.substring(0, 80) : s;
   }

   /** Inspection: what a hunter reads from the carcass (three lines in chat). */
   public static void read(Player player, KillRecord r, long now) {
      Predator p = r.predatorKind();
      String head = (p == null ? "KILL SITE" : p.title().toUpperCase(java.util.Locale.ROOT) + " KILL") + " · " + r.prey;
      String w = who(p, r.pack);
      String second = (w.isEmpty() ? "" : capital(w) + " · ") + age(Math.max(0L, now - r.killedAt)) + " · " + eaten(r.fed);
      String third = how(p) + (r.visitors.isEmpty() ? "" : " · scavenged since by " + r.visitors);
      player.displayClientMessage(Component.literal(head).withStyle(ChatFormatting.GOLD), false);
      player.displayClientMessage(Component.literal(second).withStyle(ChatFormatting.GRAY), false);
      player.displayClientMessage(Component.literal(third).withStyle(ChatFormatting.DARK_GRAY), false);
   }

   private static String capital(String s) {
      return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   // ---------------------------------------------------------------- decay

   /** The carcass has rotted away: forget the site and leave its bones (skull, ribs, legs) where it lay. */
   public static void decayed(ServerLevel level, Vec3 at, KillRecord r, UUID carcass) {
      KillSiteStore.of(level).removeSite(carcass);
      Prey.Kind k = r == null ? null : r.preyKind();
      if (k == null || k.small() || !EcologyConfig.killBones() || at == null || !Double.isFinite(at.lengthSqr())) {
         return;
      }
      BlockPos pos = BlockPos.containing(at.x, at.y + 0.2, at.z);
      if (!level.isLoaded(pos)) {
         return;
      }
      BoneSites.place(level, pos, BoneSites.Kind.of(k), false, r.antlers, level.random, 3);
   }

   /**
    * Whitetail hook (downed tick, every 20 ticks): a predator-killed deer lies for the configured time of world time,
    * then turns into bones. Returns true when the body should go now.
    */
   public static boolean deerExpired(Whitetail w) {
      KillRecord r = record(w);
      if (r == null || !(w.level() instanceof ServerLevel level)) {
         return false;
      }
      long age = level.getGameTime() - r.killedAt;
      if (age > EcologyConfig.carcassTicks() || age < -24000L) {
         decayed(level, w.position(), r, w.getUUID());
         return true;
      }
      return false;
   }
}
