package com.formaworks.frontierhunts.weather;

/**
 * Regional severe-weather events run by {@link WeatherDirector}.
 *
 * Each kind belongs to a family; one storm per family can cover a place at a time (a blizzard and a thunderstorm are
 * both "precipitation" and never overlap, but morning fog can sit under a passing wind storm).
 */
public enum WeatherKind {
    NONE("none", Family.NONE, 9999f),
    /** Wind-driven snow in snowy regions; visibility collapses to the configured minimum (default 5 blocks). */
    BLIZZARD("blizzard", Family.PRECIPITATION, 5f),
    /** Violent summer/spring thunderstorm with a heavy downpour. Needs vanilla thunder. */
    THUNDERSTORM("thunderstorm", Family.PRECIPITATION, 44f),
    /** A short, gusty band of very heavy rain (spring and fall lowlands). */
    SQUALL("squall", Family.PRECIPITATION, 60f),
    /** Orange-brown haze and blowing sand in deserts and badlands. */
    DUST_STORM("duststorm", Family.DRY, 13f),
    /** Thick valley / river / swamp fog, mostly mornings in fall and spring. */
    FOG("fog", Family.FOG, 48f), // [1.1.8] was 20: a soft morning haze, not a wall
    /** Winter fog with ice crystals in lowlands that are not snowy regions. */
    FREEZING_FOG("freezingfog", Family.FOG, 56f), // [1.1.8] was 30
    /** Dry gale with leaf flurries and dust, mostly fall. */
    WIND_STORM("windstorm", Family.WIND, 150f);

    public enum Family { NONE, PRECIPITATION, DRY, FOG, WIND }

    public static final WeatherKind[] VALUES = values();

    public final String id;
    public final Family family;
    /** Visibility at full severity in blocks (the blizzard value is replaced by the server config). */
    public final float peakVisibility;

    WeatherKind(String id, Family family, float peakVisibility) {
        this.id = id;
        this.family = family;
        this.peakVisibility = peakVisibility;
    }

    public boolean precipitation() { return family == Family.PRECIPITATION; }

    public String title() {
        return switch (this) {
            case NONE -> "Calm";
            case BLIZZARD -> "Blizzard";
            case THUNDERSTORM -> "Thunderstorm";
            case SQUALL -> "Rain squall";
            case DUST_STORM -> "Dust storm";
            case FOG -> "Fog";
            case FREEZING_FOG -> "Freezing fog";
            case WIND_STORM -> "Wind storm";
        };
    }

    public static WeatherKind byId(String id) {
        for (WeatherKind k : VALUES) if (k.id.equalsIgnoreCase(id) || k.name().equalsIgnoreCase(id)) return k;
        if ("dust".equalsIgnoreCase(id) || "dust_storm".equalsIgnoreCase(id)) return DUST_STORM;
        if ("wind".equalsIgnoreCase(id) || "wind_storm".equalsIgnoreCase(id)) return WIND_STORM;
        if ("freezing_fog".equalsIgnoreCase(id)) return FREEZING_FOG;
        if ("storm".equalsIgnoreCase(id)) return THUNDERSTORM;
        return null;
    }

    public static WeatherKind byOrdinal(int o) { return o >= 0 && o < VALUES.length ? VALUES[o] : NONE; }
}
