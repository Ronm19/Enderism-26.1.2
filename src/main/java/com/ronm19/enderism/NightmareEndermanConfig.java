package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable encounter timing and survival combat defaults for Nightmare. */
public final class NightmareEndermanConfig {
    public static final double DEFAULT_HEALTH = 42.0;
    public static final double DEFAULT_SPEED = 0.27;
    public static final double DEFAULT_DAMAGE = 7.0;
    public static final double DEFAULT_RANGE = 40.0;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 100.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_SPEED, 0.16, 0.45);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("attackDamage", DEFAULT_DAMAGE, 2.0, 16.0);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("detectionRange", DEFAULT_RANGE, 16.0, 64.0);
    private static final ModConfigSpec.IntValue MELEE = BUILDER.defineInRange("meleeIntervalTicks", 22, 16, 60);
    private static final ModConfigSpec.IntValue OBSERVATION = BUILDER.defineInRange("preHuntTicks", 100, 40, 240);
    private static final ModConfigSpec.DoubleValue COMMIT_RANGE = BUILDER.defineInRange("closeCommitRange", 3.5, 2.0, 6.0);
    private static final ModConfigSpec.IntValue REVEAL = BUILDER.defineInRange("revealTicks", 12, 6, 25);
    private static final ModConfigSpec.IntValue ILLUSION_COOLDOWN = BUILDER.defineInRange("illusionCooldownTicks", 60, 30, 160);
    private static final ModConfigSpec.IntValue ILLUSION_LIFETIME = BUILDER.defineInRange("illusionLifetimeTicks", 36, 15, 75);
    private static final ModConfigSpec.IntValue APPARITIONS = BUILDER.defineInRange("maximumApparitions", 3, 2, 3);
    private static final ModConfigSpec.IntValue DARKNESS = BUILDER.defineInRange("darknessDurationTicks", 40, 20, 80);
    private static final ModConfigSpec.IntValue DARKNESS_COOLDOWN = BUILDER.defineInRange("darknessCooldownTicks", 75, 40, 200);
    private static final ModConfigSpec.DoubleValue HUNT_SPEED = BUILDER.defineInRange("huntSpeedBonus", 0.18, 0.0, 0.40);
    private static final ModConfigSpec.IntValue TELEPORT = BUILDER.defineInRange("teleportCooldownTicks", 110, 50, 320);
    private static final ModConfigSpec.IntValue HUNT_TELEPORT = BUILDER.defineInRange("huntTeleportCooldownTicks", 80, 40, 240);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER.defineInRange("encounterMemoryTicks", 160, 60, 300);
    private static final ModConfigSpec.IntValue SEARCH = BUILDER.defineInRange("searchDisengagementTicks", 180, 80, 400);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private NightmareEndermanConfig() {}

    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE.get() : 22; }
    public static int preHuntTicks() { return SPEC.isLoaded() ? OBSERVATION.get() : 100; }
    public static double closeCommitRange() { return SPEC.isLoaded() ? COMMIT_RANGE.get() : 3.5; }
    public static int revealTicks() { return SPEC.isLoaded() ? REVEAL.get() : 12; }
    public static int illusionCooldown() { return SPEC.isLoaded() ? ILLUSION_COOLDOWN.get() : 60; }
    public static int illusionLifetime() { return SPEC.isLoaded() ? ILLUSION_LIFETIME.get() : 36; }
    public static int maximumApparitions() { return SPEC.isLoaded() ? APPARITIONS.get() : 3; }
    public static int darknessDuration() { return SPEC.isLoaded() ? DARKNESS.get() : 40; }
    public static int darknessCooldown() { return SPEC.isLoaded() ? DARKNESS_COOLDOWN.get() : 75; }
    public static double huntSpeedBonus() { return SPEC.isLoaded() ? HUNT_SPEED.get() : 0.18; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT.get() : 110; }
    public static int huntTeleportCooldown() { return SPEC.isLoaded() ? HUNT_TELEPORT.get() : 80; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 160; }
    public static int searchTicks() { return SPEC.isLoaded() ? SEARCH.get() : 180; }
}
