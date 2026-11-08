package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.RavagerEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.RavagerEnderman;
import com.ronm19.enderism.entity.RavagerEnderman.CombatPhase;
import com.ronm19.enderism.raid.RavagerRaids;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The shared Brain owns a bounded heavy strike, grounded charge, and battlefield roar. */
public final class RavagerEndermanBrain extends MastermindBrain {
    private final RavagerEnderman ravager;
    private @Nullable WeakReference<LivingEntity> pendingTarget;
    private long nextDetectionAt, nextDecisionAt, nextWrathAt, nextChargeAt, nextRoarAt, phaseEndsAt, nextTeleportAt;
    private long actionEpoch;
    private Vec3 chargeHeading = Vec3.ZERO;
    private double chargeRemaining;
    private boolean impactFrame;
    private int stuckTicks;
    private Vec3 lastPosition = Vec3.ZERO;
    public RavagerEndermanBrain(RavagerEnderman ravager) { super(ravager); this.ravager = ravager; }
    @Override protected int memoryTicks() { return RavagerEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return RavagerEndermanConfig.meleeInterval(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return this.ravager.isEligibleEnemy(target) && this.ravager.canAttack(target); }
    public boolean hasPendingAbility() { return this.pendingTarget != null || this.ravager.getCombatPhase().isCommitted(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity previous = super.observeThreat();
        if (previous != null) return previous;
        if (!(this.ravager.level() instanceof ServerLevel level) || level.getGameTime() < this.nextDetectionAt) return null;
        this.nextDetectionAt = level.getGameTime() + 8;
        LivingEntity enemy = level.getEntitiesOfClass(LivingEntity.class,
                        this.ravager.getBoundingBox().inflate(RavagerEndermanConfig.followRange()), this::isVisibleThreat)
                .stream().min(Comparator.comparingDouble(this.ravager::distanceToSqr)).orElse(null);
        if (enemy != null) this.rememberVisibleThreat(enemy);
        return enemy;
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) { this.ravager.setCombatPhase(CombatPhase.SEARCHING); return; }
        LivingEntity enemy = this.observeThreat();
        if (enemy == null) {
            this.stopCombat(); this.clearTarget(); this.stuckTicks = 0;
            if (this.tickInvestigation(level)) { this.ravager.setCombatPhase(CombatPhase.SEARCHING); return; }
            if (this.state != State.IDLE) this.clearEncounter();
            this.tickPatrol(level); return;
        }
        if (now >= this.nextDecisionAt) {
            this.nextDecisionAt = now + 10;
            if (this.tryBeginRoar(level, enemy, now)) return;
            if (!this.ravager.isWithinMeleeAttackRange(enemy) && this.tryBeginCharge(level, enemy, now)) return;
        }
        this.ravager.setCombatPhase(CombatPhase.PURSUIT);
        this.tickVisibleCombat(enemy);
        this.tickBlockedPursuit(level, enemy, now);
    }
    private void tickPatrol(ServerLevel level) {
        Vec3 objective = RavagerRaids.raidObjective(this.ravager);
        BlockPos home = this.ravager.getOutpostHome();
        boolean returnHome = home != null && this.ravager.position().multiply(1, 0, 1)
                .distanceToSqr(Vec3.atCenterOf(home).multiply(1, 0, 1)) > square(RavagerEndermanConfig.patrolRadius());
        if (objective != null || returnHome) {
            Vec3 destination = objective != null ? objective : new Vec3(home.getX() + .5, this.ravager.getY(), home.getZ() + .5);
            if (level.hasChunkAt(BlockPos.containing(destination))) {
                this.stopAmbient(); this.ravager.getNavigation().moveTo(destination.x, destination.y, destination.z, .85);
                this.ravager.setCombatPhase(CombatPhase.GUARDING); return;
            }
        }
        this.ravager.setCombatPhase(home != null ? CombatPhase.GUARDING : CombatPhase.IDLE); this.tickAmbient();
    }
    private void tickBlockedPursuit(ServerLevel level, LivingEntity enemy, long now) {
        if (this.ravager.getCombatPhase().isCommitted()) return;
        if (this.ravager.position().distanceToSqr(this.lastPosition) < .0025 && !this.ravager.isWithinMeleeAttackRange(enemy)) this.stuckTicks++;
        else this.stuckTicks = 0;
        this.lastPosition = this.ravager.position();
        if (this.stuckTicks < 60 || now < this.nextTeleportAt || !this.isVisibleThreat(enemy)) return;
        this.stuckTicks = 0; this.nextTeleportAt = now + RavagerEndermanConfig.teleportCooldown();
        Vec3 away = this.ravager.position().subtract(enemy.position()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < .1) return;
        Vec3 flank = new Vec3(-away.z, 0, away.x).scale((this.ravager.getRandom().nextBoolean() ? 1 : -1) * 3.5);
        this.ravager.tryTacticalTeleport(enemy.position().add(away.scale(4)).add(flank).add(0, 1, 0));
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target) {
        return this.ravager.level() == level && this.ravager.isAlive() && !this.ravager.isRemoved() && !this.ravager.isNoAi()
                && !this.ravager.isPassenger() && !this.ravager.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target);
    }
    public boolean tryBeginWrath(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextWrathAt
                || this.ravager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                || !this.ravager.isWithinMeleeAttackRange(target)) return false;
        this.beginCommitted(target); this.nextWrathAt = now + RavagerEndermanConfig.meleeInterval();
        this.ravager.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, RavagerEndermanConfig.meleeInterval());
        this.phaseEndsAt = now + RavagerEndermanConfig.meleeWindup(); this.ravager.setCombatPhase(CombatPhase.WRATH_WINDUP);
        this.ravager.warningCue(false); return true;
    }
    public boolean tryBeginCharge(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextChargeAt || !this.ravager.onGround() || this.ravager.isInWater()) return false;
        Vec3 toward = target.position().subtract(this.ravager.position()); double distance = toward.horizontalDistance();
        if (distance < RavagerEndermanConfig.chargeMinimumRange() || distance > RavagerEndermanConfig.chargeMaximumRange() || Math.abs(toward.y) > 1) return false;
        Vec3 heading = toward.multiply(1, 0, 1).normalize();
        // Assess the entire committed lane before winding up; travel still revalidates each actual step.
        for (double offset = .5; offset <= distance; offset += .5)
            if (!this.canTravelChargeStep(level, heading.scale(offset))) return false;
        this.beginCommitted(target); this.chargeHeading = heading; this.chargeRemaining = Math.min(distance + 2, RavagerEndermanConfig.chargeMaximumRange());
        this.nextChargeAt = now + RavagerEndermanConfig.chargeCooldown(); this.phaseEndsAt = now + RavagerEndermanConfig.chargeWindup();
        this.ravager.setCombatPhase(CombatPhase.CHARGE_WINDUP); this.ravager.warningCue(false); return true;
    }
    public boolean tryBeginRoar(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target) || now < this.nextRoarAt || this.roarEnemies(level).size() < 2) return false;
        this.beginCommitted(target); this.nextRoarAt = now + RavagerEndermanConfig.roarCooldown();
        this.phaseEndsAt = now + RavagerEndermanConfig.roarWindup(); this.ravager.setCombatPhase(CombatPhase.ROAR_WINDUP);
        this.ravager.warningCue(true); return true;
    }
    private List<LivingEntity> roarEnemies(ServerLevel level) {
        double radius = RavagerEndermanConfig.roarRadius();
        return level.getEntitiesOfClass(LivingEntity.class, this.ravager.getBoundingBox().inflate(radius),
                        e -> this.isVisibleThreat(e) && this.ravager.distanceToSqr(e) <= square(radius))
                .stream().limit(16).toList();
    }
    private void beginCommitted(LivingEntity target) {
        this.actionEpoch++;
        this.rememberVisibleThreat(target); this.stopCombat(); this.stopFloating(); this.stopAmbient(); this.clearTarget(); this.stopMovement();
        this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT;
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.ravager.getCombatPhase();
        if (!phase.isCommitted()) return false;
        if (phase == CombatPhase.RECOVERY) {
            this.stopMovement(); if (now >= this.phaseEndsAt) this.ravager.setCombatPhase(CombatPhase.IDLE); return true;
        }
        if (phase == CombatPhase.WRATH_STRIKE || phase == CombatPhase.ROARING) {
            this.stopMovement(); if (now >= this.phaseEndsAt) this.finishAction(now); return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || this.ravager.isPassenger() || this.ravager.isVehicle()) { this.finishAction(now); return true; }
        if (phase == CombatPhase.CHARGING) {
            this.ravager.getLookControl().setLookAt(this.ravager.getX() + this.chargeHeading.x * 4, this.ravager.getEyeY(), this.ravager.getZ() + this.chargeHeading.z * 4, 45, 25);
            if (now >= this.phaseEndsAt || this.ravager.isInWater()) this.finishAction(now); return true;
        }
        this.stopMovement(); this.ravager.getLookControl().setLookAt(target, 35, 25);
        if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.WRATH_WINDUP) {
            this.ravager.setCombatPhase(CombatPhase.WRATH_STRIKE); this.impactFrame = true;
            try { this.ravager.performWrathStrike(level, target); } finally { this.impactFrame = false; this.pendingTarget = null; }
            this.phaseEndsAt = now + 8; return true;
        }
        if (phase == CombatPhase.CHARGE_WINDUP) {
            if (!this.canTravelChargeStep(level, this.chargeHeading.scale(RavagerEndermanConfig.chargeSpeed()))) { this.finishAction(now); return true; }
            this.phaseEndsAt = now + RavagerEndermanConfig.chargeDuration(); this.ravager.setCombatPhase(CombatPhase.CHARGING);
            this.ravager.setYRot((float)(Mth.atan2(this.chargeHeading.z, this.chargeHeading.x) * 180 / Math.PI) - 90);
            return true;
        }
        if (phase == CombatPhase.ROAR_WINDUP) {
            this.pendingTarget = null; this.ravager.setCombatPhase(CombatPhase.ROARING); this.phaseEndsAt = now + 8;
            long transaction = this.actionEpoch;
            this.ravager.playSound(SoundEvents.RAVAGER_ROAR, 1.2F, .75F);
            level.sendParticles(ParticleTypes.POOF, this.ravager.getX(), this.ravager.getY(.5), this.ravager.getZ(), 16, 1.8, .4, 1.8, .06);
            for (LivingEntity enemy : this.roarEnemies(level)) {
                if (!this.mayFinishRoar(level, transaction)) break;
                if (!this.isVisibleThreat(enemy) || this.ravager.distanceToSqr(enemy) > square(RavagerEndermanConfig.roarRadius())) continue;
                enemy.knockback(RavagerEndermanConfig.roarKnockback(), this.ravager.getX() - enemy.getX(), this.ravager.getZ() - enemy.getZ());
                if (!this.mayFinishRoar(level, transaction)) break;
                if (!this.isLegalThreat(enemy)) continue;
                if (!enemy.hasEffect(MobEffects.SLOWNESS)) enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        RavagerEndermanConfig.roarDisruptionTicks(), 0, false, true), this.ravager);
            }
        }
        return true;
    }
    private boolean mayFinishRoar(ServerLevel level, long epoch) {
        return this.actionEpoch == epoch && this.ravager.level() == level && this.ravager.isAlive() && !this.ravager.isRemoved()
                && !this.ravager.isNoAi() && this.ravager.getCombatPhase() == CombatPhase.ROARING;
    }
    public boolean isWrathImpactFrame(LivingEntity target) { return this.impactFrame && this.getPendingTarget() == target && this.ravager.getCombatPhase() == CombatPhase.WRATH_STRIKE; }
    /** Actual motion is handled by ordinary collision physics, never by teleportation. */
    public Vec3 prepareChargeStep(ServerLevel level) {
        if (this.ravager.getCombatPhase() != CombatPhase.CHARGING || this.chargeRemaining <= .001) { this.finishAction(level.getGameTime()); return Vec3.ZERO; }
        Vec3 step = this.chargeHeading.scale(Math.min(this.chargeRemaining, RavagerEndermanConfig.chargeSpeed()));
        if (!this.canTravelChargeStep(level, step)) { this.finishAction(level.getGameTime()); return Vec3.ZERO; }
        this.chargeRemaining -= step.horizontalDistance(); return step;
    }
    public void finishChargeStep(ServerLevel level, Vec3 before) {
        if (this.ravager.getCombatPhase() != CombatPhase.CHARGING) return;
        Vec3 after = this.ravager.position();
        AABB sweep = this.ravager.getBoundingBox().move(before.subtract(after)).expandTowards(after.subtract(before));
        var contact = level.getEntitiesOfClass(LivingEntity.class, sweep.inflate(.02),
                        enemy -> this.isLegalThreat(enemy) && this.contacts(before, after, enemy) && this.ravager.getSensing().hasLineOfSight(enemy))
                .stream().min(Comparator.comparingDouble(enemy -> enemy.position().distanceToSqr(before))).orElse(null);
        if (contact != null) {
            Vec3 heading = this.chargeHeading; this.finishAction(level.getGameTime());
            long transaction = this.actionEpoch;
            float health = contact.getHealth() + contact.getAbsorptionAmount();
            if (contact.hurtServer(level, this.ravager.damageSources().mobAttack(this.ravager), RavagerEndermanConfig.chargeDamage())
                    && contact.getHealth() + contact.getAbsorptionAmount() < health && this.mayFinishCharge(level, contact, transaction)) {
                contact.knockback(RavagerEndermanConfig.chargeKnockback(), -heading.x, -heading.z);
                if (this.mayFinishCharge(level, contact, transaction)) this.ravager.impactCue(level, contact);
            }
            return;
        }
        if (this.ravager.horizontalCollision || after.distanceToSqr(before) < .0025 || this.chargeRemaining <= .001) this.finishAction(level.getGameTime());
    }
    private boolean mayFinishCharge(ServerLevel level, LivingEntity target, long epoch) {
        return this.actionEpoch == epoch && this.ravager.level() == level && this.ravager.isAlive() && !this.ravager.isRemoved()
                && !this.ravager.isNoAi() && this.ravager.getCombatPhase() == CombatPhase.RECOVERY && this.isLegalThreat(target);
    }
    private boolean contacts(Vec3 before, Vec3 after, LivingEntity enemy) {
        double width = this.ravager.getBbWidth() * .5;
        AABB expanded = enemy.getBoundingBox().inflate(width, 0, width).expandTowards(0, -this.ravager.getBbHeight(), 0);
        return expanded.contains(before) || expanded.contains(after) || expanded.clip(before, after).isPresent();
    }
    private boolean canTravelChargeStep(ServerLevel level, Vec3 step) {
        AABB body = this.ravager.getBoundingBox(); AABB sweep = body.expandTowards(step).inflate(.001);
        BlockPos min = BlockPos.containing(sweep.minX, sweep.minY - .75, sweep.minZ);
        BlockPos max = BlockPos.containing(sweep.maxX, sweep.maxY + .1, sweep.maxZ);
        if (!level.hasChunksAt(min, max) || !level.getWorldBorder().isWithinBounds(sweep) || !level.noBlockCollision(this.ravager, sweep.deflate(.002))) return false;
        if (!level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != this.ravager && !this.isLegalThreat(e)).isEmpty()) return false;
        Vec3 next = this.ravager.position().add(step); double half = this.ravager.getBbWidth() * .4;
        for (double x : new double[]{-half, half}) for (double z : new double[]{-half, half})
            if (!this.hasSafeSupport(level, next.add(x, 0, z))) return false;
        return true;
    }
    private boolean hasSafeSupport(ServerLevel level, Vec3 sample) {
        double y = this.ravager.getY();
        for (int offset = -1; offset >= -2; offset--) {
            BlockPos pos = BlockPos.containing(sample.x, y, sample.z).offset(0, offset, 0);
            var state = level.getBlockState(pos); if (RavagerEnderman.isChargeHazard(state)) return false;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            return top <= y + .05 && top >= y - .75;
        }
        return false;
    }
    private void finishAction(long now) {
        this.actionEpoch++;
        this.pendingTarget = null; this.impactFrame = false; this.chargeHeading = Vec3.ZERO; this.chargeRemaining = 0;
        this.phaseEndsAt = now + RavagerEndermanConfig.recoveryTicks(); this.stopMovement(); this.ravager.setCombatPhase(CombatPhase.RECOVERY);
    }
    private void stopMovement() {
        this.ravager.getNavigation().stop(); this.ravager.getMoveControl().setWantedPosition(this.ravager.getX(), this.ravager.getY(), this.ravager.getZ(), 0);
        this.ravager.setDeltaMovement(0, this.ravager.getDeltaMovement().y, 0);
    }
    @Override public void clearEncounter() {
        this.actionEpoch++;
        super.clearEncounter(); this.pendingTarget = null; this.impactFrame = false; this.chargeHeading = Vec3.ZERO;
        this.chargeRemaining = 0; this.phaseEndsAt = 0; this.stuckTicks = 0; this.stopMovement(); this.ravager.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("RavagerWrathAfter", this.nextWrathAt); output.putLong("RavagerChargeAfter", this.nextChargeAt); output.putLong("RavagerRoarAfter", this.nextRoarAt);
    }
    public void readCooldowns(ValueInput input) {
        long now = this.ravager.level().getGameTime();
        this.nextWrathAt = Math.min(input.getLongOr("RavagerWrathAfter", 0), now + RavagerEndermanConfig.meleeInterval());
        this.nextChargeAt = Math.min(input.getLongOr("RavagerChargeAfter", 0), now + RavagerEndermanConfig.chargeCooldown());
        this.nextRoarAt = Math.min(input.getLongOr("RavagerRoarAfter", 0), now + RavagerEndermanConfig.roarCooldown());
    }
    private static double square(double n) { return n * n; }
}
