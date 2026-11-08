package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.VampireEndermanConfig;
import com.ronm19.enderism.combat.VampireMobility;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.VampireEnderman;
import com.ronm19.enderism.entity.VampireEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native Brain selects three abilities; mobility helpers perform only its bounded physical actions. */
public final class VampireEndermanBrain extends MastermindBrain {
    private final VampireEnderman huntress;
    private @Nullable WeakReference<LivingEntity> trackedTarget, pendingTarget;
    private @Nullable Vec3 lastLocated, progressPosition;
    private VampireMobility.@Nullable DashPlan pendingDash;
    private long locatedUntil, encounterStarted, nextDetectionAt, nextDecisionAt, nextPathAt, nextProgressAt;
    private long nextStrikeAt, nextDashAt, nextFuryAt, nextEvasionAt, nextTeleportAt, phaseEndsAt;
    private int blockedTicks;
    private boolean strikeImpact, furyRelease, leftFlank;

    public VampireEndermanBrain(VampireEnderman owner) { super(owner); this.huntress = owner; }
    @Override protected int memoryTicks() { return VampireEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return this.huntress.isFuryActive() ? VampireEndermanConfig.furyStrikeIntervalTicks() : VampireEndermanConfig.strikeIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.huntress.isLegalVampireTarget(target); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.lastLocated; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil; }
    public boolean hasPendingAbility() { return this.huntress.getCombatPhase().isCommitted(); }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity threat = super.observeThreat(); if (threat != null) return threat;
        long now = this.owner.level().getGameTime(); if (this.isVisibleThreat(this.tracked())) return this.tracked();
        if (!(this.owner.level() instanceof ServerLevel level) || now < this.nextDetectionAt) return null;
        this.nextDetectionAt = now + 10; double range = Math.min(64, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE)), nearest = range * range;
        ServerPlayer chosen = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player); if (distance <= nearest && this.isVisibleThreat(player)) { chosen = player; nearest = distance; }
        }
        return chosen;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (target != this.tracked()) { this.encounterStarted = now; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; }
        super.rememberVisibleThreat(target); this.trackedTarget = new WeakReference<>(target); this.lastLocated = target.position(); this.locatedUntil = now + this.memoryTicks(); this.huntress.setActivelyHunting(true);
    }
    private boolean canAct() { return this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi(); }
    private boolean mayBegin(ServerLevel level, LivingEntity target) { return this.owner.level() == level && this.canAct() && !this.owner.isPassenger() && !this.owner.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target); }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!this.canAct()) { this.clearEncounter(); return; }
        if (this.trackedTarget != null && !this.isLegalThreat(this.tracked())) this.clearEncounter();
        if (this.tickCommitted(level, now)) return;
        if (now >= this.nextEvasionAt) {
            this.nextEvasionAt = now + VampireEndermanConfig.evasionReactionTicks();
            if (now >= this.nextDashAt) { var escape = this.huntress.getMobility().planEvasion(level); if (escape != null && this.tryBeginDash(level, escape, null, now)) return; }
        }
        if (this.tickFloating()) { this.huntress.setCombatPhase(CombatPhase.PURSUING); return; }
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible); this.state = State.COMBAT; this.sampleProgress(now);
            if (now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + this.decisionIntervalTicks();
                if (now - this.encounterStarted >= 100 && this.tryBeginFury(level, visible, now)) return;
                if (this.owner.distanceToSqr(visible) > 9 && now >= this.nextDashAt) { var plan = this.huntress.getMobility().planToward(this.lastLocated); if (plan != null && this.tryBeginDash(level, plan, visible, now)) return; }
                if (this.owner.distanceToSqr(visible) > 4 && (visible.getY() - this.owner.getY() > .6 || this.blockedTicks >= 20)
                        && this.huntress.getMobility().tryLeapToward(this.lastLocated, now)) { this.beginLeap(now); return; }
                if (this.blockedTicks >= 40 && this.tryReposition(now)) return;
            }
            this.huntress.setCombatPhase(this.huntress.isFuryActive() ? CombatPhase.FURY : CombatPhase.PURSUING); this.tickVisibleCombat(visible); return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.huntress.setActivelyHunting(true); this.huntress.setCombatPhase(CombatPhase.SEARCHING); this.sampleProgress(now);
            if (this.blockedTicks >= 40 && now >= this.nextDecisionAt) { this.nextDecisionAt = now + this.decisionIntervalTicks(); if (this.tryReposition(now)) return; }
            this.investigate(level, now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE || this.huntress.isActivelyHunting() || this.huntress.isFuryActive()) this.clearEncounter();
        this.huntress.setCombatPhase(CombatPhase.IDLE); this.tickAmbient();
    }
    public int decisionIntervalTicks() { return this.huntress.isFuryActive() ? VampireEndermanConfig.furyDecisionIntervalTicks() : 10; }
    public boolean tryBeginStrike(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextStrikeAt || !this.owner.isWithinMeleeAttackRange(target) || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return false;
        int interval = this.meleeCooldownTicks(); this.nextStrikeAt = now + interval; this.begin(target, CombatPhase.STRIKE_WINDUP, now + VampireEndermanConfig.strikeWindupTicks());
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, interval); this.huntress.abilityWarning(level, CombatPhase.STRIKE_WINDUP); return true;
    }
    public boolean tryBeginFury(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || this.huntress.isFuryActive() || now < this.nextFuryAt) return false;
        this.nextFuryAt = now + VampireEndermanConfig.furyCooldownTicks(); this.begin(target, CombatPhase.FURY_WINDUP, now + VampireEndermanConfig.furyPreparationTicks()); this.huntress.abilityWarning(level, CombatPhase.FURY_WINDUP); return true;
    }
    public boolean tryBeginDash(ServerLevel level, VampireMobility.DashPlan plan, @Nullable LivingEntity target, long now) {
        if (this.owner.level() != level || !this.canAct() || this.owner.isPassenger() || this.owner.isVehicle() || this.hasPendingAbility() || now < this.nextDashAt || target != null && !this.isVisibleThreat(target)) return false;
        this.nextDashAt = now + VampireEndermanConfig.dashCooldownTicks(); this.pendingDash = plan;
        this.begin(target, CombatPhase.DASH_WINDUP, now + VampireEndermanConfig.dashPreparationTicks()); this.huntress.abilityWarning(level, CombatPhase.DASH_WINDUP); return true;
    }
    private void begin(@Nullable LivingEntity target, CombatPhase phase, long end) {
        if (target != null) this.rememberVisibleThreat(target);
        this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.brace(); this.pendingTarget = target == null ? null : new WeakReference<>(target); this.state = State.COMBAT;
        this.phaseEndsAt = end; this.huntress.setCombatPhase(phase, end);
    }
    private void beginLeap(long now) {
        this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.pendingTarget = this.trackedTarget;
        this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0);
        this.state = State.COMBAT; this.phaseEndsAt = now + 40; this.huntress.setCombatPhase(CombatPhase.LEAP, this.phaseEndsAt);
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.huntress.getCombatPhase(); if (!phase.isCommitted()) return false;
        if (phase == CombatPhase.DASH) {
            if (this.pendingTarget != null && !this.isLegalThreat(this.getPendingTarget()) || !this.huntress.getMobility().tickDash(level)) this.finish(now);
            return true;
        }
        if (phase == CombatPhase.LEAP) {
            if (this.pendingTarget != null && !this.isLegalThreat(this.getPendingTarget()) || now >= this.phaseEndsAt || now - this.huntress.getCombatPhaseStart() > 2 && this.owner.onGround()) this.finish(now);
            return true;
        }
        this.brace();
        if (phase == CombatPhase.RECOVERY || phase == CombatPhase.STRIKE || phase == CombatPhase.DRAIN) {
            if (now >= this.phaseEndsAt) { if (phase == CombatPhase.RECOVERY) this.huntress.setCombatPhase(this.huntress.isFuryActive() ? CombatPhase.FURY : CombatPhase.PURSUING); else this.finish(now); }
            return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (this.pendingTarget != null && (!this.isLegalThreat(target) || !this.isVisibleThreat(target)) || this.owner.isPassenger() || this.owner.isVehicle()) { this.finish(now); return true; }
        if (target != null) { this.rememberVisibleThreat(target); this.owner.getLookControl().setLookAt(target, 40, 40); }
        if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.DASH_WINDUP) {
            if (this.pendingDash == null || !this.huntress.getMobility().beginDash(this.pendingDash)) { this.finish(now); return true; }
            this.huntress.setCombatPhase(CombatPhase.DASH); this.pendingDash = null; return true;
        }
        if (target == null) { this.finish(now); return true; }
        if (phase == CombatPhase.STRIKE_WINDUP) {
            this.huntress.setCombatPhase(CombatPhase.STRIKE); this.strikeImpact = true;
            try { this.huntress.performVampiricStrike(level, target); } finally { this.strikeImpact = false; this.pendingTarget = null; }
            if (this.huntress.getLastDrainHealing() > 0) this.huntress.setCombatPhase(CombatPhase.DRAIN); this.phaseEndsAt = now + 4;
        } else if (phase == CombatPhase.FURY_WINDUP) {
            this.furyRelease = true;
            try { this.huntress.beginBloodFury(level, now); } finally { this.furyRelease = false; this.pendingTarget = null; }
            this.finish(now);
        }
        return true;
    }
    public boolean isStrikeImpactFrame(LivingEntity target) { return this.strikeImpact && this.getPendingTarget() == target && this.huntress.getCombatPhase() == CombatPhase.STRIKE; }
    public boolean isFuryReleaseFrame() { return this.furyRelease && this.huntress.getCombatPhase() == CombatPhase.FURY_WINDUP; }
    private void finish(long now) { this.cancelPending(); this.phaseEndsAt = now + VampireEndermanConfig.recoveryTicks(); this.huntress.setCombatPhase(CombatPhase.RECOVERY, this.phaseEndsAt); }
    private void cancelPending() { this.pendingTarget = null; this.pendingDash = null; this.strikeImpact = this.furyRelease = false; if (this.huntress.getMobility() != null) this.huntress.getMobility().clear(); }
    private void brace() { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return; this.nextProgressAt = now + 20;
        if (this.progressPosition != null && this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 9 && this.owner.position().distanceToSqr(this.progressPosition) < .3) this.blockedTicks = Math.min(100, this.blockedTicks + 20); else this.blockedTicks = 0; this.progressPosition = this.owner.position();
    }
    private boolean tryReposition(long now) {
        if (now < this.nextTeleportAt || !this.isTargetLocalized() || this.lastLocated == null || this.owner.distanceToSqr(this.lastLocated) < 16) return false;
        this.nextTeleportAt = now + VampireEndermanConfig.tacticalTeleportCooldownTicks(); this.leftFlank = !this.leftFlank; this.stopCombat(); this.clearTarget();
        if (!this.huntress.tryVampireReposition(this.lastLocated, this.leftFlank)) return false;
        this.blockedTicks = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; return true;
    }
    private void investigate(ServerLevel level, long now) {
        Vec3 point = this.lastLocated; if (point == null) return; this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 35, 35);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.brace(); return; }
        if (now >= this.nextPathAt) { this.nextPathAt = now + 20; BlockPos block = BlockPos.containing(point); if (level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) != null) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1); }
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelPending(); this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0; this.progressPosition = null; this.blockedTicks = 0; this.phaseEndsAt = 0;
        this.huntress.clearDrainBuildup(); this.huntress.clearBloodFury(); this.huntress.setActivelyHunting(false); this.huntress.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) { output.putLong("VampireStrikeAfter", this.nextStrikeAt); output.putLong("VampireDashAfter", this.nextDashAt); output.putLong("VampireFuryAfter", this.nextFuryAt); output.putLong("VampireTeleportAfter", this.nextTeleportAt); }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime(); this.nextStrikeAt = Math.min(input.getLongOr("VampireStrikeAfter", 0), now + this.meleeCooldownTicks()); this.nextDashAt = Math.min(input.getLongOr("VampireDashAfter", 0), now + VampireEndermanConfig.dashCooldownTicks());
        this.nextFuryAt = Math.min(input.getLongOr("VampireFuryAfter", 0), now + VampireEndermanConfig.furyCooldownTicks()); this.nextTeleportAt = Math.min(input.getLongOr("VampireTeleportAfter", 0), now + VampireEndermanConfig.tacticalTeleportCooldownTicks());
    }
}
