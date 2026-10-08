package com.formaworks.frontierhunts.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * [shaders] Wind for the Frontier's own trees, grass and plants under any shader pack.
 *
 * <p>Shader packs move foliage by block id: each pack's block.properties lists vanilla names (oak_leaves, short_grass,
 * fern, vine ...) and the pack waves whatever carries those ids. Our leaves, needles, boughs, pasture grass, thickets,
 * ferns, reeds and overgrowth have names no pack knows, so they got id 0 and stood still. When Iris loads a pack, this
 * gives each of our foliage block states the id that the pack gave the nearest vanilla plant (leaves -> oak/spruce
 * leaves, grass and shrubs -> short grass, ferns -> fern, seedlings -> sapling, overgrowth -> vine), so every pack
 * waves them exactly as it waves its own foliage, with its own wind, subsurface light and material settings.
 *
 * <p>Iris is optional and reached only by reflection: without Iris (or with an Iris version that has moved these
 * classes) this does nothing. The pack's map is copied, never edited in place (Iris's chunk meshers read it from
 * worker threads), and the loaded chunks are rebuilt once. Checked against Iris 1.8 for 1.21.1 (WorldRenderingSettings).
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class ShaderFoliage {
   private static final String[] SETTINGS = {
      "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings",
      "net.irisshaders.iris.shaderpack.materialmap.BlockRenderingSettings",
      "net.coderbot.iris.block_rendering.BlockRenderingSettings"
   };
   private static String[] NAMESPACES = {"frontierhunts", "frontierstructures"};

   private static boolean resolved;
   private static Object settings;
   private static Method getIds;
   private static Method setIds;
   private static Field idsField;
   private static Method clearReload;
   /** the map Iris holds that we last looked at (ours after a swap) */
   private static Object seen;
   private static boolean failed;
   private static int mapped;

   private ShaderFoliage() {
   }

   /** Number of our block states given a foliage id under the current shader pack (0 without one). */
   public static int mapped() {
      return mapped;
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      if (failed) {
         return;
      }
      try {
         if (!resolved) {
            resolved = true;
            resolve();
         }
         if (settings == null) {
            failed = true;
            return;
         }
         Object current = getIds != null ? getIds.invoke(settings) : idsField.get(settings);
         if (current == seen) {
            return;
         }
         seen = current;
         mapped = 0;
         if (!(current instanceof Object2IntMap<?> raw) || raw.isEmpty()) {
            return;
         }
         @SuppressWarnings("unchecked")
         Object2IntMap<BlockState> ids = (Object2IntMap<BlockState>)raw;
         Object2IntOpenHashMap<BlockState> copy = new Object2IntOpenHashMap<>(ids);
         copy.defaultReturnValue(ids.defaultReturnValue());
         int added = 0;
         for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null || !ours(key.getNamespace())) {
               continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
               if (ids.containsKey(state)) {
                  continue; // the pack (or another mod) already names this block: leave its choice
               }
               for (BlockState analog : analogs(key.getPath(), state)) {
                  if (ids.containsKey(analog)) {
                     copy.put(state, ids.getInt(analog));
                     added++;
                     break;
                  }
               }
            }
         }
         if (added == 0) {
            return;
         }
         if (setIds != null) {
            setIds.invoke(settings, copy);
         } else {
            idsField.set(settings, copy);
         }
         seen = copy;
         mapped = added;
         // Iris has already meshed the visible chunks with its own map by now: rebuild them once with ours (Sodium reads
         // the map live while meshing, so new chunks pick it up anyway) and clear the flag our swap raised
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.levelRenderer != null && mc.level != null) {
            mc.levelRenderer.allChanged();
         }
         if (clearReload != null) {
            clearReload.invoke(settings);
         }
         com.mojang.logging.LogUtils.getLogger().info("Frontier Hunts: shader wind for {} foliage block states", added);
      } catch (Throwable t) {
         failed = true;
         com.mojang.logging.LogUtils.getLogger().info("Frontier Hunts: shader foliage ids unavailable ({})", t.toString());
      }
   }

   private static void resolve() {
      for (String name : SETTINGS) {
         try {
            Class<?> c = Class.forName(name, false, ShaderFoliage.class.getClassLoader());
            Object inst = c.getField("INSTANCE").get(null);
            Method g = null, s = null, r = null;
            Field f = null;
            try {
               g = c.getMethod("getBlockStateIds");
            } catch (NoSuchMethodException ignored) {
            }
            for (Method m : c.getMethods()) {
               if (m.getName().equals("setBlockStateIds") && m.getParameterCount() == 1
                  && m.getParameterTypes()[0].isAssignableFrom(Object2IntOpenHashMap.class)) {
                  s = m;
               }
            }
            try {
               r = c.getMethod("clearReloadRequired");
            } catch (NoSuchMethodException ignored) {
            }
            if (g == null || s == null) {
               try {
                  f = c.getDeclaredField("blockStateIds");
                  f.setAccessible(true);
               } catch (ReflectiveOperationException | RuntimeException ignored) {
                  f = null;
               }
            }
            if ((g != null || f != null) && (s != null || f != null)) {
               settings = inst;
               getIds = g;
               setIds = s;
               idsField = f;
               clearReload = r;
               return;
            }
         } catch (Throwable ignored) {
            // not this layout (or no Iris at all)
         }
      }
   }

   private static boolean ours(String ns) {
      for (String n : NAMESPACES) {
         if (n.equals(ns)) {
            return true;
         }
      }
      return false;
   }

   /** Vanilla states whose shader id a block of ours should borrow, best first; empty for non-foliage. */
   static List<BlockState> analogs(String path, BlockState state) {
      List<BlockState> out = new ArrayList<>(4);
      Block b = state.getBlock();
      boolean needles = path.contains("needle") || path.contains("bough") || path.contains("spruce") || path.contains("pine")
         || path.contains("fir_") || path.contains("larch");
      if (b instanceof LeavesBlock || path.endsWith("_leaves") || path.endsWith("_needles") || path.endsWith("_boughs")) {
         if (needles) {
            out.add(Blocks.SPRUCE_LEAVES.defaultBlockState());
            out.add(Blocks.OAK_LEAVES.defaultBlockState());
         } else {
            out.add(Blocks.OAK_LEAVES.defaultBlockState());
            out.add(Blocks.BIRCH_LEAVES.defaultBlockState());
            out.add(Blocks.SPRUCE_LEAVES.defaultBlockState());
         }
         // packs that list leaves with their properties
         out.add(withProps(needles ? Blocks.SPRUCE_LEAVES.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState(), state));
         return out;
      }
      if (path.equals("alpine_overgrowth")) {
         out.add(Blocks.VINE.defaultBlockState());
         out.add(Blocks.OAK_LEAVES.defaultBlockState());
         return out;
      }
      boolean plant = b instanceof BushBlock || path.equals("undergrowth");
      if (!plant) {
         return out;
      }
      if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
         out.add(Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)));
      }
      if (path.contains("fern")) {
         out.add(Blocks.FERN.defaultBlockState());
      } else if (path.contains("seedling") || path.contains("sapling")) {
         out.add(Blocks.SPRUCE_SAPLING.defaultBlockState());
         out.add(Blocks.OAK_SAPLING.defaultBlockState());
      }
      out.add(Blocks.SHORT_GRASS.defaultBlockState());
      out.add(Blocks.FERN.defaultBlockState());
      out.add(Blocks.TALL_GRASS.defaultBlockState());
      return out;
   }

   /** The vanilla state with any properties it shares with ours (persistent, waterlogged, distance ...). */
   private static BlockState withProps(BlockState vanilla, BlockState ours) {
      BlockState v = vanilla;
      for (var p : ours.getProperties()) {
         v = copy(v, ours, p);
      }
      return v;
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static BlockState copy(BlockState into, BlockState from, net.minecraft.world.level.block.state.properties.Property p) {
      if (!into.hasProperty(p)) {
         return into;
      }
      try {
         return into.setValue(p, from.getValue(p));
      } catch (RuntimeException e) {
         return into;
      }
   }
}
