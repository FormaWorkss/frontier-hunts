package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.guide.FieldSchool;
import com.formaworks.frontierhunts.guide.Tip;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.survival.block.HideBedrollBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [survival] Server driver of Frontier Survival. Once a second per player (staggered by entity id): nutrition drain from
 * idle metabolism + vanilla exhaustion + cold, body heat from {@link Thermal} and the outfit, effects (attribute modifiers,
 * heal scaling), malnutrition / freezing damage by difficulty, food spoilage in the inventory (every 10 s) and a small
 * state payload to the player. Fires and shelter are re-scanned every 3 s.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class SurvivalService {
   private SurvivalService() {
   }

   static final Map<UUID, PlayerSurvival> LIVE = new HashMap<>();
   public static final ResourceKey<DamageType> MALNUTRITION = ResourceKey.create(Registries.DAMAGE_TYPE, FrontierHunts.id("malnutrition"));
   private static final ResourceLocation MOD_MOVE = FrontierHunts.id("survival_move");
   private static final ResourceLocation MOD_ATTACK = FrontierHunts.id("survival_attack_speed");
   private static final ResourceLocation MOD_MINE = FrontierHunts.id("survival_mining");
   private static final ResourceLocation MOD_DAMAGE = FrontierHunts.id("survival_strength");
   private static final ResourceLocation MOD_HEALTH = FrontierHunts.id("survival_vigor");
   private static final Map<UUID, BlockPos> LAST_CLICKED = new HashMap<>();

   // warning bits (sent to the HUD)
   public static final int W_PROTEIN_LOW = 1, W_PROTEIN_EMPTY = 2, W_FAT_LOW = 4, W_FAT_EMPTY = 8, W_ENERGY_LOW = 16, W_ENERGY_EMPTY = 32,
      W_CHILLY = 64, W_SHIVER = 128, W_HYPOTHERMIA = 256, W_FREEZING = 512, W_HOT = 1024, W_HEATSTROKE = 2048, W_WET = 4096, W_STARVING = 8192;

   public static PlayerSurvival state(ServerPlayer p) {
      return LIVE.computeIfAbsent(p.getUUID(), u -> {
         PlayerSurvival s = new PlayerSurvival();
         s.load(p.getPersistentData().getCompound(PlayerSurvival.KEY));
         return s;
      });
   }

   static boolean exempt(ServerPlayer p) {
      return p.isCreative() || p.isSpectator() || !p.isAlive();
   }

   // ============================================================================================ tick

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p.connection == null) {
         return;
      }
      if ((p.tickCount + p.getId()) % 20 != 0) {
         return;
      }
      try {
         update(p);
      } catch (RuntimeException ex) {
         NutritionTable.LOG.warn("Frontier survival: update failed for {}: {}", p.getGameProfile().getName(), ex.toString());
      }
   }

   static void update(ServerPlayer p) {
      PlayerSurvival st = state(p);
      SurvivalMath.Mode mode = SurvivalConfig.mode();
      st.updates++;
      if (!mode.on()) {
         clearModifiers(p, st);
         sync(p, st, mode, false);
         return;
      }
      ServerLevel level = p.serverLevel();
      long now = level.getGameTime();
      boolean exempt = exempt(p);
      boolean tempOn = SurvivalConfig.temperature();
      if (com.formaworks.frontierhunts.academy.Academy.survivalFrozen(p)) { // [academy] meters and body heat hold still in the training grounds
         st.lastExhaustion = -1F;
         clearModifiers(p, st);
         sync(p, st, mode, true);
         return;
      }
      if (exempt) {
         st.heat = 0F;
         st.lastExhaustion = -1F;
         clearModifiers(p, st);
         sync(p, st, mode, true);
         return;
      }

      // ---------------------------------------------------------------- environment
      BlockPos feet = p.blockPosition();
      BlockPos head = BlockPos.containing(p.getEyePosition());
      // [shelter] the shared shelter detector + fires every second (was every 3 s), so the gauge reacts as you step inside
      st.shelter = Thermal.shelter(p);
      st.heatBonus = Thermal.heat(level, feet, head, st.shelter.enclosure());
      Clothing.Outfit outfit = Clothing.outfit(p);
      st.windProof = outfit.wind();
      st.waterProof = outfit.water();
      st.mittens = outfit.mittens();
      float rawAir = Thermal.air(level, head);
      float still = Thermal.sheltered(rawAir, st.shelter);
      boolean dugIn = Thermal.dugIn(level, head); // [1.2.5] a hole in the ground
      if (dugIn) {
         still = SurvivalMath.dugInAir(still);
      }
      float air = SurvivalMath.heatedAir(still, st.shelter.enclosure(), st.heatBonus); // [shelter] a fire heats an enclosed room
      float exposure = st.shelter.exposure();
      st.air = air;
      st.store = SurvivalMath.storeFor(still + st.heatBonus * 0.5F); // food in the pack: as before (still air + half the fire)

      // wetness
      if (p.isInWater()) {
         st.wet = Math.min(1F, st.wet + (p.isUnderWater() ? 1F : 0.35F));
      } else if (st.shelter.precipBlock() < 0.5F && level.isRainingAt(head)) { // [shelter] dry under a tent / roof
         st.wet = Math.min(1F, st.wet + 0.02F * (1F - st.waterProof));
      } else if (exposure > 0.5F && Thermal.snowingAt(level, head)) {
         st.wet = Math.min(1F, st.wet + 0.004F * (1F - st.waterProof));
      } else {
         st.wet = Math.max(0F, st.wet - (0.006F + Math.max(0F, air - 10F) * 0.0008F + st.heatBonus * 0.0025F));
      }

      float felt;
      float ins = outfit.insulation() * (1F - 0.6F * st.wet * (1F - st.waterProof));
      ins += st.fat >= 60F ? 0.4F : (st.fat <= 20F ? -0.4F : 0F);
      if (p.isInWater()) {
         float water = Math.max(1F, Math.min(24F, rawAir)) - 2F;
         felt = water - (p.isUnderWater() ? 2F : 0F) + st.heatBonus * 0.3F;
         ins = Math.max(0.6F, ins * 0.3F);
      } else {
         float wind = Thermal.windSpeed(level, head);
         // [shelter] radiant fire warmth (part of it already heats an enclosed room), driven snow/rain by exposure, wind chill
         // by wind exposure only (walls without a roof still break the wind)
         felt = air + SurvivalMath.radiant(st.heatBonus, st.shelter.enclosure()) - Thermal.weatherCold(level, head, dugIn ? exposure * 0.5F : exposure)
               * SurvivalMath.weatherShield(st.windProof, st.waterProof) // [clothing] a good shell keeps driven snow out
            - SurvivalMath.windChill(wind, dugIn ? 0F : st.shelter.windExposure(), st.windProof);
         felt -= st.wet * 9F * (1F - st.waterProof * 0.6F);
         // [1.2.5] close to a real fire you are warm, whatever the weather (it dries you too)
         float fire = Thermal.fireFloor(level, feet, head);
         if (fire > felt) {
            felt = fire;
            st.wet = Math.max(0F, st.wet - 0.01F);
         }
         // [1.2.5] dug into the ground (or a snow cave) out of the wind, your own heat stays with you: you stop losing
         // heat and slowly warm back up, though only a fire makes it cosy
         if (dugIn) {
            felt = Math.max(felt, 11F);
         }
      }
      if (p.isSleeping()) {
         BlockState bed = p.getSleepingPos().map(level::getBlockState).orElse(null);
         // blankets: a bed, a tent cot; a hide bedroll is warmer; [shelter] a tent around you adds more
         felt += SurvivalMath.sleepWarmth(bed != null && bed.getBlock() instanceof HideBedrollBlock, st.shelter.tent());
         // [1.2.5] tucked into a bed, a bedroll or a tent's bed you warm up, whatever it is like outside
         felt = Math.max(felt, 21F);
      }
      felt += com.formaworks.frontierhunts.outfitter.OutfitterItem.warmth(p, level.getGameTime()); // [1.1.7] hand warmers, camp coffee
      st.felt = felt;
      st.insulation = ins;

      // ---------------------------------------------------------------- body heat
      float before = st.heat;
      if (tempOn) {
         float work = p.isSprinting() ? 1F : (p.getDeltaMovement().horizontalDistanceSqr() > 0.004 ? 0.4F : 0F);
         st.heat = SurvivalMath.stepHeat(st.heat, felt, ins, 1F, mode.cold * SurvivalJournal.coldMultiplier(p), work, Math.min(1F, st.energy / 40F),
            st.shelter.enclosure()); // [integ4] Thick Skin; [shelter] shelter slows heat loss, speeds rewarming
         if (mode == SurvivalMath.Mode.LIGHT) {
            st.heat = Math.max(st.heat, -85F);
         }
      } else {
         st.heat = 0F;
      }
      st.trend = st.trend * 0.6F + (st.heat - before) * 0.4F;
      st.coldestBody = Math.min(st.coldestBody, st.heat);

      // ---------------------------------------------------------------- nutrition
      FoodData fd = p.getFoodData();
      float exh = fd.getExhaustionLevel();
      float spent = st.lastExhaustion < 0F ? 0F : exh - st.lastExhaustion;
      if (spent < -0.05F) {
         spent += 4F; // vanilla took 4 off the bar (a hunger point)
      } else if (spent < 0F) {
         spent = 0F; // [integ4] a small refund (journal Trail Legs) is not a hunger point
      }
      st.lastExhaustion = exh;
      float coldness = SurvivalMath.coldness(st.heat), hotness = SurvivalMath.hotness(st.heat);
      float scale = SurvivalConfig.drainScale();
      float fatPenalty = st.fat <= SurvivalMath.EMPTY ? 1.5F : 1F; // nothing to burn but sugar and muscle
      st.protein = Math.max(0F, st.protein - SurvivalMath.drain(0, 1F, spent, coldness, hotness, mode.drain, scale) * (st.fat <= SurvivalMath.EMPTY ? 1.3F : 1F));
      st.fat = Math.max(0F, st.fat - SurvivalMath.drain(1, 1F, spent, coldness, hotness, mode.drain, scale));
      st.energy = Math.max(0F, st.energy - SurvivalMath.drain(2, 1F, spent, coldness, hotness, mode.drain, scale) * fatPenalty);

      // metabolism also empties the vanilla hunger bar a little, so an idle body gets hungry and can eat again;
      // re-read afterwards so it is not counted as work next second
      if (level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
         fd.addExhaustion(SurvivalMath.METABOLISM * mode.drain * scale);
         st.lastExhaustion = fd.getExhaustionLevel();
      }

      boolean vigor = SurvivalMath.vigor(st.protein, st.fat, st.energy, st.lastGameMeal < 0L ? -1L : now - st.lastGameMeal);
      if (vigor) {
         st.vigorSeconds++;
      }
      int empties = SurvivalMath.empties(st.protein, st.fat, st.energy);
      if (empties > 0) {
         st.hungrySeconds++;
      }

      // ---------------------------------------------------------------- effects
      applyModifiers(p, st, mode, tempOn, vigor);
      float regen = 1F;
      if (mode.debuffs) {
         if (st.protein <= SurvivalMath.EMPTY) {
            regen = mode.wastingRegen;
         } else if (st.protein <= SurvivalMath.LOW) {
            regen = 0.6F;
         }
         if (st.fat <= SurvivalMath.EMPTY) {
            regen *= 0.6F;
         }
      }
      if (tempOn && st.heat <= SurvivalMath.HYPOTHERMIA) {
         regen *= 0.5F;
      }
      if (vigor) {
         regen *= 1.35F;
      }
      st.regen = regen;

      // ---------------------------------------------------------------- damage
      int interval = empties >= 2 ? mode.starveTwo : (empties == 1 ? mode.starveOne : 0);
      boolean starving = false;
      if (interval > 0 && p.getHealth() > mode.starveFloor()) {
         starving = true;
         st.starveTimer += 20;
         if (st.starveTimer >= interval) {
            st.starveTimer = 0;
            Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(MALNUTRITION).orElse(null);
            if (type != null) {
               p.hurt(new DamageSource(type), 1.0F);
            } else {
               p.hurt(p.damageSources().starve(), 1.0F);
            }
         }
      } else {
         st.starveTimer = 0;
      }
      if (tempOn && mode.freezeAt < 0F && st.heat <= mode.freezeAt) {
         st.freezeTimer += 20;
         float iv = mode.freezeTicks * (st.heat <= -97F ? 0.6F : 1F);
         if (st.freezeTimer >= iv) {
            st.freezeTimer = 0;
            p.hurt(p.damageSources().freeze(), 1.0F);
            if (!p.isAlive()) {
               st.freezes++;
            }
         }
      } else {
         st.freezeTimer = 0;
      }

      // ---------------------------------------------------------------- food spoilage
      if (SurvivalConfig.spoilage() && st.updates % 10 == 5) {
         Inventory inv = p.getInventory();
         int lost = Perishable.sweep(inv, now, st.store, 0, inv.getContainerSize());
         Perishable.consolidate(inv, now);
         if (lost > 0) {
            st.spoiledLost += lost;
            p.displayClientMessage(Component.translatable("survival.frontierhunts.msg.spoiled").withStyle(ChatFormatting.GOLD), true);
            tip(p, Tip.SPOILED);
         }
      }

      // ---------------------------------------------------------------- warnings, tips, sync, save
      int warn = warnings(st, tempOn, starving);
      notify(p, st, warn);
      st.warned = warn;
      if (SeasonClock.season(level) == SeasonClock.Season.FALL && SurvivalConfig.leanSeasons() && st.updates % 30 == 7) {
         tip(p, Tip.STOCKUP);
      }
      sync(p, st, mode, false);
      if (st.updates % 10 == 0) {
         persist(p, st);
      }
   }

   static int warnings(PlayerSurvival st, boolean tempOn, boolean starving) {
      int w = 0;
      if (st.protein <= SurvivalMath.EMPTY) {
         w |= W_PROTEIN_EMPTY;
      } else if (st.protein <= SurvivalMath.LOW) {
         w |= W_PROTEIN_LOW;
      }
      if (st.fat <= SurvivalMath.EMPTY) {
         w |= W_FAT_EMPTY;
      } else if (st.fat <= SurvivalMath.LOW) {
         w |= W_FAT_LOW;
      }
      if (st.energy <= SurvivalMath.EMPTY) {
         w |= W_ENERGY_EMPTY;
      } else if (st.energy <= SurvivalMath.LOW) {
         w |= W_ENERGY_LOW;
      }
      if (tempOn) {
         if (st.heat <= -88F) {
            w |= W_FREEZING;
         } else if (st.heat <= SurvivalMath.HYPOTHERMIA) {
            w |= W_HYPOTHERMIA;
         } else if (st.heat <= SurvivalMath.SHIVER) {
            w |= W_SHIVER;
         } else if (st.heat <= SurvivalMath.CHILLY) {
            w |= W_CHILLY;
         }
         if (st.heat >= SurvivalMath.HEATSTROKE) {
            w |= W_HEATSTROKE;
         } else if (st.heat >= SurvivalMath.HOT) {
            w |= W_HOT;
         }
         if (st.wet >= 0.35F) {
            w |= W_WET;
         }
      }
      if (starving) {
         w |= W_STARVING;
      }
      return w;
   }

   /** One action-bar line when a serious condition starts (newest wins), plus the one-time Field School notes. */
   static void notify(ServerPlayer p, PlayerSurvival st, int warn) {
      int fresh = warn & ~st.warned;
      if (fresh == 0) {
         return;
      }
      String key = null;
      if ((fresh & W_FREEZING) != 0) {
         key = "freezing";
      } else if ((fresh & W_HYPOTHERMIA) != 0) {
         key = "hypothermia";
      } else if ((fresh & W_SHIVER) != 0) {
         key = "shiver";
      } else if ((fresh & W_STARVING) != 0) {
         key = "starving";
      } else if ((fresh & W_PROTEIN_EMPTY) != 0) {
         key = "protein_empty";
      } else if ((fresh & W_FAT_EMPTY) != 0) {
         key = "fat_empty";
      } else if ((fresh & W_ENERGY_EMPTY) != 0) {
         key = "energy_empty";
      } else if ((fresh & W_HEATSTROKE) != 0) {
         key = "heatstroke";
      } else if ((fresh & (W_PROTEIN_LOW | W_FAT_LOW)) != 0) {
         key = "meat_low";
      } else if ((fresh & W_ENERGY_LOW) != 0) {
         key = "energy_low";
      }
      if (key != null) {
         ChatFormatting c = key.equals("meat_low") || key.equals("energy_low") ? ChatFormatting.GOLD : ChatFormatting.RED;
         p.displayClientMessage(Component.translatable("survival.frontierhunts.msg." + key).withStyle(c), true);
      }
      if ((fresh & (W_PROTEIN_LOW | W_FAT_LOW | W_ENERGY_LOW | W_PROTEIN_EMPTY | W_FAT_EMPTY | W_ENERGY_EMPTY)) != 0) {
         tip(p, Tip.HUNGER);
      }
      if ((fresh & (W_SHIVER | W_HYPOTHERMIA | W_FREEZING)) != 0) {
         tip(p, Tip.COLD);
      }
   }

   static void tip(ServerPlayer p, Tip t) {
      try {
         FieldSchool.survivalTip(p, t);
      } catch (RuntimeException ignored) {
      }
   }

   // ============================================================================================ attributes

   static void applyModifiers(ServerPlayer p, PlayerSurvival st, SurvivalMath.Mode mode, boolean tempOn, boolean vigor) {
      double move = 0, attack = 0, mine = 0, dmg = 0, health = 0;
      if (mode.debuffs) {
         if (st.energy <= SurvivalMath.EMPTY) {
            move -= 0.15;
            attack -= 0.2;
            mine -= 0.35;
         } else if (st.energy <= SurvivalMath.LOW) {
            attack -= 0.1;
            mine -= 0.15;
         }
         if (st.protein <= SurvivalMath.EMPTY) {
            dmg -= 0.25;
         } else if (st.protein <= SurvivalMath.LOW) {
            dmg -= 0.1;
         }
      }
      if (tempOn) {
         double k = mode.debuffs ? 1.0 : 0.5;
         if (st.heat <= SurvivalMath.HYPOTHERMIA) {
            move -= 0.2 * k;
            mine -= 0.35 * k;
            attack -= 0.15 * k;
         } else if (st.heat <= SurvivalMath.SHIVER) {
            move -= 0.08 * k;
            mine -= 0.15 * k;
            attack -= 0.1 * k;
         }
         if (st.heat >= SurvivalMath.HEATSTROKE) {
            move -= 0.1 * k;
         }
      }
      if (vigor) {
         health += 4.0;
         move += 0.03;
      }
      st.modMove = set(p, Attributes.MOVEMENT_SPEED, MOD_MOVE, st.modMove, move, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      st.modAttack = set(p, Attributes.ATTACK_SPEED, MOD_ATTACK, st.modAttack, attack, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      st.modMine = set(p, Attributes.BLOCK_BREAK_SPEED, MOD_MINE, st.modMine, mine, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      st.modDamage = set(p, Attributes.ATTACK_DAMAGE, MOD_DAMAGE, st.modDamage, dmg, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      double before = st.modHealth;
      st.modHealth = set(p, Attributes.MAX_HEALTH, MOD_HEALTH, st.modHealth, health, AttributeModifier.Operation.ADD_VALUE);
      if (st.modHealth < before && p.getHealth() > p.getMaxHealth()) {
         p.setHealth(p.getMaxHealth());
      }
   }

   private static double set(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double current, double target, AttributeModifier.Operation op) {
      AttributeInstance inst = p.getAttribute(attr);
      if (inst == null) {
         return current;
      }
      boolean present = inst.getModifier(id) != null;
      if (Math.abs(current - target) < 1.0E-4 && present == (target != 0.0)) {
         return current;
      }
      inst.removeModifier(id);
      if (target != 0.0) {
         inst.addTransientModifier(new AttributeModifier(id, target, op));
      }
      return target;
   }

   static void clearModifiers(ServerPlayer p, PlayerSurvival st) {
      st.modMove = set(p, Attributes.MOVEMENT_SPEED, MOD_MOVE, st.modMove, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      st.modAttack = set(p, Attributes.ATTACK_SPEED, MOD_ATTACK, st.modAttack, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      st.modMine = set(p, Attributes.BLOCK_BREAK_SPEED, MOD_MINE, st.modMine, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      st.modDamage = set(p, Attributes.ATTACK_DAMAGE, MOD_DAMAGE, st.modDamage, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      st.modHealth = set(p, Attributes.MAX_HEALTH, MOD_HEALTH, st.modHealth, 0, AttributeModifier.Operation.ADD_VALUE);
      if (p.getHealth() > p.getMaxHealth()) {
         p.setHealth(p.getMaxHealth());
      }
      st.regen = 1F;
   }

   // ============================================================================================ sync / persistence

   static void sync(ServerPlayer p, PlayerSurvival st, SurvivalMath.Mode mode, boolean exempt) {
      long now = p.serverLevel().getGameTime();
      int flags = 0;
      if (SurvivalConfig.spoilage()) {
         flags |= SurvivalNetwork.State.F_SPOILAGE;
      }
      if (SurvivalConfig.temperature()) {
         flags |= SurvivalNetwork.State.F_TEMPERATURE;
      }
      if (SurvivalMath.vigor(st.protein, st.fat, st.energy, st.lastGameMeal < 0L ? -1L : now - st.lastGameMeal)) {
         flags |= SurvivalNetwork.State.F_VIGOR;
      }
      if (st.shelter.roofed()) {
         flags |= SurvivalNetwork.State.F_SHELTER;
      }
      if (st.shelter.enclosed()) {
         flags |= SurvivalNetwork.State.F_INDOORS;
      }
      if (st.shelter.tent()) {
         flags |= SurvivalNetwork.State.F_TENT; // [shelter]
      }
      if (st.heatBonus >= 3F) {
         flags |= SurvivalNetwork.State.F_FIRE;
      }
      if (st.mittens) {
         flags |= SurvivalNetwork.State.F_MITTENS;
      }
      if (exempt) {
         flags |= SurvivalNetwork.State.F_EXEMPT;
      }
      SurvivalNetwork.State s = new SurvivalNetwork.State(
         mode.ordinal(), flags, st.protein, st.fat, st.energy, st.heat, st.felt, st.insulation, st.wet, st.trend, st.warned
      );
      int hash = java.util.Objects.hash(
         s.mode(), s.flags(), Math.round(s.protein() * 2F), Math.round(s.fat() * 2F), Math.round(s.energy() * 2F), Math.round(s.heat()), Math.round(s.felt() * 2F),
         Math.round(s.insulation() * 10F), Math.round(s.wet() * 20F), Math.round(s.trend() * 4F), s.warn()
      );
      if (hash != st.sentHash || now - st.lastSent >= 100L || now < st.lastSent) {
         st.sentHash = hash;
         st.lastSent = now;
         SurvivalNetwork.send(p, s);
      }
   }

   static void persist(ServerPlayer p, PlayerSurvival st) {
      p.getPersistentData().put(PlayerSurvival.KEY, st.save());
   }

   public static void forceSync(ServerPlayer p) {
      PlayerSurvival st = state(p);
      st.sentHash = 0;
      st.lastSent = Long.MIN_VALUE;
      sync(p, st, SurvivalConfig.mode(), exempt(p));
   }

   // ============================================================================================ eating

   @SubscribeEvent
   public static void ate(LivingEntityUseItemEvent.Finish e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !SurvivalConfig.mode().on() || exempt(p)) {
         return;
      }
      eat(p, e.getItem(), 1F);
   }

   /** Applies one serving of {@code food} (fraction {@code part}, e.g. a cake slice). */
   public static void eat(ServerPlayer p, ItemStack food, float part) {
      FoodValues v = NutritionTable.get(food);
      if (v == null) {
         return;
      }
      SurvivalMath.Mode mode = SurvivalConfig.mode();
      PlayerSurvival st = state(p);
      long now = p.serverLevel().getGameTime();
      float spoil = Perishable.spoil(food, now, false);
      float fv = SurvivalMath.freshnessValue(spoil) * part * (v.raw() ? 0.85F : 1F);
      if (v.source() == FoodValues.Source.GAME && !v.spoiled()) {
         fv *= SurvivalJournal.gameNutrition(p); // [integ4] journal Woodcraft perk Provider
      }
      float pf = 1F, ff = 1F, ef = 1F;
      switch (v.source()) {
         case FARM -> {
            pf = mode.farm;
            ff = mode.farm;
         }
         case CROP -> {
            pf = mode.crop;
            ff = mode.crop;
            ef = mode.crop;
         }
         case FISH -> {
            pf = mode.fish;
            ff = mode.fish;
            ef = mode.fish;
         }
         default -> {
         }
      }
      st.protein = Math.min(100F, st.protein + v.protein() * pf * fv);
      st.fat = Math.min(100F, st.fat + v.fat() * ff * fv);
      st.energy = Math.min(100F, st.energy + v.energy() * ef * fv);
      st.meals++;
      switch (v.source()) {
         case GAME -> {
            st.gameMeals++;
            st.lastGameMeal = now;
         }
         case FISH -> st.fishMeals++;
         case FARM -> st.farmMeals++;
         default -> {
         }
      }
      if (v.spoiled() || spoil >= 1F) {
         st.spoiledEaten++;
         p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 1));
         p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 240, 0));
         if (mode == SurvivalMath.Mode.HARDCORE) {
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
         }
         st.protein = Math.max(0F, st.protein - 6F);
         st.energy = Math.max(0F, st.energy - 8F);
         p.displayClientMessage(Component.translatable("survival.frontierhunts.msg.sick").withStyle(ChatFormatting.RED), true);
      } else if (spoil >= 0.8F && p.getRandom().nextFloat() < 0.35F) {
         p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 0));
         p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
         p.displayClientMessage(Component.translatable("survival.frontierhunts.msg.queasy").withStyle(ChatFormatting.GOLD), true);
      }
      forceSync(p);
   }

   @SubscribeEvent
   public static void cake(PlayerInteractEvent.RightClickBlock e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         LAST_CLICKED.put(p.getUUID(), e.getPos());
         BlockState st = p.level().getBlockState(e.getPos());
         if (st.getBlock() instanceof CakeBlock && p.canEat(false) && SurvivalConfig.mode().on() && !exempt(p) && !p.isSecondaryUseActive()) {
            eat(p, new ItemStack(st.getBlock().asItem()), 1F / 7F);
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void heal(LivingHealEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && e.getAmount() <= 1.0001F && SurvivalConfig.mode().on() && !exempt(p)) {
         float r = state(p).regen;
         if (r != 1F) {
            e.setAmount(e.getAmount() * r);
            if (e.getAmount() <= 0F) {
               e.setCanceled(true);
            }
         }
      }
   }

   // ============================================================================================ sleep

   @SubscribeEvent
   public static void slept(SleepFinishedTimeEvent e) {
      if (!(e.getLevel() instanceof ServerLevel level)) {
         return;
      }
      SurvivalMath.Mode mode = SurvivalConfig.mode();
      if (!mode.on()) {
         return;
      }
      for (ServerPlayer p : level.players()) {
         if (!p.isSleeping() || exempt(p)) {
            continue;
         }
         PlayerSurvival st = state(p);
         float k = mode.drain * SurvivalConfig.drainScale();
         st.energy = Math.max(0F, st.energy - 10F * k);
         st.protein = Math.max(0F, st.protein - 6F * k);
         st.fat = Math.max(0F, st.fat - 6F * k);
         if (SurvivalConfig.temperature()) {
            if (st.felt < SurvivalMath.comfortLow(st.insulation) - 3F) {
               st.heat = Math.min(st.heat, mode == SurvivalMath.Mode.LIGHT ? -35F : -55F);
               st.coldNights++;
               p.sendSystemMessage(Component.translatable("survival.frontierhunts.msg.cold_night").withStyle(ChatFormatting.AQUA));
            } else {
               st.heat = Math.max(Math.min(st.heat, 20F), 0F);
            }
         }
         st.lastExhaustion = -1F;
         forceSync(p);
      }
   }

   @SubscribeEvent
   public static void spawnPoint(PlayerSetSpawnEvent e) {
      if (e.isForced() || e.getNewSpawn() == null) {
         return;
      }
      if (e.getEntity().level().getBlockState(e.getNewSpawn()).getBlock() instanceof HideBedrollBlock) {
         e.setCanceled(true); // a bedroll is for the night, not a home
      }
   }

   // ============================================================================================ food stamps and stores

   @SubscribeEvent
   public static void itemJoin(EntityJoinLevelEvent e) {
      if (e.getLevel().isClientSide || !(e.getEntity() instanceof ItemEntity ie) || !SurvivalConfig.spoilage()) {
         return;
      }
      ItemStack s = ie.getItem();
      if (Perishable.values(s, false) != null && Perishable.get(s) == null) {
         ItemStack c = s.copy();
         Perishable.stamp(c, e.getLevel().getGameTime(), SurvivalMath.AMBIENT);
         ie.setItem(c);
      }
   }

   @SubscribeEvent
   public static void crafted(PlayerEvent.ItemCraftedEvent e) {
      if (e.getEntity().level().isClientSide || !SurvivalConfig.spoilage()) {
         return;
      }
      ItemStack r = e.getCrafting();
      long now = e.getEntity().level().getGameTime();
      if (r.is(SurvivalContent.JERKY.get()) || r.is(SurvivalContent.PEMMICAN.get())
         || Perishable.get(r) != null && Perishable.get(r).cure() == SurvivalMath.SALTED) {
         if (e.getEntity() instanceof ServerPlayer p) {
            state(p).preserved += r.getCount();
         }
      }
      if (Perishable.values(r, false) == null || Perishable.get(r) != null) {
         return;
      }
      Perishable.stamp(r, now, SurvivalMath.AMBIENT);
      float worst = 0F;
      Container in = e.getInventory();
      for (int i = 0; i < in.getContainerSize(); i++) {
         worst = Math.max(worst, Perishable.spoil(in.getItem(i), now, false));
      }
      Perishable.inherit(r, now, worst);
   }

   @SubscribeEvent
   public static void smelted(PlayerEvent.ItemSmeltedEvent e) {
      if (!e.getEntity().level().isClientSide && SurvivalConfig.spoilage()) {
         Perishable.stamp(e.getSmelting(), e.getEntity().level().getGameTime(), SurvivalMath.AMBIENT);
      }
   }

   @SubscribeEvent
   public static void opened(PlayerContainerEvent.Open e) {
      container(e, false);
   }

   @SubscribeEvent
   public static void closed(PlayerContainerEvent.Close e) {
      container(e, true);
   }

   private static void container(PlayerContainerEvent e, boolean closing) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !SurvivalConfig.spoilage() || e.getContainer() instanceof net.minecraft.world.inventory.InventoryMenu) {
         return;
      }
      BlockPos pos = LAST_CLICKED.get(p.getUUID());
      if (pos == null || pos.distSqr(p.blockPosition()) > 100.0) {
         return;
      }
      ServerLevel level = p.serverLevel();
      long now = level.getGameTime();
      int store = Thermal.storeAt(level, pos);
      int lost = 0;
      for (Slot slot : e.getContainer().slots) {
         if (slot.container instanceof Inventory) {
            continue;
         }
         ItemStack s = slot.getItem();
         if (s.isEmpty() || Perishable.values(s, false) == null) {
            continue;
         }
         ItemStack rotten = Perishable.spoiledReplacement(s, now);
         if (rotten != null) {
            slot.set(rotten);
            lost++;
         } else if (Perishable.store(s, now, store)) {
            slot.setChanged();
         }
      }
      if (lost > 0) {
         state(p).spoiledLost += lost;
         if (!closing) {
            p.displayClientMessage(Component.translatable("survival.frontierhunts.msg.spoiled_store").withStyle(ChatFormatting.GOLD), true);
         }
      }
   }

   // ============================================================================================ lifecycle

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         state(p);
         forceSync(p);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         PlayerSurvival st = LIVE.remove(p.getUUID());
         if (st != null) {
            persist(p, st);
         }
         LAST_CLICKED.remove(p.getUUID());
      }
   }

   @SubscribeEvent
   public static void save(PlayerEvent.SaveToFile e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         PlayerSurvival st = LIVE.get(p.getUUID());
         if (st != null) {
            persist(p, st);
         }
      }
   }

   @SubscribeEvent
   public static void clone(PlayerEvent.Clone e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) {
         return;
      }
      PlayerSurvival st = LIVE.get(p.getUUID());
      if (st == null) {
         st = new PlayerSurvival();
         st.load(e.getOriginal().getPersistentData().getCompound(PlayerSurvival.KEY));
         LIVE.put(p.getUUID(), st);
      }
      if (e.isWasDeath()) {
         st.respawn();
      }
      st.modMove = st.modAttack = st.modMine = st.modDamage = st.modHealth = 0;
      persist(p, st);
   }

   @SubscribeEvent
   public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         forceSync(p);
      }
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      LIVE.clear();
      LAST_CLICKED.clear();
   }
}
