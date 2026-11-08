package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.ArcticEndermanConfig;
import com.ronm19.enderism.effect.ArcticStun;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.ArcticEnderman;
import com.ronm19.enderism.entity.ArcticEnderman.HuntState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Arctic decisions use the shared native Brain and actuators; execution owns an exclusive combat phase. */
public final class ArcticEndermanBrain extends MastermindBrain {
    private final ArcticEnderman arctic;
    private @Nullable ServerPlayer sensedPlayer;
    private @Nullable Vec3 lastLocated;
    private long locatedUntil;
    private long nextDetectionAt;
    private long nextPathAt;
    private double awarenessRange = ArcticEndermanConfig.NORMAL_RANGE;
    private Phase phase = Phase.NONE;
    private @Nullable ServerPlayer abilityTarget;
    private long phaseEndsAt;
    private long observationStartedAt;
    private float watcherHealth;
    private int watcherHurtTimestamp;
    private long nextAbsoluteZeroAt;
    private long nextJudgmentAt;

    public ArcticEndermanBrain(ArcticEnderman owner) {
        super(owner);
        this.arctic = owner;
    }

    @Override
    protected int memoryTicks() { return ArcticEndermanConfig.memoryTicks(); }
    @Override
    protected int meleeCooldownTicks() { return ArcticEndermanConfig.meleeCooldownTicks(); }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override
    protected boolean isVisibleThreat(@Nullable LivingEntity target) {
        return this.isLegalThreat(target) && this.owner.distanceToSqr(target) <= square(this.awarenessRange)
                && this.owner.getSensing().hasLineOfSight(target);
    }

