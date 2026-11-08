package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.DemonEndermanConfig;
import com.ronm19.enderism.effect.DemonAsh;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.DemonEnderman;
import com.ronm19.enderism.entity.DemonEnderman.CombatPhase;
import java.lang.ref.WeakReference;
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

/** The shared native Brain owns all three abilities and finite pursuit memory. */
public final class DemonEndermanBrain extends MastermindBrain {
    private final DemonEnderman demon;
    private @Nullable WeakReference<LivingEntity> sequenceTarget;
    private @Nullable Vec3 progressPosition;
    private long windupUntil;
    private long recoveryUntil;
    private long sequenceDeadline;
    private long nextStrikeAt;
    private long nextClawAt;
    private long nextDetectionAt;
    private long nextDecisionAt;
    private long nextOnslaughtAt;
    private long nextFrenzyAt;
    private long nextTeleportAt;
    private long nextProgressAt;
    private long encounterStartedAt;
    private int strikeFrames;
    private int stalledTicks;
    private int connectedClaws;
    private boolean leftSide;
    private boolean retreating;

    public DemonEndermanBrain(DemonEnderman owner) { super(owner); this.demon = owner; }

    @Override protected int memoryTicks() { return DemonEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() {
        return this.demon.isFrenzied() ? DemonEndermanConfig.frenzyMeleeInterval() : DemonEndermanConfig.meleeInterval();
    }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity remembered = super.observeThreat();
        if (remembered != null) return remembered;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime() < this.nextDetectionAt) return null;
        this.nextDetectionAt = level.getGameTime() + (this.demon.isFrenzied() ? 4 : 8);
        double range = this.owner.getAttributeValue(Attributes.FOLLOW_RANGE);
        double nearestDistance = square(range);
        ServerPlayer nearest = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearestDistance && this.isVisibleThreat(player)) { nearest = player; nearestDistance = distance; }
        }
        if (nearest != null) this.rememberVisibleThreat(nearest);
        return nearest;
    }

    @Override protected void rememberVisibleThreat(LivingEntity target) {
        var old = this.owner.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        Vec3 previous = this.getLastObservedPosition();
        if (old != target) {
            if (old != null) DemonAsh.release(old, this.demon);
            this.progressPosition = this.owner.position();
            this.stalledTicks = 0;
            this.retreating = false;
            this.connectedClaws = 0;
            this.encounterStartedAt = this.owner.level().getGameTime();
        } else if (previous != null) {
            Vec3 approach = previous.subtract(this.owner.position()).multiply(1.0, 0.0, 1.0).normalize();
            this.retreating = target.position().subtract(previous).dot(approach) > 0.035;
        }
        super.rememberVisibleThreat(target);
    }

    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        var remembered = body.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (remembered != null && !this.isLegalThreat(remembered)) this.clearEncounter();
        if (this.sequenceTarget != null) { this.tickSequence(level, now); return; }
        if (now < this.recoveryUntil) { this.brace(); this.demon.setCombatPhase(CombatPhase.RECOVERY); return; }
        LivingEntity visible = this.observeThreat();
        if (this.tickFloating()) { this.demon.setCombatPhase(CombatPhase.SEARCHING); return; }
        this.sampleProgress(now);
        if (visible == null) { this.search(level, now); return; }

        double distance = body.distanceToSqr(visible);
        if (now >= this.nextDecisionAt) {
            this.nextDecisionAt = now + (this.demon.isFrenzied() ? 4 : 8);
            if (!this.demon.isFrenzied() && (this.connectedClaws >= 2 || body.getHealth() < body.getMaxHealth() * 0.7F
                    || now - this.encounterStartedAt >= 70) && this.tryBeginFrenzy(level, visible, now)) return;
            if (this.tryBeginOnslaught(level, visible, now)) return;
            if ((this.stalledTicks >= 30 || this.retreating && distance >= 100.0) && this.tryReposition(now)) return;
        }
        // Clear the finished recovery pose before the native actuator evaluates its next melee frame.
        if (this.demon.getCombatPhase() == CombatPhase.RECOVERY) this.demon.setCombatPhase(CombatPhase.PURSUIT);
        this.tickVisibleCombat(visible);
        if (this.demon.getCombatPhase() != CombatPhase.CLAWS || now - this.demon.getCombatPhaseStart() >= 7) {
            this.demon.setCombatPhase(CombatPhase.PURSUIT);
        }
    }

    /** All entry points, including scheduled Onslaught frames, use native reach and line of sight. */
    public boolean canUseClaws(ServerLevel level, LivingEntity target) {
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.demon.getCombatPhase().bracesCaster() && level.getGameTime() >= this.nextClawAt
                && !this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                && this.isLegalThreat(target)
                && this.owner.isWithinMeleeAttackRange(target) && this.owner.getSensing().hasLineOfSight(target);
    }

    public void onClawAttempt(ServerLevel level) {
        int interval = this.demon.getCombatPhase() == CombatPhase.ONSLAUGHT
                ? DemonEndermanConfig.onslaughtInterval() : this.meleeCooldownTicks();
        this.nextClawAt = level.getGameTime() + interval;
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, Math.max(1, interval - 1));
    }

    public void onClawConnected() { this.connectedClaws = Math.min(12, this.connectedClaws + 1); }

    public boolean tryBeginOnslaught(ServerLevel level, LivingEntity target, long now) {
        if (now < this.nextOnslaughtAt || now < this.nextClawAt
                || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                || !this.canStart(level, target, now, DemonEndermanConfig.onslaughtRange())) return false;
        if (!this.owner.isWithinMeleeAttackRange(target)) {
            var route = this.owner.getNavigation().createPath(target, 0);
            if (route == null || !route.canReach()) return false;
        }
        this.nextOnslaughtAt = now + DemonEndermanConfig.onslaughtCooldown();
        this.beginSequence(level, target, now, false);
        return true;
    }

    public boolean tryBeginFrenzy(ServerLevel level, LivingEntity target, long now) {
        if (this.demon.isFrenzied() || now < this.nextFrenzyAt || !this.canStart(level, target, now, 10.0)) return false;
        this.nextFrenzyAt = now + DemonEndermanConfig.frenzyCooldown();
        this.beginSequence(level, target, now, true);
        return true;
    }

    private boolean canStart(ServerLevel level, LivingEntity target, long now, double range) {
        return this.owner.level() == level && now == level.getGameTime() && this.sequenceTarget == null
                && now >= this.recoveryUntil && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.owner.isInWater() && !this.owner.isInLava() && this.owner.distanceToSqr(target) <= square(range)
                && this.isVisibleThreat(target);
    }

    private void beginSequence(ServerLevel level, LivingEntity target, long now, boolean frenzy) {
        this.rememberVisibleThreat(target);
        this.stopAmbient();
        this.stopCombat();
        this.clearTarget();
        this.brace();
        this.state = State.COMBAT;
        this.sequenceTarget = new WeakReference<>(target);
        this.strikeFrames = 0;
        this.windupUntil = now + (frenzy ? DemonEndermanConfig.frenzyWindup() : DemonEndermanConfig.onslaughtWindup());
        this.nextStrikeAt = this.windupUntil;
        this.sequenceDeadline = this.windupUntil + DemonEndermanConfig.onslaughtStrikes() * DemonEndermanConfig.onslaughtInterval() + 2;
        this.demon.setCombatPhase(frenzy ? CombatPhase.FRENZY_WINDUP : CombatPhase.ONSLAUGHT_WINDUP);
        this.owner.getLookControl().setLookAt(target, 45.0F, 35.0F);
        this.demon.beginAbilityWarning(level, frenzy);
    }

    private void tickSequence(ServerLevel level, long now) {
        LivingEntity target = this.sequenceTarget.get();
        if (target == null || !this.isLegalThreat(target)) { this.clearEncounter(); return; }
        boolean frenzy = this.demon.getCombatPhase() == CombatPhase.FRENZY_WINDUP;
        if (this.owner.isInWater() || this.owner.isInLava() || !this.isVisibleThreat(target)
                || this.owner.distanceToSqr(target) > square(frenzy ? 10.0 : DemonEndermanConfig.onslaughtRange() + 1.5)) {
            this.finishSequence(now, DemonEndermanConfig.onslaughtRecovery());
            return;
        }
        this.rememberVisibleThreat(target);
        this.owner.getLookControl().setLookAt(target, 45.0F, 35.0F);
        if (now < this.windupUntil) {
            this.brace();
            if (now % 5 == 0) this.demon.abilityWindup(level, frenzy);
            return;
        }
        if (frenzy) {
            this.demon.activateFrenzy(level);
            this.finishSequence(now, 8);
            return;
        }

        this.demon.setCombatPhase(CombatPhase.ONSLAUGHT);
        if (now >= this.sequenceDeadline || this.strikeFrames >= DemonEndermanConfig.onslaughtStrikes()) {
            this.finishSequence(now, DemonEndermanConfig.onslaughtRecovery());
            return;
        }
        if (!this.owner.isWithinMeleeAttackRange(target)) {
            if (!this.owner.getNavigation().moveTo(target, this.demon.isFrenzied() ? 1.12 : 1.04)) {
                this.finishSequence(now, DemonEndermanConfig.onslaughtRecovery());
                return;
            }
        } else this.owner.getNavigation().stop();

        if (now >= this.nextStrikeAt) {
            this.nextStrikeAt = now + DemonEndermanConfig.onslaughtInterval();
            this.strikeFrames++;
            if (this.canUseClaws(level, target)) {
                this.owner.swing(InteractionHand.MAIN_HAND);
                this.owner.doHurtTarget(level, target);
            }
            if (this.strikeFrames >= DemonEndermanConfig.onslaughtStrikes()) this.finishSequence(now, DemonEndermanConfig.onslaughtRecovery());
        }
    }

    private void finishSequence(long now, int recovery) {
        this.sequenceTarget = null;
        this.recoveryUntil = now + recovery;
        this.brace();
        this.demon.setCombatPhase(CombatPhase.RECOVERY);
    }

    private void search(ServerLevel level, long now) {
        this.stopCombat();
        this.clearTarget();
        if (this.stalledTicks >= 30 && this.tryReposition(now)) return;
        if (this.tickInvestigation(level)) {
            if (this.state == State.INVESTIGATE) this.demon.setCombatPhase(CombatPhase.SEARCHING);
            return;
        }
        if (this.state != State.IDLE) this.clearEncounter();
        this.demon.setCombatPhase(CombatPhase.IDLE);
        this.tickAmbient();
    }

    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return;
        this.nextProgressAt = now + 10;
        Vec3 observed = this.getLastObservedPosition();
        if (observed != null && this.owner.distanceToSqr(observed) > 16.0 && this.progressPosition != null
                && this.owner.distanceToSqr(this.progressPosition) < 0.16) this.stalledTicks += 10;
        else this.stalledTicks = 0;
        this.progressPosition = this.owner.position();
    }

    private boolean tryReposition(long now) {
        Vec3 observed = this.getLastObservedPosition();
        if (observed == null || now < this.nextTeleportAt) return false;
        double distance = this.owner.distanceToSqr(observed);
        if (distance < 36.0 || distance > 784.0) return false;
        this.nextTeleportAt = now + (this.demon.isFrenzied() ? DemonEndermanConfig.frenzyTeleportCooldown() : DemonEndermanConfig.teleportCooldown());
        Vec3 towardOwner = this.owner.position().subtract(observed).multiply(1.0, 0.0, 1.0).normalize();
        Vec3 side = new Vec3(-towardOwner.z, 0.0, towardOwner.x).scale(this.leftSide ? 2.5 : -2.5);
        this.leftSide = !this.leftSide;
        if (!this.demon.tryTacticalTeleport(observed.add(towardOwner.scale(1.5)).add(side).add(0.0, 1.0, 0.0), observed)) return false;
        this.progressPosition = this.owner.position();
        this.stalledTicks = 0;
        this.demon.setCombatPhase(CombatPhase.PURSUIT);
        return true;
    }

    private void brace() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(0.0, this.owner.getDeltaMovement().y, 0.0);
    }

    @Override public void clearEncounter() {
        super.clearEncounter();
        this.sequenceTarget = null;
        this.recoveryUntil = 0;
        this.progressPosition = null;
        this.stalledTicks = 0;
        this.connectedClaws = 0;
        this.retreating = false;
        this.demon.endFrenzy();
        DemonAsh.releaseOwnedBy(this.demon);
        this.demon.setCombatPhase(CombatPhase.IDLE);
    }

    public void writeCooldowns(ValueOutput output) {
        output.putLong("DemonClawsReadyAt", this.nextClawAt);
        output.putLong("DemonOnslaughtReadyAt", this.nextOnslaughtAt);
        output.putLong("DemonFrenzyReadyAt", this.nextFrenzyAt);
        output.putLong("DemonTeleportReadyAt", this.nextTeleportAt);
    }

    public void readCooldowns(ValueInput input) {
        this.nextClawAt = input.getLongOr("DemonClawsReadyAt", 0L);
        this.nextOnslaughtAt = input.getLongOr("DemonOnslaughtReadyAt", 0L);
        this.nextFrenzyAt = input.getLongOr("DemonFrenzyReadyAt", 0L);
        this.nextTeleportAt = input.getLongOr("DemonTeleportReadyAt", 0L);
    }

    private static double square(double value) { return value * value; }
}
