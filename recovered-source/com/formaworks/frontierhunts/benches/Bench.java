package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [benches] The three Frontier Hunts workbenches. Every mod item is made at exactly one of them (see {@link BenchCatalog}):
 * <ul>
 *   <li>{@link #FRONTIER} Frontier Workbench: everything that is not a weapon or ammunition (gear, clothing, camp,
 *       fishing, vehicles, building, range, food);</li>
 *   <li>{@link #GUNSMITH} Gunsmith's Bench: firearms, bows, crossbows, blades and attachments, plus fitting parts to the
 *       weapon in your hand;</li>
 *   <li>{@link #RELOADING} Reloading Bench: every kind of ammunition (cartridges, shells, arrows, arrow tips, darts,
 *       flares, bait) plus refitting arrow heads.</li>
 * </ul>
 * Common code (no client types).
 */
public enum Bench {
   FRONTIER("frontier_workbench", 0xFFD9A54E, 0xFF2B2215, "frontierhunts:skinning_tool"),
   GUNSMITH("gunsmith_bench", 0xFF6DB383, 0xFF15241C, "frontierhunts:ridgeline_rifle"),
   RELOADING("reloading_bench", 0xFFE0674C, 0xFF2A1714, "frontierhunts:rifle_round");

   private static final Bench[] VALUES = values();

   /** Block / item / menu id (path in the frontierhunts namespace). */
   public final String id;
   /** Accent colour of the bench (ARGB): header band, selection, primary button. */
   public final int accent;
   /** The dark tone of the header band (ARGB). */
   public final int deep;
   /** Item drawn when the emblem texture is missing (never in a normal jar). */
   public final String icon;

   Bench(String id, int accent, int deep, String icon) {
      this.id = id;
      this.accent = accent;
      this.deep = deep;
      this.icon = icon;
   }

   public ResourceLocation rl() {
      return FrontierHunts.id(this.id);
   }

   public String nameKey() {
      return "block.frontierhunts." + this.id;
   }

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public Block block() {
      return BenchContent.BLOCKS.get(this).get();
   }

   public Item item() {
      return BenchContent.ITEMS.get(this).get();
   }

   public ItemStack stack() {
      return new ItemStack(this.item());
   }

   public MenuType<BenchMenu> menu() {
      return BenchContent.MENUS.get(this).get();
   }

   /** Ammunition benches offer "Craft ×5" on every recipe. */
   public boolean batches() {
      return this == RELOADING;
   }

   public static Bench byOrdinal(int i) {
      return i >= 0 && i < VALUES.length ? VALUES[i] : null;
   }

   /** The bench a placed block is: one of the three, or the successor of a retired bench; null for anything else. */
   public static Bench of(BlockState state) {
      if (state == null) {
         return null;
      }
      if (state.getBlock() instanceof BenchBlock b) {
         return b.bench;
      }
      return OldBenches.successor(state);
   }

   /** The bench an item id (frontierhunts:...) is: null when it is no bench item. */
   public static Bench ofItem(Item item) {
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      for (Bench b : VALUES) {
         if (b.rl().equals(id)) {
            return b;
         }
      }
      return null;
   }
}
