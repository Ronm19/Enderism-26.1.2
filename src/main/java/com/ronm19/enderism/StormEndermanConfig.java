package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Storm's three concept abilities, with adjustable initial balance values. */
public final class StormEndermanConfig {
    public static final double DEFAULT_FOLLOW_RANGE = 32.0;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue THUNDER_DAMAGE = BUILDER.defineInRange("thunderDamage", 10.0, 1.0, 30.0);
    private static final ModConfigSpec.DoubleValue THUNDER_RANGE = BUILDER.defineInRange("thunderRange", 16.0, 6.0, 24.0);
    private static final ModConfigSpec.IntValue THUNDER_WARNING = BUILDER.defineInRange("thunderWarningTicks", 20, 10, 80);
    private static final ModConfigSpec.IntValue THUNDER_COOLDOWN = BUILDER.defineInRange("thunderCooldownTicks", 80, 40, 1200);
    private static final ModConfigSpec.DoubleValue GUST_RANGE = BUILDER.defineInRange("gustRange", 6.0, 3.0, 10.0);
    private static final ModConfigSpec.DoubleValue GUST_STRENGTH = BUILDER.defineInRange("gustKnockback", 1.0, 0.2, 2.0);
    private static final ModConfigSpec.IntValue GUST_WARNING = BUILDER.defineInRange("gustWarningTicks", 10, 5, 40);
    private static final ModConfigSpec.IntValue GUST_COOLDOWN = BUILDER.defineInRange("gustCooldownTicks", 120, 60, 1200);
    private static final ModConfigSpec.IntValue GUST_SLOW = BUILDER.defineInRange("gustSlowTicks", 40, 10, 100);
    private static final ModConfigSpec.IntValue GUST_PROJECTILES = BUILDER.defineInRange("gustProjectileTicks", 20, 10, 60);
    private static final ModConfigSpec.IntValue TEMPEST_WARNING = BUILDER.defineInRange("tempestWarningTicks", 30, 15, 100);
    private static final ModConfigSpec.IntValue TEMPEST_DURATION = BUILDER.defineInRange("tempestDurationTicks", 160, 80, 400);
    private static final ModConfigSpec.IntValue TEMPEST_COOLDOWN = BUILDER.defineInRange("tempestCooldownTicks", 400, 200, 2400);
    private static final ModConfigSpec.DoubleValue TEMPEST_RADIUS = BUILDER.defineInRange("tempestRadius", 8.0, 4.0, 12.0);
    private static final ModConfigSpec.DoubleValue TEMPEST_RANGE = BUILDER.defineInRange("tempestRange", 20.0, 8.0, 28.0);
    private static final ModConfigSpec.DoubleValue TEMPEST_DAMAGE = BUILDER.defineInRange("tempestLightningDamage", 6.0, 1.0, 20.0);
    private static final ModConfigSpec.IntValue TEMPEST_INTERVAL = BUILDER.defineInRange("tempestStrikeIntervalTicks", 40, 30, 100);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private StormEndermanConfig() {}
    public static double thunderDamage() { return SPEC.isLoaded() ? THUNDER_DAMAGE.get() : 10.0; }
    public static double thunderRange() { return SPEC.isLoaded() ? THUNDER_RANGE.get() : 16.0; }
    public static int thunderWarningTicks() { return SPEC.isLoaded() ? THUNDER_WARNING.get() : 20; }
    public static int thunderCooldownTicks() { return SPEC.isLoaded() ? THUNDER_COOLDOWN.get() : 80; }
    public static double gustRange() { return SPEC.isLoaded() ? GUST_RANGE.get() : 6.0; }
    public static double gustKnockback() { return SPEC.isLoaded() ? GUST_STRENGTH.get() : 1.0; }
    public static int gustWarningTicks() { return SPEC.isLoaded() ? GUST_WARNING.get() : 10; }
    public static int gustCooldownTicks() { return SPEC.isLoaded() ? GUST_COOLDOWN.get() : 120; }
    public static int gustSlowTicks() { return SPEC.isLoaded() ? GUST_SLOW.get() : 40; }
    public static int gustProjectileTicks() { return SPEC.isLoaded() ? GUST_PROJECTILES.get() : 20; }
    public static int tempestWarningTicks() { return SPEC.isLoaded() ? TEMPEST_WARNING.get() : 30; }
    public static int tempestDurationTicks() { return SPEC.isLoaded() ? TEMPEST_DURATION.get() : 160; }
    public static int tempestCooldownTicks() { return SPEC.isLoaded() ? TEMPEST_COOLDOWN.get() : 400; }
    public static double tempestRadius() { return SPEC.isLoaded() ? TEMPEST_RADIUS.get() : 8.0; }
    public static double tempestRange() { return SPEC.isLoaded() ? TEMPEST_RANGE.get() : 20.0; }
    public static double tempestDamage() { return SPEC.isLoaded() ? TEMPEST_DAMAGE.get() : 6.0; }
    public static int tempestStrikeIntervalTicks() { return SPEC.isLoaded() ? TEMPEST_INTERVAL.get() : 40; }
}
