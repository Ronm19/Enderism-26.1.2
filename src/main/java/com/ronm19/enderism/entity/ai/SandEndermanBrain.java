package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.SandEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.SandEnderman;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Desert decisions feed the existing native Brain's pursuit, melee, investigation, and ambient actuators. */
public final class SandEndermanBrain extends MastermindBrain {
    private final SandEnderman sand;
    private @Nullable LivingEntity trackedTarget;
    private @Nullable Vec3 lastLocated;
    private long locatedUntil;
    private long nextDetectionAt;
    private long nextPathAt;
    private long nextPoisonAt;
    private long nextVeilAt;
    private long nextHuntAt;
    private @Nullable LivingEntity veilTarget;
    private long veilWarmupEndsAt;
    private boolean leftFlank;
    private int huntAttempts;
    private int huntCount;
    private @Nullable Vec3 lastHuntDestination;

    public SandEndermanBrain(SandEnderman owner) { super(owner); this.sand = owner; }
    @Override protected int memoryTicks() { return SandEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return SandEndermanConfig.meleeCooldownTicks(); }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        long now = this.owner.level().getGameTime();
        if (!(this.owner.level() instanceof ServerLevel level) || now < this.nextDetectionAt) return super.observeThreat();
        this.nextDetectionAt = now + SandEndermanConfig.SENSOR_INTERVAL_TICKS;
        double range = SandEndermanConfig.followRange(), closest = range * range;
        ServerPlayer primary = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= closest && this.isVisibleThreat(player)) { primary = player; closest = distance; }
        }
        if (primary != null) { this.rememberVisibleThreat(primary); return primary; }
        LivingEntity observed = super.observeThreat();
        if (observed != null) return observed;
        // A recently observed player takes precedence over opportunistic rabbit hunting, even while searching.
        if (this.trackedTarget instanceof Player && this.isTargetLocalized()) return null;
        double rabbitRange = Math.min(range, SandEndermanConfig.rabbitDetectionRange());
        Rabbit prey = null; closest = rabbitRange * rabbitRange;
        for (Rabbit rabbit : level.getEntitiesOfClass(Rabbit.class, this.owner.getBoundingBox().inflate(rabbitRange))) {
            double distance = this.owner.distanceToSqr(rabbit);
            if (distance <= closest && this.isVisibleThreat(rabbit)) { prey = rabbit; closest = distance; }
        }
        if (prey != null) this.rememberVisibleThreat(prey);
        return prey;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        if (this.trackedTarget != null && this.trackedTarget != target) this.cancelSandstorm();
        super.rememberVisibleThreat(target);
        this.trackedTarget = target; this.lastLocated = target.position();
        this.locatedUntil = this.owner.level().getGameTime() + this.memoryTicks();
    }
    public int getTrackedTargetId() { return this.trackedTarget == null ? -1 : this.trackedTarget.getId(); }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.owner.level().getGameTime() < this.locatedUntil; }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }
    public long nextPoisonReadyAt() { return this.nextPoisonAt; }
    public long nextVeilReadyAt() { return this.nextVeilAt; }
    public long nextHuntReadyAt() { return this.nextHuntAt; }
    public int getHuntCount() { return this.huntCount; }
    public int getHuntAttemptCount() { return this.huntAttempts; }
    public @Nullable Vec3 getLastHuntDestination() { return this.lastHuntDestination; }
    public boolean isVeilWindingUp() { return this.veilTarget != null; }

    public void onSuccessfulMelee(ServerLevel level, LivingEntity target) {
        if (!(target instanceof Player || target instanceof Rabbit) || !this.isLegalThreat(target)
                || level.getGameTime() < this.nextPoisonAt) return;
        if (target.addEffect(new MobEffectInstance(MobEffects.POISON, SandEndermanConfig.poisonDurationTicks(), 0), this.owner))
            this.nextPoisonAt = level.getGameTime() + SandEndermanConfig.poisonCooldownTicks();
    }

    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (this.trackedTarget != null && !this.isLegalThreat(this.trackedTarget)) this.clearEncounter();
        if (this.veilTarget != null) { this.tickVeil(level, now); return; }
        LivingEntity visible = this.observeThreat();
        if (this.tickFloating()) { this.cancelSandstorm(); this.sand.setHuntState(SandEnderman.HuntState.SEARCHING); return; }
        if (visible != null) {
            if (this.beginVeil(level, visible, now)) return;
            if (this.tryHunt(level, visible, now)) return;
            this.tickVisibleCombat(visible);
            this.sand.setHuntState(this.owner.isWithinMeleeAttackRange(visible) ? SandEnderman.HuntState.ATTACKING
                    : this.sand.isSandstormActive() ? SandEnderman.HuntState.AMBUSHING : SandEnderman.HuntState.APPROACHING);
            return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE;
            this.sand.setHuntState(SandEnderman.HuntState.SEARCHING);
            this.investigateLastSeen(level, now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE || this.sand.isSandstormActive()) this.clearEncounter();
        this.sand.setHuntState(SandEnderman.HuntState.IDLE); this.tickAmbient();
    }

    private boolean beginVeil(ServerLevel level, LivingEntity target, long now) {
        double range = SandEndermanConfig.veilActivationRange();
        if (this.sand.isSandstormActive() || now < this.nextVeilAt || !this.isVisibleThreat(target)
                || this.owner.distanceToSqr(target) > range * range || this.owner.isInWater()) return false;
        this.nextVeilAt = now + SandEndermanConfig.veilCooldownTicks();
        this.veilTarget = target; this.veilWarmupEndsAt = now + SandEndermanConfig.veilWarmupTicks();
        this.sand.setVeilWarmupEnd(this.veilWarmupEndsAt);
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.freezeMovement();
        this.state = State.COMBAT; this.sand.setHuntState(SandEnderman.HuntState.SUMMONING_STORM);
        this.sand.veilWarning(level); return true;
    }
    private void tickVeil(ServerLevel level, long now) {
        LivingEntity target = this.veilTarget;
        double range = SandEndermanConfig.veilActivationRange();
        if (target == null || !this.isVisibleThreat(target) || this.owner.distanceToSqr(target) > range * range || this.owner.isInWater()) {
            this.interruptVeil(); return;
        }
        this.freezeMovement(); this.owner.getLookControl().setLookAt(target, 45.0F, 45.0F);
        if (now % 5 == 0) this.sand.veilGathering(level);
        if (now < this.veilWarmupEndsAt) return;
        // Center the encounter cloud on a legitimately visible target so its nearby flanks fit inside the veil.
        Vec3 center = target.position();
        this.rememberVisibleThreat(target);
        this.interruptVeil();
        this.sand.setSandstorm(BlockPos.containing(center), (float) SandEndermanConfig.veilRadius(),
                (float) SandEndermanConfig.veilIntensity(), now + SandEndermanConfig.veilDurationTicks());
    }
    private boolean tryHunt(ServerLevel level, LivingEntity target, long now) {
        double range = SandEndermanConfig.huntActivationRange();
        if (!this.sand.isSandstormActive() || now < this.nextHuntAt || !this.isVisibleThreat(target)
                || this.owner.distanceToSqr(target) > range * range || this.owner.distanceToSqr(target) < 4.0
                || !this.sand.isInsideSandstorm(target.position())) return false;
        this.nextHuntAt = now + SandEndermanConfig.huntCooldownTicks(); this.huntAttempts++;
        this.leftFlank = !this.leftFlank;
        Vec3 radial = this.owner.position().subtract(target.position()).multiply(1, 0, 1);
        if (radial.lengthSqr() < .01) radial = new Vec3(1, 0, 0);
        radial = radial.normalize(); double side = this.leftFlank ? 1.0 : -1.0;
        double distance = SandEndermanConfig.huntFlankDistance() + level.getRandom().nextDouble() * 2.0 - 1.0;
        Vec3 flank = new Vec3(-radial.z, 0, radial.x).scale(side * distance);
        Vec3 destination = target.position().add(flank);
        this.stopCombat(); this.clearTarget();
        if (!this.sand.tryHuntTeleport(destination, target)) return false;
        this.huntCount++; this.lastHuntDestination = this.owner.position();
        this.sand.setHuntState(SandEnderman.HuntState.AMBUSHING); this.state = State.COMBAT;
        return true;
    }
    private void investigateLastSeen(ServerLevel level, long now) {
        Vec3 point = this.lastLocated;
        if (point == null) return;
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 45.0F, 45.0F);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.freezeMovement(); return; }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            if (level.hasChunkAt(BlockPos.containing(point))) this.owner.getNavigation().moveTo(point.x, point.y, point.z, 1.0);
        }
    }
    private void freezeMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(Vec3.ZERO);
    }
    public void interruptVeil() {
        this.veilTarget = null; this.veilWarmupEndsAt = 0; this.sand.setVeilWarmupEnd(0L);
    }
    public void cancelSandstorm() { this.interruptVeil(); this.sand.endSandstorm(); }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelSandstorm(); this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0;
        this.sand.setHuntState(SandEnderman.HuntState.IDLE);
        // Cooldown timestamps and flank history deliberately survive encounter resets.
    }
}
