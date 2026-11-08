package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.WhiteEndermanConfig;
import com.ronm19.enderism.entity.ai.WhiteEndermanBrain;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** #029: a pale, grounded hunter whose temporary mark carries priority rather than hidden coordinates. */
public final class WhiteEnderman extends AbstractEnderman {
    public static final Identifier APEX_DAMAGE_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "white_apex_damage");
    public static final Identifier APEX_SPEED_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "white_apex_speed");
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(WhiteEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(WhiteEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(WhiteEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> APEX_UNTIL = SynchedEntityData.defineId(WhiteEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> MARK_PULSE_UNTIL = SynchedEntityData.defineId(WhiteEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(WhiteEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions PALE = new DustParticleOptions(0xECEAF5, .65F);
    private @Nullable WeakReference<LivingEntity> markedTarget;
    private long markExpiresAt;
    private @Nullable Vec3 teleportAnchor;
    private boolean striking;
    private @Nullable LivingEntity strikingTarget;
    private float nativeStrikeHealthDamage;

    public WhiteEnderman(EntityType<? extends WhiteEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false);
        this.setPathfindingMalus(PathType.WATER, 3); this.setPathfindingMalus(PathType.WATER_BORDER, 0);
        this.setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1); this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, WhiteEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, WhiteEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, WhiteEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, WhiteEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, .3);
    }
    public static void registerEvents(IEventBus bus) { bus.addListener(WhiteEnderman::acceptedMeleeDamage); }
    private static void acceptedMeleeDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof WhiteEnderman source && event.getSource().getDirectEntity() == source
                && event.getSource().is(DamageTypes.MOB_ATTACK) && source.striking && source.strikingTarget == event.getEntity()
                && source.getMastermindBrain().isMarkImpactFrame(event.getEntity()) && Float.isFinite(event.getHealthDamage()) && event.getHealthDamage() > 0)
            source.nativeStrikeHealthDamage = Math.max(source.nativeStrikeHealthDamage, event.getHealthDamage());
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !WhiteEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(WhiteEndermanConfig.health()); this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WhiteEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(WhiteEndermanConfig.movementSpeed()); this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(WhiteEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected WhiteEndermanBrain createMastermindBrain() { return new WhiteEndermanBrain(this); }
    @Override public WhiteEndermanBrain getMastermindBrain() { return (WhiteEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L);
        data.define(APEX_UNTIL, 0L); data.define(MARK_PULSE_UNTIL, 0L); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public int getMarkPulseTicksRemaining() { return (int)Math.max(0, this.entityData.get(MARK_PULSE_UNTIL) - this.level().getGameTime()); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public boolean isApexActive() { return this.level().getGameTime() < this.entityData.get(APEX_UNTIL); }
    public long getApexEndsAt() { return this.entityData.get(APEX_UNTIL); }
    public float getEyeBrightness() { return this.isApexActive() ? 1 : this.getCombatPhase() == CombatPhase.APEX_WINDUP ? .92F : this.getMarkPulseTicksRemaining() > 0 ? .9F : this.isActivelyHunting() ? .78F : .55F; }
    public void setActivelyHunting(boolean value) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, value); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalWhiteTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return !this.striking && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginMark(level, victim, level.getGameTime()); }
    public boolean performPredatorsMark(ServerLevel level, LivingEntity target) {
        if (this.striking || this.level() != level || !this.getMastermindBrain().isMarkImpactFrame(target) || !this.isLegalWhiteTarget(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth(); boolean accepted;
        this.striking = true; this.strikingTarget = target; this.nativeStrikeHealthDamage = 0;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; this.strikingTarget = null; }
        if (!accepted || this.nativeStrikeHealthDamage <= 0 || target.getHealth() >= before || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || !this.getMastermindBrain().isMarkImpactFrame(target) || !this.isLegalWhiteTarget(target)) return false;
        this.markedTarget = new WeakReference<>(target); this.markExpiresAt = level.getGameTime() + WhiteEndermanConfig.markDurationTicks();
        this.entityData.set(MARK_PULSE_UNTIL, level.getGameTime() + 8);
        level.sendParticles(PALE, target.getX(), target.getY(.55), target.getZ(), 5, .2, .25, .2, .01);
        this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, .55F, 1.25F); return true;
    }
    /** Returns only target identity and validity; it does not sample or refresh any target coordinates. */
    public @Nullable LivingEntity getMarkedTarget() {
        LivingEntity target = this.markedTarget == null ? null : this.markedTarget.get();
        if (this.level().isClientSide() || this.level().getGameTime() >= this.markExpiresAt || !this.isLegalWhiteTarget(target)) { this.clearPredatorsMark(); return null; }
        return target;
    }
    public boolean isMarkedTarget(LivingEntity target) { return this.getMarkedTarget() == target; }
    public boolean isMarked(LivingEntity target) { return this.isMarkedTarget(target); }
    public long getMarkExpiresAt() { this.getMarkedTarget(); return this.markExpiresAt; }
    public void clearPredatorsMark() { this.markedTarget = null; this.markExpiresAt = 0; if (!this.level().isClientSide()) this.entityData.set(MARK_PULSE_UNTIL, 0L); }
    public void clearMark() { this.clearPredatorsMark(); }
    public boolean beginApexHunt(ServerLevel level, long now) {
        if (this.level() != level || !this.isAlive() || this.isRemoved() || this.isNoAi() || this.isApexActive() || !this.getMastermindBrain().isApexReleaseFrame()) return false;
        this.entityData.set(APEX_UNTIL, now + WhiteEndermanConfig.apexDurationTicks()); this.reconcileApex();
        level.sendParticles(PALE, this.getX(), this.getY(.65), this.getZ(), 12, .35, .6, .35, .012); this.playSound(SoundEvents.ENDERMAN_STARE, .6F, .8F); return true;
    }
    private void reconcileApex() {
        if (this.level().isClientSide()) return;
        if (!this.isApexActive() || !this.isAlive() || this.isRemoved() || this.isNoAi()) { this.clearApexHunt(); return; }
        var damage = new AttributeModifier(APEX_DAMAGE_ID, WhiteEndermanConfig.apexDamageBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        var speed = new AttributeModifier(APEX_SPEED_ID, WhiteEndermanConfig.apexSpeedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        for (var pair : java.util.List.of(java.util.Map.entry(Attributes.ATTACK_DAMAGE, damage), java.util.Map.entry(Attributes.MOVEMENT_SPEED, speed))) {
            var attribute = this.getAttribute(pair.getKey()); var modifier = pair.getValue();
            if (!modifier.equals(attribute.getModifier(modifier.id()))) { attribute.removeModifier(modifier.id()); attribute.addTransientModifier(modifier); }
        }
    }
    public void clearApexHunt() {
        if (this.level().isClientSide()) return;
        this.entityData.set(APEX_UNTIL, 0L); this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(APEX_DAMAGE_ID); this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(APEX_SPEED_ID);
    }
    public void abilityWarning(ServerLevel level, CombatPhase phase) {
        this.playSound(SoundEvents.ENDERMAN_AMBIENT, phase == CombatPhase.APEX_WINDUP ? .65F : .3F, phase == CombatPhase.APEX_WINDUP ? .8F : 1.2F);
        level.sendParticles(PALE, this.getX(), this.getY(.65), this.getZ(), phase == CombatPhase.APEX_WINDUP ? 9 : 3, .22, .3, .22, .01);
    }
    public void warningCue(ServerLevel level, CombatPhase phase) { this.abilityWarning(level, phase); }
    public void gatheringCue(ServerLevel level) { level.sendParticles(PALE, this.getX(), this.getY(.6), this.getZ(), 2, .15, .25, .15, .005); }
    public @Nullable Vec3 findWhiteDestination(Vec3 requested, Vec3 observedAnchor) {
        if (!this.mayUseTacticalDestination(requested, observedAnchor)) return null;
        this.teleportAnchor = observedAnchor;
        try { return this.findSafeTeleportDestination(requested, 4); } finally { this.teleportAnchor = null; }
    }
    public boolean tryWhiteReposition(Vec3 destination, Vec3 observedAnchor) {
        if (!this.mayUseTacticalDestination(destination, observedAnchor)) return false;
        this.teleportAnchor = observedAnchor;
        try { return this.trySafeTeleport(destination, 0); } finally { this.teleportAnchor = null; }
    }
    private boolean mayUseTacticalDestination(Vec3 requested, Vec3 anchor) {
        return this.level() instanceof ServerLevel && this.isAlive() && !this.isRemoved() && !this.isNoAi() && !this.getCombatPhase().isCommitted()
                && requested.isFinite() && anchor.isFinite() && this.distanceToSqr(requested) <= WhiteEndermanConfig.teleportRange() * WhiteEndermanConfig.teleportRange()
                && this.getMastermindBrain().isTacticalAnchorValid(anchor);
    }
    @Override protected int teleportAttemptCooldownTicks() { return this.isApexActive() ? WhiteEndermanConfig.apexTeleportCooldownTicks() : WhiteEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (level.getBlockState(BlockPos.containing(destination)).is(Blocks.WITHER_ROSE)) return false;
        if (this.teleportAnchor == null) return true;
        double distance = destination.subtract(this.teleportAnchor).horizontalDistanceSqr();
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.getMastermindBrain().isTacticalAnchorValid(this.teleportAnchor)
                && distance >= 2.5 * 2.5 && distance <= 8 * 8 && Math.abs(destination.y - this.teleportAnchor.y) <= 3
                && this.distanceToSqr(destination) <= WhiteEndermanConfig.teleportRange() * WhiteEndermanConfig.teleportRange();
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) { if (this.isNoAi() || !this.isAlive()) this.getMastermindBrain().clearEncounter(); else { this.reconcileApex(); this.getMarkedTarget(); } }
        super.aiStep();
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); } else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input); }
    public enum CombatPhase {
        IDLE, OBSERVING, STALKING, FLANKING, PURSUING, MARK_WINDUP, MARK, TRACKING_WINDUP, TRACKING, APEX_WINDUP, APEX, SEARCHING, RECOVERY;
        public boolean isCommitted() { return this == MARK_WINDUP || this == MARK || this == TRACKING_WINDUP || this == APEX_WINDUP || this == RECOVERY; }
        public boolean isCasting() { return this == MARK_WINDUP || this == MARK || this == TRACKING_WINDUP || this == APEX_WINDUP; }
        public boolean bracesCaster() { return this.isCommitted() || this == OBSERVING; }
    }
}
