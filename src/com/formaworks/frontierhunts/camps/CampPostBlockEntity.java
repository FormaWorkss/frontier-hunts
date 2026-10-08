package com.formaworks.frontierhunts.camps;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lower half of a Camp Post: which camp it belongs to, plus a render copy of the camp's name, colour and rank. */
public final class CampPostBlockEntity extends BlockEntity {
   public UUID camp;
   public String name = "";
   public int color = 12;
   public int tier;
   public int members;

   public CampPostBlockEntity(BlockPos pos, BlockState state) {
      super(CampsContent.CAMP_POST_BE.get(), pos, state);
   }

   /** Server: refresh the render copy; syncs to clients only when something changed. */
   public void show(UUID camp, String name, int color, int tier, int members) {
      String n = name == null ? "" : name;
      if (java.util.Objects.equals(this.camp, camp) && this.name.equals(n) && this.color == color && this.tier == tier && this.members == members) {
         return;
      }
      this.camp = camp;
      this.name = n;
      this.color = color;
      this.tier = tier;
      this.members = members;
      this.setChanged();
      if (this.level != null && !this.level.isClientSide) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   @Override
   public void onLoad() {
      super.onLoad();
      if (this.level instanceof ServerLevel sl) {
         // defer: the chunk is still being attached
         sl.getServer().execute(() -> {
            if (!this.isRemoved()) {
               CampService.validatePost(this);
            }
         });
      }
   }

   @Override
   protected void saveAdditional(CompoundTag tag, Provider provider) {
      super.saveAdditional(tag, provider);
      if (this.camp != null) {
         tag.putUUID("camp", this.camp);
      }
      tag.putString("name", this.name);
      tag.putInt("color", this.color);
      tag.putInt("tier", this.tier);
      tag.putInt("members", this.members);
   }

   @Override
   protected void loadAdditional(CompoundTag tag, Provider provider) {
      super.loadAdditional(tag, provider);
      this.camp = tag.hasUUID("camp") ? tag.getUUID("camp") : null;
      this.name = HarvestRecord.clip(tag.getString("name"), 24);
      this.color = Math.floorMod(tag.getInt("color"), 16);
      this.tier = Math.clamp(tag.getInt("tier"), 0, 4);
      this.members = Math.clamp(tag.getInt("members"), 0, 64);
   }

   @Override
   public CompoundTag getUpdateTag(Provider provider) {
      CompoundTag tag = new CompoundTag();
      this.saveAdditional(tag, provider);
      return tag;
   }

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
