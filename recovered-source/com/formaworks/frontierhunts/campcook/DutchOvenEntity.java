package com.formaworks.frontierhunts.campcook;

import com.formaworks.frontierhunts.survival.Perishable;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * [licence] The Camp Dutch Oven's contents and cooking. Slots: 0-5 ingredients, 6 bowls, 7 fuel (coals), 8 the dish.
 * Server tick: find the recipe the pot holds (cached until the contents change), heat it (fire below, else burn fuel),
 * advance; when done take one batch of ingredients and the bowls and serve the dish into slot 8, stamped fresh but no
 * fresher than its oldest ingredient (Frontier Survival spoilage). Progress cools off slowly without heat. Hoppers:
 * top = ingredients, sides = bowls and fuel, bottom = dishes.
 */
public class DutchOvenEntity extends BaseContainerBlockEntity implements WorldlyContainer {
   public static final int BOWL = 6, FUEL = 7, OUT = 8, SIZE = 9;
   private static final int[] TOP = {0, 1, 2, 3, 4, 5};
   private static final int[] SIDE = {BOWL, FUEL};
   private static final int[] BOTTOM = {OUT};
   private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
   int progress;
   int total;
   int burn;
   int burnMax;
   /** 0 none, 1 fire below, 2 coals */
   int heat;
   private RecipeHolder<CampCookingRecipe> recipe;
   private boolean dirty = true;
   /** progress, total, burn, burnMax, heat for the screen */
   final ContainerData data = new ContainerData() {
      @Override
      public int get(int i) {
         return switch (i) {
            case 0 -> DutchOvenEntity.this.progress;
            case 1 -> DutchOvenEntity.this.total;
            case 2 -> DutchOvenEntity.this.burn;
            case 3 -> DutchOvenEntity.this.burnMax;
            case 4 -> DutchOvenEntity.this.heat;
            default -> 0;
         };
      }

      @Override
      public void set(int i, int v) {
         switch (i) {
            case 0 -> DutchOvenEntity.this.progress = v;
            case 1 -> DutchOvenEntity.this.total = v;
            case 2 -> DutchOvenEntity.this.burn = v;
            case 3 -> DutchOvenEntity.this.burnMax = v;
            case 4 -> DutchOvenEntity.this.heat = v;
            default -> {
            }
         }
      }

      @Override
      public int getCount() {
         return 5;
      }
   };

   public DutchOvenEntity(BlockPos pos, BlockState state) {
      super(CampCookContent.DUTCH_OVEN_ENTITY.get(), pos, state);
   }

   @Override
   protected Component getDefaultName() {
      return Component.translatable("container.frontierhunts.dutch_oven");
   }

   @Override
   protected NonNullList<ItemStack> getItems() {
      return this.items;
   }

   @Override
   protected void setItems(NonNullList<ItemStack> list) {
      this.items = list;
      this.dirty = true;
   }

   @Override
   protected AbstractContainerMenu createMenu(int id, Inventory inv) {
      return new DutchOvenMenu(id, inv, this, this.data);
   }

   @Override
   public int getContainerSize() {
      return SIZE;
   }

   @Override
   public void setChanged() {
      this.dirty = true;
      super.setChanged();
   }

   @Override
   public void setItem(int slot, ItemStack s) {
      super.setItem(slot, s);
      this.dirty = true;
   }

   @Override
   public boolean canPlaceItem(int slot, ItemStack s) {
      return switch (slot) {
         case OUT -> false;
         case BOWL -> s.is(Items.BOWL);
         case FUEL -> isFuel(s);
         default -> true;
      };
   }

   static boolean isFuel(ItemStack s) {
      return !s.isEmpty() && !s.is(Items.BOWL) && s.getBurnTime(RecipeType.SMELTING) > 0 && !s.is(Items.LAVA_BUCKET);
   }

   // ============================================================================================ hoppers

   @Override
   public int[] getSlotsForFace(Direction side) {
      return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDE;
   }

   @Override
   public boolean canPlaceItemThroughFace(int slot, ItemStack s, Direction side) {
      return this.canPlaceItem(slot, s);
   }

   @Override
   public boolean canTakeItemThroughFace(int slot, ItemStack s, Direction side) {
      return slot == OUT;
   }

   // ============================================================================================ cooking

   /** Comparator: how full the dish slot is. */
   int signal() {
      ItemStack o = this.items.get(OUT);
      return o.isEmpty() ? 0 : 1 + Math.round(14.0F * o.getCount() / o.getMaxStackSize());
   }

   CampCookingRecipe.PotInput input() {
      List<ItemStack> l = new ArrayList<>(CampCookingRecipe.SLOTS);
      for (int i = 0; i < CampCookingRecipe.SLOTS; i++) {
         l.add(this.items.get(i));
      }
      return new CampCookingRecipe.PotInput(l);
   }

   /** Heat source directly below: a lit campfire, fire, lava, magma or anything lit (furnace, smoker, stove). */
   static boolean fireBelow(Level level, BlockPos pos) {
      BlockState b = level.getBlockState(pos.below());
      if (b.is(Blocks.FIRE) || b.is(Blocks.SOUL_FIRE) || b.is(Blocks.LAVA) || b.is(Blocks.MAGMA_BLOCK)) {
         return true;
      }
      return b.hasProperty(BlockStateProperties.LIT) && b.getValue(BlockStateProperties.LIT);
   }

   private boolean canServe(CampCookingRecipe r) {
      if (this.items.get(BOWL).getCount() < r.bowls()) {
         return false;
      }
      ItemStack out = this.items.get(OUT);
      ItemStack res = r.result();
      if (out.isEmpty()) {
         return true;
      }
      return out.is(res.getItem()) && out.getCount() + res.getCount() <= out.getMaxStackSize();
   }

