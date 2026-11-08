package com.ronm19.enderism;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Conservative, adjustable timing for Frost's three distinct combat slots. */
public final class FrostEndermanConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue STRIKE = B.defineInRange("frostStrikeCooldownTicks",60,40,120);
    private static final ModConfigSpec.IntValue PARALYSIS_WINDUP = B.defineInRange("paralysisWindupTicks",20,20,60);
    private static final ModConfigSpec.DoubleValue PARALYSIS_RANGE = B.defineInRange("paralysisRange",8.0,3.0,12.0);
    private static final ModConfigSpec.IntValue PARALYSIS_COOLDOWN = B.defineInRange("paralysisCooldownTicks",240,200,1200);
    private static final ModConfigSpec.IntValue HYPO_WINDUP = B.defineInRange("hypothermiaWindupTicks",24,20,60);
    private static final ModConfigSpec.DoubleValue HYPO_RADIUS = B.defineInRange("hypothermiaRadius",4.0,2.0,6.0);
    private static final ModConfigSpec.DoubleValue HYPO_DAMAGE = B.defineInRange("hypothermiaDamage",4.0,1.0,8.0);
    private static final ModConfigSpec.IntValue HYPO_SLOW = B.defineInRange("hypothermiaSlownessTicks",60,20,120);
    private static final ModConfigSpec.IntValue HYPO_COOLDOWN = B.defineInRange("hypothermiaCooldownTicks",400,300,1600);
    private static final ModConfigSpec.IntValue MAX_TARGETS = B.defineInRange("maximumHypothermiaTargets",8,1,16);
    private static final ModConfigSpec.IntValue RECOVERY = B.defineInRange("abilityRecoveryTicks",6,4,20);
    public static final ModConfigSpec SPEC = B.build();
    private FrostEndermanConfig() { }
    public static int frostStrikeCooldownTicks(){return SPEC.isLoaded()?STRIKE.get():60;}
    public static int paralysisWindupTicks(){return SPEC.isLoaded()?PARALYSIS_WINDUP.get():20;}
    public static double paralysisRange(){return SPEC.isLoaded()?PARALYSIS_RANGE.get():8;}
    public static int paralysisCooldownTicks(){return SPEC.isLoaded()?PARALYSIS_COOLDOWN.get():240;}
    public static int hypothermiaWindupTicks(){return SPEC.isLoaded()?HYPO_WINDUP.get():24;}
    public static double hypothermiaRadius(){return SPEC.isLoaded()?HYPO_RADIUS.get():4;}
    public static double hypothermiaDamage(){return SPEC.isLoaded()?HYPO_DAMAGE.get():4;}
    public static int hypothermiaSlownessTicks(){return SPEC.isLoaded()?HYPO_SLOW.get():60;}
    public static int hypothermiaCooldownTicks(){return SPEC.isLoaded()?HYPO_COOLDOWN.get():400;}
    public static int maximumHypothermiaTargets(){return SPEC.isLoaded()?MAX_TARGETS.get():8;}
    public static int recoveryTicks(){return SPEC.isLoaded()?RECOVERY.get():6;}
}
