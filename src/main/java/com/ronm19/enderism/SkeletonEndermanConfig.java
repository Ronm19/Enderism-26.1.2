package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial, adjustable archery tuning. The twenty-player volley ceiling is deliberately fixed in code. */
public final class SkeletonEndermanConfig {
    public static final double DEFAULT_HEALTH = 42, DEFAULT_DAMAGE = 7, DEFAULT_SPEED = .29, DEFAULT_RANGE = 40;
    public static final int MAX_VOLLEY_ARROWS = 20;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 120);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("meleeDamage", DEFAULT_DAMAGE, 1, 24);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .1, .5);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("detectionRange", DEFAULT_RANGE, 8, 64);
    private static final ModConfigSpec.DoubleValue PLAYER_RADIUS = B.comment("Nearby eligible players contribute one arrow each. The hard cap of twenty cannot be raised by configuration.")
            .defineInRange("nearbyPlayerRadius", 32.0, 8, 48);
    private static final ModConfigSpec.IntValue MELEE = B.defineInRange("meleeIntervalTicks", 30, 20, 100);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 20, 400);
    private static final ModConfigSpec.DoubleValue ARROW_SPEED = B.defineInRange("arrowSpeed", 1.65, .5, 3);
    private static final ModConfigSpec.DoubleValue ARROW_DAMAGE = B.comment("Approximate raw physical damage at launch; vanilla arrow speed, collision and mitigation remain authoritative.")
            .defineInRange("arrowDamage", 5.0, 1, 16);
    private static final ModConfigSpec.DoubleValue ARROW_INACCURACY = B.defineInRange("arrowInaccuracy", 3.0, 1, 16);
    private static final ModConfigSpec.IntValue ARROW_LIFETIME = B.defineInRange("arrowLifetimeTicks", 100, 20, 200);
    private static final ModConfigSpec.DoubleValue ARROW_RANGE = B.defineInRange("arrowRange", 48.0, 12, 64);
    private static final ModConfigSpec.IntValue VOLLEY_PREPARATION = B.defineInRange("volleyPreparationTicks", 16, 8, 60);
    private static final ModConfigSpec.IntValue VOLLEY_COOLDOWN = B.defineInRange("volleyCooldownTicks", 70, 40, 400);
    private static final ModConfigSpec.IntValue CURSED_PREPARATION = B.defineInRange("cursedPreparationTicks", 22, 10, 80);
    private static final ModConfigSpec.IntValue CURSED_COOLDOWN = B.defineInRange("cursedCooldownTicks", 140, 60, 600);
    private static final ModConfigSpec.DoubleValue CURSED_DAMAGE = B.defineInRange("cursedDamage", 7.0, 1, 20);
    private static final ModConfigSpec.IntValue BARRAGE_PREPARATION = B.defineInRange("barragePreparationTicks", 36, 20, 100);
    private static final ModConfigSpec.IntValue BARRAGE_COOLDOWN = B.defineInRange("barrageCooldownTicks", 300, 160, 1200);
    private static final ModConfigSpec.DoubleValue BARRAGE_DAMAGE = B.defineInRange("barrageDamage", 7.0, 1, 20);
    private static final ModConfigSpec.DoubleValue BARRAGE_SPEED = B.defineInRange("barrageSpeedMultiplier", 1.12, 1, 1.4);
    private static final ModConfigSpec.IntValue FREEZE = B.comment("Initial vanilla frozen ticks. They decay normally; this does not create a permanent freeze or change powder snow.")
            .defineInRange("freezeTicks", 140, 40, 200);
    private static final ModConfigSpec.IntValue BURN = B.defineInRange("burnSeconds", 2, 1, 5);
    private static final ModConfigSpec.IntValue SLOW = B.defineInRange("slowTicks", 60, 20, 160);
    private static final ModConfigSpec.IntValue SLOW_AMPLIFIER = B.defineInRange("slowAmplifier", 0, 0, 1);
    private static final ModConfigSpec.IntValue POISON = B.defineInRange("poisonTicks", 60, 20, 120);
    private static final ModConfigSpec.IntValue POISON_AMPLIFIER = B.defineInRange("poisonAmplifier", 0, 0, 1);
    private static final ModConfigSpec.IntValue WITHER = B.defineInRange("witherTicks", 60, 20, 120);
    private static final ModConfigSpec.IntValue WITHER_AMPLIFIER = B.defineInRange("witherAmplifier", 0, 0, 1);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 12, 5, 40);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 12, 1, 50);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 3, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private SkeletonEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double nearbyPlayerRadius() { return SPEC.isLoaded() ? PLAYER_RADIUS.get() : 32; }
    public static int meleeIntervalTicks() { return SPEC.isLoaded() ? MELEE.get() : 30; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static double arrowSpeed() { return SPEC.isLoaded() ? ARROW_SPEED.get() : 1.65; }
    public static double arrowDamage() { return SPEC.isLoaded() ? ARROW_DAMAGE.get() : 5; }
    public static double arrowInaccuracy() { return SPEC.isLoaded() ? ARROW_INACCURACY.get() : 3; }
    public static int arrowLifetimeTicks() { return SPEC.isLoaded() ? ARROW_LIFETIME.get() : 100; }
    public static double arrowRange() { return SPEC.isLoaded() ? ARROW_RANGE.get() : 48; }
    public static int volleyPreparationTicks() { return SPEC.isLoaded() ? VOLLEY_PREPARATION.get() : 16; }
    public static int volleyCooldownTicks() { return SPEC.isLoaded() ? VOLLEY_COOLDOWN.get() : 70; }
    public static int cursedPreparationTicks() { return SPEC.isLoaded() ? CURSED_PREPARATION.get() : 22; }
    public static int cursedCooldownTicks() { return SPEC.isLoaded() ? CURSED_COOLDOWN.get() : 140; }
    public static double cursedDamage() { return SPEC.isLoaded() ? CURSED_DAMAGE.get() : 7; }
    public static int barragePreparationTicks() { return SPEC.isLoaded() ? BARRAGE_PREPARATION.get() : 36; }
    public static int barrageCooldownTicks() { return SPEC.isLoaded() ? BARRAGE_COOLDOWN.get() : 300; }
    public static double barrageDamage() { return SPEC.isLoaded() ? BARRAGE_DAMAGE.get() : 7; }
    public static double barrageSpeedMultiplier() { return SPEC.isLoaded() ? BARRAGE_SPEED.get() : 1.12; }
    public static int freezeTicks() { return SPEC.isLoaded() ? FREEZE.get() : 140; }
    public static int burnSeconds() { return SPEC.isLoaded() ? BURN.get() : 2; }
    public static int slowTicks() { return SPEC.isLoaded() ? SLOW.get() : 60; }
    public static int slowAmplifier() { return SPEC.isLoaded() ? SLOW_AMPLIFIER.get() : 0; }
    public static int poisonTicks() { return SPEC.isLoaded() ? POISON.get() : 60; }
    public static int poisonAmplifier() { return SPEC.isLoaded() ? POISON_AMPLIFIER.get() : 0; }
    public static int witherTicks() { return SPEC.isLoaded() ? WITHER.get() : 60; }
    public static int witherAmplifier() { return SPEC.isLoaded() ? WITHER_AMPLIFIER.get() : 0; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 12; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 12; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 3; }
}
