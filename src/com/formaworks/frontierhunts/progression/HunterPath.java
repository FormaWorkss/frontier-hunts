package com.formaworks.frontierhunts.progression;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.camps.CampPerks;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.freak.FreakQuest;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.journal.JournalPages;
import com.formaworks.frontierhunts.journal.Rank;
import com.formaworks.frontierhunts.journal.RankPerks;
import com.formaworks.frontierhunts.licence.LicenceOffice;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * [1.1.6] The Hunter's Path: what to do after the first deer, and why. One ordered list from the Ranger Academy to the
 * freaks, each stage with what it gives you, so a new hunter always has a next goal and can see how licences, tokens,
 * contracts, camp, ranks and the species hunts fit together. Two Journal pages: "Hunter's Path" (this data) and
 * "Coming Soon" (static, client side, with the Ko-fi support link).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class HunterPath {
   public static final String PAGE = "path", SOON = "soon";

   private HunterPath() {
   }

   @SubscribeEvent
   public static void setup(FMLCommonSetupEvent e) {
      e.enqueueWork(() -> {
         JournalPages.register(PAGE, "journal.frontierhunts.page.path", "frontierhunts:frontier_handbook", 5, HunterPath::page);
         JournalPages.register(SOON, "journal.frontierhunts.page.soon", "minecraft:spyglass", 95, null);
      });
   }

   /** [1.1.6] one friendly support note, once, after a hunter has filled five tags (never again, never a popup) */
   @EventBusSubscriber(modid = FrontierHunts.ID)
   public static final class Support {
      private Support() {
      }

      @SubscribeEvent
      public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
         if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + (p.getId() & 255)) % 1200 != 600) {
            return;
         }
         try {
            if (JournalApi.counter(p, "support.prompted") > 0 || JournalApi.counter(p, "licence.filled") < 5) {
               return;
            }
            JournalApi.count(p, "support.prompted", 1);
            String url = "https://ko-fi.com/formaworks26954";
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Enjoying Frontier Hunts? It's made by one developer. If you'd like to support it: ")
               .withStyle(net.minecraft.ChatFormatting.GRAY)
               .append(net.minecraft.network.chat.Component.literal("Ko-fi").withStyle(st -> st.withColor(net.minecraft.ChatFormatting.GOLD).withUnderlined(true)
                  .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, url))))
               .append(net.minecraft.network.chat.Component.literal(" (also on the Journal's Coming Soon page). Thank you!").withStyle(net.minecraft.ChatFormatting.GRAY)));
         } catch (RuntimeException ignored) {
         }
      }
   }

   private record Stage(String title, String why, String how, boolean done, String progress) {
   }

   static CompoundTag page(ServerPlayer p) {
      ExpeditionLedger.Hunter ex = ExpeditionLedger.get(p.serverLevel()).hunter(p.getUUID());
      int contracts = ex.completed;
      int camp = CampPerks.tier(p);
      Rank rank = RankPerks.rank(p);
      boolean edu = LicenceOffice.hunterEducation(p);
      int licences = JournalApi.counter(p, "licence.licences");
      int filled = JournalApi.counter(p, "licence.filled");
      int wtClean = JournalApi.counter(p, "hunt.whitetail.clean");
      int wtMature = JournalApi.counter(p, "hunt.whitetail.mature");
      int elk = JournalApi.counter(p, "hunt.elk.clean"), moose = JournalApi.counter(p, "hunt.moose.clean");
      int masters = 0;
      for (String sp : FreakQuest.SPECIES) {
         masters += JournalApi.counter(p, "hunt." + sp + ".master") > 0 ? 1 : 0;
      }
      int freaks = JournalApi.counter(p, "freak.taken");
      String freakNext = "";
      for (String sp : FreakQuest.SPECIES) {
         if (FreakQuest.stage(p, sp) < FreakQuest.DONE) {
            freakNext = FreakQuest.next(p, sp);
            if (FreakQuest.stage(p, sp) > 0) {
               break;
            }
         }
      }
      Stage[] stages = {
         // chapter I: greenhorn
         new Stage("Ranger Academy", "Your first Hunting Licence is free once you pass.",
            "Take the Academy courses (press K): rifle, field report, licence, archery.", edu, edu ? "Passed" : "Open"),
         new Stage("Licence & tags", "Every deer, elk and moose needs a tag. Without one it's poaching: fines, confiscation, no pay.",
            "Journal > Licence & Tags: claim your licence, buy a deer tag with reserve tokens.", licences > 0, licences > 0 ? "Licensed" : "No licence"),
         new Stage("First clean whitetail", "Clean, recovered, tagged animals pay tokens and Journal XP and count for contracts.",
            "Heart or lung shot, follow the blood, field-dress it, tag it.", wtClean > 0 && filled > 0, wtClean + " clean · " + filled + " tagged"),
         // chapter II: hunter
         new Stage("Contract work", "Contracts pay the tokens that buy tags, stamps, camp upgrades and ranger services.",
            "Expedition Board or Ranger Contract Board. One at a time; harder ones pay more.", contracts >= 3, Math.min(contracts, 3) + " / 3"),
         new Stage("Make camp", "Rank 1 makes your camp post a licence counter. Higher ranks pay more for venison, give longer contracts and an extra deer tag.",
            "Expedition Board > Lodge: upgrade with tokens near a station.", camp >= 1, "Rank " + camp + " / 4"),
         new Stage("Woodsman", "Unlocks the ranger's game report: where the herds are and what the rut is doing.",
            "Earn Journal XP: clean harvests, tracking, contracts, checklist.", rank.ordinal() >= Rank.WOODSMAN.ordinal(), rank.title()),
         new Stage("A mature buck", "Quality over quantity: a buck of 8 points or more. Glass first; let the young ones walk.",
            "Scout with trail cameras, hunt the rut, sit downwind of scrapes.", wtMature > 0, wtMature > 0 ? "Taken" : "Open"),
         new Stage("Big country", "Elk in the high parks, moose in the northern bogs. Bigger tags, bigger pay, longer trails.",
            "Buy elk and moose tags in season. Call the bulls in during their rut.", elk > 0 && moose > 0, (elk > 0 ? "Elk ✔" : "Elk") + " · " + (moose > 0 ? "Moose ✔" : "Moose")),
         // chapter III: master
         new Stage("Master a species", "A species' master hunt puts every skill together and opens its legend hunt.",
            "Journal > Hunts: scout, take, technique, quality, master.", masters > 0, masters + " / 3"),
         new Stage("Master Hunter", "Guide rank: 10% off at the counter. Master Hunter: 20% off and one more deer tag a season.",
            "Keep hunting clean and legal; finish checklist entries.", rank.ordinal() >= Rank.MASTER_HUNTER.ordinal(), rank.title()),
         new Stage("Legends of the Reserve", "The end of the trail. The Old Ridge Buck, the Ghost Bull and the Bog King - nobody has taken them, and luck"
            + " won't do it. Each one: 500 tokens, his legendary trophy and 5% off at the licence counter for good. All three: Master of the Reserve"
            + " - 2,000 tokens, everything at the licence counter free for life, and a gold star by your name.",
            freakNext.isEmpty() ? "All three legends taken. You are a Master of the Reserve." : freakNext, freaks >= 3,
            freaks >= 3 ? "Master of the Reserve" : freaks + " / 3")
      };
      CompoundTag t = new CompoundTag();
      ListTag list = new ListTag();
      int next = -1;
      for (int i = 0; i < stages.length; i++) {
         Stage s = stages[i];
         CompoundTag c = new CompoundTag();
         c.putString("t", s.title);
         c.putString("w", s.why);
         c.putString("h", s.how);
         c.putBoolean("d", s.done);
         c.putString("p", s.progress);
         c.putByte("ch", (byte)(i < 3 ? 0 : i < 8 ? 1 : 2));
         list.add(c);
         if (!s.done && next < 0) {
            next = i;
         }
      }
      t.put("s", list);
      t.putInt("next", next);
      return t;
   }
}
