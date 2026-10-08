package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [hunts] The waterfowl kit the duck hunt needs: a Duck Call (wood barrel call, hail and feed chuckle) and a Mallard
 * Decoy that floats on the water (placed like a lily pad). Crafted in survival (data/frontierhunts/recipe), found in the
 * Field Equipment tab next to the other calls. The call's sound is real hen-mallard quacks (NPS, public domain; [calls] see AUDIO_CREDITS.txt).
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class HuntContent {
   public static final DeferredHolder<Block, Block> DECOY = DeferredHolder.create(Registries.BLOCK, id("mallard_decoy"));
   public static final DeferredHolder<Item, Item> DECOY_ITEM = DeferredHolder.create(Registries.ITEM, id("mallard_decoy"));
   public static final DeferredHolder<Item, Item> DUCK_CALL = DeferredHolder.create(Registries.ITEM, id("duck_call"));
   public static final SoundEvent DUCK_CALL_SOUND = SoundEvent.createVariableRangeEvent(FrontierHunts.id("duck_call"));

   private HuntContent() {
   }

   static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", path);
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.BLOCK, id("mallard_decoy"), () -> new DecoyBlock(BlockBehaviour.Properties.of()
         .mapColor(MapColor.COLOR_BROWN)
         .strength(0.3F)
         .sound(SoundType.WOOD)
         .noOcclusion()
         .noCollission()
         .pushReaction(PushReaction.DESTROY)));
      e.register(Registries.ITEM, id("mallard_decoy"), () -> new PlaceOnWaterBlockItem(DECOY.get(), new Item.Properties().stacksTo(16)));
      e.register(Registries.ITEM, id("duck_call"), () -> new DuckCallItem(new Item.Properties().stacksTo(1)));
      e.register(Registries.SOUND_EVENT, helper -> helper.register(DUCK_CALL_SOUND.getLocation(), DUCK_CALL_SOUND));
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey().location().equals(id("field_equipment"))) {
         com.formaworks.frontierhunts.items.TabPlacement.after(e, DUCK_CALL.get(), "predator_call", "rattling_antlers", "deer_call");
         com.formaworks.frontierhunts.items.TabPlacement.after(e, DECOY_ITEM.get(), "duck_call", "predator_call", "rattling_antlers");
      }
   }

   /** Decoy positions near x/z that really hold a decoy (stale registry entries in loaded chunks are dropped). */
   public static List<BlockPos> liveDecoys(ServerLevel level, double x, double y, double z, double r, int max) {
      String dim = level.dimension().location().toString();
      HuntStore store = HuntStore.get(level.getServer());
      List<BlockPos> out = new ArrayList<>();
      for (BlockPos p : store.decoysNear(dim, x, y, z, r, max * 2)) {
         if (level.isLoaded(p)) {
            if (!level.getBlockState(p).is(DECOY.get())) {
               store.forgetDecoy(dim, p.asLong());
               continue;
            }
         }
         out.add(p);
         if (out.size() >= max) {
            break;
         }
      }
      return out;
   }
}
