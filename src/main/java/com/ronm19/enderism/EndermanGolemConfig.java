package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Initial adjustable guardian balance, not fixed lore values. */
public final class EndermanGolemConfig {
    public static final double DEFAULT_HEALTH = 180, DEFAULT_SPEED = .23, DEFAULT_DAMAGE = 18, DEFAULT_RANGE = 40;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 60, 500);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .14, .35);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("crusherDamage", DEFAULT_DAMAGE, 6, 36);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange", DEFAULT_RANGE, 20, 64);
    private static final ModConfigSpec.DoubleValue SCALE = B.defineInRange("sizeMultiplier", 1.0, .65, 1.3);
    private static final ModConfigSpec.DoubleValue TERRITORY = B.defineInRange("personalSpaceRadius", 6.0, 3, 10);
    private static final ModConfigSpec.DoubleValue RETREAT = B.defineInRange("calmDownDistance", 14.0, 8, 28);
    private static final ModConfigSpec.IntValue WARNING = B.defineInRange("territorialWarningTicks", 20, 10, 60);
    private static final ModConfigSpec.IntValue CALM = B.defineInRange("calmDownDelayTicks", 40, 10, 120);
    private static final ModConfigSpec.IntValue ATTACK_MEMORY = B.defineInRange("directAttackMemoryTicks", 100, 20, 240);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 240, 100, 600);
    private static final ModConfigSpec.IntValue CRUSHER_WINDUP = B.defineInRange("crusherWindupTicks", 16, 10, 40);
    private static final ModConfigSpec.IntValue CRUSHER_INTERVAL = B.defineInRange("crusherIntervalTicks", 54, 36, 120);
    private static final ModConfigSpec.DoubleValue CRUSHER_KNOCKBACK = B.defineInRange("crusherKnockback", .9, .2, 1.6);
    private static final ModConfigSpec.IntValue INTERCEPTION_COOLDOWN = B.defineInRange("interceptionCooldownTicks", 180, 80, 1200);
    private static final ModConfigSpec.IntValue INTERCEPTION_DURATION = B.defineInRange("interceptionDurationTicks", 70, 30, 160);
    private static final ModConfigSpec.DoubleValue INTERCEPTION_SPEED = B.defineInRange("interceptionSpeedModifier", 1.2, 1.0, 1.5);
    private static final ModConfigSpec.DoubleValue PROTECTION_RANGE = B.defineInRange("villagerProtectionRange", 28.0, 12, 40);
    private static final ModConfigSpec.DoubleValue JUDGMENT_RADIUS = B.defineInRange("judgmentRadius", 6.5, 4, 10);
    private static final ModConfigSpec.DoubleValue JUDGMENT_DAMAGE = B.defineInRange("judgmentDamage", 12.0, 4, 24);
    private static final ModConfigSpec.DoubleValue JUDGMENT_KNOCKBACK = B.defineInRange("judgmentKnockback", 1.05, .2, 1.7);
    private static final ModConfigSpec.IntValue JUDGMENT_WINDUP = B.defineInRange("judgmentWindupTicks", 24, 16, 60);
    private static final ModConfigSpec.IntValue JUDGMENT_COOLDOWN = B.defineInRange("judgmentCooldownTicks", 600, 240, 1800);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks", 18, 8, 60);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("teleportCooldownTicks", 160, 80, 600);
    private static final ModConfigSpec.DoubleValue PATROL = B.defineInRange("villagePatrolRadius", 32.0, 16, 64);
    private static final ModConfigSpec.IntValue SPAWN_WEIGHT = B.defineInRange("villageSpawnWeight", 2, 1, 12);
    private static final ModConfigSpec.IntValue POPULATION_CAP = B.defineInRange("villagePopulationCap", 1, 1, 3);
    private static final ModConfigSpec.DoubleValue VILLAGE_RANGE = B.defineInRange("villageRange", 32.0, 8, 48);
    public static final ModConfigSpec SPEC = B.build();
    private EndermanGolemConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double sizeMultiplier() { return SPEC.isLoaded() ? SCALE.get() : 1; }
    public static double territoryRadius() { return SPEC.isLoaded() ? TERRITORY.get() : 6; }
    public static double retreatDistance() { return Math.max(territoryRadius() + 3, SPEC.isLoaded() ? RETREAT.get() : 14); }
    public static int warningTicks() { return SPEC.isLoaded() ? WARNING.get() : 20; }
    public static int calmDelayTicks() { return SPEC.isLoaded() ? CALM.get() : 40; }
    public static int directAttackMemoryTicks() { return SPEC.isLoaded() ? ATTACK_MEMORY.get() : 100; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 240; }
    public static int crusherWindup() { return SPEC.isLoaded() ? CRUSHER_WINDUP.get() : 16; }
    public static int crusherInterval() { return SPEC.isLoaded() ? CRUSHER_INTERVAL.get() : 54; }
    public static double crusherKnockback() { return SPEC.isLoaded() ? CRUSHER_KNOCKBACK.get() : .9; }
    public static int interceptionCooldown() { return SPEC.isLoaded() ? INTERCEPTION_COOLDOWN.get() : 180; }
    public static int interceptionDuration() { return SPEC.isLoaded() ? INTERCEPTION_DURATION.get() : 70; }
    public static double interceptionSpeed() { return SPEC.isLoaded() ? INTERCEPTION_SPEED.get() : 1.2; }
    public static double protectionRange() { return SPEC.isLoaded() ? PROTECTION_RANGE.get() : 28; }
    public static double judgmentRadius() { return SPEC.isLoaded() ? JUDGMENT_RADIUS.get() : 6.5; }
    public static float judgmentDamage() { return (float)(SPEC.isLoaded() ? JUDGMENT_DAMAGE.get() : 12); }
    public static double judgmentKnockback() { return SPEC.isLoaded() ? JUDGMENT_KNOCKBACK.get() : 1.05; }
    public static int judgmentWindup() { return SPEC.isLoaded() ? JUDGMENT_WINDUP.get() : 24; }
    public static int judgmentCooldown() { return SPEC.isLoaded() ? JUDGMENT_COOLDOWN.get() : 600; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 18; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT.get() : 160; }
    public static double patrolRadius() { return SPEC.isLoaded() ? PATROL.get() : 32; }
    public static int villageSpawnWeight() { return SPEC.isLoaded() ? SPAWN_WEIGHT.get() : 2; }
    public static int villagePopulationCap() { return SPEC.isLoaded() ? POPULATION_CAP.get() : 1; }
    public static int naturalSpawnWeight() { return villageSpawnWeight(); }
    public static double villageRange() { return SPEC.isLoaded() ? VILLAGE_RANGE.get() : 32; }
}