   /** Client: steam escaping under the lid rim and from the knob vent while it cooks (a few puffs a second). */
   public static void clientTick(Level level, BlockPos pos, BlockState state, DutchOvenEntity be) {
      if (!state.getValue(DutchOvenBlock.LIT)) {
         return;
      }
      net.minecraft.util.RandomSource r = level.random;
      double lidY = pos.getY() + (state.getValue(DutchOvenBlock.GRATE) ? 9.0 : 10.0) / 16.0;
      if (r.nextFloat() < 0.18F) {
         double a = r.nextDouble() * Math.PI * 2.0;
         level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5 + Math.cos(a) * 0.34, lidY - 0.04,
            pos.getZ() + 0.5 + Math.sin(a) * 0.34, Math.cos(a) * 0.004, 0.018 + r.nextDouble() * 0.012, Math.sin(a) * 0.004);
      }
      if (r.nextFloat() < 0.1F) {
         level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.08, lidY + 0.1,
            pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.08, 0.0, 0.03, 0.0);
      }
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, DutchOvenEntity be) {
      boolean changed = false;
      if (be.dirty) {
         be.dirty = false;
         CampCookingRecipe.PotInput in = be.input();
         Optional<RecipeHolder<CampCookingRecipe>> found = in.isEmpty() ? Optional.empty()
            : level.getRecipeManager().getRecipeFor(CampCookContent.TYPE.get(), in, level, be.recipe);
         RecipeHolder<CampCookingRecipe> r = found.orElse(null);
         if (r != be.recipe) {
            if (be.recipe != null || r == null) {
               be.progress = 0; // a different dish (a freshly loaded oven keeps its progress)
            }
            be.recipe = r;
         }
         be.total = r == null ? 0 : r.value().time();
      }
      CampCookingRecipe r = be.recipe == null ? null : be.recipe.value();
      boolean want = r != null && be.canServe(r);
      boolean below = fireBelow(level, pos);
      if (want && !below && be.burn <= 0) {
         ItemStack f = be.items.get(FUEL);
         if (isFuel(f)) {
            int t = f.getBurnTime(RecipeType.SMELTING);
            be.burn = be.burnMax = t;
            ItemStack rem = f.getCraftingRemainingItem();
            f.shrink(1);
            if (f.isEmpty() && !rem.isEmpty()) {
               be.items.set(FUEL, rem);
            }
            changed = true;
         }
      }
      int heat = below ? 1 : be.burn > 0 ? 2 : 0;
      if (be.burn > 0 && !below) {
         be.burn--;
      }
      be.heat = heat;
      boolean cooking = want && heat > 0;
      if (cooking) {
         be.progress++;
         if (be.progress >= be.total) {
            be.progress = 0;
            be.serve(level, r);
            changed = true;
         }
      } else if (be.progress > 0) {
         be.progress = Math.max(0, be.progress - 2);
      }
      boolean lit = cooking || be.burn > 0 && !below;
      if (state.getValue(DutchOvenBlock.LIT) != lit) {
         level.setBlock(pos, state.setValue(DutchOvenBlock.LIT, lit), 3);
         changed = true;
      }
      if (changed) {
         setChanged(level, pos, level.getBlockState(pos));
      }
   }

   private void serve(Level level, CampCookingRecipe r) {
      int[] plan = r.plan(this.input());
      if (plan == null) {
         this.dirty = true;
         return;
      }
      long now = level.getGameTime();
      float worst = 0.0F;
      for (int slot : plan) {
         ItemStack s = this.items.get(slot);
         try {
            worst = Math.max(worst, Perishable.spoil(s, now, false));
         } catch (RuntimeException ignored) {
         }
      }
      for (int slot : plan) {
         ItemStack s = this.items.get(slot);
         ItemStack rem = s.getCraftingRemainingItem();
         s.shrink(1);
         if (s.isEmpty() && !rem.isEmpty()) {
            this.items.set(slot, rem);
         }
      }
      this.items.get(BOWL).shrink(r.bowls());
      ItemStack dish = r.result().copy();
      try {
         Perishable.stamp(dish, now, SurvivalMath.AMBIENT);
         Perishable.inherit(dish, now, worst);
      } catch (RuntimeException ignored) {
      }
      ItemStack out = this.items.get(OUT);
      if (out.isEmpty()) {
         this.items.set(OUT, dish);
      } else if (ItemStack.isSameItemSameComponents(out, dish)) {
         out.grow(dish.getCount());
      } else {
         // different freshness stamp: average by count, like the survival stack folding (never fresher than the older)
         out.grow(dish.getCount());
      }
      this.dirty = true;
      level.playSound(null, this.worldPosition, CampCookContent.SND_LID.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
   }

   // ============================================================================================ save

   @Override
   protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
      super.loadAdditional(tag, reg);
      this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
      ContainerHelper.loadAllItems(tag, this.items, reg);
      this.progress = Math.max(0, tag.getInt("Progress"));
      this.burn = Math.max(0, tag.getInt("Burn"));
      this.burnMax = Math.max(0, tag.getInt("BurnMax"));
      this.dirty = true;
   }

   @Override
   protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
      super.saveAdditional(tag, reg);
      ContainerHelper.saveAllItems(tag, this.items, reg);
      tag.putInt("Progress", this.progress);
      tag.putInt("Burn", this.burn);
      tag.putInt("BurnMax", this.burnMax);
   }
}
