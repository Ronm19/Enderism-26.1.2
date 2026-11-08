package com.ronm19.enderism.effect;

import com.ronm19.enderism.BloodEndermanConfig;
import com.ronm19.enderism.entity.BloodEnderman;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Native credited deaths renew one finite Regeneration buff, never healing ordinary hits. */
public final class Bloodlust {
    // Weak victim identity covers duplicate native callbacks without retaining corpses, worlds,
    // or owners. A respawned player is a new instance and can legitimately die again.
    private static final ReferenceQueue<LivingEntity> COLLECTED = new ReferenceQueue<>();
    private static final Set<VictimIdentity> REWARDED = new HashSet<>();

    private Bloodlust() {}

    /** Called only from BloodEnderman.awardKillScore, after native death cancellation has passed. */
    public static synchronized boolean award(BloodEnderman source, Entity victim, DamageSource killingBlow) {
        while (COLLECTED.poll() instanceof VictimIdentity collected) REWARDED.remove(collected);
        if (!(source.level() instanceof ServerLevel level) || !source.isAlive() || source.isRemoved()
                || !(victim instanceof LivingEntity living) || victim instanceof ArmorStand || victim == source
                || victim.level() != level || living.isAlive() || living.getKillCredit() != source) return false;
        if (!REWARDED.add(new VictimIdentity(living))) return false;

        int duration = BloodEndermanConfig.regenerationDuration();
        int amplifier = BloodEndermanConfig.regenerationAmplifier();
        MobEffectInstance current = source.getEffect(MobEffects.REGENERATION);
        if (current != null && current.getAmplifier() == amplifier) duration = Math.max(duration, current.getDuration());
        // Vanilla handles effect compatibility and synchronization; repeated kills refresh the
        // same controlled amplifier rather than creating extra healing timers or health modifiers.
        source.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier, false, false, true), source);
        source.onBloodlust(level);
        return true;
    }

    private static final class VictimIdentity extends WeakReference<LivingEntity> {
        private final int hash;
        private VictimIdentity(LivingEntity victim) {
            super(victim, COLLECTED);
            this.hash = System.identityHashCode(victim);
        }
        @Override public int hashCode() { return this.hash; }
        @Override public boolean equals(Object other) {
            // Entity.equals compares numerical IDs, which native player respawn deliberately reuses.
            LivingEntity victim = this.get();
            return this == other || other instanceof VictimIdentity identity && victim != null && victim == identity.get();
        }
    }
}
