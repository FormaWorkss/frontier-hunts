package com.formaworks.frontierhunts.survival.block;

import com.formaworks.frontierhunts.survival.FoodValues;
import com.formaworks.frontierhunts.survival.NutritionTable;
import com.formaworks.frontierhunts.survival.Perishable;
import com.formaworks.frontierhunts.survival.SurvivalContent;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import com.formaworks.frontierhunts.survival.Thermal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** [survival] Drying rack contents: up to four strips, each with its own drying progress (0..1). Ticks every 5 s. */
public final class DryingRackEntity extends BlockEntity {
   public static final int MAX = 4;
   private static final int STEP = 100;

   private record Strip(Item raw, float progress) {
   }

   private final List<Strip> strips = new ArrayList<>();
   /** Last drying rate (per day) for the status line: negative while rain soaks the strips. */
   private float rate;

   public DryingRackEntity(BlockPos pos, BlockState state) {
      super(SurvivalContent.DRYING_RACK_ENTITY.get(), pos, state);
   }

   public static boolean dryable(ItemStack s) {
      if (s.isEmpty() || s.is(SurvivalContent.JERKY.get())) {
         return false;
      }
      FoodValues v = NutritionTable.any(s.getItem());
      return v != null && v.dry() && !v.spoiled();
   }

   public int count() {
      return this.strips.size();
   }

   /** Hangs up to {@code max} pieces from the stack; returns how many. */
   public int hang(ItemStack stack, int max, boolean infinite) {
      int n = 0;
      while (n < max && this.strips.size() < MAX && !stack.isEmpty()) {
         this.strips.add(new Strip(stack.getItem(), 0F));
         if (!infinite) {
            stack.shrink(1);
         }
         n++;
      }
      if (n > 0) {
         if (this.level != null) {
            this.rate = this.dryingRate(this.level, this.worldPosition);
         }
         this.changed();
      }
      return n;
   }

   /** Takes one strip down: a finished one if any, else the last hung. */
   public ItemStack take() {
      if (this.strips.isEmpty()) {
         return ItemStack.EMPTY;
      }
      int idx = this.strips.size() - 1;
      for (int i = 0; i < this.strips.size(); i++) {
         if (this.strips.get(i).progress >= 1F) {
            idx = i;
            break;
         }
      }
      Strip s = this.strips.remove(idx);
      this.changed();
      return this.out(s);
   }

   private ItemStack out(Strip s) {
      if (s.progress >= 1F) {
         ItemStack j = new ItemStack(SurvivalContent.JERKY.get());
         if (this.level != null) {
            Perishable.stamp(j, this.level.getGameTime(), SurvivalMath.AMBIENT);
         }
         return j;
      }
      ItemStack r = new ItemStack(s.raw);
      if (this.level != null) {
         Perishable.stamp(r, this.level.getGameTime(), SurvivalMath.AMBIENT);
         Perishable.inherit(r, this.level.getGameTime(), 0.25F + s.progress * 0.5F);
      }
      return r;
   }

   public void dropAll() {
      if (this.level == null) {
         return;
      }
      for (Strip s : this.strips) {
         Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.6, this.worldPosition.getZ() + 0.5, this.out(s));
      }
      this.strips.clear();
   }

   public String conditionKey() {
      return "survival.frontierhunts.rack.cond." + (this.rate < 0F ? "rain" : (this.rate >= 0.9F ? "good" : (this.rate >= 0.4F ? "slow" : "poor")));
   }

   public Component statusLine() {
      int done = 0;
      for (Strip s : this.strips) {
         if (s.progress >= 1F) {
            done++;
         }
      }
      return Component.translatable("survival.frontierhunts.rack.status", this.strips.size(), done, Component.translatable(this.conditionKey()));
   }

   /** Drying per day under current conditions. */
   float dryingRate(Level level, BlockPos pos) {
      BlockPos up = pos.above();
      if (level.isRainingAt(up)) {
         return -0.6F;
      }
      boolean open = level.canSeeSky(up);
      boolean day = level.isDay();
      float r = open ? (day ? 1.0F : 0.35F) : 0.25F;
      float heat = 0F;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
               heat += Thermal.heatOf(level.getBlockState(m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)));
            }
         }
      }
      if (heat >= 3F) {
         r += 1.0F; // drying over the smoke of a fire works day and night
      }
      Biome b = level.getBiome(pos).value();
      if (!b.hasPrecipitation()) {
         r *= 1.5F;
      } else if (b.getModifiedClimateSettings().downfall() > 0.8F) {
         r *= 0.6F;
      }
      if (Thermal.air(level, pos) < -2F) {
         r *= 0.5F;
      }
      return r;
   }

   public static void tick(Level level, BlockPos pos, BlockState state, DryingRackEntity be) {
      if ((level.getGameTime() + pos.asLong()) % STEP != 0 || be.strips.isEmpty()) {
         return;
      }
      be.rate = be.dryingRate(level, pos);
      float d = be.rate * STEP / (float) SurvivalMath.DAY;
      boolean changed = false;
      for (int i = 0; i < be.strips.size(); i++) {
         Strip s = be.strips.get(i);
         if (s.progress >= 1F) {
            continue;
         }
         float p = Math.max(0F, Math.min(1F, s.progress + d));
         if (p != s.progress) {
            be.strips.set(i, new Strip(s.raw, p));
            changed = true;
         }
      }
      if (changed) {
         be.changed();
      }
   }

   private void changed() {
      this.setChanged();
      if (this.level != null && !this.level.isClientSide) {
         BlockState st = this.getBlockState();
         float min = 1F;
         for (Strip s : this.strips) {
            min = Math.min(min, s.progress);
         }
         int stage = this.strips.isEmpty() ? 0 : (min >= 1F ? 2 : (min >= 0.3F ? 1 : 0));
         BlockState ns = st.setValue(DryingRackBlock.LOAD, this.strips.size()).setValue(DryingRackBlock.STAGE, stage);
         if (ns != st) {
            this.level.setBlock(this.worldPosition, ns, 3);
         }
      }
   }

   @Override
   protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
      super.saveAdditional(tag, reg);
      ListTag list = new ListTag();
      for (Strip s : this.strips) {
         CompoundTag t = new CompoundTag();
         t.putString("item", BuiltInRegistries.ITEM.getKey(s.raw).toString());
         t.putFloat("progress", s.progress);
         list.add(t);
      }
      tag.put("strips", list);
   }

   @Override
   protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
      super.loadAdditional(tag, reg);
      this.strips.clear();
      ListTag list = tag.getList("strips", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size() && this.strips.size() < MAX; i++) {
         CompoundTag t = list.getCompound(i);
         ResourceLocation id = ResourceLocation.tryParse(t.getString("item"));
         Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
         if (item != Items.AIR) {
            float p = t.getFloat("progress");
            this.strips.add(new Strip(item, Float.isFinite(p) ? Math.max(0F, Math.min(1F, p)) : 0F));
         }
      }
   }
}
