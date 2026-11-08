package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable first-pass siege tuning; values are not fixed lore. */
public final class RavagerEndermanConfig {
    public static final double DEFAULT_HEALTH = 90, DEFAULT_SPEED = .25, DEFAULT_DAMAGE = 12, DEFAULT_RANGE = 32;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 30, 200);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .15, .40);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("wrathDamage", DEFAULT_DAMAGE, 4, 24);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 16, 64);
    private static final ModConfigSpec.DoubleValue MELEE_KNOCKBACK = B.defineInRange("wrathKnockback", .75, .1, 1.5);
    private static final ModConfigSpec.IntValue MELEE_INTERVAL = B.defineInRange("wrathIntervalTicks", 42, 28, 100);
    private static final ModConfigSpec.IntValue MELEE_WINDUP = B.defineInRange("wrathWindupTicks", 12, 8, 30);
    private static final ModConfigSpec.DoubleValue CHARGE_MIN = B.defineInRange("chargeMinimumRange", 5.0, 3, 10);
    private static final ModConfigSpec.DoubleValue CHARGE_MAX = B.defineInRange("chargeMaximumRange", 18.0, 8, 28);
    private static final ModConfigSpec.DoubleValue CHARGE_SPEED = B.defineInRange("chargeSpeed", .72, .4, 1.1);
    private static final ModConfigSpec.IntValue CHARGE_DURATION = B.defineInRange("chargeDurationTicks", 32, 16, 60);
    private static final ModConfigSpec.IntValue CHARGE_WINDUP = B.defineInRange("chargeWindupTicks", 20, 12, 50);
    private static final ModConfigSpec.DoubleValue CHARGE_DAMAGE = B.defineInRange("chargeDamage", 16.0, 6, 30);
    private static final ModConfigSpec.DoubleValue CHARGE_KNOCKBACK = B.defineInRange("chargeKnockback", 1.05, .2, 1.8);
    private static final ModConfigSpec.IntValue CHARGE_COOLDOWN = B.defineInRange("chargeCooldownTicks", 240, 100, 1200);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("recoveryTicks", 16, 8, 60);
    private static final ModConfigSpec.DoubleValue ROAR_RADIUS = B.defineInRange("roarRadius", 6.0, 3, 10);
    private static final ModConfigSpec.DoubleValue ROAR_KNOCKBACK = B.defineInRange("roarKnockback", .7, .1, 1.2);
    private static final ModConfigSpec.IntValue ROAR_DISRUPTION = B.defineInRange("roarSlownessTicks", 30, 10, 60);
    private static final ModConfigSpec.IntValue ROAR_WINDUP = B.defineInRange("roarWindupTicks", 20, 12, 50);
    private static final ModConfigSpec.IntValue ROAR_COOLDOWN = B.defineInRange("roarCooldownTicks", 400, 160, 1600);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 120, 60, 500);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 160, 60, 400);
    private static final ModConfigSpec.DoubleValue PATROL = B.defineInRange("outpostPatrolRadius", 24.0, 12, 48);
    private static final ModConfigSpec.IntValue NATURAL_WEIGHT = B.defineInRange("outpostSpawnWeight", 3, 1, 20);
    private static final ModConfigSpec.IntValue OUTPOST_CAP = B.defineInRange("outpostPopulationCap", 2, 1, 6);
    private static final ModConfigSpec.IntValue RAID_MIN_WAVE = B.defineInRange("raidMinimumWave", 3, 1, 7);
    private static final ModConfigSpec.DoubleValue RAID_CHANCE = B.defineInRange("raidWaveAppearanceChance", .8, 0, 1);
    private static final ModConfigSpec.IntValue RAID_PER_WAVE = B.defineInRange("raidMaximumPerWave", 1, 0, 3);
    private static final ModConfigSpec.IntValue RAID_CAP = B.defineInRange("raidMaximumActive", 2, 1, 6);
    private static final ModConfigSpec.IntValue RAID_ATTEMPTS = B.defineInRange("raidSpawnAttempts", 16, 4, 32);
    public static final ModConfigSpec SPEC = B.build();
    private RavagerEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double meleeKnockback() { return SPEC.isLoaded() ? MELEE_KNOCKBACK.get() : .75; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE_INTERVAL.get() : 42; }
    public static int meleeWindup() { return SPEC.isLoaded() ? MELEE_WINDUP.get() : 12; }
    public static double chargeMinimumRange() { return SPEC.isLoaded() ? CHARGE_MIN.get() : 5; }
    public static double chargeMaximumRange() { return Math.max(chargeMinimumRange() + 1, SPEC.isLoaded() ? CHARGE_MAX.get() : 18); }
    public static double chargeSpeed() { return SPEC.isLoaded() ? CHARGE_SPEED.get() : .72; }
    public static int chargeDuration() { return SPEC.isLoaded() ? CHARGE_DURATION.get() : 32; }
    public static int chargeWindup() { return SPEC.isLoaded() ? CHARGE_WINDUP.get() : 20; }
    public static float chargeDamage() { return (float)(SPEC.isLoaded() ? CHARGE_DAMAGE.get() : 16); }
    public static double chargeKnockback() { return SPEC.isLoaded() ? CHARGE_KNOCKBACK.get() : 1.05; }
    public static int chargeCooldown() { return SPEC.isLoaded() ? CHARGE_COOLDOWN.get() : 240; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 16; }
    public static double roarRadius() { return SPEC.isLoaded() ? ROAR_RADIUS.get() : 6; }
    public static double roarKnockback() { return SPEC.isLoaded() ? ROAR_KNOCKBACK.get() : .7; }
    public static int roarDisruptionTicks() { return SPEC.isLoaded() ? ROAR_DISRUPTION.get() : 30; }
    public static int roarWindup() { return SPEC.isLoaded() ? ROAR_WINDUP.get() : 20; }
    public static int roarCooldown() { return SPEC.isLoaded() ? ROAR_COOLDOWN.get() : 400; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT.get() : 120; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 160; }
    public static double patrolRadius() { return SPEC.isLoaded() ? PATROL.get() : 24; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? NATURAL_WEIGHT.get() : 3; }
    public static int outpostPopulationCap() { return SPEC.isLoaded() ? OUTPOST_CAP.get() : 2; }
    public static int raidMinimumWave() { return SPEC.isLoaded() ? RAID_MIN_WAVE.get() : 3; }
    public static double raidWaveChance() { return SPEC.isLoaded() ? RAID_CHANCE.get() : .8; }
    public static int raidMaxPerWave() { return SPEC.isLoaded() ? RAID_PER_WAVE.get() : 1; }
    public static int raidMaxActive() { return SPEC.isLoaded() ? RAID_CAP.get() : 2; }
    public static int raidSpawnAttempts() { return SPEC.isLoaded() ? RAID_ATTEMPTS.get() : 16; }
}
