package com.ronm19.enderism;

import com.ronm19.enderism.entity.FireEnderman;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable defaults for Infernal's melee, charge, and encounter behavior. */
public final class InfernalEndermanConfig {
    public static final double DEFAULT_HEALTH = 58.0;
    public static final double DEFAULT_SPEED = 0.30;
    public static final double DEFAULT_RANGE = 44.0;
    public static final double DEFAULT_DAMAGE = 10.0;
    public static final double DEFAULT_KNOCKBACK = 0.85;
    public static final int DEFAULT_BURN_TICKS = (int) (FireEnderman.MELEE_BURN_SECONDS * 20);

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = BUILDER.defineInRange("health", DEFAULT_HEALTH, 20.0, 160.0);
    private static final ModConfigSpec.DoubleValue SPEED = BUILDER.defineInRange("movementSpeed", DEFAULT_SPEED, 0.15, 0.55);
    private static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("followRange", DEFAULT_RANGE, 12.0, 64.0);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER.defineInRange("rampageDamage", DEFAULT_DAMAGE, 2.0, 22.0);
    private static final ModConfigSpec.DoubleValue KNOCKBACK = BUILDER.defineInRange("rampageKnockback", DEFAULT_KNOCKBACK, 0.0, 2.5);
    private static final ModConfigSpec.IntValue MELEE_INTERVAL = BUILDER.defineInRange("rampageInterval", 25, 16, 60);
    private static final ModConfigSpec.IntValue BURN_TICKS = BUILDER.comment("Defaults to Fire Enderman's burn duration. Infernal burn damage per native tick is always doubled.")
            .defineInRange("burnTicks", DEFAULT_BURN_TICKS, 20, 120);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER.defineInRange("encounterMemoryTicks", 120, 20, 240);
    private static final ModConfigSpec.BooleanValue CHARGE_ENABLED = BUILDER.define("chargeEnabled", true);
    private static final ModConfigSpec.IntValue CHARGE_WINDUP = BUILDER.defineInRange("chargeWindupTicks", 14, 8, 40);
    private static final ModConfigSpec.IntValue CHARGE_RECOVERY = BUILDER.defineInRange("chargeRecoveryTicks", 12, 6, 40);
    private static final ModConfigSpec.IntValue CHARGE_COOLDOWN = BUILDER.defineInRange("chargeCooldownTicks", 120, 60, 480);
    private static final ModConfigSpec.DoubleValue CHARGE_SPEED = BUILDER.defineInRange("chargeSpeed", 0.80, 0.45, 1.25);
    private static final ModConfigSpec.DoubleValue CHARGE_DISTANCE = BUILDER.defineInRange("chargeDistance", 11.0, 6.0, 18.0);
    private static final ModConfigSpec.DoubleValue CHARGE_DAMAGE = BUILDER.defineInRange("chargeDamage", 12.0, 3.0, 24.0);
    private static final ModConfigSpec.DoubleValue CHARGE_KNOCKBACK = BUILDER.defineInRange("chargeKnockback", 1.05, 0.0, 2.5);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private InfernalEndermanConfig() {}

    public static double health() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH; }
    public static double speed() { return SPEC.isLoaded() ? SPEED.get() : DEFAULT_SPEED; }
    public static double followRange() { return SPEC.isLoaded() ? RANGE.get() : DEFAULT_RANGE; }
    public static double damage() { return SPEC.isLoaded() ? DAMAGE.get() : DEFAULT_DAMAGE; }
    public static double knockback() { return SPEC.isLoaded() ? KNOCKBACK.get() : DEFAULT_KNOCKBACK; }
    public static int meleeInterval() { return SPEC.isLoaded() ? MELEE_INTERVAL.get() : 25; }
    public static int burnTicks() { return SPEC.isLoaded() ? BURN_TICKS.get() : DEFAULT_BURN_TICKS; }
    public static int memoryTicks() { return SPEC.isLoaded() ? MEMORY.get() : 120; }
    public static boolean chargeEnabled() { return !SPEC.isLoaded() || CHARGE_ENABLED.get(); }
    public static int chargeWindup() { return SPEC.isLoaded() ? CHARGE_WINDUP.get() : 14; }
    public static int chargeRecovery() { return SPEC.isLoaded() ? CHARGE_RECOVERY.get() : 12; }
    public static int chargeCooldown() { return SPEC.isLoaded() ? CHARGE_COOLDOWN.get() : 120; }
    public static double chargeSpeed() { return SPEC.isLoaded() ? CHARGE_SPEED.get() : 0.80; }
    public static double chargeDistance() { return SPEC.isLoaded() ? CHARGE_DISTANCE.get() : 11.0; }
    public static float chargeDamage() { return SPEC.isLoaded() ? CHARGE_DAMAGE.get().floatValue() : 12.0F; }
    public static double chargeKnockback() { return SPEC.isLoaded() ? CHARGE_KNOCKBACK.get() : 1.05; }
}
