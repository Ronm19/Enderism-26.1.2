package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.VoidEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.VoidEnderman;
import com.ronm19.enderism.entity.VoidEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native Mastermind actuators with three finite actions and observed, temporary terrain tactics. */
public final class VoidEndermanBrain extends MastermindBrain {
    private final VoidEnderman hunter;
    private @Nullable WeakReference<LivingEntity> trackedTarget, pendingTarget;
    private @Nullable Vec3 lastLocated, plannedStep, progressPosition;
    private Vec3 observedVelocity = Vec3.ZERO;
    private long locatedUntil, nextDetectionAt, nextDecisionAt, nextPathAt, nextProgressAt;
    private long nextStrikeAt, nextStepAt, nextPullAt, phaseEndsAt;
    private int blockedTicks;
    private boolean leftFlank, strikeImpact, pullImpact;

    public VoidEndermanBrain(VoidEnderman owner) { super(owner); this.hunter = owner; }
    @Override protected int memoryTicks() { return VoidEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return VoidEndermanConfig.strikeIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public boolean hasPendingAbility() { return this.pendingTarget != null || this.hunter.getCombatPhase().isCommitted(); }
    public boolean isTargetLocalized() {
        return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil;
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity current = super.observeThreat();
        if (current != null) return current;
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        LivingEntity previous = this.tracked();
        if (now < this.nextDetectionAt) return this.isVisibleThreat(previous) ? previous : null;
        this.nextDetectionAt = now + 10;
        double best = square(VoidEndermanConfig.followRange()); ServerPlayer selected = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(VoidEndermanConfig.followRange()))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= best && this.isVisibleThreat(player)) { best = distance; selected = player; }
        }
        return selected;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        if (target != this.tracked()) {
            this.cancelPending(); this.blockedTicks = 0; this.progressPosition = this.owner.position();
            this.nextProgressAt = this.owner.level().getGameTime() + 20;
        }
        super.rememberVisibleThreat(target);
        this.trackedTarget = new WeakReference<>(target); this.lastLocated = target.position();
        this.observedVelocity = target.getDeltaMovement().multiply(1, 0, 1);
        if (this.observedVelocity.lengthSqr() > .25) this.observedVelocity = this.observedVelocity.normalize().scale(.5);
        this.locatedUntil = this.owner.level().getGameTime() + this.memoryTicks();
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        if (this.hunter.isRecoveringFromVoid()) {
            this.cancelPending(); this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget();
            this.stopNavigation(); this.hunter.setCombatPhase(CombatPhase.VOID_RESCUE); return;
        }
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) { this.hunter.setCombatPhase(CombatPhase.PURSUING); return; }
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible); this.state = State.COMBAT; this.sampleProgress(now);
            boolean unsafePath = this.hasUnsafeNextStep();
            if (now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + 12;
                if ((unsafePath || this.blockedTicks >= 40 || this.isFleeing(visible)) && this.tryBeginStep(level, visible, now)) return;
                if (this.tryBeginPull(level, visible, now)) return;
            }
            if (unsafePath) {
                this.stopCombat(); this.stopAmbient(); this.stopMovement(); this.hunter.setCombatPhase(CombatPhase.SEARCHING); return;
            }
            this.hunter.setCombatPhase(CombatPhase.PURSUING); this.tickVisibleCombat(visible);
            if (this.hasUnsafeNextStep()) { this.stopCombat(); this.stopMovement(); this.blockedTicks = Math.max(40, this.blockedTicks); }
            return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.hunter.setCombatPhase(CombatPhase.SEARCHING);
            this.sampleProgress(now);
            LivingEntity target = this.tracked();
            if (this.blockedTicks >= 40 && now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + 12;
                if (target != null && this.tryBeginStep(level, target, now)) return;
            }
            this.investigate(now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE) this.clearEncounter();
        this.hunter.setCombatPhase(CombatPhase.IDLE); this.tickAmbient();
        if (this.hasUnsafeNextStep()) { this.stopAmbient(); this.stopMovement(); }
    }
    private boolean isFleeing(LivingEntity target) {
        if (this.owner.distanceToSqr(target) < 36 || this.lastLocated == null) return false;
        Vec3 away = this.lastLocated.subtract(this.owner.position()).multiply(1, 0, 1).normalize();
        return this.owner.distanceToSqr(target) > 100 || this.observedVelocity.dot(away) > .06;
    }
    private boolean hasUnsafeNextStep() {
        var path = this.owner.getNavigation().getPath();
        return path != null && !path.isDone() && !this.hunter.isSafeGroundSegment(path.getNextEntityPos(this.owner));
    }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return;
        this.nextProgressAt = now + 20;
        if (this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 9 && this.progressPosition != null
                && this.owner.position().distanceToSqr(this.progressPosition) < .3) this.blockedTicks = Math.min(100, this.blockedTicks + 20);
        else this.blockedTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private void investigate(long now) {
        Vec3 point = this.lastLocated;
        if (point == null) return;
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 35, 35);
        if (this.owner.distanceToSqr(point) <= 2.25 || this.hasUnsafeNextStep()) { this.stopMovement(); return; }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            BlockPos destination = BlockPos.containing(point);
            if (this.owner.level().getChunkSource().getChunkNow(destination.getX() >> 4, destination.getZ() >> 4) != null)
                this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1);
            if (this.hasUnsafeNextStep()) this.stopMovement();
        }
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target) {
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.owner.isPassenger() && !this.owner.isVehicle() && !this.hunter.isRecoveringFromVoid()
                && !this.hasPendingAbility() && this.isLegalThreat(target);
    }
    public boolean tryBeginStrike(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || !this.isVisibleThreat(target) || now < this.nextStrikeAt
                || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN) || !this.owner.isWithinMeleeAttackRange(target)) return false;
        this.rememberVisibleThreat(target); this.begin(target);
        this.nextStrikeAt = now + VoidEndermanConfig.strikeIntervalTicks();
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, VoidEndermanConfig.strikeIntervalTicks());
        this.phaseEndsAt = now + VoidEndermanConfig.strikeWindupTicks(); this.hunter.setCombatPhase(CombatPhase.STRIKE_WINDUP);
        this.hunter.warningCue(level, false); return true;
    }
    public boolean tryBeginStep(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextStepAt || this.owner.isInWater()) return false;
        if (this.isVisibleThreat(target)) this.rememberVisibleThreat(target);
        else if (target != this.tracked() || !this.isTargetLocalized()) return false;
        Vec3 observed = this.lastLocated;
        if (observed == null || this.owner.distanceToSqr(observed) < 16 || this.owner.distanceToSqr(observed) > square(VoidEndermanConfig.stepRadius() + 4)) return false;
        Vec3 radial = this.owner.position().subtract(observed).multiply(1, 0, 1).normalize();
        if (radial.lengthSqr() < .01) radial = new Vec3(1, 0, 0);
        this.leftFlank = !this.leftFlank;
        Vec3 side = new Vec3(-radial.z, 0, radial.x).scale(this.leftFlank ? 1 : -1);
        Vec3 prediction = this.observedVelocity.scale(3);
        if (prediction.lengthSqr() > 1) prediction = prediction.normalize();
        double flank = VoidEndermanConfig.stepFlankDistance();
        Vec3[] candidates = {observed.add(prediction).add(side.scale(flank)), observed.add(prediction).add(side.scale(-flank)), observed.add(radial.scale(flank))};
        Vec3 destination = null;
        for (Vec3 candidate : candidates) { destination = this.hunter.planVoidStepDestination(candidate); if (destination != null) break; }
        if (destination == null || this.owner.distanceToSqr(destination) < 4) {
            this.nextStepAt = now + Math.min(40, VoidEndermanConfig.stepCooldownTicks()); return false;
        }
        this.begin(target); this.plannedStep = destination; this.nextStepAt = now + VoidEndermanConfig.stepCooldownTicks();
        this.phaseEndsAt = now + VoidEndermanConfig.stepPreparationTicks(); this.hunter.setCombatPhase(CombatPhase.STEP_WINDUP);
        this.hunter.warningCue(level, false); return true;
    }
    public boolean tryBeginPull(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextPullAt || !this.isVisibleThreat(target) || !this.hunter.canSafelyPull(target)) return false;
        this.rememberVisibleThreat(target); this.begin(target); this.nextPullAt = now + VoidEndermanConfig.pullCooldownTicks();
        this.phaseEndsAt = now + VoidEndermanConfig.pullTelegraphTicks(); this.hunter.setCombatPhase(CombatPhase.PULL_WINDUP);
        this.hunter.warningCue(level, true); return true;
    }
    private void begin(LivingEntity target) {
        this.stopCombat(); this.stopFloating(); this.stopAmbient(); this.clearTarget(); this.stopMovement();
        this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT;
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.hunter.getCombatPhase();
        if (!phase.isCommitted()) return false;
        if (phase == CombatPhase.RECOVERY || phase == CombatPhase.VOID_RESCUE) {
            this.stopMovement(); if (now >= this.phaseEndsAt) this.hunter.setCombatPhase(CombatPhase.IDLE); return true;
        }
        if (phase == CombatPhase.STRIKE || phase == CombatPhase.STEP || phase == CombatPhase.PULL) {
            this.stopMovement(); if (now >= this.phaseEndsAt) this.finish(now); return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || this.owner.isPassenger() || this.owner.isVehicle()
                || phase != CombatPhase.STEP_WINDUP && !this.isVisibleThreat(target)
                || phase == CombatPhase.STEP_WINDUP && !this.isTargetLocalized()) { this.finish(now); return true; }
        if (this.isVisibleThreat(target)) this.rememberVisibleThreat(target);
        this.stopMovement();
        if (this.lastLocated != null) this.owner.getLookControl().setLookAt(this.lastLocated.x, this.lastLocated.y + 1.4, this.lastLocated.z, 35, 35);
        if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.STRIKE_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.STRIKE); this.strikeImpact = true;
            try { this.hunter.performAbyssalStrike(level, target); } finally { this.strikeImpact = false; this.pendingTarget = null; }
            this.phaseEndsAt = now + 6;
        } else if (phase == CombatPhase.STEP_WINDUP) {
            Vec3 position = this.plannedStep; boolean moved = position != null && this.hunter.tryVoidStepTeleport(position);
            this.pendingTarget = null; this.plannedStep = null; this.hunter.setCombatPhase(CombatPhase.STEP); this.phaseEndsAt = now + 6;
            if (moved) { this.blockedTicks = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; }
        } else if (phase == CombatPhase.PULL_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.PULL); this.pullImpact = true;
            try { this.hunter.applyAbyssalPull(level, target); } finally { this.pullImpact = false; this.pendingTarget = null; }
            this.phaseEndsAt = now + 8;
        }
        return true;
    }
    public boolean isStrikeImpactFrame(LivingEntity target) { return this.strikeImpact && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.STRIKE; }
    public boolean isPullImpactFrame(LivingEntity target) { return this.pullImpact && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.PULL; }
    private void cancelPending() { this.pendingTarget = null; this.plannedStep = null; this.strikeImpact = false; this.pullImpact = false; }
    private void finish(long now) { this.cancelPending(); this.stopMovement(); this.hunter.setCombatPhase(CombatPhase.RECOVERY); this.phaseEndsAt = now + VoidEndermanConfig.recoveryTicks(); }
    private void stopNavigation() {
        this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0);
    }
    private void stopMovement() { this.stopNavigation(); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    public void onVoidRescue(long now) {
        this.cancelPending(); this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.stopNavigation();
        this.blockedTicks = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20;
        this.phaseEndsAt = now + VoidEndermanConfig.rescueRecoveryTicks(); this.hunter.setCombatPhase(CombatPhase.VOID_RESCUE);
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelPending(); this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0;
        this.observedVelocity = Vec3.ZERO; this.progressPosition = null; this.blockedTicks = 0; this.phaseEndsAt = 0;
        this.hunter.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("VoidStrikeAfter", this.nextStrikeAt); output.putLong("VoidStepAfter", this.nextStepAt); output.putLong("VoidPullAfter", this.nextPullAt);
    }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime();
        this.nextStrikeAt = Math.min(input.getLongOr("VoidStrikeAfter", 0), now + VoidEndermanConfig.strikeIntervalTicks());
        this.nextStepAt = Math.min(input.getLongOr("VoidStepAfter", 0), now + VoidEndermanConfig.stepCooldownTicks());
        this.nextPullAt = Math.min(input.getLongOr("VoidPullAfter", 0), now + VoidEndermanConfig.pullCooldownTicks());
    }
    private static double square(double value) { return value * value; }
}
