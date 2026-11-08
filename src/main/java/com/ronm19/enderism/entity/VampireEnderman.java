package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.VampireEndermanConfig;
import com.ronm19.enderism.combat.VampireMobility;
import com.ronm19.enderism.entity.ai.VampireEndermanBrain;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** #027: repeated successful physical attacks feed a finite vampiric hunt. */
public final class VampireEnderman extends AbstractEnderman {
    public static final Identifier FURY_DAMAGE_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "vampire_fury_damage");
    public static final Identifier FURY_SPEED_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "vampire_fury_speed");
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(VampireEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(VampireEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(VampireEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FURY_UNTIL = SynchedEntityData.defineId(VampireEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DRAIN_UNTIL = SynchedEntityData.defineId(VampireEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(VampireEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(0xAB163C, .7F);
    private final VampireMobility mobility = new VampireMobility(this);
    private final Map<UUID, DrainProgress> drains = new HashMap<>();
    private @Nullable Vec3 teleportObservation;
    private boolean striking;
    private @Nullable LivingEntity strikingTarget;
    private float nativeStrikeHealthDamage, lastDrainHealing;

    public VampireEnderman(EntityType<? extends VampireEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false); this.setPathfindingMalus(PathType.WATER, 4); this.setPathfindingMalus(PathType.WATER_BORDER, 0); this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, VampireEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, VampireEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, VampireEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, VampireEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, .3);
    }
    public static void registerEvents(IEventBus bus) { bus.addListener(VampireEnderman::acceptedMeleeDamage); }
    private static void acceptedMeleeDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof VampireEnderman source && event.getSource().getDirectEntity() == source
                && event.getSource().is(DamageTypes.MOB_ATTACK) && source.striking && source.strikingTarget == event.getEntity()
                && source.getMastermindBrain().isStrikeImpactFrame(event.getEntity()) && Float.isFinite(event.getHealthDamage()) && event.getHealthDamage() > 0)
            source.nativeStrikeHealthDamage = Math.max(source.nativeStrikeHealthDamage, event.getHealthDamage());
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !VampireEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VampireEndermanConfig.health()); this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(VampireEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(VampireEndermanConfig.movementSpeed()); this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(VampireEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected VampireEndermanBrain createMastermindBrain() { return new VampireEndermanBrain(this); }
    @Override public VampireEndermanBrain getMastermindBrain() { return (VampireEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    public VampireMobility getMobility() { return this.mobility; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L);
        data.define(FURY_UNTIL, 0L); data.define(DRAIN_UNTIL, 0L); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public int getDrainPulseTicksRemaining() { return (int)Math.max(0, this.entityData.get(DRAIN_UNTIL) - this.level().getGameTime()); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public boolean isFuryActive() { return this.level().getGameTime() < this.entityData.get(FURY_UNTIL); }
    public long getFuryEndsAt() { return this.entityData.get(FURY_UNTIL); }
    public float getEyeBrightness() { return this.isFuryActive() ? 1 : this.getDrainPulseTicksRemaining() > 0 ? .95F : this.getCombatPhase() == CombatPhase.FURY_WINDUP ? .9F : this.isActivelyHunting() ? .7F : .45F; }
    public void setActivelyHunting(boolean value) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, value); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalVampireTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return !this.striking && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginStrike(level, victim, level.getGameTime()); }
    public boolean performVampiricStrike(ServerLevel level, LivingEntity target) {
        this.lastDrainHealing = 0;
        if (this.striking || !this.getMastermindBrain().isStrikeImpactFrame(target) || !this.isLegalVampireTarget(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float beforeHealth = target.getHealth(); boolean accepted;
        this.striking = true; this.strikingTarget = target; this.nativeStrikeHealthDamage = 0;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; this.strikingTarget = null; }
        float healthRemoved = Math.min(this.nativeStrikeHealthDamage, Math.min(beforeHealth, beforeHealth - target.getHealth()));
        if (!accepted || !Float.isFinite(healthRemoved) || healthRemoved <= 0 || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || !this.getMastermindBrain().isStrikeImpactFrame(target) || !this.isLegalVampireTarget(target)) return false;
        this.countQualifyingHit(level, target, healthRemoved);
        level.sendParticles(CRIMSON, target.getX(), target.getY(.55), target.getZ(), 5, .2, .25, .2, .01);
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, .6F, .85F); return true;
    }
    private void pruneDrains() {
        long now = this.level().getGameTime(); this.drains.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now || !this.isLegalVampireTarget(entry.getValue().target.get()));
    }
    private void countQualifyingHit(ServerLevel level, LivingEntity target, float healthRemoved) {
        this.pruneDrains(); long now = level.getGameTime(); UUID id = target.getUUID();
        DrainProgress prior = this.drains.get(id); int count = prior != null && prior.target.get() == target ? prior.hits + 1 : 1;
        if (count < VampireEndermanConfig.drainRequiredHits()) {
            if (this.drains.size() >= 8 && !this.drains.containsKey(id)) this.drains.entrySet().stream().min(Map.Entry.comparingByValue(java.util.Comparator.comparingLong(DrainProgress::expiresAt))).ifPresent(entry -> this.drains.remove(entry.getKey()));
            this.drains.put(id, new DrainProgress(new WeakReference<>(target), count, now + VampireEndermanConfig.drainBuildupTicks())); return;
        }
        // Consume the threshold before native healing callbacks can reenter any combat logic.
        this.drains.remove(id); float before = this.getHealth();
        float amount = (float)Math.min(VampireEndermanConfig.maxHealingPerDrain(), healthRemoved * VampireEndermanConfig.drainHealingRatio());
        amount = Math.min(amount, Math.max(0, this.getMaxHealth() - before));
        if (amount <= 0) return;
        this.heal(amount); this.lastDrainHealing = Math.max(0, this.getHealth() - before);
        if (this.lastDrainHealing <= 0 || !this.isAlive() || this.isRemoved() || this.isNoAi()) return;
        this.entityData.set(DRAIN_UNTIL, now + 10);
        Vec3 from = target.position().add(0, target.getBbHeight() * .55, 0), to = this.position().add(0, this.getBbHeight() * .65, 0);
        for (int step = 0; step < 6; step++) { Vec3 point = from.lerp(to, step / 5.0); level.sendParticles(CRIMSON, point.x, point.y, point.z, 2, .06, .06, .06, .005); }
        this.playSound(SoundEvents.ENDERMAN_STARE, .55F, 1.35F);
    }
    public int getDrainProgress(LivingEntity target) { this.pruneDrains(); var entry = this.drains.get(target.getUUID()); return entry != null && entry.target.get() == target ? entry.hits : 0; }
    public int getTrackedDrainTargets() { this.pruneDrains(); return this.drains.size(); }
    public float getLastDrainHealing() { return this.lastDrainHealing; }
    public void clearDrainBuildup() { this.drains.clear(); this.lastDrainHealing = 0; if (!this.level().isClientSide()) this.entityData.set(DRAIN_UNTIL, 0L); }
    public boolean beginBloodFury(ServerLevel level, long now) {
        if (this.level() != level || !this.isAlive() || this.isRemoved() || this.isNoAi() || this.isFuryActive() || !this.getMastermindBrain().isFuryReleaseFrame()) return false;
        this.entityData.set(FURY_UNTIL, now + VampireEndermanConfig.furyDurationTicks()); this.reconcileFury();
        level.sendParticles(CRIMSON, this.getX(), this.getY(.6), this.getZ(), 15, .4, .7, .4, .02); this.playSound(SoundEvents.ENDERMAN_SCREAM, .7F, 1.1F); return true;
    }
    private void reconcileFury() {
        if (this.level().isClientSide()) return;
        if (!this.isFuryActive() || !this.isAlive() || this.isRemoved() || this.isNoAi()) { this.clearBloodFury(); return; }
        var damage = new AttributeModifier(FURY_DAMAGE_ID, VampireEndermanConfig.furyDamageBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        var speed = new AttributeModifier(FURY_SPEED_ID, VampireEndermanConfig.furyMovementBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        for (var pair : java.util.List.of(java.util.Map.entry(Attributes.ATTACK_DAMAGE, damage), java.util.Map.entry(Attributes.MOVEMENT_SPEED, speed))) {
            var attribute = this.getAttribute(pair.getKey()); var modifier = pair.getValue();
            if (!modifier.equals(attribute.getModifier(modifier.id()))) { attribute.removeModifier(modifier.id()); attribute.addTransientModifier(modifier); }
        }
    }
    public void clearBloodFury() {
        if (this.level().isClientSide()) return;
        this.entityData.set(FURY_UNTIL, 0L); this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(FURY_DAMAGE_ID); this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(FURY_SPEED_ID);
    }
    public void abilityWarning(ServerLevel level, CombatPhase phase) { this.playSound(SoundEvents.ENDERMAN_AMBIENT, phase == CombatPhase.FURY_WINDUP ? .8F : .4F, phase == CombatPhase.FURY_WINDUP ? .65F : 1.3F); level.sendParticles(CRIMSON, this.getX(), this.getY(.65), this.getZ(), phase == CombatPhase.FURY_WINDUP ? 10 : 3, .25, .3, .25, .01); }
    public boolean tryVampireReposition(Vec3 observed, boolean left) {
        if (!(this.level() instanceof ServerLevel) || !observed.isFinite() || this.isNoAi() || this.getCombatPhase().isCommitted() || !this.getMastermindBrain().isTargetLocalized()
                || this.getMastermindBrain().getLastLocatedPosition() == null || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return false;
        Vec3 away = this.position().subtract(observed).multiply(1, 0, 1); if (away.horizontalDistanceSqr() < .01) away = new Vec3(1, 0, 0); away = away.normalize(); this.teleportObservation = observed;
        try { return this.trySafeTeleport(observed.add(away.scale(4.5)).add(new Vec3(-away.z, 0, away.x).scale(left ? 3 : -3)).add(0, 2, 0), 5); } finally { this.teleportObservation = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return VampireEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) { if (this.teleportObservation == null) return true; double distance = destination.subtract(this.teleportObservation).horizontalDistanceSqr(); return distance >= 2.5 * 2.5 && distance <= 8 * 8 && Math.abs(destination.y - this.teleportObservation.y) <= 3; }
    @Override public void aiStep() { if (!this.level().isClientSide()) { if (this.isNoAi() || !this.isAlive()) this.getMastermindBrain().clearEncounter(); else { this.reconcileFury(); this.pruneDrains(); } } super.aiStep(); }
    @Override public void travel(Vec3 input) {
        CombatPhase phase = this.getCombatPhase();
        if (!this.level().isClientSide() && phase == CombatPhase.LEAP) super.travel(Vec3.ZERO);
        else if (!this.level().isClientSide() && phase.isCommitted()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); }
        else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input); }
    private record DrainProgress(WeakReference<LivingEntity> target, int hits, long expiresAt) { }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, STRIKE_WINDUP, STRIKE, DRAIN, DASH_WINDUP, DASH, LEAP, FURY_WINDUP, FURY, RECOVERY;
        public boolean isCommitted() { return this != IDLE && this != PURSUING && this != SEARCHING && this != FURY; }
        public boolean isCasting() { return this == STRIKE_WINDUP || this == STRIKE || this == DRAIN || this == DASH_WINDUP || this == DASH || this == FURY_WINDUP; }
    }
}
