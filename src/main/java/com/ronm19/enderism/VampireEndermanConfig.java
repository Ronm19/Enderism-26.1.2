package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial balance for the three vampiric abilities and their bounded movement. */
public final class VampireEndermanConfig {
    public static final double DEFAULT_HEALTH = 42, DEFAULT_DAMAGE = 7.5, DEFAULT_SPEED = .32, DEFAULT_RANGE = 32;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 90);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 3, 18);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .2, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 16, 64);
    private static final ModConfigSpec.IntValue STRIKE_WINDUP = B.defineInRange("strikeWindupTicks", 6, 4, 16);
    private static final ModConfigSpec.IntValue STRIKE_INTERVAL = B.defineInRange("strikeIntervalTicks", 26, 20, 80);
    private static final ModConfigSpec.IntValue DRAIN_HITS = B.defineInRange("drainRequiredHits", 3, 2, 6);
    private static final ModConfigSpec.IntValue DRAIN_WINDOW = B.defineInRange("drainBuildupTicks", 120, 40, 300);
    private static final ModConfigSpec.DoubleValue DRAIN_RATIO = B.comment("On the threshold hit, convert only actual health removed by that physical strike. Absorption alone gives no progress or healing.").defineInRange("drainHealingRatio", .6, .1, 1);
    private static final ModConfigSpec.DoubleValue DRAIN_CAP = B.defineInRange("maxHealingPerDrain", 4.0, .5, 8);
    private static final ModConfigSpec.IntValue DASH_PREP = B.defineInRange("dashPreparationTicks", 3, 1, 8);
    private static final ModConfigSpec.DoubleValue DASH_DISTANCE = B.defineInRange("dashDistance", 4.5, 2, 7);
    private static final ModConfigSpec.DoubleValue DASH_SPEED = B.defineInRange("dashSpeed", .75, .4, 1.2);
    private static final ModConfigSpec.IntValue DASH_DURATION = B.defineInRange("dashDurationTicks", 6, 3, 12);
    private static final ModConfigSpec.IntValue DASH_COOLDOWN = B.defineInRange("dashCooldownTicks", 80, 40, 300);
    private static final ModConfigSpec.DoubleValue PROJECTILE_RADIUS = B.defineInRange("projectileAwarenessRadius", 12.0, 4, 16);
    private static final ModConfigSpec.IntValue EVASION_REACTION = B.defineInRange("evasionReactionTicks", 5, 3, 20);
    private static final ModConfigSpec.IntValue PROJECTILE_PREDICTION = B.defineInRange("projectilePredictionTicks", 8, 3, 12);
    private static final ModConfigSpec.DoubleValue JUMP_STRENGTH = B.defineInRange("highJumpStrength", .68, .55, .75);
    private static final ModConfigSpec.IntValue JUMP_COOLDOWN = B.defineInRange("highJumpCooldownTicks", 80, 40, 300);
    private static final ModConfigSpec.DoubleValue JUMP_DISTANCE = B.defineInRange("highJumpDistance", 3.0, 1.5, 4);
    private static final ModConfigSpec.IntValue FURY_PREP = B.defineInRange("furyPreparationTicks", 16, 8, 40);
    private static final ModConfigSpec.IntValue FURY_DURATION = B.defineInRange("furyDurationTicks", 120, 60, 240);
    private static final ModConfigSpec.IntValue FURY_COOLDOWN = B.defineInRange("furyCooldownTicks", 400, 240, 1600);
    private static final ModConfigSpec.DoubleValue FURY_DAMAGE = B.defineInRange("furyDamageBonus", .35, .1, .6);
    private static final ModConfigSpec.DoubleValue FURY_SPEED = B.defineInRange("furyMovementBonus", .12, 0, .25);
    private static final ModConfigSpec.IntValue FURY_INTERVAL = B.defineInRange("furyStrikeIntervalTicks", 20, 20, 60);
    private static final ModConfigSpec.IntValue FURY_DECISION = B.defineInRange("furyDecisionIntervalTicks", 5, 3, 15);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 40, 300);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 5, 3, 20);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 8, 1, 30);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 3, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private VampireEndermanConfig() { }
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int strikeWindupTicks() { return SPEC.isLoaded() ? STRIKE_WINDUP.get() : 6; }
    public static int strikeIntervalTicks() { return SPEC.isLoaded() ? STRIKE_INTERVAL.get() : 26; }
    public static int drainRequiredHits() { return SPEC.isLoaded() ? DRAIN_HITS.get() : 3; }
    public static int drainBuildupTicks() { return SPEC.isLoaded() ? DRAIN_WINDOW.get() : 120; }
    public static double drainHealingRatio() { return SPEC.isLoaded() ? DRAIN_RATIO.get() : .6; }
    public static double maxHealingPerDrain() { return SPEC.isLoaded() ? DRAIN_CAP.get() : 4; }
    public static int dashPreparationTicks() { return SPEC.isLoaded() ? DASH_PREP.get() : 3; }
    public static double dashDistance() { return SPEC.isLoaded() ? DASH_DISTANCE.get() : 4.5; }
    public static double dashSpeed() { return SPEC.isLoaded() ? DASH_SPEED.get() : .75; }
    public static int dashDurationTicks() { return SPEC.isLoaded() ? DASH_DURATION.get() : 6; }
    public static int dashCooldownTicks() { return SPEC.isLoaded() ? DASH_COOLDOWN.get() : 80; }
    public static double projectileAwarenessRadius() { return SPEC.isLoaded() ? PROJECTILE_RADIUS.get() : 12; }
    public static int evasionReactionTicks() { return SPEC.isLoaded() ? EVASION_REACTION.get() : 5; }
    public static int projectilePredictionTicks() { return SPEC.isLoaded() ? PROJECTILE_PREDICTION.get() : 8; }
    public static double highJumpStrength() { return SPEC.isLoaded() ? JUMP_STRENGTH.get() : .68; }
    public static int highJumpCooldownTicks() { return SPEC.isLoaded() ? JUMP_COOLDOWN.get() : 80; }
    public static double highJumpDistance() { return SPEC.isLoaded() ? JUMP_DISTANCE.get() : 3; }
    public static int furyPreparationTicks() { return SPEC.isLoaded() ? FURY_PREP.get() : 16; }
    public static int furyDurationTicks() { return SPEC.isLoaded() ? FURY_DURATION.get() : 120; }
    public static int furyCooldownTicks() { return SPEC.isLoaded() ? FURY_COOLDOWN.get() : 400; }
    public static double furyDamageBonus() { return SPEC.isLoaded() ? FURY_DAMAGE.get() : .35; }
    public static double furyMovementBonus() { return SPEC.isLoaded() ? FURY_SPEED.get() : .12; }
    public static int furyStrikeIntervalTicks() { return Math.min(strikeIntervalTicks(), SPEC.isLoaded() ? FURY_INTERVAL.get() : 20); }
    public static int furyDecisionIntervalTicks() { return SPEC.isLoaded() ? FURY_DECISION.get() : 5; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 5; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 8; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 3; }
}
