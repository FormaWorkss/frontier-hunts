package com.formaworks.frontierhunts;

import com.formaworks.frontierhunts.hunting.HuntPerception;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.level.LevelEvent.Load;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class HuntService {
   public static final TagKey<Item> BLOCKED_WEAPONS = TagKey.create(Registries.ITEM, FrontierHunts.id("blocked_vanilla_weapons"));
   private static final Map<UUID, Long> REQUEST_TIMES = new HashMap<>();
   private static final Map<UUID, Long> NOTICE_TIMES = new HashMap<>();

   @SubscribeEvent
   public static void load(Load var0) {
      if (var0.getLevel() instanceof ServerLevel var1 && var1.dimension().equals(Level.OVERWORLD)) {
         HunterLedger var3 = HunterLedger.get(var1);
         if (!var3.initialized()) {
            if (var1.dimensionTypeRegistration().is(HuntRules.RESERVE_TYPE)) {
               ((BooleanValue)var1.getGameRules().getRule(HuntRules.ENABLED)).set(true, var1.getServer());
            }

            var3.initialize();
         }

         return;
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ServerLevel var4 = var1.serverLevel();
         HuntPerception.observe(var1);
         if (HuntRules.active(var4) && var1.isAlive() && !var1.isSpectator()) {
            HunterLedger var3 = HunterLedger.get(var4);
            if (var1.tickCount % 20 == 0) {
               issueJournal(var1, var3);
               if (var4.getGameRules().getBoolean(HuntRules.TRACKING)) {
                  var4.getBiome(var1.blockPosition())
                     .unwrapKey()
                     .ifPresent(var2 -> var3.discover(var1.getUUID(), var2.location(), (Integer)HuntConfig.MAX_DISCOVERIES.get()));
               }
            }
         }

         if (var1.tickCount % (Integer)HuntConfig.SYNC_INTERVAL.get() == 0) {
            send(var1, false);
         }
      }
   }

   private static void issueJournal(ServerPlayer var0, HunterLedger var1) {
      if (!var1.hunter(var0.getUUID()).journalIssued() && (Boolean)HuntConfig.STARTER_JOURNAL.get()) {
         if (var0.getInventory().contains(new ItemStack((ItemLike)HuntContent.JOURNAL.get()))) {
            var1.issued(var0.getUUID());
         } else {
            ItemStack var2 = new ItemStack((ItemLike)HuntContent.JOURNAL.get());
            if (var0.getInventory().add(var2)) {
               var1.issued(var0.getUUID());
            }
         }
      }
   }

   public static void request(ServerPlayer var0, HuntNetwork.Request var1) {
      if (var0.isAlive() && !var0.isSpectator()) {
         if (var1.action() >= 0 && var1.action() <= 2) {
            long var2 = var0.serverLevel().getGameTime();
            Long var4 = REQUEST_TIMES.get(var0.getUUID());
            if (var4 == null || var2 < var4 || var2 - var4 >= 5L) {
               REQUEST_TIMES.put(var0.getUUID(), var2);
               if (var1.action() == 0) {
                  openJournal(var0);
               } else if (HuntRules.active(var0.level())) {
                  HunterLedger var5 = HunterLedger.get(var0.serverLevel());
                  if (var1.action() == 2) {
                     var5.claimFirstHunt(var0.getUUID());
                  } else {
                     var5.claimSurvey(var0.getUUID(), (Integer)HuntConfig.SURVEY_REWARD.get());
                  }

                  send(var0, false);
               }
            }
         }
      }
   }

   public static void openJournal(ServerPlayer var0) {
      if (var0.isAlive() && !var0.isSpectator()) {
         if (HuntRules.active(var0.level())) {
            HunterLedger.get(var0.serverLevel()).opened(var0.getUUID());
         }

         send(var0, true);
      }
   }

   public static HuntNetwork.Snapshot snapshot(ServerPlayer var0, boolean var1) {
      ServerLevel var2 = var0.serverLevel();
      Holder var3 = var2.getBiome(var0.blockPosition());
      HuntDefinitions.Region var4 = HuntDefinitions.region(var2.registryAccess(), var3);
      HunterLedger.Hunter var5 = HunterLedger.get(var2).hunter(var0.getUUID());
      Wilderness.Wind var6 = Wilderness.wind(var2.getSeed(), var2.getGameTime(), var2.isRaining(), var2.isThundering());
      List var7 = var5.discoveries();
      return new HuntNetwork.Snapshot(
         var1,
         HuntRules.active(var2),
         var2.getGameRules().getBoolean(HuntRules.TRACKING),
         var2.dimension().location().toString(),
         var4.name(),
         var3.unwrapKey().map(var0x -> var0x.location().toString()).orElse("minecraft:unknown"),
         var4.description(),
         var4.threat(),
         var5.tokens(),
         var7.size(),
         var5.surveyReady(),
         var5.surveyClaimed(),
         (Integer)HuntConfig.SURVEY_REWARD.get(),
         (float)var6.east(),
         (float)var6.south(),
         var2.isRaining(),
         ((HuntConfig.Realism)HuntConfig.REALISM.get()).name(),
         var7.subList(Math.max(0, var7.size() - 16), var7.size())
      );
   }

   public static void send(ServerPlayer var0, boolean var1) {
      if (var0.connection != null && var0.connection.hasChannel(HuntNetwork.Snapshot.TYPE)) {
         PacketDistributor.sendToPlayer(var0, snapshot(var0, var1), new CustomPacketPayload[0]);
      }

      if (var0.connection != null && var0.connection.hasChannel(HuntNetwork.HuntProgress.TYPE)) {
         HunterLedger.Hunter var2 = HunterLedger.get(var0.serverLevel()).hunter(var0.getUUID());
         PacketDistributor.sendToPlayer(
            var0,
            new HuntNetwork.HuntProgress(
               var0.level().dimension().location().toString(), var2.clueInspected(), var2.whitetailHarvested(), var2.firstHuntClaimed()
            ),
            new CustomPacketPayload[0]
         );
      }
   }

   public static boolean weaponRestricted(Level var0, ItemStack var1) {
      return HuntRules.active(var0) && !var0.getGameRules().getBoolean(HuntRules.VANILLA_WEAPONS) && var1.is(BLOCKED_WEAPONS);
   }

   private static void notice(Player var0) {
      long var1 = var0.level().getGameTime();
      Long var3 = NOTICE_TIMES.get(var0.getUUID());
      if (var3 == null || var1 < var3 || var1 - var3 >= 40L) {
         var0.displayClientMessage(Component.translatable("frontierhunts.weapon_restricted"), true);
         NOTICE_TIMES.put(var0.getUUID(), var1);
      }
   }

   @SubscribeEvent
   public static void attack(AttackEntityEvent var0) {
      if (weaponRestricted(var0.getEntity().level(), var0.getEntity().getMainHandItem())) {
         var0.setCanceled(true);
         notice(var0.getEntity());
      }
   }

   @SubscribeEvent
   public static void use(RightClickItem var0) {
      if (weaponRestricted(var0.getLevel(), var0.getItemStack())) {
         var0.setCanceled(true);
         notice(var0.getEntity());
      }
   }

   @SubscribeEvent
   public static void damage(LivingIncomingDamageEvent var0) {
      Level var1 = var0.getEntity().level();
      if (HuntRules.active(var1) && !var1.getGameRules().getBoolean(HuntRules.VANILLA_WEAPONS)) {
         DamageSource var2 = var0.getSource();
         if (var2.getEntity() instanceof Player) {
            if (var2.getDirectEntity() instanceof Player var3 && weaponRestricted(var1, var3.getMainHandItem())) {
               var0.setCanceled(true);
            }

            Entity var5 = var2.getDirectEntity();
            if (var5 != null
               && BuiltInRegistries.ENTITY_TYPE.getKey(var5.getType()).getNamespace().equals("minecraft")
               && (var5 instanceof AbstractArrow || var5 instanceof ThrownTrident || var5.getType() == EntityType.FIREWORK_ROCKET)) {
               var0.setCanceled(true);
            }
         }
      }
   }

   public static boolean denySpawn(ServerLevel var0, Mob var1, MobSpawnType var2) {
      return HuntRules.active(var0)
         && !var0.getGameRules().getBoolean(HuntRules.MONSTERS)
         && var1.getType().getCategory() == MobCategory.MONSTER
         && BuiltInRegistries.ENTITY_TYPE.getKey(var1.getType()).getNamespace().equals("minecraft")
         && (var2 == MobSpawnType.NATURAL || var2 == MobSpawnType.CHUNK_GENERATION || var2 == MobSpawnType.PATROL);
   }

   @SubscribeEvent
   public static void spawn(PositionCheck var0) {
      if (denySpawn(var0.getLevel().getLevel(), var0.getEntity(), var0.getSpawnType())) {
         var0.setResult(Result.FAIL);
      }
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         REQUEST_TIMES.remove(var1.getUUID());
         send(var1, false);
      }
   }

   @SubscribeEvent
   public static void changedDimension(PlayerChangedDimensionEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         REQUEST_TIMES.remove(var1.getUUID());
         send(var1, false);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      HuntPerception.forget(var0.getEntity().getUUID());
      REQUEST_TIMES.remove(var0.getEntity().getUUID());
      NOTICE_TIMES.remove(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent var0) {
      REQUEST_TIMES.clear();
      NOTICE_TIMES.clear();
      HuntPerception.clear();
   }

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent var0) {
      var0.getDispatcher()
         .register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("frontier")
                     .then(Commands.literal("journal").executes(var0x -> {
                        openJournal(((CommandSourceStack)var0x.getSource()).getPlayerOrException());
                        return 1;
                     })))
                  .then(
                     Commands.literal("status")
                        .executes(
                           var0x -> {
                              ServerPlayer var1 = ((CommandSourceStack)var0x.getSource()).getPlayerOrException();
                              HuntNetwork.Snapshot var2 = snapshot(var1, false);
                              ((CommandSourceStack)var0x.getSource())
                                 .sendSuccess(
                                    () -> Component.literal(
                                          "Frontier Hunts | "
                                             + (var2.active() ? "Reserve enabled" : "Reserve disabled")
                                             + " | "
                                             + var2.region()
                                             + " | "
                                             + var2.discovered()
                                             + " habitats | "
                                             + var2.tokens()
                                             + " tokens"
                                       ),
                                    false
                                 );
                              return 1;
                           }
                        )
                  ))
               .then(Commands.literal("journal_replacement").executes(var0x -> {
                  ServerPlayer var1 = ((CommandSourceStack)var0x.getSource()).getPlayerOrException();
                  if (HuntRules.active(var1.level()) && !var1.getInventory().contains(new ItemStack((ItemLike)HuntContent.JOURNAL.get()))) {
                     return var1.getInventory().add(new ItemStack((ItemLike)HuntContent.JOURNAL.get())) ? 1 : 0;
                  } else {
                     return 0;
                  }
               }))
         );
   }

   private HuntService() {
   }
}
