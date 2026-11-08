package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.FireEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.FireEnderman;
import com.ronm19.enderism.entity.FireEnderman.Ability;
import java.lang.ref.WeakReference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Three active actions layered onto the shared Brain's finite ground pursuit and investigation. */
public final class FireEndermanBrain extends MastermindBrain {
    private final FireEnderman fire;
    private long nextPlayerScanAt, nextHexAt, nextBurstAt, recoveryUntil, encounterSince = -1, epoch;
    private @Nullable WeakReference<LivingEntity> pending;
    private boolean hexRelease, burstRelease, releasing;
    public FireEndermanBrain(FireEnderman owner) { super(owner); this.fire = owner; }
    public long actionEpoch() { return this.epoch; }
    public boolean isReleasingFireAction() { return this.releasing; }
    @Override protected int meleeCooldownTicks() { return FireEndermanConfig.meleeIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return this.fire.isLegalFireTarget(target); }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity threat = super.observeThreat();
        if (threat != null) return threat;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime() < this.nextPlayerScanAt) return null;
        this.nextPlayerScanAt = level.getGameTime() + 10;
        double closest = FireEndermanConfig.followRange() * FireEndermanConfig.followRange();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(FireEndermanConfig.followRange()))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= closest && this.isVisibleThreat(player)) { threat = player; closest = distance; }
        }
        if (threat != null) this.rememberVisibleThreat(threat);
        return threat;
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!this.fire.isAlive() || this.fire.isNoAi() || this.fire.isRemoved()) { this.clearEncounter(); return; }
        if (this.pending != null) { this.tickCast(level, now); return; }
        if (now < this.recoveryUntil) { this.brace(); return; }
        if (this.fire.getAbility() != Ability.NONE && now >= this.fire.getAbilityEndsAt()) this.fire.setAbility(Ability.NONE, 0);
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            if (this.encounterSince < 0) this.encounterSince = now;
            if (now - this.encounterSince >= 60 && this.tryBeginCinderBurst(level, visible, now)) return;
            if (this.tryBeginHungerHex(level, visible, now)) return;
            this.tickVisibleCombat(visible); this.fire.setHuntState(FireEnderman.HuntState.PURSUING); return;
        }
        super.tick(level, body, now);
        this.fire.setHuntState(switch (this.state) { case IDLE -> FireEnderman.HuntState.IDLE; case COMBAT -> FireEnderman.HuntState.PURSUING; case INVESTIGATE -> FireEnderman.HuntState.SEARCHING; });
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target, long now, double range) {
        return !this.releasing && !this.fire.isResolvingFireAction() && this.pending == null && now >= this.recoveryUntil && this.owner.level() == level && this.fire.canCastOn(target, range);
    }
    public boolean tryBeginHungerHex(ServerLevel level, LivingEntity target, long now) {
        if (!(target instanceof Player) || now < this.nextHexAt || !this.mayBegin(level, target, now, FireEndermanConfig.hexRange())) return false;
        var effect = target.getEffect(MobEffects.HUNGER);
        if (effect != null && effect.getAmplifier() >= FireEndermanConfig.hexHungerAmplifier() && effect.getDuration() > 60) return false;
        this.nextHexAt = now + FireEndermanConfig.hexCooldownTicks(); this.begin(level, target, Ability.HUNGER_HEX, now + FireEndermanConfig.hexWarningTicks()); return true;
    }
    public boolean tryBeginCinderBurst(ServerLevel level, LivingEntity target, long now) {
        if (now < this.nextBurstAt || !this.mayBegin(level, target, now, FireEndermanConfig.burstRadius())) return false;
        this.nextBurstAt = now + FireEndermanConfig.burstCooldownTicks(); this.begin(level, target, Ability.CINDER_BURST, now + FireEndermanConfig.burstWarningTicks()); return true;
    }
    private void begin(ServerLevel level, LivingEntity target, Ability ability, long until) {
        this.rememberVisibleThreat(target); this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.pending = new WeakReference<>(target);
        this.state = State.COMBAT; this.fire.setAbility(ability, until); this.fire.setHuntState(FireEnderman.HuntState.PURSUING); this.brace(); this.fire.abilityWarning(level);
    }
    private void tickCast(ServerLevel level, long now) {
        LivingEntity target = this.pending == null ? null : this.pending.get(); Ability action = this.fire.getAbility();
        double range = action == Ability.HUNGER_HEX ? FireEndermanConfig.hexRange() : FireEndermanConfig.burstRadius();
        if (target == null || !this.fire.canCastOn(target, range) || this.owner.isPassenger() || this.owner.isVehicle()) { this.cancel(now); return; }
        this.brace(); this.owner.getLookControl().setLookAt(target, 40, 40);
        if (now < this.fire.getAbilityEndsAt()) return;
        long token = this.epoch; this.releasing = true;
        try {
            if (action == Ability.HUNGER_HEX) { this.hexRelease = true; this.fire.releaseHungerHex(level, target); }
            else if (action == Ability.CINDER_BURST) { this.burstRelease = true; this.fire.releaseCinderBurst(level); }
        } finally { this.hexRelease = this.burstRelease = this.releasing = false; }
        if (this.epoch == token) this.cancel(now);
    }
    public boolean isHexReleaseFrame(LivingEntity target) { return this.releasing && this.hexRelease && this.pending != null && this.pending.get() == target && this.fire.getAbility() == Ability.HUNGER_HEX; }
    public boolean isBurstReleaseFrame() { return this.releasing && this.burstRelease && this.pending != null && this.fire.getAbility() == Ability.CINDER_BURST; }
    private void cancel(long now) { this.pending = null; this.fire.setAbility(Ability.NONE, 0); this.recoveryUntil = now + FireEndermanConfig.recoveryTicks(); }
    private void brace() { this.stopCombat(); this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }
    @Override public void clearEncounter() { this.epoch++; super.clearEncounter(); this.pending = null; this.hexRelease = this.burstRelease = false; this.encounterSince = -1; this.fire.setAbility(Ability.NONE, 0); this.fire.setHuntState(FireEnderman.HuntState.IDLE); }
    public void writeCooldowns(ValueOutput output) { output.putLong("FireHexAfter", this.nextHexAt); output.putLong("FireBurstAfter", this.nextBurstAt); }
    public void readCooldowns(ValueInput input) { long now = this.owner.level().getGameTime(); this.nextHexAt = Math.min(input.getLongOr("FireHexAfter", 0), now + FireEndermanConfig.hexCooldownTicks()); this.nextBurstAt = Math.min(input.getLongOr("FireBurstAfter", 0), now + FireEndermanConfig.burstCooldownTicks()); }
}
