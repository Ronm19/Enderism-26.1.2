package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.PhantomEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.PhantomEnderman;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Ground pursuit and transient ambush decisions owned by the existing native Brain. */
public final class PhantomEndermanBrain extends MastermindBrain {
    private final PhantomEnderman phantom;
    private @Nullable LivingEntity trackedTarget;
    private @Nullable Vec3 lastLocated;
    private Vec3 lastFacing = Vec3.ZERO;
    private Vec3 turnReferenceFacing = Vec3.ZERO;
    private long locatedUntil;
    private long encounterStarted;
    private long stalkingUntil;
    private long nextDetectionAt;
    private long nextDecisionAt;
    private long nextPathAt;
    private long nextProgressAt;
    private long nextApproachAt;
    private long normalCombatUntil;
    private boolean leftFlank;
    private int facingTurns;
    private int failedFlanks;
    private int stuckTicks;
    private @Nullable Vec3 progressPosition;
    private @Nullable LivingEntity approachTarget;
    private long approachEndsAt;
    private int approachAttempts;
    private int approachCount;
    private int rearRepositions;
    private @Nullable Vec3 lastApproachDestination;
    private @Nullable WeakReference<ServerPlayer> curseTarget;
    private @Nullable ServerLevel curseLevel;
    private long curseActivatesAt;
    private long nextCurseAt;
    private int curseApplications;

