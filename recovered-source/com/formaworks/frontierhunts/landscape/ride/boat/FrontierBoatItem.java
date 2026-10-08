package com.formaworks.frontierhunts.landscape.ride.boat;

import com.formaworks.frontierhunts.landscape.ride.rig.AtvFuelConfig;
import com.formaworks.frontierhunts.landscape.ride.rig.RigContent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** [1.1.0] Places a rowboat or a jon boat on the water (or the shore) the player looks at, facing the way they face. */
public class FrontierBoatItem extends Item {
   private final boolean jon;

   public FrontierBoatItem(Item.Properties props, boolean jon) {
      super(props);
      this.jon = jon;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
      if (hit.getType() != HitResult.Type.BLOCK) {
         return InteractionResultHolder.pass(stack);
      }
      Vec3 view = player.getViewVector(1.0F);
      Vec3 eye = player.getEyePosition();
      List<Entity> near = level.getEntities(player, player.getBoundingBox().expandTowards(view.scale(5.0)).inflate(1.0), EntitySelector.NO_SPECTATORS.and(Entity::isPickable));
      for (Entity e : near) {
         AABB box = e.getBoundingBox().inflate(e.getPickRadius());
         if (box.contains(eye)) {
            return InteractionResultHolder.pass(stack);
         }
      }
      Boat boat = this.jon ? BoatContent.JON_BOAT.get().create(level) : BoatContent.ROWBOAT.get().create(level);
      if (boat == null) {
         return InteractionResultHolder.fail(stack);
      }
      Vec3 at = hit.getLocation();
      boat.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
      if (!level.noCollision(boat, boat.getBoundingBox())) {
         return InteractionResultHolder.fail(stack);
      }
      if (!level.isClientSide) {
         if (stack.has(DataComponents.CUSTOM_NAME)) {
            boat.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
         }
         if (boat instanceof JonBoat jb) {
            Float f = stack.get(RigContent.FUEL.get());
            jb.setFuel(f != null ? f : AtvFuelConfig.startFraction() * JonBoat.TANK);
         }
         level.addFreshEntity(boat);
         level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
         stack.consume(1, player);
      }
      player.awardStat(Stats.ITEM_USED.get(this));
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      String key = this.jon ? "jon_boat" : "rowboat";
      lines.add(Component.translatable("tooltip.frontierhunts." + key + ".1").withStyle(ChatFormatting.GRAY));
      lines.add(Component.translatable("tooltip.frontierhunts." + key + ".2").withStyle(ChatFormatting.GRAY));
      if (this.jon) {
         Float f = stack.get(RigContent.FUEL.get());
         float litres = f != null ? f : AtvFuelConfig.startFraction() * JonBoat.TANK;
         lines.add(Component.translatable("tooltip.frontierhunts.jon_boat.fuel", String.format("%.1f", litres), String.format("%.0f", JonBoat.TANK))
            .withStyle(ChatFormatting.GOLD));
      }
   }
}
