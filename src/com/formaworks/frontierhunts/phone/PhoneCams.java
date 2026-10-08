package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.expedition.CameraRegistry;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.expedition.LensView;
import com.formaworks.frontierhunts.expedition.TrailCameraBlock;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * [phone] Trail Cams on the server: the hunter's camera list (everything the Camera Base Station showed: name, battery,
 * frames on the card, live or logging, the best buck, the cover), clearing a card and the live lens view. The photos
 * themselves travel on the trail camera photo channel ({@code trailcam.TrailcamNet}), which accepts the phone as a
 * console ({@link #PHONE}) through {@link #mayUse}. Only the hunter's own cameras are ever listed or opened.
 */
public final class PhoneCams {
   /** the "console" a phone sends on the photo channel: never a real block (below every build limit) */
   public static final BlockPos PHONE = new BlockPos(0, -2048, 0);
   public static final int CAM_LIST = 0;
   public static final int CAM_CLEAR = 1;
   public static final int CAM_WATCH = 2;
   private static final int MAX = 64;

   private PhoneCams() {
   }

   /** The photo channel: may this player use their cameras from the phone right now? */
   public static boolean mayUse(ServerPlayer player) {
      return PhoneServer.mayUseCameras(player);
   }

   /** The cameras a player may see on the phone: their own, in this dimension, nearest first. */
   public static List<CameraRegistry.Station> mine(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      String dim = level.dimension().location().toString();
      CameraRegistry reg = CameraRegistry.get(level);
      List<CameraRegistry.Station> out = new ArrayList<>();
      for (CameraRegistry.Station s : reg.all()) {
         if (s.dimension.equals(dim) && player.getUUID().equals(s.owner)) {
            out.add(s);
         }
      }
      BlockPos at = player.blockPosition();
      out.sort(Comparator.comparingDouble(s -> s.pos.distSqr(at)));
      return out.size() > MAX ? new ArrayList<>(out.subList(0, MAX)) : out;
   }

   /** One of the player's own cameras at {@code pos} (this dimension), or null. */
   public static CameraRegistry.Station own(ServerPlayer player, BlockPos pos) {
      CameraRegistry.Station s = CameraRegistry.get(player.serverLevel()).find(player.level(), pos);
      return s != null && player.getUUID().equals(s.owner) ? s : null;
   }

   static void handle(ServerPlayer player, PhoneNet.Ask ask) {
      if (ask.op() == PhoneNet.OP_REFRESH) {
         sendList(player);
         return;
      }
      if (!mayUse(player)) {
         PhoneServer.toast(player, "No signal from the cameras here");
         return;
      }
      ServerLevel level = player.serverLevel();
      switch ((int)ask.a()) {
         case CAM_LIST -> sendList(player);
         case CAM_CLEAR -> {
            CameraRegistry.Station s = own(player, BlockPos.of(ask.b()));
            if (s != null) {
               s.roll.clear();
               CameraRegistry.get(level).touch();
               sendList(player);
            }
         }
         case CAM_WATCH -> {
            CameraRegistry.Station s = own(player, BlockPos.of(ask.b()));
            if (s == null) {
               return;
            }
            CameraRegistry.get(level).catchUp(level, s);
            if (s.charge <= 0) {
               PhoneServer.toast(player, s.label() + " has a flat battery");
               return;
            }
            LensView.close(player);
            LensView.open(player, s);
         }
         default -> {
         }
      }
   }

   static void sendList(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CameraRegistry reg = CameraRegistry.get(level);
      ListTag list = new ListTag();
      for (CameraRegistry.Station s : mine(player)) {
         boolean loaded = level.hasChunkAt(s.pos);
         if (loaded && !(level.getBlockState(s.pos).getBlock() instanceof TrailCameraBlock)) {
            reg.remove(level, s.pos);
            continue;
         }
         reg.catchUp(level, s);
         boolean best = false;
         int bestScore = 0;
         for (CameraRegistry.Capture c : s.roll) {
            if (c.traits().buck() && c.traits().trophyScore() > bestScore) {
               bestScore = c.traits().trophyScore();
               best = true;
            }
         }
         CompoundTag t = new CompoundTag();
         t.putLong("pos", s.pos.asLong());
         t.putString("name", clip(s.name, 40));
         t.putInt("pct", Math.max(0, Math.min(100, s.percent())));
         t.putInt("frames", Math.min(48, s.roll.size()));
         t.putBoolean("live", s.charge > 0);
         t.putBoolean("loaded", loaded);
         t.putBoolean("best", best);
         t.putInt("score", Math.min(10000, bestScore));
         t.putString("over", clip(s.over, 24));
         t.putLong("last", s.last());
         list.add(t);
      }
      CompoundTag tag = new CompoundTag();
      tag.put("cams", list);
      tag.putString("season", season(level));
      PhoneServer.Station st = PhoneServer.station(player);
      if (st != null) {
         tag.putLong("station", st.pos().asLong());
      }
      PhoneNet.send(player, PhoneNet.K_CAMS, tag);
   }

   static String season(ServerLevel level) {
      HuntingCalendar.Date d = HuntingCalendar.date(level);
      StringBuilder b = new StringBuilder(d.title());
      for (GameSpecies sp : GameSpecies.values()) {
         Rut.Phase p = Rut.phase(sp, d);
         if (p.active()) {
            b.append(" · ").append(sp.title).append(": ").append(p.title.toLowerCase(Locale.ROOT));
         }
      }
      return clip(b.toString(), 200);
   }

   static String clip(String s, int n) {
      return s == null ? "" : (s.length() > n ? s.substring(0, n) : s);
   }
}
