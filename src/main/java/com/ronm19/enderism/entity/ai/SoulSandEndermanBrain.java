package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.SoulSandEndermanConfig;
import com.ronm19.enderism.combat.SoulSandHuntPlanner;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.SoulSandEnderman;
import com.ronm19.enderism.entity.SoulSandEnderman.CombatPhase;
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

/** Native Brain specialization: observed pursuit, actual soul-sand ground routes and three guarded actions. */
public final class SoulSandEndermanBrain extends MastermindBrain {
    private final SoulSandEnderman hunter;
    private final SoulSandHuntPlanner planner;
    private @Nullable WeakReference<LivingEntity> trackedTarget, pendingTarget;
    private @Nullable Vec3 lastLocated, abilityAnchor, progressPosition, routeDestination;
    private long locatedUntil, encounterStarted, nextDetectionAt, nextDecisionAt, nextPathAt, nextProgressAt;
    private long nextStrikeAt, nextEruptionAt, nextDominionAt, nextTeleportAt, phaseEndsAt;
    private int blockedTicks;
    private boolean strikeImpact, eruptionRelease, dominionRelease, leftFlank;
    public SoulSandEndermanBrain(SoulSandEnderman owner) { super(owner); this.hunter = owner; this.planner = new SoulSandHuntPlanner(owner); }
    @Override protected int memoryTicks() { return SoulSandEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return SoulSandEndermanConfig.strikeIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.hunter.isLegalSoulTarget(target); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.lastLocated; }
    public @Nullable Vec3 getAbilityAnchor() { return this.abilityAnchor; }
    public @Nullable Vec3 getPlannedDestination() { return this.routeDestination; }
    public SoulSandHuntPlanner getPlanner() { return this.planner; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil; }
    public boolean hasPendingAbility() { return this.pendingTarget != null || this.hunter.getCombatPhase().isCommitted(); }
    public boolean isTacticalAnchorValid(Vec3 anchor) { return anchor.isFinite() && this.isTargetLocalized() && this.lastLocated != null && this.lastLocated.distanceToSqr(anchor) < .001; }
    public boolean isAbilityAnchorValid(Vec3 anchor) { return anchor.isFinite() && this.canAct() && this.isLegalThreat(this.getPendingTarget()) && this.abilityAnchor != null && this.abilityAnchor.distanceToSqr(anchor) < .001; }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity attacker = this.owner.getLastHurtByMob(); int hurt = this.owner.getLastHurtByMobTimestamp();
        if (hurt != this.handledHurtTimestamp && this.owner.tickCount - hurt >= 0 && this.owner.tickCount - hurt <= 40 && this.isVisibleThreat(attacker)) { this.handledHurtTimestamp = hurt; return attacker; }
        LivingEntity current = this.tracked(); if (this.isVisibleThreat(current)) return current;
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime(); if (now < this.nextDetectionAt) return null; this.nextDetectionAt = now + SoulSandEndermanConfig.decisionIntervalTicks();
        double range = Math.min(64, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE));
        var players = level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range)); players.sort(Comparator.comparingDouble(player -> player.distanceToSqr(this.owner)));
        int sampled = 0; for (var player : players) { if (++sampled > 16) break; if (this.isVisibleThreat(player)) return player; } return null;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        if (!this.isVisibleThreat(target)) return;
        long now = this.owner.level().getGameTime();
        if (target != this.tracked()) { this.encounterStarted = now; this.planner.clear(); this.routeDestination = null; this.blockedTicks = 0; this.nextPathAt = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; }
        super.rememberVisibleThreat(target); this.trackedTarget = new WeakReference<>(target); this.lastLocated = target.position();
        this.locatedUntil = now + this.memoryTicks(); this.hunter.setActivelyHunting(true);
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!this.canAct() || this.trackedTarget != null && !this.isLegalThreat(this.tracked())) { this.clearEncounter(); return; }
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) { this.hunter.setCombatPhase(CombatPhase.PURSUING); return; }
        if (now < this.nextDecisionAt) return; this.nextDecisionAt = now + SoulSandEndermanConfig.decisionIntervalTicks();
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible); this.state = State.COMBAT; this.stopAmbient(); this.sampleProgress(now);
            if (now - this.encounterStarted >= SoulSandEndermanConfig.dominionMinEncounterTicks() && this.tryBeginDominion(level, visible, now)) return;
            if (now - this.encounterStarted >= SoulSandEndermanConfig.eruptionMinEncounterTicks() && this.tryBeginEruption(level, visible, now)) return;
            this.hunter.setCombatPhase(this.hunter.isDominionActive() ? CombatPhase.DOMINION : CombatPhase.PURSUING);
            if (this.owner.isWithinMeleeAttackRange(visible)) { this.routeDestination = null; this.tickVisibleCombat(visible); return; }
            this.stopCombat(); this.clearTarget(); this.approach(level, now); return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.hunter.setCombatPhase(this.hunter.isDominionActive() ? CombatPhase.DOMINION : CombatPhase.SEARCHING); this.sampleProgress(now); this.approach(level, now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE || this.hunter.isActivelyHunting()) this.clearEncounter();
        this.hunter.setCombatPhase(CombatPhase.IDLE); this.tickAmbient();
    }
    private boolean canAct() { return this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi(); }
    private boolean mayBegin(ServerLevel level, LivingEntity target) { return this.owner.level() == level && this.canAct() && !this.owner.isPassenger() && !this.owner.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target); }
    public boolean tryBeginStrike(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextStrikeAt || !this.owner.isWithinMeleeAttackRange(target) || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return false;
        this.nextStrikeAt = now + this.meleeCooldownTicks(); this.begin(target, CombatPhase.STRIKE_WINDUP, now + SoulSandEndermanConfig.strikeWindupTicks());
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, this.meleeCooldownTicks()); this.hunter.abilityWarning(level, CombatPhase.STRIKE_WINDUP, null); return true;
    }
    public boolean tryBeginEruption(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextEruptionAt || this.owner.distanceToSqr(target) > SoulSandEndermanConfig.eruptionCastRange() * SoulSandEndermanConfig.eruptionCastRange() || !this.prepareArea(level, target, false)) return false;
        this.nextEruptionAt = now + SoulSandEndermanConfig.eruptionCooldownTicks(); this.begin(target, CombatPhase.ERUPTION_WINDUP, now + SoulSandEndermanConfig.eruptionWarningTicks()); this.hunter.abilityWarning(level, CombatPhase.ERUPTION_WINDUP, this.abilityAnchor); return true;
    }
    public boolean tryBeginDominion(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextDominionAt || this.hunter.getSoulfireCombat().isDominionActive() || this.owner.distanceToSqr(target) > SoulSandEndermanConfig.dominionCastRange() * SoulSandEndermanConfig.dominionCastRange() || !this.prepareArea(level, target, true)) return false;
        this.nextDominionAt = now + SoulSandEndermanConfig.dominionCooldownTicks(); this.begin(target, CombatPhase.DOMINION_WINDUP, now + SoulSandEndermanConfig.dominionWarningTicks()); this.hunter.abilityWarning(level, CombatPhase.DOMINION_WINDUP, this.abilityAnchor); return true;
    }
    private boolean prepareArea(ServerLevel level, LivingEntity target, boolean dominion) {
        Vec3 anchor = this.planner.standing(level, target.position());
        // The victim occupies his exact ground cell. Ground geometry remains a legitimate fixed observation.
        if (anchor == null) anchor = target.position();
        this.abilityAnchor = anchor; this.pendingTarget = new WeakReference<>(target);
        if (this.hunter.getSoulfireCombat().canManifest(level, anchor, dominion)) return true;
        this.cancelPending(); return false;
    }
    private void begin(LivingEntity target, CombatPhase phase, long end) {
        this.rememberVisibleThreat(target); this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.brace();
        this.pendingTarget = new WeakReference<>(target); this.routeDestination = null; this.state = State.COMBAT; this.phaseEndsAt = end; this.hunter.setCombatPhase(phase, end);
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.hunter.getCombatPhase(); if (!phase.isCommitted()) return false; this.brace();
        if (phase == CombatPhase.RECOVERY || phase == CombatPhase.STRIKE || phase == CombatPhase.ERUPTION) {
            if (now >= this.phaseEndsAt) { if (phase == CombatPhase.RECOVERY) this.hunter.setCombatPhase(this.hunter.isDominionActive() ? CombatPhase.DOMINION : CombatPhase.PURSUING); else this.finish(now); } return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || !this.isVisibleThreat(target) || this.owner.isPassenger() || this.owner.isVehicle()) { this.finish(now); return true; }
        this.rememberVisibleThreat(target); this.owner.getLookControl().setLookAt(target, 35, 35); if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.STRIKE_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.STRIKE); this.strikeImpact = true;
            try { this.hunter.performSoulfireStrike(level, target); } finally { this.strikeImpact = false; this.pendingTarget = null; } this.phaseEndsAt = now + 4;
        } else if (phase == CombatPhase.ERUPTION_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.ERUPTION); this.eruptionRelease = true;
            try { if (this.abilityAnchor != null) this.hunter.getSoulfireCombat().erupt(level, this.abilityAnchor, now); } finally { this.eruptionRelease = false; this.pendingTarget = null; this.abilityAnchor = null; } this.phaseEndsAt = now + 5;
        } else if (phase == CombatPhase.DOMINION_WINDUP) {
            this.dominionRelease = true;
            try { if (this.abilityAnchor != null) this.hunter.getSoulfireCombat().beginDominion(level, this.abilityAnchor, now); } finally { this.dominionRelease = false; this.pendingTarget = null; this.abilityAnchor = null; } this.finish(now);
        }
        return true;
    }
    public boolean isStrikeImpactFrame(LivingEntity target) { return this.strikeImpact && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.STRIKE; }
    public boolean isEruptionReleaseFrame() { return this.eruptionRelease && this.hunter.getCombatPhase() == CombatPhase.ERUPTION; }
    public boolean isDominionReleaseFrame() { return this.dominionRelease && this.hunter.getCombatPhase() == CombatPhase.DOMINION_WINDUP; }
    private void finish(long now) { this.cancelPending(); this.phaseEndsAt = now + SoulSandEndermanConfig.recoveryTicks(); this.hunter.setCombatPhase(CombatPhase.RECOVERY, this.phaseEndsAt); }
    private void cancelPending() { this.pendingTarget = null; this.abilityAnchor = null; this.strikeImpact = this.eruptionRelease = this.dominionRelease = false; }
    private void brace() { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return; this.nextProgressAt = now + 20;
        if (this.progressPosition != null && this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 9 && this.owner.position().distanceToSqr(this.progressPosition) < .2) this.blockedTicks = Math.min(100, this.blockedTicks + 20); else this.blockedTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private void approach(ServerLevel level, long now) {
        if (this.lastLocated == null) return; this.owner.getLookControl().setLookAt(this.lastLocated.x, this.lastLocated.y + 2, this.lastLocated.z, 35, 35);
        if (this.owner.distanceToSqr(this.lastLocated) <= 2.25) { this.brace(); return; }
        if (this.blockedTicks >= 40 && this.tryReposition(now)) return;
        if (now < this.nextPathAt) return; this.nextPathAt = now + SoulSandEndermanConfig.movementDecisionTicks();
        var plan = this.planner.plan(level, this.lastLocated, now);
        if (plan == null) { this.blockedTicks = Math.min(100, this.blockedTicks + 20); if (this.routeDestination != null) this.planner.failed(this.routeDestination, now); return; }
        this.routeDestination = plan.destination(); this.owner.getNavigation().moveTo(plan.path(), 1);
    }
    private boolean tryReposition(long now) {
        if (now < this.nextTeleportAt || !this.isTargetLocalized() || this.lastLocated == null || this.owner.distanceToSqr(this.lastLocated) < 16) return false;
        this.nextTeleportAt = now + SoulSandEndermanConfig.tacticalTeleportCooldownTicks(); this.leftFlank = !this.leftFlank;
        this.stopCombat(); this.clearTarget(); double side = this.leftFlank ? 3.5 : -3.5;
        for (Vec3 point : new Vec3[] {this.lastLocated.add(side, 2, 0), this.lastLocated.add(0, 2, side)}) {
            if (this.hunter.trySoulReposition(point, this.lastLocated)) { this.routeDestination = null; this.blockedTicks = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; return true; }
        }
        return false;
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelPending(); this.trackedTarget = null; this.lastLocated = this.progressPosition = this.routeDestination = null;
        this.locatedUntil = this.encounterStarted = this.phaseEndsAt = this.nextPathAt = this.nextProgressAt = 0; this.blockedTicks = 0; this.leftFlank = false; this.planner.clear();
        this.hunter.clearSoulfire(); this.hunter.setActivelyHunting(false); this.hunter.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) { output.putLong("SoulStrikeAfter", this.nextStrikeAt); output.putLong("SoulEruptionAfter", this.nextEruptionAt); output.putLong("SoulDominionAfter", this.nextDominionAt); output.putLong("SoulTeleportAfter", this.nextTeleportAt); }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime(); this.nextStrikeAt = Math.min(input.getLongOr("SoulStrikeAfter", 0), now + this.meleeCooldownTicks());
        this.nextEruptionAt = Math.min(input.getLongOr("SoulEruptionAfter", 0), now + SoulSandEndermanConfig.eruptionCooldownTicks()); this.nextDominionAt = Math.min(input.getLongOr("SoulDominionAfter", 0), now + SoulSandEndermanConfig.dominionCooldownTicks());
        this.nextTeleportAt = Math.min(input.getLongOr("SoulTeleportAfter", 0), now + SoulSandEndermanConfig.tacticalTeleportCooldownTicks());
    }
}
