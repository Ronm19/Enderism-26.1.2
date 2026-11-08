package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.GoldenEndermanConfig;
import com.ronm19.enderism.effect.GoldenStun;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.GoldenEnderman;
import com.ronm19.enderism.entity.GoldenEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The shared native Brain chooses one committed control attack at a time. */
public final class GoldenEndermanBrain extends MastermindBrain {
    private static final double MIN_SHACKLES_RANGE = 3.5;
    private static final int RELEASE_POSE_TICKS = 7;
    private final GoldenEnderman golden;
    private @Nullable WeakReference<LivingEntity> castTarget;
    private @Nullable Vec3 progressPosition;
    private long castReleasesAt;
    private long recoveryUntil;
    private long nextDetectionAt;
    private long nextDecisionAt;
    private long nextMidasAt;
    private long nextShacklesAt;
    private long nextJudgmentAt;
    private long nextTeleportAt;
    private long nextProgressAt;
    private int stalledTicks;
    private boolean leftSide;
    private boolean retreating;

    public GoldenEndermanBrain(GoldenEnderman owner) {
        super(owner);
        this.golden = owner;
    }

    @Override protected int memoryTicks() { return GoldenEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return GoldenEndermanConfig.meleeInterval(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override
    protected @Nullable LivingEntity observeThreat() {
        LivingEntity remembered = super.observeThreat();
        if (remembered != null) return remembered;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime() < this.nextDetectionAt) return null;
        this.nextDetectionAt = level.getGameTime() + 10;
        double range = this.owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double nearestDistance = square(range);
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
    protected void rememberVisibleThreat(LivingEntity target) {
        var oldTarget = this.owner.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        Vec3 previous = this.getLastObservedPosition();
        if (target != oldTarget) {
            this.progressPosition = this.owner.position();
            this.stalledTicks = 0;
            this.retreating = false;
        } else if (previous != null) {
            Vec3 approach = previous.subtract(this.owner.position()).multiply(1.0, 0.0, 1.0).normalize();
            this.retreating = target.position().subtract(previous).dot(approach) > 0.045;
        }
        super.rememberVisibleThreat(target);
    }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) {
            this.clearEncounter();
            return;
        }
        if (this.castTarget != null) {
            this.tickCast(level, now);
            return;
        }
        if (now < this.recoveryUntil) {
            this.brace();
            if (now - this.golden.getCombatPhaseStart() >= RELEASE_POSE_TICKS) {
                this.golden.setCombatPhase(CombatPhase.RECOVERY);
            }
            return;
        }

        var remembered = this.owner.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (remembered != null && !this.isLegalThreat(remembered)) this.clearEncounter();
        LivingEntity visible = this.observeThreat();
        if (this.tickFloating()) {
            this.golden.setCombatPhase(CombatPhase.SEARCHING);
            return;
        }
        this.sampleProgress(now);
        if (visible == null) {
            this.search(level, now);
            return;
        }

        double distance = this.owner.distanceToSqr(visible);
        if (now >= this.nextDecisionAt) {
            this.nextDecisionAt = now + 12;
            if ((visible.hasEffect(MobEffects.SLOWNESS) || distance >= 4.0) && this.tryBeginJudgment(level, visible, now)) return;
            if ((this.retreating || distance >= 64.0) && this.tryBeginShackles(level, visible, now)) return;
            if ((this.retreating && distance >= 100.0 || this.stalledTicks >= 40) && this.tryReposition(now)) return;
        }
        this.tickVisibleCombat(visible);
        if (this.golden.getCombatPhase() != CombatPhase.MIDAS_TOUCH
                || now - this.golden.getCombatPhaseStart() >= 10) {
            this.golden.setCombatPhase(CombatPhase.PURSUIT);
        }
    }

    /** Midas impairs one damage-confirmed target without refreshing active Slowness. */
    public boolean applyMidas(ServerLevel level, LivingEntity target) {
        long now = level.getGameTime();
        if (!this.isLegalThreat(target) || now < this.nextMidasAt || target.hasEffect(MobEffects.SLOWNESS)) return false;
        if (!target.addEffect(slow(GoldenEndermanConfig.midasDuration(), 0), this.owner)) return false;
        this.nextMidasAt = now + GoldenEndermanConfig.midasCooldown();
        return true;
    }

    public boolean tryBeginShackles(ServerLevel level, LivingEntity target, long now) {
        double distance = this.owner.distanceToSqr(target);
        if (now < this.nextShacklesAt || distance < square(MIN_SHACKLES_RANGE)
                || !this.canStart(level, target, now, GoldenEndermanConfig.shacklesRange()) || !canSlowHeavily(target)) return false;
        this.nextShacklesAt = now + GoldenEndermanConfig.shacklesCooldown();
        this.beginCast(level, target, now, false);
        return true;
    }

    public boolean tryBeginJudgment(ServerLevel level, LivingEntity target, long now) {
        if (now < this.nextJudgmentAt || !this.canStart(level, target, now, GoldenEndermanConfig.judgmentRange())
                || !GoldenStun.canApply(target)) return false;
        this.nextJudgmentAt = now + GoldenEndermanConfig.judgmentCooldown();
        this.beginCast(level, target, now, true);
        return true;
    }

    private boolean canStart(ServerLevel level, LivingEntity target, long now, double range) {
        return this.castTarget == null && now >= this.recoveryUntil && this.owner.level() == level
                && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi() && !this.owner.isInWater()
                && this.owner.distanceToSqr(target) <= square(range) && this.isVisibleThreat(target);
    }

