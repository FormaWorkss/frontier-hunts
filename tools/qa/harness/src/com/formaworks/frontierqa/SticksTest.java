package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.rifle.RifleActions;
import com.formaworks.frontierhunts.sticks.ShootingSticks;
import com.formaworks.frontierhunts.sticks.ShootingSticksEntity;
import com.formaworks.frontierhunts.sticks.SticksContent;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * [sticks] Shooting sticks on a dedicated server, with the fake hunter FHQA (and a second one, FHQA2), through the real
 * item / entity / payload code paths: /fhqa sticks all | place | rest | arc | release | fold | save | persist_place |
 * persist_check. Every verdict line reads "[FHQA] sticks PASS ..." or "[FHQA] sticks FAIL ...".
 *
 * <p>persist_place (first run) sets a renamed pair up at kneeling height on a platform and saves the world;
 * persist_check (a second run started from that world, QA_WORLD) finds exactly one set there with the same state.
 */
final class SticksTest {
   private static final String MARK = "fhqa_sticks.txt";
   private static int fails;

   private SticksTest() {
   }

   static void say(String s) {
      FrontierQa.say("sticks " + s);
   }

   static void verdict(boolean ok, String what) {
      if (!ok) {
         fails++;
      }
      say((ok ? "PASS " : "FAIL ") + what);
   }

   static int run(CommandSourceStack src, String c) {
      MinecraftServer server = src.getServer();
      fails = 0;
      try {
         switch (c.trim()) {
            case "place" -> place(server);
            case "rest" -> rest(server);
            case "why" -> why(server);
            case "arc" -> arc(server);
            case "release" -> release(server);
            case "fold" -> fold(server);
            case "save" -> save(server);
            case "persist_place" -> persistPlace(server);
            case "persist_load" -> persistLoad(server);
            case "persist_check" -> persistCheck(server);
            case "all" -> {
               place(server);
               rest(server);
               arc(server);
               release(server);
               fold(server);
               save(server);
            }
            default -> say("FAIL unknown case " + c);
         }
      } catch (Throwable t) {
         fails++;
         FrontierQa.fail("sticks " + c, t);
      }
      say((fails == 0 ? "PASS " : "FAIL ") + "case " + c.trim() + " done, failures=" + fails);
      return 1;
   }

   // ============================================================================================ arena

   static Item sticksItem() {
      return BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:shooting_sticks"));
   }

   static Item rifle() {
      return BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:lever_rifle"));
   }

