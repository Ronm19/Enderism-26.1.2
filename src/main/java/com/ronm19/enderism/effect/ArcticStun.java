package com.ronm19.enderism.effect;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.ArcticEnderman;
import com.ronm19.enderism.registry.ModEffects;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Synced temporary movement hold. It never changes attributes, camera, game mode, or abilities. */
public final class ArcticStun extends MobEffect {
    public static final int RECOVERY_TICKS = 20;
    public static final ResourceKey<DamageType> EXECUTION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "winters_judgment"));
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Recovery> RECOVERY = new HashMap<>();

    public ArcticStun() {
        super(MobEffectCategory.HARMFUL, 0x7FDDF5);
    }

    public static void registerEvents(IEventBus bus) {
        bus.addListener(ArcticStun::serverTick);
        bus.addListener(ArcticStun::logout);
        bus.addListener(ArcticStun::login);
        bus.addListener(ArcticStun::dimensionChanged);
        bus.addListener(ArcticStun::death);
        bus.addListener(ArcticStun::jump);
        // The native event bus rejects subscriptions to abstract parent events.
        bus.addListener((PlayerInteractEvent.RightClickItem event) -> interact(event));
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> interact(event));
        bus.addListener((PlayerInteractEvent.EntityInteract event) -> interact(event));
        bus.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> interact(event));
        bus.addListener((PlayerInteractEvent.LeftClickBlock event) -> interact(event));
        bus.addListener(ArcticStun::useStart);
        bus.addListener(ArcticStun::useTick);
        bus.addListener((EntityTeleportEvent.EnderPearl event) -> teleport(event));
        bus.addListener((EntityTeleportEvent.ItemConsumption event) -> teleport(event));
        bus.addListener(ArcticStun::serverStopped);
    }

    public static DamageSource executionDamageSource(ServerLevel level, ArcticEnderman source) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(EXECUTION_DAMAGE), source);
    }

    public static boolean isStunned(Player player) {
        return player.hasEffect(ModEffects.ARCTIC_STUN) || player.hasEffect(ModEffects.FROST_PARALYSIS);
    }

    public static boolean canApply(ServerPlayer player) {
        if (!eligible(player) || HOLDS.containsKey(player.getUUID()) || isStunned(player)) return false;
        Recovery recovery = RECOVERY.get(player.getUUID());
        return recovery == null || recovery.server.get() != player.level().getServer()
                || player.level().getServer().getTickCount() >= recovery.until;
    }

    public static boolean tryApply(ServerPlayer player, ArcticEnderman source, int durationTicks) {
        return tryApplyOwned(player, source, ModEffects.ARCTIC_STUN, durationTicks);
    }

    /** Shared ownership/lifecycle support; species attack rules are deliberately outside this effect engine. */
    static boolean tryApplyOwned(ServerPlayer player, AbstractEnderman source, Holder<MobEffect> effect, int durationTicks) {
        if (!canApply(player) || !source.isAlive() || source.isRemoved() || source.level() != player.level()
                || player.isPassenger() || durationTicks < 1) return false;
        ServerLevel level = (ServerLevel) player.level();
        int duration = Math.min(1200, durationTicks);
        Hold hold = new Hold(player, source, effect, level, player.position(), level.getGameTime() + duration);
        // Reuse the existing operational safety checks before reporting a successful application.
        if (!valid(hold)) return false;
        HOLDS.put(player.getUUID(), hold);
        if (!player.addEffect(new MobEffectInstance(effect, duration, 0, false, false, true), source)
                || !player.hasEffect(effect)) {
            HOLDS.remove(player.getUUID());
            return false;
        }
        player.stopUsingItem();
        stopVelocity(player);
        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 8, .3, .7, .3, .02);
        return true;
    }

    public static boolean isHeldBy(ServerPlayer player, ArcticEnderman source) {
        return isHeldByOwned(player, source, ModEffects.ARCTIC_STUN);
    }

    static boolean isHeldByOwned(ServerPlayer player, AbstractEnderman source, Holder<MobEffect> effect) {
        Hold hold = HOLDS.get(player.getUUID());
        return hold != null && hold.player.get() == player && hold.source.get() == source
                && hold.effect.equals(effect) && valid(hold) && player.hasEffect(effect);
    }

    public static void release(ServerPlayer player, ArcticEnderman source) {
        releaseOwned(player, source, ModEffects.ARCTIC_STUN);
    }

    static void releaseOwned(ServerPlayer player, AbstractEnderman source, Holder<MobEffect> effect) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold != null && hold.player.get() == player && hold.source.get() == source && hold.effect.equals(effect))
            release(player.getUUID(), hold);
    }

    public static void releaseOwnedBy(ArcticEnderman source) {
        releaseAllOwned(source, ModEffects.ARCTIC_STUN);
    }

    static void releaseAllOwned(AbstractEnderman source, Holder<MobEffect> effect) {
        for (var entry : Map.copyOf(HOLDS).entrySet()) {
            Hold hold = entry.getValue();
            if (hold.source.get() == source && hold.effect.equals(effect)) release(entry.getKey(), hold);
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                && !player.isCreative() && !player.isSpectator();
    }

    private static boolean valid(Hold hold) {
        ServerPlayer player = hold.player.get();
        AbstractEnderman source = hold.source.get();
        return player != null && source != null && eligible(player) && source.isAlive() && !source.isRemoved()
                && player.level() == hold.level && source.level() == hold.level
                && hold.level.getGameTime() < hold.until && player.distanceToSqr(source) <= 64 * 64;
    }

    private static void serverTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        RECOVERY.entrySet().removeIf(entry -> entry.getValue().server.get() != event.getServer() || now >= entry.getValue().until);
        for (var entry : Map.copyOf(HOLDS).entrySet()) {
            Hold hold = entry.getValue();
            ServerPlayer player = hold.player.get();
            if (!valid(hold) || player == null || !player.hasEffect(hold.effect)) {
                release(entry.getKey(), hold);
                continue;
            }
            // Commands and other substantial forced relocations are interruption, never a snap-back trap.
            double displacement = player.position().distanceToSqr(hold.anchor);
            if (displacement > 16.0 || player.isPassenger()) {
                release(entry.getKey(), hold);
                continue;
            }
            player.stopUsingItem();
            player.setSprinting(false);
            stopVelocity(player);
            player.resetFallDistance();
            if (displacement > 1.0E-6) {
                player.connection.teleport(hold.anchor.x, hold.anchor.y, hold.anchor.z, player.getYRot(), player.getXRot());
            }
        }
    }

    private static void stopVelocity(Player player) {
        if (player.getDeltaMovement().lengthSqr() > 1.0E-8) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        }
    }

    private static void release(UUID id, Hold hold) {
        if (!HOLDS.remove(id, hold)) return;
        ServerPlayer player = hold.player.get();
        if (player != null) {
            player.removeEffect(hold.effect);
            MinecraftServer server = player.level().getServer();
            RECOVERY.put(id, new Recovery(new WeakReference<>(server), server.getTickCount() + RECOVERY_TICKS));
            if (player.isAlive() && player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 3, .2, .5, .2, .01);
            }
        }
    }

    static void clearPlayer(Player player) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold != null) release(player.getUUID(), hold);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.removeEffect(ModEffects.ARCTIC_STUN);
            serverPlayer.removeEffect(ModEffects.FROST_PARALYSIS);
        }
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearPlayer(event.getEntity()); }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) { clearPlayer(event.getEntity()); }
    private static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) { clearPlayer(event.getEntity()); }
    private static void death(LivingDeathEvent event) { if (event.getEntity() instanceof Player player) clearPlayer(player); }
    private static void jump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && isStunned(player)) stopVelocity(player);
    }
    private static void interact(PlayerInteractEvent event) {
        if (isStunned(event.getEntity()) && event instanceof ICancellableEvent cancellable) cancellable.setCanceled(true);
    }
    private static void useStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && isStunned(player)) event.setCanceled(true);
    }
    private static void useTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof Player player && isStunned(player)) event.setCanceled(true);
    }
    private static void teleport(EntityTeleportEvent event) {
        if ((event instanceof EntityTeleportEvent.EnderPearl || event instanceof EntityTeleportEvent.ItemConsumption)
                && event.getEntity() instanceof Player player && isStunned(player)) event.setCanceled(true);
    }
    private static void serverStopped(ServerStoppedEvent event) { HOLDS.clear(); RECOVERY.clear(); }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        return tickOwnedEffect(mob, ModEffects.ARCTIC_STUN);
    }

    static boolean tickOwnedEffect(LivingEntity mob, Holder<MobEffect> effect) {
        if (!(mob instanceof ServerPlayer player)) return false;
        Hold hold = HOLDS.get(player.getUUID());
        return hold != null && hold.player.get() == player && hold.effect.equals(effect) && valid(hold);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        if (mob instanceof Player player) clearPlayer(player);
    }

    private record Hold(WeakReference<ServerPlayer> player, WeakReference<AbstractEnderman> source,
                        Holder<MobEffect> effect, ServerLevel level, Vec3 anchor, long until) {
        private Hold(ServerPlayer player, AbstractEnderman source, Holder<MobEffect> effect,
                     ServerLevel level, Vec3 anchor, long until) {
            this(new WeakReference<>(player), new WeakReference<>(source), effect, level, anchor, until);
        }
    }
    private record Recovery(WeakReference<MinecraftServer> server, int until) {
    }
}
