package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.benches.Bench;
import com.formaworks.frontierhunts.benches.BenchCatalog;
import com.formaworks.frontierhunts.benches.BenchTab;
import com.formaworks.frontierhunts.clothing.BaseLayer;
import com.formaworks.frontierhunts.clothing.BaseLayerService;
import com.formaworks.frontierhunts.clothing.Noses;
import com.formaworks.frontierhunts.clothing.Scent;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.expedition.WindCheck;
import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import com.formaworks.frontierhunts.wildlife2026.WildlifeContent;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.formaworks.frontierhunts.Wilderness;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * [clothing] Carbon base layer, scent, bench tab and warmth on a dedicated server, with the fake hunter FHQA:
 * <pre>
 *   /fhqa clothing wear      carbon hood + jacket + trousers as a base layer AND a ghillie jacket in the chest slot; right-click swap
 *   /fhqa clothing scent     the one scent function: full carbon cuts 85-90% for deer (WindCheck.mask = what Whitetail.perceive reads),
 *                            spray stacks, sweat undoes it; a grizzly downwind winds plain clothes but not the carbon layer
 *   /fhqa clothing persist   save/load (relog), death without keepInventory drops it like armour, with keepInventory it is kept
 *   /fhqa clothing bench     the Frontier Workbench "Scent & packs" tab holds the carbon pieces, suit, spray, pack and quiver
 *   /fhqa clothing warmth    insulation and comfort of three outfits in a cold biome night (snowy plains, January)
 *   /fhqa clothing all
 * </pre>
 * Every verdict line is "[FHQA] clothing PASS|FAIL ...".
 */
final class ClothingTest {
   private ClothingTest() {
   }

   static void say(String s) {
      FrontierQa.say("clothing " + s);
   }

   static void verdict(boolean ok, String what) {
      say((ok ? "PASS " : "FAIL ") + what);
   }

   static int run(CommandSourceStack src, String which) {
      MinecraftServer server = src.getServer();
      try {
         switch (which.trim()) {
            case "wear" -> wear(server);
            case "scent" -> scent(server);
            case "persist" -> persist(server);
            case "bench" -> bench(server);
            case "warmth" -> warmth(server);
            case "all" -> {
               wear(server);
               scent(server);
               persist(server);
               bench(server);
               warmth(server);
            }
            default -> say("FAIL unknown case " + which);
         }
      } catch (Throwable t) {
         FrontierQa.fail("clothing " + which, t);
      }
      return 1;
   }

