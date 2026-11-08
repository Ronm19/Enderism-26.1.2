package com.ronm19.enderism.effect;

import com.ronm19.enderism.DemonEndermanConfig;
import com.ronm19.enderism.entity.DemonEnderman;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/** A finite, first-owner Ash meter. Its only damage is part of the triggering native melee hit. */
public final class DemonAsh {
    private static final Map<UUID, Meter> METERS = new HashMap<>();
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(0xCB243B, 1.0F);

    private DemonAsh() {}

    public static void registerEvents(IEventBus bus) {
        bus.addListener(DemonAsh::acceptedDamage);
        bus.addListener(DemonAsh::serverTick);
        bus.addListener(DemonAsh::logout);
        bus.addListener(DemonAsh::login);
        bus.addListener(DemonAsh::dimensionChanged);
        bus.addListener(DemonAsh::death);
        bus.addListener(DemonAsh::serverStopping);
        bus.addListener(DemonAsh::serverStopped);
    }

    /**
     * Reserve one hit before native melee damage. Only its caller may use the returned bonus,
     * temporarily, on that same native attack. A canceled hit must finish with {@code false}.
     */
    public static @Nullable Hit begin(LivingEntity target, DemonEnderman source) {
        if (!(target.level() instanceof ServerLevel level) || !eligible(target)
                || !source.isAlive() || source.isRemoved() || source == target || source.level() != level) return null;
        Meter meter = METERS.get(target.getUUID());
        if (meter != null && (!valid(meter) || meter.target.get() != target)) {
            METERS.remove(target.getUUID(), meter);
            meter = null;
        }
        long now = level.getGameTime();
        boolean newMeter = meter == null;
        if (newMeter) {
            meter = new Meter(target, source, level, now + expiration());
            METERS.put(target.getUUID(), meter);
        }
        // A single reservation prevents nested attack callbacks from consuming one threshold twice.
        // Other Demons still deal ordinary damage; they cannot take over a live owner's meter.
        if (meter.source.get() != source || meter.reservation != null || now < meter.readyAt) return null;
        int threshold = Math.clamp(DemonEndermanConfig.ashThreshold(), 1, 64);
        int previous = Math.min(meter.stacks, threshold - 1);
        Hit hit = new Hit(meter, newMeter, previous, threshold,
                previous + 1 >= threshold ? Math.max(0, DemonEndermanConfig.ashBurstDamage()) : 0,
                previous + 1 >= threshold);
        meter.reservation = hit;
        return hit;
    }

    /** Commit only an attack that actually reduced health or absorption; false rolls back completely. */
    public static void finish(@Nullable Hit hit, boolean acceptedHealthOrAbsorptionDamage) {
        if (hit == null || hit.finished) return;
        hit.finished = true;
        Meter meter = hit.meter;
        LivingEntity target = meter.target.get();
        DemonEnderman source = meter.source.get();
        ServerLevel level = meter.level.get();
        if (target == null || METERS.get(target.getUUID()) != meter || meter.reservation != hit) return;
        meter.reservation = null;
        // A lethal qualifying hit may complete its eruption before the dead victim is discarded.
        boolean sameEncounter = level != null && target.level() == level && !target.isRemoved()
                && source != null && source.level() == level && source.isAlive() && !source.isRemoved()
                && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
        if (!acceptedHealthOrAbsorptionDamage || !hit.nativeAcceptedDamage || !sameEncounter) {
            if (hit.newMeter || meter.clearAfterHit || !valid(meter)) METERS.remove(target.getUUID(), meter);
            return;
        }
        long now = level.getGameTime();
        meter.expiresAt = now + expiration();
        if (hit.eruption) {
            // Damage was already included in the accepted native attack. Never issue another hurt call.
            meter.stacks = 0;
            meter.readyAt = now + Math.max(0, DemonEndermanConfig.ashRetrigger());
            eruption(level, target);
        } else {
            meter.stacks = hit.previousStacks + 1;
            buildup(level, target, meter.stacks, hit.threshold);
        }
        if (meter.clearAfterHit || !eligible(target)) METERS.remove(target.getUUID(), meter);
    }

    /** The owner's useful encounter memory, without granting knowledge of other Demons' stacks. */
    public static int stacks(LivingEntity target, DemonEnderman source) {
        Meter meter = METERS.get(target.getUUID());
        if (meter == null || meter.target.get() != target || meter.source.get() != source) return 0;
        if (!valid(meter)) {
            METERS.remove(target.getUUID(), meter);
            return 0;
        }
        return meter.stacks;
    }

    public static void release(LivingEntity target, DemonEnderman source) {
        Meter meter = METERS.get(target.getUUID());
        if (meter != null && meter.target.get() == target && meter.source.get() == source)
            METERS.remove(target.getUUID(), meter);
    }

