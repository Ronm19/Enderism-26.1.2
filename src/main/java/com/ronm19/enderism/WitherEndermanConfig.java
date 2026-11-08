package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial balance for Withering Grasp, Skull Barrage and Rose of Decay. */
public final class WitherEndermanConfig {
    public static final double DEFAULT_HEALTH = 56, DEFAULT_DAMAGE = 9, DEFAULT_SPEED = .29, DEFAULT_RANGE = 40, DEFAULT_ARMOR = 2;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 24, 120);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 3, 20);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .2, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 16, 64);
    private static final ModConfigSpec.DoubleValue ARMOR = B.defineInRange("armor", DEFAULT_ARMOR, 0, 12);
    private static final ModConfigSpec.IntValue GRASP_WINDUP = B.defineInRange("graspWindupTicks", 8, 4, 20);
    private static final ModConfigSpec.IntValue GRASP_INTERVAL = B.defineInRange("graspIntervalTicks", 30, 20, 80);
    private static final ModConfigSpec.IntValue GRASP_WITHER = B.defineInRange("graspWitherTicks", 100, 20, 240);
    private static final ModConfigSpec.IntValue WITHER_AMPLIFIER = B.defineInRange("witherAmplifier", 0, 0, 1);
    private static final ModConfigSpec.IntValue BARRAGE_COUNT = B.defineInRange("barrageProjectileCount", 3, 2, 5);
    private static final ModConfigSpec.IntValue BARRAGE_PREP = B.defineInRange("barragePreparationTicks", 22, 10, 50);
    private static final ModConfigSpec.IntValue BARRAGE_INTERVAL = B.defineInRange("barrageShotIntervalTicks", 9, 6, 20);
    private static final ModConfigSpec.IntValue BARRAGE_COOLDOWN = B.defineInRange("barrageCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.DoubleValue PROJECTILE_RANGE = B.defineInRange("projectileRange", 28.0, 12, 40);
    private static final ModConfigSpec.DoubleValue PROJECTILE_SPEED = B.defineInRange("projectileSpeed", .85, .4, 1.4);
    private static final ModConfigSpec.DoubleValue PROJECTILE_DAMAGE = B.defineInRange("projectileDamage", 8.0, 2, 18);
    private static final ModConfigSpec.IntValue PROJECTILE_WITHER = B.defineInRange("projectileWitherTicks", 80, 20, 240);
    private static final ModConfigSpec.IntValue PROJECTILE_LIFETIME = B.defineInRange("projectileLifetimeTicks", 80, 30, 120);
    private static final ModConfigSpec.DoubleValue EXPLOSION_RADIUS = B.comment("Combat radius only. Skull impacts never destroy blocks or create fire.").defineInRange("explosionRadius", 1.5, .5, 2.5);
    private static final ModConfigSpec.DoubleValue EXPLOSION_DAMAGE = B.defineInRange("explosionDamage", 4.0, 0, 10);
    private static final ModConfigSpec.IntValue ROSE_COUNT = B.defineInRange("roseCount", 4, 1, 6);
    private static final ModConfigSpec.DoubleValue ROSE_RADIUS = B.defineInRange("roseRadius", 4.5, 2, 7);
    private static final ModConfigSpec.DoubleValue ROSE_SPACING = B.defineInRange("roseSpacing", 2.0, 1.5, 4);
    private static final ModConfigSpec.IntValue ROSE_CAP = B.defineInRange("roseLocalCap", 12, 4, 20);
    private static final ModConfigSpec.IntValue ROSE_BUDGET = B.defineInRange("rosePlacementBudget", 48, 12, 80);
    private static final ModConfigSpec.DoubleValue ROSE_RANGE = B.defineInRange("roseCastingRange", 16.0, 8, 24);
    private static final ModConfigSpec.IntValue ROSE_PREP = B.defineInRange("rosePreparationTicks", 28, 12, 60);
    private static final ModConfigSpec.IntValue ROSE_COOLDOWN = B.defineInRange("roseCooldownTicks", 360, 160, 1200);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 7, 3, 20);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 40, 300);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 6, 1, 24);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 3, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private WitherEndermanConfig() { }
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double armor() { return SPEC.isLoaded() ? ARMOR.get() : DEFAULT_ARMOR; }
    public static int graspWindupTicks() { return SPEC.isLoaded() ? GRASP_WINDUP.get() : 8; }
    public static int graspIntervalTicks() { return SPEC.isLoaded() ? GRASP_INTERVAL.get() : 30; }
    public static int graspWitherTicks() { return SPEC.isLoaded() ? GRASP_WITHER.get() : 100; }
    public static int witherAmplifier() { return SPEC.isLoaded() ? WITHER_AMPLIFIER.get() : 0; }
    public static int barrageProjectileCount() { return SPEC.isLoaded() ? BARRAGE_COUNT.get() : 3; }
    public static int barragePreparationTicks() { return SPEC.isLoaded() ? BARRAGE_PREP.get() : 22; }
    public static int barrageShotIntervalTicks() { return SPEC.isLoaded() ? BARRAGE_INTERVAL.get() : 9; }
    public static int barrageCooldownTicks() { return SPEC.isLoaded() ? BARRAGE_COOLDOWN.get() : 160; }
    public static double projectileRange() { return SPEC.isLoaded() ? PROJECTILE_RANGE.get() : 28; }
    public static double projectileSpeed() { return SPEC.isLoaded() ? PROJECTILE_SPEED.get() : .85; }
    public static double projectileDamage() { return SPEC.isLoaded() ? PROJECTILE_DAMAGE.get() : 8; }
    public static int projectileWitherTicks() { return SPEC.isLoaded() ? PROJECTILE_WITHER.get() : 80; }
    public static int projectileLifetimeTicks() { return SPEC.isLoaded() ? PROJECTILE_LIFETIME.get() : 80; }
    public static double explosionRadius() { return SPEC.isLoaded() ? EXPLOSION_RADIUS.get() : 1.5; }
    public static double explosionDamage() { return SPEC.isLoaded() ? EXPLOSION_DAMAGE.get() : 4; }
    public static int roseCount() { return SPEC.isLoaded() ? ROSE_COUNT.get() : 4; }
    public static double roseRadius() { return SPEC.isLoaded() ? ROSE_RADIUS.get() : 4.5; }
    public static double roseSpacing() { return SPEC.isLoaded() ? ROSE_SPACING.get() : 2; }
    public static int roseLocalCap() { return SPEC.isLoaded() ? ROSE_CAP.get() : 12; }
    public static int rosePlacementBudget() { return SPEC.isLoaded() ? ROSE_BUDGET.get() : 48; }
    public static double roseCastingRange() { return SPEC.isLoaded() ? ROSE_RANGE.get() : 16; }
    public static int rosePreparationTicks() { return SPEC.isLoaded() ? ROSE_PREP.get() : 28; }
    public static int roseCooldownTicks() { return SPEC.isLoaded() ? ROSE_COOLDOWN.get() : 360; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 7; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 6; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 3; }
}
