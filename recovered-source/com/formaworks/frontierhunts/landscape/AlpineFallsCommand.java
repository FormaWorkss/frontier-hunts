package com.formaworks.frontierhunts.landscape;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineFallsCommand {
   @SubscribeEvent
   public static void register(RegisterCommandsEvent var0) {
      List var1 = Arrays.stream(AlpineFalls.Type.values()).map(var0x -> var0x.name().toLowerCase(Locale.ROOT)).toList();
      List var2 = Arrays.stream(AlpineFalls.Palette.values()).map(var0x -> var0x.name().toLowerCase(Locale.ROOT)).toList();
      var0.getDispatcher()
         .register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("frontierfalls")
                     .requires(var0x -> var0x.hasPermission(2)))
                  .then(Commands.literal("locate").executes(AlpineFallsCommand::locate)))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("place").executes(var0x -> place(var0x, null, null)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("type", StringArgumentType.word()).suggests((var1x, var2x) -> {
                              var1.forEach(var2x::suggest);
                              return var2x.buildFuture();
                           }).executes(var0x -> place(var0x, StringArgumentType.getString(var0x, "type"), null)))
                           .then(Commands.argument("palette", StringArgumentType.word()).suggests((var1x, var2x) -> {
                              var2.forEach(var2x::suggest);
                              return var2x.buildFuture();
                           }).executes(var0x -> place(var0x, StringArgumentType.getString(var0x, "type"), StringArgumentType.getString(var0x, "palette"))))
                     )
               )
         );
   }

   private static int locate(CommandContext<CommandSourceStack> var0) {
      CommandSourceStack var1 = (CommandSourceStack)var0.getSource();
      ServerLevel var2 = var1.getLevel();
      if (var2.getChunkSource().getGenerator() instanceof AlpineGenerator var3 && var3.layout().falls() != null) {
         AlpineFalls var17 = var3.layout().falls();
         BlockPos var5 = BlockPos.containing(var1.getPosition());
         int var6 = Math.floorDiv(var5.getX(), 384);
         int var7 = Math.floorDiv(var5.getZ(), 384);
         AlpineFalls.Site var8 = null;
         double var9 = Double.MAX_VALUE;

         for (int var11 = 0; var11 <= 5 && var8 == null; var11++) {
            for (int var12 = -var11; var12 <= var11; var12++) {
               for (int var13 = -var11; var13 <= var11; var13++) {
                  if (Math.max(Math.abs(var12), Math.abs(var13)) == var11) {
                     AlpineFalls.Site var14 = var17.site(var6 + var12, var7 + var13);
                     if (var14 != null) {
                        double var15 = Math.hypot((double)(var14.lipX - var5.getX()), (double)(var14.lipZ - var5.getZ()));
                        if (var15 < var9) {
                           var9 = var15;
                           var8 = var14;
                        }
                     }
                  }
               }
            }
         }

         if (var8 == null) {
            var1.sendFailure(Component.translatable("message.frontierhunts.falls.none"));
            return 0;
         }

         AlpineFalls.Site var18 = var8;
         String var19 = "/tp @s " + var18.lipX + " " + (var18.lipLevel + 2) + " " + var18.lipZ;
         var1.sendSuccess(
            () -> Component.literal(
                     String.format(
                        Locale.ROOT,
                        "%s %s waterfall, %d m drop, %d blocks away: ",
                        pretty(var18.type),
                        pretty(var18.palette),
                        var18.drop(),
                        Math.round(Math.hypot((double)(var18.lipX - var5.getX()), (double)(var18.lipZ - var5.getZ())))
                     )
                  )
                  .append(
                     Component.literal("[" + var18.lipX + ", " + var18.lipLevel + ", " + var18.lipZ + "]")
                        .withStyle(
                           var1xx -> var1xx.withColor(ChatFormatting.AQUA)
                                 .withClickEvent(new ClickEvent(Action.SUGGEST_COMMAND, var19))
                                 .withHoverEvent(
                                    new HoverEvent(
                                       net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to fill in a teleport to the lip")
                                    )
                                 )
                        )
                  ),
            false
         );
         return 1;
      }

      var1.sendFailure(
         Component.literal("Scenic waterfalls generate in new Frontier Alpine worlds (terrain revision 13). Use /frontierfalls place to sculpt one here.")
      );
      return 0;
   }

   private static String pretty(Enum<?> var0) {
      String var1 = var0.name().toLowerCase(Locale.ROOT);
      return Character.toUpperCase(var1.charAt(0)) + var1.substring(1);
   }

   private static int place(CommandContext<CommandSourceStack> var0, String var1, String var2) {
      CommandSourceStack var3 = (CommandSourceStack)var0.getSource();
      ServerLevel var4 = var3.getLevel();
      AlpineFalls.Type var5 = null;
      AlpineFalls.Palette var6 = null;

      try {
         if (var1 != null) {
            var5 = AlpineFalls.Type.valueOf(var1.toUpperCase(Locale.ROOT));
         }

         if (var2 != null) {
            var6 = AlpineFalls.Palette.valueOf(var2.toUpperCase(Locale.ROOT));
         }
      } catch (IllegalArgumentException var23) {
         var3.sendFailure(Component.literal("Unknown waterfall type or palette."));
         return 0;
      }

      Vec2 var7 = var3.getRotation();
      double var8 = Math.toRadians((double)var7.y);
      double var10 = -Math.sin(var8);
      double var12 = Math.cos(var8);
      Vec3 var14 = var3.getPosition();
      AlpineFallsCommand.WorldGround var15 = new AlpineFallsCommand.WorldGround(var4);
      AlpineLayout var16 = new AlpineLayout(var4.getSeed() ^ 23786L, 13);
      AlpineFalls var17 = new AlpineFalls(var16, var15, false);
      long var18 = var4.getRandom().nextLong();
      AlpineFalls.Site var20 = var17.designAt(var14.x, var14.z, -var10, -var12, var5, var6, var18);
      if (var20 == null) {
         var3.sendFailure(Component.literal("No usable drop here. Stand near the foot of a steep slope or cliff (at least 7 blocks high) and look up at it."));
         return 0;
      } else {
         int var21 = apply(var4, var17, var20, var15, var16);
         var3.sendSuccess(
            () -> Component.literal(
                  String.format(
                     Locale.ROOT,
                     "Sculpted a %s %s waterfall: %d m drop, lip at %d %d %d (%d blocks changed).",
                     pretty(var20.type),
                     pretty(var20.palette),
                     var20.drop(),
                     var20.lipX,
                     var20.lipLevel,
                     var20.lipZ,
                     var21
                  )
               ),
            true
         );
         return 1;
      }
   }

   private static int apply(ServerLevel var0, AlpineFalls var1, AlpineFalls.Site var2, AlpineFallsCommand.WorldGround var3, AlpineLayout var4) {
      int var5 = 0;
      ArrayList var6 = new ArrayList();
      MutableBlockPos var7 = new MutableBlockPos();

      for (int var8 = var2.z0; var8 < var2.z0 + var2.h; var8++) {
         for (int var9 = var2.x0; var9 < var2.x0 + var2.w; var9++) {
            AlpineLayout.Sample var10 = var3.sample((double)var9, (double)var8);
            AlpineLayout.Sample var11 = var1.edit(var2, var9, var8, var10);
            if (var11 != null) {
               double var12 = 0.0;

               for (int[] var17 : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
                  AlpineLayout.Sample var18 = var1.edit(
                     var2, var9 + var17[0], var8 + var17[1], var3.sample((double)(var9 + var17[0]), (double)(var8 + var17[1]))
                  );
                  double var19 = var18 == null ? var3.sample((double)(var9 + var17[0]), (double)(var8 + var17[1])).ground() : var18.ground();
                  var12 = Math.max(var12, Math.abs(var19 - var11.ground()) / 4.0);
               }

               AlpineColumn var29 = new AlpineColumn(var4, var11, var12, var9, var8, var11.ground());
               int var30 = var10.floor();
               int var31 = var11.floor();
               int var32 = AlpineFalls.kind(var11.site());
               boolean var33 = var32 == 4 || var32 == 5 || var32 == 8 || var32 == 6;
               int var34 = Math.min(var31, var30) - (var33 ? Math.max(4, var31 - var2.poolLevel + 3) : 4);
               if (AlpineFalls.hasCavity(var11.cavity())) {
                  var34 = Math.min(var34, AlpineFalls.cavityBottom(var11.cavity()) - 1);
               }

               var34 = Math.max(var0.getMinBuildHeight() + 1, var34);
               int var20 = Math.min(var0.getMaxBuildHeight() - 1, Math.max(var30 + 36, Math.max(var31, var11.water())));

               for (int var21 = var34; var21 <= var20; var21++) {
                  var7.set(var9, var21, var8);
                  BlockState var22 = var29.at(var21);
                  BlockState var23 = var0.getBlockState(var7);
                  if ((var21 >= Math.min(var31, var30) - 4 || !var22.is(Blocks.STONE) || var33) && var23 != var22) {
                     var0.setBlock(var7, var22, 18);
                     var5++;
                  }
               }

               if (AlpineFalls.spill(var11.site()) && var11.water() > var11.floor()) {
                  var6.add(new BlockPos(var9, var11.water(), var8));
               }
            }
         }
      }

      for (BlockPos var26 : var6) {
         var0.scheduleTick(var26, Fluids.WATER, 2);
      }

      AlpineFallsDressing.Columns var25 = (var3x, var4x) -> {
         AlpineLayout.Sample var5x = var3.sample((double)var3x, (double)var4x);
         AlpineLayout.Sample var6x = var1.edit(var2, var3x, var4x, var5x);
         return var6x == null ? var5x : var6x;
      };

      for (int var27 = var2.z0 & -16; var27 < var2.z0 + var2.h; var27 += 16) {
         for (int var28 = var2.x0 & -16; var28 < var2.x0 + var2.w; var28 += 16) {
            AlpineFallsDressing.dress(var0, var25, var4, var2, var28, var27, var0.getRandom());
         }
      }

      return var5;
   }

   private static final class WorldGround implements AlpineFalls.Natural {
      private final ServerLevel level;
      private final Map<Long, AlpineLayout.Sample> cache = new HashMap<>();

      WorldGround(ServerLevel var1) {
         this.level = var1;
      }

      @Override
      public AlpineLayout.Sample sample(double var1, double var3) {
         int var5 = (int)Math.floor(var1);
         int var6 = (int)Math.floor(var3);
         long var7 = (long)var5 << 32 ^ (long)var6 & 4294967295L;
         AlpineLayout.Sample var9 = this.cache.get(var7);
         if (var9 != null) {
            return var9;
         } else {
            this.level.getChunk(var5 >> 4, var6 >> 4);
            int var10 = this.level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var5, var6) - 1;
            MutableBlockPos var11 = new MutableBlockPos(var5, var10, var6);
            int var12 = Integer.MIN_VALUE;

            while (var10 > this.level.getMinBuildHeight() + 1) {
               var11.setY(var10);
               BlockState var13 = this.level.getBlockState(var11);
               if (!var13.getFluidState().isEmpty() && var13.getFluidState().is(FluidTags.WATER)) {
                  if (var12 == Integer.MIN_VALUE) {
                     var12 = var10;
                  }

                  var10--;
               } else {
                  if (!soft(var13)) {
                     break;
                  }

                  var10--;
               }
            }

            boolean var15 = this.level.getBiome(var11).is(BiomeTags.IS_OCEAN);
            AlpineLayout.Sample var14 = new AlpineLayout.Sample(
               (double)var10 + 0.5, var12, (double)var10 + 0.5, 9999.0, 0.0, 0.62, 0.55, 2000.0, 0.0, var15 ? 18 : 1, 0
            );
            this.cache.put(var7, var14);
            return var14;
         }
      }

      private static boolean soft(BlockState var0) {
         return var0.isAir()
            || var0.is(BlockTags.LOGS)
            || var0.is(BlockTags.LEAVES)
            || var0.is(BlockTags.REPLACEABLE)
            || var0.is(BlockTags.FLOWERS)
            || var0.is(BlockTags.SAPLINGS)
            || var0.is(Blocks.SNOW)
            || var0.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
      }
   }
}
