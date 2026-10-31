package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.EnderismConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.BrownEnderman;
import com.ronm19.enderism.entity.BrownEnderman.HuntState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Brown's species decisions within the shared native Brain; no independent target/movement selectors. */
public final class BrownEndermanBrain extends MastermindBrain {
    private final BrownEnderman brown;
    private long nextDetectionAt;
    private long nextGazeAt;
    private long intimidationEndsAt;
    private long chargeEndsAt;
    private long nextInterceptionAt;
    private long positionGraceUntil;
    private boolean hunting;
    private boolean needsPoseBaseline;
    private Vec3 gazePosition = Vec3.ZERO;
    private Vec3 previousPosePosition = Vec3.ZERO;
    private float gazeYaw;
    private float gazePitch;
    private @Nullable Vec3 lastVisiblePosition;
    private long lastVisibleTick;
    private Vec3 observedMotion = Vec3.ZERO;

    public BrownEndermanBrain(BrownEnderman owner) {
        super(owner);
        this.brown = owner;
    }

    @Override
    protected int memoryTicks() {
        return EnderismConfig.searchMemoryTicks();
    }

    @Override
    protected int meleeCooldownTicks() {
        return EnderismConfig.meleeCooldownTicks();
    }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target)
                && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        LivingEntity remembered = body.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (remembered != null && !this.isLegalThreat(remembered)) {
            this.clearEncounter();
            return;
        }

        LivingEntity attacker = body.getLastHurtByMob();
        int hurtTimestamp = body.getLastHurtByMobTimestamp();
        int elapsed = body.tickCount - hurtTimestamp;
        if (hurtTimestamp != this.handledHurtTimestamp && elapsed >= 0 && elapsed <= this.memoryTicks()
                && this.isVisibleThreat(attacker)) {
            this.handledHurtTimestamp = hurtTimestamp;
            this.rememberVisibleThreat(attacker);
            remembered = attacker;
            this.beginHunt(level, now);
        }

        LivingEntity visible = this.isVisibleThreat(remembered) ? remembered : null;
        if (visible != null) {
            this.updateObservedMotion(visible, now);
            this.rememberVisibleThreat(visible);
        } else {
            // Never extrapolate motion across an interval without sight.
            this.lastVisiblePosition = null;
            this.observedMotion = Vec3.ZERO;
        }

        if (!this.hunting && this.intimidationEndsAt != 0 && now >= this.intimidationEndsAt) {
            this.clearEncounter();
            return;
        }

        if (this.tickFloating()) {
            this.needsPoseBaseline = true;
            this.brown.setHuntState(remembered == null ? HuntState.IDLE : HuntState.SEARCHING);
            return;
        }

        if (!this.hunting && this.intimidationEndsAt != 0) {
            if (visible instanceof ServerPlayer player) {
                this.tickIntimidation(level, player, now);
            } else {
                this.needsPoseBaseline = true;
                this.search(level);
            }
            return;
        }

        if (this.hunting) {
            if (visible != null) {
                this.tickHunt(level, visible, now);
            } else if (body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                this.search(level);
            } else {
                this.clearEncounter();
            }
            return;
        }

        if (now >= this.nextDetectionAt && now >= this.nextGazeAt) {
            this.nextDetectionAt = now + 10L;
            ServerPlayer player = this.detectVisiblePlayer(level);
            if (player != null) {
                this.beginIntimidation(level, player, now);
                return;
            }
        }
        this.state = State.IDLE;
        this.brown.setHuntState(HuntState.IDLE);
        this.tickAmbient();
    }

    private @Nullable ServerPlayer detectVisiblePlayer(ServerLevel level) {
        double range = EnderismConfig.detectionRange();
        double nearestDistance = range * range;
        ServerPlayer nearest = null;
        // The section query is limited to currently loaded entities in this local box.
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearestDistance && this.isVisibleThreat(player)) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private void beginIntimidation(ServerLevel level, ServerPlayer player, long now) {
        this.stopCombat();
        this.stopAmbient();
        this.clearTarget();
        this.owner.getNavigation().stop();
        this.freezeMovement();
        this.rememberVisibleThreat(player);
        this.updateObservedMotion(player, now);
        this.hunting = false;
        this.intimidationEndsAt = now + EnderismConfig.intimidationDurationTicks();
        this.nextGazeAt = this.intimidationEndsAt + EnderismConfig.intimidationCooldownTicks();
        this.state = State.INVESTIGATE;
        this.brown.setHuntState(HuntState.INTIMIDATING);
        this.brown.beginFearVisual(player);
        this.capturePose(player);
        this.needsPoseBaseline = false;
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                Math.min(40, EnderismConfig.intimidationDurationTicks()), 0, false, false, false));
        this.brown.playSound(SoundEvents.ENDERMAN_STARE, 0.8F, 0.8F);
        this.faceVisible(player);
    }

    private void tickIntimidation(ServerLevel level, ServerPlayer player, long now) {
        this.stopCombat();
        this.stopAmbient();
        this.clearTarget();
        this.owner.getNavigation().stop();
        this.freezeMovement();
        this.state = State.INVESTIGATE;
        this.brown.setHuntState(HuntState.INTIMIDATING);
        this.faceVisible(player);
        if (this.needsPoseBaseline) {
            // Hidden motion/camera changes cannot be retroactively treated as observed provocation.
            this.capturePose(player);
            this.brown.beginFearVisual(player);
            this.needsPoseBaseline = false;
            return;
        }

        Vec3 position = player.position();
        double stepDistance = position.distanceToSqr(this.previousPosePosition);
        this.previousPosePosition = position;
        if (stepDistance > 16.0) {
            // A teleport or large correction is not a deliberate walking/jumping sample.
            this.capturePose(player);
            this.positionGraceUntil = now + EnderismConfig.forcedMovementGraceTicks();
            return;
        }
        if (player.hurtTime > 0 || player.hurtMarked || player.isInPostImpulseGraceTime() || player.isPassenger()) {
            this.positionGraceUntil = now + EnderismConfig.forcedMovementGraceTicks();
        }
        boolean ignorePosition = now < this.positionGraceUntil;
        if (ignorePosition) {
            this.gazePosition = position;
        }
        if (poseChanged(this.gazePosition, position, this.gazeYaw, this.gazePitch, player.getYRot(), player.getXRot(),
                EnderismConfig.movementTolerance(), EnderismConfig.rotationToleranceDegrees(), ignorePosition)) {
            this.beginHunt(level, now);
            this.tickHunt(level, player, now);
        }
    }

    private void capturePose(ServerPlayer player) {
        this.gazePosition = player.position();
        this.previousPosePosition = this.gazePosition;
        this.gazeYaw = player.getYRot();
        this.gazePitch = player.getXRot();
    }

    private void freezeMovement() {
        // Navigation has already ticked this frame; replace any pending movement-control command too.
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(this.owner.getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    private void faceVisible(LivingEntity target) {
        this.owner.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void beginHunt(ServerLevel level, long now) {
        if (!this.hunting) {
            this.hunting = true;
            this.intimidationEndsAt = 0;
            this.chargeEndsAt = now + EnderismConfig.chargeDurationTicks();
            this.nextInterceptionAt = now + EnderismConfig.interceptionCooldownTicks();
            this.brown.setHuntState(HuntState.CHARGING);
            this.brown.beginChargeEffects(level);
        }
    }

    private void tickHunt(ServerLevel level, LivingEntity visible, long now) {
        this.tickVisibleCombat(visible);
        boolean charging = now < this.chargeEndsAt;
        double speed = charging ? EnderismConfig.chargeSpeedModifier() : EnderismConfig.pursuitSpeedModifier();
        this.owner.getNavigation().setSpeedModifier(speed);
        this.brown.setHuntState(this.owner.isWithinMeleeAttackRange(visible) ? HuntState.ATTACKING
                : charging ? HuntState.CHARGING : HuntState.HUNTING);
        if (visible instanceof ServerPlayer player && now >= this.nextInterceptionAt
                && this.owner.distanceToSqr(player) >= Math.pow(EnderismConfig.interceptionMinDistance(), 2.0)
                && this.observedMotion.horizontalDistanceSqr() >= Math.pow(EnderismConfig.interceptionMinSpeed(), 2.0)) {
            Vec3 away = player.position().subtract(this.owner.position());
            if (away.x * this.observedMotion.x + away.z * this.observedMotion.z > 0.0) {
                this.nextInterceptionAt = now + EnderismConfig.interceptionCooldownTicks();
                Vec3 destination = interceptionPosition(player.position(), this.observedMotion,
                        EnderismConfig.interceptionLeadDistance());
                if (this.brown.attemptInterception(destination)) {
                    this.owner.getSensing().tick();
                    if (this.isVisibleThreat(player)) {
                        // One immediate path request after an occasional successful interception.
                        this.owner.getNavigation().moveTo(player, speed);
                    } else {
                        this.clearTarget();
                    }
                }
            }
        }
    }

    private void updateObservedMotion(LivingEntity visible, long now) {
        Vec3 position = visible.position();
        long interval = now - this.lastVisibleTick;
        this.observedMotion = this.lastVisiblePosition != null && interval > 0 && interval <= 2
                ? position.subtract(this.lastVisiblePosition).scale(1.0 / interval) : Vec3.ZERO;
        this.lastVisiblePosition = position;
        this.lastVisibleTick = now;
    }

    private void search(ServerLevel level) {
        this.stopCombat();
        this.clearTarget();
        this.brown.setHuntState(HuntState.SEARCHING);
        if (!this.tickInvestigation(level)) {
            this.clearEncounter();
        }
    }

    @Override
    public void clearEncounter() {
        LivingEntity previous = this.owner.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        boolean hadEncounter = previous != null || this.hunting || this.intimidationEndsAt != 0 || this.state != State.IDLE;
        boolean invalid = !this.owner.isAlive() || this.owner.isRemoved() || !this.isLegalThreat(previous);
        super.clearEncounter();
        this.hunting = false;
        this.intimidationEndsAt = 0;
        this.chargeEndsAt = 0;
        this.needsPoseBaseline = false;
        this.lastVisiblePosition = null;
        this.observedMotion = Vec3.ZERO;
        if (hadEncounter) {
            this.nextGazeAt = Math.max(this.nextGazeAt,
                    this.owner.level().getGameTime() + EnderismConfig.intimidationCooldownTicks());
        }
        this.brown.setHuntState(HuntState.IDLE);
        if (invalid) {
            this.brown.clearFearVisual();
        }
    }

    static boolean poseChanged(Vec3 anchor, Vec3 observed, float anchorYaw, float anchorPitch,
                               float observedYaw, float observedPitch, double movementTolerance,
                               double rotationTolerance, boolean ignorePosition) {
        return (!ignorePosition && anchor.distanceToSqr(observed) > movementTolerance * movementTolerance)
                || Math.abs(Mth.wrapDegrees(observedYaw - anchorYaw)) > rotationTolerance
                || Math.abs(observedPitch - anchorPitch) > rotationTolerance;
    }

    static Vec3 interceptionPosition(Vec3 observedPosition, Vec3 observedMotion, double leadDistance) {
        double horizontalLength = Math.sqrt(observedMotion.horizontalDistanceSqr());
        if (!Double.isFinite(horizontalLength) || horizontalLength < 1.0E-5 || !Double.isFinite(leadDistance)) {
            return observedPosition;
        }
        double scale = Mth.clamp(leadDistance, 0.0, 8.0) / horizontalLength;
        return observedPosition.add(observedMotion.x * scale, 0.0, observedMotion.z * scale);
    }
}
