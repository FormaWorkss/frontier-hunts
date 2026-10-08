package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.workshop.WorkbenchMenu;
import com.formaworks.frontierhunts.workshop.WorkshopContent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class ExpeditionService {
   private static final Map<UUID, Long> REQUESTS = new HashMap<>();
   private static final Map<UUID, Long> VIEWS = new HashMap<>();
   private static final String[] TRAINING_TASKS = new String[]{
      "Make two vital hits on animals", "Inspect three wildlife clues", "Craft three field supplies", "Land two fish"
   };
   private static final String[] TRAINING_EVENTS = new String[]{"vital", "clue", "craft", "fish"};
   private static final int[] TRAINING_TARGETS = new int[]{2, 3, 3, 2};
   public static ToIntFunction<Player> clientTracker = var0 -> 0;
   public static final Map<String, Integer> SHOP = new LinkedHashMap<>();

   private static void trainingEvent(ServerPlayer var0, String var1, int var2, double var3) {
      ExpeditionLedger var5 = ExpeditionLedger.get(var0.serverLevel());
      ExpeditionLedger.Hunter var6 = var5.hunter(var0.getUUID());
      int var7 = var6.training;
      if (var7 >= 0 && var7 < TRAINING_EVENTS.length) {
         long var8 = var0.level().getGameTime();
         if (var8 > var6.trainingDeadline) {
            var6.training = -1;
            var6.trainingProgress = 0;
            var5.setDirty();
            message(var0, "Field training expired. Start it again from the guide.");
            send(var0, false);
         } else if (TRAINING_EVENTS[var7].equals(var1)) {
            if (var7 != 0 || !(var3 < 12.0)) {
               var6.trainingProgress = Math.min(TRAINING_TARGETS[var7], var6.trainingProgress + var2);
               if (var6.trainingProgress >= TRAINING_TARGETS[var7]) {
                  if (var6.skills[var7] < 3 && var6.xp / 100 > Arrays.stream(var6.skills).sum()) {
                     var6.skills[var7]++;
                     message(var0, "Field training complete: " + TRAINING_TASKS[var7] + ". Rank " + var6.skills[var7] + " earned.");
                  }

                  var6.training = -1;
                  var6.trainingProgress = 0;
                  var6.trainingDeadline = 0L;
               } else {
                  message(var0, "Field training: " + var6.trainingProgress + " / " + TRAINING_TARGETS[var7]);
               }

               var5.setDirty();
               send(var0, false);
            }
         }
      }
   }

   public static double inspectRange(Player var0) {
      return 4.0 + (double)(var0 instanceof ServerPlayer var1 ? skill(var1, 1) : Math.clamp((long)clientTracker.applyAsInt(var0), 0, 3)) * 0.6;
   }

   public static int skill(ServerPlayer var0, int var1) {
      return ExpeditionLedger.get(var0.serverLevel()).hunter(var0.getUUID()).skills[var1];
   }

   public static void record(ServerPlayer var0, String var1, String var2, int var3, double var4) {
      if (var1 != null && var2 != null && var0.isAlive() && !var0.isSpectator() && var3 > 0 && var3 <= 64 && Double.isFinite(var4) && !(var4 < 0.0)) {
         if (var1.equals("harvest")) {
            ReservePermits.get(var0.serverLevel()).recover(var0, var2);
         }

         trainingEvent(var0, var1, var3, var4);
         List var6 = !var1.equals("visit") && !var1.equals("survey") && !var1.equals("display") ? members(var0) : List.of(var0);
         recordFor(var0, var6, var1, var2, var3, var4);
      }
   }

   private static void recordFor(ServerPlayer var0, List<ServerPlayer> var1, String var2, String var3, int var4, double var5) {
      ExpeditionLedger var7 = ExpeditionLedger.get(var0.serverLevel());

      for (ServerPlayer var9 : var1) {
         ExpeditionLedger.Hunter var10 = var7.hunter(var9.getUUID());
         boolean var11 = CampaignProgress.event(var10, var2, var3, var4, var5, var0.level().getGameTime(), var1.size() > 1);
         if (var11) {
            if (var2.equals("harvest") || var2.equals("fish") || var2.equals("boss")) {
               var10.xp = Math.min(1000000, var10.xp + (var2.equals("boss") ? 75 : 15));
               HunterLedger.get(var0.serverLevel()).provisionPayment(var9.getUUID(), var2.equals("boss") ? 40 : 5);
            }

            if (var2.equals("harvest") && !var0.isCreative()) {
               var10.longest = Math.max(var10.longest, var5);
            }
         }
      }

      var7.setDirty();

      for (ServerPlayer var13 : var1) {
         send(var13, false);
      }
   }

   public static boolean nearBoard(ServerPlayer var0) {
      for (BlockPos var2 : BlockPos.betweenClosed(var0.blockPosition().offset(-6, -3, -6), var0.blockPosition().offset(6, 3, 6))) {
         if (var0.level().getBlockState(var2).getBlock() instanceof ExpeditionStation var3 && var3.id.equals("expedition_board")) {
            return true;
         }
      }

      return false;
   }

   public static List<ServerPlayer> members(ServerPlayer var0) {
      ExpeditionLedger var1 = ExpeditionLedger.get(var0.serverLevel());
      ExpeditionLedger.Hunter var2 = var1.hunter(var0.getUUID());
      ArrayList var3 = new ArrayList();
      if (var0.isAlive() && !var0.isSpectator()) {
         var3.add(var0);
      }

      if (var2.party != null) {
         for (ServerPlayer var5 : var0.serverLevel().players()) {
            if (var5 != var0
               && var5.isAlive()
               && !var5.isSpectator()
               && var2.party.equals(var1.hunter(var5.getUUID()).party)
               && var5.distanceToSqr(var0) < 9216.0) {
               var3.add(var5);
               if (var3.size() >= 8) {
                  break;
               }
            }
         }
      }

      return List.copyOf(var3);
   }

   private static boolean nearStation(ServerPlayer var0) {
      for (BlockPos var2 : BlockPos.betweenClosed(var0.blockPosition().offset(-6, -3, -6), var0.blockPosition().offset(6, 3, 6))) {
         if (var0.level().getBlockState(var2).getBlock() instanceof ExpeditionStation) {
            return true;
         }
      }

      return false;
   }

   public static void request(ServerPlayer var0, ExpeditionNetwork.Request var1) {
      if (var0.isAlive() && !var0.isSpectator()) {
         long var2 = var0.level().getGameTime();
         int var4 = var1.action();
         String var5 = var1.value();
         if (var4 != 10 && var4 != 11 && var4 != 15) {
            Long var6 = REQUESTS.put(var0.getUUID(), var2);
            if (var6 != null && var2 - var6 < 3L) {
               return;
            }
         }

         if (var4 == 16) {
            if (var0.isShiftKeyDown()
               && var0.getMainHandItem().getItem() instanceof ExpeditionWeapon var13
               && ExpeditionWeapon.data(var0.getMainHandItem()).getLong("reload_until") <= var2) {
               var13.fit(var0, var0.getMainHandItem());
            }
         } else if (var4 == 15) {
            ExpeditionAim.set(var0, var5.equals("1"));
         } else if (var4 == 21) {
            FieldGearActions.packKey(var0);
         } else if (var4 == 20) {
            if (ArrowSupply.arrowBow(var0.getMainHandItem())) {
               ArrowSupply.cycle(var0);
            }
         } else if (var4 != 10 && var4 != 11) {
            ExpeditionLedger var12 = ExpeditionLedger.get(var0.serverLevel());
            ExpeditionLedger.Hunter var14 = var12.hunter(var0.getUUID());
            var14.name = var0.getScoreboardName();
            HunterLedger var8 = HunterLedger.get(var0.serverLevel());
            switch (var4) {
               case 0:
                  VIEWS.put(var0.getUUID(), var2 + 1200L);
                  send(var0, true);
                  return;
               case 1:
                  CampaignProgress.claimMission(var0, var5);
                  break;
               case 2:
                  CampaignProgress.accept(var14, number(var5), var2);
                  break;
               case 3:
                  CampaignProgress.claimContract(var0, var5);
                  break;
               case 4:
                  int var18 = number(var5);
                  if (var18 >= 0
                     && var18 < 4
                     && (var14.training < 0 || var2 > var14.trainingDeadline)
                     && var14.skills[var18] < 3
                     && var14.xp / 100 > Arrays.stream(var14.skills).sum()) {
                     var14.training = var18;
                     var14.trainingProgress = 0;
                     var14.trainingDeadline = var2 + 18000L;
                     message(var0, "Field training started: " + TRAINING_TASKS[var18] + " (15 minutes)");
                  }
                  break;
               case 5:
                  if (nearStation(var0)) {
                     buy(var0, var5);
                  }
                  break;
               case 6:
                  if (nearStation(var0) && var14.camp < 4 && var8.spend(var0.getUUID(), 25 + var14.camp * 20)) {
                     String var17 = new String[]{"lodge_stores", "ammo_reloader", "bow_tuning_rack", "fishing_station"}[var14.camp++];
                     give(var0, new ItemStack((ItemLike)ExpeditionContent.STATIONS.get(var17).get()));
                     if (var14.camp == 1) {
                        give(var0, new ItemStack((ItemLike)ExpeditionContent.STATIONS.get("lodge_stores").get(), 4));
                     }
                  }
               case 7:
               case 10:
               case 11:
               case 15:
               case 16:
               case 17:
               case 18:
               default:
                  break;
               case 8:
                  if (!var14.starter) {
                     var14.starter = true;
                     give(var0, new ItemStack(ExpeditionContent.item("expedition_guide")));
                     give(var0, new ItemStack((ItemLike)HuntContent.FIELD_BOW.get()));
                     give(var0, new ItemStack((ItemLike)HuntContent.FIELD_ARROW.get(), 24));
                     give(var0, new ItemStack((ItemLike)HuntContent.FIELD_KNIFE.get()));
                     give(var0, new ItemStack((ItemLike)HuntContent.SKINNING_TOOL.get()));
                     give(var0, new ItemStack((ItemLike)WorkshopContent.ROD.get()));
                     give(var0, new ItemStack((ItemLike)ExpeditionContent.STATIONS.get("expedition_board").get()));
                  }
                  break;
               case 9:
                  var14.contract = -1;
                  var14.contractCount = 0;
                  var14.contractReadyAt = 0L;
                  break;
               case 12:
                  if (var14.invite != null) {
                     UUID var16 = var14.invite;
                     ExpeditionLedger.Hunter var10 = var12.hunter(var16);
                     if (var16.equals(var10.party) && var12.hunters.values().stream().filter(var1x -> var16.equals(var1x.party)).count() < 8L) {
                        var14.party = var16;
                        var14.invite = null;
                     }
                  }
                  break;
               case 13:
                  UUID var9 = var14.party;
                  if (var0.getUUID().equals(var9)) {
                     var12.hunters.values().forEach(var1x -> {
                        if (var9.equals(var1x.party)) {
                           var1x.party = null;
                        }
                     });
                  } else {
                     var14.party = null;
                  }
                  break;
               case 14:
                  marker(var0, var0.blockPosition());
                  break;
               case 19:
                  ReservePermits.get(var0.serverLevel()).buy(var0, var5);
            }

            var12.setDirty();
            send(var0, false);
         } else {
            if (var0.getMainHandItem().getItem() instanceof ExpeditionWeapon var11) {
               if (var4 == 10 && !var11.weapon.bow) {
                  var11.shoot(var0, 1.0F);
               } else if (var4 == 11) {
                  var11.reload(var0);
               }
            }
         }
      }
   }

   private static void buy(ServerPlayer var0, String var1) {
      Integer var2 = SHOP.get(var1);
      if (var2 != null) {
         ItemStack var3 = new ItemStack(
            ExpeditionContent.item(var1), !var1.contains("round") && !var1.contains("shell") && !var1.contains("dart") && !var1.contains("arrow") ? 1 : 12
         );
         ArrayList var4 = var0.getInventory().items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
         if (WorkbenchMenu.insert(var4, var3)) {
            if (HunterLedger.get(var0.serverLevel()).spend(var0.getUUID(), var2)) {
               for (int var5 = 0; var5 < var4.size(); var5++) {
                  var0.getInventory().setItem(var5, (ItemStack)var4.get(var5));
               }

               var0.inventoryMenu.broadcastChanges();
            }
         }
      }
   }

   public static void marker(ServerPlayer var0, BlockPos var1) {
      ExpeditionLedger var2 = ExpeditionLedger.get(var0.serverLevel());
      String var3 = var0.level().dimension().location() + " · " + var1.getX() + ", " + var1.getY() + ", " + var1.getZ();

      for (ServerPlayer var5 : members(var0)) {
         var2.hunter(var5.getUUID()).marker = var3;
         message(var5, "Shared marker: " + var3);
      }

      var2.setDirty();
   }

   public static void send(ServerPlayer var0, boolean var1) {
      ExpeditionLedger var2 = ExpeditionLedger.get(var0.serverLevel());
      ExpeditionLedger.Hunter var3 = var2.hunter(var0.getUUID());
      JsonObject var4 = new JsonObject();
      var4.addProperty("open", var1);
      var4.addProperty("stage", var3.stage);
      var4.addProperty("mission_id", Campaign.id(var3.stage));
      var4.addProperty("starter", var3.starter);
      var4.addProperty("discoveries", var3.discoveries.size());
      var4.addProperty("count", var3.count);
      var4.addProperty("xp", var3.xp);
      var4.addProperty("training", var3.training);
      var4.addProperty("training_progress", var3.trainingProgress);
      var4.addProperty("training_seconds", Math.max(0L, (var3.trainingDeadline - var0.level().getGameTime()) / 20L));
      var4.addProperty("tokens", HunterLedger.get(var0.serverLevel()).hunter(var0.getUUID()).tokens());
      var4.addProperty("contract", var3.contract);
      var4.addProperty("contract_status", CampaignProgress.contractStatus(var3, var0.level().getGameTime()));
      var4.addProperty("contract_serial", Long.toString(var3.contractSerial));
      var4.addProperty("contract_count", var3.contractCount);
      var4.addProperty("seconds", Math.max(0L, (var3.deadline - var0.level().getGameTime()) / 20L));
      var4.addProperty("camp", var3.camp);
      var4.addProperty("invite", var3.invite != null);
      var4.addProperty("party", var3.party != null);
      var4.addProperty("marker", var3.marker);
      var4.addProperty("station", nearStation(var0));
      var4.addProperty("board", nearBoard(var0));
      var4.add("calendar", ReservePermits.get(var0.serverLevel()).snapshot(var0));
      JsonArray var5 = new JsonArray();

      for (ServerPlayer var7 : members(var0)) {
         ExpeditionLedger.Hunter var8 = var2.hunter(var7.getUUID());
         var5.add(var7.getScoreboardName() + " · " + (var8.stage == var3.stage ? "same mission" : "mission " + (var8.stage + 1)));
      }

      var4.add("companions", var5);
      JsonArray var11 = new JsonArray();

      for (int var10 : var3.skills) {
         var11.add(var10);
      }

      var4.add("skills", var11);
      JsonArray var13 = new JsonArray();
      var2.hunters
         .values()
         .stream()
         .sorted(Comparator.<ExpeditionLedger.Hunter>comparingDouble(var0x -> var0x.longest).reversed())
         .limit(8L)
         .forEach(
            var1x -> var13.add(
                  var1x.name
                     + " · "
                     + Math.round(var1x.longest)
                     + " m · "
                     + var1x.completed
                     + " contracts · "
                     + var1x.bestHunt / 20L
                     + " s best · trophy "
                     + Math.round(var1x.bestTrophy)
               )
         );
      var4.add("leaders", var13);
      packet(var0, var4);
   }

   private static void packet(ServerPlayer var0, JsonObject var1) {
      if (var0.connection != null && var0.connection.hasChannel(ExpeditionNetwork.Snapshot.TYPE)) {
         PacketDistributor.sendToPlayer(
            var0, new ExpeditionNetwork.Snapshot(var0.level().dimension().location().toString(), var1.toString()), new CustomPacketPayload[0]
         );
      }
   }

   public static void give(ServerPlayer var0, ItemStack var1) {
      if (!var0.getInventory().add(var1)) {
         var0.drop(var1, false);
      }
   }

   public static void message(ServerPlayer var0, String var1) {
      var0.displayClientMessage(Component.literal(var1), true);
   }

   private static int number(String var0) {
      try {
         return Integer.parseInt(var0);
      } catch (Exception var2) {
         return -1;
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && var1.tickCount % 100 == 0) {
         ExpeditionLedger var5 = ExpeditionLedger.get(var1.serverLevel());
         ExpeditionLedger.Hunter var3 = var5.hunter(var1.getUUID());
         if (var3.training >= 0 && var1.level().getGameTime() > var3.trainingDeadline) {
            var3.training = -1;
            var3.trainingProgress = 0;
            var3.trainingDeadline = 0L;
            var5.setDirty();
            message(var1, "Field training expired. Start it again from the guide.");
            send(var1, false);
         }

         if (var3.starter) {
            String var4 = var1.level().getBiome(var1.blockPosition()).unwrapKey().map(var0x -> var0x.location().toString()).orElse("");
            if (!var4.isEmpty() && !var3.discoveries.contains(var4)) {
               record(var1, "visit", var4, 1, 0.0);
            }

            if (skill(var1, 2) > 0 && var1.getFoodData().getFoodLevel() > 16 && nearStation(var1)) {
               var1.heal(0.25F * (float)skill(var1, 2));
            }
         }

         if (VIEWS.getOrDefault(var1.getUUID(), 0L) > var1.level().getGameTime()) {
            send(var1, false);
         }

         return;
      }
   }

   @SubscribeEvent
   public static void crafted(ItemCraftedEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && BuiltInRegistries.ITEM.getKey(var0.getCrafting().getItem()).getNamespace().equals("frontierhunts")) {
         record(var1, "craft", "*", 1, 0.0);
      }
   }

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent var0) {
      var0.getDispatcher()
         .register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("expedition").executes(var0x -> {
            send(((CommandSourceStack)var0x.getSource()).getPlayerOrException(), true);
            return 1;
         })).then(Commands.literal("start").executes(var0x -> {
            request(((CommandSourceStack)var0x.getSource()).getPlayerOrException(), new ExpeditionNetwork.Request(8, ""));
            send(((CommandSourceStack)var0x.getSource()).getPlayerOrException(), true);
            return 1;
         }))).then(Commands.literal("invite").then(Commands.argument("hunter", EntityArgument.player()).executes(var0x -> {
            ServerPlayer var1 = ((CommandSourceStack)var0x.getSource()).getPlayerOrException();
            ServerPlayer var2 = EntityArgument.getPlayer(var0x, "hunter");
            if (var2 == var1) {
               return 0;
            } else {
               ExpeditionLedger var3 = ExpeditionLedger.get(var1.serverLevel());
               ExpeditionLedger.Hunter var4 = var3.hunter(var1.getUUID());
               if (var4.party != null && !var4.party.equals(var1.getUUID())) {
                  return 0;
               } else {
                  var4.party = var1.getUUID();
                  var3.hunter(var2.getUUID()).invite = var1.getUUID();
                  var3.setDirty();
                  message(var2, var1.getScoreboardName() + " invited you. Open the expedition guide to accept.");
                  send(var2, false);
                  return 1;
               }
            }
         }))));
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      REQUESTS.remove(var0.getEntity().getUUID());
      VIEWS.remove(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stop(ServerStoppedEvent var0) {
      REQUESTS.clear();
      VIEWS.clear();
   }

   private ExpeditionService() {
   }

   static {
      SHOP.put("rifle_round", 8);
      SHOP.put("shotgun_shell", 8);
      SHOP.put("pistol_round", 6);
      SHOP.put("tranquilizer_dart", 12);
      SHOP.put("bowfishing_arrow", 8);
      SHOP.put("medkit", 10);
      SHOP.put("bait", 4);
      SHOP.put("scent_cover", 8);
      SHOP.put("suppressor", 45);
      SHOP.put("extended_magazine", 35);
      SHOP.put("steady_stock", 30);
      SHOP.put("bipod", 25);
      SHOP.put("six_power_scope", 40);
      SHOP.put("eight_power_scope", 55);
      SHOP.put("twelve_power_scope", 70);
      SHOP.put("thermal_scope", 85);
      SHOP.put("fishing_drag_kit", 20);
   }
}
