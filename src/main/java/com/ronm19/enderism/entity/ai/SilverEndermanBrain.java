package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.SilverEndermanConfig;
import com.ronm19.enderism.effect.ArmorFracture;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.SilverEnderman;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** One native Brain owns pursuit, one melee actuator, and all three Silver combat actions. */
public final class SilverEndermanBrain extends MastermindBrain {
    private final SilverEnderman silver;
    private @Nullable LivingEntity trackedTarget;
    private @Nullable Vec3 lastLocated;
    private Vec3 lastObservedVelocity = Vec3.ZERO;
    private long lastObservationAt;
    private long locatedUntil;
    private long nextDetectionAt;
    private long nextPathAt;
    private long nextProgressAt;
    private long nextStepAt;
    private long nextFractureAt;
    private @Nullable Vec3 progressPosition;
    private int stuckTicks;
    private @Nullable LivingEntity stepTarget;
    private long stepWarmupEndsAt;
    private boolean leftFlank;
    private int stepAttempts;
    private int stepCount;
    private @Nullable Vec3 lastStepDestination;
    private @Nullable WeakReference<ServerPlayer> fractureTarget;
    private long lastEligibleHitAt;
    private int fractureBuildup;
    private int successfulHits;

    public SilverEndermanBrain(SilverEnderman owner) { super(owner); this.silver = owner; }
    @Override protected int memoryTicks() { return SilverEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return SilverEndermanConfig.meleeCooldownTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override protected @Nullable LivingEntity observeThreat() {
        // Existing visible retaliation and encounter memory take priority over opportunistic acquisition.
        LivingEntity threat = super.observeThreat();
        if (threat != null) return threat;
        long now = this.owner.level().getGameTime();
        if (!(this.owner.level() instanceof ServerLevel level) || now < this.nextDetectionAt) return null;
        this.nextDetectionAt = now + SilverEndermanConfig.SENSOR_INTERVAL_TICKS;
        double range = SilverEndermanConfig.followRange(), closest = range * range;
        ServerPlayer nearest = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= closest && this.isVisibleThreat(player)) { nearest = player; closest = distance; }
        }
        if (nearest != null) this.rememberVisibleThreat(nearest);
        return nearest;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (target != this.trackedTarget) {
            this.interruptStep(); this.resetBuildup();
            this.progressPosition = this.owner.position(); this.stuckTicks = 0;
            this.nextProgressAt = now + 20;
            this.lastObservedVelocity = Vec3.ZERO;
        } else if (this.lastLocated != null && now > this.lastObservationAt) {
            Vec3 measured = target.position().subtract(this.lastLocated).scale(1.0 / (now - this.lastObservationAt)).multiply(1, 0, 1);
            // A long player teleport is an observation, not a license for unbounded prediction.
            this.lastObservedVelocity = measured.lengthSqr() > 0.2025 ? measured.normalize().scale(0.45) : measured;
        }
        super.rememberVisibleThreat(target);
        this.trackedTarget = target;
        this.lastLocated = target.position(); this.lastObservationAt = now;
        this.locatedUntil = now + this.memoryTicks();
    }
    public int getTrackedTargetId() { return this.trackedTarget == null ? -1 : this.trackedTarget.getId(); }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.owner.level().getGameTime() < this.locatedUntil; }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }
    public long getLocatedUntil() { return this.locatedUntil; }
    public long nextStepReadyAt() { return this.nextStepAt; }
    public long nextFractureReadyAt() { return this.nextFractureAt; }
    public int getSuccessfulHitCount() { return this.successfulHits; }
    public int getFractureBuildup() { return this.fractureBuildup; }
    public int getStepAttemptCount() { return this.stepAttempts; }
    public int getStepCount() { return this.stepCount; }
    public int getStuckTicks() { return this.stuckTicks; }
    public @Nullable Vec3 getLastStepDestination() { return this.lastStepDestination; }
    public boolean isStepWindingUp() { return this.stepTarget != null; }

    /** Called only after the entity's single native melee damage call returns an accepted hit. */
    public void onSuccessfulMelee(ServerLevel level, LivingEntity target) {
        if (!this.owner.isAlive() || this.owner.isNoAi() || !this.isLegalThreat(target)) return;
        this.successfulHits++;
        long now = level.getGameTime();
        if (!(target instanceof ServerPlayer player) || now < this.nextFractureAt || !ArmorFracture.canApply(player)) {
            this.resetBuildup(); return;
        }
        ServerPlayer previous = this.fractureTarget == null ? null : this.fractureTarget.get();
        if (previous != player || now - this.lastEligibleHitAt > SilverEndermanConfig.fractureHitWindowTicks()) this.fractureBuildup = 0;
        this.fractureTarget = new WeakReference<>(player); this.lastEligibleHitAt = now;
        this.fractureBuildup++;
        if (this.fractureBuildup < SilverEndermanConfig.fractureHitThreshold()) return;
        this.resetBuildup();
        if (ArmorFracture.tryApply(player, this.silver)) {
            this.nextFractureAt = now + SilverEndermanConfig.fractureCooldownTicks();
            this.silver.fractureImpact(level, player);
        }
    }

    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        if (this.trackedTarget != null && !this.isLegalThreat(this.trackedTarget)) this.clearEncounter();
        if (this.fractureBuildup > 0 && now - this.lastEligibleHitAt > SilverEndermanConfig.fractureHitWindowTicks()) this.resetBuildup();
        if (this.stepTarget != null) { this.tickStep(level, now); return; }
        LivingEntity visible = this.observeThreat();
        if (this.tickFloating()) { this.interruptStep(); this.silver.setHuntState(SilverEnderman.HuntState.SEARCHING); return; }
        if (visible != null) {
            this.sampleProgress(now, visible.position());
            if (this.shouldReposition(visible) && this.beginPhantomStep(level, visible, now)) return;
            this.tickVisibleCombat(visible);
            this.silver.setHuntState(this.silver.isFracturePulseActive() ? SilverEnderman.HuntState.FRACTURING
                    : this.owner.isWithinMeleeAttackRange(visible) ? SilverEnderman.HuntState.ATTACKING
                    : this.isReceding() ? SilverEnderman.HuntState.PURSUING : SilverEnderman.HuntState.APPROACHING);
            return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE;
            this.silver.setHuntState(SilverEnderman.HuntState.SEARCHING);
            Vec3 point = this.lastLocated;
            this.sampleProgress(now, point);
            if (this.stuckTicks >= 40 && this.trackedTarget != null && this.beginPhantomStep(level, this.trackedTarget, now)) return;
            this.investigateLastSeen(level, now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE) this.clearEncounter();
        this.silver.setHuntState(SilverEnderman.HuntState.IDLE); this.tickAmbient();
    }

    private boolean shouldReposition(LivingEntity visible) {
        double distance = this.owner.distanceToSqr(visible);
        return distance > 16.0 && (this.stuckTicks >= 40 || distance >= 100.0 || this.isReceding());
    }
    private boolean isReceding() {
        if (this.lastLocated == null) return false;
        Vec3 away = this.lastLocated.subtract(this.owner.position()).multiply(1, 0, 1).normalize();
        return this.lastObservedVelocity.dot(away) > 0.04;
    }
    /** Starts the same bounded, telegraphed action for pursuit, interception, or stuck recovery. */
    public boolean beginPhantomStep(ServerLevel level, LivingEntity target, long now) {
        if (this.stepTarget != null || now < this.nextStepAt || !this.owner.isAlive() || this.owner.isNoAi()
                || this.owner.isInWater() || !this.isLegalThreat(target)) return false;
        if (this.isVisibleThreat(target)) this.rememberVisibleThreat(target);
        else if (target != this.trackedTarget || !this.isTargetLocalized()) return false;
        Vec3 point = this.lastLocated;
        double range = SilverEndermanConfig.stepActivationRange();
        if (point == null || this.owner.distanceToSqr(point) > range * range || this.owner.distanceToSqr(point) < 9.0) return false;
        this.nextStepAt = now + SilverEndermanConfig.stepCooldownTicks(); this.stepAttempts++;
        this.stepTarget = target; this.stepWarmupEndsAt = now + SilverEndermanConfig.stepWarmupTicks();
        this.silver.setStepWarmupEnd(this.stepWarmupEndsAt);
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.freezeMovement();
        this.state = State.COMBAT; this.silver.setHuntState(SilverEnderman.HuntState.FLANKING);
        this.silver.stepWarning(level); return true;
    }
    private void tickStep(ServerLevel level, long now) {
        LivingEntity target = this.stepTarget;
        if (target == null || !this.isLegalThreat(target) || !this.isTargetLocalized() || this.owner.isInWater()) {
            this.interruptStep(); return;
        }
        if (this.isVisibleThreat(target)) this.rememberVisibleThreat(target);
        Vec3 point = this.lastLocated;
        double range = SilverEndermanConfig.stepActivationRange();
        if (point == null || this.owner.distanceToSqr(point) > range * range) { this.interruptStep(); return; }
        this.freezeMovement(); this.owner.getLookControl().setLookAt(point.x, point.y + 1.6, point.z, 45.0F, 45.0F);
        if (now < this.stepWarmupEndsAt) return;
        this.leftFlank = !this.leftFlank;
        Vec3 radial = this.owner.position().subtract(point).multiply(1, 0, 1);
        if (radial.lengthSqr() < 0.01) radial = new Vec3(1, 0, 0);
        radial = radial.normalize();
        Vec3 side = new Vec3(-radial.z, 0, radial.x).scale(this.leftFlank ? 1 : -1);
        double distance = SilverEndermanConfig.stepFlankDistance();
        Vec3 prediction = this.lastObservedVelocity.scale(4.0);
        if (prediction.lengthSqr() > 1.0) prediction = prediction.normalize();
        Vec3[] flanks = {point.add(prediction).add(side.scale(distance)),
                point.add(prediction).add(side.scale(-distance)), point.add(radial.scale(-distance))};
        Vec3 selected = null;
        for (Vec3 flank : flanks) {
            selected = this.silver.findPhantomStepDestination(flank, point);
            if (selected != null) break;
        }
        this.interruptStep();
        // Failure still consumes nextStepAt. At most one actual shared teleport attempt per cast.
        if (selected != null && this.silver.tryPhantomStepTeleport(selected, point)) {
            this.stepCount++; this.lastStepDestination = this.owner.position(); this.stuckTicks = 0;
            this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20;
        }
    }
    private void sampleProgress(long now, @Nullable Vec3 pursuitPoint) {
        if (now < this.nextProgressAt) return;
        this.nextProgressAt = now + 20;
        if (pursuitPoint != null && this.owner.distanceToSqr(pursuitPoint) > 9.0 && this.progressPosition != null
                && this.owner.position().distanceToSqr(this.progressPosition) < 0.36) this.stuckTicks += 20;
        else this.stuckTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private void investigateLastSeen(ServerLevel level, long now) {
        Vec3 point = this.lastLocated;
        if (point == null) return;
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.6, point.z, 45.0F, 45.0F);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.freezeMovement(); return; }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            if (level.hasChunkAt(BlockPos.containing(point))) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1.0);
            // A failed route does not refresh the observation or end its fixed investigation window.
        }
    }
    private void freezeMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(Vec3.ZERO);
    }
    public void interruptStep() {
        this.stepTarget = null; this.stepWarmupEndsAt = 0; this.silver.setStepWarmupEnd(0L);
    }
    private void resetBuildup() { this.fractureTarget = null; this.fractureBuildup = 0; this.lastEligibleHitAt = 0; }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.interruptStep(); this.resetBuildup();
        ArmorFracture.releaseOwnedBy(this.silver);
        this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0;
        this.lastObservedVelocity = Vec3.ZERO; this.progressPosition = null; this.stuckTicks = 0;
        this.silver.clearAbilityVisuals();
        // Attack/Step/Fracture cooldowns and flank history survive transient encounter resets.
    }
}
