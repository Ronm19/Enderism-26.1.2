package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Silver #009's adjustable initial tuning, rather than finalized lore values. */
public final class SilverEndermanConfig {
    public static final double DEFAULT_HEALTH = 40.0;
    public static final double DEFAULT_ATTACK_DAMAGE = 7.0;
    public static final double DEFAULT_MOVEMENT_SPEED = 0.32;
    public static final double DEFAULT_FOLLOW_RANGE = 36.0;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 80.0);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("attackDamage", DEFAULT_ATTACK_DAMAGE, 1.0, 16.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_MOVEMENT_SPEED, 0.1, 0.5);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("followRange", DEFAULT_FOLLOW_RANGE, 8.0, 64.0);
    private static final ModConfigSpec.IntValue MELEE_INTERVAL = BUILDER
            .comment("Ticks between individual native melee attempts; combination poses never add animation-tick damage.")
            .defineInRange("meleeCooldownTicks", 16, 12, 60);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER.defineInRange("memoryTicks", 120, 40, 400);
    private static final ModConfigSpec.IntValue STEP_WARMUP = BUILDER.defineInRange("stepWarmupTicks", 10, 5, 60);
    private static final ModConfigSpec.IntValue STEP_COOLDOWN = BUILDER
            .comment("Consumed when Phantom Step starts, including interrupted or unsuccessful attempts.")
            .defineInRange("stepCooldownTicks", 80, 40, 1200);
    private static final ModConfigSpec.DoubleValue STEP_RANGE = BUILDER.defineInRange("stepActivationRange", 18.0, 6.0, 32.0);
    private static final ModConfigSpec.DoubleValue STEP_FLANK = BUILDER.defineInRange("stepFlankDistance", 3.5, 2.5, 5.0);
    private static final ModConfigSpec.IntValue FRACTURE_HITS = BUILDER.defineInRange("fractureHitThreshold", 3, 2, 10);
    private static final ModConfigSpec.IntValue FRACTURE_WINDOW = BUILDER.defineInRange("fractureHitWindowTicks", 100, 40, 400);
    private static final ModConfigSpec.DoubleValue FRACTURE_REDUCTION = BUILDER
            .comment("Temporary fraction of effective armor removed. Never changes equipped items or durability.")
            .defineInRange("fractureReduction", 0.30, 0.05, 0.60);
    private static final ModConfigSpec.IntValue FRACTURE_DURATION = BUILDER.defineInRange("fractureDurationTicks", 120, 20, 400);
    private static final ModConfigSpec.IntValue FRACTURE_COOLDOWN = BUILDER.defineInRange("fractureCooldownTicks", 240, 100, 2400);
    private static final ModConfigSpec.IntValue FRACTURE_RECOVERY = BUILDER
            .comment("Global victim recovery after fracture ends, shared by all Silver Endermen.")
            .defineInRange("fractureRecoveryTicks", 100, 20, 1200);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private SilverEndermanConfig() {}

    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_ATTACK_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_MOVEMENT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_FOLLOW_RANGE; }
    public static int meleeCooldownTicks() { return SPEC.isLoaded() ? MELEE_INTERVAL.get() : 16; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int stepWarmupTicks() { return SPEC.isLoaded() ? STEP_WARMUP.get() : 10; }
    public static int stepCooldownTicks() { return SPEC.isLoaded() ? STEP_COOLDOWN.get() : 80; }
    public static double stepActivationRange() { return SPEC.isLoaded() ? STEP_RANGE.get() : 18.0; }
    public static double stepFlankDistance() { return SPEC.isLoaded() ? STEP_FLANK.get() : 3.5; }
    public static int fractureHitThreshold() { return SPEC.isLoaded() ? FRACTURE_HITS.get() : 3; }
    public static int fractureHitWindowTicks() { return SPEC.isLoaded() ? FRACTURE_WINDOW.get() : 100; }
    public static double fractureReduction() { return SPEC.isLoaded() ? FRACTURE_REDUCTION.get() : 0.30; }
    public static int fractureDurationTicks() { return SPEC.isLoaded() ? FRACTURE_DURATION.get() : 120; }
    public static int fractureCooldownTicks() { return SPEC.isLoaded() ? FRACTURE_COOLDOWN.get() : 240; }
    public static int fractureRecoveryTicks() { return SPEC.isLoaded() ? FRACTURE_RECOVERY.get() : 100; }
}
