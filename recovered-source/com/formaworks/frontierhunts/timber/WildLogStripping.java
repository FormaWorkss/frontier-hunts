package com.formaworks.frontierhunts.timber;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * [trees2] An axe strips the mod's logs like vanilla logs: into the stripped log of the vanilla wood family the log
 * crafts into ([wood] its planks and 2:2 log conversion, tools/trees2/gen_wood_data.py), keeping its axis. The mod has
 * no stripped variants of its own; before, an axe did nothing on them. Common code (the event fires on both sides; the
 * client predicts the same result).
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class WildLogStripping {
   private static final Map<String, Block> STRIPPED = Map.of(
      "pine_log", Blocks.STRIPPED_SPRUCE_LOG,
      "cedar_log", Blocks.STRIPPED_DARK_OAK_LOG,
      "aspen_log", Blocks.STRIPPED_BIRCH_LOG,
      "birch_log", Blocks.STRIPPED_BIRCH_LOG,
      "alpine_spruce_log", Blocks.STRIPPED_SPRUCE_LOG,
      "alpine_pine_log", Blocks.STRIPPED_SPRUCE_LOG,
      "alpine_maple_log", Blocks.STRIPPED_OAK_LOG,
      "alpine_alder_log", Blocks.STRIPPED_OAK_LOG,
      "alpine_rowan_log", Blocks.STRIPPED_OAK_LOG,
      "alpine_cottonwood_log", Blocks.STRIPPED_BIRCH_LOG
   );

   private WildLogStripping() {
   }

   @SubscribeEvent
   public static void strip(BlockEvent.BlockToolModificationEvent event) {
      if (event.getItemAbility() != ItemAbilities.AXE_STRIP) return;
      BlockState state = event.getState();
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      if (!"frontierhunts".equals(id.getNamespace())) return;
      Block stripped = STRIPPED.get(id.getPath());
      if (stripped == null) return;
      BlockState result = stripped.defaultBlockState();
      if (state.hasProperty(BlockStateProperties.AXIS)) {
         result = result.setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
      }
      event.setFinalState(result);
   }
}
