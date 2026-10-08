package com.formaworks.frontierstructures;

/** [villages] read access to a graded column for the offline harness (tools/villages) */
public final class VillageGroundDebug {
    private VillageGroundDebug() {}
    public static int kind(VillageGround.Col c) {return c.kind;}
    public static int y(VillageGround.Col c) {return c.y;}
    public static int lot(VillageGround.Col c) {return c.lot;}
}
