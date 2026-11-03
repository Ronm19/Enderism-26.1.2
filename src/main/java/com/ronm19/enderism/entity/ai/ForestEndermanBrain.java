package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.ForestEndermanConfig;
import com.ronm19.enderism.effect.ForestRoots;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.ForestEnderman;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Three Forest abilities feed the existing native Brain's combat, movement, and perception actuators. */
public final class ForestEndermanBrain extends MastermindBrain {
    private final ForestEnderman forest;
    private @Nullable LivingEntity trackedThreat;
    private @Nullable Vec3 lastLocated;
    private Vec3 lastObservedVelocity = Vec3.ZERO;
    private long lastObservedAt;
    private long locatedUntil;
    private long nextPlayerScanAt;
    private long nextPathAt;
    private long nextBurstAt;
    private long burstUntil;
    private long revealedUntil;
    private long nextInterceptAt;
    private long nextVinesAt;
    private Ability ability = Ability.NONE;
    private @Nullable ServerPlayer abilityTarget;
    private long abilityEndsAt;

    public ForestEndermanBrain(ForestEnderman owner) { super(owner); this.forest = owner; }

    @Override
    protected int memoryTicks() { return ForestEndermanConfig.memoryTicks(); }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override
    protected @Nullable LivingEntity observeThreat() {
        LivingEntity visible = super.observeThreat();
        if (visible != null) return visible;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime() < this.nextPlayerScanAt) return null;
        this.nextPlayerScanAt = level.getGameTime() + ForestEndermanConfig.SENSOR_INTERVAL_TICKS;
        double range = Math.min(64.0, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE));
        double closest = range * range;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= closest && this.isVisibleThreat(player)) { visible = player; closest = distance; }
        }
        if (visible != null) this.rememberVisibleThreat(visible);
        return visible;
    }

    @Override
    protected void rememberVisibleThreat(LivingEntity threat) {
        super.rememberVisibleThreat(threat);
        long now = this.owner.level().getGameTime();
        Vec3 observed = threat.position();
        if (this.trackedThreat != threat) {
            this.lastObservedVelocity = Vec3.ZERO;
        } else if (this.lastLocated != null && now > this.lastObservedAt) {
            Vec3 sampled = observed.subtract(this.lastLocated).scale(1.0 / (now - this.lastObservedAt)).multiply(1, 0, 1);
            // Legitimate observed motion is sufficient; large relocations must not produce huge prediction vectors.
            this.lastObservedVelocity = sampled.lengthSqr() > 1.0 ? sampled.normalize() : sampled;
        }
        this.trackedThreat = threat;
        this.lastLocated = observed;
        this.lastObservedAt = now;
        this.locatedUntil = now + this.memoryTicks();
    }

    public Ability getAbility() { return this.ability; }
    public int getWarmupTicksRemaining() { return this.ability == Ability.NONE ? 0 : (int) Math.max(0L, this.abilityEndsAt - this.owner.level().getGameTime()); }
    public int getBurstTicksRemaining() { return (int) Math.max(0L, this.burstUntil - this.owner.level().getGameTime()); }
    public long nextVinesReadyAt() { return this.nextVinesAt; }
    public long nextBurstReadyAt() { return this.nextBurstAt; }
    public long nextInterceptReadyAt() { return this.nextInterceptAt; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.owner.level().getGameTime() < this.locatedUntil; }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }
    public Vec3 getLastObservedVelocity() { return this.lastObservedVelocity; }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (this.trackedThreat != null && !this.isLegalThreat(this.trackedThreat)) this.clearEncounter();
        if (this.ability != Ability.NONE) { this.tickVines(level, now); return; }
        LivingEntity visible = this.observeThreat();
        if (this.tickFloating()) {
            this.suspendNatureStalker();
            this.forest.setHuntState(ForestEnderman.HuntState.SEARCHING);
            return;
        }
        this.tickNatureStalker(visible, now);
        if (visible != null) {
            if (visible instanceof ServerPlayer player && this.beginVines(level, player, now)) return;
            this.tickVisibleCombat(visible);
            this.forest.setHuntState(this.forest.isCamouflaged() ? ForestEnderman.HuntState.STALKING : ForestEnderman.HuntState.PURSUING);
            return;
        }
        this.stopCombat();
        this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient();
            this.state = State.INVESTIGATE;
            this.forest.setHuntState(ForestEnderman.HuntState.SEARCHING);
            this.investigateLastSeen(level, now);
            return;
        }
        if (this.lastLocated != null || this.state != State.IDLE) this.clearEncounter();
        this.forest.setCamouflaged(this.forest.isInForest() && now >= this.revealedUntil && !this.owner.isInWater());
        this.forest.setHuntState(ForestEnderman.HuntState.IDLE);
        this.tickAmbient();
    }

    private void tickNatureStalker(@Nullable LivingEntity visible, long now) {
        boolean woodland = this.forest.isInForest() && !this.owner.isInWater();
        boolean close = visible != null && this.owner.distanceToSqr(visible) <= 36.0;
        this.forest.setCamouflaged(woodland && !close && now >= this.revealedUntil);
        boolean hunting = woodland && visible != null && this.owner.distanceToSqr(visible) > 36.0;
        if (!hunting || now >= this.burstUntil) this.removeBurst();
        if (hunting && this.burstUntil == 0 && now >= this.nextBurstAt) {
            this.burstUntil = now + ForestEndermanConfig.burstDurationTicks();
            this.nextBurstAt = now + ForestEndermanConfig.burstCooldownTicks();
            this.owner.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(new AttributeModifier(
                    ForestEnderman.BURST_MODIFIER_ID, ForestEndermanConfig.burstMultiplier(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void removeBurst() {
        this.burstUntil = 0;
        this.owner.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(ForestEnderman.BURST_MODIFIER_ID);
    }

    public void reveal() {
        this.revealedUntil = this.owner.level().getGameTime() + ForestEndermanConfig.REVEAL_TICKS;
        this.forest.setCamouflaged(false);
    }

    public void onHurt() {
        this.reveal();
        this.suspendNatureStalker();
    }

    /** Stop transient movement/cast state without resetting cooldowns or cancelling an already finite target root. */
    public void suspendNatureStalker() {
        this.removeBurst();
        this.finishVines();
        this.forest.setCamouflaged(false);
    }

    private boolean eligibleForVines(ServerPlayer player) {
        double range = ForestEndermanConfig.vinesRange();
        return this.isVisibleThreat(player) && this.owner.distanceToSqr(player) <= range * range
                && player.onGround() && !player.isInWater() && !this.owner.isInWater() && !player.isPassenger()
                && ForestRoots.canApply(player);
    }

    private boolean beginVines(ServerLevel level, ServerPlayer player, long now) {
        if (now < this.nextVinesAt || !this.eligibleForVines(player)) return false;
        this.nextVinesAt = now + ForestEndermanConfig.vinesCooldownTicks();
        this.ability = Ability.ENTANGLING_VINES;
        this.abilityTarget = player;
        this.abilityEndsAt = now + ForestEndermanConfig.vinesWarmupTicks();
        this.reveal();
        this.removeBurst();
        this.forest.setVineWindingUp(true);
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.freezeMovement();
        this.state = State.COMBAT;
        this.forest.setHuntState(ForestEnderman.HuntState.CASTING);
        this.forest.vinesWarning(level, player);
        return true;
    }

    private void tickVines(ServerLevel level, long now) {
        this.forest.setWarmupTicksRemaining(this.getWarmupTicksRemaining());
        ServerPlayer player = this.abilityTarget;
        if (player == null || !this.eligibleForVines(player)) { this.finishVines(); return; }
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.freezeMovement();
        this.forest.setCamouflaged(false);
        this.owner.getLookControl().setLookAt(player, 60.0F, 60.0F);
        if (now < this.abilityEndsAt) return;
        this.finishVines();
        if (player.hurtServer(level, this.owner.damageSources().mobAttack(this.owner), (float) ForestEndermanConfig.vinesDamage())
                && this.isLegalThreat(player) && ForestRoots.tryApply(player, this.forest)) this.forest.vinesBurst(level, player);
    }

    private void finishVines() {
        this.ability = Ability.NONE;
        this.abilityTarget = null;
        this.abilityEndsAt = 0;
        this.forest.setVineWindingUp(false);
    }

    private void freezeMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(Vec3.ZERO);
    }

    private void investigateLastSeen(ServerLevel level, long now) {
        Vec3 point = this.lastLocated;
        if (point == null) return;
        if (now - this.lastObservedAt >= 20 && now >= this.nextInterceptAt
                && this.owner.distanceToSqr(point) > 64.0 && this.owner.distanceToSqr(point) <= 32.0 * 32.0) {
            this.nextInterceptAt = now + ForestEndermanConfig.interceptCooldownTicks();
            Vec3 prediction = this.lastObservedVelocity.scale(8.0);
            if (prediction.lengthSqr() > 1.0E-6) prediction = prediction.normalize().scale(Math.max(2.0, Math.min(4.0, prediction.length())));
            this.forest.tryPursuitTeleport(point.add(prediction));
        }
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 45.0F, 45.0F);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.freezeMovement(); return; }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            if (level.hasChunkAt(BlockPos.containing(point))) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1.0);
        }
        // Preserve the fixed expiry and observed identity even if a path fails or finishes early.
    }

    @Override
    public void clearEncounter() {
        super.clearEncounter();
        this.suspendNatureStalker();
        ForestRoots.releaseOwnedBy(this.forest);
        this.trackedThreat = null;
        this.lastLocated = null;
        this.lastObservedVelocity = Vec3.ZERO;
        this.locatedUntil = 0;
        this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.forest.setHuntState(ForestEnderman.HuntState.IDLE);
    }

    public enum Ability { NONE, ENTANGLING_VINES }
}
