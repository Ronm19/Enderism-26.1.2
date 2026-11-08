package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.WhiteEndermanConfig;
import com.ronm19.enderism.combat.WhiteHuntPlanner;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.WhiteEnderman;
import com.ronm19.enderism.entity.WhiteEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A patient native-Brain hunter; hidden pursuit uses finite snapshots rather than a live target's coordinates. */
public final class WhiteEndermanBrain extends MastermindBrain {
    private final WhiteEnderman huntress;
    private final WhiteHuntPlanner planner;
    private @Nullable WeakReference<LivingEntity> trackedTarget, pendingTarget;
    private @Nullable Vec3 lastLocated, observedEye, routeAnchor, progressPosition;
    private Vec3 observedMotion = Vec3.ZERO, observedFacing = new Vec3(0, 0, 1), turnReference = new Vec3(0, 0, 1);
    private WhiteHuntPlanner.@Nullable Plan route;
    private long encounterStartedAt, lastSeenAt, locatedUntil, observingUntil, stalkingUntil;
    private long nextMarkAt, nextTrackingAt, nextApexAt, nextTeleportAt, nextDecisionAt, nextDetectionAt, nextFlankAt;
    private long nextPathAt, nextProgressAt, normalCombatUntil, flankUntil, flankBackoffUntil, trackingEndsAt, nextTrackingPointAt, phaseEndsAt;
    private int failedFlanks, facingTurns, blockedTicks, investigationCandidates, completedInvestigations;
    private boolean trackingActive, markImpact, apexRelease, preferLeft;

    public WhiteEndermanBrain(WhiteEnderman owner) { super(owner); this.huntress = owner; this.planner = new WhiteHuntPlanner(owner); }
    @Override protected int memoryTicks() { return WhiteEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return this.huntress.isApexActive() ? WhiteEndermanConfig.apexAttackIntervalTicks() : WhiteEndermanConfig.markAttackIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.huntress.isLegalWhiteTarget(target); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }
    public @Nullable Vec3 getLastLocatedSnapshot() { return this.lastLocated; }
    public long getLastSeenAt() { return this.lastSeenAt; }
    public Vec3 getLastObservedMotion() { return this.observedMotion; }
    public int getInvestigationCandidates() { return this.investigationCandidates; }
    public int getCompletedInvestigations() { return this.completedInvestigations; }
    public int getFailedFlanks() { return this.failedFlanks; }
    public int getRecentFacingTurns() { return this.facingTurns; }
    public WhiteHuntPlanner getPlanner() { return this.planner; }
    public @Nullable Vec3 getPlannedDestination() { return this.route == null ? null : this.route.destination(); }
    public boolean isTrackingActive() { return this.trackingActive && this.owner.level().getGameTime() < this.trackingEndsAt; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil; }
    public boolean hasPendingAbility() { return this.pendingTarget != null || this.huntress.getCombatPhase().isCommitted(); }
    public boolean isTacticalAnchorValid(Vec3 anchor) { return anchor.isFinite() && this.isTargetLocalized() && this.lastLocated != null && this.lastLocated.distanceToSqr(anchor) < .001; }