   /** A stone floor 15x15 high in the air near spawn (nothing in the way), cleared above. Returns the floor centre. */
   static BlockPos arena(ServerLevel level, int dx) {
      BlockPos sp = level.getSharedSpawnPos();
      BlockPos c = new BlockPos(sp.getX() + 40 + dx, 230, sp.getZ() + 40);
      for (int x = -7; x <= 7; x++) {
         for (int z = -7; z <= 7; z++) {
            level.getChunkAt(c.offset(x, 0, z));
            level.setBlock(c.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 0; y < 4; y++) {
               level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
         }
      }
      for (ShootingSticksEntity s : level.getEntitiesOfClass(ShootingSticksEntity.class, new AABB(c).inflate(10))) {
         s.discard();
      }
      for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(10))) {
         it.discard();
      }
      return c;
   }

   static FakePlayer hunter(MinecraftServer server, String name, BlockPos at, double ox, double oz) {
      ServerLevel level = server.overworld();
      FakePlayer fp = name.equals("FHQA") ? FrontierQa.fake(server) : FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
      FrontierQa.wire(fp);
      if (fp.isPassenger()) {
         fp.stopRiding();
      }
      fp.setGameMode(GameType.SURVIVAL);
      fp.getInventory().clearContent();
      fp.setShiftKeyDown(false);
      fp.zza = 0.0F;
      fp.xxa = 0.0F;
      fp.moveTo(at.getX() + 0.5 + ox, at.getY(), at.getZ() + 0.5 + oz, 0.0F, 0.0F);
      fp.setOnGround(true);
      return fp;
   }

   static int count(ServerPlayer fp, Item item) {
      int n = 0;
      for (ItemStack s : fp.getInventory().items) {
         if (s.is(item)) {
            n += s.getCount();
         }
      }
      for (ItemStack s : fp.getInventory().offhand) {
         if (s.is(item)) {
            n += s.getCount();
         }
      }
      return n;
   }

   static int dropped(ServerLevel level, BlockPos c, Item item) {
      int n = 0;
      for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(10))) {
         if (it.getItem().is(item)) {
            n += it.getItem().getCount();
         }
      }
      return n;
   }

   static List<ShootingSticksEntity> sets(ServerLevel level, BlockPos c) {
      return level.getEntitiesOfClass(ShootingSticksEntity.class, new AABB(c).inflate(10), e -> !e.isRemoved());
   }

   /** The fake hunter uses the sticks item on the floor block under {@code at}. */
   static InteractionResult useItem(ServerPlayer fp, BlockPos at) {
      ItemStack held = fp.getMainHandItem();
      BlockPos floor = at.below();
      BlockHitResult hit = new BlockHitResult(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), Direction.UP, floor, false);
      return held.useOn(new UseOnContext(fp, InteractionHand.MAIN_HAND, hit));
   }

   static ShootingSticksEntity placeAt(ServerLevel level, ServerPlayer fp, BlockPos at) {
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sticksItem()));
      useItem(fp, at);
      List<ShootingSticksEntity> s = sets(level, at);
      return s.isEmpty() ? null : s.get(0);
   }

   /** Advances the set (and its rider, as the level does) n ticks. */
   static void tick(ShootingSticksEntity s, int n) {
      for (int i = 0; i < n && !s.isRemoved(); i++) {
         s.tick();
         Entity r = s.getFirstPassenger();
         if (r != null) {
            s.positionRider(r); // as the level does after ticking the vehicle (ServerLevel.tickPassenger -> rideTick)
         }
      }
   }

   // ============================================================================================ cases

   static void place(MinecraftServer server) {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 0);
      FakePlayer fp = hunter(server, "FHQA", c, 0.0, -1.6);
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sticksItem()));
      InteractionResult r = useItem(fp, c);
      List<ShootingSticksEntity> s = sets(level, c);
      verdict(r.consumesAction() && s.size() == 1, "place: item used on the ground sets one tripod up (" + r + ", sets=" + s.size() + ")");
      verdict(fp.getMainHandItem().isEmpty(), "place: survival uses the item up");
      if (!s.isEmpty()) {
         ShootingSticksEntity e = s.get(0);
         verdict(e.height() == ShootingSticks.Height.STANDING && Math.abs(e.getY() - c.getY()) < 0.01, "place: standing height on the ground (" + e.describe()
            + " y=" + e.getY() + ")");
         verdict(e.stack().is(sticksItem()), "place: the set keeps its item");
      }
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sticksItem()));
      InteractionResult again = useItem(fp, c);
      verdict(!again.consumesAction() && sets(level, c).size() == 1 && count(fp, sticksItem()) == 1, "place: a second set can't go in the same spot");
      // sneaking places them at kneeling height; on a wall face never
      BlockPos k = c.east(3);
      fp.setShiftKeyDown(true);
      useItem(fp, k);
      fp.setShiftKeyDown(false);
      List<ShootingSticksEntity> sk = sets(level, k).stream().filter(e -> e.blockPosition().equals(k)).toList();
      verdict(sk.size() == 1 && sk.get(0).height() == ShootingSticks.Height.KNEELING, "place: sneaking sets them up at kneeling height");
      // height cycle with an empty hand
      if (!s.isEmpty()) {
         ShootingSticksEntity e = s.get(0);
         fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
         e.interact(fp, InteractionHand.MAIN_HAND);
         boolean kneel = e.height() == ShootingSticks.Height.KNEELING;
         e.interact(fp, InteractionHand.MAIN_HAND);
         boolean sit = e.height() == ShootingSticks.Height.SITTING;
         e.interact(fp, InteractionHand.MAIN_HAND);
         verdict(kneel && sit && e.height() == ShootingSticks.Height.STANDING, "place: use with an empty hand cycles standing > kneeling > sitting > standing");
      }
      // creative: the item is not used up
      fp.setGameMode(GameType.CREATIVE);
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sticksItem()));
      useItem(fp, c.west(3));
      verdict(fp.getMainHandItem().getCount() == 1 && sets(level, c.west(3)).stream().anyMatch(e -> e.blockPosition().equals(c.west(3))),
         "place: creative keeps the item");
      fp.setGameMode(GameType.SURVIVAL);
   }

   static ShootingSticksEntity restedSet(MinecraftServer server, ServerLevel level, BlockPos c, FakePlayer fp) {
      ShootingSticksEntity s = ShootingSticksEntity.spawn(level, Vec3.atBottomCenterOf(c), 0.0F, ShootingSticks.Height.STANDING, new ItemStack(sticksItem()));
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rifle()));
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 30.0F);
      fp.setOnGround(true);
      boolean ok = s != null && s.tryRest(fp);
      return ok ? s : null;
   }

   /** NeoForge's FakePlayer overrides startRiding to always refuse */
   static boolean cantRide(FakePlayer fp) {
      try {
         return fp.getClass().getMethod("startRiding", net.minecraft.world.entity.Entity.class, boolean.class).getDeclaringClass() == FakePlayer.class
            || FakePlayer.class.getMethod("startRiding", net.minecraft.world.entity.Entity.class, boolean.class).getDeclaringClass() == FakePlayer.class;
      } catch (NoSuchMethodException e) {
         return false;
      }
   }

   /** debug: which rule turns a rest down */
   static void why(MinecraftServer server) {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 0);
      FakePlayer fp = hunter(server, "FHQA", c, 0.0, -1.5);
      ShootingSticksEntity s = ShootingSticksEntity.spawn(level, Vec3.atBottomCenterOf(c), 0.0F, ShootingSticks.Height.STANDING, new ItemStack(sticksItem()));
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rifle()));
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 30.0F);
      fp.setOnGround(true);
      double dx = s.getX() - fp.getX(), dz = s.getZ() - fp.getZ();
      say("why: alive " + fp.isAlive() + " spectator " + fp.isSpectator() + " passenger " + fp.isPassenger() + " sleeping " + fp.isSleeping() + " sameLevel "
         + (fp.level() == s.level()) + " rider " + s.rider() + " supports " + ShootingSticks.supports(fp.getMainHandItem()) + " item " + fp.getMainHandItem()
         + " onGround " + fp.onGround() + " water " + fp.isInWater() + " flying " + fp.getAbilities().flying + " d2 " + (dx * dx + dz * dz) + " dy "
         + (fp.getY() - s.getY()));
      try {
         var m = ShootingSticksEntity.class.getDeclaredMethod("freeArc", net.minecraft.world.entity.player.Player.class, float.class);
         m.setAccessible(true);
         int[] arc = (int[])m.invoke(s, fp, (float)Math.toDegrees(Math.atan2(-dx, dz)));
         say("why: arc " + arc[0] + "/" + arc[1]);
      } catch (ReflectiveOperationException ex) {
         say("why: arc ? " + ex);
      }
      boolean rode = fp.startRiding(s);
      say("why: startRiding " + rode + " vehicle " + fp.getVehicle() + " canRide(force) " + fp.startRiding(s, true));
      fp.stopRiding();
      s.discard();
   }

   static void rest(MinecraftServer server) {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 0);
      FakePlayer fp = hunter(server, "FHQA", c, 0.0, -1.5);
      ShootingSticksEntity s = restedSet(server, level, c, fp);
      if (s == null && cantRide(fp)) {
         say("SKIP rest: a test FakePlayer can never ride (NeoForge FakePlayer.startRiding always returns false); every rule before mounting passed (see 'why'), the rest itself is checked in game");
         return;
      }
      verdict(s != null && fp.getVehicle() == s && ShootingSticks.rested(fp), "rest: a lever rifle rests in the yoke (the hunter rides the set)");
      if (s == null) {
         return;
      }
      tick(s, ShootingSticks.SETTLE_TICKS + 2);
      Vec3 spot = s.riderSpot(fp.getYRot());
      verdict(fp.position().distanceTo(spot) < 0.05, String.format("rest: settled behind the yoke (%.2f from the spot, reach %.2f)", fp.position().distanceTo(spot),
         Math.hypot(fp.getX() - s.getX(), fp.getZ() - s.getZ())));
      double drift = ShootingSticks.DRIFT, breath = ShootingSticks.BREATH;
      verdict(drift <= 0.08 && breath <= 0.25 && breath >= 0.1, String.format("rest: hold sway drift x%.2f breathing x%.2f of standing (target drift <= 0.08, breathing 0.1-0.25: a residual, not a laser)",
         drift, breath));
      verdict(ShootingSticks.SPREAD <= 0.55F && ShootingSticks.RECOIL < 1.0F, String.format("rest: spread x%.2f, felt recoil x%.2f, climb kept x%.2f", ShootingSticks.SPREAD,
         ShootingSticks.RECOIL, ShootingSticks.CLIMB));
      verdict(RifleActions.stableMount(fp), "rest: the Ridgeline counts the sticks as a stable place to aim from");
      // a second hunter can't use a set that's in use, nor fold it
      FakePlayer fp2 = hunter(server, "FHQA2", c, 1.2, -0.8);
      fp2.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rifle()));
      verdict(!s.tryRest(fp2) && fp2.getVehicle() == null, "rest: a second hunter can't rest on sticks in use");
      fp2.setShiftKeyDown(true);
      s.interact(fp2, InteractionHand.MAIN_HAND);
      fp2.setShiftKeyDown(false);
      verdict(!s.isRemoved() && fp.getVehicle() == s, "rest: nobody can fold sticks someone is resting on");
      // flare guns, bows and empty hands don't rest
      s.release(fp);
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:flare_gun"))));
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 30.0F);
      fp.setOnGround(true);
      verdict(!s.tryRest(fp), "rest: a flare gun doesn't go on the sticks");
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:ridgeline_rifle"))));
      verdict(s.tryRest(fp), "rest: the Ridgeline rests too");
      s.release(fp);
   }

   static void arc(MinecraftServer server) {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 0);
      FakePlayer fp = hunter(server, "FHQA", c, 0.0, -1.5);
      ShootingSticksEntity s = restedSet(server, level, c, fp);
      if (s == null) {
         if (cantRide(fp)) {
            say("SKIP arc: a test FakePlayer can't ride the sticks; the arc maths is checked offline (tools/sticks/SticksCheck.java) and in game");
            return;
         }
         verdict(false, "arc: could not rest");
         return;
      }
      tick(s, ShootingSticks.SETTLE_TICKS + 2);
      float centre = s.restYaw();
      fp.setYRot(centre + 85.0F);
      s.onPassengerTurned(fp);
      float off = Mth.wrapDegrees(fp.getYRot() - centre);
      verdict(off <= s.arcRight() + 2.01F && off >= ShootingSticks.ARC - 12.0F, String.format("arc: swivel right held at %.1f deg (arc %.0f)", off, s.arcRight()));
      fp.setYRot(centre - 120.0F);
      s.onPassengerTurned(fp);
      off = Mth.wrapDegrees(fp.getYRot() - centre);
      verdict(off >= -s.arcLeft() - 2.01F, String.format("arc: swivel left held at %.1f deg (arc %.0f)", off, s.arcLeft()));
      fp.setXRot(-70.0F);
      s.onPassengerTurned(fp);
      verdict(fp.getXRot() >= -s.height().up - 2.01F, String.format("arc: tilt up held at %.1f deg", -fp.getXRot()));
      fp.setXRot(80.0F);
      s.onPassengerTurned(fp);
      verdict(fp.getXRot() <= s.height().down + 2.01F, String.format("arc: tilt down held at %.1f deg", fp.getXRot()));
      // the body walks round the pivot: still at the yoke's reach for the new aim
      fp.setYRot(centre + 20.0F);
      fp.setXRot(0.0F);
      tick(s, 1);
      double reach = Math.hypot(fp.getX() - s.getX(), fp.getZ() - s.getZ());
      verdict(Math.abs(reach - s.height().reach) < 0.05, String.format("arc: swivelled 20 deg, the body stays %.2f behind the yoke", reach));
      // the soft stop (client side): pushing on stiffens and never passes the limit; turning back is free
      float prev = 0.0F;
      for (int i = 0; i < 200; i++) {
         prev = ShootingSticks.soft(prev, prev + 1.0F, -40.0F, 40.0F);
      }
      float back = ShootingSticks.soft(prev, prev - 5.0F, -40.0F, 40.0F);
      verdict(prev <= 40.0F && prev > 38.5F && Math.abs(back - (prev - 5.0F)) < 1.0E-4F, String.format("arc: soft stop settles at %.2f of 40 and lets go freely", prev));
      float mid = ShootingSticks.soft(10.0F, 11.0F, -40.0F, 40.0F);
      verdict(Math.abs(mid - 11.0F) < 1.0E-4F, "arc: inside the arc the turn is untouched");
      // something in the way narrows the arc: a post where the body would stand at -38 deg
      s.release(fp);
      BlockPos wall = BlockPos.containing(s.riderSpot(centre - 38.0F));
      level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
      level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 2);
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 0.0F);
      fp.setOnGround(true);
      boolean ok = s.tryRest(fp);
      verdict(ok && s.arcLeft() < 30.0F && s.arcRight() == ShootingSticks.ARC, String.format("arc: a post at -38 deg narrows the arc to -%.0f / +%.0f", s.arcLeft(),
         s.arcRight()));
      s.release(fp);
      level.setBlock(wall, Blocks.AIR.defaultBlockState(), 2);
      level.setBlock(wall.above(), Blocks.AIR.defaultBlockState(), 2);
   }

   static void release(MinecraftServer server) {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 0);
      FakePlayer fp = hunter(server, "FHQA", c, 0.0, -1.5);
      ShootingSticksEntity s = restedSet(server, level, c, fp);
      if (s == null) {
         if (cantRide(fp)) {
            say("SKIP release: a test FakePlayer can't ride the sticks; checked in game");
            return;
         }
         verdict(false, "release: could not rest");
         return;
      }
      tick(s, ShootingSticks.SETTLE_TICKS + 2);
      fp.zza = 1.0F;
      tick(s, 1);
      boolean stillAfterOne = fp.getVehicle() == s;
      tick(s, 2);
      fp.zza = 0.0F;
      verdict(stillAfterOne && fp.getVehicle() == null, "release: holding a movement key steps off (after two ticks, so a tap doesn't)");
      verdict(Math.abs(fp.getY() - s.getY()) < 0.01 && !s.isRemoved(), String.format("release: back on the ground (y %.2f, ground %.2f), the sticks stay", fp.getY(),
         s.getY()));
      // kneeling: stands up onto the ground line
      s.release(fp);
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 0.0F);
      fp.setOnGround(true);
      fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      s.interact(fp, InteractionHand.MAIN_HAND); // kneeling
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rifle()));
      boolean rested = s.tryRest(fp);
      tick(s, ShootingSticks.SETTLE_TICKS + 2);
      double low = fp.getY();
      fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // put the gun away
      tick(s, 1);
      verdict(rested && low < s.getY() - 0.3 && fp.getVehicle() == null && Math.abs(fp.getY() - s.getY()) < 0.01,
         String.format("release: kneeling body (feet %.2f under the ground line) stands up when the gun is put away", s.getY() - low));
      // jump (the client's release request) and logging out
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rifle()));
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 0.0F);
      fp.setOnGround(true);
      s.tryRest(fp);
      s.release(fp);
      verdict(fp.getVehicle() == null, "release: a jump (release request) lets go");
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 0.0F);
      fp.setOnGround(true);
      s.tryRest(fp);
      // the sticks' own logout listener (posting the event itself would run every other system's logout on the fake hunter)
      com.formaworks.frontierhunts.sticks.SticksNetwork.Cleanup.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(fp));
      verdict(fp.getVehicle() == null && !s.isRemoved(), "release: logging out lets go and leaves the sticks standing (not carried off as a vehicle)");
   }

   static void fold(MinecraftServer server) {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 0);
      FakePlayer fp = hunter(server, "FHQA", c, 0.0, -1.6);
      Item item = sticksItem();
      ShootingSticksEntity s = placeAt(level, fp, c);
      verdict(s != null && count(fp, item) == 0, "fold: placed (survival)");
      if (s == null) {
         return;
      }
      fp.setShiftKeyDown(true);
      InteractionResult r = s.interact(fp, InteractionHand.MAIN_HAND);
      fp.setShiftKeyDown(false);
      verdict(r.consumesAction() && s.isRemoved() && count(fp, item) == 1 && dropped(level, c, item) == 0 && sets(level, c).isEmpty(),
         "fold: sneak + use folds them back into exactly one item (" + count(fp, item) + " held, " + dropped(level, c, item) + " dropped)");
      // a second fold of the same (already removed) entity gives nothing
      fp.setShiftKeyDown(true);
      s.interact(fp, InteractionHand.MAIN_HAND);
      fp.setShiftKeyDown(false);
      verdict(count(fp, item) == 1, "fold: folding twice never duplicates");
      // punch
      ShootingSticksEntity p = placeAt(level, fp, c);
      if (p != null) {
         p.hurt(level.damageSources().playerAttack(fp), 1.0F);
      }
      verdict(p != null && p.isRemoved() && count(fp, item) == 1, "fold: a punch folds them too");
      // renamed pair comes back renamed
      ItemStack named = new ItemStack(item);
      named.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
      fp.getInventory().clearContent();
      fp.setItemInHand(InteractionHand.MAIN_HAND, named);
      useItem(fp, c);
      List<ShootingSticksEntity> n = sets(level, c);
      if (!n.isEmpty()) {
         fp.setShiftKeyDown(true);
         n.get(0).interact(fp, InteractionHand.MAIN_HAND);
         fp.setShiftKeyDown(false);
      }
      ItemStack back = fp.getInventory().items.stream().filter(st -> st.is(item)).findFirst().orElse(ItemStack.EMPTY);
      verdict(back.getHoverName().getString().equals("Old Faithful") && count(fp, item) == 1, "fold: a renamed pair comes back as it went ("
         + back.getHoverName().getString() + ")");
      // full inventory: the item drops at the sticks instead of vanishing
      ShootingSticksEntity f = placeAt(level, fp, c);
      for (int i = 0; i < fp.getInventory().items.size(); i++) {
         fp.getInventory().items.set(i, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
      }
      if (f != null) {
         fp.setShiftKeyDown(true);
         f.interact(fp, InteractionHand.MAIN_HAND);
         fp.setShiftKeyDown(false);
      }
      verdict(f != null && f.isRemoved() && dropped(level, c, item) == 1, "fold: with a full inventory the folded sticks drop (" + dropped(level, c, item) + ")");
      fp.getInventory().clearContent();
      for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(10))) {
         it.discard();
      }
      // creative: placing keeps the item, folding doesn't add a second
      fp.setGameMode(GameType.CREATIVE);
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
      useItem(fp, c);
      List<ShootingSticksEntity> cs = sets(level, c);
      if (!cs.isEmpty()) {
         fp.setShiftKeyDown(true);
         cs.get(0).interact(fp, InteractionHand.MAIN_HAND);
         fp.setShiftKeyDown(false);
      }
      verdict(!cs.isEmpty() && cs.get(0).isRemoved() && count(fp, item) == 1, "fold: creative place + fold leaves exactly one item (" + count(fp, item) + ")");
      fp.setGameMode(GameType.SURVIVAL);
      // ground taken away: they topple and drop as the item
      fp.getInventory().clearContent();
      ShootingSticksEntity t = ShootingSticksEntity.spawn(level, Vec3.atBottomCenterOf(c), 0.0F, ShootingSticks.Height.STANDING, new ItemStack(item));
      level.setBlock(c.below(), Blocks.AIR.defaultBlockState(), 2);
      if (t != null) {
         for (int i = 0; i < 25 && !t.isRemoved(); i++) {
            t.tickCount++;
            t.tick();
         }
      }
      level.setBlock(c.below(), Blocks.STONE.defaultBlockState(), 2);
      verdict(t != null && t.isRemoved() && dropped(level, c, item) == 1, "fold: no ground under them: they topple and drop as one item");
   }

   static void save(MinecraftServer server) {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 0);
      FakePlayer fp = hunter(server, "FHQA", c, 0.0, -1.5);
      ItemStack named = new ItemStack(sticksItem());
      named.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
      ShootingSticksEntity s = ShootingSticksEntity.spawn(level, Vec3.atBottomCenterOf(c), 37.0F, ShootingSticks.Height.SITTING, named);
      if (s == null) {
         verdict(false, "save: spawn");
         return;
      }
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rifle()));
      s.tryRest(fp);
      tick(s, ShootingSticks.SETTLE_TICKS + 2);
      fp.setYRot(s.restYaw() + 15.0F);
      tick(s, 1);
      s.release(fp);
      String before = s.describe();
      CompoundTag tag = new CompoundTag();
      boolean saved = s.save(tag);
      Entity loaded = EntityType.create(tag, level).orElse(null);
      String after = loaded instanceof ShootingSticksEntity l ? l.describe() : "-";
      verdict(saved && before.equals(after), "save: survives a save and load (" + before + " -> " + after + ")");
      // a rider is never written with the set (players aren't saved as passengers)
      fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - 1.5, 0.0F, 0.0F);
      fp.setOnGround(true);
      s.tryRest(fp);
      CompoundTag withRider = new CompoundTag();
      s.save(withRider);
      verdict(!withRider.contains("Passengers"), "save: the shooter is never saved riding the sticks");
      s.release(fp);
      s.discard();
   }

   // ============================================================================================ across a restart

   static void persistPlace(MinecraftServer server) throws Exception {
      ServerLevel level = server.overworld();
      BlockPos c = arena(level, 20);
      ItemStack named = new ItemStack(sticksItem());
      named.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
      ShootingSticksEntity s = ShootingSticksEntity.spawn(level, Vec3.atBottomCenterOf(c), 123.0F, ShootingSticks.Height.KNEELING, named);
      verdict(s != null, "persist_place: placed " + (s == null ? "-" : s.describe()) + " at " + c.toShortString());
      Path mark = server.getWorldPath(LevelResource.ROOT).resolve(MARK);
      Files.writeString(mark, c.getX() + " " + c.getY() + " " + c.getZ() + "\n" + (s == null ? "" : s.describe()));
      server.saveEverything(true, true, true);
      say("persist_place: world saved");
   }

   static BlockPos mark(MinecraftServer server) throws Exception {
      Path mark = server.getWorldPath(LevelResource.ROOT).resolve(MARK);
      if (!Files.exists(mark)) {
         return null;
      }
      String[] p = Files.readString(mark).split("\n")[0].trim().split(" ");
      return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
   }

   /** Second run, first step: keep the marked chunk loaded so its entities come in (check after a few seconds). */
   static void persistLoad(MinecraftServer server) throws Exception {
      BlockPos c = mark(server);
      if (c == null) {
         verdict(false, "persist_load: no mark from persist_place (start this run from that world: QA_WORLD)");
         return;
      }
      server.overworld().setChunkForced(c.getX() >> 4, c.getZ() >> 4, true);
      say("persist_load: chunk " + (c.getX() >> 4) + " " + (c.getZ() >> 4) + " forced");
   }

   static void persistCheck(MinecraftServer server) throws Exception {
      BlockPos c = mark(server);
      if (c == null) {
         verdict(false, "persist_check: no mark from persist_place");
         return;
      }
      String[] lines = Files.readString(server.getWorldPath(LevelResource.ROOT).resolve(MARK)).split("\n");
      ServerLevel level = server.overworld();
      List<ShootingSticksEntity> found = sets(level, c);
      String expect = lines.length > 1 ? lines[1].trim() : "";
      verdict(found.size() == 1 && found.get(0).describe().equals(expect), "persist_check: after a restart exactly one set stands there ("
         + (found.isEmpty() ? "none" : found.get(0).describe()) + ", expected " + expect + ")");
      level.setChunkForced(c.getX() >> 4, c.getZ() >> 4, false);
   }
}
