package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.InfernalEndermanConfig;
import com.ronm19.enderism.effect.InfernalBurn;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.InfernalEnderman;
import com.ronm19.enderism.entity.InfernalEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The shared Brain arbitrates ordinary Rampage melee, a committed charge, and Cataclysm. */
public final class InfernalEndermanBrain extends MastermindBrain {
    private static final int SENSOR_INTERVAL = 10;
    private static final int DECISION_INTERVAL = 16;
    private static final int RAMPAGE_POSE_TICKS = 10;
    private static final double MIN_CHARGE_RANGE = 3.5;
    private static final double MAX_CHARGE_DROP = 0.80;
    private final InfernalEnderman infernal;
    private final InfernalCataclysm cataclysm;
    private long nextDetectionAt;
    private long nextDecisionAt;
    private long nextChargeAt;
    private long phaseEndsAt;
    private @Nullable WeakReference<LivingEntity> chargeTarget;
    private Vec3 chargeHeading = Vec3.ZERO;
    private double chargeRemaining;

    public InfernalEndermanBrain(InfernalEnderman owner) {
        super(owner);
        this.infernal = owner;
        this.cataclysm = new InfernalCataclysm(owner);
    }

    @Override
    protected int memoryTicks() {
        return InfernalEndermanConfig.memoryTicks();
    }

    @Override
    protected int meleeCooldownTicks() {
        return InfernalEndermanConfig.meleeInterval();
    }