    public PhantomEndermanBrain(PhantomEnderman owner) { super(owner); this.phantom = owner; }
    @Override protected int memoryTicks() { return PhantomEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return PhantomEndermanConfig.meleeCooldownTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity current = super.observeThreat();
        if (current != null) return current;
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        if (now < this.nextDetectionAt) return null;
        this.nextDetectionAt = now + PhantomEndermanConfig.SENSOR_INTERVAL_TICKS;
        double range = PhantomEndermanConfig.followRange(), distance = range * range;
        ServerPlayer selected = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double candidate = this.owner.distanceToSqr(player);
            if (candidate <= distance && this.isVisibleThreat(player)) { selected = player; distance = candidate; }
        }
        if (selected != null) this.rememberVisibleThreat(selected);
        return selected;
    }

    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        Vec3 facing = PhantomEnderman.horizontalFacing(target);
        if (target != this.trackedTarget) {
            this.cancelCurse(); this.interruptApproach();
            this.encounterStarted = now;
            this.stalkingUntil = now + (this.phantom.isNightPredator() ? 20 : 30);
            this.facingTurns = 0; this.failedFlanks = 0; this.normalCombatUntil = 0;
            this.turnReferenceFacing = facing;
            this.progressPosition = this.owner.position(); this.stuckTicks = 0; this.nextProgressAt = now + 20;
        } else if (this.turnReferenceFacing.lengthSqr() > .01 && this.turnReferenceFacing.dot(facing) < .25) {
            // A player turning around is counterplay, not permission to force another rear hit.
            this.facingTurns = Math.min(8, this.facingTurns + 1);
            this.leftFlank = !this.leftFlank;
            this.turnReferenceFacing = facing;
            this.normalCombatUntil = now + 40;
            this.stalkingUntil = 0;
        }
        super.rememberVisibleThreat(target);
        this.trackedTarget = target;
        this.lastLocated = target.position(); this.lastFacing = facing;
        this.locatedUntil = now + this.memoryTicks();
    }

    /** Invoked once only after the entity's native physical attack was accepted. */
    public void onSuccessfulMelee(ServerLevel level, LivingEntity target, boolean backstab) {
        if (!this.owner.isAlive() || this.owner.isRemoved() || this.owner.isNoAi() || !this.isLegalThreat(target)) return;
        if (this.isVisibleThreat(target)) this.rememberVisibleThreat(target);
        if (!(target instanceof ServerPlayer player) || !eligiblePlayer(player) || this.curseTarget != null
                || level.getGameTime() < this.nextCurseAt) return;
        this.curseTarget = new WeakReference<>(player); this.curseLevel = level;
        this.curseActivatesAt = level.getGameTime() + PhantomEndermanConfig.curseDelayTicks();
        this.nextCurseAt = level.getGameTime() + PhantomEndermanConfig.curseCooldownTicks();
        this.phantom.setCurseWarningEnd(this.curseActivatesAt);
        this.phantom.warnCurse(level, player);
    }

    private static boolean eligiblePlayer(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected() && !player.isCreative() && !player.isSpectator();
    }

    private void tickCurse(ServerLevel level, long now) {
        if (this.curseTarget == null) return;
        ServerPlayer target = this.curseTarget.get();
        double range = PhantomEndermanConfig.followRange();
        if (target == null || !eligiblePlayer(target) || target.level() != level || this.curseLevel != level
                || this.trackedTarget != target || !this.isTargetLocalized()
                || !this.owner.isAlive() || this.owner.isNoAi() || this.owner.isRemoved()
                // Distance is an eligibility guard only; it never updates a hidden observation or steers pursuit.
                || this.owner.distanceToSqr(target) > range * range) { this.cancelCurse(); return; }
        if (now < this.curseActivatesAt) return;
        this.cancelCurse();
        // Native merging preserves stronger effects and every application has a finite duration.
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, PhantomEndermanConfig.darknessDurationTicks(), 0), this.owner);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PhantomEndermanConfig.slownessDurationTicks(),
                PhantomEndermanConfig.slownessAmplifier()), this.owner);
        this.curseApplications++;
        this.phantom.activateCurse(level, target);
    }

    private void cancelCurse() {
        this.curseTarget = null; this.curseLevel = null; this.curseActivatesAt = 0;
        this.phantom.setCurseWarningEnd(0L);
    }

    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        if (this.trackedTarget != null && !this.isLegalThreat(this.trackedTarget)) this.clearEncounter();
        LivingEntity visible = this.observeThreat();
        this.tickCurse(level, now);
        if (this.approachTarget != null) { this.tickApproach(level, now); return; }
        if (this.tickFloating()) { this.phantom.setHuntState(PhantomEnderman.HuntState.PURSUING); return; }
        if (visible == null) { this.searchLastObservation(level, now); return; }
        this.sampleProgress(now);
        boolean inMelee = this.owner.isWithinMeleeAttackRange(visible);
        if (!inMelee && now >= this.nextDecisionAt) {
            this.nextDecisionAt = now + PhantomEndermanConfig.decisionIntervalTicks(this.phantom.isNightPredator());
            if (this.shouldTeleport(visible, now) && this.beginSilentApproach(level, visible, now)) return;
        }
        if (!inMelee && now < this.stalkingUntil && now >= this.normalCombatUntil && !this.phantom.isBehindTarget(visible)) {
            this.walkBlindSide(level, now); return;
        }
        this.tickVisibleCombat(visible);
        this.phantom.setHuntState(this.curseTarget != null ? PhantomEnderman.HuntState.CURSING
                : inMelee ? PhantomEnderman.HuntState.ATTACKING : PhantomEnderman.HuntState.PURSUING);
    }

    private boolean shouldTeleport(LivingEntity target, long now) {
        double distance = this.owner.distanceToSqr(target);
        return now >= this.normalCombatUntil && now - this.encounterStarted >= 20
                && distance > 9.0 && distance <= PhantomEndermanConfig.approachRange() * PhantomEndermanConfig.approachRange()
                && (this.stuckTicks >= 40 || !this.phantom.isBehindTarget(target) || distance > 64.0);
    }

    private void walkBlindSide(ServerLevel level, long now) {
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.state = State.COMBAT;
        this.phantom.setHuntState(PhantomEnderman.HuntState.STALKING);
        Vec3 point = this.lastLocated;
        if (point == null || now < this.nextPathAt) return;
        this.nextPathAt = now + 20;
        Vec3 side = new Vec3(-this.lastFacing.z, 0, this.lastFacing.x).scale(this.leftFlank ? 1.5 : -1.5);
        Vec3 destination = point.subtract(this.lastFacing.scale(PhantomEndermanConfig.flankDistance())).add(side);
        if (level.hasChunkAt(BlockPos.containing(destination))) this.owner.getNavigation().moveTo(
                destination.x, destination.y, destination.z, PhantomEndermanConfig.stalkingSpeedModifier());
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.6, point.z, 35.0F, 35.0F);
    }

    /** One warned cast consumes a cooldown, even if the player turns or terrain prevents the move. */
    public boolean beginSilentApproach(ServerLevel level, LivingEntity target, long now) {
        double range = PhantomEndermanConfig.approachRange();
        if (this.approachTarget != null || now < this.nextApproachAt || now < this.normalCombatUntil
                || !this.owner.isAlive() || this.owner.isRemoved() || this.owner.isNoAi() || this.owner.isInWater()
                || !this.isVisibleThreat(target) || this.owner.distanceToSqr(target) < 9.0
                || this.owner.distanceToSqr(target) > range * range) return false;
        this.rememberVisibleThreat(target);
        this.nextApproachAt = now + PhantomEndermanConfig.approachCooldownTicks(this.phantom.isNightPredator());
        this.approachAttempts++;
        this.approachTarget = target; this.approachEndsAt = now + PhantomEndermanConfig.approachWarmupTicks();
        this.phantom.setApproachWarmupEnd(this.approachEndsAt);
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.stopHorizontalMovement();
        this.state = State.COMBAT; this.phantom.setHuntState(PhantomEnderman.HuntState.AMBUSHING);
        this.phantom.warnSilentApproach(level);
        return true;
    }

    private void tickApproach(ServerLevel level, long now) {
        LivingEntity target = this.approachTarget;
        if (target == null || !this.isVisibleThreat(target) || this.owner.isInWater() || now < this.normalCombatUntil) {
            this.interruptApproach(); return;
        }
        this.rememberVisibleThreat(target);
        if (now < this.normalCombatUntil) { this.interruptApproach(); return; }
        this.stopHorizontalMovement();
        Vec3 point = this.lastLocated;
        if (point == null || this.owner.distanceToSqr(point) > PhantomEndermanConfig.approachRange() * PhantomEndermanConfig.approachRange()) {
            this.interruptApproach(); return;
        }
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.6, point.z, 45.0F, 45.0F);
        if (now < this.approachEndsAt) return;
        double flank = PhantomEndermanConfig.flankDistance();
        Vec3 behind = point.subtract(this.lastFacing.scale(flank));
        Vec3 side = new Vec3(-this.lastFacing.z, 0, this.lastFacing.x).scale(this.leftFlank ? 1.5 : -1.5);
        Vec3[] options = {behind.add(side), behind.subtract(side), behind};
        Vec3 selected = null;
        for (Vec3 option : options) { selected = this.phantom.findSilentApproachDestination(option, point); if (selected != null) break; }
        this.interruptApproach(); this.leftFlank = !this.leftFlank;
        if (selected != null && this.phantom.trySilentApproachTeleport(selected, point)) {
            this.approachCount++; this.lastApproachDestination = this.owner.position();
            this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; this.stuckTicks = 0;
            if (this.phantom.isBehindTarget(target)) this.rearRepositions++;
            else { this.failedFlanks++; this.normalCombatUntil = now + 40; }
        } else { this.failedFlanks++; this.normalCombatUntil = now + 40; }
        // Return to native physical combat; a valid-facing player can deny the rear bonus normally.
        this.stalkingUntil = 0;
    }

    public void interruptApproach() {
        this.approachTarget = null; this.approachEndsAt = 0;
        this.phantom.setApproachWarmupEnd(0L);
    }

    private void stopHorizontalMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        Vec3 velocity = this.owner.getDeltaMovement(); this.owner.setDeltaMovement(0, velocity.y, 0);
    }

    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return;
        this.nextProgressAt = now + 20;
        if (this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 9.0 && this.progressPosition != null
                && this.owner.position().distanceToSqr(this.progressPosition) < .36) this.stuckTicks += 20;
        else this.stuckTicks = 0;
        this.progressPosition = this.owner.position();
    }

    private void searchLastObservation(ServerLevel level, long now) {
        this.stopCombat(); this.clearTarget();
        if (!this.isTargetLocalized()) {
            if (this.trackedTarget != null || this.state != State.IDLE) this.clearEncounter();
            this.phantom.setHuntState(PhantomEnderman.HuntState.IDLE); this.tickAmbient(); return;
        }
        this.stopAmbient(); this.state = State.INVESTIGATE;
        this.phantom.setHuntState(this.curseTarget != null ? PhantomEnderman.HuntState.CURSING : PhantomEnderman.HuntState.SEARCHING);
        Vec3 point = this.lastLocated;
        if (point == null) return;
        this.sampleProgress(now);
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.6, point.z, 35.0F, 35.0F);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.stopHorizontalMovement(); return; }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            if (level.hasChunkAt(BlockPos.containing(point))) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1.0);
            // A failed path never updates the fixed last-seen point or extends its expiration.
        }
    }

    public boolean isTargetLocalized() { return this.lastLocated != null && this.owner.level().getGameTime() < this.locatedUntil; }
    public int getTrackedTargetId() { return this.trackedTarget == null ? -1 : this.trackedTarget.getId(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }
    public long getLocatedUntil() { return this.locatedUntil; }
    public long nextApproachReadyAt() { return this.nextApproachAt; }
    public long nextCurseReadyAt() { return this.nextCurseAt; }
    public int getApproachAttemptCount() { return this.approachAttempts; }
    public int getApproachCount() { return this.approachCount; }
    public int getRearRepositionCount() { return this.rearRepositions; }
    public @Nullable Vec3 getLastApproachDestination() { return this.lastApproachDestination; }
    public int getFacingTurnCount() { return this.facingTurns; }
    public int getFailedFlankCount() { return this.failedFlanks; }
    public int getStuckTicks() { return this.stuckTicks; }
    public int getCurseApplicationCount() { return this.curseApplications; }
    public int getPendingCurseTargetId() {
        ServerPlayer target = this.curseTarget == null ? null : this.curseTarget.get();
        return target == null ? -1 : target.getId();
    }

    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelCurse(); this.interruptApproach();
        this.trackedTarget = null; this.lastLocated = null; this.lastFacing = Vec3.ZERO;
        this.turnReferenceFacing = Vec3.ZERO; this.locatedUntil = 0;
        this.progressPosition = null; this.stuckTicks = 0; this.facingTurns = 0; this.failedFlanks = 0;
        this.stalkingUntil = 0; this.normalCombatUntil = 0;
        this.phantom.clearAbilityVisuals();
        // Ability timestamps and ATTACK_COOLING_DOWN survive transient encounter cleanup.
    }
}
