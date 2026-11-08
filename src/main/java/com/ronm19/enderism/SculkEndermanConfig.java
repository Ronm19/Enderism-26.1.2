package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable starting values for #025's three abilities and bounded sensory memory. */
public final class SculkEndermanConfig {
    public static final double DEFAULT_HEALTH = 54, DEFAULT_DAMAGE = 11, DEFAULT_SPEED = .29, DEFAULT_RANGE = 32;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 120);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 3, 24);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .15, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.comment("A combat limit, not automatic visual detection. Only legitimate sensory evidence discovers distant players.")
            .defineInRange("followRange", DEFAULT_RANGE, 16, 64);
    private static final ModConfigSpec.DoubleValue KNOCKBACK = B.defineInRange("meleeKnockback", .65, 0, 1.5);
    private static final ModConfigSpec.IntValue CRUSHER_WINDUP = B.defineInRange("crusherWindupTicks", 12, 6, 30);
    private static final ModConfigSpec.IntValue CRUSHER_INTERVAL = B.defineInRange("crusherIntervalTicks", 36, 24, 100);
    private static final ModConfigSpec.IntValue SLOW = B.defineInRange("slownessTicks", 80, 20, 160);
    private static final ModConfigSpec.IntValue SLOW_AMPLIFIER = B.defineInRange("slownessAmplifier", 2, 0, 3);
    private static final ModConfigSpec.IntValue VIBRATION_RANGE = B.comment("Bounded by the native ordinary Game Event notification radius.")
            .defineInRange("vibrationRange", 16, 8, 16);
    private static final ModConfigSpec.IntValue INVESTIGATION = B.defineInRange("investigationTicks", 120, 40, 300);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 100, 40, 240);
    private static final ModConfigSpec.DoubleValue PROXIMITY = B.comment("Stillness and sneaking do not prevent detection within this sphere. This does not extend melee reach.")
            .defineInRange("proximityRange", 5.0, 3, 7);
    private static final ModConfigSpec.IntValue DARKNESS = B.defineInRange("darknessTicks", 80, 20, 140);
    private static final ModConfigSpec.IntValue DARKNESS_REFRESH = B.comment("Per player and per creature. Kept longer than the effect, leaving a finite recovery window.")
            .defineInRange("darknessRefreshTicks", 140, 80, 600);
    private static final ModConfigSpec.IntValue DETECTION_CUE = B.defineInRange("detectionCueIntervalTicks", 30, 10, 100);
    private static final ModConfigSpec.DoubleValue PURSUIT_SPEED = B.defineInRange("pursuitSpeed", 1.05, .7, 1.4);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 10, 4, 40);
    private static final ModConfigSpec.IntValue DEEP_DARK_WEIGHT = B.defineInRange("deepDarkSpawnWeight", 10, 1, 40);
    private static final ModConfigSpec.IntValue CITY_WEIGHT = B.defineInRange("ancientCitySpawnWeight", 18, 1, 60);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 3, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private SculkEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double meleeKnockback() { return SPEC.isLoaded() ? KNOCKBACK.get() : .65; }
    public static int crusherWindupTicks() { return SPEC.isLoaded() ? CRUSHER_WINDUP.get() : 12; }
    public static int crusherIntervalTicks() { return SPEC.isLoaded() ? CRUSHER_INTERVAL.get() : 36; }
    public static int slownessTicks() { return SPEC.isLoaded() ? SLOW.get() : 80; }
    public static int slownessAmplifier() { return SPEC.isLoaded() ? SLOW_AMPLIFIER.get() : 2; }
    public static int vibrationRange() { return SPEC.isLoaded() ? VIBRATION_RANGE.get() : 16; }
    public static int investigationTicks() { return SPEC.isLoaded() ? INVESTIGATION.get() : 120; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 100; }
    public static double proximityRange() { return SPEC.isLoaded() ? PROXIMITY.get() : 5; }
    public static int darknessTicks() { return SPEC.isLoaded() ? DARKNESS.get() : 80; }
    public static int darknessRefreshTicks() { return Math.max(darknessTicks() + 20, SPEC.isLoaded() ? DARKNESS_REFRESH.get() : 140); }
    public static int detectionCueIntervalTicks() { return SPEC.isLoaded() ? DETECTION_CUE.get() : 30; }
    public static double pursuitSpeed() { return SPEC.isLoaded() ? PURSUIT_SPEED.get() : 1.05; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 10; }
    public static int deepDarkSpawnWeight() { return SPEC.isLoaded() ? DEEP_DARK_WEIGHT.get() : 10; }
    public static int ancientCitySpawnWeight() { return SPEC.isLoaded() ? CITY_WEIGHT.get() : 18; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 3; }
}