    @Override
    protected boolean brainControlsMeleeCooldown() {
        return true;
    }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return target != null && this.infernal.canDamageWithInfernalAbility(target);
    }

    @Override
    protected @Nullable LivingEntity observeThreat() {
        LivingEntity remembered = super.observeThreat();
        if (remembered != null) return remembered;
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        if (now < this.nextDetectionAt) return null;
        this.nextDetectionAt = now + SENSOR_INTERVAL;
        double range = InfernalEndermanConfig.followRange();
        double nearestDistance = range * range;
        ServerPlayer nearest = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearestDistance && this.isVisibleThreat(player)) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        if (nearest != null) this.rememberVisibleThreat(nearest);
        return nearest;
    }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) {
            this.clearEncounter();
            return;
        }
        if (this.cataclysm.isActive()) {
            this.stopMovement();
            this.cataclysm.tick(level, now);
            return;
        }
        if (this.tickCharge(level, now)) return;
        LivingEntity visible = this.observeThreat();
        // The shared float actuator recognizes lava as a swim hazard. Infernal's navigator owns lava.
        if (body.isInWater() && this.tickFloating()) {
            this.infernal.setCombatPhase(CombatPhase.SEARCHING);
            return;
        }
        this.stopFloating();
        if (visible == null) {
            this.stopCombat();
            this.clearTarget();
            if (this.tickInvestigation(level)) {
                if (this.state == State.INVESTIGATE) this.infernal.setCombatPhase(CombatPhase.SEARCHING);
                return;
            }
            if (this.state != State.IDLE) this.clearEncounter();
            this.infernal.setCombatPhase(CombatPhase.IDLE);
            this.tickAmbient();
            return;
        }

        boolean meleeRange = this.owner.isWithinMeleeAttackRange(visible);
        if (!meleeRange && now >= this.nextDecisionAt) {
            this.nextDecisionAt = now + DECISION_INTERVAL;
            if (this.cataclysm.tryBegin(level, visible, now)) {
                this.beginCommittedAction();
                return;
            }
            if (this.tryBeginCharge(level, visible, now)) return;
        }
        this.tickVisibleCombat(visible);
        if (this.infernal.getCombatPhase() != CombatPhase.RAMPAGE
                || now - this.infernal.getCombatPhaseStart() >= RAMPAGE_POSE_TICKS) {
            this.infernal.setCombatPhase(this.infernal.isOnLavaSurface() ? CombatPhase.LAVA_RUNNING : CombatPhase.PURSUIT);
        }
    }

    public boolean hasActiveCataclysm() {
        return this.cataclysm.isActive();
    }

    public long chargeCooldownDeadline() {
        return this.nextChargeAt;
    }

    public long cataclysmCooldownDeadline() {
        return this.cataclysm.cooldownDeadline();
    }

    public void restoreCooldowns(long chargeDeadline, long cataclysmDeadline) {
        this.nextChargeAt = Math.max(0, chargeDeadline);
        this.cataclysm.restoreCooldownDeadline(cataclysmDeadline);
    }

    public boolean tryBeginCharge(ServerLevel level, LivingEntity target, long now) {
        CombatPhase phase = this.infernal.getCombatPhase();
        if (!InfernalEndermanConfig.chargeEnabled() || this.cataclysm.isActive() || now < this.nextChargeAt
                || phase == CombatPhase.CHARGE_WINDUP || phase == CombatPhase.CHARGING || phase == CombatPhase.RECOVERY
                || !this.owner.isAlive() || this.owner.isNoAi() || this.owner.isInWater()
                || !(this.owner.onGround() || this.infernal.isOnLavaSurface()) || !this.isVisibleThreat(target)) return false;
        Vec3 toTarget = target.position().subtract(this.owner.position());
        double horizontalDistance = toTarget.horizontalDistance();
        double maximumDistance = InfernalEndermanConfig.chargeDistance();
        if (horizontalDistance < MIN_CHARGE_RANGE || horizontalDistance > maximumDistance
                || Math.abs(toTarget.y) > 1.25) return false;
        Vec3 heading = toTarget.multiply(1, 0, 1).normalize();
        if (!this.canTravelChargeStep(level, heading.scale(InfernalEndermanConfig.chargeSpeed()))) return false;

        this.rememberVisibleThreat(target);
        this.beginCommittedAction();
        this.chargeTarget = new WeakReference<>(target);
        this.chargeHeading = heading;
        this.chargeRemaining = maximumDistance;
        this.phaseEndsAt = now + InfernalEndermanConfig.chargeWindup();
        this.nextChargeAt = now + InfernalEndermanConfig.chargeCooldown();
        this.infernal.setCombatPhase(CombatPhase.CHARGE_WINDUP);
        this.owner.getLookControl().setLookAt(target, 60, 45);
        this.owner.playSound(SoundEvents.RAVAGER_ROAR, 0.9F, 1.2F);
        return true;
    }

    private void beginCommittedAction() {
        this.stopCombat();
        this.stopFloating();
        this.stopAmbient();
        this.clearTarget();
        this.stopMovement();
        this.state = State.COMBAT;
    }

    private boolean tickCharge(ServerLevel level, long now) {
        CombatPhase phase = this.infernal.getCombatPhase();
        if (phase == CombatPhase.RECOVERY) {
            this.stopMovement();
            if (now >= this.phaseEndsAt) this.infernal.setCombatPhase(CombatPhase.IDLE);
            return true;
        }
        if (phase != CombatPhase.CHARGE_WINDUP && phase != CombatPhase.CHARGING) return false;

        LivingEntity target = this.chargeTarget == null ? null : this.chargeTarget.get();
        if (!this.isLegalThreat(target) || this.owner.isInWater()) {
            this.finishCharge(now);
            return true;
        }
        if (phase == CombatPhase.CHARGING) {
            this.owner.getLookControl().setLookAt(this.owner.getX() + this.chargeHeading.x * 4.0,
                    this.owner.getEyeY(), this.owner.getZ() + this.chargeHeading.z * 4.0, 70, 30);
            // A hard deadline also cleans up an interrupted travel step, for example while riding.
            if (now >= this.phaseEndsAt || this.owner.isPassenger()) this.finishCharge(now);
            return true;
        }
        this.stopMovement();
        this.owner.getLookControl().setLookAt(this.owner.getX() + this.chargeHeading.x * 4.0,
                this.owner.getEyeY() - 0.35, this.owner.getZ() + this.chargeHeading.z * 4.0, 70, 30);
        if (now % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME, this.owner.getX(), this.owner.getY(0.7), this.owner.getZ(),
                    5, 0.31, 0.35, 0.31, 0.025);
        }
        if (now >= this.phaseEndsAt) {
            if (!this.canTravelChargeStep(level, this.chargeHeading.scale(InfernalEndermanConfig.chargeSpeed()))) {
                this.finishCharge(now);
                return true;
            }
            this.phaseEndsAt = now + Mth.ceil(this.chargeRemaining / InfernalEndermanConfig.chargeSpeed()) + 4L;
            this.infernal.setCombatPhase(CombatPhase.CHARGING);
            this.owner.setYRot((float) (Mth.atan2(this.chargeHeading.z, this.chargeHeading.x) * 180.0 / Math.PI) - 90.0F);
            this.owner.playSound(SoundEvents.BLAZE_SHOOT, 0.85F, 0.75F);
        }
        return true;
    }

    /** Returns only a loaded, supported movement step, which normal collision physics still checks. */
    public Vec3 prepareChargeStep(ServerLevel level) {
        if (this.chargeRemaining < 0.001) {
            this.finishCharge(level.getGameTime());
            return Vec3.ZERO;
        }
        Vec3 step = this.chargeHeading.scale(Math.min(this.chargeRemaining, InfernalEndermanConfig.chargeSpeed()));
        if (!this.canTravelChargeStep(level, step)) {
            this.finishCharge(level.getGameTime());
            return Vec3.ZERO;
        }
        this.chargeRemaining -= step.horizontalDistance();
        return step;
    }

    /** Sweep over the actual segment moved by the engine, not over the requested velocity. */
    public void finishChargeStep(ServerLevel level, Vec3 before) {
        if (this.infernal.getCombatPhase() != CombatPhase.CHARGING) return;
        LivingEntity target = this.chargeTarget == null ? null : this.chargeTarget.get();
        if (!this.isLegalThreat(target)) {
            this.finishCharge(level.getGameTime());
            return;
        }
        Vec3 after = this.owner.position();
        double width = this.owner.getBbWidth() * 0.5;
        AABB contact = target.getBoundingBox().inflate(width, 0, width).expandTowards(0, -this.owner.getBbHeight(), 0);
        if ((contact.contains(before) || contact.contains(after) || contact.clip(before, after).isPresent())
                && this.owner.getSensing().hasLineOfSight(target)) {
            Vec3 impactHeading = this.chargeHeading;
            this.finishCharge(level.getGameTime());
            if (target.hurtServer(level, this.owner.damageSources().mobAttack(this.owner), InfernalEndermanConfig.chargeDamage())) {
                target.knockback(InfernalEndermanConfig.chargeKnockback(), -impactHeading.x, -impactHeading.z);
                InfernalBurn.apply(target);
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.55), target.getZ(),
                        19, 0.43, 0.45, 0.43, 0.08);
                this.owner.playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 0.68F);
            }
        } else if (this.owner.horizontalCollision || before.subtract(after).horizontalDistanceSqr() < 0.01
                || this.chargeRemaining <= 0.001) {
            this.finishCharge(level.getGameTime());
        }
    }

    private boolean canTravelChargeStep(ServerLevel level, Vec3 step) {
        AABB body = this.owner.getBoundingBox();
        AABB sweep = body.expandTowards(step).inflate(0.001);
        BlockPos min = BlockPos.containing(sweep.minX, sweep.minY - MAX_CHARGE_DROP, sweep.minZ);
        BlockPos max = BlockPos.containing(sweep.maxX, sweep.maxY + this.owner.maxUpStep(), sweep.maxZ);
        if (!level.getWorldBorder().isWithinBounds(sweep) || !level.hasChunksAt(min, max)) return false;

        Vec3 next = this.owner.position().add(step);
        double support = this.findChargeSupport(level, next);
        if (!Double.isFinite(support)) return false;
        double rise = Math.max(0, support - this.owner.getY());
        if (this.hasClearChargeSweep(level, body, step, rise)) return true;
        if (!this.owner.onGround()) return false;
        // The leading edge reaches a higher shore while the center still occupies the lava column.
        Vec3 leadingEdge = next.add(step.normalize().scale(this.owner.getBbWidth() * 0.5));
        if (!level.hasChunkAt(BlockPos.containing(leadingEdge))) return false;
        double stepUp = this.findChargeSupport(level, leadingEdge) - this.owner.getY();
        return stepUp > rise + 0.001 && this.hasClearChargeSweep(level, body, step, stepUp);
    }

    private boolean hasClearChargeSweep(ServerLevel level, AABB body, Vec3 step, double rise) {
        return (rise <= 0.001 || level.noBlockCollision(this.owner, body.expandTowards(0, rise, 0).deflate(0.001)))
                && level.noBlockCollision(this.owner, body.move(0, rise, 0).expandTowards(step).deflate(0.001));
    }

    private double findChargeSupport(ServerLevel level, Vec3 next) {
        double feetY = this.owner.getY();
        double maximum = feetY + this.owner.maxUpStep();
        double minimum = feetY - MAX_CHARGE_DROP;
        BlockPos.MutableBlockPos pos = BlockPos.containing(next.x, maximum, next.z).mutable();
        for (int y = pos.getY(); y >= Mth.floor(minimum) - 1; y--) {
            pos.setY(y);
            double top = InfernalLavaNavigation.surfaceHeight(level, pos);
            if (!Double.isFinite(top)) {
                var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if (shape.isEmpty()) continue;
                top = y + shape.max(Direction.Axis.Y);
            }
            if (top >= minimum - 0.001 && top <= maximum + 0.001) return top;
        }
        return Double.NaN;
    }

    private void finishCharge(long now) {
        this.chargeTarget = null;
        this.chargeHeading = Vec3.ZERO;
        this.chargeRemaining = 0;
        this.phaseEndsAt = now + InfernalEndermanConfig.chargeRecovery();
        this.nextChargeAt = now + InfernalEndermanConfig.chargeCooldown();
        this.stopMovement();
        this.infernal.setCombatPhase(CombatPhase.RECOVERY);
    }

    private void stopMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        Vec3 motion = this.owner.getDeltaMovement();
        this.owner.setDeltaMovement(0, motion.y, 0);
    }

    @Override
    public void clearEncounter() {
        super.clearEncounter();
        this.cataclysm.cancel();
        this.chargeTarget = null;
        this.chargeHeading = Vec3.ZERO;
        this.chargeRemaining = 0;
        this.phaseEndsAt = 0;
        this.infernal.setCombatPhase(CombatPhase.IDLE);
    }
}