    /** Visibility is sampled on decision frames. A cached observation is never refreshed from a hidden reference. */
    @Override protected @Nullable LivingEntity observeThreat() {
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        LivingEntity attacker = this.owner.getLastHurtByMob();
        int hurtAt = this.owner.getLastHurtByMobTimestamp();
        if (hurtAt != this.handledHurtTimestamp && this.owner.tickCount - hurtAt >= 0 && this.owner.tickCount - hurtAt <= 40 && this.isVisibleThreat(attacker)) {
            this.handledHurtTimestamp = hurtAt; return attacker;
        }
        LivingEntity current = this.tracked();
        boolean currentVisible = this.isVisibleThreat(current);
        long now = level.getGameTime(); if (now < this.nextDetectionAt) return currentVisible ? current : null;
        this.nextDetectionAt = now + WhiteEndermanConfig.reacquisitionIntervalTicks();
        double range = Math.min(64, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE));
        var players = level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range));
        players.sort(Comparator.comparingDouble(player -> player.distanceToSqr(this.owner)));
        // A visible current target has hysteresis; a finite successful mark increases that
        // preference by its actual configurable factor. Immediate visible attackers still win.
        LivingEntity chosen = currentVisible ? current : null;
        double best = currentVisible ? this.owner.distanceToSqr(current) * .75 / (this.huntress.isMarkedTarget(current) ? WhiteEndermanConfig.markPriorityInfluence() : 1) : Double.MAX_VALUE;
        int sampled = 0;
        for (var player : players) {
            if (++sampled > 16) break;
            double distance = player.distanceToSqr(this.owner);
            if (distance > range * range || !this.isVisibleThreat(player)) continue;
            double score = distance / (this.huntress.isMarkedTarget(player) ? WhiteEndermanConfig.markPriorityInfluence() : 1);
            if (score < best) { best = score; chosen = player; }
        }
        return chosen;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        if (!this.isVisibleThreat(target)) return;
        long now = this.owner.level().getGameTime(); Vec3 point = target.position();
        Vec3 facing = target.getLookAngle().multiply(1, 0, 1).normalize();
        if (target != this.tracked()) {
            this.cancelPending(); this.route = null; this.routeAnchor = null; this.planner.clear(); this.huntress.clearMark();
            this.resetTacticalTimings();
            this.encounterStartedAt = now; this.observingUntil = now + WhiteEndermanConfig.observationTicks();
            this.stalkingUntil = this.observingUntil + WhiteEndermanConfig.stalkingDurationTicks();
            this.observedMotion = Vec3.ZERO; this.turnReference = facing; this.facingTurns = this.failedFlanks = this.blockedTicks = 0;
            this.trackingActive = false; this.trackingEndsAt = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20;
        } else {
            if (this.lastLocated != null && now > this.lastSeenAt) {
                Vec3 velocity = point.subtract(this.lastLocated).multiply(1, 0, 1).scale(1.0 / (now - this.lastSeenAt));
                this.observedMotion = velocity.lengthSqr() > .1225 ? velocity.normalize().scale(.35) : velocity;
            }
            if (this.turnReference.lengthSqr() > .01 && this.turnReference.dot(facing) < .25) {
                this.facingTurns = Math.min(4, this.facingTurns + 1); this.turnReference = facing; this.preferLeft = !this.preferLeft;
                if (this.facingTurns >= 2) { this.normalCombatUntil = now + 40; this.cancelRoute(now, false); }
            }
        }
        super.rememberVisibleThreat(target); this.trackedTarget = new WeakReference<>(target); this.lastLocated = point;
        this.observedEye = target.getEyePosition(); this.observedFacing = facing; this.lastSeenAt = now; this.locatedUntil = now + this.memoryTicks();
        this.huntress.setActivelyHunting(true);
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!this.canAct() || this.trackedTarget != null && !this.isLegalThreat(this.tracked())) { this.clearEncounter(); return; }
        if (this.tickCommitted(level, now)) return;
        if (this.trackingActive && now >= this.trackingEndsAt || this.trackedTarget != null && !this.isTargetLocalized()) { this.clearEncounter(); return; }
        if (this.tickFloating()) { this.huntress.setCombatPhase(CombatPhase.PURSUING); return; }
        if (now < this.nextDecisionAt) return;
        this.nextDecisionAt = now + (this.huntress.isApexActive() ? WhiteEndermanConfig.apexDecisionIntervalTicks() : WhiteEndermanConfig.decisionIntervalTicks());
        LivingEntity visible = this.observeThreat();
        if (visible == null) { this.hidden(level, now); return; }
        this.rememberVisibleThreat(visible); this.trackingActive = false; this.trackingEndsAt = 0;
        this.state = State.COMBAT; this.stopAmbient(); this.sampleProgress(now);
        if (now - this.encounterStartedAt >= WhiteEndermanConfig.apexMinEncounterTicks() && this.tryBeginApex(level, visible, now)) return;
        if (this.owner.isWithinMeleeAttackRange(visible)) {
            this.cancelRoute(now, false); this.huntress.setCombatPhase(this.huntress.isApexActive() ? CombatPhase.APEX : CombatPhase.PURSUING);
            this.tickVisibleCombat(visible); return;
        }
        this.stopCombat(); this.clearTarget();
        if (now < this.observingUntil) { this.brace(); this.huntress.setCombatPhase(CombatPhase.OBSERVING, this.observingUntil); this.lookAtSnapshot(); return; }
        if (this.route != null && this.routeAnchor != null && this.huntress.getCombatPhase() == CombatPhase.FLANKING) {
            if (this.lastLocated != null && this.lastLocated.distanceToSqr(this.routeAnchor) > 9 || now >= this.flankUntil || this.blockedTicks >= 20) this.cancelRoute(now, true);
            else if (this.owner.position().distanceToSqr(this.route.destination()) <= 1.44) { this.failedFlanks = 0; this.normalCombatUntil = now + 35; this.cancelRoute(now, false); }
            else { this.lookAtSnapshot(); return; }
        }
        if (this.blockedTicks >= 40 && this.tryReposition(level, now)) return;
        if (now >= this.normalCombatUntil && (now < this.stalkingUntil || this.huntress.isApexActive()) && this.tryFlank(level, now)) return;
        this.huntress.setCombatPhase(this.huntress.isApexActive() ? CombatPhase.APEX : now < this.stalkingUntil && now >= this.normalCombatUntil ? CombatPhase.STALKING : CombatPhase.PURSUING);
        this.approachSnapshot(level, now, false);
    }
    private void hidden(ServerLevel level, long now) {
        this.stopCombat(); this.clearTarget(); this.stopAmbient();
        if (!this.isTargetLocalized()) { if (this.trackedTarget != null || this.state != State.IDLE) this.clearEncounter(); this.huntress.setCombatPhase(CombatPhase.IDLE); this.tickAmbient(); return; }
        if (!this.trackingActive && this.tryBeginTracking(level, now)) return;
        this.state = State.INVESTIGATE; this.huntress.setActivelyHunting(true);
        this.huntress.setCombatPhase(this.trackingActive ? CombatPhase.TRACKING : CombatPhase.SEARCHING, this.trackingEndsAt);
        this.sampleProgress(now); this.lookAtSnapshot();
        if (!this.trackingActive) { this.approachSnapshot(level, now, true); return; }
        if (this.route != null) {
            if (this.owner.position().distanceToSqr(this.route.destination()) <= 1.44) { this.completedInvestigations++; this.planner.failed(this.route.destination(), now); this.route = null; this.nextTrackingPointAt = now + 8; this.brace(); }
            else if (now >= this.nextTrackingPointAt || this.owner.getNavigation().isDone() || this.blockedTicks >= 20) { this.planner.failed(this.route.destination(), now); this.route = null; this.brace(); }
            else return;
        }
        if (now < this.nextTrackingPointAt || this.lastLocated == null || this.observedEye == null) return;
        while (this.investigationCandidates < WhiteEndermanConfig.trackingCandidates()) {
            int index = this.investigationCandidates++;
            var plan = this.planner.investigate(level, this.lastLocated, this.observedMotion, this.observedEye, index, now);
            if (plan != null && this.owner.position().distanceToSqr(plan.destination()) > 1.44) {
                this.route = plan; this.owner.getNavigation().moveTo(plan.path(), this.huntress.isApexActive() ? 1.1 : 1);
                this.nextTrackingPointAt = now + WhiteEndermanConfig.trackingRepositionTicks() + 20; this.blockedTicks = 0; return;
            }
        }
        this.brace();
    }
    private boolean tryFlank(ServerLevel level, long now) {
        if (now < this.nextFlankAt || now < this.flankBackoffUntil || this.lastLocated == null || this.observedEye == null || !this.isTargetLocalized()) return false;
        if (this.failedFlanks >= 2) this.failedFlanks = 0;
        this.nextFlankAt = now + Math.max(20, WhiteEndermanConfig.flankRetryTicks() / (this.huntress.isApexActive() ? 2 : 1));
        var plan = this.planner.flank(level, this.lastLocated, this.observedFacing, this.observedEye, this.preferLeft, this.huntress.isApexActive(), now);
        this.preferLeft = !this.preferLeft;
        if (plan == null) { this.failedFlanks++; if (this.failedFlanks >= 2) this.flankBackoffUntil = now + this.memoryTicks(); this.normalCombatUntil = now + 30; return false; }
        this.route = plan; this.routeAnchor = this.lastLocated; this.flankUntil = now + WhiteEndermanConfig.flankDurationTicks();
        this.owner.getNavigation().moveTo(plan.path(), this.huntress.isApexActive() ? 1.15 : WhiteEndermanConfig.stalkingSpeedModifier());
        this.huntress.setCombatPhase(CombatPhase.FLANKING, this.flankUntil); this.lookAtSnapshot(); return true;
    }
    private void cancelRoute(long now, boolean failed) {
        if (failed && this.route != null) {
            this.planner.failed(this.route.destination(), now); this.failedFlanks++; this.normalCombatUntil = now + 35;
            if (this.failedFlanks >= 2) this.flankBackoffUntil = now + this.memoryTicks();
        }
        this.route = null; this.routeAnchor = null;
    }
    private void approachSnapshot(ServerLevel level, long now, boolean searching) {
        this.lookAtSnapshot();
        if (this.lastLocated == null || this.observedEye == null) return;
        if (this.owner.position().distanceToSqr(this.lastLocated) <= 1.44) { this.brace(); return; }
        if (now < this.nextPathAt) return; this.nextPathAt = now + 20;
        Vec3 point = this.lastLocated;
        if (!searching && this.huntress.isApexActive()) point = point.add(this.observedMotion.scale(6));
        var plan = this.planner.approach(level, point, this.observedEye, now);
        if (plan != null) this.owner.getNavigation().moveTo(plan.path(), searching ? 1 : this.huntress.isApexActive() ? 1.1 : this.huntress.getCombatPhase() == CombatPhase.STALKING ? WhiteEndermanConfig.stalkingSpeedModifier() : 1);
        else { this.blockedTicks = Math.min(100, this.blockedTicks + 20); if (this.blockedTicks >= 40) this.tryReposition(level, now); }
    }
    private void lookAtSnapshot() { if (this.observedEye != null) this.owner.getLookControl().setLookAt(this.observedEye.x, this.observedEye.y, this.observedEye.z, 35, 30); }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return; this.nextProgressAt = now + 20;
        if (this.progressPosition != null && this.lastLocated != null && this.owner.position().distanceToSqr(this.lastLocated) > 9 && this.owner.position().distanceToSqr(this.progressPosition) < .16) this.blockedTicks = Math.min(100, this.blockedTicks + 20); else this.blockedTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private boolean tryReposition(ServerLevel level, long now) {
        if (now < this.nextTeleportAt || !this.isTargetLocalized() || this.lastLocated == null || this.owner.position().distanceToSqr(this.lastLocated) < 16) return false;
        this.nextTeleportAt = now + (this.huntress.isApexActive() ? WhiteEndermanConfig.apexTeleportCooldownTicks() : WhiteEndermanConfig.tacticalTeleportCooldownTicks());
        Vec3 side = new Vec3(-this.observedFacing.z, 0, this.observedFacing.x).scale(this.preferLeft ? 4 : -4); this.preferLeft = !this.preferLeft;
        for (Vec3 requested : new Vec3[] {this.lastLocated.add(side), this.lastLocated.subtract(side), this.lastLocated.subtract(this.observedFacing.scale(4))}) {
            Vec3 destination = this.huntress.findWhiteDestination(requested.add(0, 2, 0), this.lastLocated);
            if (destination == null) continue;
            this.stopCombat(); this.clearTarget(); this.owner.getNavigation().stop();
            if (this.huntress.tryWhiteReposition(destination, this.lastLocated)) { this.cancelRoute(now, false); this.blockedTicks = 0; this.progressPosition = this.owner.position(); return true; }
            this.planner.failed(destination, now);
        }
        this.normalCombatUntil = now + 40; return false;
    }
    private boolean canAct() { return this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi(); }
    private void resetTacticalTimings() {
        this.flankBackoffUntil = this.normalCombatUntil = this.flankUntil = this.nextPathAt = this.nextProgressAt = this.nextTrackingPointAt = this.phaseEndsAt = this.nextFlankAt = 0;
        this.preferLeft = false;
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target) { return this.owner.level() == level && this.canAct() && !this.owner.isPassenger() && !this.owner.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target); }
    public boolean tryBeginMark(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextMarkAt || !this.owner.isWithinMeleeAttackRange(target) || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return false;
        this.rememberVisibleThreat(target); this.begin(target); this.nextMarkAt = now + this.meleeCooldownTicks();
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, this.meleeCooldownTicks());
        this.phaseEndsAt = now + WhiteEndermanConfig.markWindupTicks(); this.huntress.setCombatPhase(CombatPhase.MARK_WINDUP, this.phaseEndsAt); this.huntress.warningCue(level, CombatPhase.MARK_WINDUP); return true;
    }
    public boolean tryBeginApex(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextApexAt || this.huntress.isApexActive()) return false;
        this.rememberVisibleThreat(target); this.begin(target); this.nextApexAt = now + WhiteEndermanConfig.apexCooldownTicks();
        this.phaseEndsAt = now + WhiteEndermanConfig.apexPreparationTicks(); this.huntress.setCombatPhase(CombatPhase.APEX_WINDUP, this.phaseEndsAt); this.huntress.warningCue(level, CombatPhase.APEX_WINDUP); return true;
    }
    public boolean tryBeginTracking(ServerLevel level, long now) {
        LivingEntity target = this.tracked();
        if (this.owner.level() != level || !this.canAct() || this.hasPendingAbility() || this.trackingActive || now < this.nextTrackingAt || !this.isTargetLocalized() || target == null || this.isVisibleThreat(target)) return false;
        this.begin(target); this.nextTrackingAt = now + WhiteEndermanConfig.trackingCooldownTicks(); this.phaseEndsAt = now + WhiteEndermanConfig.trackingPreparationTicks();
        this.trackingEndsAt = Math.min(this.lastSeenAt + this.memoryTicks() + WhiteEndermanConfig.trackingDurationTicks(), this.phaseEndsAt + WhiteEndermanConfig.trackingDurationTicks());
        this.locatedUntil = this.trackingEndsAt; this.investigationCandidates = this.completedInvestigations = 0;
        this.huntress.setCombatPhase(CombatPhase.TRACKING_WINDUP, this.phaseEndsAt); this.huntress.warningCue(level, CombatPhase.TRACKING_WINDUP); return true;
    }
    private void begin(LivingEntity target) { this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.cancelRoute(this.owner.level().getGameTime(), false); this.brace(); this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT; }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.huntress.getCombatPhase(); if (!phase.isCommitted()) return false;
        this.brace();
        if (phase == CombatPhase.MARK || phase == CombatPhase.RECOVERY) {
            if (now >= this.phaseEndsAt) { if (phase == CombatPhase.RECOVERY) this.huntress.setCombatPhase(this.huntress.isApexActive() ? CombatPhase.APEX : CombatPhase.PURSUING); else this.finish(now); } return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || this.owner.isPassenger() || this.owner.isVehicle()) { this.finish(now); return true; }
        if (phase == CombatPhase.TRACKING_WINDUP) {
            if (!this.isTargetLocalized()) { this.finish(now); return true; }
            this.lookAtSnapshot();
            if (this.isVisibleThreat(target)) { this.cancelPending(); this.trackingActive = false; this.trackingEndsAt = 0; this.rememberVisibleThreat(target); this.huntress.setCombatPhase(CombatPhase.PURSUING); return true; }
            if (now >= this.phaseEndsAt) { this.pendingTarget = null; this.trackingActive = true; this.nextTrackingPointAt = now; this.huntress.setCombatPhase(CombatPhase.TRACKING, this.trackingEndsAt); } return true;
        }
        if (!this.isVisibleThreat(target)) { this.finish(now); return true; }
        this.rememberVisibleThreat(target); this.lookAtSnapshot();
        if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.MARK_WINDUP) {
            this.huntress.setCombatPhase(CombatPhase.MARK); this.markImpact = true;
            try { this.huntress.performPredatorsMark(level, target); } finally { this.markImpact = false; this.pendingTarget = null; } this.phaseEndsAt = now + 4;
        } else if (phase == CombatPhase.APEX_WINDUP) {
            this.apexRelease = true; try { this.huntress.beginApexHunt(level, now); } finally { this.apexRelease = false; this.pendingTarget = null; } this.finish(now);
        }
        return true;
    }
    public boolean isMarkImpactFrame(LivingEntity target) { return this.markImpact && this.getPendingTarget() == target && this.huntress.getCombatPhase() == CombatPhase.MARK; }
    public boolean isApexReleaseFrame() { return this.apexRelease && this.huntress.getCombatPhase() == CombatPhase.APEX_WINDUP; }
    private void finish(long now) { this.cancelPending(); this.phaseEndsAt = now + WhiteEndermanConfig.recoveryTicks(); this.huntress.setCombatPhase(CombatPhase.RECOVERY, this.phaseEndsAt); }
    private void cancelPending() { this.pendingTarget = null; this.markImpact = this.apexRelease = false; }
    private void brace() { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelPending(); this.trackedTarget = null; this.lastLocated = this.observedEye = this.routeAnchor = this.progressPosition = null;
        this.route = null; this.observedMotion = Vec3.ZERO; this.locatedUntil = this.trackingEndsAt = this.observingUntil = this.stalkingUntil = 0;
        this.encounterStartedAt = this.lastSeenAt = 0; this.resetTacticalTimings(); this.observedFacing = this.turnReference = new Vec3(0, 0, 1);
        this.trackingActive = false; this.failedFlanks = this.facingTurns = this.blockedTicks = this.investigationCandidates = this.completedInvestigations = 0;
        this.planner.clear(); this.huntress.clearMark(); this.huntress.clearApexHunt(); this.huntress.setActivelyHunting(false); this.huntress.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) { output.putLong("WhiteMarkAfter", this.nextMarkAt); output.putLong("WhiteTrackingAfter", this.nextTrackingAt); output.putLong("WhiteApexAfter", this.nextApexAt); output.putLong("WhiteTeleportAfter", this.nextTeleportAt); }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime(); this.nextMarkAt = Math.min(input.getLongOr("WhiteMarkAfter", 0), now + this.meleeCooldownTicks());
        this.nextTrackingAt = Math.min(input.getLongOr("WhiteTrackingAfter", 0), now + WhiteEndermanConfig.trackingCooldownTicks());
        this.nextApexAt = Math.min(input.getLongOr("WhiteApexAfter", 0), now + WhiteEndermanConfig.apexCooldownTicks());
        this.nextTeleportAt = Math.min(input.getLongOr("WhiteTeleportAfter", 0), now + WhiteEndermanConfig.tacticalTeleportCooldownTicks());
    }
}
