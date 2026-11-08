package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.HuskEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.HuskEnderman;
import com.ronm19.enderism.entity.HuskEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared Brain actuators with three exclusive, finite attrition actions and observed encounter memory. */
public final class HuskEndermanBrain extends MastermindBrain {
    private final HuskEnderman hunter;
    private @Nullable WeakReference<LivingEntity> trackedTarget, pendingTarget;
    private @Nullable Vec3 lastLocated, progressPosition;
    private long locatedUntil, encounterStarted, nextDetectionAt, nextDecisionAt, nextPathAt, nextProgressAt;
    private long nextStrikeAt, nextCurseAt, nextDeliriumAt, nextTeleportAt, phaseEndsAt;
    private int blockedTicks;
    private boolean strikeImpact, curseRelease, deliriumRelease, leftFlank;

    public HuskEndermanBrain(HuskEnderman owner) { super(owner); this.hunter = owner; }
    @Override protected int memoryTicks() { return HuskEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return HuskEndermanConfig.strikeIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.hunter.isLegalHuskTarget(target); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.lastLocated; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil; }
    public boolean hasPendingAbility() { return this.hunter.getCombatPhase().isCommitted(); }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity threat = super.observeThreat(); if (threat != null) return threat;
        long now = this.owner.level().getGameTime();
        if (this.isVisibleThreat(this.tracked())) return this.tracked();
        if (!(this.owner.level() instanceof ServerLevel level) || now < this.nextDetectionAt) return null;
        this.nextDetectionAt = now + 10;
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
        if (target != this.tracked()) { this.encounterStarted = now; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; }
        super.rememberVisibleThreat(target); this.trackedTarget = new WeakReference<>(target); this.lastLocated = target.position();
        this.locatedUntil = now + this.memoryTicks(); this.hunter.setActivelyHunting(true);
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!this.canAct()) { this.clearEncounter(); return; }
        if (this.trackedTarget != null && !this.isLegalThreat(this.tracked())) this.clearEncounter();
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) { this.hunter.setCombatPhase(CombatPhase.PURSUING); return; }
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible); this.state = State.COMBAT; this.sampleProgress(now);
            if (now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + 10;
                if (now - this.encounterStarted >= 120 && this.tryBeginDelirium(level, visible, now)) return;
                if (now - this.encounterStarted >= 50 && this.tryBeginCurse(level, visible, now)) return;
                if (this.blockedTicks >= 40 && this.tryReposition(now)) return;
            }
            this.hunter.setCombatPhase(CombatPhase.PURSUING); this.tickVisibleCombat(visible); return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.hunter.setCombatPhase(CombatPhase.SEARCHING); this.hunter.setActivelyHunting(true);
            this.sampleProgress(now);
            if (this.blockedTicks >= 40 && now >= this.nextDecisionAt) { this.nextDecisionAt = now + 10; if (this.tryReposition(now)) return; }
            this.investigate(level, now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE || this.hunter.isActivelyHunting()) this.clearEncounter();
        this.hunter.setCombatPhase(CombatPhase.IDLE); this.tickAmbient();
    }
    private boolean canAct() { return this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi(); }
    private boolean mayBegin(ServerLevel level, LivingEntity target) {
        return this.owner.level() == level && this.canAct() && !this.owner.isPassenger() && !this.owner.isVehicle()
                && !this.hasPendingAbility() && this.isVisibleThreat(target);
    }
    public boolean tryBeginStrike(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextStrikeAt || !this.owner.isWithinMeleeAttackRange(target)
                || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return false;
        this.nextStrikeAt = now + this.meleeCooldownTicks(); this.begin(target, CombatPhase.STRIKE_WINDUP, now + HuskEndermanConfig.strikeWindupTicks());
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, this.meleeCooldownTicks()); this.hunter.abilityWarning(level, CombatPhase.STRIKE_WINDUP); return true;
    }
    public boolean tryBeginCurse(ServerLevel level, LivingEntity target, long now) {
        if (!(target instanceof Player) || !this.mayBegin(level, target) || now < this.nextCurseAt || !this.hunter.canCastOn(target, HuskEndermanConfig.curseRange())) return false;
        this.nextCurseAt = now + HuskEndermanConfig.curseCooldownTicks(); this.begin(target, CombatPhase.CURSE_WINDUP, now + HuskEndermanConfig.cursePreparationTicks()); this.hunter.abilityWarning(level, CombatPhase.CURSE_WINDUP); return true;
    }
    public boolean tryBeginDelirium(ServerLevel level, LivingEntity target, long now) {
        if (!(target instanceof Player) || !this.mayBegin(level, target) || now < this.nextDeliriumAt || !this.hunter.canCastOn(target, HuskEndermanConfig.deliriumRadius())) return false;
        this.nextDeliriumAt = now + HuskEndermanConfig.deliriumCooldownTicks(); this.begin(target, CombatPhase.DELIRIUM_WINDUP, now + HuskEndermanConfig.deliriumPreparationTicks()); this.hunter.abilityWarning(level, CombatPhase.DELIRIUM_WINDUP); return true;
    }
    private void begin(LivingEntity target, CombatPhase phase, long end) {
        this.rememberVisibleThreat(target); this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.brace();
        this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT; this.phaseEndsAt = end; this.hunter.setCombatPhase(phase, end);
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.hunter.getCombatPhase(); if (!phase.isCommitted()) return false;
        this.brace();
        if (phase == CombatPhase.RECOVERY || phase == CombatPhase.STRIKE || phase == CombatPhase.CURSE || phase == CombatPhase.DELIRIUM) {
            if (now >= this.phaseEndsAt) { if (phase == CombatPhase.RECOVERY) this.hunter.setCombatPhase(CombatPhase.PURSUING); else this.finish(now); }
            return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || !this.isVisibleThreat(target) || this.owner.isPassenger() || this.owner.isVehicle()) { this.finish(now); return true; }
        double castRange = phase == CombatPhase.CURSE_WINDUP ? HuskEndermanConfig.curseRange() : phase == CombatPhase.DELIRIUM_WINDUP ? HuskEndermanConfig.deliriumRadius() : 64;
        if (this.owner.distanceToSqr(target) > castRange * castRange) { this.finish(now); return true; }
        this.rememberVisibleThreat(target); this.owner.getLookControl().setLookAt(target, 35, 35);
        if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.STRIKE_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.STRIKE); this.strikeImpact = true;
            try { this.hunter.performDesiccatingStrike(level, target); } finally { this.strikeImpact = false; this.pendingTarget = null; }
        } else if (phase == CombatPhase.CURSE_WINDUP && target instanceof Player player) {
            this.hunter.setCombatPhase(CombatPhase.CURSE); this.curseRelease = true;
            try { this.hunter.applyStarvationCurse(level, player); } finally { this.curseRelease = false; this.pendingTarget = null; }
        } else if (phase == CombatPhase.DELIRIUM_WINDUP) {
            this.hunter.setCombatPhase(CombatPhase.DELIRIUM); this.deliriumRelease = true;
            try { this.hunter.releaseDesertDelirium(level); } finally { this.deliriumRelease = false; this.pendingTarget = null; }
        }
        this.phaseEndsAt = now + 5; return true;
    }
    public boolean isStrikeImpactFrame(LivingEntity target) { return this.strikeImpact && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.STRIKE; }
    public boolean isCurseReleaseFrame(Player target) { return this.curseRelease && this.getPendingTarget() == target && this.hunter.getCombatPhase() == CombatPhase.CURSE; }
    public boolean isDeliriumReleaseFrame() { return this.deliriumRelease && this.hunter.getCombatPhase() == CombatPhase.DELIRIUM; }
    private void finish(long now) { this.cancelPending(); this.phaseEndsAt = now + HuskEndermanConfig.recoveryTicks(); this.hunter.setCombatPhase(CombatPhase.RECOVERY, this.phaseEndsAt); }
    private void cancelPending() { this.pendingTarget = null; this.strikeImpact = this.curseRelease = this.deliriumRelease = false; }
    private void brace() { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return; this.nextProgressAt = now + 20;
        if (this.progressPosition != null && this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 9 && this.owner.position().distanceToSqr(this.progressPosition) < .3) this.blockedTicks = Math.min(100, this.blockedTicks + 20); else this.blockedTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private boolean tryReposition(long now) {
        if (now < this.nextTeleportAt || !this.isTargetLocalized() || this.lastLocated == null || this.owner.distanceToSqr(this.lastLocated) < 16) return false;
        this.nextTeleportAt = now + HuskEndermanConfig.tacticalTeleportCooldownTicks(); this.leftFlank = !this.leftFlank; this.stopCombat(); this.clearTarget();
        if (!this.hunter.tryHuskReposition(this.lastLocated, this.leftFlank)) return false;
        this.blockedTicks = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; return true;
    }
    private void investigate(ServerLevel level, long now) {
        Vec3 point = this.lastLocated; if (point == null) return;
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 35, 35);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.brace(); return; }
        if (now >= this.nextPathAt) { this.nextPathAt = now + 20; BlockPos block = BlockPos.containing(point);
            if (level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) != null) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1.05); }
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelPending(); this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0;
        this.progressPosition = null; this.blockedTicks = 0; this.phaseEndsAt = 0; this.hunter.setActivelyHunting(false); this.hunter.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) { output.putLong("HuskStrikeAfter", this.nextStrikeAt); output.putLong("HuskCurseAfter", this.nextCurseAt); output.putLong("HuskDeliriumAfter", this.nextDeliriumAt); output.putLong("HuskTeleportAfter", this.nextTeleportAt); }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime(); this.nextStrikeAt = Math.min(input.getLongOr("HuskStrikeAfter", 0), now + this.meleeCooldownTicks());
        this.nextCurseAt = Math.min(input.getLongOr("HuskCurseAfter", 0), now + HuskEndermanConfig.curseCooldownTicks()); this.nextDeliriumAt = Math.min(input.getLongOr("HuskDeliriumAfter", 0), now + HuskEndermanConfig.deliriumCooldownTicks());
        this.nextTeleportAt = Math.min(input.getLongOr("HuskTeleportAfter", 0), now + HuskEndermanConfig.tacticalTeleportCooldownTicks());
    }
}
