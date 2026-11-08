package com.ronm19.enderism.effect;

import com.ronm19.enderism.entity.GoldenEnderman;
import com.ronm19.enderism.registry.ModEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Finite movement hold used by Golden Judgment, with victim-wide recovery. */
public final class GoldenStun extends MobEffect {
    private static final int GOLD = 0xFFD25A;
    private static final DustParticleOptions GILDING = new DustParticleOptions(GOLD, 0.95F);

    public GoldenStun() {
        super(MobEffectCategory.HARMFUL, GOLD);
    }

    public static boolean canApply(LivingEntity target) {
        return target instanceof ServerPlayer player && !player.isPassenger() && ArcticStun.canApply(player);
    }

    public static boolean tryApply(LivingEntity target, GoldenEnderman source, int durationTicks, int protectionTicks) {
        return target instanceof ServerPlayer player
                && ArcticStun.tryApplyOwned(player, source, ModEffects.GOLDEN_STUN, durationTicks, protectionTicks);
    }

    public static void releaseOwnedBy(GoldenEnderman source) {
        ArcticStun.releaseAllOwned(source, ModEffects.GOLDEN_STUN);
    }

    static void showGilding(ServerLevel level, ServerPlayer player, int count) {
        level.sendParticles(GILDING, player.getX(), player.getY(0.5), player.getZ(),
                count, 0.25, 0.48, 0.25, 0.012);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        return ArcticStun.tickOwnedEffect(mob, ModEffects.GOLDEN_STUN);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        if (mob instanceof Player player) ArcticStun.clearPlayer(player);
    }
}
