package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.BloodEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.BloodEnderman;
import com.ronm19.enderism.entity.BloodEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** One native Brain switches between melee, finite Lance casts, and frozen last-seen investigation. */
public final class BloodEndermanBrain extends MastermindBrain {
    private final BloodEnderman blood;
    private @Nullable WeakReference<LivingEntity> castTarget;
    private @Nullable UUID observedTarget;
    private @Nullable Vec3 lastObserved;
    private Vec3 observedVelocity = Vec3.ZERO;
    private @Nullable Vec3 progressPosition;
    private long observedAt;
    private long castReleaseAt;
    private long recoveryUntil;
    private long releasePoseUntil;
    private long nextClawAt;
    private long nextLanceAt;
    private long nextTeleportAt;
    private long nextDetectionAt;
    private long nextDecisionAt;
    private long nextProgressAt;
    private int stalledTicks;
    private int blockedAttempts;
    private long lastBlockedLanceAt = Long.MIN_VALUE / 2;
    private boolean leftSide;

    public BloodEndermanBrain(BloodEnderman owner) { super(owner); this.blood = owner; }
    @Override protected int memoryTicks() { return BloodEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return BloodEndermanConfig.meleeInterval(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && !(target instanceof ServerPlayer p && p.hasDisconnected());
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity remembered = super.observeThreat();
        if (remembered != null) return remembered;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime() < this.nextDetectionAt) return null;
        this.nextDetectionAt = level.getGameTime() + BloodEndermanConfig.sensorInterval();
        double range = this.owner.getAttributeValue(Attributes.FOLLOW_RANGE), nearestDistance = square(range);
        ServerPlayer nearest = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearestDistance && this.isVisibleThreat(player)) { nearest = player; nearestDistance = distance; }
        }
        if (nearest != null) this.rememberVisibleThreat(nearest);
        return nearest;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (!target.getUUID().equals(this.observedTarget)) {
            this.cancelCast();
            this.observedVelocity = Vec3.ZERO;
            this.progressPosition = this.owner.position();
            this.stalledTicks = 0; this.blockedAttempts = 0;
        } else if (this.lastObserved != null && now > this.observedAt) {
            Vec3 measured = target.position().subtract(this.lastObserved).multiply(1, 0, 1).scale(1.0 / (now - this.observedAt));
            this.observedVelocity = measured.lengthSqr() > .1225 ? measured.normalize().scale(.35) : measured;
        }
        super.rememberVisibleThreat(target);
        this.observedTarget = target.getUUID(); this.lastObserved = target.position(); this.observedAt = now;
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        var remembered = body.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (remembered != null && !this.isLegalThreat(remembered)) this.clearEncounter();
        LivingEntity visible = this.observeThreat();
        if (this.castTarget != null) { this.tickCast(level, now); return; }
        if (now < this.recoveryUntil) {
            this.brace();
            this.blood.setCombatPhase(now < this.releasePoseUntil ? CombatPhase.LANCE_RELEASE : CombatPhase.RECOVERY);
            return;
        }
        if (this.tickFloating()) { this.blood.setCombatPhase(CombatPhase.SEARCHING); return; }
        this.sampleProgress(now);
        if (visible == null) { this.search(level, now); return; }

        boolean close = this.owner.isWithinMeleeAttackRange(visible);
        if (!close && now >= this.nextDecisionAt) {
            this.nextDecisionAt = now + BloodEndermanConfig.decisionInterval();
            if (now - this.lastBlockedLanceAt < this.memoryTicks() && this.tryReposition(now)) return;
            if (this.tryBeginLance(level, visible, now)) return;
            boolean blocked = !this.blood.hasClearProjectilePath(visible);
            if (blocked) this.blockedAttempts = Math.min(8, this.blockedAttempts + 1);
            else this.blockedAttempts = 0;
            boolean retreating = this.lastObserved != null && this.observedVelocity.dot(
                    this.lastObserved.subtract(this.owner.position()).multiply(1, 0, 1).normalize()) > .04;
            if ((this.blockedAttempts >= 2 || this.stalledTicks >= 20
                    || retreating && this.owner.distanceToSqr(visible) > 144) && this.tryReposition(now)) return;
        }
        // A finished cast/recovery must stop bracing before the shared native melee actuator runs.
        if (this.blood.getCombatPhase().bracesCaster()) this.blood.setCombatPhase(CombatPhase.PURSUIT);
        this.tickVisibleCombat(visible);
        if (this.blood.getCombatPhase() != CombatPhase.BUTCHERY || now - this.blood.getCombatPhaseStart() >= 8)
            this.blood.setCombatPhase(CombatPhase.PURSUIT);
    }
    public boolean canUseButchery(ServerLevel level, LivingEntity target) {
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.blood.getCombatPhase().bracesCaster() && level.getGameTime() >= this.nextClawAt
                && !this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                && this.isLegalThreat(target)
                && this.owner.isWithinMeleeAttackRange(target) && this.owner.getSensing().hasLineOfSight(target);
    }
    public void onButcheryAttempt(ServerLevel level) {
        this.nextClawAt = level.getGameTime() + this.meleeCooldownTicks();
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, Math.max(1, this.meleeCooldownTicks() - 1));
    }
    public boolean tryBeginLance(ServerLevel level, LivingEntity target, long now) {
        double distance = this.owner.distanceToSqr(target);
        if (now != level.getGameTime() || this.owner.level() != level || this.castTarget != null
                || now < this.nextLanceAt || now < this.recoveryUntil || now < this.nextClawAt
                || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                || distance < square(BloodEndermanConfig.lanceMinimumRange())
                || this.owner.isInWater() || this.owner.isInLava() || !this.isVisibleThreat(target)
                || !this.blood.canUseRangedAttack(target)) return false;
        this.rememberVisibleThreat(target);
        this.nextLanceAt = now + BloodEndermanConfig.lanceCooldown();
        this.castTarget = new WeakReference<>(target);
        this.castReleaseAt = now + BloodEndermanConfig.lanceWindup();
        this.stopAmbient(); this.stopCombat(); this.clearTarget(); this.brace(); this.state = State.COMBAT;
        this.owner.getLookControl().setLookAt(target, 40, 35);
        this.blood.setCombatPhase(CombatPhase.LANCE_WINDUP); this.blood.warnLance(level);
        return true;
    }
    private void tickCast(ServerLevel level, long now) {
        LivingEntity target = this.castTarget.get();
        if (target == null || !this.isLegalThreat(target)) { this.clearEncounter(); return; }
        if (this.owner.isWithinMeleeAttackRange(target) || !this.isVisibleThreat(target)
                || !this.blood.canUseRangedAttack(target) || this.owner.isInWater() || this.owner.isInLava()) {
            this.cancelCast(); return;
        }
        this.brace(); this.owner.getLookControl().setLookAt(target, 40, 35);
        if (now < this.castReleaseAt) return;
        if (!this.blood.launchBloodLance(target, this.observedVelocity)) { this.cancelCast(); return; }
        this.castTarget = null;
        this.recoveryUntil = now + BloodEndermanConfig.lanceRecovery(); this.releasePoseUntil = Math.min(this.recoveryUntil, now + 4);
        this.blood.setCombatPhase(CombatPhase.LANCE_RELEASE);
    }
    public void cancelCast() {
        this.castTarget = null; this.castReleaseAt = 0;
        if (this.blood.getCombatPhase() == CombatPhase.LANCE_WINDUP) this.blood.setCombatPhase(CombatPhase.PURSUIT);
    }
    /** A real projectile obstruction informs only this confrontation, never a permanent route database. */
    public void onLanceBlocked() { this.lastBlockedLanceAt = this.owner.level().getGameTime(); }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return;
        int interval = BloodEndermanConfig.pathInterval(); this.nextProgressAt = now + interval;
        Vec3 observed = this.getLastObservedPosition();
        if (observed != null && this.owner.distanceToSqr(observed) > 16 && this.progressPosition != null
                && this.owner.distanceToSqr(this.progressPosition) < .16) this.stalledTicks += interval;
        else this.stalledTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private boolean tryReposition(long now) {
        Vec3 observed = this.getLastObservedPosition();
        if (observed == null || now < this.nextTeleportAt || this.owner.isInWater() || this.owner.isInLava()
                || this.owner.distanceToSqr(observed) < 36 || this.owner.distanceToSqr(observed) > square(BloodEndermanConfig.teleportRange())) return false;
        this.nextTeleportAt = now + BloodEndermanConfig.teleportCooldown();
        Vec3 radial = this.owner.position().subtract(observed).multiply(1, 0, 1).normalize();
        if (radial.lengthSqr() < .01) radial = new Vec3(1, 0, 0);
        Vec3 side = new Vec3(-radial.z, 0, radial.x).scale(this.leftSide ? 1 : -1); this.leftSide = !this.leftSide;
        Vec3 requested = observed.add(radial.scale(1.5)).add(side.scale(BloodEndermanConfig.teleportDistance())).add(0, 1, 0);
        if (!this.blood.tryTacticalTeleport(requested, observed)) return false;
        this.progressPosition = this.owner.position(); this.stalledTicks = 0; this.blockedAttempts = 0;
        this.lastBlockedLanceAt = Long.MIN_VALUE / 2;
        this.blood.setCombatPhase(CombatPhase.PURSUIT); return true;
    }
    private void search(ServerLevel level, long now) {
        this.stopCombat(); this.clearTarget();
        if (this.stalledTicks >= 20 && this.tryReposition(now)) return;
        if (this.tickInvestigation(level)) {
            if (this.state == State.INVESTIGATE) this.blood.setCombatPhase(CombatPhase.SEARCHING);
            return;
        }
        if (this.state != State.IDLE || this.observedTarget != null) this.clearEncounter();
        this.blood.setCombatPhase(CombatPhase.IDLE); this.tickAmbient();
    }
    private void brace() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0);
        this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0);
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelCast(); this.recoveryUntil = 0; this.releasePoseUntil = 0;
        this.observedTarget = null; this.lastObserved = null; this.observedVelocity = Vec3.ZERO; this.observedAt = 0;
        this.progressPosition = null; this.stalledTicks = 0; this.blockedAttempts = 0;
        this.lastBlockedLanceAt = Long.MIN_VALUE / 2;
        this.blood.cancelOwnedLances(); this.blood.setCombatPhase(CombatPhase.IDLE);
        // Bloodlust is a finite vanilla status effect; acquiring/losing a target must not cancel its earned healing.
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("BloodButcheryReadyAt", this.nextClawAt);
        output.putLong("BloodLanceReadyAt", this.nextLanceAt);
        output.putLong("BloodTeleportReadyAt", this.nextTeleportAt);
    }
    public void readCooldowns(ValueInput input) {
        this.nextClawAt = input.getLongOr("BloodButcheryReadyAt", 0L);
        this.nextLanceAt = input.getLongOr("BloodLanceReadyAt", 0L);
        this.nextTeleportAt = input.getLongOr("BloodTeleportReadyAt", 0L);
    }
    private static double square(double n) { return n * n; }
}
