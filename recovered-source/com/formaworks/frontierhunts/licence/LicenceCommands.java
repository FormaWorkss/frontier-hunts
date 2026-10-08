package com.formaworks.frontierhunts.licence;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [licence] {@code /frontierhunts licence}: your licence status. Ops: {@code licence mode <off|relaxed|strict>} (saves
 * the server config), {@code licence issue <targets> <what>} (issues valid permits for this season without paying:
 * licence, a tag kind, a stamp, or all), {@code licence reset <targets>} (forgets purchases, strikes and the log).
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class LicenceCommands {
   private LicenceCommands() {
   }

   private static List<String> issuable() {
      List<String> l = new ArrayList<>();
      l.add("licence");
      l.add("all");
      for (Regulations.TagKind k : Regulations.TagKind.values()) {
         l.add(k.item);
      }
      for (Regulations.Stamp s : Regulations.Stamp.values()) {
         l.add(s.item);
      }
      return l;
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      e.getDispatcher().register(Commands.literal("frontierhunts").then(Commands.literal("licence")
         .executes(c -> status(c, c.getSource().getPlayerOrException()))
         .then(Commands.literal("mode").requires(s -> s.hasPermission(2))
            .then(Commands.argument("mode", StringArgumentType.word())
               .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"off", "relaxed", "strict"}, b))
               .executes(LicenceCommands::mode)))
         .then(Commands.literal("issue").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .then(Commands.argument("what", StringArgumentType.word())
                  .suggests((c, b) -> SharedSuggestionProvider.suggest(issuable(), b))
                  .executes(LicenceCommands::issue))))
         .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players()).executes(LicenceCommands::reset)))));
   }

   private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
      int period = LicenceTime.period(p.level());
      LicenceStore.Hunter h = LicenceStore.get(p.server).hunter(p.getUUID());
      LicenceStore.Season s = h.season(period);
      boolean carried = Tagging.find(p, LicenceContent.LICENCE.get(), period) >= 0;
      String line = LicenceConfig.mode().title + " regulations · " + Regulations.periodTitle(period) + " (" + LicenceTime.daysLeft(p.level())
         + " days left) · licence " + (s.licence ? (carried ? "valid" : "issued, not carried") : "none") + " · filled " + s.filled + " · violations "
         + s.violations + " · strikes " + s.strikes + (s.suspended ? " · SUSPENDED" : "") + " · counter nearby: " + LicenceOffice.nearVendor(p);
      c.getSource().sendSuccess(() -> Component.literal(line), false);
      return 1;
   }

   private static int mode(CommandContext<CommandSourceStack> c) {
      String m = StringArgumentType.getString(c, "mode").toUpperCase(Locale.ROOT);
      LicenceConfig.Mode mode;
      try {
         mode = LicenceConfig.Mode.valueOf(m);
      } catch (IllegalArgumentException ex) {
         c.getSource().sendFailure(Component.literal("off, relaxed or strict"));
         return 0;
      }
      boolean ok = LicenceConfig.set(mode);
      c.getSource().sendSuccess(() -> Component.literal("Hunting regulations: " + mode.title + (ok ? " (saved)" : " (could not save the config)")), true);
      return 1;
   }

   private static int issue(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      String what = StringArgumentType.getString(c, "what");
      int n = 0;
      for (ServerPlayer p : targets) {
         int period = LicenceTime.period(p.level());
         LicenceStore store = LicenceStore.get(p.server);
         LicenceStore.Season s = store.hunter(p.getUUID()).season(period);
         if (what.equals("licence") || what.equals("all") || what.equals(Regulations.LICENCE_ITEM)) {
            s.licence = true;
            LicenceOffice.give(p, LicenceOffice.issue(p, LicenceContent.LICENCE.get(), period));
            n++;
         }
         for (Regulations.TagKind k : Regulations.TagKind.values()) {
            if (what.equals("all") || what.equals(k.item)) {
               s.tags[k.ordinal()]++;
               LicenceOffice.give(p, LicenceOffice.issue(p, LicenceContent.tag(k), period));
               n++;
            }
         }
         for (Regulations.Stamp st : Regulations.Stamp.values()) {
            if (what.equals("all") || what.equals(st.item)) {
               s.stamps |= 1 << st.ordinal();
               LicenceOffice.give(p, LicenceOffice.issue(p, LicenceContent.stamp(st), period));
               n++;
            }
         }
         store.setDirty();
      }
      int fn = n;
      c.getSource().sendSuccess(() -> Component.literal("Issued " + fn + " permit(s) for the current licence season."), true);
      return n;
   }

   private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      for (ServerPlayer p : targets) {
         LicenceStore.get(p.server).reset(p.getUUID());
      }
      c.getSource().sendSuccess(() -> Component.literal("Licence records reset for " + targets.size() + " hunter(s)."), true);
      return targets.size();
   }
}