   static ItemStack stack(String id) {
      Item i = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.contains(":") ? id : "frontierhunts:" + id));
      return new ItemStack(i);
   }

   static void reset(FakePlayer fp) {
      BaseLayerService.undress(fp);
      for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         fp.setItemSlot(s, ItemStack.EMPTY);
      }
      fp.getInventory().clearContent();
      fp.getPersistentData().remove(Scent.SPRAY_KEY);
      BaseLayer.of(fp).sweat = 0F;
   }

   static void dressCarbon(FakePlayer fp) {
      BaseLayerService.wear(fp, stack("carbon_hood"));
      BaseLayerService.wear(fp, stack("carbon_jacket"));
      BaseLayerService.wear(fp, stack("carbon_trousers"));
   }

   static String worn(Player p) {
      ItemStack[] s = BaseLayer.worn(p);
      List<String> n = new ArrayList<>();
      for (ItemStack st : s) {
         n.add(st.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
      }
      return String.join(",", n);
   }

   // -------------------------------------------------------------------------------------------- wear
   static void wear(MinecraftServer server) {
      FakePlayer fp = FrontierQa.fake(server);
      reset(fp);
      dressCarbon(fp);
      fp.setItemSlot(EquipmentSlot.CHEST, stack("ghillie_jacket"));
      boolean base = "carbon_hood,carbon_jacket,carbon_trousers".equals(worn(fp));
      boolean outer = BuiltInRegistries.ITEM.getKey(fp.getItemBySlot(EquipmentSlot.CHEST).getItem()).getPath().equals("ghillie_jacket");
      verdict(base && outer, "carbon base layer (" + worn(fp) + ") worn under a ghillie jacket in the chest slot");
      verdict(!stack("carbon_jacket").canEquip(EquipmentSlot.CHEST, fp) && !stack("scent_suit").canEquip(EquipmentSlot.CHEST, fp),
         "carbon pieces and the scent suit do not go in the armour slots");
      // right-click the suit: hood, jacket and trousers come off (jacket to the hand, the rest to the inventory)
      fp.setItemInHand(InteractionHand.MAIN_HAND, stack("scent_suit"));
      fp.getMainHandItem().use(fp.level(), fp, InteractionHand.MAIN_HAND);
      boolean suit = BaseLayer.suit(BaseLayer.worn(fp));
      boolean hand = fp.getMainHandItem().getItem() instanceof ScentControl c && c.piece == ScentControl.Piece.TOP;
      int inv = 0;
      for (int i = 0; i < fp.getInventory().getContainerSize(); i++) {
         if (fp.getInventory().getItem(i).getItem() instanceof ScentControl) {
            inv++;
         }
      }
      verdict(suit && hand && inv == 3, "right-click wears the scent suit: worn " + worn(fp) + ", jacket back in the hand, " + inv
         + " carbon stacks in the inventory (jacket in hand + hood + trousers)");
      // old saves: a carbon jacket left in the chest slot moves into the base layer
      reset(fp);
      fp.setItemSlot(EquipmentSlot.CHEST, stack("carbon_jacket"));
      BaseLayerService.migrate(fp);
      verdict(fp.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && "-,carbon_jacket,-".equals(worn(fp)),
         "an old carbon jacket in the chest slot moves into the base layer (" + worn(fp) + ")");
      reset(fp);
   }

   // -------------------------------------------------------------------------------------------- scent
   static void scent(MinecraftServer server) {
      FakePlayer fp = FrontierQa.fake(server);
      ServerLevel level = fp.serverLevel();
      reset(fp);
      long t = level.getGameTime();
      double plain = WindCheck.mask(fp, level);
      dressCarbon(fp);
      fp.setItemSlot(EquipmentSlot.CHEST, stack("ghillie_jacket")); // outer clothing does not change the carbon underneath
      // the server caches the factor per tick: read it on the next tick value through the pure form too
      Scent.Reading r = Scent.read(fp);
      double carbon = r.factor();
      double ratio = carbon / plain;
      verdict(Math.abs(plain - 1.0) < 1e-6, String.format(Locale.ROOT, "plain clothes: deer scent factor %.3f", plain));
      verdict(ratio >= 0.10 && ratio <= 0.15, String.format(Locale.ROOT, "full carbon base layer under a ghillie: deer smell %.0f%% of the scent (factor %.3f, "
         + "coverage %.2f, charge-weighted %.2f)", ratio * 100.0, carbon, r.coverage(), r.carbon()));
      fp.getPersistentData().putLong(Scent.SPRAY_KEY, t + Scent.SPRAY_TICKS);
      double spray = Scent.read(fp).factor();
      verdict(spray < carbon && spray > Scent.MIN, String.format(Locale.ROOT, "scent cover spray stacks on the carbon: %.3f (never zero)", spray));
      fp.getPersistentData().remove(Scent.SPRAY_KEY);
      BaseLayer.of(fp).sweat = 0.8F;
      double sweat = Scent.read(fp).factor();
      verdict(sweat > carbon * 2.0, String.format(Locale.ROOT, "sweat gets through the carbon: %.3f", sweat));
      BaseLayer.of(fp).sweat = 0F;
      ItemStack top = BaseLayer.worn(fp)[BaseLayer.TOP];
      ScentControl.setCharge(top, 0F);
      double spent = Scent.read(fp).factor();
      verdict(spent > carbon && spent < 1.0, String.format(Locale.ROOT, "a spent jacket lets more through: %.3f", spent));
      ScentControl.setCharge(top, 1F);
      // a grizzly 20 blocks straight downwind
      Wilderness.Wind w = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
      double sp = Math.max(1e-3, w.speed());
      WildlifeMob bear = WildlifeContent.TYPES.get(WildlifeSpecies.GRIZZLY).get().create(level);
      if (bear == null) {
         verdict(false, "could not create a grizzly");
         return;
      }
      bear.moveTo(fp.getX() + w.east() / sp * 20.0, fp.getY(), fp.getZ() + w.south() / sp * 20.0, 0F, 0F);
      double withCarbon = Noses.whiff(bear, fp);
      reset(fp);
      double without = Noses.whiff(bear, fp);
      verdict(without > Noses.WINDED && withCarbon < Noses.WINDED, String.format(Locale.ROOT,
         "grizzly 20 blocks downwind (wind %.1f m/s): plain clothes %.2f (winded > %.2f), carbon layer %.2f", sp, without, Noses.WINDED, withCarbon));
      bear.discard();
      reset(fp);
   }

   // -------------------------------------------------------------------------------------------- persistence
   static void persist(MinecraftServer server) {
      FakePlayer fp = FrontierQa.fake(server);
      ServerLevel level = fp.serverLevel();
      reset(fp);
      dressCarbon(fp);
      ScentControl.setCharge(BaseLayer.of(fp).get(BaseLayer.TOP), 0.42F);
      CompoundTag saved = new CompoundTag();
      fp.saveWithoutId(saved);
      FakePlayer other = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("fhqa_clothing".getBytes()),
         "FHQA_CL"));
      BaseLayerService.undress(other);
      other.load(saved);
      float charge = ScentControl.charge(BaseLayer.of(other).get(BaseLayer.TOP));
      verdict("carbon_hood,carbon_jacket,carbon_trousers".equals(worn(other)) && Math.abs(charge - 0.42F) < 0.005F,
         "relog: the base layer and its charge come back from the player file (" + worn(other) + ", charge " + charge + ")");
      BaseLayerService.undress(other);
      other.getInventory().clearContent();
      // death without keepInventory: dropped like armour
      GameRules.BooleanValue keep = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
      boolean was = keep.get();
      try {
         keep.set(false, server);
         List<ItemEntity> drops = new ArrayList<>();
         LivingDropsEvent ev = new LivingDropsEvent(fp, fp.damageSources().generic(), drops, true);
         BaseLayerService.drops(ev);
         int dropped = 0;
         for (ItemEntity ie : ev.getDrops()) {
            if (ie.getItem().getItem() instanceof ScentControl) {
               dropped++;
            }
         }
         verdict(dropped == 3 && BaseLayer.of(fp).isEmpty(), "death without keepInventory drops the 3 base-layer pieces like armour (" + dropped + ")");
         // keepInventory: kept and copied to the respawned player
         dressCarbon(fp);
         keep.set(true, server);
         List<ItemEntity> none = new ArrayList<>();
         BaseLayerService.drops(new LivingDropsEvent(fp, fp.damageSources().generic(), none, true));
         AttachmentInternals.copyEntityAttachments(fp, other, true);
         verdict(none.isEmpty() && "carbon_hood,carbon_jacket,carbon_trousers".equals(worn(other)),
            "death with keepInventory keeps the base layer through respawn (" + worn(other) + ")");
         // an End return / dimension change (not a death) always copies
         BaseLayerService.undress(other);
         AttachmentInternals.copyEntityAttachments(fp, other, false);
         verdict("carbon_hood,carbon_jacket,carbon_trousers".equals(worn(other)), "a non-death respawn (End return) keeps the base layer");
      } finally {
         keep.set(was, server);
         BaseLayerService.undress(other);
         other.getInventory().clearContent();
         reset(fp);
      }
   }

   // -------------------------------------------------------------------------------------------- bench
   static void bench(MinecraftServer server) {
      ServerLevel level = server.overworld();
      String[] want = {"carbon_hood", "carbon_jacket", "carbon_trousers", "scent_suit", "scent_cover", "hunter_pack", "hunters_quiver"};
      List<String> wrong = new ArrayList<>();
      for (String id : want) {
         BenchCatalog.Place p = BenchCatalog.place(stack(id).getItem());
         if (p == null || p.tab() != BenchTab.SCENT) {
            wrong.add(id + "->" + (p == null ? "none" : p.tab()));
         }
      }
      verdict(wrong.isEmpty(), "the Scent & packs tab places the carbon pieces, suit, spray, pack and quiver" + (wrong.isEmpty() ? "" : ": " + wrong));
      List<String> listed = new ArrayList<>();
      for (BenchCatalog.Entry e : BenchCatalog.entries(level.getRecipeManager(), level.registryAccess(), Bench.FRONTIER)) {
         if (e.tab() == BenchTab.SCENT) {
            listed.add(BuiltInRegistries.ITEM.getKey(e.product.isEmpty() ? e.display.getItem() : e.product.getItem()).getPath());
         } else if (e.tab() == BenchTab.CLOTHING || e.tab() == BenchTab.HUNTING) {
            String id = BuiltInRegistries.ITEM.getKey(e.display.getItem()).getPath();
            for (String w : want) {
               if (w.equals(id)) {
                  wrong.add(id + " still in " + e.tab());
               }
            }
         }
      }
      verdict(listed.size() == want.length && wrong.isEmpty(), "the Frontier Workbench lists " + listed.size() + " recipes in Scent & packs: " + listed
         + (wrong.isEmpty() ? "" : ", but " + wrong));
   }

   // -------------------------------------------------------------------------------------------- warmth
   static void warmth(MinecraftServer server) {
      FakePlayer fp = FrontierQa.fake(server);
      reset(fp);
      // snowy plains in January (biome base temperature 0.0, seasons on), 70 blocks up, at noon (night is ~6 C colder)
      float air = SurvivalMath.air(0.0F, true, true, 0.5, 6000L, 70, false);
      float night = SurvivalMath.air(0.0F, true, true, 0.5, 18000L, 70, false);
      float wind = 6F;
      String[] names = {"plain clothes", "ghillie set over the carbon base layer",
         "arctic: scent suit, fur hat, fur-lined bear coat with mittens, leggings, mukluks"};
      float[] ins = new float[3];
      float[] low = new float[3];
      for (int k = 0; k < 3; k++) {
         reset(fp);
         if (k == 1) {
            dressCarbon(fp);
            fp.setItemSlot(EquipmentSlot.HEAD, stack("ghillie_hood"));
            fp.setItemSlot(EquipmentSlot.CHEST, stack("ghillie_jacket"));
            fp.setItemSlot(EquipmentSlot.LEGS, stack("ghillie_trousers"));
         } else if (k == 2) {
            BaseLayerService.wear(fp, stack("scent_suit"));
            fp.setItemSlot(EquipmentSlot.HEAD, stack("fur_hat"));
            ItemStack coat = stack("bear_fur_coat");
            coat.set(com.formaworks.frontierhunts.survival.SurvivalContent.LINING.get(), 3); // fur lining + mittens sewn in
            fp.setItemSlot(EquipmentSlot.CHEST, coat);
            fp.setItemSlot(EquipmentSlot.LEGS, stack("buckskin_leggings"));
            fp.setItemSlot(EquipmentSlot.FEET, stack("fur_mukluks"));
         }
         Clothing.Outfit o = Clothing.outfit(fp);
         float felt = air - SurvivalMath.windChill(wind, 1F, o.wind());
         ins[k] = o.insulation();
         low[k] = SurvivalMath.comfortLow(o.insulation());
         say(String.format(Locale.ROOT, "warmth %s: insulation %.2f, windproof %.0f%%, water %.0f%%, comfortable down to %.1f C; snowy plains January "
            + "noon %.1f C air, %.1f C felt in a %.0f m/s wind -> %s (midnight %.1f C air)", names[k], o.insulation(), o.wind() * 100, o.water() * 100, low[k],
            air, felt, wind, felt >= low[k] ? "comfortable" : "losing heat", night));
      }
      verdict(ins[0] < ins[1] && ins[1] < ins[2], String.format(Locale.ROOT, "insulation plain %.2f < ghillie over carbon %.2f < arctic %.2f", ins[0], ins[1], ins[2]));
      float felt2 = air - SurvivalMath.windChill(wind, 1F, Clothing.outfit(fp).wind());
      verdict(low[2] <= felt2 && low[0] > air, String.format(Locale.ROOT, "the arctic outfit is comfortable on a snowy-plains January day (%.1f C felt, "
         + "comfortable to %.1f C), plain clothes are not (comfortable to %.1f C)", felt2, low[2], low[0]));
      reset(fp);
   }
}
