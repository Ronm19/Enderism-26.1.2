package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial encounter tuning; these defaults are adjustable, not final species balance. */
public final class EnderismConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue DETECTION_RANGE = BUILDER
            .comment("Maximum visible-player detection distance in blocks.")
            .defineInRange("detectionRange", 24.0, 4.0, 48.0);
    private static final ModConfigSpec.IntValue INTIMIDATION_DURATION = BUILDER
            .comment("Maximum stationary Hunter's Gaze duration, in server ticks.")
            .defineInRange("intimidationDurationTicks", 80, 20, 400);
    private static final ModConfigSpec.IntValue INTIMIDATION_COOLDOWN = BUILDER
            .comment("Minimum ticks between separate Hunter's Gaze encounters.")
            .defineInRange("intimidationCooldownTicks", 200, 40, 2400);
    private static final ModConfigSpec.DoubleValue MOVEMENT_TOLERANCE = BUILDER
            .comment("Observed displacement threshold in blocks; tolerates small network jitter.")
            .defineInRange("movementTolerance", 0.025, 0.005, 0.1);
    private static final ModConfigSpec.DoubleValue ROTATION_TOLERANCE = BUILDER
            .comment("Observed yaw or pitch threshold in degrees; does not read player inputs.")
            .defineInRange("rotationToleranceDegrees", 0.8, 0.1, 5.0);
    private static final ModConfigSpec.IntValue FORCED_MOVEMENT_GRACE = BUILDER
            .comment("Ticks to rebaseline position after identifiable forced movement or damage.")
            .defineInRange("forcedMovementGraceTicks", 10, 0, 40);
    private static final ModConfigSpec.IntValue CHARGE_DURATION = BUILDER
            .defineInRange("chargeDurationTicks", 30, 10, 120);
    private static final ModConfigSpec.DoubleValue CHARGE_SPEED = BUILDER
            .defineInRange("chargeSpeedModifier", 1.65, 1.0, 2.5);
    private static final ModConfigSpec.DoubleValue PURSUIT_SPEED = BUILDER
            .defineInRange("pursuitSpeedModifier", 1.3, 1.0, 2.0);
    private static final ModConfigSpec.IntValue MELEE_COOLDOWN = BUILDER
            .comment("Minimum ticks between Savage Hunter strikes, including state transitions.")
            .defineInRange("meleeCooldownTicks", 24, 20, 100);
    private static final ModConfigSpec.IntValue SEARCH_MEMORY = BUILDER
            .comment("Ticks to retain the last observed position after losing sight.")
            .defineInRange("searchMemoryTicks", 100, 20, 400);
    private static final ModConfigSpec.IntValue INTERCEPTION_COOLDOWN = BUILDER
            .defineInRange("interceptionCooldownTicks", 100, 40, 1200);
    private static final ModConfigSpec.DoubleValue INTERCEPTION_MIN_DISTANCE = BUILDER
            .defineInRange("interceptionMinDistance", 8.0, 4.0, 24.0);
    private static final ModConfigSpec.DoubleValue INTERCEPTION_LEAD = BUILDER
            .comment("Maximum lead in blocks along the target's currently observed horizontal movement.")
            .defineInRange("interceptionLeadDistance", 5.0, 2.0, 8.0);
    private static final ModConfigSpec.DoubleValue INTERCEPTION_MIN_SPEED = BUILDER
            .defineInRange("interceptionMinSpeed", 0.08, 0.02, 0.3);
    private static final ModConfigSpec.IntValue TELEPORT_ATTEMPT_COOLDOWN = BUILDER
            .comment("Shared minimum delay between attempted random or tactical teleports, including failures.")
            .defineInRange("teleportAttemptCooldownTicks", 20, 10, 200);

    private static final ModConfigSpec.BooleanValue SPAWN_FORCER_ENABLED = BUILDER
            .comment("Enable bounded fallback spawning when an eligible player has no matching Enderism mob nearby.")
            .define("spawnForcerEnabled", true);
    private static final ModConfigSpec.IntValue SPAWN_FORCER_DELAY = BUILDER
            .comment("Eligible in-game seconds without a matching mob before fallback spawning is attempted. Biome gaps pause the wait; each species' time restrictions reset it.")
            .defineInRange("spawnForcerDelaySeconds", 120, 10, 3600);
    private static final ModConfigSpec.IntValue SPAWN_FORCER_RETRY = BUILDER
            .comment("Seconds before retrying when terrain is unsafe or a spawn is denied. Habitat and night rules still apply.")
            .defineInRange("spawnForcerRetrySeconds", 10, 1, 300);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EnderismConfig() {
    }

    public static double detectionRange() { return DETECTION_RANGE.get(); }
    public static int intimidationDurationTicks() { return INTIMIDATION_DURATION.get(); }
    public static int intimidationCooldownTicks() { return INTIMIDATION_COOLDOWN.get(); }
    public static double movementTolerance() { return MOVEMENT_TOLERANCE.get(); }
    public static double rotationToleranceDegrees() { return ROTATION_TOLERANCE.get(); }
    public static int forcedMovementGraceTicks() { return FORCED_MOVEMENT_GRACE.get(); }
    public static int chargeDurationTicks() { return CHARGE_DURATION.get(); }
    public static double chargeSpeedModifier() { return CHARGE_SPEED.get(); }
    public static double pursuitSpeedModifier() { return PURSUIT_SPEED.get(); }
    public static int meleeCooldownTicks() { return MELEE_COOLDOWN.get(); }
    public static int searchMemoryTicks() { return SEARCH_MEMORY.get(); }
    public static int interceptionCooldownTicks() { return INTERCEPTION_COOLDOWN.get(); }
    public static double interceptionMinDistance() { return INTERCEPTION_MIN_DISTANCE.get(); }
    public static double interceptionLeadDistance() { return INTERCEPTION_LEAD.get(); }
    public static double interceptionMinSpeed() { return INTERCEPTION_MIN_SPEED.get(); }
    public static int teleportAttemptCooldownTicks() { return TELEPORT_ATTEMPT_COOLDOWN.get(); }
    public static boolean spawnForcerEnabled() { return SPAWN_FORCER_ENABLED.get(); }
    public static int spawnForcerDelaySeconds() { return SPAWN_FORCER_DELAY.get(); }
    public static int spawnForcerRetrySeconds() { return SPAWN_FORCER_RETRY.get(); }
}
