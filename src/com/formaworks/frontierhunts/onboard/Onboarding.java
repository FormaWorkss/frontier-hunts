package com.formaworks.frontierhunts.onboard;

import com.formaworks.frontierhunts.academy.Academy;
import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.academy.TrainingStore;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.guide.FieldSchool;
import com.formaworks.frontierhunts.guide.FieldSchoolData;
import com.formaworks.frontierhunts.guide.GuideConfig;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.journal.Stat;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * [onboard] Server side of the Frontier Handbook: the first-join kit, which Handbook tasks a hunter has done (read from
 * the Hunter's Journal counters, the Field School, the Ranger Academy records and vanilla statistics), the journal
 * checklist entries the Handbook adds, academy course credit for Field School lessons, and the sync to the client.
 *
 * <p>Cost: one evaluation (a few map lookups, at most two 41-slot inventory scans) per online hunter every two seconds
 * while their path is unfinished, plus one right after they craft, smelt or place something.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class Onboarding {
   static final Logger LOG = LogUtils.getLogger();
   private static final Map<UUID, Integer> SENT = new HashMap<>();
   private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
   private static final Set<UUID> SOON = new HashSet<>();
   private static final Map<String, Item> ITEMS = new HashMap<>();

   private static final String[] TIP_ITEMS = {"field_point", "fixed_broadhead", "mechanical_broadhead", "cut_on_contact_broadhead", "judo_point",
      "flint_point", "obsidian_point", "bone_point", "tracer_broadhead", "tracer_field_point", "tracer_ice_broadhead"};
   private static final String[] TENTS = {"trail_dome_tent", "woodland_camp_tent", "canvas_wall_tent", "bell_tent", "family_cabin_tent", "pup_tent",
      "backpacker_dome_tent", "hunters_canvas_tent", "solo_ridge_tent"};
   private static final String[] COOKED = {"cooked_venison", "cooked_backstrap", "cooked_game", "cooked_wild_fowl", "cooked_bear_meat"};
   private static final String[] FURS = {"fur_hat", "buckskin_coat", "bear_fur_coat", "hide_robe", "buckskin_leggings", "fur_mukluks"};
   /** journal counters the Handbook owns (checklist entries registered in {@link Setup}) */
   static final String C_TABLE = "hb.table", C_ARROWS = "hb.arrows", C_BENCH = "hb.bench", C_TIPS = "hb.tips";

   private Onboarding() {
   }

   // ============================================================================================ setup (both sides)

   @EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLCommonSetupEvent e) {
         e.enqueueWork(() -> {
            try {
               // The Handbook's own steps and the Ranger Academy courses, in the journal's Field School category
               Checklist.register("hb_table", Checklist.Category.SCHOOL, C_TABLE, 1, 10, "frontierhunts:frontier_handbook");
               Checklist.register("hb_arrows", Checklist.Category.SCHOOL, C_ARROWS, 1, 15, "frontierhunts:field_arrow");
               Checklist.register("hb_bench", Checklist.Category.SCHOOL, C_BENCH, 1, 15, "frontierhunts:frontier_workbench"); // [benches]
               Checklist.register("hb_tips", Checklist.Category.SCHOOL, C_TIPS, 1, 15, "frontierhunts:field_point");
               for (Course c : Course.curriculum()) {
                  Checklist.register("academy_" + c.key, Checklist.Category.SCHOOL, "academy." + c.key, 1, 25, courseIcon(c));
               }
            } catch (RuntimeException ex) {
               LOG.warn("Frontier Hunts handbook: journal checklist entries not registered", ex);
            }
         });
      }
   }

   public static String courseIcon(Course c) {
      return switch (c) {
         case ARCHERY -> "frontierhunts:field_bow";
         case GLASSING -> "frontierhunts:binoculars";
         case STALK -> "frontierhunts:wind_checker";
         case RANGE -> "frontierhunts:ridgeline_rifle";
         case TRACKING -> "icon:blood";
         case DRESSING -> "frontierhunts:skinning_tool";
      };
   }

   // ============================================================================================ helpers

   private static Item item(String id) {
      return ITEMS.computeIfAbsent(id, k -> {
         ResourceLocation rl = ResourceLocation.tryParse(k.contains(":") ? k : "frontierhunts:" + k);
         return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
      });
   }

   private static int crafted(ServerStatsCounter st, String id) {
      Item i = item(id);
      return i == Items.AIR ? 0 : st.getValue(Stats.ITEM_CRAFTED.get(i));
   }

   private static int used(ServerStatsCounter st, String id) {
      Item i = item(id);
      return i == Items.AIR ? 0 : st.getValue(Stats.ITEM_USED.get(i));
   }

   private static int counter(ServerPlayer p, String key) {
      try {
         return JournalApi.counter(p, key);
      } catch (RuntimeException ex) {
         return 0;
      }
   }

   private static boolean lent(ItemStack st) {
      CustomData d = st.get(DataComponents.CUSTOM_DATA);
      return d != null && d.contains(Academy.LENT);
   }

   private static boolean carries(ServerPlayer p, String... ids) {
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack st = inv.getItem(i);
         if (st.isEmpty() || lent(st)) {
            continue;
         }
         for (String id : ids) {
            if (st.is(item(id))) {
               return true;
            }
         }
      }
      return false;
   }

   /** An arrow carried with a fitted (non-default) tip: field points, judo, tracers... */
   private static boolean fittedArrow(ServerPlayer p) {
      Inventory inv = p.getInventory();
      Item arrow = item("field_arrow"), primitive = item("primitive_arrow");
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack st = inv.getItem(i);
         if (st.isEmpty() || lent(st) || !st.is(arrow) && !st.is(primitive)) {
            continue;
         }
         CustomData d = st.get(DataComponents.CUSTOM_DATA);
         if (d != null && !d.copyTag().getString("arrow_tip").isEmpty()) {
            return true;
         }
      }
      return false;
   }

   private static boolean wearsFurs(ServerPlayer p) {
      for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack st = p.getItemBySlot(s);
         if (st.isEmpty()) {
            continue;
         }
         for (String id : FURS) {
            if (st.is(item(id))) {
               return true;
            }
         }
      }
      return false;
   }

   private static boolean passed(ServerPlayer p, Course c) {
      try {
         TrainingStore.Record r = TrainingStore.get(p.server).peek(p.getUUID(), c);
         return r != null && r.passes > 0;
      } catch (RuntimeException ex) {
         return false;
      }
   }

   static int passedCourses(ServerPlayer p) {
      int m = 0;
      for (Course c : Course.values()) {
         if (passed(p, c)) {
            m |= 1 << c.ordinal();
         }
      }
      return m;
   }

   /** Ticks the journal checklist entry behind a Handbook task the first time the task is seen done. */
   private static void credit(ServerPlayer p, String counter) {
      if (counter(p, counter) == 0) {
         try {
            JournalApi.count(p, counter, 1);
         } catch (RuntimeException ignored) {
         }
      }
   }

   // ============================================================================================ evaluation

   /** Done tasks (skipped ones not included). */
   static int evaluate(ServerPlayer p) {
      int m = 0;
      ServerStatsCounter st = p.getStats();
      boolean training = Academy.inTraining(p);
      // 1 crafting table
      if (counter(p, C_TABLE) > 0 || crafted(st, "minecraft:crafting_table") > 0 || used(st, "minecraft:crafting_table") > 0) {
         m |= Handbook.Task.TABLE.bit();
         credit(p, C_TABLE);
      }
      // 2 arrows; [onboard2] step 8: the Archery Range (or a real bow kill)
      if (counter(p, C_ARROWS) > 0 || crafted(st, "field_arrow") > 0 || crafted(st, "primitive_arrow") > 0) {
         m |= Handbook.Task.ARROWS.bit();
         credit(p, C_ARROWS);
      }
      if (passed(p, Course.ARCHERY) || counter(p, "academy." + Course.ARCHERY.key) > 0 || counter(p, Stat.BOW_KILLS) > 0) {
         m |= Handbook.Task.ARCHERY.bit();
      }
      // [benches] 1 the three benches (made, placed or used, each); 3 arrow tips
      if (counter(p, C_BENCH) > 0 || com.formaworks.frontierhunts.benches.BenchProgress.allThree(p, st)) {
         m |= Handbook.Task.BENCH.bit();
         credit(p, C_BENCH);
      }
      boolean tips = counter(p, C_TIPS) > 0;
      for (int i = 0; !tips && i < TIP_ITEMS.length; i++) {
         tips = crafted(st, TIP_ITEMS[i]) > 0;
      }
      if (tips || !training && fittedArrow(p)) {
         m |= Handbook.Task.TIPS.bit();
         credit(p, C_TIPS);
      }
      // 4-6 Field School lessons (all counted as done when the server turned Field School off)
      FieldSchoolData.Hunter h = FieldSchoolData.get(p.server).find(p.getUUID());
      // [1.1.5] a player who skipped the Field School ("I know the ropes") has its lessons counted done too, so the
      // Handbook moves on to the later steps instead of waiting forever on lessons that will never be credited
      boolean lessonsOff = !GuideConfig.enabled() || h != null && h.skipped;
      for (Handbook.Task t : Handbook.Task.values()) {
         if (t.lesson != null && (lessonsOff || h != null && t.lesson.done(h.done))) {
            m |= t.bit();
         }
      }
      // 6 cook and eat game
      if (counter(p, Stat.EATEN) > 0 || counter(p, "gear.cooked") > 0 || !training && carries(p, COOKED)) {
         m |= Handbook.Task.COOK.bit();
      }
      // [licence] 5 a hunting licence (nothing to do when the server turned regulations off); 6 a camp meal
      if (!com.formaworks.frontierhunts.licence.LicenceConfig.mode().on() || counter(p, "licence.licences") > 0) {
         m |= Handbook.Task.LICENCE.bit();
      }
      if (counter(p, "campcook.meals") > 0) {
         m |= Handbook.Task.CAMP_COOK.bit();
      }
      // 7 tent, fur clothing
      boolean tent = counter(p, "placed.tent") > 0;
      for (int i = 0; !tent && i < TENTS.length; i++) {
         tent = used(st, TENTS[i]) > 0;
      }
      if (tent) {
         m |= Handbook.Task.TENT.bit();
      }
      if (counter(p, Stat.SURV_FURS) > 0 || !training && wearsFurs(p)) {
         m |= Handbook.Task.FURS.bit();
      }
      // 8 a rifle, the first expedition report
      if (counter(p, "gear.rifle") > 0 || !training && carries(p, "ridgeline_rifle", "lever_rifle", "semi_auto_rifle")) {
         m |= Handbook.Task.RIFLE.bit();
      }
      if (counter(p, Stat.CAMPAIGN) > 0 || ExpeditionLedger.get(p.serverLevel()).hunter(p.getUUID()).stage > 0) {
         m |= Handbook.Task.REPORT.bit();
      }
      return m;
   }

   /** Sends the hunter's Handbook state (and asks the client to open the book when {@code open}). */
   public static void sync(ServerPlayer p, boolean open) {
      if (p == null || p.connection == null) {
         return;
      }
      try {
         int done = evaluate(p);
         int skipped = OnboardData.get(p.server).skipped(p.getUUID()) & ~done & ~Handbook.lessonMask();
         int flags = (open ? OnboardNetwork.F_OPEN : 0) | (GuideConfig.enabled() ? 0 : OnboardNetwork.F_LESSONS_OFF);
         SENT.put(p.getUUID(), done | skipped);
         OnboardNetwork.send(p, new OnboardNetwork.State(done, skipped, passedCourses(p), flags));
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts handbook: sync failed for {}", p.getGameProfile().getName(), ex);
      }
   }

   /** The Handbook item was used: fresh state, and the client opens the book. */
   public static void open(ServerPlayer p) {
      sync(p, true);
   }

   // ============================================================================================ hooks

   /**
    * [hook: FieldSchool.login] A new hunter's first-join kit: the Frontier Handbook, the Field Recurve Bow and three
    * arrows (configurable). The expedition campaign starts counting at once, without its old starting kit.
    */
   public static void giveStarterKit(ServerPlayer p) {
      int given = 0;
      for (String entry : GuideConfig.starterItems()) {
         if (given >= 16) {
            break;
         }
         int star = entry.indexOf('*');
         ResourceLocation id = ResourceLocation.tryParse((star < 0 ? entry : entry.substring(0, star)).trim());
         int count = 1;
         if (star >= 0) {
            try {
               count = Math.clamp(Integer.parseInt(entry.substring(star + 1).trim()), 1, 64);
            } catch (NumberFormatException ignored) {
            }
         }
         Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
         if (item != Items.AIR) {
            ExpeditionService.give(p, new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize())));
            given++;
         }
      }
      p.inventoryMenu.broadcastChanges();
      try {
         ExpeditionLedger ledger = ExpeditionLedger.get(p.serverLevel());
         ExpeditionLedger.Hunter hunter = ledger.hunter(p.getUUID());
         if (!hunter.starter) {
            hunter.starter = true; // Mara's campaign counts from day one; its gear is earned, not handed out
            ledger.setDirty();
         }
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts handbook: could not open the expedition campaign", ex);
      }
      SOON.add(p.getUUID());
   }

   /**
    * [hook: TrainingService.end] A Ranger Academy course was passed (the hunter is home again): it completes the
    * matching Field School lessons (and through them the Handbook), and its journal checklist entry.
    */
   public static void coursePassed(ServerPlayer p, Course c) {
      if (p == null || c == null) {
         return;
      }
      for (Lesson l : Handbook.lessonsFor(c)) {
         try {
            FieldSchool.complete(p, l, false);
         } catch (RuntimeException ex) {
            LOG.warn("Frontier Hunts handbook: lesson credit failed", ex);
         }
      }
      try {
         JournalApi.count(p, "academy." + c.key, 1);
      } catch (RuntimeException ignored) {
      }
      sync(p, false);
   }

   static void action(ServerPlayer p, byte action, byte arg) {
      long now = p.serverLevel().getGameTime();
      Long last = LAST_ACTION.get(p.getUUID());
      if (last != null && now >= last && now - last < 4L) {
         return;
      }
      LAST_ACTION.put(p.getUUID(), now);
      switch (action) {
         case OnboardNetwork.A_SYNC -> sync(p, false);
         case OnboardNetwork.A_SKIP, OnboardNetwork.A_UNSKIP -> {
            Handbook.Task t = Handbook.Task.byId(arg);
            if (t == null || t.lesson != null) {
               return; // lessons are skipped through the Field School (its own two-click confirm)
            }
            OnboardData d = OnboardData.get(p.server);
            int m = d.skipped(p.getUUID());
            d.setSkipped(p.getUUID(), action == OnboardNetwork.A_SKIP ? m | t.bit() : m & ~t.bit());
            sync(p, false);
         }
         default -> {
         }
      }
   }

   // ============================================================================================ events

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         SOON.add(p.getUUID());
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      UUID id = e.getEntity().getUUID();
      SENT.remove(id);
      LAST_ACTION.remove(id);
      SOON.remove(id);
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      SENT.clear();
      LAST_ACTION.clear();
      SOON.clear();
   }

   @SubscribeEvent
   public static void crafted(PlayerEvent.ItemCraftedEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         SOON.add(p.getUUID());
      }
   }

   @SubscribeEvent
   public static void smelted(PlayerEvent.ItemSmeltedEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         SOON.add(p.getUUID());
      }
   }

   @SubscribeEvent
   public static void placed(BlockEvent.EntityPlaceEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         SOON.add(p.getUUID());
      }
   }

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p.connection == null || p.isSpectator()) {
         return;
      }
      UUID id = p.getUUID();
      boolean soon = SOON.contains(id) && p.tickCount > 20;
      if (!soon && (p.tickCount + p.getId()) % 40 != 7) {
         return;
      }
      Integer sent = SENT.get(id);
      if (!soon && sent != null && sent == Handbook.ALL) {
         return; // the whole path is done: nothing left to look for
      }
      if (Academy.inTraining(p)) {
         return; // lent gear and scripted animals never count; the result is re-read once home
      }
      SOON.remove(id);
      try {
         int done = evaluate(p);
         int skipped = OnboardData.get(p.server).skipped(id) & ~done & ~Handbook.lessonMask();
         if (sent == null || sent != (done | skipped)) {
            sync(p, false);
         }
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts handbook: check failed", ex);
      }
   }

   // ============================================================================================ commands

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent e) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("handbook")
         .executes(ctx -> status(ctx.getSource()))
         .then(Commands.literal("open").executes(ctx -> {
            open(ctx.getSource().getPlayerOrException());
            return 1;
         }))
         .then(Commands.literal("unskip")
            .executes(ctx -> unskip(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
            .then(Commands.argument("targets", EntityArgument.players()).requires(s -> s.hasPermission(2))
               .executes(ctx -> unskip(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
      e.getDispatcher().register(Commands.literal("frontierhunts").then(root));
   }

   private static int status(CommandSourceStack src) throws CommandSyntaxException {
      ServerPlayer p = src.getPlayerOrException();
      int done = evaluate(p);
      int all = done | OnboardData.get(p.server).skipped(p.getUUID());
      Handbook.Task next = Handbook.next(all);
      Component line = next == null
         ? Component.translatable("onboard.frontierhunts.cmd.done")
         : Component.translatable("onboard.frontierhunts.cmd.status", Handbook.stepsDone(all), Handbook.STEPS, next.step,
            Component.translatable(next.lang("title")));
      src.sendSuccess(() -> line, false);
      return 1;
   }

   private static int unskip(CommandSourceStack src, Collection<ServerPlayer> targets) {
      for (ServerPlayer p : targets) {
         OnboardData.get(p.server).setSkipped(p.getUUID(), 0);
         sync(p, false);
      }
      int n = targets.size();
      src.sendSuccess(() -> Component.translatable("onboard.frontierhunts.cmd.unskip", n), true);
      return n;
   }
}
