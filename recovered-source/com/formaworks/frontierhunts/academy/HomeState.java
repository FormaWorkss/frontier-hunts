package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.rifle.RifleState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * [academy] Capture and restore of everything a hunter takes into the training grounds, and the live {@link
 * TrainingFlow.Host} for a {@link ServerPlayer}.
 */
public final class HomeState {
   private HomeState() {
   }

   public static CompoundTag capture(ServerPlayer p) {
      CompoundTag t = new CompoundTag();
      t.putInt("schema", 1);
      t.put("inventory", p.getInventory().save(new ListTag()));
      t.putInt("selected", p.getInventory().selected);
      t.putInt("xpLevel", p.experienceLevel);
      t.putFloat("xpProgress", p.experienceProgress);
      t.putInt("xpTotal", p.totalExperience);
      t.putFloat("health", p.getHealth());
      t.putFloat("absorption", p.getAbsorptionAmount());
      CompoundTag food = new CompoundTag();
      p.getFoodData().addAdditionalSaveData(food);
      t.put("food", food);
      ListTag effects = new ListTag();
      for (MobEffectInstance e : p.getActiveEffects()) {
         effects.add(e.save());
      }
      t.put("effects", effects);
      t.putInt("gameMode", p.gameMode.getGameModeForPlayer().getId());
      t.putString("dimension", p.level().dimension().location().toString());
      t.putDouble("x", p.getX());
      t.putDouble("y", p.getY());
      t.putDouble("z", p.getZ());
      t.putFloat("yaw", p.getYRot());
      t.putFloat("pitch", p.getXRot());
      t.putInt("fire", p.getRemainingFireTicks());
      t.putInt("air", p.getAirSupply());
      t.putInt("frozen", p.getTicksFrozen());
      t.putFloat("fall", p.fallDistance);
      return t;
   }

   /** Restores the snapshot. Inventory first, then stats, then (optionally) the move home. */
   public static void apply(ServerPlayer p, CompoundTag t, boolean position) {
      Inventory inv = p.getInventory();
      inv.clearContent();
      inv.load(t.getList("inventory", Tag.TAG_COMPOUND));
      inv.selected = Mth.clamp(t.getInt("selected"), 0, 8);
      p.setExperienceLevels(Math.max(0, t.getInt("xpLevel")));
      p.experienceProgress = Mth.clamp(t.getFloat("xpProgress"), 0.0F, 0.9999F);
      p.totalExperience = Math.max(0, t.getInt("xpTotal"));
      p.removeAllEffects();
      ListTag effects = t.getList("effects", Tag.TAG_COMPOUND);
      for (int i = 0; i < effects.size(); i++) {
         MobEffectInstance e = MobEffectInstance.load(effects.getCompound(i));
         if (e != null) {
            p.addEffect(e);
         }
      }
      GameType mode = GameType.byId(t.getInt("gameMode"));
      if (p.gameMode.getGameModeForPlayer() != mode) {
         p.setGameMode(mode);
      }
      if (t.contains("food", Tag.TAG_COMPOUND)) {
         p.getFoodData().readAdditionalSaveData(t.getCompound("food"));
      }
      float health = t.getFloat("health");
      p.setHealth(Mth.clamp(health <= 0.0F ? p.getMaxHealth() : health, 1.0F, p.getMaxHealth()));
      p.setAbsorptionAmount(Math.max(0.0F, t.getFloat("absorption")));
      p.setRemainingFireTicks(Math.max(0, t.getInt("fire")));
      p.setAirSupply(Mth.clamp(t.getInt("air"), 0, p.getMaxAirSupply()));
      p.setTicksFrozen(Math.max(0, t.getInt("frozen")));
      p.inventoryMenu.broadcastChanges();
      if (position) {
         moveHome(p, t);
      }
      p.fallDistance = Math.max(0.0F, t.getFloat("fall"));
   }

   static ServerLevel homeLevel(MinecraftServer server, CompoundTag t) {
      ResourceLocation id = ResourceLocation.tryParse(t.getString("dimension"));
      ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
      return level == null || Academy.isTrainingLevel(level) ? null : level;
   }

