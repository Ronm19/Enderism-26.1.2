package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance settings are captured once per Cataclysm so an in-progress cast stays consistent. */
public final class InfernalMeteorConfig {
    private static final Settings DEFAULTS = new Settings(24.0, 24, 4, 12, 6.0, 2.7, 0.75,
            36, 2.8, 15.0, 6.0, 1.05, 380);
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue RANGE =
            BUILDER.defineInRange("activationRange", DEFAULTS.activationRange(), 8.0, 32.0);
    private static final ModConfigSpec.IntValue WINDUP =
            BUILDER.defineInRange("windupTicks", DEFAULTS.windupTicks(), 12, 80);
    private static final ModConfigSpec.IntValue COUNT =
            BUILDER.defineInRange("smallMeteorCount", DEFAULTS.smallCount(), 2, 5);
    private static final ModConfigSpec.IntValue SPACING =
            BUILDER.defineInRange("barrageIntervalTicks", DEFAULTS.barrageInterval(), 8, 32);
    private static final ModConfigSpec.DoubleValue DAMAGE =
            BUILDER.defineInRange("smallMeteorDamage", DEFAULTS.smallDamage(), 2.0, 10.0);
    private static final ModConfigSpec.DoubleValue RADIUS =
            BUILDER.defineInRange("smallMeteorRadius", DEFAULTS.smallRadius(), 1.5, 4.0);
    private static final ModConfigSpec.DoubleValue SPEED =
            BUILDER.defineInRange("smallMeteorDescentSpeed", DEFAULTS.smallSpeed(), 0.4, 1.25);
    private static final ModConfigSpec.IntValue WARNING =
            BUILDER.defineInRange("finalWarningTicks", DEFAULTS.finalWarning(), 25, 80);
    private static final ModConfigSpec.DoubleValue FINAL_SIZE =
            BUILDER.defineInRange("finalMeteorDiameter", DEFAULTS.finalSize(), 2.0, 3.5);
    private static final ModConfigSpec.DoubleValue FINAL_DAMAGE =
            BUILDER.defineInRange("finalMeteorDamage", DEFAULTS.finalDamage(), 12.0, 20.0);
    private static final ModConfigSpec.DoubleValue FINAL_RADIUS =
            BUILDER.defineInRange("finalMeteorRadius", DEFAULTS.finalRadius(), 5.0, 8.0);
    private static final ModConfigSpec.DoubleValue FINAL_SPEED =
            BUILDER.defineInRange("finalMeteorDescentSpeed", DEFAULTS.finalSpeed(), 0.55, 1.5);
    private static final ModConfigSpec.IntValue COOLDOWN =
            BUILDER.defineInRange("cooldownTicks", DEFAULTS.cooldownTicks(), 220, 1600);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private InfernalMeteorConfig() {
    }

    public static Settings settings() {
        return SPEC.isLoaded() ? new Settings(RANGE.get(), WINDUP.get(), COUNT.get(), SPACING.get(),
                DAMAGE.get(), RADIUS.get(), SPEED.get(), WARNING.get(), FINAL_SIZE.get(), FINAL_DAMAGE.get(),
                FINAL_RADIUS.get(), FINAL_SPEED.get(), COOLDOWN.get()) : DEFAULTS;
    }

    public record Settings(double activationRange, int windupTicks, int smallCount, int barrageInterval,
                           double smallDamage, double smallRadius, double smallSpeed, int finalWarning,
                           double finalSize, double finalDamage, double finalRadius, double finalSpeed,
                           int cooldownTicks) {
    }
}
