package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial tuning, not fixed lore, for Spectral's patient real-entity ambushes. */
public final class SpectralEndermanConfig {
    public static final double DEFAULT_HEALTH = 40.0;
    public static final double DEFAULT_SPEED = .29;
    public static final double DEFAULT_DAMAGE = 7.0;
    public static final double DEFAULT_RANGE = 36.0;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20.0, 100.0);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .16, .45);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("rendDamage", DEFAULT_DAMAGE, 2.0, 14.0);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("detectionRange", DEFAULT_RANGE, 16.0, 64.0);
    private static final ModConfigSpec.IntValue MELEE = B.defineInRange("meleeIntervalTicks", 24, 12, 60);
    private static final ModConfigSpec.IntValue OBSERVATION = B.defineInRange("observationTicks", 24, 8, 80);
    private static final ModConfigSpec.DoubleValue STALKING = B.defineInRange("stalkingDistance", 8.0, 4.0, 16.0);
    private static final ModConfigSpec.IntValue MANIFESTATION_PREP = B.defineInRange("manifestationPreparationTicks", 10, 6, 40);
    private static final ModConfigSpec.IntValue APPARITION_PREP = B.defineInRange("apparitionPreparationTicks", 14, 8, 50);
    private static final ModConfigSpec.IntValue TRANSITION = B.defineInRange("spectralTransitionTicks", 6, 4, 20);
    private static final ModConfigSpec.IntValue MATERIALIZATION = B.defineInRange("materializationTicks", 8, 6, 30);
    private static final ModConfigSpec.IntValue WINDOW = B.defineInRange("ambushOpportunityTicks", 20, 8, 60);
    private static final ModConfigSpec.DoubleValue MANIFESTATION_BONUS = B.defineInRange("manifestationBonusDamage", 2.0, 0.0, 8.0);
    private static final ModConfigSpec.DoubleValue APPARITION_BONUS = B.defineInRange("apparitionBonusDamage", 5.0, 1.0, 12.0);
    private static final ModConfigSpec.IntValue MANIFESTATION_COOLDOWN = B.defineInRange("manifestationCooldownTicks", 140, 80, 600);
    private static final ModConfigSpec.IntValue APPARITION_COOLDOWN = B.defineInRange("apparitionCooldownTicks", 360, 160, 1200);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("ambushRecoveryTicks", 14, 8, 40);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("teleportAttemptCooldownTicks", 50, 30, 200);
    private static final ModConfigSpec.DoubleValue TELEPORT_RANGE = B.defineInRange("teleportActivationRange", 24.0, 8.0, 40.0);
    private static final ModConfigSpec.DoubleValue RADIUS = B.defineInRange("ambushPositioningRadius", 1.25, 1.1, 1.4);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 40, 300);
    private static final ModConfigSpec.IntValue SENSOR = B.defineInRange("sensorIntervalTicks", 8, 4, 30);
    private static final ModConfigSpec.IntValue DECISION = B.defineInRange("decisionIntervalTicks", 8, 4, 30);
    private static final ModConfigSpec.IntValue SEARCH = B.defineInRange("failedPositionRetryTicks", 30, 20, 100);
    public static final ModConfigSpec SPEC = B.build();
    private SpectralEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE.get() : 24; }
    public static int observationTicks() { return SPEC.isLoaded() ? OBSERVATION.get() : 24; }
    public static double stalkingDistance() { return SPEC.isLoaded() ? STALKING.get() : 8.0; }
    public static int manifestationPreparation() { return SPEC.isLoaded() ? MANIFESTATION_PREP.get() : 10; }
    public static int apparitionPreparation() { return SPEC.isLoaded() ? APPARITION_PREP.get() : 14; }
    public static int transitionTicks() { return SPEC.isLoaded() ? TRANSITION.get() : 6; }
    public static int materializationTicks() { return SPEC.isLoaded() ? MATERIALIZATION.get() : 8; }
    public static int ambushWindowTicks() { return SPEC.isLoaded() ? WINDOW.get() : 20; }
    public static double manifestationBonus() { return SPEC.isLoaded() ? MANIFESTATION_BONUS.get() : 2.0; }
    public static double apparitionBonus() { return SPEC.isLoaded() ? APPARITION_BONUS.get() : 5.0; }
    public static int manifestationCooldown() { return SPEC.isLoaded() ? MANIFESTATION_COOLDOWN.get() : 140; }
    public static int apparitionCooldown() { return SPEC.isLoaded() ? APPARITION_COOLDOWN.get() : 360; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 14; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT.get() : 50; }
    public static double teleportRange() { return SPEC.isLoaded() ? TELEPORT_RANGE.get() : 24.0; }
    public static double positioningRadius() { return SPEC.isLoaded() ? RADIUS.get() : 1.25; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int sensorInterval() { return SPEC.isLoaded() ? SENSOR.get() : 8; }
    public static int decisionInterval() { return SPEC.isLoaded() ? DECISION.get() : 8; }
    public static int failedPositionRetry() { return SPEC.isLoaded() ? SEARCH.get() : 30; }
}
