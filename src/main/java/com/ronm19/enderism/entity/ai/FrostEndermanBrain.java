package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.effect.FrostParalysis;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.FrostEnderman;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Sampled cold awareness feeding the existing native Brain's pursuit and investigation actuators. */
public final class FrostEndermanBrain extends MastermindBrain {
    private static final int SENSOR_INTERVAL_TICKS = 10;
    private static final int LOCATION_MEMORY_TICKS = 100;
    private final FrostEnderman frost;
    private @Nullable ServerPlayer sensedPlayer;
    private @Nullable Vec3 lastLocated;
    private long locatedUntil;
    private long nextDetectionAt;
    private long nextPathAt;
    private double awarenessRange = FrostEnderman.NORMAL_DETECTION_RANGE;

    public FrostEndermanBrain(FrostEnderman owner) {
        super(owner);
        this.frost = owner;
    }

    @Override
    protected int memoryTicks() { return LOCATION_MEMORY_TICKS; }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override
    protected boolean isVisibleThreat(@Nullable LivingEntity target) {
        // The shared generic helper caps its range at64; Frost's approved storm range is74.
        return this.isLegalThreat(target) && this.owner.distanceToSqr(target) <= this.awarenessRange * this.awarenessRange
                && this.owner.getSensing().hasLineOfSight(target);
    }

    public int getSensedPlayerId() { return this.sensedPlayer == null ? -1 : this.sensedPlayer.getId(); }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.owner.level().getGameTime() < this.locatedUntil; }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (this.sensedPlayer != null && !this.isLegalThreat(this.sensedPlayer)) this.clearEncounter();
        if (now >= this.nextDetectionAt) {
            this.nextDetectionAt = now + SENSOR_INTERVAL_TICKS;
            this.samplePresence(level, now);
        }
        LivingEntity visible = this.isVisibleThreat(this.sensedPlayer) ? this.sensedPlayer : this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible);
            this.locate(visible.position(), now);
        }
        if (this.tickFloating()) {
            this.frost.setHuntState(FrostEnderman.HuntState.SEARCHING);
            return;
        }
        if (visible != null) {
            this.tickVisibleCombat(visible);
            this.frost.setHuntState(FrostEnderman.HuntState.PURSUING);
            return;
        }
        this.stopCombat();
        this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient();
            this.state = State.INVESTIGATE;
            this.investigatePoint(level, this.lastLocated, now);
            this.frost.setHuntState(FrostEnderman.HuntState.SEARCHING);
            return;
        }
        if (this.lastLocated != null && now >= this.locatedUntil) {
            this.lastLocated = null;
            this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            this.owner.getNavigation().stop();
        }
        if (this.sensedPlayer != null) {
            // Stationary/hidden presence remains awareness, never a continuously updated live attack target.
            this.stopAmbient();
            this.owner.getNavigation().stop();
            this.state = State.INVESTIGATE;
            this.frost.setHuntState(FrostEnderman.HuntState.SENSING);
            return;
        }
        if (this.state != State.IDLE) this.clearEncounter();
        this.frost.setHuntState(FrostEnderman.HuntState.IDLE);
        this.tickAmbient();
    }

    private void samplePresence(ServerLevel level, long now) {
        this.awarenessRange = FrostEnderman.detectionRange(level, this.owner.blockPosition());
        ServerPlayer previous = this.sensedPlayer;
        ServerPlayer candidate = previous;
        double rangeSquared = this.awarenessRange * this.awarenessRange;
        if (!this.isLegalThreat(candidate) || this.owner.distanceToSqr(candidate) > rangeSquared) {
            candidate = null;
            double closest = rangeSquared;
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(this.awarenessRange))) {
                double distance = this.owner.distanceToSqr(player);
                if (distance <= closest && this.isLegalThreat(player)) {
                    candidate = player;
                    closest = distance;
                }
            }
        }
        this.sensedPlayer = candidate;
        if (candidate != previous && candidate != null) {
            // A single detected location can be investigated, but hidden positions are not refreshed by each scan.
            this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            this.locate(candidate.position(), now);
            this.nextPathAt = now;
        }
    }

    private void locate(Vec3 position, long now) {
        this.lastLocated = position;
        this.locatedUntil = now + this.memoryTicks();
        WalkTarget previous = this.owner.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (previous == null || !previous.getTarget().currentPosition().equals(position))
            previous = new WalkTarget(new BlockPosTracker(position), 1.0F, 1);
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, previous, this.memoryTicks());
    }

    private void investigatePoint(ServerLevel level, Vec3 position, long now) {
        this.owner.getLookControl().setLookAt(position.x, position.y + 1.5, position.z, 45.0F, 45.0F);
        if (this.owner.distanceToSqr(position) <= 2.25) {
            this.owner.getNavigation().stop();
            this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
            return;
        }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            if (level.hasChunkAt(BlockPos.containing(position)))
                this.owner.getNavigation().moveTo(position.x, position.y, position.z, 1.0);
        }
        // A blocked/completed path does not forget the sensed identity and magically reacquire hidden coordinates.
    }

    @Override
    public void clearEncounter() {
        super.clearEncounter();
        FrostParalysis.releaseOwnedBy(this.frost);
        this.sensedPlayer = null;
        this.lastLocated = null;
        this.locatedUntil = 0;
        this.frost.setHuntState(FrostEnderman.HuntState.IDLE);
    }
}