    private void beginCast(ServerLevel level, LivingEntity target, long now, boolean judgment) {
        this.rememberVisibleThreat(target);
        this.stopAmbient();
        this.stopCombat();
        this.clearTarget();
        this.brace();
        this.state = State.COMBAT;
        this.castTarget = new WeakReference<>(target);
        this.castReleasesAt = now + (judgment ? GoldenEndermanConfig.judgmentWindup() : GoldenEndermanConfig.shacklesWindup());
        this.golden.setCombatPhase(judgment ? CombatPhase.JUDGMENT_WINDUP : CombatPhase.SHACKLES_WINDUP);
        this.owner.getLookControl().setLookAt(target, 45.0F, 35.0F);
        this.golden.beginGoldCast(level, judgment);
    }

    private void tickCast(ServerLevel level, long now) {
        LivingEntity target = this.castTarget.get();
        if (target == null || !this.isLegalThreat(target) || this.owner.isInWater()) {
            this.clearEncounter();
            return;
        }
        this.brace();
        boolean judgment = this.golden.getCombatPhase() == CombatPhase.JUDGMENT_WINDUP;
        boolean visible = this.isVisibleThreat(target);
        if (visible) {
            this.rememberVisibleThreat(target);
            this.owner.getLookControl().setLookAt(target, 45.0F, 35.0F);
        }
        if (now < this.castReleasesAt) {
            if (now % 5 == 0) this.golden.goldWindup(level, judgment);
            return;
        }

        this.castTarget = null;
        this.recoveryUntil = now + (judgment ? 14 : 10);
        double range = judgment ? GoldenEndermanConfig.judgmentRange() : GoldenEndermanConfig.shacklesRange();
        if (!visible || this.owner.distanceToSqr(target) > square(range)) {
            this.golden.setCombatPhase(CombatPhase.RECOVERY);
            return;
        }
        this.golden.setCombatPhase(judgment ? CombatPhase.JUDGMENT_IMPACT : CombatPhase.SHACKLES_RELEASE);
        if (judgment) {
            if (GoldenStun.canApply(target) && this.golden.strikeJudgment(level, target)) {
                GoldenStun.tryApply(target, this.golden, GoldenEndermanConfig.judgmentStun(), GoldenEndermanConfig.judgmentProtection());
                this.golden.goldImpact(level, target, true);
            }
        } else if (canSlowHeavily(target)
                && target.addEffect(slow(GoldenEndermanConfig.shacklesDuration(), GoldenEndermanConfig.shacklesAmplifier()), this.owner)) {
            this.golden.releaseShackles(level, target);
        }
    }

    private static boolean canSlowHeavily(LivingEntity target) {
        var current = target.getEffect(MobEffects.SLOWNESS);
        return current == null || current.getAmplifier() < GoldenEndermanConfig.shacklesAmplifier();
    }

    private static MobEffectInstance slow(int ticks, int amplifier) {
        return new MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier, false, false, true);
    }

    private void search(ServerLevel level, long now) {
        this.stopCombat();
        this.clearTarget();
        if (this.stalledTicks >= 40 && this.tryReposition(now)) return;
        if (this.tickInvestigation(level)) {
            if (this.state == State.INVESTIGATE) this.golden.setCombatPhase(CombatPhase.SEARCHING);
            return;
        }
        if (this.state != State.IDLE) this.clearEncounter();
        this.golden.setCombatPhase(CombatPhase.IDLE);
        this.tickAmbient();
    }

    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return;
        this.nextProgressAt = now + 20;
        Vec3 position = this.owner.position();
        Vec3 observed = this.getLastObservedPosition();
        if (observed != null && position.distanceToSqr(observed) > 25.0 && this.progressPosition != null
                && position.distanceToSqr(this.progressPosition) < 0.36) this.stalledTicks += 20;
        else this.stalledTicks = 0;
        this.progressPosition = position;
    }

    private boolean tryReposition(long now) {
        Vec3 observed = this.getLastObservedPosition();
        if (observed == null || now < this.nextTeleportAt) return false;
        double distance = this.owner.distanceToSqr(observed);
        if (distance < 49.0 || distance > 625.0) return false;
        this.nextTeleportAt = now + GoldenEndermanConfig.teleportCooldown();
        Vec3 towardOwner = this.owner.position().subtract(observed).multiply(1.0, 0.0, 1.0).normalize();
        Vec3 side = new Vec3(-towardOwner.z, 0.0, towardOwner.x).scale(this.leftSide ? 3.25 : -3.25);
        this.leftSide = !this.leftSide;
        Vec3 standing = observed.add(towardOwner.scale(2.0)).add(side).add(0.0, 1.0, 0.0);
        if (!this.golden.tryTacticalTeleport(standing, observed)) return false;
        this.progressPosition = this.owner.position();
        this.stalledTicks = 0;
        this.golden.setCombatPhase(CombatPhase.PURSUIT);
        return true;
    }

    private void brace() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(0.0, this.owner.getDeltaMovement().y, 0.0);
    }

    @Override
    public void clearEncounter() {
        super.clearEncounter();
        this.castTarget = null;
        this.recoveryUntil = 0;
        this.progressPosition = null;
        this.stalledTicks = 0;
        this.retreating = false;
        this.golden.setCombatPhase(CombatPhase.IDLE);
    }

    public void writeCooldowns(ValueOutput output) {
        output.putLong("GoldenMidasReadyAt", this.nextMidasAt);
        output.putLong("GoldenShacklesReadyAt", this.nextShacklesAt);
        output.putLong("GoldenJudgmentReadyAt", this.nextJudgmentAt);
    }

    public void readCooldowns(ValueInput input) {
        this.nextMidasAt = input.getLongOr("GoldenMidasReadyAt", 0);
        this.nextShacklesAt = input.getLongOr("GoldenShacklesReadyAt", 0);
        this.nextJudgmentAt = input.getLongOr("GoldenJudgmentReadyAt", 0);
    }

    private static double square(double value) { return value * value; }
}
