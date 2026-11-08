package com.ronm19.enderism.effect;

import com.ronm19.enderism.entity.FrostEnderman;
import com.ronm19.enderism.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Two-second melee paralysis, alongside the reusable four-second ability hold. */
public final class FrostParalysis extends MobEffect {
    public static final int DURATION_TICKS = 80;
    public static final int MELEE_DURATION_TICKS = 40;

    public FrostParalysis() {
        super(MobEffectCategory.HARMFUL, 0x59E4FF);
    }

    public static boolean canApply(ServerPlayer player) {
        return ArcticStun.canApply(player);
    }

    public static boolean tryApply(ServerPlayer player, FrostEnderman source) {
        return ArcticStun.tryApplyOwned(player, source, ModEffects.FROST_PARALYSIS, DURATION_TICKS);
    }

    /** A warned ability may upgrade its own shorter melee hold, never another source's hold. */
    public static boolean tryApplyAbility(ServerPlayer player, FrostEnderman source) {
        return ArcticStun.tryRefreshOwnedOnHit(player, source, ModEffects.FROST_PARALYSIS, DURATION_TICKS);
    }

    public static boolean tryApplyOnHit(ServerPlayer player, FrostEnderman source) {
        return ArcticStun.tryRefreshOwnedOnHit(player, source, ModEffects.FROST_PARALYSIS, MELEE_DURATION_TICKS);
    }

    public static boolean isHeldBy(ServerPlayer player, FrostEnderman source) {
        return ArcticStun.isHeldByOwned(player, source, ModEffects.FROST_PARALYSIS);
    }

    public static void release(ServerPlayer player, FrostEnderman source) {
        ArcticStun.releaseOwned(player, source, ModEffects.FROST_PARALYSIS);
    }

    public static void releaseOwnedBy(FrostEnderman source) {
        ArcticStun.releaseAllOwned(source, ModEffects.FROST_PARALYSIS);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        return ArcticStun.tickOwnedEffect(mob, ModEffects.FROST_PARALYSIS);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        if (mob instanceof Player player) ArcticStun.clearPlayer(player);
    }
}
