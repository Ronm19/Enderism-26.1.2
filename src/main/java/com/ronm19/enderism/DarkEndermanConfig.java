package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Adjustable initial balance. The confirmed Wither application interval is deliberately not configurable. */
public final class DarkEndermanConfig {
    public static final double DEFAULT_HEALTH = 40.0;
    public static final double DEFAULT_ATTACK_DAMAGE = 6.0;
    public static final double DEFAULT_MOVEMENT_SPEED = .28;
    public static final double DEFAULT_FOLLOW_RANGE = 32.0;
    public static final int WITHER_APPLICATION_COOLDOWN_TICKS = 400;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue HEALTH = B.defineInRange("health",DEFAULT_HEALTH,20.0,80.0);
    private static final ModConfigSpec.DoubleValue DAMAGE = B.defineInRange("attackDamage",DEFAULT_ATTACK_DAMAGE,1.0,16.0);
    private static final ModConfigSpec.DoubleValue SPEED = B.defineInRange("movementSpeed",DEFAULT_MOVEMENT_SPEED,.1,.5);
    private static final ModConfigSpec.DoubleValue RANGE = B.defineInRange("followRange",DEFAULT_FOLLOW_RANGE,8.0,64.0);
    private static final ModConfigSpec.IntValue MELEE = B.defineInRange("meleeCooldownTicks",24,16,80);
    private static final ModConfigSpec.IntValue MEMORY = B.defineInRange("memoryTicks",120,40,400);
    private static final ModConfigSpec.DoubleValue BARRAGE_MIN = B.defineInRange("barrageMinimumRange",4.0,2.0,12.0);
    private static final ModConfigSpec.DoubleValue BARRAGE_MAX = B.defineInRange("barrageMaximumRange",24.0,8.0,40.0);
    private static final ModConfigSpec.IntValue BARRAGE_WARMUP = B.defineInRange("barrageWindupTicks",12,5,60);
    private static final ModConfigSpec.IntValue BARRAGE_SHOTS = B.defineInRange("barrageProjectileCount",3,1,5);
    private static final ModConfigSpec.IntValue BARRAGE_SPACING = B.defineInRange("barrageShotIntervalTicks",8,5,30);
    private static final ModConfigSpec.IntValue BARRAGE_COOLDOWN = B.defineInRange("barrageCooldownTicks",100,60,1200);
    private static final ModConfigSpec.DoubleValue BOLT_DAMAGE = B.defineInRange("projectileDamage",3.0,1.0,8.0);
    private static final ModConfigSpec.DoubleValue BOLT_SPEED = B.defineInRange("projectileSpeed",.9,.3,1.8);
    private static final ModConfigSpec.IntValue BLINDNESS = B.defineInRange("blindnessDurationTicks",60,20,160);
    private static final ModConfigSpec.DoubleValue WITHER_RANGE = B.defineInRange("witherRange",18.0,4.0,32.0);
    private static final ModConfigSpec.IntValue WITHER_WARMUP = B.defineInRange("witherWindupTicks",20,10,80);
    private static final ModConfigSpec.DoubleValue WITHER_DAMAGE = B.defineInRange("witherProjectileDamage",2.0,1.0,6.0);
    private static final ModConfigSpec.DoubleValue WITHER_SPEED = B.defineInRange("witherProjectileSpeed",.75,.3,1.5);
    private static final ModConfigSpec.IntValue WITHER_DURATION = B.defineInRange("witherDurationTicks",100,20,200);
    private static final ModConfigSpec.IntValue WITHER_AMPLIFIER = B.defineInRange("witherAmplifier",0,0,1);
    private static final ModConfigSpec.IntValue WITHER_RETRY = B.comment("Recovery after an attempted cast. Successful Wither application separately enforces exactly 400 ticks.")
            .defineInRange("witherAttemptRecoveryTicks",100,40,400);
    private static final ModConfigSpec.IntValue REPOSITION_COOLDOWN = B.defineInRange("repositionCooldownTicks",120,60,1200);
    private static final ModConfigSpec.DoubleValue REPOSITION_RANGE = B.defineInRange("repositionActivationRange",24.0,8.0,32.0);
    private static final ModConfigSpec.DoubleValue REPOSITION_DISTANCE = B.defineInRange("repositionDistance",4.0,3.0,6.0);
    public static final ModConfigSpec SPEC = B.build();
    private DarkEndermanConfig() {}
    public static double health(){return SPEC.isLoaded()?HEALTH.get():DEFAULT_HEALTH;}
    public static double attackDamage(){return SPEC.isLoaded()?DAMAGE.get():DEFAULT_ATTACK_DAMAGE;}
    public static double movementSpeed(){return SPEC.isLoaded()?SPEED.get():DEFAULT_MOVEMENT_SPEED;}
    public static double followRange(){return SPEC.isLoaded()?RANGE.get():DEFAULT_FOLLOW_RANGE;}
    public static int meleeCooldownTicks(){return SPEC.isLoaded()?MELEE.get():24;}
    public static int memoryTicks(){return SPEC.isLoaded()?MEMORY.get():120;}
    public static double barrageMinimumRange(){return SPEC.isLoaded()?BARRAGE_MIN.get():4;}
    public static double barrageMaximumRange(){return SPEC.isLoaded()?BARRAGE_MAX.get():24;}
    public static int barrageWindupTicks(){return SPEC.isLoaded()?BARRAGE_WARMUP.get():12;}
    public static int barrageProjectileCount(){return SPEC.isLoaded()?BARRAGE_SHOTS.get():3;}
    public static int barrageShotIntervalTicks(){return SPEC.isLoaded()?BARRAGE_SPACING.get():8;}
    public static int barrageCooldownTicks(){return SPEC.isLoaded()?BARRAGE_COOLDOWN.get():100;}
    public static double projectileDamage(){return SPEC.isLoaded()?BOLT_DAMAGE.get():3;}
    public static double projectileSpeed(){return SPEC.isLoaded()?BOLT_SPEED.get():.9;}
    public static int projectileLifetimeTicks(){return 60;}
    public static int blindnessDurationTicks(){return SPEC.isLoaded()?BLINDNESS.get():60;}
    public static double witherRange(){return SPEC.isLoaded()?WITHER_RANGE.get():18;}
    public static int witherWindupTicks(){return SPEC.isLoaded()?WITHER_WARMUP.get():20;}
    public static double witherProjectileDamage(){return SPEC.isLoaded()?WITHER_DAMAGE.get():2;}
    public static double witherProjectileSpeed(){return SPEC.isLoaded()?WITHER_SPEED.get():.75;}
    public static int witherDurationTicks(){return SPEC.isLoaded()?WITHER_DURATION.get():100;}
    public static int witherAmplifier(){return SPEC.isLoaded()?WITHER_AMPLIFIER.get():0;}
    public static int witherAttemptRecoveryTicks(){return SPEC.isLoaded()?WITHER_RETRY.get():100;}
    public static int repositionCooldownTicks(){return SPEC.isLoaded()?REPOSITION_COOLDOWN.get():120;}
    public static double repositionActivationRange(){return SPEC.isLoaded()?REPOSITION_RANGE.get():24;}
    public static double repositionDistance(){return SPEC.isLoaded()?REPOSITION_DISTANCE.get():4;}
}
