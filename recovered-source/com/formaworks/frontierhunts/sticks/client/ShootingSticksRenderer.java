package com.formaworks.frontierhunts.sticks.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.sticks.ShootingSticks;
import com.formaworks.frontierhunts.sticks.ShootingSticksEntity;
import com.formaworks.frontierhunts.sticks.SticksContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * [sticks] Shooting sticks standing in the world: the tripod at its height (legs sliding when the height changes,
 * swinging out when they were just set up), the yoke turned with the gun lying in it (or where it was left). A shooter
 * resting on them in first person sees the set in the hand pass instead ({@link SticksView}), so it sits exactly under
 * the gun; it is not drawn twice.
 */
public final class ShootingSticksRenderer extends EntityRenderer<ShootingSticksEntity> {
   /** ticks the legs take to swing out and extend after the sticks are set up */
   static final float DEPLOY_TICKS = 11.0F;

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
         e.registerEntityRenderer(SticksContent.STICKS.get(), ShootingSticksRenderer::new);
      }

      @SubscribeEvent
      public static void reload(RegisterClientReloadListenersEvent e) {
         e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)rm -> SticksMesh.clear());
      }
   }

   public ShootingSticksRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.shadowRadius = 0.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(ShootingSticksEntity e) {
      return SticksMesh.TEX;
   }

   @Override
   public boolean shouldRender(ShootingSticksEntity e, Frustum frustum, double x, double y, double z) {
      return !SticksView.hides(e) && super.shouldRender(e, frustum, x, y, z);
   }

   @Override
   public void render(ShootingSticksEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
      PoseStack.Pose p = ps.last();
      SticksMesh.drawSet(p.pose(), p.normal(), buffers.getBuffer(RenderType.entityCutoutNoCull(SticksMesh.TEX)), light, e.getYRot(), splay(e, pt), legLength(e, pt),
         yokeYaw(e, pt), yokeTilt(e, pt), true);
      super.render(e, yaw, pt, ps, buffers, light);
   }

   /** How far set up (0 folded .. 1 standing) on clients that saw them placed. */
   static float deploy(ShootingSticksEntity e, float pt) {
      double age = e.level().getGameTime() + pt - e.deployedAt();
      return age < 0.0 || age >= DEPLOY_TICKS ? 1.0F : ShootingSticks.ease((float)(age / DEPLOY_TICKS));
   }

   static float splay(ShootingSticksEntity e, float pt) {
      return (float)ShootingSticks.SPLAY * deploy(e, pt);
   }

   static float legLength(ShootingSticksEntity e, float pt) {
      float target = Float.isNaN(e.legShown) ? (float)e.height().legLength() : Mth.lerp(pt, e.legShownO, e.legShown);
      float d = deploy(e, pt);
      return d >= 1.0F ? target : Mth.lerp(ShootingSticks.ease(Math.clamp(d * 1.3F - 0.3F, 0.0F, 1.0F)), SticksMesh.MIN_LEG, target);
   }

   static float yokeYaw(ShootingSticksEntity e, float pt) {
      Player r = e.rider();
      return r != null ? Mth.rotLerp(pt, r.yRotO, r.getYRot()) : e.yokeYaw();
   }

   static float yokeTilt(ShootingSticksEntity e, float pt) {
      Player r = e.rider();
      return r != null ? Mth.lerp(pt, r.xRotO, r.getXRot()) * 0.5F * e.settled(pt) : 0.0F;
   }
}
