package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.benches.OldBenches;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * [benches] Common (both sides): the expedition lodge upgrades still name the retired Ammo Reloader, Bow Tuning Rack and
 * Fishing Station; whatever {@link ExpeditionService#give} hands out, a retired bench becomes its successor bench.
 */
@Mixin(ExpeditionService.class)
public abstract class BenchGiveMixin {
   @ModifyVariable(method = "give(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), argsOnly = true)
   private static ItemStack frontierhunts$modernBench(ItemStack stack) {
      return OldBenches.modernise(stack);
   }
}