   private static void moveHome(ServerPlayer p, CompoundTag t) {
      ServerLevel level = homeLevel(p.server, t);
      double x = t.getDouble("x"), y = t.getDouble("y"), z = t.getDouble("z");
      if (level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 2.9E7 || Math.abs(z) > 2.9E7) {
         spawn(p);
         return;
      }
      TrainingService.moving(p, true);
      try {
         p.teleportTo(level, x, y, z, t.getFloat("yaw"), Mth.clamp(t.getFloat("pitch"), -90.0F, 90.0F));
      } finally {
         TrainingService.moving(p, false);
      }
   }

   /** Sends a player to their respawn point (or the world spawn): used when no home record exists. */
   static void spawn(ServerPlayer p) {
      MinecraftServer server = p.server;
      ServerLevel level = server.getLevel(p.getRespawnDimension());
      if (level == null || Academy.isTrainingLevel(level) || p.getRespawnPosition() == null) {
         level = server.overworld();
         var s = level.getSharedSpawnPos();
         TrainingService.moving(p, true);
         try {
            p.teleportTo(level, s.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.getX(), s.getZ()), s.getZ() + 0.5,
               p.getYRot(), p.getXRot());
         } finally {
            TrainingService.moving(p, false);
         }
         return;
      }
      var pos = p.getRespawnPosition();
      Vec3 at;
      float yaw = p.getRespawnAngle();
      try {
         var safe = level.getBlockState(pos).getRespawnPosition(net.minecraft.world.entity.EntityType.PLAYER, level, pos, yaw); // no side effects (anchor charge)
         at = safe.isPresent() ? safe.get().position() : Vec3.atBottomCenterOf(pos).add(0.0, 1.0, 0.0);
         if (safe.isPresent()) {
            yaw = safe.get().yaw();
         }
      } catch (RuntimeException ex) {
         at = Vec3.atBottomCenterOf(pos).add(0.0, 1.0, 0.0);
      }
      TrainingService.moving(p, true);
      try {
         p.teleportTo(level, at.x, at.y, at.z, yaw, 0.0F);
      } finally {
         TrainingService.moving(p, false);
      }
   }

   /** Empties the hunter for training: no items, no effects, adventure mode, rested and fed. */
   static void prepare(ServerPlayer p, Course course, String nonce) {
      p.closeContainer();
      p.stopUsingItem();
      p.stopRiding();
      if (p.isSleeping()) {
         p.stopSleeping();
      }
      p.getInventory().clearContent();
      p.removeAllEffects();
      if (p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
         p.setGameMode(GameType.ADVENTURE);
      }
      p.setHealth(p.getMaxHealth());
      p.getFoodData().setFoodLevel(20);
      p.getFoodData().setSaturation(10.0F);
      p.getFoodData().setExhaustion(0.0F);
      p.clearFire();
      p.setAirSupply(p.getMaxAirSupply());
      p.setTicksFrozen(0);
      p.resetFallDistance();
      lend(p, course, nonce);
      p.inventoryMenu.broadcastChanges();
   }

   /** The course kit. Every stack is tagged as lent (so it can never leave the grounds) and named as range issue. */
   static void lend(ServerPlayer p, Course course, String nonce) {
      int slot = 0;
      for (String spec : course.gear) {
         ItemStack st = stack(spec, nonce);
         if (st.isEmpty()) {
            continue;
         }
         if (st.getItem() == BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:ridgeline_rifle"))) {
            try {
               new RifleState(2, true, false, 0, 0L, "", "", -1000L, RifleState.capacity(st)).write(st); // loaded and chambered
            } catch (RuntimeException ignored) {
            }
         }
         if (slot < 9) {
            p.getInventory().setItem(slot++, st);
         } else {
            p.getInventory().add(st);
         }
      }
      for (String spec : course.armor) {
         ItemStack st = stack(spec, nonce);
         if (!st.isEmpty()) {
            EquipmentSlot es = p.getEquipmentSlotForItem(st);
            if (es.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
               p.setItemSlot(es, st);
            } else {
               p.getInventory().add(st);
            }
         }
      }
      p.getInventory().selected = 0;
   }

   static ItemStack stack(String spec, String nonce) {
      // [onboard] "id*count#arrow_tip": arrows with a fitted tip (field points / broadheads for the archery range)
      String tip = null;
      int hash = spec.indexOf('#');
      if (hash >= 0) {
         tip = spec.substring(hash + 1);
         spec = spec.substring(0, hash);
      }
      int star = spec.indexOf('*');
      String id = star < 0 ? spec : spec.substring(0, star);
      int count = 1;
      if (star >= 0) {
         try {
            count = Integer.parseInt(spec.substring(star + 1));
         } catch (NumberFormatException ignored) {
         }
      }
      ResourceLocation rl = ResourceLocation.tryParse(id);
      Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
      if (item == Items.AIR) {
         return ItemStack.EMPTY;
      }
      ItemStack st = new ItemStack(item, Mth.clamp(count, 1, item.getDefaultMaxStackSize()));
      CompoundTag data = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
      data.putString(Academy.LENT, nonce);
      if (tip != null && !tip.isEmpty() && !"fixed_broadhead".equals(tip)) {
         data.putString("arrow_tip", tip); // [onboard] same key as hunting.ArrowTip.KEY (fixed broadhead = no key)
      }
      st.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
      st.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("academy.frontierhunts.lent").withStyle(s -> s.withColor(0xB99859).withItalic(true)))));
      return st;
   }

   public static boolean lent(ItemStack st) {
      if (st.isEmpty()) {
         return false;
      }
      CustomData d = st.get(DataComponents.CUSTOM_DATA);
      return d != null && d.contains(Academy.LENT);
   }

   /** Removes lent gear from a player outside the grounds. Returns the number of stacks removed. */
   static int sweep(ServerPlayer p) {
      Inventory inv = p.getInventory();
      int n = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         if (lent(inv.getItem(i))) {
            inv.setItem(i, ItemStack.EMPTY);
            n++;
         }
      }
      if (lent(p.containerMenu.getCarried())) {
         p.containerMenu.setCarried(ItemStack.EMPTY);
         n++;
      }
      if (n > 0) {
         p.inventoryMenu.broadcastChanges();
      }
      return n;
   }

   /** The live host for one online player. */
   public static final class PlayerHost implements TrainingFlow.Host {
      private final ServerPlayer p;

      public PlayerHost(ServerPlayer p) {
         this.p = p;
      }

      @Override
      public UUID id() {
         return this.p.getUUID();
      }

      @Override
      public CompoundTag persisted() {
         CompoundTag root = this.p.getPersistentData();
         if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
         }
         return root.getCompound(Player.PERSISTED_NBT_TAG);
      }

      @Override
      public CompoundTag captureHome() {
         return capture(this.p);
      }

      @Override
      public void prepareForTraining(Course course, String nonce) {
         prepare(this.p, course, nonce);
      }

      @Override
      public boolean enterPlot(Course course, int slot) {
         return TrainingService.enterPlot(this.p, course, slot);
      }

      @Override
      public void wipeTraining() {
         this.p.closeContainer();
         this.p.stopUsingItem();
         this.p.getInventory().clearContent();
         this.p.containerMenu.setCarried(ItemStack.EMPTY);
         this.p.removeAllEffects();
      }

      @Override
      public void applyHome(CompoundTag home, boolean position) {
         apply(this.p, home, position);
      }

      @Override
      public boolean inTraining() {
         return Academy.inTraining(this.p);
      }
   }

   /** Item ids of a list of stacks (debug/log). */
   static List<String> ids(List<ItemStack> stacks) {
      List<String> out = new ArrayList<>();
      for (ItemStack s : stacks) {
         out.add(BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount());
      }
      return out;
   }

   static boolean sameLevel(Level a, Level b) {
      return a != null && b != null && a.dimension() == b.dimension();
   }
}
