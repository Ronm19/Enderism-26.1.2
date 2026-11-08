package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial balance for #021; these defaults are not fixed lore values. */
public final class VoidEndermanConfig {
    public static final double DEFAULT_HEALTH = 50, DEFAULT_DAMAGE = 8, DEFAULT_SPEED = .29, DEFAULT_RANGE = 40;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 24, 100);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 2, 16);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .16, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 12, 64);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 140, 40, 400);
    private static final ModConfigSpec.IntValue STRIKE_WINDUP = B.defineInRange("strikeWindupTicks", 8, 5, 30);
    private static final ModConfigSpec.IntValue STRIKE_INTERVAL = B.defineInRange("strikeIntervalTicks", 32, 24, 100);
    private static final ModConfigSpec.DoubleValue STRIKE_KNOCKBACK = B.defineInRange("strikeKnockback", .35, 0, .8);
    private static final ModConfigSpec.IntValue STEP_WINDUP = B.defineInRange("stepPreparationTicks", 10, 6, 40);
    private static final ModConfigSpec.IntValue STEP_COOLDOWN = B.defineInRange("stepCooldownTicks", 120, 60, 1200);
    private static final ModConfigSpec.DoubleValue STEP_RADIUS = B.defineInRange("stepRadius", 12.0, 6, 24);
    private static final ModConfigSpec.DoubleValue STEP_FLANK = B.defineInRange("stepFlankDistance", 3.0, 2.5, 5);
    private static final ModConfigSpec.IntValue PULL_WINDUP = B.defineInRange("pullTelegraphTicks", 24, 16, 60);
    private static final ModConfigSpec.IntValue PULL_COOLDOWN = B.defineInRange("pullCooldownTicks", 240, 120, 1800);
    private static final ModConfigSpec.DoubleValue PULL_RANGE = B.defineInRange("pullRange", 10.0, 5, 16);
    private static final ModConfigSpec.DoubleValue PULL_STRENGTH = B.defineInRange("pullStrength", .22, .08, .3);
    private static final ModConfigSpec.DoubleValue PULL_DISTANCE = B.comment("Conservative fully supported movement corridor checked before one horizontal impulse.")
            .defineInRange("pullSafetyDistance", 3.0, 2.5, 5);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 12, 8, 40);
    private static final ModConfigSpec.IntValue SAMPLE = B.defineInRange("safeGroundSampleIntervalTicks", 10, 4, 40);
    private static final ModConfigSpec.DoubleValue DETECTION_DEPTH = B.defineInRange("rescueDetectionDepth", 12.0, 6, 28);
    private static final ModConfigSpec.IntValue SEARCH_RADIUS = B.defineInRange("rescueSearchRadius", 24, 8, 48);
    private static final ModConfigSpec.IntValue RETRY = B.defineInRange("rescueRetryTicks", 10, 5, 40);
    private static final ModConfigSpec.IntValue SAFE_MEMORY = B.defineInRange("safeGroundMemoryTicks", 1200, 200, 12000);
    private static final ModConfigSpec.IntValue RESCUE_RECOVERY = B.defineInRange("rescueRecoveryTicks", 12, 6, 40);
    private static final ModConfigSpec.IntValue RESCUE_BURST = B.defineInRange("rescueMaxBurstAttempts", 4, 1, 8);
    private static final ModConfigSpec.IntValue RESCUE_BACKOFF = B.defineInRange("rescueBackoffTicks", 100, 40, 400);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 4, 1, 20);
    private static final ModConfigSpec.IntValue POPULATION_CAP = B.defineInRange("naturalPopulationCap", 2, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private VoidEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 140; }
    public static int strikeWindupTicks() { return SPEC.isLoaded() ? STRIKE_WINDUP.get() : 8; }
    public static int strikeIntervalTicks() { return SPEC.isLoaded() ? STRIKE_INTERVAL.get() : 32; }
    public static double strikeKnockback() { return SPEC.isLoaded() ? STRIKE_KNOCKBACK.get() : .35; }
    public static int stepPreparationTicks() { return SPEC.isLoaded() ? STEP_WINDUP.get() : 10; }
    public static int stepCooldownTicks() { return SPEC.isLoaded() ? STEP_COOLDOWN.get() : 120; }
    public static double stepRadius() { return SPEC.isLoaded() ? STEP_RADIUS.get() : 12; }
    public static double stepFlankDistance() { return SPEC.isLoaded() ? STEP_FLANK.get() : 3; }
    public static int pullTelegraphTicks() { return SPEC.isLoaded() ? PULL_WINDUP.get() : 24; }
    public static int pullCooldownTicks() { return SPEC.isLoaded() ? PULL_COOLDOWN.get() : 240; }
    public static double pullRange() { return SPEC.isLoaded() ? PULL_RANGE.get() : 10; }
    public static double pullStrength() { return SPEC.isLoaded() ? PULL_STRENGTH.get() : .22; }
    public static double pullSafetyDistance() { return SPEC.isLoaded() ? PULL_DISTANCE.get() : 3; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 12; }
    public static int safeGroundSampleIntervalTicks() { return SPEC.isLoaded() ? SAMPLE.get() : 10; }
    public static double rescueDetectionDepth() { return SPEC.isLoaded() ? DETECTION_DEPTH.get() : 12; }
    public static int rescueSearchRadius() { return SPEC.isLoaded() ? SEARCH_RADIUS.get() : 24; }
    public static int rescueRetryTicks() { return SPEC.isLoaded() ? RETRY.get() : 10; }
    public static int rescueMemoryTicks() { return SPEC.isLoaded() ? SAFE_MEMORY.get() : 1200; }
    public static int rescueRecoveryTicks() { return SPEC.isLoaded() ? RESCUE_RECOVERY.get() : 12; }
    public static int rescueMaxBurstAttempts() { return SPEC.isLoaded() ? RESCUE_BURST.get() : 4; }
    public static int rescueBackoffTicks() { return SPEC.isLoaded() ? RESCUE_BACKOFF.get() : 100; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 4; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION_CAP.get() : 2; }
}
