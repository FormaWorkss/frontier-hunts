package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.workshop.WideStationBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * [benches] The seven retired workbenches (Weapons Workbench, Optics & Attachment Workbench, Ammo Reloader, Bow Tuning
 * Rack, Fishing Station, Field Clothing Workbench, Tent Bench). Their blocks and items stay registered so old worlds and
 * inventories load, but:
 * <ul>
 *   <li>using a placed one rebuilds it in place as its successor ({@link #SUCCESSOR}), same facing, both halves, and opens
 *       the new bench; a single-block one only becomes the two-wide bench when the block to its left is free, otherwise
 *       its successor's screen opens and the old block stays;</li>
 *   <li>placing an old item builds the successor instead (a single-block one needs the room for two);</li>
 *   <li>they are gone from the creative tabs and have no recipe; their tooltip says they are retired
 *       ({@code benches/client/BenchClient});</li>
 *   <li>items handed out by old code paths (camp upgrades) are swapped for the successor ({@link #modernise}).</li>
 * </ul>
 * The Bow Tuning Rack's bows on display come back to the player when it is rebuilt.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class OldBenches {
   /** retired block / item id (frontierhunts:...) -> the bench that replaces it */
   public static final Map<String, Bench> SUCCESSOR = new LinkedHashMap<>();

   static {
      SUCCESSOR.put("weapons_workbench", Bench.GUNSMITH);
      SUCCESSOR.put("attachment_workbench", Bench.GUNSMITH);
      SUCCESSOR.put("bow_tuning_rack", Bench.GUNSMITH);
      SUCCESSOR.put("ammo_reloader", Bench.RELOADING);
      SUCCESSOR.put("fishing_station", Bench.FRONTIER);
      SUCCESSOR.put("clothing_workbench", Bench.FRONTIER);
      SUCCESSOR.put("tent_bench", Bench.FRONTIER);
   }

   private OldBenches() {
   }

   private static String oldId(ResourceLocation rl) {
      return rl != null && rl.getNamespace().equals(FrontierHunts.ID) && SUCCESSOR.containsKey(rl.getPath()) ? rl.getPath() : null;
   }

   /** The retired id of a block state, or null. */
   public static String oldId(BlockState s) {
      return s == null ? null : oldId(BuiltInRegistries.BLOCK.getKey(s.getBlock()));
   }

   /** The retired id of an item, or null. */
   public static String oldId(Item item) {
      return item == null ? null : oldId(BuiltInRegistries.ITEM.getKey(item));
   }

   public static boolean retired(Item item) {
      return oldId(item) != null;
   }

   /** The bench that replaces a retired block (null for any other block). */
   public static Bench successor(BlockState s) {
      String id = oldId(s);
      return id == null ? null : SUCCESSOR.get(id);
   }

   public static Bench successor(Item item) {
      String id = oldId(item);
      return id == null ? null : SUCCESSOR.get(id);
   }

   /** A retired bench stack becomes the same number of its successor; anything else is returned as it is. */
   public static ItemStack modernise(ItemStack stack) {
      Bench b = stack == null || stack.isEmpty() ? null : successor(stack.getItem());
      return b == null ? stack : new ItemStack(b.item(), stack.getCount());
   }

   static void hideFromCreative(BuildCreativeModeTabContentsEvent e) {
      for (String id : SUCCESSOR.keySet()) {
         Item it = BuiltInRegistries.ITEM.get(FrontierHunts.id(id));
         if (it == Items.AIR) {
            continue;
         }
         ItemStack s = new ItemStack(it);
         if (e.getParentEntries().contains(s) || e.getSearchEntries().contains(s)) {
            e.remove(s, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
         }
      }
   }

   // ============================================================================================ using a placed one

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void use(PlayerInteractEvent.RightClickBlock e) {
      Level level = e.getLevel();
      BlockState st = level.getBlockState(e.getPos());
      Bench b = successor(st);
      if (b == null) {
         return;
      }
      Player p = e.getEntity();
      // sneaking with something in hand uses the item (places a block against the bench...), as vanilla does
      if (p.isSecondaryUseActive() && !(p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty())) {
         return;
      }
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
      if (!level.isClientSide && p instanceof ServerPlayer sp && !sp.isSpectator()) {
         BlockPos at = rebuild(sp, level, e.getPos(), st, b);
         sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new BenchMenu(id, inv, level, at, b), Component.translatable(b.nameKey())));
         BenchProgress.opened(sp, b);
      }
   }

   /**
    * Rebuilds the retired bench at {@code pos} as {@code b} and returns where the new bench's menu belongs (its left
    * half; the old block when it could not be rebuilt).
    */
   public static BlockPos rebuild(ServerPlayer sp, Level level, BlockPos pos, BlockState st, Bench b) {
      Direction facing = st.getValue(WideStationBlock.FACING);
      WideStationBlock.Part part = st.getValue(WideStationBlock.PART);
      BlockPos origin = pos;
      BlockPos right = null;
      if (part != WideStationBlock.Part.SINGLE) {
         BlockPos o = WideStationBlock.origin(pos, st);
         BlockPos r = o.relative(facing.getClockWise());
         BlockState os = level.getBlockState(o), rs = level.getBlockState(r);
         if (os.is(st.getBlock()) && rs.is(st.getBlock())) {
            origin = o;
            right = r;
         } else {
            // a broken pair: rebuild from the half that is left, if there is room
            origin = pos;
         }
      }
      if (right == null) {
         BlockPos r = origin.relative(facing.getClockWise());
         if (level.hasChunkAt(r) && level.getWorldBorder().isWithinBounds(r) && level.getBlockState(r).canBeReplaced()) {
            right = r;
         }
      }
      String was = oldId(st);
      if (right == null) {
         sp.displayClientMessage(Component.translatable("bench.frontierhunts.retired.cramped", Component.translatable(b.nameKey()))
            .withStyle(ChatFormatting.GOLD), true);
         return origin;
      }
      // bows on a Bow Tuning Rack come back to the hunter
      if (level.getBlockEntity(origin) instanceof Container c) {
         for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.removeItemNoUpdate(i);
            if (!s.isEmpty() && !sp.getInventory().add(s)) {
               sp.drop(s, false);
            }
         }
         c.setChanged();
      }
      Block nb = b.block();
      BlockState left = nb.defaultBlockState().setValue(WideStationBlock.FACING, facing).setValue(WideStationBlock.PART, WideStationBlock.Part.LEFT);
      // the old pair takes its other half with it when one half changes: set the left, then the right
      level.setBlock(origin, left, Block.UPDATE_ALL);
      level.setBlock(right, left.setValue(WideStationBlock.PART, WideStationBlock.Part.RIGHT), Block.UPDATE_ALL);
      sp.displayClientMessage(Component.translatable("bench.frontierhunts.retired.rebuilt",
         Component.translatable("block.frontierhunts." + was), Component.translatable(b.nameKey())).withStyle(ChatFormatting.GOLD), true);
      return origin;
   }

   // ============================================================================================ placing an old item

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void placed(BlockEvent.EntityPlaceEvent e) {
      if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof Level level)) {
         return;
      }
      BlockPos pos = e.getPos();
      BlockState st = level.getBlockState(pos);
      Bench b = successor(st);
      if (b == null) {
         return;
      }
      Direction facing = st.getValue(WideStationBlock.FACING);
      BlockPos origin = WideStationBlock.origin(pos, st);
      BlockPos right = origin.relative(facing.getClockWise());
      boolean pairPlaced = level.getBlockState(right).is(st.getBlock()) && st.getValue(WideStationBlock.PART) != WideStationBlock.Part.SINGLE;
      if (!pairPlaced && !(level.hasChunkAt(right) && level.getWorldBorder().isWithinBounds(right) && level.getBlockState(right).canBeReplaced())) {
         e.setCanceled(true); // the snapshot is restored and the item is not used up
         sp.displayClientMessage(Component.translatable("bench.frontierhunts.retired.no_room", Component.translatable(b.nameKey()))
            .withStyle(ChatFormatting.GOLD), true);
         return;
      }
      BlockState left = b.block().defaultBlockState().setValue(WideStationBlock.FACING, facing)
         .setValue(WideStationBlock.PART, WideStationBlock.Part.LEFT);
      level.setBlock(origin, left, Block.UPDATE_ALL);
      level.setBlock(right, left.setValue(WideStationBlock.PART, WideStationBlock.Part.RIGHT), Block.UPDATE_ALL);
   }

   /** The block a retired id names (air if none); used by the QA harness. */
   public static Block block(String id) {
      Block bl = BuiltInRegistries.BLOCK.get(FrontierHunts.id(id));
      return bl == null ? Blocks.AIR : bl;
   }
}
