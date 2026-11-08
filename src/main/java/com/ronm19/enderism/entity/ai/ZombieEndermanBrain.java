package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.ZombieEndermanConfig;
import com.ronm19.enderism.combat.ZombieHordeSignal;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.ZombieEnderman;
import com.ronm19.enderism.entity.ZombieEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import java.util.UUID;
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

/** A native Brain encounter owner: bite, reactive horde awareness and a finite personal frenzy. */
public final class ZombieEndermanBrain extends MastermindBrain {
    private final ZombieEnderman hunter;
    private @Nullable WeakReference<LivingEntity> trackedTarget, pendingTarget, signalAttacker;
    private @Nullable UUID signalAttackerUuid;
    private @Nullable Vec3 lastLocated, signalPoint, progressPosition;
    private long locatedUntil, signalExpiresAt, directThreatUntil, encounterStarted;
    private long nextDetectionAt, nextDecisionAt, nextPathAt, nextProgressAt, nextBiteAt, nextFrenzyAt, nextTeleportAt;
    private long phaseEndsAt, nextShadeAt, shadeUntil, burningSince;
    private int blockedTicks, coordinationSlot;
    private boolean biteImpact, frenzyRelease, leftFlank;

    public ZombieEndermanBrain(ZombieEnderman owner) { super(owner); this.hunter = owner; }
    @Override protected int memoryTicks() { return ZombieEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return this.hunter.isFrenzied() ? ZombieEndermanConfig.frenzyBiteIntervalTicks() : ZombieEndermanConfig.biteIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.hunter.isLegalInfectedTarget(target); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.lastLocated; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil; }
    public boolean hasPendingAbility() { return this.hunter.getCombatPhase().isCommitted(); }
    public @Nullable UUID getSignalAttackerUuid() { return this.signalAttackerUuid; }
    public @Nullable Vec3 getSignalPoint() { return this.signalPoint; }
    public long getSignalExpiresAt() { return this.signalExpiresAt; }
    public boolean hasSignalMemory() { return this.liveSignalAttacker(this.owner.level().getGameTime()) != null; }

