package com.ronm19.enderism.combat;

import com.ronm19.enderism.ZombieEndermanConfig;
import com.ronm19.enderism.entity.ZombieEnderman;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** A real damaging attack alerts existing nearby allies; receiving an alert never emits another one. */
public final class ZombieHordeSignal {
    // Neither these weak keys nor their values keep unloaded creatures or attackers alive.
    private static final Map<ZombieEnderman, RecentSignal> RECENT = new WeakHashMap<>();

    private ZombieHordeSignal() {}

    public static void registerEvents(IEventBus bus) { bus.addListener(ZombieHordeSignal::damaged); }

    public static void clear(ZombieEnderman source) { RECENT.remove(source); }

    private static void damaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ZombieEnderman source) || !(source.level() instanceof ServerLevel level)
                || source.isRemoved() || source.isNoAi()
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !source.isLegalInfectedTarget(attacker)) return;
        float accepted = event.getHealthDamage() + Math.max(0, event.getReduction(DamageContainer.Reduction.ABSORPTION));
        if (!Float.isFinite(accepted) || accepted <= 0) return;

        long now = level.getGameTime();
        double awareness = Math.min(64, source.getAttributeValue(Attributes.FOLLOW_RANGE));
        // Damage attribution is valid without line of sight. Hidden attackers disclose only the attack site.
        Vec3 observed = source.distanceToSqr(attacker) <= awareness * awareness
                && source.getSensing().hasLineOfSight(attacker) ? attacker.position() : source.position();
        if (!observed.isFinite()) return;
        source.getMastermindBrain().onReceivedHostileAttack(attacker, observed, now);

        RecentSignal previous = RECENT.get(source);
        if (previous != null && previous.attacker().get() == attacker
                && now >= previous.at() && now - previous.at() < ZombieEndermanConfig.signalAntiSpamTicks()) return;
        RECENT.put(source, new RecentSignal(new WeakReference<>(attacker), now));

        // Post fires after native death handling. A fatal first hit can still warn living nearby allies.
        source.showHordeSignal(level);
        double radius = ZombieEndermanConfig.signalRadius(), squared = radius * radius;
        var nearby = level.getEntitiesOfClass(ZombieEnderman.class, source.getBoundingBox().inflate(radius), ally ->
                ally != source && ally.level() == level && ally.isAlive() && !ally.isRemoved() && !ally.isNoAi()
                        && source.distanceToSqr(ally) <= squared && ally.isLegalInfectedTarget(attacker));
        nearby.sort(Comparator.<ZombieEnderman>comparingDouble(source::distanceToSqr)
                .thenComparing(ally -> ally.getUUID().toString()));
        int responding = 0;
        for (ZombieEnderman ally : nearby) {
            if (responding >= ZombieEndermanConfig.maxSignalRecipients()) break;
            if (ally.getMastermindBrain().receiveHordeSignal(source, attacker, observed, now)) responding++;
        }
    }

    private record RecentSignal(WeakReference<LivingEntity> attacker, long at) {}
}
