package com.formaworks.frontierhunts.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * [survival] Item data component {@code frontierhunts:freshness}: when a perishable food was made (game time, snapped so
 * meat from one hunt stacks), the store class it currently sits in (rate of spoiling, see {@link SurvivalMath#STORE_RATE})
 * and how it was preserved (raw, smoked, salt-cured).
 */
public record Freshness(long made, int store, int cure) {
   public static final Codec<Freshness> CODEC = RecordCodecBuilder.create(
      i -> i.group(
            Codec.LONG.fieldOf("made").forGetter(Freshness::made),
            Codec.INT.optionalFieldOf("store", 0).forGetter(Freshness::store),
            Codec.INT.optionalFieldOf("cure", 0).forGetter(Freshness::cure)
         )
         .apply(i, Freshness::new)
   );
   public static final StreamCodec<ByteBuf, Freshness> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_LONG, Freshness::made, ByteBufCodecs.VAR_INT, Freshness::store, ByteBufCodecs.VAR_INT, Freshness::cure, Freshness::new
   );

   public Freshness {
      made = Math.max(0L, made);
      store = SurvivalMath.clampStore(store);
      cure = Math.max(0, Math.min(SurvivalMath.CURE_SHELF.length - 1, cure));
   }

   public Freshness withMade(long m) {
      return new Freshness(m, this.store, this.cure);
   }
}
