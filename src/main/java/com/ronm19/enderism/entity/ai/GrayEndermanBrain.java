package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.GrayEnderman;
import com.ronm19.enderism.entity.GrayEnderman.HuntState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Gray decisions in the shared native Brain, with sampled senses rather than a second goal selector. */
public final class GrayEndermanBrain extends MastermindBrain {
    private final GrayEnderman gray;
    private @Nullable ServerPlayer sensedPlayer;
    private @Nullable Vec3 previousSample;
    private @Nullable Vec3 coarsePresence;
    private @Nullable Vec3 lastLocatedPosition;
    private Vec3 observedMotion = Vec3.ZERO;
    private @Nullable LivingEntity eclipseTarget;
    private long nextDetectionAt;
    private long lastSampleAt;
    private long locatedUntil;
    private long sensedSince;
    private long nextPathAt;
    private long movementGraceUntil;
    private long nextEclipseAt;
    private long eclipseEndsAt;
    private long ambushVisualUntil;
    private long idleStillUntil;
    private long nextStillAt;

    public GrayEndermanBrain(GrayEnderman owner) {
        super(owner);
        this.gray = owner;
    }

    @Override
    protected int memoryTicks() {
        return GrayEndermanTuning.LOCATION_MEMORY_TICKS;
    }

    @Override
    protected int meleeCooldownTicks() {
        return GrayEndermanTuning.MELEE_COOLDOWN_TICKS;
    }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target)
                && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    public int getSensedPlayerId() {
        return this.sensedPlayer == null ? -1 : this.sensedPlayer.getId();
    }

    public boolean isTargetLocalized() {
        return this.lastLocatedPosition != null && this.owner.level().getGameTime() < this.locatedUntil;
    }

    public @Nullable Vec3 getLastLocatedPosition() {
        return this.isTargetLocalized() ? this.lastLocatedPosition : null;
    }

    public long nextEclipseReadyAt() {
        return this.nextEclipseAt;
    }

    public boolean isEclipseWindingUp() {
        return this.eclipseEndsAt != 0;
    }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (this.sensedPlayer != null && !this.isLegalThreat(this.sensedPlayer)) {
            this.clearEncounter();
        }
        if (now >= this.nextDetectionAt) {
            this.nextDetectionAt = now + GrayEndermanTuning.SENSOR_INTERVAL_TICKS;
            this.samplePresence(level, now);
        }

        // Sight legitimately supplies an accurate position, including for a motionless player.
        LivingEntity visible = this.isVisibleThreat(this.sensedPlayer) ? this.sensedPlayer : this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible);
            this.locate(visible.position(), now);
        }

        if (this.tickFloating()) {
            this.cancelEclipse();
            this.gray.setHuntState(HuntState.SEARCHING);
            return;
        }
        if (this.eclipseEndsAt != 0) {
            this.tickEclipse(level, now);
            return;
        }

        if (visible != null) {
            this.tickLocatedCombat(level, visible, now);
            return;
        }

        this.stopCombat();
        this.clearTarget();
        if (this.lastLocatedPosition != null && now < this.locatedUntil) {
            this.state = State.INVESTIGATE;
            this.gray.setHuntState(now < this.ambushVisualUntil ? HuntState.AMBUSHING : HuntState.SEARCHING);
            this.maybeAmbush(now);
            this.investigatePoint(this.lastLocatedPosition, now, GrayEndermanTuning.STALK_SPEED);
            return;
        }
        if (this.lastLocatedPosition != null) {
            // Stale precise knowledge is discarded even if the same stationary presence remains nearby.
            this.lastLocatedPosition = null;
            this.observedMotion = Vec3.ZERO;
            this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            this.owner.getNavigation().stop();
            this.nextPathAt = now;
        }
        if (this.sensedPlayer != null && this.coarsePresence != null) {
            this.state = State.INVESTIGATE;
            this.gray.setHuntState(now - this.sensedSince < GrayEndermanTuning.OBSERVATION_TICKS
                    ? HuntState.SENSING : HuntState.OBSERVING);
            this.investigatePoint(this.coarsePresence, now, 0.9);
            return;
        }
        if (this.state != State.IDLE) {
            this.clearEncounter();
        }
        this.tickIdle(now);
    }

    private void samplePresence(ServerLevel level, long now) {
        ServerPlayer previous = this.sensedPlayer;
        ServerPlayer player = previous;
        double rangeSquared = GrayEndermanTuning.DETECTION_RANGE * GrayEndermanTuning.DETECTION_RANGE;
        if (!this.isLegalThreat(player) || this.owner.distanceToSqr(player) > rangeSquared) {
            player = null;
            double closest = rangeSquared;
            for (ServerPlayer candidate : level.getEntitiesOfClass(ServerPlayer.class,
                    this.owner.getBoundingBox().inflate(GrayEndermanTuning.DETECTION_RANGE))) {
                double distance = this.owner.distanceToSqr(candidate);
                if (distance <= closest && this.isLegalThreat(candidate)) {
                    player = candidate;
                    closest = distance;
                }
            }
        }
        this.sensedPlayer = player;
        if (player == null) {
            this.previousSample = null;
            this.coarsePresence = null;
            return;
        }
        Vec3 current = player.position();
        if (player != previous) {
            this.previousSample = current;
            this.lastSampleAt = now;
            this.sensedSince = now;
            this.coarsePresence = coarsePosition(current);
            this.nextPathAt = now;
            // Switching player identity must not hand the new target another player's exact location.
            this.lastLocatedPosition = null;
            this.observedMotion = Vec3.ZERO;
            this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            return;
        }

        Vec3 delta = this.previousSample == null ? Vec3.ZERO : current.subtract(this.previousSample);
        long elapsed = now - this.lastSampleAt;
        double distanceSquared = delta.lengthSqr();
        boolean forced = distanceSquared > square(GrayEndermanTuning.MAXIMUM_MOVEMENT_SAMPLE)
                || player.hurtTime > 0 || player.hurtMarked || player.isInPostImpulseGraceTime() || player.isPassenger();
        if (forced) {
            this.movementGraceUntil = now + GrayEndermanTuning.FORCED_MOVEMENT_GRACE_TICKS;
        }
        if (!forced && now >= this.movementGraceUntil && elapsed > 0
                && distanceSquared > square(GrayEndermanTuning.MOVEMENT_THRESHOLD)) {
            this.observedMotion = delta.scale(1.0 / elapsed);
            this.locate(current, now);
        } else {
            this.observedMotion = Vec3.ZERO;
        }
        this.previousSample = current;
        this.lastSampleAt = now;
        // Basic stationary sensing retains its initial uncertain area; it never refreshes exact hidden coordinates.
    }

    static Vec3 coarsePosition(Vec3 position) {
        double cell = GrayEndermanTuning.PRESENCE_CELL_SIZE;
        return new Vec3(Math.floor(position.x / cell) * cell + cell / 2.0,
                Math.floor(position.y / 4.0) * 4.0, Math.floor(position.z / cell) * cell + cell / 2.0);
    }

    private void locate(Vec3 position, long now) {
        this.lastLocatedPosition = position;
        this.locatedUntil = now + this.memoryTicks();
        WalkTarget previous = this.owner.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (previous == null || !previous.getTarget().currentPosition().equals(position)) {
            previous = new WalkTarget(new BlockPosTracker(position), (float) GrayEndermanTuning.STALK_SPEED, 1);
        }
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, previous, this.memoryTicks());
    }

    private void tickLocatedCombat(ServerLevel level, LivingEntity visible, long now) {
        this.stopAmbient();
        this.owner.getLookControl().setLookAt(visible, 90.0F, 90.0F);
        if (now - this.sensedSince < GrayEndermanTuning.OBSERVATION_TICKS
                && !this.owner.isWithinMeleeAttackRange(visible)) {
            this.stopCombat();
            this.clearTarget();
            this.freezeMovement();
            this.state = State.INVESTIGATE;
            this.gray.setHuntState(HuntState.OBSERVING);
            return;
        }
        if (this.beginEclipse(level, visible, now)) {
            return;
        }
        boolean repositioned = this.maybeAmbush(now);
        // A teleport may have put tree cover between the two entities; do not path to an unseen live entity.
        if (repositioned) this.owner.getSensing().tick();
        if (repositioned && !this.isVisibleThreat(visible)) {
            this.stopCombat();
            this.clearTarget();
            this.state = State.INVESTIGATE;
            this.gray.setHuntState(HuntState.AMBUSHING);
            return;
        }
        this.tickVisibleCombat(visible);
        this.owner.getNavigation().setSpeedModifier(GrayEndermanTuning.STALK_SPEED);
        this.gray.setHuntState(now < this.ambushVisualUntil ? HuntState.AMBUSHING
                : this.owner.isWithinMeleeAttackRange(visible) ? HuntState.ATTACKING : HuntState.STALKING);
    }

    private boolean beginEclipse(ServerLevel level, LivingEntity target, long now) {
        if (now < this.nextEclipseAt || !this.isLegalThreat(target) || !this.isVisibleThreat(target)
                || this.owner.distanceToSqr(target) > square(GrayEndermanTuning.ECLIPSE_RANGE)
                || target.hasEffect(MobEffects.BLINDNESS)) {
            return false;
        }
        this.nextEclipseAt = now + GrayEndermanTuning.ECLIPSE_COOLDOWN_TICKS;
        this.eclipseEndsAt = now + GrayEndermanTuning.ECLIPSE_WARNING_TICKS;
        this.eclipseTarget = target;
        this.stopCombat();
        this.stopAmbient();
        this.clearTarget();
        this.freezeMovement();
        this.state = State.COMBAT;
        this.gray.setHuntState(HuntState.ATTACKING);
        this.gray.beginEclipseWarning(level);
        return true;
    }

    private void tickEclipse(ServerLevel level, long now) {
        LivingEntity target = this.eclipseTarget;
        this.stopCombat();
        this.clearTarget();
        this.freezeMovement();
        this.gray.setHuntState(HuntState.ATTACKING);
        if (!this.isLegalThreat(target) || !this.isVisibleThreat(target)
                || this.owner.distanceToSqr(target) > square(GrayEndermanTuning.ECLIPSE_RANGE)) {
            this.cancelEclipse();
            return;
        }
        this.owner.getLookControl().setLookAt(target, 90.0F, 90.0F);
        if (now < this.eclipseEndsAt) return;
        this.cancelEclipse();
        // Never merge/extend an existing Blindness application, including another Gray's effect.
        if (!target.hasEffect(MobEffects.BLINDNESS)
                && target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                        GrayEndermanTuning.BLINDNESS_TICKS, 0, false, false, true), this.owner)) {
            this.gray.finishEclipseEffects(level);
            this.maybeAmbush(now);
        }
    }

    private void cancelEclipse() {
        this.eclipseEndsAt = 0;
        this.eclipseTarget = null;
        this.gray.setEclipseWindingUp(false);
    }

    private boolean maybeAmbush(long now) {
        Vec3 location = this.lastLocatedPosition;
        if (location == null || now >= this.locatedUntil || now < this.gray.nextPaleAmbushReadyAt()
                || this.owner.distanceToSqr(location) <= 36.0) return false;
        if (this.gray.attemptPaleAmbush(location, this.observedMotion)) {
            this.ambushVisualUntil = now + GrayEndermanTuning.PALE_AMBUSH_VISUAL_TICKS;
            this.nextPathAt = now;
            return true;
        }
        return false;
    }

    private void investigatePoint(Vec3 position, long now, double speed) {
        this.stopAmbient();
        this.owner.getLookControl().setLookAt(position.x, position.y + 1.5, position.z, 45.0F, 45.0F);
        if (this.owner.distanceToSqr(position) <= 2.25) {
            this.freezeMovement();
            return;
        }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + GrayEndermanTuning.PATH_REFRESH_TICKS;
            var positionBlock = net.minecraft.core.BlockPos.containing(position);
            if (this.owner.level().hasChunkAt(positionBlock)) {
                this.owner.getNavigation().moveTo(position.x, position.y, position.z, speed);
            }
        }
    }

    private void tickIdle(long now) {
        this.state = State.IDLE;
        this.gray.setHuntState(HuntState.IDLE);
        if (now >= this.nextStillAt) {
            this.nextStillAt = now + GrayEndermanTuning.IDLE_STILLNESS_INTERVAL_TICKS;
            this.idleStillUntil = now + GrayEndermanTuning.IDLE_STILLNESS_TICKS;
        }
        if (now < this.idleStillUntil) {
            this.stopAmbient();
            this.freezeMovement();
        } else {
            this.tickAmbient();
        }
    }

    private void freezeMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(this.owner.getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    @Override
    public void clearEncounter() {
        super.clearEncounter();
        this.sensedPlayer = null;
        this.previousSample = null;
        this.coarsePresence = null;
        this.lastLocatedPosition = null;
        this.observedMotion = Vec3.ZERO;
        this.locatedUntil = 0;
        this.ambushVisualUntil = 0;
        this.cancelEclipse();
        this.gray.setHuntState(HuntState.RESETTING);
        // Ability deadlines remain intact: losing/reacquiring a player cannot bypass cooldowns.
    }

    private static double square(double value) {
        return value * value;
    }
}
