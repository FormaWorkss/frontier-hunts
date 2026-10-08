package com.formaworks.frontierhunts.landscape.ride.boat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * [1.1.0] The bow and stern sections of a long hull, so the whole boat can be clicked (to board, refuel, open the dry
 * box) and hit, not just the vanilla-sized box in its middle. Everything is passed on to the boat.
 */
public class BoatPart extends PartEntity<Boat> {
   private final EntityDimensions size;
   final double along;

   BoatPart(Boat parent, double along, float width, float height) {
      super(parent);
      this.size = EntityDimensions.scalable(width, height);
      this.along = along;
      this.refreshDimensions();
   }

   /** keep the sections at the bow and stern as the boat moves and turns */
   static void place(Boat b, BoatPart[] parts) {
      float yaw = b.getYRot() * (float)(Math.PI / 180.0);
      for (BoatPart p : parts) {
         Vec3 at = b.position().add(-Mth.sin(yaw) * p.along, 0.0, Mth.cos(yaw) * p.along);
         p.xo = p.getX();
         p.yo = p.getY();
         p.zo = p.getZ();
         p.xOld = p.xo;
         p.yOld = p.yo;
         p.zOld = p.zo;
         p.setPos(at);
      }
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean isPickable() {
      return true;
   }

   @Override
   public ItemStack getPickResult() {
      return this.getParent().getPickResult();
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      return !this.isInvulnerableTo(source) && this.getParent().hurt(source, amount);
   }

   @Override
   public InteractionResult interact(Player player, InteractionHand hand) {
      return this.getParent().interact(player, hand);
   }

   @Override
   public InteractionResult interactAt(Player player, Vec3 at, InteractionHand hand) {
      return this.getParent().interactAt(player, at, hand);
   }

   @Override
   public boolean is(Entity e) {
      return this == e || this.getParent() == e;
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      return this.size;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }
}
