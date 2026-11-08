package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable starting values for the grounded Phantom hunter, not finalized lore balance. */
public final class PhantomEndermanConfig {
    public static final double DEFAULT_HEALTH = 36.0;
    public static final double DEFAULT_ATTACK_DAMAGE = 6.0;
    public static final double DEFAULT_MOVEMENT_SPEED = .30;
    public static final double DEFAULT_FOLLOW_RANGE = 32.0;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 80);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_ATTACK_DAMAGE, 1, 16);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_MOVEMENT_SPEED, .1, .5);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_FOLLOW_RANGE, 8, 64);
    private static final ModConfigSpec.IntValue MELEE = B.comment("Brain cooldown ticks between individual native melee attempts.")
            .defineInRange("meleeCooldownTicks", 22, 16, 80);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("memoryTicks", 120, 40, 400);
    private static final ModConfigSpec.DoubleValue REAR_MULTIPLIER = B.defineInRange("backstabMultiplier", 1.5, 1, 2.5);
    private static final ModConfigSpec.DoubleValue REAR_DOT = B.comment("Target facing dot target-to-hunter; negative values require a rear attack.")
            .defineInRange("rearDotThreshold", -.5, -.95, -.05);
    private static final ModConfigSpec.IntValue REAR_COOLDOWN = B.defineInRange("backstabBonusCooldownTicks", 60, 20, 600);
    private static final ModConfigSpec.DoubleValue STALKING_SPEED = B.defineInRange("stalkingSpeed", .75, .25, 1.1);
    private static final ModConfigSpec.IntValue DAY_DECISION = B.defineInRange("dayTacticalIntervalTicks", 20, 10, 100);
    private static final ModConfigSpec.IntValue NIGHT_DECISION = B.defineInRange("nightTacticalIntervalTicks", 12, 5, 100);
    private static final ModConfigSpec.IntValue DAY_TELEPORT = B.defineInRange("dayTeleportCooldownTicks", 120, 60, 1200);
    private static final ModConfigSpec.IntValue NIGHT_TELEPORT = B.defineInRange("nightTeleportCooldownTicks", 80, 40, 1200);
    private static final ModConfigSpec.IntValue STEP_WARMUP = B.defineInRange("stepWarmupTicks", 10, 5, 60);
    private static final ModConfigSpec.DoubleValue STEP_RANGE = B.defineInRange("stepActivationRange", 18.0, 6.0, 32.0);
    private static final ModConfigSpec.DoubleValue STEP_FLANK = B.defineInRange("stepFlankDistance", 3, 2.5, 5);
    private static final ModConfigSpec.DoubleValue NIGHT_SPEED = B.comment("Modest passive movement multiplier at night; never changes gravity or navigation type.")
            .defineInRange("nightMovementBonus", .10, 0, .30);
    private static final ModConfigSpec.IntValue CURSE_DELAY = B.defineInRange("curseDelayTicks", 40, 20, 120);
    private static final ModConfigSpec.IntValue DARKNESS_DURATION = B.defineInRange("darknessDurationTicks", 100, 20, 400);
    private static final ModConfigSpec.IntValue SLOW_DURATION = B.defineInRange("slownessDurationTicks", 80, 20, 400);
    private static final ModConfigSpec.IntValue SLOW_AMPLIFIER = B.defineInRange("slownessAmplifier", 0, 0, 1);
    private static final ModConfigSpec.IntValue CURSE_COOLDOWN = B.comment("Consumed when the delayed curse is queued, including cancelled sequences.")
            .defineInRange("curseCooldownTicks", 240, 100, 2400);
    public static final ModConfigSpec SPEC = B.build();
    private PhantomEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_ATTACK_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_MOVEMENT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_FOLLOW_RANGE; }
    public static int meleeCooldownTicks() { return SPEC.isLoaded() ? MELEE.get() : 22; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static double backstabMultiplier() { return SPEC.isLoaded() ? REAR_MULTIPLIER.get() : 1.5; }
    public static double rearDotThreshold() { return SPEC.isLoaded() ? REAR_DOT.get() : -.5; }
    public static int backstabCooldownTicks() { return SPEC.isLoaded() ? REAR_COOLDOWN.get() : 60; }
    public static double stalkingSpeedModifier() { return SPEC.isLoaded() ? STALKING_SPEED.get() : .75; }
    public static int decisionIntervalTicks(boolean night) { return night ? (SPEC.isLoaded() ? NIGHT_DECISION.get() : 12) : (SPEC.isLoaded() ? DAY_DECISION.get() : 20); }
    public static int approachCooldownTicks(boolean night) { return night ? (SPEC.isLoaded() ? NIGHT_TELEPORT.get() : 80) : (SPEC.isLoaded() ? DAY_TELEPORT.get() : 120); }
    public static int approachWarmupTicks() { return SPEC.isLoaded() ? STEP_WARMUP.get() : 10; }
    public static double approachRange() { return SPEC.isLoaded() ? STEP_RANGE.get() : 18; }
    public static double flankDistance() { return SPEC.isLoaded() ? STEP_FLANK.get() : 3; }
    public static double nightMovementBonus() { return SPEC.isLoaded() ? NIGHT_SPEED.get() : .10; }
    public static int curseDelayTicks() { return SPEC.isLoaded() ? CURSE_DELAY.get() : 40; }
    public static int darknessDurationTicks() { return SPEC.isLoaded() ? DARKNESS_DURATION.get() : 100; }
    public static int slownessDurationTicks() { return SPEC.isLoaded() ? SLOW_DURATION.get() : 80; }
    public static int slownessAmplifier() { return SPEC.isLoaded() ? SLOW_AMPLIFIER.get() : 0; }
    public static int curseCooldownTicks() { return SPEC.isLoaded() ? CURSE_COOLDOWN.get() : 240; }
}
