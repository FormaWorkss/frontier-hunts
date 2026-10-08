package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.RealisticWorld;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockStateBase.class})
public abstract class BlockStateOcclusionMixin {
   @Inject(
      method = {"canOcclude"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void frontierhunts$roundTrunks(CallbackInfoReturnable<Boolean> var1) {
      if (RealisticWorld.round && RealisticWorld.isRound(((BlockStateBase)this).getBlock())) {
         var1.setReturnValue(false);
      }
   }
}
