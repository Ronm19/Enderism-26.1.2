package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial values for Fire's three active abilities. */
public final class FireEndermanConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", 40.0, 10, 100);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", 7.0, 1, 20);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", .3, .1, .5);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", 32.0, 8, 64);
    private static final ModConfigSpec.IntValue MELEE = B.defineInRange("meleeIntervalTicks", 24, 16, 80);
    private static final ModConfigSpec.IntValue STRIKE_HUNGER = B.defineInRange("strikeHungerTicks", 80, 20, 200);
    private static final ModConfigSpec.IntValue HEX_WARNING = B.defineInRange("hexWarningTicks", 18, 10, 60);
    private static final ModConfigSpec.DoubleValue HEX_RANGE = B.defineInRange("hexRange", 8.0, 3, 16);
    private static final ModConfigSpec.IntValue HEX_HUNGER = B.defineInRange("hexHungerTicks", 180, 40, 400);
    private static final ModConfigSpec.IntValue HEX_AMPLIFIER = B.defineInRange("hexHungerAmplifier", 1, 0, 2);
    private static final ModConfigSpec.IntValue HEX_COOLDOWN = B.defineInRange("hexCooldownTicks", 220, 120, 1000);
    private static final ModConfigSpec.IntValue BURST_WARNING = B.defineInRange("burstWarningTicks", 20, 12, 60);
    private static final ModConfigSpec.DoubleValue BURST_RADIUS = B.defineInRange("burstRadius", 3.0, 2, 6);
    private static final ModConfigSpec.DoubleValue BURST_DAMAGE = B.defineInRange("burstDamage", 4.0, 1, 12);
    private static final ModConfigSpec.IntValue BURST_COOLDOWN = B.defineInRange("burstCooldownTicks", 360, 180, 1200);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("castRecoveryTicks", 6, 4, 30);
    public static final ModConfigSpec SPEC = B.build();
    public static final ModConfigSpec COMMON_SPEC = SPEC;
    private FireEndermanConfig() { }
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : 40; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : 7; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : .3; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : 32; }
    public static int meleeIntervalTicks() { return SPEC.isLoaded() ? MELEE.get() : 24; }
    public static int strikeHungerTicks() { return SPEC.isLoaded() ? STRIKE_HUNGER.get() : 80; }
    public static int hexWarningTicks() { return SPEC.isLoaded() ? HEX_WARNING.get() : 18; }
    public static double hexRange() { return SPEC.isLoaded() ? HEX_RANGE.get() : 8; }
    public static int hexHungerTicks() { return SPEC.isLoaded() ? HEX_HUNGER.get() : 180; }
    public static int hexHungerAmplifier() { return SPEC.isLoaded() ? HEX_AMPLIFIER.get() : 1; }
    public static int hexCooldownTicks() { return SPEC.isLoaded() ? HEX_COOLDOWN.get() : 220; }
    public static int burstWarningTicks() { return SPEC.isLoaded() ? BURST_WARNING.get() : 20; }
    public static double burstRadius() { return SPEC.isLoaded() ? BURST_RADIUS.get() : 3; }
    public static double burstDamage() { return SPEC.isLoaded() ? BURST_DAMAGE.get() : 4; }
    public static int burstCooldownTicks() { return SPEC.isLoaded() ? BURST_COOLDOWN.get() : 360; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 6; }
}
