package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.rifle.RifleBullet;
import com.formaworks.frontierhunts.workshop.FieldFloat;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public final class FirearmDamage {
   private static final String HISTORY = "frontier_firearm_hits";
   public static final int LETHAL_HITS = 3;
   public static final String BOSS_TAG = "frontier_boss";

   public static UUID shot(DamageSource var0) {
      Entity var1 = var0.getDirectEntity();
      if (var1 instanceof RifleBullet) {
         return var1.getUUID();
      } else if (var1 instanceof HuntProjectile var2) {
         Weapon var3 = var2.kind();
         if (!var3.bow && var3 != Weapon.TRANQUILIZER_RIFLE && var3 != Weapon.FLARE_GUN && var3 != Weapon.BAIT_LAUNCHER) {
            CompoundTag var4 = var2.getPersistentData();
            return var4.hasUUID("frontier_trigger") ? var4.getUUID("frontier_trigger") : var2.getUUID();
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static UUID hitId(DamageSource var0) {
      UUID var1 = shot(var0);
      if (var1 != null) {
         return var1;
      } else {
         Entity var2 = var0.getDirectEntity();
         if (var2 instanceof HuntProjectile var6) {
            Weapon var4 = var6.kind();
            return var4 != Weapon.TRANQUILIZER_RIFLE && var4 != Weapon.FLARE_GUN && var4 != Weapon.BAIT_LAUNCHER ? var6.getUUID() : null;
         } else if (var2 instanceof FishingHook || var2 instanceof FieldFloat) {
            return null;
         } else if (var2 instanceof Projectile var5) {
            return var5.getUUID();
         } else if ((!var0.is(DamageTypeTags.IS_PROJECTILE) || var0.getEntity() == null || var2 != var0.getEntity())
            && (var2 != null || !var0.is(DamageTypeTags.IS_PROJECTILE) || var0.getEntity() == null)) {
            return null;
         } else {
            Level var3 = var0.getEntity().level();
            return new UUID(var0.getEntity().getUUID().getMostSignificantBits(), var3.getGameTime());
         }
      }
   }

   private static boolean contains(ListTag var0, UUID var1) {
      for (Tag var3 : var0) {
         if (var3.getAsString().equals(var1.toString())) {
            return true;
         }
      }

      return false;
   }

   public static float animalDamage(LivingEntity var0, DamageSource var1, float var2) {
      UUID var3 = hitId(var1);
      if (var3 != null && !(var2 <= 0.0F) && !boss(var0)) {
         ListTag var4 = var0.getPersistentData().getList("frontier_firearm_hits", 8);
         return var4.size() >= 2 && !contains(var4, var3) ? Math.max(var2, var0.getHealth() + 1.0F) : var2;
      } else {
         return var2;
      }
   }

   public static boolean boss(LivingEntity var0) {
      return var0.getPersistentData().getBoolean("frontier_boss");
   }

   public static void accepted(LivingEntity var0, DamageSource var1) {
      UUID var2 = hitId(var1);
      if (var2 != null) {
         ListTag var3 = var0.getPersistentData().getList("frontier_firearm_hits", 8);
         if (var3.size() < 4 && !contains(var3, var2)) {
            var3.add(StringTag.valueOf(var2.toString()));
            var0.getPersistentData().put("frontier_firearm_hits", var3);
         }
      }
   }

   public static float mobDamage(Entity var0, float var1) {
      return var0 instanceof Mob ? var1 * 2.0F : var1;
   }

   private FirearmDamage() {
   }
}
