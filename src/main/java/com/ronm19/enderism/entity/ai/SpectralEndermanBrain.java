package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.SpectralEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.SpectralEnderman;
import com.ronm19.enderism.entity.SpectralEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Patient observation leads to one real materialization; the native Brain retains ordinary combat. */
public final class SpectralEndermanBrain extends MastermindBrain {
    private final SpectralEnderman spectral;
    private final SpectralAmbush ambush;
    private @Nullable UUID observedTarget;
    private @Nullable Vec3 lastObserved;
    private Vec3 observedVelocity = Vec3.ZERO;
    private long observedAt;
    private long observationUntil;
    private @Nullable WeakReference<LivingEntity> sequenceTarget;
    private @Nullable Vec3 sequenceDestination;
    private boolean specialSequence;
    private long phaseUntil;
    private long readyStrikeAt;
    private long recoveryUntil;
    private long nextClawAt;
    private long nextManifestationAt;
    private long nextApparitionAt;
    private long nextDecisionAt;
    private long nextDetectionAt;
    private long nextPositionSearchAt;
    private boolean sawDirectCombat;

    public SpectralEndermanBrain(SpectralEnderman owner) { super(owner); this.spectral = owner; this.ambush = new SpectralAmbush(owner); }
    public SpectralAmbush ambush() { return this.ambush; }
    public boolean hasPendingAmbush() { return this.sequenceTarget != null; }
    @Override protected int memoryTicks() { return SpectralEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return SpectralEndermanConfig.meleeInterval(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity remembered = super.observeThreat();
        if (remembered != null) return remembered;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime() < this.nextDetectionAt) return null;
        this.nextDetectionAt = level.getGameTime() + SpectralEndermanConfig.sensorInterval();
        double range = this.owner.getAttributeValue(Attributes.FOLLOW_RANGE), nearestDistance = range * range;
        ServerPlayer nearest = null;
        for (var player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearestDistance && this.isVisibleThreat(player)) { nearest = player; nearestDistance = distance; }
        }
        if (nearest != null) this.rememberVisibleThreat(nearest);
        return nearest;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (!target.getUUID().equals(this.observedTarget)) {
            this.cancelSequence(true); this.ambush.clear(); this.sawDirectCombat = false;
            this.observationUntil = now + SpectralEndermanConfig.observationTicks();
            this.observedVelocity = Vec3.ZERO;
        } else if (this.lastObserved != null && now > this.observedAt) {
            Vec3 velocity = target.position().subtract(this.lastObserved).multiply(1, 0, 1).scale(1.0 / (now - this.observedAt));
            this.observedVelocity = velocity.lengthSqr() > .1225 ? velocity.normalize().scale(.35) : velocity;
        }
        super.rememberVisibleThreat(target);
        this.observedTarget = target.getUUID(); this.lastObserved = target.position(); this.observedAt = now;
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators(); this.ambush.expire(now);
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        var remembered = body.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (remembered != null && !this.isLegalThreat(remembered)) this.clearEncounter();
        LivingEntity visible = this.observeThreat();
        if (this.sequenceTarget != null) { this.tickSequence(level, now); return; }
        if (now < this.recoveryUntil) {
            this.brace();
            if (this.spectral.getCombatPhase() != CombatPhase.EMPOWERED_REND || now - this.spectral.getCombatPhaseStart() >= 8)
                this.spectral.setCombatPhase(CombatPhase.RECOVERY);
            return;
        }
        if (this.tickFloating()) { this.spectral.setCombatPhase(CombatPhase.SEARCHING); return; }
        if (visible == null) { this.search(level); return; }
        if (now < this.observationUntil && !this.owner.isWithinMeleeAttackRange(visible)) {
            this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.brace(); this.state = State.COMBAT;
            this.owner.getLookControl().setLookAt(visible, 25, 20); this.spectral.setCombatPhase(CombatPhase.OBSERVING); return;
        }
        boolean close = this.owner.isWithinMeleeAttackRange(visible);
        if (!close && now >= this.nextDecisionAt) {
            this.nextDecisionAt = now + SpectralEndermanConfig.decisionInterval();
            // The committed special is an observed opportunity, not a rear-angle damage test.
            if ((this.sawDirectCombat || now >= this.observationUntil + 24) && this.tryBeginApparition(level, visible, now)) return;
            if (this.tryBeginManifestation(level, visible, now)) return;
        }
        // If no safe ambush exists, the native melee actuator supplies real pursuit and terrain handling.
        if (this.spectral.getCombatPhase().bracesCaster()) this.spectral.setCombatPhase(CombatPhase.STALKING);
        this.tickVisibleCombat(visible);
        this.sawDirectCombat |= close;
        if (this.spectral.getCombatPhase() != CombatPhase.REND && this.spectral.getCombatPhase() != CombatPhase.EMPOWERED_REND
                || now - this.spectral.getCombatPhaseStart() >= 8) this.spectral.setCombatPhase(CombatPhase.STALKING);
    }
    public boolean canUseRend(ServerLevel level, LivingEntity target) {
        LivingEntity bound = this.sequenceTarget == null ? null : this.sequenceTarget.get();
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.spectral.getCombatPhase().bracesCaster() && level.getGameTime() >= this.nextClawAt && level.getGameTime() >= this.recoveryUntil
                && !this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                && (bound == null || bound == target && this.spectral.getCombatPhase() == CombatPhase.AMBUSH_READY)
                && this.isLegalThreat(target)
                && this.owner.isWithinMeleeAttackRange(target) && this.owner.getSensing().hasLineOfSight(target);
    }
    public void onRendAttempt(ServerLevel level) {
        this.nextClawAt = level.getGameTime() + this.meleeCooldownTicks();
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, Math.max(1, this.meleeCooldownTicks() - 1));
    }
    public void onRendFinished(ServerLevel level, LivingEntity target, boolean accepted, boolean empowered) {
        this.sawDirectCombat |= accepted;
        if (this.sequenceTarget != null && (this.specialSequence || accepted && empowered)) this.finishSequence(level.getGameTime());
    }
    public boolean tryBeginManifestation(ServerLevel level, LivingEntity target, long now) { return this.tryBegin(level, target, now, false); }
    public boolean tryBeginApparition(ServerLevel level, LivingEntity target, long now) { return this.tryBegin(level, target, now, true); }
    private boolean tryBegin(ServerLevel level, LivingEntity target, long now, boolean special) {
        if (this.owner.level() != level || now != level.getGameTime() || this.sequenceTarget != null
                || now < this.recoveryUntil || now < this.nextPositionSearchAt || now < this.nextClawAt
                || now < (special ? this.nextApparitionAt : this.nextManifestationAt)
                || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                || this.owner.isNoAi() || !this.owner.isAlive() || this.owner.isRemoved() || this.owner.isInWater() || this.owner.isInLava()
                || !this.isVisibleThreat(target) || this.owner.distanceToSqr(target) < 3 * 3
                || this.owner.distanceToSqr(target) > square(SpectralEndermanConfig.teleportRange())) return false;
        this.nextPositionSearchAt = now + SpectralEndermanConfig.failedPositionRetry();
        Vec3 destination = this.ambush.selectDestination(level, target, target.position(), this.observedVelocity, special, now);
        if (destination == null) return false;
        this.rememberVisibleThreat(target);
        this.sequenceTarget = new WeakReference<>(target); this.sequenceDestination = destination; this.specialSequence = special;
        if (special) this.nextApparitionAt = now + SpectralEndermanConfig.apparitionCooldown();
        else this.nextManifestationAt = now + SpectralEndermanConfig.manifestationCooldown();
        this.phaseUntil = now + (special ? SpectralEndermanConfig.apparitionPreparation() : SpectralEndermanConfig.manifestationPreparation());
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.brace(); this.state = State.COMBAT;
        this.spectral.setCombatPhase(special ? CombatPhase.APPARITION_PREP : CombatPhase.MANIFESTATION_PREP);
        this.spectral.warnMaterialization(level, target, special); return true;
    }
    private void tickSequence(ServerLevel level, long now) {
        LivingEntity target = this.sequenceTarget.get();
        if (target == null || !this.isLegalThreat(target)) { this.clearEncounter(); return; }
        if (!this.isVisibleThreat(target) || this.owner.isInWater() || this.owner.isInLava()) { this.finishSequence(now); return; }
        this.owner.getLookControl().setLookAt(target, 40, 30);
        switch (this.spectral.getCombatPhase()) {
            case MANIFESTATION_PREP, APPARITION_PREP -> {
                this.brace();
                if (now >= this.phaseUntil) {
                    this.phaseUntil = now + SpectralEndermanConfig.transitionTicks(); this.spectral.setCombatPhase(CombatPhase.TRANSITION);
                }
            }
            case TRANSITION -> {
                this.brace();
                if (now >= this.phaseUntil) {
                    if (this.sequenceDestination == null || !this.spectral.tryMaterializationTeleport(this.sequenceDestination, target)) {
                        this.finishSequence(now); return;
                    }
                    this.owner.getSensing().tick();
                    this.phaseUntil = now + SpectralEndermanConfig.materializationTicks(); this.spectral.setCombatPhase(CombatPhase.MATERIALIZING);
                }
            }
            case MATERIALIZING -> {
                this.brace();
                if (now >= this.phaseUntil) {
                    this.ambush.arm(target, now, SpectralEndermanConfig.ambushWindowTicks(),
                            this.specialSequence ? SpectralEndermanConfig.apparitionBonus() : SpectralEndermanConfig.manifestationBonus(), this.specialSequence);
                    this.phaseUntil = now + SpectralEndermanConfig.ambushWindowTicks(); this.readyStrikeAt = now + 2;
                    this.spectral.setCombatPhase(CombatPhase.AMBUSH_READY); this.spectral.materializationComplete(level, this.specialSequence);
                }
            }
            case AMBUSH_READY -> {
                if (now >= this.phaseUntil || !this.ambush.isArmedFor(target, now)) { this.finishSequence(now); return; }
                if (now < this.readyStrikeAt) { this.brace(); return; }
                if (this.specialSequence) {
                    if (this.canUseRend(level, target)) { this.owner.swing(InteractionHand.MAIN_HAND); this.owner.doHurtTarget(level, target); }
                    if (this.sequenceTarget != null) this.finishSequence(now);
                } else if (this.canUseRend(level, target)) {
                    this.owner.swing(InteractionHand.MAIN_HAND); this.owner.doHurtTarget(level, target);
                } else this.owner.getNavigation().moveTo(target, .9);
            }
            default -> this.finishSequence(now);
        }
    }
    private void finishSequence(long now) {
        this.cancelSequence(true); this.recoveryUntil = now + SpectralEndermanConfig.recoveryTicks();
        this.brace();
        if (this.spectral.getCombatPhase() != CombatPhase.EMPOWERED_REND) this.spectral.setCombatPhase(CombatPhase.RECOVERY);
    }
    private void cancelSequence(boolean clearBonus) {
        this.sequenceTarget = null; this.sequenceDestination = null; this.specialSequence = false; this.phaseUntil = 0; this.readyStrikeAt = 0;
        if (clearBonus) this.ambush.clearArmed();
    }
    private void search(ServerLevel level) {
        this.stopCombat(); this.clearTarget(); this.ambush.clearArmed();
        if (this.tickInvestigation(level)) {
            if (this.state == State.INVESTIGATE) this.spectral.setCombatPhase(CombatPhase.SEARCHING);
            return;
        }
        if (this.state != State.IDLE || this.observedTarget != null) this.clearEncounter();
        this.spectral.setCombatPhase(CombatPhase.IDLE); this.tickAmbient();
    }
    private void brace() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0);
        this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0);
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.cancelSequence(true); this.ambush.clear(); this.recoveryUntil = 0;
        this.observedTarget = null; this.lastObserved = null; this.observedVelocity = Vec3.ZERO;
        this.observedAt = 0; this.observationUntil = 0; this.sawDirectCombat = false;
        this.spectral.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("SpectralRendReadyAt", this.nextClawAt); output.putLong("SpectralManifestationReadyAt", this.nextManifestationAt);
        output.putLong("SpectralApparitionReadyAt", this.nextApparitionAt); output.putLong("SpectralPositionSearchReadyAt", this.nextPositionSearchAt);
    }
    public void readCooldowns(ValueInput input) {
        this.nextClawAt = input.getLongOr("SpectralRendReadyAt", 0L); this.nextManifestationAt = input.getLongOr("SpectralManifestationReadyAt", 0L);
        this.nextApparitionAt = input.getLongOr("SpectralApparitionReadyAt", 0L); this.nextPositionSearchAt = input.getLongOr("SpectralPositionSearchReadyAt", 0L);
    }
    private static double square(double value) { return value * value; }
}
