package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [survival] {@code /survival} - your status (anyone). Op: {@code /survival set <protein|fat|energy> <0-100>},
 * {@code /survival heat <-100..100>}, {@code /survival age <hours>} (ages the held food), {@code /survival difficulty
 * <off|light|balanced|hardcore>}, {@code /survival season} (yield outlook).
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class SurvivalCommands {
   private SurvivalCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      e.getDispatcher().register(
         Commands.literal("survival")
            .executes(SurvivalCommands::status)
            .then(Commands.literal("season").executes(SurvivalCommands::season))
            .then(
               Commands.literal("set")
                  .requires(s -> s.hasPermission(2))
                  .then(
                     Commands.argument("meter", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"protein", "fat", "energy", "all"}, b))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0F, 100F)).executes(SurvivalCommands::set))
                  )
            )
            .then(
               Commands.literal("heat")
                  .requires(s -> s.hasPermission(2))
                  .then(Commands.argument("value", FloatArgumentType.floatArg(-100F, 100F)).executes(c -> {
                     ServerPlayer p = c.getSource().getPlayerOrException();
                     SurvivalService.state(p).heat = FloatArgumentType.getFloat(c, "value");
                     SurvivalService.forceSync(p);
                     c.getSource().sendSuccess(() -> Component.literal("Body heat set to " + FloatArgumentType.getFloat(c, "value")), false);
                     return 1;
                  }))
            )
            .then(
               Commands.literal("age")
                  .requires(s -> s.hasPermission(2))
                  .then(Commands.argument("hours", IntegerArgumentType.integer(1, 24 * 60)).executes(SurvivalCommands::age))
            )
            .then(
               Commands.literal("difficulty")
                  .requires(s -> s.hasPermission(3))
                  .then(
                     Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"off", "light", "balanced", "hardcore"}, b))
                        .executes(SurvivalCommands::difficulty)
                  )
            )
      );
   }

   private static int status(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer p = c.getSource().getPlayerOrException();
      SurvivalApi.Status s = SurvivalApi.status(p);
      SurvivalApi.Stats t = SurvivalApi.stats(p);
      String text = String.format(
         Locale.ROOT,
         "Frontier Survival: %s%nProtein %.0f  Fat %.0f  Energy %.0f%s%nBody %.1f°C (heat %.0f)  Air feels %.1f°C  Clothing %.1f layers  Wet %.0f%%%s%s%nMeals %d (wild game %d, fish %d, farm %d)  Spoiled lost %d",
         s.difficulty().name().toLowerCase(Locale.ROOT),
         s.protein(),
         s.fat(),
         s.energy(),
         s.huntersVigor() ? "  - Hunter's Vigor" : "",
         s.coreTemperature(),
         s.bodyHeat(),
         s.feltTemperature(),
         s.insulation(),
         s.wetness() * 100F,
         s.sheltered() ? "  sheltered" : "",
         s.nearFire() ? "  by a fire" : "",
         t.meals(),
         t.gameMeals(),
         t.fishMeals(),
         t.farmMeals(),
         t.spoiledLost()
      );
      c.getSource().sendSuccess(() -> Component.literal(text), false);
      return 1;
   }

   private static int season(CommandContext<CommandSourceStack> c) {
      SurvivalApi.SeasonYield y = SurvivalApi.seasonYield(c.getSource().getLevel());
      SeasonClock.Season season = SeasonClock.season(c.getSource().getLevel());
      c.getSource().sendSuccess(() -> Component.translatable(y.langKey()), false);
      c.getSource().sendSuccess(
         () -> Component.literal(String.format(
               Locale.ROOT, "%s: game condition %.0f%%, meat x%.2f, a deer carries ~%d fat, %.0f%% of spawns turned away",
               season.title(), y.condition() * 100F, y.meatMultiplier(), y.typicalDeerFat(), y.spawnDenial() * 100F
            )),
         false
      );
      return 1;
   }

   private static int set(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer p = c.getSource().getPlayerOrException();
      PlayerSurvival st = SurvivalService.state(p);
      String m = StringArgumentType.getString(c, "meter");
      float v = FloatArgumentType.getFloat(c, "value");
      switch (m) {
         case "protein" -> st.protein = v;
         case "fat" -> st.fat = v;
         case "energy" -> st.energy = v;
         case "all" -> {
            st.protein = v;
            st.fat = v;
            st.energy = v;
         }
         default -> {
            c.getSource().sendFailure(Component.literal("Unknown meter: " + m));
            return 0;
         }
      }
      SurvivalService.forceSync(p);
      c.getSource().sendSuccess(() -> Component.literal("Set " + m + " to " + v), false);
      return 1;
   }

   private static int age(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer p = c.getSource().getPlayerOrException();
      ItemStack s = p.getMainHandItem();
      long now = p.serverLevel().getGameTime();
      if (Perishable.values(s, false) == null) {
         c.getSource().sendFailure(Component.literal("Hold a perishable food (and keep spoilage on)."));
         return 0;
      }
      Perishable.stamp(s, now, SurvivalMath.AMBIENT);
      Freshness f = Perishable.get(s);
      long ticks = IntegerArgumentType.getInteger(c, "hours") * 1000L;
      s.set(SurvivalContent.FRESHNESS.get(), f.withMade(Math.max(0L, f.made() - (long) (ticks / SurvivalMath.STORE_RATE[f.store()]))));
      c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Aged by %d in-game hours: %.0f%% spoiled",
         IntegerArgumentType.getInteger(c, "hours"), Perishable.spoil(s, now, false) * 100F)), false);
      return 1;
   }

   private static int difficulty(CommandContext<CommandSourceStack> c) {
      String m = StringArgumentType.getString(c, "mode").toUpperCase(Locale.ROOT);
      SurvivalConfig.Difficulty d;
      try {
         d = SurvivalConfig.Difficulty.valueOf(m);
      } catch (IllegalArgumentException ex) {
         c.getSource().sendFailure(Component.literal("off, light, balanced or hardcore"));
         return 0;
      }
      if (SurvivalConfig.DIFFICULTY == null) {
         return 0;
      }
      SurvivalConfig.DIFFICULTY.set(d);
      SurvivalConfig.DIFFICULTY.save();
      for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
         SurvivalService.forceSync(p);
      }
      c.getSource().sendSuccess(() -> Component.literal("Frontier Survival difficulty: " + d.name().toLowerCase(Locale.ROOT)), true);
      return 1;
   }
}
