package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial values for the three Zombie Enderman abilities and their encounter memory. */
public final class ZombieEndermanConfig {
    public static final double DEFAULT_HEALTH = 46, DEFAULT_DAMAGE = 8, DEFAULT_SPEED = .28, DEFAULT_RANGE = 32;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 100);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage", DEFAULT_DAMAGE, 3, 20);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .15, .4);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 16, 64);
    private static final ModConfigSpec.DoubleValue REACH = B.comment("Small species reach addition, on top of the shared combat profile. Explicit item attack ranges are preserved.")
            .defineInRange("biteReachBonus", .1, 0, .6);
    private static final ModConfigSpec.IntValue BITE_WINDUP = B.defineInRange("biteWindupTicks", 8, 4, 20);
    private static final ModConfigSpec.IntValue BITE_INTERVAL = B.defineInRange("biteIntervalTicks", 32, 24, 100);
    private static final ModConfigSpec.IntValue POISON = B.defineInRange("poisonTicks", 80, 20, 160);
    private static final ModConfigSpec.IntValue POISON_AMPLIFIER = B.defineInRange("poisonAmplifier", 0, 0, 1);
    private static final ModConfigSpec.IntValue SLOW = B.defineInRange("slownessTicks", 60, 20, 120);
    private static final ModConfigSpec.IntValue SLOW_AMPLIFIER = B.defineInRange("slownessAmplifier", 0, 0, 1);
    private static final ModConfigSpec.DoubleValue SIGNAL_RADIUS = B.defineInRange("signalRadius", 32.0, 8, 48);
    private static final ModConfigSpec.IntValue SIGNAL_INTERVAL = B.comment("Rate limit for repeat hits by the same attacker. The first accepted attack and a new attacker can alert immediately.")
            .defineInRange("signalAntiSpamTicks", 20, 10, 100);
    private static final ModConfigSpec.IntValue SIGNAL_MEMORY = B.defineInRange("signalMemoryTicks", 120, 40, 300);
    private static final ModConfigSpec.IntValue SIGNAL_RECIPIENTS = B.defineInRange("maxSignalRecipients", 8, 1, 16);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 120, 40, 300);
    private static final ModConfigSpec.IntValue FRENZY_PREP = B.defineInRange("frenzyPreparationTicks", 20, 10, 50);
    private static final ModConfigSpec.IntValue FRENZY_DURATION = B.defineInRange("frenzyDurationTicks", 100, 40, 200);
    private static final ModConfigSpec.IntValue FRENZY_COOLDOWN = B.defineInRange("frenzyCooldownTicks", 300, 160, 1200);
    private static final ModConfigSpec.DoubleValue FRENZY_SPEED = B.defineInRange("frenzyMovementBonus", .15, 0, .3);
    private static final ModConfigSpec.DoubleValue FRENZY_INTERVAL = B.comment("Multiplies bite intervals while frenzied. A hard minimum of twenty ticks preserves native damage windows.")
            .defineInRange("frenzyBiteIntervalMultiplier", .75, .6, 1);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("tacticalTeleportCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 10, 4, 40);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("naturalSpawnWeight", 12, 1, 40);
    private static final ModConfigSpec.IntValue POPULATION = B.defineInRange("naturalPopulationCap", 3, 1, 6);
    public static final ModConfigSpec SPEC = B.build();
    private ZombieEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double attackDamage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double movementSpeed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double biteReachBonus() { return SPEC.isLoaded() ? REACH.get() : .1; }
    public static int biteWindupTicks() { return SPEC.isLoaded() ? BITE_WINDUP.get() : 8; }
    public static int biteIntervalTicks() { return SPEC.isLoaded() ? BITE_INTERVAL.get() : 32; }
    public static int poisonTicks() { return SPEC.isLoaded() ? POISON.get() : 80; }
    public static int poisonAmplifier() { return SPEC.isLoaded() ? POISON_AMPLIFIER.get() : 0; }
    public static int slownessTicks() { return SPEC.isLoaded() ? SLOW.get() : 60; }
    public static int slownessAmplifier() { return SPEC.isLoaded() ? SLOW_AMPLIFIER.get() : 0; }
    public static double signalRadius() { return SPEC.isLoaded() ? SIGNAL_RADIUS.get() : 32; }
    public static int signalAntiSpamTicks() { return SPEC.isLoaded() ? SIGNAL_INTERVAL.get() : 20; }
    public static int signalMemoryTicks() { return SPEC.isLoaded() ? SIGNAL_MEMORY.get() : 120; }
    public static int maxSignalRecipients() { return SPEC.isLoaded() ? SIGNAL_RECIPIENTS.get() : 8; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static int frenzyPreparationTicks() { return SPEC.isLoaded() ? FRENZY_PREP.get() : 20; }
    public static int frenzyDurationTicks() { return SPEC.isLoaded() ? FRENZY_DURATION.get() : 100; }
    public static int frenzyCooldownTicks() { return SPEC.isLoaded() ? FRENZY_COOLDOWN.get() : 300; }
    public static double frenzyMovementBonus() { return SPEC.isLoaded() ? FRENZY_SPEED.get() : .15; }
    public static double frenzyBiteIntervalMultiplier() { return SPEC.isLoaded() ? FRENZY_INTERVAL.get() : .75; }
    public static int frenzyBiteIntervalTicks() { return Math.max(20, (int)Math.round(biteIntervalTicks() * frenzyBiteIntervalMultiplier())); }
    public static int tacticalTeleportCooldownTicks() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 10; }
    public static int naturalSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 12; }
    public static int naturalPopulationCap() { return SPEC.isLoaded() ? POPULATION.get() : 3; }
}
