package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Sand #008 initial balance choices; these values are tunable rather than established lore. */
public final class SandEndermanConfig {
    public static final double DEFAULT_HEALTH = 30.0;
    public static final double DEFAULT_ATTACK_DAMAGE = 6.0;
    public static final double DEFAULT_MOVEMENT_SPEED = 0.27;
    public static final double DEFAULT_FOLLOW_RANGE = 28.0;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 60.0);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("attackDamage", DEFAULT_ATTACK_DAMAGE, 1.0, 12.0);
    private static final ModConfigSpec.DoubleValue RABBIT_DAMAGE = BUILDER
            .comment("A small strike lets ordinary three-health rabbits survive the hit and receive weak Poison.")
            .defineInRange("rabbitStrikeDamage", 1.0, 0.5, 2.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_MOVEMENT_SPEED, 0.1, 0.5);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("followRange", DEFAULT_FOLLOW_RANGE, 8.0, 48.0);
    private static final ModConfigSpec.DoubleValue RABBIT_RANGE = BUILDER.defineInRange("rabbitDetectionRange", 12.0, 4.0, 24.0);
    private static final ModConfigSpec.IntValue MELEE_COOLDOWN = BUILDER.defineInRange("meleeCooldownTicks", 24, 20, 80);
    private static final ModConfigSpec.IntValue POISON_DURATION = BUILDER
            .comment("Vanilla Poison I duration; no custom damage amplification or area poisoning.")
            .defineInRange("poisonDurationTicks", 60, 20, 100);
    private static final ModConfigSpec.IntValue POISON_COOLDOWN = BUILDER.defineInRange("poisonCooldownTicks", 100, 40, 1200);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER.defineInRange("memoryTicks", 100, 40, 300);
    private static final ModConfigSpec.DoubleValue VEIL_RANGE = BUILDER.defineInRange("veilActivationRange", 16.0, 6.0, 28.0);
    private static final ModConfigSpec.IntValue VEIL_WARMUP = BUILDER.defineInRange("veilWarmupTicks", 20, 10, 100);
    private static final ModConfigSpec.DoubleValue VEIL_RADIUS = BUILDER.defineInRange("veilRadius", 8.0, 4.0, 16.0);
    private static final ModConfigSpec.IntValue VEIL_DURATION = BUILDER.defineInRange("veilDurationTicks", 160, 40, 400);
    private static final ModConfigSpec.IntValue VEIL_COOLDOWN = BUILDER.defineInRange("veilCooldownTicks", 280, 100, 2400);
    private static final ModConfigSpec.DoubleValue VEIL_INTENSITY = BUILDER
            .comment("Synchronized local dust/fog intensity. Particle settings still cap the client's total budget.")
            .defineInRange("veilIntensity", 1.0, 0.1, 1.0);
    private static final ModConfigSpec.IntValue HUNT_COOLDOWN = BUILDER.defineInRange("huntCooldownTicks", 100, 60, 1200);
    private static final ModConfigSpec.DoubleValue HUNT_RANGE = BUILDER.defineInRange("huntActivationRange", 16.0, 6.0, 24.0);
    private static final ModConfigSpec.DoubleValue HUNT_DISTANCE = BUILDER
            .comment("Desired flank distance; each attempt alternates sides and varies this by up to one block.")
            .defineInRange("huntFlankDistance", 5.0, 3.0, 7.0);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private SandEndermanConfig() {}

    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_ATTACK_DAMAGE; }
    public static double rabbitStrikeDamage() { return SPEC.isLoaded() ? RABBIT_DAMAGE.get() : 1.0; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_MOVEMENT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_FOLLOW_RANGE; }
    public static double rabbitDetectionRange() { return SPEC.isLoaded() ? RABBIT_RANGE.get() : 12.0; }
    public static int meleeCooldownTicks() { return SPEC.isLoaded() ? MELEE_COOLDOWN.get() : 24; }
    public static int poisonDurationTicks() { return SPEC.isLoaded() ? POISON_DURATION.get() : 60; }
    public static int poisonCooldownTicks() { return SPEC.isLoaded() ? POISON_COOLDOWN.get() : 100; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 100; }
    public static double veilActivationRange() { return SPEC.isLoaded() ? VEIL_RANGE.get() : 16.0; }
    public static int veilWarmupTicks() { return SPEC.isLoaded() ? VEIL_WARMUP.get() : 20; }
    public static double veilRadius() { return SPEC.isLoaded() ? VEIL_RADIUS.get() : 8.0; }
    public static int veilDurationTicks() { return SPEC.isLoaded() ? VEIL_DURATION.get() : 160; }
    public static int veilCooldownTicks() { return SPEC.isLoaded() ? VEIL_COOLDOWN.get() : 280; }
    public static double veilIntensity() { return SPEC.isLoaded() ? VEIL_INTENSITY.get() : 1.0; }
    public static int huntCooldownTicks() { return SPEC.isLoaded() ? HUNT_COOLDOWN.get() : 100; }
    public static double huntActivationRange() { return SPEC.isLoaded() ? HUNT_RANGE.get() : 16.0; }
    public static double huntFlankDistance() { return SPEC.isLoaded() ? HUNT_DISTANCE.get() : 5.0; }
}
