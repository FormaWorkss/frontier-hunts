package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * [1.1.2] Readable weapon icons. Guns and bows are dark (blued steel, black synthetic, oiled walnut) and sank into the
 * dark hotbar. In inventory slots only, they are now drawn with their own icon shader
 * ({@code frontierhunts:rendertype_weapon_icon}): Minecraft's two item lights but brighter, the dark tones lifted to
 * readable gunmetal and wood, and a soft sheen along every surface that turns away from you - the weapon's shape reads
 * from its own lighting, with nothing drawn around it (the 1.1.0 plate and the 1.1.1 outline are gone). The model,
 * its attachments and its pose are unchanged, and nothing changes in the hand, on the ground or in item frames.
 */
public final class WeaponIcons {
   private WeaponIcons() {
   }

   /** the fragment shader's tone lift and sheen (mirrored by tools/gunsmith/IconBench) */
   public static final float GAMMA = 0.68F, SHEEN = 0.16F;
   static ShaderInstance shader;
   private static final Map<RenderType, RenderType> LIFTED = new HashMap<>();

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Registration {
      private Registration() {
      }

      @SubscribeEvent
      public static void shaders(RegisterShadersEvent event) {
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FrontierHunts.id("rendertype_weapon_icon"), DefaultVertexFormat.NEW_ENTITY),
               s -> WeaponIcons.shader = s);
         } catch (IOException | RuntimeException e) {
            // [1.1.3] a driver that will not compile it just keeps the normal look
            WeaponIcons.shader = null;
            org.slf4j.LoggerFactory.getLogger("frontierhunts").warn("Weapon icon shader unavailable, using the normal look", e);
         }
      }
   }

   /** the buffers a weapon draws into: in a slot, every entity layer goes through the icon shader */
   public static MultiBufferSource lift(ItemDisplayContext ctx, MultiBufferSource buffers) {
      if (ctx != ItemDisplayContext.GUI || shader == null) {
         return buffers;
      }
      return type -> buffers.getBuffer(lifted(type));
   }

   static RenderType lifted(RenderType type) {
      if (type.format() != DefaultVertexFormat.NEW_ENTITY) {
         return type;
      }
      return LIFTED.computeIfAbsent(type, Lifted::new);
   }

   /** the same layer (texture, blending, depth, culling), drawn with the icon shader */
   static final class Lifted extends RenderType {
      Lifted(RenderType base) {
         super("frontierhunts_weapon_icon/" + base, base.format(), base.mode(), base.bufferSize(), base.affectsCrumbling(),
            base.toString().contains("translucent"), () -> {
               base.setupRenderState();
               if (WeaponIcons.shader != null) {
                  RenderSystem.setShader(() -> WeaponIcons.shader);
               }
            }, base::clearRenderState);
      }
   }
}
