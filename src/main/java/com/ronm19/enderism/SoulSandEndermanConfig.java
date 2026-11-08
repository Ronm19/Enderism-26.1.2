package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Provisional tuning for common soul-valley hunters, with bounded actions and terrain advantages. */
public final class SoulSandEndermanConfig {
    public static final double DEFAULT_HEALTH = 38, DEFAULT_DAMAGE = 7, DEFAULT_SPEED = .3, DEFAULT_RANGE = 32;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 65);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 3, 14);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .2, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 16, 48);
    private static final ModConfigSpec.DoubleValue MASTERY = B.defineInRange("soulSandMovementMultiplier", 1.3, 1.1, 1.6);
    private static final ModConfigSpec.IntValue TERRAIN_UPDATE = B.defineInRange("terrainUpdateTicks", 1, 1, 5);
    private static final ModConfigSpec.IntValue DECISION = B.defineInRange("decisionIntervalTicks", 10, 5, 30);
    private static final ModConfigSpec.IntValue ROUTE_INTERVAL = B.defineInRange("movementDecisionTicks", 20, 10, 60);
    private static final ModConfigSpec.IntValue ROUTE_CANDIDATES = B.defineInRange("routeCandidates", 6, 2, 8);
    private static final ModConfigSpec.DoubleValue ROUTE_RADIUS = B.defineInRange("routePreferenceRadius", 4.0, 2, 6);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 60, 240);
    private static final ModConfigSpec.IntValue STRIKE_WINDUP = B.defineInRange("strikeWindupTicks", 6, 4, 16);
    private static final ModConfigSpec.IntValue STRIKE_INTERVAL = B.defineInRange("strikeIntervalTicks", 28, 20, 80);
    private static final ModConfigSpec.IntValue STRIKE_BURN = B.defineInRange("strikeBurnTicks", 80, 20, 160);
    private static final ModConfigSpec.DoubleValue BURN_DAMAGE = B.defineInRange("burnDamage", 1, .25, 2);
    private static final ModConfigSpec.IntValue BURN_INTERVAL = B.defineInRange("burnIntervalTicks", 20, 20, 60);
    private static final ModConfigSpec.DoubleValue ERUPTION_RANGE = B.defineInRange("eruptionCastRange", 8.0, 4, 12);
    private static final ModConfigSpec.DoubleValue ERUPTION_RADIUS = B.defineInRange("eruptionRadius", 3, 1.5, 4);
    private static final ModConfigSpec.DoubleValue ERUPTION_DAMAGE = B.defineInRange("eruptionDamage", 5.0, 1, 9);
    private static final ModConfigSpec.IntValue ERUPTION_BURN = B.defineInRange("eruptionBurnTicks", 40, 20, 100);
    private static final ModConfigSpec.DoubleValue ERUPTION_KNOCKBACK = B.defineInRange("eruptionKnockback", .25, 0, .5);
    private static final ModConfigSpec.IntValue ERUPTION_WARNING = B.defineInRange("eruptionWarningTicks", 18, 10, 40);
    private static final ModConfigSpec.IntValue ERUPTION_COOLDOWN = B.defineInRange("eruptionCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.IntValue ERUPTION_MIN = B.defineInRange("eruptionMinEncounterTicks", 40, 20, 160);
    private static final ModConfigSpec.DoubleValue DOMINION_RANGE = B.defineInRange("dominionCastRange", 8.0, 4, 12);
    private static final ModConfigSpec.DoubleValue DOMINION_RADIUS = B.defineInRange("dominionRadius", 5.0, 3, 7);
    private static final ModConfigSpec.IntValue DOMINION_DURATION = B.defineInRange("dominionDurationTicks", 140, 60, 240);
    private static final ModConfigSpec.IntValue DOMINION_WARNING = B.defineInRange("dominionWarningTicks", 26, 16, 50);
    private static final ModConfigSpec.IntValue DOMINION_COOLDOWN = B.defineInRange("dominionCooldownTicks", 500, 300, 1600);
    private static final ModConfigSpec.IntValue DOMINION_MIN = B.defineInRange("dominionMinEncounterTicks", 120, 60, 300);
    private static final ModConfigSpec.IntValue HAZARDS = B.defineInRange("dominionHazards", 10, 4, 16);
    private static final ModConfigSpec.DoubleValue DOMINION_DAMAGE = B.defineInRange("dominionDamage", 2, .5, 4);
    private static final ModConfigSpec.IntValue DOMINION_INTERVAL = B.defineInRange("dominionDamageIntervalTicks", 20, 20, 60);
    private static final ModConfigSpec.IntValue DOMINION_BURN = B.defineInRange("dominionBurnTicks", 40, 20, 100);
    private static final ModConfigSpec.IntValue DOMINION_SAMPLES = B.defineInRange("dominionSampleBudget", 40, 10, 64);
    private static final ModConfigSpec.IntValue AFFECTED = B.defineInRange("maximumAffectedTargets", 16, 4, 24);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 140, 80, 600);
    private static final ModConfigSpec.DoubleValue TELEPORT_RANGE = B.defineInRange("teleportRange", 20.0, 8, 28);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("recoveryTicks", 6, 3, 20);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 30, 10, 60);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 4, 2, 8);
    private static final ModConfigSpec.IntValue MIN_GROUP = B.defineInRange("naturalMinGroup", 1, 1, 2);
    private static final ModConfigSpec.IntValue MAX_GROUP = B.defineInRange("naturalMaxGroup", 2, 1, 3);
    public static final ModConfigSpec SPEC = B.build();
    private SoulSandEndermanConfig() { }
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double soulSandMovementMultiplier() { return SPEC.isLoaded() ? MASTERY.get() : 1.3; }
    public static int terrainUpdateTicks() { return SPEC.isLoaded() ? TERRAIN_UPDATE.get() : 1; }
    public static int decisionIntervalTicks() { return SPEC.isLoaded() ? DECISION.get() : 10; }
    public static int movementDecisionTicks() { return SPEC.isLoaded() ? ROUTE_INTERVAL.get() : 20; }
    public static int routeCandidates() { return SPEC.isLoaded() ? ROUTE_CANDIDATES.get() : 6; }
    public static double routePreferenceRadius() { return SPEC.isLoaded() ? ROUTE_RADIUS.get() : 4; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int strikeWindupTicks() { return SPEC.isLoaded() ? STRIKE_WINDUP.get() : 6; }
    public static int strikeIntervalTicks() { return SPEC.isLoaded() ? STRIKE_INTERVAL.get() : 28; }
    public static int strikeBurnTicks() { return SPEC.isLoaded() ? STRIKE_BURN.get() : 80; }
    public static float burnDamage() { return (float)(SPEC.isLoaded() ? BURN_DAMAGE.get() : 1); }
    public static int burnIntervalTicks() { return SPEC.isLoaded() ? BURN_INTERVAL.get() : 20; }
    public static double eruptionCastRange() { return SPEC.isLoaded() ? ERUPTION_RANGE.get() : 8; }
    public static double eruptionRadius() { return SPEC.isLoaded() ? ERUPTION_RADIUS.get() : 3; }
    public static float eruptionDamage() { return (float)(SPEC.isLoaded() ? ERUPTION_DAMAGE.get() : 5); }
    public static int eruptionBurnTicks() { return SPEC.isLoaded() ? ERUPTION_BURN.get() : 40; }
    public static double eruptionKnockback() { return SPEC.isLoaded() ? ERUPTION_KNOCKBACK.get() : .25; }
    public static int eruptionWarningTicks() { return SPEC.isLoaded() ? ERUPTION_WARNING.get() : 18; }
    public static int eruptionCooldownTicks() { return SPEC.isLoaded() ? ERUPTION_COOLDOWN.get() : 160; }
    public static int eruptionMinEncounterTicks() { return SPEC.isLoaded() ? ERUPTION_MIN.get() : 40; }
    public static double dominionCastRange() { return SPEC.isLoaded() ? DOMINION_RANGE.get() : 8; }
    public static double dominionRadius() { return SPEC.isLoaded() ? DOMINION_RADIUS.get() : 5; }
    public static int dominionDurationTicks() { return SPEC.isLoaded() ? DOMINION_DURATION.get() : 140; }
    public static int dominionWarningTicks() { return SPEC.isLoaded() ? DOMINION_WARNING.get() : 26; }
    public static int dominionCooldownTicks() { return SPEC.isLoaded() ? DOMINION_COOLDOWN.get() : 500; }
    public static int dominionMinEncounterTicks() { return SPEC.isLoaded() ? DOMINION_MIN.get() : 120; }
    public static int dominionHazards() { return SPEC.isLoaded() ? HAZARDS.get() : 10; }
    public static float dominionDamage() { return (float)(SPEC.isLoaded() ? DOMINION_DAMAGE.get() : 2); }
    public static int dominionDamageIntervalTicks() { return SPEC.isLoaded() ? DOMINION_INTERVAL.get() : 20; }
    public static int dominionBurnTicks() { return SPEC.isLoaded() ? DOMINION_BURN.get() : 40; }
    public static int dominionSampleBudget() { return SPEC.isLoaded() ? DOMINION_SAMPLES.get() : 40; }
    public static int maximumAffectedTargets() { return SPEC.isLoaded() ? AFFECTED.get() : 16; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 140; }
    public static double teleportRange() { return SPEC.isLoaded() ? TELEPORT_RANGE.get() : 20; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 6; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 30; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 4; }
    public static int naturalMinGroup() { return SPEC.isLoaded() ? MIN_GROUP.get() : 1; }
    public static int naturalMaxGroup() { return Math.max(naturalMinGroup(), SPEC.isLoaded() ? MAX_GROUP.get() : 2); }
}
