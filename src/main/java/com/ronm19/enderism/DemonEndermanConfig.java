package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial tuning for Demon's three melee abilities. */
public final class DemonEndermanConfig {
    public static final double DEFAULT_HEALTH = 46.0;
    public static final double DEFAULT_SPEED = 0.32;
    public static final double DEFAULT_DAMAGE = 6.5;
    public static final double DEFAULT_RANGE = 40.0;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 120.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_SPEED, 0.16, 0.50);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("attackDamage", DEFAULT_DAMAGE, 2.0, 18.0);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("detectionRange", DEFAULT_RANGE, 16.0, 64.0);
    private static final ModConfigSpec.IntValue MELEE_INTERVAL = BUILDER.defineInRange("meleeIntervalTicks", 22, 12, 60);
    private static final ModConfigSpec.IntValue ASH_THRESHOLD = BUILDER.defineInRange("ashThreshold", 4, 2, 12);
    private static final ModConfigSpec.IntValue ASH_EXPIRATION = BUILDER.defineInRange("ashExpirationTicks", 160, 40, 600);
    private static final ModConfigSpec.DoubleValue ASH_BURST = BUILDER
            .comment("Additional physical damage on the threshold strike; no periodic damage or terrain explosion.")
            .defineInRange("ashBurstDamage", 5.0, 1.0, 20.0);
    private static final ModConfigSpec.IntValue ASH_RETRIGGER = BUILDER.defineInRange("ashRetriggerTicks", 30, 12, 200);
    private static final ModConfigSpec.DoubleValue ONSLAUGHT_RANGE = BUILDER.defineInRange("onslaughtEngagementRange", 3.5, 1.5, 6.0);
    private static final ModConfigSpec.IntValue ONSLAUGHT_WINDUP = BUILDER.defineInRange("onslaughtWindupTicks", 12, 8, 40);
    private static final ModConfigSpec.IntValue ONSLAUGHT_STRIKES = BUILDER.defineInRange("onslaughtStrikeCount", 3, 2, 5);
    private static final ModConfigSpec.IntValue ONSLAUGHT_INTERVAL = BUILDER
            .comment("At least eleven ticks preserves vanilla damage immunity without resetting victim invulnerability.")
            .defineInRange("onslaughtStrikeIntervalTicks", 12, 11, 24);
    private static final ModConfigSpec.DoubleValue ONSLAUGHT_DAMAGE = BUILDER.defineInRange("onslaughtBonusDamage", 0.0, 0.0, 4.0);
    private static final ModConfigSpec.IntValue ONSLAUGHT_RECOVERY = BUILDER.defineInRange("onslaughtRecoveryTicks", 14, 8, 50);
    private static final ModConfigSpec.IntValue ONSLAUGHT_COOLDOWN = BUILDER.defineInRange("onslaughtCooldownTicks", 140, 80, 600);
    private static final ModConfigSpec.IntValue FRENZY_WINDUP = BUILDER.defineInRange("frenzyWindupTicks", 18, 12, 50);
    private static final ModConfigSpec.IntValue FRENZY_DURATION = BUILDER.defineInRange("frenzyDurationTicks", 140, 40, 400);
    private static final ModConfigSpec.DoubleValue FRENZY_SPEED = BUILDER.defineInRange("frenzyMovementBonus", 0.20, 0.0, 0.45);
    private static final ModConfigSpec.DoubleValue FRENZY_DAMAGE = BUILDER
            .comment("Flat temporary melee bonus, kept separate from the Ash burst bonus.")
            .defineInRange("frenzyBonusDamage", 1.0, 0.0, 4.0);
    private static final ModConfigSpec.IntValue FRENZY_MELEE_INTERVAL = BUILDER.defineInRange("frenzyMeleeIntervalTicks", 18, 12, 50);
    private static final ModConfigSpec.IntValue FRENZY_COOLDOWN = BUILDER.defineInRange("frenzyCooldownTicks", 480, 240, 1600);
    private static final ModConfigSpec.IntValue TELEPORT_COOLDOWN = BUILDER.defineInRange("teleportCooldownTicks", 90, 40, 400);
    private static final ModConfigSpec.IntValue FRENZY_TELEPORT_COOLDOWN = BUILDER.defineInRange("frenzyTeleportCooldownTicks", 65, 35, 300);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER.defineInRange("encounterMemoryTicks", 140, 40, 300);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private DemonEndermanConfig() { }

    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE_INTERVAL.get() : 22; }
    public static int ashThreshold() { return SPEC.isLoaded() ? ASH_THRESHOLD.get() : 4; }
    public static int ashExpiration() { return SPEC.isLoaded() ? ASH_EXPIRATION.get() : 160; }
    public static double ashBurstDamage() { return SPEC.isLoaded() ? ASH_BURST.get() : 5.0; }
    public static int ashRetrigger() { return SPEC.isLoaded() ? ASH_RETRIGGER.get() : 30; }
    public static double onslaughtRange() { return SPEC.isLoaded() ? ONSLAUGHT_RANGE.get() : 3.5; }
    public static int onslaughtWindup() { return SPEC.isLoaded() ? ONSLAUGHT_WINDUP.get() : 12; }
    public static int onslaughtStrikes() { return SPEC.isLoaded() ? ONSLAUGHT_STRIKES.get() : 3; }
    public static int onslaughtInterval() { return SPEC.isLoaded() ? ONSLAUGHT_INTERVAL.get() : 12; }
    public static double onslaughtDamageBonus() { return SPEC.isLoaded() ? ONSLAUGHT_DAMAGE.get() : 0.0; }
    public static int onslaughtRecovery() { return SPEC.isLoaded() ? ONSLAUGHT_RECOVERY.get() : 14; }
    public static int onslaughtCooldown() { return SPEC.isLoaded() ? ONSLAUGHT_COOLDOWN.get() : 140; }
    public static int frenzyWindup() { return SPEC.isLoaded() ? FRENZY_WINDUP.get() : 18; }
    public static int frenzyDuration() { return SPEC.isLoaded() ? FRENZY_DURATION.get() : 140; }
    public static double frenzySpeedBonus() { return SPEC.isLoaded() ? FRENZY_SPEED.get() : 0.20; }
    public static double frenzyDamageBonus() { return SPEC.isLoaded() ? FRENZY_DAMAGE.get() : 1.0; }
    public static int frenzyMeleeInterval() { return SPEC.isLoaded() ? FRENZY_MELEE_INTERVAL.get() : 18; }
    public static int frenzyCooldown() { return SPEC.isLoaded() ? FRENZY_COOLDOWN.get() : 480; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT_COOLDOWN.get() : 90; }
    public static int frenzyTeleportCooldown() { return SPEC.isLoaded() ? FRENZY_TELEPORT_COOLDOWN.get() : 65; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 140; }
}
