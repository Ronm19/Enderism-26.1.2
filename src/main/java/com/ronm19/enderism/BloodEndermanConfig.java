package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial, adjustable tuning for Blood's hybrid combat and every-kill Regeneration. */
public final class BloodEndermanConfig {
    public static final double DEFAULT_HEALTH = 46.0;
    public static final double DEFAULT_SPEED = 0.33;
    public static final double DEFAULT_DAMAGE = 6.5;
    public static final double DEFAULT_RANGE = 42.0;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 120.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_SPEED, .16, .50);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("meleeDamage", DEFAULT_DAMAGE, 2.0, 18.0);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("detectionRange", DEFAULT_RANGE, 16.0, 64.0);
    private static final ModConfigSpec.IntValue MELEE_INTERVAL = BUILDER.defineInRange("meleeIntervalTicks", 20, 12, 60);
    private static final ModConfigSpec.DoubleValue LANCE_DAMAGE = BUILDER.defineInRange("lanceDamage", 6.0, 1.0, 18.0);
    private static final ModConfigSpec.DoubleValue LANCE_SPEED = BUILDER.defineInRange("lanceSpeed", 1.05, .35, 2.0);
    private static final ModConfigSpec.DoubleValue LANCE_RANGE = BUILDER.defineInRange("lanceRange", 24.0, 8.0, 40.0);
    private static final ModConfigSpec.DoubleValue LANCE_MINIMUM_RANGE = BUILDER.defineInRange("lanceMinimumRange", 3.0, 2.0, 8.0);
    private static final ModConfigSpec.IntValue LANCE_LIFETIME = BUILDER.defineInRange("lanceLifetimeTicks", 60, 20, 120);
    private static final ModConfigSpec.IntValue LANCE_WINDUP = BUILDER.defineInRange("lanceWindupTicks", 12, 8, 40);
    private static final ModConfigSpec.IntValue LANCE_RECOVERY = BUILDER.defineInRange("lanceRecoveryTicks", 8, 4, 30);
    private static final ModConfigSpec.IntValue LANCE_COOLDOWN = BUILDER.defineInRange("lanceCooldownTicks", 70, 35, 240);
    private static final ModConfigSpec.IntValue REGENERATION_DURATION = BUILDER
            .comment("Every credited kill renews the same finite vanilla effect; there is no kill cooldown or amplifier stacking.")
            .defineInRange("regenerationDurationTicks", 120, 40, 400);
    private static final ModConfigSpec.IntValue REGENERATION_AMPLIFIER = BUILDER.defineInRange("regenerationAmplifier", 0, 0, 2);
    private static final ModConfigSpec.DoubleValue TELEPORT_RANGE = BUILDER.defineInRange("teleportActivationRange", 28.0, 8.0, 48.0);
    private static final ModConfigSpec.DoubleValue TELEPORT_DISTANCE = BUILDER.defineInRange("teleportTargetDistance", 4.5, 3.0, 6.0);
    private static final ModConfigSpec.IntValue TELEPORT_COOLDOWN = BUILDER.defineInRange("teleportCooldownTicks", 80, 40, 400);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER.defineInRange("encounterMemoryTicks", 140, 40, 300);
    private static final ModConfigSpec.IntValue SENSOR_INTERVAL = BUILDER.defineInRange("sensorIntervalTicks", 4, 2, 20);
    private static final ModConfigSpec.IntValue DECISION_INTERVAL = BUILDER.defineInRange("decisionIntervalTicks", 4, 2, 20);
    private static final ModConfigSpec.IntValue PATH_INTERVAL = BUILDER.defineInRange("pathEvaluationIntervalTicks", 10, 5, 30);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private BloodEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE_INTERVAL.get() : 20; }
    public static double lanceDamage() { return SPEC.isLoaded() ? LANCE_DAMAGE.get() : 6.0; }
    public static double lanceSpeed() { return SPEC.isLoaded() ? LANCE_SPEED.get() : 1.05; }
    public static double lanceRange() { return SPEC.isLoaded() ? LANCE_RANGE.get() : 24.0; }
    public static double lanceMinimumRange() { return SPEC.isLoaded() ? LANCE_MINIMUM_RANGE.get() : 3.0; }
    public static int lanceLifetime() { return SPEC.isLoaded() ? LANCE_LIFETIME.get() : 60; }
    public static int lanceWindup() { return SPEC.isLoaded() ? LANCE_WINDUP.get() : 12; }
    public static int lanceRecovery() { return SPEC.isLoaded() ? LANCE_RECOVERY.get() : 8; }
    public static int lanceCooldown() { return SPEC.isLoaded() ? LANCE_COOLDOWN.get() : 70; }
    public static int regenerationDuration() { return SPEC.isLoaded() ? REGENERATION_DURATION.get() : 120; }
    public static int regenerationAmplifier() { return SPEC.isLoaded() ? REGENERATION_AMPLIFIER.get() : 0; }
    public static double teleportRange() { return SPEC.isLoaded() ? TELEPORT_RANGE.get() : 28.0; }
    public static double teleportDistance() { return SPEC.isLoaded() ? TELEPORT_DISTANCE.get() : 4.5; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT_COOLDOWN.get() : 80; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 140; }
    public static int sensorInterval() { return SPEC.isLoaded() ? SENSOR_INTERVAL.get() : 4; }
    public static int decisionInterval() { return SPEC.isLoaded() ? DECISION_INTERVAL.get() : 4; }
    public static int pathInterval() { return SPEC.isLoaded() ? PATH_INTERVAL.get() : 10; }
}
