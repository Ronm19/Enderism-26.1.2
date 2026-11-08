package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial balance values for Golden's movement-control kit. */
public final class GoldenEndermanConfig {
    public static final double DEFAULT_HEALTH = 38.0;
    public static final double DEFAULT_SPEED = 0.29;
    public static final double DEFAULT_DAMAGE = 6.0;
    public static final double DEFAULT_RANGE = 36.0;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 100.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_SPEED, 0.16, 0.50);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("attackDamage", DEFAULT_DAMAGE, 2.0, 15.0);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("detectionRange", DEFAULT_RANGE, 16.0, 64.0);
    private static final ModConfigSpec.IntValue MELEE_INTERVAL = BUILDER.defineInRange("meleeIntervalTicks", 24, 16, 60);
    private static final ModConfigSpec.IntValue MIDAS_DURATION = BUILDER.defineInRange("midasDurationTicks", 60, 20, 120);
    private static final ModConfigSpec.IntValue MIDAS_COOLDOWN = BUILDER.defineInRange("midasCooldownTicks", 76, 40, 240);
    private static final ModConfigSpec.DoubleValue SHACKLES_RANGE = BUILDER.defineInRange("shacklesRange", 14.0, 6.0, 24.0);
    private static final ModConfigSpec.IntValue SHACKLES_AMPLIFIER = BUILDER
            .comment("Zero-based vanilla Slowness amplifier. Three is Slowness IV.")
            .defineInRange("shacklesAmplifier", 3, 2, 4);
    private static final ModConfigSpec.IntValue SHACKLES_DURATION = BUILDER.defineInRange("shacklesDurationTicks", 78, 25, 140);
    private static final ModConfigSpec.IntValue SHACKLES_WINDUP = BUILDER.defineInRange("shacklesWindupTicks", 14, 10, 40);
    private static final ModConfigSpec.IntValue SHACKLES_COOLDOWN = BUILDER.defineInRange("shacklesCooldownTicks", 144, 90, 420);
    private static final ModConfigSpec.DoubleValue JUDGMENT_RANGE = BUILDER.defineInRange("judgmentRange", 4.5, 2.0, 7.0);
    private static final ModConfigSpec.DoubleValue JUDGMENT_DAMAGE = BUILDER.defineInRange("judgmentDamage", 9.0, 3.0, 18.0);
    private static final ModConfigSpec.IntValue JUDGMENT_WINDUP = BUILDER.defineInRange("judgmentWindupTicks", 20, 14, 60);
    private static final ModConfigSpec.IntValue JUDGMENT_STUN = BUILDER.defineInRange("judgmentStunTicks", 30, 12, 60);
    private static final ModConfigSpec.IntValue JUDGMENT_PROTECTION = BUILDER
            .comment("Target-wide immunity to another Golden Judgment stun after a stun ends.")
            .defineInRange("postStunProtectionTicks", 72, 40, 240);
    private static final ModConfigSpec.IntValue JUDGMENT_COOLDOWN = BUILDER.defineInRange("judgmentCooldownTicks", 200, 100, 600);
    private static final ModConfigSpec.IntValue TELEPORT_COOLDOWN = BUILDER.defineInRange("teleportCooldownTicks", 110, 40, 320);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER.defineInRange("encounterMemoryTicks", 120, 40, 240);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private GoldenEndermanConfig() {
    }

    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE_INTERVAL.get() : 24; }
    public static int midasDuration() { return SPEC.isLoaded() ? MIDAS_DURATION.get() : 60; }
    public static int midasCooldown() { return SPEC.isLoaded() ? MIDAS_COOLDOWN.get() : 76; }
    public static double shacklesRange() { return SPEC.isLoaded() ? SHACKLES_RANGE.get() : 14.0; }
    public static int shacklesAmplifier() { return SPEC.isLoaded() ? SHACKLES_AMPLIFIER.get() : 3; }
    public static int shacklesDuration() { return SPEC.isLoaded() ? SHACKLES_DURATION.get() : 78; }
    public static int shacklesWindup() { return SPEC.isLoaded() ? SHACKLES_WINDUP.get() : 14; }
    public static int shacklesCooldown() { return SPEC.isLoaded() ? SHACKLES_COOLDOWN.get() : 144; }
    public static double judgmentRange() { return SPEC.isLoaded() ? JUDGMENT_RANGE.get() : 4.5; }
    public static float judgmentDamage() { return SPEC.isLoaded() ? JUDGMENT_DAMAGE.get().floatValue() : 9.0F; }
    public static int judgmentWindup() { return SPEC.isLoaded() ? JUDGMENT_WINDUP.get() : 20; }
    public static int judgmentStun() { return SPEC.isLoaded() ? JUDGMENT_STUN.get() : 30; }
    public static int judgmentProtection() { return SPEC.isLoaded() ? JUDGMENT_PROTECTION.get() : 72; }
    public static int judgmentCooldown() { return SPEC.isLoaded() ? JUDGMENT_COOLDOWN.get() : 200; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT_COOLDOWN.get() : 110; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
}
