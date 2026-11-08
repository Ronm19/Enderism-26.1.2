package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Shared, adjustable challenge profile layered over each species' existing tuning. */
public final class CombatBalanceConfig {
    public static final double DEFAULT_HEALTH_MULTIPLIER = 1.45;
    public static final double DEFAULT_ARMOR_DAMAGE_BONUS = .85;
    public static final double DEFAULT_TOUGHNESS_DAMAGE_BONUS = .15;
    public static final double DEFAULT_MAX_ADDED_DAMAGE = 12;
    public static final double DEFAULT_KNOCKBACK_RESISTANCE_BONUS = .12;
    public static final double DEFAULT_PURSUIT_SPEED_BONUS = .08;
    public static final double DEFAULT_MELEE_REACH_BONUS = .6;
    public static final int DEFAULT_REJECTED_ATTACK_RETRY_TICKS = 4;

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Apply the shared combat profile without replacing individual species settings.")
            .define("enabled", true);
    private static final ModConfigSpec.DoubleValue HEALTH = B
            .comment("Multiplies species max health. Existing creatures keep their current health percentage.")
            .defineInRange("healthMultiplier", DEFAULT_HEALTH_MULTIPLIER, 1, 3);
    private static final ModConfigSpec.DoubleValue ARMOR = B
            .comment("Additional raw damage against players, scaled by their actual armor / 20.",
                    "Native armor, Protection, shields and other damage reductions still apply.",
                    "Armor-bypassing attacks and non-player victims receive no bonus.")
            .defineInRange("armorDamageBonus", DEFAULT_ARMOR_DAMAGE_BONUS, 0, 2);
    private static final ModConfigSpec.DoubleValue TOUGHNESS = B
            .comment("Additional raw damage scaled by player armor toughness / 12, only while armored.")
            .defineInRange("toughnessDamageBonus", DEFAULT_TOUGHNESS_DAMAGE_BONUS, 0, 1);
    private static final ModConfigSpec.DoubleValue DAMAGE_CAP = B
            .comment("Maximum extra raw damage per accepted native hit, before native mitigation.")
            .defineInRange("maxAddedDamage", DEFAULT_MAX_ADDED_DAMAGE, 0, 24);
    private static final ModConfigSpec.DoubleValue KNOCKBACK = B
            .comment("Small resistance addition for light creatures; never raises this profile above 0.45.",
                    "Creatures already at 0.5 resistance or above retain their existing resistance.")
            .defineInRange("knockbackResistanceBonus", DEFAULT_KNOCKBACK_RESISTANCE_BONUS, 0, .3);
    private static final ModConfigSpec.DoubleValue PURSUIT = B
            .comment("Movement-speed multiplier bonus only during an existing encounter with an armored player.",
                    "Does not acquire targets or alter neutral creatures' aggression.")
            .defineInRange("pursuitSpeedBonus", DEFAULT_PURSUIT_SPEED_BONUS, 0, .25);
    private static final ModConfigSpec.DoubleValue REACH = B
            .comment("Extra horizontal native melee reach in blocks; attacks still require line of sight.")
            .defineInRange("meleeReachBonus", DEFAULT_MELEE_REACH_BONUS, 0, 1.2);
    private static final ModConfigSpec.IntValue RETRY = B
            .comment("Retry delay when a prepared melee attack could not start; accepted attacks keep their full cooldown.")
            .defineInRange("rejectedAttackRetryTicks", DEFAULT_REJECTED_ATTACK_RETRY_TICKS, 2, 10);

    public static final ModConfigSpec SPEC = B.build();

    private CombatBalanceConfig() {}

    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    public static double healthMultiplier() { return SPEC.isLoaded() ? HEALTH.get() : DEFAULT_HEALTH_MULTIPLIER; }
    public static double armorDamageBonus() { return SPEC.isLoaded() ? ARMOR.get() : DEFAULT_ARMOR_DAMAGE_BONUS; }
    public static double toughnessDamageBonus() { return SPEC.isLoaded() ? TOUGHNESS.get() : DEFAULT_TOUGHNESS_DAMAGE_BONUS; }
    public static double maxAddedDamage() { return SPEC.isLoaded() ? DAMAGE_CAP.get() : DEFAULT_MAX_ADDED_DAMAGE; }
    public static double knockbackResistanceBonus() { return SPEC.isLoaded() ? KNOCKBACK.get() : DEFAULT_KNOCKBACK_RESISTANCE_BONUS; }
    public static double pursuitSpeedBonus() { return SPEC.isLoaded() ? PURSUIT.get() : DEFAULT_PURSUIT_SPEED_BONUS; }
    public static double meleeReachBonus() { return SPEC.isLoaded() ? REACH.get() : DEFAULT_MELEE_REACH_BONUS; }
    public static int rejectedAttackRetryTicks() { return SPEC.isLoaded() ? RETRY.get() : DEFAULT_REJECTED_ATTACK_RETRY_TICKS; }
}
