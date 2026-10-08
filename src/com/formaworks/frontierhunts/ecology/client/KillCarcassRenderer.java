package com.formaworks.frontierhunts.ecology.client;

import com.formaworks.frontierhunts.ecology.KillCarcass;
import com.formaworks.frontierhunts.wildlife2026.WildlifeContent;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.joml.Quaternionf;

/**
 * [ecology] Draws a kill carcass as the animal itself lying on its side: a still, client-only stand-in of that species
 * is handed to the species' own wildlife renderer (so it matches the Vanilla / Ultra look exactly), rolled
 * over about its long axis. A fresh kill tips over during its first half second instead of popping into place.
 */
public final class KillCarcassRenderer extends EntityRenderer<KillCarcass> {
   private final WeakHashMap<KillCarcass, WildlifeMob> standIns = new WeakHashMap<>();
   private boolean failed;

   public KillCarcassRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.shadowRadius = 0.6F;
      this.shadowStrength = 0.6F;
   }

   @Override
   public ResourceLocation getTextureLocation(KillCarcass e) {
      return TextureAtlas.LOCATION_BLOCKS;
   }

   private WildlifeMob standIn(KillCarcass c) {
      WildlifeMob m = this.standIns.get(c);
      WildlifeSpecies s = c.species();
      if (m == null || m.species != s) {
         var holder = WildlifeContent.TYPES.get(s);
         if (holder == null) {
            return null;
         }
         @SuppressWarnings("unchecked")
         EntityType<WildlifeMob> type = (EntityType<WildlifeMob>)holder.get();
         m = new WildlifeMob(type, c.level(), s);
         m.setNoAi(true);
         m.setSilent(true);
         m.setOnGround(true);
         this.standIns.put(c, m);
      }
      return m;
   }

   @Override
   public void render(KillCarcass c, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
      if (this.failed) {
         return;
      }
      WildlifeMob m = this.standIn(c);
      if (m == null) {
         return;
      }
      float body = c.bodyYaw();
      // keep the stand-in still and where the carcass is (renderers measure distance from its position)
      m.setPos(c.getX(), c.getY(), c.getZ());
      m.xo = c.getX();
      m.yo = c.getY();
      m.zo = c.getZ();
      m.yBodyRot = m.yBodyRotO = body;
      m.yHeadRot = m.yHeadRotO = body + (c.leftSide() ? 18.0F : -18.0F);
      m.setYRot(body);
      m.yRotO = body;
      m.setXRot(20.0F);
      m.xRotO = 20.0F;
      m.tickCount = 1;
      float age = (float)(c.level().getGameTime() - c.killedAt()) + pt;
      float fall = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
      fall = fall * fall * (3.0F - 2.0F * fall);
      float roll = (c.leftSide() ? 90.0F : -90.0F) * fall;
      ps.pushPose();
      try {
         // lying on its side the flank rests on the ground: lift by half the body's width, rolled about the long axis
         WildlifeSpecies s = c.species();
         ps.translate(0.0F, s.width * 0.42F * fall, 0.0F);
         float r = (180.0F - body) * Mth.DEG_TO_RAD;
         // the animal's long axis in world space after its yaw (model z), roll about it
         float ax = Mth.sin(r), az = Mth.cos(r);
         ps.mulPose(new Quaternionf().rotationAxis(roll * Mth.DEG_TO_RAD, ax, 0.0F, az));
         this.entityRenderDispatcher.getRenderer(m).render(m, body, 0.0F, ps, buffers, light);
      } catch (RuntimeException ex) {
         this.failed = true;
         com.mojang.logging.LogUtils.getLogger().error("Frontier ecology: kill carcass render failed, hiding carcasses", ex);
      } finally {
         ps.popPose();
      }
      super.render(c, yaw, pt, ps, buffers, light);
   }
}
