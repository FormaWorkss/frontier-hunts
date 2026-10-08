package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.HunterLedger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * [phone] The Field Phone's Licence & Tags wallet: the same facts as the journal's Licence & Tags page (what the hunter
 * bought this licence season and carries, bag limits, the stamps and today's birds, which seasons are open now, the
 * warden record and the last filled tags), as one small record for the phone.
 */
public final class LicencePhone {
   private LicencePhone() {
   }

   public static CompoundTag wallet(ServerPlayer p) {
      CompoundTag t = new CompoundTag();
      LicenceConfig.Mode mode = LicenceConfig.mode();
      t.putString("rules", mode.title);
      int period = LicenceTime.period(p.level());
      int month = LicenceTime.month(p.level());
      t.putString("period", Regulations.periodTitle(period));
      t.putString("month", Regulations.monthName(month));
      t.putInt("daysLeft", LicenceTime.daysLeft(p.level()));
      LicenceStore.Hunter h = LicenceStore.get(p.server).hunter(p.getUUID());
      LicenceStore.Season s = h.season(period);
      t.putBoolean("licence", s.licence);
      t.putBoolean("licenceHeld", Tagging.find(p, LicenceContent.LICENCE.get(), period) >= 0);
      t.putBoolean("suspended", s.suspended || h.revoked(period));
      t.putInt("tokens", Math.max(0, HunterLedger.get(p.serverLevel()).hunter(p.getUUID()).tokens()));
      ListTag tags = new ListTag();
      for (Regulations.TagKind k : Regulations.TagKind.values()) {
         CompoundTag c = new CompoundTag();
         c.putString("name", title(k.key()) + " tag");
         c.putString("item", "frontierhunts:" + k.item);
         c.putInt("color", k.color);
         c.putInt("held", held(p, LicenceContent.tag(k), period));
         c.putInt("bought", s.tags[k.ordinal()]);
         c.putInt("bag", k.bag + LicenceOffice.bonusTags(p, k));
         int months = Regulations.months(k);
         boolean open = (months >> Math.floorMod(month, 12) & 1) != 0;
         c.putString("months", Regulations.months(months) + (open ? " · open now" : ""));
         c.putBoolean("open", open);
         tags.add(c);
      }
      t.put("tags", tags);
      ListTag stamps = new ListTag();
      long day = LicenceTime.day(p.level());
      for (Regulations.Stamp st : Regulations.Stamp.values()) {
         CompoundTag c = new CompoundTag();
         c.putString("name", title(st.key()) + " stamp");
         c.putString("item", "frontierhunts:" + st.item);
         c.putBoolean("held", Tagging.find(p, LicenceContent.stamp(st), period) >= 0);
         int birds = 0;
         for (Regulations.Rule r : Regulations.covered(st)) {
            birds += h.birds(day, r.species());
         }
         c.putString("today", birds + " / " + st.daily + " birds today");
         int months = Regulations.months(st);
         boolean open = (months >> Math.floorMod(month, 12) & 1) != 0;
         c.putString("months", Regulations.months(months) + (open ? " · open now" : ""));
         c.putBoolean("open", open);
         stamps.add(c);
      }
      t.put("stamps", stamps);
      ListTag openNow = new ListTag();
      for (Regulations.Rule r : Regulations.all()) {
         if (r.regulated() && r.open(month) && openNow.size() < 40) {
            openNow.add(StringTag.valueOf(r.title()));
         }
      }
      t.put("open", openNow);
      t.putInt("violations", s.violations);
      t.putInt("strikes", s.strikes);
      t.putInt("fines", s.fines);
      t.putInt("filled", s.filled);
      t.putInt("record", h.record(period));
      ListTag log = new ListTag();
      for (FilledTag f : h.log) {
         if (log.size() >= 24) {
            break;
         }
         CompoundTag c = new CompoundTag();
         c.putString("species", f.species());
         c.putString("title", f.title() + (f.points() > 0 ? " · " + f.points() + " points" : ""));
         c.putString("detail", f.weight());
         c.putString("when", f.date());
         c.putString("where", f.place());
         c.putBoolean("legal", f.legal());
         log.add(c);
      }
      t.put("trophies", log);
      return t;
   }

   private static int held(ServerPlayer p, net.minecraft.world.item.Item item, int period) {
      int n = 0;
      var inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.is(item)) {
            PermitData d = s.get(LicenceContent.PERMIT.get());
            if (d != null && d.validFor(p.getUUID(), period)) {
               n += s.getCount();
            }
         }
      }
      return n;
   }

   private static String title(String key) {
      return key.isEmpty() ? key : Character.toUpperCase(key.charAt(0)) + key.substring(1);
   }
}
