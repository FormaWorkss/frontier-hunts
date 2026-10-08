package com.formaworks.frontierhunts.camps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Origin of a Big-Buck Board. Only stores which rack category it shows; the standings arrive via the live sync. */
public final class TrophyBoardBlockEntity extends BlockEntity {
   public static final RecordBook.Category[] SHOWN = new RecordBook.Category[]{RecordBook.Category.WHITETAIL, RecordBook.Category.ELK, RecordBook.Category.MOOSE};
   private int shown;

   public TrophyBoardBlockEntity(BlockPos pos, BlockState state) {
      super(CampsContent.TROPHY_BOARD_BE.get(), pos, state);
   }

   public RecordBook.Category category() {
      return SHOWN[Math.floorMod(this.shown, SHOWN.length)];
   }

   public int shownIndex() {
      return Math.floorMod(this.shown, SHOWN.length);
   }

   public void cycle() {
      this.shown = (this.shownIndex() + 1) % SHOWN.length;
      this.setChanged();
      if (this.level != null && !this.level.isClientSide) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   @Override
   protected void saveAdditional(CompoundTag tag, Provider provider) {
      super.saveAdditional(tag, provider);
      tag.putInt("shown", this.shownIndex());
   }

   @Override
   protected void loadAdditional(CompoundTag tag, Provider provider) {
      super.loadAdditional(tag, provider);
      this.shown = Math.floorMod(tag.getInt("shown"), SHOWN.length);
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
