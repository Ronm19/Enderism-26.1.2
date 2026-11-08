package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial balance for #026's three separate attrition abilities. */
public final class HuskEndermanConfig {
    public static final double DEFAULT_HEALTH = 48, DEFAULT_DAMAGE = 8, DEFAULT_SPEED = .29, DEFAULT_RANGE = 32;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 100);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 3, 20);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .15, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 16, 64);
    private static final ModConfigSpec.DoubleValue REACH = B.comment("Small unarmed addition to the shared combat profile. Item-defined attack ranges stay unchanged.").defineInRange("meleeReachBonus", .1, 0, .6);
    private static final ModConfigSpec.IntValue STRIKE_WINDUP = B.defineInRange("strikeWindupTicks", 10, 5, 24);
    private static final ModConfigSpec.IntValue STRIKE_INTERVAL = B.defineInRange("strikeIntervalTicks", 34, 24, 100);
    private static final ModConfigSpec.IntValue WEAKNESS = B.defineInRange("weaknessTicks", 100, 20, 200);
    private static final ModConfigSpec.IntValue WEAKNESS_AMPLIFIER = B.defineInRange("weaknessAmplifier", 0, 0, 2);
    private static final ModConfigSpec.IntValue CURSE_PREP = B.defineInRange("cursePreparationTicks", 20, 10, 60);
    private static final ModConfigSpec.DoubleValue CURSE_RANGE = B.defineInRange("curseRange", 14.0, 4, 24);
    private static final ModConfigSpec.IntValue CURSE_COOLDOWN = B.defineInRange("curseCooldownTicks", 240, 120, 1000);
    private static final ModConfigSpec.IntValue HUNGER = B.defineInRange("hungerTicks", 200, 40, 400);
    private static final ModConfigSpec.IntValue HUNGER_AMPLIFIER = B.defineInRange("hungerAmplifier", 1, 0, 2);
    private static final ModConfigSpec.IntValue DELIRIUM_PREP = B.defineInRange("deliriumPreparationTicks", 24, 12, 60);
    private static final ModConfigSpec.DoubleValue DELIRIUM_RADIUS = B.defineInRange("deliriumRadius", 6.0, 3, 10);
    private static final ModConfigSpec.IntValue DELIRIUM_COOLDOWN = B.defineInRange("deliriumCooldownTicks", 360, 180, 1200);
    private static final ModConfigSpec.IntValue NAUSEA = B.defineInRange("nauseaTicks", 140, 60, 240);
    private static final ModConfigSpec.IntValue DELIRIUM_TARGETS = B.defineInRange("maxDeliriumTargets", 8, 1, 16);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 40, 300);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 10, 4, 40);
    private static final ModConfigSpec.IntValue DESERT_WEIGHT = B.defineInRange("desertSpawnWeight", 12, 1, 40);
    private static final ModConfigSpec.IntValue BADLANDS_WEIGHT = B.defineInRange("badlandsSpawnWeight", 8, 1, 40);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 3, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private HuskEndermanConfig() { }
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double meleeReachBonus() { return SPEC.isLoaded() ? REACH.get() : .1; }
    public static int strikeWindupTicks() { return SPEC.isLoaded() ? STRIKE_WINDUP.get() : 10; }
    public static int strikeIntervalTicks() { return SPEC.isLoaded() ? STRIKE_INTERVAL.get() : 34; }
    public static int weaknessTicks() { return SPEC.isLoaded() ? WEAKNESS.get() : 100; }
    public static int weaknessAmplifier() { return SPEC.isLoaded() ? WEAKNESS_AMPLIFIER.get() : 0; }
    public static int cursePreparationTicks() { return SPEC.isLoaded() ? CURSE_PREP.get() : 20; }
    public static double curseRange() { return SPEC.isLoaded() ? CURSE_RANGE.get() : 14; }
    public static int curseCooldownTicks() { return SPEC.isLoaded() ? CURSE_COOLDOWN.get() : 240; }
    public static int hungerTicks() { return SPEC.isLoaded() ? HUNGER.get() : 200; }
    public static int hungerAmplifier() { return SPEC.isLoaded() ? HUNGER_AMPLIFIER.get() : 1; }
    public static int deliriumPreparationTicks() { return SPEC.isLoaded() ? DELIRIUM_PREP.get() : 24; }
    public static double deliriumRadius() { return SPEC.isLoaded() ? DELIRIUM_RADIUS.get() : 6; }
    public static int deliriumCooldownTicks() { return SPEC.isLoaded() ? DELIRIUM_COOLDOWN.get() : 360; }
    public static int nauseaTicks() { return SPEC.isLoaded() ? NAUSEA.get() : 140; }
    public static int maxDeliriumTargets() { return SPEC.isLoaded() ? DELIRIUM_TARGETS.get() : 8; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 10; }
    public static int desertSpawnWeight() { return SPEC.isLoaded() ? DESERT_WEIGHT.get() : 12; }
    public static int badlandsSpawnWeight() { return SPEC.isLoaded() ? BADLANDS_WEIGHT.get() : 8; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 3; }
}
