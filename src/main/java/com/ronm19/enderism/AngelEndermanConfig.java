package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable first-pass balance; these values are not fixed lore. */
public final class AngelEndermanConfig {
    public static final double DEFAULT_HEALTH = 40, DEFAULT_SPEED = .25, DEFAULT_DAMAGE = 6, DEFAULT_RANGE = 24;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health", DEFAULT_HEALTH, 20, 100);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed", DEFAULT_SPEED, .15, .4);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("seraphicStrikeDamage", DEFAULT_DAMAGE, 2, 12);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("defensiveDetectionRange", DEFAULT_RANGE, 12, 48);
    private static final ModConfigSpec.DoubleValue TERRITORY = B.defineInRange("territorialRadius", 4.0, 2, 8);
    private static final ModConfigSpec.DoubleValue RETREAT = B.defineInRange("calmDownDistance", 10.0, 5, 24);
    private static final ModConfigSpec.IntValue WARNING = B.defineInRange("territorialWarningTicks", 16, 8, 60);
    private static final ModConfigSpec.IntValue CALM = B.defineInRange("calmDownDelayTicks", 40, 10, 120);
    private static final ModConfigSpec.IntValue ATTACK_MEMORY = B.defineInRange("directAttackMemoryTicks", 100, 20, 240);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("encounterMemoryTicks", 240, 80, 600);
    private static final ModConfigSpec.IntValue MELEE = B.defineInRange("defensiveAttackIntervalTicks", 26, 16, 60);
    private static final ModConfigSpec.DoubleValue SUPPORT = B.defineInRange("supportRange", 16.0, 6, 24);
    private static final ModConfigSpec.DoubleValue BENEDICTION_RADIUS = B.defineInRange("benedictionRadius", 12.0, 4, 20);
    private static final ModConfigSpec.DoubleValue HEAL = B.defineInRange("healingAmount", 6.0, 1, 12);
    private static final ModConfigSpec.IntValue HEAL_CAST = B.defineInRange("healingCastTicks", 24, 12, 80);
    private static final ModConfigSpec.IntValue HEAL_COOLDOWN = B.defineInRange("healingCooldownTicks", 160, 80, 1200);
    private static final ModConfigSpec.IntValue HEAL_LOCK = B.defineInRange("recipientHealingIntervalTicks", 240, 120, 2400);
    private static final ModConfigSpec.IntValue BUFF_CAST = B.defineInRange("benedictionCastTicks", 36, 16, 100);
    private static final ModConfigSpec.IntValue BUFF_COOLDOWN = B.defineInRange("benedictionCooldownTicks", 600, 240, 2400);
    private static final ModConfigSpec.IntValue BUFF_DURATION = B.defineInRange("benedictionDurationTicks", 240, 60, 600);
    private static final ModConfigSpec.IntValue BUFF_LOCK = B.defineInRange("recipientBenedictionIntervalTicks", 1200, 400, 4800);
    private static final ModConfigSpec.IntValue SCAN = B.defineInRange("supportScanIntervalTicks", 10, 5, 40);
    private static final ModConfigSpec.IntValue ALLIES = B.defineInRange("maximumBenedictionRecipients", 8, 1, 12);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("supportRecoveryTicks", 12, 6, 40);
    private static final ModConfigSpec.IntValue TELEPORT = B.defineInRange("teleportCooldownTicks", 80, 40, 240);
    public static final ModConfigSpec SPEC = B.build();
    private AngelEndermanConfig() {}
    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double territoryRadius() { return SPEC.isLoaded() ? TERRITORY.get() : 4; }
    public static double retreatDistance() { return Math.max(territoryRadius() + 2, SPEC.isLoaded() ? RETREAT.get() : 10); }
    public static int warningTicks() { return SPEC.isLoaded() ? WARNING.get() : 16; }
    public static int calmDelayTicks() { return SPEC.isLoaded() ? CALM.get() : 40; }
    public static int directAttackMemoryTicks() { return SPEC.isLoaded() ? ATTACK_MEMORY.get() : 100; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 240; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE.get() : 26; }
    public static double supportRange() { return SPEC.isLoaded() ? SUPPORT.get() : 16; }
    public static double benedictionRadius() { return Math.min(supportRange(), SPEC.isLoaded() ? BENEDICTION_RADIUS.get() : 12); }
    public static float healingAmount() { return (float)(SPEC.isLoaded() ? HEAL.get() : 6); }
    public static int healCastTicks() { return SPEC.isLoaded() ? HEAL_CAST.get() : 24; }
    public static int healingCooldown() { return SPEC.isLoaded() ? HEAL_COOLDOWN.get() : 160; }
    public static int recipientHealingInterval() { return SPEC.isLoaded() ? HEAL_LOCK.get() : 240; }
    public static int benedictionCastTicks() { return SPEC.isLoaded() ? BUFF_CAST.get() : 36; }
    public static int benedictionCooldown() { return SPEC.isLoaded() ? BUFF_COOLDOWN.get() : 600; }
    public static int benedictionDuration() { return SPEC.isLoaded() ? BUFF_DURATION.get() : 240; }
    public static int recipientBenedictionInterval() { return Math.max(benedictionDuration() + benedictionCooldown(), SPEC.isLoaded() ? BUFF_LOCK.get() : 1200); }
    public static int scanInterval() { return SPEC.isLoaded() ? SCAN.get() : 10; }
    public static int maximumRecipients() { return SPEC.isLoaded() ? ALLIES.get() : 8; }
    public static int recoveryTicks() { return SPEC.isLoaded() ? RECOVERY.get() : 12; }
    public static int teleportCooldown() { return SPEC.isLoaded() ? TELEPORT.get() : 80; }
}
