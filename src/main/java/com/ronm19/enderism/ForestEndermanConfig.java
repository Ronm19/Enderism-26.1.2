package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Forest #006 balance defaults; the concept defines the mechanics, while these values remain tunable. */
public final class ForestEndermanConfig {
    public static final double DEFAULT_HEALTH = 60.0;
    public static final double DEFAULT_ARMOR = 4.0;
    public static final double DEFAULT_ATTACK_DAMAGE = 8.0;
    public static final double DEFAULT_MOVEMENT_SPEED = 0.25;
    public static final double DEFAULT_FOLLOW_RANGE = 32.0;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    public static final int REVEAL_TICKS = 60;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 200.0);
    private static final ModConfigSpec.DoubleValue ARMOR = BUILDER.defineInRange("armor", DEFAULT_ARMOR, 0.0, 20.0);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("attackDamage", DEFAULT_ATTACK_DAMAGE, 1.0, 30.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_MOVEMENT_SPEED, 0.1, 0.5);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("followRange", DEFAULT_FOLLOW_RANGE, 8.0, 64.0);
    private static final ModConfigSpec.IntValue BURST_DURATION = BUILDER
            .comment("Nature's Stalker hunting burst. Its transient modifier is removed when the hunt or burst ends.")
            .defineInRange("burstDurationTicks", 60, 20, 200);
    private static final ModConfigSpec.IntValue BURST_COOLDOWN = BUILDER.defineInRange("burstCooldownTicks", 160, 80, 2400);
    private static final ModConfigSpec.DoubleValue BURST_MULTIPLIER = BUILDER
            .comment("Fractional speed increase during a hunting burst; 0.25 means +25%.")
            .defineInRange("burstMultiplier", 0.25, 0.05, 1.0);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER
            .comment("Rooted Pursuit retains the last visibly observed position and velocity, never live hidden coordinates.")
            .defineInRange("memoryTicks", 100, 40, 300);
    private static final ModConfigSpec.IntValue INTERCEPT_COOLDOWN = BUILDER.defineInRange("interceptCooldownTicks", 200, 100, 2400);
    private static final ModConfigSpec.IntValue VINES_WARMUP = BUILDER
            .comment("Entangling Vines warning. Breaking sight, leaving range, or damaging the caster interrupts it.")
            .defineInRange("vinesWarmupTicks", 20, 10, 100);
    private static final ModConfigSpec.IntValue VINES_COOLDOWN = BUILDER.defineInRange("vinesCooldownTicks", 200, 100, 2400);
    private static final ModConfigSpec.DoubleValue VINES_RANGE = BUILDER.defineInRange("vinesRange", 8.0, 3.0, 12.0);
    private static final ModConfigSpec.DoubleValue VINES_DAMAGE = BUILDER
            .comment("Base vine strike damage before the ordinary armor, shield, and damage-event rules.")
            .defineInRange("vinesDamage", 4.0, 1.0, 12.0);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ForestEndermanConfig() {}

    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double armor() { return SPEC.isLoaded() ? ARMOR.get() : DEFAULT_ARMOR; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_ATTACK_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_MOVEMENT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_FOLLOW_RANGE; }
    public static int burstDurationTicks() { return SPEC.isLoaded() ? BURST_DURATION.get() : 60; }
    public static int burstCooldownTicks() { return SPEC.isLoaded() ? BURST_COOLDOWN.get() : 160; }
    public static double burstMultiplier() { return SPEC.isLoaded() ? BURST_MULTIPLIER.get() : 0.25; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 100; }
    public static int interceptCooldownTicks() { return SPEC.isLoaded() ? INTERCEPT_COOLDOWN.get() : 200; }
    public static int vinesWarmupTicks() { return SPEC.isLoaded() ? VINES_WARMUP.get() : 20; }
    public static int vinesCooldownTicks() { return SPEC.isLoaded() ? VINES_COOLDOWN.get() : 200; }
    public static double vinesRange() { return SPEC.isLoaded() ? VINES_RANGE.get() : 8.0; }
    public static double vinesDamage() { return SPEC.isLoaded() ? VINES_DAMAGE.get() : 4.0; }
}
