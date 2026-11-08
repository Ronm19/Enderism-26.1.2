package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.SculkEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.SculkEnderman;
import com.ronm19.enderism.entity.SculkEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native Brain decision owner whose knowledge is acquired through vibration, touch-range or actual attacks. */
public final class SculkEndermanBrain extends MastermindBrain {
    private final SculkEnderman huntress;
    private @Nullable WeakReference<LivingEntity> sensedTarget, pendingTarget;
    private @Nullable Vec3 lastLocated, progressPosition;
    private final Map<UUID, Long> darknessAfter = new HashMap<>();
    private long knownUntil, nextCrusherAt, nextTeleportAt, nextPathAt, nextProgressAt, nextCueAt, phaseEndsAt;
    private int blockedTicks;
    private boolean crusherImpact, leftFlank;

    public SculkEndermanBrain(SculkEnderman owner) { super(owner); this.huntress = owner; }
    @Override protected int memoryTicks() { return SculkEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return SculkEndermanConfig.crusherIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.huntress.isLegalSculkTarget(target); }
    public @Nullable LivingEntity getSensedTarget() { return this.sensedTarget == null ? null : this.sensedTarget.get(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.lastLocated; }
    public long getKnowledgeExpiresAt() { return this.knownUntil; }
    public boolean hasInvestigation() { return this.lastLocated != null && this.owner.level().getGameTime() < this.knownUntil; }
    public boolean hasPendingAbility() { return this.huntress.getCombatPhase().isCommitted(); }
    public long nextDarknessReadyAt(Player player) { return this.darknessAfter.getOrDefault(player.getUUID(), 0L); }
    public boolean hasSensoryKnowledge(LivingEntity target) { return this.hasInvestigation() && this.getSensedTarget() == target && this.isLegalThreat(target); }

    /** Called only by the native listener after source filtering, travel and wool occlusion. */
    public void onVibration(Vec3 point, @Nullable Player player, long now) {
        if (!(this.owner.level() instanceof ServerLevel level) || !this.canSense() || !point.isFinite()
                || this.owner.distanceToSqr(point) > square(SculkEndermanConfig.vibrationRange() + 2)
                || player != null && !this.isLegalThreat(player)) return;
        this.record(point, player, now, SculkEndermanConfig.investigationTicks());
        this.cue(level, now);
        if (player != null) this.awaken(level, player, now);
    }
    public void onDirectAttack(LivingEntity attacker, long now) {
        if (!(this.owner.level() instanceof ServerLevel level) || !this.canSense() || !this.isLegalThreat(attacker)) return;
        // A personally visible attacker is real evidence; an unseen attacker provides no omniscient coordinates.
        if (super.isVisibleThreat(attacker)) {
            this.record(attacker.position(), attacker, now, this.memoryTicks()); this.cue(level, now);
            if (attacker instanceof Player player) this.awaken(level, player, now);
        } else { this.record(this.owner.position(), null, now, this.memoryTicks()); this.cue(level, now); }
        this.handledHurtTimestamp = this.owner.getLastHurtByMobTimestamp();
    }
    private boolean canSense() { return this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi(); }
    private void record(Vec3 point, @Nullable LivingEntity target, long now, int duration) {
        this.lastLocated = new Vec3(point.x, point.y, point.z); this.knownUntil = now + duration;
        this.sensedTarget = target == null ? null : new WeakReference<>(target); this.huntress.setActivelyHunting(true);
        // Do not install a live EntityTracker/WALK_TARGET. Outside close range the frozen point is authoritative.
        this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY); this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
    private void cue(ServerLevel level, long now) {
        if (now < this.nextCueAt) return; this.nextCueAt = now + SculkEndermanConfig.detectionCueIntervalTicks();
        this.huntress.detectionCue(level);
        if (!this.hasPendingAbility()) { this.huntress.setCombatPhase(CombatPhase.DETECTING); this.phaseEndsAt = now + 8; }
    }
    private void awaken(ServerLevel level, Player player, long now) {
        if (!this.isLegalThreat(player) || this.owner.distanceToSqr(player) > square(Math.max(SculkEndermanConfig.vibrationRange(), SculkEndermanConfig.proximityRange()) + 2)
                || now < this.darknessAfter.getOrDefault(player.getUUID(), 0L)) return;
        this.darknessAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (this.darknessAfter.size() >= 16) this.darknessAfter.entrySet().stream().min(Map.Entry.comparingByValue()).ifPresent(entry -> this.darknessAfter.remove(entry.getKey()));
        this.darknessAfter.put(player.getUUID(), now + SculkEndermanConfig.darknessRefreshTicks());
        var current = player.getEffect(MobEffects.DARKNESS);
        if (current == null || current.getDuration() < SculkEndermanConfig.darknessTicks())
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SculkEndermanConfig.darknessTicks(), 0), this.huntress);
    }
    private @Nullable ServerPlayer nearbyPlayer(ServerLevel level) {
        double radius = SculkEndermanConfig.proximityRange(), closest = radius * radius; ServerPlayer chosen = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(radius))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= closest && this.isLegalThreat(player)) { closest = distance; chosen = player; }
        }
        return chosen;
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        // Never call the shared visual/anger observer: it would refresh distant silent players indefinitely.
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        ServerPlayer close = this.nearbyPlayer(level);
        if (close != null) return close;
        LivingEntity known = this.getSensedTarget();
        return known != null && this.hasSensoryKnowledge(known) && this.owner.distanceToSqr(known) <= square(SculkEndermanConfig.proximityRange()) ? known : null;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        // Shared actuator callers may remember only personally confirmed close-range evidence.
        if (this.owner.distanceToSqr(target) <= square(SculkEndermanConfig.proximityRange())) this.record(target.position(), target, this.owner.level().getGameTime(), this.memoryTicks());
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!this.canSense()) { this.clearEncounter(); return; }
        LivingEntity known = this.getSensedTarget();
        if (this.sensedTarget != null && !this.isLegalThreat(known)) this.clearEncounter();
        this.darknessAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
        LivingEntity close = this.observeThreat();
        if (close != null) { this.record(close.position(), close, now, this.memoryTicks()); if (close instanceof Player player) this.awaken(level, player, now); if (!this.canSense()) { this.clearEncounter(); return; } this.cue(level, now); }
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) return;
        if (close != null && this.owner.getSensing().hasLineOfSight(close)) {
            this.huntress.setCombatPhase(CombatPhase.PURSUING); this.tickVisibleCombat(close); return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.hasInvestigation()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.huntress.setActivelyHunting(true);
            if (this.huntress.getCombatPhase() != CombatPhase.DETECTING || now >= this.phaseEndsAt)
                this.huntress.setCombatPhase(this.getSensedTarget() == null ? CombatPhase.INVESTIGATING : CombatPhase.SEARCHING);
            this.investigate(level, now); return;
        }
        if (this.lastLocated != null || this.state != State.IDLE || this.huntress.isActivelyHunting()) this.clearEncounter();
        this.huntress.setCombatPhase(CombatPhase.LISTENING); this.tickAmbient();
    }
    public boolean tryBeginCrusher(ServerLevel level, LivingEntity target, long now) {
        if (this.owner.level() != level || !this.canSense() || !this.isLegalThreat(target) || this.owner.isPassenger() || this.owner.isVehicle()
                || this.hasPendingAbility() || now < this.nextCrusherAt || !this.owner.isWithinMeleeAttackRange(target)
                || !this.owner.getSensing().hasLineOfSight(target) || this.owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return false;
        if (!this.hasSensoryKnowledge(target)) {
            if (!(target instanceof Player player) || this.owner.distanceToSqr(target) > square(SculkEndermanConfig.proximityRange())) return false;
            this.record(target.position(), target, now, this.memoryTicks()); this.awaken(level, player, now);
        }
        this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.brace();
        this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT;
        this.nextCrusherAt = now + this.meleeCooldownTicks(); this.phaseEndsAt = now + SculkEndermanConfig.crusherWindupTicks();
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, this.meleeCooldownTicks());
        this.huntress.setCombatPhase(CombatPhase.CRUSHER_WINDUP, this.phaseEndsAt); this.huntress.crusherWarning(level); return true;
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.huntress.getCombatPhase(); if (!phase.isCommitted()) return false;
        this.brace();
        if (phase == CombatPhase.RECOVERY || phase == CombatPhase.CRUSHER) {
            if (now >= this.phaseEndsAt) { if (phase == CombatPhase.RECOVERY) this.huntress.setCombatPhase(CombatPhase.PURSUING); else this.finish(now); }
            return true;
        }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegalThreat(target) || !this.hasSensoryKnowledge(target) || !this.owner.getSensing().hasLineOfSight(target)
                || this.owner.isPassenger() || this.owner.isVehicle()) { this.finish(now); return true; }
        this.owner.getLookControl().setLookAt(target, 35, 35);
        if (now < this.phaseEndsAt) return true;
        this.huntress.setCombatPhase(CombatPhase.CRUSHER); this.crusherImpact = true;
        try { this.huntress.performSculkCrusher(level, target); } finally { this.crusherImpact = false; this.pendingTarget = null; }
        this.phaseEndsAt = now + 5; return true;
    }
    public boolean isCrusherImpactFrame(LivingEntity target) { return this.crusherImpact && this.getPendingTarget() == target && this.huntress.getCombatPhase() == CombatPhase.CRUSHER; }
    private void finish(long now) { this.pendingTarget = null; this.crusherImpact = false; this.phaseEndsAt = now + SculkEndermanConfig.recoveryTicks(); this.huntress.setCombatPhase(CombatPhase.RECOVERY, this.phaseEndsAt); }
    private void brace() { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    private void investigate(ServerLevel level, long now) {
        Vec3 point = this.lastLocated; if (point == null) return;
        this.owner.getLookControl().setLookAt(point.x, point.y + 1.5, point.z, 35, 35);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.brace(); return; }
        if (now >= this.nextProgressAt) {
            this.nextProgressAt = now + 20;
            if (this.progressPosition != null && this.owner.position().distanceToSqr(this.progressPosition) < .3) this.blockedTicks = Math.min(100, this.blockedTicks + 20); else this.blockedTicks = 0;
            this.progressPosition = this.owner.position();
        }
        if (this.blockedTicks >= 40 && now >= this.nextTeleportAt) {
            this.nextTeleportAt = now + SculkEndermanConfig.tacticalTeleportCooldownTicks(); this.leftFlank = !this.leftFlank;
            if (this.huntress.trySculkReposition(point, this.leftFlank)) { this.blockedTicks = 0; return; }
        }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20; BlockPos block = BlockPos.containing(point);
            if (level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) != null)
                this.owner.getNavigation().moveTo(point.x, point.y, point.z, SculkEndermanConfig.pursuitSpeed());
        }
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.pendingTarget = null; this.crusherImpact = false; this.sensedTarget = null; this.lastLocated = null; this.knownUntil = 0;
        this.progressPosition = null; this.blockedTicks = 0; this.phaseEndsAt = 0;
        this.huntress.setActivelyHunting(false); this.huntress.clearDetectionPulse(); this.huntress.setCombatPhase(CombatPhase.LISTENING);
    }
    public void writeCooldowns(ValueOutput output) { output.putLong("SculkCrusherAfter", this.nextCrusherAt); output.putLong("SculkTeleportAfter", this.nextTeleportAt); }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime(); this.nextCrusherAt = Math.min(input.getLongOr("SculkCrusherAfter", 0), now + this.meleeCooldownTicks());
        this.nextTeleportAt = Math.min(input.getLongOr("SculkTeleportAfter", 0), now + SculkEndermanConfig.tacticalTeleportCooldownTicks()); this.darknessAfter.clear();
    }
    private static double square(double value) { return value * value; }
}