    private @Nullable LivingEntity liveSignalAttacker(long now) {
        LivingEntity target = this.signalAttacker == null ? null : this.signalAttacker.get();
        return now < this.signalExpiresAt && this.signalAttackerUuid != null && target != null
                && this.signalAttackerUuid.equals(target.getUUID()) && this.isLegalThreat(target) ? target : null;
    }
    private void clearSignalMemory() {
        this.signalAttacker = null; this.signalAttackerUuid = null; this.signalPoint = null;
        this.signalExpiresAt = 0; this.directThreatUntil = 0; this.coordinationSlot = 0;
    }
    public boolean onReceivedHostileAttack(LivingEntity attacker, Vec3 observedPoint, long now) {
        if (!this.owner.isAlive() || !this.isLegalThreat(attacker) || !observedPoint.isFinite() || this.owner.isRemoved() || this.owner.isNoAi()) return false;
        this.adoptSignal(attacker, observedPoint, now, 0); this.directThreatUntil = now + this.memoryTicks(); return true;
    }
    /** Receiving information never emits damage or rebroadcasts the call. Hidden targets only contribute a frozen point. */
    public boolean receiveHordeSignal(ZombieEnderman source, LivingEntity attacker, Vec3 observedPoint, long now) {
        if (source == this.hunter || source.level() != this.owner.level() || !this.owner.isAlive() || this.owner.isRemoved() || this.owner.isNoAi()
                || !this.isLegalThreat(attacker) || !observedPoint.isFinite()) return false;
        LivingEntity current = this.liveSignalAttacker(now);
        if (current != null && current != attacker && now < this.directThreatUntil) return false;
        this.adoptSignal(attacker, observedPoint, now, Math.floorMod(this.owner.getId() - source.getId(), 5) - 2); return true;
    }
    private void adoptSignal(LivingEntity attacker, Vec3 observedPoint, long now, int slot) {
        this.signalAttacker = new WeakReference<>(attacker); this.signalAttackerUuid = attacker.getUUID();
        this.signalPoint = new Vec3(observedPoint.x, observedPoint.y, observedPoint.z);
        this.signalExpiresAt = now + ZombieEndermanConfig.signalMemoryTicks(); this.coordinationSlot = slot;
        this.hunter.setActivelyHunting(true);
        // Personally visible threats may enter ordinary combat. Otherwise preserve only the message's frozen coordinates.
        if (this.isVisibleThreat(attacker)) this.rememberVisibleThreat(attacker);
        else {
            // A newly signaled hidden attacker supersedes the previous encounter's tracking.
            // Invalidation of that old player must not erase this independent, still-valid alert.
            if (this.tracked() != attacker) {
                this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0;
                this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
                this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                this.progressPosition = null; this.blockedTicks = 0;
            }
            this.stopCombat(); this.clearTarget(); if (!this.hasPendingAbility()) this.hunter.setCombatPhase(CombatPhase.RESPONDING);
        }
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        long now = this.owner.level().getGameTime(); LivingEntity signal = this.liveSignalAttacker(now);
        if (signal != null) return this.isVisibleThreat(signal) ? signal : null;
        LivingEntity remembered = super.observeThreat(); if (remembered != null) return remembered;
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        if (now < this.nextDetectionAt) return this.isVisibleThreat(this.tracked()) ? this.tracked() : null;
        this.nextDetectionAt = now + 10; double range = Math.min(64, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE)), nearest = range * range;
        ServerPlayer chosen = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearest && this.isVisibleThreat(player)) { chosen = player; nearest = distance; }
        }
        return chosen;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (this.trackedTarget == null || target != this.tracked()) { this.encounterStarted = now; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; }
        super.rememberVisibleThreat(target); this.trackedTarget = new WeakReference<>(target); this.lastLocated = target.position();
        this.locatedUntil = now + this.memoryTicks(); this.hunter.setActivelyHunting(true);
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        if (this.signalAttacker != null && this.liveSignalAttacker(now) == null) {
            boolean invalid = this.signalAttacker.get() == null || !this.isLegalThreat(this.signalAttacker.get());
            this.clearSignalMemory(); if (invalid) this.clearEncounter();
        }
        if (this.trackedTarget != null && !this.isLegalThreat(this.tracked())) this.clearEncounter();
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) { this.hunter.setCombatPhase(this.hunter.isFrenzied() ? CombatPhase.FRENZIED : CombatPhase.PURSUING); return; }
        if (this.tickShade(level, now)) return;
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible); this.state = State.COMBAT; this.sampleProgress(now);
            if (now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + 10;
                if (!this.hunter.isFrenzied() && now - this.encounterStarted >= 80 && this.tryBeginFrenzy(level, visible, now)) return;
                if (this.blockedTicks >= 40 && this.tryReposition(level, now)) return;
            }
            this.hunter.setCombatPhase(this.hunter.isFrenzied() ? CombatPhase.FRENZIED : CombatPhase.PURSUING);
            // The shared actuator owns legitimate physical reach, line of sight and the ordinary movement target.
            this.tickVisibleCombat(visible);
            if (!this.hasPendingAbility()) this.applyApproachSpacing(level, visible, now);
            return;
        }
        this.stopCombat(); this.clearTarget();
        LivingEntity signaled = this.liveSignalAttacker(now);
        if (signaled != null && this.signalPoint != null) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.hunter.setActivelyHunting(true);
            this.hunter.setCombatPhase(CombatPhase.RESPONDING); this.investigate(level, this.signalPoint, now); return;
        }
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.hunter.setActivelyHunting(true); this.hunter.setCombatPhase(CombatPhase.SEARCHING);
            this.sampleProgress(now);
            if (this.blockedTicks >= 40 && now >= this.nextDecisionAt) { this.nextDecisionAt = now + 10; if (this.tryReposition(level, now)) return; }
            this.investigate(level, this.lastLocated, now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE || this.hunter.isFrenzied()) this.clearEncounter();
        this.hunter.setCombatPhase(CombatPhase.IDLE); this.hunter.setActivelyHunting(false); this.tickAmbient();
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target) {
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.owner.isPassenger() && !this.owner.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target);
    }
    public boolean tryBeginBite(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextBiteAt || !this.owner.isWithinMeleeAttackRange(target)
                || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return false;
        LivingEntity signal = this.liveSignalAttacker(now); if (signal != null && signal != target) return false;
        this.rememberVisibleThreat(target); this.begin(target); int interval = this.meleeCooldownTicks();
        this.nextBiteAt = now + interval; this.phaseEndsAt = now + ZombieEndermanConfig.biteWindupTicks();
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, interval);
        this.hunter.setCombatPhase(CombatPhase.BITE_WINDUP, this.phaseEndsAt); this.hunter.biteWarning(level); return true;
    }
    public boolean tryBeginFrenzy(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || this.hunter.isFrenzied() || now < this.nextFrenzyAt) return false;
        this.rememberVisibleThreat(target); this.begin(target); this.nextFrenzyAt = now + ZombieEndermanConfig.frenzyCooldownTicks();
        this.phaseEndsAt = now + ZombieEndermanConfig.frenzyPreparationTicks(); this.hunter.setCombatPhase(CombatPhase.FRENZY_WINDUP, this.phaseEndsAt);
        this.hunter.frenzyWarning(level); return true;
    }
    private void begin(LivingEntity target) {
        this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.brace(); this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT;
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.hunter.getCombatPhase(); if (!phase.isCommitted()) return false;
        this.brace();
        if (phase == CombatPhase.RECOVERY || phase == CombatPhase.BITE) {
            if (now >= this.phaseEndsAt) { if (phase == CombatPhase.RECOVERY) this.hunter.setCombatPhase(this.hunter.isFrenzied() ? CombatPhase.FRENZIED : CombatPhase.PURSUING); else this.finish(now); }
            return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || !this.isVisibleThreat(target) || this.owner.isPassenger() || this.owner.isVehicle()) { this.finish(now); return true; }
        LivingEntity signal = this.liveSignalAttacker(now); if (signal != null && signal != target) { this.finish(now); return true; }
        this.rememberVisibleThreat(target); this.owner.getLookControl().setLookAt(target, 40, 40);
        if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.BITE_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.BITE); this.biteImpact = true;
            try { this.hunter.performInfectedBite(level, target); } finally { this.biteImpact = false; this.pendingTarget = null; }
            this.phaseEndsAt = now + 5;
        } else if (phase == CombatPhase.FRENZY_WINDUP) {
            this.frenzyRelease = true;
            try { this.hunter.beginFrenzy(level, now); } finally { this.frenzyRelease = false; this.pendingTarget = null; }
            this.finish(now);
        }
        return true;
    }
    public boolean isBiteImpactFrame(LivingEntity target) { return this.biteImpact && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.BITE; }
    public boolean isFrenzyReleaseFrame() { return this.frenzyRelease && this.hunter.getCombatPhase() == CombatPhase.FRENZY_WINDUP; }
    private void cancelPending() { this.pendingTarget = null; this.biteImpact = false; this.frenzyRelease = false; }
    private void finish(long now) { this.cancelPending(); this.phaseEndsAt = now + ZombieEndermanConfig.recoveryTicks(); this.hunter.setCombatPhase(CombatPhase.RECOVERY, this.phaseEndsAt); }
    private void brace() { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    private void applyApproachSpacing(ServerLevel level, LivingEntity target, long now) {
        if (this.coordinationSlot == 0 || this.owner.distanceToSqr(target) < 16 || now < this.nextPathAt) return;
        this.nextPathAt = now + 15; Vec3 direction = target.position().subtract(this.owner.position()).multiply(1,0,1);
        if (direction.horizontalDistanceSqr() < .01) return;
        Vec3 side = new Vec3(-direction.z,0,direction.x).normalize().scale(this.coordinationSlot * 1.2);
        Vec3 position = target.position().add(side); BlockPos block = BlockPos.containing(position);
        if (ZombieEnderman.readyChunks(level, block, block)) this.owner.getNavigation().moveTo(position.x, position.y, position.z, 1);
    }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return; this.nextProgressAt = now + 20;
        if (this.progressPosition != null && this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 9
                && this.owner.position().distanceToSqr(this.progressPosition) < .3) this.blockedTicks = Math.min(100, this.blockedTicks + 20); else this.blockedTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private boolean tryReposition(ServerLevel level, long now) {
        if (now < this.nextTeleportAt || !this.isTargetLocalized() || this.lastLocated == null || this.owner.distanceToSqr(this.lastLocated) < 16) return false;
        this.nextTeleportAt = now + ZombieEndermanConfig.tacticalTeleportCooldownTicks(); this.leftFlank = !this.leftFlank;
        this.stopCombat(); this.clearTarget();
        if (!this.hunter.tryHordeReposition(this.lastLocated, this.leftFlank)) return false;
        this.blockedTicks = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; return true;
    }
    private void investigate(ServerLevel level, @Nullable Vec3 point, long now) {
        if (point == null) return; this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 35, 35);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.brace(); return; }
        if (now >= this.nextPathAt) { this.nextPathAt = now + 20; BlockPos pos = BlockPos.containing(point);
            if (ZombieEnderman.readyChunks(level, pos, pos)) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1); }
    }
    private boolean tickShade(ServerLevel level, long now) {
        if (!this.owner.isOnFire() || !level.isBrightOutside()) { this.burningSince = 0; this.shadeUntil = 0; return false; }
        if (this.burningSince == 0) this.burningSince = now;
        if (this.shadeUntil > now && !this.owner.getNavigation().isDone()) { this.hunter.setCombatPhase(CombatPhase.SEEKING_SHADE); return true; }
        if (now - this.burningSince < 40 || now < this.nextShadeAt) return false;
        this.nextShadeAt = now + 80; Vec3 shade = this.hunter.findNearbyShade(); if (shade == null) return false;
        this.stopCombat(); this.stopAmbient(); this.clearTarget();
        if (!this.owner.getNavigation().moveTo(shade.x, shade.y, shade.z, 1.1)) return false;
        this.shadeUntil = now + 60; this.hunter.setCombatPhase(CombatPhase.SEEKING_SHADE); return true;
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelPending(); this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0;
        this.clearSignalMemory(); this.progressPosition = null; this.blockedTicks = 0; this.phaseEndsAt = 0;
        this.hunter.clearFrenzy(); this.hunter.setActivelyHunting(false); this.hunter.setCombatPhase(CombatPhase.IDLE); ZombieHordeSignal.clear(this.hunter);
    }
    public void writeCooldowns(ValueOutput output) { output.putLong("ZombieBiteAfter", this.nextBiteAt); output.putLong("ZombieFrenzyAfter", this.nextFrenzyAt); output.putLong("ZombieTeleportAfter", this.nextTeleportAt); }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime(); this.nextBiteAt = Math.min(input.getLongOr("ZombieBiteAfter", 0), now + ZombieEndermanConfig.biteIntervalTicks());
        this.nextFrenzyAt = Math.min(input.getLongOr("ZombieFrenzyAfter", 0), now + ZombieEndermanConfig.frenzyCooldownTicks());
        this.nextTeleportAt = Math.min(input.getLongOr("ZombieTeleportAfter", 0), now + ZombieEndermanConfig.tacticalTeleportCooldownTicks());
    }
}
