package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial, adjustable swamp combat values; the shared challenge profile applies separately. */
public final class BoggedEndermanConfig {
    public static final double DEFAULT_HEALTH = 48, DEFAULT_DAMAGE = 8, DEFAULT_SPEED = .28, DEFAULT_RANGE = 32;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 160);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("meleeDamage", DEFAULT_DAMAGE, 1, 24);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .1, .5);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("detectionRange", DEFAULT_RANGE, 8, 64);
    private static final ModConfigSpec.DoubleValue REACH = B.comment("Extra horizontal reach beyond the shared combat profile; explicit weapon ranges remain native.")
            .defineInRange("meleeReachBonus", .05, 0, .75);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 20, 400);
    private static final ModConfigSpec.IntValue GRASP_WINDUP = B.defineInRange("graspWindupTicks", 8, 4, 30);
    private static final ModConfigSpec.IntValue GRASP_INTERVAL = B.defineInRange("graspIntervalTicks", 30, 20, 100);
    private static final ModConfigSpec.IntValue POISON_AMPLIFIER = B.comment("Zero is Poison I; default one is Poison II. Repeated hits never add amplifiers.")
            .defineInRange("poisonAmplifier", 1, 0, 3);
    private static final ModConfigSpec.IntValue GRASP_POISON = B.defineInRange("graspPoisonTicks", 160, 40, 400);
    private static final ModConfigSpec.IntValue BARRAGE_PREP = B.defineInRange("barragePreparationTicks", 20, 10, 60);
    private static final ModConfigSpec.IntValue BARRAGE_SHOTS = B.defineInRange("barrageProjectileCount", 3, 1, 6);
    private static final ModConfigSpec.IntValue BARRAGE_SHOT_INTERVAL = B.defineInRange("barrageShotIntervalTicks", 10, 6, 30);
    private static final ModConfigSpec.IntValue BARRAGE_COOLDOWN = B.defineInRange("barrageCooldownTicks", 140, 60, 600);
    private static final ModConfigSpec.DoubleValue PROJECTILE_SPEED = B.defineInRange("projectileSpeed", 1.1, .35, 2.0);
    private static final ModConfigSpec.DoubleValue PROJECTILE_DAMAGE = B.defineInRange("projectileDamage", 4.0, 1, 12);
    private static final ModConfigSpec.DoubleValue PROJECTILE_RANGE = B.defineInRange("projectileRange", 28.0, 8, 40);
    private static final ModConfigSpec.IntValue PROJECTILE_LIFETIME = B.defineInRange("projectileLifetimeTicks", 80, 20, 160);
    private static final ModConfigSpec.IntValue PROJECTILE_POISON = B.defineInRange("projectilePoisonTicks", 100, 20, 300);
    private static final ModConfigSpec.IntValue MIRE_PREP = B.defineInRange("mirePreparationTicks", 30, 15, 80);
    private static final ModConfigSpec.DoubleValue MIRE_RANGE = B.defineInRange("mireCastingRange", 20.0, 8, 32);
    private static final ModConfigSpec.DoubleValue MIRE_RADIUS = B.defineInRange("mireRadius", 4.5, 2, 7);
    private static final ModConfigSpec.IntValue MIRE_LIFETIME = B.defineInRange("mireLifetimeTicks", 160, 40, 300);
    private static final ModConfigSpec.IntValue MIRE_INTERVAL = B.defineInRange("mireApplicationIntervalTicks", 20, 10, 60);
    private static final ModConfigSpec.IntValue MIRE_POISON = B.defineInRange("mirePoisonTicks", 60, 20, 160);
    private static final ModConfigSpec.IntValue MIRE_AMPLIFIER = B.defineInRange("mirePoisonAmplifier", 1, 0, 3);
    private static final ModConfigSpec.DoubleValue MIRE_DAMAGE = B.comment("Limited native damage per application, in addition to finite vanilla Poison; zero disables direct damage.")
            .defineInRange("mireDirectDamage", 1.0, 0, 3);
    private static final ModConfigSpec.IntValue MIRE_COOLDOWN = B.defineInRange("mireCooldownTicks", 300, 160, 1200);
    private static final ModConfigSpec.IntValue TELEPORT_COOLDOWN = B.defineInRange("tacticalTeleportCooldownTicks", 140, 60, 600);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 10, 4, 40);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 16, 1, 50);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 3, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private BoggedEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double meleeReachBonus() { return SPEC.isLoaded() ? REACH.get() : .05; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int graspWindupTicks() { return SPEC.isLoaded() ? GRASP_WINDUP.get() : 8; }
    public static int graspIntervalTicks() { return SPEC.isLoaded() ? GRASP_INTERVAL.get() : 30; }
    public static int poisonAmplifier() { return SPEC.isLoaded() ? POISON_AMPLIFIER.get() : 1; }
    public static int graspPoisonTicks() { return SPEC.isLoaded() ? GRASP_POISON.get() : 160; }
    public static int barragePreparationTicks() { return SPEC.isLoaded() ? BARRAGE_PREP.get() : 20; }
    public static int barrageProjectileCount() { return SPEC.isLoaded() ? BARRAGE_SHOTS.get() : 3; }
    public static int barrageShotIntervalTicks() { return SPEC.isLoaded() ? BARRAGE_SHOT_INTERVAL.get() : 10; }
    public static int barrageCooldownTicks() { return SPEC.isLoaded() ? BARRAGE_COOLDOWN.get() : 140; }
    public static double projectileSpeed() { return SPEC.isLoaded() ? PROJECTILE_SPEED.get() : 1.1; }
    public static double projectileDamage() { return SPEC.isLoaded() ? PROJECTILE_DAMAGE.get() : 4; }
    public static double projectileRange() { return SPEC.isLoaded() ? PROJECTILE_RANGE.get() : 28; }
    public static int projectileLifetimeTicks() { return SPEC.isLoaded() ? PROJECTILE_LIFETIME.get() : 80; }
    public static int projectilePoisonTicks() { return SPEC.isLoaded() ? PROJECTILE_POISON.get() : 100; }
    public static int mirePreparationTicks() { return SPEC.isLoaded() ? MIRE_PREP.get() : 30; }
    public static double mireCastingRange() { return SPEC.isLoaded() ? MIRE_RANGE.get() : 20; }
    public static double mireRadius() { return SPEC.isLoaded() ? MIRE_RADIUS.get() : 4.5; }
    public static int mireLifetimeTicks() { return SPEC.isLoaded() ? MIRE_LIFETIME.get() : 160; }
    public static int mireApplicationIntervalTicks() { return SPEC.isLoaded() ? MIRE_INTERVAL.get() : 20; }
    public static int mirePoisonTicks() { return SPEC.isLoaded() ? MIRE_POISON.get() : 60; }
    public static int mirePoisonAmplifier() { return SPEC.isLoaded() ? MIRE_AMPLIFIER.get() : 1; }
    public static double mireDirectDamage() { return SPEC.isLoaded() ? MIRE_DAMAGE.get() : 1; }
    public static int mireCooldownTicks() { return SPEC.isLoaded() ? MIRE_COOLDOWN.get() : 300; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT_COOLDOWN.get() : 140; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 10; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 16; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 3; }
}
