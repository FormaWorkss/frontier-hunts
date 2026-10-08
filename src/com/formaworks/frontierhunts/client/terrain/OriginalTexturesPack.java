package com.formaworks.frontierhunts.client.terrain;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * [1.1.5] Makes the untouched vanilla copies that {@link BlendSpriteSource} adds to the block atlas
 * ({@code frontierhunts:block/original/<namespace>/<path>}) readable as ordinary resources too.
 *
 * <p>The atlas sprites are built in memory, so a mod that looks a block's texture up by its file (Xaero's World Map,
 * for one) found nothing at {@code frontierhunts:textures/block/original/...png} and fell back to a flat colour. This
 * hidden, always-on pack answers those reads by handing out the matching vanilla texture from the game's own vanilla
 * resources. It ships no images itself and serves nothing else.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class OriginalTexturesPack implements PackResources {
   static final String ID = "frontierhunts_original_textures";
   static final String PREFIX = "textures/block/original/";

   private final PackLocationInfo location;

   private OriginalTexturesPack(PackLocationInfo location) {
      this.location = location;
   }

   @SubscribeEvent
   public static void register(AddPackFindersEvent event) {
      if (event.getPackType() != PackType.CLIENT_RESOURCES) {
         return;
      }
      event.addRepositorySource(out -> {
         PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Frontier original textures"), PackSource.BUILT_IN, Optional.empty());
         Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) {
               return new OriginalTexturesPack(loc);
            }

            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata metadata) {
               return new OriginalTexturesPack(loc);
            }
         };
         // required and fixed at the bottom: always on, below every other pack, never shown in the pack list
         Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
         if (pack != null) {
            out.accept(pack.hidden());
         }
      });
   }

   /** frontierhunts:textures/block/original/minecraft/block/x.png -> minecraft:textures/block/x.png */
   static ResourceLocation source(ResourceLocation id) {
      if (!"frontierhunts".equals(id.getNamespace()) || !id.getPath().startsWith(PREFIX)) {
         return null;
      }
      String rest = id.getPath().substring(PREFIX.length());
      int slash = rest.indexOf('/');
      if (slash <= 0 || slash == rest.length() - 1) {
         return null;
      }
      return ResourceLocation.tryBuild(rest.substring(0, slash), "textures/" + rest.substring(slash + 1));
   }

   @Override
   public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
      if (type != PackType.CLIENT_RESOURCES) {
         return null;
      }
      ResourceLocation from = source(id);
      if (from == null) {
         return null;
      }
      try {
         return Minecraft.getInstance().getVanillaPackResources().getResource(type, from);
      } catch (RuntimeException e) {
         return null;
      }
   }

   @Override
   public IoSupplier<InputStream> getRootResource(String... path) {
      return null;
   }

   @Override
   public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
      // nothing to list: the sprites already come from BlendSpriteSource; this pack only answers direct reads
   }

   @Override
   public Set<String> getNamespaces(PackType type) {
      return type == PackType.CLIENT_RESOURCES ? Set.of("frontierhunts") : Set.of();
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
      if (serializer == PackMetadataSection.TYPE) {
         return (T)new PackMetadataSection(Component.literal("Vanilla textures for FrontierHunts' built blocks"),
            SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
      }
      return null;
   }

   @Override
   public PackLocationInfo location() {
      return this.location;
   }

   @Override
   public void close() {
   }
}
