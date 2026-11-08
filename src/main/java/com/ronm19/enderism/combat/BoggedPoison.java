package com.ronm19.enderism.combat;

import com.ronm19.enderism.entity.BoggedEnderman;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The three Bogged abilities share native Poison's finite refresh and immunity rules. */
public final class BoggedPoison {
    private BoggedPoison() {}

    public static boolean applyVenom(BoggedEnderman source, LivingEntity target, int duration, int amplifier) {
        if (!(source.level() instanceof ServerLevel) || !source.isAlive() || source.isRemoved() || source.isNoAi()
                || !source.isLegalVenomTarget(target) || duration <= 0) return false;
        int strength = Math.clamp(amplifier, 0, 3);
        int ticks = Math.clamp(duration, 1, 1200);
        MobEffectInstance current = target.getEffect(MobEffects.POISON);
        // Never add amplifiers together, downgrade a stronger poison, or create hidden weaker tails.
        if (current != null && (current.getAmplifier() > strength
                || current.getAmplifier() == strength && current.getDuration() >= ticks)) return false;
        return target.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, strength), source);
    }
}
