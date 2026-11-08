package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial tuning for a patient hunter; every search and temporary advantage has a finite budget. */
public final class WhiteEndermanConfig {
    public static final double DEFAULT_HEALTH = 40, DEFAULT_DAMAGE = 8, DEFAULT_SPEED = .31, DEFAULT_RANGE = 40;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 75);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 3, 16);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .2, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 16, 48);
    private static final ModConfigSpec.IntValue OBSERVATION = B.defineInRange("observationTicks", 24, 10, 60);
    private static final ModConfigSpec.IntValue STALKING_DURATION = B.defineInRange("stalkingDurationTicks", 60, 20, 120);
    private static final ModConfigSpec.DoubleValue STALKING_SPEED = B.defineInRange("stalkingSpeedModifier", .8, .5, 1.1);
    private static final ModConfigSpec.IntValue DECISION_INTERVAL = B.defineInRange("decisionIntervalTicks", 10, 5, 30);
    private static final ModConfigSpec.DoubleValue FLANK_RADIUS = B.defineInRange("flankRadius", 4.0, 2.5, 7);
    private static final ModConfigSpec.IntValue FLANK_CANDIDATES = B.defineInRange("flankCandidates", 8, 3, 12);
    private static final ModConfigSpec.IntValue FLANK_RETRY = B.defineInRange("flankRetryTicks", 50, 30, 160);
    private static final ModConfigSpec.IntValue FLANK_DURATION = B.defineInRange("flankDurationTicks", 35, 15, 70);
    private static final ModConfigSpec.IntValue MARK_WINDUP = B.defineInRange("markWindupTicks", 6, 4, 16);
    private static final ModConfigSpec.IntValue MARK_INTERVAL = B.defineInRange("markAttackIntervalTicks", 28, 20, 80);
    private static final ModConfigSpec.IntValue MARK_DURATION = B.comment("An encounter-local priority mark, never a debuff or permission to read hidden coordinates.").defineInRange("markDurationTicks", 160, 60, 300);
    private static final ModConfigSpec.DoubleValue MARK_PRIORITY = B.defineInRange("markPriorityInfluence", 1.4, 1, 2);
    private static final ModConfigSpec.IntValue TRACKING_PREP = B.defineInRange("trackingPreparationTicks", 10, 5, 24);
    private static final ModConfigSpec.IntValue TRACKING_DURATION = B.defineInRange("trackingDurationTicks", 180, 80, 300);
    private static final ModConfigSpec.IntValue TRACKING_COOLDOWN = B.defineInRange("trackingCooldownTicks", 240, 160, 800);
    private static final ModConfigSpec.DoubleValue TRACKING_RADIUS = B.defineInRange("trackingRadius", 7.0, 3, 10);
    private static final ModConfigSpec.IntValue TRACKING_CANDIDATES = B.defineInRange("trackingCandidates", 6, 2, 10);
    private static final ModConfigSpec.IntValue TRACKING_REPOSITION = B.defineInRange("trackingRepositionTicks", 25, 15, 80);
    private static final ModConfigSpec.IntValue REACQUISITION = B.defineInRange("reacquisitionIntervalTicks", 10, 5, 30);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 140, 60, 260);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 160, 100, 600);
    private static final ModConfigSpec.DoubleValue TELEPORT_RANGE = B.defineInRange("teleportRange", 20.0, 8, 28);
    private static final ModConfigSpec.IntValue APEX_TELEPORT = B.defineInRange("apexTeleportCooldownTicks", 100, 60, 300);
    private static final ModConfigSpec.IntValue APEX_PREP = B.defineInRange("apexPreparationTicks", 18, 10, 40);
    private static final ModConfigSpec.IntValue APEX_MIN_ENCOUNTER = B.defineInRange("apexMinEncounterTicks", 90, 40, 240);
    private static final ModConfigSpec.IntValue APEX_DURATION = B.defineInRange("apexDurationTicks", 140, 60, 240);
    private static final ModConfigSpec.IntValue APEX_COOLDOWN = B.defineInRange("apexCooldownTicks", 500, 300, 1600);
    private static final ModConfigSpec.DoubleValue APEX_DAMAGE = B.defineInRange("apexDamageBonus", .2, 0, .4);
    private static final ModConfigSpec.DoubleValue APEX_SPEED = B.defineInRange("apexSpeedBonus", .1, 0, .2);
    private static final ModConfigSpec.IntValue APEX_INTERVAL = B.defineInRange("apexAttackIntervalTicks", 22, 20, 60);
    private static final ModConfigSpec.IntValue APEX_DECISION = B.defineInRange("apexDecisionIntervalTicks", 5, 3, 15);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 5, 3, 20);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 3, 1, 8);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 2, 1, 3);
    public static final ModConfigSpec SPEC = B.build();
    private WhiteEndermanConfig() { }
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int observationTicks() { return SPEC.isLoaded() ? OBSERVATION.get() : 24; }
    public static int stalkingDurationTicks() { return SPEC.isLoaded() ? STALKING_DURATION.get() : 60; }
    public static double stalkingSpeedModifier() { return SPEC.isLoaded() ? STALKING_SPEED.get() : .8; }
    public static int decisionIntervalTicks() { return SPEC.isLoaded() ? DECISION_INTERVAL.get() : 10; }
    public static double flankRadius() { return SPEC.isLoaded() ? FLANK_RADIUS.get() : 4; }
    public static int flankCandidates() { return SPEC.isLoaded() ? FLANK_CANDIDATES.get() : 8; }
    public static int flankRetryTicks() { return SPEC.isLoaded() ? FLANK_RETRY.get() : 50; }
    public static int flankDurationTicks() { return SPEC.isLoaded() ? FLANK_DURATION.get() : 35; }
    public static int markWindupTicks() { return SPEC.isLoaded() ? MARK_WINDUP.get() : 6; }
    public static int markAttackIntervalTicks() { return SPEC.isLoaded() ? MARK_INTERVAL.get() : 28; }
    public static int markDurationTicks() { return SPEC.isLoaded() ? MARK_DURATION.get() : 160; }
    public static double markPriorityInfluence() { return SPEC.isLoaded() ? MARK_PRIORITY.get() : 1.4; }
    public static int trackingPreparationTicks() { return SPEC.isLoaded() ? TRACKING_PREP.get() : 10; }
    public static int trackingDurationTicks() { return SPEC.isLoaded() ? TRACKING_DURATION.get() : 180; }
    public static int trackingCooldownTicks() { return SPEC.isLoaded() ? TRACKING_COOLDOWN.get() : 240; }
    public static double trackingRadius() { return SPEC.isLoaded() ? TRACKING_RADIUS.get() : 7; }
    public static int trackingCandidates() { return SPEC.isLoaded() ? TRACKING_CANDIDATES.get() : 6; }
    public static int trackingRepositionTicks() { return SPEC.isLoaded() ? TRACKING_REPOSITION.get() : 25; }
    public static int reacquisitionIntervalTicks() { return SPEC.isLoaded() ? REACQUISITION.get() : 10; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 140; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static double teleportRange() { return SPEC.isLoaded() ? TELEPORT_RANGE.get() : 20; }
    public static int apexTeleportCooldownTicks() { return Math.min(tacticalTeleportCooldownTicks(), SPEC.isLoaded() ? APEX_TELEPORT.get() : 100); }
    public static int apexPreparationTicks() { return SPEC.isLoaded() ? APEX_PREP.get() : 18; }
    public static int apexMinEncounterTicks() { return SPEC.isLoaded() ? APEX_MIN_ENCOUNTER.get() : 90; }
    public static int apexDurationTicks() { return SPEC.isLoaded() ? APEX_DURATION.get() : 140; }
    public static int apexCooldownTicks() { return SPEC.isLoaded() ? APEX_COOLDOWN.get() : 500; }
    public static double apexDamageBonus() { return SPEC.isLoaded() ? APEX_DAMAGE.get() : .2; }
    public static double apexSpeedBonus() { return SPEC.isLoaded() ? APEX_SPEED.get() : .1; }
    public static int apexAttackIntervalTicks() { return Math.min(markAttackIntervalTicks(), SPEC.isLoaded() ? APEX_INTERVAL.get() : 22); }
    public static int apexDecisionIntervalTicks() { return SPEC.isLoaded() ? APEX_DECISION.get() : 5; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 5; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 3; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 2; }
}
