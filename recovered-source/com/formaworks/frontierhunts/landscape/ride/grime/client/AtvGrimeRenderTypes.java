package com.formaworks.frontierhunts.landscape.ride.grime.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4fStack;

/**
 * Overlay render types for the ATV grime layers. They redraw the vehicle's own geometry with a mask texture and use
 * vanilla's entity shaders (so Iris / shader packs treat them like any other entity layer):
 * <ul>
 *   <li>mud / snow: entity cutout (alpha-tested binary masks, no blending) like vanilla armour and sheep-wool layers.</li>
 * </ul>
 * [atv2] No translucent layer any more: shader packs draw entity layers opaque, so wetness is a vertex-colour tint.
 * Each layer is pulled a hair toward the camera with a view-space scale (the vanilla armour-layering trick), one step
 * per layer (mud &lt; snow), so coplanar layers never z-fight and the order holds even when a shader mod
 * reorders draws. The scale is about the camera, so nothing moves on screen.
 */
public final class AtvGrimeRenderTypes extends RenderType {
    private static final float STEP = 1f / 4096f;
    private static final LayeringStateShard[] LAYERS = {layer(1), layer(2)};
    private static final Map<String, RenderType> CACHE = new HashMap<>();

    private AtvGrimeRenderTypes() {
        super("frontierhunts_atv_grime", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    private static LayeringStateShard layer(int k) {
        final float s = 1f - k * STEP;
        return new LayeringStateShard("frontierhunts_grime_layer_" + k, () -> {
            Matrix4fStack mv = RenderSystem.getModelViewStack();
            mv.pushMatrix();
            mv.scale(s, s, s);
            RenderSystem.applyModelViewMatrix();
        }, () -> {
            Matrix4fStack mv = RenderSystem.getModelViewStack();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        });
    }

    /** Cutout mask layer. triangles: realistic mesh (TRIANGLES) vs blocky model (QUADS). layer: 1 mud, 2 snow. */
    public static RenderType cutout(ResourceLocation tex, boolean triangles, boolean cull, int layer) {
        String key = tex + (triangles ? "|t" : "|q") + (cull ? "c" : "n") + layer;
        RenderType rt = CACHE.get(key);
        if (rt == null) {
            CompositeState st = CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(cull ? CULL : NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setLayeringState(LAYERS[Math.max(0, Math.min(1, layer - 1))])
                    .createCompositeState(false);
            rt = create(FrontierHunts.ID + "_atv_grime_cutout", DefaultVertexFormat.NEW_ENTITY,
                    triangles ? VertexFormat.Mode.TRIANGLES : VertexFormat.Mode.QUADS, triangles ? 131072 : 4096, false, false, st);
            CACHE.put(key, rt);
        }
        return rt;
    }
}
