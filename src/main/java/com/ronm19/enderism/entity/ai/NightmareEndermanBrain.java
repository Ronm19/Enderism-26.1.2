package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.NightmareEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.NightmareEnderman;
import com.ronm19.enderism.entity.NightmareEnderman.CombatPhase;
import com.ronm19.enderism.network.NightmareIllusions;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Commitment belongs to the confrontation, independently of expiring native navigation memories. */
public final class NightmareEndermanBrain extends MastermindBrain {
    private final NightmareEnderman nightmare;
    private @Nullable WeakReference<LivingEntity> encounterTarget;
    private @Nullable Vec3 lastObserved;
    private @Nullable Vec3 progressPosition;
    private boolean committed;
    private boolean leftSide;
    private boolean retreating;
    private long encounterStartedAt;
    private long lastSeenAt;
    private long revealUntil;
    private long illusionPoseUntil;
    private long nextDetectionAt;
    private long nextIllusionAt;
    private long nextDarknessAt;
    private long nextTeleportAt;
    private long nextPathAt;
    private long nextProgressAt;
    private int stalledTicks;

    public NightmareEndermanBrain(NightmareEnderman owner) {
        super(owner);
        this.nightmare = owner;
    }

    @Override protected int memoryTicks() { return NightmareEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return NightmareEndermanConfig.meleeInterval(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    public boolean isEncounterTarget(ServerPlayer target) {
        return this.currentTarget() == target && this.isLegalThreat(target);
    }

    public boolean canUseGrasp(LivingEntity target) {
        return this.committed && this.currentTarget() == target && this.isLegalThreat(target)
                && this.owner.level().getGameTime() >= this.revealUntil;
    }

    private @Nullable LivingEntity currentTarget() {
        return this.encounterTarget == null ? null : this.encounterTarget.get();
    }

    private void beginEncounter(LivingEntity target, long now) {
        this.stopCombat();
        this.stopAmbient();
        this.clearTarget();
        this.encounterTarget = new WeakReference<>(target);
        this.encounterStartedAt = now;
        this.lastSeenAt = now;
        this.lastObserved = target.position();
        this.progressPosition = this.owner.position();
        this.nextProgressAt = now + 20;
        this.nextIllusionAt = now;
        this.illusionPoseUntil = 0;
        this.stalledTicks = 0;
        this.retreating = false;
        this.committed = false;
        this.nightmare.nextEncounter();
        this.nightmare.setHuntCommitted(false);
        this.nightmare.setCombatPhase(CombatPhase.OBSERVING);
        this.state = State.COMBAT;
        this.rememberVisibleThreat(target);
    }

    @Override
    protected @Nullable LivingEntity observeThreat() {
        LivingEntity existing = this.currentTarget();
        if (existing != null) {
            if (!this.isVisibleThreat(existing)) return null;
            this.rememberVisibleThreat(existing);
            return existing;
        }
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        if (now < this.nextDetectionAt) return null;
        this.nextDetectionAt = now + 10;
        double range = NightmareEndermanConfig.followRange();
        double nearestDistance = range * range;
        ServerPlayer nearest = null;
        for (ServerPlayer candidate : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(candidate);
            if (distance <= nearestDistance && this.isVisibleThreat(candidate)) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest != null) this.beginEncounter(nearest, now);
        return nearest;
    }

    @Override
    protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (this.lastObserved != null && now > this.lastSeenAt && target == this.currentTarget()) {
            Vec3 radial = this.lastObserved.subtract(this.owner.position()).multiply(1.0, 0.0, 1.0).normalize();
            this.retreating = target.position().subtract(this.lastObserved).dot(radial) > 0.045;
        }
        super.rememberVisibleThreat(target);
        this.lastObserved = target.position();
        this.lastSeenAt = now;
    }

    public void onAttacked(ServerLevel level, LivingEntity attacker) {
        if (!this.isLegalThreat(attacker) || this.owner.isNoAi()) return;
        LivingEntity current = this.currentTarget();
        if (current == null) {
            this.beginEncounter(attacker, level.getGameTime());
        } else if (current != attacker) {
            this.clearIllusions(false);
            this.encounterTarget = new WeakReference<>(attacker);
            this.rememberVisibleThreat(attacker);
        }
        this.handledHurtTimestamp = this.owner.getLastHurtByMobTimestamp();
        this.commitToHunt(level, attacker);
    }

    public boolean commitToHunt(ServerLevel level, LivingEntity target) {
        if (!this.isLegalThreat(target) || this.owner.isNoAi() || this.committed) return false;
        if (this.currentTarget() == null) this.beginEncounter(target, level.getGameTime());
        if (target != this.currentTarget()) return false;
        // Disable the ability first, then close its client session before publishing the reveal.
        this.committed = true;
        this.nextIllusionAt = Long.MAX_VALUE;
        this.clearIllusions(true);
        this.stopCombat();
        this.stopAmbient();
        this.clearTarget();
        this.brace();
        this.nightmare.setHuntCommitted(true);
        this.revealUntil = level.getGameTime() + NightmareEndermanConfig.revealTicks();
        this.nightmare.setCombatPhase(CombatPhase.REVEAL);
        this.nightmare.revealHunt(level);
        return true;
    }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) {
            this.clearEncounter();
            return;
        }
        LivingEntity target = this.currentTarget();
        if (this.encounterTarget != null && !this.isLegalThreat(target)) this.clearEncounter();
        LivingEntity visible = this.observeThreat();
        target = this.currentTarget();
        if (target == null) {
            this.nightmare.setCombatPhase(CombatPhase.IDLE);
            this.tickAmbient();
            return;
        }
        // Neither path completion nor native WALK_TARGET expiry can reset a committed confrontation.
        if (visible == null && now - this.lastSeenAt >= Math.max(this.memoryTicks(), NightmareEndermanConfig.searchTicks())) {
            this.clearEncounter();
            return;
        }
        if (!this.committed) {
            if (now - this.encounterStartedAt >= NightmareEndermanConfig.preHuntTicks()
                    || visible != null && this.owner.distanceToSqr(visible) <= square(NightmareEndermanConfig.closeCommitRange())) {
                this.commitToHunt(level, target);
                return;
            }
            this.tickPreHunt(level, visible, now);
            return;
        }
        if (now < this.revealUntil) {
            this.brace();
            return;
        }
        if (this.tickFloating()) {
            this.nightmare.setCombatPhase(CombatPhase.SEARCHING);
            return;
        }
        this.sampleProgress(now);
        if (visible == null) {
            this.search(level, now);
            return;
        }
        if ((this.stalledTicks >= 40 || this.retreating && this.owner.distanceToSqr(visible) > 144.0)
                && this.tryReposition(now)) return;
        this.tickVisibleCombat(visible);
        if (this.nightmare.getCombatPhase() != CombatPhase.GRASP || now - this.nightmare.getCombatPhaseStart() >= 9) {
            this.nightmare.setCombatPhase(CombatPhase.PURSUIT);
        }
    }

    private void tickPreHunt(ServerLevel level, @Nullable LivingEntity visible, long now) {
        this.stopCombat();
        this.clearTarget();
        this.stopAmbient();
        if (visible == null) {
            this.search(level, now);
            return;
        }
        this.owner.getLookControl().setLookAt(visible, 25.0F, 25.0F);
        if (visible instanceof ServerPlayer player && now >= this.nextIllusionAt && this.lastObserved != null) {
            this.nextIllusionAt = now + NightmareEndermanConfig.illusionCooldown();
            if (NightmareIllusions.tryShow(player, this.nightmare, this.nightmare.getEncounterId(), this.lastObserved,
                    NightmareEndermanConfig.maximumApparitions(), NightmareEndermanConfig.illusionLifetime())) {
                this.illusionPoseUntil = now + 9;
                this.nightmare.setCombatPhase(CombatPhase.ILLUSIONS);
                if (now >= this.nextDarknessAt && !player.hasEffect(MobEffects.DARKNESS)
                        && player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                                NightmareEndermanConfig.darknessDuration(), 0, false, false, true), this.owner)) {
                    this.nextDarknessAt = now + NightmareEndermanConfig.darknessCooldown();
                }
            }
        }
        if (now < this.illusionPoseUntil) {
            this.brace();
            return;
        }
        if (this.owner.distanceToSqr(visible) > 100.0 && now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            Vec3 radial = this.owner.position().subtract(visible.position()).multiply(1.0, 0.0, 1.0).normalize();
            Vec3 side = new Vec3(-radial.z, 0.0, radial.x).scale(this.leftSide ? 3.0 : -3.0);
            Vec3 stalking = visible.position().add(radial.scale(8.0)).add(side);
            if (level.hasChunkAt(BlockPos.containing(stalking))) {
                this.owner.getNavigation().moveTo(stalking.x, stalking.y, stalking.z, 0.55);
            }
            this.nightmare.setCombatPhase(CombatPhase.STALKING);
        } else if (this.owner.distanceToSqr(visible) <= 100.0) {
            this.brace();
            this.nightmare.setCombatPhase(CombatPhase.OBSERVING);
        }
    }

    private void search(ServerLevel level, long now) {
        this.stopCombat();
        this.clearTarget();
        this.stopAmbient();
        this.state = State.INVESTIGATE;
        this.nightmare.setCombatPhase(CombatPhase.SEARCHING);
        Vec3 observed = this.lastObserved;
        if (observed == null) return;
        this.owner.getLookControl().setLookAt(observed.x, observed.y + 1.5, observed.z);
        if (this.owner.distanceToSqr(observed) <= 2.25) {
            this.owner.getNavigation().stop();
            return;
        }
        if (now >= this.nextPathAt && level.hasChunkAt(BlockPos.containing(observed))) {
            this.nextPathAt = now + 20;
            this.owner.getNavigation().moveTo(observed.x, observed.y, observed.z, this.committed ? 1.0 : 0.55);
        }
        if (this.committed && this.stalledTicks >= 40) this.tryReposition(now);
    }

    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return;
        this.nextProgressAt = now + 20;
        Vec3 current = this.owner.position();
        if (this.lastObserved != null && this.owner.distanceToSqr(this.lastObserved) > 25.0
                && this.progressPosition != null && current.distanceToSqr(this.progressPosition) < 0.36) {
            this.stalledTicks += 20;
        } else {
            this.stalledTicks = 0;
        }
        this.progressPosition = current;
    }

    private boolean tryReposition(long now) {
        Vec3 observed = this.lastObserved;
        if (observed == null || now < this.nextTeleportAt || this.owner.isInWater()) return false;
        double distance = this.owner.distanceToSqr(observed);
        if (distance < 64.0 || distance > 900.0) return false;
        this.nextTeleportAt = now + (this.committed ? NightmareEndermanConfig.huntTeleportCooldown()
                : NightmareEndermanConfig.teleportCooldown());
        Vec3 radial = this.owner.position().subtract(observed).multiply(1.0, 0.0, 1.0).normalize();
        Vec3 side = new Vec3(-radial.z, 0.0, radial.x).scale(this.leftSide ? 4.0 : -4.0);
        this.leftSide = !this.leftSide;
        Vec3 requested = observed.add(radial.scale(2.0)).add(side).add(0.0, 1.0, 0.0);
        if (!this.nightmare.tryTacticalTeleport(requested, observed)) return false;
        this.progressPosition = this.owner.position();
        this.stalledTicks = 0;
        return true;
    }

    private void brace() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
        this.owner.setDeltaMovement(0.0, this.owner.getDeltaMovement().y, 0.0);
    }

    private void clearIllusions(boolean huntCommitted) {
        LivingEntity target = this.currentTarget();
        if (target instanceof ServerPlayer player) {
            NightmareIllusions.clear(player, this.nightmare, this.nightmare.getEncounterId(), huntCommitted);
        }
    }

    @Override
    public void clearEncounter() {
        this.clearIllusions(this.committed);
        super.clearEncounter();
        this.encounterTarget = null;
        this.lastObserved = null;
        this.progressPosition = null;
        this.stalledTicks = 0;
        this.retreating = false;
        this.committed = false;
        this.revealUntil = 0;
        this.illusionPoseUntil = 0;
        this.nextDetectionAt = this.owner.level().getGameTime() + 20;
        this.nightmare.setHuntCommitted(false);
        this.nightmare.setCombatPhase(CombatPhase.IDLE);
    }

    private static double square(double value) { return value * value; }
}
