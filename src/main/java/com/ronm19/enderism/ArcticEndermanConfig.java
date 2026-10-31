package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Arctic #003 tuning. Apart from the confirmed awareness ranges, these are provisional balance choices. */
public final class ArcticEndermanConfig {
    public static final double NORMAL_RANGE = 32.0;
    public static final double SNOW_RANGE = 42.0;
    public static final double DEFAULT_ATTACK_DAMAGE = 9.0;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    public static final int PATH_REFRESH_TICKS = 20;
    public static final int STRIKE_FREEZE_TICKS = 100;
    public static final double ABSOLUTE_ZERO_RANGE = 8.0;
    public static final double PURSUIT_SPEED = 1.10;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue STUN_DURATION = BUILDER
            .comment("Absolute Zero stun duration. Initial Arctic balance only; this is not Frost's four-second paralysis.")
            .defineInRange("stunDurationTicks", 40, 10, 120);
    private static final ModConfigSpec.IntValue ZERO_COOLDOWN = BUILDER
            .defineInRange("absoluteZeroCooldownTicks", 200, 80, 2400);
    private static final ModConfigSpec.IntValue WARMUP = BUILDER
            .comment("Readable pre-activation warning. Losing line of sight or leaving range interrupts it.")
            .defineInRange("abilityWarmupTicks", 20, 10, 100);
    private static final ModConfigSpec.IntValue OBSERVATION = BUILDER
            .comment("Winter's Judgment observation before the final freezing damage. Arctic remains still and does not melee.")
            .defineInRange("judgmentObserveTicks", 80, 20, 200);
    private static final ModConfigSpec.IntValue JUDGMENT_COOLDOWN = BUILDER
            .defineInRange("judgmentCooldownTicks", 600, 200, 4800);
    private static final ModConfigSpec.DoubleValue JUDGMENT_RANGE = BUILDER
            .comment("Provisional initiation range. Close range, existing cold, and uninterrupted warning provide counterplay.")
            .defineInRange("judgmentRange", 6.0, 2.5, 12.0);
    private static final ModConfigSpec.IntValue REQUIRED_FREEZE = BUILDER
            .comment("Provisional cold buildup required before Winter's Judgment can begin.")
            .defineInRange("judgmentRequiredFreezeTicks", 80, 0, 130);
    private static final ModConfigSpec.DoubleValue ATTACK_DAMAGE = BUILDER
            .defineInRange("attackDamage", DEFAULT_ATTACK_DAMAGE, 1.0, 30.0);
    private static final ModConfigSpec.IntValue MELEE_COOLDOWN = BUILDER
            .defineInRange("meleeCooldownTicks", 24, 20, 100);
    private static final ModConfigSpec.IntValue MEMORY = BUILDER
            .defineInRange("memoryTicks", 100, 40, 400);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ArcticEndermanConfig() {}

    public static int stunDurationTicks() { return STUN_DURATION.get(); }
    public static int absoluteZeroCooldownTicks() { return ZERO_COOLDOWN.get(); }
    public static int abilityWarmupTicks() { return WARMUP.get(); }
    public static int judgmentObserveTicks() { return OBSERVATION.get(); }
    public static int judgmentCooldownTicks() { return JUDGMENT_COOLDOWN.get(); }
    public static double judgmentRange() { return JUDGMENT_RANGE.get(); }
    public static int judgmentRequiredFreezeTicks() { return REQUIRED_FREEZE.get(); }
    public static double attackDamage() { return ATTACK_DAMAGE.get(); }
    public static int meleeCooldownTicks() { return MELEE_COOLDOWN.get(); }
    public static int memoryTicks() { return MEMORY.get(); }
}
