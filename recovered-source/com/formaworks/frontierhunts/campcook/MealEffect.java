package com.formaworks.frontierhunts.campcook;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** [licence] A camp-meal buff (beneficial). Hearty heals half a heart every 12 s; the others act through hooks/attributes. */
public class MealEffect extends MobEffect {
   private final MealBuffs.Buff buff;

   public MealEffect(MealBuffs.Buff buff) {
      super(MobEffectCategory.BENEFICIAL, buff.color & 0xFFFFFF);
      this.buff = buff;
   }

   @Override
   public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
      return this.buff == MealBuffs.Buff.HEARTY && duration % 240 == 0;
   }

   @Override
   public boolean applyEffectTick(LivingEntity e, int amplifier) {
      if (this.buff == MealBuffs.Buff.HEARTY && e.getHealth() < e.getMaxHealth()) {
         e.heal(1.0F);
      }
      return true;
   }
}