    public static void releaseOwnedBy(DemonEnderman source) {
        METERS.entrySet().removeIf(entry -> entry.getValue().source.get() == source);
    }

    private static int expiration() {
        return Math.max(1, DemonEndermanConfig.ashExpiration());
    }

    private static boolean eligible(LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && (!(target instanceof ServerPlayer player)
                || !player.hasDisconnected() && !player.isCreative());
    }

    private static boolean valid(Meter meter) {
        LivingEntity target = meter.target.get();
        DemonEnderman source = meter.source.get();
        ServerLevel level = meter.level.get();
        return target != null && source != null && level != null && eligible(target)
                && source.isAlive() && !source.isRemoved() && target.level() == level && source.level() == level
                && level.getGameTime() < meter.expiresAt;
    }

    private static void buildup(ServerLevel level, LivingEntity target, int stacks, int threshold) {
        level.sendParticles(ParticleTypes.ASH, target.getX(), target.getY() + .8, target.getZ(),
                2 + 4 * stacks / threshold, .24, .4, .24, .018);
        level.sendParticles(CRIMSON, target.getX(), target.getY() + .8, target.getZ(),
                2, .24, .3, .24, .015);
    }

    private static void eruption(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.ASH, target.getX(), target.getY() + .8, target.getZ(),
                24, .55, .6, .55, .12);
        level.sendParticles(CRIMSON, target.getX(), target.getY() + .8, target.getZ(),
                14, .6, .5, .6, .08);
        level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + .7, target.getZ(),
                7, .4, .35, .4, .055);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, .55F, 1.35F);
    }

    private static void acceptedDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Meter meter = METERS.get(target.getUUID());
        if (meter == null || meter.target.get() != target || meter.reservation == null) return;
        DemonEnderman source = meter.source.get();
        if (source != null && event.getSource().getEntity() == source
                && event.getSource().getDirectEntity() == source && event.getSource().is(DamageTypes.MOB_ATTACK)
                && event.getInflictedDamage() > 0) meter.reservation.nativeAcceptedDamage = true;
    }

    private static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        METERS.entrySet().removeIf(entry -> {
            ServerLevel level = entry.getValue().level.get();
            return level == null || level.getServer() == server && !valid(entry.getValue());
        });
    }

    private static void clearTarget(LivingEntity target) {
        Meter meter = METERS.get(target.getUUID());
        if (meter != null && meter.target.get() == target) METERS.remove(target.getUUID(), meter);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearTarget(event.getEntity()); }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) { clearTarget(event.getEntity()); }
    private static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) { clearTarget(event.getEntity()); }

    private static void death(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        Meter meter = METERS.get(target.getUUID());
        if (meter != null && meter.target.get() == target) {
            if (meter.reservation != null) meter.clearAfterHit = true;
            else METERS.remove(target.getUUID(), meter);
        }
        if (target instanceof DemonEnderman source) releaseOwnedBy(source);
    }

    private static void serverStopping(ServerStoppingEvent event) { clearServer(event.getServer()); }
    private static void serverStopped(ServerStoppedEvent event) { clearServer(event.getServer()); }

    private static void clearServer(MinecraftServer server) {
        METERS.entrySet().removeIf(entry -> {
            ServerLevel level = entry.getValue().level.get();
            return level == null || level.getServer() == server;
        });
    }

    public static final class Hit {
        private final Meter meter;
        private final boolean newMeter;
        private final int previousStacks;
        private final int threshold;
        private final double bonusDamage;
        private final boolean eruption;
        private boolean nativeAcceptedDamage;
        private boolean finished;

        private Hit(Meter meter, boolean newMeter, int previousStacks, int threshold,
                    double bonusDamage, boolean eruption) {
            this.meter = meter;
            this.newMeter = newMeter;
            this.previousStacks = previousStacks;
            this.threshold = threshold;
            this.bonusDamage = bonusDamage;
            this.eruption = eruption;
        }

        public double bonusDamage() { return bonusDamage; }
    }

    private static final class Meter {
        private final WeakReference<LivingEntity> target;
        private final WeakReference<DemonEnderman> source;
        private final WeakReference<ServerLevel> level;
        private long expiresAt;
        private long readyAt;
        private int stacks;
        private @Nullable Hit reservation;
        private boolean clearAfterHit;

        private Meter(LivingEntity target, DemonEnderman source, ServerLevel level, long expiresAt) {
            this.target = new WeakReference<>(target);
            this.source = new WeakReference<>(source);
            this.level = new WeakReference<>(level);
            this.expiresAt = expiresAt;
        }
    }
}