    public int getSensedPlayerId() { return this.sensedPlayer == null ? -1 : this.sensedPlayer.getId(); }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.owner.level().getGameTime() < this.locatedUntil; }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }
    public long nextAbsoluteZeroReadyAt() { return this.nextAbsoluteZeroAt; }
    public long nextJudgmentReadyAt() { return this.nextJudgmentAt; }
    public boolean isExecuting() { return this.phase == Phase.JUDGMENT_WARMUP || this.phase == Phase.OBSERVE; }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (this.phase != Phase.NONE) {
            this.tickAbility(level, now);
            return;
        }
        if (this.sensedPlayer != null && !this.isLegalThreat(this.sensedPlayer)) this.clearEncounter();
        if (this.sensedPlayer != null && ArcticStun.isHeldBy(this.sensedPlayer, this.arctic)
                && (!level.getGameRules().get(GameRules.FREEZE_DAMAGE) || !this.sensedPlayer.canFreeze())) {
            ArcticStun.release(this.sensedPlayer, this.arctic);
        }
        if (now >= this.nextDetectionAt) {
            this.nextDetectionAt = now + ArcticEndermanConfig.SENSOR_INTERVAL_TICKS;
            this.samplePlayers(level, now);
        }
        LivingEntity visible = this.isVisibleThreat(this.sensedPlayer) ? this.sensedPlayer : this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible);
            this.locate(visible.position(), now);
        }
        if (this.tickFloating()) {
            this.arctic.setHuntState(HuntState.SEARCHING);
            return;
        }
        if (visible != null) {
            this.stopAmbient();
            if (visible instanceof ServerPlayer player) {
                // An existing hold is not extended or converted into a second overlapping stun.
                if (ArcticStun.isHeldBy(player, this.arctic)) {
                    if (!level.getGameRules().get(GameRules.FREEZE_DAMAGE) || !player.canFreeze()) {
                        ArcticStun.release(player, this.arctic);
                        this.arctic.setHuntState(HuntState.RESETTING);
                        return;
                    }
                    // Absolute Zero sustains cold while its original finite hold runs; it never reapplies the effect.
                    // The capped reserve survives the short global recovery window, allowing a natural Judgment chain.
                    this.arctic.addColdPressure(level, player, player.getTicksRequiredToFreeze());
                    this.stopCombat(); this.clearTarget(); this.freezeMovement();
                    this.state = State.COMBAT;
                    this.arctic.setHuntState(HuntState.FREEZING);
                    this.owner.getLookControl().setLookAt(player, 60.0F, 60.0F);
                    return;
                }
                if (this.beginJudgment(level, player, now) || this.beginAbsoluteZero(level, player, now)) return;
            }
            this.tickVisibleCombat(visible);
            this.owner.getNavigation().setSpeedModifier(ArcticEndermanConfig.PURSUIT_SPEED);
            this.arctic.setHuntState(this.owner.isWithinMeleeAttackRange(visible) ? HuntState.ATTACKING : HuntState.APPROACHING);
            return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.state = State.INVESTIGATE;
            this.arctic.setHuntState(HuntState.SEARCHING);
            this.investigate(this.lastLocated, now);
            return;
        }
        if (this.lastLocated != null) {
            this.lastLocated = null;
            this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            this.owner.getNavigation().stop();
        }
        if (this.sensedPlayer != null) {
            // Presence can persist, but the hidden player's exact coordinates are not refreshed every scan.
            this.stopAmbient(); this.freezeMovement();
            this.state = State.INVESTIGATE;
            this.arctic.setHuntState(HuntState.SENSING);
            return;
        }
        if (this.state != State.IDLE) this.clearEncounter();
        this.arctic.setHuntState(HuntState.IDLE);
        this.tickAmbient();
    }

    private void samplePlayers(ServerLevel level, long now) {
        this.awarenessRange = ArcticEnderman.detectionRange(level, this.owner.blockPosition());
        ServerPlayer previous = this.sensedPlayer;
        ServerPlayer candidate = previous;
        if (!this.isLegalThreat(candidate) || this.owner.distanceToSqr(candidate) > square(this.awarenessRange)) {
            candidate = null;
            double closest = square(this.awarenessRange);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(this.awarenessRange))) {
                double distance = this.owner.distanceToSqr(player);
                if (distance <= closest && this.isLegalThreat(player)) { candidate = player; closest = distance; }
            }
        }
        this.sensedPlayer = candidate;
        if (candidate != previous && candidate != null) {
            this.locate(candidate.position(), now);
            this.nextPathAt = now;
        }
    }

    private void locate(Vec3 position, long now) {
        this.lastLocated = position;
        this.locatedUntil = now + this.memoryTicks();
        WalkTarget previous = this.owner.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (previous == null || !previous.getTarget().currentPosition().equals(position))
            previous = new WalkTarget(new BlockPosTracker(position), (float) ArcticEndermanConfig.PURSUIT_SPEED, 1);
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, previous, this.memoryTicks());
    }

    private void investigate(Vec3 position, long now) {
        this.stopAmbient();
        this.owner.getLookControl().setLookAt(position.x, position.y + 1.5, position.z, 45, 45);
        if (this.owner.distanceToSqr(position) <= 2.25) { this.freezeMovement(); return; }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + ArcticEndermanConfig.PATH_REFRESH_TICKS;
            if (this.owner.level().hasChunkAt(BlockPos.containing(position)))
                this.owner.getNavigation().moveTo(position.x, position.y, position.z, ArcticEndermanConfig.PURSUIT_SPEED);
        }
    }

    private boolean coldAllowed(ServerLevel level, ServerPlayer player) {
        return this.isLegalThreat(player) && player.canFreeze() && level.getGameRules().get(GameRules.FREEZE_DAMAGE)
                && !player.isInvulnerableTo(level, ArcticStun.executionDamageSource(level, this.arctic));
    }

    private boolean beginAbsoluteZero(ServerLevel level, ServerPlayer player, long now) {
        if (now < this.nextAbsoluteZeroAt || !this.coldAllowed(level, player) || !this.isVisibleThreat(player)
                || this.owner.distanceToSqr(player) > square(ArcticEndermanConfig.ABSOLUTE_ZERO_RANGE)
                || !ArcticStun.canApply(player)) return false;
        this.nextAbsoluteZeroAt = now + ArcticEndermanConfig.absoluteZeroCooldownTicks();
        this.beginWarning(level, player, now, Phase.ZERO_WARMUP, HuntState.FREEZING);
        return true;
    }

    private boolean beginJudgment(ServerLevel level, ServerPlayer player, long now) {
        if (now < this.nextJudgmentAt || !this.coldAllowed(level, player) || !this.isVisibleThreat(player)
                || this.owner.distanceToSqr(player) > square(ArcticEndermanConfig.judgmentRange())
                || player.getHealth() < 1.0F || player.getTicksFrozen() < ArcticEndermanConfig.judgmentRequiredFreezeTicks()
                || !ArcticStun.canApply(player)) return false;
        this.nextJudgmentAt = now + ArcticEndermanConfig.judgmentCooldownTicks();
        this.beginWarning(level, player, now, Phase.JUDGMENT_WARMUP, HuntState.EXECUTING);
        return true;
    }

    private void beginWarning(ServerLevel level, ServerPlayer target, long now, Phase ability, HuntState visual) {
        this.phase = ability; this.abilityTarget = target;
        this.phaseEndsAt = now + ArcticEndermanConfig.abilityWarmupTicks();
        this.watcherHealth = this.owner.getHealth();
        this.watcherHurtTimestamp = this.owner.getLastHurtByMobTimestamp();
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.freezeMovement();
        this.state = State.COMBAT; this.arctic.setHuntState(visual); this.arctic.beginColdWarning(level);
    }

    private void tickAbility(ServerLevel level, long now) {
        ServerPlayer victim = this.abilityTarget;
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.freezeMovement();
        this.state = State.COMBAT;
        double maximumRange = this.phase == Phase.ZERO_WARMUP ? ArcticEndermanConfig.ABSOLUTE_ZERO_RANGE
                : ArcticEndermanConfig.judgmentRange() + (this.phase == Phase.OBSERVE ? 2.0 : 0.0);
        // Proposed counterplay: warning/observation is broken by cover, distance, or damaging the watcher.
        if (victim == null || !this.coldAllowed(level, victim) || !this.isVisibleThreat(victim)
                || this.owner.distanceToSqr(victim) > square(maximumRange)
                || this.owner.getHealth() < this.watcherHealth
                || this.owner.getLastHurtByMobTimestamp() != this.watcherHurtTimestamp) {
            this.cancelAbility(true);
            return;
        }
        this.owner.getLookControl().setLookAt(victim, 70.0F, 70.0F);
        if (this.phase == Phase.OBSERVE) {
            this.arctic.setHuntState(HuntState.OBSERVING);
            if (!ArcticStun.isHeldBy(victim, this.arctic)) { this.cancelAbility(true); return; }
            int nativeLimit = Math.max(0, victim.getTicksRequiredToFreeze() - 1);
            victim.setTicksFrozen(nativeLimit);
            if ((now - this.observationStartedAt) % 10 == 0) {
                float progress = (float) (now - this.observationStartedAt) / ArcticEndermanConfig.judgmentObserveTicks();
                this.arctic.executionFrost(level, victim, progress);
            }
            if (now >= this.phaseEndsAt) {
                // A genuine delayed, attributed damage event: damage hooks, invulnerability, and totems retain authority.
                float damage = victim.getMaxHealth() + victim.getAbsorptionAmount() + 100.0F;
                victim.hurtServer(level, ArcticStun.executionDamageSource(level, this.arctic), damage);
                this.cancelAbility(true);
            }
            return;
        }
        this.arctic.setHuntState(this.phase == Phase.ZERO_WARMUP ? HuntState.FREEZING : HuntState.EXECUTING);
        if (now < this.phaseEndsAt) return;
        if (this.phase == Phase.ZERO_WARMUP) {
            if (ArcticStun.tryApply(victim, this.arctic, ArcticEndermanConfig.stunDurationTicks())) {
                this.arctic.addColdPressure(level, victim, ArcticEndermanConfig.STRIKE_FREEZE_TICKS);
                this.arctic.coldBurst(level, victim);
            }
            this.cancelAbility(false);
            return;
        }
        if (victim.getHealth() < 1.0F || !ArcticStun.tryApply(victim, this.arctic, ArcticEndermanConfig.judgmentObserveTicks() + 2)) {
            this.cancelAbility(true); return;
        }
        float reduction = victim.getHealth() + victim.getAbsorptionAmount() - 1.0F;
        boolean applied = reduction <= 0.0F || victim.hurtServer(level, ArcticStun.executionDamageSource(level, this.arctic), reduction);
        // Never force health through a denied/modified damage event, and never repeat this subtraction each tick.
        if (!applied || !victim.isAlive() || victim.getHealth() != 1.0F) { this.cancelAbility(true); return; }
        this.phase = Phase.OBSERVE;
        this.observationStartedAt = now;
        this.phaseEndsAt = now + ArcticEndermanConfig.judgmentObserveTicks();
        this.arctic.setAbilityWindingUp(false);
        this.arctic.setHuntState(HuntState.OBSERVING);
        this.arctic.coldBurst(level, victim);
    }

    private void cancelAbility(boolean releaseHold) {
        if (releaseHold && this.abilityTarget != null) ArcticStun.release(this.abilityTarget, this.arctic);
        this.phase = Phase.NONE; this.abilityTarget = null; this.phaseEndsAt = 0;
        this.arctic.setAbilityWindingUp(false);
        this.arctic.setHuntState(HuntState.RESETTING);
    }

    private void freezeMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0);
    }

    @Override
    public void clearEncounter() {
        super.clearEncounter();
        this.cancelAbility(true);
        ArcticStun.releaseOwnedBy(this.arctic);
        this.sensedPlayer = null; this.lastLocated = null; this.locatedUntil = 0;
        // Ability deadlines intentionally survive reset/reacquisition; an interrupted execution cannot immediately restart.
    }

    private static double square(double value) { return value * value; }
    private enum Phase { NONE, ZERO_WARMUP, JUDGMENT_WARMUP, OBSERVE }
}
