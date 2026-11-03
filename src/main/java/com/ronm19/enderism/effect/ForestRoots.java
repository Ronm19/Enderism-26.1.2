package com.ronm19.enderism.effect;

import com.ronm19.enderism.entity.ForestEnderman;
import com.ronm19.enderism.registry.ModEffects;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/** Brief vine restraint. Movement is held; looking, attacking, and using items remain available. */
public final class ForestRoots extends MobEffect {
    public static final int DURATION_TICKS = 40;

    public ForestRoots() {
        super(MobEffectCategory.HARMFUL, 0x75A93C);
    }

    public static boolean canApply(ServerPlayer player) {
        return ArcticStun.canApply(player);
    }

    public static boolean tryApply(ServerPlayer player, ForestEnderman source) {
        return ArcticStun.tryApplyOwned(player, source, ModEffects.FOREST_ROOTS, DURATION_TICKS);
    }

    public static boolean isHeldBy(ServerPlayer player, ForestEnderman source) {
        return ArcticStun.isHeldByOwned(player, source, ModEffects.FOREST_ROOTS);
    }

    public static void release(ServerPlayer player, ForestEnderman source) {
        ArcticStun.releaseOwned(player, source, ModEffects.FOREST_ROOTS);
    }

    public static void releaseOwnedBy(ForestEnderman source) {
        ArcticStun.releaseAllOwned(source, ModEffects.FOREST_ROOTS);
    }

    /** A few native vine fragments circle the lower body; this never places or changes blocks. */
    static void showVines(ServerLevel level, ServerPlayer player, int countPerPoint) {
        var particle = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.VINE.defaultBlockState());
        double phase = (level.getGameTime() % 40L) * 0.12;
        for (int point = 0; point < 4; point++) {
            double angle = phase + point * Math.PI * 0.5;
            level.sendParticles(particle,
                    player.getX() + Math.cos(angle) * 0.34,
                    player.getY() + 0.18 + (point & 1) * 0.45,
                    player.getZ() + Math.sin(angle) * 0.34,
                    countPerPoint, 0.025, 0.08, 0.025, 0.005);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        return ArcticStun.tickOwnedEffect(mob, ModEffects.FOREST_ROOTS);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        if (mob instanceof Player player) ArcticStun.clearPlayer(player);
    }
}
