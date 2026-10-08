package com.formaworks.frontierhunts.season;

/**
 * Seasonal deer, elk and moose coats. Real cervids wear a thin reddish summer coat and moult into a thick grey-brown
 * winter coat over August-October, and back in April-May. Each animal's own coat value (0..99, stored in its traits)
 * becomes its moult date, so a herd moults over weeks, not all at once; its individual shade/warmth variation is
 * unchanged. With seasons off, the coat is the old random trait (coat &lt; 55 = grey).
 */
public final class SeasonCoats {
    private SeasonCoats() {}

    public static boolean grey(int coat) {
        if (!SeasonState.enabled()) return coat < 55;
        return FoliageSeason.coatGrey(SeasonState.yearPos()) > (coat + 0.5F) / 100F;
    }
}
