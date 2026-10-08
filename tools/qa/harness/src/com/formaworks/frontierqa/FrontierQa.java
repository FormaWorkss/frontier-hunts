package com.formaworks.frontierqa;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frontier Hunts QA harness. A tiny test-only mod (never shipped) that drives server-side smoke checks from the
 * dedicated-server console: /fhqa registry | spawn [level] [n] | blocks | items | recipes | loot | count.
 * Every line it prints starts with [FHQA] so tools/qa/server_smoke.sh can grep the verdicts.
 */
@Mod("frontierqa")
public final class FrontierQa {
   static final Logger LOG = LoggerFactory.getLogger("FHQA");
   static final Set<String> NS = Set.of("frontierhunts", "frontierstructures");

   public FrontierQa(IEventBus modBus) {
      NeoForge.EVENT_BUS.addListener(FrontierQa::commands);
      HerdTest.hook(); // [herds] where animals stand when they are saved / loaded (unload and restart checks)
   }

   static void say(String s) {
      LOG.info("[FHQA] " + s);
   }

   static void fail(String s, Throwable t) {
      LOG.error("[FHQA] FAIL " + s, t);
   }

   static boolean ours(ResourceLocation id) {
      return id != null && NS.contains(id.getNamespace());
   }

   static void commands(RegisterCommandsEvent e) {
      e.getDispatcher().register(Commands.literal("fhqa").requires(s -> s.hasPermission(2))
         .then(Commands.literal("registry").executes(c -> registry(c.getSource())))
         .then(Commands.literal("items").executes(c -> items(c.getSource())))
         .then(Commands.literal("packs").executes(c -> packs(c.getSource())))
         .then(Commands.literal("academy").then(Commands.argument("course", StringArgumentType.word())
            .executes(c -> academy(c.getSource(), StringArgumentType.getString(c, "course")))))
         .then(Commands.literal("recipes").executes(c -> recipes(c.getSource())))
         .then(Commands.literal("sledtest").then(Commands.argument("n", IntegerArgumentType.integer(1, 30)).then(Commands.argument("radius", IntegerArgumentType.integer(100, 8000))
            .executes(c -> SledTest.run(c.getSource(), IntegerArgumentType.getInteger(c, "n"), IntegerArgumentType.getInteger(c, "radius")))
            .then(Commands.argument("kind", StringArgumentType.word())
               .executes(c -> SledTest.run(c.getSource(), IntegerArgumentType.getInteger(c, "n"), IntegerArgumentType.getInteger(c, "radius"),
                  StringArgumentType.getString(c, "kind")))))))
         .then(Commands.literal("sledtrace").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
            .then(Commands.argument("kind", StringArgumentType.word()).then(Commands.argument("up", IntegerArgumentType.integer(0, 1))
               .executes(c -> SledTest.trace(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"),
                  StringArgumentType.getString(c, "kind"), IntegerArgumentType.getInteger(c, "up") == 1)))))))
         .then(Commands.literal("chunkhash").then(Commands.argument("r", IntegerArgumentType.integer(1, 16))
            .executes(c -> chunkHash(c.getSource(), IntegerArgumentType.getInteger(c, "r")))))
         .then(Commands.literal("locatebench").then(Commands.argument("biome", StringArgumentType.greedyString())
            .executes(c -> locateBench(c.getSource(), StringArgumentType.getString(c, "biome")))))
         .then(Commands.literal("loot").executes(c -> loot(c.getSource())))
         .then(Commands.literal("blocks").then(Commands.argument("level", StringArgumentType.greedyString())
            .executes(c -> blocks(c.getSource(), StringArgumentType.getString(c, "level")))))
         .then(Commands.literal("column").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
            .executes(c -> column(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
         .then(Commands.literal("crowns").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
            .executes(c -> crowns(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
         .then(Commands.literal("count").then(Commands.argument("level", StringArgumentType.greedyString())
            .executes(c -> count(c.getSource(), StringArgumentType.getString(c, "level")))))
         .then(Commands.literal("flow").then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(c -> FlowTest.run(c.getSource(), StringArgumentType.getString(c, "args")))))
         .then(Commands.literal("clothing").then(Commands.argument("case", StringArgumentType.greedyString()) // [clothing]
            .executes(c -> ClothingTest.run(c.getSource(), StringArgumentType.getString(c, "case")))))
         .then(Commands.literal("bench").then(Commands.argument("case", StringArgumentType.greedyString()) // [benches]
            .executes(c -> BenchTest.run(c.getSource(), StringArgumentType.getString(c, "case")))))
         .then(Commands.literal("sticks").then(Commands.argument("case", StringArgumentType.greedyString()) // [sticks]
            .executes(c -> SticksTest.run(c.getSource(), StringArgumentType.getString(c, "case")))))
         .then(Commands.literal("herds").then(Commands.argument("args", StringArgumentType.greedyString()) // [herds]
            .executes(c -> HerdTest.run(c.getSource(), StringArgumentType.getString(c, "args")))))
         .then(Commands.literal("smalls").then(Commands.argument("case", StringArgumentType.greedyString()) // [smalls]
            .executes(c -> SmallsTest.run(c.getSource(), StringArgumentType.getString(c, "case")))))
         .then(Commands.literal("phone").then(Commands.argument("case", StringArgumentType.greedyString()) // [phone]
            .executes(c -> PhoneTest.run(c.getSource(), StringArgumentType.getString(c, "case")))))
         .then(Commands.literal("persist").then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(c -> Persist.run(c.getSource(), StringArgumentType.getString(c, "args")))))
         .then(Commands.literal("player").then(Commands.argument("cmd", StringArgumentType.greedyString())
            .executes(c -> asPlayer(c.getSource(), StringArgumentType.getString(c, "cmd")))))
         .then(Commands.literal("tickplayer").then(Commands.argument("n", IntegerArgumentType.integer(1, 4000))
            .executes(c -> tickPlayer(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
         .then(Commands.literal("spawn").then(Commands.argument("n", IntegerArgumentType.integer(1, 8))
            .then(Commands.argument("level", StringArgumentType.greedyString())
               .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "level"), IntegerArgumentType.getInteger(c, "n")))))));
   }

   static ServerLevel level(MinecraftServer server, String id) {
      ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id.trim()));
      ServerLevel l = server.getLevel(key);
      if (l == null) say("FAIL no level " + id);
      return l;
   }

   static int registry(CommandSourceStack src) {
      int blocks = 0, items = 0, ents = 0, sounds = 0, bes = 0, menus = 0, parts = 0, effects = 0;
      for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) if (ours(id)) blocks++;
      for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) if (ours(id)) items++;
      List<String> types = new ArrayList<>();
      for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) if (ours(id)) { ents++; types.add(id.toString()); }
      for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) if (ours(id)) sounds++;
      for (ResourceLocation id : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) if (ours(id)) bes++;
      for (ResourceLocation id : BuiltInRegistries.MENU.keySet()) if (ours(id)) menus++;
      for (ResourceLocation id : BuiltInRegistries.PARTICLE_TYPE.keySet()) if (ours(id)) parts++;
      for (ResourceLocation id : BuiltInRegistries.MOB_EFFECT.keySet()) if (ours(id)) effects++;
      say("registry blocks=" + blocks + " items=" + items + " entities=" + ents + " sounds=" + sounds + " blockEntities=" + bes
         + " menus=" + menus + " particles=" + parts + " effects=" + effects);
      say("entity types: " + String.join(" ", types));
      for (ResourceKey<Level> k : src.getServer().levelKeys()) say("level " + k.location());
      // every id of ours, for tools/qa/static_sweep.py (models, lang, sounds completeness)
      try {
         com.google.gson.JsonObject o = new com.google.gson.JsonObject();
         o.add("block", ids(BuiltInRegistries.BLOCK.keySet()));
         o.add("item", ids(BuiltInRegistries.ITEM.keySet()));
         o.add("entity_type", ids(BuiltInRegistries.ENTITY_TYPE.keySet()));
         o.add("sound_event", ids(BuiltInRegistries.SOUND_EVENT.keySet()));
         o.add("particle_type", ids(BuiltInRegistries.PARTICLE_TYPE.keySet()));
         o.add("block_entity_type", ids(BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()));
         o.add("menu", ids(BuiltInRegistries.MENU.keySet()));
         o.add("mob_effect", ids(BuiltInRegistries.MOB_EFFECT.keySet()));
         o.add("creative_tab", ids(BuiltInRegistries.CREATIVE_MODE_TAB.keySet()));
         java.util.Map<String, java.util.List<String>> blockItems = new java.util.TreeMap<>();
         com.google.gson.JsonArray bi = new com.google.gson.JsonArray();
         for (Item it : BuiltInRegistries.ITEM) {
            if (it instanceof net.minecraft.world.item.BlockItem b && ours(BuiltInRegistries.ITEM.getKey(it))) bi.add(BuiltInRegistries.ITEM.getKey(it).toString());
         }
         o.add("block_item", bi);
         java.nio.file.Files.writeString(src.getServer().getServerDirectory().resolve("fhqa_ids.json"), o.toString());
         say("wrote fhqa_ids.json");
      } catch (Throwable t) {
         fail("ids dump", t);
      }
      return 1;
   }

   static com.google.gson.JsonArray ids(java.util.Set<ResourceLocation> keys) {
      com.google.gson.JsonArray a = new com.google.gson.JsonArray();
      keys.stream().filter(FrontierQa::ours).map(ResourceLocation::toString).sorted().forEach(a::add);
      return a;
   }

   /**
    * Starts an academy session for the fake player directly (TrainingService.begin, what the departure fade ends in for a
    * real player): builds the plot in frontierhunts:training_grounds and teleports the hunter there.
    */
   static int academy(CommandSourceStack src, String course) {
      var fp = fake(src.getServer());
      try {
         Class<?> ts = Class.forName("com.formaworks.frontierhunts.academy.TrainingService");
         Class<?> cc = Class.forName("com.formaworks.frontierhunts.academy.Course");
         Object c = cc.getMethod("byKey", String.class).invoke(null, course);
         if (c == null) { say("FAIL unknown course " + course); return 0; }
         var m = ts.getDeclaredMethod("begin", net.minecraft.server.level.ServerPlayer.class, cc);
         m.setAccessible(true);
         m.invoke(null, fp, c);
         say("academy " + course + " begun: fake player now in " + fp.level().dimension().location() + " at " + fp.blockPosition().toShortString());
      } catch (java.lang.reflect.InvocationTargetException t) {
         fail("academy begin " + course, t.getCause());
      } catch (Throwable t) {
         fail("academy begin " + course, t);
      }
      return 1;
   }

   static int packs(CommandSourceStack src) {
      for (var pack : src.getServer().getPackRepository().getAvailablePacks()) {
         say("pack " + pack.getId() + " compat=" + pack.getCompatibility() + " selected=" + src.getServer().getPackRepository().getSelectedIds().contains(pack.getId()));
         for (var child : pack.getChildren()) {
            say("  child " + child.getId() + " compat=" + child.getCompatibility() + (child.getCompatibility().isCompatible() ? "" : "  <-- FLAGGED INCOMPATIBLE"));
         }
      }
      return 1;
   }

   static int items(CommandSourceStack src) {
      int ok = 0, bad = 0;
      for (Item item : BuiltInRegistries.ITEM) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
         if (!ours(id)) continue;
         try {
            ItemStack s = new ItemStack(item);
            s.getHoverName();
            s.getMaxStackSize();
            s.getRarity();
            s.copy();
            ItemStack.CODEC.encodeStart(src.getServer().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), s).getOrThrow();
            ok++;
         } catch (Throwable t) {
            bad++;
            fail("item " + id, t);
         }
      }
      say("items ok=" + ok + " bad=" + bad);
      return 1;
   }

   static int recipes(CommandSourceStack src) {
      int ok = 0, bad = 0;
      for (RecipeHolder<?> h : src.getServer().getRecipeManager().getRecipes()) {
         if (!ours(h.id())) continue;
         try {
            ItemStack out = h.value().getResultItem(src.getServer().registryAccess());
            if (out.isEmpty() && !h.value().isSpecial()) {
               bad++;
               say("FAIL recipe " + h.id() + " has empty result (" + h.value().getType() + ")");
               continue;
            }
            if (h.value().getIngredients().isEmpty() && !h.value().isSpecial()) say("WARN recipe " + h.id() + " has no ingredients (" + h.value().getType() + ")");
            for (var ing : h.value().getIngredients()) {
               if (!ing.isEmpty() && ing.getItems().length == 0) say("WARN recipe " + h.id() + " has an ingredient that matches nothing (empty tag?)");
            }
            ok++;
         } catch (Throwable t) {
            bad++;
            fail("recipe " + h.id(), t);
         }
      }
      say("recipes ok=" + ok + " bad=" + bad);
      return 1;
   }

   static int loot(CommandSourceStack src) {
      MinecraftServer server = src.getServer();
      int ok = 0, missing = 0;
      var tables = server.reloadableRegistries().getKeys(Registries.LOOT_TABLE);
      int ourTables = 0;
      for (ResourceLocation id : tables) if (ours(id)) ourTables++;
      // every block of ours that drops via a loot table must have one
      for (Block b : BuiltInRegistries.BLOCK) {
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
         if (!ours(id)) continue;
         ResourceKey<LootTable> key = b.getLootTable();
         if (key == null || key.location().getPath().equals("empty")) continue;
         if (!tables.contains(key.location())) {
            missing++;
            say("WARN block " + id + " has no loot table " + key.location() + " (drops nothing when broken)");
         } else ok++;
      }
      for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
         ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(t);
         if (!ours(id)) continue;
         var key = t.getDefaultLootTable();
         if (key != null && !tables.contains(key.location()) && t.getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
            say("INFO entity " + id + " has no default loot table " + key.location());
         }
      }
      say("loot ourTables=" + ourTables + " blocksWithTable=" + ok + " blocksMissingTable=" + missing);
      return 1;
   }

   static int blocks(CommandSourceStack src, String levelId) {
      ServerLevel level = level(src.getServer(), levelId);
      if (level == null) return 0;
      BlockPos origin = BlockPos.containing(src.getPosition()).offset(40, 0, 40);
      int x = 0, z = 0, ok = 0, bad = 0;
      int y = 200;
      for (Block b : BuiltInRegistries.BLOCK) {
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
         if (!ours(id)) continue;
         BlockPos p = origin.offset(x * 3, y - origin.getY(), z * 3);
         if (++x > 20) { x = 0; z++; }
         try {
            level.getChunkAt(p);
            level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), 2);
            BlockState st = b.defaultBlockState();
            level.setBlock(p, st, 3);
            BlockState placed = level.getBlockState(p);
            placed.getShape(level, p);
            placed.getCollisionShape(level, p);
            placed.getLightEmission(level, p);
            Block.getDrops(placed, level, p, level.getBlockEntity(p));
            if (placed.isRandomlyTicking()) placed.randomTick(level, p, level.random);
            placed.tick(level, p, level.random);
            ok++;
         } catch (Throwable t) {
            bad++;
            fail("block " + id + " at " + p.toShortString(), t);
         }
      }
      say("blocks placed ok=" + ok + " bad=" + bad + " in " + levelId);
      return 1;
   }

   static int spawn(CommandSourceStack src, String levelId, int n) {
      ServerLevel level = level(src.getServer(), levelId);
      if (level == null) return 0;
      BlockPos base = BlockPos.containing(src.getPosition());
      int ok = 0, bad = 0, i = 0;
      for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
         ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
         if (!ours(id)) continue;
         for (int k = 0; k < n; k++) {
            int px = base.getX() + (i % 6) * 6 - 15 + k * 2, pz = base.getZ() + (i / 6) * 6 - 15;
            level.getChunkAt(new BlockPos(px, 0, pz));
            int py = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            try {
               Entity e = type.create(level);
               if (e == null) { say("WARN entity " + id + " create() returned null"); break; }
               e.moveTo(px + 0.5, py, pz + 0.5, level.random.nextFloat() * 360f, 0f);
               if (e instanceof Mob m) m.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.COMMAND, null);
               if (!level.addFreshEntity(e)) say("WARN entity " + id + " was refused by addFreshEntity");
               else ok++;
            } catch (Throwable t) {
               bad++;
               fail("spawn " + id, t);
            }
         }
         i++;
      }
      say("spawn ok=" + ok + " bad=" + bad + " in " + levelId);
      return 1;
   }

   static net.neoforged.neoforge.common.util.FakePlayer fake(MinecraftServer server) {
      ServerLevel level = server.overworld();
      var fp = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
         new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("fhqa".getBytes()), "FHQA"));
      if (fp.tickCount == 0 && fp.getY() < -1000 || fp.position().lengthSqr() == 0) {
         BlockPos sp = level.getSharedSpawnPos();
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
         fp.moveTo(sp.getX() + 0.5, y, sp.getZ() + 0.5, 0f, 0f);
      }
      wire(fp);
      return fp;
   }

   static boolean wired;

   /**
    * A NeoForge FakePlayer has a Connection with no netty channel, so every hasChannel()/payload send on it throws.
    * Real players always have one. Give the fake player an EmbeddedChannel and advertise every frontierhunts /
    * frontierstructures PLAY payload as negotiated, so server code runs exactly the paths it runs for a modded client
    * (FakePlayerNetHandler.send() then drops the packets).
    */
   static void wire(net.neoforged.neoforge.common.util.FakePlayer fp) {
      if (wired) return;
      try {
         net.minecraft.network.Connection conn = fp.connection.getConnection();
         var f = net.minecraft.network.Connection.class.getDeclaredField("channel");
         f.setAccessible(true);
         if (f.get(conn) != null) return; // [1.2.7] this hunter's connection is wired already (one per fake player)
         f.set(conn, new io.netty.channel.embedded.EmbeddedChannel());
         var r = net.neoforged.neoforge.network.registration.NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
         r.setAccessible(true);
         @SuppressWarnings("unchecked")
         var regs = (java.util.Map<net.minecraft.network.ConnectionProtocol, java.util.Map<ResourceLocation, ?>>) r.get(null);
         var ids = net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateCommonChannels(conn, net.minecraft.network.ConnectionProtocol.PLAY);
         int n = 0;
         for (ResourceLocation id : regs.getOrDefault(net.minecraft.network.ConnectionProtocol.PLAY, java.util.Map.of()).keySet()) {
            if (ours(id)) { ids.add(id); n++; }
         }
         say("fake player " + fp.getGameProfile().getName() + " wired with " + n + " play payload channels");
      } catch (Throwable t) {
         fail("could not wire the fake player connection", t);
         wired = true;
      }
   }

   static int asPlayer(CommandSourceStack src, String cmd) {
      MinecraftServer server = src.getServer();
      var fp = fake(server);
      net.minecraft.commands.CommandSource out = new net.minecraft.commands.CommandSource() {
         @Override public void sendSystemMessage(net.minecraft.network.chat.Component c) { say("player> " + c.getString()); }
         @Override public boolean acceptsSuccess() { return true; }
         @Override public boolean acceptsFailure() { return true; }
         @Override public boolean shouldInformAdmins() { return false; }
      };
      CommandSourceStack stack = new CommandSourceStack(out, fp.position(), fp.getRotationVector(), fp.serverLevel(), 4, "FHQA",
         fp.getDisplayName(), server, fp);
      say("as player: /" + cmd);
      try {
         server.getCommands().performPrefixedCommand(stack, cmd);
      } catch (Throwable t) {
         fail("player command /" + cmd, t);
      }
      return 1;
   }

   static int tickPlayer(CommandSourceStack src, int n) {
      var fp = fake(src.getServer());
      int bad = 0;
      for (int i = 0; i < n; i++) {
         try {
            fp.doTick();
         } catch (Throwable t) {
            if (bad++ < 3) fail("fake player tick " + i, t);
         }
      }
      say("ticked fake player " + n + " times, failures=" + bad + " at " + fp.blockPosition().toShortString());
      return 1;
   }

   /** [1.1.9] the top of a column in the overworld: surface height, the top blocks and the biome */
   static int column(CommandSourceStack src, int x, int z) {
      ServerLevel level = src.getServer().overworld();
      level.getChunkAt(new BlockPos(x, 0, z));
      int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
      StringBuilder b = new StringBuilder("column " + x + " " + z + " y=" + y + " biome=" + level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(k -> k.location().toString()).orElse("?") + " :");
      for (int k = 1; k <= 4; k++) {
         BlockState st = level.getBlockState(new BlockPos(x, y - k, z));
         b.append(' ').append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
         if (st.hasProperty(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)) b.append('x').append(st.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
      }
      BlockPos tp = new BlockPos(x, y - 1, z), bp = new BlockPos(x, y - 2, z);
      BlockState t = level.getBlockState(tp), u = level.getBlockState(bp);
      b.append(" | top replaceable=").append(t.canBeReplaced()).append(" noCollision=").append(t.getCollisionShape(level, tp).isEmpty())
         .append(" | under sturdy=").append(u.isFaceSturdy(level, bp, net.minecraft.core.Direction.UP)).append(" snowSurvives=")
         .append(Blocks.SNOW.defaultBlockState().canSurvive(level, tp)).append(" state=").append(t);
      say(b.toString());
      return 1;
   }

   /** [1.2.0] in the 32x32 around x z: columns topped by leaves, snow lying on leaves, snow on the ground under a crown, snow on open ground */
   static int crowns(CommandSourceStack src, int cx, int cz) {
      ServerLevel level = src.getServer().overworld();
      int leafTop = 0, snowOnLeaves = 0, snowUnder = 0, snowOpen = 0, cols = 0, thick = 0;
      for (int x = cx - 16; x < cx + 16; x++) {
         for (int z = cz - 16; z < cz + 16; z++) {
            cols++;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            BlockState top = level.getBlockState(new BlockPos(x, y, z)), under = level.getBlockState(new BlockPos(x, y - 1, z));
            boolean leaves = top.is(net.minecraft.tags.BlockTags.LEAVES);
            if (leaves) {
               leafTop++;
               for (int gy = y - 1; gy > y - 40; gy--) {
                  BlockState g = level.getBlockState(new BlockPos(x, gy, z));
                  if (g.isAir() || g.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                  if (g.is(Blocks.SNOW)) snowUnder++;
                  break;
               }
            } else if (top.is(Blocks.SNOW)) {
               if (under.is(net.minecraft.tags.BlockTags.LEAVES)) {
                  snowOnLeaves++;
                  if (top.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) > 1) thick++;
                  for (int gy = y - 2; gy > y - 40; gy--) {
                     BlockState g = level.getBlockState(new BlockPos(x, gy, z));
                     if (g.isAir() || g.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                     if (g.is(Blocks.SNOW)) snowUnder++;
                     break;
                  }
               } else snowOpen++;
            }
         }
      }
      say("crowns " + cx + " " + cz + ": columns=" + cols + " crownTops=" + leafTop + " snowOnLeaves=" + snowOnLeaves + " snowUnderCrowns=" + snowUnder + " snowOpen=" + snowOpen + " thickOnLeaves=" + thick);
      return 1;
   }

   static int count(CommandSourceStack src, String levelId) {
      ServerLevel level = level(src.getServer(), levelId);
      if (level == null) return 0;
      java.util.Map<String, Integer> c = new java.util.TreeMap<>();
      for (Entity e : level.getAllEntities()) {
         ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
         if (ours(id)) c.merge(id.getPath() + (e.isAlive() ? "" : "(dead)"), 1, Integer::sum);
      }
      say("alive in " + levelId + ": " + c);
      return 1;
   }

   /** [1.2.5] time what /locate biome does (ServerLevel.findClosestBiome3d, radius 6400, steps 32/64) and the raw lookup */
   static int locateBench(net.minecraft.commands.CommandSourceStack src, String ids) {
      net.minecraft.server.level.ServerLevel level = src.getServer().overworld();
      var gen = level.getChunkSource().getGenerator();
      var sampler = level.getChunkSource().randomState().sampler();
      say("locatebench source " + gen.getBiomeSource().getClass().getName() + ", " + gen.getBiomeSource().possibleBiomes().size() + " possible biomes");
      // the lookups /locate makes: a spiral out from the origin, one every 32 blocks (8 quarts)
      java.util.List<String> list = new java.util.ArrayList<>();
      if (ids.equals("all")) {
         for (var h : gen.getBiomeSource().possibleBiomes()) {
            h.unwrapKey().ifPresent(k -> list.add(k.location().toString()));
         }
         java.util.Collections.sort(list);
      } else {
         list.addAll(java.util.List.of(ids.split(" ")));
      }
      int found = 0, right = 0, gaveUp = 0;
      long worst = 0;
      for (String id : list) {
         var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(id));
         long t = System.nanoTime();
         var res = level.findClosestBiome3d(h -> h.is(key), net.minecraft.core.BlockPos.ZERO, 6400, 32, 64);
         long ms = (System.nanoTime() - t) / 1000000L;
         String where = "not found";
         if (res != null) {
            var p = res.getFirst();
            // the biome stored in the chunk at that column (level.getBiome would blur the border by a few blocks)
            var chunkBiome = level.getChunk(p.getX() >> 4, p.getZ() >> 4).getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(p.getX()),
               net.minecraft.core.QuartPos.fromBlock(80), net.minecraft.core.QuartPos.fromBlock(p.getZ()));
            where = "at " + p.getX() + " " + p.getY() + " " + p.getZ() + " (chunk biome there: "
               + chunkBiome.unwrapKey().map(k -> k.location().toString()).orElse("?") + ")";
         }
         say(String.format("locatebench %s: %d ms, %s", id, ms, where));
         worst = Math.max(worst, ms);
         if (res != null) {
            found++;
            if (where.contains("(chunk biome there: " + id + ")")) {
               right++;
            }
         } else if (ms > 9000) {
            gaveUp++;
         }
      }
      say(String.format("locatebench summary: %d biomes, %d found (%d with the chunks holding that biome there), %d gave up at the time limit, slowest %d ms",
         list.size(), found, right, gaveUp, worst));
      return 1;
   }

   /** [1.2.5] a hash of every block state and biome in fresh chunks far from spawn, one per spot (worldgen comparisons:
    * the chunks are generated inside the command, so nothing has ticked them) */
   static int chunkHash(net.minecraft.commands.CommandSourceStack src, int r) {
      net.minecraft.server.level.ServerLevel level = src.getServer().overworld();
      int[] spots = {300, 300, -420, 180, 600, -1000, 1450, 230, -900, -900, 2000, 2000};
      StringBuilder out = new StringBuilder();
      for (int s = 0; s < spots.length; s += 2) {
         long hash = 1125899906842597L;
         int cx0 = spots[s], cz0 = spots[s + 1];
         for (int cx = cx0 - r; cx <= cx0 + r; cx++) {
            for (int cz = cz0 - r; cz <= cz0 + r; cz++) {
               var chunk = level.getChunk(cx, cz);
               for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += 1) {
                  for (int x = 0; x < 16; x += 3) {
                     for (int z = 0; z < 16; z += 3) {
                        hash = 31 * hash + net.minecraft.world.level.block.Block.getId(chunk.getBlockState(new net.minecraft.core.BlockPos(cx * 16 + x, y, cz * 16 + z)));
                     }
                  }
               }
               for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += 16) {
                  hash = 31 * hash + chunk.getNoiseBiome(0, y >> 2, 0).unwrapKey().map(k -> k.location().toString().hashCode()).orElse(0);
               }
            }
         }
         out.append(String.format(" %d,%d=%08x", cx0, cz0, hash & 0xFFFFFFFFL));
      }
      say("chunkhash r=" + r + out);
      return 1;
   }
}
