package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local, finite encounter coordination; species abilities keep their own tuning and priority. */
public final class GroupTacticsConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue ENABLED = B.define("enabled", true);
    private static final ModConfigSpec.IntValue MEMBERS = B.defineInRange("maximumMembers", 6, 2, 8);
    private static final ModConfigSpec.DoubleValue RADIUS = B.defineInRange("recruitmentRadius", 18.0, 6.0, 24.0);
    private static final ModConfigSpec.DoubleValue LEASH = B.defineInRange("encounterRadius", 40.0, 12.0, 64.0);
    private static final ModConfigSpec.IntValue LIFETIME = B
            .comment("Hard group lifetime. Sight and recruitment never extend this deadline.")
            .defineInRange("encounterTicks", 600, 100, 1200);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("observationTicks", 60, 20, 100);
    private static final ModConfigSpec.IntValue INTERVAL = B.defineInRange("coordinationIntervalTicks", 20, 10, 60);
    private static final ModConfigSpec.IntValue PATH_INTERVAL = B.defineInRange("pathIntervalTicks", 15, 10, 40);
    private static final ModConfigSpec.DoubleValue FLANK = B.defineInRange("flankWidth", 4.0, 2.0, 6.0);
    private static final ModConfigSpec.IntValue FLANK_TICKS = B.defineInRange("flankTicks", 80, 20, 160);
    private static final ModConfigSpec.DoubleValue LOW_HEALTH = B.defineInRange("retreatHealthFraction", .25, .1, .4);
    private static final ModConfigSpec.IntValue RETREAT = B
            .comment("A member may retreat once per encounter. Retreat never heals or grants resistance.")
            .defineInRange("retreatTicks", 60, 20, 100);
    private static final ModConfigSpec.IntValue REGROUP = B.defineInRange("regroupTicks", 40, 20, 80);
    private static final ModConfigSpec.IntValue REFORM = B.defineInRange("reformDelayTicks", 200, 100, 600);
    public static final ModConfigSpec SPEC = B.build();

    private GroupTacticsConfig() {}
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    public static int maximumMembers() { return SPEC.isLoaded() ? MEMBERS.get() : 6; }
    public static double recruitmentRadius() { return SPEC.isLoaded() ? RADIUS.get() : 18; }
    public static double encounterRadius() { return SPEC.isLoaded() ? LEASH.get() : 40; }
    public static int encounterTicks() { return SPEC.isLoaded() ? LIFETIME.get() : 600; }
    public static int observationTicks() { return SPEC.isLoaded() ? MEMORY.get() : 60; }
    public static int coordinationIntervalTicks() { return SPEC.isLoaded() ? INTERVAL.get() : 20; }
    public static int pathIntervalTicks() { return SPEC.isLoaded() ? PATH_INTERVAL.get() : 15; }
    public static double flankWidth() { return SPEC.isLoaded() ? FLANK.get() : 4; }
    public static int flankTicks() { return SPEC.isLoaded() ? FLANK_TICKS.get() : 80; }
    public static double retreatHealthFraction() { return SPEC.isLoaded() ? LOW_HEALTH.get() : .25; }
    public static int retreatTicks() { return SPEC.isLoaded() ? RETREAT.get() : 60; }
    public static int regroupTicks() { return SPEC.isLoaded() ? REGROUP.get() : 40; }
    public static int reformDelayTicks() { return SPEC.isLoaded() ? REFORM.get() : 200; }
}
