package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EquipmentSounds {
   private static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, "frontierhunts");
   private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> EVENTS = new HashMap<>();
   static final List<Weapon> FAR_WEAPONS = List.of(
      Weapon.LEVER_RIFLE, Weapon.SEMI_AUTO_RIFLE, Weapon.PUMP_SHOTGUN, Weapon.DOUBLE_BARREL, Weapon.SEMI_AUTO_SHOTGUN, Weapon.REVOLVER, Weapon.FIELD_PISTOL
   );

   private static void add(String var0) {
      EVENTS.put(var0, REGISTRY.register(var0, () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(var0))));
   }

   public static SoundEvent action(Weapon var0, String var1) {
      return (SoundEvent)EVENTS.get(var0.id() + "_" + var1).get();
   }

   public static boolean layered(Weapon var0) {
      return FAR_WEAPONS.contains(var0);
   }

   public static SoundEvent casing(boolean var0) {
      return (SoundEvent)EVENTS.get(var0 ? "casing_hull" : "casing_brass").get();
   }

   public static SoundEvent predator() {
      return (SoundEvent)EVENTS.get("predator_call").get();
   }

   public static SoundEvent named(String var0) {
      DeferredHolder var1 = EVENTS.get(var0);
      return var1 == null ? null : (SoundEvent)var1.get();
   }

   public static void register(IEventBus var0) {
      REGISTRY.register(var0);
   }

   private EquipmentSounds() {
   }

   static {
      for (Weapon var3 : Weapon.values()) {
         if (!var3.bow) {
            for (String var5 : List.of("shot", "open", "load", "close", "dry")) {
               add(var3.id() + "_" + var5);
            }
         }
      }

      for (Weapon var7 : FAR_WEAPONS) {
         for (String var9 : List.of("shot_far", "shot_suppressed")) {
            add(var7.id() + "_" + var9);
         }
      }

      add("predator_call");
      add("casing_brass");
      add("casing_hull");
      add("grunt_tube");
      add("bleat_call");
      add("antler_rattle");
      add("wind_puff");
   }
}
