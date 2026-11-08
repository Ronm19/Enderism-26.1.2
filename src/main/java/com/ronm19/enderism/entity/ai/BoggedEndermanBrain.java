package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.BoggedEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.BoggedEnderman;
import com.ronm19.enderism.entity.BoggedEnderman.CombatPhase;
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

/** Shared native Brain actuators choose three finite attacks and observation-limited swamp tactics. */
public final class BoggedEndermanBrain extends MastermindBrain {
    private final BoggedEnderman hunter;
    private @Nullable WeakReference<LivingEntity> trackedTarget, pendingTarget;
    private @Nullable Vec3 lastLocated, mireCenter, stillAnchor, progressPosition;
    private long locatedUntil, stillSince, nextDetectionAt, nextDecisionAt, nextPathAt, nextProgressAt;
    private long nextGraspAt, nextBarrageAt, nextMireAt, nextTeleportAt, phaseEndsAt, nextShotAt;
    private int shotsRemaining, blockedTicks, blockedProjectiles;
    private boolean graspImpact, barrageRelease, mireRelease, leftFlank;

    public BoggedEndermanBrain(BoggedEnderman owner) { super(owner); this.hunter = owner; }
    @Override protected int memoryTicks() { return BoggedEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return BoggedEndermanConfig.graspIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.hunter.isLegalVenomTarget(target); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.lastLocated; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil; }
    public boolean hasPendingAbility() { return this.pendingTarget != null || this.hunter.getCombatPhase().isCommitted(); }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity remembered = super.observeThreat();
        if (remembered != null) return remembered;
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        LivingEntity previous = this.tracked();
        if (level.getGameTime() < this.nextDetectionAt) return this.isVisibleThreat(previous) ? previous : null;
        this.nextDetectionAt = level.getGameTime() + 10;
        double range = Math.min(64, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE)), nearest = range * range;
        ServerPlayer chosen = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearest && this.isVisibleThreat(player)) { chosen = player; nearest = distance; }
        }
        return chosen;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (target != this.tracked()) {
            this.cancelPending(); this.hunter.clearOwnedEffects(); this.blockedTicks = 0; this.blockedProjectiles = 0;
            this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; this.stillAnchor = null;
        }
        super.rememberVisibleThreat(target);
        this.trackedTarget = new WeakReference<>(target); this.lastLocated = target.position(); this.locatedUntil = now + this.memoryTicks();
        if (this.stillAnchor == null || this.stillAnchor.distanceToSqr(target.position()) > 1) { this.stillAnchor = target.position(); this.stillSince = now; }
        this.hunter.setActivelyHunting(true);
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        if (this.trackedTarget != null && !this.isLegalThreat(this.tracked())) this.clearEncounter();
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) { this.hunter.setCombatPhase(CombatPhase.PURSUING); this.hunter.setActivelyHunting(this.isTargetLocalized()); return; }
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible); this.state = State.COMBAT; this.sampleProgress(now);
            // Physical contact is the first choice while Grasp is ready.
            if (this.owner.isWithinMeleeAttackRange(visible) && now >= this.nextGraspAt
                    && !this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) {
                this.hunter.setCombatPhase(CombatPhase.PURSUING); this.tickVisibleCombat(visible);
                if (this.hasPendingAbility()) return;
            }
            if (now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + 10;
                if ((this.blockedTicks >= 40 || this.blockedProjectiles >= 2 || this.owner.isInWater()) && this.tryReposition(level, now)) return;
                if (now - this.stillSince >= 40 && this.tryBeginMire(level, visible, now)) return;
                if (!this.owner.isWithinMeleeAttackRange(visible) && this.tryBeginBarrage(level, visible, now)) return;
            }
            this.hunter.setCombatPhase(CombatPhase.PURSUING); this.tickVisibleCombat(visible); return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.hunter.setCombatPhase(CombatPhase.SEARCHING); this.hunter.setActivelyHunting(true);
            this.sampleProgress(now);
            if (this.blockedTicks >= 40 && now >= this.nextDecisionAt) { this.nextDecisionAt = now + 10; if (this.tryReposition(level, now)) return; }
            this.investigate(level, now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE) this.clearEncounter();
        this.hunter.setCombatPhase(CombatPhase.IDLE); this.hunter.setActivelyHunting(false); this.tickAmbient();
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target) {
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.owner.isPassenger() && !this.owner.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target);
    }
    public boolean tryBeginGrasp(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextGraspAt || !this.owner.isWithinMeleeAttackRange(target)
                || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return false;
        this.rememberVisibleThreat(target); this.begin(target);
        this.nextGraspAt = now + BoggedEndermanConfig.graspIntervalTicks(); this.phaseEndsAt = now + BoggedEndermanConfig.graspWindupTicks();
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, BoggedEndermanConfig.graspIntervalTicks());
        this.hunter.setCombatPhase(CombatPhase.GRASP_WINDUP, this.phaseEndsAt); this.hunter.warningCue(level, false, null); return true;
    }
    public boolean tryBeginBarrage(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextBarrageAt || this.owner.isWithinMeleeAttackRange(target)
                || this.owner.distanceToSqr(target) > square(BoggedEndermanConfig.projectileRange()) || !this.hunter.hasClearProjectilePath(target)) return false;
        this.rememberVisibleThreat(target); this.begin(target);
        this.nextBarrageAt = now + BoggedEndermanConfig.barrageCooldownTicks(); this.shotsRemaining = BoggedEndermanConfig.barrageProjectileCount();
        this.phaseEndsAt = now + BoggedEndermanConfig.barragePreparationTicks();
        this.hunter.setCombatPhase(CombatPhase.BARRAGE_WINDUP, this.phaseEndsAt); this.hunter.warningCue(level, false, null); return true;
    }
    public boolean tryBeginMire(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextMireAt || this.hunter.hasActiveMire() || !target.onGround()
                || this.owner.distanceToSqr(target) > square(BoggedEndermanConfig.mireCastingRange())) return false;
        Vec3 center = target.position();
        if (!this.isValidMireCenter(level, center)) return false;
        this.rememberVisibleThreat(target); this.begin(target); this.mireCenter = center;
        this.nextMireAt = now + BoggedEndermanConfig.mireCooldownTicks(); this.phaseEndsAt = now + BoggedEndermanConfig.mirePreparationTicks();
        this.hunter.setCombatPhase(CombatPhase.MIRE_WINDUP, this.phaseEndsAt); this.hunter.warningCue(level, true, center); return true;
    }
    private boolean isValidMireCenter(ServerLevel level, Vec3 center) {
        double radius = BoggedEndermanConfig.mireRadius();
        BlockPos low = BlockPos.containing(center.x - radius - 1, center.y - 1, center.z - radius - 1);
        BlockPos high = BlockPos.containing(center.x + radius + 1, center.y + 2, center.z + radius + 1);
        return center.isFinite() && low.getY() >= level.getMinY() && high.getY() < level.getMaxY()
                && BoggedEnderman.readyChunks(level, low, high) && level.getWorldBorder().isWithinBounds(low) && level.getWorldBorder().isWithinBounds(high)
                && level.getBlockState(BlockPos.containing(center).below()).isFaceSturdy(level, BlockPos.containing(center).below(), net.minecraft.core.Direction.UP);
    }
    private void begin(LivingEntity target) {
        this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.brace();
        this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT; this.hunter.setActivelyHunting(true);
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.hunter.getCombatPhase();
        if (!phase.isCommitted()) return false;
        this.brace();
        if (phase == CombatPhase.RECOVERY || phase == CombatPhase.GRASP || phase == CombatPhase.MIRE) {
            if (now >= this.phaseEndsAt) {
                if (phase == CombatPhase.RECOVERY) this.hunter.setCombatPhase(CombatPhase.PURSUING);
                else this.finish(now);
            }
            return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || this.owner.isPassenger() || this.owner.isVehicle() || !this.isVisibleThreat(target)) { this.finish(now); return true; }
        this.rememberVisibleThreat(target);
        this.owner.getLookControl().setLookAt(target, 40, 40);
        if (phase == CombatPhase.BARRAGE) {
            if (now < this.nextShotAt) return true;
            if (this.shotsRemaining <= 0 || this.owner.isWithinMeleeAttackRange(target) || !this.hunter.hasClearProjectilePath(target)
                    || this.owner.distanceToSqr(target) > square(BoggedEndermanConfig.projectileRange())) { this.finish(now); return true; }
            this.barrageRelease = true;
            try { if (!this.hunter.launchVenom(level, target)) this.blockedProjectiles++; }
            finally { this.barrageRelease = false; }
            this.shotsRemaining--; this.nextShotAt = now + BoggedEndermanConfig.barrageShotIntervalTicks();
            if (this.shotsRemaining == 0) this.finish(now);
            return true;
        }
        if (now < this.phaseEndsAt) { if (now % 6 == 0) this.hunter.gatheringCue(level, phase == CombatPhase.MIRE_WINDUP ? this.mireCenter : null); return true; }
        if (phase == CombatPhase.GRASP_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.GRASP); this.graspImpact = true;
            try { this.hunter.performVenomousGrasp(level, target); } finally { this.graspImpact = false; this.pendingTarget = null; }
            this.phaseEndsAt = now + 5;
        } else if (phase == CombatPhase.BARRAGE_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.BARRAGE); this.nextShotAt = now;
        } else if (phase == CombatPhase.MIRE_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.MIRE); this.mireRelease = true;
            try { if (this.mireCenter != null && this.isValidMireCenter(level, this.mireCenter)) this.hunter.createToxicMire(level, target, this.mireCenter); }
            finally { this.mireRelease = false; this.pendingTarget = null; this.mireCenter = null; }
            this.phaseEndsAt = now + 6;
        }
        return true;
    }
    public boolean isGraspImpactFrame(LivingEntity target) { return this.graspImpact && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.GRASP; }
    public boolean isBarrageReleaseFrame(LivingEntity target) { return this.barrageRelease && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.BARRAGE; }
    public boolean isMireReleaseFrame(LivingEntity target) { return this.mireRelease && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.MIRE; }
    public void onVenomBlocked() { this.blockedProjectiles = Math.min(4, this.blockedProjectiles + 1); }
    private void cancelPending() { this.pendingTarget = null; this.mireCenter = null; this.shotsRemaining = 0; this.graspImpact = false; this.barrageRelease = false; this.mireRelease = false; }
    private void finish(long now) { this.cancelPending(); this.phaseEndsAt = now + BoggedEndermanConfig.recoveryTicks(); this.hunter.setCombatPhase(CombatPhase.RECOVERY, this.phaseEndsAt); }
    private void brace() {
        this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0);
        this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0);
    }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return;
        this.nextProgressAt = now + 20;
        if (this.progressPosition != null && this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 9
                && this.owner.position().distanceToSqr(this.progressPosition) < .3) this.blockedTicks = Math.min(100, this.blockedTicks + 20);
        else this.blockedTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private boolean tryReposition(ServerLevel level, long now) {
        if (now < this.nextTeleportAt || !this.isTargetLocalized() || this.lastLocated == null || this.owner.distanceToSqr(this.lastLocated) < 16) return false;
        this.nextTeleportAt = now + BoggedEndermanConfig.tacticalTeleportCooldownTicks(); this.leftFlank = !this.leftFlank;
        this.stopCombat(); this.clearTarget();
        if (!this.hunter.trySwampReposition(this.lastLocated, this.leftFlank)) return false;
        this.blockedTicks = 0; this.blockedProjectiles = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20;
        this.hunter.setCombatPhase(CombatPhase.PURSUING); return true;
    }
    private void investigate(ServerLevel level, long now) {
        Vec3 point = this.lastLocated;
        if (point == null) return;
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 35, 35);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.brace(); return; }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20; BlockPos pos = BlockPos.containing(point);
            if (BoggedEnderman.readyChunks(level, pos, pos)) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1);
        }
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelPending(); this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0;
        this.stillAnchor = null; this.progressPosition = null; this.blockedTicks = 0; this.blockedProjectiles = 0; this.phaseEndsAt = 0;
        this.hunter.setActivelyHunting(false); this.hunter.setCombatPhase(CombatPhase.IDLE); this.hunter.clearOwnedEffects();
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("BoggedGraspAfter", this.nextGraspAt); output.putLong("BoggedBarrageAfter", this.nextBarrageAt);
        output.putLong("BoggedMireAfter", this.nextMireAt); output.putLong("BoggedTeleportAfter", this.nextTeleportAt);
    }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime();
        this.nextGraspAt = Math.min(input.getLongOr("BoggedGraspAfter", 0), now + BoggedEndermanConfig.graspIntervalTicks());
        this.nextBarrageAt = Math.min(input.getLongOr("BoggedBarrageAfter", 0), now + BoggedEndermanConfig.barrageCooldownTicks());
        this.nextMireAt = Math.min(input.getLongOr("BoggedMireAfter", 0), now + BoggedEndermanConfig.mireCooldownTicks());
        this.nextTeleportAt = Math.min(input.getLongOr("BoggedTeleportAfter", 0), now + BoggedEndermanConfig.tacticalTeleportCooldownTicks());
    }
    private static double square(double value) { return value * value; }
}
