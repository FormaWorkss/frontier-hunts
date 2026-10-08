package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.camps.CampRegistry;
import com.formaworks.frontierhunts.expedition.CameraRegistry;
import com.formaworks.frontierhunts.livingworld.SpawnSite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * [phone] Places for Maps: the hunter's camp post, the ranger check station, the reserve's spawn, the bed they respawn
 * at, their trail cameras and their own pins (saved per world in {@link PhoneStore}). The beginner area of the first
 * hunt comes from the first-hunt state the client already has. Only places in the hunter's dimension are sent.
 */
public final class PhonePlaces {
   private PhonePlaces() {
   }

   private static void put(ListTag list, String id, String kind, String name, BlockPos p, boolean removable, String note) {
      if (list.size() >= 96) {
         return;
      }
      CompoundTag t = new CompoundTag();
      t.putString("id", id);
      t.putString("kind", kind);
      t.putString("name", PhoneCams.clip(name, 40));
      t.putInt("x", p.getX());
      t.putInt("y", p.getY());
      t.putInt("z", p.getZ());
      t.putBoolean("rm", removable);
      t.putString("note", PhoneCams.clip(note, 60));
      list.add(t);
   }

   static void send(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      String dim = level.dimension().location().toString();
      ListTag list = new ListTag();
      // camp
      try {
         CampRegistry.Camp camp = CampRegistry.get(player.server).campOf(player.getUUID());
         if (camp != null && camp.post != null && dim.equals(camp.postDim)) {
            put(list, "camp:" + camp.id, "camp", camp.name, camp.post, false, "Camp post · tier " + camp.tier);
         }
      } catch (RuntimeException e) {
         // camps are optional
      }
      // ranger check station near spawn, and the spawn itself
      if (level.dimension().equals(Level.OVERWORLD)) {
         try {
            SpawnSite.Data site = SpawnSite.Data.get(level);
            if (site.pos != null) {
               put(list, "ranger:spawn", "ranger", "Ranger check station", site.pos, false, "Licences, tags and the Frontier Handbook");
            }
         } catch (RuntimeException e) {
            // older worlds have no check station
         }
         put(list, "home:spawn", "lodge", "Reserve gate", level.getSharedSpawnPos(), false, "World spawn");
      }
      // the bed
      BlockPos bed = player.getRespawnPosition();
      if (bed != null && player.getRespawnDimension().equals(level.dimension())) {
         put(list, "home:bed", "home", "Your bed", bed, false, "Where you wake up");
      }
      // where the hunter last took an animal (to walk back for the recovery)
      PhoneStore.Hunter me = PhoneStore.get(player.server).hunter(player.getUUID(), player.getScoreboardName());
      if (dim.equals(me.killDim)) {
         put(list, "kill:last", "kill", "Last harvest", BlockPos.of(me.killPos), false, me.killWhat);
      }
      // trail cameras
      for (CameraRegistry.Station s : PhoneCams.mine(player)) {
         put(list, "cam:" + s.pos.asLong(), "cam", s.label(), s.pos, false, s.percent() + "% · " + s.roll.size() + (s.roll.size() == 1 ? " photo" : " photos"));
      }
      // pins
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunter(player.getUUID(), player.getScoreboardName());
      for (PhoneStore.Pin p : h.pins) {
         if (dim.equals(p.dim)) {
            put(list, "pin:" + p.id, "pin" + Math.max(0, Math.min(9, p.icon)), p.name, new BlockPos(p.x, p.y, p.z), true, "");
         }
      }
      CompoundTag t = new CompoundTag();
      t.put("places", list);
      PhoneNet.send(player, PhoneNet.K_PLACES, t);
   }

   static void addPin(ServerPlayer player, String raw, int icon) {
      String name = clean(raw);
      if (name.isEmpty()) {
         name = "Pin";
      }
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunter(player.getUUID(), player.getScoreboardName());
      if (h.pins.size() >= PhoneStore.MAX_PINS) {
         PhoneServer.toast(player, "Your map is full: remove a pin first");
         return;
      }
      PhoneStore.Pin p = new PhoneStore.Pin();
      p.id = h.nextPin++;
      p.name = name;
      p.icon = Math.max(0, Math.min(9, icon));
      BlockPos at = player.blockPosition();
      p.x = at.getX();
      p.y = at.getY();
      p.z = at.getZ();
      p.dim = player.level().dimension().location().toString();
      h.pins.add(p);
      store.setDirty();
      send(player);
   }

   static void removePin(ServerPlayer player, String id) {
      if (id == null || !id.startsWith("pin:")) {
         return;
      }
      int n;
      try {
         n = Integer.parseInt(id.substring(4));
      } catch (NumberFormatException e) {
         return;
      }
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunter(player.getUUID(), player.getScoreboardName());
      if (h.pins.removeIf(p -> p.id == n)) {
         store.setDirty();
      }
      send(player);
   }

   /** A pin or message name: printable, trimmed, at most {@code 32} characters. */
   static String clean(String raw) {
      return clean(raw, 32);
   }

   static String clean(String raw, int max) {
      if (raw == null) {
         return "";
      }
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < raw.length() && b.length() < max; i++) {
         char c = raw.charAt(i);
         if (c == '§' || Character.isISOControl(c) || Character.isSurrogate(c)) {
            continue;
         }
         b.append(c);
      }
      return b.toString().trim();
   }
}
