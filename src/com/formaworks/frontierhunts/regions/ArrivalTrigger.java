package com.formaworks.frontierhunts.regions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * [regions] Pure decision logic for arrival cards (no Minecraft types, checked offline by tools/regions/harness).
 *
 * <p>The client feeds one sample a few times per second: the biome and region under the player, the horizontal position and
 * whether a card may be shown right now. A different biome becomes a <i>candidate</i>; it is accepted once the player has
 * stayed in it for {@link Tuning#dwell} ticks and travelled {@link Tuning#travel} blocks inside it (or simply stayed for
 * {@link Tuning#longDwell}), which filters border flicker and short detours. An accepted crossing fires a card unless:
 * <ul>
 * <li>the mode does not announce it (Off, or a biome-only change in Regions mode),</li>
 * <li>the biome was announced within the repeat window (a different region is always announced, except the same region
 * again within {@link Tuning#regionCooldown} (3 min), which stops ping-pong along a region border),</li>
 * <li>a card was shown less than {@link Tuning#gap} ticks ago or the caller reports suppression (menus, combat, aiming,
 * kill cam, ...). Then the crossing is held and shown as soon as that ends, or dropped silently after
 * {@link Tuning#maxHold} ticks so a stale card never appears long after the fact.</li>
 * </ul>
 */
public final class ArrivalTrigger {
   public enum Kind {
      REGION,
      BIOME
   }

   /** A card to show now. */
   public record Fire(Kind kind, String biome, String region) {
   }

   /** Timings in client ticks (20 per second) and distances in blocks. */
   public static final class Tuning {
      public int dwell = 60;
      public int longDwell = 200;
      public double travel = 16.0;
      public int gap = 240;
      public int regionCooldown = 3600;
      public int maxHold = 300;
   }

   public final Tuning tuning;
   private String biome;
   private String region;
   private String candBiome;
   private String candRegion;
   private long candSince;
   private double candTravel;
   private long qualifiedAt = -1L;
   private boolean hasLast;
   private double lastX;
   private double lastZ;
   private long lastFire = Long.MIN_VALUE / 4;
   private final Map<String, Long> biomeShown = new HashMap<>();
   private final Map<String, Long> regionShown = new HashMap<>();

   public ArrivalTrigger() {
      this(new Tuning());
   }

   public ArrivalTrigger(Tuning tuning) {
      this.tuning = tuning;
   }

   /** Forget the current place (dimension change, respawn into another world). {@code cooldowns} also clears the repeat memory. */
   public void reset(boolean cooldowns) {
      this.biome = null;
      this.region = null;
      this.clearCandidate();
      this.hasLast = false;
      if (cooldowns) {
         this.biomeShown.clear();
         this.regionShown.clear();
         this.lastFire = Long.MIN_VALUE / 4;
      }
   }

   public boolean seeded() {
      return this.biome != null;
   }

   /** Accept a place without a card (join, training grounds). {@code shown}: count it as announced (the join card showed it). */
   public void seed(String biome, String region, long tick, boolean shown) {
      this.biome = biome;
      this.region = region;
      this.clearCandidate();
      if (shown) {
         this.mark(Kind.REGION, biome, region, tick);
      }
   }

   /** Record a card the caller showed itself (join card, debug command). */
   public void mark(Kind kind, String biome, String region, long tick) {
      this.lastFire = tick;
      this.biomeShown.put(biome, tick);
      if (kind == Kind.REGION) {
         this.regionShown.put(region, tick);
      }
      this.prune(tick);
   }

   /**
    * One sample. Returns the card to show now, or null.
    *
    * @param suppressed     a card must not start right now (menu, combat, aiming, cinematic, another card)
    * @param showRegions    region crossings are announced
    * @param showBiomes     biome crossings inside a region are announced
    * @param biomeRepeat    the same biome is not announced again within this many ticks
    */
   public Fire update(long tick, String biome, String region, double x, double z, boolean suppressed, boolean showRegions, boolean showBiomes, long biomeRepeat) {
      double step = this.hasLast ? Math.hypot(x - this.lastX, z - this.lastZ) : 0.0;
      this.hasLast = true;
      this.lastX = x;
      this.lastZ = z;
      if (biome == null) {
         return null;
      }
      if (this.biome == null) {
         this.seed(biome, region, tick, false);
         return null;
      }
      if (biome.equals(this.biome)) {
         this.clearCandidate();
         return null;
      }
      if (!biome.equals(this.candBiome)) {
         this.candBiome = biome;
         this.candRegion = region;
         this.candSince = tick;
         this.candTravel = 0.0;
         this.qualifiedAt = -1L;
         return null;
      }
      this.candTravel += step;
      long held = tick - this.candSince;
      if (this.qualifiedAt < 0L) {
         boolean settled = held >= this.tuning.dwell && this.candTravel >= this.tuning.travel || held >= this.tuning.longDwell;
         if (!settled) {
            return null;
         }
         this.qualifiedAt = tick;
      }
      Kind kind = this.decide(tick, showRegions, showBiomes, biomeRepeat);
      if (kind == null) {
         this.accept();
         return null;
      }
      if (suppressed || tick - this.lastFire < this.tuning.gap) {
         if (tick - this.qualifiedAt > this.tuning.maxHold) {
            this.accept();
         }
         return null;
      }
      Fire fire = new Fire(kind, this.candBiome, this.candRegion);
      this.mark(kind, this.candBiome, this.candRegion, tick);
      this.accept();
      return fire;
   }

   private Kind decide(long tick, boolean showRegions, boolean showBiomes, long biomeRepeat) {
      boolean regionChange = !Objects.equals(this.candRegion, this.region);
      if (regionChange && showRegions && !recent(this.regionShown, this.candRegion, this.tuning.regionCooldown, tick)) {
         return Kind.REGION;
      }
      if (showBiomes && !recent(this.biomeShown, this.candBiome, biomeRepeat, tick)) {
         return Kind.BIOME;
      }
      return null;
   }

   private static boolean recent(Map<String, Long> shown, String key, long window, long tick) {
      Long at = shown.get(key);
      return at != null && tick - at < window && tick >= at;
   }

   private void accept() {
      this.biome = this.candBiome;
      this.region = this.candRegion;
      this.clearCandidate();
   }

   private void clearCandidate() {
      this.candBiome = null;
      this.candRegion = null;
      this.candTravel = 0.0;
      this.qualifiedAt = -1L;
   }

   private void prune(long tick) {
      if (this.biomeShown.size() + this.regionShown.size() > 256) {
         for (Map<String, Long> m : java.util.List.of(this.biomeShown, this.regionShown)) {
            for (Iterator<Map.Entry<String, Long>> it = m.entrySet().iterator(); it.hasNext(); ) {
               if (tick - it.next().getValue() > 36000L) {
                  it.remove();
               }
            }
         }
      }
   }

   // ----------------------------------------------------------------------------------------- debug / harness access
   public String biome() {
      return this.biome;
   }

   public String region() {
      return this.region;
   }

   public String candidate() {
      return this.candBiome;
   }

   public long lastFire() {
      return this.lastFire;
   }

   public String describe(long tick) {
      return "current=" + this.biome + " / " + this.region
         + (this.candBiome == null ? "" : "  candidate=" + this.candBiome + " held=" + (tick - this.candSince) + "t travel=" + Math.round(this.candTravel) + "m"
            + (this.qualifiedAt >= 0L ? " (waiting " + (tick - this.qualifiedAt) + "t)" : ""))
         + "  lastCard=" + (this.lastFire < 0L ? "never" : (tick - this.lastFire) / 20 + "s ago")
         + "  remembered=" + this.biomeShown.size() + " biomes";
   }
}
