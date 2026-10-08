package com.formaworks.frontierhunts.seating;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.items.TabPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [onboard2] Registers the seats (blocks, items, the seat entity), lists them in the creative tabs and holds the
 * sit / adjust logic shared by every seat block. Server side decides everything; the client only renders.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class Seats {
   public static Block LOG_STUMP, CAMP_CHAIR, TRAIL_BENCH, BLIND_CHAIR, TOWER_CHAIR;
   public static EntityType<SeatEntity> SEAT;
   private static final ResourceKey<CreativeModeTab> WORLD_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, FrontierHunts.id("world"));

   private Seats() {
   }

   private static BlockBehaviour.Properties props(MapColor color, float strength, SoundType sound) {
      return BlockBehaviour.Properties.of().mapColor(color).strength(strength).sound(sound).noOcclusion();
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.BLOCK, helper -> {
         LOG_STUMP = new SeatBlock(SeatKind.LOG_STUMP, props(MapColor.PODZOL, 1.6F, SoundType.WOOD));
         CAMP_CHAIR = new SeatBlock(SeatKind.CAMP_CHAIR, props(MapColor.COLOR_GREEN, 0.8F, SoundType.WOOL));
         TRAIL_BENCH = new BenchBlock(props(MapColor.WOOD, 1.5F, SoundType.WOOD));
         BLIND_CHAIR = new SwivelChairBlock(SeatKind.BLIND_CHAIR, props(MapColor.COLOR_GREEN, 1.0F, SoundType.WOOL));
         TOWER_CHAIR = new TowerChairBlock(props(MapColor.COLOR_GREEN, 1.0F, SoundType.WOOL));
         helper.register(FrontierHunts.id("log_stump_seat"), LOG_STUMP);
         helper.register(FrontierHunts.id("camp_chair"), CAMP_CHAIR);
         helper.register(FrontierHunts.id("trail_bench"), TRAIL_BENCH);
         helper.register(FrontierHunts.id("blind_chair"), BLIND_CHAIR);
         helper.register(FrontierHunts.id("tower_chair"), TOWER_CHAIR);
      });
      e.register(Registries.ITEM, helper -> {
         helper.register(FrontierHunts.id("log_stump_seat"), new SeatItem((SeatBlock)LOG_STUMP, new Item.Properties()));
         helper.register(FrontierHunts.id("camp_chair"), new SeatItem((SeatBlock)CAMP_CHAIR, new Item.Properties()));
         helper.register(FrontierHunts.id("trail_bench"), new SeatItem((SeatBlock)TRAIL_BENCH, new Item.Properties()));
         helper.register(FrontierHunts.id("blind_chair"), new SeatItem((SeatBlock)BLIND_CHAIR, new Item.Properties()));
         helper.register(FrontierHunts.id("tower_chair"), new SeatItem((SeatBlock)TOWER_CHAIR, new Item.Properties()));
      });
      e.register(Registries.ENTITY_TYPE, helper -> {
         SEAT = EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
            .sized(0.4F, 0.4F)
            .clientTrackingRange(10)
            .updateInterval(20)
            .noSummon()
            .fireImmune()
            .build("frontierhunts:seat");
         helper.register(FrontierHunts.id("seat"), SEAT);
      });
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (LOG_STUMP == null) {
         return;
      }
      if (e.getTabKey().equals(WORLD_TAB)) {
         // with the cabin furniture: lodge chair, then the outdoor seats, then the blind chairs
         TabPlacement.after(e, LOG_STUMP, "lodge_chair", "lodge_table");
         TabPlacement.after(e, CAMP_CHAIR, "log_stump_seat");
         TabPlacement.after(e, TRAIL_BENCH, "camp_chair");
         TabPlacement.after(e, BLIND_CHAIR, "trail_bench");
         TabPlacement.after(e, TOWER_CHAIR, "blind_chair");
      } else if (e.getTabKey() == HuntContent.GEAR_TAB) {
         // and next to the blinds in Field Equipment, where hunters look for blind gear
         TabPlacement.after(e, BLIND_CHAIR, "tower_blind", "field_blind");
         TabPlacement.after(e, TOWER_CHAIR, "blind_chair");
      }
   }

   // ============================================================================================ sitting

   static InteractionResult use(SeatBlock block, BlockState state, Level level, BlockPos pos, Player player) {
      if (level.isClientSide) {
         return InteractionResult.SUCCESS;
      }
      if (!(player instanceof ServerPlayer sp) || sp.isSpectator() || !sp.isAlive()) {
         return InteractionResult.PASS;
      }
      if (sp.isSecondaryUseActive()) {
         // sneak + use (empty hand): the tower chair's gas lift
         if (block instanceof TowerChairBlock && !state.getValue(SwivelChairBlock.OCCUPIED)) {
            boolean up = !state.getValue(TowerChairBlock.RAISED);
            level.setBlock(pos, state.setValue(TowerChairBlock.RAISED, up), Block.UPDATE_ALL);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35F, up ? 1.7F : 1.4F);
            sp.displayClientMessage(Component.translatable(up ? "seat.frontierhunts.raised" : "seat.frontierhunts.lowered"), true);
            return InteractionResult.CONSUME;
         }
         return InteractionResult.PASS;
      }
      if (sp.isPassenger() || sp.isSleeping() || sp.isFallFlying()) {
         return InteractionResult.PASS;
      }
      if (sp.distanceToSqr(Vec3.atCenterOf(pos)) > 16.0) {
         return InteractionResult.PASS;
      }
      SeatEntity old = SeatEntity.at(level, pos);
      if (old != null && old.isVehicle()) {
         sp.displayClientMessage(Component.translatable("seat.frontierhunts.taken"), true);
         return InteractionResult.CONSUME;
      }
      if (old != null) {
         old.discard();
      }
      Direction facing = state.getValue(SeatBlock.FACING);
      Vec3 at = block.seatPoint(state, pos);
      SeatEntity seat = new SeatEntity(level, pos, block.kind, facing, at);
      if (!level.addFreshEntity(seat)) {
         return InteractionResult.CONSUME;
      }
      if (!sp.startRiding(seat)) {
         seat.discard();
         return InteractionResult.CONSUME;
      }
      if (!block.kind.swivel) {
         float yaw = facing.toYRot();
         sp.setYRot(yaw);
         sp.setYHeadRot(yaw);
         sp.setYBodyRot(yaw);
      }
      sp.fallDistance = 0.0F;
      SoundType st = state.getSoundType(level, pos, sp);
      level.playSound(null, pos, st.getPlaceSound(), SoundSource.BLOCKS, st.getVolume() * 0.45F, st.getPitch() * 0.8F);
      return InteractionResult.CONSUME;
   }
}
