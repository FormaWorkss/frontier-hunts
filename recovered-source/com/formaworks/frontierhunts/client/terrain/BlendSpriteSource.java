package com.formaworks.frontierhunts.client.terrain;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/**
 * Adds untouched vanilla copies of a few block textures to the block atlas
 * ({@code frontierhunts:block/original/...}) so that player-built or special surfaces (mossy
 * cobblestone, built logs/leaves - see the tree package) can keep the vanilla look while the
 * realistic world pack re-textures natural ones. Source images are never altered.
 *
 * <p>This is not terrain smoothing. The sprite source type keeps its historical id
 * {@code frontierhunts:terrain_blends} because the realistic world pack's
 * {@code atlases/blocks.json} references it; an unknown sprite source type would break resource
 * loading. The old terrain blend-mask sprites are gone.
 */
@EventBusSubscriber(modid="frontierhunts", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class BlendSpriteSource implements SpriteSource {
    static final Set<ResourceLocation> ORIGINALS=originals();
    public static final MapCodec<BlendSpriteSource> CODEC=MapCodec.unit(BlendSpriteSource::new);
    public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
    @SubscribeEvent public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("frontierhunts","terrain_blends"),TYPE);
    }
    public SpriteSourceType type() { return TYPE; }
    public static ResourceLocation originalId(ResourceLocation texture) {
        return ResourceLocation.fromNamespaceAndPath("frontierhunts","block/original/"+texture.getNamespace()+"/"+texture.getPath());
    }
    public static TextureAtlasSprite original(TextureAtlasSprite texture) {
        if(!ORIGINALS.contains(texture.contents().name()))return texture;
        var wanted=originalId(texture.contents().name());
        var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(wanted);
        return sprite.contents().name().equals(wanted)?sprite:texture;
    }
    static Set<ResourceLocation> originals() {
        Set<ResourceLocation> names=new HashSet<>();
        // Same vanilla set as dev.61, so every caller of original()/originalQuads() behaves as before.
        for(String name:new String[]{"grass_block_top","dirt","coarse_dirt","podzol_top","rooted_dirt","gravel","sand","stone","moss_block",
                "andesite","granite","diorite","mud","snow","mossy_cobblestone","cobblestone"})
            names.add(ResourceLocation.fromNamespaceAndPath("minecraft","block/"+name));
        for(String wood:new String[]{"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry"})
            for(String suffix:new String[]{"_log","_log_top","_leaves"})names.add(ResourceLocation.parse("minecraft:block/"+wood+suffix));
        return Set.copyOf(names);
    }
    public static List<BakedQuad> originalQuads(List<BakedQuad> quads) {
        List<BakedQuad> result=new ArrayList<>(quads.size());
        for(var quad:quads) {
            var old=quad.getSprite();var sprite=original(old);
            if(sprite==old){result.add(quad);continue;}
            int[] vertices=quad.getVertices().clone();int stride=vertices.length/4;
            for(int i=0;i<4;i++) {
                float u=(Float.intBitsToFloat(vertices[i*stride+4])-old.getU0())/(old.getU1()-old.getU0());
                float v=(Float.intBitsToFloat(vertices[i*stride+5])-old.getV0())/(old.getV1()-old.getV0());
                vertices[i*stride+4]=Float.floatToRawIntBits(sprite.getU(u));vertices[i*stride+5]=Float.floatToRawIntBits(sprite.getV(v));
            }
            result.add(new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),sprite,true,true));
        }
        return result;
    }
    public void run(ResourceManager manager,SpriteSource.Output output) {
        for(var texture:ORIGINALS) {
            var vanilla=manager.getResourceStack(TEXTURE_ID_CONVERTER.idToFile(texture)).stream()
                .filter(resource->resource.sourcePackId().equals("vanilla")).findFirst();
            if(vanilla.isEmpty())continue;
            var id=originalId(texture);
            output.add(id,loader->{
                try(var stream=vanilla.get().open()) {
                    var image=NativeImage.read(stream);
                    return new SpriteContents(id,new FrameSize(image.getWidth(),image.getHeight()),image,ResourceMetadata.EMPTY);
                }catch(java.io.IOException error){throw new IllegalStateException("Cannot load original building material "+texture,error);}
            });
        }
    }
}
